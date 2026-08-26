package com.nendo.argosy.ui.dualscreen

import com.nendo.argosy.ui.components.InputButton

/**
 * What the buttons do on the home surface being driven, spelled out on the screen describing it.
 * One list serves both showcase hosts; [drawerOpen] and [appBarFocused] exist only on the primary
 * display's host and default to absent for the companion's.
 */
fun companionHomeHints(
    viewMode: String,
    isDownloaded: Boolean,
    isFavorite: Boolean,
    drawerOpen: Boolean = false,
    appBarFocused: Boolean = false
): List<Pair<InputButton, String>> {
    val actionLabel = if (isDownloaded) "Play" else "Download"
    return when (viewMode) {
        "COLLECTION_GAMES" -> listOf(
            InputButton.DPAD to "Navigate",
            InputButton.A to actionLabel,
            InputButton.X to "Details",
            InputButton.B to "Back"
        )
        "LIBRARY_GRID" -> listOf(
            InputButton.LB_RB to "Platform",
            InputButton.LT_RT to "Letter",
            InputButton.A to "Details",
            InputButton.X to "Options",
            InputButton.Y to "Filters",
            InputButton.B to "Back"
        )
        "MEDIA_GRID" -> listOf(
            InputButton.LB_RB to "Library",
            InputButton.Y to "Resume",
            InputButton.X to "Options",
            InputButton.A to "Play",
            InputButton.B to "Back"
        )
        "MEDIA_INFO" -> listOf(
            InputButton.LB_RB to "Prev/Next Title",
            InputButton.DPAD_HORIZONTAL to "Season",
            InputButton.A to "Watch",
            InputButton.B to "Back"
        )
        else -> when {
            drawerOpen -> listOf(
                InputButton.A to "Open",
                InputButton.X to "Pin/Unpin",
                InputButton.Y to "Open Top",
                InputButton.B to "Close"
            )
            appBarFocused -> listOf(
                InputButton.A to "Select",
                InputButton.Y to "Open Top",
                InputButton.SELECT to "All Apps"
            )
            else -> listOf(
                InputButton.LB_RB to "Platform",
                InputButton.A to actionLabel,
                InputButton.X to "Details",
                InputButton.Y to if (isFavorite) "Unfavorite" else "Favorite",
                InputButton.DPAD_UP to "Collections",
                InputButton.SELECT to "Library"
            )
        }
    }
}
