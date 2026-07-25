package com.nendo.argosy.data.quaypass.ble

import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothProfile
import android.os.Build
import android.util.Log
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import kotlin.coroutines.resume

class QuayPassGattClient(private val application: Application) {

    private var currentGatt: BluetoothGatt? = null

    @SuppressLint("MissingPermission")
    suspend fun exchangeProfiles(
        device: BluetoothDevice,
        ourProfileBytes: ByteArray
    ): ByteArray? = withTimeoutOrNull(QuayPassConfig.EXCHANGE_TIMEOUT_MS) {
        val connectionChannel = Channel<Boolean>(Channel.CONFLATED)
        val mtuChannel = Channel<Int>(Channel.CONFLATED)
        val servicesChannel = Channel<Boolean>(Channel.CONFLATED)
        val readChannel = Channel<ByteArray?>(Channel.CONFLATED)
        val writeChannel = Channel<Boolean>(Channel.CONFLATED)

        val callback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> connectionChannel.trySend(true)
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        connectionChannel.trySend(false)
                        mtuChannel.trySend(0)
                        readChannel.trySend(null)
                        writeChannel.trySend(false)
                    }
                }
            }
            override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
                mtuChannel.trySend(mtu)
            }
            override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
                servicesChannel.trySend(status == BluetoothGatt.GATT_SUCCESS)
            }
            @Suppress("DEPRECATION")
            override fun onCharacteristicRead(
                g: BluetoothGatt,
                ch: BluetoothGattCharacteristic,
                status: Int
            ) {
                readChannel.trySend(if (status == BluetoothGatt.GATT_SUCCESS) ch.value else null)
            }
            override fun onCharacteristicRead(
                g: BluetoothGatt,
                ch: BluetoothGattCharacteristic,
                value: ByteArray,
                status: Int
            ) {
                readChannel.trySend(if (status == BluetoothGatt.GATT_SUCCESS) value else null)
            }
            @Suppress("DEPRECATION")
            override fun onCharacteristicWrite(
                g: BluetoothGatt,
                ch: BluetoothGattCharacteristic,
                status: Int
            ) {
                writeChannel.trySend(status == BluetoothGatt.GATT_SUCCESS)
            }
        }

        try {
            val gatt = suspendCancellableCoroutine<BluetoothGatt?> { cont ->
                val g = device.connectGatt(
                    application,
                    false,
                    callback,
                    BluetoothDevice.TRANSPORT_LE
                )
                currentGatt = g
                cont.invokeOnCancellation { runCatching { g.close() } }
                cont.resume(g)
            } ?: return@withTimeoutOrNull null

            val connected = withTimeoutOrNull(QuayPassConfig.CONNECT_TIMEOUT_MS) { connectionChannel.receive() }
            if (connected != true) return@withTimeoutOrNull null

            gatt.requestMtu(MTU_REQUEST)
            val negotiatedMtu = withTimeoutOrNull(QuayPassConfig.GATT_STAGE_TIMEOUT_MS) { mtuChannel.receive() }
                ?.takeIf { it > 0 } ?: DEFAULT_MTU

            gatt.discoverServices()
            val discovered = withTimeoutOrNull(QuayPassConfig.GATT_STAGE_TIMEOUT_MS) { servicesChannel.receive() }
            if (discovered != true) return@withTimeoutOrNull null

            val service = gatt.getService(QuayPassConfig.SERVICE_UUID) ?: return@withTimeoutOrNull null
            val readChar = service.getCharacteristic(QuayPassConfig.CHARACTERISTIC_PROFILE_UUID)
                ?: return@withTimeoutOrNull null
            val writeChar = service.getCharacteristic(QuayPassConfig.CHARACTERISTIC_WRITE_UUID)
                ?: return@withTimeoutOrNull null

            val chunkSize = (negotiatedMtu - ATT_WRITE_OVERHEAD_BYTES).coerceAtLeast(MIN_CHUNK_BYTES)
            val stream = ByteBuffer.allocate(2 + ourProfileBytes.size).apply {
                putShort(ourProfileBytes.size.toShort())
                put(ourProfileBytes)
            }.array()

            var offset = 0
            while (offset < stream.size) {
                val end = minOf(offset + chunkSize, stream.size)
                val chunk = stream.copyOfRange(offset, end)
                val writeOk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    gatt.writeCharacteristic(
                        writeChar,
                        chunk,
                        BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                    ) == BluetoothGatt.GATT_SUCCESS
                } else {
                    @Suppress("DEPRECATION")
                    run {
                        writeChar.value = chunk
                        writeChar.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                        gatt.writeCharacteristic(writeChar)
                    }
                }
                val wrote = writeOk &&
                    withTimeoutOrNull(QuayPassConfig.GATT_STAGE_TIMEOUT_MS) { writeChannel.receive() } == true
                if (!wrote) return@withTimeoutOrNull null
                offset = end
            }

            readAssembledProfile(gatt, readChar, readChannel)
        } catch (t: Throwable) {
            Log.w(TAG, "Exchange failed", t)
            null
        } finally {
            currentGatt?.let {
                @SuppressLint("MissingPermission")
                runCatching { it.close() }
            }
            currentGatt = null
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun readAssembledProfile(
        gatt: BluetoothGatt,
        readChar: BluetoothGattCharacteristic,
        readChannel: Channel<ByteArray?>
    ): ByteArray? {
        val assembled = ByteArrayOutputStream()
        var expectedLen = -1
        while (true) {
            if (!gatt.readCharacteristic(readChar)) return null
            val chunk = withTimeoutOrNull(QuayPassConfig.GATT_STAGE_TIMEOUT_MS) { readChannel.receive() } ?: return null
            if (chunk.isEmpty()) return null
            assembled.write(chunk)
            val bytes = assembled.toByteArray()
            if (expectedLen < 0 && bytes.size >= 2) {
                expectedLen = ((bytes[0].toInt() and 0xFF) shl 8) or (bytes[1].toInt() and 0xFF)
                if (expectedLen <= 0 || expectedLen > QuayPassConfig.MAX_PROFILE_BYTES) return null
            }
            if (expectedLen >= 0 && bytes.size - 2 >= expectedLen) {
                return bytes.copyOfRange(2, 2 + expectedLen)
            }
            if (bytes.size > QuayPassConfig.MAX_PROFILE_BYTES + 2) return null
        }
    }

    companion object {
        private const val TAG = "QuayPassGattClient"
        private const val MTU_REQUEST = 512
        private const val DEFAULT_MTU = 23
        private const val ATT_WRITE_OVERHEAD_BYTES = 3
        private const val MIN_CHUNK_BYTES = 20
    }
}
