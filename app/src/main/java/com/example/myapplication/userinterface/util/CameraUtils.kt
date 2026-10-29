package com.example.myapplication.userinterface.util

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.tensorflow.lite.task.vision.detector.Detection
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

// Executor dedicado — inferência nunca roda na main thread
private val inferenceExecutor = Executors.newSingleThreadExecutor()

/**
 * Rotaciona o bitmap pelos graus indicados (normalmente vindos de
 * imageInfo.rotationDegrees). Se degrees == 0, devolve o próprio bitmap
 * sem custo extra.
 */
private fun Bitmap.rotatedBy(degrees: Int): Bitmap {
    if (degrees == 0) return this
    val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
    return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
}

@Composable
fun CameraWithAI(
    helper: ObjectDetectorHelper,
    capture: ImageCapture,
    onResultsUpdated: (List<Detection>) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val providerFuture = remember { ProcessCameraProvider.getInstance(context) }

    androidx.compose.ui.viewinterop.AndroidView(factory = { ctx ->
        val view = PreviewView(ctx).apply {
            // Garante que o preview use FILL_CENTER mantendo proporção da imagem
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }

        // Throttle para manter fluidez e resposta em tempo real
        val inferenceRunning = AtomicBoolean(false)
        val lastInferenceMs = AtomicLong(0L)
        val minIntervalMs = 150L // Inferência rápida e fluida (~6 a 7 FPS)

        providerFuture.addListener({
            val provider = providerFuture.get()

            // Força Preview e Análise a usarem a mesma proporção 4:3 para evitar distorção ou FOV diferente
            val preview = Preview.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .build()
                .also { it.setSurfaceProvider(view.surfaceProvider) }

            val analyzer = ImageAnalysis.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build().also { analysis ->
                    analysis.setAnalyzer(inferenceExecutor) { proxy ->
                        val now = System.currentTimeMillis()

                        // Pula frame se inferência ainda está rodando ou se intervalo mínimo não passou
                        if (inferenceRunning.get() || (now - lastInferenceMs.get()) < minIntervalMs) {
                            proxy.close()
                            return@setAnalyzer
                        }

                        inferenceRunning.set(true)
                        lastInferenceMs.set(now)

                        try {
                            val rotation = proxy.imageInfo.rotationDegrees
                            val bitmap = proxy.toBitmap().rotatedBy(rotation)
                            proxy.close() // Libera buffer imediatamente

                            helper.setListener(object : ObjectDetectorHelper.DetectorListener {
                                override fun onResults(results: List<Detection>) {
                                    onResultsUpdated(results)
                                }
                                override fun onError(mensagem: String) {
                                    Log.e("CameraAI", "Detector: $mensagem")
                                }
                            })

                            helper.detect(bitmap)

                        } catch (e: Exception) {
                            Log.e("CameraAI", "Erro no frame: ${e.message}")
                            try { proxy.close() } catch (_: Exception) {}
                        } finally {
                            inferenceRunning.set(false)
                        }
                    }
                }

            provider.unbindAll()
            provider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview, capture, analyzer
            )
        }, ContextCompat.getMainExecutor(ctx))
        view
    }, modifier = Modifier.fillMaxSize())
}

/**
 * Renderiza os bounding boxes calculados pelo detector sobre a PreviewView
 * respeitando com exatidão matemática a geometria de escala (FILL_CENTER) do CameraX.
 * Isso garante que os boxes verdes fiquem exatamente alinhados sobre os frutos
 * em qualquer celular (Samsung A50, Redmi Note 13, Xiaomi, Motorola, etc.).
 */
@Composable
fun BoundingBoxOverlay(
    detections: List<Detection>,
    bitmapWidth: Float,
    bitmapHeight: Float,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        if (bitmapWidth <= 0f || bitmapHeight <= 0f || size.width <= 0f || size.height <= 0f) return@Canvas

        val viewWidth = size.width
        val viewHeight = size.height
        val viewAspect = viewWidth / viewHeight
        val imageAspect = bitmapWidth / bitmapHeight

        val scale: Float
        val offsetX: Float
        val offsetY: Float

        if (viewAspect > imageAspect) {
            // View é mais larga que o frame da câmera -> preenche largura e corta topo/baixo
            scale = viewWidth / bitmapWidth
            offsetX = 0f
            offsetY = (viewHeight - bitmapHeight * scale) / 2f
        } else {
            // View é mais alta que o frame da câmera (padrão em smartphones modernos) -> preenche altura e corta laterais
            scale = viewHeight / bitmapHeight
            offsetX = (viewWidth - bitmapWidth * scale) / 2f
            offsetY = 0f
        }

        detections.forEach { det ->
            val rect = det.boundingBox
            val screenLeft = rect.left * scale + offsetX
            val screenTop = rect.top * scale + offsetY
            val screenWidth = rect.width() * scale
            val screenHeight = rect.height() * scale

            // Desenha o retângulo verde vibrante
            drawRect(
                color = Color(0xFF00E676),
                topLeft = androidx.compose.ui.geometry.Offset(screenLeft, screenTop),
                size = androidx.compose.ui.geometry.Size(screenWidth, screenHeight),
                style = Stroke(width = 3.dp.toPx())
            )
        }
    }
}

fun tirarFoto(context: Context, capture: ImageCapture, id: String, onSucesso: (Uri) -> Unit) {
    val name = "${id}_${System.currentTimeMillis()}.jpg"
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

    capture.takePicture(outputOptions, ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                output.savedUri?.let { onSucesso(it) }
            }
            override fun onError(exc: ImageCaptureException) {
                Log.e("Camera", "Erro ao capturar: ${exc.message}")
            }
        }
    )
}
