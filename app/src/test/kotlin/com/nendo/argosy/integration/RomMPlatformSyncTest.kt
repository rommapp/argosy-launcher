package com.nendo.argosy.integration

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RomMPlatformSyncTest : RomMIntegrationTest() {

    @Before
    fun auth() {
        authenticate()
    }

    @Test
    fun `getPlatform by numeric ID succeeds`() = runBlocking {
        val platformsResponse = api.getPlatforms()
        assertTrue("Need platforms to test", platformsResponse.isSuccessful)
        val platforms = platformsResponse.body()!!
        assertTrue("Need at least one platform", platforms.isNotEmpty())

        val platformId = platforms.first().id
        val response = api.getPlatform(platformId)

        assertTrue("getPlatform by ID should succeed", response.isSuccessful)
        assertEquals("Platform ID should match", platformId, response.body()!!.id)
    }

    @Test
    fun `getPlatforms allows lookup by slug`() = runBlocking {
        val platformsResponse = api.getPlatforms()
        assertTrue("Need platforms to test", platformsResponse.isSuccessful)
        val platforms = platformsResponse.body()!!
        assertTrue("Need at least one platform", platforms.isNotEmpty())

        val targetSlug = platforms.first().slug
        val foundPlatform = platforms.find { it.slug == targetSlug }

        assertNotNull("Should find platform by slug", foundPlatform)
        assertEquals("Slug should match", targetSlug, foundPlatform!!.slug)
    }

    @Test
    fun `ROM response includes platformSlug for mapping`() = runBlocking {
        val romsResponse = api.getRoms(mapOf("limit" to "1"))
        assertTrue("Need ROMs to test", romsResponse.isSuccessful)
        val roms = romsResponse.body()!!.items
        assertTrue("Need at least one ROM", roms.isNotEmpty())

        val rom = roms.first()
        assertTrue("ROM platformSlug should not be empty", rom.platformSlug.isNotEmpty())
        assertTrue("ROM platformId should be positive", rom.platformId > 0)
    }

}
