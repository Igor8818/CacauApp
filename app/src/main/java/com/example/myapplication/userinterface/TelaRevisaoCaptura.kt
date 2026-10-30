package com.example.myapplication.userinterface

import android.content.Context
import android.graphics.BitmapFactory
import android.media.ExifInterface
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import org.tensorflow.lite.task.vision.detector.Detection

/**
 * Classificação individual de cada fruto detectado pelo modelo ou marcado pelo usuário.
 */
enum class EstadoFruto(
    val rotulo: String,
    val rotuloCurto: String,
    val cor: Color
) {
    SADIO("Sadio", "Sadio", Color(0xFF00E676)),                  // Verde vibrante
    VASSOURA("Vassoura de Bruxa", "Vassoura", Color(0xFFFF9100)), // Laranja
    PODRIDAO("Podridão", "Podridão", Color(0xFFFF1744)),          // Vermelho
    NAO_AVALIAVEL("Não Avaliável", "Não Aval.", Color(0xFF90A4AE)) // Cinza azulado
}

/**
 * Caixa de detecção auditável com coordenadas normalizadas (0.0 a 1.0)
 * para perfeita fidelidade em qualquer tamanho de foto e tela.
 */
data class CaixaAuditavel(
    val normLeft: Float,
    val normTop: Float,
    val normRight: Float,
    val normBottom: Float,
    var estado: EstadoFruto = EstadoFruto.SADIO,
    val manual: Boolean = false,
    val confianca: Float = 0.90f
)

/**
 * Tela de Revisão Pós-Captura / Pós-Processamento:
 * - Exibe a foto capturada com as caixas de detecção da IA sobrepostas;
 * - O usuário pode TOCAR em qualquer quadrado verde para classificá-lo (Sadio, Vassoura, Podridão, Não Avaliável);
 * - O usuário pode TOCAR em frutos não detectados para adicionar caixas manualmente;
 * - Botão de lixeira para excluir falsos positivos;
 * - Resumo consolidado para exportação na planilha CSV e envio futuro ao backend Spring (CacauSync).
 */
@Composable
fun TelaRevisaoCaptura(
    fotoUri: Uri,
    plantId: String,
    deteccoesIniciais: List<Detection>,
    bitmapWidthRef: Float,
    bitmapHeightRef: Float,
    onConfirmar: (
        diagnostico: String,
        total: Int,
        sadios: Int,
        vassoura: Int,
        podridao: Int,
        naoAvaliavel: Int,
        caixas: List<CaixaAuditavel>
    ) -> Unit,
    onRefazer: () -> Unit
) {
    val context = LocalContext.current

    // Caixas de detecção normalizadas
    val caixas = remember(fotoUri) {
        mutableStateListOf<CaixaAuditavel>().apply {
            val wRef = bitmapWidthRef.coerceAtLeast(1f)
            val hRef = bitmapHeightRef.coerceAtLeast(1f)
            deteccoesIniciais.forEach { det ->
                val rect = det.boundingBox
                val score = det.categories.firstOrNull()?.score ?: 0.85f
                add(
                    CaixaAuditavel(
                        normLeft = (rect.left / wRef).coerceIn(0f, 1f),
                        normTop = (rect.top / hRef).coerceIn(0f, 1f),
                        normRight = (rect.right / wRef).coerceIn(0f, 1f),
                        normBottom = (rect.bottom / hRef).coerceIn(0f, 1f),
                        estado = EstadoFruto.SADIO,
                        manual = false,
                        confianca = score
                    )
                )
            }
        }
    }

    var indiceSelecionado by remember { mutableIntStateOf(-1) }
    var plantaNaoColetavel by remember { mutableStateOf(false) }

    // Proporção real da imagem considerando rotação EXIF
    val imgAspect = remember(fotoUri) {
        try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(fotoUri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }
            val exif = context.contentResolver.openInputStream(fotoUri)?.use { stream ->
                ExifInterface(stream)
            }
            val orientation = exif?.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            val rotated = orientation == ExifInterface.ORIENTATION_ROTATE_90 || orientation == ExifInterface.ORIENTATION_ROTATE_270
            val w = if (rotated) options.outHeight else options.outWidth
            val h = if (rotated) options.outWidth else options.outHeight
            if (w > 0 && h > 0) w.toFloat() / h.toFloat() else (3f / 4f)
        } catch (e: Exception) {
            3f / 4f
        }
    }

    // Contagens dinâmicas
    val qtdSadios = caixas.count { it.estado == EstadoFruto.SADIO }
    val qtdVassoura = caixas.count { it.estado == EstadoFruto.VASSOURA }
    val qtdPodridao = caixas.count { it.estado == EstadoFruto.PODRIDAO }
    val qtdNaoAvaliavel = caixas.count { it.estado == EstadoFruto.NAO_AVALIAVEL }
    val totalFrutos = caixas.size

    val textPaint = remember {
        android.graphics.Paint().apply {
            isAntiAlias = true
            textSize = 28f
            color = android.graphics.Color.WHITE
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
    }
    val badgePaint = remember {
        android.graphics.Paint().apply {
            isAntiAlias = true
            style = android.graphics.Paint.Style.FILL
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Imagem capturada ajustada
        AsyncImage(
            model = fotoUri,
            contentDescription = "Foto capturada da planta $plantId",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit
        )

        // Overlay interativo de caixas
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val canvasW = constraints.maxWidth.toFloat()
            val canvasH = constraints.maxHeight.toFloat()
            val containerAspect = canvasW / canvasH.coerceAtLeast(1f)

            val displayedW: Float
            val displayedH: Float
            if (containerAspect > imgAspect) {
                displayedH = canvasH
                displayedW = displayedH * imgAspect
            } else {
                displayedW = canvasW
                displayedH = displayedW / imgAspect
            }
            val offsetX = (canvasW - displayedW) / 2f
            val offsetY = (canvasH - displayedH) / 2f

            if (!plantaNaoColetavel) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(fotoUri, caixas.size) {
                            detectTapGestures { tapOffset ->
                                val normTapX = (tapOffset.x - offsetX) / displayedW
                                val normTapY = (tapOffset.y - offsetY) / displayedH

                                if (normTapX in 0f..1f && normTapY in 0f..1f) {
                                    val margemNormX = 24.dp.toPx() / displayedW
                                    val margemNormY = 24.dp.toPx() / displayedH

                                    // Busca se clicou em uma caixa existente
                                    val idxTocado = caixas.indexOfLast { c ->
                                        normTapX in (c.normLeft - margemNormX)..(c.normRight + margemNormX) &&
                                                normTapY in (c.normTop - margemNormY)..(c.normBottom + margemNormY)
                                    }

                                    if (idxTocado >= 0) {
                                        indiceSelecionado = idxTocado
                                    } else {
                                        if (indiceSelecionado >= 0) {
                                            indiceSelecionado = -1
                                        } else {
                                            // Tocou em área vazia da foto: adiciona nova caixa manual
                                            val halfW = 0.08f
                                            val halfH = 0.08f
                                            val nova = CaixaAuditavel(
                                                normLeft = (normTapX - halfW).coerceIn(0f, 1f),
                                                normTop = (normTapY - halfH).coerceIn(0f, 1f),
                                                normRight = (normTapX + halfW).coerceIn(0f, 1f),
                                                normBottom = (normTapY + halfH).coerceIn(0f, 1f),
                                                estado = EstadoFruto.SADIO,
                                                manual = true,
                                                confianca = 1.0f
                                            )
                                            caixas.add(nova)
                                            indiceSelecionado = caixas.lastIndex
                                        }
                                    }
                                }
                            }
                        }
                ) {
                    caixas.forEachIndexed { idx, caixa ->
                        val bLeft = offsetX + caixa.normLeft * displayedW
                        val bTop = offsetY + caixa.normTop * displayedH
                        val bW = (caixa.normRight - caixa.normLeft) * displayedW
                        val bH = (caixa.normBottom - caixa.normTop) * displayedH
                        val selecionada = idx == indiceSelecionado

                        // Retângulo do fruto
                        drawRect(
                            color = caixa.estado.cor,
                            topLeft = androidx.compose.ui.geometry.Offset(bLeft, bTop),
                            size = androidx.compose.ui.geometry.Size(bW, bH),
                            style = Stroke(width = if (selecionada) 5.dp.toPx() else 3.dp.toPx())
                        )

                        // Destaque externo branco se selecionada
                        if (selecionada) {
                            drawRect(
                                color = Color.White,
                                topLeft = androidx.compose.ui.geometry.Offset(bLeft - 2.dp.toPx(), bTop - 2.dp.toPx()),
                                size = androidx.compose.ui.geometry.Size(bW + 4.dp.toPx(), bH + 4.dp.toPx()),
                                style = Stroke(width = 2.dp.toPx())
                            )
                        }

                        // Badge com a classificação sobre a caixa
                        val label = "#${idx + 1} ${caixa.estado.rotuloCurto}"
                        val textWidth = textPaint.measureText(label)
                        val badgeHeight = 32f

                        badgePaint.color = caixa.estado.cor.toArgb()
                        drawContext.canvas.nativeCanvas.drawRoundRect(
                            bLeft,
                            (bTop - badgeHeight - 4f).coerceAtLeast(0f),
                            bLeft + textWidth + 16f,
                            bTop,
                            8f, 8f,
                            badgePaint
                        )

                        drawContext.canvas.nativeCanvas.drawText(
                            label,
                            bLeft + 8f,
                            (bTop - 8f).coerceAtLeast(20f),
                            textPaint
                        )
                    }
                }
            }
        }

        // Cabeçalho superior: ID e Contagem Rápida
        Surface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(16.dp),
            color = Color.Black.copy(alpha = 0.75f),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(plantId, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("Toque em um quadrado para classificar", color = Color.LightGray, fontSize = 12.sp)
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF2E7D32)
                ) {
                    Text(
                        if (plantaNaoColetavel) "NÃO COLETÁVEL" else "$totalFrutos Frutos",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }

        // Painel Flutuante quando uma caixa de fruto está selecionada
        if (indiceSelecionado in caixas.indices && !plantaNaoColetavel) {
            val caixaAtual = caixas[indiceSelecionado]
            Card(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 24.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF212121).copy(alpha = 0.95f)),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Classificar Fruto #${indiceSelecionado + 1}",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        IconButton(onClick = { indiceSelecionado = -1 }) {
                            Icon(Icons.Default.Close, null, tint = Color.LightGray)
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    // Botões de Classificação do Fruto
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Sadio
                        Button(
                            onClick = {
                                caixaAtual.estado = EstadoFruto.SADIO
                                indiceSelecionado = -1
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (caixaAtual.estado == EstadoFruto.SADIO) Color(0xFF00E676) else Color(0xFF333333)
                            ),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                        ) {
                            Text("Sadio", fontSize = 12.sp, color = if (caixaAtual.estado == EstadoFruto.SADIO) Color.Black else Color.White, fontWeight = FontWeight.Bold)
                        }

                        // Vassoura
                        Button(
                            onClick = {
                                caixaAtual.estado = EstadoFruto.VASSOURA
                                indiceSelecionado = -1
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (caixaAtual.estado == EstadoFruto.VASSOURA) Color(0xFFFF9100) else Color(0xFF333333)
                            ),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                        ) {
                            Text("Vassoura", fontSize = 11.sp, color = if (caixaAtual.estado == EstadoFruto.VASSOURA) Color.Black else Color.White, fontWeight = FontWeight.Bold)
                        }

                        // Podridão
                        Button(
                            onClick = {
                                caixaAtual.estado = EstadoFruto.PODRIDAO
                                indiceSelecionado = -1
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (caixaAtual.estado == EstadoFruto.PODRIDAO) Color(0xFFFF1744) else Color(0xFF333333)
                            ),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                        ) {
                            Text("Podridão", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Não Avaliável
                        OutlinedButton(
                            onClick = {
                                caixaAtual.estado = EstadoFruto.NAO_AVALIAVEL
                                indiceSelecionado = -1
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFB0BEC5))
                        ) {
                            Text("Não Avaliável", fontSize = 11.sp)
                        }

                        // Excluir caixa
                        OutlinedButton(
                            onClick = {
                                caixas.removeAt(indiceSelecionado)
                                indiceSelecionado = -1
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Delete, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Excluir", fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        // Painel Inferior: Resumo das Classes e Ações
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            color = Color(0xFF1E1E1E).copy(alpha = 0.95f),
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Barra de Badges de Totais por Categoria
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    BadgeCategoria(rotulo = "Sadios", total = qtdSadios, cor = Color(0xFF00E676))
                    BadgeCategoria(rotulo = "Vassoura", total = qtdVassoura, cor = Color(0xFFFF9100))
                    BadgeCategoria(rotulo = "Podridão", total = qtdPodridao, cor = Color(0xFFFF1744))
                    BadgeCategoria(rotulo = "Não Aval.", total = qtdNaoAvaliavel, cor = Color(0xFF90A4AE))
                }

                Spacer(Modifier.height(12.dp))

                // Toggle para marcar planta inteira como Não Coletável
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Checkbox(
                        checked = plantaNaoColetavel,
                        onCheckedChange = { plantaNaoColetavel = it },
                        colors = CheckboxDefaults.colors(checkedColor = Color.Red)
                    )
                    Text("Marcar Planta como Não Coletável / Ausente", color = Color.White, fontSize = 13.sp)
                }

                Spacer(Modifier.height(10.dp))

                // Botões de Ação
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Refazer Foto
                    OutlinedButton(
                        onClick = onRefazer,
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Refresh, null)
                        Spacer(Modifier.width(6.dp))
                        Text("REFAZER")
                    }

                    // Confirmar e Salvar
                    Button(
                        onClick = {
                            if (plantaNaoColetavel) {
                                onConfirmar("Não Coletável", 0, 0, 0, 0, 0, emptyList())
                            } else {
                                // "Resultado" fica SOMENTE numérico: o CacauSync extrai os dígitos desse campo.
                                // O detalhamento por classe vai em colunas próprias no fim do CSV.
                                onConfirmar(
                                    totalFrutos.toString(),
                                    totalFrutos,
                                    qtdSadios,
                                    qtdVassoura,
                                    qtdPodridao,
                                    qtdNaoAvaliavel,
                                    caixas.toList()
                                )
                            }
                        },
                        modifier = Modifier
                            .weight(1.4f)
                            .height(52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.CheckCircle, null)
                        Spacer(Modifier.width(6.dp))
                        Text("CONFIRMAR COLETA", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun BadgeCategoria(rotulo: String, total: Int, cor: Color) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = cor.copy(alpha = 0.18f),
        border = androidx.compose.foundation.BorderStroke(1.dp, cor.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("$total", color = cor, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
            Text(rotulo, color = Color.White, fontSize = 10.sp)
        }
    }
}
