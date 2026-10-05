package com.example.myapplication.userinterface.util

import android.content.Context
import android.graphics.*
import android.net.Uri
import android.util.Log
import com.example.myapplication.data.DetectionCategory
import com.example.myapplication.data.DetectionResult
import com.example.myapplication.data.ModelConfig
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.GpuDelegate
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.exp
import kotlin.math.roundToInt

/**
 * Helper de detecção TFLite otimizado para inferência YOLO em campo:
 * - Pré-processamento com Letterbox (proporção preservada, preenchimento cinza 114);
 * - Mapeamento reverso preciso de coordenadas para o frame original;
 * - Suporte a GPU Delegate com fallback transparente para CPU;
 * - Reutilização de buffers e matrizes de pixels para reduzir pressão no GC;
 * - Suporte a múltiplos rótulos, NMS por classe e medição de latência.
 */
class ObjectDetectorHelper(
    val context: Context,
    var detectorListener: DetectorListener?,
    var modelSource: ModelSource = ModelSource.Asset("detector.tflite"),
    var scoreThreshold: Float = 0.55f,
    var maxResults: Int = 10,
    var labelsModelo: List<String> = listOf("cacau", "fruto"),
    var numThreads: Int = 2,
    var tentarGpu: Boolean = true
) {
    sealed class ModelSource {
        data class Asset(val fileName: String) : ModelSource()
        data class External(val uri: Uri, val displayName: String) : ModelSource()
        data class InternalFile(val path: String, val displayName: String) : ModelSource()
    }

    private var interpreter: Interpreter? = null
    private var gpuDelegate: GpuDelegate? = null

    // Metadados do modelo
    var inputSize   = 300; private set
    var numAnchors  = 0;   private set
    var numClasses  = 0;   private set
    private var transposed  = false
    private var coordScale  = 1f
    private var applySigmoid = true

    var ultimoErro: String? = null; private set
    var lastBitmapWidth:  Float = 1f; private set
    var lastBitmapHeight: Float = 1f; private set
    var ultimaLatenciaMs: Long = 0; private set

    private var loadedPath: String? = null

    // Buffers reutilizáveis para evitar alocações por frame
    private var bufferEntrada: ByteBuffer? = null
    private var pixelsBuffer: IntArray? = null
    private var letterboxBitmap: Bitmap? = null
    private var letterboxCanvas: Canvas? = null
    private val paintCinza = Paint().apply { color = Color.rgb(114, 114, 114) }

    // Estabilização de contagem (mediana dos últimos N frames)
    private val historicoContagens = mutableListOf<Int>()
    private val janelaMediana = 5

    init {
        setup()
    }

    fun setListener(l: DetectorListener) { detectorListener = l }

    fun reloadModel(
        source: ModelSource,
        threshold: Float = scoreThreshold,
        maxRes: Int = maxResults,
        labels: List<String> = labelsModelo
    ) {
        close()
        modelSource = source
        scoreThreshold = threshold
        maxResults = maxRes
        labelsModelo = labels
        ultimoErro = null
        loadedPath = null
        setup()
    }

    private fun setup() {
        val file = resolveFile() ?: return
        if (file.absolutePath == loadedPath && interpreter != null) return

        close()

        try {
            val options = Interpreter.Options().apply {
                setNumThreads(numThreads)
                if (tentarGpu) {
                    try {
                        val delegate = GpuDelegate()
                        addDelegate(delegate)
                        gpuDelegate = delegate
                        Log.d("Detector", "GPU Delegate ativado com sucesso.")
                    } catch (e: Throwable) {
                        Log.w("Detector", "GPU Delegate indisponivel, usando CPU: ${e.message}")
                    }
                }
            }

            interpreter = Interpreter(file, options)

            val outShape = interpreter!!.getOutputTensor(0).shape()
            val inShape  = interpreter!!.getInputTensor(0).shape()
            Log.d("Detector", "Input: ${inShape.toList()} | Output: ${outShape.toList()}")

            inputSize = inShape[1]

            when {
                // Formato [1, 4+C, anchors] — YOLOv8 / YOLOv11
                outShape.size == 3 && outShape[1] in 5..300 && outShape[2] > outShape[1] -> {
                    transposed  = true
                    numClasses  = outShape[1] - 4
                    numAnchors  = outShape[2]
                }
                // Formato [1, anchors, 4+C]
                outShape.size == 3 && outShape[2] in 5..300 && outShape[1] > outShape[2] -> {
                    transposed  = false
                    numAnchors  = outShape[1]
                    numClasses  = outShape[2] - 4
                }
                else -> {
                    transposed  = false
                    numAnchors  = if (outShape.size >= 2) outShape[1] else 0
                    numClasses  = if (outShape.size >= 3) outShape[2] - 4 else 0
                    Log.w("Detector", "Shape de saida nao convencional: ${outShape.toList()}")
                }
            }

            // Aloca buffers fixos para o tamanho de entrada do modelo
            bufferEntrada = ByteBuffer.allocateDirect(inputSize * inputSize * 3 * 4).order(ByteOrder.nativeOrder())
            pixelsBuffer = IntArray(inputSize * inputSize)
            letterboxBitmap = Bitmap.createBitmap(inputSize, inputSize, Bitmap.Config.ARGB_8888)
            letterboxCanvas = Canvas(letterboxBitmap!!)

            coordScale = 1f
            loadedPath = file.absolutePath
            ultimoErro = null
            Log.d("Detector", "Modelo carregado com sucesso: size=$inputSize, anchors=$numAnchors, classes=$numClasses, transposto=$transposed")

        } catch (e: Exception) {
            ultimoErro = "Erro ao carregar modelo: ${e.message?.take(200)}"
            Log.e("Detector", ultimoErro!!)
            detectorListener?.onError(ultimoErro!!)
        }
    }

    /**
     * Executa a inferência YOLO em uma imagem aplicando letterbox e mapeamento reverso.
     */
    fun detect(image: Bitmap) {
        val inicio = System.currentTimeMillis()
        lastBitmapWidth  = image.width.toFloat()
        lastBitmapHeight = image.height.toFloat()

        val interp = interpreter ?: run { setup(); interpreter ?: return }
        if (numAnchors == 0) {
            detectorListener?.onResults(emptyList())
            return
        }

        try {
            val imgW = image.width.toFloat()
            val imgH = image.height.toFloat()

            // 1. Pré-processamento com Letterbox (proporção exata + preenchimento cinza 114)
            val scale = minOf(inputSize / imgW, inputSize / imgH)
            val scaledW = (imgW * scale).roundToInt()
            val scaledH = (imgH * scale).roundToInt()
            val padX = (inputSize - scaledW) / 2f
            val padY = (inputSize - scaledH) / 2f

            val lbBitmap = letterboxBitmap ?: Bitmap.createBitmap(inputSize, inputSize, Bitmap.Config.ARGB_8888).also { letterboxBitmap = it }
            val lbCanvas = letterboxCanvas ?: Canvas(lbBitmap).also { letterboxCanvas = it }

            lbCanvas.drawRect(0f, 0f, inputSize.toFloat(), inputSize.toFloat(), paintCinza)
            val srcRect = Rect(0, 0, image.width, image.height)
            val dstRect = RectF(padX, padY, padX + scaledW, padY + scaledH)
            lbCanvas.drawBitmap(image, srcRect, dstRect, null)

            // 2. Normalização dos pixels [0, 1] no buffer direto
            val buf = bufferEntrada ?: ByteBuffer.allocateDirect(inputSize * inputSize * 3 * 4).order(ByteOrder.nativeOrder()).also { bufferEntrada = it }
            buf.rewind()
            val pixels = pixelsBuffer ?: IntArray(inputSize * inputSize).also { pixelsBuffer = it }
            lbBitmap.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)

            for (px in pixels) {
                buf.putFloat(((px shr 16) and 0xFF) / 255f)
                buf.putFloat(((px shr 8)  and 0xFF) / 255f)
                buf.putFloat((px          and 0xFF) / 255f)
            }

            // 3. Execução da inferência no Interpreter
            @Suppress("UNCHECKED_CAST")
            val out = if (transposed)
                Array(1) { Array(4 + numClasses) { FloatArray(numAnchors) } } as Array<Array<FloatArray>>
            else
                Array(1) { Array(numAnchors) { FloatArray(4 + numClasses) } } as Array<Array<FloatArray>>
            interp.run(buf, out)

            // Calibração de escala e sigmoid
            val sampleCoord = if (transposed) out[0][0][0] else out[0][0][0]
            if (coordScale == 1f && sampleCoord > 2f) {
                coordScale = 1f / inputSize.toFloat()
            }
            val sampleScore = if (transposed) out[0][4][0] else out[0][0][4]
            applySigmoid = sampleScore < 0f || sampleScore > 1f

            // 4. Decodificação de candidatos com mapeamento reverso retirando o letterbox
            val candidates = mutableListOf<FloatArray>()
            val maxClasses = maxOf(numClasses, 1)

            for (i in 0 until numAnchors) {
                val cxNorm: Float
                val cyNorm: Float
                val wNorm: Float
                val hNorm: Float
                var bestScore = -Float.MAX_VALUE
                var bestClass = 0

                if (transposed) {
                    cxNorm = out[0][0][i] * coordScale
                    cyNorm = out[0][1][i] * coordScale
                    wNorm  = out[0][2][i] * coordScale
                    hNorm  = out[0][3][i] * coordScale
                    for (c in 0 until maxClasses) {
                        val s = out[0][4 + c][i]
                        if (s > bestScore) { bestScore = s; bestClass = c }
                    }
                } else {
                    cxNorm = out[0][i][0] * coordScale
                    cyNorm = out[0][i][1] * coordScale
                    wNorm  = out[0][i][2] * coordScale
                    hNorm  = out[0][i][3] * coordScale
                    for (c in 0 until maxClasses) {
                        val s = out[0][i][4 + c]
                        if (s > bestScore) { bestScore = s; bestClass = c }
                    }
                }

                val prob = if (applySigmoid) sigmoid(bestScore) else bestScore.coerceIn(0f, 1f)

                if (prob >= scoreThreshold && wNorm > 0.01f && hNorm > 0.01f) {
                    // Mapeamento reverso do letterbox -> espaço da imagem original
                    val cxPx = cxNorm * inputSize
                    val cyPx = cyNorm * inputSize
                    val wPx  = wNorm * inputSize
                    val hPx  = hNorm * inputSize

                    val origCx = (cxPx - padX) / scale
                    val origCy = (cyPx - padY) / scale
                    val origW  = wPx / scale
                    val origH  = hPx / scale

                    val left   = (origCx - origW / 2f).coerceIn(0f, imgW)
                    val top    = (origCy - origH / 2f).coerceIn(0f, imgH)
                    val right  = (origCx + origW / 2f).coerceIn(0f, imgW)
                    val bottom = (origCy + origH / 2f).coerceIn(0f, imgH)

                    if (right > left && bottom > top) {
                        candidates.add(floatArrayOf(left, top, right, bottom, prob, bestClass.toFloat()))
                    }
                }
            }

            // 5. NMS e conversão para DetectionResult
            val selecionados = nms(candidates).take(maxResults)
            val detections = selecionados.map { box ->
                val rect = RectF(box[0], box[1], box[2], box[3])
                val classIdx = box[5].toInt()
                val labelNome = labelsModelo.getOrNull(classIdx) ?: "cacau"
                DetectionResult.create(
                    rect,
                    listOf(DetectionCategory.create(labelNome, labelNome, box[4]))
                )
            }

            // Estabilização de contagem
            synchronized(historicoContagens) {
                historicoContagens.add(detections.size)
                if (historicoContagens.size > janelaMediana) historicoContagens.removeAt(0)
            }

            ultimaLatenciaMs = System.currentTimeMillis() - inicio
            detectorListener?.onResults(detections)

        } catch (e: Exception) {
            Log.e("Detector", "Erro durante deteccao: ${e.message}")
            detectorListener?.onResults(emptyList())
        }
    }

    /** Retorna a contagem estabilizada pela mediana da janela temporal recente. */
    fun contagemEstabilizada(): Int {
        synchronized(historicoContagens) {
            if (historicoContagens.isEmpty()) return 0
            val ordenado = historicoContagens.sorted()
            return ordenado[ordenado.size / 2]
        }
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

    private fun sigmoid(x: Float) = (1f / (1f + exp(-x.toDouble()))).toFloat()

    private fun resolveFile(): File? = when (val s = modelSource) {
        is ModelSource.Asset -> try {
            File(context.cacheDir, s.fileName).also { f ->
                if (!f.exists() || f.length() == 0L) {
                    context.assets.open(s.fileName).use { i ->
                        FileOutputStream(f).use { i.copyTo(it) }
                    }
                }
            }
        } catch (e: Exception) {
            ultimoErro = "Modelo '${s.fileName}' nao encontrado nos assets."
            detectorListener?.onError(ultimoErro!!); null
        }
        is ModelSource.InternalFile -> File(s.path).takeIf { it.exists() && it.length() > 0L } ?: run {
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
            ultimoErro = "Nao foi possivel acessar o modelo externo."
            detectorListener?.onError(ultimoErro!!); null
        }
    }

    fun fechar() = close()

    private fun close() {
        try {
            interpreter?.close()
        } catch (_: Exception) {}
        interpreter = null

        try {
            gpuDelegate?.close()
        } catch (_: Exception) {}
        gpuDelegate = null

        loadedPath = null
    }

    interface DetectorListener {
        fun onResults(results: List<DetectionResult>)
        fun onError(mensagem: String) {}
    }
}

// ── Factory Segura Compartilhada ─────────────────────────────────────────────

fun assetModeloExiste(context: Context, nome: String): Boolean = try {
    context.assets.open(nome).close(); true
} catch (_: Exception) { false }

fun modeloDisponivel(context: Context, config: ModelConfig): Boolean = when {
    config.modeloExternoUri != null  -> true
    config.modeloSelecionado != null -> assetModeloExiste(context, config.modeloSelecionado.nomeArquivo)
    else                             -> false
}

fun buildDetectorHelper(
    context: Context,
    config: ModelConfig,
    aoErro: (String) -> Unit = {},
    aoDetectar: (List<DetectionResult>) -> Unit
): ObjectDetectorHelper? {
    if (!modeloDisponivel(context, config)) {
        aoErro("Nenhum modelo de IA disponivel. Importe um arquivo .tflite.")
        return null
    }
    return try {
        val source = when {
            config.modeloExternoUri != null ->
                ObjectDetectorHelper.ModelSource.External(config.modeloExternoUri, config.modeloExternoNome)
            else ->
                ObjectDetectorHelper.ModelSource.Asset(config.modeloSelecionado!!.nomeArquivo)
        }
        val helper = ObjectDetectorHelper(
            context          = context,
            detectorListener = object : ObjectDetectorHelper.DetectorListener {
                override fun onResults(results: List<DetectionResult>) { aoDetectar(results) }
                override fun onError(mensagem: String) { aoErro(mensagem) }
            },
            modelSource      = source,
            scoreThreshold   = config.threshold,
            maxResults       = config.maxResultados,
            labelsModelo     = config.labelsFiltro
        )
        if (helper.ultimoErro != null) {
            aoErro(helper.ultimoErro!!)
            helper.fechar()
            null
        } else helper
    } catch (t: Throwable) {
        Log.e("Detector", "Falha ao criar detector: ${t.message}")
        aoErro("Falha ao carregar o modelo: ${t.message?.take(120)}")
        null
    }
}