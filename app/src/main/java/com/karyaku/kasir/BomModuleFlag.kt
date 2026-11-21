package com.karyaku.kasir

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Inventory2
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

fun isBomEnabledInContext(context: Context): Boolean {
    return context.getSharedPreferences(BOM_PREFS_NAME, Context.MODE_PRIVATE)
        .getBoolean(BOM_PREF_KEY, false)
}

fun setBomEnabledInContext(context: Context, enabled: Boolean) {
    context.getSharedPreferences(BOM_PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putBoolean(BOM_PREF_KEY, enabled)
        .apply()
}

val SettingsManager.isBomModuleEnabled: Boolean
    get() {
        val ctx = resolveContext(this) ?: return false
        return isBomEnabledInContext(ctx)
    }

fun SettingsManager.setBomModuleEnabled(enabled: Boolean) {
    val ctx = resolveContext(this) ?: return
    setBomEnabledInContext(ctx, enabled)
}

@Composable
fun BomModuleCombinedRow(
    enabled: Boolean,
    onToggleEnabled: (Boolean) -> Unit,
    onManageRawMaterialClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
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
                        text = "Resep & Bahan Baku (BOM)",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (enabled) "Aktif • Kelola resep & bahan baku produk" else "Nonaktif • Aktifkan untuk memakai resep & bahan baku",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Switch(
                checked = enabled,
                onCheckedChange = { newVal ->
                    setBomEnabledInContext(context, newVal)
                    onToggleEnabled(newVal)
                }
            )
        }

        if (enabled) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onManageRawMaterialClick() },
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Inventory2,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Kelola Bahan Baku",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Atur stok bahan mentah & resep (BOM)",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
