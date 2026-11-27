package com.nendo.argosy.ui.screens.home

import com.nendo.argosy.ui.screens.home.BandBadgePart.COMMUNITY_RATING
import com.nendo.argosy.ui.screens.home.BandBadgePart.DIFFICULTY
import com.nendo.argosy.ui.screens.home.BandBadgePart.FRIENDS
import com.nendo.argosy.ui.screens.home.BandBadgePart.STATUS
import com.nendo.argosy.ui.screens.home.BandBadgePart.STATUS_ICON
import com.nendo.argosy.ui.screens.home.BandBadgePart.USER_RATING
import com.nendo.argosy.ui.screens.home.BandMetaPart.GENRE
import com.nendo.argosy.ui.screens.home.BandMetaPart.PLATFORM
import com.nendo.argosy.ui.screens.home.BandMetaPart.YEAR
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeColumnsFitTest {

    @Test
    fun `the AYN Thor bottom screen is compact`() {
        assertEquals(HomeColumnsBandTier.COMPACT, homeColumnsBandTier(620, 540))
    }

    @Test
    fun `a 1080p screen keeps the full band`() {
        assertEquals(HomeColumnsBandTier.FULL, homeColumnsBandTier(960, 540))
    }

    @Test
    fun `a 16 by 9 handheld keeps the full band`() {
        assertEquals(HomeColumnsBandTier.FULL, homeColumnsBandTier(730, 411))
    }

    @Test
    fun `a portrait phone stacks`() {
        assertEquals(HomeColumnsBandTier.STACKED, homeColumnsBandTier(412, 915))
    }

    @Test
    fun `a wide screen under the compact width is compact`() {
        assertEquals(HomeColumnsBandTier.COMPACT, homeColumnsBandTier(599, 337))
    }

    @Test
    fun `four by three and three by two screens are compact`() {
        assertEquals(HomeColumnsBandTier.COMPACT, homeColumnsBandTier(1024, 768))
        assertEquals(HomeColumnsBandTier.COMPACT, homeColumnsBandTier(960, 640))
    }

    @Test
    fun `a square screen stacks`() {
        assertEquals(HomeColumnsBandTier.STACKED, homeColumnsBandTier(540, 540))
    }

    @Test
    fun `a platform name that fits stays whole`() {
        assertEquals("Game Boy", platformLabel("Game Boy", "GB") { it.length <= 10 })
    }

    @Test
    fun `a platform name that would truncate uses its short name`() {
        assertEquals(
            "SNES",
            platformLabel("Super Nintendo Entertainment System", "SNES") { it.length <= 10 }
        )
    }

    @Test
    fun `a platform with no short name keeps its full name`() {
        assertEquals("Supervision Deluxe", platformLabel("Supervision Deluxe", " ") { false })
    }

    private fun metaLength(name: String, parts: List<BandMetaPart>): Int = parts.sumOf {
        when (it) {
            PLATFORM -> name.length
            YEAR -> 4
            GENRE -> 6
        }
    }

    @Test
    fun `the meta line keeps the full platform name when everything fits`() {
        val fit = fitBandMeta("Nintendo 64", "N64", listOf(PLATFORM, YEAR, GENRE)) { name, parts ->
            metaLength(name, parts) <= 30
        }
        assertEquals(BandMetaFit("Nintendo 64", listOf(PLATFORM, YEAR, GENRE)), fit)
    }

    @Test
    fun `the meta line shortens the platform before dropping anything`() {
        val fit = fitBandMeta("Nintendo Entertainment System", "NES", listOf(PLATFORM, YEAR, GENRE)) { name, parts ->
            metaLength(name, parts) <= 13
        }
        assertEquals(BandMetaFit("NES", listOf(PLATFORM, YEAR, GENRE)), fit)
    }

    @Test
    fun `the meta line drops genre then year`() {
        val parts = listOf(PLATFORM, YEAR, GENRE)
        val withoutGenre = fitBandMeta("Nintendo Entertainment System", "NES", parts) { name, kept ->
            metaLength(name, kept) <= 7
        }
        val platformOnly = fitBandMeta("Nintendo Entertainment System", "NES", parts) { name, kept ->
            metaLength(name, kept) <= 3
        }
        assertEquals(listOf(PLATFORM, YEAR), withoutGenre.parts)
        assertEquals(listOf(PLATFORM), platformOnly.parts)
        assertEquals("NES", platformOnly.platformName)
    }

    private val badgeWidths = mapOf(
        FRIENDS to 30,
        COMMUNITY_RATING to 20,
        USER_RATING to 20,
        DIFFICULTY to 20,
        STATUS to 40,
        STATUS_ICON to 10
    )

    private fun badgesFit(limit: Int): (List<BandBadgePart>) -> Boolean =
        { parts -> parts.sumOf { badgeWidths.getValue(it) } <= limit }

    private val allBadges = listOf(FRIENDS, COMMUNITY_RATING, USER_RATING, DIFFICULTY, STATUS)

    @Test
    fun `badges that fit are all kept in display order`() {
        assertEquals(allBadges, fitBandBadges(allBadges, badgesFit(130)))
    }

    @Test
    fun `badges drop difficulty then community rating then user rating`() {
        assertEquals(
            listOf(FRIENDS, COMMUNITY_RATING, USER_RATING, STATUS),
            fitBandBadges(allBadges, badgesFit(110))
        )
        assertEquals(listOf(FRIENDS, USER_RATING, STATUS), fitBandBadges(allBadges, badgesFit(90)))
        assertEquals(listOf(FRIENDS, STATUS), fitBandBadges(allBadges, badgesFit(70)))
    }

    @Test
    fun `a status that still does not fit becomes icon only and friends stay`() {
        assertEquals(listOf(FRIENDS, STATUS_ICON), fitBandBadges(allBadges, badgesFit(45)))
        assertEquals(listOf(FRIENDS, STATUS_ICON), fitBandBadges(allBadges, badgesFit(0)))
    }

    @Test
    fun `dropping stops at the first arrangement that fits`() {
        val checked = mutableListOf<List<Int>>()
        val kept = dropUntilFits(listOf(1, 2, 3, 4), listOf(4, 3, 2)) { parts ->
            checked += parts
            parts.sum() <= 6
        }
        assertEquals(listOf(1, 2, 3), kept)
        assertEquals(listOf(listOf(1, 2, 3, 4), listOf(1, 2, 3)), checked)
    }

    @Test
    fun `the last played line falls back from device to verbose to short`() {
        val withDevice = "Last played 2 weeks ago on Odin3"
        val verbose = "Last played 2 weeks ago"
        val short = "Played 14d ago"
        val candidates = listOf(withDevice, verbose)
        assertEquals(withDevice, firstFitting(candidates, short) { it.length <= 40 })
        assertEquals(verbose, firstFitting(candidates, short) { it.length <= 25 })
        assertEquals(short, firstFitting(candidates, short) { it.length <= 14 })
    }
}
