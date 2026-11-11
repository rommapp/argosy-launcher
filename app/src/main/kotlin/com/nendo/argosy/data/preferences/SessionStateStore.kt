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

    fun setActiveSession(
        gameId: Long,
        channelName: String?,
        isHardcore: Boolean,
        sessionStartTimeMillis: Long,
        emulatorPackage: String? = null
    ) {
        prefs.edit()
            .putLong(KEY_GAME_ID, gameId)
            .putString(KEY_CHANNEL_NAME, channelName)
            .putBoolean(KEY_IS_HARDCORE, isHardcore)
            .putBoolean(KEY_HAS_SESSION, true)
            .putLong(KEY_SESSION_START_TIME, sessionStartTimeMillis)
            .apply {
                if (emulatorPackage != null) putString(KEY_EMULATOR_PACKAGE, emulatorPackage)
            }
            .apply()
    }

    fun getEmulatorPackage(): String? = prefs.getString(KEY_EMULATOR_PACKAGE, null)

    /**
     * The display the running game occupies, written with commit() because its whole purpose is to
     * outlive the process that set it: every site that lets a game on the other display survive the
     * launcher coming forward reads this, and a launcher that comes back without it cannot tell a
     * game on the other screen from one it just covered.
     */
    fun setEmulatorDisplayId(displayId: Int?) {
        val editor = prefs.edit()
        if (displayId == null) editor.remove(KEY_EMULATOR_DISPLAY_ID)
        else editor.putInt(KEY_EMULATOR_DISPLAY_ID, displayId)
        editor.commit()
    }

    fun getEmulatorDisplayId(): Int? =
        if (prefs.contains(KEY_EMULATOR_DISPLAY_ID)) prefs.getInt(KEY_EMULATOR_DISPLAY_ID, 0) else null

    fun clearSession() {
        prefs.edit()
            .putBoolean(KEY_HAS_SESSION, false)
            .putLong(KEY_GAME_ID, -1)
            .remove(KEY_CHANNEL_NAME)
            .remove(KEY_EMULATOR_PACKAGE)
            .remove(KEY_EMULATOR_DISPLAY_ID)
            .putBoolean(KEY_IS_HARDCORE, false)
            .putLong(KEY_SESSION_START_TIME, 0)
            .apply()
    }

    fun hasActiveSession(): Boolean = prefs.getBoolean(KEY_HAS_SESSION, false)

    fun getGameId(): Long = prefs.getLong(KEY_GAME_ID, -1)

    fun getChannelName(): String? = prefs.getString(KEY_CHANNEL_NAME, null)

    fun isHardcore(): Boolean = prefs.getBoolean(KEY_IS_HARDCORE, false)

    fun getSessionStartTimeMillis(): Long =
        prefs.getLong(KEY_SESSION_START_TIME, 0)

    fun setSaveDirty(isDirty: Boolean) {
        prefs.edit().putBoolean(KEY_SAVE_DIRTY, isDirty).apply()
    }

    fun isSaveDirty(): Boolean = prefs.getBoolean(KEY_SAVE_DIRTY, false)

    fun setArgosyForeground(isForeground: Boolean) {
        prefs.edit().putBoolean(KEY_ARGOSY_FOREGROUND, isForeground).apply()
    }

    fun isArgosyForeground(): Boolean = prefs.getBoolean(KEY_ARGOSY_FOREGROUND, false)

    fun setForeignAppOnSecondary(active: Boolean) {
        prefs.edit().putBoolean(KEY_FOREIGN_APP_ON_SECONDARY, active).commit()
    }

    fun isForeignAppOnSecondary(): Boolean =
        prefs.getBoolean(KEY_FOREIGN_APP_ON_SECONDARY, false)

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

    fun setInputSwapPreferences(
        swapAB: Boolean,
        swapXY: Boolean,
        swapStartSelect: Boolean,
        controllerLayout: String
    ) {
        prefs.edit()
            .putBoolean(KEY_SWAP_AB, swapAB)
            .putBoolean(KEY_SWAP_XY, swapXY)
            .putBoolean(KEY_SWAP_START_SELECT, swapStartSelect)
            .putString(KEY_CONTROLLER_LAYOUT, controllerLayout)
            .apply()
    }

    fun getSwapAB(): Boolean = prefs.getBoolean(KEY_SWAP_AB, false)
    fun getSwapXY(): Boolean = prefs.getBoolean(KEY_SWAP_XY, false)
    fun getSwapStartSelect(): Boolean = prefs.getBoolean(KEY_SWAP_START_SELECT, false)
    fun getControllerLayout(): String = prefs.getString(KEY_CONTROLLER_LAYOUT, "auto") ?: "auto"

    fun setDualScreenEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DUAL_SCREEN_ENABLED, enabled).commit()
    }

    fun isDualScreenEnabled(): Boolean = prefs.getBoolean(KEY_DUAL_SCREEN_ENABLED, false)

    fun setSecondaryDisplayUsable(usable: Boolean) {
        prefs.edit().putBoolean(KEY_SECONDARY_DISPLAY_USABLE, usable).commit()
    }

    fun isSecondaryDisplayUsable(): Boolean =
        prefs.getBoolean(KEY_SECONDARY_DISPLAY_USABLE, true)

    fun setSaveSyncEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SAVE_SYNC_ENABLED, enabled).apply()
    }

    fun isSaveSyncEnabled(): Boolean = prefs.getBoolean(KEY_SAVE_SYNC_ENABLED, false)

    fun setDisplayRoleOverride(override: String) {
        prefs.edit().putString(KEY_DISPLAY_ROLE_OVERRIDE, override).commit()
    }

    fun getDisplayRoleOverride(): String =
        prefs.getString(KEY_DISPLAY_ROLE_OVERRIDE, "AUTO") ?: "AUTO"

    fun setRolesSwapped(swapped: Boolean) {
        prefs.edit().putBoolean(KEY_ROLES_SWAPPED, swapped).commit()
    }

    fun isRolesSwapped(): Boolean = prefs.getBoolean(KEY_ROLES_SWAPPED, false)

    /**
     * The launcher's chosen display language as a BCP 47 tag, or the "system" sentinel. Written
     * with commit() because every Activity and foreground service reads it during attachBaseContext
     * or notification setup, before Hilt injection has run and before DataStore's async flow could
     * possibly have caught up.
     */
    fun setAppLanguage(tag: String) {
        prefs.edit().putString(KEY_APP_LANGUAGE, tag).commit()
    }

    fun getAppLanguage(): String =
        prefs.getString(KEY_APP_LANGUAGE, AppLanguage.SYSTEM.tag) ?: AppLanguage.SYSTEM.tag

    /**
     * A RomM account switch that is under way. Written with commit() rather than apply() because
     * the whole point of the marker is that it outlives the process that set it: an apply() that
     * has not reached disk when the app is killed leaves the disk half torn down with no record
     * that anything was in flight.
     */
    fun setAccountSwitchMarker(fromUserId: Long?, toUserId: Long, startedAtMillis: Long) {
        prefs.edit()
            .putBoolean(KEY_SWITCH_ACTIVE, true)
            .putLong(KEY_SWITCH_FROM_USER, fromUserId ?: NO_USER_ID)
            .putLong(KEY_SWITCH_TO_USER, toUserId)
            .putLong(KEY_SWITCH_STARTED_AT, startedAtMillis)
            .commit()
    }

    fun getAccountSwitchMarker(): AccountSwitchMarker? {
        if (!prefs.getBoolean(KEY_SWITCH_ACTIVE, false)) return null
        val toUserId = prefs.getLong(KEY_SWITCH_TO_USER, NO_USER_ID)
        if (toUserId == NO_USER_ID) return null
        val fromUserId = prefs.getLong(KEY_SWITCH_FROM_USER, NO_USER_ID)
        return AccountSwitchMarker(
            fromUserId = fromUserId.takeIf { it != NO_USER_ID },
            toUserId = toUserId,
            startedAtMillis = prefs.getLong(KEY_SWITCH_STARTED_AT, 0)
        )
    }

    fun isAccountSwitchInProgress(): Boolean = prefs.getBoolean(KEY_SWITCH_ACTIVE, false)

    fun clearAccountSwitchMarker() {
        prefs.edit()
            .putBoolean(KEY_SWITCH_ACTIVE, false)
            .remove(KEY_SWITCH_FROM_USER)
            .remove(KEY_SWITCH_TO_USER)
            .remove(KEY_SWITCH_STARTED_AT)
            .commit()
    }

    data class AccountSwitchMarker(
        val fromUserId: Long?,
        val toUserId: Long,
        val startedAtMillis: Long
    )

    companion object {
        private const val NO_USER_ID = -1L
        private const val PREFS_NAME = "argosy_session_state"
        private const val KEY_HAS_SESSION = "has_session"
        private const val KEY_GAME_ID = "game_id"
        private const val KEY_CHANNEL_NAME = "channel_name"
        private const val KEY_IS_HARDCORE = "is_hardcore"
        private const val KEY_SAVE_DIRTY = "save_dirty"
        private const val KEY_ARGOSY_FOREGROUND = "argosy_foreground"
        private const val KEY_FOREIGN_APP_ON_SECONDARY = "foreign_app_on_secondary"
        private const val KEY_PRIMARY_COLOR = "primary_color"
        private const val KEY_SWAP_AB = "swap_ab"
        private const val KEY_SWAP_XY = "swap_xy"
        private const val KEY_SWAP_START_SELECT = "swap_start_select"
        private const val KEY_CONTROLLER_LAYOUT = "controller_layout"
        private const val KEY_DISPLAY_ROLE_OVERRIDE = "display_role_override"
        private const val KEY_ROLES_SWAPPED = "roles_swapped"
        private const val KEY_SESSION_START_TIME = "session_start_time"
        private const val KEY_EMULATOR_PACKAGE = "emulator_package"
        private const val KEY_EMULATOR_DISPLAY_ID = "emulator_display_id"
        private const val KEY_DUAL_SCREEN_ENABLED = "dual_screen_enabled"
        private const val KEY_SECONDARY_DISPLAY_USABLE = "secondary_display_usable"
        private const val KEY_SAVE_SYNC_ENABLED = "save_sync_enabled"
        private const val KEY_SWITCH_ACTIVE = "account_switch_active"
        private const val KEY_SWITCH_FROM_USER = "account_switch_from_user"
        private const val KEY_SWITCH_TO_USER = "account_switch_to_user"
        private const val KEY_SWITCH_STARTED_AT = "account_switch_started_at"
        private const val KEY_APP_LANGUAGE = "app_language"
    }
}
