package br.gov.ceplac.cepec.cacauapp.userinterface

import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Scale
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.gov.ceplac.cepec.cacauapp.data.ConfigRendimentoCacau
import br.gov.ceplac.cepec.cacauapp.data.ModelConfig
import br.gov.ceplac.cepec.cacauapp.data.ModoIACacau
import br.gov.ceplac.cepec.cacauapp.data.RegistroCacau
import br.gov.ceplac.cepec.cacauapp.data.calcularRendimentoEnsaioCacau
import br.gov.ceplac.cepec.cacauapp.data.formatarEstimativaCurta
import br.gov.ceplac.cepec.cacauapp.ui.theme.CacauBordaSuave
import br.gov.ceplac.cepec.cacauapp.ui.theme.CacauDourado
import br.gov.ceplac.cepec.cacauapp.ui.theme.CacauDouradoClaro
import br.gov.ceplac.cepec.cacauapp.ui.theme.CacauTextoPrincipal
import br.gov.ceplac.cepec.cacauapp.ui.theme.CacauTextoSecundario
import br.gov.ceplac.cepec.cacauapp.ui.theme.CacauVerdeBotanico
import br.gov.ceplac.cepec.cacauapp.ui.theme.CacauVerdeClaro
import br.gov.ceplac.cepec.cacauapp.ui.theme.CacauVerdeEscuro
import br.gov.ceplac.cepec.cacauapp.userinterface.util.BoundingBoxOverlay
import br.gov.ceplac.cepec.cacauapp.userinterface.util.CameraWithAI
import br.gov.ceplac.cepec.cacauapp.userinterface.util.ObjectDetectorHelper
import br.gov.ceplac.cepec.cacauapp.userinterface.util.criarImageCaptureCacau
import br.gov.ceplac.cepec.cacauapp.userinterface.util.tirarFoto
import kotlinx.coroutines.delay
import org.tensorflow.lite.task.vision.detector.Detection
import java.util.Locale

/**
 * Ponte global para disparar captura pelos botões físicos de Volume (+/-) no campo.
 */
object VolumeShutterBridge {
    @Volatile
    var onVolumePress: (() -> Boolean)? = null
}

@Composable
fun CameraFullscreenCacau(
    plantId: String,
    modelConfig: ModelConfig,
    helper: ObjectDetectorHelper,
    detections: List<Detection>,
    bitmapWidth: Float,
    bitmapHeight: Float,
    inferenciaMs: Long,
    onResultsUpdated: (ObjectDetectorHelper.ResultadoDeteccao) -> Unit,
    onTrocarModoIA: (ModoIACacau) -> Unit,
    onAtualizarThreshold: (Float) -> Unit,
    onFotoCapturada: (Uri) -> Unit,
    onAbrirGaleria: (() -> Unit)? = null,
    onFechar: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val imageCapture = remember { criarImageCaptureCacau() }

    var lanternaAtiva by remember { mutableStateOf(false) }
    var suportaLanterna by remember { mutableStateOf(false) }
    var zoomRatio by remember { mutableFloatStateOf(1f) }
    var zoomMaxDisponivel by remember { mutableFloatStateOf(4f) }
    var focoToque by remember { mutableStateOf<Offset?>(null) }
    var erroCamera by remember { mutableStateOf<String?>(null) }
    var capturando by remember { mutableStateOf(false) }
    var mostrarSliderConfianca by remember { mutableStateOf(false) }

    val thresholdAtual = if (modelConfig.modoEfetivo == ModoIACacau.SOMENTE_DOENCAS) {
        modelConfig.thresholdDoenca
    } else {
        modelConfig.threshold
    }

    LaunchedEffect(focoToque) {
        if (focoToque != null) {
            delay(950)
            focoToque = null
        }
    }

    fun dispararCaptura() {
        if (capturando) return
        capturando = true
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        tirarFoto(
            context = context,
            capture = imageCapture,
            id = plantId,
            onErro = { msg ->
                capturando = false
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            },
            onSucesso = { uri ->
                capturando = false
                onFotoCapturada(uri)
            }
        )
    }

    DisposableEffect(plantId, imageCapture) {
        VolumeShutterBridge.onVolumePress = {
            dispararCaptura()
            true
        }
        onDispose {
            VolumeShutterBridge.onVolumePress = null
        }
    }

    val ringAlpha by animateFloatAsState(targetValue = if (focoToque != null) 1f else 0f, label = "foco")

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        CameraWithAI(
            helper = helper,
            capture = imageCapture,
            lanternaAtiva = lanternaAtiva,
            zoomRatio = zoomRatio,
            onSuportaLanterna = { suportaLanterna = it },
            onZoomMaxDisponivel = { zoomMaxDisponivel = it.coerceAtLeast(1.5f) },
            onZoomRatioChanged = { zoomRatio = it },
            onErroCamera = { erroCamera = it },
            onTapFocus = { x, y -> focoToque = Offset(x, y) },
            onResultsUpdated = onResultsUpdated
        )

        BoundingBoxOverlay(
            detections = detections,
            bitmapWidth = bitmapWidth,
            bitmapHeight = bitmapHeight,
            modifier = Modifier.fillMaxSize()
        )

        // Anel visual de tap-to-focus
        val foco = focoToque
        if (foco != null && ringAlpha > 0.05f) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(
                    color = Color(0xFFFFD54F).copy(alpha = ringAlpha),
                    radius = 34.dp.toPx(),
                    center = foco,
                    style = Stroke(width = 2.5.dp.toPx())
                )
            }
        }

        // Cabeçalho superior translúcido
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = Color.Black.copy(alpha = 0.65f),
                    shape = CircleShape,
                    modifier = Modifier.clickable { onFechar() }
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Fechar câmera",
                        tint = Color.White,
                        modifier = Modifier.padding(10.dp)
                    )
                }

                Surface(
                    color = Color.Black.copy(alpha = 0.68f),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = plantId,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${detections.size} fruto(s) ao vivo • ${inferenciaMs} ms",
                            color = Color(0xFFA5D6A7),
                            fontSize = 11.sp
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (suportaLanterna) {
                        Surface(
                            color = if (lanternaAtiva) CacauDourado else Color.Black.copy(alpha = 0.65f),
                            shape = CircleShape,
                            modifier = Modifier.clickable {
                                lanternaAtiva = !lanternaAtiva
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        ) {
                            Icon(
                                if (lanternaAtiva) Icons.Default.FlashOn else Icons.Default.FlashOff,
                                contentDescription = "Lanterna",
                                tint = Color.White,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }

                    Surface(
                        color = if (mostrarSliderConfianca) CacauVerdeBotanico else Color.Black.copy(alpha = 0.65f),
                        shape = CircleShape,
                        modifier = Modifier.clickable { mostrarSliderConfianca = !mostrarSliderConfianca }
                    ) {
                        Icon(
                            Icons.Default.Tune,
                            contentDescription = "Ajustar confiança ao vivo",
                            tint = Color.White,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            }

            // Seletor rápido de modo das 2 IAs de Cacau + mensagem de fallback caso ocorra erro
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Surface(
                    color = Color.Black.copy(alpha = 0.66f),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
                ) {
                    Row(
                        modifier = Modifier.padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ModoIACacau.entries.forEach { modo ->
                            val ativo = modelConfig.modoEfetivo == modo && !modelConfig.usaModeloCustomizado
                            Surface(
                                color = if (ativo) CacauVerdeBotanico else Color.Transparent,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onTrocarModoIA(modo)
                                }
                            ) {
                                Text(
                                    text = modo.rotuloCurto,
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = if (ativo) FontWeight.Bold else FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }
                }
            }

            erroCamera?.let { msg ->
                Surface(
                    color = Color(0xFFB71C1C).copy(alpha = 0.90f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = msg,
                        color = Color.White,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }

            AnimatedVisibility(visible = mostrarSliderConfianca) {
                Surface(
                    color = Color.Black.copy(alpha = 0.78f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                        Text(
                            text = "Sensibilidade ao vivo: ${(thresholdAtual * 100).toInt()}%",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Slider(
                            value = thresholdAtual.coerceIn(0.15f, 0.75f),
                            onValueChange = { onAtualizarThreshold(it) },
                            valueRange = 0.15f..0.75f,
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFFA5D6A7),
                                activeTrackColor = Color(0xFF4CAF50)
                            )
                        )
                    }
                }
            }
        }

        // Controles inferiores: chips de zoom + obturador grande + atalho de galeria
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 18.dp, start = 16.dp, end = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Chips 1x / 2x
            Surface(
                color = Color.Black.copy(alpha = 0.62f),
                shape = RoundedCornerShape(20.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(1f to "1x", 2f to "2x").forEach { (alvo, rotulo) ->
                        if (alvo <= zoomMaxDisponivel) {
                            val ativo = kotlin.math.abs(zoomRatio - alvo) < 0.25f
                            Surface(
                                color = if (ativo) Color.White else Color.Transparent,
                                shape = CircleShape,
                                modifier = Modifier.clickable {
                                    zoomRatio = alvo
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                            ) {
                                Text(
                                    text = rotulo,
                                    color = if (ativo) Color.Black else Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }
                    if (zoomRatio > 2.3f) {
                        Surface(color = Color.White, shape = CircleShape) {
                            Text(
                                text = "${String.format(Locale.US, "%.1f", zoomRatio)}x",
                                color = Color.Black,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                if (onAbrirGaleria != null) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.65f),
                        shape = CircleShape,
                        modifier = Modifier
                            .size(52.dp)
                            .clickable { onAbrirGaleria() }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = "Galeria", tint = Color.White)
                        }
                    }
                } else {
                    Spacer(Modifier.size(52.dp))
                }

                // Obturador principal
                Surface(
                    color = Color.White,
                    shape = CircleShape,
                    modifier = Modifier
                        .size(78.dp)
                        .border(4.dp, Color.White.copy(alpha = 0.45f), CircleShape)
                        .clickable(enabled = !capturando) { dispararCaptura() }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (capturando) {
                            CircularProgressIndicator(modifier = Modifier.size(30.dp), color = CacauVerdeEscuro, strokeWidth = 3.dp)
                        } else {
                            Icon(
                                Icons.Default.CameraAlt,
                                contentDescription = "Fotografar",
                                tint = CacauVerdeEscuro,
                                modifier = Modifier.size(34.dp)
                            )
                        }
                    }
                }

                Surface(
                    color = Color.Black.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "Vol ±\nCaptura",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 10.sp,
                        lineHeight = 12.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun BarraStatusModeloCacau(
    modelConfig: ModelConfig,
    onClickConfig: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = CacauVerdeClaro,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, CacauBordaSuave),
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClickConfig() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = CacauVerdeEscuro, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(
                        text = modelConfig.nomeExibicao,
                        color = CacauVerdeEscuro,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Conf. ${(modelConfig.threshold * 100).toInt()}% • Doenças ${(modelConfig.thresholdDoenca * 100).toInt()}%",
                        color = CacauTextoSecundario,
                        fontSize = 11.sp
                    )
                }
            }
            Icon(Icons.Default.Settings, contentDescription = "Configurar IA", tint = CacauVerdeEscuro, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
fun FaixaAcumuladoRendimentoCacau(
    registros: List<RegistroCacau>,
    configRendimento: ConfigRendimentoCacau,
    onAbrirConfigRendimento: () -> Unit,
    modifier: Modifier = Modifier
) {
    val resumo = remember(registros, configRendimento) {
        calcularRendimentoEnsaioCacau(registros, configRendimento)
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, CacauBordaSuave),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
            .fillMaxWidth()
            .clickable { onAbrirConfigRendimento() }
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Scale, contentDescription = null, tint = CacauDourado, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Produção Estimada (@ e kg)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = CacauTextoPrincipal
                    )
                }
                Surface(color = CacauDouradoClaro, shape = RoundedCornerShape(8.dp)) {
                    Text(
                        text = "IF ${configRendimento.indiceFruto.toInt()} • ${configRendimento.plantasPorHa} pl/ha",
                        color = Color(0xFF6D4C00),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricBoxRendimento(
                    valor = formatarEstimativaCurta(resumo.arrobasAcumuladas, "@"),
                    rotulo = formatarEstimativaCurta(resumo.kgAcumulado, "kg acum."),
                    cor = CacauVerdeEscuro,
                    modifier = Modifier.weight(1f)
                )
                MetricBoxRendimento(
                    valor = formatarEstimativaCurta(resumo.realArrobasHa, "@/ha"),
                    rotulo = formatarEstimativaCurta(resumo.realKgHa, "kg/ha"),
                    cor = CacauVerdeBotanico,
                    modifier = Modifier.weight(1f)
                )
                MetricBoxRendimento(
                    valor = if (resumo.frutosPerdidosDoenca > 0) formatarEstimativaCurta(resumo.perdaArrobasHa, "@/ha") else "0 @/ha",
                    rotulo = "Perda doença (${resumo.taxaInfeccaoPercentual.toInt()}%)",
                    cor = if (resumo.frutosPerdidosDoenca > 0) Color(0xFFC62828) else CacauTextoSecundario,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun MetricBoxRendimento(
    valor: String,
    rotulo: String,
    cor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color(0xFFF6F9F6),
        shape = RoundedCornerShape(10.dp),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp)) {
            Text(valor, color = cor, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, maxLines = 1)
            Text(rotulo, color = CacauTextoSecundario, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun DialogConfiguracaoRendimentoCacau(
    configAtual: ConfigRendimentoCacau,
    onDismiss: () -> Unit,
    onSalvar: (ConfigRendimentoCacau) -> Unit
) {
    var indiceTexto by remember(configAtual) { mutableStateOf(configAtual.indiceFruto.toInt().toString()) }
    var plantasHaTexto by remember(configAtual) { mutableStateOf(configAtual.plantasPorHa.toString()) }
    var kgArrobaTexto by remember(configAtual) { mutableStateOf(configAtual.kgPorArroba.toInt().toString()) }
    var descontarDoencas by remember(configAtual) { mutableStateOf(configAtual.descontarDoencas) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Parâmetros Agronômicos (@ / ha)", fontWeight = FontWeight.Bold, color = CacauVerdeEscuro)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Padrão CEPEC (SPEC-003): 22 frutos sadios = 1 kg de amêndoas secas; 1 @ = 15 kg; 1.111 plantas/ha (3×3 m).",
                    fontSize = 12.sp,
                    color = CacauTextoSecundario
                )

                OutlinedTextField(
                    value = indiceTexto,
                    onValueChange = { indiceTexto = it.filter { ch -> ch.isDigit() || ch == '.' } },
                    label = { Text("Índice de Fruto (frutos p/ 1 kg seco)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(20, 22, 25).forEach { preset ->
                        OutlinedButton(
                            onClick = { indiceTexto = preset.toString() },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("IF $preset", fontSize = 11.sp)
                        }
                    }
                }

                OutlinedTextField(
                    value = plantasHaTexto,
                    onValueChange = { plantasHaTexto = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Estande (plantas / hectare)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(1000, 1111, 1250).forEach { preset ->
                        OutlinedButton(
                            onClick = { plantasHaTexto = preset.toString() },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("$preset/ha", fontSize = 11.sp)
                        }
                    }
                }

                OutlinedTextField(
                    value = kgArrobaTexto,
                    onValueChange = { kgArrobaTexto = it.filter { ch -> ch.isDigit() || ch == '.' } },
                    label = { Text("Quilos por arroba (@)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Descontar frutos doentes", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text("Calcula @/kg apenas com frutos sadios", fontSize = 11.sp, color = CacauTextoSecundario)
                    }
                    Switch(checked = descontarDoencas, onCheckedChange = { descontarDoencas = it })
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val idx = indiceTexto.toDoubleOrNull()?.coerceIn(5.0, 80.0) ?: ConfigRendimentoCacau.INDICE_FRUTO_PADRAO
                    val plHa = plantasHaTexto.toIntOrNull()?.coerceIn(100, 5000) ?: ConfigRendimentoCacau.PLANTAS_POR_HA_PADRAO
                    val kgArb = kgArrobaTexto.toDoubleOrNull()?.coerceIn(5.0, 50.0) ?: ConfigRendimentoCacau.KG_POR_ARROBA
                    onSalvar(
                        ConfigRendimentoCacau(
                            indiceFruto = idx,
                            kgPorArroba = kgArb,
                            plantasPorHa = plHa,
                            descontarDoencas = descontarDoencas
                        )
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = CacauVerdeEscuro)
            ) {
                Text("Aplicar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}
