package com.nendo.argosy.data.mod

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SwitchModLaunchGateTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test
    fun `a surviving journal blocks launch until explicit recovery clears it`() {
        val appFiles = temporary.newFolder()
        assertFalse(SwitchModLaunchGate.isBlocked(appFiles))
        val journal = File(SwitchModLaunchGate.stateRoot(appFiles), "package/transaction/pending.properties")
        journal.parentFile!!.mkdirs()
        journal.writeText("pending")
        assertTrue(SwitchModLaunchGate.isBlocked(appFiles))
        journal.delete()
        assertFalse(SwitchModLaunchGate.isBlocked(appFiles))
    }

    @Test
    fun `an invalid state root never permits a launch`() {
        val appFiles = temporary.newFolder()
        SwitchModLaunchGate.stateRoot(appFiles).writeText("invalid")
        assertTrue(SwitchModLaunchGate.isBlocked(appFiles))
    }
}
