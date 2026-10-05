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
    var scoreThreshold: Float = 0.30f,
    var maxResults: Int = 15,
    var labelsModelo: List<String> = listOf("cacau", "fruto"),
    var numThreads: Int = 4,
    var tentarGpu: Boolean = false
) {
    sealed class ModelSource {
        data class Asset(val fileName: String) : ModelSource()
        data class External(val uri: Uri, val displayName: String) : ModelSource()
        data class InternalFile(val path: String, val displayName: String) : ModelSource()
    }

    private var interpreter: Interpreter? = null
    private var gpuDelegate: GpuDelegate? = null

    // Metadados do modelo
    var inputSize   = 640; private set
    var numAnchors  = 0;   private set
    var numClasses  = 0;   private set
    private var transposed  = false

    var ultimoErro: String? = null; private set
    var lastBitmapWidth:  Float = 1f; private set
    var lastBitmapHeight: Float = 1f; private set
    var ultimaLatenciaMs: Long = 0; private set

    private var loadedPath: String? = null

    // Tabela de consulta (LUT) para normalização RGB [0..255] -> [0f..1f] sem divisão em loop
    private val lut255 = FloatArray(256) { it / 255f }

    // Buffers reutilizáveis para evitar pressão no Garbage Collector (GC)
    private var bufferEntrada: ByteBuffer? = null
    private var pixelsBuffer: IntArray? = null
    private var letterboxBitmap: Bitmap? = null
    private var letterboxCanvas: Canvas? = null
    private val paintCinza = Paint().apply { color = Color.rgb(114, 114, 114) }

    // Arrays 3D de saída pré-alocados para evitar milhares de alocações por frame
    private var outBufferTransposed: Array<Array<FloatArray>>? = null
    private var outBufferStandard: Array<Array<FloatArray>>? = null

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
                        Log.w("Detector", "GPU Delegate indisponivel, usando CPU XNNPACK: ${e.message}")
                    }
                }
            }

            interpreter = Interpreter(file, options)

            val outShape = interpreter!!.getOutputTensor(0).shape()
            val inShape  = interpreter!!.getInputTensor(0).shape()
            Log.d("Detector", "Input: ${inShape.toList()} | Output: ${outShape.toList()}")

            inputSize = if (inShape.size >= 3) inShape[1] else 640

            when {
                // Formato [1, 4+C, anchors] — YOLOv8 / YOLOv11 (ex: [1, 5, 8400])
                outShape.size == 3 && outShape[1] in 5..300 && outShape[2] > outShape[1] -> {
                    transposed  = true
                    numClasses  = outShape[1] - 4
                    numAnchors  = outShape[2]
                    outBufferTransposed = Array(1) { Array(outShape[1]) { FloatArray(numAnchors) } }
                    outBufferStandard = null
                }
                // Formato [1, anchors, 4+C]
                outShape.size == 3 && outShape[2] in 5..300 && outShape[1] > outShape[2] -> {
                    transposed  = false
                    numAnchors  = outShape[1]
                    numClasses  = outShape[2] - 4
                    outBufferStandard = Array(1) { Array(numAnchors) { FloatArray(outShape[2]) } }
                    outBufferTransposed = null
                }
                else -> {
                    transposed  = false
                    numAnchors  = if (outShape.size >= 2) outShape[1] else 0
                    numClasses  = if (outShape.size >= 3) outShape[2] - 4 else 1
                    outBufferStandard = Array(1) { Array(maxOf(numAnchors, 1)) { FloatArray(4 + maxOf(numClasses, 1)) } }
                    outBufferTransposed = null
                    Log.w("Detector", "Shape de saida nao convencional: ${outShape.toList()}")
                }
            }

            // Aloca buffers de tamanho fixo para o modelo
            bufferEntrada = ByteBuffer.allocateDirect(inputSize * inputSize * 3 * 4).order(ByteOrder.nativeOrder())
            pixelsBuffer = IntArray(inputSize * inputSize)
            letterboxBitmap = Bitmap.createBitmap(inputSize, inputSize, Bitmap.Config.ARGB_8888)
            letterboxCanvas = Canvas(letterboxBitmap!!)

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
     * Executa a inferência YOLO em uma imagem aplicando letterbox e mapeamento reverso otimizado.
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

            // 2. Normalização dos pixels [0, 1] no buffer direto via LUT rápida (sem divisão float em loop)
            val buf = bufferEntrada ?: ByteBuffer.allocateDirect(inputSize * inputSize * 3 * 4).order(ByteOrder.nativeOrder()).also { bufferEntrada = it }
            buf.rewind()
            val pixels = pixelsBuffer ?: IntArray(inputSize * inputSize).also { pixelsBuffer = it }
            lbBitmap.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)

            for (px in pixels) {
                buf.putFloat(lut255[(px ushr 16) and 0xFF])
                buf.putFloat(lut255[(px ushr 8) and 0xFF])
                buf.putFloat(lut255[px and 0xFF])
            }

            // 3. Execução da inferência reutilizando buffers pré-alocados (zero GC allocation)
            val out = if (transposed) {
                outBufferTransposed ?: Array(1) { Array(4 + numClasses) { FloatArray(numAnchors) } }.also { outBufferTransposed = it }
            } else {
                outBufferStandard ?: Array(1) { Array(numAnchors) { FloatArray(4 + numClasses) } }.also { outBufferStandard = it }
            }
            interp.run(buf, out)

            // 4. Verificação de sistema de coordenadas: pixel (0..inputSize) vs normalizado (0..1)
            // Checa a âncora central para evitar instabilidades nos cantos
            val centerIdx = numAnchors / 2
            val centerCoord = if (transposed) out[0][0][centerIdx] else out[0][centerIdx][0]
            val isPixelCoord = centerCoord > 1.5f || (if (transposed) out[0][2][centerIdx] else out[0][centerIdx][2]) > 1.5f

            // 5. Decodificação de candidatos com mapeamento reverso retirando o letterbox
            val candidates = ArrayList<FloatArray>(64)
            val maxClasses = maxOf(numClasses, 1)

            for (i in 0 until numAnchors) {
                val cx: Float
                val cy: Float
                val w: Float
                val h: Float
                var bestScore = -Float.MAX_VALUE
                var bestClass = 0

                if (transposed) {
                    val rawCx = out[0][0][i]
                    val rawCy = out[0][1][i]
                    val rawW  = out[0][2][i]
                    val rawH  = out[0][3][i]
                    cx = if (isPixelCoord) rawCx else rawCx * inputSize
                    cy = if (isPixelCoord) rawCy else rawCy * inputSize
                    w  = if (isPixelCoord) rawW  else rawW  * inputSize
                    h  = if (isPixelCoord) rawH  else rawH  * inputSize

                    for (c in 0 until maxClasses) {
                        val s = out[0][4 + c][i]
                        if (s > bestScore) { bestScore = s; bestClass = c }
                    }
                } else {
                    val rawCx = out[0][i][0]
                    val rawCy = out[0][i][1]
                    val rawW  = out[0][i][2]
                    val rawH  = out[0][i][3]
                    cx = if (isPixelCoord) rawCx else rawCx * inputSize
                    cy = if (isPixelCoord) rawCy else rawCy * inputSize
                    w  = if (isPixelCoord) rawW  else rawW  * inputSize
                    h  = if (isPixelCoord) rawH  else rawH  * inputSize

                    for (c in 0 until maxClasses) {
                        val s = out[0][i][4 + c]
                        if (s > bestScore) { bestScore = s; bestClass = c }
                    }
                }

                // O modelo YOLOv8 já tem ativação Sigmoid na saída; apenas aplica caso seja logit puro
                val prob = if (bestScore < -0.05f || bestScore > 1.05f) sigmoid(bestScore) else bestScore.coerceIn(0f, 1f)

                // Filtro preliminar: score >= threshold e tamanho mínimo de fruto
                if (prob >= scoreThreshold && w > 4f && h > 4f) {
                    // Mapeamento reverso do letterbox -> coordenadas originais do bitmap
                    val origCx = (cx - padX) / scale
                    val origCy = (cy - padY) / scale
                    val origW  = w / scale
                    val origH  = h / scale

                    val left   = (origCx - origW / 2f).coerceIn(0f, imgW)
                    val top    = (origCy - origH / 2f).coerceIn(0f, imgH)
                    val right  = (origCx + origW / 2f).coerceIn(0f, imgW)
                    val bottom = (origCy + origH / 2f).coerceIn(0f, imgH)

                    if ((right - left) >= 4f && (bottom - top) >= 4f) {
                        candidates.add(floatArrayOf(left, top, right, bottom, prob, bestClass.toFloat()))
                    }
                }
            }

            // 6. NMS e conversão para DetectionResult
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

            // Estabilização de contagem (mediana temporal)
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
        val result = ArrayList<FloatArray>(sorted.size)
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
                val assetFd = try { context.assets.openFd(s.fileName) } catch (_: Exception) { null }
                val assetLen = assetFd?.length ?: -1L
                assetFd?.close()
                if (!f.exists() || f.length() == 0L || (assetLen > 0L && f.length() != assetLen)) {
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