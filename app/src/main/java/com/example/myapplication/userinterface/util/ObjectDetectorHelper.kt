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
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Helper de detecção TFLite otimizado para inferência YOLO de frutos de cacau em campo (CEPLAC):
 * - Pré-processamento com Letterbox (proporção preservada, preenchimento cinza 114, interpolação bilinear);
 * - Compensação adaptativa de sombra (Gamma LUT para cabruca/sub-bosque escuro);
 * - Inferência Multi-Escala e Tiling sobreposto (SAHI-lite) com Weighted Box Fusion (WBF) para fotos;
 * - NMS aprimorado com supressão por contenção (IoS / Intersection over Smaller Area) e filtro geométrico;
 * - Rastreamento temporal com histerese e suavização EMA para estabilidade na câmera ao vivo;
 * - Inferência serializada (thread-safe): a câmera ao vivo e a revisão da foto nunca competem pelos buffers.
 */
class ObjectDetectorHelper(
    val context: Context,
    var detectorListener: DetectorListener?,
    var modelSource: ModelSource = ModelSource.Asset("detector.tflite"),
    var scoreThreshold: Float = 0.25f,
    var maxResults: Int = 50,
    var labelsModelo: List<String> = listOf("cacau", "fruto"),
    var numThreads: Int = 4,
    var tentarGpu: Boolean = false
) {
    sealed class ModelSource {
        data class Asset(val fileName: String) : ModelSource()
        data class External(val uri: Uri, val displayName: String) : ModelSource()
        data class InternalFile(val path: String, val displayName: String) : ModelSource()
    }

    private data class RastroTemporal(
        var left: Float,
        var top: Float,
        var right: Float,
        var bottom: Float,
        var score: Float,
        val classe: Int,
        var quadrosVistos: Int = 1,
        var quadrosPerdidos: Int = 0
    )

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

    // Tabelas de consulta (LUT) para normalização RGB [0..255] -> [0f..1f]
    private val lut255 = FloatArray(256) { it / 255f }
    // LUT com realce de sombra (gamma 0.72 + leve ganho nos tons escuros) para troncos sombreados na cabruca
    private val lutSombra255 = FloatArray(256) { i ->
        if (i == 0) 0f else (i / 255.0).pow(0.72).toFloat().coerceIn(0f, 1f)
    }

    // Buffers reutilizáveis para evitar pressão no Garbage Collector (GC)
    private var bufferEntrada: ByteBuffer? = null
    private var pixelsBuffer: IntArray? = null
    private var letterboxBitmap: Bitmap? = null
    private var letterboxCanvas: Canvas? = null
    private val paintCinza = Paint().apply { color = Color.rgb(114, 114, 114) }
    private val paintFiltro = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private var outBuffer: Array<Array<FloatArray>>? = null

    // Estabilização temporal (câmera ao vivo)
    private val historicoContagens = mutableListOf<Int>()
    private val janelaMediana = 5
    private val rastrosAtivos = mutableListOf<RastroTemporal>()
    private var ultimoFrameLargura = 0f
    private var ultimoFrameAltura = 0f

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
        synchronized(rastrosAtivos) { rastrosAtivos.clear() }
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
                try {
                    setUseXNNPACK(true)
                } catch (_: Throwable) {}
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
     * Inferência para a câmera ao vivo com suavização temporal (EMA + histerese de 1 quadro)
     * para evitar que caixas pisquem quando o trabalhador movimenta o celular.
     */
    fun detect(image: Bitmap) {
        val inicio = System.currentTimeMillis()
        val brutas = detectarInterno(
            image = image,
            usarMultiEscala = false,
            permitirHisterese = true
        )
        val estabilizadas = aplicarRastreamentoTemporal(brutas, image.width.toFloat(), image.height.toFloat())
        val deteccoes = converterParaDetectionResults(estabilizadas)
        registrarContagemHistorico(deteccoes.size)
        ultimaLatenciaMs = System.currentTimeMillis() - inicio
        detectorListener?.onResults(deteccoes)
    }

    /**
     * Inferência síncrona padrão em 1 passada (rápida e determinística).
     */
    @Synchronized
    fun detectar(image: Bitmap): List<DetectionResult> {
        val inicio = System.currentTimeMillis()
        val caixas = detectarInterno(
            image = image,
            usarMultiEscala = false,
            permitirHisterese = false
        )
        val deteccoes = converterParaDetectionResults(caixas)
        registrarContagemHistorico(deteccoes.size)
        ultimaLatenciaMs = System.currentTimeMillis() - inicio
        return deteccoes
    }

    /**
     * Inferência de Alta Precisão para a foto capturada (Revisão Pós-Foto):
     * Executa um ensemble Multi-Escala com Tiling 1:1 sobreposto (SAHI-lite) + foco no tronco
     * central com recuperação adaptativa de sombra (cabruca) e fusão ponderada de caixas (WBF).
     * Permite detectar frutos pequenos, parcialmente ocluídos ou sombreados sem duplicar caixas.
     */
    @Synchronized
    fun detectarAltaPrecisao(image: Bitmap): List<DetectionResult> {
        val inicio = System.currentTimeMillis()
        val caixas = detectarInterno(
            image = image,
            usarMultiEscala = true,
            permitirHisterese = false
        )
        val deteccoes = converterParaDetectionResults(caixas)
        registrarContagemHistorico(deteccoes.size)
        ultimaLatenciaMs = System.currentTimeMillis() - inicio
        return deteccoes
    }

    @Synchronized
    private fun detectarInterno(
        image: Bitmap,
        usarMultiEscala: Boolean,
        permitirHisterese: Boolean
    ): List<FloatArray> {
        lastBitmapWidth  = image.width.toFloat()
        lastBitmapHeight = image.height.toFloat()

        val interp = interpreter ?: run { setup(); interpreter ?: return emptyList() }
        val out = outBuffer ?: return emptyList()
        if (numAnchors == 0) return emptyList()

        try {
            val imgW = image.width
            val imgH = image.height
            if (imgW < 16 || imgH < 16) return emptyList()

            val limiarBase = if (permitirHisterese) {
                (scoreThreshold - 0.06f).coerceAtLeast(0.16f)
            } else {
                scoreThreshold.coerceAtLeast(0.18f)
            }

            if (!usarMultiEscala) {
                val candidatos = ArrayList<FloatArray>(64)
                executarPassadaRecorte(
                    interp = interp,
                    out = out,
                    image = image,
                    srcRect = Rect(0, 0, imgW, imgH),
                    limiar = limiarBase,
                    forcarRealceSombra = false,
                    passadaId = 0,
                    destino = candidatos
                )
                val filtrados = if (layout == LayoutSaida.END_TO_END) {
                    nmsAprimorado(candidatos, iouThresh = 0.45f, iosThresh = 0.68f)
                } else {
                    nmsAprimorado(candidatos, iouThresh = 0.42f, iosThresh = 0.65f)
                }
                return filtrados.take(maxResults)
            }

            // ── MODO ALTA PRECISÃO (MULTI-ESCALA + TILING SOBREPOSTO + REALCE DE SOMBRA) ──
            val todosCandidatos = ArrayList<FloatArray>(160)
            val limiarConsenso = (scoreThreshold - 0.04f).coerceAtLeast(0.18f)

            // Passada 0: Visão Global Completa (captura frutos grandes/médios e contexto geral)
            executarPassadaRecorte(
                interp = interp,
                out = out,
                image = image,
                srcRect = Rect(0, 0, imgW, imgH),
                limiar = limiarConsenso,
                forcarRealceSombra = false,
                passadaId = 0,
                destino = todosCandidatos
            )

            // Passadas 1 e 2: Recortes quadrados 1:1 sobrepostos (0% de barra cinza no letterbox, +33% de resolução!)
            if (imgH > (imgW * 1.12f).roundToInt()) {
                // Foto vertical (retrato): quadrado superior e quadrado inferior cobrindo o tronco inteiro com sobreposição central
                executarPassadaRecorte(
                    interp = interp,
                    out = out,
                    image = image,
                    srcRect = Rect(0, 0, imgW, imgW),
                    limiar = limiarConsenso,
                    forcarRealceSombra = false,
                    passadaId = 1,
                    destino = todosCandidatos
                )
                executarPassadaRecorte(
                    interp = interp,
                    out = out,
                    image = image,
                    srcRect = Rect(0, imgH - imgW, imgW, imgH),
                    limiar = limiarConsenso,
                    forcarRealceSombra = false,
                    passadaId = 2,
                    destino = todosCandidatos
                )
            } else if (imgW > (imgH * 1.12f).roundToInt()) {
                // Foto horizontal (paisagem): quadrado esquerdo e quadrado direito
                executarPassadaRecorte(
                    interp = interp,
                    out = out,
                    image = image,
                    srcRect = Rect(0, 0, imgH, imgH),
                    limiar = limiarConsenso,
                    forcarRealceSombra = false,
                    passadaId = 1,
                    destino = todosCandidatos
                )
                executarPassadaRecorte(
                    interp = interp,
                    out = out,
                    image = image,
                    srcRect = Rect(imgW - imgH, 0, imgW, imgH),
                    limiar = limiarConsenso,
                    forcarRealceSombra = false,
                    passadaId = 2,
                    destino = todosCandidatos
                )
            }

            // Passada 3: Zoom central no tronco (12%..88%) com realce de sombra para frutos escuros/roxos
            val margemX = (imgW * 0.12f).roundToInt()
            val margemY = (imgH * 0.12f).roundToInt()
            if (imgW - 2 * margemX >= 120 && imgH - 2 * margemY >= 120) {
                executarPassadaRecorte(
                    interp = interp,
                    out = out,
                    image = image,
                    srcRect = Rect(margemX, margemY, imgW - margemX, imgH - margemY),
                    limiar = scoreThreshold.coerceAtLeast(0.22f),
                    forcarRealceSombra = true,
                    passadaId = 3,
                    destino = todosCandidatos
                )
            }

            val fundidos = fundirMultiEscalaWbf(
                candidatos = todosCandidatos,
                scoreThresholdFinal = scoreThreshold,
                iouThresh = 0.42f,
                iosThresh = 0.65f
            )
            return fundidos.take(maxResults)

        } catch (e: Exception) {
            Log.e("Detector", "Erro durante deteccao: ${e.message}")
            return emptyList()
        }
    }

    /**
     * Executa uma passada de inferência sobre a região [srcRect] do [image] e projeta as caixas
     * detectadas diretamente para o sistema de coordenadas global da imagem original.
     */
    private fun executarPassadaRecorte(
        interp: Interpreter,
        out: Array<Array<FloatArray>>,
        image: Bitmap,
        srcRect: Rect,
        limiar: Float,
        forcarRealceSombra: Boolean,
        passadaId: Int,
        destino: MutableList<FloatArray>
    ) {
        val cropW = srcRect.width().toFloat()
        val cropH = srcRect.height().toFloat()
        if (cropW <= 1f || cropH <= 1f) return

        val scale = minOf(inputSize / cropW, inputSize / cropH)
        val scaledW = (cropW * scale).roundToInt()
        val scaledH = (cropH * scale).roundToInt()
        val padX = (inputSize - scaledW) / 2f
        val padY = (inputSize - scaledH) / 2f

        val lbBitmap = letterboxBitmap ?: Bitmap.createBitmap(inputSize, inputSize, Bitmap.Config.ARGB_8888).also { letterboxBitmap = it }
        val lbCanvas = letterboxCanvas ?: Canvas(lbBitmap).also { letterboxCanvas = it }

        lbCanvas.drawRect(0f, 0f, inputSize.toFloat(), inputSize.toFloat(), paintCinza)
        val dstRect = RectF(padX, padY, padX + scaledW, padY + scaledH)
        lbCanvas.drawBitmap(image, srcRect, dstRect, paintFiltro)

        val buf = bufferEntrada ?: return
        buf.rewind()
        val pixels = pixelsBuffer ?: return
        lbBitmap.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)

        val usarLutSombra = forcarRealceSombra || imagemSombreada(pixels)
        preencherEntrada(buf, pixels, if (usarLutSombra) lutSombra255 else lut255)
        buf.rewind()

        interp.run(buf, out)

        val candidatosPassada = ArrayList<FloatArray>(48)
        decodificarRecorte(
            out = out,
            scale = scale,
            padX = padX,
            padY = padY,
            srcRect = srcRect,
            imgW = image.width.toFloat(),
            imgH = image.height.toFloat(),
            limiar = limiar,
            passadaId = passadaId,
            destino = candidatosPassada
        )

        // NMS intra-passada para eliminar âncoras redundantes da mesma escala antes da fusão global
        val limposPassada = nmsAprimorado(candidatosPassada, iouThresh = 0.44f, iosThresh = 0.68f)
        destino.addAll(limposPassada)
    }

    /** Avalia rapidamente por amostragem se o quadro está escuro/sombreado (típico de cabruca). */
    private fun imagemSombreada(pixels: IntArray): Boolean {
        if (pixels.isEmpty()) return false
        var somaLuminancia = 0L
        var amostras = 0
        val passo = 37 // Amostragem esparsa (~11.000 pontos em 640x640, custo < 0.2ms)
        var idx = 0
        while (idx < pixels.size) {
            val px = pixels[idx]
            val r = (px ushr 16) and 0xFF
            val g = (px ushr 8) and 0xFF
            val b = px and 0xFF
            // Ignora o cinza exato do letterbox (114, 114, 114)
            if (!(r == 114 && g == 114 && b == 114)) {
                somaLuminancia += (r * 77 + g * 150 + b * 29) shr 8
                amostras++
            }
            idx += passo
        }
        if (amostras == 0) return false
        val media = somaLuminancia / amostras
        return media in 15..92
    }

    private fun preencherEntrada(buf: ByteBuffer, pixels: IntArray, tabelaFloat: FloatArray) {
        val plano = inputSize * inputSize
        if (entradaFloat) {
            if (!entradaNchw) {
                for (px in pixels) {
                    buf.putFloat(tabelaFloat[(px ushr 16) and 0xFF])
                    buf.putFloat(tabelaFloat[(px ushr 8) and 0xFF])
                    buf.putFloat(tabelaFloat[px and 0xFF])
                }
            } else {
                val fb = buf.asFloatBuffer()
                for (i in 0 until plano) {
                    val px = pixels[i]
                    fb.put(i, tabelaFloat[(px ushr 16) and 0xFF])
                    fb.put(plano + i, tabelaFloat[(px ushr 8) and 0xFF])
                    fb.put(2 * plano + i, tabelaFloat[px and 0xFF])
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

    /**
     * Converte a saída bruta do modelo em caixas `[left, top, right, bottom, score, classe, passadaId]`
     * no espaço da imagem global, aplicando filtro geométrico e proteção de borda de corte.
     */
    private fun decodificarRecorte(
        out: Array<Array<FloatArray>>,
        scale: Float,
        padX: Float,
        padY: Float,
        srcRect: Rect,
        imgW: Float,
        imgH: Float,
        limiar: Float,
        passadaId: Int,
        destino: MutableList<FloatArray>
    ) {
        val tam = inputSize.toFloat()
        val cropW = srcRect.width().toFloat()
        val cropH = srcRect.height().toFloat()

        // Detecta se as coordenadas estão em pixels (0..tam) ou normalizadas (0..1)
        // Usa cx e cy com limiar > 2.0f para não confundir larguras grandes de âncoras stride-32 (até 1.5) com pixels
        var maxCoord = 0f
        when (layout) {
            LayoutSaida.TRANSPOSTO -> for (i in 0 until numAnchors) {
                maxCoord = maxOf(maxCoord, out[0][0][i], out[0][1][i])
            }
            LayoutSaida.PADRAO -> for (i in 0 until numAnchors) {
                maxCoord = maxOf(maxCoord, out[0][i][0], out[0][i][1])
            }
            LayoutSaida.END_TO_END -> for (i in 0 until numAnchors) {
                maxCoord = maxOf(maxCoord, out[0][i][2], out[0][i][3])
            }
        }
        val fator = if (maxCoord > 2.0f) 1f else tam

        val temCorteEsq = srcRect.left > 2
        val temCorteTopo = srcRect.top > 2
        val temCorteDir = srcRect.right < (imgW.roundToInt() - 2)
        val temCorteBase = srcRect.bottom < (imgH.roundToInt() - 2)
        val margemBordaCorteX = cropW * 0.018f
        val margemBordaCorteY = cropH * 0.018f

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
                        for (c in 0 until numClasses) {
                            val s = out[0][4 + c][i]
                            if (s > melhorScore) { melhorScore = s; melhorClasse = c }
                        }
                        val probPrevia = if (melhorScore < -0.05f || melhorScore > 1.05f) sigmoid(melhorScore) else melhorScore
                        if (probPrevia < limiar) continue
                        cx = out[0][0][i] * fator; cy = out[0][1][i] * fator
                        w = out[0][2][i] * fator;  h = out[0][3][i] * fator
                    } else {
                        val row = out[0][i]
                        for (c in 0 until numClasses) {
                            val s = row[4 + c]
                            if (s > melhorScore) { melhorScore = s; melhorClasse = c }
                        }
                        val probPrevia = if (melhorScore < -0.05f || melhorScore > 1.05f) sigmoid(melhorScore) else melhorScore
                        if (probPrevia < limiar) continue
                        cx = row[0] * fator; cy = row[1] * fator
                        w = row[2] * fator;  h = row[3] * fator
                    }
                    l = cx - w / 2f; t = cy - h / 2f; r = cx + w / 2f; b = cy + h / 2f
                }
            }

            val prob = if (melhorScore < -0.05f || melhorScore > 1.05f) sigmoid(melhorScore) else melhorScore.coerceIn(0f, 1f)
            if (prob < limiar) continue

            // Coordenadas locais dentro do recorte (crop)
            val cropLeft   = ((l - padX) / scale).coerceIn(0f, cropW)
            val cropTop    = ((t - padY) / scale).coerceIn(0f, cropH)
            val cropRight  = ((r - padX) / scale).coerceIn(0f, cropW)
            val cropBottom = ((b - padY) / scale).coerceIn(0f, cropH)

            // Em recortes parciais (tiles/zoom), ignora frutos cortados exatamente pela linha artificial do recorte
            if (passadaId > 0) {
                val cortadoEsq = temCorteEsq && cropLeft <= margemBordaCorteX
                val cortadoTopo = temCorteTopo && cropTop <= margemBordaCorteY
                val cortadoDir = temCorteDir && cropRight >= (cropW - margemBordaCorteX)
                val cortadoBase = temCorteBase && cropBottom >= (cropH - margemBordaCorteY)
                if (cortadoEsq || cortadoTopo || cortadoDir || cortadoBase) continue
            }

            // Projeção para a imagem global
            val left   = (srcRect.left + cropLeft).coerceIn(0f, imgW)
            val top    = (srcRect.top + cropTop).coerceIn(0f, imgH)
            val right  = (srcRect.left + cropRight).coerceIn(0f, imgW)
            val bottom = (srcRect.top + cropBottom).coerceIn(0f, imgH)

            if (caixaValidaParaCacau(left, top, right, bottom, imgW, imgH)) {
                destino.add(floatArrayOf(left, top, right, bottom, prob, melhorClasse.toFloat(), passadaId.toFloat()))
            }
        }
    }

    /**
     * Suaviza coordenadas (EMA) e aplica histerese temporal de 1 quadro para a câmera ao vivo,
     * eliminando piscadas nas caixas quando a confiança oscila próxima ao limiar.
     */
    private fun aplicarRastreamentoTemporal(
        novasCaixas: List<FloatArray>,
        imgW: Float,
        imgH: Float
    ): List<FloatArray> {
        synchronized(rastrosAtivos) {
            if (ultimoFrameLargura != imgW || ultimoFrameAltura != imgH) {
                rastrosAtivos.clear()
                ultimoFrameLargura = imgW
                ultimoFrameAltura = imgH
            }

            val usados = BooleanArray(novasCaixas.size)
            val rastrosAtualizados = mutableListOf<RastroTemporal>()
            val alfaPos = 0.65f
            val alfaScore = 0.60f

            // 1. Associa rastros existentes às novas detecções
            for (rastro in rastrosAtivos) {
                val caixaRastro = floatArrayOf(rastro.left, rastro.top, rastro.right, rastro.bottom, rastro.score, rastro.classe.toFloat())
                var melhorIdx = -1
                var melhorIou = 0f

                for (i in novasCaixas.indices) {
                    if (usados[i]) continue
                    val cand = novasCaixas[i]
                    if (cand[5].toInt() != rastro.classe) continue
                    val iou = iouCorners(caixaRastro, cand)
                    val ios = iosContainment(caixaRastro, cand)
                    val afinidade = maxOf(iou, ios * 0.8f)
                    if (afinidade > melhorIou && (iou >= 0.25f || ios >= 0.60f)) {
                        melhorIou = afinidade
                        melhorIdx = i
                    }
                }

                if (melhorIdx >= 0) {
                    usados[melhorIdx] = true
                    val c = novasCaixas[melhorIdx]
                    rastro.left = alfaPos * c[0] + (1f - alfaPos) * rastro.left
                    rastro.top = alfaPos * c[1] + (1f - alfaPos) * rastro.top
                    rastro.right = alfaPos * c[2] + (1f - alfaPos) * rastro.right
                    rastro.bottom = alfaPos * c[3] + (1f - alfaPos) * rastro.bottom
                    rastro.score = maxOf(c[4], alfaScore * c[4] + (1f - alfaScore) * rastro.score)
                    rastro.quadrosVistos = (rastro.quadrosVistos + 1).coerceAtMost(10)
                    rastro.quadrosPerdidos = 0
                    rastrosAtualizados.add(rastro)
                } else if (rastro.quadrosVistos >= 2 && rastro.quadrosPerdidos == 0 && rastro.score >= scoreThreshold) {
                    // Mantém por 1 quadro caso tenha sumido momentaneamente por tremido da mão
                    rastro.quadrosPerdidos = 1
                    rastro.score *= 0.92f
                    rastrosAtualizados.add(rastro)
                }
            }

            // 2. Cria novos rastros para detecções inéditas que superem o scoreThreshold
            for (i in novasCaixas.indices) {
                if (usados[i]) continue
                val c = novasCaixas[i]
                if (c[4] >= scoreThreshold) {
                    rastrosAtualizados.add(
                        RastroTemporal(
                            left = c[0],
                            top = c[1],
                            right = c[2],
                            bottom = c[3],
                            score = c[4],
                            classe = c[5].toInt(),
                            quadrosVistos = 1,
                            quadrosPerdidos = 0
                        )
                    )
                }
            }

            rastrosAtivos.clear()
            rastrosAtivos.addAll(rastrosAtualizados)

            val caixasFinais = rastrosAtivos
                .filter { it.score >= (scoreThreshold - 0.03f) }
                .map { floatArrayOf(it.left, it.top, it.right, it.bottom, it.score.coerceAtLeast(scoreThreshold), it.classe.toFloat()) }

            return nmsAprimorado(caixasFinais, iouThresh = 0.42f, iosThresh = 0.65f).take(maxResults)
        }
    }

    private fun converterParaDetectionResults(selecionados: List<FloatArray>): List<DetectionResult> {
        return selecionados.map { box ->
            val rect = RectF(box[0], box[1], box[2], box[3])
            val classIdx = box[5].toInt()
            val labelNome = labelsModelo.getOrNull(classIdx)
                ?: if (numClasses <= 1) "cacau" else "classe_$classIdx"
            DetectionResult.create(
                rect,
                listOf(DetectionCategory.create(labelNome, labelNome, box[4]))
            )
        }
    }

    private fun registrarContagemHistorico(qtd: Int) {
        synchronized(historicoContagens) {
            historicoContagens.add(qtd)
            if (historicoContagens.size > janelaMediana) historicoContagens.removeAt(0)
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

// ── Funções Geométricas, NMS por Contenção (IoS) e Fusão Multi-Escala (WBF) ──

internal fun iouCorners(a: FloatArray, b: FloatArray): Float {
    val interLeft   = maxOf(a[0], b[0])
    val interTop    = maxOf(a[1], b[1])
    val interRight  = minOf(a[2], b[2])
    val interBottom = minOf(a[3], b[3])
    val interArea   = maxOf(0f, interRight - interLeft) * maxOf(0f, interBottom - interTop)
    val areaA = maxOf(0f, a[2] - a[0]) * maxOf(0f, a[3] - a[1])
    val areaB = maxOf(0f, b[2] - b[0]) * maxOf(0f, b[3] - b[1])
    val union = areaA + areaB - interArea
    return if (union <= 0f) 0f else interArea / union
}

/**
 * Intersection over Smaller Area (IoS / IoM): mede o quanto a menor caixa está contida dentro da maior.
 * Essencial para frutos de cacau atravessados por folhas/galhos, evitando que 1 fruto gere 2 caixas.
 */
internal fun iosContainment(a: FloatArray, b: FloatArray): Float {
    val interLeft   = maxOf(a[0], b[0])
    val interTop    = maxOf(a[1], b[1])
    val interRight  = minOf(a[2], b[2])
    val interBottom = minOf(a[3], b[3])
    val interArea   = maxOf(0f, interRight - interLeft) * maxOf(0f, interBottom - interTop)
    val areaA = maxOf(0f, a[2] - a[0]) * maxOf(0f, a[3] - a[1])
    val areaB = maxOf(0f, b[2] - b[0]) * maxOf(0f, b[3] - b[1])
    val minArea = minOf(areaA, areaB)
    return if (minArea <= 0f) 0f else interArea / minArea
}

internal fun mesmoFruto(
    a: FloatArray,
    b: FloatArray,
    iouThresh: Float = 0.42f,
    iosThresh: Float = 0.65f
): Boolean {
    if (a.size >= 6 && b.size >= 6 && a[5].toInt() != b[5].toInt()) return false
    val iou = iouCorners(a, b)
    if (iou > iouThresh) return true
    val ios = iosContainment(a, b)
    if (ios > iosThresh) return true

    // Proximidade de centro com sobreposição moderada
    if (iou > 0.22f) {
        val cxa = (a[0] + a[2]) * 0.5f
        val cya = (a[1] + a[3]) * 0.5f
        val cxb = (b[0] + b[2]) * 0.5f
        val cyb = (b[1] + b[3]) * 0.5f
        val diagA = hypot((a[2] - a[0]).toDouble(), (a[3] - a[1]).toDouble()).toFloat()
        val diagB = hypot((b[2] - b[0]).toDouble(), (b[3] - b[1]).toDouble()).toFloat()
        val minDiag = minOf(diagA, diagB).coerceAtLeast(1f)
        val dist = hypot((cxa - cxb).toDouble(), (cya - cyb).toDouble()).toFloat()
        if (dist < minDiag * 0.30f) return true
    }
    return false
}

/**
 * Filtra artefatos geométricos incompatíveis com frutos de cacau (ex.: galhos finos ou caixa cobrindo a árvore toda).
 */
internal fun caixaValidaParaCacau(
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    imgW: Float,
    imgH: Float
): Boolean {
    val w = right - left
    val h = bottom - top
    val minDim = maxOf(5f, minOf(imgW, imgH) * 0.010f)
    if (w < minDim || h < minDim) return false

    val aspect = w / h
    if (aspect < 0.18f || aspect > 5.5f) return false

    val areaRatio = (w * h) / (imgW * imgH).coerceAtLeast(1f)
    if (areaRatio > 0.60f) return false

    return true
}

/**
 * NMS aprimorado com refinamento de coordenadas (WBF para sobreposições fortes) e
 * supressão de sub-caixas contidas (IoS > iosThresh).
 */
internal fun nmsAprimorado(
    boxes: List<FloatArray>,
    iouThresh: Float = 0.42f,
    iosThresh: Float = 0.65f
): List<FloatArray> {
    if (boxes.isEmpty()) return emptyList()
    val sorted = boxes.sortedByDescending { it[4] }.toMutableList()
    val result = ArrayList<FloatArray>(sorted.size)

    while (sorted.isNotEmpty()) {
        val best = sorted.removeAt(0).copyOf()
        var pesoTotal = best[4]
        var somaL = best[0] * pesoTotal
        var somaT = best[1] * pesoTotal
        var somaR = best[2] * pesoTotal
        var somaB = best[3] * pesoTotal

        val iter = sorted.iterator()
        while (iter.hasNext()) {
            val cand = iter.next()
            if (mesmoFruto(best, cand, iouThresh, iosThresh)) {
                // Só refina as bordas se as duas caixas tiverem tamanho compatível (IoU alto),
                // evitando que uma sub-caixa parcial encolha a caixa completa do fruto.
                if (iouCorners(best, cand) >= 0.55f) {
                    val p = cand[4]
                    somaL += cand[0] * p
                    somaT += cand[1] * p
                    somaR += cand[2] * p
                    somaB += cand[3] * p
                    pesoTotal += p
                }
                iter.remove()
            }
        }

        best[0] = somaL / pesoTotal
        best[1] = somaT / pesoTotal
        best[2] = somaR / pesoTotal
        best[3] = somaB / pesoTotal
        result.add(best)
    }
    return result
}

/**
 * Fusão Multi-Escala (Weighted Box Fusion + Consenso entre Passadas):
 * Combina detecções da imagem global, recortes quadrados 1:1 e zoom no tronco.
 * Frutos detectados em 2 ou mais escalas ganham bônus de confiança e têm suas caixas refinadas.
 */
internal fun fundirMultiEscalaWbf(
    candidatos: List<FloatArray>,
    scoreThresholdFinal: Float,
    iouThresh: Float = 0.42f,
    iosThresh: Float = 0.65f
): List<FloatArray> {
    if (candidatos.isEmpty()) return emptyList()
    val ordenados = candidatos.sortedByDescending { it[4] }.toMutableList()
    val fundidos = ArrayList<FloatArray>(ordenados.size)

    while (ordenados.isNotEmpty()) {
        val semente = ordenados.removeAt(0)
        val cluster = mutableListOf(semente)
        val passadasDistintas = mutableSetOf<Int>()
        if (semente.size >= 7) passadasDistintas.add(semente[6].toInt()) else passadasDistintas.add(0)

        val iter = ordenados.iterator()
        while (iter.hasNext()) {
            val cand = iter.next()
            if (mesmoFruto(semente, cand, iouThresh, iosThresh)) {
                cluster.add(cand)
                if (cand.size >= 7) passadasDistintas.add(cand[6].toInt())
                iter.remove()
            }
        }

        val maxScore = cluster.maxOf { it[4] }
        val numPassadas = passadasDistintas.size
        // Consenso multi-escala: se visto em >= 2 escalas diferentes, recebe reforço de confiança
        val bonusConsenso = if (numPassadas >= 2) 0.05f * (numPassadas - 1).coerceAtMost(2) else 0f
        val scoreFinal = (maxScore + bonusConsenso).coerceAtMost(0.99f)

        // Se veio apenas de um recorte secundário (sem confirmação na visão global ou outra escala),
        // exige que supere o limiar configurado com folga para evitar falsos positivos isolados.
        val veioDaGlobal = passadasDistintas.contains(0)
        val limiarExigido = if (numPassadas >= 2 || veioDaGlobal) {
            scoreThresholdFinal
        } else {
            maxOf(scoreThresholdFinal + 0.03f, 0.28f)
        }

        if (scoreFinal < limiarExigido) continue

        // Calcula média ponderada das caixas completas do cluster (aquelas com IoU >= 0.48 com a melhor)
        var pesoTotal = semente[4]
        var l = semente[0] * pesoTotal
        var t = semente[1] * pesoTotal
        var r = semente[2] * pesoTotal
        var b = semente[3] * pesoTotal

        for ( idx in 1 until cluster.size) {
            val c = cluster[idx]
            if (iouCorners(semente, c) >= 0.48f) {
                val p = c[4]
                l += c[0] * p
                t += c[1] * p
                r += c[2] * p
                b += c[3] * p
                pesoTotal += p
            }
        }

        fundidos.add(
            floatArrayOf(
                l / pesoTotal,
                t / pesoTotal,
                r / pesoTotal,
                b / pesoTotal,
                scoreFinal,
                semente[5]
            )
        )
    }

    return nmsAprimorado(fundidos, iouThresh = iouThresh, iosThresh = iosThresh)
        .sortedByDescending { it[4] }
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
