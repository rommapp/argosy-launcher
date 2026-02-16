package com.nendo.argosy.ui.dualscreen.gamedetail

import com.nendo.argosy.ui.common.savechannel.SaveFocusColumn
import com.nendo.argosy.ui.input.GamepadEvent
import com.nendo.argosy.ui.input.InputResult

class DualGameDetailInputHandler(
    private val context: android.content.Context,
    private val viewModel: () -> DualGameDetailViewModel?,
    private val isScreenshotViewerOpen: () -> Boolean,
    private val onBroadcastScreenshotSelected: (Int) -> Unit,
    private val onBroadcastScreenshotCleared: () -> Unit,
    private val onBroadcastModalState: (DualGameDetailViewModel, ActiveModal) -> Unit,
    private val onBroadcastModalClose: () -> Unit,
    private val onBroadcastInlineUpdate: (String, Any) -> Unit,
    private val onBroadcastDirectAction: (String, Long, String?) -> Unit,
    private val onBroadcastEmulatorModalOpen: (List<com.nendo.argosy.data.emulator.InstalledEmulator>, String?) -> Unit,
    private val onBroadcastCollectionModalOpen: (DualGameDetailViewModel) -> Unit,
    private val onBroadcastSaveNamePrompt: (String, Long?) -> Unit,
    private val onBroadcastSaveAction: (String, Long, String?, Long?) -> Unit,
    private val onReturnToHome: () -> Unit,
    private val onRefocusSelf: () -> Unit,
    private val lifecycleLaunch: (suspend () -> Unit) -> Unit
) {

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
                        if (idx >= 0) onBroadcastScreenshotSelected(idx)
                    }
                    DualGameDetailTab.OPTIONS -> handleOptionAction(vm)
                }
                InputResult.HANDLED
            }
            GamepadEvent.Back -> {
                if (isScreenshotViewerOpen()) {
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
            ActiveModal.EMULATOR, ActiveModal.COLLECTION -> {
                if (event == GamepadEvent.Back) {
                    when (modal) {
                        ActiveModal.EMULATOR -> vm.dismissPicker()
                        ActiveModal.COLLECTION -> vm.dismissCollectionModal()
                        else -> {}
                    }
                    onBroadcastModalClose()
                }
                return InputResult.HANDLED
            }
            else -> {}
        }

        return when (event) {
            GamepadEvent.Confirm -> {
                val ratingValue = vm.ratingPickerValue.value
                val statusValue = vm.statusPickerValue.value
                vm.confirmPicker()
                onBroadcastInlineUpdate("modal_confirm", "$ratingValue|$statusValue|${modal.name}")
                InputResult.HANDLED
            }
            GamepadEvent.Back -> {
                vm.dismissPicker()
                onBroadcastModalClose()
                InputResult.HANDLED
            }
            else -> InputResult.HANDLED
        }
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
