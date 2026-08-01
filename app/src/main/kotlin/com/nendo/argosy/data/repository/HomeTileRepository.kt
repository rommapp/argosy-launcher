package com.nendo.argosy.data.repository

import com.nendo.argosy.data.local.dao.HomeTileDao
import com.nendo.argosy.data.local.entity.HomeTileEntity
import com.nendo.argosy.data.local.entity.HomeTileTarget
import com.nendo.argosy.domain.model.HomeTile
import com.nendo.argosy.domain.model.HomeTileTargetRef
import com.nendo.argosy.domain.model.TileRect
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The custom home grid's tiles. Placement rules live in the domain model; this layer only stores
 * and resolves.
 */
@Singleton
class HomeTileRepository @Inject constructor(
    private val homeTileDao: HomeTileDao
) {

    fun observeTiles(ownerUserId: Long?): Flow<List<HomeTile>> =
        homeTileDao.observeTiles(ownerUserId).map { rows -> rows.map { it.toDomain() } }

    suspend fun pageCount(ownerUserId: Long?): Int =
        (homeTileDao.getMaxPageIndex(ownerUserId)?.plus(1) ?: 0).coerceAtLeast(DEFAULT_PAGE_COUNT)

    suspend fun place(
        ownerUserId: Long?,
        pageIndex: Int,
        rect: TileRect,
        target: HomeTileTargetRef
    ): Long = homeTileDao.insert(entityFor(ownerUserId, pageIndex, rect, target))

    suspend fun move(tile: HomeTile, ownerUserId: Long?, rect: TileRect) {
        homeTileDao.update(
            entityFor(ownerUserId, tile.pageIndex, rect, tile.target).copy(id = tile.id)
        )
    }

    suspend fun remove(tileId: Long) = homeTileDao.deleteById(tileId)

    /**
     * Removes a page and closes the gap behind it. The tiles go; nothing they pointed at is
     * touched, which is why the confirmation this sits behind says the games stay on the device.
     */
    suspend fun removePage(ownerUserId: Long?, pageIndex: Int) {
        homeTileDao.deletePage(ownerUserId, pageIndex)
        homeTileDao.shiftPagesDown(ownerUserId, pageIndex)
    }

    suspend fun pruneMissingGames() = homeTileDao.deleteTilesForMissingGames()

    private fun entityFor(
        ownerUserId: Long?,
        pageIndex: Int,
        rect: TileRect,
        target: HomeTileTargetRef
    ): HomeTileEntity {
        val base = HomeTileEntity(
            ownerUserId = ownerUserId,
            pageIndex = pageIndex,
            columnIndex = rect.columnIndex,
            rowIndex = rect.rowIndex,
            columnSpan = rect.columnSpan,
            rowSpan = rect.rowSpan,
            targetType = target.storedType()
        )
        return when (target) {
            is HomeTileTargetRef.Game -> base.copy(gameId = target.gameId)
            is HomeTileTargetRef.Collection -> base.copy(collectionId = target.collectionId)
            is HomeTileTargetRef.VirtualCollection ->
                base.copy(virtualType = target.type, virtualName = target.name)
            is HomeTileTargetRef.App -> base.copy(packageName = target.packageName)
            HomeTileTargetRef.Unresolvable -> base
        }
    }

    companion object {
        const val DEFAULT_PAGE_COUNT = 2
    }
}

private fun HomeTileTargetRef.storedType(): String = when (this) {
    is HomeTileTargetRef.Game -> HomeTileTarget.GAME.name
    is HomeTileTargetRef.Collection -> HomeTileTarget.COLLECTION.name
    is HomeTileTargetRef.VirtualCollection -> HomeTileTarget.VIRTUAL_COLLECTION.name
    is HomeTileTargetRef.App -> HomeTileTarget.APP.name
    HomeTileTargetRef.Unresolvable -> ""
}

private fun HomeTileEntity.toDomain(): HomeTile = HomeTile(
    id = id,
    pageIndex = pageIndex,
    rect = TileRect(columnIndex, rowIndex, columnSpan, rowSpan),
    target = resolveTarget()
)

/**
 * A row whose target column is missing, or whose type this build does not know, resolves to
 * unresolvable rather than throwing: a tile written by a newer build should leave a visible gap on
 * an older one, not stop the page loading.
 */
private fun HomeTileEntity.resolveTarget(): HomeTileTargetRef =
    when (runCatching { HomeTileTarget.valueOf(targetType) }.getOrNull()) {
        HomeTileTarget.GAME -> gameId?.let { HomeTileTargetRef.Game(it) }
        HomeTileTarget.COLLECTION -> collectionId?.let { HomeTileTargetRef.Collection(it) }
        HomeTileTarget.VIRTUAL_COLLECTION -> virtualType?.let { type ->
            HomeTileTargetRef.VirtualCollection(type, virtualName.orEmpty())
        }
        HomeTileTarget.APP -> packageName?.let { HomeTileTargetRef.App(it) }
        null -> null
    } ?: HomeTileTargetRef.Unresolvable
