package com.nendo.argosy.ui.components.playtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SquarifyTest {

    private val bounds = TreemapRect(0f, 0f, 600f, 400f)
    private val epsilon = 0.5f

    private fun overlaps(a: TreemapRect, b: TreemapRect): Boolean =
        a.x < b.right - epsilon && b.x < a.right - epsilon &&
            a.y < b.bottom - epsilon && b.y < a.bottom - epsilon

    @Test
    fun `tile areas sum to the bounds and stay proportional to the inputs`() {
        val areas = listOf(6f, 6f, 4f, 3f, 2f, 2f, 1f)
        val rects = Squarify.layout(areas, bounds)
        assertEquals(bounds.area, rects.sumOf { it.area.toDouble() }.toFloat(), 1f)
        val scale = bounds.area / areas.sum()
        areas.forEachIndexed { index, area ->
            assertEquals(area * scale, rects[index].area, 1f)
        }
    }

    @Test
    fun `tiles never overlap and stay inside the bounds`() {
        val areas = listOf(50f, 30f, 12f, 8f, 5f, 3f, 2f, 1f, 1f)
        val rects = Squarify.layout(areas, bounds)
        rects.forEach { rect ->
            assertTrue(rect.x >= bounds.x - epsilon && rect.y >= bounds.y - epsilon)
            assertTrue(rect.right <= bounds.right + epsilon && rect.bottom <= bounds.bottom + epsilon)
        }
        for (i in rects.indices) {
            for (j in i + 1 until rects.size) {
                assertTrue("tiles $i and $j overlap", !overlaps(rects[i], rects[j]))
            }
        }
    }

    @Test
    fun `output keeps the input order and the largest input gets the largest tile`() {
        val areas = listOf(10f, 5f, 2f, 1f)
        val rects = Squarify.layout(areas, bounds)
        assertEquals(areas.size, rects.size)
        assertEquals(0, rects.indices.maxByOrNull { rects[it].area })
        assertEquals(areas.lastIndex, rects.indices.minByOrNull { rects[it].area })
        assertTrue(rects.zipWithNext().all { (a, b) -> a.area >= b.area - epsilon })
    }

    @Test
    fun `a single tile fills the bounds`() {
        val rects = Squarify.layout(listOf(7f), bounds)
        assertEquals(bounds, rects.single())
    }

    @Test
    fun `non-positive inputs get empty tiles and do not take space`() {
        val rects = Squarify.layout(listOf(3f, 0f, 1f), bounds)
        assertEquals(0f, rects[1].area, 0f)
        assertEquals(bounds.area, rects[0].area + rects[2].area, 1f)
    }

    @Test
    fun `no inputs give no tiles and zero total gives empty tiles`() {
        assertTrue(Squarify.layout(emptyList(), bounds).isEmpty())
        Squarify.layout(listOf(0f, 0f), bounds).forEach { assertEquals(0f, it.area, 0f) }
    }
}
