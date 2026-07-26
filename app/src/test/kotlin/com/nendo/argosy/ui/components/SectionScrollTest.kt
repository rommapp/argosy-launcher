package com.nendo.argosy.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class SectionScrollTest {
    @Test
    fun `initially visible focus remains stationary`() {
        assertEquals(
            FocusedScrollBehavior.KEEP,
            focusedScrollBehavior(isInitialPass = true, isTargetFullyVisible = true)
        )
    }

    @Test
    fun `initially clipped focus snaps fully into view`() {
        assertEquals(
            FocusedScrollBehavior.SNAP,
            focusedScrollBehavior(isInitialPass = true, isTargetFullyVisible = false)
        )
    }

    @Test
    fun `ordinary focus movement remains animated`() {
        assertEquals(
            FocusedScrollBehavior.ANIMATE,
            focusedScrollBehavior(isInitialPass = false, isTargetFullyVisible = true)
        )
    }

    @Test
    fun `large focus jumps remain immediate`() {
        assertEquals(
            FocusedScrollBehavior.SNAP,
            focusedScrollBehavior(
                isInitialPass = false,
                isTargetFullyVisible = false,
                jumped = true
            )
        )
    }
}
