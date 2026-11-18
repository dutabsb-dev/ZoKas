package com.karyaku.kasir

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
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
import com.karyaku.kasir.data.local.UserEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URL
import java.text.NumberFormat
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

data class CfdDisplayItem(
    val name: String,
    val variation: String,
    val quantity: Int,
    val unitPrice: Long,
    val subtotal: Long,
    val notes: String
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
    val paymentMethod: String = "",
    val qrisPayload: String = "",
    val bankInfo: String = "",
    val isCfdActive: Boolean = true,
    val updatedAt: Long = System.currentTimeMillis()
)

object LocalCfdServerManager {
    private const val DEFAULT_PORT = 8765
    private const val PREFS_CFD = "zokas_wireless_cfd_prefs"

    private val isRunningFlag = AtomicBoolean(false)
    private var serverSocket: ServerSocket? = null
    private var serverThread: Thread? = null

    @Volatile
    var currentState: CfdLiveState = CfdLiveState()
        private set

    var isServerRunning by mutableStateOf(false)
        private set

    var activePort by mutableStateOf(DEFAULT_PORT)
        private set

    var lastClientPingTime by mutableStateOf(0L)
        private set

    // Status mode kiosk layar pelanggan pada perangkat klien (Tablet/Layar Kedua)
    var isKioskReceiverLocked by mutableStateOf(false)
        private set

    var connectedHostUrl by mutableStateOf("")
        private set

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
        currentState = newState.copy(
            isCfdActive = isRunningFlag.get(),
            updatedAt = System.currentTimeMillis()
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
        // Tandai isCfdActive = false agar perangkat penerima yang sedang terkunci otomatis keluar dari mode CFD
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

                if (path.startsWith("/api/cart")) {
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
        root.put("bankInfo", state.bankInfo)

        val arr = JSONArray()
        state.items.forEach { item ->
            val obj = JSONObject()
            obj.put("name", item.name)
            obj.put("variation", item.variation)
            obj.put("quantity", item.quantity)
            obj.put("unitPrice", formatRp(item.unitPrice))
            obj.put("unitPriceRaw", item.unitPrice)
            obj.put("subtotal", formatRp(item.subtotal))
            obj.put("subtotalRaw", item.subtotal)
            obj.put("notes", item.notes)
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
<meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
<title>ZoKas Customer Display</title>
<style>
  :root {
    --bg: #0f172a;
    --card: #1e293b;
    --border: #334155;
    --primary: #10b981;
    --accent: #f59e0b;
    --text: #f8fafc;
    --muted: #94a3b8;
  }
  * { box-sizing: border-box; margin: 0; padding: 0; font-family: system-ui, -apple-system, sans-serif; }
  body { background: var(--bg); color: var(--text); height: 100vh; display: flex; flex-direction: column; overflow: hidden; }
  header {
    background: var(--card); padding: 14px 24px; border-bottom: 1px solid var(--border);
    display: flex; justify-content: space-between; align-items: center;
  }
  .store-title { font-size: 22px; font-weight: 800; color: var(--primary); }
  .store-sub { font-size: 12px; color: var(--muted); }
  .status-pill {
    background: rgba(16,185,129,0.15); color: var(--primary); border: 1px solid rgba(16,185,129,0.4);
    padding: 6px 12px; border-radius: 999px; font-size: 12px; font-weight: 700;
  }
  main { flex: 1; display: grid; grid-template-columns: 1.45fr 1fr; gap: 16px; padding: 16px; overflow: hidden; }
  @media (max-width: 720px) { main { grid-template-columns: 1fr; overflow-y: auto; } }
  .panel {
    background: var(--card); border: 1px solid var(--border); border-radius: 16px;
    display: flex; flex-direction: column; overflow: hidden;
  }
  .panel-header {
    padding: 14px 18px; border-bottom: 1px solid var(--border); font-weight: 700; font-size: 15px;
    display: flex; justify-content: space-between; color: var(--muted);
  }
  .items-list { flex: 1; overflow-y: auto; padding: 12px 18px; }
  .empty-box {
    height: 100%; display: flex; flex-direction: column; align-items: center; justify-content: center;
    text-align: center; color: var(--muted); padding: 24px;
  }
  .empty-box h2 { color: var(--text); font-size: 24px; margin-bottom: 8px; }
  .item-row {
    display: flex; justify-content: space-between; align-items: center;
    padding: 12px 0; border-bottom: 1px dashed var(--border);
  }
  .item-name { font-size: 17px; font-weight: 700; }
  .item-meta { font-size: 13px; color: var(--muted); margin-top: 2px; }
  .item-var { color: var(--primary); font-weight: 600; }
  .item-note { color: #f87171; font-size: 12px; margin-top: 2px; }
  .item-sub { font-size: 17px; font-weight: 800; color: var(--text); }
  .summary-body { padding: 18px; display: flex; flex-direction: column; justify-content: space-between; flex: 1; }
  .sum-row { display: flex; justify-content: space-between; font-size: 15px; margin-bottom: 10px; color: var(--muted); }
  .sum-row strong { color: var(--text); }
  .total-box {
    background: rgba(16,185,129,0.12); border: 1.5px solid var(--primary);
    border-radius: 14px; padding: 18px; text-align: center; margin-top: 12px;
  }
  .total-label { font-size: 13px; text-transform: uppercase; letter-spacing: 1px; color: var(--muted); }
  .total-val { font-size: 36px; font-weight: 900; color: var(--primary); margin-top: 4px; }
  .bank-box {
    margin-top: 12px; background: rgba(245,158,11,0.12); border: 1px solid var(--accent);
    border-radius: 12px; padding: 12px; text-align: center; font-size: 14px; color: var(--accent); font-weight: 700;
  }
  footer {
    background: var(--card); border-top: 1px solid var(--border); padding: 10px 24px;
    text-align: center; font-size: 13px; color: var(--muted);
  }
</style>
</head>
<body>
<header>
  <div>
    <div class="store-title" id="storeName">ZoKas Store</div>
    <div class="store-sub" id="storeAddr">Layar Pelanggan • Terhubung ke Kasir</div>
  </div>
  <div class="status-pill" id="connStatus">● LIVE</div>
</header>
<main>
  <section class="panel">
    <div class="panel-header">
      <span>DAFTAR PESANAN</span>
      <span id="orderMeta">Pelanggan Umum • Dine-In</span>
    </div>
    <div class="items-list" id="itemsContainer">
      <div class="empty-box">
        <h2>Selamat Datang!</h2>
        <p>Silakan letakkan belanjaan Anda di meja kasir.</p>
      </div>
    </div>
  </section>
  <section class="panel">
    <div class="panel-header">
      <span>RINGKASAN TAGIHAN</span>
      <span id="itemCount">0 Item</span>
    </div>
    <div class="summary-body">
      <div>
        <div class="sum-row"><span>Subtotal</span><strong id="subtotalVal">Rp 0</strong></div>
        <div class="sum-row" id="discRow" style="display:none;"><span>Diskon</span><strong style="color:#f87171;" id="discVal">- Rp 0</strong></div>
        <div class="sum-row" id="vouchRow" style="display:none;"><span>Voucher</span><strong style="color:#10b981;" id="vouchVal">- Rp 0</strong></div>
        <div class="sum-row" id="taxRow" style="display:none;"><span id="taxLabel">Pajak</span><strong id="taxVal">Rp 0</strong></div>
        <div class="sum-row" id="scRow" style="display:none;"><span>Service Charge</span><strong id="scVal">Rp 0</strong></div>
        <div class="sum-row" id="roundRow" style="display:none;"><span>Pembulatan</span><strong id="roundVal">Rp 0</strong></div>
      </div>
      <div>
        <div class="total-box">
          <div class="total-label">Total Pembayaran</div>
          <div class="total-val" id="totalVal">Rp 0</div>
        </div>
        <div class="bank-box" id="bankBox" style="display:none;"></div>
      </div>
    </div>
  </section>
</main>
<footer id="storeFooter">Terima Kasih atas Kunjungan Anda</footer>
<script>
async function pollCart() {
  try {
    const res = await fetch('/api/cart?t=' + Date.now());
    const data = await res.json();
    document.getElementById('connStatus').textContent = '● TERHUBUNG';
    document.getElementById('storeName').textContent = data.storeName || 'ZoKas Store';
    document.getElementById('storeAddr').textContent = data.storeAddress || 'Layar Pelanggan • Terhubung ke Kasir';
    document.getElementById('storeFooter').textContent = data.storeFooter || 'Terima Kasih atas Kunjungan Anda';
    document.getElementById('orderMeta').textContent = (data.customerName || 'Pelanggan Umum') + ' • ' + (data.orderType || 'Dine-In');

    const container = document.getElementById('itemsContainer');
    const items = data.items || [];
    let totalQty = 0;
    if (items.length === 0) {
      container.innerHTML = '<div class="empty-box"><h2>Selamat Datang!</h2><p>Silakan letakkan belanjaan Anda di meja kasir.</p></div>';
    } else {
      container.innerHTML = items.map(it => {
        totalQty += it.quantity;
        const varHtml = it.variation ? ' <span class="item-var">(' + it.variation + ')</span>' : '';
        const noteHtml = it.notes ? '<div class="item-note">Cat: ' + it.notes + '</div>' : '';
        return '<div class="item-row"><div><div class="item-name">' + it.name + varHtml + '</div><div class="item-meta">' + it.quantity + ' x ' + it.unitPrice + '</div>' + noteHtml + '</div><div class="item-sub">' + it.subtotal + '</div></div>';
      }).join('');
    }
    document.getElementById('itemCount').textContent = totalQty + ' Item';
    document.getElementById('subtotalVal').textContent = data.subtotal;

    document.getElementById('discRow').style.display = data.discountRaw > 0 ? 'flex' : 'none';
    document.getElementById('discVal').textContent = '- ' + data.discount;

    document.getElementById('vouchRow').style.display = data.voucherDiscountRaw > 0 ? 'flex' : 'none';
    document.getElementById('vouchVal').textContent = '- ' + data.voucherDiscount;

    document.getElementById('taxRow').style.display = data.taxAmountRaw > 0 ? 'flex' : 'none';
    document.getElementById('taxLabel').textContent = data.isTaxInclusive ? 'Pajak (Termasuk)' : 'Pajak';
    document.getElementById('taxVal').textContent = data.isTaxInclusive ? '(' + data.taxAmount + ')' : '+ ' + data.taxAmount;

    document.getElementById('scRow').style.display = data.serviceChargeRaw > 0 ? 'flex' : 'none';
    document.getElementById('scVal').textContent = '+ ' + data.serviceCharge;

    document.getElementById('roundRow').style.display = data.roundingDiffRaw !== 0 ? 'flex' : 'none';
    document.getElementById('roundVal').textContent = data.roundingDiff;

    document.getElementById('totalVal').textContent = data.totalPrice;

    const bankBox = document.getElementById('bankBox');
    if (data.bankInfo) {
      bankBox.style.display = 'block';
      bankBox.textContent = data.bankInfo;
    } else {
      bankBox.style.display = 'none';
    }
  } catch (e) {
    document.getElementById('connStatus').textContent = '○ MENUNGGU KONEKSI';
  }
}
setInterval(pollCart, 650);
pollCart();
</script>
</body>
</html>
""".trimIndent()
    }
}

// ==============================================================================
// DIALOG KONTROL TRANSMITTER CFD (DI PERANGKAT KASIR UTAMA)
// - Tanpa menyebut merek perangkat apapun (profesional & netral)
// - Jika ingin mematikan / mengeluarkan perangkat penerima CFD, wajib verifikasi
//   PIN Owner atau Sidik Jari (Biometrik) di perangkat kasir ini!
// ==============================================================================
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

    val qrConnectLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        if (result.contents != null && result.contents.startsWith("http")) {
            LocalCfdServerManager.stopServer()
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
    }

    val isRunning = LocalCfdServerManager.isServerRunning
    val serverUrl = remember(isRunning, LocalCfdServerManager.activePort) {
        LocalCfdServerManager.getServerUrl()
    }
    val qrBitmap = remember(serverUrl, isRunning) {
        if (isRunning) generateQrCodeBitmap(serverUrl) else null
    }
    val isClientConnected = (System.currentTimeMillis() - LocalCfdServerManager.lastClientPingTime) < 5000L

    // Dialog Verifikasi PIN Owner / Biometrik untuk mematikan / mengeluarkan CFD
    if (showStopAuthDialog) {
        OwnerSecurityAuthDialog(
            title = "Otorisasi Matikan Layar Pelanggan",
            subtitle = "Masukkan PIN Owner atau gunakan Sidik Jari untuk menghentikan transmisi & membuka kunci perangkat penerima CFD.",
            users = users,
            activity = activity,
            onSuccess = {
                LocalCfdServerManager.stopServer()
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
                                } else {
                                    // Wajib verifikasi PIN Owner / Biometrik sebelum mematikan CFD
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

// ==============================================================================
// LAYAR FULL-SCREEN NATIVE & KIOSK LOCK UNTUK PERANGKAT PENERIMA CFD (DI TABLET)
// - Menyembunyikan Status Bar & Navigation Bar secara penuh (Immersive Sticky)
// - Memblokir tombol Back Android sepenuhnya
// - Tanpa tombol Exit di layar penerima! Keluar HANYA terjadi otomatis ketika
//   perangkat kasir utama menghentikan transmisi CFD (dengan PIN Owner / Fingerprint),
//   atau melalui otorisasi darurat PIN Owner lokal jika koneksi terputus permanen.
// ==============================================================================
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

    // Blokir total tombol Back Android selama mode CFD terkunci
    BackHandler(enabled = true) {
        Toast.makeText(
            context,
            "Layar Pelanggan terkunci. Hentikan transmisi dari perangkat kasir utama.",
            Toast.LENGTH_SHORT
        ).show()
    }

    // Aktifkan Mode Immersive Full-Screen (sembunyikan status bar & nav bar)
    DisposableEffect(view) {
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    // Polling real-time ke server kasir utama
    LaunchedEffect(hostUrl) {
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
                                parsedItems.add(
                                    CfdDisplayItem(
                                        name = obj.optString("name"),
                                        variation = obj.optString("variation"),
                                        quantity = obj.optInt("quantity"),
                                        unitPrice = obj.optLong("unitPriceRaw"),
                                        subtotal = obj.optLong("subtotalRaw"),
                                        notes = obj.optString("notes")
                                    )
                                )
                            }
                            val parsedState = CfdLiveState(
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
                                bankInfo = json.optString("bankInfo", ""),
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
                // Perangkat kasir pengirim telah menghentikan transmisi CFD!
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
            delay(650)
        }
    }

    if (showEmergencyPinDialog) {
        OwnerSecurityAuthDialog(
            title = "Buka Kunci Layar Pelanggan",
            subtitle = "Masukkan PIN Owner untuk keluar dari mode tampilan pelanggan secara manual.",
            users = users,
            activity = LocalActivity.current as? FragmentActivity,
            onSuccess = {
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
            // HEADER
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
                        // Ikon gembok kecil hanya aktif jika koneksi terputus > 10 detik (darurat PIN Owner)
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

            // KONTEN UTAMA 2 KOLOM (DAFTAR PESANAN & RINGKASAN TAGIHAN)
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // KOLOM KIRI: DAFTAR ITEM BELANJA
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
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = item.name,
                                                    fontSize = 17.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurface
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

                // KOLOM KANAN: RINGKASAN TOTAL PEMBAYARAN
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

                            if (liveState.bankInfo.isNotBlank()) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Surface(
                                    color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.14f),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = liveState.bankInfo,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.tertiary,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(10.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // FOOTER
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

// ==============================================================================
// DIALOG OTORISASI PIN OWNER / FINGERPRINT UNTUK AKSI SENSITIF
// (Dipakai bersama oleh: Exit CFD, Hapus Massal Produk, Reset Total Omzet)
// ==============================================================================
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
