package com.nendo.argosy.data.repository

import com.nendo.argosy.data.local.dao.MediaItemDao
import com.nendo.argosy.data.local.dao.MediaLibraryDao
import com.nendo.argosy.data.local.dao.MediaStreamDao
import com.nendo.argosy.data.local.dao.MediaUserDataDao
import com.nendo.argosy.data.local.entity.MediaCollectionType
import com.nendo.argosy.data.local.entity.MediaItemEntity
import com.nendo.argosy.data.local.entity.MediaItemType
import com.nendo.argosy.data.local.entity.MediaLibraryEntity
import com.nendo.argosy.data.local.entity.MediaStreamEntity
import com.nendo.argosy.data.local.entity.MediaUserDataEntity
import com.nendo.argosy.data.preferences.JellyfinPreferencesRepository
import com.nendo.argosy.data.remote.jellyfin.JellyfinApiClient
import com.nendo.argosy.data.remote.jellyfin.JellyfinLibrarySyncService
import com.nendo.argosy.data.remote.jellyfin.JellyfinResult
import com.nendo.argosy.data.remote.jellyfin.JellyfinSyncProgress
import com.nendo.argosy.data.remote.jellyfin.JellyfinSyncResult
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
    private val librarySyncService: JellyfinLibrarySyncService,
    private val apiClient: JellyfinApiClient
) {
    private val ownerFlow: Flow<String?> = jellyfinPreferencesRepository.preferences
        .map { it.userId?.takeIf { id -> id.isNotBlank() } }
        .distinctUntilChanged()

    private val _nextUp = MutableStateFlow<List<MediaItemEntity>>(emptyList())
    private val _continueWatching = MutableStateFlow<List<MediaItemEntity>>(emptyList())

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
        _nextUp.asStateFlow().onStart { refreshNextUp() }

    fun observeContinueWatching(): Flow<List<MediaItemEntity>> =
        _continueWatching.asStateFlow().onStart { refreshContinueWatching() }

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
        return mediaUserDataDao.getByItems(owner, itemIds).associateBy { it.itemId }
    }

    suspend fun getSeasons(seriesId: String): List<MediaItemEntity> {
        val owner = currentOwner() ?: return emptyList()
        return mediaItemDao.getByParent(owner, seriesId)
    }

    suspend fun getEpisodes(seasonId: String): List<MediaItemEntity> {
        val owner = currentOwner() ?: return emptyList()
        return mediaItemDao.getByParent(owner, seasonId)
    }

    suspend fun countDownloadedInSeries(seriesId: String): Int {
        val owner = currentOwner() ?: return 0
        return mediaItemDao.countDownloadedInSeries(owner, seriesId)
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

    suspend fun markDownloaded(itemId: String, localPath: String, quality: String, bytes: Long) {
        val owner = currentOwner() ?: return
        mediaItemDao.markDownloaded(owner, itemId, localPath, quality, bytes, Instant.now())
    }

    /**
     * Forgets a downloaded copy. Only for a file that is genuinely gone or has been replaced - a
     * volume that is merely unplugged keeps its path, because unreadable is not absent.
     */
    suspend fun clearDownloaded(itemId: String) {
        val owner = currentOwner() ?: return
        mediaItemDao.clearDownloaded(owner, itemId)
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
     */
    suspend fun refreshLibraries(): JellyfinResult<JellyfinSyncResult> =
        librarySyncService.syncLibraries()

    suspend fun refreshEpisodes(seriesId: String, seasonId: String): JellyfinResult<Int> =
        librarySyncService.syncSeasonEpisodes(seriesId, seasonId)

    suspend fun refreshNextUp(): JellyfinResult<List<MediaItemEntity>> {
        val result = librarySyncService.syncNextUp()
        if (result is JellyfinResult.Success) _nextUp.value = result.data
        return result
    }

    suspend fun refreshContinueWatching(): JellyfinResult<List<MediaItemEntity>> {
        val result = librarySyncService.syncContinueWatching()
        if (result is JellyfinResult.Success) _continueWatching.value = result.data
        return result
    }

    suspend fun pushPendingWatchState(): JellyfinResult<Int> = librarySyncService.pushPendingUserData()

    /**
     * Records where playback was left. The write lands locally first and is marked for the server,
     * so a position taken offline is never the one that is lost.
     */
    suspend fun recordPosition(
        itemId: String,
        positionTicks: Long,
        playedPercentage: Double? = null,
        played: Boolean = false
    ) {
        val owner = currentOwner() ?: return
        mediaUserDataDao.recordPosition(owner, itemId, positionTicks, playedPercentage, played, Instant.now())
    }

    suspend fun setPlayed(itemId: String, played: Boolean) {
        val owner = currentOwner() ?: return
        val position = if (played) 0L else resumePositionFor(itemId)
        mediaUserDataDao.recordPosition(owner, itemId, position, null, played, Instant.now())
    }

    suspend fun setFavorite(itemId: String, isFavorite: Boolean) {
        val owner = currentOwner() ?: return
        mediaUserDataDao.setFavorite(owner, itemId, isFavorite, Instant.now())
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

    companion object {
        const val TICKS_PER_SECOND = 10_000_000L
        private const val SEARCH_LIMIT = 50
        private const val IMAGE_PRIMARY = "Primary"
        private const val IMAGE_BACKDROP = "Backdrop"
        private const val IMAGE_THUMB = "Thumb"
    }
}
