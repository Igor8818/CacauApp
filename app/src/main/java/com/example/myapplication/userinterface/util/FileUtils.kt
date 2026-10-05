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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    val dataIso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).format(Date())
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
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    gravarCsvNoFluxo(out, cabecalho, registros, separador, dataIso)
                }
                arquivoCriadoUri = uri
                sucesso = true
                mensagemRetorno = "Planilha salva em Downloads/CEPEC: $nomeFinal"
            }
        }

        // Fallback para Android 9 ou quando a inserção no MediaStore não for permitida
        if (!sucesso) {
            val diretorio = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
            val arquivoDestino = File(diretorio, nomeFinal)
            FileOutputStream(arquivoDestino).use { out ->
                gravarCsvNoFluxo(out, cabecalho, registros, separador, dataIso)
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

/**
 * Grava o conteúdo CSV no stream fornecido, incluindo o cabeçalho UTF-8 BOM.
 */
private fun gravarCsvNoFluxo(
    out: OutputStream,
    cabecalho: String,
    registros: List<RegistroCacau>,
    separador: String,
    dataIsoPadrao: String
) {
    // Injeção de UTF-8 BOM (0xEF, 0xBB, 0xBF) para compatibilidade nativa com Excel
    out.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
    BufferedWriter(OutputStreamWriter(out, StandardCharsets.UTF_8)).use { writer ->
        writer.write(cabecalho)
        writer.newLine()

        registros.forEach { reg ->
            val clone = reg.plantId.split("_").firstOrNull() ?: ""
            val linha = listOf(
                reg.ensaioManual,
                reg.plantId,
                clone,
                reg.parcelaManual,
                reg.numPlanta.toString(),
                reg.contagemIa.toString(),
                reg.contagemFinal.toString(),
                reg.diagnostico,
                reg.data,
                dataIsoPadrao,
                reg.lat.toString(),
                reg.lng.toString(),
                "%.1f".format(Locale.US, reg.precisaoGpsM),
                "%.1f".format(Locale.US, reg.altitude),
                reg.modelo,
                "%.2f".format(Locale.US, reg.threshold),
                reg.operador,
                reg.fazenda,
                reg.versaoApp,
                reg.uri.toString()
            ).joinToString(separador) { escaparECorrigirCsv(it, separador) }

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
 * 1. Proteção contra CSV Formula Injection: se iniciar com '=', '+', '-' ou '@', prefixa com '\''.
 * 2. RFC 4180 Escaping: se contiver delimitador, aspas ou quebra de linha, envolve em aspas e duplica aspas internas.
 */
fun escaparECorrigirCsv(valor: String, separador: String = ";"): String {
    var v = valor.trim()
    // Proteção contra injeção de fórmulas executáveis no Excel / LibreOffice
    if (v.startsWith("=") || v.startsWith("+") || v.startsWith("-") || v.startsWith("@")) {
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

/**
 * Importa um modelo .tflite escolhido pelo usuário copiando-o para o
 * armazenamento interno do app (filesDir/modelo_externo.tflite).
 */
fun importarModeloExterno(context: Context, uri: Uri): Pair<Uri, String>? {
    val nome = nomeDoArquivo(context, uri) ?: "modelo_externo.tflite"
    return try {
        val destino = File(context.filesDir, "modelo_externo.tflite")
        val entrada = context.contentResolver.openInputStream(uri) ?: return null
        entrada.use { i -> FileOutputStream(destino).use { o -> i.copyTo(o) } }
        if (destino.length() == 0L) null else Pair(Uri.fromFile(destino), nome)
    } catch (e: Exception) {
        Log.e("FileUtils", "Erro ao importar modelo: ${e.message}")
        null
    }
}

private fun nomeDoArquivo(context: Context, uri: Uri): String? = try {
    context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getString(0) else null
    } ?: uri.lastPathSegment?.substringAfterLast('/')
} catch (_: Exception) { uri.lastPathSegment?.substringAfterLast('/') }