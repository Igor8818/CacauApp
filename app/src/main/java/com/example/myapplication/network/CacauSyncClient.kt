package com.example.myapplication.network

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.media.ExifInterface
import com.example.myapplication.data.RegistroCacau
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

/**
 * Cliente de rede HTTP assíncrono para comunicação com a API REST do CacauSync.
 * Gerencia autenticação JWT, ingestão de dados em lote (JSON com caixas YOLO e scores reais)
 * e upload de fotos redimensionadas com orientação EXIF correta.
 */
object CacauSyncClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    private val JPEG_MEDIA_TYPE = "image/jpeg".toMediaType()

    data class SyncResultado(
        val sucesso: Boolean,
        val ensaio: String,
        val totalProcessadas: Int,
        val novasInseridas: Int,
        val duplicadasIgnoradas: Int,
        val fotosEnviadas: Int,
        val mensagens: List<String>
    )

    /**
     * Autentica pesquisador e obtém o Token Bearer JWT.
     */
    fun login(serverUrl: String, email: String, pass: String): Result<String> {
        val baseUrl = normalizarUrl(serverUrl)
        val loginJson = JSONObject().apply {
            put("email", email.trim())
            put("senha", pass)
        }

        val request = Request.Builder()
            .url("$baseUrl/api/v1/auth/login")
            .post(loginJson.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    val jsonObj = JSONObject(bodyStr)
                    val token = jsonObj.optString("token")
                    if (token.isNotBlank()) {
                        Result.success(token)
                    } else {
                        Result.failure(Exception("Resposta de autenticação sem token."))
                    }
                } else {
                    val msg = extrairMensagemErro(bodyStr, "Falha na autenticação (HTTP ${response.code})")
                    Result.failure(Exception(msg))
                }
            }
        } catch (e: Exception) {
            Result.failure(Exception("Não foi possível conectar ao servidor CacauSync: ${e.message}"))
        }
    }

    /**
     * Executa a sincronização completa:
     * 1. Envia lote de leituras com caixas YOLO e scores reais (POST /api/v1/coletas/sync-json)
     * 2. Recebe os IDs criados/existentes no banco
     * 3. Faz o upload das fotos correspondentes corrigindo orientação EXIF (POST /api/v1/leituras/{id}/foto)
     */
    fun sincronizar(
        context: Context,
        serverUrl: String,
        token: String,
        codigoEnsaio: String,
        registros: List<RegistroCacau>,
        onProgresso: (status: String) -> Unit,
        onStatusRegistro: ((plantId: String, status: String) -> Unit)? = null
    ): Result<SyncResultado> {
        if (registros.isEmpty()) {
            return Result.failure(Exception("Nenhum registro para sincronizar."))
        }

        val baseUrl = normalizarUrl(serverUrl)
        onProgresso("Preparando ${registros.size} coleta(s)...")

        // 1. Monta o payload JSON (SyncJsonRequest)
        val leiturasArray = JSONArray()
        for (reg in registros) {
            val itemObj = JSONObject().apply {
                put("campanha", reg.ensaioManual.ifBlank { codigoEnsaio })
                put("plantId", reg.plantId)
                // Extrai clone do ID ou da parcela
                val partes = reg.plantId.split("_")
                val cloneCode = if (partes.isNotEmpty()) partes[0] else "CCN-51"
                put("clone", cloneCode)
                put("parcela", reg.parcelaManual.ifBlank { "1" })
                put("planta", if (reg.numPlanta > 0) reg.numPlanta else 1)
                put("resultado", if (reg.diagnostico.equals("Não Coletável", ignoreCase = true)) "Não Coletável" else reg.totalFrutos.toString())
                put("data", reg.data)
                put("lat", reg.lat)
                put("lng", reg.lng)

                // Caixas auditadas do YOLO com confiança real
                val deteccoesArray = JSONArray()
                for (cx in reg.caixas) {
                    val detObj = JSONObject().apply {
                        put("classe", cx.estado.rotulo)
                        put("confianca", cx.confianca.toDouble())
                        put("x", cx.normLeft.toDouble())
                        put("y", cx.normTop.toDouble())
                        put("largura", (cx.normRight - cx.normLeft).toDouble())
                        put("altura", (cx.normBottom - cx.normTop).toDouble())
                    }
                    deteccoesArray.put(detObj)
                }
                put("deteccoes", deteccoesArray)
            }
            leiturasArray.put(itemObj)
        }

        val requestRoot = JSONObject().apply {
            put("codigoEnsaio", codigoEnsaio.trim().uppercase())
            put("leituras", leiturasArray)
        }

        onProgresso("Enviando dados para o servidor...")
        val syncRequest = Request.Builder()
            .url("$baseUrl/api/v1/coletas/sync-json")
            .header("Authorization", "Bearer $token")
            .post(requestRoot.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        val idMap = mutableMapOf<String, Long>()
        var novas = 0
        var duplicadas = 0
        var totalProc = 0
        val mensagens = mutableListOf<String>()

        try {
            client.newCall(syncRequest).execute().use { response ->
                val bodyStr = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    val msg = extrairMensagemErro(bodyStr, "Erro ao enviar coletas (HTTP ${response.code})")
                    return Result.failure(Exception(msg))
                }

                val respObj = JSONObject(bodyStr)
                totalProc = respObj.optInt("totalLinhasProcessadas", registros.size)
                novas = respObj.optInt("leiturasNovasInseridas", 0)
                duplicadas = respObj.optInt("leiturasDuplicadasIgnoradas", 0)

                val avisosArray = respObj.optJSONArray("avisos")
                if (avisosArray != null) {
                    for (i in 0 until avisosArray.length()) {
                        mensagens.add(avisosArray.getString(i))
                    }
                }

                // Mapeamento plantId -> leituraId
                val leiturasRetornadas = respObj.optJSONArray("leituras")
                if (leiturasRetornadas != null) {
                    for (i in 0 until leiturasRetornadas.length()) {
                        val item = leiturasRetornadas.getJSONObject(i)
                        val pId = item.optString("plantId")
                        val lId = item.optLong("leituraId")
                        if (pId.isNotBlank() && lId > 0) {
                            idMap[pId] = lId
                        }
                    }
                }
            }
        } catch (e: Exception) {
            return Result.failure(Exception("Falha na sincronização dos dados: ${e.message}"))
        }

        // 2. Upload das fotos correspondentes
        var fotosEnviadas = 0
        for ((idx, reg) in registros.withIndex()) {
            val leituraId = idMap[reg.plantId]
            if (leituraId == null) {
                // Não obteve ID do backend
                onStatusRegistro?.invoke(reg.plantId, "ERRO")
                continue
            }

            onProgresso("Enviando fotos (${idx + 1}/${registros.size})...")

            val fotoBytes = prepararFotoBytes(context, reg.uri)
            if (fotoBytes != null && fotoBytes.isNotEmpty()) {
                val sucessoFoto = uploadFoto(baseUrl, token, leituraId, reg.plantId, fotoBytes)
                if (sucessoFoto) {
                    fotosEnviadas++
                    reg.statusSync = "SINCRONIZADO"
                    onStatusRegistro?.invoke(reg.plantId, "SINCRONIZADO")
                } else {
                    mensagens.add("Não foi possível enviar a foto da planta ${reg.plantId}")
                    reg.statusSync = "ERRO"
                    onStatusRegistro?.invoke(reg.plantId, "ERRO")
                }
            } else {
                // Leitura sem foto (ex: planta não coletável / seca)
                reg.statusSync = "SINCRONIZADO"
                onStatusRegistro?.invoke(reg.plantId, "SINCRONIZADO")
            }
        }

        return Result.success(
            SyncResultado(
                sucesso = true,
                ensaio = codigoEnsaio,
                totalProcessadas = totalProc,
                novasInseridas = novas,
                duplicadasIgnoradas = duplicadas,
                fotosEnviadas = fotosEnviadas,
                mensagens = mensagens
            )
        )
    }

    private fun uploadFoto(
        baseUrl: String,
        token: String,
        leituraId: Long,
        plantId: String,
        bytes: ByteArray
    ): Boolean {
        val fileName = "${plantId}_${System.currentTimeMillis()}.jpg"
        val multipartBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "foto",
                fileName,
                bytes.toRequestBody(JPEG_MEDIA_TYPE)
            )
            .build()

        val request = Request.Builder()
            .url("$baseUrl/api/v1/leituras/$leituraId/foto")
            .header("Authorization", "Bearer $token")
            .post(multipartBody)
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Lê a foto, corrige orientação EXIF (para casar com as caixas do YOLO)
     * e redimensiona com inSampleSize seguro (evita OutOfMemory em celulares com pouca RAM).
     */
    private fun prepararFotoBytes(context: Context, uri: Uri): ByteArray? {
        return try {
            // 1. Orientação EXIF da foto capturada
            val rotationDegrees = try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val exif = ExifInterface(stream)
                    when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                        ExifInterface.ORIENTATION_ROTATE_90 -> 90
                        ExifInterface.ORIENTATION_ROTATE_180 -> 180
                        ExifInterface.ORIENTATION_ROTATE_270 -> 270
                        else -> 0
                    }
                } ?: 0
            } catch (e: Exception) {
                0
            }

            // 2. Leitura segura de dimensões
            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, boundsOptions)
            }

            val maxDim = 1600
            var sampleSize = 1
            while (boundsOptions.outWidth / (sampleSize * 2) >= maxDim && boundsOptions.outHeight / (sampleSize * 2) >= maxDim) {
                sampleSize *= 2
            }

            // 3. Decodificação econômica em memória
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.RGB_565
            }

            val decodedBitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOptions)
            } ?: return null

            // 4. Aplica rotação para ficar de pé (alinhada às caixas)
            val rotatedBitmap = if (rotationDegrees != 0) {
                val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
                val rotated = Bitmap.createBitmap(decodedBitmap, 0, 0, decodedBitmap.width, decodedBitmap.height, matrix, true)
                if (rotated != decodedBitmap) {
                    decodedBitmap.recycle()
                }
                rotated
            } else {
                decodedBitmap
            }

            // 5. Escala final para até 1600px
            val curW = rotatedBitmap.width
            val curH = rotatedBitmap.height
            val finalBitmap = if (curW > maxDim || curH > maxDim) {
                val scale = maxDim.toFloat() / maxOf(curW, curH)
                val targetW = (curW * scale).toInt()
                val targetH = (curH * scale).toInt()
                val scaled = Bitmap.createScaledBitmap(rotatedBitmap, targetW, targetH, true)
                if (scaled != rotatedBitmap) {
                    rotatedBitmap.recycle()
                }
                scaled
            } else {
                rotatedBitmap
            }

            val baos = ByteArrayOutputStream()
            finalBitmap.compress(Bitmap.CompressFormat.JPEG, 85, baos)
            finalBitmap.recycle()
            baos.toByteArray()
        } catch (e: Exception) {
            null
        }
    }

    private fun normalizarUrl(url: String): String {
        var u = url.trim()
        if (!u.startsWith("http://") && !u.startsWith("https://")) {
            u = "http://$u"
        }
        if (u.endsWith("/")) {
            u = u.substring(0, u.length - 1)
        }
        return u
    }

    private fun extrairMensagemErro(body: String, fallback: String): String {
        return try {
            val json = JSONObject(body)
            json.optString("mensagem", json.optString("message", fallback))
        } catch (e: Exception) {
            fallback
        }
    }
}
