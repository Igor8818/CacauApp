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
    val lng: Double,
    val contagemIa: Int = diagnostico.toIntOrNull() ?: 0,
    val contagemFinal: Int = diagnostico.toIntOrNull() ?: 0,
    val confiancaMedia: Float = 0f,
    val modelo: String = "",
    val threshold: Float = 0.55f,
    val operador: String = "",
    val fazenda: String = "",
    val precisaoGpsM: Float = 0f,
    val altitude: Double = 0.0,
    val versaoApp: String = "1.0",
    val observacao: String = "",
    /** Momento real da captura (epoch ms). 0 em registros antigos, criados antes deste campo existir. */
    val timestampMs: Long = 0L
)