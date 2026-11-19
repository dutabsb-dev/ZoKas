package com.karyaku.kasir

import android.content.Context
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
import org.json.JSONObject
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
 * P2pCfdManager (Zero-Dependency Native Android UDP/LAN Broadcast & Direct Peer Sync)
 * Menggunakan UDP Broadcast & Direct Socket standar Android SDK sehingga dapat dikompilasi
 * langsung tanpa memerlukan dependensi tambahan Google Play Services Nearby.
 */
object P2pCfdManager {
    private const val DISCOVERY_PORT = 48921
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var listenerJob: Job? = null
    private var advertiserJob: Job? = null
    private var lastBroadcastTime = 0L

    var isHosting by mutableStateOf(false)
        private set

    var isDiscovering by mutableStateOf(false)
        private set

    var connectionStatus by mutableStateOf("Siap menghubungkan layar pelanggan")
        private set

    var connectedDeviceName by mutableStateOf<String?>(null)
        private set

    var discoveredPeers by mutableStateOf<List<DiscoveredCfdPeer>>(emptyList())
        private set

    fun startHosting(context: Context, storeName: String) {
        stopAll()
        isHosting = true
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

    fun startDiscovery(context: Context) {
        stopAll()
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
                val buf = ByteArray(2048)
                while (isActive && isDiscovering) {
                    try {
                        val packet = DatagramPacket(buf, buf.size)
                        socket.receive(packet)
                        val msg = String(packet.data, 0, packet.length, Charsets.UTF_8)
                        val json = JSONObject(msg)
                        if (json.optString("type") == "ZOKAS_CFD_HOST") {
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
                            connectionStatus = "Ditemukan ${discoveredPeers.size} perangkat kasir"
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

    fun connectToPeer(context: Context, peer: DiscoveredCfdPeer) {
        connectedDeviceName = peer.deviceName
        isDiscovering = false
        connectionStatus = "Terhubung ke ${peer.deviceName} (${peer.ipAddress})"
    }

    fun disconnectPeer() {
        connectedDeviceName = null
        connectionStatus = "Koneksi terputus"
    }

    fun stopAll() {
        isHosting = false
        isDiscovering = false
        listenerJob?.cancel()
        advertiserJob?.cancel()
        listenerJob = null
        advertiserJob = null
    }

    fun broadcastCurrentState(context: Context, force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastBroadcastTime < 400L) return
        lastBroadcastTime = now

        if (!isHosting && connectedDeviceName == null) return

        scope.launch {
            try {
                val state = LocalCfdServerManager.liveState
                val payload = JSONObject().apply {
                    put("type", "ZOKAS_CFD_STATE")
                    put("stage", state.displayStage)
                    put("storeName", state.storeName)
                    put("totalPrice", state.totalPrice)
                    put("itemCount", state.items.sumOf { it.quantity })
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
        }
    }
}
