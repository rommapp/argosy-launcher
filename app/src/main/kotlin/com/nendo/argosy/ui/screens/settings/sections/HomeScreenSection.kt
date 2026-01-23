package com.nendo.argosy.ui.screens.settings.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.nendo.argosy.ui.components.ActionPreference
import com.nendo.argosy.ui.components.CyclePreference
import com.nendo.argosy.ui.components.SectionFocusedScroll
import com.nendo.argosy.ui.components.SliderPreference
import com.nendo.argosy.ui.components.SwitchPreference
import com.nendo.argosy.ui.screens.settings.DisplayState
import com.nendo.argosy.ui.screens.settings.SettingsUiState
import com.nendo.argosy.ui.screens.settings.SettingsViewModel
import com.nendo.argosy.ui.screens.settings.menu.SettingsLayout
import com.nendo.argosy.ui.theme.Dimens

private sealed class HomeScreenItem(
    val key: String,
    val section: String,
    val visibleWhen: (DisplayState) -> Boolean = { true }
) {
    val isFocusable: Boolean get() = this !is Header

    class Header(key: String, section: String, val title: String) : HomeScreenItem(key, section)

    data object GameArtwork : HomeScreenItem("gameArtwork", "background")
    data object CustomImage : HomeScreenItem(
        key = "customImage",
        section = "background",
        visibleWhen = { !it.useGameBackground }
    )
    data object Blur : HomeScreenItem("blur", "background")
    data object Saturation : HomeScreenItem("saturation", "background")
    data object Opacity : HomeScreenItem("opacity", "background")

    data object VideoWallpaper : HomeScreenItem("videoWallpaper", "video")
    data object VideoDelay : HomeScreenItem(
        key = "videoDelay",
        section = "video",
        visibleWhen = { it.videoWallpaperEnabled }
    )
    data object VideoMuted : HomeScreenItem(
        key = "videoMuted",
        section = "video",
        visibleWhen = { it.videoWallpaperEnabled }
    )

    data object AccentFooter : HomeScreenItem("accentFooter", "footer")

    companion object {
        private val BackgroundHeader = Header("backgroundHeader", "background", "Background")
        private val VideoHeader = Header("videoHeader", "video", "Video Wallpaper")
        private val FooterHeader = Header("footerHeader", "footer", "Footer")

        val ALL: List<HomeScreenItem> = listOf(
            BackgroundHeader,
            GameArtwork, CustomImage, Blur, Saturation, Opacity,
            VideoHeader,
            VideoWallpaper, VideoDelay, VideoMuted,
            FooterHeader,
            AccentFooter
        )
    }
}

private val homeScreenLayout = SettingsLayout<HomeScreenItem, DisplayState>(
    allItems = HomeScreenItem.ALL,
    isFocusable = { it.isFocusable },
    visibleWhen = { item, state -> item.visibleWhen(state) },
    sectionOf = { it.section }
)

internal fun homeScreenMaxFocusIndex(display: DisplayState): Int = homeScreenLayout.maxFocusIndex(display)

internal fun homeScreenSections(display: DisplayState) = homeScreenLayout.buildSections(display)

@Composable
fun HomeScreenSection(uiState: SettingsUiState, viewModel: SettingsViewModel) {
    val listState = rememberLazyListState()
    val display = uiState.display

    val visibleItems = remember(display.useGameBackground, display.videoWallpaperEnabled) {
        homeScreenLayout.visibleItems(display)
    }
    val sections = remember(display.useGameBackground, display.videoWallpaperEnabled) {
        homeScreenLayout.buildSections(display)
    }

    fun isFocused(item: HomeScreenItem): Boolean =
        uiState.focusedIndex == homeScreenLayout.focusIndexOf(item, display)

    SectionFocusedScroll(
        listState = listState,
        focusedIndex = uiState.focusedIndex,
        focusToListIndex = { homeScreenLayout.focusToListIndex(it, display) },
        sections = sections
    )

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().padding(Dimens.spacingMd),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) {
        items(visibleItems, key = { it.key }) { item ->
            when (item) {
                is HomeScreenItem.Header -> HomeScreenSectionHeader(item.title)

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
                    val delayText = when (display.videoWallpaperDelaySeconds) {
                        0 -> "Instant"
                        1 -> "1 second"
                        else -> "${display.videoWallpaperDelaySeconds} seconds"
                    }
                    CyclePreference(
                        title = "Delay Before Playback",
                        value = delayText,
                        isFocused = isFocused(item),
                        onClick = { viewModel.cycleVideoWallpaperDelay() }
                    )
                }

                HomeScreenItem.VideoMuted -> {
                    val hasCustomBgm = uiState.ambientAudio.enabled && uiState.ambientAudio.audioUri != null
                    val effectiveMuted = hasCustomBgm || display.videoWallpaperMuted
                    SwitchPreference(
                        title = "Muted Playback",
                        subtitle = if (hasCustomBgm) "Auto-muted while Custom BGM is active" else "Mute video audio",
                        isEnabled = effectiveMuted,
                        isFocused = isFocused(item),
                        onToggle = { if (!hasCustomBgm) viewModel.setVideoWallpaperMuted(!display.videoWallpaperMuted) }
                    )
                }

                HomeScreenItem.AccentFooter -> SwitchPreference(
                    title = "Accent Color Footer",
                    subtitle = "Use accent color for footer background",
                    isEnabled = display.useAccentColorFooter,
                    isFocused = isFocused(item),
                    onToggle = { viewModel.setUseAccentColorFooter(it) }
                )
            }
        }
    }
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
