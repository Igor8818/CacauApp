package com.example.myapplication

import com.example.myapplication.userinterface.CaixaAuditavel
import com.example.myapplication.userinterface.EstadoFruto
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.min
import kotlin.math.round

/**
 * Testes unitários para as invariantes matemáticas de Letterbox e Projeção Geométrica (SPEC-002).
 */
class LetterboxAndGeometryUnitTest {

    @Test
    fun testLetterboxCalculations_4_3_Ratio() {
        // Frame típico de câmera 4:3 portrait (960 x 1280) e tensor de entrada 640 x 640
        val imgW = 960f
        val imgH = 1280f
        val inputW = 640f
        val inputH = 640f

        val scale = min(inputW / imgW, inputH / imgH)
        val newW = round(imgW * scale).toInt()
        val newH = round(imgH * scale).toInt()
        val padX = (inputW - newW) / 2f
        val padY = (inputH - newH) / 2f

        // imgH é a dimensão restritiva (1280 > 960) -> scale = 640 / 1280 = 0.5f
        assertEquals(0.5f, scale, 0.0001f)
        assertEquals(480, newW)
        assertEquals(640, newH)
        assertEquals(80f, padX, 0.0001f) // (640 - 480) / 2 = 80px de borda cinza nas laterais
        assertEquals(0f, padY, 0.0001f)
    }

    @Test
    fun testLetterboxCalculations_16_9_Ratio() {
        // Sensor widescreen 16:9 portrait (1080 x 1920) e tensor de entrada 640 x 640
        val imgW = 1080f
        val imgH = 1920f
        val inputW = 640f
        val inputH = 640f

        val scale = min(inputW / imgW, inputH / imgH)
        val newW = round(imgW * scale).toInt()
        val newH = round(imgH * scale).toInt()
        val padX = (inputW - newW) / 2f
        val padY = (inputH - newH) / 2f

        assertEquals(640f / 1920f, scale, 0.0001f)
        assertEquals(360, newW)
        assertEquals(640, newH)
        assertEquals(140f, padX, 0.0001f)
        assertEquals(0f, padY, 0.0001f)
    }

    @Test
    fun testInverseProjection_TensorToOriginalImage() {
        // Objeto centralizado no frame 960x1280 (Letterbox 640x640 com padX=80, scale=0.5)
        val imgW = 960f
        val imgH = 1280f
        val inputW = 640f
        val inputH = 640f
        val scale = 0.5f
        val padX = 80f
        val padY = 0f

        // Caixa detectada no tensor: centro em (320, 320) com largura 100 e altura 200
        val x1Tensor = 270f
        val y1Tensor = 220f
        val x2Tensor = 370f
        val y2Tensor = 420f

        val left = ((x1Tensor - padX) / scale).coerceIn(0f, imgW)
        val top = ((y1Tensor - padY) / scale).coerceIn(0f, imgH)
        val right = ((x2Tensor - padX) / scale).coerceIn(0f, imgW)
        val bottom = ((y2Tensor - padY) / scale).coerceIn(0f, imgH)

        // (270 - 80) / 0.5 = 380px no bitmap original
        assertEquals(380f, left, 0.01f)
        // (220 - 0) / 0.5 = 440px no bitmap original
        assertEquals(440f, top, 0.01f)
        // (370 - 80) / 0.5 = 580px
        assertEquals(580f, right, 0.01f)
        // (420 - 0) / 0.5 = 840px
        assertEquals(840f, bottom, 0.01f)

        // Verificação de largura e altura projetadas
        assertEquals(200f, right - left, 0.01f) // 100 / 0.5 = 200px
        assertEquals(400f, bottom - top, 0.01f) // 200 / 0.5 = 400px
    }

    @Test
    fun testInverseProjection_ClampingBoundaries() {
        // Caixa cujas coordenadas ultrapassam as bordas da imagem original
        val imgW = 960f
        val imgH = 1280f
        val scale = 0.5f
        val padX = 80f
        val padY = 0f

        val x1Tensor = 20f // Menor que o padX (80px), na borda cinza
        val y1Tensor = -50f
        val x2Tensor = 700f // Maior que 640px
        val y2Tensor = 1500f

        val left = ((x1Tensor - padX) / scale).coerceIn(0f, imgW)
        val top = ((y1Tensor - padY) / scale).coerceIn(0f, imgH)
        val right = ((x2Tensor - padX) / scale).coerceIn(0f, imgW)
        val bottom = ((y2Tensor - padY) / scale).coerceIn(0f, imgH)

        assertEquals(0f, left, 0.0f)
        assertEquals(0f, top, 0.0f)
        assertEquals(imgW, right, 0.0f)
        assertEquals(imgH, bottom, 0.0f)
    }

    @Test
    fun testIoUCalculation_IdenticalAndDisjoint() {
        val boxA = CaixaAuditavel(0.1f, 0.1f, 0.5f, 0.5f, EstadoFruto.SADIO)

        // Idêntica -> IoU = 1.0
        val iouSame = iouNorm(boxA, 0.1f, 0.1f, 0.5f, 0.5f)
        assertEquals(1.0f, iouSame, 0.001f)

        // Disjunta -> IoU = 0.0
        val iouDisjoint = iouNorm(boxA, 0.6f, 0.6f, 0.9f, 0.9f)
        assertEquals(0.0f, iouDisjoint, 0.001f)

        // Sobreposição parcial
        val iouPartial = iouNorm(boxA, 0.3f, 0.3f, 0.7f, 0.7f)
        assertTrue(iouPartial > 0.1f && iouPartial < 0.3f)
    }

    @Test
    fun testAntiResurrection_DetectionThreshold() {
        // Simula caixa que o usuário excluiu manualmente
        val caixaExcluida = CaixaAuditavel(0.2f, 0.3f, 0.6f, 0.7f, EstadoFruto.SADIO)

        // Nova detecção predita pela inferência da foto sobre a mesma fruta
        val novaPredicaoL = 0.22f
        val novaPredicaoT = 0.31f
        val novaPredicaoR = 0.59f
        val novaPredicaoB = 0.69f

        val iou = iouNorm(caixaExcluida, novaPredicaoL, novaPredicaoT, novaPredicaoR, novaPredicaoB)

        // O IoU deve ser superior a 0.35f, garantindo que o sistema reconheça como o fruto excluído
        assertTrue("IoU ($iou) deve ser > 0.35 para barrar ressurreição", iou > 0.35f)
    }

    private fun iouNorm(c: CaixaAuditavel, l: Float, t: Float, r: Float, b: Float): Float {
        val interL = maxOf(c.normLeft, l)
        val interT = maxOf(c.normTop, t)
        val interR = minOf(c.normRight, r)
        val interB = minOf(c.normBottom, b)
        val w = maxOf(0f, interR - interL)
        val h = maxOf(0f, interB - interT)
        val inter = w * h
        val area1 = (c.normRight - c.normLeft) * (c.normBottom - c.normTop)
        val area2 = (r - l) * (b - t)
        val union = area1 + area2 - inter
        return if (union <= 0f) 0f else inter / union
    }
}
