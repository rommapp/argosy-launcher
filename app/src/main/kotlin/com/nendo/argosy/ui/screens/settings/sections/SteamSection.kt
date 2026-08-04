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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
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

    class Header(key: String, section: String, val title: String) : SteamItem(key, section)

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

    add(SteamItem.Header("setupHeader", "setup", "GAMENATIVE"))
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
        add(SteamItem.Header("accountHeader", "account", "ACCOUNT"))
        add(SteamItem.AccountInfo)
    }

    val launchers = steamVisibleLaunchers(steam)
    val showStorageNote = !steam.hasStoragePermission && steam.installedLaunchers.isNotEmpty()
    val hasLauncherRows = loggedIn || launchers.isNotEmpty() ||
        steam.notInstalledLaunchers.isNotEmpty() || showStorageNote

    if (hasLauncherRows) {
        add(SteamItem.SectionSpacer("librarySpacer", "library"))
        add(SteamItem.Header("libraryHeader", "library", "LIBRARY"))
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
        add(SteamItem.Header("dangerHeader", "danger", "DANGER ZONE"))
        add(SteamItem.Disconnect)
        add(SteamItem.ResetLibrary)
    }
}

internal fun createSteamLayout(items: List<SteamItem>) = SettingsLayout<SteamItem, Unit>(
    allItems = items,
    isFocusable = { it.isFocusable },
    visibleWhen = { _, _ -> true },
    sectionOf = { it.section },
    sectionTitle = {
        when (it) {
            "setup" -> "GAMENATIVE"
            "account" -> "ACCOUNT"
            "library" -> "LIBRARY"
            "danger" -> "DANGER ZONE"
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

private fun gameNativeSubtitle(steam: SteamSettingsState): String = when {
    steam.isGameNativeScanning -> "Scanning..."
    steam.gameNativeSyncDirs.isEmpty() ->
        "Set GameNative's Frontend Sync folders to import GOG, Epic and Amazon installs and mark Steam games installed"
    steam.gameNativeMissingDirs.isNotEmpty() -> "Folder missing: " + steam.gameNativeMissingDirs
        .sortedBy { it.ordinal }
        .joinToString(", ") { it.displayName }
    else -> steam.gameNativeSyncDirs.keys
        .sortedBy { it.ordinal }
        .joinToString(", ") { it.displayName } + " configured"
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
    val sections = remember(allItems) { layout.buildSections(Unit) }
    val isDownloading = steam.downloadingLauncherId != null
    val gameNativeRowSubtitle = remember(
        steam.isGameNativeScanning,
        steam.gameNativeSyncDirs,
        steam.gameNativeMissingDirs
    ) {
        gameNativeSubtitle(steam)
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
                is SteamItem.Header -> SectionHeader(item.title)
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
                        Triple(Icons.Default.CheckCircle, "External storage configured", MaterialTheme.colorScheme.primary)
                    } else if (steam.gnInstalled) {
                        Triple(Icons.Default.Warning, "External storage not configured", MaterialTheme.colorScheme.error)
                    } else {
                        Triple(Icons.Default.Warning, "Not installed", MaterialTheme.colorScheme.error)
                    }
                    InfoPreference(
                        title = "GameNative",
                        value = subtitle,
                        icon = icon,
                        isFocused = isFocused(item)
                    )
                }

                SteamItem.GnInstall -> ActionPreference(
                    icon = Icons.Default.Download,
                    title = "Install GameNative",
                    subtitle = "Required to launch Steam games",
                    isFocused = isFocused(item),
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW,
                            Uri.parse("https://github.com/utkarshdalal/GameNative/releases"))
                        context.startActivity(intent)
                    }
                )

                SteamItem.GnStorageWarning -> InfoPreference(
                    title = "External Storage",
                    value = "Open GameNative and enable 'Write to external storage' in Settings",
                    icon = Icons.Default.Warning,
                    isFocused = isFocused(item)
                )

                SteamItem.InstallPath -> ActionPreference(
                    icon = Icons.Default.Folder,
                    title = if (steam.steamInstallPathIsCustom) "Install Path (custom)" else "Install Path",
                    subtitle = formatPath(steam.steamInstallPath),
                    trailingButtonLabel = "Change",
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
                        title = "Installed Games",
                        value = summary,
                        icon = Icons.Default.CheckCircle,
                        isFocused = isFocused(item)
                    )
                }

                SteamItem.AccountInfo -> InfoPreference(
                    title = "Steam Account",
                    value = steam.username ?: "Connected",
                    icon = Icons.Default.Cloud,
                    isFocused = isFocused(item)
                )

                SteamItem.SyncLibrary -> {
                    val syncText = when (val s = steam.syncState) {
                        is LibrarySyncState.Idle -> "Sync owned games from Steam"
                        is LibrarySyncState.SyncingLicenses -> "Syncing licenses..."
                        is LibrarySyncState.FetchingPackages -> "Fetching packages (${s.current}/${s.total})..."
                        is LibrarySyncState.FetchingApps -> "Fetching games (${s.current}/${s.total})..."
                        is LibrarySyncState.Complete -> "Added ${s.gamesAdded}, updated ${s.gamesUpdated}"
                        is LibrarySyncState.Error -> "Error: ${s.message}"
                    }
                    ActionPreference(
                        icon = Icons.Default.Sync,
                        title = "Sync Library",
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
                    title = "Add by App ID",
                    subtitle = "Add a Steam game by its App ID",
                    isFocused = isFocused(item),
                    onClick = { viewModel.showAddSteamGameDialog() }
                )

                SteamItem.GameNativeLibrary -> DualActionPreference(
                    title = "GameNative Library",
                    subtitle = gameNativeRowSubtitle,
                    primaryLabel = "Folders",
                    secondaryLabel = "Scan",
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
                    title = "Refresh Metadata",
                    subtitle = if (steam.isSyncing && steam.syncingLauncher == "refresh") {
                        "Refreshing..."
                    } else {
                        "Update screenshots and backgrounds"
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
                            isThisDownloading && steam.downloadProgress != null ->
                                "Downloading... ${(steam.downloadProgress * 100).toInt()}%"
                            isThisDownloading -> "Waiting for install..."
                            launcher.hasDirectDownload -> "Download APK"
                            else -> "Open Play Store"
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
                        title = "Grant Storage Permission",
                        subtitle = "Required for Steam integration",
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
                    title = "Disconnect",
                    subtitle = "Log out of Steam",
                    isFocused = isFocused(item),
                    onClick = { viewModel.disconnectSteam() },
                    iconTint = MaterialTheme.colorScheme.error
                )

                SteamItem.ResetLibrary -> ActionPreference(
                    icon = Icons.Default.Delete,
                    title = "Reset Steam Library",
                    subtitle = "Remove all synced Steam games",
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
            text = "GameNative Required",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(Dimens.spacingMd))

        Text(
            text = "Steam games are downloaded by Argosy and launched through GameNative. " +
                "Install GameNative and enable external storage in its settings before continuing.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(Dimens.spacingLg))

        FocusableButton(
            text = "Install GameNative",
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
            text = "Log in to Steam",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(Dimens.spacingMd))

        Text(
            text = "Connect your Steam account to sync your library and download games.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(Dimens.spacingLg))

        FocusableButton(
            text = "Connect",
            isFocused = isFocused,
            onClick = onConnect
        )
    }
}

@Composable
private fun ConnectingContent(message: String = "Connecting...") {
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
                text = "Scan with the Steam mobile app",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(Dimens.spacingSm))

            Text(
                text = "Open Steam on your phone, tap the guard icon, then 'Confirm sign-in'.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(Dimens.spacingSm))

            FocusableButton(
                text = "Cancel",
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
            text = "Connection Error",
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
            text = "Try Again",
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
                contentDescription = "QR Code",
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
        title = "Add Steam Game",
        onDismiss = { if (!isAddingGame) viewModel.dismissAddSteamGameDialog() }
    ) {
        val description = if (selectedLauncherName != null) {
            "Enter the Steam App ID to add a game for $selectedLauncherName. You can find this in the game's Steam store URL."
        } else {
            "Enter the Steam App ID to add a game. You can find this in the game's Steam store URL."
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
            label = { Text("Steam App ID") },
            placeholder = { Text("e.g. 730") },
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
                label = "Cancel",
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
                    label = "Add",
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
