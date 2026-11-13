package com.karyaku.kasir

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.util.UUID

class BluetoothPrinterManager(private val context: Context) {
    private val printerUUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    @SuppressLint("MissingPermission")
    fun getPairedPrinters(): List<BluetoothDevice> {
        return try {
            val adapter = BluetoothAdapter.getDefaultAdapter()
            if (adapter != null && adapter.isEnabled) {
                adapter.bondedDevices?.toList() ?: emptyList()
            } else {
                emptyList()
            }
        } catch (e: Throwable) {
            emptyList()
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun printData(deviceAddress: String, data: ByteArray): Boolean = withContext(Dispatchers.IO) {
        var socket: BluetoothSocket? = null
        try {
            val adapter = BluetoothAdapter.getDefaultAdapter() ?: return@withContext false
            val device = adapter.getRemoteDevice(deviceAddress)
            socket = device.createRfcommSocketToServiceRecord(printerUUID)
            socket.connect()
            
            val outputStream: OutputStream = socket.outputStream
            outputStream.write(data)
            outputStream.flush()
            socket.close()
            true
        } catch (e: Throwable) {
            e.printStackTrace()
            try { socket?.close() } catch (ex: Throwable) {}
            false
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun openCashDrawer(deviceAddress: String): Boolean = withContext(Dispatchers.IO) {
        var socket: BluetoothSocket? = null
        try {
            val adapter = BluetoothAdapter.getDefaultAdapter() ?: return@withContext false
            val device = adapter.getRemoteDevice(deviceAddress)
            socket = device.createRfcommSocketToServiceRecord(printerUUID)
            socket.connect()
            
            val outputStream: OutputStream = socket.outputStream
            // Perintah ESC/POS untuk membuka laci (ESC p 0 25 250)
            outputStream.write(byteArrayOf(0x1B, 0x70, 0x00, 0x19, 0xFA.toByte()))
            outputStream.flush()
            socket.close()
            true
        } catch (e: Throwable) {
            e.printStackTrace()
            try { socket?.close() } catch (ex: Throwable) {}
            false
        }
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

    // Fungsi Cetak Gambar (Logo) ke Thermal Printer
    fun printImage(bitmap: Bitmap): ByteArray {
        val outputStream = ByteArrayOutputStream()
        val width = bitmap.width
        val height = bitmap.height
        
        // ESC/POS Header for Image (GS v 0)
        outputStream.write(byteArrayOf(0x1D, 0x76, 0x30, 0x00)) 
        outputStream.write(byteArrayOf((width / 8).toByte(), 0x00)) // xL, xH
        outputStream.write(byteArrayOf((height % 256).toByte(), (height / 256).toByte())) // yL, yH
        
        for (y in 0 until height) {
            for (x in 0 until width step 8) {
                var byte = 0
                for (bit in 0 until 8) {
                    if (x + bit < width) {
                        val pixel = bitmap.getPixel(x + bit, y)
                        val r = (pixel shr 16) and 0xff
                        val g = (pixel shr 8) and 0xff
                        val b = pixel and 0xff
                        // Konversi ke Grayscale (Hitam/Putih)
                        if ((r + g + b) / 3 < 128) {
                            byte = byte or (0x80 shr bit)
                        }
                    }
                }
                outputStream.write(byte)
            }
        }
        return outputStream.toByteArray()
    }
}