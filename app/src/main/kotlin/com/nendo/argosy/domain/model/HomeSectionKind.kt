package com.nendo.argosy.domain.model

/**
 * The rows a home surface offers, in the order they appear.
 *
 * The single-screen home screen is the authority on this listing; the dual-screen companion carries
 * the same game rows. Declaring the order here rather than in either screen keeps the two from
 * drifting, which they already had: the companion carried only Continue, Favorites and Platform,
 * and listed Steam and Android as ordinary platforms where home splits them into rows of their own.
 *
 * The media kinds are the exception to that sameness, and deliberately: on the companion they live
 * in its media panel rather than in its carousel. The companion's carousel is a game carousel down
 * to its restore key, which identifies a position by game id, and the panel is where the companion
 * is already showing what is being watched. Media reaching the second screen through the surface
 * that exists for media is the parity, not a media row wedged into a rail of games that cannot hold
 * one.
 *
 * The listing is three runs, and [entries] declares them in that order:
 *
 * [LEADING] are the fixed rows that open the surface. [REPEATING] appear once per thing that exists:
 * [PLATFORM] once per platform that has games, excluding the Steam and Android platforms because
 * they are their own rows; [PINNED_REGULAR] and [PINNED_VIRTUAL] once per pinned collection, ordered
 * by descending display order; [MEDIA_LIBRARY] once per Jellyfin library, so a library is a row of
 * its own the way a platform is rather than being folded into an everything-at-once rail.
 * [TRAILING] are the fixed rows that close it.
 *
 * [NEXT_UP] is last of all, and that position is load-bearing rather than incidental: the row cursor
 * wraps, so the last row is one backwards press from the first. The row most worth reaching quickly
 * belongs where reaching it is cheapest.
 *
 * [CONTINUE_WATCHING] and [NEXT_UP] stay two rows rather than one on purpose: continuing offers what
 * was left part-watched, while next up offers the episode after the one that was finished. Folding
 * them together would surface a finished episode as the thing to play next, which is the opposite of
 * what next up means.
 */
enum class HomeSectionKind {
    CONTINUE,
    RECOMMENDATIONS,
    FAVORITES,
    ANDROID,
    STEAM,
    PLATFORM,
    PINNED_REGULAR,
    PINNED_VIRTUAL,
    MEDIA_LIBRARY,
    CONTINUE_WATCHING,
    NEXT_UP;

    companion object {
        /**
         * The fixed rows that precede every repeating row, in order.
         */
        val LEADING: List<HomeSectionKind> = listOf(
            CONTINUE,
            RECOMMENDATIONS,
            FAVORITES,
            ANDROID,
            STEAM
        )

        /**
         * The kinds that appear once per platform, pinned collection or media library, in the order
         * their runs follow one another.
         */
        val REPEATING: List<HomeSectionKind> = listOf(
            PLATFORM,
            PINNED_REGULAR,
            PINNED_VIRTUAL,
            MEDIA_LIBRARY
        )

        /**
         * The fixed rows that close the surface, in order.
         */
        val TRAILING: List<HomeSectionKind> = listOf(
            CONTINUE_WATCHING,
            NEXT_UP
        )

        /**
         * The kinds that come from a media server, whichever run they sit in. Nothing here stands
         * without a signed-in media account.
         */
        val MEDIA: Set<HomeSectionKind> = setOf(MEDIA_LIBRARY, CONTINUE_WATCHING, NEXT_UP)
    }
}
