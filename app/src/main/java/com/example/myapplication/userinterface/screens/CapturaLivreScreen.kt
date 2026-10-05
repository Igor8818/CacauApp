package com.example.myapplication.userinterface.screens

import android.app.Activity
import android.net.Uri
import android.view.WindowManager
import android.widget.Toast
import androidx.camera.core.CameraControl
import androidx.camera.core.ImageCapture
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.example.myapplication.R
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.data.DetectionResult
import com.example.myapplication.data.ModelConfig
import com.example.myapplication.userinterface.components.AvisoModeloAusente
import com.example.myapplication.userinterface.util.CameraWithAI
import com.example.myapplication.userinterface.util.buildDetectorHelper
import com.example.myapplication.userinterface.util.tirarFoto
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Tela de Coleta Livre (Manual) para captura rápida e auditada de plantas individuais.
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
    jaExistePlantId: (String) -> Boolean,
    onSave: (Uri, String, Int, Int) -> Unit
) {
    val context = LocalContext.current
    var cameraAtiva by remember { mutableStateOf(false) }
    var menuClonesExpandido by remember { mutableStateOf(false) }
    var mostrarDialogLabels by remember { mutableStateOf(false) }
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

    val pExibicao = if (isNum2) parcelaNum.toString() else parcelaTxt
    val plExibicao = if (isNum3) plantaNum.toString() else plantaTxt
    val plantIdAtual = "${cloneVal}_${label2}${pExibicao}_${label3}${plExibicao}"
    val idJaExiste = cloneVal.isNotBlank() && jaExistePlantId(plantIdAtual)

    val isCloneValido = cloneVal.isNotBlank() &&
            (if (isNum2) true else parcelaTxt.isNotBlank()) &&
            (if (isNum3) true else plantaTxt.isNotBlank())

    when {
        fotoParaRevisaoUri != null -> {
            RevisaoCapturaScreen(
                fotoUri = fotoParaRevisaoUri!!,
                plantId = plantIdAtual,
                modelConfig = modelConfig,
                detectorHelper = detectorHelper,
                onConfirmar = { contagemIa, contagemFinal ->
                    onSave(fotoParaRevisaoUri!!, contagemFinal.toString(), contagemIa, contagemFinal)
                    fotoParaRevisaoUri = null
                    cameraAtiva = false
                },
                onRefazer = {
                    fotoParaRevisaoUri = null
                }
            )
        }

        !cameraAtiva || detectorHelper == null -> {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(id = R.drawable.app_logo),
                            contentDescription = null,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .border(1.dp, Color(0xFFE0D5C1), RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("CacauApp CEPEC", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = Color(0xFF1B5E20))
                            Text("Campanha: $ensaio", fontSize = 12.sp, color = Color(0xFF5D4037))
                        }
                    }
                    IconButton(onClick = { mostrarDialogLabels = true }) {
                        Icon(Icons.Default.Settings, null, tint = Color(0xFF2E7D32))
                    }
                }

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
                    onClick = { onSave(Uri.EMPTY, "Não Coletável", 0, 0) },
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

                        // Contorno escuro para contraste + linha verde-neon
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(10.dp).background(
                                    if (contagemFrutos > 0) Color(0xFF00E676) else Color(0xFFFFB300),
                                    androidx.compose.foundation.shape.CircleShape
                                )
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Frutos detectados: $contagemFrutos", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                        Text(
                            "${modelConfig.nomeArquivoModelo} · ${(modelConfig.threshold * 100).roundToInt()}% min · ${detectorHelper.ultimaLatenciaMs}ms",
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
                        tirarFoto(
                            context = context,
                            capture = imageCapture,
                            id = plantIdAtual,
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
