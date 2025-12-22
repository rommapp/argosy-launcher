package com.nendo.argosy.ui.screens.gamedetail

import android.content.Intent
import com.nendo.argosy.data.emulator.InstalledEmulator
import com.nendo.argosy.data.emulator.RetroArchCore
import com.nendo.argosy.data.launcher.SteamLauncher
import com.nendo.argosy.domain.model.SyncProgress
import com.nendo.argosy.domain.model.SyncState
import com.nendo.argosy.ui.common.savechannel.SaveChannelState
import com.nendo.argosy.ui.screens.gamedetail.components.SaveStatusInfo

data class ScreenshotPair(
    val remoteUrl: String,
    val cachedPath: String?
)

data class DiscUi(
    val id: Long,
    val discNumber: Int,
    val fileName: String,
    val isDownloaded: Boolean,
    val isLastPlayed: Boolean
)

data class AchievementUi(
    val raId: Long,
    val title: String,
    val description: String?,
    val points: Int,
    val type: String?,
    val badgeUrl: String?,
    val isUnlocked: Boolean = false
)

data class GameDetailUi(
    val id: Long,
    val title: String,
    val platformId: String,
    val platformSlug: String,
    val platformName: String,
    val coverPath: String?,
    val backgroundPath: String?,
    val developer: String?,
    val publisher: String?,
    val releaseYear: Int?,
    val genre: String?,
    val description: String?,
    val players: String?,
    val rating: Float?,
    val userRating: Int,
    val userDifficulty: Int,
    val completion: Int,
    val status: String?,
    val isRommGame: Boolean,
    val isFavorite: Boolean,
    val playCount: Int,
    val playTimeMinutes: Int,
    val screenshots: List<ScreenshotPair>,
    val achievements: List<AchievementUi> = emptyList(),
    val emulatorName: String?,
    val canPlay: Boolean,
    val isMultiDisc: Boolean = false,
    val lastPlayedDiscId: Long? = null,
    val isRetroArchEmulator: Boolean = false,
    val selectedCoreName: String? = null,
    val canManageSaves: Boolean = false,
    val isSteamGame: Boolean = false,
    val steamLauncherName: String? = null
)

sealed class LaunchEvent {
    data class Launch(val intent: Intent) : LaunchEvent()
    data object NavigateBack : LaunchEvent()
}

enum class GameDownloadStatus {
    NOT_DOWNLOADED,
    QUEUED,
    WAITING_FOR_STORAGE,
    DOWNLOADING,
    PAUSED,
    DOWNLOADED
}

enum class RatingType { OPINION, DIFFICULTY }

data class GameDetailUiState(
    val game: GameDetailUi? = null,
    val showMoreOptions: Boolean = false,
    val moreOptionsFocusIndex: Int = 0,
    val isLoading: Boolean = true,
    val isRefreshingGameData: Boolean = false,
    val downloadStatus: GameDownloadStatus = GameDownloadStatus.NOT_DOWNLOADED,
    val downloadProgress: Float = 0f,
    val showEmulatorPicker: Boolean = false,
    val availableEmulators: List<InstalledEmulator> = emptyList(),
    val emulatorPickerFocusIndex: Int = 0,
    val showCorePicker: Boolean = false,
    val availableCores: List<RetroArchCore> = emptyList(),
    val corePickerFocusIndex: Int = 0,
    val selectedCoreId: String? = null,
    val showSteamLauncherPicker: Boolean = false,
    val availableSteamLaunchers: List<SteamLauncher> = emptyList(),
    val steamLauncherPickerFocusIndex: Int = 0,
    val siblingGameIds: List<Long> = emptyList(),
    val currentGameIndex: Int = -1,
    val showRatingPicker: Boolean = false,
    val ratingPickerType: RatingType = RatingType.OPINION,
    val ratingPickerValue: Int = 0,
    val showStatusPicker: Boolean = false,
    val statusPickerValue: String? = null,
    val discs: List<DiscUi> = emptyList(),
    val showDiscPicker: Boolean = false,
    val discPickerFocusIndex: Int = 0,
    val showMissingDiscPrompt: Boolean = false,
    val missingDiscNumbers: List<Int> = emptyList(),
    val syncProgress: SyncProgress = SyncProgress.Idle,
    @Deprecated("Use syncProgress instead")
    val syncState: SyncState = SyncState.Idle,
    val isSyncing: Boolean = false,
    val saveChannel: SaveChannelState = SaveChannelState(),
    val saveStatusInfo: SaveStatusInfo? = null,
    val showPermissionModal: Boolean = false,
    val focusedScreenshotIndex: Int = 0,
    val showScreenshotViewer: Boolean = false,
    val viewerScreenshotIndex: Int = 0
) {
    val hasPreviousGame: Boolean get() = currentGameIndex > 0
    val hasNextGame: Boolean get() = currentGameIndex >= 0 && currentGameIndex < siblingGameIds.size - 1

    // Convenience accessors for backward compatibility
    val showSaveCacheDialog: Boolean get() = saveChannel.isVisible
    val showRenameDialog: Boolean get() = saveChannel.showRenameDialog
}
