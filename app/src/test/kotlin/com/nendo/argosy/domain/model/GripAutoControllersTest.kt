package com.nendo.argosy.domain.model

import com.nendo.argosy.data.preferences.GripReserveMode
import com.nendo.argosy.ui.theme.isGripAutoControllerConnected
import com.nendo.argosy.ui.theme.isGripReserveActive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val PAD_A = "1118:2835:abc"
private const val PAD_B = "1356:3302:def"

class GripAutoControllersTest {

    @Test
    fun `a stored list survives a round trip through json`() {
        val controllers = GripAutoControllers()
            .with(PAD_A, "Xbox Wireless Controller")
            .with(PAD_B, "8BitDo Pro 2")

        val restored = GripAutoControllers.fromJson(controllers.toJson())

        assertEquals(controllers.controllers, restored.controllers)
    }

    @Test
    fun `adding the same controller twice updates its name instead of duplicating it`() {
        val controllers = GripAutoControllers()
            .with(PAD_A, "Old Name")
            .with(PAD_A, "New Name")

        assertEquals(1, controllers.controllers.size)
        assertEquals("New Name", controllers.controllers.first().name)
    }

    @Test
    fun `removing a controller leaves the others`() {
        val controllers = GripAutoControllers()
            .with(PAD_A, "Pad A")
            .with(PAD_B, "Pad B")
            .without(PAD_A)

        assertEquals(listOf(PAD_B), controllers.controllers.map { it.controllerId })
    }

    @Test
    fun `an empty id identifies nothing and is not stored`() {
        val controllers = GripAutoControllers().with("", "Nameless")

        assertTrue(controllers.controllers.isEmpty())
    }

    @Test
    fun `malformed stored json yields an empty list rather than throwing`() {
        assertTrue(GripAutoControllers.fromJson("not json at all").controllers.isEmpty())
        assertTrue(GripAutoControllers.fromJson(null).controllers.isEmpty())
        assertTrue(GripAutoControllers.fromJson("").controllers.isEmpty())
    }

    @Test
    fun `an entry with no id is dropped but one with no name is kept`() {
        val restored = GripAutoControllers.fromJson(
            """[{"name":"Orphan"},{"controllerId":"$PAD_A"}]"""
        )

        assertEquals(listOf(PAD_A), restored.controllers.map { it.controllerId })
        assertEquals("Controller", restored.controllers.first().name)
    }

    @Test
    fun `the reserve switches on when any listed controller is connected`() {
        val ids = GripAutoControllers().with(PAD_A, "Pad A").with(PAD_B, "Pad B").controllerIds

        assertTrue(isGripAutoControllerConnected(true, ids, setOf(PAD_B)))
        assertFalse(isGripAutoControllerConnected(true, ids, setOf("9999:1:other")))
    }

    @Test
    fun `switching auto off ignores a connected controller but keeps the list`() {
        val controllers = GripAutoControllers().with(PAD_A, "Pad A")

        assertFalse(isGripAutoControllerConnected(false, controllers.controllerIds, setOf(PAD_A)))
        assertEquals(1, controllers.controllers.size)
    }

    @Test
    fun `choosing no controllers never switches the reserve on by itself`() {
        val ids = GripAutoControllers().controllerIds

        assertFalse(isGripAutoControllerConnected(true, ids, setOf(PAD_A)))
    }

    @Test
    fun `off stays off and on stays on whatever is connected`() {
        assertFalse(isGripReserveActive(GripReserveMode.OFF, autoControllerConnected = true))
        assertFalse(isGripReserveActive(GripReserveMode.OFF, autoControllerConnected = false))
        assertTrue(isGripReserveActive(GripReserveMode.ON, autoControllerConnected = false))
        assertTrue(isGripReserveActive(GripReserveMode.ON, autoControllerConnected = true))
    }

    @Test
    fun `auto follows whether a chosen controller is connected`() {
        assertTrue(isGripReserveActive(GripReserveMode.AUTO, autoControllerConnected = true))
        assertFalse(isGripReserveActive(GripReserveMode.AUTO, autoControllerConnected = false))
    }

    @Test
    fun `an unknown stored mode reads as off rather than throwing`() {
        assertEquals(GripReserveMode.OFF, GripReserveMode.fromString("SOMETHING_ELSE"))
        assertEquals(GripReserveMode.OFF, GripReserveMode.fromString(null))
        assertEquals(GripReserveMode.AUTO, GripReserveMode.fromString("AUTO"))
    }
}
