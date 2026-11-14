package com.karyaku.kasir

import androidx.lifecycle.viewModelScope
import com.karyaku.kasir.data.local.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ====================================================================
// MODUL C — ABSENSI & PAYROLL
// ====================================================================

// ================================================================
// ABSENSI (CHECK-IN & CHECK-OUT)
// ================================================================
fun PosViewModel.checkIn(user: UserEntity, onResult: (Boolean, String) -> Unit) {
    viewModelScope.launch(Dispatchers.IO) {
        try {
            val dateFormat = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
            val todayStr = dateFormat.format(Date())
            
            val existing = attendanceDao.getTodayAttendance(user.id, todayStr)
            if (existing != null) {
                withContext(Dispatchers.Main) { onResult(false, "Anda sudah absen masuk hari ini.") }
                return@launch
            }

            val attendance = AttendanceEntity(
                userId = user.id,
                userName = user.name,
                date = todayStr,
                checkInTime = System.currentTimeMillis(),
                branchId = _currentBranchId.value
            )
            attendanceDao.insertCheckIn(attendance)
            
            userDao.insertAuditLog(AuditLogEntity(
                userId = user.id, userName = user.name,
                action = "ABSEN MASUK (Check-In)",
                actionType = "ATTENDANCE"
            ))
            
            withContext(Dispatchers.Main) { onResult(true, "Absen masuk berhasil pada ${SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())}.") }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) { onResult(false, "Gagal absen masuk: ${e.message}") }
        }
    }
}

fun PosViewModel.checkOut(user: UserEntity, onResult: (Boolean, String) -> Unit) {
    viewModelScope.launch(Dispatchers.IO) {
        try {
            val dateFormat = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
            val todayStr = dateFormat.format(Date())
            
            val existing = attendanceDao.getTodayAttendance(user.id, todayStr)
            if (existing == null) {
                withContext(Dispatchers.Main) { onResult(false, "Anda belum absen masuk hari ini.") }
                return@launch
            }
            if (existing.checkOutTime != null) {
                withContext(Dispatchers.Main) { onResult(false, "Anda sudah absen pulang hari ini.") }
                return@launch
            }

            attendanceDao.updateCheckOut(user.id, todayStr, System.currentTimeMillis())
            
            userDao.insertAuditLog(AuditLogEntity(
                userId = user.id, userName = user.name,
                action = "ABSEN PULANG (Check-Out)",
                actionType = "ATTENDANCE"
            ))
            
            withContext(Dispatchers.Main) { onResult(true, "Absen pulang berhasil pada ${SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())}.") }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) { onResult(false, "Gagal absen pulang: ${e.message}") }
        }
    }
}

// ================================================================
// PAYROLL (GAJI KARYAWAN)
// ================================================================
fun PosViewModel.updateUserBaseSalary(userId: Long, newSalary: Long, adminUser: UserEntity, onResult: (Boolean, String) -> Unit) {
    viewModelScope.launch(Dispatchers.IO) {
        try {
            userDao.updateBaseSalary(userId, newSalary)
            userDao.insertAuditLog(AuditLogEntity(
                userId = adminUser.id, userName = adminUser.name,
                action = "UPDATE GAJI POKOK: Karyawan ID #$userId menjadi ${formatRupiah(newSalary)}",
                actionType = "PAYROLL"
            ))
            withContext(Dispatchers.Main) { onResult(true, "Gaji pokok berhasil diperbarui.") }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) { onResult(false, "Gagal update gaji: ${e.message}") }
        }
    }
}

fun PosViewModel.generatePayroll(
    user: UserEntity, 
    period: String, 
    resetCommission: Boolean,
    adminUser: UserEntity,
    onResult: (Boolean, String) -> Unit
) {
    viewModelScope.launch(Dispatchers.IO) {
        try {
            val baseSalary = user.baseSalary
            val commission = user.totalCommission
            val totalSalary = baseSalary + commission

            if (totalSalary <= 0L) {
                withContext(Dispatchers.Main) { onResult(false, "Total gaji 0. Pastikan Gaji Pokok atau Komisi terisi.") }
                return@launch
            }

            val payroll = PayrollEntity(
                userId = user.id,
                userName = user.name,
                period = period,
                baseSalary = baseSalary,
                commission = commission,
                totalSalary = totalSalary,
                status = "UNPAID",
                branchId = _currentBranchId.value
            )
            payrollDao.insertPayroll(payroll)

            if (resetCommission) {
                userDao.updateUser(user.copy(totalCommission = 0L))
            }

            userDao.insertAuditLog(AuditLogEntity(
                userId = adminUser.id, userName = adminUser.name,
                action = "BUAT SLIP GAJI: ${user.name} periode $period. Total: ${formatRupiah(totalSalary)}",
                actionType = "PAYROLL"
            ))

            withContext(Dispatchers.Main) { onResult(true, "Slip gaji berhasil dibuat.") }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) { onResult(false, "Gagal buat slip gaji: ${e.message}") }
        }
    }
}

fun PosViewModel.payPayroll(payrollId: Long, adminUser: UserEntity, onResult: (Boolean, String) -> Unit) {
    viewModelScope.launch(Dispatchers.IO) {
        try {
            payrollDao.markAsPaid(payrollId)
            userDao.insertAuditLog(AuditLogEntity(
                userId = adminUser.id, userName = adminUser.name,
                action = "BAYAR GAJI: Slip Gaji #$payrollId telah dibayarkan.",
                actionType = "PAYROLL"
            ))
            withContext(Dispatchers.Main) { onResult(true, "Gaji berhasil ditandai LUNAS.") }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) { onResult(false, "Gagal konfirmasi gaji: ${e.message}") }
        }
    }
}