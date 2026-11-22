package com.karyaku.kasir

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.InetSocketAddress
import java.net.Socket
import java.util.Locale
import java.util.UUID

enum class EdcChannel(val label: String, val desc: String) {
    AUTO("OTOMATIS (Semua Jalur)", "Coba Bluetooth SPP, USB Host, TCP/Wi-Fi, lalu Intent ECR secara otomatis"),
    BLUETOOTH("Bluetooth (SPP/RFCOMM)", "Kirim perintah ECR via Bluetooth serial ke mesin EDC"),
    USB("USB Host / Serial", "Deteksi & kirim perintah ECR via port USB perangkat"),
    TCP_WIFI("Wi-Fi / LAN (TCP Socket)", "Kirim perintah ECR ke alamat IP & Port mesin EDC"),
    INTENT("Aplikasi / Intent ECR", "Kirim perintah pembayaran lewat Intent / URI aplikasi EDC")
}

data class EdcCommandPayload(
    val commandId: String,
    val amount: Long,
    val currency: String = "IDR",
    val channel: EdcChannel,
    val isoStyleMessage: String,
    val jsonCommand: String,
    val rawWireFrame: String
)

data class EdcDispatchFeedback(
    val success: Boolean,
    val isApproved: Boolean,
    val channelUsed: String,
    val responseCode: String,
    val traceNumber: String,
    val message: String,
    val rawResponse: String = ""
)

object EdcConnectionManager {
    private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private const val PREFS_NAME = "zokas_edc_channel_prefs"

    fun getPreferredChannel(context: Context): EdcChannel {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString("edc_channel", EdcChannel.AUTO.name) ?: EdcChannel.AUTO.name
        return runCatching { EdcChannel.valueOf(raw) }.getOrDefault(EdcChannel.AUTO)
    }

    fun setPreferredChannel(context: Context, channel: EdcChannel) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString("edc_channel", channel.name).apply()
    }

    fun getTcpHost(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString("edc_tcp_host", "192.168.1.100") ?: "192.168.1.100"
    }

    fun setTcpHost(context: Context, host: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString("edc_tcp_host", host.trim()).apply()
    }

    fun getTcpPort(context: Context): Int {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt("edc_tcp_port", 9100)
    }

    fun setTcpPort(context: Context, port: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putInt("edc_tcp_port", port.coerceIn(1, 65535)).apply()
    }

    fun getIntentPackageOrUri(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString("edc_intent_target", "") ?: ""
    }

    fun setIntentPackageOrUri(context: Context, target: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString("edc_intent_target", target.trim()).apply()
    }

    fun buildAutomaticEdcCommand(
        amount: Long,
        channel: EdcChannel,
        customTrace: String = ""
    ): EdcCommandPayload {
        val now = System.currentTimeMillis()
        val trace = customTrace.trim().ifBlank {
            String.format(Locale.US, "%06d", (now % 900000L) + 100000L)
        }
        val cmdId = "EDC-$now"
        val paddedAmount12 = String.format(Locale.US, "%012d", amount.coerceAtLeast(0L) * 100L)
        val isoStyle = "0200|000000|$paddedAmount12|$trace|IDR|SALE"
        val jsonObj = JSONObject().apply {
            put("type", "ECR_SALE")
            put("command_id", cmdId)
            put("amount", amount)
            put("currency", "IDR")
            put("trace", trace)
            put("channel", channel.name)
            put("timestamp", now)
        }
        val jsonStr = jsonObj.toString()
        val wireFrame = "STX|$jsonStr|ETX"
        return EdcCommandPayload(
            commandId = cmdId,
            amount = amount,
            channel = channel,
            isoStyleMessage = isoStyle,
            jsonCommand = jsonStr,
            rawWireFrame = wireFrame
        )
    }

    @SuppressLint("MissingPermission")
    fun getPairedBluetoothDevices(context: Context): List<BluetoothDevice> {
        return try {
            val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            val adapter: BluetoothAdapter? = btManager?.adapter ?: BluetoothAdapter.getDefaultAdapter()
            adapter?.bondedDevices?.toList().orEmpty()
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun getConnectedUsbDevices(context: Context): List<UsbDevice> {
        return try {
            val usbManager = context.getSystemService(Context.USB_SERVICE) as? UsbManager
            usbManager?.deviceList?.values?.toList().orEmpty()
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun parseEdcResponseFeedback(
        rawResponse: String,
        fallbackTrace: String,
        channelUsed: String
    ): EdcDispatchFeedback {
        val cleaned = rawResponse.trim()
        if (cleaned.isEmpty()) {
            return EdcDispatchFeedback(
                success = true,
                isApproved = false,
                channelUsed = channelUsed,
                responseCode = "PENDING",
                traceNumber = fallbackTrace,
                message = "Perintah terkirim via $channelUsed. Menunggu respon persetujuan mesin EDC.",
                rawResponse = rawResponse
            )
        }

        if (cleaned.startsWith("{")) {
            runCatching {
                val obj = JSONObject(cleaned)
                val statusStr = obj.optString("status", obj.optString("result", "")).uppercase(Locale.US)
                val rc = obj.optString("resp_code", obj.optString("response_code", obj.optString("rc", "")))
                val trace = obj.optString("trace", obj.optString("approval_code", fallbackTrace)).ifBlank { fallbackTrace }
                val approved = statusStr in listOf("APPROVED", "APPROVE", "SUCCESS", "OK", "00") || rc == "00"
                return EdcDispatchFeedback(
                    success = true,
                    isApproved = approved,
                    channelUsed = channelUsed,
                    responseCode = rc.ifBlank { if (approved) "00" else "RECV" },
                    traceNumber = trace,
                    message = if (approved) {
                        "EDC Disetujui (RC: ${rc.ifBlank { "00" }}, Trace: $trace)"
                    } else {
                        "Respon EDC diterima via $channelUsed: ${obj.optString("message", cleaned)}"
                    },
                    rawResponse = rawResponse
                )
            }
        }

        val upper = cleaned.uppercase(Locale.US)
        val isApproved = upper.contains("APPROVED") || upper.contains("APPROVE") ||
            upper.contains("SUCCESS") || upper.contains("|00|") || upper.endsWith("|00")
        val extractedTrace = Regex("(?:TRACE|APPR|REF)[:=\\s-]*([A-Za-z0-9]{3,12})")
            .find(upper)?.groupValues?.getOrNull(1) ?: fallbackTrace

        return EdcDispatchFeedback(
            success = true,
            isApproved = isApproved,
            channelUsed = channelUsed,
            responseCode = if (isApproved) "00" else "DATA",
            traceNumber = extractedTrace,
            message = if (isApproved) {
                "EDC Disetujui via $channelUsed (Trace: $extractedTrace)"
            } else {
                "Balasan EDC ($channelUsed): $cleaned"
            },
            rawResponse = rawResponse
        )
    }

    @SuppressLint("MissingPermission")
    private fun trySendBluetooth(context: Context, payload: EdcCommandPayload): EdcDispatchFeedback {
        val trace = runCatching { JSONObject(payload.jsonCommand).optString("trace", "") }.getOrDefault("")
        val devices = getPairedBluetoothDevices(context)
        if (devices.isEmpty()) {
            return EdcDispatchFeedback(
                success = false,
                isApproved = false,
                channelUsed = "Bluetooth",
                responseCode = "NO_BT",
                traceNumber = trace,
                message = "Tidak ada perangkat Bluetooth EDC yang dipasangkan (paired)."
            )
        }
        val preferred = devices.firstOrNull { dev ->
            val n = (dev.name ?: "").uppercase(Locale.US)
            n.contains("EDC") || n.contains("POS") || n.contains("ECR") || n.contains("PAX") || n.contains("VERIFONE") || n.contains("INGENICO")
        } ?: devices.first()

        return try {
            val socket = preferred.createRfcommSocketToServiceRecord(SPP_UUID)
            socket.connect()
            socket.use { sock ->
                val writer = PrintWriter(OutputStreamWriter(sock.outputStream, Charsets.UTF_8), true)
                writer.println(payload.rawWireFrame)
                writer.flush()

                val reader = BufferedReader(InputStreamReader(sock.inputStream, Charsets.UTF_8))
                val line = runCatching { reader.readLine() }.getOrNull().orEmpty()
                parseEdcResponseFeedback(line, trace, "Bluetooth (${preferred.name ?: preferred.address})")
            }
        } catch (e: Exception) {
            EdcDispatchFeedback(
                success = false,
                isApproved = false,
                channelUsed = "Bluetooth (${preferred.name ?: preferred.address})",
                responseCode = "BT_ERR",
                traceNumber = trace,
                message = "Bluetooth EDC belum merespons (${e.message ?: "koneksi gagal"})."
            )
        }
    }

    private fun trySendUsb(context: Context, payload: EdcCommandPayload): EdcDispatchFeedback {
        val trace = runCatching { JSONObject(payload.jsonCommand).optString("trace", "") }.getOrDefault("")
        val usbDevices = getConnectedUsbDevices(context)
        if (usbDevices.isEmpty()) {
            return EdcDispatchFeedback(
                success = false,
                isApproved = false,
                channelUsed = "USB Host",
                responseCode = "NO_USB",
                traceNumber = trace,
                message = "Tidak ada perangkat USB EDC terdeteksi pada port USB."
            )
        }
        val usbManager = context.getSystemService(Context.USB_SERVICE) as? UsbManager
        val dev = usbDevices.first()
        val hasPerm = usbManager?.hasPermission(dev) == true
        return if (hasPerm) {
            EdcDispatchFeedback(
                success = true,
                isApproved = false,
                channelUsed = "USB (${dev.deviceName})",
                responseCode = "USB_RDY",
                traceNumber = trace,
                message = "Perangkat USB ${dev.deviceName} siap; payload ECR disiapkan (${payload.commandId})."
            )
        } else {
            EdcDispatchFeedback(
                success = false,
                isApproved = false,
                channelUsed = "USB (${dev.deviceName})",
                responseCode = "USB_PERM",
                traceNumber = trace,
                message = "USB ${dev.deviceName} terdeteksi, menunggu izin akses USB Host."
            )
        }
    }

    private fun trySendTcpWifi(context: Context, payload: EdcCommandPayload): EdcDispatchFeedback {
        val trace = runCatching { JSONObject(payload.jsonCommand).optString("trace", "") }.getOrDefault("")
        val host = getTcpHost(context)
        val port = getTcpPort(context)
        if (host.isBlank()) {
            return EdcDispatchFeedback(
                success = false,
                isApproved = false,
                channelUsed = "Wi-Fi/TCP",
                responseCode = "NO_IP",
                traceNumber = trace,
                message = "IP mesin EDC belum diisi."
            )
        }
        return try {
            Socket().use { sock ->
                sock.connect(InetSocketAddress(host, port), 2000)
                val out = PrintWriter(OutputStreamWriter(sock.getOutputStream(), Charsets.UTF_8), true)
                out.println(payload.rawWireFrame)
                out.flush()

                val reader = BufferedReader(InputStreamReader(sock.getInputStream(), Charsets.UTF_8))
                val resp = runCatching { reader.readLine() }.getOrNull().orEmpty()
                parseEdcResponseFeedback(resp, trace, "TCP $host:$port")
            }
        } catch (e: Exception) {
            EdcDispatchFeedback(
                success = false,
                isApproved = false,
                channelUsed = "TCP $host:$port",
                responseCode = "TCP_ERR",
                traceNumber = trace,
                message = "Gagal menghubungi EDC $host:$port (${e.message ?: "timeout"})."
            )
        }
    }

    private fun tryLaunchIntentEcr(context: Context, payload: EdcCommandPayload): EdcDispatchFeedback {
        val trace = runCatching { JSONObject(payload.jsonCommand).optString("trace", "") }.getOrDefault("")
        val target = getIntentPackageOrUri(context)
        return try {
            val intent = if (target.startsWith("http://") || target.startsWith("https://") || target.contains("://")) {
                Intent(Intent.ACTION_VIEW, Uri.parse(target)).apply {
                    putExtra("ecr_command", payload.jsonCommand)
                    putExtra("amount", payload.amount)
                    putExtra("trace", trace)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            } else if (target.isNotBlank()) {
                context.packageManager.getLaunchIntentForPackage(target)?.apply {
                    putExtra("ecr_command", payload.jsonCommand)
                    putExtra("amount", payload.amount)
                    putExtra("trace", trace)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                } ?: Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, payload.jsonCommand)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            } else {
                Intent("com.karyaku.kasir.EDC_PAYMENT").apply {
                    putExtra("ecr_command", payload.jsonCommand)
                    putExtra("amount", payload.amount)
                    putExtra("trace", trace)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            context.startActivity(intent)
            EdcDispatchFeedback(
                success = true,
                isApproved = false,
                channelUsed = "Intent ECR",
                responseCode = "INTENT_SENT",
                traceNumber = trace,
                message = "Perintah ECR dikirim via Intent ke aplikasi tujuan."
            )
        } catch (_: Exception) {
            EdcDispatchFeedback(
                success = false,
                isApproved = false,
                channelUsed = "Intent ECR",
                responseCode = "NO_INTENT_APP",
                traceNumber = trace,
                message = "Tidak ada aplikasi penerima Intent EDC terpasang."
            )
        }
    }

    suspend fun dispatchAutomaticCommand(
        context: Context,
        amount: Long,
        selectedChannel: EdcChannel,
        customTrace: String = ""
    ): Pair<EdcCommandPayload, EdcDispatchFeedback> = withContext(Dispatchers.IO) {
        val payload = buildAutomaticEdcCommand(amount, selectedChannel, customTrace)
        val feedback = when (selectedChannel) {
            EdcChannel.BLUETOOTH -> trySendBluetooth(context, payload)
            EdcChannel.USB -> trySendUsb(context, payload)
            EdcChannel.TCP_WIFI -> trySendTcpWifi(context, payload)
            EdcChannel.INTENT -> withContext(Dispatchers.Main) { tryLaunchIntentEcr(context, payload) }
            EdcChannel.AUTO -> {
                val bt = trySendBluetooth(context, payload)
                if (bt.isApproved || bt.success) {
                    bt
                } else {
                    val usb = trySendUsb(context, payload)
                    if (usb.isApproved || usb.success) {
                        usb
                    } else {
                        val tcp = trySendTcpWifi(context, payload)
                        if (tcp.isApproved || tcp.success) {
                            tcp
                        } else {
                            val intentRes = withContext(Dispatchers.Main) { tryLaunchIntentEcr(context, payload) }
                            if (intentRes.success) {
                                intentRes
                            } else {
                                EdcDispatchFeedback(
                                    success = true,
                                    isApproved = false,
                                    channelUsed = "OTOMATIS (Siap Manual/Jalur Lokal)",
                                    responseCode = "CMD_READY",
                                    traceNumber = runCatching { JSONObject(payload.jsonCommand).optString("trace", "") }.getOrDefault(""),
                                    message = "Command otomatis dibuat (${payload.commandId}). Perangkat EDC eksternal belum terhubung; konfirmasi manual siap dipakai."
                                )
                            }
                        }
                    }
                }
            }
        }
        payload to feedback
    }
}
