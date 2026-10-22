package com.example.myapplication.data

data class RotaColeta(
    val clone: String,
    val parcela: String,
    val planta: Int,
    val coletado: Boolean = false,
    val qtdFrutos: String = "0",
    /** Ocorrências fitossanitárias / observações separadas por "|" (ver [OcorrenciaCampo]). */
    val observacao: String = "",
    /** Identificador original vindo da planilha (ex: SCP-16005_TC-1801_par1_pla1_c0226). */
    val plantIdOriginal: String = "",
    val ensaio: String = "",
    val bloco: String = "",
    val ordem: Int = 0,
    /** Histórico compactado de avaliações anteriores (ex: N11_P0_E0_M0_V0 ou N0_P0_E0_M11_V0). */
    val sitPla2023c: String = "",
    /** Histórico em letras das avaliações anteriores (ex: nnnnnnnnnnn ou MMMMMMMMMMM). */
    val sitPla2023L: String = "",
    /** Situação atual da planta avaliada em campo: Normal, Cavalo, Morta, Errada. */
    val sitPla: String = "",
    val nfPequenos: Int = 0,
    val nfMedios: Int = 0,
    val nfAdultos: Int = 0,
    val nfMaduros: Int = 0,
    val nfVassoura: Int = 0,
    val nfPodridao: Int = 0,
    val nfOutrasPerdas: Int = 0,
    val nvTotal: Int = 0,
    /** Cor do fruto avaliada na planta: "Roxo", "Verde" ou vazio. */
    val corFruto: String = "",
    /** Nota de sintomas de vírus (escala CEPLAC: 0, 1, 2, 4, 8, 16, 32, 64). */
    val notaVirus: Int = 0
) {
    /** Usa o plant_id oficial da planilha quando existir; caso contrário, gera o padrão do app. */
    val idEfetivo: String
        get() = plantIdOriginal.ifBlank { "${clone}_P${parcela}_Pl${planta}" }

    /** Indica se a planta foi classificada como improdutiva/inativa (Cavalo, Morta, Errada ou Não Coletável). */
    val isInativa: Boolean
        get() = SituacaoPlanta.ehInativa(sitPla) || SituacaoPlanta.ehInativa(qtdFrutos)

    /** Situação efetiva para exibição e exportação. */
    val situacaoEfetiva: String
        get() = when {
            sitPla.isNotBlank() -> sitPla
            SituacaoPlanta.ehInativa(qtdFrutos) -> qtdFrutos
            coletado -> "Normal"
            else -> ""
        }

    /**
     * Analisa o histórico anterior (SitPla2023c / SitPla2023L) para alertar o coletor
     * quando a planta já vinha sendo registrada como Morta/Cavalo (M) ou Errada (E).
     */
    val alertaHistoricoFalha: String?
        get() {
            val histL = sitPla2023L.trim()
            val histC = sitPla2023c.trim()
            if (histL.isEmpty() && histC.isEmpty()) return null
            val qtdM = histL.count { it == 'M' || it == 'm' }
            val qtdE = histL.count { it == 'E' || it == 'e' }
            val qtdP = histL.count { it == 'P' || it == 'p' }
            return when {
                histL.isNotEmpty() && qtdM >= histL.length - 1 ->
                    "Planta registrada como Morta/Falha em 2023"
                histC.startsWith("N0_") && histC.contains("M") ->
                    "Planta sem histórico de produção normal"
                qtdP >= 1 ->
                    "Histórico anterior aponta Porta-enxerto (Cavalo)"
                qtdE >= 1 ->
                    "Histórico anterior aponta Planta Errada"
                else -> null
            }
        }

    /**
     * Resumo amigável e legível do histórico 2023 sem códigos crípticos (como N11_P0_E0_M0_V0 / nnnnnnnnnnn).
     */
    val resumoHistoricoLegivel: String?
        get() {
            val alerta = alertaHistoricoFalha
            if (alerta != null) return alerta
            if (sitPla2023c.isBlank() && sitPla2023L.isBlank()) return null
            return "Histórico 2023: Planta Normal"
        }
}

/** Situações oficiais de planta utilizadas nas planilhas de campo da CEPLAC. */
enum class SituacaoPlanta(val rotulo: String) {
    NORMAL("Normal"),
    CAVALO("Cavalo"),
    MORTA("Morta"),
    ERRADA("Errada"),
    NAO_COLETAVEL("Não Coletável");

    companion object {
        fun ehInativa(valor: String): Boolean {
            val v = valor.trim().lowercase()
            return v == "não coletável" || v == "nao coletavel" ||
                v == "não identificável" || v == "nao identificavel" ||
                v == "cavalo" || v == "morta" || v == "errada"
        }
    }
}

/**
 * Ocorrências rápidas de campo para marcação por toque no FieldBook e na Coleta Livre.
 * São as principais pragas/doenças monitoradas na cacauicultura.
 */
enum class OcorrenciaCampo(val rotulo: String) {
    VASSOURA_BRUXA("Vassoura-de-bruxa"),
    PODRIDAO_PARDA("Podridão-parda"),
    MONILIASE("Monilíase"),
    MIRIDEO("Mirídeos"),
    FALTA_AGUA("Estresse hídrico")
}

val NOTAS_VIRUS_CEPLAC = listOf(0, 1, 2, 4, 8, 16, 32, 64)
val CORES_FRUTO_CEPLAC = listOf("Roxo", "Verde")

fun String.ocorrencias(): List<String> = split("|").map { it.trim() }.filter { it.isNotEmpty() }

fun List<String>.comoObservacao(): String = joinToString("|")
