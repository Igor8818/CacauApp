package com.example.myapplication

import android.net.Uri
import com.example.myapplication.data.MODELOS_EMBUTIDOS
import com.example.myapplication.data.ModelConfig
import com.example.myapplication.data.RegistroCacau
import com.example.myapplication.userinterface.CaixaAuditavel
import com.example.myapplication.userinterface.EstadoFruto
import org.junit.Assert.*
import org.junit.Test

/**
 * Testes unitários para configurações do modelo, imutabilidade das caixas auditáveis e estados fitossanitários.
 */
class ModelAndStateUnitTest {

    @Test
    fun testModelConfig_FieldReadyDefaults() {
        val config = ModelConfig()

        // Verifica os novos padrões otimizados para campo (sombra sob copa/cabruca)
        assertEquals(0.30f, config.threshold, 0.001f)
        assertEquals(25, config.maxResultados)
        assertTrue(config.labelsFiltro.contains("cacau"))
        assertTrue(config.labelsFiltro.contains("fruto"))
    }

    @Test
    fun testModelosEmbutidos_ContainsDetectorTflite() {
        assertFalse(MODELOS_EMBUTIDOS.isEmpty())
        val defaultModel = MODELOS_EMBUTIDOS.first()
        assertEquals("detector.tflite", defaultModel.nomeArquivo)
        assertTrue(defaultModel.nomeExibicao.contains("CEPEC"))
        assertTrue(defaultModel.labelsDefault.contains("cacau"))
    }

    @Test
    fun testEstadoFruto_AllSanitaryClassesPresent() {
        val estados = EstadoFruto.entries.map { it.name }
        assertTrue(estados.contains("SADIO"))
        assertTrue(estados.contains("VASSOURA"))
        assertTrue(estados.contains("PODRIDAO"))
        assertTrue(estados.contains("NAO_AVALIAVEL"))

        assertEquals("Sadio", EstadoFruto.SADIO.rotuloCurto)
        assertEquals("Vassoura", EstadoFruto.VASSOURA.rotuloCurto)
        assertEquals("Podridão", EstadoFruto.PODRIDAO.rotuloCurto)
        assertEquals("Não Aval.", EstadoFruto.NAO_AVALIAVEL.rotuloCurto)
    }

    @Test
    fun testCaixaAuditavel_ImmutabilityAndCopyState() {
        val original = CaixaAuditavel(
            normLeft = 0.2f,
            normTop = 0.3f,
            normRight = 0.5f,
            normBottom = 0.7f,
            estado = EstadoFruto.SADIO,
            manual = false,
            confianca = 0.88f
        )

        // Altera o estado usando copy (imunizando recomposição Compose)
        val modificado = original.copy(estado = EstadoFruto.VASSOURA)

        // O original permanece SADIO
        assertEquals(EstadoFruto.SADIO, original.estado)
        // O modificado é VASSOURA
        assertEquals(EstadoFruto.VASSOURA, modificado.estado)
        // Coordenadas preservadas
        assertEquals(original.normLeft, modificado.normLeft, 0.001f)
        assertEquals(original.normTop, modificado.normTop, 0.001f)
        assertEquals(original.confianca, modificado.confianca, 0.001f)
        assertNotSame(original, modificado)
    }

    @Test
    fun testRegistroCacau_FrutosBreakdown() {
        val mockUri = org.mockito.Mockito.mock(Uri::class.java)
        val caixas = listOf(
            CaixaAuditavel(0.1f, 0.1f, 0.2f, 0.2f, EstadoFruto.SADIO),
            CaixaAuditavel(0.3f, 0.3f, 0.4f, 0.4f, EstadoFruto.SADIO),
            CaixaAuditavel(0.5f, 0.5f, 0.6f, 0.6f, EstadoFruto.VASSOURA),
            CaixaAuditavel(0.7f, 0.7f, 0.8f, 0.8f, EstadoFruto.PODRIDAO)
        )

        val reg = RegistroCacau(
            uri = mockUri,
            plantId = "CCN-51_Parcela1_Planta1",
            ensaioManual = "TC-1801",
            parcelaManual = "1",
            diagnostico = "4",
            data = "04/11 12:00",
            numPlanta = 1,
            lat = -14.7938,
            lng = -39.0494,
            totalFrutos = 4,
            sadios = 2,
            vassoura = 1,
            podridao = 1,
            naoAvaliavel = 0,
            caixas = caixas
        )

        assertEquals(4, reg.totalFrutos)
        assertEquals(2, reg.sadios)
        assertEquals(1, reg.vassoura)
        assertEquals(1, reg.podridao)
        assertEquals(0, reg.naoAvaliavel)
        assertEquals(4, reg.caixas.size)
        assertEquals("PENDENTE", reg.statusSync)
    }
}
