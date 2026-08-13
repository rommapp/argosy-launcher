package com.nendo.argosy.domain.model

import com.nendo.argosy.ui.dualscreen.home.DualHomeSection
import com.nendo.argosy.ui.screens.home.HomeRow
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The two home surfaces build their own row lists from their own repositories, so the only thing
 * keeping them in the same order is this enum. These tests fail if either side stops agreeing with
 * it, which is what a shared listing is supposed to guarantee.
 */
class HomeSectionKindTest {

    @Test
    fun `leading order is the one both surfaces build against`() {
        assertEquals(
            listOf(
                HomeSectionKind.CONTINUE,
                HomeSectionKind.CONTINUE_WATCHING,
                HomeSectionKind.NEXT_UP,
                HomeSectionKind.RECOMMENDATIONS,
                HomeSectionKind.FAVORITES,
                HomeSectionKind.ANDROID,
                HomeSectionKind.STEAM
            ),
            HomeSectionKind.LEADING
        )
    }

    @Test
    fun `every leading kind is claimed by a row on the single screen`() {
        val rows = listOf(
            HomeRow.Continue,
            HomeRow.ContinueWatching,
            HomeRow.NextUp,
            HomeRow.Recommendations,
            HomeRow.Favorites,
            HomeRow.Android,
            HomeRow.Steam
        )
        assertEquals(HomeSectionKind.LEADING, rows.map { it.kind })
    }

    /**
     * The companion carries the game rows only. Media rails are single-screen, so the companion is
     * expected to skip exactly those two kinds and nothing else; a game kind going missing here is
     * still the drift this test was written to catch.
     */
    @Test
    fun `every leading game kind is claimed by a section on the companion`() {
        val sections = listOf(
            DualHomeSection.Recent,
            DualHomeSection.Recommendations,
            DualHomeSection.Favorites,
            DualHomeSection.Android,
            DualHomeSection.Steam
        )
        assertEquals(HomeSectionKind.LEADING - MEDIA_KINDS, sections.map { it.kind })
    }

    @Test
    fun `the trailing kinds are the repeating ones and are not in the leading run`() {
        val trailing = HomeSectionKind.entries.filterNot { it in HomeSectionKind.LEADING }
        assertEquals(
            listOf(
                HomeSectionKind.PLATFORM,
                HomeSectionKind.PINNED_REGULAR,
                HomeSectionKind.PINNED_VIRTUAL
            ),
            trailing
        )
    }

    private companion object {
        val MEDIA_KINDS = setOf(HomeSectionKind.CONTINUE_WATCHING, HomeSectionKind.NEXT_UP)
    }
}
