package com.nendo.argosy.ui.screens.settings.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import com.nendo.argosy.ui.screens.settings.components.SectionPaneLayout
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nendo.argosy.R
import com.nendo.argosy.data.preferences.HomeBackgroundMode
import com.nendo.argosy.domain.model.HomeLayoutKind
import com.nendo.argosy.ui.components.ActionPreference
import com.nendo.argosy.ui.components.CyclePreference
import com.nendo.argosy.ui.components.HomeLayoutPreview
import com.nendo.argosy.ui.components.HomeLayoutSelectorRow
import com.nendo.argosy.ui.components.HomeLayoutSettingField
import com.nendo.argosy.ui.components.HomeLayoutSettingRow
import com.nendo.argosy.ui.components.adjustHomeLayoutField
import com.nendo.argosy.ui.components.homeLayoutFieldsFor
import com.nendo.argosy.ui.components.homeRailFields
import com.nendo.argosy.ui.components.toggleHomeLayoutField
import com.nendo.argosy.ui.components.SliderPreference
import com.nendo.argosy.ui.components.SwitchPreference
import com.nendo.argosy.ui.screens.settings.DisplayState
import com.nendo.argosy.ui.screens.settings.SettingsInputHandler
import com.nendo.argosy.ui.screens.settings.SettingsUiState
import com.nendo.argosy.ui.screens.settings.SettingsViewModel
import com.nendo.argosy.ui.screens.settings.delegates.DisplaySettingsDelegate
import com.nendo.argosy.ui.screens.settings.menu.SettingsLayout
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.generated.ComponentDefaults

internal sealed class HomeScreenItem(
    val key: String,
    val section: String,
    val visibleWhen: (DisplayState) -> Boolean = { true }
) {
    val isFocusable: Boolean get() = this !is Header && this !is LayoutPreview

    class Header(key: String, section: String, val titleRes: Int) : HomeScreenItem(key, section)

    data object Background : HomeScreenItem(
        key = "homeBackgroundMode",
        section = "background",
        visibleWhen = { it.surfaceBackdrop.enabled && drawsBackgroundArt(it) }
    )
    data object GameArtwork : HomeScreenItem(
        key = "gameArtwork",
        section = "background",
        visibleWhen = { showsArtLayer(it) }
    )
    data object CustomImage : HomeScreenItem(
        key = "customImage",
        section = "background",
        visibleWhen = { showsArtLayer(it) && !it.useGameBackground }
    )
    data object Blur : HomeScreenItem("blur", "background", { showsArtLayer(it) })
    data object Saturation : HomeScreenItem("saturation", "background", { showsArtLayer(it) })
    data object Opacity : HomeScreenItem("opacity", "background", { showsArtLayer(it) })

    data object VideoWallpaper : HomeScreenItem(
        key = "videoWallpaper",
        section = "video",
        visibleWhen = { drawsBackgroundArt(it) }
    )
    data object VideoDelay : HomeScreenItem(
        key = "videoDelay",
        section = "video",
        visibleWhen = { it.videoWallpaperEnabled && drawsBackgroundArt(it) }
    )
    data object VideoMuted : HomeScreenItem(
        key = "videoMuted",
        section = "video",
        visibleWhen = { it.videoWallpaperEnabled && drawsBackgroundArt(it) }
    )

    data object LayoutPreview : HomeScreenItem("layoutPreview", "layout")

    data object LayoutSelector : HomeScreenItem("layoutSelector", "layout")

    /**
     * One home setting field, wherever it belongs on this pane. Rail toggles are content rather
     * than layout, so the section and the visibility rule come from the field rather than being
     * fixed: everything else follows the selected layout, and a rail follows whether the selected
     * layout draws rows at all.
     */
    data class LayoutField(val field: HomeLayoutSettingField) : HomeScreenItem(
        key = "layoutField_${field.name}",
        section = if (field in homeRailFields()) "content" else "layout",
        visibleWhen = {
            if (field in homeRailFields()) {
                it.homeLayout.selected != HomeLayoutKind.CUSTOM_GRID
            } else {
                field in homeLayoutFieldsFor(it.homeLayout.selected)
            }
        }
    )

    data object InstalledOnly : HomeScreenItem(
        key = "installedOnly",
        section = "content",
        visibleWhen = { it.homeLayout.selected != HomeLayoutKind.CUSTOM_GRID }
    )

    companion object {
        /**
         * Mirrors the home screen's own `showArtLayer`: with the theme backdrop off the art layer
         * always draws, and with it on the background mode decides. Every row that only tunes that
         * layer is hidden when it is not drawn.
         */
        private fun showsArtLayer(state: DisplayState): Boolean =
            drawsBackgroundArt(state) &&
                (!state.surfaceBackdrop.enabled ||
                    state.homeBackgroundMode == HomeBackgroundMode.GAME_ART)

        /**
         * A grid fills the screen with covers, so nothing is drawn behind it and every row that
         * tunes a backdrop would be a setting with no effect. Only the carousel has room for art.
         */
        private fun drawsBackgroundArt(state: DisplayState): Boolean =
            state.homeLayout.selected == HomeLayoutKind.CAROUSEL

        private val BackgroundHeader =
            Header("backgroundHeader", "background", R.string.settings_home_screen_section_background)
        private val VideoHeader = Header("videoHeader", "video", R.string.settings_home_screen_section_video)
        private val LayoutHeader = Header("layoutHeader", "layout", R.string.settings_home_screen_section_layout)
        private val ContentHeader =
            Header("contentHeader", "content", R.string.settings_home_screen_section_content)

        val ALL: List<HomeScreenItem>
            get() = listOf(
                LayoutHeader,
                LayoutPreview,
                LayoutSelector,
                *HomeLayoutKind.entries
                    .flatMap { homeLayoutFieldsFor(it) }
                    .distinct()
                    .map { LayoutField(it) }
                    .toTypedArray(),
                ContentHeader,
                InstalledOnly,
                *homeRailFields().map { LayoutField(it) }.toTypedArray(),
                BackgroundHeader,
                Background, GameArtwork, CustomImage, Blur, Saturation, Opacity,
                VideoHeader,
                VideoWallpaper, VideoDelay, VideoMuted
            )
    }
}

private val homeScreenLayout = SettingsLayout<HomeScreenItem, DisplayState>(
    allItems = HomeScreenItem.ALL,
    isFocusable = { it.isFocusable },
    visibleWhen = { item, state -> item.visibleWhen(state) },
    sectionOf = { it.section },
    sectionTitleRes = {
        when (it) {
            "layout" -> R.string.settings_home_screen_section_layout
            "background" -> R.string.settings_home_screen_section_background
            "video" -> R.string.settings_home_screen_section_video
            "content" -> R.string.settings_home_screen_section_content
            else -> null
        }
    }
)

private fun homeBackgroundModeLabelRes(mode: HomeBackgroundMode): Int = when (mode) {
    HomeBackgroundMode.GAME_ART -> R.string.settings_home_screen_background_mode_game_art
    HomeBackgroundMode.PATTERN -> R.string.settings_home_screen_background_mode_pattern
}

internal fun homeScreenMaxFocusIndex(display: DisplayState): Int = homeScreenLayout.maxFocusIndex(display)

internal fun homeScreenSections(display: DisplayState) = homeScreenLayout.buildSections(display)

internal fun homeScreenItemAtFocusIndex(index: Int, display: DisplayState): HomeScreenItem? =
    homeScreenLayout.itemAtFocusIndex(index, display)

internal fun homeScreenFocusIndexOf(item: HomeScreenItem, display: DisplayState): Int =
    homeScreenLayout.focusIndexOf(item, display)

@Composable
fun HomeScreenSection(uiState: SettingsUiState, viewModel: SettingsViewModel) {
    val display = uiState.display
    val context = LocalContext.current

    val visibleItems = remember(
        display.useGameBackground,
        display.videoWallpaperEnabled,
        display.surfaceBackdrop.enabled,
        display.homeBackgroundMode,
        display.homeLayout.selected
    ) {
        homeScreenLayout.visibleItems(display)
    }
    val sections = remember(
        display.useGameBackground,
        display.videoWallpaperEnabled,
        display.surfaceBackdrop.enabled,
        display.homeBackgroundMode,
        display.homeLayout.selected,
        context
    ) {
        homeScreenLayout.buildSections(display, context)
    }

    fun isFocused(item: HomeScreenItem): Boolean =
        uiState.focusedIndex == homeScreenLayout.focusIndexOf(item, display)

    fun pickerToken(item: HomeScreenItem): Int =
        if (uiState.enumPickerKey == item.key) uiState.enumPickerToken else 0

    SectionPaneLayout(
        items = visibleItems,
        sections = sections,
        focusedIndex = uiState.focusedIndex,
        focusToListIndex = { homeScreenLayout.focusToListIndex(it, display) },
        itemKey = { it.key },
        isNavItem = { false },
        isHeader = { it is HomeScreenItem.Header },
        onSectionTap = { viewModel.setFocusIndex(it.focusStartIndex) },
        modifier = Modifier.fillMaxSize().padding(Dimens.spacingMd),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) { item ->
            when (item) {
                is HomeScreenItem.Header -> HomeScreenSectionHeader(stringResource(item.titleRes))

                HomeScreenItem.Background -> CyclePreference(
                    title = stringResource(R.string.settings_home_screen_background_title),
                    subtitle = stringResource(R.string.settings_home_screen_background_subtitle),
                    value = stringResource(homeBackgroundModeLabelRes(display.homeBackgroundMode)),
                    isFocused = isFocused(item),
                    onClick = { viewModel.cycleHomeBackgroundMode() },
                    onPrev = { viewModel.cycleHomeBackgroundMode(-1) },
                    options = remember(context) {
                        HomeBackgroundMode.entries.map { context.getString(homeBackgroundModeLabelRes(it)) }
                    },
                    onSelect = { index -> viewModel.setHomeBackgroundMode(HomeBackgroundMode.entries[index]) },
                    pickerRequestToken = pickerToken(item)
                )

                HomeScreenItem.GameArtwork -> SwitchPreference(
                    title = stringResource(R.string.settings_home_screen_game_artwork_title),
                    subtitle = stringResource(R.string.settings_home_screen_game_artwork_subtitle),
                    isEnabled = display.useGameBackground,
                    isFocused = isFocused(item),
                    onToggle = { viewModel.setUseGameBackground(it) }
                )

                HomeScreenItem.CustomImage -> {
                    val subtitle = if (display.customBackgroundPath != null) {
                        stringResource(R.string.settings_home_screen_custom_image_subtitle_set)
                    } else {
                        stringResource(R.string.settings_home_screen_custom_image_subtitle_empty)
                    }
                    ActionPreference(
                        icon = Icons.Outlined.PhotoLibrary,
                        title = stringResource(R.string.settings_home_screen_custom_image_title),
                        subtitle = subtitle,
                        isFocused = isFocused(item),
                        onClick = { viewModel.openBackgroundPicker() }
                    )
                }

                HomeScreenItem.Blur -> SliderPreference(
                    title = stringResource(R.string.settings_home_screen_blur_title),
                    value = display.backgroundBlur / 10,
                    minValue = 0,
                    maxValue = 10,
                    isFocused = isFocused(item),
                    onAdjust = { viewModel.adjustBackgroundBlur(it * SettingsInputHandler.SLIDER_STEP) }
                )

                HomeScreenItem.Saturation -> SliderPreference(
                    title = stringResource(R.string.settings_home_screen_saturation_title),
                    value = display.backgroundSaturation / 10,
                    minValue = 0,
                    maxValue = 10,
                    isFocused = isFocused(item),
                    onAdjust = { viewModel.adjustBackgroundSaturation(it * SettingsInputHandler.SLIDER_STEP) }
                )

                HomeScreenItem.Opacity -> SliderPreference(
                    title = stringResource(R.string.settings_home_screen_opacity_title),
                    value = display.backgroundOpacity / 10,
                    minValue = 0,
                    maxValue = 10,
                    isFocused = isFocused(item),
                    onAdjust = { viewModel.adjustBackgroundOpacity(it * SettingsInputHandler.SLIDER_STEP) }
                )

                HomeScreenItem.VideoWallpaper -> SwitchPreference(
                    title = stringResource(R.string.settings_home_screen_video_wallpaper_title),
                    subtitle = stringResource(R.string.settings_home_screen_video_wallpaper_subtitle),
                    isEnabled = display.videoWallpaperEnabled,
                    isFocused = isFocused(item),
                    onToggle = { viewModel.setVideoWallpaperEnabled(!display.videoWallpaperEnabled) }
                )

                HomeScreenItem.VideoDelay -> {
                    val delayText = videoDelayLabel(context, display.videoWallpaperDelaySeconds)
                    CyclePreference(
                        title = stringResource(R.string.settings_home_screen_video_delay_title),
                        value = delayText,
                        isFocused = isFocused(item),
                        onClick = { viewModel.cycleVideoWallpaperDelay() },
                        onPrev = { viewModel.cycleVideoWallpaperDelay(-1) },
                        options = remember(context) {
                            DisplaySettingsDelegate.VIDEO_DELAY_SECONDS.map { videoDelayLabel(context, it) }
                        },
                        onSelect = { index ->
                            val currentIndex = DisplaySettingsDelegate.VIDEO_DELAY_SECONDS
                                .indexOf(display.videoWallpaperDelaySeconds).coerceAtLeast(0)
                            viewModel.cycleVideoWallpaperDelay(index - currentIndex)
                        },
                        pickerRequestToken = pickerToken(item)
                    )
                }

                HomeScreenItem.VideoMuted -> {
                    SwitchPreference(
                        title = stringResource(R.string.settings_home_screen_video_muted_title),
                        subtitle = stringResource(R.string.settings_home_screen_video_muted_subtitle),
                        isEnabled = display.videoWallpaperMuted,
                        isFocused = isFocused(item),
                        onToggle = { viewModel.setVideoWallpaperMuted(it) }
                    )
                }

                HomeScreenItem.LayoutPreview -> Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    HomeLayoutPreview(
                        settings = display.homeLayout,
                        gridDensity = display.gridDensity,
                        modifier = Modifier.heightIn(
                            max = ComponentDefaults.HomeLayoutPreview.maxHeightDp.dp
                        )
                    )
                }

                HomeScreenItem.LayoutSelector -> HomeLayoutSelectorRow(
                    selected = display.homeLayout.selected,
                    isFocused = isFocused(item),
                    onSelect = { kind ->
                        viewModel.setFocusIndex(homeScreenFocusIndexOf(item, display))
                        viewModel.setHomeLayout(display.homeLayout.copy(selected = kind))
                    }
                )

                is HomeScreenItem.LayoutField -> HomeLayoutSettingRow(
                    settings = display.homeLayout,
                    field = item.field,
                    isFocused = isFocused(item),
                    onAdjust = { direction ->
                        viewModel.setFocusIndex(homeScreenFocusIndexOf(item, display))
                        viewModel.setHomeLayout(
                            adjustHomeLayoutField(display.homeLayout, item.field, direction)
                        )
                    },
                    onToggle = {
                        viewModel.setFocusIndex(homeScreenFocusIndexOf(item, display))
                        viewModel.setHomeLayout(toggleHomeLayoutField(display.homeLayout, item.field))
                    },
                    boxArtCapableGames = display.boxArtCapableGames
                )

                HomeScreenItem.InstalledOnly -> SwitchPreference(
                    title = stringResource(R.string.settings_home_screen_installed_only_title),
                    subtitle = stringResource(R.string.settings_home_screen_installed_only_subtitle),
                    isEnabled = display.installedOnlyHome,
                    isFocused = isFocused(item),
                    onToggle = { viewModel.setInstalledOnlyHome(it) }
                )
            }
    }
}

private fun videoDelayLabel(context: android.content.Context, seconds: Int): String =
    if (seconds == 0) {
        context.getString(R.string.settings_home_screen_video_delay_instant)
    } else {
        context.resources.getQuantityString(
            R.plurals.settings_home_screen_video_delay_seconds,
            seconds,
            seconds
        )
    }

@Composable
private fun HomeScreenSectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = Dimens.spacingXs)
    )
}

