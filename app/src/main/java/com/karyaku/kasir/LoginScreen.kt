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
    val activity = context as? FragmentActivity

    val isInitializing by viewModel.isInitializing.collectAsStateWithLifecycle()

    // Cek perangkat mendukung Biometrik atau tidak
    val biometricManager = BiometricManager.from(context)
    val canUseBiometric = biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) != BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE

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
            Text(LanguageManager.tr("selamat_datang", settingsManager.language), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = SlateGreyDark, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Text(LanguageManager.tr("masukkan_pin", settingsManager.language), style = MaterialTheme.typography.bodyMedium, color = SlateGrey)
            Spacer(modifier = Modifier.height(32.dp))

            OutlinedTextField(
                value = pin,
                onValueChange = { 
                    if (it.length <= 6 && it.all { char -> char.isDigit() }) {
                        pin = it 
                        errorMessage = ""
                    }
                },
                label = { Text(LanguageManager.tr("pin_6", settingsManager.language)) },
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
                                errorMessage = LanguageManager.tr("pin_salah", settingsManager.language)
                                pin = "" 
                            }
                        }
                    } else {
                        errorMessage = LanguageManager.tr("pin_harus_6", settingsManager.language)
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
                    Text(LanguageManager.tr("login", settingsManager.language), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }

            // HANYA MUNCUL JIKA HARDWARE ADA DAN DIAKTIFKAN DI PENGATURAN
            if (settingsManager.isBiometricEnabled && canUseBiometric && activity != null) {
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedButton(
                    onClick = {
                        val executor = ContextCompat.getMainExecutor(context)
                        val biometricPrompt = BiometricPrompt(activity, executor,
                            object : BiometricPrompt.AuthenticationCallback() {
                                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                                    super.onAuthenticationSucceeded(result)
                                    scope.launch {
                                        val owner = viewModel.getFirstOwner()
                                        if (owner != null) {
                                            onLoginSuccess(owner) 
                                        } else {
                                            errorMessage = "No registered Owner found."
                                        }
                                    }
                                }
                                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                                    super.onAuthenticationError(errorCode, errString)
                                    if (errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON && errorCode != BiometricPrompt.ERROR_USER_CANCELED) {
                                        errorMessage = "Fingerprint Error: $errString"
                                    }
                                }
                            })

                        // PERBAIKAN: Tambahkan setAllowedAuthenticators
                        val promptInfo = BiometricPrompt.PromptInfo.Builder()
                            .setTitle("Login Sidik Jari")
                            .setSubtitle("Sentuh sensor sidik jari Anda")
                            .setNegativeButtonText("Batal")
                            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK) // Wajib untuk Android 11+
                            .build()

                        biometricPrompt.authenticate(promptInfo)
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, EmeraldGreen)
                ) {
                    Icon(Icons.Default.Fingerprint, contentDescription = "Fingerprint", tint = EmeraldGreen)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(LanguageManager.tr("login_fingerprint", settingsManager.language), color = EmeraldGreen, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}