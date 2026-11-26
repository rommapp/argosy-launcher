package com.nendo.argosy.data.repository

import com.nendo.argosy.data.local.dao.GameChangeDao
import com.nendo.argosy.data.local.dao.GameCategoryChanges
import com.nendo.argosy.data.local.dao.GameRegionChanges
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

data class GameRegionTokens(
    val id: Long,
    val regions: Set<String>
)

data class GameCategoryTokens(
    val id: Long,
    val genres: List<String>?,
    val gameModes: List<String>?
)

fun regionTokens(id: Long, regions: String?): GameRegionTokens = GameRegionTokens(id, splitRegions(regions).toSet())

fun categoryTokens(id: Long, genre: String?, gameModes: String?): GameCategoryTokens =
    GameCategoryTokens(id, genre?.split(",")?.map { it.trim() }, gameModes?.split(",")?.map { it.trim() })

fun List<GameRegionTokens>.idsInRegions(regions: Set<String>): Set<Long> =
    filter { row -> row.regions.any { it in regions } }.mapTo(HashSet()) { it.id }

fun GameRegionChanges.toDelta(): GameScopeDelta<GameRegionTokens> = GameScopeDelta(
    changes = rows.map { GameRowChange(it.id, if (it.inScope) regionTokens(it.id, it.regions) else null) },
    tally = tally,
    maxSeq = maxSeq
)

fun GameCategoryChanges.toDelta(): GameScopeDelta<GameCategoryTokens> = GameScopeDelta(
    changes = rows.map {
        GameRowChange(it.id, if (it.inScope) categoryTokens(it.id, it.genre, it.gameModes) else null)
    },
    tally = tally,
    maxSeq = maxSeq
)

class GameRegionScopeSource(private val changeDao: GameChangeDao) : GameScopeSource<GameRegionTokens> {
    override val order: Comparator<in GameRegionTokens>? = null

    override fun idOf(row: GameRegionTokens): Long = row.id

    override suspend fun load(): List<GameRegionTokens> =
        changeDao.getRegionInfo().map { regionTokens(it.id, it.regions) }

    override suspend fun changesSince(seq: Long): GameScopeDelta<GameRegionTokens> =
        changeDao.regionChanges(seq).toDelta()
}

class GameCategoryScopeSource(
    private val ownerUserId: Long?,
    private val changeDao: GameChangeDao
) : GameScopeSource<GameCategoryTokens> {
    override val order: Comparator<in GameCategoryTokens>? = null

    override fun idOf(row: GameCategoryTokens): Long = row.id

    override suspend fun load(): List<GameCategoryTokens> =
        changeDao.getCategoryInfo(ownerUserId).map { categoryTokens(it.id, it.genre, it.gameModes) }

    override suspend fun changesSince(seq: Long): GameScopeDelta<GameCategoryTokens> =
        changeDao.categoryChanges(seq, ownerUserId).toDelta()
}

/**
 * Genre and game-mode tokens of every game on an enabled platform that the account has not hidden,
 * kept current from changed rows. One account's index is held at a time.
 */
@Singleton
class GameCategoryIndex @Inject constructor(private val changeDao: GameChangeDao) {

    private class OwnedCursor(val ownerUserId: Long?, val cursor: GameScopeCursor<GameCategoryTokens>)

    private val mutex = Mutex()
    private var active: OwnedCursor? = null

    suspend fun rows(ownerUserId: Long?): List<GameCategoryTokens> = cursorFor(ownerUserId).current()

    fun observeEachChange(ownerUserId: Long?): Flow<List<GameCategoryTokens>> = flow {
        emitAll(cursorFor(ownerUserId).observeEachChange())
    }

    private suspend fun cursorFor(ownerUserId: Long?): GameScopeCursor<GameCategoryTokens> = mutex.withLock {
        active?.takeIf { it.ownerUserId == ownerUserId }?.cursor
            ?: GameScopeCursor(GameCategoryScopeSource(ownerUserId, changeDao), changeDao)
                .also { active = OwnedCursor(ownerUserId, it) }
    }
}
