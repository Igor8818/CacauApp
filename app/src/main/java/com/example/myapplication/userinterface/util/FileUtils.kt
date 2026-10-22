package com.example.myapplication.userinterface.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.util.Xml
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.myapplication.data.OcorrenciaCampo
import com.example.myapplication.data.RegistroCacau
import com.example.myapplication.data.RotaColeta
import com.example.myapplication.data.SituacaoPlanta
import com.example.myapplication.data.comoObservacao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.xmlpull.v1.XmlPullParser
import java.io.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.zip.ZipInputStream

/**
 * Lê todas as linhas de uma planilha CSV ou Excel (.xlsx) apontada por [uri].
 * Se o arquivo for um .xlsx (formato ZIP OpenXML do Excel), extrai automaticamente
 * as strings compartilhadas e a aba de dados sem precisar converter para CSV.
 */
fun lerLinhasPlanilha(context: Context, uri: Uri): List<String> {
    return try {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return emptyList()
        if (bytes.size >= 4 &&
            bytes[0] == 0x50.toByte() &&
            bytes[1] == 0x4B.toByte() &&
            bytes[2] == 0x03.toByte() &&
            bytes[3] == 0x04.toByte()
        ) {
            extrairLinhasDeXlsx(bytes)
        } else {
            BufferedReader(InputStreamReader(ByteArrayInputStream(bytes), StandardCharsets.UTF_8)).use { it.readLines() }
        }
    } catch (e: Exception) {
        Log.e("FileUtils", "Erro ao ler planilha: ${e.message}")
        emptyList()
    }
}

/**
 * Extrai as linhas da planilha Excel (.xlsx) convertendo cada linha em formato delimitado por ';'.
 * Prioriza a aba que contiver colunas "Clone"/"Parcela"/"Planta", ou usa a primeira aba.
 */
private fun extrairLinhasDeXlsx(xlsxBytes: ByteArray): List<String> {
    val sharedStrings = mutableListOf<String>()
    val sheetsBytes = sortedMapOf<String, ByteArray>()

    ZipInputStream(ByteArrayInputStream(xlsxBytes)).use { zis ->
        while (true) {
            val entry = zis.nextEntry ?: break
            val name = entry.name
            when {
                name.equals("xl/sharedStrings.xml", ignoreCase = true) -> {
                    val xmlBytes = zis.readBytes()
                    sharedStrings.addAll(parseSharedStringsXml(xmlBytes))
                }
                name.startsWith("xl/worksheets/sheet", ignoreCase = true) && name.endsWith(".xml", ignoreCase = true) -> {
                    sheetsBytes[name] = zis.readBytes()
                }
            }
            zis.closeEntry()
        }
    }

    if (sheetsBytes.isEmpty()) return emptyList()

    var melhorAba: List<String> = emptyList()
    for ((_, sheetXml) in sheetsBytes) {
        val linhas = parseWorksheetXmlParaCsv(sheetXml, sharedStrings)
        if (linhas.isNotEmpty()) {
            val cab = linhas.first().lowercase()
            if (cab.contains("clone") && (cab.contains("parcela") || cab.contains("planta"))) {
                return linhas
            }
            if (melhorAba.isEmpty()) {
                melhorAba = linhas
            }
        }
    }
    return melhorAba
}

private fun parseSharedStringsXml(xmlBytes: ByteArray): List<String> {
    val resultado = mutableListOf<String>()
    val parser = Xml.newPullParser()
    parser.setInput(ByteArrayInputStream(xmlBytes), "UTF-8")
    var dentroSi = false
    var dentroT = false
    val sb = StringBuilder()

    var event = parser.eventType
    while (event != XmlPullParser.END_DOCUMENT) {
        when (event) {
            XmlPullParser.START_TAG -> {
                if (parser.name == "si") {
                    dentroSi = true
                    sb.setLength(0)
                } else if (dentroSi && parser.name == "t") {
                    dentroT = true
                }
            }
            XmlPullParser.TEXT -> {
                if (dentroSi && dentroT) {
                    sb.append(parser.text ?: "")
                }
            }
            XmlPullParser.END_TAG -> {
                if (parser.name == "t") {
                    dentroT = false
                } else if (parser.name == "si") {
                    resultado.add(sb.toString())
                    dentroSi = false
                }
            }
        }
        event = parser.next()
    }
    return resultado
}

private fun parseWorksheetXmlParaCsv(xmlBytes: ByteArray, sharedStrings: List<String>): List<String> {
    val linhas = mutableListOf<String>()
    val parser = Xml.newPullParser()
    parser.setInput(ByteArrayInputStream(xmlBytes), "UTF-8")

    var celulasLinha = mutableListOf<String>()
    var colIdxAtual = 0
    var tipoCelula = ""
    var dentroValor = false
    val valorCelula = StringBuilder()

    var event = parser.eventType
    while (event != XmlPullParser.END_DOCUMENT) {
        when (event) {
            XmlPullParser.START_TAG -> {
                when (parser.name) {
                    "row" -> {
                        celulasLinha = mutableListOf()
                        colIdxAtual = 0
                    }
                    "c" -> {
                        val ref = parser.getAttributeValue(null, "r") ?: ""
                        tipoCelula = parser.getAttributeValue(null, "t") ?: ""
                        colIdxAtual = indiceColunaExcel(ref, celulasLinha.size)
                        valorCelula.setLength(0)
                    }
                    "v", "t" -> {
                        dentroValor = true
                    }
                }
            }
            XmlPullParser.TEXT -> {
                if (dentroValor) {
                    valorCelula.append(parser.text ?: "")
                }
            }
            XmlPullParser.END_TAG -> {
                when (parser.name) {
                    "v", "t" -> dentroValor = false
                    "c" -> {
                        while (celulasLinha.size <= colIdxAtual) {
                            celulasLinha.add("")
                        }
                        val bruto = valorCelula.toString().trim()
                        val resolvido = if (tipoCelula == "s") {
                            val idx = bruto.toIntOrNull()
                            if (idx != null && idx in sharedStrings.indices) sharedStrings[idx] else bruto
                        } else {
                            bruto
                        }
                        celulasLinha[colIdxAtual] = resolvido
                    }
                    "row" -> {
                        if (celulasLinha.any { it.isNotBlank() }) {
                            linhas.add(celulasLinha.joinToString(";") { escaparECorrigirCsv(it, ";") })
                        }
                    }
                }
            }
        }
        event = parser.next()
    }
    return linhas
}

private fun indiceColunaExcel(ref: String, fallback: Int): Int {
    val letras = ref.takeWhile { it.isLetter() }.uppercase()
    if (letras.isEmpty()) return fallback
    var idx = 0
    for (c in letras) {
        idx = idx * 26 + (c - 'A' + 1)
    }
    return (idx - 1).coerceAtLeast(0)
}

/**
 * Lê apenas a primeira linha (cabeçalho) de um arquivo CSV ou Excel (.xlsx) apontado por [uri].
 */
fun lerApenasCabecalho(context: Context, uri: Uri): List<String> {
    try {
        val linhas = lerLinhasPlanilha(context, uri)
        val primeiraLinha = linhas.firstOrNull() ?: return emptyList()
        val linhaLimpa = limparBomECaracteresInvalidos(primeiraLinha)
        val separador = detectarSeparador(linhaLimpa)
        return parseLinhaCsvRfc4180(linhaLimpa, separador)
            .mapIndexed { idx, col -> col.trim().ifEmpty { "Coluna_${idx + 1}" } }
    } catch (e: Exception) {
        Log.e("FileUtils", "Erro ao ler cabecalho: ${e.message}")
    }
    return emptyList()
}

/**
 * Processa uma planilha CSV ou Excel (.xlsx) completa e retorna lista de [RotaColeta]
 * usando os índices de coluna já resolvidos ([idxClone], [idxParcela], [idxPlanta]).
 */
fun processarCSVComIndices(
    context: Context,
    uri: Uri,
    idxClone: Int,
    idxParcela: Int,
    idxPlanta: Int
): List<RotaColeta> {
    try {
        val linhas = lerLinhasPlanilha(context, uri)
        return parseLinhasCSVComIndices(linhas, idxClone, idxParcela, idxPlanta)
    } catch (e: Exception) {
        Log.e("FileUtils", "Erro ao processar planilha: ${e.message}")
    }
    return emptyList()
}

/**
 * Função pura de parsing de planilha de rota (testável sem dependência de Context/Android).
 * Preserva a ordem das linhas (ou ordena pela coluna `Ordem` quando presente) e extrai
 * metadados e coletas prévias de planilhas CEPLAC / Field Book.
 */
fun parseLinhasCSVComIndices(
    linhas: List<String>,
    idxClone: Int,
    idxParcela: Int,
    idxPlanta: Int
): List<RotaColeta> {
    if (linhas.isEmpty()) return emptyList()
    val cabecalhoBruto = limparBomECaracteresInvalidos(linhas.first())
    if (cabecalhoBruto.isBlank()) return emptyList()

    val separador = detectarSeparador(cabecalhoBruto)
    val cabecalho = parseLinhaCsvRfc4180(cabecalhoBruto, separador).map { it.trim().lowercase() }

    fun idxCol(vararg nomes: String): Int =
        cabecalho.indexOfFirst { col -> nomes.any { n -> col == n || col.replace(" ", "") == n } }

    val idxPlantId = idxCol("plant_id", "plantid", "id_planta", "id_gerado")
    val idxEnsaio = idxCol("ensaio", "campanha", "experimento")
    val idxBloco = idxCol("bloco", "block", "rep_bloco")
    val idxOrdem = idxCol("ordem", "order", "sequencia", "seq")
    val idxSit2023c = idxCol("sitpla2023c", "hist_c")
    val idxSit2023L = idxCol("sitpla2023l", "hist_l")
    val idxSitPla = idxCol("sitpla", "situacao", "sit_planta")
    val idxNFPeq = idxCol("nfpequenos")
    val idxNFMed = idxCol("nfmedios")
    val idxNFAd = idxCol("nfadultos")
    val idxNFMad = idxCol("nfmaduros")
    val idxNFVas = idxCol("nfvassoura")
    val idxNFPod = idxCol("nfpodridao")
    val idxNFOut = idxCol("nfoutrasperdas")
    val idxNVTot = idxCol("nvtotal")
    val idxCor = idxCol("corfruto")
    val idxVirus = idxCol("notavirus")

    val lista = mutableListOf<RotaColeta>()
    var sequencial = 1

    for (i in 1 until linhas.size) {
        val linhaLimpa = limparBomECaracteresInvalidos(linhas[i])
        if (linhaLimpa.isBlank()) continue
        val colunas = parseLinhaCsvRfc4180(linhaLimpa, separador)
        if (colunas.size <= maxOf(idxClone, idxParcela, idxPlanta)) continue

        fun col(idx: Int): String = if (idx in colunas.indices) colunas[idx].trim() else ""
        fun colInt(idx: Int): Int = col(idx).takeIf { it.isNotBlank() && it != "." }?.toIntOrNull() ?: 0

        val clone = col(idxClone)
        if (clone.isBlank()) continue

        val parcela = col(idxParcela).ifBlank { "1" }
        val planta = col(idxPlanta).filter { it.isDigit() }.toIntOrNull() ?: 1
        val plantIdOrig = col(idxPlantId)
        val ensaio = col(idxEnsaio)
        val bloco = col(idxBloco)
        val ordem = col(idxOrdem).filter { it.isDigit() }.toIntOrNull() ?: sequencial
        val sit2023c = col(idxSit2023c)
        val sit2023L = col(idxSit2023L)

        val sitPlaBruto = col(idxSitPla)
        val nfPeq = colInt(idxNFPeq)
        val nfMed = colInt(idxNFMed)
        val nfAd = colInt(idxNFAd)
        val nfMad = colInt(idxNFMad)
        val nfVas = colInt(idxNFVas)
        val nfPod = colInt(idxNFPod)
        val nfOut = colInt(idxNFOut)
        val nvTot = colInt(idxNVTot)
        val corBruta = col(idxCor).takeIf { it != "." }.orEmpty()
        val notaVirus = colInt(idxVirus)

        val temDadosColeta = sitPlaBruto.isNotBlank() ||
            col(idxNFPeq).isNotBlank() || col(idxNFMed).isNotBlank() ||
            col(idxNFAd).isNotBlank() || col(idxNFMad).isNotBlank() ||
            col(idxNVTot).isNotBlank() || corBruta.isNotBlank()

        val inativa = SituacaoPlanta.ehInativa(sitPlaBruto)
        val somaFrutos = nfPeq + nfMed + nfAd + nfMad

        val ocorrenciasAuto = mutableListOf<String>()
        if (nfVas > 0 || nvTot > 0) ocorrenciasAuto.add(OcorrenciaCampo.VASSOURA_BRUXA.rotulo)
        if (nfPod > 0) ocorrenciasAuto.add(OcorrenciaCampo.PODRIDAO_PARDA.rotulo)

        val sitPlaNormalizado = when {
            sitPlaBruto.equals("cavalo", ignoreCase = true) -> SituacaoPlanta.CAVALO.rotulo
            sitPlaBruto.equals("morta", ignoreCase = true) -> SituacaoPlanta.MORTA.rotulo
            sitPlaBruto.equals("errada", ignoreCase = true) -> SituacaoPlanta.ERRADA.rotulo
            sitPlaBruto.equals("normal", ignoreCase = true) -> SituacaoPlanta.NORMAL.rotulo
            temDadosColeta -> SituacaoPlanta.NORMAL.rotulo
            else -> ""
        }

        val qtdFrutosStr = when {
            inativa -> sitPlaNormalizado
            temDadosColeta -> somaFrutos.toString()
            else -> "0"
        }

        lista.add(
            RotaColeta(
                clone = clone,
                parcela = parcela,
                planta = planta,
                coletado = temDadosColeta,
                qtdFrutos = qtdFrutosStr,
                observacao = ocorrenciasAuto.comoObservacao(),
                plantIdOriginal = plantIdOrig,
                ensaio = ensaio,
                bloco = bloco,
                ordem = ordem,
                sitPla2023c = sit2023c,
                sitPla2023L = sit2023L,
                sitPla = sitPlaNormalizado,
                nfPequenos = nfPeq,
                nfMedios = nfMed,
                nfAdultos = nfAd,
                nfMaduros = nfMad,
                nfVassoura = nfVas,
                nfPodridao = nfPod,
                nfOutrasPerdas = nfOut,
                nvTotal = nvTot,
                corFruto = corBruta,
                notaVirus = notaVirus
            )
        )
        sequencial++
    }

    // Se a planilha possui coluna explícita de Ordem, garante ordenação crescente fiel ao caminhamento
    return if (idxOrdem != -1 && lista.all { it.ordem > 0 }) {
        lista.sortedBy { it.ordem }
    } else {
        lista
    }
}

/**
 * Exporta os [registros] como planilha CSV compatível com Excel pt-BR (delimitador ';' e UTF-8 + BOM)
 * e com as colunas analíticas padrão da CEPLAC / Field Book.
 */
suspend fun exportarPlanilhaCEPECAssync(
    context: Context,
    registros: List<RegistroCacau>,
    ensaio: String,
    labelColuna1: String = "Clone",
    labelColuna2: String = "Parcela",
    labelColuna3: String = "Planta",
    aoConcluir: (sucesso: Boolean, mensagem: String, uriArquivo: Uri?) -> Unit
) = withContext(Dispatchers.IO) {
    if (registros.isEmpty()) {
        withContext(Dispatchers.Main) {
            aoConcluir(false, "Nenhum registro para exportar.", null)
        }
        return@withContext
    }

    val dataHora = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.getDefault()).format(Date())
    val nomeEnsaio = limparSimbologiaPura(ensaio).ifBlank { "SemNome" }
    val nomeFinal = "Coleta_${nomeEnsaio}_${dataHora}.csv"
    val separador = ";"

    val cabecalho = gerarCabecalhoCsvCepec(labelColuna1, labelColuna2, labelColuna3, separador)

    var arquivoCriadoUri: Uri? = null
    var sucesso = false
    var mensagemRetorno = ""

    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, nomeFinal)
                put(MediaStore.MediaColumns.MIME_TYPE, "text/csv")
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/CEPEC")
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            if (uri != null) {
                try {
                    val saida = context.contentResolver.openOutputStream(uri)
                        ?: throw IOException("Sem acesso de escrita ao arquivo criado.")
                    saida.use { out -> gravarCsvNoFluxo(out, cabecalho, registros, separador) }
                    arquivoCriadoUri = uri
                    sucesso = true
                    mensagemRetorno = "Planilha salva em Downloads/CEPEC: $nomeFinal"
                } catch (e: Exception) {
                    Log.w("FileUtils", "Falha no MediaStore, usando fallback: ${e.message}")
                    try { context.contentResolver.delete(uri, null, null) } catch (_: Exception) {}
                }
            }
        }

        if (!sucesso) {
            val diretorio = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
            val arquivoDestino = File(diretorio, nomeFinal)
            FileOutputStream(arquivoDestino).use { out ->
                gravarCsvNoFluxo(out, cabecalho, registros, separador)
            }
            arquivoCriadoUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                arquivoDestino
            )
            sucesso = true
            mensagemRetorno = "Planilha salva com sucesso: $nomeFinal"
        }
    } catch (e: Exception) {
        Log.e("FileUtils", "Erro ao exportar planilha: ${e.message}")
        sucesso = false
        mensagemRetorno = "Erro ao exportar: ${e.message}"
    }

    withContext(Dispatchers.Main) {
        aoConcluir(sucesso, mensagemRetorno, arquivoCriadoUri)
    }
}

/** Wrapper síncrono para compatibilidade com chamadas simples da interface. */
fun exportarPlanilhaCEPEC(
    context: Context,
    registros: List<RegistroCacau>,
    ensaio: String,
    labelColuna1: String,
    labelColuna2: String,
    labelColuna3: String
) {
    CoroutineScope(Dispatchers.Main).launch {
        exportarPlanilhaCEPECAssync(context, registros, ensaio, labelColuna1, labelColuna2, labelColuna3) { _, msg, _ ->
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
        }
    }
}

fun gerarCabecalhoCsvCepec(
    labelColuna1: String = "Clone",
    labelColuna2: String = "Parcela",
    labelColuna3: String = "Planta",
    separador: String = ";"
): String = listOf(
    "plant_id",
    "Ensaio",
    "Dia",
    "Mês",
    "Ano",
    "Bloco",
    labelColuna2.ifBlank { "Parcela" },
    labelColuna3.ifBlank { "Planta" },
    labelColuna1.ifBlank { "Clone" },
    "Ordem",
    "SitPla2023c",
    "SitPla2023L",
    "SitPla",
    "Contagem_IA",
    "Contagem_Final",
    "NFPequenos",
    "NFMedios",
    "NFAdultos",
    "NFMaduros",
    "NFVassoura",
    "NFPodridao",
    "NFOutrasPerdas",
    "NVTotal",
    "NotaVirus",
    "CorFruto",
    "Ocorrencias",
    "Data_Hora",
    "Data_ISO",
    "Lat",
    "Lng",
    "Precisao_GPS_m",
    "Altitude_m",
    "Modelo_IA",
    "Threshold",
    "Operador",
    "Fazenda",
    "Versao_App",
    "Foto_URI"
).joinToString(separador) { escaparECorrigirCsv(it, separador) }

/** Formata número com vírgula decimal (padrão pt-BR), coerente com o delimitador ';' e aceito pelo Excel brasileiro. */
private fun numBr(valor: Double, casas: Int): String =
    String.format(Locale.forLanguageTag("pt-BR"), "%.${casas}f", valor)

/**
 * Obtém o clone a partir do ID da planta.
 * Suporta tanto o formato CEPLAC ("SCP-16005_TC-1801_par1_pla1_c0226")
 * quanto o formato gerado pelo app ("{clone}_{rotulo2}{parcela}_{rotulo3}{planta}").
 */
fun cloneDoPlantId(plantId: String): String {
    val partes = plantId.split("_")
    return when {
        partes.size >= 5 && partes.any { it.startsWith("par", ignoreCase = true) } -> partes.first()
        partes.size >= 3 -> partes.dropLast(2).joinToString("_")
        else -> partes.firstOrNull().orEmpty()
    }
}

/**
 * Grava o conteúdo CSV no stream fornecido, incluindo o cabeçalho UTF-8 BOM.
 */
private fun gravarCsvNoFluxo(
    out: OutputStream,
    cabecalho: String,
    registros: List<RegistroCacau>,
    separador: String
) {
    // Injeção de UTF-8 BOM (0xEF, 0xBB, 0xBF) para compatibilidade nativa com Excel
    out.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
    val formatoIso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)

    BufferedWriter(OutputStreamWriter(out, StandardCharsets.UTF_8)).use { writer ->
        writer.write(cabecalho)
        writer.newLine()

        fun t(v: String) = escaparECorrigirCsv(v, separador)
        fun n(v: String) = escaparECorrigirCsv(v, separador, protegerFormula = false)

        val registrosOrdenados = if (registros.any { it.ordem > 0 }) {
            registros.sortedWith(compareBy({ if (it.ordem > 0) it.ordem else Int.MAX_VALUE }, { it.timestampMs }))
        } else {
            registros
        }

        registrosOrdenados.forEach { reg ->
            val temGps = !(reg.lat == 0.0 && reg.lng == 0.0)
            val inativa = reg.isInativa
            val sitPlaFinal = when {
                reg.sitPla.isNotBlank() -> reg.sitPla
                inativa -> reg.diagnostico
                else -> "Normal"
            }

            val cal = Calendar.getInstance().apply {
                timeInMillis = if (reg.timestampMs > 0L) reg.timestampMs else System.currentTimeMillis()
            }
            val dia = cal.get(Calendar.DAY_OF_MONTH).toString()
            val mes = (cal.get(Calendar.MONTH) + 1).toString()
            val ano = cal.get(Calendar.YEAR).toString()
            val iso = if (reg.timestampMs > 0L) formatoIso.format(Date(reg.timestampMs)) else ""
            val cloneFinal = reg.cloneExplicito.ifBlank { cloneDoPlantId(reg.plantId) }

            // Padrão estatístico CEPLAC: quando a planta é Cavalo/Morta/Errada, exporta "." nos campos de frutos
            val linha = listOf(
                t(reg.plantId),
                t(reg.ensaioManual),
                n(dia),
                n(mes),
                n(ano),
                t(reg.bloco),
                t(reg.parcelaManual),
                n(reg.numPlanta.toString()),
                t(cloneFinal),
                n(if (reg.ordem > 0) reg.ordem.toString() else ""),
                t(reg.sitPla2023c),
                t(reg.sitPla2023L),
                t(sitPlaFinal),
                n(if (inativa) "." else reg.contagemIa.toString()),
                n(if (inativa) "." else reg.contagemFinal.toString()),
                n(if (inativa) "." else reg.nfPequenos.toString()),
                n(if (inativa) "." else reg.nfMedios.toString()),
                n(if (inativa) "." else reg.nfAdultos.toString()),
                n(if (inativa) "." else reg.nfMaduros.toString()),
                n(if (inativa) "." else reg.nfVassoura.toString()),
                n(if (inativa) "." else reg.nfPodridao.toString()),
                n(if (inativa) "." else reg.nfOutrasPerdas.toString()),
                n(if (inativa) "." else reg.nvTotal.toString()),
                n(if (inativa) "." else reg.notaVirus.toString()),
                t(if (inativa) "." else reg.corFruto.ifBlank { "." }),
                t(reg.observacao.split("|").filter { it.isNotBlank() }.joinToString(" / ")),
                t(reg.data),
                n(iso),
                n(if (temGps) numBr(reg.lat, 7) else ""),
                n(if (temGps) numBr(reg.lng, 7) else ""),
                n(if (temGps) numBr(reg.precisaoGpsM.toDouble(), 1) else ""),
                n(if (temGps) numBr(reg.altitude, 1) else ""),
                t(reg.modelo),
                n(numBr(reg.threshold.toDouble(), 2)),
                t(reg.operador),
                t(reg.fazenda),
                t(reg.versaoApp),
                t(reg.uri.toString())
            ).joinToString(separador)

            writer.write(linha)
            writer.newLine()
        }
        writer.flush()
    }
}

/**
 * Abre o seletor do sistema (ACTION_SEND) para compartilhar o arquivo CSV
 * diretamente via WhatsApp, E-mail, Google Drive, etc.
 */
fun compartilharArquivo(context: Context, uri: Uri, nomeExibicao: String) {
    try {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Dados de Campo - $nomeExibicao")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Compartilhar dados CEPEC"))
    } catch (e: Exception) {
        Log.e("FileUtils", "Erro ao compartilhar arquivo: ${e.message}")
        Toast.makeText(context, "Erro ao abrir compartilhamento.", Toast.LENGTH_SHORT).show()
    }
}

// ── Utilitários RFC 4180 e Proteção de Injeção CSV ────────────────────────────

fun parseLinhaCsvRfc4180(linha: String, separador: Char = ';'): List<String> {
    val resultado = mutableListOf<String>()
    val sb = StringBuilder()
    var emAspas = false
    var i = 0

    val tam = linha.length
    while (i < tam) {
        val c = linha[i]
        when {
            c == '"' -> {
                if (emAspas && i + 1 < tam && linha[i + 1] == '"') {
                    sb.append('"')
                    i++
                } else {
                    emAspas = !emAspas
                }
            }
            c == separador && !emAspas -> {
                resultado.add(sb.toString())
                sb.setLength(0)
            }
            c == '\r' || c == '\n' -> {
                if (emAspas) sb.append(c)
            }
            else -> {
                sb.append(c)
            }
        }
        i++
    }
    resultado.add(sb.toString())
    return resultado
}

fun detectarSeparador(cabecalho: String): Char {
    var contPontoEVirgula = 0
    var contVirgula = 0
    var contTab = 0
    var emAspas = false

    for (c in cabecalho) {
        if (c == '"') {
            emAspas = !emAspas
        } else if (!emAspas) {
            when (c) {
                ';' -> contPontoEVirgula++
                ',' -> contVirgula++
                '\t' -> contTab++
            }
        }
    }

    return when {
        contPontoEVirgula >= contVirgula && contPontoEVirgula >= contTab && contPontoEVirgula > 0 -> ';'
        contVirgula > contPontoEVirgula && contVirgula >= contTab -> ','
        contTab > 0 -> '\t'
        else -> ';'
    }
}

fun escaparECorrigirCsv(valor: String, separador: String = ";", protegerFormula: Boolean = true): String {
    var v = valor.trim()
    if (protegerFormula && (v.startsWith("=") || v.startsWith("+") || v.startsWith("-") || v.startsWith("@"))) {
        v = "'$v"
    }

    val precisaDeAspas = v.contains(separador) || v.contains('"') || v.contains('\n') || v.contains('\r')
    return if (precisaDeAspas) {
        "\"${v.replace("\"", "\"\"")}\""
    } else {
        v
    }
}

fun limparSimbologiaPura(texto: String): String =
    texto.trim()
        .replace(Regex("[^a-zA-Z0-9_\\-\\u00C0-\\u024F]+"), "_")
        .trim('_')

fun limparBomECaracteresInvalidos(texto: String): String {
    if (texto.isEmpty()) return ""
    return texto
        .replace("\uFEFF", "")
        .replace("\uFFFD", "")
        .replace("ï»¿", "")
        .replace(Regex("[^\\x20-\\x7E\\u00A0-\\u024F\\t]"), "")
        .trim()
}

data class ModeloImportado(
    val uri: Uri,
    val nome: String,
    val formato: FormatoModelo,
    val tamanhoMb: Double,
    val sha256Curto: String
)

fun importarModeloExterno(context: Context, uri: Uri): Result<ModeloImportado> {
    val nome = nomeDoArquivo(context, uri) ?: "modelo_externo.tflite"
    val temporario = File(context.filesDir, "modelo_importando.tmp")
    return try {
        val md = MessageDigest.getInstance("SHA-256")
        val entrada = context.contentResolver.openInputStream(uri)
            ?: return Result.failure(IllegalStateException("Não foi possível abrir o arquivo selecionado."))
        entrada.use { i ->
            FileOutputStream(temporario).use { o ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val n = i.read(buf)
                    if (n < 0) break
                    o.write(buf, 0, n)
                    md.update(buf, 0, n)
                }
            }
        }

        if (temporario.length() < 1024L) {
            throw IllegalArgumentException("Arquivo muito pequeno para ser um modelo TFLite.")
        }
        val cabecalho = ByteArray(8)
        temporario.inputStream().use { it.read(cabecalho) }
        if (String(cabecalho, 4, 4, Charsets.US_ASCII) != "TFL3") {
            throw IllegalArgumentException("O arquivo não é um modelo TFLite válido (assinatura TFL3 ausente).")
        }

        val formato = validarModeloTflite(temporario)

        val destino = File(context.filesDir, "modelo_${System.currentTimeMillis()}.tflite")
        if (!temporario.renameTo(destino)) {
            temporario.copyTo(destino, overwrite = true)
            temporario.delete()
        }

        context.filesDir.listFiles { f -> f.name.startsWith("modelo_") && f.name.endsWith(".tflite") }
            ?.sortedByDescending { it.lastModified() }
            ?.drop(2)
            ?.forEach { it.delete() }

        val sha = md.digest().take(6).joinToString("") { "%02x".format(it) }
        Result.success(
            ModeloImportado(Uri.fromFile(destino), nome, formato, destino.length() / (1024.0 * 1024.0), sha)
        )
    } catch (e: Exception) {
        Log.e("FileUtils", "Erro ao importar modelo: ${e.message}")
        temporario.delete()
        Result.failure(e)
    }
}

private fun validarModeloTflite(arquivo: File): FormatoModelo {
    val interp = try {
        Interpreter(arquivo, Interpreter.Options().apply { setNumThreads(2) })
    } catch (e: Exception) {
        throw IllegalArgumentException("O TFLite não conseguiu carregar o modelo: ${e.message?.take(160)}")
    }
    try {
        if (interp.outputTensorCount != 1) {
            throw IllegalArgumentException(
                "Modelo com ${interp.outputTensorCount} saídas não suportado (esperado detector YOLO com 1 saída)."
            )
        }
        val inT = interp.getInputTensor(0)
        val outT = interp.getOutputTensor(0)
        val formato = analisarFormatoModelo(
            inShape = inT.shape(),
            entradaFloat32 = inT.dataType() == DataType.FLOAT32,
            entradaUint8 = inT.dataType() == DataType.UINT8,
            outShape = outT.shape(),
            saidaFloat32 = outT.dataType() == DataType.FLOAT32
        )

        val bytesPorCanal = if (formato.entradaFloat) 4 else 1
        val entrada = ByteBuffer.allocateDirect(formato.inputSize * formato.inputSize * 3 * bytesPorCanal)
            .order(ByteOrder.nativeOrder())
        val s = outT.shape()
        val saida = Array(s[0]) { Array(s[1]) { FloatArray(s[2]) } }
        try {
            interp.run(entrada, saida)
        } catch (e: Exception) {
            throw IllegalArgumentException("Falha ao executar o modelo: ${e.message?.take(160)}")
        }
        return formato
    } finally {
        interp.close()
    }
}

private fun nomeDoArquivo(context: Context, uri: Uri): String? = try {
    context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getString(0) else null
    } ?: uri.lastPathSegment?.substringAfterLast('/')
} catch (_: Exception) { uri.lastPathSegment?.substringAfterLast('/') }
