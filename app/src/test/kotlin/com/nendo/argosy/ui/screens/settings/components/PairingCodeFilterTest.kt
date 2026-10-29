package com.nendo.argosy.ui.screens.settings.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PairingCodeFilterTest {

    @Test
    fun `letters are upper-cased and separators dropped`() {
        assertEquals("AB12CD34", pairingCodeFilter("ab12-cd34"))
        assertEquals("AB12CD34", pairingCodeFilter("ab12 cd34"))
    }

    @Test
    fun `a full code is kept as typed`() {
        assertEquals("ABCD1234", pairingCodeFilter("ABCD1234"))
    }

    @Test
    fun `an empty field stays empty`() {
        assertEquals("", pairingCodeFilter(""))
    }

    @Test
    fun `a ninth character is rejected without touching the code`() {
        assertNull(pairingCodeFilter("ABCD12345"))
        assertEquals("ABCD1234", pairingCodeFilter("ABCD-1234-"))
    }
}
