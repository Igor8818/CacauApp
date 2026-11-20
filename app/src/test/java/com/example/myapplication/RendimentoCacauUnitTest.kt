package com.example.myapplication

import android.net.Uri
import com.example.myapplication.data.ConfigRendimentoCacau
import com.example.myapplication.data.RegistroCacau
import com.example.myapplication.data.calcularEstimativaPlantaCacau
import com.example.myapplication.data.calcularEstimativaPorContagensCacau
import com.example.myapplication.data.calcularRankingClonesCacau
import com.example.myapplication.data.calcularRendimentoEnsaioCacau
import com.example.myapplication.data.gerarResumoTextoCacau
import com.example.myapplication.userinterface.CaixaAuditavel
import com.example.myapplication.userinterface.EstadoFruto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito

class RendimentoCacauUnitTest {

    private fun fakeUri(): Uri = Mockito.mock(Uri::class.java)

    private fun reg(
        plantId: String,
        sadios: Int,
        vassoura: Int = 0,
        podridao: Int = 0,
        naoAval: Int = 0,
        diag: String = "Auditada"
    ): RegistroCacau {
        val total = sadios + vassoura + podridao + naoAval
        return RegistroCacau(
            plantId = plantId,
            parcelaManual = "1",
            ensaioManual = "TC-1801",
            diagnostico = diag,
            data = "20/11/2026 10:00",
            uri = fakeUri(),
            lat = -14.77,
            lng = -39.23,
            numPlanta = 1,
            totalFrutos = total,
            sadios = sadios,
            vassoura = vassoura,
            podridao = podridao,
            naoAvaliavel = naoAval,
            caixas = emptyList()
        )
    }

    @Test
    fun testEstimativaPlanta_Indice22DescontandoDoencas() {
        val caixas = List(22) {
            CaixaAuditavel(0.1f, 0.1f, 0.2f, 0.2f, EstadoFruto.SADIO)
        } + List(4) {
            CaixaAuditavel(0.3f, 0.3f, 0.4f, 0.4f, EstadoFruto.PODRIDAO)
        }

        val est = calcularEstimativaPlantaCacau(caixas, caixas.size, ConfigRendimentoCacau())

        assertEquals(22, est.frutosConsiderados)
        assertEquals(4, est.frutosPerdidosDoenca)
        assertEquals(1.0, est.kgAmendoasSecas, 0.0001)
        assertEquals(1.0 / 15.0, est.arrobas, 0.0001)
        assertEquals(4.0 / 22.0, est.perdaKg, 0.0001)
        assertEquals((4.0 / 22.0) / 15.0, est.perdaArrobas, 0.0001)
    }

    @Test
    fun testEstimativaPorContagens_SemDescontarDoencas() {
        val est = calcularEstimativaPorContagensCacau(
            sadios = 22,
            doentes = 4,
            totalFrutos = 26,
            config = ConfigRendimentoCacau(descontarDoencas = false)
        )

        assertEquals(26, est.frutosConsiderados)
        assertEquals(26.0 / 22.0, est.kgAmendoasSecas, 0.0001)
    }

    @Test
    fun testRendimentoEnsaio_SobrevivenciaEInfeccaoPonderados() {
        val regs = listOf(
            reg("CCN-51_P1_Pl1", sadios = 22),
            reg("CCN-51_P1_Pl2", sadios = 22),
            reg("CCN-51_P1_Pl3", sadios = 0, diag = "Não Coletável (Ausente)")
        )

        val res = calcularRendimentoEnsaioCacau(regs, ConfigRendimentoCacau())

        assertEquals(3, res.totalRegistros)
        assertEquals(2, res.plantasColetaveis)
        assertEquals(1, res.plantasNaoColetaveis)
        assertEquals(66.666, res.taxaSobrevivenciaPercentual, 0.05)
        assertEquals(1111.0, res.potencialKgHa, 0.01)
        assertEquals(1111.0 * (2.0 / 3.0), res.realKgHa, 0.05)
        assertEquals((1111.0 * (2.0 / 3.0)) / 15.0, res.realArrobasHa, 0.05)
    }

    @Test
    fun testRankingClones_OrdenaDecrescentePorArrobasHa() {
        val regs = listOf(
            reg("PH-16_P1_Pl1", sadios = 11, vassoura = 2),
            reg("CCN-51_P2_Pl1", sadios = 33, podridao = 1),
            reg("BN-34_P3_Pl1", sadios = 22)
        )

        val ranking = calcularRankingClonesCacau(regs, ConfigRendimentoCacau())

        assertEquals(3, ranking.size)
        assertEquals("CCN-51", ranking[0].codigoClone)
        assertEquals("BN-34", ranking[1].codigoClone)
        assertEquals("PH-16", ranking[2].codigoClone)
        assertTrue(ranking[0].arrobasHa > ranking[1].arrobasHa)
        assertTrue(ranking[1].arrobasHa > ranking[2].arrobasHa)

        val texto = gerarResumoTextoCacau("TC-1801", regs)
        assertTrue(texto.contains("TC-1801"))
        assertTrue(texto.contains("CCN-51"))
        assertTrue(texto.contains("@/ha"))
    }

    @Test
    fun testRendimentoEnsaio_DeduplicaMultiFotosEFormataResultadoCsvCanonico() {
        val foto1 = reg("CCN-51_P1_Pl1", sadios = 10, podridao = 1, diag = "Podridão e Vassoura (2 Podr / 3 Vass)")
        val foto2 = reg("CCN-51_P1_Pl1_Foto2", sadios = 12, vassoura = 1, diag = "13")

        val resumo = calcularRendimentoEnsaioCacau(listOf(foto1, foto2), ConfigRendimentoCacau())
        assertEquals(1, resumo.totalRegistros)
        assertEquals(1, resumo.plantasColetaveis)
        assertEquals(22, resumo.totalSadios)
        assertEquals(22.0, resumo.mediaSadiosPorPlanta, 0.001)

        // Coluna 5 (Resultado) deve ser estritamente inteiro ou Não Coletável, nunca texto multi-numérico
        val linhaCsv = formatarLinhaCsvPersonalizado(foto1)
        val cols = fatiarColunasCampos(linhaCsv)
        assertEquals("11", cols[5])

        val naoCol = reg("CCN-51_P1_Pl2", sadios = 0, diag = "Não Coletável (Ausente)")
        val colsNaoCol = fatiarColunasCampos(formatarLinhaCsvPersonalizado(naoCol))
        assertEquals("Não Coletável", colsNaoCol[5])
    }
}
