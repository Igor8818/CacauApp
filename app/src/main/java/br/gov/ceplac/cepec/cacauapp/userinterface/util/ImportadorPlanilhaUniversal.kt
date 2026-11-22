package br.gov.ceplac.cepec.cacauapp.userinterface.util

import android.content.Context
import android.net.Uri
import br.gov.ceplac.cepec.cacauapp.data.RotaColeta
import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.zip.ZipInputStream
import javax.xml.parsers.SAXParserFactory

/**
 * Importador offline de planilhas para o Modo Rota (Field Book CEPEC).
 * Suporta nativamente:
 * - Excel (.xlsx) via ZipInputStream + SAXParser (100% offline, compatível com minSdk 24)
 * - CSV / TSV / TXT com detecção automática de encoding (UTF-8 / Windows-1252) e delimitador (;, ,, \t, |)
 */
object ImportadorPlanilhaUniversal {

    data class PlanilhaLida(
        val cabecalho: List<String>,
        val linhas: List<List<String>>,
        val formato: String // "XLSX", "CSV", "VAZIO"
    )

    data class MapeamentoSugerido(
        val idxClone: Int,
        val idxParcela: Int,
        val idxPlanta: Int,
        val idxOrdem: Int = -1
    ) {
        val completo: Boolean get() = idxClone >= 0 && idxParcela >= 0 && idxPlanta >= 0
    }

    fun lerPlanilha(context: Context, uri: Uri): PlanilhaLida {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: return PlanilhaLida(emptyList(), emptyList(), "VAZIO")
        return lerPlanilhaDeBytes(bytes)
    }

    fun lerPlanilhaDeBytes(bytes: ByteArray): PlanilhaLida {
        if (bytes.isEmpty()) return PlanilhaLida(emptyList(), emptyList(), "VAZIO")
        return if (isXlsxMagicBytes(bytes)) {
            parseXlsx(bytes)
        } else {
            parseTextoDelimitado(bytes)
        }
    }

    internal fun isXlsxMagicBytes(bytes: ByteArray): Boolean {
        return bytes.size >= 4 &&
            bytes[0] == 0x50.toByte() &&
            bytes[1] == 0x4B.toByte() &&
            bytes[2] == 0x03.toByte() &&
            bytes[3] == 0x04.toByte()
    }

    internal fun isSafeZipEntryName(entryName: String): Boolean {
        if (entryName.isBlank()) return false
        if (entryName.startsWith("/") || entryName.startsWith("\\")) return false
        val normalized = entryName.replace('\\', '/')
        return !normalized.split('/').any { it == ".." } && !normalized.contains(":")
    }

    internal fun readZipEntryBounded(input: InputStream, maxBytes: Int): ByteArray? {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(8192)
        var total = 0
        while (true) {
            val n = input.read(buf)
            if (n <= 0) break
            total += n
            if (total > maxBytes) return null
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    internal fun decodeBytesAutoCharset(bytes: ByteArray): String {
        if (bytes.isEmpty()) return ""
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            return String(bytes, 3, bytes.size - 3, StandardCharsets.UTF_8)
        }
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
            return String(bytes, 2, bytes.size - 2, StandardCharsets.UTF_16LE)
        }
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
            return String(bytes, 2, bytes.size - 2, StandardCharsets.UTF_16BE)
        }

        return try {
            val decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
            decoder.decode(ByteBuffer.wrap(bytes)).toString()
        } catch (_: Exception) {
            String(bytes, Charset.forName("windows-1252"))
        }
    }

    internal fun limparSimbologiaPura(texto: String): String {
        if (texto.isEmpty()) return ""
        return texto
            .replace("\uFEFF", "")
            .replace("\uFFFD", "")
            .replace("ï»¿", "")
            .replace("\r", "")
            .trim()
    }

    internal fun detectarDelimitador(linhaCabecalho: String): Char {
        val candidatos = listOf(';', ',', '\t', '|')
        var melhor = ','
        var maiorContagem = 0
        for (delim in candidatos) {
            var count = 0
            var emAspas = false
            for (ch in linhaCabecalho) {
                if (ch == '"') {
                    emAspas = !emAspas
                } else if (ch == delim && !emAspas) {
                    count++
                }
            }
            if (count > maiorContagem) {
                maiorContagem = count
                melhor = delim
            }
        }
        return melhor
    }

    internal fun fatiarLinhaCsv(linha: String, delimitador: Char? = null): List<String> {
        if (linha.isBlank()) return emptyList()
        val sep = delimitador ?: detectarDelimitador(linha)
        val resultado = mutableListOf<String>()
        val sb = StringBuilder()
        var emAspas = false
        val limpa = linha.replace("\r", "").replace("\n", "")
        var i = 0
        while (i < limpa.length) {
            val c = limpa[i]
            if (c == '"') {
                if (emAspas && i + 1 < limpa.length && limpa[i + 1] == '"') {
                    sb.append('"')
                    i++
                } else {
                    emAspas = !emAspas
                }
            } else if (c == sep && !emAspas) {
                resultado.add(sb.toString().trim())
                sb.clear()
            } else {
                sb.append(c)
            }
            i++
        }
        resultado.add(sb.toString().trim())
        return resultado
    }

    private fun parseTextoDelimitado(bytes: ByteArray): PlanilhaLida {
        val textoCompleto = limparSimbologiaPura(decodeBytesAutoCharset(bytes))
        if (textoCompleto.isBlank()) return PlanilhaLida(emptyList(), emptyList(), "CSV")

        val linhasRaw = parseRegistrosCsvMultilinha(textoCompleto)
        val naoVazias = linhasRaw.filter { it.isNotBlank() }
        if (naoVazias.isEmpty()) return PlanilhaLida(emptyList(), emptyList(), "CSV")

        val delim = detectarDelimitador(naoVazias.first())
        val cabecalho = fatiarLinhaCsv(naoVazias.first(), delim)
            .map { limparSimbologiaPura(it) }
        val linhasDados = naoVazias.drop(1)
            .map { fatiarLinhaCsv(it, delim) }
            .filter { row -> row.any { it.isNotBlank() } }

        return PlanilhaLida(cabecalho = cabecalho, linhas = linhasDados, formato = "CSV")
    }

    private fun parseRegistrosCsvMultilinha(conteudo: String): List<String> {
        val linhas = mutableListOf<String>()
        val sb = StringBuilder()
        var emAspas = false
        for (ch in conteudo) {
            if (ch == '"') {
                emAspas = !emAspas
                sb.append(ch)
            } else if (ch == '\n' && !emAspas) {
                linhas.add(sb.toString())
                sb.clear()
            } else {
                sb.append(ch)
            }
        }
        if (sb.isNotEmpty()) linhas.add(sb.toString())
        return linhas
    }

    private fun parseXlsx(bytes: ByteArray): PlanilhaLida {
        var sharedStringsBytes: ByteArray? = null
        val sheetsMap = sortedMapOf<String, ByteArray>()

        try {
            ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory && isSafeZipEntryName(entry.name)) {
                        val nomeLower = entry.name.lowercase(Locale.ROOT)
                        when {
                            nomeLower == "xl/sharedstrings.xml" -> {
                                sharedStringsBytes = readZipEntryBounded(zis, 16 * 1024 * 1024)
                            }
                            nomeLower.startsWith("xl/worksheets/sheet") && nomeLower.endsWith(".xml") -> {
                                readZipEntryBounded(zis, 16 * 1024 * 1024)?.let {
                                    sheetsMap[nomeLower] = it
                                }
                            }
                        }
                    }
                    entry = zis.nextEntry
                }
            }
        } catch (_: Exception) {
            return PlanilhaLida(emptyList(), emptyList(), "XLSX")
        }

        val sheetBytes = sheetsMap["xl/worksheets/sheet1.xml"]
            ?: sheetsMap.values.firstOrNull()
            ?: return PlanilhaLida(emptyList(), emptyList(), "XLSX")

        val sharedStrings = sharedStringsBytes?.let { parseSharedStringsXml(it) } ?: emptyList()
        val todasLinhas = parseSheetXml(sheetBytes, sharedStrings)
            .filter { row -> row.any { it.isNotBlank() } }

        if (todasLinhas.isEmpty()) return PlanilhaLida(emptyList(), emptyList(), "XLSX")

        val cabecalho = todasLinhas.first().map { it.trim() }
        val larguraCabecalho = cabecalho.size
        val dados = todasLinhas.drop(1).map { linha ->
            if (linha.size < larguraCabecalho) {
                linha + List(larguraCabecalho - linha.size) { "" }
            } else {
                linha
            }
        }
        return PlanilhaLida(cabecalho = cabecalho, linhas = dados, formato = "XLSX")
    }

    private fun createSaxParser() = SAXParserFactory.newInstance().apply {
        isNamespaceAware = false
        runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
    }.newSAXParser()

    private fun tagLocal(localName: String?, qName: String?): String {
        val raw = localName?.takeIf { it.isNotBlank() } ?: qName.orEmpty()
        return raw.substringAfter(':').lowercase(Locale.ROOT)
    }

    private fun parseSharedStringsXml(xmlBytes: ByteArray): List<String> {
        val lista = mutableListOf<String>()
        val parser = createSaxParser()

        parser.parse(ByteArrayInputStream(xmlBytes), object : DefaultHandler() {
            private var dentroSi = false
            private var dentroRPh = false
            private var dentroT = false
            private val sb = StringBuilder()

            override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes?) {
                when (tagLocal(localName, qName)) {
                    "si" -> {
                        dentroSi = true
                        sb.clear()
                    }
                    "rph" -> dentroRPh = true
                    "t" -> if (dentroSi && !dentroRPh) dentroT = true
                }
            }

            override fun characters(ch: CharArray?, start: Int, length: Int) {
                if (ch != null && dentroT && dentroSi && !dentroRPh) {
                    sb.append(ch, start, length)
                }
            }

            override fun endElement(uri: String?, localName: String?, qName: String?) {
                when (tagLocal(localName, qName)) {
                    "t" -> dentroT = false
                    "rph" -> dentroRPh = false
                    "si" -> {
                        lista.add(sb.toString())
                        dentroSi = false
                    }
                }
            }
        })
        return lista
    }

    private fun parseSheetXml(xmlBytes: ByteArray, sharedStrings: List<String>): List<List<String>> {
        val linhas = mutableListOf<List<String>>()
        val parser = createSaxParser()

        parser.parse(ByteArrayInputStream(xmlBytes), object : DefaultHandler() {
            private var linhaAtual: MutableMap<Int, String>? = null
            private var maxColLinha = -1
            private var colCursor = 0
            private var colAtualIdx = 0
            private var tipoCelula = ""
            private var dentroV = false
            private var dentroIsT = false
            private val valorSb = StringBuilder()

            override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes?) {
                when (tagLocal(localName, qName)) {
                    "row" -> {
                        linhaAtual = mutableMapOf()
                        maxColLinha = -1
                        colCursor = 0
                    }
                    "c" -> {
                        val ref = attributes?.getValue("r")
                        colAtualIdx = if (!ref.isNullOrBlank()) extrairIndiceColunaExcel(ref) else colCursor
                        colCursor = colAtualIdx + 1
                        tipoCelula = attributes?.getValue("t") ?: ""
                        valorSb.clear()
                    }
                    "v" -> {
                        dentroV = true
                        valorSb.clear()
                    }
                    "t" -> {
                        if (tipoCelula == "inlineStr") {
                            dentroIsT = true
                        }
                    }
                }
            }

            override fun characters(ch: CharArray?, start: Int, length: Int) {
                if (ch != null && (dentroV || dentroIsT)) {
                    valorSb.append(ch, start, length)
                }
            }

            override fun endElement(uri: String?, localName: String?, qName: String?) {
                when (tagLocal(localName, qName)) {
                    "v" -> dentroV = false
                    "t" -> dentroIsT = false
                    "c" -> {
                        val textoFinal = resolverValorCelulaXlsx(tipoCelula, valorSb.toString(), sharedStrings)
                        linhaAtual?.put(colAtualIdx, textoFinal)
                        if (colAtualIdx > maxColLinha) maxColLinha = colAtualIdx
                    }
                    "row" -> {
                        val map = linhaAtual
                        if (map != null && maxColLinha >= 0) {
                            val rowList = MutableList(maxColLinha + 1) { idx -> map[idx] ?: "" }
                            linhas.add(rowList)
                        }
                        linhaAtual = null
                    }
                }
            }
        })
        return linhas
    }

    internal fun extrairIndiceColunaExcel(cellRef: String): Int {
        var col = 0
        for (ch in cellRef) {
            if (ch in 'A'..'Z') {
                col = col * 26 + (ch - 'A' + 1)
            } else if (ch in 'a'..'z') {
                col = col * 26 + (ch - 'a' + 1)
            } else {
                break
            }
        }
        return (col - 1).coerceAtLeast(0)
    }

    private fun resolverValorCelulaXlsx(tipo: String, bruto: String, sharedStrings: List<String>): String {
        val trim = bruto.trim()
        return when (tipo) {
            "s" -> {
                val idx = trim.toIntOrNull() ?: return ""
                sharedStrings.getOrNull(idx)?.trim() ?: ""
            }
            "b" -> if (trim == "1") "TRUE" else "FALSE"
            "inlineStr", "str", "e" -> trim
            else -> normalizarNumeroExcel(trim)
        }
    }

    private fun normalizarNumeroExcel(texto: String): String {
        val match = Regex("^(-?\\d+)\\.0+$").matchEntire(texto)
        return match?.groupValues?.get(1) ?: texto
    }

    fun sugerirMapeamento(cabecalho: List<String>): MapeamentoSugerido {
        val norm = cabecalho.map { normalizarCabecalho(it) }

        val idxClone = norm.indexOfFirst {
            it == "clone" || it == "v_id" || it == "tratamento" || it == "variedade" || it == "genotipo"
        }.let { found ->
            if (found >= 0) found else norm.indexOfFirst {
                it.contains("clone") || it.contains("variedade") || it.contains("tratamento") || it.contains("genotipo")
            }
        }

        val idxParcela = norm.indexOfFirst {
            it == "parcela" || it == "rep"
        }.let { found ->
            if (found >= 0) found else norm.indexOfFirst {
                it == "bloco" || it.contains("parcela") || it.contains("bloco") || it.contains("rep")
            }
        }

        val idxPlanta = norm.indexOfFirst {
            it == "planta" || it == "p_id" || it == "pl" || it == "num_planta"
        }.let { found ->
            if (found >= 0) found else norm.indexOfFirst {
                (it.contains("planta") && !it.contains("plant_id")) || it == "id" || it.contains("arvore")
            }.let { f -> if (f >= 0) f else norm.indexOfFirst { it == "plant_id" } }
        }

        val idxOrdem = norm.indexOfFirst {
            it == "ordem" || it == "ordem_rota" || it == "seq" || it == "sequencia"
        }

        return MapeamentoSugerido(idxClone, idxParcela, idxPlanta, idxOrdem)
    }

    fun extrairRotaCacau(
        planilha: PlanilhaLida,
        idxClone: Int,
        idxParcela: Int,
        idxPlanta: Int,
        idxOrdem: Int = -1
    ): List<RotaColeta> {
        if (planilha.linhas.isEmpty()) return emptyList()
        val maxIdx = maxOf(idxClone, idxParcela, idxPlanta)
        if (idxClone < 0 || idxParcela < 0 || idxPlanta < 0) return emptyList()

        val lista = mutableListOf<RotaColeta>()
        planilha.linhas.forEachIndexed { idx, colunas ->
            if (colunas.size > maxIdx) {
                val clone = colunas[idxClone].trim()
                val parcela = colunas[idxParcela].trim().ifBlank { "1" }
                val plantaText = colunas[idxPlanta].trim()
                val planta = Regex("(?i)(?:pl|planta)[\\s_:-]*(\\d+)").find(plantaText)?.groupValues?.get(1)?.toIntOrNull()?.coerceAtLeast(1)
                    ?: plantaText.substringBefore('.').substringBefore(',').filter { it.isDigit() }.toIntOrNull()?.coerceAtLeast(1)
                    ?: (idx + 1)
                val ordem = if (idxOrdem in colunas.indices) {
                    colunas[idxOrdem].trim().filter { it.isDigit() }.toIntOrNull()?.coerceAtLeast(1) ?: (lista.size + 1)
                } else {
                    lista.size + 1
                }
                if (clone.isNotBlank()) {
                    lista.add(
                        RotaColeta(
                            clone = clone,
                            parcela = parcela,
                            planta = planta,
                            ordem = ordem
                        )
                    )
                }
            }
        }
        return lista
    }

    private fun normalizarCabecalho(texto: String): String {
        return texto.trim().lowercase(Locale.ROOT)
            .replace("á", "a").replace("à", "a").replace("ã", "a").replace("â", "a")
            .replace("é", "e").replace("ê", "e")
            .replace("í", "i")
            .replace("ó", "o").replace("ô", "o").replace("õ", "o")
            .replace("ú", "u").replace("ç", "c")
    }
}
