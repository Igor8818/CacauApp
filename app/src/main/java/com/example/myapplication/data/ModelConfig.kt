package com.example.myapplication.data

import android.net.Uri

val LABELS_DOENCA_CACAU = listOf("PodridaoParda", "Moniliase", "Sadio", "VassouraBruxa")

// Modelos embutidos no APK (ficam na pasta assets/)
data class ModelEmbutido(
    val nomeArquivo: String,       // ex: "detector_doenca.tflite"
    val nomeExibicao: String,      // ex: "Cacau Doenças CEPEC (IA Fitossanitária)"
    val descricao: String,         // ex: "Detecta frutos e classifica doenças"
    val labelsDefault: List<String> // rótulos padrão desse modelo
)

// Estado completo da configuração de IA do app
data class ModelConfig(
    // Fonte do modelo
    val modeloSelecionado: ModelEmbutido? = null,   // null = usando externo
    val modeloExternoUri: Uri? = null,
    val modeloExternoNome: String = "",

    // Filtros de detecção
    val labelsFiltro: List<String> = listOf("PodridaoParda", "Moniliase", "Sadio", "VassouraBruxa", "cacau", "fruto"),
    val threshold: Float = 0.30f,
    val maxResultados: Int = 25
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
        nomeArquivo = "detector_doenca.tflite",
        nomeExibicao = "Cacau Doenças CEPEC (IA Fitossanitária)",
        descricao = "Detecta frutos e classifica automaticamente: Sadio, Vassoura-de-Bruxa, Podridão Parda e Monilíase",
        labelsDefault = LABELS_DOENCA_CACAU
    ),
    ModelEmbutido(
        nomeArquivo = "detector.tflite",
        nomeExibicao = "Cacau Contagem CEPEC (Apenas Frutos)",
        descricao = "Modelo clássico de contagem geral de frutos de cacau",
        labelsDefault = listOf("cacau", "fruto")
    )
)
