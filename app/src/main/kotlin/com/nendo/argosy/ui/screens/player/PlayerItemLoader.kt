package com.nendo.argosy.ui.screens.player

import com.nendo.argosy.data.remote.jellyfin.JellyfinApiClient
import com.nendo.argosy.data.remote.jellyfin.JellyfinItem
import com.nendo.argosy.data.remote.jellyfin.JellyfinResult
import com.nendo.argosy.data.remote.jellyfin.TICKS_PER_MILLISECOND
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
 */
class PlayerItemLoader @Inject constructor(
    private val apiClient: JellyfinApiClient
) {

    suspend fun load(itemId: String): PlayerItemDetail = withContext(Dispatchers.IO) {
        val userId = apiClient.currentUserId() ?: return@withContext PlayerItemDetail()
        val item = when (val result = apiClient.getItem(itemId, userId)) {
            is JellyfinResult.Success -> result.data
            is JellyfinResult.Error -> return@withContext PlayerItemDetail()
        }
        PlayerItemDetail(
            title = item.displayTitle(),
            subtitle = item.displaySubtitle(),
            runtimeMs = (item.runTimeTicks ?: 0L) / TICKS_PER_MILLISECOND,
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
