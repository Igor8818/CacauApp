package com.example.myapplication

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraInfo
import androidx.camera.core.ImageCapture
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.myapplication.data.DetectionResult
import com.example.myapplication.data.ModelConfig
import com.example.myapplication.data.RegistroCacau
import com.example.myapplication.data.RotaColeta
import com.example.myapplication.userinterface.TelaConfiguracaoModelo
import com.example.myapplication.userinterface.util.*
import com.example.myapplication.viewmodel.AppViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AppCacauCEPEC()
                }
            }
        }
    }
}

@SuppressLint("MissingPermission")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppCacauCEPEC(viewModel: AppViewModel = viewModel()) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // ── Estados coletados reativamente do AppViewModel ─────────────────────────
    val listaRegistros by viewModel.registros.collectAsStateWithLifecycle()
    val rotaPreCarregada by viewModel.rota.collectAsStateWithLifecycle()
    val modelConfig by viewModel.modelConfig.collectAsStateWithLifecycle()
    val ensaioNome by viewModel.ensaioNome.collectAsStateWithLifecycle()
    val configConcluida by viewModel.configConcluida.collectAsStateWithLifecycle()
    val historicoValores1 by viewModel.historicoClones.collectAsStateWithLifecycle()

    val labelVariavel1 by viewModel.label1.collectAsStateWithLifecycle()
    val labelVariavel2 by viewModel.label2.collectAsStateWithLifecycle()
    val labelVariavel3 by viewModel.label3.collectAsStateWithLifecycle()
    val tipoNumVariavel2 by viewModel.isNum2.collectAsStateWithLifecycle()
    val tipoNumVariavel3 by viewModel.isNum3.collectAsStateWithLifecycle()

    val valorVariavel1 by viewModel.valor1.collectAsStateWithLifecycle()
    val valorNumVariavel2 by viewModel.valorNum2.collectAsStateWithLifecycle()
    val valorNumVariavel3 by viewModel.valorNum3.collectAsStateWithLifecycle()
    val valorTxtVariavel2 by viewModel.valorTxt2.collectAsStateWithLifecycle()
    val valorTxtVariavel3 by viewModel.valorTxt3.collectAsStateWithLifecycle()

    val latAtual by viewModel.latAtual.collectAsStateWithLifecycle()
    val lngAtual by viewModel.lngAtual.collectAsStateWithLifecycle()
    val gpsPrecisao by viewModel.gpsPrecisaoM.collectAsStateWithLifecycle()
    val temFixGps by viewModel.temFixGps.collectAsStateWithLifecycle()
    val altitudeAtual by viewModel.altitude.collectAsStateWithLifecycle()

    var abaSelecionada by rememberSaveable { mutableIntStateOf(0) }
    var mostrarTelaModelo by remember { mutableStateOf(false) }
    var mostrarDialogNovoEnsaio by remember { mutableStateOf(false) }

    var mostrarDialogMapeamento by remember { mutableStateOf(false) }
    val colunasEncontradas = remember { mutableStateListOf<String>() }
    var uriArquivoPendente by remember { mutableStateOf<Uri?>(null) }
    var avisoModeloExibido by rememberSaveable { mutableStateOf(false) }

    // ── Gerenciamento do Ciclo de Vida do GPS ─────────────────────────────────
    val launcherPermissoes = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissoes ->
        if (permissoes[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissoes[Manifest.permission.ACCESS_COARSE_LOCATION] == true) {
            viewModel.iniciarGps()
        }
    }

    LaunchedEffect(Unit) {
        launcherPermissoes.launch(
            arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
        viewModel.iniciarGps()
    }

    // Leva o usuário à tela de modelo caso o build inicial não contenha o .tflite
    LaunchedEffect(configConcluida) {
        if (configConcluida && !avisoModeloExibido && !modeloDisponivel(context, modelConfig)) {
            avisoModeloExibido = true
            Toast.makeText(context, "Nenhum modelo de IA encontrado. Importe um arquivo .tflite.", Toast.LENGTH_LONG).show()
            mostrarTelaModelo = true
        }
    }

    val launcherArquivoCSV = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            val cabecalho = lerApenasCabecalho(context, it)
            if (cabecalho.isNotEmpty()) {
                var idxClone = cabecalho.indexOfFirst { c -> c.lowercase() == "clone" || c.lowercase() == "v_id" }
                if (idxClone == -1) idxClone = cabecalho.indexOfFirst { c -> c.lowercase().contains("clone") || c.lowercase().contains("variedade") }
                var idxParcela = cabecalho.indexOfFirst { c -> c.lowercase() == "parcela" || c.lowercase() == "rep" }
                if (idxParcela == -1) idxParcela = cabecalho.indexOfFirst { c -> c.lowercase() == "bloco" || c.lowercase().contains("parcela") || c.lowercase().contains("rep") }
                var idxPlanta = cabecalho.indexOfFirst { c -> c.lowercase() == "planta" || c.lowercase() == "p_id" }
                if (idxPlanta == -1) idxPlanta = cabecalho.indexOfFirst { c -> c.lowercase() == "id" || c.lowercase().contains("planta") }

                if (idxClone != -1 && idxParcela != -1 && idxPlanta != -1) {
                    val lista = processarCSVComIndices(context, it, idxClone, idxParcela, idxPlanta)
                    viewModel.carregarRota(lista)
                    Toast.makeText(context, "${lista.size} plantas carregadas na rota!", Toast.LENGTH_SHORT).show()
                } else {
                    colunasEncontradas.clear()
                    colunasEncontradas.addAll(cabecalho)
                    uriArquivoPendente = it
                    mostrarDialogMapeamento = true
                }
            } else {
                Toast.makeText(context, "Planilha vazia ou com formato inválido.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ── ROTEAMENTO DE TELAS ───────────────────────────────────────────────────
    when {
        mostrarTelaModelo -> {
            TelaConfiguracaoModelo(
                configAtual = modelConfig,
                onSalvar = { novaConfig ->
                    viewModel.atualizarModelConfig(novaConfig)
                    mostrarTelaModelo = false
                    Toast.makeText(context, "Modelo \"${novaConfig.nomeExibicao}\" ativado!", Toast.LENGTH_SHORT).show()
                },
                onVoltar = { mostrarTelaModelo = false }
            )
        }

        !configConcluida -> {
            TelaCheckInSimples(
                ensaio = ensaioNome,
                onEnsaioChange = { viewModel.setEnsaioNome(it) },
                exibirBotaoVoltar = listaRegistros.isNotEmpty() || rotaPreCarregada.isNotEmpty(),
                onVoltarClick = { viewModel.setConfigConcluida(true) },
                onContinuar = {
                    if (listaRegistros.isNotEmpty()) {
                        mostrarDialogNovoEnsaio = true
                    } else {
                        viewModel.iniciarNovoEnsaio(ensaioNome)
                    }
                }
            )

            if (mostrarDialogNovoEnsaio) {
                AlertDialog(
                    onDismissRequest = { mostrarDialogNovoEnsaio = false },
                    title = { Text("Iniciar Novo Ensaio?", fontWeight = FontWeight.Bold) },
                    text = {
                        Text(
                            "Você possui ${listaRegistros.size} registros salvos do ensaio anterior. Deseja exportar a planilha antes de iniciar a nova campanha?"
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                exportarPlanilhaCEPEC(
                                    context,
                                    listaRegistros,
                                    ensaioNome,
                                    labelVariavel1,
                                    labelVariavel2,
                                    labelVariavel3
                                )
                                viewModel.limparRegistrosCampanha()
                                viewModel.iniciarNovoEnsaio(ensaioNome)
                                mostrarDialogNovoEnsaio = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                        ) {
                            Text("Exportar e Iniciar")
                        }
                    },
                    dismissButton = {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = {
                                viewModel.limparRegistrosCampanha()
                                viewModel.iniciarNovoEnsaio(ensaioNome)
                                mostrarDialogNovoEnsaio = false
                            }) {
                                Text("Iniciar Direto", color = Color.Red)
                            }
                            TextButton(onClick = { mostrarDialogNovoEnsaio = false }) {
                                Text("Cancelar")
                            }
                        }
                    }
                )
            }
        }

        else -> {
            Scaffold(
                snackbarHost = { SnackbarHost(snackbarHostState) },
                bottomBar = {
                    NavigationBar(containerColor = Color.White) {
                        NavigationBarItem(
                            selected = abaSelecionada == 0,
                            onClick = { abaSelecionada = 0 },
                            icon = { Icon(Icons.Default.PhotoCamera, null) },
                            label = { Text("Coleta Livre") }
                        )
                        NavigationBarItem(
                            selected = abaSelecionada == 1,
                            onClick = { abaSelecionada = 1 },
                            icon = { Icon(Icons.Default.Route, null) },
                            label = { Text("Modo Rota") }
                        )
                        NavigationBarItem(
                            selected = abaSelecionada == 2,
                            onClick = { abaSelecionada = 2 },
                            icon = {
                                BadgedBox(badge = {
                                    if (listaRegistros.isNotEmpty()) {
                                        Badge { Text("${listaRegistros.size}") }
                                    }
                                }) {
                                    Icon(Icons.Default.Collections, null)
                                }
                            },
                            label = { Text("Galeria") }
                        )
                        NavigationBarItem(
                            selected = false,
                            onClick = { mostrarTelaModelo = true },
                            icon = { Icon(Icons.Default.SmartToy, null) },
                            label = { Text("Modelo IA") }
                        )
                        NavigationBarItem(
                            selected = false,
                            onClick = { viewModel.setConfigConcluida(false) },
                            icon = { Icon(Icons.Default.Settings, null) },
                            label = { Text("Ensaio") }
                        )
                    }
                }
            ) { padding ->
                Box(Modifier.padding(padding)) {
                    val pExibicao = if (tipoNumVariavel2) valorNumVariavel2.toString() else valorTxtVariavel2
                    val plExibicao = if (tipoNumVariavel3) valorNumVariavel3.toString() else valorTxtVariavel3

                    when (abaSelecionada) {
                        0 -> TelaPrincipalCapturaOtimizada(
                            ensaio = ensaioNome,
                            label1 = labelVariavel1, label2 = labelVariavel2, label3 = labelVariavel3,
                            isNum2 = tipoNumVariavel2, isNum3 = tipoNumVariavel3,
                            onConfigChanged = { l1, l2, l3, n2, n3 ->
                                viewModel.atualizarLabels(l1, l2, l3, n2, n3)
                            },
                            cloneVal = valorVariavel1, onCloneChange = { viewModel.setValor1(it) },
                            historicoClones = historicoValores1,
                            parcelaNum = valorNumVariavel2, onParcelaNumChange = { viewModel.setValorNum2(it) },
                            parcelaTxt = valorTxtVariavel2, onParcelaTxtChange = { viewModel.setValorTxt2(it) },
                            plantaNum = valorNumVariavel3, onPlantaNumChange = { viewModel.setValorNum3(it) },
                            plantaTxt = valorTxtVariavel3, onPlantaTxtChange = { viewModel.setValorTxt3(it) },
                            modelConfig = modelConfig,
                            gpsPrecisao = gpsPrecisao,
                            temFixGps = temFixGps,
                            onAbrirModelo = { mostrarTelaModelo = true },
                            jaExistePlantId = { id -> viewModel.existePlantId(id) },
                            onSave = { uri, contagemIA ->
                                val data = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date())
                                val idCompleto = "${valorVariavel1}_${labelVariavel2}${pExibicao}_${labelVariavel3}${plExibicao}"
                                val plantIntVal = if (tipoNumVariavel3) valorNumVariavel3 else 1
                                val contagemInt = contagemIA.toIntOrNull() ?: 0

                                val reg = RegistroCacau(
                                    uri = uri,
                                    plantId = idCompleto,
                                    ensaioManual = ensaioNome,
                                    parcelaManual = pExibicao,
                                    diagnostico = contagemIA,
                                    data = data,
                                    numPlanta = plantIntVal,
                                    lat = latAtual,
                                    lng = lngAtual,
                                    contagemIa = contagemInt,
                                    contagemFinal = contagemInt,
                                    modelo = modelConfig.nomeExibicao,
                                    threshold = modelConfig.threshold,
                                    precisaoGpsM = gpsPrecisao,
                                    altitude = altitudeAtual
                                )
                                viewModel.salvarRegistro(reg)
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("Planta $idCompleto salva ($contagemIA frutos)")
                                }
                            }
                        )

                        1 -> TelaModoFieldBook(
                            ensaio = ensaioNome,
                            rotaCompleta = rotaPreCarregada,
                            modelConfig = modelConfig,
                            gpsPrecisao = gpsPrecisao,
                            temFixGps = temFixGps,
                            onAbrirModelo = { mostrarTelaModelo = true },
                            onCarregarPlanilhaClick = { launcherArquivoCSV.launch("*/*") },
                            onLimparRotaClick = { viewModel.limparRota() },
                            onSaveColetaRota = { uri, ponto, contagemIA ->
                                val data = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date())
                                val idCompleto = "${ponto.clone}_P${ponto.parcela}_Pl${ponto.planta}"
                                val contagemInt = contagemIA.toIntOrNull() ?: 0

                                val reg = RegistroCacau(
                                    uri = uri,
                                    plantId = idCompleto,
                                    ensaioManual = ensaioNome,
                                    parcelaManual = ponto.parcela,
                                    diagnostico = contagemIA,
                                    data = data,
                                    numPlanta = ponto.planta,
                                    lat = latAtual,
                                    lng = lngAtual,
                                    contagemIa = contagemInt,
                                    contagemFinal = contagemInt,
                                    modelo = modelConfig.nomeExibicao,
                                    threshold = modelConfig.threshold,
                                    precisaoGpsM = gpsPrecisao,
                                    altitude = altitudeAtual
                                )
                                viewModel.salvarRegistro(reg)
                                viewModel.marcarPontoRota(ponto, coletado = true, qtdFrutos = contagemIA)
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("Ponto ${ponto.clone} P${ponto.parcela} salvo")
                                }
                            }
                        )

                        2 -> TelaGaleria(
                            registros = listaRegistros,
                            ensaio = ensaioNome,
                            l1 = labelVariavel1,
                            l2 = labelVariavel2,
                            l3 = labelVariavel3,
                            onExcluirRegistro = { reg ->
                                viewModel.excluirRegistro(reg)
                                coroutineScope.launch {
                                    val res = snackbarHostState.showSnackbar(
                                        message = "Registro ${reg.plantId} excluído",
                                        actionLabel = "Desfazer",
                                        duration = SnackbarDuration.Short
                                    )
                                    if (res == SnackbarResult.ActionPerformed) {
                                        viewModel.desfazerExclusao()
                                    }
                                }
                            },
                            onEditarContagem = { plantId, novaContagem ->
                                viewModel.editarContagemRegistro(plantId, novaContagem)
                            }
                        )
                    }
                }
            }
        }
    }

    if (mostrarDialogMapeamento && uriArquivoPendente != null) {
        var selClone by remember { mutableStateOf(colunasEncontradas.firstOrNull() ?: "") }
        var selParcela by remember { mutableStateOf(colunasEncontradas.firstOrNull() ?: "") }
        var selPlanta by remember { mutableStateOf(colunasEncontradas.firstOrNull() ?: "") }

        AlertDialog(
            onDismissRequest = { mostrarDialogMapeamento = false },
            title = { Text("Vincular Colunas da Planilha", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Selecione os títulos correspondentes da sua tabela:", fontSize = 14.sp, color = Color.Gray)
                    Text("Coluna do Clone / Variedade:", fontWeight = FontWeight.Medium)
                    SeletorDropdownSimples(opcoes = colunasEncontradas, selecionado = selClone, onSelecionadoChange = { selClone = it })
                    Text("Coluna da Parcela / Bloco:", fontWeight = FontWeight.Medium)
                    SeletorDropdownSimples(opcoes = colunasEncontradas, selecionado = selParcela, onSelecionadoChange = { selParcela = it })
                    Text("Coluna do ID da Planta:", fontWeight = FontWeight.Medium)
                    SeletorDropdownSimples(opcoes = colunasEncontradas, selecionado = selPlanta, onSelecionadoChange = { selPlanta = it })
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val idxClone   = colunasEncontradas.indexOf(selClone)
                        val idxParcela = colunasEncontradas.indexOf(selParcela)
                        val idxPlanta  = colunasEncontradas.indexOf(selPlanta)
                        val lista = processarCSVComIndices(context, uriArquivoPendente!!, idxClone, idxParcela, idxPlanta)
                        viewModel.carregarRota(lista)
                        mostrarDialogMapeamento = false
                        uriArquivoPendente = null
                        Toast.makeText(context, "${lista.size} plantas vinculadas!", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                ) { Text("Confirmar Rota") }
            },
            dismissButton = {
                TextButton(onClick = { mostrarDialogMapeamento = false; uriArquivoPendente = null }) { Text("Cancelar") }
            }
        )
    }
}

// ════════════════════════════════════════════════════
// TELA PRINCIPAL DE CAPTURA (COLETA LIVRE)
// ════════════════════════════════════════════════════
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
    jaExistePlantId: (String) -> Boolean,
    onSave: (Uri, String) -> Unit
) {
    val context = LocalContext.current
    var cameraAtiva by remember { mutableStateOf(false) }
    var menuClonesExpandido by remember { mutableStateOf(false) }
    var mostrarDialogLabels by remember { mutableStateOf(false) }
    var deteccoesAtuais by remember { mutableStateOf<List<DetectionResult>>(emptyList()) }

    var cameraControlRef by remember { mutableStateOf<CameraControl?>(null) }
    var lanternaLigada by remember { mutableStateOf(false) }

    val imageCapture = remember { ImageCapture.Builder().build() }

    // Cria detector helper e fecha devidamente no descarte
    val detectorHelper = remember(modelConfig) {
        buildDetectorHelper(context, modelConfig) { deteccoesAtuais = it }
    }
    DisposableEffect(detectorHelper) {
        onDispose {
            detectorHelper?.fechar()
        }
    }

    // Mantém a tela acesa durante a captura na câmera
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

    val isCloneValido = cloneVal.isNotBlank() &&
            (if (isNum2) true else parcelaTxt.isNotBlank()) &&
            (if (isNum3) true else plantaTxt.isNotBlank())

    if (!cameraAtiva || detectorHelper == null) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Sessão Manual: $ensaio", fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                IconButton(onClick = { mostrarDialogLabels = true }) {
                    Icon(Icons.Default.Settings, null, tint = Color(0xFF2E7D32))
                }
            }

            // Barra de Status: Modelo e GPS
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFFE8F5E9),
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) {
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (temFixGps) Icons.Default.GpsFixed else Icons.Default.GpsNotFixed,
                            null,
                            tint = if (temFixGps) Color(0xFF2E7D32) else Color(0xFFE65100),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            if (temFixGps) "±${"%.1f".format(Locale.US, gpsPrecisao)}m" else "Sem GPS",
                            fontSize = 11.sp,
                            color = if (temFixGps) Color(0xFF2E7D32) else Color(0xFFE65100)
                        )
                    }
                }
            }

            if (detectorHelper == null) {
                Spacer(Modifier.height(8.dp))
                AvisoModeloAusente(modelConfig.nomeExibicao, onAbrirModelo)
            }

            if (idJaExiste) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFFFF3E0),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Info, null, tint = Color(0xFFE65100), modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Planta já registrada na sessão. Salvar atualizará a contagem.", fontSize = 11.sp, color = Color(0xFFE65100))
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            ExposedDropdownMenuBox(expanded = menuClonesExpandido, onExpandedChange = { menuClonesExpandido = !menuClonesExpandido }) {
                OutlinedTextField(
                    value = cloneVal, onValueChange = onCloneChange, label = { Text(label1) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryEditable, true),
                    leadingIcon = { Icon(Icons.Default.Settings, null, tint = Color(0xFF2E7D32)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = menuClonesExpandido) }
                )
                if (historicoClones.isNotEmpty()) {
                    ExposedDropdownMenu(expanded = menuClonesExpandido, onDismissRequest = { menuClonesExpandido = false }) {
                        historicoClones.forEach { nome ->
                            DropdownMenuItem(text = { Text(nome) }, onClick = { onCloneChange(nome); menuClonesExpandido = false })
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            if (isNum2) {
                Text(label2, fontSize = 12.sp, color = Color.Gray)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                    IconButton(onClick = { if (parcelaNum > 1) onParcelaNumChange(parcelaNum - 1) }, modifier = Modifier.size(80.dp)) {
                        Icon(Icons.Default.RemoveCircle, "Diminuir $label2", tint = Color.Red, modifier = Modifier.size(64.dp))
                    }
                    Text("$parcelaNum", fontSize = 40.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 20.dp))
                    IconButton(onClick = { onParcelaNumChange(parcelaNum + 1) }, modifier = Modifier.size(80.dp)) {
                        Icon(Icons.Default.AddCircle, "Aumentar $label2", tint = Color(0xFF2E7D32), modifier = Modifier.size(64.dp))
                    }
                }
            } else {
                OutlinedTextField(value = parcelaTxt, onValueChange = onParcelaTxtChange, label = { Text(label2) }, modifier = Modifier.fillMaxWidth(), leadingIcon = { Icon(Icons.Default.Layers, null, tint = Color(0xFF2E7D32)) })
            }

            Spacer(Modifier.height(24.dp))

            if (isNum3) {
                Card(modifier = Modifier.fillMaxWidth().height(100.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F8E9))) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(label3.uppercase(), fontSize = 12.sp)
                            Text("$plantaNum", fontSize = 48.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF1B5E20))
                        }
                    }
                }
            } else {
                OutlinedTextField(value = plantaTxt, onValueChange = onPlantaTxtChange, label = { Text(label3) }, modifier = Modifier.fillMaxWidth(), leadingIcon = { Icon(Icons.Default.PinDrop, null, tint = Color(0xFF2E7D32)) })
            }

            Spacer(Modifier.weight(1f))

            OutlinedButton(
                onClick = { onSave(Uri.EMPTY, "Não Coletável") },
                enabled = isCloneValido,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).height(56.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.ReportProblem, null); Spacer(Modifier.width(8.dp)); Text("PLANTA NÃO COLETÁVEL")
            }
            Button(
                onClick = { cameraAtiva = true },
                enabled = isCloneValido && detectorHelper != null,
                modifier = Modifier.fillMaxWidth().height(72.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
            ) {
                Icon(Icons.Default.PhotoCamera, null); Spacer(Modifier.width(8.dp)); Text("ABRIR CÂMERA", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    } else {
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
                score >= modelConfig.threshold &&
                        (modelConfig.labelsFiltro.isEmpty() || modelConfig.labelsFiltro.any { f -> label.contains(f.lowercase()) })
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
                    drawRect(
                        color = Color.Green,
                        topLeft = androidx.compose.ui.geometry.Offset(rect.left * scale + offsetX, rect.top * scale + offsetY),
                        size = androidx.compose.ui.geometry.Size(rect.width() * scale, rect.height() * scale),
                        style = Stroke(width = 3.dp.toPx())
                    )
                }
            }

            // Badge Superior com Contagem Estabilizada e Latência
            Surface(
                Modifier.statusBarsPadding().align(Alignment.TopCenter).padding(top = 8.dp),
                color = Color.Black.copy(alpha = 0.7f),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Detectados: $contagemFrutos", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(
                        "${modelConfig.nomeArquivoModelo} · ${(modelConfig.threshold * 100).roundToInt()}% · ${detectorHelper.ultimaLatenciaMs}ms",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 11.sp
                    )
                }
            }

            // Controles de Câmera: Flash / Lanterna
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

            // Fechar Câmera
            IconButton(
                onClick = { cameraAtiva = false },
                modifier = Modifier.align(Alignment.TopEnd).padding(16.dp).size(56.dp)
            ) {
                Icon(Icons.Default.Cancel, "Fechar câmera", tint = Color.White, modifier = Modifier.size(44.dp))
            }

            ExtendedFloatingActionButton(
                onClick = {
                    tirarFoto(
                        context = context,
                        capture = imageCapture,
                        id = plantIdAtual,
                        onSucesso = { uri ->
                            onSave(uri, contagemFrutos.toString())
                            cameraAtiva = false
                        },
                        onErro = { erro ->
                            Toast.makeText(context, erro, Toast.LENGTH_SHORT).show()
                        }
                    )
                },
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp),
                containerColor = Color(0xFF2E7D32),
                text = { Text("SALVAR ($contagemFrutos FRUTOS)", fontWeight = FontWeight.Bold) },
                icon = { Icon(Icons.Default.Save, null) }
            )
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

// ════════════════════════════════════════════════════
// MODO ROTA (FIELDBOOK)
// ════════════════════════════════════════════════════
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
    onSaveColetaRota: (Uri, RotaColeta, String) -> Unit
) {
    val context = LocalContext.current
    var indiceAtual by rememberSaveable { mutableIntStateOf(0) }
    var cameraAtiva by remember { mutableStateOf(false) }
    var deteccoesAtuais by remember { mutableStateOf<List<DetectionResult>>(emptyList()) }
    var cameraControlRef by remember { mutableStateOf<CameraControl?>(null) }
    var lanternaLigada by remember { mutableStateOf(false) }

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
                Text("SELECIONAR PLANILHA DE ROTA")
            }
        }
    } else {
        val pontoAtual = rotaCompleta[indiceAtual.coerceIn(0, rotaCompleta.size - 1)]
        val isPontoValido = pontoAtual.clone.isNotBlank()

        if (!cameraAtiva || detectorHelper == null) {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Sessão em Rota: $ensaio", fontWeight = FontWeight.Bold, color = Color(0xFF1B5E20))
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

                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Progresso: ${indiceAtual + 1} de ${rotaCompleta.size}", fontSize = 14.sp)
                    // Atalho para pular para a próxima não coletada
                    val proximaPendente = rotaCompleta.indexOfFirst { !it.coletado }
                    if (proximaPendente >= 0 && proximaPendente != indiceAtual) {
                        TextButton(
                            onClick = { indiceAtual = proximaPendente },
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text("Ir para pendente (#${proximaPendente + 1})", fontSize = 12.sp, color = Color(0xFF2E7D32))
                        }
                    }
                }
                LinearProgressIndicator(
                    progress = { (indiceAtual + 1).toFloat() / rotaCompleta.size.toFloat() },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                    color = Color(0xFF2E7D32)
                )
                Spacer(Modifier.height(24.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = if (pontoAtual.coletado) Color(0xFFE8F5E9) else Color(0xFFFFF3E0))
                ) {
                    Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            if (pontoAtual.coletado) "PLANTA CONCLUÍDA (EDITÁVEL)" else "PRÓXIMA PLANTA DA ROTA",
                            fontWeight = FontWeight.Bold,
                            color = if (pontoAtual.coletado) Color(0xFF2E7D32) else Color(0xFFE65100)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text("CLONE / VARIEDADE", fontSize = 12.sp, color = Color.Gray)
                        Text(
                            if (isPontoValido) pontoAtual.clone else "[IDENTIFICADOR VAZIO]",
                            fontSize = 32.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (isPontoValido) Color.Black else Color.Red
                        )
                        Spacer(Modifier.height(16.dp))
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
                        if (pontoAtual.coletado) {
                            Spacer(Modifier.height(8.dp))
                            Text("Resultado Atual: ${pontoAtual.qtdFrutos}", fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                        }
                    }
                }

                Spacer(Modifier.weight(1f))

                OutlinedButton(
                    onClick = {
                        onSaveColetaRota(Uri.EMPTY, pontoAtual, "Não Coletável")
                        if (indiceAtual < rotaCompleta.size - 1) indiceAtual++
                    },
                    enabled = isPontoValido,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).height(56.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.ReportProblem, null); Spacer(Modifier.width(8.dp)); Text("PLANTA MORTA / SECA / AUSENTE")
                }

                val podeVoltar = indiceAtual > 0
                val podeAvancar = indiceAtual < rotaCompleta.size - 1
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { if (podeVoltar) indiceAtual-- }, enabled = podeVoltar, modifier = Modifier.size(72.dp)) {
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
                    IconButton(onClick = { if (podeAvancar) indiceAtual++ }, enabled = podeAvancar, modifier = Modifier.size(72.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, "Próxima planta", modifier = Modifier.size(44.dp), tint = if (podeAvancar) Color(0xFF2E7D32) else Color.Gray)
                    }
                }
            }
        } else {
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
                    score >= modelConfig.threshold &&
                            (modelConfig.labelsFiltro.isEmpty() || modelConfig.labelsFiltro.any { f -> label.contains(f.lowercase()) })
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
                        drawRect(
                            color = Color.Green,
                            topLeft = androidx.compose.ui.geometry.Offset(rect.left * scale + offsetX, rect.top * scale + offsetY),
                            size = androidx.compose.ui.geometry.Size(rect.width() * scale, rect.height() * scale),
                            style = Stroke(width = 3.dp.toPx())
                        )
                    }
                }

                Surface(
                    Modifier.statusBarsPadding().align(Alignment.TopCenter).padding(16.dp),
                    color = Color.Black.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${pontoAtual.clone} | Contagem: $contagemFrutos", color = Color.White, fontWeight = FontWeight.Bold)
                        Text("${modelConfig.nomeArquivoModelo} · ${(modelConfig.threshold * 100).roundToInt()}%", color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
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
                                onSaveColetaRota(uri, pontoAtual, contagemFrutos.toString())
                                cameraAtiva = false
                                if (indiceAtual < rotaCompleta.size - 1) indiceAtual++ else Toast.makeText(context, "Rota Finalizada!", Toast.LENGTH_SHORT).show()
                            },
                            onErro = { erro ->
                                Toast.makeText(context, erro, Toast.LENGTH_SHORT).show()
                            }
                        )
                    },
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp),
                    containerColor = Color(0xFF2E7D32),
                    text = { Text("CONFIRMAR PLANTA ${pontoAtual.planta}") },
                    icon = { Icon(Icons.Default.Check, null) }
                )
            }
        }
    }
}

// ════════════════════════════════════════════════════
// TELA GALERIA COM EXCLUSÃO (UNDO), EDIÇÃO E EXPORTAÇÃO
// ════════════════════════════════════════════════════
@Composable
fun TelaGaleria(
    registros: List<RegistroCacau>,
    ensaio: String,
    l1: String,
    l2: String,
    l3: String,
    onExcluirRegistro: (RegistroCacau) -> Unit,
    onEditarContagem: (plantId: String, novaContagem: String) -> Unit
) {
    val context = LocalContext.current
    var itemEmEdicao by remember { mutableStateOf<RegistroCacau?>(null) }
    var textoEdicao by remember { mutableStateOf("") }

    val totalRegistros = registros.size
    val totalFrutos = registros.sumOf { it.diagnostico.toIntOrNull() ?: 0 }
    val naoColetaveis = registros.count { it.diagnostico == "Não Coletável" }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Campanha: $ensaio", fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f))
            Row {
                IconButton(
                    onClick = {
                        exportarPlanilhaCEPEC(context, registros, ensaio, l1, l2, l3)
                    },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Default.FileDownload, contentDescription = "Exportar CSV", tint = Color(0xFF2E7D32), modifier = Modifier.size(28.dp))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CartaoMetrica(Modifier.weight(1f), Icons.Default.PhotoCamera, "$totalRegistros", "Plantas registradas", Color(0xFF2E7D32))
            CartaoMetrica(Modifier.weight(1f), Icons.Default.Eco, "$totalFrutos", "Frutos detectados", Color(0xFF1B5E20))
        }
        if (naoColetaveis > 0) {
            Spacer(Modifier.height(6.dp))
            Text("$naoColetaveis não coletável(is)", fontSize = 12.sp, color = Color(0xFFE65100))
        }
        Spacer(Modifier.height(12.dp))
        if (registros.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Collections, null, tint = Color.LightGray, modifier = Modifier.size(64.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("Nenhum item registrado.", color = Color.Gray)
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(registros.reversed(), key = { it.plantId + it.data }) { reg ->
                    Card(
                        Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (reg.diagnostico == "Não Coletável") Color(0xFFFFF3E0) else Color.White
                        )
                    ) {
                        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (reg.uri != Uri.EMPTY) {
                                AsyncImage(
                                    model = reg.uri,
                                    contentDescription = "Foto de ${reg.plantId}",
                                    modifier = Modifier.size(60.dp).clip(RoundedCornerShape(8.dp)),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Icon(Icons.Default.ReportProblem, null, tint = Color.Red, modifier = Modifier.size(60.dp).padding(8.dp))
                            }
                            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                                Text(reg.plantId, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(
                                    "Resultado: ${reg.diagnostico} | $l2: ${reg.parcelaManual}",
                                    fontSize = 12.sp,
                                    color = if (reg.diagnostico == "Não Coletável") Color(0xFFE65100) else Color.Gray
                                )
                                Text("${reg.data} • Lat: ${"%.4f".format(Locale.US, reg.lat)}", fontSize = 11.sp, color = Color.LightGray)
                            }
                            // Ações de editar contagem e excluir registro com desfazer
                            IconButton(onClick = {
                                itemEmEdicao = reg
                                textoEdicao = reg.diagnostico
                            }) {
                                Icon(Icons.Default.Edit, "Editar contagem", tint = Color.Gray, modifier = Modifier.size(20.dp))
                            }
                            IconButton(onClick = { onExcluirRegistro(reg) }) {
                                Icon(Icons.Default.Delete, "Excluir registro", tint = Color.Red.copy(alpha = 0.7f), modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    if (itemEmEdicao != null) {
        AlertDialog(
            onDismissRequest = { itemEmEdicao = null },
            title = { Text("Editar Contagem de Frutos", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Planta: ${itemEmEdicao!!.plantId}", fontSize = 13.sp, color = Color.Gray)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = textoEdicao,
                        onValueChange = { textoEdicao = it },
                        label = { Text("Quantidade de Frutos") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onEditarContagem(itemEmEdicao!!.plantId, textoEdicao)
                        itemEmEdicao = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                ) { Text("Salvar") }
            },
            dismissButton = {
                TextButton(onClick = { itemEmEdicao = null }) { Text("Cancelar") }
            }
        )
    }
}

// ════════════════════════════════════════════════════
// COMPONENTES AUXILIARES
// ════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeletorDropdownSimples(opcoes: List<String>, selecionado: String, onSelecionadoChange: (String) -> Unit) {
    var expandido by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expandido, onExpandedChange = { expandido = !expandido }) {
        OutlinedTextField(
            value = selecionado,
            onValueChange = {},
            readOnly = true,
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable, true),
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandido) }
        )
        ExposedDropdownMenu(expanded = expandido, onDismissRequest = { expandido = false }) {
            opcoes.forEach { opcao -> DropdownMenuItem(text = { Text(opcao) }, onClick = { onSelecionadoChange(opcao); expandido = false }) }
        }
    }
}

@Composable
fun TelaCheckInSimples(
    ensaio: String,
    onEnsaioChange: (String) -> Unit,
    exibirBotaoVoltar: Boolean,
    onVoltarClick: () -> Unit,
    onContinuar: () -> Unit
) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.Eco, null, tint = Color(0xFF2E7D32), modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(16.dp))
        Text("CacauApp CEPEC", fontSize = 26.sp, fontWeight = FontWeight.Black, color = Color(0xFF1B5E20))
        Text("Monitoramento de Frutos com IA", fontSize = 14.sp, color = Color(0xFF558B2F))
        Spacer(Modifier.height(32.dp))
        OutlinedTextField(
            value = ensaio,
            onValueChange = onEnsaioChange,
            label = { Text("Nome da Campanha de Monitoramento") },
            placeholder = { Text("Ex: TC_LOTE_2026") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = onContinuar,
            enabled = ensaio.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
        ) { Text("INICIAR MONITORAMENTO") }
        if (exibirBotaoVoltar) {
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onVoltarClick, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("VOLTAR PARA COLETA ATUAL") }
        }
    }
}

@Composable
private fun CartaoMetrica(
    modifier: Modifier,
    icone: androidx.compose.ui.graphics.vector.ImageVector,
    valor: String,
    rotulo: String,
    cor: Color
) {
    Card(modifier = modifier, shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9))) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icone, null, tint = cor, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(10.dp))
            Column {
                Text(valor, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = cor)
                Text(rotulo, fontSize = 11.sp, color = Color(0xFF558B2F))
            }
        }
    }
}

@Composable
private fun AvisoModeloAusente(nomeModelo: String, onAbrirModelo: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0))
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.ReportProblem, null, tint = Color(0xFFE65100))
                Spacer(Modifier.width(8.dp))
                Text("Modelo de IA indisponível", fontWeight = FontWeight.Bold, color = Color(0xFFE65100))
            }
            Text(
                "O modelo \"$nomeModelo\" não foi encontrado ou não pôde ser carregado. Importe um arquivo .tflite para habilitar a câmera.",
                fontSize = 12.sp, color = Color.DarkGray
            )
            Button(
                onClick = onAbrirModelo,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE65100))
            ) {
                Icon(Icons.Default.SmartToy, null); Spacer(Modifier.width(8.dp)); Text("CONFIGURAR MODELO IA", fontWeight = FontWeight.Bold)
            }
        }
    }
}
