package com.nendo.argosy.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.nendo.argosy.ui.input.SoundConfig
import com.nendo.argosy.ui.input.SoundType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

data class ControlsPreferences(
    val hapticEnabled: Boolean = true,
    val soundEnabled: Boolean = false,
    val soundVolume: Int = 40,
    val soundConfigs: Map<SoundType, SoundConfig> = emptyMap(),
    val swapAB: Boolean = false,
    val swapXY: Boolean = false,
    val controllerLayout: String = "auto",
    val swapStartSelect: Boolean = false,
    val accuratePlayTimeEnabled: Boolean = false,
    val ambientAudioEnabled: Boolean = false,
    val ambientAudioVolume: Int = 50,
    val ambientAudioUri: String? = null,
    val ambientAudioShuffle: Boolean = false
)

@Singleton
class ControlsPreferencesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    private object Keys {
        val HAPTIC_ENABLED = booleanPreferencesKey("haptic_enabled")
        val SOUND_ENABLED = booleanPreferencesKey("sound_enabled")
        val SOUND_VOLUME = intPreferencesKey("sound_volume")
        val SOUND_CONFIGS = stringPreferencesKey("sound_configs")
        val SWAP_AB = booleanPreferencesKey("nintendo_button_layout")
        val SWAP_XY = booleanPreferencesKey("swap_xy")
        val CONTROLLER_LAYOUT = stringPreferencesKey("controller_layout")
        val SWAP_START_SELECT = booleanPreferencesKey("swap_start_select")
        val ACCURATE_PLAY_TIME_ENABLED = booleanPreferencesKey("accurate_play_time_enabled")
        val AMBIENT_AUDIO_ENABLED = booleanPreferencesKey("ambient_audio_enabled")
        val AMBIENT_AUDIO_VOLUME = intPreferencesKey("ambient_audio_volume")
        val AMBIENT_AUDIO_URI = stringPreferencesKey("ambient_audio_uri")
        val AMBIENT_AUDIO_SHUFFLE = booleanPreferencesKey("ambient_audio_shuffle")
    }

    val preferences: Flow<ControlsPreferences> = dataStore.data.map { prefs ->
        ControlsPreferences(
            hapticEnabled = prefs[Keys.HAPTIC_ENABLED] ?: true,
            soundEnabled = prefs[Keys.SOUND_ENABLED] ?: false,
            soundVolume = prefs[Keys.SOUND_VOLUME] ?: 40,
            soundConfigs = parseSoundConfigs(prefs[Keys.SOUND_CONFIGS]),
            swapAB = prefs[Keys.SWAP_AB] ?: false,
            swapXY = prefs[Keys.SWAP_XY] ?: false,
            controllerLayout = prefs[Keys.CONTROLLER_LAYOUT] ?: "auto",
            swapStartSelect = prefs[Keys.SWAP_START_SELECT] ?: false,
            accuratePlayTimeEnabled = prefs[Keys.ACCURATE_PLAY_TIME_ENABLED] ?: false,
            ambientAudioEnabled = prefs[Keys.AMBIENT_AUDIO_ENABLED] ?: false,
            ambientAudioVolume = prefs[Keys.AMBIENT_AUDIO_VOLUME] ?: 50,
            ambientAudioUri = prefs[Keys.AMBIENT_AUDIO_URI],
            ambientAudioShuffle = prefs[Keys.AMBIENT_AUDIO_SHUFFLE] ?: false
        )
    }

    suspend fun setHapticEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.HAPTIC_ENABLED] = enabled }
    }

    suspend fun setSoundEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.SOUND_ENABLED] = enabled }
    }

    suspend fun setSoundVolume(volume: Int) {
        dataStore.edit { it[Keys.SOUND_VOLUME] = volume.coerceIn(0, 100) }
    }

    suspend fun setSoundConfigs(configs: Map<SoundType, SoundConfig>) {
        dataStore.edit { prefs ->
            if (configs.isEmpty()) {
                prefs.remove(Keys.SOUND_CONFIGS)
            } else {
                prefs[Keys.SOUND_CONFIGS] = serializeSoundConfigs(configs)
            }
        }
    }

    suspend fun setSoundConfig(type: SoundType, config: SoundConfig?) {
        dataStore.edit { prefs ->
            val current = parseSoundConfigs(prefs[Keys.SOUND_CONFIGS])
            val updated = if (config != null) {
                current + (type to config)
            } else {
                current - type
            }
            if (updated.isEmpty()) {
                prefs.remove(Keys.SOUND_CONFIGS)
            } else {
                prefs[Keys.SOUND_CONFIGS] = serializeSoundConfigs(updated)
            }
        }
    }

    suspend fun setSwapAB(enabled: Boolean) {
        dataStore.edit { it[Keys.SWAP_AB] = enabled }
    }

    suspend fun setSwapXY(enabled: Boolean) {
        dataStore.edit { it[Keys.SWAP_XY] = enabled }
    }

    suspend fun setControllerLayout(layout: String) {
        dataStore.edit { it[Keys.CONTROLLER_LAYOUT] = layout }
    }

    suspend fun setSwapStartSelect(enabled: Boolean) {
        dataStore.edit { it[Keys.SWAP_START_SELECT] = enabled }
    }

    suspend fun setAccuratePlayTimeEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.ACCURATE_PLAY_TIME_ENABLED] = enabled }
    }

    suspend fun setAmbientAudioEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.AMBIENT_AUDIO_ENABLED] = enabled }
    }

    suspend fun setAmbientAudioVolume(volume: Int) {
        dataStore.edit { it[Keys.AMBIENT_AUDIO_VOLUME] = volume.coerceIn(0, 100) }
    }

    suspend fun setAmbientAudioUri(uri: String?) {
        dataStore.edit { prefs ->
            if (uri != null) prefs[Keys.AMBIENT_AUDIO_URI] = uri
            else prefs.remove(Keys.AMBIENT_AUDIO_URI)
        }
    }

    suspend fun setAmbientAudioShuffle(shuffle: Boolean) {
        dataStore.edit { it[Keys.AMBIENT_AUDIO_SHUFFLE] = shuffle }
    }

    private fun parseSoundConfigs(raw: String?): Map<SoundType, SoundConfig> {
        if (raw.isNullOrBlank()) return emptyMap()
        return raw.split(";")
            .mapNotNull { entry ->
                val parts = entry.split("=", limit = 2)
                if (parts.size != 2) return@mapNotNull null
                val soundType = try { SoundType.valueOf(parts[0]) } catch (_: Exception) { return@mapNotNull null }
                val value = parts[1]
                val config = when {
                    value.startsWith("custom:") -> SoundConfig(customFilePath = value.removePrefix("custom:"))
                    else -> SoundConfig(presetName = value)
                }
                soundType to config
            }
            .toMap()
    }

    private fun serializeSoundConfigs(configs: Map<SoundType, SoundConfig>): String {
        return configs.entries.joinToString(";") { (type, config) ->
            val value = when {
                config.customFilePath != null -> "custom:${config.customFilePath}"
                config.presetName != null -> config.presetName
                else -> return@joinToString ""
            }
            "${type.name}=$value"
        }
    }
}
