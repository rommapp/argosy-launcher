/**
 * DUAL-SCREEN COMPONENT - Game detail data models.
 */
package com.nendo.argosy.ui.dualscreen.gamedetail

import com.nendo.argosy.domain.model.UnifiedSaveEntry
import com.nendo.argosy.ui.common.savechannel.SaveFocusColumn
import com.nendo.argosy.ui.common.savechannel.SaveHistoryItem
import com.nendo.argosy.ui.common.savechannel.SaveSlotItem
import com.nendo.argosy.ui.screens.gamedetail.UpdateFileUi
import org.json.JSONArray
import org.json.JSONObject

enum class DualGameDetailTab {
    SAVES,
    MEDIA,
    OPTIONS
}

enum class ActiveModal { NONE, RATING, DIFFICULTY, STATUS, EMULATOR, COLLECTION, SAVE_NAME, UPDATES_DLC }

enum class GameDetailOption {
    PLAY,
    RATING,
    DIFFICULTY,
    STATUS,
    TOGGLE_FAVORITE,
    CHANGE_EMULATOR,
    UPDATES_DLC,
    ADD_TO_COLLECTION,
    REFRESH_METADATA,
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
    val isPlayable: Boolean = false,
    val userDifficulty: Int = 0,
    val currentTab: DualGameDetailTab = DualGameDetailTab.OPTIONS,
    val isFavorite: Boolean = false,
    val isLoading: Boolean = true,
    val achievementCount: Int = 0,
    val earnedAchievementCount: Int = 0,
    val isRommGame: Boolean = false,
    val isSteamGame: Boolean = false,
    val isAndroidApp: Boolean = false,
    val isDownloaded: Boolean = false,
    val platformSlug: String = "",
    val platformId: Long = 0,
    val emulatorName: String? = null,
    val saveFocusColumn: SaveFocusColumn = SaveFocusColumn.SLOTS,
    val activeChannel: String? = null,
    val activeSaveTimestamp: Long? = null,
    val downloadProgress: Float? = null,
    val downloadState: String? = null
)

fun DualGameDetailUiState.visibleOptions(): List<GameDetailOption> {
    val isEmulated = !isSteamGame && !isAndroidApp
    return buildList {
        add(GameDetailOption.PLAY)
        add(GameDetailOption.RATING)
        add(GameDetailOption.DIFFICULTY)
        add(GameDetailOption.STATUS)
        add(GameDetailOption.TOGGLE_FAVORITE)
        if (isEmulated) add(GameDetailOption.CHANGE_EMULATOR)
        if (isDownloaded) add(GameDetailOption.UPDATES_DLC)
        add(GameDetailOption.ADD_TO_COLLECTION)
        if (isRommGame || isAndroidApp) add(GameDetailOption.REFRESH_METADATA)
        if (isDownloaded || isAndroidApp) add(GameDetailOption.DELETE)
        add(GameDetailOption.HIDE)
    }
}

data class DualGameDetailUpperState(
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
    val userDifficulty: Int = 0,
    val communityRating: Float? = null,
    val titleId: String? = null,
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
    val collectionItems: List<DualCollectionItem> = emptyList(),
    val collectionFocusIndex: Int = 0,
    val showCreateDialog: Boolean = false,
    val saveNamePromptAction: String? = null,
    val saveNameCacheId: Long? = null,
    val saveNameText: String = "",
    val updateFiles: List<UpdateFileUi> = emptyList(),
    val dlcFiles: List<UpdateFileUi> = emptyList(),
    val updatesPickerFocusIndex: Int = 0,
    val isEdenGame: Boolean = false
)

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
