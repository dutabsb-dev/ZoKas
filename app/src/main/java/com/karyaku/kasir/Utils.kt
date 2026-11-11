package com.karyaku.kasir

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.staticCompositionLocalOf

// KUNCI UTAMA: Menyediakan Activity secara aman di seluruh Compose
val LocalActivity = staticCompositionLocalOf<Activity> {
    error("No Activity provided in CompositionLocalProvider")
}

// Fungsi lama (opsional, untuk berjaga-jaga jika HistoryScreen membutuhkannya)
fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}