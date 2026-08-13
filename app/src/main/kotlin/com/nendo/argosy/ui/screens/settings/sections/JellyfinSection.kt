package com.nendo.argosy.ui.screens.settings.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Login
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.nendo.argosy.data.preferences.MediaDownloadQuality
import com.nendo.argosy.data.preferences.MediaStreamingBitrate
import com.nendo.argosy.data.preferences.MediaSubtitleLanguage
import com.nendo.argosy.data.preferences.MediaSubtitleMode
import com.nendo.argosy.ui.components.ActionPreference
import com.nendo.argosy.ui.components.CyclePreference
import com.nendo.argosy.ui.components.NavigationPreference
import com.nendo.argosy.ui.components.SwitchPreference
import com.nendo.argosy.ui.screens.settings.JellyfinState
import com.nendo.argosy.ui.screens.settings.SettingsUiState
import com.nendo.argosy.ui.screens.settings.SettingsViewModel
import com.nendo.argosy.ui.screens.settings.components.JellyfinConfigForm
import com.nendo.argosy.ui.screens.settings.components.SectionHeader
import com.nendo.argosy.ui.screens.settings.components.SectionPaneLayout
import com.nendo.argosy.ui.screens.settings.menu.SettingsLayout
import com.nendo.argosy.ui.theme.Dimens

internal data class JellyfinLayoutState(
    val hasServer: Boolean,
    val isSignedIn: Boolean,
    val subtitlesEnabled: Boolean
) {
    companion object {
        fun from(state: SettingsUiState): JellyfinLayoutState = from(state.jellyfin)

        fun from(jellyfin: JellyfinState): JellyfinLayoutState = JellyfinLayoutState(
            hasServer = jellyfin.hasServer,
            isSignedIn = jellyfin.isSignedIn,
            subtitlesEnabled = jellyfin.subtitleMode != MediaSubtitleMode.OFF
        )
    }
}

internal sealed class JellyfinItem(
    val key: String,
    val section: String,
    val visibleWhen: (JellyfinLayoutState) -> Boolean = { true }
) {
    val isFocusable: Boolean get() = this !is Header

    class Header(
        key: String,
        section: String,
        val title: String,
        visibleWhen: (JellyfinLayoutState) -> Boolean = { true }
    ) : JellyfinItem(key, section, visibleWhen)

    data object MediaServer : JellyfinItem("jellyfinServer", "server")
    data object Account : JellyfinItem("jellyfinAccount", "server", { it.hasServer })

    data object StreamingBitrate : JellyfinItem("jellyfinBitrate", "playback", { it.hasServer })
    data object Subtitles : JellyfinItem("jellyfinSubtitles", "playback", { it.hasServer })
    data object SubtitleLanguage : JellyfinItem(
        "jellyfinSubtitleLanguage", "playback",
        { it.hasServer && it.subtitlesEnabled }
    )
    data object BurnInSubtitles : JellyfinItem(
        "jellyfinBurnInSubtitles", "playback",
        { it.hasServer && it.subtitlesEnabled }
    )

    data object DownloadQuality : JellyfinItem("jellyfinDownloadQuality", "downloads", { it.hasServer })
    data object MediaLocation : JellyfinItem("jellyfinMediaLocation", "downloads", { it.hasServer })

    data object SharePresence : JellyfinItem("jellyfinSharePresence", "privacy", { it.hasServer })

    companion object {
        val ALL: List<JellyfinItem>
            get() = listOf(
                Header("jellyfinServerHeader", "server", "SERVER"),
                MediaServer, Account,
                Header("jellyfinPlaybackHeader", "playback", "PLAYBACK", { it.hasServer }),
                StreamingBitrate, Subtitles, SubtitleLanguage, BurnInSubtitles,
                Header("jellyfinDownloadsHeader", "downloads", "DOWNLOADS", { it.hasServer }),
                DownloadQuality, MediaLocation,
                Header("jellyfinPrivacyHeader", "privacy", "PRIVACY", { it.hasServer }),
                SharePresence
            )
    }
}

private val jellyfinLayout = SettingsLayout<JellyfinItem, JellyfinLayoutState>(
    allItems = JellyfinItem.ALL,
    isFocusable = { it.isFocusable },
    visibleWhen = { item, state -> item.visibleWhen(state) },
    sectionOf = { it.section },
    sectionTitle = {
        when (it) {
            "server" -> "SERVER"
            "playback" -> "PLAYBACK"
            "downloads" -> "DOWNLOADS"
            "privacy" -> "PRIVACY"
            else -> null
        }
    }
)

internal fun jellyfinItemAtFocusIndex(index: Int, state: JellyfinLayoutState): JellyfinItem? =
    jellyfinLayout.itemAtFocusIndex(index, state)

internal fun jellyfinMaxFocusIndex(state: JellyfinLayoutState): Int =
    jellyfinLayout.maxFocusIndex(state)

internal fun jellyfinSections(state: JellyfinLayoutState) = jellyfinLayout.buildSections(state)

internal fun jellyfinFocusIndexOf(item: JellyfinItem, state: JellyfinLayoutState): Int =
    jellyfinLayout.focusIndexOf(item, state)

private fun accountSubtitle(jellyfin: JellyfinState): String = when {
    jellyfin.isSignedIn -> jellyfin.userName.takeIf { it.isNotBlank() }
        ?.let { "Signed in as $it" } ?: "Signed in"
    jellyfin.quickConnectRequested -> "Waiting for approval in your Jellyfin app"
    else -> "Approve a Quick Connect code from your Jellyfin app"
}

private fun subtitleModeSubtitle(mode: MediaSubtitleMode): String = when (mode) {
    MediaSubtitleMode.OFF -> "No subtitle track is selected"
    MediaSubtitleMode.FORCED_ONLY -> "Only tracks marked forced"
    MediaSubtitleMode.PREFERRED -> "Pick a track in your preferred language"
}

@Composable
fun JellyfinSection(uiState: SettingsUiState, viewModel: SettingsViewModel) {
    if (uiState.jellyfin.configuring) {
        JellyfinConfigForm(uiState, viewModel)
    } else {
        JellyfinContent(uiState, viewModel)
    }
}

@Composable
private fun JellyfinContent(uiState: SettingsUiState, viewModel: SettingsViewModel) {
    val jellyfin = uiState.jellyfin
    val layoutState = remember(jellyfin.serverUrl, jellyfin.isSignedIn, jellyfin.subtitleMode) {
        JellyfinLayoutState.from(jellyfin)
    }
    val visibleItems = remember(layoutState) { jellyfinLayout.visibleItems(layoutState) }
    val sections = remember(layoutState) { jellyfinLayout.buildSections(layoutState) }

    fun isFocused(item: JellyfinItem): Boolean =
        uiState.focusedIndex == jellyfinLayout.focusIndexOf(item, layoutState)

    SectionPaneLayout(
        items = visibleItems,
        sections = sections,
        focusedIndex = uiState.focusedIndex,
        focusToListIndex = { jellyfinLayout.focusToListIndex(it, layoutState) },
        itemKey = { it.key },
        isNavItem = { false },
        isHeader = { it is JellyfinItem.Header },
        onSectionTap = { viewModel.setFocusIndex(it.focusStartIndex) },
        modifier = Modifier.fillMaxSize().padding(Dimens.spacingMd),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) { item ->
        when (item) {
            is JellyfinItem.Header -> {
                if (item.key != "jellyfinServerHeader") {
                    Spacer(modifier = Modifier.height(Dimens.spacingSm))
                }
                SectionHeader(item.title)
            }

            JellyfinItem.MediaServer -> NavigationPreference(
                icon = Icons.Default.Dns,
                title = "Media Server",
                subtitle = jellyfin.serverUrl.ifBlank { "Not configured" },
                isFocused = isFocused(item),
                onClick = { viewModel.startJellyfinConfig() }
            )

            JellyfinItem.Account -> ActionPreference(
                icon = if (jellyfin.isSignedIn) Icons.AutoMirrored.Filled.Logout else Icons.Default.Login,
                title = if (jellyfin.isSignedIn) "Sign Out" else "Sign In",
                subtitle = accountSubtitle(jellyfin),
                isFocused = isFocused(item),
                isDangerous = jellyfin.isSignedIn,
                onClick = {
                    if (jellyfin.isSignedIn) viewModel.requestJellyfinSignOut()
                    else viewModel.requestJellyfinQuickConnect()
                }
            )

            JellyfinItem.StreamingBitrate -> CyclePreference(
                title = "Max Streaming Quality",
                value = jellyfin.maxStreamingBitrate.displayName,
                subtitle = "Ceiling the server transcodes down to",
                isFocused = isFocused(item),
                onClick = { viewModel.cycleJellyfinStreamingBitrate(1) },
                onPrev = { viewModel.cycleJellyfinStreamingBitrate(-1) },
                options = remember { MediaStreamingBitrate.entries.map { it.displayName } },
                onSelect = { viewModel.setJellyfinStreamingBitrate(MediaStreamingBitrate.entries[it]) },
                pickerRequestToken = if (uiState.enumPickerKey == item.key) uiState.enumPickerToken else 0
            )

            JellyfinItem.Subtitles -> CyclePreference(
                title = "Subtitles",
                value = jellyfin.subtitleMode.displayName,
                subtitle = subtitleModeSubtitle(jellyfin.subtitleMode),
                isFocused = isFocused(item),
                onClick = { viewModel.cycleJellyfinSubtitleMode(1) },
                onPrev = { viewModel.cycleJellyfinSubtitleMode(-1) },
                options = remember { MediaSubtitleMode.entries.map { it.displayName } },
                onSelect = { viewModel.setJellyfinSubtitleMode(MediaSubtitleMode.entries[it]) },
                pickerRequestToken = if (uiState.enumPickerKey == item.key) uiState.enumPickerToken else 0
            )

            JellyfinItem.SubtitleLanguage -> CyclePreference(
                title = "Subtitle Language",
                value = jellyfin.subtitleLanguage.displayName,
                isFocused = isFocused(item),
                onClick = { viewModel.cycleJellyfinSubtitleLanguage(1) },
                onPrev = { viewModel.cycleJellyfinSubtitleLanguage(-1) },
                options = remember { MediaSubtitleLanguage.entries.map { it.displayName } },
                onSelect = { viewModel.setJellyfinSubtitleLanguage(MediaSubtitleLanguage.entries[it]) },
                pickerRequestToken = if (uiState.enumPickerKey == item.key) uiState.enumPickerToken else 0
            )

            JellyfinItem.BurnInSubtitles -> SwitchPreference(
                title = "Burn In Image Subtitles",
                subtitle = if (jellyfin.burnInImageSubtitles) {
                    "Image tracks are rendered by the server, which forces a transcode"
                } else {
                    "Image tracks are skipped"
                },
                isEnabled = jellyfin.burnInImageSubtitles,
                isFocused = isFocused(item),
                onToggle = { viewModel.setJellyfinBurnInImageSubtitles(it) }
            )

            JellyfinItem.DownloadQuality -> CyclePreference(
                title = "Download Quality",
                value = jellyfin.downloadQuality.displayName,
                subtitle = if (jellyfin.downloadQuality == MediaDownloadQuality.ORIGINAL) {
                    "Downloads the file as it sits on the server"
                } else {
                    "The server prepares a smaller copy before the download starts"
                },
                isFocused = isFocused(item),
                onClick = { viewModel.cycleJellyfinDownloadQuality(1) },
                onPrev = { viewModel.cycleJellyfinDownloadQuality(-1) },
                options = remember { MediaDownloadQuality.entries.map { it.displayName } },
                onSelect = { viewModel.setJellyfinDownloadQuality(MediaDownloadQuality.entries[it]) },
                pickerRequestToken = if (uiState.enumPickerKey == item.key) uiState.enumPickerToken else 0
            )

            JellyfinItem.MediaLocation -> ActionPreference(
                icon = Icons.Default.Folder,
                title = "Media Location",
                subtitle = jellyfin.mediaDirPath ?: "Internal storage",
                isFocused = isFocused(item),
                onClick = { viewModel.openMediaLocationPicker() }
            )

            JellyfinItem.SharePresence -> SwitchPreference(
                title = "Show What You're Watching",
                subtitle = if (jellyfin.sharePresence) {
                    "Share the title you are watching with friends"
                } else {
                    "Hide media activity from friends"
                },
                isEnabled = jellyfin.sharePresence,
                isFocused = isFocused(item),
                onToggle = { viewModel.setJellyfinSharePresence(it) }
            )
        }
    }
}
