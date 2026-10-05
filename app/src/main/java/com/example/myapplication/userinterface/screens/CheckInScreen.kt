package com.example.myapplication.userinterface.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Tela inicial de Check-in para definição da campanha de coleta.
 */
@Composable
fun TelaCheckInSimples(
    ensaio: String,
    onEnsaioChange: (String) -> Unit,
    exibirBotaoVoltar: Boolean,
    onVoltarClick: () -> Unit,
    onContinuar: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.Eco, null, tint = Color(0xFF2E7D32), modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(16.dp))
        Text("CacauApp CEPEC", fontSize = 26.sp, fontWeight = FontWeight.Black, color = Color(0xFF1B5E20))
        Text("Monitoramento Biométrico de Frutos com IA", fontSize = 14.sp, color = Color(0xFF558B2F))
        Spacer(Modifier.height(32.dp))
        OutlinedTextField(
            value = ensaio,
            onValueChange = onEnsaioChange,
            label = { Text("Nome da Campanha de Monitoramento") },
            placeholder = { Text("Ex: TC_LOTE_2026") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = onContinuar,
            enabled = ensaio.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
        ) {
            Text("INICIAR MONITORAMENTO", fontWeight = FontWeight.Bold)
        }
        if (exibirBotaoVoltar) {
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = onVoltarClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text("VOLTAR PARA COLETA ATUAL")
            }
        }
    }
}
