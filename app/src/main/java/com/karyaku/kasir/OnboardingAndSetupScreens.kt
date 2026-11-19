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
                        label = { Text("Email (cth: nama@gmail.com) *", maxLines = 1, overflow = TextOverflow.Ellipsis) },
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
                        text = "Mendukung Gmail, Yahoo Mail, Outlook, iCloud, maupun email usaha lainnya.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 16.sp
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

            OwnerAccountFormSection(
                ownerName = ownerName,
                onOwnerNameChange = onOwnerNameChange,
                pin = pin,
                onPinChange = onPinChange,
                confirmPin = confirmPin,
                onConfirmPinChange = onConfirmPinChange
            )

            AdaptiveActionButton(
                text = "SIMPAN & MULAI GUNAKAN ZOKAS",
                onClick = onSubmitSetup,
                enabled = ownerName.isNotBlank() && pin.length >= 4 && pin == confirmPin
            )
        }
    }
}
