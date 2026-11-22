package br.gov.ceplac.cepec.cacauapp

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import br.gov.ceplac.cepec.cacauapp.data.ModoIACacau
import br.gov.ceplac.cepec.cacauapp.ui.theme.CacauFundoSolar
import br.gov.ceplac.cepec.cacauapp.ui.theme.CacauTheme
import br.gov.ceplac.cepec.cacauapp.ui.theme.CacauVerdeClaro
import br.gov.ceplac.cepec.cacauapp.ui.theme.CacauVerdeEscuro
import br.gov.ceplac.cepec.cacauapp.userinterface.CameraFullscreenCacau
import br.gov.ceplac.cepec.cacauapp.userinterface.DialogConfiguracaoRendimentoCacau
import br.gov.ceplac.cepec.cacauapp.userinterface.DialogConfiguracaoServidor
import br.gov.ceplac.cepec.cacauapp.userinterface.TelaColetaLivreCacau
import br.gov.ceplac.cepec.cacauapp.userinterface.TelaConfiguracaoModelo
import br.gov.ceplac.cepec.cacauapp.userinterface.TelaDadosERanking
import br.gov.ceplac.cepec.cacauapp.userinterface.TelaInicioCacau
import br.gov.ceplac.cepec.cacauapp.userinterface.TelaModoRotaCacau
import br.gov.ceplac.cepec.cacauapp.userinterface.TelaRevisaoCaptura
import br.gov.ceplac.cepec.cacauapp.userinterface.VolumeShutterBridge
import br.gov.ceplac.cepec.cacauapp.userinterface.util.ObjectDetectorHelper
import br.gov.ceplac.cepec.cacauapp.userinterface.util.copiarUriGaleriaLocal
import br.gov.ceplac.cepec.cacauapp.viewmodel.CacauViewModel
import br.gov.ceplac.cepec.cacauapp.viewmodel.DestinoCaptura
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import org.tensorflow.lite.task.vision.detector.Detection

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val viewModel = ViewModelProvider(this)[CacauViewModel::class.java]
        setContent {
            CacauTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = CacauFundoSolar) {
                    AppCacauCepec(viewModel = viewModel)
                }
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            if (VolumeShutterBridge.onVolumePress?.invoke() == true) {
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }
}

@SuppressLint("MissingPermission")
@Composable
fun AppCacauCepec(viewModel: CacauViewModel) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    LaunchedEffect(viewModel) {
        viewModel.eventosToast.collect { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
        }
    }

    // Permissões de Câmera e GPS (Fino com fallback para Aproximado)
    var permFineConcedida by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var permCoarseConcedida by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val temAlgumaLoc = permFineConcedida || permCoarseConcedida

    val launcherPermissoes = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissoes ->
        permFineConcedida = permissoes[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        permCoarseConcedida = permissoes[Manifest.permission.ACCESS_COARSE_LOCATION] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    LaunchedEffect(Unit) {
        val permCam = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
        if (permCam != PackageManager.PERMISSION_GRANTED || !temAlgumaLoc) {
            launcherPermissoes.launch(
                arrayOf(
                    Manifest.permission.CAMERA,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    DisposableEffect(temAlgumaLoc, permFineConcedida) {
        if (!temAlgumaLoc) {
            onDispose { }
        } else {
            val callback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    result.lastLocation?.let {
                        viewModel.atualizarLocalizacao(it.latitude, it.longitude)
                    }
                }
            }
            val prioridade = if (permFineConcedida) {
                Priority.PRIORITY_HIGH_ACCURACY
            } else {
                Priority.PRIORITY_BALANCED_POWER_ACCURACY
            }
            val iniciado = runCatching {
                val req = LocationRequest.Builder(prioridade, 2500L).build()
                fusedLocationClient.requestLocationUpdates(
                    req,
                    callback,
                    android.os.Looper.getMainLooper()
                )
            }.isSuccess
            onDispose {
                if (iniciado) {
                    runCatching { fusedLocationClient.removeLocationUpdates(callback) }
                }
            }
        }
    }

    // Motor TFLite ao vivo para preview
    var liveDetections by remember { mutableStateOf<List<Detection>>(emptyList()) }
    var liveBmpW by remember { mutableStateOf(640f) }
    var liveBmpH by remember { mutableStateOf(480f) }
    var liveInferenciaMs by remember { mutableLongStateOf(0L) }

    val modelConfig = uiState.modelConfig
    val detectorPreview = remember(
        modelConfig.arquivoPrincipalEfetivo,
        modelConfig.modeloInternoPath,
        modelConfig.modeloExternoUri,
        modelConfig.modoEfetivo
    ) {
        val src = when {
            !modelConfig.modeloInternoPath.isNullOrBlank() -> ObjectDetectorHelper.ModelSource.InternalFile(
                modelConfig.modeloInternoPath!!,
                modelConfig.modeloExternoNome.ifBlank { "Modelo CEPEC" }
            )
            modelConfig.modeloExternoUri != null -> ObjectDetectorHelper.ModelSource.External(
                modelConfig.modeloExternoUri!!,
                modelConfig.modeloExternoNome.ifBlank { "Modelo CEPEC" }
            )
            else -> ObjectDetectorHelper.ModelSource.Asset(modelConfig.arquivoPrincipalEfetivo.ifBlank { "detector.tflite" })
        }
        val thresh = if (modelConfig.modoEfetivo == ModoIACacau.SOMENTE_DOENCAS) {
            modelConfig.thresholdDoenca
        } else {
            modelConfig.threshold
        }
        ObjectDetectorHelper(
            context = context,
            detectorListener = null,
            modelSource = src,
            scoreThreshold = thresh,
            maxResults = modelConfig.maxResultados,
            customLabels = modelConfig.labelsFiltro
        )
    }

    LaunchedEffect(
        modelConfig.threshold,
        modelConfig.thresholdDoenca,
        modelConfig.maxResultados,
        modelConfig.modoEfetivo
    ) {
        detectorPreview.scoreThreshold = if (modelConfig.modoEfetivo == ModoIACacau.SOMENTE_DOENCAS) {
            modelConfig.thresholdDoenca
        } else {
            modelConfig.threshold
        }
        detectorPreview.maxResults = modelConfig.maxResultados
    }

    DisposableEffect(detectorPreview) {
        onDispose {
            detectorPreview.fechar()
        }
    }

    val launcherGaleriaLivre = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val copiaLocal = copiarUriGaleriaLocal(context, uri)
            val uriUsada = copiaLocal?.let { Uri.fromFile(it) } ?: uri
            viewModel.abrirRevisaoGaleriaLivre(uriUsada)
        }
    }

    val emTelaCheia = uiState.emTelaCheia

    Scaffold(
        containerColor = CacauFundoSolar,
        bottomBar = {
            if (!emTelaCheia) {
                NavigationBar(containerColor = Color.White, tonalElevation = 4.dp) {
                    val itens = listOf(
                        Triple(0, "Início", Icons.Default.Home),
                        Triple(1, "Rota", Icons.Default.Route),
                        Triple(2, "Livre", Icons.Default.CameraAlt),
                        Triple(3, "Dados", Icons.Default.Analytics)
                    )
                    itens.forEach { (idx, titulo, icone) ->
                        NavigationBarItem(
                            selected = uiState.abaSelecionada == idx,
                            onClick = { viewModel.selecionarAba(idx) },
                            icon = { Icon(icone, contentDescription = titulo) },
                            label = {
                                Text(
                                    titulo,
                                    fontWeight = if (uiState.abaSelecionada == idx) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 11.sp
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = CacauVerdeEscuro,
                                selectedTextColor = CacauVerdeEscuro,
                                indicatorColor = CacauVerdeClaro
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (emTelaCheia) PaddingValues(0.dp) else innerPadding)
        ) {
            when {
                uiState.uriFotoEmRevisao != null -> {
                    val uriRev = uiState.uriFotoEmRevisao!!
                    val temProxima = uiState.destinoCaptura == DestinoCaptura.ROTA ||
                        uiState.destinoCaptura == DestinoCaptura.LIVRE
                    val rotuloProx = if (uiState.destinoCaptura == DestinoCaptura.ROTA) {
                        "Salvar + Próx. →"
                    } else {
                        "Salvar + Pl.${uiState.numPlantaLivre + 1}"
                    }

                    TelaRevisaoCaptura(
                        fotoUri = uriRev,
                        plantId = uiState.plantIdEmCaptura,
                        modelConfig = uiState.modelConfig,
                        caixasIniciais = uiState.caixasIniciaisRevisao,
                        pularInferenciaInicial = (uiState.destinoCaptura == DestinoCaptura.EDICAO_DADOS),
                        configRendimento = uiState.configRendimento,
                        mostrarBotaoProximaFoto = temProxima,
                        rotuloBotaoProximaFoto = rotuloProx,
                        onRefazer = { viewModel.refazerFotoRevisao() },
                        onSalvar = { caixas, tot, sad, vas, pod, nav ->
                            viewModel.salvarRevisao(caixas, tot, sad, vas, pod, nav)
                        },
                        onSalvarEProxima = { caixas, tot, sad, vas, pod, nav ->
                            viewModel.salvarRevisaoEProxima(caixas, tot, sad, vas, pod, nav)
                        }
                    )
                }

                uiState.cameraAberta -> {
                    CameraFullscreenCacau(
                        plantId = uiState.plantIdEmCaptura.ifBlank { "TESTE_IA" },
                        modelConfig = uiState.modelConfig,
                        helper = detectorPreview,
                        detections = liveDetections,
                        bitmapWidth = liveBmpW,
                        bitmapHeight = liveBmpH,
                        inferenciaMs = liveInferenciaMs,
                        onResultsUpdated = { res ->
                            liveDetections = res.deteccoes
                            liveBmpW = res.larguraImagem
                            liveBmpH = res.alturaImagem
                            liveInferenciaMs = res.inferenciaMs
                        },
                        onTrocarModoIA = { novoModo -> viewModel.trocarModoIA(novoModo) },
                        onAtualizarThreshold = { novoThresh -> viewModel.atualizarThresholdPreview(novoThresh) },
                        onFotoCapturada = { uri -> viewModel.aoFotoCapturada(uri) },
                        onAbrirGaleria = if (uiState.destinoCaptura == DestinoCaptura.LIVRE) {
                            {
                                viewModel.fecharCamera()
                                launcherGaleriaLivre.launch("image/*")
                            }
                        } else null,
                        onFechar = { viewModel.fecharCamera() }
                    )
                }

                uiState.mostrarConfigIA -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(CacauFundoSolar)
                            .statusBarsPadding()
                    ) {
                        Surface(color = Color.White, shadowElevation = 2.dp) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(onClick = { viewModel.abrirConfigIA(false) }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                                }
                                Text(
                                    "Configuração de IA Cacau",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = CacauVerdeEscuro
                                )
                            }
                        }
                        TelaConfiguracaoModelo(
                            configAtual = uiState.modelConfig,
                            inferenciaMsAtual = liveInferenciaMs,
                            onAtualizarConfig = { viewModel.atualizarConfigModelo(it) },
                            onTestarCamera = { viewModel.abrirTesteCameraIA() }
                        )
                    }
                }

                else -> {
                    when (uiState.abaSelecionada) {
                        0 -> TelaInicioCacau(
                            configEnsaio = uiState.configEnsaio,
                            registros = uiState.registros,
                            rota = uiState.rota,
                            modelConfig = uiState.modelConfig,
                            configRendimento = uiState.configRendimento,
                            sincronizandoPc = uiState.sincronizandoPc,
                            servidorConfigurado = uiState.servidorConfigurado,
                            onAtualizarCodigoEnsaio = { viewModel.atualizarCodigoEnsaio(it) },
                            onCriarNovoEnsaio = { cod, manter -> viewModel.criarNovoEnsaio(cod, manter) },
                            onAlternarEnsaio = { viewModel.alternarEnsaio(it) },
                            onAbrirModoRota = { viewModel.selecionarAba(1) },
                            onAbrirColetaLivre = { viewModel.selecionarAba(2) },
                            onAbrirDadosERanking = { viewModel.selecionarAba(3) },
                            onAbrirConfigIA = { viewModel.abrirConfigIA(true) },
                            onTestarCameraIA = { viewModel.abrirTesteCameraIA() },
                            onSincronizarPc = { viewModel.sincronizarComPcAgora() },
                            onAbrirConfigServidor = { viewModel.abrirConfigServidor(true) },
                            onAbrirConfigRendimento = { viewModel.abrirConfigRendimento(true) }
                        )

                        1 -> TelaModoRotaCacau(
                            configEnsaio = uiState.configEnsaio,
                            modelConfig = uiState.modelConfig,
                            rota = uiState.rota,
                            onAbrirConfigIA = { viewModel.abrirConfigIA(true) },
                            onAtualizarRota = { novaRota, novoEnsaio -> viewModel.atualizarRota(novaRota, novoEnsaio) },
                            onFotografarPlanta = { item -> viewModel.abrirCameraRota(item) },
                            onMarcarNaoColetavel = { item -> viewModel.marcarNaoColetavelRota(item) }
                        )

                        2 -> TelaColetaLivreCacau(
                            configEnsaio = uiState.configEnsaio,
                            modelConfig = uiState.modelConfig,
                            registros = uiState.registros,
                            clone = uiState.cloneLivre,
                            parcela = uiState.parcelaLivre,
                            numPlanta = uiState.numPlantaLivre,
                            onAbrirConfigIA = { viewModel.abrirConfigIA(true) },
                            onAlterarClone = { novoClone, resetarPlanta ->
                                viewModel.alterarCloneLivre(novoClone, resetarPlanta)
                            },
                            onAlterarParcela = { novaParcela, resetarPlanta ->
                                viewModel.alterarParcelaLivre(novaParcela, resetarPlanta)
                            },
                            onAlterarNumPlanta = { viewModel.alterarNumPlantaLivre(it) },
                            onAbrirCamera = { mesmoAngulo -> viewModel.abrirCameraLivre(mesmoAngulo) },
                            onImportarGaleria = { mesmoAngulo ->
                                viewModel.prepararGaleriaLivre(mesmoAngulo)
                                launcherGaleriaLivre.launch("image/*")
                            },
                            onMarcarNaoColetavel = { viewModel.marcarNaoColetavelLivre() }
                        )

                        3 -> TelaDadosERanking(
                            configEnsaio = uiState.configEnsaio,
                            registros = uiState.registros,
                            configRendimento = uiState.configRendimento,
                            sincronizandoPc = uiState.sincronizandoPc,
                            onSincronizarPc = { viewModel.sincronizarComPcAgora() },
                            onAbrirConfigServidor = { viewModel.abrirConfigServidor(true) },
                            onAbrirConfigRendimento = { viewModel.abrirConfigRendimento(true) },
                            onEditarRegistro = { origIdx, atualizado -> viewModel.editarRegistro(origIdx, atualizado) },
                            onRevisarFotoRegistro = { origIdx, reg -> viewModel.abrirRevisaoRegistro(origIdx, reg) },
                            onExcluirRegistro = { origIdx -> viewModel.excluirRegistro(origIdx) }
                        )
                    }
                }
            }
        }
    }

    if (uiState.mostrarConfigRendimento) {
        DialogConfiguracaoRendimentoCacau(
            configAtual = uiState.configRendimento,
            onDismiss = { viewModel.abrirConfigRendimento(false) },
            onSalvar = { novo -> viewModel.atualizarConfigRendimento(novo) }
        )
    }

    if (uiState.mostrarConfigServidor) {
        DialogConfiguracaoServidor(
            onDismiss = { viewModel.abrirConfigServidor(false) },
            onSalvar = { url, email, pass, ensaio ->
                viewModel.salvarConfigServidor(url, email, pass, ensaio)
            }
        )
    }
}
