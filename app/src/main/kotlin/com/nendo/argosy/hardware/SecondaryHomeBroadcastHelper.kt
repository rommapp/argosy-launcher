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

    fun broadcastCoreModalOpen(
        coreNames: List<String>,
        currentName: String?
    ) {
        dsm.openCoreModal(coreNames, currentName)
    }

    fun broadcastSavePathModalOpen(overridePath: String?) {
        dsm.openSavePathModal(overridePath)
    }

    fun broadcastDisplayTargetModalOpen(
        names: List<String>,
        currentName: String?,
        inheritedName: String?
    ) {
        dsm.openDisplayTargetModal(names, currentName, inheritedName)
    }

    fun broadcastMemoryCardModalOpen(
        names: List<String>,
        currentName: String?,
        inheritedName: String?
    ) {
        dsm.openMemoryCardModal(names, currentName, inheritedName)
    }

    fun broadcastVariantModalOpen(
        variantNames: List<String>,
        currentName: String?
    ) {
        dsm.openVariantModal(variantNames, currentName)
    }

    fun broadcastCollectionModalOpen(vm: DualGameDetailViewModel) {
        val items = vm.collectionItems.value
        dsm.openCollectionModal(
            items.map { it.id },
            items.map { it.name },
            items.map { it.isInCollection }
        )
    }

    fun broadcastSteamInstallModalOpen(vm: DualGameDetailViewModel) {
        val options = vm.steamInstallOptions.value
        dsm.openSteamInstallModal(
            options.map { it.displayName },
            options.map { it.launcherPackage }
        )
    }

    fun openSteamChooserForHome(gameId: Long) {
        dsm.openSteamChooserForHome(gameId)
    }

    fun broadcastDiscModalOpen(discs: List<com.nendo.argosy.data.emulator.DiscOption>) {
        dsm.openDiscModal(discs)
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
                totalPlaytimeMinutes = item.totalPlaytimeMinutes,
                installedCount = item.installedCount,
                achievementsEarned = item.achievementsEarned,
                achievementsTotal = item.achievementsTotal
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

    /**
     * The showcase follows whatever the lower screen has under its cursor, which is not always the
     * carousel's selection: a curated grid tracks a cell instead, so the focused tile is the thing
     * to send when that layout is showing.
     */
    fun broadcastCurrentGameSelection() {
        val state = dualHomeViewModel.uiState.value
        if (state.layoutKind == com.nendo.argosy.domain.model.HomeLayoutKind.CUSTOM_GRID) {
            val target = dualHomeViewModel.focusedTile()?.target
            if (target is com.nendo.argosy.domain.model.HomeTileTargetRef.Collection) {
                broadcastTileCollection(target.collectionId)
                return
            }
        }
        val game = if (state.layoutKind == com.nendo.argosy.domain.model.HomeLayoutKind.CUSTOM_GRID) {
            dualHomeViewModel.focusedTileGameId()?.let { state.tileGames[it] }
        } else {
            state.selectedGame
        }
        if (game != null) dsm.onGameSelected(game.toShowcaseState())
        else dsm.onGameSelected(com.nendo.argosy.ui.dualscreen.home.DualHomeShowcaseState())
    }

    /**
     * Shows the summary for a collection sitting under the grid cursor. Built on demand because a
     * curated tile can point at a collection the collections list has never loaded.
     */
    fun broadcastTileCollection(collectionId: Long) {
        dualHomeViewModel.loadCollectionShowcase(collectionId) { dsm.onCollectionFocused(it) }
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

    fun selectDualCover(index: Int) {
        dsm.selectDualCover(index)
    }

    fun updateDualCoverPickerQuery(text: String) {
        dsm.updateDualCoverPickerQuery(text)
    }

    fun searchDualCovers() {
        dsm.searchDualCovers()
    }

    fun reviewEditorFocusSection(section: com.nendo.argosy.ui.screens.gamedetail.ReviewEditorSection) {
        dsm.focusDualReviewEditorSection(section)
    }

    fun reviewEditorMoveSection(delta: Int) {
        dsm.moveDualReviewEditorSection(delta)
    }

    fun reviewEditorAdjust(delta: Int) {
        dsm.adjustDualReviewEditor(delta)
    }

    fun reviewEditorSetVerdict(recommended: Boolean) {
        dsm.setDualReviewVerdict(recommended)
    }

    fun reviewEditorSetVisibility(visibility: String) {
        dsm.setDualReviewVisibility(visibility)
    }

    fun reviewEditorSetBody(text: String) {
        dsm.setDualReviewEditorBody(text)
    }

    fun reviewEditorConfirm() {
        dsm.confirmDualReviewEditor()
    }

    fun reviewEditorSubmit() {
        dsm.submitDualReview()
    }

    fun reviewEditorPromptDelete() {
        dsm.promptDualReviewDelete()
    }

    fun reviewEditorConfirmDelete() {
        dsm.confirmDualReviewDelete()
    }

    fun reviewEditorDiscard() {
        dsm.discardDualReviewEditor()
    }

    fun reviewEditorDismissConfirm() {
        dsm.dismissDualReviewConfirm()
    }

    fun reviewEditorBack() {
        dsm.backDualReviewEditor()
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
