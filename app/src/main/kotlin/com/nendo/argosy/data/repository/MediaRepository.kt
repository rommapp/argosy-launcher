package com.nendo.argosy.data.repository

import com.nendo.argosy.data.local.dao.MediaCreditDao
import com.nendo.argosy.data.local.dao.MediaItemDao
import com.nendo.argosy.data.local.dao.MediaLibraryDao
import com.nendo.argosy.data.local.dao.MediaDownloadQueueDao
import com.nendo.argosy.data.local.dao.MediaSourceDao
import com.nendo.argosy.data.local.dao.MediaStreamDao
import com.nendo.argosy.data.local.dao.MediaUserDataDao
import com.nendo.argosy.data.local.entity.MediaCollectionType
import com.nendo.argosy.data.local.entity.MediaCreditEntity
import com.nendo.argosy.data.local.entity.MediaDownloadDbState
import com.nendo.argosy.data.local.entity.MediaItemEntity
import com.nendo.argosy.data.local.entity.MediaItemType
import com.nendo.argosy.data.local.entity.MediaLibraryEntity
import com.nendo.argosy.data.local.entity.MediaSourceEntity
import com.nendo.argosy.data.local.entity.MediaStreamEntity
import com.nendo.argosy.data.local.entity.MediaStreamType
import com.nendo.argosy.data.local.entity.MediaUserDataEntity
import com.nendo.argosy.data.preferences.JellyfinPreferencesRepository
import com.nendo.argosy.data.remote.jellyfin.IMAGE_TYPE_BACKDROP
import com.nendo.argosy.data.remote.jellyfin.IMAGE_TYPE_PRIMARY
import com.nendo.argosy.data.remote.jellyfin.IMAGE_TYPE_THUMB
import com.nendo.argosy.data.remote.jellyfin.JellyfinApiClient
import com.nendo.argosy.data.remote.jellyfin.JellyfinLibrarySyncService
import com.nendo.argosy.data.remote.jellyfin.JellyfinMediaSource
import com.nendo.argosy.data.remote.jellyfin.JellyfinResult
import com.nendo.argosy.data.remote.jellyfin.JellyfinSyncProgress
import com.nendo.argosy.data.remote.jellyfin.JellyfinSyncResult
import com.nendo.argosy.data.remote.jellyfin.toSourceEntity
import com.nendo.argosy.data.remote.jellyfin.toStreamEntities
import com.nendo.argosy.data.storage.StorageAttributionRepository
import com.nendo.argosy.data.storage.StorageCategory
import dagger.Lazy
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import java.io.File
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The one door the rest of the app uses to reach media: libraries, the item hierarchy, watch state
 * and the refreshes that fill them. The media DAOs are reached only through here. The player is the
 * exception on the network side: it holds the Jellyfin client directly for playback negotiation and
 * session reporting, which are per-playback exchanges with nothing to cache.
 *
 * Every read is scoped to the signed-in media account, which is taken from the stored credentials
 * rather than from the live connection so a browse still answers while the server is unreachable.
 * With no account signed in each observation answers empty instead of failing, because an unscoped
 * media read would show one account another's library.
 *
 * Next Up and Continue Watching are server-ordered rails, not queries: the server decides what comes
 * next and no local column reproduces that, so they are held as the last answer the server gave and
 * refreshed rather than derived from the item table.
 */
@Suppress("TooManyFunctions")
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class MediaRepository @Inject constructor(
    private val jellyfinPreferencesRepository: JellyfinPreferencesRepository,
    private val mediaLibraryDao: MediaLibraryDao,
    private val mediaItemDao: MediaItemDao,
    private val mediaCreditDao: MediaCreditDao,
    private val mediaUserDataDao: MediaUserDataDao,
    private val mediaStreamDao: MediaStreamDao,
    private val mediaSourceDao: MediaSourceDao,
    private val mediaDownloadQueueDao: MediaDownloadQueueDao,
    private val librarySyncService: JellyfinLibrarySyncService,
    private val apiClient: JellyfinApiClient,
    private val attributionRepository: Lazy<StorageAttributionRepository>
) {
    private val ownerFlow: Flow<String?> = jellyfinPreferencesRepository.preferences
        .map { it.userId?.takeIf { id -> id.isNotBlank() } }
        .distinctUntilChanged()

    private val _nextUp = MutableStateFlow(OwnedRail())
    private val _continueWatching = MutableStateFlow(OwnedRail())

    val isSignedIn: Flow<Boolean> = jellyfinPreferencesRepository.preferences
        .map { it.isSignedIn }
        .distinctUntilChanged()

    val syncProgress: StateFlow<JellyfinSyncProgress> = librarySyncService.syncProgress

    fun observeLibraries(): Flow<List<MediaLibraryEntity>> =
        scoped(emptyList()) { owner -> mediaLibraryDao.observeLibraries(owner) }

    /**
     * The top level of one library: its movies, or its series. Which of the two is the library's own
     * collection type rather than a caller's choice, so a caller cannot ask a movie library for
     * series and silently get nothing.
     */
    fun observeLibraryItems(libraryId: String): Flow<List<MediaItemEntity>> =
        scoped(emptyList()) { owner ->
            flow {
                val library = mediaLibraryDao.getByLibraryId(owner, libraryId)
                val itemType = topLevelTypeOf(library?.collectionType)
                if (itemType == null) emit(emptyList())
                else emitAll(mediaItemDao.observeByLibrary(owner, libraryId, itemType.wireValue))
            }
        }

    fun observeItem(itemId: String): Flow<MediaItemEntity?> =
        scoped(null) { owner -> mediaItemDao.observeByItemId(owner, itemId) }

    fun observeSeasons(seriesId: String): Flow<List<MediaItemEntity>> =
        scoped(emptyList()) { owner -> mediaItemDao.observeByParent(owner, seriesId) }

    fun observeEpisodes(seasonId: String): Flow<List<MediaItemEntity>> =
        scoped(emptyList()) { owner -> mediaItemDao.observeByParent(owner, seasonId) }

    /**
     * The rail that surfaces the episode after the one that was finished, never the finished episode
     * itself. Refreshes once on first collection so a consumer that only observes still gets an
     * answer; call [refreshNextUp] to take a newer one.
     */
    fun observeNextUp(): Flow<List<MediaItemEntity>> =
        scoped(emptyList()) { owner ->
            _nextUp.map { it.itemsFor(owner) }.onStart { refreshNextUp() }
        }

    fun observeContinueWatching(): Flow<List<MediaItemEntity>> =
        scoped(emptyList()) { owner ->
            _continueWatching.map { it.itemsFor(owner) }.onStart { refreshContinueWatching() }
        }

    fun observeUserData(itemId: String): Flow<MediaUserDataEntity?> =
        scoped(null) { owner -> mediaUserDataDao.observeByItem(owner, itemId) }

    /**
     * What this account has marked as a favourite, most recently marked first.
     *
     * Watch state is what carries the flag, so this answers with the state rows rather than with
     * items: an item favourited on another client is a flag the server has told us about before the
     * library sync has stored the title itself, and a join here would silently drop it. The caller
     * resolves the ones it can draw.
     */
    fun observeFavorites(): Flow<List<MediaUserDataEntity>> =
        scoped(emptyList()) { owner -> mediaUserDataDao.observeFavorites(owner) }

    /**
     * The stored items for a list of ids, in one read. An id with no stored item is absent rather
     * than represented by a placeholder, so a caller can tell "not synced yet" from "synced empty".
     */
    suspend fun getItems(itemIds: List<String>): List<MediaItemEntity> {
        val owner = currentOwner() ?: return emptyList()
        if (itemIds.isEmpty()) return emptyList()
        return itemIds.chunked(SQL_VARIABLE_LIMIT).flatMap { mediaItemDao.getByItemIds(owner, it) }
    }

    fun observeStreams(itemId: String): Flow<List<MediaStreamEntity>> =
        scoped(emptyList()) { owner -> mediaStreamDao.observeByItem(owner, itemId) }

    fun observeDownloaded(): Flow<List<MediaItemEntity>> =
        scoped(emptyList()) { owner -> mediaItemDao.observeDownloaded(owner) }

    /**
     * Where playback of one item was left, in the server's 100ns tick unit. Zero means there is
     * nothing to resume, which is also the answer for an item that has never been played.
     */
    suspend fun resumePositionFor(itemId: String): Long {
        val owner = currentOwner() ?: return 0
        return mediaUserDataDao.getByItem(owner, itemId)?.playbackPositionTicks ?: 0
    }

    suspend fun getItem(itemId: String): MediaItemEntity? {
        val owner = currentOwner() ?: return null
        return mediaItemDao.getByItemId(owner, itemId)
    }

    /**
     * Records the colours sampled from an item's poster, in the same serialized all-presets form a
     * game's cover colours use, so switching preset later needs no second sampling pass.
     */
    suspend fun updateGradientColors(itemId: String, json: String) {
        val owner = currentOwner() ?: return
        mediaItemDao.updateGradientColors(owner, itemId, json)
    }

    /**
     * How far along every copy currently being fetched is, keyed by the item it belongs to.
     *
     * An entry exists only while a download is genuinely moving, so a tile can ask "is this one of
     * mine" and get an answer that means work in flight rather than work already finished. A series
     * is keyed by its own id as well as the episode's, because the row a viewer is looking at on the
     * home screen is the series, not the episode being fetched underneath it.
     */
    fun observeDownloadProgress(): Flow<Map<String, MediaTransferProgress>> =
        scoped(emptyMap()) { owner ->
            mediaDownloadQueueDao.observeQueue(owner).map { rows ->
                buildMap {
                    rows.filter { it.totalBytes > 0 && MediaDownloadDbState.isShown(it.state) }
                        .forEach { row ->
                            val transfer = MediaTransferProgress(
                                fraction = (row.bytesDownloaded.toFloat() / row.totalBytes)
                                    .coerceIn(0f, 1f),
                                isPaused = row.state == MediaDownloadDbState.PAUSED.name
                            )
                            put(row.itemId, transfer)
                            row.seriesId?.let { series ->
                                merge(series, transfer) { existing, _ ->
                                    if (transfer.fraction < existing.fraction) transfer else existing
                                }
                            }
                        }
                }
            }
        }

    /**
     * The people credited on a title, in the billing order the server sent.
     *
     * Empty for a title whose library has not synced since credits started being collected, which
     * is indistinguishable here from a title that genuinely has none: both mean there is no cast to
     * draw, and the next library sync settles which it was.
     */
    suspend fun getCredits(itemId: String): List<MediaCreditEntity> {
        val owner = currentOwner() ?: return emptyList()
        return mediaCreditDao.getForItem(owner, itemId)
    }

    suspend fun getUserData(itemId: String): MediaUserDataEntity? {
        val owner = currentOwner() ?: return null
        return mediaUserDataDao.getByItem(owner, itemId)
    }

    /**
     * Watch state for a list of items in one read, keyed by item id. An item that has never been
     * played is absent rather than present-and-zero, so a caller can tell "not started" from
     * "started and rewound to the beginning".
     */
    suspend fun getUserDataFor(itemIds: List<String>): Map<String, MediaUserDataEntity> {
        val owner = currentOwner() ?: return emptyMap()
        if (itemIds.isEmpty()) return emptyMap()
        return itemIds
            .chunked(SQL_VARIABLE_LIMIT)
            .flatMap { mediaUserDataDao.getByItems(owner, it) }
            .associateBy { it.itemId }
    }

    suspend fun getSeasons(seriesId: String): List<MediaItemEntity> {
        val owner = currentOwner() ?: return emptyList()
        return mediaItemDao.getByParent(owner, seriesId)
    }

    suspend fun getEpisodes(seasonId: String): List<MediaItemEntity> {
        val owner = currentOwner() ?: return emptyList()
        return mediaItemDao.getByParent(owner, seasonId)
    }

    /**
     * Every episode of a series that has been stored locally. Episodes arrive a season at a time, so
     * this is what is known rather than what the server holds, and it is the honest denominator for a
     * "downloaded n of m" reading.
     */
    suspend fun getSeriesEpisodes(seriesId: String): List<MediaItemEntity> {
        val owner = currentOwner() ?: return emptyList()
        return mediaItemDao.getBySeries(owner, seriesId, MediaItemType.EPISODE.wireValue)
    }

    /**
     * How many top-level titles each library actually holds here, keyed by library id.
     *
     * Counted rather than read back from [MediaLibraryEntity.itemCount], which is only rewritten
     * when a whole sync pass completes: a library the server has listed but nothing has crawled yet
     * reads zero, and a pass that failed part way through leaves the stored figure describing a
     * table that has since changed. A caller putting a number in front of the user needs the one the
     * browse will actually show.
     *
     * A library whose collection type this build cannot place is absent rather than zero. Nothing
     * can be counted for it, and zero would read as an answer.
     */
    suspend fun countsByLibrary(): Map<String, Int> {
        val owner = currentOwner() ?: return emptyMap()
        return mediaLibraryDao.getLibraries(owner).mapNotNull { library ->
            val itemType = topLevelTypeOf(library.collectionType) ?: return@mapNotNull null
            library.libraryId to
                mediaItemDao.countByLibrary(owner, library.libraryId, itemType.wireValue)
        }.toMap()
    }

    /**
     * The top level of every library at once, capped, in library order then sort name.
     *
     * Each library is asked for its own kind rather than the table being read flat, which is what
     * keeps a season or an episode out of an answer that is supposed to be a list of titles. The cap
     * is applied across the whole set, so a first library of thousands cannot spend it before the
     * second is reached -- it is taken a library at a time and stopped when full.
     */
    suspend fun topLevelItems(limit: Int): List<MediaItemEntity> {
        val owner = currentOwner() ?: return emptyList()
        val collected = mutableListOf<MediaItemEntity>()
        for (library in mediaLibraryDao.getLibraries(owner)) {
            if (collected.size >= limit) break
            val itemType = topLevelTypeOf(library.collectionType) ?: continue
            collected += mediaItemDao
                .getByLibrary(owner, library.libraryId, itemType.wireValue)
                .take(limit - collected.size)
        }
        return collected
    }

    suspend fun getLibraryName(libraryId: String): String? {
        val owner = currentOwner() ?: return null
        return mediaLibraryDao.getByLibraryId(owner, libraryId)?.name
    }

    /**
     * The account every media read is scoped to. Exposed because the download queue is stored per
     * account and its writer needs the same identity the reads use.
     */
    suspend fun currentUserId(): String? = currentOwner()

    /**
     * Keeps what one negotiation reported about a playable version and its tracks.
     *
     * Every download and every stream already asks the server this, so it is captured where the
     * answer arrives rather than fetched again: the library sync never asks for `MediaSources`, and a
     * call whose only purpose is to learn what a call in flight already reported is a call that
     * should not exist.
     *
     * The tracks are replaced rather than merged. A version that came back with fewer tracks than
     * last time has fewer tracks, and merging would leave the missing ones on offer.
     */
    suspend fun recordSourceFacts(itemId: String, source: JellyfinMediaSource) {
        val owner = currentOwner() ?: return
        mediaSourceDao.upsert(source.toSourceEntity(owner, itemId))
        mediaStreamDao.deleteBySource(owner, itemId, source.id)
        mediaStreamDao.insertAll(source.toStreamEntities(owner, itemId))
    }

    /**
     * What is known about this item's largest version, or null when nothing has negotiated it yet.
     *
     * Null means unknown, never small - a caller deciding whether a source already fits inside a
     * quality tier has to treat it as too big to assume otherwise. The largest version answers rather
     * than an arbitrary one, so an item carrying a small alternate cut never stands in for the copy a
     * download would actually take.
     */
    suspend fun knownSourceFacts(itemId: String): MediaSourceEntity? {
        val owner = currentOwner() ?: return null
        return mediaSourceDao.getByItem(owner, itemId)
            .maxWithOrNull(
                compareBy<MediaSourceEntity> { it.videoHeight ?: 0 }.thenBy { it.bitrateKbps ?: 0 }
            )
    }

    /**
     * The subtitle tracks last seen on this item, empty for one nothing has negotiated yet.
     *
     * Read from what an earlier negotiation recorded rather than asked of the server: the download
     * picker asks about a whole batch at the moment it opens, and a round trip per title would make
     * the question cost more than the answer is worth.
     */
    suspend fun knownSubtitleStreams(itemId: String): List<MediaStreamEntity> {
        val owner = currentOwner() ?: return emptyList()
        return mediaStreamDao.getByItem(owner, itemId)
            .filter { it.streamType == MediaStreamType.SUBTITLE.wireValue }
    }

    suspend fun markDownloaded(itemId: String, localPath: String, quality: String, bytes: Long) {
        val owner = currentOwner() ?: return
        mediaItemDao.markDownloaded(owner, itemId, localPath, quality, bytes, Instant.now())
        attributionRepository.get().markDirty(StorageCategory.MEDIA)
    }

    /**
     * Forgets a downloaded copy. Only for a file that is genuinely gone or has been replaced - a
     * volume that is merely unplugged keeps its path, because unreadable is not absent.
     */
    suspend fun clearDownloaded(itemId: String) {
        val owner = currentOwner() ?: return
        mediaItemDao.clearDownloaded(owner, itemId)
        attributionRepository.get().markDirty(StorageCategory.MEDIA)
    }

    /**
     * Titles matching a query: films and shows, never their seasons or episodes.
     *
     * Episodes are excluded because they are stored a season at a time, as a season is opened, so the
     * episode table holds what has been browsed rather than what the server has. Searching it would
     * answer with the episodes of the handful of shows already visited and silently with none of the
     * rest, and a result set that depends on browsing history is one the reader has no way to read as
     * incomplete. A show is found by its own name, and its episodes are on its detail screen.
     *
     * Seasons are excluded for the plainer reason that they are named "Season 1".
     */
    suspend fun search(query: String, limit: Int = SEARCH_LIMIT): List<MediaItemEntity> {
        val owner = currentOwner() ?: return emptyList()
        if (query.isBlank()) return emptyList()
        return mediaItemDao.search(owner, query, SEARCHABLE_TYPES, limit)
    }

    /**
     * Refreshes every library the account can see. The sync pass is whole-account by design -- it
     * reconciles which libraries still exist alongside their contents -- so there is no cheaper
     * per-library entry to call.
     *
     * Unreported local watch state goes up first. The pull refuses to overwrite a row that is still
     * carrying one, so draining afterwards would spend the whole pass declining the very state it
     * just fetched.
     */
    suspend fun refreshLibraries(): JellyfinResult<JellyfinSyncResult> {
        currentOwner()?.let { purgeOtherOwners(it) }
        pushPendingWatchState()
        return librarySyncService.syncLibraries()
    }

    /**
     * Signing in as a different Jellyfin user strands the previous one's rows and files, which are
     * keyed by that user's id and are unreachable once it is no longer current. Their downloads are
     * removed with them; leaving the bytes behind would be space the user cannot see or reclaim.
     */
    private suspend fun purgeOtherOwners(owner: String) {
        val strandedPaths = mediaItemDao.otherOwnerLocalPaths(owner)
        if (strandedPaths.isEmpty() && mediaLibraryDao.countOtherOwners(owner) == 0) return
        for (path in strandedPaths) {
            runCatching { File(path).takeIf { it.exists() }?.delete() }
        }
        mediaDownloadQueueDao.deleteOtherOwners(owner)
        mediaSourceDao.deleteOtherOwners(owner)
        mediaStreamDao.deleteOtherOwners(owner)
        mediaUserDataDao.deleteOtherOwners(owner)
        mediaItemDao.deleteOtherOwners(owner)
        mediaLibraryDao.deleteOtherOwners(owner)
        attributionRepository.get().markDirty(StorageCategory.MEDIA)
    }

    suspend fun refreshEpisodes(seriesId: String, seasonId: String): JellyfinResult<Int> =
        librarySyncService.syncSeasonEpisodes(seriesId, seasonId)

    suspend fun refreshNextUp(): JellyfinResult<List<MediaItemEntity>> {
        val result = librarySyncService.syncNextUp()
        if (result is JellyfinResult.Success) _nextUp.value = OwnedRail(currentOwner(), result.data)
        return result
    }

    suspend fun refreshContinueWatching(): JellyfinResult<List<MediaItemEntity>> {
        val result = librarySyncService.syncContinueWatching()
        if (result is JellyfinResult.Success) {
            _continueWatching.value = OwnedRail(currentOwner(), result.data)
        }
        return result
    }

    suspend fun pushPendingWatchState(): JellyfinResult<Int> = librarySyncService.pushPendingUserData()

    /**
     * Records where playback was left. The write lands locally first and is marked for the server,
     * so a position taken offline is never the one that is lost.
     *
     * The push follows immediately because an item with an unreported write refuses the server's own
     * state, so a position left queued would also freeze that item's watch state until the queue
     * next moved.
     */
    suspend fun recordPosition(
        itemId: String,
        positionTicks: Long,
        playedPercentage: Double? = null,
        played: Boolean = false
    ) {
        val owner = currentOwner() ?: return
        mediaUserDataDao.recordPosition(owner, itemId, positionTicks, playedPercentage, played, Instant.now())
        pushPendingWatchState()
    }

    /**
     * Marking something watched, or favouriting it, lands locally and then goes straight up. The
     * local write is what the screen reads, so the push is allowed to fail: it stays queued and the
     * next connection drains it.
     */
    suspend fun setPlayed(itemId: String, played: Boolean) {
        val owner = currentOwner() ?: return
        val position = if (played) 0L else resumePositionFor(itemId)
        mediaUserDataDao.recordPosition(owner, itemId, position, null, played, Instant.now())
        pushPendingWatchState()
    }

    suspend fun setFavorite(itemId: String, isFavorite: Boolean) {
        val owner = currentOwner() ?: return
        mediaUserDataDao.setFavorite(owner, itemId, isFavorite, Instant.now())
        pushPendingWatchState()
    }

    /**
     * Flips the favourite flag from its stored value and answers the new one, so every surface that
     * offers a toggle shares one owner instead of hard-coding a direction.
     */
    suspend fun toggleFavorite(itemId: String): Boolean {
        val next = !(getUserData(itemId)?.isFavorite ?: false)
        setFavorite(itemId, next)
        return next
    }

    /**
     * Repoints downloaded content after the media directory moves. Stored paths are absolute, so a
     * relocation that does not rewrite them leaves every downloaded item pointing into a tree that
     * no longer exists. Returns the number of rows repointed.
     */
    suspend fun repointDownloads(oldRoot: String, newRoot: String): Int {
        val owner = currentOwner() ?: return 0
        if (oldRoot == newRoot) return 0
        val oldPrefix = oldRoot.trimEnd('/')
        val newPrefix = newRoot.trimEnd('/')
        var repointed = 0
        for (item in mediaItemDao.getDownloaded(owner)) {
            val path = item.localPath ?: continue
            if (path != oldPrefix && !path.startsWith("$oldPrefix/")) continue
            mediaItemDao.updateLocalPath(owner, item.itemId, newPrefix + path.removePrefix(oldPrefix))
            repointed++
        }
        if (repointed > 0) attributionRepository.get().markDirty(StorageCategory.MEDIA)
        return repointed
    }

    fun posterUrl(itemId: String, tag: String?, maxWidth: Int? = null): String =
        imageUrl(itemId, MediaImageType.PRIMARY, tag, maxWidth)

    fun thumbUrl(itemId: String, tag: String?, maxWidth: Int? = null): String =
        imageUrl(itemId, MediaImageType.THUMB, tag, maxWidth)

    /**
     * One image of one item, with the kind of image named rather than assumed.
     *
     * A tag identifies a single image of a single item, so the kind and the item id are part of the
     * same choice as the tag: a request for a kind the item does not carry answers 404 however valid
     * the tag is. Callers that resolve a tag by falling back between kinds must pick the matching
     * kind here rather than varying the tag alone.
     */
    fun imageUrl(
        itemId: String,
        type: MediaImageType,
        tag: String?,
        maxWidth: Int? = null
    ): String = apiClient.buildImageUrl(itemId, type.wireValue, tag, maxWidth)

    private suspend fun currentOwner(): String? = ownerFlow.first()

    private fun <T> scoped(empty: T, block: (String) -> Flow<T>): Flow<T> =
        ownerFlow.flatMapLatest { owner -> if (owner == null) flowOf(empty) else block(owner) }

    private fun topLevelTypeOf(collectionType: String?): MediaItemType? =
        when (MediaCollectionType.fromWire(collectionType)) {
            MediaCollectionType.MOVIES -> MediaItemType.MOVIE
            MediaCollectionType.TV_SHOWS -> MediaItemType.SERIES
            null -> null
        }

    /**
     * The server orders these rails, so they are held rather than queried. The owner is held with
     * them because a cached list outliving a sign-out would otherwise serve the previous account's
     * viewing to the next one.
     */
    private data class OwnedRail(
        val owner: String? = null,
        val items: List<MediaItemEntity> = emptyList()
    ) {
        fun itemsFor(currentOwner: String): List<MediaItemEntity> =
            if (owner == currentOwner) items else emptyList()
    }

    companion object {
        const val TICKS_PER_SECOND = 10_000_000L
        private const val SEARCH_LIMIT = 50

        private val SEARCHABLE_TYPES = listOf(
            MediaItemType.MOVIE.wireValue,
            MediaItemType.SERIES.wireValue
        )

        /**
         * SQLite binds each element of an `IN` list as its own variable and refuses past 999 of
         * them, so a read spanning a whole library has to arrive in pieces.
         */
        private const val SQL_VARIABLE_LIMIT = 900
    }
}

/**
 * The image kinds this client asks for, carrying the server's own image-type tokens so a request
 * spells the kind the same way the item's tag map does.
 */
/**
 * How far a copy has been fetched, and whether it is currently moving. A paused transfer keeps its
 * fraction: the bytes are on disk, they are simply not growing.
 */
data class MediaTransferProgress(
    val fraction: Float,
    val isPaused: Boolean
)

enum class MediaImageType(val wireValue: String) {
    PRIMARY(IMAGE_TYPE_PRIMARY),
    BACKDROP(IMAGE_TYPE_BACKDROP),
    THUMB(IMAGE_TYPE_THUMB)
}
