package com.example.myapplication.userinterface.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.data.*
import java.util.Locale

private val VerdeCacau = Color(0xFF2E7D32)
private val VerdeClaro = Color(0xFFE8F5E9)

/** Pacote de dados agronômicos opcionais da ficha CEPLAC / Field Book para uma planta. */
data class DadosAgronomicosPlanta(
    val sitPla: String = SituacaoPlanta.NORMAL.rotulo,
    val ocorrencias: Set<String> = emptySet(),
    val nfPequenos: Int = 0,
    val nfMedios: Int = 0,
    val nfAdultos: Int = 0,
    val nfMaduros: Int = 0,
    val nfVassoura: Int = 0,
    val nfPodridao: Int = 0,
    val nfOutrasPerdas: Int = 0,
    val nvTotal: Int = 0,
    val corFruto: String = "",
    val notaVirus: Int = 0
) {
    val observacaoFormatada: String
        get() {
            val tags = ocorrencias.toMutableSet()
            if (nfVassoura > 0 || nvTotal > 0) tags.add(OcorrenciaCampo.VASSOURA_BRUXA.rotulo)
            if (nfPodridao > 0) tags.add(OcorrenciaCampo.PODRIDAO_PARDA.rotulo)
            return tags.toList().comoObservacao()
        }

    val somaPorteFrutos: Int
        get() = nfPequenos + nfMedios + nfAdultos + nfMaduros

    val temDetalhesPreenchidos: Boolean
        get() = nfVassoura > 0 || nfPodridao > 0 || nfOutrasPerdas > 0 ||
            nvTotal > 0 || corFruto.isNotBlank() || notaVirus > 0 || somaPorteFrutos > 0

    companion object {
        fun deRota(ponto: RotaColeta, corSugeridaClone: String = ""): DadosAgronomicosPlanta =
            DadosAgronomicosPlanta(
                sitPla = ponto.situacaoEfetiva.ifBlank { SituacaoPlanta.NORMAL.rotulo },
                ocorrencias = ponto.observacao.ocorrencias().toSet(),
                nfPequenos = ponto.nfPequenos,
                nfMedios = ponto.nfMedios,
                nfAdultos = ponto.nfAdultos,
                nfMaduros = ponto.nfMaduros,
                nfVassoura = ponto.nfVassoura,
                nfPodridao = ponto.nfPodridao,
                nfOutrasPerdas = ponto.nfOutrasPerdas,
                nvTotal = ponto.nvTotal,
                corFruto = ponto.corFruto.ifBlank { corSugeridaClone },
                notaVirus = ponto.notaVirus
            )
    }
}

/** Resumo numérico do andamento do FieldBook. */
data class EstatisticasRota(
    val coletadas: Int,
    val total: Int,
    val frutos: Int,
    val mortas: Int,
    val comOcorrencia: Int,
    val cavalos: Int = 0,
    val erradas: Int = 0
) {
    val pendentes: Int get() = total - coletadas
    val percentual: Float get() = if (total == 0) 0f else coletadas.toFloat() / total
    /** Média de frutos por planta efetivamente avaliada (exclui plantas mortas/cavalo/erradas). */
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
        frutos = coletadas.sumOf { if (it.isInativa) 0 else (it.qtdFrutos.toIntOrNull() ?: 0) },
        mortas = coletadas.count { it.isInativa },
        comOcorrencia = coletadas.count {
            it.observacao.isNotBlank() || it.nfVassoura > 0 || it.nfPodridao > 0 || it.nvTotal > 0 || it.notaVirus > 0
        },
        cavalos = coletadas.count { it.situacaoEfetiva.equals("Cavalo", ignoreCase = true) },
        erradas = coletadas.count { it.situacaoEfetiva.equals("Errada", ignoreCase = true) }
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
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    "${stats.coletadas} de ${stats.total} plantas avaliadas",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = Color(0xFF1B5E20)
                )
                Text(
                    "${(stats.percentual * 100).toInt()}%",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 13.sp,
                    color = VerdeCacau
                )
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                IndicadorRota("${stats.frutos}", "frutos", VerdeCacau)
                IndicadorRota("%.1f".format(Locale.US, stats.mediaFrutos), "média/planta", Color(0xFF5D4037))
                IndicadorRota("${stats.pendentes}", "pendentes", Color(0xFFE65100))
                IndicadorRota("${stats.mortas}", "cavalo/morta", Color(0xFF795548))
                IndicadorRota("${stats.comOcorrencia}", "doentes", Color(0xFFC62828))
            }
        }
    }
}

@Composable
private fun IndicadorRota(valor: String, rotulo: String, cor: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(valor, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = cor)
        Text(rotulo, fontSize = 10.sp, color = Color.Gray)
    }
}

/**
 * Grade limpa de botões de Registro Rápido (1 toque salva e avança para a próxima planta):
 * - 🟢 Sem Frutos (0) (Normal viva com 0 frutos)
 * - ⚪ Não Coletável / Não Identificável
 * - 🟠 Cavalo (Porta-enxerto)
 * - 🔴 Morta (Planta morta)
 * - 🟣 Errada (Mistura varietal)
 */
@Composable
fun GradeBotoesRegistroRapido(
    habilitado: Boolean,
    onSemFrutos: () -> Unit,
    onCavalo: () -> Unit,
    onMorta: () -> Unit,
    onErrada: () -> Unit,
    onNaoColetavel: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            "Registro Rápido (1 toque salva e avança para próxima planta):",
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF5D4037)
        )
        Spacer(Modifier.height(6.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            BotaoRapidoSituacao(
                rotulo = "Sem Frutos (0)",
                corIndicador = Color(0xFF2E7D32),
                corFundo = Color(0xFFE8F5E9),
                corBorda = Color(0xFF81C784),
                corTexto = Color(0xFF1B5E20),
                habilitado = habilitado,
                onClick = onSemFrutos,
                modifier = Modifier.weight(1f)
            )
            if (onNaoColetavel != null) {
                BotaoRapidoSituacao(
                    rotulo = "Não Coletável",
                    corIndicador = Color(0xFF455A64),
                    corFundo = Color(0xFFECEFF1),
                    corBorda = Color(0xFF90A4AE),
                    corTexto = Color(0xFF263238),
                    habilitado = habilitado,
                    onClick = onNaoColetavel,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            BotaoRapidoSituacao(
                rotulo = "Cavalo",
                corIndicador = Color(0xFFE65100),
                corFundo = Color(0xFFFFF3E0),
                corBorda = Color(0xFFFFB74D),
                corTexto = Color(0xFFE65100),
                habilitado = habilitado,
                onClick = onCavalo,
                modifier = Modifier.weight(1f)
            )
            BotaoRapidoSituacao(
                rotulo = "Morta",
                corIndicador = Color(0xFFC62828),
                corFundo = Color(0xFFFFEBEE),
                corBorda = Color(0xFFE57373),
                corTexto = Color(0xFFC62828),
                habilitado = habilitado,
                onClick = onMorta,
                modifier = Modifier.weight(1f)
            )
            BotaoRapidoSituacao(
                rotulo = "Errada",
                corIndicador = Color(0xFF6A1B9A),
                corFundo = Color(0xFFF3E5F5),
                corBorda = Color(0xFFBA68C8),
                corTexto = Color(0xFF6A1B9A),
                habilitado = habilitado,
                onClick = onErrada,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun BotaoRapidoSituacao(
    rotulo: String,
    corIndicador: Color,
    corFundo: Color,
    corBorda: Color,
    corTexto: Color,
    habilitado: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        enabled = habilitado,
        modifier = modifier.height(46.dp),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.2.dp, if (habilitado) corBorda else Color.LightGray),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (habilitado) corFundo else Color(0xFFF5F5F5),
            contentColor = corTexto
        ),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Box(
            Modifier
                .size(10.dp)
                .background(if (habilitado) corIndicador else Color.Gray, CircleShape)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = rotulo,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

/**
 * Conteúdo limpo e direto do Modal de Ficha CEPLAC / Doenças (sem duplicação e sem acordeão interno):
 * 1. Cor do Fruto: 2 chips grandes lado a lado ([ Verde ] | [ Roxo ])
 * 2. Perdas e Sanidade (Contadores - 0 + limpos em Grid 2x2):
 *    - Frutos c/ Vassoura (NFVassoura)
 *    - Frutos c/ Podridão (NFPodridao)
 *    - Outras Perdas (NFOutrasPerdas)
 *    - Vassoura na Copa (NVTotal)
 * 3. Ocorrências Extras (Seletor simples em 1 linha): Nota Vírus (0, 1, 2, 4, 8, 16, 32, 64)
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ConteudoFichaCeplacLimpa(
    dados: DadosAgronomicosPlanta,
    onDadosChange: (DadosAgronomicosPlanta) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Cor do Fruto: 2 chips grandes lado a lado
        Column {
            Text(
                "Cor do Fruto",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF3E2723)
            )
            Spacer(Modifier.height(6.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                listOf("Verde", "Roxo").forEach { cor ->
                    val selecionado = dados.corFruto.equals(cor, ignoreCase = true)
                    val corFundoSel = if (cor == "Verde") Color(0xFFC8E6C9) else Color(0xFFE1BEE7)
                    val corTextoSel = if (cor == "Verde") Color(0xFF1B5E20) else Color(0xFF4A148C)
                    val corBordaSel = if (cor == "Verde") Color(0xFF2E7D32) else Color(0xFF7B1FA2)

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (selecionado) corFundoSel else Color(0xFFF5F5F5),
                        border = BorderStroke(
                            width = if (selecionado) 2.dp else 1.dp,
                            color = if (selecionado) corBordaSel else Color(0xFFD0D0D0)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                onDadosChange(dados.copy(corFruto = if (selecionado) "" else cor))
                            }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = cor,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (selecionado) corTextoSel else Color.DarkGray
                            )
                        }
                    }
                }
            }
        }

        // 2. Perdas e Sanidade (Grid 2x2 limpo)
        Column {
            Text(
                "Perdas e Sanidade",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF3E2723)
            )
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MiniContadorTrait(
                    rotulo = "Frutos c/ Vassoura",
                    valor = dados.nfVassoura,
                    cor = Color(0xFFC62828),
                    onChange = { onDadosChange(dados.copy(nfVassoura = it)) },
                    modifier = Modifier.weight(1f)
                )
                MiniContadorTrait(
                    rotulo = "Frutos c/ Podridão",
                    valor = dados.nfPodridao,
                    cor = Color(0xFFC62828),
                    onChange = { onDadosChange(dados.copy(nfPodridao = it)) },
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MiniContadorTrait(
                    rotulo = "Outras Perdas",
                    valor = dados.nfOutrasPerdas,
                    cor = Color(0xFFE65100),
                    onChange = { onDadosChange(dados.copy(nfOutrasPerdas = it)) },
                    modifier = Modifier.weight(1f)
                )
                MiniContadorTrait(
                    rotulo = "Vassoura na Copa",
                    valor = dados.nvTotal,
                    cor = Color(0xFF6A1B9A),
                    onChange = { onDadosChange(dados.copy(nvTotal = it)) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // 3. Nota Vírus (Seletor simples em 1 linha)
        Column {
            Text(
                "Nota Vírus (0 a 64)",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF3E2723)
            )
            Spacer(Modifier.height(4.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                NOTAS_VIRUS_CEPLAC.forEach { nota ->
                    FilterChip(
                        selected = dados.notaVirus == nota,
                        onClick = { onDadosChange(dados.copy(notaVirus = nota)) },
                        label = { Text("$nota", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                    )
                }
            }
        }
    }
}

@Composable
fun MiniContadorTrait(
    rotulo: String,
    valor: Int,
    cor: Color,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = Color.White,
        border = BorderStroke(1.dp, if (valor > 0) cor else Color(0xFFE0E0E0))
    ) {
        Column(
            Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                rotulo,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.DarkGray,
                maxLines = 1
            )
            Spacer(Modifier.height(2.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { if (valor > 0) onChange(valor - 1) },
                    enabled = valor > 0,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.RemoveCircleOutline,
                        contentDescription = "Diminuir $rotulo",
                        tint = if (valor > 0) cor else Color.LightGray,
                        modifier = Modifier.size(26.dp)
                    )
                }
                Text(
                    "$valor",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (valor > 0) cor else Color.Gray
                )
                IconButton(
                    onClick = { onChange(valor + 1) },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.AddCircleOutline,
                        contentDescription = "Aumentar $rotulo",
                        tint = cor,
                        modifier = Modifier.size(26.dp)
                    )
                }
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
                    (busca.isBlank() || "${p.clone} ${p.parcela} ${p.planta} ${p.bloco} ${p.ordem} ${p.plantIdOriginal}".contains(busca.trim(), ignoreCase = true))
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
                    placeholder = { Text("Buscar clone, parcela, bloco ou ordem") },
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
                    LegendaMapa(VerdeClaro, "normal")
                    LegendaMapa(Color(0xFFFFE0B2), "pendente")
                    LegendaMapa(Color(0xFFFFCDD2), "cavalo/morta/errada")
                }
                if (grupos.isEmpty()) {
                    Text("Nenhuma planta encontrada.", color = Color.Gray, modifier = Modifier.padding(vertical = 16.dp))
                }
                LazyColumn(Modifier.heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(grupos, key = { "${it.key.first}|${it.key.second}" }) { grupo ->
                        val feitas = grupo.value.count { it.value.coletado }
                        val blocoGrupo = grupo.value.firstOrNull()?.value?.bloco.orEmpty()
                        val prefixoBloco = if (blocoGrupo.isNotBlank()) "Bloco $blocoGrupo · " else ""
                        Column {
                            Text(
                                "${prefixoBloco}Parcela ${grupo.key.first} · ${grupo.key.second}  ($feitas/${grupo.value.size})",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF4E342E)
                            )
                            Spacer(Modifier.height(4.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                grupo.value.forEach { (idx, p) ->
                                    val inativa = p.coletado && p.isInativa
                                    val fundo = when {
                                        inativa -> Color(0xFFFFCDD2)
                                        p.coletado -> VerdeClaro
                                        else -> Color(0xFFFFE0B2)
                                    }
                                    Box(
                                        modifier = Modifier
                                            .size(width = 48.dp, height = 42.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(fundo)
                                            .then(
                                                if (idx == indiceAtual) Modifier.border(2.dp, VerdeCacau, RoundedCornerShape(10.dp))
                                                else Modifier
                                            )
                                            .clickable { onSelecionar(idx) },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("${p.planta}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                            if (p.coletado) {
                                                Text(
                                                    if (inativa) p.situacaoEfetiva.take(3).uppercase() else "${p.qtdFrutos}f",
                                                    fontSize = 8.sp,
                                                    color = if (inativa) Color(0xFFB71C1C) else VerdeCacau
                                                )
                                            }
                                        }
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
