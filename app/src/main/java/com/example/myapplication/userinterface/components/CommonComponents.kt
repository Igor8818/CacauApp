package com.example.myapplication.userinterface.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Card de métrica consolidada (usado na Galeria e Diagnóstico). */
@Composable
fun CartaoMetrica(
    modifier: Modifier = Modifier,
    icone: ImageVector,
    valor: String,
    rotulo: String,
    cor: Color
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9))
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icone, null, tint = cor, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(10.dp))
            Column {
                Text(valor, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = cor)
                Text(rotulo, fontSize = 11.sp, color = Color(0xFF558B2F))
            }
        }
    }
}

/** Aviso exibido quando não há modelo .tflite utilizável; direciona à tela de configuração. */
@Composable
fun AvisoModeloAusente(nomeModelo: String, onAbrirModelo: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0))
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.ReportProblem, null, tint = Color(0xFFE65100))
                Spacer(Modifier.width(8.dp))
                Text("Modelo de IA indisponível", fontWeight = FontWeight.Bold, color = Color(0xFFE65100))
            }
            Text(
                "O modelo \"$nomeModelo\" não foi encontrado ou não pôde ser carregado. Importe um arquivo .tflite para habilitar a câmera.",
                fontSize = 12.sp, color = Color.DarkGray
            )
            Button(
                onClick = onAbrirModelo,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE65100))
            ) {
                Icon(Icons.Default.SmartToy, null)
                Spacer(Modifier.width(8.dp))
                Text("CONFIGURAR MODELO IA", fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** Seletor Dropdown para opções de mapeamento e configuração. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeletorDropdownSimples(
    opcoes: List<String>,
    selecionado: String,
    onSelecionadoChange: (String) -> Unit
) {
    var expandido by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expandido, onExpandedChange = { expandido = !expandido }) {
        OutlinedTextField(
            value = selecionado,
            onValueChange = {},
            readOnly = true,
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable, true),
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandido) }
        )
        ExposedDropdownMenu(expanded = expandido, onDismissRequest = { expandido = false }) {
            opcoes.forEach { opcao ->
                DropdownMenuItem(
                    text = { Text(opcao) },
                    onClick = {
                        onSelecionadoChange(opcao)
                        expandido = false
                    }
                )
            }
        }
    }
}
