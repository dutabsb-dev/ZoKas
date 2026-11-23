package com.karyaku.kasir

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.util.UUID

data class PrinterDiagnosticReport(
    val canPrint: Boolean,
    val stepFailed: String,
    val title: String,
    val detail: String,
    val recommendedAction: String
)

object BluetoothThermalPrinterHelper {
    private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    fun resolveBluetoothAdapter(context: Context): BluetoothAdapter? {
        val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        return btManager?.adapter ?: BluetoothAdapter.getDefaultAdapter()
    }

    fun hasBluetoothConnectPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.BLUETOOTH_CONNECT
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        return true
    }

    fun getRequiredRuntimePermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                android.Manifest.permission.BLUETOOTH_CONNECT,
                android.Manifest.permission.BLUETOOTH_SCAN
            )
        } else {
            emptyArray()
        }
    }

    @SuppressLint("MissingPermission")
    fun runDeepDiagnostic(context: Context, savedMac: String?): PrinterDiagnosticReport {
        val adapter = resolveBluetoothAdapter(context)
            ?: return PrinterDiagnosticReport(
                canPrint = false,
                stepFailed = "ADAPTER_MISSING",
                title = "Perangkat Tidak Mendukung Bluetooth",
                detail = "Adapter Bluetooth tidak ditemukan pada perangkat ini.",
                recommendedAction = "Gunakan cetak struk PDF / Bagikan ke WhatsApp atau koneksi jaringan."
            )

        if (!hasBluetoothConnectPermission(context)) {
            return PrinterDiagnosticReport(
                canPrint = false,
                stepFailed = "PERMISSION_DENIED",
                title = "Izin Perangkat Terdekat (BLUETOOTH_CONNECT) Belum Diberikan",
                detail = "Pada Android 12+, aplikasi wajib mendapat izin BLUETOOTH_CONNECT untuk mengakses printer Bluetooth.",
                recommendedAction = "Berikan izin 'Perangkat di sekitar / Bluetooth' pada dialog izin atau Pengaturan Aplikasi -> Izin."
            )
        }

        if (!adapter.isEnabled) {
            return PrinterDiagnosticReport(
                canPrint = false,
                stepFailed = "BT_DISABLED",
                title = "Bluetooth Perangkat Masih Mati",
                detail = "Bluetooth HP/Tablet dalam kondisi OFF sehingga tidak dapat membuka soket ke printer thermal.",
                recommendedAction = "Nyalakan Bluetooth perangkat lalu coba cetak ulang."
            )
        }

        val cleanMac = savedMac?.trim().orEmpty()
        if (cleanMac.isBlank()) {
            return PrinterDiagnosticReport(
                canPrint = false,
                stepFailed = "MAC_NOT_SET",
                title = "Printer Thermal Belum Dipilih",
                detail = "Alamat MAC printer kasir/dapur belum tersimpan di pengaturan aplikasi.",
                recommendedAction = "Buka Pengaturan -> Printer Kasir/Dapur -> Pilih Printer yang sudah di-Pair."
            )
        }

        val bonded = runCatching { adapter.bondedDevices.orEmpty() }.getOrDefault(emptySet())
        if (bonded.isEmpty()) {
            return PrinterDiagnosticReport(
                canPrint = false,
                stepFailed = "NO_PAIRED_DEVICES",
                title = "Belum Ada Perangkat Bluetooth Ter-Pair",
                detail = "Daftar perangkat Bluetooth yang dipasangkan kosong.",
                recommendedAction = "Nyalakan printer thermal, buka Pengaturan Bluetooth HP, lalu Pair printer terlebih dahulu."
            )
        }

        val matched = bonded.find { it.address.equals(cleanMac, ignoreCase = true) }
        if (matched == null) {
            val pairedList = bonded.joinToString(", ") { "${it.name ?: "Unknown"} (${it.address})" }
            return PrinterDiagnosticReport(
                canPrint = false,
                stepFailed = "MAC_NOT_BONDED",
                title = "Printer Tersimpan ($cleanMac) Tidak Ditemukan di Daftar Pair",
                detail = "Perangkat yang ter-pair saat ini: $pairedList.",
                recommendedAction = "Pair ulang printer thermal di pengaturan Bluetooth HP atau pilih ulang printer di menu Pengaturan."
            )
        }

        return PrinterDiagnosticReport(
            canPrint = true,
            stepFailed = "NONE",
            title = "Siap Cetak (${matched.name ?: matched.address})",
            detail = "Izin aktif, Bluetooth ON, dan MAC ${matched.address} terdaftar di perangkat.",
            recommendedAction = "Jika masih gagal saat konek, pastikan printer menyala, kertas terpasang, dan tidak sedang dipakai HP lain."
        )
    }

    @SuppressLint("MissingPermission")
    fun openResilientRfcommSocket(adapter: BluetoothAdapter, device: BluetoothDevice): BluetoothSocket {
        runCatching {
            if (adapter.isDiscovering) {
                adapter.cancelDiscovery()
            }
        }

        var lastError: Throwable? = null

        // Jalur 1: Standar SPP UUID (00001101-0000-1000-8000-00805F9B34FB)
        try {
            val sock = device.createRfcommSocketToServiceRecord(SPP_UUID)
            sock.connect()
            return sock
        } catch (e: Throwable) {
            lastError = e
        }

        // Jalur 2: Channel RFCOMM langsung (channel 1) via reflection untuk printer thermal tertentu
        try {
            val method = device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
            val fallbackSock = method.invoke(device, 1) as BluetoothSocket
            fallbackSock.connect()
            return fallbackSock
        } catch (e: Throwable) {
            lastError = e
        }

        // Jalur 3: Insecure RFCOMM SPP socket
        try {
            val insecureSock = device.createInsecureRfcommSocketToServiceRecord(SPP_UUID)
            insecureSock.connect()
            return insecureSock
        } catch (e: Throwable) {
            lastError = e
        }

        throw IllegalStateException(
            lastError?.message ?: "Gagal membuka koneksi RFCOMM ke ${device.name ?: device.address}"
        )
    }

    fun writeChunkedEscPos(outputStream: OutputStream, data: ByteArray, chunkSize: Int = 128) {
        var offset = 0
        while (offset < data.size) {
            val len = minOf(chunkSize, data.size - offset)
            outputStream.write(data, offset, len)
            outputStream.flush()
            offset += len
            if (offset < data.size) {
                try {
                    Thread.sleep(12L)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    break
                }
            }
        }
        outputStream.flush()
    }
}

class BluetoothPrinterManager(private val context: Context) {
    private val printerUUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    @SuppressLint("MissingPermission")
    fun getPairedPrinters(): List<BluetoothDevice> {
        return try {
            val adapter = BluetoothThermalPrinterHelper.resolveBluetoothAdapter(context)
            if (adapter != null && adapter.isEnabled && BluetoothThermalPrinterHelper.hasBluetoothConnectPermission(context)) {
                adapter.bondedDevices?.toList() ?: emptyList()
            } else {
                emptyList()
            }
        } catch (_: Throwable) {
            emptyList()
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun printData(deviceAddress: String, data: ByteArray): Boolean = withContext(Dispatchers.IO) {
        var socket: BluetoothSocket? = null
        try {
            val report = BluetoothThermalPrinterHelper.runDeepDiagnostic(context, deviceAddress)
            if (!report.canPrint) return@withContext false

            val adapter = BluetoothThermalPrinterHelper.resolveBluetoothAdapter(context) ?: return@withContext false
            val device = adapter.bondedDevices?.find { it.address.equals(deviceAddress.trim(), ignoreCase = true) }
                ?: adapter.getRemoteDevice(deviceAddress.trim())

            socket = BluetoothThermalPrinterHelper.openResilientRfcommSocket(adapter, device)
            val outputStream: OutputStream = socket.outputStream
            BluetoothThermalPrinterHelper.writeChunkedEscPos(outputStream, data)
            try { socket.close() } catch (_: Throwable) {}
            true
        } catch (e: Throwable) {
            e.printStackTrace()
            try { socket?.close() } catch (_: Throwable) {}
            false
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun openCashDrawer(deviceAddress: String): Boolean = withContext(Dispatchers.IO) {
        val drawerCmd = byteArrayOf(0x1B, 0x70, 0x00, 0x19, 0xFA.toByte())
        return@withContext printData(deviceAddress, drawerCmd)
    }
}

object EscPosCommands {
    val INIT = byteArrayOf(0x1B, 0x40)
    val ALIGN_LEFT = byteArrayOf(0x1B, 0x61, 0x00)
    val ALIGN_CENTER = byteArrayOf(0x1B, 0x61, 0x01)
    val ALIGN_RIGHT = byteArrayOf(0x1B, 0x61, 0x02)
    val BOLD_ON = byteArrayOf(0x1B, 0x45, 0x01)
    val BOLD_OFF = byteArrayOf(0x1B, 0x45, 0x00)
    val FEED_3 = byteArrayOf(0x1B, 0x64, 0x03)
    val CUT_PAPER = byteArrayOf(0x1D, 0x56, 0x42, 0x00)

    fun printImage(bitmap: Bitmap): ByteArray {
        val outputStream = ByteArrayOutputStream()
        val width = bitmap.width
        val height = bitmap.height
        val bytesPerRow = (width + 7) / 8

        outputStream.write(byteArrayOf(0x1D, 0x76, 0x30, 0x00))
        outputStream.write(byteArrayOf((bytesPerRow and 0xFF).toByte(), ((bytesPerRow shr 8) and 0xFF).toByte()))
        outputStream.write(byteArrayOf((height and 0xFF).toByte(), ((height shr 8) and 0xFF).toByte()))

        for (y in 0 until height) {
            for (bIdx in 0 until bytesPerRow) {
                var byteVal = 0
                val xBase = bIdx * 8
                for (bit in 0 until 8) {
                    val x = xBase + bit
                    if (x < width) {
                        val pixel = bitmap.getPixel(x, y)
                        val r = (pixel shr 16) and 0xff
                        val g = (pixel shr 8) and 0xff
                        val b = pixel and 0xff
                        if ((r + g + b) / 3 < 128) {
                            byteVal = byteVal or (0x80 shr bit)
                        }
                    }
                }
                outputStream.write(byteVal)
            }
        }
        return outputStream.toByteArray()
    }
}
