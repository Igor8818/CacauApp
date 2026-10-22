package com.example.myapplication

import com.example.myapplication.userinterface.util.caixaValidaParaCacau
import com.example.myapplication.userinterface.util.fundirMultiEscalaWbf
import com.example.myapplication.userinterface.util.iosContainment
import com.example.myapplication.userinterface.util.iouCorners
import com.example.myapplication.userinterface.util.nmsAprimorado
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.exp

/**
 * Testes unitários para a lógica geométrica, NMS por contenção (IoS),
 * fusão multi-escala (WBF) e validação de caixas de frutos de cacau.
 */
class DetectorLogicUnitTest {

    private fun sigmoid(x: Float) = (1f / (1f + exp(-x.toDouble()))).toFloat()

    @Test
    fun testSigmoid_valoresExtremos() {
        assertEquals(0.5f, sigmoid(0f), 0.001f)
        assertTrue(sigmoid(10f) > 0.999f)
        assertTrue(sigmoid(-10f) < 0.001f)
    }

    @Test
    fun testIoU_caixasIdenticas_retornaUm() {
        val boxA = floatArrayOf(10f, 10f, 50f, 50f)
        val boxB = floatArrayOf(10f, 10f, 50f, 50f)
        assertEquals(1f, iouCorners(boxA, boxB), 0.001f)
    }

    @Test
    fun testIoU_caixasSemIntersecao_retornaZero() {
        val boxA = floatArrayOf(0f, 0f, 10f, 10f)
        val boxB = floatArrayOf(20f, 20f, 30f, 30f)
        assertEquals(0f, iouCorners(boxA, boxB), 0.001f)
    }

    @Test
    fun testIoU_intersecaoParcial() {
        val boxA = floatArrayOf(0f, 0f, 100f, 100f)
        val boxB = floatArrayOf(50f, 50f, 150f, 150f)
        assertEquals(2500f / 17500f, iouCorners(boxA, boxB), 0.005f)
    }

    @Test
    fun testIoS_subCaixaContidaNoMesmoFruto_suprimidaPeloNmsAprimorado() {
        // Fruto inteiro (100x200) e sub-caixa na metade iluminada do mesmo fruto (80x80 dentro dele)
        // IoU seria apenas 6400 / 20000 = 0.32 (< 0.45), mas IoS = 1.0 (> 0.65)
        val frutoInteiro = floatArrayOf(100f, 100f, 200f, 300f, 0.85f, 0f)
        val subCaixaInterna = floatArrayOf(110f, 120f, 190f, 200f, 0.62f, 0f)

        assertTrue(iouCorners(frutoInteiro, subCaixaInterna) < 0.40f)
        assertEquals(1.0f, iosContainment(frutoInteiro, subCaixaInterna), 0.001f)

        val resultado = nmsAprimorado(listOf(frutoInteiro, subCaixaInterna))
        assertEquals(1, resultado.size)
        // Garante que a caixa do fruto inteiro não encolheu por causa da sub-caixa parcial
        assertEquals(100f, resultado[0][0], 0.5f)
        assertEquals(300f, resultado[0][3], 0.5f)
    }

    @Test
    fun testNMS_eliminaCaixasSobrepostasERefinaWbf() {
        val box1 = floatArrayOf(10f, 10f, 50f, 50f, 0.90f, 0f)
        val box2 = floatArrayOf(12f, 12f, 52f, 52f, 0.60f, 0f)
        val box3 = floatArrayOf(100f, 100f, 140f, 140f, 0.88f, 0f)

        val suprimidos = nmsAprimorado(listOf(box1, box2, box3), 0.42f, 0.65f)
        assertEquals(2, suprimidos.size)
        assertEquals(0.90f, suprimidos[0][4], 0.001f)
        // WBF entre box1 (peso 0.9) e box2 (peso 0.6) deve situar left entre 10 e 12 (10.8)
        assertEquals(10.8f, suprimidos[0][0], 0.1f)
        assertEquals(0.88f, suprimidos[1][4], 0.001f)
    }

    @Test
    fun testFusaoMultiEscalaWbf_consensoRecuperaFrutoPequenoSemDuplicar() {
        // Fruto detectado na passada Global (0) com score 0.23 e no Tile Superior (1) com score 0.24
        // Com limiar 0.25, sozinho cairia fora, mas visto em 2 escalas ganha +0.05 -> 0.29 >= 0.25!
        val global = floatArrayOf(200f, 300f, 260f, 390f, 0.23f, 0f, 0f)
        val tileSup = floatArrayOf(202f, 302f, 258f, 388f, 0.24f, 0f, 1f)

        val fundidos = fundirMultiEscalaWbf(
            candidatos = listOf(global, tileSup),
            scoreThresholdFinal = 0.25f
        )
        assertEquals(1, fundidos.size)
        assertEquals(0.29f, fundidos[0][4], 0.005f)
    }

    @Test
    fun testFiltroGeometricoCacau_rejeitaGalhosFinosECaixasGigantes() {
        // Fruto normal 80x130 numa foto 1200x1600 -> válido
        assertTrue(caixaValidaParaCacau(100f, 200f, 180f, 330f, 1200f, 1600f))
        // Artefato de galho fino (largura 10, altura 200 -> aspect 0.05) -> inválido
        assertFalse(caixaValidaParaCacau(100f, 200f, 110f, 400f, 1200f, 1600f))
        // Caixa cobrindo 80% da foto inteira -> inválido
        assertFalse(caixaValidaParaCacau(0f, 0f, 1150f, 1500f, 1200f, 1600f))
    }

    @Test
    fun testMapeamentoReversoLetterbox() {
        val imgW = 1000f
        val imgH = 2000f
        val inputSize = 300f

        val scale = minOf(inputSize / imgW, inputSize / imgH)
        val scaledW = imgW * scale
        val scaledH = imgH * scale
        val padX = (inputSize - scaledW) / 2f
        val padY = (inputSize - scaledH) / 2f

        val cxLetterbox = 150f
        val cyLetterbox = 150f

        val origX = (cxLetterbox - padX) / scale
        val origY = (cyLetterbox - padY) / scale

        assertEquals(500f, origX, 0.01f)
        assertEquals(1000f, origY, 0.01f)
    }
}
