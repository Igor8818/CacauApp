package com.example.myapplication.userinterface

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.data.ModelConfig
import com.example.myapplication.data.ModelEmbutido
import com.example.myapplication.data.MODELOS_EMBUTIDOS

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelaConfiguracaoModelo(
    configAtual: ModelConfig,
    onSalvar: (ModelConfig) -> Unit,
    onVoltar: () -> Unit
) {
    // --- estados locais para edição ---
    var modeloSelecionado by remember { mutableStateOf(configAtual.modeloSelecionado) }
    var modeloExternoUri by remember { mutableStateOf(configAtual.modeloExternoUri) }
    var modeloExternoNome by remember { mutableStateOf(configAtual.modeloExternoNome) }

    var labelsTexto by remember { mutableStateOf(configAtual.labelsFiltro.joinToString(", ")) }
    var thresholdTexto by remember { mutableStateOf((configAtual.threshold * 100).toInt().toString()) }
    var maxResultados by remember { mutableStateOf(configAtual.maxResultados) }

    var novoLabel by remember { mutableStateOf("") }

    val verde = Color(0xFF2E7D32)

    val launcherModelo = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            modeloExternoUri = it
            modeloSelecionado = null // desseleciona embutido
            // Tenta pegar o nome do arquivo
            modeloExternoNome = it.lastPathSegment?.substringAfterLast("/") ?: "modelo_externo.tflite"
        }
    }

    // Parse dos labels atuais como lista
    val listaLabels = remember(labelsTexto) {
        labelsTexto.split(",").map { it.trim() }.filter { it.isNotBlank() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Configurar Modelo de IA", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onVoltar) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Voltar")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = verde,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {

            // ── SEÇÃO 1: MODELOS EMBUTIDOS ──
            item {
                SectionHeader(icon = Icons.Default.Memory, title = "Modelos Embutidos no App")
            }

            items(MODELOS_EMBUTIDOS) { modelo ->
                CartaoModeloEmbutido(
                    modelo = modelo,
                    selecionado = modeloSelecionado?.nomeArquivo == modelo.nomeArquivo &&
                            modeloSelecionado?.nomeExibicao == modelo.nomeExibicao,
                    onClick = {
                        modeloSelecionado = modelo
                        modeloExternoUri = null
                        modeloExternoNome = ""
                        // Preenche os labels padrão do modelo
                        labelsTexto = modelo.labelsDefault.joinToString(", ")
                    }
                )
            }

            // ── SEÇÃO 2: MODELO EXTERNO ──
            item {
                SectionHeader(icon = Icons.Default.FolderOpen, title = "Importar Modelo Externo (.tflite)")
            }

            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = if (modeloExternoUri != null) 2.dp else 1.dp,
                            color = if (modeloExternoUri != null) verde else Color.LightGray,
                            shape = RoundedCornerShape(12.dp)
                        ),
                    colors = CardDefaults.cardColors(
                        containerColor = if (modeloExternoUri != null) Color(0xFFE8F5E9) else Color.White
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (modeloExternoUri != null) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CheckCircle, null, tint = verde)
                                Spacer(Modifier.width(8.dp))
                                Text("Modelo carregado:", fontSize = 12.sp, color = Color.Gray)
                            }
                            Text(modeloExternoNome, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(
                                "⚠️ Após importar, configure os rótulos abaixo manualmente.",
                                fontSize = 12.sp,
                                color = Color(0xFFE65100)
                            )
                        } else {
                            Text(
                                "Selecione um arquivo .tflite exportado do seu treinamento YOLO/TFLite.",
                                fontSize = 13.sp,
                                color = Color.Gray
                            )
                        }
                        Button(
                            onClick = { launcherModelo.launch("*/*") },
                            colors = ButtonDefaults.buttonColors(containerColor = verde),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Upload, null)
                            Spacer(Modifier.width(8.dp))
                            Text(if (modeloExternoUri != null) "TROCAR MODELO" else "SELECIONAR ARQUIVO .tflite")
                        }
                    }
                }
            }

            // ── SEÇÃO 3: RÓTULOS (LABELS) ──
            item {
                SectionHeader(icon = Icons.Default.Label, title = "Rótulos Detectados (Labels)")
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF9F9F9))
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "Somente objetos com estes rótulos serão contados. Deixe vazio para aceitar todos.",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )

                        // Chips dos labels atuais
                        if (listaLabels.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                listaLabels.chunked(3).forEach { linha ->
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        linha.forEach { label ->
                                            LabelChip(
                                                texto = label,
                                                onRemover = {
                                                    val nova = listaLabels.toMutableList().also { l -> l.remove(label) }
                                                    labelsTexto = nova.joinToString(", ")
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            Text("Nenhum filtro — todos os objetos serão contados", color = Color.Gray, fontSize = 13.sp)
                        }

                        // Campo para adicionar novo label
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = novoLabel,
                                onValueChange = { novoLabel = it },
                                label = { Text("Adicionar rótulo") },
                                placeholder = { Text("ex: cacau, fruto...") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                            IconButton(
                                onClick = {
                                    if (novoLabel.isNotBlank()) {
                                        val nova = listaLabels.toMutableList()
                                        nova.add(novoLabel.trim().lowercase())
                                        labelsTexto = nova.joinToString(", ")
                                        novoLabel = ""
                                    }
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(verde)
                            ) {
                                Icon(Icons.Default.Add, null, tint = Color.White)
                            }
                        }

                        // Botão para limpar tudo
                        if (listaLabels.isNotEmpty()) {
                            TextButton(
                                onClick = { labelsTexto = "" },
                                colors = ButtonDefaults.textButtonColors(contentColor = Color.Red)
                            ) {
                                Icon(Icons.Default.ClearAll, null)
                                Spacer(Modifier.width(4.dp))
                                Text("Aceitar todos os rótulos")
                            }
                        }
                    }
                }
            }

            // ── SEÇÃO 4: THRESHOLD E MAX RESULTADOS ──
            item {
                SectionHeader(icon = Icons.Default.Tune, title = "Parâmetros de Detecção")
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF9F9F9))
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {

                        // Threshold
                        Column {
                            val thresholdVal = thresholdTexto.toIntOrNull()?.coerceIn(1, 99) ?: 55
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                Text("Confiança mínima: $thresholdVal%", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                                if (thresholdVal > 60) {
                                    Surface(shape = RoundedCornerShape(6.dp), color = Color(0xFFFFECB3)) {
                                        Text(
                                            "⚠ Muito alto",
                                            fontSize = 11.sp,
                                            color = Color(0xFFE65100),
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                            Text(
                                if (thresholdVal > 60)
                                    "Modelos em desenvolvimento costumam ter score entre 40–70%. Recomendamos 30–50%."
                                else
                                    "Detecções abaixo deste valor serão ignoradas. Para modelos novos, use valores baixos (20–40%).",
                                fontSize = 12.sp,
                                color = if (thresholdVal > 60) Color(0xFFE65100) else Color.Gray
                            )
                            Slider(
                                value = thresholdVal.toFloat(),
                                onValueChange = { thresholdTexto = it.toInt().toString() },
                                valueRange = 10f..95f,
                                steps = 16,
                                colors = SliderDefaults.colors(thumbColor = if (thresholdVal > 60) Color(0xFFE65100) else verde, activeTrackColor = if (thresholdVal > 60) Color(0xFFE65100) else verde)
                            )
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("10% (detecta mais)", fontSize = 11.sp, color = Color.Gray)
                                Text("95%", fontSize = 11.sp, color = Color.Gray)
                            }
                        }

                        Divider()

                        // Max resultados
                        Column {
                            Text("Máximo de detecções: $maxResultados", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                            Text("Quantos objetos podem ser detectados por frame.", fontSize = 12.sp, color = Color.Gray)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                IconButton(onClick = { if (maxResultados > 1) maxResultados-- }) {
                                    Icon(Icons.Default.RemoveCircle, null, tint = Color.Red, modifier = Modifier.size(36.dp))
                                }
                                Text("$maxResultados", fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 24.dp))
                                IconButton(onClick = { if (maxResultados < 50) maxResultados++ }) {
                                    Icon(Icons.Default.AddCircle, null, tint = verde, modifier = Modifier.size(36.dp))
                                }
                            }
                        }
                    }
                }
            }

            // ── BOTÃO SALVAR ──
            item {
                Spacer(Modifier.height(8.dp))
                val podeSalvar = modeloSelecionado != null || modeloExternoUri != null
                Button(
                    onClick = {
                        val threshold = (thresholdTexto.toIntOrNull() ?: 50) / 100f
                        onSalvar(
                            ModelConfig(
                                modeloSelecionado = modeloSelecionado,
                                modeloExternoUri = modeloExternoUri,
                                modeloExternoNome = modeloExternoNome,
                                labelsFiltro = listaLabels,
                                threshold = threshold,
                                maxResultados = maxResultados
                            )
                        )
                    },
                    enabled = podeSalvar,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = verde)
                ) {
                    Icon(Icons.Default.Save, null)
                    Spacer(Modifier.width(8.dp))
                    Text("SALVAR CONFIGURAÇÃO", fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

// ── Componentes auxiliares ──

@Composable
private fun SectionHeader(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Color(0xFF2E7D32), modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF1B5E20))
    }
}

@Composable
private fun CartaoModeloEmbutido(
    modelo: ModelEmbutido,
    selecionado: Boolean,
    onClick: () -> Unit
) {
    val verde = Color(0xFF2E7D32)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (selecionado) 2.dp else 1.dp,
                color = if (selecionado) verde else Color.LightGray,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (selecionado) Color(0xFFE8F5E9) else Color.White
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (selecionado) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,
                null,
                tint = if (selecionado) verde else Color.Gray
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(modelo.nomeExibicao, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(modelo.descricao, fontSize = 12.sp, color = Color.Gray)
                Text(
                    "Labels: ${modelo.labelsDefault.joinToString(", ")}",
                    fontSize = 11.sp,
                    color = Color(0xFF558B2F)
                )
            }
        }
    }
}

@Composable
private fun LabelChip(texto: String, onRemover: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFFE8F5E9),
        modifier = Modifier.border(1.dp, Color(0xFF81C784), RoundedCornerShape(20.dp))
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Text(texto, fontSize = 13.sp, color = Color(0xFF2E7D32))
            Spacer(Modifier.width(4.dp))
            Icon(
                Icons.Default.Close,
                contentDescription = "Remover $texto",
                tint = Color(0xFF2E7D32),
                modifier = Modifier
                    .size(14.dp)
                    .clickable { onRemover() }
            )
        }
    }
}
