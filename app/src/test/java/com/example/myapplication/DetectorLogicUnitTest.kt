package com.example.myapplication

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.exp

/**
 * Testes unitários para a lógica geométrica e matemática do detector:
 * IoU, NMS, sigmoid e mapeamento reverso de Letterbox.
 */
class DetectorLogicUnitTest {

    private fun sigmoid(x: Float) = (1f / (1f + exp(-x.toDouble()))).toFloat()

    private fun iouCorners(a: FloatArray, b: FloatArray): Float {
        val interLeft   = maxOf(a[0], b[0])
        val interTop    = maxOf(a[1], b[1])
        val interRight  = minOf(a[2], b[2])
        val interBottom = minOf(a[3], b[3])
        val interArea   = maxOf(0f, interRight - interLeft) * maxOf(0f, interBottom - interTop)
        val areaA = (a[2] - a[0]) * (a[3] - a[1])
        val areaB = (b[2] - b[0]) * (b[3] - b[1])
        val union = areaA + areaB - interArea
        return if (union <= 0f) 0f else interArea / union
    }

    private fun nms(boxes: List<FloatArray>, iouThresh: Float = 0.45f): List<FloatArray> {
        val sorted = boxes.sortedByDescending { it[4] }.toMutableList()
        val result = mutableListOf<FloatArray>()
        while (sorted.isNotEmpty()) {
            val best = sorted.removeAt(0)
            result.add(best)
            sorted.removeAll { iouCorners(best, it) > iouThresh }
        }
        return result
    }

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
        // Área A: 100x100 = 10000; Área B: 100x100 = 10000.
        // Interseção: [50, 50, 100, 100] = 50x50 = 2500.
        // União: 10000 + 10000 - 2500 = 17500.
        // IoU: 2500 / 17500 = 0.1428...
        val boxA = floatArrayOf(0f, 0f, 100f, 100f)
        val boxB = floatArrayOf(50f, 50f, 150f, 150f)
        assertEquals(2500f / 17500f, iouCorners(boxA, boxB), 0.005f)
    }

    @Test
    fun testNMS_eliminaCaixasSobrepostasComMenorScore() {
        val box1 = floatArrayOf(10f, 10f, 50f, 50f, 0.92f) // Maior confiança
        val box2 = floatArrayOf(12f, 12f, 52f, 52f, 0.65f) // Sobreposta com menor confiança
        val box3 = floatArrayOf(100f, 100f, 140f, 140f, 0.88f) // Fruto isolado

        val suprimidos = nms(listOf(box1, box2, box3), 0.45f)
        assertEquals(2, suprimidos.size)
        assertEquals(0.92f, suprimidos[0][4], 0.001f)
        assertEquals(0.88f, suprimidos[1][4], 0.001f)
    }

    @Test
    fun testMapeamentoReversoLetterbox() {
        // Imagem original: 1000x2000 (proporção 1:2)
        // Rede: 300x300
        val imgW = 1000f
        val imgH = 2000f
        val inputSize = 300f

        val scale = minOf(inputSize / imgW, inputSize / imgH) // scale = 300 / 2000 = 0.15
        val scaledW = imgW * scale // 150
        val scaledH = imgH * scale // 300
        val padX = (inputSize - scaledW) / 2f // (300 - 150) / 2 = 75
        val padY = (inputSize - scaledH) / 2f // 0

        // Ponto no centro da imagem no espaço de letterbox (150, 150)
        val cxLetterbox = 150f
        val cyLetterbox = 150f

        val origX = (cxLetterbox - padX) / scale
        val origY = (cyLetterbox - padY) / scale

        assertEquals(500f, origX, 0.01f) // Deve mapear de volta exatamente para o centro (500)
        assertEquals(1000f, origY, 0.01f) // Deve mapear de volta exatamente para o centro (1000)
    }
}
