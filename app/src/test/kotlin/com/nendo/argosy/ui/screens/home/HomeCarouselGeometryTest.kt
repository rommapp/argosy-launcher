package com.nendo.argosy.ui.screens.home

import androidx.compose.ui.unit.LayoutDirection
import com.nendo.argosy.domain.model.HomeRowAlignment
import com.nendo.argosy.ui.components.carouselNeighbourTranslation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class HomeCarouselGeometryTest {
    @Test
    fun `neighbour translations preserve visible gap for each layout direction and reverse order`() {
        val directions = listOf(
            Triple(LayoutDirection.Ltr, false, 1f),
            Triple(LayoutDirection.Ltr, true, -1f),
            Triple(LayoutDirection.Rtl, false, -1f),
            Triple(LayoutDirection.Rtl, true, 1f)
        )
        val cardWidth = 230f
        val gap = cardWidth * 0.13f
        for ((layoutDirection, reversed, nextItemDirection) in directions) {
            for (scale in listOf(1f, 1.5f, 2f)) {
                val push = cardWidth * (scale - 1f) / 2f
                for (indexOffset in listOf(-1, 1)) {
                    val translation = carouselNeighbourTranslation(
                        3 + indexOffset, 3, push, reversed, layoutDirection
                    )
                    val restingCenter = indexOffset * nextItemDirection * (cardWidth + gap)
                    val nearestEdge = abs(restingCenter + translation) - cardWidth / 2f
                    val focusedEdge = cardWidth * scale / 2f
                    assertEquals(gap, nearestEdge - focusedEdge, 0.001f)
                    assertEquals(push, abs(translation), 0f)
                }
                assertEquals(0f, carouselNeighbourTranslation(3, 3, push, reversed, layoutDirection), 0f)
            }
        }
    }

    @Test
    fun `disabled neighbour push stays zero for every direction`() {
        for (layoutDirection in LayoutDirection.entries) {
            for (reversed in listOf(false, true)) {
                for (index in 2..4) {
                    assertEquals(0f, carouselNeighbourTranslation(index, 3, 0f, reversed, layoutDirection), 0f)
                }
            }
        }
    }

    @Test
    fun `short wide viewport chooses larger feasible above allocation over degenerate side art`() {
        val result = homeCarouselGeometry(
            800f, 360f, 100f, 64f, 24f, 16f, 98f,
            aboveTitleReserve = 84f,
            aspectRatio = 0.75f, restingScale = 0.5f,
            rowAlignment = HomeRowAlignment.BOTTOM, centeredFocus = false, mirrored = false,
            badgeOverflow = 20f, dampingRatio = 0.6f
        )
        val sideHeight = 172f - 98f - 32f - 40f
        assertTrue(result.titleAbove)
        assertTrue(result.cardHeight > sideHeight)
        assertEquals(72f / interruptedSpringMaximum(1f, 2f, 0.6f) - 20f, result.cardHeight, 0.001f)
        assertTrue(result.titleCenterY + 42f + 16f <= result.railTop)
        assertTrue(result.railTop + result.railHeight <= 272f)
    }

    private fun geometry(
        width: Float = 800f,
        height: Float = 360f,
        reserve: Float = 96f,
        aspect: Float = 2f / 3f,
        centered: Boolean = false,
        mirrored: Boolean = false,
        alignment: HomeRowAlignment = HomeRowAlignment.BOTTOM,
        restingScale: Float = 0.5f,
        minimumRestingScaleSeen: Float = restingScale,
        showTitle: Boolean = true
    ) = homeCarouselGeometry(
        width, height, 64f, 48f, 16f, 16f, reserve,
        aspectRatio = aspect,
        restingScale = restingScale,
        springMinimumRestingScale = minimumRestingScaleSeen,
        rowAlignment = alignment,
        centeredFocus = centered,
        mirrored = mirrored,
        badgeOverflow = 8f,
        dampingRatio = 0.6f,
        showTitle = showTitle
    )

    @Test
    fun `square and portrait always center above the rail`() {
        for ((width, height) in listOf(600f to 600f, 360f to 800f, 412f to 915f)) {
            for (alignment in HomeRowAlignment.entries) {
                val result = geometry(width, height, alignment = alignment)
                assertTrue(result.titleAbove)
                assertEquals(width / 2f, result.titleCenterX)
                assertTrue(result.titleCenterY + 48f + 16f <= result.railTop)
            }
        }
    }

    @Test
    fun `square above band begins immediately below measured navigation`() {
        val result = geometry(600f, 600f)
        assertEquals(64f + 96f / 2f, result.titleCenterY, 0f)
    }

    @Test
    fun `side composition mirrors both anchors and width without changing allocation`() {
        val normal = geometry(1200f, 800f, aspect = 3f)
        val mirrored = geometry(1200f, 800f, aspect = 3f, mirrored = true)
        assertFalse(normal.titleAbove)
        assertEquals(1200f, normal.titleCenterX + mirrored.titleCenterX, 0.001f)
        assertEquals(normal.titleCenterY, mirrored.titleCenterY)
        assertEquals(normal.titleMaxWidth, mirrored.titleMaxWidth)
        assertEquals(normal.cardHeight, mirrored.cardHeight)
    }

    @Test
    fun `normal wide original artwork uses side title without charging metadata against card size`() {
        for (aspect in listOf(2f / 3f, 3f / 4f)) {
            fun resolve(reserve: Float) = homeCarouselGeometry(
                960f, 540f, 72f, 64f, 24f, 16f, reserve,
                aspectRatio = aspect, restingScale = 0.5f,
                rowAlignment = HomeRowAlignment.BOTTOM, centeredFocus = false, mirrored = false,
                badgeOverflow = 20f, dampingRatio = 0.6f
            )
            val standard = resolve(84f)
            val fullMetadata = resolve(124f)
            assertFalse(standard.titleAbove)
            assertFalse(fullMetadata.titleAbove)
            assertEquals(standard.cardHeight, fullMetadata.cardHeight, 0f)
            assertEquals(480f, standard.titleMaxWidth, 0f)
            assertTrue(standard.cardHeight * 4f > 600f)
        }
    }

    @Test
    fun `scaled chrome fits the side gap before resorting to a smaller above rail`() {
        fun resolve() = homeCarouselGeometry(
            960f, 540f, 150f, 77.5f, 36f, 24f, 102f,
            aspectRatio = 3f / 4f, restingScale = 0.5f,
            rowAlignment = HomeRowAlignment.BOTTOM, centeredFocus = false, mirrored = false,
            badgeOverflow = 20f, dampingRatio = 0.6f, scrollAnchorOffset = 12.5f
        )
        val idle = resolve()
        assertFalse(idle.titleAbove)
        assertEquals(86.5f, idle.cardHeight, 0.001f)
        assertTrue(idle.cardHeight * 4f > 300f)
        assertEquals(idle, resolve())
    }

    @Test
    fun `centered focus always uses above band even with a safe side region`() {
        assertTrue(geometry(1200f, 800f, aspect = 3f, centered = true).titleAbove)
    }

    @Test
    fun `zero width and exhausted content yield finite nonnegative card allocation`() {
        for ((width, height, reserve) in listOf(
            Triple(0f, 0f, 0f), Triple(360f, 240f, 300f), Triple(800f, 360f, 480f)
        )) {
            val result = geometry(width, height, reserve)
            assertTrue(result.cardWidth.isFinite() && result.cardWidth >= 0f)
            assertTrue(result.cardHeight.isFinite() && result.cardHeight >= 0f)
            assertTrue(result.railHeight.isFinite() && result.railHeight >= 0f)
            assertTrue(result.titleMaxWidth.isFinite() && result.titleMaxWidth >= 0f)
        }
    }

    @Test
    fun `insufficient height does not silently cap the measured title reserve`() {
        val result = geometry(800f, 360f, reserve = 300f)
        assertEquals(64f + 300f / 2f, result.titleCenterY, 0.001f)
        assertEquals(0f, result.cardHeight, 0f)
        assertEquals(64f + 300f + 16f, result.railTop, 0.001f)
    }

    @Test
    fun `metadata scale changes reduce artwork without violating aspect or envelope`() {
        for (reserve in listOf(48f, 96f, 144f, 192f)) {
            val result = geometry(412f, 915f, reserve)
            val maximumScale = interruptedSpringMaximum(1f, 2f, 0.6f)
            assertEquals(2f / 3f, result.cardWidth / result.cardHeight, 0.001f)
            assertTrue(result.cardHeight * maximumScale <= result.railHeight + 0.001f)
            assertTrue(result.railTop + result.railHeight <= 915f - 48f - 16f + 0.001f)
        }
    }

    @Test
    fun `presentation removes title reserve and leaves room for artwork`() {
        val single = geometry(360f, 480f)
        val dual = geometry(360f, 480f, showTitle = false)
        assertFalse(dual.titleAbove)
        assertTrue(dual.cardHeight > single.cardHeight)
        assertEquals(dual, geometry(360f, 480f, reserve = 400f, showTitle = false))
    }

    @Test
    fun `presentation keeps the artwork width cap when both modes already reach it`() {
        val single = geometry(360f, 800f)
        val dual = geometry(360f, 800f, showTitle = false)
        assertEquals(single.cardHeight, dual.cardHeight, 0f)
        assertTrue(dual.railTop < single.railTop)
    }

    @Test
    fun `settings change keeps the previous spring scale and velocity envelope`() {
        val before = geometry(480f, 800f, restingScale = 0.5f)
        val after = geometry(480f, 800f, restingScale = 1f, minimumRestingScaleSeen = 0.5f)
        val maximumScale = interruptedSpringMaximum(1f, 2f, 0.6f)
        assertEquals(before.cardHeight, after.cardHeight, 0.001f)
        assertTrue(after.cardHeight * maximumScale <= after.railHeight + 0.001f)
        assertTrue(after.titleCenterY + 48f + 16f <= after.railTop)
        assertTrue(after.railTop + after.railHeight <= 800f - 48f - 16f + 0.001f)
    }

    @Test
    fun `settled receipt releases old settings and restores current artwork allocation`() {
        val tracker = com.nendo.argosy.ui.components.CarouselScaleAnimationTracker()
        tracker.register("focused")
        tracker.report("focused", 1f, true)
        val pending = geometry(800f, 480f, restingScale = 1f, minimumRestingScaleSeen = 0.5f)
        assertFalse(tracker.isSettled(1f))
        tracker.report("focused", 1f, false)
        val minimum = if (tracker.isSettled(1f)) 1f else 0.5f
        val settled = geometry(800f, 480f, restingScale = 1f, minimumRestingScaleSeen = minimum)
        val fresh = geometry(800f, 480f, restingScale = 1f)
        assertEquals(fresh, settled)
        assertTrue(settled.cardHeight > pending.cardHeight)
    }

    @Test
    fun `flow reserve wraps every field with equal gaps and stays independent of selected title`() {
        val metadata = listOf(HomeFlowSize(90, 20), HomeFlowSize(90, 20), HomeFlowSize(90, 20))
        assertEquals(HomeFlowSize(196, 48), homeFlowSize(metadata, 200, 16, 8))
        assertEquals(HomeFlowSize(90, 76), homeFlowSize(metadata, 100, 16, 8))
        assertEquals(HomeFlowSize(0, 0), homeFlowSize(emptyList(), 100, 16, 8))
        assertEquals(HomeFlowSize(90, 76), homeFlowSize(metadata.reversed(), 100, 16, 8))
    }

    @Test
    fun `analytic spring envelope bounds repeated rapid reversals including retained velocity`() {
        val maximum = interruptedSpringMaximum(1f, 2f, 0.6f)
        val midpoint = 1.5
        val amplitude = maximum - midpoint
        var position = 1.0
        var velocity = 0.0
        val dt = 0.0001
        for (step in 0 until 100000) {
            val target = if ((step / 733) % 2 == 0) 2.0 else 1.0
            val acceleration = 400.0 * (target - position) - 24.0 * velocity
            velocity += acceleration * dt
            position += velocity * dt
            assertTrue(abs(position - midpoint) <= amplitude + 0.001)
        }
        assertEquals(2f, interruptedSpringMaximum(1f, 2f, 1f))
        assertTrue(maximum > 2f)
    }
}
