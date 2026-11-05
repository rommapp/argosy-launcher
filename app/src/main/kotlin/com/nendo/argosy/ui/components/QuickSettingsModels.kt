package com.nendo.argosy.ui.components

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.ui.graphics.vector.ImageVector
import com.nendo.argosy.R
import com.nendo.argosy.data.preferences.ControlsPreferences
import com.nendo.argosy.data.preferences.ThemeMode
import com.nendo.argosy.hardware.FanController
import com.nendo.argosy.hardware.PerformanceMode

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
fun ThemeMode.quickLabelRes(): Int = when (this) {
    ThemeMode.LIGHT -> R.string.ui_quick_settings_theme_light
    ThemeMode.DARK -> R.string.ui_quick_settings_theme_dark
    ThemeMode.SYSTEM -> R.string.ui_quick_settings_theme_system
}

data class QuickSettingsState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
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
    val systemVolume: Float = 1f,
    val screenBrightness: Float = 0.5f,
    val secondaryBrightness: Float? = null,
    val isDualScreenActive: Boolean = false,
    val isRolesSwapped: Boolean = false,
    val hudEnabled: Boolean = false,
    val isSocialLinked: Boolean = false,
    val isSocialConnected: Boolean = false,
    val quayPassEnabled: Boolean = false,
    val hardwareStats: HardwareStatsUi? = null
) {
    val deviceControlsLocked: Boolean get() = deviceSettingsSupported && !deviceSettingsEnabled
}

enum class QuickSettingsPage(val icon: ImageVector, @StringRes val titleRes: Int) {
    FRIENDS(Icons.Default.Group, R.string.ui_quick_settings_page_friends),
    QUICK(Icons.Default.Tune, R.string.ui_quick_settings_title),
    PERFORMANCE(Icons.Default.Speed, R.string.ui_quick_settings_page_performance),
    CONNECTIONS(Icons.Default.Wifi, R.string.ui_quick_settings_page_connections),
    MUSIC(Icons.Default.MusicNote, R.string.ui_quick_settings_page_music)
}

enum class QuickSettingsGroup(val page: QuickSettingsPage, @StringRes val titleRes: Int) {
    DISPLAY(QuickSettingsPage.QUICK, R.string.ui_quick_settings_group_display),
    SOUND(QuickSettingsPage.QUICK, R.string.ui_quick_settings_group_audio),
    CONTROLS(QuickSettingsPage.QUICK, R.string.ui_quick_settings_group_controls),
    PERFORMANCE(QuickSettingsPage.PERFORMANCE, R.string.ui_quick_settings_group_performance),
    FAN(QuickSettingsPage.PERFORMANCE, R.string.ui_quick_settings_group_fan),
    OVERLAY(QuickSettingsPage.PERFORMANCE, R.string.ui_quick_settings_group_overlay)
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
        visibleWhen = { it.deviceControlsLocked }
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

    companion object {
        private val DisplayHeader = Header(QuickSettingsGroup.DISPLAY)
        private val SoundHeader = Header(QuickSettingsGroup.SOUND)
        private val ControlsHeader = Header(QuickSettingsGroup.CONTROLS)
        private val PerformanceHeader = Header(QuickSettingsGroup.PERFORMANCE)
        private val FanHeader = Header(QuickSettingsGroup.FAN)
        private val OverlayHeader = Header(QuickSettingsGroup.OVERLAY)

        val ALL: List<QuickSettingsItem>
            get() = listOf(
                FriendsPage,
                DisplayHeader, Theme, Accent, ScreenBrightness, SecondScreenBrightness, SwapDisplays,
                SoundHeader, SystemVolume, UISounds,
                ControlsHeader, Haptic, SwapAB, SwapXY, SwapStartSelect,
                HardwareStats,
                DeviceAccess,
                PerformanceHeader, Performance, Refresh,
                FanHeader, Fan, FanSpeed,
                OverlayHeader, HudOverlay,
                ConnectionsPage,
                MusicPlayer
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
    QuickSettingsPage.entries.filter { quickSettingsFocusableItems(it, state).isNotEmpty() }

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
