package com.example.myapplication

import com.example.myapplication.data.RotaColeta
import com.example.myapplication.data.SituacaoPlanta
import com.example.myapplication.data.comoObservacao
import com.example.myapplication.data.ocorrencias
import com.example.myapplication.userinterface.screens.estatisticas
import com.example.myapplication.userinterface.screens.proximoPendente
import com.example.myapplication.userinterface.util.cloneDoPlantId
import com.example.myapplication.userinterface.util.parseLinhasCSVComIndices
import org.junit.Assert.*
import org.junit.Test

class FieldBookLogicUnitTest {

    private fun rota() = listOf(
        RotaColeta("CCN51", "1", 1, coletado = true, qtdFrutos = "6"),
        RotaColeta("CCN51", "1", 2, coletado = true, qtdFrutos = "Não Coletável"),
        RotaColeta("CCN51", "1", 3),
        RotaColeta("PS1319", "2", 1, coletado = true, qtdFrutos = "4", observacao = "Vassoura-de-bruxa"),
        RotaColeta("PS1319", "2", 2)
    )

    @Test
    fun estatisticas_calculaTotaisMediaEOcorrencias() {
        val s = rota().estatisticas()
        assertEquals(3, s.coletadas)
        assertEquals(5, s.total)
        assertEquals(10, s.frutos)
        assertEquals(1, s.mortas)
        assertEquals(1, s.comOcorrencia)
        assertEquals(2, s.pendentes)
        // 10 frutos / 2 plantas avaliadas (a morta fica fora da média)
        assertEquals(5f, s.mediaFrutos, 0.001f)
    }

    @Test
    fun estatisticas_excluiCavaloMortaEErradaDaMediaDeFrutos() {
        val r = listOf(
            RotaColeta("SCP-16005", "1", 1, coletado = true, qtdFrutos = "16", sitPla = "Normal"),
            RotaColeta("SCP-16005", "1", 2, coletado = true, qtdFrutos = "Cavalo", sitPla = "Cavalo"),
            RotaColeta("SCP-16005", "1", 3, coletado = true, qtdFrutos = "Morta", sitPla = "Morta"),
            RotaColeta("SCP-16005", "1", 4, coletado = true, qtdFrutos = "Errada", sitPla = "Errada"),
            RotaColeta("SCP-16005", "1", 5, coletado = true, qtdFrutos = "4", sitPla = "Normal")
        )
        val s = r.estatisticas()
        assertEquals(5, s.coletadas)
        assertEquals(3, s.mortas) // Cavalo + Morta + Errada
        assertEquals(1, s.cavalos)
        assertEquals(1, s.erradas)
        assertEquals(20, s.frutos)
        assertEquals(10f, s.mediaFrutos, 0.001f) // 20 frutos / 2 plantas normais
    }

    @Test
    fun estatisticas_rotaVazia_naoDivideporZero() {
        val s = emptyList<RotaColeta>().estatisticas()
        assertEquals(0f, s.percentual, 0f)
        assertEquals(0f, s.mediaFrutos, 0f)
    }

    @Test
    fun proximoPendente_avancaDepoisDoAtualEDaVoltaNoFim() {
        val r = rota()
        assertEquals(2, proximoPendente(r, 0))
        assertEquals(4, proximoPendente(r, 2))
        // a partir do último pendente, volta ao primeiro que sobrou
        assertEquals(2, proximoPendente(r, 4))
    }

    @Test
    fun proximoPendente_tudoColetado_retornaNulo() {
        val r = rota().map { it.copy(coletado = true) }
        assertNull(proximoPendente(r, 0))
    }

    @Test
    fun observacao_serializaEDeserializaOcorrencias() {
        val obs = listOf("Vassoura-de-bruxa", "Monilíase").comoObservacao()
        assertEquals("Vassoura-de-bruxa|Monilíase", obs)
        assertEquals(listOf("Vassoura-de-bruxa", "Monilíase"), obs.ocorrencias())
        assertEquals(emptyList<String>(), "".ocorrencias())
    }

    @Test
    fun contadorParcelaEPlanta_garanteMinimoUmEReset() {
        var parcela = 1
        var planta = 1

        parcela = (parcela - 1).coerceAtLeast(1)
        planta = (planta - 1).coerceAtLeast(1)
        assertEquals(1, parcela)
        assertEquals(1, planta)

        parcela += 1
        planta += 4
        assertEquals(2, parcela)
        assertEquals(5, planta)

        parcela = 1
        planta = 1
        assertEquals(1, parcela)
        assertEquals(1, planta)
    }

    @Test
    fun importacaoPlanilhaCeplac_preservaOrdemPlantIdBlocoEHistorico() {
        val csv = listOf(
            "plant_id,Ensaio ,Bloco,Parcela,Planta,Clone,Ordem,SitPla2023c,SitPla2023L",
            "SCP-16005_TC-1801_par1_pla1_c0226,TC-1801,1,1,1,SCP-16005,1,N11_P0_E0_M0_V0,nnnnnnnnnnn",
            "SCP-16005_TC-1801_par1_pla4_c0226,TC-1801,1,1,4,SCP-16005,4,N0_P0_E0_M11_V0,MMMMMMMMMMM",
            "PS-13.19_TC-1801_par88_pla1_c0226,TC-1801,2,88,1,PS-13.19,351,N11_P0_E0_M0_V0,nnnnnnnnnnn"
        )
        // Índices: Clone=5, Parcela=3, Planta=4
        val rota = parseLinhasCSVComIndices(csv, idxClone = 5, idxParcela = 3, idxPlanta = 4)
        assertEquals(3, rota.size)

        val p1 = rota[0]
        assertEquals("SCP-16005_TC-1801_par1_pla1_c0226", p1.idEfetivo)
        assertEquals("TC-1801", p1.ensaio)
        assertEquals("1", p1.bloco)
        assertEquals("1", p1.parcela)
        assertEquals(1, p1.planta)
        assertEquals("SCP-16005", p1.clone)
        assertEquals(1, p1.ordem)
        assertFalse(p1.coletado)
        assertNull(p1.alertaHistoricoFalha)

        val p2 = rota[1]
        assertEquals(4, p2.ordem)
        assertEquals("MMMMMMMMMMM", p2.sitPla2023L)
        assertNotNull(p2.alertaHistoricoFalha)

        val p3 = rota[2]
        assertEquals("PS-13.19", p3.clone)
        assertEquals("2", p3.bloco)
        assertEquals("88", p3.parcela)
        assertEquals(351, p3.ordem)
        assertEquals("PS-13.19", cloneDoPlantId(p3.idEfetivo))
    }

    @Test
    fun importacaoPlanilhaCeplacParcialmenteColetada_reconheceColetasPrevias() {
        val csv = listOf(
            "plant_id,Ensaio ,Bloco,Parcela,Planta,Clone,Ordem,SitPla2023c,SitPla2023L,SitPla,NFPequenos,NFMedios,NFAdultos,NFMaduros,NFVassoura,NFPodridao,NFOutrasPerdas,NVTotal,CorFruto,NotaVirus",
            "SCP-16005_TC-1801_par1_pla1_c0226,TC-1801,1,1,1,SCP-16005,1,N11_P0_E0_M0_V0,nnnnnnnnnnn,Normal,13,2,1,,,,,,Roxo,0",
            "SCP-16005_TC-1801_par1_pla4_c0226,TC-1801,1,1,4,SCP-16005,4,N0_P0_E0_M11_V0,MMMMMMMMMMM,Cavalo,,,,,,,,,,",
            "SCP-17202_TC-1801_par2_pla1_c0226,TC-1801,1,2,1,SCP-17202,6,N11_P0_E0_M0_V0,nnnnnnnnnnn,,,,,,,,,,,"
        )
        val rota = parseLinhasCSVComIndices(csv, idxClone = 5, idxParcela = 3, idxPlanta = 4)
        assertEquals(3, rota.size)

        // Planta 1: Normal com 13+2+1 = 16 frutos e Cor Roxo
        assertTrue(rota[0].coletado)
        assertEquals("Normal", rota[0].sitPla)
        assertEquals("16", rota[0].qtdFrutos)
        assertEquals("Roxo", rota[0].corFruto)
        assertEquals(13, rota[0].nfPequenos)
        assertEquals(2, rota[0].nfMedios)
        assertEquals(1, rota[0].nfAdultos)

        // Planta 2: Cavalo (inativa)
        assertTrue(rota[1].coletado)
        assertTrue(rota[1].isInativa)
        assertEquals("Cavalo", rota[1].sitPla)
        assertTrue(SituacaoPlanta.ehInativa(rota[1].qtdFrutos))

        // Planta 3: Ainda pendente
        assertFalse(rota[2].coletado)
        assertEquals(2, proximoPendente(rota, 0))
    }
}
