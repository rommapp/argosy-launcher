package com.nendo.argosy.ui.screens.home

import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.domain.model.HomeColumnLabel
import com.nendo.argosy.domain.model.HomeColumnTheme
import com.nendo.argosy.domain.model.HomeLayoutKind
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.screens.home.delegates.HomeInputActions
import com.nendo.argosy.ui.screens.home.delegates.HomeInputHandler
import com.nendo.argosy.ui.screens.home.delegates.VideoPreviewPhase
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeColumnsInputTest {

    private val game = HomeGameUi(
        id = 42L,
        title = "Game",
        platformId = 1L,
        platformSlug = "snes",
        platformDisplayName = "Super Nintendo",
        platformShortName = "SNES",
        coverPath = null,
        backgroundPath = null,
        developer = null,
        releaseYear = null,
        genre = null,
        isFavorite = false,
        isDownloaded = true
    )

    private val columnsState = HomeUiState(
        layoutKind = HomeLayoutKind.COLUMNS,
        columns = ColumnsState(
            columns = listOf(
                HomeColumnUi(
                    id = "theme:CONTINUE",
                    label = HomeColumnLabel.Theme(HomeColumnTheme.CONTINUE),
                    items = listOf(HomeRowItem.Game(game))
                )
            ),
            isLoaded = true
        )
    )

    private fun handler(
        state: HomeUiState = columnsState,
        rtl: Boolean = false,
        setup: HomeInputActions.() -> Unit = {}
    ): Pair<HomeInputHandler, HomeInputActions> {
        val actions = mockk<HomeInputActions>(relaxed = true)
        every { actions.uiState } returns MutableStateFlow(state)
        actions.setup()
        return HomeInputHandler(actions, true, {}, {}, {}, dockIsRtl = rtl) to actions
    }

    @Test
    fun `left and right move the loop one column`() {
        val (handler, actions) = handler { every { moveColumn(any()) } returns true }

        assertTrue(handler.onLeft().handled)
        assertTrue(handler.onRight().handled)

        verify(exactly = 1) { actions.moveColumn(-1) }
        verify(exactly = 1) { actions.moveColumn(1) }
        verify(exactly = 0) { actions.previousGame() }
        verify(exactly = 0) { actions.nextGame() }
    }

    @Test
    fun `left and right follow a right to left layout`() {
        val (handler, actions) = handler(rtl = true) { every { moveColumn(any()) } returns true }

        handler.onLeft()

        verify(exactly = 1) { actions.moveColumn(1) }
    }

    @Test
    fun `a blocked move is consumed with the boundary sound`() {
        val (handler, _) = handler { every { moveColumn(any()) } returns false }

        val result = handler.onRight()

        assertTrue(result.handled)
        assertEquals(SoundType.BOUNDARY, result.soundOverride)
    }

    @Test
    fun `up and down step games in the focused column instead of changing rows`() {
        val (handler, actions) = handler { every { stepColumnGame(any()) } returns true }

        handler.onUp()
        handler.onDown()

        verify(exactly = 1) { actions.stepColumnGame(-1) }
        verify(exactly = 1) { actions.stepColumnGame(1) }
        verify(exactly = 0) { actions.previousRow() }
        verify(exactly = 0) { actions.nextRow() }
        verify(exactly = 0) { actions.focusAppBar() }
    }

    @Test
    fun `down past the last game focuses the app bar`() {
        val (handler, actions) = handler {
            every { stepColumnGame(1) } returns false
            every { focusAppBar() } returns true
        }

        val result = handler.onDown()

        assertTrue(result.handled)
        assertEquals(SoundType.SECTION_CHANGE, result.soundOverride)
        verify(exactly = 1) { actions.focusAppBar() }
    }

    @Test
    fun `up past the first game stays put`() {
        val (handler, actions) = handler { every { stepColumnGame(-1) } returns false }

        val result = handler.onUp()

        assertEquals(SoundType.BOUNDARY, result.soundOverride)
        verify(exactly = 0) { actions.focusAppBar() }
    }

    @Test
    fun `the triggers jump runs even when the app bar is not drawn`() {
        val (handler, actions) = handler {
            every { appBarIsDrawn() } returns false
            every { quickNavigation() } returns false
            every { jumpColumnRun(any()) } returns true
        }

        assertTrue(handler.onPrevTrigger().handled)
        assertTrue(handler.onNextTrigger().handled)

        verify(exactly = 1) { actions.jumpColumnRun(-1) }
        verify(exactly = 1) { actions.jumpColumnRun(1) }
        verify(exactly = 0) { actions.previousRow() }
        verify(exactly = 0) { actions.nextRow() }
    }

    @Test
    fun `the triggers outside Columns keep the drawn app bar gate`() {
        val (handler, actions) = handler(state = HomeUiState(layoutKind = HomeLayoutKind.CAROUSEL)) {
            every { appBarIsDrawn() } returns false
            every { quickNavigation() } returns false
        }

        handler.onNextTrigger()

        verify(exactly = 0) { actions.jumpColumnRun(any()) }
        verify(exactly = 0) { actions.nextRow() }
    }

    @Test
    fun `the bumpers do not jump runs`() {
        val (handler, actions) = handler {
            every { appBarIsDrawn() } returns false
            every { quickNavigation() } returns false
        }

        handler.onNextSection()

        verify(exactly = 0) { actions.jumpColumnRun(any()) }
        verify(exactly = 0) { actions.nextRow() }
    }

    @Test
    fun `confirm activates the focused game`() {
        val (handler, actions) = handler()

        handler.onConfirm()

        verify(exactly = 1) { actions.activateGame(game) }
    }

    @Test
    fun `details opens the focused game with its column as the context`() {
        var opened: Long? = null
        val actions = mockk<HomeInputActions>(relaxed = true)
        every { actions.uiState } returns MutableStateFlow(columnsState)
        val handler = HomeInputHandler(actions, true, { opened = it }, {}, {})

        handler.onContextMenu()

        assertEquals(42L, opened)
        verify(exactly = 1) { actions.setNavigationContext(listOf(42L)) }
    }

    private val takeoverPhases = listOf(VideoPreviewPhase.EXPANDED, VideoPreviewPhase.INFO)

    @Test
    fun `every press during the takeover only restores it`() {
        takeoverPhases.forEach { phase ->
            val presses: List<Pair<String, (HomeInputHandler) -> InputResult>> = listOf(
                "up" to { it.onUp() },
                "down" to { it.onDown() },
                "left" to { it.onLeft() },
                "right" to { it.onRight() },
                "back" to { it.onBack() },
                "start" to { it.onMenu() },
                "select" to { it.onSelect() },
                "long select" to { it.onLongSelect() },
                "long confirm" to { it.onLongConfirm() },
                "Y" to { it.onSecondaryAction() },
                "X" to { it.onContextMenu() },
                "LT" to { it.onPrevTrigger() },
                "RT" to { it.onNextTrigger() },
                "LB" to { it.onPrevSection() },
                "RB" to { it.onNextSection() },
                "L3" to { it.onLeftStickClick() },
                "R3" to { it.onRightStickClick() },
                "right stick up" to { it.onRightStickUp() },
                "right stick down" to { it.onRightStickDown() },
                "right stick left" to { it.onRightStickLeft() },
                "right stick right" to { it.onRightStickRight() }
            )
            presses.forEach { (name, press) ->
                var opened: Long? = null
                var drawerToggled = false
                val actions = mockk<HomeInputActions>(relaxed = true)
                every { actions.uiState } returns MutableStateFlow(columnsState.copy(videoPreviewPhase = phase))
                every { actions.restoreVideoTakeover() } returns true
                val handler = HomeInputHandler(actions, true, { opened = it }, {}, { drawerToggled = true })

                val result = press(handler)

                assertTrue("$name in $phase", result.handled)
                verify(exactly = 1) { actions.restoreVideoTakeover() }
                verify(exactly = 0) { actions.moveColumn(any()) }
                verify(exactly = 0) { actions.stepColumnGame(any()) }
                verify(exactly = 0) { actions.jumpColumnRun(any()) }
                verify(exactly = 0) { actions.resetColumnsFocus() }
                verify(exactly = 0) { actions.focusAppBar() }
                verify(exactly = 0) { actions.toggleFavorite(any()) }
                verify(exactly = 0) { actions.toggleGameMenu() }
                verify(exactly = 0) { actions.pressSelect(any()) }
                verify(exactly = 0) { actions.activateGame(any()) }
                assertEquals("$name in $phase", null, opened)
                assertFalse("$name in $phase", drawerToggled)
            }
        }
    }

    @Test
    fun `confirm during the takeover restores it and plays the focused game`() {
        takeoverPhases.forEach { phase ->
            val (handler, actions) = handler(state = columnsState.copy(videoPreviewPhase = phase))

            assertTrue(handler.onConfirm().handled)

            verify(exactly = 1) { actions.restoreVideoTakeover() }
            verify(exactly = 1) { actions.activateGame(game) }
        }
    }

    @Test
    fun `a clip still inside its column does not hold the input`() {
        listOf(VideoPreviewPhase.LOADING, VideoPreviewPhase.PLAYING, VideoPreviewPhase.RESTORING).forEach { phase ->
            val (handler, actions) = handler(state = columnsState.copy(videoPreviewPhase = phase)) {
                every { moveColumn(any()) } returns true
            }

            handler.onRight()

            verify(exactly = 0) { actions.restoreVideoTakeover() }
            verify(exactly = 1) { actions.moveColumn(1) }
        }
    }

    @Test
    fun `an expanded carousel preview does not hold the input`() {
        val (handler, actions) = handler(
            state = HomeUiState(layoutKind = HomeLayoutKind.CAROUSEL, videoPreviewPhase = VideoPreviewPhase.EXPANDED)
        )

        handler.onRight()

        verify(exactly = 0) { actions.restoreVideoTakeover() }
    }

    @Test
    fun `back returns to the first column before leaving Home`() {
        val (handler, actions) = handler { every { resetColumnsFocus() } returns true }

        handler.onBack()

        verify(exactly = 1) { actions.resetColumnsFocus() }
        verify(exactly = 0) { actions.scrollToFirst() }
        verify(exactly = 0) { actions.navigateToContinuePlaying() }
    }
}
