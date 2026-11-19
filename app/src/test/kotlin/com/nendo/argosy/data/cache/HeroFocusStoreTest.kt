package com.nendo.argosy.data.cache

import com.nendo.argosy.data.model.HeroFocus
import com.nendo.argosy.data.model.HeroFocusSpec
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

class HeroFocusStoreTest {

    private val dir = createTempDirectory("hero_focus").toFile()
    private val storeFile = dir.resolve("hero_focus.tsv")
    private val spec = HeroFocusSpec(narrowAspect = 0.45f, wideAspect = 0.9f, maxZoom = 1.15f, analysisSize = 128)

    private class CountingSource : HeroPixelSource {
        var decodes = 0

        override fun aspect(file: File): Float = 128f / 40f

        override fun pixels(file: File, longSide: Int): HeroPixels {
            decodes++
            val width = 128
            val height = 40
            val pixels = IntArray(width * height) { 0xFF5A6470.toInt() }
            var seed = 7
            for (y in 14 until 26) {
                for (x in 96 until 108) {
                    seed = seed * 1103515245 + 12345
                    val level = (seed ushr 16) and 0xFF
                    pixels[y * width + x] = (0xFF shl 24) or (level shl 16) or (level shl 8) or level
                }
            }
            return HeroPixels(pixels, width, height)
        }
    }

    private fun image(name: String, lastModified: Long): File =
        dir.resolve(name).apply {
            writeBytes(ByteArray(2048) { it.toByte() })
            setLastModified(lastModified)
        }

    @Test
    fun `an unchanged file is analysed once`() = runBlocking {
        val source = CountingSource()
        val store = HeroFocusStore(storeFile, source)
        val art = image("bg_g1_a.jpg", 1_000_000L)

        val first = store.resolve(art.path, spec)
        val second = store.resolve(art.path, spec)

        assertEquals(1, source.decodes)
        assertEquals(first, second)
        assertEquals(first, store.peek(art.path, spec))
    }

    @Test
    fun `a replaced file with a new modified time is analysed again`() = runBlocking {
        val source = CountingSource()
        val store = HeroFocusStore(storeFile, source)
        val art = image("bg_g1_b.jpg", 1_000_000L)
        store.resolve(art.path, spec)

        art.setLastModified(2_000_000L)
        store.resolve(art.path, spec)

        assertEquals(2, source.decodes)
    }

    @Test
    fun `a new store reads the saved focus back without analysing`() = runBlocking {
        val art = image("bg_g1_c.jpg", 1_000_000L)
        val saved = HeroFocusStore(storeFile, CountingSource()).resolve(art.path, spec)
        assertNotEquals(HeroFocus.CENTERED, saved)

        val source = CountingSource()
        val reloaded = HeroFocusStore(storeFile, source).resolve(art.path, spec)

        assertEquals(saved, reloaded)
        assertEquals(0, source.decodes)
    }

    @Test
    fun `a different column layout is analysed separately`() = runBlocking {
        val source = CountingSource()
        val store = HeroFocusStore(storeFile, source)
        val art = image("bg_g1_d.jpg", 1_000_000L)
        store.resolve(art.path, spec)

        store.resolve(art.path, spec.copy(narrowAspect = 0.3f, wideAspect = 0.6f))

        assertEquals(2, source.decodes)
    }

    @Test
    fun `precompute waits for a layout and then fills the store`() = runBlocking {
        val source = CountingSource()
        val store = HeroFocusStore(storeFile, source)
        val art = image("bg_g1_e.jpg", 1_000_000L)
        val shown = image("bg_g1_f.jpg", 1_000_000L)

        store.precompute(art, 1280, 400) { longSide -> source.pixels(art, longSide) }
        assertNull(store.peek(art.path, spec))

        store.resolve(shown.path, spec)
        store.precompute(art, 1280, 400) { longSide -> source.pixels(art, longSide) }
        store.resolve(art.path, spec)

        assertEquals(2, source.decodes)
        assertNotEquals(HeroFocus.CENTERED, store.peek(art.path, spec))
    }
}
