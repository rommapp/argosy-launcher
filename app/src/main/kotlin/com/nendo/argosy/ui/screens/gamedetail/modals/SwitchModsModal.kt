package com.nendo.argosy.ui.screens.gamedetail.modals

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.nendo.argosy.R
import com.nendo.argosy.data.mod.ModInstallFailure
import com.nendo.argosy.data.mod.SwitchModProfile
import com.nendo.argosy.ui.components.FocusedScroll
import com.nendo.argosy.ui.components.Modal
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.ModalInputEffect
import com.nendo.argosy.ui.screens.gamedetail.components.OptionItem
import com.nendo.argosy.ui.screens.gamedetail.delegates.ModInstallAction
import com.nendo.argosy.ui.screens.gamedetail.delegates.ModInstallUiState
import com.nendo.argosy.ui.theme.Dimens

@Composable
fun SwitchModsModal(
    state: ModInstallUiState,
    handler: InputHandler,
    onActivate: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    if (!state.visible) return
    ModalInputEffect(active = true, handler = handler)
    Modal(
        title = stringResource(R.string.gamedetail_mods_title),
        subtitle = state.selected?.fileName ?: state.emulatorName,
        onDismiss = onDismiss
    ) {
        if (state.loading || state.working) {
            CircularProgressIndicator()
            Text(stringResource(if (state.loading) R.string.gamedetail_mods_loading else R.string.gamedetail_mods_working))
        } else {
            state.error?.let { Text(stringResource(it.labelRes), color = MaterialTheme.colorScheme.error) }
            if (state.succeeded) Text(stringResource(R.string.gamedetail_mods_success))
            if (state.skippedDesktopSettings) Text(stringResource(R.string.gamedetail_mods_settings_skipped))
            if (state.selected == null && state.packages.isEmpty()) {
                Text(stringResource(R.string.gamedetail_mods_empty))
            }
            if (state.selected != null) Text(stringResource(R.string.gamedetail_mods_compatibility))
            Spacer(Modifier.height(Dimens.spacingSm))
            val listState = rememberLazyListState()
            FocusedScroll(listState, state.focusIndex)
            LazyColumn(state = listState, modifier = Modifier.weight(1f, fill = false)) {
                if (state.selected == null) {
                    itemsIndexed(state.packages, key = { _, item -> "${item.titleId}:${item.packageId}" }) { index, item ->
                        OptionItem(
                            label = item.fileName,
                            subtext = stringResource(when {
                                item.needsRecovery -> R.string.gamedetail_mods_status_recovery
                                item.installed -> R.string.gamedetail_mods_status_installed
                                else -> R.string.gamedetail_mods_status_downloaded
                            }),
                            isFocused = state.focusIndex == index,
                            onClick = { onActivate(index) }
                        )
                    }
                    item(key = "back") {
                        OptionItem(label = stringResource(R.string.gamedetail_mods_close),
                            isFocused = state.focusIndex == state.packages.size,
                            onClick = { onActivate(state.packages.size) })
                    }
                } else {
                    itemsIndexed(state.actions, key = { _, item -> item.name }) { index, action ->
                        OptionItem(
                            label = stringResource(action.labelRes(state.selected.installed)),
                            subtext = when (action) {
                                ModInstallAction.HANDHELD_PROFILE -> stringResource(R.string.gamedetail_mods_handheld_description)
                                ModInstallAction.ORIGINAL_PROFILE -> stringResource(R.string.gamedetail_mods_original_description)
                                ModInstallAction.RECOVER -> stringResource(R.string.gamedetail_mods_restore_description)
                                else -> null
                            },
                            isFocused = state.focusIndex == index,
                            isSelected = when (action) {
                                ModInstallAction.ACKNOWLEDGE -> state.confirmedClosed
                                ModInstallAction.HANDHELD_PROFILE -> state.profile == SwitchModProfile.HANDHELD_720
                                ModInstallAction.ORIGINAL_PROFILE -> state.profile == SwitchModProfile.ORIGINAL
                                else -> false
                            },
                            isDangerous = action == ModInstallAction.REMOVE || action == ModInstallAction.RECOVER,
                            onClick = { onActivate(index) }
                        )
                    }
                }
            }
        }
    }
}

@StringRes
private fun ModInstallAction.labelRes(installed: Boolean): Int = when (this) {
    ModInstallAction.HANDHELD_PROFILE -> R.string.gamedetail_mods_handheld
    ModInstallAction.ORIGINAL_PROFILE -> R.string.gamedetail_mods_original
    ModInstallAction.ACKNOWLEDGE -> R.string.gamedetail_mods_acknowledgement
    ModInstallAction.INSTALL -> if (installed) R.string.gamedetail_mods_update else R.string.gamedetail_mods_install
    ModInstallAction.REMOVE -> R.string.gamedetail_mods_remove
    ModInstallAction.RECOVER -> R.string.gamedetail_mods_restore
    ModInstallAction.BACK -> R.string.gamedetail_mods_back
}

private val ModInstallFailure.labelRes: Int get() = when (this) {
    ModInstallFailure.INVALID_TITLE -> R.string.gamedetail_mods_error_title
    ModInstallFailure.ACCESS_DENIED -> R.string.gamedetail_mods_error_access
    ModInstallFailure.CONFLICT -> R.string.gamedetail_mods_error_conflict
    ModInstallFailure.MODIFIED_FILE -> R.string.gamedetail_mods_error_modified
    ModInstallFailure.NEEDS_RECOVERY -> R.string.gamedetail_mods_error_recovery
    ModInstallFailure.EMULATOR_CLOSE_REQUIRED -> R.string.gamedetail_mods_error_acknowledgement
    ModInstallFailure.UNSUPPORTED_EMULATOR -> R.string.gamedetail_mods_error_emulator
    ModInstallFailure.CORRUPT_STATE -> R.string.gamedetail_mods_error_state
    ModInstallFailure.UNSUPPORTED_PROFILE -> R.string.gamedetail_mods_error_profile
    else -> R.string.gamedetail_mods_error_package
}
