package com.example.myapplication.userinterface.util

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import android.view.ScaleGestureDetector
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.myapplication.data.DetectionResult
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

private val inferenceExecutor = Executors.newSingleThreadExecutor()

/**
 * Visualizador de câmera com CameraX e inferência de IA desacoplada da UI:
 * - Aplica rotationDegrees aos frames antes da inferência para garantir caixas alinhadas;
 * - Foco e fotometria (AF/AE/AWB) automáticos priorizando o tronco no centro do quadro;
 * - Suporta toque para focar/expor e pinch-to-zoom nativo na tela do operador;
 * - Throttling adaptativo (~5.5 FPS) para alimentar o rastreamento temporal suave.
 */
@Composable
fun CameraWithAI(
    helper: ObjectDetectorHelper,
    capture: ImageCapture,
    onCameraControlPronto: ((CameraControl, CameraInfo) -> Unit)? = null,
    onResultsUpdated: (List<DetectionResult>) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val providerFuture = remember { ProcessCameraProvider.getInstance(context) }

    // Ao sair da tela de câmera (ex.: abrir a revisão da foto) é obrigatório desvincular o CameraX;
    // caso contrário o analisador continua rodando em segundo plano e disputa o detector com a revisão.
    DisposableEffect(Unit) {
        onDispose {
            try {
                if (providerFuture.isDone) providerFuture.get().unbindAll()
            } catch (e: Exception) {
                Log.w("CameraAI", "Falha ao desvincular camera: ${e.message}")
            }
        }
    }

    androidx.compose.ui.viewinterop.AndroidView(
        factory = { ctx ->
            val view = PreviewView(ctx).apply {
                implementationMode = PreviewView.ImplementationMode.PERFORMANCE
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }

            val inferenceRunning = AtomicBoolean(false)
            val lastInferenceMs = AtomicLong(0L)
            val minIntervalMs = 180L // ~5.5 FPS: resposta rápida com rastreamento temporal suave

            helper.setListener(object : ObjectDetectorHelper.DetectorListener {
                override fun onResults(results: List<DetectionResult>) {
                    onResultsUpdated(results)
                }
                override fun onError(mensagem: String) {
                    Log.e("CameraAI", "Detector: $mensagem")
                }
            })

            providerFuture.addListener({
                val provider = providerFuture.get()
                val preview = Preview.Builder()
                    .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                    .build().also {
                        it.setSurfaceProvider(view.surfaceProvider)
                    }

                val analyzer = ImageAnalysis.Builder()
                    .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .build().also { analysis ->
                        analysis.setAnalyzer(inferenceExecutor) { proxy ->
                            val now = System.currentTimeMillis()
                            if (inferenceRunning.get() || (now - lastInferenceMs.get()) < minIntervalMs) {
                                proxy.close()
                                return@setAnalyzer
                            }

                            inferenceRunning.set(true)
                            lastInferenceMs.set(now)

                            try {
                                val rotation = proxy.imageInfo.rotationDegrees
                                val rawBitmap = proxy.toBitmap()
                                proxy.close()

                                // Rotaciona o bitmap conforme orientação física do sensor
                                val bitmapProcessado = if (rotation != 0) {
                                    val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
                                    Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true)
                                } else {
                                    rawBitmap
                                }

                                helper.detect(bitmapProcessado)

                            } catch (e: Exception) {
                                Log.e("CameraAI", "Erro no processamento do frame: ${e.message}")
                                try { proxy.close() } catch (_: Exception) {}
                            } finally {
                                inferenceRunning.set(false)
                            }
                        }
                    }

                try {
                    provider.unbindAll()
                    val camera = provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview, capture, analyzer
                    )

                    // Configura zoom por pinça, fotometria automática no centro (tronco) e foco/exposição por toque
                    val cameraControl = camera.cameraControl
                    val cameraInfo = camera.cameraInfo
                    onCameraControlPronto?.invoke(cameraControl, cameraInfo)

                    view.post {
                        try {
                            if (view.width > 0 && view.height > 0) {
                                val centerPoint = view.meteringPointFactory.createPoint(view.width / 2f, view.height / 2f, 0.25f)
                                val centerAction = FocusMeteringAction.Builder(
                                    centerPoint,
                                    FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE or FocusMeteringAction.FLAG_AWB
                                ).setAutoCancelDuration(4, TimeUnit.SECONDS).build()
                                cameraControl.startFocusAndMetering(centerAction)
                            }
                        } catch (_: Exception) {}
                    }

                    val scaleGestureDetector = ScaleGestureDetector(ctx, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                        override fun onScale(detector: ScaleGestureDetector): Boolean {
                            val currentZoomRatio = cameraInfo.zoomState.value?.zoomRatio ?: 1f
                            val delta = detector.scaleFactor
                            cameraControl.setZoomRatio(currentZoomRatio * delta)
                            return true
                        }
                    })

                    view.setOnTouchListener { _, event ->
                        scaleGestureDetector.onTouchEvent(event)
                        if (event.action == android.view.MotionEvent.ACTION_UP && !scaleGestureDetector.isInProgress) {
                            val factory = view.meteringPointFactory
                            val point = factory.createPoint(event.x, event.y, 0.22f)
                            val action = FocusMeteringAction.Builder(
                                point,
                                FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE or FocusMeteringAction.FLAG_AWB
                            ).setAutoCancelDuration(5, TimeUnit.SECONDS).build()
                            cameraControl.startFocusAndMetering(action)
                        }
                        true
                    }

                } catch (e: Exception) {
                    Log.e("CameraAI", "Falha ao vincular CameraX: ${e.message}")
                }
            }, ContextCompat.getMainExecutor(ctx))
            view
        },
        modifier = Modifier.fillMaxSize()
    )
}

/**
 * Captura foto em resolução cheia com tratamento de falhas e notificação ao chamador.
 */
fun tirarFoto(
    context: Context,
    capture: ImageCapture,
    id: String,
    onSucesso: (Uri) -> Unit,
    onErro: (String) -> Unit = {}
) {
    val name = "${limparSimbologiaPura(id)}_${System.currentTimeMillis()}.jpg"
    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, name)
        put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/CEPEC_Analise")
        }
    }
    val outputOptions = ImageCapture.OutputFileOptions.Builder(
        context.contentResolver,
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        values
    ).build()

    capture.takePicture(
        outputOptions,
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                val uri = output.savedUri
                if (uri != null) {
                    onSucesso(uri)
                } else {
                    onErro("Foto salva, mas o identificador de arquivo não foi retornado.")
                }
            }

            override fun onError(exc: ImageCaptureException) {
                val erroMsg = "Erro na captura da foto: ${exc.message}"
                Log.e("Camera", erroMsg)
                onErro(erroMsg)
            }
        }
    )
}

/**
 * Decodifica um bitmap a partir de uma URI de imagem no dispositivo.
 */
fun carregarBitmapDeUri(context: Context, uri: Uri, ladoMaximo: Int = 2400): Bitmap? {
    return try {
        // 1. Dimensões originais (sem alocar pixels) para calcular o downsample
        val limites = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, limites) }
        if (limites.outWidth <= 0 || limites.outHeight <= 0) return null

        var amostra = 1
        while (maxOf(limites.outWidth, limites.outHeight) / (amostra * 2) >= ladoMaximo) amostra *= 2

        // 2. Decodifica já reduzido (evita OOM com fotos de 12-50 MP)
        val bruto = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = amostra })
        } ?: return null

        // 3. Aplica a orientação EXIF: o Coil exibe a foto já girada, então a IA precisa
        //    analisar exatamente a mesma orientação para que as caixas coincidam com a imagem.
        val graus = lerRotacaoExif(context, uri)
        if (graus == 0) bruto else {
            val m = Matrix().apply { postRotate(graus.toFloat()) }
            Bitmap.createBitmap(bruto, 0, 0, bruto.width, bruto.height, m, true).also {
                if (it !== bruto) bruto.recycle()
            }
        }
    } catch (e: Exception) {
        Log.e("CameraUtils", "Erro ao carregar bitmap da URI: ${e.message}")
        null
    }
}

/** Rotação (0/90/180/270) indicada pelo EXIF da foto, ou 0 se ausente/ilegível. */
private fun lerRotacaoExif(context: Context, uri: Uri): Int = try {
    context.contentResolver.openInputStream(uri)?.use { s ->
        when (android.media.ExifInterface(s).getAttributeInt(
            android.media.ExifInterface.TAG_ORIENTATION,
            android.media.ExifInterface.ORIENTATION_NORMAL
        )) {
            android.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90
            android.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180
            android.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
    } ?: 0
} catch (_: Exception) { 0 }
