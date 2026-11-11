package com.nendo.argosy.hardware

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat

data class NearbyBluetoothDevice(val address: String, val name: String)

/**
 * Finding, pairing and forgetting Bluetooth controllers. Discovery reports only devices whose
 * class marks them as a gamepad or joystick. Forgetting goes through the platform's hidden
 * removeBond, so [canForget] is false where it is unreachable.
 */
class BluetoothPairing(
    private val context: Context,
    private val adapter: () -> BluetoothAdapter?
) {
    interface Listener {
        fun onFound(device: NearbyBluetoothDevice)
        fun onDiscoveryFinished()
        fun onBondChanged(address: String, bonded: Boolean, failed: Boolean)
    }

    val canForget: Boolean by lazy { removeBondMethod != null }

    /**
     * The runtime permissions discovery and pairing need on this Android release, empty when
     * every one is granted.
     */
    fun missingPermissions(): List<String> = requiredPermissions().filter {
        ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
    }

    /**
     * Delivers discovery results and bond changes to [listener] on the main thread until the
     * returned handle is closed.
     */
    fun watch(listener: Listener): AutoCloseable {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    BluetoothDevice.ACTION_FOUND -> intent.device()?.let { device ->
                        nearbyController(device, intent)?.let(listener::onFound)
                    }
                    BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> listener.onDiscoveryFinished()
                    BluetoothDevice.ACTION_BOND_STATE_CHANGED -> intent.device()?.let { device ->
                        val state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR)
                        val previous = intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, BluetoothDevice.ERROR)
                        listener.onBondChanged(
                            address = device.address,
                            bonded = state == BluetoothDevice.BOND_BONDED,
                            failed = state == BluetoothDevice.BOND_NONE && previous == BluetoothDevice.BOND_BONDING
                        )
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        return AutoCloseable { runCatching { context.unregisterReceiver(receiver) } }
    }

    @SuppressLint("MissingPermission")
    fun startDiscovery(): Boolean = guarded {
        val bluetooth = adapter() ?: return@guarded false
        if (bluetooth.isDiscovering) bluetooth.cancelDiscovery()
        bluetooth.startDiscovery()
    }

    @SuppressLint("MissingPermission")
    fun cancelDiscovery() {
        guarded { adapter()?.cancelDiscovery() ?: false }
    }

    @SuppressLint("MissingPermission")
    fun pair(address: String): Boolean = guarded {
        val bluetooth = adapter() ?: return@guarded false
        bluetooth.cancelDiscovery()
        bluetooth.getRemoteDevice(address).createBond()
    }

    fun forget(address: String): Boolean = guarded {
        val method = removeBondMethod ?: return@guarded false
        val device = adapter()?.getRemoteDevice(address) ?: return@guarded false
        method.invoke(device) as? Boolean ?: false
    }

    @SuppressLint("MissingPermission")
    private fun nearbyController(device: BluetoothDevice, intent: Intent): NearbyBluetoothDevice? {
        val deviceClass = IntentCompat.getParcelableExtra(intent, BluetoothDevice.EXTRA_CLASS, BluetoothClass::class.java)
            ?: guardedOrNull { device.bluetoothClass }
        if (kindOf(deviceClass) != BluetoothDeviceKind.CONTROLLER) return null
        if (guardedOrNull { device.bondState } == BluetoothDevice.BOND_BONDED) return null
        val name = intent.getStringExtra(BluetoothDevice.EXTRA_NAME)?.takeIf { it.isNotBlank() }
            ?: guardedOrNull { device.name }?.takeIf { it.isNotBlank() }
            ?: return null
        return NearbyBluetoothDevice(address = device.address, name = name)
    }

    private fun Intent.device(): BluetoothDevice? =
        IntentCompat.getParcelableExtra(this, BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)

    private inline fun guarded(block: () -> Boolean): Boolean = try {
        block()
    } catch (_: SecurityException) {
        false
    } catch (_: ReflectiveOperationException) {
        false
    }

    private inline fun <T> guardedOrNull(block: () -> T?): T? = try {
        block()
    } catch (_: SecurityException) {
        null
    }

    private val removeBondMethod by lazy {
        runCatching { BluetoothDevice::class.java.getMethod("removeBond") }.getOrNull()
    }

    companion object {
        private const val PERIPHERAL_MINOR_MASK = 0x3C
        private val GAME_PERIPHERAL_MINORS = setOf(0x04, 0x08)

        fun requiredPermissions(): List<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            listOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        fun kindOf(deviceClass: BluetoothClass?): BluetoothDeviceKind {
            deviceClass ?: return BluetoothDeviceKind.OTHER
            return when (deviceClass.majorDeviceClass) {
                BluetoothClass.Device.Major.AUDIO_VIDEO -> BluetoothDeviceKind.AUDIO
                BluetoothClass.Device.Major.PERIPHERAL ->
                    if ((deviceClass.deviceClass and PERIPHERAL_MINOR_MASK) in GAME_PERIPHERAL_MINORS) {
                        BluetoothDeviceKind.CONTROLLER
                    } else {
                        BluetoothDeviceKind.OTHER
                    }
                else -> BluetoothDeviceKind.OTHER
            }
        }
    }
}
