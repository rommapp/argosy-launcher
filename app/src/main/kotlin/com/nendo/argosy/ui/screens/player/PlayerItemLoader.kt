package com.nendo.argosy.ui.screens.player

import com.nendo.argosy.data.local.entity.MediaItemEntity
import com.nendo.argosy.data.local.entity.MediaItemType
import com.nendo.argosy.data.remote.jellyfin.JellyfinApiClient
import com.nendo.argosy.data.remote.jellyfin.JellyfinItem
import com.nendo.argosy.data.remote.jellyfin.JellyfinResult
import com.nendo.argosy.data.remote.jellyfin.TICKS_PER_MILLISECOND
import com.nendo.argosy.data.repository.MediaRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

private const val SEGMENT_TYPE_INTRO = "Intro"
private const val SEGMENT_TYPE_OUTRO = "Outro"
private const val ITEM_TYPE_EPISODE = "Episode"

/**
 * What the chrome needs about the item that the playback negotiation does not answer: what to call
 * it, where it was left, where its chapters are, and where its intro and credits sit.
 */
data class PlayerItemDetail(
    val title: String = "",
    val subtitle: String = "",
    val runtimeMs: Long = 0,
    val serverResumeMs: Long = 0,
    val chapters: List<PlayerChapter> = emptyList(),
    val skipSegments: List<PlayerSkipSegment> = emptyList(),
    val trickplay: PlayerTrickplay? = null,
    val isWatched: Boolean = false,
    val nextEpisode: PlayerNextEpisode? = null
)

/**
 * Reads the descriptive half of an item.
 *
 * Kept apart from the negotiation because the two have different failure meanings: a negotiation
 * that fails means there is nothing to play, while a missing chapter list or segment list only
 * means an affordance stays hidden. Nothing here is allowed to stop a playback.
 *
 * The stored row is read first and the server only ever improves on it, so a downloaded title played
 * with no server in reach still knows its name and its length - and a length is not cosmetic, it is
 * what the scrubber and the resume rule are measured against.
 */
class PlayerItemLoader @Inject constructor(
    private val apiClient: JellyfinApiClient,
    private val mediaRepository: MediaRepository,
    private val resolvePlayTarget: com.nendo.argosy.domain.usecase.media.ResolveMediaPlayTargetUseCase
) {

    suspend fun load(itemId: String): PlayerItemDetail = withContext(Dispatchers.IO) {
        val entity = runCatching { mediaRepository.getItem(itemId) }.getOrNull()
        val stored = storedDetail(entity, itemId)
        val userId = apiClient.currentUserId() ?: return@withContext stored
        val item = when (val result = apiClient.getItem(itemId, userId)) {
            is JellyfinResult.Success -> result.data
            is JellyfinResult.Error -> return@withContext stored
        }
        PlayerItemDetail(
            title = item.displayTitle().ifBlank { stored.title },
            subtitle = item.displaySubtitle().ifBlank { stored.subtitle },
            runtimeMs = ((item.runTimeTicks ?: 0L) / TICKS_PER_MILLISECOND)
                .takeIf { it > 0 } ?: stored.runtimeMs,
            serverResumeMs = (item.userData?.playbackPositionTicks ?: 0L) / TICKS_PER_MILLISECOND,
            chapters = item.chapters.orEmpty().mapIndexed { index, chapter ->
                PlayerChapter(
                    startMs = chapter.startPositionTicks / TICKS_PER_MILLISECOND,
                    name = chapter.name?.takeIf { it.isNotBlank() } ?: "Chapter ${index + 1}"
                )
            },
            skipSegments = loadSkipSegments(itemId),
            trickplay = trickplayOf(item),
            isWatched = item.userData?.played ?: stored.isWatched,
            nextEpisode = stored.nextEpisode
        )
    }

    /**
     * The scrub thumbnails this item has, or nothing.
     *
     * Both halves have to hold: a server old enough to serve the endpoint, and a manifest on this
     * item saying thumbnails were generated for it. The version alone is what left every scrub
     * showing an empty box on a library with trickplay generation switched off - the endpoint exists
     * and answers 404 for every tile of every title.
     *
     * The widest set is taken because there is only ever one on an ordinary library, and where an
     * administrator generated several the largest is the one worth showing on a preview this size.
     */
    private fun trickplayOf(item: JellyfinItem): PlayerTrickplay? {
        if (!apiClient.getCapabilities().supportsTrickplay) return null
        return item.trickplay.orEmpty().entries.firstNotNullOfOrNull { (sourceId, byWidth) ->
            byWidth.entries
                .map { (width, info) -> (width.toIntOrNull() ?: info.width) to info }
                .filter { (width, info) -> width > 0 && info.tileWidth > 0 && info.tileHeight > 0 }
                .maxByOrNull { (width, _) -> width }
                ?.let { (width, info) ->
                    PlayerTrickplay(
                        mediaSourceId = sourceId,
                        thumbnailWidth = width,
                        columns = info.tileWidth,
                        rows = info.tileHeight,
                        intervalMs = info.interval.toLong(),
                        thumbnailCount = info.thumbnailCount
                    )
                }
        }
    }

    private suspend fun storedDetail(
        entity: MediaItemEntity?,
        itemId: String
    ): PlayerItemDetail {
        val watched = runCatching { mediaRepository.getUserData(itemId)?.played }.getOrNull() == true
        if (entity == null) return PlayerItemDetail(isWatched = watched)
        return PlayerItemDetail(
            title = entity.storedTitle(),
            subtitle = entity.storedSubtitle(),
            runtimeMs = (entity.runTimeTicks ?: 0L) / TICKS_PER_MILLISECOND,
            isWatched = watched,
            nextEpisode = nextEpisodeOf(entity)
        )
    }

    /**
     * The episode after this one, resolved by the shared play-target use case so a stored season
     * boundary is crossed by fetching the following season rather than answering nothing. A film,
     * an episode whose series was never stored, and the true last episode of a show still answer
     * with nothing, and the button for it never appears rather than appearing and failing.
     */
    private suspend fun nextEpisodeOf(entity: MediaItemEntity): PlayerNextEpisode? {
        if (entity.itemType != MediaItemType.EPISODE.wireValue) return null
        val next = runCatching { resolvePlayTarget.nextEpisodeAfter(entity.itemId) }
            .getOrNull()
            ?: return null
        return PlayerNextEpisode(itemId = next.itemId, label = next.episodeLabel())
    }

    private fun MediaItemEntity.storedTitle(): String = when (itemType) {
        MediaItemType.EPISODE.wireValue -> seriesName ?: name
        else -> name
    }

    private fun MediaItemEntity.storedSubtitle(): String = when (itemType) {
        MediaItemType.EPISODE.wireValue -> episodeLabel()
        else -> productionYear?.toString().orEmpty()
    }

    private fun MediaItemEntity.episodeLabel(): String {
        val number = when {
            parentIndexNumber != null && indexNumber != null -> "S$parentIndexNumber E$indexNumber"
            indexNumber != null -> "Episode $indexNumber"
            else -> null
        }
        return listOfNotNull(number, name.takeIf { it.isNotBlank() }).joinToString("  ")
    }

    private suspend fun loadSkipSegments(itemId: String): List<PlayerSkipSegment> {
        val response = apiClient.getMediaSegments(itemId)
        if (response !is JellyfinResult.Success) return emptyList()
        return response.data.items.mapNotNull { segment ->
            val kind = when (segment.type) {
                SEGMENT_TYPE_INTRO -> PlayerSkipKind.INTRO
                SEGMENT_TYPE_OUTRO -> PlayerSkipKind.CREDITS
                else -> null
            } ?: return@mapNotNull null
            val start = segment.startTicks / TICKS_PER_MILLISECOND
            val end = segment.endTicks / TICKS_PER_MILLISECOND
            if (end <= start) null else PlayerSkipSegment(kind, start, end)
        }
    }

    /**
     * An episode is announced by its series, because that is the name the viewer went looking for;
     * which episode it is belongs on the second line.
     */
    private fun JellyfinItem.displayTitle(): String = when (type) {
        ITEM_TYPE_EPISODE -> seriesName ?: name.orEmpty()
        else -> name.orEmpty()
    }

    private fun JellyfinItem.displaySubtitle(): String = when (type) {
        ITEM_TYPE_EPISODE -> {
            val season = parentIndexNumber
            val episode = indexNumber
            val number = when {
                season != null && episode != null -> "S$season E$episode"
                episode != null -> "Episode $episode"
                else -> null
            }
            listOfNotNull(number, name?.takeIf { it.isNotBlank() }).joinToString("  ")
        }
        else -> productionYear?.toString().orEmpty()
    }
}
