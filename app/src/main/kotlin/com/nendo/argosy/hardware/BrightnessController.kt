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
    private val hasSecondaryDisplay: Boolean by lazy {
        PServerExecutor.execute("cat /sys/class/backlight/panel1-backlight/max_brightness")
            .getOrNull()?.toIntOrNull() != null
    }

    val isMultiDisplaySupported: Boolean
        get() = hasSecondaryDisplay && PServerExecutor.isAvailable

    fun getBrightness(): DisplayBrightness {
        val primary = getPrimaryBrightness()
        val secondary = if (hasSecondaryDisplay) getSecondaryBrightness() else null
        return DisplayBrightness(primary, secondary)
    }

    private fun getPrimaryBrightness(): Float? {
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

    private fun getSecondaryBrightness(): Float? {
        return try {
            val raw = PServerExecutor.execute("cat /sys/class/backlight/panel1-backlight/brightness")
                .getOrNull()?.toIntOrNull() ?: return null
            val max = PServerExecutor.execute("cat /sys/class/backlight/panel1-backlight/max_brightness")
                .getOrNull()?.toIntOrNull() ?: 4095
            // AYN displays at 2.5x the sysfs value
            (raw.toFloat() / max.toFloat() * 2.5f).coerceIn(0f, 1f)
        } catch (e: Exception) {
            null
        }
    }

    fun setPrimaryBrightness(brightness: Float): Boolean {
        val clamped = brightness.coerceIn(0f, 1f)
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

    fun setSecondaryBrightness(brightness: Float): Boolean {
        if (!isMultiDisplaySupported) return false

        val clamped = brightness.coerceIn(0f, 1f)
        return try {
            val max = PServerExecutor.execute("cat /sys/class/backlight/panel1-backlight/max_brightness")
                .getOrNull()?.toIntOrNull() ?: 4095
            // AYN displays at 2.5x sysfs value, so divide by 2.5 when writing
            val value = (clamped / 2.5f * max).toInt()
            PServerExecutor.execute("echo $value > /sys/class/backlight/panel1-backlight/brightness").isSuccess
        } catch (e: Exception) {
            false
        }
    }
}
