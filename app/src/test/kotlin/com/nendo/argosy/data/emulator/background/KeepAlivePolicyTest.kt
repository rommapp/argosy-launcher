package com.nendo.argosy.data.emulator.background

import android.app.Application
import android.content.Intent
import com.nendo.argosy.data.emulator.EmulatorRegistry
import com.nendo.argosy.data.emulator.EmulatorResolver
import com.nendo.argosy.data.preferences.UserPreferences
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.util.PermissionHelper
import com.nendo.argosy.util.RootShell
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class KeepAlivePolicyTest {

    private val application = mockk<Application> {
        every { packageName } returns OWN_PACKAGE
    }
    private val emulatorResolver = mockk<EmulatorResolver> {
        every { resolveEmulatorId(DUCKSTATION) } returns "duckstation"
        every { resolveEmulatorId(VITA3K) } returns "vita3k"
        every { resolveEmulatorId(OWN_PACKAGE) } returns null
        every { resolveEmulatorId(EmulatorRegistry.BUILTIN_PACKAGE) } returns null
    }
    private val permissionHelper = mockk<PermissionHelper> {
        every { hasUsageStatsPermission(any()) } returns true
    }

    @Before
    fun stubRoot() {
        mockkObject(RootShell)
    }

    @After
    fun tearDown() {
        unmockkObject(RootShell)
    }

    private fun policy(closeOnReturn: Boolean, root: Boolean): KeepAlivePolicy {
        every { RootShell.isAvailable } returns root
        val preferences = mockk<UserPreferencesRepository> {
            every { this@mockk.preferences } returns flowOf(UserPreferences(closeEmulatorOnSessionEnd = closeOnReturn))
        }
        return KeepAlivePolicy(application, preferences, emulatorResolver, permissionHelper)
    }

    @Test
    fun `built-in games stay open with or without root unless the player closes them`() = runTest {
        assertTrue(policy(closeOnReturn = false, root = false).keepsAlive(EmulatorRegistry.BUILTIN_PACKAGE))
        assertTrue(policy(closeOnReturn = false, root = true).keepsAlive(EmulatorRegistry.BUILTIN_PACKAGE))
        assertFalse(policy(closeOnReturn = true, root = true).keepsAlive(EmulatorRegistry.BUILTIN_PACKAGE))
    }

    @Test
    fun `an external emulator stays open only on root`() = runTest {
        assertTrue(policy(closeOnReturn = false, root = true).keepsAlive(DUCKSTATION))
        assertFalse(policy(closeOnReturn = false, root = false).keepsAlive(DUCKSTATION))
        assertFalse(policy(closeOnReturn = true, root = true).keepsAlive(DUCKSTATION))
    }

    @Test
    fun `usage access alone does not keep an external emulator open`() = runTest {
        every { permissionHelper.hasUsageStatsPermission(any()) } returns true

        assertFalse(policy(closeOnReturn = false, root = false).keepsAlive(DUCKSTATION))
    }

    @Test
    fun `an emulator stopped before every launch never stays open`() = runTest {
        assertFalse(policy(closeOnReturn = false, root = true).keepsAlive(VITA3K))
    }

    @Test
    fun `argosy itself never counts as a kept game`() = runTest {
        assertFalse(policy(closeOnReturn = false, root = true).keepsAlive(OWN_PACKAGE))
    }

    @Test
    fun `a kept emulator launches without no-history and a closed one keeps it`() = runTest {
        val flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_HISTORY

        assertEquals(
            Intent.FLAG_ACTIVITY_NEW_TASK,
            policy(closeOnReturn = false, root = true).launchFlags(DUCKSTATION, flags)
        )
        assertEquals(flags, policy(closeOnReturn = false, root = false).launchFlags(DUCKSTATION, flags))
    }

    private companion object {
        const val OWN_PACKAGE = "com.nendo.argosy.debug"
        const val DUCKSTATION = "com.github.stenzek.duckstation"
        const val VITA3K = "org.vita3k.emulator"
    }
}
