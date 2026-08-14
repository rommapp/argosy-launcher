package com.nendo.argosy.util

import android.content.Context
import android.content.res.Resources
import android.provider.Settings
import android.text.format.DateFormat
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

private const val FALLBACK_MAX_BRIGHTNESS = 255
private const val FALLBACK_MIN_BRIGHTNESS = 1
private const val CLOCK_DATE_PATTERN = "MMM d"

/**
 * Reads and writes the Android system state the launcher mirrors: the configured clock
 * form, the screen brightness and its mode.
 *
 * These values belong to the platform, not to Argosy. Going through here rather than
 * poking a hardware register keeps what the launcher shows and what the system reasserts
 * on the next display policy pass as one and the same value.
 */
@Singleton
class SystemSettings @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val maxBrightness: Int by lazy {
        platformInteger("config_screenBrightnessSettingMaximum", FALLBACK_MAX_BRIGHTNESS)
    }

    private val minBrightness: Int by lazy {
        platformInteger("config_screenBrightnessSettingMinimum", FALLBACK_MIN_BRIGHTNESS)
            .coerceIn(0, maxBrightness)
    }

    fun screenBrightness(): Float? {
        val stored = readBrightness() ?: return null
        return (stored.toFloat() / maxBrightness.toFloat()).coerceIn(0f, 1f)
    }

    /**
     * Stores [fraction] as the system screen brightness, switching the display out of
     * automatic mode so the written value is the one the system keeps.
     *
     * Returns false when neither the privileged settings channel nor the WRITE_SETTINGS
     * permission is available, leaving the current brightness untouched.
     */
    fun setScreenBrightness(fraction: Float): Boolean {
        val value = brightnessValueFor(fraction.coerceIn(0f, 1f))
        return storeBrightnessPrivileged(value) || storeBrightnessDirect(value)
    }

    private fun brightnessValueFor(fraction: Float): Int =
        (fraction * maxBrightness).toInt().coerceIn(minBrightness, maxBrightness)

    private fun storeBrightnessPrivileged(value: Int): Boolean {
        if (!PServerExecutor.isAvailable) return false
        PServerExecutor.setSystemSetting(
            Settings.System.SCREEN_BRIGHTNESS_MODE,
            Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
        )
        PServerExecutor.setSystemSetting(Settings.System.SCREEN_BRIGHTNESS, value)
        return readBrightness() == value
    }

    private fun storeBrightnessDirect(value: Int): Boolean {
        if (!Settings.System.canWrite(context)) return false
        return try {
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
            )
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS,
                value
            )
        } catch (e: Exception) {
            false
        }
    }

    private fun readBrightness(): Int? = try {
        Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
    } catch (e: Settings.SettingNotFoundException) {
        null
    }

    private fun platformInteger(name: String, fallback: Int): Int {
        val resources = Resources.getSystem()
        val id = resources.getIdentifier(name, "integer", "android")
        if (id == 0) return fallback
        return runCatching { resources.getInteger(id) }.getOrNull()?.takeIf { it > 0 } ?: fallback
    }
}

/**
 * Formats a wall-clock time in the 12- or 24-hour form the device is configured for.
 */
fun formatClockTime(context: Context, epochMillis: Long): String =
    DateFormat.getTimeFormat(context).format(Date(epochMillis))

/**
 * Formats a short date followed by the time in the device's configured clock form.
 */
fun formatClockDateTime(context: Context, epochMillis: Long): String {
    val date = SimpleDateFormat(CLOCK_DATE_PATTERN, Locale.getDefault()).format(Date(epochMillis))
    return "$date, ${formatClockTime(context, epochMillis)}"
}
