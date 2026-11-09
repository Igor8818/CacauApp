package com.example.myapplication

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageCapture
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.google.android.gms.location.*
import java.text.SimpleDateFormat
import java.util.*
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets

import com.example.myapplication.data.ModelConfig
import com.example.myapplication.userinterface.CaixaAuditavel
import com.example.myapplication.data.MODELOS_EMBUTIDOS
import com.example.myapplication.data.RegistroCacau
import com.example.myapplication.data.RotaColeta
import com.example.myapplication.userinterface.TelaConfiguracaoModelo
import com.example.myapplication.network.CacauSyncClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.example.myapplication.userinterface.TelaRevisaoCaptura
import com.example.myapplication.userinterface.util.*
import org.tensorflow.lite.task.vision.detector.Detection

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
fun AppCacauCEPEC() {
    val context = LocalContext.current
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    val listaRegistros = remember { mutableStateListOf<RegistroCacau>() }
    val rotaPreCarregada = remember { mutableStateListOf<RotaColeta>() }

    var configConcluida by remember { mutableStateOf(false) }
    var abaSelecionada by remember { mutableIntStateOf(0) }
    var mostrarTelaModelo by remember { mutableStateOf(false) }

    var ensaioNome by rememberSaveable { mutableStateOf("") }

    var labelVariavel1 by rememberSaveable { mutableStateOf("Clone") }
    var labelVariavel2 by rememberSaveable { mutableStateOf("Parcela") }
    var labelVariavel3 by rememberSaveable { mutableStateOf("Planta") }

    var tipoNumVariavel2 by rememberSaveable { mutableStateOf(true) }
    var tipoNumVariavel3 by rememberSaveable { mutableStateOf(true) }

    var valorVariavel1 by rememberSaveable { mutableStateOf("CCN-51") }
    var valorNumVariavel2 by rememberSaveable { mutableIntStateOf(1) }
    var valorNumVariavel3 by rememberSaveable { mutableIntStateOf(1) }
    var valorTxtVariavel2 by rememberSaveable { mutableStateOf("") }
    var valorTxtVariavel3 by rememberSaveable { mutableStateOf("") }

    val historicoValores1 = remember { mutableStateListOf("CCN-51", "PS-13.19", "CEPEC-2002", "BN-34") }

    var latAtual by remember { mutableDoubleStateOf(0.0) }
    var lngAtual by remember { mutableDoubleStateOf(0.0) }

    var mostrarDialogMapeamento by remember { mutableStateOf(false) }
    var mostrarDialogoConfirmacaoNovoEnsaio by remember { mutableStateOf(false) }
    val colunasEncontradas = remember { mutableStateListOf<String>() }
    var uriArquivoPendente by remember { mutableStateOf<Uri?>(null) }
    var temDadosAnteriores by remember { mutableStateOf(false) }

    fun reconciliarRotaComRegistros(rota: List<RotaColeta>, registros: List<RegistroCacau>) {
        rota.forEach { ponto ->
            val idCompleto = "${ponto.clone}_P${ponto.parcela}_Pl${ponto.planta}"
            val reg = registros.firstOrNull { it.plantId == idCompleto }
            if (reg != null) {
                ponto.coletado = true
                ponto.qtdFrutos = reg.diagnostico
            }
        }
    }

    // ── CONFIGURAÇÃO DE MODELO (persistida via SharedPreferences) ──
    var modelConfig by remember {
        mutableStateOf(PersistenciaLocalRegistros.carregarConfiguracaoModelo(context))
    }
    var registroParaEdicao by remember { mutableStateOf<RegistroCacau?>(null) }

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
                    reconciliarRotaComRegistros(lista, listaRegistros)
                    rotaPreCarregada.clear()
                    rotaPreCarregada.addAll(lista)
                    Toast.makeText(context, "${lista.size} plantas carregadas automaticamente!", Toast.LENGTH_SHORT).show()
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

    val launcherPermissoes = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissoes ->
        if (permissoes[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000).build()
            fusedLocationClient.requestLocationUpdates(request, object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    result.lastLocation?.let { latAtual = it.latitude; lngAtual = it.longitude }
                }
            }, android.os.Looper.getMainLooper())
        }
    }

    LaunchedEffect(Unit) {
        val salvos = PersistenciaLocalRegistros.carregarRegistros(context)
        if (salvos.isNotEmpty() && listaRegistros.isEmpty()) {
            listaRegistros.addAll(salvos)
            temDadosAnteriores = true
            if (ensaioNome.isBlank()) {
                ensaioNome = salvos.first().ensaioManual.ifBlank { "TC-1801" }
            }
            // Garante que fotos de sessões anteriores tenham cópia pronta em files/fotos_sync/
            PersistenciaLocalRegistros.salvarRegistros(context, salvos)
        }
        launcherPermissoes.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.ACCESS_FINE_LOCATION))
    }

    // ── ROTEAMENTO DE TELAS ──
    when {
        registroParaEdicao != null -> {
            val reg = registroParaEdicao!!
            val detectorHelperRevisao = remember(modelConfig) {
                val cfg = modelConfig
                val extUri = cfg.modeloExternoUri
                val emb = cfg.modeloSelecionado
                val source = when {
                    extUri != null ->
                        ObjectDetectorHelper.ModelSource.External(extUri, cfg.modeloExternoNome)
                    emb != null ->
                        ObjectDetectorHelper.ModelSource.Asset(emb.nomeArquivo)
                    else -> ObjectDetectorHelper.ModelSource.Asset("detector.tflite")
                }
                ObjectDetectorHelper(
                    context = context,
                    detectorListener = null,
                    modelSource = source,
                    scoreThreshold = cfg.threshold,
                    maxResults = cfg.maxResultados
                )
            }
            DisposableEffect(detectorHelperRevisao) {
                onDispose { detectorHelperRevisao.fechar() }
            }

            TelaRevisaoCaptura(
                fotoUri = reg.uri,
                plantId = reg.plantId,
                deteccoesIniciais = emptyList(),
                bitmapWidthRef = 1f,
                bitmapHeightRef = 1f,
                detectorHelper = detectorHelperRevisao,
                labelsFiltro = modelConfig.labelsFiltro,
                caixasAuditadasIniciais = reg.caixas,
                onConfirmar = { diag, total, sadios, vassoura, podridao, naoAvail, caixasNovas ->
                    val idx = listaRegistros.indexOfFirst { it.plantId == reg.plantId }
                    if (idx != -1) {
                        listaRegistros[idx] = listaRegistros[idx].copy(
                            diagnostico = diag,
                            totalFrutos = total,
                            sadios = sadios,
                            vassoura = vassoura,
                            podridao = podridao,
                            naoAvaliavel = naoAvail,
                            caixas = caixasNovas
                        )
                        PersistenciaLocalRegistros.salvarRegistros(context, listaRegistros)
                        Toast.makeText(context, "Registro ${reg.plantId} atualizado!", Toast.LENGTH_SHORT).show()
                    }
                    registroParaEdicao = null
                },
                onRefazer = {
                    registroParaEdicao = null
                }
            )
        }

        mostrarTelaModelo -> {
            TelaConfiguracaoModelo(
                configAtual = modelConfig,
                onSalvar = { novaConfig ->
                    modelConfig = novaConfig
                    PersistenciaLocalRegistros.salvarConfiguracaoModelo(context, novaConfig)
                    mostrarTelaModelo = false
                    Toast.makeText(context, "Modelo \"${novaConfig.nomeExibicao}\" ativado e salvo!", Toast.LENGTH_SHORT).show()
                },
                onVoltar = { mostrarTelaModelo = false }
            )
        }

        !configConcluida -> {
            if (mostrarDialogoConfirmacaoNovoEnsaio) {
                AlertDialog(
                    onDismissRequest = { mostrarDialogoConfirmacaoNovoEnsaio = false },
                    title = { Text("Iniciar Novo Monitoramento?") },
                    text = {
                        Text("Existem ${listaRegistros.size} registros coletados salvos no aparelho que serão substituídos. Deseja realmente iniciar uma nova campanha e limpar os registros anteriores?")
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                listaRegistros.clear()
                                valorNumVariavel2 = 1
                                valorNumVariavel3 = 1
                                temDadosAnteriores = true
                                configConcluida = true
                                mostrarDialogoConfirmacaoNovoEnsaio = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828))
                        ) {
                            Text("Sim, Iniciar Novo")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { mostrarDialogoConfirmacaoNovoEnsaio = false }) {
                            Text("Cancelar")
                        }
                    }
                )
            }

            TelaCheckInSimples(
                ensaio = ensaioNome,
                onEnsaioChange = { ensaioNome = it },
                exibirBotaoVoltar = temDadosAnteriores,
                onVoltarClick = { configConcluida = true },
                onContinuar = {
                    if (listaRegistros.isNotEmpty()) {
                        mostrarDialogoConfirmacaoNovoEnsaio = true
                    } else {
                        listaRegistros.clear()
                        valorNumVariavel2 = 1
                        valorNumVariavel3 = 1
                        temDadosAnteriores = true
                        configConcluida = true
                    }
                }
            )
        }

        else -> {
            var modoImersivoAtivo by remember { mutableStateOf(false) }

            Scaffold(
                bottomBar = {
                    if (!modoImersivoAtivo) {
                        NavigationBar(containerColor = Color.White) {
                            NavigationBarItem(selected = abaSelecionada == 0, onClick = { abaSelecionada = 0 }, icon = { Icon(Icons.Default.PhotoCamera, null) }, label = { Text("Coleta Livre") })
                            NavigationBarItem(selected = abaSelecionada == 1, onClick = { abaSelecionada = 1 }, icon = { Icon(Icons.Default.Route, null) }, label = { Text("Modo Rota") })
                            NavigationBarItem(selected = abaSelecionada == 2, onClick = { abaSelecionada = 2 }, icon = { Icon(Icons.Default.Collections, null) }, label = { Text("Galeria") })
                            NavigationBarItem(
                                selected = false,
                                onClick = { mostrarTelaModelo = true },
                                icon = { Icon(Icons.Default.SmartToy, null) },
                                label = { Text("Modelo IA") }
                            )
                            NavigationBarItem(selected = false, onClick = { configConcluida = false }, icon = { Icon(Icons.Default.Settings, null) }, label = { Text("Ensaio") })
                        }
                    }
                }
            ) { padding ->
                Box(Modifier.padding(if (modoImersivoAtivo) PaddingValues(0.dp) else padding)) {
                    val pExibicao = if (tipoNumVariavel2) valorNumVariavel2.toString() else valorTxtVariavel2
                    val plExibicao = if (tipoNumVariavel3) valorNumVariavel3.toString() else valorTxtVariavel3

                    when (abaSelecionada) {
                        0 -> TelaPrincipalCapturaOtimizada(
                            ensaio = ensaioNome,
                            label1 = labelVariavel1, label2 = labelVariavel2, label3 = labelVariavel3,
                            isNum2 = tipoNumVariavel2, isNum3 = tipoNumVariavel3,
                            onConfigChanged = { l1, l2, l3, n2, n3 ->
                                labelVariavel1 = l1; labelVariavel2 = l2; labelVariavel3 = l3
                                tipoNumVariavel2 = n2; tipoNumVariavel3 = n3
                            },
                            cloneVal = valorVariavel1, onCloneChange = { valorVariavel1 = it },
                            historicoClones = historicoValores1,
                            parcelaNum = valorNumVariavel2, onParcelaNumChange = { valorNumVariavel2 = it },
                            parcelaTxt = valorTxtVariavel2, onParcelaTxtChange = { valorTxtVariavel2 = it },
                            plantaNum = valorNumVariavel3, onPlantaNumChange = { valorNumVariavel3 = it },
                            plantaTxt = valorTxtVariavel3, onPlantaTxtChange = { valorTxtVariavel3 = it },
                            modelConfig = modelConfig,
                            onModoImersivoChange = { modoImersivoAtivo = it },
                            onSave = { uri, diagnostico, total, sadios, vassoura, podridao, naoAvail, caixas ->
                                val data = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.ROOT).format(Date())
                                val idCompleto = "${valorVariavel1}_${labelVariavel2}${pExibicao}_${labelVariavel3}${plExibicao}"
                                if (valorVariavel1.isNotBlank() && !historicoValores1.contains(valorVariavel1)) historicoValores1.add(valorVariavel1)
                                val plantIntVal = if (tipoNumVariavel3) valorNumVariavel3 else 1
                                listaRegistros.removeAll { it.plantId == idCompleto }
                                listaRegistros.add(
                                    RegistroCacau(
                                        uri = uri,
                                        plantId = idCompleto,
                                        ensaioManual = ensaioNome,
                                        parcelaManual = pExibicao,
                                        diagnostico = diagnostico,
                                        data = data,
                                        numPlanta = plantIntVal,
                                        lat = latAtual,
                                        lng = lngAtual,
                                        totalFrutos = total,
                                        sadios = sadios,
                                        vassoura = vassoura,
                                        podridao = podridao,
                                        naoAvaliavel = naoAvail,
                                        caixas = caixas
                                    )
                                )
                                PersistenciaLocalRegistros.salvarRegistros(context, listaRegistros)
                                if (tipoNumVariavel3) valorNumVariavel3++
                            }
                        )
                        1 -> TelaModoFieldBook(
                            ensaio = ensaioNome,
                            rotaCompleta = rotaPreCarregada,
                            modelConfig = modelConfig,
                            onCarregarPlanilhaClick = { launcherArquivoCSV.launch("*/*") },
                            onLimparRotaClick = { rotaPreCarregada.clear() },
                            onModoImersivoChange = { modoImersivoAtivo = it },
                            onSaveColetaRota = { uri, ponto, diagnostico, total, sadios, vassoura, podridao, naoAvail, caixas ->
                                val data = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.ROOT).format(Date())
                                val idCompleto = "${ponto.clone}_P${ponto.parcela}_Pl${ponto.planta}"
                                listaRegistros.removeAll { it.plantId == idCompleto }
                                listaRegistros.add(
                                    RegistroCacau(
                                        uri = uri,
                                        plantId = idCompleto,
                                        ensaioManual = ensaioNome,
                                        parcelaManual = ponto.parcela,
                                        diagnostico = diagnostico,
                                        data = data,
                                        numPlanta = ponto.planta,
                                        lat = latAtual,
                                        lng = lngAtual,
                                        totalFrutos = total,
                                        sadios = sadios,
                                        vassoura = vassoura,
                                        podridao = podridao,
                                        naoAvaliavel = naoAvail,
                                        caixas = caixas
                                    )
                                )
                                PersistenciaLocalRegistros.salvarRegistros(context, listaRegistros)
                            }
                        )
                        2 -> TelaGaleria(
                            registros = listaRegistros,
                            ensaio = ensaioNome,
                            l1 = labelVariavel1,
                            l2 = labelVariavel2,
                            l3 = labelVariavel3,
                            onEditarRegistro = { reg ->
                                if (reg.uri != Uri.EMPTY) {
                                    registroParaEdicao = reg
                                } else {
                                    Toast.makeText(context, "Planta sem foto para revisão de caixas.", Toast.LENGTH_SHORT).show()
                                }
                            },
                            onExcluirRegistro = { reg ->
                                listaRegistros.remove(reg)
                                PersistenciaLocalRegistros.salvarRegistros(context, listaRegistros)
                                Toast.makeText(context, "Registro excluído.", Toast.LENGTH_SHORT).show()
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
                        val idxClone = colunasEncontradas.indexOf(selClone)
                        val idxParcela = colunasEncontradas.indexOf(selParcela)
                        val idxPlanta = colunasEncontradas.indexOf(selPlanta)
                        val lista = processarCSVComIndices(context, uriArquivoPendente!!, idxClone, idxParcela, idxPlanta)
                        reconciliarRotaComRegistros(lista, listaRegistros)
                        rotaPreCarregada.clear()
                        rotaPreCarregada.addAll(lista)
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
// TELA PRINCIPAL DE CAPTURA — recebe modelConfig
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
    onModoImersivoChange: (Boolean) -> Unit = {},
    onSave: (Uri, String, Int, Int, Int, Int, Int, List<CaixaAuditavel>) -> Unit
) {
    val context = LocalContext.current
    var cameraAtiva by remember { mutableStateOf(false) }
    var menuClonesExpandido by remember { mutableStateOf(false) }
    var mostrarDialogLabels by remember { mutableStateOf(false) }
    var resultadoDeteccaoAtual by remember { mutableStateOf<ObjectDetectorHelper.ResultadoDeteccao?>(null) }

    var fotoParaRevisao by remember { mutableStateOf<Uri?>(null) }
    var idParaRevisao by remember { mutableStateOf("") }
    var caixasCapturadas by remember { mutableStateOf<List<Detection>>(emptyList()) }
    var bitmapWidthCapturado by remember { mutableFloatStateOf(1f) }
    var bitmapHeightCapturado by remember { mutableFloatStateOf(1f) }

    var ultimoResultadoValido by remember { mutableStateOf<ObjectDetectorHelper.ResultadoDeteccao?>(null) }
    var ultimoTimestampValidoMs by remember { mutableLongStateOf(0L) }

    LaunchedEffect(cameraAtiva, fotoParaRevisao) {
        onModoImersivoChange(cameraAtiva || fotoParaRevisao != null)
    }
    DisposableEffect(Unit) {
        onDispose { onModoImersivoChange(false) }
    }

    val imageCapture = remember {
        ImageCapture.Builder()
            .setTargetAspectRatio(androidx.camera.core.AspectRatio.RATIO_4_3)
            .build()
    }

    val launcherGaleriaColeta = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { galeriaUri ->
            val arqLocal = copiarUriGaleriaParaArquivoLocal(context, galeriaUri)
            if (arqLocal != null) {
                val pVal = if (isNum2) "$parcelaNum" else parcelaTxt
                val plVal = if (isNum3) "$plantaNum" else plantaTxt
                idParaRevisao = "${cloneVal}_${label2}${pVal}_${label3}${plVal}"
                fotoParaRevisao = Uri.fromFile(arqLocal)
                caixasCapturadas = emptyList()
                bitmapWidthCapturado = 1f
                bitmapHeightCapturado = 1f
            } else {
                Toast.makeText(context, "Erro ao carregar foto da galeria.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Recria o detector quando a config de modelo mudar
    val detectorHelper = remember(modelConfig) {
        val source = when {
            modelConfig.modeloExternoUri != null ->
                ObjectDetectorHelper.ModelSource.External(modelConfig.modeloExternoUri, modelConfig.modeloExternoNome)
            modelConfig.modeloSelecionado != null ->
                ObjectDetectorHelper.ModelSource.Asset(modelConfig.modeloSelecionado.nomeArquivo)
            else -> ObjectDetectorHelper.ModelSource.Asset("detector.tflite")
        }
        ObjectDetectorHelper(
            context = context,
            detectorListener = null,
            modelSource = source,
            scoreThreshold = modelConfig.threshold,
            maxResults = modelConfig.maxResultados
        )
    }

    DisposableEffect(detectorHelper) {
        onDispose { detectorHelper.fechar() }
    }

    val isCloneValido = cloneVal.isNotBlank() &&
            (if (isNum2) true else parcelaTxt.isNotBlank()) &&
            (if (isNum3) true else plantaTxt.isNotBlank())

    if (fotoParaRevisao != null) {
        TelaRevisaoCaptura(
            fotoUri = fotoParaRevisao!!,
            plantId = idParaRevisao,
            deteccoesIniciais = caixasCapturadas,
            bitmapWidthRef = bitmapWidthCapturado,
            bitmapHeightRef = bitmapHeightCapturado,
            detectorHelper = detectorHelper,
            labelsFiltro = modelConfig.labelsFiltro,
            onConfirmar = { diag, total, sadios, vassoura, podridao, naoAvail, caixas ->
                onSave(fotoParaRevisao!!, diag, total, sadios, vassoura, podridao, naoAvail, caixas)
                fotoParaRevisao = null
                cameraAtiva = false
            },
            onRefazer = {
                fotoParaRevisao = null
            }
        )
    } else if (!cameraAtiva) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Sessão Manual: $ensaio", fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                IconButton(onClick = { mostrarDialogLabels = true }) {
                    Icon(Icons.Default.Settings, null, tint = Color(0xFF2E7D32))
                }
            }

            // Badge do modelo ativo
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFFE8F5E9),
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.SmartToy, null, tint = Color(0xFF2E7D32), modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Modelo: ${modelConfig.nomeExibicao}", fontSize = 12.sp, color = Color(0xFF2E7D32))
                    if (modelConfig.labelsFiltro.isNotEmpty()) {
                        Text(" • Labels: ${modelConfig.labelsFiltro.take(3).joinToString(", ")}${if (modelConfig.labelsFiltro.size > 3) "..." else ""}", fontSize = 11.sp, color = Color.Gray)
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
                    IconButton(onClick = { if (parcelaNum > 1) onParcelaNumChange(parcelaNum - 1) }) { Icon(Icons.Default.RemoveCircle, null, tint = Color.Red, modifier = Modifier.size(40.dp)) }
                    Text("$parcelaNum", fontSize = 32.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 24.dp))
                    IconButton(onClick = { onParcelaNumChange(parcelaNum + 1) }) { Icon(Icons.Default.AddCircle, null, tint = Color(0xFF2E7D32), modifier = Modifier.size(40.dp)) }
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

            OutlinedButton(onClick = { onSave(Uri.EMPTY, "Não Coletável", 0, 0, 0, 0, 0, emptyList()) }, enabled = isCloneValido, modifier = Modifier.fillMaxWidth().height(48.dp).padding(bottom = 8.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red), shape = RoundedCornerShape(12.dp)) {
                Icon(Icons.Default.ReportProblem, null); Spacer(Modifier.width(8.dp)); Text("PLANTA NÃO COLETÁVEL")
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { launcherGaleriaColeta.launch("image/*") },
                    enabled = isCloneValido,
                    modifier = Modifier.weight(1f).height(60.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF2E7D32))
                ) {
                    Icon(Icons.Default.Image, null)
                    Spacer(Modifier.width(6.dp))
                    Text("GALERIA", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = { cameraAtiva = true },
                    enabled = isCloneValido,
                    modifier = Modifier.weight(1.3f).height(60.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                ) {
                    Icon(Icons.Default.PhotoCamera, null)
                    Spacer(Modifier.width(6.dp))
                    Text("ABRIR CÂMERA", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    } else {
        Box(Modifier.fillMaxSize()) {
            CameraWithAI(detectorHelper, imageCapture) { res ->
                resultadoDeteccaoAtual = res
                if (res.deteccoes.isNotEmpty()) {
                    ultimoResultadoValido = res
                    ultimoTimestampValidoMs = System.currentTimeMillis()
                }
            }

            // Anti-flicker: se o frame atual oscilou para vazio por tremor/movimento nos últimos 650ms,
            // mantém as caixas válidas recentes para visualização fluida e captura sem perda.
            val resultadoAtivo = if ((resultadoDeteccaoAtual?.deteccoes?.size ?: 0) > 0) {
                resultadoDeteccaoAtual
            } else if (System.currentTimeMillis() - ultimoTimestampValidoMs < 650L) {
                ultimoResultadoValido
            } else {
                resultadoDeteccaoAtual
            }

            val deteccoesAtuais = resultadoAtivo?.deteccoes ?: emptyList()
            val frameWidth = resultadoAtivo?.larguraImagem ?: 1f
            val frameHeight = resultadoAtivo?.alturaImagem ?: 1f

            // Filtra usando os labels da config (vazio = aceita tudo)
            val listaFiltrada = deteccoesAtuais.filter { det ->
                val label = det.categories.firstOrNull()?.label?.lowercase() ?: ""
                val score = det.categories.firstOrNull()?.score ?: 0f
                score >= modelConfig.threshold &&
                    (modelConfig.labelsFiltro.isEmpty() ||
                     modelConfig.labelsFiltro.any { f -> label.contains(f.lowercase()) } ||
                     label == "cacau" || label == "fruto")
            }
            val contagemFrutos = listaFiltrada.size

            BoundingBoxOverlay(
                detections = listaFiltrada,
                bitmapWidth = frameWidth,
                bitmapHeight = frameHeight,
                modifier = Modifier.fillMaxSize()
            )

            // Topo compacto elevado para a barra de status (idêntico ao Modo Rota)
            val plExHud = if (isNum3) plantaNum.toString() else plantaTxt
            Surface(
                Modifier
                    .statusBarsPadding()
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                color = Color.Black.copy(alpha = 0.75f),
                shape = RoundedCornerShape(14.dp)
            ) {
                Row(
                    Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("$cloneVal | Pl $plExHud", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(Modifier.width(8.dp))
                    Text("• Detectados: $contagemFrutos", color = Color(0xFF00E676), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }

            IconButton(
                onClick = { cameraAtiva = false },
                modifier = Modifier
                    .statusBarsPadding()
                    .align(Alignment.TopEnd)
                    .padding(end = 12.dp, top = 6.dp)
            ) {
                Icon(Icons.Default.Cancel, null, tint = Color.White, modifier = Modifier.size(30.dp))
            }

            // Botão de captura rebaixado
            ExtendedFloatingActionButton(
                onClick = {
                    val pEx = if (isNum2) parcelaNum.toString() else parcelaTxt
                    val plEx = if (isNum3) plantaNum.toString() else plantaTxt
                    val idParaArquivo = "${cloneVal}_${label2}${pEx}_${label3}${plEx}"

                    // Snapshot atômico de detecções e dimensões do mesmo frame no exato momento do clique
                    val caixasSnapshot = listaFiltrada.toList()
                    val widthSnapshot = frameWidth
                    val heightSnapshot = frameHeight

                    tirarFoto(context, imageCapture, idParaArquivo) { uri ->
                        idParaRevisao = idParaArquivo
                        caixasCapturadas = caixasSnapshot
                        bitmapWidthCapturado = widthSnapshot
                        bitmapHeightCapturado = heightSnapshot
                        fotoParaRevisao = uri
                    }
                },
                modifier = Modifier
                    .navigationBarsPadding()
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp),
                containerColor = Color(0xFF2E7D32),
                text = { Text("CAPTURAR ($contagemFrutos DETECTADOS)", fontWeight = FontWeight.Bold) },
                icon = { Icon(Icons.Default.PhotoCamera, null) }
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
                    Divider()
                    Column {
                        OutlinedTextField(value = t2, onValueChange = { t2 = it }, label = { Text("Coluna 2 (Ex: Parcela)") })
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                            Checkbox(checked = n2, onCheckedChange = { n2 = it })
                            Text("Usar como Contador Numérico", fontSize = 13.sp)
                        }
                    }
                    Divider()
                    Column {
                        OutlinedTextField(value = t3, onValueChange = { t3 = it }, label = { Text("Coluna 3 (Ex: Planta)") })
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                            Checkbox(checked = n3, onCheckedChange = { n3 = it })
                            Text("Usar como Contador Numérico", fontSize = 13.sp)
                        }
                    }
                }
            },
            confirmButton = { Button(onClick = { onConfigChanged(t1, t2, t3, n2, n3); mostrarDialogLabels = false }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))) { Text("Salvar") } },
            dismissButton = { TextButton(onClick = { mostrarDialogLabels = false }) { Text("Cancelar") } }
        )
    }
}

// ════════════════════════════════════════════════════
// MODO ROTA — recebe modelConfig
// ════════════════════════════════════════════════════
@Composable
fun TelaModoFieldBook(
    ensaio: String,
    rotaCompleta: List<RotaColeta>,
    modelConfig: ModelConfig,
    onCarregarPlanilhaClick: () -> Unit,
    onLimparRotaClick: () -> Unit,
    onModoImersivoChange: (Boolean) -> Unit = {},
    onSaveColetaRota: (Uri, RotaColeta, String, Int, Int, Int, Int, Int, List<CaixaAuditavel>) -> Unit
) {
    val context = LocalContext.current
    val primeiroNaoColetado = remember(rotaCompleta) {
        val idx = rotaCompleta.indexOfFirst { !it.coletado }
        if (idx >= 0) idx else 0
    }
    var indiceAtual by rememberSaveable(rotaCompleta.size) { mutableIntStateOf(primeiroNaoColetado) }
    var cameraAtiva by remember { mutableStateOf(false) }
    var resultadoDeteccaoAtual by remember { mutableStateOf<ObjectDetectorHelper.ResultadoDeteccao?>(null) }
    var fotoParaRevisao by remember { mutableStateOf<Uri?>(null) }
    var caixasCapturadas by remember { mutableStateOf<List<Detection>>(emptyList()) }
    var bitmapWidthCapturado by remember { mutableFloatStateOf(1f) }
    var bitmapHeightCapturado by remember { mutableFloatStateOf(1f) }

    var ultimoResultadoValido by remember { mutableStateOf<ObjectDetectorHelper.ResultadoDeteccao?>(null) }
    var ultimoTimestampValidoMs by remember { mutableLongStateOf(0L) }

    LaunchedEffect(cameraAtiva, fotoParaRevisao) {
        onModoImersivoChange(cameraAtiva || fotoParaRevisao != null)
    }
    DisposableEffect(Unit) {
        onDispose { onModoImersivoChange(false) }
    }

    val imageCapture = remember {
        ImageCapture.Builder()
            .setTargetAspectRatio(androidx.camera.core.AspectRatio.RATIO_4_3)
            .build()
    }

    val launcherGaleriaRota = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { galeriaUri ->
            val arqLocal = copiarUriGaleriaParaArquivoLocal(context, galeriaUri)
            if (arqLocal != null) {
                fotoParaRevisao = Uri.fromFile(arqLocal)
                caixasCapturadas = emptyList()
                bitmapWidthCapturado = 1f
                bitmapHeightCapturado = 1f
            } else {
                Toast.makeText(context, "Erro ao carregar foto da galeria.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val detectorHelper = remember(modelConfig) {
        val source = when {
            modelConfig.modeloExternoUri != null ->
                ObjectDetectorHelper.ModelSource.External(modelConfig.modeloExternoUri, modelConfig.modeloExternoNome)
            modelConfig.modeloSelecionado != null ->
                ObjectDetectorHelper.ModelSource.Asset(modelConfig.modeloSelecionado.nomeArquivo)
            else -> ObjectDetectorHelper.ModelSource.Asset("detector.tflite")
        }
        ObjectDetectorHelper(
            context = context,
            detectorListener = null,
            modelSource = source,
            scoreThreshold = modelConfig.threshold,
            maxResults = modelConfig.maxResultados
        )
    }

    DisposableEffect(detectorHelper) {
        onDispose { detectorHelper.fechar() }
    }

    if (rotaCompleta.isEmpty()) {
        Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.FolderOpen, null, modifier = Modifier.size(64.dp), tint = Color.Gray)
            Spacer(Modifier.height(16.dp))
            Text("Nenhuma rota carregada", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text("Selecione o arquivo de planejamento (CSV) para iniciar o monitoramento sequencial.", color = Color.Gray, modifier = Modifier.padding(16.dp))
            Spacer(Modifier.height(16.dp))
            Button(onClick = onCarregarPlanilhaClick, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))) {
                Text("SELECIONAR PLANILHA DE COLETA")
            }
        }
    } else {
        val pontoAtual = rotaCompleta[indiceAtual]
        val isPontoValido = pontoAtual.clone.isNotBlank()

        if (fotoParaRevisao != null) {
            val idPonto = "${pontoAtual.clone}_P${pontoAtual.parcela}_Pl${pontoAtual.planta}"
            TelaRevisaoCaptura(
                fotoUri = fotoParaRevisao!!,
                plantId = idPonto,
                deteccoesIniciais = caixasCapturadas,
                bitmapWidthRef = bitmapWidthCapturado,
                bitmapHeightRef = bitmapHeightCapturado,
                detectorHelper = detectorHelper,
                labelsFiltro = modelConfig.labelsFiltro,
                onConfirmar = { diag, total, sadios, vassoura, podridao, naoAvail, caixas ->
                    onSaveColetaRota(fotoParaRevisao!!, pontoAtual, diag, total, sadios, vassoura, podridao, naoAvail, caixas)
                    pontoAtual.coletado = true
                    pontoAtual.qtdFrutos = diag
                    fotoParaRevisao = null
                    cameraAtiva = false
                    if (indiceAtual < rotaCompleta.size - 1) indiceAtual++ else Toast.makeText(context, "Rota Finalizada!", Toast.LENGTH_SHORT).show()
                },
                onRefazer = {
                    fotoParaRevisao = null
                }
            )
        } else if (!cameraAtiva) {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Sessão em Rota: $ensaio", fontWeight = FontWeight.Bold, color = Color(0xFF1B5E20))
                    TextButton(onClick = { onLimparRotaClick(); indiceAtual = 0 }, colors = ButtonDefaults.textButtonColors(contentColor = Color.Red)) {
                        Icon(Icons.Default.DeleteSweep, null); Spacer(Modifier.width(4.dp)); Text("Trocar Planilha")
                    }
                }
                Spacer(Modifier.height(8.dp))

                // Badge modelo
                Surface(shape = RoundedCornerShape(8.dp), color = Color(0xFFE8F5E9), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.SmartToy, null, tint = Color(0xFF2E7D32), modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("${modelConfig.nomeExibicao} • ${(modelConfig.threshold * 100).toInt()}% conf.", fontSize = 12.sp, color = Color(0xFF2E7D32))
                    }
                }

                Spacer(Modifier.height(12.dp))
                Text("Progresso: ${indiceAtual + 1} de ${rotaCompleta.size}", fontSize = 14.sp)
                LinearProgressIndicator(progress = { (indiceAtual + 1).toFloat() / rotaCompleta.size.toFloat() }, modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)), color = Color(0xFF2E7D32))
                Spacer(Modifier.height(24.dp))

                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = if (pontoAtual.coletado) Color(0xFFE8F5E9) else Color(0xFFFFF3E0))) {
                    Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (pontoAtual.coletado) "PLANTA CONCLUÍDA (EDITÁVEL)" else "PRÓXIMA PLANTA DA ROTA", fontWeight = FontWeight.Bold, color = if (pontoAtual.coletado) Color(0xFF2E7D32) else Color(0xFFE65100))
                        Spacer(Modifier.height(16.dp))
                        Text("CLONE / VARIEDADE", fontSize = 12.sp, color = Color.Gray)
                        Text(if (isPontoValido) pontoAtual.clone else "[IDENTIFICADOR VAZIO]", fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, color = if (isPontoValido) Color.Black else Color.Red)
                        Spacer(Modifier.height(16.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) { Text("PARCELA", fontSize = 12.sp, color = Color.Gray); Text(pontoAtual.parcela, fontSize = 24.sp, fontWeight = FontWeight.Bold) }
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) { Text("PLANTA ID", fontSize = 12.sp, color = Color.Gray); Text("${pontoAtual.planta}", fontSize = 24.sp, fontWeight = FontWeight.Bold) }
                        }
                        if (pontoAtual.coletado) { Spacer(Modifier.height(8.dp)); Text("Resultado: ${pontoAtual.qtdFrutos}", fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32)) }
                    }
                }

                Spacer(Modifier.weight(1f))

                OutlinedButton(onClick = { onSaveColetaRota(Uri.EMPTY, pontoAtual, "Não Coletável", 0, 0, 0, 0, 0, emptyList()); pontoAtual.coletado = true; pontoAtual.qtdFrutos = "Não Coletável"; if (indiceAtual < rotaCompleta.size - 1) indiceAtual++ }, enabled = isPontoValido, modifier = Modifier.fillMaxWidth().height(48.dp).padding(bottom = 8.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red), shape = RoundedCornerShape(12.dp)) {
                    Icon(Icons.Default.ReportProblem, null); Spacer(Modifier.width(8.dp)); Text("PLANTA MORTA / SECA / AUSENTE")
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { if (indiceAtual > 0) indiceAtual-- }, enabled = indiceAtual > 0) { Icon(Icons.Default.ArrowBackIos, null, modifier = Modifier.size(36.dp), tint = if (indiceAtual > 0) Color(0xFF2E7D32) else Color.Gray) }
                    OutlinedButton(
                        onClick = { launcherGaleriaRota.launch("image/*") },
                        enabled = isPontoValido,
                        modifier = Modifier.height(56.dp).padding(horizontal = 4.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF2E7D32)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Image, null)
                        Spacer(Modifier.width(4.dp))
                        Text("GALERIA", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = { cameraAtiva = true },
                        enabled = isPontoValido,
                        modifier = Modifier.height(56.dp).weight(1f).padding(end = 4.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.PhotoCamera, null)
                        Spacer(Modifier.width(4.dp))
                        Text(if (pontoAtual.coletado) "REFAZER" else "CÂMERA", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    IconButton(onClick = { if (indiceAtual < rotaCompleta.size - 1) indiceAtual++ }, enabled = indiceAtual < rotaCompleta.size - 1) { Icon(Icons.Default.ArrowForwardIos, null, modifier = Modifier.size(36.dp), tint = if (indiceAtual < rotaCompleta.size - 1) Color(0xFF2E7D32) else Color.Gray) }
                }
            }
        } else {
            Box(Modifier.fillMaxSize()) {
                CameraWithAI(detectorHelper, imageCapture) { res ->
                    resultadoDeteccaoAtual = res
                    if (res.deteccoes.isNotEmpty()) {
                        ultimoResultadoValido = res
                        ultimoTimestampValidoMs = System.currentTimeMillis()
                    }
                }

                val resultadoAtivo = if ((resultadoDeteccaoAtual?.deteccoes?.size ?: 0) > 0) {
                    resultadoDeteccaoAtual
                } else if (System.currentTimeMillis() - ultimoTimestampValidoMs < 650L) {
                    ultimoResultadoValido
                } else {
                    resultadoDeteccaoAtual
                }

                val deteccoesAtuais = resultadoAtivo?.deteccoes ?: emptyList()
                val frameWidth = resultadoAtivo?.larguraImagem ?: 1f
                val frameHeight = resultadoAtivo?.alturaImagem ?: 1f

                val listaFiltrada = deteccoesAtuais.filter { det ->
                    val label = det.categories.firstOrNull()?.label?.lowercase() ?: ""
                    val score = det.categories.firstOrNull()?.score ?: 0f
                    score >= modelConfig.threshold &&
                        (modelConfig.labelsFiltro.isEmpty() ||
                         modelConfig.labelsFiltro.any { f -> label.contains(f.lowercase()) } ||
                         label == "cacau" || label == "fruto")
                }
                val contagemFrutos = listaFiltrada.size

                BoundingBoxOverlay(
                    detections = listaFiltrada,
                    bitmapWidth = frameWidth,
                    bitmapHeight = frameHeight,
                    modifier = Modifier.fillMaxSize()
                )

                // Topo compacto elevado para a barra de status
                Surface(
                    Modifier
                        .statusBarsPadding()
                        .align(Alignment.TopCenter)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    color = Color.Black.copy(alpha = 0.75f),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Row(
                        Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("${pontoAtual.clone} | Pl ${pontoAtual.planta}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Spacer(Modifier.width(8.dp))
                        Text("• Detectados: $contagemFrutos", color = Color(0xFF00E676), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }

                IconButton(
                    onClick = { cameraAtiva = false },
                    modifier = Modifier
                        .statusBarsPadding()
                        .align(Alignment.TopEnd)
                        .padding(end = 12.dp, top = 6.dp)
                ) {
                    Icon(Icons.Default.Cancel, null, tint = Color.White, modifier = Modifier.size(30.dp))
                }

                // Botão de captura rebaixado
                ExtendedFloatingActionButton(
                    onClick = {
                        val idParaArquivo = "${pontoAtual.clone}_P${pontoAtual.parcela}_Pl${pontoAtual.planta}"

                        // Snapshot atômico de detecções e dimensões do mesmo frame no exato momento do clique
                        val caixasSnapshot = listaFiltrada.toList()
                        val widthSnapshot = frameWidth
                        val heightSnapshot = frameHeight

                        tirarFoto(context, imageCapture, idParaArquivo) { uri ->
                            caixasCapturadas = caixasSnapshot
                            bitmapWidthCapturado = widthSnapshot
                            bitmapHeightCapturado = heightSnapshot
                            fotoParaRevisao = uri
                        }
                    },
                    modifier = Modifier
                        .navigationBarsPadding()
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 16.dp),
                    containerColor = Color(0xFF2E7D32),
                    text = { Text("CAPTURAR PLANTA ${pontoAtual.planta} ($contagemFrutos)", fontWeight = FontWeight.Bold) },
                    icon = { Icon(Icons.Default.PhotoCamera, null) }
                )
            }
        }
    }
}

// ════════════════════════════════════════════════════
// FUNÇÕES UTILITÁRIAS (mantidas igual)
// ════════════════════════════════════════════════════
fun lerApenasCabecalho(context: Context, uri: Uri): List<String> {
    try {
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            BufferedReader(InputStreamReader(inputStream, StandardCharsets.UTF_8)).use { reader ->
                val primeiraLinha = reader.readLine() ?: return emptyList()
                val linhaLimpa = limparSimbologiaPura(primeiraLinha)
                return fatiarColunasCampos(linhaLimpa).map { it.trim() }.filter { it.isNotEmpty() }
            }
        }
    } catch (e: Exception) { e.printStackTrace() }
    return emptyList()
}

fun processarCSVComIndices(context: Context, uri: Uri, idxClone: Int, idxParcela: Int, idxPlanta: Int): List<RotaColeta> {
    val lista = mutableListOf<RotaColeta>()
    try {
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            BufferedReader(InputStreamReader(inputStream, StandardCharsets.UTF_8)).use { reader ->
                reader.readLine()
                var javaLinha: String? = reader.readLine()
                while (javaLinha != null) {
                    val linhaLimpa = limparSimbologiaPura(javaLinha)
                    val colunas = fatiarColunasCampos(linhaLimpa)
                    if (colunas.size > maxOf(idxClone, idxParcela, idxPlanta)) {
                        val clone = colunas[idxClone].trim()
                        val parcela = colunas[idxParcela].trim()
                        val plantaText = colunas[idxPlanta].trim()
                        val planta = plantaText.filter { it.isDigit() }.toIntOrNull() ?: 1
                        lista.add(RotaColeta(clone, parcela, planta))
                    }
                    javaLinha = reader.readLine()
                }
            }
        }
    } catch (e: Exception) { e.printStackTrace() }
    return lista
}

fun limparSimbologiaPura(texto: String): String {
    if (texto.isEmpty()) return ""
    return texto.replace("\uFEFF", "").replace("\uFFFD", "").replace("ï»¿", "")
        .replace("[^\\x20-\\x7E\\u00A0-\\u00FF]".toRegex(), "").trim()
}

fun fatiarColunasCampos(linha: String): List<String> {
    if (linha.isBlank()) return emptyList()
    val divisor = if (linha.contains(";")) ';' else ','
    val resultado = mutableListOf<String>()
    val sb = StringBuilder()
    var dentroAspas = false
    val limpa = linha.replace("\r", "").replace("\n", "")
    for (i in limpa.indices) {
        val c = limpa[i]
        if (c == '"') {
            dentroAspas = !dentroAspas
        } else if (c == divisor && !dentroAspas) {
            resultado.add(sb.toString().trim())
            sb.clear()
        } else {
            sb.append(c)
        }
    }
    resultado.add(sb.toString().trim())
    return resultado
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeletorDropdownSimples(opcoes: List<String>, selecionado: String, onSelecionadoChange: (String) -> Unit) {
    var expandido by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expandido, onExpandedChange = { expandido = !expandido }) {
        OutlinedTextField(value = selecionado, onValueChange = {}, readOnly = true, modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable, true), trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandido) })
        ExposedDropdownMenu(expanded = expandido, onDismissRequest = { expandido = false }) {
            opcoes.forEach { opcao -> DropdownMenuItem(text = { Text(opcao) }, onClick = { onSelecionadoChange(opcao); expandido = false }) }
        }
    }
}

@Composable
fun TelaCheckInSimples(ensaio: String, onEnsaioChange: (String) -> Unit, exibirBotaoVoltar: Boolean, onVoltarClick: () -> Unit, onContinuar: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.Eco, null, tint = Color(0xFF2E7D32), modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(16.dp))
        Text("CacauApp", fontSize = 28.sp, fontWeight = FontWeight.Black, color = Color(0xFF1B5E20))
        Text("CEPEC / CEPLAC • Avaliação de Clones", fontSize = 13.sp, color = Color(0xFF388E3C), fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(32.dp))
        OutlinedTextField(value = ensaio, onValueChange = onEnsaioChange, label = { Text("Nome da Campanha de Monitoramento") }, placeholder = { Text("Ex: TC_LOTE_2026") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(32.dp))
        Button(onClick = onContinuar, enabled = ensaio.isNotBlank(), modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("INICIAR MONITORAMENTO") }
        if (exibirBotaoVoltar) {
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onVoltarClick, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("VOLTAR PARA COLETA ATUAL") }
        }
    }
}

@Composable
fun DialogConfiguracaoServidor(
    onDismiss: () -> Unit,
    onSalvar: (url: String, email: String, pass: String, ensaio: String) -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("cacausync_prefs", Context.MODE_PRIVATE) }

    var url by remember { mutableStateOf((prefs.getString("server_url", "") ?: "").ifBlank { CacauSyncClient.DEFAULT_SERVER_URL }) }
    var email by remember { mutableStateOf((prefs.getString("user_email", "") ?: "").ifBlank { CacauSyncClient.DEFAULT_EMAIL }) }
    var pass by remember { mutableStateOf((prefs.getString("user_pass", "") ?: "").ifBlank { CacauSyncClient.DEFAULT_PASS }) }
    var ensaio by remember { mutableStateOf(prefs.getString("codigo_ensaio", "TC-1801") ?: "TC-1801") }
    var testando by remember { mutableStateOf(false) }
    var resultadoTeste by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Configurações Avançadas (Opcional)", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "O envio principal já descobre o PC sozinho via USB ou Wi-Fi. Use abaixo apenas se quiser fixar outro servidor:",
                    fontSize = 12.sp,
                    color = Color.DarkGray
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("URL do Servidor (Auto-descoberta ativa)") },
                    placeholder = { Text("http://127.0.0.1:8080 ou IP local:8080") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("E-mail Institucional") },
                    placeholder = { Text("coordenador@cepec.gov.br") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = pass,
                    onValueChange = { pass = it },
                    label = { Text("Senha") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = ensaio,
                    onValueChange = { ensaio = it },
                    label = { Text("Código do Ensaio") },
                    placeholder = { Text("TC-1801") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (resultadoTeste != null) {
                    Text(
                        resultadoTeste!!,
                        fontSize = 12.sp,
                        color = if (resultadoTeste!!.startsWith("Conectado")) Color(0xFF2E7D32) else Color.Red,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    prefs.edit()
                        .putString("server_url", url.trim())
                        .putString("user_email", email.trim())
                        .putString("user_pass", pass)
                        .putString("codigo_ensaio", ensaio.trim().uppercase())
                        .apply()
                    onSalvar(url.trim(), email.trim(), pass, ensaio.trim().uppercase())
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
            ) {
                Text("SALVAR")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(
                    onClick = {
                        testando = true
                        resultadoTeste = "Buscando PC automaticamente..."
                        coroutineScope.launch(Dispatchers.IO) {
                            val descoberto = CacauSyncClient.descobrirServidorAutomaticamente(url)
                            val targetUrl = descoberto ?: url
                            val res = CacauSyncClient.login(targetUrl, email, pass)
                            android.os.Handler(android.os.Looper.getMainLooper()).post {
                                testando = false
                                if (descoberto != null) {
                                    url = descoberto
                                }
                                resultadoTeste = if (res.isSuccess) {
                                    "Conectado com sucesso em $targetUrl!"
                                } else {
                                    "Erro: ${res.exceptionOrNull()?.message}"
                                }
                            }
                        }
                    },
                    enabled = !testando
                ) {
                    Text(if (testando) "Buscando..." else "AUTO-DETECTAR")
                }
                TextButton(onClick = onDismiss) {
                    Text("FECHAR")
                }
            }
        }
    )
}

@Composable
fun TelaGaleria(
    registros: List<RegistroCacau>,
    ensaio: String,
    l1: String,
    l2: String,
    l3: String,
    onEditarRegistro: (RegistroCacau) -> Unit = {},
    onExcluirRegistro: (RegistroCacau) -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var mostrarConfigServidor by remember { mutableStateOf(false) }
    var sincronizando by remember { mutableStateOf(false) }
    var statusSyncMensagem by remember { mutableStateOf("") }
    var resultadoSyncDialog by remember { mutableStateOf<String?>(null) }
    var registroParaExcluir by remember { mutableStateOf<RegistroCacau?>(null) }

    if (registroParaExcluir != null) {
        AlertDialog(
            onDismissRequest = { registroParaExcluir = null },
            title = { Text("Excluir Registro?", fontWeight = FontWeight.Bold) },
            text = { Text("Deseja realmente remover o registro da planta \"${registroParaExcluir!!.plantId}\"? Esta ação não pode ser desfeita localmente.") },
            confirmButton = {
                Button(
                    onClick = {
                        val reg = registroParaExcluir!!
                        registroParaExcluir = null
                        onExcluirRegistro(reg)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828))
                ) {
                    Text("Excluir")
                }
            },
            dismissButton = {
                TextButton(onClick = { registroParaExcluir = null }) {
                    Text("Cancelar")
                }
            }
        )
    }

    if (mostrarConfigServidor) {
        DialogConfiguracaoServidor(
            onDismiss = { mostrarConfigServidor = false },
            onSalvar = { _, _, _, _ ->
                Toast.makeText(context, "Configurações salvas!", Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (resultadoSyncDialog != null) {
        AlertDialog(
            onDismissRequest = { resultadoSyncDialog = null },
            title = { Text("Resultado da Sincronização", fontWeight = FontWeight.Bold) },
            text = { Text(resultadoSyncDialog!!) },
            confirmButton = {
                Button(onClick = { resultadoSyncDialog = null }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))) {
                    Text("OK")
                }
            }
        )
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("Registros: $ensaio", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text("${registros.size} coletados • Envio automático 1-clique", fontSize = 12.sp, color = Color.Gray)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { mostrarConfigServidor = true }) {
                    Icon(Icons.Default.CloudSync, "Configurações Avançadas", tint = Color(0xFF1976D2))
                }
                IconButton(onClick = { exportarCSVCEPEC_Personalizado(context, registros, l1, l2, l3) }) {
                    Icon(Icons.Default.FileDownload, "Exportar CSV", tint = Color(0xFF2E7D32))
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // Botão principal de sincronização 1-clique (Zero-Config: descobre PC via USB ou Wi-Fi sozinho)
        Button(
            onClick = {
                if (registros.isEmpty()) {
                    Toast.makeText(context, "Nenhum registro para sincronizar.", Toast.LENGTH_SHORT).show()
                    return@Button
                }

                sincronizando = true
                statusSyncMensagem = "Procurando PC (USB / Wi-Fi)..."
                val snapshotRegistros = registros.toList()

                coroutineScope.launch(Dispatchers.IO) {
                    val syncRes = CacauSyncClient.sincronizarAutomatico(
                        context = context,
                        ensaioPadrao = ensaio.ifBlank { "TC-1801" },
                        registros = snapshotRegistros,
                        onProgresso = { msg ->
                            android.os.Handler(android.os.Looper.getMainLooper()).post {
                                statusSyncMensagem = msg
                            }
                        },
                        onStatusRegistro = { pId, novoStatus ->
                            android.os.Handler(android.os.Looper.getMainLooper()).post {
                                val idx = registros.indexOfFirst { it.plantId == pId }
                                if (idx != -1 && registros is SnapshotStateList) {
                                    (registros as SnapshotStateList)[idx] = registros[idx].copy(statusSync = novoStatus)
                                }
                            }
                        }
                    )

                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        sincronizando = false
                        // Gravação em disco thread-safe com todos os status de sincronização devidamente atualizados
                        PersistenciaLocalRegistros.salvarRegistros(context, registros)
                        if (syncRes.isSuccess) {
                            val res = syncRes.getOrThrow()
                            var resumo = "Enviado para o PC com sucesso!\n" +
                                    "Servidor: ${res.servidorUtilizado}\n\n" +
                                    "• Total processadas: ${res.totalProcessadas}\n" +
                                    "• Leituras novas inseridas: ${res.novasInseridas}\n" +
                                    "• Leituras atualizadas/existentes: ${res.duplicadasIgnoradas}\n" +
                                    "• Fotos enviadas com caixas YOLO: ${res.fotosEnviadas}\n"
                            if (res.mensagens.isNotEmpty()) {
                                resumo += "\nAvisos do servidor:\n" + res.mensagens.take(3).joinToString("\n")
                            }
                            resultadoSyncDialog = resumo
                        } else {
                            resultadoSyncDialog = "Não foi possível enviar automaticamente:\n${syncRes.exceptionOrNull()?.message}"
                        }
                    }
                }
            },
            enabled = !sincronizando && registros.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
            shape = RoundedCornerShape(10.dp)
        ) {
            if (sincronizando) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text(statusSyncMensagem, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            } else {
                Icon(Icons.Default.CloudUpload, null)
                Spacer(Modifier.width(8.dp))
                Text("ENVIAR PARA O PC AGORA (${registros.size})", fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.height(12.dp))

        if (registros.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Nenhum item registrado.") }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(registros.reversed()) { reg ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (reg.uri != Uri.EMPTY) {
                                AsyncImage(model = reg.uri, contentDescription = null, modifier = Modifier.size(60.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
                            } else {
                                Icon(Icons.Default.ReportProblem, null, tint = Color.Red, modifier = Modifier.size(60.dp).padding(8.dp))
                            }
                            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                Text(reg.plantId, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("Resultado: ${reg.diagnostico} | $l2: ${reg.parcelaManual}", fontSize = 12.sp, color = Color.Gray)
                                if (reg.totalFrutos > 0) {
                                    Text("Sadios: ${reg.sadios} • Vassoura: ${reg.vassoura} • Podridão: ${reg.podridao}", fontSize = 11.sp, color = Color(0xFF2E7D32))
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                if (reg.uri != Uri.EMPTY) {
                                    IconButton(
                                        onClick = { onEditarRegistro(reg) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.Edit, "Editar / Revisar Caixas", tint = Color(0xFF2E7D32), modifier = Modifier.size(18.dp))
                                    }
                                }
                                IconButton(
                                    onClick = { registroParaExcluir = reg },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.Delete, "Excluir Registro", tint = Color(0xFFC62828), modifier = Modifier.size(18.dp))
                                }

                                // Ícone de status de sincronização
                                when (reg.statusSync) {
                                    "SINCRONIZADO" -> {
                                        Icon(Icons.Default.CloudDone, "Sincronizado", tint = Color(0xFF2E7D32), modifier = Modifier.size(20.dp).padding(start = 4.dp))
                                    }
                                    "ERRO" -> {
                                        Icon(Icons.Default.CloudOff, "Erro", tint = Color.Red, modifier = Modifier.size(20.dp).padding(start = 4.dp))
                                    }
                                    else -> {
                                        Icon(Icons.Default.Schedule, "Pendente", tint = Color(0xFFFFA000), modifier = Modifier.size(18.dp).padding(start = 4.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Copia uma imagem selecionada na galeria para o diretório privado do aplicativo,
 * garantindo caminho de arquivo real e persistência permanente no dispositivo.
 */
fun copiarUriGaleriaParaArquivoLocal(context: Context, uriOrigem: Uri): File? {
    return try {
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: context.filesDir
        val arquivoDestino = File(dir, "CACAU_GAL_${System.currentTimeMillis()}.jpg")
        context.contentResolver.openInputStream(uriOrigem)?.use { input ->
            arquivoDestino.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        arquivoDestino
    } catch (e: Exception) {
        null
    }
}

/**
 * Escapa um valor para uso seguro como campo CSV (RFC 4180): se o texto contém
 * vírgula, aspas ou quebra de linha, envolve tudo em aspas duplas e duplica
 * qualquer aspa interna. Sem isso, um nome de campanha/clone/coluna com vírgula
 * (bem comum em texto digitado por usuário) desalinha todas as colunas dali
 * pra frente na planilha exportada.
 */
internal fun csvField(value: Any?): String {
    val text = value?.toString() ?: ""
    return if (text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r")) {
        "\"" + text.replace("\"", "\"\"") + "\""
    } else {
        text
    }
}

internal fun formatarLinhaCsvPersonalizado(reg: RegistroCacau, itemPrincipal: String = reg.plantId.split("_").firstOrNull() ?: ""): String {
    val detJson = PersistenciaLocalRegistros.serializarCaixasParaCsv(reg.caixas)
    return listOf(
        reg.ensaioManual, reg.plantId, itemPrincipal, reg.parcelaManual,
        reg.numPlanta, reg.diagnostico, reg.data, reg.lat, reg.lng,
        reg.totalFrutos, reg.sadios, reg.vassoura, reg.podridao, reg.naoAvaliavel, detJson
    ).joinToString(",") { csvField(it) }
}

fun exportarCSVCEPEC_Personalizado(context: Context, registros: List<RegistroCacau>, l1: String, l2: String, l3: String) {
    val timestamp = System.currentTimeMillis()
    val nomeFinal = "Coleta_Custom_${timestamp}.csv"
    val conteudo = buildString {
        val header = listOf("Campanha", "ID_Gerado", l1, l2, l3, "Resultado", "Data", "Lat", "Lng", "Total_Frutos", "Sadios", "Vassoura", "Podridao", "Nao_Avaliavel", "Deteccoes_YOLO")
            .joinToString(",") { csvField(it) }
        appendLine(header)
        registros.forEach { reg ->
            val itemPrincipal = reg.plantId.split("_").firstOrNull() ?: ""
            appendLine(formatarLinhaCsvPersonalizado(reg, itemPrincipal))
        }
    }

    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, nomeFinal)
                put(MediaStore.MediaColumns.MIME_TYPE, "text/csv")
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/CEPEC")
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            if (uri != null) {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(conteudo.toByteArray(StandardCharsets.UTF_8))
                }
                Toast.makeText(context, "Planilha exportada com sucesso!", Toast.LENGTH_SHORT).show()
                return
            }
        }
        // Fallback robusto para Android < Q (API < 29, ex.: Galaxy A50 com Android 9)
        val downloadDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "CEPEC")
        if (!downloadDir.exists()) downloadDir.mkdirs()
        val file = File(downloadDir, nomeFinal)
        FileOutputStream(file).use { out ->
            out.write(conteudo.toByteArray(StandardCharsets.UTF_8))
        }
        Toast.makeText(context, "Planilha exportada em Downloads/CEPEC!", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(context, "Erro ao exportar CSV: ${e.message}", Toast.LENGTH_LONG).show()
    }
}
