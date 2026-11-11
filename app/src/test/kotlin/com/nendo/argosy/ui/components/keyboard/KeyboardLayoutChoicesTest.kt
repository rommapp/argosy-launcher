package com.nendo.argosy.ui.components.keyboard

import org.junit.Assert.assertEquals
import org.junit.Test

class KeyboardLayoutChoicesTest {

    private fun ids(choices: List<KeyboardLayoutChoice>) = choices.map { it.layoutId }

    private val builtInOrder = listOf(
        KeyboardLayout.QWERTY_LAYOUT_ID,
        KeyboardLayout.AZERTY_LAYOUT_ID,
        KeyboardLayout.QWERTZ_LAYOUT_ID,
        KeyboardLayout.JCUKEN_LAYOUT_ID,
        KeyboardLayout.HANGUL_LAYOUT_ID
    )

    @Test
    fun `enabled languages lead in system order and every built-in layout follows once`() {
        val choices = keyboardLayoutChoices(listOf("fr-FR", "de-DE", "fr-CA"), fallbackTag = "en")
        assertEquals(
            listOf(
                KeyboardLayout.AZERTY_LAYOUT_ID,
                KeyboardLayout.QWERTZ_LAYOUT_ID,
                KeyboardLayout.QWERTY_LAYOUT_ID,
                KeyboardLayout.JCUKEN_LAYOUT_ID,
                KeyboardLayout.HANGUL_LAYOUT_ID
            ),
            ids(choices)
        )
        assertEquals("fr-FR", choices.first().languageTag)
        assertEquals("en", choices[2].languageTag)
    }

    @Test
    fun `latin languages share qwerty and keep the first tag for its label`() {
        val choices = keyboardLayoutChoices(listOf("es-ES", "en-US"), fallbackTag = "en")
        assertEquals(builtInOrder, ids(choices))
        assertEquals("es-ES", choices.first().languageTag)
    }

    @Test
    fun `a language with no argosy layout adds the system keyboard entry last when the field allows it`() {
        val tags = listOf("ja-JP", "en-US", "ko-KR")
        val argosyOrder = listOf(
            KeyboardLayout.QWERTY_LAYOUT_ID,
            KeyboardLayout.HANGUL_LAYOUT_ID,
            KeyboardLayout.AZERTY_LAYOUT_ID,
            KeyboardLayout.QWERTZ_LAYOUT_ID,
            KeyboardLayout.JCUKEN_LAYOUT_ID
        )
        assertEquals(
            argosyOrder + SYSTEM_KEYBOARD_CHOICE_ID,
            ids(keyboardLayoutChoices(tags, fallbackTag = "en", systemKeyboardAvailable = true))
        )
        assertEquals(
            argosyOrder,
            ids(keyboardLayoutChoices(tags, fallbackTag = "en", systemKeyboardAvailable = false))
        )
    }

    @Test
    fun `no enabled subtypes still offers every built-in layout starting with qwerty`() {
        assertEquals(builtInOrder, ids(keyboardLayoutChoices(emptyList(), "en")))
    }
}
