package com.nendo.argosy.ui.components

import com.nendo.argosy.domain.model.StickerOutlineWidth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class StickerOutlineCacheKeyTest {

    private val path = "/storage/emulated/0/Pictures/mascot.png"

    @Test
    fun `the same file and width give the same key`() {
        assertEquals(
            stickerOutlineCacheKey(path, 1_000L, 2_048L, 0.03f),
            stickerOutlineCacheKey(path, 1_000L, 2_048L, 0.03f)
        )
    }

    @Test
    fun `a rewritten file gets a new key`() {
        assertNotEquals(
            stickerOutlineCacheKey(path, 1_000L, 2_048L, 0.03f),
            stickerOutlineCacheKey(path, 2_000L, 2_048L, 0.03f)
        )
        assertNotEquals(
            stickerOutlineCacheKey(path, 1_000L, 2_048L, 0.03f),
            stickerOutlineCacheKey(path, 1_000L, 4_096L, 0.03f)
        )
    }

    @Test
    fun `another file at the same size and time gets a new key`() {
        assertNotEquals(
            stickerOutlineCacheKey(path, 1_000L, 2_048L, 0.03f),
            stickerOutlineCacheKey("/storage/emulated/0/Pictures/other.png", 1_000L, 2_048L, 0.03f)
        )
    }

    @Test
    fun `every outline width traces its own mask`() {
        val keys = StickerOutlineWidth.entries.map { stickerOutlineCacheKey(path, 1_000L, 2_048L, it.ratio) }

        assertEquals(keys.size, keys.toSet().size)
    }

    @Test
    fun `the key is a fixed-length file name`() {
        val key = stickerOutlineCacheKey(path, 1_000L, 2_048L, 0.05f)

        assertEquals(32, key.length)
        assertEquals(key, key.filter { it in '0'..'9' || it in 'a'..'f' })
    }
}
