package com.karyaku.kasir

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SettingsScreen(
    settingsManager: SettingsManager,
    isPro: Boolean,
    onUpgradeClick: () -> Unit,
    isDarkMode: Boolean,
    onToggleDarkMode: () -> Unit,
    language: String,
    onToggleLanguage: () -> Unit,
    onLogout: () -> Unit // Parameter Baru (sudah diberi koma yang benar)
) {
    var storeName by remember { mutableStateOf(settingsManager.storeName) }
    var storeFooter by remember { mutableStateOf(settingsManager.storeFooter) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Pengaturan & Status",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(16.dp))

        // --- PENGATURAN TEMA & BAHASA ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Mode Gelap", style = MaterialTheme.typography.titleMedium)
                    Switch(
                        checked = isDarkMode,
                        onCheckedChange = { onToggleDarkMode() }
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Bahasa", style = MaterialTheme.typography.titleMedium)
                    Button(onClick = { onToggleLanguage() }) {
                        Text(if (language == "in") "Indonesia" else "English")
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))

        // --- STATUS PRO ---
        if (isPro) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFD700)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.Star, contentDescription = "Pro", tint = Color.White, modifier = Modifier.size(48.dp))
                    Text("VERSI PRO AKTIF", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color.White)
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text("Kustomisasi Struk", fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Start))
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = storeName,
                onValueChange = { storeName = it; settingsManager.storeName = it },
                label = { Text("Nama Toko") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = storeFooter,
                onValueChange = { storeFooter = it; settingsManager.storeFooter = it },
                label = { Text("Pesan Bawah Struk") },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 2
            )
        } else {
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
                    Text("Upgrade ke Pro untuk kustom nama toko, ekspor CSV, dan backup data.", textAlign = TextAlign.Center)
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
        
        Spacer(modifier = Modifier.height(32.dp))
        Text("ZoKas v1.0.0", style = MaterialTheme.typography.bodySmall, color = Color.Gray)

        // --- TOMBOL LOGOUT (Sudah dipindahkan ke DALAM Column) ---
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = onLogout,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) {
            Text("LOGOUT (KELUAR KASIR)", fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}