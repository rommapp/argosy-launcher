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

    fun clearSession() {
        prefs.edit()
            .putBoolean(KEY_HAS_SESSION, false)
            .putLong(KEY_GAME_ID, -1)
            .remove(KEY_CHANNEL_NAME)
            .remove(KEY_EMULATOR_PACKAGE)
            .putBoolean(KEY_IS_HARDCORE, false)
            .putLong(KEY_SESSION_START_TIME, 0)
            .putString(KEY_COMPANION_SCREEN, "HOME")
            .putLong(KEY_DETAIL_GAME_ID, -1)
            .putString(KEY_ACTIVE_MODAL, "NONE")
            .putLong(KEY_MODAL_GAME_ID, -1)
            .putBoolean(KEY_SCREENSHOT_VIEWER_OPEN, false)
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

    fun setHomeApps(apps: Set<String>) {
        prefs.edit().putStringSet(KEY_HOME_APPS, apps).apply()
    }

    fun getHomeApps(): Set<String> = prefs.getStringSet(KEY_HOME_APPS, emptySet()) ?: emptySet()

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

    fun setDualScreenEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DUAL_SCREEN_ENABLED, enabled).commit()
    }

    fun isDualScreenEnabled(): Boolean = prefs.getBoolean(KEY_DUAL_SCREEN_ENABLED, false)

    fun setSaveSyncEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SAVE_SYNC_ENABLED, enabled).apply()
    }

    fun isSaveSyncEnabled(): Boolean = prefs.getBoolean(KEY_SAVE_SYNC_ENABLED, false)

    fun setDisplayRoleOverride(override: String) {
        prefs.edit().putString(KEY_DISPLAY_ROLE_OVERRIDE, override).commit()
    }

    fun getDisplayRoleOverride(): String =
        prefs.getString(KEY_DISPLAY_ROLE_OVERRIDE, "AUTO") ?: "AUTO"

    fun setDualScreenInputFocus(focus: String) {
        prefs.edit().putString(KEY_DUAL_SCREEN_INPUT_FOCUS, focus).apply()
    }

    fun getDualScreenInputFocus(): String =
        prefs.getString(KEY_DUAL_SCREEN_INPUT_FOCUS, "AUTO") ?: "AUTO"

    fun setRolesSwapped(swapped: Boolean) {
        prefs.edit().putBoolean(KEY_ROLES_SWAPPED, swapped).commit()
    }

    fun isRolesSwapped(): Boolean = prefs.getBoolean(KEY_ROLES_SWAPPED, false)

    fun setCompanionScreen(screen: String, detailGameId: Long = -1) {
        prefs.edit()
            .putString(KEY_COMPANION_SCREEN, screen)
            .putLong(KEY_DETAIL_GAME_ID, detailGameId)
            .apply()
    }

    fun getCompanionScreen(): String =
        prefs.getString(KEY_COMPANION_SCREEN, "HOME") ?: "HOME"

    fun getDetailGameId(): Long = prefs.getLong(KEY_DETAIL_GAME_ID, -1)

    fun setActiveModal(modal: String, modalGameId: Long = -1) {
        prefs.edit()
            .putString(KEY_ACTIVE_MODAL, modal)
            .putLong(KEY_MODAL_GAME_ID, modalGameId)
            .apply()
    }

    fun getActiveModal(): String =
        prefs.getString(KEY_ACTIVE_MODAL, "NONE") ?: "NONE"

    fun getModalGameId(): Long = prefs.getLong(KEY_MODAL_GAME_ID, -1)

    fun clearActiveModal() {
        prefs.edit()
            .putString(KEY_ACTIVE_MODAL, "NONE")
            .putLong(KEY_MODAL_GAME_ID, -1)
            .apply()
    }

    fun setDetailTab(tab: String) {
        prefs.edit().putString(KEY_DETAIL_TAB, tab).apply()
    }

    fun getDetailTab(): String =
        prefs.getString(KEY_DETAIL_TAB, "") ?: ""

    fun setScreenshotViewerState(isOpen: Boolean, index: Int = -1) {
        prefs.edit()
            .putBoolean(KEY_SCREENSHOT_VIEWER_OPEN, isOpen)
            .putInt(KEY_SCREENSHOT_VIEWER_INDEX, index)
            .apply()
    }

    fun isScreenshotViewerOpen(): Boolean =
        prefs.getBoolean(KEY_SCREENSHOT_VIEWER_OPEN, false)

    fun getScreenshotViewerIndex(): Int =
        prefs.getInt(KEY_SCREENSHOT_VIEWER_INDEX, -1)

    fun setCarouselPosition(sectionIndex: Int, selectedIndex: Int) {
        prefs.edit()
            .putInt(KEY_CAROUSEL_SECTION_INDEX, sectionIndex)
            .putInt(KEY_CAROUSEL_SELECTED_INDEX, selectedIndex)
            .apply()
    }

    fun getCarouselSectionIndex(): Int =
        prefs.getInt(KEY_CAROUSEL_SECTION_INDEX, 0)

    fun getCarouselSelectedIndex(): Int =
        prefs.getInt(KEY_CAROUSEL_SELECTED_INDEX, 0)

    /**
     * Stable identity of the lower home carousel: which section (by kind and, for
     * a platform, its id) and which game (by id) were selected, plus the active
     * home filter context. Restored by identity so a shifting dynamic section list
     * does not strand the upper and lower screens on different games. Legacy index
     * fields remain populated for graceful fallback before any id-context exists.
     */
    data class CarouselNavContext(
        val hasContext: Boolean,
        val sectionKind: String,
        val platformId: Long,
        val gameId: Long,
        val legacySectionIndex: Int,
        val legacySelectedIndex: Int,
        val filterSource: String,
        val filterPlatformId: Long,
        val filterSearch: String,
        val sortOption: String,
        val sortDescending: Boolean,
        val genres: Set<String>,
        val players: Set<String>,
        val franchises: Set<String>
    )

    fun setCarouselNavContext(ctx: CarouselNavContext) {
        prefs.edit()
            .putBoolean(KEY_CAROUSEL_HAS_CONTEXT, ctx.hasContext)
            .putString(KEY_CAROUSEL_SECTION_KIND, ctx.sectionKind)
            .putLong(KEY_CAROUSEL_PLATFORM_ID, ctx.platformId)
            .putLong(KEY_CAROUSEL_GAME_ID, ctx.gameId)
            .putInt(KEY_CAROUSEL_SECTION_INDEX, ctx.legacySectionIndex)
            .putInt(KEY_CAROUSEL_SELECTED_INDEX, ctx.legacySelectedIndex)
            .putString(KEY_CAROUSEL_FILTER_SOURCE, ctx.filterSource)
            .putLong(KEY_CAROUSEL_FILTER_PLATFORM_ID, ctx.filterPlatformId)
            .putString(KEY_CAROUSEL_FILTER_SEARCH, ctx.filterSearch)
            .putString(KEY_CAROUSEL_SORT_OPTION, ctx.sortOption)
            .putBoolean(KEY_CAROUSEL_SORT_DESC, ctx.sortDescending)
            .putStringSet(KEY_CAROUSEL_GENRES, ctx.genres)
            .putStringSet(KEY_CAROUSEL_PLAYERS, ctx.players)
            .putStringSet(KEY_CAROUSEL_FRANCHISES, ctx.franchises)
            .apply()
    }

    fun getCarouselNavContext(): CarouselNavContext =
        CarouselNavContext(
            hasContext = prefs.getBoolean(KEY_CAROUSEL_HAS_CONTEXT, false),
            sectionKind = prefs.getString(KEY_CAROUSEL_SECTION_KIND, "") ?: "",
            platformId = prefs.getLong(KEY_CAROUSEL_PLATFORM_ID, -1),
            gameId = prefs.getLong(KEY_CAROUSEL_GAME_ID, -1),
            legacySectionIndex = prefs.getInt(KEY_CAROUSEL_SECTION_INDEX, 0),
            legacySelectedIndex = prefs.getInt(KEY_CAROUSEL_SELECTED_INDEX, 0),
            filterSource = prefs.getString(KEY_CAROUSEL_FILTER_SOURCE, "ALL") ?: "ALL",
            filterPlatformId = prefs.getLong(KEY_CAROUSEL_FILTER_PLATFORM_ID, -1),
            filterSearch = prefs.getString(KEY_CAROUSEL_FILTER_SEARCH, "") ?: "",
            sortOption = prefs.getString(KEY_CAROUSEL_SORT_OPTION, "") ?: "",
            sortDescending = prefs.getBoolean(KEY_CAROUSEL_SORT_DESC, false),
            genres = prefs.getStringSet(KEY_CAROUSEL_GENRES, emptySet()) ?: emptySet(),
            players = prefs.getStringSet(KEY_CAROUSEL_PLAYERS, emptySet()) ?: emptySet(),
            franchises = prefs.getStringSet(KEY_CAROUSEL_FRANCHISES, emptySet()) ?: emptySet()
        )

    fun setWizardActive(active: Boolean) {
        prefs.edit().putBoolean(KEY_WIZARD_ACTIVE, active).apply()
    }

    fun isWizardActive(): Boolean = prefs.getBoolean(KEY_WIZARD_ACTIVE, false)

    fun setFirstRunComplete(complete: Boolean) {
        prefs.edit().putBoolean(KEY_FIRST_RUN_COMPLETE, complete).apply()
    }

    fun isFirstRunComplete(): Boolean = prefs.getBoolean(KEY_FIRST_RUN_COMPLETE, false)

    companion object {
        private const val PREFS_NAME = "argosy_session_state"
        private const val KEY_HAS_SESSION = "has_session"
        private const val KEY_GAME_ID = "game_id"
        private const val KEY_CHANNEL_NAME = "channel_name"
        private const val KEY_IS_HARDCORE = "is_hardcore"
        private const val KEY_SAVE_DIRTY = "save_dirty"
        private const val KEY_HOME_APPS = "home_apps"
        private const val KEY_ARGOSY_FOREGROUND = "argosy_foreground"
        private const val KEY_FOREIGN_APP_ON_SECONDARY = "foreign_app_on_secondary"
        private const val KEY_PRIMARY_COLOR = "primary_color"
        private const val KEY_SWAP_AB = "swap_ab"
        private const val KEY_SWAP_XY = "swap_xy"
        private const val KEY_SWAP_START_SELECT = "swap_start_select"
        private const val KEY_DISPLAY_ROLE_OVERRIDE = "display_role_override"
        private const val KEY_DUAL_SCREEN_INPUT_FOCUS = "dual_screen_input_focus"
        private const val KEY_ROLES_SWAPPED = "roles_swapped"
        private const val KEY_COMPANION_SCREEN = "companion_screen"
        private const val KEY_DETAIL_GAME_ID = "detail_game_id"
        private const val KEY_CAROUSEL_SECTION_INDEX = "carousel_section_index"
        private const val KEY_CAROUSEL_SELECTED_INDEX = "carousel_selected_index"
        private const val KEY_CAROUSEL_HAS_CONTEXT = "carousel_has_context"
        private const val KEY_CAROUSEL_SECTION_KIND = "carousel_section_kind"
        private const val KEY_CAROUSEL_PLATFORM_ID = "carousel_platform_id"
        private const val KEY_CAROUSEL_GAME_ID = "carousel_game_id"
        private const val KEY_CAROUSEL_FILTER_SOURCE = "carousel_filter_source"
        private const val KEY_CAROUSEL_FILTER_PLATFORM_ID = "carousel_filter_platform_id"
        private const val KEY_CAROUSEL_FILTER_SEARCH = "carousel_filter_search"
        private const val KEY_CAROUSEL_SORT_OPTION = "carousel_sort_option"
        private const val KEY_CAROUSEL_SORT_DESC = "carousel_sort_desc"
        private const val KEY_CAROUSEL_GENRES = "carousel_filter_genres"
        private const val KEY_CAROUSEL_PLAYERS = "carousel_filter_players"
        private const val KEY_CAROUSEL_FRANCHISES = "carousel_filter_franchises"
        private const val KEY_SESSION_START_TIME = "session_start_time"
        private const val KEY_EMULATOR_PACKAGE = "emulator_package"
        private const val KEY_WIZARD_ACTIVE = "wizard_active"
        private const val KEY_FIRST_RUN_COMPLETE = "first_run_complete"
        private const val KEY_ACTIVE_MODAL = "active_modal"
        private const val KEY_MODAL_GAME_ID = "modal_game_id"
        private const val KEY_DETAIL_TAB = "detail_tab"
        private const val KEY_SCREENSHOT_VIEWER_OPEN = "screenshot_viewer_open"
        private const val KEY_SCREENSHOT_VIEWER_INDEX = "screenshot_viewer_index"
        private const val KEY_DUAL_SCREEN_ENABLED = "dual_screen_enabled"
        private const val KEY_SAVE_SYNC_ENABLED = "save_sync_enabled"
    }
}
