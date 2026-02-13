/**
 * DUAL-SCREEN COMPONENT - Game detail data models.
 */
package com.nendo.argosy.ui.dualscreen.gamedetail

enum class DualGameDetailTab {
    SAVES,
    MEDIA,
    OPTIONS
}

enum class ActiveModal { NONE, RATING, DIFFICULTY, STATUS, EMULATOR, COLLECTION }

enum class GameDetailOption {
    PLAY,
    RATING,
    DIFFICULTY,
    STATUS,
    TOGGLE_FAVORITE,
    CHANGE_EMULATOR,
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
    val emulatorName: String? = null
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
    val showCreateDialog: Boolean = false
)

data class SaveTimelineEntry(
    val id: String,
    val timestamp: Long,
    val label: String?,
    val isLocked: Boolean,
    val isLocal: Boolean,
    val isSynced: Boolean,
    val isRemoteOnly: Boolean
)

data class SaveChannelUi(
    val name: String,
    val isActive: Boolean
)
