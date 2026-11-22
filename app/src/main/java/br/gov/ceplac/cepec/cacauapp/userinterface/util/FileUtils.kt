package br.gov.ceplac.cepec.cacauapp.userinterface.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import br.gov.ceplac.cepec.cacauapp.data.ConfigRendimentoCacau
import br.gov.ceplac.cepec.cacauapp.data.RegistroCacau
import br.gov.ceplac.cepec.cacauapp.data.calcularRankingClonesCacau
import br.gov.ceplac.cepec.cacauapp.network.CacauSyncClient
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

fun carregarDadosDeUri(context: Context, uri: Uri): Pair<Map<String, String>, String> {
    val mapa = mutableMapOf<String, String>()
    val nome = "Planilha"
    try {
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            val reader = BufferedReader(InputStreamReader(inputStream))
            reader.readLine()
            var linha = reader.readLine()
            while (linha != null) {
                val colunas = linha.split(",")
                if (colunas.isNotEmpty()) mapa[colunas[0]] = linha
                linha = reader.readLine()
            }
        }
    } catch (e: Exception) {
        Log.e("CSV", "Erro: ${e.message}")
    }
    return Pair(mapa, nome)
}

fun copiarUriGaleriaLocal(context: Context, uriOrigem: Uri): File? {
    return try {
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: context.filesDir
        val arquivoDestino = File(dir, "CACAU_GAL_${System.currentTimeMillis()}.jpg")
        context.contentResolver.openInputStream(uriOrigem)?.use { input ->
            arquivoDestino.outputStream().use { output -> input.copyTo(output) }
        }
        if (arquivoDestino.exists() && arquivoDestino.length() > 0L) arquivoDestino else null
    } catch (_: Exception) {
        null
    }
}

fun gerarCsvColetaCacauString(
    registros: List<RegistroCacau>,
    l1: String = "Clone",
    l2: String = "Parcela",
    l3: String = "Planta"
): String = buildString {
    val header = listOf(
        "Campanha", "ID_Gerado", l1, l2, l3,
        "Resultado", "Data", "Lat", "Lng",
        "Total_Frutos", "Sadios", "Vassoura", "Podridao", "Nao_Avaliavel", "Deteccoes_YOLO"
    ).joinToString(",") { csvField(it) }
    appendLine(header)
    registros.forEach { reg ->
        val itemPrincipal = reg.plantId.split("_").firstOrNull() ?: ""
        appendLine(formatarLinhaCsvPersonalizado(reg, itemPrincipal))
    }
}

fun gerarCsvRankingClonesCacau(
    registros: List<RegistroCacau>,
    configRendimento: ConfigRendimentoCacau = ConfigRendimentoCacau()
): String {
    val ranking = calcularRankingClonesCacau(registros, configRendimento)
    if (ranking.isEmpty()) return ""
    return buildString {
        appendLine("Posicao,Clone,Plantas_Avaliadas,Sobrevivencia_Pct,Total_Frutos,Sadios,Vassoura,Podridao,Infeccao_Pct,Media_Sadios_Planta,Kg_Estimado,Arrobas_Estimadas,Kg_Ha,Arrobas_Ha,Perda_Doencas_Arrobas_Ha")
        ranking.forEachIndexed { idx, r ->
            appendLine(
                listOf(
                    idx + 1,
                    r.codigoClone,
                    r.plantasColetaveis,
                    String.format(Locale.US, "%.1f", r.taxaSobrevivenciaPercentual),
                    r.totalFrutos,
                    r.sadios,
                    r.vassoura,
                    r.podridao,
                    String.format(Locale.US, "%.1f", r.taxaInfeccaoPercentual),
                    String.format(Locale.US, "%.2f", r.mediaSadiosPlanta),
                    String.format(Locale.US, "%.2f", r.kgAcumulado),
                    String.format(Locale.US, "%.3f", r.arrobasAcumuladas),
                    String.format(Locale.US, "%.1f", r.kgHa),
                    String.format(Locale.US, "%.2f", r.arrobasHa),
                    String.format(Locale.US, "%.2f", r.perdaArrobasHa)
                ).joinToString(",") { csvField(it) }
            )
        }
    }
}

fun gerarArquivoCsvParaCompartilhar(
    context: Context,
    registros: List<RegistroCacau>,
    ensaio: String,
    l1: String = "Clone",
    l2: String = "Parcela",
    l3: String = "Planta"
): File? {
    return try {
        val safeEnsaio = ensaio.ifBlank { "TC-1801" }.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val nomeFinal = "CacauApp_${safeEnsaio}_${System.currentTimeMillis()}.csv"
        val conteudo = gerarCsvColetaCacauString(registros, l1, l2, l3)
        val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        val payload = bom + conteudo.toByteArray(StandardCharsets.UTF_8)

        val cacheDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val cacheFile = File(cacheDir, nomeFinal).apply { writeBytes(payload) }
        salvarArquivoEmDownloadsCEPEC(
            context = context,
            nomeArquivo = nomeFinal,
            mimeType = "text/csv",
            payload = payload,
            cacheFallback = cacheFile,
            mensagemSucesso = "Planilha salva em Downloads/CEPEC!"
        )
    } catch (e: Exception) {
        mostrarToastSeguro(context, "Erro ao gerar CSV: ${e.message}", Toast.LENGTH_LONG)
        null
    }
}

fun exportarPacoteZipCacau(
    context: Context,
    registros: List<RegistroCacau>,
    ensaio: String,
    configRendimento: ConfigRendimentoCacau = ConfigRendimentoCacau(),
    l1: String = "Clone",
    l2: String = "Parcela",
    l3: String = "Planta"
): File? {
    return try {
        val safeEnsaio = ensaio.ifBlank { "TC-1801" }.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val nomeZip = "CacauApp_Pacote_${safeEnsaio}_${System.currentTimeMillis()}.zip"
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val zipFile = File(dir, nomeZip)

        val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        val csvColeta = gerarCsvColetaCacauString(registros, l1, l2, l3)
        val csvRanking = gerarCsvRankingClonesCacau(registros, configRendimento)

        val nomesUsados = mutableSetOf<String>()
        fun nomeUnico(base: String): String {
            val limpo = base.ifBlank { "planta" }.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            var cand = limpo
            var n = 2
            while (!nomesUsados.add(cand.lowercase(Locale.ROOT))) {
                cand = "${limpo}_$n"
                n++
            }
            return cand
        }

        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            zos.putNextEntry(ZipEntry("coleta_${safeEnsaio}.csv"))
            zos.write(bom + csvColeta.toByteArray(StandardCharsets.UTF_8))
            zos.closeEntry()

            if (csvRanking.isNotBlank()) {
                zos.putNextEntry(ZipEntry("ranking_clones_${safeEnsaio}.csv"))
                zos.write(bom + csvRanking.toByteArray(StandardCharsets.UTF_8))
                zos.closeEntry()
            }

            registros.forEach { reg ->
                if (reg.uri != Uri.EMPTY) {
                    abrirFluxoFotoUri(context, reg.uri)?.use { fotoStream ->
                        val base = nomeUnico(reg.plantId)
                        zos.putNextEntry(ZipEntry("fotos/${base}.jpg"))
                        fotoStream.copyTo(zos, 8192)
                        zos.closeEntry()
                    }
                }
            }
        }

        salvarArquivoFileEmDownloadsCEPEC(
            context = context,
            nomeArquivo = nomeZip,
            mimeType = "application/zip",
            origemFile = zipFile,
            cacheFallback = zipFile,
            mensagemSucesso = "Pacote ZIP salvo em Downloads/CEPEC!"
        )
    } catch (e: Exception) {
        mostrarToastSeguro(context, "Erro ao gerar ZIP: ${e.message}", Toast.LENGTH_LONG)
        null
    }
}

private fun abrirFluxoFotoUri(context: Context, uri: Uri): InputStream? {
    return try {
        context.contentResolver.openInputStream(uri)
            ?: uri.path?.let { p -> File(p).takeIf { it.exists() }?.inputStream() }
    } catch (_: Exception) {
        try {
            uri.path?.let { p -> File(p).takeIf { it.exists() }?.inputStream() }
        } catch (_: Exception) {
            null
        }
    }
}

private fun salvarArquivoEmDownloadsCEPEC(
    context: Context,
    nomeArquivo: String,
    mimeType: String,
    payload: ByteArray,
    cacheFallback: File?,
    mensagemSucesso: String
): File? {
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, nomeArquivo)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/CEPEC")
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            if (uri != null) {
                context.contentResolver.openOutputStream(uri)?.use { out -> out.write(payload) }
                mostrarToastSeguro(context, mensagemSucesso, Toast.LENGTH_SHORT)
                return cacheFallback
            }
        }
        val downloadDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "CEPEC")
        if (!downloadDir.exists()) downloadDir.mkdirs()
        val file = File(downloadDir, nomeArquivo)
        FileOutputStream(file).use { out -> out.write(payload) }
        mostrarToastSeguro(context, mensagemSucesso, Toast.LENGTH_SHORT)
        return cacheFallback ?: file
    } catch (e: Exception) {
        mostrarToastSeguro(context, "Erro ao salvar: ${e.message}", Toast.LENGTH_LONG)
        return cacheFallback
    }
}

private fun salvarArquivoFileEmDownloadsCEPEC(
    context: Context,
    nomeArquivo: String,
    mimeType: String,
    origemFile: File,
    cacheFallback: File?,
    mensagemSucesso: String
): File? {
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, nomeArquivo)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/CEPEC")
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            if (uri != null) {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    origemFile.inputStream().use { input -> input.copyTo(out) }
                }
                mostrarToastSeguro(context, mensagemSucesso, Toast.LENGTH_SHORT)
                return cacheFallback
            }
        }
        val downloadDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "CEPEC")
        if (!downloadDir.exists()) downloadDir.mkdirs()
        val file = File(downloadDir, nomeArquivo)
        origemFile.inputStream().use { input ->
            FileOutputStream(file).use { out -> input.copyTo(out) }
        }
        mostrarToastSeguro(context, mensagemSucesso, Toast.LENGTH_SHORT)
        return cacheFallback ?: file
    } catch (e: Exception) {
        mostrarToastSeguro(context, "Erro ao salvar: ${e.message}", Toast.LENGTH_LONG)
        return cacheFallback
    }
}

private fun mostrarToastSeguro(context: Context, mensagem: String, duracao: Int) {
    try {
        val mainLooper = Looper.getMainLooper() ?: return
        if (Looper.myLooper() == mainLooper) {
            Toast.makeText(context, mensagem, duracao).show()
        } else {
            Handler(mainLooper).post {
                try {
                    Toast.makeText(context, mensagem, duracao).show()
                } catch (_: Throwable) {}
            }
        }
    } catch (_: Throwable) {}
}

fun compartilharArquivoViaAndroid(
    context: Context,
    arquivo: File,
    mimeType: String,
    tituloChooser: String
) {
    try {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", arquivo)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, tituloChooser))
    } catch (e: Exception) {
        mostrarToastSeguro(context, "Erro ao compartilhar arquivo: ${e.message}", Toast.LENGTH_LONG)
    }
}

fun compartilharTextoViaAndroid(
    context: Context,
    texto: String,
    tituloChooser: String
) {
    try {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, texto)
        }
        context.startActivity(Intent.createChooser(intent, tituloChooser))
    } catch (e: Exception) {
        mostrarToastSeguro(context, "Erro ao compartilhar resumo: ${e.message}", Toast.LENGTH_LONG)
    }
}

fun exportarCSVFinal(context: Context, registros: List<RegistroCacau>, ensaio: String, parcela: String) {
    val nomeFinal = "Relatorio_Cacau_${ensaio}.csv"
    val contentValues = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, nomeFinal)
        put(MediaStore.MediaColumns.MIME_TYPE, "text/csv")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/CEPEC_Relatorios")
        }
    }
    val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) MediaStore.Downloads.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    val uri = context.contentResolver.insert(collection, contentValues)
    uri?.let {
        context.contentResolver.openOutputStream(it)?.use { out ->
            out.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
            val writer = out.bufferedWriter()
            writer.write("Ensaio,Parcela,Plant_ID,Diagnostico,Data,Lat,Lng,Total_Frutos,Sadios,Vassoura,Podridao,Nao_Avaliavel,Deteccoes_YOLO")
            writer.newLine()
            registros.forEach { reg ->
                val detJson = PersistenciaLocalRegistros.serializarCaixasParaCsv(reg.caixas).replace("\"", "\"\"")
                val detCol = if (detJson.isNotEmpty()) "\"$detJson\"" else ""
                writer.write("${ensaio},${parcela},${reg.plantId},${reg.diagnostico},${reg.data},${reg.lat},${reg.lng},${reg.totalFrutos},${reg.sadios},${reg.vassoura},${reg.podridao},${reg.naoAvaliavel},${detCol}")
                writer.newLine()
            }
            writer.flush()
            mostrarToastSeguro(context, "Exportado para Downloads!", Toast.LENGTH_LONG)
        }
    }
}

fun limparSimbologiaPura(texto: String): String {
    if (texto.isEmpty()) return ""
    return texto.replace("\uFEFF", "").replace("\uFFFD", "").replace("ï»¿", "")
        .replace("[^\\x20-\\x7E\\u00A0-\\u00FF]".toRegex(), "").trim()
}

fun fatiarColunasCampos(linha: String): List<String> {
    if (linha.isBlank()) return emptyList()
    val divisor = if (linha.contains(";")) ';' else ','
    val resultado = mutableListOf<String>()
    val sb = StringBuilder()
    var dentroAspas = false
    val limpa = linha.replace("\r", "").replace("\n", "")
    for (i in limpa.indices) {
        val c = limpa[i]
        if (c == '"') {
            dentroAspas = !dentroAspas
        } else if (c == divisor && !dentroAspas) {
            resultado.add(sb.toString().trim())
            sb.clear()
        } else {
            sb.append(c)
        }
    }
    resultado.add(sb.toString().trim())
    return resultado
}

internal fun csvField(value: Any?): String {
    val text = value?.toString() ?: ""
    return if (text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r")) {
        "\"" + text.replace("\"", "\"\"") + "\""
    } else {
        text
    }
}

internal fun formatarLinhaCsvPersonalizado(
    reg: RegistroCacau,
    itemPrincipal: String = reg.plantId.split("_").firstOrNull() ?: ""
): String {
    val detJson = PersistenciaLocalRegistros.serializarCaixasParaCsv(reg.caixas)
    val resultadoCsv = if (CacauSyncClient.isNaoColetavel(reg.diagnostico)) {
        "Não Coletável"
    } else {
        reg.totalFrutos.toString()
    }
    return listOf(
        reg.ensaioManual, reg.plantId, itemPrincipal, reg.parcelaManual,
        reg.numPlanta, resultadoCsv, reg.data, reg.lat, reg.lng,
        reg.totalFrutos, reg.sadios, reg.vassoura, reg.podridao, reg.naoAvaliavel, detJson
    ).joinToString(",") { csvField(it) }
}

fun exportarCSVCEPEC_Personalizado(
    context: Context,
    registros: List<RegistroCacau>,
    l1: String,
    l2: String,
    l3: String
) {
    val timestamp = System.currentTimeMillis()
    val nomeFinal = "Coleta_Custom_${timestamp}.csv"
    val conteudo = buildString {
        val header = listOf(
            "Campanha", "ID_Gerado", l1, l2, l3,
            "Resultado", "Data", "Lat", "Lng",
            "Total_Frutos", "Sadios", "Vassoura", "Podridao", "Nao_Avaliavel", "Deteccoes_YOLO"
        ).joinToString(",") { csvField(it) }
        appendLine(header)
        registros.forEach { reg ->
            val itemPrincipal = reg.plantId.split("_").firstOrNull() ?: ""
            appendLine(formatarLinhaCsvPersonalizado(reg, itemPrincipal))
        }
    }

    try {
        val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        val payload = bom + conteudo.toByteArray(StandardCharsets.UTF_8)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, nomeFinal)
                put(MediaStore.MediaColumns.MIME_TYPE, "text/csv")
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/CEPEC")
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            if (uri != null) {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(payload)
                }
                mostrarToastSeguro(context, "Planilha exportada em Downloads/CEPEC!", Toast.LENGTH_SHORT)
                return
            }
        }
        val downloadDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "CEPEC")
        if (!downloadDir.exists()) downloadDir.mkdirs()
        val file = File(downloadDir, nomeFinal)
        FileOutputStream(file).use { out ->
            out.write(payload)
        }
        mostrarToastSeguro(context, "Planilha exportada em Downloads/CEPEC!", Toast.LENGTH_SHORT)
    } catch (e: Exception) {
        mostrarToastSeguro(context, "Erro ao exportar CSV: ${e.message}", Toast.LENGTH_LONG)
    }
}
