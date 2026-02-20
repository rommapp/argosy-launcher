package com.nendo.argosy.hardware

import com.nendo.argosy.DualScreenManager
import com.nendo.argosy.data.emulator.InstalledEmulator
import com.nendo.argosy.ui.dualscreen.gamedetail.ActiveModal
import com.nendo.argosy.ui.dualscreen.gamedetail.DualGameDetailViewModel
import com.nendo.argosy.ui.dualscreen.home.DualCollectionShowcaseState
import com.nendo.argosy.ui.dualscreen.home.DualHomeFocusZone
import com.nendo.argosy.ui.dualscreen.home.DualHomeViewModel
import com.nendo.argosy.ui.dualscreen.home.toShowcaseState

class SecondaryHomeBroadcastHelper(
    private val dsm: DualScreenManager,
    private val dualHomeViewModel: DualHomeViewModel,
    private val secondaryHomeViewModel: () -> com.nendo.argosy.ui.screens.secondaryhome.SecondaryHomeViewModel
) {

    fun broadcastGameDetailOpened(gameId: Long) {
        dsm.handleGameDetailOpened(gameId)
    }

    fun broadcastGameDetailClosed() {
        dsm.onGameDetailClosed()
    }

    fun broadcastScreenshotSelected(index: Int) {
        dsm.onScreenshotSelected(index)
    }

    fun broadcastScreenshotCleared() {
        dsm.onScreenshotCleared()
    }

    fun broadcastModalState(
        vm: DualGameDetailViewModel,
        modal: ActiveModal
    ) {
        dsm.openModal(
            modal,
            vm.ratingPickerValue.value,
            vm.statusPickerValue.value,
            vm.uiState.value.status
        )
    }

    fun broadcastModalClose() {
        dsm.onModalClose()
    }

    fun broadcastEmulatorModalOpen(
        emulators: List<InstalledEmulator>,
        currentName: String?
    ) {
        dsm.openEmulatorModal(
            emulators.map { it.def.displayName },
            emulators.map { it.versionName ?: "" },
            currentName
        )
    }

    fun broadcastCollectionModalOpen(vm: DualGameDetailViewModel) {
        val items = vm.collectionItems.value
        dsm.openCollectionModal(
            items.map { it.id },
            items.map { it.name },
            items.map { it.isInCollection }
        )
    }

    fun broadcastUpdatesModalOpen(vm: DualGameDetailViewModel) {
        val updates = vm.updateFiles.value
        val dlc = vm.dlcFiles.value
        dsm.openUpdatesModal(updates + dlc)
    }

    fun broadcastViewModeChange(drawerOpen: Boolean? = null) {
        val state = dualHomeViewModel.uiState.value
        dsm.onViewModeChanged(
            state.viewMode.name,
            state.focusZone == DualHomeFocusZone.APP_BAR,
            drawerOpen ?: secondaryHomeViewModel().uiState.value.isDrawerOpen
        )
    }

    fun broadcastCollectionFocused() {
        val item = dualHomeViewModel.selectedCollectionItem() ?: return
        dsm.onCollectionFocused(
            DualCollectionShowcaseState(
                name = item.name,
                description = item.description,
                coverPaths = item.coverPaths,
                gameCount = item.gameCount,
                platformSummary = item.platformSummary,
                totalPlaytimeMinutes = item.totalPlaytimeMinutes
            )
        )
    }

    fun broadcastLibraryGameSelection() {
        val state = dualHomeViewModel.uiState.value
        val game = state.libraryGames.getOrNull(state.libraryFocusedIndex)
            ?: return
        dsm.onGameSelected(game.toShowcaseState())
    }

    fun broadcastCollectionGameSelection() {
        val game = dualHomeViewModel.focusedCollectionGame() ?: return
        dsm.onGameSelected(game.toShowcaseState())
    }

    fun broadcastCurrentGameSelection() {
        val state = dualHomeViewModel.uiState.value
        val game = state.selectedGame ?: return
        dsm.onGameSelected(game.toShowcaseState())
    }

    fun broadcastDirectAction(
        type: String,
        gameId: Long,
        channelName: String? = null
    ) {
        dsm.handleDirectAction(type, gameId, channelName)
    }

    fun broadcastInlineUpdate(field: String, value: Int) {
        dsm.handleInlineUpdate(field, intValue = value)
    }

    fun broadcastInlineUpdate(field: String, value: String?) {
        dsm.handleInlineUpdate(field, stringValue = value)
    }

    fun broadcastModalConfirmResult(
        modal: ActiveModal,
        ratingValue: Int,
        statusValue: String?
    ) {
        dsm.onModalConfirmResult(modal, ratingValue, statusValue)
    }

    fun broadcastSaveAction(
        type: String,
        gameId: Long,
        channelName: String? = null,
        timestamp: Long? = null
    ) {
        dsm.handleDirectAction(type, gameId, channelName, timestamp)
    }

    fun broadcastSaveNamePrompt(actionType: String, cacheId: Long?) {
        dsm.openSaveNameModal(actionType, cacheId)
    }

    fun broadcastOpenOverlay(eventName: String) {
        dualHomeViewModel.startDrawerForwarding()
        dsm.onOpenOverlayFromCompanion(eventName)
    }

    fun broadcastRefocusUpper() {
        dsm.onRefocusUpper()
    }

    fun broadcastCompanionResumed() {
        dsm.onCompanionResumed()
    }

    fun broadcastCompanionPaused() {
        dsm.onCompanionPaused()
    }
}
