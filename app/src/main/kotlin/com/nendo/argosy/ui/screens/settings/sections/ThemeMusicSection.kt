package com.nendo.argosy.ui.screens.settings.sections

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
import com.nendo.argosy.ui.components.NavigationPreference
import com.nendo.argosy.ui.components.SliderPreference
import com.nendo.argosy.ui.components.SwitchPreference
import com.nendo.argosy.ui.screens.settings.SettingsUiState
import com.nendo.argosy.ui.screens.settings.SettingsViewModel
import com.nendo.argosy.ui.screens.settings.components.SectionPaneLayout
import com.nendo.argosy.ui.screens.settings.menu.SettingsLayout
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
    val isFocusable: Boolean get() = when (this) {
        is Header, is SectionSpacer -> false
        else -> true
    }

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

    data object BgmToggle : ThemeMusicItem("bgmToggle", "music")
    data object BgmVolume : ThemeMusicItem("bgmVolume", "music", { it.bgmEnabled })
    data object BgmPlaylist : ThemeMusicItem("bgmPlaylist", "music", { it.bgmEnabled })
    data object BrowseServerMusic : ThemeMusicItem("browseServerMusic", "music", { it.bgmEnabled && it.musicApiSupported })
    data object BrowseLocalMusic : ThemeMusicItem("browseLocalMusic", "music", { it.bgmEnabled })
    data object MusicLocation : ThemeMusicItem("musicLocation", "storage", { it.bgmEnabled })
    data object BgmShuffle : ThemeMusicItem("bgmShuffle", "music", { it.bgmEnabled })
    data object GameThemeToggle : ThemeMusicItem("gameDetailTheme", "music", { it.bgmEnabled && it.musicApiSupported })

    companion object {
        private val StorageSpacer = SectionSpacer("storageSpacer", "storage", { it.bgmEnabled })
        private val StorageHeader =
            Header("storageHeader", "storage", R.string.settings_music_section_storage, { it.bgmEnabled })

        val ALL: List<ThemeMusicItem>
            get() = listOf(
                BgmToggle, BgmVolume, BgmPlaylist, BrowseServerMusic, BrowseLocalMusic, BgmShuffle, GameThemeToggle,
                StorageSpacer, StorageHeader, MusicLocation
            )
    }
}

private val themeMusicLayout = SettingsLayout<ThemeMusicItem, ThemeMusicLayoutState>(
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

            ThemeMusicItem.BgmToggle -> SwitchPreference(
                title = stringResource(R.string.settings_music_toggle_title),
                subtitle = stringResource(R.string.settings_music_toggle_subtitle),
                isEnabled = uiState.ambientAudio.enabled,
                isFocused = isFocused(item),
                onToggle = { viewModel.setAmbientAudioEnabled(it) }
            )

            ThemeMusicItem.BgmVolume -> {
                val volumeLevels = listOf(2, 5, 10, 20, 35)
                val currentIndex = volumeLevels.indexOfFirst { it >= uiState.ambientAudio.volume }.takeIf { it >= 0 } ?: 0
                val sliderValue = currentIndex + 1
                SliderPreference(
                    title = stringResource(R.string.settings_music_volume_title),
                    value = sliderValue,
                    minValue = 1,
                    maxValue = 5,
                    isFocused = isFocused(item),
                    onAdjust = { viewModel.adjustAmbientAudioVolume(if (it < 0) -1 else 1) }
                )
            }

            ThemeMusicItem.BgmPlaylist -> {
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
                    title = stringResource(R.string.settings_music_playlist_title),
                    subtitle = countLabel,
                    isFocused = isFocused(item),
                    onClick = { viewModel.openBgmPlaylistManager() }
                )
            }

            ThemeMusicItem.BrowseServerMusic -> NavigationPreference(
                icon = Icons.Outlined.LibraryMusic,
                title = stringResource(R.string.settings_music_browse_server_title),
                subtitle = stringResource(R.string.settings_music_browse_server_subtitle),
                isFocused = isFocused(item),
                onClick = { viewModel.openMusicBrowserBgm() }
            )

            ThemeMusicItem.BrowseLocalMusic -> NavigationPreference(
                icon = Icons.Outlined.FolderOpen,
                title = stringResource(R.string.settings_music_browse_local_title),
                subtitle = stringResource(R.string.settings_music_browse_local_subtitle),
                isFocused = isFocused(item),
                onClick = { viewModel.openBgmAddMusicBrowser() }
            )

            ThemeMusicItem.MusicLocation -> NavigationPreference(
                icon = Icons.Outlined.Folder,
                title = stringResource(R.string.settings_music_location_title),
                subtitle = uiState.ambientAudio.musicDirPath ?: "",
                isFocused = isFocused(item),
                onClick = { viewModel.openMusicLocationPicker() }
            )

            ThemeMusicItem.BgmShuffle -> SwitchPreference(
                title = stringResource(R.string.settings_music_shuffle_title),
                subtitle = stringResource(R.string.settings_music_shuffle_subtitle),
                isEnabled = uiState.ambientAudio.shuffle,
                isFocused = isFocused(item),
                onToggle = { viewModel.setAmbientAudioShuffle(it) }
            )

            ThemeMusicItem.GameThemeToggle -> SwitchPreference(
                title = stringResource(R.string.settings_music_game_themes_title),
                subtitle = stringResource(R.string.settings_music_game_themes_subtitle),
                isEnabled = uiState.ambientAudio.gameDetailThemeEnabled,
                isFocused = isFocused(item),
                onToggle = { viewModel.setGameDetailThemeEnabled(it) }
            )
        }
    }
}

