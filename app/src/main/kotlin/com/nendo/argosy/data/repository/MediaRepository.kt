package com.nendo.argosy.data.repository

import com.nendo.argosy.data.local.dao.MediaItemDao
import com.nendo.argosy.data.local.dao.MediaLibraryDao
import com.nendo.argosy.data.local.dao.MediaDownloadQueueDao
import com.nendo.argosy.data.local.dao.MediaSourceDao
import com.nendo.argosy.data.local.dao.MediaStreamDao
import com.nendo.argosy.data.local.dao.MediaUserDataDao
import com.nendo.argosy.data.local.entity.MediaCollectionType
import com.nendo.argosy.data.local.entity.MediaItemEntity
import com.nendo.argosy.data.local.entity.MediaItemType
import com.nendo.argosy.data.local.entity.MediaLibraryEntity
import com.nendo.argosy.data.local.entity.MediaSourceEntity
import com.nendo.argosy.data.local.entity.MediaStreamEntity
import com.nendo.argosy.data.local.entity.MediaStreamType
import com.nendo.argosy.data.local.entity.MediaUserDataEntity
import com.nendo.argosy.data.preferences.JellyfinPreferencesRepository
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
 * and the refreshes that fill them. UI never touches the media DAOs or the Jellyfin client directly.
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

    suspend fun search(query: String, limit: Int = SEARCH_LIMIT): List<MediaItemEntity> {
        val owner = currentOwner() ?: return emptyList()
        if (query.isBlank()) return emptyList()
        return mediaItemDao.search(owner, query, limit)
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
        apiClient.buildImageUrl(itemId, IMAGE_PRIMARY, tag, maxWidth)

    fun backdropUrl(itemId: String, tag: String?, maxWidth: Int? = null): String =
        apiClient.buildImageUrl(itemId, IMAGE_BACKDROP, tag, maxWidth)

    fun thumbUrl(itemId: String, tag: String?, maxWidth: Int? = null): String =
        apiClient.buildImageUrl(itemId, IMAGE_THUMB, tag, maxWidth)

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

        /**
         * SQLite binds each element of an `IN` list as its own variable and refuses past 999 of
         * them, so a read spanning a whole library has to arrive in pieces.
         */
        private const val SQL_VARIABLE_LIMIT = 900
        private const val IMAGE_PRIMARY = "Primary"
        private const val IMAGE_BACKDROP = "Backdrop"
        private const val IMAGE_THUMB = "Thumb"
    }
}
