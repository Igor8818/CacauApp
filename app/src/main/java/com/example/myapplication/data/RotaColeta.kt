package com.example.myapplication.data

data class RotaColeta(
    val clone: String,
    val parcela: String,
    val planta: Int,
    val coletado: Boolean = false,
    val qtdFrutos: String = "0"
)