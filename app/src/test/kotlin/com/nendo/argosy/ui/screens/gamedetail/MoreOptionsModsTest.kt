package com.nendo.argosy.ui.screens.gamedetail

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MoreOptionsModsTest {
    @Test
    fun `mods remain reachable for an already downloaded Switch game`() {
        val actions = buildMoreOptions(MoreOptionsContext(isDownloaded = true, platformSlug = "switch"))
        assertTrue(actions.contains(MoreOptionAction.Mods))
    }

    @Test
    fun `mods are hidden until a Switch game is downloaded`() {
        assertFalse(buildMoreOptions(MoreOptionsContext(platformSlug = "switch")).contains(MoreOptionAction.Mods))
        assertFalse(buildMoreOptions(MoreOptionsContext(isDownloaded = true, platformSlug = "3ds")).contains(MoreOptionAction.Mods))
    }
}
