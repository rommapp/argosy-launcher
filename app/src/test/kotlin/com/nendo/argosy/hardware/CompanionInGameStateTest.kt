package com.nendo.argosy.hardware

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CompanionInGameStateTest {

    @Test
    fun `metadata refresh preserves live quick save availability`() {
        val metadata = CompanionInGameState(
            gameId = 1L,
            title = "EarthBound",
            isLoaded = true
        )
        val merged = metadata.withLiveQuickActionState(
            quickActionsAvailable = true,
            hasQuickSave = true
        )

        assertEquals(1L, merged.gameId)
        assertEquals("EarthBound", merged.title)
        assertTrue(merged.isLoaded)
        assertTrue(merged.quickActionsAvailable)
        assertTrue(merged.hasQuickSave)
    }
}
