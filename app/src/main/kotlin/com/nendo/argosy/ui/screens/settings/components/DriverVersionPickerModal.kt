package com.nendo.argosy.ui.screens.settings.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nendo.argosy.ui.components.FocusedScroll
import com.nendo.argosy.ui.components.FooterBar
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.ui.screens.settings.DriverDownloadState
import com.nendo.argosy.ui.screens.settings.DriverGroupUi
import com.nendo.argosy.ui.screens.settings.DriverReleaseUi
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalLauncherTheme
import com.nendo.argosy.ui.util.clickableNoFocus

@Composable
fun DriverVersionPickerModal(
    group: DriverGroupUi,
    focusIndex: Int,
    download: DriverDownloadState?,
    downloadedFiles: List<String>,
    onItemTap: (Int) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val listState = rememberLazyListState()
    FocusedScroll(listState = listState, focusedIndex = focusIndex)

    val isDarkTheme = LocalLauncherTheme.current.isDarkTheme
    val overlayColor = if (isDarkTheme) Color.Black.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.5f)
    val terminal = download != null && (download.isComplete || download.error != null)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(overlayColor)
            .clickableNoFocus(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(Dimens.modalWidthLg)
                .clip(RoundedCornerShape(Dimens.radiusLg))
                .background(MaterialTheme.colorScheme.surface)
                .clickableNoFocus(enabled = false) {}
                .padding(Dimens.spacingLg),
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingMd)
        ) {
            Text(
                text = "SELECT VERSION",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = group.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = group.repoPath,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(Dimens.spacingSm))

            when {
                download != null -> DownloadStatusBlock(download)
                group.error != null -> Text(
                    text = group.error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
                group.releases.isEmpty() -> Text(
                    text = "No releases available",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.heightIn(max = 400.dp),
                    verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
                ) {
                    itemsIndexed(group.releases, key = { _, r -> r.tagName }) { index, release ->
                        DriverReleaseRow(
                            release = release,
                            isFocused = index == focusIndex,
                            isDownloaded = release.artifacts.any { it.name in downloadedFiles },
                            onClick = { onItemTap(index) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(Dimens.spacingSm))

            val hints = when {
                download != null && !terminal -> listOf(InputButton.B to "Hide")
                download != null && terminal -> listOf(InputButton.A to "Close")
                else -> listOf(
                    InputButton.DPAD to "Navigate",
                    InputButton.A to "Download",
                    InputButton.B to "Cancel"
                )
            }
            FooterBar(hints = hints)
        }
    }
}

@Composable
private fun DriverReleaseRow(
    release: DriverReleaseUi,
    isFocused: Boolean,
    isDownloaded: Boolean,
    onClick: () -> Unit
) {
    val displayName = release.title.ifBlank { release.tagName }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.radiusMd))
            .background(
                if (isFocused) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
            .clickableNoFocus(onClick = onClick)
            .padding(Dimens.spacingMd),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isDownloaded) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "Downloaded",
                tint = if (isFocused) MaterialTheme.colorScheme.onPrimaryContainer
                       else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(Dimens.iconSm)
            )
            Spacer(modifier = Modifier.width(Dimens.spacingSm))
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isFocused) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurface
                )
                if (release.isLatestStable) {
                    Spacer(modifier = Modifier.width(Dimens.spacingSm))
                    StatusPill(text = "Latest", color = MaterialTheme.colorScheme.primary)
                }
                if (release.prerelease) {
                    Spacer(modifier = Modifier.width(Dimens.spacingSm))
                    StatusPill(text = "Pre", color = MaterialTheme.colorScheme.tertiary)
                }
            }
            val firstArtifact = release.artifacts.firstOrNull()
            val subtitle = when {
                firstArtifact == null -> "No downloadable asset"
                release.artifacts.size == 1 -> formatBytes(firstArtifact.size)
                else -> "${formatBytes(firstArtifact.size)} · ${release.artifacts.size} assets"
            }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = if (isFocused) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DownloadStatusBlock(download: DriverDownloadState) {
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)) {
        when {
            download.error != null -> {
                Text(
                    text = download.artifactName,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = download.error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            download.isComplete -> {
                Text(
                    text = "SAVED TO",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Downloads/drivers/",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = download.artifactName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = Dimens.spacingMd)
                )
            }
            else -> {
                Text(
                    text = download.artifactName,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                val progress = if (download.total > 0) {
                    download.downloaded.toFloat() / download.total
                } else 0f
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(Dimens.radiusSm)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Text(
                    text = "${formatBytes(download.downloaded)} / ${formatBytes(download.total)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun StatusPill(text: String, color: Color) {
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.18f), RoundedCornerShape(Dimens.radiusSm))
            .padding(horizontal = Dimens.spacingSm, vertical = 2.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.SemiBold
        )
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = listOf("B", "KB", "MB", "GB")
    var value = bytes.toDouble()
    var unit = 0
    while (value >= 1024 && unit < units.lastIndex) {
        value /= 1024
        unit++
    }
    return "%.1f %s".format(value, units[unit])
}
