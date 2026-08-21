package com.nendo.argosy.data.remote.jellyfin

import androidx.room.withTransaction
import com.nendo.argosy.data.local.ALauncherDatabase
import com.nendo.argosy.data.local.dao.MediaCreditDao
import com.nendo.argosy.data.local.dao.MediaItemDao
import com.nendo.argosy.data.local.dao.MediaLibraryDao
import com.nendo.argosy.data.local.dao.MediaUserDataDao
import com.nendo.argosy.data.local.entity.MediaCollectionType
import com.nendo.argosy.data.local.entity.MediaCreditEntity
import com.nendo.argosy.data.local.entity.MediaItemEntity
import com.nendo.argosy.data.local.entity.MediaItemType
import com.nendo.argosy.data.local.entity.MediaLibraryEntity
import com.nendo.argosy.data.local.entity.MediaUserDataEntity
import com.nendo.argosy.util.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "JellyfinLibrarySyncService"
private const val SYNC_RESUME_TTL_MINUTES = 30L

/**
 * SQLite binds each element of an `IN` list as its own variable and refuses past 999 of them, so a
 * library that shrank by more than that would throw rather than prune.
 */
private const val SQL_VARIABLE_LIMIT = 900
private const val HTTP_NOT_FOUND = 404

/**
 * Pulls the media library onto disk.
 *
 * Series and seasons are fetched eagerly; episodes are not. On the reference instance episodes are
 * 7,716 of 8,030 items, and the rails that need an episode before its season is opened - Next Up and
 * Continue Watching - are server endpoints that already answer with the whole episode. Fetching them
 * all up front would be almost the entire sync cost for data that is only read a season at a time.
 *
 * Nothing here fetches an image. Image addresses are content-addressed by tag, so they are built on
 * demand and cached by address; syncing them would be storing a URL that can be derived.
 */
@Singleton
class JellyfinLibrarySyncService @Inject constructor(
    private val apiClient: JellyfinApiClient,
    private val connectionManager: JellyfinConnectionManager,
    private val database: ALauncherDatabase,
    private val mediaLibraryDao: MediaLibraryDao,
    private val mediaItemDao: MediaItemDao,
    private val mediaCreditDao: MediaCreditDao,
    private val mediaUserDataDao: MediaUserDataDao
) {
    private val syncMutex = Mutex()
    private val pushMutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _syncProgress = MutableStateFlow(JellyfinSyncProgress())
    val syncProgress: StateFlow<JellyfinSyncProgress> = _syncProgress.asStateFlow()

    /**
     * Watch state recorded while the server could not be told is drained the moment one can be
     * reached again. Without this the queue only moves when the user happens to open the media
     * library, and until it moves the server's own state for those items is refused as the local
     * write is still unreported.
     */
    init {
        scope.launch {
            connectionManager.connectionState
                .map { it is JellyfinConnectionState.Connected }
                .distinctUntilChanged()
                .filter { it }
                .collect { pushPendingUserData() }
        }
    }

    private data class LibraryCheckpoint(val startIndex: Int, val recordedAt: Instant)

    /**
     * Where the last interrupted pass of each library got to.
     *
     * The offset is only meaningful against a stable ordering, which is why every page is requested
     * sorted by SortName ascending. It is process-local and expires: a stale offset would silently
     * skip the head of a library that has been re-scanned server-side, so anything older than the
     * TTL restarts from the beginning rather than resuming into a different list.
     */
    private val checkpoints = mutableMapOf<String, LibraryCheckpoint>()

    suspend fun syncLibraries(): JellyfinResult<JellyfinSyncResult> = syncMutex.withLock {
        withContext(Dispatchers.IO) { runLibrarySync() }
    }

    private suspend fun runLibrarySync(): JellyfinResult<JellyfinSyncResult> {
        val owner = connectionManager.getUserId()
            ?: return JellyfinResult.Error("Not signed in")
        if (!apiClient.getCapabilities().isSupportedVersion) {
            Logger.info(TAG, "syncing against a server below the verified floor")
        }

        _syncProgress.value = JellyfinSyncProgress(isSyncing = true)
        val errors = mutableListOf<String>()
        var itemsAdded = 0
        var itemsRemoved = 0

        val views = when (val result = apiClient.getUserViews(owner)) {
            is JellyfinResult.Success -> result.data.items
            is JellyfinResult.Error -> {
                _syncProgress.value = JellyfinSyncProgress()
                return JellyfinResult.Error(result.message, result.code)
            }
        }

        val libraries = views.mapIndexedNotNull { order, view -> view.toLibraryEntity(owner, order) }
        persistLibraries(owner, libraries)
        _syncProgress.value = _syncProgress.value.copy(librariesTotal = libraries.size)

        for ((index, library) in libraries.withIndex()) {
            _syncProgress.value = _syncProgress.value.copy(
                currentLibrary = library.name,
                librariesDone = index
            )
            when (val outcome = syncOneLibrary(owner, library)) {
                is JellyfinResult.Success -> {
                    itemsAdded += outcome.data.itemsAdded
                    itemsRemoved += outcome.data.itemsRemoved
                }
                is JellyfinResult.Error -> errors += "${library.name}: ${outcome.message}"
            }
        }

        _syncProgress.value = JellyfinSyncProgress()
        return JellyfinResult.Success(
            JellyfinSyncResult(
                librariesSynced = libraries.size - errors.size,
                itemsAdded = itemsAdded,
                itemsRemoved = itemsRemoved,
                errors = errors
            )
        )
    }

    private suspend fun syncOneLibrary(
        owner: String,
        library: MediaLibraryEntity
    ): JellyfinResult<JellyfinSyncResult> {
        val collectionType = MediaCollectionType.fromWire(library.collectionType)
        val itemType = when (collectionType) {
            MediaCollectionType.MOVIES -> MediaItemType.MOVIE
            MediaCollectionType.TV_SHOWS -> MediaItemType.SERIES
            null -> return JellyfinResult.Error("Library type ${library.collectionType} is not supported")
        }

        val seenTopLevel = mutableSetOf<String>()
        val seenSeasons = mutableSetOf<String>()
        val firstIndex = resumeOffsetFor(library.libraryId)
        var startIndex = firstIndex
        var added = 0
        var everySeasonAnswered = true

        while (true) {
            val params = apiClient.buildItemQueryParams(
                userId = owner,
                parentId = library.libraryId,
                includeItemTypes = itemType.wireValue,
                startIndex = startIndex,
                limit = JellyfinApiClient.DEFAULT_PAGE_SIZE,
                fields = JellyfinApiClient.TITLE_FIELDS
            )
            val page = when (val result = apiClient.getItems(params)) {
                is JellyfinResult.Success -> result.data
                is JellyfinResult.Error -> {
                    recordCheckpoint(library.libraryId, startIndex)
                    return JellyfinResult.Error(result.message, result.code)
                }
            }
            if (page.items.isEmpty()) break

            val entities = page.items.mapNotNull { it.toItemEntity(owner, library.libraryId) }
            persistItems(owner, entities)
            persistCredits(owner, page.items)
            persistUserData(owner, page.items)
            entities.forEach { seenTopLevel += it.itemId }
            added += entities.size

            if (itemType == MediaItemType.SERIES) {
                for (series in entities) {
                    when (val outcome = syncSeasons(owner, library.libraryId, series.itemId)) {
                        is JellyfinResult.Success -> seenSeasons += outcome.data
                        is JellyfinResult.Error -> {
                            everySeasonAnswered = false
                            Logger.info(TAG, "seasons for ${series.itemId}: ${outcome.message}")
                        }
                    }
                }
            }

            startIndex += page.items.size
            _syncProgress.value = _syncProgress.value.copy(
                itemsTotal = page.totalRecordCount ?: startIndex,
                itemsDone = startIndex
            )
            if (reachedLastPage(page, startIndex)) break
        }

        clearCheckpoint(library.libraryId)
        val removed = if (firstIndex == 0) {
            pruneMissing(owner, library.libraryId, itemType, seenTopLevel) +
                pruneSeasons(owner, library, itemType, seenSeasons, everySeasonAnswered)
        } else {
            Logger.info(TAG, "resumed pass on ${library.libraryId} never saw the head; nothing pruned")
            0
        }
        mediaLibraryDao.markSynced(
            owner,
            library.libraryId,
            mediaItemDao.countByLibrary(owner, library.libraryId, itemType.wireValue),
            Instant.now()
        )

        return JellyfinResult.Success(
            JellyfinSyncResult(
                librariesSynced = 1,
                itemsAdded = added,
                itemsRemoved = removed,
                errors = emptyList()
            )
        )
    }

    /**
     * Whether the page just consumed was the last one.
     *
     * The record count answers it when the server sent one. When it did not, nothing here can prove
     * the library ends where the page does, so the walk keeps asking until a page comes back empty:
     * one wasted request buys proof, and the alternative is a guess that decides what gets deleted.
     */
    private fun reachedLastPage(page: JellyfinItemsResponse, consumed: Int): Boolean {
        val total = page.totalRecordCount ?: return false
        return consumed >= total
    }

    /**
     * Seasons are enumerated per series, so one series the server would not answer for leaves a hole
     * that is indistinguishable from a season that was deleted. A pass with any such hole prunes no
     * seasons at all rather than guessing which absences were real.
     */
    private suspend fun pruneSeasons(
        owner: String,
        library: MediaLibraryEntity,
        itemType: MediaItemType,
        seenSeasons: Set<String>,
        everySeasonAnswered: Boolean
    ): Int {
        if (itemType != MediaItemType.SERIES) return 0
        if (!everySeasonAnswered) {
            Logger.info(TAG, "a series would not answer for its seasons on ${library.libraryId}; none pruned")
            return 0
        }
        return pruneMissing(owner, library.libraryId, MediaItemType.SEASON, seenSeasons)
    }

    private suspend fun syncSeasons(
        owner: String,
        libraryId: String,
        seriesId: String
    ): JellyfinResult<Set<String>> {
        val params = apiClient.buildSeasonQueryParams(owner)
        val response = when (val result = apiClient.getSeasons(seriesId, params)) {
            is JellyfinResult.Success -> result.data
            is JellyfinResult.Error -> return JellyfinResult.Error(result.message, result.code)
        }
        val entities = response.items.mapNotNull { it.toItemEntity(owner, libraryId) }
        persistItems(owner, entities)
        persistUserData(owner, response.items)
        refileStoredEpisodes(owner, seriesId, JellyfinSeasonPlacement.of(entities))
        return JellyfinResult.Success(entities.map { it.itemId }.toSet())
    }

    /**
     * Puts episodes already on disk under the season their own number names.
     *
     * The number is stored on the row, so this is a local read and a local write: a library that was
     * synced while the reported season id was trusted is put right without fetching any of it again,
     * and a series opened with the server unreachable is corrected all the same. A row already where
     * it belongs is not rewritten, which is what stops this from re-triggering the queries observing
     * it.
     */
    private suspend fun refileStoredEpisodes(
        owner: String,
        seriesId: String,
        placement: JellyfinSeasonPlacement
    ) {
        val stored = mediaItemDao.getBySeries(owner, seriesId, MediaItemType.EPISODE.wireValue)
        val refiled = stored.mapNotNull { episode ->
            val parent = placement.parentFor(episode.parentIndexNumber, episode.parentId)
            episode.takeIf { parent != it.parentId }?.copy(parentId = parent)
        }
        if (refiled.isEmpty()) return
        mediaItemDao.insertAll(refiled)
        Logger.info(TAG, "refiled ${refiled.size} episodes of $seriesId under the season they number")
    }

    private suspend fun placementFor(owner: String, seriesId: String): JellyfinSeasonPlacement =
        JellyfinSeasonPlacement.of(mediaItemDao.getByParent(owner, seriesId))

    /**
     * Fetches one season's episodes, which is the lazy half of the strategy: a season is pulled the
     * first time it is opened and refreshed on each later visit.
     *
     * The whole series is refiled before anything is fetched, and deliberately not just the season
     * being opened. The endpoint answers for the directory as readily as for the season, so a request
     * for one season can come back carrying the entire run; every episode in the answer is filed by
     * its own number, which is also why a season that answers with more than its own no longer empties
     * the seasons beside it.
     */
    suspend fun syncSeasonEpisodes(
        seriesId: String,
        seasonId: String
    ): JellyfinResult<Int> = withContext(Dispatchers.IO) {
        val owner = connectionManager.getUserId()
            ?: return@withContext JellyfinResult.Error("Not signed in")
        val libraryId = mediaItemDao.getByItemId(owner, seriesId)?.libraryId
        val placement = placementFor(owner, seriesId)
        refileStoredEpisodes(owner, seriesId, placement)
        var startIndex = 0
        var stored = 0

        while (true) {
            val params = apiClient.buildEpisodeQueryParams(
                userId = owner,
                seasonId = seasonId,
                startIndex = startIndex,
                limit = JellyfinApiClient.DEFAULT_PAGE_SIZE
            )
            val page = when (val result = apiClient.getEpisodes(seriesId, params)) {
                is JellyfinResult.Success -> result.data
                is JellyfinResult.Error -> return@withContext JellyfinResult.Error(result.message, result.code)
            }
            if (page.items.isEmpty()) break

            val entities = page.items.mapNotNull { it.toItemEntity(owner, libraryId, placement) }
            persistItems(owner, entities)
            persistUserData(owner, page.items)
            stored += entities.size

            startIndex += page.items.size
            if (reachedLastPage(page, startIndex)) break
        }
        JellyfinResult.Success(stored)
    }

    /**
     * The Next Up rail. Its episodes arrive complete, and they routinely belong to a season that has
     * never been opened and so was never synced. They are stored anyway: the item table carries the
     * hierarchy as ids rather than as foreign keys precisely so an episode can exist before its
     * parents do.
     */
    suspend fun syncNextUp(limit: Int = JellyfinApiClient.DEFAULT_RAIL_SIZE): JellyfinResult<List<MediaItemEntity>> =
        withContext(Dispatchers.IO) {
            val owner = connectionManager.getUserId()
                ?: return@withContext JellyfinResult.Error("Not signed in")
            val params = apiClient.buildNextUpQueryParams(owner, limit)
            when (val result = apiClient.getNextUp(params)) {
                is JellyfinResult.Success -> {
                    val entities = toRailEntities(owner, result.data.items)
                    persistItems(owner, entities, narrowFields = true)
                    persistUserData(owner, result.data.items)
                    JellyfinResult.Success(entities)
                }
                is JellyfinResult.Error -> JellyfinResult.Error(result.message, result.code)
            }
        }

    suspend fun syncContinueWatching(
        limit: Int = JellyfinApiClient.DEFAULT_RAIL_SIZE
    ): JellyfinResult<List<MediaItemEntity>> = withContext(Dispatchers.IO) {
        val owner = connectionManager.getUserId()
            ?: return@withContext JellyfinResult.Error("Not signed in")
        val params = apiClient.buildResumeQueryParams(owner, limit)
        when (val result = apiClient.getResumeItems(params)) {
            is JellyfinResult.Success -> {
                val entities = toRailEntities(owner, result.data.items)
                persistItems(owner, entities, narrowFields = true)
                persistUserData(owner, result.data.items)
                JellyfinResult.Success(entities)
            }
            is JellyfinResult.Error -> JellyfinResult.Error(result.message, result.code)
        }
    }

    /**
     * A rail answers with episodes and no season context, so each one is filed against the seasons of
     * its own series, read once per series in the answer. An episode whose series has not been synced
     * has no seasons to file it against and keeps the parent the server reported, which the next pass
     * over that series corrects.
     */
    private suspend fun toRailEntities(
        owner: String,
        items: List<JellyfinItem>
    ): List<MediaItemEntity> {
        val placements = mutableMapOf<String, JellyfinSeasonPlacement>()
        return items.mapNotNull { item ->
            val placement = item.seriesId
                ?.let { series -> placements.getOrPut(series) { placementFor(owner, series) } }
                ?: JellyfinSeasonPlacement.EMPTY
            item.toItemEntity(owner, null, placement)
        }
    }

    /**
     * Drains watch state that was written while the server could not be told.
     *
     * Deliberately dumb: the server is authority while online, an offline write queues, and the last
     * write wins. It is one position and three flags, and a row is cleared only if nothing wrote to
     * it again while the report was in flight.
     */
    suspend fun pushPendingUserData(): JellyfinResult<Int> = withContext(Dispatchers.IO) {
        pushMutex.withLock {
            val owner = connectionManager.getUserId()
                ?: return@withLock JellyfinResult.Error("Not signed in")
            val pending = mediaUserDataDao.getNeedingSync(owner)
            var reported = 0
            for (row in pending) {
                when (reportOne(owner, row)) {
                    ReportOutcome.REPORTED -> {
                        mediaUserDataDao.clearNeedsSync(owner, row.itemId, row.updatedAt)
                        reported++
                    }
                    ReportOutcome.GONE -> {
                        Logger.info(TAG, "server no longer knows ${row.itemId}; dropping its pending write")
                        mediaUserDataDao.clearNeedsSync(owner, row.itemId, row.updatedAt)
                    }
                    ReportOutcome.UNREACHABLE -> Unit
                }
            }
            JellyfinResult.Success(reported)
        }
    }

    /**
     * [GONE] is separated from [UNREACHABLE] because a row the server will never accept has to stop
     * being pending. While it is pending the server's own state for that item is refused, so an
     * entry that can never drain would silently freeze that item's watch state forever.
     */
    private enum class ReportOutcome { REPORTED, UNREACHABLE, GONE }

    private suspend fun reportOne(owner: String, row: MediaUserDataEntity): ReportOutcome {
        val results = listOf(
            apiClient.reportPlaybackStopped(
                JellyfinPlaybackStopInfo(
                    itemId = row.itemId,
                    positionTicks = row.playbackPositionTicks
                )
            ),
            apiClient.setPlayed(row.itemId, owner, row.played),
            apiClient.setFavorite(row.itemId, owner, row.isFavorite)
        )
        return when {
            results.all { it is JellyfinResult.Success } -> ReportOutcome.REPORTED
            results.any { it is JellyfinResult.Error && it.code == HTTP_NOT_FOUND } -> ReportOutcome.GONE
            else -> ReportOutcome.UNREACHABLE
        }
    }

    private suspend fun persistLibraries(owner: String, libraries: List<MediaLibraryEntity>) {
        if (libraries.isEmpty()) return
        database.withTransaction {
            val existing = mediaLibraryDao.getLibraries(owner).associateBy { it.libraryId }
            mediaLibraryDao.insertAll(
                libraries.map { fresh ->
                    val prior = existing[fresh.libraryId]
                    if (prior == null) fresh
                    else fresh.copy(
                        id = prior.id,
                        itemCount = prior.itemCount,
                        lastSyncedAt = prior.lastSyncedAt
                    )
                }
            )
            mediaLibraryDao.deleteMissing(owner, libraries.map { it.libraryId })
        }
    }

    /**
     * Stores the people credited on each title that answered with any.
     *
     * A title whose response carried no `People` at all is left alone rather than cleared: the
     * field is only requested at the top level of a library, so an absent list means this response
     * was not asked for it, not that the title lost its cast.
     */
    private suspend fun persistCredits(owner: String, items: List<JellyfinItem>) {
        items.forEach { item ->
            val people = item.people ?: return@forEach
            val itemId = item.id ?: return@forEach
            val credits = people.mapIndexedNotNull { index, person ->
                val personId = person.id ?: return@mapIndexedNotNull null
                val name = person.name ?: return@mapIndexedNotNull null
                MediaCreditEntity(
                    ownerUserId = owner,
                    itemId = itemId,
                    personId = personId,
                    name = name,
                    role = person.role?.takeIf { it.isNotBlank() },
                    personType = person.type ?: PERSON_TYPE_ACTOR,
                    sortOrder = index,
                    primaryImageTag = person.primaryImageTag
                )
            }
            mediaCreditDao.replaceForItem(owner, itemId, credits)
        }
    }

    /**
     * Writes items without losing what the server does not know.
     *
     * The unique index makes an insert a replace, and a replace drops the row the download fields
     * live on. Those record a file already on disk, which no sync ever put there and no sync may
     * take away, so they are carried across explicitly rather than left to survive by accident.
     *
     * [narrowFields] marks an answer to a request that asked for less than the full field set - the
     * home rails do, because a tile needs the hierarchy and nothing else. A field the request never
     * asked for comes back absent, and absent is not empty, so the stored value stands instead of
     * being overwritten with a null the server never asserted.
     */
    private suspend fun persistItems(
        owner: String,
        items: List<MediaItemEntity>,
        narrowFields: Boolean = false
    ) {
        if (items.isEmpty()) return
        database.withTransaction {
            val existing = mediaItemDao
                .getByItemIds(owner, items.map { it.itemId })
                .associateBy { it.itemId }
            mediaItemDao.insertAll(
                items.map { fresh ->
                    val prior = existing[fresh.itemId] ?: return@map fresh
                    val merged = if (narrowFields) {
                        fresh.copy(
                            overview = fresh.overview ?: prior.overview,
                            genres = fresh.genres ?: prior.genres,
                            studios = fresh.studios ?: prior.studios,
                            dateCreated = fresh.dateCreated ?: prior.dateCreated,
                            childCount = fresh.childCount ?: prior.childCount,
                            tmdbId = fresh.tmdbId ?: prior.tmdbId,
                            imdbId = fresh.imdbId ?: prior.imdbId,
                            tvdbId = fresh.tvdbId ?: prior.tvdbId
                        )
                    } else {
                        fresh
                    }
                    merged.copy(
                        id = prior.id,
                        libraryId = merged.libraryId ?: prior.libraryId,
                        localPath = prior.localPath,
                        downloadQuality = prior.downloadQuality,
                        downloadedBytes = prior.downloadedBytes,
                        downloadedAt = prior.downloadedAt
                    )
                }
            )
        }
    }

    private suspend fun persistUserData(owner: String, items: List<JellyfinItem>) {
        for (item in items) {
            val userData = item.userData ?: continue
            mediaUserDataDao.applyServerState(
                MediaUserDataEntity(
                    ownerUserId = owner,
                    itemId = item.id,
                    playbackPositionTicks = userData.playbackPositionTicks,
                    playedPercentage = userData.playedPercentage,
                    played = userData.played,
                    playCount = userData.playCount,
                    isFavorite = userData.isFavorite,
                    lastPlayedAt = parseJellyfinInstant(userData.lastPlayedDate),
                    needsSync = false,
                    updatedAt = Instant.now()
                )
            )
        }
    }

    /**
     * Removes rows the server no longer lists, except any that has a downloaded copy.
     *
     * A library the server cannot currently see - a mount that is offline, a scan mid-flight - would
     * otherwise take the user's downloads with it. Unreadable is not absent, so the item stays and
     * the file stays with it.
     */
    private suspend fun pruneMissing(
        owner: String,
        libraryId: String,
        itemType: MediaItemType,
        seen: Set<String>
    ): Int {
        val stored = mediaItemDao.getByLibrary(owner, libraryId, itemType.wireValue)
        val orphaned = stored.filter { it.itemId !in seen && it.localPath == null }
        if (orphaned.isEmpty()) return 0
        orphaned.map { it.itemId }.chunked(SQL_VARIABLE_LIMIT).forEach {
            mediaItemDao.deleteByItemIds(owner, it)
        }
        Logger.info(TAG, "pruned ${orphaned.size} ${itemType.wireValue} rows from $libraryId")
        return orphaned.size
    }

    private fun resumeOffsetFor(libraryId: String): Int {
        val checkpoint = checkpoints[libraryId] ?: return 0
        val fresh = Duration.between(checkpoint.recordedAt, Instant.now()) <
            Duration.ofMinutes(SYNC_RESUME_TTL_MINUTES)
        if (!fresh) {
            checkpoints.remove(libraryId)
            return 0
        }
        Logger.info(TAG, "resuming $libraryId at ${checkpoint.startIndex}")
        return checkpoint.startIndex
    }

    private fun recordCheckpoint(libraryId: String, startIndex: Int) {
        checkpoints[libraryId] = LibraryCheckpoint(startIndex, Instant.now())
    }

    private fun clearCheckpoint(libraryId: String) {
        checkpoints.remove(libraryId)
    }

    private fun JellyfinItem.toLibraryEntity(owner: String, order: Int): MediaLibraryEntity? {
        val libraryName = name ?: return null
        if (MediaCollectionType.fromWire(collectionType) == null) return null
        return MediaLibraryEntity(
            ownerUserId = owner,
            libraryId = id,
            name = libraryName,
            collectionType = collectionType,
            primaryImageTag = primaryImageTag,
            displayOrder = order
        )
    }

    /**
     * The hierarchy is carried as ids rather than as a walked chain: a season points at its series,
     * an episode at its season, and both denormalise the series so a series can gather its episodes
     * in one query.
     *
     * An episode's season comes from [placement] rather than from the id the episode reports, which
     * names the directory its file sits in; see [JellyfinSeasonPlacement].
     */
    private fun JellyfinItem.toItemEntity(
        owner: String,
        libraryId: String?,
        placement: JellyfinSeasonPlacement = JellyfinSeasonPlacement.EMPTY
    ): MediaItemEntity? {
        val itemName = name ?: return null
        val resolvedType = MediaItemType.fromWire(type) ?: return null
        val resolvedName = if (resolvedType == MediaItemType.SEASON) {
            JellyfinUtils.seasonName(itemName, seriesName, indexNumber)
        } else {
            itemName
        }
        val resolvedParent = when (resolvedType) {
            MediaItemType.SEASON -> seriesId ?: parentId
            MediaItemType.EPISODE -> placement.parentFor(parentIndexNumber, seasonId ?: parentId)
            MediaItemType.MOVIE, MediaItemType.SERIES -> null
        }
        val resolvedSortName = if (resolvedName == itemName) {
            sortName ?: JellyfinUtils.createSortName(itemName)
        } else {
            JellyfinUtils.createSortName(resolvedName)
        }
        return MediaItemEntity(
            ownerUserId = owner,
            itemId = id,
            libraryId = libraryId,
            parentId = resolvedParent,
            seriesId = seriesId,
            itemType = resolvedType.wireValue,
            name = resolvedName,
            sortName = resolvedSortName,
            overview = overview,
            productionYear = productionYear,
            premiereDate = parseJellyfinInstant(premiereDate),
            dateCreated = parseJellyfinInstant(dateCreated),
            communityRating = communityRating,
            officialRating = officialRating,
            tmdbId = tmdbId,
            imdbId = imdbId,
            tvdbId = tvdbId,
            genres = genres?.takeIf { it.isNotEmpty() }?.joinToString(","),
            studios = studios?.mapNotNull { it.name }?.takeIf { it.isNotEmpty() }?.joinToString(","),
            runTimeTicks = runTimeTicks,
            indexNumber = indexNumber,
            parentIndexNumber = parentIndexNumber,
            seriesName = seriesName,
            childCount = childCount,
            primaryImageTag = primaryImageTag,
            backdropImageTag = ownBackdropImageTag,
            thumbImageTag = thumbImageTag,
            container = container,
            lastSyncedAt = Instant.now()
        )
    }
}
