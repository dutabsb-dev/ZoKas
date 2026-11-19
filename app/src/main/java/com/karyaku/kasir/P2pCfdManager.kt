package com.karyaku.kasir

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Base64
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * P2pCfdManager (Offline Peer-to-Peer Tanpa Server & Tanpa Harus Satu Wi-Fi)
 * Menggunakan Google Nearby Connections (P2P_STAR) yang menggabungkan Bluetooth + Wi-Fi Direct.
 * HP Kasir bisa tetap memakai 4G dan Tablet CFD bisa tetap memakai Wi-Fi terpisah (atau tanpa internet sama sekali).
 */
object P2pCfdManager {
    private const val SERVICE_ID = "com.dbsbstudio.zokas.cfd.p2p"

    var isAdvertising by mutableStateOf(false)
        private set

    var isDiscovering by mutableStateOf(false)
        private set

    var connectedEndpointId by mutableStateOf<String?>(null)
        private set

    var connectedEndpointName by mutableStateOf("")
        private set

    var p2pStatusMessage by mutableStateOf("Siap menghubungkan langsung antar perangkat (Wi-Fi Direct / Bluetooth)")
        private set

    var latestReceivedState by mutableStateOf<CfdLiveState?>(null)
        private set

    // Cache Base64 thumbnail agar tidak berulang kali mengompres gambar yang sama
    private val base64ThumbCache = ConcurrentHashMap<String, String>()
    // Peta productId -> decoded Bitmap pada perangkat penerima (Tablet CFD)
    val receivedProductBitmaps = ConcurrentHashMap<Long, Bitmap>()

    private var lastBroadcastSignature: String = ""

    fun getRequiredPermissions(): Array<String> {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.NEARBY_WIFI_DEVICES,
                Manifest.permission.ACCESS_WIFI_STATE,
                Manifest.permission.CHANGE_WIFI_STATE
            )
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
            else -> arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        }
    }

    fun hasAllPermissions(context: Context): Boolean {
        return getRequiredPermissions().all { perm ->
            ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
        }
    }

    // =========================================================================
    // MODE PENGIRIM / KASIR (ADVERTISER)
    // =========================================================================
    fun startAdvertisingHost(context: Context, storeName: String) {
        val client: ConnectionsClient = Nearby.getConnectionsClient(context)
        client.stopAdvertising()

        val options = AdvertisingOptions.Builder()
            .setStrategy(Strategy.P2P_STAR)
            .build()

        val hostEndpointName = "ZoKas-Kasir-${storeName.take(18)}"
        client.startAdvertising(
            hostEndpointName,
            SERVICE_ID,
            hostConnectionLifecycleCallback(client, context),
            options
        ).addOnSuccessListener {
            isAdvertising = true
            p2pStatusMessage = "Menunggu Tablet CFD terhubung secara langsung..."
        }.addOnFailureListener { e ->
            isAdvertising = false
            p2pStatusMessage = "Gagal memulai P2P: ${e.localizedMessage ?: "Periksa izin Bluetooth & Lokasi"}"
        }
    }

    fun stopAdvertisingHost(context: Context) {
        try {
            // Kirim sinyal nonaktif ke klien jika sedang terhubung
            connectedEndpointId?.let { epId ->
                val stopJson = JSONObject().put("isCfdActive", false).toString()
                Nearby.getConnectionsClient(context)
                    .sendPayload(epId, Payload.fromBytes(stopJson.toByteArray(Charsets.UTF_8)))
            }
            Nearby.getConnectionsClient(context).stopAdvertising()
            Nearby.getConnectionsClient(context).stopAllEndpoints()
        } catch (_: Exception) {
        }
        isAdvertising = false
        connectedEndpointId = null
        connectedEndpointName = ""
        lastBroadcastSignature = ""
        p2pStatusMessage = "Transmisi P2P dihentikan"
    }

    private fun hostConnectionLifecycleCallback(
        client: ConnectionsClient,
        context: Context
    ) = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            // Otomatis menerima koneksi dari Tablet CFD ZoKas
            client.acceptConnection(endpointId, receiverPayloadCallback)
            p2pStatusMessage = "Menyambungkan dengan ${info.endpointName}..."
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            if (result.status.isSuccess) {
                connectedEndpointId = endpointId
                connectedEndpointName = "Layar Pelanggan Terhubung"
                p2pStatusMessage = "Terhubung Langsung (Wi-Fi Direct / Bluetooth)"
                lastBroadcastSignature = ""
                // Langsung kirim state terkini saat baru konek
                broadcastCurrentState(context, force = true)
            } else {
                p2pStatusMessage = "Koneksi P2P ditolak atau terputus."
            }
        }

        override fun onDisconnected(endpointId: String) {
            if (connectedEndpointId == endpointId) {
                connectedEndpointId = null
                connectedEndpointName = ""
                lastBroadcastSignature = ""
                p2pStatusMessage = "Perangkat CFD terputus. Menunggu koneksi ulang..."
            }
        }
    }

    fun broadcastCurrentState(context: Context, force: Boolean = false) {
        val epId = connectedEndpointId ?: return
        try {
            val state = LocalCfdServerManager.currentState
            val jsonStr = buildP2pStateJson(state)
            if (!force && jsonStr == lastBroadcastSignature) return
            lastBroadcastSignature = jsonStr
            val payload = Payload.fromBytes(jsonStr.toByteArray(Charsets.UTF_8))
            Nearby.getConnectionsClient(context).sendPayload(epId, payload)
        } catch (_: Exception) {
        }
    }

    // =========================================================================
    // MODE PENERIMA / TABLET CFD (DISCOVERER)
    // =========================================================================
    fun startDiscoveryReceiver(context: Context, onHostDisconnected: () -> Unit) {
        val client: ConnectionsClient = Nearby.getConnectionsClient(context)
        client.stopDiscovery()

        val options = DiscoveryOptions.Builder()
            .setStrategy(Strategy.P2P_STAR)
            .build()

        client.startDiscovery(
            SERVICE_ID,
            object : EndpointDiscoveryCallback() {
                override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
                    p2pStatusMessage = "Menemukan ${info.endpointName}, menghubungkan..."
                    client.requestConnection(
                        "ZoKas-Display",
                        endpointId,
                        object : ConnectionLifecycleCallback() {
                            override fun onConnectionInitiated(epId: String, connInfo: ConnectionInfo) {
                                client.acceptConnection(epId, receiverPayloadCallback)
                            }

                            override fun onConnectionResult(epId: String, result: ConnectionResolution) {
                                if (result.status.isSuccess) {
                                    connectedEndpointId = epId
                                    connectedEndpointName = info.endpointName
                                    isDiscovering = false
                                    client.stopDiscovery()
                                    p2pStatusMessage = "Terhubung Langsung ke Kasir"
                                } else {
                                    p2pStatusMessage = "Gagal menyambung, mencari ulang..."
                                }
                            }

                            override fun onDisconnected(epId: String) {
                                connectedEndpointId = null
                                connectedEndpointName = ""
                                p2pStatusMessage = "Koneksi terputus, mencari ulang kasir..."
                                onHostDisconnected()
                                // Otomatis mencari ulang agar auto-reconnect saat aplikasi kasir dibuka lagi
                                startDiscoveryReceiver(context, onHostDisconnected)
                            }
                        }
                    )
                }

                override fun onEndpointLost(endpointId: String) {
                    p2pStatusMessage = "Mencari perangkat kasir di sekitar..."
                }
            },
            options
        ).addOnSuccessListener {
            isDiscovering = true
            p2pStatusMessage = "Mencari HP Kasir di sekitar (Tanpa Server / Wi-Fi Direct)..."
        }.addOnFailureListener { e ->
            isDiscovering = false
            p2pStatusMessage = "Gagal mencari perangkat: ${e.localizedMessage ?: "Aktifkan Bluetooth & Wi-Fi"}"
        }
    }

    fun stopDiscoveryReceiver(context: Context) {
        try {
            Nearby.getConnectionsClient(context).stopDiscovery()
            Nearby.getConnectionsClient(context).stopAllEndpoints()
        } catch (_: Exception) {
        }
        isDiscovering = false
        connectedEndpointId = null
        connectedEndpointName = ""
        latestReceivedState = null
    }

    private val receiverPayloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            if (payload.type == Payload.Type.BYTES) {
                val bytes = payload.asBytes() ?: return
                val raw = String(bytes, Charsets.UTF_8)
                parseIncomingStateJson(raw)
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
        }
    }

    private fun getBase64Thumbnail(path: String): String {
        if (path.isBlank()) return ""
        base64ThumbCache[path]?.let { return it }
        return try {
            val file = File(path)
            if (!file.exists()) return ""
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, opts)
            var sample = 1
            while (opts.outWidth / sample > 120 || opts.outHeight / sample > 120) {
                sample *= 2
            }
            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
            val bmp = BitmapFactory.decodeFile(file.absolutePath, decodeOpts) ?: return ""
            val baos = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.JPEG, 65, baos)
            val encoded = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
            base64ThumbCache[path] = encoded
            encoded
        } catch (_: Exception) {
            ""
        }
    }

    private fun buildP2pStateJson(state: CfdLiveState): String {
        val root = JSONObject()
        root.put("isCfdActive", state.isCfdActive)
        root.put("displayStage", state.displayStage)
        root.put("storeName", state.storeName)
        root.put("storeAddress", state.storeAddress)
        root.put("storeFooter", state.storeFooter)
        root.put("cashierName", state.cashierName)
        root.put("customerName", state.customerName)
        root.put("orderType", state.orderType)
        root.put("subtotalRaw", state.subtotal)
        root.put("discountRaw", state.discount)
        root.put("voucherDiscountRaw", state.voucherDiscount)
        root.put("taxAmountRaw", state.taxAmount)
        root.put("isTaxInclusive", state.isTaxInclusive)
        root.put("serviceChargeRaw", state.serviceCharge)
        root.put("roundingDiffRaw", state.roundingDiff)
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
            obj.put("unitPriceRaw", item.unitPrice)
            obj.put("subtotalRaw", item.subtotal)
            obj.put("notes", item.notes)
            val b64 = getBase64Thumbnail(item.imagePath)
            if (b64.isNotEmpty()) {
                obj.put("thumbB64", b64)
            }
            arr.put(obj)
        }
        root.put("items", arr)
        return root.toString()
    }

    private fun parseIncomingStateJson(raw: String) {
        try {
            val json = JSONObject(raw)
            val active = json.optBoolean("isCfdActive", true)
            if (!active) {
                latestReceivedState = CfdLiveState(isCfdActive = false)
                return
            }
            val itemsArr = json.optJSONArray("items") ?: JSONArray()
            val parsedItems = mutableListOf<CfdDisplayItem>()
            for (i in 0 until itemsArr.length()) {
                val obj = itemsArr.getJSONObject(i)
                val pid = obj.optLong("productId", 0L)
                val b64 = obj.optString("thumbB64", "")
                if (pid > 0L && b64.isNotEmpty() && !receivedProductBitmaps.containsKey(pid)) {
                    try {
                        val imgBytes = Base64.decode(b64, Base64.DEFAULT)
                        val bmp = BitmapFactory.decodeByteArray(imgBytes, 0, imgBytes.size)
                        if (bmp != null) {
                            receivedProductBitmaps[pid] = bmp
                        }
                    } catch (_: Exception) {
                    }
                }
                parsedItems.add(
                    CfdDisplayItem(
                        productId = pid,
                        name = obj.optString("name"),
                        variation = obj.optString("variation"),
                        quantity = obj.optInt("quantity"),
                        unitPrice = obj.optLong("unitPriceRaw"),
                        subtotal = obj.optLong("subtotalRaw"),
                        notes = obj.optString("notes"),
                        imagePath = if (b64.isNotEmpty() || receivedProductBitmaps.containsKey(pid)) "p2p" else ""
                    )
                )
            }

            latestReceivedState = CfdLiveState(
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
        } catch (_: Exception) {
        }
    }
}
