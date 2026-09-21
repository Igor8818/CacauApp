package com.example.myapplication.userinterface.util

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import android.util.Size
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
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

// Executor dedicado — inferencia nunca roda na main thread
private val inferenceExecutor = Executors.newSingleThreadExecutor()

@Composable
fun CameraWithAI(
    helper: ObjectDetectorHelper,
    capture: ImageCapture,
    onResultsUpdated: (List<DetectionResult>) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val providerFuture = remember { ProcessCameraProvider.getInstance(context) }

    androidx.compose.ui.viewinterop.AndroidView(factory = { ctx ->
        val view = PreviewView(ctx)

        // Throttle: evita enfileirar inferencias quando o modelo eh lento
        val inferenceRunning = AtomicBoolean(false)
        val lastInferenceMs = AtomicLong(0L)
        val minIntervalMs = 1000L // 1 inferencia/segundo para modelos pesados

        providerFuture.addListener({
            val provider = providerFuture.get()
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(view.surfaceProvider) }

            val analyzer = ImageAnalysis.Builder()
                .setTargetResolution(Size(640, 480))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
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
                            val bitmap = proxy.toBitmap()
                            proxy.close()

                            helper.setListener(object : ObjectDetectorHelper.DetectorListener {
                                override fun onResults(results: List<DetectionResult>) {
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