package com.nendo.argosy.ui.screens.gamedetail

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Deleting a download and dropping a game from the library are separate acts for Steam,
 * because a Steam row exists only because the user added it. Removing a download used to
 * take the row with it, which left an owned game unreachable, and the removal itself was
 * reachable only while the game happened to be installed.
 */
class MoreOptionsSteamTest {

    private fun steam(isDownloaded: Boolean) = MoreOptionsContext(
        isDownloaded = isDownloaded,
        isSteamGame = true,
        platformSlug = "steam"
    )

    @Test
    fun `an installed steam game offers both deleting the download and removing it`() {
        val options = buildMoreOptions(steam(isDownloaded = true))

        assertTrue(options.contains(MoreOptionAction.Delete))
        assertTrue(options.contains(MoreOptionAction.RemoveFromLibrary))
    }

    @Test
    fun `an uninstalled steam game can still be removed from the library`() {
        val options = buildMoreOptions(steam(isDownloaded = false))

        assertFalse("there is no download to delete", options.contains(MoreOptionAction.Delete))
        assertTrue(options.contains(MoreOptionAction.RemoveFromLibrary))
    }

    @Test
    fun `removal is offered for steam games only`() {
        val romm = MoreOptionsContext(isDownloaded = true, isRommGame = true, platformSlug = "3ds")

        assertTrue(buildMoreOptions(romm).contains(MoreOptionAction.Delete))
        assertFalse(buildMoreOptions(romm).contains(MoreOptionAction.RemoveFromLibrary))
    }

    @Test
    fun `removal sits after deleting the download`() {
        val options = buildMoreOptions(steam(isDownloaded = true))

        assertTrue(
            options.indexOf(MoreOptionAction.RemoveFromLibrary) >
                options.indexOf(MoreOptionAction.Delete)
        )
    }
}
