/**
 * DUAL-SCREEN COMPONENT - Game detail data models.
 */
package com.nendo.argosy.ui.dualscreen.gamedetail

import androidx.annotation.StringRes
import com.nendo.argosy.R
import com.nendo.argosy.data.emulator.DiscOption
import com.nendo.argosy.data.model.visibleWithCollapsed
import com.nendo.argosy.data.platform.PlatformDefinitions
import com.nendo.argosy.domain.model.UnifiedSaveEntry
import com.nendo.argosy.domain.model.UnifiedStateEntry
import com.nendo.argosy.ui.common.savechannel.SaveFocusColumn
import com.nendo.argosy.ui.common.savechannel.SaveHistoryItem
import com.nendo.argosy.ui.common.savechannel.SaveSlotItem
import com.nendo.argosy.ui.screens.gamedetail.CoverCandidate
import com.nendo.argosy.ui.screens.gamedetail.ReviewEditorState
import com.nendo.argosy.ui.screens.gamedetail.UpdateFileUi
import org.json.JSONArray
import org.json.JSONObject

enum class DualGameDetailTab(@StringRes val labelRes: Int) {
    SAVES(R.string.dual_detail_tab_saves),
    STATES(R.string.dual_detail_tab_states),
    MEDIA(R.string.dual_detail_tab_media),
    REVIEWS(R.string.dual_detail_tab_reviews),
    OPTIONS(R.string.dual_detail_tab_options)
}

enum class ActiveModal { NONE, RATING, DIFFICULTY, STATUS, EMULATOR, CORE, SAVE_PATH, DISPLAY_TARGET, MEMORY_CARD, COLLECTION, SAVE_NAME, DISC_PICKER, VARIANT_PICKER, STEAM_INSTALL, FILE_PICKER, COVER_PICKER, REVIEW_EDITOR }

enum class DualStateMenuAction(@StringRes val labelRes: Int) {
    COPY_TO(R.string.dual_state_menu_copy_to),
    DELETE(R.string.dual_state_menu_delete)
}

enum class DualStatePrompt { DELETE, OVERWRITE }

enum class GameDetailOption {
    PLAY,
    RATING,
    DIFFICULTY,
    STATUS,
    TOGGLE_FAVORITE,
    CHANGE_EMULATOR,
    CHANGE_CORE,
    SAVE_PATH,
    DISPLAY_TARGET,
    MEMORY_CARD,
    SELECT_VARIANT,
    SELECT_DISC,
    TITLE_ID,
    FILES,
    ADD_TO_COLLECTION,
    WRITE_REVIEW,
    REFRESH_METADATA,
    CHANGE_COVER,
    RESET_COVER,
    DELETE,
    HIDE
}

data class DualCollectionItem(
    val id: Long,
    val name: String,
    val isInCollection: Boolean
)

data class DualGameDetailUiState(
    val gameId: Long = -1,
    val title: String = "",
    val coverPath: String? = null,
    val backgroundPath: String? = null,
    val platformName: String = "",
    val developer: String? = null,
    val releaseYear: Int? = null,
    val description: String? = null,
    val playTimeMinutes: Int = 0,
    val lastPlayedAt: Long = 0,
    val status: String? = null,
    val rating: Int? = null,
    val screenshots: List<String> = emptyList(),
    val igdbId: Int? = null,
    val reviewPage: com.nendo.argosy.data.social.GameReviewsPage? = null,
    val reviewFocusIndex: Int = 0,
    val isPlayable: Boolean = false,
    val userDifficulty: Int = 0,
    val currentTab: DualGameDetailTab = DualGameDetailTab.OPTIONS,
    val availableTabs: List<DualGameDetailTab> = DualGameDetailTab.entries,
    val isFavorite: Boolean = false,
    val isLoading: Boolean = true,
    val achievementCount: Int = 0,
    val earnedAchievementCount: Int = 0,
    val isRommGame: Boolean = false,
    val isSteamGame: Boolean = false,
    val steamAppId: Long? = null,
    val isAndroidApp: Boolean = false,
    val isDownloaded: Boolean = false,
    val platformSlug: String = "",
    val platformId: Long = 0,
    val emulatorName: String? = null,
    val isBuiltInEmulator: Boolean = false,
    val saveFocusColumn: SaveFocusColumn = SaveFocusColumn.SLOTS,
    val activeChannel: String? = null,
    val activeSaveTimestamp: Long? = null,
    val saveSyncStatusName: String? = null,
    val hasMultipleCores: Boolean = false,
    val selectedCoreName: String? = null,
    val selectedCoreId: String? = null,
    val hasFileBasedSaves: Boolean = false,
    val savePathOverride: String? = null,
    val hasSecondaryDisplay: Boolean = false,
    val displayTargetName: String? = null,
    val platformDisplayTargetName: String? = null,
    val hasMultipleMemcards: Boolean = false,
    val selectedMemcardName: String? = null,
    val hasMultipleVariants: Boolean = false,
    val selectedVariantName: String? = null,
    val downloadProgress: Float? = null,
    val downloadState: String? = null,
    val isAwaitingServer: Boolean = false,
    val isDeleting: Boolean = false,
    val isMultiDisc: Boolean = false,
    val isHidden: Boolean = false,
    val titleId: String? = null,
    val canSearchCovers: Boolean = false,
    val coverSetManually: Boolean = false,
    val stateMenuVisible: Boolean = false,
    val stateMenuFocusIndex: Int = 0,
    val stateCopySourceSlot: Int? = null,
    val statePrompt: DualStatePrompt? = null,
    val statePromptSlot: Int = 0,
    val statePromptFocusIndex: Int = 0
) {
    val canWriteReview: Boolean
        get() = igdbId != null && DualGameDetailTab.REVIEWS in availableTabs

    val reviewListHasWriteRow: Boolean
        get() = canWriteReview && reviewPage?.myReview == null
}

fun DualGameDetailUiState.visibleOptions(): List<GameDetailOption> {
    val isEmulated = !isSteamGame && !isAndroidApp
    val usesTitleId = platformSlug in PlatformDefinitions.TITLE_ID_PLATFORMS
    return buildList {
        if (!isDeleting) add(GameDetailOption.PLAY)
        add(GameDetailOption.RATING)
        add(GameDetailOption.DIFFICULTY)
        add(GameDetailOption.STATUS)
        add(GameDetailOption.TOGGLE_FAVORITE)
        if (isEmulated) add(GameDetailOption.CHANGE_EMULATOR)
        if (hasMultipleCores && isEmulated) add(GameDetailOption.CHANGE_CORE)
        if (hasFileBasedSaves && isEmulated) add(GameDetailOption.SAVE_PATH)
        if (hasSecondaryDisplay && isEmulated) add(GameDetailOption.DISPLAY_TARGET)
        if (hasMultipleMemcards && isEmulated) add(GameDetailOption.MEMORY_CARD)
        if (hasMultipleVariants && isEmulated) add(GameDetailOption.SELECT_VARIANT)
        if (isMultiDisc && isEmulated) add(GameDetailOption.SELECT_DISC)
        if (usesTitleId && isEmulated) add(GameDetailOption.TITLE_ID)
        if (isDownloaded && !isDeleting) add(GameDetailOption.FILES)
        add(GameDetailOption.ADD_TO_COLLECTION)
        if (canWriteReview) add(GameDetailOption.WRITE_REVIEW)
        if (isRommGame || isAndroidApp) add(GameDetailOption.REFRESH_METADATA)
        if (canSearchCovers) add(GameDetailOption.CHANGE_COVER)
        if (coverSetManually) add(GameDetailOption.RESET_COVER)
        if ((isDownloaded || isAndroidApp) && !isDeleting) add(GameDetailOption.DELETE)
        add(GameDetailOption.HIDE)
    }
}

data class DualGameDetailUpperState(
    val gameId: Long = -1,
    val title: String = "",
    val coverPath: String? = null,
    val backgroundPath: String? = null,
    val boxBackPath: String? = null,
    val boxSpinePath: String? = null,
    val platformName: String = "",
    val developer: String? = null,
    val releaseYear: Int? = null,
    val description: String? = null,
    val timeToBeatMainSec: Int? = null,
    val timeToBeatExtraSec: Int? = null,
    val timeToBeatCompletionistSec: Int? = null,
    val playTimeMinutes: Int = 0,
    val lastPlayedAt: Long = 0,
    val status: String? = null,
    val rating: Int? = null,
    val userDifficulty: Int = 0,
    val communityRating: Float? = null,
    val titleId: String? = null,
    val players: String? = null,
    val achievementCount: Int = 0,
    val earnedAchievementCount: Int = 0,
    val screenshots: List<String> = emptyList(),
    val viewerScreenshotIndex: Int? = null,
    val modalType: ActiveModal = ActiveModal.NONE,
    val modalRatingValue: Int = 0,
    val modalStatusSelected: String? = null,
    val modalStatusCurrent: String? = null,
    val emulatorNames: List<String> = emptyList(),
    val emulatorVersions: List<String> = emptyList(),
    val emulatorFocusIndex: Int = 0,
    val emulatorCurrentName: String? = null,
    val coreNames: List<String> = emptyList(),
    val coreFocusIndex: Int = 0,
    val coreCurrentName: String? = null,
    val savePathOverride: String? = null,
    val savePathFocusIndex: Int = 0,
    val displayTargetNames: List<String> = emptyList(),
    val displayTargetFocusIndex: Int = 0,
    val displayTargetCurrentName: String? = null,
    val displayTargetInheritedName: String? = null,
    val memoryCardNames: List<String> = emptyList(),
    val memoryCardFocusIndex: Int = 0,
    val memoryCardCurrentName: String? = null,
    val memoryCardInheritedName: String? = null,
    val variantNames: List<String> = emptyList(),
    val variantFocusIndex: Int = 0,
    val variantCurrentName: String? = null,
    val collectionItems: List<DualCollectionItem> = emptyList(),
    val collectionFocusIndex: Int = 0,
    val showCreateDialog: Boolean = false,
    val saveNamePromptAction: String? = null,
    val saveNameCacheId: Long? = null,
    val saveNameText: String = "",
    val updateFiles: List<UpdateFileUi> = emptyList(),
    val dlcFiles: List<UpdateFileUi> = emptyList(),
    val focusedStateEntry: UnifiedStateEntry? = null,
    val statePreviewScreenshotPath: String? = null,
    val discPickerOptions: List<DiscOption> = emptyList(),
    val discPickerFocusIndex: Int = 0,
    val steamInstallOptionNames: List<String> = emptyList(),
    val steamInstallOptionPackages: List<String> = emptyList(),
    val steamInstallFocusIndex: Int = 0,
    val isHomeChooser: Boolean = false,
    val filePickerRows: List<com.nendo.argosy.data.model.FilePickerRow> = emptyList(),
    val filePickerSelected: Set<Long> = emptySet(),
    val filePickerSelectedVersions: Set<Long> = emptySet(),
    val filePickerFocusIndex: Int = 0,
    val filePickerCollapsed: Set<String> = emptySet(),
    val filePickerManageMode: Boolean = false,
    val coverCandidates: List<CoverCandidate> = emptyList(),
    val coverPickerFocusIndex: Int = 0,
    val coverPickerLoading: Boolean = false,
    val coverPickerError: String? = null,
    val coverPickerQuery: String = "",
    val reviewEditor: ReviewEditorState? = null
) {
    val visibleFilePickerRows: List<com.nendo.argosy.data.model.FilePickerRow>
        get() = filePickerRows.visibleWithCollapsed(filePickerCollapsed)
}

data class SaveEntryData(
    val localCacheId: Long?,
    val serverSaveId: Long?,
    val timestamp: Long,
    val size: Long,
    val channelName: String?,
    val source: String,
    val isLatest: Boolean,
    val isLocked: Boolean,
    val isHardcore: Boolean,
    val isRollback: Boolean,
    val cheatsUsed: Boolean,
    val displayName: String
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("localCacheId", localCacheId ?: JSONObject.NULL)
        put("serverSaveId", serverSaveId ?: JSONObject.NULL)
        put("timestamp", timestamp)
        put("size", size)
        put("channelName", channelName ?: JSONObject.NULL)
        put("source", source)
        put("isLatest", isLatest)
        put("isLocked", isLocked)
        put("isHardcore", isHardcore)
        put("isRollback", isRollback)
        put("cheatsUsed", cheatsUsed)
        put("displayName", displayName)
    }

    companion object {
        fun fromJson(json: JSONObject): SaveEntryData = SaveEntryData(
            localCacheId = if (json.isNull("localCacheId")) null
                else json.getLong("localCacheId"),
            serverSaveId = if (json.isNull("serverSaveId")) null
                else json.getLong("serverSaveId"),
            timestamp = json.getLong("timestamp"),
            size = json.getLong("size"),
            channelName = if (json.isNull("channelName")) null
                else json.getString("channelName"),
            source = json.getString("source"),
            isLatest = json.getBoolean("isLatest"),
            isLocked = json.getBoolean("isLocked"),
            isHardcore = json.getBoolean("isHardcore"),
            isRollback = json.getBoolean("isRollback"),
            cheatsUsed = json.getBoolean("cheatsUsed"),
            displayName = json.getString("displayName")
        )
    }
}

fun List<SaveEntryData>.toJsonString(): String {
    val arr = JSONArray()
    forEach { arr.put(it.toJson()) }
    return arr.toString()
}

fun parseSaveEntryDataList(json: String): List<SaveEntryData> {
    if (json.isBlank()) return emptyList()
    val arr = JSONArray(json)
    return (0 until arr.length()).map { SaveEntryData.fromJson(arr.getJSONObject(it)) }
}

fun UnifiedSaveEntry.toSaveEntryData(): SaveEntryData = SaveEntryData(
    localCacheId = localCacheId,
    serverSaveId = serverSaveId,
    timestamp = timestamp.toEpochMilli(),
    size = size,
    channelName = channelName,
    source = source.name,
    isLatest = isLatest,
    isLocked = isLocked,
    isHardcore = isHardcore,
    isRollback = isRollback,
    cheatsUsed = cheatsUsed,
    displayName = displayName
)
