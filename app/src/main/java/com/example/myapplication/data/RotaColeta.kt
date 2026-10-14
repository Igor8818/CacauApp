package com.example.myapplication.data

data class RotaColeta(
    val clone: String,
    val parcela: String,
    val planta: Int,
    val coletado: Boolean = false,
    val qtdFrutos: String = "0",
    /** Ocorrências fitossanitárias / observações separadas por "|" (ver [OcorrenciaCampo]). */
    val observacao: String = ""
)

/**
 * Ocorrências rápidas de campo para marcação por toque no FieldBook.
 * São as principais pragas/doenças monitoradas na cacauicultura.
 */
enum class OcorrenciaCampo(val rotulo: String) {
    VASSOURA_BRUXA("Vassoura-de-bruxa"),
    PODRIDAO_PARDA("Podridão-parda"),
    MONILIASE("Monilíase"),
    MIRIDEO("Mirídeos"),
    FALTA_AGUA("Estresse hídrico")
}

fun String.ocorrencias(): List<String> = split("|").map { it.trim() }.filter { it.isNotEmpty() }

fun List<String>.comoObservacao(): String = joinToString("|")