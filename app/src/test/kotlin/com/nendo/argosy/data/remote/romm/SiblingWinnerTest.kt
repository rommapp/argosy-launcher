package com.nendo.argosy.data.remote.romm

import com.nendo.argosy.data.preferences.RegionFilterMode
import com.nendo.argosy.data.preferences.SyncFilterPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Sibling consolidation picks which regional variant a user's library shows. The pick is
 * now made as pages stream in rather than over a retained list, so it has to land on the
 * same ROM the deferred rule did, whatever order the members arrive in.
 */
class SiblingWinnerTest {

    private val filters = SyncFilterPreferences(
        enabledRegions = listOf("USA", "Europe", "Japan")
    )

    private fun rom(id: Long, vararg regions: String, tags: List<String>? = null) = RomMRom(
        id = id,
        name = "rom$id",
        fileName = "rom$id.nes",
        filePath = "/roms/nes/rom$id.nes",
        platformId = 1L,
        platformSlug = "nes",
        slug = "rom$id",
        igdbId = null,
        mobyId = null,
        summary = null,
        coverSmall = null,
        coverLarge = null,
        regions = regions.toList(),
        languages = null,
        revision = null,
        tags = tags
    )

    private fun feed(group: SiblingGroup, roms: List<RomMRom>): RomMRom? {
        roms.forEach { candidate ->
            group.members.add(SiblingMember(candidate.id, candidate.regions, candidate.files))
            group.winner = chooseWinner(group, candidate, filters)
        }
        return group.winner
    }

    private fun deferredWinner(group: SiblingGroup): RomMRom? = group.winner

    @Test
    fun `best ranked region wins when no main sibling is declared`() {
        val group = SiblingGroup()
        val winner = feed(group, listOf(rom(1, "Japan"), rom(2, "USA"), rom(3, "Europe")))

        assertEquals(2L, winner?.id)
    }

    @Test
    fun `arrival order does not change the winner`() {
        val ordered = feed(SiblingGroup(), listOf(rom(1, "Japan"), rom(2, "USA"), rom(3, "Europe")))
        val reversed = feed(SiblingGroup(), listOf(rom(3, "Europe"), rom(2, "USA"), rom(1, "Japan")))

        assertEquals(ordered?.id, reversed?.id)
    }

    @Test
    fun `a declared main sibling beats a better ranked region`() {
        val group = SiblingGroup()
        group.mainSiblingId = 1L
        val winner = feed(group, listOf(rom(1, "Japan"), rom(2, "USA")))

        assertEquals(1L, winner?.id)
    }

    @Test
    fun `a main sibling declared after a better region still wins when it arrives later`() {
        val group = SiblingGroup()
        val first = rom(2, "USA")
        group.members.add(SiblingMember(first.id, first.regions, first.files))
        group.winner = chooseWinner(group, first, filters)

        group.mainSiblingId = 1L
        val main = rom(1, "Japan")
        group.members.add(SiblingMember(main.id, main.regions, main.files))
        group.winner = chooseWinner(group, main, filters)

        assertEquals(1L, deferredWinner(group)?.id)
    }

    @Test
    fun `ties keep the member seen first`() {
        val group = SiblingGroup()
        val winner = feed(group, listOf(rom(7, "USA"), rom(8, "USA")))

        assertEquals(7L, winner?.id)
    }

    @Test
    fun `an unranked region loses to a ranked one regardless of order`() {
        val group = SiblingGroup()
        val winner = feed(group, listOf(rom(1, "Brazil"), rom(2, "Europe")))

        assertEquals(2L, winner?.id)
    }

    @Test
    fun `a group of only unranked regions still resolves to its first member`() {
        val group = SiblingGroup()
        val winner = feed(group, listOf(rom(4, "Brazil"), rom(5, "Korea")))

        assertEquals(4L, winner?.id)
    }

    private val priorityFilters = SyncFilterPreferences(
        enabledRegions = listOf("Germany", "Europe", "World", "USA"),
        regionMode = RegionFilterMode.INCLUDE
    )

    private fun feedWith(
        group: SiblingGroup,
        roms: List<RomMRom>,
        with: SyncFilterPreferences
    ): RomMRom? {
        roms.forEach { candidate ->
            group.members.add(SiblingMember(candidate.id, candidate.regions, candidate.files))
            group.winner = chooseWinner(group, candidate, with)
        }
        return group.winner
    }

    @Test
    fun `an ordered region priority beats a declared main sibling`() {
        val group = SiblingGroup()
        group.mainSiblingId = 1L
        val winner = feedWith(group, listOf(rom(1, "USA"), rom(2, "Europe")), priorityFilters)

        assertEquals(2L, winner?.id)
    }

    @Test
    fun `region priority wins whichever order the members arrive in`() {
        val forward = SiblingGroup().also { it.mainSiblingId = 1L }
        val reverse = SiblingGroup().also { it.mainSiblingId = 1L }
        val first = feedWith(forward, listOf(rom(1, "USA"), rom(2, "Germany")), priorityFilters)
        val second = feedWith(reverse, listOf(rom(2, "Germany"), rom(1, "USA")), priorityFilters)

        assertEquals(2L, first?.id)
        assertEquals(2L, second?.id)
    }

    @Test
    fun `the main sibling still breaks a tie between equally ranked regions`() {
        val group = SiblingGroup()
        group.mainSiblingId = 2L
        val winner = feedWith(group, listOf(rom(1, "Europe"), rom(2, "Europe")), priorityFilters)

        assertEquals(2L, winner?.id)
    }

    @Test
    fun `an exclude list carries no priority so the main sibling still wins`() {
        val group = SiblingGroup()
        group.mainSiblingId = 1L
        val winner = feedWith(group, listOf(rom(1, "USA"), rom(2, "Europe")), filters)

        assertEquals(1L, winner?.id)
    }

    @Test
    fun `an original release beats a re-release of the same region`() {
        val forward = feedWith(
            SiblingGroup(),
            listOf(
                rom(535, "Europe", tags = listOf("Wii Virtual Console")),
                rom(536, "Europe")
            ),
            priorityFilters
        )
        val reverse = feedWith(
            SiblingGroup(),
            listOf(
                rom(536, "Europe"),
                rom(535, "Europe", tags = listOf("Wii Virtual Console"))
            ),
            priorityFilters
        )

        assertEquals(536L, forward?.id)
        assertEquals(536L, reverse?.id)
    }

    @Test
    fun `a better region still beats an original release`() {
        val group = SiblingGroup()
        val winner = feedWith(
            group,
            listOf(rom(1, "USA"), rom(2, "Germany", tags = listOf("Switch Online"))),
            priorityFilters
        )

        assertEquals(2L, winner?.id)
    }

    @Test
    fun `re-release markers are read from the tag list`() {
        assertTrue(rom(1, "Europe", tags = listOf("Wii U Virtual Console")).isRerelease)
        assertTrue(rom(2, "Europe", tags = listOf("Classic Mini")).isRerelease)
        assertFalse(rom(3, "Europe", tags = listOf("SGB Enhanced")).isRerelease)
        assertFalse(rom(4, "Europe").isRerelease)
    }

    /**
     * Two groups form when sibling lists disagree about the set, and merging must re-run the
     * pick. Keeping the group that formed first hands the release to page order: the German
     * dump wins when its id sorts lower and loses when it does not.
     */
    @Test
    fun `merging two groups re-runs the pick instead of keeping the first winner`() {
        val usaFirst = SiblingGroup()
        usaFirst.winner = rom(3644, "USA", "Europe")
        val german = rom(3645, "Germany")

        val merged = chooseWinner(usaFirst, german, priorityFilters)

        assertEquals(3645L, merged?.id)
    }

    @Test
    fun `merging is symmetric whichever group formed first`() {
        val germanFirst = SiblingGroup()
        germanFirst.winner = rom(3645, "Germany")

        val merged = chooseWinner(germanFirst, rom(3644, "USA", "Europe"), priorityFilters)

        assertEquals(3645L, merged?.id)
    }

    private fun groupOf(winner: RomMRom, vararg expected: Long) = SiblingGroup().apply {
        this.winner = winner
        members.add(SiblingMember(winner.id, winner.regions, winner.files))
        expectedIds.addAll(expected.toList())
    }

    /**
     * The German dump of Pokemon Red sorts after the USA one, so its group formed second and the
     * merge discarded it. The English dump of Blue sorts after the German one, which is the only
     * reason Blue survived the same code path.
     */
    @Test
    fun `merging keeps the better ranked region whichever group formed first`() {
        val usaFirst = mergeSiblingGroups(
            listOf(
                groupOf(rom(3644, "USA", "Europe"), 3644L, 3645L),
                groupOf(rom(3645, "Germany"), 3644L, 3645L)
            ),
            priorityFilters
        )
        val germanFirst = mergeSiblingGroups(
            listOf(
                groupOf(rom(3636, "Germany"), 3636L, 3638L),
                groupOf(rom(3638, "USA", "Europe"), 3636L, 3638L)
            ),
            priorityFilters
        )

        assertEquals(3645L, usaFirst.winner?.id)
        assertEquals(3636L, germanFirst.winner?.id)
    }

    @Test
    fun `merging carries every member and expected id across`() {
        val merged = mergeSiblingGroups(
            listOf(
                groupOf(rom(3644, "USA", "Europe"), 3644L, 3645L),
                groupOf(rom(3645, "Germany"), 3645L, 3651L)
            ),
            priorityFilters
        )

        assertEquals(setOf(3644L, 3645L, 3651L), merged.expectedIds)
        assertEquals(setOf(3644L, 3645L), merged.members.map { it.id }.toSet())
    }

    @Test
    fun `merging a single group leaves its winner alone`() {
        val only = groupOf(rom(3645, "Germany"), 3645L)

        assertEquals(3645L, mergeSiblingGroups(listOf(only), priorityFilters).winner?.id)
    }

    @Test
    fun `merging nothing yields an empty group`() {
        assertEquals(null, mergeSiblingGroups(emptyList(), priorityFilters).winner)
    }

    private fun namedRom(
        id: Long,
        name: String,
        vararg siblings: RomMSibling,
        tags: List<String>? = null
    ) = RomMRom(
        id = id,
        name = name,
        fileName = "rom$id.gb",
        filePath = "/roms/gb/rom$id.gb",
        platformId = 5L,
        platformSlug = "gb",
        slug = "rom$id",
        igdbId = null,
        mobyId = null,
        summary = null,
        coverSmall = null,
        coverLarge = null,
        regions = listOf("Germany"),
        languages = null,
        revision = null,
        tags = tags,
        siblingRoms = siblings.toList()
    )

    private fun sibling(id: Long, name: String?, fileNameNoExt: String = "rom$id") = RomMSibling(
        id = id,
        name = name,
        fileNameNoTags = "rom$id",
        fileNameNoExt = fileNameNoExt
    )

    /**
     * The server's sibling list is the union over every metadata source, and Moby files
     * Pokemon Red and Blue under one entry, so the USA dumps of both list each other. That
     * single link chains the two games into one group and the loser's every regional dump is
     * absorbed under the winner. A sibling naming a different game must not join the group.
     */
    @Test
    fun `a sibling naming a different game stays out of the group`() {
        val blue = namedRom(
            3638, "Pokémon Blue Version",
            sibling(3636, "Pokémon Blue Version"),
            sibling(3644, "Pokémon Red Version")
        )

        assertEquals(listOf(3636L), blue.sameGameSiblings.map { it.id })
        assertTrue(blue.hasNonDiscSiblings)
    }

    @Test
    fun `a rom whose only sibling is a different game consolidates alone`() {
        val homebrew = namedRom(
            2989, "Into the Blue",
            sibling(3636, "Pokémon Blue Version")
        )

        assertFalse(homebrew.hasNonDiscSiblings)
    }

    @Test
    fun `a sibling without a name is kept in the group`() {
        val rom = namedRom(
            3645, "Pokémon Red Version",
            sibling(3644, null)
        )

        assertEquals(listOf(3644L), rom.sameGameSiblings.map { it.id })
    }

    /**
     * The disc paths delete redundant individual-disc games, so a cross-game sibling that
     * happens to carry a disc tag must not pull another game into a multi-disc group.
     */
    @Test
    fun `a disc tagged sibling of a different game does not make a multi-disc group`() {
        val disc = namedRom(
            10, "Some RPG",
            sibling(11, "Another RPG", fileNameNoExt = "Another RPG (Disc 2)"),
            tags = listOf("Disc 1")
        )
        val sameGame = namedRom(
            10, "Some RPG",
            sibling(11, "Some RPG", fileNameNoExt = "Some RPG (Disc 2)"),
            tags = listOf("Disc 1")
        )

        assertFalse(disc.hasDiscSiblings)
        assertTrue(sameGame.hasDiscSiblings)
    }
}
