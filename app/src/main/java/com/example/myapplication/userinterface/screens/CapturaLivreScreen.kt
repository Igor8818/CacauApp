package com.example.myapplication.userinterface.screens

import android.app.Activity
import android.net.Uri
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraControl
import androidx.camera.core.ImageCapture
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.R
import com.example.myapplication.data.DetectionResult
import com.example.myapplication.data.ModelConfig
import com.example.myapplication.data.SituacaoPlanta
import com.example.myapplication.userinterface.components.AvisoModeloAusente
import com.example.myapplication.userinterface.util.CameraWithAI
import com.example.myapplication.userinterface.util.buildDetectorHelper
import com.example.myapplication.userinterface.util.tirarFoto
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Tela de Coleta Livre limpa, compacta e focada na agilidade de campo:
 * - Cabeçalho compacto + Seletor de Clone;
 * - Parcela e Planta lado a lado (Grid de 2 colunas);
 * - Botão principal em destaque "FOTOGRAFAR PLANTA (IA)" + 4 botões de Registro Rápido (1 toque).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelaPrincipalCapturaOtimizada(
    ensaio: String,
    label1: String, label2: String, label3: String,
    isNum2: Boolean, isNum3: Boolean,
    onConfigChanged: (String, String, String, Boolean, Boolean) -> Unit,
    cloneVal: String, onCloneChange: (String) -> Unit,
    historicoClones: List<String>,
    parcelaNum: Int, onParcelaNumChange: (Int) -> Unit,
    parcelaTxt: String, onParcelaTxtChange: (String) -> Unit,
    plantaNum: Int, onPlantaNumChange: (Int) -> Unit,
    plantaTxt: String, onPlantaTxtChange: (String) -> Unit,
    modelConfig: ModelConfig,
    gpsPrecisao: Float,
    temFixGps: Boolean,
    onAbrirModelo: () -> Unit,
    onTrocarCampanha: (() -> Unit)? = null,
    onModoTelaCheiaChange: (Boolean) -> Unit = {},
    jaExistePlantId: (String) -> Boolean,
    onSave: (Uri, String, String, Int, Int, DadosAgronomicosPlanta) -> Unit
) {
    val context = LocalContext.current
    var cameraAtiva by rememberSaveable { mutableStateOf(false) }
    var fotoParaRevisaoUriStr by rememberSaveable { mutableStateOf<String?>(null) }
    val fotoParaRevisaoUri = remember(fotoParaRevisaoUriStr) {
        fotoParaRevisaoUriStr?.takeIf { it.isNotBlank() }?.let { Uri.parse(it) }
    }

    var menuClonesExpandido by remember { mutableStateOf(false) }
    var mostrarDialogLabels by remember { mutableStateOf(false) }
    var deteccoesAtuais by remember { mutableStateOf<List<DetectionResult>>(emptyList()) }
    var deteccoesNoClique by remember { mutableStateOf<List<DetectionResult>>(emptyList()) }
    var larguraFrameNoClique by remember { mutableFloatStateOf(1f) }
    var alturaFrameNoClique by remember { mutableFloatStateOf(1f) }

    var cameraControlRef by remember { mutableStateOf<CameraControl?>(null) }
    var lanternaLigada by remember { mutableStateOf(false) }

    val memoriaCorPorClone = remember { mutableStateMapOf<String, String>() }

    val imageCapture = remember {
        ImageCapture.Builder()
            .setTargetAspectRatio(AspectRatio.RATIO_4_3)
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

    val emTelaCheia = cameraAtiva || fotoParaRevisaoUri != null
    LaunchedEffect(emTelaCheia) {
        onModoTelaCheiaChange(emTelaCheia)
    }
    DisposableEffect(Unit) {
        onDispose {
            onModoTelaCheiaChange(false)
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

    val pExibicao = if (isNum2) parcelaNum.toString() else parcelaTxt
    val plExibicao = if (isNum3) plantaNum.toString() else plantaTxt
    val plantIdAtual = "${cloneVal}_${label2}${pExibicao}_${label3}${plExibicao}"
    val idJaExiste = cloneVal.isNotBlank() && jaExistePlantId(plantIdAtual)

    var dadosAgro by remember(plantIdAtual) {
        mutableStateOf(DadosAgronomicosPlanta(corFruto = memoriaCorPorClone[cloneVal].orEmpty()))
    }

    val isCloneValido = cloneVal.isNotBlank() &&
        (if (isNum2) true else parcelaTxt.isNotBlank()) &&
        (if (isNum3) true else plantaTxt.isNotBlank())

    BackHandler(enabled = fotoParaRevisaoUri != null) {
        fotoParaRevisaoUriStr = null
    }

    BackHandler(enabled = cameraAtiva && fotoParaRevisaoUri == null) {
        cameraAtiva = false
    }

    BackHandler(enabled = mostrarDialogLabels) {
        mostrarDialogLabels = false
    }

    BackHandler(enabled = menuClonesExpandido) {
        menuClonesExpandido = false
    }

    val salvarSituacaoRapida: (SituacaoPlanta) -> Unit = { sit ->
        val idSalvo = plantIdAtual
        val isNormalZero = sit == SituacaoPlanta.NORMAL
        val diag = if (isNormalZero) "0" else sit.rotulo
        val dados = dadosAgro.copy(
            sitPla = sit.rotulo,
            nfPequenos = 0,
            nfMedios = 0,
            nfAdultos = 0,
            nfMaduros = 0
        )
        onSave(Uri.EMPTY, idSalvo, diag, 0, 0, dados)
    }

    when {
        fotoParaRevisaoUri != null -> {
            val idCapturado = plantIdAtual
            val uriCapturada = fotoParaRevisaoUri
            RevisaoCapturaScreen(
                fotoUri = uriCapturada,
                plantId = idCapturado,
                modelConfig = modelConfig,
                detectorHelper = detectorHelper,
                deteccoesIniciais = deteccoesNoClique,
                larguraFrameVivo = larguraFrameNoClique,
                alturaFrameVivo = alturaFrameNoClique,
                dadosAgronomicos = dadosAgro,
                onDadosAgronomicosChange = { dadosAgro = it },
                onConfirmar = { contagemIa, contagemFinal ->
                    if (dadosAgro.corFruto.isNotBlank()) {
                        memoriaCorPorClone[cloneVal] = dadosAgro.corFruto
                    }
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
                    onSave(uriCapturada, idCapturado, diagTextoFinal, contagemIa, if (ehInativaNaFoto) 0 else contagemFinal, dadosFinais)
                    fotoParaRevisaoUriStr = null
                    cameraAtiva = false
                },
                onRefazer = {
                    fotoParaRevisaoUriStr = null
                }
            )
        }

        !cameraAtiva -> {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // 1. Cabeçalho Compacto
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Image(
                                painter = painterResource(id = R.drawable.app_logo),
                                contentDescription = null,
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .border(1.dp, Color(0xFFE0D5C1), RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(
                                modifier = if (onTrocarCampanha != null) {
                                    Modifier.clickable { onTrocarCampanha() }
                                } else Modifier
                            ) {
                                Text("Campanha: $ensaio", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = Color(0xFF1B5E20))
                                Text(
                                    "${modelConfig.nomeExibicao} · ${(modelConfig.threshold * 100).roundToInt()}% · " +
                                        if (temFixGps) "GPS ±${"%.1f".format(Locale.US, gpsPrecisao)}m" else "Sem GPS",
                                    fontSize = 11.sp,
                                    color = Color(0xFF5D4037)
                                )
                            }
                        }
                        IconButton(onClick = { mostrarDialogLabels = true }) {
                            Icon(Icons.Default.Settings, "Configurar colunas", tint = Color(0xFF2E7D32))
                        }
                    }

                    if (detectorHelper == null) {
                        AvisoModeloAusente(modelConfig.nomeExibicao, onAbrirModelo)
                    }

                    if (idJaExiste) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFFFF3E0),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Info, null, tint = Color(0xFFE65100), modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Planta já registrada na sessão. Salvar atualizará o registro.", fontSize = 11.sp, color = Color(0xFFE65100))
                            }
                        }
                    }

                    // 2. Seletor de Clone Limpo no Topo
                    ExposedDropdownMenuBox(
                        expanded = menuClonesExpandido,
                        onExpandedChange = { menuClonesExpandido = !menuClonesExpandido }
                    ) {
                        OutlinedTextField(
                            value = cloneVal,
                            onValueChange = onCloneChange,
                            label = { Text(label1) },
                            placeholder = { Text("Ex: CCN-51, SCP-16005...") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(MenuAnchorType.PrimaryEditable, true),
                            leadingIcon = { Icon(Icons.Default.Eco, null, tint = Color(0xFF2E7D32)) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = menuClonesExpandido) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )
                        if (historicoClones.isNotEmpty()) {
                            ExposedDropdownMenu(
                                expanded = menuClonesExpandido,
                                onDismissRequest = { menuClonesExpandido = false }
                            ) {
                                historicoClones.forEach { nome ->
                                    DropdownMenuItem(
                                        text = { Text(nome) },
                                        onClick = { onCloneChange(nome); menuClonesExpandido = false }
                                    )
                                }
                            }
                        }
                    }

                    // 3. Parcela e Planta LADO A LADO (Grid de 2 Colunas)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(Modifier.weight(1f)) {
                            if (isNum2) {
                                ContadorNumericoCampo(
                                    rotulo = label2,
                                    valor = parcelaNum,
                                    onValorChange = onParcelaNumChange,
                                    onResetar = { onParcelaNumChange(1) },
                                    compacto = true
                                )
                            } else {
                                OutlinedTextField(
                                    value = parcelaTxt,
                                    onValueChange = onParcelaTxtChange,
                                    label = { Text(label2) },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp)
                                )
                            }
                        }

                        Box(Modifier.weight(1f)) {
                            if (isNum3) {
                                ContadorNumericoCampo(
                                    rotulo = label3,
                                    valor = plantaNum,
                                    onValorChange = onPlantaNumChange,
                                    onResetar = { onPlantaNumChange(1) },
                                    compacto = true
                                )
                            } else {
                                OutlinedTextField(
                                    value = plantaTxt,
                                    onValueChange = onPlantaTxtChange,
                                    label = { Text(label3) },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp)
                                )
                            }
                        }
                    }
                }

                // 4. Área de Ação Clara e Hierárquica (Parte Inferior)
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Ação Principal (Destaque Máximo)
                    Button(
                        onClick = { cameraAtiva = true },
                        enabled = isCloneValido,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(58.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                    ) {
                        Icon(Icons.Default.PhotoCamera, null, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(10.dp))
                        Text("FOTOGRAFAR PLANTA (IA)", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                    }

                    // Ação Secundária (1 toque salva e avança)
                    GradeBotoesRegistroRapido(
                        habilitado = isCloneValido,
                        onSemFrutos = { salvarSituacaoRapida(SituacaoPlanta.NORMAL) },
                        onCavalo = { salvarSituacaoRapida(SituacaoPlanta.CAVALO) },
                        onMorta = { salvarSituacaoRapida(SituacaoPlanta.MORTA) },
                        onErrada = { salvarSituacaoRapida(SituacaoPlanta.ERRADA) },
                        onNaoColetavel = { salvarSituacaoRapida(SituacaoPlanta.NAO_COLETAVEL) }
                    )
                }
            }
        }

        else -> {
            // Câmera em Tela Cheia com fundo preto sólido (sem vazamento)
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
                    deteccoesAtuais.filter { det ->
                        val label = det.categories.firstOrNull()?.label?.lowercase() ?: ""
                        val score = det.categories.firstOrNull()?.score ?: 0f
                        score >= (modelConfig.threshold - 0.02f) &&
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(10.dp).background(
                                    if (contagemFrutos > 0) Color(0xFF00E676) else Color(0xFFFFB300),
                                    CircleShape
                                )
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Frutos detectados: $contagemFrutos",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }
                        Text(
                            "$cloneVal · $label2 $pExibicao · $label3 $plExibicao",
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
                        .padding(12.dp)
                        .size(52.dp)
                ) {
                    Icon(
                        if (lanternaLigada) Icons.Default.FlashOn else Icons.Default.FlashOff,
                        "Lanterna",
                        tint = if (lanternaLigada) Color.Yellow else Color.White,
                        modifier = Modifier.size(34.dp)
                    )
                }

                IconButton(
                    onClick = { cameraAtiva = false },
                    modifier = Modifier
                        .statusBarsPadding()
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
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
                            id = plantIdAtual,
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
                    text = { Text("FOTOGRAFAR E REVISAR", fontWeight = FontWeight.Bold) },
                    icon = { Icon(Icons.Default.PhotoCamera, null) }
                )
            }
        }
    }

    if (mostrarDialogLabels) {
        var t1 by remember { mutableStateOf(label1) }
        var t2 by remember { mutableStateOf(label2) }
        var t3 by remember { mutableStateOf(label3) }
        var n2 by remember { mutableStateOf(isNum2) }
        var n3 by remember { mutableStateOf(isNum3) }

        AlertDialog(
            onDismissRequest = { mostrarDialogLabels = false },
            title = { Text("Configurar Variáveis da Tabela", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("Escolha o nome e o tipo de entrada das colunas:", fontSize = 14.sp, color = Color.Gray)
                    OutlinedTextField(value = t1, onValueChange = { t1 = it }, label = { Text("Coluna 1 (Ex: Clone)") })
                    HorizontalDivider()
                    Column {
                        OutlinedTextField(value = t2, onValueChange = { t2 = it }, label = { Text("Coluna 2 (Ex: Parcela)") })
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                            Checkbox(checked = n2, onCheckedChange = { n2 = it })
                            Text("Usar como Contador Numérico", fontSize = 13.sp)
                        }
                    }
                    HorizontalDivider()
                    Column {
                        OutlinedTextField(value = t3, onValueChange = { t3 = it }, label = { Text("Coluna 3 (Ex: Planta)") })
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                            Checkbox(checked = n3, onCheckedChange = { n3 = it })
                            Text("Usar como Contador Numérico", fontSize = 13.sp)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { onConfigChanged(t1, t2, t3, n2, n3); mostrarDialogLabels = false },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                ) { Text("Salvar") }
            },
            dismissButton = {
                TextButton(onClick = { mostrarDialogLabels = false }) { Text("Cancelar") }
            }
        )
    }
}

/**
 * Componente unificado de contador numérico para campo (Parcela, Planta, etc.).
 * Suporta modo compacto de 2 colunas lado a lado, mantendo botões - e + bem visíveis
 * e toque no número central para edição direta.
 */
@Composable
fun ContadorNumericoCampo(
    rotulo: String,
    valor: Int,
    onValorChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    corTema: Color = Color(0xFF2E7D32),
    onResetar: (() -> Unit)? = null,
    compacto: Boolean = false
) {
    var mostrarDialogEditar by remember { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF9FBF7)),
        border = BorderStroke(1.dp, Color(0xFFE0E8DC))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = if (compacto) 10.dp else 14.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = rotulo.uppercase(),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF5D4037)
                )

                if (valor > 1 && onResetar != null) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFFECEFF1),
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onResetar() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.RestartAlt,
                                contentDescription = "Voltar para 1",
                                tint = Color(0xFF455A64),
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(Modifier.width(2.dp))
                            Text(
                                "1",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF455A64)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(2.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = { if (valor > 1) onValorChange(valor - 1) },
                    enabled = valor > 1,
                    modifier = Modifier.size(if (compacto) 42.dp else 50.dp)
                ) {
                    Icon(
                        Icons.Default.RemoveCircle,
                        contentDescription = "Diminuir $rotulo",
                        tint = if (valor > 1) Color(0xFFC62828) else Color(0xFFCFD8DC),
                        modifier = Modifier.size(if (compacto) 36.dp else 42.dp)
                    )
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { mostrarDialogEditar = true }
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "$valor",
                        fontSize = if (compacto) 28.sp else 34.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = corTema
                    )
                    Text(
                        text = "editar",
                        fontSize = 9.sp,
                        color = Color.Gray
                    )
                }

                IconButton(
                    onClick = { onValorChange(valor + 1) },
                    modifier = Modifier.size(if (compacto) 42.dp else 50.dp)
                ) {
                    Icon(
                        Icons.Default.AddCircle,
                        contentDescription = "Aumentar $rotulo",
                        tint = corTema,
                        modifier = Modifier.size(if (compacto) 36.dp else 42.dp)
                    )
                }
            }
        }
    }

    if (mostrarDialogEditar) {
        var textoDigitado by remember { mutableStateOf(valor.toString()) }
        AlertDialog(
            onDismissRequest = { mostrarDialogEditar = false },
            title = { Text("Definir $rotulo", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Informe o número desejado para $rotulo:", fontSize = 13.sp, color = Color.Gray)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = textoDigitado,
                        onValueChange = { novo ->
                            if (novo.all { it.isDigit() }) {
                                textoDigitado = novo
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val num = textoDigitado.toIntOrNull()
                        if (num != null && num >= 1) {
                            onValorChange(num)
                        }
                        mostrarDialogEditar = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = corTema)
                ) {
                    Text("Confirmar")
                }
            },
            dismissButton = {
                TextButton(onClick = { mostrarDialogEditar = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}
