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
import com.nendo.argosy.ui.screens.settings.sections.aboutHasChangelog
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
import com.nendo.argosy.ui.screens.settings.sections.SteamItem
import com.nendo.argosy.ui.screens.settings.sections.steamItemAtFocusIndex
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
        if (state.currentSection == SettingsSection.STEAM_SETTINGS) {
            val item = steamItemAtFocusIndex(state.focusedIndex, state.steam)
            if (item == SteamItem.SyncLibrary) {
                viewModel.forceSyncSteamLibrary()
                return InputResult.HANDLED
            }
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
            SettingsSection.BUILTIN_EMULATOR -> handleBuiltinEmulatorLeftRight(direction)
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
            if (!state.server.rommDevicePairing && state.focusedIndex == 1) {
                val methods = com.nendo.argosy.ui.screens.settings.RomMAuthMethod.entries
                val next = methods[(methods.indexOf(state.server.rommAuthMethod) + direction).mod(methods.size)]
                viewModel.setRommAuthMethod(next)
                return InputResult.HANDLED
            }
            return InputResult.UNHANDLED
        }
        val items = buildGameDataItemsFromState(state)
        when (gameDataItemAtFocusIndex(state.focusedIndex, items)) {
            is GameDataItem.InstalledLauncher -> {
                viewModel.moveLauncherActionFocus(direction)
                return InputResult.HANDLED
            }
            GameDataItem.SaveCacheLimit -> {
                viewModel.cycleSaveCacheLimit(direction)
                return InputResult.HANDLED
            }
            else -> {}
        }
        return InputResult.UNHANDLED
    }

    private fun handleHomeScreenLeftRight(direction: Int): InputResult {
        val state = viewModel.uiState.value
        val display = state.display
        val step = SettingsInputHandler.SLIDER_STEP
        when (homeScreenItemAtFocusIndex(state.focusedIndex, display)) {
            HomeScreenItem.Background -> { viewModel.cycleHomeBackgroundMode(direction); return InputResult.HANDLED }
            HomeScreenItem.Blur -> { viewModel.adjustBackgroundBlur(direction * step); return InputResult.HANDLED }
            HomeScreenItem.Saturation -> { viewModel.adjustBackgroundSaturation(direction * step); return InputResult.HANDLED }
            HomeScreenItem.Opacity -> { viewModel.adjustBackgroundOpacity(direction * step); return InputResult.HANDLED }
            HomeScreenItem.GameArtwork ->
                return toggleLeftRight(direction, display.useGameBackground) { viewModel.setUseGameBackground(it) }
            HomeScreenItem.VideoWallpaper ->
                return toggleLeftRight(direction, display.videoWallpaperEnabled) { viewModel.setVideoWallpaperEnabled(it) }
            HomeScreenItem.VideoDelay -> { viewModel.cycleVideoWallpaperDelay(direction); return InputResult.HANDLED }
            HomeScreenItem.VideoMuted ->
                return toggleLeftRight(direction, display.videoWallpaperMuted) { viewModel.setVideoWallpaperMuted(it) }
            HomeScreenItem.AccentFooter ->
                return toggleLeftRight(direction, display.useAccentColorFooter) { viewModel.setUseAccentColorFooter(it) }
            HomeScreenItem.InstalledOnly ->
                return toggleLeftRight(direction, display.installedOnlyHome) { viewModel.setInstalledOnlyHome(it) }
            else -> {}
        }
        return InputResult.UNHANDLED
    }

    private fun handleControlsLeftRight(direction: Int): InputResult {
        val state = viewModel.uiState.value
        val controls = state.controls
        when (controlsItemAtFocusIndex(state.focusedIndex, controls)) {
            ControlsItem.VibrationStrength -> if (controls.hapticEnabled && controls.vibrationSupported) {
                viewModel.adjustVibrationStrength(direction * 0.1f)
                return InputResult.HANDLED
            }
            ControlsItem.HapticFeedback ->
                return toggleLeftRight(direction, controls.hapticEnabled) { viewModel.setHapticEnabled(it) }
            ControlsItem.ControllerLayout -> { viewModel.cycleControllerLayout(direction); return InputResult.HANDLED }
            ControlsItem.SwapAB ->
                return toggleLeftRight(direction, controls.swapAB) { viewModel.setSwapAB(it) }
            ControlsItem.SwapXY ->
                return toggleLeftRight(direction, controls.swapXY) { viewModel.setSwapXY(it) }
            ControlsItem.SwapStartSelect ->
                return toggleLeftRight(direction, controls.swapStartSelect) { viewModel.setSwapStartSelect(it) }
            ControlsItem.SelectLCombo -> { viewModel.cycleSelectLCombo(direction); return InputResult.HANDLED }
            ControlsItem.SelectRCombo -> { viewModel.cycleSelectRCombo(direction); return InputResult.HANDLED }
            ControlsItem.MenuWrap -> { viewModel.cycleMenuWrapMode(direction); return InputResult.HANDLED }
            else -> {}
        }
        return InputResult.UNHANDLED
    }

    private fun handleSyncSettingsLeftRight(direction: Int): InputResult {
        val state = viewModel.uiState.value
        when (syncSettingsItemAtFocusIndex(state.focusedIndex)) {
            is SyncSettingsItem.ImageCacheLocation -> {
                viewModel.moveImageCacheActionFocus(direction)
                return InputResult.HANDLED
            }
            SyncSettingsItem.CacheScreenshots ->
                return toggleLeftRight(direction, state.server.syncScreenshotsEnabled) { viewModel.toggleSyncScreenshots() }
            SyncSettingsItem.CacheBoxArt ->
                return toggleLeftRight(direction, state.server.boxArtCacheEnabled) { viewModel.toggleBoxArtCache() }
            SyncSettingsItem.UploadScreenshots -> {
                if (!state.server.screenshotUploadSupported) return InputResult.UNHANDLED
                return toggleLeftRight(direction, state.server.uploadScreenshotsEnabled) { viewModel.toggleUploadScreenshots() }
            }
            else -> {}
        }
        return InputResult.UNHANDLED
    }

    private fun handleAboutLeftRight(direction: Int): InputResult {
        val state = viewModel.uiState.value
        val hasLogPath = state.fileLoggingPath != null
        val hasChangelog = aboutHasChangelog(state.updateCheck)
        when (aboutItemAtFocusIndex(state.focusedIndex, hasLogPath, hasChangelog)) {
            AboutItem.CheckUpdates -> { viewModel.moveUpdateActionFocus(direction); return InputResult.HANDLED }
            AboutItem.LogLevel -> { viewModel.cycleFileLogLevel(direction); return InputResult.HANDLED }
            AboutItem.BetaUpdates ->
                return toggleLeftRight(direction, state.betaUpdatesEnabled) { viewModel.setBetaUpdatesEnabled(it) }
            AboutItem.FileLogging -> if (hasLogPath) {
                return toggleLeftRight(direction, state.fileLoggingEnabled) { viewModel.toggleFileLogging(it) }
            }
            AboutItem.SaveDebugLogging ->
                return toggleLeftRight(direction, state.saveDebugLoggingEnabled) { viewModel.setSaveDebugLoggingEnabled(it) }
            AboutItem.AppAffinity ->
                return toggleLeftRight(direction, state.appAffinityEnabled) { viewModel.setAppAffinityEnabled(it) }
            else -> {}
        }
        return InputResult.UNHANDLED
    }

    private fun handleSteamLeftRight(@Suppress("UNUSED_PARAMETER") direction: Int): InputResult {
        return InputResult.UNHANDLED
    }

    private fun handleBuiltinEmulatorLeftRight(direction: Int): InputResult {
        val state = viewModel.uiState.value
        if (state.emulators.builtinLibretroEnabled && state.focusedIndex == 1) {
            viewModel.cycleBuiltinArchitecture(direction)
            return InputResult.HANDLED
        }
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
            SettingsSection.ABOUT -> aboutSections(state.fileLoggingPath != null, aboutHasChangelog(state.updateCheck))
            else -> return InputResult.HANDLED
        }
        val jumped = if (direction < 0) viewModel.jumpToPrevSection(sections)
            else viewModel.jumpToNextSection(sections)
        return if (jumped) InputResult.HANDLED else InputResult.HANDLED
    }

}
