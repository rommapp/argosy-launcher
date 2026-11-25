package com.nendo.argosy.ui.screens.settings.sections

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.nendo.argosy.R
import com.nendo.argosy.ui.components.ActionPreference
import com.nendo.argosy.ui.components.NavigationPreference
import com.nendo.argosy.ui.components.SliderPreference
import com.nendo.argosy.ui.components.SwitchPreference
import com.nendo.argosy.ui.screens.settings.SettingsUiState
import com.nendo.argosy.ui.screens.settings.SettingsViewModel
import com.nendo.argosy.ui.screens.settings.components.SectionPaneLayout
import com.nendo.argosy.ui.screens.settings.delegates.VolumeLevels
import com.nendo.argosy.ui.screens.settings.delegates.levelIndexIn
import com.nendo.argosy.ui.screens.settings.menu.SettingsLayout
import com.nendo.argosy.ui.screens.settings.search.SearchableSetting
import com.nendo.argosy.ui.theme.Dimens

internal data class ThemeMusicLayoutState(
    val bgmEnabled: Boolean,
    val musicApiSupported: Boolean
) {
    companion object {
        fun from(state: SettingsUiState) = ThemeMusicLayoutState(
            bgmEnabled = state.ambientAudio.enabled,
            musicApiSupported = state.server.musicApiSupported
        )
    }
}

internal sealed class ThemeMusicItem(
    val key: String,
    val section: String,
    val visibleWhen: (ThemeMusicLayoutState) -> Boolean = { true }
) {
    val isFocusable: Boolean get() = this is SearchableSetting

    class Header(
        key: String,
        section: String,
        val titleRes: Int,
        visibleWhen: (ThemeMusicLayoutState) -> Boolean = { true }
    ) : ThemeMusicItem(key, section, visibleWhen)

    class SectionSpacer(
        key: String,
        section: String,
        visibleWhen: (ThemeMusicLayoutState) -> Boolean = { true }
    ) : ThemeMusicItem(key, section, visibleWhen)

    sealed class Row(
        key: String,
        section: String,
        @StringRes override val labelRes: Int,
        visibleWhen: (ThemeMusicLayoutState) -> Boolean = { true }
    ) : ThemeMusicItem(key, section, visibleWhen), SearchableSetting

    data object BgmToggle : Row("bgmToggle", "music", R.string.settings_music_toggle_title)
    data object BgmVolume : Row("bgmVolume", "music", R.string.settings_music_volume_title, { it.bgmEnabled })
    data object GameMusicVolume :
        Row("gameMusicVolume", "music", R.string.settings_music_game_volume_title, { it.bgmEnabled })
    data object BgmPlaylist :
        Row("bgmPlaylist", "music", R.string.settings_music_playlist_title, { it.bgmEnabled })
    data object BrowseServerMusic : Row(
        "browseServerMusic",
        "music",
        R.string.settings_music_browse_server_title,
        { it.bgmEnabled && it.musicApiSupported }
    )
    data object BrowseLocalMusic :
        Row("browseLocalMusic", "music", R.string.settings_music_browse_local_title, { it.bgmEnabled })
    data object MusicLocation :
        Row("musicLocation", "storage", R.string.settings_music_location_title, { it.bgmEnabled })
    data object BgmShuffle : Row("bgmShuffle", "music", R.string.settings_music_shuffle_title, { it.bgmEnabled })
    data object GameThemeToggle : Row(
        "gameDetailTheme",
        "music",
        R.string.settings_music_game_themes_title,
        { it.bgmEnabled && it.musicApiSupported }
    )

    companion object {
        private val StorageSpacer = SectionSpacer("storageSpacer", "storage", { it.bgmEnabled })
        private val StorageHeader =
            Header("storageHeader", "storage", R.string.settings_music_section_storage, { it.bgmEnabled })

        val ALL: List<ThemeMusicItem>
            get() = listOf(
                BgmToggle, BgmVolume, GameMusicVolume, BgmPlaylist, BrowseServerMusic, BrowseLocalMusic, BgmShuffle, GameThemeToggle,
                StorageSpacer, StorageHeader, MusicLocation
            )
    }
}

internal val themeMusicLayout = SettingsLayout<ThemeMusicItem, ThemeMusicLayoutState>(
    allItems = ThemeMusicItem.ALL,
    isFocusable = { it.isFocusable },
    visibleWhen = { item, state -> item.visibleWhen(state) },
    sectionOf = { it.section },
    sectionTitleRes = {
        when (it) {
            "music" -> R.string.settings_music_section_music
            "storage" -> R.string.settings_music_section_storage
            else -> null
        }
    }
)

internal fun themeMusicMaxFocusIndex(state: ThemeMusicLayoutState): Int =
    themeMusicLayout.maxFocusIndex(state)

internal fun themeMusicItemAtFocusIndex(index: Int, state: ThemeMusicLayoutState): ThemeMusicItem? =
    themeMusicLayout.itemAtFocusIndex(index, state)

internal fun themeMusicSections(state: ThemeMusicLayoutState) = themeMusicLayout.buildSections(state)

@Composable
fun ThemeMusicSection(uiState: SettingsUiState, viewModel: SettingsViewModel) {
    val bgmEnabled = uiState.ambientAudio.enabled
    val musicApiSupported = uiState.server.musicApiSupported
    val context = LocalContext.current

    val layoutState = remember(bgmEnabled, musicApiSupported) {
        ThemeMusicLayoutState(bgmEnabled, musicApiSupported)
    }

    val visibleItems = remember(layoutState) {
        themeMusicLayout.visibleItems(layoutState)
    }
    val sections = remember(layoutState, context) {
        themeMusicLayout.buildSections(layoutState, context)
    }

    fun isFocused(item: ThemeMusicItem): Boolean =
        uiState.focusedIndex == themeMusicLayout.focusIndexOf(item, layoutState)

    SectionPaneLayout(
        items = visibleItems,
        sections = sections,
        focusedIndex = uiState.focusedIndex,
        focusToListIndex = { themeMusicLayout.focusToListIndex(it, layoutState) },
        itemKey = { it.key },
        isNavItem = { it is ThemeMusicItem.SectionSpacer },
        isHeader = { it is ThemeMusicItem.Header },
        onSectionTap = { viewModel.setFocusIndex(it.focusStartIndex) },
        modifier = Modifier.fillMaxSize().padding(Dimens.spacingMd),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) { item ->
        when (item) {
            is ThemeMusicItem.Header -> Text(
                text = stringResource(item.titleRes).uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(vertical = Dimens.spacingXs)
            )

            is ThemeMusicItem.SectionSpacer -> Spacer(modifier = Modifier.height(Dimens.spacingMd))

            is ThemeMusicItem.BgmToggle -> SwitchPreference(
                title = stringResource(item.labelRes),
                subtitle = stringResource(R.string.settings_music_toggle_subtitle),
                isEnabled = uiState.ambientAudio.enabled,
                isFocused = isFocused(item),
                onToggle = { viewModel.setAmbientAudioEnabled(it) }
            )

            is ThemeMusicItem.BgmVolume -> {
                val sliderValue = levelIndexIn(uiState.ambientAudio.volume, VolumeLevels.AMBIENT_AUDIO) + 1
                SliderPreference(
                    title = stringResource(item.labelRes),
                    value = sliderValue,
                    minValue = 1,
                    maxValue = 5,
                    isFocused = isFocused(item),
                    onAdjust = { viewModel.adjustAmbientAudioVolume(if (it < 0) -1 else 1) }
                )
            }

            is ThemeMusicItem.GameMusicVolume -> SliderPreference(
                title = stringResource(item.labelRes),
                value = uiState.ambientAudio.gameVolume,
                minValue = VolumeLevels.GAME_MUSIC.first(),
                maxValue = VolumeLevels.GAME_MUSIC.last(),
                isFocused = isFocused(item),
                suffix = "%",
                onAdjust = { viewModel.adjustAmbientAudioGameVolume(if (it < 0) -1 else 1) }
            )

            is ThemeMusicItem.BgmPlaylist -> {
                val entryCount = uiState.ambientAudio.playlistEntryCount
                val countLabel = if (entryCount == 0) {
                    stringResource(R.string.settings_music_playlist_empty)
                } else {
                    pluralStringResource(
                        R.plurals.settings_music_playlist_count,
                        entryCount,
                        entryCount
                    )
                }
                NavigationPreference(
                    icon = Icons.Outlined.MusicNote,
                    title = stringResource(item.labelRes),
                    subtitle = countLabel,
                    isFocused = isFocused(item),
                    onClick = { viewModel.openBgmPlaylistManager() }
                )
            }

            is ThemeMusicItem.BrowseServerMusic -> NavigationPreference(
                icon = Icons.Outlined.LibraryMusic,
                title = stringResource(item.labelRes),
                subtitle = stringResource(R.string.settings_music_browse_server_subtitle),
                isFocused = isFocused(item),
                onClick = { viewModel.openMusicBrowserBgm() }
            )

            is ThemeMusicItem.BrowseLocalMusic -> NavigationPreference(
                icon = Icons.Outlined.FolderOpen,
                title = stringResource(item.labelRes),
                subtitle = stringResource(R.string.settings_music_browse_local_subtitle),
                isFocused = isFocused(item),
                onClick = { viewModel.openBgmAddMusicBrowser() }
            )

            is ThemeMusicItem.MusicLocation -> ActionPreference(
                icon = Icons.Outlined.Folder,
                title = stringResource(item.labelRes),
                subtitle = uiState.ambientAudio.musicDirPath ?: "",
                isFocused = isFocused(item),
                onClick = { viewModel.openMusicLocationPicker() },
                showResetButton = uiState.ambientAudio.isCustomMusicLocation,
                onReset = { viewModel.resetMusicLocation() }
            )

            is ThemeMusicItem.BgmShuffle -> SwitchPreference(
                title = stringResource(item.labelRes),
                subtitle = stringResource(R.string.settings_music_shuffle_subtitle),
                isEnabled = uiState.ambientAudio.shuffle,
                isFocused = isFocused(item),
                onToggle = { viewModel.setAmbientAudioShuffle(it) }
            )

            is ThemeMusicItem.GameThemeToggle -> SwitchPreference(
                title = stringResource(item.labelRes),
                subtitle = stringResource(R.string.settings_music_game_themes_subtitle),
                isEnabled = uiState.ambientAudio.gameDetailThemeEnabled,
                isFocused = isFocused(item),
                onToggle = { viewModel.setGameDetailThemeEnabled(it) }
            )
        }
    }
}

