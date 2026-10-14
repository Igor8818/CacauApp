package com.example.myapplication.userinterface.screens

import android.app.Activity
import android.net.Uri
import android.view.WindowManager
import android.widget.Toast
import androidx.camera.core.CameraControl
import androidx.camera.core.ImageCapture
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.data.DetectionResult
import com.example.myapplication.data.ModelConfig
import com.example.myapplication.data.RotaColeta
import com.example.myapplication.userinterface.components.AvisoModeloAusente
import com.example.myapplication.userinterface.util.CameraWithAI
import com.example.myapplication.userinterface.util.buildDetectorHelper
import com.example.myapplication.userinterface.util.tirarFoto
import java.util.Locale
import kotlin.math.roundToInt
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.example.myapplication.data.comoObservacao
import com.example.myapplication.data.ocorrencias

/**
 * Modo Rota (FieldBook) para monitoramento guiado e sequencial com busca e filtros.
 */
@Composable
fun TelaModoFieldBook(
    ensaio: String,
    rotaCompleta: List<RotaColeta>,
    modelConfig: ModelConfig,
    gpsPrecisao: Float,
    temFixGps: Boolean,
    onAbrirModelo: () -> Unit,
    onCarregarPlanilhaClick: () -> Unit,
    onLimparRotaClick: () -> Unit,
    onSaveColetaRota: (Uri, RotaColeta, String, String, Int) -> Unit
) {
    val context = LocalContext.current
    // Retoma sempre da primeira planta pendente (ex.: ao reabrir o app no meio da rota)
    var indiceAtual by rememberSaveable { mutableIntStateOf(rotaCompleta.indexOfFirst { !it.coletado }.coerceAtLeast(0)) }
    var mostrarMapa by remember { mutableStateOf(false) }
    var confirmarPlantaMorta by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    var cameraAtiva by remember { mutableStateOf(false) }
    var deteccoesAtuais by remember { mutableStateOf<List<DetectionResult>>(emptyList()) }
    var cameraControlRef by remember { mutableStateOf<CameraControl?>(null) }
    var lanternaLigada by remember { mutableStateOf(false) }

    // Foto capturada pendente de revisão
    var fotoParaRevisaoUri by remember { mutableStateOf<Uri?>(null) }

    val imageCapture = remember { ImageCapture.Builder().build() }

    val detectorHelper = remember(modelConfig) {
        buildDetectorHelper(context, modelConfig) { deteccoesAtuais = it }
    }
    DisposableEffect(detectorHelper) {
        onDispose {
            detectorHelper?.fechar()
        }
    }

    val activity = context as? Activity
    DisposableEffect(cameraAtiva) {
        if (cameraAtiva) {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    if (rotaCompleta.isEmpty()) {
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Default.FolderOpen, null, modifier = Modifier.size(64.dp), tint = Color.Gray)
            Spacer(Modifier.height(16.dp))
            Text("Nenhuma rota carregada", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(
                "Selecione o arquivo CSV de planejamento para iniciar o monitoramento sequencial no campo.",
                color = Color.Gray,
                modifier = Modifier.padding(16.dp)
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onCarregarPlanilhaClick,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                modifier = Modifier.height(56.dp)
            ) {
                Text("SELECIONAR PLANILHA DE ROTA", fontWeight = FontWeight.Bold)
            }
        }
    } else {
        val indiceSeguro = indiceAtual.coerceIn(0, rotaCompleta.size - 1)
        val pontoAtual = rotaCompleta[indiceSeguro]
        val isPontoValido = pontoAtual.clone.isNotBlank()
        val stats = remember(rotaCompleta) { rotaCompleta.estatisticas() }
        val plantaMorta = pontoAtual.coletado && pontoAtual.qtdFrutos == "Não Coletável"

        // Ocorrências fitossanitárias marcadas para a planta atual (reinicia ao trocar de planta)
        var ocorrenciasSel by remember(indiceSeguro, pontoAtual.observacao) {
            mutableStateOf(pontoAtual.observacao.ocorrencias().toSet())
        }
        val observacaoAtual = ocorrenciasSel.toList().comoObservacao()

        // Após salvar: plantas novas seguem para a próxima pendente; refações apenas avançam um passo.
        val avancarAposSalvar: (Boolean) -> Unit = { jaEstavaColetada ->
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            cameraAtiva = false
            val restantes = rotaCompleta.count { !it.coletado } - (if (jaEstavaColetada) 0 else 1)
            if (restantes <= 0) Toast.makeText(context, "Rota finalizada! Todas as plantas foram coletadas.", Toast.LENGTH_LONG).show()
            val destino = if (jaEstavaColetada) (indiceSeguro + 1).takeIf { it < rotaCompleta.size }
                          else proximoPendente(rotaCompleta, indiceSeguro)
            destino?.let { indiceAtual = it }
        }

        when {
            fotoParaRevisaoUri != null -> {
                RevisaoCapturaScreen(
                    fotoUri = fotoParaRevisaoUri!!,
                    plantId = "${pontoAtual.clone}_P${pontoAtual.parcela}_Pl${pontoAtual.planta}",
                    modelConfig = modelConfig,
                    detectorHelper = detectorHelper,
                    onConfirmar = { contagemIa, contagemFinal ->
                        onSaveColetaRota(fotoParaRevisaoUri!!, pontoAtual, contagemFinal.toString(), observacaoAtual, contagemIa)
                        fotoParaRevisaoUri = null
                        avancarAposSalvar(pontoAtual.coletado)
                    },
                    onRefazer = {
                        fotoParaRevisaoUri = null
                    }
                )
            }

            !cameraAtiva || detectorHelper == null -> {
                Column(Modifier.fillMaxSize().padding(16.dp)) {
                    // Área rolável: cabeçalho, estatísticas, planta atual e ocorrências
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Sessão em Rota: $ensaio", fontWeight = FontWeight.Bold, color = Color(0xFF1B5E20), modifier = Modifier.weight(1f))
                            TextButton(onClick = { onLimparRotaClick(); indiceAtual = 0 }, colors = ButtonDefaults.textButtonColors(contentColor = Color.Red)) {
                                Icon(Icons.Default.DeleteSweep, null); Spacer(Modifier.width(4.dp)); Text("Trocar Planilha")
                            }
                        }
                        Spacer(Modifier.height(8.dp))

                        Surface(shape = RoundedCornerShape(8.dp), color = Color(0xFFE8F5E9), modifier = Modifier.fillMaxWidth()) {
                            Row(
                                Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.SmartToy, null, tint = Color(0xFF2E7D32), modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("${modelConfig.nomeExibicao} · ${(modelConfig.threshold * 100).roundToInt()}%", fontSize = 12.sp, color = Color(0xFF2E7D32))
                                }
                                Text(
                                    if (temFixGps) "GPS ±${"%.1f".format(Locale.US, gpsPrecisao)}m" else "Sem GPS",
                                    fontSize = 11.sp,
                                    color = if (temFixGps) Color(0xFF2E7D32) else Color(0xFFE65100)
                                )
                            }
                        }

                        if (detectorHelper == null) {
                            Spacer(Modifier.height(8.dp))
                            AvisoModeloAusente(modelConfig.nomeExibicao, onAbrirModelo)
                        }

                        Spacer(Modifier.height(10.dp))
                        PainelEstatisticasRota(stats)
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { stats.percentual },
                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                            color = Color(0xFF2E7D32)
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Planta ${indiceSeguro + 1} de ${rotaCompleta.size}", fontSize = 13.sp)
                            Row {
                                TextButton(onClick = { mostrarMapa = true }, contentPadding = PaddingValues(horizontal = 8.dp)) {
                                    Icon(Icons.Default.GridView, null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Mapa da rota", fontSize = 12.sp)
                                }
                                val proximaPendente = rotaCompleta.indexOfFirst { !it.coletado }
                                if (proximaPendente >= 0 && proximaPendente != indiceSeguro) {
                                    TextButton(
                                        onClick = { indiceAtual = proximaPendente },
                                        contentPadding = PaddingValues(horizontal = 8.dp)
                                    ) {
                                        Text("Pendente (#${proximaPendente + 1})", fontSize = 12.sp, color = Color(0xFF2E7D32))
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = when {
                                    plantaMorta -> Color(0xFFFFEBEE)
                                    pontoAtual.coletado -> Color(0xFFE8F5E9)
                                    else -> Color(0xFFFFF3E0)
                                }
                            )
                        ) {
                            Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    when {
                                        plantaMorta -> "PLANTA MORTA / AUSENTE (EDITÁVEL)"
                                        pontoAtual.coletado -> "PLANTA CONCLUÍDA (EDITÁVEL)"
                                        else -> "PRÓXIMA PLANTA DA ROTA"
                                    },
                                    fontWeight = FontWeight.Bold,
                                    color = when {
                                        plantaMorta -> Color(0xFFC62828)
                                        pontoAtual.coletado -> Color(0xFF2E7D32)
                                        else -> Color(0xFFE65100)
                                    }
                                )
                                Spacer(Modifier.height(12.dp))
                                Text("CLONE / VARIEDADE", fontSize = 12.sp, color = Color.Gray)
                                Text(
                                    if (isPontoValido) pontoAtual.clone else "[IDENTIFICADOR VAZIO]",
                                    fontSize = 32.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (isPontoValido) Color.Black else Color.Red
                                )
                                Spacer(Modifier.height(12.dp))
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                                        Text("PARCELA", fontSize = 12.sp, color = Color.Gray)
                                        Text(pontoAtual.parcela, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                                        Text("PLANTA ID", fontSize = 12.sp, color = Color.Gray)
                                        Text("${pontoAtual.planta}", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                                if (pontoAtual.coletado && !plantaMorta) {
                                    Spacer(Modifier.height(8.dp))
                                    Text("Resultado Atual: ${pontoAtual.qtdFrutos} frutos", fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                                }
                            }
                        }

                        Spacer(Modifier.height(12.dp))
                        SeletorOcorrencias(
                            selecionadas = ocorrenciasSel,
                            onAlternar = { rotulo ->
                                ocorrenciasSel = if (rotulo in ocorrenciasSel) ocorrenciasSel - rotulo else ocorrenciasSel + rotulo
                            }
                        )
                        Spacer(Modifier.height(8.dp))
                    }

                    OutlinedButton(
                        onClick = { confirmarPlantaMorta = true },
                        enabled = isPontoValido,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).height(52.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.ReportProblem, null); Spacer(Modifier.width(8.dp)); Text("PLANTA MORTA / SECA / AUSENTE")
                    }

                    val podeVoltar = indiceSeguro > 0
                    val podeAvancar = indiceSeguro < rotaCompleta.size - 1
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { if (podeVoltar) indiceAtual = indiceSeguro - 1 }, enabled = podeVoltar, modifier = Modifier.size(72.dp)) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBackIos, "Planta anterior", modifier = Modifier.size(44.dp), tint = if (podeVoltar) Color(0xFF2E7D32) else Color.Gray)
                        }
                        Button(
                            onClick = { cameraAtiva = true },
                            enabled = isPontoValido && detectorHelper != null,
                            modifier = Modifier.height(72.dp).weight(1f).padding(horizontal = 8.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                        ) {
                            Icon(Icons.Default.PhotoCamera, null); Text(if (pontoAtual.coletado) " REFAZER FOTO" else " ABRIR CÂMERA", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                        IconButton(onClick = { if (podeAvancar) indiceAtual = indiceSeguro + 1 }, enabled = podeAvancar, modifier = Modifier.size(72.dp)) {
                            Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, "Próxima planta", modifier = Modifier.size(44.dp), tint = if (podeAvancar) Color(0xFF2E7D32) else Color.Gray)
                        }
                    }
                }
            }

            else -> {
                Box(Modifier.fillMaxSize()) {
                    CameraWithAI(
                        helper = detectorHelper,
                        capture = imageCapture,
                        onCameraControlPronto = { control, _ ->
                            cameraControlRef = control
                        },
                        onResultsUpdated = { deteccoesAtuais = it }
                    )

                    val listaFiltrada = deteccoesAtuais.filter { det ->
                        val label = det.categories.firstOrNull()?.label?.lowercase() ?: ""
                        val score = det.categories.firstOrNull()?.score ?: 0f
                        score >= (modelConfig.threshold - 0.02f) &&
                                (modelConfig.labelsFiltro.isEmpty() || detectorHelper.numClasses <= 1 || modelConfig.labelsFiltro.any { f -> label.contains(f.lowercase()) })
                    }
                    val contagemFrutos = listaFiltrada.size

                    Canvas(Modifier.fillMaxSize()) {
                        val imgW = detectorHelper.lastBitmapWidth.coerceAtLeast(1f)
                        val imgH = detectorHelper.lastBitmapHeight.coerceAtLeast(1f)
                        val scale = maxOf(size.width / imgW, size.height / imgH)
                        val offsetX = (size.width - imgW * scale) / 2f
                        val offsetY = (size.height - imgH * scale) / 2f

                        listaFiltrada.forEach { det ->
                            val rect = det.boundingBox
                            val left = rect.left * scale + offsetX
                            val top = rect.top * scale + offsetY
                            val w = rect.width() * scale
                            val h = rect.height() * scale

                            drawRect(
                                color = Color(0x99000000),
                                topLeft = androidx.compose.ui.geometry.Offset(left - 1.dp.toPx(), top - 1.dp.toPx()),
                                size = androidx.compose.ui.geometry.Size(w + 2.dp.toPx(), h + 2.dp.toPx()),
                                style = Stroke(width = 4.dp.toPx())
                            )
                            drawRect(
                                color = Color(0xFF00E676),
                                topLeft = androidx.compose.ui.geometry.Offset(left, top),
                                size = androidx.compose.ui.geometry.Size(w, h),
                                style = Stroke(width = 2.5.dp.toPx())
                            )
                        }
                    }

                    Surface(
                        Modifier.statusBarsPadding().align(Alignment.TopCenter).padding(top = 10.dp),
                        color = Color(0xDD1E130D),
                        shape = RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x66FFA000))
                    ) {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${pontoAtual.clone} · Frutos: $contagemFrutos", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text("${modelConfig.nomeArquivoModelo} · ${(modelConfig.threshold * 100).roundToInt()}% min · ${detectorHelper.ultimaLatenciaMs}ms", color = Color(0xFFD7CCC8), fontSize = 11.sp)
                        }
                    }

                    IconButton(
                        onClick = {
                            lanternaLigada = !lanternaLigada
                            cameraControlRef?.enableTorch(lanternaLigada)
                        },
                        modifier = Modifier.align(Alignment.TopStart).padding(16.dp).size(56.dp)
                    ) {
                        Icon(
                            if (lanternaLigada) Icons.Default.FlashOn else Icons.Default.FlashOff,
                            "Lanterna",
                            tint = if (lanternaLigada) Color.Yellow else Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    IconButton(
                        onClick = { cameraAtiva = false },
                        modifier = Modifier.align(Alignment.TopEnd).padding(16.dp).size(56.dp)
                    ) {
                        Icon(Icons.Default.Cancel, "Fechar câmera", tint = Color.White, modifier = Modifier.size(44.dp))
                    }

                    ExtendedFloatingActionButton(
                        onClick = {
                            val idParaArquivo = "${pontoAtual.clone}_P${pontoAtual.parcela}_Pl${pontoAtual.planta}"
                            tirarFoto(
                                context = context,
                                capture = imageCapture,
                                id = idParaArquivo,
                                onSucesso = { uri ->
                                    fotoParaRevisaoUri = uri
                                },
                                onErro = { erro ->
                                    Toast.makeText(context, erro, Toast.LENGTH_SHORT).show()
                                }
                            )
                        },
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp),
                        containerColor = Color(0xFF2E7D32),
                        text = { Text("FOTOGRAFAR (${pontoAtual.planta})", fontWeight = FontWeight.Bold) },
                        icon = { Icon(Icons.Default.PhotoCamera, null) }
                    )
                }
            }
        }

        if (mostrarMapa) {
            MapaDaRotaDialog(
                rota = rotaCompleta,
                indiceAtual = indiceSeguro,
                onSelecionar = { indiceAtual = it; mostrarMapa = false },
                onFechar = { mostrarMapa = false }
            )
        }

        if (confirmarPlantaMorta) {
            AlertDialog(
                onDismissRequest = { confirmarPlantaMorta = false },
                title = { Text("Marcar planta morta / ausente?", fontWeight = FontWeight.Bold) },
                text = {
                    Text(
                        "${pontoAtual.clone} · Parcela ${pontoAtual.parcela} · Planta ${pontoAtual.planta}" +
                            if (pontoAtual.coletado) "\n\nAtenção: isto substituirá a coleta já registrada (${pontoAtual.qtdFrutos})." else ""
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            confirmarPlantaMorta = false
                            onSaveColetaRota(Uri.EMPTY, pontoAtual, "Não Coletável", observacaoAtual, 0)
                            avancarAposSalvar(pontoAtual.coletado)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828))
                    ) { Text("Confirmar") }
                },
                dismissButton = { TextButton(onClick = { confirmarPlantaMorta = false }) { Text("Cancelar") } }
            )
        }
    }
}
