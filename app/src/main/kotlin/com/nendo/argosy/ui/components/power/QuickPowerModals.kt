package com.nendo.argosy.ui.components.power

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import com.nendo.argosy.R
import com.nendo.argosy.ui.primitives.ArgosyConfirmModalHost

@Composable
fun QuickPowerModals(controller: QuickPowerController) {
    val prompt by controller.prompt.collectAsState()
    val state by controller.state.collectAsState()
    val restart = prompt.command == PowerCommand.RESTART
    val gamesOpen = state.openGames.isNotEmpty()
    ArgosyConfirmModalHost(
        visible = prompt.visible,
        title = stringResource(
            if (restart) R.string.ui_quick_settings_power_restart_title else R.string.ui_quick_settings_power_off_title
        ),
        message = stringResource(
            when {
                restart && gamesOpen -> R.string.ui_quick_settings_power_restart_message
                restart -> R.string.ui_quick_settings_power_restart_message_idle
                gamesOpen -> R.string.ui_quick_settings_power_off_message
                else -> R.string.ui_quick_settings_power_off_message_idle
            }
        ),
        confirmLabel = stringResource(
            if (restart) R.string.ui_quick_settings_power_restart_confirm else R.string.ui_quick_settings_power_off_confirm
        ),
        onConfirm = controller::confirm,
        onDismiss = controller::dismiss,
        destructive = true,
        working = if (prompt.working) {
            stringResource(
                if (restart) R.string.ui_quick_settings_power_working_restart else R.string.ui_quick_settings_power_working_off
            )
        } else {
            null
        }
    )
}
