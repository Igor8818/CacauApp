package com.example.myapplication.userinterface.util

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import com.example.myapplication.data.RegistroCacau
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Le apenas a primeira linha (cabecalho) de um arquivo CSV/Excel apontado por [uri].
 * Retorna lista de nomes de colunas, ou lista vazia em caso de falha.
 */
fun lerApenasCabecalho(context: Context, uri: Uri): List<String> {
    try {
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            BufferedReader(InputStreamReader(inputStream, StandardCharsets.UTF_8)).use { reader ->
                val primeiraLinha = reader.readLine() ?: return emptyList()
                val linhaLimpa = limparBomECaracteresInvalidos(primeiraLinha)
                return fatiarColunas(linhaLimpa).map { it.trim() }.filter { it.isNotEmpty() }
            }
        }
    } catch (e: Exception) {
        Log.e("FileUtils", "Erro ao ler cabecalho: ${e.message}")
    }
    return emptyList()
}

/**
 * Processa um CSV completo e retorna lista de [com.example.myapplication.data.RotaColeta]
 * usando os indices de coluna ja resolvidos ([idxClone], [idxParcela], [idxPlanta]).
 */
fun processarCSVComIndices(
    context: Context,
    uri: Uri,
    idxClone: Int,
    idxParcela: Int,
    idxPlanta: Int
): List<com.example.myapplication.data.RotaColeta> {
    val lista = mutableListOf<com.example.myapplication.data.RotaColeta>()
    try {
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            BufferedReader(InputStreamReader(inputStream, StandardCharsets.UTF_8)).use { reader ->
                reader.readLine() // pula cabecalho
                var linha = reader.readLine()
                while (linha != null) {
                    val linhaLimpa = limparBomECaracteresInvalidos(linha)
                    val colunas = fatiarColunas(linhaLimpa)
                    if (colunas.size > maxOf(idxClone, idxParcela, idxPlanta)) {
                        val clone   = colunas[idxClone].trim()
                        val parcela = colunas[idxParcela].trim()
                        val planta  = colunas[idxPlanta].trim().filter { it.isDigit() }.toIntOrNull() ?: 1
                        lista.add(com.example.myapplication.data.RotaColeta(clone, parcela, planta))
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
 * Exporta os [registros] como planilha CSV compativel com Excel (UTF-8 + BOM)
 * para a pasta Downloads/CEPEC do dispositivo.
 * O nome do arquivo inclui o nome do ensaio e a data atual para facil identificacao.
 */
fun exportarPlanilhaCEPEC(
    context: Context,
    registros: List<RegistroCacau>,
    ensaio: String,
    labelColuna1: String,
    labelColuna2: String,
    labelColuna3: String
) {
    val dataHoje   = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    val nomeEnsaio = ensaio.ifBlank { "SemNome" }.replace(Regex("[^a-zA-Z0-9_-]"), "_")
    val nomeFinal  = "Coleta_${nomeEnsaio}_${dataHoje}.csv"

    val contentValues = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, nomeFinal)
        put(MediaStore.MediaColumns.MIME_TYPE, "text/csv")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/CEPEC")
        }
    }

    val contentUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
        MediaStore.Downloads.EXTERNAL_CONTENT_URI
    else
        Uri.parse("content://media/external/file")

    val uri = context.contentResolver.insert(contentUri, contentValues)
    uri?.let { targetUri ->
        try {
            context.contentResolver.openOutputStream(targetUri)?.use { out ->
                // BOM UTF-8: garante que o Excel abra com encoding correto
                out.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
                BufferedWriter(OutputStreamWriter(out, StandardCharsets.UTF_8)).use { writer ->
                    // Cabecalho
                    writer.write("Campanha,ID_Gerado,$labelColuna1,$labelColuna2,$labelColuna3,Resultado_IA,Data,Lat,Lng")
                    writer.newLine()
                    // Linhas de dados
                    registros.forEach { reg ->
                        val itemPrincipal = reg.plantId.split("_").firstOrNull() ?: ""
                        writer.write(
                            "${escaparCampoCsv(reg.ensaioManual)}," +
                            "${escaparCampoCsv(reg.plantId)}," +
                            "${escaparCampoCsv(itemPrincipal)}," +
                            "${escaparCampoCsv(reg.parcelaManual)}," +
                            "${reg.numPlanta}," +
                            "${escaparCampoCsv(reg.diagnostico)}," +
                            "${reg.data}," +
                            "${reg.lat}," +
                            "${reg.lng}"
                        )
                        writer.newLine()
                    }
                    writer.flush()
                }
            }
            Toast.makeText(context, "Planilha exportada: $nomeFinal", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Log.e("FileUtils", "Erro ao exportar CSV: ${e.message}")
            Toast.makeText(context, "Erro ao exportar: ${e.message}", Toast.LENGTH_LONG).show()
        }
    } ?: Toast.makeText(context, "Nao foi possivel criar o arquivo.", Toast.LENGTH_LONG).show()
}

// ── Utilitarios internos ─────────────────────────────────────────────────────

/**
 * Remove BOM UTF-8 e caracteres de controle indesejados, preservando
 * letras latinas acentuadas (a-z, A-Z, 0-9, Latin-1 Supplement, Latin Extended).
 */
fun limparBomECaracteresInvalidos(texto: String): String {
    if (texto.isEmpty()) return ""
    return texto
        .replace("\uFEFF", "")   // BOM UTF-8
        .replace("\uFFFD", "")   // caractere de substituicao Unicode
        .replace("ï»¿", "")      // BOM mal-interpretado como Latin-1
        .replace(Regex("[^\\x20-\\x7E\\u00A0-\\u024F\\t]"), "")
        .trim()
}

/**
 * Divide uma linha CSV em campos, suportando separador `;` ou `,`.
 * Remove aspas duplas externas.
 */
fun fatiarColunas(linha: String): List<String> {
    if (linha.isBlank()) return emptyList()
    val sep = if (linha.contains(";")) ";" else ","
    return linha.replace("\r", "").replace("\n", "")
        .split(sep)
        .map { it.replace("\"", "").trim() }
}

/**
 * Envolve um campo em aspas duplas se ele contiver virgula, ponto-e-virgula ou aspas,
 * garantindo que o CSV seja valido mesmo com dados compostos.
 */
private fun escaparCampoCsv(valor: String): String {
    return if (valor.contains(',') || valor.contains(';') || valor.contains('"') || valor.contains('\n')) {
        "\"${valor.replace("\"", "\"\"")}\""
    } else valor
}