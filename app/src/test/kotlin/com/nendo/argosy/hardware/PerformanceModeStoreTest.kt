package com.nendo.argosy.hardware

import com.nendo.argosy.data.preferences.PerformancePreferencesRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PerformanceModeStoreTest {

    private class RootBackend(private var granted: Boolean, private val grantOnRequest: Boolean = true) :
        DevicePerformance {
        override val modes = listOf(PerformanceMode.BALANCED, PerformanceMode.GAMING)
        override val controlsFan = false
        override val controlsRefreshRate = false
        override val needsRootGrant = true
        override val restoresSavedMode = true
        override val canWrite: Boolean get() = granted
        var accessRequests = 0
            private set
        val applied = mutableListOf<PerformanceMode>()

        override fun requestAccess(): Boolean {
            accessRequests++
            if (!granted) granted = grantOnRequest
            return granted
        }

        override fun currentMode(): PerformanceMode? = applied.lastOrNull()

        override fun applyMode(mode: PerformanceMode): Boolean {
            applied += mode
            return true
        }
    }

    private fun preferences(saved: PerformanceMode?, approved: Boolean) =
        mockk<PerformancePreferencesRepository>(relaxed = true) {
            coEvery { getPerformanceMode() } returns saved?.name
            coEvery { isRootGranted() } returns approved
        }

    private fun store(backend: DevicePerformance, preferences: PerformancePreferencesRepository) =
        PerformanceModeStore(mockk<DevicePerformanceResolver> { every { resolve() } returns backend }, preferences)

    @Test
    fun `startup leaves a device never approved untouched`() = runTest {
        val backend = RootBackend(granted = false)

        store(backend, preferences(PerformanceMode.GAMING, approved = false)).restoreSavedMode()

        assertEquals(0, backend.accessRequests)
        assertTrue(backend.applied.isEmpty())
    }

    @Test
    fun `startup reapplies the saved mode once approval is on record`() = runTest {
        val backend = RootBackend(granted = false)

        store(backend, preferences(PerformanceMode.GAMING, approved = true)).restoreSavedMode()

        assertEquals(1, backend.accessRequests)
        assertEquals(listOf(PerformanceMode.GAMING), backend.applied)
    }

    @Test
    fun `startup clears the approval record when root is refused`() = runTest {
        val backend = RootBackend(granted = false, grantOnRequest = false)
        val preferences = preferences(PerformanceMode.GAMING, approved = true)

        store(backend, preferences).restoreSavedMode()

        assertTrue(backend.applied.isEmpty())
        coVerify { preferences.setRootGranted(false) }
    }

    @Test
    fun `a granted request is recorded and a chosen mode is saved by its token`() = runTest {
        val backend = RootBackend(granted = false)
        val preferences = preferences(saved = null, approved = false)
        val store = store(backend, preferences)

        assertTrue(store.requestAccess(backend))
        store.remember(backend, PerformanceMode.GAMING)

        coVerify { preferences.setRootGranted(true) }
        coVerify { preferences.setPerformanceMode("GAMING") }
    }

    @Test
    fun `a new process re-acquires access only when approval is on record`() = runTest {
        val unapproved = RootBackend(granted = false)
        store(unapproved, preferences(saved = null, approved = false)).resumeAccess(unapproved)
        assertEquals(0, unapproved.accessRequests)

        val approved = RootBackend(granted = false)
        store(approved, preferences(saved = null, approved = true)).resumeAccess(approved)
        assertEquals(1, approved.accessRequests)
    }
}
