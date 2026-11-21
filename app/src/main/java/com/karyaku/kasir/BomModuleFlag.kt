package com.karyaku.kasir

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private const val BOM_PREFS_NAME = "zokas_modules"
private const val BOM_PREF_KEY = "enable_bom_module"

private fun resolveContext(manager: SettingsManager): Context? {
    return try {
        val field = SettingsManager::class.java.declaredFields.firstOrNull {
            Context::class.java.isAssignableFrom(it.type)
        }?.apply { isAccessible = true }
        field?.get(manager) as? Context
    } catch (_: Exception) {
        null
    }
}

val SettingsManager.isBomModuleEnabled: Boolean
    get() {
        val ctx = resolveContext(this) ?: return false
        return ctx.getSharedPreferences(BOM_PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(BOM_PREF_KEY, false)
    }

fun SettingsManager.setBomModuleEnabled(enabled: Boolean) {
    val ctx = resolveContext(this) ?: return
    ctx.getSharedPreferences(BOM_PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putBoolean(BOM_PREF_KEY, enabled)
        .apply()
}

@Composable
fun BomModuleToggleCard(
    settingsManager: SettingsManager,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.Science,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Modul Resep / BOM",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (checked) "Aktif • Kelola bahan baku & resep produk" else "Nonaktif • Aktifkan untuk memakai BOM",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Switch(
                checked = checked,
                onCheckedChange = { newVal ->
                    settingsManager.setBomModuleEnabled(newVal)
                    context.getSharedPreferences(BOM_PREFS_NAME, Context.MODE_PRIVATE)
                        .edit()
                        .putBoolean(BOM_PREF_KEY, newVal)
                        .apply()
                    onCheckedChange(newVal)
                }
            )
        }
    }
}
