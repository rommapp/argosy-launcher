package com.nendo.argosy.ui.common

import androidx.annotation.StringRes
import com.nendo.argosy.R
import com.nendo.argosy.data.emulator.config.ConfigFileState
import com.nendo.argosy.data.emulator.config.ConfigWriteFailure
import com.nendo.argosy.data.emulator.config.EmulatorDataRoute
import com.nendo.argosy.data.emulator.config.EmulatorEditBlock

@get:StringRes
val EmulatorDataRoute.labelRes: Int
    get() = when (this) {
        EmulatorDataRoute.FILE_ACCESS -> R.string.settings_emulator_options_route_file_access
        EmulatorDataRoute.ALT_ACCESS -> R.string.settings_emulator_options_route_alt_access
        EmulatorDataRoute.ROOT -> R.string.settings_emulator_options_route_root
        EmulatorDataRoute.APP_DATA_SHELL -> R.string.settings_emulator_options_route_app_data_shell
    }

@get:StringRes
val ConfigFileState.labelRes: Int
    get() = when (this) {
        ConfigFileState.READ -> R.string.settings_emulator_options_file_read
        ConfigFileState.ABSENT -> R.string.settings_emulator_options_file_missing
        ConfigFileState.BLOCKED -> R.string.settings_emulator_options_file_blocked
    }

@get:StringRes
val ConfigWriteFailure.labelRes: Int
    get() = when (this) {
        ConfigWriteFailure.NOT_EDITABLE, ConfigWriteFailure.INVALID_VALUE ->
            R.string.settings_emulator_options_edit_failed_not_allowed
        ConfigWriteFailure.NO_EDIT_ROUTE -> R.string.settings_emulator_options_edit_failed_no_route
        ConfigWriteFailure.FILE_MISSING -> R.string.settings_emulator_options_edit_failed_missing
        ConfigWriteFailure.FILE_UNREADABLE, ConfigWriteFailure.PATCH_FAILED ->
            R.string.settings_emulator_options_edit_failed_unreadable
        ConfigWriteFailure.EMULATOR_RUNNING -> R.string.settings_emulator_options_edit_failed_running
        ConfigWriteFailure.BACKUP_FAILED, ConfigWriteFailure.WRITE_FAILED ->
            R.string.settings_emulator_options_edit_failed_write
        ConfigWriteFailure.VERIFY_FAILED -> R.string.settings_emulator_options_edit_failed_verify
        ConfigWriteFailure.NO_BACKUP -> R.string.settings_emulator_options_edit_failed_no_backup
        ConfigWriteFailure.CHANGED_SINCE -> R.string.settings_emulator_options_edit_failed_changed_since
        ConfigWriteFailure.GAME_RUNNING -> R.string.settings_emulator_options_edit_failed_game_running
    }

@get:StringRes
val EmulatorEditBlock.labelRes: Int
    get() = when (this) {
        EmulatorEditBlock.NO_ROOT -> R.string.settings_emulator_options_edit_needs_root
        EmulatorEditBlock.ROOT_PROBE_FAILED -> R.string.settings_emulator_options_edit_probe_failed
    }
