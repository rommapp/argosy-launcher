package com.nendo.argosy.ui.screens.settings.sections

import android.content.Context
import androidx.annotation.StringRes
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
import androidx.compose.ui.res.stringResource
import com.nendo.argosy.R
import com.nendo.argosy.data.preferences.MediaAudioLanguage
import com.nendo.argosy.data.preferences.MediaDownloadQuality
import com.nendo.argosy.data.preferences.MediaStreamingQuality
import com.nendo.argosy.data.preferences.MediaSubtitleLanguage
import com.nendo.argosy.data.preferences.MediaSubtitleMode
import com.nendo.argosy.data.remote.jellyfin.JellyfinSyncProgress
import com.nendo.argosy.ui.common.labelRes
import com.nendo.argosy.ui.components.ActionPreference
import com.nendo.argosy.ui.components.CyclePreference
import com.nendo.argosy.ui.components.NavigationPreference
import com.nendo.argosy.ui.components.SegmentedPreference
import com.nendo.argosy.ui.components.SwitchPreference
import com.nendo.argosy.ui.screens.settings.JellyfinState
import com.nendo.argosy.ui.screens.settings.SettingsUiState
import com.nendo.argosy.ui.screens.settings.SettingsViewModel
import com.nendo.argosy.ui.screens.settings.components.JellyfinConfigForm
import com.nendo.argosy.ui.screens.settings.components.JellyfinSignInForm
import com.nendo.argosy.ui.screens.settings.components.SectionHeader
import com.nendo.argosy.ui.screens.settings.components.SectionPaneLayout
import com.nendo.argosy.ui.screens.settings.menu.SettingsLayout
import com.nendo.argosy.ui.screens.settings.search.SearchableSetting
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.util.formatClockDateTime
import com.nendo.argosy.ui.screens.settings.components.ClientCertificatePreference
import com.nendo.argosy.data.remote.ssl.ClientCertTarget

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
    val isFocusable: Boolean get() = this is SearchableSetting

    class Header(
        key: String,
        section: String,
        val titleRes: Int,
        visibleWhen: (JellyfinLayoutState) -> Boolean = { true }
    ) : JellyfinItem(key, section, visibleWhen)

    sealed class Row(
        key: String,
        section: String,
        @StringRes override val labelRes: Int,
        visibleWhen: (JellyfinLayoutState) -> Boolean = { true }
    ) : JellyfinItem(key, section, visibleWhen), SearchableSetting

    data object MediaServer : Row("jellyfinServer", "server", R.string.settings_jellyfin_server_title)
    data object Account : Row(
        "jellyfinAccount", "server", R.string.settings_search_label_jellyfin_account,
        { it.hasServer }
    )
    data object QuickConnectCode : JellyfinItem(
        "jellyfinQuickConnectCode", "server",
        { it.hasQuickConnectCode }
    )
    data object SignInError : JellyfinItem(
        "jellyfinSignInError", "server",
        { it.hasSignInError }
    )
    data object PasswordSignIn : Row(
        "jellyfinPasswordSignIn", "server", R.string.settings_jellyfin_password_sign_in_title,
        { it.showPasswordFallback }
    )
    data object ClientCertificate :
        Row("jellyfinClientCertificate", "server", R.string.settings_client_cert_row_title)

    data object SyncLibrary :
        Row("jellyfinSyncLibrary", "library", R.string.settings_jellyfin_sync_title, { it.hasServer })

    data object StreamingQuality : Row(
        "jellyfinStreamingQuality", "playback", R.string.settings_jellyfin_streaming_title,
        { it.hasServer }
    )
    data object AudioLanguage : Row(
        "jellyfinAudioLanguage", "playback", R.string.settings_jellyfin_audio_language_title,
        { it.hasServer }
    )
    data object Subtitles : Row(
        "jellyfinSubtitles", "playback", R.string.settings_jellyfin_subtitles_title,
        { it.hasServer }
    )
    data object SubtitleLanguage : Row(
        "jellyfinSubtitleLanguage", "playback", R.string.settings_jellyfin_subtitle_language_title,
        { it.hasServer && it.subtitlesEnabled }
    )
    data object BurnInSubtitles : Row(
        "jellyfinBurnInSubtitles", "playback", R.string.settings_jellyfin_burn_in_title,
        { it.hasServer && it.subtitlesEnabled }
    )
    data object ConfirmPlayerExit : Row(
        "jellyfinConfirmPlayerExit", "playback", R.string.settings_jellyfin_confirm_exit_title,
        { it.hasServer }
    )

    data object DownloadQuality : Row(
        "jellyfinDownloadQuality", "downloads", R.string.settings_jellyfin_download_quality_title,
        { it.hasServer }
    )
    data object MediaLocation : Row(
        "jellyfinMediaLocation", "downloads", R.string.settings_jellyfin_media_location_title,
        { it.hasServer }
    )

    data object SharePresence : Row(
        "jellyfinSharePresence", "privacy", R.string.settings_jellyfin_share_presence_title,
        { it.hasServer }
    )

    companion object {
        val ALL: List<JellyfinItem>
            get() = listOf(
                Header("jellyfinServerHeader", "server", R.string.settings_jellyfin_section_server),
                MediaServer, Account, QuickConnectCode, SignInError, PasswordSignIn, ClientCertificate,
                Header("jellyfinLibraryHeader", "library", R.string.settings_jellyfin_section_library, { it.hasServer }),
                SyncLibrary,
                Header("jellyfinPlaybackHeader", "playback", R.string.settings_jellyfin_section_playback, { it.hasServer }),
                StreamingQuality, AudioLanguage, Subtitles, SubtitleLanguage, BurnInSubtitles,
                ConfirmPlayerExit,
                Header("jellyfinDownloadsHeader", "downloads", R.string.settings_jellyfin_section_downloads, { it.hasServer }),
                DownloadQuality, MediaLocation,
                Header("jellyfinPrivacyHeader", "privacy", R.string.settings_jellyfin_section_privacy, { it.hasServer }),
                SharePresence
            )
    }
}

internal val jellyfinLayout = SettingsLayout<JellyfinItem, JellyfinLayoutState>(
    allItems = JellyfinItem.ALL,
    isFocusable = { it.isFocusable },
    visibleWhen = { item, state -> item.visibleWhen(state) },
    sectionOf = { it.section },
    sectionTitleRes = {
        when (it) {
            "server" -> R.string.settings_jellyfin_section_server
            "library" -> R.string.settings_jellyfin_section_library
            "playback" -> R.string.settings_jellyfin_section_playback
            "downloads" -> R.string.settings_jellyfin_section_downloads
            "privacy" -> R.string.settings_jellyfin_section_privacy
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

private fun accountTitleRes(jellyfin: JellyfinState): Int = when {
    jellyfin.isSignedIn -> R.string.settings_jellyfin_account_sign_out
    jellyfin.quickConnectRequested -> R.string.settings_jellyfin_account_cancel
    else -> R.string.settings_jellyfin_account_sign_in
}

private fun accountSubtitle(context: Context, jellyfin: JellyfinState): String = when {
    jellyfin.isSignedIn -> jellyfin.userName.takeIf { it.isNotBlank() }
        ?.let { context.getString(R.string.settings_jellyfin_account_signed_in_as, it) }
        ?: context.getString(R.string.settings_jellyfin_account_signed_in)
    jellyfin.hasQuickConnectCode ->
        context.getString(R.string.settings_jellyfin_account_awaiting_approval)
    jellyfin.quickConnectRequested ->
        context.getString(R.string.settings_jellyfin_account_requesting_code)
    jellyfin.quickConnectAvailable ->
        context.getString(R.string.settings_jellyfin_account_quick_connect)
    else -> context.getString(R.string.settings_jellyfin_account_password)
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
        context.getString(
            R.string.settings_jellyfin_sync_progress,
            progress.currentLibrary,
            progress.librariesDone + 1,
            progress.librariesTotal
        )
    jellyfin.isSyncingLibrary -> context.getString(R.string.settings_jellyfin_sync_reading)
    !jellyfin.isSignedIn -> context.getString(R.string.settings_jellyfin_sync_signed_out)
    jellyfin.librarySyncError != null -> jellyfin.librarySyncError
    jellyfin.lastLibrarySync != null -> context.getString(
        R.string.settings_jellyfin_sync_last,
        formatClockDateTime(context, jellyfin.lastLibrarySync.toEpochMilli())
    )
    else -> context.getString(R.string.settings_jellyfin_sync_subtitle)
}

private fun subtitleModeSubtitleRes(mode: MediaSubtitleMode): Int = when (mode) {
    MediaSubtitleMode.OFF -> R.string.settings_jellyfin_subtitles_subtitle_off
    MediaSubtitleMode.FORCED_ONLY -> R.string.settings_jellyfin_subtitles_subtitle_forced
    MediaSubtitleMode.PREFERRED -> R.string.settings_jellyfin_subtitles_subtitle_preferred
}

private fun streamingQualitySubtitleRes(quality: MediaStreamingQuality): Int =
    if (quality == MediaStreamingQuality.AUTO) {
        R.string.settings_jellyfin_streaming_subtitle_auto
    } else {
        R.string.settings_jellyfin_streaming_subtitle_capped
    }

private fun downloadQualitySubtitleRes(quality: MediaDownloadQuality): Int =
    if (quality == MediaDownloadQuality.ORIGINAL) {
        R.string.settings_jellyfin_download_subtitle_original
    } else {
        R.string.settings_jellyfin_download_subtitle_capped
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
            text = stringResource(R.string.settings_jellyfin_quick_connect_label),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = code,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = stringResource(R.string.settings_jellyfin_quick_connect_message),
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
    val context = LocalContext.current
    val layoutState = remember(jellyfin) { JellyfinLayoutState.from(jellyfin) }
    val visibleItems = remember(layoutState) { jellyfinLayout.visibleItems(layoutState) }
    val sections = remember(layoutState, context) { jellyfinLayout.buildSections(layoutState, context) }

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
                SectionHeader(stringResource(item.titleRes))
            }

            is JellyfinItem.MediaServer -> NavigationPreference(
                icon = Icons.Default.Dns,
                title = stringResource(item.labelRes),
                subtitle = jellyfin.serverUrl.ifBlank {
                    stringResource(R.string.settings_jellyfin_server_unconfigured)
                },
                isFocused = isFocused(item),
                onClick = { viewModel.startJellyfinConfig() }
            )

            is JellyfinItem.Account -> ActionPreference(
                icon = if (jellyfin.isSignedIn) Icons.AutoMirrored.Filled.Logout else Icons.Default.Login,
                title = stringResource(accountTitleRes(jellyfin)),
                subtitle = accountSubtitle(context, jellyfin),
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

            is JellyfinItem.QuickConnectCode -> QuickConnectCodePanel(jellyfin.quickConnectCode)

            is JellyfinItem.SignInError -> SignInErrorPanel(jellyfin.signInError.orEmpty())

            is JellyfinItem.PasswordSignIn -> ActionPreference(
                icon = Icons.Default.Password,
                title = stringResource(item.labelRes),
                subtitle = stringResource(R.string.settings_jellyfin_password_sign_in_subtitle),
                isFocused = isFocused(item),
                onClick = { viewModel.showJellyfinLoginForm() }
            )

            is JellyfinItem.ClientCertificate ->
                ClientCertificatePreference(
                    summary = uiState.clientCert.summary,
                    isFocused = isFocused(item),
                    onClick = {
                        viewModel.openClientCertManager(ClientCertTarget.JELLYFIN)
                    }
                )

            is JellyfinItem.SyncLibrary -> {
                val progress = viewModel.mediaSyncProgress.collectAsState().value
                ActionPreference(
                    icon = Icons.Default.Sync,
                    title = stringResource(item.labelRes),
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

            is JellyfinItem.StreamingQuality -> SegmentedPreference(
                title = stringResource(item.labelRes),
                subtitle = stringResource(streamingQualitySubtitleRes(jellyfin.streamingQuality)),
                options = MediaStreamingQuality.entries.map { stringResource(it.labelRes) },
                selectedIndex = MediaStreamingQuality.entries.indexOf(jellyfin.streamingQuality),
                isFocused = isFocused(item),
                onSelect = { viewModel.setJellyfinStreamingQuality(MediaStreamingQuality.entries[it]) }
            )

            is JellyfinItem.AudioLanguage -> CyclePreference(
                title = stringResource(item.labelRes),
                value = stringResource(jellyfin.audioLanguage.labelRes),
                subtitle = stringResource(R.string.settings_jellyfin_audio_language_subtitle),
                isFocused = isFocused(item),
                onClick = { viewModel.cycleJellyfinAudioLanguage(1) },
                onPrev = { viewModel.cycleJellyfinAudioLanguage(-1) },
                options = MediaAudioLanguage.entries.map { stringResource(it.labelRes) },
                onSelect = { viewModel.setJellyfinAudioLanguage(MediaAudioLanguage.entries[it]) },
                pickerRequestToken = if (uiState.enumPickerKey == item.key) uiState.enumPickerToken else 0
            )

            is JellyfinItem.Subtitles -> CyclePreference(
                title = stringResource(item.labelRes),
                value = stringResource(jellyfin.subtitleMode.labelRes),
                subtitle = stringResource(subtitleModeSubtitleRes(jellyfin.subtitleMode)),
                isFocused = isFocused(item),
                onClick = { viewModel.cycleJellyfinSubtitleMode(1) },
                onPrev = { viewModel.cycleJellyfinSubtitleMode(-1) },
                options = MediaSubtitleMode.entries.map { stringResource(it.labelRes) },
                onSelect = { viewModel.setJellyfinSubtitleMode(MediaSubtitleMode.entries[it]) },
                pickerRequestToken = if (uiState.enumPickerKey == item.key) uiState.enumPickerToken else 0
            )

            is JellyfinItem.SubtitleLanguage -> CyclePreference(
                title = stringResource(item.labelRes),
                value = stringResource(jellyfin.subtitleLanguage.labelRes),
                isFocused = isFocused(item),
                onClick = { viewModel.cycleJellyfinSubtitleLanguage(1) },
                onPrev = { viewModel.cycleJellyfinSubtitleLanguage(-1) },
                options = MediaSubtitleLanguage.entries.map { stringResource(it.labelRes) },
                onSelect = { viewModel.setJellyfinSubtitleLanguage(MediaSubtitleLanguage.entries[it]) },
                pickerRequestToken = if (uiState.enumPickerKey == item.key) uiState.enumPickerToken else 0
            )

            is JellyfinItem.BurnInSubtitles -> SwitchPreference(
                title = stringResource(item.labelRes),
                subtitle = if (jellyfin.burnInImageSubtitles) {
                    stringResource(R.string.settings_jellyfin_burn_in_subtitle_on)
                } else {
                    stringResource(R.string.settings_jellyfin_burn_in_subtitle_off)
                },
                isEnabled = jellyfin.burnInImageSubtitles,
                isFocused = isFocused(item),
                onToggle = { viewModel.setJellyfinBurnInImageSubtitles(it) }
            )

            is JellyfinItem.ConfirmPlayerExit -> SwitchPreference(
                title = stringResource(item.labelRes),
                subtitle = if (jellyfin.confirmPlayerExit) {
                    stringResource(R.string.settings_jellyfin_confirm_exit_subtitle_on)
                } else {
                    stringResource(R.string.settings_jellyfin_confirm_exit_subtitle_off)
                },
                isEnabled = jellyfin.confirmPlayerExit,
                isFocused = isFocused(item),
                onToggle = { viewModel.setJellyfinConfirmPlayerExit(it) }
            )

            is JellyfinItem.DownloadQuality -> CyclePreference(
                title = stringResource(item.labelRes),
                value = stringResource(jellyfin.downloadQuality.labelRes),
                subtitle = stringResource(downloadQualitySubtitleRes(jellyfin.downloadQuality)),
                isFocused = isFocused(item),
                onClick = { viewModel.cycleJellyfinDownloadQuality(1) },
                onPrev = { viewModel.cycleJellyfinDownloadQuality(-1) },
                options = MediaDownloadQuality.entries.map { stringResource(it.labelRes) },
                onSelect = { viewModel.setJellyfinDownloadQuality(MediaDownloadQuality.entries[it]) },
                pickerRequestToken = if (uiState.enumPickerKey == item.key) uiState.enumPickerToken else 0
            )

            is JellyfinItem.MediaLocation -> ActionPreference(
                icon = Icons.Default.Folder,
                title = stringResource(item.labelRes),
                subtitle = jellyfin.mediaDirPath
                    ?: stringResource(R.string.settings_jellyfin_media_location_internal),
                isFocused = isFocused(item),
                onClick = { viewModel.openMediaLocationPicker() }
            )

            is JellyfinItem.SharePresence -> SwitchPreference(
                title = stringResource(item.labelRes),
                subtitle = if (jellyfin.sharePresence) {
                    stringResource(R.string.settings_jellyfin_share_presence_subtitle_on)
                } else {
                    stringResource(R.string.settings_jellyfin_share_presence_subtitle_off)
                },
                isEnabled = jellyfin.sharePresence,
                isFocused = isFocused(item),
                onToggle = { viewModel.setJellyfinSharePresence(it) }
            )
        }
    }
}
