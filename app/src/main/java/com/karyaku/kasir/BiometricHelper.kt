package com.karyaku.kasir

import android.content.Context
import androidx.biometric.BiometricManager

object BiometricHelper {
    /**
     * Mengecek status sensor biometrik di perangkat.
     * Mengembalikan String status:
     * - "SUPPORTED" : Perangkat support dan sudah ada biometrik terdaftar.
     * - "NO_HARDWARE" : Perangkat sama sekali tidak punya sensor.
     * - "HW_UNAVAILABLE" : Sensor ada tapi sedang tidak tersedia/rusak.
     * - "NONE_ENROLLED" : Sensor ada, tapi pengguna belum mendaftarkan sidik jari/wajah di HP.
     */
    fun checkSensorStatus(context: Context): String {
        val biometricManager = BiometricManager.from(context)
        // BiometricManager.Authenticators.BIOMETRIC_WEAK mencakup Face Unlock & Fingerprint
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_WEAK
        
        return when (biometricManager.canAuthenticate(authenticators)) {
            BiometricManager.BIOMETRIC_SUCCESS -> "SUPPORTED"
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> "NO_HARDWARE"
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> "HW_UNAVAILABLE"
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> "NONE_ENROLLED"
            else -> "UNKNOWN"
        }
    }

    /**
     * Mendapatkan pesan teks untuk ditampilkan ke pengguna.
     */
    fun getSensorStatusMessage(status: String): String {
        return when (status) {
            "SUPPORTED" -> "Sensor siap digunakan."
            "NO_HARDWARE" -> "Perangkat Anda tidak memiliki sensor sidik jari atau wajah."
            "HW_UNAVAILABLE" -> "Sensor sidik jari/wajah sedang tidak tersedia."
            "NONE_ENROLLED" -> "Anda belum mendaftarkan sidik jari/wajah di Pengaturan HP."
            else -> "Status sensor tidak diketahui."
        }
    }
}