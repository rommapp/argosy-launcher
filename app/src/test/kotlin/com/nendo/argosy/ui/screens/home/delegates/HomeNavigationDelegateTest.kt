package com.nendo.argosy.ui.screens.home.delegates

import com.nendo.argosy.ui.screens.home.HomeRow
import com.nendo.argosy.ui.screens.home.HomeUiState
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeNavigationDelegateTest {
    private val navigation = HomeNavigationDelegate(mockk(), mockk())
    private val rows = HomeUiState(platforms = listOf(mockk(), mockk()))

    @Test
    fun `last carousel row yields to visible dock and remembers game focus`() {
        val lastRow = rows.copy(currentRow = HomeRow.Platform(1), focusedGameIndex = 6)

        assertNull(navigation.nextRow(lastRow, wrap = false))
        assertEquals(
            HomeRow.Platform(1) to 6,
            navigation.previousRow(rows.copy(currentRow = HomeRow.Platform(0)))
        )
    }

    @Test
    fun `last carousel row still wraps when dock is absent`() {
        assertEquals(
            HomeRow.Platform(0) to 0,
            navigation.nextRow(rows.copy(currentRow = HomeRow.Platform(1)))
        )
    }

    @Test
    fun `earlier row advances before entering dock`() {
        assertEquals(
            HomeRow.Platform(1) to 0,
            navigation.nextRow(rows.copy(currentRow = HomeRow.Platform(0)), wrap = false)
        )
    }

    @Test
    fun `single carousel row yields to visible dock`() {
        assertNull(
            navigation.nextRow(
                HomeUiState(platforms = listOf(mockk()), currentRow = HomeRow.Platform(0)),
                wrap = false
            )
        )
    }
}
