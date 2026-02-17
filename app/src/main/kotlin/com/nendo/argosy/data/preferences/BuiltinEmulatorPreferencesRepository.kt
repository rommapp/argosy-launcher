package com.nendo.argosy.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BuiltinEmulatorPreferencesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    private object Keys {
        val BUILTIN_SHADER = stringPreferencesKey("builtin_shader")
        val BUILTIN_SHADER_CHAIN = stringPreferencesKey("builtin_shader_chain")
        val BUILTIN_FILTER = stringPreferencesKey("builtin_filter")
        val BUILTIN_LIBRETRO_ENABLED = booleanPreferencesKey("builtin_libretro_enabled")
        val BUILTIN_ASPECT_RATIO = stringPreferencesKey("builtin_aspect_ratio")
        val BUILTIN_SKIP_DUPLICATE_FRAMES = booleanPreferencesKey("builtin_skip_duplicate_frames")
        val BUILTIN_LOW_LATENCY_AUDIO = booleanPreferencesKey("builtin_low_latency_audio")
        val BUILTIN_FORCE_SOFTWARE_TIMING = booleanPreferencesKey("builtin_force_software_timing")
        val BUILTIN_RUMBLE_ENABLED = booleanPreferencesKey("builtin_rumble_enabled")
        val BUILTIN_BLACK_FRAME_INSERTION = booleanPreferencesKey("builtin_black_frame_insertion")
        val BUILTIN_LIMIT_HOTKEYS_TO_PLAYER1 = booleanPreferencesKey("builtin_limit_hotkeys_to_player1")
        val BUILTIN_ANALOG_AS_DPAD = booleanPreferencesKey("builtin_analog_as_dpad")
        val BUILTIN_DPAD_AS_ANALOG = booleanPreferencesKey("builtin_dpad_as_analog")
        val BUILTIN_CORE_SELECTIONS = stringPreferencesKey("builtin_core_selections")
        val BUILTIN_FAST_FORWARD_SPEED = intPreferencesKey("builtin_fast_forward_speed")
        val BUILTIN_ROTATION = intPreferencesKey("builtin_rotation")
        val BUILTIN_OVERSCAN_CROP = intPreferencesKey("builtin_overscan_crop")
        val BUILTIN_REWIND_ENABLED = booleanPreferencesKey("builtin_rewind_enabled")
        val BUILTIN_FRAMES_ENABLED = booleanPreferencesKey("builtin_frames_enabled")
        val BUILTIN_MIGRATION_V1 = booleanPreferencesKey("builtin_migration_v2")
    }

    fun isBuiltinLibretroEnabled(): Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[Keys.BUILTIN_LIBRETRO_ENABLED] ?: true
    }

    fun getBuiltinEmulatorSettings(): Flow<BuiltinEmulatorSettings> = dataStore.data.map { prefs ->
        BuiltinEmulatorSettings(
            shader = prefs[Keys.BUILTIN_SHADER] ?: "None",
            shaderChainJson = prefs[Keys.BUILTIN_SHADER_CHAIN] ?: "",
            filter = prefs[Keys.BUILTIN_FILTER] ?: "Auto",
            aspectRatio = prefs[Keys.BUILTIN_ASPECT_RATIO] ?: "Core Provided",
            skipDuplicateFrames = prefs[Keys.BUILTIN_SKIP_DUPLICATE_FRAMES] ?: false,
            lowLatencyAudio = prefs[Keys.BUILTIN_LOW_LATENCY_AUDIO] ?: true,
            forceSoftwareTiming = prefs[Keys.BUILTIN_FORCE_SOFTWARE_TIMING] ?: false,
            rumbleEnabled = prefs[Keys.BUILTIN_RUMBLE_ENABLED] ?: true,
            blackFrameInsertion = prefs[Keys.BUILTIN_BLACK_FRAME_INSERTION] ?: false,
            framesEnabled = prefs[Keys.BUILTIN_FRAMES_ENABLED] ?: false,
            limitHotkeysToPlayer1 = prefs[Keys.BUILTIN_LIMIT_HOTKEYS_TO_PLAYER1] ?: true,
            analogAsDpad = prefs[Keys.BUILTIN_ANALOG_AS_DPAD] ?: false,
            dpadAsAnalog = prefs[Keys.BUILTIN_DPAD_AS_ANALOG] ?: false,
            fastForwardSpeed = prefs[Keys.BUILTIN_FAST_FORWARD_SPEED] ?: 4,
            rotation = prefs[Keys.BUILTIN_ROTATION] ?: -1,
            overscanCrop = prefs[Keys.BUILTIN_OVERSCAN_CROP] ?: 0,
            rewindEnabled = prefs[Keys.BUILTIN_REWIND_ENABLED] ?: true
        )
    }

    fun getBuiltinCoreSelections(): Flow<Map<String, String>> = dataStore.data.map { prefs ->
        prefs[Keys.BUILTIN_CORE_SELECTIONS]
            ?.split(",")
            ?.filter { it.isNotBlank() && it.contains(":") }
            ?.associate {
                val parts = it.split(":")
                parts[0] to parts.getOrElse(1) { "" }
            }
            ?: emptyMap()
    }

    fun isBuiltinMigrationComplete(): Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[Keys.BUILTIN_MIGRATION_V1] ?: false
    }

    suspend fun setBuiltinShader(shader: String) {
        dataStore.edit { it[Keys.BUILTIN_SHADER] = shader }
    }

    suspend fun setBuiltinShaderChain(chainJson: String) {
        dataStore.edit { it[Keys.BUILTIN_SHADER_CHAIN] = chainJson }
    }

    suspend fun setBuiltinFilter(filter: String) {
        dataStore.edit { it[Keys.BUILTIN_FILTER] = filter }
    }

    suspend fun setBuiltinLibretroEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.BUILTIN_LIBRETRO_ENABLED] = enabled }
    }

    suspend fun setBuiltinAspectRatio(aspectRatio: String) {
        dataStore.edit { it[Keys.BUILTIN_ASPECT_RATIO] = aspectRatio }
    }

    suspend fun setBuiltinSkipDuplicateFrames(enabled: Boolean) {
        dataStore.edit { it[Keys.BUILTIN_SKIP_DUPLICATE_FRAMES] = enabled }
    }

    suspend fun setBuiltinLowLatencyAudio(enabled: Boolean) {
        dataStore.edit { it[Keys.BUILTIN_LOW_LATENCY_AUDIO] = enabled }
    }

    suspend fun setBuiltinForceSoftwareTiming(enabled: Boolean) {
        dataStore.edit { it[Keys.BUILTIN_FORCE_SOFTWARE_TIMING] = enabled }
    }

    suspend fun setBuiltinRumbleEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.BUILTIN_RUMBLE_ENABLED] = enabled }
    }

    suspend fun setBuiltinBlackFrameInsertion(enabled: Boolean) {
        dataStore.edit { it[Keys.BUILTIN_BLACK_FRAME_INSERTION] = enabled }
    }

    suspend fun setBuiltinLimitHotkeysToPlayer1(enabled: Boolean) {
        dataStore.edit { it[Keys.BUILTIN_LIMIT_HOTKEYS_TO_PLAYER1] = enabled }
    }

    suspend fun setBuiltinAnalogAsDpad(enabled: Boolean) {
        dataStore.edit { it[Keys.BUILTIN_ANALOG_AS_DPAD] = enabled }
    }

    suspend fun setBuiltinDpadAsAnalog(enabled: Boolean) {
        dataStore.edit { it[Keys.BUILTIN_DPAD_AS_ANALOG] = enabled }
    }

    suspend fun setBuiltinCoreForPlatform(platformSlug: String, coreId: String) {
        dataStore.edit { prefs ->
            val current = prefs[Keys.BUILTIN_CORE_SELECTIONS]
                ?.split(",")
                ?.filter { it.isNotBlank() }
                ?.associate {
                    val parts = it.split(":")
                    parts[0] to parts.getOrElse(1) { "" }
                }
                ?.toMutableMap()
                ?: mutableMapOf()

            current[platformSlug] = coreId

            prefs[Keys.BUILTIN_CORE_SELECTIONS] = current.entries
                .joinToString(",") { "${it.key}:${it.value}" }
        }
    }

    suspend fun setBuiltinFastForwardSpeed(speed: Int) {
        dataStore.edit { it[Keys.BUILTIN_FAST_FORWARD_SPEED] = speed.coerceIn(2, 8) }
    }

    suspend fun setBuiltinRotation(rotation: Int) {
        dataStore.edit { it[Keys.BUILTIN_ROTATION] = rotation }
    }

    suspend fun setBuiltinOverscanCrop(crop: Int) {
        dataStore.edit { it[Keys.BUILTIN_OVERSCAN_CROP] = crop.coerceIn(0, 16) }
    }

    suspend fun setBuiltinRewindEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.BUILTIN_REWIND_ENABLED] = enabled }
    }

    suspend fun setBuiltinFramesEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.BUILTIN_FRAMES_ENABLED] = enabled }
    }

    suspend fun setBuiltinMigrationComplete() {
        dataStore.edit { it[Keys.BUILTIN_MIGRATION_V1] = true }
    }
}
