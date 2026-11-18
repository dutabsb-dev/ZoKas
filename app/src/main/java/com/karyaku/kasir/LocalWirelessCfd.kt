package com.karyaku.kasir

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
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
    val updatedAt: Long = System.currentTimeMillis()
)

object LocalCfdServerManager {
    private const val DEFAULT_PORT = 8765
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

    fun updateState(newState: CfdLiveState) {
        currentState = newState.copy(updatedAt = System.currentTimeMillis())
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
        } catch (e: Exception) {
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
                    val headers = buildString {
                        append("HTTP/1.1 200 OK
")
                        append("Content-Type: application/json; charset=utf-8
")
                        append("Cache-Control: no-store, no-cache, must-revalidate
")
                        append("Access-Control-Allow-Origin: *
")
                        append("Content-Length: ${jsonBytes.size}
")
                        append("Connection: close

")
                    }.toByteArray(Charsets.UTF_8)
                    out.write(headers)
                    out.write(jsonBytes)
                } else {
                    val htmlBytes = buildCfdHtmlPage().toByteArray(Charsets.UTF_8)
                    val headers = buildString {
                        append("HTTP/1.1 200 OK
")
                        append("Content-Type: text/html; charset=utf-8
")
                        append("Cache-Control: no-store
")
                        append("Content-Length: ${htmlBytes.size}
")
                        append("Connection: close

")
                    }.toByteArray(Charsets.UTF_8)
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
        root.put("storeName", state.storeName)
        root.put("storeAddress", state.storeAddress)
        root.put("storeFooter", state.storeFooter)
        root.put("cashierName", state.cashierName)
        root.put("customerName", state.customerName)
        root.put("orderType", state.orderType)
        root.put("subtotal", formatRp(state.subtotal))
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
            obj.put("subtotal", formatRp(item.subtotal))
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

@Composable
fun LocalWirelessCfdDialog(
    viewModel: PosViewModel,
    settingsManager: SettingsManager,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var showLocalScreenPreview by remember { mutableStateOf(false) }

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
                                text = if (isRunning) "Server Local CFD Aktif" else "Server Local CFD Nonaktif",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = if (isRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (isClientConnected)
                                    "Tablet / Layar Pelanggan sedang terhubung!"
                                else
                                    "Hubungkan HP Kasir & Tablet ke 1 Wi-Fi / Hotspot",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = isRunning,
                            onCheckedChange = { enable ->
                                if (enable) LocalCfdServerManager.startServer()
                                else LocalCfdServerManager.stopServer()
                            },
                            colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary)
                        )
                    }
                }

                if (isRunning) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "1. Pastikan Tablet (misal Galaxy Tab A11+) terhubung ke Wi-Fi / Hotspot yang sama dengan HP ini.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "2. Scan QR Code di bawah dengan Kamera Tablet, atau buka alamat IP di Browser Tablet:",
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
                                    Toast.makeText(context, "Alamat CFD disalin!", Toast.LENGTH_SHORT).show()
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
                }

                Spacer(modifier = Modifier.height(12.dp))
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
                Text("Selesai", fontWeight = FontWeight.Bold)
            }
        }
    )
}
