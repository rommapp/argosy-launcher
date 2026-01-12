package com.nendo.argosy.integration

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RomMUserPropsTest : RomMIntegrationTest() {

    @Before
    fun auth() {
        authenticate()
    }

    @Test
    fun `ROM response includes romUser object`() = runBlocking {
        val romsResponse = api.getRoms(mapOf("limit" to "5"))
        assertTrue("getRoms should succeed", romsResponse.isSuccessful)
        val roms = romsResponse.body()!!.items
        assertTrue("Need at least one ROM", roms.isNotEmpty())

        val rom = roms.first()
        val romDetail = api.getRom(rom.id)
        assertTrue("getRom should succeed", romDetail.isSuccessful)

        val fullRom = romDetail.body()!!
        assertNotNull("ROM should have romUser object", fullRom.romUser)
    }

    @Test
    fun `romUser has rating field`() = runBlocking {
        val romsResponse = api.getRoms(mapOf("limit" to "1"))
        assertTrue("getRoms should succeed", romsResponse.isSuccessful)
        val rom = romsResponse.body()!!.items.first()

        val romDetail = api.getRom(rom.id)
        assertTrue("getRom should succeed", romDetail.isSuccessful)

        val fullRom = romDetail.body()!!
        assertNotNull("romUser should not be null", fullRom.romUser)
        assertTrue("rating should be non-negative", fullRom.romUser!!.rating >= 0)
    }

    @Test
    fun `romUser has difficulty field`() = runBlocking {
        val romsResponse = api.getRoms(mapOf("limit" to "1"))
        assertTrue("getRoms should succeed", romsResponse.isSuccessful)
        val rom = romsResponse.body()!!.items.first()

        val romDetail = api.getRom(rom.id)
        assertTrue("getRom should succeed", romDetail.isSuccessful)

        val fullRom = romDetail.body()!!
        assertNotNull("romUser should not be null", fullRom.romUser)
        assertTrue("difficulty should be non-negative", fullRom.romUser!!.difficulty >= 0)
    }
}
