package com.nendo.argosy.ui.components

import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DockGeometryTest {
    @Test
    fun `group spacing doubles the visible app gap rather than the touch slot gap`() {
        for ((appIcon, controlIcon, gap) in listOf(
            Triple(24f, 12f, 4f),
            Triple(48f, 24f, 8f),
            Triple(72f, 36f, 12f)
        )) {
            val slot = maxOf(48f, appIcon)
            val layout = dockWidths(1000f, 3, slot, gap, gap * 2, appIcon, controlIcon)
            val visibleAppGap = gap + slot - appIcon
            val visibleGroupGap = (slot - controlIcon) / 2f + layout.groupGap +
                (slot - appIcon) / 2f

            assertEquals(visibleAppGap * 2f, visibleGroupGap, 0.01f)
        }
    }

    @Test
    fun `empty dock keeps only controls and their group gap`() {
        val layout = dockWidths(400f, 0, 48f, 8f, 16f, 48f, 24f)
        assertEquals(144f, layout.width, 0.01f)
        assertEquals(0f, layout.appsWidth, 0.01f)
    }

    @Test
    fun `one app hugs while many apps scroll with end controls intact`() {
        val one = dockWidths(400f, 1, 48f, 8f, 16f, 48f, 24f)
        assertEquals(184f, one.width, 0.01f)
        val many = dockWidths(400f, 20, 48f, 8f, 16f, 48f, 24f)
        assertEquals(400f, many.width, 0.01f)
        assertEquals(264f, many.appsWidth, 0.01f)
    }

    @Test
    fun `popup centers keyboard over caret and clamps near both edges`() {
        assertEquals(120, dockPopupLeft(200, 160, 480, 16))
        assertEquals(16, dockPopupLeft(30, 160, 480, 16))
        assertEquals(304, dockPopupLeft(460, 160, 480, 16))
        assertTrue(dockPopupLeft(20, 600, 480, 16) >= 0)
    }

    @Test
    fun `popup keeps scaled painted gap while transparent tail covers caret`() {
        for ((slot, gap, inset) in listOf(
            Triple(96, 8, 16),
            Triple(96, 16, 32),
            Triple(144, 24, 48)
        )) {
            val window = IntSize(1920, 1080)
            val toolsHeight = slot + gap * 2
            val dockTop = window.height - inset - toolsHeight
            val caretCenter = 1000
            val anchor = IntRect(
                caretCenter - slot / 2, dockTop + gap,
                caretCenter + slot / 2, dockTop + gap + slot
            )
            val popupWidth = slot * 3 + gap * 2 + inset * 2
            for (paintedHeight in listOf(toolsHeight, toolsHeight * 2 + gap)) {
                val popupHeight = paintedHeight + slot + gap * 2
                for (direction in LayoutDirection.entries) {
                    val position = DockPopupPositionProvider(inset).calculatePosition(
                        anchor, window, direction, IntSize(popupWidth, popupHeight)
                    )
                    assertEquals(gap, dockTop - position.y - paintedHeight)
                    assertEquals(caretCenter, position.x + popupWidth / 2)
                    assertEquals(anchor.bottom, position.y + popupHeight)
                    assertTrue(position.y + paintedHeight <= anchor.top)
                }
            }
        }
    }

    @Test
    fun `popup keeps window clamps when there is no room above padded dock anchor`() {
        val position = DockPopupPositionProvider(16).calculatePosition(
            IntRect(432, 24, 480, 72), IntSize(480, 320),
            LayoutDirection.Ltr, IntSize(192, 160 + 48 + 8 * 2)
        )
        assertEquals(16, position.y)
        assertEquals(480 - 16 - 192, position.x)
    }

    @Test
    fun `popup tail covers caret after horizontal clamp in either direction`() {
        for (caretCenter in listOf(40, 440)) {
            for (direction in LayoutDirection.entries) {
                val anchor = IntRect(caretCenter - 24, 248, caretCenter + 24, 296)
                val position = DockPopupPositionProvider(16).calculatePosition(
                    anchor, IntSize(480, 320), direction, IntSize(192, 128)
                )
                assertTrue(position.x <= anchor.left)
                assertTrue(position.x + 192 >= anchor.right)
                assertEquals(anchor.bottom, position.y + 128)
                assertEquals(16, anchor.top - position.y - 64)
            }
        }
    }

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
    fun `compact dock preserves touch slots and visible gaps by reducing outer padding`() {
        val compact = dockWidths(168f, 20, 48f, 8f, 16f, 48f, 24f)
        assertEquals(168f, compact.width, 0.01f)
        assertEquals(48f, compact.appsWidth, 0.01f)
        assertEquals(8f, compact.padding, 0.01f)
        assertEquals(4f, compact.groupGap, 0.01f)
        val minimum = dockWidths(96f, 0, 48f, 8f, 16f, 48f, 24f)
        assertEquals(96f, minimum.width, 0.01f)
        assertEquals(0f, minimum.padding, 0.01f)
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
