package com.nendo.argosy.ui.common

import com.nendo.argosy.ui.components.HudCorner
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The stored value moved from the English label to the enum name. Devices upgrading from an
 * earlier build, and settings backups those builds exported, still hold the old labels, so
 * reading has to accept both for as long as any of those files exist.
 */
class HudCornerUiTest {

    @Test
    fun `tokens read back as themselves`() {
        HudCorner.entries.forEach { corner ->
            assertEquals(corner, hudCornerFromStored(corner.name))
        }
    }

    @Test
    fun `labels written by earlier builds still read`() {
        assertEquals(HudCorner.TOP_LEFT, hudCornerFromStored("Top Left"))
        assertEquals(HudCorner.TOP_RIGHT, hudCornerFromStored("Top Right"))
        assertEquals(HudCorner.BOTTOM_LEFT, hudCornerFromStored("Bottom Left"))
        assertEquals(HudCorner.BOTTOM_RIGHT, hudCornerFromStored("Bottom Right"))
    }

    @Test
    fun `unknown and missing values fall back rather than throw`() {
        assertEquals(HudCorner.TOP_LEFT, hudCornerFromStored(null))
        assertEquals(HudCorner.TOP_LEFT, hudCornerFromStored(""))
        assertEquals(HudCorner.TOP_LEFT, hudCornerFromStored("Haut Gauche"))
    }

    @Test
    fun `every corner has a distinct legacy label`() {
        val labels = HudCorner.entries.map { it.legacyLabel }
        assertEquals(labels.size, labels.toSet().size)
    }
}
