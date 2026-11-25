package com.nendo.argosy.data.music.romm

import com.nendo.argosy.data.local.dao.RommMusicIndexDao
import com.nendo.argosy.data.local.entity.RommMusicTrackEntity
import com.nendo.argosy.data.preferences.SyncPreferencesRepository
import com.nendo.argosy.data.remote.romm.RomMMusicLibraryService
import com.nendo.argosy.data.remote.romm.RomMMusicTrack
import com.nendo.argosy.data.remote.romm.RomMMusicTrackPage
import com.nendo.argosy.data.remote.romm.RomMResult
import com.nendo.argosy.domain.usecase.music.BGM_MIN_DURATION_SECONDS
import com.nendo.argosy.util.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "RommMusicIndex"
private const val INDEX_PAGE_SIZE = 500
private const val MAX_INDEX_TRACKS = 200_000
private val INDEX_MAX_AGE_MS = TimeUnit.HOURS.toMillis(24)

@Singleton
class RommMusicIndex @Inject constructor(
    private val musicLibrary: RomMMusicLibraryService,
    private val dao: RommMusicIndexDao,
    private val syncPreferences: SyncPreferencesRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val refreshLock = Mutex()
    private val staleRefreshLock = Mutex()
    private var staleRefresh: Job? = null

    private val _revision = MutableStateFlow(0)
    val revision: StateFlow<Int> = _revision.asStateFlow()

    suspend fun activeOwner(): Long? = syncPreferences.getRommUserId()

    suspend fun tracks(ownerUserId: Long): List<RommMusicTrackEntity> =
        withContext(Dispatchers.IO) { dao.tracks(ownerUserId) }

    suspend fun albumTracks(ownerUserId: Long, romId: Long): List<RommMusicTrackEntity> =
        withContext(Dispatchers.IO) { dao.tracksForRom(ownerUserId, romId) }

    suspend fun refreshIfStale(ownerUserId: Long) {
        val state = withContext(Dispatchers.IO) { dao.state(ownerUserId) }
        val age = System.currentTimeMillis() - (state?.fetchedAt ?: 0L)
        if (state != null && state.trackCount > 0 && age < INDEX_MAX_AGE_MS) return
        staleRefreshLock.withLock {
            if (staleRefresh?.isActive == true) return
            staleRefresh = scope.launch { refresh(ownerUserId) }
        }
    }

    fun refreshInBackground() {
        scope.launch { activeOwner()?.let { refresh(it) } }
    }

    suspend fun refresh(ownerUserId: Long): Boolean = withContext(Dispatchers.IO) {
        refreshLock.withLock {
            if (!musicLibrary.isConnected() || !musicLibrary.capabilities().supportsMusicApi) {
                return@withLock false
            }
            val rows = fetchAll(ownerUserId) ?: return@withLock false
            if (activeOwner() != ownerUserId) {
                Logger.info(TAG, "Account changed during the track walk, index kept")
                return@withLock false
            }
            dao.replaceForOwner(ownerUserId, rows, System.currentTimeMillis())
            _revision.update { it + 1 }
            Logger.info(TAG, "Indexed ${rows.size} tracks")
            true
        }
    }

    private suspend fun fetchAll(ownerUserId: Long): List<RommMusicTrackEntity>? {
        val rows = ArrayList<RommMusicTrackEntity>()
        while (rows.size < MAX_INDEX_TRACKS) {
            val page = pageAt(rows.size) ?: return null
            page.items.mapTo(rows) { it.toIndexRow(ownerUserId) }
            if (page.items.size < INDEX_PAGE_SIZE || rows.size >= page.total) return rows
        }
        Logger.warn(TAG, "Track walk stopped at $MAX_INDEX_TRACKS tracks, index kept")
        return null
    }

    private suspend fun pageAt(offset: Int): RomMMusicTrackPage? =
        when (val result = musicLibrary.getTrackPage(BGM_MIN_DURATION_SECONDS, INDEX_PAGE_SIZE, offset)) {
            is RomMResult.Success -> result.data
            is RomMResult.Error -> {
                Logger.warn(TAG, "Track page at offset $offset failed (code ${result.code}), index kept")
                null
            }
        }

    private fun RomMMusicTrack.toIndexRow(ownerUserId: Long) = RommMusicTrackEntity(
        ownerUserId = ownerUserId,
        romFileId = romFileId,
        romId = romId,
        title = title,
        artist = artist,
        album = album,
        genre = genre,
        year = year,
        track = track,
        disc = disc,
        durationSeconds = durationSeconds,
        gameName = gameName,
        platformId = platformId,
        platformSlug = platformSlug,
        platformName = platformName,
        streamUrl = streamUrl,
        coverUrl = coverUrl,
        gameCoverUrl = gameCoverUrl
    )
}
