package com.karyaku.kasir

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karyaku.kasir.data.local.UserEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ====================================================================
// 1. DIALOG ABSENSI (CHECK-IN & CHECK-OUT)
// ====================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttendanceDialog(
    viewModel: PosViewModel,
    currentUser: UserEntity,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val attendanceList by viewModel.attendanceList.collectAsStateWithLifecycle()
    
    val dateFormat = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    val todayStr = dateFormat.format(Date())
    
    val todayAttendance = attendanceList.find { it.userId == currentUser.id && it.date == todayStr }
    
    var selectedTab by remember { mutableStateOf(0) } // 0: Absen Saya, 1: Rekap Hari Ini (Owner)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Absensi Karyawan", fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.fillMaxWidth().heightIn(max = 500.dp)) {
                TabRow(selectedTabIndex = selectedTab, containerColor = MaterialTheme.colorScheme.surface, contentColor = EmeraldGreen) {
                    Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Absen Saya") })
                    if (currentUser.role == "OWNER") {
                        Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Rekap Hari Ini") })
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (selectedTab == 0) {
                    // TAMPILAN ABSEN PRIBADI
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        Text("Halo, ${currentUser.name}", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("Tanggal: $todayStr", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(24.dp))

                        if (todayAttendance == null) {
                            Icon(Icons.Default.Login, contentDescription = null, tint = EmeraldGreen, modifier = Modifier.size(64.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Anda belum absen hari ini.", fontSize = 14.sp)
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = {
                                    viewModel.checkIn(currentUser) { success, msg ->
                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(50.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                                shape = RoundedCornerShape(12.dp)
                            ) { Text("ABSEN MASUK", fontWeight = FontWeight.Bold) }
                        } else if (todayAttendance?.checkOutTime == null) {
                            Icon(Icons.Default.Timer, contentDescription = null, tint = AmberAccent, modifier = Modifier.size(64.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Masuk pada: ${timeFormat.format(Date(todayAttendance!!.checkInTime))}", fontWeight = FontWeight.Bold)
                            Text("Anda sedang bekerja.", fontSize = 12.sp, color = EmeraldGreen)
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = {
                                    viewModel.checkOut(currentUser) { success, msg ->
                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(50.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = ErrorRed),
                                shape = RoundedCornerShape(12.dp)
                            ) { Text("ABSEN PULANG", fontWeight = FontWeight.Bold, color = Color.White) }
                        } else {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = EmeraldGreen, modifier = Modifier.size(64.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Absensi Selesai!", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text("Masuk: ${timeFormat.format(Date(todayAttendance!!.checkInTime))}", fontSize = 14.sp)
                            Text("Pulang: ${timeFormat.format(Date(todayAttendance!!.checkOutTime!!))}", fontSize = 14.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            val durationMs = todayAttendance!!.checkOutTime!! - todayAttendance!!.checkInTime
                            val hours = durationMs / (1000 * 60 * 60)
                            val minutes = (durationMs / (1000 * 60)) % 60
                            Text("Total Jam Kerja: $hours jam $minutes menit", color = EmeraldGreen, fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    // TAMPILAN REKAP HARI INI (OWNER)
                    val todaysRecords = attendanceList.filter { it.date == todayStr }
                    if (todaysRecords.isEmpty()) {
                        Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            Text("Belum ada karyawan yang absen hari ini.", textAlign = TextAlign.Center)
                        }
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(todaysRecords) { record ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(record.userName, fontWeight = FontWeight.Bold)
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("Masuk: ${timeFormat.format(Date(record.checkInTime))}", fontSize = 12.sp)
                                            if (record.checkOutTime != null) {
                                                Text("Pulang: ${timeFormat.format(Date(record.checkOutTime!!))}", fontSize = 12.sp)
                                            } else {
                                                Text("Sedang Bekerja", fontSize = 12.sp, color = EmeraldGreen, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Tutup") }
        }
    )
}

// ====================================================================
// 2. DIALOG MANAJEMEN PAYROLL (GAJI)
// ====================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PayrollManagementDialog(
    viewModel: PosViewModel,
    currentUser: UserEntity,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val users by viewModel.users.collectAsStateWithLifecycle()
    val payrollList by viewModel.payrollList.collectAsStateWithLifecycle()
    
    val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    var selectedTab by remember { mutableStateOf(0) } // 0: Buat Gaji, 1: Riwayat

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Manajemen Gaji (Payroll)", fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.fillMaxWidth().heightIn(max = 500.dp)) {
                TabRow(selectedTabIndex = selectedTab, containerColor = MaterialTheme.colorScheme.surface, contentColor = EmeraldGreen) {
                    Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Karyawan") })
                    Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Riwayat Gaji") })
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (selectedTab == 0) {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val employees = users.filter { it.role != "OWNER" }
                        if (employees.isEmpty()) {
                            item { Text("Belum ada data karyawan.") }
                        }
                        items(employees) { emp ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(emp.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Gaji Pokok:", fontSize = 12.sp)
                                        Text(formatRupiah(emp.baseSalary), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Komisi:", fontSize = 12.sp)
                                        Text(formatRupiah(emp.totalCommission), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = EmeraldGreen)
                                    }
                                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Total Diterima:", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                        Text(formatRupiah(emp.baseSalary + emp.totalCommission), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = AmberAccentDark)
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Button(
                                        onClick = {
                                            val periodStr = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date())
                                            viewModel.generatePayroll(emp, periodStr, true, currentUser) { success, msg ->
                                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                                        shape = RoundedCornerShape(8.dp)
                                    ) { Text("BUAT SLIP GAJI BULAN INI", fontSize = 12.sp) }
                                }
                            }
                        }
                    }
                } else {
                    if (payrollList.isEmpty()) {
                        Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            Text("Belum ada riwayat slip gaji.", textAlign = TextAlign.Center)
                        }
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(payrollList) { payroll ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                            Text(payroll.userName, fontWeight = FontWeight.Bold)
                                            Surface(
                                                color = if (payroll.status == "PAID") EmeraldGreen.copy(alpha = 0.2f) else ErrorRed.copy(alpha = 0.2f),
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    payroll.status,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (payroll.status == "PAID") EmeraldGreen else ErrorRed,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                        Text("Periode: ${payroll.period}", fontSize = 12.sp)
                                        Text("Total: ${formatRupiah(payroll.totalSalary)}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = AmberAccentDark)
                                        Text("Dibuat: ${dateFormat.format(Date(payroll.timestamp))}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        
                                        if (payroll.status == "UNPAID") {
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Button(
                                                onClick = {
                                                    viewModel.payPayroll(payroll.id, currentUser) { success, msg ->
                                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                                    }
                                                },
                                                modifier = Modifier.fillMaxWidth(),
                                                colors = ButtonDefaults.buttonColors(containerColor = AmberAccent),
                                                shape = RoundedCornerShape(8.dp)
                                            ) { Text("TANDAI LUNAS / BAYAR", fontSize = 12.sp, color = Color.White) }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Tutup") }
        }
    )
}