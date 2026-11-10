package com.karyaku.kasir

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
    var isLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val isInitializing by viewModel.isInitializing.collectAsStateWithLifecycle()

    // Tambahkan imePadding agar aman dari keyboard
    Box(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.Center
    ) {
        if (isInitializing) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = EmeraldGreen)
                Spacer(modifier = Modifier.height(16.dp))
                Text("Menyiapkan database...", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(32.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.logo_zokas),
                    contentDescription = "Logo",
                    modifier = Modifier.size(100.dp).clip(RoundedCornerShape(16.dp))
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text("Masuk ke ZoKas", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = SlateGreyDark)
                Text("Masukkan PIN Keamanan Anda", style = MaterialTheme.typography.bodyMedium, color = SlateGrey)
                Spacer(modifier = Modifier.height(32.dp))

                OutlinedTextField(
                    value = pin,
                    onValueChange = { 
                        // Hanya izinkan angka dan maksimal 6 digit
                        if (it.length <= 6 && it.all { char -> char.isDigit() }) {
                            pin = it 
                            errorMessage = ""
                        }
                    },
                    label = { Text("PIN 6-Digit") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    isError = errorMessage.isNotEmpty(),
                    enabled = !isLoading,
                    shape = RoundedCornerShape(12.dp)
                )
                
                if (errorMessage.isNotEmpty()) {
                    Text(
                        text = errorMessage, 
                        color = ErrorRed, 
                        fontSize = 12.sp, 
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = {
                        if (pin.length == 6) {
                            isLoading = true
                            scope.launch {
                                val user = viewModel.login(pin)
                                isLoading = false
                                if (user != null) {
                                    onLoginSuccess(user)
                                } else {
                                    errorMessage = "PIN salah! Silakan coba lagi."
                                    pin = "" // Reset input jika salah
                                }
                            }
                        } else {
                            errorMessage = "PIN harus 6 digit angka."
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    enabled = !isLoading,
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(color = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    } else {
                        Text("MASUK", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                Text("Default Owner: 123456 | Kasir: 111111", fontSize = 12.sp, color = SlateGrey)
            }
        }
    }
}