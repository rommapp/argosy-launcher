package com.nendo.argosy.ui.screens.settings.sections

import androidx.annotation.StringRes
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nendo.argosy.R
import com.nendo.argosy.data.preferences.HomeBackgroundMode
import com.nendo.argosy.data.preferences.NewBadgePlacement
import com.nendo.argosy.domain.model.HomeLayoutKind
import com.nendo.argosy.ui.components.ActionPreference
import com.nendo.argosy.ui.components.CyclePreference
import com.nendo.argosy.ui.components.HomeLayoutPreview
import com.nendo.argosy.ui.components.HomeLayoutSelectorRow
import com.nendo.argosy.ui.components.HomeLayoutSettingField
import com.nendo.argosy.ui.components.HomeLayoutSettingRow
import com.nendo.argosy.ui.components.homeLayoutFieldsFor
import com.nendo.argosy.ui.components.isHomeLayoutFieldShown
import com.nendo.argosy.ui.components.homeRailFields
import com.nendo.argosy.ui.components.titleRes
import com.nendo.argosy.ui.components.toggleHomeLayoutField
import com.nendo.argosy.ui.components.SegmentedPreference
import com.nendo.argosy.ui.components.SliderPreference
import com.nendo.argosy.ui.components.SwitchPreference
import com.nendo.argosy.ui.screens.settings.DisplayState
import com.nendo.argosy.data.preferences.RecentNewGamesLimit
import com.nendo.argosy.ui.screens.settings.SettingsInputHandler
import com.nendo.argosy.ui.screens.settings.SettingsUiState
import com.nendo.argosy.ui.screens.settings.SettingsViewModel
import com.nendo.argosy.ui.screens.settings.delegates.DisplaySettingsDelegate
import com.nendo.argosy.ui.screens.settings.menu.SettingsLayout
import com.nendo.argosy.ui.screens.settings.search.SearchableSetting
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.generated.ComponentDefaults

internal sealed class HomeScreenItem(
    val key: String,
    val section: String,
    val visibleWhen: (DisplayState) -> Boolean = { true }
) {
    val isFocusable: Boolean get() = this is SearchableSetting

    class Header(key: String, section: String, val titleRes: Int) : HomeScreenItem(key, section)

    sealed class Row(
        key: String,
        section: String,
        @StringRes override val labelRes: Int,
        visibleWhen: (DisplayState) -> Boolean = { true }
    ) : HomeScreenItem(key, section, visibleWhen), SearchableSetting

    data object Background : Row(
        key = "homeBackgroundMode",
        section = "background",
        labelRes = R.string.settings_home_screen_background_title,
        visibleWhen = { it.surfaceBackdrop.enabled && drawsBackgroundArt(it) }
    )
    data object GameArtwork : Row(
        key = "gameArtwork",
        section = "background",
        labelRes = R.string.settings_home_screen_game_artwork_title,
        visibleWhen = { showsArtLayer(it) }
    )
    data object CustomImage : Row(
        key = "customImage",
        section = "background",
        labelRes = R.string.settings_home_screen_custom_image_title,
        visibleWhen = { showsArtLayer(it) && !it.useGameBackground }
    )
    data object Blur :
        Row("blur", "background", R.string.settings_home_screen_blur_title, { showsArtLayer(it) })
    data object Saturation :
        Row("saturation", "background", R.string.settings_home_screen_saturation_title, { showsArtLayer(it) })
    data object Opacity :
        Row("opacity", "background", R.string.settings_home_screen_opacity_title, { showsArtLayer(it) })

    data object VideoWallpaper : Row(
        key = "videoWallpaper",
        section = "video",
        labelRes = R.string.settings_home_screen_video_wallpaper_title,
        visibleWhen = { playsVideo(it) }
    )
    data object VideoDelay : Row(
        key = "videoDelay",
        section = "video",
        labelRes = R.string.settings_home_screen_video_delay_title,
        visibleWhen = { it.videoWallpaperEnabled && playsVideo(it) }
    )
    data object VideoMuted : Row(
        key = "videoMuted",
        section = "video",
        labelRes = R.string.settings_home_screen_video_muted_title,
        visibleWhen = { it.videoWallpaperEnabled && playsVideo(it) }
    )

    data object LayoutPreview : HomeScreenItem("layoutPreview", "layout")

    data object LayoutSelector :
        Row("layoutSelector", "layout", R.string.settings_search_label_home_layout_selector)

    data object BoxArt : Row(
        key = "homeBoxArt",
        section = "layout",
        labelRes = R.string.settings_home_screen_box_art_title,
        visibleWhen = { it.box3dCapableGames > 0 || it.homeLayout.boxArt3d }
    )

    /**
     * One home setting field on this pane, with its section and visibility taken from the field. A
     * rail field shows when the selected layout shows that rail; every other field follows the
     * selected layout.
     */
    data class LayoutField(val field: HomeLayoutSettingField) : Row(
        key = "layoutField_${field.name}",
        section = if (field in homeRailFields()) "content" else "layout",
        labelRes = field.titleRes,
        visibleWhen = {
            if (field in homeRailFields()) {
                showsRail(it.homeLayout.selected, field)
            } else {
                isHomeLayoutFieldShown(it.homeLayout, field)
            }
        }
    ) {
        override val keywordsRes: Int?
            get() = if (this.field == HomeLayoutSettingField.AUTO_GRID_LANES) R.string.ui_home_layout_lanes_rows else null
    }

    data object InstalledOnly : Row(
        key = "installedOnly",
        section = "content",
        labelRes = R.string.settings_home_screen_installed_only_title,
        visibleWhen = { it.homeLayout.selected != HomeLayoutKind.CUSTOM_GRID }
    )

    data object RecentNewGames : Row(
        key = "recentNewGames",
        section = "content",
        labelRes = R.string.settings_home_screen_recent_new_games_title,
        visibleWhen = {
            when (it.homeLayout.selected) {
                HomeLayoutKind.CAROUSEL, HomeLayoutKind.AUTO_GRID -> true
                HomeLayoutKind.CUSTOM_GRID, HomeLayoutKind.COLUMNS -> false
            }
        }
    )

    data object NewBadge : Row(
        key = "newBadge",
        section = "content",
        labelRes = R.string.settings_home_screen_new_badge_title,
        visibleWhen = { it.homeLayout.selected == HomeLayoutKind.CAROUSEL }
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

        private fun drawsBackgroundArt(state: DisplayState): Boolean = when (state.homeLayout.selected) {
            HomeLayoutKind.CAROUSEL -> true
            HomeLayoutKind.AUTO_GRID, HomeLayoutKind.CUSTOM_GRID, HomeLayoutKind.COLUMNS -> false
        }

        private fun showsRail(kind: HomeLayoutKind, field: HomeLayoutSettingField): Boolean = when (kind) {
            HomeLayoutKind.CAROUSEL, HomeLayoutKind.AUTO_GRID -> true
            HomeLayoutKind.CUSTOM_GRID -> false
            HomeLayoutKind.COLUMNS -> field != HomeLayoutSettingField.RAIL_MEDIA_LIBRARIES
        }

        private fun playsVideo(state: DisplayState): Boolean =
            com.nendo.argosy.ui.screens.home.playsHomeVideo(state.homeLayout.selected) ||
                (state.dualScreenEnabled && state.hasSecondaryDisplay && !state.secondaryDisplayUnsupported)

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
                BoxArt,
                *HomeLayoutKind.entries
                    .flatMap { homeLayoutFieldsFor(it) }
                    .distinct()
                    .map { LayoutField(it) }
                    .toTypedArray(),
                ContentHeader,
                InstalledOnly,
                RecentNewGames,
                NewBadge,
                *homeRailFields().map { LayoutField(it) }.toTypedArray(),
                BackgroundHeader,
                Background, GameArtwork, CustomImage, Blur, Saturation, Opacity,
                VideoHeader,
                VideoWallpaper, VideoDelay, VideoMuted
            )
    }
}

internal val homeScreenLayout = SettingsLayout<HomeScreenItem, DisplayState>(
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

private fun newBadgePlacementLabelRes(placement: NewBadgePlacement): Int = when (placement) {
    NewBadgePlacement.RIGHT -> R.string.settings_home_screen_new_badge_option_right
    NewBadgePlacement.LEFT -> R.string.settings_home_screen_new_badge_option_left
    NewBadgePlacement.HIDDEN -> R.string.settings_home_screen_new_badge_option_hidden
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
    val presentsPreview = com.nendo.argosy.DualScreenManagerHolder.instance
        ?.isCompanionActive?.collectAsState()?.value == true
    if (presentsPreview) {
        com.nendo.argosy.ui.dualscreen.PresentOnCompanion(
            owner = com.nendo.argosy.ui.dualscreen.SlotOwner("settings.homeScreen"),
            slot = com.nendo.argosy.ui.dualscreen.PresentationSlot.HomeLayoutPreview(display.homeLayout)
        )
    }

    val visibleItems = remember(
        display.useGameBackground,
        display.videoWallpaperEnabled,
        display.surfaceBackdrop.enabled,
        display.homeBackgroundMode,
        display.homeLayout.selected,
        display.homeLayout.customGrid.gridKind,
        display.box3dCapableGames,
        display.homeLayout.boxArt3d
    ) {
        homeScreenLayout.visibleItems(display)
    }
    val sections = remember(
        display.useGameBackground,
        display.videoWallpaperEnabled,
        display.surfaceBackdrop.enabled,
        display.homeBackgroundMode,
        display.homeLayout.selected,
        display.homeLayout.customGrid.gridKind,
        display.box3dCapableGames,
        display.homeLayout.boxArt3d,
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

                is HomeScreenItem.Background -> SegmentedPreference(
                    title = stringResource(item.labelRes),
                    subtitle = stringResource(R.string.settings_home_screen_background_subtitle),
                    options = HomeBackgroundMode.entries.map { stringResource(homeBackgroundModeLabelRes(it)) },
                    selectedIndex = HomeBackgroundMode.entries.indexOf(display.homeBackgroundMode),
                    isFocused = isFocused(item),
                    onSelect = { index -> viewModel.setHomeBackgroundMode(HomeBackgroundMode.entries[index]) }
                )

                is HomeScreenItem.GameArtwork -> SwitchPreference(
                    title = stringResource(item.labelRes),
                    subtitle = stringResource(R.string.settings_home_screen_game_artwork_subtitle),
                    isEnabled = display.useGameBackground,
                    isFocused = isFocused(item),
                    onToggle = { viewModel.setUseGameBackground(it) }
                )

                is HomeScreenItem.CustomImage -> {
                    val subtitle = if (display.customBackgroundPath != null) {
                        stringResource(R.string.settings_home_screen_custom_image_subtitle_set)
                    } else {
                        stringResource(R.string.settings_home_screen_custom_image_subtitle_empty)
                    }
                    ActionPreference(
                        icon = Icons.Outlined.PhotoLibrary,
                        title = stringResource(item.labelRes),
                        subtitle = subtitle,
                        isFocused = isFocused(item),
                        onClick = { viewModel.openBackgroundPicker() }
                    )
                }

                is HomeScreenItem.Blur -> SliderPreference(
                    title = stringResource(item.labelRes),
                    value = display.backgroundBlur / 10,
                    minValue = 0,
                    maxValue = 10,
                    isFocused = isFocused(item),
                    onAdjust = { viewModel.adjustBackgroundBlur(it * SettingsInputHandler.SLIDER_STEP) }
                )

                is HomeScreenItem.Saturation -> SliderPreference(
                    title = stringResource(item.labelRes),
                    value = display.backgroundSaturation / 10,
                    minValue = 0,
                    maxValue = 10,
                    isFocused = isFocused(item),
                    onAdjust = { viewModel.adjustBackgroundSaturation(it * SettingsInputHandler.SLIDER_STEP) }
                )

                is HomeScreenItem.Opacity -> SliderPreference(
                    title = stringResource(item.labelRes),
                    value = display.backgroundOpacity / 10,
                    minValue = 0,
                    maxValue = 10,
                    isFocused = isFocused(item),
                    onAdjust = { viewModel.adjustBackgroundOpacity(it * SettingsInputHandler.SLIDER_STEP) }
                )

                is HomeScreenItem.VideoWallpaper -> SwitchPreference(
                    title = stringResource(item.labelRes),
                    subtitle = stringResource(R.string.settings_home_screen_video_wallpaper_subtitle),
                    isEnabled = display.videoWallpaperEnabled,
                    isFocused = isFocused(item),
                    onToggle = { viewModel.setVideoWallpaperEnabled(!display.videoWallpaperEnabled) }
                )

                is HomeScreenItem.VideoDelay -> {
                    val delayText = videoDelayLabel(context, display.videoWallpaperDelaySeconds)
                    CyclePreference(
                        title = stringResource(item.labelRes),
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

                is HomeScreenItem.VideoMuted -> {
                    SwitchPreference(
                        title = stringResource(item.labelRes),
                        subtitle = stringResource(R.string.settings_home_screen_video_muted_subtitle),
                        isEnabled = display.videoWallpaperMuted,
                        isFocused = isFocused(item),
                        onToggle = { viewModel.setVideoWallpaperMuted(it) }
                    )
                }

                is HomeScreenItem.LayoutPreview -> if (presentsPreview) Unit else Box(
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

                is HomeScreenItem.LayoutSelector -> HomeLayoutSelectorRow(
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
                        viewModel.adjustHomeLayoutField(item.field, direction)
                    },
                    onToggle = {
                        viewModel.setFocusIndex(homeScreenFocusIndexOf(item, display))
                        viewModel.setHomeLayout(toggleHomeLayoutField(display.homeLayout, item.field))
                    }
                )

                is HomeScreenItem.BoxArt -> SegmentedPreference(
                    title = stringResource(item.labelRes),
                    subtitle = stringResource(R.string.settings_home_screen_box_art_subtitle),
                    options = listOf(
                        stringResource(R.string.settings_home_screen_box_art_option_2d),
                        stringResource(R.string.settings_home_screen_box_art_option_3d)
                    ),
                    selectedIndex = if (display.homeLayout.boxArt3d) 1 else 0,
                    isFocused = isFocused(item),
                    onSelect = { viewModel.setHomeBoxArt3d(it == 1) }
                )

                is HomeScreenItem.InstalledOnly -> SwitchPreference(
                    title = stringResource(item.labelRes),
                    subtitle = stringResource(R.string.settings_home_screen_installed_only_subtitle),
                    isEnabled = display.installedOnlyHome,
                    isFocused = isFocused(item),
                    onToggle = { viewModel.setInstalledOnlyHome(it) }
                )

                is HomeScreenItem.RecentNewGames -> CyclePreference(
                    title = stringResource(item.labelRes),
                    subtitle = stringResource(R.string.settings_home_screen_recent_new_games_subtitle),
                    value = recentNewGamesLabel(context, display.recentNewGamesLimit),
                    isFocused = isFocused(item),
                    onClick = { viewModel.cycleRecentNewGamesLimit() },
                    onPrev = { viewModel.cycleRecentNewGamesLimit(-1) },
                    options = remember(context) {
                        RecentNewGamesLimit.STEPS.map { recentNewGamesLabel(context, it) }
                    },
                    onSelect = { index ->
                        val currentIndex = RecentNewGamesLimit.STEPS
                            .indexOf(display.recentNewGamesLimit).coerceAtLeast(0)
                        viewModel.cycleRecentNewGamesLimit(index - currentIndex)
                    },
                    pickerRequestToken = pickerToken(item)
                )

                is HomeScreenItem.NewBadge -> SegmentedPreference(
                    title = stringResource(item.labelRes),
                    subtitle = stringResource(R.string.settings_home_screen_new_badge_subtitle),
                    options = NewBadgePlacement.entries.map { stringResource(newBadgePlacementLabelRes(it)) },
                    selectedIndex = NewBadgePlacement.entries.indexOf(display.newBadgePlacement),
                    isFocused = isFocused(item),
                    onSelect = { index -> viewModel.setNewBadgePlacement(NewBadgePlacement.entries[index]) }
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

private fun recentNewGamesLabel(context: android.content.Context, limit: Int): String = when (limit) {
    RecentNewGamesLimit.ALL -> context.getString(R.string.settings_home_screen_recent_new_games_all)
    0 -> context.getString(R.string.settings_home_screen_recent_new_games_none)
    else -> context.resources.getQuantityString(
        R.plurals.settings_home_screen_recent_new_games_up_to,
        limit,
        limit
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

