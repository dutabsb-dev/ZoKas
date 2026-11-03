package com.karyaku.kasir

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SettingsScreen(
    isPro: Boolean,
    onUpgradeClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Pengaturan & Status",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(24.dp))

        if (isPro) {
            // Tampilan jika sudah beli Pro
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFD700)), // Warna Emas
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.Star, contentDescription = "Pro", tint = Color.White, modifier = Modifier.size(48.dp))
                    Text("VERSI PRO AKTIF", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color.White)
                    Text("Terima kasih telah mendukung UMKM!", color = Color.White, textAlign = TextAlign.Center)
                }
            }
        } else {
            // Tampilan jika masih Gratis
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Versi Gratis", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Upgrade ke Pro untuk membuka fitur ekspor laporan, kustom struk, dan backup data.", textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = onUpgradeClick,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Beli Versi Pro - Rp 10.000", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}