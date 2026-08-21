package com.nendo.argosy.ui.screens.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * The cursor must always index the row it is drawn on. A start-up that chose a row while carrying
 * an index restored from the previous session is what let a press launch a title that was never
 * under the cursor.
 */
class HomeFocusRowChangeTest {

    @Test
    fun `moving to another row drops an index measured against the old one`() {
        val restored = HomeUiState(currentRow = HomeRow.Continue, focusedGameIndex = 7)

        val moved = restored.movedTo(HomeRow.Platform(2))

        assertEquals(HomeRow.Platform(2), moved.currentRow)
        assertEquals(0, moved.focusedGameIndex)
    }

    @Test
    fun `staying on the same row keeps the restored position`() {
        val restored = HomeUiState(currentRow = HomeRow.Platform(2), focusedGameIndex = 7)

        val moved = restored.movedTo(HomeRow.Platform(2))

        assertSame(restored, moved)
        assertEquals(7, moved.focusedGameIndex)
    }

    @Test
    fun `a different platform index is a different row`() {
        val restored = HomeUiState(currentRow = HomeRow.Platform(2), focusedGameIndex = 7)

        val moved = restored.movedTo(HomeRow.Platform(5))

        assertEquals(HomeRow.Platform(5), moved.currentRow)
        assertEquals(0, moved.focusedGameIndex)
    }

    @Test
    fun `an index past the end of a row selects nothing rather than the wrong thing`() {
        val state = HomeUiState(currentRow = HomeRow.Continue, focusedGameIndex = 7)

        assertEquals(null, state.focusedItem)
        assertEquals(null, state.focusedGame)
    }
}
