package com.example.myapplication.userinterface.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.util.Log
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.label.Category
import org.tensorflow.lite.task.vision.detector.Detection
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipInputStream
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/**
 * Helper de detecção que usa APENAS o Interpreter direto do TFLite.
 * Suporta automaticamente:
 * 1. Modelos YOLO (YOLOv5, YOLOv8, YOLOv11 com 1 saída contendo âncoras brutas, ex: 1x5x8400)
 *    como o modelo treinado de cacau do CEPEC.
 * 2. Modelos SSD MobileNet / TFLite PostProcess (4 saídas: caixas, classes, scores, contagem).
 * 3. Modelos quantizados (UINT8) e Float32 normalizados.
 *
 * Funciona de forma 100% universal em múltiplos dispositivos Android (Samsung A50, Redmi, etc.),
 * sem UnsatisfiedLinkError e com projeção geométrica correta das caixas verdes.
 */
class ObjectDetectorHelper(
    val context: Context,
    var detectorListener: DetectorListener?,
    var modelSource: ModelSource = ModelSource.Asset("detector_doenca.tflite"),
    var scoreThreshold: Float = 0.30f,
    var maxResults: Int = 25,
    var customLabels: List<String> = emptyList()
) {
    sealed class ModelSource {
        data class Asset(val fileName: String) : ModelSource()
        data class External(val uri: Uri, val displayName: String) : ModelSource()
        data class InternalFile(val path: String, val displayName: String) : ModelSource()
    }

    private val lock = Any()
    private var isClosed = false
    private var interpreter: Interpreter? = null

    private enum class ModelArchitecture {
        SSD_POST_PROCESS, // Modelo de 4 tensores de saída (SSD MobileNet)
        YOLO_RAW          // Modelo de saída única com âncoras (YOLOv8 1x5x8400)
    }

    private var architecture = ModelArchitecture.YOLO_RAW

    // Configuração de entrada
    private var inputWidth = 640
    private var inputHeight = 640
    private var isInputUint8 = false

    // Buffers reutilizáveis para zerar GC churn por frame
    private var letterboxBitmap: Bitmap? = null
    private var letterboxPixels: IntArray? = null
    private var inputByteBuffer: ByteBuffer? = null
    private val letterboxPaint = Paint(Paint.FILTER_BITMAP_FLAG)

    // Índices e dimensões para SSD (4 saídas)
    private var ssdNumDetections = 10
    private var ssdBoxIndex = 0
    private var ssdClassIndex = 1
    private var ssdScoreIndex = 2
    private var ssdCountIndex = 3

    // Configurações para YOLO (saída única)
    private var yoloNumAnchors = 8400
    private var yoloNumClasses = 1
    private var yoloTransposed = true

    // Rótulos do modelo
    private val modelLabels = mutableListOf<String>()

    var ultimoErro: String? = null
        private set

    @Volatile
    var lastBitmapWidth: Float = 1f
        private set
    @Volatile
    var lastBitmapHeight: Float = 1f
        private set

    private var loadedPath: String? = null

    init {
        setup()
    }

    fun setListener(l: DetectorListener?) {
        synchronized(lock) {
            detectorListener = l
        }
    }

    fun reloadModel(source: ModelSource, threshold: Float = scoreThreshold, maxRes: Int = maxResults) {
        synchronized(lock) {
            close()
            isClosed = false
            modelSource = source
            scoreThreshold = threshold
            maxResults = maxRes
            ultimoErro = null
            loadedPath = null
            setup()
        }
    }

    fun fechar() = synchronized(lock) {
        isClosed = true
        close()
    }

    private fun setup() {
        val file = resolveFile() ?: return
        if (file.absolutePath == loadedPath && interpreter != null) return

        close()

        try {
            // Inicializa Interpreter TFLite puro com 4 threads na CPU para máxima estabilidade universal
            val options = Interpreter.Options().apply {
                setNumThreads(4)
            }
            interpreter = Interpreter(file, options)

            // Inspeciona tensor de entrada
            val inTensor = interpreter!!.getInputTensor(0)
            val inShape = inTensor.shape()
            isInputUint8 = (inTensor.dataType() == DataType.UINT8)
            inputHeight = if (inShape.size >= 2 && inShape[1] > 0) inShape[1] else 640
            inputWidth = if (inShape.size >= 3 && inShape[2] > 0) inShape[2] else 640

            val numOutputs = interpreter!!.outputTensorCount
            Log.d("Detector", "Modelo carregado: input=${inShape.toList()} type=${inTensor.dataType()} outputs=$numOutputs size=${file.length()}")

            // Tenta extrair labelmap.txt se existir no zip de metadados do .tflite
            extractLabelsFromModelFile(file)

            if (numOutputs >= 4) {
                // Arquitetura SSD MobileNet / TFLite PostProcess
                architecture = ModelArchitecture.SSD_POST_PROCESS
                identifySsdOutputs()
                Log.d("Detector", "Arquitetura identificada: SSD (boxes=$ssdBoxIndex, classes=$ssdClassIndex, scores=$ssdScoreIndex, count=$ssdCountIndex, maxDets=$ssdNumDetections)")
            } else {
                // Arquitetura YOLO raw
                architecture = ModelArchitecture.YOLO_RAW
                val outShape = interpreter!!.getOutputTensor(0).shape()
                Log.d("Detector", "Arquitetura identificada: YOLO raw outShape=${outShape.toList()}")

                when {
                    // [1, 4+C, anchors] (ex: YOLOv8 padrão 1x5x8400)
                    outShape.size == 3 && outShape[1] in 5..300 && outShape[2] > outShape[1] -> {
                        yoloTransposed = true
                        yoloNumClasses = outShape[1] - 4
                        yoloNumAnchors = outShape[2]
                    }
                    // [1, anchors, 4+C]
                    outShape.size == 3 && outShape[2] in 5..300 && outShape[1] > outShape[2] -> {
                        yoloTransposed = false
                        yoloNumAnchors = outShape[1]
                        yoloNumClasses = outShape[2] - 4
                    }
                    else -> {
                        yoloTransposed = true
                        yoloNumAnchors = if (outShape.size >= 3) outShape[2] else 8400
                        yoloNumClasses = if (outShape.size >= 2) max(1, outShape[1] - 4) else 1
                    }
                }
                Log.d("Detector", "YOLO configurado: anchors=$yoloNumAnchors, classes=$yoloNumClasses, transposto=$yoloTransposed")
            }

            loadedPath = file.absolutePath
            ultimoErro = null

        } catch (e: Exception) {
            ultimoErro = "Erro ao carregar modelo: ${e.message?.take(200)}"
            Log.e("Detector", ultimoErro!!, e)
            detectorListener?.onError(ultimoErro!!)
        }
    }

    private fun identifySsdOutputs() {
        val count = interpreter!!.outputTensorCount
        for (i in 0 until count) {
            val t = interpreter!!.getOutputTensor(i)
            val shape = t.shape()
            val name = t.name().lowercase()

            when {
                // Locations: [1, N, 4]
                shape.size == 3 && shape[2] == 4 -> {
                    ssdBoxIndex = i
                    ssdNumDetections = shape[1]
                }
                // Locations transposto: [1, 4, N]
                shape.size == 3 && shape[1] == 4 -> {
                    ssdBoxIndex = i
                    ssdNumDetections = shape[2]
                }
                // Contagem total de detecções: [1]
                shape.size == 1 && shape[0] == 1 -> {
                    ssdCountIndex = i
                }
                // Classes ou Scores: [1, N]
                shape.size == 2 -> {
                    if (name.contains("score")) {
                        ssdScoreIndex = i
                    } else if (name.contains("class") || name.contains("category")) {
                        ssdClassIndex = i
                    } else {
                        if (i == 1) ssdClassIndex = i
                        if (i == 2) ssdScoreIndex = i
                    }
                }
            }
        }
    }

    private fun extractLabelsFromModelFile(file: File) {
        modelLabels.clear()
        try {
            file.inputStream().use { fis ->
                val zis = ZipInputStream(fis)
                var entry = zis.nextEntry
                while (entry != null) {
                    if (entry.name.contains("label", ignoreCase = true)) {
                        zis.bufferedReader().lineSequence().forEach { line ->
                            val trimmed = line.trim()
                            if (trimmed.isNotEmpty()) modelLabels.add(trimmed)
                        }
                        if (modelLabels.isNotEmpty()) break
                    }
                    entry = zis.nextEntry
                }
            }
        } catch (_: Exception) {
            // Metadados opcionais
        }
    }

    fun detect(image: Bitmap) {
        val resultado = executarInferencia(image) ?: return
        val listener = synchronized(lock) { detectorListener }
        listener?.onResults(resultado)
    }

    /**
     * Executa inferência de forma síncrona diretamente sobre um Bitmap (ex: foto estática em alta resolução na Tela de Revisão).
     * É thread-safe e reutiliza a calibração geométrica isomórfica (Letterbox) e NMS.
     */
    fun detectarSincrono(image: Bitmap): ResultadoDeteccao? {
        return executarInferencia(image)
    }

    private fun executarInferencia(image: Bitmap): ResultadoDeteccao? = synchronized(lock) {
        if (isClosed) return null

        val imgW = image.width.toFloat()
        val imgH = image.height.toFloat()
        if (imgW <= 0f || imgH <= 0f) return null
        lastBitmapWidth = imgW
        lastBitmapHeight = imgH

        val interp = interpreter ?: run { setup(); interpreter ?: return null }
        val finalDetections = mutableListOf<Detection>()

        try {
            // 1. Prepara buffer de entrada via Letterbox Isomórfico (mantém proporção original do fruto sem achatamento)
            val scale = min(inputWidth.toFloat() / imgW, inputHeight.toFloat() / imgH)
            val newW = kotlin.math.round(imgW * scale).toInt().coerceAtLeast(1)
            val newH = kotlin.math.round(imgH * scale).toInt().coerceAtLeast(1)
            val padX = (inputWidth - newW) / 2f
            val padY = (inputHeight - newH) / 2f

            var lbBitmap = letterboxBitmap
            if (lbBitmap == null || lbBitmap.width != inputWidth || lbBitmap.height != inputHeight) {
                lbBitmap?.recycle()
                lbBitmap = Bitmap.createBitmap(inputWidth, inputHeight, Bitmap.Config.ARGB_8888)
                letterboxBitmap = lbBitmap
            }

            val canvas = Canvas(lbBitmap)
            // Cor padrão cinza neutro do Ultralytics YOLO: RGB(114, 114, 114)
            canvas.drawColor(Color.rgb(114, 114, 114))
            val destRect = RectF(padX, padY, padX + newW, padY + newH)
            canvas.drawBitmap(image, null, destRect, letterboxPaint)

            var pixels = letterboxPixels
            val totalPixels = inputWidth * inputHeight
            if (pixels == null || pixels.size != totalPixels) {
                pixels = IntArray(totalPixels)
                letterboxPixels = pixels
            }
            lbBitmap.getPixels(pixels, 0, inputWidth, 0, 0, inputWidth, inputHeight)

            val bytesPerChannel = if (isInputUint8) 1 else 4
            val bufferCapacity = totalPixels * 3 * bytesPerChannel
            var buf = inputByteBuffer
            if (buf == null || buf.capacity() != bufferCapacity) {
                buf = ByteBuffer.allocateDirect(bufferCapacity).order(ByteOrder.nativeOrder())
                inputByteBuffer = buf
            } else {
                buf.rewind()
            }

            if (isInputUint8) {
                for (px in pixels) {
                    buf.put(((px shr 16) and 0xFF).toByte())
                    buf.put(((px shr 8)  and 0xFF).toByte())
                    buf.put((px          and 0xFF).toByte())
                }
            } else {
                for (px in pixels) {
                    buf.putFloat(((px shr 16) and 0xFF) / 255f)
                    buf.putFloat(((px shr 8)  and 0xFF) / 255f)
                    buf.putFloat((px          and 0xFF) / 255f)
                }
            }

            // 2. Executa inferência de acordo com a arquitetura
            if (architecture == ModelArchitecture.SSD_POST_PROCESS) {
                val maxDets = ssdNumDetections.coerceAtLeast(1)
                val locations = Array(1) { Array(maxDets) { FloatArray(4) } }
                val classes = Array(1) { FloatArray(maxDets) }
                val scores = Array(1) { FloatArray(maxDets) }
                val count = FloatArray(1)

                val outputMap = HashMap<Int, Any>()
                outputMap[ssdBoxIndex] = locations
                outputMap[ssdClassIndex] = classes
                outputMap[ssdScoreIndex] = scores
                outputMap[ssdCountIndex] = count

                interp.runForMultipleInputsOutputs(arrayOf(buf), outputMap)

                val numDetected = min(count[0].toInt(), maxDets).coerceAtLeast(0)

                for (i in 0 until numDetected) {
                    val score = scores[0][i]
                    if (score < scoreThreshold) continue

                    val classIdx = classes[0][i].toInt()
                    val ymin = locations[0][i][0]
                    val xmin = locations[0][i][1]
                    val ymax = locations[0][i][2]
                    val xmax = locations[0][i][3]

                    val x1Tensor = xmin * inputWidth
                    val y1Tensor = ymin * inputHeight
                    val x2Tensor = xmax * inputWidth
                    val y2Tensor = ymax * inputHeight

                    val left   = ((x1Tensor - padX) / scale).coerceIn(0f, imgW)
                    val top    = ((y1Tensor - padY) / scale).coerceIn(0f, imgH)
                    val right  = ((x2Tensor - padX) / scale).coerceIn(0f, imgW)
                    val bottom = ((y2Tensor - padY) / scale).coerceIn(0f, imgH)

                    if (right <= left || bottom <= top) continue

                    val label = resolverRotuloClasse(classIdx)

                    finalDetections.add(
                        Detection.create(
                            RectF(left, top, right, bottom),
                            listOf(Category.create(label, label, score))
                        )
                    )

                    if (finalDetections.size >= maxResults) break
                }

            } else {
                // Inferência YOLO raw (ex: YOLOv8 / YOLOv11 1x5x8400)
                val out = if (yoloTransposed)
                    Array(1) { Array(4 + yoloNumClasses) { FloatArray(yoloNumAnchors) } } as Array<Array<FloatArray>>
                else
                    Array(1) { Array(yoloNumAnchors) { FloatArray(4 + yoloNumClasses) } } as Array<Array<FloatArray>>

                interp.run(buf, out)

                // Verifica em múltiplas âncoras (início, meio e fim do grid) se coordenadas estão em pixels (0..inputWidth) ou normalizadas (0..1)
                val lastIdx = (yoloNumAnchors - 1).coerceAtLeast(0)
                val midIdx = yoloNumAnchors / 2
                val maxSampleCoord = if (yoloTransposed) {
                    maxOf(out[0][0][0], out[0][0][midIdx], out[0][0][lastIdx], out[0][2][lastIdx])
                } else {
                    maxOf(out[0][0][0], out[0][midIdx][0], out[0][lastIdx][0], out[0][lastIdx][2])
                }
                val coordsArePixels = (maxSampleCoord > 2.0f)
                val scaleX = if (coordsArePixels) (1f / inputWidth.toFloat()) else 1f
                val scaleY = if (coordsArePixels) (1f / inputHeight.toFloat()) else 1f

                // Verifica se precisa de sigmoid (no detector.tflite OP[321] Sigmoid já entrega scores em [0..1])
                val minSampleScore = if (yoloTransposed) {
                    minOf(out[0][4][0], out[0][4][midIdx], out[0][4][lastIdx])
                } else {
                    minOf(out[0][0][4], out[0][midIdx][4], out[0][lastIdx][4])
                }
                val maxSampleScore = if (yoloTransposed) {
                    maxOf(out[0][4][0], out[0][4][midIdx], out[0][4][lastIdx])
                } else {
                    maxOf(out[0][0][4], out[0][midIdx][4], out[0][lastIdx][4])
                }
                val needsSigmoid = (minSampleScore < -0.001f || maxSampleScore > 1.001f)

                val candidates = mutableListOf<FloatArray>() // [normCx, normCy, normW, normH, prob, classIdx, probClass0]

                for (i in 0 until yoloNumAnchors) {
                    val normCx: Float
                    val normCy: Float
                    val normW: Float
                    val normH: Float
                    var rawScore = -Float.MAX_VALUE
                    var bestClass = 0
                    val rawClass0: Float

                    if (yoloTransposed) {
                        normCx = out[0][0][i] * scaleX
                        normCy = out[0][1][i] * scaleY
                        normW  = out[0][2][i] * scaleX
                        normH  = out[0][3][i] * scaleY
                        rawClass0 = out[0][4][i]
                        for (c in 0 until yoloNumClasses) {
                            val s = out[0][4 + c][i]
                            if (s > rawScore) {
                                rawScore = s
                                bestClass = c
                            }
                        }
                    } else {
                        normCx = out[0][i][0] * scaleX
                        normCy = out[0][i][1] * scaleY
                        normW  = out[0][i][2] * scaleX
                        normH  = out[0][i][3] * scaleY
                        rawClass0 = out[0][i][4]
                        for (c in 0 until yoloNumClasses) {
                            val s = out[0][i][4 + c]
                            if (s > rawScore) {
                                rawScore = s
                                bestClass = c
                            }
                        }
                    }

                    val prob = if (needsSigmoid) sigmoid(rawScore) else rawScore.coerceIn(0f, 1f)
                    val probClass0 = if (needsSigmoid) sigmoid(rawClass0) else rawClass0.coerceIn(0f, 1f)

                    // Filtra: score >= threshold e tamanho de fruto realista (1.5% a 98% do quadro)
                    if (prob >= scoreThreshold &&
                        normW > 0.015f && normH > 0.015f &&
                        normW < 0.98f && normH < 0.98f &&
                        normCx > 0.005f && normCy > 0.005f &&
                        normCx < 0.995f && normCy < 0.995f
                    ) {
                        candidates.add(floatArrayOf(normCx, normCy, normW, normH, prob, bestClass.toFloat(), probClass0))
                    }
                }

                // Aplica Non-Maximum Suppression (NMS) para eliminar caixas duplicadas sobre o mesmo fruto
                val nmsResult = nms(candidates, iouThresh = 0.45f).take(maxResults)

                for (box in nmsResult) {
                    val x1Tensor = (box[0] - box[2] / 2f) * inputWidth
                    val y1Tensor = (box[1] - box[3] / 2f) * inputHeight
                    val x2Tensor = (box[0] + box[2] / 2f) * inputWidth
                    val y2Tensor = (box[1] + box[3] / 2f) * inputHeight

                    val left   = ((x1Tensor - padX) / scale).coerceIn(0f, imgW)
                    val top    = ((y1Tensor - padY) / scale).coerceIn(0f, imgH)
                    val right  = ((x2Tensor - padX) / scale).coerceIn(0f, imgW)
                    val bottom = ((y2Tensor - padY) / scale).coerceIn(0f, imgH)

                    if (right <= left || bottom <= top) continue

                    val rawClassIdx = box[5].toInt()
                    val classIdx = if (yoloNumClasses == 4) {
                        refinarClasseDoencaPorMorfologiaCor(
                            classIdx = rawClassIdx,
                            probBest = box[4],
                            probClass0 = if (box.size > 6) box[6] else 0f,
                            pixels = pixels,
                            width = inputWidth,
                            height = inputHeight,
                            x1Tensor = x1Tensor,
                            y1Tensor = y1Tensor,
                            x2Tensor = x2Tensor,
                            y2Tensor = y2Tensor
                        )
                    } else {
                        rawClassIdx
                    }
                    val label = resolverRotuloClasse(classIdx)

                    finalDetections.add(
                        Detection.create(
                            RectF(left, top, right, bottom),
                            listOf(Category.create(label, label, box[4]))
                        )
                    )
                }
            }

            Log.d("Detector", "Detecções encontradas: ${finalDetections.size}")
            ResultadoDeteccao(finalDetections, imgW, imgH)

        } catch (e: Exception) {
            Log.e("Detector", "Erro na detecção: ${e.message}", e)
            ResultadoDeteccao(emptyList(), imgW, imgH)
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
        val ax1 = a[0] - a[2] / 2f; val ay1 = a[1] - a[3] / 2f
        val ax2 = a[0] + a[2] / 2f; val ay2 = a[1] + a[3] / 2f
        val bx1 = b[0] - b[2] / 2f; val by1 = b[1] - b[3] / 2f
        val bx2 = b[0] + b[2] / 2f; val by2 = b[1] + b[3] / 2f
        val inter = max(0f, min(ax2, bx2) - max(ax1, bx1)) * max(0f, min(ay2, by2) - max(ay1, by1))
        val union = (ax2 - ax1) * (ay2 - ay1) + (bx2 - bx1) * (by2 - by1) - inter
        return if (union <= 0f) 0f else inter / union
    }

    private fun sigmoid(x: Float) = (1f / (1f + exp(-x.toDouble()))).toFloat()

    private fun resolveFile(): File? = when (val s = modelSource) {
        is ModelSource.Asset -> try {
            val targetFile = File(context.cacheDir, s.fileName)
            val assetFd = try { context.assets.openFd(s.fileName) } catch (_: Exception) { null }
            val assetLen = assetFd?.length ?: -1L
            assetFd?.close()

            // Se o arquivo em cache não existe ou tem tamanho diferente do asset, copia o novo
            if (!targetFile.exists() || (assetLen > 0L && targetFile.length() != assetLen)) {
                context.assets.open(s.fileName).use { input ->
                    FileOutputStream(targetFile).use { output ->
                        input.copyTo(output)
                    }
                }
            }
            targetFile
        } catch (e: Exception) {
            ultimoErro = "Modelo '${s.fileName}' não encontrado nos assets."
            detectorListener?.onError(ultimoErro!!)
            null
        }
        is ModelSource.InternalFile -> File(s.path).takeIf { it.exists() } ?: run {
            ultimoErro = "Arquivo do modelo não encontrado. Importe novamente."
            detectorListener?.onError(ultimoErro!!)
            null
        }
        is ModelSource.External -> try {
            File(context.cacheDir, "custom_model_cache.tflite").also { f ->
                context.contentResolver.openInputStream(s.uri)?.use { i ->
                    FileOutputStream(f).use { i.copyTo(it) }
                }
            }
        } catch (e: Exception) {
            ultimoErro = "Não foi possível acessar o arquivo. Selecione novamente."
            detectorListener?.onError(ultimoErro!!)
            null
        }
    }

    private fun close() {
        interpreter?.close()
        interpreter = null
        loadedPath = null
        letterboxBitmap?.recycle()
        letterboxBitmap = null
        letterboxPixels = null
        inputByteBuffer = null
    }

    data class ResultadoDeteccao(
        val deteccoes: List<Detection>,
        val larguraImagem: Float,
        val alturaImagem: Float
    )

    interface DetectorListener {
        fun onResults(resultado: ResultadoDeteccao)
        fun onError(mensagem: String) {}
    }

    private fun resolverRotuloClasse(classIdx: Int): String {
        val modelPath = loadedPath ?: ""
        return resolverRotuloPorIndice(
            classIdx = classIdx,
            modelLabels = modelLabels,
            customLabels = customLabels,
            yoloNumClasses = yoloNumClasses,
            modelPath = modelPath,
            fallback = "cacau"
        )
    }

    companion object {
        val DEFAULT_4CLASS_DOENCA_LABELS = listOf("PodridaoParda", "Moniliase", "Sadio", "VassouraBruxa")

        internal fun resolverRotuloPorIndice(
            classIdx: Int,
            modelLabels: List<String>,
            customLabels: List<String>,
            yoloNumClasses: Int,
            modelPath: String,
            fallback: String = "cacau"
        ): String {
            return modelLabels.getOrNull(classIdx)
                ?: (if (yoloNumClasses == 4 && modelPath.contains("doenca", ignoreCase = true)) {
                    DEFAULT_4CLASS_DOENCA_LABELS.getOrNull(classIdx)
                } else null)
                ?: customLabels.getOrNull(classIdx)
                ?: (if (yoloNumClasses == 4) DEFAULT_4CLASS_DOENCA_LABELS.getOrNull(classIdx) else null)
                ?: fallback
        }

        /**
         * No checkpoint `best-doenca.pt` (4 classes: 0=PodridaoParda, 1=Moniliase, 2=Sadio, 3=VassouraBruxa),
         * o cabeçalho convolucional Detect.cv3 agrupou os frutos necrosados/doentes no canal 0 (PodridaoParda).
         * Este refinamento analisa o miolo (70% central) do recorte do fruto detectado para distinguir:
         * - Fruto vassourado / chirimoya (VassouraBruxa, classe 3): necrosado seco/acinzentado-escuro
         *   com baixa saturação cromática (satMean < 0.265), baixa diferença R-B (rbDiff < 0.145) e menor brilho (valMean < 0.68).
         * - Podridão Parda (PodridaoParda, classe 0): fruto com fundo amarelo/alaranjado/marrom-quente (rbDiff >= 0.145 ou valMean >= 0.68).
         */
        internal fun refinarClasseDoencaPorMorfologiaCor(
            classIdx: Int,
            probBest: Float,
            probClass0: Float,
            pixels: IntArray,
            width: Int,
            height: Int,
            x1Tensor: Float,
            y1Tensor: Float,
            x2Tensor: Float,
            y2Tensor: Float
        ): Int {
            if (classIdx != 0 && !(classIdx == 2 && probBest < 0.72f && probClass0 >= 0.20f)) {
                return classIdx
            }
            val bw = x2Tensor - x1Tensor
            val bh = y2Tensor - y1Tensor
            if (bw < 4f || bh < 4f) return classIdx

            val startX = (x1Tensor + bw * 0.15f).toInt().coerceIn(0, width - 1)
            val endX = (x2Tensor - bw * 0.15f).toInt().coerceIn(startX + 1, width)
            val startY = (y1Tensor + bh * 0.15f).toInt().coerceIn(0, height - 1)
            val endY = (y2Tensor - bh * 0.15f).toInt().coerceIn(startY + 1, height)

            var rSum = 0f
            var gSum = 0f
            var bSum = 0f
            var satSum = 0f
            var valSum = 0f
            var count = 0

            val stepY = ((endY - startY) / 24).coerceAtLeast(1)
            val stepX = ((endX - startX) / 24).coerceAtLeast(1)

            var y = startY
            while (y < endY) {
                val rowOffset = y * width
                var x = startX
                while (x < endX) {
                    val px = pixels[rowOffset + x]
                    val r = ((px shr 16) and 0xFF) / 255f
                    val g = ((px shr 8) and 0xFF) / 255f
                    val b = (px and 0xFF) / 255f
                    val cmax = maxOf(r, g, b)
                    val cmin = minOf(r, g, b)
                    val sat = if (cmax > 0.001f) (cmax - cmin) / cmax else 0f
                    rSum += r
                    gSum += g
                    bSum += b
                    satSum += sat
                    valSum += cmax
                    count++
                    x += stepX
                }
                y += stepY
            }

            if (count == 0) return classIdx
            val rMean = rSum / count
            val bMean = bSum / count
            val satMean = satSum / count
            val valMean = valSum / count
            val rbDiff = rMean - bMean

            return if (classIdx == 0) {
                if (rbDiff < 0.145f && satMean < 0.265f && valMean < 0.68f) 3 else 0
            } else {
                // classIdx == 2 (Sadio moderado) mas com ativação simultânea de doença em fruto seco/escuro
                if (rbDiff < 0.10f && satMean < 0.20f && valMean < 0.58f) 3 else classIdx
            }
        }
    }
}
