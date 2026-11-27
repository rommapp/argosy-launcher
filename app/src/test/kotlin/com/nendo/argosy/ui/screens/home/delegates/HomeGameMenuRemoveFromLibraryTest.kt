package com.nendo.argosy.ui.screens.home.delegates

import com.nendo.argosy.ui.screens.home.HomeGameUi
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class HomeGameMenuRemoveFromLibraryTest {

    private val delegate = HomeGameMenuDelegate(
        context = mockk(relaxed = true),
        gameActions = mockk(relaxed = true),
        gameRepository = mockk(relaxed = true),
        soundManager = mockk(relaxed = true),
        notificationManager = mockk(relaxed = true)
    )

    private fun game(canRemoveFromLibrary: Boolean) = HomeGameUi(
        id = 1,
        title = "Rockman X4",
        platformId = 7,
        platformSlug = "saturn",
        platformDisplayName = "Saturn",
        platformShortName = "Saturn",
        coverPath = null,
        backgroundPath = null,
        developer = null,
        releaseYear = null,
        genre = null,
        isFavorite = false,
        isDownloaded = false,
        isRommGame = true,
        canRemoveFromLibrary = canRemoveFromLibrary
    )

    @Test
    fun `a removable game puts remove from library between refresh and hide`() {
        val removable = game(canRemoveFromLibrary = true)
        delegate.moveGameMenuFocus(20, removable, isPlatformRow = false)
        val lastIndex = delegate.state.value.gameMenuFocusIndex

        assertEquals(GameMenuAction.Hide(1), delegate.resolveMenuAction(lastIndex, removable, isPlatformRow = false))
        assertEquals(
            GameMenuAction.RemoveFromLibrary(1),
            delegate.resolveMenuAction(lastIndex - 1, removable, isPlatformRow = false)
        )
        assertEquals(
            GameMenuAction.Refresh(1, false),
            delegate.resolveMenuAction(lastIndex - 2, removable, isPlatformRow = false)
        )
    }

    @Test
    fun `a game that is not removable never resolves to remove from library`() {
        val kept = game(canRemoveFromLibrary = false)

        val actions = (0 until 12).map { delegate.resolveMenuAction(it, kept, isPlatformRow = false) }

        assertFalse(actions.any { it is GameMenuAction.RemoveFromLibrary })
    }
}
