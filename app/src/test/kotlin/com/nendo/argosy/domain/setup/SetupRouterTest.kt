package com.nendo.argosy.domain.setup

import com.nendo.argosy.domain.model.SetupFacts
import com.nendo.argosy.domain.model.SetupItem
import com.nendo.argosy.domain.model.SetupState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupRouterTest {

    private val satisfied = SetupFacts(
        librarySignedIn = true,
        librarySourceConfirmed = true,
        allFilesAccess = true,
        usageAccess = true,
        notifications = true,
        romFolderPresent = true,
        romFolderConfirmed = true,
        saveSyncAnswered = true
    )

    @Test
    fun `every enforced item satisfied is complete`() {
        assertEquals(SetupState.Complete, SetupRouter.derive(satisfied))
    }

    @Test
    fun `nothing satisfied starts at the library source`() {
        assertEquals(SetupState.Incomplete(SetupItem.LIBRARY_SOURCE), SetupRouter.derive(SetupFacts()))
    }

    @Test
    fun `the library needs both a sign-in and a confirmation`() {
        assertFalse(SetupRouter.isSatisfied(SetupItem.LIBRARY_SOURCE, satisfied.copy(librarySignedIn = false)))
        assertFalse(SetupRouter.isSatisfied(SetupItem.LIBRARY_SOURCE, satisfied.copy(librarySourceConfirmed = false)))
        assertTrue(SetupRouter.isSatisfied(SetupItem.LIBRARY_SOURCE, satisfied))
    }

    @Test
    fun `all files access follows the live grant`() {
        assertFalse(SetupRouter.isSatisfied(SetupItem.ALL_FILES_ACCESS, satisfied.copy(allFilesAccess = false)))
        assertTrue(SetupRouter.isSatisfied(SetupItem.ALL_FILES_ACCESS, satisfied))
    }

    @Test
    fun `usage access is satisfied by a grant or by a device with no screen to grant it`() {
        val denied = satisfied.copy(usageAccess = false)
        assertFalse(SetupRouter.isSatisfied(SetupItem.USAGE_ACCESS, denied))
        assertTrue(SetupRouter.isSatisfied(SetupItem.USAGE_ACCESS, denied.copy(usageAccessGrantable = false)))
        assertTrue(SetupRouter.isSatisfied(SetupItem.USAGE_ACCESS, satisfied))
    }

    @Test
    fun `notifications follow the live grant`() {
        assertFalse(SetupRouter.isSatisfied(SetupItem.NOTIFICATIONS, satisfied.copy(notifications = false)))
        assertTrue(SetupRouter.isSatisfied(SetupItem.NOTIFICATIONS, satisfied))
    }

    @Test
    fun `the rom folder needs a stored path and a confirmation`() {
        assertFalse(SetupRouter.isSatisfied(SetupItem.ROM_FOLDER, satisfied.copy(romFolderPresent = false)))
        assertFalse(SetupRouter.isSatisfied(SetupItem.ROM_FOLDER, satisfied.copy(romFolderConfirmed = false)))
        assertTrue(SetupRouter.isSatisfied(SetupItem.ROM_FOLDER, satisfied))
    }

    @Test
    fun `save sync needs an answer either way`() {
        assertFalse(SetupRouter.isSatisfied(SetupItem.SAVE_SYNC, satisfied.copy(saveSyncAnswered = false)))
        assertTrue(SetupRouter.isSatisfied(SetupItem.SAVE_SYNC, satisfied))
    }

    @Test
    fun `default home only applies to dual-screen devices`() {
        val dual = satisfied.copy(dualScreenDevice = true)
        assertTrue(SetupRouter.isSatisfied(SetupItem.DEFAULT_HOME, satisfied))
        assertFalse(SetupRouter.isSatisfied(SetupItem.DEFAULT_HOME, dual))
        assertTrue(SetupRouter.isSatisfied(SetupItem.DEFAULT_HOME, dual.copy(defaultHome = true)))
        assertTrue(SetupRouter.isSatisfied(SetupItem.DEFAULT_HOME, dual.copy(defaultHomeDeclined = true)))
    }

    @Test
    fun `default home is never enforced, so a dual-screen install that is not home stays complete`() {
        assertFalse(SetupItem.DEFAULT_HOME in SetupRouter.ENFORCED_ITEMS)
        assertEquals(SetupState.Complete, SetupRouter.derive(satisfied))
        assertEquals(SetupState.Complete, SetupRouter.derive(satisfied.copy(dualScreenDevice = true)))
    }

    @Test
    fun `satisfied required items still need the flow finished`() {
        assertTrue(SetupRouter.needsSetup(satisfied))
        assertFalse(SetupRouter.needsSetup(satisfied.copy(flowFinished = true)))
        assertTrue(SetupRouter.needsSetup(satisfied.copy(flowFinished = true, notifications = false)))
    }

    @Test
    fun `each unsatisfied item alone is the one reported`() {
        val cases = mapOf(
            SetupItem.LIBRARY_SOURCE to satisfied.copy(librarySourceConfirmed = false),
            SetupItem.ALL_FILES_ACCESS to satisfied.copy(allFilesAccess = false),
            SetupItem.USAGE_ACCESS to satisfied.copy(usageAccess = false),
            SetupItem.NOTIFICATIONS to satisfied.copy(notifications = false),
            SetupItem.ROM_FOLDER to satisfied.copy(romFolderConfirmed = false),
            SetupItem.SAVE_SYNC to satisfied.copy(saveSyncAnswered = false)
        )
        cases.forEach { (item, facts) -> assertEquals(SetupState.Incomplete(item), SetupRouter.derive(facts)) }
    }

    @Test
    fun `the earliest missing item in setup order wins`() {
        val laterFirst = satisfied.copy(saveSyncAnswered = false, notifications = false)
        assertEquals(SetupState.Incomplete(SetupItem.NOTIFICATIONS), SetupRouter.derive(laterFirst))
        val earlierFirst = satisfied.copy(allFilesAccess = false, romFolderConfirmed = false)
        assertEquals(SetupState.Incomplete(SetupItem.ALL_FILES_ACCESS), SetupRouter.derive(earlierFirst))
        assertEquals(
            SetupState.Incomplete(SetupItem.USAGE_ACCESS),
            SetupRouter.derive(
                satisfied.copy(usageAccess = false, notifications = false),
                listOf(SetupItem.NOTIFICATIONS, SetupItem.USAGE_ACCESS)
            )
        )
    }

    @Test
    fun `the enforced set is setup order ending at default home`() {
        assertEquals(
            listOf(
                SetupItem.LIBRARY_SOURCE,
                SetupItem.ALL_FILES_ACCESS,
                SetupItem.USAGE_ACCESS,
                SetupItem.NOTIFICATIONS,
                SetupItem.ROM_FOLDER,
                SetupItem.SAVE_SYNC
            ),
            SetupRouter.ENFORCED_ITEMS
        )
    }

    @Test
    fun `permissions are satisfied only when all three are`() {
        assertTrue(SetupRouter.permissionsSatisfied(satisfied))
        assertTrue(SetupRouter.permissionsSatisfied(satisfied.copy(usageAccess = false, usageAccessGrantable = false)))
        assertFalse(SetupRouter.permissionsSatisfied(satisfied.copy(allFilesAccess = false)))
        assertFalse(SetupRouter.permissionsSatisfied(satisfied.copy(usageAccess = false)))
        assertFalse(SetupRouter.permissionsSatisfied(satisfied.copy(notifications = false)))
    }
}
