package com.nendo.argosy.data.cache

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UserScreenshotFilesTest {

    @Test
    fun `the declared extension wins when it is a plain token`() {
        assertEquals("webp", userScreenshotExtension("webp", "image/png"))
        assertEquals("jpg", userScreenshotExtension(".JPG", null))
    }

    @Test
    fun `the content type names the extension when none is declared`() {
        assertEquals("jpg", userScreenshotExtension(null, "image/jpeg"))
        assertEquals("webp", userScreenshotExtension("", "image/webp; charset=binary"))
        assertEquals("png", userScreenshotExtension("../x", null))
    }

    @Test
    fun `a cached file is current only for the version it was written from`() {
        val stem = userScreenshotFileStem(42L, 9L, "2026-01-01T00:00:00Z")

        assertTrue(stem.startsWith("uss_42_9_"))
        assertTrue(isUserScreenshotFileCurrent("$stem.webp", 42L, 9L, "2026-01-01T00:00:00Z"))
        assertFalse(isUserScreenshotFileCurrent("$stem.webp", 42L, 9L, "2026-02-01T00:00:00Z"))
        assertFalse(isUserScreenshotFileCurrent("${stem}x.png", 42L, 9L, "2026-01-01T00:00:00Z"))
    }
}
