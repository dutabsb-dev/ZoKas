package com.karyaku.kasir

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

// Data class untuk membawa hasil ekstraksi notifikasi
data class PaymentNotificationEvent(
    val rawText: String,
    val packageName: String,
    val extractedAmount: Long
)

class PaymentNotificationListener : NotificationListenerService() {

    companion object {
        private val _paymentEvents = MutableSharedFlow<PaymentNotificationEvent>()
        val paymentEvents = _paymentEvents.asSharedFlow()

        private val PAYMENT_PACKAGES = listOf(
            "com.bca", "com.bca.smartbranch",
            "id.dana",
            "com.gojek.merchant", "com.gojek.app",
            "com.ovo.id",
            "com.shopee.id",
            "com.mandiri.mandirionline", "id.co.bankmandiri.mandiri",
            "com.bni.mobile",
            "com.bri.mobile", "id.co.bri.brimo"
        )
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (PAYMENT_PACKAGES.contains(sbn.packageName)) {
            val extras = sbn.notification?.extras
            val title = extras?.getString(Notification.EXTRA_TITLE) ?: ""
            val text = extras?.getString(Notification.EXTRA_TEXT) ?: ""
            val bigText = extras?.getString(Notification.EXTRA_BIG_TEXT) ?: ""
            
            val fullText = "$title $text $bigText".lowercase()
            Log.d("QRIS_Listener", "Notif dari: ${sbn.packageName} | Isi: $fullText")

            // Cek kata kunci uang masuk
            if (fullText.contains("masuk") || fullText.contains("diterima") || fullText.contains("berhasil")) {
                // Ekstrak nominal uang menggunakan Regex (Mencari pola angka setelah Rp)
                val amountRegex = Regex("""(?:rp\.?\s?)([\d\.]+)""", RegexOption.IGNORE_CASE)
                val match = amountRegex.find(fullText)
                
                if (match != null) {
                    // Hapus titik pemisah ribuan (misal 50.000 menjadi 50000) lalu konversi ke Long
                    val amountStr = match.groupValues[1].replace(".", "")
                    val amount = amountStr.toLongOrNull() ?: 0L
                    
                    if (amount > 0) {
                        _paymentEvents.tryEmit(
                            PaymentNotificationEvent(
                                rawText = fullText,
                                packageName = sbn.packageName,
                                extractedAmount = amount
                            )
                        )
                    }
                }
            }
        }
    }
}