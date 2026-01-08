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

data class UpdateFileUi(
    val fileName: String,
    val filePath: String,
    val sizeBytes: Long,
    val type: UpdateFileType = UpdateFileType.UPDATE,
    val isDownloaded: Boolean = true,
    val gameFileId: Long? = null,
    val rommFileId: Long? = null,
    val romId: Long? = null
)

enum class UpdateFileType {
    UPDATE, DLC
}

data class CollectionItemUi(
    val id: Long,
    val name: String,
    val isInCollection: Boolean
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
    val platformId: Long,
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
    val steamLauncherName: String? = null,
    val isAndroidApp: Boolean = false,
    val packageName: String? = null,
    val isHidden: Boolean = false
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
    EXTRACTING,
    PAUSED,
    DOWNLOADED,
    NEEDS_INSTALL
}

enum class RatingType { OPINION, DIFFICULTY }

sealed class MoreOptionAction {
    data object ManageSaves : MoreOptionAction()
    data object RateGame : MoreOptionAction()
    data object SetDifficulty : MoreOptionAction()
    data object SetStatus : MoreOptionAction()
    data object ChangeEmulator : MoreOptionAction()
    data object ChangeSteamLauncher : MoreOptionAction()
    data object ChangeCore : MoreOptionAction()
    data object SelectDisc : MoreOptionAction()
    data object UpdatesDlc : MoreOptionAction()
    data object RefreshData : MoreOptionAction()
    data object AddToCollection : MoreOptionAction()
    data object Delete : MoreOptionAction()
    data object ToggleHide : MoreOptionAction()
}

data class ExtractionFailedInfo(
    val gameId: Long,
    val fileName: String,
    val errorReason: String
)

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
    val showMissingDiscPrompt: Boolean = false,
    val missingDiscNumbers: List<Int> = emptyList(),
    val updateFiles: List<UpdateFileUi> = emptyList(),
    val dlcFiles: List<UpdateFileUi> = emptyList(),
    val showUpdatesPicker: Boolean = false,
    val updatesPickerFocusIndex: Int = 0,
    val syncProgress: SyncProgress = SyncProgress.Idle,
    @Deprecated("Use syncProgress instead")
    val syncState: SyncState = SyncState.Idle,
    val isSyncing: Boolean = false,
    val saveChannel: SaveChannelState = SaveChannelState(),
    val saveStatusInfo: SaveStatusInfo? = null,
    val showPermissionModal: Boolean = false,
    val focusedScreenshotIndex: Int = 0,
    val showScreenshotViewer: Boolean = false,
    val viewerScreenshotIndex: Int = 0,
    val repairedBackgroundPath: String? = null,
    val showExtractionFailedPrompt: Boolean = false,
    val extractionFailedInfo: ExtractionFailedInfo? = null,
    val extractionPromptFocusIndex: Int = 0,
    val showAddToCollectionModal: Boolean = false,
    val collections: List<CollectionItemUi> = emptyList(),
    val collectionModalFocusIndex: Int = 0,
    val showCreateCollectionDialog: Boolean = false
) {
    val hasPreviousGame: Boolean get() = currentGameIndex > 0
    val hasNextGame: Boolean get() = currentGameIndex >= 0 && currentGameIndex < siblingGameIds.size - 1

    // Convenience accessors for backward compatibility
    val showSaveCacheDialog: Boolean get() = saveChannel.isVisible
    val showRenameDialog: Boolean get() = saveChannel.showRenameDialog
}
