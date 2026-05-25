package com.nendo.argosy.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.nendo.argosy.data.local.entity.FastForwardMode
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
        val BUILTIN_FAST_FORWARD_ENABLED = booleanPreferencesKey("builtin_fast_forward_enabled")
        val BUILTIN_FAST_FORWARD_SPEED = intPreferencesKey("builtin_fast_forward_speed")
        val BUILTIN_FAST_FORWARD_MODE = stringPreferencesKey("builtin_fast_forward_mode")
        val BUILTIN_FAST_FORWARD_PRESERVE_PITCH = booleanPreferencesKey("builtin_fast_forward_preserve_pitch")
        val BUILTIN_ROTATION = intPreferencesKey("builtin_rotation")
        val BUILTIN_OVERSCAN_CROP = intPreferencesKey("builtin_overscan_crop")
        val BUILTIN_REWIND_ENABLED = booleanPreferencesKey("builtin_rewind_enabled")
        val BUILTIN_REWIND_SPEED = intPreferencesKey("builtin_rewind_speed")
        val BUILTIN_REWIND_BUFFER_DURATION = intPreferencesKey("builtin_rewind_buffer_duration")
        val BUILTIN_FRAMES_ENABLED = booleanPreferencesKey("builtin_frames_enabled")
        val BUILTIN_AUTO_SAVE_STATE = booleanPreferencesKey("builtin_auto_save_state")
        val BUILTIN_AUTO_RESTORE_STATE = booleanPreferencesKey("builtin_auto_restore_state")
        val BUILTIN_AUTO_RESTORE_STATE_MODE = stringPreferencesKey("builtin_auto_restore_state_mode")
        val BUILTIN_CUSTOM_SAVE_PATH = stringPreferencesKey("builtin_custom_save_path")
        val BUILTIN_CUSTOM_STATE_PATH = stringPreferencesKey("builtin_custom_state_path")
        val BUILTIN_MIGRATION_V1 = booleanPreferencesKey("builtin_migration_v2")
        val BUILTIN_ARCHITECTURE_OVERRIDE = stringPreferencesKey("builtin_architecture_override")
        val TOUCH_SHOW_WHEN_NO_GAMEPAD = booleanPreferencesKey("builtin_touch_show_when_no_gamepad")
        val TOUCH_OPACITY_LANDSCAPE = floatPreferencesKey("builtin_touch_opacity_landscape")
        val TOUCH_OPACITY_PORTRAIT = floatPreferencesKey("builtin_touch_opacity_portrait")
        val TOUCH_SIZE_SCALE = floatPreferencesKey("builtin_touch_size_scale")
        val TOUCH_HAPTIC = booleanPreferencesKey("builtin_touch_haptic")
        val TOUCH_FADE_ON_IDLE = booleanPreferencesKey("builtin_touch_fade_on_idle")
        val TOUCH_SWAP_HANDED = booleanPreferencesKey("builtin_touch_swap_handed")
        val TOUCH_LOCK_ORIENTATION = booleanPreferencesKey("builtin_touch_lock_orientation")
        val TOUCH_MIRROR_180 = booleanPreferencesKey("builtin_touch_mirror_180")
        val TOUCH_ALLOW_LONG_PRESS_EDIT = booleanPreferencesKey("builtin_touch_allow_long_press_edit")
        val TOUCH_COLOURED_FACE_BUTTONS = booleanPreferencesKey("builtin_touch_coloured_face_buttons")
        val TOUCH_GENESIS_6_BUTTON = booleanPreferencesKey("builtin_touch_genesis_6_button")
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
            fastForwardEnabled = prefs[Keys.BUILTIN_FAST_FORWARD_ENABLED] ?: true,
            fastForwardSpeed = prefs[Keys.BUILTIN_FAST_FORWARD_SPEED] ?: 4,
            fastForwardMode = FastForwardMode.fromString(prefs[Keys.BUILTIN_FAST_FORWARD_MODE]),
            fastForwardPreservePitch = prefs[Keys.BUILTIN_FAST_FORWARD_PRESERVE_PITCH] ?: false,
            rotation = prefs[Keys.BUILTIN_ROTATION] ?: -1,
            overscanCrop = prefs[Keys.BUILTIN_OVERSCAN_CROP] ?: 0,
            rewindEnabled = prefs[Keys.BUILTIN_REWIND_ENABLED] ?: true,
            rewindSpeed = prefs[Keys.BUILTIN_REWIND_SPEED] ?: 1,
            rewindBufferDuration = prefs[Keys.BUILTIN_REWIND_BUFFER_DURATION] ?: 15,
            autoSaveState = prefs[Keys.BUILTIN_AUTO_SAVE_STATE] ?: true,
            autoRestoreState = prefs[Keys.BUILTIN_AUTO_RESTORE_STATE]
                ?: (prefs[Keys.BUILTIN_AUTO_RESTORE_STATE_MODE] != "off"),
            autoRestoreStateMode = prefs[Keys.BUILTIN_AUTO_RESTORE_STATE_MODE] ?: "restore",
            customSavePath = prefs[Keys.BUILTIN_CUSTOM_SAVE_PATH],
            customStatePath = prefs[Keys.BUILTIN_CUSTOM_STATE_PATH],
            architectureOverride = prefs[Keys.BUILTIN_ARCHITECTURE_OVERRIDE],
            showTouchControlsWhenNoGamepad = prefs[Keys.TOUCH_SHOW_WHEN_NO_GAMEPAD] ?: true,
            touchControlsOpacityLandscape = prefs[Keys.TOUCH_OPACITY_LANDSCAPE] ?: 0.45f,
            touchControlsOpacityPortrait = prefs[Keys.TOUCH_OPACITY_PORTRAIT] ?: 1.0f,
            touchControlsSizeScale = prefs[Keys.TOUCH_SIZE_SCALE] ?: 1.0f,
            touchControlsHaptic = prefs[Keys.TOUCH_HAPTIC] ?: true,
            touchControlsFadeOnIdle = prefs[Keys.TOUCH_FADE_ON_IDLE] ?: false,
            touchControlsSwapHanded = prefs[Keys.TOUCH_SWAP_HANDED] ?: false,
            touchControlsLockOrientation = prefs[Keys.TOUCH_LOCK_ORIENTATION] ?: false,
            touchControlsMirror180 = prefs[Keys.TOUCH_MIRROR_180] ?: false,
            touchControlsAllowLongPressEdit = prefs[Keys.TOUCH_ALLOW_LONG_PRESS_EDIT] ?: false,
            touchControlsColouredFaceButtons = prefs[Keys.TOUCH_COLOURED_FACE_BUTTONS] ?: false,
            touchControlsGenesis6Button = prefs[Keys.TOUCH_GENESIS_6_BUTTON] ?: false
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

    suspend fun setBuiltinFastForwardEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.BUILTIN_FAST_FORWARD_ENABLED] = enabled }
    }

    suspend fun setBuiltinFastForwardSpeed(speed: Int) {
        dataStore.edit { it[Keys.BUILTIN_FAST_FORWARD_SPEED] = speed.coerceIn(2, 8) }
    }

    suspend fun setBuiltinFastForwardMode(mode: FastForwardMode) {
        dataStore.edit { it[Keys.BUILTIN_FAST_FORWARD_MODE] = mode.name }
    }

    suspend fun setBuiltinFastForwardPreservePitch(enabled: Boolean) {
        dataStore.edit { it[Keys.BUILTIN_FAST_FORWARD_PRESERVE_PITCH] = enabled }
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

    suspend fun setBuiltinRewindSpeed(speed: Int) {
        val valid = listOf(1, 2, 4)
        dataStore.edit { it[Keys.BUILTIN_REWIND_SPEED] = valid.minByOrNull { v -> kotlin.math.abs(v - speed) } ?: 1 }
    }

    suspend fun setBuiltinRewindBufferDuration(duration: Int) {
        val valid = listOf(5, 15, 30, 60)
        dataStore.edit { it[Keys.BUILTIN_REWIND_BUFFER_DURATION] = valid.minByOrNull { v -> kotlin.math.abs(v - duration) } ?: 15 }
    }

    suspend fun setBuiltinFramesEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.BUILTIN_FRAMES_ENABLED] = enabled }
    }

    suspend fun setBuiltinAutoSaveState(enabled: Boolean) {
        dataStore.edit { it[Keys.BUILTIN_AUTO_SAVE_STATE] = enabled }
    }

    suspend fun setBuiltinAutoRestoreState(enabled: Boolean) {
        dataStore.edit { it[Keys.BUILTIN_AUTO_RESTORE_STATE] = enabled }
    }

    suspend fun setBuiltinCustomSavePath(path: String?) {
        dataStore.edit {
            if (path != null) it[Keys.BUILTIN_CUSTOM_SAVE_PATH] = path
            else it.remove(Keys.BUILTIN_CUSTOM_SAVE_PATH)
        }
    }

    suspend fun setBuiltinCustomStatePath(path: String?) {
        dataStore.edit {
            if (path != null) it[Keys.BUILTIN_CUSTOM_STATE_PATH] = path
            else it.remove(Keys.BUILTIN_CUSTOM_STATE_PATH)
        }
    }

    suspend fun setBuiltinAutoRestoreStateMode(mode: String) {
        dataStore.edit { it[Keys.BUILTIN_AUTO_RESTORE_STATE_MODE] = mode }
    }

    suspend fun setBuiltinMigrationComplete() {
        dataStore.edit { it[Keys.BUILTIN_MIGRATION_V1] = true }
    }

    suspend fun setTouchControlsShowWhenNoGamepad(enabled: Boolean) {
        dataStore.edit { it[Keys.TOUCH_SHOW_WHEN_NO_GAMEPAD] = enabled }
    }

    suspend fun setTouchControlsOpacityLandscape(opacity: Float) {
        dataStore.edit { it[Keys.TOUCH_OPACITY_LANDSCAPE] = opacity.coerceIn(0.2f, 1.0f) }
    }

    suspend fun setTouchControlsOpacityPortrait(opacity: Float) {
        dataStore.edit { it[Keys.TOUCH_OPACITY_PORTRAIT] = opacity.coerceIn(0.5f, 1.0f) }
    }

    suspend fun setTouchControlsSizeScale(scale: Float) {
        dataStore.edit { it[Keys.TOUCH_SIZE_SCALE] = scale.coerceIn(0.7f, 1.4f) }
    }

    suspend fun setTouchControlsHaptic(enabled: Boolean) {
        dataStore.edit { it[Keys.TOUCH_HAPTIC] = enabled }
    }

    suspend fun setTouchControlsFadeOnIdle(enabled: Boolean) {
        dataStore.edit { it[Keys.TOUCH_FADE_ON_IDLE] = enabled }
    }

    suspend fun setTouchControlsSwapHanded(enabled: Boolean) {
        dataStore.edit { it[Keys.TOUCH_SWAP_HANDED] = enabled }
    }

    suspend fun setTouchControlsLockOrientation(enabled: Boolean) {
        dataStore.edit { it[Keys.TOUCH_LOCK_ORIENTATION] = enabled }
    }

    suspend fun setTouchControlsMirror180(enabled: Boolean) {
        dataStore.edit { it[Keys.TOUCH_MIRROR_180] = enabled }
    }

    suspend fun setTouchControlsAllowLongPressEdit(enabled: Boolean) {
        dataStore.edit { it[Keys.TOUCH_ALLOW_LONG_PRESS_EDIT] = enabled }
    }

    suspend fun setTouchControlsColouredFaceButtons(enabled: Boolean) {
        dataStore.edit { it[Keys.TOUCH_COLOURED_FACE_BUTTONS] = enabled }
    }

    suspend fun setTouchControlsGenesis6Button(enabled: Boolean) {
        dataStore.edit { it[Keys.TOUCH_GENESIS_6_BUTTON] = enabled }
    }

    fun getArchitectureOverride(): Flow<String?> = dataStore.data.map { prefs ->
        prefs[Keys.BUILTIN_ARCHITECTURE_OVERRIDE]
    }

    suspend fun setArchitectureOverride(abi: String?) {
        dataStore.edit {
            if (abi != null) it[Keys.BUILTIN_ARCHITECTURE_OVERRIDE] = abi
            else it.remove(Keys.BUILTIN_ARCHITECTURE_OVERRIDE)
        }
    }
}
