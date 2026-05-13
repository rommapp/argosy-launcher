package com.nendo.argosy.ui.screens.settings

import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.core.input.SoundType

internal enum class InputMethod {
    UP, DOWN, LEFT, RIGHT, CONFIRM, LONG_CONFIRM, BACK, CONTEXT_MENU, SECONDARY_ACTION,
    PREV_SECTION, NEXT_SECTION, PREV_TRIGGER, NEXT_TRIGGER,
    MENU, SELECT, LEFT_STICK_CLICK, RIGHT_STICK_CLICK
}

internal class ModalInputRouter(private val viewModel: SettingsViewModel) {

    fun intercept(state: SettingsUiState, method: InputMethod): InputResult? {
        // Pass-through modals: system AlertDialog handles its own input
        if (state.steam.showAddGameDialog) return null
        // Pass-through modals: builtin controls sub-InputHandlers handle their own input
        if (state.builtinControls.showControllerOrderModal ||
            state.builtinControls.showInputMappingModal ||
            state.builtinControls.showHotkeysModal
        ) return null

        interceptGpuDriverPrompt(state, method)?.let { return it }
        interceptAppPickerModal(state, method)?.let { return it }
        interceptLaunchArgsModal(state, method)?.let { return it }
        interceptSavePathModal(state, method)?.let { return it }
        interceptMemcardPicker(state, method)?.let { return it }
        interceptPlatformSettingsModal(state, method)?.let { return it }
        interceptSoundPicker(state, method)?.let { return it }
        interceptRegionPicker(state, method)?.let { return it }
        interceptPlatformFiltersModal(state, method)?.let { return it }
        interceptSyncFiltersModal(state, method)?.let { return it }
        interceptForceSyncConfirm(state, method)?.let { return it }
        interceptUpdateModal(state, method)?.let { return it }
        interceptVariantPicker(state, method)?.let { return it }
        interceptSteamVariantPicker(state, method)?.let { return it }
        interceptEmulatorPicker(state, method)?.let { return it }
        interceptShaderPicker(method)?.let { return it }

        return null
    }

    private fun interceptGpuDriverPrompt(state: SettingsUiState, method: InputMethod): InputResult? {
        if (!state.bios.showGpuDriverPrompt) return null
        val isInstalling = state.bios.gpuDriverInfo?.isInstalling == true
        return when (method) {
            InputMethod.UP -> if (!isInstalling) { viewModel.moveGpuDriverPromptFocus(-1); InputResult.HANDLED } else InputResult.HANDLED
            InputMethod.DOWN -> if (!isInstalling) { viewModel.moveGpuDriverPromptFocus(1); InputResult.HANDLED } else InputResult.HANDLED
            InputMethod.CONFIRM -> if (!isInstalling) {
                when (state.bios.gpuDriverPromptFocusIndex) {
                    0 -> viewModel.installGpuDriver()
                    1 -> viewModel.openGpuDriverFilePicker()
                    2 -> viewModel.dismissGpuDriverPrompt()
                }
                InputResult.HANDLED
            } else InputResult.HANDLED
            InputMethod.BACK -> if (!isInstalling) { viewModel.dismissGpuDriverPrompt(); InputResult.HANDLED } else InputResult.HANDLED
            else -> InputResult.HANDLED
        }
    }

    private fun interceptAppPickerModal(state: SettingsUiState, method: InputMethod): InputResult? {
        if (!state.emulators.showAppPickerModal) return null
        return when (method) {
            InputMethod.UP -> { viewModel.moveAppPickerFocus(-1); InputResult.HANDLED }
            InputMethod.DOWN -> { viewModel.moveAppPickerFocus(1); InputResult.HANDLED }
            InputMethod.PREV_SECTION -> { viewModel.moveAppPickerFocus(-5); InputResult.HANDLED }
            InputMethod.NEXT_SECTION -> { viewModel.moveAppPickerFocus(5); InputResult.HANDLED }
            InputMethod.CONFIRM -> { viewModel.confirmAppPickerSelection(); InputResult.HANDLED }
            InputMethod.BACK -> { viewModel.closeAppPickerModal(); InputResult.HANDLED }
            else -> InputResult.HANDLED
        }
    }

    private fun interceptLaunchArgsModal(state: SettingsUiState, method: InputMethod): InputResult? {
        if (!state.emulators.showLaunchArgsModal) return null
        val modal = state.emulators.launchArgsModalState ?: return null
        val rows = launchArgsModalRows(modal)
        val focusedRow = rows.getOrNull(modal.focusIndex)
        val isCycleable = focusedRow is LaunchArgsRow.DataBinding ||
            focusedRow is LaunchArgsRow.ExtraBinding ||
            focusedRow is LaunchArgsRow.ClipDataBinding ||
            focusedRow is LaunchArgsRow.MimeType

        return when (method) {
            InputMethod.UP -> { viewModel.moveLaunchArgsFocus(-1); InputResult.HANDLED }
            InputMethod.DOWN -> { viewModel.moveLaunchArgsFocus(1); InputResult.HANDLED }
            InputMethod.LEFT -> if (isCycleable) {
                cycleLaunchArgsRow(viewModel, focusedRow, -1); InputResult.HANDLED
            } else InputResult.HANDLED
            InputMethod.RIGHT -> if (isCycleable) {
                cycleLaunchArgsRow(viewModel, focusedRow, 1); InputResult.HANDLED
            } else InputResult.HANDLED
            InputMethod.CONFIRM -> {
                when (focusedRow) {
                    is LaunchArgsRow.DataBinding -> viewModel.cycleLaunchArgsDataBinding()
                    is LaunchArgsRow.ExtraBinding -> viewModel.cycleLaunchArgsExtraBinding()
                    is LaunchArgsRow.ClipDataBinding -> viewModel.cycleLaunchArgsClipDataBinding()
                    is LaunchArgsRow.Flag -> viewModel.toggleLaunchArgsFlag(focusedRow.bit)
                    is LaunchArgsRow.MimeType -> viewModel.cycleLaunchArgsMimeType()
                    is LaunchArgsRow.LockedBinding -> {}
                    null -> {}
                }
                InputResult.HANDLED
            }
            InputMethod.SECONDARY_ACTION -> { viewModel.resetLaunchArgsFocused(); InputResult.HANDLED }
            InputMethod.LONG_CONFIRM -> { viewModel.resetLaunchArgsFocused(); InputResult.HANDLED }
            InputMethod.CONTEXT_MENU -> { viewModel.resetAllLaunchArgs(); InputResult.HANDLED }
            InputMethod.BACK -> { viewModel.closeLaunchArgsModal(); InputResult.HANDLED }
            else -> InputResult.HANDLED
        }
    }

    private fun cycleLaunchArgsRow(vm: SettingsViewModel, row: LaunchArgsRow?, direction: Int) {
        when (row) {
            is LaunchArgsRow.DataBinding -> vm.cycleLaunchArgsDataBinding(direction)
            is LaunchArgsRow.ExtraBinding -> vm.cycleLaunchArgsExtraBinding(direction)
            is LaunchArgsRow.ClipDataBinding -> vm.cycleLaunchArgsClipDataBinding(direction)
            is LaunchArgsRow.MimeType -> vm.cycleLaunchArgsMimeType(direction)
            else -> {}
        }
    }

    private fun interceptSavePathModal(state: SettingsUiState, method: InputMethod): InputResult? {
        if (!state.emulators.showSavePathModal) return null
        return when (method) {
            InputMethod.UP -> { viewModel.moveSavePathModalFocus(-1); InputResult.HANDLED }
            InputMethod.DOWN -> { viewModel.moveSavePathModalFocus(1); InputResult.HANDLED }
            InputMethod.LEFT -> { viewModel.moveSavePathModalButtonFocus(1); InputResult.HANDLED }
            InputMethod.RIGHT -> { viewModel.moveSavePathModalButtonFocus(-1); InputResult.HANDLED }
            InputMethod.CONFIRM -> { viewModel.confirmSavePathModalSelection(); InputResult.HANDLED }
            InputMethod.BACK -> { viewModel.dismissSavePathModal(); InputResult.HANDLED }
            else -> InputResult.HANDLED
        }
    }

    private fun interceptMemcardPicker(state: SettingsUiState, method: InputMethod): InputResult? {
        if (!state.emulators.showMemcardPicker) return null
        return when (method) {
            InputMethod.UP -> { viewModel.moveMemcardPickerFocus(-1); InputResult.HANDLED }
            InputMethod.DOWN -> { viewModel.moveMemcardPickerFocus(1); InputResult.HANDLED }
            InputMethod.CONFIRM -> {
                val info = state.emulators.memcardPickerInfo
                val card = info?.cards?.getOrNull(state.emulators.memcardPickerFocusIndex)
                if (card != null) viewModel.confirmMemcardSelection(card.path)
                InputResult.HANDLED
            }
            InputMethod.BACK -> { viewModel.dismissMemcardPicker(); InputResult.HANDLED }
            else -> InputResult.HANDLED
        }
    }

    private fun interceptPlatformSettingsModal(state: SettingsUiState, method: InputMethod): InputResult? {
        if (state.storage.platformSettingsModalId == null) return null
        return when (method) {
            InputMethod.UP -> { viewModel.movePlatformSettingsFocus(-1); InputResult.HANDLED }
            InputMethod.DOWN -> { viewModel.movePlatformSettingsFocus(1); InputResult.HANDLED }
            InputMethod.LEFT -> {
                val focusIdx = state.storage.platformSettingsFocusIndex
                if (focusIdx in 1..3) viewModel.movePlatformSettingsButtonFocus(1)
                InputResult.HANDLED
            }
            InputMethod.RIGHT -> {
                val focusIdx = state.storage.platformSettingsFocusIndex
                if (focusIdx in 1..3) viewModel.movePlatformSettingsButtonFocus(-1)
                InputResult.HANDLED
            }
            InputMethod.CONFIRM -> { viewModel.selectPlatformSettingsOption(); InputResult.HANDLED }
            InputMethod.BACK -> { viewModel.closePlatformSettingsModal(); InputResult.HANDLED }
            else -> InputResult.HANDLED
        }
    }

    private fun interceptSoundPicker(state: SettingsUiState, method: InputMethod): InputResult? {
        if (!state.sounds.showSoundPicker) return null
        return when (method) {
            InputMethod.UP -> { viewModel.moveSoundPickerFocus(-1); InputResult.HANDLED }
            InputMethod.DOWN -> { viewModel.moveSoundPickerFocus(1); InputResult.HANDLED }
            InputMethod.CONFIRM -> { viewModel.confirmSoundPickerSelection(); InputResult.HANDLED }
            InputMethod.BACK -> { viewModel.dismissSoundPicker(); InputResult.HANDLED }
            InputMethod.CONTEXT_MENU -> { viewModel.previewSoundPickerSelection(); InputResult.HANDLED }
            else -> InputResult.HANDLED
        }
    }

    private fun interceptRegionPicker(state: SettingsUiState, method: InputMethod): InputResult? {
        if (!state.syncSettings.showRegionPicker) return null
        return when (method) {
            InputMethod.UP -> { viewModel.moveRegionPickerFocus(-1); InputResult.HANDLED }
            InputMethod.DOWN -> { viewModel.moveRegionPickerFocus(1); InputResult.HANDLED }
            InputMethod.CONFIRM -> { viewModel.confirmRegionPickerSelection(); InputResult.handled(SoundType.TOGGLE) }
            InputMethod.BACK -> { viewModel.dismissRegionPicker(); InputResult.HANDLED }
            else -> InputResult.HANDLED
        }
    }

    private fun interceptPlatformFiltersModal(state: SettingsUiState, method: InputMethod): InputResult? {
        if (!state.syncSettings.showPlatformFiltersModal) return null
        return when (method) {
            InputMethod.UP -> { viewModel.movePlatformFiltersModalFocus(-1); InputResult.HANDLED }
            InputMethod.DOWN -> { viewModel.movePlatformFiltersModalFocus(1); InputResult.HANDLED }
            InputMethod.CONFIRM -> { viewModel.confirmPlatformFiltersModalSelection(); InputResult.handled(SoundType.TOGGLE) }
            InputMethod.BACK -> { viewModel.dismissPlatformFiltersModal(); InputResult.HANDLED }
            else -> InputResult.HANDLED
        }
    }

    private fun interceptSyncFiltersModal(state: SettingsUiState, method: InputMethod): InputResult? {
        if (!state.syncSettings.showSyncFiltersModal) return null
        return when (method) {
            InputMethod.UP -> { viewModel.moveSyncFiltersModalFocus(-1); InputResult.HANDLED }
            InputMethod.DOWN -> { viewModel.moveSyncFiltersModalFocus(1); InputResult.HANDLED }
            InputMethod.CONFIRM -> { viewModel.confirmSyncFiltersModalSelection(); InputResult.handled(SoundType.TOGGLE) }
            InputMethod.BACK -> { viewModel.dismissSyncFiltersModal(); InputResult.HANDLED }
            else -> InputResult.HANDLED
        }
    }

    private fun interceptForceSyncConfirm(state: SettingsUiState, method: InputMethod): InputResult? {
        if (!state.syncSettings.showForceSyncConfirm) return null
        return when (method) {
            InputMethod.LEFT -> { viewModel.moveSyncConfirmFocus(-1); InputResult.HANDLED }
            InputMethod.RIGHT -> { viewModel.moveSyncConfirmFocus(1); InputResult.HANDLED }
            InputMethod.CONFIRM -> {
                if (state.syncSettings.syncConfirmButtonIndex == 0) {
                    viewModel.cancelSyncSaves()
                } else {
                    viewModel.confirmSyncSaves()
                }
                InputResult.HANDLED
            }
            InputMethod.BACK -> { viewModel.cancelSyncSaves(); InputResult.HANDLED }
            else -> InputResult.HANDLED
        }
    }

    private fun interceptUpdateModal(state: SettingsUiState, method: InputMethod): InputResult? {
        val modal = state.emulators.updateModal ?: return null
        return when (modal.state) {
            is UpdateModalState.SelectVariant -> when (method) {
                InputMethod.UP -> { viewModel.moveUpdateModalFocus(-1); InputResult.HANDLED }
                InputMethod.DOWN -> { viewModel.moveUpdateModalFocus(1); InputResult.HANDLED }
                InputMethod.CONFIRM -> { viewModel.selectUpdateModalVariant(); InputResult.HANDLED }
                InputMethod.BACK -> { viewModel.dismissUpdateModal(); InputResult.HANDLED }
                else -> InputResult.HANDLED
            }
            is UpdateModalState.Installed -> when (method) {
                InputMethod.CONFIRM, InputMethod.BACK -> { viewModel.dismissUpdateModal(); InputResult.HANDLED }
                else -> InputResult.HANDLED
            }
            is UpdateModalState.Failed -> when (method) {
                InputMethod.BACK, InputMethod.CONFIRM -> { viewModel.dismissUpdateModal(); InputResult.HANDLED }
                else -> InputResult.HANDLED
            }
            else -> InputResult.HANDLED
        }
    }

    private fun interceptVariantPicker(state: SettingsUiState, method: InputMethod): InputResult? {
        if (!state.emulators.showVariantPicker) return null
        return when (method) {
            InputMethod.UP -> { viewModel.moveVariantPickerFocus(-1); InputResult.HANDLED }
            InputMethod.DOWN -> { viewModel.moveVariantPickerFocus(1); InputResult.HANDLED }
            InputMethod.CONFIRM -> { viewModel.selectVariant(); InputResult.HANDLED }
            InputMethod.BACK -> { viewModel.dismissVariantPicker(); InputResult.HANDLED }
            else -> InputResult.HANDLED
        }
    }

    private fun interceptSteamVariantPicker(state: SettingsUiState, method: InputMethod): InputResult? {
        if (state.steam.variantPickerInfo == null) return null
        return when (method) {
            InputMethod.UP -> { viewModel.moveSteamVariantFocus(-1); InputResult.HANDLED }
            InputMethod.DOWN -> { viewModel.moveSteamVariantFocus(1); InputResult.HANDLED }
            InputMethod.CONFIRM -> { viewModel.confirmSteamVariantSelection(); InputResult.HANDLED }
            InputMethod.BACK -> { viewModel.dismissSteamVariantPicker(); InputResult.HANDLED }
            else -> InputResult.HANDLED
        }
    }

    private fun interceptEmulatorPicker(state: SettingsUiState, method: InputMethod): InputResult? {
        if (!state.emulators.showEmulatorPicker) return null
        return when (method) {
            InputMethod.UP -> { viewModel.moveEmulatorPickerFocus(-1); InputResult.HANDLED }
            InputMethod.DOWN -> { viewModel.moveEmulatorPickerFocus(1); InputResult.HANDLED }
            InputMethod.CONFIRM -> { viewModel.confirmEmulatorPickerSelection(); InputResult.HANDLED }
            InputMethod.BACK -> { viewModel.dismissEmulatorPicker(); InputResult.HANDLED }
            else -> InputResult.HANDLED
        }
    }

    private fun interceptShaderPicker(method: InputMethod): InputResult? {
        if (!viewModel.shaderChainManager.shaderStack.showShaderPicker) return null
        return when (method) {
            InputMethod.UP -> { viewModel.moveShaderPickerFocus(-1); InputResult.HANDLED }
            InputMethod.DOWN -> { viewModel.moveShaderPickerFocus(1); InputResult.HANDLED }
            InputMethod.LEFT, InputMethod.PREV_SECTION -> { viewModel.jumpShaderPickerSection(-1); InputResult.HANDLED }
            InputMethod.RIGHT, InputMethod.NEXT_SECTION -> { viewModel.jumpShaderPickerSection(1); InputResult.HANDLED }
            InputMethod.CONFIRM -> { viewModel.confirmShaderPickerSelection(); InputResult.HANDLED }
            InputMethod.BACK -> { viewModel.dismissShaderPicker(); InputResult.HANDLED }
            else -> InputResult.HANDLED
        }
    }
}
