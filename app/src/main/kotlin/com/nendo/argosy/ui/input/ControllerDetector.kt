package com.nendo.argosy.ui.input

import android.annotation.SuppressLint
import android.view.InputDevice
import java.io.BufferedReader
import java.io.InputStreamReader

enum class DetectedLayout {
    XBOX,
    NINTENDO
}

enum class DetectionSource {
    VENDOR_ID,
    DEVICE_NAME,
    SYSTEM_PROPERTY,
    UNKNOWN
}

data class DetectionResult(
    val layout: DetectedLayout?,
    val deviceName: String?,
    val vendorId: Int?,
    val source: DetectionSource
)

object ControllerDetector {
    private val NINTENDO_VIDS = setOf(0x057e)
    private val XBOX_VIDS = setOf(0x045e)
    private val SONY_VIDS = setOf(0x054c)

    private val NINTENDO_LAYOUT_VIDS = setOf(
        0x2dc8,
        0x20d6,
        0x0f0d
    )

    private val MULTI_MODE_NAME_PATTERNS = listOf(
        "odin",
        "ayn"
    )

    private val NINTENDO_NAME_PATTERNS = listOf(
        "nintendo",
        "switch",
        "pro controller",
        "8bitdo",
        "retroid",
        "anbernic",
        "rg351", "rg353", "rg35xx", "rg405", "rg505", "rg556", "rg arc",
        "miyoo",
        "powkiddy",
        "trimui",
        "sn30", "sf30", "zero 2", "lite 2"
    )

    private val XBOX_NAME_PATTERNS = listOf(
        "xbox",
        "x-box",
        "microsoft"
    )

    @SuppressLint("PrivateApi")
    private fun getGamepadTypeProperty(): Int? {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("getprop", "persist.sys.gamepad.type"))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val value = reader.readLine()?.trim()?.toIntOrNull()
            reader.close()
            process.waitFor()
            android.util.Log.d("ControllerDetector", "getGamepadTypeProperty: $value")
            value
        } catch (e: Exception) {
            android.util.Log.e("ControllerDetector", "getGamepadTypeProperty failed", e)
            null
        }
    }

    fun detectFromDevice(device: InputDevice): DetectionResult {
        val vendorId = device.vendorId
        val deviceName = device.name?.lowercase() ?: ""

        // Check for multi-mode devices (Odin, AYN) - use system property
        val isMultiModeDevice = MULTI_MODE_NAME_PATTERNS.any { deviceName.contains(it) }
        if (isMultiModeDevice) {
            val gamepadType = getGamepadTypeProperty()
            val layout = when (gamepadType) {
                1 -> DetectedLayout.XBOX      // Alternate mode
                else -> DetectedLayout.NINTENDO  // Default mode (0) or unknown
            }
            return DetectionResult(layout, device.name, vendorId, DetectionSource.SYSTEM_PROPERTY)
        }

        when (vendorId) {
            in NINTENDO_VIDS -> return DetectionResult(
                DetectedLayout.NINTENDO, device.name, vendorId, DetectionSource.VENDOR_ID
            )
            in XBOX_VIDS, in SONY_VIDS -> return DetectionResult(
                DetectedLayout.XBOX, device.name, vendorId, DetectionSource.VENDOR_ID
            )
        }

        if (vendorId in NINTENDO_LAYOUT_VIDS) {
            return DetectionResult(
                DetectedLayout.NINTENDO, device.name, vendorId, DetectionSource.VENDOR_ID
            )
        }

        for (pattern in XBOX_NAME_PATTERNS) {
            if (deviceName.contains(pattern)) {
                return DetectionResult(
                    DetectedLayout.XBOX, device.name, vendorId, DetectionSource.DEVICE_NAME
                )
            }
        }

        for (pattern in NINTENDO_NAME_PATTERNS) {
            if (deviceName.contains(pattern)) {
                return DetectionResult(
                    DetectedLayout.NINTENDO, device.name, vendorId, DetectionSource.DEVICE_NAME
                )
            }
        }

        return DetectionResult(null, device.name, vendorId, DetectionSource.UNKNOWN)
    }

    fun detectFromActiveGamepad(): DetectionResult {
        val deviceIds = InputDevice.getDeviceIds()
        for (deviceId in deviceIds) {
            val device = InputDevice.getDevice(deviceId) ?: continue
            val sources = device.sources
            if (sources and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD ||
                sources and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK
            ) {
                return detectFromDevice(device)
            }
        }
        return DetectionResult(null, null, null, DetectionSource.UNKNOWN)
    }

    fun getDetectedLayout(): DetectedLayout? = detectFromActiveGamepad().layout
}
