package com.nendo.argosy.ui.screens.media.delegates

import com.nendo.argosy.data.download.MediaDownloadManager
import com.nendo.argosy.data.preferences.MediaDownloadQuality
import com.nendo.argosy.data.repository.MediaRepository
import com.nendo.argosy.ui.screens.media.MediaDownloadOption
import com.nendo.argosy.ui.screens.media.MediaDownloadPrompt
import com.nendo.argosy.ui.screens.media.MediaDownloadScope
import com.nendo.argosy.ui.screens.media.MediaDownloadStep
import com.nendo.argosy.ui.screens.media.MediaDownloadSummary
import com.nendo.argosy.ui.screens.media.MediaItemUi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject
import javax.inject.Singleton

private const val NEXT_FIVE = 5
private const val NEXT_TEN = 10
private const val BYTES_PER_GIGABYTE = 1024.0 * 1024.0 * 1024.0
private const val STORAGE_HEADROOM_BYTES = 200L * 1024 * 1024

/**
 * The download half of a media detail view.
 *
 * A series is asked what to fetch before it is asked at what quality, because the answer to the
 * first changes the size of the second: the estimate shown against each quality is the size of the
 * whole batch. A movie or a single episode skips straight to the quality question.
 *
 * Nothing here decides that a series is or is not downloaded. That is a count against a count -
 * partial seasons are the ordinary case - and the screen renders whichever of the three numbers in
 * [MediaDownloadSummary] it needs.
 */
@Singleton
class MediaDownloadDelegate @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val mediaDownloadManager: MediaDownloadManager
) {

    /**
     * How many of this title's downloads the queue is still holding. For a series that is every
     * episode of it in flight, which is what makes a just-enqueued batch visible before any file
     * exists.
     */
    fun pendingCount(itemId: String, isSeries: Boolean): Flow<Int> = combine(
        mediaDownloadManager.downloadQueue,
        mediaDownloadManager.activeDownload
    ) { queue, active ->
        val queued = queue.count { if (isSeries) it.seriesId == itemId else it.itemId == itemId }
        val inFlight = active?.let {
            if (isSeries) it.seriesId == itemId else it.itemId == itemId
        } == true
        queued + if (inFlight) 1 else 0
    }.distinctUntilChanged()

    /**
     * How much of a series is on this device. [pending] counts what the queue is holding for it, so
     * a batch that was just enqueued reads as in-flight rather than as absent.
     */
    suspend fun summaryFor(item: MediaItemUi, pending: Int): MediaDownloadSummary {
        if (!item.isSeries) {
            return MediaDownloadSummary(
                downloaded = if (item.isDownloaded) 1 else 0,
                known = 1,
                pending = pending
            )
        }
        val episodes = mediaRepository.getSeriesEpisodes(item.itemId)
        return MediaDownloadSummary(
            downloaded = mediaRepository.countDownloadedInSeries(item.itemId),
            known = episodes.size,
            pending = pending
        )
    }

    suspend fun openPrompt(
        item: MediaItemUi,
        seasonEpisodes: List<MediaItemUi>,
        summary: MediaDownloadSummary
    ): MediaDownloadPrompt? {
        if (!item.isSeries) {
            return qualityPrompt(
                title = item.title,
                targets = listOf(item.itemId),
                totalRuntimeTicks = item.runTimeTicks ?: 0,
                allowRemove = item.isDownloaded
            )
        }
        return scopePrompt(item, seasonEpisodes, summary)
    }

    /**
     * Moves the prompt to its next state. A scope choice resolves the titles it covers and asks for
     * a quality; a quality choice enqueues and closes; removal runs immediately and closes.
     */
    suspend fun advance(
        prompt: MediaDownloadPrompt,
        item: MediaItemUi,
        seasonEpisodes: List<MediaItemUi>
    ): MediaDownloadPrompt? {
        val option = prompt.focusedOption ?: return null
        if (!option.enabled) return prompt

        option.quality?.let { quality ->
            mediaDownloadManager.enqueueAll(prompt.targets, quality)
            return null
        }

        if (option.scope == MediaDownloadScope.REMOVE) {
            val targets = if (prompt.step == MediaDownloadStep.QUALITY) {
                prompt.targets
            } else {
                downloadedEpisodeIds(item.itemId)
            }
            removeAll(targets)
            return null
        }

        val targets = targetsFor(option.scope, item, seasonEpisodes)
        if (targets.isEmpty()) return null
        return qualityPrompt(item.title, targets, runtimeOf(targets, seasonEpisodes))
    }

    private suspend fun targetsFor(
        scope: MediaDownloadScope?,
        item: MediaItemUi,
        seasonEpisodes: List<MediaItemUi>
    ): List<String> = when (scope) {
        MediaDownloadScope.SEASON -> seasonEpisodes.filterNot { it.isDownloaded }.map { it.itemId }
        MediaDownloadScope.NEXT_FIVE -> nextEpisodeIds(item.itemId, NEXT_FIVE)
        MediaDownloadScope.NEXT_TEN -> nextEpisodeIds(item.itemId, NEXT_TEN)
        MediaDownloadScope.REMOVE -> downloadedEpisodeIds(item.itemId)
        MediaDownloadScope.THIS_ITEM, null -> listOf(item.itemId)
    }

    fun moveFocus(prompt: MediaDownloadPrompt, delta: Int): MediaDownloadPrompt {
        if (prompt.options.isEmpty()) return prompt
        return prompt.copy(focusedIndex = (prompt.focusedIndex + delta).mod(prompt.options.size))
    }

    fun focus(prompt: MediaDownloadPrompt, index: Int): MediaDownloadPrompt {
        if (prompt.options.isEmpty()) return prompt
        return prompt.copy(focusedIndex = index.coerceIn(0, prompt.options.lastIndex))
    }

    private suspend fun removeAll(itemIds: List<String>) {
        for (itemId in itemIds) {
            mediaDownloadManager.removeDownload(itemId)
        }
    }

    private suspend fun scopePrompt(
        item: MediaItemUi,
        seasonEpisodes: List<MediaItemUi>,
        summary: MediaDownloadSummary
    ): MediaDownloadPrompt {
        val seasonPending = seasonEpisodes.count { !it.isDownloaded }
        val nextFive = nextEpisodeIds(item.itemId, NEXT_FIVE)
        val nextTen = nextEpisodeIds(item.itemId, NEXT_TEN)
        val options = buildList {
            add(
                MediaDownloadOption(
                    scope = MediaDownloadScope.SEASON,
                    label = "This Season",
                    supporting = if (seasonPending == 0) "Every episode is already downloaded"
                                 else "$seasonPending to download",
                    enabled = seasonPending > 0
                )
            )
            add(
                MediaDownloadOption(
                    scope = MediaDownloadScope.NEXT_FIVE,
                    label = "Next $NEXT_FIVE Episodes",
                    supporting = supportingForNext(nextFive.size),
                    enabled = nextFive.isNotEmpty()
                )
            )
            add(
                MediaDownloadOption(
                    scope = MediaDownloadScope.NEXT_TEN,
                    label = "Next $NEXT_TEN Episodes",
                    supporting = supportingForNext(nextTen.size),
                    enabled = nextTen.isNotEmpty()
                )
            )
            if (summary.downloaded > 0) {
                add(
                    MediaDownloadOption(
                        scope = MediaDownloadScope.REMOVE,
                        label = "Remove Downloads",
                        supporting = "Frees ${summary.downloaded} episodes from this device"
                    )
                )
            }
        }
        return MediaDownloadPrompt(
            step = MediaDownloadStep.SCOPE,
            title = item.title,
            subtitle = summary.label,
            options = options
        )
    }

    private suspend fun qualityPrompt(
        title: String,
        targets: List<String>,
        totalRuntimeTicks: Long,
        allowRemove: Boolean = false
    ): MediaDownloadPrompt {
        val available = mediaDownloadManager.availableBytes()
        val default = mediaDownloadManager.defaultQuality()
        val options = buildList {
            MediaDownloadQuality.entries.forEach { quality ->
                val estimate = mediaDownloadManager.estimateBytes(totalRuntimeTicks, quality)
                add(
                    MediaDownloadOption(
                        quality = quality,
                        label = quality.displayName,
                        supporting = when {
                            quality == MediaDownloadQuality.ORIGINAL -> "Source file, size varies"
                            estimate == null -> "Server transcode"
                            else -> "About ${formatGigabytes(estimate)}"
                        }
                    )
                )
            }
            if (allowRemove) {
                add(
                    MediaDownloadOption(
                        scope = MediaDownloadScope.REMOVE,
                        label = "Remove Download",
                        supporting = "Deletes the copy on this device"
                    )
                )
            }
        }
        val largest = options.mapNotNull { option ->
            option.quality?.let { mediaDownloadManager.estimateBytes(totalRuntimeTicks, it) }
        }.maxOrNull() ?: 0L
        val warning = if (largest > 0 && largest + STORAGE_HEADROOM_BYTES > available) {
            "Video files are large and this may not fit"
        } else {
            null
        }
        return MediaDownloadPrompt(
            step = MediaDownloadStep.QUALITY,
            title = title,
            subtitle = subtitleFor(targets.size, available),
            options = options,
            focusedIndex = options.indexOfFirst { it.quality == default }.coerceAtLeast(0),
            targets = targets,
            totalRuntimeTicks = totalRuntimeTicks,
            warning = warning
        )
    }

    private fun subtitleFor(targetCount: Int, availableBytes: Long): String {
        val scope = if (targetCount == 1) "1 title" else "$targetCount titles"
        return "$scope - ${formatGigabytes(availableBytes)} free"
    }

    private fun supportingForNext(count: Int): String =
        if (count == 0) "Nothing left to download" else "$count to download"

    /**
     * The episodes the viewer is up to, in broadcast order: unwatched first, already-downloaded ones
     * skipped, so asking twice does not re-fetch what the first ask already took.
     */
    private suspend fun nextEpisodeIds(seriesId: String, count: Int): List<String> {
        val episodes = mediaRepository.getSeriesEpisodes(seriesId)
        if (episodes.isEmpty()) return emptyList()
        val watchState = mediaRepository.getUserDataFor(episodes.map { it.itemId })
        return episodes
            .filter { it.localPath == null }
            .filterNot { watchState[it.itemId]?.played == true }
            .take(count)
            .map { it.itemId }
    }

    private suspend fun downloadedEpisodeIds(seriesId: String): List<String> =
        mediaRepository.getSeriesEpisodes(seriesId)
            .filter { it.localPath != null }
            .map { it.itemId }

    /**
     * Total runtime of a batch. The open season is already loaded, so its episodes answer from
     * memory and only titles from other seasons cost a read.
     */
    private suspend fun runtimeOf(itemIds: List<String>, seasonEpisodes: List<MediaItemUi>): Long {
        if (itemIds.isEmpty()) return 0
        val loaded = seasonEpisodes.associateBy { it.itemId }
        return itemIds.sumOf { id ->
            loaded[id]?.runTimeTicks ?: mediaRepository.getItem(id)?.runTimeTicks ?: 0L
        }
    }

    private fun formatGigabytes(bytes: Long): String = "%.1f GB".format(bytes / BYTES_PER_GIGABYTE)
}
