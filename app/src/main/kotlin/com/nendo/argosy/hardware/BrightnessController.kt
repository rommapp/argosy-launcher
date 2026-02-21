package com.nendo.argosy.hardware

import android.content.Context
import android.provider.Settings
import com.nendo.argosy.util.PServerExecutor
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class DisplayBrightness(
    val primary: Float?,
    val secondary: Float?
)

@Singleton
class BrightnessController @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun getBrightness(): DisplayBrightness {
        return DisplayBrightness(primary = getPrimaryBrightness(), secondary = null)
    }

    private fun getPrimaryBrightness(): Float? {
        if (!PServerExecutor.isAvailable) return getSystemBrightnessSync()
        return try {
            val raw = PServerExecutor.execute("cat /sys/class/backlight/panel0-backlight/brightness")
                .getOrNull()?.toIntOrNull() ?: return getSystemBrightnessSync()
            val max = PServerExecutor.execute("cat /sys/class/backlight/panel0-backlight/max_brightness")
                .getOrNull()?.toIntOrNull() ?: 4095
            raw.toFloat() / max.toFloat()
        } catch (e: Exception) {
            getSystemBrightnessSync()
        }
    }

    fun getSystemBrightnessSync(): Float? {
        return try {
            val brightness = Settings.System.getInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS
            )
            brightness.toFloat() / 255f
        } catch (e: Settings.SettingNotFoundException) {
            null
        }
    }

    fun setPrimaryBrightness(brightness: Float): Boolean {
        val clamped = brightness.coerceIn(0f, 1f)

        if (PServerExecutor.isAvailable) {
            val sysfsResult = try {
                val max = PServerExecutor.execute("cat /sys/class/backlight/panel0-backlight/max_brightness")
                    .getOrNull()?.toIntOrNull() ?: 4095
                val value = (clamped * max).toInt()
                PServerExecutor.execute("echo $value > /sys/class/backlight/panel0-backlight/brightness").isSuccess
            } catch (e: Exception) {
                false
            }
            if (sysfsResult) return true
        }

        return try {
            val brightnessValue = (clamped * 255).toInt()
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS,
                brightnessValue
            )
            true
        } catch (e: Exception) {
            false
        }
    }
}
