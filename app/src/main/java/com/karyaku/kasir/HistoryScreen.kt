// (Biarkan import dan bagian HistoryScreen sama persis)

@Composable
fun TransactionDetailDialog(
    transaction: TransactionEntity,
    viewModel: PosViewModel,
    settingsManager: SettingsManager,
    currentUser: UserEntity,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
    
    // PERBAIKAN FATAL: Gunakan remember agar tidak terjadi Infinite Recomposition / Memory Leak
    val itemsFlow = remember(transaction.id) { viewModel.getTransactionItems(transaction.id) }
    val items by itemsFlow.collectAsStateWithLifecycle()
    
    var showReceipt by remember { mutableStateOf(false) }
    var showVoidPinDialog by remember { mutableStateOf(false) }

    if (showReceipt) {
        ReceiptDialog(
            transaction = transaction,
            storeName = if (settingsManager.isPro) settingsManager.storeName else "ZoKas (Gratis)",
            storeFooter = if (settingsManager.isPro) settingsManager.storeFooter else "Terima kasih!",
            settingsManager = settingsManager,
            items = items,
            onDismiss = { showReceipt = false }
        )
    }

    if (showVoidPinDialog) {
        VoidPinDialog(
            onDismiss = { showVoidPinDialog = false },
            onConfirm = { pin ->
                viewModel.voidTransaction(transaction, pin, currentUser) { success, message ->
                    showVoidPinDialog = false
                    if (success) {
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                        onDismiss()
                    } else {
                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                    }
                }
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (transaction.status == "SUCCESS") {
                    Button(
                        onClick = { showVoidPinDialog = true }, 
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                    ) {
                        Text("VOID (BATAL)")
                    }
                }
                
                Button(onClick = { showReceipt = true }, colors = ButtonDefaults.buttonColors(containerColor = ZokasOrange)) {
                    Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("CETAK")
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Tutup") } },
        title = { Text("Rincian Transaksi #${transaction.id}") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (transaction.status == "VOIDED") {
                    Text("TRANSAKSI INI TELAH DIBATALKAN (VOID)", color = Color.Red, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
                }
                Text("Tanggal: ${dateFormat.format(Date(transaction.timestamp))}", fontSize = 12.sp)
                Text("Pelanggan: ${transaction.customerName}", fontSize = 12.sp)
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                
                if (items.isEmpty()) {
                    Text("Memuat rincian...", fontSize = 12.sp, color = Color.Gray)
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 250.dp)) {
                        items(items) { item ->
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(item.productName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text("${item.quantity} x ${formatRupiah(item.price)}", fontSize = 12.sp, color = Color.Gray)
                                }
                                Text(formatRupiah(item.subtotal), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                        }
                    }
                }
                
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Total:", fontWeight = FontWeight.Bold)
                    Text(formatRupiah(transaction.totalPrice), fontWeight = FontWeight.Bold, color = if (transaction.status == "VOIDED") Color.Gray else ZokasOrange)
                }
            }
        }
    )
}