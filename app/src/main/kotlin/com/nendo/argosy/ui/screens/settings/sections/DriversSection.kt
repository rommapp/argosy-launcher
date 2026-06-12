package com.nendo.argosy.ui.screens.settings.sections

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nendo.argosy.ui.components.FocusedScroll
import com.nendo.argosy.ui.components.NavigationPreference
import com.nendo.argosy.ui.screens.settings.DriverGroupUi
import com.nendo.argosy.ui.screens.settings.DriversState
import com.nendo.argosy.ui.screens.settings.SettingsUiState
import com.nendo.argosy.ui.screens.settings.SettingsViewModel
import com.nendo.argosy.ui.screens.settings.components.DriverVersionPickerModal
import com.nendo.argosy.ui.theme.Dimens

@Composable
fun DriversSection(uiState: SettingsUiState, viewModel: SettingsViewModel) {
    val listState = rememberLazyListState()
    val drivers = uiState.drivers
    val pickerIndex = drivers.pickerGroupIndex

    FocusedScroll(listState = listState, focusedIndex = uiState.focusedIndex + 1)

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
            DriverSourceRow(
                group = drivers.groups[index],
                isFocused = uiState.focusedIndex == index,
                onClick = { viewModel.openDriverPicker(index) }
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

    if (pickerIndex != null) {
        val group = drivers.groups.getOrNull(pickerIndex)
        if (group != null) {
            DriverVersionPickerModal(
                group = group,
                focusIndex = drivers.pickerReleaseFocusIndex,
                download = drivers.activeDownload,
                downloadedFiles = drivers.downloadedFiles,
                onItemTap = { idx ->
                    viewModel.moveDriverPickerFocus(idx - drivers.pickerReleaseFocusIndex)
                    viewModel.downloadSelectedDriverRelease()
                },
                onConfirm = { viewModel.downloadSelectedDriverRelease() },
                onDismiss = {
                    if (drivers.activeDownload != null) viewModel.dismissDriverDownload()
                    else viewModel.dismissDriverPicker()
                }
            )
        }
    }
}

@Composable
private fun DriversHeader(drivers: DriversState) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(Dimens.radiusMd))
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
        }
    }
}

@Composable
private fun DriverSourceRow(
    group: DriverGroupUi,
    isFocused: Boolean,
    onClick: () -> Unit
) {
    val subtitle = when {
        group.error != null -> group.error
        group.releases.isEmpty() -> "No releases"
        else -> {
            val latest = group.releases.firstOrNull { !it.prerelease } ?: group.releases.first()
            "${group.releases.size} releases · latest ${latest.title.ifBlank { latest.tagName }}"
        }
    }
    NavigationPreference(
        icon = Icons.Default.Memory,
        title = group.name,
        subtitle = subtitle,
        isFocused = isFocused,
        onClick = onClick
    )
}

@Composable
private fun DownloadedFileRow(fileName: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(Dimens.radiusSm))
            .padding(horizontal = Dimens.spacingMd, vertical = Dimens.spacingSm)
    ) {
        Text(
            text = fileName,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
