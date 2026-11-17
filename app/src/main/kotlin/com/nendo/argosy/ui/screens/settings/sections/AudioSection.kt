package com.nendo.argosy.ui.screens.settings.sections

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.nendo.argosy.R
import com.nendo.argosy.ui.components.NavigationPreference
import com.nendo.argosy.ui.screens.settings.SettingsUiState
import com.nendo.argosy.ui.screens.settings.SettingsViewModel
import com.nendo.argosy.ui.screens.settings.components.SectionPaneLayout
import com.nendo.argosy.ui.screens.settings.menu.SettingsLayout
import com.nendo.argosy.ui.screens.settings.search.SearchableSetting
import com.nendo.argosy.ui.theme.Dimens

internal sealed class AudioItem(
    val key: String,
    val section: String,
    @StringRes override val labelRes: Int
) : SearchableSetting {
    data object Sounds : AudioItem("sounds", "audio", R.string.settings_audio_sounds_title)
    data object Music : AudioItem("music", "audio", R.string.settings_audio_music_title)

    companion object {
        val ALL: List<AudioItem>
            get() = listOf(Sounds, Music)
    }
}

internal val audioLayout = SettingsLayout<AudioItem, Unit>(
    allItems = AudioItem.ALL,
    isFocusable = { true },
    visibleWhen = { _, _ -> true },
    sectionOf = { it.section }
)

internal fun audioMaxFocusIndex(): Int = audioLayout.maxFocusIndex(Unit)

internal fun audioItemAtFocusIndex(index: Int): AudioItem? =
    audioLayout.itemAtFocusIndex(index, Unit)

internal fun audioSections() = audioLayout.buildSections(Unit)

internal fun audioFocusIndexOf(item: AudioItem): Int = audioLayout.focusIndexOf(item, Unit)

@Composable
fun AudioSection(uiState: SettingsUiState, viewModel: SettingsViewModel) {
    val visibleItems = remember { audioLayout.visibleItems(Unit) }
    val sections = remember { audioLayout.buildSections(Unit) }

    fun isFocused(item: AudioItem): Boolean =
        uiState.focusedIndex == audioLayout.focusIndexOf(item, Unit)

    fun openFrom(item: AudioItem, enter: () -> Unit) {
        viewModel.setFocusIndex(audioLayout.focusIndexOf(item, Unit))
        enter()
    }

    SectionPaneLayout(
        items = visibleItems,
        sections = sections,
        focusedIndex = uiState.focusedIndex,
        focusToListIndex = { audioLayout.focusToListIndex(it, Unit) },
        itemKey = { it.key },
        isNavItem = { false },
        onSectionTap = { viewModel.setFocusIndex(it.focusStartIndex) },
        modifier = Modifier.fillMaxSize().padding(Dimens.spacingMd),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) { item ->
        when (item) {
            is AudioItem.Sounds -> NavigationPreference(
                icon = Icons.Outlined.MusicNote,
                title = stringResource(item.labelRes),
                subtitle = if (uiState.sounds.enabled) {
                    stringResource(R.string.settings_audio_sounds_subtitle_on, uiState.sounds.volume)
                } else {
                    stringResource(R.string.settings_audio_sounds_subtitle_off)
                },
                isFocused = isFocused(item),
                onClick = { openFrom(item) { viewModel.navigateToThemeSounds() } }
            )

            is AudioItem.Music -> NavigationPreference(
                icon = Icons.Outlined.LibraryMusic,
                title = stringResource(item.labelRes),
                subtitle = if (uiState.ambientAudio.enabled) {
                    stringResource(R.string.settings_audio_music_subtitle_on, uiState.ambientAudio.volume)
                } else {
                    stringResource(R.string.settings_audio_music_subtitle_off)
                },
                isFocused = isFocused(item),
                onClick = { openFrom(item) { viewModel.navigateToThemeMusic() } }
            )
        }
    }
}
