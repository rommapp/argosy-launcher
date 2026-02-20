package com.nendo.argosy.ui.dualscreen.gamedetail

import com.nendo.argosy.ui.common.savechannel.SaveFocusColumn
import com.nendo.argosy.ui.input.GamepadEvent
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult

class DualGameDetailInputHandler(
    private val context: android.content.Context,
    private val viewModel: () -> DualGameDetailViewModel?,
    private val isScreenshotViewerOpen: () -> Boolean,
    private val setScreenshotViewerOpen: (Boolean) -> Unit,
    private val onBroadcastScreenshotSelected: (Int) -> Unit,
    private val onBroadcastScreenshotCleared: () -> Unit,
    private val onBroadcastModalState: (DualGameDetailViewModel, ActiveModal) -> Unit,
    private val onBroadcastModalClose: () -> Unit,
    private val onBroadcastModalConfirm: (ActiveModal, Int, String?) -> Unit,
    private val onBroadcastInlineUpdate: (String, Any) -> Unit,
    private val onBroadcastDirectAction: (String, Long, String?) -> Unit,
    private val onBroadcastEmulatorModalOpen: (List<com.nendo.argosy.data.emulator.InstalledEmulator>, String?) -> Unit,
    private val onBroadcastCollectionModalOpen: (DualGameDetailViewModel) -> Unit,
    private val onBroadcastSaveNamePrompt: (String, Long?) -> Unit,
    private val onBroadcastSaveAction: (String, Long, String?, Long?) -> Unit,
    private val onReturnToHome: () -> Unit,
    private val onRefocusSelf: () -> Unit,
    private val lifecycleLaunch: (suspend () -> Unit) -> Unit
) : InputHandler {

    override fun onUp() = dispatch(GamepadEvent.Up)
    override fun onDown() = dispatch(GamepadEvent.Down)
    override fun onLeft() = dispatch(GamepadEvent.Left)
    override fun onRight() = dispatch(GamepadEvent.Right)
    override fun onConfirm() = dispatch(GamepadEvent.Confirm)
    override fun onBack() = dispatch(GamepadEvent.Back)
    override fun onMenu() = dispatch(GamepadEvent.Menu)
    override fun onSecondaryAction() = dispatch(GamepadEvent.SecondaryAction)
    override fun onContextMenu() = dispatch(GamepadEvent.ContextMenu)
    override fun onPrevSection() = dispatch(GamepadEvent.PrevSection)
    override fun onNextSection() = dispatch(GamepadEvent.NextSection)
    override fun onPrevTrigger() = dispatch(GamepadEvent.PrevTrigger)
    override fun onNextTrigger() = dispatch(GamepadEvent.NextTrigger)
    override fun onSelect() = dispatch(GamepadEvent.Select)
    override fun onLeftStickClick() = dispatch(GamepadEvent.LeftStickClick)
    override fun onRightStickClick() = dispatch(GamepadEvent.RightStickClick)

    fun dispatch(event: GamepadEvent): InputResult {
        val vm = viewModel() ?: return InputResult.UNHANDLED

        val modal = vm.activeModal.value
        if (modal != ActiveModal.NONE) {
            return handleModal(vm, modal, event)
        }

        return when (event) {
            GamepadEvent.PrevSection -> {
                onBroadcastScreenshotCleared()
                vm.previousTab()
                InputResult.HANDLED
            }
            GamepadEvent.NextSection -> {
                onBroadcastScreenshotCleared()
                vm.nextTab()
                InputResult.HANDLED
            }
            GamepadEvent.Up -> {
                vm.moveSelectionUp()
                if (isScreenshotViewerOpen()) {
                    onBroadcastScreenshotSelected(vm.selectedScreenshotIndex.value)
                }
                InputResult.HANDLED
            }
            GamepadEvent.Down -> {
                vm.moveSelectionDown()
                if (isScreenshotViewerOpen()) {
                    onBroadcastScreenshotSelected(vm.selectedScreenshotIndex.value)
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
                            onBroadcastScreenshotSelected(vm.selectedScreenshotIndex.value)
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
                            onBroadcastScreenshotSelected(vm.selectedScreenshotIndex.value)
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
                            onBroadcastScreenshotSelected(idx)
                        }
                    }
                    DualGameDetailTab.OPTIONS -> handleOptionAction(vm)
                }
                InputResult.HANDLED
            }
            GamepadEvent.Back -> {
                if (isScreenshotViewerOpen()) {
                    setScreenshotViewerOpen(false)
                    onBroadcastScreenshotCleared()
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

    private fun handleModal(
        vm: DualGameDetailViewModel,
        modal: ActiveModal,
        event: GamepadEvent
    ): InputResult {
        when (modal) {
            ActiveModal.EMULATOR -> {
                when (event) {
                    GamepadEvent.Up -> {
                        vm.moveEmulatorPickerFocus(-1)
                        onBroadcastInlineUpdate(
                            "emulator_focus",
                            vm.emulatorPickerFocusIndex.value
                        )
                    }
                    GamepadEvent.Down -> {
                        vm.moveEmulatorPickerFocus(1)
                        onBroadcastInlineUpdate(
                            "emulator_focus",
                            vm.emulatorPickerFocusIndex.value
                        )
                    }
                    GamepadEvent.Confirm -> {
                        val idx = vm.emulatorPickerFocusIndex.value
                        vm.confirmEmulatorByIndex(idx)
                        onBroadcastModalConfirm(ActiveModal.EMULATOR, idx, null)
                    }
                    GamepadEvent.Back -> {
                        vm.dismissPicker()
                        onBroadcastModalClose()
                    }
                    else -> {}
                }
                return InputResult.HANDLED
            }
            ActiveModal.COLLECTION -> {
                when (event) {
                    GamepadEvent.Up -> {
                        vm.moveCollectionPickerFocus(-1)
                        onBroadcastInlineUpdate(
                            "collection_focus",
                            vm.collectionPickerFocusIndex.value
                        )
                    }
                    GamepadEvent.Down -> {
                        vm.moveCollectionPickerFocus(1)
                        onBroadcastInlineUpdate(
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
                            onBroadcastInlineUpdate(
                                "collection_toggle",
                                item.id.toInt()
                            )
                        } else {
                            onBroadcastInlineUpdate(
                                "collection_create", 1
                            )
                        }
                    }
                    GamepadEvent.Back -> {
                        vm.dismissCollectionModal()
                        onBroadcastModalClose()
                    }
                    else -> {}
                }
                return InputResult.HANDLED
            }
            ActiveModal.RATING, ActiveModal.DIFFICULTY -> {
                when (event) {
                    GamepadEvent.Left -> {
                        vm.adjustPickerValue(-1)
                        onBroadcastInlineUpdate(
                            "modal_rating",
                            vm.ratingPickerValue.value
                        )
                    }
                    GamepadEvent.Right -> {
                        vm.adjustPickerValue(1)
                        onBroadcastInlineUpdate(
                            "modal_rating",
                            vm.ratingPickerValue.value
                        )
                    }
                    GamepadEvent.Confirm -> {
                        val value = vm.ratingPickerValue.value
                        vm.confirmPicker()
                        onBroadcastModalConfirm(modal, value, null)
                    }
                    GamepadEvent.Back -> {
                        vm.dismissPicker()
                        onBroadcastModalClose()
                    }
                    else -> {}
                }
                return InputResult.HANDLED
            }
            ActiveModal.STATUS -> {
                when (event) {
                    GamepadEvent.Up -> {
                        vm.moveStatusSelection(-1)
                        onBroadcastInlineUpdate(
                            "modal_status",
                            vm.statusPickerValue.value ?: ""
                        )
                    }
                    GamepadEvent.Down -> {
                        vm.moveStatusSelection(1)
                        onBroadcastInlineUpdate(
                            "modal_status",
                            vm.statusPickerValue.value ?: ""
                        )
                    }
                    GamepadEvent.Confirm -> {
                        val status = vm.statusPickerValue.value
                        vm.confirmPicker()
                        onBroadcastModalConfirm(ActiveModal.STATUS, 0, status)
                    }
                    GamepadEvent.Back -> {
                        vm.dismissPicker()
                        onBroadcastModalClose()
                    }
                    else -> {}
                }
                return InputResult.HANDLED
            }
            ActiveModal.UPDATES_DLC -> {
                if (event == GamepadEvent.Back) {
                    vm.dismissUpdatesModal()
                    onBroadcastModalClose()
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
                onBroadcastInlineUpdate("rating", vm.uiState.value.rating ?: 0)
            }
            GameDetailOption.DIFFICULTY -> {
                vm.adjustDifficultyInline(delta)
                onBroadcastInlineUpdate("difficulty", vm.uiState.value.userDifficulty)
            }
            GameDetailOption.STATUS -> {
                vm.cycleStatusInline(delta)
                onBroadcastInlineUpdate("status", vm.uiState.value.status ?: "")
            }
            else -> {}
        }
    }

    private fun handleOptionAction(vm: DualGameDetailViewModel) {
        val index = vm.selectedOptionIndex.value
        val option = vm.visibleOptions.value.getOrNull(index) ?: return
        handleOption(vm, option)
    }

    fun handleOption(vm: DualGameDetailViewModel, option: GameDetailOption) {
        val gameId = vm.uiState.value.gameId
        when (option) {
            GameDetailOption.PLAY -> {
                if (vm.uiState.value.isPlayable) {
                    onBroadcastDirectAction("PLAY", gameId, vm.uiState.value.activeChannel)
                } else {
                    onBroadcastDirectAction("DOWNLOAD", gameId, null)
                }
            }
            GameDetailOption.RATING -> {
                vm.openRatingPicker()
                onBroadcastModalState(vm, ActiveModal.RATING)
            }
            GameDetailOption.DIFFICULTY -> {
                vm.openDifficultyPicker()
                onBroadcastModalState(vm, ActiveModal.DIFFICULTY)
            }
            GameDetailOption.STATUS -> {
                vm.openStatusPicker()
                onBroadcastModalState(vm, ActiveModal.STATUS)
            }
            GameDetailOption.TOGGLE_FAVORITE -> vm.toggleFavorite()
            GameDetailOption.CHANGE_EMULATOR -> {
                lifecycleLaunch {
                    val detector = com.nendo.argosy.data.emulator.EmulatorDetector(
                        context
                    )
                    detector.detectEmulators()
                    val emulators = detector.getInstalledForPlatform(
                        vm.uiState.value.platformSlug
                    )
                    vm.openEmulatorPicker(emulators)
                    onBroadcastEmulatorModalOpen(emulators, vm.uiState.value.emulatorName)
                }
            }
            GameDetailOption.ADD_TO_COLLECTION -> {
                vm.openCollectionModal()
                lifecycleLaunch {
                    kotlinx.coroutines.delay(50)
                    onBroadcastCollectionModalOpen(vm)
                }
            }
            GameDetailOption.UPDATES_DLC -> onBroadcastDirectAction("UPDATES_DLC", gameId, null)
            GameDetailOption.REFRESH_METADATA -> onBroadcastDirectAction("REFRESH_METADATA", gameId, null)
            GameDetailOption.DELETE -> onBroadcastDirectAction("DELETE", gameId, null)
            GameDetailOption.HIDE -> onBroadcastDirectAction("HIDE", gameId, null)
        }
    }

    private fun handleSaveConfirm(vm: DualGameDetailViewModel) {
        val state = vm.uiState.value
        if (state.saveFocusColumn == SaveFocusColumn.SLOTS) {
            val slot = vm.saveSlots.value.getOrNull(vm.selectedSlotIndex.value) ?: return
            if (slot.isCreateAction) {
                onBroadcastSaveNamePrompt("CREATE_SLOT", null)
            } else {
                vm.setActiveChannel(slot.channelName)
                onBroadcastSaveAction("SAVE_SWITCH_CHANNEL", state.gameId, slot.channelName, null)
            }
        } else {
            val item = vm.saveHistory.value.getOrNull(vm.selectedHistoryIndex.value) ?: return
            val channelName = vm.focusedSlotChannelName
            vm.setActiveRestorePoint(channelName, item.timestamp)
            onBroadcastSaveAction("SAVE_SET_RESTORE_POINT", state.gameId, channelName, item.timestamp)
        }
    }

    private fun handleSaveLockAsSlot(vm: DualGameDetailViewModel) {
        val state = vm.uiState.value
        if (state.saveFocusColumn != SaveFocusColumn.HISTORY) return
        val item = vm.saveHistory.value.getOrNull(vm.selectedHistoryIndex.value) ?: return
        onBroadcastSaveNamePrompt("LOCK_AS_SLOT", item.cacheId)
    }
}
