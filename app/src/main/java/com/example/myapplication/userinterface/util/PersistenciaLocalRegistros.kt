package com.example.myapplication.userinterface.util

import android.content.Context
import android.net.Uri
import com.example.myapplication.data.RegistroCacau
import com.example.myapplication.userinterface.CaixaAuditavel
import com.example.myapplication.userinterface.EstadoFruto
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object PersistenciaLocalRegistros {

    private const val FILE_NAME = "registros_cacau_local.json"
    private const val TEMP_FILE_NAME = "registros_cacau_local.tmp"
    private val fileLock = Any()

    fun salvarRegistros(context: Context, registros: List<RegistroCacau>) {
        // Snapshot defensivo imutável para evitar ConcurrentModificationException
        // caso outra thread (ex: IO sync ou UI) modifique a lista durante a serialização
        val snapshot = registros.toList()

        synchronized(fileLock) {
            try {
                val root = JSONArray()
                for (reg in snapshot) {
                    val obj = JSONObject().apply {
                        put("uri", reg.uri.toString())
                        put("plantId", reg.plantId)
                        put("ensaioManual", reg.ensaioManual)
                        put("parcelaManual", reg.parcelaManual)
                        put("diagnostico", reg.diagnostico)
                        put("data", reg.data)
                        put("numPlanta", reg.numPlanta)
                        put("lat", reg.lat)
                        put("lng", reg.lng)
                        put("totalFrutos", reg.totalFrutos)
                        put("sadios", reg.sadios)
                        put("vassoura", reg.vassoura)
                        put("podridao", reg.podridao)
                        put("naoAvaliavel", reg.naoAvaliavel)
                        put("statusSync", reg.statusSync)
                        put("caixas", serializarCaixasJsonArray(reg.caixas))
                    }
                    root.put(obj)
                }

                val targetFile = File(context.filesDir, FILE_NAME)
                val tempFile = File(context.filesDir, TEMP_FILE_NAME)

                // Escrita atômica: grava no arquivo temporário e renomeia
                tempFile.writeText(root.toString(), Charsets.UTF_8)
                if (!tempFile.renameTo(targetFile)) {
                    // Fallback caso renameTo falhe em alguns sistemas de arquivos Android
                    targetFile.writeText(root.toString(), Charsets.UTF_8)
                    tempFile.delete()
                }
            } catch (_: Exception) {}
        }
    }

    fun carregarRegistros(context: Context): List<RegistroCacau> {
        synchronized(fileLock) {
            val file = File(context.filesDir, FILE_NAME)
            if (!file.exists()) return emptyList()
            return try {
                val arr = JSONArray(file.readText(Charsets.UTF_8))
                val lista = mutableListOf<RegistroCacau>()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val uriStr = o.optString("uri", "")
                    val caixasArr = o.optJSONArray("caixas") ?: JSONArray()
                    val caixas = mutableListOf<CaixaAuditavel>()
                    for (j in 0 until caixasArr.length()) {
                        val c = caixasArr.getJSONObject(j)
                        val x = c.optDouble("x", 0.0).toFloat()
                        val y = c.optDouble("y", 0.0).toFloat()
                        val w = c.optDouble("largura", 0.1).toFloat()
                        val h = c.optDouble("altura", 0.1).toFloat()
                        val classeStr = c.optString("classe", "Sadio")
                        val estado = EstadoFruto.entries.firstOrNull {
                            it.rotulo.equals(classeStr, ignoreCase = true) || it.name.equals(classeStr, ignoreCase = true)
                        } ?: EstadoFruto.SADIO
                        caixas.add(
                            CaixaAuditavel(
                                normLeft = x.coerceIn(0f, 1f),
                                normTop = y.coerceIn(0f, 1f),
                                normRight = (x + w).coerceIn(0f, 1f),
                                normBottom = (y + h).coerceIn(0f, 1f),
                                estado = estado,
                                manual = c.optBoolean("manual", false),
                                confianca = c.optDouble("confianca", 0.9).toFloat()
                            )
                        )
                    }
                    lista.add(
                        RegistroCacau(
                            uri = if (uriStr.isBlank()) Uri.EMPTY else Uri.parse(uriStr),
                            plantId = o.optString("plantId"),
                            ensaioManual = o.optString("ensaioManual"),
                            parcelaManual = o.optString("parcelaManual"),
                            diagnostico = o.optString("diagnostico"),
                            data = o.optString("data"),
                            numPlanta = o.optInt("numPlanta", 1),
                            lat = o.optDouble("lat", 0.0),
                            lng = o.optDouble("lng", 0.0),
                            totalFrutos = o.optInt("totalFrutos", 0),
                            sadios = o.optInt("sadios", 0),
                            vassoura = o.optInt("vassoura", 0),
                            podridao = o.optInt("podridao", 0),
                            naoAvaliavel = o.optInt("naoAvaliavel", 0),
                            caixas = caixas,
                            statusSync = o.optString("statusSync", "PENDENTE")
                        )
                    )
                }
                lista
            } catch (_: Exception) {
                emptyList()
            }
        }
    }

    fun serializarCaixasJsonArray(caixas: List<CaixaAuditavel>): JSONArray {
        val arr = JSONArray()
        for (cx in caixas) {
            arr.put(
                JSONObject().apply {
                    put("classe", cx.estado.rotulo)
                    put("confianca", cx.confianca.toDouble())
                    put("x", cx.normLeft.toDouble())
                    put("y", cx.normTop.toDouble())
                    put("largura", (cx.normRight - cx.normLeft).toDouble())
                    put("altura", (cx.normBottom - cx.normTop).toDouble())
                    put("manual", cx.manual)
                }
            )
        }
        return arr
    }

    fun serializarCaixasParaCsv(caixas: List<CaixaAuditavel>): String {
        if (caixas.isEmpty()) return ""
        return serializarCaixasJsonArray(caixas).toString()
    }
}
