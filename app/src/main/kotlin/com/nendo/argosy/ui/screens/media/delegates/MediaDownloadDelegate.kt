package com.nendo.argosy.ui.screens.media.delegates

import com.nendo.argosy.data.download.MediaDownloadManager
import com.nendo.argosy.data.download.MediaSizeEstimate
import com.nendo.argosy.data.download.MediaSubtitleOutlook
import com.nendo.argosy.data.download.isInFlight
import com.nendo.argosy.data.media.MediaAvailability
import com.nendo.argosy.data.media.MediaAvailabilityVerifier
import com.nendo.argosy.data.media.mediaAvailabilityOf
import com.nendo.argosy.data.preferences.MediaDownloadQuality
import com.nendo.argosy.data.repository.MediaRepository
import com.nendo.argosy.ui.components.EpisodePickerEntry
import com.nendo.argosy.ui.components.EpisodePickerQuickAction
import com.nendo.argosy.ui.components.EpisodePickerRow
import com.nendo.argosy.ui.components.EpisodePickerState
import com.nendo.argosy.ui.components.EpisodeSelection
import com.nendo.argosy.ui.components.buildEpisodePickerRows
import com.nendo.argosy.ui.screens.media.MediaDownloadOption
import com.nendo.argosy.ui.screens.media.MediaDownloadPrompt
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
 * The answer to asking for a download prompt: either the prompt itself, or the reason there is
 * none. A refusal carries user-facing words because the caller's only correct move is to show
 * them - closing the menu with nothing said reads as the button doing nothing.
 */
sealed interface MediaDownloadPromptOutcome {
    data class Ready(val prompt: MediaDownloadPrompt) : MediaDownloadPromptOutcome
    data class Refused(val reason: String) : MediaDownloadPromptOutcome
}

/**
 * The download half of a media detail view.
 *
 * A series opens the episode chooser and is asked at what quality afterwards, because the answer to
 * the first changes the size of the second: the estimate shown against each quality is the size of
 * the whole batch. A movie or a single episode skips straight to the quality question.
 *
 * Nothing here decides that a series is or is not downloaded. That is a count against a count -
 * partial seasons are the ordinary case - and the screen renders whichever of the three numbers in
 * [MediaDownloadSummary] it needs.
 */
@Singleton
class MediaDownloadDelegate @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val mediaDownloadManager: MediaDownloadManager,
    private val availabilityVerifier: MediaAvailabilityVerifier,
    private val resolveMediaPlayTarget:
        com.nendo.argosy.domain.usecase.media.ResolveMediaPlayTargetUseCase
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
        val inFlight = active != null &&
            active.state.isInFlight &&
            (if (isSeries) active.seriesId == itemId else active.itemId == itemId)
        queued + if (inFlight) 1 else 0
    }.distinctUntilChanged()

    /**
     * How much of a series is on this device. [pending] counts what the queue is holding for it, so
     * a batch that was just enqueued reads as in-flight rather than as absent.
     *
     * Episodes on storage that is not connected stay inside the downloaded count and are reported
     * separately. Shrinking the count when a card is pulled would say the copies were lost, which is
     * the one thing that did not happen.
     */
    suspend fun summaryFor(item: MediaItemUi, pending: Int): MediaDownloadSummary {
        if (!item.isSeries) {
            return MediaDownloadSummary(
                downloaded = if (item.isDownloaded) 1 else 0,
                known = 1,
                pending = pending,
                unavailable = if (item.availability == MediaAvailability.UNAVAILABLE) 1 else 0
            )
        }
        val episodes = mediaRepository.getSeriesEpisodes(item.itemId)
        val verified = availabilityVerifier.availability.value
        val states = episodes.map { mediaAvailabilityOf(it.localPath, verified[it.itemId]) }
        return MediaDownloadSummary(
            downloaded = states.count { it.hasLocalCopy },
            known = episodes.size,
            pending = pending,
            unavailable = states.count { it == MediaAvailability.UNAVAILABLE }
        )
    }

    /**
     * A movie is asked only for a quality; a series opens the chooser, or answers with the reason
     * the chooser cannot be built.
     */
    suspend fun openPrompt(item: MediaItemUi): MediaDownloadPromptOutcome {
        if (!item.isSeries) {
            return MediaDownloadPromptOutcome.Ready(
                qualityPrompt(
                    title = item.title,
                    targets = listOf(item.itemId),
                    totalRuntimeTicks = item.runTimeTicks ?: 0,
                    allowRemove = item.isDownloaded
                )
            )
        }
        return episodesPrompt(item)
    }

    /**
     * Fetches a season before refusing, for a show reached from somewhere that never opened it.
     *
     * Episodes arrive a season at a time and only a screen that has opened the show has asked for
     * any. A caller that starts from a grid tile has none stored, and refusing there would say the
     * show cannot be downloaded when the truth is that nobody had looked yet. A refusal says which
     * of the two things went wrong: the fetch did not answer, or it answered with nothing.
     */
    private suspend fun episodesPrompt(item: MediaItemUi): MediaDownloadPromptOutcome {
        var rows = episodePickerRows(item.itemId)
        var fetchFailed = false
        if (rows.isEmpty()) {
            fetchFailed = !resolveMediaPlayTarget.fetchFirstSeason(item.itemId)
            if (!fetchFailed) rows = episodePickerRows(item.itemId)
        }
        if (rows.isEmpty()) {
            return MediaDownloadPromptOutcome.Refused(
                if (fetchFailed) {
                    "Couldn't get this show's episodes from the server"
                } else {
                    "No episodes were found for this show"
                }
            )
        }
        return MediaDownloadPromptOutcome.Ready(
            MediaDownloadPrompt(
                step = MediaDownloadStep.EPISODES,
                title = item.title,
                subtitle = "Choose episodes",
                episodes = EpisodePickerState(
                    selection = EpisodeSelection(rows = rows),
                    quickActions = quickActionsFor(item.itemId)
                )
            )
        )
    }

    /**
     * The two "decide for me" shortcuts, resolved from one walk of the rolling window so the shorter
     * one is always the head of the longer one.
     */
    private suspend fun quickActionsFor(seriesId: String): List<EpisodePickerQuickAction> {
        val rolling = nextEpisodeIds(seriesId, NEXT_TEN)
        if (rolling.isEmpty()) return emptyList()
        return listOf(
            EpisodePickerQuickAction(
                key = "next-$NEXT_FIVE",
                label = "Next $NEXT_FIVE",
                itemIds = rolling.take(NEXT_FIVE)
            ),
            EpisodePickerQuickAction(
                key = "next-$NEXT_TEN",
                label = "Next $NEXT_TEN",
                itemIds = rolling
            )
        )
    }

    /**
     * The removal confirmation on its own, for a caller that already knows removal is what was asked
     * for. Answers null when there is nothing on this device to take, so a stale menu row cannot
     * raise a prompt over an empty set.
     */
    suspend fun openRemovalPrompt(item: MediaItemUi): MediaDownloadPrompt? {
        val targets = if (item.isSeries) {
            downloadedEpisodeIds(item.itemId)
        } else {
            listOf(item.itemId).takeIf { item.isDownloaded }.orEmpty()
        }
        if (targets.isEmpty()) return null
        return removalPrompt(item.title, targets)
    }

    /**
     * Moves the prompt to its next state. A quality choice enqueues and closes; removal asks once
     * more before it deletes anything, because the files it takes are not fetched back in a moment.
     */
    suspend fun advance(
        prompt: MediaDownloadPrompt,
        item: MediaItemUi
    ): MediaDownloadPrompt? {
        val option = prompt.focusedOption ?: return null
        if (!option.enabled) return prompt

        if (prompt.step == MediaDownloadStep.CONFIRM) {
            if (option.isRemoval) removeAll(prompt.targets)
            return null
        }

        option.quality?.let { quality ->
            mediaDownloadManager.enqueueAll(prompt.targets, quality)
            return null
        }

        if (option.isRemoval) {
            if (prompt.targets.isEmpty()) return null
            return removalPrompt(item.title, prompt.targets)
        }

        return null
    }

    /**
     * Leaves the chooser for the quality step, carrying whatever was ticked. Answers null when
     * nothing is ticked, so confirming an empty set closes rather than queueing nothing.
     */
    suspend fun confirmEpisodeSelection(prompt: MediaDownloadPrompt): MediaDownloadPrompt? {
        val targets = prompt.episodes.selectedIdsInRowOrder()
        if (targets.isEmpty()) return null
        return qualityPrompt(prompt.title, targets, 0)
    }

    fun moveFocus(prompt: MediaDownloadPrompt, delta: Int): MediaDownloadPrompt {
        if (prompt.step == MediaDownloadStep.EPISODES) {
            return prompt.copy(episodes = prompt.episodes.move(delta))
        }
        if (prompt.options.isEmpty()) return prompt
        return prompt.copy(focusedIndex = (prompt.focusedIndex + delta).mod(prompt.options.size))
    }

    fun focus(prompt: MediaDownloadPrompt, index: Int): MediaDownloadPrompt {
        if (prompt.step == MediaDownloadStep.EPISODES) {
            return prompt.copy(episodes = prompt.episodes.focus(index))
        }
        if (prompt.options.isEmpty()) return prompt
        return prompt.copy(focusedIndex = index.coerceIn(0, prompt.options.lastIndex))
    }

    private suspend fun removeAll(itemIds: List<String>) {
        if (itemIds.isEmpty()) return
        mediaDownloadManager.removeDownloads(itemIds)
    }

    /**
     * The last step before a deletion. Keeping is the focused answer, so a confirm pressed twice out
     * of habit does not take the files.
     */
    private fun removalPrompt(title: String, targets: List<String>): MediaDownloadPrompt =
        MediaDownloadPrompt(
            step = MediaDownloadStep.CONFIRM,
            title = title,
            subtitle = "${downloadCount(targets.size)} on this device",
            options = listOf(
                MediaDownloadOption(
                    label = "Keep",
                    supporting = "Nothing is deleted"
                ),
                MediaDownloadOption(
                    label = "Remove",
                    supporting = "Deletes ${downloadCount(targets.size)} from this device",
                    isRemoval = true
                )
            ),
            targets = targets,
            warning = "The files come back only by downloading them again"
        )

    private fun downloadCount(count: Int): String =
        if (count == 1) "1 download" else "$count downloads"

    private suspend fun qualityPrompt(
        title: String,
        targets: List<String>,
        totalRuntimeTicks: Long,
        allowRemove: Boolean = false
    ): MediaDownloadPrompt {
        val available = mediaDownloadManager.availableBytes()
        val default = mediaDownloadManager.defaultQuality()
        val estimates = mediaDownloadManager.estimateBatch(targets)
        val options = buildList {
            MediaDownloadQuality.entries.forEach { quality ->
                add(
                    MediaDownloadOption(
                        quality = quality,
                        label = quality.displayName,
                        supporting = supportingFor(quality, estimates[quality])
                    )
                )
            }
            if (allowRemove) {
                add(
                    MediaDownloadOption(
                        label = "Remove Download",
                        supporting = "Deletes the copy on this device",
                        isRemoval = true
                    )
                )
            }
        }
        val largest = estimates.values.maxOfOrNull { it.bytes } ?: 0L
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
            note = subtitleNote(mediaDownloadManager.subtitleOutlook(targets)),
            warning = warning
        )
    }

    /**
     * What the download will do with subtitles, said before the choice rather than found out during
     * playback.
     *
     * The two kinds behave differently. Text tracks are saved as files beside the video at any size.
     * A picture track cannot be: it survives a smaller copy only by being drawn into the video
     * itself, which is worth doing only for a title that has nothing readable to offer instead, and
     * only where that has been asked for. A batch nothing has negotiated is told the rule that holds
     * either way rather than promised subtitles it may not have.
     */
    private fun subtitleNote(outlook: MediaSubtitleOutlook): String {
        val pictureOnly = outlook.hasImageSubtitles && !outlook.hasTextSubtitles
        return when {
            !outlook.anythingKnown ->
                "Text subtitles download with the video, picture subtitles come only with Original"
            !outlook.hasTextSubtitles && !outlook.hasImageSubtitles -> "No subtitles on this title"
            !outlook.hasImageSubtitles -> "Subtitles download with the video"
            pictureOnly && outlook.burnsInImageSubtitles ->
                "Picture subtitles are drawn into a smaller copy and cannot be turned off"
            pictureOnly -> "Picture subtitles come only with Original"
            else -> "Text subtitles download with the video, picture subtitles come only with Original"
        }
    }

    /**
     * What each quality says about its size. A tier the source already fits inside is named as the
     * source file rather than as a transcode, because that is what the server will hand over and its
     * size is the one the server reported instead of one computed from the tier's bitrate.
     */
    private fun supportingFor(
        quality: MediaDownloadQuality,
        estimate: MediaSizeEstimate?
    ): String = when {
        estimate == null && quality == MediaDownloadQuality.ORIGINAL -> "Source file, size varies"
        estimate == null -> "Server transcode"
        estimate.isSourceSize -> "Source file - ${formatGigabytes(estimate.bytes)}"
        else -> "About ${formatGigabytes(estimate.bytes)}"
    }

    private fun subtitleFor(targetCount: Int, availableBytes: Long): String {
        val scope = if (targetCount == 1) "1 title" else "$targetCount titles"
        return "$scope - ${formatGigabytes(availableBytes)} free"
    }

    /**
     * The whole series as a chooser: a header per season, an episode under it, in the order they
     * were broadcast. Episodes already on the device are listed and locked rather than dropped, so
     * a season reads as what it is rather than as what is left of it.
     */
    private suspend fun episodePickerRows(seriesId: String): List<EpisodePickerRow> {
        val episodes = mediaRepository.getSeriesEpisodes(seriesId)
        if (episodes.isEmpty()) return emptyList()
        return buildEpisodePickerRows(
            entries = episodes.map { episode ->
                EpisodePickerEntry(
                    itemId = episode.itemId,
                    seasonNumber = episode.parentIndexNumber,
                    label = episode.indexNumber?.let { "$it. ${episode.name}" } ?: episode.name,
                    isDownloaded = episode.localPath != null
                )
            },
            lockDownloaded = true
        )
    }

    /**
     * Answers a press inside the chooser: a shortcut ticks its set, anything else ticks the row.
     */
    fun toggleEpisode(prompt: MediaDownloadPrompt): MediaDownloadPrompt {
        val picker = prompt.episodes
        if (picker.focusedQuickAction != null) {
            return prompt.copy(episodes = picker.applyFocusedQuickAction())
        }
        return prompt.copy(episodes = picker.toggleFocused())
    }

    fun moveSideways(prompt: MediaDownloadPrompt, towardsEnd: Boolean): MediaDownloadPrompt {
        if (prompt.step != MediaDownloadStep.EPISODES) return prompt
        return prompt.copy(episodes = prompt.episodes.moveSideways(towardsEnd))
    }

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

    private fun formatGigabytes(bytes: Long): String = "%.1f GB".format(bytes / BYTES_PER_GIGABYTE)
}
