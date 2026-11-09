package com.example.myapplication

import com.example.myapplication.userinterface.CaixaAuditavel
import com.example.myapplication.userinterface.DetEstilizada
import com.example.myapplication.userinterface.EstadoFruto
import com.example.myapplication.userinterface.fundirDeteccoesComSnapping
import com.example.myapplication.userinterface.iouNorm
import com.example.myapplication.userinterface.redimensionarCaixaAuditavel
import com.example.myapplication.userinterface.selecionarIndiceCaixaNoToque
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

    @Test
    fun testSelecaoCaixaNoToque_FrutosAgrupadosEscolheCentroMaisProximo() {
        // Dois frutos próximos no tronco (almofada floral):
        // Fruto 1: x in [0.20, 0.40], y in [0.20, 0.50], centro = (0.30, 0.35)
        // Fruto 2: x in [0.35, 0.55], y in [0.20, 0.50], centro = (0.45, 0.35)
        val f1 = CaixaAuditavel(0.20f, 0.20f, 0.40f, 0.50f, EstadoFruto.SADIO)
        val f2 = CaixaAuditavel(0.35f, 0.20f, 0.55f, 0.50f, EstadoFruto.VASSOURA)
        val caixas = listOf(f1, f2)

        // Toque em (0.28, 0.35): mais próximo do centro de f1 (0.30)
        val idx1 = selecionarIndiceCaixaNoToque(caixas, 0.28f, 0.35f, 0.03f, 0.03f)
        assertEquals(0, idx1)

        // Toque em (0.48, 0.35): mais próximo do centro de f2 (0.45)
        val idx2 = selecionarIndiceCaixaNoToque(caixas, 0.48f, 0.35f, 0.03f, 0.03f)
        assertEquals(1, idx2)

        // Toque longe de ambos: retorna -1 (permitirá criar caixa manual)
        val idxVazio = selecionarIndiceCaixaNoToque(caixas, 0.85f, 0.85f, 0.03f, 0.03f)
        assertEquals(-1, idxVazio)
    }

    @Test
    fun testRedimensionarCaixaAuditavel_PreservaCentroERespeitaLimites() {
        val original = CaixaAuditavel(0.40f, 0.40f, 0.60f, 0.60f, EstadoFruto.SADIO, manual = true)
        assertEquals(0.50f, original.centroX, 0.001f)
        assertEquals(0.50f, original.centroY, 0.001f)
        assertEquals(0.20f, original.largura, 0.001f)

        // Aumenta em 20%
        val ampliada = redimensionarCaixaAuditavel(original, 1.20f)
        assertEquals(0.50f, ampliada.centroX, 0.005f)
        assertEquals(0.50f, ampliada.centroY, 0.005f)
        assertEquals(0.24f, ampliada.largura, 0.005f)

        // Reduz em 20%
        val reduzida = redimensionarCaixaAuditavel(original, 0.80f)
        assertEquals(0.50f, reduzida.centroX, 0.005f)
        assertEquals(0.50f, reduzida.centroY, 0.005f)
        assertEquals(0.16f, reduzida.largura, 0.005f)

        // Respeito às bordas da tela [0, 1]
        val canto = CaixaAuditavel(0.01f, 0.01f, 0.15f, 0.15f, EstadoFruto.SADIO)
        val cantoAmpliado = redimensionarCaixaAuditavel(canto, 3.0f)
        assertTrue(cantoAmpliado.normLeft >= 0f)
        assertTrue(cantoAmpliado.normTop >= 0f)
        assertTrue(cantoAmpliado.normRight <= 1f)
        assertTrue(cantoAmpliado.normBottom <= 1f)
    }

    @Test
    fun testCoordinateSnappingPosCaptura_AtualizaCoordenadasPreservandoEstado() {
        // Simula caixa que veio do preview da câmera, levemente deslocada por tremor da mão,
        // mas que o técnico já tocou e classificou como VASSOURA
        val caixaPreview = CaixaAuditavel(
            normLeft = 0.22f,
            normTop = 0.22f,
            normRight = 0.42f,
            normBottom = 0.52f,
            estado = EstadoFruto.VASSOURA,
            manual = false,
            confianca = 0.75f
        )

        // Inferência em background sobre a foto estática retorna o fruto na posição exata da foto
        val detFotoEstatica = DetEstilizada(
            normLeft = 0.20f,
            normTop = 0.20f,
            normRight = 0.40f,
            normBottom = 0.50f,
            confianca = 0.88f,
            estado = EstadoFruto.SADIO
        )

        // Fruto novo que o preview não havia detectado, mas a foto estática pegou
        val detNovoFruto = DetEstilizada(
            normLeft = 0.65f,
            normTop = 0.60f,
            normRight = 0.85f,
            normBottom = 0.85f,
            confianca = 0.82f,
            estado = EstadoFruto.SADIO
        )

        val caixasFundidas = fundirDeteccoesComSnapping(
            caixasAtuais = listOf(caixaPreview),
            deteccoesEstaticas = listOf(detFotoEstatica, detNovoFruto),
            caixasExcluidas = emptyList()
        )

        // Deve conter 2 caixas no total
        assertEquals(2, caixasFundidas.size)

        // A primeira caixa fez SNAP para a posição exata da foto estática
        val snap = caixasFundidas[0]
        assertEquals(0.20f, snap.normLeft, 0.001f)
        assertEquals(0.20f, snap.normTop, 0.001f)
        assertEquals(0.40f, snap.normRight, 0.001f)
        assertEquals(0.50f, snap.normBottom, 0.001f)
        // CRUCIAL: O estado classificado pelo técnico (VASSOURA) foi preservado!
        assertEquals(EstadoFruto.VASSOURA, snap.estado)
        assertEquals(0.88f, snap.confianca, 0.001f)

        // A segunda caixa é o fruto novo adicionado
        val novo = caixasFundidas[1]
        assertEquals(0.65f, novo.normLeft, 0.001f)
        assertEquals(EstadoFruto.SADIO, novo.estado)
    }
}
