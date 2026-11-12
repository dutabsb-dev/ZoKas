package com.karyaku.kasir

import android.app.Notification
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class PaymentNotificationListener : NotificationListenerService() {

    companion object {
        // Event Bus sederhana untuk mengirim pesan ke ViewModel/MainActivity
        private val _paymentEvents = MutableSharedFlow<String>()
        val paymentEvents = _paymentEvents.asSharedFlow()

        // Daftar package name aplikasi bank/e-wallet yang umum di Indonesia
        private val PAYMENT_PACKAGES = listOf(
            "com.bca", "com.bca.smartbranch", // BCA
            "id.dana", // DANA
            "com.gojek.merchant", "com.gojek.app", // GoPay
            "com.ovo.id", // OVO
            "com.shopee.id", // ShopeePay
            "com.mandiri.mandirionline", "id.co.bankmandiri.mandiri", // Mandiri
            "com.bni.mobile", // BNI
            "com.bri.mobile", // BRI
            "id.co.bri.brimo"
        )
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        // Cek apakah notifikasi dari aplikasi pembayaran yang di-whitelist
        if (PAYMENT_PACKAGES.contains(sbn.packageName)) {
            val extras = sbn.notification?.extras
            val title = extras?.getString(Notification.EXTRA_TITLE) ?: ""
            val text = extras?.getString(Notification.EXTRA_TEXT) ?: ""
            val bigText = extras?.getString(Notification.EXTRA_BIG_TEXT) ?: ""
            
            val fullText = "$title $text $bigText".lowercase()

            // Log untuk debugging
            Log.d("QRIS_Listener", "Notif dari: ${sbn.packageName} | Isi: $fullText")

            // Cek apakah notifikasi mengandung kata kunci uang masuk
            if (fullText.contains("masuk") || fullText.contains("diterima") || fullText.contains("berhasil")) {
                // Kirim sinyal ke aplikasi bahwa ada potensi pembayaran masuk
                _paymentEvents.tryEmit(fullText)
            }
        }
    }
}