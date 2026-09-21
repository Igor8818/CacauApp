package com.example.myapplication.userinterface.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.net.Uri
import android.util.Log
import com.example.myapplication.data.DetectionCategory
import com.example.myapplication.data.DetectionResult
import org.tensorflow.lite.Interpreter
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.exp

/**
 * Helper de deteccao que usa APENAS o Interpreter direto do TFLite.
 * Tipos proprios DetectionResult/DetectionCategory substituem os da
 * task-vision (removida por incompatibilidade de AndroidManifest com AGP 9).
 */
class ObjectDetectorHelper(
    val context: Context,
    var detectorListener: DetectorListener?,
    var modelSource: ModelSource = ModelSource.Asset("detector.tflite"),
    var scoreThreshold: Float = 0.55f,
    var maxResults: Int = 10
) {
    sealed class ModelSource {
        data class Asset(val fileName: String) : ModelSource()
        data class External(val uri: Uri, val displayName: String) : ModelSource()
        data class InternalFile(val path: String, val displayName: String) : ModelSource()
    }

    private var interpreter: Interpreter? = null

    // Configuracao do modelo — preenchida ao inspecionar shapes
    private var inputSize   = 300
    private var numAnchors  = 0
    private var numClasses  = 0
    private var transposed  = false   // true → [1, 4+C, anchors]; false → [1, anchors, 4+C]
    private var coordScale  = 1f

    private var applySignoid = true

    var ultimoErro: String? = null
        private set

    var lastBitmapWidth:  Float = 1f; private set
    var lastBitmapHeight: Float = 1f; private set

    private var loadedPath: String? = null

    init { setup() }

    fun setListener(l: DetectorListener) { detectorListener = l }

    fun reloadModel(source: ModelSource, threshold: Float = scoreThreshold, maxRes: Int = maxResults) {
        close()
        modelSource = source
        scoreThreshold = threshold
        maxResults = maxRes
        ultimoErro = null
        loadedPath = null
        setup()
    }

    private fun setup() {
        val file = resolveFile() ?: return
        if (file.absolutePath == loadedPath && interpreter != null) return

        close()

        try {
            interpreter = Interpreter(file, Interpreter.Options().apply { setNumThreads(2) })

            val outShape = interpreter!!.getOutputTensor(0).shape()
            val inShape  = interpreter!!.getInputTensor(0).shape()
            Log.d("Detector", "Input: ${inShape.toList()}  Output: ${outShape.toList()}")

            inputSize = inShape[1]  // [1, H, W, 3]

            when {
                // [1, 4+C, anchors] — transposto (YOLOv8/v11 padrao)
                outShape.size == 3 && outShape[1] in 5..300 && outShape[2] > outShape[1] -> {
                    transposed  = true
                    numClasses  = outShape[1] - 4
                    numAnchors  = outShape[2]
                }
                // [1, anchors, 4+C]
                outShape.size == 3 && outShape[2] in 5..300 && outShape[1] > outShape[2] -> {
                    transposed  = false
                    numAnchors  = outShape[1]
                    numClasses  = outShape[2] - 4
                }
                else -> {
                    transposed  = false
                    numAnchors  = if (outShape.size >= 2) outShape[1] else 0
                    numClasses  = if (outShape.size >= 3) outShape[2] - 4 else 0
                    Log.w("Detector", "Formato de output nao reconhecido: ${outShape.toList()}")
                }
            }

            coordScale = 1f
            loadedPath = file.absolutePath
            ultimoErro = null
            Log.d("Detector", "Modelo carregado — size=$inputSize anchors=$numAnchors classes=$numClasses transposto=$transposed")

        } catch (e: Exception) {
            ultimoErro = "Erro ao carregar modelo: ${e.message?.take(200)}"
            Log.e("Detector", ultimoErro!!)
            detectorListener?.onError(ultimoErro!!)
        }
    }

    fun detect(image: Bitmap) {
        lastBitmapWidth  = image.width.toFloat()
        lastBitmapHeight = image.height.toFloat()
        val interp = interpreter ?: run { setup(); interpreter ?: return }
        if (numAnchors == 0) { detectorListener?.onResults(emptyList()); return }

        try {
            // 1. Prepara input normalizado [0,1]
            val scaled = Bitmap.createScaledBitmap(image, inputSize, inputSize, true)
            val buf = ByteBuffer.allocateDirect(inputSize * inputSize * 3 * 4).order(ByteOrder.nativeOrder())
            val pixels = IntArray(inputSize * inputSize)
            scaled.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)
            for (px in pixels) {
                buf.putFloat(((px shr 16) and 0xFF) / 255f)
                buf.putFloat(((px shr 8)  and 0xFF) / 255f)
                buf.putFloat((px          and 0xFF) / 255f)
            }

            // 2. Roda inferencia
            @Suppress("UNCHECKED_CAST")
            val out = if (transposed)
                Array(1) { Array(4 + numClasses) { FloatArray(numAnchors) } } as Array<Array<FloatArray>>
            else
                Array(1) { Array(numAnchors) { FloatArray(4 + numClasses) } } as Array<Array<FloatArray>>
            interp.run(buf, out)

            // 3. Calibra coordScale
            val sampleCoord = if (transposed) out[0][0][0] else out[0][0][0]
            if (coordScale == 1f && sampleCoord > 2f) {
                coordScale = 1f / inputSize.toFloat()
                Log.d("Detector", "Coordenadas em pixels detectadas, coordScale=$coordScale")
            }

            // 4. Calibra sigmoid
            val sampleScore = if (transposed) out[0][4][0] else out[0][0][4]
            applySignoid = sampleScore < 0f || sampleScore > 1f

            // 5. Decodifica anchors
            val candidates = mutableListOf<FloatArray>()
            var maxScore = -Float.MAX_VALUE

            for (i in 0 until numAnchors) {
                val cx: Float; val cy: Float; val w: Float; val h: Float
                var rawScore = -Float.MAX_VALUE

                if (transposed) {
                    cx = out[0][0][i] * coordScale
                    cy = out[0][1][i] * coordScale
                    w  = out[0][2][i] * coordScale
                    h  = out[0][3][i] * coordScale
                    for (c in 0 until maxOf(numClasses, 1)) {
                        val s = out[0][4 + c][i]; if (s > rawScore) rawScore = s
                    }
                } else {
                    cx = out[0][i][0] * coordScale
                    cy = out[0][i][1] * coordScale
                    w  = out[0][i][2] * coordScale
                    h  = out[0][i][3] * coordScale
                    for (c in 0 until maxOf(numClasses, 1)) {
                        val s = out[0][i][4 + c]; if (s > rawScore) rawScore = s
                    }
                }

                val prob = if (applySignoid) sigmoid(rawScore) else rawScore.coerceIn(0f, 1f)
                if (prob > maxScore) maxScore = prob

                if (prob >= scoreThreshold
                    && w  > 0.01f && h  > 0.01f
                    && w  < 1.5f  && h  < 1.5f
                    && cx > 0f    && cy > 0f
                    && cx < 1.5f  && cy < 1.5f) {
                    candidates.add(floatArrayOf(cx, cy, w, h, prob))
                }
            }

            Log.d("Detector", "maxScore=${"%.3f".format(maxScore)} threshold=$scoreThreshold candidatos=${candidates.size} sigmoid=$applySignoid scale=$coordScale")

            // 6. NMS + conversao para DetectionResult proprio
            val imgW = image.width.toFloat()
            val imgH = image.height.toFloat()
            val detections = nms(candidates).take(maxResults).map { box ->
                val left   = ((box[0] - box[2] / 2f) * imgW).coerceIn(0f, imgW)
                val top    = ((box[1] - box[3] / 2f) * imgH).coerceIn(0f, imgH)
                val right  = ((box[0] + box[2] / 2f) * imgW).coerceIn(0f, imgW)
                val bottom = ((box[1] + box[3] / 2f) * imgH).coerceIn(0f, imgH)
                DetectionResult.create(
                    RectF(left, top, right, bottom),
                    listOf(DetectionCategory.create("cacau", "cacau", box[4]))
                )
            }

            Log.d("Detector", "Deteccoes finais: ${detections.size}")
            detectorListener?.onResults(detections)

        } catch (e: Exception) {
            Log.e("Detector", "Erro na deteccao: ${e.message}")
            detectorListener?.onResults(emptyList())
        }
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
        val ax1=a[0]-a[2]/2; val ay1=a[1]-a[3]/2; val ax2=a[0]+a[2]/2; val ay2=a[1]+a[3]/2
        val bx1=b[0]-b[2]/2; val by1=b[1]-b[3]/2; val bx2=b[0]+b[2]/2; val by2=b[1]+b[3]/2
        val inter = maxOf(0f,minOf(ax2,bx2)-maxOf(ax1,bx1)) * maxOf(0f,minOf(ay2,by2)-maxOf(ay1,by1))
        val union = (ax2-ax1)*(ay2-ay1) + (bx2-bx1)*(by2-by1) - inter
        return if (union <= 0f) 0f else inter / union
    }

    private fun sigmoid(x: Float) = (1f / (1f + exp(-x.toDouble()))).toFloat()

    private fun resolveFile(): File? = when (val s = modelSource) {
        is ModelSource.Asset -> try {
            File(context.cacheDir, s.fileName).also { f ->
                if (!f.exists()) context.assets.open(s.fileName).use { i ->
                    FileOutputStream(f).use { i.copyTo(it) }
                }
            }
        } catch (e: Exception) {
            ultimoErro = "Modelo '${s.fileName}' nao encontrado nos assets."
            detectorListener?.onError(ultimoErro!!); null
        }
        is ModelSource.InternalFile -> File(s.path).takeIf { it.exists() } ?: run {
            ultimoErro = "Arquivo do modelo nao encontrado. Importe novamente."
            detectorListener?.onError(ultimoErro!!); null
        }
        is ModelSource.External -> try {
            File(context.cacheDir, "custom_model_cache.tflite").also { f ->
                context.contentResolver.openInputStream(s.uri)?.use { i ->
                    FileOutputStream(f).use { i.copyTo(it) }
                }
            }
        } catch (e: Exception) {
            ultimoErro = "Nao foi possivel acessar o arquivo. Selecione novamente."
            detectorListener?.onError(ultimoErro!!); null
        }
    }

    fun fechar() = close()
    private fun close() { interpreter?.close(); interpreter = null; loadedPath = null }

    interface DetectorListener {
        fun onResults(results: List<DetectionResult>)
        fun onError(mensagem: String) {}
    }
}