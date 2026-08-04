package com.nendo.argosy.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LocalRomClaimsTest {

    private fun file(name: String) = ClaimCandidate(name, isDirectory = false)
    private fun dir(name: String) = ClaimCandidate(name, isDirectory = true)

    @Test
    fun `an exact server filename claims its file`() {
        val claims = claimLocalEntries(
            listOf(ClaimTarget(1, "Sonic (USA).md", "Sonic")),
            listOf(file("Sonic (USA).md"), file("Other.md"))
        )
        assertEquals("Sonic (USA).md", claims[1]?.name)
    }

    @Test
    fun `a folder named for the server filename claims it`() {
        val claims = claimLocalEntries(
            listOf(ClaimTarget(1, "Death Road to Canada", "Death Road to Canada")),
            listOf(dir("Death Road to Canada"))
        )
        assertEquals("Death Road to Canada", claims[1]?.name)
    }

    @Test
    fun `region and revision tags no longer defeat a folder match`() {
        val claims = claimLocalEntries(
            listOf(ClaimTarget(1, "Custom Robo (USA) (Rev 1)", "Custom Robo")),
            listOf(dir("Custom Robo (USA) (Rev 1)"))
        )
        assertEquals("Custom Robo (USA) (Rev 1)", claims[1]?.name)
    }

    @Test
    fun `a stem match still claims a file whose extension changed`() {
        val claims = claimLocalEntries(
            listOf(ClaimTarget(1, "Game (USA).zip", "Game")),
            listOf(file("Game (USA).chd"))
        )
        assertEquals("Game (USA).chd", claims[1]?.name)
    }

    @Test
    fun `an exact folder owner beats another game's stem match on the same folder`() {
        val claims = claimLocalEntries(
            listOf(
                ClaimTarget(1, "Game (USA).wux", "Game"),
                ClaimTarget(2, "Game (USA)", "Game")
            ),
            listOf(dir("Game (USA)"))
        )
        assertEquals("the exact owner takes it", "Game (USA)", claims[2]?.name)
        assertNull("the stem claimant gets nothing", claims[1])
    }

    @Test
    fun `two games with the same server filename claim nothing`() {
        val claims = claimLocalEntries(
            listOf(
                ClaimTarget(1, "Game.zip", "Game One"),
                ClaimTarget(2, "Game.zip", "Game Two")
            ),
            listOf(file("Game.zip"))
        )
        assertNull(claims[1])
        assertNull(claims[2])
    }

    @Test
    fun `a game matching two candidates in one tier claims neither`() {
        val claims = claimLocalEntries(
            listOf(ClaimTarget(1, "Game.zip", "Game")),
            listOf(file("Game.chd"), file("Game.iso"))
        )
        assertNull(claims[1])
    }

    @Test
    fun `title matching only applies when the server gave no filename`() {
        val withName = claimLocalEntries(
            listOf(ClaimTarget(1, "Actual Name.md", "Sonic")),
            listOf(file("Sonic.md"))
        )
        assertNull(withName[1])

        val withoutName = claimLocalEntries(
            listOf(ClaimTarget(1, null, "Sonic")),
            listOf(file("Sonic.md"))
        )
        assertEquals("Sonic.md", withoutName[1]?.name)
    }

    @Test
    fun `a title-named folder is still claimed for installs argosy created`() {
        val claims = claimLocalEntries(
            listOf(ClaimTarget(1, "Some Server Name", "Pretty Title")),
            listOf(dir("Pretty Title"))
        )
        assertEquals("Pretty Title", claims[1]?.name)
    }

    @Test
    fun `the outcome does not depend on the order games arrive in`() {
        val targets = listOf(
            ClaimTarget(1, "Game (USA).wux", "Game"),
            ClaimTarget(2, "Game (USA)", "Game"),
            ClaimTarget(3, "Other.md", "Other")
        )
        val candidates = listOf(dir("Game (USA)"), file("Other.md"))
        val forward = claimLocalEntries(targets, candidates)
        val reversed = claimLocalEntries(targets.reversed(), candidates.reversed())
        assertEquals(forward.mapValues { it.value.name }, reversed.mapValues { it.value.name })
    }

    @Test
    fun `the same name listed from two roots is one candidate, not a conflict`() {
        val collapsed = listOf(file("Game.zip"), file("Game.zip")).distinct()
        val claims = claimLocalEntries(listOf(ClaimTarget(1, "Game.zip", "Game")), collapsed)
        assertEquals(
            "a rom present in both a custom folder and the shared one must still be claimed",
            "Game.zip",
            claims[1]?.name
        )
    }

    @Test
    fun `one entry is never handed to two games`() {
        val claims = claimLocalEntries(
            listOf(ClaimTarget(1, "Game.zip", "Game"), ClaimTarget(2, null, "Game")),
            listOf(file("Game.zip"))
        )
        assertEquals(1, claims.values.distinct().size)
    }
}
