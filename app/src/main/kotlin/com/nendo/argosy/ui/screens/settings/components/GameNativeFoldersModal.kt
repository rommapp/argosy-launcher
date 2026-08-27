package com.nendo.argosy.ui.screens.settings.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.nendo.argosy.R
import com.nendo.argosy.data.launcher.GameNativeSyncFolder
import com.nendo.argosy.ui.components.DualActionPreference
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.ui.components.Modal
import com.nendo.argosy.ui.screens.settings.sections.formatPath
import com.nendo.argosy.ui.theme.Dimens

/**
 * Per-store folder pickers for GameNative's Frontend Sync export. Each row owns a Set/Change and a
 * Clear button; both are reachable on a gamepad through the row's action index, because on TV
 * there is no touch fallback for a reset affordance.
 */
@Composable
fun GameNativeFoldersModal(
    paths: Map<GameNativeSyncFolder, String>,
    focusIndex: Int,
    actionIndex: Int,
    onPick: (GameNativeSyncFolder) -> Unit,
    onClear: (GameNativeSyncFolder) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    Modal(
        title = stringResource(R.string.settings_gamenative_folders_title),
        subtitle = stringResource(R.string.settings_gamenative_folders_subtitle),
        baseWidth = Dimens.modalWidthXl,
        onDismiss = onDismiss,
        inlineFooterHints = true,
        footerHints = listOf(
            InputButton.DPAD_HORIZONTAL to stringResource(R.string.settings_gamenative_folders_hint_set),
            InputButton.A to stringResource(R.string.settings_gamenative_folders_hint_select),
            InputButton.B to stringResource(R.string.settings_gamenative_folders_hint_back)
        )
    ) {
        LazyColumn(
            modifier = Modifier.weight(1f, fill = false),
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
        ) {
            itemsIndexed(
                GameNativeSyncFolder.entries,
                key = { _, folder -> folder.slug }
            ) { index, folder ->
                val path = paths[folder]
                DualActionPreference(
                    title = folder.displayName,
                    subtitle = formatPath(context, path),
                    primaryLabel = if (path == null) {
                        stringResource(R.string.settings_gamenative_folders_action_set)
                    } else {
                        stringResource(R.string.settings_gamenative_folders_action_change)
                    },
                    secondaryLabel = stringResource(R.string.settings_gamenative_folders_action_clear),
                    showSecondary = path != null,
                    isFocused = focusIndex == index,
                    actionIndex = actionIndex,
                    onPrimary = { onPick(folder) },
                    onSecondary = { onClear(folder) }
                )
            }
        }
    }
}
