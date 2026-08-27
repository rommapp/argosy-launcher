package com.nendo.argosy.ui.screens.home.delegates

import android.content.Context
import com.nendo.argosy.R
import com.nendo.argosy.data.local.entity.MediaItemEntity
import com.nendo.argosy.data.local.entity.MediaItemType
import com.nendo.argosy.data.media.MediaAvailability
import com.nendo.argosy.data.media.MediaAvailabilityVerifier
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.repository.MediaRepository
import com.nendo.argosy.data.repository.MediaTransferProgress
import com.nendo.argosy.domain.model.MediaPlayTarget
import com.nendo.argosy.domain.usecase.media.ResolveMediaPlayTargetUseCase
import com.nendo.argosy.ui.screens.common.GradientExtractionDelegate
import com.nendo.argosy.ui.screens.home.HomeMediaUi
import com.nendo.argosy.ui.screens.home.toHomeMediaUi
import com.nendo.argosy.ui.screens.media.MediaLibraryUi
import com.nendo.argosy.ui.screens.media.MediaResumePrompt
import com.nendo.argosy.ui.screens.media.toMediaLibraryUi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * How many titles the picker lists. High enough to reach a whole library by scrolling; the list is
 * lazy, so the cost is the query rather than the rows.
 */
private const val TILE_PICKER_LIMIT = 2000

/**
 * [libraryItems] belongs to [libraryItemsFor] and to no other library. Naming which library the
 * tiles came from is what lets a row that has just been entered say it is loading rather than show
 * the previous library's contents under the new library's heading.
 */
data class HomeMediaState(
    val nextUp: List<HomeMediaUi> = emptyList(),
    val continueWatching: List<HomeMediaUi> = emptyList(),
    val favorites: List<HomeMediaUi> = emptyList(),
    val libraries: List<MediaLibraryUi> = emptyList(),
    val librariesLoaded: Boolean = false,
    val libraryItems: List<HomeMediaUi> = emptyList(),
    val libraryItemsFor: String? = null,
    val tileItems: Map<String, HomeMediaUi> = emptyMap(),
    /**
     * How far along anything currently downloading is, keyed by item id and by series id. Held
     * beside the tiles rather than on them because a download moves far more often than a tile's
     * own contents do.
     */
    val downloadProgress: Map<String, MediaTransferProgress> = emptyMap(),
    val isSignedIn: Boolean = false,
    val isLoading: Boolean = false,
    val showNextUp: Boolean = true,
    val showContinueWatching: Boolean = false,
    val showLibraries: Boolean = true,
    val resumePrompt: MediaResumePrompt? = null
)

/**
 * Builds home's media rows: the two personal rails, and one row per library the server offers.
 *
 * The rails come from the server's own ordering rather than from a local query, so this delegate
 * only observes what the repository holds and turns it into tiles; it never sorts, filters or merges
 * the two, because the whole point of keeping them apart is that Next Up answers "what comes after
 * the episode I finished" and Continue Watching answers "what did I stop halfway through". A local
 * reconstruction of either would get the first one wrong.
 *
 * A library row is the opposite case: it is a plain read of one library's top-level items, which the
 * repository already answers with the right item type for that library's own kind. Only the library
 * currently under the cursor is read, the way only the current platform's games are, so a shelf of
 * several thousand titles is never built for a row nobody is looking at.
 *
 * An episode is drawn as its show -- the show's poster, the show's name -- while the tile still
 * plays the episode. The show's own artwork is preferred when its row has been synced and falls back
 * to the untagged address for the same show, which the server still answers; only when there is no
 * show at all does the tile fall back to the episode's own still.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class HomeMediaDelegate @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mediaRepository: MediaRepository,
    private val preferencesRepository: UserPreferencesRepository,
    private val availabilityVerifier: MediaAvailabilityVerifier,
    private val gradientExtractionDelegate: GradientExtractionDelegate,
    private val resolveMediaPlayTarget: ResolveMediaPlayTargetUseCase
) {
    private var gradientScope: CoroutineScope? = null

    private val _state = MutableStateFlow(HomeMediaState())
    val state: StateFlow<HomeMediaState> = _state.asStateFlow()

    private val selectedLibraryId = MutableStateFlow<String?>(null)
    private val tileItemIds = MutableStateFlow<List<String>>(emptyList())
    private val librarySyncRequested = AtomicBoolean(false)

    fun observe(scope: CoroutineScope) {
        gradientScope = scope
        scope.launch {
            mediaRepository.isSignedIn.collect { signedIn ->
                _state.update { it.copy(isSignedIn = signedIn, isLoading = signedIn && it.isEmptyRails) }
            }
        }
        scope.launch {
            preferencesRepository.userPreferences.collect { prefs ->
                _state.update {
                    it.copy(
                        showNextUp = prefs.homeLayout.rails.showNextUp,
                        showContinueWatching = prefs.homeLayout.rails.showContinueWatching,
                        showLibraries = prefs.homeLayout.rails.showLibraries
                    )
                }
            }
        }
        scope.launch {
            combine(
                mediaRepository.observeNextUp(),
                availabilityVerifier.availability
            ) { entities, verified -> toTiles(entities, verified) }.collect { tiles ->
                _state.update { it.copy(nextUp = tiles, isLoading = false) }
            }
        }
        scope.launch {
            combine(
                mediaRepository.observeContinueWatching(),
                availabilityVerifier.availability
            ) { entities, verified -> toTiles(entities, verified) }.collect { tiles ->
                _state.update { it.copy(continueWatching = tiles, isLoading = false) }
            }
        }
        observeLibraries(scope)
        observeLibraryItems(scope)
        observeFavorites(scope)
        observeTileItems(scope)
        observeDownloadProgress(scope)
    }

    /**
     * The titles this account has marked as favourites, as tiles for home's Favorites row.
     *
     * Marked state is stored per item, so a favourite the library sync has not stored a title for
     * yet resolves to nothing and is simply not drawn: the flag stays, the row fills in once the
     * title arrives. A show among them carries whatever the Continue Watching rail knows about it,
     * the same treatment a library row's tiles get, so the footer's promise of Resume is true.
     */
    private fun observeDownloadProgress(scope: CoroutineScope) {
        scope.launch {
            mediaRepository.observeDownloadProgress()
                .distinctUntilChanged()
                .collect { progress -> _state.update { it.copy(downloadProgress = progress) } }
        }
    }

    private fun observeFavorites(scope: CoroutineScope) {
        scope.launch {
            mediaRepository.observeFavorites()
                .map { rows -> rows.map { it.itemId } }
                .distinctUntilChanged()
                .combine(availabilityVerifier.availability) { itemIds, verified ->
                    toTiles(orderedItems(itemIds), verified)
                }
                .combine(inProgressEpisodes()) { tiles, inProgress ->
                    tiles.map { it.withResume(inProgress) }
                }
                .collect { tiles -> _state.update { it.copy(favorites = tiles) } }
        }
    }

    /**
     * The titles a curated grid points at, keyed by item id.
     *
     * Read here rather than by the grid so a pinned show carries the same facts a row's tile does --
     * whether it is on the device, how far through it the viewer is, which episode a press resumes --
     * and so both come from one place instead of the grid growing its own idea of a media tile.
     */
    private fun observeTileItems(scope: CoroutineScope) {
        scope.launch {
            tileItemIds
                .combine(availabilityVerifier.availability) { itemIds, verified ->
                    toTiles(orderedItems(itemIds), verified)
                }
                .combine(inProgressEpisodes()) { tiles, inProgress ->
                    tiles.associate { tile -> tile.itemId to tile.withResume(inProgress) }
                }
                .collect { tiles -> _state.update { it.copy(tileItems = tiles) } }
        }
    }

    /**
     * Names the media items the curated grid is currently showing, so only those are resolved.
     */
    fun selectTileItems(itemIds: List<String>) {
        tileItemIds.value = itemIds
    }

    /**
     * The stored items for [itemIds], kept in the order asked for. The read answers in whatever
     * order the database finds them, and for the favourites row that order is the answer: it is the
     * order they were marked in.
     */
    private suspend fun orderedItems(itemIds: List<String>): List<MediaItemEntity> {
        if (itemIds.isEmpty()) return emptyList()
        val byId = mediaRepository.getItems(itemIds).associateBy { it.itemId }
        return itemIds.mapNotNull { byId[it] }
    }

    private fun observeLibraries(scope: CoroutineScope) {
        scope.launch {
            mediaRepository.observeLibraries().collect { entities ->
                _state.update {
                    it.copy(
                        libraries = entities.map { entity -> entity.toMediaLibraryUi() },
                        librariesLoaded = true
                    )
                }
                if (entities.isEmpty()) syncLibrariesOnce(scope)
            }
        }
    }

    /**
     * The library list is filled by the media screen's own sync, so home would otherwise show no
     * library rows until that screen had been opened at least once. One sync is asked for when the
     * table is empty and never again for this delegate, which keeps a server that genuinely offers
     * nothing from being asked on every emission.
     */
    private fun syncLibrariesOnce(scope: CoroutineScope) {
        if (!_state.value.isSignedIn) return
        if (!librarySyncRequested.compareAndSet(false, true)) return
        scope.launch { mediaRepository.refreshLibraries() }
    }

    /**
     * A library row's tiles, carrying what the Continue Watching rail already knows about the shows
     * among them.
     *
     * The rail is read rather than the episode table because it is the one answer that costs nothing:
     * it is in memory, it is the server's own idea of where the viewer is, and a show it names is a
     * show whose next press resumes. Without it a half watched series would offer Play and then
     * resume anyway, which is the footer lying about the button under it.
     */
    private fun observeLibraryItems(scope: CoroutineScope) {
        scope.launch {
            selectedLibraryId
                .flatMapLatest { libraryId -> libraryItemsOf(libraryId) }
                .combine(availabilityVerifier.availability) { batch, verified ->
                    LibraryTiles(batch.libraryId, toTiles(batch.entities, verified))
                }
                .combine(inProgressEpisodes()) { batch, inProgress ->
                    LibraryTiles(batch.libraryId, batch.tiles.map { it.withResume(inProgress) })
                }
                .collect { batch ->
                    _state.update {
                        it.copy(libraryItems = batch.tiles, libraryItemsFor = batch.libraryId)
                    }
                }
        }
    }

    /**
     * The part-watched episode of each show the Continue Watching rail is holding, keyed by show.
     *
     * Taken from this delegate's own state rather than by collecting the rail a second time, so the
     * tiles are the ones already built and the rail's refresh is not asked for twice.
     */
    private fun inProgressEpisodes(): Flow<Map<String, HomeMediaUi>> =
        _state
            .map { it.continueWatching }
            .distinctUntilChanged()
            .map { tiles ->
                tiles.filter { it.hasResumePosition }
                    .mapNotNull { tile -> tile.seriesId?.let { it to tile } }
                    .toMap()
            }

    private fun HomeMediaUi.withResume(inProgress: Map<String, HomeMediaUi>): HomeMediaUi {
        if (!isSeries) return this
        val episode = inProgress[itemId] ?: return this
        return copy(
            resumeItemId = episode.itemId,
            resumeTicks = episode.resumeTicks,
            progressFraction = episode.progressFraction
        )
    }

    private fun libraryItemsOf(libraryId: String?): Flow<LibraryBatch> =
        if (libraryId == null) {
            flowOf(LibraryBatch(null, emptyList()))
        } else {
            mediaRepository.observeLibraryItems(libraryId)
                .map { entities -> LibraryBatch(libraryId, entities) }
        }

    /**
     * Names the library whose row is under the cursor. Anything else stops being observed, so moving
     * off a row stops the work that row was doing.
     */
    fun selectLibrary(libraryId: String?) {
        selectedLibraryId.value = libraryId
    }

    /**
     * Takes a newer answer for both rails. Called when home comes back to the foreground, because a
     * position written by the player -- or by another client entirely -- moves what the server puts
     * at the front of either rail.
     *
     * Library rows are not refreshed here: they read the stored item table, which a full library sync
     * fills, and running that sync every time home is foregrounded would be a whole-server crawl for
     * a row that is usually not even on screen.
     */
    fun refresh(scope: CoroutineScope) {
        if (!_state.value.isSignedIn) return
        availabilityVerifier.verifyOnOpen()
        scope.launch { mediaRepository.refreshNextUp() }
        scope.launch { mediaRepository.refreshContinueWatching() }
    }

    /**
     * Asks the server for the library listing again. This is the deliberate, user-asked-for version
     * of the sync [syncLibrariesOnce] performs unprompted, so it is not rate limited.
     */
    fun refreshLibraries(scope: CoroutineScope) {
        if (!_state.value.isSignedIn) return
        scope.launch { mediaRepository.refreshLibraries() }
    }

    /**
     * Titles a curated grid can be filled from.
     *
     * A blank query answers with the favourites first and then whatever else has been synced, so the
     * list opens on the titles most likely to be worth pinning rather than on whatever the library
     * happens to hold first. Only movies and series are offered: a tile stands for a show, not for
     * one episode of it, which would be stale the moment it was watched.
     */
    suspend fun searchForTiles(query: String): List<com.nendo.argosy.ui.components.TilePickerEntry> {
        if (!_state.value.isSignedIn) return emptyList()
        val matches = if (query.isBlank()) {
            _state.value.favorites.map { it.toPickerEntry() } +
                mediaRepository.topLevelItems(TILE_PICKER_LIMIT).map { it.toPickerEntry() }
        } else {
            mediaRepository.search(query, TILE_PICKER_LIMIT).map { it.toPickerEntry() }
        }
        return matches.distinctBy { it.target }.take(TILE_PICKER_LIMIT)
    }

    private fun HomeMediaUi.toPickerEntry(): com.nendo.argosy.ui.components.TilePickerEntry {
        val standsForSeries = isSeries || isEpisode
        return com.nendo.argosy.ui.components.TilePickerEntry(
            target = com.nendo.argosy.domain.model.HomeTileTargetRef.Media(detailItemId),
            title = title,
            subtitle = if (standsForSeries) {
                context.getString(R.string.home_media_picker_series)
            } else {
                context.getString(R.string.home_media_picker_movie)
            },
            posterUrl = posterUrl,
            isSeries = standsForSeries,
            isLocal = isDownloaded
        )
    }

    private fun MediaItemEntity.toPickerEntry(): com.nendo.argosy.ui.components.TilePickerEntry {
        val standsForSeries = MediaItemType.fromWire(itemType) == MediaItemType.SERIES
        return com.nendo.argosy.ui.components.TilePickerEntry(
            target = com.nendo.argosy.domain.model.HomeTileTargetRef.Media(itemId),
            title = name,
            subtitle = if (standsForSeries) {
                context.getString(R.string.home_media_picker_series)
            } else {
                context.getString(R.string.home_media_picker_movie)
            },
            posterUrl = mediaRepository.posterUrl(itemId, primaryImageTag),
            isSeries = standsForSeries,
            isLocal = localPath != null
        )
    }

    /**
     * Raises the Start Over prompt for one tile. A tile with no stored position has no choice to
     * offer, so it answers false and the caller plays instead of showing a prompt with one real
     * option in it.
     *
     * The prompt names the episode, never the show: a series tile that reached here is one whose
     * resumable episode is already known, and it is that episode the two answers apply to.
     */
    fun openResumePrompt(media: HomeMediaUi): Boolean {
        if (!media.hasResumePosition) return false
        _state.update {
            it.copy(
                resumePrompt = MediaResumePrompt(
                    itemId = media.resumeTargetId,
                    title = media.title,
                    subtitle = media.subtitle,
                    resumeTicks = media.resumeTicks
                )
            )
        }
        return true
    }

    fun dismissResumePrompt() {
        _state.update { it.copy(resumePrompt = null) }
    }

    /**
     * Clears the favourite flag on one title, locally and on the server. The favourites row observes
     * the flag, so it empties the tile out on its own rather than being told to.
     */
    suspend fun unfavorite(itemId: String) {
        mediaRepository.setFavorite(itemId, isFavorite = false)
    }

    /**
     * Carries a tile's playback position into the stored one, so the fullscreen player opens where
     * the tile had reached instead of where the library was last left.
     */
    suspend fun handOffPosition(itemId: String, positionMs: Long) {
        if (positionMs <= 0) return
        mediaRepository.recordPosition(
            itemId,
            positionMs * com.nendo.argosy.data.remote.jellyfin.TICKS_PER_MILLISECOND
        )
    }

    /**
     * What a press on this tile plays. The rails this delegate holds are handed to the shared
     * resolver as its first two rungs; everything after that is the resolver's own ladder.
     */
    suspend fun resolvePlayTarget(media: HomeMediaUi): MediaPlayTarget {
        if (!media.isSeries) return MediaPlayTarget.Play(media.itemId)
        return resolveMediaPlayTarget(
            itemId = media.itemId,
            knownResumeItemId = media.resumeItemId,
            nextUpHint = _state.value.nextUp.firstOrNull { it.seriesId == media.itemId }?.itemId
        )
    }

    private suspend fun toTiles(
        entities: List<MediaItemEntity>,
        verified: Map<String, MediaAvailability>
    ): List<HomeMediaUi> {
        if (entities.isEmpty()) return emptyList()
        val userData = mediaRepository.getUserDataFor(entities.map { it.itemId })
        val seriesIds = entities.mapNotNull { it.seriesId }.distinct()
        val series = seriesIds.mapNotNull { mediaRepository.getItem(it) }.associateBy { it.itemId }
        gradientScope?.let { scope ->
            gradientExtractionDelegate.loadPersistedMediaGradients(scope, entities.map { it.itemId })
        }
        val gradients = gradientExtractionDelegate.mediaGradients.value
        return entities.map { entity ->
            entity.toHomeMediaUi(
                context,
                mediaRepository,
                userData[entity.itemId],
                series[entity.seriesId],
                verified[entity.itemId],
                gradients[entity.itemId]
            )
        }
    }
}

private val HomeMediaState.isEmptyRails: Boolean
    get() = nextUp.isEmpty() && continueWatching.isEmpty()

/**
 * One library's stored items, carrying the library they came from. The pairing travels with the
 * items so that a batch which arrives after the cursor has already moved on can be recognised as
 * belonging to the library it was asked for rather than the one now on screen.
 */
private data class LibraryBatch(val libraryId: String?, val entities: List<MediaItemEntity>)

private data class LibraryTiles(val libraryId: String?, val tiles: List<HomeMediaUi>)
