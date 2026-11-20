package com.example.myapplication.userinterface.util

import android.content.Context
import android.net.Uri
import android.util.AtomicFile
import android.util.Log
import com.example.myapplication.data.ConfigRendimentoCacau
import com.example.myapplication.data.LABELS_DOENCA_CACAU
import com.example.myapplication.data.MODELOS_EMBUTIDOS
import com.example.myapplication.data.ModelConfig
import com.example.myapplication.data.ModoIACacau
import com.example.myapplication.data.RegistroCacau
import com.example.myapplication.data.RotaColeta
import com.example.myapplication.userinterface.CaixaAuditavel
import com.example.myapplication.userinterface.EstadoFruto
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

object PersistenciaLocalRegistros {

    private const val FILE_NAME = "registros_cacau_local.json"
    private const val ROTA_FILE_NAME = "rota_precarregada.json"
    private const val DIR_ENSAIOS = "ensaios"
    private const val DIR_MODELOS = "modelos_ia"
    private val fileLock = Any()
    private val fotosSyncExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()

    data class ResumoEnsaioLocal(
        val id: String,
        val codigo: String,
        val totalRegistros: Int,
        val totalPontosRota: Int,
        val atualizadoEm: Long
    )

    data class ConfigEnsaioLocal(
        val ensaioId: String = "ensaio_padrao",
        val codigoEnsaio: String = "TC-1801",
        val labelClone: String = "Clone",
        val labelParcela: String = "Parcela",
        val labelPlanta: String = "Planta",
        val isNumParcela: Boolean = true,
        val isNumPlanta: Boolean = true,
        val autoAvancarPlanta: Boolean = true
    )

    data class ModeloImportadoLocal(
        val path: String,
        val nomeExibicao: String,
        val tamanhoBytes: Long,
        val modificadoEm: Long
    )

    private fun safeEnsaioId(id: String): String =
        id.trim().ifBlank { "ensaio_padrao" }.replace(Regex("[^a-zA-Z0-9._-]"), "_")

    private fun arquivoEnsaioRelativo(ensaioId: String, sufixo: String): String =
        "$DIR_ENSAIOS/${safeEnsaioId(ensaioId)}_$sufixo"

    private fun garantirDirEnsaios(context: Context) {
        try {
            File(context.filesDir, DIR_ENSAIOS).mkdirs()
        } catch (_: Exception) {}
    }

    private fun migrarLegadoSeNecessario(context: Context, ensaioId: String) {
        if (safeEnsaioId(ensaioId) != "ensaio_padrao") return
        garantirDirEnsaios(context)
        val regEnsaio = File(context.filesDir, arquivoEnsaioRelativo(ensaioId, "registros.json"))
        val regLegado = File(context.filesDir, FILE_NAME)
        if (!regEnsaio.exists() && regLegado.exists() && regLegado.length() > 0L) {
            runCatching { regLegado.copyTo(regEnsaio, overwrite = true) }
        }
        val rotaEnsaio = File(context.filesDir, arquivoEnsaioRelativo(ensaioId, "rota.json"))
        val rotaLegado = File(context.filesDir, ROTA_FILE_NAME)
        if (!rotaEnsaio.exists() && rotaLegado.exists() && rotaLegado.length() > 0L) {
            runCatching { rotaLegado.copyTo(rotaEnsaio, overwrite = true) }
        }
    }

    fun salvarRegistros(context: Context, registros: List<RegistroCacau>) {
        val snapshot = registros.toList()
        val cfg = carregarConfigEnsaio(context)
        val json = serializarRegistrosParaJson(snapshot).toString()

        synchronized(fileLock) {
            garantirDirEnsaios(context)
            escreverAtomico(context, arquivoEnsaioRelativo(cfg.ensaioId, "registros.json"), json)
            // INVARIANTE USB ADB: mantém files/registros_cacau_local.json sempre espelhado para 1-Clique USB!
            escreverAtomico(context, FILE_NAME, json)
            salvarMetaEnsaioArquivo(context, cfg)
        }

        // Espelho JPEG em files/fotos_sync/<safePlantId>.jpg para UsbAdbSyncService
        espelharFotosSync(context, snapshot)
    }

    private fun limparFotosSync(context: Context) {
        try {
            val dirFotos = File(context.filesDir, "fotos_sync")
            if (dirFotos.exists() && dirFotos.isDirectory) {
                dirFotos.listFiles()?.forEach { it.delete() }
            }
        } catch (_: Throwable) {}
    }

    private fun espelharFotosSync(context: Context, snapshot: List<RegistroCacau>) {
        try {
            if (context.contentResolver == null) return
            fotosSyncExecutor.execute {
                try {
                    val dirFotos = File(context.filesDir, "fotos_sync")
                    if (!dirFotos.exists()) dirFotos.mkdirs()

                    val ativos = mutableSetOf<String>()
                    for (reg in snapshot) {
                        if (reg.plantId.isBlank() || reg.uri == Uri.EMPTY) continue
                        val safeName = reg.plantId.replace(Regex("[^a-zA-Z0-9._-]"), "_") + ".jpg"
                        val markerName = "$safeName.uri"
                        ativos.add(safeName)
                        ativos.add(markerName)

                        val arqFoto = File(dirFotos, safeName)
                        val arqMarker = File(dirFotos, markerName)
                        val uriAtual = reg.uri.toString()
                        val uriSalva = if (arqMarker.exists()) {
                            runCatching { arqMarker.readText(Charsets.UTF_8) }.getOrDefault("")
                        } else ""

                        if (!arqFoto.exists() || arqFoto.length() == 0L || uriSalva != uriAtual) {
                            val bytes = com.example.myapplication.network.CacauSyncClient.prepararFotoBytes(context, reg.uri)
                            if (bytes != null && bytes.isNotEmpty()) {
                                arqFoto.writeBytes(bytes)
                                arqMarker.writeText(uriAtual, Charsets.UTF_8)
                            }
                        }
                    }

                    // Remove fotos antigas que não pertencem mais ao ensaio ativo
                    dirFotos.listFiles()?.forEach { f ->
                        if (f.name !in ativos) {
                            f.delete()
                        }
                    }
                } catch (_: Throwable) {}
            }
        } catch (_: Throwable) {}
    }

    fun carregarRegistros(context: Context): List<RegistroCacau> {
        val cfg = carregarConfigEnsaio(context)
        migrarLegadoSeNecessario(context, cfg.ensaioId)
        synchronized(fileLock) {
            val baseFile = File(context.filesDir, FILE_NAME)
            val ensaioFile = File(context.filesDir, arquivoEnsaioRelativo(cfg.ensaioId, "registros.json"))
            val target = if (ensaioFile.exists() && ensaioFile.length() > 0L) ensaioFile else baseFile
            val backupFile = File(target.parentFile, "${target.name}.bak")
            if (!target.exists() && !backupFile.exists()) return emptyList()

            return try {
                val atomicFile = AtomicFile(target)
                val bytes = atomicFile.readFully()
                val jsonString = String(bytes, Charsets.UTF_8)
                deserializarRegistrosDeJson(jsonString)
            } catch (e: Exception) {
                Log.e("PersistenciaLocal", "Erro ao carregar registros locais: ${e.message}", e)
                try {
                    if (target.exists() && target.length() > 0) {
                        val backup = File(context.filesDir, "registros_backup_corrompido_${System.currentTimeMillis()}.json")
                        target.copyTo(backup, overwrite = true)
                    }
                } catch (_: Exception) {}
                emptyList()
            }
        }
    }

    private fun escreverAtomico(context: Context, relativePath: String, conteudo: String) {
        val baseFile = File(context.filesDir, relativePath)
        baseFile.parentFile?.mkdirs()
        val atomicFile = AtomicFile(baseFile)
        var fos: FileOutputStream? = null
        try {
            fos = atomicFile.startWrite()
            fos.write(conteudo.toByteArray(Charsets.UTF_8))
            atomicFile.finishWrite(fos)
        } catch (e: Exception) {
            Log.e("PersistenciaLocal", "Erro ao gravar $relativePath: ${e.message}", e)
            if (fos != null) atomicFile.failWrite(fos)
        }
    }

    private fun lerAtomico(context: Context, relativePath: String): String? {
        val baseFile = File(context.filesDir, relativePath)
        val bakFile = File(baseFile.parentFile, "${baseFile.name}.bak")
        if (!baseFile.exists() && !bakFile.exists()) return null
        return try {
            String(AtomicFile(baseFile).readFully(), Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    fun serializarRegistrosParaJson(registros: List<RegistroCacau>): JSONArray {
        val root = JSONArray()
        for (reg in registros) {
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
        return root
    }

    fun deserializarRegistrosDeJson(jsonString: String): List<RegistroCacau> {
        if (jsonString.isBlank()) return emptyList()
        val arr = JSONArray(jsonString)
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
                val rawLabel = c.optString("label", classeStr)
                val estado = EstadoFruto.entries.firstOrNull {
                    it.rotulo.equals(classeStr, ignoreCase = true) ||
                        it.rotuloCurto.equals(classeStr, ignoreCase = true) ||
                        it.name.equals(classeStr, ignoreCase = true)
                } ?: com.example.myapplication.userinterface.mapearLabelParaEstadoFruto(rawLabel)
                caixas.add(
                    CaixaAuditavel(
                        normLeft = x.coerceIn(0f, 1f),
                        normTop = y.coerceIn(0f, 1f),
                        normRight = (x + w).coerceIn(0f, 1f),
                        normBottom = (y + h).coerceIn(0f, 1f),
                        estado = estado,
                        manual = c.optBoolean("manual", false),
                        confianca = c.optDouble("confianca", 0.9).toFloat(),
                        label = rawLabel
                    )
                )
            }
            lista.add(
                RegistroCacau(
                    uri = safeParseUri(uriStr),
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
        return lista
    }

    fun serializarCaixasJsonArray(caixas: List<CaixaAuditavel>): JSONArray {
        val arr = JSONArray()
        for (cx in caixas) {
            arr.put(
                JSONObject().apply {
                    put("classe", cx.estado.rotulo)
                    put("label", cx.label)
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

    private fun safeParseUri(uriStr: String): Uri {
        try {
            val u = if (uriStr.isNotBlank()) Uri.parse(uriStr) else Uri.EMPTY
            if (u != null) return u
            if (Uri.EMPTY != null) return Uri.EMPTY
        } catch (_: Throwable) {}

        return try {
            val mockitoClass = Class.forName("org.mockito.Mockito")
            val mockMethod = mockitoClass.getMethod("mock", Class::class.java)
            mockMethod.invoke(null, Uri::class.java) as Uri
        } catch (_: Throwable) {
            try {
                val unsafeClass = Class.forName("sun.misc.Unsafe")
                val theUnsafeField = unsafeClass.getDeclaredField("theUnsafe").apply { isAccessible = true }
                val unsafe = theUnsafeField.get(null)
                val allocateMethod = unsafeClass.getMethod("allocateInstance", Class::class.java)
                allocateMethod.invoke(unsafe, Uri::class.java) as Uri
            } catch (_: Throwable) {
                error("Não foi possível instanciar android.net.Uri no ambiente atual")
            }
        }
    }

    // ── CONFIGURAÇÃO DO MOTOR IA (2 MODELOS CACAU CEPEC) ──

    private const val PREFS_MODELO = "cacau_model_prefs"
    private const val KEY_MODELO_ARQUIVO = "modelo_arquivo"
    private const val KEY_INTERNO_PATH = "modelo_interno_path"
    private const val KEY_EXTERNO_URI = "externo_uri"
    private const val KEY_EXTERNO_NOME = "externo_nome"
    private const val KEY_THRESHOLD = "threshold"
    private const val KEY_THRESHOLD_DOENCA = "threshold_doenca"
    private const val KEY_MAX_RESULTADOS = "max_resultados"
    private const val KEY_LABELS = "labels_filtro"
    private const val KEY_USAR_DIAG_SECUNDARIO = "usar_diag_secundario"
    private const val KEY_MODELO_SECUNDARIO = "modelo_secundario_arquivo"
    private const val KEY_MODO_IA = "modo_ia_cacau"

    fun salvarConfiguracaoModelo(context: Context, config: ModelConfig) {
        try {
            val prefs = context.getSharedPreferences(PREFS_MODELO, Context.MODE_PRIVATE)
            val labelsJson = JSONArray().apply { config.labelsFiltro.forEach { put(it) } }.toString()

            val edit = prefs.edit()
                .putString(KEY_MODELO_ARQUIVO, config.modeloSelecionado?.nomeArquivo)
                .putString(KEY_INTERNO_PATH, config.modeloInternoPath)
                .putString(KEY_EXTERNO_URI, config.modeloExternoUri?.toString())
                .putString(KEY_EXTERNO_NOME, config.modeloExternoNome)
                .putFloat(KEY_THRESHOLD, config.threshold.coerceIn(0.05f, 0.95f))
                .putFloat(KEY_THRESHOLD_DOENCA, config.thresholdDoenca.coerceIn(0.05f, 0.95f))
                .putInt(KEY_MAX_RESULTADOS, config.maxResultados.coerceIn(1, 200))
                .putString(KEY_LABELS, labelsJson)
                .putBoolean(KEY_USAR_DIAG_SECUNDARIO, config.modoEfetivo == ModoIACacau.COMBINADO_2_IAS)
                .putString(KEY_MODELO_SECUNDARIO, config.modeloSecundarioArquivo)
                .putString(KEY_MODO_IA, config.modoEfetivo.name)
            if (!config.modeloInternoPath.isNullOrBlank() && config.modeloExternoNome.isNotBlank()) {
                val nomeArq = File(config.modeloInternoPath).name
                edit.putString("import_name_$nomeArq", config.modeloExternoNome.trim())
            }
            edit.apply()
        } catch (e: Exception) {
            Log.e("PersistenciaLocal", "Erro ao salvar configuracao do modelo: ${e.message}")
        }
    }

    fun salvarNomeModeloImportado(context: Context, path: String, nomeExibicao: String) {
        try {
            val nomeArq = File(path).name
            context.getSharedPreferences(PREFS_MODELO, Context.MODE_PRIVATE).edit()
                .putString("import_name_$nomeArq", nomeExibicao.trim())
                .apply()
        } catch (_: Exception) {}
    }

    fun listarModelosImportados(context: Context): List<ModeloImportadoLocal> {
        return try {
            val dir = File(context.filesDir, DIR_MODELOS)
            if (!dir.exists() || !dir.isDirectory) return emptyList()
            val prefs = context.getSharedPreferences(PREFS_MODELO, Context.MODE_PRIVATE)
            dir.listFiles()
                ?.filter { it.isFile && it.name.endsWith(".tflite", ignoreCase = true) && it.length() > 16L }
                ?.map { arq ->
                    val nomeSalvo = prefs.getString("import_name_${arq.name}", null)
                    val nomeAmigavel = nomeSalvo?.takeIf { it.isNotBlank() }
                        ?: arq.nameWithoutExtension.replace(Regex("_\\d{10,}$"), "")
                    ModeloImportadoLocal(
                        path = arq.absolutePath,
                        nomeExibicao = nomeAmigavel,
                        tamanhoBytes = arq.length(),
                        modificadoEm = arq.lastModified()
                    )
                }
                ?.sortedByDescending { it.modificadoEm }
                ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun excluirModeloImportado(context: Context, path: String): Boolean {
        return try {
            val arq = File(path)
            val deleted = arq.exists() && arq.delete()
            if (deleted) {
                context.getSharedPreferences(PREFS_MODELO, Context.MODE_PRIVATE).edit()
                    .remove("import_name_${arq.name}")
                    .apply()
            }
            deleted
        } catch (_: Exception) {
            false
        }
    }

    fun carregarConfiguracaoModelo(context: Context): ModelConfig {
        return try {
            val prefs = context.getSharedPreferences(PREFS_MODELO, Context.MODE_PRIVATE)
            val threshold = prefs.getFloat(KEY_THRESHOLD, 0.30f).coerceIn(0.05f, 0.95f)
            val thresholdDoenca = prefs.getFloat(KEY_THRESHOLD_DOENCA, 0.25f).coerceIn(0.05f, 0.95f)
            val maxResultados = prefs.getInt(KEY_MAX_RESULTADOS, 25).coerceIn(1, 200)
            val modeloArquivo = prefs.getString(KEY_MODELO_ARQUIVO, null)
            val internoPath = prefs.getString(KEY_INTERNO_PATH, null)?.takeIf { File(it).exists() }
            val externoUriStr = prefs.getString(KEY_EXTERNO_URI, null)
            val externoNome = prefs.getString(KEY_EXTERNO_NOME, "") ?: ""
            val labelsJson = prefs.getString(KEY_LABELS, null)

            val labelsPadrao = LABELS_DOENCA_CACAU + listOf("cacau", "fruto")
            val labels = if (!labelsJson.isNullOrBlank()) {
                try {
                    val arr = JSONArray(labelsJson)
                    (0 until arr.length()).map { arr.getString(it) }
                } catch (_: Exception) {
                    labelsPadrao
                }
            } else {
                labelsPadrao
            }

            val usaCustom = !internoPath.isNullOrBlank() || !externoUriStr.isNullOrBlank()
            val modeloEmbutido = if (!usaCustom) {
                MODELOS_EMBUTIDOS.firstOrNull { it.nomeArquivo == modeloArquivo }
                    ?: MODELOS_EMBUTIDOS.firstOrNull()
            } else {
                null
            }

            val externoUri = if (internoPath.isNullOrBlank() && !externoUriStr.isNullOrBlank()) {
                safeParseUri(externoUriStr)
            } else null

            val usarDiagSecundario = prefs.getBoolean(KEY_USAR_DIAG_SECUNDARIO, true)
            val modoIASalvo = prefs.getString(KEY_MODO_IA, null)
            val modoIA = when {
                usaCustom -> ModoIACacau.SOMENTE_CONTAGEM
                !modoIASalvo.isNullOrBlank() -> ModoIACacau.fromName(modoIASalvo)
                usarDiagSecundario -> ModoIACacau.COMBINADO_2_IAS
                modeloEmbutido?.nomeArquivo == "detector.tflite" -> ModoIACacau.SOMENTE_CONTAGEM
                else -> ModoIACacau.SOMENTE_DOENCAS
            }
            val modeloSecundario = prefs.getString(KEY_MODELO_SECUNDARIO, "detector_doenca.tflite")
                ?.takeIf { it.isNotBlank() } ?: "detector_doenca.tflite"

            ModelConfig(
                modeloSelecionado = modeloEmbutido,
                modeloInternoPath = internoPath,
                modeloExternoUri = externoUri,
                modeloExternoNome = externoNome,
                labelsFiltro = labels,
                threshold = threshold,
                thresholdDoenca = thresholdDoenca,
                maxResultados = maxResultados,
                usarDiagnosticoSecundario = modoIA == ModoIACacau.COMBINADO_2_IAS && !usaCustom,
                modeloSecundarioArquivo = modeloSecundario,
                modoIA = modoIA
            )
        } catch (e: Exception) {
            Log.e("PersistenciaLocal", "Erro ao carregar configuracao do modelo: ${e.message}")
            ModelConfig(modeloSelecionado = MODELOS_EMBUTIDOS.firstOrNull())
        }
    }

    // ── PARÂMETROS DE RENDIMENTO (@ / kg) E CONFIGURAÇÃO DO ENSAIO ──

    private const val PREFS_ENSAIO = "cacau_ensaio_prefs"

    fun salvarConfigRendimento(context: Context, cfg: ConfigRendimentoCacau) {
        try {
            context.getSharedPreferences(PREFS_ENSAIO, Context.MODE_PRIVATE).edit()
                .putFloat("rend_indice_fruto", cfg.indiceFruto.toFloat().coerceAtLeast(1f))
                .putFloat("rend_kg_arroba", cfg.kgPorArroba.toFloat().coerceAtLeast(1f))
                .putInt("rend_plantas_ha", cfg.plantasPorHa.coerceAtLeast(0))
                .putBoolean("rend_descontar_doencas", cfg.descontarDoencas)
                .apply()
        } catch (_: Exception) {}
    }

    fun carregarConfigRendimento(context: Context): ConfigRendimentoCacau {
        return try {
            val p = context.getSharedPreferences(PREFS_ENSAIO, Context.MODE_PRIVATE)
            ConfigRendimentoCacau(
                indiceFruto = p.getFloat("rend_indice_fruto", ConfigRendimentoCacau.INDICE_FRUTO_PADRAO.toFloat()).toDouble().coerceAtLeast(1.0),
                kgPorArroba = p.getFloat("rend_kg_arroba", ConfigRendimentoCacau.KG_POR_ARROBA.toFloat()).toDouble().coerceAtLeast(1.0),
                plantasPorHa = p.getInt("rend_plantas_ha", ConfigRendimentoCacau.PLANTAS_POR_HA_PADRAO).coerceAtLeast(0),
                descontarDoencas = p.getBoolean("rend_descontar_doencas", true)
            )
        } catch (_: Exception) {
            ConfigRendimentoCacau()
        }
    }

    fun isTutorialVisto(context: Context): Boolean = try {
        context.getSharedPreferences(PREFS_ENSAIO, Context.MODE_PRIVATE).getBoolean("tutorial_visto", false)
    } catch (_: Exception) { false }

    fun marcarTutorialVisto(context: Context) {
        try {
            context.getSharedPreferences(PREFS_ENSAIO, Context.MODE_PRIVATE).edit()
                .putBoolean("tutorial_visto", true)
                .apply()
        } catch (_: Exception) {}
    }

    fun salvarConfigEnsaio(context: Context, cfg: ConfigEnsaioLocal) {
        try {
            val norm = cfg.copy(
                ensaioId = safeEnsaioId(cfg.ensaioId),
                codigoEnsaio = cfg.codigoEnsaio.trim().ifBlank { "TC-1801" }
            )
            context.getSharedPreferences(PREFS_ENSAIO, Context.MODE_PRIVATE).edit()
                .putString("ensaio_id", norm.ensaioId)
                .putString("codigo_ensaio", norm.codigoEnsaio)
                .putString("label_clone", norm.labelClone.ifBlank { "Clone" })
                .putString("label_parcela", norm.labelParcela.ifBlank { "Parcela" })
                .putString("label_planta", norm.labelPlanta.ifBlank { "Planta" })
                .putBoolean("num_parcela", norm.isNumParcela)
                .putBoolean("num_planta", norm.isNumPlanta)
                .putBoolean("auto_avancar_planta", norm.autoAvancarPlanta)
                .apply()
            // Mantém compatibilidade com CacauSyncClient
            context.getSharedPreferences("cacausync_prefs", Context.MODE_PRIVATE).edit()
                .putString("codigo_ensaio", norm.codigoEnsaio)
                .apply()
            salvarMetaEnsaioArquivo(context, norm)
        } catch (_: Exception) {}
    }

    fun carregarConfigEnsaio(context: Context): ConfigEnsaioLocal {
        return try {
            val p = context.getSharedPreferences(PREFS_ENSAIO, Context.MODE_PRIVATE)
            val syncPrefs = context.getSharedPreferences("cacausync_prefs", Context.MODE_PRIVATE)
            val defaultCod = syncPrefs.getString("codigo_ensaio", "TC-1801") ?: "TC-1801"
            ConfigEnsaioLocal(
                ensaioId = safeEnsaioId(p.getString("ensaio_id", "ensaio_padrao") ?: "ensaio_padrao"),
                codigoEnsaio = (p.getString("codigo_ensaio", defaultCod) ?: defaultCod).ifBlank { "TC-1801" },
                labelClone = p.getString("label_clone", "Clone") ?: "Clone",
                labelParcela = p.getString("label_parcela", "Parcela") ?: "Parcela",
                labelPlanta = p.getString("label_planta", "Planta") ?: "Planta",
                isNumParcela = p.getBoolean("num_parcela", true),
                isNumPlanta = p.getBoolean("num_planta", true),
                autoAvancarPlanta = p.getBoolean("auto_avancar_planta", true)
            )
        } catch (_: Exception) {
            ConfigEnsaioLocal()
        }
    }

    private fun salvarMetaEnsaioArquivo(context: Context, cfg: ConfigEnsaioLocal) {
        try {
            garantirDirEnsaios(context)
            val obj = JSONObject().apply {
                put("ensaioId", safeEnsaioId(cfg.ensaioId))
                put("codigoEnsaio", cfg.codigoEnsaio)
                put("labelClone", cfg.labelClone)
                put("labelParcela", cfg.labelParcela)
                put("labelPlanta", cfg.labelPlanta)
                put("isNumParcela", cfg.isNumParcela)
                put("isNumPlanta", cfg.isNumPlanta)
                put("autoAvancarPlanta", cfg.autoAvancarPlanta)
                put("atualizadoEm", System.currentTimeMillis())
            }
            escreverAtomico(context, arquivoEnsaioRelativo(cfg.ensaioId, "meta.json"), obj.toString())
        } catch (_: Exception) {}
    }

    private fun lerMetaEnsaioArquivo(context: Context, ensaioId: String): Pair<ConfigEnsaioLocal, Long>? {
        val raw = lerAtomico(context, arquivoEnsaioRelativo(ensaioId, "meta.json")) ?: return null
        return try {
            val o = JSONObject(raw)
            val cfg = ConfigEnsaioLocal(
                ensaioId = safeEnsaioId(o.optString("ensaioId", ensaioId)),
                codigoEnsaio = o.optString("codigoEnsaio", "TC-1801").ifBlank { "TC-1801" },
                labelClone = o.optString("labelClone", "Clone").ifBlank { "Clone" },
                labelParcela = o.optString("labelParcela", "Parcela").ifBlank { "Parcela" },
                labelPlanta = o.optString("labelPlanta", "Planta").ifBlank { "Planta" },
                isNumParcela = o.optBoolean("isNumParcela", true),
                isNumPlanta = o.optBoolean("isNumPlanta", true),
                autoAvancarPlanta = o.optBoolean("autoAvancarPlanta", true)
            )
            cfg to o.optLong("atualizadoEm", System.currentTimeMillis())
        } catch (_: Exception) {
            null
        }
    }

    fun listarEnsaiosSalvos(context: Context): List<ResumoEnsaioLocal> {
        val ativo = carregarConfigEnsaio(context)
        migrarLegadoSeNecessario(context, ativo.ensaioId)
        salvarMetaEnsaioArquivo(context, ativo)

        val dir = File(context.filesDir, DIR_ENSAIOS)
        if (!dir.exists() || !dir.isDirectory) return emptyList()
        val ids = dir.listFiles()
            ?.mapNotNull { f ->
                when {
                    f.name.endsWith("_meta.json") -> f.name.removeSuffix("_meta.json")
                    f.name.endsWith("_registros.json") -> f.name.removeSuffix("_registros.json")
                    else -> null
                }
            }
            ?.distinct()
            .orEmpty()

        return ids.mapNotNull { id ->
            val meta = lerMetaEnsaioArquivo(context, id)
            val cfg = meta?.first ?: (if (id == ativo.ensaioId) ativo else null) ?: return@mapNotNull null
            val regs = lerAtomico(context, arquivoEnsaioRelativo(id, "registros.json"))?.let {
                runCatching { JSONArray(it).length() }.getOrDefault(0)
            } ?: 0
            val pontos = lerAtomico(context, arquivoEnsaioRelativo(id, "rota.json"))?.let {
                runCatching {
                    val root = JSONObject(it)
                    root.optJSONArray("itens")?.length() ?: 0
                }.getOrDefault(0)
            } ?: 0
            ResumoEnsaioLocal(
                id = id,
                codigo = cfg.codigoEnsaio,
                totalRegistros = regs,
                totalPontosRota = pontos,
                atualizadoEm = meta?.second ?: File(dir, "${id}_meta.json").lastModified()
            )
        }.sortedByDescending { it.atualizadoEm }
    }

    fun criarNovoEnsaioIsolado(
        context: Context,
        codigoEnsaio: String,
        manterRotaAtual: Boolean
    ): ConfigEnsaioLocal {
        val atual = carregarConfigEnsaio(context)
        migrarLegadoSeNecessario(context, atual.ensaioId)
        salvarMetaEnsaioArquivo(context, atual)

        val rotaAtual = if (manterRotaAtual) carregarRotaPrecarregada(context).second else emptyList()
        var novoId = "ens_${System.currentTimeMillis()}"
        garantirDirEnsaios(context)
        while (File(context.filesDir, arquivoEnsaioRelativo(novoId, "meta.json")).exists()) {
            novoId = "ens_${System.currentTimeMillis()}_${(10..99).random()}"
        }

        val novoCfg = atual.copy(
            ensaioId = novoId,
            codigoEnsaio = codigoEnsaio.trim().ifBlank { "TC-1801" }
        )
        salvarConfigEnsaio(context, novoCfg)
        escreverAtomico(context, arquivoEnsaioRelativo(novoId, "registros.json"), "[]")
        escreverAtomico(context, FILE_NAME, "[]")

        if (manterRotaAtual && rotaAtual.isNotEmpty()) {
            val limpa = rotaAtual.map { it.copy(coletado = false, qtdFrutos = "0") }
            salvarRotaPrecarregada(context, limpa, novoCfg.codigoEnsaio)
        } else {
            limparRotaPrecarregada(context)
        }
        fotosSyncExecutor.execute { limparFotosSync(context) }
        return novoCfg
    }

    fun alternarEnsaioAtivo(context: Context, ensaioId: String): ConfigEnsaioLocal? {
        val atual = carregarConfigEnsaio(context)
        salvarMetaEnsaioArquivo(context, atual)

        val idLimpo = safeEnsaioId(ensaioId)
        val meta = lerMetaEnsaioArquivo(context, idLimpo)?.first ?: return null
        salvarConfigEnsaio(context, meta)

        val regJson = lerAtomico(context, arquivoEnsaioRelativo(idLimpo, "registros.json")) ?: "[]"
        escreverAtomico(context, FILE_NAME, regJson)
        val regsAtivos = runCatching { deserializarRegistrosDeJson(regJson) }.getOrDefault(emptyList())
        fotosSyncExecutor.execute {
            limparFotosSync(context)
        }
        espelharFotosSync(context, regsAtivos)

        val rotaJson = lerAtomico(context, arquivoEnsaioRelativo(idLimpo, "rota.json"))
        if (rotaJson != null) {
            File(context.filesDir, ROTA_FILE_NAME).writeText(rotaJson, Charsets.UTF_8)
        } else {
            File(context.filesDir, ROTA_FILE_NAME).delete()
        }
        return meta
    }

    // ── ROTA FIELD BOOK PRÉ-CARREGADA (ESPELHO USB ADB) ──

    fun obterTimestampRotaPrecarregada(context: Context): Long {
        return try {
            val f = File(context.filesDir, ROTA_FILE_NAME)
            if (f.exists() && f.length() > 2L) f.lastModified() else 0L
        } catch (_: Exception) {
            0L
        }
    }

    fun salvarRotaPrecarregada(context: Context, rota: List<RotaColeta>, codigoEnsaio: String = "TC-1801") {
        val snapshot = rota.toList()
        val cfg = carregarConfigEnsaio(context)
        synchronized(fileLock) {
            try {
                val arr = JSONArray()
                for ((idx, item) in snapshot.withIndex()) {
                    arr.put(
                        JSONObject().apply {
                            put("ordem", if (item.ordem > 0) item.ordem else (idx + 1))
                            put("clone", item.clone)
                            put("parcela", item.parcela)
                            put("planta", item.planta)
                            put("coletado", item.coletado)
                            put("qtdFrutos", item.qtdFrutos)
                        }
                    )
                }
                val root = JSONObject().apply {
                    put("codigoEnsaio", codigoEnsaio.ifBlank { "TC-1801" })
                    put("atualizadoEm", System.currentTimeMillis())
                    put("itens", arr)
                }
                val json = root.toString()
                File(context.filesDir, ROTA_FILE_NAME).writeText(json, Charsets.UTF_8)
                garantirDirEnsaios(context)
                escreverAtomico(context, arquivoEnsaioRelativo(cfg.ensaioId, "rota.json"), json)
            } catch (e: Exception) {
                Log.e("PersistenciaLocal", "Erro ao salvar rota precarregada: ${e.message}", e)
            }
        }
    }

    fun limparRotaPrecarregada(context: Context) {
        val cfg = carregarConfigEnsaio(context)
        synchronized(fileLock) {
            try {
                val f = File(context.filesDir, ROTA_FILE_NAME)
                if (f.exists()) f.delete()
                val fe = File(context.filesDir, arquivoEnsaioRelativo(cfg.ensaioId, "rota.json"))
                if (fe.exists()) fe.delete()
                File(context.filesDir, arquivoEnsaioRelativo(cfg.ensaioId, "rota.json") + ".bak").delete()
            } catch (_: Exception) {}
        }
    }

    fun carregarRotaPrecarregada(context: Context): Pair<String, List<RotaColeta>> {
        synchronized(fileLock) {
            try {
                val f = File(context.filesDir, ROTA_FILE_NAME)
                if (!f.exists() || f.length() == 0L) return "" to emptyList()
                val texto = f.readText(Charsets.UTF_8).trim()
                return deserializarRotaDeJson(texto)
            } catch (e: Exception) {
                Log.e("PersistenciaLocal", "Erro ao carregar rota precarregada: ${e.message}", e)
                return "" to emptyList()
            }
        }
    }

    fun deserializarRotaDeJson(jsonTexto: String): Pair<String, List<RotaColeta>> {
        if (jsonTexto.isBlank()) return "" to emptyList()
        var codigoEnsaio = ""
        val arr = if (jsonTexto.startsWith("{")) {
            val root = JSONObject(jsonTexto)
            codigoEnsaio = root.optString("codigoEnsaio", "")
            root.optJSONArray("itens") ?: JSONArray()
        } else {
            JSONArray(jsonTexto)
        }

        val lista = mutableListOf<RotaColeta>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val clone = o.optString("clone", "").trim()
            val parcela = o.optString("parcela", "1").trim().ifBlank { "1" }
            val planta = o.optInt("planta", 1).coerceAtLeast(1)
            val ordem = o.optInt("ordem", i + 1).coerceAtLeast(1)
            val coletado = o.optBoolean("coletado", false)
            val qtdFrutos = o.optString("qtdFrutos", "0")
            if (clone.isNotBlank()) {
                lista.add(
                    RotaColeta(
                        clone = clone,
                        parcela = parcela,
                        planta = planta,
                        coletado = coletado,
                        qtdFrutos = qtdFrutos,
                        ordem = ordem
                    )
                )
            }
        }
        return codigoEnsaio to lista
    }
}
