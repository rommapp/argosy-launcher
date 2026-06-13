package com.nendo.argosy.data.quaypass.ble

import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.util.Log
import java.util.concurrent.ConcurrentHashMap

class QuayPassGattServer(
    private val application: Application,
    private val getOurProfileBytes: () -> ByteArray?,
    private val onPeerProfileWritten: (ByteArray) -> Boolean
) {

    private val bluetoothManager by lazy {
        application.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    }

    private var gattServer: BluetoothGattServer? = null
    private val connectedDevices = ConcurrentHashMap<String, Long>()
    private val recentWritesByPeer = ConcurrentHashMap<String, Long>()
    private val pendingWriteBuffers = ConcurrentHashMap<String, ByteArray>()
    private val verifiedReaders = ConcurrentHashMap.newKeySet<String>()

    @SuppressLint("MissingPermission")
    fun start() {
        val server = bluetoothManager?.openGattServer(application, callback) ?: run {
            Log.e(TAG, "Failed to open GATT server")
            return
        }
        gattServer = server

        val service = BluetoothGattService(
            QuayPassConfig.SERVICE_UUID,
            BluetoothGattService.SERVICE_TYPE_PRIMARY
        )
        service.addCharacteristic(
            BluetoothGattCharacteristic(
                QuayPassConfig.CHARACTERISTIC_PROFILE_UUID,
                BluetoothGattCharacteristic.PROPERTY_READ,
                BluetoothGattCharacteristic.PERMISSION_READ
            )
        )
        service.addCharacteristic(
            BluetoothGattCharacteristic(
                QuayPassConfig.CHARACTERISTIC_WRITE_UUID,
                BluetoothGattCharacteristic.PROPERTY_WRITE,
                BluetoothGattCharacteristic.PERMISSION_WRITE
            )
        )
        server.addService(service)
        Log.d(TAG, "GATT server started")
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        gattServer?.close()
        gattServer = null
        connectedDevices.clear()
        recentWritesByPeer.clear()
        pendingWriteBuffers.clear()
        verifiedReaders.clear()
        Log.d(TAG, "GATT server stopped")
    }

    private val callback = object : BluetoothGattServerCallback() {

        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    if (connectedDevices.size >= QuayPassConfig.MAX_CONCURRENT_CONNECTIONS) {
                        gattServer?.cancelConnection(device)
                        return
                    }
                    connectedDevices[device.address] = System.currentTimeMillis()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    connectedDevices.remove(device.address)
                    pendingWriteBuffers.remove(device.address)
                    verifiedReaders.remove(device.address)
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onCharacteristicReadRequest(
            device: BluetoothDevice,
            requestId: Int,
            offset: Int,
            characteristic: BluetoothGattCharacteristic
        ) {
            if (characteristic.uuid != QuayPassConfig.CHARACTERISTIC_PROFILE_UUID) {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, 0, null)
                return
            }
            if (!verifiedReaders.contains(device.address)) {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, ByteArray(0))
                return
            }
            val data = getOurProfileBytes() ?: ByteArray(0)
            val response = if (offset < data.size) data.sliceArray(offset until data.size) else ByteArray(0)
            gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, response)
        }

        @SuppressLint("MissingPermission")
        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray?
        ) {
            if (characteristic.uuid != QuayPassConfig.CHARACTERISTIC_WRITE_UUID || value == null) {
                if (responseNeeded) {
                    gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, 0, null)
                }
                return
            }
            if (!checkRateLimit(device.address)) {
                if (responseNeeded) {
                    gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, 0, null)
                }
                return
            }

            val combined = if (preparedWrite || offset > 0) {
                val existing = pendingWriteBuffers[device.address] ?: ByteArray(0)
                if (existing.size + value.size > QuayPassConfig.MAX_PROFILE_BYTES) {
                    pendingWriteBuffers.remove(device.address)
                    if (responseNeeded) {
                        gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, 0, null)
                    }
                    return
                }
                val merged = existing + value
                pendingWriteBuffers[device.address] = merged
                merged
            } else {
                if (value.size > QuayPassConfig.MAX_PROFILE_BYTES) {
                    if (responseNeeded) {
                        gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, 0, null)
                    }
                    return
                }
                value
            }

            if (!preparedWrite) {
                pendingWriteBuffers.remove(device.address)
                if (onPeerProfileWritten(combined)) {
                    verifiedReaders.add(device.address)
                }
            }

            if (responseNeeded) {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
            }
        }

        @SuppressLint("MissingPermission")
        override fun onExecuteWrite(device: BluetoothDevice, requestId: Int, execute: Boolean) {
            val accumulated = pendingWriteBuffers.remove(device.address)
            if (execute && accumulated != null && onPeerProfileWritten(accumulated)) {
                verifiedReaders.add(device.address)
            }
            gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
        }
    }

    private fun checkRateLimit(deviceAddress: String): Boolean {
        val now = System.currentTimeMillis() / 1000
        val last = recentWritesByPeer[deviceAddress] ?: 0
        if (now - last < QuayPassConfig.PER_PEER_WRITE_RATE_LIMIT_SECS) {
            return false
        }
        recentWritesByPeer[deviceAddress] = now
        return true
    }

    companion object {
        private const val TAG = "QuayPassGattServer"
    }
}
