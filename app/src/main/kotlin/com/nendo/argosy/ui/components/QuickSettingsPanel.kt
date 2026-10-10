package com.nendo.argosy.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.SettingsBrightness
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Toys
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.outlined.Monitor
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import com.nendo.argosy.R
import com.nendo.argosy.data.preferences.ThemeMode
import com.nendo.argosy.hardware.FanController
import com.nendo.argosy.ui.primitives.FocusIndicators
import com.nendo.argosy.ui.primitives.InputGlyph
import com.nendo.argosy.ui.primitives.argosyFocusIndicators
import com.nendo.argosy.ui.theme.AccentHue
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.LocalMotionTier
import com.nendo.argosy.ui.theme.Motion
import com.nendo.argosy.ui.theme.MotionTier
import com.nendo.argosy.ui.theme.generated.ComponentDefaults
import com.nendo.argosy.ui.util.clickableNoFocus
import kotlin.math.roundToInt

private const val WIDE_PAGE_MAX_SCREEN_FRACTION = 0.65f
private val RailActiveIndicator = FocusIndicators(stripe = true)

@Composable
fun QuickSettingsPanel(
    isVisible: Boolean,
    state: QuickSettingsState,
    page: QuickSettingsPage,
    focusedIndex: Int,
    controller: QuickSettingsController,
    onOpenDeviceAccess: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    friendsPage: @Composable () -> Unit = {},
    musicPage: @Composable () -> Unit = {},
    footerHints: List<Pair<InputButton, String>> = emptyList(),
    onHintClick: ((InputButton) -> Unit)? = null
) {
    val theme = LocalArgosyTheme.current
    val reduced = LocalMotionTier.current == MotionTier.Reduced
    val pages = remember(state) { quickSettingsVisiblePages(state) }
    val activePage = remember(page, state) { quickSettingsEffectivePage(page, state) }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.CenterEnd
    ) {
        if (isVisible) {
            val scrim = if (theme.isDark) {
                Color.Black.copy(alpha = ComponentDefaults.Launcher.overlayDarkAlpha)
            } else {
                Color.White.copy(alpha = ComponentDefaults.Launcher.overlayLightAlpha)
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(scrim)
                    .clickableNoFocus(onClick = onDismiss)
            )
        }

        val panelWidth by animateDpAsState(
            targetValue = quickPanelWidthFor(activePage),
            animationSpec = if (reduced) snap() else tween(Motion.durationSlide, easing = Motion.argosyEase),
            label = "quickSettingsPanelWidth"
        )
        AnimatedVisibility(
            visible = isVisible,
            enter = slideInHorizontally(initialOffsetX = { it }),
            exit = slideOutHorizontally(targetOffsetX = { it })
        ) {
            Row(
                modifier = Modifier
                    .width(panelWidth)
                    .fillMaxHeight()
                    .background(theme.surfaceBase)
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    QuickPanelHeader(title = stringResource(activePage.titleRes))
                    Box(modifier = Modifier.weight(1f)) {
                        when (activePage) {
                            QuickSettingsPage.FRIENDS -> friendsPage()
                            QuickSettingsPage.MUSIC -> musicPage()
                            else -> QuickSettingsList(
                                page = activePage,
                                state = state,
                                focusedIndex = focusedIndex,
                                controller = controller,
                                onOpenDeviceAccess = onOpenDeviceAccess
                            )
                        }
                    }
                    FooterHints(hints = footerHints, onHintClick = onHintClick)
                    FooterSpacer()
                }
                QuickSettingsRail(
                    pages = pages,
                    activePage = activePage,
                    onPageSelect = controller::selectPage
                )
            }
        }
    }
}

@Composable
private fun quickPanelWidthFor(page: QuickSettingsPage): Dp {
    val cap = LocalConfiguration.current.screenWidthDp.dp * WIDE_PAGE_MAX_SCREEN_FRACTION
    val width = when (page) {
        QuickSettingsPage.MUSIC -> Dimens.quickPanelWidthMedia
        QuickSettingsPage.FRIENDS, QuickSettingsPage.QUICK, QuickSettingsPage.PERFORMANCE ->
            Dimens.quickPanelWidthWide
    }
    return min(width, cap)
}

@Composable
private fun QuickPanelHeader(title: String) {
    val theme = LocalArgosyTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.quickPanelHeaderHeight)
            .padding(horizontal = Dimens.spacingMd),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = theme.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        SystemStatusBar(contentColor = theme.textDim, scrim = false)
    }
}

@Composable
private fun QuickSettingsRail(
    pages: List<QuickSettingsPage>,
    activePage: QuickSettingsPage,
    onPageSelect: (QuickSettingsPage) -> Unit
) {
    val theme = LocalArgosyTheme.current
    Column(
        modifier = Modifier
            .width(Dimens.quickPanelRailWidth)
            .fillMaxHeight()
            .background(theme.surfaceRaised)
            .padding(vertical = Dimens.spacingSm),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        pages.forEach { page ->
            key(page) {
                QuickSettingsRailItem(
                    page = page,
                    isActive = page == activePage,
                    onClick = { onPageSelect(page) }
                )
            }
        }
        if (pages.size > 1) {
            Spacer(modifier = Modifier.weight(1f))
            PagingHint()
        }
    }
}

@Composable
private fun PagingHint() {
    val mute = LocalArgosyTheme.current.textMute
    val strokeWidth = Dimens.borderThin
    Box(
        modifier = Modifier
            .size(Dimens.quickPanelRailWidth - Dimens.spacingSm)
            .drawBehind {
                val inset = size.width * PAGING_SLASH_INSET
                drawLine(
                    color = mute,
                    start = Offset(size.width - inset, inset),
                    end = Offset(inset, size.height - inset),
                    strokeWidth = strokeWidth.toPx()
                )
            }
    ) {
        InputGlyph(button = InputButton.LB, tint = mute, size = Dimens.iconMd, modifier = Modifier.align(Alignment.TopStart))
        InputGlyph(button = InputButton.RB, tint = mute, size = Dimens.iconMd, modifier = Modifier.align(Alignment.BottomEnd))
    }
}

private const val PAGING_SLASH_INSET = 0.3f

@Composable
private fun QuickSettingsRailItem(
    page: QuickSettingsPage,
    isActive: Boolean,
    onClick: () -> Unit
) {
    val theme = LocalArgosyTheme.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(Dimens.quickPanelRailWidth)
            .background(
                if (isActive) theme.focusAccent.copy(alpha = ComponentDefaults.QuickPanel.selectedFillAlpha)
                else Color.Transparent
            )
            .argosyFocusIndicators(focused = false, selected = isActive, indicators = RailActiveIndicator)
            .clickableNoFocus(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = page.icon,
            contentDescription = stringResource(page.titleRes),
            tint = if (isActive) theme.focusAccent else theme.textDim,
            modifier = Modifier.size(Dimens.iconMd)
        )
    }
}

@Composable
private fun QuickSettingsList(
    page: QuickSettingsPage,
    state: QuickSettingsState,
    focusedIndex: Int,
    controller: QuickSettingsController,
    onOpenDeviceAccess: () -> Unit
) {
    val visibleItems = remember(page, state) { quickSettingsVisibleItems(page, state) }
    val focusable = remember(page, state) { quickSettingsFocusableItems(page, state) }
    val sections = remember(page, state) { quickSettingsSections(page, state) }
    val listState = key(page) { rememberLazyListState() }

    SectionFocusedScroll(
        listState = listState,
        focusedIndex = focusedIndex,
        focusToListIndex = { quickSettingsFocusToListIndex(page, it, state) },
        sections = sections
    )

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Top
    ) {
        items(visibleItems, key = { it.key }) { item ->
            QuickSettingsItemRow(
                item = item,
                state = state,
                isFocused = focusable.indexOf(item) == focusedIndex,
                controller = controller,
                onOpenDeviceAccess = onOpenDeviceAccess
            )
        }
    }
}

@Composable
private fun QuickSettingsItemRow(
    item: QuickSettingsItem,
    state: QuickSettingsState,
    isFocused: Boolean,
    controller: QuickSettingsController,
    onOpenDeviceAccess: () -> Unit
) {
    val focus = { controller.focusItem(item) }
    val enabled = !item.isLocked(state)
    when (item) {
        is QuickSettingsItem.Header -> QuickSectionHeader(title = stringResource(item.headerGroup.titleRes))

        QuickSettingsItem.Theme -> QuickSegmentedRow(
            icon = when (state.themeMode) {
                ThemeMode.LIGHT -> Icons.Default.LightMode
                ThemeMode.DARK -> Icons.Default.DarkMode
                ThemeMode.SYSTEM -> Icons.Default.SettingsBrightness
            },
            label = stringResource(R.string.ui_quick_settings_theme),
            options = QUICK_THEME_ORDER.map { stringResource(it.quickLabelRes()) },
            selectedIndex = QUICK_THEME_ORDER.indexOf(state.themeMode),
            isFocused = isFocused,
            onFocus = focus,
            onSelect = { controller.setThemeMode(QUICK_THEME_ORDER[it]) },
            inline = true
        )

        QuickSettingsItem.Accent -> QuickSwitchedSliderRow(
            icon = Icons.Default.Palette,
            label = stringResource(R.string.ui_quick_settings_accent),
            on = state.primaryColor != null,
            fraction = AccentHue.hueOf(state.primaryColor) / 360f,
            valueText = "",
            offText = stringResource(R.string.ui_quick_settings_accent_default),
            isFocused = isFocused,
            onFocus = focus,
            onToggle = controller::setAccentEnabled,
            onFractionChange = controller::setAccentHue
        )

        QuickSettingsItem.ScreenBrightness -> QuickSliderRow(
            icon = Icons.Default.SettingsBrightness,
            label = brightnessLabel(state, physicalPrimary = true),
            valueText = quickPercentLabel(state.screenBrightness),
            fraction = state.screenBrightness,
            isFocused = isFocused,
            onFocus = focus,
            onFractionChange = controller::setScreenBrightness
        )

        QuickSettingsItem.SecondScreenBrightness -> QuickSliderRow(
            icon = Icons.Default.SettingsBrightness,
            label = brightnessLabel(state, physicalPrimary = false),
            valueText = quickPercentLabel(state.secondaryBrightness ?: 0f),
            fraction = state.secondaryBrightness ?: 0f,
            isFocused = isFocused,
            onFocus = focus,
            onFractionChange = controller::setSecondaryBrightness
        )

        QuickSettingsItem.SwapDisplays -> QuickToggleRow(
            icon = Icons.Default.SwapHoriz,
            label = stringResource(R.string.ui_quick_settings_swap_displays),
            checked = state.isRolesSwapped,
            isFocused = isFocused,
            onFocus = focus,
            onToggle = { controller.swapDisplays() }
        )

        QuickSettingsItem.SystemVolume -> QuickSliderRow(
            icon = if (state.systemVolume > 0f) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
            label = stringResource(R.string.ui_quick_settings_volume),
            valueText = quickPercentLabel(state.systemVolume),
            fraction = state.systemVolume,
            isFocused = isFocused,
            onFocus = focus,
            onFractionChange = controller::setSystemVolume
        )

        QuickSettingsItem.UISounds -> QuickToggleRow(
            icon = if (state.soundEnabled) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
            label = stringResource(R.string.ui_quick_settings_ui_sounds),
            checked = state.soundEnabled,
            isFocused = isFocused,
            onFocus = focus,
            onToggle = controller::setSoundEnabled
        )

        QuickSettingsItem.Haptic -> QuickSwitchedSliderRow(
            icon = Icons.Default.Vibration,
            label = stringResource(R.string.ui_quick_settings_haptics),
            on = state.hapticEnabled,
            fraction = state.vibrationStrength,
            valueText = quickPercentLabel(state.vibrationStrength),
            isFocused = isFocused,
            onFocus = focus,
            onToggle = controller::setHapticEnabled,
            onFractionChange = controller::setVibrationStrength
        )

        QuickSettingsItem.SwapAB -> QuickToggleRow(
            icon = Icons.Default.SportsEsports,
            label = stringResource(R.string.ui_quick_settings_swap_ab),
            checked = state.swapAB,
            isFocused = isFocused,
            onFocus = focus,
            onToggle = controller::setSwapAB
        )

        QuickSettingsItem.SwapXY -> QuickToggleRow(
            icon = Icons.Default.SportsEsports,
            label = stringResource(R.string.ui_quick_settings_swap_xy),
            checked = state.swapXY,
            isFocused = isFocused,
            onFocus = focus,
            onToggle = controller::setSwapXY
        )

        QuickSettingsItem.SwapStartSelect -> QuickToggleRow(
            icon = Icons.Default.SportsEsports,
            label = stringResource(R.string.ui_quick_settings_swap_start_select),
            checked = state.swapStartSelect,
            isFocused = isFocused,
            onFocus = focus,
            onToggle = controller::setSwapStartSelect
        )

        QuickSettingsItem.DeviceAccess -> QuickNoticeRow(
            message = stringResource(R.string.ui_quick_settings_device_access_notice),
            actionLabel = stringResource(R.string.ui_quick_settings_device_access_allow),
            isFocused = isFocused,
            onAction = {
                focus()
                onOpenDeviceAccess()
            }
        )

        QuickSettingsItem.Performance -> QuickSegmentedRow(
            icon = Icons.Default.Speed,
            label = stringResource(R.string.ui_quick_settings_performance_mode),
            options = state.performanceModes.map { stringResource(it.labelRes) },
            selectedIndex = state.performanceModes.indexOf(state.performanceMode),
            isFocused = isFocused,
            onFocus = focus,
            onSelect = { controller.setPerformanceMode(state.performanceModes[it]) },
            inline = false,
            enabled = enabled
        )

        QuickSettingsItem.Refresh -> QuickSegmentedRow(
            icon = Icons.Outlined.Monitor,
            label = stringResource(R.string.ui_quick_settings_refresh_rate),
            options = state.refreshRates.map { hz ->
                if (hz == null) {
                    stringResource(R.string.ui_quick_settings_refresh_auto)
                } else {
                    stringResource(R.string.ui_quick_settings_refresh_hz, hz)
                }
            },
            selectedIndex = state.refreshRates.indexOf(state.refreshRateHz),
            isFocused = isFocused,
            onFocus = focus,
            onSelect = { controller.setRefreshRate(state.refreshRates[it]) },
            inline = false,
            enabled = enabled
        )

        QuickSettingsItem.Fan -> QuickSegmentedRow(
            icon = Icons.Default.Toys,
            label = stringResource(R.string.ui_quick_settings_fan),
            options = FanMode.entries.map { stringResource(it.labelRes) },
            selectedIndex = FanMode.entries.indexOf(state.fanMode),
            isFocused = isFocused,
            onFocus = focus,
            onSelect = { controller.setFanMode(FanMode.entries[it]) },
            inline = false,
            enabled = enabled
        )

        QuickSettingsItem.FanSpeed -> QuickSliderRow(
            icon = Icons.Default.Toys,
            label = stringResource(R.string.ui_quick_settings_fan_speed),
            valueText = stringResource(
                R.string.ui_quick_settings_percent,
                (state.fanSpeed * 100f / FanController.SPORT_DUTY).roundToInt()
            ),
            fraction = fanDutyFraction(state.fanSpeed),
            isFocused = isFocused,
            onFocus = focus,
            onFractionChange = { controller.setFanSpeed(fanDutyFor(it)) },
            enabled = enabled
        )

        QuickSettingsItem.HudOverlay -> QuickToggleRow(
            icon = Icons.Default.Layers,
            label = stringResource(R.string.ui_quick_settings_hud),
            checked = state.hudEnabled,
            isFocused = isFocused,
            onFocus = focus,
            onToggle = controller::setHudEnabled,
            description = stringResource(R.string.ui_quick_settings_hud_description)
        )

        QuickSettingsItem.FriendsPage, QuickSettingsItem.MusicPlayer -> Unit
    }
}

@Composable
private fun brightnessLabel(state: QuickSettingsState, physicalPrimary: Boolean): String {
    if (!state.isDualScreenActive) return stringResource(R.string.ui_quick_settings_brightness)
    val isMain = physicalPrimary != state.isRolesSwapped
    return stringResource(
        if (isMain) R.string.ui_quick_settings_brightness_main else R.string.ui_quick_settings_brightness_second
    )
}

private fun fanDutyFraction(duty: Int): Float =
    (duty - FanController.CUSTOM_DUTY_MIN).toFloat() /
        (FanController.CUSTOM_DUTY_MAX - FanController.CUSTOM_DUTY_MIN)

private fun fanDutyFor(fraction: Float): Int {
    val span = FanController.CUSTOM_DUTY_MAX - FanController.CUSTOM_DUTY_MIN
    val steps = (fraction.coerceIn(0f, 1f) * span / FanController.CUSTOM_DUTY_STEP).roundToInt()
    return FanController.CUSTOM_DUTY_MIN + steps * FanController.CUSTOM_DUTY_STEP
}
