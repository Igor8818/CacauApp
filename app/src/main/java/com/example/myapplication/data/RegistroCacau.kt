package com.example.myapplication.data

import android.net.Uri
import com.example.myapplication.userinterface.CaixaAuditavel

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
    val totalFrutos: Int = 0,
    val sadios: Int = 0,
    val vassoura: Int = 0,
    val podridao: Int = 0,
    val naoAvaliavel: Int = 0,
    val caixas: List<CaixaAuditavel> = emptyList(),
    var statusSync: String = "PENDENTE" // "PENDENTE", "SINCRONIZADO", "ERRO"
)
