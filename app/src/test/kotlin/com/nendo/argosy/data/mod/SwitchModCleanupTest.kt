package com.nendo.argosy.data.mod

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SwitchModCleanupTest {
    @get:Rule val temporary = TemporaryFolder()
    private val title = "0100F2C0115B6000"
    private val emulatorRoot = "/eden"
    private val path = "load/$title/mod/exefs/main"

    @Test
    fun `restart after interrupted successful cleanup keeps the committed install`() {
        val committed = receipt("updated")
        checkInterruptedCleanup(receipt("original"), committed, committed)
    }

    @Test
    fun `restart after interrupted rollback cleanup keeps the restored install`() {
        val restored = receipt("original")
        checkInterruptedCleanup(restored, receipt("failed-update"), restored)
    }

    @Test
    fun `restart after interrupted removal cleanup leaves the package removed`() {
        checkInterruptedCleanup(receipt("removed"), null, null)
    }

    @Test
    fun `restart after interrupted first install rollback leaves no installed package`() {
        checkInterruptedCleanup(null, receipt("failed-first-install"), null)
    }

    private fun checkInterruptedCleanup(before: ModReceipt?, after: ModReceipt?, terminal: ModReceipt?) {
        val appFiles = temporary.newFolder()
        val stateRoot = SwitchModLaunchGate.stateRoot(appFiles)
        val store = SwitchModStateStore(stateRoot, emulatorRoot, title, "package")
        store.prepare(before, after, listOf(ModMutation(path, "before", "after", "0")))
        File(store.transaction, "0").writeText("backup")
        store.markStarted(1)
        store.persistReceipt(terminal)
        assertTrue(store.hasPending())
        assertTrue(SwitchModLaunchGate.isBlocked(appFiles))

        val retired = store.retireTransaction()
        assertNotNull(retired)
        File(retired!!, "0").delete()
        File(retired, "before.properties").delete()
        File(retired, "after.properties").delete()
        assertTrue(File(retired, "pending.properties").exists())

        val restarted = SwitchModStateStore(stateRoot, emulatorRoot, title, "package")
        assertFalse(restarted.pending.exists())
        assertFalse(restarted.hasPending())
        assertFalse(SwitchModLaunchGate.isBlocked(appFiles))
        assertEquals(terminal, restarted.receipt())
        assertEquals(listOfNotNull(terminal), restarted.claims())
        restarted.clearTransaction()
        assertFalse(retired.exists())
        assertEquals(terminal, restarted.receipt())
    }

    private fun receipt(hash: String) = ModReceipt(title, emulatorRoot, "package", hash, listOf(ModOwnedFile(path, hash, true)))
}
