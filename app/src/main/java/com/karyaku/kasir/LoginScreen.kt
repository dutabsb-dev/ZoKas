package com.karyaku.kasir

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karyaku.kasir.data.local.UserEntity
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    viewModel: PosViewModel,
    settingsManager: SettingsManager,
    onLoginSuccess: (UserEntity) -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    // Pastikan MainActivity kita adalah AppCompatActivity (lihat file MainActivity di bawah)
    val activity = context as? FragmentActivity

    val isInitializing by viewModel.isInitializing.collectAsStateWithLifecycle()

    // Cek apakah HP mendukung Biometrik (Sidik Jari)
    val biometricManager = BiometricManager.from(context)
    val canUseBiometric = biometricManager.canAuthenticate(
        BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
    ) == BiometricManager.BIOMETRIC_SUCCESS

    if (isInitializing) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = EmeraldGreen)
        }
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState()),
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
            Spacer(modifier = Modifier.height(24.dp))
            Text("Selamat Datang di ZoKas", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = SlateGreyDark)
            Text("Masukkan PIN Anda untuk melanjutkan", style = MaterialTheme.typography.bodyMedium, color = SlateGrey)
            Spacer(modifier = Modifier.height(32.dp))

            OutlinedTextField(
                value = pin,
                onValueChange = { 
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
                Text(errorMessage, color = ErrorRed, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Tombol Login Manual (PIN)
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
                                pin = "" 
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
                    Text("LOGIN", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Tombol Login Sidik Jari (Hanya muncul jika HP mendukung & Activity valid)
            if (canUseBiometric && activity != null) {
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedButton(
                    onClick = {
                        val executor = ContextCompat.getMainExecutor(context)
                        val biometricPrompt = BiometricPrompt(activity, executor,
                            object : BiometricPrompt.AuthenticationCallback() {
                                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                                    super.onAuthenticationSucceeded(result)
                                    // Jika sidik jari berhasil, login sebagai Owner (User pertama)
                                    scope.launch {
                                        val owner = viewModel.getFirstOwner()
                                        if (owner != null) {
                                            onLoginSuccess(owner) 
                                        } else {
                                            errorMessage = "Tidak ada akun Owner terdaftar."
                                        }
                                    }
                                }
                                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                                    super.onAuthenticationError(errorCode, errString)
                                    errorMessage = "Gagal sidik jari: $errString"
                                }
                            })

                        val promptInfo = BiometricPrompt.PromptInfo.Builder()
                            .setTitle("Login Sidik Jari")
                            .setSubtitle("Sentuh sensor sidik jari Anda")
                            .setNegativeButtonText("Batal")
                            .build()

                        biometricPrompt.authenticate(promptInfo)
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, EmeraldGreen)
                ) {
                    Icon(Icons.Default.Fingerprint, contentDescription = "Fingerprint", tint = EmeraldGreen)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("LOGIN DENGAN SIDIK JARI", color = EmeraldGreen, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}