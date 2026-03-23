package com.nendo.argosy.ui.screens.settings.sections.input

import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.screens.settings.SettingsInputHandler
import com.nendo.argosy.ui.screens.settings.SettingsSection
import com.nendo.argosy.ui.screens.settings.SettingsViewModel
import com.nendo.argosy.ui.screens.settings.sections.AboutItem
import com.nendo.argosy.ui.screens.settings.sections.BiosItem
import com.nendo.argosy.ui.screens.settings.sections.ControlsItem
import com.nendo.argosy.ui.screens.settings.sections.GameDataItem
import com.nendo.argosy.ui.screens.settings.sections.HomeScreenItem
import com.nendo.argosy.ui.screens.settings.sections.SyncSettingsItem
import com.nendo.argosy.ui.screens.settings.sections.aboutItemAtFocusIndex
import com.nendo.argosy.ui.screens.settings.sections.aboutSections
import com.nendo.argosy.ui.screens.settings.sections.biosItemAtFocusIndex
import com.nendo.argosy.ui.screens.settings.sections.biosSections
import com.nendo.argosy.ui.screens.settings.sections.buildGameDataItemsFromState
import com.nendo.argosy.ui.screens.settings.sections.controlsItemAtFocusIndex
import com.nendo.argosy.ui.screens.settings.sections.gameDataItemAtFocusIndex
import com.nendo.argosy.ui.screens.settings.sections.gameDataSections
import com.nendo.argosy.ui.screens.settings.sections.homeScreenItemAtFocusIndex
import com.nendo.argosy.ui.screens.settings.sections.homeScreenSections
import com.nendo.argosy.ui.screens.settings.sections.socialSections
import com.nendo.argosy.ui.screens.settings.sections.syncSettingsItemAtFocusIndex

internal class LightSectionsInput(
    private val viewModel: SettingsViewModel
) : InputHandler {

    override fun onLeft(): InputResult = handleLeftRight(-1)

    override fun onRight(): InputResult = handleLeftRight(1)

    override fun onContextMenu(): InputResult {
        val state = viewModel.uiState.value
        if (state.currentSection == SettingsSection.SYNC_SETTINGS) {
            viewModel.showSyncFiltersModal()
            return InputResult.HANDLED
        }
        return InputResult.UNHANDLED
    }

    override fun onPrevSection(): InputResult = handleSectionJump(-1)

    override fun onNextSection(): InputResult = handleSectionJump(1)

    private fun handleLeftRight(direction: Int): InputResult {
        val state = viewModel.uiState.value
        return when (state.currentSection) {
            SettingsSection.BIOS -> handleBiosLeftRight(direction)
            SettingsSection.SERVER -> handleServerLeftRight(direction)
            SettingsSection.HOME_SCREEN -> handleHomeScreenLeftRight(direction)
            SettingsSection.CONTROLS -> handleControlsLeftRight(direction)
            SettingsSection.SYNC_SETTINGS -> handleSyncSettingsLeftRight(direction)
            SettingsSection.ABOUT -> handleAboutLeftRight(direction)
            SettingsSection.STEAM_SETTINGS -> handleSteamLeftRight(direction)
            SettingsSection.CORE_MANAGEMENT -> handleCoreManagementLeftRight(direction)
            else -> InputResult.UNHANDLED
        }
    }

    private fun handleBiosLeftRight(direction: Int): InputResult {
        val state = viewModel.uiState.value
        val bios = state.bios
        when (biosItemAtFocusIndex(state.focusedIndex, bios.platformGroups, bios.expandedPlatformIndex)) {
            BiosItem.Summary -> {
                viewModel.moveBiosActionFocus(direction)
                return InputResult.HANDLED
            }
            BiosItem.BiosPath -> {
                if (viewModel.moveBiosPathActionFocus(direction)) {
                    return InputResult.HANDLED
                }
            }
            is BiosItem.Platform -> {
                if (viewModel.moveBiosPlatformSubFocus(direction)) {
                    return InputResult.HANDLED
                }
            }
            else -> {}
        }
        return InputResult.UNHANDLED
    }

    private fun handleServerLeftRight(direction: Int): InputResult {
        val state = viewModel.uiState.value
        if (state.server.rommConfiguring) {
            val isPairingCode = state.server.rommAuthMethod == com.nendo.argosy.ui.screens.settings.RomMAuthMethod.PAIRING_CODE
            if (!isPairingCode && (state.focusedIndex == 2 || state.focusedIndex == 3)) {
                val targetIndex = if (direction < 0) 2 else 3
                if (state.focusedIndex != targetIndex) {
                    viewModel.setRommConfigFocusIndex(targetIndex)
                    return InputResult.HANDLED
                }
            }
            return InputResult.UNHANDLED
        }
        val items = buildGameDataItemsFromState(state)
        val item = gameDataItemAtFocusIndex(state.focusedIndex, items)
        if (item is GameDataItem.InstalledLauncher) {
            viewModel.moveLauncherActionFocus(direction)
            return InputResult.HANDLED
        }
        return InputResult.UNHANDLED
    }

    private fun handleHomeScreenLeftRight(direction: Int): InputResult {
        val state = viewModel.uiState.value
        val step = SettingsInputHandler.SLIDER_STEP
        when (homeScreenItemAtFocusIndex(state.focusedIndex, state.display)) {
            HomeScreenItem.Blur -> { viewModel.adjustBackgroundBlur(direction * step); return InputResult.HANDLED }
            HomeScreenItem.Saturation -> { viewModel.adjustBackgroundSaturation(direction * step); return InputResult.HANDLED }
            HomeScreenItem.Opacity -> { viewModel.adjustBackgroundOpacity(direction * step); return InputResult.HANDLED }
            else -> {}
        }
        return InputResult.UNHANDLED
    }

    private fun handleControlsLeftRight(direction: Int): InputResult {
        val state = viewModel.uiState.value
        when (controlsItemAtFocusIndex(state.focusedIndex, state.controls)) {
            ControlsItem.VibrationStrength -> if (state.controls.hapticEnabled && state.controls.vibrationSupported) {
                viewModel.adjustVibrationStrength(direction * 0.1f)
                return InputResult.HANDLED
            }
            else -> {}
        }
        return InputResult.UNHANDLED
    }

    private fun handleSyncSettingsLeftRight(direction: Int): InputResult {
        val state = viewModel.uiState.value
        if (syncSettingsItemAtFocusIndex(state.focusedIndex) is SyncSettingsItem.ImageCacheLocation) {
            viewModel.moveImageCacheActionFocus(direction)
            return InputResult.HANDLED
        }
        return InputResult.UNHANDLED
    }

    private fun handleAboutLeftRight(direction: Int): InputResult {
        val state = viewModel.uiState.value
        val hasLogPath = state.fileLoggingPath != null
        when (aboutItemAtFocusIndex(state.focusedIndex, hasLogPath)) {
            AboutItem.LogLevel -> { viewModel.cycleFileLogLevel(direction); return InputResult.HANDLED }
            else -> {}
        }
        return InputResult.UNHANDLED
    }

    private fun handleSteamLeftRight(direction: Int): InputResult {
        return InputResult.UNHANDLED
    }

    private fun handleCoreManagementLeftRight(direction: Int): InputResult {
        viewModel.moveCoreManagementCoreFocus(direction)
        return InputResult.HANDLED
    }

    private fun handleSectionJump(direction: Int): InputResult {
        val state = viewModel.uiState.value
        val sections = when (state.currentSection) {
            SettingsSection.HOME_SCREEN -> homeScreenSections(state.display)
            SettingsSection.BIOS -> biosSections(state.bios.platformGroups, state.bios.expandedPlatformIndex)
            SettingsSection.SERVER -> gameDataSections(buildGameDataItemsFromState(state))
            SettingsSection.SOCIAL -> socialSections()
            SettingsSection.ABOUT -> aboutSections(state.fileLoggingPath != null)
            else -> return InputResult.HANDLED
        }
        val jumped = if (direction < 0) viewModel.jumpToPrevSection(sections)
            else viewModel.jumpToNextSection(sections)
        return if (jumped) InputResult.HANDLED else InputResult.HANDLED
    }

}
