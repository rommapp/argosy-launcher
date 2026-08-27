package com.nendo.argosy.ui.screens.collections

import androidx.annotation.StringRes
import com.nendo.argosy.R

/**
 * The Browse By rows, identified by kind rather than by the text on them.
 *
 * Gamepad confirm used to route by comparing the focused row's display label, so translating
 * those labels would have left A doing nothing on a controller while touch carried on working.
 * [route] is the navigation token and is not display text; [labelRes] is the half that shows.
 */
enum class VirtualBrowseKind(val route: String, @StringRes val labelRes: Int) {
    GENRES("genres", R.string.collections_browse_genres),
    GAME_MODES("modes", R.string.collections_browse_game_modes),
    SERIES("series", R.string.collections_browse_series)
}
