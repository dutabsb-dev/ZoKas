package com.karyaku.kasir

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karyaku.kasir.data.local.AppDatabase
import com.karyaku.kasir.data.local.ProductEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Inisialisasi Database
        val database = AppDatabase.getDatabase(this)
        val productDao = database.productDao()
        val settingsManager = SettingsManager(this)

        setContent {
            var isDarkMode by remember { mutableStateOf(settingsManager.isDarkMode) }
            var language by remember { mutableStateOf(settingsManager.language) }

            PosTheme(isDarkMode = isDarkMode, language = language) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // Memanggil layar uji coba database
                    TestDatabaseScreen(
                        productDao = productDao,
                        isDarkMode = isDarkMode,
                        onToggleDarkMode = {
                            isDarkMode = !isDarkMode
                            settingsManager.isDarkMode = isDarkMode
                        },
                        language = language,
                        onToggleLanguage = {
                            val newLang = if (language == "in") "en" else "in"
                            language = newLang
                            settingsManager.language = newLang
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TestDatabaseScreen(
    productDao: com.karyaku.kasir.data.local.ProductDao,
    isDarkMode: Boolean,
    onToggleDarkMode: () -> Unit,
    language: String,
    onToggleLanguage: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Mengambil data dari database secara reaktif (Flow)
    val productList by productDao.getAllProducts().collectAsStateWithLifecycle(initialValue = emptyList())

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp)
    ) {
        // Tampilkan Status Pengaturan
        Text(
            text = "Bahasa: $language | Dark Mode: $isDarkMode",
            style = MaterialTheme.typography.bodyMedium
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onToggleDarkMode) { Text("Tema") }
            Button(onClick = onToggleLanguage) { Text("Bahasa") }
        }

        Divider(modifier = Modifier.padding(vertical = 16.dp))

        // Tombol untuk menyimpan data ke database SQLite
        Button(
            onClick = {
                scope.launch(Dispatchers.IO) {
                    val dummyProduct = ProductEntity(
                        name = "Kopi Susu ${(1..100).random()}",
                        price = 18000,
                        stock = 50
                    )
                    productDao.insertProduct(dummyProduct)
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("+ Tambah Produk Dummy ke Database")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Daftar Produk (Offline DB):",
            style = MaterialTheme.typography.titleMedium
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Menampilkan List Produk
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(productList) { product ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(text = product.name, style = MaterialTheme.typography.titleMedium)
                        Text(text = "Harga: Rp ${product.price}", style = MaterialTheme.typography.bodyMedium)
                        Text(text = "Stok: ${product.stock}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}