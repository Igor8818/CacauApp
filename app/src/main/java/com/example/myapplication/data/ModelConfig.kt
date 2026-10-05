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
    val threshold: Float = 0.30f,
    val maxResultados: Int = 15
) {
    val nomeExibicao: String get() = when {
        modeloSelecionado != null -> modeloSelecionado.nomeExibicao
        modeloExternoUri != null  -> modeloExternoNome.ifBlank { "Modelo Externo" }
        else                      -> "Nenhum modelo"
    }

    /** Nome do arquivo .tflite em uso (ex: "detector.tflite") — exibido no badge da camera. */
    val nomeArquivoModelo: String get() = when {
        modeloSelecionado != null -> modeloSelecionado.nomeArquivo
        modeloExternoUri != null  -> modeloExternoNome.ifBlank { "modelo_externo.tflite" }
        else                      -> "sem modelo"
    }

    /** True quando ha alguma fonte de modelo configurada (nao garante que o arquivo exista). */
    val temFonteDeModelo: Boolean get() = modeloSelecionado != null || modeloExternoUri != null
}

/**
 * Catalogo de modelos embutidos no APK.
 *
 * Contem apenas o modelo padrao do CEPEC. O arquivo .tflite NAO e versionado
 * (propriedade intelectual) — se ausente de app/src/main/assets/, o app
 * direciona o usuario para a tela "Configurar Modelo IA" para importar um
 * arquivo externo. Qualquer outro modelo deve ser importado dinamicamente.
 */
val MODELOS_EMBUTIDOS = listOf(
    ModelEmbutido(
        nomeArquivo   = "detector.tflite",
        nomeExibicao  = "Cacau CEPEC (Padrao)",
        descricao     = "Modelo YOLO treinado para deteccao de frutos de cacaueiro",
        labelsDefault = listOf("cacau", "fruto")
    )
)