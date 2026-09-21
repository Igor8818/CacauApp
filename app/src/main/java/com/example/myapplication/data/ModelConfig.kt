package com.example.myapplication.data

import android.net.Uri

/** Representa um modelo TFLite embutido no APK (pasta assets/). */
data class ModelEmbutido(
    val nomeArquivo: String,        // ex: "detector.tflite"
    val nomeExibicao: String,       // ex: "Cacau CEPEC v1"
    val descricao: String,          // ex: "Deteccao de frutos de cacau"
    val labelsDefault: List<String> // rotulos padrao desse modelo
)

/** Estado completo da configuracao de IA vigente no app. */
data class ModelConfig(
    // Fonte do modelo — apenas um dos dois sera nao-nulo
    val modeloSelecionado: ModelEmbutido? = null,   // modelo embutido nos assets
    val modeloExternoUri: Uri? = null,              // modelo importado pelo usuario
    val modeloExternoNome: String = "",

    // Filtros de deteccao
    val labelsFiltro: List<String> = listOf("cacau", "fruto"),
    val threshold: Float = 0.55f,
    val maxResultados: Int = 10
) {
    val nomeExibicao: String get() = when {
        modeloSelecionado != null -> modeloSelecionado.nomeExibicao
        modeloExternoUri != null  -> modeloExternoNome.ifBlank { "Modelo Externo" }
        else                      -> "Nenhum modelo"
    }
}

/**
 * Catalogo de modelos embutidos no APK.
 * Para adicionar um novo modelo: coloque o .tflite em app/src/main/assets/
 * e adicione uma entrada aqui.
 */
val MODELOS_EMBUTIDOS = listOf(
    ModelEmbutido(
        nomeArquivo   = "detector.tflite",
        nomeExibicao  = "Cacau CEPEC (Padrao)",
        descricao     = "Modelo YOLO treinado para deteccao de frutos de cacaueiro",
        labelsDefault = listOf("cacau", "fruto")
    )
)