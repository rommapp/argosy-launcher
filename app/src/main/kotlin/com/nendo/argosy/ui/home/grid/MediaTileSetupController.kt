package com.nendo.argosy.ui.home.grid

import com.nendo.argosy.data.local.entity.MediaTilePlayMode
import com.nendo.argosy.domain.model.HomeTileTargetRef
import com.nendo.argosy.ui.components.CustomGridState
import com.nendo.argosy.ui.components.MediaTileModeOption
import com.nendo.argosy.ui.components.MediaTileNotice
import com.nendo.argosy.ui.components.MediaTileSetup
import com.nendo.argosy.ui.components.MediaTileStep
import com.nendo.argosy.ui.components.TilePickerEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Where the two answers sit, matching the order the confirmation draws them in. A batch big enough
 * to warrant a size warning opens on the cancel side, the way a removal does: the reader asked for a
 * tile, not for a card's worth of downloads, so that one is agreed to deliberately.
 */
private const val CANCEL_BUTTON = 0
private const val CONFIRM_BUTTON = 1

/**
 * The questions a media tile is asked between being chosen and being placed.
 *
 * Kept apart from the grid coordinator because it is a sequence rather than a set of commands: every
 * step decides what the next one asks, back means the previous question rather than "close", and the
 * whole run ends in one placement. Folding that into the coordinator would put a small state machine
 * in the middle of a file whose other methods all complete in one call.
 *
 * Nothing here downloads anything. It works out what is missing, says so, and only enqueues once the
 * reader has answered the notice - which is the whole reason the notice exists.
 */
class MediaTileSetupController(
    private val scope: CoroutineScope,
    private val catalog: MediaTileCatalog?,
    private val read: () -> CustomGridState,
    private val write: ((CustomGridState) -> CustomGridState) -> Unit,
    private val onPlace: (HomeTileTargetRef, List<String>) -> Unit
) {

    private val setup: MediaTileSetup? get() = read().mediaSetup

    /**
     * Starts the run for a series. A movie never comes here: it has nothing to be asked, so its
     * caller goes straight to [offerSingle].
     */
    fun begin(entry: TilePickerEntry) {
        write { it.copy(mediaSetup = MediaTileSetup(entry = entry)) }
    }

    /**
     * The path a movie takes. There is no mode to choose and no run to build, so the only question
     * left is whether anything has to be fetched first.
     */
    fun offerSingle(entry: TilePickerEntry) {
        val itemId = entry.mediaItemId ?: return
        write { it.copy(mediaSetup = MediaTileSetup(entry = entry, mode = MediaTilePlayMode.SINGLE)) }
        settle(listOf(itemId))
    }

    fun close() {
        write { it.copy(mediaSetup = null) }
    }

    fun moveFocus(delta: Int) {
        val current = setup ?: return
        if (current.notice != null) {
            moveNoticeFocus(delta)
            return
        }
        val count = current.rowCount
        if (count <= 0) return
        update { it.copy(focusIndex = (it.focusIndex + delta).mod(count)) }
    }

    fun moveNoticeFocus(delta: Int) {
        val notice = setup?.notice ?: return
        update { it.copy(notice = notice.copy(buttonIndex = (notice.buttonIndex + delta).mod(2))) }
    }

    /**
     * Answers the current question. A row reached by touch names its own index, so the same call
     * serves a tap and a press of confirm.
     */
    fun confirm(index: Int? = null) {
        val current = setup ?: return
        if (current.isLoading) return
        if (index != null) update { it.copy(focusIndex = index) }
        when (current.step) {
            MediaTileStep.MODE -> confirmMode(index ?: current.focusIndex)
            MediaTileStep.SEASON -> confirmSeason(index ?: current.focusIndex)
            MediaTileStep.EPISODES -> confirmEpisode(index ?: current.focusIndex)
        }
    }

    /**
     * Steps back one question, answering false when there is no question behind this one - which is
     * the caller's cue to close the whole run and return to the list of titles.
     */
    fun back(): Boolean {
        val current = setup ?: return false
        if (current.notice != null) {
            update { it.copy(notice = null) }
            return true
        }
        if (current.step == MediaTileStep.MODE) return false
        update {
            it.copy(
                step = MediaTileStep.MODE,
                focusIndex = 0,
                seasons = emptyList(),
                episodes = emptyList(),
                selected = emptyList(),
                scopeId = null,
                error = null
            )
        }
        return true
    }

    fun confirmNotice() {
        val current = setup ?: return
        val notice = current.notice ?: return
        val ids = notice.downloadIds
        scope.launch { catalog?.enqueue(ids) }
        place(current)
    }

    fun dismissNotice() {
        val current = setup ?: return
        val notice = current.notice ?: return
        update { it.copy(notice = null) }
        if (notice.placesOnDecline) place(current)
    }

    private fun confirmMode(index: Int) {
        val current = setup ?: return
        val option = MediaTileModeOption.entries.getOrNull(index) ?: return
        val seriesId = current.entry.mediaItemId ?: return
        update { it.copy(mode = option.mode) }
        when (option.mode) {
            MediaTilePlayMode.SEASON -> loadSeasons(seriesId)
            MediaTilePlayMode.PLAYLIST -> loadEpisodes(seriesId, seasonId = null)
            else -> settleFromSeries(seriesId)
        }
    }

    private fun confirmSeason(index: Int) {
        val current = setup ?: return
        val season = current.seasons.getOrNull(index) ?: return
        val seriesId = current.entry.mediaItemId ?: return
        update { it.copy(scopeId = season.itemId, isLoading = true) }
        scope.launch {
            val episodes = catalog?.episodes(seriesId, season.itemId).orEmpty()
            if (episodes.isEmpty()) {
                update {
                    it.copy(
                        isLoading = false,
                        error = "No episodes have been synced for that season yet"
                    )
                }
                return@launch
            }
            settle(episodes.map { it.itemId })
        }
    }

    /**
     * A press inside the episode list either ticks a row or commits the run, depending on which of
     * the two the cursor is over. Ticking keeps the order the rows were chosen in, because that order
     * is the run and nothing else records it.
     */
    private fun confirmEpisode(index: Int) {
        val current = setup ?: return
        if (index >= current.episodes.size) {
            if (current.selected.isEmpty()) return
            settle(current.selected)
            return
        }
        val option = current.episodes.getOrNull(index) ?: return
        update {
            it.copy(
                selected = if (option.itemId in it.selected) {
                    it.selected - option.itemId
                } else {
                    it.selected + option.itemId
                }
            )
        }
    }

    private fun loadSeasons(seriesId: String) {
        update { it.copy(step = MediaTileStep.SEASON, focusIndex = 0, isLoading = true, error = null) }
        scope.launch {
            val seasons = catalog?.seasons(seriesId).orEmpty()
            update { it.copy(seasons = seasons, isLoading = false) }
        }
    }

    private fun loadEpisodes(seriesId: String, seasonId: String?) {
        update {
            it.copy(
                step = MediaTileStep.EPISODES,
                focusIndex = 0,
                isLoading = true,
                error = null,
                selected = emptyList()
            )
        }
        scope.launch {
            val episodes = catalog?.episodes(seriesId, seasonId).orEmpty()
            update { it.copy(episodes = episodes, isLoading = false) }
        }
    }

    /**
     * A whole-series mode has no chosen set, so what it needs on the device is every episode of the
     * show. That is a large ask and it is exactly why the notice reports a count and, past a handful,
     * a size: the reader agrees to it before anything is queued.
     */
    private fun settleFromSeries(seriesId: String) {
        update { it.copy(isLoading = true, error = null) }
        scope.launch {
            val episodes = catalog?.episodes(seriesId, seasonId = null).orEmpty()
            if (episodes.isEmpty()) {
                update {
                    it.copy(
                        isLoading = false,
                        error = "No episodes have been synced for this series yet"
                    )
                }
                return@launch
            }
            offerBulkDownload(episodes.map { it.itemId })
        }
    }

    /**
     * A tile that works out its own episode plays whatever is on the device and wraps round at the
     * end, so it needs no download to be useful. The whole series is offered rather than fetched:
     * placing a tile is not a standing order for every episode of a long-running show, and a viewer
     * who wants the lot can say so here.
     */
    private fun offerBulkDownload(targets: List<String>) {
        update { it.copy(selected = emptyList(), isLoading = true, error = null) }
        scope.launch {
            val plan = catalog?.planDownloads(targets) ?: MediaTileDownloadPlan()
            val settled = setup ?: return@launch
            if (plan.missingIds.isEmpty()) {
                update { it.copy(isLoading = false) }
                place(settled)
                return@launch
            }
            val onDevice = targets.size - plan.missingIds.size
            update {
                it.copy(
                    isLoading = false,
                    notice = MediaTileNotice(
                        message = derivedModeMessage(onDevice),
                        warning = noticeWarning(plan.missingIds.size, plan.approximateSize),
                        downloadIds = plan.missingIds,
                        buttonIndex = if (onDevice > 0) CANCEL_BUTTON else CONFIRM_BUTTON,
                        confirmLabel = "Download all",
                        declineLabel = if (onDevice > 0) "Use downloaded" else "Cancel",
                        placesOnDecline = onDevice > 0
                    )
                )
            }
        }
    }

    private fun derivedModeMessage(onDevice: Int): String = when (onDevice) {
        0 -> "No episodes of this show are on this device yet. This tile plays downloaded episodes, " +
            "so it stays empty until some arrive."
        1 -> "This tile plays the 1 episode on this device, and starts it again when it ends."
        else -> "This tile plays the $onDevice episodes on this device, in order, looping back to " +
            "the first when it reaches the end."
    }

    /**
     * Decides whether the run can be placed now or has to be asked about first. A batch already on
     * the device is placed without a word, because there is nothing to tell.
     */
    private fun settle(targets: List<String>) {
        val current = setup ?: return
        val selected = if (current.mode == MediaTilePlayMode.PLAYLIST) targets else emptyList()
        update { it.copy(selected = selected, isLoading = true, error = null) }
        scope.launch {
            val plan = catalog?.planDownloads(targets) ?: MediaTileDownloadPlan()
            val settled = setup ?: return@launch
            if (plan.missingIds.isEmpty()) {
                update { it.copy(isLoading = false) }
                place(settled)
                return@launch
            }
            val warning = noticeWarning(plan.missingIds.size, plan.approximateSize)
            update {
                it.copy(
                    isLoading = false,
                    notice = MediaTileNotice(
                        message = noticeMessage(plan.missingIds.size, settled.entry.isSeries),
                        warning = warning,
                        downloadIds = plan.missingIds,
                        buttonIndex = if (warning == null) CONFIRM_BUTTON else CANCEL_BUTTON
                    )
                )
            }
        }
    }

    private fun place(current: MediaTileSetup) {
        val itemId = current.entry.mediaItemId ?: return
        onPlace(
            HomeTileTargetRef.Media(
                itemId = itemId,
                playMode = current.mode ?: MediaTilePlayMode.SINGLE,
                scopeId = current.scopeId
            ),
            current.selected
        )
        close()
    }

    private fun update(transform: (MediaTileSetup) -> MediaTileSetup) {
        write { state ->
            val current = state.mediaSetup ?: return@write state
            state.copy(mediaSetup = transform(current))
        }
    }

    private fun noticeMessage(missing: Int, isSeries: Boolean): String {
        val subject = if (isSeries) "episode" else "title"
        return if (missing == 1) {
            "1 $subject is not on this device yet. Adding this tile starts its download."
        } else {
            "$missing ${subject}s are not on this device yet. " +
                "Adding this tile starts their downloads."
        }
    }

    /**
     * The size, said as the estimate it is. A queued download begins with no size recorded at all and
     * the figure here is worked back from a bitrate until the server has been asked, so the wording
     * has to leave room for it to be wrong in either direction.
     */
    private fun noticeWarning(missing: Int, approximateSize: String?): String? {
        if (missing <= MEDIA_TILE_SIZE_WARNING_THRESHOLD) return null
        if (approximateSize == null) {
            return "That is a large batch, and its size is only known once each download starts."
        }
        return "Roughly $approximateSize in total, give or take - " +
            "each download settles its own size when it starts."
    }
}
