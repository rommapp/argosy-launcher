package com.nendo.argosy.ui.screens.settings.sections

import com.nendo.argosy.data.emulator.config.ConfigFileState
import com.nendo.argosy.data.emulator.config.ConfigValueType
import com.nendo.argosy.data.emulator.config.EmulatorDataRoute
import com.nendo.argosy.ui.screens.settings.EmulatorDriverChoiceUi
import com.nendo.argosy.ui.screens.settings.EmulatorOptionUi
import com.nendo.argosy.ui.screens.settings.EmulatorOptionsFileUi
import com.nendo.argosy.ui.screens.settings.EmulatorOptionsPhase
import com.nendo.argosy.ui.screens.settings.EmulatorOptionsState
import com.nendo.argosy.ui.screens.settings.emulatorOptionId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmulatorOptionsRowsTest {

    private fun option(key: String) = EmulatorOptionUi(
        section = "Renderer",
        key = key,
        value = "0",
        isDefault = false,
        hidden = false,
        fileId = "config/config.ini",
        type = ConfigValueType.INT,
        token = "0",
        editable = true
    )

    private fun state(undoable: Set<String>, editRoute: EmulatorDataRoute? = EmulatorDataRoute.ROOT) = EmulatorOptionsState(
        emulatorId = "eden",
        packageName = "dev.eden.eden_emulator",
        phase = EmulatorOptionsPhase.READY,
        readRoute = EmulatorDataRoute.FILE_ACCESS,
        editRoute = editRoute,
        files = listOf(
            EmulatorOptionsFileUi(
                id = "config/config.ini",
                path = "/x/config/config.ini",
                state = ConfigFileState.READ,
                modifiedAt = 1L,
                options = listOf(option("a"), option("b")),
                otherKeys = emptyList(),
                playTimes = emptyList()
            )
        ),
        undoableIds = undoable
    )

    private fun undoKeys(state: EmulatorOptionsState): List<String> =
        emulatorOptionsRows(state).filterIsInstance<EmulatorOptionsRow.Undo>().map { it.option.key }

    private val a = emulatorOptionId("config/config.ini", "Renderer", "a")
    private val b = emulatorOptionId("config/config.ini", "Renderer", "b")

    @Test
    fun `an undoable option offers undo right under it`() {
        val rows = emulatorOptionsRows(state(setOf(b)))

        assertEquals(listOf("b"), undoKeys(state(setOf(b))))
        val undoIndex = rows.indexOfFirst { it is EmulatorOptionsRow.Undo }
        assertEquals("b", (rows[undoIndex - 1] as EmulatorOptionsRow.Option).option.key)
        assertEquals(listOf("a"), undoKeys(state(setOf(a))))
    }

    @Test
    fun `no undo row without an edit route`() {
        assertEquals(emptyList<String>(), undoKeys(state(setOf(a), editRoute = null)))
    }

    @Test
    fun `the driver option writes and undoes with app-data access alone`() {
        val driverOnly = state(setOf(a), editRoute = null).copy(
            driverOptionId = a,
            writeAppDataRoute = EmulatorDataRoute.FILE_ACCESS
        )

        assertTrue(driverOnly.canWrite(a))
        assertFalse(driverOnly.canWrite(b))
        assertEquals(listOf("a"), undoKeys(driverOnly))
        assertFalse(driverOnly.copy(writeAppDataRoute = null).canWrite(a))
    }

    @Test
    fun `the d-pad stops on options and actions, never on headers or summaries`() {
        val state = state(setOf(b))

        val focused = (0..emulatorOptionsMaxFocusIndex(state)).map { emulatorOptionsRowAtFocusIndex(it, state) }

        assertEquals(listOf("option_0_0", "option_0_1", "undo_0_1"), focused.map { it?.key })
        assertTrue(emulatorOptionsRows(state).any { it is EmulatorOptionsRow.Access && !it.isFocusable })
        assertTrue(emulatorOptionsRows(state).any { it is EmulatorOptionsRow.FileHeader && !it.isFocusable })
    }

    @Test
    fun `a blocked file with nothing to show stays listed but out of the rail`() {
        val blocked = EmulatorOptionsFileUi(
            id = "config/blocked.ini",
            path = "/x/config/blocked.ini",
            state = ConfigFileState.BLOCKED,
            modifiedAt = null,
            options = emptyList(),
            otherKeys = emptyList(),
            playTimes = emptyList()
        )
        val state = state(emptySet()).let { it.copy(files = it.files + blocked) }

        val header = emulatorOptionsRows(state).filterIsInstance<EmulatorOptionsRow.FileHeader>().last()
        val sections = emulatorOptionsSections(state, named = true)

        assertEquals("config/blocked.ini", header.file.id)
        assertEquals(null, header.section)
        assertEquals(listOf("config.ini"), sections.map { it.name })
        assertEquals(0, sections.single().focusStartIndex)
    }

    @Test
    fun `each file with options is its own rail entry`() {
        val second = state(emptySet()).files.single().copy(
            id = "config/other.ini",
            options = listOf(option("c").copy(fileId = "config/other.ini"))
        )
        val state = state(emptySet()).let { it.copy(files = it.files + second) }

        val sections = emulatorOptionsSections(state, named = true)

        assertEquals(listOf("config.ini", "other.ini"), sections.map { it.name })
        assertEquals(listOf(0, 2), sections.map { it.focusStartIndex })
    }

    @Test
    fun `driver values start at the system driver`() {
        val withDrivers = state(emptySet()).copy(
            drivers = listOf(EmulatorDriverChoiceUi("t", "Turnip", "/x/customDrivers/u1"))
        )

        assertEquals(listOf("", "/x/customDrivers/u1"), withDrivers.driverTokens)
    }
}
