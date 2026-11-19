package com.nendo.argosy.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.res.stringResource
import com.nendo.argosy.R
import com.nendo.argosy.ui.components.connections.FocusedActionSlot
import com.nendo.argosy.ui.components.connections.OutsideLaunchButton
import com.nendo.argosy.ui.components.power.PowerCommand
import com.nendo.argosy.ui.components.power.QuickOpenGame
import com.nendo.argosy.ui.components.power.QuickPowerController
import com.nendo.argosy.ui.theme.Motion

@Composable
internal fun QuickPowerRow(
    item: QuickSettingsItem,
    state: QuickSettingsState,
    isFocused: Boolean,
    power: QuickPowerController,
    onFocus: () -> Unit,
    onDismiss: () -> Unit
) {
    when (item) {
        QuickSettingsItem.PowerAccess -> QuickNoticeRow(
            message = stringResource(R.string.ui_quick_settings_power_access_notice),
            actionLabel = stringResource(R.string.ui_quick_settings_power_access_allow),
            isFocused = isFocused,
            onAction = {
                onFocus()
                power.openAccessibilitySettings(onDismiss)
            }
        )

        QuickSettingsItem.PowerSleep -> QuickDetailRow(
            icon = Icons.Default.Bedtime,
            label = stringResource(R.string.ui_quick_settings_power_sleep),
            isFocused = isFocused,
            onClick = {
                onFocus()
                power.sleep(onDismiss)
            }
        )

        QuickSettingsItem.PowerRestart -> QuickDetailRow(
            icon = Icons.Default.RestartAlt,
            label = stringResource(R.string.ui_quick_settings_power_restart),
            isFocused = isFocused,
            onClick = {
                onFocus()
                power.request(PowerCommand.RESTART)
            }
        )

        QuickSettingsItem.PowerOff -> QuickDetailRow(
            icon = Icons.Default.PowerSettingsNew,
            label = stringResource(R.string.ui_quick_settings_power_off),
            isFocused = isFocused,
            onClick = {
                onFocus()
                power.request(PowerCommand.POWER_OFF)
            }
        )

        QuickSettingsItem.PowerMenu -> {
            val open = {
                onFocus()
                power.openSystemPowerMenu(onDismiss)
            }
            val buttonLabel = stringResource(R.string.ui_quick_settings_power_menu_open)
            QuickDetailRow(
                icon = Icons.Default.RestartAlt,
                label = stringResource(R.string.ui_quick_settings_power_menu),
                isFocused = isFocused,
                onClick = open
            ) {
                FocusedActionSlot(
                    shown = isFocused,
                    animationSpec = Motion.focusSpring,
                    rest = {}
                ) {
                    OutsideLaunchButton(label = buttonLabel, onClick = open)
                }
            }
        }

        QuickSettingsItem.OpenGames -> OpenGamesBlock(state.openGames)

        else -> Unit
    }
}

@Composable
private fun OpenGamesBlock(games: List<QuickOpenGame>) {
    if (games.isEmpty()) {
        QuickDetailRow(
            icon = Icons.Default.SportsEsports,
            label = stringResource(R.string.ui_quick_settings_power_open_games_empty),
            isFocused = false,
            onClick = null
        )
        return
    }
    val builtIn = stringResource(R.string.ui_quick_settings_power_open_game_builtin)
    Column {
        games.forEach { game ->
            key(game.taskId) {
                QuickStatusRow(
                    icon = Icons.Default.SportsEsports,
                    label = game.title,
                    valueText = if (game.isBuiltIn) builtIn else game.emulatorLabel.orEmpty()
                )
            }
        }
    }
}
