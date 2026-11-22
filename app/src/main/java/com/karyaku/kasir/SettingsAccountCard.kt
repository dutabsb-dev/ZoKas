package com.karyaku.kasir

import android.content.Context
import android.widget.Toast
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.karyaku.kasir.data.local.UserEntity

@Composable
fun SettingsAccountCard(
    currentUser: UserEntity,
    isBiometricEnabled: Boolean,
    onBiometricToggle: (Boolean) -> Unit,
    onChangePinClick: () -> Unit,
    onPhotoSettingsClick: () -> Unit = {},
    onManageStaffClick: () -> Unit,
    onShiftHistoryClick: () -> Unit,
    onRegisterBiometricClick: () -> Unit,
    language: String
) {
    var isExpanded by remember { mutableStateOf(false) }
    val lang = language
    val context: Context = LocalContext.current
    val activity = LocalActivity.current as? FragmentActivity

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { isExpanded = !isExpanded }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    SettingsSectionLeadingIcon(Icons.Default.ManageAccounts, MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(
                            LanguageManager.tr("manajemen_akun", lang),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            currentUser.name,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = "Toggle",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            AnimatedVisibility(visible = isExpanded, enter = expandVertically(), exit = shrinkVertically()) {
                Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                    HorizontalDivider(
                        modifier = Modifier.padding(bottom = 12.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )

                    Text(
                        "${LanguageManager.tr("login_sebagai", lang)}: ${currentUser.name} (${currentUser.role})",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = onChangePinClick,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.tertiary,
                                contentColor = MaterialTheme.colorScheme.onTertiary
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(LanguageManager.tr("ubah_pin", lang), fontSize = 12.sp)
                        }
                        if (currentUser.role == "OWNER") {
                            Button(
                                onClick = onManageStaffClick,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(LanguageManager.tr("kelola_karyawan", lang), fontSize = 12.sp)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = onPhotoSettingsClick,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.AddAPhoto, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Foto Profil Akun", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = onRegisterBiometricClick,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (currentUser.biometricKey.isNotBlank())
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.secondary,
                            contentColor = if (currentUser.biometricKey.isNotBlank())
                                MaterialTheme.colorScheme.onPrimary
                            else
                                MaterialTheme.colorScheme.onSecondary
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Fingerprint, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            if (currentUser.biometricKey.isNotBlank()) "Biometrik Terdaftar (Ubah)" else "Daftarkan Sidik Jari/Wajah",
                            fontSize = 12.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = onShiftHistoryClick,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary,
                            contentColor = MaterialTheme.colorScheme.onSecondary
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(LanguageManager.tr("riwayat_shift", lang), fontSize = 12.sp)
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Login Sidik Jari (Login Screen)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "Aktifkan fitur login cepat di halaman awal.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = isBiometricEnabled,
                            onCheckedChange = { isChecked ->
                                if (isChecked) {
                                    if (activity == null) {
                                        Toast.makeText(context, "ERROR: Activity tidak ditemukan.", Toast.LENGTH_LONG).show()
                                        return@Switch
                                    }
                                    val biometricManager = BiometricManager.from(context)
                                    val authStatus = biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK)
                                    if (authStatus == BiometricManager.BIOMETRIC_SUCCESS) {
                                        val executor = ContextCompat.getMainExecutor(context)
                                        val biometricPrompt = BiometricPrompt(activity, executor,
                                            object : BiometricPrompt.AuthenticationCallback() {
                                                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                                                    super.onAuthenticationSucceeded(result)
                                                    onBiometricToggle(true)
                                                    Toast.makeText(context, "Sidik jari berfungsi.", Toast.LENGTH_SHORT).show()
                                                }

                                                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                                                    super.onAuthenticationError(errorCode, errString)
                                                    onBiometricToggle(false)
                                                    Toast.makeText(context, "Gagal: $errString", Toast.LENGTH_LONG).show()
                                                }
                                            })
                                        val promptInfo = BiometricPrompt.PromptInfo.Builder()
                                            .setTitle("Aktivasi Sidik Jari")
                                            .setSubtitle("Sentuh sensor untuk mengonfirmasi")
                                            .setNegativeButtonText("Batal")
                                            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
                                            .build()
                                        biometricPrompt.authenticate(promptInfo)
                                    } else {
                                        Toast.makeText(context, "HP belum mendaftarkan sidik jari.", Toast.LENGTH_LONG).show()
                                        onBiometricToggle(false)
                                    }
                                } else {
                                    onBiometricToggle(false)
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedTrackColor = MaterialTheme.colorScheme.primary,
                                uncheckedTrackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                            )
                        )
                    }
                }
            }
        }
    }
}
