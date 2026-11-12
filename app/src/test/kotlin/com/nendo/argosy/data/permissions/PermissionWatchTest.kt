package com.nendo.argosy.data.permissions

import com.nendo.argosy.data.preferences.AppPreferencesRepository
import com.nendo.argosy.data.preferences.SyncPreferences
import com.nendo.argosy.data.preferences.SyncPreferencesRepository
import com.nendo.argosy.domain.model.ArgosyPermission
import com.nendo.argosy.domain.model.PermissionAvailability
import com.nendo.argosy.domain.model.PermissionSheetKind
import com.nendo.argosy.domain.model.PermissionSheetRequest
import com.nendo.argosy.domain.model.PermissionSnapshot
import com.nendo.argosy.domain.permissions.PermissionChanges
import com.nendo.argosy.domain.permissions.PermissionRecord
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionWatchTest {

    private val usage = ArgosyPermission.USAGE_ACCESS

    private fun snapshot(vararg missing: ArgosyPermission): PermissionSnapshot = PermissionSnapshot(
        availability = ArgosyPermission.entries.associateWith {
            if (it in missing) PermissionAvailability.MISSING else PermissionAvailability.GRANTED
        }
    )

    private var current = snapshot()
    private var record = PermissionChanges.acknowledge(snapshot())
    private val syncPrefs = MutableStateFlow(SyncPreferences(saveSyncEnabled = true))

    private val registry: PermissionRegistry = mockk {
        every { snapshot(any()) } answers { current }
    }
    private val appPreferences: AppPreferencesRepository = mockk {
        coEvery { permissionRecord() } answers { record }
        val written = slot<PermissionRecord>()
        coEvery { setPermissionRecord(capture(written)) } answers { record = written.captured }
    }
    private val syncPreferences: SyncPreferencesRepository = mockk {
        every { preferences } returns syncPrefs
        val enabled = slot<Boolean>()
        coEvery { setSaveSyncEnabled(capture(enabled)) } answers {
            syncPrefs.value = syncPrefs.value.copy(saveSyncEnabled = enabled.captured)
        }
    }
    private val sheets: PermissionSheetController = mockk(relaxed = true) {
        every { hasHost } returns true
    }

    private val watch = PermissionWatch(registry, appPreferences, syncPreferences, mockk(relaxed = true), sheets)

    @Test
    fun `a revoked sync-required permission turns save sync off before the launch sheet`() = runTest {
        current = snapshot(usage)
        coEvery { sheets.show(any()) } returns true

        assertTrue(watch.clearForLaunch())

        assertFalse(syncPrefs.value.saveSyncEnabled)
        coVerify { sheets.show(PermissionSheetRequest(PermissionSheetKind.BEFORE_LAUNCH, listOf(usage))) }
    }

    @Test
    fun `save sync stays off once the permission is granted again`() = runTest {
        current = snapshot(usage)
        coEvery { sheets.show(any()) } returns true
        watch.clearForLaunch()

        current = snapshot()
        assertTrue(watch.clearForLaunch())

        assertFalse(syncPrefs.value.saveSyncEnabled)
        coVerify(exactly = 0) { syncPreferences.setSaveSyncEnabled(true) }
    }

    @Test
    fun `closing the launch sheet stops the launch and keeps the change unacknowledged`() = runTest {
        current = snapshot(ArgosyPermission.DISPLAY_OVERLAY)
        coEvery { sheets.show(any()) } returns false

        assertFalse(watch.clearForLaunch())

        assertTrue(ArgosyPermission.DISPLAY_OVERLAY in record.acknowledged.orEmpty())
    }

    @Test
    fun `continuing from the launch sheet records the new state so it does not return`() = runTest {
        current = snapshot(ArgosyPermission.DISPLAY_OVERLAY)
        coEvery { sheets.show(any()) } returns true

        assertTrue(watch.clearForLaunch())
        assertTrue(watch.clearForLaunch())

        coVerify(exactly = 1) { sheets.show(any()) }
    }

    @Test
    fun `with no surface to draw the sheet the launch goes on`() = runTest {
        current = snapshot(ArgosyPermission.DISPLAY_OVERLAY)
        every { sheets.hasHost } returns false

        assertTrue(watch.clearForLaunch())

        coVerify(exactly = 0) { sheets.show(any()) }
    }
}
