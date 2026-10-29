package com.example.myapplication.data

import android.net.Uri

// Modelos embutidos no APK (ficam na pasta assets/)
data class ModelEmbutido(
    val nomeArquivo: String,       // ex: "detector.tflite"
    val nomeExibicao: String,      // ex: "Cacau CEPEC v1"
    val descricao: String,         // ex: "Detecção de frutos de cacau"
    val labelsDefault: List<String> // rótulos padrão desse modelo
)

// Estado completo da configuração de IA do app
data class ModelConfig(
    // Fonte do modelo
    val modeloSelecionado: ModelEmbutido? = null,   // null = usando externo
    val modeloExternoUri: Uri? = null,
    val modeloExternoNome: String = "",

    // Filtros de detecção
    val labelsFiltro: List<String> = listOf("cacau", "fruto"),
    val threshold: Float = 0.55f,
    val maxResultados: Int = 10
) {
    val nomeExibicao: String get() = when {
        modeloSelecionado != null -> modeloSelecionado.nomeExibicao
        modeloExternoUri != null -> modeloExternoNome.ifBlank { "Modelo Externo" }
        else -> "Nenhum modelo"
    }
}

// Catálogo de modelos embutidos — adicione novos aqui quando quiser.
// IMPORTANTE: nomeArquivo precisa apontar para um .tflite que realmente existe
// em assets/ e que foi treinado para os labelsDefault declarados. Duas entradas
// apontando para o mesmo arquivo com labels diferentes fazem a filtragem por
// rótulo (labelsFiltro) descartar 100% das detecções silenciosamente, porque
// o label que o modelo devolve nunca bate com o filtro configurado.
val MODELOS_EMBUTIDOS = listOf(
    ModelEmbutido(
        nomeArquivo = "detector.tflite",
        nomeExibicao = "Cacau CEPEC (Padrão)",
        descricao = "Modelo padrão para detecção de frutos de cacau",
        labelsDefault = listOf("cacau", "fruto")
    )
    // Removida a entrada placeholder "Frutas Genéricas (Demo)": ela apontava para
    // o mesmo detector.tflite do cacau, mas filtrava por labels ("apple", "banana",
    // "orange") que o modelo nunca retorna — resultado era zero detecções, sem erro
    // nenhum na tela. Quando houver um modelo genérico de verdade treinado e
    // exportado, basta adicionar uma nova entrada aqui com o nomeArquivo correto.
)
