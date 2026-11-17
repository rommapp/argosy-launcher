package com.nendo.argosy.ui.components

import androidx.annotation.StringRes
import com.nendo.argosy.R
import com.nendo.argosy.data.model.SortOption
import com.nendo.argosy.domain.model.CompletionStatus
import com.nendo.argosy.domain.model.FeatureTileKind
import com.nendo.argosy.domain.model.LibraryLinkFilters
import com.nendo.argosy.domain.model.PlayerCountBucket
import com.nendo.argosy.domain.model.RandomTileFilters

enum class FeatureSetupStep {
    MODE,
    FILTERS,
    PLATFORMS,
    GENRES,
    SERIES,
    PLAYERS,
    SORT,
    DEVELOPERS,
    RELEASE_YEAR,
    PLAY_STATUS
}

/**
 * The release year ranges an auto-folder rule offers. The bounds are what gets stored.
 */
enum class ReleaseYearPreset(val from: Int?, val to: Int?) {
    ANY(null, null),
    BEFORE_1990(null, 1989),
    YEARS_1990S(1990, 1999),
    YEARS_2000S(2000, 2009),
    YEARS_2010S(2010, 2019),
    SINCE_2020(2020, null);

    @get:StringRes
    val labelRes: Int
        get() = when (this) {
            ANY -> R.string.ui_feature_setup_year_any
            BEFORE_1990 -> R.string.ui_feature_setup_year_before_1990
            YEARS_1990S -> R.string.ui_feature_setup_year_1990s
            YEARS_2000S -> R.string.ui_feature_setup_year_2000s
            YEARS_2010S -> R.string.ui_feature_setup_year_2010s
            SINCE_2020 -> R.string.ui_feature_setup_year_since_2020
        }

    companion object {
        fun of(filters: LibraryLinkFilters): ReleaseYearPreset? =
            entries.find { it.from == filters.releaseYearFrom && it.to == filters.releaseYearTo }
    }
}

/**
 * The windows the recently added row steps through, in days; null is no limit.
 */
val RECENTLY_ADDED_DAYS: List<Int?> = listOf(null, 7, 30, 90)

/**
 * One row of the filter question. The list a kind asks is [FeatureTileSetup.filterRows]; the
 * modal, the gamepad and the row count all read it, so a kind that asks a different set of
 * questions changes in one place.
 */
enum class FeatureSetupRow {
    DOWNLOADED_ONLY,
    NEVER_PLAYED,
    SOURCE,
    PLATFORMS,
    GENRES,
    SERIES,
    PLAYERS,
    SORT,
    DEVELOPERS,
    RELEASE_YEAR,
    PLAY_STATUS,
    RECENTLY_ADDED,
    RA_PROGRESS,
    DONE
}

/**
 * A platform a tile can be limited to. The id is what gets stored; [label] names it in the row
 * that offers it and [shortLabel] on the tile, where several names share one cell.
 */
data class FeatureSetupOption(
    val id: Long,
    val label: String,
    val shortLabel: String = label
)

data class FeatureFilterOptions(
    val platforms: List<FeatureSetupOption>,
    val genres: List<String>,
    val series: List<String> = emptyList(),
    val developers: List<String> = emptyList()
)

/**
 * The questions asked before a feature tile is placed, or again when it is edited. A random game
 * tile asks about its filters; the RetroAchievements tile asks one question, whether it shows the
 * account or follows a game. One value with a step, for the same reason the media setup is: back
 * means the previous question, and only one is ever on screen.
 *
 * [editingTileId] is the tile being changed, or null when the run ends by placing a new tile on
 * the focused cell. [pickedGameId] is the game an RetroAchievements tile already follows.
 */
data class FeatureTileSetup(
    val kind: FeatureTileKind,
    val editingTileId: Long?,
    val filters: RandomTileFilters,
    val platforms: List<FeatureSetupOption>,
    val genres: List<String>,
    val series: List<String> = emptyList(),
    val libraryLink: LibraryLinkFilters = LibraryLinkFilters(),
    val pickedGameId: Long? = null,
    val step: FeatureSetupStep = if (kind == FeatureTileKind.RA_SUMMARY) {
        FeatureSetupStep.MODE
    } else {
        FeatureSetupStep.FILTERS
    },
    val focusIndex: Int = 0,
    val forFolder: Boolean = false,
    val developers: List<String> = emptyList()
) {
    /**
     * A library link asks the library's own filter categories; a random tile asks what it may
     * pick from. Narrowing to downloaded or never-played games has no counterpart in the library,
     * and the library's source, series, players and sort have none in a random pick, so neither
     * kind is offered a row the other owns. An auto-folder's rule asks the library's categories
     * plus the ones only a folder can apply, and no sort.
     */
    val filterRows: List<FeatureSetupRow>
        get() = when {
            forFolder -> listOf(
                FeatureSetupRow.SOURCE,
                FeatureSetupRow.PLATFORMS,
                FeatureSetupRow.GENRES,
                FeatureSetupRow.SERIES,
                FeatureSetupRow.DEVELOPERS,
                FeatureSetupRow.PLAYERS,
                FeatureSetupRow.RELEASE_YEAR,
                FeatureSetupRow.PLAY_STATUS,
                FeatureSetupRow.RECENTLY_ADDED,
                FeatureSetupRow.RA_PROGRESS,
                FeatureSetupRow.DONE
            )
            else -> kindRows
        }

    private val kindRows: List<FeatureSetupRow>
        get() = when (kind) {
            FeatureTileKind.LIBRARY_LINK -> listOf(
                FeatureSetupRow.SOURCE,
                FeatureSetupRow.PLATFORMS,
                FeatureSetupRow.GENRES,
                FeatureSetupRow.SERIES,
                FeatureSetupRow.PLAYERS,
                FeatureSetupRow.SORT,
                FeatureSetupRow.DONE
            )
            FeatureTileKind.RANDOM_GAME,
            FeatureTileKind.CONTINUE,
            FeatureTileKind.RA_SUMMARY -> listOf(
                FeatureSetupRow.DOWNLOADED_ONLY,
                FeatureSetupRow.NEVER_PLAYED,
                FeatureSetupRow.PLATFORMS,
                FeatureSetupRow.GENRES,
                FeatureSetupRow.DONE
            )
        }

    /**
     * The platforms and genres currently chosen, whichever filter the kind stores them in. The
     * sub-lists render and toggle through these, so neither has to know which kind it is serving.
     */
    val selectedPlatformIds: Set<Long>
        get() = if (kind == FeatureTileKind.LIBRARY_LINK) {
            libraryLink.platformIds
        } else {
            filters.platformIds
        }

    val selectedGenres: Set<String>
        get() = if (kind == FeatureTileKind.LIBRARY_LINK) libraryLink.genres else filters.genres

    fun rowAt(index: Int): FeatureSetupRow? = filterRows.getOrNull(index)

    fun indexOf(row: FeatureSetupRow): Int = filterRows.indexOf(row).coerceAtLeast(0)

    val rowCount: Int
        get() = when (step) {
            FeatureSetupStep.MODE -> MODE_ROW_COUNT
            FeatureSetupStep.FILTERS -> filterRows.size
            FeatureSetupStep.PLATFORMS -> platforms.size
            FeatureSetupStep.GENRES -> genres.size
            FeatureSetupStep.SERIES -> series.size
            FeatureSetupStep.PLAYERS -> PlayerCountBucket.entries.size + 1
            FeatureSetupStep.SORT -> SortOption.entries.size
            FeatureSetupStep.DEVELOPERS -> developers.size
            FeatureSetupStep.RELEASE_YEAR -> ReleaseYearPreset.entries.size
            FeatureSetupStep.PLAY_STATUS -> CompletionStatus.entries.size
        }

    @get:StringRes
    val titleRes: Int
        get() = if (forFolder) R.string.ui_feature_setup_title_auto_folder else when (kind) {
            FeatureTileKind.RA_SUMMARY -> R.string.ui_feature_setup_title_ra
            FeatureTileKind.LIBRARY_LINK -> R.string.ui_feature_setup_title_library_link
            FeatureTileKind.RANDOM_GAME,
            FeatureTileKind.CONTINUE -> R.string.ui_feature_setup_title
        }

    @get:StringRes
    val subtitleRes: Int
        get() = when (step) {
            FeatureSetupStep.MODE -> R.string.ui_feature_setup_step_mode
            FeatureSetupStep.FILTERS -> when {
                forFolder -> R.string.ui_feature_setup_step_auto_folder
                kind == FeatureTileKind.LIBRARY_LINK -> R.string.ui_feature_setup_step_library_link
                else -> R.string.ui_feature_setup_step_filters
            }
            FeatureSetupStep.PLATFORMS -> R.string.ui_feature_setup_step_platforms
            FeatureSetupStep.GENRES -> R.string.ui_feature_setup_step_genres
            FeatureSetupStep.SERIES -> R.string.ui_feature_setup_step_series
            FeatureSetupStep.PLAYERS -> R.string.ui_feature_setup_step_players
            FeatureSetupStep.SORT -> R.string.ui_feature_setup_step_sort
            FeatureSetupStep.DEVELOPERS -> R.string.ui_feature_setup_step_developers
            FeatureSetupStep.RELEASE_YEAR -> R.string.ui_feature_setup_step_release_year
            FeatureSetupStep.PLAY_STATUS -> R.string.ui_feature_setup_step_play_status
        }

    companion object {
        const val ROW_MODE_ACCOUNT = 0
        const val ROW_MODE_TRACK = 1
        const val MODE_ROW_COUNT = 2
    }
}
