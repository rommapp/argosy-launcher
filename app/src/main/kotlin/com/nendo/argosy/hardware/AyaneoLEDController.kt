package com.nendo.argosy.hardware

import android.content.Context
import android.provider.Settings
import androidx.compose.ui.graphics.Color
import kotlin.math.roundToInt

class AyaneoLEDController(private val context: Context) : LEDController {

    override val isAvailable: Boolean
        get() = Settings.System.canWrite(context)

    override fun setColor(left: Color, right: Color): Boolean {
        put(KEY_MODE, MODE_SINGLE_STATIC)
        return put(KEY_COLOR, left.toRgbTriple())
    }

    override fun setBrightness(percent: Float): Boolean {
        val level = (percent.coerceIn(0f, 1f) * BRIGHTNESS_MAX).roundToInt().coerceIn(0, BRIGHTNESS_MAX)
        return put(KEY_BRIGHTNESS, level.toString())
    }

    override fun setEnabled(left: Boolean, right: Boolean): Boolean =
        put(KEY_ENABLED, if (left || right) "true" else "false")

    private fun put(key: String, value: String): Boolean = try {
        Settings.System.putString(context.contentResolver, key, value)
    } catch (e: Exception) {
        false
    }

    private fun Color.toRgbTriple(): String {
        val r = (red * 255).roundToInt().coerceIn(0, 255)
        val g = (green * 255).roundToInt().coerceIn(0, 255)
        val b = (blue * 255).roundToInt().coerceIn(0, 255)
        return "$r,$g,$b"
    }

    companion object {
        private const val KEY_ENABLED = "ayaneo/share/aya_rgb_is_open.conf"
        private const val KEY_MODE = "ayaneo/share/aya_rgb_mode.conf"
        private const val KEY_COLOR = "ayaneo/share/aya_rgb_single_mode_color.conf"
        private const val KEY_BRIGHTNESS = "ayaneo/share/aya_rgb_single_mode_bright.conf"
        private const val MODE_SINGLE_STATIC = "6"
        private const val BRIGHTNESS_MAX = 10
    }
}
