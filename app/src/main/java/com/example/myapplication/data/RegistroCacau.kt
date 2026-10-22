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
    val timestampMs: Long = 0L,
    // ── Metadados e Variáveis Agronômicas CEPLAC / Field Book ────────────────
    val cloneExplicito: String = "",
    val bloco: String = "",
    val ordem: Int = 0,
    val sitPla2023c: String = "",
    val sitPla2023L: String = "",
    /** Situação da planta: "Normal", "Cavalo", "Morta", "Errada". */
    val sitPla: String = if (SituacaoPlanta.ehInativa(diagnostico)) {
        if (diagnostico == "Não Coletável") "Morta" else diagnostico
    } else "Normal",
    val nfPequenos: Int = 0,
    val nfMedios: Int = 0,
    val nfAdultos: Int = 0,
    val nfMaduros: Int = 0,
    val nfVassoura: Int = 0,
    val nfPodridao: Int = 0,
    val nfOutrasPerdas: Int = 0,
    val nvTotal: Int = 0,
    val corFruto: String = "",
    val notaVirus: Int = 0
) {
    val isInativa: Boolean
        get() = SituacaoPlanta.ehInativa(sitPla) || SituacaoPlanta.ehInativa(diagnostico)
}
