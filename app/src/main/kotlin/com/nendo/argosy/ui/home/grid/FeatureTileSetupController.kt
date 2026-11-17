package com.nendo.argosy.ui.home.grid

import com.nendo.argosy.data.model.ActiveSort
import com.nendo.argosy.data.model.SortOption
import com.nendo.argosy.data.model.SourceFilter
import com.nendo.argosy.domain.model.CompletionStatus
import com.nendo.argosy.domain.model.FeatureTileKind
import com.nendo.argosy.domain.model.HomeTile
import com.nendo.argosy.domain.model.HomeTileTargetRef
import com.nendo.argosy.domain.model.LibraryLinkFilters
import com.nendo.argosy.domain.model.PlayerCountBucket
import com.nendo.argosy.domain.model.RaProgressFilter
import com.nendo.argosy.domain.model.RandomTileFilters
import com.nendo.argosy.ui.components.CustomGridState
import com.nendo.argosy.ui.components.FeatureFilterOptions
import com.nendo.argosy.ui.components.FeatureSetupRow
import com.nendo.argosy.ui.components.FeatureSetupStep
import com.nendo.argosy.ui.components.FeatureTileSetup
import com.nendo.argosy.ui.components.RECENTLY_ADDED_DAYS
import com.nendo.argosy.ui.components.ReleaseYearPreset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Drives the feature tiles' setup questions: the random game tile's filters and the
 * RetroAchievements tile's mode. Owns no state of its own: the run lives on
 * [CustomGridState.featureSetup] so the gamepad and touch reach it through the same reads and
 * writes as the rest of the grid.
 *
 * [onTrackGame] hands the screen to a game picker while the setup waits underneath; the pick comes
 * back through [trackGame], so the setup settles it the same way it settles every other answer.
 */
class FeatureTileSetupController(
    private val scope: CoroutineScope,
    private val filterOptions: suspend () -> FeatureFilterOptions,
    private val read: () -> CustomGridState,
    private val write: ((CustomGridState) -> CustomGridState) -> Unit,
    private val onPlace: (HomeTileTargetRef.Feature) -> Unit,
    private val onEdit: (Long, HomeTileTargetRef.Feature) -> Unit,
    private val onTrackGame: () -> Unit = {},
    private val onFolderRule: (Long?, LibraryLinkFilters) -> Unit = { _, _ -> }
) {
    private val setup: FeatureTileSetup? get() = read().featureSetup

    /**
     * Asks an auto-folder's rule. [existing] is the folder being edited, or null when the answer
     * becomes a new folder.
     */
    fun beginFolderRule(existing: HomeTile? = null) {
        val rule = (existing?.target as? HomeTileTargetRef.Folder)?.rule ?: LibraryLinkFilters()
        scope.launch {
            val options = filterOptions()
            write {
                it.copy(
                    featureSetup = FeatureTileSetup(
                        kind = FeatureTileKind.LIBRARY_LINK,
                        editingTileId = existing?.id,
                        filters = RandomTileFilters(),
                        platforms = options.platforms,
                        genres = options.genres,
                        series = options.series,
                        developers = options.developers,
                        libraryLink = rule,
                        forFolder = true
                    )
                )
            }
        }
    }

    fun begin(existing: HomeTile? = null, kind: FeatureTileKind = FeatureTileKind.RANDOM_GAME) {
        val target = existing?.target as? HomeTileTargetRef.Feature
        val effectiveKind = target?.kind ?: kind
        val current = target?.filters ?: RandomTileFilters()
        val currentLink = target?.libraryLink ?: LibraryLinkFilters()
        if (effectiveKind == FeatureTileKind.RA_SUMMARY) {
            write {
                it.copy(
                    featureSetup = FeatureTileSetup(
                        kind = effectiveKind,
                        editingTileId = existing?.id,
                        filters = current,
                        platforms = emptyList(),
                        genres = emptyList(),
                        pickedGameId = target?.pickedGameId,
                        focusIndex = if (target?.pickedGameId != null) {
                            FeatureTileSetup.ROW_MODE_TRACK
                        } else {
                            FeatureTileSetup.ROW_MODE_ACCOUNT
                        }
                    )
                )
            }
            return
        }
        scope.launch {
            val options = filterOptions()
            write {
                it.copy(
                    featureSetup = FeatureTileSetup(
                        kind = effectiveKind,
                        editingTileId = existing?.id,
                        filters = current,
                        platforms = options.platforms,
                        genres = options.genres,
                        series = options.series,
                        libraryLink = currentLink,
                        pickedGameId = target?.pickedGameId
                    )
                )
            }
        }
    }

    fun close() = write { it.copy(featureSetup = null) }

    fun moveFocus(delta: Int) {
        val current = setup ?: return
        val count = current.rowCount
        if (count == 0) return
        update { it.copy(focusIndex = (it.focusIndex + delta).mod(count)) }
    }

    fun confirm(index: Int? = null) {
        val current = setup ?: return
        val row = index ?: current.focusIndex
        when (current.step) {
            FeatureSetupStep.MODE -> confirmModeRow(row)
            FeatureSetupStep.FILTERS -> confirmFilterRow(row)
            FeatureSetupStep.PLATFORMS -> current.platforms.getOrNull(row)?.let { option ->
                if (current.kind == FeatureTileKind.LIBRARY_LINK) {
                    updateLink { it.copy(platformIds = it.platformIds.toggled(option.id)) }
                } else {
                    updateFilters { it.copy(platformIds = it.platformIds.toggled(option.id)) }
                }
            }
            FeatureSetupStep.GENRES -> current.genres.getOrNull(row)?.let { genre ->
                if (current.kind == FeatureTileKind.LIBRARY_LINK) {
                    updateLink { it.copy(genres = it.genres.toggled(genre)) }
                } else {
                    updateFilters { it.copy(genres = it.genres.toggled(genre)) }
                }
            }
            FeatureSetupStep.SERIES -> current.series.getOrNull(row)?.let { name ->
                updateLink { it.copy(series = it.series.toggled(name)) }
            }
            FeatureSetupStep.PLAYERS -> updateLink {
                it.copy(players = PlayerCountBucket.entries.getOrNull(row - 1))
            }
            FeatureSetupStep.DEVELOPERS -> current.developers.getOrNull(row)?.let { name ->
                updateLink { it.copy(developers = it.developers.toggled(name)) }
            }
            FeatureSetupStep.RELEASE_YEAR -> ReleaseYearPreset.entries.getOrNull(row)?.let { preset ->
                updateLink { it.copy(releaseYearFrom = preset.from, releaseYearTo = preset.to) }
            }
            FeatureSetupStep.PLAY_STATUS -> CompletionStatus.entries.getOrNull(row)?.let { status ->
                updateLink { it.copy(playStatuses = it.playStatuses.toggled(status.apiValue)) }
            }
            FeatureSetupStep.SORT -> SortOption.entries.getOrNull(row)?.let { option ->
                updateLink {
                    val flip = it.sort.option == option
                    it.copy(
                        sort = ActiveSort(
                            option = option,
                            descending = if (flip) !it.sort.descending else option.defaultDescending
                        )
                    )
                }
            }
        }
        if (index != null && setup != null) update { it.copy(focusIndex = index) }
    }

    /**
     * Returns to the filter list from a sub-list, landing on the row that opened it. Answers
     * whether there was anything to go back to.
     */
    fun back(): Boolean {
        val current = setup ?: return false
        val returnRow = when (current.step) {
            FeatureSetupStep.MODE, FeatureSetupStep.FILTERS -> return false
            FeatureSetupStep.PLATFORMS -> current.indexOf(FeatureSetupRow.PLATFORMS)
            FeatureSetupStep.GENRES -> current.indexOf(FeatureSetupRow.GENRES)
            FeatureSetupStep.SERIES -> current.indexOf(FeatureSetupRow.SERIES)
            FeatureSetupStep.PLAYERS -> current.indexOf(FeatureSetupRow.PLAYERS)
            FeatureSetupStep.SORT -> current.indexOf(FeatureSetupRow.SORT)
            FeatureSetupStep.DEVELOPERS -> current.indexOf(FeatureSetupRow.DEVELOPERS)
            FeatureSetupStep.RELEASE_YEAR -> current.indexOf(FeatureSetupRow.RELEASE_YEAR)
            FeatureSetupStep.PLAY_STATUS -> current.indexOf(FeatureSetupRow.PLAY_STATUS)
        }
        update { it.copy(step = FeatureSetupStep.FILTERS, focusIndex = returnRow) }
        return true
    }

    /**
     * Settles the RetroAchievements tile on the game the picker named. The setup is still the one
     * that opened the picker, so the tile it was placing or editing is the one that changes.
     */
    fun trackGame(gameId: Long) {
        val current = setup ?: return
        if (current.kind != FeatureTileKind.RA_SUMMARY) return
        settle(HomeTileTargetRef.Feature(FeatureTileKind.RA_SUMMARY, pickedGameId = gameId))
    }

    private fun confirmModeRow(row: Int) {
        when (row) {
            FeatureTileSetup.ROW_MODE_ACCOUNT ->
                settle(HomeTileTargetRef.Feature(FeatureTileKind.RA_SUMMARY, pickedGameId = null))
            FeatureTileSetup.ROW_MODE_TRACK -> onTrackGame()
        }
    }

    private fun confirmFilterRow(row: Int) {
        when (setup?.rowAt(row)) {
            FeatureSetupRow.DOWNLOADED_ONLY ->
                updateFilters { it.copy(downloadedOnly = !it.downloadedOnly) }
            FeatureSetupRow.NEVER_PLAYED ->
                updateFilters { it.copy(neverPlayed = !it.neverPlayed) }
            FeatureSetupRow.SOURCE -> updateLink {
                it.copy(source = SourceFilter.entries.next(it.source))
            }
            FeatureSetupRow.PLATFORMS ->
                update { it.copy(step = FeatureSetupStep.PLATFORMS, focusIndex = 0) }
            FeatureSetupRow.GENRES ->
                update { it.copy(step = FeatureSetupStep.GENRES, focusIndex = 0) }
            FeatureSetupRow.SERIES ->
                update { it.copy(step = FeatureSetupStep.SERIES, focusIndex = 0) }
            FeatureSetupRow.PLAYERS ->
                update { it.copy(step = FeatureSetupStep.PLAYERS, focusIndex = 0) }
            FeatureSetupRow.SORT ->
                update { it.copy(step = FeatureSetupStep.SORT, focusIndex = 0) }
            FeatureSetupRow.DEVELOPERS ->
                update { it.copy(step = FeatureSetupStep.DEVELOPERS, focusIndex = 0) }
            FeatureSetupRow.RELEASE_YEAR ->
                update { it.copy(step = FeatureSetupStep.RELEASE_YEAR, focusIndex = 0) }
            FeatureSetupRow.PLAY_STATUS ->
                update { it.copy(step = FeatureSetupStep.PLAY_STATUS, focusIndex = 0) }
            FeatureSetupRow.RECENTLY_ADDED -> updateLink {
                it.copy(addedWithinDays = RECENTLY_ADDED_DAYS.next(it.addedWithinDays))
            }
            FeatureSetupRow.RA_PROGRESS -> updateLink {
                it.copy(raProgress = RA_PROGRESS_STEPS.next(it.raProgress))
            }
            FeatureSetupRow.DONE -> setup?.let { current ->
                if (current.forFolder) {
                    onFolderRule(current.editingTileId, current.libraryLink)
                    close()
                    return
                }
                settle(
                    HomeTileTargetRef.Feature(
                        kind = current.kind,
                        filters = current.filters,
                        pickedGameId = current.pickedGameId,
                        libraryLink = current.libraryLink
                            .takeIf { current.kind == FeatureTileKind.LIBRARY_LINK }
                    )
                )
            }
            null -> Unit
        }
    }

    private fun settle(target: HomeTileTargetRef.Feature) {
        val current = setup ?: return
        val editing = current.editingTileId
        if (editing == null) onPlace(target) else onEdit(editing, target)
        close()
    }

    private fun update(transform: (FeatureTileSetup) -> FeatureTileSetup) =
        write { state -> state.copy(featureSetup = state.featureSetup?.let(transform)) }

    private fun updateFilters(transform: (RandomTileFilters) -> RandomTileFilters) =
        update { it.copy(filters = transform(it.filters)) }

    private fun updateLink(transform: (LibraryLinkFilters) -> LibraryLinkFilters) =
        update { it.copy(libraryLink = transform(it.libraryLink)) }

    private fun <T> Set<T>.toggled(value: T): Set<T> =
        if (value in this) this - value else this + value

    private fun <T> List<T>.next(current: T): T =
        getOrNull((indexOf(current) + 1).mod(size)) ?: current

    private companion object {
        val RA_PROGRESS_STEPS: List<RaProgressFilter?> = listOf(null) + RaProgressFilter.entries
    }
}
