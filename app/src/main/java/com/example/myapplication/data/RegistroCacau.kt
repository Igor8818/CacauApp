package com.example.myapplication.data

import android.net.Uri

data class RegistroCacau(
    val uri: Uri,
    val plantId: String,
    val ensaioManual: String,
    val parcelaManual: String,
    val diagnostico: String,
    val data: String,
    val numPlanta: Int,
    val lat: Double,
    val lng: Double
)