package com.nendo.argosy.ui.components

import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DockGeometryTest {
    @Test
    fun `popup total touch window remains bounded when it nearly fills a tiny window`() {
        for (direction in LayoutDirection.entries) {
            val position = DockPopupPositionProvider(16).calculatePosition(
                IntRect(168, 248, 216, 296), IntSize(240, 320),
                direction, IntSize(192, 312)
            )
            assertTrue(position.y >= 0)
            assertTrue(position.y + 312 <= 320)
            assertTrue(position.x >= 0)
            assertTrue(position.x + 192 <= 240)
        }
    }

    @Test
    fun `tool focus wraps and skips unavailable actions`() {
        assertEquals(2, dockToolFocusMove(0, -1, listOf(true, true, true)))
        assertEquals(0, dockToolFocusMove(2, 1, listOf(true, true, true)))
        assertEquals(2, dockToolFocusMove(0, 1, listOf(true, false, true)))
        assertEquals(1, dockToolFocusMove(1, 1, listOf(false, true, false)))
        assertEquals(1, dockToolFocusMove(1, 1, listOf(false, false, false)))
        assertEquals(1, dockToolFocusMove(1, 1, emptyList()))
    }
}
