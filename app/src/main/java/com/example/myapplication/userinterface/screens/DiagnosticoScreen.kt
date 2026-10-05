package com.example.myapplication.userinterface.screens

import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.data.ModelConfig
import com.example.myapplication.userinterface.components.CartaoMetrica
import com.example.myapplication.userinterface.util.ObjectDetectorHelper
import com.example.myapplication.userinterface.util.assetModeloExiste
import java.io.File

/**
 * Tela de Diagnóstico Técnico e Informações do Sistema:
 * - Exibe metadados do modelo de IA (tamanho em disco, tensores de entrada/saída, latência);
 * - Status do GPS e da câmera;
 * - Informações de hardware e versão do CacauApp.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticoScreen(
    modelConfig: ModelConfig,
    detectorHelper: ObjectDetectorHelper?,
    onVoltar: () -> Unit
) {
    val context = LocalContext.current
    val verde = Color(0xFF2E7D32)

    val tamanhoModeloStr = run {
        if (modelConfig.modeloSelecionado != null) {
            val f = File(context.cacheDir, modelConfig.modeloSelecionado.nomeArquivo)
            if (f.exists()) "%.2f MB".format(f.length().toDouble() / (1024 * 1024))
            else if (assetModeloExiste(context, modelConfig.modeloSelecionado.nomeArquivo)) "Embutido (Assets)"
            else "Não encontrado"
        } else if (modelConfig.modeloExternoUri != null) {
            val f = File(context.filesDir, "modelo_externo.tflite")
            if (f.exists()) "%.2f MB".format(f.length().toDouble() / (1024 * 1024))
            else "Externo"
        } else {
            "Sem modelo"
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Diagnóstico e Sobre", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onVoltar) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text("Métricas de Desempenho", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF1B5E20))
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    val latencia = detectorHelper?.ultimaLatenciaMs ?: 0L
                    CartaoMetrica(
                        modifier = Modifier.weight(1f),
                        icone = Icons.Default.Speed,
                        valor = if (latencia > 0) "${latencia}ms" else "--",
                        rotulo = "Latência IA",
                        cor = Color(0xFF2E7D32)
                    )
                    CartaoMetrica(
                        modifier = Modifier.weight(1f),
                        icone = Icons.Default.Memory,
                        valor = "${detectorHelper?.inputSize ?: 300}px",
                        rotulo = "Resolução IA",
                        cor = Color(0xFF1B5E20)
                    )
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Modelo de Visão Computacional", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = verde)
                        HorizontalDivider()
                        ItemInfoDiagnostico("Identificador", modelConfig.nomeExibicao)
                        ItemInfoDiagnostico("Arquivo", modelConfig.nomeArquivoModelo)
                        ItemInfoDiagnostico("Tamanho em Disco", tamanhoModeloStr)
                        ItemInfoDiagnostico("Âncoras de Detecção", "${detectorHelper?.numAnchors ?: 0}")
                        ItemInfoDiagnostico("Classes Mapeadas", "${detectorHelper?.numClasses ?: 1}")
                        ItemInfoDiagnostico("Threshold Ativo", "${(modelConfig.threshold * 100).toInt()}%")
                        ItemInfoDiagnostico("Rótulos Ativos", modelConfig.labelsFiltro.joinToString(", ").ifBlank { "Todos" })
                        ItemInfoDiagnostico("Aceleração", if (detectorHelper != null) "GPU Delegate (com fallback CPU)" else "Inativo")
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Dispositivo e Aplicação", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = verde)
                        HorizontalDivider()
                        ItemInfoDiagnostico("CacauApp CEPEC", "Versão 1.0 (Build 2026)")
                        ItemInfoDiagnostico("Dispositivo", "${Build.MANUFACTURER.uppercase()} ${Build.MODEL}")
                        ItemInfoDiagnostico("Android OS", "API ${Build.VERSION.SDK_INT} (Android ${Build.VERSION.RELEASE})")
                        ItemInfoDiagnostico("Modo de Operação", "100% Offline (Edge AI)")
                        ItemInfoDiagnostico("Exportação CSV", "RFC 4180 com UTF-8 BOM (;)")
                    }
                }
            }
        }
    }
}

@Composable
private fun ItemInfoDiagnostico(titulo: String, valor: String) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(titulo, fontSize = 13.sp, color = Color.Gray)
        Text(valor, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.Black)
    }
}
