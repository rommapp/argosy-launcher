package com.nendo.argosy.ui.input

import com.nendo.argosy.ui.components.InputButton
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ButtonGlyphsTest {

    private val none = ButtonGlyphSwaps(ab = false, xy = false, startSelect = false)

    private val all = listOf(false, true).flatMap { ab ->
        listOf(false, true).flatMap { xy ->
            listOf(false, true).map { startSelect -> ButtonGlyphSwaps(ab, xy, startSelect) }
        }
    }

    @Test
    fun `xbox layout puts A at the bottom and Y at the top`() {
        assertEquals(ControllerPosition.BOTTOM, InputButton.A.positionUnder(none))
        assertEquals(ControllerPosition.RIGHT, InputButton.B.positionUnder(none))
        assertEquals(ControllerPosition.LEFT, InputButton.X.positionUnder(none))
        assertEquals(ControllerPosition.TOP, InputButton.Y.positionUnder(none))
        assertEquals(ControllerPosition.MENU_RIGHT, InputButton.START.positionUnder(none))
        assertEquals(ControllerPosition.MENU_LEFT, InputButton.SELECT.positionUnder(none))
    }

    @Test
    fun `nintendo layout puts A on the right and X at the top`() {
        val nintendo = ButtonGlyphSwaps(ab = true, xy = true, startSelect = false)
        assertEquals(ControllerPosition.RIGHT, InputButton.A.positionUnder(nintendo))
        assertEquals(ControllerPosition.BOTTOM, InputButton.B.positionUnder(nintendo))
        assertEquals(ControllerPosition.TOP, InputButton.X.positionUnder(nintendo))
        assertEquals(ControllerPosition.LEFT, InputButton.Y.positionUnder(nintendo))
    }

    @Test
    fun `swapping start and select trades the menu pills`() {
        val swapped = none.copy(startSelect = true)
        assertEquals(ControllerPosition.MENU_LEFT, InputButton.START.positionUnder(swapped))
        assertEquals(ControllerPosition.MENU_RIGHT, InputButton.SELECT.positionUnder(swapped))
    }

    @Test
    fun `the diagram reads the inverse of the glyph mapping under every swap`() {
        all.forEach { swaps ->
            ControllerPosition.entries.forEach { position ->
                assertEquals("$position under $swaps", position, position.intentButton(swaps).positionUnder(swaps))
            }
        }
    }

    @Test
    fun `buttons without a face or menu position have none`() {
        assertNull(InputButton.DPAD.positionUnder(none))
        assertNull(InputButton.LB.positionUnder(none))
    }
}
