package com.karyaku.kasir

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.karyaku.kasir.data.local.UserEntity

@Composable
fun SetupWizardScreen(settingsManager: SettingsManager, onSetupComplete: (UserEntity) -> Unit) {
    var step by remember { mutableStateOf(1) }
    
    // State Data
    var storeName by remember { mutableStateOf("") }
    var storeAddress by remember { mutableStateOf("") }
    var adminName by remember { mutableStateOf("") }
    var adminPin by remember { mutableStateOf("") }
    var cashierName by remember { mutableStateOf("") }
    var cashierPin by remember { mutableStateOf("") }
    var paymentMethod by remember { mutableStateOf("QRIS") }

    Box(modifier = Modifier.fillMaxSize().imePadding(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("ZoKas Setup", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = EmeraldGreen)
            Text("Step $step of 4", style = MaterialTheme.typography.bodyMedium, color = SlateGrey)
            Spacer(modifier = Modifier.height(32.dp))

            when (step) {
                1 -> {
                    Text("Store Details", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(value = storeName, onValueChange = { storeName = it }, label = { Text("Store Name") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = storeAddress, onValueChange = { storeAddress = it }, label = { Text("Store Address") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = { 
                            settingsManager.storeName = storeName
                            settingsManager.storeAddress = storeAddress
                            step = 2 
                        }, 
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        enabled = storeName.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("Next") }
                }
                2 -> {
                    Text("Admin (Owner) Account", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(value = adminName, onValueChange = { adminName = it }, label = { Text("Admin Name") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = adminPin, onValueChange = { if (it.length <= 6) adminPin = it }, 
                        label = { Text("Admin PIN (6-digit)") }, 
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = { step = 3 }, 
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        enabled = adminName.isNotBlank() && adminPin.length == 6,
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("Next") }
                    TextButton(onClick = { step = 1 }) { Text("Back") }
                }
                3 -> {
                    Text("Cashier Account", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(value = cashierName, onValueChange = { cashierName = it }, label = { Text("Cashier Name") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = cashierPin, onValueChange = { if (it.length <= 6) cashierPin = it }, 
                        label = { Text("Cashier PIN (6-digit)") }, 
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = { step = 4 }, 
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        enabled = cashierName.isNotBlank() && cashierPin.length == 6,
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("Next") }
                    TextButton(onClick = { step = 2 }) { Text("Back") }
                }
                4 -> {
                    Text("Payment Method", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(16.dp))
                    listOf("QRIS", "Virtual Account", "Bank Transfer").forEach { method ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        ) {
                            RadioButton(selected = paymentMethod == method, onClick = { paymentMethod = method }, colors = RadioButtonDefaults.colors(selectedColor = EmeraldGreen))
                            Text(method, fontSize = 16.sp)
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = { 
                            settingsManager.paymentMethod = paymentMethod
                            settingsManager.isSetupCompleted = true
                            val newAdmin = UserEntity(name = adminName, pin = adminPin, role = "OWNER")
                            // Note: Dalam implementasi nyata, Anda harus memanggil ViewModel untuk insert user ke Database
                            // Karena ini Setup Screen, kita asumsikan ViewModel disuntikkan atau kita gunakan callback
                            onSetupComplete(newAdmin) 
                        }, 
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AmberAccent),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("Finish Setup & Login") }
                    TextButton(onClick = { step = 3 }) { Text("Back") }
                }
            }
        }
    }
}