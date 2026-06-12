package com.nendo.argosy.ui.screens.gamedetail.delegates

import com.nendo.argosy.ui.input.SoundFeedbackManager
import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.ui.screens.gamedetail.GameDownloadStatus
import com.nendo.argosy.ui.screens.gamedetail.MoreOptionAction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class MoreOptionsState(
    val showMoreOptions: Boolean = false,
    val moreOptionsFocusIndex: Int = 0
)

class MoreOptionsDelegate @Inject constructor(
    private val soundManager: SoundFeedbackManager
) {
    private val _state = MutableStateFlow(MoreOptionsState())
    val state: StateFlow<MoreOptionsState> = _state.asStateFlow()

    fun reset() {
        _state.value = MoreOptionsState()
    }

    fun toggleMoreOptions() {
        val wasShowing = _state.value.showMoreOptions
        _state.update {
            it.copy(
                showMoreOptions = !it.showMoreOptions,
                moreOptionsFocusIndex = 0
            )
        }
        if (!wasShowing) {
            soundManager.play(SoundType.OPEN_MODAL)
        } else {
            soundManager.play(SoundType.CLOSE_MODAL)
        }
    }

    fun moveOptionsFocus(
        delta: Int,
        downloadStatus: GameDownloadStatus,
        isRommGame: Boolean,
        isAndroidApp: Boolean,
        canManageSaves: Boolean,
        hasMultipleCores: Boolean,
        isMultiDisc: Boolean,
        hasVariants: Boolean,
        isSteamGame: Boolean,
        hasUpdates: Boolean,
        platformSlug: String?
    ) {
        val canTrackProgress = isRommGame || isAndroidApp
        val isEmulatedGame = !isSteamGame && !isAndroidApp
        val isDownloaded = downloadStatus == GameDownloadStatus.DOWNLOADED
        val usesTitleId = platformSlug in TITLE_ID_PLATFORMS

        var optionCount = 2
        if (canManageSaves) optionCount++
        if (canTrackProgress) optionCount += 2
        if (isSteamGame || isEmulatedGame) optionCount++
        if (hasMultipleCores && isEmulatedGame) optionCount++
        if (isEmulatedGame) optionCount++
        if (usesTitleId && isEmulatedGame) optionCount++
        if (isMultiDisc) optionCount++
        if (hasVariants && isEmulatedGame) optionCount++
        if (hasUpdates) optionCount++
        if (isDownloaded || isAndroidApp) optionCount++

        val maxIndex = optionCount - 1
        _state.update { state ->
            val newIndex = (state.moreOptionsFocusIndex + delta).coerceIn(0, maxIndex)
            state.copy(moreOptionsFocusIndex = newIndex)
        }
    }

    fun resolveOptionAction(
        downloadStatus: GameDownloadStatus,
        isRommGame: Boolean,
        isAndroidApp: Boolean,
        canManageSaves: Boolean,
        hasMultipleCores: Boolean,
        isMultiDisc: Boolean,
        hasVariants: Boolean,
        isSteamGame: Boolean,
        hasUpdates: Boolean,
        platformSlug: String?
    ): MoreOptionAction? {
        val canTrackProgress = isRommGame || isAndroidApp
        val isEmulatedGame = !isSteamGame && !isAndroidApp
        val isDownloaded = downloadStatus == GameDownloadStatus.DOWNLOADED
        val usesTitleId = platformSlug in TITLE_ID_PLATFORMS
        val index = _state.value.moreOptionsFocusIndex

        var currentIdx = 0
        val saveCacheIdx = if (canManageSaves) currentIdx++ else -1
        val ratingsStatusIdx = if (canTrackProgress) currentIdx++ else -1
        val emulatorOrLauncherIdx = if (isSteamGame || isEmulatedGame) currentIdx++ else -1
        val coreIdx = if (hasMultipleCores && isEmulatedGame) currentIdx++ else -1
        val platformSettingsIdx = if (isEmulatedGame) currentIdx++ else -1
        val titleIdIdx = if (usesTitleId && isEmulatedGame) currentIdx++ else -1
        val discIdx = if (isMultiDisc) currentIdx++ else -1
        val variantIdx = if (hasVariants && isEmulatedGame) currentIdx++ else -1
        val updatesIdx = if (hasUpdates) currentIdx++ else -1
        val refreshIdx = if (canTrackProgress) currentIdx++ else -1
        val addToCollectionIdx = currentIdx++
        val deleteIdx = if (isDownloaded || isAndroidApp) currentIdx++ else -1
        val hideIdx = currentIdx

        return when (index) {
            saveCacheIdx -> MoreOptionAction.ManageSaves
            ratingsStatusIdx -> MoreOptionAction.RatingsStatus
            emulatorOrLauncherIdx -> if (isSteamGame) MoreOptionAction.ChangeSteamLauncher else MoreOptionAction.ChangeEmulator
            coreIdx -> MoreOptionAction.ChangeCore
            platformSettingsIdx -> MoreOptionAction.PlatformSettings
            titleIdIdx -> MoreOptionAction.RefreshTitleId
            discIdx -> MoreOptionAction.SelectDisc
            variantIdx -> MoreOptionAction.SelectVariant
            updatesIdx -> MoreOptionAction.UpdatesDlc
            refreshIdx -> MoreOptionAction.RefreshData
            addToCollectionIdx -> MoreOptionAction.AddToCollection
            deleteIdx -> MoreOptionAction.Delete
            hideIdx -> MoreOptionAction.ToggleHide
            else -> null
        }
    }

    companion object {
        val TITLE_ID_PLATFORMS = setOf("switch", "wiiu", "3ds", "vita", "psvita", "psp", "wii", "ps2")
    }
}
