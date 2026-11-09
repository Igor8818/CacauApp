package com.example.myapplication.data

data class RotaColeta(
    val clone: String,
    val parcela: String,
    val planta: Int,
    var coletado: Boolean = false,
    var qtdFrutos: String = "0",
    val ordem: Int = 0
)