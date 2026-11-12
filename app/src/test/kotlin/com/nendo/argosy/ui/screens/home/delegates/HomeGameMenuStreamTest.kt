package com.nendo.argosy.ui.screens.home.delegates

import com.nendo.argosy.ui.screens.home.HomeGameUi
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class HomeGameMenuStreamTest {

    private val delegate = HomeGameMenuDelegate(
        context = mockk(relaxed = true),
        gameActions = mockk(relaxed = true),
        gameRepository = mockk(relaxed = true),
        soundManager = mockk(relaxed = true),
        notificationManager = mockk(relaxed = true)
    )

    private val game = HomeGameUi(
        id = 1,
        title = "Game",
        platformId = 7,
        platformSlug = "psx",
        platformDisplayName = "PlayStation",
        coverPath = null,
        backgroundPath = null,
        developer = null,
        releaseYear = null,
        genre = null,
        isFavorite = false,
        isDownloaded = false,
        isRommGame = true
    )

    @Test
    fun `the stream row sits right after download`() {
        assertEquals(GameMenuAction.Play(1, false, false), delegate.resolveMenuAction(0, game, false, streamable = true))
        assertEquals(GameMenuAction.Stream(1), delegate.resolveMenuAction(1, game, false, streamable = true))
        assertEquals(GameMenuAction.ToggleFavorite(1), delegate.resolveMenuAction(2, game, false, streamable = true))
    }

    @Test
    fun `no stream row without an offer`() {
        val actions = (0 until 12).map { delegate.resolveMenuAction(it, game, isPlatformRow = false) }
        assertFalse(actions.any { it is GameMenuAction.Stream })
        assertEquals(GameMenuAction.ToggleFavorite(1), delegate.resolveMenuAction(1, game, false))
    }

    @Test
    fun `focus reaches the last row with the stream row added`() {
        delegate.moveGameMenuFocus(20, game, isPlatformRow = false, streamable = true)
        val lastIndex = delegate.state.value.gameMenuFocusIndex

        assertEquals(GameMenuAction.Hide(1), delegate.resolveMenuAction(lastIndex, game, false, streamable = true))
    }
}
