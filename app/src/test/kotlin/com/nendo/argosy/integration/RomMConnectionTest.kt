package com.nendo.argosy.integration

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RomMConnectionTest : RomMIntegrationTest() {

    @Test
    fun `heartbeat returns success when server reachable`() = runBlocking {
        val response = api.heartbeat()

        assertTrue("Heartbeat should succeed", response.isSuccessful)
        assertNotNull("Heartbeat should return version", response.body()?.version)
    }

    @Test
    fun `getCurrentUser returns user profile after auth`() = runBlocking {
        authenticate()

        val response = api.getCurrentUser()

        assertTrue("Get current user should succeed", response.isSuccessful)
        assertNotNull("User should have ID", response.body()?.id)
        assertNotNull("User should have username", response.body()?.username)
    }
}
