package com.nendo.argosy.hardware

import android.content.Context
import android.media.AudioManager
import android.provider.Settings
import com.nendo.argosy.util.PServerExecutor
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class DisplayVolume(
    val primary: Float,
    val secondary: Float?
)

@Singleton
class VolumeController @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val hasSecondaryVolume: Boolean by lazy {
        try {
            Settings.System.getInt(context.contentResolver, SECONDARY_VOLUME_SETTING)
            true
        } catch (e: Settings.SettingNotFoundException) {
            false
        }
    }

    val isMultiDisplaySupported: Boolean
        get() = hasSecondaryVolume && PServerExecutor.isAvailable

    val maxVolume: Int
        get() = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

    fun getVolume(): DisplayVolume {
        val primary = getPrimaryVolume()
        val secondary = if (hasSecondaryVolume) getSecondaryVolume() else null
        return DisplayVolume(primary, secondary)
    }

    private fun getPrimaryVolume(): Float {
        val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        return current.toFloat() / max.toFloat()
    }

    private fun getSecondaryVolume(): Float {
        return try {
            val current = Settings.System.getInt(context.contentResolver, SECONDARY_VOLUME_SETTING)
            val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            current.toFloat() / max.toFloat()
        } catch (e: Settings.SettingNotFoundException) {
            0.5f
        }
    }

    fun setPrimaryVolume(volume: Float): Boolean {
        val clamped = volume.coerceIn(0f, 1f)
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val value = (clamped * max).toInt()
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, value, 0)
        return true
    }

    fun setSecondaryVolume(volume: Float): Boolean {
        if (!isMultiDisplaySupported) return false

        val clamped = volume.coerceIn(0f, 1f)
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val value = (clamped * max).toInt()
        return PServerExecutor.execute("settings put system $SECONDARY_VOLUME_SETTING $value").isSuccess
    }

    companion object {
        private const val SECONDARY_VOLUME_SETTING = "secondary_screen_volume_level"
    }
}
