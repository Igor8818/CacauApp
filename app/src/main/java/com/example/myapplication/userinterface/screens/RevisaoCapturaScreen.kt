package com.example.myapplication.userinterface.screens

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
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
import com.example.myapplication.data.OcorrenciaCampo
import com.example.myapplication.data.SituacaoPlanta
import com.example.myapplication.userinterface.util.ObjectDetectorHelper
import com.example.myapplication.userinterface.util.carregarBitmapDeUri
import com.example.myapplication.userinterface.util.mesmoFruto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Classificação individual de cada fruto detectado ou marcado na foto.
 */
private enum class EstadoFruto(
    val rotuloCurto: String,
    val corCaixa: Color
) {
    SADIO("Sadio", Color(0xFF00E676)),
    VASSOURA("Vassoura", Color(0xFFFF9100)),
    PODRIDAO("Podridão", Color(0xFFFF1744)),
    NAO_IDENTIFICAVEL("Não Identif.", Color(0xFF90A4AE))
}

private data class CaixaAuditavel(
    val rect: RectF,
    val score: Float,
    val manual: Boolean = false,
    val estado: EstadoFruto = EstadoFruto.SADIO
)

/**
 * Tela de Revisão Pós-Foto unificada com o Registro Rápido e com Classificação por Fruto:
 * - Preserva a contagem e as caixas detectadas na câmera ao vivo no momento do clique;
 * - Permite classificar a planta/foto inteira com as mesmas opções do Registro Rápido:
 *   🟢 Normal | ⚪ Não Coletável | 🟠 Cavalo | 🔴 Morta | 🟣 Errada;
 * - Ao tocar em qualquer caixa de fruto na foto, o trabalhador escolhe se AQUELE fruto é:
 *   🟢 Sadio, 🟠 Vassoura, 🔴 Podridão, ⚪ Não Identif. ou 🗑️ Excluir.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RevisaoCapturaScreen(
    fotoUri: Uri,
    plantId: String,
    modelConfig: ModelConfig,
    detectorHelper: ObjectDetectorHelper?,
    deteccoesIniciais: List<DetectionResult> = emptyList(),
    larguraFrameVivo: Float = 1f,
    alturaFrameVivo: Float = 1f,
    dadosAgronomicos: DadosAgronomicosPlanta = DadosAgronomicosPlanta(),
    onDadosAgronomicosChange: ((DadosAgronomicosPlanta) -> Unit)? = null,
    onConfirmar: (contagemIa: Int, contagemFinal: Int) -> Unit,
    onRefazer: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    var mostrarSheetFicha by remember { mutableStateOf(false) }
    var indiceCaixaSelecionada by remember(fotoUri) { mutableIntStateOf(-1) }
    var situacaoPlantaSelecionada by remember(fotoUri) {
        mutableStateOf(
            SituacaoPlanta.entries.firstOrNull { it.rotulo.equals(dadosAgronomicos.sitPla, ignoreCase = true) }
                ?: SituacaoPlanta.NORMAL
        )
    }

    val plantaInativa = situacaoPlantaSelecionada != SituacaoPlanta.NORMAL

    BackHandler(enabled = mostrarSheetFicha) {
        mostrarSheetFicha = false
    }

    BackHandler(enabled = !mostrarSheetFicha && indiceCaixaSelecionada >= 0) {
        indiceCaixaSelecionada = -1
    }

    BackHandler(enabled = !mostrarSheetFicha && indiceCaixaSelecionada < 0) {
        onRefazer()
    }

    var carregandoInferencia by remember { mutableStateOf(true) }
    var bitmapFoto by remember { mutableStateOf<Bitmap?>(null) }

    // Inicializa já com as detecções vistas na câmera ao vivo para nunca zerar a contagem
    val caixasDetectadas = remember(fotoUri) {
        mutableStateListOf<CaixaAuditavel>().apply {
            deteccoesIniciais.forEach { det ->
                add(
                    CaixaAuditavel(
                        rect = RectF(det.boundingBox),
                        score = det.categories.firstOrNull()?.score ?: 0.6f,
                        manual = false,
                        estado = EstadoFruto.SADIO
                    )
                )
            }
        }
    }
    var contagemIaOriginal by remember(fotoUri) { mutableIntStateOf(deteccoesIniciais.size) }
    var ajusteRapidoDelta by remember(fotoUri) { mutableIntStateOf(0) }

    val qtdSadiosCaixas = caixasDetectadas.count { it.estado == EstadoFruto.SADIO }
    val qtdVassouraCaixas = caixasDetectadas.count { it.estado == EstadoFruto.VASSOURA }
    val qtdPodridaoCaixas = caixasDetectadas.count { it.estado == EstadoFruto.PODRIDAO }
    val qtdNaoIdentifCaixas = caixasDetectadas.count { it.estado == EstadoFruto.NAO_IDENTIFICAVEL }

    val contagemFinalAuditada = if (plantaInativa) 0 else (caixasDetectadas.size + ajusteRapidoDelta).coerceAtLeast(0)
    val qtdSadiosTotal = if (plantaInativa) 0 else (qtdSadiosCaixas + ajusteRapidoDelta).coerceAtLeast(0)

    // Sincroniza a marcação individual dos frutos e a situação da planta na foto com os dados da planilha CEPLAC
    fun sincronizarFrutosComFicha(novaSit: SituacaoPlanta = situacaoPlantaSelecionada) {
        val cb = onDadosAgronomicosChange ?: return
        if (novaSit != SituacaoPlanta.NORMAL) {
            cb(
                dadosAgronomicos.copy(
                    sitPla = novaSit.rotulo,
                    nfPequenos = 0,
                    nfMedios = 0,
                    nfAdultos = 0,
                    nfMaduros = 0
                )
            )
            return
        }
        val vas = caixasDetectadas.count { it.estado == EstadoFruto.VASSOURA }
        val pod = caixasDetectadas.count { it.estado == EstadoFruto.PODRIDAO }
        val naoId = caixasDetectadas.count { it.estado == EstadoFruto.NAO_IDENTIFICAVEL }
        val sadios = (caixasDetectadas.count { it.estado == EstadoFruto.SADIO } + ajusteRapidoDelta).coerceAtLeast(0)

        val novasOcorrencias = dadosAgronomicos.ocorrencias.toMutableSet()
        if (vas > 0) novasOcorrencias.add(OcorrenciaCampo.VASSOURA_BRUXA.rotulo)
        if (pod > 0) novasOcorrencias.add(OcorrenciaCampo.PODRIDAO_PARDA.rotulo)

        cb(
            dadosAgronomicos.copy(
                sitPla = SituacaoPlanta.NORMAL.rotulo,
                nfAdultos = sadios,
                nfVassoura = vas,
                nfPodridao = pod,
                nfOutrasPerdas = naoId,
                ocorrencias = novasOcorrencias
            )
        )
    }

    LaunchedEffect(fotoUri) {
        carregandoInferencia = true
        val (bmp, caixasFinais) = withContext(Dispatchers.Default) {
            val bitmap = carregarBitmapDeUri(context, fotoUri, ladoMaximo = 1600)
            val imgW = bitmap?.width?.toFloat() ?: larguraFrameVivo.coerceAtLeast(1f)
            val imgH = bitmap?.height?.toFloat() ?: alturaFrameVivo.coerceAtLeast(1f)

            val scaleX = imgW / larguraFrameVivo.coerceAtLeast(1f)
            val scaleY = imgH / alturaFrameVivo.coerceAtLeast(1f)
            val caixasVivoEscaladas = deteccoesIniciais.map { det ->
                val r = det.boundingBox
                CaixaAuditavel(
                    rect = RectF(
                        (r.left * scaleX).coerceIn(0f, imgW),
                        (r.top * scaleY).coerceIn(0f, imgH),
                        (r.right * scaleX).coerceIn(0f, imgW),
                        (r.bottom * scaleY).coerceIn(0f, imgH)
                    ),
                    score = det.categories.firstOrNull()?.score ?: 0.6f,
                    manual = false,
                    estado = EstadoFruto.SADIO
                )
            }

            val caixasFoto = if (bitmap != null && detectorHelper != null) {
                val limiarEfetivoFoto = minOf(modelConfig.threshold, 0.25f) - 0.03f
                detectorHelper.detectarAltaPrecisao(bitmap)
                    .filter { det ->
                        val label = det.categories.firstOrNull()?.label?.lowercase() ?: ""
                        val score = det.categories.firstOrNull()?.score ?: 0f
                        score >= limiarEfetivoFoto &&
                            (modelConfig.labelsFiltro.isEmpty() || detectorHelper.numClasses <= 1 || modelConfig.labelsFiltro.any { f -> label.contains(f.lowercase()) })
                    }
                    .map { det ->
                        CaixaAuditavel(
                            rect = det.boundingBox,
                            score = det.categories.firstOrNull()?.score ?: 0.5f,
                            manual = false,
                            estado = EstadoFruto.SADIO
                        )
                    }
            } else emptyList()

            val combinadas = if (caixasFoto.isEmpty()) {
                caixasVivoEscaladas
            } else {
                val resultado = caixasFoto.toMutableList()
                for (vivo in caixasVivoEscaladas) {
                    val arrVivo = floatArrayOf(vivo.rect.left, vivo.rect.top, vivo.rect.right, vivo.rect.bottom, vivo.score, 0f)
                    val jaExisteNaFoto = resultado.any { foto ->
                        val arrFoto = floatArrayOf(foto.rect.left, foto.rect.top, foto.rect.right, foto.rect.bottom, foto.score, 0f)
                        mesmoFruto(arrFoto, arrVivo, iouThresh = 0.32f, iosThresh = 0.55f)
                    }
                    if (!jaExisteNaFoto) {
                        resultado.add(vivo)
                    }
                }
                resultado
            }
            bitmap to combinadas
        }
        bitmapFoto = bmp
        caixasDetectadas.clear()
        caixasDetectadas.addAll(caixasFinais)
        contagemIaOriginal = caixasFinais.size
        sincronizarFrutosComFicha()
        carregandoInferencia = false
    }

    val textPaint = remember {
        Paint().apply {
            isAntiAlias = true
            color = android.graphics.Color.WHITE
            textSize = 30f
            isFakeBoldText = true
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AsyncImage(
            model = fotoUri,
            contentDescription = "Foto capturada de $plantId",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit
        )

        if (bitmapFoto != null && !plantaInativa) {
            Canvas(
                Modifier
                    .fillMaxSize()
                    .pointerInput(bitmapFoto) {
                        detectTapGestures { tapOffset ->
                            val bmp = bitmapFoto ?: return@detectTapGestures
                            val imgW = bmp.width.toFloat()
                            val imgH = bmp.height.toFloat()

                            val scale = minOf(size.width / imgW, size.height / imgH)
                            val offsetX = (size.width - imgW * scale) / 2f
                            val offsetY = (size.height - imgH * scale) / 2f

                            val xImg = (tapOffset.x - offsetX) / scale
                            val yImg = (tapOffset.y - offsetY) / scale

                            if (xImg in 0f..imgW && yImg in 0f..imgH) {
                                val margemToqueX = imgW * 0.02f
                                val margemToqueY = imgH * 0.02f
                                val caixaExistenteIdx = caixasDetectadas.indexOfLast { c ->
                                    xImg in (c.rect.left - margemToqueX)..(c.rect.right + margemToqueX) &&
                                        yImg in (c.rect.top - margemToqueY)..(c.rect.bottom + margemToqueY)
                                }

                                if (caixaExistenteIdx >= 0) {
                                    indiceCaixaSelecionada = caixaExistenteIdx
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                } else {
                                    val raioW = imgW * 0.07f
                                    val raioH = imgH * 0.07f
                                    val novaCaixa = RectF(
                                        (xImg - raioW).coerceAtLeast(0f),
                                        (yImg - raioH).coerceAtLeast(0f),
                                        (xImg + raioW).coerceAtMost(imgW),
                                        (yImg + raioH).coerceAtMost(imgH)
                                    )
                                    caixasDetectadas.add(
                                        CaixaAuditavel(
                                            rect = novaCaixa,
                                            score = 1f,
                                            manual = true,
                                            estado = EstadoFruto.SADIO
                                        )
                                    )
                                    indiceCaixaSelecionada = caixasDetectadas.lastIndex
                                    sincronizarFrutosComFicha()
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                }
                            }
                        }
                    }
            ) {
                val bmp = bitmapFoto ?: return@Canvas
                val imgW = bmp.width.toFloat()
                val imgH = bmp.height.toFloat()

                val scale = minOf(size.width / imgW, size.height / imgH)
                val offsetX = (size.width - imgW * scale) / 2f
                val offsetY = (size.height - imgH * scale) / 2f

                caixasDetectadas.forEachIndexed { idx, item ->
                    val rect = item.rect
                    val x = rect.left * scale + offsetX
                    val y = rect.top * scale + offsetY
                    val w = rect.width() * scale
                    val h = rect.height() * scale
                    val selecionada = idx == indiceCaixaSelecionada
                    val corCaixa = item.estado.corCaixa

                    if (selecionada) {
                        drawRoundRect(
                            color = Color.White,
                            topLeft = Offset(x - 4.dp.toPx(), y - 4.dp.toPx()),
                            size = Size(w + 8.dp.toPx(), h + 8.dp.toPx()),
                            cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx()),
                            style = Stroke(width = 3.5.dp.toPx())
                        )
                    }

                    drawRect(
                        color = Color(0xAA000000),
                        topLeft = Offset(x - 1.dp.toPx(), y - 1.dp.toPx()),
                        size = Size(w + 2.dp.toPx(), h + 2.dp.toPx()),
                        style = Stroke(width = 4.dp.toPx())
                    )
                    drawRect(
                        color = corCaixa,
                        topLeft = Offset(x, y),
                        size = Size(w, h),
                        style = Stroke(width = if (selecionada) 4.dp.toPx() else 2.8.dp.toPx())
                    )

                    val etiqueta = "#${idx + 1} ${item.estado.rotuloCurto}"
                    val larguraTexto = textPaint.measureText(etiqueta)
                    val alturaEtiqueta = 22.dp.toPx()
                    val yEtiqueta = (y - alturaEtiqueta).coerceAtLeast(4.dp.toPx())

                    drawRoundRect(
                        color = corCaixa.copy(alpha = 0.92f),
                        topLeft = Offset(x, yEtiqueta),
                        size = Size(larguraTexto + 14.dp.toPx(), alturaEtiqueta),
                        cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                    )
                    drawContext.canvas.nativeCanvas.drawText(
                        etiqueta,
                        x + 7.dp.toPx(),
                        yEtiqueta + alturaEtiqueta - 6.dp.toPx(),
                        textPaint
                    )
                }
            }
        }

        // ── Painel Superior Limpo: Situação da Planta (unificada com Registro Rápido) + Contagem ──
        Surface(
            Modifier
                .statusBarsPadding()
                .align(Alignment.TopCenter)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            color = Color(0xE6141414),
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, Color(0x55FFFFFF))
        ) {
            Column(
                Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = plantId,
                    color = Color(0xFFA5D6A7),
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    maxLines = 1
                )
                Spacer(Modifier.height(6.dp))

                // Linha de Situação da Planta / Foto (idêntica às opções do Registro Rápido!)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ChipSituacaoFoto(
                        texto = "🟢 Normal",
                        cor = Color(0xFF00E676),
                        selecionado = situacaoPlantaSelecionada == SituacaoPlanta.NORMAL,
                        onClick = {
                            situacaoPlantaSelecionada = SituacaoPlanta.NORMAL
                            sincronizarFrutosComFicha(SituacaoPlanta.NORMAL)
                        }
                    )
                    ChipSituacaoFoto(
                        texto = "⚪ Não Coletável",
                        cor = Color(0xFF90A4AE),
                        selecionado = situacaoPlantaSelecionada == SituacaoPlanta.NAO_COLETAVEL,
                        onClick = {
                            situacaoPlantaSelecionada = SituacaoPlanta.NAO_COLETAVEL
                            indiceCaixaSelecionada = -1
                            sincronizarFrutosComFicha(SituacaoPlanta.NAO_COLETAVEL)
                        }
                    )
                    ChipSituacaoFoto(
                        texto = "🟠 Cavalo",
                        cor = Color(0xFFFF9100),
                        selecionado = situacaoPlantaSelecionada == SituacaoPlanta.CAVALO,
                        onClick = {
                            situacaoPlantaSelecionada = SituacaoPlanta.CAVALO
                            indiceCaixaSelecionada = -1
                            sincronizarFrutosComFicha(SituacaoPlanta.CAVALO)
                        }
                    )
                    ChipSituacaoFoto(
                        texto = "🔴 Morta",
                        cor = Color(0xFFFF5252),
                        selecionado = situacaoPlantaSelecionada == SituacaoPlanta.MORTA,
                        onClick = {
                            situacaoPlantaSelecionada = SituacaoPlanta.MORTA
                            indiceCaixaSelecionada = -1
                            sincronizarFrutosComFicha(SituacaoPlanta.MORTA)
                        }
                    )
                    ChipSituacaoFoto(
                        texto = "🟣 Errada",
                        cor = Color(0xFFEA80FC),
                        selecionado = situacaoPlantaSelecionada == SituacaoPlanta.ERRADA,
                        onClick = {
                            situacaoPlantaSelecionada = SituacaoPlanta.ERRADA
                            indiceCaixaSelecionada = -1
                            sincronizarFrutosComFicha(SituacaoPlanta.ERRADA)
                        }
                    )
                }

                Spacer(Modifier.height(6.dp))

                if (plantaInativa) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF263238),
                        border = BorderStroke(1.dp, Color(0xFF90A4AE))
                    ) {
                        Text(
                            "Planta marcada como: ${situacaoPlantaSelecionada.rotulo.uppercase()}",
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        IconButton(
                            onClick = {
                                if (contagemFinalAuditada > 0) {
                                    if (ajusteRapidoDelta > 0) {
                                        ajusteRapidoDelta--
                                    } else if (caixasDetectadas.isNotEmpty()) {
                                        caixasDetectadas.removeAt(caixasDetectadas.lastIndex)
                                        if (indiceCaixaSelecionada >= caixasDetectadas.size) {
                                            indiceCaixaSelecionada = -1
                                        }
                                    }
                                    sincronizarFrutosComFicha()
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                            },
                            enabled = contagemFinalAuditada > 0,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                Icons.Default.RemoveCircle,
                                contentDescription = "Diminuir 1 fruto",
                                tint = if (contagemFinalAuditada > 0) Color(0xFFFF8A80) else Color.Gray,
                                modifier = Modifier.size(34.dp)
                            )
                        }

                        val txtPlural = if (contagemFinalAuditada == 1) "1 FRUTO" else "$contagemFinalAuditada FRUTOS"
                        Text(
                            txtPlural,
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 22.sp
                        )

                        IconButton(
                            onClick = {
                                ajusteRapidoDelta++
                                sincronizarFrutosComFicha()
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                Icons.Default.AddCircle,
                                contentDescription = "Adicionar 1 fruto",
                                tint = Color(0xFF00E676),
                                modifier = Modifier.size(34.dp)
                            )
                        }
                    }

                    // Resumo visual de quantos frutos estão Sadios vs Vassoura vs Podridão vs Não Identif.
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("🟢 $qtdSadiosTotal Sadios", color = Color(0xFF69F0AE), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        if (qtdVassouraCaixas > 0) {
                            Text("🟠 $qtdVassouraCaixas Vassoura", color = Color(0xFFFFAB40), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        if (qtdPodridaoCaixas > 0) {
                            Text("🔴 $qtdPodridaoCaixas Podridão", color = Color(0xFFFF5252), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        if (qtdNaoIdentifCaixas > 0) {
                            Text("⚪ $qtdNaoIdentifCaixas Não Identif.", color = Color(0xFFCFD8DC), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(Modifier.height(2.dp))
                    Text(
                        "Toque num fruto na foto para marcar Vassoura, Podridão ou Não Identif.",
                        color = Color.White.copy(alpha = 0.78f),
                        fontSize = 10.sp
                    )

                    if (onDadosAgronomicosChange != null) {
                        Spacer(Modifier.height(6.dp))
                        val temDados = dadosAgronomicos.corFruto.isNotBlank() || dadosAgronomicos.nvTotal > 0 || dadosAgronomicos.notaVirus > 0
                        OutlinedButton(
                            onClick = { mostrarSheetFicha = true },
                            modifier = Modifier.height(30.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (temDados) Color(0xFFE8F5E9) else Color(0xFF263238),
                                contentColor = if (temDados) Color(0xFF1B5E20) else Color.White
                            ),
                            border = BorderStroke(1.dp, if (temDados) Color(0xFF66BB6A) else Color(0xFF78909C)),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Assignment, null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                if (dadosAgronomicos.corFruto.isNotBlank()) "Cor: ${dadosAgronomicos.corFruto} / Ficha ✓"
                                else "+ Cor do Fruto / Extras (Opcional)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // ── Painel Inferior: Seletor do Fruto Clicado (se houver) + Botões Refazer / Salvar ──
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (!plantaInativa && indiceCaixaSelecionada in caixasDetectadas.indices) {
                val frutoAtual = caixasDetectadas[indiceCaixaSelecionada]
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xF01E1E1E),
                    border = BorderStroke(1.5.dp, frutoAtual.estado.corCaixa),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Fruto #${indiceCaixaSelecionada + 1} selecionado — Marcar como:",
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 12.sp
                            )
                            IconButton(
                                onClick = { indiceCaixaSelecionada = -1 },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.Close, "Fechar seleção", tint = Color.LightGray, modifier = Modifier.size(18.dp))
                            }
                        }

                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            BotaoEstadoFruto(
                                modifier = Modifier.weight(1f),
                                texto = "🟢 Sadio",
                                cor = Color(0xFF00E676),
                                selecionado = frutoAtual.estado == EstadoFruto.SADIO,
                                onClick = {
                                    caixasDetectadas[indiceCaixaSelecionada] = frutoAtual.copy(estado = EstadoFruto.SADIO)
                                    sincronizarFrutosComFicha()
                                }
                            )
                            BotaoEstadoFruto(
                                modifier = Modifier.weight(1.1f),
                                texto = "🟠 Vassoura",
                                cor = Color(0xFFFF9100),
                                selecionado = frutoAtual.estado == EstadoFruto.VASSOURA,
                                onClick = {
                                    caixasDetectadas[indiceCaixaSelecionada] = frutoAtual.copy(estado = EstadoFruto.VASSOURA)
                                    sincronizarFrutosComFicha()
                                }
                            )
                            BotaoEstadoFruto(
                                modifier = Modifier.weight(1.1f),
                                texto = "🔴 Podridão",
                                cor = Color(0xFFFF1744),
                                selecionado = frutoAtual.estado == EstadoFruto.PODRIDAO,
                                onClick = {
                                    caixasDetectadas[indiceCaixaSelecionada] = frutoAtual.copy(estado = EstadoFruto.PODRIDAO)
                                    sincronizarFrutosComFicha()
                                }
                            )
                            BotaoEstadoFruto(
                                modifier = Modifier.weight(1.15f),
                                texto = "⚪ Não Identif.",
                                cor = Color(0xFF90A4AE),
                                selecionado = frutoAtual.estado == EstadoFruto.NAO_IDENTIFICAVEL,
                                onClick = {
                                    caixasDetectadas[indiceCaixaSelecionada] = frutoAtual.copy(estado = EstadoFruto.NAO_IDENTIFICAVEL)
                                    sincronizarFrutosComFicha()
                                }
                            )
                            OutlinedButton(
                                onClick = {
                                    caixasDetectadas.removeAt(indiceCaixaSelecionada)
                                    indiceCaixaSelecionada = -1
                                    sincronizarFrutosComFicha()
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                },
                                modifier = Modifier.height(38.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = Color(0xFF3E2723),
                                    contentColor = Color(0xFFFF8A80)
                                ),
                                border = BorderStroke(1.dp, Color(0xFFFF5252)),
                                contentPadding = PaddingValues(horizontal = 6.dp)
                            ) {
                                Icon(Icons.Default.Delete, "Excluir fruto", modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }

            // Botões principais de ação: REFAZER FOTO vs SALVAR
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onRefazer,
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color(0xFFFFEBEE),
                        contentColor = Color(0xFFC62828)
                    ),
                    border = BorderStroke(1.5.dp, Color(0xFFC62828))
                ) {
                    Icon(Icons.Default.Refresh, null, tint = Color(0xFFC62828))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "REFAZER FOTO",
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFC62828),
                        fontSize = 13.sp
                    )
                }

                Button(
                    onClick = {
                        sincronizarFrutosComFicha()
                        onConfirmar(contagemIaOriginal, contagemFinalAuditada)
                    },
                    modifier = Modifier
                        .weight(1.35f)
                        .height(56.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                ) {
                    Icon(Icons.Default.Check, null, tint = Color.White)
                    Spacer(Modifier.width(6.dp))
                    val rotuloSalvar = when {
                        plantaInativa -> "SALVAR (${situacaoPlantaSelecionada.rotulo.uppercase()})"
                        contagemFinalAuditada == 1 -> "SALVAR (1 FRUTO)"
                        else -> "SALVAR ($contagemFinalAuditada FRUTOS)"
                    }
                    Text(
                        rotuloSalvar,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        fontSize = 13.sp,
                        maxLines = 1
                    )
                }
            }
        }
    }

    if (mostrarSheetFicha && onDadosAgronomicosChange != null) {
        ModalBottomSheet(
            onDismissRequest = { mostrarSheetFicha = false },
            containerColor = Color.White,
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    "Dados Extras da Planta (Opcional)",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 17.sp,
                    color = Color(0xFF1B5E20)
                )

                ConteudoFichaCeplacLimpa(
                    dados = dadosAgronomicos,
                    onDadosChange = onDadosAgronomicosChange
                )

                Button(
                    onClick = { mostrarSheetFicha = false },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                ) {
                    Text("CONCLUIR", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun ChipSituacaoFoto(
    texto: String,
    cor: Color,
    selecionado: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .height(30.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        color = if (selecionado) cor.copy(alpha = 0.28f) else Color(0xFF262626),
        border = BorderStroke(if (selecionado) 1.5.dp else 1.dp, if (selecionado) cor else Color(0xFF555555))
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 10.dp)) {
            Text(
                texto,
                color = Color.White,
                fontWeight = if (selecionado) FontWeight.ExtraBold else FontWeight.Medium,
                fontSize = 11.sp,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun BotaoEstadoFruto(
    modifier: Modifier = Modifier,
    texto: String,
    cor: Color,
    selecionado: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .height(38.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(10.dp),
        color = if (selecionado) cor.copy(alpha = 0.28f) else Color(0xFF2C2C2C),
        border = BorderStroke(if (selecionado) 2.dp else 1.dp, if (selecionado) cor else Color(0xFF555555))
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 3.dp)) {
            Text(
                texto,
                color = Color.White,
                fontWeight = if (selecionado) FontWeight.ExtraBold else FontWeight.SemiBold,
                fontSize = 10.sp,
                maxLines = 1
            )
        }
    }
}
