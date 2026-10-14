package com.example.myapplication

import android.Manifest
import android.annotation.SuppressLint
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myapplication.data.RegistroCacau
import com.example.myapplication.userinterface.TelaConfiguracaoModelo
import com.example.myapplication.userinterface.components.SeletorDropdownSimples
import com.example.myapplication.userinterface.screens.*
import com.example.myapplication.userinterface.util.*
import com.example.myapplication.viewmodel.AppViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val appViewModel: AppViewModel = viewModel()
                    val dadosCarregados by appViewModel.dadosCarregados.collectAsStateWithLifecycle()
                    var splashVisivel by rememberSaveable { mutableStateOf(true) }

                    Box(Modifier.fillMaxSize()) {
                        AppCacauCEPEC(appViewModel)
                        AnimatedVisibility(
                            visible = splashVisivel,
                            exit = fadeOut(tween(450))
                        ) {
                            SplashScreen(
                                dadosCarregados = dadosCarregados,
                                onConcluir = { splashVisivel = false }
                            )
                        }
                    }
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

    // Estados coletados do ViewModel
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
    var mostrarTelaDiagnostico by remember { mutableStateOf(false) }
    var mostrarDialogNovoEnsaio by remember { mutableStateOf(false) }

    var mostrarDialogMapeamento by remember { mutableStateOf(false) }
    val colunasEncontradas = remember { mutableStateListOf<String>() }
    var uriArquivoPendente by remember { mutableStateOf<Uri?>(null) }
    var avisoModeloExibido by rememberSaveable { mutableStateOf(false) }
    var ultimoToqueVoltar by remember { mutableLongStateOf(0L) }

    BackHandler(enabled = mostrarDialogNovoEnsaio) {
        mostrarDialogNovoEnsaio = false
    }

    BackHandler(enabled = mostrarDialogMapeamento) {
        mostrarDialogMapeamento = false
        uriArquivoPendente = null
    }

    // Permissões de Câmera e Localização
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

    // Alerta automático se não houver modelo inicial
    LaunchedEffect(configConcluida) {
        if (configConcluida && !avisoModeloExibido && !modeloDisponivel(context, modelConfig)) {
            avisoModeloExibido = true
            Toast.makeText(context, "Nenhum modelo de IA encontrado. Importe um arquivo .tflite.", Toast.LENGTH_LONG).show()
            mostrarTelaModelo = true
        }
    }

    // Leitor de planilha CSV de rota
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

    // Detector helper compartilhado para diagnóstico
    val detectorHelperGlobal = remember(modelConfig) {
        buildDetectorHelper(context, modelConfig) {}
    }
    DisposableEffect(detectorHelperGlobal) {
        onDispose { detectorHelperGlobal?.fechar() }
    }

    // Roteamento
    when {
        mostrarTelaDiagnostico -> {
            DiagnosticoScreen(
                modelConfig = modelConfig,
                detectorHelper = detectorHelperGlobal,
                onVoltar = { mostrarTelaDiagnostico = false }
            )
        }

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
            val podeVoltarParaSessao = listaRegistros.isNotEmpty() || rotaPreCarregada.isNotEmpty()
            BackHandler(enabled = podeVoltarParaSessao) {
                viewModel.setConfigConcluida(true)
            }
            BackHandler(enabled = !podeVoltarParaSessao) {
                val agora = System.currentTimeMillis()
                if (agora - ultimoToqueVoltar < 2000L) {
                    (context as? ComponentActivity)?.finish()
                } else {
                    ultimoToqueVoltar = agora
                    Toast.makeText(context, "Pressione voltar novamente para sair", Toast.LENGTH_SHORT).show()
                }
            }

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
            BackHandler(enabled = abaSelecionada != 0) {
                abaSelecionada = 0
            }

            BackHandler(enabled = abaSelecionada == 0) {
                val agora = System.currentTimeMillis()
                if (agora - ultimoToqueVoltar < 2000L) {
                    (context as? ComponentActivity)?.finish()
                } else {
                    ultimoToqueVoltar = agora
                    Toast.makeText(context, "Pressione voltar novamente para sair do CacauApp", Toast.LENGTH_SHORT).show()
                }
            }

            Scaffold(
                snackbarHost = { SnackbarHost(snackbarHostState) },
                bottomBar = {
                    val navItemColors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF1B5E20),
                        selectedTextColor = Color(0xFF1B5E20),
                        indicatorColor = Color(0xFFE8F5E9),
                        unselectedIconColor = Color(0xFF6D4C41),
                        unselectedTextColor = Color(0xFF6D4C41)
                    )
                    NavigationBar(
                        containerColor = Color(0xFFFAF7F2),
                        tonalElevation = 8.dp
                    ) {
                        NavigationBarItem(
                            selected = abaSelecionada == 0,
                            onClick = { abaSelecionada = 0 },
                            icon = { Icon(Icons.Default.PhotoCamera, null) },
                            label = { Text("Coleta Livre", fontWeight = if (abaSelecionada == 0) FontWeight.Bold else FontWeight.Normal) },
                            colors = navItemColors
                        )
                        NavigationBarItem(
                            selected = abaSelecionada == 1,
                            onClick = { abaSelecionada = 1 },
                            icon = { Icon(Icons.Default.Route, null) },
                            label = { Text("Modo Rota", fontWeight = if (abaSelecionada == 1) FontWeight.Bold else FontWeight.Normal) },
                            colors = navItemColors
                        )
                        NavigationBarItem(
                            selected = abaSelecionada == 2,
                            onClick = { abaSelecionada = 2 },
                            icon = {
                                BadgedBox(badge = {
                                    if (listaRegistros.isNotEmpty()) {
                                        Badge(containerColor = Color(0xFF2E7D32)) { Text("${listaRegistros.size}") }
                                    }
                                }) {
                                    Icon(Icons.Default.Collections, null)
                                }
                            },
                            label = { Text("Galeria", fontWeight = if (abaSelecionada == 2) FontWeight.Bold else FontWeight.Normal) },
                            colors = navItemColors
                        )
                        NavigationBarItem(
                            selected = false,
                            onClick = { mostrarTelaModelo = true },
                            icon = { Icon(Icons.Default.SmartToy, null) },
                            label = { Text("Modelo IA") },
                            colors = navItemColors
                        )
                        NavigationBarItem(
                            selected = false,
                            onClick = { mostrarTelaDiagnostico = true },
                            icon = { Icon(Icons.Default.Info, null) },
                            label = { Text("Diagnóstico") },
                            colors = navItemColors
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
                            parcelaNum = valorNumVariavel2,
                            onParcelaNumChange = { novaParcela ->
                                val anterior = valorNumVariavel2
                                viewModel.setValorNum2(novaParcela)
                                if (novaParcela > anterior && tipoNumVariavel3) {
                                    viewModel.setValorNum3(1)
                                    Toast.makeText(context, "$labelVariavel2 $novaParcela iniciada ($labelVariavel3 reiniciada em 1)", Toast.LENGTH_SHORT).show()
                                }
                            },
                            parcelaTxt = valorTxtVariavel2, onParcelaTxtChange = { viewModel.setValorTxt2(it) },
                            plantaNum = valorNumVariavel3, onPlantaNumChange = { viewModel.setValorNum3(it) },
                            plantaTxt = valorTxtVariavel3, onPlantaTxtChange = { viewModel.setValorTxt3(it) },
                            modelConfig = modelConfig,
                            gpsPrecisao = gpsPrecisao,
                            temFixGps = temFixGps,
                            onAbrirModelo = { mostrarTelaModelo = true },
                            jaExistePlantId = { id -> viewModel.existePlantId(id) },
                            onSave = { uri, _, contagemIa, contagemFinal ->
                                val data = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date())
                                val idCompleto = "${valorVariavel1}_${labelVariavel2}${pExibicao}_${labelVariavel3}${plExibicao}"
                                val plantIntVal = if (tipoNumVariavel3) valorNumVariavel3 else 1

                                val reg = RegistroCacau(
                                    uri = uri,
                                    plantId = idCompleto,
                                    ensaioManual = ensaioNome,
                                    parcelaManual = pExibicao,
                                    diagnostico = contagemFinal.toString(),
                                    data = data,
                                    numPlanta = plantIntVal,
                                    lat = latAtual,
                                    lng = lngAtual,
                                    contagemIa = contagemIa,
                                    contagemFinal = contagemFinal,
                                    modelo = modelConfig.nomeExibicao,
                                    threshold = modelConfig.threshold,
                                    precisaoGpsM = gpsPrecisao,
                                    altitude = altitudeAtual,
                                    timestampMs = System.currentTimeMillis()
                                )
                                viewModel.salvarRegistro(reg)
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("Planta $idCompleto salva ($contagemFinal frutos)")
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
                            onSaveColetaRota = { uri, ponto, contagemFinal, observacao, contagemIaRevisada ->
                                val data = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date())
                                val idCompleto = "${ponto.clone}_P${ponto.parcela}_Pl${ponto.planta}"
                                val contagemInt = contagemFinal.toIntOrNull() ?: 0

                                val reg = RegistroCacau(
                                    uri = uri,
                                    plantId = idCompleto,
                                    ensaioManual = ensaioNome,
                                    parcelaManual = ponto.parcela,
                                    diagnostico = contagemFinal,
                                    data = data,
                                    numPlanta = ponto.planta,
                                    lat = latAtual,
                                    lng = lngAtual,
                                    contagemIa = contagemIaRevisada,
                                    contagemFinal = contagemInt,
                                    modelo = modelConfig.nomeExibicao,
                                    threshold = modelConfig.threshold,
                                    precisaoGpsM = gpsPrecisao,
                                    altitude = altitudeAtual,
                                    observacao = observacao,
                                    timestampMs = System.currentTimeMillis()
                                )
                                viewModel.salvarRegistro(reg)
                                viewModel.marcarPontoRota(ponto, coletado = true, qtdFrutos = contagemFinal, observacao = observacao)
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
