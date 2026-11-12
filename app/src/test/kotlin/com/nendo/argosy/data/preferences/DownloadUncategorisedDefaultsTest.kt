package com.nendo.argosy.data.preferences

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadUncategorisedDefaultsTest {

    private val otherOff = DownloadDefaults.FACTORY

    @Test
    fun `a windows game keeps its nested folders with Other off`() {
        assertTrue(DownloadDefaults.includesUncategorised(otherOff, "windows"))
    }

    @Test
    fun `the raw RomM win slug counts as windows`() {
        assertTrue(DownloadDefaults.includesUncategorised(otherOff, "win"))
    }

    @Test
    fun `dos and scummvm games keep their nested folders`() {
        assertTrue(DownloadDefaults.includesUncategorised(otherOff, "dos"))
        assertTrue(DownloadDefaults.includesUncategorised(otherOff, "scummvm"))
    }

    @Test
    fun `a rom platform leaves uncategorised extras out while Other is off`() {
        assertFalse(DownloadDefaults.includesUncategorised(otherOff, "snes"))
    }

    @Test
    fun `a rom platform takes uncategorised extras once Other is on`() {
        val otherOn = otherOff + (DownloadDefaults.OTHER_KEY to true)
        assertTrue(DownloadDefaults.includesUncategorised(otherOn, "snes"))
    }
}
