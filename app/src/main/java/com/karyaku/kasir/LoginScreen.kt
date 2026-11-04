package com.karyaku.kasir

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.karyaku.kasir.data.local.UserEntity
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    viewModel: PosViewModel,
    onLoginSuccess: (UserEntity) -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.logo_zokas),
                contentDescription = "Logo",
                modifier = Modifier.size(100.dp).clip(RoundedCornerShape(16.dp))
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text("Masuk ke ZoKas", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("Masukkan PIN Anda", style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(32.dp))

            OutlinedTextField(
                value = pin,
                onValueChange = { 
                    if (it.length <= 6) pin = it 
                    errorMessage = ""
                },
                label = { Text("PIN 6-Digit") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                isError = errorMessage.isNotEmpty()
            )
            
            if (errorMessage.isNotEmpty()) {
                Text(errorMessage, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    if (pin.length == 6) {
                        scope.launch {
                            val user = viewModel.login(pin)
                            if (user != null) {
                                onLoginSuccess(user)
                            } else {
                                errorMessage = "PIN salah! Silakan coba lagi."
                            }
                        }
                    } else {
                        errorMessage = "PIN harus 6 digit"
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Text("MASUK", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            Text("Default Owner: 123456 | Kasir: 111111", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}