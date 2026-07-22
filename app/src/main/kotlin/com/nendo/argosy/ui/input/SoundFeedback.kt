package com.nendo.argosy.ui.input

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.SystemClock
import android.util.Log
import com.nendo.argosy.R
import com.nendo.argosy.core.input.SoundConfig
import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.core.media.SfxTranscoder
import com.nendo.argosy.data.storage.StorageAttributionRepository
import com.nendo.argosy.data.storage.StorageCategory
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "SoundFeedback"

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
    CUSTOM(null, "Custom File"),
    ROMM_MUSIC(null, "RomM Music"),
    DEFAULT(null, "Default");
}

@Singleton
class SoundFeedbackManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val attributionRepository: StorageAttributionRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val initLock = Any()

    @Volatile
    private var enabled = false

    @Volatile
    private var volume = 0.4f

    @Volatile
    private var soundPool: SoundPool? = null
    private val soundIds = ConcurrentHashMap<SoundPreset, Int>()
    private val customSoundIds = ConcurrentHashMap<SoundType, Int>()
    private val customSoundPaths = ConcurrentHashMap<SoundType, String>()
    private val loadedSampleIds: MutableSet<Int> = ConcurrentHashMap.newKeySet()

    @Volatile
    private var soundConfigs: Map<SoundType, SoundConfig> = emptyMap()

    @Volatile
    private var lastBoundaryPlayMs = 0L

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
        SoundType.TOGGLE to SoundPreset.CLICK_SOFT,
        SoundType.LAUNCH_GAME to SoundPreset.COLLECT
    )

    private val presetPriorities = mapOf(
        SoundPreset.TAP_LIGHT to PRIORITY_LOW,
        SoundPreset.HOVER_SOFT to PRIORITY_LOW,
        SoundPreset.CLICK_SOFT to PRIORITY_LOW,
        SoundPreset.CHIME_SUCCESS to PRIORITY_HIGH,
        SoundPreset.COLLECT to PRIORITY_HIGH,
        SoundPreset.BUZZ_ERROR to PRIORITY_HIGH,
        SoundPreset.NOTIFY_START to PRIORITY_HIGH
    )

    companion object {
        private const val PRIORITY_LOW = 1
        private const val PRIORITY_DEFAULT = 2
        private const val PRIORITY_HIGH = 3
        private const val BOUNDARY_RATE_LIMIT_MS = 500L
    }

    fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
        if (enabled) preload() else releaseSoundPool()
    }

    private fun preload() {
        scope.launch {
            synchronized(initLock) {
                if (!enabled || soundPool != null) return@launch
                initSoundPool()
            }
        }
    }

    private fun initSoundPool() {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val pool = SoundPool.Builder()
            .setMaxStreams(10)
            .setAudioAttributes(audioAttributes)
            .build().apply {
                setOnLoadCompleteListener { _, sampleId, status ->
                    if (status == 0) loadedSampleIds.add(sampleId)
                }
            }
        soundPool = pool

        SoundPreset.entries.forEach { preset ->
            preset.resourceId?.let { resId ->
                try {
                    soundIds[preset] = pool.load(context, resId, 1)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to load ${preset.name}", e)
                }
            }
        }
        syncCustomSounds(pool)
    }

    private fun syncCustomSounds(pool: SoundPool) {
        val desired = soundConfigs.entries
            .mapNotNull { (type, config) -> config.customFilePath?.let { type to it } }
            .toMap()
        customSoundPaths.keys.filterNot { it in desired }.forEach { type ->
            unloadCustomSound(pool, type)
        }
        desired.forEach { (type, path) ->
            if (customSoundPaths[type] != path) {
                unloadCustomSound(pool, type)
                loadCustomSound(pool, type, path)
            }
        }
    }

    private fun unloadCustomSound(pool: SoundPool, type: SoundType) {
        customSoundPaths.remove(type)
        customSoundIds.remove(type)?.let { sampleId ->
            loadedSampleIds.remove(sampleId)
            pool.unload(sampleId)
        }
    }

    private fun loadCustomSound(pool: SoundPool, type: SoundType, path: String) {
        if (!File(path).exists()) {
            Log.w(TAG, "Custom sound missing for ${type.name}: $path")
            return
        }
        val playablePath = resolvePlayablePath(path)
        if (playablePath == null) {
            Log.w(TAG, "Skipping custom sound for ${type.name}: transcode failed for $path")
            return
        }
        try {
            customSoundIds[type] = pool.load(playablePath, 1)
            customSoundPaths[type] = path
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load custom sound for ${type.name}", e)
        }
    }

    private fun resolvePlayablePath(path: String): String? {
        if (path.substringAfterLast('.', "").lowercase() == "wav") return path
        return SfxTranscoder.transcodeToWavCache(context, path)?.absolutePath
    }

    private fun releaseSoundPool() {
        synchronized(initLock) {
            soundPool?.release()
            soundPool = null
            soundIds.clear()
            customSoundIds.clear()
            customSoundPaths.clear()
            loadedSampleIds.clear()
        }
    }

    private fun shouldPlaySound(type: SoundType): Boolean {
        if (!enabled) return false
        if (type == SoundType.SILENT) return false
        val config = soundConfigs[type]
        if (config?.presetName == SoundPreset.SILENT.name) return false
        return true
    }

    fun setVolume(volume: Int) {
        val linear = (volume / 100f).coerceIn(0f, 1f)
        this.volume = linear * linear
    }

    fun setSoundConfigs(configs: Map<SoundType, SoundConfig>) {
        soundConfigs = configs
        refreshCustomSounds()
    }

    fun setSoundConfig(type: SoundType, config: SoundConfig) {
        soundConfigs = soundConfigs + (type to config)
        refreshCustomSounds()
    }

    fun clearSoundConfig(type: SoundType) {
        soundConfigs = soundConfigs - type
        refreshCustomSounds()
    }

    fun playDefault(type: SoundType) {
        defaultPresetMap[type]?.let { playPreset(it) }
    }

    private fun refreshCustomSounds() {
        scope.launch {
            synchronized(initLock) {
                soundPool?.let { syncCustomSounds(it) }
            }
        }
    }

    fun getSoundConfig(type: SoundType): SoundConfig? = soundConfigs[type]

    fun playPreset(preset: SoundPreset) {
        if (!enabled) return
        if (preset == SoundPreset.SILENT || preset == SoundPreset.CUSTOM) return

        val pool = soundPool
        if (pool == null) {
            preload()
            return
        }
        val soundId = soundIds[preset] ?: return
        if (soundId !in loadedSampleIds) return

        try {
            val priority = presetPriorities[preset] ?: PRIORITY_DEFAULT
            pool.play(soundId, volume, volume, priority, 0, 1f)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play preset ${preset.name}", e)
        }
    }

    fun play(type: SoundType) {
        if (!shouldPlaySound(type)) return
        if (type == SoundType.BOUNDARY) {
            val now = SystemClock.elapsedRealtime()
            if (now - lastBoundaryPlayMs < BOUNDARY_RATE_LIMIT_MS) return
            lastBoundaryPlayMs = now
        }

        val config = soundConfigs[type]
        if (config?.customFilePath != null && playCustomSample(type)) return
        val preset = config?.presetName
            ?.let { name -> SoundPreset.entries.find { it.name == name } }
            ?.takeIf { it.resourceId != null }
            ?: defaultPresetMap[type]
            ?: return
        playPreset(preset)
    }

    /** Plays the loaded custom sample assigned to this type, if any; for picker previews. */
    fun playCustom(type: SoundType) {
        if (!enabled) return
        playCustomSample(type)
    }

    private fun playCustomSample(type: SoundType): Boolean {
        val pool = soundPool ?: return false
        val soundId = customSoundIds[type] ?: return false
        if (soundId !in loadedSampleIds) return false
        return try {
            pool.play(soundId, volume, volume, PRIORITY_DEFAULT, 0, 1f) != 0
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play custom sound for ${type.name}", e)
            false
        }
    }

    fun release() {
        releaseSoundPool()
    }

    /** Deletes transcoded SFX WAVs; loaded samples stay in memory and re-transcode on next load. */
    suspend fun clearSfxCache() = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "sfx")
        if (dir.exists()) dir.deleteRecursively()
        attributionRepository.markDirty(StorageCategory.SFX_CACHE)
    }
}
