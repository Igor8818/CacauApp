package br.gov.ceplac.cepec.cacauapp.userinterface

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.gov.ceplac.cepec.cacauapp.network.CacauSyncClient
import br.gov.ceplac.cepec.cacauapp.ui.theme.CacauPerigo
import br.gov.ceplac.cepec.cacauapp.ui.theme.CacauTextoSecundario
import br.gov.ceplac.cepec.cacauapp.ui.theme.CacauVerdeBotanico
import br.gov.ceplac.cepec.cacauapp.ui.theme.CacauVerdeEscuro
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun DialogConfiguracaoServidor(
    onDismiss: () -> Unit,
    onSalvar: (url: String, email: String, pass: String, ensaio: String) -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("cacausync_prefs", Context.MODE_PRIVATE) }

    var url by remember { mutableStateOf((prefs.getString("server_url", "") ?: "").ifBlank { CacauSyncClient.DEFAULT_SERVER_URL }) }
    var email by remember { mutableStateOf((prefs.getString("user_email", "") ?: "").ifBlank { CacauSyncClient.DEFAULT_EMAIL }) }
    var pass by remember { mutableStateOf((prefs.getString("user_pass", "") ?: "").ifBlank { CacauSyncClient.DEFAULT_PASS }) }
    var ensaio by remember { mutableStateOf(prefs.getString("codigo_ensaio", "TC-1801") ?: "TC-1801") }
    var testando by remember { mutableStateOf(false) }
    var resultadoTeste by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Servidor CacauSync (Wi-Fi / USB)", fontWeight = FontWeight.Bold, color = CacauVerdeEscuro) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "Se estiver conectado no cabo USB com o CacauSync aberto, o app descobre o PC automaticamente. Para Wi-Fi, informe o IP do computador:",
                    fontSize = 12.sp,
                    color = CacauTextoSecundario
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("URL do Servidor CacauSync") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = ensaio,
                    onValueChange = { ensaio = it.uppercase() },
                    label = { Text("Código do Ensaio") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("E-mail CEPEC") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = pass,
                    onValueChange = { pass = it },
                    label = { Text("Senha") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedButton(
                    onClick = {
                        testando = true
                        resultadoTeste = null
                        coroutineScope.launch {
                            val descoberta = withContext(Dispatchers.IO) {
                                if (CacauSyncClient.testarServidorAtivo(url)) {
                                    url
                                } else {
                                    CacauSyncClient.descobrirServidorAutomaticamente(url)
                                }
                            }
                            testando = false
                            if (descoberta != null) {
                                url = descoberta
                                resultadoTeste = "Conectado com sucesso em $descoberta"
                            } else {
                                resultadoTeste = "Servidor não respondeu em $url nem na rede local."
                            }
                        }
                    },
                    enabled = !testando,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (testando) "Testando..." else "Descobrir / Testar Conexão")
                }

                resultadoTeste?.let { msg ->
                    Text(
                        text = msg,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (msg.startsWith("Conectado")) CacauVerdeBotanico else CacauPerigo
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSalvar(url, email, pass, ensaio) },
                colors = ButtonDefaults.buttonColors(containerColor = CacauVerdeEscuro)
            ) { Text("Salvar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}
