package com.example.myapplication.userinterface.util

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.camera.core.*
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.tensorflow.lite.task.vision.detector.Detection
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

// Executor dedicado — inferência nunca roda na main thread
private val inferenceExecutor = Executors.newSingleThreadExecutor()

fun criarImageCaptureCacau(): ImageCapture {
    val captureResSelector = ResolutionSelector.Builder()
        .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
        .setResolutionStrategy(
            ResolutionStrategy(
                Size(1600, 1200),
                ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
            )
        )
        .build()
    return ImageCapture.Builder()
        .setResolutionSelector(captureResSelector)
        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
        .setJpegQuality(88)
        .build()
}

@Composable
fun CameraWithAI(
    helper: ObjectDetectorHelper,
    capture: ImageCapture,
    lanternaAtiva: Boolean = false,
    zoomRatio: Float = 1f,
    onSuportaLanterna: (Boolean) -> Unit = {},
    onZoomMaxDisponivel: (Float) -> Unit = {},
    onZoomRatioChanged: (Float) -> Unit = {},
    onErroCamera: (String) -> Unit = {},
    onTapFocus: (Float, Float) -> Unit = { _, _ -> },
    onResultsUpdated: (ObjectDetectorHelper.ResultadoDeteccao) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val providerFuture = remember { ProcessCameraProvider.getInstance(context) }
    val mainExecutor = remember { ContextCompat.getMainExecutor(context) }
    val disposed = remember { AtomicBoolean(false) }
    val inferenceRunning = remember { AtomicBoolean(false) }
    val lastFinishedMs = remember { AtomicLong(0L) }
    var cameraRef by remember { mutableStateOf<Camera?>(null) }
    var zoomMinRef by remember { mutableFloatStateOf(1f) }
    var zoomMaxRef by remember { mutableFloatStateOf(4f) }

    LaunchedEffect(cameraRef, lanternaAtiva) {
        try {
            if (cameraRef?.cameraInfo?.hasFlashUnit() == true) {
                cameraRef?.cameraControl?.enableTorch(lanternaAtiva)
            }
        } catch (_: Exception) {}
    }

    LaunchedEffect(cameraRef, zoomRatio) {
        try {
            val zMin = cameraRef?.cameraInfo?.zoomState?.value?.minZoomRatio ?: zoomMinRef
            val zMax = cameraRef?.cameraInfo?.zoomState?.value?.maxZoomRatio ?: zoomMaxRef
            cameraRef?.cameraControl?.setZoomRatio(zoomRatio.coerceIn(zMin, zMax.coerceAtLeast(zMin)))
        } catch (_: Exception) {}
    }

    DisposableEffect(helper, lifecycleOwner) {
        disposed.set(false)
        inferenceRunning.set(false)
        lastFinishedMs.set(0L)
        helper.setListener(object : ObjectDetectorHelper.DetectorListener {
            override fun onResults(resultado: ObjectDetectorHelper.ResultadoDeteccao) {
                if (!disposed.get()) {
                    mainExecutor.execute {
                        if (!disposed.get()) {
                            onResultsUpdated(resultado)
                        }
                    }
                }
            }
            override fun onError(mensagem: String) {
                Log.e("CameraAI", "Detector: $mensagem")
            }
        })
        onDispose {
            disposed.set(true)
            try {
                cameraRef?.cameraControl?.enableTorch(false)
            } catch (_: Exception) {}
            cameraRef = null
            try {
                if (providerFuture.isDone) {
                    providerFuture.get().unbindAll()
                }
            } catch (_: Exception) {}
            helper.setListener(null)
        }
    }

    androidx.compose.ui.viewinterop.AndroidView(factory = { ctx ->
        val view = PreviewView(ctx).apply {
            keepScreenOn = true
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.O_MR1) {
                PreviewView.ImplementationMode.COMPATIBLE
            } else {
                PreviewView.ImplementationMode.PERFORMANCE
            }
        }

        var pinchInProgress = false
        val scaleDetector = ScaleGestureDetector(
            ctx,
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                    pinchInProgress = true
                    return true
                }

                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    val cam = cameraRef ?: return false
                    val zState = cam.cameraInfo.zoomState.value
                    val cur = zState?.zoomRatio ?: zoomRatio
                    val minZ = zState?.minZoomRatio ?: zoomMinRef
                    val maxZ = zState?.maxZoomRatio ?: zoomMaxRef
                    val novo = (cur * detector.scaleFactor).coerceIn(minZ, maxZ.coerceAtLeast(minZ))
                    try {
                        cam.cameraControl.setZoomRatio(novo)
                        onZoomRatioChanged(novo)
                    } catch (_: Exception) {}
                    return true
                }
            }
        )

        view.setOnTouchListener { v, event ->
            scaleDetector.onTouchEvent(event)
            if (event.pointerCount <= 1 && !scaleDetector.isInProgress && !pinchInProgress &&
                event.actionMasked == MotionEvent.ACTION_UP
            ) {
                try {
                    val point = view.meteringPointFactory.createPoint(event.x, event.y)
                    val action = FocusMeteringAction.Builder(point).build()
                    cameraRef?.cameraControl?.startFocusAndMetering(action)
                } catch (_: Exception) {}
                onTapFocus(event.x, event.y)
                v.performClick()
            }
            if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
                pinchInProgress = false
            }
            true
        }

        val minCooldownMs = 120L

        fun buildAnalyzer(targetW: Int, targetH: Int, lockAspect: Boolean): ImageAnalysis {
            val resBuilder = ResolutionSelector.Builder()
            if (lockAspect) {
                resBuilder.setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
            }
            val analysisResSelector = resBuilder
                .setResolutionStrategy(
                    ResolutionStrategy(
                        Size(targetW, targetH),
                        ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER
                    )
                )
                .build()

            return ImageAnalysis.Builder()
                .setResolutionSelector(analysisResSelector)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build().also { analysis ->
                    analysis.setAnalyzer(inferenceExecutor) { proxy ->
                        val now = System.currentTimeMillis()
                        if (disposed.get() || inferenceRunning.get() || (now - lastFinishedMs.get()) < minCooldownMs) {
                            proxy.close()
                            return@setAnalyzer
                        }

                        inferenceRunning.set(true)
                        var rawBitmap: Bitmap? = null
                        val tStart = System.currentTimeMillis()
                        try {
                            val rotation = proxy.imageInfo.rotationDegrees
                            rawBitmap = proxy.toBitmap()
                            proxy.close()

                            if (!disposed.get()) {
                                // Rotaciona e aplica Letterbox em passo único (sem bitmap intermediário)
                                helper.detect(rawBitmap, rotation)
                            }
                        } catch (t: Throwable) {
                            Log.e("CameraAI", "Erro no frame: ${t.message}")
                            try { proxy.close() } catch (_: Exception) {}
                        } finally {
                            if (rawBitmap != null && !rawBitmap.isRecycled) {
                                rawBitmap.recycle()
                            }
                            val elapsed = System.currentTimeMillis() - tStart
                            val adaptiveGap = (elapsed / 3L).coerceIn(0L, 140L)
                            lastFinishedMs.set(System.currentTimeMillis() + adaptiveGap)
                            inferenceRunning.set(false)
                        }
                    }
                }
        }

        providerFuture.addListener({
            if (disposed.get()) return@addListener
            try {
                val provider = providerFuture.get()
                val camSelector = when {
                    provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) -> CameraSelector.DEFAULT_BACK_CAMERA
                    provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) -> CameraSelector.DEFAULT_FRONT_CAMERA
                    else -> {
                        onErroCamera("Nenhuma câmera disponível neste dispositivo.")
                        return@addListener
                    }
                }

                val previewResSelector = ResolutionSelector.Builder()
                    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                    .build()

                val preview = Preview.Builder()
                    .setResolutionSelector(previewResSelector)
                    .build()
                    .also { it.setSurfaceProvider(view.surfaceProvider) }

                provider.unbindAll()
                val cam: Camera = try {
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        camSelector,
                        preview, capture, buildAnalyzer(640, 480, true)
                    )
                } catch (_: IllegalArgumentException) {
                    provider.unbindAll()
                    try {
                        provider.bindToLifecycle(
                            lifecycleOwner,
                            camSelector,
                            preview, capture, buildAnalyzer(480, 360, false)
                        )
                    } catch (_: Exception) {
                        provider.unbindAll()
                        val compatCam = provider.bindToLifecycle(lifecycleOwner, camSelector, preview, capture)
                        onErroCamera("Modo compatibilidade: a IA analisará a foto capturada em seguida.")
                        compatCam
                    }
                }

                cameraRef = cam
                val zState = cam.cameraInfo.zoomState.value
                zoomMinRef = zState?.minZoomRatio ?: 1f
                zoomMaxRef = zState?.maxZoomRatio ?: 4f
                onZoomMaxDisponivel(zoomMaxRef)
                val temLanterna = cam.cameraInfo.hasFlashUnit()
                onSuportaLanterna(temLanterna)
                if (lanternaAtiva && temLanterna) {
                    try { cam.cameraControl.enableTorch(true) } catch (_: Exception) {}
                }
                if (zoomRatio > 1f) {
                    try { cam.cameraControl.setZoomRatio(zoomRatio.coerceIn(zoomMinRef, zoomMaxRef)) } catch (_: Exception) {}
                }
            } catch (t: Throwable) {
                Log.e("CameraAI", "Falha ao abrir câmera: ${t.message}", t)
                onErroCamera("Não foi possível iniciar a câmera neste aparelho.")
            }
        }, mainExecutor)
        view
    }, modifier = Modifier.fillMaxSize())
}

/**
 * Renderiza os bounding boxes sobre a PreviewView respeitando a geometria FILL_CENTER do CameraX.
 */
@Composable
fun BoundingBoxOverlay(
    detections: List<Detection>,
    bitmapWidth: Float,
    bitmapHeight: Float,
    modifier: Modifier = Modifier
) {
    val badgePaint = remember {
        android.graphics.Paint().apply { isAntiAlias = true }
    }
    val textPaint = remember {
        android.graphics.Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = 26f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            isAntiAlias = true
        }
    }

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
            scale = viewWidth / bitmapWidth
            offsetX = 0f
            offsetY = (viewHeight - bitmapHeight * scale) / 2f
        } else {
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

            val cat = det.categories.firstOrNull()
            val rawLabel = cat?.label
            val estado = com.example.myapplication.userinterface.mapearLabelParaEstadoFruto(rawLabel)
            val boxColor = estado.cor

            drawRect(
                color = boxColor,
                topLeft = androidx.compose.ui.geometry.Offset(screenLeft, screenTop),
                size = androidx.compose.ui.geometry.Size(screenWidth, screenHeight),
                style = Stroke(width = 3.dp.toPx())
            )

            val curto = com.example.myapplication.userinterface.rotuloCurtoDoenca(estado, rawLabel ?: estado.rotuloCurto)
            val confPct = ((cat?.score ?: 0f) * 100f).toInt()
            val badgeText = if (confPct > 0) "$curto $confPct%" else curto
            val textW = textPaint.measureText(badgeText)
            val badgeH = 32f
            val badgeTop = (screenTop - badgeH - 2f).coerceAtLeast(0f)
            badgePaint.color = android.graphics.Color.argb(
                220,
                (boxColor.red * 255).toInt(),
                (boxColor.green * 255).toInt(),
                (boxColor.blue * 255).toInt()
            )
            drawContext.canvas.nativeCanvas.drawRoundRect(
                screenLeft,
                badgeTop,
                screenLeft + textW + 14f,
                badgeTop + badgeH,
                6f,
                6f,
                badgePaint
            )
            drawContext.canvas.nativeCanvas.drawText(
                badgeText,
                screenLeft + 7f,
                badgeTop + badgeH - 8f,
                textPaint
            )
        }
    }
}

private fun salvarFotoEmArquivoPrivado(
    context: Context,
    capture: ImageCapture,
    id: String,
    onErro: (String) -> Unit,
    onSucesso: (Uri) -> Unit
) {
    try {
        val dirBase = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: context.filesDir
        val dirFotos = File(dirBase, "CEPEC_Analise").apply { mkdirs() }
        val file = File(dirFotos, "${id}_${System.currentTimeMillis()}.jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(file).build()
        capture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val uri = output.savedUri ?: Uri.fromFile(file)
                    onSucesso(uri)
                }

                override fun onError(exc: ImageCaptureException) {
                    Log.e("Camera", "Erro ao capturar no armazenamento privado: ${exc.message}")
                    onErro(exc.message ?: "Falha ao capturar foto.")
                }
            }
        )
    } catch (t: Throwable) {
        onErro(t.message ?: "Falha ao salvar foto.")
    }
}

fun tirarFoto(
    context: Context,
    capture: ImageCapture,
    id: String,
    onErro: (String) -> Unit = {},
    onSucesso: (Uri) -> Unit
) {
    val podeUsarMediaStore = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
        PackageManager.PERMISSION_GRANTED
    if (!podeUsarMediaStore) {
        salvarFotoEmArquivoPrivado(context, capture, id, onErro, onSucesso)
        return
    }

    try {
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

        capture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val uri = output.savedUri
                    if (uri != null) {
                        onSucesso(uri)
                    } else {
                        salvarFotoEmArquivoPrivado(context, capture, id, onErro, onSucesso)
                    }
                }

                override fun onError(exc: ImageCaptureException) {
                    Log.w("Camera", "MediaStore falhou (${exc.message}), usando armazenamento privado")
                    salvarFotoEmArquivoPrivado(context, capture, id, onErro, onSucesso)
                }
            }
        )
    } catch (t: Throwable) {
        Log.w("Camera", "Exceção MediaStore (${t.message}), usando armazenamento privado")
        salvarFotoEmArquivoPrivado(context, capture, id, onErro, onSucesso)
    }
}
