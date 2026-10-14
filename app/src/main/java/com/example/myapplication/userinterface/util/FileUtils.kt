package com.example.myapplication.userinterface.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.myapplication.data.RegistroCacau
import com.example.myapplication.data.RotaColeta
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.*
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter

/**
 * Lê apenas a primeira linha (cabeçalho) de um arquivo CSV/Excel apontado por [uri].
 * Utiliza o parser RFC 4180 com autodetecção de delimitador.
 */
fun lerApenasCabecalho(context: Context, uri: Uri): List<String> {
    try {
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            BufferedReader(InputStreamReader(inputStream, StandardCharsets.UTF_8)).use { reader ->
                val primeiraLinha = reader.readLine() ?: return emptyList()
                val linhaLimpa = limparBomECaracteresInvalidos(primeiraLinha)
                val separador = detectarSeparador(linhaLimpa)
                return parseLinhaCsvRfc4180(linhaLimpa, separador).map { it.trim() }.filter { it.isNotEmpty() }
            }
        }
    } catch (e: Exception) {
        Log.e("FileUtils", "Erro ao ler cabecalho: ${e.message}")
    }
    return emptyList()
}

/**
 * Processa um CSV completo e retorna lista de [RotaColeta]
 * usando os índices de coluna já resolvidos ([idxClone], [idxParcela], [idxPlanta]).
 */
fun processarCSVComIndices(
    context: Context,
    uri: Uri,
    idxClone: Int,
    idxParcela: Int,
    idxPlanta: Int
): List<RotaColeta> {
    val lista = mutableListOf<RotaColeta>()
    try {
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            BufferedReader(InputStreamReader(inputStream, StandardCharsets.UTF_8)).use { reader ->
                val cabecalho = reader.readLine() ?: return emptyList()
                val separador = detectarSeparador(limparBomECaracteresInvalidos(cabecalho))
                var linha = reader.readLine()
                while (linha != null) {
                    val linhaLimpa = limparBomECaracteresInvalidos(linha)
                    if (linhaLimpa.isNotBlank()) {
                        val colunas = parseLinhaCsvRfc4180(linhaLimpa, separador)
                        if (colunas.size > maxOf(idxClone, idxParcela, idxPlanta)) {
                            val clone = colunas[idxClone].trim()
                            val parcela = colunas[idxParcela].trim()
                            val planta = colunas[idxPlanta].trim().filter { it.isDigit() }.toIntOrNull() ?: 1
                            if (clone.isNotBlank()) {
                                lista.add(RotaColeta(clone = clone, parcela = parcela, planta = planta))
                            }
                        }
                    }
                    linha = reader.readLine()
                }
            }
        }
    } catch (e: Exception) {
        Log.e("FileUtils", "Erro ao processar CSV: ${e.message}")
    }
    return lista
}

/**
 * Exporta os [registros] como planilha CSV compatível com Excel pt-BR (delimitador ';' e UTF-8 + BOM).
 * Suporta Android 10+ (via MediaStore Downloads) e versões anteriores (via getExternalFilesDir).
 * Executa em thread de I/O e oferece opção de compartilhamento direto.
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

    val cabecalho = listOf(
        "Campanha",
        "ID_Gerado",
        labelColuna1.ifBlank { "Clone" },
        labelColuna2.ifBlank { "Parcela" },
        labelColuna3.ifBlank { "Planta" },
        "Contagem_IA",
        "Contagem_Final",
        "Status",
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
        "Ocorrencias",
        "Foto_URI"
    ).joinToString(separador) { escaparECorrigirCsv(it, separador) }

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
                    // Evita deixar um arquivo vazio/corrompido em Downloads
                    Log.w("FileUtils", "Falha no MediaStore, usando fallback: ${e.message}")
                    try { context.contentResolver.delete(uri, null, null) } catch (_: Exception) {}
                }
            }
        }

        // Fallback para Android 9 ou quando a inserção no MediaStore não for permitida
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
    kotlinx.coroutines.CoroutineScope(Dispatchers.Main).launch {
        exportarPlanilhaCEPECAssync(context, registros, ensaio, labelColuna1, labelColuna2, labelColuna3) { sucesso, msg, uri ->
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
        }
    }
}

/** Formata número com vírgula decimal (padrão pt-BR), coerente com o delimitador ';' e aceito pelo Excel brasileiro. */
private fun numBr(valor: Double, casas: Int): String =
    String.format(Locale("pt", "BR"), "%.${casas}f", valor)

/**
 * Obtém o clone a partir do ID da planta. O ID é "{clone}_{rotulo2}{parcela}_{rotulo3}{planta}",
 * então removemos os dois últimos segmentos: clones que contêm '_' não são mais truncados.
 */
fun cloneDoPlantId(plantId: String): String {
    val partes = plantId.split("_")
    return if (partes.size >= 3) partes.dropLast(2).joinToString("_") else partes.firstOrNull().orEmpty()
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

        // Texto livre: protegido contra injeção de fórmulas. Numéricos: apenas escape RFC 4180
        // (senão latitudes/longitudes negativas receberiam um apóstrofo e virariam texto).
        fun t(v: String) = escaparECorrigirCsv(v, separador)
        fun n(v: String) = escaparECorrigirCsv(v, separador, protegerFormula = false)

        registros.forEach { reg ->
            val temGps = !(reg.lat == 0.0 && reg.lng == 0.0)
            val naoColetavel = reg.diagnostico.toIntOrNull() == null
            val status = if (naoColetavel) reg.diagnostico.ifBlank { "Sem contagem" } else "OK"
            val iso = if (reg.timestampMs > 0L) formatoIso.format(Date(reg.timestampMs)) else ""

            val linha = listOf(
                t(reg.ensaioManual),
                t(reg.plantId),
                t(cloneDoPlantId(reg.plantId)),
                t(reg.parcelaManual),
                n(reg.numPlanta.toString()),
                n(if (naoColetavel) "" else reg.contagemIa.toString()),
                n(if (naoColetavel) "" else reg.contagemFinal.toString()),
                t(status),
                t(reg.data),
                n(iso),
                // Sem fix de GPS o app guarda 0,0 (oceano): exporta vazio em vez de coordenada falsa
                n(if (temGps) numBr(reg.lat, 7) else ""),
                n(if (temGps) numBr(reg.lng, 7) else ""),
                n(if (temGps) numBr(reg.precisaoGpsM.toDouble(), 1) else ""),
                n(if (temGps) numBr(reg.altitude, 1) else ""),
                t(reg.modelo),
                n(numBr(reg.threshold.toDouble(), 2)),
                t(reg.operador),
                t(reg.fazenda),
                t(reg.versaoApp),
                t(reg.observacao.split("|").filter { it.isNotBlank() }.joinToString(" / ")),
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

/**
 * Parser de linha CSV compatível com RFC 4180:
 * - Trata campos delimitados por aspas;
 * - Suporta aspas escapadas via dupla aspa ("");
 * - Suporta delimitadores dentro de campos entre aspas.
 */
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
                    // Aspa escapada ("") dentro de campo com aspas
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

/**
 * Autodetecta o separador mais provável de uma linha de cabeçalho:
 * avalia contagem de ';', ',', '\t' fora de aspas.
 */
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
        else -> ';' // Padrão Excel pt-BR
    }
}

/**
 * Sanitiza e escapa valores para saída CSV:
 * 1. Proteção contra CSV Formula Injection (apenas quando [protegerFormula]): se iniciar com
 *    '=', '+', '-' ou '@', prefixa com '\''. Deve ser desligada para colunas numéricas.
 * 2. RFC 4180 Escaping: se contiver delimitador, aspas ou quebra de linha, envolve em aspas e duplica aspas internas.
 */
fun escaparECorrigirCsv(valor: String, separador: String = ";", protegerFormula: Boolean = true): String {
    var v = valor.trim()
    // Proteção contra injeção de fórmulas executáveis no Excel / LibreOffice
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

/**
 * Sanitiza texto livre (campanha, clone, parcela, fazenda, operador) para uso seguro
 * em nomes de arquivo, preservando letras latinas estendidas (\u00C0-\u024F).
 */
fun limparSimbologiaPura(texto: String): String =
    texto.trim()
        .replace(Regex("[^a-zA-Z0-9_\\-\\u00C0-\\u024F]+"), "_")
        .trim('_')

/**
 * Remove BOM UTF-8 e caracteres de controle indesejados, preservando
 * letras latinas acentuadas.
 */
fun limparBomECaracteresInvalidos(texto: String): String {
    if (texto.isEmpty()) return ""
    return texto
        .replace("\uFEFF", "")
        .replace("\uFFFD", "")
        .replace("ï»¿", "")
        .replace(Regex("[^\\x20-\\x7E\\u00A0-\\u024F\\t]"), "")
        .trim()
}

/** Resultado de uma importação de modelo bem-sucedida. */
data class ModeloImportado(
    val uri: Uri,
    val nome: String,
    val formato: FormatoModelo,
    val tamanhoMb: Double,
    val sha256Curto: String
)

/**
 * Importa um modelo .tflite escolhido pelo usuário de forma segura:
 * 1. copia para um arquivo temporário calculando o SHA-256 (sem carregar tudo na memória);
 * 2. valida a assinatura TFL3;
 * 3. carrega no TFLite, valida entrada/saída e roda uma inferência de teste;
 * 4. só então promove para um arquivo definitivo com nome único.
 * O modelo ativo anterior nunca é sobrescrito por um arquivo inválido, e o nome único garante
 * que o app recarregue o detector ao importar uma nova versão.
 */
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

        // Mantém só os 2 modelos importados mais recentes (o ativo + o anterior)
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

/**
 * Carrega o modelo, valida shapes/tipos e executa uma inferência com entrada zerada para garantir
 * que ele realmente roda neste aparelho. Lança exceção com mensagem amigável se não for suportado.
 */
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