package com.karyaku.kasir

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

    // FITUR BARU: Buka Laci Kasir
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
            // FIX: Menambahkan .toByte() pada 0xFA karena nilainya melebihi batas maksimal tipe data Byte
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
}