package com.nendo.argosy.data.quaypass.ble

import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.content.Context
import android.util.Log
import java.security.SecureRandom

class QuayPassAdvertiser(private val application: Application) {

    private val bluetoothAdapter by lazy {
        (application.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
    }

    private var isAdvertising = false
    private val secureRandom = SecureRandom()

    @SuppressLint("MissingPermission")
    fun start() {
        if (isAdvertising) return
        val advertiser = bluetoothAdapter?.bluetoothLeAdvertiser ?: run {
            Log.w(TAG, "BLE advertiser unavailable")
            return
        }

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setConnectable(true)
            .setTimeout(0)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .build()

        val ephemeralId = ByteArray(EPHEMERAL_ID_BYTES).also { secureRandom.nextBytes(it) }
        val manufacturerData =
            QuayPassConfig.MAGIC_BYTES +
                byteArrayOf(QuayPassConfig.PROTOCOL_MAJOR) +
                ephemeralId

        val data = AdvertiseData.Builder()
            .addManufacturerData(QuayPassConfig.MANUFACTURER_ID, manufacturerData)
            .build()

        advertiser.startAdvertising(settings, data, advertiseCallback)
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        if (!isAdvertising) return
        bluetoothAdapter?.bluetoothLeAdvertiser?.stopAdvertising(advertiseCallback)
        isAdvertising = false
        Log.d(TAG, "QuayPass advertising stopped")
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            isAdvertising = true
            Log.d(TAG, "QuayPass advertising started")
        }

        override fun onStartFailure(errorCode: Int) {
            isAdvertising = false
            Log.e(TAG, "QuayPass advertising failed: $errorCode")
        }
    }

    companion object {
        private const val TAG = "QuayPassAdvertiser"
        private const val EPHEMERAL_ID_BYTES = 8
    }
}
