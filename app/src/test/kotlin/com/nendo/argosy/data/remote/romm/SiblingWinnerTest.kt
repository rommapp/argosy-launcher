package com.nendo.argosy.data.remote.romm

import com.nendo.argosy.data.preferences.SyncFilterPreferences
import org.junit.Assert.assertEquals
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

    private fun rom(id: Long, vararg regions: String) = RomMRom(
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
        revision = null
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
}
