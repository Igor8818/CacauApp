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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.myapplication.data.DetectionResult
import java.io.InputStream
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

private val inferenceExecutor = Executors.newSingleThreadExecutor()

/**
 * Visualizador de câmera com CameraX e inferência de IA desacoplada da UI:
 * - Aplica rotationDegrees aos frames antes da inferência para garantir caixas alinhadas;
 * - Suporta toque para focar e pinch-to-zoom nativo na tela do operador;
 * - Throttling adaptativo para prevenir acúmulo de frames na fila.
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

    androidx.compose.ui.viewinterop.AndroidView(
        factory = { ctx ->
            val view = PreviewView(ctx).apply {
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            }

            val inferenceRunning = AtomicBoolean(false)
            val lastInferenceMs = AtomicLong(0L)
            val minIntervalMs = 600L // Taxa equilibrada para eficiência térmica em campo

            providerFuture.addListener({
                val provider = providerFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(view.surfaceProvider)
                }

                val analyzer = ImageAnalysis.Builder()
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

                                helper.setListener(object : ObjectDetectorHelper.DetectorListener {
                                    override fun onResults(results: List<DetectionResult>) {
                                        onResultsUpdated(results)
                                    }
                                    override fun onError(mensagem: String) {
                                        Log.e("CameraAI", "Detector: $mensagem")
                                    }
                                })

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

                    // Configura zoom por pinça e foco por toque
                    val cameraControl = camera.cameraControl
                    val cameraInfo = camera.cameraInfo
                    onCameraControlPronto?.invoke(cameraControl, cameraInfo)

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
                        if (event.action == android.view.MotionEvent.ACTION_UP) {
                            val factory = view.meteringPointFactory
                            val point = factory.createPoint(event.x, event.y)
                            val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF).build()
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
fun carregarBitmapDeUri(context: Context, uri: Uri): Bitmap? {
    return try {
        val input: InputStream? = context.contentResolver.openInputStream(uri)
        BitmapFactory.decodeStream(input)
    } catch (e: Exception) {
        Log.e("CameraUtils", "Erro ao carregar bitmap da URI: ${e.message}")
        null
    }
}