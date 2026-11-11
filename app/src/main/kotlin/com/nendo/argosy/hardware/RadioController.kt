package com.nendo.argosy.hardware

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import android.view.InputDevice
import androidx.core.content.ContextCompat
import com.nendo.argosy.core.input.controllerBatteryPercent
import com.nendo.argosy.util.RootShell
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class RadioStatus(
    val wifiEnabled: Boolean = false,
    val wifiConnected: Boolean = false,
    val bluetoothEnabled: Boolean = false
)

enum class BluetoothDeviceKind { CONTROLLER, AUDIO, OTHER }

data class PairedBluetoothDevice(
    val address: String,
    val name: String,
    val connected: Boolean,
    val kind: BluetoothDeviceKind,
    val batteryPercent: Int?
)

/**
 * Wi-Fi and Bluetooth state for every device, and switching them through the root route where one
 * exists. Android refuses both switches to ordinary apps, so [canToggle] is false without it.
 * Switching and every [wifi] call block on a shell call.
 */
@Singleton
class RadioController @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager

    val wifi: WifiNetworks = WifiNetworks { script -> RootShell.run(context, script) }

    val bluetooth: BluetoothPairing = BluetoothPairing(context) { bluetoothManager?.adapter }

    val canToggle: Boolean
        get() = RootShell.isAvailable

    fun status(): RadioStatus = RadioStatus(
        wifiEnabled = wifiManager?.isWifiEnabled == true,
        wifiConnected = isWifiConnected(),
        bluetoothEnabled = isBluetoothEnabled()
    )

    fun setWifiEnabled(enabled: Boolean): Boolean {
        val cmdState = if (enabled) "enabled" else "disabled"
        val svcState = if (enabled) "enable" else "disable"
        return runRoot("cmd wifi set-wifi-enabled $cmdState || svc wifi $svcState")
    }

    fun setBluetoothEnabled(enabled: Boolean): Boolean {
        val state = if (enabled) "enable" else "disable"
        return runRoot("cmd bluetooth_manager $state || svc bluetooth $state")
    }

    fun hasBluetoothPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || isGranted(Manifest.permission.BLUETOOTH_CONNECT)

    fun hasWifiPermission(): Boolean = isGranted(Manifest.permission.ACCESS_FINE_LOCATION)

    fun wifiPermissions(): List<String> =
        listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

    @Suppress("DEPRECATION")
    fun publicWifiConnection(): WifiConnection? {
        if (!isWifiConnected()) return null
        val info = try {
            wifiManager?.connectionInfo
        } catch (_: SecurityException) {
            null
        } ?: return WifiConnection(ssid = null, rssi = null, frequencyMhz = null)
        val ssid = info.ssid?.removeSurrounding("\"")
            ?.takeIf { hasWifiPermission() && it.isNotBlank() && it != WifiManager.UNKNOWN_SSID }
        return WifiConnection(ssid = ssid, rssi = info.rssi, frequencyMhz = info.frequency.takeIf { it > 0 })
    }

    @Suppress("DEPRECATION")
    fun startPublicWifiScan(): Boolean = try {
        wifiManager?.startScan() == true
    } catch (_: SecurityException) {
        false
    }

    @Suppress("DEPRECATION")
    @SuppressLint("MissingPermission")
    fun publicWifiScanResults(): List<WifiScanResult> {
        if (!hasWifiPermission()) return emptyList()
        val results = try {
            wifiManager?.scanResults.orEmpty()
        } catch (_: SecurityException) {
            emptyList()
        }
        return WifiShellParser.strongestPerNetwork(
            results.mapNotNull { result ->
                val ssid = result.SSID?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                WifiScanResult(
                    ssid = ssid,
                    rssi = result.level,
                    frequencyMhz = result.frequency,
                    security = WifiShellParser.securityOf(result.capabilities.orEmpty())
                )
            }
        )
    }

    private fun isGranted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun pairedDevices(): List<PairedBluetoothDevice> {
        if (!hasBluetoothPermission()) return emptyList()
        return try {
            val bonded = bluetoothManager?.adapter?.bondedDevices.orEmpty()
            val inputs = externalInputDevices()
            bonded.map { device ->
                val name = device.name?.takeIf { it.isNotBlank() } ?: device.address
                val input = inputs.firstOrNull { it.name == name }
                PairedBluetoothDevice(
                    address = device.address,
                    name = name,
                    connected = isDeviceConnected(device) ?: (input != null),
                    kind = kindOf(device, input),
                    batteryPercent = input?.let(::controllerBatteryPercent)
                )
            }.sortedWith(compareByDescending<PairedBluetoothDevice> { it.connected }.thenBy { it.name.lowercase() })
        } catch (_: SecurityException) {
            emptyList()
        }
    }

    private fun runRoot(script: String): Boolean = RootShell.run(context, script)?.succeeded == true

    private fun isWifiConnected(): Boolean = try {
        val manager = connectivityManager ?: return false
        manager.getNetworkCapabilities(manager.activeNetwork)
            ?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
    } catch (_: SecurityException) {
        false
    }

    private fun isBluetoothEnabled(): Boolean = try {
        bluetoothManager?.adapter?.isEnabled == true
    } catch (_: SecurityException) {
        false
    }

    private fun externalInputDevices(): List<InputDevice> = InputDevice.getDeviceIds().toList()
        .mapNotNull { InputDevice.getDevice(it) }
        .filter { Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || it.isExternal }

    @SuppressLint("DiscouragedPrivateApi")
    private fun isDeviceConnected(device: BluetoothDevice): Boolean? = try {
        BluetoothDevice::class.java.getMethod("isConnected").invoke(device) as? Boolean
    } catch (_: Exception) {
        null
    }

    @SuppressLint("MissingPermission")
    private fun kindOf(device: BluetoothDevice, input: InputDevice?): BluetoothDeviceKind {
        if (input != null && input.isGamepad()) return BluetoothDeviceKind.CONTROLLER
        val deviceClass = try {
            device.bluetoothClass
        } catch (_: SecurityException) {
            null
        }
        return BluetoothPairing.kindOf(deviceClass)
    }

    private fun InputDevice.isGamepad(): Boolean =
        supportsSource(InputDevice.SOURCE_GAMEPAD) || supportsSource(InputDevice.SOURCE_JOYSTICK)
}
