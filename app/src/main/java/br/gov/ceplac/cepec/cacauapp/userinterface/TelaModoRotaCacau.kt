package br.gov.ceplac.cepec.cacauapp.userinterface

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.UploadFile
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.gov.ceplac.cepec.cacauapp.data.ModelConfig
import br.gov.ceplac.cepec.cacauapp.data.RotaColeta
import br.gov.ceplac.cepec.cacauapp.network.CacauSyncClient
import br.gov.ceplac.cepec.cacauapp.ui.theme.CacauBordaSuave
import br.gov.ceplac.cepec.cacauapp.ui.theme.CacauDourado
import br.gov.ceplac.cepec.cacauapp.ui.theme.CacauFundoSolar
import br.gov.ceplac.cepec.cacauapp.ui.theme.CacauTextoPrincipal
import br.gov.ceplac.cepec.cacauapp.ui.theme.CacauTextoSecundario
import br.gov.ceplac.cepec.cacauapp.ui.theme.CacauVerdeBotanico
import br.gov.ceplac.cepec.cacauapp.ui.theme.CacauVerdeEscuro
import br.gov.ceplac.cepec.cacauapp.userinterface.util.ImportadorPlanilhaUniversal
import br.gov.ceplac.cepec.cacauapp.userinterface.util.PersistenciaLocalRegistros
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

@Composable
fun TelaModoRotaCacau(
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
    val rotaFiltrada = remember(rota, filtroAba, busca) {
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
