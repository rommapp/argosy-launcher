package com.nendo.argosy.data.quaypass.ble

import android.annotation.SuppressLint
import android.app.Application
import android.app.PendingIntent
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.util.Log

class QuayPassScanner(private val application: Application) {

    private val bluetoothAdapter by lazy {
        (application.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
    }

    private var pendingIntent: PendingIntent? = null

    @SuppressLint("MissingPermission")
    fun start() {
        val scanner = bluetoothAdapter?.bluetoothLeScanner ?: run {
            Log.w(TAG, "BLE scanner unavailable")
            return
        }

        val intent = Intent(application, QuayPassScanReceiver::class.java).apply {
            action = ACTION_SCAN_RESULT
        }
        val pi = PendingIntent.getBroadcast(
            application,
            SCAN_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
        pendingIntent = pi

        val filter = ScanFilter.Builder()
            .setManufacturerData(
                QuayPassConfig.MANUFACTURER_ID,
                QuayPassConfig.MAGIC_BYTES,
                byteArrayOf(0xFF.toByte(), 0xFF.toByte())
            )
            .build()

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
            .setReportDelay(0)
            .build()

        scanner.startScan(listOf(filter), settings, pi)
        Log.d(TAG, "QuayPass scanning started")
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        val pi = pendingIntent ?: return
        bluetoothAdapter?.bluetoothLeScanner?.stopScan(pi)
        pendingIntent = null
        Log.d(TAG, "QuayPass scanning stopped")
    }

    companion object {
        private const val TAG = "QuayPassScanner"
        private const val SCAN_REQUEST_CODE = 0x4151
        const val ACTION_SCAN_RESULT = "com.nendo.argosy.quaypass.SCAN_RESULT"
    }
}
