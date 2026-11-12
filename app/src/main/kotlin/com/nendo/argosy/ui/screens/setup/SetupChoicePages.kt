package com.nendo.argosy.ui.screens.setup

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.SdCard
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import com.nendo.argosy.R
import com.nendo.argosy.domain.model.FolderSpace
import com.nendo.argosy.ui.components.FocusedScroll
import com.nendo.argosy.ui.primitives.ArgosyProgressBar
import com.nendo.argosy.ui.primitives.PanelSectionHeader
import com.nendo.argosy.ui.screens.setup.components.SetupChoiceRow
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme

private const val IMAGE_CACHE_FOLDER_NAME = "argosy_images"

private sealed interface FolderListItem {
    val key: String

    data class Header(val games: Boolean) : FolderListItem {
        override val key: String get() = if (games) "header-games" else "header-covers"
    }

    data class Rom(val kind: RomFolderKind) : FolderListItem {
        override val key: String get() = "rom-${kind.name}"
    }

    data class Cover(val kind: CoverStorageKind) : FolderListItem {
        override val key: String get() = "cover-${kind.name}"
    }
}

private fun folderList(choices: FolderChoices): List<FolderListItem> =
    listOf<FolderListItem>(FolderListItem.Header(games = true)) +
        choices.romKinds.map { FolderListItem.Rom(it) } +
        FolderListItem.Header(games = false) +
        CoverStorageKind.entries.map { FolderListItem.Cover(it) }

@Composable
private fun spaceLine(path: String, space: FolderSpace?): String {
    val context = LocalContext.current
    return if (space == null) {
        stringResource(R.string.setup_folders_checking_space)
    } else {
        stringResource(R.string.setup_folders_path_free, path, Formatter.formatShortFileSize(context, space.freeBytes))
    }
}

@Composable
internal fun FoldersPage(state: SetupUiState, viewModel: SetupViewModel) {
    val choices = state.folders
    val focus = focusedTarget(state)
    val focusedRom = (focus as? SetupTarget.RomFolder)?.kind
    SetupPageBody(
        state = state,
        viewModel = viewModel,
        context = if (focusedRom != null && choices.pathOf(focusedRom) != null) ({ FolderContext(choices, focusedRom) }) else null,
    ) {
        val listItems = folderList(choices)
        val listState = rememberLazyListState()
        val focusedIndex = listItems.indexOfFirst {
            (it is FolderListItem.Rom && focus == SetupTarget.RomFolder(it.kind)) ||
                (it is FolderListItem.Cover && focus == SetupTarget.CoverStorage(it.kind))
        }
        FocusedScroll(listState = listState, focusedIndex = focusedIndex)
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth().weight(1f),
            verticalArrangement = Arrangement.spacedBy(Dimens.listGap),
        ) {
            items(listItems, key = { it.key }) { item ->
                when (item) {
                    is FolderListItem.Header -> PanelSectionHeader(
                        title = stringResource(if (item.games) R.string.setup_folders_group_games else R.string.setup_folders_group_covers)
                    )
                    is FolderListItem.Rom -> RomFolderRow(choices, item.kind, focus == SetupTarget.RomFolder(item.kind)) {
                        viewModel.tap(SetupTarget.RomFolder(item.kind))
                    }
                    is FolderListItem.Cover -> CoverRow(choices, item.kind, focus == SetupTarget.CoverStorage(item.kind)) {
                        viewModel.tap(SetupTarget.CoverStorage(item.kind))
                    }
                }
            }
        }
    }
}

@Composable
private fun RomFolderRow(choices: FolderChoices, kind: RomFolderKind, focused: Boolean, onClick: () -> Unit) {
    val path = choices.pathOf(kind)
    val available = choices.isAvailable(kind)
    val title = when (kind) {
        RomFolderKind.INTERNAL -> R.string.setup_folders_internal_title
        RomFolderKind.SD_CARD -> R.string.setup_folders_sd_title
        RomFolderKind.CUSTOM -> R.string.setup_folders_custom_title
    }
    val subtitle = when {
        !available -> stringResource(R.string.setup_folders_sd_removed)
        path == null -> stringResource(R.string.setup_folders_custom_subtitle)
        else -> spaceLine(path, choices.space[path])
    }
    SetupChoiceRow(
        icon = when (kind) {
            RomFolderKind.INTERNAL -> Icons.Default.PhoneAndroid
            RomFolderKind.SD_CARD -> Icons.Default.SdCard
            RomFolderKind.CUSTOM -> Icons.Default.CreateNewFolder
        },
        title = stringResource(title),
        subtitle = subtitle,
        selected = available && choices.selectedRom == kind,
        focused = focused,
        enabled = available,
        onClick = onClick,
        error = if (choices.unwritable == kind) stringResource(R.string.setup_folders_error_unwritable) else null,
    )
}

@Composable
private fun CoverRow(choices: FolderChoices, kind: CoverStorageKind, focused: Boolean, onClick: () -> Unit) {
    val publicPath = choices.publicCoverPath
    SetupChoiceRow(
        icon = if (kind == CoverStorageKind.PRIVATE) Icons.Default.Image else Icons.Default.Folder,
        title = stringResource(
            if (kind == CoverStorageKind.PRIVATE) R.string.setup_folders_private_title else R.string.setup_folders_public_title
        ),
        subtitle = when {
            kind == CoverStorageKind.PRIVATE -> stringResource(R.string.setup_folders_private_subtitle)
            publicPath != null -> "$publicPath/$IMAGE_CACHE_FOLDER_NAME"
            else -> stringResource(R.string.setup_folders_public_subtitle)
        },
        selected = choices.selectedCover == kind,
        focused = focused,
        onClick = onClick,
    )
}

@Composable
private fun FolderContext(choices: FolderChoices, kind: RomFolderKind) {
    val context = LocalContext.current
    val path = choices.pathOf(kind) ?: return
    val space = choices.space[path]
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs)) {
        Text(text = path, fontFamily = FontFamily.Monospace, color = LocalArgosyTheme.current.textPrimary)
        if (space != null && space.totalBytes > 0) {
            ArgosyProgressBar(progress = 1f - space.freeBytes.toFloat() / space.totalBytes.toFloat())
            Text(
                stringResource(
                    R.string.setup_folders_free_of_total,
                    Formatter.formatShortFileSize(context, space.freeBytes),
                    Formatter.formatShortFileSize(context, space.totalBytes)
                )
            )
            if (!space.exists) Text(stringResource(R.string.setup_folders_creates))
        }
    }
}

@Composable
internal fun SaveSyncPage(state: SetupUiState, viewModel: SetupViewModel) {
    val focus = focusedTarget(state)
    val server = state.signIn.serverName
    SetupPageBody(
        state = state,
        viewModel = viewModel,
        context = { SaveSyncContext(server) },
    ) {
        if (server.isNotBlank()) {
            Text(
                text = stringResource(R.string.setup_save_sync_purpose, server),
                style = MaterialTheme.typography.bodyMedium,
                color = LocalArgosyTheme.current.textDim,
                modifier = Modifier.padding(bottom = Dimens.spacingMd),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.listGap)) {
            SetupChoiceRow(
                icon = Icons.Default.CloudSync,
                title = stringResource(R.string.firstrun_save_sync_option_on_title),
                subtitle = stringResource(R.string.firstrun_save_sync_option_on_description),
                selected = state.saveSyncOn,
                focused = focus == SetupTarget.SaveSyncChoice(true),
                onClick = { viewModel.tap(SetupTarget.SaveSyncChoice(true)) },
            )
            SetupChoiceRow(
                icon = Icons.Default.PhoneAndroid,
                title = stringResource(R.string.firstrun_save_sync_option_off_title),
                subtitle = stringResource(R.string.firstrun_save_sync_option_off_description),
                selected = !state.saveSyncOn,
                focused = focus == SetupTarget.SaveSyncChoice(false),
                onClick = { viewModel.tap(SetupTarget.SaveSyncChoice(false)) },
            )
        }
    }
}

@Composable
private fun SaveSyncContext(server: String) {
    val theme = LocalArgosyTheme.current
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
        ) {
            Text(text = stringResource(R.string.setup_save_sync_context_device), color = theme.textPrimary)
            Icon(imageVector = Icons.Default.SwapHoriz, contentDescription = null, tint = theme.focusAccent)
            Text(text = server, color = theme.textPrimary)
        }
        Text(stringResource(R.string.setup_save_sync_context_when))
    }
}
