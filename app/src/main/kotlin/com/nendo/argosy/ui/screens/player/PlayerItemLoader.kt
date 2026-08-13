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
    val trickplayEnabled: Boolean = false
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
    private val mediaRepository: MediaRepository
) {

    suspend fun load(itemId: String): PlayerItemDetail = withContext(Dispatchers.IO) {
        val stored = storedDetail(itemId)
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
            trickplayEnabled = apiClient.getCapabilities().supportsTrickplay
        )
    }

    private suspend fun storedDetail(itemId: String): PlayerItemDetail {
        val entity = runCatching { mediaRepository.getItem(itemId) }.getOrNull()
            ?: return PlayerItemDetail()
        return PlayerItemDetail(
            title = entity.storedTitle(),
            subtitle = entity.storedSubtitle(),
            runtimeMs = (entity.runTimeTicks ?: 0L) / TICKS_PER_MILLISECOND
        )
    }

    private fun MediaItemEntity.storedTitle(): String = when (itemType) {
        MediaItemType.EPISODE.wireValue -> seriesName ?: name
        else -> name
    }

    private fun MediaItemEntity.storedSubtitle(): String = when (itemType) {
        MediaItemType.EPISODE.wireValue -> {
            val number = when {
                parentIndexNumber != null && indexNumber != null -> "S$parentIndexNumber E$indexNumber"
                indexNumber != null -> "Episode $indexNumber"
                else -> null
            }
            listOfNotNull(number, name.takeIf { it.isNotBlank() }).joinToString("  ")
        }
        else -> productionYear?.toString().orEmpty()
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
