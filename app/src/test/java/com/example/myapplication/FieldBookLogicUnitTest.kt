package com.example.myapplication

import com.example.myapplication.data.RotaColeta
import com.example.myapplication.data.comoObservacao
import com.example.myapplication.data.ocorrencias
import com.example.myapplication.userinterface.screens.estatisticas
import com.example.myapplication.userinterface.screens.proximoPendente
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
}
