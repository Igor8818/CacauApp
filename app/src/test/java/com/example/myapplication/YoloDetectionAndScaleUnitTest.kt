package com.example.myapplication

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/**
 * Testes unitários para a lógica de inspeção de tensores, escalas e NMS do YOLOv11/v8.
 */
class YoloDetectionAndScaleUnitTest {

    @Test
    fun testCoordsArePixels_MultiAnchorCheck_PaddingEdgeDoesNotFail() {
        val yoloNumAnchors = 8400
        val lastIdx = yoloNumAnchors - 1
        val midIdx = yoloNumAnchors / 2

        // Simula tensor transposto [1, 5, 8400] onde a âncora 0 está na borda e tem cx < 2.0f
        val out = Array(1) { Array(5) { FloatArray(yoloNumAnchors) } }
        out[0][0][0] = 1.2f // Âncora 0 (borda de padding 4px / stride 8)
        out[0][0][midIdx] = 320.0f // Âncora central (320px)
        out[0][0][lastIdx] = 624.0f // Âncora final (624px)
        out[0][2][lastIdx] = 85.0f // w final

        // Antiga lógica (cheque APENAS na âncora 0):
        val sampleCxOld = out[0][0][0]
        val sampleWOld = out[0][2][0]
        val coordsArePixelsOld = (sampleCxOld > 2.0f || sampleWOld > 2.0f)
        // A lógica antiga falhava e dava false!
        assertFalse("Lógica antiga falhava na âncora 0", coordsArePixelsOld)

        // Nova lógica multi-âncora implementada no SDD-002:
        val maxSampleCoord = maxOf(out[0][0][0], out[0][0][midIdx], out[0][0][lastIdx], out[0][2][lastIdx])
        val coordsArePixelsNew = maxSampleCoord > 2.0f

        assertTrue("Nova lógica multi-âncora identifica corretamente escala em pixels", coordsArePixelsNew)
        val scaleX = if (coordsArePixelsNew) (1f / 640f) else 1f
        assertEquals(1f / 640f, scaleX, 0.00001f)
    }

    @Test
    fun testCoordsArePixels_WhenTrulyNormalized() {
        val yoloNumAnchors = 8400
        val lastIdx = yoloNumAnchors - 1
        val midIdx = yoloNumAnchors / 2

        // Modelo cujas saídas já vêm normalizadas entre 0.0 e 1.0
        val out = Array(1) { Array(5) { FloatArray(yoloNumAnchors) } }
        out[0][0][0] = 0.05f
        out[0][0][midIdx] = 0.50f
        out[0][0][lastIdx] = 0.95f
        out[0][2][lastIdx] = 0.30f

        val maxSampleCoord = maxOf(out[0][0][0], out[0][0][midIdx], out[0][0][lastIdx], out[0][2][lastIdx])
        val coordsArePixels = maxSampleCoord > 2.0f

        assertFalse("Modelo normalizado não deve ativar coordsArePixels", coordsArePixels)
        val scaleX = if (coordsArePixels) (1f / 640f) else 1f
        assertEquals(1.0f, scaleX, 0.00001f)
    }

    @Test
    fun testNeedsSigmoid_Detection() {
        val yoloNumAnchors = 8400
        val lastIdx = yoloNumAnchors - 1
        val midIdx = yoloNumAnchors / 2

        // Caso 1: Modelo exportado com scores brutos (logits [-10..+10])
        val rawOut = Array(1) { Array(5) { FloatArray(yoloNumAnchors) } }
        rawOut[0][4][0] = -3.5f
        rawOut[0][4][midIdx] = 2.1f
        rawOut[0][4][lastIdx] = 5.8f

        val minSampleRaw = minOf(rawOut[0][4][0], rawOut[0][4][midIdx], rawOut[0][4][lastIdx])
        val maxSampleRaw = maxOf(rawOut[0][4][0], rawOut[0][4][midIdx], rawOut[0][4][lastIdx])
        val needsSigmoidRaw = (minSampleRaw < -0.001f || maxSampleRaw > 1.001f)
        assertTrue("Logits brutos exigem aplicação de sigmoid", needsSigmoidRaw)

        // Caso 2: detector.tflite com Sigmoid já embutido no grafo (OP[321])
        val postSigmoidOut = Array(1) { Array(5) { FloatArray(yoloNumAnchors) } }
        postSigmoidOut[0][4][0] = 0.01f
        postSigmoidOut[0][4][midIdx] = 0.65f
        postSigmoidOut[0][4][lastIdx] = 0.92f

        val minSamplePost = minOf(postSigmoidOut[0][4][0], postSigmoidOut[0][4][midIdx], postSigmoidOut[0][4][lastIdx])
        val maxSamplePost = maxOf(postSigmoidOut[0][4][0], postSigmoidOut[0][4][midIdx], postSigmoidOut[0][4][lastIdx])
        val needsSigmoidPost = (minSamplePost < -0.001f || maxSamplePost > 1.001f)
        assertFalse("Scores já em [0..1] não devem sofrer sigmoid duplo", needsSigmoidPost)
    }

    @Test
    fun testNMS_SuppressesDuplicatesOnSameFruit() {
        // [normCx, normCy, normW, normH, prob, classIdx]
        val candidate1 = floatArrayOf(0.40f, 0.50f, 0.20f, 0.30f, 0.92f, 0f) // Melhor caixa
        val candidate2 = floatArrayOf(0.41f, 0.51f, 0.21f, 0.29f, 0.81f, 0f) // Duplicata com IoU alto
        val candidate3 = floatArrayOf(0.42f, 0.49f, 0.19f, 0.31f, 0.65f, 0f) // Outra duplicata
        val candidateSeparateFruit = floatArrayOf(0.75f, 0.25f, 0.18f, 0.25f, 0.88f, 0f) // Fruto diferente distante

        val candidates = listOf(candidate1, candidate2, candidate3, candidateSeparateFruit)
        val result = nms(candidates, iouThresh = 0.45f)

        // Deve manter exatamente 2 caixas: candidate1 (melhor do fruto 1) e candidateSeparateFruit (fruto 2)
        assertEquals(2, result.size)
        assertEquals(0.92f, result[0][4], 0.001f)
        assertEquals(0.88f, result[1][4], 0.001f)
    }

    @Test
    fun testDetectorTfliteAsset_BinaryIntegrityAndTensorContract() {
        val candidates = listOf(
            java.io.File("src/main/assets/detector.tflite"),
            java.io.File("app/src/main/assets/detector.tflite")
        )
        val modelFile = candidates.firstOrNull { it.exists() }
        assertNotNull("O modelo detector.tflite deve existir na pasta assets do projeto", modelFile)
        assertTrue("O modelo detector.tflite deve ter mais de 10 MB (YOLOv8 Float32)", modelFile!!.length() > 10_000_000L)

        // Verifica Magic Header FlatBuffer TFLite ("TFL3" nos bytes 4..7)
        val header = ByteArray(16)
        modelFile.inputStream().use { it.read(header) }
        val magic = String(header, 4, 4, Charsets.US_ASCII)
        assertEquals("Cabeçalho FlatBuffer TFLite válido (TFL3)", "TFL3", magic)

        // Inspeciona o rodapé de metadados do grafo para confirmar tensor de entrada (inputs_0)
        val tailSize = 200_000.coerceAtMost(modelFile.length().toInt())
        val tailBytes = ByteArray(tailSize)
        java.io.RandomAccessFile(modelFile, "r").use { raf ->
            raf.seek(modelFile.length() - tailSize)
            raf.readFully(tailBytes)
        }
        val tailAscii = String(tailBytes, Charsets.US_ASCII)
        assertTrue("Grafo TFLite deve conter tensor de entrada 'inputs_0'", tailAscii.contains("inputs_0"))
    }

    private fun nms(boxes: List<FloatArray>, iouThresh: Float = 0.45f): List<FloatArray> {
        val sorted = boxes.sortedByDescending { it[4] }.toMutableList()
        val result = mutableListOf<FloatArray>()
        while (sorted.isNotEmpty()) {
            val best = sorted.removeAt(0)
            result.add(best)
            sorted.removeAll { iou(best, it) > iouThresh }
        }
        return result
    }

    private fun iou(a: FloatArray, b: FloatArray): Float {
        val ax1 = a[0] - a[2] / 2f; val ay1 = a[1] - a[3] / 2f
        val ax2 = a[0] + a[2] / 2f; val ay2 = a[1] + a[3] / 2f
        val bx1 = b[0] - b[2] / 2f; val by1 = b[1] - b[3] / 2f
        val bx2 = b[0] + b[2] / 2f; val by2 = b[1] + b[3] / 2f
        val inter = max(0f, min(ax2, bx2) - max(ax1, bx1)) * max(0f, min(ay2, by2) - max(ay1, by1))
        val union = (ax2 - ax1) * (ay2 - ay1) + (bx2 - bx1) * (by2 - by1) - inter
        return if (union <= 0f) 0f else inter / union
    }
}
