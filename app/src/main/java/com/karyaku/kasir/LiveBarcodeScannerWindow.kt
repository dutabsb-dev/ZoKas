package com.karyaku.kasir

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import com.journeyapps.barcodescanner.DefaultDecoderFactory
import com.journeyapps.barcodescanner.camera.CameraSettings

@Composable
fun LiveBarcodeScannerWindow(
    enabled: Boolean,
    onToggleEnabled: (Boolean) -> Unit,
    onBarcodeDetected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var lastCode by remember { mutableStateOf("") }
    var lastScanMillis by remember { mutableLongStateOf(0L) }
    var statusText by remember { mutableStateOf("Kamera siap") }

    val onBarcodeDetectedState by rememberUpdatedState(onBarcodeDetected)

    val barcodeView = remember {
        DecoratedBarcodeView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            decoderFactory = DefaultDecoderFactory()
            cameraSettings = CameraSettings().apply {
                requestedCameraId = 0
                isAutoFocusEnabled = true
            }
            setStatusText("")
            viewFinder?.visibility = android.view.View.GONE
        }
    }

    LaunchedEffect(enabled) {
        if (enabled) {
            statusText = "Scan aktif"
            barcodeView.resume()
            barcodeView.decodeContinuous(object : BarcodeCallback {
                override fun barcodeResult(result: BarcodeResult?) {
                    val raw = result?.text?.trim().orEmpty()
                    if (raw.isEmpty()) return
                    val now = System.currentTimeMillis()
                    if (raw == lastCode && (now - lastScanMillis) < 1200L) return
                    if ((now - lastScanMillis) < 550L) return
                    lastCode = raw
                    lastScanMillis = now
                    statusText = "Terbaca: $raw"
                    onBarcodeDetectedState(raw)
                }
            })
        } else {
            statusText = "Dijeda"
            barcodeView.pause()
        }
    }

    DisposableEffect(lifecycleOwner, enabled) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> if (enabled) barcodeView.resume()
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> barcodeView.pause()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            barcodeView.pause()
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(
            1.dp,
            if (enabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
            else MaterialTheme.colorScheme.outlineVariant
        ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
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
                        imageVector = Icons.Default.QrCodeScanner,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Scan Kamera ($statusText)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                TextButton(
                    onClick = { onToggleEnabled(!enabled) },
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                    modifier = Modifier.height(26.dp)
                ) {
                    Icon(
                        imageVector = if (enabled) Icons.Default.VisibilityOff else Icons.Default.CameraAlt,
                        contentDescription = null,
                        modifier = Modifier.size(13.dp),
                        tint = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = if (enabled) "Matikan" else "Nyalakan",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (enabled) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(82.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black)
                ) {
                    AndroidView(
                        factory = { barcodeView },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}
