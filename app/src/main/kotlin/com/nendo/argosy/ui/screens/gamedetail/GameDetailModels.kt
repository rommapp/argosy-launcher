package com.nendo.argosy.ui.screens.gamedetail

import android.content.Intent
import androidx.annotation.StringRes
import com.nendo.argosy.core.game.AchievementUi
import com.nendo.argosy.data.model.ArtSlot
import com.nendo.argosy.domain.model.SyncProgress
import com.nendo.argosy.domain.model.SyncState
import com.nendo.argosy.ui.common.savechannel.SaveChannelState
import com.nendo.argosy.ui.screens.common.SyncOverlayState
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

object UpdateFileVersionSort {
    private val VERSION_PATTERN = Regex("""[vV]?(\d+(?:\.\d+)*)""")

    fun parseVersion(fileName: String): List<Int> {
        val match = VERSION_PATTERN.findAll(fileName)
            .maxByOrNull { it.groupValues[1].length }
            ?: return listOf(0)
        return match.groupValues[1].split(".").map { it.toIntOrNull() ?: 0 }
    }

    val LATEST_FIRST = Comparator<UpdateFileUi> { a, b ->
        val va = parseVersion(a.fileName)
        val vb = parseVersion(b.fileName)
        val maxLen = maxOf(va.size, vb.size)
        for (i in 0 until maxLen) {
            val pa = va.getOrElse(i) { 0 }
            val pb = vb.getOrElse(i) { 0 }
            if (pa != pb) return@Comparator pb - pa
        }
        0
    }
}

data class CollectionItemUi(
    val id: Long,
    val name: String,
    val isInCollection: Boolean
)

data class GameDetailUi(
    val id: Long,
    val title: String,
    val platformId: Long,
    val platformSlug: String,
    val platformName: String,
    val coverPath: String?,
    val overriddenArtSlots: Set<ArtSlot> = emptySet(),
    val backgroundPath: String?,
    val boxBackPath: String? = null,
    val boxSpinePath: String? = null,
    val developer: String?,
    val publisher: String?,
    val releaseYear: Int?,
    val genre: String?,
    val description: String?,
    val players: String?,
    val rating: Float?,
    val timeToBeatMainSec: Int? = null,
    val timeToBeatExtraSec: Int? = null,
    val timeToBeatCompletionistSec: Int? = null,
    val userRating: Int,
    val userDifficulty: Int,
    val completion: Int,
    val status: String?,
    val isRommGame: Boolean,
    val isRemovedFromRomm: Boolean = false,
    val canRemoveFromLibrary: Boolean = false,
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
    val isBuiltInEmulator: Boolean = false,
    val hasMultipleCores: Boolean = false,
    val selectedCoreName: String? = null,
    val canManageSaves: Boolean = false,
    val canManageStates: Boolean = false,
    val isSteamGame: Boolean = false,
    /**
     * Display label of the resolved Steam launcher, never compared.
     * Launcher identity is [steamLauncherPackage].
     */
    val steamLauncherName: String? = null,
    val steamLauncherPackage: String? = null,
    val steamLauncherSetManually: Boolean = false,
    val isExternallyManaged: Boolean = false,
    val managingLauncherDisplayName: String? = null,
    val isAndroidApp: Boolean = false,
    val packageName: String? = null,
    val isHidden: Boolean = false,
    val titleId: String? = null,
    val igdbId: Long? = null,
    val steamAppId: Long? = null,
    val rommFileName: String? = null
)

sealed class LaunchEvent {
    data class LaunchIntent(val intent: Intent) : LaunchEvent()
    data object NavigateBack : LaunchEvent()
    data class OpenSaveTimeline(val gameId: Long) : LaunchEvent()
}

enum class GameDownloadStatus {
    NO_FILE,
    NOT_DOWNLOADED,
    QUEUED,
    WAITING_FOR_STORAGE,
    DOWNLOADING,
    EXTRACTING,
    PAUSED,
    FAILED,
    DOWNLOADED,
    NEEDS_INSTALL
}

enum class RatingType(val maxValue: Int, val step: Int) {
    OPINION(maxValue = 10, step = 1),
    DIFFICULTY(maxValue = 10, step = 1),
    PROGRESS(maxValue = 100, step = 5);

    fun clamp(value: Int): Int = value.coerceIn(0, maxValue)

    /**
     * The next value one step in [direction] (negative lowers, positive raises), landing on a
     * multiple of [step] so an off-grid value snaps to the grid first. Clamped to 0..[maxValue].
     */
    fun stepFrom(current: Int, direction: Int): Int {
        val base = clamp(current)
        val next = when {
            direction > 0 -> (base / step + 1) * step
            direction < 0 -> if (base % step == 0) base - step else base / step * step
            else -> base
        }
        return clamp(next)
    }
}

/**
 * A picture offered in the artwork picker. [source] is either a remote url or an absolute local
 * path; [thumbUrl] is what the tile shows when it differs.
 */
data class ArtCandidate(
    val source: String,
    val thumbUrl: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    @StringRes val originRes: Int? = null,
    val isRevert: Boolean = false
) {
    val dimensionLabel: String?
        get() = if (width != null && height != null) "$width x $height" else null
}

/**
 * A manual or a walkthrough attached to a game. [localPath] is null while the document is only
 * on the server.
 */
data class GameDocument(
    val fileName: String,
    val title: String,
    val category: String,
    val rommFileId: Long? = null,
    val remoteUrl: String? = null,
    val source: String? = null,
    val localPath: String? = null,
    val sizeBytes: Long = 0
) {
    val isLocal: Boolean get() = localPath != null

    val isPdf: Boolean get() = fileName.endsWith(".pdf", ignoreCase = true)
}

data class MoreOptionsContext(
    val isDownloaded: Boolean = false,
    val isRommGame: Boolean = false,
    val isAndroidApp: Boolean = false,
    val isSteamGame: Boolean = false,
    val canRemoveFromLibrary: Boolean = false,
    val canManageSaves: Boolean = false,
    val canManageStates: Boolean = false,
    val isMultiDisc: Boolean = false,
    val hasVariants: Boolean = false,
    val hasSiblingGroup: Boolean = false,
    val hasUpdates: Boolean = false,
    val hasManageableFiles: Boolean = false,
    val platformSlug: String? = null,
    val launchDisplayCount: Int = 0
)

/**
 * The focusable option rows, in render order. Single source of truth: the modal renders
 * these and the delegate indexes into them, so order and visibility cannot drift apart.
 */
fun buildMoreOptions(ctx: MoreOptionsContext): List<MoreOptionAction> = buildList {
    val canTrackProgress = ctx.isRommGame || ctx.isAndroidApp
    val isEmulatedGame = !ctx.isSteamGame && !ctx.isAndroidApp
    val usesTitleId = ctx.platformSlug?.let(com.nendo.argosy.data.platform.PlatformDefinitions::hasTitleId) == true

    if (ctx.isDownloaded && ctx.launchDisplayCount > 1) add(MoreOptionAction.LaunchOnDisplay)
    if (ctx.canManageSaves || ctx.canManageStates) add(MoreOptionAction.ManageSaves)
    if (canTrackProgress) add(MoreOptionAction.RatingsStatus)
    if (ctx.isSteamGame) add(MoreOptionAction.ChangeSteamLauncher)
    if (isEmulatedGame) add(MoreOptionAction.SpeedrunSplits)
    if (usesTitleId && isEmulatedGame) add(MoreOptionAction.RefreshTitleId)
    if (ctx.isMultiDisc) add(MoreOptionAction.SelectDisc)
    if (ctx.hasVariants && isEmulatedGame) add(MoreOptionAction.SelectVariant)
    if (ctx.hasSiblingGroup) add(MoreOptionAction.ActiveVariant)
    if ((ctx.hasManageableFiles || ctx.hasUpdates) && ctx.isDownloaded) add(MoreOptionAction.Files)
    if (canTrackProgress) add(MoreOptionAction.RefreshData)
    add(MoreOptionAction.AddToCollection)
    add(MoreOptionAction.Artwork)
    if (ctx.isDownloaded || ctx.isAndroidApp) add(MoreOptionAction.Delete)
    if (ctx.isSteamGame || ctx.canRemoveFromLibrary) add(MoreOptionAction.RemoveFromLibrary)
    add(MoreOptionAction.ToggleHide)
}

sealed class MoreOptionAction {
    data object LaunchOnDisplay : MoreOptionAction()
    data object ManageSaves : MoreOptionAction()
    data object RatingsStatus : MoreOptionAction()
    data object RateGame : MoreOptionAction()
    data object SetDifficulty : MoreOptionAction()
    data object SetProgress : MoreOptionAction()
    data object SetStatus : MoreOptionAction()
    data object ChangeEmulator : MoreOptionAction()
    data object ChangeSteamLauncher : MoreOptionAction()
    data object ChangeCore : MoreOptionAction()
    data object PlatformSettings : MoreOptionAction()
    data object SelectDisc : MoreOptionAction()
    data object SelectVariant : MoreOptionAction()
    data object ActiveVariant : MoreOptionAction()
    data object Files : MoreOptionAction()
    data object RefreshData : MoreOptionAction()
    data object AddToCollection : MoreOptionAction()
    data object RefreshTitleId : MoreOptionAction()
    data object SpeedrunSplits : MoreOptionAction()
    data object Artwork : MoreOptionAction()
    data object Delete : MoreOptionAction()
    data object RemoveFromLibrary : MoreOptionAction()
    data object ToggleHide : MoreOptionAction()
}

data class ExtractionFailedInfo(
    val gameId: Long,
    val fileName: String,
    val errorReason: com.nendo.argosy.core.notification.NotificationText
)

data class GameDetailUiState(
    val game: GameDetailUi? = null,
    val menuFocusIndex: Int = 0,
    val showMoreOptions: Boolean = false,
    val moreOptionsFocusIndex: Int = 0,
    val documents: List<GameDocument> = emptyList(),
    val documentFocusIndex: Int = 0,
    val launchDisplayNumbers: List<Int> = emptyList(),
    val launchDisplayIndex: Int = 0,
    val showPlayOptions: Boolean = false,
    val playOptionsFocusIndex: Int = 0,
    val hasCasualSaves: Boolean = false,
    val hasHardcoreSave: Boolean = false,
    val hasRASupport: Boolean = false,
    val hardcoreAvailable: Boolean = false,
    val statesAvailable: Boolean = false,
    val isOnline: Boolean = false,
    val canSearchCovers: Boolean = false,
    val isLoading: Boolean = true,
    val isRefreshingGameData: Boolean = false,
    val downloadStatus: GameDownloadStatus = GameDownloadStatus.NOT_DOWNLOADED,
    val hasManageableFiles: Boolean = false,
    val isAwaitingServer: Boolean = false,
    val downloadSizeBytes: Long? = null,
    val selectedCoreId: String? = null,
    val siblingGameIds: List<Long> = emptyList(),
    val currentGameIndex: Int = -1,
    val showRatingPicker: Boolean = false,
    val ratingPickerType: RatingType = RatingType.OPINION,
    val ratingPickerValue: Int = 0,
    val showStatusPicker: Boolean = false,
    val statusPickerValue: String? = null,
    val showRatingsStatusMenu: Boolean = false,
    val ratingsStatusFocusIndex: Int = 0,
    val showMissingDiscPrompt: Boolean = false,
    val missingDiscNumbers: List<Int> = emptyList(),
    val updateFiles: List<UpdateFileUi> = emptyList(),
    val dlcFiles: List<UpdateFileUi> = emptyList(),
    val syncProgress: SyncProgress = SyncProgress.Idle,
    @Deprecated("Use syncProgress instead")
    val syncState: SyncState = SyncState.Idle,
    val isSyncing: Boolean = false,
    val syncOverlayState: SyncOverlayState? = null,
    val sessionEndRunning: Boolean = false,
    val memcardPickerState: com.nendo.argosy.ui.screens.common.MemcardPickerState? = null,
    val memcardPickerFocusIndex: Int = 0,
    val launchVariantPickerState: com.nendo.argosy.ui.screens.common.VariantPickerState? = null,
    val launchVariantPickerFocusIndex: Int = 0,
    val relatedGames: List<com.nendo.argosy.ui.screens.home.HomeGameUi> = emptyList(),
    val relatedFocusIndex: Int = 0,
    val saveChannel: SaveChannelState = SaveChannelState(),
    val syncScreenshotsEnabled: Boolean = false,
    val saveStatusInfo: SaveStatusInfo? = null,
    val isSyncingSaves: Boolean = false,
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
    val showCreateCollectionDialog: Boolean = false,
    val showAchievementList: Boolean = false,
    val achievementListFocusIndex: Int = 0,
    val reviewSummary: com.nendo.argosy.data.social.ReviewSummary? = null,
    val friendsActivity: List<com.nendo.argosy.data.social.FriendActivity> = emptyList(),
    val reviewPage: com.nendo.argosy.data.social.GameReviewsPage? = null,
    val showReviewList: Boolean = false,
    val reviewListFocusIndex: Int = 0,
    val reviewEditor: ReviewEditorState? = null,
    val hasVariants: Boolean = false,
    val hasSiblingGroup: Boolean = false,
    val hasSocialAccount: Boolean = false,
    val isPrivate: Boolean = false,
    val perGameSettings: com.nendo.argosy.ui.screens.gamedetail.delegates.PerGameSettingsState =
        com.nendo.argosy.ui.screens.gamedetail.delegates.PerGameSettingsState()
) {
    val hasPreviousGame: Boolean get() = currentGameIndex > 0
    val hasNextGame: Boolean get() = currentGameIndex >= 0 && currentGameIndex < siblingGameIds.size - 1

    val reviewListHasWriteRow: Boolean
        get() = hasSocialAccount && game?.igdbId != null && reviewPage?.myReview == null

    // Convenience accessors for backward compatibility
    val showSaveCacheDialog: Boolean get() = saveChannel.isVisible
    val showRenameDialog: Boolean get() = saveChannel.showRenameDialog

    /**
     * Which menu rows this state shows. The menu's focus index is resolved against it everywhere,
     * so every reader has to build it here.
     */
    val menuLayoutState: com.nendo.argosy.ui.screens.gamedetail.components.MenuLayoutState
        get() {
            val status = saveStatusInfo?.status
            val hasSaveSync = status != null &&
                status != com.nendo.argosy.ui.screens.gamedetail.components.SaveSyncStatus.NO_SAVE &&
                status != com.nendo.argosy.ui.screens.gamedetail.components.SaveSyncStatus.NOT_CONFIGURED
            return com.nendo.argosy.ui.screens.gamedetail.components.MenuLayoutState(
                hasDescription = !game?.description.isNullOrBlank(),
                hasScreenshots = game?.screenshots?.isNotEmpty() == true,
                hasAchievements = game?.achievements?.isNotEmpty() == true,
                hasSocialAccount = hasSocialAccount,
                hasSaveSync = hasSaveSync,
                hasRelated = relatedGames.isNotEmpty(),
                hasPerGameSettings = game != null && !game.isSteamGame && !game.isAndroidApp &&
                    downloadStatus == GameDownloadStatus.DOWNLOADED,
                hasDocuments = documents.isNotEmpty()
            )
        }
}
