package com.nendo.argosy.ui.dualscreen

import com.nendo.argosy.ui.theme.generated.ComponentDefaults
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PresentationGeometryTest {
    @Test
    fun artworkKeepsItsNativeAspectAcrossViewportShapes() {
        for ((width, height) in listOf(560f to 780f, 220f to 940f, 360f to 120f)) {
            for (aspect in listOf(2f / 3f, 1f, 16f / 9f)) {
                val fitted = fitPresentationArt(width, height, aspect)
                assertTrue(fitted.width <= width + 0.001f && fitted.height <= height + 0.001f)
                assertEquals(aspect, fitted.width / fitted.height, 0.00001f)
                assertTrue(kotlin.math.abs(fitted.width - width) < 0.001f || kotlin.math.abs(fitted.height - height) < 0.001f)
            }
        }
    }

    @Test
    fun emptyAndInvalidArtworkConstraintsStayFinite() {
        for (size in listOf(
            fitPresentationArt(0f, 200f, 1f),
            fitPresentationArt(200f, -1f, 1f),
            fitPresentationArt(200f, 200f, Float.NaN),
            fitPresentationArt(200f, 200f, 0f)
        )) {
            assertEquals(PresentationSize(0f, 0f), size)
        }
    }

    @Test
    fun titleReflowChangesEllipseWithoutChangingViewportOverscan() {
        val horizontalOverscan = ComponentDefaults.Presentation.titleOverscanWidthRatio
        val verticalOverscan = ComponentDefaults.Presentation.titleOverscanHeightRatio
        val oneLine = presentationTitleRadii(1920f, 1080f, 900f, 140f, horizontalOverscan, verticalOverscan)
        val wrapped = presentationTitleRadii(1920f, 1080f, 700f, 320f, horizontalOverscan, verticalOverscan)
        assertEquals(100f, oneLine.width - wrapped.width, 0.001f)
        assertEquals(90f, wrapped.height - oneLine.height, 0.001f)
        assertEquals(814.8f, oneLine.width, 0.001f)
        assertEquals(266.02f, oneLine.height, 0.001f)
    }

    @Test
    fun viewportResizeScalesOverscanIndependentlyOfTitleSize() {
        val small = presentationTitleRadii(800f, 600f, 300f, 100f, 0.1354f, 0.1815f)
        val large = presentationTitleRadii(1600f, 1200f, 300f, 100f, 0.1354f, 0.1815f)
        assertEquals((small.width - 150f) * 2f, large.width - 150f, 0.001f)
        assertEquals((small.height - 50f) * 2f, large.height - 50f, 0.001f)
    }

    @Test
    fun softEllipseContainsMeasuredTitleAndMetadataCornersAfterReflow() {
        for ((width, height) in listOf(900f to 140f, 700f to 320f, 300f to 700f)) {
            val radii = presentationTitleRadii(
                1920f, 1080f, width, height,
                ComponentDefaults.Presentation.titleOverscanWidthRatio,
                ComponentDefaults.Presentation.titleOverscanHeightRatio
            )
            val x = width / 2f / radii.width
            val y = height / 2f / radii.height
            assertTrue(x * x + y * y < 1f)
        }
    }

    @Test
    fun bothTextGradientStopsFollowUserStrengthIncludingZero() {
        for (alpha in listOf(
            ComponentDefaults.Presentation.titleCenterAlpha,
            ComponentDefaults.Presentation.titleMidAlpha
        )) {
            assertEquals(0f, presentationScrimAlpha(alpha, 0f), 0f)
            assertEquals(alpha, presentationScrimAlpha(alpha, 1f), 0f)
            assertEquals(alpha / 2f, presentationScrimAlpha(alpha, 0.5f), 0.00001f)
            assertTrue(presentationScrimAlpha(alpha, 100f / 90f) <= 1f)
        }
    }
}
