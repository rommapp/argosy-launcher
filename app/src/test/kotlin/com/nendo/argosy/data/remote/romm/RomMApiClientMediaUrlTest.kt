package com.nendo.argosy.data.remote.romm

import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test

class RomMApiClientMediaUrlTest {

    private val connectionManager = mockk<RomMConnectionManager>()
    private val client = RomMApiClient(connectionManager, mockk(relaxed = true))

    @Test
    fun `a stored base with a trailing slash joins media paths with a single slash`() {
        every { connectionManager.getBaseUrl() } returns "http://192.168.1.215:3000/"

        assertEquals(
            "http://192.168.1.215:3000/assets/romm/resources/roms/3/852/cover/small.webp",
            client.buildMediaUrl("/assets/romm/resources/roms/3/852/cover/small.webp")
        )
        assertEquals(
            "http://192.168.1.215:3000/api/screenshots/9/content",
            client.buildMediaUrl("/api/screenshots/9/content")
        )
        assertEquals(
            "http://192.168.1.215:3000/assets/romm/resources/roms/3/852/box2d.png",
            client.buildResourceUrl("roms/3/852/box2d.png")
        )
        assertEquals(
            "http://192.168.1.215:3000/api/screenshots/9/content",
            client.buildScreenshotContentUrl(9L)
        )
    }
}
