package br.gov.ceplac.cepec.cacauapp.userinterface

import android.net.Uri
import android.widget.Toast
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.RateReview
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import br.gov.ceplac.cepec.cacauapp.data.ConfigRendimentoCacau
import br.gov.ceplac.cepec.cacauapp.data.RankingCloneCacau
import br.gov.ceplac.cepec.cacauapp.data.RegistroCacau
import br.gov.ceplac.cepec.cacauapp.data.calcularEstimativaPorContagensCacau
import br.gov.ceplac.cepec.cacauapp.data.calcularRankingClonesCacau
import br.gov.ceplac.cepec.cacauapp.data.formatarEstimativaCurta
import br.gov.ceplac.cepec.cacauapp.data.gerarResumoTextoCacau
import br.gov.ceplac.cepec.cacauapp.userinterface.util.exportarCSVCEPEC_Personalizado
import br.gov.ceplac.cepec.cacauapp.ui.theme.CacauBordaSuave
import br.gov.ceplac.cepec.cacauapp.ui.theme.CacauDourado
import br.gov.ceplac.cepec.cacauapp.ui.theme.CacauDouradoClaro
import br.gov.ceplac.cepec.cacauapp.ui.theme.CacauFundoSolar
import br.gov.ceplac.cepec.cacauapp.ui.theme.CacauPerigo
import br.gov.ceplac.cepec.cacauapp.ui.theme.CacauTextoPrincipal
import br.gov.ceplac.cepec.cacauapp.ui.theme.CacauTextoSecundario
import br.gov.ceplac.cepec.cacauapp.ui.theme.CacauVerdeBotanico
import br.gov.ceplac.cepec.cacauapp.ui.theme.CacauVerdeClaro
import br.gov.ceplac.cepec.cacauapp.ui.theme.CacauVerdeEscuro
import br.gov.ceplac.cepec.cacauapp.userinterface.util.PersistenciaLocalRegistros
import br.gov.ceplac.cepec.cacauapp.userinterface.util.compartilharArquivoViaAndroid
import br.gov.ceplac.cepec.cacauapp.userinterface.util.compartilharTextoViaAndroid
import br.gov.ceplac.cepec.cacauapp.userinterface.util.exportarPacoteZipCacau
import br.gov.ceplac.cepec.cacauapp.userinterface.util.gerarArquivoCsvParaCompartilhar
import br.gov.ceplac.cepec.cacauapp.userinterface.util.gerarCsvRankingClonesCacau
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.Locale

@Composable
fun TelaDadosERanking(
    configEnsaio: PersistenciaLocalRegistros.ConfigEnsaioLocal,
    registros: List<RegistroCacau>,
    configRendimento: ConfigRendimentoCacau,
    sincronizandoPc: Boolean,
    onSincronizarPc: () -> Unit,
    onAbrirConfigServidor: () -> Unit,
    onAbrirConfigRendimento: () -> Unit,
    onEditarRegistro: (indexOriginal: Int, registroAtualizado: RegistroCacau) -> Unit,
    onRevisarFotoRegistro: (indexOriginal: Int, registro: RegistroCacau) -> Unit,
    onExcluirRegistro: (indexOriginal: Int) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var subAba by remember { mutableIntStateOf(0) }
    var buscaTexto by remember { mutableStateOf("") }
    var filtroStatus by remember { mutableIntStateOf(0) } // 0=Todos, 1=Com Doença, 2=Não Coletável
    var ordenacaoRanking by remember { mutableIntStateOf(1) } // 0=@/ha, 1=Sadios/pl (CEPEC), 2=Menor Infec
    var gerandoPacote by remember { mutableStateOf(false) }

    var indiceEditando by remember { mutableStateOf<Int?>(null) }
    var indiceExcluindo by remember { mutableStateOf<Int?>(null) }

    val registrosFiltrados = remember(registros, buscaTexto, filtroStatus) {
        val q = buscaTexto.trim().lowercase(Locale.ROOT)
        registros.mapIndexed { idx, reg -> idx to reg }.filter { (_, reg) ->
            val bateBusca = q.isEmpty() ||
                reg.plantId.lowercase(Locale.ROOT).contains(q) ||
                reg.diagnostico.lowercase(Locale.ROOT).contains(q) ||
                reg.data.lowercase(Locale.ROOT).contains(q)
            val naoColetavel = reg.diagnostico.contains("coletável", ignoreCase = true) ||
                reg.diagnostico.contains("coletavel", ignoreCase = true)
            val bateFiltro = when (filtroStatus) {
                1 -> (reg.vassoura + reg.podridao) > 0
                2 -> naoColetavel
                else -> true
            }
            bateBusca && bateFiltro
        }.reversed()
    }

    val rankingClones = remember(registros, configRendimento, buscaTexto, ordenacaoRanking) {
        val base = calcularRankingClonesCacau(registros, configRendimento)
        val q = buscaTexto.trim().lowercase(Locale.ROOT)
        val filtrado = if (q.isEmpty()) base else base.filter { it.codigoClone.lowercase(Locale.ROOT).contains(q) }
        when (ordenacaoRanking) {
            1 -> filtrado.sortedByDescending { it.mediaSadiosPlanta }
            2 -> filtrado.sortedBy { it.taxaInfeccaoPercentual }
            else -> filtrado
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CacauFundoSolar)
    ) {
        // Cabeçalho com Sincronização 1-clique + abas internas
        Surface(color = Color.White, shadowElevation = 2.dp) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Dados & Ranking • ${configEnsaio.codigoEnsaio}",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 18.sp,
                            color = CacauVerdeEscuro,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${registros.size} registro(s) • ${registros.sumOf { it.totalFrutos }} frutos totais",
                            fontSize = 12.sp,
                            color = CacauTextoSecundario
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(onClick = onAbrirConfigServidor, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.Wifi, contentDescription = "Configurar servidor CacauSync", tint = CacauTextoSecundario)
                        }
                        Button(
                            onClick = onSincronizarPc,
                            enabled = !sincronizandoPc && registros.isNotEmpty(),
                            colors = ButtonDefaults.buttonColors(containerColor = CacauVerdeEscuro),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            if (sincronizandoPc) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), color = Color.White, strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Sync PC", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                TabRow(selectedTabIndex = subAba, containerColor = Color(0xFFF3F6F3), contentColor = CacauVerdeEscuro) {
                    Tab(
                        selected = subAba == 0,
                        onClick = { subAba = 0 },
                        text = { Text("Registros (${registros.size})", fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                    )
                    Tab(
                        selected = subAba == 1,
                        onClick = { subAba = 1 },
                        text = { Text("Ranking Clones (@/ha)", fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                    )
                }

                OutlinedTextField(
                    value = buscaTexto,
                    onValueChange = { buscaTexto = it },
                    placeholder = { Text(if (subAba == 0) "Buscar clone, parcela, planta ou nota..." else "Filtrar clone no ranking...", fontSize = 12.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        if (subAba == 0) {
            // Aba Registros
            Column(modifier = Modifier.fillMaxSize()) {
                // Filtros + Exportação rápida
                Column(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        listOf(0 to "Todos", 1 to "Com Doença", 2 to "Não Colet.").forEach { (id, label) ->
                            FilterChip(
                                selected = filtroStatus == id,
                                onClick = { filtroStatus = id },
                                label = { Text(label, fontSize = 11.sp) }
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = {
                                if (registros.isEmpty()) {
                                    Toast.makeText(context, "Sem registros para exportar.", Toast.LENGTH_SHORT).show()
                                } else {
                                    scope.launch {
                                        withContext(Dispatchers.IO) {
                                            exportarCSVCEPEC_Personalizado(
                                                context,
                                                registros,
                                                configEnsaio.labelClone,
                                                configEnsaio.labelParcela,
                                                configEnsaio.labelPlanta
                                            )
                                        }
                                    }
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.weight(1f).height(34.dp)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("CSV", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = {
                                if (registros.isEmpty()) {
                                    Toast.makeText(context, "Sem registros para compartilhar.", Toast.LENGTH_SHORT).show()
                                } else {
                                    scope.launch {
                                        val arq = withContext(Dispatchers.IO) {
                                            gerarArquivoCsvParaCompartilhar(
                                                context,
                                                registros,
                                                configEnsaio.codigoEnsaio,
                                                configEnsaio.labelClone,
                                                configEnsaio.labelParcela,
                                                configEnsaio.labelPlanta
                                            )
                                        }
                                        if (arq != null) {
                                            compartilharArquivoViaAndroid(context, arq, "text/csv", "Compartilhar CSV CEPEC")
                                        }
                                    }
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.weight(1f).height(34.dp)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Env. CSV", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = {
                                if (registros.isEmpty()) {
                                    Toast.makeText(context, "Sem registros para resumo.", Toast.LENGTH_SHORT).show()
                                } else {
                                    val texto = gerarResumoTextoCacau(configEnsaio.codigoEnsaio, registros, configRendimento)
                                    compartilharTextoViaAndroid(context, texto, "Enviar Resumo CEPEC")
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.weight(1f).height(34.dp)
                        ) {
                            Text("WhatsApp", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = CacauVerdeBotanico)
                        }

                        OutlinedButton(
                            enabled = !gerandoPacote,
                            onClick = {
                                if (registros.isEmpty()) {
                                    Toast.makeText(context, "Sem registros para pacote ZIP.", Toast.LENGTH_SHORT).show()
                                } else {
                                    gerandoPacote = true
                                    scope.launch {
                                        val zip = withContext(Dispatchers.IO) {
                                            exportarPacoteZipCacau(
                                                context,
                                                registros,
                                                configEnsaio.codigoEnsaio,
                                                configRendimento,
                                                configEnsaio.labelClone,
                                                configEnsaio.labelParcela,
                                                configEnsaio.labelPlanta
                                            )
                                        }
                                        gerandoPacote = false
                                        if (zip != null) {
                                            compartilharArquivoViaAndroid(context, zip, "application/zip", "Compartilhar Pacote ZIP CEPEC")
                                        }
                                    }
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.weight(0.95f).height(34.dp)
                        ) {
                            Icon(Icons.Default.FolderZip, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(3.dp))
                            Text("ZIP", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                if (registrosFiltrados.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = "Nenhum registro encontrado neste ensaio.",
                            color = CacauTextoSecundario,
                            fontSize = 14.sp
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(registrosFiltrados, key = { (idx, r) -> "${idx}_${r.plantId}_${r.data}" }) { (origIdx, reg) ->
                            RegistroCardCacau(
                                reg = reg,
                                configRendimento = configRendimento,
                                onEditar = { indiceEditando = origIdx },
                                onRevisarFoto = { onRevisarFotoRegistro(origIdx, reg) },
                                onExcluir = { indiceExcluindo = origIdx }
                            )
                        }
                    }
                }
            }
        } else {
            // Aba Ranking Clones @/ha
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    FaixaAcumuladoRendimentoCacau(
                        registros = registros,
                        configRendimento = configRendimento,
                        onAbrirConfigRendimento = onAbrirConfigRendimento
                    )
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(0 to "@/ha", 1 to "Sadios/pl", 2 to "Menor Infec.").forEach { (id, lbl) ->
                                FilterChip(
                                    selected = ordenacaoRanking == id,
                                    onClick = { ordenacaoRanking = id },
                                    label = { Text(lbl, fontSize = 11.sp) }
                                )
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            IconButton(onClick = onAbrirConfigRendimento, modifier = Modifier.size(34.dp)) {
                                Icon(Icons.Default.Tune, contentDescription = "Configurar Índice de Fruto", tint = CacauVerdeEscuro)
                            }
                            IconButton(
                                onClick = {
                                    val csv = gerarCsvRankingClonesCacau(registros, configRendimento)
                                    if (csv.isNotBlank()) {
                                        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
                                        val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
                                        val f = File(dir, "Ranking_${configEnsaio.codigoEnsaio}.csv")
                                        f.writeBytes(bom + csv.toByteArray(StandardCharsets.UTF_8))
                                        compartilharArquivoViaAndroid(context, f, "text/csv", "Compartilhar Ranking Clones")
                                    }
                                },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = "Compartilhar ranking CSV", tint = CacauVerdeEscuro)
                            }
                        }
                    }
                }

                if (rankingClones.isEmpty()) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(28.dp), contentAlignment = Alignment.Center) {
                            Text("Registre plantas no campo para gerar o ranking de clones em @/ha.", color = CacauTextoSecundario, fontSize = 13.sp)
                        }
                    }
                } else {
                    itemsIndexed(rankingClones, key = { _, item -> item.codigoClone }) { idx, clone ->
                        RankingCloneCard(posicao = idx + 1, item = clone)
                    }
                }
            }
        }
    }

    val editIdx = indiceEditando
    if (editIdx != null && editIdx in registros.indices) {
        DialogEditarRegistroCacau(
            reg = registros[editIdx],
            onDismiss = { indiceEditando = null },
            onSalvar = { atualizado ->
                onEditarRegistro(editIdx, atualizado)
                indiceEditando = null
            }
        )
    }

    val delIdx = indiceExcluindo
    if (delIdx != null && delIdx in registros.indices) {
        AlertDialog(
            onDismissRequest = { indiceExcluindo = null },
            title = { Text("Excluir coleta?", fontWeight = FontWeight.Bold, color = CacauPerigo) },
            text = { Text("Deseja remover permanentemente o registro ${registros[delIdx].plantId}?") },
            confirmButton = {
                Button(
                    onClick = {
                        onExcluirRegistro(delIdx)
                        indiceExcluindo = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CacauPerigo)
                ) { Text("Excluir") }
            },
            dismissButton = {
                TextButton(onClick = { indiceExcluindo = null }) { Text("Cancelar") }
            }
        )
    }
}

@Composable
private fun RegistroCardCacau(
    reg: RegistroCacau,
    configRendimento: ConfigRendimentoCacau,
    onEditar: () -> Unit,
    onRevisarFoto: () -> Unit,
    onExcluir: () -> Unit
) {
    val doentes = reg.vassoura + reg.podridao
    val estimativa = remember(reg, configRendimento) {
        calcularEstimativaPorContagensCacau(reg.sadios, doentes, reg.totalFrutos, configRendimento)
    }
    val temFoto = reg.uri != Uri.EMPTY

    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, CacauBordaSuave),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = Color(0xFFECF2ED),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .size(64.dp)
                        .clickable(enabled = temFoto) { onRevisarFoto() }
                ) {
                    if (temFoto) {
                        AsyncImage(
                            model = reg.uri,
                            contentDescription = reg.plantId,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp))
                        )
                    } else {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Image, contentDescription = null, tint = CacauTextoSecundario)
                        }
                    }
                }

                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = reg.plantId,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp,
                            color = CacauTextoPrincipal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Surface(color = CacauDouradoClaro, shape = RoundedCornerShape(6.dp)) {
                            Text(
                                text = formatarEstimativaCurta(estimativa.arrobas, "@"),
                                color = Color(0xFF6D4C00),
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Text(
                        text = "${reg.data} • ${reg.diagnostico}",
                        fontSize = 11.sp,
                        color = CacauTextoSecundario,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        BadgeEstadoMini("Tot ${reg.totalFrutos}", CacauVerdeEscuro)
                        BadgeEstadoMini("Sad ${reg.sadios}", EstadoFruto.SADIO.cor)
                        if (reg.vassoura > 0) BadgeEstadoMini("Vas ${reg.vassoura}", EstadoFruto.VASSOURA.cor)
                        if (reg.podridao > 0) BadgeEstadoMini("Pod ${reg.podridao}", EstadoFruto.PODRIDAO.cor)
                        if (reg.naoAvaliavel > 0) BadgeEstadoMini("N.Av ${reg.naoAvaliavel}", EstadoFruto.NAO_AVALIAVEL.cor)
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (temFoto) {
                    TextButton(onClick = onRevisarFoto, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)) {
                        Icon(Icons.Default.RateReview, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Revisar Foto", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
                TextButton(onClick = onEditar, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Editar", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                TextButton(
                    onClick = onExcluir,
                    colors = ButtonDefaults.textButtonColors(contentColor = CacauPerigo),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Excluir", fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun BadgeEstadoMini(texto: String, cor: Color) {
    Surface(color = cor.copy(alpha = 0.12f), shape = RoundedCornerShape(6.dp)) {
        Text(
            text = texto,
            color = cor,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun RankingCloneCard(posicao: Int, item: RankingCloneCacau) {
    val corMedalha = when (posicao) {
        1 -> Color(0xFFC98A00)
        2 -> Color(0xFF546E7A)
        3 -> Color(0xFF8D6E63)
        else -> CacauVerdeEscuro
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, if (posicao <= 3) corMedalha.copy(alpha = 0.4f) else CacauBordaSuave),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(color = corMedalha.copy(alpha = 0.14f), shape = RoundedCornerShape(8.dp)) {
                        Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (posicao <= 3) {
                                Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = corMedalha, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(3.dp))
                            }
                            Text("#$posicao", color = corMedalha, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                        }
                    }
                    Column {
                        Text(item.codigoClone, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = CacauTextoPrincipal)
                        Text(
                            "${item.plantasColetaveis}/${item.totalRegistros} pl. vivas (${item.taxaSobrevivenciaPercentual.toInt()}%)",
                            fontSize = 11.sp,
                            color = CacauTextoSecundario
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        formatarEstimativaCurta(item.arrobasHa, "@/ha"),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp,
                        color = CacauVerdeEscuro
                    )
                    Text(
                        formatarEstimativaCurta(item.kgHa, "kg/ha"),
                        fontSize = 11.sp,
                        color = CacauTextoSecundario
                    )
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Média sadios/pl: ${String.format(Locale.US, "%.1f", item.mediaSadiosPlanta).replace('.', ',')}", fontSize = 11.sp, color = CacauTextoPrincipal)
                Text("Infecção: ${String.format(Locale.US, "%.1f", item.taxaInfeccaoPercentual).replace('.', ',')}%", fontSize = 11.sp, color = if (item.taxaInfeccaoPercentual >= 20.0) CacauPerigo else CacauTextoSecundario)
                if (item.perdaArrobasHa > 0.0) {
                    Text("Perda: -${formatarEstimativaCurta(item.perdaArrobasHa, "@/ha")}", fontSize = 11.sp, color = CacauPerigo, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun DialogEditarRegistroCacau(
    reg: RegistroCacau,
    onDismiss: () -> Unit,
    onSalvar: (RegistroCacau) -> Unit
) {
    val matchId = remember(reg.plantId) {
        Regex("""^(.+?)_P([^_]+)_Pl(\d+)(?:_(Foto\d+))?$""", RegexOption.IGNORE_CASE).matchEntire(reg.plantId.trim())
    }
    val cloneIni = matchId?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() }
        ?: reg.plantId.substringBefore("_P").ifBlank { reg.plantId.substringBefore("_") }
    val parcIni = reg.parcelaManual.ifBlank { matchId?.groupValues?.getOrNull(2) ?: "1" }
    val plantaIni = if (reg.numPlanta > 0) reg.numPlanta.toString() else (matchId?.groupValues?.getOrNull(3) ?: "1")
    val sufixoFoto = matchId?.groupValues?.getOrNull(4).orEmpty()

    var clone by remember { mutableStateOf(cloneIni) }
    var parcela by remember { mutableStateOf(parcIni) }
    var planta by remember { mutableStateOf(plantaIni) }
    var sadios by remember { mutableStateOf(reg.sadios.toString()) }
    var vassoura by remember { mutableStateOf(reg.vassoura.toString()) }
    var podridao by remember { mutableStateOf(reg.podridao.toString()) }
    var naoAv by remember { mutableStateOf(reg.naoAvaliavel.toString()) }
    var naoColetavel by remember {
        mutableStateOf(
            reg.diagnostico.contains("coletável", ignoreCase = true) ||
                reg.diagnostico.contains("coletavel", ignoreCase = true)
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Editar Registro da Planta", fontWeight = FontWeight.Bold, color = CacauVerdeEscuro) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = clone, onValueChange = { clone = it }, label = { Text("Clone / Genótipo") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = parcela, onValueChange = { parcela = it }, label = { Text("Parcela") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(
                        value = planta,
                        onValueChange = { planta = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Planta") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = sadios,
                        onValueChange = { sadios = it.filter { c -> c.isDigit() } },
                        enabled = !naoColetavel,
                        label = { Text("Sadios") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = vassoura,
                        onValueChange = { vassoura = it.filter { c -> c.isDigit() } },
                        enabled = !naoColetavel,
                        label = { Text("Vassoura") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = podridao,
                        onValueChange = { podridao = it.filter { c -> c.isDigit() } },
                        enabled = !naoColetavel,
                        label = { Text("Podridão") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = naoAv,
                        onValueChange = { naoAv = it.filter { c -> c.isDigit() } },
                        enabled = !naoColetavel,
                        label = { Text("Não Aval.") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                FilterChip(
                    selected = naoColetavel,
                    onClick = { naoColetavel = !naoColetavel },
                    label = { Text("Planta Não Coletável (Ausente / Morta)") }
                )
                Text(
                    "Se você alterar as quantidades manualmente aqui, as bounding boxes antigas serão limpas para manter a concordância; para editar fruto a fruto, use 'Revisar Foto'.",
                    fontSize = 11.sp,
                    color = CacauTextoSecundario
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val s = if (naoColetavel) 0 else (sadios.toIntOrNull() ?: 0)
                    val v = if (naoColetavel) 0 else (vassoura.toIntOrNull() ?: 0)
                    val p = if (naoColetavel) 0 else (podridao.toIntOrNull() ?: 0)
                    val n = if (naoColetavel) 0 else (naoAv.toIntOrNull() ?: 0)
                    val total = s + v + p + n
                    val parcelLimpa = parcela.trim().ifBlank { "1" }
                    val plantaNum = planta.toIntOrNull()?.coerceAtLeast(1) ?: 1
                    val baseId = "${clone.trim().ifBlank { "CACAU" }}_P${parcelLimpa}_Pl$plantaNum"
                    val novoId = if (sufixoFoto.isNotBlank()) "${baseId}_$sufixoFoto" else baseId
                    val alterouContagens = s != reg.sadios || v != reg.vassoura || p != reg.podridao || n != reg.naoAvaliavel || naoColetavel
                    onSalvar(
                        reg.copy(
                            plantId = novoId,
                            parcelaManual = parcelLimpa,
                            numPlanta = plantaNum,
                            totalFrutos = total,
                            sadios = s,
                            vassoura = v,
                            podridao = p,
                            naoAvaliavel = n,
                            diagnostico = if (naoColetavel) "Não Coletável" else total.toString(),
                            caixas = if (alterouContagens || naoColetavel) emptyList() else reg.caixas,
                            statusSync = "PENDENTE"
                        )
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = CacauVerdeEscuro)
            ) {
                Text("Salvar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}
