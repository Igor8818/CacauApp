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
import java.io.InputStreamReader

fun carregarDadosDeUri(context: Context, uri: Uri): Pair<Map<String, String>, String> {
    val mapa = mutableMapOf<String, String>()
    var nome = "Planilha"
    try {
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            val reader = BufferedReader(InputStreamReader(inputStream))
            reader.readLine() // Pula cabeçalho
            var linha = reader.readLine()
            while (linha != null) {
                val colunas = linha.split(",")
                if (colunas.isNotEmpty()) mapa[colunas[0]] = linha
                linha = reader.readLine()
            }
        }
    } catch (e: Exception) { Log.e("CSV", "Erro: ${e.message}") }
    return Pair(mapa, nome)
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
            writer.write("Ensaio,Parcela,Plant_ID,Diagnostico,Data,Lat,Lng,Total_Frutos,Sadios,Vassoura,Podridao,Nao_Avaliavel")
            writer.newLine()
            registros.forEach { reg ->
                writer.write("${ensaio},${parcela},${reg.plantId},${reg.diagnostico},${reg.data},${reg.lat},${reg.lng},${reg.totalFrutos},${reg.sadios},${reg.vassoura},${reg.podridao},${reg.naoAvaliavel}")
                writer.newLine()
            }
            writer.flush()
            Toast.makeText(context, "Exportado para Downloads!", Toast.LENGTH_LONG).show()
        }
    }
}