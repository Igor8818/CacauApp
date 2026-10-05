package com.example.myapplication.userinterface.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.R
import com.example.myapplication.ui.theme.*

/**
 * Tela inicial de Check-in estilizada com identidade visual CacauApp CEPEC.
 */
@Composable
fun TelaCheckInSimples(
    ensaio: String,
    onEnsaioChange: (String) -> Unit,
    exibirBotaoVoltar: Boolean,
    onVoltarClick: () -> Unit,
    onContinuar: () -> Unit
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFF9F6F0),
                        Color(0xFFF1EBE1),
                        Color(0xFFE8E0D2)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .shadow(12.dp, RoundedCornerShape(24.dp)),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Logo da aplicação
                Box(
                    modifier = Modifier
                        .size(108.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .border(2.dp, Color(0xFFE0D5C1), RoundedCornerShape(22.dp))
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.app_logo),
                        contentDescription = "Logo CacauApp CEPEC",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }

                Spacer(Modifier.height(18.dp))

                Text(
                    text = "CacauApp CEPEC",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF1B5E20)
                )

                Text(
                    text = "Visão Computacional & Biometria de Frutos",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF5D4037)
                )

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFE8F5E9),
                    modifier = Modifier.padding(top = 10.dp)
                ) {
                    Text(
                        text = "100% Offline · Edge AI · CEPLAC",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF2E7D32),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }

                Spacer(Modifier.height(28.dp))

                OutlinedTextField(
                    value = ensaio,
                    onValueChange = onEnsaioChange,
                    label = { Text("Nome da Campanha / Ensaio") },
                    placeholder = { Text("Ex: CEPEC_SAFRA_2026") },
                    singleLine = true,
                    leadingIcon = {
                        Icon(Icons.Default.CalendarToday, null, tint = Color(0xFF2E7D32))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF2E7D32),
                        focusedLabelColor = Color(0xFF2E7D32)
                    )
                )

                Spacer(Modifier.height(24.dp))

                Button(
                    onClick = onContinuar,
                    enabled = ensaio.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF2E7D32),
                        disabledContainerColor = Color(0xFFC8E6C9)
                    )
                ) {
                    Text(
                        "INICIAR MONITORAMENTO",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, null, modifier = Modifier.size(18.dp))
                }

                if (exibirBotaoVoltar) {
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = onVoltarClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF4E342E))
                    ) {
                        Text(
                            "VOLTAR PARA COLETA ATUAL",
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF4E342E)
                        )
                    }
                }
            }
        }
    }
}
