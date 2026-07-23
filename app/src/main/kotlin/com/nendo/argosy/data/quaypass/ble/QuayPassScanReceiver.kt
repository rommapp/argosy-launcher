package com.nendo.argosy.data.quaypass.ble

import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanResult
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

class QuayPassScanReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val results: List<ScanResult>? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayListExtra(
                BluetoothLeScanner.EXTRA_LIST_SCAN_RESULT,
                ScanResult::class.java
            )
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableArrayListExtra(BluetoothLeScanner.EXTRA_LIST_SCAN_RESULT)
        }
        if (results.isNullOrEmpty()) return

        val sink = scanResultSink ?: return
        for (result in results) {
            if (result.rssi < QuayPassConfig.RSSI_THRESHOLD) continue
            val manufacturerData = result.scanRecord
                ?.getManufacturerSpecificData(QuayPassConfig.MANUFACTURER_ID)
                ?: continue
            if (manufacturerData.size < MIN_MANUFACTURER_DATA_BYTES) continue
            if (manufacturerData[0] != QuayPassConfig.MAGIC_BYTES[0] ||
                manufacturerData[1] != QuayPassConfig.MAGIC_BYTES[1]
            ) continue
            val protocolMajor = manufacturerData[2].toInt() and 0xFF
            if (protocolMajor != QuayPassConfig.PROTOCOL_MAJOR.toInt()) continue
            sink(result.device, result.rssi)
        }
    }

    companion object {
        private const val TAG = "QuayPassScanReceiver"
        private const val MIN_MANUFACTURER_DATA_BYTES = 3

        @Volatile
        var scanResultSink: ((android.bluetooth.BluetoothDevice, Int) -> Unit)? = null
    }
}
