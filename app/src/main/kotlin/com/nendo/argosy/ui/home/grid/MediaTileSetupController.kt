package com.nendo.argosy.ui.home.grid

import android.content.Context
import com.nendo.argosy.R
import com.nendo.argosy.data.local.entity.MediaTilePlayMode
import com.nendo.argosy.domain.model.HomeTileTargetRef
import com.nendo.argosy.ui.components.CustomGridState
import com.nendo.argosy.ui.components.EpisodePickerState
import com.nendo.argosy.ui.components.EpisodeSelection
import com.nendo.argosy.ui.components.buildEpisodePickerRows
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
    private val context: Context,
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
        if (current.step == MediaTileStep.EPISODES) {
            update { it.copy(picker = it.picker.move(delta)) }
            return
        }
        val count = current.rowCount
        if (count <= 0) return
        update { it.copy(focusIndex = (it.focusIndex + delta).mod(count)) }
    }

    /**
     * Folds a season away in the chooser, or steps out of the list to its actions. Nothing else in
     * the run answers sideways, so the other steps refuse it.
     */
    fun moveSideways(towardsEnd: Boolean) {
        val current = setup ?: return
        if (current.notice != null || current.isLoading) return
        if (current.step != MediaTileStep.EPISODES) return
        update { it.copy(picker = it.picker.moveSideways(towardsEnd)) }
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
        if (current.step == MediaTileStep.EPISODES) {
            confirmEpisode(index ?: current.picker.focusedIndex)
            return
        }
        if (index != null) update { it.copy(focusIndex = index) }
        when (current.step) {
            MediaTileStep.MODE -> confirmMode(index ?: current.focusIndex)
            MediaTileStep.SEASON -> confirmSeason(index ?: current.focusIndex)
            MediaTileStep.EPISODES -> Unit
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
                picker = EpisodePickerState(),
                selected = emptyList(),
                scopeId = null,
                errorRes = null
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
                        errorRes = R.string.ui_media_tile_error_season_not_synced
                    )
                }
                return@launch
            }
            settle(episodes.map { it.itemId })
        }
    }

    /**
     * A press inside the chooser ticks a row, steps back a question, or commits the run, depending on
     * which of the three the cursor is over. Ticking keeps the order the rows were chosen in, because
     * that order is the run and nothing else records it.
     */
    private fun confirmEpisode(index: Int) {
        val current = setup ?: return
        val picker = current.picker.focus(index)
        update { it.copy(picker = picker) }
        when {
            picker.isCancelFocused -> back()
            picker.isConfirmFocused ->
                if (picker.hasSelection) settle(picker.selection.selected)
            picker.focusedQuickAction != null ->
                update { it.copy(picker = it.picker.applyFocusedQuickAction()) }
            else -> update { it.copy(picker = it.picker.toggleFocused()) }
        }
    }

    private fun loadSeasons(seriesId: String) {
        update {
            it.copy(
                step = MediaTileStep.SEASON,
                focusIndex = 0,
                isLoading = true,
                errorRes = null
            )
        }
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
                errorRes = null,
                picker = EpisodePickerState(),
                selected = emptyList()
            )
        }
        scope.launch {
            val episodes = catalog?.episodes(seriesId, seasonId).orEmpty()
            val rows = buildEpisodePickerRows(context, episodes)
            update {
                it.copy(
                    picker = EpisodePickerState(selection = EpisodeSelection(rows = rows)),
                    isLoading = false
                )
            }
        }
    }

    /**
     * A whole-series mode has no chosen set, so what it needs on the device is every episode of the
     * show. That is a large ask and it is exactly why the notice reports a count and, past a handful,
     * a size: the reader agrees to it before anything is queued.
     */
    private fun settleFromSeries(seriesId: String) {
        update { it.copy(isLoading = true, errorRes = null) }
        scope.launch {
            val episodes = catalog?.episodes(seriesId, seasonId = null).orEmpty()
            if (episodes.isEmpty()) {
                update {
                    it.copy(
                        isLoading = false,
                        errorRes = R.string.ui_media_tile_error_series_not_synced
                    )
                }
                return@launch
            }
            offerBulkDownload(episodes.map { it.itemId })
        }
    }

    /**
     * Offers the series rather than fetching it. A derived-mode tile plays what is already on the
     * device, so the download is optional.
     */
    private fun offerBulkDownload(targets: List<String>) {
        update { it.copy(selected = emptyList(), isLoading = true, errorRes = null) }
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
                        confirmLabelRes = R.string.media_tile_notice_confirm_download_all,
                        declineLabelRes = if (onDevice > 0) {
                            R.string.media_tile_notice_decline_use_downloaded
                        } else {
                            R.string.media_tile_notice_decline_cancel
                        },
                        placesOnDecline = onDevice > 0
                    )
                )
            }
        }
    }

    private fun derivedModeMessage(onDevice: Int): String = when (onDevice) {
        0 -> context.getString(R.string.media_tile_notice_derived_message_none)
        else -> context.resources.getQuantityString(
            R.plurals.media_tile_notice_derived_message_some,
            onDevice,
            onDevice
        )
    }

    /**
     * Decides whether the run can be placed now or has to be asked about first. A batch already on
     * the device is placed without a word, because there is nothing to tell.
     */
    private fun settle(targets: List<String>) {
        val current = setup ?: return
        val selected = if (current.mode == MediaTilePlayMode.PLAYLIST) targets else emptyList()
        update { it.copy(selected = selected, isLoading = true, errorRes = null) }
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
        val plural = if (isSeries) {
            R.plurals.media_tile_notice_missing_episodes
        } else {
            R.plurals.media_tile_notice_missing_titles
        }
        return context.resources.getQuantityString(plural, missing, missing)
    }

    /**
     * The size, said as the estimate it is. A queued download begins with no size recorded at all and
     * the figure here is worked back from a bitrate until the server has been asked, so the wording
     * has to leave room for it to be wrong in either direction.
     */
    private fun noticeWarning(missing: Int, approximateSize: String?): String? {
        if (missing <= MEDIA_TILE_SIZE_WARNING_THRESHOLD) return null
        if (approximateSize == null) {
            return context.getString(R.string.media_tile_notice_warning_unknown_size)
        }
        return context.getString(R.string.media_tile_notice_warning_approximate_size, approximateSize)
    }
}
