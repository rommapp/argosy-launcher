package com.nendo.argosy.data.download.transfer

import org.junit.Assert.assertEquals
import org.junit.Test

class ExpectedTransferContentTest {
    @Test fun canonicalPico8AliasesAllowPng() {
        for (slug in listOf("pico8", "pico-8", "pico_8", "PICO-8", "PICO8")) {
            assertEquals(ExpectedTransferContent.PICO8_PNG,
                ExpectedTransferContent.forDownload("CART.PNG", slug, false))
        }
        assertEquals(ExpectedTransferContent.PICO8_PNG,
            ExpectedTransferContent.forDownload("CART.P8.PNG", "unknown", false))
    }

    @Test fun ordinaryImagesArchivesAndMultifileRemainClosed() {
        for ((name, slug, multi) in listOf(
            Triple("game.nes", "nes", false),
            Triple("cart.png", "nes", false),
            Triple("cart.png", "tic80", false),
            Triple("cart.p8.png.zip", "pico8", false),
            Triple("cart.ZIP", "pico8", false),
            Triple("cart.p8.png", "pico8", true),
            Triple("cart.png", "pico-8", true)
        )) {
            assertEquals(ExpectedTransferContent.ROM, ExpectedTransferContent.forDownload(name, slug, multi))
        }
    }
}
