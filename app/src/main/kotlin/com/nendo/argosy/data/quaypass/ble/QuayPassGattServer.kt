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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

class QuayPassGattServer(
    private val application: Application,
    private val scope: CoroutineScope,
    private val onProfileWrite: suspend (deviceKey: String, frame: QuayPassExchangeFrames.ProfileWrite) -> ByteArray?,
    private val onAttestationWrite: suspend (deviceKey: String, frame: QuayPassExchangeFrames.AttestationWrite) -> Unit,
    private val onDeviceGone: (deviceKey: String) -> Unit
) {

    private val bluetoothManager by lazy {
        application.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    }

    private var gattServer: BluetoothGattServer? = null
    private val connectedDevices = ConcurrentHashMap<String, Long>()
    private val recentWritesByPeer = ConcurrentHashMap<String, Long>()
    private val pendingWriteBuffers = ConcurrentHashMap<String, ByteArray>()
    private val sequentialWriteBuffers = ConcurrentHashMap<String, ByteArray>()
    private val deviceMtus = ConcurrentHashMap<String, Int>()
    private val readCursors = ConcurrentHashMap<String, Int>()
    private val readWindowStarts = ConcurrentHashMap<String, Int>()
    private val readResponses = ConcurrentHashMap<String, ByteArray>()
    private val watchdogs = ConcurrentHashMap<String, Job>()

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
        watchdogs.values.forEach { it.cancel() }
        watchdogs.clear()
        connectedDevices.clear()
        recentWritesByPeer.clear()
        pendingWriteBuffers.clear()
        sequentialWriteBuffers.clear()
        deviceMtus.clear()
        readCursors.clear()
        readWindowStarts.clear()
        readResponses.clear()
        Log.d(TAG, "GATT server stopped")
    }

    @SuppressLint("MissingPermission")
    private fun armWatchdog(device: BluetoothDevice) {
        val address = device.address
        watchdogs.remove(address)?.cancel()
        watchdogs[address] = scope.launch {
            delay(QuayPassConfig.EXCHANGE_TIMEOUT_MS)
            Log.d(TAG, "Exchange watchdog expired, dropping $address")
            runCatching { gattServer?.cancelConnection(device) }
            clearDeviceState(address)
        }
    }

    private fun clearDeviceState(address: String) {
        watchdogs.remove(address)?.cancel()
        connectedDevices.remove(address)
        pendingWriteBuffers.remove(address)
        sequentialWriteBuffers.remove(address)
        deviceMtus.remove(address)
        readCursors.remove(address)
        readWindowStarts.remove(address)
        readResponses.remove(address)
        onDeviceGone(address)
    }

    private fun dispatchMessage(deviceKey: String, payload: ByteArray) {
        when (QuayPassExchangeFrames.messageType(payload)) {
            QuayPassExchangeFrames.MSG_PROFILE -> {
                val frame = QuayPassExchangeFrames.parseProfileWrite(payload) ?: return
                scope.launch {
                    val response = onProfileWrite(deviceKey, frame)
                    if (response != null) {
                        readResponses[deviceKey] = response
                        readCursors.remove(deviceKey)
                        readWindowStarts.remove(deviceKey)
                    }
                }
            }
            QuayPassExchangeFrames.MSG_ATTESTATION -> {
                val frame = QuayPassExchangeFrames.parseAttestationWrite(payload) ?: return
                scope.launch { onAttestationWrite(deviceKey, frame) }
            }
        }
    }

    private sealed interface Assembly {
        class Complete(val payload: ByteArray) : Assembly
        object Incomplete : Assembly
        object Malformed : Assembly
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
                    armWatchdog(device)
                }
                BluetoothProfile.STATE_DISCONNECTED -> clearDeviceState(device.address)
            }
        }

        override fun onMtuChanged(device: BluetoothDevice, mtu: Int) {
            deviceMtus[device.address] = mtu
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
            val response = readResponses[device.address]
            if (response == null || response.isEmpty()) {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, ByteArray(0))
                return
            }
            val stream = ByteArray(2 + response.size)
            stream[0] = ((response.size shr 8) and 0xFF).toByte()
            stream[1] = (response.size and 0xFF).toByte()
            response.copyInto(stream, 2)

            val mtu = deviceMtus[device.address] ?: DEFAULT_MTU
            val windowCap = (mtu - WINDOW_MARGIN_BYTES).coerceAtLeast(MIN_WINDOW_BYTES)
            val start = if (offset == 0) {
                val cursor = (readCursors[device.address] ?: 0).let { if (it >= stream.size) 0 else it }
                readWindowStarts[device.address] = cursor
                cursor
            } else {
                (readWindowStarts[device.address] ?: 0) + offset
            }
            if (start >= stream.size) {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, ByteArray(0))
                return
            }
            val end = minOf(start + windowCap, stream.size)
            readCursors[device.address] = maxOf(readCursors[device.address] ?: 0, end)
            gattServer?.sendResponse(
                device, requestId, BluetoothGatt.GATT_SUCCESS, offset,
                stream.copyOfRange(start, end)
            )
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

            if (preparedWrite || offset > 0) {
                val existing = pendingWriteBuffers[device.address]
                if (existing == null && !checkRateLimit(device.address)) {
                    if (responseNeeded) {
                        gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, 0, null)
                    }
                    return
                }
                val merged = (existing ?: ByteArray(0)) + value
                if (merged.size > MAX_STREAM_BYTES) {
                    pendingWriteBuffers.remove(device.address)
                    if (responseNeeded) {
                        gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, 0, null)
                    }
                    return
                }
                pendingWriteBuffers[device.address] = merged
                if (responseNeeded) {
                    gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
                }
                return
            }

            val existing = sequentialWriteBuffers[device.address]
            if (existing == null && !checkRateLimit(device.address)) {
                if (responseNeeded) {
                    gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, 0, null)
                }
                return
            }
            val merged = (existing ?: ByteArray(0)) + value
            when (val assembly = assemble(merged)) {
                is Assembly.Complete -> {
                    sequentialWriteBuffers.remove(device.address)
                    dispatchMessage(device.address, assembly.payload)
                    if (responseNeeded) {
                        gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
                    }
                }
                Assembly.Incomplete -> {
                    sequentialWriteBuffers[device.address] = merged
                    if (responseNeeded) {
                        gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
                    }
                }
                Assembly.Malformed -> {
                    sequentialWriteBuffers.remove(device.address)
                    if (responseNeeded) {
                        gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, 0, null)
                    }
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onExecuteWrite(device: BluetoothDevice, requestId: Int, execute: Boolean) {
            val accumulated = pendingWriteBuffers.remove(device.address)
            if (execute && accumulated != null) {
                val assembly = assemble(accumulated)
                if (assembly is Assembly.Complete) {
                    dispatchMessage(device.address, assembly.payload)
                }
            }
            gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
        }
    }

    private fun assemble(buffer: ByteArray): Assembly {
        if (buffer.size < 2) return Assembly.Incomplete
        val totalLen = ((buffer[0].toInt() and 0xFF) shl 8) or (buffer[1].toInt() and 0xFF)
        if (totalLen == 0 || totalLen > QuayPassConfig.MAX_PROFILE_BYTES) return Assembly.Malformed
        val bodySize = buffer.size - 2
        return when {
            bodySize < totalLen -> Assembly.Incomplete
            bodySize > totalLen -> Assembly.Malformed
            else -> Assembly.Complete(buffer.copyOfRange(2, buffer.size))
        }
    }

    private fun checkRateLimit(deviceAddress: String): Boolean {
        val now = System.currentTimeMillis() / 1000
        recentWritesByPeer.entries.removeAll { now - it.value >= QuayPassConfig.PER_PEER_WRITE_RATE_LIMIT_SECS }
        val last = recentWritesByPeer[deviceAddress] ?: 0
        if (now - last < QuayPassConfig.PER_PEER_WRITE_RATE_LIMIT_SECS) {
            return false
        }
        recentWritesByPeer[deviceAddress] = now
        return true
    }

    companion object {
        private const val TAG = "QuayPassGattServer"
        private const val DEFAULT_MTU = 23
        private const val WINDOW_MARGIN_BYTES = 3
        private const val MIN_WINDOW_BYTES = 20
        private const val MAX_STREAM_BYTES = QuayPassConfig.MAX_PROFILE_BYTES + 2
    }
}
