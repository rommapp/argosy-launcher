package com.nendo.argosy.data.repository

import com.nendo.argosy.data.local.dao.HomeTileDao
import com.nendo.argosy.data.local.entity.HomeTileEntity
import com.nendo.argosy.data.local.entity.HomeTileEpisodeEntity
import com.nendo.argosy.data.local.entity.HomeTileTarget
import com.nendo.argosy.data.local.entity.MediaTilePlayMode
import com.nendo.argosy.data.model.ActiveSort
import com.nendo.argosy.data.model.SortOption
import com.nendo.argosy.data.model.SourceFilter
import com.nendo.argosy.domain.model.CustomGridLayout
import com.nendo.argosy.domain.model.CustomGridShape
import com.nendo.argosy.domain.model.FOLDER_MEMBER_PAGE_INDEX
import com.nendo.argosy.domain.model.FeatureTileKind
import com.nendo.argosy.domain.model.FolderAccent
import com.nendo.argosy.domain.model.FolderAppearance
import com.nendo.argosy.domain.model.FolderBackdropStyle
import com.nendo.argosy.domain.model.FolderCoverStyle
import com.nendo.argosy.domain.model.FolderMemberSize
import com.nendo.argosy.domain.model.HomeGridKind
import com.nendo.argosy.domain.model.HomeTile
import com.nendo.argosy.domain.model.HomeTileTargetRef
import com.nendo.argosy.domain.model.LibraryLinkFilters
import com.nendo.argosy.domain.model.PlayerCountBucket
import com.nendo.argosy.domain.model.RaProgressFilter
import com.nendo.argosy.domain.model.RandomTileFilters
import com.nendo.argosy.domain.model.StickerOutline
import com.nendo.argosy.domain.model.StickerOutlineColor
import com.nendo.argosy.domain.model.StickerOutlineWidth
import com.nendo.argosy.domain.model.TileArtSlot
import com.nendo.argosy.domain.model.TileCoverScale
import com.nendo.argosy.domain.model.TileImageSource
import com.nendo.argosy.domain.model.TileRect
import com.nendo.argosy.domain.model.firstFreeScrollRect
import com.nendo.argosy.domain.model.gridKindFor
import com.nendo.argosy.domain.model.minimumSpanFor
import com.nendo.argosy.domain.model.reflowScrollTiles
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import org.json.JSONArray
import org.json.JSONObject
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
    fun observeTiles(ownerUserId: Long?, kind: HomeGridKind): Flow<List<HomeTile>> =
        combine(
            homeTileDao.observeTiles(ownerUserId, kind.name),
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
        (homeTileDao.getMaxPageIndex(ownerUserId, HomeGridKind.PAGED.name)?.plus(1) ?: 0)
            .coerceAtLeast(DEFAULT_PAGE_COUNT)

    /**
     * Stores a tile on the [kind] grid and, when it was given one, the run it should play. [rect] is
     * raised to the floor the target's kind imposes before it is written. A scrolling grid has no
     * pages, so its tiles are stored on page 0 whatever [pageIndex] says.
     */
    suspend fun place(
        ownerUserId: Long?,
        kind: HomeGridKind,
        pageIndex: Int,
        rect: TileRect,
        target: HomeTileTargetRef,
        playlist: List<String> = emptyList()
    ): Long {
        val id = homeTileDao.insert(entityFor(ownerUserId, kind, pageIndex, rect, target))
        writePlaylist(id, playlist)
        return id
    }

    /**
     * Rewrites only the stored row's page and rectangle; every other column keeps its stored value.
     * [rect] is raised to the target's minimum span, and a scrolling tile stays on page 0.
     */
    suspend fun move(
        tile: HomeTile,
        rect: TileRect,
        pageIndex: Int = tile.pageIndex
    ) {
        val row = homeTileDao.getById(tile.id) ?: return
        homeTileDao.update(row.placedAt(pageIndex, rect.atLeast(tile.minSpan)))
    }

    /**
     * Repacks the scrolling grid's tiles from [from] onto [to] and writes every moved row in one
     * update. Paged tiles are never read or written, and nothing happens unless both layouts scroll
     * and differ in scroll axis or lane count.
     */
    suspend fun reflowScroll(ownerUserId: Long?, from: CustomGridLayout, to: CustomGridLayout) {
        if (!from.needsScrollReflowTo(to)) return
        val rows = homeTileDao.getPage(ownerUserId, HomeGridKind.SCROLL.name, 0)
        val stored = rows.associateBy { it.id }
        val moved = reflowScrollTiles(rows.map { it.toDomain(emptyList()) }, from, to)
            .mapNotNull { tile ->
                stored[tile.id]?.takeIf { it.rect() != tile.rect }?.placedAt(0, tile.rect)
            }
        if (moved.isNotEmpty()) homeTileDao.updateAll(moved)
    }

    suspend fun setCoverScale(tileId: Long, scale: TileCoverScale) {
        val row = homeTileDao.getById(tileId) ?: return
        homeTileDao.update(row.copy(coverScale = scale.name))
    }

    /**
     * Changes what a stored tile points at, reading its page, rectangle and cover scale from the
     * stored row. The span only grows when the new target's minimum demands it. The playlist is
     * replaced, not merged.
     */
    suspend fun retarget(tileId: Long, target: HomeTileTargetRef, playlist: List<String>) {
        val row = homeTileDao.getById(tileId) ?: return
        homeTileDao.update(
            entityFor(
                ownerUserId = row.ownerUserId,
                kind = gridKindOf(row),
                pageIndex = row.pageIndex,
                rect = row.rect(),
                target = target,
                coverScale = TileCoverScale.fromString(row.coverScale)
            ).copy(
                id = tileId,
                pageId = row.pageId,
                artStyle = row.artStyle,
                createdAt = row.createdAt,
                parentTileId = row.parentTileId,
                pageIndex = row.pageIndex
            )
        )
        homeTileDao.replaceEpisodes(
            tileId,
            playlist.mapIndexed { index, itemId ->
                HomeTileEpisodeEntity(tileId = tileId, itemId = itemId, orderIndex = index)
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
        val kind = HomeGridKind.PAGED.name
        homeTileDao.deleteFolderMemberEpisodesForPage(ownerUserId, kind, pageIndex)
        homeTileDao.deleteFolderMembersForPage(ownerUserId, kind, pageIndex)
        homeTileDao.deleteEpisodesForPage(ownerUserId, kind, pageIndex)
        homeTileDao.deletePage(ownerUserId, kind, pageIndex)
        homeTileDao.shiftPagesDown(ownerUserId, kind, pageIndex)
    }

    /**
     * Puts a grid tile inside a hand-filled folder, after its last member. Answers false and writes
     * nothing when [folderId] is not a hand-filled folder on the grid, or when the tile is itself a
     * folder, since folders nest one level deep.
     */
    suspend fun moveIntoFolder(tileId: Long, folderId: Long): Boolean {
        if (tileId == folderId) return false
        val folder = homeTileDao.getById(folderId) ?: return false
        val folderTarget = folder.resolveTarget() as? HomeTileTargetRef.Folder ?: return false
        if (folderTarget.rule != null || folder.parentTileId != null) return false
        val row = homeTileDao.getById(tileId) ?: return false
        if (row.resolveTarget() is HomeTileTargetRef.Folder) return false
        val order = (homeTileDao.getFolderMembers(folderId).maxOfOrNull { it.columnIndex } ?: -1) + 1
        homeTileDao.update(
            row.copy(
                parentTileId = folderId,
                gridKind = folder.gridKind,
                pageIndex = FOLDER_MEMBER_PAGE_INDEX,
                columnIndex = order,
                rowIndex = 0
            )
        )
        return true
    }

    /**
     * Takes a member out of its folder and onto the first free spot of the folder's page on the grid
     * [layout] describes, falling back to the last page and then a new one.
     */
    suspend fun removeFromFolder(tileId: Long, layout: CustomGridLayout) {
        val row = homeTileDao.getById(tileId) ?: return
        val folderId = row.parentTileId ?: return
        val page = homeTileDao.getById(folderId)?.pageIndex ?: 0
        releaseToGrid(row, layout, page)
    }

    /**
     * Returns every member of a folder to the grid in folder order, then deletes the folder tile.
     */
    suspend fun deleteFolder(folderId: Long, layout: CustomGridLayout) {
        val folder = homeTileDao.getById(folderId) ?: return
        homeTileDao.getFolderMembers(folderId).forEach { releaseToGrid(it, layout, folder.pageIndex) }
        homeTileDao.deleteTileWithEpisodes(folderId)
    }

    private suspend fun releaseToGrid(row: HomeTileEntity, layout: CustomGridLayout, page: Int) {
        val (pageIndex, rect) = freeSpot(row.ownerUserId, layout, page, minimumSpanFor(row.resolveTarget()))
        homeTileDao.update(
            row.copy(parentTileId = null, gridKind = gridKindFor(layout.scrollAxis).name)
                .placedAt(pageIndex, rect)
        )
    }

    private suspend fun freeSpot(
        ownerUserId: Long?,
        layout: CustomGridLayout,
        page: Int,
        span: Int
    ): Pair<Int, TileRect> {
        val axis = layout.scrollAxis
        if (axis != null) {
            val taken = homeTileDao.getPage(ownerUserId, HomeGridKind.SCROLL.name, 0).map { it.rect() }
            return 0 to firstFreeScrollRect(taken, layout.lanes, axis, span, span)
        }
        val kind = HomeGridKind.PAGED.name
        val start = page.coerceAtLeast(0)
        firstFreeRect(homeTileDao.getPage(ownerUserId, kind, start).map { it.rect() }, layout.shape, span)
            ?.let { return start to it }
        val lastPage = (homeTileDao.getMaxPageIndex(ownerUserId, kind) ?: 0).coerceAtLeast(start)
        if (lastPage != start) {
            firstFreeRect(homeTileDao.getPage(ownerUserId, kind, lastPage).map { it.rect() }, layout.shape, span)
                ?.let { return lastPage to it }
        }
        return lastPage + 1 to TileRect(0, 0).atLeast(span)
    }

    suspend fun pruneMissingGames() = homeTileDao.deleteTilesForMissingGames()

    /**
     * Places [target] on the grid [layout] describes. A paged grid takes the first free spot of its
     * last page, reading left to right then down, or the top-left of a new page when that page is
     * full. A scrolling grid takes the first free spot scanning along its scroll axis.
     */
    suspend fun append(
        ownerUserId: Long?,
        target: HomeTileTargetRef,
        layout: CustomGridLayout,
        playlist: List<String> = emptyList()
    ): Long {
        val lastPage = if (layout.scrollAxis == null) {
            (homeTileDao.getMaxPageIndex(ownerUserId, HomeGridKind.PAGED.name) ?: 0).coerceAtLeast(0)
        } else {
            0
        }
        val (page, free) = freeSpot(ownerUserId, layout, lastPage, minimumSpanFor(target))
        return place(ownerUserId, gridKindFor(layout.scrollAxis), page, free, target, playlist)
    }

    private fun entityFor(
        ownerUserId: Long?,
        kind: HomeGridKind,
        pageIndex: Int,
        rect: TileRect,
        target: HomeTileTargetRef,
        coverScale: TileCoverScale = TileCoverScale.CROP
    ): HomeTileEntity {
        val sized = rect.atLeast(minimumSpanFor(target))
        val base = HomeTileEntity(
            ownerUserId = ownerUserId,
            gridKind = kind.name,
            pageIndex = if (kind == HomeGridKind.SCROLL) 0 else pageIndex,
            coverScale = coverScale.name,
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
            is HomeTileTargetRef.Feature -> base.copy(
                featureKind = target.kind.name,
                featureConfig = encodeFeatureConfig(target)
            )
            is HomeTileTargetRef.Folder -> base.copy(featureConfig = encodeFolder(target))
            is HomeTileTargetRef.Image -> base.copy(featureConfig = encodeDecor(target))
            is HomeTileTargetRef.Sticker -> base.copy(featureConfig = encodeDecor(target))
            HomeTileTargetRef.Unresolvable -> base
        }
    }

    suspend fun updateFeaturePick(tileId: Long, gameId: Long?) {
        val row = homeTileDao.getById(tileId) ?: return
        val target = row.resolveTarget() as? HomeTileTargetRef.Feature ?: return
        homeTileDao.update(
            row.copy(featureConfig = encodeFeatureConfig(target.copy(pickedGameId = gameId)))
        )
    }

    companion object {
        const val DEFAULT_PAGE_COUNT = 2
    }
}

private fun firstFreeRect(taken: List<TileRect>, shape: CustomGridShape, span: Int): TileRect? {
    for (row in 0 until shape.rows) {
        for (column in 0 until shape.columns) {
            val candidate = TileRect(column, row).atLeast(span)
            if (candidate.withinBounds(shape.columns, shape.rows) && taken.none { it.overlaps(candidate) }) {
                return candidate
            }
        }
    }
    return null
}

private fun HomeTileTargetRef.storedType(): String = when (this) {
    is HomeTileTargetRef.Game -> HomeTileTarget.GAME.name
    is HomeTileTargetRef.Collection -> HomeTileTarget.COLLECTION.name
    is HomeTileTargetRef.VirtualCollection -> HomeTileTarget.VIRTUAL_COLLECTION.name
    is HomeTileTargetRef.App -> HomeTileTarget.APP.name
    is HomeTileTargetRef.Media -> HomeTileTarget.MEDIA.name
    is HomeTileTargetRef.LocalMedia -> HomeTileTarget.MEDIA.name
    is HomeTileTargetRef.Feature -> HomeTileTarget.FEATURE.name
    is HomeTileTargetRef.Folder -> HomeTileTarget.FOLDER.name
    is HomeTileTargetRef.Image -> HomeTileTarget.IMAGE.name
    is HomeTileTargetRef.Sticker -> HomeTileTarget.STICKER.name
    HomeTileTargetRef.Unresolvable -> ""
}

private const val KEY_DOWNLOADED_ONLY = "downloadedOnly"
private const val KEY_NEVER_PLAYED = "neverPlayed"
private const val KEY_PLATFORM_IDS = "platformIds"
private const val KEY_GENRES = "genres"
private const val KEY_PICKED_GAME_ID = "pickedGameId"
private const val KEY_SERIES = "series"
private const val KEY_SOURCE = "source"
private const val KEY_PLAYERS = "players"
private const val KEY_SORT = "sort"
private const val KEY_SORT_DESCENDING = "sortDescending"
private const val KEY_DEVELOPERS = "developers"
private const val KEY_YEAR_FROM = "releaseYearFrom"
private const val KEY_YEAR_TO = "releaseYearTo"
private const val KEY_PLAY_STATUSES = "playStatuses"
private const val KEY_ADDED_WITHIN_DAYS = "addedWithinDays"
private const val KEY_RA_PROGRESS = "raProgress"
private const val KEY_NAME = "name"
private const val KEY_RULE = "rule"
private const val KEY_IMAGE_SOURCE = "imageSource"
private const val KEY_IMAGE_PATH = "path"
private const val KEY_IMAGE_GAME_ID = "gameId"
private const val KEY_IMAGE_SLOT = "slot"
private const val KEY_OUTLINE = "outline"
private const val KEY_OUTLINE_WIDTH = "width"
private const val KEY_OUTLINE_COLOR = "color"
private const val IMAGE_SOURCE_FILE = "FILE"
private const val IMAGE_SOURCE_GAME_ART = "GAME_ART"

private const val KEY_APPEARANCE = "appearance"
private const val KEY_COVER = "cover"
private const val KEY_COVER_SOURCE = "coverSource"
private const val KEY_COVER_OUTLINE = "coverOutline"
private const val KEY_SHOW_NAME = "showName"
private const val KEY_BACKDROP = "backdrop"
private const val KEY_BACKDROP_SOURCE = "backdropSource"
private const val KEY_ACCENT = "accent"
private const val KEY_MEMBER_SIZE = "memberSize"

private fun encodeFolder(target: HomeTileTargetRef.Folder): String =
    JSONObject().apply {
        put(KEY_NAME, target.name)
        target.rule?.let { put(KEY_RULE, JSONObject(encodeLibraryLink(it))) }
        if (target.appearance != FolderAppearance()) put(KEY_APPEARANCE, encodeAppearance(target.appearance))
    }.toString()

private fun decodeFolder(config: String?): HomeTileTargetRef.Folder {
    val json = config?.let { runCatching { JSONObject(it) }.getOrNull() }
        ?: return HomeTileTargetRef.Folder(name = "")
    return HomeTileTargetRef.Folder(
        name = json.optString(KEY_NAME),
        rule = json.optJSONObject(KEY_RULE)?.let(::decodeLibraryLink),
        appearance = json.optJSONObject(KEY_APPEARANCE)?.let(::decodeAppearance) ?: FolderAppearance()
    )
}

private fun encodeAppearance(appearance: FolderAppearance): JSONObject = JSONObject().apply {
    put(KEY_COVER, appearance.cover.name)
    appearance.coverSource?.let { source -> put(KEY_COVER_SOURCE, JSONObject().also { encodeImageSource(source, it) }) }
    put(KEY_COVER_OUTLINE, encodeOutline(appearance.coverOutline))
    put(KEY_SHOW_NAME, appearance.showName)
    put(KEY_BACKDROP, appearance.backdrop.name)
    appearance.backdropSource?.let { source ->
        put(KEY_BACKDROP_SOURCE, JSONObject().also { encodeImageSource(source, it) })
    }
    put(KEY_ACCENT, appearance.accent.name)
    put(KEY_MEMBER_SIZE, appearance.memberSize.name)
}

private fun decodeAppearance(json: JSONObject): FolderAppearance = FolderAppearance(
    cover = FolderCoverStyle.fromStored(json.optString(KEY_COVER)),
    coverSource = json.optJSONObject(KEY_COVER_SOURCE)?.let(::decodeImageSource),
    coverOutline = decodeOutline(json.optJSONObject(KEY_COVER_OUTLINE)),
    showName = json.optBoolean(KEY_SHOW_NAME, true),
    backdrop = FolderBackdropStyle.fromStored(json.optString(KEY_BACKDROP)),
    backdropSource = json.optJSONObject(KEY_BACKDROP_SOURCE)?.let(::decodeImageSource),
    accent = FolderAccent.fromStored(json.optString(KEY_ACCENT)),
    memberSize = FolderMemberSize.fromStored(json.optString(KEY_MEMBER_SIZE))
)

private fun encodeOutline(outline: StickerOutline): JSONObject = JSONObject().apply {
    put(KEY_OUTLINE_ENABLED, outline.enabled)
    put(KEY_OUTLINE_WIDTH, outline.width.name)
    put(KEY_OUTLINE_COLOR, outline.color.name)
}

private fun decodeOutline(json: JSONObject?): StickerOutline = StickerOutline(
    enabled = json?.optBoolean(KEY_OUTLINE_ENABLED, false) ?: false,
    width = StickerOutlineWidth.fromStored(json?.optString(KEY_OUTLINE_WIDTH)),
    color = StickerOutlineColor.fromStored(json?.optString(KEY_OUTLINE_COLOR))
)

private fun encodeImageSource(source: TileImageSource, into: JSONObject) {
    when (source) {
        is TileImageSource.File -> {
            into.put(KEY_IMAGE_SOURCE, IMAGE_SOURCE_FILE)
            into.put(KEY_IMAGE_PATH, source.path)
        }
        is TileImageSource.GameArt -> {
            into.put(KEY_IMAGE_SOURCE, IMAGE_SOURCE_GAME_ART)
            into.put(KEY_IMAGE_GAME_ID, source.gameId)
            into.put(KEY_IMAGE_SLOT, source.slot.name)
        }
    }
}

private fun decodeImageSource(json: JSONObject): TileImageSource? =
    when (json.optString(KEY_IMAGE_SOURCE)) {
        IMAGE_SOURCE_FILE -> json.optString(KEY_IMAGE_PATH).takeIf { it.isNotBlank() }
            ?.let { TileImageSource.File(it) }
        IMAGE_SOURCE_GAME_ART -> {
            val slot = TileArtSlot.fromStored(json.optString(KEY_IMAGE_SLOT))
            if (json.has(KEY_IMAGE_GAME_ID) && slot != null) {
                TileImageSource.GameArt(json.getLong(KEY_IMAGE_GAME_ID), slot)
            } else {
                null
            }
        }
        else -> null
    }

private fun encodeDecor(target: HomeTileTargetRef): String = JSONObject().apply {
    when (target) {
        is HomeTileTargetRef.Image -> encodeImageSource(target.source, this)
        is HomeTileTargetRef.Sticker -> {
            encodeImageSource(target.source, this)
            put(KEY_OUTLINE, encodeOutline(target.outline))
        }
        else -> Unit
    }
}.toString()

private const val KEY_OUTLINE_ENABLED = "enabled"

private fun decodeImage(config: String?): HomeTileTargetRef.Image? {
    val json = config?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return null
    return decodeImageSource(json)?.let { HomeTileTargetRef.Image(it) }
}

private fun decodeSticker(config: String?): HomeTileTargetRef.Sticker? {
    val json = config?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return null
    val source = decodeImageSource(json) ?: return null
    return HomeTileTargetRef.Sticker(source = source, outline = decodeOutline(json.optJSONObject(KEY_OUTLINE)))
}

private fun encodeFeatureConfig(target: HomeTileTargetRef.Feature): String =
    if (target.kind == FeatureTileKind.LIBRARY_LINK) {
        encodeLibraryLink(target.libraryLink ?: LibraryLinkFilters())
    } else {
        JSONObject().apply {
            put(KEY_DOWNLOADED_ONLY, target.filters.downloadedOnly)
            put(KEY_NEVER_PLAYED, target.filters.neverPlayed)
            put(KEY_PLATFORM_IDS, JSONArray(target.filters.platformIds.toList()))
            put(KEY_GENRES, JSONArray(target.filters.genres.toList()))
            target.pickedGameId?.let { put(KEY_PICKED_GAME_ID, it) }
        }.toString()
    }

private fun encodeLibraryLink(filters: LibraryLinkFilters): String =
    JSONObject().apply {
        put(KEY_SOURCE, filters.source.name)
        put(KEY_PLATFORM_IDS, JSONArray(filters.platformIds.toList()))
        put(KEY_GENRES, JSONArray(filters.genres.toList()))
        put(KEY_SERIES, JSONArray(filters.series.toList()))
        filters.players?.let { put(KEY_PLAYERS, it.name) }
        put(KEY_SORT, filters.sort.option.name)
        put(KEY_SORT_DESCENDING, filters.sort.descending)
        if (filters.developers.isNotEmpty()) put(KEY_DEVELOPERS, JSONArray(filters.developers.toList()))
        filters.releaseYearFrom?.let { put(KEY_YEAR_FROM, it) }
        filters.releaseYearTo?.let { put(KEY_YEAR_TO, it) }
        if (filters.playStatuses.isNotEmpty()) put(KEY_PLAY_STATUSES, JSONArray(filters.playStatuses.toList()))
        filters.addedWithinDays?.let { put(KEY_ADDED_WITHIN_DAYS, it) }
        filters.raProgress?.let { put(KEY_RA_PROGRESS, it.name) }
    }.toString()

private fun decodeLibraryLink(json: JSONObject): LibraryLinkFilters {
    val sortOption = SortOption.entries.find { it.name == json.optString(KEY_SORT) }
        ?: SortOption.TITLE
    return LibraryLinkFilters(
        source = SourceFilter.fromString(json.optString(KEY_SOURCE)) ?: SourceFilter.ALL,
        platformIds = json.longSet(KEY_PLATFORM_IDS),
        genres = json.stringSet(KEY_GENRES),
        series = json.stringSet(KEY_SERIES),
        players = PlayerCountBucket.entries.find { it.name == json.optString(KEY_PLAYERS) },
        sort = ActiveSort(
            option = sortOption,
            descending = json.optBoolean(KEY_SORT_DESCENDING, sortOption.defaultDescending)
        ),
        developers = json.stringSet(KEY_DEVELOPERS),
        releaseYearFrom = json.optIntOrNull(KEY_YEAR_FROM),
        releaseYearTo = json.optIntOrNull(KEY_YEAR_TO),
        playStatuses = json.stringSet(KEY_PLAY_STATUSES),
        addedWithinDays = json.optIntOrNull(KEY_ADDED_WITHIN_DAYS),
        raProgress = RaProgressFilter.fromStored(json.optString(KEY_RA_PROGRESS))
    )
}

private fun JSONObject.optIntOrNull(key: String): Int? = if (has(key)) optInt(key) else null

private fun JSONObject.longSet(key: String): Set<Long> =
    optJSONArray(key)?.let { array -> (0 until array.length()).map { array.getLong(it) } }
        .orEmpty()
        .toSet()

private fun JSONObject.stringSet(key: String): Set<String> =
    optJSONArray(key)?.let { array -> (0 until array.length()).map { array.getString(it) } }
        .orEmpty()
        .toSet()

private fun decodeFeature(kind: FeatureTileKind, config: String?): HomeTileTargetRef.Feature {
    val json = config?.let { runCatching { JSONObject(it) }.getOrNull() }
        ?: return HomeTileTargetRef.Feature(
            kind,
            libraryLink = LibraryLinkFilters().takeIf { kind == FeatureTileKind.LIBRARY_LINK }
        )
    if (kind == FeatureTileKind.LIBRARY_LINK) {
        return HomeTileTargetRef.Feature(kind, libraryLink = decodeLibraryLink(json))
    }
    val platformIds = json.optJSONArray(KEY_PLATFORM_IDS)
        ?.let { array -> (0 until array.length()).map { array.getLong(it) } }
        .orEmpty()
        .toSet()
    val genres = json.optJSONArray(KEY_GENRES)
        ?.let { array -> (0 until array.length()).map { array.getString(it) } }
        .orEmpty()
        .toSet()
    return HomeTileTargetRef.Feature(
        kind = kind,
        filters = RandomTileFilters(
            downloadedOnly = json.optBoolean(KEY_DOWNLOADED_ONLY, true),
            neverPlayed = json.optBoolean(KEY_NEVER_PLAYED, false),
            platformIds = platformIds,
            genres = genres
        ),
        pickedGameId = if (json.has(KEY_PICKED_GAME_ID)) json.getLong(KEY_PICKED_GAME_ID) else null
    )
}

private fun HomeTileEntity.rect(): TileRect = TileRect(columnIndex, rowIndex, columnSpan, rowSpan)

private fun HomeTileEntity.placedAt(pageIndex: Int, rect: TileRect): HomeTileEntity = copy(
    pageIndex = if (gridKindOf(this) == HomeGridKind.SCROLL) 0 else pageIndex,
    columnIndex = rect.columnIndex,
    rowIndex = rect.rowIndex,
    columnSpan = rect.columnSpan,
    rowSpan = rect.rowSpan
)

private fun gridKindOf(row: HomeTileEntity): HomeGridKind =
    HomeGridKind.entries.firstOrNull { it.name == row.gridKind } ?: HomeGridKind.PAGED

private fun HomeTileEntity.toDomain(playlist: List<String>): HomeTile = HomeTile(
    id = id,
    pageIndex = pageIndex,
    rect = rect(),
    target = resolveTarget(),
    playlist = playlist,
    coverScale = TileCoverScale.fromString(coverScale),
    kind = gridKindOf(this),
    parentTileId = parentTileId
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
        HomeTileTarget.FEATURE -> FeatureTileKind.fromStored(featureKind)?.let { kind ->
            decodeFeature(kind, featureConfig)
        }
        HomeTileTarget.FOLDER -> decodeFolder(featureConfig)
        HomeTileTarget.IMAGE -> decodeImage(featureConfig)
        HomeTileTarget.STICKER -> decodeSticker(featureConfig)
        null -> null
    } ?: HomeTileTargetRef.Unresolvable
