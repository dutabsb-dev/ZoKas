package com.karyaku.kasir

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.karyaku.kasir.data.local.UserEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URL
import java.text.NumberFormat
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

data class CfdDisplayItem(
    val productId: Long = 0L,
    val name: String,
    val variation: String,
    val quantity: Int,
    val unitPrice: Long,
    val subtotal: Long,
    val notes: String,
    val imagePath: String = ""
)

data class CfdLiveState(
    val storeName: String = "ZoKas Store",
    val storeAddress: String = "",
    val storeFooter: String = "Terima Kasih atas Kunjungan Anda",
    val cashierName: String = "",
    val customerName: String = "Pelanggan Umum",
    val orderType: String = "Dine-In",
    val items: List<CfdDisplayItem> = emptyList(),
    val subtotal: Long = 0L,
    val discount: Long = 0L,
    val voucherDiscount: Long = 0L,
    val taxAmount: Long = 0L,
    val serviceCharge: Long = 0L,
    val roundingDiff: Long = 0L,
    val totalPrice: Long = 0L,
    val isTaxInclusive: Boolean = false,
    // Mode tampilan CFD: "CART" | "PAYMENT" | "COMPLETED"
    val displayStage: String = "CART",
    val paymentMethod: String = "CASH",
    val amountToPay: Long = 0L,
    val qrisPayload: String = "",
    val bankName: String = "",
    val bankAccountNumber: String = "",
    val bankAccountName: String = "",
    val bankInfo: String = "",
    val cashReceived: Long = 0L,
    val changeAmount: Long = 0L,
    val thankYouMessage: String = "Terima Kasih atas pembeliannya 🙏",
    val completedTimestamp: Long = 0L,
    val isCfdActive: Boolean = true,
    val updatedAt: Long = System.currentTimeMillis()
)

object LocalCfdServerManager {
    private const val DEFAULT_PORT = 8765
    private const val PREFS_CFD = "zokas_wireless_cfd_prefs"
    const val P2P_NEARBY_URI = "p2p://zokas-nearby"

    private val isRunningFlag = AtomicBoolean(false)
    private var serverSocket: ServerSocket? = null
    private var serverThread: Thread? = null

    private val productImageMap = ConcurrentHashMap<Long, String>()
    private val thumbnailCache = ConcurrentHashMap<String, ByteArray>()

    @Volatile
    var currentState: CfdLiveState = CfdLiveState()
        private set

    @Volatile
    private var completedHoldUntil: Long = 0L

    var isServerRunning by mutableStateOf(false)
        private set

    var activePort by mutableStateOf(DEFAULT_PORT)
        private set

    var lastClientPingTime by mutableStateOf(0L)
        private set

    var isKioskReceiverLocked by mutableStateOf(false)
        private set

    var connectedHostUrl by mutableStateOf("")
        private set

    fun getCustomThankYouMessage(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_CFD, Context.MODE_PRIVATE)
        return prefs.getString("cfd_thank_you_msg", "Terima Kasih atas pembeliannya 🙏")
            ?: "Terima Kasih atas pembeliannya 🙏"
    }

    fun setCustomThankYouMessage(context: Context, message: String) {
        val prefs = context.getSharedPreferences(PREFS_CFD, Context.MODE_PRIVATE)
        prefs.edit().putString("cfd_thank_you_msg", message).apply()
        currentState = currentState.copy(
            thankYouMessage = message.ifBlank { "Terima Kasih atas pembeliannya 🙏" },
            updatedAt = System.currentTimeMillis()
        )
    }

    fun restoreReceiverLockState(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_CFD, Context.MODE_PRIVATE)
        val locked = prefs.getBoolean("kiosk_receiver_locked", false)
        val url = prefs.getString("connected_host_url", "") ?: ""
        if (locked && url.isNotBlank()) {
            connectedHostUrl = url
            isKioskReceiverLocked = true
        }
    }

    fun lockAsKioskReceiver(context: Context, hostUrl: String) {
        val cleanUrl = hostUrl.trim().trimEnd('/')
        val normalizedUrl = if (cleanUrl.startsWith("http://") || cleanUrl.startsWith("https://")) {
            cleanUrl
        } else {
            "http://$cleanUrl"
        }
        val prefs = context.getSharedPreferences(PREFS_CFD, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean("kiosk_receiver_locked", true)
            .putString("connected_host_url", normalizedUrl)
            .apply()
        connectedHostUrl = normalizedUrl
        isKioskReceiverLocked = true
    }

    fun unlockKioskReceiver(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_CFD, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean("kiosk_receiver_locked", false)
            .remove("connected_host_url")
            .apply()
        isKioskReceiverLocked = false
        connectedHostUrl = ""
    }

    fun updateState(newState: CfdLiveState) {
        newState.items.forEach { item ->
            if (item.productId > 0L && item.imagePath.isNotBlank()) {
                productImageMap[item.productId] = item.imagePath
            }
        }
        val now = System.currentTimeMillis()
        if (now < completedHoldUntil && newState.items.isEmpty() && newState.displayStage == "CART") {
            currentState = currentState.copy(
                storeName = newState.storeName,
                storeAddress = newState.storeAddress,
                storeFooter = newState.storeFooter,
                thankYouMessage = newState.thankYouMessage,
                isCfdActive = isRunningFlag.get(),
                updatedAt = now
            )
            return
        }
        if (newState.items.isNotEmpty()) {
            completedHoldUntil = 0L
            if (currentState.displayStage == "COMPLETED") {
                currentState = newState.copy(
                    displayStage = "CART",
                    isCfdActive = isRunningFlag.get(),
                    updatedAt = now
                )
                return
            }
        }
        currentState = newState.copy(
            isCfdActive = isRunningFlag.get(),
            updatedAt = now
        )
    }

    fun updatePaymentStage(
        isPaymentOpen: Boolean,
        method: String = "CASH",
        amountToPay: Long = 0L,
        cashReceived: Long = 0L,
        changeAmount: Long = 0L,
        qrisPayload: String = "",
        bankName: String = "",
        bankAccountNumber: String = "",
        bankAccountName: String = ""
    ) {
        val now = System.currentTimeMillis()
        if (!isPaymentOpen && now < completedHoldUntil) {
            return
        }
        if (isPaymentOpen) {
            completedHoldUntil = 0L
        }
        currentState = currentState.copy(
            displayStage = if (isPaymentOpen) "PAYMENT" else "CART",
            paymentMethod = method,
            amountToPay = amountToPay,
            cashReceived = cashReceived,
            changeAmount = changeAmount,
            qrisPayload = qrisPayload,
            bankName = bankName,
            bankAccountNumber = bankAccountNumber,
            bankAccountName = bankAccountName,
            isCfdActive = isRunningFlag.get(),
            updatedAt = now
        )
    }

    fun triggerCompletedStage(
        totalPaid: Long,
        cashReceived: Long,
        changeAmount: Long,
        method: String,
        thankYouMessage: String
    ) {
        val now = System.currentTimeMillis()
        completedHoldUntil = now + 5000L
        currentState = currentState.copy(
            displayStage = "COMPLETED",
            amountToPay = totalPaid,
            cashReceived = cashReceived,
            changeAmount = changeAmount,
            paymentMethod = method,
            thankYouMessage = thankYouMessage.ifBlank { "Terima Kasih atas pembeliannya 🙏" },
            completedTimestamp = now,
            isCfdActive = isRunningFlag.get(),
            updatedAt = now
        )
    }

    fun getLocalIpAddress(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return "127.0.0.1"
            var fallbackIp: String? = null
            while (interfaces.hasMoreElements()) {
                val intf = interfaces.nextElement()
                if (!intf.isUp || intf.isLoopback) continue
                val addrs = intf.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        val host = addr.hostAddress ?: continue
                        val name = intf.name.lowercase()
                        if (name.contains("wlan") || name.contains("ap") || name.contains("swlan")) {
                            return host
                        }
                        if (fallbackIp == null) fallbackIp = host
                    }
                }
            }
            return fallbackIp ?: "127.0.0.1"
        } catch (_: Exception) {
            return "127.0.0.1"
        }
    }

    fun getServerUrl(): String {
        return "http://${getLocalIpAddress()}:$activePort"
    }

    @Synchronized
    fun startServer() {
        if (isRunningFlag.get()) return
        try {
            var boundSocket: ServerSocket? = null
            var chosenPort = DEFAULT_PORT
            for (p in DEFAULT_PORT..(DEFAULT_PORT + 10)) {
                try {
                    boundSocket = ServerSocket(p).apply { reuseAddress = true }
                    chosenPort = p
                    break
                } catch (_: Exception) {
                }
            }
            val socket = boundSocket ?: return
            serverSocket = socket
            activePort = chosenPort
            isRunningFlag.set(true)
            isServerRunning = true
            currentState = currentState.copy(isCfdActive = true, updatedAt = System.currentTimeMillis())

            serverThread = Thread {
                while (isRunningFlag.get() && !socket.isClosed) {
                    try {
                        val client = socket.accept()
                        handleClient(client)
                    } catch (_: Exception) {
                    }
                }
            }.apply {
                isDaemon = true
                name = "ZoKas-Local-CFD-Server"
                start()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            isRunningFlag.set(false)
            isServerRunning = false
        }
    }

    @Synchronized
    fun stopServer() {
        currentState = currentState.copy(isCfdActive = false, updatedAt = System.currentTimeMillis())
        isRunningFlag.set(false)
        isServerRunning = false
        try {
            serverSocket?.close()
        } catch (_: Exception) {
        }
        serverSocket = null
        serverThread = null
    }

    private fun getCompressedThumbnailBytes(path: String): ByteArray? {
        if (path.isBlank()) return null
        thumbnailCache[path]?.let { return it }
        return try {
            val file = File(path)
            if (!file.exists()) return null
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, opts)
            var sample = 1
            while (opts.outWidth / sample > 240 || opts.outHeight / sample > 240) {
                sample *= 2
            }
            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
            val bmp = BitmapFactory.decodeFile(file.absolutePath, decodeOpts) ?: return null
            val baos = ByteArrayOutputStream()
            bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, baos)
            val bytes = baos.toByteArray()
            thumbnailCache[path] = bytes
            bytes
        } catch (_: Exception) {
            null
        }
    }

    private fun handleClient(client: Socket) {
        Thread {
            try {
                client.soTimeout = 3000
                val reader = BufferedReader(InputStreamReader(client.getInputStream(), Charsets.UTF_8))
                val requestLine = reader.readLine() ?: ""
                while (true) {
                    val header = reader.readLine()
                    if (header.isNullOrEmpty()) break
                }

                val path = requestLine.split(" ").getOrNull(1) ?: "/"
                val out = client.getOutputStream()

                if (path.startsWith("/api/image")) {
                    val idStr = path.substringAfter("id=", "").substringBefore("&").trim()
                    val prodId = idStr.toLongOrNull() ?: 0L
                    val imgPath = productImageMap[prodId]
                        ?: currentState.items.find { it.productId == prodId }?.imagePath
                        ?: ""
                    val imgBytes = getCompressedThumbnailBytes(imgPath)
                    if (imgBytes != null && imgBytes.isNotEmpty()) {
                        val headers = (
                            "HTTP/1.1 200 OK\r\n" +
                            "Content-Type: image/jpeg\r\n" +
                            "Cache-Control: max-age=3600\r\n" +
                            "Content-Length: ${imgBytes.size}\r\n" +
                            "Connection: close\r\n\r\n"
                        ).toByteArray(Charsets.UTF_8)
                        out.write(headers)
                        out.write(imgBytes)
                    } else {
                        val notFound = "HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
                            .toByteArray(Charsets.UTF_8)
                        out.write(notFound)
                    }
                } else if (path.startsWith("/api/cart")) {
                    lastClientPingTime = System.currentTimeMillis()
                    val jsonBytes = buildStateJson(currentState).toByteArray(Charsets.UTF_8)
                    val headers = (
                        "HTTP/1.1 200 OK\r\n" +
                        "Content-Type: application/json; charset=utf-8\r\n" +
                        "Cache-Control: no-store, no-cache, must-revalidate\r\n" +
                        "Access-Control-Allow-Origin: *\r\n" +
                        "Content-Length: ${jsonBytes.size}\r\n" +
                        "Connection: close\r\n\r\n"
                    ).toByteArray(Charsets.UTF_8)
                    out.write(headers)
                    out.write(jsonBytes)
                } else {
                    val htmlBytes = buildCfdHtmlPage().toByteArray(Charsets.UTF_8)
                    val headers = (
                        "HTTP/1.1 200 OK\r\n" +
                        "Content-Type: text/html; charset=utf-8\r\n" +
                        "Cache-Control: no-store\r\n" +
                        "Content-Length: ${htmlBytes.size}\r\n" +
                        "Connection: close\r\n\r\n"
                    ).toByteArray(Charsets.UTF_8)
                    out.write(headers)
                    out.write(htmlBytes)
                }
                out.flush()
            } catch (_: Exception) {
            } finally {
                try {
                    client.close()
                } catch (_: Exception) {
                }
            }
        }.start()
    }

    private fun formatRp(valLong: Long): String {
        val nf = NumberFormat.getInstance(Locale("id", "ID"))
        return "Rp " + nf.format(valLong)
    }

    private fun buildStateJson(state: CfdLiveState): String {
        val root = JSONObject()
        root.put("isCfdActive", state.isCfdActive)
        root.put("displayStage", state.displayStage)
        root.put("storeName", state.storeName)
        root.put("storeAddress", state.storeAddress)
        root.put("storeFooter", state.storeFooter)
        root.put("cashierName", state.cashierName)
        root.put("customerName", state.customerName)
        root.put("orderType", state.orderType)
        root.put("subtotal", formatRp(state.subtotal))
        root.put("subtotalRaw", state.subtotal)
        root.put("discount", formatRp(state.discount))
        root.put("discountRaw", state.discount)
        root.put("voucherDiscount", formatRp(state.voucherDiscount))
        root.put("voucherDiscountRaw", state.voucherDiscount)
        root.put("taxAmount", formatRp(state.taxAmount))
        root.put("taxAmountRaw", state.taxAmount)
        root.put("isTaxInclusive", state.isTaxInclusive)
        root.put("serviceCharge", formatRp(state.serviceCharge))
        root.put("serviceChargeRaw", state.serviceCharge)
        root.put("roundingDiff", formatRp(state.roundingDiff))
        root.put("roundingDiffRaw", state.roundingDiff)
        root.put("totalPrice", formatRp(state.totalPrice))
        root.put("totalPriceRaw", state.totalPrice)
        root.put("paymentMethod", state.paymentMethod)
        root.put("amountToPayRaw", state.amountToPay)
        root.put("qrisPayload", state.qrisPayload)
        root.put("bankName", state.bankName)
        root.put("bankAccountNumber", state.bankAccountNumber)
        root.put("bankAccountName", state.bankAccountName)
        root.put("bankInfo", state.bankInfo)
        root.put("cashReceivedRaw", state.cashReceived)
        root.put("changeAmountRaw", state.changeAmount)
        root.put("thankYouMessage", state.thankYouMessage)
        root.put("completedTimestamp", state.completedTimestamp)

        val arr = JSONArray()
        state.items.forEach { item ->
            val obj = JSONObject()
            obj.put("productId", item.productId)
            obj.put("name", item.name)
            obj.put("variation", item.variation)
            obj.put("quantity", item.quantity)
            obj.put("unitPrice", formatRp(item.unitPrice))
            obj.put("unitPriceRaw", item.unitPrice)
            obj.put("subtotal", formatRp(item.subtotal))
            obj.put("subtotalRaw", item.subtotal)
            obj.put("notes", item.notes)
            obj.put("hasImage", item.imagePath.isNotBlank() && File(item.imagePath).exists())
            arr.put(obj)
        }
        root.put("items", arr)
        return root.toString()
    }

    private fun buildCfdHtmlPage(): String {
        return """
<!DOCTYPE html>
<html lang="id">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>ZoKas Customer Display</title>
</head>
<body style="background:#0f172a;color:#f8fafc;font-family:sans-serif;text-align:center;padding:32px;">
<h2>ZoKas Customer Facing Display Server</h2>
<p>Gunakan aplikasi ZoKas pada perangkat layar pelanggan untuk tampilan Full-Screen interaktif.</p>
</body>
</html>
""".trimIndent()
    }
}

@Composable
fun LocalWirelessCfdDialog(
    viewModel: PosViewModel,
    settingsManager: SettingsManager,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val activity = LocalActivity.current as? FragmentActivity
    val users by viewModel.users.collectAsStateWithLifecycle()

    var showLocalScreenPreview by remember { mutableStateOf(false) }
    var showStopAuthDialog by remember { mutableStateOf(false) }

    val p2pPermissionLauncher = rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        if (perms.values.all { it }) {
            P2pCfdManager.startHosting(context, settingsManager.storeName ?: "ZoKas")
        } else {
            Toast.makeText(context, "Izin jaringan lokal diperlukan untuk mode P2P langsung.", Toast.LENGTH_LONG).show()
        }
    }

    val qrConnectLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        if (result.contents != null && (result.contents.startsWith("http") || result.contents == LocalCfdServerManager.P2P_NEARBY_URI)) {
            LocalCfdServerManager.stopServer()
            P2pCfdManager.stopAll()
            LocalCfdServerManager.lockAsKioskReceiver(context, result.contents)
            onDismiss()
        } else if (result.contents != null) {
            Toast.makeText(context, "QR Code bukan alamat Layar Pelanggan ZoKas.", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        if (!LocalCfdServerManager.isServerRunning) {
            LocalCfdServerManager.startServer()
        }
        if (!P2pCfdManager.isHosting && P2pCfdManager.connectedDeviceName == null) {
            P2pCfdManager.startHosting(context, settingsManager.storeName ?: "ZoKas")
        }
    }

    val isRunning = LocalCfdServerManager.isServerRunning || P2pCfdManager.isHosting || P2pCfdManager.connectedDeviceName != null
    val serverUrl = remember(isRunning, LocalCfdServerManager.activePort) {
        LocalCfdServerManager.getServerUrl()
    }
    val qrBitmap = remember(serverUrl, isRunning) {
        if (isRunning) generateQrCodeBitmap(serverUrl) else null
    }
    val isClientConnected = ((System.currentTimeMillis() - LocalCfdServerManager.lastClientPingTime) < 5000L) || (P2pCfdManager.connectedDeviceName != null)

    if (showStopAuthDialog) {
        OwnerSecurityAuthDialog(
            title = "Otorisasi Matikan Layar Pelanggan",
            subtitle = "Masukkan PIN Owner atau gunakan Sidik Jari untuk menghentikan transmisi & membuka kunci perangkat penerima CFD.",
            users = users,
            activity = activity,
            onSuccess = {
                LocalCfdServerManager.stopServer()
                P2pCfdManager.stopAll()
                showStopAuthDialog = false
                Toast.makeText(context, "Transmisi Layar Pelanggan dihentikan.", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showStopAuthDialog = false }
        )
    }

    if (showLocalScreenPreview) {
        Dialog(
            onDismissRequest = { showLocalScreenPreview = false },
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
        ) {
            CfdContent(
                viewModel = viewModel,
                settingsManager = settingsManager,
                onDismiss = { showLocalScreenPreview = false }
            )
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Default.CastConnected,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                "Layar Pelanggan Nirkabel (Local CFD)",
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    color = if (isRunning)
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                    else
                        MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isRunning) "Transmisi CFD Aktif" else "Transmisi CFD Nonaktif",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = if (isRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (isClientConnected)
                                    "Perangkat Layar Pelanggan terhubung & terkunci!"
                                else
                                    "Hubungkan perangkat kasir & layar pelanggan ke 1 Wi-Fi / Hotspot",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = isRunning,
                            onCheckedChange = { enable ->
                                if (enable) {
                                    LocalCfdServerManager.startServer()
                                    P2pCfdManager.startHosting(context, settingsManager.storeName ?: "ZoKas")
                                } else {
                                    showStopAuthDialog = true
                                }
                            },
                            colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary)
                        )
                    }
                }

                if (isRunning) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "1. Pastikan perangkat kedua (layar pelanggan) terhubung ke jaringan Wi-Fi / Hotspot yang sama.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "2. Buka aplikasi ZoKas di perangkat kedua lalu pilih 'Mode Layar Pelanggan (CFD)' untuk memindai QR Code di bawah (otomatis Full-Screen & terkunci PIN Owner):",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    if (qrBitmap != null) {
                        Surface(
                            color = Color.White,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Image(
                                bitmap = qrBitmap.asImageBitmap(),
                                contentDescription = "QR CFD",
                                modifier = Modifier.size(185.dp).padding(10.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = serverUrl,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            IconButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("ZoKas CFD URL", serverUrl))
                                    Toast.makeText(context, "Alamat koneksi CFD disalin!", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.ContentCopy,
                                    contentDescription = "Salin URL",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = { showStopAuthDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Keluarkan & Hentikan Layar CFD (PIN Owner)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        val options = ScanOptions().apply {
                            setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                            setPrompt("Pindai QR Layar Pelanggan dari Kasir Utama")
                            setCameraId(0)
                            setBeepEnabled(false)
                            setOrientationLocked(true)
                        }
                        qrConnectLauncher.launch(options)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.tertiary,
                        contentColor = MaterialTheme.colorScheme.onTertiary
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Pindai / Scan QR Code (Jadikan Layar CFD)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { showLocalScreenPreview = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Pratinjau Tampilan Pelanggan di Layar Ini", fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Tutup", fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
private fun CfdProductThumbnail(
    hostUrl: String,
    productId: Long,
    productName: String,
    hasImage: Boolean,
    sizeDp: Int = 46
) {
    Box(
        modifier = Modifier
            .size(sizeDp.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center
    ) {
        if (hasImage && productId > 0L && hostUrl.startsWith("http")) {
            AsyncImage(
                model = "$hostUrl/api/image?id=$productId",
                contentDescription = productName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text(
                text = productName.trim().take(2).uppercase().ifBlank { "ZK" },
                fontWeight = FontWeight.ExtraBold,
                fontSize = (sizeDp / 3).sp,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1
            )
        }
    }
}

@Composable
fun FullScreenKioskCfdReceiverScreen(
    hostUrl: String,
    users: List<UserEntity>,
    onUnlockedAndExit: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    var liveState by remember { mutableStateOf(CfdLiveState(isCfdActive = true)) }
    var isConnected by remember { mutableStateOf(false) }
    var disconnectedStreak by remember { mutableStateOf(0) }
    var showEmergencyPinDialog by remember { mutableStateOf(false) }

    var lastShownCompletedTs by remember { mutableStateOf(0L) }
    var showThankYouOverlay by remember { mutableStateOf(false) }

    BackHandler(enabled = true) {
        Toast.makeText(
            context,
            "Layar Pelanggan terkunci. Hentikan transmisi dari perangkat kasir utama.",
            Toast.LENGTH_SHORT
        ).show()
    }

    DisposableEffect(view) {
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    LaunchedEffect(liveState.completedTimestamp) {
        if (liveState.completedTimestamp > 0L && liveState.completedTimestamp != lastShownCompletedTs) {
            lastShownCompletedTs = liveState.completedTimestamp
            showThankYouOverlay = true
            delay(5000L)
            showThankYouOverlay = false
        }
    }

    LaunchedEffect(hostUrl) {
        if (hostUrl == LocalCfdServerManager.P2P_NEARBY_URI) return@LaunchedEffect
        while (true) {
            val fetched = withContext(Dispatchers.IO) {
                try {
                    val conn = (URL("$hostUrl/api/cart?t=${System.currentTimeMillis()}").openConnection() as HttpURLConnection).apply {
                        connectTimeout = 1500
                        readTimeout = 1500
                        requestMethod = "GET"
                    }
                    if (conn.responseCode == 200) {
                        val raw = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                        val json = JSONObject(raw)
                        val active = json.optBoolean("isCfdActive", true)
                        if (!active) {
                            Pair(null, false)
                        } else {
                            val itemsArr = json.optJSONArray("items") ?: JSONArray()
                            val parsedItems = mutableListOf<CfdDisplayItem>()
                            for (i in 0 until itemsArr.length()) {
                                val obj = itemsArr.getJSONObject(i)
                                val hasImg = obj.optBoolean("hasImage", false)
                                parsedItems.add(
                                    CfdDisplayItem(
                                        productId = obj.optLong("productId", 0L),
                                        name = obj.optString("name"),
                                        variation = obj.optString("variation"),
                                        quantity = obj.optInt("quantity"),
                                        unitPrice = obj.optLong("unitPriceRaw"),
                                        subtotal = obj.optLong("subtotalRaw"),
                                        notes = obj.optString("notes"),
                                        imagePath = if (hasImg) "remote" else ""
                                    )
                                )
                            }
                            val parsedState = CfdLiveState(
                                displayStage = json.optString("displayStage", "CART"),
                                storeName = json.optString("storeName", "ZoKas Store"),
                                storeAddress = json.optString("storeAddress", ""),
                                storeFooter = json.optString("storeFooter", "Terima Kasih atas Kunjungan Anda"),
                                cashierName = json.optString("cashierName", ""),
                                customerName = json.optString("customerName", "Pelanggan Umum"),
                                orderType = json.optString("orderType", "Dine-In"),
                                items = parsedItems,
                                subtotal = json.optLong("subtotalRaw"),
                                discount = json.optLong("discountRaw"),
                                voucherDiscount = json.optLong("voucherDiscountRaw"),
                                taxAmount = json.optLong("taxAmountRaw"),
                                serviceCharge = json.optLong("serviceChargeRaw"),
                                roundingDiff = json.optLong("roundingDiffRaw"),
                                totalPrice = json.optLong("totalPriceRaw"),
                                isTaxInclusive = json.optBoolean("isTaxInclusive"),
                                paymentMethod = json.optString("paymentMethod", "CASH"),
                                amountToPay = json.optLong("amountToPayRaw"),
                                qrisPayload = json.optString("qrisPayload", ""),
                                bankName = json.optString("bankName", ""),
                                bankAccountNumber = json.optString("bankAccountNumber", ""),
                                bankAccountName = json.optString("bankAccountName", ""),
                                bankInfo = json.optString("bankInfo", ""),
                                cashReceived = json.optLong("cashReceivedRaw"),
                                changeAmount = json.optLong("changeAmountRaw"),
                                thankYouMessage = json.optString("thankYouMessage", "Terima Kasih atas pembeliannya 🙏"),
                                completedTimestamp = json.optLong("completedTimestamp", 0L),
                                isCfdActive = true
                            )
                            Pair(parsedState, true)
                        }
                    } else {
                        Pair(null, true)
                    }
                } catch (_: Exception) {
                    Pair(null, true)
                }
            }

            val (newState, stillActive) = fetched
            if (!stillActive) {
                LocalCfdServerManager.unlockKioskReceiver(context)
                onUnlockedAndExit()
                break
            } else if (newState != null) {
                liveState = newState
                isConnected = true
                disconnectedStreak = 0
            } else {
                isConnected = false
                disconnectedStreak++
            }
            delay(500)
        }
    }

    if (showEmergencyPinDialog) {
        OwnerSecurityAuthDialog(
            title = "Buka Kunci Layar Pelanggan",
            subtitle = "Masukkan PIN Owner untuk keluar dari mode tampilan pelanggan secara manual.",
            users = users,
            activity = LocalActivity.current as? FragmentActivity,
            onSuccess = {
                P2pCfdManager.stopAll()
                LocalCfdServerManager.unlockKioskReceiver(context)
                showEmergencyPinDialog = false
                onUnlockedAndExit()
            },
            onDismiss = { showEmergencyPinDialog = false }
        )
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = liveState.storeName,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (liveState.storeAddress.isNotBlank()) {
                            Text(
                                text = liveState.storeAddress,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = if (isConnected)
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            else
                                MaterialTheme.colorScheme.error.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(
                                            if (isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                            CircleShape
                                        )
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isConnected) "TERHUBUNG KE KASIR" else "MENYAMBUNGKAN...",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                )
                            }
                        }
                        if (disconnectedStreak >= 15) {
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(
                                onClick = { showEmergencyPinDialog = true },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.LockOpen,
                                    contentDescription = "Buka Kunci Darurat",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            if (showThankYouOverlay || liveState.displayStage == "COMPLETED") {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(0.88f),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(76.dp)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(48.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = "PEMBAYARAN BERHASIL",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary,
                                letterSpacing = 1.5.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = liveState.thankYouMessage,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center,
                                lineHeight = 32.sp
                            )
                            Spacer(modifier = Modifier.height(18.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Spacer(modifier = Modifier.height(14.dp))

                            Column(
                                modifier = Modifier.fillMaxWidth(0.75f),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Total Tagihan:", fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(formatRupiah(liveState.amountToPay), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Bayar (${liveState.paymentMethod}):", fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(
                                        formatRupiah(if (liveState.cashReceived > 0L) liveState.cashReceived else liveState.amountToPay),
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Kembalian:", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                    Text(
                                        formatRupiah(liveState.changeAmount),
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Black,
                                        color = MaterialTheme.colorScheme.tertiary
                                    )
                                }
                            }
                        }
                    }
                }
            } else if (liveState.displayStage == "PAYMENT") {
                val payAmount = if (liveState.amountToPay > 0L) liveState.amountToPay else liveState.totalPrice
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Card(
                        modifier = Modifier.weight(1.1f).fillMaxHeight(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxSize().padding(18.dp)) {
                            Text(
                                "RINCIAN PESANAN (${liveState.items.sumOf { it.quantity }} ITEM)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.outlineVariant)
                            LazyColumn(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(liveState.items) { item ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        CfdProductThumbnail(
                                            hostUrl = hostUrl,
                                            productId = item.productId,
                                            productName = item.name,
                                            hasImage = item.imagePath.isNotBlank(),
                                            sizeDp = 40
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = if (item.variation.isNotBlank()) "${item.name} (${item.variation})" else item.name,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "${item.quantity} x ${formatRupiah(item.unitPrice)}",
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(formatRupiah(item.subtotal), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.outlineVariant)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Total Tagihan", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Text(formatRupiah(payAmount), fontWeight = FontWeight.Black, fontSize = 20.sp, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }

                    Card(
                        modifier = Modifier.weight(1.2f).fillMaxHeight(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(
                                    text = "PEMBAYARAN VIA ${liveState.paymentMethod.uppercase()}",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = formatRupiah(payAmount),
                                fontSize = 34.sp,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            if (liveState.paymentMethod.equals("QRIS", ignoreCase = true) && liveState.qrisPayload.isNotBlank()) {
                                val qrisBitmap = remember(liveState.qrisPayload) {
                                    generateQrCodeBitmap(liveState.qrisPayload)
                                }
                                if (qrisBitmap != null) {
                                    Surface(
                                        color = Color.White,
                                        shape = RoundedCornerShape(14.dp),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                    ) {
                                        Image(
                                            bitmap = qrisBitmap.asImageBitmap(),
                                            contentDescription = "QRIS Pelanggan",
                                            modifier = Modifier.size(200.dp).padding(10.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        "Silakan scan QRIS di atas dengan aplikasi Bank / E-Wallet Anda",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            } else if (liveState.paymentMethod.equals("Bank Transfer", ignoreCase = true)) {
                                Surface(
                                    color = MaterialTheme.colorScheme.surface,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.fillMaxWidth(0.9f)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(16.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text("Silakan Transfer ke Rekening:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(liveState.bankName.ifBlank { "-" }, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                                        Text(
                                            liveState.bankAccountNumber.ifBlank { "-" },
                                            fontSize = 24.sp,
                                            fontWeight = FontWeight.Black,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text("a/n ${liveState.bankAccountName.ifBlank { "-" }}", fontSize = 13.sp)
                                    }
                                }
                            } else {
                                Surface(
                                    color = MaterialTheme.colorScheme.surface,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.fillMaxWidth(0.9f)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("Uang Diterima:", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text(
                                                formatRupiah(liveState.cashReceived),
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("Kembalian:", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                            Text(
                                                formatRupiah(liveState.changeAmount),
                                                fontSize = 18.sp,
                                                fontWeight = FontWeight.Black,
                                                color = MaterialTheme.colorScheme.tertiary
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    "Silakan lakukan pembayaran tunai kepada kasir",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Card(
                        modifier = Modifier
                            .weight(1.45f)
                            .fillMaxHeight(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    "DAFTAR PESANAN",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    "${liveState.customerName} • ${liveState.orderType}",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                            if (liveState.items.isEmpty()) {
                                Box(
                                    modifier = Modifier.fillMaxSize().padding(24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(
                                            Icons.Default.Storefront,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(64.dp)
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text(
                                            "Selamat Datang!",
                                            fontSize = 24.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            "Silakan periksa rincian pesanan Anda di layar ini.",
                                            fontSize = 14.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    items(liveState.items) { item ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            CfdProductThumbnail(
                                                hostUrl = hostUrl,
                                                productId = item.productId,
                                                productName = item.name,
                                                hasImage = item.imagePath.isNotBlank(),
                                                sizeDp = 48
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))

                                            Column(modifier = Modifier.weight(1f)) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(
                                                        text = item.name,
                                                        fontSize = 17.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.onSurface,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                        modifier = Modifier.weight(1f, fill = false)
                                                    )
                                                    if (item.variation.isNotBlank()) {
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        Text(
                                                            text = "(${item.variation})",
                                                            fontSize = 14.sp,
                                                            fontWeight = FontWeight.SemiBold,
                                                            color = MaterialTheme.colorScheme.primary
                                                        )
                                                    }
                                                }
                                                Text(
                                                    text = "${item.quantity} x ${formatRupiah(item.unitPrice)}",
                                                    fontSize = 13.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                if (item.notes.isNotBlank()) {
                                                    Text(
                                                        text = "Catatan: ${item.notes}",
                                                        fontSize = 12.sp,
                                                        color = MaterialTheme.colorScheme.error
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = formatRupiah(item.subtotal),
                                                fontSize = 17.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                        HorizontalDivider(
                                            modifier = Modifier.padding(top = 8.dp),
                                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(20.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    "RINGKASAN TAGIHAN",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 12.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant
                                )
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Subtotal", fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(formatRupiah(liveState.subtotal), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                }
                                if (liveState.discount > 0L) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Diskon", fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text("- ${formatRupiah(liveState.discount)}", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                                    }
                                }
                                if (liveState.voucherDiscount > 0L) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Voucher", fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text("- ${formatRupiah(liveState.voucherDiscount)}", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                                if (liveState.taxAmount > 0L) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(if (liveState.isTaxInclusive) "Pajak (Termasuk)" else "Pajak", fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(if (liveState.isTaxInclusive) "(${formatRupiah(liveState.taxAmount)})" else "+ ${formatRupiah(liveState.taxAmount)}", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                                if (liveState.serviceCharge > 0L) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Service Charge", fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text("+ ${formatRupiah(liveState.serviceCharge)}", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                                if (liveState.roundingDiff != 0L) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Pembulatan", fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(formatRupiah(liveState.roundingDiff), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            Column {
                                Surface(
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                                    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(18.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            "TOTAL PEMBAYARAN",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = formatRupiah(liveState.totalPrice),
                                            fontSize = 34.sp,
                                            fontWeight = FontWeight.Black,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = liveState.storeFooter,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(10.dp)
                )
            }
        }
    }
}

@Composable
fun OwnerSecurityAuthDialog(
    title: String,
    subtitle: String,
    users: List<UserEntity>,
    activity: FragmentActivity?,
    onSuccess: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var pinInput by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf("") }

    val canUseBiometric = remember {
        val bm = BiometricManager.from(context)
        bm.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS
    }

    val triggerBiometric: () -> Unit = {
        if (activity != null && canUseBiometric) {
            val executor = ContextCompat.getMainExecutor(context)
            val prompt = BiometricPrompt(
                activity,
                executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        super.onAuthenticationSucceeded(result)
                        onSuccess()
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        super.onAuthenticationError(errorCode, errString)
                        errorText = errString.toString()
                    }
                }
            )
            val info = BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setSubtitle("Verifikasi identitas Owner dengan sidik jari")
                .setNegativeButtonText("Gunakan PIN")
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
                .build()
            prompt.authenticate(info)
        } else {
            Toast.makeText(context, "Sensor biometrik tidak tersedia, gunakan PIN Owner.", Toast.LENGTH_SHORT).show()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Default.VerifiedUser,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(title, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = pinInput,
                    onValueChange = { raw ->
                        if (raw.length <= 6 && raw.all { it.isDigit() }) {
                            pinInput = raw
                            errorText = ""
                        }
                    },
                    label = { Text("PIN Owner") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    isError = errorText.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                if (errorText.isNotEmpty()) {
                    Text(
                        text = errorText,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                if (canUseBiometric && activity != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = triggerBiometric,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(
                            Icons.Default.Fingerprint,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Verifikasi dengan Sidik Jari",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val hashed = hashPin(pinInput)
                    val isOwnerValid = users.any { it.role == "OWNER" && it.pin == hashed }
                    if (isOwnerValid) {
                        onSuccess()
                    } else {
                        errorText = "PIN Owner salah! Akses ditolak."
                    }
                },
                enabled = pinInput.length >= 4,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("KONFIRMASI", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    )
}
