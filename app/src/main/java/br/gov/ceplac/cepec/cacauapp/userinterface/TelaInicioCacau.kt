package br.gov.ceplac.cepec.cacauapp.userinterface

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.OfflinePin
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.gov.ceplac.cepec.cacauapp.data.ConfigRendimentoCacau
import br.gov.ceplac.cepec.cacauapp.data.ModelConfig
import br.gov.ceplac.cepec.cacauapp.data.RegistroCacau
import br.gov.ceplac.cepec.cacauapp.data.RotaColeta
import br.gov.ceplac.cepec.cacauapp.ui.theme.CacauBordaSuave
import br.gov.ceplac.cepec.cacauapp.ui.theme.CacauDourado
import br.gov.ceplac.cepec.cacauapp.ui.theme.CacauDouradoClaro
import br.gov.ceplac.cepec.cacauapp.ui.theme.CacauFundoSolar
import br.gov.ceplac.cepec.cacauapp.ui.theme.CacauTextoPrincipal
import br.gov.ceplac.cepec.cacauapp.ui.theme.CacauTextoSecundario
import br.gov.ceplac.cepec.cacauapp.ui.theme.CacauVerdeBotanico
import br.gov.ceplac.cepec.cacauapp.ui.theme.CacauVerdeClaro
import br.gov.ceplac.cepec.cacauapp.ui.theme.CacauVerdeEscuro
import br.gov.ceplac.cepec.cacauapp.userinterface.util.PersistenciaLocalRegistros

@Composable
fun TelaInicioCacau(
    configEnsaio: PersistenciaLocalRegistros.ConfigEnsaioLocal,
    registros: List<RegistroCacau>,
    rota: List<RotaColeta>,
    modelConfig: ModelConfig,
    configRendimento: ConfigRendimentoCacau,
    sincronizandoPc: Boolean,
    servidorConfigurado: Boolean,
    onAtualizarCodigoEnsaio: (String) -> Unit,
    onCriarNovoEnsaio: (codigo: String, manterRota: Boolean) -> Unit,
    onAlternarEnsaio: (ensaioId: String) -> Unit,
    onAbrirModoRota: () -> Unit,
    onAbrirColetaLivre: () -> Unit,
    onAbrirDadosERanking: () -> Unit,
    onAbrirConfigIA: () -> Unit,
    onTestarCameraIA: () -> Unit,
    onSincronizarPc: () -> Unit,
    onAbrirConfigServidor: () -> Unit,
    onAbrirConfigRendimento: () -> Unit
) {
    val context = LocalContext.current
    var showDialogEnsaios by remember { mutableStateOf(false) }
    var showDialogOffline by remember { mutableStateOf(false) }
    var showDialogTutorial by remember { mutableStateOf(!PersistenciaLocalRegistros.isTutorialVisto(context)) }

    val totalRota = rota.size
    val coletadosRota = remember(rota, registros) {
        if (rota.isEmpty()) 0 else {
            val idsSalvos = registros.map { it.plantId.uppercase() }.toSet()
            rota.count { item ->
                item.coletado || idsSalvos.contains("${item.clone}_P${item.parcela}_Pl${item.planta}".uppercase())
            }
        }
    }
    val progressoRota = if (totalRota > 0) (coletadosRota.toFloat() / totalRota.toFloat()).coerceIn(0f, 1f) else 0f

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CacauFundoSolar)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Hero Card Verde Botânico
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFF103814), CacauVerdeEscuro, CacauVerdeBotanico)
                    )
                )
                .padding(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "CEPEC / CEPLAC • CACAU",
                            color = Color(0xFFA5D6A7),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { showDialogEnsaios = true }
                        ) {
                            Text(
                                text = "Ensaio ${configEnsaio.codigoEnsaio}",
                                color = Color.White,
                                fontSize = 21.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.width(6.dp))
                            Icon(Icons.Default.Edit, contentDescription = "Trocar ou editar ensaio", tint = Color(0xFFA5D6A7), modifier = Modifier.size(16.dp))
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Surface(
                            color = Color.White.copy(alpha = 0.16f),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.clickable { showDialogOffline = true }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.OfflinePin, contentDescription = null, tint = Color(0xFFA5D6A7), modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("100% Offline", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }

                        IconButton(
                            onClick = { showDialogTutorial = true },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = "Guia de campo", tint = Color.White)
                        }
                    }
                }

                // Progresso da rota ou resumo rápido
                if (totalRota > 0) {
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Progresso da Rota",
                                color = Color.White.copy(alpha = 0.9f),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "$coletadosRota / $totalRota plantas (${(progressoRota * 100).toInt()}%)",
                                color = Color(0xFFFFE082),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        LinearProgressIndicator(
                            progress = { progressoRota },
                            color = Color(0xFFA5D6A7),
                            trackColor = Color.White.copy(alpha = 0.22f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                        )
                    }
                } else {
                    Text(
                        text = "${registros.size} planta(s) registrada(s) localmente neste ensaio.",
                        color = Color.White.copy(alpha = 0.88f),
                        fontSize = 12.sp
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        color = Color.White.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.clickable { showDialogEnsaios = true }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.SwapHoriz, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(5.dp))
                            Text("Trocar / Novo Ensaio", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

        // Card de Sincronização rápida com o CacauSync Desktop
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (registros.isNotEmpty()) Color(0xFFFFFBF2) else Color.White
            ),
            border = BorderStroke(
                1.dp,
                if (registros.isNotEmpty()) CacauDourado.copy(alpha = 0.5f) else CacauBordaSuave
            ),
            shape = RoundedCornerShape(14.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(
                        if (registros.isEmpty()) Icons.Default.CloudDone else Icons.Default.CloudUpload,
                        contentDescription = null,
                        tint = if (registros.isEmpty()) CacauVerdeBotanico else CacauDourado,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            text = if (registros.isEmpty()) "Nenhuma coleta pendente" else "${registros.size} coleta(s) prontas p/ PC",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = CacauTextoPrincipal
                        )
                        Text(
                            text = if (servidorConfigurado) "CacauSync configurado (Wi-Fi/USB)" else "Toque na engrenagem p/ IP do PC",
                            fontSize = 11.sp,
                            color = CacauTextoSecundario,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = onAbrirConfigServidor, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Wifi, contentDescription = "Configurar IP CacauSync", tint = CacauTextoSecundario)
                    }
                    Button(
                        onClick = onSincronizarPc,
                        enabled = !sincronizandoPc && registros.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(containerColor = CacauVerdeEscuro),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        if (sincronizandoPc) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                        } else {
                            Text("Enviar PC", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Estimativa acumulada em @ e kg
        FaixaAcumuladoRendimentoCacau(
            registros = registros,
            configRendimento = configRendimento,
            onAbrirConfigRendimento = onAbrirConfigRendimento
        )

        // 2 Botões gigantes de Coleta em Campo
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            AcaoCampoCard(
                titulo = "Modo Rota",
                subtitulo = if (totalRota > 0) "$coletadosRota/$totalRota na planilha" else "Importe .xlsx ou puxe PC",
                icone = Icons.Default.Route,
                destaque = CacauVerdeEscuro,
                fundo = CacauVerdeClaro,
                onClick = onAbrirModoRota,
                modifier = Modifier.weight(1f)
            )
            AcaoCampoCard(
                titulo = "Coleta Livre",
                subtitulo = "Clone, Parcela & Planta ±",
                icone = Icons.Default.CameraAlt,
                destaque = Color(0xFF6D4C00),
                fundo = CacauDouradoClaro,
                onClick = onAbrirColetaLivre,
                modifier = Modifier.weight(1f)
            )
        }

        // Card dos 2 Modelos Oficiais de Cacau CEPEC
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, CacauBordaSuave),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = CacauVerdeEscuro, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("IA Cacau CEPEC (2 Modelos)", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = CacauTextoPrincipal)
                            Text(modelConfig.nomeExibicao, fontSize = 12.sp, color = CacauVerdeBotanico, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Surface(color = CacauVerdeClaro, shape = RoundedCornerShape(8.dp)) {
                        Text(
                            text = modelConfig.modoEfetivo.rotuloCurto,
                            color = CacauVerdeEscuro,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Text(
                    text = "Contagem rápida (detector.tflite • ${(modelConfig.threshold * 100).toInt()}%) + Diagnóstico fitossanitário 4 classes (detector_doenca.tflite • ${(modelConfig.thresholdDoenca * 100).toInt()}%).",
                    fontSize = 12.sp,
                    color = CacauTextoSecundario
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = onAbrirConfigIA,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Configurar IA", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = onTestarCameraIA,
                        colors = ButtonDefaults.buttonColors(containerColor = CacauVerdeBotanico),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Testar Câmera", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Atalho para Dados & Ranking @/ha
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, CacauBordaSuave),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onAbrirDadosERanking() }
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Analytics, contentDescription = null, tint = CacauVerdeEscuro, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("Dados, Ranking de Clones & Exportação", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = CacauTextoPrincipal)
                        Text("Revise fotos, ranking @/ha, resumos WhatsApp e pacotes ZIP", fontSize = 12.sp, color = CacauTextoSecundario)
                    }
                }
                Icon(Icons.AutoMirrored.Filled.ListAlt, contentDescription = null, tint = CacauVerdeBotanico)
            }
        }
    }

    if (showDialogEnsaios) {
        DialogGerenciarEnsaios(
            configAtual = configEnsaio,
            onDismiss = { showDialogEnsaios = false },
            onAtualizarCodigo = { novo ->
                onAtualizarCodigoEnsaio(novo)
                showDialogEnsaios = false
            },
            onCriarNovo = { cod, manter ->
                onCriarNovoEnsaio(cod, manter)
                showDialogEnsaios = false
            },
            onAlternar = { id ->
                onAlternarEnsaio(id)
                showDialogEnsaios = false
            }
        )
    }

    if (showDialogOffline) {
        AlertDialog(
            onDismissRequest = { showDialogOffline = false },
            title = { Text("Operação 100% Offline", fontWeight = FontWeight.Bold, color = CacauVerdeEscuro) },
            text = {
                Text(
                    "Os dois modelos neurais de cacau (detector.tflite e detector_doenca.tflite), a leitura de planilhas Excel (.xlsx/.csv), " +
                        "os cálculos em @/ha e o banco de registros operam localmente sem internet na roça.\n\n" +
                        "Ao retornar ao laboratório CEPEC, envie tudo em 1 clique pelo Cabo USB (CacauSync) ou Wi-Fi local.",
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(onClick = { showDialogOffline = false }) { Text("Entendi") }
            }
        )
    }

    if (showDialogTutorial) {
        AlertDialog(
            onDismissRequest = {
                PersistenciaLocalRegistros.marcarTutorialVisto(context)
                showDialogTutorial = false
            },
            title = { Text("Guia Rápido de Campo CEPEC", fontWeight = FontWeight.Bold, color = CacauVerdeEscuro) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("1. Modo Rota ou Coleta Livre: escolha o clone/parcela/planta ou siga a planilha .xlsx carregada.", fontSize = 12.sp)
                    Text("2. Câmera IA: troque o modo com 1 toque (2 IAs / Doenças / Contagem), use o Botão Volume (±) para bater foto firme no campo.", fontSize = 12.sp)
                    Text("3. Revisão Científica: toque para selecionar fruto (4 alças nos cantos permitem arrastar e redimensionar), toque novamente para trocar entre Sadio, Vassoura, Podridão e Não Avaliável.", fontSize = 12.sp)
                    Text("4. Rendimento e PC: acompanhe kg/@ em tempo real e sincronize com o CacauSync.", fontSize = 12.sp)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        PersistenciaLocalRegistros.marcarTutorialVisto(context)
                        showDialogTutorial = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CacauVerdeEscuro)
                ) {
                    Text("Começar")
                }
            }
        )
    }
}

@Composable
private fun AcaoCampoCard(
    titulo: String,
    subtitulo: String,
    icone: ImageVector,
    destaque: Color,
    fundo: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = fundo),
        border = BorderStroke(1.dp, destaque.copy(alpha = 0.25f)),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.clickable { onClick() }
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(color = destaque, shape = RoundedCornerShape(10.dp), modifier = Modifier.size(38.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icone, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }
            Text(titulo, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = destaque)
            Text(subtitulo, fontSize = 11.sp, color = CacauTextoSecundario, maxLines = 2)
        }
    }
}

@Composable
private fun DialogGerenciarEnsaios(
    configAtual: PersistenciaLocalRegistros.ConfigEnsaioLocal,
    onDismiss: () -> Unit,
    onAtualizarCodigo: (String) -> Unit,
    onCriarNovo: (codigo: String, manterRota: Boolean) -> Unit,
    onAlternar: (String) -> Unit
) {
    val context = LocalContext.current
    val ensaios = remember { PersistenciaLocalRegistros.listarEnsaiosSalvos(context) }
    var codigoEditado by remember { mutableStateOf(configAtual.codigoEnsaio) }
    var novoCodigo by remember { mutableStateOf("") }
    var manterRota by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ensaios Genéticos Locais", fontWeight = FontWeight.Bold, color = CacauVerdeEscuro) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text("Código do ensaio atual:", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        value = codigoEditado,
                        onValueChange = { codigoEditado = it.uppercase() },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = { onAtualizarCodigo(codigoEditado) },
                        colors = ButtonDefaults.buttonColors(containerColor = CacauVerdeEscuro)
                    ) { Text("Salvar") }
                }

                if (ensaios.isNotEmpty()) {
                    Text("Ensaios salvos neste aparelho:", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    ensaios.forEach { e ->
                        val ativo = e.id == configAtual.ensaioId
                        Surface(
                            color = if (ativo) CacauVerdeClaro else Color(0xFFF6F8F6),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, if (ativo) CacauVerdeBotanico else CacauBordaSuave),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !ativo) { onAlternar(e.id) }
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(e.codigo, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = CacauTextoPrincipal)
                                    Text("${e.totalRegistros} coleta(s) • ${e.totalPontosRota} na rota", fontSize = 11.sp, color = CacauTextoSecundario)
                                }
                                if (ativo) {
                                    Text("ATIVO", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = CacauVerdeEscuro)
                                }
                            }
                        }
                    }
                }

                Text("Criar novo ensaio isolado:", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                OutlinedTextField(
                    value = novoCodigo,
                    onValueChange = { novoCodigo = it.uppercase() },
                    placeholder = { Text("Ex: TC-1904") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = manterRota, onCheckedChange = { manterRota = it })
                    Text("Copiar lista de plantas da rota atual", fontSize = 12.sp)
                }
                OutlinedButton(
                    onClick = { if (novoCodigo.isNotBlank()) onCriarNovo(novoCodigo.trim(), manterRota) },
                    enabled = novoCodigo.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Iniciar Novo Ensaio")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Fechar") }
        }
    )
}
