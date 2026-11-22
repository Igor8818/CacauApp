package br.gov.ceplac.cepec.cacauapp

import android.net.Uri
import br.gov.ceplac.cepec.cacauapp.data.MODELOS_EMBUTIDOS
import br.gov.ceplac.cepec.cacauapp.data.ModelConfig
import br.gov.ceplac.cepec.cacauapp.data.RegistroCacau
import br.gov.ceplac.cepec.cacauapp.userinterface.CaixaAuditavel
import br.gov.ceplac.cepec.cacauapp.userinterface.EstadoFruto
import br.gov.ceplac.cepec.cacauapp.userinterface.util.ObjectDetectorHelper
import org.junit.Assert.*
import org.junit.Test

/**
 * Testes unitários para configurações do modelo, imutabilidade das caixas auditáveis e estados fitossanitários.
 */
class ModelAndStateUnitTest {

    @Test
    fun testModelConfig_FieldReadyDefaults() {
        val config = ModelConfig()

        // Verifica os padrões otimizados para campo e suporte fitossanitário multi-classe
        assertEquals(0.30f, config.threshold, 0.001f)
        assertEquals(25, config.maxResultados)
        assertTrue(config.labelsFiltro.containsAll(listOf("PodridaoParda", "Moniliase", "Sadio", "VassouraBruxa")))
        assertTrue(config.labelsFiltro.contains("cacau"))
        assertTrue(config.labelsFiltro.contains("fruto"))
    }

    @Test
    fun testModelosEmbutidos_ContainsDoencaAndContagemModels() {
        assertTrue(MODELOS_EMBUTIDOS.size >= 2)
        val defaultModel = MODELOS_EMBUTIDOS.first()
        assertEquals("detector_doenca.tflite", defaultModel.nomeArquivo)
        assertTrue(defaultModel.nomeExibicao.contains("CEPEC"))
        assertEquals(listOf("PodridaoParda", "Moniliase", "Sadio", "VassouraBruxa"), defaultModel.labelsDefault)

        val fallbackContagem = MODELOS_EMBUTIDOS.firstOrNull { it.nomeArquivo == "detector.tflite" }
        assertNotNull("Modelo de contagem detector.tflite deve permanecer disponível", fallbackContagem)
        assertTrue(fallbackContagem!!.labelsDefault.contains("cacau"))
    }

    @Test
    fun testMapearLabelParaEstadoFruto_MultiClassAndLegacy() {
        assertEquals(EstadoFruto.PODRIDAO, br.gov.ceplac.cepec.cacauapp.userinterface.mapearLabelParaEstadoFruto("PodridaoParda"))
        assertEquals(EstadoFruto.PODRIDAO, br.gov.ceplac.cepec.cacauapp.userinterface.mapearLabelParaEstadoFruto("Moniliase"))
        assertEquals(EstadoFruto.SADIO, br.gov.ceplac.cepec.cacauapp.userinterface.mapearLabelParaEstadoFruto("Sadio"))
        assertEquals(EstadoFruto.VASSOURA, br.gov.ceplac.cepec.cacauapp.userinterface.mapearLabelParaEstadoFruto("VassouraBruxa"))
        assertEquals(EstadoFruto.SADIO, br.gov.ceplac.cepec.cacauapp.userinterface.mapearLabelParaEstadoFruto("cacau"))
        assertEquals(EstadoFruto.SADIO, br.gov.ceplac.cepec.cacauapp.userinterface.mapearLabelParaEstadoFruto(null))
    }

    @Test
    fun testResolverRotuloPorIndice_4ClassDefaultsAndCustomLabels() {
        val helper4Classes = br.gov.ceplac.cepec.cacauapp.userinterface.util.ObjectDetectorHelper.resolverRotuloPorIndice(
            classIdx = 3,
            modelLabels = emptyList(),
            customLabels = listOf("PodridaoParda", "Moniliase", "Sadio", "VassouraBruxa", "cacau", "fruto"),
            yoloNumClasses = 4,
            modelPath = "detector_doenca.tflite",
            fallback = "cacau"
        )
        assertEquals("VassouraBruxa", helper4Classes)

        val fallback4WhenGeneric = br.gov.ceplac.cepec.cacauapp.userinterface.util.ObjectDetectorHelper.resolverRotuloPorIndice(
            classIdx = 1,
            modelLabels = emptyList(),
            customLabels = emptyList(),
            yoloNumClasses = 4,
            modelPath = "custom.tflite",
            fallback = "cacau"
        )
        assertEquals("Moniliase", fallback4WhenGeneric)
    }

    @Test
    fun testModeloContagemUmaClasse_naoHerdaRotuloDeDoenca() {
        val label = br.gov.ceplac.cepec.cacauapp.userinterface.util.ObjectDetectorHelper.resolverRotuloPorIndice(
            classIdx = 0,
            modelLabels = emptyList(),
            customLabels = listOf("PodridaoParda", "Moniliase", "Sadio", "VassouraBruxa", "cacau", "fruto"),
            yoloNumClasses = 1,
            modelPath = "detector.tflite",
            fallback = "cacau"
        )
        assertEquals(EstadoFruto.SADIO, br.gov.ceplac.cepec.cacauapp.userinterface.mapearLabelParaEstadoFruto(label))
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

    @Test
    fun `refinarClasseDoencaPorMorfologiaCor distingue VassouraBruxa seca escura de PodridaoParda quente`() {
        val w = 100
        val h = 100
        // Fruto vassourado (seco/acinzentado-escuro: RGB ~ 115, 105, 102)
        val pxVassoura = (0xFF shl 24) or (115 shl 16) or (105 shl 8) or 102
        val gridVassoura = IntArray(w * h) { pxVassoura }
        val clsVassoura = ObjectDetectorHelper.refinarClasseDoencaPorMorfologiaCor(
            classIdx = 0,
            probBest = 0.88f,
            probClass0 = 0.88f,
            pixels = gridVassoura,
            width = w,
            height = h,
            x1Tensor = 10f,
            y1Tensor = 10f,
            x2Tensor = 90f,
            y2Tensor = 90f
        )
        assertEquals(3, clsVassoura)

        // Fruto com Podridão Parda (fundo amarelo/marrom quente: RGB ~ 215, 190, 140)
        val pxPodridao = (0xFF shl 24) or (215 shl 16) or (190 shl 8) or 140
        val gridPodridao = IntArray(w * h) { pxPodridao }
        val clsPodridao = ObjectDetectorHelper.refinarClasseDoencaPorMorfologiaCor(
            classIdx = 0,
            probBest = 0.90f,
            probClass0 = 0.90f,
            pixels = gridPodridao,
            width = w,
            height = h,
            x1Tensor = 10f,
            y1Tensor = 10f,
            x2Tensor = 90f,
            y2Tensor = 90f
        )
        assertEquals(0, clsPodridao)

        // Frutos secos marrons-escuros típicos de Vassoura de Bruxa (chirimoya / seca)
        for ((r, g, b) in listOf(Triple(95, 72, 58), Triple(130, 105, 85))) {
            val pxSecoEscuro = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            val gridSecoEscuro = IntArray(w * h) { pxSecoEscuro }
            val clsSeco = ObjectDetectorHelper.refinarClasseDoencaPorMorfologiaCor(
                classIdx = 0,
                probBest = 0.85f,
                probClass0 = 0.85f,
                pixels = gridSecoEscuro,
                width = w,
                height = h,
                x1Tensor = 10f,
                y1Tensor = 10f,
                x2Tensor = 90f,
                y2Tensor = 90f
            )
            assertEquals("Esperado VassouraBruxa (3) para RGB($r,$g,$b)", 3, clsSeco)
        }
    }
}
