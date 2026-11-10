package com.karyaku.kasir

import androidx.compose.foundation.clickable
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
fun SetupWizardScreen(
    settingsManager: SettingsManager, 
    viewModel: PosViewModel, 
    onSetupComplete: (UserEntity) -> Unit
) {
    var step by remember { mutableStateOf(1) }
    
    // State Data
    var storeName by remember { mutableStateOf("") }
    var storeAddress by remember { mutableStateOf("") }
    var adminName by remember { mutableStateOf("") }
    var adminPin by remember { mutableStateOf("") }
    var cashierName by remember { mutableStateOf("") }
    var cashierPin by remember { mutableStateOf("") }
    
    // State Multi Payment
    var selectedMethods by remember { mutableStateOf(setOf("CASH")) }
    
    // State Loading
    var isLoading by remember { mutableStateOf(false) }

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
                    Text("Payment Methods", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Select one or more methods", fontSize = 12.sp, color = SlateGrey)
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    val methods = listOf("CASH", "QRIS", "Virtual Account", "Bank Transfer")
                    methods.forEach { method ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { 
                                    selectedMethods = if (selectedMethods.contains(method)) {
                                        selectedMethods - method
                                    } else {
                                        selectedMethods + method
                                    }
                                }
                                .padding(vertical = 4.dp)
                        ) {
                            Checkbox(
                                checked = selectedMethods.contains(method), 
                                onCheckedChange = { isChecked ->
                                    selectedMethods = if (isChecked) {
                                        selectedMethods + method
                                    } else {
                                        selectedMethods - method
                                    }
                                }, 
                                colors = CheckboxDefaults.colors(checkedColor = EmeraldGreen)
                            )
                            Text(method, fontSize = 16.sp)
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = { 
                            if (selectedMethods.isEmpty()) return@Button
                            
                            isLoading = true
                            settingsManager.paymentMethods = selectedMethods
                            
                            // SIMPAN USER KE DATABASE
                            viewModel.registerInitialSetup(
                                adminName = adminName,
                                adminPin = adminPin,
                                cashierName = cashierName,
                                cashierPin = cashierPin
                            ) { newAdmin ->
                                // JIKA SUKSES, BARU PINDAH LAYAR
                                isLoading = false
                                settingsManager.isSetupCompleted = true
                                onSetupComplete(newAdmin)
                            }
                        }, 
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        enabled = selectedMethods.isNotEmpty() && !isLoading,
                        colors = ButtonDefaults.buttonColors(containerColor = AmberAccent),
                        shape = RoundedCornerShape(12.dp)
                    ) { 
                        if (isLoading) {
                            CircularProgressIndicator(color = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        } else {
                            Text("Finish Setup & Login") 
                        }
                    }
                    TextButton(onClick = { if (!isLoading) step = 3 }) { Text("Back") }
                }
            }
        }
    }
}