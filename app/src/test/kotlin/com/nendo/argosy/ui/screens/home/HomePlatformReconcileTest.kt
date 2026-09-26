package com.nendo.argosy.ui.screens.home

import com.nendo.argosy.ui.screens.home.delegates.HomeNavigationDelegate
import com.nendo.argosy.ui.screens.home.delegates.PlatformChangeResult
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A platform row is addressed by its index in the list, so any change to that list's order has to
 * move the row to the same platform's new index. Keeping the old index points the row at a
 * different platform, whose games never load, and Home draws an empty row.
 */
class HomePlatformReconcileTest {

    private val delegate = HomeNavigationDelegate(mockk(relaxed = true), mockk(relaxed = true))

    private fun platform(id: Long) = HomePlatformUi(
        id = id,
        slug = "p$id",
        name = "P$id",
        shortName = "P$id",
        displayName = "P$id",
        logoPath = null
    )

    @Test
    fun `a reorder follows the current platform to its new index`() {
        val current = listOf(platform(1), platform(2), platform(3))
        val reordered = listOf(platform(3), platform(1), platform(2))
        val state = HomeUiState(currentRow = HomeRow.Platform(1), platforms = current)

        val result = delegate.reconcilePlatformChange(state, current, reordered)

        assertTrue(result is PlatformChangeResult.StructuralChange)
        assertEquals(HomeRow.Platform(2), (result as PlatformChangeResult.StructuralChange).row)
    }

    @Test
    fun `the same platforms in the same order change only what is displayed`() {
        val current = listOf(platform(1), platform(2))
        val refreshed = listOf(platform(1), platform(2).copy(hasEmulator = false))
        val state = HomeUiState(currentRow = HomeRow.Platform(1), platforms = current)

        val result = delegate.reconcilePlatformChange(state, current, refreshed)

        assertTrue(result is PlatformChangeResult.DisplayOnly)
    }
}
