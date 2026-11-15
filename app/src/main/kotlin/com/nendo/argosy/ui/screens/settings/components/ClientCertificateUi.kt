package com.nendo.argosy.ui.screens.settings.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.nendo.argosy.R
import com.nendo.argosy.ui.common.ClientCertSummary
import com.nendo.argosy.ui.common.rememberCertificateDate
import com.nendo.argosy.ui.components.ActionPreference
import com.nendo.argosy.ui.components.Modal
import com.nendo.argosy.ui.components.SwitchPreference
import com.nendo.argosy.ui.screens.common.ClientCertAction
import com.nendo.argosy.ui.screens.common.ClientCertState
import com.nendo.argosy.ui.theme.Dimens

@Composable
fun ClientCertificatePreference(
    summary: ClientCertSummary?,
    isFocused: Boolean,
    onClick: () -> Unit
) {
    ActionPreference(
        icon = Icons.Default.VerifiedUser,
        title = stringResource(R.string.settings_client_cert_row_title),
        subtitle = if (summary == null) {
            stringResource(R.string.settings_client_cert_row_subtitle_none)
        } else {
            stringResource(
                R.string.settings_client_cert_row_subtitle_active,
                summary.displayName,
                rememberCertificateDate(summary.expiresAtMillis)
            )
        },
        isFocused = isFocused,
        onClick = onClick
    )
}

@Composable
fun ClientCertManagerModal(
    state: ClientCertState,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val summary = state.summary
    Modal(
        title = stringResource(R.string.settings_client_cert_modal_header),
        subtitle = if (summary == null) {
            stringResource(R.string.settings_client_cert_modal_title_none)
        } else {
            stringResource(
                R.string.settings_client_cert_modal_title_active,
                summary.displayName,
                rememberCertificateDate(summary.expiresAtMillis)
            )
        },
        baseWidth = Dimens.modalWidthXl,
        onDismiss = onDismiss
    ) {
        state.actions.forEachIndexed { index, action ->
            val isFocused = index == state.managerFocusIndex
            when (action) {
                ClientCertAction.TOGGLE_ROMM -> SwitchPreference(
                    title = stringResource(R.string.settings_client_cert_use_romm_title),
                    subtitle = stringResource(R.string.settings_client_cert_use_romm_subtitle),
                    isEnabled = summary?.forRomm == true,
                    isFocused = isFocused,
                    onToggle = { onSelect(index) }
                )
                ClientCertAction.TOGGLE_JELLYFIN -> SwitchPreference(
                    title = stringResource(R.string.settings_client_cert_use_jellyfin_title),
                    subtitle = stringResource(R.string.settings_client_cert_use_jellyfin_subtitle),
                    isEnabled = summary?.forJellyfin == true,
                    isFocused = isFocused,
                    onToggle = { onSelect(index) }
                )
                ClientCertAction.IMPORT -> ActionPreference(
                    title = stringResource(
                        if (summary == null) R.string.settings_client_cert_import else R.string.settings_client_cert_replace
                    ),
                    subtitle = stringResource(
                        if (summary == null) {
                            R.string.settings_client_cert_import_subtitle
                        } else {
                            R.string.settings_client_cert_replace_subtitle
                        }
                    ),
                    icon = Icons.Default.FileOpen,
                    isFocused = isFocused,
                    onClick = { onSelect(index) }
                )
                ClientCertAction.REMOVE -> ActionPreference(
                    title = stringResource(R.string.settings_client_cert_remove),
                    subtitle = stringResource(R.string.settings_client_cert_remove_subtitle),
                    icon = Icons.Default.Delete,
                    isDangerous = true,
                    isFocused = isFocused,
                    onClick = { onSelect(index) }
                )
            }
        }
    }
}
