package com.example.myapplication.userinterface

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.data.LABELS_DOENCA_CACAU
import com.example.myapplication.data.MODELOS_EMBUTIDOS
import com.example.myapplication.data.ModelConfig
import com.example.myapplication.data.ModoIACacau
import com.example.myapplication.ui.theme.CacauBordaSuave
import com.example.myapplication.ui.theme.CacauFundoSolar
import com.example.myapplication.ui.theme.CacauInfo
import com.example.myapplication.ui.theme.CacauInfoClaro
import com.example.myapplication.ui.theme.CacauPerigo
import com.example.myapplication.ui.theme.CacauTextoPrincipal
import com.example.myapplication.ui.theme.CacauTextoSecundario
import com.example.myapplication.ui.theme.CacauVerdeBotanico
import com.example.myapplication.ui.theme.CacauVerdeClaro
import com.example.myapplication.ui.theme.CacauVerdeEscuro
import com.example.myapplication.userinterface.util.ImportadorPlanilhaUniversal
import com.example.myapplication.userinterface.util.ObjectDetectorHelper
import com.example.myapplication.userinterface.util.PersistenciaLocalRegistros
import java.io.File
import java.util.Locale
import java.util.zip.ZipInputStream

@Composable
fun TelaConfiguracaoModelo(
    configAtual: ModelConfig,
    inferenciaMsAtual: Long = 0L,
    onAtualizarConfig: (ModelConfig) -> Unit,
    onTestarCamera: () -> Unit = {}
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    var mostrarDiagnostico by remember { mutableStateOf(false) }
    var mostrarAvancado by remember { mutableStateOf(configAtual.usaModeloCustomizado) }
    var feedbackImportacao by remember { mutableStateOf<String?>(null) }
    var modelosImportados by remember { mutableStateOf(PersistenciaLocalRegistros.listarModelosImportados(context)) }

    val launcherModelo = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val (novoCfg, msg) = importarModeloCustomizadoCacau(context, uri, configAtual)
        feedbackImportacao = msg
        modelosImportados = PersistenciaLocalRegistros.listarModelosImportados(context)
        if (novoCfg != null) {
            onAtualizarConfig(novoCfg)
            Toast.makeText(context, "Modelo importado e ativado!", Toast.LENGTH_SHORT).show()
        }
    }

    val modoEfetivo = configAtual.modoEfetivo

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CacauFundoSolar)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Cabeçalho com botão discreto de Diagnóstico & Sobre
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Inteligência Artificial • Cacau",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = CacauVerdeEscuro
                )
                Text(
                    text = "Motores exclusivos CEPEC: detector.tflite + detector_doenca.tflite",
                    fontSize = 12.sp,
                    color = CacauTextoSecundario
                )
            }
            IconButton(onClick = { mostrarDiagnostico = true }) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = "Diagnóstico técnico e sobre",
                    tint = CacauVerdeEscuro
                )
            }
        }

        // Botão de Teste ao Vivo na Câmera Fullscreen
        Button(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onTestarCamera()
            },
            colors = ButtonDefaults.buttonColors(containerColor = CacauVerdeEscuro),
            shape = RoundedCornerShape(14.dp),
            contentPadding = PaddingValues(vertical = 13.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("TESTAR NA CÂMERA AO VIVO", fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }

        if (configAtual.usaModeloCustomizado) {
            Surface(
                color = CacauInfoClaro,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, CacauInfo.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Modelo customizado ativo:", fontSize = 11.sp, color = CacauInfo, fontWeight = FontWeight.Bold)
                        Text(configAtual.nomeExibicao, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = CacauTextoPrincipal)
                    }
                    OutlinedButton(
                        onClick = {
                            onAtualizarConfig(configAtual.comModoIA(ModoIACacau.COMBINADO_2_IAS))
                        },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Usar 2 IAs CEPEC", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Estratégias com os 2 modelos oficiais de cacau
        Text(
            text = "Modo de Operação dos 2 Modelos CEPEC",
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = CacauTextoPrincipal
        )

        ModoIACacau.entries.forEach { modo ->
            val selecionado = !configAtual.usaModeloCustomizado && modoEfetivo == modo
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (selecionado) CacauVerdeClaro else Color.White
                ),
                border = BorderStroke(
                    width = if (selecionado) 2.dp else 1.dp,
                    color = if (selecionado) CacauVerdeEscuro else CacauBordaSuave
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onAtualizarConfig(configAtual.comModoIA(modo))
                    }
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = if (selecionado) Icons.Default.CheckCircle else Icons.Default.Psychology,
                        contentDescription = null,
                        tint = if (selecionado) CacauVerdeEscuro else CacauTextoSecundario,
                        modifier = Modifier.size(24.dp)
                    )
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = modo.nomeExibicao,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = if (selecionado) CacauVerdeEscuro else CacauTextoPrincipal
                            )
                            if (modo == ModoIACacau.COMBINADO_2_IAS) {
                                Surface(color = CacauVerdeEscuro, shape = RoundedCornerShape(6.dp)) {
                                    Text(
                                        "RECOMENDADO",
                                        color = Color.White,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            text = modo.descricao,
                            fontSize = 12.sp,
                            color = CacauTextoSecundario,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }

        // Calibração independente dos 2 limiares de confiança + máx detecções
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, CacauBordaSuave),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Calibração Fina em Campo", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = CacauTextoPrincipal)

                Column {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Confiança Contagem (detector.tflite)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Text("${(configAtual.threshold * 100).toInt()}%", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CacauVerdeEscuro)
                    }
                    Slider(
                        value = configAtual.threshold.coerceIn(0.15f, 0.75f),
                        onValueChange = { onAtualizarConfig(configAtual.copy(threshold = it)) },
                        valueRange = 0.15f..0.75f,
                        colors = SliderDefaults.colors(thumbColor = CacauVerdeEscuro, activeTrackColor = CacauVerdeBotanico)
                    )
                    Text("Valores menores detectam mais frutos em sombra densa.", fontSize = 11.sp, color = CacauTextoSecundario)
                }

                Column {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Sensibilidade Doenças (detector_doenca.tflite)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Text("${(configAtual.thresholdDoenca * 100).toInt()}%", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CacauVerdeEscuro)
                    }
                    Slider(
                        value = configAtual.thresholdDoenca.coerceIn(0.15f, 0.70f),
                        onValueChange = { onAtualizarConfig(configAtual.copy(thresholdDoenca = it)) },
                        valueRange = 0.15f..0.70f,
                        enabled = configAtual.usaModeloCustomizado || modoEfetivo != ModoIACacau.SOMENTE_CONTAGEM,
                        colors = SliderDefaults.colors(thumbColor = CacauVerdeEscuro, activeTrackColor = CacauVerdeBotanico)
                    )
                    Text("Controla sensibilidade para Podridão Parda, Monilíase e Vassoura.", fontSize = 11.sp, color = CacauTextoSecundario)
                }

                Column {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Máximo de frutos por foto", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Text("${configAtual.maxResultados}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CacauVerdeEscuro)
                    }
                    Slider(
                        value = configAtual.maxResultados.toFloat().coerceIn(10f, 60f),
                        onValueChange = { onAtualizarConfig(configAtual.copy(maxResultados = it.toInt().coerceIn(10, 60))) },
                        valueRange = 10f..60f,
                        steps = 9,
                        colors = SliderDefaults.colors(thumbColor = CacauVerdeEscuro, activeTrackColor = CacauVerdeBotanico)
                    )
                }
            }
        }

        // Seção colapsável para importação de modelos CEPEC experimentais (.tflite / .zip)
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, CacauBordaSuave),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { mostrarAvancado = !mostrarAvancado },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = CacauVerdeEscuro, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Avançado: Modelo CEPEC Externo (.tflite / .zip)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                    Icon(if (mostrarAvancado) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null)
                }

                AnimatedVisibility(visible = mostrarAvancado) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                        Text(
                            "Importe modelos YOLO treinados exclusivamente no acervo CEPEC de cacau (.tflite ou pacote .zip Ultralytics).",
                            fontSize = 11.sp,
                            color = CacauTextoSecundario
                        )
                        OutlinedButton(
                            onClick = { launcherModelo.launch(arrayOf("*/*")) },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Importar Arquivo .tflite ou .zip", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        feedbackImportacao?.let { msg ->
                            Text(msg, fontSize = 11.sp, color = CacauInfo)
                        }

                        modelosImportados.forEach { m ->
                            val ativo = configAtual.modeloInternoPath == m.path
                            Surface(
                                color = if (ativo) CacauVerdeClaro else Color(0xFFF6F8F6),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, if (ativo) CacauVerdeBotanico else CacauBordaSuave),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            val report = ObjectDetectorHelper.inspecionarModeloArquivo(File(m.path))
                                            onAtualizarConfig(
                                                configAtual.copy(
                                                    modeloSelecionado = null,
                                                    modeloInternoPath = m.path,
                                                    modeloExternoUri = null,
                                                    modeloExternoNome = m.nomeExibicao,
                                                    labelsFiltro = report.labelsExtraidas.ifEmpty { LABELS_DOENCA_CACAU },
                                                    usarDiagnosticoSecundario = false,
                                                    modoIA = ModoIACacau.SOMENTE_CONTAGEM
                                                )
                                            )
                                        }
                                        .padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(m.nomeExibicao, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text("${formatarTamanhoMb(m.tamanhoBytes)} • ${if (ativo) "Ativo" else "Toque para ativar"}", fontSize = 11.sp, color = CacauTextoSecundario)
                                    }
                                    IconButton(
                                        onClick = {
                                            PersistenciaLocalRegistros.excluirModeloImportado(context, m.path)
                                            modelosImportados = PersistenciaLocalRegistros.listarModelosImportados(context)
                                            if (ativo) {
                                                onAtualizarConfig(configAtual.comModoIA(ModoIACacau.COMBINADO_2_IAS))
                                            }
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Remover modelo", tint = CacauPerigo, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (mostrarDiagnostico) {
        DialogDiagnosticoSobre(
            configAtual = configAtual,
            inferenciaMsAtual = inferenciaMsAtual,
            onDismiss = { mostrarDiagnostico = false }
        )
    }
}

@Composable
private fun DialogDiagnosticoSobre(
    configAtual: ModelConfig,
    inferenciaMsAtual: Long,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val versaoApp = remember {
        runCatching {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            info.versionName ?: "1.0"
        }.getOrDefault("1.0")
    }
    val tamanhoContagem = remember { tamanhoAssetStr(context, "detector.tflite") }
    val tamanhoDoenca = remember { tamanhoAssetStr(context, "detector_doenca.tflite") }
    val nucleos = remember { (Runtime.getRuntime().availableProcessors() / 2).coerceIn(2, 4) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Diagnóstico Técnico • CacauApp CEPEC", fontWeight = FontWeight.Bold, color = CacauVerdeEscuro)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                LinhaDiagnostico("Versão do App", versaoApp)
                LinhaDiagnostico("Modo IA Ativo", configAtual.modoEfetivo.nomeExibicao)
                LinhaDiagnostico("Motor TFLite", "XNNPACK + CPU ($nucleos threads)")
                LinhaDiagnostico("Entrada Tensor", "640 × 640 (Letterbox Isomórfico)")
                LinhaDiagnostico("Latência Ao Vivo", if (inferenciaMsAtual > 0L) "$inferenciaMsAtual ms/frame" else "Aguardando câmera")
                LinhaDiagnostico("detector.tflite", tamanhoContagem)
                LinhaDiagnostico("detector_doenca.tflite", tamanhoDoenca)
                LinhaDiagnostico("Classes Oficiais", LABELS_DOENCA_CACAU.joinToString(", "))
                LinhaDiagnostico("Modelos Embutidos", "${MODELOS_EMBUTIDOS.size} oficiais CEPEC")
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = CacauVerdeEscuro)
            ) {
                Text("Fechar")
            }
        }
    )
}

@Composable
private fun LinhaDiagnostico(chave: String, valor: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(chave, fontSize = 12.sp, color = CacauTextoSecundario, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(8.dp))
        Text(valor, fontSize = 12.sp, color = CacauTextoPrincipal, fontWeight = FontWeight.Bold)
    }
}

private fun tamanhoAssetStr(context: Context, assetName: String): String {
    return runCatching {
        context.assets.openFd(assetName).use { fd ->
            formatarTamanhoMb(fd.length)
        }
    }.getOrElse {
        runCatching {
            context.assets.open(assetName).use { formatarTamanhoMb(it.readBytes().size.toLong()) }
        }.getOrDefault("N/D")
    }
}

private fun formatarTamanhoMb(bytes: Long): String {
    if (bytes <= 0L) return "0 MB"
    val mb = bytes.toDouble() / (1024.0 * 1024.0)
    return String.format(Locale.US, "%.1f MB", mb).replace('.', ',')
}

private fun importarModeloCustomizadoCacau(
    context: Context,
    uri: Uri,
    configAtual: ModelConfig
): Pair<ModelConfig?, String> {
    return try {
        val dir = File(context.filesDir, "modelos_ia").apply { mkdirs() }
        val nomeOriginal = resolverNomeArquivoUri(context, uri)
        val ts = System.currentTimeMillis()
        var arquivoTflite: File? = null
        val labelsExtras = mutableListOf<String>()

        context.contentResolver.openInputStream(uri)?.use { input ->
            val cabecalho = ByteArray(4)
            val lidos = input.read(cabecalho)
            input.close()
            if (lidos < 4) return null to "Arquivo selecionado está vazio."

            val isZip = ImportadorPlanilhaUniversal.isXlsxMagicBytes(cabecalho) || nomeOriginal.endsWith(".zip", ignoreCase = true)
            if (isZip) {
                context.contentResolver.openInputStream(uri)?.use { zipRaw ->
                    ZipInputStream(zipRaw).use { zis ->
                        while (true) {
                            val entry = zis.nextEntry ?: break
                            if (!entry.isDirectory && ImportadorPlanilhaUniversal.isSafeZipEntryName(entry.name)) {
                                if (entry.name.endsWith(".tflite", ignoreCase = true) && arquivoTflite == null) {
                                    val bytes = ImportadorPlanilhaUniversal.readZipEntryBounded(zis, 128 * 1024 * 1024)
                                    if (bytes != null && ObjectDetectorHelper.isTfliteFlatBuffer(bytes)) {
                                        val dest = File(dir, "cepec_${ts}.tflite")
                                        dest.writeBytes(bytes)
                                        arquivoTflite = dest
                                    }
                                } else if (entry.name.endsWith(".txt", ignoreCase = true) || entry.name.endsWith(".yaml", ignoreCase = true)) {
                                    val lblBytes = ImportadorPlanilhaUniversal.readZipEntryBounded(zis, 512 * 1024)
                                    if (lblBytes != null) {
                                        labelsExtras.addAll(
                                            ObjectDetectorHelper.parseLabelsDeTextoOuYaml(
                                                String(lblBytes, Charsets.UTF_8),
                                                entry.name
                                            )
                                        )
                                    }
                                }
                            }
                            zis.closeEntry()
                        }
                    }
                }
            } else {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: return null to "Não foi possível ler o arquivo."
                if (!ObjectDetectorHelper.isTfliteFlatBuffer(bytes)) {
                    return null to "Arquivo não possui assinatura TFL3 válida de modelo TensorFlow Lite."
                }
                val baseLimpo = nomeOriginal.substringBeforeLast(".").replace(Regex("[^a-zA-Z0-9._-]"), "_").ifBlank { "modelo" }
                val dest = File(dir, "${baseLimpo}_$ts.tflite")
                dest.writeBytes(bytes)
                arquivoTflite = dest
            }
        }

        val arqFinal = arquivoTflite ?: return null to "Nenhum arquivo .tflite válido foi encontrado."
        val report = ObjectDetectorHelper.inspecionarModeloArquivo(arqFinal)
        if (!report.compativel) {
            arqFinal.delete()
            return null to report.avisoUsuario
        }

        val nomeAmigavel = nomeOriginal.substringBeforeLast(".").ifBlank { "Modelo CEPEC Customizado" }
        PersistenciaLocalRegistros.salvarNomeModeloImportado(context, arqFinal.absolutePath, nomeAmigavel)
        val labelsFinais = when {
            report.labelsExtraidas.isNotEmpty() -> report.labelsExtraidas
            labelsExtras.isNotEmpty() -> labelsExtras
            else -> configAtual.labelsFiltro
        }

        val novoConfig = configAtual.copy(
            modeloSelecionado = null,
            modeloInternoPath = arqFinal.absolutePath,
            modeloExternoUri = null,
            modeloExternoNome = nomeAmigavel,
            labelsFiltro = labelsFinais,
            usarDiagnosticoSecundario = false,
            modoIA = ModoIACacau.SOMENTE_CONTAGEM
        )
        novoConfig to report.detalhesTecnicos
    } catch (e: Exception) {
        null to "Erro ao importar modelo: ${e.message}"
    }
}

private fun resolverNomeArquivoUri(context: Context, uri: Uri): String {
    var result = "modelo_cacau.tflite"
    runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) {
                result = cursor.getString(idx) ?: result
            }
        }
    }
    return result
}
