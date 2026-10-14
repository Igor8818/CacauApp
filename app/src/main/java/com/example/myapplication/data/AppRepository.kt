package com.example.myapplication.data

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

/**
 * Repositório central com persistência atômica em disco para garantir
 * que dados de campo, configurações e rotas sobrevivam a reinicializações,
 * rotação de tela e descarte de processo pelo sistema Android.
 */
class AppRepository(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("cacau_app_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val ARQUIVO_REGISTROS = "registros_campanha.json"
        private const val ARQUIVO_ROTA = "rota_campanha.json"
        private const val TAG = "AppRepository"
    }

    // ── Gravação Atômica Auxiliar ─────────────────────────────────────────────

    private fun gravarTextoAtomico(nomeArquivo: String, conteudo: String): Boolean {
        return try {
            val destino = File(context.filesDir, nomeArquivo)
            val temp = File(context.filesDir, "$nomeArquivo.tmp")

            FileOutputStream(temp).use { fos ->
                fos.write(conteudo.toByteArray(Charsets.UTF_8))
                fos.flush()
                try {
                    fos.fd.sync()
                } catch (_: Exception) {}
            }

            if (temp.renameTo(destino)) {
                true
            } else {
                // Fallback para sistemas onde renameTo falha se destino existe
                destino.delete()
                temp.renameTo(destino)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro na gravacao atomica de $nomeArquivo: ${e.message}")
            false
        }
    }

    private fun lerTexto(nomeArquivo: String): String? {
        val arquivo = File(context.filesDir, nomeArquivo)
        if (!arquivo.exists() || arquivo.length() == 0L) return null
        return try {
            arquivo.readText(Charsets.UTF_8)
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao ler $nomeArquivo: ${e.message}")
            null
        }
    }

    // ── Registros de Coleta ──────────────────────────────────────────────────

    fun salvarRegistros(registros: List<RegistroCacau>) {
        val jsonArray = JSONArray()
        registros.forEach { reg ->
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
                put("contagemIa", reg.contagemIa)
                put("contagemFinal", reg.contagemFinal)
                put("confiancaMedia", reg.confiancaMedia.toDouble())
                put("modelo", reg.modelo)
                put("threshold", reg.threshold.toDouble())
                put("operador", reg.operador)
                put("fazenda", reg.fazenda)
                put("precisaoGpsM", reg.precisaoGpsM.toDouble())
                put("altitude", reg.altitude)
                put("versaoApp", reg.versaoApp)
                put("observacao", reg.observacao)
                put("timestampMs", reg.timestampMs)
            }
            jsonArray.put(obj)
        }
        gravarTextoAtomico(ARQUIVO_REGISTROS, jsonArray.toString())
    }

    fun carregarRegistros(): List<RegistroCacau> {
        val jsonString = lerTexto(ARQUIVO_REGISTROS) ?: return emptyList()
        val lista = mutableListOf<RegistroCacau>()
        try {
            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val uriStr = obj.optString("uri", "")
                val uri = if (uriStr.isNotBlank()) Uri.parse(uriStr) else Uri.EMPTY
                val diagnostico = obj.optString("diagnostico", "0")
                lista.add(
                    RegistroCacau(
                        uri = uri,
                        plantId = obj.optString("plantId", ""),
                        ensaioManual = obj.optString("ensaioManual", ""),
                        parcelaManual = obj.optString("parcelaManual", "1"),
                        diagnostico = diagnostico,
                        data = obj.optString("data", ""),
                        numPlanta = obj.optInt("numPlanta", 1),
                        lat = obj.optDouble("lat", 0.0),
                        lng = obj.optDouble("lng", 0.0),
                        contagemIa = obj.optInt("contagemIa", diagnostico.toIntOrNull() ?: 0),
                        contagemFinal = obj.optInt("contagemFinal", diagnostico.toIntOrNull() ?: 0),
                        confiancaMedia = obj.optDouble("confiancaMedia", 0.0).toFloat(),
                        modelo = obj.optString("modelo", ""),
                        threshold = obj.optDouble("threshold", 0.55).toFloat(),
                        operador = obj.optString("operador", ""),
                        fazenda = obj.optString("fazenda", ""),
                        precisaoGpsM = obj.optDouble("precisaoGpsM", 0.0).toFloat(),
                        altitude = obj.optDouble("altitude", 0.0),
                        versaoApp = obj.optString("versaoApp", "1.0"),
                        observacao = obj.optString("observacao", ""),
                        timestampMs = obj.optLong("timestampMs", 0L)
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao decodificar registros JSON: ${e.message}")
        }
        return lista
    }

    fun limparRegistros() {
        File(context.filesDir, ARQUIVO_REGISTROS).delete()
    }

    // ── Rota de Coleta (FieldBook) ────────────────────────────────────────────

    fun salvarRota(rota: List<RotaColeta>) {
        val jsonArray = JSONArray()
        rota.forEach { r ->
            val obj = JSONObject().apply {
                put("clone", r.clone)
                put("parcela", r.parcela)
                put("planta", r.planta)
                put("coletado", r.coletado)
                put("qtdFrutos", r.qtdFrutos)
                put("observacao", r.observacao)
            }
            jsonArray.put(obj)
        }
        gravarTextoAtomico(ARQUIVO_ROTA, jsonArray.toString())
    }

    fun carregarRota(): List<RotaColeta> {
        val jsonString = lerTexto(ARQUIVO_ROTA) ?: return emptyList()
        val lista = mutableListOf<RotaColeta>()
        try {
            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                lista.add(
                    RotaColeta(
                        clone = obj.optString("clone", ""),
                        parcela = obj.optString("parcela", "1"),
                        planta = obj.optInt("planta", 1),
                        coletado = obj.optBoolean("coletado", false),
                        qtdFrutos = obj.optString("qtdFrutos", "0"),
                        observacao = obj.optString("observacao", "")
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao decodificar rota JSON: ${e.message}")
        }
        return lista
    }

    fun limparRota() {
        File(context.filesDir, ARQUIVO_ROTA).delete()
    }

    // ── ModelConfig (Persistência com sobrevivência a reboot) ────────────────

    fun salvarModelConfig(config: ModelConfig) {
        prefs.edit().apply {
            putBoolean("tem_externo", config.modeloExternoUri != null)
            putString("externo_uri", config.modeloExternoUri?.toString() ?: "")
            putString("externo_nome", config.modeloExternoNome)
            putString("modelo_embutido_arq", config.modeloSelecionado?.nomeArquivo ?: "")
            putFloat("threshold", config.threshold)
            putInt("max_resultados", config.maxResultados)
            putString("labels_filtro", config.labelsFiltro.joinToString(","))
            apply()
        }
    }

    fun carregarModelConfig(): ModelConfig {
        val threshold = prefs.getFloat("threshold", 0.30f)
        val maxRes = prefs.getInt("max_resultados", 15)
        val labelsStr = prefs.getString("labels_filtro", "cacau,fruto") ?: "cacau,fruto"
        val labels = labelsStr.split(",").map { it.trim() }.filter { it.isNotEmpty() }

        val temExterno = prefs.getBoolean("tem_externo", false)
        val externoUriStr = prefs.getString("externo_uri", "") ?: ""
        val externoNome = prefs.getString("externo_nome", "") ?: ""

        if (temExterno && externoUriStr.isNotBlank()) {
            val uri = Uri.parse(externoUriStr)
            // Se o arquivo externo em filesDir existir ou for uma URI utilizavel
            return ModelConfig(
                modeloSelecionado = null,
                modeloExternoUri = uri,
                modeloExternoNome = externoNome.ifBlank { "modelo_externo.tflite" },
                labelsFiltro = labels,
                threshold = threshold,
                maxResultados = maxRes
            )
        }

        val embutidoArq = prefs.getString("modelo_embutido_arq", "detector.tflite") ?: "detector.tflite"
        val embutido = MODELOS_EMBUTIDOS.firstOrNull { it.nomeArquivo == embutidoArq }
            ?: MODELOS_EMBUTIDOS.firstOrNull()

        return ModelConfig(
            modeloSelecionado = embutido,
            modeloExternoUri = null,
            modeloExternoNome = "",
            labelsFiltro = labels,
            threshold = threshold,
            maxResultados = maxRes
        )
    }

    // ── Estado da Sessão e Metadados ──────────────────────────────────────────

    fun salvarEstadoSessao(
        ensaioNome: String,
        configConcluida: Boolean,
        historicoClones: List<String>,
        label1: String,
        label2: String,
        label3: String,
        isNum2: Boolean,
        isNum3: Boolean,
        operador: String = "",
        fazenda: String = "",
        valorNum2: Int = 1,
        valorNum3: Int = 1
    ) {
        prefs.edit().apply {
            putString("ensaio_nome", ensaioNome)
            putBoolean("config_concluida", configConcluida)
            putString("historico_clones", JSONArray(historicoClones).toString())
            putString("label1", label1)
            putString("label2", label2)
            putString("label3", label3)
            putBoolean("is_num2", isNum2)
            putBoolean("is_num3", isNum3)
            putString("operador", operador)
            putString("fazenda", fazenda)
            putInt("valor_num2", valorNum2)
            putInt("valor_num3", valorNum3)
            apply()
        }
    }

    fun carregarValoresNumericos(): Pair<Int, Int> {
        val v2 = prefs.getInt("valor_num2", 1).coerceAtLeast(1)
        val v3 = prefs.getInt("valor_num3", 1).coerceAtLeast(1)
        return Pair(v2, v3)
    }

    fun carregarEnsaioNome(): String = prefs.getString("ensaio_nome", "") ?: ""
    fun carregarConfigConcluida(): Boolean = prefs.getBoolean("config_concluida", false)

    fun carregarHistoricoClones(): List<String> {
        val raw = prefs.getString("historico_clones", null) ?: return emptyList()
        val list = mutableListOf<String>()
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) list.add(arr.getString(i))
        } catch (_: Exception) {}
        return list
    }

    fun carregarLabels(): Triple<String, String, String> {
        val l1 = prefs.getString("label1", "Clone") ?: "Clone"
        val l2 = prefs.getString("label2", "Parcela") ?: "Parcela"
        val l3 = prefs.getString("label3", "Planta") ?: "Planta"
        return Triple(l1, l2, l3)
    }

    fun carregarTiposNumericos(): Pair<Boolean, Boolean> {
        val n2 = prefs.getBoolean("is_num2", true)
        val n3 = prefs.getBoolean("is_num3", true)
        return Pair(n2, n3)
    }

    fun carregarOperador(): String = prefs.getString("operador", "") ?: ""
    fun carregarFazenda(): String = prefs.getString("fazenda", "") ?: ""
}
