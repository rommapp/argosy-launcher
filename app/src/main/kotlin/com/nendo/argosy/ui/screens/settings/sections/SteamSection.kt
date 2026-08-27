package com.nendo.argosy.ui.screens.settings.sections

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GetApp
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.nendo.argosy.R
import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.data.steam.LibrarySyncState
import com.nendo.argosy.data.steam.SteamConnectionState
import com.nendo.argosy.ui.components.ActionPreference
import com.nendo.argosy.ui.components.DualActionPreference
import com.nendo.argosy.ui.components.InfoPreference
import com.nendo.argosy.ui.components.Modal
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.input.ModalInputEffect
import com.nendo.argosy.ui.primitives.ModalActionButton
import com.nendo.argosy.ui.screens.settings.InstalledSteamLauncher
import com.nendo.argosy.ui.screens.settings.NotInstalledSteamLauncher
import com.nendo.argosy.ui.screens.settings.SettingsUiState
import com.nendo.argosy.ui.screens.settings.SettingsViewModel
import com.nendo.argosy.ui.screens.settings.SteamSettingsState
import com.nendo.argosy.ui.screens.settings.components.GameNativeFoldersModal
import com.nendo.argosy.ui.screens.settings.components.SectionPaneLayout
import com.nendo.argosy.ui.screens.settings.components.SteamLauncherPreference
import com.nendo.argosy.ui.screens.settings.menu.SettingsLayout
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme

private const val GN_PACKAGE = "app.gamenative"

internal sealed class SteamItem(val key: String, val section: String) {
    val isFocusable: Boolean get() = when (this) {
        is Header, is SectionSpacer, is StorageNote -> false
        else -> true
    }

    class Header(key: String, section: String, val titleRes: Int) : SteamItem(key, section)

    class SectionSpacer(key: String, section: String) : SteamItem(key, section)

    data object PreLogin : SteamItem("preLogin", "login")
    data object GnStatus : SteamItem("gnStatus", "setup")
    data object GnInstall : SteamItem("gnInstall", "setup")
    data object GnStorageWarning : SteamItem("gnStorageWarning", "setup")
    data object InstallPath : SteamItem("installPath", "setup")
    data object InstallTriage : SteamItem("installTriage", "setup")
    data object AccountInfo : SteamItem("accountInfo", "account")
    data object SyncLibrary : SteamItem("syncLibrary", "library")
    data object AddManual : SteamItem("addManual", "library")
    data object GameNativeLibrary : SteamItem("gameNativeLibrary", "setup")
    data class InstalledLauncher(val data: InstalledSteamLauncher) :
        SteamItem("steamLauncher_${data.packageName}", "library")
    data object RefreshMetadata : SteamItem("refreshMetadata", "library")
    data class NotInstalledLauncher(val data: NotInstalledSteamLauncher) :
        SteamItem("steamLauncherInstall_${data.emulatorId}", "library")
    data object StorageNote : SteamItem("steamStorageNote", "library")
    data object Disconnect : SteamItem("disconnect", "danger")
    data object ResetLibrary : SteamItem("resetLibrary", "danger")
}

/**
 * The launcher rows survive a logged-out Steam account: GameHub and friends are scannable
 * without ever signing in, so they hang off the library group in both modes.
 */
internal fun steamVisibleLaunchers(steam: SteamSettingsState): List<InstalledSteamLauncher> =
    if (isLoggedIn(steam)) {
        steam.installedLaunchers.filter { it.packageName != GN_PACKAGE }
    } else {
        steam.installedLaunchers
    }

internal fun buildSteamItems(steam: SteamSettingsState): List<SteamItem> = buildList {
    val loggedIn = isLoggedIn(steam)
    val gnConfigured = steam.gnStoragePath != null

    if (!loggedIn) add(SteamItem.PreLogin)

    add(SteamItem.Header("setupHeader", "setup", R.string.settings_steam_section_gamenative))
    if (loggedIn) {
        add(SteamItem.GnStatus)
        if (!steam.gnInstalled) add(SteamItem.GnInstall)
        if (steam.gnInstalled && !gnConfigured) add(SteamItem.GnStorageWarning)
        if (gnConfigured) {
            add(SteamItem.InstallPath)
            if (steam.installedGamesByVolume.isNotEmpty()) add(SteamItem.InstallTriage)
        }
    }
    add(SteamItem.GameNativeLibrary)

    if (loggedIn) {
        add(SteamItem.SectionSpacer("accountSpacer", "account"))
        add(SteamItem.Header("accountHeader", "account", R.string.settings_steam_section_account))
        add(SteamItem.AccountInfo)
    }

    val launchers = steamVisibleLaunchers(steam)
    val showStorageNote = !steam.hasStoragePermission && steam.installedLaunchers.isNotEmpty()
    val hasLauncherRows = loggedIn || launchers.isNotEmpty() ||
        steam.notInstalledLaunchers.isNotEmpty() || showStorageNote

    if (hasLauncherRows) {
        add(SteamItem.SectionSpacer("librarySpacer", "library"))
        add(SteamItem.Header("libraryHeader", "library", R.string.settings_steam_section_library))
        if (loggedIn) {
            add(SteamItem.SyncLibrary)
            add(SteamItem.AddManual)
        }
        for (launcher in launchers) {
            add(SteamItem.InstalledLauncher(launcher))
        }
        if (launchers.isNotEmpty()) {
            add(SteamItem.RefreshMetadata)
        }
        for (launcher in steam.notInstalledLaunchers) {
            add(SteamItem.NotInstalledLauncher(launcher))
        }
        if (showStorageNote) {
            add(SteamItem.StorageNote)
        }
    }

    if (loggedIn) {
        add(SteamItem.SectionSpacer("dangerSpacer", "danger"))
        add(SteamItem.Header("dangerHeader", "danger", R.string.settings_steam_section_danger))
        add(SteamItem.Disconnect)
        add(SteamItem.ResetLibrary)
    }
}

internal fun createSteamLayout(items: List<SteamItem>) = SettingsLayout<SteamItem, Unit>(
    allItems = items,
    isFocusable = { it.isFocusable },
    visibleWhen = { _, _ -> true },
    sectionOf = { it.section },
    sectionTitleRes = {
        when (it) {
            "setup" -> R.string.settings_steam_section_gamenative
            "account" -> R.string.settings_steam_section_account
            "library" -> R.string.settings_steam_section_library
            "danger" -> R.string.settings_steam_section_danger
            else -> null
        }
    }
)

internal fun steamMaxFocusIndex(steam: SteamSettingsState): Int =
    createSteamLayout(buildSteamItems(steam)).maxFocusIndex(Unit)

internal fun steamItemAtFocusIndex(focusIndex: Int, steam: SteamSettingsState): SteamItem? =
    createSteamLayout(buildSteamItems(steam)).itemAtFocusIndex(focusIndex, Unit)

internal fun steamSections(steam: SteamSettingsState) =
    createSteamLayout(buildSteamItems(steam)).buildSections(Unit)

internal fun isLoggedIn(steam: SteamSettingsState): Boolean =
    steam.connectionState == SteamConnectionState.LOGGED_IN

private fun gameNativeSubtitle(
    context: android.content.Context,
    steam: SteamSettingsState
): String = when {
    steam.isGameNativeScanning -> context.getString(R.string.settings_steam_gamenative_library_scanning)
    steam.gameNativeSyncDirs.isEmpty() ->
        context.getString(R.string.settings_steam_gamenative_library_unconfigured)
    steam.gameNativeMissingDirs.isNotEmpty() -> context.getString(
        R.string.settings_steam_gamenative_library_missing,
        steam.gameNativeMissingDirs.sortedBy { it.ordinal }.joinToString(", ") { it.displayName }
    )
    else -> context.getString(
        R.string.settings_steam_gamenative_library_configured,
        steam.gameNativeSyncDirs.keys.sortedBy { it.ordinal }.joinToString(", ") { it.displayName }
    )
}

@Composable
fun SteamSection(uiState: SettingsUiState, viewModel: SettingsViewModel) {
    val context = LocalContext.current
    val steam = uiState.steam

    LaunchedEffect(Unit) {
        viewModel.refreshSteamSettings()
    }

    val allItems = remember(
        steam.connectionState,
        steam.gnInstalled,
        steam.gnStoragePath,
        steam.installedGamesByVolume,
        steam.installedLaunchers,
        steam.notInstalledLaunchers,
        steam.hasStoragePermission
    ) {
        buildSteamItems(steam)
    }
    val layout = remember(allItems) { createSteamLayout(allItems) }
    val sections = remember(allItems, context) { layout.buildSections(Unit, context) }
    val isDownloading = steam.downloadingLauncherId != null
    val gameNativeRowSubtitle = remember(
        steam.isGameNativeScanning,
        steam.gameNativeSyncDirs,
        steam.gameNativeMissingDirs,
        context
    ) {
        gameNativeSubtitle(context, steam)
    }

    fun isFocused(item: SteamItem): Boolean =
        uiState.focusedIndex == layout.focusIndexOf(item, Unit)

    SectionPaneLayout(
        items = allItems,
        sections = sections,
        focusedIndex = uiState.focusedIndex,
        focusToListIndex = { layout.focusToListIndex(it, Unit) },
        itemKey = { it.key },
        isNavItem = { it is SteamItem.SectionSpacer },
        isHeader = { it is SteamItem.Header },
        onSectionTap = { viewModel.setFocusIndex(it.focusStartIndex) },
        modifier = Modifier.fillMaxSize().padding(horizontal = Dimens.spacingMd),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) { item ->
            when (item) {
                is SteamItem.Header -> SectionHeader(stringResource(item.titleRes))
                is SteamItem.SectionSpacer -> Spacer(modifier = Modifier.height(Dimens.spacingLg))

                SteamItem.PreLogin -> SteamPreLoginPane(
                    steam = steam,
                    isFocused = isFocused(item),
                    onInstallGn = {
                        val intent = Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("https://github.com/utkarshdalal/GameNative/releases")
                        )
                        context.startActivity(intent)
                    },
                    onCancelQr = { viewModel.cancelSteamQrAuth() },
                    onConnect = {
                        viewModel.connectToSteam()
                        viewModel.startSteamQrAuth()
                    }
                )

                SteamItem.GnStatus -> {
                    val (icon, subtitle, color) = if (steam.gnStoragePath != null) {
                        Triple(
                            Icons.Default.CheckCircle,
                            stringResource(R.string.settings_steam_gn_status_configured),
                            MaterialTheme.colorScheme.primary
                        )
                    } else if (steam.gnInstalled) {
                        Triple(
                            Icons.Default.Warning,
                            stringResource(R.string.settings_steam_gn_status_unconfigured),
                            MaterialTheme.colorScheme.error
                        )
                    } else {
                        Triple(
                            Icons.Default.Warning,
                            stringResource(R.string.settings_steam_gn_status_missing),
                            MaterialTheme.colorScheme.error
                        )
                    }
                    InfoPreference(
                        title = stringResource(R.string.settings_steam_gn_status_title),
                        value = subtitle,
                        icon = icon,
                        isFocused = isFocused(item)
                    )
                }

                SteamItem.GnInstall -> ActionPreference(
                    icon = Icons.Default.Download,
                    title = stringResource(R.string.settings_steam_gn_install_title),
                    subtitle = stringResource(R.string.settings_steam_gn_install_subtitle),
                    isFocused = isFocused(item),
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW,
                            Uri.parse("https://github.com/utkarshdalal/GameNative/releases"))
                        context.startActivity(intent)
                    }
                )

                SteamItem.GnStorageWarning -> InfoPreference(
                    title = stringResource(R.string.settings_steam_gn_storage_warning_title),
                    value = stringResource(R.string.settings_steam_gn_storage_warning_value),
                    icon = Icons.Default.Warning,
                    isFocused = isFocused(item)
                )

                SteamItem.InstallPath -> ActionPreference(
                    icon = Icons.Default.Folder,
                    title = if (steam.steamInstallPathIsCustom) {
                        stringResource(R.string.settings_steam_install_path_title_custom)
                    } else {
                        stringResource(R.string.settings_steam_install_path_title)
                    },
                    subtitle = formatPath(context, steam.steamInstallPath),
                    trailingButtonLabel = stringResource(R.string.settings_steam_install_path_change),
                    isFocused = isFocused(item),
                    onClick = { viewModel.openSteamInstallPathPicker() },
                    showResetButton = steam.steamInstallPathIsCustom,
                    onReset = { viewModel.resetSteamInstallPath() }
                )

                SteamItem.InstallTriage -> {
                    val summary = steam.installedGamesByVolume.entries.joinToString(", ") { (label, count) ->
                        "$label ($count)"
                    }
                    InfoPreference(
                        title = stringResource(R.string.settings_steam_install_triage_title),
                        value = summary,
                        icon = Icons.Default.CheckCircle,
                        isFocused = isFocused(item)
                    )
                }

                SteamItem.AccountInfo -> InfoPreference(
                    title = stringResource(R.string.settings_steam_account_title),
                    value = steam.username ?: stringResource(R.string.settings_steam_account_connected),
                    icon = Icons.Default.Cloud,
                    isFocused = isFocused(item)
                )

                SteamItem.SyncLibrary -> {
                    val syncText = when (val s = steam.syncState) {
                        is LibrarySyncState.Idle -> stringResource(R.string.settings_steam_sync_idle)
                        is LibrarySyncState.SyncingLicenses ->
                            stringResource(R.string.settings_steam_sync_licenses)
                        is LibrarySyncState.FetchingPackages ->
                            stringResource(R.string.settings_steam_sync_packages, s.current, s.total)
                        is LibrarySyncState.FetchingApps ->
                            stringResource(R.string.settings_steam_sync_apps, s.current, s.total)
                        is LibrarySyncState.Complete ->
                            stringResource(R.string.settings_steam_sync_complete, s.gamesAdded, s.gamesUpdated)
                        is LibrarySyncState.Error ->
                            stringResource(R.string.settings_steam_sync_error, s.message)
                    }
                    ActionPreference(
                        icon = Icons.Default.Sync,
                        title = stringResource(R.string.settings_steam_sync_title),
                        subtitle = syncText,
                        isFocused = isFocused(item),
                        isEnabled = steam.syncState is LibrarySyncState.Idle ||
                            steam.syncState is LibrarySyncState.Complete ||
                            steam.syncState is LibrarySyncState.Error,
                        onClick = { viewModel.syncSteamLibrary() }
                    )
                }

                SteamItem.AddManual -> ActionPreference(
                    icon = Icons.Default.Cloud,
                    title = stringResource(R.string.settings_steam_add_manual_title),
                    subtitle = stringResource(R.string.settings_steam_add_manual_subtitle),
                    isFocused = isFocused(item),
                    onClick = { viewModel.showAddSteamGameDialog() }
                )

                SteamItem.GameNativeLibrary -> DualActionPreference(
                    title = stringResource(R.string.settings_steam_gamenative_library_title),
                    subtitle = gameNativeRowSubtitle,
                    primaryLabel = stringResource(R.string.settings_steam_gamenative_library_folders),
                    secondaryLabel = stringResource(R.string.settings_steam_gamenative_library_scan),
                    showSecondary = steam.gameNativeSyncDirs.isNotEmpty(),
                    isFocused = isFocused(item),
                    actionIndex = steam.gameNativeActionIndex,
                    icon = Icons.Default.Folder,
                    isBusy = steam.isGameNativeScanning,
                    busyDisablesPrimary = false,
                    onPrimary = { viewModel.openGameNativeFoldersModal() },
                    onSecondary = { viewModel.rescanGameNativeStores() }
                )

                is SteamItem.InstalledLauncher -> {
                    val launcher = item.data
                    SteamLauncherPreference(
                        displayName = launcher.displayName,
                        subtitle = null,
                        isSyncing = steam.isSyncing && steam.syncingLauncher == launcher.packageName,
                        isFocused = isFocused(item),
                        isEnabled = steam.hasStoragePermission && !steam.isSyncing,
                        onAdd = { viewModel.showAddSteamGameDialog(launcher.packageName) }
                    )
                }

                SteamItem.RefreshMetadata -> ActionPreference(
                    icon = Icons.Default.Sync,
                    title = stringResource(R.string.settings_steam_refresh_metadata_title),
                    subtitle = if (steam.isSyncing && steam.syncingLauncher == "refresh") {
                        stringResource(R.string.settings_steam_refresh_metadata_busy)
                    } else {
                        stringResource(R.string.settings_steam_refresh_metadata_subtitle)
                    },
                    isFocused = isFocused(item),
                    isEnabled = !steam.isSyncing,
                    onClick = { viewModel.refreshSteamMetadata() }
                )

                is SteamItem.NotInstalledLauncher -> {
                    val launcher = item.data
                    val isThisDownloading = steam.downloadingLauncherId == launcher.emulatorId
                    ActionPreference(
                        icon = if (launcher.hasDirectDownload) Icons.Default.GetApp
                            else Icons.AutoMirrored.Filled.OpenInNew,
                        title = launcher.displayName,
                        subtitle = when {
                            isThisDownloading && steam.downloadProgress != null -> stringResource(
                                R.string.settings_steam_launcher_downloading,
                                (steam.downloadProgress * 100).toInt()
                            )
                            isThisDownloading ->
                                stringResource(R.string.settings_steam_launcher_waiting)
                            launcher.hasDirectDownload ->
                                stringResource(R.string.settings_steam_launcher_download)
                            else -> stringResource(R.string.settings_steam_launcher_store)
                        },
                        isFocused = isFocused(item),
                        isEnabled = !isDownloading,
                        onClick = { viewModel.installSteamLauncher(launcher.emulatorId) }
                    )
                }

                SteamItem.StorageNote -> {
                    Spacer(modifier = Modifier.height(Dimens.spacingSm))
                    ActionPreference(
                        icon = Icons.Default.Cloud,
                        title = stringResource(R.string.settings_steam_storage_note_title),
                        subtitle = stringResource(R.string.settings_steam_storage_note_subtitle),
                        isFocused = false,
                        onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                val intent = Intent(
                                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION
                                ).apply { data = Uri.parse("package:${context.packageName}") }
                                context.startActivity(intent)
                            }
                        }
                    )
                }

                SteamItem.Disconnect -> ActionPreference(
                    icon = Icons.Default.LinkOff,
                    title = stringResource(R.string.settings_steam_disconnect_title),
                    subtitle = stringResource(R.string.settings_steam_disconnect_subtitle),
                    isFocused = isFocused(item),
                    onClick = { viewModel.disconnectSteam() },
                    iconTint = MaterialTheme.colorScheme.error
                )

                SteamItem.ResetLibrary -> ActionPreference(
                    icon = Icons.Default.Delete,
                    title = stringResource(R.string.settings_steam_reset_library_title),
                    subtitle = stringResource(R.string.settings_steam_reset_library_subtitle),
                    isFocused = isFocused(item),
                    onClick = { viewModel.resetSteamLibrary() },
                    iconTint = MaterialTheme.colorScheme.error
                )
            }
    }

    if (uiState.steam.showAddGameDialog) {
        AddSteamGameDialog(uiState, viewModel)
    }

    if (uiState.steam.showGameNativeFoldersModal) {
        GameNativeFoldersModal(
            paths = uiState.steam.gameNativeSyncDirs,
            focusIndex = uiState.steam.gameNativeFoldersFocusIndex,
            actionIndex = uiState.steam.gameNativeFoldersActionIndex,
            onPick = { folder -> viewModel.openGameNativeSyncDirPicker(folder) },
            onClear = { folder -> viewModel.clearGameNativeSyncDir(folder) },
            onDismiss = { viewModel.dismissGameNativeFoldersModal() }
        )
    }

    if (uiState.steam.variantPickerInfo != null) {
        com.nendo.argosy.ui.screens.settings.components.VariantPickerModal(
            info = uiState.steam.variantPickerInfo,
            focusIndex = uiState.steam.variantPickerFocusIndex,
            onItemTap = { index -> viewModel.handleSteamVariantItemTap(index) },
            onConfirm = { viewModel.confirmSteamVariantSelection() },
            onDismiss = { viewModel.dismissSteamVariantPicker() }
        )
    }
}

/**
 * State priority: GameNative check, then a visible QR, then auth in progress, then connecting,
 * then an idle error, then the connect button. Errors surface only while the user is idle so a
 * transient connect failure never flashes, and CONNECTED/LOGGING_IN with no auth flow means the
 * user cancelled QR auth on a live socket, which shows connect rather than a stuck spinner.
 */
@Composable
private fun SteamPreLoginPane(
    steam: SteamSettingsState,
    isFocused: Boolean,
    onInstallGn: () -> Unit,
    onCancelQr: () -> Unit,
    onConnect: () -> Unit
) {
    when {
        !steam.gnInstalled -> GnNotInstalledContent(isFocused = isFocused, onInstall = onInstallGn)

        steam.qrUrl != null -> QrAuthContent(
            qrUrl = steam.qrUrl,
            isFocused = isFocused,
            onCancel = onCancelQr
        )

        steam.authPolling -> ConnectingContent()

        steam.connectionState == SteamConnectionState.CONNECTING -> ConnectingContent()

        steam.error != null && steam.connectionState == SteamConnectionState.DISCONNECTED ->
            ErrorContent(message = steam.error, isFocused = isFocused, onRetry = onConnect)

        else -> NotConnectedContent(isFocused = isFocused, onConnect = onConnect)
    }
}

@Composable
private fun GnNotInstalledContent(
    isFocused: Boolean,
    onInstall: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.radiusMd))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(Dimens.spacingLg),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.settings_steam_gn_required_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(Dimens.spacingMd))

        Text(
            text = stringResource(R.string.settings_steam_gn_required_message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(Dimens.spacingLg))

        FocusableButton(
            text = stringResource(R.string.settings_steam_gn_required_action),
            isFocused = isFocused,
            onClick = onInstall
        )
    }
}

@Composable
private fun NotConnectedContent(
    isFocused: Boolean,
    onConnect: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.radiusMd))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(Dimens.spacingLg),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.settings_steam_login_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(Dimens.spacingMd))

        Text(
            text = stringResource(R.string.settings_steam_login_message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(Dimens.spacingLg))

        FocusableButton(
            text = stringResource(R.string.settings_steam_login_action),
            isFocused = isFocused,
            onClick = onConnect
        )
    }
}

@Composable
private fun ConnectingContent(
    message: String = stringResource(R.string.settings_steam_connecting)
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(Dimens.spacingXl),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(48.dp),
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(Dimens.spacingMd))

        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun QrAuthContent(
    qrUrl: String,
    isFocused: Boolean,
    onCancel: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.radiusMd))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(Dimens.spacingLg),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.weight(0.3f),
            contentAlignment = Alignment.Center
        ) {
            QrCodeImage(
                url = qrUrl,
                modifier = Modifier.size(160.dp)
            )
        }

        Column(
            modifier = Modifier.weight(0.7f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
        ) {
            Text(
                text = stringResource(R.string.settings_steam_qr_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(Dimens.spacingSm))

            Text(
                text = stringResource(R.string.settings_steam_qr_message),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(Dimens.spacingSm))

            FocusableButton(
                text = stringResource(R.string.settings_steam_qr_cancel),
                isFocused = isFocused,
                onClick = onCancel
            )
        }
    }
}

@Composable
private fun ErrorContent(
    message: String,
    isFocused: Boolean,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.radiusMd))
            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f))
            .padding(Dimens.spacingLg),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.settings_steam_error_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.error
        )

        Spacer(modifier = Modifier.height(Dimens.spacingSm))

        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(Dimens.spacingMd))

        FocusableButton(
            text = stringResource(R.string.settings_steam_error_retry),
            isFocused = isFocused,
            onClick = onRetry
        )
    }
}

@Composable
private fun FocusableButton(
    text: String,
    isFocused: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = if (isFocused) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val textColor = if (isFocused) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(Dimens.radiusMd))
            .background(backgroundColor)
            .clickable(
                onClick = onClick,
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            )
            .padding(horizontal = Dimens.spacingLg, vertical = Dimens.spacingMd),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = textColor
        )
    }
}

@Composable
private fun QrCodeImage(
    url: String,
    modifier: Modifier = Modifier
) {
    val qrBitmap = remember(url) { generateQrCode(url, 512) }

    if (qrBitmap != null) {
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White)
                .border(2.dp, Color.White, RoundedCornerShape(8.dp))
                .padding(8.dp)
        ) {
            Image(
                bitmap = qrBitmap.asImageBitmap(),
                contentDescription = stringResource(R.string.settings_steam_qr_image_description),
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

private fun generateQrCode(content: String, size: Int): Bitmap? {
    return try {
        val hints = mapOf(
            EncodeHintType.MARGIN to 1,
            EncodeHintType.CHARACTER_SET to "UTF-8"
        )
        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size, hints)
        val width = bitMatrix.width
        val height = bitMatrix.height
        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                pixels[y * width + x] = if (bitMatrix[x, y]) {
                    android.graphics.Color.BLACK
                } else {
                    android.graphics.Color.WHITE
                }
            }
        }
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            setPixels(pixels, 0, width, 0, 0, width, height)
        }
    } catch (e: Exception) {
        null
    }
}

private fun installVolumeLabel(
    selectedVolume: String?,
    volumes: List<com.nendo.argosy.data.steam.SteamInstallVolume>
): String {
    if (selectedVolume == null) return "Automatic"
    return volumes.find { it.target.toPreferenceValue() == selectedVolume }?.label ?: "Automatic"
}

private fun installVolumeSubtitle(
    selectedVolume: String?,
    volumes: List<com.nendo.argosy.data.steam.SteamInstallVolume>
): String? {
    val vol = if (selectedVolume == null) {
        volumes.firstOrNull { it.target is com.nendo.argosy.data.steam.SteamInstallTarget.CustomVolume && it.hasGnPath }
            ?: volumes.firstOrNull { it.target is com.nendo.argosy.data.steam.SteamInstallTarget.Internal && it.hasGnPath }
    } else {
        volumes.find { it.target.toPreferenceValue() == selectedVolume }
    } ?: return null
    if (!vol.hasGnPath) return "Not writable"
    val freeGb = vol.freeBytes / (1024L * 1024L * 1024L)
    return if (selectedVolume == null) "${vol.label} · ${freeGb}GB free" else "${freeGb}GB free"
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        modifier = Modifier.padding(start = Dimens.spacingSm, top = Dimens.spacingSm)
    )
}

private const val ADD_GAME_ROW_FIELD = 0
private const val ADD_GAME_ROW_BUTTONS = 1

@Composable
internal fun AddSteamGameDialog(uiState: SettingsUiState, viewModel: SettingsViewModel) {
    val theme = LocalArgosyTheme.current
    val selectedLauncherName = uiState.steam.selectedLauncherPackage?.let { pkg ->
        uiState.steam.installedLaunchers.find { it.packageName == pkg }?.displayName
    }
    val isAddingGame = uiState.steam.isAddingGame
    val canAdd = !isAddingGame && uiState.steam.addGameAppId.isNotBlank()

    var focusRow by remember { mutableIntStateOf(ADD_GAME_ROW_FIELD) }
    var buttonIndex by remember { mutableIntStateOf(1) }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val currentIsAdding by rememberUpdatedState(isAddingGame)
    val currentCanAdd by rememberUpdatedState(canAdd)

    LaunchedEffect(focusRow) {
        if (focusRow == ADD_GAME_ROW_FIELD) focusRequester.requestFocus() else focusManager.clearFocus()
    }

    val inputHandler = remember {
        object : InputHandler {
            override fun onUp(): InputResult {
                if (!currentIsAdding) focusRow = ADD_GAME_ROW_FIELD
                return InputResult.HANDLED
            }

            override fun onDown(): InputResult {
                if (!currentIsAdding) focusRow = ADD_GAME_ROW_BUTTONS
                return InputResult.HANDLED
            }

            override fun onLeft(): InputResult {
                if (!currentIsAdding && focusRow == ADD_GAME_ROW_BUTTONS) buttonIndex = 0
                return InputResult.HANDLED
            }

            override fun onRight(): InputResult {
                if (!currentIsAdding && focusRow == ADD_GAME_ROW_BUTTONS) buttonIndex = 1
                return InputResult.HANDLED
            }

            override fun onConfirm(): InputResult {
                when {
                    currentIsAdding -> {}
                    focusRow == ADD_GAME_ROW_FIELD -> focusRow = ADD_GAME_ROW_BUTTONS
                    buttonIndex == 0 -> viewModel.dismissAddSteamGameDialog()
                    currentCanAdd -> viewModel.confirmAddSteamGame()
                }
                return InputResult.HANDLED
            }

            override fun onBack(): InputResult {
                if (!currentIsAdding) viewModel.dismissAddSteamGameDialog()
                return InputResult.handled(SoundType.CLOSE_MODAL)
            }

            override fun onMenu(): InputResult = InputResult.HANDLED
            override fun onSecondaryAction(): InputResult = InputResult.HANDLED
            override fun onContextMenu(): InputResult = InputResult.HANDLED
            override fun onPrevSection(): InputResult = InputResult.HANDLED
            override fun onNextSection(): InputResult = InputResult.HANDLED
            override fun onPrevTrigger(): InputResult = InputResult.HANDLED
            override fun onNextTrigger(): InputResult = InputResult.HANDLED
            override fun onSelect(): InputResult = InputResult.HANDLED
            override fun onLeftStickClick(): InputResult = InputResult.HANDLED
            override fun onRightStickClick(): InputResult = InputResult.HANDLED
            override fun onLongConfirm(): InputResult = InputResult.HANDLED
        }
    }
    ModalInputEffect(active = true, handler = inputHandler)

    val fieldShape = RoundedCornerShape(Dimens.radiusMd)
    Modal(
        title = stringResource(R.string.settings_steam_add_game_title),
        onDismiss = { if (!isAddingGame) viewModel.dismissAddSteamGameDialog() }
    ) {
        val description = if (selectedLauncherName != null) {
            stringResource(R.string.settings_steam_add_game_message_launcher, selectedLauncherName)
        } else {
            stringResource(R.string.settings_steam_add_game_message)
        }
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(Dimens.spacingMd))
        OutlinedTextField(
            value = uiState.steam.addGameAppId,
            onValueChange = { viewModel.setAddGameAppId(it) },
            label = { Text(stringResource(R.string.settings_steam_add_game_field_label)) },
            placeholder = { Text(stringResource(R.string.settings_steam_add_game_field_hint)) },
            singleLine = true,
            enabled = !isAddingGame,
            isError = uiState.steam.addGameError != null,
            shape = fieldShape,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (canAdd) viewModel.confirmAddSteamGame() }),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .then(
                    if (focusRow == ADD_GAME_ROW_FIELD) {
                        Modifier.background(theme.focusAccent.copy(alpha = 0.15f), fieldShape)
                    } else Modifier
                )
        )
        if (uiState.steam.addGameError != null) {
            Spacer(modifier = Modifier.height(Dimens.spacingSm))
            Text(
                text = uiState.steam.addGameError,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
        Spacer(modifier = Modifier.height(Dimens.spacingLg))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm, Alignment.End)
        ) {
            ModalActionButton(
                label = stringResource(R.string.settings_steam_add_game_cancel),
                tint = theme.focusAccent,
                restLabelColor = theme.textPrimary,
                focused = focusRow == ADD_GAME_ROW_BUTTONS && buttonIndex == 0,
                onClick = { viewModel.dismissAddSteamGameDialog() },
                enabled = !isAddingGame
            )
            if (isAddingGame) {
                CircularProgressIndicator(
                    modifier = Modifier.size(Dimens.iconMd),
                    strokeWidth = Dimens.borderMedium
                )
            } else {
                ModalActionButton(
                    label = stringResource(R.string.settings_steam_add_game_confirm),
                    tint = theme.focusAccent,
                    restLabelColor = theme.textPrimary,
                    focused = focusRow == ADD_GAME_ROW_BUTTONS && buttonIndex == 1,
                    onClick = { viewModel.confirmAddSteamGame() },
                    enabled = canAdd
                )
            }
        }
    }
}
