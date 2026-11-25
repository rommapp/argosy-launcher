package com.nendo.argosy.data.emulator.config

import com.nendo.argosy.data.emulator.config.EmulatorDataRoute.ALT_ACCESS
import com.nendo.argosy.data.emulator.config.EmulatorDataRoute.APP_DATA_SHELL
import com.nendo.argosy.data.emulator.config.EmulatorDataRoute.FILE_ACCESS
import com.nendo.argosy.data.emulator.config.EmulatorDataRoute.ROOT
import com.nendo.argosy.util.RootShell
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EmulatorAccessRulesTest {

    private fun facts(
        root: Boolean = false,
        shell: Boolean = false,
        falWrite: Boolean = false,
        probe: Boolean = false,
        readable: Boolean = true,
        alt: Boolean = false,
        mirrored: Boolean = false
    ) = EmulatorAccessFacts(
        fileAccessReadable = readable,
        fileAccessWritable = falWrite,
        altAccess = alt,
        mirrored = mirrored,
        rootAvailable = root,
        appDataShellAvailable = shell,
        labelProbePassed = probe
    )

    private data class Row(
        val root: Boolean,
        val shell: Boolean,
        val falWrite: Boolean,
        val probe: Boolean,
        val write: EmulatorDataRoute?,
        val edit: EmulatorDataRoute?
    )

    private val table = listOf(
        Row(root = false, shell = false, falWrite = false, probe = false, write = null, edit = null),
        Row(root = false, shell = false, falWrite = false, probe = true, write = null, edit = null),
        Row(root = false, shell = false, falWrite = true, probe = false, write = FILE_ACCESS, edit = null),
        Row(root = false, shell = false, falWrite = true, probe = true, write = FILE_ACCESS, edit = null),
        Row(root = false, shell = true, falWrite = false, probe = false, write = APP_DATA_SHELL, edit = null),
        Row(root = false, shell = true, falWrite = false, probe = true, write = APP_DATA_SHELL, edit = null),
        Row(root = false, shell = true, falWrite = true, probe = false, write = FILE_ACCESS, edit = null),
        Row(root = false, shell = true, falWrite = true, probe = true, write = FILE_ACCESS, edit = null),
        Row(root = true, shell = false, falWrite = false, probe = false, write = ROOT, edit = null),
        Row(root = true, shell = false, falWrite = false, probe = true, write = ROOT, edit = ROOT),
        Row(root = true, shell = false, falWrite = true, probe = false, write = FILE_ACCESS, edit = null),
        Row(root = true, shell = false, falWrite = true, probe = true, write = FILE_ACCESS, edit = ROOT),
        Row(root = true, shell = true, falWrite = false, probe = false, write = ROOT, edit = null),
        Row(root = true, shell = true, falWrite = false, probe = true, write = ROOT, edit = ROOT),
        Row(root = true, shell = true, falWrite = true, probe = false, write = FILE_ACCESS, edit = null),
        Row(root = true, shell = true, falWrite = true, probe = true, write = FILE_ACCESS, edit = ROOT)
    )

    @Test
    fun `write and edit follow the decision table for every combination`() {
        assertEquals(16, table.map { listOf(it.root, it.shell, it.falWrite, it.probe) }.toSet().size)
        table.forEach { row ->
            val decided = EmulatorAccessRules.decide(ROOT_PATH, facts(row.root, row.shell, row.falWrite, row.probe))

            assertEquals("write for $row", row.write, decided.writeAppDataRoute)
            assertEquals("edit for $row", row.edit, decided.editRoute)
            assertEquals("read for $row", FILE_ACCESS, decided.readRoute)
            assertEquals(ROOT_PATH, decided.dataRoot)
        }
    }

    @Test
    fun `a missing edit route names whether root is absent or failed the probe`() {
        table.forEach { row ->
            val decided = EmulatorAccessRules.decide(ROOT_PATH, facts(row.root, row.shell, row.falWrite, row.probe))
            val expected = when {
                row.edit != null -> null
                row.root -> EmulatorEditBlock.ROOT_PROBE_FAILED
                else -> EmulatorEditBlock.NO_ROOT
            }

            assertEquals("edit block for $row", expected, decided.editBlock)
        }
    }

    @Test
    fun `the app data shell never edits`() {
        val decided = EmulatorAccessRules.decide(ROOT_PATH, facts(shell = true, falWrite = true, probe = true))

        assertFalse(decided.canEdit)
        assertTrue(decided.canWriteAppData)
    }

    @Test
    fun `an unreadable data root reads nothing yet root still writes and edits`() {
        val decided = EmulatorAccessRules.decide(ROOT_PATH, facts(root = true, probe = true, readable = false))

        assertNull(decided.readRoute)
        assertEquals(ROOT, decided.writeAppDataRoute)
        assertEquals(ROOT, decided.editRoute)
    }

    @Test
    fun `read route names the tier the file access layer uses`() {
        assertEquals(FILE_ACCESS, EmulatorAccessRules.decide(ROOT_PATH, facts()).readRoute)
        assertEquals(ALT_ACCESS, EmulatorAccessRules.decide(ROOT_PATH, facts(alt = true)).readRoute)
        assertEquals(ROOT, EmulatorAccessRules.decide(ROOT_PATH, facts(mirrored = true, root = true, alt = true)).readRoute)
        assertEquals(APP_DATA_SHELL, EmulatorAccessRules.decide(ROOT_PATH, facts(mirrored = true, shell = true, alt = true)).readRoute)
    }

    @Test
    fun `a mirrored write through the file access layer names the mirror's route`() {
        val decided = EmulatorAccessRules.decide(ROOT_PATH, facts(mirrored = true, shell = true, falWrite = true))

        assertEquals(APP_DATA_SHELL, decided.writeAppDataRoute)
    }

    @Test
    fun `the data root is the volume holding the newest primary file`() {
        val chosen = EmulatorAccessRules.chooseDataRoot(
            listOf(
                DataRootCandidate(INTERNAL, 100L),
                DataRootCandidate(SD, 200L)
            )
        )

        assertEquals(SD, chosen)
    }

    @Test
    fun `equal primary file times keep candidate order`() {
        val chosen = EmulatorAccessRules.chooseDataRoot(
            listOf(
                DataRootCandidate(SD, 100L),
                DataRootCandidate(INTERNAL, 100L)
            )
        )

        assertEquals(SD, chosen)
    }

    @Test
    fun `a volume holding the primary file beats one that does not`() {
        val chosen = EmulatorAccessRules.chooseDataRoot(
            listOf(
                DataRootCandidate(INTERNAL, null),
                DataRootCandidate(SD, 1L)
            )
        )

        assertEquals(SD, chosen)
    }

    @Test
    fun `with no primary file anywhere the first volume is the data root`() {
        val chosen = EmulatorAccessRules.chooseDataRoot(
            listOf(
                DataRootCandidate(INTERNAL, null),
                DataRootCandidate(SD, null)
            )
        )

        assertEquals(INTERNAL, chosen)
        assertNull(EmulatorAccessRules.chooseDataRoot(emptyList()))
    }

    @Test
    fun `probe passes only on a zero exit with the pass marker`() {
        assertEquals(true, EmulatorAccessRules.probePassed(RootShell.Result(0, listOf("probe=0"))))
        assertEquals(false, EmulatorAccessRules.probePassed(RootShell.Result(3, listOf("probe=3"))))
        assertEquals(false, EmulatorAccessRules.probePassed(RootShell.Result(0, emptyList())))
        assertEquals(false, EmulatorAccessRules.probePassed(RootShell.Result(null, listOf("probe=0"))))
        assertNull(EmulatorAccessRules.probePassed(null))
    }

    @Test
    fun `probe checks uid then relabels, confirms, chowns and always deletes the scratch file`() {
        val script = EmulatorAccessRules.probeScript("/data/user/0/com.nendo.argosy/cache/emulator-config-probe")
        val steps = listOf("id -u", "touch ", "chcon ", "ls -Z ", "chown 1000", "echo probe=")

        val positions = steps.map { script.indexOf(it) }

        assertTrue("every step present: $positions", positions.none { it < 0 })
        assertEquals(positions.sorted(), positions)
        assertTrue(script.contains(EmulatorAccessRules.PROBE_LABEL))
        assertTrue(script.lastIndexOf("rm -f ") in script.indexOf("chown 1000")..script.indexOf("echo probe="))
    }

    private companion object {
        const val INTERNAL = "/storage/emulated/0/Android/data/dev.eden.eden_emulator/files"
        const val SD = "/storage/546B-6466/Android/data/dev.eden.eden_emulator/files"
        const val ROOT_PATH = INTERNAL
    }
}
