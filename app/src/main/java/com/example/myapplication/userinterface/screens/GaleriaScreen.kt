package com.example.myapplication.userinterface.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.example.myapplication.data.SituacaoPlanta
import com.example.myapplication.data.ocorrencias
import com.example.myapplication.userinterface.components.CartaoMetrica
import com.example.myapplication.userinterface.util.compartilharArquivo
import com.example.myapplication.userinterface.util.exportarPlanilhaCEPECAssync
import kotlinx.coroutines.launch
import java.util.Locale

private enum class FiltroGaleria(val rotulo: String) {
    TODAS("Todas"),
    DOENTES("Com Doença"),
    INATIVAS("Cavalo / Morta / Errada")
}

/**
 * Tela da Galeria com histórico de coletas, filtros por sanidade/situação,
 * edição completa de contagem e variáveis CEPLAC, exclusão com desfazer e exportação CSV.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TelaGaleria(
    registros: List<RegistroCacau>,
    ensaio: String,
    l1: String,
    l2: String,
    l3: String,
    onExcluirRegistro: (RegistroCacau) -> Unit,
    onEditarContagem: (plantId: String, novaContagem: String) -> Unit,
    onAtualizarRegistroCompleto: ((RegistroCacau) -> Unit)? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var itemEmEdicao by remember { mutableStateOf<RegistroCacau?>(null) }
    var textoEdicao by remember { mutableStateOf("") }
    var sitPlaEdicao by remember { mutableStateOf(SituacaoPlanta.NORMAL.rotulo) }
    var dadosAgroEdicao by remember { mutableStateOf(DadosAgronomicosPlanta()) }
    var exportandoPlanilha by remember { mutableStateOf(false) }
    var filtroAtivo by remember { mutableStateOf(FiltroGaleria.TODAS) }

    BackHandler(enabled = itemEmEdicao != null) {
        itemEmEdicao = null
    }

    val totalRegistros = registros.size
    val totalFrutos = registros.sumOf { if (it.isInativa) 0 else (it.diagnostico.toIntOrNull() ?: 0) }
    val inativas = registros.count { it.isInativa }
    val comDoenca = registros.count {
        it.observacao.isNotBlank() || it.nfVassoura > 0 || it.nfPodridao > 0 || it.nvTotal > 0 || it.notaVirus > 0
    }

    val registrosFiltrados = remember(registros, filtroAtivo) {
        when (filtroAtivo) {
            FiltroGaleria.TODAS -> registros
            FiltroGaleria.DOENTES -> registros.filter {
                it.observacao.isNotBlank() || it.nfVassoura > 0 || it.nfPodridao > 0 || it.nvTotal > 0 || it.notaVirus > 0
            }
            FiltroGaleria.INATIVAS -> registros.filter { it.isInativa }
        }
    }

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

        if (registros.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                FiltroGaleria.entries.forEach { f ->
                    val qtd = when (f) {
                        FiltroGaleria.TODAS -> totalRegistros
                        FiltroGaleria.DOENTES -> comDoenca
                        FiltroGaleria.INATIVAS -> inativas
                    }
                    FilterChip(
                        selected = filtroAtivo == f,
                        onClick = { filtroAtivo = f },
                        label = { Text("${f.rotulo} ($qtd)", fontSize = 11.sp) }
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        if (registrosFiltrados.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Collections, null, tint = Color.LightGray, modifier = Modifier.size(64.dp))
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (registros.isEmpty()) "Nenhum item registrado nesta campanha."
                        else "Nenhuma planta neste filtro.",
                        color = Color.Gray
                    )
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(registrosFiltrados.reversed(), key = { it.plantId + it.data + it.timestampMs }) { reg ->
                    Card(
                        Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (reg.isInativa) Color(0xFFFFF3E0) else Color.White
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
                                Icon(
                                    if (reg.isInativa) Icons.Default.ReportProblem else Icons.Default.Eco,
                                    null,
                                    tint = if (reg.isInativa) Color(0xFFE65100) else Color(0xFF2E7D32),
                                    modifier = Modifier.size(64.dp).padding(12.dp)
                                )
                            }
                            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                                Text(reg.plantId, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                val textoSit = if (reg.isInativa) "Situação: ${reg.sitPla}" else "Frutos: ${reg.diagnostico} (${reg.sitPla})"
                                val infoBloco = if (reg.bloco.isNotBlank()) "Bloco ${reg.bloco} · " else ""
                                Text(
                                    "$textoSit | $infoBloco$l2: ${reg.parcelaManual}",
                                    fontSize = 12.sp,
                                    color = if (reg.isInativa) Color(0xFFE65100) else Color.DarkGray
                                )
                                val extrasCeplac = buildList {
                                    if (reg.corFruto.isNotBlank()) add("Cor: ${reg.corFruto}")
                                    if (reg.nfVassoura > 0) add("NFVas: ${reg.nfVassoura}")
                                    if (reg.nfPodridao > 0) add("NFPod: ${reg.nfPodridao}")
                                    if (reg.nvTotal > 0) add("NVTot: ${reg.nvTotal}")
                                    if (reg.notaVirus > 0) add("Vírus: ${reg.notaVirus}")
                                }.joinToString(" · ")
                                if (extrasCeplac.isNotBlank()) {
                                    Text(extrasCeplac, fontSize = 11.sp, color = Color(0xFF2E7D32), fontWeight = FontWeight.Medium)
                                }
                                if (reg.observacao.isNotBlank()) {
                                    Text(
                                        "⚠ " + reg.observacao.split("|").filter { it.isNotBlank() }.joinToString(", "),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFFC62828)
                                    )
                                }
                                Text(
                                    "${reg.data} • Lat: ${"%.4f".format(Locale.US, reg.lat)}",
                                    fontSize = 10.sp,
                                    color = Color.Gray
                                )
                            }
                            IconButton(onClick = {
                                itemEmEdicao = reg
                                textoEdicao = if (reg.isInativa) "0" else reg.diagnostico
                                sitPlaEdicao = reg.sitPla.ifBlank { SituacaoPlanta.NORMAL.rotulo }
                                dadosAgroEdicao = DadosAgronomicosPlanta(
                                    sitPla = sitPlaEdicao,
                                    ocorrencias = reg.observacao.ocorrencias().toSet(),
                                    nfPequenos = reg.nfPequenos,
                                    nfMedios = reg.nfMedios,
                                    nfAdultos = reg.nfAdultos,
                                    nfMaduros = reg.nfMaduros,
                                    nfVassoura = reg.nfVassoura,
                                    nfPodridao = reg.nfPodridao,
                                    nfOutrasPerdas = reg.nfOutrasPerdas,
                                    nvTotal = reg.nvTotal,
                                    corFruto = reg.corFruto,
                                    notaVirus = reg.notaVirus
                                )
                            }) {
                                Icon(Icons.Default.Edit, "Editar registro", tint = Color.Gray, modifier = Modifier.size(20.dp))
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
        val regOriginal = itemEmEdicao!!
        AlertDialog(
            onDismissRequest = { itemEmEdicao = null },
            title = { Text("Editar Registro da Planta", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(regOriginal.plantId, fontSize = 12.sp, color = Color.Gray, fontWeight = FontWeight.SemiBold)

                    Text("Situação da Planta (SitPla):", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        SituacaoPlanta.entries.forEach { sit ->
                            FilterChip(
                                selected = sitPlaEdicao.equals(sit.rotulo, ignoreCase = true),
                                onClick = { sitPlaEdicao = sit.rotulo },
                                label = { Text(sit.rotulo, fontSize = 12.sp) }
                            )
                        }
                    }

                    if (!SituacaoPlanta.ehInativa(sitPlaEdicao)) {
                        OutlinedTextField(
                            value = textoEdicao,
                            onValueChange = { textoEdicao = it.filter { c -> c.isDigit() } },
                            label = { Text("Quantidade Total de Frutos") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        ConteudoFichaCeplacLimpa(
                            dados = dadosAgroEdicao,
                            onDadosChange = {
                                dadosAgroEdicao = it
                            }
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (onAtualizarRegistroCompleto != null) {
                            val inativa = SituacaoPlanta.ehInativa(sitPlaEdicao)
                            val qtdFinalInt = if (inativa) 0 else (textoEdicao.toIntOrNull() ?: regOriginal.contagemFinal)
                            val diagFinal = if (inativa) sitPlaEdicao else qtdFinalInt.toString()
                            val atualizado = regOriginal.copy(
                                diagnostico = diagFinal,
                                contagemFinal = qtdFinalInt,
                                sitPla = sitPlaEdicao,
                                observacao = dadosAgroEdicao.observacaoFormatada,
                                nfPequenos = if (inativa) 0 else dadosAgroEdicao.nfPequenos,
                                nfMedios = if (inativa) 0 else dadosAgroEdicao.nfMedios,
                                nfAdultos = if (inativa) 0 else dadosAgroEdicao.nfAdultos,
                                nfMaduros = if (inativa) 0 else dadosAgroEdicao.nfMaduros,
                                nfVassoura = if (inativa) 0 else dadosAgroEdicao.nfVassoura,
                                nfPodridao = if (inativa) 0 else dadosAgroEdicao.nfPodridao,
                                nfOutrasPerdas = if (inativa) 0 else dadosAgroEdicao.nfOutrasPerdas,
                                nvTotal = if (inativa) 0 else dadosAgroEdicao.nvTotal,
                                corFruto = if (inativa) "" else dadosAgroEdicao.corFruto,
                                notaVirus = if (inativa) 0 else dadosAgroEdicao.notaVirus
                            )
                            onAtualizarRegistroCompleto(atualizado)
                        } else {
                            onEditarContagem(regOriginal.plantId, if (SituacaoPlanta.ehInativa(sitPlaEdicao)) sitPlaEdicao else textoEdicao)
                        }
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
