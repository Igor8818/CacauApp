package com.example.myapplication.userinterface.util

import android.content.Context
import android.graphics.*
import android.net.Uri
import android.util.Log
import com.example.myapplication.data.DetectionCategory
import com.example.myapplication.data.DetectionResult
import com.example.myapplication.data.ModelConfig
import org.tensorflow.lite.DataType
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
 * - Suporte a entrada NHWC/NCHW, float32/uint8 e saídas YOLO [4+C,N], [N,4+C] e [N,6] (NMS embutido);
 * - Suporte a GPU Delegate com fallback transparente para CPU;
 * - Reutilização de buffers para reduzir pressão no GC;
 * - Inferência serializada (thread-safe): a câmera ao vivo e a revisão da foto nunca competem pelos buffers.
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
    @Volatile var inputSize   = 640; private set
    @Volatile var numAnchors  = 0;   private set
    @Volatile var numClasses  = 0;   private set
    @Volatile var formato: FormatoModelo? = null; private set
    private var layout = LayoutSaida.TRANSPOSTO
    private var entradaNchw = false
    private var entradaFloat = true

    @Volatile var ultimoErro: String? = null; private set
    @Volatile var lastBitmapWidth:  Float = 1f; private set
    @Volatile var lastBitmapHeight: Float = 1f; private set
    @Volatile var ultimaLatenciaMs: Long = 0; private set

    private var loadedPath: String? = null

    // Tabela de consulta (LUT) para normalização RGB [0..255] -> [0f..1f] sem divisão em loop
    private val lut255 = FloatArray(256) { it / 255f }

    // Buffers reutilizáveis para evitar pressão no Garbage Collector (GC)
    private var bufferEntrada: ByteBuffer? = null
    private var pixelsBuffer: IntArray? = null
    private var letterboxBitmap: Bitmap? = null
    private var letterboxCanvas: Canvas? = null
    private val paintCinza = Paint().apply { color = Color.rgb(114, 114, 114) }
    private var outBuffer: Array<Array<FloatArray>>? = null

    // Estabilização de contagem (mediana dos últimos N frames)
    private val historicoContagens = mutableListOf<Int>()
    private val janelaMediana = 5

    init {
        setup()
    }

    fun setListener(l: DetectorListener) { detectorListener = l }

    @Synchronized
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

    @Synchronized
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

            val interp = Interpreter(file, options)
            val inTensor = interp.getInputTensor(0)
            val outTensor = interp.getOutputTensor(0)

            val fmt = try {
                analisarFormatoModelo(
                    inShape = inTensor.shape(),
                    entradaFloat32 = inTensor.dataType() == DataType.FLOAT32,
                    entradaUint8 = inTensor.dataType() == DataType.UINT8,
                    outShape = outTensor.shape(),
                    saidaFloat32 = outTensor.dataType() == DataType.FLOAT32
                )
            } catch (e: IllegalArgumentException) {
                interp.close()
                throw IllegalStateException(e.message)
            }

            interpreter = interp
            formato = fmt
            inputSize = fmt.inputSize
            entradaNchw = fmt.entradaNchw
            entradaFloat = fmt.entradaFloat
            layout = fmt.layout
            numAnchors = fmt.numAnchors
            numClasses = fmt.numClasses

            val outShape = outTensor.shape()
            outBuffer = Array(outShape[0]) { Array(outShape[1]) { FloatArray(outShape[2]) } }

            val bytesPorCanal = if (entradaFloat) 4 else 1
            bufferEntrada = ByteBuffer.allocateDirect(inputSize * inputSize * 3 * bytesPorCanal).order(ByteOrder.nativeOrder())
            pixelsBuffer = IntArray(inputSize * inputSize)
            letterboxBitmap = Bitmap.createBitmap(inputSize, inputSize, Bitmap.Config.ARGB_8888)
            letterboxCanvas = Canvas(letterboxBitmap!!)

            loadedPath = file.absolutePath
            ultimoErro = null
            Log.d("Detector", "Modelo carregado: ${fmt.descricao()}")

        } catch (e: Exception) {
            ultimoErro = "Erro ao carregar modelo: ${e.message?.take(200)}"
            Log.e("Detector", ultimoErro!!)
            detectorListener?.onError(ultimoErro!!)
        }
    }

    /**
     * Inferência com entrega via listener (usada pela câmera ao vivo).
     */
    fun detect(image: Bitmap) {
        val resultados = detectar(image)
        detectorListener?.onResults(resultados)
    }

    /**
     * Inferência síncrona e thread-safe: retorna as detecções em coordenadas do [image] sem
     * tocar no listener. É a API correta para a revisão da foto, pois não sofre interferência
     * de quadros da câmera ao vivo (o acesso aos buffers é serializado).
     */
    @Synchronized
    fun detectar(image: Bitmap): List<DetectionResult> {
        val inicio = System.currentTimeMillis()
        lastBitmapWidth  = image.width.toFloat()
        lastBitmapHeight = image.height.toFloat()

        val interp = interpreter ?: run { setup(); interpreter ?: return emptyList() }
        val out = outBuffer ?: return emptyList()
        if (numAnchors == 0) return emptyList()

        try {
            val imgW = image.width.toFloat()
            val imgH = image.height.toFloat()

            // 1. Letterbox (proporção exata + preenchimento cinza 114)
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

            // 2. Normalização para o buffer de entrada (NHWC/NCHW, float32/uint8)
            val buf = bufferEntrada ?: return emptyList()
            buf.rewind()
            val pixels = pixelsBuffer ?: return emptyList()
            lbBitmap.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)
            preencherEntrada(buf, pixels)
            buf.rewind()

            // 3. Inferência reutilizando buffers pré-alocados
            interp.run(buf, out)

            // 4. Decodificação -> candidatos em coordenadas do bitmap original
            val candidatos = ArrayList<FloatArray>(64)
            decodificar(out, scale, padX, padY, imgW, imgH, candidatos)

            // 5. NMS e conversão
            val selecionados = if (layout == LayoutSaida.END_TO_END) {
                candidatos.sortedByDescending { it[4] }
            } else {
                nms(candidatos)
            }.take(maxResults)

            val deteccoes = selecionados.map { box ->
                val rect = RectF(box[0], box[1], box[2], box[3])
                val classIdx = box[5].toInt()
                val labelNome = labelsModelo.getOrNull(classIdx)
                    ?: if (numClasses <= 1) "cacau" else "classe_$classIdx"
                DetectionResult.create(
                    rect,
                    listOf(DetectionCategory.create(labelNome, labelNome, box[4]))
                )
            }

            synchronized(historicoContagens) {
                historicoContagens.add(deteccoes.size)
                if (historicoContagens.size > janelaMediana) historicoContagens.removeAt(0)
            }

            ultimaLatenciaMs = System.currentTimeMillis() - inicio
            return deteccoes

        } catch (e: Exception) {
            Log.e("Detector", "Erro durante deteccao: ${e.message}")
            return emptyList()
        }
    }

    private fun preencherEntrada(buf: ByteBuffer, pixels: IntArray) {
        val plano = inputSize * inputSize
        if (entradaFloat) {
            if (!entradaNchw) {
                for (px in pixels) {
                    buf.putFloat(lut255[(px ushr 16) and 0xFF])
                    buf.putFloat(lut255[(px ushr 8) and 0xFF])
                    buf.putFloat(lut255[px and 0xFF])
                }
            } else {
                val fb = buf.asFloatBuffer()
                for (i in 0 until plano) {
                    val px = pixels[i]
                    fb.put(i, lut255[(px ushr 16) and 0xFF])
                    fb.put(plano + i, lut255[(px ushr 8) and 0xFF])
                    fb.put(2 * plano + i, lut255[px and 0xFF])
                }
            }
        } else {
            if (!entradaNchw) {
                for (px in pixels) {
                    buf.put(((px ushr 16) and 0xFF).toByte())
                    buf.put(((px ushr 8) and 0xFF).toByte())
                    buf.put((px and 0xFF).toByte())
                }
            } else {
                for (i in 0 until plano) {
                    val px = pixels[i]
                    buf.put(i, ((px ushr 16) and 0xFF).toByte())
                    buf.put(plano + i, ((px ushr 8) and 0xFF).toByte())
                    buf.put(2 * plano + i, (px and 0xFF).toByte())
                }
            }
        }
    }

    /** Converte a saída bruta do modelo em caixas (l, t, r, b, score, classe) no espaço do bitmap. */
    private fun decodificar(
        out: Array<Array<FloatArray>>,
        scale: Float, padX: Float, padY: Float,
        imgW: Float, imgH: Float,
        destino: MutableList<FloatArray>
    ) {
        val tam = inputSize.toFloat()

        // Detecta se as coordenadas estão em pixels (0..tam) ou normalizadas (0..1)
        var maxRef = 0f
        when (layout) {
            LayoutSaida.TRANSPOSTO -> for (i in 0 until numAnchors) maxRef = maxOf(maxRef, out[0][2][i])
            LayoutSaida.PADRAO     -> for (i in 0 until numAnchors) maxRef = maxOf(maxRef, out[0][i][2])
            LayoutSaida.END_TO_END -> for (i in 0 until numAnchors) maxRef = maxOf(maxRef, out[0][i][2], out[0][i][3])
        }
        val fator = if (maxRef > 1.5f) 1f else tam

        for (i in 0 until numAnchors) {
            var l: Float; var t: Float; var r: Float; var b: Float
            var melhorScore = -Float.MAX_VALUE
            var melhorClasse = 0

            when (layout) {
                LayoutSaida.END_TO_END -> {
                    val row = out[0][i]
                    melhorScore = row[4]
                    melhorClasse = row[5].roundToInt()
                    l = row[0] * fator; t = row[1] * fator; r = row[2] * fator; b = row[3] * fator
                }
                else -> {
                    val cx: Float; val cy: Float; val w: Float; val h: Float
                    if (layout == LayoutSaida.TRANSPOSTO) {
                        cx = out[0][0][i] * fator; cy = out[0][1][i] * fator
                        w = out[0][2][i] * fator;  h = out[0][3][i] * fator
                        for (c in 0 until numClasses) {
                            val s = out[0][4 + c][i]
                            if (s > melhorScore) { melhorScore = s; melhorClasse = c }
                        }
                    } else {
                        val row = out[0][i]
                        cx = row[0] * fator; cy = row[1] * fator
                        w = row[2] * fator;  h = row[3] * fator
                        for (c in 0 until numClasses) {
                            val s = row[4 + c]
                            if (s > melhorScore) { melhorScore = s; melhorClasse = c }
                        }
                    }
                    l = cx - w / 2f; t = cy - h / 2f; r = cx + w / 2f; b = cy + h / 2f
                }
            }

            // Modelos exportados com sigmoid já embutido entregam [0,1]; só aplica sigmoid se vier logit.
            val prob = if (melhorScore < -0.05f || melhorScore > 1.05f) sigmoid(melhorScore) else melhorScore.coerceIn(0f, 1f)
            if (prob < scoreThreshold) continue

            // Mapeamento reverso do letterbox -> coordenadas do bitmap original
            val left   = ((l - padX) / scale).coerceIn(0f, imgW)
            val top    = ((t - padY) / scale).coerceIn(0f, imgH)
            val right  = ((r - padX) / scale).coerceIn(0f, imgW)
            val bottom = ((b - padY) / scale).coerceIn(0f, imgH)

            if ((right - left) >= 4f && (bottom - top) >= 4f) {
                destino.add(floatArrayOf(left, top, right, bottom, prob, melhorClasse.toFloat()))
            }
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
            // NMS por classe: só suprime caixas da mesma classe
            sorted.removeAll { it[5] == best[5] && iouCorners(best, it) > iouThresh }
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
            // Modelos importados ficam em filesDir (file://): usa direto, sem recopiar dezenas de MB a cada abertura.
            val direto = if (s.uri.scheme == "file") s.uri.path?.let { File(it) } else null
            if (direto != null && direto.exists() && direto.length() > 0L) {
                direto
            } else {
                File(context.cacheDir, "custom_model_cache.tflite").also { f ->
                    val entrada = context.contentResolver.openInputStream(s.uri)
                        ?: throw IllegalStateException("Sem acesso ao modelo")
                    entrada.use { i -> FileOutputStream(f).use { i.copyTo(it) } }
                }
            }
        } catch (e: Exception) {
            ultimoErro = "Nao foi possivel acessar o modelo externo. Importe novamente."
            detectorListener?.onError(ultimoErro!!); null
        }
    }

    fun fechar() = close()

    @Synchronized
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