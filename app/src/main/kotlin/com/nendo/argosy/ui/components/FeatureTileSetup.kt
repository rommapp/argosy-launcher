package com.nendo.argosy.ui.components

import androidx.annotation.StringRes
import com.nendo.argosy.R
import com.nendo.argosy.domain.model.RandomTileFilters

enum class FeatureSetupStep { FILTERS, PLATFORMS, GENRES }

/**
 * A platform the random tile can be limited to. The id is what gets stored; the label is only
 * for the row that offers it.
 */
data class FeatureSetupOption(val id: Long, val label: String)

data class FeatureFilterOptions(
    val platforms: List<FeatureSetupOption>,
    val genres: List<String>
)

/**
 * The filter questions asked before a random game tile is placed, or again when its filters are
 * edited. One value with a step, for the same reason the media setup is: back means the previous
 * question, and only one is ever on screen.
 *
 * [editingTileId] is the tile whose filters are being changed, or null when the run ends by
 * placing a new tile on the focused cell.
 */
data class FeatureTileSetup(
    val editingTileId: Long?,
    val filters: RandomTileFilters,
    val platforms: List<FeatureSetupOption>,
    val genres: List<String>,
    val step: FeatureSetupStep = FeatureSetupStep.FILTERS,
    val focusIndex: Int = 0
) {
    val rowCount: Int
        get() = when (step) {
            FeatureSetupStep.FILTERS -> FILTER_ROW_COUNT
            FeatureSetupStep.PLATFORMS -> platforms.size
            FeatureSetupStep.GENRES -> genres.size
        }

    @get:StringRes
    val subtitleRes: Int
        get() = when (step) {
            FeatureSetupStep.FILTERS -> R.string.ui_feature_setup_step_filters
            FeatureSetupStep.PLATFORMS -> R.string.ui_feature_setup_step_platforms
            FeatureSetupStep.GENRES -> R.string.ui_feature_setup_step_genres
        }

    companion object {
        const val ROW_DOWNLOADED_ONLY = 0
        const val ROW_NEVER_PLAYED = 1
        const val ROW_PLATFORMS = 2
        const val ROW_GENRES = 3
        const val ROW_DONE = 4
        const val FILTER_ROW_COUNT = 5
    }
}
