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
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil.compose.AsyncImage
import com.nendo.argosy.R
import com.nendo.argosy.ui.common.rememberFileImageModel
import com.nendo.argosy.ui.components.FooterHints
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.ui.input.LocalInputDispatcher
import com.nendo.argosy.ui.navigation.Screen
import com.nendo.argosy.ui.primitives.ArgosyProgressBar
import com.nendo.argosy.ui.screens.gamedetail.components.SaveStatusInfo
import com.nendo.argosy.ui.screens.gamedetail.components.SaveStatusRow
import com.nendo.argosy.ui.screens.gamedetail.components.mapSaveSyncStatus
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalLauncherTheme
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
    val forceCheck by viewModel.forceCheckStatus.collectAsState()
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

            uiState.accessNotice?.let { notice ->
                item { AccessNoticeCard(notice) }
            }

            if (uiState.isEmpty && !uiState.isLoading) {
                item { EmptyState(isConnected = uiState.deviceCard.isConnected) }
            }

            if (uiState.attentionRows.isNotEmpty()) {
                item { SectionHeader(stringResource(R.string.savesync_section_attention)) }
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
                item { SectionHeader(stringResource(R.string.savesync_section_in_progress)) }
                val offset = uiState.attentionRows.size
                itemsIndexed(uiState.inProgressRows, key = { _, row -> row.key }) { index, row ->
                    InProgressRowCard(
                        row = row,
                        isFocused = (offset + index) == uiState.focusedIndex
                    )
                }
            }

            if (uiState.gameRows.isNotEmpty()) {
                item { SectionHeader(stringResource(R.string.savesync_section_games)) }
                val offset = uiState.attentionRows.size + uiState.inProgressRows.size
                itemsIndexed(uiState.gameRows, key = { _, row -> row.key }) { index, row ->
                    GameSaveRowCard(
                        row = row,
                        isFocused = (offset + index) == uiState.focusedIndex
                    )
                }
            }

            if (uiState.deviceCard.isConnected) {
                item {
                    ForceSaveCheckCard(
                        state = forceCheck,
                        onClick = { viewModel.forceSaveCheck() },
                        onDismiss = { viewModel.dismissForceCheckStatus() }
                    )
                }
            }
        }

        FooterHints(
            hints = buildFooterHints(uiState),
            onHintClick = { button ->
                when (button) {
                    InputButton.A -> { inputHandler.onConfirm() }
                    InputButton.Y -> { inputHandler.onSecondaryAction() }
                    InputButton.B -> { inputHandler.onBack() }
                    else -> Unit
                }
            }
        )
    }
}

private fun SaveSyncUiState.lazyIndexForFocused(): Int {
    var lazyIndex = if (accessNotice != null) 2 else 1
    val attn = attentionRows.size
    val prog = inProgressRows.size
    if (focusedIndex < attn) return lazyIndex + 1 + focusedIndex
    if (attn > 0) lazyIndex += 1 + attn
    val progIdx = focusedIndex - attn
    if (progIdx < prog) return lazyIndex + 1 + progIdx
    if (prog > 0) lazyIndex += 1 + prog
    return lazyIndex + 1 + (focusedIndex - attn - prog)
}

@Composable
private fun buildFooterHints(state: SaveSyncUiState): List<Pair<InputButton, String>> {
    val navigateLabel = stringResource(R.string.savesync_footer_navigate)
    val chooseLabel = stringResource(R.string.savesync_footer_choose)
    val confirmLabel = state.attentionAction.confirmLabel()
    val openGameLabel = stringResource(R.string.savesync_footer_open_game)
    val scanLabel = stringResource(R.string.savesync_footer_scan_server)
    val backLabel = stringResource(R.string.savesync_footer_back)
    return buildList {
        if (state.allRows.isNotEmpty()) {
            add(InputButton.DPAD_VERTICAL to navigateLabel)
        }
        when (val focused = state.focusedRow) {
            is AttentionRow -> {
                add(InputButton.DPAD_HORIZONTAL to chooseLabel)
                add(InputButton.A to confirmLabel)
            }
            is GameSaveRow -> if (!focused.hasConflict) add(InputButton.A to openGameLabel)
            else -> Unit
        }
        if (state.deviceCard.isConnected) {
            add(InputButton.Y to scanLabel)
        }
        add(InputButton.B to backLabel)
    }
}

@Composable
private fun AttentionAction.confirmLabel(): String = stringResource(
    when (this) {
        AttentionAction.KEEP_LOCAL -> R.string.savesync_footer_confirm_keep_local
        AttentionAction.KEEP_SERVER -> R.string.savesync_footer_confirm_keep_server
        AttentionAction.SKIP -> R.string.savesync_footer_confirm_skip
    }
)

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
                        text = stringResource(R.string.savesync_device_this_label),
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
                    text = card.deviceName ?: stringResource(R.string.savesync_device_not_connected),
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
        val displayName = name.replaceFirstChar { it.titlecase() }
        val version = clientVersion?.takeIf { it.isNotBlank() }
        if (version == null) {
            displayName
        } else {
            stringResource(R.string.savesync_device_client_versioned, displayName, version)
        }
    }
    val serverLabel = when {
        connected && !serverVersion.isNullOrBlank() ->
            stringResource(R.string.savesync_device_server_versioned, serverVersion)
        connected -> stringResource(R.string.savesync_device_server_connected)
        else -> stringResource(R.string.savesync_device_server_offline)
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
            text = stringResource(R.string.savesync_device_id_pill, deviceIdShort),
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
            text = pluralStringResource(R.plurals.savesync_device_save_count_unit, saveCount),
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
                text = stringResource(R.string.savesync_other_devices_heading),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.padding(bottom = Dimens.spacingXs)
            )
            devices.forEach { device ->
                OtherDeviceRow(device)
            }
            if (hiddenCount > 0) {
                Text(
                    text = pluralStringResource(
                        R.plurals.savesync_other_devices_more,
                        hiddenCount,
                        hiddenCount
                    ),
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
private fun AccessNoticeCard(notice: SaveAccessNoticeUi) {
    val warningColor = LocalLauncherTheme.current.semanticColors.warning
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Dimens.radiusLg),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimens.spacingMd),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.spacingMd)
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = warningColor,
                modifier = Modifier.size(Dimens.iconMd)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = pluralStringResource(
                        R.plurals.savesync_access_notice_title,
                        notice.count,
                        notice.count
                    ),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = notice.emulatorNames.joinToString(", "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
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
                text = if (isConnected) {
                    stringResource(R.string.savesync_empty_title_connected)
                } else {
                    stringResource(R.string.savesync_empty_title_disconnected)
                },
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(Dimens.spacingSm))
            Text(
                text = if (isConnected) {
                    stringResource(R.string.savesync_empty_message_connected)
                } else {
                    stringResource(R.string.savesync_empty_message_disconnected)
                },
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
                    label = stringResource(R.string.savesync_conflict_side_local),
                    time = row.localTime,
                    device = stringResource(R.string.savesync_conflict_device_this),
                    isNewer = row.isLocalNewer
                )
                Spacer(modifier = Modifier.height(Dimens.spacingXs))
                ConflictSide(
                    icon = Icons.Default.Cloud,
                    label = stringResource(R.string.savesync_conflict_side_server),
                    time = row.serverTime,
                    device = row.serverDeviceName
                        ?: stringResource(R.string.savesync_conflict_device_other),
                    isNewer = !row.isLocalNewer
                )
                Spacer(modifier = Modifier.height(Dimens.spacingMd))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
                ) {
                    ActionButton(
                        label = stringResource(R.string.savesync_attention_button_keep_local),
                        isSelected = isFocused && selectedAction == AttentionAction.KEEP_LOCAL,
                        onClick = { onActionClick(AttentionAction.KEEP_LOCAL) },
                        modifier = Modifier.weight(1f)
                    )
                    ActionButton(
                        label = stringResource(R.string.savesync_attention_button_keep_server),
                        isSelected = isFocused && selectedAction == AttentionAction.KEEP_SERVER,
                        onClick = { onActionClick(AttentionAction.KEEP_SERVER) },
                        modifier = Modifier.weight(1f)
                    )
                    ActionButton(
                        label = stringResource(R.string.savesync_attention_button_skip),
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
        val timeText = time?.let { formatRelativeTimeVerbose(LocalContext.current, it) }
            ?: stringResource(R.string.savesync_conflict_time_unknown)
        Text(
            text = stringResource(
                R.string.savesync_conflict_side_summary,
                label,
                timeText,
                device
            ),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (isNewer) {
            Spacer(modifier = Modifier.width(Dimens.spacingXs))
            Text(
                text = stringResource(R.string.savesync_conflict_newer_badge),
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
                    ArgosyProgressBar(progress = row.progress.coerceIn(0f, 1f))
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
                        status = mapSaveSyncStatus(row.syncStatus),
                        channelName = row.channelDisplay,
                        activeSaveTimestamp = saveTime?.toEpochMilli(),
                        lastSyncTime = saveTime
                    )
                )
                val deviceText = when {
                    row.isLastSyncThisDevice ->
                        stringResource(R.string.savesync_game_last_write_this_device)
                    row.lastSyncDeviceName != null ->
                        stringResource(R.string.savesync_game_last_write_device, row.lastSyncDeviceName)
                    else -> stringResource(R.string.savesync_game_last_write_unknown)
                }
                val lastWriteText = saveTime?.let {
                    stringResource(
                        R.string.savesync_game_last_write_time,
                        deviceText,
                        formatRelativeTimeVerbose(LocalContext.current, it)
                    )
                } ?: deviceText
                Text(
                    text = lastWriteText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
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
private fun JustSyncedPill() = Pill(
    text = stringResource(R.string.savesync_game_pill_just_synced),
    color = MaterialTheme.colorScheme.primary
)

@Composable
private fun ConflictPill() = Pill(
    text = stringResource(R.string.savesync_game_pill_conflict),
    color = MaterialTheme.colorScheme.error
)

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

@Composable
private fun ForceSaveCheckCard(
    state: ForceSaveCheckUiState,
    onClick: () -> Unit,
    onDismiss: () -> Unit
) {
    val running = state is ForceSaveCheckUiState.Running
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickableNoFocus(enabled = !running) { if (state is ForceSaveCheckUiState.Complete || state is ForceSaveCheckUiState.Failed) onDismiss() else onClick() },
        shape = RoundedCornerShape(Dimens.radiusLg),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(Dimens.spacingLg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.spacingLg)
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.savesync_force_check_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(Dimens.spacingXs))
                val subtitle = when (state) {
                    ForceSaveCheckUiState.Idle ->
                        stringResource(R.string.savesync_force_check_idle)
                    ForceSaveCheckUiState.Running ->
                        stringResource(R.string.savesync_force_check_running)
                    is ForceSaveCheckUiState.Complete -> when {
                        state.message != null -> state.message
                        state.queued == 0 -> pluralStringResource(
                            R.plurals.savesync_force_check_up_to_date,
                            state.inspected,
                            state.inspected
                        )
                        else -> pluralStringResource(
                            R.plurals.savesync_force_check_queued,
                            state.queued,
                            state.queued,
                            state.inspected,
                            state.downloaded
                        )
                    }
                    is ForceSaveCheckUiState.Failed ->
                        stringResource(R.string.savesync_force_check_failed, state.message)
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
