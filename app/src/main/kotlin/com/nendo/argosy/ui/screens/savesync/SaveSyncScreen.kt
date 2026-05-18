package com.nendo.argosy.ui.screens.savesync

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil.compose.AsyncImage
import com.nendo.argosy.ui.common.rememberFileImageModel
import com.nendo.argosy.ui.components.FooterBar
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.ui.input.LocalInputDispatcher
import com.nendo.argosy.ui.navigation.Screen
import com.nendo.argosy.ui.screens.gamedetail.components.SaveStatusInfo
import com.nendo.argosy.ui.screens.gamedetail.components.SaveStatusRow
import com.nendo.argosy.ui.screens.gamedetail.components.SaveSyncStatus
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.util.clickableNoFocus
import com.nendo.argosy.data.local.entity.SaveSyncEntity
import com.nendo.argosy.data.sync.SyncDirection
import com.nendo.argosy.util.formatRelativeTimeVerbose

@Composable
fun SaveSyncScreen(
    onBack: () -> Unit,
    onDrawerToggle: () -> Unit,
    onNavigateToGame: (Long) -> Unit,
    viewModel: SaveSyncViewModel = hiltViewModel()
) {
    val inputDispatcher = LocalInputDispatcher.current
    val inputHandler = remember(onBack, onNavigateToGame) {
        viewModel.createInputHandler(onBack = onBack, onNavigateToGame = onNavigateToGame)
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, inputHandler) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                inputDispatcher.subscribeView(inputHandler, forRoute = Screen.ROUTE_SAVE_SYNC)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        inputDispatcher.subscribeView(inputHandler, forRoute = Screen.ROUTE_SAVE_SYNC)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current

    LaunchedEffect(
        uiState.focusedIndex,
        uiState.attentionRows.size,
        uiState.inProgressRows.size,
        uiState.gameRows.size
    ) {
        if (uiState.allRows.isEmpty()) return@LaunchedEffect
        val info = listState.layoutInfo
        if (info.totalItemsCount == 0) {
            android.util.Log.d("SaveSyncScroll", "skipped: layout not measured yet (totalItemsCount=0)")
            return@LaunchedEffect
        }

        if (uiState.focusedIndex == 0) {
            android.util.Log.d("SaveSyncScroll", "focusedIndex=0 -> animateScrollToItem(0)")
            listState.animateScrollToItem(0)
            return@LaunchedEffect
        }

        val target = uiState.lazyIndexForFocused()
        val viewportHeight = info.viewportEndOffset - info.viewportStartOffset
        val visibleItem = info.visibleItemsInfo.firstOrNull { it.index == target }
        val itemSize = visibleItem?.size ?: 0
        val visibleSummary = info.visibleItemsInfo.joinToString(prefix = "[", postfix = "]") {
            "${it.index}@${it.offset}(${it.size})"
        }

        val centerOffset = -((viewportHeight - itemSize) / 2).coerceAtLeast(0)
        android.util.Log.d(
            "SaveSyncScroll",
            "tick focusedIndex=${uiState.focusedIndex} -> lazyIndex=$target " +
                "attn=${uiState.attentionRows.size} prog=${uiState.inProgressRows.size} games=${uiState.gameRows.size} " +
                "viewport=${viewportHeight} itemSize=$itemSize offset=$centerOffset " +
                "density=${density.density} screenDp=${configuration.screenWidthDp}x${configuration.screenHeightDp} " +
                "visible=$visibleSummary"
        )
        listState.animateScrollToItem(target, centerOffset)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = Dimens.spacingLg, end = Dimens.spacingLg, top = Dimens.spacingLg, bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(Dimens.radiusLg)
        ) {
            item { DeviceHeader(uiState.deviceCard, uiState.otherDevices, uiState.otherDevicesHidden) }

            if (uiState.isEmpty && !uiState.isLoading) {
                item { EmptyState(isConnected = uiState.deviceCard.isConnected) }
            }

            if (uiState.attentionRows.isNotEmpty()) {
                item { SectionHeader("Needs your attention") }
                itemsIndexed(uiState.attentionRows, key = { _, row -> row.key }) { index, row ->
                    AttentionRowCard(
                        row = row,
                        isFocused = index == uiState.focusedIndex,
                        selectedAction = uiState.attentionAction,
                        onActionClick = { action ->
                            viewModel.setAttentionAction(action)
                            viewModel.resolveFocusedAttention(action)
                        }
                    )
                }
            }

            if (uiState.inProgressRows.isNotEmpty()) {
                item { SectionHeader("In progress") }
                val offset = uiState.attentionRows.size
                itemsIndexed(uiState.inProgressRows, key = { _, row -> row.key }) { index, row ->
                    InProgressRowCard(
                        row = row,
                        isFocused = (offset + index) == uiState.focusedIndex
                    )
                }
            }

            if (uiState.gameRows.isNotEmpty()) {
                item { SectionHeader("Games with saves") }
                val offset = uiState.attentionRows.size + uiState.inProgressRows.size
                itemsIndexed(uiState.gameRows, key = { _, row -> row.key }) { index, row ->
                    GameSaveRowCard(
                        row = row,
                        isFocused = (offset + index) == uiState.focusedIndex
                    )
                }
            }
        }

        FooterBar(
            hints = buildFooterHints(uiState),
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

private fun SaveSyncUiState.lazyIndexForFocused(): Int {
    var lazyIndex = 1
    val attn = attentionRows.size
    val prog = inProgressRows.size
    if (focusedIndex < attn) return lazyIndex + 1 + focusedIndex
    if (attn > 0) lazyIndex += 1 + attn
    val progIdx = focusedIndex - attn
    if (progIdx < prog) return lazyIndex + 1 + progIdx
    if (prog > 0) lazyIndex += 1 + prog
    return lazyIndex + 1 + (focusedIndex - attn - prog)
}

private fun buildFooterHints(state: SaveSyncUiState): List<Pair<InputButton, String>> = buildList {
    if (state.allRows.isNotEmpty()) {
        add(InputButton.DPAD_VERTICAL to "Navigate")
    }
    when (val focused = state.focusedRow) {
        is AttentionRow -> {
            add(InputButton.DPAD_HORIZONTAL to "Choose")
            add(InputButton.A to state.attentionAction.confirmLabel())
        }
        is GameSaveRow -> if (!focused.hasConflict) add(InputButton.A to "Open game")
        else -> Unit
    }
    add(InputButton.B to "Back")
}

private fun AttentionAction.confirmLabel(): String = when (this) {
    AttentionAction.KEEP_LOCAL -> "Keep Local"
    AttentionAction.KEEP_SERVER -> "Keep Server"
    AttentionAction.SKIP -> "Skip"
}

@Composable
private fun ThisDeviceCardView(card: ThisDeviceCard, modifier: Modifier = Modifier) {
    val accent = if (card.isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Dimens.radiusLg),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimens.spacingLg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.spacingLg)
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(Dimens.radiusLg))
                    .background(accent.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = iconForPlatform(card.platform),
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(36.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "This device",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                    card.deviceIdShort?.let { id ->
                        Spacer(modifier = Modifier.width(Dimens.spacingSm))
                        DeviceIdPill(id)
                    }
                }
                Spacer(modifier = Modifier.height(Dimens.spacingXs))
                Text(
                    text = card.deviceName ?: "Not connected to RomM",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(Dimens.spacingXs))
                VersionsLine(card.client, card.clientVersion, card.isConnected, card.serverVersion)
            }
            if (card.isConnected) {
                SaveCountChip(card.saveCount)
            }
        }
    }
}

@Composable
private fun VersionsLine(
    client: String?,
    clientVersion: String?,
    connected: Boolean,
    serverVersion: String?
) {
    val fadedColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
    val clientLabel = client?.takeIf { it.isNotBlank() }?.let { name ->
        val versionSuffix = clientVersion?.takeIf { it.isNotBlank() }?.let { " v$it" } ?: ""
        "${name.replaceFirstChar { it.titlecase() }}$versionSuffix"
    }
    val serverLabel = when {
        connected && !serverVersion.isNullOrBlank() -> "RomM v$serverVersion"
        connected -> "Connected"
        else -> "Offline"
    }
    if (clientLabel == null && serverLabel.isBlank()) return
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = clientLabel.orEmpty(),
            style = MaterialTheme.typography.labelMedium,
            color = fadedColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = serverLabel,
            style = MaterialTheme.typography.labelMedium,
            color = fadedColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun DeviceIdPill(deviceIdShort: String) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(Dimens.radiusSm))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = Dimens.spacingSm, vertical = Dimens.spacingXs)
    ) {
        Text(
            text = "…$deviceIdShort",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = color
        )
    }
}

@Composable
private fun SaveCountChip(saveCount: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = saveCount.toString(),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = if (saveCount == 1) "save" else "saves",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun DeviceHeader(card: ThisDeviceCard, others: List<DeviceSummary>, hiddenCount: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingMd),
        verticalAlignment = Alignment.Top
    ) {
        ThisDeviceCardView(card, modifier = Modifier.weight(1f))
        if (others.isNotEmpty()) {
            OtherDevicesPanel(others, hiddenCount, modifier = Modifier.width(260.dp))
        }
    }
}

@Composable
private fun OtherDevicesPanel(devices: List<DeviceSummary>, hiddenCount: Int, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(Dimens.radiusLg),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(horizontal = Dimens.spacingMd, vertical = Dimens.spacingSm)) {
            Text(
                text = "Other devices",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.padding(bottom = Dimens.spacingXs)
            )
            devices.forEach { device ->
                OtherDeviceRow(device)
            }
            if (hiddenCount > 0) {
                Text(
                    text = "+$hiddenCount more",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                    modifier = Modifier.padding(top = Dimens.spacingXs)
                )
            }
        }
    }
}

@Composable
private fun OtherDeviceRow(device: DeviceSummary) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Dimens.spacingXs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) {
        Icon(
            imageVector = if (device.isWeb) Icons.Default.Language else iconForPlatform(device.platform),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = device.deviceName,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = device.saveCount.toString(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

private fun iconForPlatform(platform: String?): androidx.compose.ui.graphics.vector.ImageVector {
    val lower = platform?.lowercase()?.trim()
    return when {
        lower == null -> Icons.Default.Devices
        "android" in lower || "tv" in lower -> Icons.Default.PhoneAndroid
        "ios" in lower || "iphone" in lower || "ipad" in lower -> Icons.Default.Smartphone
        "linux" in lower || "windows" in lower || "mac" in lower || "darwin" in lower || "deck" in lower -> Icons.Default.Computer
        "web" in lower || "browser" in lower -> Icons.Default.Language
        else -> Icons.Default.Devices
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = Dimens.spacingSm, bottom = Dimens.spacingXs)
    )
}

@Composable
private fun EmptyState(isConnected: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Dimens.spacingXl * 2),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = if (isConnected) Icons.Default.CloudSync else Icons.Default.CloudOff,
                contentDescription = null,
                modifier = Modifier.size(Dimens.iconXl),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.height(Dimens.spacingMd))
            Text(
                text = if (isConnected) "No game saves synced yet" else "Connect to RomM to sync saves",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(Dimens.spacingSm))
            Text(
                text = if (isConnected) "Saves will appear here as you play" else "Open Settings to add your RomM server",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

private val AttentionCoverSize = 144.dp

@Composable
private fun AttentionRowCard(
    row: AttentionRow,
    isFocused: Boolean,
    selectedAction: AttentionAction,
    onActionClick: (AttentionAction) -> Unit
) {
    android.util.Log.d(
        "SaveSyncTime",
        "attention conflictId=${row.conflictId} gameId=${row.gameId} title='${row.title}' " +
            "channel='${row.channelName}' localTime=${row.localTime} serverTime=${row.serverTime} " +
            "isLocalNewer=${row.isLocalNewer}"
    )
    val borderColor = if (isFocused) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.surfaceVariant
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(width = 2.dp, color = borderColor, shape = RoundedCornerShape(Dimens.radiusLg)),
        shape = RoundedCornerShape(Dimens.radiusLg),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimens.spacingMd),
            verticalAlignment = Alignment.Top
        ) {
            CoverThumbnail(coverPath = row.coverPath, size = AttentionCoverSize)
            Spacer(modifier = Modifier.width(Dimens.spacingMd))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(Dimens.iconSm)
                    )
                    Spacer(modifier = Modifier.width(Dimens.spacingXs))
                    Text(
                        text = row.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = row.channelDisplay,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.height(Dimens.spacingSm))
                ConflictSide(
                    icon = Icons.Default.PhoneAndroid,
                    label = "Local",
                    time = row.localTime,
                    device = "this device",
                    isNewer = row.isLocalNewer
                )
                Spacer(modifier = Modifier.height(Dimens.spacingXs))
                ConflictSide(
                    icon = Icons.Default.Cloud,
                    label = "Server",
                    time = row.serverTime,
                    device = row.serverDeviceName ?: "another device",
                    isNewer = !row.isLocalNewer
                )
                Spacer(modifier = Modifier.height(Dimens.spacingMd))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
                ) {
                    ActionButton(
                        label = "Keep Local",
                        isSelected = isFocused && selectedAction == AttentionAction.KEEP_LOCAL,
                        onClick = { onActionClick(AttentionAction.KEEP_LOCAL) },
                        modifier = Modifier.weight(1f)
                    )
                    ActionButton(
                        label = "Keep Server",
                        isSelected = isFocused && selectedAction == AttentionAction.KEEP_SERVER,
                        onClick = { onActionClick(AttentionAction.KEEP_SERVER) },
                        modifier = Modifier.weight(1f)
                    )
                    ActionButton(
                        label = "Skip",
                        isSelected = isFocused && selectedAction == AttentionAction.SKIP,
                        onClick = { onActionClick(AttentionAction.SKIP) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun ConflictSide(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    time: java.time.Instant?,
    device: String,
    isNewer: Boolean
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(Dimens.iconSm)
        )
        Spacer(modifier = Modifier.width(Dimens.spacingXs))
        Text(
            text = "$label — ${time?.let { formatRelativeTimeVerbose(it) } ?: "unknown"} ($device)",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (isNewer) {
            Spacer(modifier = Modifier.width(Dimens.spacingXs))
            Text(
                text = "newer",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun ActionButton(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
    val contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.radiusSm))
            .background(containerColor)
            .border(width = 1.5.dp, color = borderColor, shape = RoundedCornerShape(Dimens.radiusSm))
            .clickableNoFocus(onClick = onClick)
            .padding(vertical = Dimens.spacingSm, horizontal = Dimens.spacingMd),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = contentColor
        )
    }
}

@Composable
private fun InProgressRowCard(row: InProgressRow, isFocused: Boolean) {
    val borderColor = if (isFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(width = 2.dp, color = borderColor, shape = RoundedCornerShape(Dimens.radiusLg)),
        shape = RoundedCornerShape(Dimens.radiusLg),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimens.spacingMd),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CoverThumbnail(coverPath = row.coverPath)
            Spacer(modifier = Modifier.width(Dimens.spacingMd))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = row.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (row.direction == SyncDirection.UPLOAD) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(Dimens.iconSm)
                    )
                    Spacer(modifier = Modifier.width(Dimens.spacingXs))
                    Text(
                        text = row.statusLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (row.progress > 0f) {
                    Spacer(modifier = Modifier.height(Dimens.spacingXs))
                    LinearProgressIndicator(
                        progress = { row.progress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun GameSaveRowCard(row: GameSaveRow, isFocused: Boolean) {
    val borderColor = when {
        isFocused && row.hasConflict -> MaterialTheme.colorScheme.error
        isFocused -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(width = 2.dp, color = borderColor, shape = RoundedCornerShape(Dimens.radiusLg)),
        shape = RoundedCornerShape(Dimens.radiusLg),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimens.spacingMd),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CoverThumbnail(coverPath = row.coverPath)
            Spacer(modifier = Modifier.width(Dimens.spacingMd))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = row.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (row.hasConflict) {
                        Spacer(modifier = Modifier.width(Dimens.spacingSm))
                        ConflictPill()
                    } else if (row.isJustSynced) {
                        Spacer(modifier = Modifier.width(Dimens.spacingSm))
                        JustSyncedPill()
                    }
                }
                Text(
                    text = row.platformDisplayName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
                val saveTime = when (row.syncStatus) {
                    SaveSyncEntity.STATUS_SYNCED, SaveSyncEntity.STATUS_SERVER_NEWER ->
                        row.serverUpdatedAt ?: row.localUpdatedAt
                    SaveSyncEntity.STATUS_LOCAL_NEWER, SaveSyncEntity.STATUS_PENDING_UPLOAD ->
                        row.localUpdatedAt ?: row.serverUpdatedAt
                    else -> listOfNotNull(row.serverUpdatedAt, row.localUpdatedAt).maxOrNull()
                }
                SaveStatusRow(
                    status = SaveStatusInfo(
                        status = mapSyncStatus(row.syncStatus),
                        channelName = row.channelDisplay,
                        activeSaveTimestamp = saveTime?.toEpochMilli(),
                        lastSyncTime = saveTime
                    )
                )
                val deviceText = when {
                    row.isLastSyncThisDevice -> "Last write — this device"
                    row.lastSyncDeviceName != null -> "Last write — ${row.lastSyncDeviceName}"
                    else -> "Last write — unknown device"
                }
                val timeSuffix = saveTime?.let { " · ${formatRelativeTimeVerbose(it)}" } ?: ""
                Text(
                    text = "$deviceText$timeSuffix",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun mapSyncStatus(raw: String): SaveSyncStatus = when (raw) {
    SaveSyncEntity.STATUS_SYNCED -> SaveSyncStatus.SYNCED
    SaveSyncEntity.STATUS_LOCAL_NEWER -> SaveSyncStatus.LOCAL_NEWER
    SaveSyncEntity.STATUS_SERVER_NEWER -> SaveSyncStatus.SYNCED
    SaveSyncEntity.STATUS_CONFLICT -> SaveSyncStatus.LOCAL_NEWER
    SaveSyncEntity.STATUS_PENDING_UPLOAD -> SaveSyncStatus.PENDING_UPLOAD
    SaveSyncEntity.STATUS_NEEDS_HARDCORE_RESOLUTION -> SaveSyncStatus.LOCAL_NEWER
    else -> SaveSyncStatus.NOT_CONFIGURED
}

@Composable
private fun CoverThumbnail(coverPath: String?, size: androidx.compose.ui.unit.Dp = 56.dp) {
    val model = rememberFileImageModel(coverPath)
    if (model != null) {
        AsyncImage(
            model = model,
            contentDescription = null,
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(Dimens.radiusSm))
        )
    } else {
        Box(
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(Dimens.radiusSm))
        ) {
            Icon(
                imageVector = Icons.Default.CloudSync,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(size / 2)
            )
        }
    }
}

@Composable
private fun JustSyncedPill() = Pill(text = "Just synced", color = MaterialTheme.colorScheme.primary)

@Composable
private fun ConflictPill() = Pill(text = "Conflict", color = MaterialTheme.colorScheme.error)

@Composable
private fun Pill(text: String, color: androidx.compose.ui.graphics.Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(Dimens.radiusSm))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = Dimens.spacingSm, vertical = Dimens.spacingXs)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}
