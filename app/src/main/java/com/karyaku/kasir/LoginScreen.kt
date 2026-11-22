package com.karyaku.kasir

import android.content.res.Configuration
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val activity = LocalActivity.current as? FragmentActivity
    val scope = rememberCoroutineScope()

    val isTablet = configuration.screenWidthDp >= 600
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val isWideTablet = isTablet && isLandscape

    val isInitializing by viewModel.isInitializing.collectAsStateWithLifecycle()
    val users by viewModel.users.collectAsStateWithLifecycle()
    val biometricAllowed = settingsManager.isBiometricEnabled

    var selectedUser by remember { mutableStateOf<UserEntity?>(null) }
    var pin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var isPinVisible by remember { mutableStateOf(false) }

    if (isInitializing) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .padding(
                    horizontal = if (isTablet) 36.dp else 24.dp,
                    vertical = if (isWideTablet) 24.dp else 36.dp
                )
                .fillMaxWidth()
                .widthIn(max = if (isWideTablet) 920.dp else if (isTablet) 720.dp else 600.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.logo_zokas),
                contentDescription = "Logo",
                modifier = Modifier
                    .size(if (isTablet) 116.dp else 100.dp)
                    .clip(RoundedCornerShape(18.dp))
            )
            Spacer(modifier = Modifier.height(if (isTablet) 18.dp else 24.dp))

            if (selectedUser == null) {
                Text(
                    "Pilih Akun Anda",
                    style = if (isTablet) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "Ketuk profil untuk masuk",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(if (isTablet) 28.dp else 32.dp))

                if (users.isEmpty()) {
                    Text("Belum ada karyawan terdaftar.", color = MaterialTheme.colorScheme.error)
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = if (isWideTablet) 210.dp else if (isTablet) 180.dp else 130.dp),
                        horizontalArrangement = Arrangement.spacedBy(if (isTablet) 20.dp else 16.dp),
                        verticalArrangement = Arrangement.spacedBy(if (isTablet) 20.dp else 16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = if (isWideTablet) 520.dp else 440.dp)
                    ) {
                        items(users) { user ->
                            UserProfileCard(
                                user = user,
                                showBiometricBadge = biometricAllowed,
                                isLargeCard = isTablet,
                                onClick = {
                                    errorMessage = ""
                                    isPinVisible = false
                                    if (biometricAllowed && user.role == "OWNER" && user.biometricKey.isNotBlank() && activity != null) {
                                        viewModel.loginWithBiometric(activity, user) { success, msg ->
                                            if (success) {
                                                onLoginSuccess(user)
                                            } else {
                                                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                                selectedUser = user
                                            }
                                        }
                                    } else {
                                        selectedUser = user
                                    }
                                }
                            )
                        }
                    }
                }
            } else {
                val user = selectedUser!!

                TextButton(
                    onClick = {
                        selectedUser = null
                        pin = ""
                        errorMessage = ""
                        isPinVisible = false
                    },
                    modifier = Modifier.align(Alignment.Start)
                ) {
                    Icon(
                        Icons.Default.ArrowBack,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Kembali", color = MaterialTheme.colorScheme.primary)
                }

                Box(
                    modifier = Modifier
                        .size(if (isTablet) 96.dp else 80.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = user.name.firstOrNull()?.uppercase() ?: "?",
                        fontSize = if (isTablet) 42.sp else 36.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    user.name,
                    style = if (isTablet) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
                Text(
                    user.role,
                    fontSize = if (isTablet) 13.sp else 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(24.dp))

                OutlinedTextField(
                    value = pin,
                    onValueChange = {
                        if (it.length <= 6 && it.all { char -> char.isDigit() }) {
                            pin = it
                            errorMessage = ""
                        }
                    },
                    label = { Text("PIN (4-6 Digit)") },
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                    trailingIcon = {
                        IconButton(onClick = { isPinVisible = !isPinVisible }) {
                            Icon(
                                imageVector = if (isPinVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (isPinVisible) "Sembunyikan PIN" else "Tampilkan PIN",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = if (isPinVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    isError = errorMessage.isNotEmpty(),
                    enabled = !isLoading,
                    shape = RoundedCornerShape(12.dp)
                )

                if (errorMessage.isNotEmpty()) {
                    Text(
                        errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 8.dp).align(Alignment.Start)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = {
                        if (pin.length in 4..6) {
                            isLoading = true
                            scope.launch {
                                val loggedInUser = viewModel.login(pin)
                                isLoading = false
                                if (loggedInUser != null && loggedInUser.id == user.id) {
                                    onLoginSuccess(loggedInUser)
                                } else {
                                    errorMessage = "PIN salah untuk akun ini."
                                    pin = ""
                                }
                            }
                        } else {
                            errorMessage = "PIN harus 4 sampai 6 digit."
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(if (isTablet) 56.dp else 50.dp),
                    enabled = !isLoading,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("MASUK", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                    }
                }

                if (biometricAllowed && user.role == "OWNER" && user.biometricKey.isNotBlank() && activity != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = {
                            viewModel.loginWithBiometric(activity, user) { success, msg ->
                                if (success) {
                                    onLoginSuccess(user)
                                } else {
                                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(if (isTablet) 56.dp else 50.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.Fingerprint, contentDescription = "Biometric", tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("MASUK DENGAN BIOMETRIK", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun UserProfileCard(
    user: UserEntity,
    showBiometricBadge: Boolean = true,
    isLargeCard: Boolean = false,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(if (isLargeCard) 1.18f else 1.02f)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(if (isLargeCard) 20.dp else 16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = if (isLargeCard) 18.dp else 12.dp, vertical = if (isLargeCard) 16.dp else 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(if (isLargeCard) 72.dp else 56.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = user.name.firstOrNull()?.uppercase() ?: "?",
                    fontSize = if (isLargeCard) 32.sp else 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(if (isLargeCard) 12.dp else 8.dp))
            Text(
                text = user.name,
                fontWeight = FontWeight.Bold,
                fontSize = if (isLargeCard) 17.sp else 14.sp,
                lineHeight = if (isLargeCard) 21.sp else 18.sp,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = user.role,
                fontSize = if (isLargeCard) 12.sp else 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (showBiometricBadge && user.role == "OWNER" && user.biometricKey.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Icon(
                    Icons.Default.Fingerprint,
                    contentDescription = "Biometrik Aktif",
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(if (isLargeCard) 18.dp else 14.dp)
                )
            }
        }
    }
}
