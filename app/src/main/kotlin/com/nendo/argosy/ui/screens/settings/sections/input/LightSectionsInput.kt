package com.nendo.argosy.ui.screens.settings.sections.input

import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.data.preferences.ControlsPreferences
import com.nendo.argosy.data.preferences.GridDensity
import com.nendo.argosy.data.preferences.GripReserveMode
import com.nendo.argosy.data.preferences.HomeBackgroundMode
import com.nendo.argosy.data.preferences.NewBadgePlacement
import com.nendo.argosy.data.preferences.MediaStreamingQuality
import com.nendo.argosy.data.preferences.MenuWrapMode
import com.nendo.argosy.data.preferences.SelectSwapMode
import com.nendo.argosy.ui.screens.settings.delegates.ControlsSettingsDelegate
import com.nendo.argosy.util.LogLevel
import com.nendo.argosy.data.preferences.LibraryLayout
import com.nendo.argosy.data.preferences.LibraryListStyle
import com.nendo.argosy.domain.model.HomeLayoutKind
import com.nendo.argosy.ui.common.hudCornerFromStored
import com.nendo.argosy.ui.components.HudCorner
import com.nendo.argosy.ui.components.isHomeLayoutFieldAtBound
import com.nendo.argosy.ui.screens.settings.ARCHITECTURE_OPTIONS
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.input.stepOption
import com.nendo.argosy.ui.screens.settings.sections.librarySourceKeys
import com.nendo.argosy.ui.screens.settings.SettingsInputHandler
import com.nendo.argosy.ui.screens.settings.SettingsSection
import com.nendo.argosy.ui.screens.settings.SettingsViewModel
import com.nendo.argosy.ui.screens.settings.components.rommConfigIndices
import com.nendo.argosy.ui.screens.settings.sections.AboutItem
import com.nendo.argosy.ui.screens.settings.sections.PresentationItem
import com.nendo.argosy.ui.screens.settings.sections.adjustPresentationItem
import com.nendo.argosy.ui.screens.settings.sections.presentationItemAtFocusIndex
import com.nendo.argosy.ui.screens.settings.sections.presentationSections
import com.nendo.argosy.ui.screens.settings.sections.BiosItem
import com.nendo.argosy.ui.screens.settings.sections.BuiltinEmulatorItem
import com.nendo.argosy.ui.screens.settings.sections.NavigationItem
import com.nendo.argosy.ui.screens.settings.sections.ControllerGripItem
import com.nendo.argosy.ui.screens.settings.sections.GRIP_RESERVE_PERCENT_STEP
import com.nendo.argosy.ui.screens.settings.sections.controllerGripItemAtFocusIndex
import com.nendo.argosy.ui.screens.settings.sections.controllerGripSections
import com.nendo.argosy.ui.screens.settings.sections.HomeScreenItem
import com.nendo.argosy.ui.screens.settings.sections.SavesItem
import com.nendo.argosy.ui.screens.settings.sections.SavesLayoutState
import com.nendo.argosy.ui.screens.settings.sections.SyncSettingsItem
import com.nendo.argosy.ui.screens.settings.sections.aboutHasChangelog
import com.nendo.argosy.ui.screens.settings.sections.aboutItemAtFocusIndex
import com.nendo.argosy.ui.screens.settings.sections.aboutSections
import com.nendo.argosy.ui.screens.settings.sections.biosItemAtFocusIndex
import com.nendo.argosy.ui.screens.settings.sections.biosSections
import com.nendo.argosy.ui.screens.settings.sections.buildRomMItemsFromState
import com.nendo.argosy.ui.screens.settings.sections.JellyfinItem
import com.nendo.argosy.ui.screens.settings.sections.JellyfinLayoutState
import com.nendo.argosy.ui.screens.settings.sections.jellyfinItemAtFocusIndex
import com.nendo.argosy.ui.screens.settings.sections.jellyfinSections
import com.nendo.argosy.ui.screens.settings.sections.mainSettingsSections
import com.nendo.argosy.ui.screens.settings.sections.audioSections
import com.nendo.argosy.ui.screens.settings.sections.navigationItemAtFocusIndex
import com.nendo.argosy.ui.screens.settings.sections.navigationSections
import com.nendo.argosy.ui.screens.settings.sections.rommSections
import com.nendo.argosy.ui.screens.settings.sections.savesItemAtFocusIndex
import com.nendo.argosy.ui.screens.settings.sections.savesSections
import com.nendo.argosy.ui.screens.settings.sections.homeScreenItemAtFocusIndex
import com.nendo.argosy.ui.screens.settings.sections.homeScreenSections
import com.nendo.argosy.ui.screens.settings.sections.socialSections
import com.nendo.argosy.ui.screens.settings.sections.SteamItem
import com.nendo.argosy.ui.screens.settings.sections.steamItemAtFocusIndex
import com.nendo.argosy.ui.screens.settings.sections.steamSections
import com.nendo.argosy.ui.screens.settings.sections.syncSettingsItemAtFocusIndex
import com.nendo.argosy.ui.screens.settings.sections.librarySections
import com.nendo.argosy.ui.screens.settings.sections.libraryPlatformTokens
import com.nendo.argosy.ui.screens.settings.sections.libraryItemAtFocusIndex
import com.nendo.argosy.ui.screens.settings.sections.LibraryLayoutState
import com.nendo.argosy.ui.screens.settings.sections.LibraryItem

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

    override fun onSecondaryAction(): InputResult {
        val state = viewModel.uiState.value
        if (state.currentSection != SettingsSection.NAVIGATION) return InputResult.UNHANDLED
        val item = navigationItemAtFocusIndex(state.focusedIndex, state.controls)
        item?.systemHotkeyAction?.let { action ->
            if (!state.controls.isSystemComboCustom(action)) return InputResult.UNHANDLED
            viewModel.resetSystemCombo(action)
            return InputResult.HANDLED
        }
        val shortcut = item?.shortcut ?: return InputResult.UNHANDLED
        viewModel.clearShortcutKey(shortcut)
        return InputResult.HANDLED
    }

    override fun onPrevSection(): InputResult = handleSectionJump(-1)

    override fun onNextSection(): InputResult = handleSectionJump(1)

    override fun onPrevTrigger(): InputResult = mainGroupTrigger(-1)

    override fun onNextTrigger(): InputResult = mainGroupTrigger(1)

    private fun mainGroupTrigger(direction: Int): InputResult =
        if (viewModel.uiState.value.controls.quickNavigation) handleMainGroupJump(direction) else InputResult.UNHANDLED

    private fun handleMainGroupJump(direction: Int): InputResult {
        val state = viewModel.uiState.value
        if (state.currentSection != SettingsSection.MAIN) return InputResult.UNHANDLED
        val sections = mainSettingsSections(state.controls)
        if (direction < 0) viewModel.jumpToPrevSection(sections) else viewModel.jumpToNextSection(sections)
        return InputResult.HANDLED
    }

    private fun handleLeftRight(direction: Int): InputResult {
        val state = viewModel.uiState.value
        return when (state.currentSection) {
            SettingsSection.ACCOUNTS -> handleAccountsLeftRight(direction)
            SettingsSection.BIOS -> handleBiosLeftRight(direction)
            SettingsSection.ROMM -> handleRomMLeftRight(direction)
            SettingsSection.SAVES -> handleSavesLeftRight(direction)
            SettingsSection.CONTROLLER_GRIP -> handleControllerGripLeftRight(direction)
            SettingsSection.HOME_SCREEN -> handleHomeScreenLeftRight(direction)
            SettingsSection.PRESENTATION -> handlePresentationLeftRight(direction)
            SettingsSection.LIBRARY_VIEW -> handleLibraryViewLeftRight(direction)
            SettingsSection.NAVIGATION -> handleNavigationLeftRight(direction)
            SettingsSection.SYNC_SETTINGS -> handleSyncSettingsLeftRight(direction)
            SettingsSection.STEAM_SETTINGS -> handleSteamLeftRight(direction)
            SettingsSection.JELLYFIN -> handleJellyfinLeftRight(direction)
            SettingsSection.ABOUT -> handleAboutLeftRight(direction)
            SettingsSection.BUILTIN_EMULATOR -> handleBuiltinEmulatorLeftRight(direction)
            SettingsSection.CORE_MANAGEMENT -> handleCoreManagementLeftRight(direction)
            else -> InputResult.UNHANDLED
        }
    }

    private fun handleAccountsLeftRight(direction: Int): InputResult {
        val state = viewModel.uiState.value
        if (state.accounts.pairing.active || state.accounts.switchInProgress) {
            return InputResult.HANDLED
        }
        return if (viewModel.moveAccountRowAction(direction)) {
            InputResult.HANDLED
        } else {
            InputResult.UNHANDLED
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

    private fun handleRomMLeftRight(direction: Int): InputResult {
        val state = viewModel.uiState.value
        if (state.server.rommConfiguring && !state.server.rommDevicePairing) {
            val indices = rommConfigIndices(state.server)
            if (state.focusedIndex == indices.authMethodIndex) {
                val methods = com.nendo.argosy.ui.screens.settings.RomMAuthMethod.entries
                val next = methods[(methods.indexOf(state.server.rommAuthMethod) + direction).mod(methods.size)]
                viewModel.setRommAuthMethod(next)
                return InputResult.HANDLED
            }
        }
        return InputResult.UNHANDLED
    }

    private fun handleSavesLeftRight(direction: Int): InputResult {
        val state = viewModel.uiState.value
        if (savesItemAtFocusIndex(state.focusedIndex, SavesLayoutState.from(state)) ==
            SavesItem.SaveCacheLimit
        ) {
            viewModel.cycleSaveCacheLimit(direction)
            return InputResult.HANDLED
        }
        return InputResult.UNHANDLED
    }

    private fun handleControllerGripLeftRight(direction: Int): InputResult {
        val state = viewModel.uiState.value
        return when (controllerGripItemAtFocusIndex(state.focusedIndex, state.display)) {
            ControllerGripItem.Mode -> stepOption(
                GripReserveMode.entries,
                state.display.gripReserveMode,
                direction,
                viewModel::setGripReserveMode
            )
            ControllerGripItem.ReservedHeight -> {
                viewModel.adjustGripReservePercent(direction * GRIP_RESERVE_PERCENT_STEP)
                InputResult.HANDLED
            }
            else -> InputResult.UNHANDLED
        }
    }

    private fun handlePresentationLeftRight(direction: Int): InputResult {
        val style = viewModel.uiState.value.display.presentationStyle
        val item = presentationItemAtFocusIndex(viewModel.uiState.value.focusedIndex, style)
            ?: return InputResult.UNHANDLED
        if (item == PresentationItem.BackgroundBlur) {
            viewModel.adjustPresentationBackgroundBlur(direction * SettingsInputHandler.SLIDER_STEP)
            return InputResult.HANDLED
        }
        val adjusted = adjustPresentationItem(style, item, direction) ?: return InputResult.UNHANDLED
        if (adjusted == style) {
            return InputResult.handled(if (item is PresentationItem.Stat) SoundType.SILENT else SoundType.BOUNDARY)
        }
        viewModel.setPresentationStyle(adjusted)
        return InputResult.HANDLED
    }

    private fun handleHomeScreenLeftRight(direction: Int): InputResult {
        val state = viewModel.uiState.value
        val display = state.display
        val step = SettingsInputHandler.SLIDER_STEP
        when (val focused = homeScreenItemAtFocusIndex(state.focusedIndex, display)) {
            HomeScreenItem.Background -> return stepOption(
                HomeBackgroundMode.entries,
                display.homeBackgroundMode,
                direction,
                viewModel::setHomeBackgroundMode
            )
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
            HomeScreenItem.InstalledOnly ->
                return toggleLeftRight(direction, display.installedOnlyHome) { viewModel.setInstalledOnlyHome(it) }
            HomeScreenItem.RecentNewGames -> { viewModel.cycleRecentNewGamesLimit(direction); return InputResult.HANDLED }
            HomeScreenItem.NewBadge ->
                return stepOption(NewBadgePlacement.entries, display.newBadgePlacement, direction) {
                    viewModel.setNewBadgePlacement(it)
                }
            HomeScreenItem.BoxArt ->
                return stepOption(listOf(false, true), display.homeLayout.boxArt3d, direction, viewModel::setHomeBoxArt3d)
            HomeScreenItem.LayoutSelector -> return stepOption(
                HomeLayoutKind.entries,
                display.homeLayout.selected,
                direction
            ) { viewModel.setHomeLayout(display.homeLayout.copy(selected = it)) }
            is HomeScreenItem.LayoutField -> {
                if (isHomeLayoutFieldAtBound(display.homeLayout, focused.field, direction)) {
                    return InputResult.handled(SoundType.BOUNDARY)
                }
                viewModel.adjustHomeLayoutField(focused.field, direction)
                return InputResult.HANDLED
            }
            else -> {}
        }
        return InputResult.UNHANDLED
    }

    private fun handleNavigationLeftRight(direction: Int): InputResult {
        val state = viewModel.uiState.value
        val controls = state.controls
        when (navigationItemAtFocusIndex(state.focusedIndex, controls)) {
            NavigationItem.VibrationStrength -> if (controls.hapticEnabled) {
                viewModel.adjustVibrationStrength(direction * ControlsPreferences.HAPTIC_STRENGTH_STEP)
                return InputResult.HANDLED
            }
            NavigationItem.HapticFeedback ->
                return toggleLeftRight(direction, controls.hapticEnabled) { viewModel.setHapticEnabled(it) }
            NavigationItem.ControllerLayout -> return stepOption(
                ControlsSettingsDelegate.LAYOUT_OPTIONS,
                controls.controllerLayout,
                direction,
                viewModel::setControllerLayout
            )
            NavigationItem.SwapAB ->
                return toggleLeftRight(direction, controls.swapAB) { viewModel.setSwapAB(it) }
            NavigationItem.SwapXY ->
                return toggleLeftRight(direction, controls.swapXY) { viewModel.setSwapXY(it) }
            NavigationItem.SwapStartSelect ->
                return toggleLeftRight(direction, controls.swapStartSelect) { viewModel.setSwapStartSelect(it) }
            NavigationItem.IgnoreControllerTouchpad ->
                return toggleLeftRight(direction, controls.ignoreControllerTouchpad) {
                    viewModel.setIgnoreControllerTouchpad(it)
                }
            NavigationItem.QuickNavigation ->
                return toggleLeftRight(direction, controls.quickNavigation) { viewModel.setQuickNavigation(it) }
            NavigationItem.NavBarAutoHide ->
                return toggleLeftRight(direction, controls.navBarAutoHide) { viewModel.setNavBarAutoHide(it) }
            NavigationItem.SystemHotkeys ->
                return toggleLeftRight(direction, controls.systemHotkeysEnabled) {
                    viewModel.setSystemHotkeysEnabled(it)
                }
            NavigationItem.SelectLCombo -> return stepOption(
                ControlsSettingsDelegate.COMBO_OPTIONS,
                controls.selectLCombo,
                direction,
                viewModel::setSelectLCombo
            )
            NavigationItem.SelectRCombo -> return stepOption(
                ControlsSettingsDelegate.COMBO_OPTIONS,
                controls.selectRCombo,
                direction,
                viewModel::setSelectRCombo
            )
            NavigationItem.MenuWrap ->
                return stepOption(MenuWrapMode.entries, controls.menuWrapMode, direction, viewModel::setMenuWrapMode)
            NavigationItem.SelectSwap ->
                return stepOption(SelectSwapMode.entries, controls.selectSwapMode, direction, viewModel::setSelectSwapMode)
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

    private fun handleSteamLeftRight(direction: Int): InputResult {
        val state = viewModel.uiState.value
        if (steamItemAtFocusIndex(state.focusedIndex, state.steam) == SteamItem.GameNativeLibrary &&
            viewModel.moveGameNativeActionFocus(direction)
        ) {
            return InputResult.HANDLED
        }
        return InputResult.UNHANDLED
    }

    private fun handleJellyfinLeftRight(direction: Int): InputResult {
        val state = viewModel.uiState.value
        if (state.jellyfin.configuring || state.jellyfin.showLoginForm) return InputResult.UNHANDLED
        val layoutState = JellyfinLayoutState.from(state)
        when (jellyfinItemAtFocusIndex(state.focusedIndex, layoutState)) {
            JellyfinItem.StreamingQuality -> return stepOption(
                MediaStreamingQuality.entries,
                state.jellyfin.streamingQuality,
                direction,
                viewModel::setJellyfinStreamingQuality
            )
            JellyfinItem.AudioLanguage -> viewModel.cycleJellyfinAudioLanguage(direction)
            JellyfinItem.Subtitles -> viewModel.cycleJellyfinSubtitleMode(direction)
            JellyfinItem.SubtitleLanguage -> viewModel.cycleJellyfinSubtitleLanguage(direction)
            JellyfinItem.DownloadQuality -> viewModel.cycleJellyfinDownloadQuality(direction)
            JellyfinItem.BurnInSubtitles -> return toggleLeftRight(
                direction,
                state.jellyfin.burnInImageSubtitles
            ) { viewModel.setJellyfinBurnInImageSubtitles(it) }
            JellyfinItem.ConfirmPlayerExit -> return toggleLeftRight(
                direction,
                state.jellyfin.confirmPlayerExit
            ) { viewModel.setJellyfinConfirmPlayerExit(it) }
            JellyfinItem.SharePresence -> return toggleLeftRight(
                direction,
                state.jellyfin.sharePresence
            ) { viewModel.setJellyfinSharePresence(it) }
            else -> return InputResult.UNHANDLED
        }
        return InputResult.HANDLED
    }

    private fun handleAboutLeftRight(direction: Int): InputResult {
        val state = viewModel.uiState.value
        val hasLogPath = state.fileLoggingPath != null
        val hasChangelog = aboutHasChangelog(state.updateCheck)
        when (aboutItemAtFocusIndex(state.focusedIndex, hasLogPath, hasChangelog)) {
            AboutItem.CheckUpdates -> { viewModel.moveUpdateActionFocus(direction); return InputResult.HANDLED }
            AboutItem.LogLevel ->
                return stepOption(LogLevel.entries, state.fileLogLevel, direction, viewModel::setFileLogLevel)
            AboutItem.BetaUpdates ->
                return toggleLeftRight(direction, state.betaUpdatesEnabled) { viewModel.setBetaUpdatesEnabled(it) }
            AboutItem.FileLogging -> if (hasLogPath) {
                return toggleLeftRight(direction, state.fileLoggingEnabled) { viewModel.toggleFileLogging(it) }
            }
            AboutItem.SaveDebugLogging ->
                return toggleLeftRight(direction, state.saveDebugLoggingEnabled) { viewModel.setSaveDebugLoggingEnabled(it) }
            else -> {}
        }
        return InputResult.UNHANDLED
    }

    private fun handleLibraryViewLeftRight(direction: Int): InputResult {
        val state = viewModel.uiState.value
        val layoutState = LibraryLayoutState.from(state)
        val display = state.display
        when (libraryItemAtFocusIndex(state.focusedIndex, layoutState)) {
            LibraryItem.LayoutItem ->
                return stepOption(LibraryLayout.entries, display.libraryLayout, direction, viewModel::setLibraryLayout)
            LibraryItem.ListStyleItem ->
                return stepOption(LibraryListStyle.entries, display.libraryListStyle, direction, viewModel::setLibraryListStyle)
            LibraryItem.GridDensityItem ->
                return stepOption(GridDensity.entries, display.gridDensity, direction, viewModel::setGridDensity)
            LibraryItem.BoxArtItem ->
                return stepOption(listOf(false, true), display.libraryBoxArt3d, direction, viewModel::setLibraryBoxArt3d)
            LibraryItem.DefaultSort -> viewModel.cycleLibraryDefaultSort(direction)
            LibraryItem.DefaultPlatform ->
                viewModel.cycleLibraryDefaultPlatform(direction, libraryPlatformTokens(layoutState))
            LibraryItem.DefaultSource -> return stepOption(
                librarySourceKeys(),
                display.libraryDefaultSource,
                direction,
                viewModel::setLibraryDefaultSource
            )
            LibraryItem.DefaultPlayers -> viewModel.cycleLibraryDefaultPlayers(direction)
            else -> return InputResult.UNHANDLED
        }
        return InputResult.HANDLED
    }

    private fun handleBuiltinEmulatorLeftRight(direction: Int): InputResult {
        val state = viewModel.uiState.value
        if (!state.emulators.builtinLibretroEnabled) return InputResult.UNHANDLED
        return when (state.focusedIndex) {
            BuiltinEmulatorItem.ARCHITECTURE.focusIndex -> stepOption(
                ARCHITECTURE_OPTIONS,
                state.emulators.architectureDisplay,
                direction,
                viewModel::setBuiltinArchitecture
            )
            BuiltinEmulatorItem.HUD_CORNER.focusIndex -> stepOption(
                HudCorner.entries,
                hudCornerFromStored(state.emulators.hudCorner),
                direction
            ) { viewModel.setHudCorner(it.name) }
            else -> InputResult.UNHANDLED
        }
    }

    private fun handleCoreManagementLeftRight(direction: Int): InputResult {
        viewModel.moveCoreManagementCoreFocus(direction)
        return InputResult.HANDLED
    }

    private fun handleSectionJump(direction: Int): InputResult {
        val state = viewModel.uiState.value
        val sections = when (state.currentSection) {
            SettingsSection.MAIN -> return if (state.controls.quickNavigation) {
                InputResult.UNHANDLED
            } else {
                handleMainGroupJump(direction)
            }
            SettingsSection.CONTROLLER_GRIP -> controllerGripSections(state.display)
            SettingsSection.HOME_SCREEN -> homeScreenSections(state.display)
            SettingsSection.PRESENTATION -> presentationSections(state.display.presentationStyle)
            SettingsSection.LIBRARY_VIEW -> librarySections(LibraryLayoutState.from(state))
            SettingsSection.BIOS -> biosSections(state.bios.platformGroups, state.bios.expandedPlatformIndex)
            SettingsSection.ROMM -> rommSections(buildRomMItemsFromState(state))
            SettingsSection.SAVES -> savesSections(SavesLayoutState.from(state))
            SettingsSection.STEAM_SETTINGS -> steamSections(state.steam)
            SettingsSection.JELLYFIN -> if (state.jellyfin.configuring || state.jellyfin.showLoginForm) {
                return InputResult.HANDLED
            } else {
                jellyfinSections(JellyfinLayoutState.from(state))
            }
            SettingsSection.SOCIAL -> socialSections(hasAvatarDoodle = state.social.avatarDoodle != null)
            SettingsSection.NAVIGATION -> navigationSections(state.controls)
            SettingsSection.AUDIO -> audioSections()
            SettingsSection.ABOUT -> aboutSections(state.fileLoggingPath != null, aboutHasChangelog(state.updateCheck))
            else -> return InputResult.HANDLED
        }
        val jumped = if (direction < 0) viewModel.jumpToPrevSection(sections)
            else viewModel.jumpToNextSection(sections)
        return if (jumped) InputResult.HANDLED else InputResult.HANDLED
    }

}
