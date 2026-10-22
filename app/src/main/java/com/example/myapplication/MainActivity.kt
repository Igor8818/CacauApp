package com.example.myapplication

import android.Manifest
import android.annotation.SuppressLint
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
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
import com.example.myapplication.data.SituacaoPlanta
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
    val operadorNome by viewModel.operador.collectAsStateWithLifecycle()
    val fazendaNome by viewModel.fazenda.collectAsStateWithLifecycle()
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
    var mostrarTelaModelo by rememberSaveable { mutableStateOf(false) }
    var mostrarDialogNovoEnsaio by remember { mutableStateOf(false) }
    var emTelaCheiaCamera by rememberSaveable { mutableStateOf(false) }

    var mostrarDialogMapeamento by remember { mutableStateOf(false) }
    val colunasEncontradas = remember { mutableStateListOf<String>() }
    var uriArquivoPendente by remember { mutableStateOf<Uri?>(null) }
    var avisoModeloExibido by rememberSaveable { mutableStateOf(false) }
    var ultimoToqueVoltar by remember { mutableLongStateOf(0L) }

    LaunchedEffect(abaSelecionada) {
        if (abaSelecionada == 2) {
            emTelaCheiaCamera = false
        }
    }

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
            Toast.makeText(context, "Nenhum modelo de IA encontrado. Importe um arquivo .tflite ou opere no modo manual.", Toast.LENGTH_LONG).show()
        }
    }

    // Leitor de planilha CSV de rota (suporta formato CEPLAC / Field Book e planilhas personalizadas)
    val launcherArquivoCSV = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            val cabecalho = lerApenasCabecalho(context, it)
            if (cabecalho.isNotEmpty()) {
                var idxClone = cabecalho.indexOfFirst { c -> c.trim().lowercase() == "clone" || c.trim().lowercase() == "v_id" }
                if (idxClone == -1) idxClone = cabecalho.indexOfFirst { c -> c.lowercase().contains("clone") || c.lowercase().contains("variedade") }
                var idxParcela = cabecalho.indexOfFirst { c -> c.trim().lowercase() == "parcela" || c.trim().lowercase() == "rep" }
                if (idxParcela == -1) idxParcela = cabecalho.indexOfFirst { c -> c.trim().lowercase() == "bloco" || c.lowercase().contains("parcela") || c.lowercase().contains("rep") }
                var idxPlanta = cabecalho.indexOfFirst { c -> c.trim().lowercase() == "planta" || c.trim().lowercase() == "p_id" }
                if (idxPlanta == -1) idxPlanta = cabecalho.indexOfFirst { c -> c.trim().lowercase() == "id" || c.lowercase().contains("planta") }

                if (idxClone != -1 && idxParcela != -1 && idxPlanta != -1) {
                    val lista = processarCSVComIndices(context, it, idxClone, idxParcela, idxPlanta)
                    viewModel.carregarRota(lista)
                    val jaColetadas = lista.count { p -> p.coletado }
                    val msgExtra = if (jaColetadas > 0) " ($jaColetadas já avaliadas)" else ""
                    Toast.makeText(context, "${lista.size} plantas carregadas na rota!$msgExtra", Toast.LENGTH_SHORT).show()
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

    // Detector helper compartilhado para diagnóstico na tela do Modelo IA
    val detectorHelperGlobal = remember(modelConfig) {
        buildDetectorHelper(context, modelConfig) {}
    }
    DisposableEffect(detectorHelperGlobal) {
        onDispose { detectorHelperGlobal?.fechar() }
    }

    // Roteamento Principal
    when {
        mostrarTelaModelo -> {
            TelaConfiguracaoModelo(
                configAtual = modelConfig,
                detectorHelper = detectorHelperGlobal,
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
                operador = operadorNome,
                onOperadorChange = { viewModel.setOperador(it) },
                fazenda = fazendaNome,
                onFazendaChange = { viewModel.setFazenda(it) },
                exibirBotaoVoltar = podeVoltarParaSessao,
                onVoltarClick = { viewModel.setConfigConcluida(true) },
                onContinuar = {
                    if (listaRegistros.isNotEmpty()) {
                        mostrarDialogNovoEnsaio = true
                    } else {
                        viewModel.iniciarNovoEnsaio(ensaioNome, operadorNome, fazendaNome)
                    }
                }
            )

            if (mostrarDialogNovoEnsaio) {
                AlertDialog(
                    onDismissRequest = { mostrarDialogNovoEnsaio = false },
                    title = { Text("Atualizar ou Iniciar Novo Ensaio?", fontWeight = FontWeight.Bold) },
                    text = {
                        Text(
                            "Você possui ${listaRegistros.size} registros salvos. Deseja manter os registros atuais apenas atualizando os dados da campanha, ou exportar/limpar para um novo ensaio?"
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                viewModel.setConfigConcluida(true)
                                mostrarDialogNovoEnsaio = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                        ) {
                            Text("Manter Registros")
                        }
                    },
                    dismissButton = {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            TextButton(onClick = {
                                exportarPlanilhaCEPEC(
                                    context,
                                    listaRegistros,
                                    ensaioNome,
                                    labelVariavel1,
                                    labelVariavel2,
                                    labelVariavel3
                                )
                                viewModel.limparRegistrosCampanha()
                                viewModel.iniciarNovoEnsaio(ensaioNome, operadorNome, fazendaNome)
                                mostrarDialogNovoEnsaio = false
                            }) {
                                Text("Exportar e Limpar")
                            }
                            TextButton(onClick = {
                                viewModel.limparRegistrosCampanha()
                                viewModel.iniciarNovoEnsaio(ensaioNome, operadorNome, fazendaNome)
                                mostrarDialogNovoEnsaio = false
                            }) {
                                Text("Limpar Direto", color = Color.Red)
                            }
                        }
                    }
                )
            }
        }

        else -> {
            BackHandler(enabled = !emTelaCheiaCamera && abaSelecionada != 0) {
                abaSelecionada = 0
            }

            BackHandler(enabled = !emTelaCheiaCamera && abaSelecionada == 0) {
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
                    // Oculta completamente a barra inferior quando a câmera ou tela de revisão pós-foto está ativa
                    if (!emTelaCheiaCamera) {
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
                        }
                    }
                }
            ) { padding ->
                val boxModifier = if (emTelaCheiaCamera) Modifier.fillMaxSize() else Modifier.padding(padding)
                Box(boxModifier) {
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
                            onTrocarCampanha = { viewModel.setConfigConcluida(false) },
                            jaExistePlantId = { id -> viewModel.existePlantId(id) },
                            onModoTelaCheiaChange = { emTelaCheiaCamera = it },
                            onSave = { uri, idSalvo, diagTexto, contagemIa, contagemFinal, dadosAgro ->
                                val data = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date())
                                val idCompleto = idSalvo.ifBlank { "${valorVariavel1}_${labelVariavel2}${pExibicao}_${labelVariavel3}${plExibicao}" }
                                val plantIntVal = if (tipoNumVariavel3) valorNumVariavel3 else 1
                                val plSalvaLabel = plExibicao

                                val reg = RegistroCacau(
                                    uri = uri,
                                    plantId = idCompleto,
                                    ensaioManual = ensaioNome,
                                    parcelaManual = pExibicao,
                                    diagnostico = diagTexto,
                                    data = data,
                                    numPlanta = plantIntVal,
                                    lat = latAtual,
                                    lng = lngAtual,
                                    contagemIa = contagemIa,
                                    contagemFinal = contagemFinal,
                                    modelo = modelConfig.nomeExibicao,
                                    threshold = modelConfig.threshold,
                                    operador = operadorNome,
                                    fazenda = fazendaNome,
                                    precisaoGpsM = gpsPrecisao,
                                    altitude = altitudeAtual,
                                    observacao = dadosAgro.observacaoFormatada,
                                    timestampMs = System.currentTimeMillis(),
                                    cloneExplicito = valorVariavel1,
                                    sitPla = dadosAgro.sitPla,
                                    nfPequenos = dadosAgro.nfPequenos,
                                    nfMedios = dadosAgro.nfMedios,
                                    nfAdultos = dadosAgro.nfAdultos,
                                    nfMaduros = dadosAgro.nfMaduros,
                                    nfVassoura = dadosAgro.nfVassoura,
                                    nfPodridao = dadosAgro.nfPodridao,
                                    nfOutrasPerdas = dadosAgro.nfOutrasPerdas,
                                    nvTotal = dadosAgro.nvTotal,
                                    corFruto = dadosAgro.corFruto,
                                    notaVirus = dadosAgro.notaVirus
                                )
                                // Garante que permanece na aba Coleta Livre (0) e persiste o registro imediatamente
                                abaSelecionada = 0
                                viewModel.salvarRegistro(reg, incrementarPlantaManual = true)

                                val detalheMsg = if (SituacaoPlanta.ehInativa(diagTexto)) diagTexto else "$diagTexto frutos"
                                coroutineScope.launch {
                                    val res = snackbarHostState.showSnackbar(
                                        message = "Planta $plSalvaLabel salva ($detalheMsg)",
                                        actionLabel = "Desfazer",
                                        duration = SnackbarDuration.Short
                                    )
                                    if (res == SnackbarResult.ActionPerformed) {
                                        viewModel.desfazerUltimoSalvoLivre()
                                    }
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
                            onModoTelaCheiaChange = { emTelaCheiaCamera = it },
                            onSaveColetaRota = { uri, pontoAnterior, contagemFinal, dadosAgro, contagemIaRevisada ->
                                val data = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date())
                                val idCompleto = pontoAnterior.idEfetivo
                                val contagemInt = contagemFinal.toIntOrNull() ?: 0
                                val eraColetadoAntes = pontoAnterior.coletado

                                val reg = RegistroCacau(
                                    uri = uri,
                                    plantId = idCompleto,
                                    ensaioManual = pontoAnterior.ensaio.ifBlank { ensaioNome },
                                    parcelaManual = pontoAnterior.parcela,
                                    diagnostico = contagemFinal,
                                    data = data,
                                    numPlanta = pontoAnterior.planta,
                                    lat = latAtual,
                                    lng = lngAtual,
                                    contagemIa = contagemIaRevisada,
                                    contagemFinal = contagemInt,
                                    modelo = modelConfig.nomeExibicao,
                                    threshold = modelConfig.threshold,
                                    operador = operadorNome,
                                    fazenda = fazendaNome,
                                    precisaoGpsM = gpsPrecisao,
                                    altitude = altitudeAtual,
                                    observacao = dadosAgro.observacaoFormatada,
                                    timestampMs = System.currentTimeMillis(),
                                    cloneExplicito = pontoAnterior.clone,
                                    bloco = pontoAnterior.bloco,
                                    ordem = pontoAnterior.ordem,
                                    sitPla2023c = pontoAnterior.sitPla2023c,
                                    sitPla2023L = pontoAnterior.sitPla2023L,
                                    sitPla = dadosAgro.sitPla,
                                    nfPequenos = dadosAgro.nfPequenos,
                                    nfMedios = dadosAgro.nfMedios,
                                    nfAdultos = dadosAgro.nfAdultos,
                                    nfMaduros = dadosAgro.nfMaduros,
                                    nfVassoura = dadosAgro.nfVassoura,
                                    nfPodridao = dadosAgro.nfPodridao,
                                    nfOutrasPerdas = dadosAgro.nfOutrasPerdas,
                                    nvTotal = dadosAgro.nvTotal,
                                    corFruto = dadosAgro.corFruto,
                                    notaVirus = dadosAgro.notaVirus
                                )
                                viewModel.salvarRegistro(reg, incrementarPlantaManual = false)
                                viewModel.marcarPontoRota(
                                    pontoOriginal = pontoAnterior,
                                    coletado = true,
                                    qtdFrutos = contagemFinal,
                                    observacao = dadosAgro.observacaoFormatada,
                                    sitPla = dadosAgro.sitPla,
                                    nfPequenos = dadosAgro.nfPequenos,
                                    nfMedios = dadosAgro.nfMedios,
                                    nfAdultos = dadosAgro.nfAdultos,
                                    nfMaduros = dadosAgro.nfMaduros,
                                    nfVassoura = dadosAgro.nfVassoura,
                                    nfPodridao = dadosAgro.nfPodridao,
                                    nfOutrasPerdas = dadosAgro.nfOutrasPerdas,
                                    nvTotal = dadosAgro.nvTotal,
                                    corFruto = dadosAgro.corFruto,
                                    notaVirus = dadosAgro.notaVirus
                                )
                                coroutineScope.launch {
                                    val res = snackbarHostState.showSnackbar(
                                        message = "${pontoAnterior.clone} P${pontoAnterior.parcela}-Pl${pontoAnterior.planta} salvo ($contagemFinal)",
                                        actionLabel = "Desfazer",
                                        duration = SnackbarDuration.Short
                                    )
                                    if (res == SnackbarResult.ActionPerformed) {
                                        viewModel.restaurarPontoRota(pontoAnterior, removerRegistroSeInedito = !eraColetadoAntes)
                                    }
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
                            },
                            onAtualizarRegistroCompleto = { atualizado ->
                                viewModel.atualizarRegistroCompleto(atualizado)
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
