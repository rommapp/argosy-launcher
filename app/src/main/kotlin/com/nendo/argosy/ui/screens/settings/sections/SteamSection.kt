package com.nendo.argosy.ui.screens.settings.sections

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.nendo.argosy.data.steam.LibrarySyncState
import com.nendo.argosy.data.steam.SteamConnectionState
import com.nendo.argosy.ui.components.ActionPreference
import com.nendo.argosy.ui.components.CyclePreference
import com.nendo.argosy.ui.components.InfoPreference
import com.nendo.argosy.ui.screens.settings.SettingsUiState
import com.nendo.argosy.ui.screens.settings.SettingsViewModel
import com.nendo.argosy.ui.screens.settings.SteamSettingsState
import com.nendo.argosy.ui.screens.settings.components.SectionPaneLayout
import com.nendo.argosy.ui.screens.settings.menu.SettingsLayout
import com.nendo.argosy.ui.components.ListSection
import com.nendo.argosy.ui.theme.Dimens

internal data class SteamLayoutState(
    val isLoggedIn: Boolean,
    val gnInstalled: Boolean,
    val gnConfigured: Boolean,
    val isSyncing: Boolean
)

internal sealed class SteamItem(
    val key: String,
    val section: String,
    val visibleWhen: (SteamLayoutState) -> Boolean = { true }
) {
    val isFocusable: Boolean get() = when (this) {
        is Header, is SectionSpacer -> false
        else -> true
    }

    class Header(key: String, section: String, val title: String, visibleWhen: (SteamLayoutState) -> Boolean = { true })
        : SteamItem(key, section, visibleWhen)

    class SectionSpacer(key: String, section: String, visibleWhen: (SteamLayoutState) -> Boolean = { true })
        : SteamItem(key, section, visibleWhen)

    data object GnStatus : SteamItem("gnStatus", "setup")
    data object GnInstall : SteamItem("gnInstall", "setup", visibleWhen = { !it.gnInstalled })
    data object GnStorageWarning : SteamItem("gnStorageWarning", "setup",
        visibleWhen = { it.gnInstalled && !it.gnConfigured })
    data object InstallPath : SteamItem("installPath", "setup",
        visibleWhen = { it.gnConfigured })
    data object AccountInfo : SteamItem("accountInfo", "account")
    data object SyncLibrary : SteamItem("syncLibrary", "library")
    data object AddManual : SteamItem("addManual", "library")
    data object Disconnect : SteamItem("disconnect", "danger")
    data object ResetLibrary : SteamItem("resetLibrary", "danger")

    companion object {
        private val SetupHeader = Header("setupHeader", "setup", "GAMENATIVE")
        private val AccountHeader = Header("accountHeader", "account", "ACCOUNT")
        private val LibraryHeader = Header("libraryHeader", "library", "LIBRARY")
        private val AccountSpacer = SectionSpacer("accountSpacer", "account")
        private val LibrarySpacer = SectionSpacer("librarySpacer", "library")
        private val DangerHeader = Header("dangerHeader", "danger", "DANGER ZONE")
        private val DangerSpacer = SectionSpacer("dangerSpacer", "danger")

        val ALL: List<SteamItem> = listOf(
            SetupHeader, GnStatus, GnInstall, GnStorageWarning, InstallPath,
            AccountSpacer, AccountHeader, AccountInfo,
            LibrarySpacer, LibraryHeader, SyncLibrary, AddManual,
            DangerSpacer, DangerHeader, Disconnect, ResetLibrary
        )
    }
}

private val steamLayout = SettingsLayout<SteamItem, SteamLayoutState>(
    allItems = SteamItem.ALL,
    isFocusable = { it.isFocusable },
    visibleWhen = { item, state -> item.visibleWhen(state) },
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

internal fun steamMaxFocusIndex(steam: SteamSettingsState): Int {
    val state = steamLayoutState(steam)
    return if (isLoggedIn(steam)) steamLayout.maxFocusIndex(state) else 0
}

internal fun steamItemAtFocusIndex(focusIndex: Int, steam: SteamSettingsState): SteamItem? {
    return steamLayout.itemAtFocusIndex(focusIndex, steamLayoutState(steam))
}

internal fun isLoggedIn(steam: SteamSettingsState): Boolean =
    steam.connectionState == SteamConnectionState.LOGGED_IN

private fun steamLayoutState(steam: SteamSettingsState) = SteamLayoutState(
    isLoggedIn = isLoggedIn(steam),
    gnInstalled = steam.gnInstalled,
    gnConfigured = steam.gnStoragePath != null,
    isSyncing = steam.syncState !is LibrarySyncState.Idle &&
        steam.syncState !is LibrarySyncState.Complete &&
        steam.syncState !is LibrarySyncState.Error
)

@Composable
fun SteamSection(uiState: SettingsUiState, viewModel: SettingsViewModel) {
    val context = LocalContext.current
    val steam = uiState.steam

    LaunchedEffect(Unit) {
        viewModel.refreshSteamSettings()
    }

    val layoutState = remember(steam.connectionState, steam.gnInstalled, steam.gnStoragePath, steam.syncState) {
        steamLayoutState(steam)
    }

    if (isLoggedIn(steam)) {
        val visibleItems = remember(layoutState) { steamLayout.visibleItems(layoutState) }
        val sections = remember(layoutState) { steamLayout.buildSections(layoutState) }

        fun isFocused(item: SteamItem): Boolean =
            uiState.focusedIndex == steamLayout.focusIndexOf(item, layoutState)

        SectionPaneLayout(
            items = visibleItems,
            sections = sections,
            focusedIndex = uiState.focusedIndex,
            focusToListIndex = { steamLayout.focusToListIndex(it, layoutState) },
            itemKey = { it.key },
            isNavItem = { it is SteamItem.SectionSpacer },
            onSectionTap = { viewModel.setFocusIndex(it.focusStartIndex) },
            modifier = Modifier.fillMaxSize().padding(horizontal = Dimens.spacingMd),
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
        ) { item ->
            when (item) {
                is SteamItem.Header -> SectionHeader(item.title)
                is SteamItem.SectionSpacer -> Spacer(modifier = Modifier.height(Dimens.spacingLg))

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

                SteamItem.InstallPath -> {
                    val volumeLabel = installVolumeLabel(
                        steam.steamInstallVolume,
                        steam.availableVolumes
                    )
                    val subtitle = installVolumeSubtitle(
                        steam.steamInstallVolume,
                        steam.availableVolumes
                    )
                    CyclePreference(
                        title = "Install Path",
                        value = volumeLabel,
                        subtitle = subtitle,
                        isFocused = isFocused(item),
                        onClick = { viewModel.cycleSteamInstallVolume() }
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
                        is LibrarySyncState.FetchingProtonDbRatings -> "Fetching ratings..."
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
    } else {
        val listState = rememberLazyListState()

        LaunchedEffect(uiState.focusedIndex) {
            if (uiState.focusedIndex == 0) {
                val layoutInfo = listState.layoutInfo
                val viewportHeight = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset
                val itemHeight = layoutInfo.visibleItemsInfo.firstOrNull()?.size ?: 60
                val centerOffset = (viewportHeight - itemHeight) / 2
                listState.animateScrollToItem(0, -centerOffset)
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(horizontal = Dimens.spacingMd),
            contentPadding = PaddingValues(top = Dimens.spacingMd, bottom = Dimens.spacingXxl),
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
        ) {
            // State priority: GN check -> QR visible -> auth in progress ->
            // connecting -> idle error -> default (connect button).
            //
            // Errors are only surfaced when the user is idle (DISCONNECTED, no
            // active auth flow).  Transient errors during connect/reconnect are
            // swallowed so the user never sees a flash.
            //
            // CONNECTED/LOGGING_IN without an active auth flow means the user
            // cancelled QR auth while the TCP connection was still alive -- show
            // the connect button, not a stuck "Logging in" spinner.
            when {
                !steam.gnInstalled -> item {
                    GnNotInstalledContent(
                        isFocused = uiState.focusedIndex == 0,
                        onInstall = {
                            val intent = Intent(Intent.ACTION_VIEW,
                                Uri.parse("https://github.com/utkarshdalal/GameNative/releases"))
                            context.startActivity(intent)
                        }
                    )
                }

                steam.qrUrl != null -> item {
                    QrAuthContent(
                        qrUrl = steam.qrUrl,
                        isFocused = uiState.focusedIndex == 0,
                        onCancel = { viewModel.cancelSteamQrAuth() }
                    )
                }

                steam.authPolling -> item {
                    ConnectingContent()
                }

                steam.connectionState == SteamConnectionState.CONNECTING -> item {
                    ConnectingContent()
                }

                steam.error != null &&
                    steam.connectionState == SteamConnectionState.DISCONNECTED -> item {
                    ErrorContent(
                        message = steam.error,
                        isFocused = uiState.focusedIndex == 0,
                        onRetry = {
                            viewModel.connectToSteam()
                            viewModel.startSteamQrAuth()
                        }
                    )
                }

                else -> item {
                    NotConnectedContent(
                        isFocused = uiState.focusedIndex == 0,
                        onConnect = {
                            viewModel.connectToSteam()
                            viewModel.startSteamQrAuth()
                        }
                    )
                }
            }
        }
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
    if (selectedVolume == null) return "Auto"
    return volumes.find { it.path == selectedVolume }?.label ?: "Auto"
}

private fun installVolumeSubtitle(
    selectedVolume: String?,
    volumes: List<com.nendo.argosy.data.steam.SteamInstallVolume>
): String? {
    val vol = if (selectedVolume == null) {
        volumes.find { it.hasGnPath }
    } else {
        volumes.find { it.path == selectedVolume }
    } ?: return null
    val freeMb = vol.freeBytes / (1024 * 1024)
    val freeGb = freeMb / 1024
    val freeLabel = if (freeGb > 0) "${freeGb}GB free" else "${freeMb}MB free"
    return if (selectedVolume == null) "Auto-detect (${vol.label}, $freeLabel)" else freeLabel
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
