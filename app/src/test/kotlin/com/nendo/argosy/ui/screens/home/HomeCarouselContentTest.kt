package com.nendo.argosy.ui.screens.home

import com.nendo.argosy.domain.model.HomeRowAlignment
import com.nendo.argosy.ui.components.HERO_MAX_WIDTH_FRACTION
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeCarouselContentTest {
    @Test
    fun `compact normal text retains its fitted artwork without scrolling`() {
        val fitted = fitted(800f, 360f, 59.5f, 51.5f, 24f, 16f, 89.5f)
        val result = content(fitted = fitted)

        assertEquals(63.5f, fitted.cardHeight, 0.001f)
        assertFalse(result.scrollable)
        assertEquals(fitted, result.geometry)
        assertEquals(360f, result.viewportHeight, 0f)
    }

    @Test
    fun `ui150 keeps the usable side allocation`() {
        val fitted = fitted(960f, 540f, 150f, 77.5f, 36f, 24f, 102f)
        val result = content(
            fitted = fitted, width = 960f, height = 540f,
            header = 150f, footer = 77.5f, edge = 36f, gap = 24f, reserve = 102f,
            preferred = 144f
        )

        assertEquals(86.5f, fitted.cardHeight, 0.001f)
        assertFalse(result.scrollable)
        assertEquals(fitted, result.geometry)
    }

    @Test
    fun `48dp focused shortest extent stays fitted and smaller art enables overflow`() {
        val atMinimum = content(fitted = geometry(32f))
        val belowMinimum = content(fitted = geometry(31f))

        assertFalse(atMinimum.scrollable)
        assertTrue(belowMinimum.scrollable)
        assertTrue(belowMinimum.geometry.cardHeight * 0.75f * 2f >= 48f)
        assertEquals(59.5f, belowMinimum.viewportTop, 0f)
        assertEquals(225f, belowMinimum.viewportHeight, 0f)
    }

    @Test
    fun `full large font reserve remains reachable at each ui scale`() {
        for (scale in listOf(0.5f, 1f, 1.5f)) {
            val result = content(reserve = 500f, preferred = 96f * scale)
            val geometry = result.geometry
            assertTrue(result.scrollable)
            assertTrue(geometry.titleAbove)
            assertEquals(250f, geometry.titleCenterY, 0f)
            assertTrue(geometry.railTop >= 500f + 16f)
            assertEquals(96f * scale, geometry.cardHeight, 0.001f)
            assertEquals(geometry.railTop + geometry.railHeight, result.contentHeight, 0.001f)
            assertTrue(result.contentHeight > result.viewportHeight)
        }
    }

    @Test
    fun `overflow keeps aspect and the horizontal spring envelope inside parent bounds`() {
        val maximumScale = interruptedSpringMaximum(1f, 2f, 0.6f)
        for (aspect in listOf(0.5f, 1f, 2f, 3f)) {
            val result = content(width = 180f, reserve = 500f, aspect = aspect, preferred = 144f)
            val geometry = result.geometry
            assertTrue(result.scrollable)
            assertEquals(aspect, geometry.cardWidth / geometry.cardHeight, 0.001f)
            assertTrue(geometry.cardWidth <= 180f * HERO_MAX_WIDTH_FRACTION + 0.001f)
            assertTrue(geometry.cardWidth * maximumScale <= 180f - 48f + 0.001f)
            assertEquals(90f, geometry.titleCenterX, 0f)
        }
    }

    @Test
    fun `zero viewport has finite nonnegative overflow geometry`() {
        val result = content(width = 0f, height = 0f, reserve = 500f)
        assertEquals(0f, result.viewportTop, 0f)
        assertEquals(0f, result.viewportHeight, 0f)
        assertEquals(0f, result.geometry.cardWidth, 0f)
        assertEquals(0f, result.geometry.cardHeight, 0f)
        assertTrue(result.contentHeight.isFinite() && result.contentHeight >= 0f)
    }

    @Test
    fun `disabled overflow preserves no-title geometry despite extreme reserve`() {
        val fitted = geometry(1f)
        val result = content(fitted = fitted, reserve = 500f, enabled = false)
        assertFalse(result.scrollable)
        assertEquals(fitted, result.geometry)
        assertEquals(360f, result.contentHeight, 0f)
    }

    private fun fitted(
        width: Float, height: Float, header: Float, footer: Float,
        edge: Float, gap: Float, reserve: Float
    ) = homeCarouselGeometry(
        width, height, header, footer, edge, gap, reserve,
        aspectRatio = 0.75f, restingScale = 0.5f,
        rowAlignment = HomeRowAlignment.BOTTOM, centeredFocus = false, mirrored = false,
        badgeOverflow = 20f, dampingRatio = 0.6f
    )

    private fun geometry(cardHeight: Float = 100f) = HomeCarouselGeometry(
        cardWidth = cardHeight * 0.75f, cardHeight = cardHeight,
        railTop = 80f, railHeight = 240f,
        titleCenterX = 400f, titleCenterY = 120f, titleMaxWidth = 400f, titleAbove = false
    )

    private fun content(
        fitted: HomeCarouselGeometry = geometry(),
        width: Float = 800f,
        height: Float = 360f,
        header: Float = 59.5f,
        footer: Float = 51.5f,
        edge: Float = 24f,
        gap: Float = 16f,
        reserve: Float = 89.5f,
        aspect: Float = 0.75f,
        preferred: Float = 96f,
        enabled: Boolean = true
    ) = homeCarouselContent(
        fitted = fitted, width = width, height = height,
        headerHeight = header, footerHeight = footer, edge = edge, gap = gap,
        titleReserve = reserve, aspectRatio = aspect, restingScale = 0.5f,
        springMinimumRestingScale = 0.5f, badgeOverflow = 20f, dampingRatio = 0.6f,
        minimumFocusedExtent = 48f, preferredCardHeight = preferred, enabled = enabled
    )
}
