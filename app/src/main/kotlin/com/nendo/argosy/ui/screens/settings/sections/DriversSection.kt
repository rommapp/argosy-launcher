package com.nendo.argosy.ui.screens.settings.sections

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nendo.argosy.ui.screens.settings.DriverGroupUi
import com.nendo.argosy.ui.screens.settings.DriverReleaseUi
import com.nendo.argosy.ui.screens.settings.DriversState
import com.nendo.argosy.ui.screens.settings.SettingsUiState
import com.nendo.argosy.ui.screens.settings.SettingsViewModel
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.util.clickableNoFocus

@Composable
fun DriversSection(uiState: SettingsUiState, viewModel: SettingsViewModel) {
    val listState = rememberLazyListState()
    val drivers = uiState.drivers

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().padding(Dimens.spacingMd),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) {
        item(key = "header") {
            DriversHeader(drivers)
        }

        if (drivers.isLoading && drivers.groups.isEmpty()) {
            item(key = "loading") {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(Dimens.spacingLg),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }
            }
        }

        items(drivers.groups.size, key = { drivers.groups[it].repoPath }) { index ->
            val group = drivers.groups[index]
            DriverGroupRow(
                group = group,
                isFocused = uiState.focusedIndex == index,
                isExpanded = drivers.expandedGroupIndex == index,
                releaseFocusIndex = drivers.releaseFocusIndex,
                downloadedFiles = drivers.downloadedFiles,
                onToggleExpand = { viewModel.driversDelegate.toggleGroupExpanded(index) },
                onDownloadRelease = { release ->
                    val artifact = release.artifacts.firstOrNull() ?: return@DriverGroupRow
                    viewModel.downloadDriverArtifact(artifact)
                }
            )
        }

        if (drivers.downloadedFiles.isNotEmpty()) {
            item(key = "downloads_header") {
                Spacer(modifier = Modifier.height(Dimens.spacingMd))
                Text(
                    text = "DOWNLOADED",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = Dimens.spacingSm)
                )
            }
            items(drivers.downloadedFiles, key = { "dl_$it" }) { fileName ->
                DownloadedFileRow(fileName)
            }
        }
    }

    drivers.activeDownload?.let { active ->
        DownloadProgressOverlay(
            artifactName = active.artifactName,
            downloaded = active.downloaded,
            total = active.total,
            isComplete = active.isComplete,
            error = active.error,
            onDismiss = { viewModel.driversDelegate.dismissActiveDownload() }
        )
    }
}

@Composable
private fun DriversHeader(drivers: DriversState) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(Dimens.spacingSm)
            )
            .padding(Dimens.spacingMd)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs)) {
            Text(
                text = "GPU",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = drivers.gpuModel?.takeIf { it.isNotBlank() } ?: "Unknown",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(Dimens.spacingXs))
            Text(
                text = "RECOMMENDED",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = drivers.recommendedDriver,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(Dimens.spacingXs))
            Text(
                text = "Downloads land in Downloads/drivers on internal storage.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DriverGroupRow(
    group: DriverGroupUi,
    isFocused: Boolean,
    isExpanded: Boolean,
    releaseFocusIndex: Int,
    downloadedFiles: List<String>,
    onToggleExpand: () -> Unit,
    onDownloadRelease: (DriverReleaseUi) -> Unit
) {
    val borderColor = if (isFocused) MaterialTheme.colorScheme.primary else Color.Transparent
    val backgroundColor = if (isFocused) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor, RoundedCornerShape(Dimens.spacingSm))
            .border(Dimens.borderThin, borderColor, RoundedCornerShape(Dimens.spacingSm))
            .clickableNoFocus { onToggleExpand() }
            .padding(Dimens.spacingMd)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = group.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = group.repoPath,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                group.error?.let { err ->
                    Text(
                        text = err,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            Icon(
                imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (isExpanded && group.releases.isNotEmpty()) {
            Spacer(modifier = Modifier.height(Dimens.spacingSm))
            group.releases.forEachIndexed { index, release ->
                ReleaseRow(
                    release = release,
                    isFocused = index == releaseFocusIndex,
                    isDownloaded = release.artifacts.any { it.name in downloadedFiles },
                    onClick = { onDownloadRelease(release) }
                )
            }
        }

        if (isExpanded && group.releases.isEmpty() && group.error == null) {
            Text(
                text = "No releases available",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Dimens.spacingSm)
            )
        }
    }
}

@Composable
private fun ReleaseRow(
    release: DriverReleaseUi,
    isFocused: Boolean,
    isDownloaded: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (isFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Dimens.spacingXs)
            .border(Dimens.borderThin, borderColor, RoundedCornerShape(Dimens.spacingSm))
            .clickableNoFocus { onClick() }
            .padding(horizontal = Dimens.spacingMd, vertical = Dimens.spacingSm)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = release.title.ifBlank { release.tagName },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium
                )
                if (release.isLatestStable) {
                    Spacer(modifier = Modifier.width(Dimens.spacingSm))
                    Pill(text = "Latest", color = MaterialTheme.colorScheme.primary)
                }
                if (release.prerelease) {
                    Spacer(modifier = Modifier.width(Dimens.spacingSm))
                    Pill(text = "Pre", color = MaterialTheme.colorScheme.tertiary)
                }
            }
            if (release.artifacts.isEmpty()) {
                Text(
                    text = "No downloadable assets",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
        Icon(
            imageVector = if (isDownloaded) Icons.Default.CheckCircle else Icons.Default.Download,
            contentDescription = null,
            tint = if (isDownloaded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun Pill(text: String, color: Color) {
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.18f), RoundedCornerShape(Dimens.spacingSm))
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

@Composable
private fun DownloadedFileRow(fileName: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(Dimens.spacingSm))
            .padding(horizontal = Dimens.spacingMd, vertical = Dimens.spacingSm)
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(Dimens.spacingSm))
        Text(
            text = fileName,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun DownloadProgressOverlay(
    artifactName: String,
    downloaded: Long,
    total: Long,
    isComplete: Boolean,
    error: String?,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickableNoFocus { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(Dimens.spacingMd))
                .padding(Dimens.spacingLg)
                .width(320.dp),
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
        ) {
            Text(
                text = artifactName,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            when {
                error != null -> Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
                isComplete -> Text(
                    text = "Saved to Downloads/drivers",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
                else -> {
                    val progress = if (total > 0) downloaded.toFloat() / total else 0f
                    LinearProgressIndicator(
                        progress = { progress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "${formatBytes(downloaded)} / ${formatBytes(total)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(
                text = "A to dismiss",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
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
