package com.nendo.argosy.hardware

import android.util.Log
import com.nendo.argosy.data.emulator.EmulatorDetector
import com.nendo.argosy.ui.common.savechannel.SaveFocusColumn
import com.nendo.argosy.ui.dualscreen.DualScreenBroadcasts
import com.nendo.argosy.ui.dualscreen.gamedetail.ActiveModal
import com.nendo.argosy.ui.dualscreen.gamedetail.DualGameDetailTab
import com.nendo.argosy.ui.dualscreen.gamedetail.DualGameDetailViewModel
import com.nendo.argosy.ui.dualscreen.gamedetail.GameDetailOption
import com.nendo.argosy.ui.dualscreen.home.DualHomeFocusZone
import com.nendo.argosy.ui.dualscreen.home.DualHomeViewModel
import com.nendo.argosy.ui.dualscreen.home.DualHomeViewMode
import com.nendo.argosy.ui.dualscreen.home.ForwardingMode
import com.nendo.argosy.hardware.CompanionScreen
import com.nendo.argosy.ui.input.GamepadEvent
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.screens.secondaryhome.SecondaryHomeViewModel

class SecondaryHomeInputHandler(
    private val viewModel: SecondaryHomeViewModel,
    private val dualHomeViewModel: DualHomeViewModel,
    private val broadcasts: SecondaryHomeBroadcastHelper,
    private val homeApps: () -> List<String>,
    private val dualGameDetailViewModel: () -> DualGameDetailViewModel?,
    private val isScreenshotViewerOpen: () -> Boolean,
    private val setScreenshotViewerOpen: (Boolean) -> Unit,
    private val onSelectGame: (Long) -> Unit,
    private val onReturnToHome: () -> Unit,
    private val onLaunchApp: (String) -> Unit,
    private val onLaunchAppOnOtherDisplay: (String) -> Unit,
    private val onRefocusSelf: () -> Unit,
    private val onPersistCarouselPosition: () -> Unit,
    private val context: android.content.Context,
    private val lifecycleLaunch: (suspend () -> Unit) -> Unit
) {

    fun routeInput(
        event: GamepadEvent,
        useDualScreenMode: Boolean,
        isArgosyForeground: Boolean,
        isGameActive: Boolean,
        currentScreen: CompanionScreen
    ): InputResult = if (useDualScreenMode && isArgosyForeground && !isGameActive) {
        when (currentScreen) {
            CompanionScreen.HOME -> handleDualHomeInput(event)
            CompanionScreen.GAME_DETAIL -> handleDualDetailInput(event)
        }
    } else if (useDualScreenMode && isGameActive) {
        handleCompanionInput(event)
    } else {
        handleGridInput(event)
    }

    fun handleDualHomeInput(event: GamepadEvent): InputResult {
        if (viewModel.uiState.value.isDrawerOpen) return handleDrawerInput(event)
        if (dualHomeViewModel.forwardingMode.value != ForwardingMode.NONE) {
            return InputResult.HANDLED
        }

        return when (dualHomeViewModel.uiState.value.viewMode) {
            DualHomeViewMode.CAROUSEL -> handleCarouselInput(event)
            DualHomeViewMode.COLLECTIONS -> handleCollectionsInput(event)
            DualHomeViewMode.COLLECTION_GAMES -> handleCollectionGamesInput(event)
            DualHomeViewMode.LIBRARY_GRID -> handleLibraryGridInput(event)
        }
    }

    fun handleDualDetailInput(event: GamepadEvent): InputResult {
        val vm = dualGameDetailViewModel() ?: return InputResult.UNHANDLED

        val modal = vm.activeModal.value
        if (modal != ActiveModal.NONE) {
            return handleModalInput(vm, modal, event)
        }

        return when (event) {
            GamepadEvent.PrevSection -> {
                broadcasts.broadcastScreenshotCleared()
                setScreenshotViewerOpen(false)
                vm.previousTab()
                InputResult.HANDLED
            }
            GamepadEvent.NextSection -> {
                broadcasts.broadcastScreenshotCleared()
                setScreenshotViewerOpen(false)
                vm.nextTab()
                InputResult.HANDLED
            }
            GamepadEvent.Up -> {
                vm.moveSelectionUp()
                if (isScreenshotViewerOpen()) {
                    broadcasts.broadcastScreenshotSelected(vm.selectedScreenshotIndex.value)
                }
                InputResult.HANDLED
            }
            GamepadEvent.Down -> {
                vm.moveSelectionDown()
                if (isScreenshotViewerOpen()) {
                    broadcasts.broadcastScreenshotSelected(vm.selectedScreenshotIndex.value)
                }
                InputResult.HANDLED
            }
            GamepadEvent.Left -> {
                when (vm.uiState.value.currentTab) {
                    DualGameDetailTab.SAVES -> vm.focusSlotsColumn()
                    DualGameDetailTab.OPTIONS -> handleInlineAdjust(vm, -1)
                    DualGameDetailTab.MEDIA -> {
                        vm.moveSelectionLeft()
                        if (isScreenshotViewerOpen()) {
                            broadcasts.broadcastScreenshotSelected(vm.selectedScreenshotIndex.value)
                        }
                    }
                }
                InputResult.HANDLED
            }
            GamepadEvent.Right -> {
                when (vm.uiState.value.currentTab) {
                    DualGameDetailTab.SAVES -> vm.focusHistoryColumn()
                    DualGameDetailTab.OPTIONS -> handleInlineAdjust(vm, 1)
                    DualGameDetailTab.MEDIA -> {
                        vm.moveSelectionRight()
                        if (isScreenshotViewerOpen()) {
                            broadcasts.broadcastScreenshotSelected(vm.selectedScreenshotIndex.value)
                        }
                    }
                }
                InputResult.HANDLED
            }
            GamepadEvent.Confirm -> {
                when (vm.uiState.value.currentTab) {
                    DualGameDetailTab.SAVES -> handleSaveConfirm(vm)
                    DualGameDetailTab.MEDIA -> {
                        val idx = vm.selectedScreenshotIndex.value
                        if (idx >= 0) {
                            setScreenshotViewerOpen(true)
                            broadcasts.broadcastScreenshotSelected(idx)
                        }
                    }
                    DualGameDetailTab.OPTIONS -> handleOptionAction(vm)
                }
                InputResult.HANDLED
            }
            GamepadEvent.Back -> {
                if (isScreenshotViewerOpen()) {
                    broadcasts.broadcastScreenshotCleared()
                    setScreenshotViewerOpen(false)
                } else {
                    onReturnToHome()
                }
                InputResult.HANDLED
            }
            GamepadEvent.ContextMenu -> {
                if (vm.uiState.value.currentTab == DualGameDetailTab.SAVES) {
                    handleSaveLockAsSlot(vm)
                }
                InputResult.HANDLED
            }
            GamepadEvent.SecondaryAction -> InputResult.HANDLED
            else -> InputResult.UNHANDLED
        }
    }

    fun handleCompanionInput(event: GamepadEvent): InputResult {
        if (viewModel.uiState.value.isDrawerOpen) return handleDrawerInput(event)

        val state = viewModel.uiState.value
        val appBarIndex = state.companionAppBarIndex

        return when (event) {
            GamepadEvent.Left -> {
                viewModel.companionSelectPreviousApp()
                InputResult.HANDLED
            }
            GamepadEvent.Right -> {
                viewModel.companionSelectNextApp(homeApps().size)
                InputResult.HANDLED
            }
            GamepadEvent.Confirm -> {
                if (appBarIndex == -1) {
                    viewModel.openDrawer()
                    InputResult.HANDLED
                } else {
                    val packageName = homeApps().getOrNull(appBarIndex)
                    if (packageName != null) {
                        onLaunchApp(packageName)
                        InputResult.HANDLED
                    } else InputResult.UNHANDLED
                }
            }
            GamepadEvent.Select -> {
                viewModel.openDrawer()
                InputResult.HANDLED
            }
            else -> InputResult.UNHANDLED
        }
    }

    fun handleGridInput(event: GamepadEvent): InputResult {
        if (viewModel.uiState.value.isDrawerOpen) return handleDrawerInput(event)

        return when (event) {
            GamepadEvent.Up -> {
                viewModel.moveFocusUp()
                InputResult.HANDLED
            }
            GamepadEvent.Down -> {
                viewModel.moveFocusDown()
                InputResult.HANDLED
            }
            GamepadEvent.Left -> {
                viewModel.moveFocusLeft()
                InputResult.HANDLED
            }
            GamepadEvent.Right -> {
                viewModel.moveFocusRight()
                InputResult.HANDLED
            }
            GamepadEvent.PrevSection -> {
                viewModel.previousSection()
                InputResult.HANDLED
            }
            GamepadEvent.NextSection -> {
                viewModel.nextSection()
                InputResult.HANDLED
            }
            GamepadEvent.Confirm -> {
                selectFocusedGame()
                InputResult.HANDLED
            }
            GamepadEvent.ContextMenu -> {
                launchFocusedGame()
                InputResult.HANDLED
            }
            GamepadEvent.Select -> {
                viewModel.openDrawer()
                broadcasts.broadcastViewModeChange(drawerOpen = true)
                InputResult.HANDLED
            }
            else -> InputResult.UNHANDLED
        }
    }

    fun handleOption(vm: DualGameDetailViewModel, option: GameDetailOption) {
        val gameId = vm.uiState.value.gameId
        when (option) {
            GameDetailOption.PLAY -> {
                if (vm.uiState.value.isPlayable) {
                    broadcasts.broadcastDirectAction("PLAY", gameId, vm.uiState.value.activeChannel)
                } else {
                    broadcasts.broadcastDirectAction("DOWNLOAD", gameId)
                }
            }
            GameDetailOption.RATING -> {
                vm.openRatingPicker()
                broadcasts.broadcastModalState(vm, ActiveModal.RATING)
            }
            GameDetailOption.DIFFICULTY -> {
                vm.openDifficultyPicker()
                broadcasts.broadcastModalState(vm, ActiveModal.DIFFICULTY)
            }
            GameDetailOption.STATUS -> {
                vm.openStatusPicker()
                broadcasts.broadcastModalState(vm, ActiveModal.STATUS)
            }
            GameDetailOption.TOGGLE_FAVORITE -> vm.toggleFavorite()
            GameDetailOption.CHANGE_EMULATOR -> {
                lifecycleLaunch {
                    val detector = EmulatorDetector(context)
                    detector.detectEmulators()
                    val emulators = detector.getInstalledForPlatform(
                        vm.uiState.value.platformSlug
                    )
                    vm.openEmulatorPicker(emulators)
                    broadcasts.broadcastEmulatorModalOpen(
                        emulators, vm.uiState.value.emulatorName
                    )
                }
            }
            GameDetailOption.ADD_TO_COLLECTION -> {
                vm.openCollectionModal()
                lifecycleLaunch {
                    kotlinx.coroutines.delay(50)
                    broadcasts.broadcastCollectionModalOpen(vm)
                }
            }
            GameDetailOption.UPDATES_DLC -> {
                lifecycleLaunch {
                    vm.openUpdatesModal()
                    broadcasts.broadcastUpdatesModalOpen(vm)
                }
            }
            GameDetailOption.REFRESH_METADATA -> {
                broadcasts.broadcastDirectAction("REFRESH_METADATA", gameId)
            }
            GameDetailOption.DELETE -> {
                broadcasts.broadcastDirectAction("DELETE", gameId)
            }
            GameDetailOption.HIDE -> {
                broadcasts.broadcastDirectAction("HIDE", gameId)
            }
        }
    }

    private fun handleCarouselInput(event: GamepadEvent): InputResult {
        val state = dualHomeViewModel.uiState.value
        val inAppBar = state.focusZone == DualHomeFocusZone.APP_BAR
        val apps = homeApps()

        return when (event) {
            GamepadEvent.Menu, GamepadEvent.LeftStickClick, GamepadEvent.RightStickClick -> {
                broadcasts.broadcastOpenOverlay(overlayNameFor(event))
                InputResult.HANDLED
            }
            GamepadEvent.Left -> {
                if (inAppBar) dualHomeViewModel.selectPreviousApp()
                else {
                    dualHomeViewModel.selectPrevious()
                    onPersistCarouselPosition()
                }
                InputResult.HANDLED
            }
            GamepadEvent.Right -> {
                if (inAppBar) dualHomeViewModel.selectNextApp(apps.size)
                else {
                    dualHomeViewModel.selectNext()
                    onPersistCarouselPosition()
                }
                InputResult.HANDLED
            }
            GamepadEvent.Down -> {
                if (!inAppBar) {
                    dualHomeViewModel.focusAppBar(apps.size)
                    broadcasts.broadcastViewModeChange()
                    InputResult.HANDLED
                } else InputResult.UNHANDLED
            }
            GamepadEvent.Up -> {
                if (inAppBar) {
                    dualHomeViewModel.focusCarousel()
                    broadcasts.broadcastViewModeChange()
                    InputResult.HANDLED
                } else {
                    dualHomeViewModel.enterCollections()
                    broadcasts.broadcastViewModeChange()
                    broadcasts.broadcastCollectionFocused()
                    InputResult.HANDLED
                }
            }
            GamepadEvent.PrevSection -> {
                if (inAppBar) {
                    dualHomeViewModel.focusCarousel()
                    broadcasts.broadcastViewModeChange()
                }
                dualHomeViewModel.previousSortSection()
                onPersistCarouselPosition()
                InputResult.HANDLED
            }
            GamepadEvent.NextSection -> {
                if (inAppBar) {
                    dualHomeViewModel.focusCarousel()
                    broadcasts.broadcastViewModeChange()
                }
                dualHomeViewModel.nextSortSection()
                onPersistCarouselPosition()
                InputResult.HANDLED
            }
            GamepadEvent.Select -> {
                if (inAppBar) {
                    viewModel.openDrawer()
                    broadcasts.broadcastViewModeChange(drawerOpen = true)
                } else {
                    dualHomeViewModel.toggleLibraryGrid {
                        broadcasts.broadcastViewModeChange()
                        val s = dualHomeViewModel.uiState.value
                        if (s.viewMode == DualHomeViewMode.LIBRARY_GRID)
                            broadcasts.broadcastLibraryGameSelection()
                        else
                            broadcasts.broadcastCurrentGameSelection()
                    }
                }
                InputResult.HANDLED
            }
            GamepadEvent.Confirm -> {
                if (inAppBar && state.appBarIndex == -1) {
                    viewModel.openDrawer()
                    broadcasts.broadcastViewModeChange(drawerOpen = true)
                    InputResult.HANDLED
                } else if (inAppBar) {
                    val packageName = apps.getOrNull(state.appBarIndex)
                    if (packageName != null) {
                        onLaunchApp(packageName)
                        InputResult.HANDLED
                    } else InputResult.UNHANDLED
                } else if (state.isViewAllFocused) {
                    val platformId = state.currentPlatformId
                    if (platformId != null) {
                        dualHomeViewModel.enterLibraryGridForPlatform(platformId) {
                            broadcasts.broadcastViewModeChange()
                            broadcasts.broadcastLibraryGameSelection()
                        }
                    } else {
                        dualHomeViewModel.enterLibraryGrid {
                            broadcasts.broadcastViewModeChange()
                            broadcasts.broadcastLibraryGameSelection()
                        }
                    }
                    InputResult.HANDLED
                } else {
                    val game = state.selectedGame
                    if (game != null) {
                        val action = if (game.isPlayable) "PLAY" else "DOWNLOAD"
                        broadcasts.broadcastDirectAction(action, game.id)
                        InputResult.HANDLED
                    } else InputResult.UNHANDLED
                }
            }
            GamepadEvent.ContextMenu -> {
                if (inAppBar) return InputResult.UNHANDLED
                val game = state.selectedGame
                if (game != null) {
                    onSelectGame(game.id)
                    InputResult.HANDLED
                } else InputResult.UNHANDLED
            }
            GamepadEvent.SecondaryAction -> {
                if (inAppBar) {
                    val packageName = apps.getOrNull(state.appBarIndex)
                    if (packageName != null) {
                        onLaunchAppOnOtherDisplay(packageName)
                        InputResult.HANDLED
                    } else InputResult.UNHANDLED
                } else {
                    dualHomeViewModel.toggleFavorite()
                    InputResult.HANDLED
                }
            }
            else -> InputResult.UNHANDLED
        }
    }

    private fun handleCollectionsInput(event: GamepadEvent): InputResult {
        return when (event) {
            GamepadEvent.Up -> {
                dualHomeViewModel.moveCollectionFocus(-1)
                broadcasts.broadcastCollectionFocused()
                InputResult.HANDLED
            }
            GamepadEvent.Down -> {
                dualHomeViewModel.moveCollectionFocus(1)
                broadcasts.broadcastCollectionFocused()
                InputResult.HANDLED
            }
            GamepadEvent.Confirm -> {
                val collection = dualHomeViewModel.selectedCollectionItem()
                if (collection != null) {
                    dualHomeViewModel.enterCollectionGames(collection.id) {
                        broadcasts.broadcastViewModeChange()
                        broadcasts.broadcastCollectionGameSelection()
                    }
                }
                InputResult.HANDLED
            }
            GamepadEvent.Back -> {
                dualHomeViewModel.exitToCarousel()
                broadcasts.broadcastViewModeChange()
                broadcasts.broadcastCurrentGameSelection()
                InputResult.HANDLED
            }
            GamepadEvent.Menu, GamepadEvent.LeftStickClick, GamepadEvent.RightStickClick -> {
                broadcasts.broadcastOpenOverlay(overlayNameFor(event))
                InputResult.HANDLED
            }
            else -> InputResult.HANDLED
        }
    }

    private fun handleCollectionGamesInput(event: GamepadEvent): InputResult {
        val columns = dualHomeViewModel.uiState.value.libraryColumns
        return when (event) {
            GamepadEvent.Left -> {
                dualHomeViewModel.moveCollectionGamesFocus(-1)
                broadcasts.broadcastCollectionGameSelection()
                InputResult.HANDLED
            }
            GamepadEvent.Right -> {
                dualHomeViewModel.moveCollectionGamesFocus(1)
                broadcasts.broadcastCollectionGameSelection()
                InputResult.HANDLED
            }
            GamepadEvent.Up -> {
                dualHomeViewModel.moveCollectionGamesFocus(-columns)
                broadcasts.broadcastCollectionGameSelection()
                InputResult.HANDLED
            }
            GamepadEvent.Down -> {
                dualHomeViewModel.moveCollectionGamesFocus(columns)
                broadcasts.broadcastCollectionGameSelection()
                InputResult.HANDLED
            }
            GamepadEvent.Confirm -> {
                val game = dualHomeViewModel.focusedCollectionGame()
                if (game != null) {
                    val action = if (game.isPlayable) "PLAY" else "DOWNLOAD"
                    broadcasts.broadcastDirectAction(action, game.id)
                }
                InputResult.HANDLED
            }
            GamepadEvent.ContextMenu -> {
                val game = dualHomeViewModel.focusedCollectionGame()
                if (game != null) onSelectGame(game.id)
                InputResult.HANDLED
            }
            GamepadEvent.Back -> {
                dualHomeViewModel.exitCollectionGames()
                broadcasts.broadcastViewModeChange()
                broadcasts.broadcastCollectionFocused()
                InputResult.HANDLED
            }
            GamepadEvent.Menu, GamepadEvent.LeftStickClick, GamepadEvent.RightStickClick -> {
                broadcasts.broadcastOpenOverlay(overlayNameFor(event))
                InputResult.HANDLED
            }
            else -> InputResult.HANDLED
        }
    }

    private fun handleLibraryGridInput(event: GamepadEvent): InputResult {
        if (dualHomeViewModel.uiState.value.showFilterOverlay) {
            return handleFilterInput(event)
        }

        val columns = dualHomeViewModel.uiState.value.libraryColumns
        return when (event) {
            GamepadEvent.Left -> {
                dualHomeViewModel.moveLibraryFocus(-1)
                broadcasts.broadcastLibraryGameSelection()
                InputResult.HANDLED
            }
            GamepadEvent.Right -> {
                dualHomeViewModel.moveLibraryFocus(1)
                broadcasts.broadcastLibraryGameSelection()
                InputResult.HANDLED
            }
            GamepadEvent.Up -> {
                dualHomeViewModel.moveLibraryFocus(-columns)
                broadcasts.broadcastLibraryGameSelection()
                InputResult.HANDLED
            }
            GamepadEvent.Down -> {
                dualHomeViewModel.moveLibraryFocus(columns)
                broadcasts.broadcastLibraryGameSelection()
                InputResult.HANDLED
            }
            GamepadEvent.PrevTrigger -> {
                dualHomeViewModel.previousSortSection()
                broadcasts.broadcastLibraryGameSelection()
                InputResult.HANDLED
            }
            GamepadEvent.NextTrigger -> {
                dualHomeViewModel.nextSortSection()
                broadcasts.broadcastLibraryGameSelection()
                InputResult.HANDLED
            }
            GamepadEvent.Select -> {
                dualHomeViewModel.toggleLibraryGrid {
                    broadcasts.broadcastViewModeChange()
                    broadcasts.broadcastCurrentGameSelection()
                }
                InputResult.HANDLED
            }
            GamepadEvent.Back -> {
                dualHomeViewModel.exitToCarousel()
                broadcasts.broadcastViewModeChange()
                broadcasts.broadcastCurrentGameSelection()
                InputResult.HANDLED
            }
            GamepadEvent.Confirm -> {
                val s = dualHomeViewModel.uiState.value
                val game = s.libraryGames.getOrNull(s.libraryFocusedIndex)
                if (game != null) {
                    val action = if (game.isPlayable) "PLAY" else "DOWNLOAD"
                    broadcasts.broadcastDirectAction(action, game.id)
                }
                InputResult.HANDLED
            }
            GamepadEvent.ContextMenu -> {
                val s = dualHomeViewModel.uiState.value
                val game = s.libraryGames.getOrNull(s.libraryFocusedIndex)
                if (game != null) onSelectGame(game.id)
                InputResult.HANDLED
            }
            GamepadEvent.PrevSection -> {
                dualHomeViewModel.cycleLibraryPlatform(-1) {
                    broadcasts.broadcastLibraryGameSelection()
                }
                InputResult.HANDLED
            }
            GamepadEvent.NextSection -> {
                dualHomeViewModel.cycleLibraryPlatform(1) {
                    broadcasts.broadcastLibraryGameSelection()
                }
                InputResult.HANDLED
            }
            GamepadEvent.SecondaryAction -> {
                dualHomeViewModel.toggleFilterOverlay()
                InputResult.HANDLED
            }
            GamepadEvent.Menu, GamepadEvent.LeftStickClick, GamepadEvent.RightStickClick -> {
                broadcasts.broadcastOpenOverlay(overlayNameFor(event))
                InputResult.HANDLED
            }
            else -> InputResult.HANDLED
        }
    }

    private fun handleFilterInput(event: GamepadEvent): InputResult {
        return when (event) {
            GamepadEvent.Up -> {
                dualHomeViewModel.moveFilterFocus(-1)
                InputResult.HANDLED
            }
            GamepadEvent.Down -> {
                dualHomeViewModel.moveFilterFocus(1)
                InputResult.HANDLED
            }
            GamepadEvent.PrevSection -> {
                dualHomeViewModel.previousFilterCategory()
                InputResult.HANDLED
            }
            GamepadEvent.NextSection -> {
                dualHomeViewModel.nextFilterCategory()
                InputResult.HANDLED
            }
            GamepadEvent.Confirm -> {
                dualHomeViewModel.confirmFilter()
                InputResult.HANDLED
            }
            GamepadEvent.Back, GamepadEvent.SecondaryAction -> {
                dualHomeViewModel.toggleFilterOverlay()
                InputResult.HANDLED
            }
            GamepadEvent.ContextMenu -> {
                dualHomeViewModel.clearCategoryFilters()
                InputResult.HANDLED
            }
            else -> InputResult.HANDLED
        }
    }

    private fun handleModalInput(
        vm: DualGameDetailViewModel,
        modal: ActiveModal,
        event: GamepadEvent
    ): InputResult {
        when (modal) {
            ActiveModal.EMULATOR -> {
                when (event) {
                    GamepadEvent.Up -> {
                        vm.moveEmulatorPickerFocus(-1)
                        broadcasts.broadcastInlineUpdate(
                            "emulator_focus",
                            vm.emulatorPickerFocusIndex.value
                        )
                    }
                    GamepadEvent.Down -> {
                        vm.moveEmulatorPickerFocus(1)
                        broadcasts.broadcastInlineUpdate(
                            "emulator_focus",
                            vm.emulatorPickerFocusIndex.value
                        )
                    }
                    GamepadEvent.Confirm -> {
                        val idx = vm.emulatorPickerFocusIndex.value
                        vm.confirmEmulatorByIndex(idx)
                        broadcasts.broadcastModalConfirmResult(
                            modal, idx, null
                        )
                    }
                    GamepadEvent.Back -> {
                        vm.dismissPicker()
                        broadcasts.broadcastModalClose()
                    }
                    else -> {}
                }
                return InputResult.HANDLED
            }
            ActiveModal.COLLECTION -> {
                when (event) {
                    GamepadEvent.Up -> {
                        vm.moveCollectionPickerFocus(-1)
                        broadcasts.broadcastInlineUpdate(
                            "collection_focus",
                            vm.collectionPickerFocusIndex.value
                        )
                    }
                    GamepadEvent.Down -> {
                        vm.moveCollectionPickerFocus(1)
                        broadcasts.broadcastInlineUpdate(
                            "collection_focus",
                            vm.collectionPickerFocusIndex.value
                        )
                    }
                    GamepadEvent.Confirm -> {
                        val idx = vm.collectionPickerFocusIndex.value
                        val items = vm.collectionItems.value
                        if (idx < items.size) {
                            val item = items[idx]
                            vm.toggleCollection(item.id)
                            broadcasts.broadcastInlineUpdate(
                                "collection_toggle",
                                item.id.toInt()
                            )
                        } else {
                            broadcasts.broadcastInlineUpdate(
                                "collection_create", 1
                            )
                        }
                    }
                    GamepadEvent.Back -> {
                        vm.dismissCollectionModal()
                        broadcasts.broadcastModalClose()
                    }
                    else -> {}
                }
                return InputResult.HANDLED
            }
            ActiveModal.RATING, ActiveModal.DIFFICULTY -> {
                when (event) {
                    GamepadEvent.Left -> {
                        vm.adjustPickerValue(-1)
                        broadcasts.broadcastInlineUpdate(
                            "modal_rating",
                            vm.ratingPickerValue.value
                        )
                    }
                    GamepadEvent.Right -> {
                        vm.adjustPickerValue(1)
                        broadcasts.broadcastInlineUpdate(
                            "modal_rating",
                            vm.ratingPickerValue.value
                        )
                    }
                    GamepadEvent.Confirm -> {
                        val ratingValue = vm.ratingPickerValue.value
                        vm.confirmPicker()
                        broadcasts.broadcastModalConfirmResult(
                            modal, ratingValue, null
                        )
                    }
                    GamepadEvent.Back -> {
                        vm.dismissPicker()
                        broadcasts.broadcastModalClose()
                    }
                    else -> {}
                }
                return InputResult.HANDLED
            }
            ActiveModal.STATUS -> {
                when (event) {
                    GamepadEvent.Up -> {
                        vm.moveStatusSelection(-1)
                        broadcasts.broadcastInlineUpdate(
                            "modal_status",
                            vm.statusPickerValue.value
                        )
                    }
                    GamepadEvent.Down -> {
                        vm.moveStatusSelection(1)
                        broadcasts.broadcastInlineUpdate(
                            "modal_status",
                            vm.statusPickerValue.value
                        )
                    }
                    GamepadEvent.Confirm -> {
                        val statusValue = vm.statusPickerValue.value
                        vm.confirmPicker()
                        broadcasts.broadcastModalConfirmResult(
                            modal, 0, statusValue
                        )
                    }
                    GamepadEvent.Back -> {
                        vm.dismissPicker()
                        broadcasts.broadcastModalClose()
                    }
                    else -> {}
                }
                return InputResult.HANDLED
            }
            ActiveModal.UPDATES_DLC -> {
                Log.d("UpdatesDLC", "handleModalInput: event=$event")
                when (event) {
                    GamepadEvent.Up -> {
                        vm.moveUpdatesFocus(-1)
                        broadcasts.broadcastInlineUpdate(
                            "updates_focus",
                            vm.updatesPickerFocusIndex.value
                        )
                    }
                    GamepadEvent.Down -> {
                        vm.moveUpdatesFocus(1)
                        broadcasts.broadcastInlineUpdate(
                            "updates_focus",
                            vm.updatesPickerFocusIndex.value
                        )
                    }
                    GamepadEvent.Confirm -> {
                        val allFiles = vm.updateFiles.value + vm.dlcFiles.value
                        val idx = vm.updatesPickerFocusIndex.value
                        val file = allFiles.getOrNull(idx)
                        Log.d("UpdatesDLC", "Confirm: idx=$idx, file=${file?.fileName}, downloaded=${file?.isDownloaded}, applied=${file?.isAppliedToEmulator}, gameFileId=${file?.gameFileId}")
                        if (file != null) {
                            val gameId = vm.uiState.value.gameId
                            when {
                                !file.isDownloaded && file.gameFileId != null -> {
                                    Log.d("UpdatesDLC", "Confirm: broadcasting DOWNLOAD_UPDATE_FILE gameId=$gameId fileId=${file.gameFileId}")
                                    broadcasts.broadcastDirectAction(
                                        "DOWNLOAD_UPDATE_FILE",
                                        gameId,
                                        file.gameFileId.toString()
                                    )
                                }
                                file.isDownloaded && !file.isAppliedToEmulator -> {
                                    Log.d("UpdatesDLC", "Confirm: broadcasting INSTALL_UPDATE_FILE gameId=$gameId fileId=${file.gameFileId}")
                                    broadcasts.broadcastDirectAction(
                                        "INSTALL_UPDATE_FILE",
                                        gameId,
                                        file.gameFileId?.toString()
                                    )
                                }
                                else -> {
                                    Log.d("UpdatesDLC", "Confirm: no action for file state")
                                }
                            }
                        }
                    }
                    GamepadEvent.SecondaryAction -> {
                        val allFiles = vm.updateFiles.value + vm.dlcFiles.value
                        val gameId = vm.uiState.value.gameId
                        val downloadable = allFiles.filter {
                            !it.isDownloaded && it.gameFileId != null
                        }
                        Log.d("UpdatesDLC", "SecondaryAction: ${downloadable.size} downloadable, ${allFiles.count { it.isDownloaded && !it.isAppliedToEmulator }} installable")
                        if (downloadable.isNotEmpty()) {
                            for (file in downloadable) {
                                broadcasts.broadcastDirectAction(
                                    "DOWNLOAD_UPDATE_FILE",
                                    gameId,
                                    file.gameFileId.toString()
                                )
                            }
                        } else {
                            val installable = allFiles.filter {
                                it.isDownloaded && !it.isAppliedToEmulator
                            }
                            for (file in installable) {
                                broadcasts.broadcastDirectAction(
                                    "INSTALL_UPDATE_FILE",
                                    gameId,
                                    file.gameFileId?.toString()
                                )
                            }
                        }
                    }
                    GamepadEvent.Back -> {
                        vm.dismissUpdatesModal()
                        broadcasts.broadcastModalClose()
                    }
                    else -> {}
                }
                return InputResult.HANDLED
            }
            ActiveModal.SAVE_NAME, ActiveModal.NONE -> {}
        }

        return InputResult.HANDLED
    }

    private fun handleInlineAdjust(vm: DualGameDetailViewModel, delta: Int) {
        val index = vm.selectedOptionIndex.value
        val option = vm.visibleOptions.value.getOrNull(index) ?: return
        when (option) {
            GameDetailOption.RATING -> {
                vm.adjustRatingInline(delta)
                broadcasts.broadcastInlineUpdate("rating", vm.uiState.value.rating ?: 0)
            }
            GameDetailOption.DIFFICULTY -> {
                vm.adjustDifficultyInline(delta)
                broadcasts.broadcastInlineUpdate("difficulty", vm.uiState.value.userDifficulty)
            }
            GameDetailOption.STATUS -> {
                vm.cycleStatusInline(delta)
                broadcasts.broadcastInlineUpdate("status", vm.uiState.value.status)
            }
            else -> {}
        }
    }

    private fun handleOptionAction(vm: DualGameDetailViewModel) {
        val index = vm.selectedOptionIndex.value
        val option = vm.visibleOptions.value.getOrNull(index) ?: return
        handleOption(vm, option)
    }

    private fun handleSaveConfirm(vm: DualGameDetailViewModel) {
        val state = vm.uiState.value
        if (state.saveFocusColumn == SaveFocusColumn.SLOTS) {
            val slot = vm.saveSlots.value
                .getOrNull(vm.selectedSlotIndex.value) ?: return
            if (slot.isCreateAction) {
                broadcasts.broadcastSaveNamePrompt("CREATE_SLOT", cacheId = null)
            } else {
                vm.setActiveChannel(slot.channelName)
                broadcasts.broadcastSaveAction(
                    "SAVE_SWITCH_CHANNEL",
                    state.gameId,
                    channelName = slot.channelName
                )
            }
        } else {
            val item = vm.saveHistory.value
                .getOrNull(vm.selectedHistoryIndex.value) ?: return
            val channelName = vm.focusedSlotChannelName
            vm.setActiveRestorePoint(channelName, item.timestamp)
            broadcasts.broadcastSaveAction(
                "SAVE_SET_RESTORE_POINT",
                state.gameId,
                channelName = channelName,
                timestamp = item.timestamp
            )
        }
    }

    private fun handleSaveLockAsSlot(vm: DualGameDetailViewModel) {
        val state = vm.uiState.value
        if (state.saveFocusColumn != SaveFocusColumn.HISTORY) return
        val item = vm.saveHistory.value
            .getOrNull(vm.selectedHistoryIndex.value) ?: return
        broadcasts.broadcastSaveNamePrompt("LOCK_AS_SLOT", cacheId = item.cacheId)
    }

    fun handleDrawerInput(event: GamepadEvent): InputResult {
        return when (event) {
            GamepadEvent.Up -> {
                viewModel.moveDrawerFocusUp()
                InputResult.HANDLED
            }
            GamepadEvent.Down -> {
                viewModel.moveDrawerFocusDown()
                InputResult.HANDLED
            }
            GamepadEvent.Left -> {
                viewModel.moveDrawerFocusLeft()
                InputResult.HANDLED
            }
            GamepadEvent.Right -> {
                viewModel.moveDrawerFocusRight()
                InputResult.HANDLED
            }
            GamepadEvent.Confirm -> {
                val (intent, options) = viewModel.launchDrawerApp()
                    ?: return InputResult.HANDLED
                viewModel.closeDrawer()
                broadcasts.broadcastViewModeChange(drawerOpen = false)
                if (options != null) {
                    intent?.let { onLaunchApp("__drawer_intent__") }
                    // Use the intent/options directly via onLaunchDrawerApp
                    launchDrawerApp(intent, options)
                } else {
                    intent?.let { launchDrawerApp(it, null) }
                }
                InputResult.HANDLED
            }
            GamepadEvent.ContextMenu -> {
                viewModel.toggleDrawerFocusedPin()
                InputResult.HANDLED
            }
            GamepadEvent.SecondaryAction -> {
                val packageName = viewModel.focusedDrawerAppPackageName()
                    ?: return InputResult.HANDLED
                viewModel.closeDrawer()
                broadcasts.broadcastViewModeChange(drawerOpen = false)
                onLaunchAppOnOtherDisplay(packageName)
                InputResult.HANDLED
            }
            GamepadEvent.Back, GamepadEvent.Select -> {
                viewModel.closeDrawer()
                broadcasts.broadcastViewModeChange(drawerOpen = false)
                InputResult.HANDLED
            }
            else -> InputResult.HANDLED
        }
    }

    private var drawerAppLauncher: ((android.content.Intent?, android.os.Bundle?) -> Unit)? = null

    fun setDrawerAppLauncher(launcher: (android.content.Intent?, android.os.Bundle?) -> Unit) {
        drawerAppLauncher = launcher
    }

    private fun launchDrawerApp(intent: android.content.Intent?, options: android.os.Bundle?) {
        drawerAppLauncher?.invoke(intent, options)
    }

    private fun selectFocusedGame() {
        val (intent, options) = viewModel.selectFocusedGame() ?: return
        drawerAppLauncher?.invoke(intent, options)
    }

    private fun launchFocusedGame() {
        val result = viewModel.launchFocusedGame() ?: return
        val (intent, options) = result
        intent?.let { drawerAppLauncher?.invoke(it, options) }
    }
}

private fun overlayNameFor(event: GamepadEvent): String = when (event) {
    GamepadEvent.LeftStickClick -> DualScreenBroadcasts.OVERLAY_QUICK_MENU
    GamepadEvent.RightStickClick -> DualScreenBroadcasts.OVERLAY_QUICK_SETTINGS
    else -> DualScreenBroadcasts.OVERLAY_MENU
}
