package com.karyaku.kasir

import android.content.Context

val SettingsManager.isBomModuleEnabled: Boolean
    get() {
        return try {
            val ctxField = SettingsManager::class.java.declaredFields.firstOrNull {
                Context::class.java.isAssignableFrom(it.type)
            }?.apply { isAccessible = true }
            val ctx = ctxField?.get(this) as? Context
            if (ctx != null) {
                val prefs = ctx.getSharedPreferences("zokas_modules", Context.MODE_PRIVATE)
                prefs.getBoolean("enable_bom_module", true)
            } else {
                true
            }
        } catch (_: Exception) {
            true
        }
    }
