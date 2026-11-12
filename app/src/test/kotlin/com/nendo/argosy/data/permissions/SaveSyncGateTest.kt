package com.nendo.argosy.data.permissions

import com.nendo.argosy.domain.model.ArgosyPermission
import com.nendo.argosy.domain.model.PermissionAvailability
import com.nendo.argosy.domain.model.PermissionSheetKind
import com.nendo.argosy.domain.model.PermissionSheetRequest
import com.nendo.argosy.domain.model.PermissionSnapshot
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SaveSyncGateTest {

    private fun snapshot(vararg missing: ArgosyPermission): PermissionSnapshot = PermissionSnapshot(
        availability = ArgosyPermission.entries.associateWith {
            if (it in missing) PermissionAvailability.MISSING else PermissionAvailability.GRANTED
        }
    )

    private var current = snapshot()
    private val registry: PermissionRegistry = mockk {
        every { snapshot(any()) } answers { current }
    }
    private val sheets: PermissionSheetController = mockk()
    private val gate = SaveSyncGate(registry, sheets)

    @Test
    fun `with every sync-required permission granted save sync turns on without a sheet`() = runTest {
        assertTrue(gate.clearToEnable())

        coVerify(exactly = 0) { sheets.show(any()) }
    }

    @Test
    fun `turning save sync on with notifications missing is blocked when the sheet is closed`() = runTest {
        current = snapshot(ArgosyPermission.NOTIFICATIONS, ArgosyPermission.DISPLAY_OVERLAY)
        coEvery { sheets.show(any()) } returns false

        assertFalse(gate.clearToEnable())

        coVerify {
            sheets.show(PermissionSheetRequest(PermissionSheetKind.SAVE_SYNC, listOf(ArgosyPermission.NOTIFICATIONS)))
        }
    }

    @Test
    fun `granting the missing permission from the sheet lets save sync turn on`() = runTest {
        current = snapshot(ArgosyPermission.USAGE_ACCESS)
        coEvery { sheets.show(any()) } answers {
            current = snapshot()
            true
        }

        assertTrue(gate.clearToEnable())
    }

    @Test
    fun `a sheet answered while a permission is still missing keeps save sync off`() = runTest {
        current = snapshot(ArgosyPermission.ALL_FILES_ACCESS)
        coEvery { sheets.show(any()) } returns true

        assertFalse(gate.clearToEnable())
    }

    @Test
    fun `usage access with no screen on this device does not block save sync`() = runTest {
        current = PermissionSnapshot(
            availability = snapshot().availability +
                (ArgosyPermission.USAGE_ACCESS to PermissionAvailability.UNAVAILABLE)
        )

        assertTrue(gate.clearToEnable())
        coVerify(exactly = 0) { sheets.show(any()) }
    }
}
