package com.example.myapplication.userinterface.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.data.OcorrenciaCampo
import com.example.myapplication.data.RotaColeta
import java.util.Locale

private val VerdeCacau = Color(0xFF2E7D32)
private val VerdeClaro = Color(0xFFE8F5E9)
private const val NAO_COLETAVEL = "Não Coletável"

/** Resumo numérico do andamento do FieldBook. */
data class EstatisticasRota(
    val coletadas: Int,
    val total: Int,
    val frutos: Int,
    val mortas: Int,
    val comOcorrencia: Int
) {
    val pendentes: Int get() = total - coletadas
    val percentual: Float get() = if (total == 0) 0f else coletadas.toFloat() / total
    /** Média de frutos por planta efetivamente avaliada (exclui plantas mortas/ausentes). */
    val mediaFrutos: Float get() {
        val avaliadas = coletadas - mortas
        return if (avaliadas <= 0) 0f else frutos.toFloat() / avaliadas
    }
}

fun List<RotaColeta>.estatisticas(): EstatisticasRota {
    val coletadas = filter { it.coletado }
    return EstatisticasRota(
        coletadas = coletadas.size,
        total = size,
        frutos = coletadas.sumOf { it.qtdFrutos.toIntOrNull() ?: 0 },
        mortas = coletadas.count { it.qtdFrutos == NAO_COLETAVEL },
        comOcorrencia = coletadas.count { it.observacao.isNotBlank() }
    )
}

/**
 * Índice da próxima planta pendente após [aPartirDe]; se não houver, procura desde o início.
 * Ignora o próprio índice [aPartirDe]. Retorna null se tudo já foi coletado.
 */
fun proximoPendente(rota: List<RotaColeta>, aPartirDe: Int): Int? =
    rota.indices.firstOrNull { it > aPartirDe && !rota[it].coletado }
        ?: rota.indices.firstOrNull { it != aPartirDe && !rota[it].coletado }

@Composable
fun PainelEstatisticasRota(stats: EstatisticasRota, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    "${stats.coletadas} de ${stats.total} plantas",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = Color(0xFF1B5E20)
                )
                Text(
                    "${(stats.percentual * 100).toInt()}%",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 14.sp,
                    color = VerdeCacau
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                IndicadorRota("${stats.frutos}", "frutos", VerdeCacau)
                IndicadorRota("%.1f".format(Locale.US, stats.mediaFrutos), "média/planta", Color(0xFF5D4037))
                IndicadorRota("${stats.pendentes}", "pendentes", Color(0xFFE65100))
                IndicadorRota("${stats.comOcorrencia}", "ocorrências", Color(0xFFC62828))
            }
        }
    }
}

@Composable
private fun IndicadorRota(valor: String, rotulo: String, cor: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(valor, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = cor)
        Text(rotulo, fontSize = 10.sp, color = Color.Gray)
    }
}

/** Chips de ocorrência fitossanitária (vassoura-de-bruxa, podridão-parda, etc.). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SeletorOcorrencias(
    selecionadas: Set<String>,
    onAlternar: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier) {
        Text("OCORRÊNCIAS NA PLANTA", fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OcorrenciaCampo.entries.forEach { oc ->
                FilterChip(
                    selected = oc.rotulo in selecionadas,
                    onClick = { onAlternar(oc.rotulo) },
                    label = { Text(oc.rotulo, fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFFFFCDD2),
                        selectedLabelColor = Color(0xFFB71C1C)
                    )
                )
            }
        }
    }
}

/**
 * Mapa da rota: visão geral por parcela/clone com status de cada planta,
 * busca rápida e salto direto para qualquer ponto.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MapaDaRotaDialog(
    rota: List<RotaColeta>,
    indiceAtual: Int,
    onSelecionar: (Int) -> Unit,
    onFechar: () -> Unit
) {
    var busca by remember { mutableStateOf("") }
    var somentePendentes by remember { mutableStateOf(false) }

    val grupos = remember(rota, busca, somentePendentes) {
        rota.withIndex()
            .filter { (_, p) ->
                (!somentePendentes || !p.coletado) &&
                    (busca.isBlank() || "${p.clone} ${p.parcela} ${p.planta}".contains(busca.trim(), ignoreCase = true))
            }
            .groupBy { (_, p) -> p.parcela to p.clone }
            .entries.toList()
    }

    AlertDialog(
        onDismissRequest = onFechar,
        title = { Text("Mapa da Rota", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                OutlinedTextField(
                    value = busca,
                    onValueChange = { busca = it },
                    placeholder = { Text("Buscar clone, parcela ou planta") },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = {
                        if (busca.isNotEmpty()) {
                            IconButton(onClick = { busca = "" }) { Icon(Icons.Default.Close, "Limpar busca") }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
                    FilterChip(
                        selected = somentePendentes,
                        onClick = { somentePendentes = !somentePendentes },
                        label = { Text("Somente pendentes", fontSize = 12.sp) }
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 6.dp)) {
                    LegendaMapa(VerdeClaro, "coletada")
                    LegendaMapa(Color(0xFFFFE0B2), "pendente")
                    LegendaMapa(Color(0xFFFFCDD2), "morta/ausente")
                }
                if (grupos.isEmpty()) {
                    Text("Nenhuma planta encontrada.", color = Color.Gray, modifier = Modifier.padding(vertical = 16.dp))
                }
                LazyColumn(Modifier.heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(grupos, key = { "${it.key.first}|${it.key.second}" }) { grupo ->
                        val feitas = grupo.value.count { it.value.coletado }
                        Column {
                            Text(
                                "Parcela ${grupo.key.first} · ${grupo.key.second}  ($feitas/${grupo.value.size})",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF4E342E)
                            )
                            Spacer(Modifier.height(4.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                grupo.value.forEach { (idx, p) ->
                                    val morta = p.coletado && p.qtdFrutos == NAO_COLETAVEL
                                    val fundo = when {
                                        morta -> Color(0xFFFFCDD2)
                                        p.coletado -> VerdeClaro
                                        else -> Color(0xFFFFE0B2)
                                    }
                                    Box(
                                        modifier = Modifier
                                            .size(width = 46.dp, height = 40.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(fundo)
                                            .then(
                                                if (idx == indiceAtual) Modifier.border(2.dp, VerdeCacau, RoundedCornerShape(10.dp))
                                                else Modifier
                                            )
                                            .clickable { onSelecionar(idx) },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("${p.planta}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onFechar) { Text("Fechar") } }
    )
}

@Composable
private fun LegendaMapa(cor: Color, texto: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(cor))
        Spacer(Modifier.width(4.dp))
        Text(texto, fontSize = 10.sp, color = Color.Gray)
    }
}
