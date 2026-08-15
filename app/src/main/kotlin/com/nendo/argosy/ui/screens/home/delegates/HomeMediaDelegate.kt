package com.nendo.argosy.ui.screens.home.delegates

import com.nendo.argosy.data.local.entity.MediaItemEntity
import com.nendo.argosy.data.local.entity.MediaItemType
import com.nendo.argosy.data.local.entity.MediaUserDataEntity
import com.nendo.argosy.data.media.MediaAvailability
import com.nendo.argosy.data.media.MediaAvailabilityVerifier
import com.nendo.argosy.data.media.mediaAvailabilityOf
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.remote.jellyfin.JellyfinResult
import com.nendo.argosy.data.repository.MediaRepository
import com.nendo.argosy.ui.screens.home.HomeMediaUi
import com.nendo.argosy.ui.screens.media.MediaLibraryUi
import com.nendo.argosy.ui.screens.media.MediaResumePrompt
import com.nendo.argosy.ui.screens.media.toMediaLibraryUi
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

private const val FINISHED_FRACTION = 0.95f

private const val SPECIALS_SEASON_NUMBER = 0

/**
 * How many titles the curated grid's picker offers at once, matching the game picker's own cap.
 */
private const val TILE_PICKER_LIMIT = 60

private const val SERIES_LABEL = "Series"

private const val MOVIE_LABEL = "Movie"

/**
 * What a press on a media tile turns into once the tile has been asked what it actually stands for.
 *
 * [OpenDetail] is the answer for a series nothing playable can be found in, not a refusal: the detail
 * screen is where the seasons are, so a press that cannot resolve to an episode still lands somewhere
 * the user can pick one.
 */
sealed class MediaPlayTarget {
    data class Play(val itemId: String) : MediaPlayTarget()
    data class OpenDetail(val itemId: String) : MediaPlayTarget()
}

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
    private val mediaRepository: MediaRepository,
    private val preferencesRepository: UserPreferencesRepository,
    private val availabilityVerifier: MediaAvailabilityVerifier
) {
    private val _state = MutableStateFlow(HomeMediaState())
    val state: StateFlow<HomeMediaState> = _state.asStateFlow()

    private val selectedLibraryId = MutableStateFlow<String?>(null)
    private val tileItemIds = MutableStateFlow<List<String>>(emptyList())
    private val librarySyncRequested = AtomicBoolean(false)

    fun observe(scope: CoroutineScope) {
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
    }

    /**
     * The titles this account has marked as favourites, as tiles for home's Favorites row.
     *
     * Marked state is stored per item, so a favourite the library sync has not stored a title for
     * yet resolves to nothing and is simply not drawn: the flag stays, the row fills in once the
     * title arrives. A show among them carries whatever the Continue Watching rail knows about it,
     * the same treatment a library row's tiles get, so the footer's promise of Resume is true.
     */
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

    private fun HomeMediaUi.toPickerEntry() =
        com.nendo.argosy.ui.components.TilePickerEntry(
            target = com.nendo.argosy.domain.model.HomeTileTargetRef.Media(detailItemId),
            title = title,
            subtitle = if (isSeries || isEpisode) SERIES_LABEL else MOVIE_LABEL,
            posterUrl = posterUrl
        )

    private fun MediaItemEntity.toPickerEntry() =
        com.nendo.argosy.ui.components.TilePickerEntry(
            target = com.nendo.argosy.domain.model.HomeTileTargetRef.Media(itemId),
            title = name,
            subtitle = if (MediaItemType.fromWire(itemType) == MediaItemType.SERIES) {
                SERIES_LABEL
            } else {
                MOVIE_LABEL
            },
            posterUrl = mediaRepository.posterUrl(itemId, primaryImageTag)
        )

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
     * What a press on this tile plays.
     *
     * A movie and a rail's episode play themselves. A series has to be asked, and the order the
     * answer is looked for in is the order it is cheapest and most likely to be right:
     *
     * 1. the episode the Continue Watching rail is holding for this show, already carried on the tile
     * 2. the episode the Next Up rail names for it, which is the server's own idea of what follows
     * 3. what the episode table holds -- part watched first, then the first unwatched, then the first
     * 4. failing all of that, the first season is fetched and the table asked again
     *
     * Step four is the normal path for a show that has never been opened, because episodes are synced
     * a season at a time and a library sync stores only seasons. Fetching one season is enough: a show
     * far enough in to make a later season the right answer is a show one of the rails names, and
     * those are settled before any of this. A show whose seasons are not stored either, or whose fetch
     * does not answer, resolves to its detail screen rather than to nothing.
     */
    suspend fun resolvePlayTarget(media: HomeMediaUi): MediaPlayTarget {
        if (!media.isSeries) return MediaPlayTarget.Play(media.itemId)
        media.resumeItemId?.let { return MediaPlayTarget.Play(it) }
        _state.value.nextUp.firstOrNull { it.seriesId == media.itemId }
            ?.let { return MediaPlayTarget.Play(it.itemId) }
        episodeToPlay(media.itemId)?.let { return MediaPlayTarget.Play(it) }
        if (!fetchFirstSeason(media.itemId)) return MediaPlayTarget.OpenDetail(media.itemId)
        return episodeToPlay(media.itemId)
            ?.let { MediaPlayTarget.Play(it) }
            ?: MediaPlayTarget.OpenDetail(media.itemId)
    }

    private suspend fun episodeToPlay(seriesId: String): String? {
        val episodes = mediaRepository.getSeriesEpisodes(seriesId)
        if (episodes.isEmpty()) return null
        val watched = mediaRepository.getUserDataFor(episodes.map { it.itemId })
        val partWatched = episodes.firstOrNull {
            val userData = watched[it.itemId]
            userData != null && !userData.played && userData.playbackPositionTicks > 0
        }
        val unwatched = episodes.firstOrNull { watched[it.itemId]?.played != true }
        return (partWatched ?: unwatched ?: episodes.first()).itemId
    }

    /**
     * Reads one season's episodes into the library. Specials are passed over when the show has an
     * ordinary season to offer, since season zero sorts first and is nobody's idea of where a show
     * starts.
     */
    private suspend fun fetchFirstSeason(seriesId: String): Boolean {
        val seasons = mediaRepository.getSeasons(seriesId)
        if (seasons.isEmpty()) return false
        val season = seasons.firstOrNull { it.indexNumber != SPECIALS_SEASON_NUMBER } ?: seasons.first()
        return mediaRepository.refreshEpisodes(seriesId, season.itemId) is JellyfinResult.Success
    }

    private suspend fun toTiles(
        entities: List<MediaItemEntity>,
        verified: Map<String, MediaAvailability>
    ): List<HomeMediaUi> {
        if (entities.isEmpty()) return emptyList()
        val userData = mediaRepository.getUserDataFor(entities.map { it.itemId })
        val seriesIds = entities.mapNotNull { it.seriesId }.distinct()
        val series = seriesIds.mapNotNull { mediaRepository.getItem(it) }.associateBy { it.itemId }
        return entities.map { entity ->
            entity.toTile(userData[entity.itemId], series[entity.seriesId], verified[entity.itemId])
        }
    }

    private fun MediaItemEntity.toTile(
        userData: MediaUserDataEntity?,
        series: MediaItemEntity?,
        verified: MediaAvailability?
    ): HomeMediaUi {
        val position = userData?.playbackPositionTicks ?: 0
        val played = userData?.played ?: false
        val kind = MediaItemType.fromWire(itemType)
        val isEpisode = kind == MediaItemType.EPISODE
        val posterId = if (isEpisode) seriesId ?: itemId else itemId
        val posterTag = when {
            !isEpisode -> primaryImageTag
            series != null -> series.primaryImageTag
            seriesId != null -> null
            else -> primaryImageTag
        }
        return HomeMediaUi(
            itemId = itemId,
            title = if (isEpisode) seriesName ?: series?.name ?: name else name,
            subtitle = if (isEpisode) episodeSubtitle() else productionYear?.toString(),
            posterUrl = mediaRepository.posterUrl(posterId, posterTag),
            seriesId = seriesId,
            isEpisode = isEpisode,
            isSeries = kind == MediaItemType.SERIES,
            availability = mediaAvailabilityOf(localPath, verified),
            resumeTicks = position,
            progressFraction = progressFraction(position, runTimeTicks, played)
        )
    }

    /**
     * The episode a tile will actually play, spelled out. Numbering is dropped when the server did
     * not give it rather than printed as a blank, because a special has a name and no numbers.
     */
    private fun MediaItemEntity.episodeSubtitle(): String {
        val season = parentIndexNumber
        val episode = indexNumber
        val marker = when {
            season != null && episode != null -> "S$season E$episode"
            episode != null -> "E$episode"
            else -> null
        }
        return listOfNotNull(marker, name).joinToString(" - ")
    }

    private fun progressFraction(positionTicks: Long, runTimeTicks: Long?, played: Boolean): Float {
        if (played) return 1f
        if (positionTicks <= 0 || runTimeTicks == null || runTimeTicks <= 0) return 0f
        return (positionTicks.toFloat() / runTimeTicks.toFloat()).coerceIn(0f, FINISHED_FRACTION)
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
