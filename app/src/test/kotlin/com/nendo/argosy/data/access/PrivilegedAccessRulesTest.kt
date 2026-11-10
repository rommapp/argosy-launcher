package com.nendo.argosy.data.access

import com.nendo.argosy.data.model.PrivilegedReach
import com.nendo.argosy.data.model.PrivilegedRoute
import com.nendo.argosy.util.RootShell
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivilegedAccessRulesTest {

    private val reached = PrivilegedAccessRules.REACHED

    @Test
    fun `a vendor root daemon means nothing is offered`() {
        assertNull(PrivilegedAccessRules.offeredRoute(vendorRoot = true, magiskInstalled = true, shizukuRunning = true))
    }

    @Test
    fun `Magisk is offered before Shizuku and either alone is offered`() {
        assertEquals(
            PrivilegedRoute.MAGISK,
            PrivilegedAccessRules.offeredRoute(vendorRoot = false, magiskInstalled = true, shizukuRunning = true)
        )
        assertEquals(
            PrivilegedRoute.MAGISK,
            PrivilegedAccessRules.offeredRoute(vendorRoot = false, magiskInstalled = true, shizukuRunning = false)
        )
        assertEquals(
            PrivilegedRoute.SHIZUKU,
            PrivilegedAccessRules.offeredRoute(vendorRoot = false, magiskInstalled = false, shizukuRunning = true)
        )
        assertNull(PrivilegedAccessRules.offeredRoute(vendorRoot = false, magiskInstalled = false, shizukuRunning = false))
    }

    @Test
    fun `the prompt waits for first run, appears once, and never for an enabled route`() {
        val detected = PrivilegedRoute.MAGISK
        assertNull(PrivilegedAccessRules.promptRoute(firstRunComplete = false, answered = false, enabled = null, detected = detected))
        assertEquals(detected, PrivilegedAccessRules.promptRoute(firstRunComplete = true, answered = false, enabled = null, detected = detected))
        assertNull(PrivilegedAccessRules.promptRoute(firstRunComplete = true, answered = true, enabled = null, detected = detected))
        assertNull(PrivilegedAccessRules.promptRoute(firstRunComplete = true, answered = false, enabled = detected, detected = detected))
        assertNull(PrivilegedAccessRules.promptRoute(firstRunComplete = true, answered = false, enabled = null, detected = null))
    }

    @Test
    fun `uid 0 that reaches the folders backs root features through either route`() {
        val result = RootShell.Result(0, listOf("0", reached))
        assertEquals(PrivilegedReach.ROOT, PrivilegedAccessRules.reachOf(PrivilegedRoute.MAGISK, result))
        assertEquals(PrivilegedReach.ROOT, PrivilegedAccessRules.reachOf(PrivilegedRoute.SHIZUKU, result))
    }

    @Test
    fun `shell-level Shizuku that reaches the folders backs save folders only`() {
        val result = RootShell.Result(0, listOf("2000", reached))
        assertEquals(PrivilegedReach.APP_DATA, PrivilegedAccessRules.reachOf(PrivilegedRoute.SHIZUKU, result))
    }

    @Test
    fun `a Magisk route that is not uid 0 is not trusted`() {
        val result = RootShell.Result(0, listOf("2000", reached))
        assertEquals(PrivilegedReach.UNREACHABLE, PrivilegedAccessRules.reachOf(PrivilegedRoute.MAGISK, result))
    }

    @Test
    fun `a listing failure or a missing marker is unreachable, and no result is refused`() {
        assertEquals(
            PrivilegedReach.UNREACHABLE,
            PrivilegedAccessRules.reachOf(PrivilegedRoute.SHIZUKU, RootShell.Result(5, listOf("2000")))
        )
        assertEquals(
            PrivilegedReach.UNREACHABLE,
            PrivilegedAccessRules.reachOf(PrivilegedRoute.SHIZUKU, RootShell.Result(0, listOf("2000")))
        )
        assertEquals(
            PrivilegedReach.UNREACHABLE,
            PrivilegedAccessRules.reachOf(PrivilegedRoute.MAGISK, RootShell.Result(0, listOf("uid?", reached)))
        )
        assertEquals(PrivilegedReach.REFUSED, PrivilegedAccessRules.reachOf(PrivilegedRoute.MAGISK, null))
    }

    @Test
    fun `the check reads the path each identity can reach and skips Argosy's own folder`() {
        val script = PrivilegedAccessRules.checkScript(userId = 0)
        assertTrue("/data/media/0/Android/data" in script)
        assertTrue("/storage/emulated/0/Android/data" in script)
        assertTrue("*/com.nendo.argosy*) continue" in script)
        assertTrue(script.trimEnd().endsWith("echo $reached"))
    }
}
