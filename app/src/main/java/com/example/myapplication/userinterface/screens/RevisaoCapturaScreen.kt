package com.example.myapplication.userinterface.screens

import android.graphics.Bitmap
import android.graphics.RectF
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.myapplication.data.DetectionResult
import com.example.myapplication.data.ModelConfig
import com.example.myapplication.userinterface.util.ObjectDetectorHelper
import com.example.myapplication.userinterface.util.carregarBitmapDeUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Tela de Revisão Científica da Captura:
 * - Executa a inferência na foto em resolução total;
 * - Permite ao pesquisador auditar a contagem tocando em caixas para removê-las (falsos positivos)
 *   ou tocando em áreas vazias para adicionar novos frutos (falsos negativos);
 * - Salva contagem_ia e contagem_final separadamente para rastreabilidade experimental.
 */
@Composable
fun RevisaoCapturaScreen(
    fotoUri: Uri,
    plantId: String,
    modelConfig: ModelConfig,
    detectorHelper: ObjectDetectorHelper?,
    onConfirmar: (contagemIa: Int, contagemFinal: Int) -> Unit,
    onRefazer: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    BackHandler {
        onRefazer()
    }

    var carregandoInferencia by remember { mutableStateOf(true) }
    var bitmapFoto by remember { mutableStateOf<Bitmap?>(null) }
    val caixasDetectadas = remember { mutableStateListOf<RectF>() }
    var contagemIaOriginal by remember { mutableIntStateOf(0) }

    // Dimensões do canvas para mapeamento de toque
    var canvasWidth by remember { mutableFloatStateOf(1f) }
    var canvasHeight by remember { mutableFloatStateOf(1f) }

    LaunchedEffect(fotoUri) {
        carregandoInferencia = true
        val (bmp, filtradas) = withContext(Dispatchers.Default) {
            val bitmap = carregarBitmapDeUri(context, fotoUri)
            val caixas = if (bitmap != null && detectorHelper != null) {
                detectorHelper.detectar(bitmap)
                    .filter { det ->
                        val score = det.categories.firstOrNull()?.score ?: 0f
                        score >= (modelConfig.threshold - 0.02f)
                    }
                    .map { it.boundingBox }
            } else emptyList()
            bitmap to caixas
        }
        bitmapFoto = bmp
        caixasDetectadas.clear()
        caixasDetectadas.addAll(filtradas)
        contagemIaOriginal = filtradas.size
        carregandoInferencia = false
    }

    Box(Modifier.fillMaxSize()) {
        if (bitmapFoto != null) {
            AsyncImage(
                model = fotoUri,
                contentDescription = "Foto capturada de $plantId",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )

            // Canvas interativo para desenhar e editar caixas delimitadoras
            Canvas(
                Modifier
                    .fillMaxSize()
                    .pointerInput(caixasDetectadas, bitmapFoto) {
                        detectTapGestures { tapOffset ->
                            val bmp = bitmapFoto ?: return@detectTapGestures
                            val imgW = bmp.width.toFloat()
                            val imgH = bmp.height.toFloat()

                            // Mapeamento ContentScale.Fit -> coordenadas do bitmap
                            val scale = minOf(size.width / imgW, size.height / imgH)
                            val offsetX = (size.width - imgW * scale) / 2f
                            val offsetY = (size.height - imgH * scale) / 2f

                            val xImg = (tapOffset.x - offsetX) / scale
                            val yImg = (tapOffset.y - offsetY) / scale

                            if (xImg in 0f..imgW && yImg in 0f..imgH) {
                                // Verifica se o toque atingiu uma caixa existente para removê-la
                                val caixaExistenteIdx = caixasDetectadas.indexOfLast { rect ->
                                    rect.contains(xImg, yImg)
                                }

                                if (caixaExistenteIdx >= 0) {
                                    caixasDetectadas.removeAt(caixaExistenteIdx)
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                } else {
                                    // Adiciona nova caixa manual de tamanho padrão no local do toque
                                    val raioW = imgW * 0.08f
                                    val raioH = imgH * 0.08f
                                    val novaCaixa = RectF(
                                        (xImg - raioW).coerceAtLeast(0f),
                                        (yImg - raioH).coerceAtLeast(0f),
                                        (xImg + raioW).coerceAtMost(imgW),
                                        (yImg + raioH).coerceAtMost(imgH)
                                    )
                                    caixasDetectadas.add(novaCaixa)
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                            }
                        }
                    }
            ) {
                canvasWidth = size.width
                canvasHeight = size.height
                val bmp = bitmapFoto ?: return@Canvas
                val imgW = bmp.width.toFloat()
                val imgH = bmp.height.toFloat()

                val scale = minOf(size.width / imgW, size.height / imgH)
                val offsetX = (size.width - imgW * scale) / 2f
                val offsetY = (size.height - imgH * scale) / 2f

                caixasDetectadas.forEach { rect ->
                    val x = rect.left * scale + offsetX
                    val y = rect.top * scale + offsetY
                    val w = rect.width() * scale
                    val h = rect.height() * scale

                    drawRect(
                        color = Color.Green,
                        topLeft = Offset(x, y),
                        size = Size(w, h),
                        style = Stroke(width = 3.dp.toPx())
                    )
                }
            }
        }

        // Header Superior: Rastreabilidade de Contagens
        Surface(
            Modifier
                .statusBarsPadding()
                .align(Alignment.TopCenter)
                .padding(16.dp),
            color = Color.Black.copy(alpha = 0.8f),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Revisão Científica: $plantId",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("IA: $contagemIaOriginal", color = Color(0xFFA5D6A7), fontSize = 13.sp)
                    Text("Auditado: ${caixasDetectadas.size}", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "Toque na caixa para remover • Toque livre para adicionar",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 11.sp
                )
            }
        }

        if (carregandoInferencia) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = Color.Green
            )
        }

        // Barra Inferior de Ação: Refazer vs Confirmar
        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onRefazer,
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
            ) {
                Icon(Icons.Default.Refresh, null)
                Spacer(Modifier.width(6.dp))
                Text("REFAZER FOTO", fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = {
                    onConfirmar(contagemIaOriginal, caixasDetectadas.size)
                },
                modifier = Modifier
                    .weight(1.4f)
                    .height(56.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
            ) {
                Icon(Icons.Default.Check, null)
                Spacer(Modifier.width(6.dp))
                Text("CONFIRMAR (${caixasDetectadas.size})", fontWeight = FontWeight.Bold)
            }
        }
    }
}
