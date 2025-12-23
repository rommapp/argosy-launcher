package com.nendo.argosy.ui.input

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.util.Log
import com.nendo.argosy.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "SoundFeedback"

enum class SoundType {
    SILENT,
    NAVIGATE,
    BOUNDARY,
    SECTION_CHANGE,
    SELECT,
    BACK,
    OPEN_MODAL,
    CLOSE_MODAL,
    FAVORITE,
    UNFAVORITE,
    DOWNLOAD_START,
    DOWNLOAD_COMPLETE,
    DOWNLOAD_CANCEL,
    ERROR,
    VOLUME_PREVIEW,
    TOGGLE,
    LAUNCH_GAME
}

enum class SoundPreset(val resourceId: Int?, val displayName: String) {
    CLICK_SOFT(R.raw.click_soft, "Click Soft"),
    TAP_LIGHT(R.raw.tap_light, "Tap Light"),
    POP_CONFIRM(R.raw.pop_confirm, "Pop Confirm"),
    SWIPE_BACK(R.raw.swipe_back, "Swipe Back"),
    POP_CLOSE(R.raw.pop_close, "Pop Close"),
    BUZZ_ERROR(R.raw.buzz_error, "Buzz Error"),
    CHIME_OPEN(R.raw.chime_open, "Chime Open"),
    HOVER_SOFT(R.raw.hover_soft, "Hover Soft"),
    DING_PICKUP(R.raw.ding_pickup, "Ding Pickup"),
    NOTIFY_START(R.raw.notify_start, "Notify Start"),
    CHIME_SUCCESS(R.raw.chime_success, "Chime Success"),
    TICK_ACCEPT(R.raw.tick_accept, "Tick Accept"),
    BELL_HIGH(R.raw.bell_high, "Bell High"),
    COLLECT(R.raw.collect, "Collect"),
    DISMISS_FAIL(R.raw.dismiss_fail, "Dismiss Fail"),
    SILENT(null, "Silent"),
    CUSTOM(null, "Custom...")
}

data class SoundConfig(
    val presetName: String? = null,
    val customFilePath: String? = null
)

@Singleton
class SoundFeedbackManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var enabled = false
    private var volume = 0.4f
    private var soundPool: SoundPool? = null
    private val soundIds = mutableMapOf<SoundPreset, Int>()
    private val lastPlayTime = mutableMapOf<SoundPreset, Long>()
    private var soundsLoaded = false
    private var soundConfigs: Map<SoundType, SoundConfig> = emptyMap()

    companion object {
        private const val DEBOUNCE_MS = 50L
    }

    private val defaultPresetMap = mapOf(
        SoundType.NAVIGATE to SoundPreset.TAP_LIGHT,
        SoundType.BOUNDARY to SoundPreset.BUZZ_ERROR,
        SoundType.SECTION_CHANGE to SoundPreset.HOVER_SOFT,
        SoundType.SELECT to SoundPreset.POP_CONFIRM,
        SoundType.BACK to SoundPreset.SWIPE_BACK,
        SoundType.OPEN_MODAL to SoundPreset.CHIME_OPEN,
        SoundType.CLOSE_MODAL to SoundPreset.POP_CLOSE,
        SoundType.FAVORITE to SoundPreset.DING_PICKUP,
        SoundType.UNFAVORITE to SoundPreset.TICK_ACCEPT,
        SoundType.DOWNLOAD_START to SoundPreset.NOTIFY_START,
        SoundType.DOWNLOAD_COMPLETE to SoundPreset.CHIME_SUCCESS,
        SoundType.DOWNLOAD_CANCEL to SoundPreset.DISMISS_FAIL,
        SoundType.ERROR to SoundPreset.BUZZ_ERROR,
        SoundType.VOLUME_PREVIEW to SoundPreset.CLICK_SOFT,
        SoundType.TOGGLE to SoundPreset.BELL_HIGH,
        SoundType.LAUNCH_GAME to SoundPreset.COLLECT
    )

    init {
        initSoundPool()
    }

    private fun initSoundPool() {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(6)
            .setAudioAttributes(audioAttributes)
            .build().apply {
                setOnLoadCompleteListener { _, _, status ->
                    if (status == 0) {
                        val loadedCount = soundIds.count { it.value > 0 }
                        val totalCount = SoundPreset.entries.count { it.resourceId != null }
                        if (loadedCount >= totalCount) {
                            soundsLoaded = true
                            Log.d(TAG, "All $loadedCount sounds loaded")
                        }
                    }
                }
            }

        SoundPreset.entries.forEach { preset ->
            preset.resourceId?.let { resId ->
                try {
                    val id = soundPool?.load(context, resId, 1) ?: 0
                    soundIds[preset] = id
                    Log.d(TAG, "Loading ${preset.name} (id=$id)")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to load ${preset.name}", e)
                }
            }
        }
    }

    fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
        Log.d(TAG, "setEnabled=$enabled")
    }

    fun setVolume(volume: Int) {
        this.volume = (volume / 100f).coerceIn(0f, 1f)
        Log.d(TAG, "setVolume=$volume (${this.volume})")
    }

    fun setSoundConfigs(configs: Map<SoundType, SoundConfig>) {
        soundConfigs = configs
        Log.d(TAG, "Sound configs updated: ${configs.size} custom sounds")
    }

    fun setSoundConfig(type: SoundType, config: SoundConfig) {
        soundConfigs = soundConfigs + (type to config)
    }

    fun getSoundConfig(type: SoundType): SoundConfig? = soundConfigs[type]

    fun playPreset(preset: SoundPreset) {
        if (!enabled) return
        if (preset == SoundPreset.SILENT || preset == SoundPreset.CUSTOM) return

        val soundId = soundIds[preset] ?: return
        if (soundId == 0) return

        val now = System.currentTimeMillis()
        val lastTime = lastPlayTime[preset] ?: 0L
        if (now - lastTime < DEBOUNCE_MS) {
            return
        }
        lastPlayTime[preset] = now

        try {
            soundPool?.play(soundId, volume, volume, 1, 0, 1f)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play preset ${preset.name}", e)
        }
    }

    fun play(type: SoundType) {
        if (!enabled) return
        if (type == SoundType.SILENT) return

        val config = soundConfigs[type]
        when {
            config?.presetName == SoundPreset.SILENT.name -> {
                Log.d(TAG, "Sound $type is silenced")
                return
            }
            config?.customFilePath != null -> {
                playCustomFile(config.customFilePath)
                return
            }
            config?.presetName != null -> {
                val preset = SoundPreset.entries.find { it.name == config.presetName }
                if (preset != null && preset.resourceId != null) {
                    playPreset(preset)
                    return
                }
            }
        }

        val preset = defaultPresetMap[type] ?: return
        playPreset(preset)
    }

    private fun playCustomFile(path: String) {
        Log.d(TAG, "Custom file playback not yet implemented: $path")
    }

    fun release() {
        soundPool?.release()
        soundPool = null
        soundIds.clear()
        lastPlayTime.clear()
        soundsLoaded = false
    }
}
