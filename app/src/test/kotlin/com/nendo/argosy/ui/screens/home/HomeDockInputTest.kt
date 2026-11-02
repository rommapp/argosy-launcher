package com.nendo.argosy.ui.screens.home

import com.nendo.argosy.domain.model.GridDirection2D
import com.nendo.argosy.domain.model.HomeLayoutKind
import com.nendo.argosy.ui.components.AppContextMenuItem
import com.nendo.argosy.ui.components.AppMenuRow
import com.nendo.argosy.ui.components.CustomGridState
import com.nendo.argosy.ui.components.TileEditMode
import com.nendo.argosy.ui.screens.home.delegates.HomeInputActions
import com.nendo.argosy.ui.screens.home.delegates.HomeInputHandler
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Test

class HomeDockInputTest {
    @Test
    fun `dock focus follows physical controller directions in both layouts`() {
        for (isRtl in listOf(false, true)) {
            val state = MutableStateFlow(HomeUiState(homeApps = listOf("a", "b", "c"),
                appBarFocused = true, appBarIndex = 1))
            val actions = mockk<HomeInputActions>(relaxed = true)
            every { actions.uiState } returns state
            every { actions.moveAppBarFocus(any()) } answers {
                state.value = state.value.copy(appBarIndex = state.value.appBarIndex + firstArg<Int>())
            }
            val handler = HomeInputHandler(actions, true, {}, {}, {}, dockIsRtl = isRtl)
            handler.onLeft()
            assertEquals(if (isRtl) 2 else 0, state.value.appBarIndex)
            handler.onRight()
            assertEquals(1, state.value.appBarIndex)
        }
    }

    @Test
    fun `game actions belong to carousel and resume after leaving dock`() {
        val game = mockk<HomeGameUi>(relaxed = true)
        every { game.id } returns 42L
        val state = MutableStateFlow(HomeUiState(appBarFocused = true, recentGames = listOf(game)))
        val actions = mockk<HomeInputActions>(relaxed = true)
        every { actions.uiState } returns state
        every { actions.releaseAppBar() } answers { state.value = state.value.copy(appBarFocused = false) }
        every { actions.toggleGameMenu() } answers { state.value = state.value.copy(showGameMenu = true) }
        every { actions.pressSelect(any()) } answers { firstArg<() -> Unit>().invoke() }
        var detailsId: Long? = null
        var favoriteId: Long? = null
        every { actions.toggleFavorite(any()) } answers { favoriteId = firstArg() }
        val handler = HomeInputHandler(actions, true, { detailsId = it }, {}, {})

        assertTrue(handler.onContextMenu().handled)
        assertTrue(handler.onSecondaryAction().handled)
        assertTrue(handler.onSelect().handled)
        assertNull(detailsId)
        assertNull(favoriteId)
        assertFalse(state.value.showGameMenu)

        handler.onUp()
        handler.onContextMenu()
        handler.onSecondaryAction()
        handler.onSelect()
        assertEquals(42L, detailsId)
        assertEquals(42L, favoriteId)
        assertTrue(state.value.showGameMenu)
    }

    @Test
    fun `dock focus follows app identity through a preview and its cancellation`() {
        val original = HomeUiState(homeApps = listOf("a", "b", "c"), appBarFocused = true)
        val preview = original.withDockApps(listOf("c", "b", "a"))
        assertEquals(2, preview.appBarIndex)
        assertEquals(0, preview.withDockApps(original.homeApps).appBarIndex)
    }

    @Test
    fun `hydrating pins does not mistake the initial index for focused tools`() {
        val initial = HomeUiState().withDockApps(listOf("a", "b"))
        assertEquals(0, initial.appBarIndex)
        val tools = HomeUiState(homeApps = listOf("a", "b"), appBarFocused = true, appBarIndex = 2)
        assertEquals(3, tools.withDockApps(listOf("a", "b", "c")).appBarIndex)
    }
    private fun handler(state: HomeUiState): Pair<HomeInputHandler, HomeInputActions> {
        val actions = mockk<HomeInputActions>(relaxed = true)
        every { actions.uiState } returns MutableStateFlow(state)
        every { actions.focusAppBar() } returns true
        return HomeInputHandler(actions, true, {}, {}, {}) to actions
    }

    @Test
    fun `down enters dock only after browse movement reaches the canvas boundary`() {
        val (handler, actions) = handler(HomeUiState(layoutKind = HomeLayoutKind.CUSTOM_GRID))
        every { actions.moveCustomGridFocus(GridDirection2D.DOWN) } returns true
        handler.onDown()
        verify(exactly = 0) { actions.focusAppBar() }
        every { actions.moveCustomGridFocus(GridDirection2D.DOWN) } returns false
        handler.onDown()
        verify(exactly = 1) { actions.focusAppBar() }
    }

    @Test
    fun `failed tile edit movement cannot enter dock`() {
        for (mode in listOf(TileEditMode.MOVE, TileEditMode.RESIZE)) {
            val (handler, actions) = handler(HomeUiState(layoutKind = HomeLayoutKind.CUSTOM_GRID,
                customGrid = CustomGridState(editMode = mode)))
            handler.onDown()
            verify(exactly = 0) { actions.focusAppBar() }
        }
    }

    @Test
    fun `hold confirm on focused dock opens the dock app menu`() {
        val (handler, actions) = handler(HomeUiState(appBarFocused = true, homeApps = listOf("a", "b")))
        every { actions.openDockAppMenu() } returns true

        assertTrue(handler.onLongConfirm().handled)
        verify(exactly = 1) { actions.openDockAppMenu() }
        verify(exactly = 0) { actions.activateAppBarSlot() }
    }

    @Test
    fun `hold confirm on a dock slot without an app is consumed without opening a menu`() {
        val (handler, actions) = handler(
            HomeUiState(appBarFocused = true, homeApps = listOf("a"), appBarIndex = -1)
        )
        every { actions.openDockAppMenu() } returns false

        assertTrue(handler.onLongConfirm().handled)
        verify(exactly = 0) { actions.activateAppBarSlot() }
    }

    @Test
    fun `an open dock app menu takes every direction and button`() {
        val menu = AppBarLaunchMenu(
            packageName = "a",
            label = "A",
            rows = listOf(
                AppMenuRow.Action(AppContextMenuItem.REORDER),
                AppMenuRow.OpenOnScreen(0, 1, "x"),
                AppMenuRow.OpenOnScreen(4, 2, "y")
            )
        )
        val (handler, actions) = handler(
            HomeUiState(appBarFocused = true, homeApps = listOf("a", "b"), appBarMenu = menu)
        )

        assertTrue(handler.onDown().handled)
        verify(exactly = 1) { actions.moveAppBarAppMenu(1) }
        assertTrue(handler.onUp().handled)
        verify(exactly = 1) { actions.moveAppBarAppMenu(-1) }
        assertTrue(handler.onLeft().handled)
        assertTrue(handler.onRight().handled)
        verify(exactly = 0) { actions.moveAppBarFocus(any()) }
        assertTrue(handler.onConfirm().handled)
        verify(exactly = 1) { actions.confirmAppBarAppMenu() }
        verify(exactly = 0) { actions.activateAppBarSlot() }
        assertTrue(handler.onLongConfirm().handled)
        verify(exactly = 0) { actions.openDockAppMenu() }
        assertTrue(handler.onSecondaryAction().handled)
        assertTrue(handler.onContextMenu().handled)
        assertTrue(handler.onMenu().handled)
        assertTrue(handler.onPrevSection().handled)
        assertTrue(handler.onNextTrigger().handled)
        assertTrue(handler.onBack().handled)
        verify(exactly = 1) { actions.dismissAppBarAppMenu() }
        verify(exactly = 0) { actions.releaseAppBar() }
    }
}
