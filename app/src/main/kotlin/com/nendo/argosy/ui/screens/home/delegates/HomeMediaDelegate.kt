package com.nendo.argosy.ui.screens.home.delegates

import com.nendo.argosy.data.local.entity.MediaItemEntity
import com.nendo.argosy.data.local.entity.MediaItemType
import com.nendo.argosy.data.local.entity.MediaUserDataEntity
import com.nendo.argosy.data.media.MediaAvailability
import com.nendo.argosy.data.media.MediaAvailabilityVerifier
import com.nendo.argosy.data.media.mediaAvailabilityOf
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.repository.MediaRepository
import com.nendo.argosy.ui.screens.home.HomeMediaUi
import com.nendo.argosy.ui.screens.media.MediaResumePrompt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

private const val FINISHED_FRACTION = 0.95f

data class HomeMediaState(
    val nextUp: List<HomeMediaUi> = emptyList(),
    val continueWatching: List<HomeMediaUi> = emptyList(),
    val isSignedIn: Boolean = false,
    val isLoading: Boolean = false,
    val showNextUp: Boolean = true,
    val showContinueWatching: Boolean = true,
    val resumePrompt: MediaResumePrompt? = null
)

/**
 * Builds home's two media rails.
 *
 * Both come from the server's own ordering rather than from a local query, so this delegate only
 * observes what the repository holds and turns it into tiles; it never sorts, filters or merges the
 * two, because the whole point of keeping them apart is that Next Up answers "what comes after the
 * episode I finished" and Continue Watching answers "what did I stop halfway through". A local
 * reconstruction of either would get the first one wrong.
 *
 * An episode is drawn as its show -- the show's poster, the show's name -- while the tile still
 * plays the episode. The show's own artwork is preferred when its row has been synced and falls back
 * to the untagged address for the same show, which the server still answers; only when there is no
 * show at all does the tile fall back to the episode's own still.
 */
@Singleton
class HomeMediaDelegate @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val preferencesRepository: UserPreferencesRepository,
    private val availabilityVerifier: MediaAvailabilityVerifier
) {
    private val _state = MutableStateFlow(HomeMediaState())
    val state: StateFlow<HomeMediaState> = _state.asStateFlow()

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
                        showContinueWatching = prefs.homeLayout.rails.showContinueWatching
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
    }

    /**
     * Takes a newer answer for both rails. Called when home comes back to the foreground, because a
     * position written by the player -- or by another client entirely -- moves what the server puts
     * at the front of either rail.
     */
    fun refresh(scope: CoroutineScope) {
        if (!_state.value.isSignedIn) return
        availabilityVerifier.verifyOnOpen()
        scope.launch { mediaRepository.refreshNextUp() }
        scope.launch { mediaRepository.refreshContinueWatching() }
    }

    /**
     * Raises the Start Over prompt for one tile. A tile with no stored position has no choice to
     * offer, so it answers false and the caller plays instead of showing a prompt with one real
     * option in it.
     */
    fun openResumePrompt(media: HomeMediaUi): Boolean {
        if (!media.hasResumePosition) return false
        _state.update {
            it.copy(
                resumePrompt = MediaResumePrompt(
                    itemId = media.itemId,
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
        val isEpisode = MediaItemType.fromWire(itemType) == MediaItemType.EPISODE
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
