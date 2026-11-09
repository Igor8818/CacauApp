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

    private val fastDiscoveryClient = OkHttpClient.Builder()
        .connectTimeout(380, TimeUnit.MILLISECONDS)
        .readTimeout(450, TimeUnit.MILLISECONDS)
        .callTimeout(600, TimeUnit.MILLISECONDS)
        .build()

    const val DEFAULT_EMAIL = "coordenador@cepec.gov.br"
    const val DEFAULT_PASS = "cepec123"
    const val DEFAULT_SERVER_URL = "http://192.168.15.4:8080"

    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    private val JPEG_MEDIA_TYPE = "image/jpeg".toMediaType()

    data class SyncResultado(
        val sucesso: Boolean,
        val ensaio: String,
        val totalProcessadas: Int,
        val novasInseridas: Int,
        val duplicadasIgnoradas: Int,
        val fotosEnviadas: Int,
        val mensagens: List<String>,
        val servidorUtilizado: String = ""
    )

    /**
     * Testa rapidamente se uma URL responde como um servidor CacauSync ativo.
     */
    fun testarServidorAtivo(candidateUrl: String): Boolean {
        val baseUrl = normalizarUrl(candidateUrl)
        val req = Request.Builder()
            .url("$baseUrl/v3/api-docs")
            .get()
            .build()
        return try {
            fastDiscoveryClient.newCall(req).execute().use { resp ->
                resp.isSuccessful
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Descobre automaticamente o computador onde o CacauSync está rodando:
     * 1. Testa túnel USB direto (127.0.0.1:8080 via adb reverse)
     * 2. Testa a última URL salva e o IP padrão da estação local (192.168.15.4:8080)
     * 3. Varre automaticamente em paralelo a sub-rede Wi-Fi local (/24) na porta 8080
     */
    fun descobrirServidorAutomaticamente(
        urlSalva: String = "",
        onProgresso: (String) -> Unit = {}
    ): String? {
        onProgresso("Procurando PC (USB / Wi-Fi)...")

        val candidatosPrioritarios = mutableListOf(
            "http://127.0.0.1:8080",
            DEFAULT_SERVER_URL,
            "http://10.0.2.2:8080"
        )
        if (urlSalva.isNotBlank()) {
            candidatosPrioritarios.add(0, normalizarUrl(urlSalva))
        }

        for (cand in candidatosPrioritarios.distinct()) {
            if (testarServidorAtivo(cand)) {
                return normalizarUrl(cand)
            }
        }

        // Varredura rápida da sub-rede local Wi-Fi (ex: 192.168.x.1..254)
        val prefixosRede = obterPrefixosSubredeLocal()
        if (prefixosRede.isNotEmpty()) {
            onProgresso("Buscando PC na rede Wi-Fi (${prefixosRede.first()}.x)...")
            val encontrado = java.util.concurrent.atomic.AtomicReference<String?>(null)
            val pool = java.util.concurrent.Executors.newFixedThreadPool(32)
            try {
                val futures = mutableListOf<java.util.concurrent.Future<*>>()
                for (prefixo in prefixosRede) {
                    for (host in 1..254) {
                        val candidate = "http://$prefixo.$host:8080"
                        futures.add(
                            pool.submit {
                                if (encontrado.get() == null && testarServidorAtivo(candidate)) {
                                    encontrado.compareAndSet(null, candidate)
                                }
                            }
                        )
                    }
                }
                for (f in futures) {
                    if (encontrado.get() != null) break
                    try {
                        f.get(5, TimeUnit.SECONDS)
                    } catch (_: Exception) {}
                }
            } finally {
                pool.shutdownNow()
            }
            encontrado.get()?.let { return it }
        }

        return null
    }

    private fun obterPrefixosSubredeLocal(): List<String> {
        val prefixos = mutableListOf<String>()
        try {
            val interfaces = java.net.NetworkInterface.getNetworkInterfaces() ?: return emptyList()
            for (nif in java.util.Collections.list(interfaces)) {
                if (!nif.isUp || nif.isLoopback) continue
                for (addr in java.util.Collections.list(nif.inetAddresses)) {
                    if (addr is java.net.Inet4Address && !addr.isLoopbackAddress) {
                        val ip = addr.hostAddress ?: continue
                        val partes = ip.split(".")
                        if (partes.size == 4) {
                            val prefixo = "${partes[0]}.${partes[1]}.${partes[2]}"
                            if (!prefixos.contains(prefixo)) {
                                prefixos.add(prefixo)
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {}
        return prefixos
    }

    /**
     * Sincronização em 1 Clique (Zero-Config):
     * Descobre o servidor PC automaticamente (via cabo USB ou Wi-Fi), autentica com
     * as credenciais padrão do laboratório e envia todas as coletas + caixas YOLO + fotos.
     */
    fun sincronizarAutomatico(
        context: Context,
        ensaioPadrao: String,
        registros: List<RegistroCacau>,
        onProgresso: (status: String) -> Unit,
        onStatusRegistro: ((plantId: String, status: String) -> Unit)? = null
    ): Result<SyncResultado> {
        if (registros.isEmpty()) {
            return Result.failure(Exception("Nenhum registro para sincronizar."))
        }

        val prefs = context.getSharedPreferences("cacausync_prefs", Context.MODE_PRIVATE)
        val urlSalva = prefs.getString("server_url", "") ?: ""
        val emailSalvo = (prefs.getString("user_email", "") ?: "").ifBlank { DEFAULT_EMAIL }
        val passSalva = (prefs.getString("user_pass", "") ?: "").ifBlank { DEFAULT_PASS }
        val codigoEnsaio = (prefs.getString("codigo_ensaio", "TC-1801") ?: "TC-1801")
            .ifBlank { "TC-1801" }

        val urlDescoberta = descobrirServidorAutomaticamente(urlSalva, onProgresso)
            ?: return Result.failure(
                Exception(
                    "Servidor CacauSync não encontrado automaticamente.\n\n" +
                    "• Verifique se o CacauSync está aberto no PC\n" +
                    "• Conecte o cabo USB ou deixe o celular e o PC no mesmo Wi-Fi"
                )
            )

        // Salva a URL descoberta para que o próximo envio seja instantâneo
        prefs.edit()
            .putString("server_url", urlDescoberta)
            .putString("user_email", emailSalvo)
            .putString("user_pass", passSalva)
            .putString("codigo_ensaio", codigoEnsaio)
            .apply()

        onProgresso("Conectado a $urlDescoberta! Autenticando...")
        var loginRes = login(urlDescoberta, emailSalvo, passSalva)
        if (loginRes.isFailure && (emailSalvo != DEFAULT_EMAIL || passSalva != DEFAULT_PASS)) {
            loginRes = login(urlDescoberta, DEFAULT_EMAIL, DEFAULT_PASS)
        }
        if (loginRes.isFailure) {
            return Result.failure(
                Exception("Servidor encontrado em $urlDescoberta, mas falhou ao autenticar: ${loginRes.exceptionOrNull()?.message}")
            )
        }

        val token = loginRes.getOrThrow()
        val syncRes = sincronizar(
            context = context,
            serverUrl = urlDescoberta,
            token = token,
            codigoEnsaio = codigoEnsaio,
            registros = registros,
            onProgresso = onProgresso,
            onStatusRegistro = onStatusRegistro
        )
        return syncRes.map { it.copy(servidorUtilizado = urlDescoberta) }
    }

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
     * Identifica se o diagnóstico indica que a planta não é coletável (morta, ausente, seca).
     */
    fun isNaoColetavel(diagnostico: String?): Boolean {
        if (diagnostico == null || diagnostico.isBlank()) return false
        val d = diagnostico.trim().lowercase(java.util.Locale.ROOT)
        return d == "não coletável" || d == "nao coletavel" ||
                d.contains("não coletável") || d.contains("nao coletavel") ||
                d.contains("ausente") || d.contains("morta") || d.contains("seca")
    }

    /**
     * Constrói o payload JSON estritamente concordante com a especificação SPEC-001 (SyncJsonRequest).
     */
    fun criarPayloadSyncJson(codigoEnsaio: String, registros: List<RegistroCacau>): JSONObject {
        val leiturasArray = JSONArray()
        for (reg in registros) {
            val naoColetavel = isNaoColetavel(reg.diagnostico)
            val itemObj = JSONObject().apply {
                put("campanha", reg.ensaioManual.ifBlank { codigoEnsaio })
                put("plantId", reg.plantId)
                // Extrai clone do ID ou da parcela
                val partes = reg.plantId.split("_")
                val cloneCode = partes.firstOrNull { it.isNotBlank() } ?: "CCN-51"
                put("clone", cloneCode)
                put("parcela", reg.parcelaManual.ifBlank { "1" })
                put("planta", if (reg.numPlanta > 0) reg.numPlanta else 1)
                put("resultado", if (naoColetavel) "Não Coletável" else reg.totalFrutos.toString())
                put("data", reg.data)
                put("lat", reg.lat)
                put("lng", reg.lng)
                put("sadios", if (naoColetavel) 0 else reg.sadios)
                put("vassoura", if (naoColetavel) 0 else reg.vassoura)
                put("podridao", if (naoColetavel) 0 else reg.podridao)
                put("naoAvaliavel", if (naoColetavel) 0 else reg.naoAvaliavel)

                // Caixas auditadas do YOLO com confiança real e clamping [0.0, 1.0]
                val deteccoesArray = JSONArray()
                if (!naoColetavel) {
                    for (cx in reg.caixas) {
                        val left = cx.normLeft.coerceIn(0f, 1f)
                        val top = cx.normTop.coerceIn(0f, 1f)
                        val right = cx.normRight.coerceIn(left, 1f)
                        val bottom = cx.normBottom.coerceIn(top, 1f)
                        val detObj = JSONObject().apply {
                            put("classe", cx.estado.rotulo)
                            put("confianca", cx.confianca.coerceIn(0f, 1f).toDouble())
                            put("x", left.toDouble())
                            put("y", top.toDouble())
                            put("largura", (right - left).coerceIn(0f, 1f).toDouble())
                            put("altura", (bottom - top).coerceIn(0f, 1f).toDouble())
                        }
                        deteccoesArray.put(detObj)
                    }
                }
                put("deteccoes", deteccoesArray)
            }
            leiturasArray.put(itemObj)
        }

        return JSONObject().apply {
            put("codigoEnsaio", codigoEnsaio.trim().uppercase())
            put("leituras", leiturasArray)
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
        val requestRoot = criarPayloadSyncJson(codigoEnsaio, registros)

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

            val isPlantaNaoColetavel = isNaoColetavel(reg.diagnostico)
            val fotoBytes = if (isPlantaNaoColetavel || reg.uri == Uri.EMPTY) null else prepararFotoBytes(context, reg.uri)
            if (fotoBytes != null && fotoBytes.isNotEmpty()) {
                val sucessoFoto = uploadFoto(baseUrl, token, leituraId, reg.plantId, fotoBytes)
                if (sucessoFoto) {
                    fotosEnviadas++
                    onStatusRegistro?.invoke(reg.plantId, "SINCRONIZADO")
                } else {
                    mensagens.add("Não foi possível enviar a foto da planta ${reg.plantId}")
                    onStatusRegistro?.invoke(reg.plantId, "ERRO")
                }
            } else {
                // Leitura sem foto (ex: planta não coletável / seca)
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
    fun prepararFotoBytes(context: Context, uri: Uri): ByteArray? {
        return try {
            // 1. Orientação EXIF da foto capturada
            val orientation = try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val exif = ExifInterface(stream)
                    exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                } ?: ExifInterface.ORIENTATION_NORMAL
            } catch (e: Exception) {
                ExifInterface.ORIENTATION_NORMAL
            }

            // 2. Leitura segura de dimensões
            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, boundsOptions)
            }
            if (boundsOptions.outWidth <= 0 || boundsOptions.outHeight <= 0) return null

            val maxDim = 1600
            var sampleSize = 1
            while ((boundsOptions.outWidth / sampleSize) > maxDim || (boundsOptions.outHeight / sampleSize) > maxDim) {
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

            // 4. Aplica matriz EXIF completa para ficar de pé (alinhada às caixas)
            val matrix = Matrix()
            var needsTransform = true
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> {
                    matrix.preScale(-1f, 1f)
                    matrix.postRotate(270f)
                }
                ExifInterface.ORIENTATION_TRANSVERSE -> {
                    matrix.preScale(-1f, 1f)
                    matrix.postRotate(90f)
                }
                else -> needsTransform = false
            }

            val rotatedBitmap = if (needsTransform) {
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
