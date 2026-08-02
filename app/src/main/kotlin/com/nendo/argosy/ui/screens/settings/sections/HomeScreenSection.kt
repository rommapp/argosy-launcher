package com.nendo.argosy.ui.screens.settings.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import com.nendo.argosy.ui.screens.settings.components.SectionPaneLayout
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
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
import com.nendo.argosy.ui.components.toggleHomeLayoutField
import com.nendo.argosy.ui.components.SliderPreference
import com.nendo.argosy.ui.components.SwitchPreference
import com.nendo.argosy.ui.screens.settings.DisplayState
import com.nendo.argosy.ui.screens.settings.SettingsUiState
import com.nendo.argosy.ui.screens.settings.SettingsViewModel
import com.nendo.argosy.ui.screens.settings.delegates.DisplaySettingsDelegate
import com.nendo.argosy.ui.screens.settings.menu.SettingsLayout
import com.nendo.argosy.ui.theme.Dimens

internal sealed class HomeScreenItem(
    val key: String,
    val section: String,
    val visibleWhen: (DisplayState) -> Boolean = { true }
) {
    val isFocusable: Boolean get() = this !is Header && this !is LayoutPreview

    class Header(key: String, section: String, val title: String) : HomeScreenItem(key, section)

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

    data class LayoutField(val field: HomeLayoutSettingField) : HomeScreenItem(
        key = "layoutField_${field.name}",
        section = "layout",
        visibleWhen = { field in homeLayoutFieldsFor(it.homeLayout.selected) }
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

        private val BackgroundHeader = Header("backgroundHeader", "background", "Background")
        private val VideoHeader = Header("videoHeader", "video", "Video Wallpaper")
        private val LayoutHeader = Header("layoutHeader", "layout", "Layout")
        private val ContentHeader = Header("contentHeader", "content", "Content")

        val ALL: List<HomeScreenItem> = listOf(
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
    sectionTitle = {
        when (it) {
            "layout" -> "Layout"
            "background" -> "Background"
            "video" -> "Video Wallpaper"
            "content" -> "Content"
            else -> null
        }
    }
)

internal fun homeScreenMaxFocusIndex(display: DisplayState): Int = homeScreenLayout.maxFocusIndex(display)

internal fun homeScreenSections(display: DisplayState) = homeScreenLayout.buildSections(display)

internal fun homeScreenItemAtFocusIndex(index: Int, display: DisplayState): HomeScreenItem? =
    homeScreenLayout.itemAtFocusIndex(index, display)

internal fun homeScreenFocusIndexOf(item: HomeScreenItem, display: DisplayState): Int =
    homeScreenLayout.focusIndexOf(item, display)

@Composable
fun HomeScreenSection(uiState: SettingsUiState, viewModel: SettingsViewModel) {
    val display = uiState.display

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
        display.homeLayout.selected
    ) {
        homeScreenLayout.buildSections(display)
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
                is HomeScreenItem.Header -> HomeScreenSectionHeader(item.title)

                HomeScreenItem.Background -> CyclePreference(
                    title = "Background",
                    subtitle = "Game art or the backdrop pattern",
                    value = display.homeBackgroundMode.displayName,
                    isFocused = isFocused(item),
                    onClick = { viewModel.cycleHomeBackgroundMode() },
                    onPrev = { viewModel.cycleHomeBackgroundMode(-1) },
                    options = remember { HomeBackgroundMode.entries.map { it.displayName } },
                    onSelect = { index -> viewModel.setHomeBackgroundMode(HomeBackgroundMode.entries[index]) },
                    pickerRequestToken = pickerToken(item)
                )

                HomeScreenItem.GameArtwork -> SwitchPreference(
                    title = "Game Artwork",
                    subtitle = "Use game cover as background",
                    isEnabled = display.useGameBackground,
                    isFocused = isFocused(item),
                    onToggle = { viewModel.setUseGameBackground(it) }
                )

                HomeScreenItem.CustomImage -> {
                    val subtitle = if (display.customBackgroundPath != null) {
                        "Custom image selected"
                    } else {
                        "No image selected"
                    }
                    ActionPreference(
                        icon = Icons.Outlined.PhotoLibrary,
                        title = "Custom Image",
                        subtitle = subtitle,
                        isFocused = isFocused(item),
                        onClick = { viewModel.openBackgroundPicker() }
                    )
                }

                HomeScreenItem.Blur -> SliderPreference(
                    title = "Blur",
                    value = display.backgroundBlur / 10,
                    minValue = 0,
                    maxValue = 10,
                    isFocused = isFocused(item),
                    onClick = { viewModel.cycleBackgroundBlur() }
                )

                HomeScreenItem.Saturation -> SliderPreference(
                    title = "Saturation",
                    value = display.backgroundSaturation / 10,
                    minValue = 0,
                    maxValue = 10,
                    isFocused = isFocused(item),
                    onClick = { viewModel.cycleBackgroundSaturation() }
                )

                HomeScreenItem.Opacity -> SliderPreference(
                    title = "Opacity",
                    value = display.backgroundOpacity / 10,
                    minValue = 0,
                    maxValue = 10,
                    isFocused = isFocused(item),
                    onClick = { viewModel.cycleBackgroundOpacity() }
                )

                HomeScreenItem.VideoWallpaper -> SwitchPreference(
                    title = "Show Video Wallpaper",
                    subtitle = "Play video backgrounds on home screen",
                    isEnabled = display.videoWallpaperEnabled,
                    isFocused = isFocused(item),
                    onToggle = { viewModel.setVideoWallpaperEnabled(!display.videoWallpaperEnabled) }
                )

                HomeScreenItem.VideoDelay -> {
                    val delayText = videoDelayLabel(display.videoWallpaperDelaySeconds)
                    CyclePreference(
                        title = "Delay Before Playback",
                        value = delayText,
                        isFocused = isFocused(item),
                        onClick = { viewModel.cycleVideoWallpaperDelay() },
                        onPrev = { viewModel.cycleVideoWallpaperDelay(-1) },
                        options = remember { DisplaySettingsDelegate.VIDEO_DELAY_SECONDS.map { videoDelayLabel(it) } },
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
                        title = "Muted Playback",
                        subtitle = "Mute video audio",
                        isEnabled = display.videoWallpaperMuted,
                        isFocused = isFocused(item),
                        onToggle = { viewModel.setVideoWallpaperMuted(it) }
                    )
                }

                HomeScreenItem.LayoutPreview -> HomeLayoutPreview(
                    settings = display.homeLayout,
                    gridDensity = display.gridDensity,
                    modifier = Modifier.fillMaxWidth()
                )

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
                    }
                )

                HomeScreenItem.InstalledOnly -> SwitchPreference(
                    title = "Installed Games Only",
                    subtitle = "Only show downloaded games on home screen",
                    isEnabled = display.installedOnlyHome,
                    isFocused = isFocused(item),
                    onToggle = { viewModel.setInstalledOnlyHome(it) }
                )
            }
    }
}

private fun videoDelayLabel(seconds: Int): String = when (seconds) {
    0 -> "Instant"
    1 -> "1 second"
    else -> "$seconds seconds"
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

