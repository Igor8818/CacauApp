package com.example.myapplication

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.myapplication.data.CLONES_CEPEC_PADRAO
import com.example.myapplication.data.ConfigRendimentoCacau
import com.example.myapplication.data.ModelConfig
import com.example.myapplication.data.ModoIACacau
import com.example.myapplication.data.RegistroCacau
import com.example.myapplication.data.RotaColeta
import com.example.myapplication.network.CacauSyncClient
import com.example.myapplication.ui.theme.CacauBordaSuave
import com.example.myapplication.ui.theme.CacauDourado
import com.example.myapplication.ui.theme.CacauFundoSolar
import com.example.myapplication.ui.theme.CacauInfo
import com.example.myapplication.ui.theme.CacauInfoClaro
import com.example.myapplication.ui.theme.CacauPerigo
import com.example.myapplication.ui.theme.CacauTextoPrincipal
import com.example.myapplication.ui.theme.CacauTextoSecundario
import com.example.myapplication.ui.theme.CacauTheme
import com.example.myapplication.ui.theme.CacauVerdeBotanico
import com.example.myapplication.ui.theme.CacauVerdeClaro
import com.example.myapplication.ui.theme.CacauVerdeEscuro
import com.example.myapplication.userinterface.BarraStatusModeloCacau
import com.example.myapplication.userinterface.CaixaAuditavel
import com.example.myapplication.userinterface.CameraFullscreenCacau
import com.example.myapplication.userinterface.DialogConfiguracaoRendimentoCacau
import com.example.myapplication.userinterface.TelaConfiguracaoModelo
import com.example.myapplication.userinterface.TelaDadosERanking
import com.example.myapplication.userinterface.TelaInicioCacau
import com.example.myapplication.userinterface.TelaRevisaoCaptura
import com.example.myapplication.userinterface.VolumeShutterBridge
import com.example.myapplication.userinterface.util.ImportadorPlanilhaUniversal
import com.example.myapplication.userinterface.util.ObjectDetectorHelper
import com.example.myapplication.userinterface.util.PersistenciaLocalRegistros
import com.example.myapplication.userinterface.util.copiarUriGaleriaLocal
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.tensorflow.lite.task.vision.detector.Detection
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CacauTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = CacauFundoSolar) {
                    AppCacauCepec()
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

private enum class DestinoCaptura {
    LIVRE,
    ROTA,
    TESTE_IA,
    EDICAO_DADOS
}

private data class AlvoRotaCaptura(
    val clone: String,
    val parcela: String,
    val planta: Int
)

@SuppressLint("MissingPermission")
@Composable
fun AppCacauCepec() {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    var abaSelecionada by remember { mutableIntStateOf(0) } // 0=Início, 1=Rota, 2=Livre, 3=Dados

    // Estado persistido local
    var configEnsaio by remember { mutableStateOf(PersistenciaLocalRegistros.carregarConfigEnsaio(context)) }
    var configRendimento by remember { mutableStateOf(PersistenciaLocalRegistros.carregarConfigRendimento(context)) }
    var modelConfig by remember { mutableStateOf(PersistenciaLocalRegistros.carregarConfiguracaoModelo(context)) }
    val listaRegistros = remember {
        mutableStateListOf<RegistroCacau>().apply {
            addAll(PersistenciaLocalRegistros.carregarRegistros(context))
        }
    }
    val rotaPreCarregada = remember {
        mutableStateListOf<RotaColeta>().apply {
            addAll(PersistenciaLocalRegistros.carregarRotaPrecarregada(context).second)
        }
    }
    var timestampRota by remember { mutableLongStateOf(PersistenciaLocalRegistros.obterTimestampRotaPrecarregada(context)) }

    // Coordenadas GPS
    var latAtual by remember { mutableDoubleStateOf(0.0) }
    var lngAtual by remember { mutableDoubleStateOf(0.0) }
    var permLocConcedida by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val launcherPermissoes = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissoes ->
        permLocConcedida = permissoes[Manifest.permission.ACCESS_FINE_LOCATION] == true
    }

    DisposableEffect(permLocConcedida) {
        if (!permLocConcedida) {
            onDispose { }
        } else {
            val callback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    result.lastLocation?.let {
                        latAtual = it.latitude
                        lngAtual = it.longitude
                    }
                }
            }
            val iniciado = runCatching {
                val req = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2500L).build()
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

    LaunchedEffect(Unit) {
        val permCam = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
        val permLoc = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        if (permCam != PackageManager.PERMISSION_GRANTED || permLoc != PackageManager.PERMISSION_GRANTED) {
            launcherPermissoes.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.ACCESS_FINE_LOCATION))
        }

        // Hot-reload automático se o CacauSync enviar nova rota via cabo USB ADB
        while (true) {
            delay(2500L)
            val novoTs = PersistenciaLocalRegistros.obterTimestampRotaPrecarregada(context)
            if (novoTs > timestampRota) {
                timestampRota = novoTs
                val (codEnsaioRota, itensRota) = PersistenciaLocalRegistros.carregarRotaPrecarregada(context)
                if (itensRota.isNotEmpty()) {
                    rotaPreCarregada.clear()
                    rotaPreCarregada.addAll(itensRota)
                    if (codEnsaioRota.isNotBlank() && codEnsaioRota != configEnsaio.codigoEnsaio) {
                        configEnsaio = configEnsaio.copy(codigoEnsaio = codEnsaioRota)
                        PersistenciaLocalRegistros.salvarConfigEnsaio(context, configEnsaio)
                    }
                    Toast.makeText(context, "Nova rota recebida via USB (${itensRota.size} plantas)!", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Estado da câmera ao vivo & revisão
    var cameraAberta by remember { mutableStateOf(false) }
    var destinoCaptura by remember { mutableStateOf(DestinoCaptura.LIVRE) }
    var plantIdEmCaptura by remember { mutableStateOf("") }
    var uriFotoEmRevisao by remember { mutableStateOf<Uri?>(null) }
    var caixasIniciaisRevisao by remember { mutableStateOf<List<CaixaAuditavel>>(emptyList()) }
    var indiceEditandoDados by remember { mutableIntStateOf(-1) }
    var mostrarConfigIA by remember { mutableStateOf(false) }
    var mostrarConfigRendimento by remember { mutableStateOf(false) }
    var mostrarConfigServidor by remember { mutableStateOf(false) }
    var sincronizandoPc by remember { mutableStateOf(false) }

    // Estado Coleta Livre
    var cloneLivre by remember { mutableStateOf(CLONES_CEPEC_PADRAO.first()) }
    var parcelaLivre by remember { mutableStateOf("1") }
    var numPlantaLivre by remember { mutableIntStateOf(1) }
    var gerarProximoAnguloLivre by remember { mutableStateOf(false) }
    var sufixoFotoLivreConfirmado by remember { mutableStateOf<String?>(null) }
    var alvoRotaAtual by remember { mutableStateOf<AlvoRotaCaptura?>(null) }

    // Motor TFLite ao vivo para preview
    var liveDetections by remember { mutableStateOf<List<Detection>>(emptyList()) }
    var liveBmpW by remember { mutableStateOf(640f) }
    var liveBmpH by remember { mutableStateOf(480f) }
    var liveInferenciaMs by remember { mutableLongStateOf(0L) }

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

    LaunchedEffect(modelConfig.threshold, modelConfig.thresholdDoenca, modelConfig.maxResultados, modelConfig.modoEfetivo) {
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

    val prefsSync = remember { context.getSharedPreferences("cacausync_prefs", Context.MODE_PRIVATE) }
    var servidorConfigurado by remember {
        mutableStateOf(!(prefsSync.getString("server_url", "") ?: "").isBlank())
    }

    fun atualizarConfigModelo(novo: ModelConfig) {
        modelConfig = novo
        PersistenciaLocalRegistros.salvarConfiguracaoModelo(context, novo)
    }

    fun sincronizarComPcAgora() {
        if (sincronizandoPc || listaRegistros.isEmpty()) return
        sincronizandoPc = true
        scope.launch {
            val statusAtualizados = java.util.concurrent.ConcurrentHashMap<String, String>()
            val res = withContext(Dispatchers.IO) {
                CacauSyncClient.sincronizarAutomatico(
                    context = context,
                    ensaioPadrao = configEnsaio.codigoEnsaio,
                    registros = listaRegistros.toList(),
                    onProgresso = {},
                    onStatusRegistro = { pid, st ->
                        statusAtualizados[pid] = st
                    }
                )
            }
            if (statusAtualizados.isNotEmpty()) {
                for (i in listaRegistros.indices) {
                    val novoSt = statusAtualizados[listaRegistros[i].plantId]
                    if (novoSt != null) {
                        listaRegistros[i] = listaRegistros[i].copy(statusSync = novoSt)
                    }
                }
            }
            sincronizandoPc = false
            res.onSuccess { syncRes ->
                PersistenciaLocalRegistros.salvarRegistros(context, listaRegistros.toList())
                Toast.makeText(
                    context,
                    "Enviado! ${syncRes.novasInseridas} nova(s), ${syncRes.fotosEnviadas} foto(s) em ${syncRes.servidorUtilizado}",
                    Toast.LENGTH_LONG
                ).show()
            }.onFailure { err ->
                Toast.makeText(
                    context,
                    "PC não encontrado (${err.message ?: "verifique Wi-Fi ou cabo USB"})",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    fun registrarPlantaSalva(
        pidFinal: String,
        fotoUri: Uri,
        caixas: List<CaixaAuditavel>,
        total: Int,
        sadios: Int,
        vassoura: Int,
        podridao: Int,
        naoAval: Int,
        diagnosticoCustom: String? = null,
        naoColetavelFlag: Boolean = false,
        parcelaRegistro: String = alvoRotaAtual?.parcela?.takeIf { destinoCaptura == DestinoCaptura.ROTA } ?: parcelaLivre,
        numPlantaRegistro: Int = alvoRotaAtual?.planta?.takeIf { destinoCaptura == DestinoCaptura.ROTA } ?: numPlantaLivre
    ) {
        val dataStr = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.ROOT).format(Date())
        val ehNaoColetavel = naoColetavelFlag || (diagnosticoCustom != null && CacauSyncClient.isNaoColetavel(diagnosticoCustom))
        val diag = diagnosticoCustom ?: if (ehNaoColetavel) "Não Coletável" else total.toString()

        val novoReg = RegistroCacau(
            plantId = pidFinal,
            parcelaManual = parcelaRegistro,
            ensaioManual = configEnsaio.codigoEnsaio,
            diagnostico = diag,
            data = dataStr,
            uri = fotoUri,
            lat = latAtual,
            lng = lngAtual,
            numPlanta = numPlantaRegistro,
            totalFrutos = total,
            sadios = sadios,
            vassoura = vassoura,
            podridao = podridao,
            naoAvaliavel = naoAval,
            caixas = caixas,
            statusSync = "PENDENTE"
        )

        if (destinoCaptura == DestinoCaptura.EDICAO_DADOS && indiceEditandoDados in listaRegistros.indices) {
            val orig = listaRegistros[indiceEditandoDados]
            listaRegistros[indiceEditandoDados] = orig.copy(
                diagnostico = diag,
                uri = fotoUri,
                totalFrutos = total,
                sadios = sadios,
                vassoura = vassoura,
                podridao = podridao,
                naoAvaliavel = naoAval,
                caixas = caixas,
                statusSync = "PENDENTE"
            )
        } else {
            listaRegistros.removeAll { it.plantId.equals(pidFinal, ignoreCase = true) }
            listaRegistros.add(novoReg)
        }
        PersistenciaLocalRegistros.salvarRegistros(context, listaRegistros.toList())

        // Atualiza progresso na rota se veio da Rota
        val alvoR = alvoRotaAtual
        if (destinoCaptura == DestinoCaptura.ROTA && alvoR != null) {
            val rIdx = rotaPreCarregada.indexOfFirst {
                it.clone.equals(alvoR.clone, ignoreCase = true) &&
                    it.parcela.equals(alvoR.parcela, ignoreCase = true) &&
                    it.planta == alvoR.planta
            }
            if (rIdx >= 0) {
                val qtdStr = if (ehNaoColetavel) "Não Coletável" else total.toString()
                rotaPreCarregada[rIdx] = rotaPreCarregada[rIdx].copy(coletado = true, qtdFrutos = qtdStr)
                PersistenciaLocalRegistros.salvarRotaPrecarregada(context, rotaPreCarregada.toList(), configEnsaio.codigoEnsaio)
                timestampRota = PersistenciaLocalRegistros.obterTimestampRotaPrecarregada(context)
            }
        }
    }

    fun proximoPlantIdLivre(forcarMesmaPlanta: Boolean = false): String {
        val base = "${cloneLivre.trim()}_P${parcelaLivre.trim().ifBlank { "1" }}_Pl$numPlantaLivre"
        if (!forcarMesmaPlanta && !gerarProximoAnguloLivre) return base
        var idx = 2
        while (listaRegistros.any { it.plantId.equals("${base}_Foto$idx", ignoreCase = true) }) {
            idx++
        }
        return "${base}_Foto$idx"
    }

    fun abrirCameraParaLivre(mesmaPlantaNovoAngulo: Boolean = false) {
        destinoCaptura = DestinoCaptura.LIVRE
        sufixoFotoLivreConfirmado = if (mesmaPlantaNovoAngulo) proximoPlantIdLivre(true) else proximoPlantIdLivre(false)
        plantIdEmCaptura = sufixoFotoLivreConfirmado ?: proximoPlantIdLivre(false)
        uriFotoEmRevisao = null
        caixasIniciaisRevisao = emptyList()
        cameraAberta = true
    }

    fun abrirCameraParaRota(item: RotaColeta) {
        destinoCaptura = DestinoCaptura.ROTA
        alvoRotaAtual = AlvoRotaCaptura(item.clone, item.parcela, item.planta)
        plantIdEmCaptura = "${item.clone}_P${item.parcela}_Pl${item.planta}"
        uriFotoEmRevisao = null
        caixasIniciaisRevisao = emptyList()
        cameraAberta = true
    }

    // Seletor de galeria rápida para Coleta Livre
    val launcherGaleriaLivre = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val copiaLocal = copiarUriGaleriaLocal(context, uri)
            val uriUsada = copiaLocal?.let { Uri.fromFile(it) } ?: uri
            destinoCaptura = DestinoCaptura.LIVRE
            plantIdEmCaptura = proximoPlantIdLivre(gerarProximoAnguloLivre)
            caixasIniciaisRevisao = emptyList()
            uriFotoEmRevisao = uriUsada
        }
    }

    val emTelaCheia = cameraAberta || uriFotoEmRevisao != null || mostrarConfigIA

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
                            selected = abaSelecionada == idx,
                            onClick = { abaSelecionada = idx },
                            icon = { Icon(icone, contentDescription = titulo) },
                            label = { Text(titulo, fontWeight = if (abaSelecionada == idx) FontWeight.Bold else FontWeight.Medium, fontSize = 11.sp) },
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
                uriFotoEmRevisao != null -> {
                    val uriRev = uriFotoEmRevisao!!
                    val temProxima = destinoCaptura == DestinoCaptura.ROTA || destinoCaptura == DestinoCaptura.LIVRE
                    val rotuloProx = if (destinoCaptura == DestinoCaptura.ROTA) "Salvar + Próx. →" else "Salvar + Pl.${numPlantaLivre + 1}"

                    TelaRevisaoCaptura(
                        fotoUri = uriRev,
                        plantId = plantIdEmCaptura,
                        modelConfig = modelConfig,
                        caixasIniciais = caixasIniciaisRevisao,
                        pularInferenciaInicial = (destinoCaptura == DestinoCaptura.EDICAO_DADOS),
                        configRendimento = configRendimento,
                        mostrarBotaoProximaFoto = temProxima,
                        rotuloBotaoProximaFoto = rotuloProx,
                        onRefazer = {
                            uriFotoEmRevisao = null
                            caixasIniciaisRevisao = emptyList()
                            if (destinoCaptura != DestinoCaptura.EDICAO_DADOS) {
                                cameraAberta = true
                            }
                        },
                        onSalvar = { caixas, tot, sad, vas, pod, nav ->
                            if (destinoCaptura != DestinoCaptura.TESTE_IA) {
                                registrarPlantaSalva(plantIdEmCaptura, uriRev, caixas, tot, sad, vas, pod, nav)
                                if (destinoCaptura == DestinoCaptura.LIVRE && configEnsaio.autoAvancarPlanta && !gerarProximoAnguloLivre) {
                                    numPlantaLivre += 1
                                }
                                Toast.makeText(context, "Salvo: $plantIdEmCaptura ($tot frutos)", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Teste concluído ($tot frutos)", Toast.LENGTH_SHORT).show()
                            }
                            uriFotoEmRevisao = null
                            caixasIniciaisRevisao = emptyList()
                            indiceEditandoDados = -1
                            gerarProximoAnguloLivre = false
                        },
                        onSalvarEProxima = { caixas, tot, sad, vas, pod, nav ->
                            val alvoAnterior = alvoRotaAtual
                            registrarPlantaSalva(plantIdEmCaptura, uriRev, caixas, tot, sad, vas, pod, nav)
                            uriFotoEmRevisao = null
                            caixasIniciaisRevisao = emptyList()
                            gerarProximoAnguloLivre = false

                            if (destinoCaptura == DestinoCaptura.ROTA) {
                                val idxAtual = if (alvoAnterior != null) {
                                    rotaPreCarregada.indexOfFirst {
                                        it.clone.equals(alvoAnterior.clone, ignoreCase = true) &&
                                            it.parcela.equals(alvoAnterior.parcela, ignoreCase = true) &&
                                            it.planta == alvoAnterior.planta
                                    }
                                } else -1
                                val proxPendente = (if (idxAtual >= 0) {
                                    rotaPreCarregada.drop(idxAtual + 1).firstOrNull { !it.coletado }
                                } else null) ?: rotaPreCarregada.firstOrNull { !it.coletado }

                                if (proxPendente != null) {
                                    abrirCameraParaRota(proxPendente)
                                } else {
                                    Toast.makeText(context, "Rota concluída! Todas as plantas foram coletadas.", Toast.LENGTH_LONG).show()
                                }
                            } else if (destinoCaptura == DestinoCaptura.LIVRE) {
                                numPlantaLivre += 1
                                abrirCameraParaLivre(false)
                            }
                        }
                    )
                }

                cameraAberta -> {
                    CameraFullscreenCacau(
                        plantId = plantIdEmCaptura.ifBlank { "TESTE_IA" },
                        modelConfig = modelConfig,
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
                        onTrocarModoIA = { novoModo ->
                            atualizarConfigModelo(modelConfig.comModoIA(novoModo))
                        },
                        onAtualizarThreshold = { novoThresh ->
                            val atualizado = if (modelConfig.modoEfetivo == ModoIACacau.SOMENTE_DOENCAS) {
                                modelConfig.copy(thresholdDoenca = novoThresh)
                            } else {
                                modelConfig.copy(threshold = novoThresh)
                            }
                            atualizarConfigModelo(atualizado)
                        },
                        onFotoCapturada = { uri ->
                            cameraAberta = false
                            caixasIniciaisRevisao = emptyList()
                            uriFotoEmRevisao = uri
                        },
                        onAbrirGaleria = if (destinoCaptura == DestinoCaptura.LIVRE) {
                            {
                                cameraAberta = false
                                launcherGaleriaLivre.launch("image/*")
                            }
                        } else null,
                        onFechar = { cameraAberta = false }
                    )
                }

                mostrarConfigIA -> {
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
                                IconButton(onClick = { mostrarConfigIA = false }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                                }
                                Text("Configuração de IA Cacau", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = CacauVerdeEscuro)
                            }
                        }
                        TelaConfiguracaoModelo(
                            configAtual = modelConfig,
                            inferenciaMsAtual = liveInferenciaMs,
                            onAtualizarConfig = { atualizarConfigModelo(it) },
                            onTestarCamera = {
                                mostrarConfigIA = false
                                destinoCaptura = DestinoCaptura.TESTE_IA
                                plantIdEmCaptura = "TESTE_CAMPO"
                                cameraAberta = true
                            }
                        )
                    }
                }

                else -> {
                    when (abaSelecionada) {
                        0 -> TelaInicioCacau(
                            configEnsaio = configEnsaio,
                            registros = listaRegistros,
                            rota = rotaPreCarregada,
                            modelConfig = modelConfig,
                            configRendimento = configRendimento,
                            sincronizandoPc = sincronizandoPc,
                            servidorConfigurado = servidorConfigurado,
                            onAtualizarCodigoEnsaio = { novo ->
                                configEnsaio = configEnsaio.copy(codigoEnsaio = novo.trim().ifBlank { "TC-1801" })
                                PersistenciaLocalRegistros.salvarConfigEnsaio(context, configEnsaio)
                            },
                            onCriarNovoEnsaio = { cod, manter ->
                                val novoCfg = PersistenciaLocalRegistros.criarNovoEnsaioIsolado(context, cod, manter)
                                configEnsaio = novoCfg
                                listaRegistros.clear()
                                listaRegistros.addAll(PersistenciaLocalRegistros.carregarRegistros(context))
                                rotaPreCarregada.clear()
                                rotaPreCarregada.addAll(PersistenciaLocalRegistros.carregarRotaPrecarregada(context).second)
                                timestampRota = PersistenciaLocalRegistros.obterTimestampRotaPrecarregada(context)
                            },
                            onAlternarEnsaio = { id ->
                                val trocado = PersistenciaLocalRegistros.alternarEnsaioAtivo(context, id)
                                if (trocado != null) {
                                    configEnsaio = trocado
                                    listaRegistros.clear()
                                    listaRegistros.addAll(PersistenciaLocalRegistros.carregarRegistros(context))
                                    rotaPreCarregada.clear()
                                    rotaPreCarregada.addAll(PersistenciaLocalRegistros.carregarRotaPrecarregada(context).second)
                                    timestampRota = PersistenciaLocalRegistros.obterTimestampRotaPrecarregada(context)
                                }
                            },
                            onAbrirModoRota = { abaSelecionada = 1 },
                            onAbrirColetaLivre = { abaSelecionada = 2 },
                            onAbrirDadosERanking = { abaSelecionada = 3 },
                            onAbrirConfigIA = { mostrarConfigIA = true },
                            onTestarCameraIA = {
                                destinoCaptura = DestinoCaptura.TESTE_IA
                                plantIdEmCaptura = "TESTE_CAMPO"
                                cameraAberta = true
                            },
                            onSincronizarPc = { sincronizarComPcAgora() },
                            onAbrirConfigServidor = { mostrarConfigServidor = true },
                            onAbrirConfigRendimento = { mostrarConfigRendimento = true }
                        )

                        1 -> TelaModoRotaCacau(
                            configEnsaio = configEnsaio,
                            modelConfig = modelConfig,
                            rota = rotaPreCarregada,
                            onAbrirConfigIA = { mostrarConfigIA = true },
                            onAtualizarRota = { novaRota, novoEnsaio ->
                                rotaPreCarregada.clear()
                                rotaPreCarregada.addAll(novaRota)
                                val cod = novoEnsaio?.takeIf { it.isNotBlank() } ?: configEnsaio.codigoEnsaio
                                if (cod != configEnsaio.codigoEnsaio) {
                                    configEnsaio = configEnsaio.copy(codigoEnsaio = cod)
                                    PersistenciaLocalRegistros.salvarConfigEnsaio(context, configEnsaio)
                                }
                                PersistenciaLocalRegistros.salvarRotaPrecarregada(context, novaRota, cod)
                                timestampRota = PersistenciaLocalRegistros.obterTimestampRotaPrecarregada(context)
                            },
                            onFotografarPlanta = { item -> abrirCameraParaRota(item) },
                            onMarcarNaoColetavel = { item ->
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                destinoCaptura = DestinoCaptura.ROTA
                                alvoRotaAtual = AlvoRotaCaptura(item.clone, item.parcela, item.planta)
                                val pid = "${item.clone}_P${item.parcela}_Pl${item.planta}"
                                registrarPlantaSalva(
                                    pidFinal = pid,
                                    fotoUri = Uri.EMPTY,
                                    caixas = emptyList(),
                                    total = 0,
                                    sadios = 0,
                                    vassoura = 0,
                                    podridao = 0,
                                    naoAval = 0,
                                    diagnosticoCustom = "Não Coletável",
                                    naoColetavelFlag = true,
                                    parcelaRegistro = item.parcela,
                                    numPlantaRegistro = item.planta
                                )
                                Toast.makeText(context, "$pid marcada como Não Coletável.", Toast.LENGTH_SHORT).show()
                            }
                        )

                        2 -> TelaColetaLivreCacau(
                            configEnsaio = configEnsaio,
                            modelConfig = modelConfig,
                            registros = listaRegistros,
                            clone = cloneLivre,
                            parcela = parcelaLivre,
                            numPlanta = numPlantaLivre,
                            onAbrirConfigIA = { mostrarConfigIA = true },
                            onAlterarClone = { novoClone, resetarPlanta ->
                                cloneLivre = novoClone
                                if (resetarPlanta) numPlantaLivre = 1
                            },
                            onAlterarParcela = { novaParcela, resetarPlanta ->
                                parcelaLivre = novaParcela
                                if (resetarPlanta) numPlantaLivre = 1
                            },
                            onAlterarNumPlanta = { numPlantaLivre = it.coerceAtLeast(1) },
                            onAbrirCamera = { mesmoAngulo ->
                                gerarProximoAnguloLivre = mesmoAngulo
                                abrirCameraParaLivre(mesmoAngulo)
                            },
                            onImportarGaleria = { mesmoAngulo ->
                                gerarProximoAnguloLivre = mesmoAngulo
                                launcherGaleriaLivre.launch("image/*")
                            },
                            onMarcarNaoColetavel = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                val pid = "${cloneLivre.trim()}_P${parcelaLivre.trim().ifBlank { "1" }}_Pl$numPlantaLivre"
                                destinoCaptura = DestinoCaptura.LIVRE
                                registrarPlantaSalva(
                                    pidFinal = pid,
                                    fotoUri = Uri.EMPTY,
                                    caixas = emptyList(),
                                    total = 0,
                                    sadios = 0,
                                    vassoura = 0,
                                    podridao = 0,
                                    naoAval = 0,
                                    diagnosticoCustom = "Não Coletável",
                                    naoColetavelFlag = true
                                )
                                Toast.makeText(context, "$pid salva como Não Coletável.", Toast.LENGTH_SHORT).show()
                                numPlantaLivre += 1
                            }
                        )

                        3 -> TelaDadosERanking(
                            configEnsaio = configEnsaio,
                            registros = listaRegistros,
                            configRendimento = configRendimento,
                            sincronizandoPc = sincronizandoPc,
                            onSincronizarPc = { sincronizarComPcAgora() },
                            onAbrirConfigServidor = { mostrarConfigServidor = true },
                            onAbrirConfigRendimento = { mostrarConfigRendimento = true },
                            onEditarRegistro = { origIdx, atualizado ->
                                if (origIdx in listaRegistros.indices) {
                                    listaRegistros[origIdx] = atualizado
                                    PersistenciaLocalRegistros.salvarRegistros(context, listaRegistros.toList())
                                }
                            },
                            onRevisarFotoRegistro = { origIdx, reg ->
                                if (reg.uri != Uri.EMPTY) {
                                    indiceEditandoDados = origIdx
                                    destinoCaptura = DestinoCaptura.EDICAO_DADOS
                                    plantIdEmCaptura = reg.plantId
                                    caixasIniciaisRevisao = reg.caixas
                                    uriFotoEmRevisao = reg.uri
                                }
                            },
                            onExcluirRegistro = { origIdx ->
                                if (origIdx in listaRegistros.indices) {
                                    listaRegistros.removeAt(origIdx)
                                    PersistenciaLocalRegistros.salvarRegistros(context, listaRegistros.toList())
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    if (mostrarConfigRendimento) {
        DialogConfiguracaoRendimentoCacau(
            configAtual = configRendimento,
            onDismiss = { mostrarConfigRendimento = false },
            onSalvar = { novo ->
                configRendimento = novo
                PersistenciaLocalRegistros.salvarConfigRendimento(context, novo)
                mostrarConfigRendimento = false
            }
        )
    }

    if (mostrarConfigServidor) {
        DialogConfiguracaoServidor(
            onDismiss = { mostrarConfigServidor = false },
            onSalvar = { url, email, pass, ensaio ->
                prefsSync.edit()
                    .putString("server_url", url.trim())
                    .putString("user_email", email.trim())
                    .putString("user_pass", pass)
                    .putString("codigo_ensaio", ensaio.trim().ifBlank { "TC-1801" })
                    .apply()
                configEnsaio = configEnsaio.copy(codigoEnsaio = ensaio.trim().ifBlank { "TC-1801" })
                PersistenciaLocalRegistros.salvarConfigEnsaio(context, configEnsaio)
                servidorConfigurado = url.isNotBlank()
                mostrarConfigServidor = false
                Toast.makeText(context, "Servidor CacauSync salvo!", Toast.LENGTH_SHORT).show()
            }
        )
    }
}

private data class PendenciaResetPlanta(
    val mensagem: String,
    val onResetarPlanta1: () -> Unit,
    val onManterPlanta: () -> Unit
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TelaColetaLivreCacau(
    configEnsaio: PersistenciaLocalRegistros.ConfigEnsaioLocal,
    modelConfig: ModelConfig,
    registros: List<RegistroCacau>,
    clone: String,
    parcela: String,
    numPlanta: Int,
    onAbrirConfigIA: () -> Unit,
    onAlterarClone: (String, resetarPlanta: Boolean) -> Unit,
    onAlterarParcela: (String, resetarPlanta: Boolean) -> Unit,
    onAlterarNumPlanta: (Int) -> Unit,
    onAbrirCamera: (mesmaPlantaNovoAngulo: Boolean) -> Unit,
    onImportarGaleria: (mesmaPlantaNovoAngulo: Boolean) -> Unit,
    onMarcarNaoColetavel: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    var dropdownCloneAberto by remember { mutableStateOf(false) }
    var filtroClone by remember { mutableStateOf(clone) }
    var dialogDigitarParcela by remember { mutableStateOf(false) }
    var dialogDigitarPlanta by remember { mutableStateOf(false) }
    var pendenciaReset by remember { mutableStateOf<PendenciaResetPlanta?>(null) }

    LaunchedEffect(clone) {
        filtroClone = clone
    }

    val prefixoPlanta = "${clone.trim()}_P${parcela.trim().ifBlank { "1" }}_Pl$numPlanta"
    val registrosMesmaPlanta = remember(registros.toList(), prefixoPlanta) {
        registros.filter {
            it.plantId.equals(prefixoPlanta, ignoreCase = true) ||
                it.plantId.startsWith("${prefixoPlanta}_Foto", ignoreCase = true)
        }
    }

    val clonesFiltrados = remember(filtroClone) {
        val q = filtroClone.trim().lowercase(Locale.ROOT)
        if (q.isEmpty()) CLONES_CEPEC_PADRAO else CLONES_CEPEC_PADRAO.filter { it.lowercase(Locale.ROOT).contains(q) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CacauFundoSolar)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Coleta Livre • ${configEnsaio.codigoEnsaio}",
            fontSize = 20.sp,
            fontWeight = FontWeight.ExtraBold,
            color = CacauVerdeEscuro
        )

        BarraStatusModeloCacau(modelConfig = modelConfig, onClickConfig = onAbrirConfigIA)

        // Card Hierarquia Clone -> Parcela -> Planta
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, CacauBordaSuave),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ExposedDropdownMenuBox(
                    expanded = dropdownCloneAberto,
                    onExpandedChange = { dropdownCloneAberto = !dropdownCloneAberto }
                ) {
                    OutlinedTextField(
                        value = filtroClone,
                        onValueChange = {
                            filtroClone = it
                            dropdownCloneAberto = true
                        },
                        label = { Text("${configEnsaio.labelClone} (digite ou escolha)") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownCloneAberto) },
                        singleLine = true,
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryEditable)
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = dropdownCloneAberto,
                        onDismissRequest = { dropdownCloneAberto = false }
                    ) {
                        if (filtroClone.isNotBlank() && clonesFiltrados.none { it.equals(filtroClone.trim(), ignoreCase = true) }) {
                            val digitado = filtroClone.trim().uppercase()
                            DropdownMenuItem(
                                text = { Text("Usar clone \"$digitado\"", fontWeight = FontWeight.Bold, color = CacauVerdeEscuro) },
                                onClick = {
                                    dropdownCloneAberto = false
                                    if (numPlanta > 1 && digitado != clone) {
                                        pendenciaReset = PendenciaResetPlanta(
                                            mensagem = "Trocar clone para $digitado e reiniciar Planta para 1?",
                                            onResetarPlanta1 = { onAlterarClone(digitado, true) },
                                            onManterPlanta = { onAlterarClone(digitado, false) }
                                        )
                                    } else {
                                        onAlterarClone(digitado, false)
                                    }
                                }
                            )
                        }
                        clonesFiltrados.take(25).forEach { op ->
                            DropdownMenuItem(
                                text = { Text(op) },
                                onClick = {
                                    dropdownCloneAberto = false
                                    filtroClone = op
                                    if (numPlanta > 1 && op != clone) {
                                        pendenciaReset = PendenciaResetPlanta(
                                            mensagem = "Trocar clone para $op e reiniciar Planta para 1?",
                                            onResetarPlanta1 = { onAlterarClone(op, true) },
                                            onManterPlanta = { onAlterarClone(op, false) }
                                        )
                                    } else {
                                        onAlterarClone(op, false)
                                    }
                                }
                            )
                        }
                    }
                }

                // Controles ergonômicos - / + para PARCELA e PLANTA
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CardControleNumerico(
                        titulo = configEnsaio.labelParcela.uppercase(),
                        valorTexto = parcela,
                        onDiminuir = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            val atual = parcela.toIntOrNull() ?: 1
                            val nova = (atual - 1).coerceAtLeast(1).toString()
                            if (numPlanta > 1 && nova != parcela) {
                                pendenciaReset = PendenciaResetPlanta(
                                    mensagem = "Mudou para Parcela $nova. Reiniciar Planta para 1?",
                                    onResetarPlanta1 = { onAlterarParcela(nova, true) },
                                    onManterPlanta = { onAlterarParcela(nova, false) }
                                )
                            } else {
                                onAlterarParcela(nova, false)
                            }
                        },
                        onAumentar = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            val atual = parcela.toIntOrNull() ?: 1
                            val nova = (atual + 1).toString()
                            if (numPlanta > 1 && nova != parcela) {
                                pendenciaReset = PendenciaResetPlanta(
                                    mensagem = "Mudou para Parcela $nova. Reiniciar Planta para 1?",
                                    onResetarPlanta1 = { onAlterarParcela(nova, true) },
                                    onManterPlanta = { onAlterarParcela(nova, false) }
                                )
                            } else {
                                onAlterarParcela(nova, false)
                            }
                        },
                        onClicarNumero = { dialogDigitarParcela = true },
                        modifier = Modifier.weight(1f)
                    )

                    CardControleNumerico(
                        titulo = configEnsaio.labelPlanta.uppercase(),
                        valorTexto = numPlanta.toString(),
                        onDiminuir = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onAlterarNumPlanta((numPlanta - 1).coerceAtLeast(1))
                        },
                        onAumentar = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onAlterarNumPlanta(numPlanta + 1)
                        },
                        onClicarNumero = { dialogDigitarPlanta = true },
                        modifier = Modifier.weight(1f)
                    )
                }

                Surface(color = CacauVerdeClaro, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "ID de campo: $prefixoPlanta",
                        color = CacauVerdeEscuro,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }
        }

        // Banner informativo se planta já possui fotos registradas na campanha
        if (registrosMesmaPlanta.isNotEmpty()) {
            val somaFrutos = registrosMesmaPlanta.sumOf { it.totalFrutos }
            Surface(
                color = CacauInfoClaro,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, CacauInfo.copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = CacauInfo, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Planta já possui ${registrosMesmaPlanta.size} foto(s) ($somaFrutos frutos).",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = CacauInfo
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = { onAbrirCamera(true) },
                            colors = ButtonDefaults.buttonColors(containerColor = CacauInfo),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("+ Ângulo 360°", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = { onAbrirCamera(false) },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Substituir Foto", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Ação principal de captura
        Button(
            onClick = { onAbrirCamera(false) },
            colors = ButtonDefaults.buttonColors(containerColor = CacauVerdeEscuro),
            shape = RoundedCornerShape(16.dp),
            contentPadding = PaddingValues(vertical = 18.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(10.dp))
            Text("ABRIR CÂMERA IA • $prefixoPlanta", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = { onImportarGaleria(false) },
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(vertical = 12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Galeria", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }

            OutlinedButton(
                onClick = onMarcarNaoColetavel,
                border = BorderStroke(1.2.dp, CacauDourado),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF8D5500)),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(vertical = 12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Block, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Não Coletável", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
    }

    if (dialogDigitarParcela) {
        DialogValorNumerico("Número / Código da Parcela", parcela, isNumerico = false, onDismiss = { dialogDigitarParcela = false }) { nova ->
            dialogDigitarParcela = false
            val norm = nova.trim().ifBlank { "1" }
            if (numPlanta > 1 && norm != parcela) {
                pendenciaReset = PendenciaResetPlanta(
                    mensagem = "Mudou para Parcela $norm. Reiniciar Planta para 1?",
                    onResetarPlanta1 = { onAlterarParcela(norm, true) },
                    onManterPlanta = { onAlterarParcela(norm, false) }
                )
            } else {
                onAlterarParcela(norm, false)
            }
        }
    }

    if (dialogDigitarPlanta) {
        DialogValorNumerico("Número da Planta", numPlanta.toString(), isNumerico = true, onDismiss = { dialogDigitarPlanta = false }) { novo ->
            dialogDigitarPlanta = false
            onAlterarNumPlanta(novo.toIntOrNull()?.coerceAtLeast(1) ?: numPlanta)
        }
    }

    val pend = pendenciaReset
    if (pend != null) {
        AlertDialog(
            onDismissRequest = { pendenciaReset = null },
            title = { Text("Reiniciar Planta para 1?", fontWeight = FontWeight.Bold, color = CacauVerdeEscuro) },
            text = { Text(pend.mensagem) },
            confirmButton = {
                Button(
                    onClick = {
                        pend.onResetarPlanta1()
                        pendenciaReset = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CacauVerdeEscuro)
                ) { Text("Sim, ir p/ Pl 1") }
            },
            dismissButton = {
                TextButton(onClick = {
                    pend.onManterPlanta()
                    pendenciaReset = null
                }) { Text("Manter Planta $numPlanta") }
            }
        )
    }
}

@Composable
private fun CardControleNumerico(
    titulo: String,
    valorTexto: String,
    onDiminuir: () -> Unit,
    onAumentar: () -> Unit,
    onClicarNumero: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color(0xFFF6F9F6),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, CacauBordaSuave),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(titulo, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = CacauTextoSecundario)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onDiminuir,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(Icons.Default.Remove, contentDescription = "Diminuir $titulo")
                }

                Text(
                    text = valorTexto,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = CacauVerdeEscuro,
                    modifier = Modifier
                        .clickable { onClicarNumero() }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )

                Button(
                    onClick = onAumentar,
                    colors = ButtonDefaults.buttonColors(containerColor = CacauVerdeEscuro),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Aumentar $titulo")
                }
            }
            Text("Toque no nº para digitar", fontSize = 10.sp, color = CacauTextoSecundario)
        }
    }
}

@Composable
private fun DialogValorNumerico(
    titulo: String,
    valorAtual: String,
    isNumerico: Boolean,
    onDismiss: () -> Unit,
    onConfirmar: (String) -> Unit
) {
    var texto by remember { mutableStateOf(valorAtual) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(titulo, fontWeight = FontWeight.Bold, color = CacauVerdeEscuro) },
        text = {
            OutlinedTextField(
                value = texto,
                onValueChange = { texto = if (isNumerico) it.filter { ch -> ch.isDigit() } else it },
                keyboardOptions = KeyboardOptions(keyboardType = if (isNumerico) KeyboardType.Number else KeyboardType.Text),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = { onConfirmar(texto) },
                colors = ButtonDefaults.buttonColors(containerColor = CacauVerdeEscuro)
            ) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

@Composable
private fun TelaModoRotaCacau(
    configEnsaio: PersistenciaLocalRegistros.ConfigEnsaioLocal,
    modelConfig: ModelConfig,
    rota: List<RotaColeta>,
    onAbrirConfigIA: () -> Unit,
    onAtualizarRota: (List<RotaColeta>, codigoEnsaioPlanilha: String?) -> Unit,
    onFotografarPlanta: (RotaColeta) -> Unit,
    onMarcarNaoColetavel: (RotaColeta) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var filtroAba by remember { mutableIntStateOf(0) } // 0=Pendentes, 1=Coletadas, 2=Todas
    var busca by remember { mutableStateOf("") }
    var puxandoPc by remember { mutableStateOf(false) }

    // Estado de importação offline Excel (.xlsx) / CSV
    var planilhaPendenteMapeamento by remember { mutableStateOf<Pair<ImportadorPlanilhaUniversal.PlanilhaLida, ImportadorPlanilhaUniversal.MapeamentoSugerido>?>(null) }

    val launcherPlanilha = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val res = withContext(Dispatchers.IO) {
                ImportadorPlanilhaUniversal.lerPlanilha(context, uri)
            }
            if (res.linhas.isEmpty()) {
                Toast.makeText(context, "Planilha vazia ou ilegível.", Toast.LENGTH_LONG).show()
                return@launch
            }
            val map = ImportadorPlanilhaUniversal.sugerirMapeamento(res.cabecalho)
            if (map.completo) {
                val itens = ImportadorPlanilhaUniversal.extrairRotaCacau(
                    res, map.idxClone, map.idxParcela, map.idxPlanta, map.idxOrdem
                )
                if (itens.isNotEmpty()) {
                    onAtualizarRota(itens, null)
                    Toast.makeText(context, "${itens.size} plantas importadas (${res.formato})!", Toast.LENGTH_LONG).show()
                } else {
                    planilhaPendenteMapeamento = res to map
                }
            } else {
                planilhaPendenteMapeamento = res to map
            }
        }
    }

    val pendentesCount = rota.count { !it.coletado }
    val coletadasCount = rota.count { it.coletado }
    val rotaFiltrada = remember(rota.toList(), filtroAba, busca) {
        val q = busca.trim().lowercase(Locale.ROOT)
        rota.filter { item ->
            val okFiltro = when (filtroAba) {
                0 -> !item.coletado
                1 -> item.coletado
                else -> true
            }
            val okBusca = q.isEmpty() ||
                item.clone.lowercase(Locale.ROOT).contains(q) ||
                item.parcela.lowercase(Locale.ROOT).contains(q) ||
                "pl${item.planta}".contains(q) ||
                item.planta.toString() == q
            okFiltro && okBusca
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CacauFundoSolar)
    ) {
        Surface(color = Color.White, shadowElevation = 2.dp) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Modo Rota • ${configEnsaio.codigoEnsaio}", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = CacauVerdeEscuro)
                        Text("$coletadasCount/${rota.size} coletadas • $pendentesCount pendentes", fontSize = 12.sp, color = CacauTextoSecundario)
                    }
                    IconButton(onClick = {
                        val (_, rec) = PersistenciaLocalRegistros.carregarRotaPrecarregada(context)
                        if (rec.isNotEmpty()) {
                            onAtualizarRota(rec, null)
                            Toast.makeText(context, "Rota recarregada (${rec.size})", Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Recarregar rota USB", tint = CacauVerdeEscuro)
                    }
                }

                BarraStatusModeloCacau(modelConfig = modelConfig, onClickConfig = onAbrirConfigIA)

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = {
                            launcherPlanilha.launch(
                                arrayOf(
                                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                    "text/csv",
                                    "text/comma-separated-values",
                                    "text/plain",
                                    "*/*"
                                )
                            )
                        },
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("Importar .xlsx/.csv", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            if (puxandoPc) return@Button
                            puxandoPc = true
                            scope.launch {
                                val res = withContext(Dispatchers.IO) {
                                    CacauSyncClient.puxarRotaAutomatica(context, configEnsaio.codigoEnsaio)
                                }
                                puxandoPc = false
                                res.onSuccess { nova ->
                                    onAtualizarRota(nova, configEnsaio.codigoEnsaio)
                                    Toast.makeText(context, "Rota do PC atualizada (${nova.size} plantas)!", Toast.LENGTH_SHORT).show()
                                }.onFailure { err ->
                                    Toast.makeText(context, "Falha ao puxar rota do PC: ${err.message}", Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        enabled = !puxandoPc,
                        colors = ButtonDefaults.buttonColors(containerColor = CacauVerdeEscuro),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        if (puxandoPc) {
                            CircularProgressIndicator(modifier = Modifier.size(15.dp), color = Color.White, strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(5.dp))
                            Text("Puxar Rota do PC", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                OutlinedTextField(
                    value = busca,
                    onValueChange = { busca = it },
                    placeholder = { Text("Buscar clone, parcela ou nº planta...", fontSize = 12.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = filtroAba == 0, onClick = { filtroAba = 0 }, label = { Text("Pendentes ($pendentesCount)", fontSize = 11.sp) })
                    FilterChip(selected = filtroAba == 1, onClick = { filtroAba = 1 }, label = { Text("Coletadas ($coletadasCount)", fontSize = 11.sp) })
                    FilterChip(selected = filtroAba == 2, onClick = { filtroAba = 2 }, label = { Text("Todas (${rota.size})", fontSize = 11.sp) })
                }
            }
        }

        if (rotaFiltrada.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(
                    text = if (rota.isEmpty()) {
                        "Nenhuma rota carregada. Envie pelo CacauSync USB, puxe do PC ou importe uma planilha .xlsx / .csv offline."
                    } else {
                        "Nenhuma planta nesta aba/filtro."
                    },
                    color = CacauTextoSecundario,
                    fontSize = 13.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(rotaFiltrada, key = { "${it.ordem}_${it.clone}_${it.parcela}_${it.planta}" }) { item ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = if (item.coletado) Color(0xFFF3F8F4) else Color.White),
                        border = BorderStroke(1.dp, if (item.coletado) CacauVerdeBotanico.copy(alpha = 0.4f) else CacauBordaSuave),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("#${item.ordem}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = CacauTextoSecundario)
                                    Text(item.clone, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = CacauTextoPrincipal)
                                    if (item.coletado) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = "Coletada", tint = CacauVerdeBotanico, modifier = Modifier.size(16.dp))
                                    }
                                }
                                Text(
                                    text = "Parcela ${item.parcela} • Planta ${item.planta}" +
                                        if (item.coletado) " • ${item.qtdFrutos} fruto(s)" else "",
                                    fontSize = 12.sp,
                                    color = CacauTextoSecundario
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                OutlinedButton(
                                    onClick = { onMarcarNaoColetavel(item) },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    modifier = Modifier.height(36.dp)
                                ) {
                                    Icon(Icons.Default.Block, contentDescription = "Não Coletável", tint = CacauDourado, modifier = Modifier.size(16.dp))
                                }
                                Button(
                                    onClick = { onFotografarPlanta(item) },
                                    colors = ButtonDefaults.buttonColors(containerColor = CacauVerdeEscuro),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.height(36.dp)
                                ) {
                                    Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(if (item.coletado) "Refazer" else "Foto", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    val pendMap = planilhaPendenteMapeamento
    if (pendMap != null) {
        DialogMapeamentoPlanilha(
            planilha = pendMap.first,
            sugestao = pendMap.second,
            onDismiss = { planilhaPendenteMapeamento = null },
            onConfirmar = { iClone, iParcela, iPlanta, iOrdem ->
                val itens = ImportadorPlanilhaUniversal.extrairRotaCacau(pendMap.first, iClone, iParcela, iPlanta, iOrdem)
                planilhaPendenteMapeamento = null
                if (itens.isNotEmpty()) {
                    onAtualizarRota(itens, null)
                    Toast.makeText(context, "${itens.size} plantas importadas!", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(context, "Nenhuma linha válida encontrada com as colunas escolhidas.", Toast.LENGTH_LONG).show()
                }
            }
        )
    }
}

@Composable
private fun DialogMapeamentoPlanilha(
    planilha: ImportadorPlanilhaUniversal.PlanilhaLida,
    sugestao: ImportadorPlanilhaUniversal.MapeamentoSugerido,
    onDismiss: () -> Unit,
    onConfirmar: (idxClone: Int, idxParcela: Int, idxPlanta: Int, idxOrdem: Int) -> Unit
) {
    val cols = planilha.cabecalho
    var idxClone by remember { mutableIntStateOf(if (sugestao.idxClone in cols.indices) sugestao.idxClone else 0) }
    var idxParcela by remember { mutableIntStateOf(if (sugestao.idxParcela in cols.indices) sugestao.idxParcela else minOf(1, cols.lastIndex.coerceAtLeast(0))) }
    var idxPlanta by remember { mutableIntStateOf(if (sugestao.idxPlanta in cols.indices) sugestao.idxPlanta else minOf(2, cols.lastIndex.coerceAtLeast(0))) }
    val idxOrdem = sugestao.idxOrdem

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Mapear Colunas (${planilha.formato})", fontWeight = FontWeight.Bold, color = CacauVerdeEscuro) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${planilha.linhas.size} linhas detectadas. Confirme qual coluna representa cada campo:", fontSize = 12.sp)
                SeletorColunaIndice("Coluna do Clone", cols, idxClone) { idxClone = it }
                SeletorColunaIndice("Coluna da Parcela", cols, idxParcela) { idxParcela = it }
                SeletorColunaIndice("Coluna da Planta", cols, idxPlanta) { idxPlanta = it }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirmar(idxClone, idxParcela, idxPlanta, idxOrdem) },
                colors = ButtonDefaults.buttonColors(containerColor = CacauVerdeEscuro)
            ) { Text("Importar Rota") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SeletorColunaIndice(
    rotulo: String,
    colunas: List<String>,
    selecionado: Int,
    onSelecionar: (Int) -> Unit
) {
    var aberto by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = aberto, onExpandedChange = { aberto = !aberto }) {
        OutlinedTextField(
            value = colunas.getOrNull(selecionado) ?: "Coluna ${selecionado + 1}",
            onValueChange = {},
            readOnly = true,
            label = { Text(rotulo, fontSize = 11.sp) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = aberto) },
            singleLine = true,
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = aberto, onDismissRequest = { aberto = false }) {
            colunas.forEachIndexed { idx, nome ->
                DropdownMenuItem(
                    text = { Text("${idx + 1}. $nome") },
                    onClick = {
                        onSelecionar(idx)
                        aberto = false
                    }
                )
            }
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
        title = { Text("Servidor CacauSync (Wi-Fi / USB)", fontWeight = FontWeight.Bold, color = CacauVerdeEscuro) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "Se estiver conectado no cabo USB com o CacauSync aberto, o app descobre o PC automaticamente. Para Wi-Fi, informe o IP do computador:",
                    fontSize = 12.sp,
                    color = CacauTextoSecundario
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("URL do Servidor CacauSync") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = ensaio,
                    onValueChange = { ensaio = it.uppercase() },
                    label = { Text("Código do Ensaio") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("E-mail CEPEC") },
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

                OutlinedButton(
                    onClick = {
                        testando = true
                        resultadoTeste = null
                        coroutineScope.launch {
                            val descoberta = withContext(Dispatchers.IO) {
                                if (CacauSyncClient.testarServidorAtivo(url)) {
                                    url
                                } else {
                                    CacauSyncClient.descobrirServidorAutomaticamente(url)
                                }
                            }
                            testando = false
                            if (descoberta != null) {
                                url = descoberta
                                resultadoTeste = "Conectado com sucesso em $descoberta"
                            } else {
                                resultadoTeste = "Servidor não respondeu em $url nem na rede local."
                            }
                        }
                    },
                    enabled = !testando,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (testando) "Testando..." else "Descobrir / Testar Conexão")
                }

                resultadoTeste?.let { msg ->
                    Text(
                        text = msg,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (msg.startsWith("Conectado")) CacauVerdeBotanico else CacauPerigo
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSalvar(url, email, pass, ensaio) },
                colors = ButtonDefaults.buttonColors(containerColor = CacauVerdeEscuro)
            ) { Text("Salvar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
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
    val resultadoCsv = if (CacauSyncClient.isNaoColetavel(reg.diagnostico)) {
        "Não Coletável"
    } else {
        reg.totalFrutos.toString()
    }
    return listOf(
        reg.ensaioManual, reg.plantId, itemPrincipal, reg.parcelaManual,
        reg.numPlanta, resultadoCsv, reg.data, reg.lat, reg.lng,
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
        val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        val payload = bom + conteudo.toByteArray(StandardCharsets.UTF_8)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, nomeFinal)
                put(MediaStore.MediaColumns.MIME_TYPE, "text/csv")
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/CEPEC")
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            if (uri != null) {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(payload)
                }
                Toast.makeText(context, "Planilha exportada em Downloads/CEPEC!", Toast.LENGTH_SHORT).show()
                return
            }
        }
        val downloadDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "CEPEC")
        if (!downloadDir.exists()) downloadDir.mkdirs()
        val file = File(downloadDir, nomeFinal)
        FileOutputStream(file).use { out ->
            out.write(payload)
        }
        Toast.makeText(context, "Planilha exportada em Downloads/CEPEC!", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(context, "Erro ao exportar CSV: ${e.message}", Toast.LENGTH_LONG).show()
    }
}
