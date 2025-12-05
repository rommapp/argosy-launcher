package com.nendo.argosy.ui.input

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.content.getSystemService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

enum class HapticPattern {
    FOCUS_CHANGE,
    SELECTION,
    BOUNDARY_HIT,
    ERROR
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

    fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
    }

    fun vibrate(pattern: HapticPattern) {
        if (!enabled || vibrator == null || !vibrator.hasVibrator()) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val effect = when (pattern) {
                HapticPattern.FOCUS_CHANGE -> VibrationEffect.createOneShot(
                    10L,
                    VibrationEffect.DEFAULT_AMPLITUDE
                )
                HapticPattern.SELECTION -> VibrationEffect.createOneShot(
                    20L,
                    VibrationEffect.DEFAULT_AMPLITUDE
                )
                HapticPattern.BOUNDARY_HIT -> VibrationEffect.createWaveform(
                    longArrayOf(0, 10, 50, 10),
                    -1
                )
                HapticPattern.ERROR -> VibrationEffect.createOneShot(
                    100L,
                    VibrationEffect.DEFAULT_AMPLITUDE
                )
            }
            vibrator.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            when (pattern) {
                HapticPattern.FOCUS_CHANGE -> vibrator.vibrate(10L)
                HapticPattern.SELECTION -> vibrator.vibrate(20L)
                HapticPattern.BOUNDARY_HIT -> vibrator.vibrate(longArrayOf(0, 10, 50, 10), -1)
                HapticPattern.ERROR -> vibrator.vibrate(100L)
            }
        }
    }
}
