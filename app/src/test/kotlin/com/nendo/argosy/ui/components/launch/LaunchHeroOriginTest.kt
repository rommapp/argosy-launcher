package com.nendo.argosy.ui.components.launch

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LaunchHeroOriginTest {

    private val origin = LaunchHeroOrigin(
        gameId = 7L,
        displayId = 0,
        bounds = Rect(10f, 20f, 300f, 600f),
        artPath = "/art/7.jpg",
        artIsScreenshot = false
    )

    @Test
    fun `the launch of the offered game on its screen grows the offered art`() {
        assertEquals(origin, heroOriginFor(origin, gameId = 7L, displayId = 0, gameOpensHere = true))
    }

    @Test
    fun `a launch of another game shows no hero`() {
        assertNull(heroOriginFor(origin, gameId = 8L, displayId = 0, gameOpensHere = true))
    }

    @Test
    fun `a launch drawn on another screen shows no hero`() {
        assertNull(heroOriginFor(origin, gameId = 7L, displayId = 4, gameOpensHere = true))
    }

    @Test
    fun `a game opening on the other screen shows no hero`() {
        assertNull(heroOriginFor(origin, gameId = 7L, displayId = 0, gameOpensHere = false))
    }

    @Test
    fun `a launch with no game or no offer shows no hero`() {
        assertNull(heroOriginFor(origin, gameId = null, displayId = 0, gameOpensHere = true))
        assertNull(heroOriginFor(null, gameId = 7L, displayId = 0, gameOpensHere = true))
    }

    @Test
    fun `withdrawing a screen clears only that screen's offer`() {
        val origins = LaunchHeroOrigins()
        origins.offer(origin)
        origins.withdraw(displayId = 4)
        assertEquals(origin, origins.origin.value)
        origins.withdraw(displayId = 0)
        assertNull(origins.origin.value)
    }
}
