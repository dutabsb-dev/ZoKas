package com.karyaku.kasir

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress

data class DiscoveredCfdPeer(
    val endpointId: String,
    val deviceName: String,
    val ipAddress: String = ""
)

/**
 * P2pCfdManager (Zero-Dependency Native Android UDP/LAN Broadcast & Full State Sync)
 * Menggunakan UDP Broadcast standar Android SDK tanpa dependensi eksternal Nearby,
 * serta menyinkronkan penuh CfdLiveState, tahap pembayaran/selesai, dan thumbnail produk.
 */
object P2pCfdManager {
    private const val DISCOVERY_PORT = 48921
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var listenerJob: Job? = null
    private var advertiserJob: Job? = null
    private var lastBroadcastTime = 0L
    private var onDisconnectCallback: (() -> Unit)? = null

    var isHosting by mutableStateOf(false)
        private set

    var isAdvertising by mutableStateOf(false)
        private set

    var isDiscovering by mutableStateOf(false)
        private set

    var connectionStatus by mutableStateOf("Siap menghubungkan layar pelanggan")
        private set

    var connectedDeviceName by mutableStateOf<String?>(null)
        private set

    var connectedEndpointId by mutableStateOf<String?>(null)
        private set

    var discoveredPeers by mutableStateOf<List<DiscoveredCfdPeer>>(emptyList())
        private set

    var latestReceivedState by mutableStateOf<CfdLiveState?>(null)
        private set

    var receivedProductBitmaps by mutableStateOf<Map<Long, Bitmap>>(emptyMap())
        private set

    fun hasAllPermissions(context: Context): Boolean = true

    fun getRequiredPermissions(): Array<String> = emptyArray()

    fun startHosting(context: Context, storeName: String) {
        stopAll()
        isHosting = true
        isAdvertising = true
        connectionStatus = "Menunggu perangkat CFD terhubung..."
        val label = storeName.ifBlank { "ZoKas Kasir" }

        advertiserJob = scope.launch {
            while (isActive && isHosting) {
                try {
                    val payload = JSONObject().apply {
                        put("type", "ZOKAS_CFD_HOST")
                        put("name", label)
                        put("port", LocalCfdServerManager.activePort)
                    }.toString().toByteArray(Charsets.UTF_8)

                    DatagramSocket().use { socket ->
                        socket.broadcast = true
                        val packet = DatagramPacket(
                            payload,
                            payload.size,
                            InetAddress.getByName("255.255.255.255"),
                            DISCOVERY_PORT
                        )
                        socket.send(packet)
                    }
                } catch (_: Exception) {
                }
                delay(2000L)
            }
        }
    }

    fun startAdvertisingHost(context: Context, storeName: String) {
        startHosting(context, storeName)
    }

    fun stopAdvertisingHost(context: Context? = null) {
        isHosting = false
        isAdvertising = false
        advertiserJob?.cancel()
        advertiserJob = null
    }

    fun startDiscovery(context: Context, onDisconnected: (() -> Unit)? = null) {
        stopAll()
        onDisconnectCallback = onDisconnected
        isDiscovering = true
        discoveredPeers = emptyList()
        connectionStatus = "Mencari perangkat kasir ZoKas..."

        listenerJob = scope.launch {
            var socket: DatagramSocket? = null
            try {
                socket = DatagramSocket(null).apply {
                    reuseAddress = true
                    bind(InetSocketAddress(DISCOVERY_PORT))
                    soTimeout = 2500
                }
                val buf = ByteArray(8192)
                while (isActive && isDiscovering) {
                    try {
                        val packet = DatagramPacket(buf, buf.size)
                        socket.receive(packet)
                        val msg = String(packet.data, 0, packet.length, Charsets.UTF_8)
                        val json = JSONObject(msg)
                        when (json.optString("type")) {
                            "ZOKAS_CFD_HOST" -> {
                                val senderIp = packet.address?.hostAddress ?: continue
                                val hostName = json.optString("name", "Kasir ZoKas")
                                val peer = DiscoveredCfdPeer(
                                    endpointId = senderIp,
                                    deviceName = hostName,
                                    ipAddress = senderIp
                                )
                                val current = discoveredPeers.toMutableList()
                                if (current.none { it.endpointId == senderIp }) {
                                    current.add(peer)
                                    discoveredPeers = current
                                }
                                if (connectedEndpointId == null) {
                                    connectedEndpointId = senderIp
                                    connectedDeviceName = hostName
                                }
                                connectionStatus = "Terhubung ke $hostName ($senderIp)"
                            }
                            "ZOKAS_CFD_STATE" -> {
                                val senderIp = packet.address?.hostAddress ?: ""
                                if (connectedEndpointId == null && senderIp.isNotBlank()) {
                                    connectedEndpointId = senderIp
                                }
                                val parsed = parseStateJson(json)
                                latestReceivedState = parsed
                                parseProductBitmaps(json)
                            }
                        }
                    } catch (_: Exception) {
                    }
                }
            } catch (e: Exception) {
                connectionStatus = "Pencarian berhenti: ${e.message ?: "Port sibuk"}"
            } finally {
                try {
                    socket?.close()
                } catch (_: Exception) {
                }
            }
        }
    }

    fun startDiscoveryReceiver(context: Context, onDisconnected: (() -> Unit)? = null) {
        startDiscovery(context, onDisconnected)
    }

    fun stopDiscoveryReceiver(context: Context? = null) {
        stopAll()
    }

    fun connectToPeer(context: Context, peer: DiscoveredCfdPeer) {
        connectedEndpointId = peer.endpointId
        connectedDeviceName = peer.deviceName
        isDiscovering = false
        connectionStatus = "Terhubung ke ${peer.deviceName} (${peer.ipAddress})"
    }

    fun disconnectPeer() {
        connectedEndpointId = null
        connectedDeviceName = null
        connectionStatus = "Koneksi terputus"
        onDisconnectCallback?.invoke()
    }

    fun stopAll() {
        isHosting = false
        isAdvertising = false
        isDiscovering = false
        connectedEndpointId = null
        listenerJob?.cancel()
        advertiserJob?.cancel()
        listenerJob = null
        advertiserJob = null
    }

    fun broadcastCurrentState(context: Context, force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastBroadcastTime < 400L) return
        lastBroadcastTime = now

        if (!isHosting && connectedDeviceName == null && connectedEndpointId == null) return

        scope.launch {
            try {
                val state = LocalCfdServerManager.currentState
                val payload = buildFullStatePacket(state).toByteArray(Charsets.UTF_8)
                if (payload.size > 60000) return@launch

                DatagramSocket().use { socket ->
                    socket.broadcast = true
                    val packet = DatagramPacket(
                        payload,
                        payload.size,
                        InetAddress.getByName("255.255.255.255"),
                        DISCOVERY_PORT
                    )
                    socket.send(packet)
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun encodeThumbnailBase64(path: String): String? {
        if (path.isBlank()) return null
        return try {
            val file = File(path)
            if (!file.exists()) return null
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, opts)
            var sample = 1
            while (opts.outWidth / sample > 128 || opts.outHeight / sample > 128) {
                sample *= 2
            }
            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
            val bmp = BitmapFactory.decodeFile(file.absolutePath, decodeOpts) ?: return null
            val baos = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.JPEG, 65, baos)
            android.util.Base64.encodeToString(baos.toByteArray(), android.util.Base64.NO_WRAP)
        } catch (_: Exception) {
            null
        }
    }

    private fun buildFullStatePacket(state: CfdLiveState): String {
        val itemsArr = JSONArray()
        val thumbsObj = JSONObject()
        state.items.forEach { item ->
            itemsArr.put(
                JSONObject().apply {
                    put("productId", item.productId)
                    put("name", item.name)
                    put("variation", item.variation)
                    put("quantity", item.quantity)
                    put("unitPrice", item.unitPrice)
                    put("subtotal", item.subtotal)
                    put("notes", item.notes)
                    put("hasImage", item.imagePath.isNotBlank() && File(item.imagePath).exists())
                }
            )
            if (item.productId > 0L && item.imagePath.isNotBlank()) {
                encodeThumbnailBase64(item.imagePath)?.let { b64 ->
                    thumbsObj.put(item.productId.toString(), b64)
                }
            }
        }
        return JSONObject().apply {
            put("type", "ZOKAS_CFD_STATE")
            put("isCfdActive", state.isCfdActive)
            put("displayStage", state.displayStage)
            put("storeName", state.storeName)
            put("storeAddress", state.storeAddress)
            put("storeFooter", state.storeFooter)
            put("cashierName", state.cashierName)
            put("customerName", state.customerName)
            put("orderType", state.orderType)
            put("subtotal", state.subtotal)
            put("discount", state.discount)
            put("voucherDiscount", state.voucherDiscount)
            put("taxAmount", state.taxAmount)
            put("serviceCharge", state.serviceCharge)
            put("roundingDiff", state.roundingDiff)
            put("totalPrice", state.totalPrice)
            put("isTaxInclusive", state.isTaxInclusive)
            put("paymentMethod", state.paymentMethod)
            put("amountToPay", state.amountToPay)
            put("qrisPayload", state.qrisPayload)
            put("bankName", state.bankName)
            put("bankAccountNumber", state.bankAccountNumber)
            put("bankAccountName", state.bankAccountName)
            put("bankInfo", state.bankInfo)
            put("cashReceived", state.cashReceived)
            put("changeAmount", state.changeAmount)
            put("thankYouMessage", state.thankYouMessage)
            put("completedTimestamp", state.completedTimestamp)
            put("updatedAt", state.updatedAt)
            put("items", itemsArr)
            put("thumbnails", thumbsObj)
        }.toString()
    }

    private fun parseStateJson(json: JSONObject): CfdLiveState {
        val arr = json.optJSONArray("items") ?: JSONArray()
        val parsedItems = mutableListOf<CfdDisplayItem>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val hasImg = obj.optBoolean("hasImage", false)
            parsedItems.add(
                CfdDisplayItem(
                    productId = obj.optLong("productId", 0L),
                    name = obj.optString("name", ""),
                    variation = obj.optString("variation", ""),
                    quantity = obj.optInt("quantity", 1),
                    unitPrice = obj.optLong("unitPrice", 0L),
                    subtotal = obj.optLong("subtotal", 0L),
                    notes = obj.optString("notes", ""),
                    imagePath = if (hasImg) "remote" else ""
                )
            )
        }
        return CfdLiveState(
            storeName = json.optString("storeName", "ZoKas Store"),
            storeAddress = json.optString("storeAddress", ""),
            storeFooter = json.optString("storeFooter", "Terima Kasih atas Kunjungan Anda"),
            cashierName = json.optString("cashierName", ""),
            customerName = json.optString("customerName", "Pelanggan Umum"),
            orderType = json.optString("orderType", "Dine-In"),
            items = parsedItems,
            subtotal = json.optLong("subtotal", 0L),
            discount = json.optLong("discount", 0L),
            voucherDiscount = json.optLong("voucherDiscount", 0L),
            taxAmount = json.optLong("taxAmount", 0L),
            serviceCharge = json.optLong("serviceCharge", 0L),
            roundingDiff = json.optLong("roundingDiff", 0L),
            totalPrice = json.optLong("totalPrice", 0L),
            isTaxInclusive = json.optBoolean("isTaxInclusive", false),
            displayStage = json.optString("displayStage", "CART"),
            paymentMethod = json.optString("paymentMethod", "CASH"),
            amountToPay = json.optLong("amountToPay", 0L),
            qrisPayload = json.optString("qrisPayload", ""),
            bankName = json.optString("bankName", ""),
            bankAccountNumber = json.optString("bankAccountNumber", ""),
            bankAccountName = json.optString("bankAccountName", ""),
            bankInfo = json.optString("bankInfo", ""),
            cashReceived = json.optLong("cashReceived", 0L),
            changeAmount = json.optLong("changeAmount", 0L),
            thankYouMessage = json.optString("thankYouMessage", "Terima Kasih atas pembeliannya 🙏"),
            completedTimestamp = json.optLong("completedTimestamp", 0L),
            isCfdActive = json.optBoolean("isCfdActive", true),
            updatedAt = json.optLong("updatedAt", System.currentTimeMillis())
        )
    }

    private fun parseProductBitmaps(json: JSONObject) {
        val thumbs = json.optJSONObject("thumbnails") ?: return
        val updated = receivedProductBitmaps.toMutableMap()
        val keys = thumbs.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val prodId = key.toLongOrNull() ?: continue
            val b64 = thumbs.optString(key, "")
            if (b64.isNotBlank() && !updated.containsKey(prodId)) {
                try {
                    val bytes = android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
                    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    if (bmp != null) {
                        updated[prodId] = bmp
                    }
                } catch (_: Exception) {
                }
            }
        }
        receivedProductBitmaps = updated
    }
}
