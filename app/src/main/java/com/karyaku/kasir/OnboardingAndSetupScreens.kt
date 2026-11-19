package com.karyaku.kasir

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun EmailPrivacyOnboardingScreen(
    email: String,
    onEmailChange: (String) -> Unit,
    isPolicyAccepted: Boolean,
    onPolicyAcceptedChange: (Boolean) -> Unit,
    onOpenFullPolicy: () -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .navigationBarsPadding()
    ) {
        SafeTopBarHeader(
            title = "Masuk dengan Email Anda",
            subtitle = "Daftarkan email aktif (Gmail, Yahoo, Outlook, dll.) dan setujui Kebijakan Privasi lengkap sebelum menyiapkan toko Anda.",
            leadingIcon = Icons.Default.MarkEmailRead
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "1. Alamat Email Pengguna",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    OutlinedTextField(
                        value = email,
                        onValueChange = onEmailChange,
                        label = {
                            Text(
                                text = "Email *",
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        placeholder = {
                            Text(
                                text = "nama@email.com",
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                        },
                        leadingIcon = { Icon(Icons.Default.AlternateEmail, contentDescription = null) },
                        trailingIcon = {
                            if (email.isNotEmpty()) {
                                IconButton(onClick = { onEmailChange("") }) {
                                    Icon(Icons.Default.Close, contentDescription = "Hapus")
                                }
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Text(
                        text = "Contoh: nama@gmail.com / nama@yahoo.com • Mendukung Gmail, Yahoo Mail, Outlook, iCloud, maupun email usaha.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 17.sp
                    )
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "2. Kebijakan Privasi & Ketentuan",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = onOpenFullPolicy) {
                            Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Baca Penuh", fontWeight = FontWeight.Bold)
                        }
                    }

                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "KEBIJAKAN PRIVASI, PERLINDUNGAN DATA & KETENTUAN PENGGUNAAN ZOKAS\nDikembangkan oleh: DBSB Studio (dbsb-studio.my.id)\nData operasional kasir, stok, dan transaksi disimpan secara lokal di perangkat Anda.",
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(12.dp)
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Checkbox(
                            checked = isPolicyAccepted,
                            onCheckedChange = onPolicyAcceptedChange
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Saya menyetujui Kebijakan Privasi & Ketentuan Penggunaan ZoKas.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = 17.sp
                        )
                    }
                }
            }

            AdaptiveActionButton(
                text = "LANJUTKAN",
                onClick = onContinue,
                enabled = email.contains("@") && email.contains(".") && isPolicyAccepted
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun BusinessTypeSetupScreen(
    selectedBusinessType: String,
    onSelectBusinessType: (String) -> Unit,
    autoApplyRecommendation: Boolean,
    onAutoApplyRecommendationChange: (Boolean) -> Unit,
    ownerName: String,
    onOwnerNameChange: (String) -> Unit,
    pin: String,
    onPinChange: (String) -> Unit,
    confirmPin: String,
    onConfirmPinChange: (String) -> Unit,
    onSubmitSetup: () -> Unit,
    modifier: Modifier = Modifier
) {
    var pinVisible by remember { mutableStateOf(false) }
    var confirmPinVisible by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .navigationBarsPadding()
    ) {
        SafeTopBarHeader(
            title = "Pengaturan Awal Toko",
            subtitle = "Pilih tipe usaha Anda dan buat akun pemilik toko untuk mulai menggunakan ZoKas.",
            leadingIcon = Icons.Default.Storefront
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            BusinessTypeOptionCard(
                title = "Jasa (Salon, Laundry, Bengkel, Klinik)",
                description = "Cocok untuk barbershop, klinik kecantikan, cuci kendaraan, & servis.",
                icon = Icons.Default.ContentCut,
                selected = selectedBusinessType == "JASA",
                onSelect = { onSelectBusinessType("JASA") }
            )

            BusinessTypeOptionCard(
                title = "Musiman (Toko Kue, Hampers, Event)",
                description = "Cocok untuk usaha PO kue kering, katering, bazar, & musiman.",
                icon = Icons.Default.EventAvailable,
                selected = selectedBusinessType == "MUSIMAN",
                onSelect = { onSelectBusinessType("MUSIMAN") }
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Rekomendasi Modul untuk $selectedBusinessType:",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                    Text(
                        text = "• Harga Grosir Otomatis (Wholesale Min. Qty)\n• Kode Unik Produk (SKU) & Scan Barcode Cepat\n• Supplier & Pembelian Stok (Purchase Order)\n• Multi-Varian Satuan (Biji, Renteng, Dus)",
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Checkbox(
                            checked = autoApplyRecommendation,
                            onCheckedChange = onAutoApplyRecommendationChange
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Aktifkan otomatis rekomendasi modul $selectedBusinessType",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Susunan vertikal dari atas ke bawah: Nama Pemilik -> PIN / Password -> Ulangi PIN / Pengecekan Password
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "3. Akun Pemilik Toko (Owner)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    OutlinedTextField(
                        value = ownerName,
                        onValueChange = onOwnerNameChange,
                        label = { Text("Nama Pemilik *", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    OutlinedTextField(
                        value = pin,
                        onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) onPinChange(it) },
                        label = { Text("PIN / Password (4-6 Angka) *", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
                        trailingIcon = {
                            IconButton(onClick = { pinVisible = !pinVisible }) {
                                Icon(
                                    imageVector = if (pinVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (pinVisible) "Sembunyikan PIN" else "Tampilkan PIN"
                                )
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = if (pinVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    OutlinedTextField(
                        value = confirmPin,
                        onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) onConfirmPinChange(it) },
                        label = { Text("Ulangi PIN / Pengecekan Password *", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
                        trailingIcon = {
                            IconButton(onClick = { confirmPinVisible = !confirmPinVisible }) {
                                Icon(
                                    imageVector = if (confirmPinVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (confirmPinVisible) "Sembunyikan Ulangi PIN" else "Tampilkan Ulangi PIN"
                                )
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = if (confirmPinVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }

            AdaptiveActionButton(
                text = "SIMPAN & MULAI GUNAKAN ZOKAS",
                onClick = onSubmitSetup,
                enabled = ownerName.isNotBlank() && pin.length >= 4 && pin == confirmPin
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
