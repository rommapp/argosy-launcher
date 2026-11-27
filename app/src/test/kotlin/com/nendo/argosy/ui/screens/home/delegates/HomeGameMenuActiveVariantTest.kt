package com.nendo.argosy.ui.screens.home.delegates

import com.nendo.argosy.ui.screens.home.HomeGameUi
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeGameMenuActiveVariantTest {

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
        platformSlug = "snes",
        platformDisplayName = "SNES",
        platformShortName = "SNES",
        coverPath = null,
        backgroundPath = null,
        developer = null,
        releaseYear = null,
        genre = null,
        isFavorite = false,
        isDownloaded = false,
        isRommGame = true
    )

    private fun actions(hasSiblingGroup: Boolean): List<GameMenuAction> =
        (0 until 12).map { delegate.resolveMenuAction(it, game, isPlatformRow = false, hasSiblingGroup = hasSiblingGroup) }

    @Test
    fun `the active variant row is hidden for a single-member group`() {
        assertFalse(actions(hasSiblingGroup = false).any { it is GameMenuAction.ActiveVariant })
    }

    @Test
    fun `the active variant row follows add to collection`() {
        assertEquals(
            GameMenuAction.AddToCollection(1),
            delegate.resolveMenuAction(3, game, isPlatformRow = false, hasSiblingGroup = true)
        )
        assertEquals(
            GameMenuAction.ActiveVariant(1),
            delegate.resolveMenuAction(4, game, isPlatformRow = false, hasSiblingGroup = true)
        )
        assertTrue(
            delegate.resolveMenuAction(5, game, isPlatformRow = false, hasSiblingGroup = true) is
                GameMenuAction.Refresh
        )
    }

    @Test
    fun `focus reaches the last row once the active variant row is added`() {
        delegate.moveGameMenuFocus(20, game, isPlatformRow = false, hasSiblingGroup = true)
        val lastIndex = delegate.state.value.gameMenuFocusIndex

        assertEquals(
            GameMenuAction.Hide(1),
            delegate.resolveMenuAction(lastIndex, game, isPlatformRow = false, hasSiblingGroup = true)
        )
        assertEquals(
            GameMenuAction.Refresh(1, false),
            delegate.resolveMenuAction(lastIndex - 1, game, isPlatformRow = false, hasSiblingGroup = true)
        )
    }
}
