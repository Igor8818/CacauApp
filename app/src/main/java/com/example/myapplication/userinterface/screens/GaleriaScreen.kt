package com.example.myapplication.userinterface.screens

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.myapplication.data.RegistroCacau
import com.example.myapplication.userinterface.components.CartaoMetrica
import com.example.myapplication.userinterface.util.compartilharArquivo
import com.example.myapplication.userinterface.util.exportarPlanilhaCEPECAssync
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Tela da Galeria com histórico de coletas, métricas consolidadas,
 * edição manual de frutos, exclusão com desfazer e exportação/compartilhamento direto.
 */
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
    val coroutineScope = rememberCoroutineScope()
    var itemEmEdicao by remember { mutableStateOf<RegistroCacau?>(null) }
    var textoEdicao by remember { mutableStateOf("") }
    var exportandoPlanilha by remember { mutableStateOf(false) }

    val totalRegistros = registros.size
    val totalFrutos = registros.sumOf { it.diagnostico.toIntOrNull() ?: 0 }
    val naoColetaveis = registros.count { it.diagnostico == "Não Coletável" }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Campanha: $ensaio", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("$totalRegistros plantas registradas", fontSize = 12.sp, color = Color.Gray)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                // Compartilhar CSV diretamente (WhatsApp / E-mail / Drive)
                IconButton(
                    onClick = {
                        coroutineScope.launch {
                            exportandoPlanilha = true
                            exportarPlanilhaCEPECAssync(context, registros, ensaio, l1, l2, l3) { sucesso, _, uri ->
                                exportandoPlanilha = false
                                if (sucesso && uri != null) {
                                    compartilharArquivo(context, uri, "Campanha_${ensaio}")
                                }
                            }
                        }
                    },
                    modifier = Modifier.size(48.dp),
                    enabled = registros.isNotEmpty() && !exportandoPlanilha
                ) {
                    Icon(Icons.Default.Share, contentDescription = "Compartilhar CSV", tint = Color(0xFF2E7D32), modifier = Modifier.size(24.dp))
                }

                // Salvar planilha no dispositivo
                IconButton(
                    onClick = {
                        coroutineScope.launch {
                            exportandoPlanilha = true
                            exportarPlanilhaCEPECAssync(context, registros, ensaio, l1, l2, l3) { _, msg, _ ->
                                exportandoPlanilha = false
                                android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    modifier = Modifier.size(48.dp),
                    enabled = registros.isNotEmpty() && !exportandoPlanilha
                ) {
                    if (exportandoPlanilha) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = Color(0xFF2E7D32))
                    } else {
                        Icon(Icons.Default.FileDownload, contentDescription = "Exportar CSV", tint = Color(0xFF2E7D32), modifier = Modifier.size(26.dp))
                    }
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
                    Text("Nenhum item registrado nesta campanha.", color = Color.Gray)
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
                                    modifier = Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Icon(Icons.Default.ReportProblem, null, tint = Color.Red, modifier = Modifier.size(64.dp).padding(8.dp))
                            }
                            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                                Text(reg.plantId, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(
                                    "Resultado: ${reg.diagnostico} | $l2: ${reg.parcelaManual}",
                                    fontSize = 12.sp,
                                    color = if (reg.diagnostico == "Não Coletável") Color(0xFFE65100) else Color.Gray
                                )
                                Text(
                                    "${reg.data} • Lat: ${"%.4f".format(Locale.US, reg.lat)}",
                                    fontSize = 11.sp,
                                    color = Color.LightGray
                                )
                            }
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
                ) {
                    Text("Salvar")
                }
            },
            dismissButton = {
                TextButton(onClick = { itemEmEdicao = null }) { Text("Cancelar") }
            }
        )
    }
}
