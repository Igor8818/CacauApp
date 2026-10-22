package com.example.myapplication.userinterface.screens

import android.app.Activity
import android.net.Uri
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.camera.core.CameraControl
import androidx.camera.core.ImageCapture
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.data.*
import com.example.myapplication.userinterface.components.AvisoModeloAusente
import com.example.myapplication.userinterface.util.CameraWithAI
import com.example.myapplication.userinterface.util.buildDetectorHelper
import com.example.myapplication.userinterface.util.tirarFoto
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Modo Rota (FieldBook) simplificado (Progressive Disclosure):
 * - Permite iniciar com Planilha Excel (.xlsx) / CSV, Rota CEPLAC Embutida (TC-1801) ou Criação Rápida sem arquivo;
 * - Card principal grande e limpo com progresso (`Planta X de Y` e setas grandes `◀ Anterior` / `Próxima ▶`),
 *   Clone em destaque, Bloco · Parcela · Planta e Badge de Histórico legível em português;
 * - Preserva a contagem de frutos da câmera ao vivo na tela pós-foto;
 * - Botão gigante `📷 FOTOGRAFAR PLANTA NORMAL` + 4 botões de 1 toque (`Sem Frutos (0)`, `Cavalo`, `Morta`, `Errada`).
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
    onModoTelaCheiaChange: (Boolean) -> Unit = {},
    onSaveColetaRota: (
        uri: Uri,
        ponto: RotaColeta,
        contagemFinal: String,
        dadosAgro: DadosAgronomicosPlanta,
        contagemIaRevisada: Int
    ) -> Unit
) {
    val context = LocalContext.current
    var indiceAtual by rememberSaveable { mutableIntStateOf(rotaCompleta.indexOfFirst { !it.coletado }.coerceAtLeast(0)) }
    var mostrarMapa by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    var cameraAtiva by rememberSaveable { mutableStateOf(false) }
    var deteccoesAtuais by remember { mutableStateOf<List<DetectionResult>>(emptyList()) }
    var deteccoesNoClique by remember { mutableStateOf<List<DetectionResult>>(emptyList()) }
    var larguraFrameNoClique by remember { mutableFloatStateOf(1f) }
    var alturaFrameNoClique by remember { mutableFloatStateOf(1f) }
    var cameraControlRef by remember { mutableStateOf<CameraControl?>(null) }
    var lanternaLigada by remember { mutableStateOf(false) }

    // Memória de CorFruto por Clone para agilizar parcelas do mesmo clone
    val memoriaCorPorClone = remember { mutableStateMapOf<String, String>() }

    // Foto capturada pendente de revisão
    var fotoParaRevisaoUriStr by rememberSaveable { mutableStateOf<String?>(null) }
    val fotoParaRevisaoUri = remember(fotoParaRevisaoUriStr) {
        fotoParaRevisaoUriStr?.takeIf { it.isNotBlank() }?.let { Uri.parse(it) }
    }

    val emTelaCheia = cameraAtiva || fotoParaRevisaoUri != null
    LaunchedEffect(emTelaCheia) {
        onModoTelaCheiaChange(emTelaCheia)
    }
    DisposableEffect(Unit) {
        onDispose { onModoTelaCheiaChange(false) }
    }

    val imageCapture = remember {
        ImageCapture.Builder()
            .setResolutionSelector(com.example.myapplication.userinterface.util.seletorCaptura4x3())
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .build()
    }

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

    BackHandler(enabled = fotoParaRevisaoUri != null) {
        fotoParaRevisaoUriStr = null
    }

    BackHandler(enabled = cameraAtiva && fotoParaRevisaoUri == null) {
        cameraAtiva = false
    }

    BackHandler(enabled = mostrarMapa) {
        mostrarMapa = false
    }

    if (rotaCompleta.isEmpty()) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Default.FolderOpen, null, modifier = Modifier.size(64.dp), tint = Color(0xFF2E7D32))
            Spacer(Modifier.height(14.dp))
            Text("Nenhuma rota carregada", fontWeight = FontWeight.ExtraBold, fontSize = 19.sp, color = Color(0xFF1B5E20))
            Spacer(Modifier.height(8.dp))
            Text(
                "Selecione a planilha ordenada do experimento (formato Excel .xlsx ou .csv) para iniciar o monitoramento sequencial das plantas.",
                color = Color.DarkGray,
                textAlign = TextAlign.Center,
                fontSize = 14.sp
            )
            Spacer(Modifier.height(24.dp))

            Button(
                onClick = onCarregarPlanilhaClick,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B5E20)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.UploadFile, null)
                Spacer(Modifier.width(10.dp))
                Text("SELECIONAR PLANILHA (.XLSX OU .CSV)", fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
            }
        }
    } else {
        val indiceSeguro = indiceAtual.coerceIn(0, rotaCompleta.size - 1)
        val pontoAtual = rotaCompleta[indiceSeguro]
        val isPontoValido = pontoAtual.clone.isNotBlank()
        val stats = remember(rotaCompleta) { rotaCompleta.estatisticas() }
        val plantaInativa = pontoAtual.coletado && pontoAtual.isInativa

        val corSugerida = remember(pontoAtual.clone, rotaCompleta) {
            memoriaCorPorClone[pontoAtual.clone]
                ?: rotaCompleta.firstOrNull { it.clone == pontoAtual.clone && it.corFruto.isNotBlank() }?.corFruto
                ?: ""
        }

        var dadosAgro by remember(indiceSeguro, pontoAtual) {
            mutableStateOf(DadosAgronomicosPlanta.deRota(pontoAtual, corSugerida))
        }

        val avancarAposSalvar: (Boolean) -> Unit = { jaEstavaColetada ->
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            cameraAtiva = false
            if (dadosAgro.corFruto.isNotBlank()) {
                memoriaCorPorClone[pontoAtual.clone] = dadosAgro.corFruto
            }
            val restantes = rotaCompleta.count { !it.coletado } - (if (jaEstavaColetada) 0 else 1)
            if (restantes <= 0) {
                Toast.makeText(context, "Rota finalizada! Todas as plantas foram coletadas.", Toast.LENGTH_LONG).show()
            }
            val destino = if (jaEstavaColetada) (indiceSeguro + 1).takeIf { it < rotaCompleta.size }
                          else proximoPendente(rotaCompleta, indiceSeguro)
            destino?.let { indiceAtual = it }
        }

        val salvarSituacaoInativaDireto: (SituacaoPlanta) -> Unit = { sit ->
            val jaColetada = pontoAtual.coletado
            val dadosInativos = dadosAgro.copy(
                sitPla = sit.rotulo,
                nfPequenos = 0,
                nfMedios = 0,
                nfAdultos = 0,
                nfMaduros = 0
            )
            onSaveColetaRota(Uri.EMPTY, pontoAtual, sit.rotulo, dadosInativos, 0)
            avancarAposSalvar(jaColetada)
        }

        when {
            fotoParaRevisaoUri != null -> {
                RevisaoCapturaScreen(
                    fotoUri = fotoParaRevisaoUri,
                    plantId = "${pontoAtual.clone} · Parcela ${pontoAtual.parcela} · Planta ${pontoAtual.planta}",
                    modelConfig = modelConfig,
                    detectorHelper = detectorHelper,
                    deteccoesIniciais = deteccoesNoClique,
                    larguraFrameVivo = larguraFrameNoClique,
                    alturaFrameVivo = alturaFrameNoClique,
                    dadosAgronomicos = dadosAgro,
                    onDadosAgronomicosChange = { dadosAgro = it },
                    onConfirmar = { contagemIa, contagemFinal ->
                        val uriConfirmada = fotoParaRevisaoUri
                        val jaColetada = pontoAtual.coletado
                        val ehInativaNaFoto = SituacaoPlanta.ehInativa(dadosAgro.sitPla)
                        val diagTextoFinal = if (ehInativaNaFoto) dadosAgro.sitPla else contagemFinal.toString()
                        val dadosFinais = if (ehInativaNaFoto) {
                            dadosAgro.copy(nfPequenos = 0, nfMedios = 0, nfAdultos = 0, nfMaduros = 0)
                        } else {
                            dadosAgro.copy(
                                sitPla = SituacaoPlanta.NORMAL.rotulo,
                                nfAdultos = if (dadosAgro.somaPorteFrutos == 0) contagemFinal else dadosAgro.nfAdultos
                            )
                        }
                        if (dadosFinais.corFruto.isNotBlank()) {
                            memoriaCorPorClone[pontoAtual.clone] = dadosFinais.corFruto
                        }
                        onSaveColetaRota(
                            uriConfirmada,
                            pontoAtual,
                            diagTextoFinal,
                            dadosFinais,
                            contagemIa
                        )
                        fotoParaRevisaoUriStr = null
                        cameraAtiva = false
                        avancarAposSalvar(jaColetada)
                    },
                    onRefazer = {
                        fotoParaRevisaoUriStr = null
                        cameraAtiva = true
                    }
                )
            }

            !cameraAtiva -> {
                val podeVoltar = indiceSeguro > 0
                val podeAvancar = indiceSeguro < rotaCompleta.size - 1

                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // ── Parte Superior: Cabeçalho + Progresso + Card Principal Limpo ──
                    Column(
                        Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Cabeçalho Compacto
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFFE8F5E9),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        "Rota: ${pontoAtual.ensaio.ifBlank { ensaio }}",
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 15.sp,
                                        color = Color(0xFF1B5E20)
                                    )
                                    Text(
                                        "${stats.coletadas} de ${stats.total} avaliadas (${stats.pendentes} pendentes)",
                                        fontSize = 11.sp,
                                        color = Color(0xFF2E7D32),
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    TextButton(
                                        onClick = { mostrarMapa = true },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Icon(Icons.Default.GridView, null, tint = Color(0xFF1B5E20), modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Mapa", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1B5E20))
                                    }
                                    IconButton(
                                        onClick = { onLimparRotaClick(); indiceAtual = 0 },
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Icon(Icons.Default.FolderOpen, "Trocar Rota", tint = Color(0xFF5D4037), modifier = Modifier.size(20.dp))
                                    }
                                }
                            }
                        }

                        if (detectorHelper == null) {
                            AvisoModeloAusente(modelConfig.nomeExibicao, onAbrirModelo)
                        }

                        LinearProgressIndicator(
                            progress = { stats.percentual },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(7.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = Color(0xFF2E7D32)
                        )

                        // ── Navegação Sequencial Grande: ◀ Anterior | Planta X de Y | Próxima ▶ ──
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = { if (podeVoltar) indiceAtual = indiceSeguro - 1 },
                                enabled = podeVoltar,
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Anterior", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    "Planta ${indiceSeguro + 1} de ${rotaCompleta.size}",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 15.sp,
                                    color = Color(0xFF1B5E20)
                                )
                                val proximaPendente = rotaCompleta.indexOfFirst { !it.coletado }
                                if (proximaPendente >= 0 && proximaPendente != indiceSeguro) {
                                    TextButton(
                                        onClick = { indiceAtual = proximaPendente },
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                                        modifier = Modifier.height(22.dp)
                                    ) {
                                        Text(
                                            "Ir p/ Pendente (#${proximaPendente + 1})",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFE65100)
                                        )
                                    }
                                }
                            }

                            OutlinedButton(
                                onClick = { if (podeAvancar) indiceAtual = indiceSeguro + 1 },
                                enabled = podeAvancar,
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Text("Próxima", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Spacer(Modifier.width(4.dp))
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, null, modifier = Modifier.size(18.dp))
                            }
                        }

                        // ── Card Principal Grande e Limpo da Planta Atual ──
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(
                                1.5.dp,
                                when {
                                    plantaInativa -> Color(0xFFEF9A9A)
                                    pontoAtual.coletado -> Color(0xFFA5D6A7)
                                    else -> Color(0xFFFFCC80)
                                }
                            ),
                            colors = CardDefaults.cardColors(
                                containerColor = when {
                                    plantaInativa -> Color(0xFFFFEBEE)
                                    pontoAtual.coletado -> Color(0xFFE8F5E9)
                                    else -> Color(0xFFFFF8E1)
                                }
                            )
                        ) {
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = when {
                                        plantaInativa -> Color(0xFFC62828)
                                        pontoAtual.coletado -> Color(0xFF2E7D32)
                                        else -> Color(0xFFE65100)
                                    }
                                ) {
                                    Text(
                                        when {
                                            plantaInativa -> "AVALIADA: ${pontoAtual.situacaoEfetiva.uppercase()}"
                                            pontoAtual.coletado -> "CONCLUÍDA: ${pontoAtual.qtdFrutos} FRUTOS"
                                            else -> "PLANTA ATUAL DA FILA"
                                        },
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                    )
                                }

                                Spacer(Modifier.height(10.dp))
                                Text(
                                    if (isPontoValido) "Clone: ${pontoAtual.clone}" else "[CLONE VAZIO]",
                                    fontSize = 26.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (isPontoValido) Color(0xFF1B5E20) else Color.Red,
                                    textAlign = TextAlign.Center
                                )

                                Spacer(Modifier.height(10.dp))
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    if (pontoAtual.bloco.isNotBlank()) {
                                        Surface(
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(10.dp),
                                            color = Color.White.copy(alpha = 0.85f)
                                        ) {
                                            Column(
                                                Modifier.padding(vertical = 8.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                Text("BLOCO", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                                                Text(pontoAtual.bloco, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF3E2723))
                                            }
                                        }
                                    }
                                    Surface(
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(10.dp),
                                        color = Color.White.copy(alpha = 0.85f)
                                    ) {
                                        Column(
                                            Modifier.padding(vertical = 8.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text("PARCELA", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                                            Text(pontoAtual.parcela, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF3E2723))
                                        }
                                    }
                                    Surface(
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(10.dp),
                                        color = Color.White.copy(alpha = 0.85f)
                                    ) {
                                        Column(
                                            Modifier.padding(vertical = 8.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text("PLANTA", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                                            Text("${pontoAtual.planta}", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF1B5E20))
                                        }
                                    }
                                }

                                // Badge de Histórico legível (em destaque amarelo/laranja quando houver falha anterior)
                                val resumoHist = pontoAtual.resumoHistoricoLegivel
                                val alertaFalha = pontoAtual.alertaHistoricoFalha
                                if (resumoHist != null) {
                                    Spacer(Modifier.height(10.dp))
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (alertaFalha != null) Color(0xFFFFE082) else Color.White.copy(alpha = 0.75f),
                                        border = BorderStroke(
                                            1.dp,
                                            if (alertaFalha != null) Color(0xFFF57C00) else Color(0xFFA5D6A7)
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Center
                                        ) {
                                            Icon(
                                                if (alertaFalha != null) Icons.Default.WarningAmber else Icons.Default.CheckCircleOutline,
                                                contentDescription = null,
                                                tint = if (alertaFalha != null) Color(0xFFE65100) else Color(0xFF2E7D32),
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(Modifier.width(6.dp))
                                            Text(
                                                resumoHist,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (alertaFalha != null) Color(0xFFBF360C) else Color(0xFF2E7D32)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // ── Área de Ação Inferior (Hierárquica e Limpa) ──
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { cameraAtiva = true },
                            enabled = isPontoValido,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(58.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B5E20))
                        ) {
                            Icon(Icons.Default.PhotoCamera, null, modifier = Modifier.size(26.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(
                                if (pontoAtual.coletado) "REFAZER FOTO DA PLANTA" else "FOTOGRAFAR PLANTA NORMAL",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }

                        GradeBotoesRegistroRapido(
                            habilitado = isPontoValido,
                            onSemFrutos = {
                                val jaColetada = pontoAtual.coletado
                                val dadosZero = dadosAgro.copy(
                                    sitPla = SituacaoPlanta.NORMAL.rotulo,
                                    nfPequenos = 0,
                                    nfMedios = 0,
                                    nfAdultos = 0,
                                    nfMaduros = 0
                                )
                                onSaveColetaRota(Uri.EMPTY, pontoAtual, "0", dadosZero, 0)
                                avancarAposSalvar(jaColetada)
                            },
                            onCavalo = { salvarSituacaoInativaDireto(SituacaoPlanta.CAVALO) },
                            onMorta = { salvarSituacaoInativaDireto(SituacaoPlanta.MORTA) },
                            onErrada = { salvarSituacaoInativaDireto(SituacaoPlanta.ERRADA) },
                            onNaoColetavel = { salvarSituacaoInativaDireto(SituacaoPlanta.NAO_COLETAVEL) }
                        )
                    }
                }
            }

            else -> {
                // ── Câmera em Tela Cheia com Fundo Preto (Sem Vazamento) ──
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                ) {
                    if (detectorHelper != null) {
                        CameraWithAI(
                            helper = detectorHelper,
                            capture = imageCapture,
                            onCameraControlPronto = { control, _ ->
                                cameraControlRef = control
                            },
                            onResultsUpdated = { deteccoesAtuais = it }
                        )
                    }

                    val listaFiltrada = if (detectorHelper != null) {
                        val limiarCamera = minOf(modelConfig.threshold, 0.25f) - 0.02f
                        deteccoesAtuais.filter { det ->
                            val label = det.categories.firstOrNull()?.label?.lowercase() ?: ""
                            val score = det.categories.firstOrNull()?.score ?: 0f
                            score >= limiarCamera &&
                                (modelConfig.labelsFiltro.isEmpty() || detectorHelper.numClasses <= 1 || modelConfig.labelsFiltro.any { f -> label.contains(f.lowercase()) })
                        }
                    } else emptyList()
                    val contagemFrutos = listaFiltrada.size

                    if (detectorHelper != null) {
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
                    }

                    Surface(
                        Modifier
                            .statusBarsPadding()
                            .align(Alignment.TopCenter)
                            .padding(top = 12.dp),
                        color = Color(0xDD1E130D),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, Color(0x66FFA000))
                    ) {
                        Column(
                            Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "Frutos detectados: $contagemFrutos",
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 16.sp
                            )
                            Text(
                                "${pontoAtual.clone} · Parcela ${pontoAtual.parcela} · Planta ${pontoAtual.planta}",
                                color = Color(0xFFD7CCC8),
                                fontSize = 11.sp
                            )
                        }
                    }

                    IconButton(
                        onClick = {
                            lanternaLigada = !lanternaLigada
                            cameraControlRef?.enableTorch(lanternaLigada)
                        },
                        modifier = Modifier
                            .statusBarsPadding()
                            .align(Alignment.TopStart)
                            .padding(16.dp)
                            .size(52.dp)
                    ) {
                        Icon(
                            if (lanternaLigada) Icons.Default.FlashOn else Icons.Default.FlashOff,
                            "Lanterna",
                            tint = if (lanternaLigada) Color.Yellow else Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    IconButton(
                        onClick = { cameraAtiva = false },
                        modifier = Modifier
                            .statusBarsPadding()
                            .align(Alignment.TopEnd)
                            .padding(16.dp)
                            .size(52.dp)
                    ) {
                        Icon(Icons.Default.Cancel, "Fechar câmera", tint = Color.White, modifier = Modifier.size(40.dp))
                    }

                    ExtendedFloatingActionButton(
                        onClick = {
                            deteccoesNoClique = listaFiltrada
                            larguraFrameNoClique = detectorHelper?.lastBitmapWidth ?: 1f
                            alturaFrameNoClique = detectorHelper?.lastBitmapHeight ?: 1f
                            tirarFoto(
                                context = context,
                                capture = imageCapture,
                                id = pontoAtual.idEfetivo,
                                onSucesso = { uri ->
                                    fotoParaRevisaoUriStr = uri.toString()
                                },
                                onErro = { erro ->
                                    Toast.makeText(context, erro, Toast.LENGTH_SHORT).show()
                                }
                            )
                        },
                        modifier = Modifier
                            .navigationBarsPadding()
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 28.dp),
                        containerColor = Color(0xFF2E7D32),
                        contentColor = Color.White,
                        text = {
                            Text(
                                "FOTOGRAFAR E REVISAR (Planta ${pontoAtual.planta})",
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        },
                        icon = { Icon(Icons.Default.PhotoCamera, null, tint = Color.White) }
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
    }
}
