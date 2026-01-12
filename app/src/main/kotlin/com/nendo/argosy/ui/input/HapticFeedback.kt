package com.nendo.argosy.ui.input

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.content.getSystemService
import com.nendo.argosy.util.PServerExecutor
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

enum class HapticPattern {
    FOCUS_CHANGE,
    SELECTION,
    BOUNDARY_HIT,
    ERROR,
    STRENGTH_PREVIEW
}

@Singleton
class HapticFeedbackManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService<VibratorManager>()?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService()
    }

    private var enabled = true
    private val hasAmplitudeControl = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        vibrator?.hasAmplitudeControl() == true
    } else false

    val supportsSystemVibration: Boolean
        get() = PServerExecutor.isAvailable

    fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
    }

    fun getSystemVibrationStrength(): Float {
        return PServerExecutor.getSystemSettingFloat("vibrate_strength_value", 0.5f)
    }

    fun setSystemVibrationStrength(strength: Float): Boolean {
        return PServerExecutor.setSystemSettingFloat("vibrate_strength_value", strength.coerceIn(0f, 1f))
    }

    private fun getAmplitude(): Int {
        val strength = getSystemVibrationStrength()
        return (strength * 255).toInt().coerceIn(1, 255)
    }

    fun vibrate(pattern: HapticPattern) {
        if (!enabled || vibrator == null || !vibrator.hasVibrator()) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val amplitude = getAmplitude()

            val effect = if (hasAmplitudeControl) {
                when (pattern) {
                    HapticPattern.FOCUS_CHANGE -> VibrationEffect.createOneShot(100L, amplitude)
                    HapticPattern.SELECTION -> VibrationEffect.createOneShot(150L, amplitude)
                    HapticPattern.BOUNDARY_HIT -> VibrationEffect.createOneShot(150L, 255)
                    HapticPattern.ERROR -> VibrationEffect.createOneShot(240L, 255)
                    HapticPattern.STRENGTH_PREVIEW -> VibrationEffect.createWaveform(
                        longArrayOf(0, 500, 100, 500, 100, 500),
                        intArrayOf(0, amplitude, 0, amplitude, 0, amplitude),
                        -1
                    )
                }
            } else {
                val strength = getSystemVibrationStrength()
                val duration = (45 + strength * 105).toLong()
                when (pattern) {
                    HapticPattern.FOCUS_CHANGE -> VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE)
                    HapticPattern.SELECTION -> VibrationEffect.createOneShot(duration + 45, VibrationEffect.DEFAULT_AMPLITUDE)
                    HapticPattern.BOUNDARY_HIT -> VibrationEffect.createOneShot(180L, VibrationEffect.DEFAULT_AMPLITUDE)
                    HapticPattern.ERROR -> VibrationEffect.createOneShot(300L, VibrationEffect.DEFAULT_AMPLITUDE)
                    HapticPattern.STRENGTH_PREVIEW -> VibrationEffect.createWaveform(
                        longArrayOf(0, 500, 100, 500, 100, 500),
                        -1
                    )
                }
            }
            vibrator.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            val strength = getSystemVibrationStrength()
            val duration = (45 + strength * 105).toLong()
            when (pattern) {
                HapticPattern.FOCUS_CHANGE -> vibrator.vibrate(duration)
                HapticPattern.SELECTION -> vibrator.vibrate(duration + 45)
                HapticPattern.BOUNDARY_HIT -> vibrator.vibrate(180L)
                HapticPattern.ERROR -> vibrator.vibrate(300L)
                HapticPattern.STRENGTH_PREVIEW -> vibrator.vibrate(longArrayOf(0, 500, 100, 500, 100, 500), -1)
            }
        }
    }
}
