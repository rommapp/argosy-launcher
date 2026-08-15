package com.nendo.argosy.ui.screens.settings.sections

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.nendo.argosy.data.preferences.MediaAudioLanguage
import com.nendo.argosy.data.preferences.MediaDownloadQuality
import com.nendo.argosy.data.preferences.MediaStreamingQuality
import com.nendo.argosy.data.preferences.MediaSubtitleLanguage
import com.nendo.argosy.data.preferences.MediaSubtitleMode
import com.nendo.argosy.data.remote.jellyfin.JellyfinSyncProgress
import com.nendo.argosy.ui.components.ActionPreference
import com.nendo.argosy.ui.components.CyclePreference
import com.nendo.argosy.ui.components.NavigationPreference
import com.nendo.argosy.ui.components.SwitchPreference
import com.nendo.argosy.ui.screens.settings.JellyfinState
import com.nendo.argosy.ui.screens.settings.SettingsUiState
import com.nendo.argosy.ui.screens.settings.SettingsViewModel
import com.nendo.argosy.ui.screens.settings.components.JellyfinConfigForm
import com.nendo.argosy.ui.screens.settings.components.JellyfinSignInForm
import com.nendo.argosy.ui.screens.settings.components.SectionHeader
import com.nendo.argosy.ui.screens.settings.components.SectionPaneLayout
import com.nendo.argosy.ui.screens.settings.menu.SettingsLayout
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.util.formatClockDateTime

internal data class JellyfinLayoutState(
    val hasServer: Boolean,
    val isSignedIn: Boolean,
    val subtitlesEnabled: Boolean,
    val hasQuickConnectCode: Boolean,
    val hasSignInError: Boolean,
    val showPasswordFallback: Boolean
) {
    companion object {
        fun from(state: SettingsUiState): JellyfinLayoutState = from(state.jellyfin)

        fun from(jellyfin: JellyfinState): JellyfinLayoutState = JellyfinLayoutState(
            hasServer = jellyfin.hasServer,
            isSignedIn = jellyfin.isSignedIn,
            subtitlesEnabled = jellyfin.subtitleMode != MediaSubtitleMode.OFF,
            hasQuickConnectCode = jellyfin.hasQuickConnectCode,
            hasSignInError = jellyfin.signInError != null && !jellyfin.isSignedIn,
            showPasswordFallback = jellyfin.hasServer && !jellyfin.isSignedIn &&
                !jellyfin.quickConnectRequested && jellyfin.passwordFallbackOffered
        )
    }
}

internal sealed class JellyfinItem(
    val key: String,
    val section: String,
    val visibleWhen: (JellyfinLayoutState) -> Boolean = { true }
) {
    val isFocusable: Boolean get() = this !is Header && this !is QuickConnectCode &&
        this !is SignInError

    class Header(
        key: String,
        section: String,
        val title: String,
        visibleWhen: (JellyfinLayoutState) -> Boolean = { true }
    ) : JellyfinItem(key, section, visibleWhen)

    data object MediaServer : JellyfinItem("jellyfinServer", "server")
    data object Account : JellyfinItem("jellyfinAccount", "server", { it.hasServer })
    data object QuickConnectCode : JellyfinItem(
        "jellyfinQuickConnectCode", "server",
        { it.hasQuickConnectCode }
    )
    data object SignInError : JellyfinItem(
        "jellyfinSignInError", "server",
        { it.hasSignInError }
    )
    data object PasswordSignIn : JellyfinItem(
        "jellyfinPasswordSignIn", "server",
        { it.showPasswordFallback }
    )

    data object SyncLibrary : JellyfinItem("jellyfinSyncLibrary", "library", { it.hasServer })

    data object StreamingQuality : JellyfinItem("jellyfinStreamingQuality", "playback", { it.hasServer })
    data object AudioLanguage : JellyfinItem("jellyfinAudioLanguage", "playback", { it.hasServer })
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
                MediaServer, Account, QuickConnectCode, SignInError, PasswordSignIn,
                Header("jellyfinLibraryHeader", "library", "LIBRARY", { it.hasServer }),
                SyncLibrary,
                Header("jellyfinPlaybackHeader", "playback", "PLAYBACK", { it.hasServer }),
                StreamingQuality, AudioLanguage, Subtitles, SubtitleLanguage, BurnInSubtitles,
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
            "library" -> "LIBRARY"
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

private fun accountTitle(jellyfin: JellyfinState): String = when {
    jellyfin.isSignedIn -> "Sign Out"
    jellyfin.quickConnectRequested -> "Cancel Sign In"
    else -> "Sign In"
}

private fun accountSubtitle(jellyfin: JellyfinState): String = when {
    jellyfin.isSignedIn -> jellyfin.userName.takeIf { it.isNotBlank() }
        ?.let { "Signed in as $it" } ?: "Signed in"
    jellyfin.hasQuickConnectCode -> "Waiting for approval in your Jellyfin app"
    jellyfin.quickConnectRequested -> "Asking the server for a code"
    jellyfin.quickConnectAvailable -> "Approve a Quick Connect code from your Jellyfin app"
    else -> "Sign in with your Jellyfin username and password"
}

/**
 * What the row says about the library, in the order the user cares about: what is happening now,
 * then what stopped it happening, then when it last happened.
 */
private fun syncLibrarySubtitle(
    jellyfin: JellyfinState,
    progress: JellyfinSyncProgress,
    context: Context
): String = when {
    jellyfin.isSyncingLibrary && progress.currentLibrary.isNotBlank() && progress.librariesTotal > 0 ->
        "${progress.currentLibrary} (${progress.librariesDone + 1} of ${progress.librariesTotal})"
    jellyfin.isSyncingLibrary -> "Reading your libraries"
    !jellyfin.isSignedIn -> "Sign in to bring your movies and shows across"
    jellyfin.librarySyncError != null -> jellyfin.librarySyncError
    jellyfin.lastLibrarySync != null ->
        "Last: ${formatClockDateTime(context, jellyfin.lastLibrarySync.toEpochMilli())}"
    else -> "Bring your movies, shows and episodes in line with the server"
}

private fun subtitleModeSubtitle(mode: MediaSubtitleMode): String = when (mode) {
    MediaSubtitleMode.OFF -> "No subtitle track is selected"
    MediaSubtitleMode.FORCED_ONLY -> "Only tracks marked forced"
    MediaSubtitleMode.PREFERRED -> "Pick a track in your preferred language"
}

private fun streamingQualitySubtitle(quality: MediaStreamingQuality): String =
    if (quality == MediaStreamingQuality.AUTO) {
        "Plays every title as it is, which costs the server nothing"
    } else {
        "Titles larger than this are transcoded by the server as they play. " +
            "Anything already smaller plays as it is"
    }

private fun downloadQualitySubtitle(quality: MediaDownloadQuality): String =
    if (quality == MediaDownloadQuality.ORIGINAL) {
        "Downloads the file as it sits on the server"
    } else {
        "Titles larger than this are transcoded before the download starts. " +
            "Anything already smaller downloads as it is"
    }

@Composable
fun JellyfinSection(uiState: SettingsUiState, viewModel: SettingsViewModel) {
    when {
        uiState.jellyfin.configuring -> JellyfinConfigForm(uiState, viewModel)
        uiState.jellyfin.showLoginForm -> JellyfinSignInForm(uiState, viewModel)
        else -> JellyfinContent(uiState, viewModel)
    }
}

/**
 * The Quick Connect code is what the user types into a client that is already signed in, so it is
 * given the weight of the screen rather than a value slot on a settings row.
 */
@Composable
private fun QuickConnectCodePanel(code: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                LocalArgosyTheme.current.focusAccent.copy(alpha = 0.15f),
                RoundedCornerShape(Dimens.radiusMd)
            )
            .padding(Dimens.spacingMd),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
    ) {
        Text(
            text = "Quick Connect Code",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = code,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "Open Jellyfin on a device you are already signed in on, go to Quick Connect " +
                "and enter this code. It expires after a few minutes.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SignInErrorPanel(message: String) {
    Text(
        text = message,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.padding(horizontal = Dimens.spacingMd)
    )
}

@Composable
private fun JellyfinContent(uiState: SettingsUiState, viewModel: SettingsViewModel) {
    val jellyfin = uiState.jellyfin
    val layoutState = remember(jellyfin) { JellyfinLayoutState.from(jellyfin) }
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
                title = accountTitle(jellyfin),
                subtitle = accountSubtitle(jellyfin),
                isFocused = isFocused(item),
                isDangerous = jellyfin.isSignedIn,
                onClick = {
                    when {
                        jellyfin.isSignedIn -> viewModel.requestJellyfinSignOut()
                        jellyfin.quickConnectRequested -> viewModel.cancelJellyfinSignIn()
                        else -> viewModel.requestJellyfinSignIn()
                    }
                }
            )

            JellyfinItem.QuickConnectCode -> QuickConnectCodePanel(jellyfin.quickConnectCode)

            JellyfinItem.SignInError -> SignInErrorPanel(jellyfin.signInError.orEmpty())

            JellyfinItem.PasswordSignIn -> ActionPreference(
                icon = Icons.Default.Password,
                title = "Sign In With Password",
                subtitle = "Use your Jellyfin username and password instead of a code",
                isFocused = isFocused(item),
                onClick = { viewModel.showJellyfinLoginForm() }
            )

            JellyfinItem.SyncLibrary -> {
                val progress = viewModel.mediaSyncProgress.collectAsState().value
                val context = LocalContext.current
                ActionPreference(
                    icon = Icons.Default.Sync,
                    title = "Sync Library",
                    subtitle = syncLibrarySubtitle(jellyfin, progress, context),
                    isFocused = isFocused(item),
                    isEnabled = jellyfin.isSignedIn && !jellyfin.isSyncingLibrary,
                    iconTint = if (jellyfin.librarySyncError != null && !jellyfin.isSyncingLibrary) {
                        MaterialTheme.colorScheme.error
                    } else {
                        null
                    },
                    spinIcon = jellyfin.isSyncingLibrary,
                    onClick = { viewModel.syncJellyfinLibrary() }
                )
            }

            JellyfinItem.StreamingQuality -> CyclePreference(
                title = "Streaming Quality",
                value = jellyfin.streamingQuality.displayName,
                subtitle = streamingQualitySubtitle(jellyfin.streamingQuality),
                isFocused = isFocused(item),
                onClick = { viewModel.cycleJellyfinStreamingQuality(1) },
                onPrev = { viewModel.cycleJellyfinStreamingQuality(-1) },
                options = remember { MediaStreamingQuality.entries.map { it.displayName } },
                onSelect = { viewModel.setJellyfinStreamingQuality(MediaStreamingQuality.entries[it]) },
                pickerRequestToken = if (uiState.enumPickerKey == item.key) uiState.enumPickerToken else 0
            )

            JellyfinItem.AudioLanguage -> CyclePreference(
                title = "Audio Language",
                value = jellyfin.audioLanguage.displayName,
                subtitle = "Chosen first when a title carries more than one audio track",
                isFocused = isFocused(item),
                onClick = { viewModel.cycleJellyfinAudioLanguage(1) },
                onPrev = { viewModel.cycleJellyfinAudioLanguage(-1) },
                options = remember { MediaAudioLanguage.entries.map { it.displayName } },
                onSelect = { viewModel.setJellyfinAudioLanguage(MediaAudioLanguage.entries[it]) },
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
                    "New playbacks start with image tracks rendered by the server, which forces " +
                        "a transcode. Change it for a single title in the subtitle picker"
                } else {
                    "New playbacks start with image tracks off. Turn one on for a single title " +
                        "in the subtitle picker"
                },
                isEnabled = jellyfin.burnInImageSubtitles,
                isFocused = isFocused(item),
                onToggle = { viewModel.setJellyfinBurnInImageSubtitles(it) }
            )

            JellyfinItem.DownloadQuality -> CyclePreference(
                title = "Download Quality",
                value = jellyfin.downloadQuality.displayName,
                subtitle = downloadQualitySubtitle(jellyfin.downloadQuality),
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
