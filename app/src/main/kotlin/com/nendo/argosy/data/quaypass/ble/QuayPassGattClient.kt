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
import kotlin.coroutines.resume

class QuayPassGattClient(private val application: Application) {

    private var currentGatt: BluetoothGatt? = null

    @SuppressLint("MissingPermission")
    suspend fun exchangeProfiles(
        device: BluetoothDevice,
        ourProfileBytes: ByteArray
    ): ByteArray? = withTimeoutOrNull(15_000) {
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

            if (!connectionChannel.receive()) return@withTimeoutOrNull null

            gatt.requestMtu(MTU_REQUEST)
            mtuChannel.receive()

            gatt.discoverServices()
            if (!servicesChannel.receive()) return@withTimeoutOrNull null

            val service = gatt.getService(QuayPassConfig.SERVICE_UUID) ?: return@withTimeoutOrNull null
            val readChar = service.getCharacteristic(QuayPassConfig.CHARACTERISTIC_PROFILE_UUID)
                ?: return@withTimeoutOrNull null
            val writeChar = service.getCharacteristic(QuayPassConfig.CHARACTERISTIC_WRITE_UUID)
                ?: return@withTimeoutOrNull null

            gatt.readCharacteristic(readChar)
            val theirProfile = readChannel.receive()

            val writeOk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeCharacteristic(
                    writeChar,
                    ourProfileBytes,
                    BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                ) == BluetoothGatt.GATT_SUCCESS
            } else {
                @Suppress("DEPRECATION")
                run {
                    writeChar.value = ourProfileBytes
                    writeChar.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                    gatt.writeCharacteristic(writeChar)
                }
            }
            if (writeOk) writeChannel.receive()

            theirProfile
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

    companion object {
        private const val TAG = "QuayPassGattClient"
        private const val MTU_REQUEST = 512
    }
}
