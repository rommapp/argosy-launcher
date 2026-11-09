package com.nendo.argosy.data.preferences

import android.view.KeyEvent
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.nendo.argosy.core.input.SoundConfig
import com.nendo.argosy.core.input.SoundType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

data class ControlsPreferences(
    val hapticEnabled: Boolean = true,
    val hapticStrength: Float = DEFAULT_HAPTIC_STRENGTH,
    val soundEnabled: Boolean = false,
    val soundVolume: Int = 40,
    val soundConfigs: Map<SoundType, SoundConfig> = emptyMap(),
    val swapAB: Boolean = false,
    val swapXY: Boolean = false,
    val controllerLayout: String = "auto",
    val swapStartSelect: Boolean = false,
    val ambientAudioEnabled: Boolean = false,
    val ambientAudioVolume: Int = 50,
    val ambientAudioUri: String? = null,
    val ambientAudioShuffle: Boolean = false,
    val ambientAudioPlayInBackground: Boolean = false,
    val gameDetailThemeEnabled: Boolean = false,
    val selectLCombo: String = "quick_menu",
    val selectRCombo: String = "quick_settings",
    val menuWrapMode: MenuWrapMode = MenuWrapMode.HARD_STOP,
    val selectSwapMode: SelectSwapMode = SelectSwapMode.HOLD,
    val openNavigationKey: Int = DEFAULT_OPEN_NAVIGATION_KEY,
    val openQuickPanelKey: Int = DEFAULT_OPEN_QUICK_PANEL_KEY,
    val quickPanelInEmulators: Boolean = false,
    val navRingRoutes: List<String>? = null,
    val quickNavigation: Boolean = true,
    val navBarAutoHide: Boolean = true,
    val ignoreControllerTouchpad: Boolean = true,
    val systemHotkeysEnabled: Boolean = true,
    val systemHomeCombo: List<Int>? = null,
    val systemBackCombo: List<Int>? = null
) {
    companion object {
        const val DEFAULT_HAPTIC_STRENGTH = 0.5f
        const val HAPTIC_STRENGTH_STEPS = 10
        const val HAPTIC_STRENGTH_STEP = 1f / HAPTIC_STRENGTH_STEPS
        const val DEFAULT_OPEN_NAVIGATION_KEY = KeyEvent.KEYCODE_BUTTON_C
        const val DEFAULT_OPEN_QUICK_PANEL_KEY = KeyEvent.KEYCODE_BUTTON_Z
        const val UNASSIGNED_KEY = KeyEvent.KEYCODE_UNKNOWN
    }
}

@Singleton
class ControlsPreferencesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    private object Keys {
        val HAPTIC_ENABLED = booleanPreferencesKey("haptic_enabled")
        val HAPTIC_STRENGTH = floatPreferencesKey("haptic_strength")
        val SOUND_ENABLED = booleanPreferencesKey("sound_enabled")
        val SOUND_VOLUME = intPreferencesKey("sound_volume")
        val SOUND_CONFIGS = stringPreferencesKey("sound_configs")
        val SWAP_AB = booleanPreferencesKey("nintendo_button_layout")
        val SWAP_XY = booleanPreferencesKey("swap_xy")
        val CONTROLLER_LAYOUT = stringPreferencesKey("controller_layout")
        val SWAP_START_SELECT = booleanPreferencesKey("swap_start_select")
        val AMBIENT_AUDIO_ENABLED = booleanPreferencesKey("ambient_audio_enabled")
        val AMBIENT_AUDIO_VOLUME = intPreferencesKey("ambient_audio_volume")
        val AMBIENT_AUDIO_URI = stringPreferencesKey("ambient_audio_uri")
        val AMBIENT_AUDIO_SHUFFLE = booleanPreferencesKey("ambient_audio_shuffle")
        val AMBIENT_AUDIO_PLAY_IN_BACKGROUND = booleanPreferencesKey("ambient_audio_play_in_background")
        val GAME_DETAIL_THEME = booleanPreferencesKey("game_detail_theme")
        val SELECT_L_COMBO = stringPreferencesKey("select_l_combo")
        val SELECT_R_COMBO = stringPreferencesKey("select_r_combo")
        val MENU_WRAP_MODE = stringPreferencesKey("menu_wrap_mode")
        val SELECT_SWAP_MODE = stringPreferencesKey("select_swap_mode")
        val OPEN_NAVIGATION_KEY = intPreferencesKey("open_navigation_key")
        val OPEN_QUICK_PANEL_KEY = intPreferencesKey("open_quick_panel_key")
        val QUICK_PANEL_IN_EMULATORS = booleanPreferencesKey("quick_panel_in_emulators")
        val NAV_RING_ROUTES = stringPreferencesKey("nav_ring_routes")
        val QUICK_NAVIGATION = booleanPreferencesKey("quick_navigation")
        val NAV_BAR_AUTO_HIDE = booleanPreferencesKey("nav_bar_auto_hide")
        val IGNORE_CONTROLLER_TOUCHPAD = booleanPreferencesKey("ignore_controller_touchpad")
        val SYSTEM_HOTKEYS_ENABLED = booleanPreferencesKey("system_hotkeys_enabled")
        val SYSTEM_HOME_COMBO = stringPreferencesKey("system_home_combo")
        val SYSTEM_BACK_COMBO = stringPreferencesKey("system_back_combo")
    }

    val preferences: Flow<ControlsPreferences> = dataStore.data.map { prefs ->
        ControlsPreferences(
            hapticEnabled = prefs[Keys.HAPTIC_ENABLED] ?: true,
            hapticStrength = prefs[Keys.HAPTIC_STRENGTH] ?: ControlsPreferences.DEFAULT_HAPTIC_STRENGTH,
            soundEnabled = prefs[Keys.SOUND_ENABLED] ?: false,
            soundVolume = prefs[Keys.SOUND_VOLUME] ?: 40,
            soundConfigs = parseSoundConfigs(prefs[Keys.SOUND_CONFIGS]),
            swapAB = prefs[Keys.SWAP_AB] ?: false,
            swapXY = prefs[Keys.SWAP_XY] ?: false,
            controllerLayout = prefs[Keys.CONTROLLER_LAYOUT] ?: "auto",
            swapStartSelect = prefs[Keys.SWAP_START_SELECT] ?: false,
            ambientAudioEnabled = prefs[Keys.AMBIENT_AUDIO_ENABLED] ?: false,
            ambientAudioVolume = prefs[Keys.AMBIENT_AUDIO_VOLUME] ?: 50,
            ambientAudioUri = prefs[Keys.AMBIENT_AUDIO_URI],
            ambientAudioShuffle = prefs[Keys.AMBIENT_AUDIO_SHUFFLE] ?: false,
            ambientAudioPlayInBackground = prefs[Keys.AMBIENT_AUDIO_PLAY_IN_BACKGROUND] ?: false,
            gameDetailThemeEnabled = prefs[Keys.GAME_DETAIL_THEME] ?: false,
            selectLCombo = prefs[Keys.SELECT_L_COMBO] ?: "quick_menu",
            selectRCombo = prefs[Keys.SELECT_R_COMBO] ?: "quick_settings",
            menuWrapMode = MenuWrapMode.fromString(prefs[Keys.MENU_WRAP_MODE]),
            selectSwapMode = SelectSwapMode.fromString(prefs[Keys.SELECT_SWAP_MODE]),
            openNavigationKey = prefs[Keys.OPEN_NAVIGATION_KEY]
                ?: ControlsPreferences.DEFAULT_OPEN_NAVIGATION_KEY,
            openQuickPanelKey = prefs[Keys.OPEN_QUICK_PANEL_KEY]
                ?: ControlsPreferences.DEFAULT_OPEN_QUICK_PANEL_KEY,
            quickPanelInEmulators = prefs[Keys.QUICK_PANEL_IN_EMULATORS] ?: false,
            navRingRoutes = prefs[Keys.NAV_RING_ROUTES]
                ?.split(',')
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() },
            quickNavigation = prefs[Keys.QUICK_NAVIGATION] ?: true,
            navBarAutoHide = prefs[Keys.NAV_BAR_AUTO_HIDE] ?: true,
            ignoreControllerTouchpad = prefs[Keys.IGNORE_CONTROLLER_TOUCHPAD] ?: true,
            systemHotkeysEnabled = prefs[Keys.SYSTEM_HOTKEYS_ENABLED] ?: true,
            systemHomeCombo = parseKeyCodes(prefs[Keys.SYSTEM_HOME_COMBO]),
            systemBackCombo = parseKeyCodes(prefs[Keys.SYSTEM_BACK_COMBO])
        )
    }.flowOn(Dispatchers.Default)

    suspend fun setHapticEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.HAPTIC_ENABLED] = enabled }
    }

    suspend fun setHapticStrength(strength: Float) {
        dataStore.edit { it[Keys.HAPTIC_STRENGTH] = strength.coerceIn(0f, 1f) }
    }

    /**
     * Moves the stored haptic strength by [delta] inside one DataStore transaction, snapping the
     * result to the nearest of [ControlsPreferences.HAPTIC_STRENGTH_STEPS] steps, and returns the
     * stored value.
     */
    suspend fun adjustHapticStrength(delta: Float): Float {
        val steps = ControlsPreferences.HAPTIC_STRENGTH_STEPS
        val updated = dataStore.edit { prefs ->
            val current = prefs[Keys.HAPTIC_STRENGTH] ?: ControlsPreferences.DEFAULT_HAPTIC_STRENGTH
            val snapped = ((current + delta).coerceIn(0f, 1f) * steps).roundToInt() / steps.toFloat()
            prefs[Keys.HAPTIC_STRENGTH] = snapped
        }
        return updated[Keys.HAPTIC_STRENGTH] ?: ControlsPreferences.DEFAULT_HAPTIC_STRENGTH
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

    /** Repoints custom/romm sound paths under [oldPrefix] to [newPrefix] after a music relocation. */
    suspend fun rewriteSoundConfigPathPrefix(oldPrefix: String, newPrefix: String) {
        val old = oldPrefix.trimEnd('/')
        val new = newPrefix.trimEnd('/')
        dataStore.edit { prefs ->
            val current = parseSoundConfigs(prefs[Keys.SOUND_CONFIGS])
            if (current.isEmpty()) return@edit
            val updated = current.mapValues { (_, config) ->
                val path = config.customFilePath
                if (path != null && (path == old || path.startsWith("$old/"))) {
                    config.copy(customFilePath = new + path.removePrefix(old))
                } else {
                    config
                }
            }
            if (updated != current) {
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

    suspend fun setAmbientAudioPlayInBackground(enabled: Boolean) {
        dataStore.edit { it[Keys.AMBIENT_AUDIO_PLAY_IN_BACKGROUND] = enabled }
    }

    suspend fun setGameDetailThemeEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.GAME_DETAIL_THEME] = enabled }
    }

    suspend fun setSelectLCombo(value: String) {
        dataStore.edit { it[Keys.SELECT_L_COMBO] = value }
    }

    suspend fun setSelectRCombo(value: String) {
        dataStore.edit { it[Keys.SELECT_R_COMBO] = value }
    }

    suspend fun setMenuWrapMode(mode: MenuWrapMode) {
        dataStore.edit { it[Keys.MENU_WRAP_MODE] = mode.name }
    }

    suspend fun setSelectSwapMode(mode: SelectSwapMode) {
        dataStore.edit { it[Keys.SELECT_SWAP_MODE] = mode.name }
    }

    suspend fun setOpenNavigationKey(keyCode: Int) {
        dataStore.edit { it[Keys.OPEN_NAVIGATION_KEY] = keyCode }
    }

    suspend fun setOpenQuickPanelKey(keyCode: Int) {
        dataStore.edit { it[Keys.OPEN_QUICK_PANEL_KEY] = keyCode }
    }

    suspend fun setQuickPanelInEmulators(enabled: Boolean) {
        dataStore.edit { it[Keys.QUICK_PANEL_IN_EMULATORS] = enabled }
    }

    suspend fun setQuickNavigation(enabled: Boolean) {
        dataStore.edit { it[Keys.QUICK_NAVIGATION] = enabled }
    }

    suspend fun setIgnoreControllerTouchpad(enabled: Boolean) {
        dataStore.edit { it[Keys.IGNORE_CONTROLLER_TOUCHPAD] = enabled }
    }

    suspend fun setNavBarAutoHide(enabled: Boolean) {
        dataStore.edit { it[Keys.NAV_BAR_AUTO_HIDE] = enabled }
    }

    suspend fun setSystemHotkeysEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.SYSTEM_HOTKEYS_ENABLED] = enabled }
    }

    suspend fun setSystemHomeCombo(keyCodes: List<Int>?) {
        dataStore.edit { prefs -> writeKeyCodes(prefs, Keys.SYSTEM_HOME_COMBO, keyCodes) }
    }

    suspend fun setSystemBackCombo(keyCodes: List<Int>?) {
        dataStore.edit { prefs -> writeKeyCodes(prefs, Keys.SYSTEM_BACK_COMBO, keyCodes) }
    }

    private fun writeKeyCodes(
        prefs: MutablePreferences,
        key: Preferences.Key<String>,
        keyCodes: List<Int>?
    ) {
        if (keyCodes.isNullOrEmpty()) prefs.remove(key) else prefs[key] = keyCodes.joinToString(",")
    }

    private fun parseKeyCodes(raw: String?): List<Int>? =
        raw?.split(',')?.mapNotNull { it.trim().toIntOrNull() }?.takeIf { it.isNotEmpty() }

    suspend fun setNavRingRoutes(routes: List<String>?) {
        dataStore.edit { prefs ->
            if (routes == null) {
                prefs.remove(Keys.NAV_RING_ROUTES)
            } else {
                prefs[Keys.NAV_RING_ROUTES] = routes.joinToString(",")
            }
        }
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
                    value.startsWith("romm:") -> SoundConfig(
                        presetName = SoundConfig.ROMM_SOURCE,
                        customFilePath = value.removePrefix("romm:")
                    )
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
                config.customFilePath != null && config.presetName == SoundConfig.ROMM_SOURCE ->
                    "romm:${config.customFilePath}"
                config.customFilePath != null -> "custom:${config.customFilePath}"
                config.presetName != null -> config.presetName
                else -> return@joinToString ""
            }
            "${type.name}=$value"
        }
    }
}
