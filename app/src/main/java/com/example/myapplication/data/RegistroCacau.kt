package com.example.myapplication.data

import android.net.Uri
import com.example.myapplication.userinterface.CaixaAuditavel
import com.example.myapplication.userinterface.EstadoFruto
import java.util.Locale

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
) {
    /**
     * Valida a concordância semântica agronômica e matemática do registro:
     * - Se Não Coletável: todas as contagens devem ser estritamente zero.
     * - Se Coletável: soma das classes (sadios + vassoura + podridao + naoAvaliavel) deve ser igual a totalFrutos.
     * - Se caixas auditadas presentes: caixas.size == totalFrutos e contagens por classe batem exatamente.
     */
    fun isConcordante(): Boolean {
        val norm = diagnostico.trim().lowercase(Locale.ROOT)
        val isNaoColetavel = norm == "não coletável" || norm == "nao coletavel" ||
                norm.contains("não coletável") || norm.contains("nao coletavel") ||
                norm.contains("ausente") || norm.contains("morta") || norm.contains("seca")

        if (isNaoColetavel) {
            return totalFrutos == 0 && sadios == 0 && vassoura == 0 && podridao == 0 && naoAvaliavel == 0
        }

        val somaClasses = sadios + vassoura + podridao + naoAvaliavel
        if (totalFrutos != somaClasses) return false

        if (caixas.isNotEmpty()) {
            if (caixas.size != totalFrutos) return false
            val cSadios = caixas.count { it.estado == EstadoFruto.SADIO }
            val cVassoura = caixas.count { it.estado == EstadoFruto.VASSOURA }
            val cPodridao = caixas.count { it.estado == EstadoFruto.PODRIDAO }
            val cNaoAvail = caixas.count { it.estado == EstadoFruto.NAO_AVALIAVEL }
            if (cSadios != sadios || cVassoura != vassoura || cPodridao != podridao || cNaoAvail != naoAvaliavel) {
                return false
            }
        }

        return true
    }
}
