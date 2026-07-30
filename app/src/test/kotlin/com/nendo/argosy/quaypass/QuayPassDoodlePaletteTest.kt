package com.nendo.argosy.quaypass

import com.nendo.argosy.data.quaypass.ble.QuayPassDoodleCodec
import com.nendo.argosy.ui.screens.doodle.DoodleColor
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins the cross-layer invariant the sparse decode relies on: the data-layer
 * PALETTE_RGB must stay byte-identical, index for index, to the ui DoodleColor
 * enum. If they drift, sparse doodles silently decode to the wrong colours.
 */
class QuayPassDoodlePaletteTest {

    @Test
    fun `palette rgb matches DoodleColor index for index`() {
        assertEquals(
            "palette size must match the DoodleColor count",
            DoodleColor.entries.size,
            QuayPassDoodleCodec.PALETTE_RGB.size
        )
        DoodleColor.entries.forEach { color ->
            val rgb = (color.hex and 0xFFFFFF).toInt()
            assertEquals(
                "DoodleColor ${color.name} (index ${color.index}) must equal PALETTE_RGB[${color.index}]",
                rgb,
                QuayPassDoodleCodec.PALETTE_RGB[color.index]
            )
        }
    }
}
