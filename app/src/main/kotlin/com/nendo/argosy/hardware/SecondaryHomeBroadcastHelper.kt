package com.nendo.argosy.hardware

import android.content.Context
import android.content.Intent
import com.nendo.argosy.data.emulator.InstalledEmulator
import com.nendo.argosy.ui.dualscreen.DualScreenBroadcasts
import com.nendo.argosy.ui.dualscreen.gamedetail.ActiveModal
import com.nendo.argosy.ui.dualscreen.gamedetail.DualGameDetailViewModel
import com.nendo.argosy.ui.dualscreen.home.DualHomeFocusZone
import com.nendo.argosy.ui.dualscreen.home.DualHomeViewModel
import com.nendo.argosy.ui.screens.gamedetail.UpdateFileUi

class SecondaryHomeBroadcastHelper(
    private val context: Context,
    private val dualHomeViewModel: DualHomeViewModel,
    private val secondaryHomeViewModel: () -> com.nendo.argosy.ui.screens.secondaryhome.SecondaryHomeViewModel
) {

    fun broadcastGameDetailOpened(gameId: Long) {
        context.sendBroadcast(
            Intent(DualScreenBroadcasts.ACTION_GAME_DETAIL_OPENED).apply {
                setPackage(context.packageName)
                putExtra(DualScreenBroadcasts.EXTRA_GAME_ID, gameId)
            }
        )
    }

    fun broadcastGameDetailClosed() {
        context.sendBroadcast(
            Intent(DualScreenBroadcasts.ACTION_GAME_DETAIL_CLOSED)
                .setPackage(context.packageName)
        )
    }

    fun broadcastScreenshotSelected(index: Int) {
        context.sendBroadcast(
            Intent(DualScreenBroadcasts.ACTION_SCREENSHOT_SELECTED).apply {
                setPackage(context.packageName)
                putExtra(DualScreenBroadcasts.EXTRA_SCREENSHOT_INDEX, index)
            }
        )
    }

    fun broadcastScreenshotCleared() {
        context.sendBroadcast(
            Intent(DualScreenBroadcasts.ACTION_SCREENSHOT_CLEAR)
                .setPackage(context.packageName)
        )
    }

    fun broadcastModalState(
        vm: DualGameDetailViewModel,
        modal: ActiveModal
    ) {
        context.sendBroadcast(
            Intent(DualScreenBroadcasts.ACTION_MODAL_OPEN).apply {
                setPackage(context.packageName)
                putExtra(DualScreenBroadcasts.EXTRA_MODAL_TYPE, modal.name)
                putExtra(
                    DualScreenBroadcasts.EXTRA_MODAL_VALUE,
                    vm.ratingPickerValue.value
                )
                putExtra(
                    DualScreenBroadcasts.EXTRA_MODAL_STATUS_SELECTED,
                    vm.statusPickerValue.value
                )
                putExtra(
                    DualScreenBroadcasts.EXTRA_MODAL_STATUS_CURRENT,
                    vm.uiState.value.status
                )
            }
        )
    }

    fun broadcastModalClose() {
        context.sendBroadcast(
            Intent(DualScreenBroadcasts.ACTION_MODAL_RESULT).apply {
                setPackage(context.packageName)
                putExtra(DualScreenBroadcasts.EXTRA_MODAL_DISMISSED, true)
            }
        )
    }

    fun broadcastEmulatorModalOpen(
        emulators: List<InstalledEmulator>,
        currentName: String?
    ) {
        context.sendBroadcast(
            Intent(DualScreenBroadcasts.ACTION_MODAL_OPEN).apply {
                setPackage(context.packageName)
                putExtra(
                    DualScreenBroadcasts.EXTRA_MODAL_TYPE,
                    ActiveModal.EMULATOR.name
                )
                putStringArrayListExtra(
                    DualScreenBroadcasts.EXTRA_EMULATOR_NAMES,
                    ArrayList(emulators.map { it.def.displayName })
                )
                putStringArrayListExtra(
                    DualScreenBroadcasts.EXTRA_EMULATOR_VERSIONS,
                    ArrayList(emulators.map { it.versionName ?: "" })
                )
                putExtra(
                    DualScreenBroadcasts.EXTRA_EMULATOR_CURRENT,
                    currentName
                )
            }
        )
    }

    fun broadcastCollectionModalOpen(vm: DualGameDetailViewModel) {
        val items = vm.collectionItems.value
        context.sendBroadcast(
            Intent(DualScreenBroadcasts.ACTION_MODAL_OPEN).apply {
                setPackage(context.packageName)
                putExtra(
                    DualScreenBroadcasts.EXTRA_MODAL_TYPE,
                    ActiveModal.COLLECTION.name
                )
                putExtra(
                    DualScreenBroadcasts.EXTRA_COLLECTION_IDS,
                    items.map { it.id }.toLongArray()
                )
                putStringArrayListExtra(
                    DualScreenBroadcasts.EXTRA_COLLECTION_NAMES,
                    ArrayList(items.map { it.name })
                )
                putExtra(
                    DualScreenBroadcasts.EXTRA_COLLECTION_CHECKED,
                    items.map { it.isInCollection }.toBooleanArray()
                )
            }
        )
    }

    fun broadcastUpdatesModalOpen(vm: DualGameDetailViewModel) {
        val updates = vm.updateFiles.value
        val dlc = vm.dlcFiles.value
        val allFiles = updates + dlc
        context.sendBroadcast(
            Intent(DualScreenBroadcasts.ACTION_MODAL_OPEN).apply {
                setPackage(context.packageName)
                putExtra(
                    DualScreenBroadcasts.EXTRA_MODAL_TYPE,
                    ActiveModal.UPDATES_DLC.name
                )
                putStringArrayListExtra(
                    DualScreenBroadcasts.EXTRA_UPDATE_FILE_NAMES,
                    ArrayList(allFiles.map { it.fileName })
                )
                putExtra(
                    DualScreenBroadcasts.EXTRA_UPDATE_FILE_SIZES,
                    allFiles.map { it.sizeBytes }.toLongArray()
                )
                putStringArrayListExtra(
                    DualScreenBroadcasts.EXTRA_UPDATE_FILE_TYPES,
                    ArrayList(allFiles.map { it.type.name })
                )
                putExtra(
                    DualScreenBroadcasts.EXTRA_UPDATE_FILE_DOWNLOADED,
                    allFiles.map { it.isDownloaded }.toBooleanArray()
                )
                putExtra(
                    DualScreenBroadcasts.EXTRA_UPDATE_FILE_GAME_FILE_IDS,
                    allFiles.map { it.gameFileId ?: -1L }.toLongArray()
                )
            }
        )
    }

    fun broadcastViewModeChange(drawerOpen: Boolean? = null) {
        val state = dualHomeViewModel.uiState.value
        context.sendBroadcast(
            Intent(DualScreenBroadcasts.ACTION_VIEW_MODE_CHANGED).apply {
                setPackage(context.packageName)
                putExtra(DualScreenBroadcasts.EXTRA_VIEW_MODE, state.viewMode.name)
                putExtra(
                    DualScreenBroadcasts.EXTRA_IS_APP_BAR_FOCUSED,
                    state.focusZone == DualHomeFocusZone.APP_BAR
                )
                putExtra(
                    DualScreenBroadcasts.EXTRA_IS_DRAWER_OPEN,
                    drawerOpen ?: secondaryHomeViewModel().uiState.value.isDrawerOpen
                )
            }
        )
    }

    fun broadcastCollectionFocused() {
        val item = dualHomeViewModel.selectedCollectionItem() ?: return
        context.sendBroadcast(
            Intent(DualScreenBroadcasts.ACTION_COLLECTION_FOCUSED).apply {
                setPackage(context.packageName)
                putExtra(DualScreenBroadcasts.EXTRA_COLLECTION_NAME_DISPLAY, item.name)
                putExtra(DualScreenBroadcasts.EXTRA_COLLECTION_DESCRIPTION, item.description)
                putStringArrayListExtra(
                    DualScreenBroadcasts.EXTRA_COLLECTION_COVER_PATHS,
                    ArrayList(item.coverPaths)
                )
                putExtra(DualScreenBroadcasts.EXTRA_COLLECTION_GAME_COUNT, item.gameCount)
                putExtra(DualScreenBroadcasts.EXTRA_COLLECTION_PLATFORM_SUMMARY, item.platformSummary)
                putExtra(DualScreenBroadcasts.EXTRA_COLLECTION_TOTAL_PLAYTIME, item.totalPlaytimeMinutes)
            }
        )
    }

    fun broadcastLibraryGameSelection() {
        val state = dualHomeViewModel.uiState.value
        val game = state.libraryGames.getOrNull(state.libraryFocusedIndex) ?: return
        com.nendo.argosy.ui.dualscreen.home.broadcastGameSelection(context, game)
    }

    fun broadcastCollectionGameSelection() {
        val game = dualHomeViewModel.focusedCollectionGame() ?: return
        com.nendo.argosy.ui.dualscreen.home.broadcastGameSelection(context, game)
    }

    fun broadcastCurrentGameSelection() {
        val state = dualHomeViewModel.uiState.value
        val game = state.selectedGame ?: return
        com.nendo.argosy.ui.dualscreen.home.broadcastGameSelection(context, game)
    }

    fun broadcastDirectAction(type: String, gameId: Long, channelName: String? = null) {
        context.sendBroadcast(
            Intent(DualScreenBroadcasts.ACTION_DIRECT_ACTION).apply {
                setPackage(context.packageName)
                putExtra(DualScreenBroadcasts.EXTRA_ACTION_TYPE, type)
                putExtra(DualScreenBroadcasts.EXTRA_GAME_ID, gameId)
                channelName?.let { putExtra(DualScreenBroadcasts.EXTRA_CHANNEL_NAME, it) }
            }
        )
    }

    fun broadcastInlineUpdate(field: String, value: Int) {
        context.sendBroadcast(
            Intent(DualScreenBroadcasts.ACTION_INLINE_UPDATE).apply {
                setPackage(context.packageName)
                putExtra(DualScreenBroadcasts.EXTRA_INLINE_FIELD, field)
                putExtra(DualScreenBroadcasts.EXTRA_INLINE_INT_VALUE, value)
            }
        )
    }

    fun broadcastInlineUpdate(field: String, value: String?) {
        context.sendBroadcast(
            Intent(DualScreenBroadcasts.ACTION_INLINE_UPDATE).apply {
                setPackage(context.packageName)
                putExtra(DualScreenBroadcasts.EXTRA_INLINE_FIELD, field)
                putExtra(DualScreenBroadcasts.EXTRA_INLINE_STRING_VALUE, value)
            }
        )
    }

    fun broadcastModalConfirmResult(
        modal: ActiveModal,
        ratingValue: Int,
        statusValue: String?
    ) {
        context.sendBroadcast(
            Intent(DualScreenBroadcasts.ACTION_MODAL_RESULT).apply {
                setPackage(context.packageName)
                putExtra(DualScreenBroadcasts.EXTRA_MODAL_TYPE, modal.name)
                putExtra(DualScreenBroadcasts.EXTRA_MODAL_VALUE, ratingValue)
                putExtra(DualScreenBroadcasts.EXTRA_MODAL_STATUS_SELECTED, statusValue)
            }
        )
    }

    fun broadcastSaveAction(
        type: String,
        gameId: Long,
        channelName: String? = null,
        timestamp: Long? = null
    ) {
        context.sendBroadcast(
            Intent(DualScreenBroadcasts.ACTION_DIRECT_ACTION).apply {
                setPackage(context.packageName)
                putExtra(DualScreenBroadcasts.EXTRA_ACTION_TYPE, type)
                putExtra(DualScreenBroadcasts.EXTRA_GAME_ID, gameId)
                channelName?.let { putExtra(DualScreenBroadcasts.EXTRA_CHANNEL_NAME, it) }
                timestamp?.let { putExtra(DualScreenBroadcasts.EXTRA_SAVE_TIMESTAMP, it) }
            }
        )
    }

    fun broadcastSaveNamePrompt(actionType: String, cacheId: Long?) {
        context.sendBroadcast(
            Intent(DualScreenBroadcasts.ACTION_MODAL_OPEN).apply {
                setPackage(context.packageName)
                putExtra(DualScreenBroadcasts.EXTRA_MODAL_TYPE, ActiveModal.SAVE_NAME.name)
                putExtra(DualScreenBroadcasts.EXTRA_ACTION_TYPE, actionType)
                if (cacheId != null) {
                    putExtra(DualScreenBroadcasts.EXTRA_SAVE_CACHE_ID, cacheId)
                }
            }
        )
    }

    fun broadcastOpenOverlay(eventName: String) {
        dualHomeViewModel.startDrawerForwarding()
        context.sendBroadcast(
            Intent(DualScreenBroadcasts.ACTION_OPEN_OVERLAY).apply {
                setPackage(context.packageName)
                putExtra(DualScreenBroadcasts.EXTRA_EVENT_NAME, eventName)
            }
        )
    }

    fun broadcastRefocusUpper() {
        context.sendBroadcast(
            Intent(DualScreenBroadcasts.ACTION_REFOCUS_UPPER)
                .setPackage(context.packageName)
        )
    }

    fun broadcastCompanionResumed() {
        context.sendBroadcast(
            Intent(DualScreenBroadcasts.ACTION_COMPANION_RESUMED)
                .setPackage(context.packageName)
        )
    }

    fun broadcastCompanionPaused() {
        context.sendBroadcast(
            Intent(DualScreenBroadcasts.ACTION_COMPANION_PAUSED)
                .setPackage(context.packageName)
        )
    }
}
