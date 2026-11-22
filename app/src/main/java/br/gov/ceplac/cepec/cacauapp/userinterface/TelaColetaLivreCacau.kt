package br.gov.ceplac.cepec.cacauapp.userinterface

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.gov.ceplac.cepec.cacauapp.data.CLONES_CEPEC_PADRAO
import br.gov.ceplac.cepec.cacauapp.data.ModelConfig
import br.gov.ceplac.cepec.cacauapp.data.RegistroCacau
import br.gov.ceplac.cepec.cacauapp.ui.theme.CacauBordaSuave
import br.gov.ceplac.cepec.cacauapp.ui.theme.CacauDourado
import br.gov.ceplac.cepec.cacauapp.ui.theme.CacauFundoSolar
import br.gov.ceplac.cepec.cacauapp.ui.theme.CacauInfo
import br.gov.ceplac.cepec.cacauapp.ui.theme.CacauInfoClaro
import br.gov.ceplac.cepec.cacauapp.ui.theme.CacauTextoSecundario
import br.gov.ceplac.cepec.cacauapp.ui.theme.CacauVerdeClaro
import br.gov.ceplac.cepec.cacauapp.ui.theme.CacauVerdeEscuro
import br.gov.ceplac.cepec.cacauapp.userinterface.util.PersistenciaLocalRegistros
import java.util.Locale

private data class PendenciaResetPlanta(
    val mensagem: String,
    val onResetarPlanta1: () -> Unit,
    val onManterPlanta: () -> Unit
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelaColetaLivreCacau(
    configEnsaio: PersistenciaLocalRegistros.ConfigEnsaioLocal,
    modelConfig: ModelConfig,
    registros: List<RegistroCacau>,
    clone: String,
    parcela: String,
    numPlanta: Int,
    onAbrirConfigIA: () -> Unit,
    onAlterarClone: (String, resetarPlanta: Boolean) -> Unit,
    onAlterarParcela: (String, resetarPlanta: Boolean) -> Unit,
    onAlterarNumPlanta: (Int) -> Unit,
    onAbrirCamera: (mesmaPlantaNovoAngulo: Boolean) -> Unit,
    onImportarGaleria: (mesmaPlantaNovoAngulo: Boolean) -> Unit,
    onMarcarNaoColetavel: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    var dropdownCloneAberto by remember { mutableStateOf(false) }
    var filtroClone by remember { mutableStateOf(clone) }
    var dialogDigitarParcela by remember { mutableStateOf(false) }
    var dialogDigitarPlanta by remember { mutableStateOf(false) }
    var pendenciaReset by remember { mutableStateOf<PendenciaResetPlanta?>(null) }

    LaunchedEffect(clone) {
        filtroClone = clone
    }

    val prefixoPlanta = "${clone.trim()}_P${parcela.trim().ifBlank { "1" }}_Pl$numPlanta"
    val registrosMesmaPlanta = remember(registros, prefixoPlanta) {
        registros.filter {
            it.plantId.equals(prefixoPlanta, ignoreCase = true) ||
                it.plantId.startsWith("${prefixoPlanta}_Foto", ignoreCase = true)
        }
    }

    val clonesFiltrados = remember(filtroClone) {
        val q = filtroClone.trim().lowercase(Locale.ROOT)
        if (q.isEmpty()) CLONES_CEPEC_PADRAO else CLONES_CEPEC_PADRAO.filter { it.lowercase(Locale.ROOT).contains(q) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CacauFundoSolar)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Coleta Livre • ${configEnsaio.codigoEnsaio}",
            fontSize = 20.sp,
            fontWeight = FontWeight.ExtraBold,
            color = CacauVerdeEscuro
        )

        BarraStatusModeloCacau(modelConfig = modelConfig, onClickConfig = onAbrirConfigIA)

        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, CacauBordaSuave),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ExposedDropdownMenuBox(
                    expanded = dropdownCloneAberto,
                    onExpandedChange = { dropdownCloneAberto = !dropdownCloneAberto }
                ) {
                    OutlinedTextField(
                        value = filtroClone,
                        onValueChange = {
                            filtroClone = it
                            dropdownCloneAberto = true
                        },
                        label = { Text("${configEnsaio.labelClone} (digite ou escolha)") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownCloneAberto) },
                        singleLine = true,
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryEditable)
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = dropdownCloneAberto,
                        onDismissRequest = { dropdownCloneAberto = false }
                    ) {
                        if (filtroClone.isNotBlank() && clonesFiltrados.none { it.equals(filtroClone.trim(), ignoreCase = true) }) {
                            val digitado = filtroClone.trim().uppercase()
                            DropdownMenuItem(
                                text = { Text("Usar clone \"$digitado\"", fontWeight = FontWeight.Bold, color = CacauVerdeEscuro) },
                                onClick = {
                                    dropdownCloneAberto = false
                                    if (numPlanta > 1 && digitado != clone) {
                                        pendenciaReset = PendenciaResetPlanta(
                                            mensagem = "Trocar clone para $digitado e reiniciar Planta para 1?",
                                            onResetarPlanta1 = { onAlterarClone(digitado, true) },
                                            onManterPlanta = { onAlterarClone(digitado, false) }
                                        )
                                    } else {
                                        onAlterarClone(digitado, false)
                                    }
                                }
                            )
                        }
                        clonesFiltrados.take(25).forEach { op ->
                            DropdownMenuItem(
                                text = { Text(op) },
                                onClick = {
                                    dropdownCloneAberto = false
                                    filtroClone = op
                                    if (numPlanta > 1 && op != clone) {
                                        pendenciaReset = PendenciaResetPlanta(
                                            mensagem = "Trocar clone para $op e reiniciar Planta para 1?",
                                            onResetarPlanta1 = { onAlterarClone(op, true) },
                                            onManterPlanta = { onAlterarClone(op, false) }
                                        )
                                    } else {
                                        onAlterarClone(op, false)
                                    }
                                }
                            )
                        }
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CardControleNumerico(
                        titulo = configEnsaio.labelParcela.uppercase(),
                        valorTexto = parcela,
                        onDiminuir = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            val atual = parcela.toIntOrNull() ?: 1
                            val nova = (atual - 1).coerceAtLeast(1).toString()
                            if (numPlanta > 1 && nova != parcela) {
                                pendenciaReset = PendenciaResetPlanta(
                                    mensagem = "Mudou para Parcela $nova. Reiniciar Planta para 1?",
                                    onResetarPlanta1 = { onAlterarParcela(nova, true) },
                                    onManterPlanta = { onAlterarParcela(nova, false) }
                                )
                            } else {
                                onAlterarParcela(nova, false)
                            }
                        },
                        onAumentar = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            val atual = parcela.toIntOrNull() ?: 1
                            val nova = (atual + 1).toString()
                            if (numPlanta > 1 && nova != parcela) {
                                pendenciaReset = PendenciaResetPlanta(
                                    mensagem = "Mudou para Parcela $nova. Reiniciar Planta para 1?",
                                    onResetarPlanta1 = { onAlterarParcela(nova, true) },
                                    onManterPlanta = { onAlterarParcela(nova, false) }
                                )
                            } else {
                                onAlterarParcela(nova, false)
                            }
                        },
                        onClicarNumero = { dialogDigitarParcela = true },
                        modifier = Modifier.weight(1f)
                    )

                    CardControleNumerico(
                        titulo = configEnsaio.labelPlanta.uppercase(),
                        valorTexto = numPlanta.toString(),
                        onDiminuir = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onAlterarNumPlanta((numPlanta - 1).coerceAtLeast(1))
                        },
                        onAumentar = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onAlterarNumPlanta(numPlanta + 1)
                        },
                        onClicarNumero = { dialogDigitarPlanta = true },
                        modifier = Modifier.weight(1f)
                    )
                }

                Surface(color = CacauVerdeClaro, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "ID de campo: $prefixoPlanta",
                        color = CacauVerdeEscuro,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }
        }

        if (registrosMesmaPlanta.isNotEmpty()) {
            val somaFrutos = registrosMesmaPlanta.sumOf { it.totalFrutos }
            Surface(
                color = CacauInfoClaro,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, CacauInfo.copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = CacauInfo, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Planta já possui ${registrosMesmaPlanta.size} foto(s) ($somaFrutos frutos).",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = CacauInfo
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = { onAbrirCamera(true) },
                            colors = ButtonDefaults.buttonColors(containerColor = CacauInfo),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("+ Ângulo 360°", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = { onAbrirCamera(false) },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Substituir Foto", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        Button(
            onClick = { onAbrirCamera(false) },
            colors = ButtonDefaults.buttonColors(containerColor = CacauVerdeEscuro),
            shape = RoundedCornerShape(16.dp),
            contentPadding = PaddingValues(vertical = 18.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(10.dp))
            Text("ABRIR CÂMERA IA • $prefixoPlanta", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = { onImportarGaleria(false) },
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(vertical = 12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Galeria", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }

            OutlinedButton(
                onClick = onMarcarNaoColetavel,
                border = BorderStroke(1.2.dp, CacauDourado),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF8D5500)),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(vertical = 12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Block, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Não Coletável", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
    }

    if (dialogDigitarParcela) {
        DialogValorNumerico("Número / Código da Parcela", parcela, isNumerico = false, onDismiss = { dialogDigitarParcela = false }) { nova ->
            dialogDigitarParcela = false
            val norm = nova.trim().ifBlank { "1" }
            if (numPlanta > 1 && norm != parcela) {
                pendenciaReset = PendenciaResetPlanta(
                    mensagem = "Mudou para Parcela $norm. Reiniciar Planta para 1?",
                    onResetarPlanta1 = { onAlterarParcela(norm, true) },
                    onManterPlanta = { onAlterarParcela(norm, false) }
                )
            } else {
                onAlterarParcela(norm, false)
            }
        }
    }

    if (dialogDigitarPlanta) {
        DialogValorNumerico("Número da Planta", numPlanta.toString(), isNumerico = true, onDismiss = { dialogDigitarPlanta = false }) { novo ->
            dialogDigitarPlanta = false
            onAlterarNumPlanta(novo.toIntOrNull()?.coerceAtLeast(1) ?: numPlanta)
        }
    }

    val pend = pendenciaReset
    if (pend != null) {
        AlertDialog(
            onDismissRequest = { pendenciaReset = null },
            title = { Text("Reiniciar Planta para 1?", fontWeight = FontWeight.Bold, color = CacauVerdeEscuro) },
            text = { Text(pend.mensagem) },
            confirmButton = {
                Button(
                    onClick = {
                        pend.onResetarPlanta1()
                        pendenciaReset = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CacauVerdeEscuro)
                ) { Text("Sim, ir p/ Pl 1") }
            },
            dismissButton = {
                TextButton(onClick = {
                    pend.onManterPlanta()
                    pendenciaReset = null
                }) { Text("Manter Planta $numPlanta") }
            }
        )
    }
}

@Composable
private fun CardControleNumerico(
    titulo: String,
    valorTexto: String,
    onDiminuir: () -> Unit,
    onAumentar: () -> Unit,
    onClicarNumero: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color(0xFFF6F9F6),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, CacauBordaSuave),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(titulo, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = CacauTextoSecundario)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onDiminuir,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(Icons.Default.Remove, contentDescription = "Diminuir $titulo")
                }

                Text(
                    text = valorTexto,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = CacauVerdeEscuro,
                    modifier = Modifier
                        .clickable { onClicarNumero() }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )

                Button(
                    onClick = onAumentar,
                    colors = ButtonDefaults.buttonColors(containerColor = CacauVerdeEscuro),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Aumentar $titulo")
                }
            }
            Text("Toque no nº para digitar", fontSize = 10.sp, color = CacauTextoSecundario)
        }
    }
}

@Composable
private fun DialogValorNumerico(
    titulo: String,
    valorAtual: String,
    isNumerico: Boolean,
    onDismiss: () -> Unit,
    onConfirmar: (String) -> Unit
) {
    var texto by remember { mutableStateOf(valorAtual) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(titulo, fontWeight = FontWeight.Bold, color = CacauVerdeEscuro) },
        text = {
            OutlinedTextField(
                value = texto,
                onValueChange = { texto = if (isNumerico) it.filter { ch -> ch.isDigit() } else it },
                keyboardOptions = KeyboardOptions(keyboardType = if (isNumerico) KeyboardType.Number else KeyboardType.Text),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = { onConfirmar(texto) },
                colors = ButtonDefaults.buttonColors(containerColor = CacauVerdeEscuro)
            ) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}
