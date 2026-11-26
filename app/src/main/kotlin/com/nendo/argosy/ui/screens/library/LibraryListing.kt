package com.nendo.argosy.ui.screens.library

import com.nendo.argosy.data.local.entity.CollectionType
import com.nendo.argosy.data.local.entity.GameListItem
import com.nendo.argosy.data.model.GameSection
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.model.SortPartition
import com.nendo.argosy.data.model.SourceFilter
import com.nendo.argosy.data.model.computeSections
import com.nendo.argosy.data.repository.CollectionRepository
import com.nendo.argosy.data.repository.GameRepository
import com.nendo.argosy.domain.model.PlayerCount
import com.nendo.argosy.util.SearchNormalizer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * Game ids the region and series filters admit, resolved from the database. Null means the filter
 * is off and admits every game.
 */
data class LibraryFilterIds(
    val regionIds: Set<Long>? = null,
    val seriesIds: Set<Long>? = null
)

/**
 * A filtered, sorted and sectioned game list as the library grid lays it out. [games] is the flat
 * reading order that every focus index points into.
 */
data class LibraryListing(
    val sections: List<GameSection> = emptyList(),
    val gridItems: List<LibraryGridItem> = emptyList()
) {
    val games: List<LibraryGameUi> = gridItems.mapNotNull { (it as? LibraryGridItem.Game)?.game }

    val sectionLabels: List<String> = sections.map { it.sidebarLabel }

    fun sectionLabelForGameIndex(gameIndex: Int): String {
        var offset = 0
        for (section in sections) {
            if (gameIndex < offset + section.games.size) return section.sidebarLabel
            offset += section.games.size
        }
        return sections.firstOrNull()?.sidebarLabel ?: ""
    }

    fun firstGameIndexOfSection(sidebarLabel: String): Int? {
        var offset = 0
        for (section in sections) {
            if (section.sidebarLabel == sidebarLabel && section.games.isNotEmpty()) return offset
            offset += section.games.size
        }
        return null
    }
}

fun observeLibraryFilterIds(
    filters: ActiveFilters,
    gameRepository: GameRepository,
    collectionRepository: CollectionRepository
): Flow<LibraryFilterIds> {
    val seriesIds: Flow<Set<Long>?> = if (filters.series.isEmpty()) {
        flowOf(null)
    } else {
        collectionRepository.observeGameIdsByTypeAndNames(CollectionType.SERIES, filters.series.toList())
            .map { it.toSet() }
    }
    val regionIds: Flow<Set<Long>?> = if (filters.regions.isEmpty()) {
        flowOf(null)
    } else {
        gameRepository.observeGameIdsInRegions(filters.regions)
    }
    return combine(regionIds, seriesIds) { regions, series -> LibraryFilterIds(regions, series) }
}

fun filterLibraryGames(
    games: List<GameListItem>,
    filters: ActiveFilters,
    ids: LibraryFilterIds
): List<GameListItem> {
    val normalizedQuery = SearchNormalizer.normalize(filters.searchQuery)
    val platformIds = filters.platforms.map { it.id }.toSet()
    return games.filter { game ->
        val matchesSearch = filters.searchQuery.isEmpty() ||
            SearchNormalizer.normalize(game.title).contains(normalizedQuery)
        val matchesPlatform = platformIds.isEmpty() || game.platformId in platformIds
        val matchesGenre = filters.genres.isEmpty() || filters.genres.contains(game.genre)
        val matchesRegion = ids.regionIds == null || game.id in ids.regionIds
        val matchesPlayers = filters.players?.admits(PlayerCount.parse(game.players)) ?: true
        val matchesSeries = ids.seriesIds == null || game.id in ids.seriesIds
        matchesSearch && matchesPlatform && matchesGenre && matchesRegion && matchesPlayers && matchesSeries
    }
}

/**
 * Whether [game] belongs under [source], matching the library's per-source queries for a set that
 * was loaded without one.
 */
fun SourceFilter.admitsSource(game: GameListItem): Boolean = when (this) {
    SourceFilter.ALL -> !game.isHidden
    SourceFilter.HIDDEN -> game.isHidden
    SourceFilter.FAVORITES -> !game.isHidden && game.isFavorite
    SourceFilter.PLAYABLE -> !game.isHidden && game.source in PLAYABLE_SOURCES &&
        (game.source != GameSource.STEAM || game.localPath != null || game.isExternallyManaged)
}

suspend fun buildLibraryListing(
    games: List<GameListItem>,
    filters: ActiveFilters,
    ids: LibraryFilterIds,
    partition: SortPartition,
    toUi: suspend (GameListItem) -> LibraryGameUi
): LibraryListing {
    val sections = computeSections(filterLibraryGames(games, filters, ids), filters.sort, partition)
    var gameOffset = 0
    val gridItems = sections.flatMap { section ->
        val header = LibraryGridItem.Header(section.label, section.sidebarLabel)
        val gameItems = section.games.mapIndexed { i, game ->
            LibraryGridItem.Game(toUi(game), gameIndex = gameOffset + i)
        }
        gameOffset += section.games.size
        listOf(header) + gameItems
    }
    return LibraryListing(sections, gridItems)
}

private val PLAYABLE_SOURCES = setOf(
    GameSource.LOCAL_ONLY,
    GameSource.ROMM_SYNCED,
    GameSource.STEAM,
    GameSource.ANDROID_APP
)
