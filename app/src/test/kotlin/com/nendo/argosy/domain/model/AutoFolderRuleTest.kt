package com.nendo.argosy.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val NOW = 100L * 86_400_000L
private const val DAY = 86_400_000L

class AutoFolderRuleTest {

    private fun game(
        releaseYear: Int? = 1995,
        developer: String? = "Atlus",
        status: String? = null,
        addedAt: Long = NOW,
        total: Int = 0,
        earned: Int = 0
    ) = LibraryFilterFacts(
        id = 1L,
        genre = "RPG",
        players = null,
        developer = developer,
        releaseYear = releaseYear,
        status = status,
        addedAt = addedAt,
        achievementCount = total,
        earnedAchievementCount = earned
    )

    private fun LibraryLinkFilters.admits(facts: LibraryFilterFacts) = admits(facts, null, NOW)

    @Test
    fun `an empty rule admits every game`() {
        assertTrue(LibraryLinkFilters().admits(game(releaseYear = null, developer = null)))
    }

    @Test
    fun `release year bounds are inclusive at both ends`() {
        val nineties = LibraryLinkFilters(releaseYearFrom = 1990, releaseYearTo = 1999)

        assertTrue(nineties.admits(game(releaseYear = 1990)))
        assertTrue(nineties.admits(game(releaseYear = 1999)))
        assertFalse(nineties.admits(game(releaseYear = 1989)))
        assertFalse(nineties.admits(game(releaseYear = 2000)))
        assertFalse(nineties.admits(game(releaseYear = null)))
    }

    @Test
    fun `an open lower bound admits everything up to the upper one`() {
        val before = LibraryLinkFilters(releaseYearTo = 1989)

        assertTrue(before.admits(game(releaseYear = 1970)))
        assertFalse(before.admits(game(releaseYear = 1990)))
    }

    @Test
    fun `developers match on the stored value`() {
        val rule = LibraryLinkFilters(developers = setOf("Atlus"))

        assertTrue(rule.admits(game(developer = "Atlus")))
        assertFalse(rule.admits(game(developer = "Square")))
        assertFalse(rule.admits(game(developer = null)))
    }

    @Test
    fun `play status matches the completion token`() {
        val rule = LibraryLinkFilters(playStatuses = setOf(CompletionStatus.FINISHED.apiValue))

        assertTrue(rule.admits(game(status = "finished")))
        assertFalse(rule.admits(game(status = "incomplete")))
        assertFalse(rule.admits(game(status = null)))
    }

    @Test
    fun `recently added keeps games inside the window and drops older ones`() {
        val rule = LibraryLinkFilters(addedWithinDays = 7)

        assertTrue(rule.admits(game(addedAt = NOW - 7 * DAY)))
        assertTrue(rule.admits(game(addedAt = NOW)))
        assertFalse(rule.admits(game(addedAt = NOW - 7 * DAY - 1)))
    }

    @Test
    fun `achievement progress separates not started, in progress and mastered`() {
        val notStarted = LibraryLinkFilters(raProgress = RaProgressFilter.NOT_STARTED)
        val inProgress = LibraryLinkFilters(raProgress = RaProgressFilter.IN_PROGRESS)
        val mastered = LibraryLinkFilters(raProgress = RaProgressFilter.MASTERED)

        assertTrue(notStarted.admits(game(total = 10, earned = 0)))
        assertFalse(notStarted.admits(game(total = 0, earned = 0)))
        assertTrue(inProgress.admits(game(total = 10, earned = 3)))
        assertFalse(inProgress.admits(game(total = 10, earned = 10)))
        assertTrue(mastered.admits(game(total = 10, earned = 10)))
        assertFalse(mastered.admits(game(total = 10, earned = 9)))
    }

    @Test
    fun `new categories leave the rule filtered`() {
        assertFalse(LibraryLinkFilters(developers = setOf("Atlus")).isUnfiltered)
        assertFalse(LibraryLinkFilters(raProgress = RaProgressFilter.MASTERED).isUnfiltered)
        assertTrue(LibraryLinkFilters().isUnfiltered)
    }
}
