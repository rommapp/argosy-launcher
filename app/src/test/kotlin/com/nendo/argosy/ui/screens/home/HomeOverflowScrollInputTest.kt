package com.nendo.argosy.ui.screens.home

import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.domain.model.GridDirection2D
import com.nendo.argosy.domain.model.HomeLayoutKind
import com.nendo.argosy.ui.common.GridDirection
import com.nendo.argosy.ui.components.AutoGridMove
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.screens.home.delegates.HomeInputActions
import com.nendo.argosy.ui.screens.home.delegates.HomeInputHandler
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeOverflowScrollInputTest {

    @Test
    fun `overflow scroll moves vertically without changing the selected row`() {
        val fixture = Fixture()
        var scrollOffset = 0
        val handler = fixture.handler { direction ->
            scrollOffset += direction
            true
        }

        assertEquals(InputResult.HANDLED, handler.onDown())
        assertEquals(1, scrollOffset)
        assertEquals(1, fixture.row)

        assertEquals(InputResult.HANDLED, handler.onUp())
        assertEquals(0, scrollOffset)
        assertEquals(1, fixture.row)
    }

    @Test
    fun `scroll boundaries restore row navigation and section feedback`() {
        val fixture = Fixture()
        var scrollOffset = 0
        val handler = fixture.handler { direction ->
            val next = (scrollOffset + direction).coerceIn(0, 1)
            val moved = next != scrollOffset
            scrollOffset = next
            moved
        }

        assertEquals(InputResult.handled(SoundType.SECTION_CHANGE), handler.onUp())
        assertEquals(0, fixture.row)
        assertEquals(0, scrollOffset)

        assertEquals(InputResult.HANDLED, handler.onDown())
        assertEquals(0, fixture.row)
        assertEquals(1, scrollOffset)

        assertEquals(InputResult.handled(SoundType.SECTION_CHANGE), handler.onDown())
        assertEquals(1, fixture.row)
        assertEquals(1, scrollOffset)
    }

    @Test
    fun `an absent overflow callback preserves row navigation`() {
        val fixture = Fixture()
        val handler = fixture.handler()

        assertEquals(InputResult.handled(SoundType.SECTION_CHANGE), handler.onDown())
        assertEquals(2, fixture.row)
        assertEquals(InputResult.handled(SoundType.SECTION_CHANGE), handler.onUp())
        assertEquals(1, fixture.row)
    }

    @Test
    fun `focused dock retains its boundary and release behavior`() {
        val fixture = Fixture(HomeUiState(appBarFocused = true))
        val handler = fixture.handler { error("Dock input reached carousel scrolling") }

        assertEquals(InputResult.handled(SoundType.BOUNDARY), handler.onDown())
        assertTrue(fixture.state.value.appBarFocused)
        assertEquals(InputResult.handled(SoundType.SECTION_CHANGE), handler.onUp())
        assertFalse(fixture.state.value.appBarFocused)
        assertEquals(1, fixture.row)
    }

    @Test
    fun `game menu moves its selection without scrolling the carousel`() {
        val fixture = Fixture(HomeUiState(showGameMenu = true, gameMenuFocusIndex = 1))
        every { fixture.actions.moveGameMenuFocus(any()) } answers {
            val direction = firstArg<Int>()
            fixture.state.update { it.copy(gameMenuFocusIndex = it.gameMenuFocusIndex + direction) }
        }
        val handler = fixture.handler { error("Modal input reached carousel scrolling") }

        assertEquals(InputResult.HANDLED, handler.onDown())
        assertEquals(2, fixture.state.value.gameMenuFocusIndex)
        assertEquals(InputResult.HANDLED, handler.onUp())
        assertEquals(1, fixture.state.value.gameMenuFocusIndex)
        assertEquals(1, fixture.row)
    }

    @Test
    fun `auto grid retains section transitions without invoking carousel scrolling`() {
        val fixture = Fixture(HomeUiState(layoutKind = HomeLayoutKind.AUTO_GRID))
        every { fixture.actions.moveGridFocus(GridDirection.UP) } returns AutoGridMove.PreviousSection
        every { fixture.actions.moveGridFocus(GridDirection.DOWN) } returns AutoGridMove.NextSection
        val handler = fixture.handler { error("Auto grid input reached carousel scrolling") }

        assertEquals(InputResult.handled(SoundType.SECTION_CHANGE), handler.onUp())
        assertEquals(0, fixture.row)
        assertEquals(InputResult.handled(SoundType.SECTION_CHANGE), handler.onDown())
        assertEquals(1, fixture.row)
    }

    @Test
    fun `custom grid moves its selection without scrolling the carousel`() {
        val fixture = Fixture(HomeUiState(layoutKind = HomeLayoutKind.CUSTOM_GRID))
        var cellRow = 1
        every { fixture.actions.moveCustomGridFocus(GridDirection2D.UP) } answers {
            cellRow -= 1
            true
        }
        every { fixture.actions.moveCustomGridFocus(GridDirection2D.DOWN) } answers {
            cellRow += 1
            true
        }
        val handler = fixture.handler { error("Custom grid input reached carousel scrolling") }

        assertEquals(InputResult.HANDLED, handler.onUp())
        assertEquals(0, cellRow)
        assertEquals(InputResult.HANDLED, handler.onDown())
        assertEquals(1, cellRow)
        assertEquals(1, fixture.row)
    }

    private class Fixture(initialState: HomeUiState = HomeUiState()) {
        val state = MutableStateFlow(initialState)
        val actions = mockk<HomeInputActions>(relaxed = true)
        var row = 1

        init {
            every { actions.uiState } returns state
            every { actions.previousRow() } answers { row -= 1 }
            every { actions.nextRow() } answers { row += 1 }
            every { actions.releaseAppBar() } answers {
                state.update { it.copy(appBarFocused = false) }
            }
        }

        fun handler(onScrollOverflow: ((Int) -> Boolean)? = null) = HomeInputHandler(
            actions = actions,
            isDefaultView = true,
            onGameSelect = {},
            onNavigateToDefault = {},
            onDrawerToggle = {},
            onScrollOverflow = onScrollOverflow
        )
    }
}
