package br.gov.ceplac.cepec.cacauapp.data

import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject

val LABELS_DOENCA_CACAU = listOf("PodridaoParda", "Moniliase", "Sadio", "VassouraBruxa")

/**
 * Estratégia de utilização exclusiva dos 2 modelos oficiais de cacau do CEPEC/CEPLAC:
 * - COMBINADO_2_IAS: preview rápido com `detector.tflite` + diagnóstico fitossanitário pós-foto com `detector_doenca.tflite`
 * - SOMENTE_DOENCAS: apenas `detector_doenca.tflite` ao vivo e na revisão
 * - SOMENTE_CONTAGEM: apenas `detector.tflite` para contagem acelerada de frutos
 */
enum class ModoIACacau(
    val rotuloCurto: String,
    val nomeExibicao: String,
    val descricao: String
) {
    COMBINADO_2_IAS(
        rotuloCurto = "2 IAs",
        nomeExibicao = "Pipeline Duplo (2 IAs)",
        descricao = "Conta frutos no preview com detector.tflite e diagnostica doenças na revisão com detector_doenca.tflite."
    ),
    SOMENTE_DOENCAS(
        rotuloCurto = "Doenças",
        nomeExibicao = "Só Doenças Ao Vivo",
        descricao = "Usa detector_doenca.tflite ao vivo e na revisão (Sadio, Vassoura de Bruxa, Podridão Parda e Monilíase)."
    ),
    SOMENTE_CONTAGEM(
        rotuloCurto = "Contagem",
        nomeExibicao = "Só Contagem Rápida",
        descricao = "Usa apenas detector.tflite para contagem rápida de frutos no campo."
    );

    companion object {
        fun fromName(name: String?): ModoIACacau =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: COMBINADO_2_IAS
    }
}

// Modelos embutidos no APK (assets/)
data class ModelEmbutido(
    val nomeArquivo: String,
    val nomeExibicao: String,
    val descricao: String,
    val labelsDefault: List<String>,
    val thresholdSugerido: Float = 0.30f,
    val papel: String = ""
)

// Estado completo da configuração de IA do CacauApp CEPEC
data class ModelConfig(
    val modeloSelecionado: ModelEmbutido? = null,
    val modeloInternoPath: String? = null,
    val modeloExternoUri: Uri? = null,
    val modeloExternoNome: String = "",
    val labelsFiltro: List<String> = listOf("PodridaoParda", "Moniliase", "Sadio", "VassouraBruxa", "cacau", "fruto"),
    val threshold: Float = 0.30f,
    val thresholdDoenca: Float = 0.25f,
    val maxResultados: Int = 25,
    val usarDiagnosticoSecundario: Boolean = true,
    val modeloSecundarioArquivo: String = "detector_doenca.tflite",
    val modoIA: ModoIACacau = ModoIACacau.COMBINADO_2_IAS
) {
    val usaModeloCustomizado: Boolean
        get() = !modeloInternoPath.isNullOrBlank() || modeloExternoUri != null

    val modoEfetivo: ModoIACacau
        get() = when {
            usaModeloCustomizado -> ModoIACacau.SOMENTE_CONTAGEM
            modoIA == ModoIACacau.COMBINADO_2_IAS && usarDiagnosticoSecundario -> ModoIACacau.COMBINADO_2_IAS
            usarDiagnosticoSecundario -> ModoIACacau.COMBINADO_2_IAS
            modeloSelecionado?.nomeArquivo == "detector.tflite" -> ModoIACacau.SOMENTE_CONTAGEM
            else -> ModoIACacau.SOMENTE_DOENCAS
        }

    val arquivoPrincipalEfetivo: String
        get() = when {
            usaModeloCustomizado -> ""
            modoEfetivo == ModoIACacau.COMBINADO_2_IAS -> "detector.tflite"
            modoEfetivo == ModoIACacau.SOMENTE_CONTAGEM -> "detector.tflite"
            else -> "detector_doenca.tflite"
        }

    val nomeExibicao: String
        get() = when {
            !modeloInternoPath.isNullOrBlank() && modeloExternoNome.isNotBlank() -> modeloExternoNome
            modeloExternoUri != null -> modeloExternoNome.ifBlank { "Modelo CEPEC Customizado" }
            modoEfetivo == ModoIACacau.COMBINADO_2_IAS -> "Contagem + Doenças CEPEC (2 IAs)"
            modeloSelecionado != null -> modeloSelecionado.nomeExibicao
            modoEfetivo == ModoIACacau.SOMENTE_CONTAGEM -> "Cacau Contagem CEPEC (Apenas Frutos)"
            else -> "Cacau Doenças CEPEC (IA Fitossanitária)"
        }

    fun comModoIA(novoModo: ModoIACacau): ModelConfig = when (novoModo) {
        ModoIACacau.COMBINADO_2_IAS -> copy(
            modoIA = ModoIACacau.COMBINADO_2_IAS,
            usarDiagnosticoSecundario = true,
            modeloSelecionado = MODELOS_EMBUTIDOS.firstOrNull { it.nomeArquivo == "detector_doenca.tflite" },
            modeloInternoPath = null,
            modeloExternoUri = null,
            modeloExternoNome = "",
            labelsFiltro = (LABELS_DOENCA_CACAU + listOf("cacau", "fruto")).distinct()
        )
        ModoIACacau.SOMENTE_DOENCAS -> copy(
            modoIA = ModoIACacau.SOMENTE_DOENCAS,
            usarDiagnosticoSecundario = false,
            modeloSelecionado = MODELOS_EMBUTIDOS.firstOrNull { it.nomeArquivo == "detector_doenca.tflite" },
            modeloInternoPath = null,
            modeloExternoUri = null,
            modeloExternoNome = "",
            labelsFiltro = LABELS_DOENCA_CACAU
        )
        ModoIACacau.SOMENTE_CONTAGEM -> copy(
            modoIA = ModoIACacau.SOMENTE_CONTAGEM,
            usarDiagnosticoSecundario = false,
            modeloSelecionado = MODELOS_EMBUTIDOS.firstOrNull { it.nomeArquivo == "detector.tflite" },
            modeloInternoPath = null,
            modeloExternoUri = null,
            modeloExternoNome = "",
            labelsFiltro = listOf("cacau", "fruto")
        )
    }

    fun toManifestJson(): String {
        val root = JSONObject().apply {
            put("nomeExibicao", nomeExibicao)
            put("threshold", threshold.toDouble())
            put("thresholdDoenca", thresholdDoenca.toDouble())
            put("maxResultados", maxResultados)
            put("modoIA", modoEfetivo.name)
            put("usarDiagnosticoSecundario", modoEfetivo == ModoIACacau.COMBINADO_2_IAS)
            put("modeloSecundarioArquivo", modeloSecundarioArquivo)
            put("labelsFiltro", JSONArray().apply { labelsFiltro.forEach { put(it) } })
        }
        return root.toString(2)
    }
}

// Catálogo estrito com APENAS os 2 modelos oficiais de cacau CEPEC embutidos em assets/.
val MODELOS_EMBUTIDOS = listOf(
    ModelEmbutido(
        nomeArquivo = "detector_doenca.tflite",
        nomeExibicao = "Cacau Doenças CEPEC (IA Fitossanitária)",
        descricao = "Detecta frutos de cacau e classifica em Sadio, Podridão Parda, Monilíase e Vassoura de Bruxa.",
        labelsDefault = LABELS_DOENCA_CACAU,
        thresholdSugerido = 0.25f,
        papel = "Diagnóstico Fitossanitário (4 classes)"
    ),
    ModelEmbutido(
        nomeArquivo = "detector.tflite",
        nomeExibicao = "Cacau Contagem CEPEC (Apenas Frutos)",
        descricao = "Detector de alta sensibilidade e baixa latência focado na contagem geral de frutos no tronco e copa.",
        labelsDefault = listOf("cacau", "fruto"),
        thresholdSugerido = 0.30f,
        papel = "Contagem Geral (Alto Recall)"
    )
)
