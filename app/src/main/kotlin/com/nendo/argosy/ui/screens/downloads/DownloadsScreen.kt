package com.nendo.argosy.ui.screens.downloads

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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.nendo.argosy.data.download.DownloadProgress
import com.nendo.argosy.data.download.DownloadState
import com.nendo.argosy.ui.input.LocalInputDispatcher
import com.nendo.argosy.ui.navigation.Screen
import com.nendo.argosy.ui.components.FooterBar
import com.nendo.argosy.ui.components.InputButton

@Composable
fun DownloadsScreen(
    onBack: () -> Unit,
    onDrawerToggle: () -> Unit,
    viewModel: DownloadsViewModel = hiltViewModel()
) {
    val inputDispatcher = LocalInputDispatcher.current
    val inputHandler = remember(onBack) {
        viewModel.createInputHandler(onBack = onBack)
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, inputHandler) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                inputDispatcher.subscribeView(inputHandler, forRoute = Screen.ROUTE_DOWNLOADS)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        inputDispatcher.subscribeView(inputHandler, forRoute = Screen.ROUTE_DOWNLOADS)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val uiState by viewModel.uiState.collectAsState()
    val state = uiState.downloadState
    val listState = rememberLazyListState()

    LaunchedEffect(uiState.focusedIndex) {
        if (uiState.allItems.isNotEmpty()) {
            listState.animateScrollToItem(uiState.focusedIndex)
        }
    }

    val hasAnyDownloads = state.activeDownloads.isNotEmpty() ||
        state.queue.isNotEmpty() ||
        state.completed.isNotEmpty()

    if (!hasAnyDownloads) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "No Downloads",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Downloads will appear here",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
        return
    }

    val activeItems = uiState.activeItems
    val queuedItems = uiState.queuedItems

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (activeItems.isNotEmpty()) {
                val hasDownloading = activeItems.any { it.state == DownloadState.DOWNLOADING }
                val headerText = if (hasDownloading) "Downloading" else "Active"
                item { SectionHeader(headerText) }
                itemsIndexed(activeItems) { index, download ->
                    DownloadItem(
                        download = download,
                        isFocused = index == uiState.focusedIndex,
                        availableStorage = state.availableStorageBytes
                    )
                }
            }

            if (queuedItems.isNotEmpty()) {
                item { SectionHeader("Queued") }
                itemsIndexed(queuedItems) { index, download ->
                    DownloadItem(
                        download = download,
                        isFocused = (activeItems.size + index) == uiState.focusedIndex,
                        availableStorage = state.availableStorageBytes
                    )
                }
            }

            if (state.completed.isNotEmpty()) {
                item { SectionHeader("Completed") }
                itemsIndexed(state.completed) { _, download ->
                    CompletedDownloadItem(download = download)
                }
            }
        }

        if (uiState.allItems.isNotEmpty()) {
            val footerHints = buildList {
                add(InputButton.DPAD_VERTICAL to "Navigate")
                if (uiState.canToggle) {
                    add(InputButton.SOUTH to uiState.toggleLabel)
                }
                if (uiState.canCancel) {
                    add(InputButton.WEST to "Cancel")
                }
                add(InputButton.EAST to "Back")
            }

            FooterBar(
                hints = footerHints,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
private fun DownloadItem(
    download: DownloadProgress,
    isFocused: Boolean,
    availableStorage: Long
) {
    val borderColor = if (isFocused) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }

    val (statusIcon, statusText, statusColor) = when (download.state) {
        DownloadState.DOWNLOADING -> Triple(
            Icons.Default.Download,
            "${formatBytes(download.bytesDownloaded)} / ${formatBytes(download.totalBytes)}",
            MaterialTheme.colorScheme.primary
        )
        DownloadState.PAUSED -> Triple(
            Icons.Default.Pause,
            "Paused - ${formatBytes(download.bytesDownloaded)} / ${formatBytes(download.totalBytes)}",
            MaterialTheme.colorScheme.onSurfaceVariant
        )
        DownloadState.WAITING_FOR_STORAGE -> Triple(
            Icons.Default.Warning,
            "Waiting for storage - Need ${formatBytes(download.totalBytes - download.bytesDownloaded)}, Available ${formatBytes(availableStorage)}",
            MaterialTheme.colorScheme.error
        )
        DownloadState.FAILED -> Triple(
            Icons.Default.Error,
            download.errorReason ?: "Download failed",
            MaterialTheme.colorScheme.error
        )
        DownloadState.QUEUED -> Triple(
            Icons.Default.Schedule,
            "Queued",
            MaterialTheme.colorScheme.onSurfaceVariant
        )
        else -> Triple(
            Icons.Default.Schedule,
            "Queued",
            MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 2.dp,
                color = borderColor,
                shape = RoundedCornerShape(12.dp)
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DownloadCover(download)
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = download.displayTitle,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = download.platformSlug.uppercase(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (download.state == DownloadState.DOWNLOADING) {
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { download.progressPercent },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = statusColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Icon(
                imageVector = statusIcon,
                contentDescription = null,
                tint = statusColor,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun CompletedDownloadItem(download: DownloadProgress) {
    val (icon, iconColor) = when (download.state) {
        DownloadState.COMPLETED -> Icons.Default.CheckCircle to MaterialTheme.colorScheme.primary
        DownloadState.FAILED -> Icons.Default.Error to MaterialTheme.colorScheme.error
        else -> Icons.Default.CheckCircle to MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DownloadCover(download)
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = download.displayTitle,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = download.platformSlug.uppercase(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (download.state == DownloadState.FAILED && download.errorReason != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = download.errorReason,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor
            )
        }
    }
}

@Composable
private fun DownloadCover(download: DownloadProgress) {
    if (download.coverPath != null) {
        AsyncImage(
            model = download.coverPath,
            contentDescription = download.gameTitle,
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop
        )
    } else {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Download,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
    val safeIndex = digitGroups.coerceIn(0, units.lastIndex)
    return String.format("%.1f %s", bytes / Math.pow(1024.0, safeIndex.toDouble()), units[safeIndex])
}
