package com.nendo.argosy.integration

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RomMLibrarySyncTest : RomMIntegrationTest() {

    @Before
    fun auth() {
        authenticate()
    }

    @Test
    fun `getPlatforms returns list with expected fields`() = runBlocking {
        val response = api.getPlatforms()

        assertTrue("getPlatforms should succeed", response.isSuccessful)
        val platforms = response.body()
        assertNotNull("Platforms list should not be null", platforms)
        assertTrue("Platforms list should not be empty", platforms!!.isNotEmpty())

        val platform = platforms.first()
        assertTrue("Platform should have positive ID", platform.id > 0)
        assertTrue("Platform slug should not be empty", platform.slug.isNotEmpty())
        assertTrue("Platform name should not be empty", platform.name.isNotEmpty())
        assertTrue("Platform romCount should be non-negative", platform.romCount >= 0)
    }

    @Test
    fun `getPlatform by ID returns single platform`() = runBlocking {
        val platformsResponse = api.getPlatforms()
        assertTrue("Need platforms to test", platformsResponse.isSuccessful)
        val platforms = platformsResponse.body()!!
        assertTrue("Need at least one platform", platforms.isNotEmpty())

        val platformId = platforms.first().id
        val response = api.getPlatform(platformId)

        assertTrue("getPlatform should succeed", response.isSuccessful)
        val platform = response.body()
        assertNotNull("Platform should not be null", platform)
        assertTrue("Platform ID should match", platform!!.id == platformId)
    }

    @Test
    fun `getRoms returns paginated results with limit 5`() = runBlocking {
        val response = api.getRoms(mapOf("limit" to "5"))

        assertTrue("getRoms should succeed", response.isSuccessful)
        val page = response.body()
        assertNotNull("ROM page should not be null", page)
        assertTrue("Page should have items", page!!.items.isNotEmpty())
        assertTrue("Items should respect limit", page.items.size <= 5)
        assertTrue("Total should be positive", page.total > 0)
    }

    @Test
    fun `getRom by ID returns full ROM with metadata`() = runBlocking {
        val romsResponse = api.getRoms(mapOf("limit" to "1"))
        assertTrue("Need ROMs to test", romsResponse.isSuccessful)
        val roms = romsResponse.body()!!.items
        assertTrue("Need at least one ROM", roms.isNotEmpty())

        val romId = roms.first().id
        val response = api.getRom(romId)

        assertTrue("getRom should succeed", response.isSuccessful)
        val rom = response.body()
        assertNotNull("ROM should not be null", rom)
        assertTrue("ROM ID should match", rom!!.id == romId)
        assertTrue("ROM should have name", rom.name.isNotEmpty())
        assertTrue("ROM should have platformSlug", rom.platformSlug.isNotEmpty())
        assertTrue("ROM should have platformId", rom.platformId > 0)
    }
}
