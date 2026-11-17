package com.nendo.argosy.ui.components

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.ui.graphics.vector.ImageVector
import com.nendo.argosy.R
import com.nendo.argosy.data.preferences.ControlsPreferences
import com.nendo.argosy.data.preferences.ScreenOrientationMode
import com.nendo.argosy.data.preferences.ThemeMode
import com.nendo.argosy.hardware.FanController
import com.nendo.argosy.hardware.PerformanceMode
import com.nendo.argosy.hardware.PowerRoute
import com.nendo.argosy.ui.components.activity.QuickActivityState
import com.nendo.argosy.ui.components.power.QuickOpenGame

enum class FanMode(val value: Int, @StringRes val labelRes: Int) {
    QUIET(1, R.string.ui_quick_settings_fan_quiet),
    SMART(4, R.string.ui_quick_settings_fan_smart),
    SPORT(5, R.string.ui_quick_settings_fan_sport),
    CUSTOM(6, R.string.ui_quick_settings_fan_custom);

    companion object {
        fun fromValue(value: Int) = entries.find { it.value == value } ?: SMART
    }
}

@get:StringRes
val PerformanceMode.labelRes: Int
    get() = when (this) {
        PerformanceMode.ECO -> R.string.ui_quick_settings_performance_eco
        PerformanceMode.BALANCED -> R.string.ui_quick_settings_performance_balanced
        PerformanceMode.STREAMING -> R.string.ui_quick_settings_performance_streaming
        PerformanceMode.GAMING -> R.string.ui_quick_settings_performance_gaming
        PerformanceMode.STANDARD -> R.string.ui_quick_settings_performance_standard
        PerformanceMode.HIGH -> R.string.ui_quick_settings_performance_high
        PerformanceMode.MAX -> R.string.ui_quick_settings_performance_max
    }

val QUICK_THEME_ORDER: List<ThemeMode> = listOf(ThemeMode.LIGHT, ThemeMode.DARK, ThemeMode.SYSTEM)

@StringRes
fun ScreenOrientationMode.quickLabelRes(): Int = when (this) {
    ScreenOrientationMode.AUTO -> R.string.ui_quick_settings_orientation_auto
    ScreenOrientationMode.LANDSCAPE -> R.string.ui_quick_settings_orientation_landscape
    ScreenOrientationMode.PORTRAIT -> R.string.ui_quick_settings_orientation_portrait
}

@StringRes
fun ThemeMode.quickLabelRes(): Int = when (this) {
    ThemeMode.LIGHT -> R.string.ui_quick_settings_theme_light
    ThemeMode.DARK -> R.string.ui_quick_settings_theme_dark
    ThemeMode.SYSTEM -> R.string.ui_quick_settings_theme_system
}

data class QuickSettingsState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val screenOrientation: ScreenOrientationMode = ScreenOrientationMode.AUTO,
    val soundEnabled: Boolean = false,
    val hapticEnabled: Boolean = true,
    val vibrationStrength: Float = ControlsPreferences.DEFAULT_HAPTIC_STRENGTH,
    val primaryColor: Int? = null,
    val swapAB: Boolean = false,
    val swapXY: Boolean = false,
    val swapStartSelect: Boolean = false,
    val fanMode: FanMode = FanMode.SMART,
    val fanSpeed: Int = FanController.SPORT_DUTY,
    val fanSupported: Boolean = false,
    val performanceModes: List<PerformanceMode> = emptyList(),
    val performanceMode: PerformanceMode? = null,
    val refreshRates: List<Int?> = emptyList(),
    val refreshRateHz: Int? = null,
    val deviceSettingsSupported: Boolean = false,
    val deviceSettingsEnabled: Boolean = false,
    val deviceAccessNeedsRoot: Boolean = false,
    val systemVolume: Float = 1f,
    val screenBrightness: Float = 0.5f,
    val secondaryBrightness: Float? = null,
    val isDualScreenActive: Boolean = false,
    val isRolesSwapped: Boolean = false,
    val hudEnabled: Boolean = false,
    val isSocialLinked: Boolean = false,
    val isSocialConnected: Boolean = false,
    val quayPassEnabled: Boolean = false,
    val hardwareStats: HardwareStatsUi? = null,
    val powerRoute: PowerRoute = PowerRoute.NONE,
    val powerSleepAvailable: Boolean = false,
    val openGames: List<QuickOpenGame> = emptyList(),
    val activity: QuickActivityState = QuickActivityState()
) {
    val deviceControlsLocked: Boolean get() = deviceSettingsSupported && !deviceSettingsEnabled
    val activityIdle: Boolean get() = activity.downloads.isEmpty() && activity.sync == null
}

enum class QuickSettingsPage(val icon: ImageVector, @StringRes val titleRes: Int) {
    FRIENDS(Icons.Default.Group, R.string.ui_quick_settings_page_friends),
    QUICK(Icons.Default.Tune, R.string.ui_quick_settings_title),
    ACTIVITY(Icons.Default.History, R.string.ui_quick_settings_page_activity),
    PERFORMANCE(Icons.Default.Speed, R.string.ui_quick_settings_page_performance),
    CONNECTIONS(Icons.Default.Wifi, R.string.ui_quick_settings_page_connections),
    MUSIC(Icons.Default.MusicNote, R.string.ui_quick_settings_page_music),
    POWER(Icons.Default.PowerSettingsNew, R.string.ui_quick_settings_page_power)
}

enum class QuickSettingsGroup(val page: QuickSettingsPage, @StringRes val titleRes: Int) {
    DISPLAY(QuickSettingsPage.QUICK, R.string.ui_quick_settings_group_display),
    SOUND(QuickSettingsPage.QUICK, R.string.ui_quick_settings_group_audio),
    CONTROLS(QuickSettingsPage.QUICK, R.string.ui_quick_settings_group_controls),
    ACTIVITY_NOW(QuickSettingsPage.ACTIVITY, R.string.ui_quick_settings_group_activity_now),
    ACTIVITY_RECENT(QuickSettingsPage.ACTIVITY, R.string.ui_quick_settings_group_activity_recent),
    PERFORMANCE(QuickSettingsPage.PERFORMANCE, R.string.ui_quick_settings_group_performance),
    FAN(QuickSettingsPage.PERFORMANCE, R.string.ui_quick_settings_group_fan),
    OVERLAY(QuickSettingsPage.PERFORMANCE, R.string.ui_quick_settings_group_overlay),
    POWER_ACTIONS(QuickSettingsPage.POWER, R.string.ui_quick_settings_group_power_actions),
    OPEN_GAMES(QuickSettingsPage.POWER, R.string.ui_quick_settings_group_open_games)
}

sealed class QuickSettingsItem(
    val key: String,
    val page: QuickSettingsPage,
    val group: QuickSettingsGroup? = null,
    val visibleWhen: (QuickSettingsState) -> Boolean = { true },
    private val needsDeviceAccess: Boolean = false
) {
    open fun isFocusable(state: QuickSettingsState): Boolean =
        !(needsDeviceAccess && state.deviceControlsLocked)

    fun isLocked(state: QuickSettingsState): Boolean = needsDeviceAccess && state.deviceControlsLocked

    class Header(val headerGroup: QuickSettingsGroup) :
        QuickSettingsItem("header_${headerGroup.name}", headerGroup.page, headerGroup) {
        override fun isFocusable(state: QuickSettingsState): Boolean = false
    }

    data object Theme : QuickSettingsItem("theme", QuickSettingsPage.QUICK, QuickSettingsGroup.DISPLAY)
    data object Accent : QuickSettingsItem("accent", QuickSettingsPage.QUICK, QuickSettingsGroup.DISPLAY)
    data object ScreenBrightness : QuickSettingsItem(
        "screenBrightness", QuickSettingsPage.QUICK, QuickSettingsGroup.DISPLAY
    )
    data object SecondScreenBrightness : QuickSettingsItem(
        "secondScreenBrightness", QuickSettingsPage.QUICK, QuickSettingsGroup.DISPLAY,
        visibleWhen = { it.isDualScreenActive && it.secondaryBrightness != null }
    )
    data object SwapDisplays : QuickSettingsItem(
        "swapDisplays", QuickSettingsPage.QUICK, QuickSettingsGroup.DISPLAY,
        visibleWhen = { it.isDualScreenActive }
    )
    data object ScreenOrientation : QuickSettingsItem(
        "screenOrientation", QuickSettingsPage.QUICK, QuickSettingsGroup.DISPLAY
    )
    data object SystemVolume : QuickSettingsItem("systemVolume", QuickSettingsPage.QUICK, QuickSettingsGroup.SOUND)
    data object UISounds : QuickSettingsItem("uiSounds", QuickSettingsPage.QUICK, QuickSettingsGroup.SOUND)
    data object Haptic : QuickSettingsItem("haptic", QuickSettingsPage.QUICK, QuickSettingsGroup.CONTROLS)
    data object SwapAB : QuickSettingsItem("swapAB", QuickSettingsPage.QUICK, QuickSettingsGroup.CONTROLS)
    data object SwapXY : QuickSettingsItem("swapXY", QuickSettingsPage.QUICK, QuickSettingsGroup.CONTROLS)
    data object SwapStartSelect : QuickSettingsItem("swapStartSelect", QuickSettingsPage.QUICK, QuickSettingsGroup.CONTROLS)

    data object HardwareStats : QuickSettingsItem(
        "hardwareStats", QuickSettingsPage.PERFORMANCE,
        visibleWhen = { it.hardwareStats != null }
    ) {
        override fun isFocusable(state: QuickSettingsState): Boolean = false
    }

    data object DeviceAccess : QuickSettingsItem(
        "deviceAccess", QuickSettingsPage.PERFORMANCE,
        visibleWhen = { it.deviceControlsLocked && !it.deviceAccessNeedsRoot }
    )
    data object RootAccess : QuickSettingsItem(
        "rootAccess", QuickSettingsPage.PERFORMANCE,
        visibleWhen = { it.deviceControlsLocked && it.deviceAccessNeedsRoot }
    )
    data object Performance : QuickSettingsItem(
        "performance", QuickSettingsPage.PERFORMANCE, QuickSettingsGroup.PERFORMANCE,
        visibleWhen = { it.deviceSettingsSupported },
        needsDeviceAccess = true
    )
    data object Refresh : QuickSettingsItem(
        "refreshRate", QuickSettingsPage.PERFORMANCE, QuickSettingsGroup.PERFORMANCE,
        visibleWhen = { it.deviceSettingsSupported && it.refreshRates.isNotEmpty() },
        needsDeviceAccess = true
    )
    data object Fan : QuickSettingsItem(
        "fan", QuickSettingsPage.PERFORMANCE, QuickSettingsGroup.FAN,
        visibleWhen = { it.deviceSettingsSupported && it.fanSupported },
        needsDeviceAccess = true
    )
    data object FanSpeed : QuickSettingsItem(
        "fanSpeed", QuickSettingsPage.PERFORMANCE, QuickSettingsGroup.FAN,
        visibleWhen = { it.deviceSettingsSupported && it.fanSupported && it.fanMode == FanMode.CUSTOM },
        needsDeviceAccess = true
    )
    data object HudOverlay : QuickSettingsItem(
        "hudOverlay", QuickSettingsPage.PERFORMANCE, QuickSettingsGroup.OVERLAY,
        visibleWhen = { it.deviceSettingsSupported }
    )

    data object FriendsPage : QuickSettingsItem(
        "friendsPage", QuickSettingsPage.FRIENDS,
        visibleWhen = { it.isSocialLinked || it.isSocialConnected }
    )

    data object ConnectionsPage : QuickSettingsItem("connectionsPage", QuickSettingsPage.CONNECTIONS)

    data object MusicPlayer : QuickSettingsItem("musicPlayer", QuickSettingsPage.MUSIC)

    data object ActivityDownloads : QuickSettingsItem(
        "activityDownloads", QuickSettingsPage.ACTIVITY, QuickSettingsGroup.ACTIVITY_NOW,
        visibleWhen = { it.activity.downloads.isNotEmpty() }
    )
    data object ActivitySync : QuickSettingsItem(
        "activitySync", QuickSettingsPage.ACTIVITY, QuickSettingsGroup.ACTIVITY_NOW,
        visibleWhen = { it.activity.sync != null }
    )
    data object ActivityIdle : QuickSettingsItem(
        "activityIdle", QuickSettingsPage.ACTIVITY, QuickSettingsGroup.ACTIVITY_NOW,
        visibleWhen = { it.activityIdle }
    ) {
        override fun isFocusable(state: QuickSettingsState): Boolean = false
    }
    data object ActivityRecent : QuickSettingsItem(
        "activityRecent", QuickSettingsPage.ACTIVITY, QuickSettingsGroup.ACTIVITY_RECENT
    ) {
        override fun isFocusable(state: QuickSettingsState): Boolean = false
    }

    data object PowerAccess : QuickSettingsItem(
        "powerAccess", QuickSettingsPage.POWER,
        visibleWhen = { it.powerRoute == PowerRoute.NONE }
    )
    data object PowerSleep : QuickSettingsItem(
        "powerSleep", QuickSettingsPage.POWER, QuickSettingsGroup.POWER_ACTIONS,
        visibleWhen = { it.powerSleepAvailable }
    )
    data object PowerRestart : QuickSettingsItem(
        "powerRestart", QuickSettingsPage.POWER, QuickSettingsGroup.POWER_ACTIONS,
        visibleWhen = { it.powerRoute == PowerRoute.ROOT }
    )
    data object PowerOff : QuickSettingsItem(
        "powerOff", QuickSettingsPage.POWER, QuickSettingsGroup.POWER_ACTIONS,
        visibleWhen = { it.powerRoute == PowerRoute.ROOT }
    )
    data object PowerMenu : QuickSettingsItem(
        "powerMenu", QuickSettingsPage.POWER, QuickSettingsGroup.POWER_ACTIONS,
        visibleWhen = { it.powerRoute == PowerRoute.ACCESSIBILITY }
    )
    data object OpenGames : QuickSettingsItem("openGames", QuickSettingsPage.POWER, QuickSettingsGroup.OPEN_GAMES) {
        override fun isFocusable(state: QuickSettingsState): Boolean = false
    }

    companion object {
        private val DisplayHeader = Header(QuickSettingsGroup.DISPLAY)
        private val SoundHeader = Header(QuickSettingsGroup.SOUND)
        private val ControlsHeader = Header(QuickSettingsGroup.CONTROLS)
        private val ActivityNowHeader = Header(QuickSettingsGroup.ACTIVITY_NOW)
        private val ActivityRecentHeader = Header(QuickSettingsGroup.ACTIVITY_RECENT)
        private val PerformanceHeader = Header(QuickSettingsGroup.PERFORMANCE)
        private val FanHeader = Header(QuickSettingsGroup.FAN)
        private val OverlayHeader = Header(QuickSettingsGroup.OVERLAY)
        private val PowerActionsHeader = Header(QuickSettingsGroup.POWER_ACTIONS)
        private val OpenGamesHeader = Header(QuickSettingsGroup.OPEN_GAMES)

        val ALL: List<QuickSettingsItem>
            get() = listOf(
                FriendsPage,
                DisplayHeader, Theme, Accent, ScreenBrightness, SecondScreenBrightness, SwapDisplays, ScreenOrientation,
                SoundHeader, SystemVolume, UISounds,
                ControlsHeader, Haptic, SwapAB, SwapXY, SwapStartSelect,
                ActivityNowHeader, ActivityDownloads, ActivitySync, ActivityIdle,
                ActivityRecentHeader, ActivityRecent,
                HardwareStats,
                DeviceAccess,
                RootAccess,
                PerformanceHeader, Performance, Refresh,
                FanHeader, Fan, FanSpeed,
                OverlayHeader, HudOverlay,
                ConnectionsPage,
                MusicPlayer,
                PowerAccess,
                PowerActionsHeader, PowerSleep, PowerRestart, PowerOff, PowerMenu,
                OpenGamesHeader, OpenGames
            )
    }
}

fun quickSettingsVisibleItems(page: QuickSettingsPage, state: QuickSettingsState): List<QuickSettingsItem> {
    val shown = QuickSettingsItem.ALL.filter { it.page == page && it.visibleWhen(state) }
    val groupsWithRows = shown.filterNot { it is QuickSettingsItem.Header }.mapNotNull { it.group }.toSet()
    return shown.filter { it !is QuickSettingsItem.Header || it.group in groupsWithRows }
}

fun quickSettingsFocusableItems(page: QuickSettingsPage, state: QuickSettingsState): List<QuickSettingsItem> =
    quickSettingsVisibleItems(page, state).filter { it.isFocusable(state) }

fun quickSettingsVisiblePages(state: QuickSettingsState): List<QuickSettingsPage> =
    QuickSettingsPage.entries.filter {
        it == QuickSettingsPage.ACTIVITY || quickSettingsFocusableItems(it, state).isNotEmpty()
    }

fun quickSettingsEffectivePage(page: QuickSettingsPage, state: QuickSettingsState): QuickSettingsPage {
    val pages = quickSettingsVisiblePages(state)
    return when {
        page in pages -> page
        QuickSettingsPage.QUICK in pages -> QuickSettingsPage.QUICK
        else -> pages.firstOrNull() ?: QuickSettingsPage.QUICK
    }
}

fun quickSettingsMaxFocusIndex(page: QuickSettingsPage, state: QuickSettingsState): Int =
    (quickSettingsFocusableItems(page, state).size - 1).coerceAtLeast(0)

fun quickSettingsItemAtFocusIndex(
    page: QuickSettingsPage,
    index: Int,
    state: QuickSettingsState
): QuickSettingsItem? = quickSettingsFocusableItems(page, state).getOrNull(index)

fun quickSettingsFocusIndexOf(item: QuickSettingsItem, state: QuickSettingsState): Int =
    quickSettingsFocusableItems(item.page, state).indexOf(item)

fun quickSettingsFocusToListIndex(page: QuickSettingsPage, focusIndex: Int, state: QuickSettingsState): Int {
    val item = quickSettingsItemAtFocusIndex(page, focusIndex, state) ?: return focusIndex
    return quickSettingsVisibleItems(page, state).indexOf(item)
}

fun quickSettingsSections(page: QuickSettingsPage, state: QuickSettingsState): List<ListSection> {
    val visible = quickSettingsVisibleItems(page, state)
    val focusable = visible.filter { it.isFocusable(state) }
    return visible.map { it.group?.name ?: it.key }.distinct().mapNotNull { section ->
        val members = visible.filter { (it.group?.name ?: it.key) == section }
        val focusableMembers = members.filter { it in focusable }
        if (focusableMembers.isEmpty()) return@mapNotNull null
        ListSection(
            listStartIndex = visible.indexOf(members.first()),
            listEndIndex = visible.indexOf(members.last()),
            focusStartIndex = focusable.indexOf(focusableMembers.first()),
            focusEndIndex = focusable.indexOf(focusableMembers.last())
        )
    }
}
