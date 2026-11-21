package com.karyaku.kasir

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import coil.compose.AsyncImage
import com.karyaku.kasir.data.local.UserEntity
import java.io.File
import java.io.FileOutputStream

import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

@Composable
fun SettingsSectionLeadingIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color = MaterialTheme.colorScheme.primary
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .background(tint.copy(alpha = 0.15f), RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
fun SettingsModuleCard(
    isTableModeEnabled: Boolean, onTableModeToggle: (Boolean) -> Unit,
    enableMultiCurrency: Boolean, onMultiCurrencyToggle: (Boolean) -> Unit,
    exchangeRateUsd: String, onExchangeRateChange: (String) -> Unit,
    onRawMaterialClick: () -> Unit,
    enableOrderTypes: Boolean, onOrderTypesToggle: (Boolean) -> Unit,
    enableWholesale: Boolean, onWholesaleToggle: (Boolean) -> Unit,
    enablePreOrder: Boolean, onPreOrderToggle: (Boolean) -> Unit,
    enableMembership: Boolean, onMembershipToggle: (Boolean) -> Unit,
    onManageMembershipClick: () -> Unit,
    enableSupplier: Boolean, onSupplierToggle: (Boolean) -> Unit,
    onManageSupplierClick: () -> Unit,
    onCreatePOClick: () -> Unit,
    enableReservation: Boolean, onReservationToggle: (Boolean) -> Unit,
    onManageReservationClick: () -> Unit,
    enableFinance: Boolean, onFinanceToggle: (Boolean) -> Unit,
    onManageReceivableClick: () -> Unit,
    onManagePayableClick: () -> Unit,
    onManageExpenseClick: () -> Unit,
    enableHrModule: Boolean, onHrModuleToggle: (Boolean) -> Unit,
    enableCrmModule: Boolean, onCrmModuleToggle: (Boolean) -> Unit,
    enableBomModule: Boolean = false, onBomModuleToggle: (Boolean) -> Unit = {},
    isOwner: Boolean = false,
    currentBranchName: String = "-",
    totalBranches: Int = 1,
    onManageBranchesClick: () -> Unit = {},
    onTransferStockClick: () -> Unit = {},
    hhStart: String = "17:00",
    onHhStartChange: (String) -> Unit = {},
    hhEnd: String = "19:00",
    onHhEndChange: (String) -> Unit = {},
    hhDiscount: String = "0",
    onHhDiscountChange: (String) -> Unit = {},
    maxDiscountCap: String = "0",
    onMaxDiscountCapChange: (String) -> Unit = {},
    language: String
) {
    var isExpanded by remember { mutableStateOf(false) }
    val lang = language

    val switchColors = SwitchDefaults.colors(
        checkedTrackColor = MaterialTheme.colorScheme.primary,
        uncheckedTrackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { isExpanded = !isExpanded }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    SettingsSectionLeadingIcon(Icons.Default.Widgets, MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            LanguageManager.tr("modul_toko", lang),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            LanguageManager.tr("modul_toko_desc", lang),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = "Toggle",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            AnimatedVisibility(visible = isExpanded, enter = expandVertically(), exit = shrinkVertically()) {
                Column(modifier = Modifier.padding(bottom = 16.dp)) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onBomModuleToggle(!enableBomModule) }.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Science,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Resep & Bahan Baku (BOM)",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "Kelola stok bahan mentah & resep produk.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = enableBomModule, onCheckedChange = onBomModuleToggle, colors = switchColors)
                    }

                    if (enableBomModule) {
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { onRawMaterialClick() }.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Inventory,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Kelola Bahan Baku",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    "Atur stok bahan mentah & resep (BOM).",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(
                                Icons.Default.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}
