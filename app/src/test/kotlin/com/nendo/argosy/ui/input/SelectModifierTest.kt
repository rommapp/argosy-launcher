package com.nendo.argosy.ui.input

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SelectModifierTest {

    private val combos = SelectModifier.comboMapFrom("quick_menu", "quick_settings")

    @Test
    fun `standalone Select is held on press and emits on release`() {
        val modifier = SelectModifier(combos)

        assertNull(modifier.filter(GamepadEvent.Select, KeyEvent.ACTION_DOWN))
        assertEquals(GamepadEvent.Select, modifier.filter(GamepadEvent.Select, KeyEvent.ACTION_UP))
    }

    @Test
    fun `Select then L1 emits the combo and swallows the Select release`() {
        val modifier = SelectModifier(combos)

        assertNull(modifier.filter(GamepadEvent.Select, KeyEvent.ACTION_DOWN))
        assertEquals(
            GamepadEvent.LeftStickClick,
            modifier.filter(GamepadEvent.PrevSection, KeyEvent.ACTION_DOWN)
        )
        assertNull(modifier.filter(GamepadEvent.PrevSection, KeyEvent.ACTION_UP))
        assertNull(modifier.filter(GamepadEvent.Select, KeyEvent.ACTION_UP))
    }

    @Test
    fun `Select then R1 emits the right combo`() {
        val modifier = SelectModifier(combos)

        modifier.filter(GamepadEvent.Select, KeyEvent.ACTION_DOWN)
        assertEquals(
            GamepadEvent.RightStickClick,
            modifier.filter(GamepadEvent.NextSection, KeyEvent.ACTION_DOWN)
        )
    }

    @Test
    fun `an unmapped button while Select is held passes through and keeps Select armed`() {
        val modifier = SelectModifier(combos)

        modifier.filter(GamepadEvent.Select, KeyEvent.ACTION_DOWN)
        assertEquals(GamepadEvent.Up, modifier.filter(GamepadEvent.Up, KeyEvent.ACTION_DOWN))
        assertEquals(GamepadEvent.Select, modifier.filter(GamepadEvent.Select, KeyEvent.ACTION_UP))
    }

    @Test
    fun `empty map passes Select through on press and emits nothing on release`() {
        val modifier = SelectModifier(emptyMap())

        assertEquals(GamepadEvent.Select, modifier.filter(GamepadEvent.Select, KeyEvent.ACTION_DOWN))
        assertNull(modifier.filter(GamepadEvent.Select, KeyEvent.ACTION_UP))
    }

    @Test
    fun `a stick-derived press with no release still emits`() {
        val modifier = SelectModifier(combos)

        assertEquals(GamepadEvent.Left, modifier.filter(GamepadEvent.Left, KeyEvent.ACTION_DOWN))
        assertEquals(GamepadEvent.Left, modifier.filter(GamepadEvent.Left, KeyEvent.ACTION_DOWN))
    }

    @Test
    fun `L1 without Select is a plain L1`() {
        val modifier = SelectModifier(combos)

        assertEquals(
            GamepadEvent.PrevSection,
            modifier.filter(GamepadEvent.PrevSection, KeyEvent.ACTION_DOWN)
        )
    }

    @Test
    fun `a release that was never pressed is not a Select`() {
        val modifier = SelectModifier(combos)

        assertNull(modifier.filter(GamepadEvent.Select, KeyEvent.ACTION_UP))
    }

    @Test
    fun `reset drops a held Select`() {
        val modifier = SelectModifier(combos)

        modifier.filter(GamepadEvent.Select, KeyEvent.ACTION_DOWN)
        modifier.reset()
        assertNull(modifier.filter(GamepadEvent.Select, KeyEvent.ACTION_UP))
    }

    @Test
    fun `a combo set to none leaves its button unmapped`() {
        val modifier = SelectModifier(SelectModifier.comboMapFrom("none", "quick_settings"))

        modifier.filter(GamepadEvent.Select, KeyEvent.ACTION_DOWN)
        assertEquals(
            GamepadEvent.PrevSection,
            modifier.filter(GamepadEvent.PrevSection, KeyEvent.ACTION_DOWN)
        )
    }
}
