package com.nendo.argosy.ui.dualscreen

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.nendo.argosy.R
import com.nendo.argosy.ui.components.InputButton

/**
 * What the buttons do on the home surface being driven, spelled out on the screen describing it.
 * One list serves both showcase hosts; [drawerOpen] and [appBarFocused] exist only on the primary
 * display's host and default to absent for the companion's.
 */
@Composable
fun companionHomeHints(
    viewMode: String,
    isDownloaded: Boolean,
    isFavorite: Boolean,
    drawerOpen: Boolean = false,
    appBarFocused: Boolean = false
): List<Pair<InputButton, String>> {
    val actionLabel = if (isDownloaded) {
        stringResource(R.string.dual_home_hint_action_play)
    } else {
        stringResource(R.string.dual_home_hint_action_download)
    }
    return when (viewMode) {
        "COLLECTION_GAMES" -> listOf(
            InputButton.DPAD to stringResource(R.string.dual_home_hint_collection_navigate),
            InputButton.A to actionLabel,
            InputButton.X to stringResource(R.string.dual_home_hint_collection_details),
            InputButton.B to stringResource(R.string.dual_home_hint_collection_back)
        )
        "LIBRARY_GRID" -> listOf(
            InputButton.LB_RB to stringResource(R.string.dual_home_hint_library_platform),
            InputButton.LT_RT to stringResource(R.string.dual_home_hint_library_letter),
            InputButton.A to stringResource(R.string.dual_home_hint_library_details),
            InputButton.X to stringResource(R.string.dual_home_hint_library_options),
            InputButton.Y to stringResource(R.string.dual_home_hint_library_filters),
            InputButton.B to stringResource(R.string.dual_home_hint_library_back)
        )
        "MEDIA_GRID" -> listOf(
            InputButton.LB_RB to stringResource(R.string.dual_home_hint_media_grid_library),
            InputButton.Y to stringResource(R.string.dual_home_hint_media_grid_resume),
            InputButton.X to stringResource(R.string.dual_home_hint_media_grid_options),
            InputButton.A to stringResource(R.string.dual_home_hint_media_grid_play),
            InputButton.B to stringResource(R.string.dual_home_hint_media_grid_back)
        )
        "MEDIA_INFO" -> listOf(
            InputButton.LB_RB to stringResource(R.string.dual_home_hint_media_info_title),
            InputButton.DPAD_HORIZONTAL to
                stringResource(R.string.dual_home_hint_media_info_season),
            InputButton.A to stringResource(R.string.dual_home_hint_media_info_watch),
            InputButton.B to stringResource(R.string.dual_home_hint_media_info_back)
        )
        else -> when {
            drawerOpen -> listOf(
                InputButton.A to stringResource(R.string.dual_home_hint_drawer_open),
                InputButton.X to stringResource(R.string.dual_home_hint_drawer_pin),
                InputButton.Y to stringResource(R.string.dual_home_hint_drawer_open_top),
                InputButton.B to stringResource(R.string.dual_home_hint_drawer_close)
            )
            appBarFocused -> listOf(
                InputButton.A to stringResource(R.string.dual_home_hint_app_bar_select),
                InputButton.Y to stringResource(R.string.dual_home_hint_app_bar_open_top),
                InputButton.SELECT to stringResource(R.string.dual_home_hint_app_bar_all_apps)
            )
            else -> listOf(
                InputButton.LB_RB to stringResource(R.string.dual_home_hint_carousel_platform),
                InputButton.A to actionLabel,
                InputButton.X to stringResource(R.string.dual_home_hint_carousel_details),
                InputButton.Y to if (isFavorite) {
                    stringResource(R.string.dual_home_hint_carousel_unfavorite)
                } else {
                    stringResource(R.string.dual_home_hint_carousel_favorite)
                },
                InputButton.DPAD_UP to
                    stringResource(R.string.dual_home_hint_carousel_collections),
                InputButton.SELECT to stringResource(R.string.dual_home_hint_carousel_library)
            )
        }
    }
}
