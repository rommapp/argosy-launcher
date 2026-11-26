package com.nendo.argosy.data.repository

import com.nendo.argosy.data.local.dao.GameChangeDao
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.GameListChanges
import com.nendo.argosy.data.local.entity.GameListItem
import com.nendo.argosy.data.model.GameSource
import kotlinx.coroutines.flow.first

sealed interface GameListScope {
    data object All : GameListScope
    data object Playable : GameListScope
    data object Favorites : GameListScope
    data class Platform(val platformId: Long) : GameListScope
    data class PlatformPlayable(val platformId: Long) : GameListScope
    data class PlatformFavorites(val platformId: Long) : GameListScope
}

val GAME_LIST_TITLE_ORDER: Comparator<GameListItem> =
    Comparator<GameListItem> { a, b -> compareBinaryCollation(a.sortTitle, b.sortTitle) }.thenBy { it.id }

val GAME_LIST_FAVORITES_ORDER: Comparator<GameListItem> =
    compareBy<GameListItem> { it.source == GameSource.ROMM_REMOTE }.then(GAME_LIST_TITLE_ORDER)

fun GameListChanges.toDelta(): GameScopeDelta<GameListItem> = GameScopeDelta(
    changes = rows.map { change -> GameRowChange(change.item.id, change.item.takeIf { change.inScope }) },
    tally = tally,
    maxSeq = maxSeq
)

class GameListScopeSource(
    private val scope: GameListScope,
    private val ownerUserId: Long?,
    private val gameDao: GameDao,
    private val changeDao: GameChangeDao
) : GameScopeSource<GameListItem> {

    override val order: Comparator<in GameListItem> = when (scope) {
        GameListScope.Favorites, is GameListScope.PlatformFavorites -> GAME_LIST_FAVORITES_ORDER
        else -> GAME_LIST_TITLE_ORDER
    }

    override fun idOf(row: GameListItem): Long = row.id

    override suspend fun load(): List<GameListItem> = when (scope) {
        GameListScope.All -> gameDao.observeAllList(ownerUserId)
        GameListScope.Playable -> gameDao.observePlayableList(ownerUserId)
        GameListScope.Favorites -> gameDao.observeFavoritesList(ownerUserId)
        is GameListScope.Platform -> gameDao.observeByPlatformList(scope.platformId, ownerUserId)
        is GameListScope.PlatformPlayable -> gameDao.observePlayableByPlatformList(scope.platformId, ownerUserId)
        is GameListScope.PlatformFavorites -> gameDao.observeFavoritesByPlatformList(scope.platformId, ownerUserId)
    }.first()

    override suspend fun changesSince(seq: Long): GameScopeDelta<GameListItem> = when (scope) {
        GameListScope.All -> changeDao.allListChanges(seq, ownerUserId)
        GameListScope.Playable -> changeDao.playableListChanges(seq, ownerUserId)
        GameListScope.Favorites -> changeDao.favoritesListChanges(seq, ownerUserId)
        is GameListScope.Platform -> changeDao.platformListChanges(seq, scope.platformId, ownerUserId)
        is GameListScope.PlatformPlayable ->
            changeDao.platformPlayableListChanges(seq, scope.platformId, ownerUserId)
        is GameListScope.PlatformFavorites ->
            changeDao.platformFavoritesListChanges(seq, scope.platformId, ownerUserId)
    }.toDelta()
}
