package com.karyaku.kasir

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.karyaku.kasir.data.local.CashFlowEntity
import com.karyaku.kasir.data.local.ShiftEntity
import com.karyaku.kasir.data.local.TransactionEntity

@Composable
fun CloseShiftWizardDialog(
    shift: ShiftEntity,
    transactions: List<TransactionEntity>,
    cashFlows: List<CashFlowEntity>,
    onDismiss: () -> Unit,
    onConfirm: (Long, String) -> Unit
) {
    var step by remember { mutableStateOf(1) }
    var physicalCashInput by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }

    val physicalCash = physicalCashInput.toLongOrNull() ?: 0L

    // ================================================================
    // PHASE 0 — SESI 2: KALKULASI KAS SHIFT
    // ================================================================
    // Masalah lama: totalPenjualan menjumlahkan SEMUA transaksi SUCCESS
    // (termasuk QRIS, transfer, BON) → expectedCash membengkak dan
    // selisih kas tampak minus padahal laci cocok.
    //
    // Perbaikan:
    //   - Hanya hitung transaksi dengan paymentMethod == "CASH".
    //   - Pelunasan BON otomatis masuk lewat cash_flows (type=IN) yang
    //     dicatat oleh PosViewModel.payUnpaidTransaction.
    //   - QRIS/Transfer/BON-unpaid tidak masuk perhitungan laci.
    // ================================================================
    val totalPenjualan = transactions.filter { 
        it.timestamp >= shift.startTime 
        && it.status == "SUCCESS" 
        && it.paymentMethod == "CASH"
    }.sumOf { it.totalPrice }
    
    val totalKasKeluar = cashFlows.filter { it.shiftId == shift.id && it.type == "OUT" }.sumOf { it.amount }
    val totalKasMasuk = cashFlows.filter { it.shiftId == shift.id && it.type == "IN" }.sumOf { it.amount }
    val expectedCash = shift.openingCash + totalPenjualan + totalKasMasuk - totalKasKeluar
    val discrepancy = physicalCash - expectedCash
    val isDiscrepancy = discrepancy != 0L

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tutup Kasir (Langkah $step dari 3)", fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                when (step) {
                    1 -> {
                        Text("Masukkan total uang fisik yang ada di dalam laci kasir saat ini.", fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedTextField(
                            value = physicalCashInput,
                            onValueChange = { if (it.length <= 12) physicalCashInput = it },
                            label = { Text("Uang Fisik (Rp)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                    2 -> {
                        Text("Sistem telah menghitung total uang yang seharusnya ada.", fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween) {
                            Text("Modal Awal:"); Text(formatRupiah(shift.openingCash))
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween) {
                            Text("Penjualan Tunai:"); Text(formatRupiah(totalPenjualan))
                        }
                        if (totalKasMasuk > 0) Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween) {
                            Text("Kas Masuk:"); Text(formatRupiah(totalKasMasuk))
                        }
                        if (totalKasKeluar > 0) Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween) {
                            Text("Kas Keluar:"); Text("- ${formatRupiah(totalKasKeluar)}")
                        }
                        
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween) {
                            Text("Kas Sistem:", fontWeight = FontWeight.Bold); Text(formatRupiah(expectedCash), fontWeight = FontWeight.Bold, color = EmeraldGreen)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween) {
                            Text("Kas Fisik:", fontWeight = FontWeight.Bold); Text(formatRupiah(physicalCash), fontWeight = FontWeight.Bold)
                        }
                        
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween) {
                            Text("Selisih:", fontWeight = FontWeight.Bold)
                            Text(
                                formatRupiah(discrepancy), 
                                fontWeight = FontWeight.ExtraBold,
                                color = if (discrepancy < 0) ErrorRed else if (discrepancy > 0) AmberAccent else EmeraldGreen
                            )
                        }
                    }
                    3 -> {
                        Text("Terdapat selisih uang sebesar ${formatRupiah(discrepancy)}.", fontSize = 14.sp, color = ErrorRed, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Wajib memberikan catatan alasan selisih (misal: uang kembalian kurang, tip, dll).", fontSize = 12.sp, color = SlateGrey)
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedTextField(
                            value = note,
                            onValueChange = { note = it },
                            label = { Text("Catatan Selisih") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            when (step) {
                1 -> Button(
                    onClick = { step = 2 }, 
                    enabled = physicalCashInput.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("Lanjut ke Kalkulasi") }
                
                2 -> Button(
                    onClick = { 
                        if (isDiscrepancy) step = 3 
                        else onConfirm(physicalCash, "Tidak ada selisih") 
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                    shape = RoundedCornerShape(12.dp)
                ) { Text(if (isDiscrepancy) "Lanjut" else "Tutup Kasir") }
                
                3 -> Button(
                    onClick = { onConfirm(physicalCash, note) },
                    enabled = note.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("Selesaikan & Tutup Kasir") }
            }
        },
        dismissButton = {
            if (step > 1) {
                TextButton(onClick = { step-- }) { Text("Kembali", color = SlateGrey) }
            } else {
                TextButton(onClick = onDismiss) { Text("Batal", color = SlateGrey) }
            }
        }
    )
}