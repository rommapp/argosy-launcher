package com.nendo.argosy.domain.model

/**
 * The rows a home surface offers, in the order they appear.
 *
 * The single-screen home screen is the authority on this listing; the dual-screen companion shows
 * the same set. Declaring the order here rather than in either screen keeps the two from drifting,
 * which they already had: the companion carried only Continue, Favorites and Platform, and listed
 * Steam and Android as ordinary platforms where home splits them into rows of their own.
 *
 * [PLATFORM] repeats once per platform that has games, excluding the Steam and Android platforms
 * because they are their own rows. [PINNED_REGULAR] and [PINNED_VIRTUAL] repeat once per pinned
 * collection, ordered by descending display order.
 *
 * [CONTINUE_WATCHING] and [NEXT_UP] are the media rails, and they are two rows rather than one on
 * purpose: continuing offers what was left part-watched, while next up offers the episode after the
 * one that was finished. Folding them together would surface a finished episode as the thing to
 * play next, which is the opposite of what next up means.
 */
enum class HomeSectionKind {
    CONTINUE,
    CONTINUE_WATCHING,
    NEXT_UP,
    RECOMMENDATIONS,
    FAVORITES,
    ANDROID,
    STEAM,
    PLATFORM,
    PINNED_REGULAR,
    PINNED_VIRTUAL;

    companion object {
        /**
         * The fixed rows that precede the per-platform and per-collection rows, in order.
         */
        val LEADING: List<HomeSectionKind> = listOf(
            CONTINUE,
            CONTINUE_WATCHING,
            NEXT_UP,
            RECOMMENDATIONS,
            FAVORITES,
            ANDROID,
            STEAM
        )
    }
}
