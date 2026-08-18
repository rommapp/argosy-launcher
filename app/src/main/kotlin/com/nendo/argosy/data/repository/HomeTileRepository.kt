package com.nendo.argosy.data.repository

import com.nendo.argosy.data.local.dao.HomeTileDao
import com.nendo.argosy.data.local.entity.HomeTileEntity
import com.nendo.argosy.data.local.entity.HomeTileEpisodeEntity
import com.nendo.argosy.data.local.entity.HomeTileTarget
import com.nendo.argosy.data.local.entity.MediaTilePlayMode
import com.nendo.argosy.domain.model.HomeTile
import com.nendo.argosy.domain.model.HomeTileTargetRef
import com.nendo.argosy.domain.model.TileRect
import com.nendo.argosy.domain.model.minimumSpanFor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
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

    /**
     * The stored tiles, each carrying whatever run was chosen for it.
     *
     * The two tables are read together rather than a tile being asked for its episodes when one is
     * drawn: a page holds a handful of tiles and the chosen runs are a handful of rows, so one join
     * on every emission is cheaper than a query per tile and, more importantly, keeps a tile and its
     * run arriving as one value instead of a tile that briefly has no run.
     */
    fun observeTiles(ownerUserId: Long?): Flow<List<HomeTile>> =
        combine(
            homeTileDao.observeTiles(ownerUserId),
            homeTileDao.observeAllEpisodes()
        ) { rows, episodes ->
            val runs = episodes.groupBy { it.tileId }
            rows.map { row ->
                row.toDomain(
                    runs[row.id].orEmpty().sortedBy { it.orderIndex }.map { it.itemId }
                )
            }
        }

    suspend fun pageCount(ownerUserId: Long?): Int =
        (homeTileDao.getMaxPageIndex(ownerUserId)?.plus(1) ?: 0).coerceAtLeast(DEFAULT_PAGE_COUNT)

    /**
     * Stores a tile and, when it was given one, the run it should play.
     *
     * [rect] is raised to whatever floor the target's kind imposes before it is written, so no caller
     * can leave a media tile at a size it is not allowed to be - including the ones that place from a
     * single cell and never mention a span at all.
     */
    suspend fun place(
        ownerUserId: Long?,
        pageIndex: Int,
        rect: TileRect,
        target: HomeTileTargetRef,
        playlist: List<String> = emptyList()
    ): Long {
        val id = homeTileDao.insert(entityFor(ownerUserId, pageIndex, rect, target))
        writePlaylist(id, playlist)
        return id
    }

    suspend fun move(
        tile: HomeTile,
        ownerUserId: Long?,
        rect: TileRect,
        pageIndex: Int = tile.pageIndex
    ) {
        homeTileDao.update(
            entityFor(ownerUserId, pageIndex, rect, tile.target).copy(id = tile.id)
        )
    }

    /**
     * Changes what a placed tile plays without moving it. The playlist is replaced, not merged.
     */
    suspend fun retarget(tile: HomeTile, ownerUserId: Long?, target: HomeTileTargetRef, playlist: List<String>) {
        homeTileDao.update(
            entityFor(ownerUserId, tile.pageIndex, tile.rect, target).copy(id = tile.id)
        )
        homeTileDao.replaceEpisodes(
            tile.id,
            playlist.mapIndexed { index, itemId ->
                HomeTileEpisodeEntity(tileId = tile.id, itemId = itemId, orderIndex = index)
            }
        )
    }

    suspend fun remove(tileId: Long) = homeTileDao.deleteTileWithEpisodes(tileId)

    private suspend fun writePlaylist(tileId: Long, playlist: List<String>) {
        if (playlist.isEmpty()) return
        homeTileDao.replaceEpisodes(
            tileId,
            playlist.mapIndexed { index, itemId ->
                HomeTileEpisodeEntity(tileId = tileId, itemId = itemId, orderIndex = index)
            }
        )
    }

    /**
     * Removes a page and closes the gap behind it. The tiles go; nothing they pointed at is
     * touched, which is why the confirmation this sits behind says the games stay on the device.
     */
    suspend fun removePage(ownerUserId: Long?, pageIndex: Int) {
        homeTileDao.deleteEpisodesForPage(ownerUserId, pageIndex)
        homeTileDao.deletePage(ownerUserId, pageIndex)
        homeTileDao.shiftPagesDown(ownerUserId, pageIndex)
    }

    suspend fun pruneMissingGames() = homeTileDao.deleteTilesForMissingGames()

    /**
     * Places [target] in the first cell the last page has free, reading left to right then down.
     *
     * The page shape depends on the display, and this runs where no display is in scope, so it
     * searches the anchors already taken rather than a grid: a cell no tile claims is free on every
     * shape wide enough to contain it. A row that lands outside a narrower screen is trimmed back by
     * the placement pass on read, which is the same recovery an edited page gets.
     */
    suspend fun appendToLastPage(
        ownerUserId: Long?,
        target: HomeTileTargetRef,
        columns: Int,
        playlist: List<String> = emptyList()
    ): Long? {
        val pageIndex = (homeTileDao.getMaxPageIndex(ownerUserId) ?: 0).coerceAtLeast(0)
        val taken = homeTileDao.getPage(ownerUserId, pageIndex)
            .flatMap { tile ->
                (tile.columnIndex until tile.columnIndex + tile.columnSpan).flatMap { column ->
                    (tile.rowIndex until tile.rowIndex + tile.rowSpan).map { row -> column to row }
                }
            }
            .toSet()
        val lanes = columns.coerceAtLeast(1)
        var row = 0
        while (row < MAX_APPEND_ROWS) {
            for (column in 0 until lanes) {
                if (column to row !in taken) {
                    return place(ownerUserId, pageIndex, TileRect(column, row), target, playlist)
                }
            }
            row++
        }
        return null
    }

    private fun entityFor(
        ownerUserId: Long?,
        pageIndex: Int,
        rect: TileRect,
        target: HomeTileTargetRef
    ): HomeTileEntity {
        val sized = rect.atLeast(minimumSpanFor(target))
        val base = HomeTileEntity(
            ownerUserId = ownerUserId,
            pageIndex = pageIndex,
            columnIndex = sized.columnIndex,
            rowIndex = sized.rowIndex,
            columnSpan = sized.columnSpan,
            rowSpan = sized.rowSpan,
            targetType = target.storedType()
        )
        return when (target) {
            is HomeTileTargetRef.Game -> base.copy(gameId = target.gameId)
            is HomeTileTargetRef.Collection -> base.copy(
                collectionId = target.collectionId,
                gameId = target.focusGameId
            )
            is HomeTileTargetRef.VirtualCollection ->
                base.copy(virtualType = target.type, virtualName = target.name)
            is HomeTileTargetRef.App -> base.copy(packageName = target.packageName)
            is HomeTileTargetRef.Media -> base.copy(
                mediaItemId = target.itemId,
                mediaPlayMode = target.playMode.name,
                mediaScopeId = target.scopeId
            )
            is HomeTileTargetRef.LocalMedia -> base.copy(mediaFilePath = target.filePath)
            HomeTileTargetRef.Unresolvable -> base
        }
    }

    companion object {
        const val DEFAULT_PAGE_COUNT = 2
        private const val MAX_APPEND_ROWS = 64
    }
}

private fun HomeTileTargetRef.storedType(): String = when (this) {
    is HomeTileTargetRef.Game -> HomeTileTarget.GAME.name
    is HomeTileTargetRef.Collection -> HomeTileTarget.COLLECTION.name
    is HomeTileTargetRef.VirtualCollection -> HomeTileTarget.VIRTUAL_COLLECTION.name
    is HomeTileTargetRef.App -> HomeTileTarget.APP.name
    is HomeTileTargetRef.Media -> HomeTileTarget.MEDIA.name
    is HomeTileTargetRef.LocalMedia -> HomeTileTarget.MEDIA.name
    HomeTileTargetRef.Unresolvable -> ""
}

private fun HomeTileEntity.toDomain(playlist: List<String>): HomeTile = HomeTile(
    id = id,
    pageIndex = pageIndex,
    rect = TileRect(columnIndex, rowIndex, columnSpan, rowSpan),
    target = resolveTarget(),
    playlist = playlist
)

/**
 * A row whose target column is missing, or whose type this build does not know, resolves to
 * unresolvable rather than throwing: a tile written by a newer build should leave a visible gap on
 * an older one, not stop the page loading.
 *
 * A media row naming a file on this device is read as that file before it is read as a library
 * title. The two share a stored type because both are media and only one of the two columns is ever
 * filled, so the path is what distinguishes them rather than a type an older build could not name.
 *
 * A play mode this build cannot read falls back to the single-title reading rather than making the
 * whole tile unresolvable: the item it points at is still known, and playing that is a better answer
 * than a gap on the page.
 */
private fun HomeTileEntity.resolveTarget(): HomeTileTargetRef =
    when (runCatching { HomeTileTarget.valueOf(targetType) }.getOrNull()) {
        HomeTileTarget.GAME -> gameId?.let { HomeTileTargetRef.Game(it) }
        HomeTileTarget.COLLECTION -> collectionId?.let {
            HomeTileTargetRef.Collection(it, focusGameId = gameId)
        }
        HomeTileTarget.VIRTUAL_COLLECTION -> virtualType?.let { type ->
            HomeTileTargetRef.VirtualCollection(type, virtualName.orEmpty())
        }
        HomeTileTarget.APP -> packageName?.let { HomeTileTargetRef.App(it) }
        HomeTileTarget.MEDIA -> mediaFilePath?.takeIf { it.isNotBlank() }
            ?.let { HomeTileTargetRef.LocalMedia(it) }
            ?: mediaItemId?.let {
                HomeTileTargetRef.Media(
                    itemId = it,
                    playMode = MediaTilePlayMode.fromStored(mediaPlayMode)
                        ?: MediaTilePlayMode.SINGLE,
                    scopeId = mediaScopeId
                )
            }
        null -> null
    } ?: HomeTileTargetRef.Unresolvable
