package com.example.myapplication.userinterface

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.myapplication.userinterface.util.ObjectDetectorHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    val estado: EstadoFruto = EstadoFruto.SADIO,
    val manual: Boolean = false,
    val confianca: Float = 0.90f
) {
    val centroX: Float get() = (normLeft + normRight) / 2f
    val centroY: Float get() = (normTop + normBottom) / 2f
    val largura: Float get() = (normRight - normLeft).coerceAtLeast(0f)
    val altura: Float get() = (normBottom - normTop).coerceAtLeast(0f)
}

/**
 * Seleciona o índice da caixa tocada na foto.
 * Em cachos densos / frutos agrupados no tronco (almofadas florais), prioriza
 * as caixas que realmente contêm o toque e desempata pela menor distância ao centro.
 */
internal fun selecionarIndiceCaixaNoToque(
    caixas: List<CaixaAuditavel>,
    normTapX: Float,
    normTapY: Float,
    margemNormX: Float = 0.03f,
    margemNormY: Float = 0.03f
): Int {
    if (caixas.isEmpty()) return -1

    var melhorIndice = -1
    var melhorDistancia = Float.MAX_VALUE
    var melhorEstritamenteDentro = false

    for (i in caixas.indices) {
        val c = caixas[i]
        val dentroEstrito = normTapX in c.normLeft..c.normRight && normTapY in c.normTop..c.normBottom
        val dentroComMargem = normTapX in (c.normLeft - margemNormX)..(c.normRight + margemNormX) &&
                normTapY in (c.normTop - margemNormY)..(c.normBottom + margemNormY)

        if (dentroComMargem) {
            val dx = normTapX - c.centroX
            val dy = normTapY - c.centroY
            val distSq = dx * dx + dy * dy

            if (dentroEstrito && !melhorEstritamenteDentro) {
                melhorEstritamenteDentro = true
                melhorDistancia = distSq
                melhorIndice = i
            } else if (dentroEstrito == melhorEstritamenteDentro) {
                if (distSq < melhorDistancia) {
                    melhorDistancia = distSq
                    melhorIndice = i
                }
            }
        }
    }

    return melhorIndice
}

/**
 * Redimensiona a caixa proporcionalmente a partir do centro (para ajustar frutos pequenos ou grandes).
 */
internal fun redimensionarCaixaAuditavel(
    caixa: CaixaAuditavel,
    fatorEscala: Float,
    minDim: Float = 0.03f,
    maxDim: Float = 0.70f
): CaixaAuditavel {
    val cx = caixa.centroX
    val cy = caixa.centroY
    val novaLargura = (caixa.largura * fatorEscala).coerceIn(minDim, maxDim)
    val novaAltura = (caixa.altura * fatorEscala).coerceIn(minDim, maxDim)

    val halfW = novaLargura / 2f
    val halfH = novaAltura / 2f

    val nL = (cx - halfW).coerceIn(0f, 1f - novaLargura)
    val nT = (cy - halfH).coerceIn(0f, 1f - novaAltura)
    val nR = (nL + novaLargura).coerceIn(0f, 1f)
    val nB = (nT + novaAltura).coerceIn(0f, 1f)

    return caixa.copy(
        normLeft = nL,
        normTop = nT,
        normRight = nR,
        normBottom = nB
    )
}

/**
 * Modelo desacoplado para representar detecção candidata a Coordinate Snapping na foto estática.
 */
data class DetEstilizada(
    val normLeft: Float,
    val normTop: Float,
    val normRight: Float,
    val normBottom: Float,
    val confianca: Float,
    val estado: EstadoFruto = EstadoFruto.SADIO
)

/**
 * Realiza a fusão com "Coordinate Snapping":
 * - Se a detecção da foto estática casar (IoU > 0.20f) com uma caixa automática existente,
 *   atualiza suas coordenadas para o ponto 100% cravado da foto estática (eliminando desalinhamento de movimento);
 * - Preserva a classificação do usuário caso ele já tenha marcado Vassoura/Podridão/etc;
 * - Respeita caixas manuais e caixas excluídas;
 * - Adiciona frutos novos detectados na foto estática que não haviam sido capturados no preview.
 */
internal fun fundirDeteccoesComSnapping(
    caixasAtuais: List<CaixaAuditavel>,
    deteccoesEstaticas: List<DetEstilizada>,
    caixasExcluidas: List<CaixaAuditavel>
): List<CaixaAuditavel> {
    if (deteccoesEstaticas.isEmpty()) return caixasAtuais

    val resultado = caixasAtuais.toMutableList()
    val indicesPareados = mutableSetOf<Int>()

    for (det in deteccoesEstaticas) {
        // Ignora se colidir com algo que o usuário excluiu manualmente
        val foiExcluida = caixasExcluidas.any { ex ->
            iouNorm(ex, det.normLeft, det.normTop, det.normRight, det.normBottom) > 0.35f
        }
        if (foiExcluida) continue

        // Procura a caixa automática com maior sobreposição (IoU > 0.20f)
        var melhorIdx = -1
        var melhorIou = 0.20f

        for (i in resultado.indices) {
            if (i in indicesPareados) continue
            val c = resultado[i]
            if (c.manual) continue // caixas manuais são intocáveis pelo modelo

            val iou = iouNorm(c, det.normLeft, det.normTop, det.normRight, det.normBottom)
            if (iou > melhorIou) {
                melhorIou = iou
                melhorIdx = i
            }
        }

        if (melhorIdx >= 0) {
            indicesPareados.add(melhorIdx)
            val existente = resultado[melhorIdx]
            // SNAP: atualiza para a coordenada exata da foto estática preservando o estado selecionado pelo usuário
            resultado[melhorIdx] = existente.copy(
                normLeft = det.normLeft,
                normTop = det.normTop,
                normRight = det.normRight,
                normBottom = det.normBottom,
                confianca = maxOf(existente.confianca, det.confianca)
            )
        } else {
            // Fruto novo detectado na foto estática
            resultado.add(
                CaixaAuditavel(
                    normLeft = det.normLeft,
                    normTop = det.normTop,
                    normRight = det.normRight,
                    normBottom = det.normBottom,
                    estado = det.estado,
                    manual = false,
                    confianca = det.confianca
                )
            )
        }
    }

    return resultado
}

/**
 * Tela de Revisão Pós-Captura / Pós-Processamento:
 * - Exibe a foto capturada com as caixas de detecção da IA sobrepostas;
 * - Executa inferência em background sobre a foto estática para garantir detecções em qualquer condição de tremor da mão;
 * - Topo compacto e elevado para a barra de status (libera visão superior da árvore);
 * - Base compacta e rebaixada para a barra de navegação (libera visão inferior);
 * - Painel de classificação ancorado logo acima dos botões inferiores (nunca no meio da tela cobrindo os frutos);
 * - O usuário pode TOCAR em qualquer quadrado verde para classificá-lo (Sadio, Vassoura, Podridão, Não Avaliável);
 * - O usuário pode TOCAR em frutos não detectados para adicionar caixas manualmente;
 * - Resumo consolidado para exportação na planilha CSV e envio ao backend CacauSync.
 */
@Composable
fun TelaRevisaoCaptura(
    fotoUri: Uri,
    plantId: String,
    deteccoesIniciais: List<Detection>,
    bitmapWidthRef: Float,
    bitmapHeightRef: Float,
    detectorHelper: ObjectDetectorHelper? = null,
    labelsFiltro: List<String> = listOf("cacau", "fruto"),
    caixasAuditadasIniciais: List<CaixaAuditavel>? = null,
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
            if (!caixasAuditadasIniciais.isNullOrEmpty()) {
                addAll(caixasAuditadasIniciais)
            } else {
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
    }

    var indiceSelecionado by remember { mutableIntStateOf(-1) }
    var plantaNaoColetavel by remember { mutableStateOf(false) }
    val caixasExcluidasManualmente = remember(fotoUri) { mutableListOf<CaixaAuditavel>() }

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
            val rotated = orientation == ExifInterface.ORIENTATION_ROTATE_90 ||
                    orientation == ExifInterface.ORIENTATION_ROTATE_270 ||
                    orientation == ExifInterface.ORIENTATION_TRANSPOSE ||
                    orientation == ExifInterface.ORIENTATION_TRANSVERSE
            val w = if (rotated) options.outHeight else options.outWidth
            val h = if (rotated) options.outWidth else options.outHeight
            if (w > 0 && h > 0) w.toFloat() / h.toFloat() else (3f / 4f)
        } catch (e: Exception) {
            3f / 4f
        }
    }

    // Re-inferência em background na foto estática capturada com Coordinate Snapping:
    // Faz o alinhamento exato (snap) dos quadradinhos sobre os frutos da foto estática,
    // eliminando o atraso de movimento da mão ao disparar o obturador da câmera.
    LaunchedEffect(fotoUri) {
        val helper = detectorHelper ?: return@LaunchedEffect
        withContext(Dispatchers.IO) {
            val bmp = carregarBitmapFoto(context, fotoUri, maxDim = 1280) ?: return@withContext
            try {
                val res = helper.detectarSincrono(bmp) ?: return@withContext
                val bmpW = bmp.width.toFloat()
                val bmpH = bmp.height.toFloat()

                val candidatasEstaticas = mutableListOf<DetEstilizada>()
                res.deteccoes.forEach { det ->
                    val cat = det.categories.firstOrNull()
                    val label = (cat?.label ?: "cacau").lowercase()
                    val labelValido = labelsFiltro.isEmpty() ||
                            labelsFiltro.any { f -> label.contains(f.lowercase()) } ||
                            label == "cacau" || label == "fruto"
                    if (!labelValido) return@forEach

                    val r = det.boundingBox
                    val nL = (r.left / bmpW).coerceIn(0f, 1f)
                    val nT = (r.top / bmpH).coerceIn(0f, 1f)
                    val nR = (r.right / bmpW).coerceIn(0f, 1f)
                    val nB = (r.bottom / bmpH).coerceIn(0f, 1f)

                    candidatasEstaticas.add(
                        DetEstilizada(
                            normLeft = nL,
                            normTop = nT,
                            normRight = nR,
                            normBottom = nB,
                            confianca = cat?.score ?: 0.85f,
                            estado = EstadoFruto.SADIO
                        )
                    )
                }

                withContext(Dispatchers.Main) {
                    val fundidas = fundirDeteccoesComSnapping(
                        caixasAtuais = caixas.toList(),
                        deteccoesEstaticas = candidatasEstaticas,
                        caixasExcluidas = caixasExcluidasManualmente.toList()
                    )
                    caixas.clear()
                    caixas.addAll(fundidas)
                }
            } finally {
                bmp.recycle()
            }
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

                                    // Busca se clicou em uma caixa existente com prioridade ao centro mais próximo
                                    val idxTocado = selecionarIndiceCaixaNoToque(
                                        caixas = caixas,
                                        normTapX = normTapX,
                                        normTapY = normTapY,
                                        margemNormX = margemNormX,
                                        margemNormY = margemNormY
                                    )

                                    if (idxTocado >= 0) {
                                        indiceSelecionado = idxTocado
                                    } else {
                                        if (indiceSelecionado >= 0) {
                                            indiceSelecionado = -1
                                        } else {
                                            // Tocou em área vazia da foto: adiciona nova caixa manual
                                            val halfW = 0.07f
                                            val halfH = 0.07f
                                            val nova = CaixaAuditavel(
                                                normLeft = (normTapX - halfW).coerceIn(0f, 1f - halfW * 2),
                                                normTop = (normTapY - halfH).coerceIn(0f, 1f - halfH * 2),
                                                normRight = (normTapX + halfW).coerceIn(halfW * 2, 1f),
                                                normBottom = (normTapY + halfH).coerceIn(halfH * 2, 1f),
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

        // Cabeçalho superior compacto e elevado para a barra de status (libera visão superior)
        Surface(
            modifier = Modifier
                .statusBarsPadding()
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 4.dp),
            color = Color.Black.copy(alpha = 0.78f),
            shape = RoundedCornerShape(14.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(plantId, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("Toque no fruto p/ classificar ou no fundo p/ +", color = Color(0xFFB0BEC5), fontSize = 10.sp)
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (plantaNaoColetavel) Color(0xFFC62828) else Color(0xFF2E7D32)
                ) {
                    Text(
                        if (plantaNaoColetavel) "NÃO COLETÁVEL" else "$totalFrutos Frutos",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }

        // Painel Flutuante quando uma caixa de fruto está selecionada (acoplado acima da barra inferior)
        if (indiceSelecionado in caixas.indices && !plantaNaoColetavel) {
            val caixaAtual = caixas[indiceSelecionado]
            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 98.dp, start = 8.dp, end = 8.dp)
                    .fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E).copy(alpha = 0.98f)),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF424242))
            ) {
                Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Fruto #${indiceSelecionado + 1}${if (caixaAtual.manual) " (Manual)" else ""} • Classifique:",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // Diminuir tamanho do quadradinho
                            IconButton(
                                onClick = {
                                    caixas[indiceSelecionado] = redimensionarCaixaAuditavel(caixaAtual, 0.85f)
                                },
                                modifier = Modifier
                                    .size(28.dp)
                                    .background(Color(0xFF333333), RoundedCornerShape(6.dp))
                            ) {
                                Text("-", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            }
                            // Aumentar tamanho do quadradinho
                            IconButton(
                                onClick = {
                                    caixas[indiceSelecionado] = redimensionarCaixaAuditavel(caixaAtual, 1.18f)
                                },
                                modifier = Modifier
                                    .size(28.dp)
                                    .background(Color(0xFF333333), RoundedCornerShape(6.dp))
                            ) {
                                Text("+", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                            Spacer(Modifier.width(4.dp))
                            IconButton(
                                onClick = { indiceSelecionado = -1 },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.Close, null, tint = Color.LightGray, modifier = Modifier.size(18.dp))
                            }
                        }
                    }

                    Spacer(Modifier.height(6.dp))

                    // Botões de Classificação do Fruto
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Sadio
                        Button(
                            onClick = {
                                caixas[indiceSelecionado] = caixaAtual.copy(estado = EstadoFruto.SADIO)
                                indiceSelecionado = -1
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (caixaAtual.estado == EstadoFruto.SADIO) Color(0xFF00E676) else Color(0xFF2C2C2C)
                            ),
                            modifier = Modifier.weight(1f).height(38.dp),
                            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Sadio", fontSize = 11.sp, color = if (caixaAtual.estado == EstadoFruto.SADIO) Color.Black else Color.White, fontWeight = FontWeight.Bold)
                        }

                        // Vassoura
                        Button(
                            onClick = {
                                caixas[indiceSelecionado] = caixaAtual.copy(estado = EstadoFruto.VASSOURA)
                                indiceSelecionado = -1
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (caixaAtual.estado == EstadoFruto.VASSOURA) Color(0xFFFF9100) else Color(0xFF2C2C2C)
                            ),
                            modifier = Modifier.weight(1f).height(38.dp),
                            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Vassoura", fontSize = 11.sp, color = if (caixaAtual.estado == EstadoFruto.VASSOURA) Color.Black else Color.White, fontWeight = FontWeight.Bold)
                        }

                        // Podridão
                        Button(
                            onClick = {
                                caixas[indiceSelecionado] = caixaAtual.copy(estado = EstadoFruto.PODRIDAO)
                                indiceSelecionado = -1
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (caixaAtual.estado == EstadoFruto.PODRIDAO) Color(0xFFFF1744) else Color(0xFF2C2C2C)
                            ),
                            modifier = Modifier.weight(1f).height(38.dp),
                            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Podridão", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                        }

                        // Não Avaliável
                        Button(
                            onClick = {
                                caixas[indiceSelecionado] = caixaAtual.copy(estado = EstadoFruto.NAO_AVALIAVEL)
                                indiceSelecionado = -1
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (caixaAtual.estado == EstadoFruto.NAO_AVALIAVEL) Color(0xFF90A4AE) else Color(0xFF2C2C2C)
                            ),
                            modifier = Modifier.weight(1f).height(38.dp),
                            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("N. Aval.", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                        }

                        // Excluir caixa
                        IconButton(
                            onClick = {
                                caixasExcluidasManualmente.add(caixaAtual)
                                caixas.removeAt(indiceSelecionado)
                                indiceSelecionado = -1
                            },
                            modifier = Modifier
                                .size(38.dp)
                                .background(Color(0xFFB71C1C).copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                        ) {
                            Icon(Icons.Default.Delete, null, tint = Color(0xFFFF5252), modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }

        // Painel Inferior: Resumo das Classes e Ações compactado e rebaixado
        Surface(
            modifier = Modifier
                .navigationBarsPadding()
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            color = Color(0xFF181818).copy(alpha = 0.96f),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF333333))
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Barra de Chips de Totais por Categoria
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ChipResumo(rotulo = "Sadios", total = qtdSadios, cor = Color(0xFF00E676))
                    ChipResumo(rotulo = "Vassoura", total = qtdVassoura, cor = Color(0xFFFF9100))
                    ChipResumo(rotulo = "Podridão", total = qtdPodridao, cor = Color(0xFFFF1744))
                    ChipResumo(rotulo = "N.Aval", total = qtdNaoAvaliavel, cor = Color(0xFF90A4AE))
                }

                Spacer(Modifier.height(4.dp))

                // Toggle para marcar planta inteira como Não Coletável
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Checkbox(
                        checked = plantaNaoColetavel,
                        onCheckedChange = { plantaNaoColetavel = it },
                        colors = CheckboxDefaults.colors(checkedColor = Color.Red),
                        modifier = Modifier.scale(0.85f)
                    )
                    Text("Marcar Planta como Não Coletável / Ausente", color = Color.White, fontSize = 11.sp)
                }

                Spacer(Modifier.height(4.dp))

                // Botões de Ação
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Refazer Foto
                    OutlinedButton(
                        onClick = onRefazer,
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("REFAZER", fontSize = 12.sp)
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
                            .weight(1.3f)
                            .height(40.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(if (plantaNaoColetavel) "CONFIRMAR AUSENTE" else "SALVAR ($totalFrutos)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun ChipResumo(rotulo: String, total: Int, cor: Color) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = cor.copy(alpha = 0.16f),
        border = androidx.compose.foundation.BorderStroke(1.dp, cor.copy(alpha = 0.45f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(cor, CircleShape)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                "$total $rotulo",
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 10.sp
            )
        }
    }
}

internal fun iouNorm(c: CaixaAuditavel, l: Float, t: Float, r: Float, b: Float): Float {
    val interL = maxOf(c.normLeft, l)
    val interT = maxOf(c.normTop, t)
    val interR = minOf(c.normRight, r)
    val interB = minOf(c.normBottom, b)
    val w = maxOf(0f, interR - interL)
    val h = maxOf(0f, interB - interT)
    val inter = w * h
    val area1 = (c.normRight - c.normLeft) * (c.normBottom - c.normTop)
    val area2 = (r - l) * (b - t)
    val union = area1 + area2 - inter
    return if (union <= 0f) 0f else inter / union
}

private fun carregarBitmapFoto(context: Context, uri: Uri, maxDim: Int = 1280): Bitmap? {
    return try {
        val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, boundsOptions)
        }
        val origW = boundsOptions.outWidth
        val origH = boundsOptions.outHeight
        if (origW <= 0 || origH <= 0) return null

        var sampleSize = 1
        var w = origW
        var h = origH
        while (w > maxDim || h > maxDim) {
            sampleSize *= 2
            w /= 2
            h /= 2
        }

        val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val rawBmp = context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOptions)
        } ?: return null

        val orientation = try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            } ?: ExifInterface.ORIENTATION_NORMAL
        } catch (_: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }
        }

        if (matrix.isIdentity) {
            rawBmp
        } else {
            val rotated = Bitmap.createBitmap(rawBmp, 0, 0, rawBmp.width, rawBmp.height, matrix, true)
            if (rotated != rawBmp) rawBmp.recycle()
            rotated
        }
    } catch (_: Exception) {
        null
    }
}
