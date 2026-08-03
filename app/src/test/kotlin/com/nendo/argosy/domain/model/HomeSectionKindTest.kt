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
            HomeRow.Recommendations,
            HomeRow.Favorites,
            HomeRow.Android,
            HomeRow.Steam
        )
        assertEquals(HomeSectionKind.LEADING, rows.map { it.kind })
    }

    @Test
    fun `every leading kind is claimed by a section on the companion`() {
        val sections = listOf(
            DualHomeSection.Recent,
            DualHomeSection.Recommendations,
            DualHomeSection.Favorites,
            DualHomeSection.Android,
            DualHomeSection.Steam
        )
        assertEquals(HomeSectionKind.LEADING, sections.map { it.kind })
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
}
