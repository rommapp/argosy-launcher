package com.nendo.argosy.data.preferences

import android.content.Context
import android.content.SharedPreferences

/**
 * Simple SharedPreferences-based store for session state.
 * Safe for cross-process reads (companion process reads, main process writes).
 */
class SessionStateStore(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    fun setActiveSession(gameId: Long, channelName: String?, isHardcore: Boolean) {
        prefs.edit()
            .putLong(KEY_GAME_ID, gameId)
            .putString(KEY_CHANNEL_NAME, channelName)
            .putBoolean(KEY_IS_HARDCORE, isHardcore)
            .putBoolean(KEY_HAS_SESSION, true)
            .apply()
    }

    fun clearSession() {
        prefs.edit()
            .putBoolean(KEY_HAS_SESSION, false)
            .putLong(KEY_GAME_ID, -1)
            .remove(KEY_CHANNEL_NAME)
            .putBoolean(KEY_IS_HARDCORE, false)
            .apply()
    }

    fun hasActiveSession(): Boolean = prefs.getBoolean(KEY_HAS_SESSION, false)

    fun getGameId(): Long = prefs.getLong(KEY_GAME_ID, -1)

    fun getChannelName(): String? = prefs.getString(KEY_CHANNEL_NAME, null)

    fun isHardcore(): Boolean = prefs.getBoolean(KEY_IS_HARDCORE, false)

    fun setSaveDirty(isDirty: Boolean) {
        prefs.edit().putBoolean(KEY_SAVE_DIRTY, isDirty).apply()
    }

    fun isSaveDirty(): Boolean = prefs.getBoolean(KEY_SAVE_DIRTY, false)

    fun setHomeApps(apps: Set<String>) {
        prefs.edit().putStringSet(KEY_HOME_APPS, apps).apply()
    }

    fun getHomeApps(): Set<String> = prefs.getStringSet(KEY_HOME_APPS, emptySet()) ?: emptySet()

    fun setArgosyForeground(isForeground: Boolean) {
        prefs.edit().putBoolean(KEY_ARGOSY_FOREGROUND, isForeground).apply()
    }

    fun isArgosyForeground(): Boolean = prefs.getBoolean(KEY_ARGOSY_FOREGROUND, false)

    fun setPrimaryColor(color: Int?) {
        if (color != null) {
            prefs.edit().putInt(KEY_PRIMARY_COLOR, color).apply()
        } else {
            prefs.edit().remove(KEY_PRIMARY_COLOR).apply()
        }
    }

    fun getPrimaryColor(): Int? {
        return if (prefs.contains(KEY_PRIMARY_COLOR)) {
            prefs.getInt(KEY_PRIMARY_COLOR, 0)
        } else {
            null
        }
    }

    fun setInputSwapPreferences(swapAB: Boolean, swapXY: Boolean, swapStartSelect: Boolean) {
        prefs.edit()
            .putBoolean(KEY_SWAP_AB, swapAB)
            .putBoolean(KEY_SWAP_XY, swapXY)
            .putBoolean(KEY_SWAP_START_SELECT, swapStartSelect)
            .apply()
    }

    fun getSwapAB(): Boolean = prefs.getBoolean(KEY_SWAP_AB, false)
    fun getSwapXY(): Boolean = prefs.getBoolean(KEY_SWAP_XY, false)
    fun getSwapStartSelect(): Boolean = prefs.getBoolean(KEY_SWAP_START_SELECT, false)

    fun setDisplayRoleOverride(override: String) {
        prefs.edit().putString(KEY_DISPLAY_ROLE_OVERRIDE, override).apply()
    }

    fun getDisplayRoleOverride(): String =
        prefs.getString(KEY_DISPLAY_ROLE_OVERRIDE, "AUTO") ?: "AUTO"

    fun setRolesSwapped(swapped: Boolean) {
        prefs.edit().putBoolean(KEY_ROLES_SWAPPED, swapped).apply()
    }

    fun isRolesSwapped(): Boolean = prefs.getBoolean(KEY_ROLES_SWAPPED, false)

    companion object {
        private const val PREFS_NAME = "argosy_session_state"
        private const val KEY_HAS_SESSION = "has_session"
        private const val KEY_GAME_ID = "game_id"
        private const val KEY_CHANNEL_NAME = "channel_name"
        private const val KEY_IS_HARDCORE = "is_hardcore"
        private const val KEY_SAVE_DIRTY = "save_dirty"
        private const val KEY_HOME_APPS = "home_apps"
        private const val KEY_ARGOSY_FOREGROUND = "argosy_foreground"
        private const val KEY_PRIMARY_COLOR = "primary_color"
        private const val KEY_SWAP_AB = "swap_ab"
        private const val KEY_SWAP_XY = "swap_xy"
        private const val KEY_SWAP_START_SELECT = "swap_start_select"
        private const val KEY_DISPLAY_ROLE_OVERRIDE = "display_role_override"
        private const val KEY_ROLES_SWAPPED = "roles_swapped"
    }
}
