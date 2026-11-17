package com.nendo.argosy.domain.model

import com.nendo.argosy.data.model.ActiveSort
import com.nendo.argosy.data.model.SortOption
import com.nendo.argosy.data.model.SourceFilter

/**
 * What a library link tile opens the library onto, and the rule an auto-folder fills from. Every
 * value is a stored token: ids, raw server values, [CompletionStatus.apiValue]s, never labels.
 * The release year bounds are inclusive and either may be open.
 */
data class LibraryLinkFilters(
    val source: SourceFilter = SourceFilter.ALL,
    val platformIds: Set<Long> = emptySet(),
    val genres: Set<String> = emptySet(),
    val series: Set<String> = emptySet(),
    val players: PlayerCountBucket? = null,
    val sort: ActiveSort = ActiveSort(),
    val developers: Set<String> = emptySet(),
    val releaseYearFrom: Int? = null,
    val releaseYearTo: Int? = null,
    val playStatuses: Set<String> = emptySet(),
    val addedWithinDays: Int? = null,
    val raProgress: RaProgressFilter? = null
) {
    val isUnfiltered: Boolean
        get() = source == SourceFilter.ALL &&
            platformIds.isEmpty() &&
            genres.isEmpty() &&
            series.isEmpty() &&
            players == null &&
            sort.option == SortOption.TITLE &&
            developers.isEmpty() &&
            releaseYearFrom == null &&
            releaseYearTo == null &&
            playStatuses.isEmpty() &&
            addedWithinDays == null &&
            raProgress == null

    /**
     * Whether [game] passes every category this filter narrows by except platform and source,
     * which the candidate query already applied. [seriesGameIds] is null when the filter names no
     * series.
     */
    fun admits(game: LibraryFilterFacts, seriesGameIds: Set<Long>?, nowMillis: Long): Boolean =
        (genres.isEmpty() || game.genre in genres) &&
            (seriesGameIds == null || game.id in seriesGameIds) &&
            (players == null || players.admits(PlayerCount.parse(game.players))) &&
            (developers.isEmpty() || game.developer in developers) &&
            (releaseYearFrom == null || (game.releaseYear != null && game.releaseYear >= releaseYearFrom)) &&
            (releaseYearTo == null || (game.releaseYear != null && game.releaseYear <= releaseYearTo)) &&
            (playStatuses.isEmpty() || game.status in playStatuses) &&
            (addedWithinDays == null || nowMillis - game.addedAt <= addedWithinDays * MILLIS_PER_DAY) &&
            (raProgress == null || raProgress.admits(game.achievementCount, game.earnedAchievementCount))

    private companion object {
        const val MILLIS_PER_DAY = 86_400_000L
    }
}

/**
 * The per-game facts [LibraryLinkFilters.admits] reads.
 */
data class LibraryFilterFacts(
    val id: Long,
    val genre: String?,
    val players: String?,
    val developer: String? = null,
    val releaseYear: Int? = null,
    val status: String? = null,
    val addedAt: Long = 0L,
    val achievementCount: Int = 0,
    val earnedAchievementCount: Int = 0
)

enum class RaProgressFilter {
    NOT_STARTED,
    IN_PROGRESS,
    MASTERED;

    fun admits(total: Int, earned: Int): Boolean = when (this) {
        NOT_STARTED -> total > 0 && earned == 0
        IN_PROGRESS -> total > 0 && earned in 1 until total
        MASTERED -> total > 0 && earned >= total
    }

    companion object {
        fun fromStored(value: String?): RaProgressFilter? = entries.find { it.name == value }
    }
}
