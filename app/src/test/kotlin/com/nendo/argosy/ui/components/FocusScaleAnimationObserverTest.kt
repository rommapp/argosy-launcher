package com.nendo.argosy.ui.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusScaleAnimationObserverTest {
    @Test
    fun focusChangeWaitsForEveryComposedCardToReportTheNewTarget() {
        val tracker = CarouselScaleAnimationTracker()
        tracker.register("previous")
        tracker.register("next")
        tracker.report("previous", 2f, false, focusIndex = 0)
        tracker.report("next", 2f, false, focusIndex = 0)
        assertFalse(tracker.isSettled(2f, focusIndex = 1))
        tracker.report("previous", 2f, false, focusIndex = 1)
        assertFalse(tracker.isSettled(2f, focusIndex = 1))
        tracker.report("next", 2f, true, focusIndex = 1)
        assertFalse(tracker.isSettled(2f, focusIndex = 1))
        tracker.report("next", 2f, false, focusIndex = 1)
        assertTrue(tracker.isSettled(2f, focusIndex = 1))
    }

    @Test
    fun configurationChangeWaitsForEveryComposedAnimationReceipt() {
        val tracker = CarouselScaleAnimationTracker()
        tracker.register("game")
        tracker.register("media")
        tracker.report("game", 2f, false)
        tracker.report("media", 2f, false)
        assertTrue(tracker.isSettled(2f))
        assertFalse(tracker.isSettled(1f))
        tracker.report("game", 1f, false)
        assertFalse(tracker.isSettled(1f))
        tracker.report("media", 1f, true)
        assertFalse(tracker.isSettled(1f))
        tracker.report("media", 1f, false)
        assertTrue(tracker.isSettled(1f))
    }

    @Test
    fun newlyComposedAndDisposedCardsCannotLeaveAStaleRunningReceipt() {
        val tracker = CarouselScaleAnimationTracker()
        assertFalse(tracker.isSettled(1f))
        tracker.register("game")
        tracker.report("game", 1f, false)
        tracker.register("view-all")
        assertFalse(tracker.isSettled(1f))
        tracker.report("view-all", 1f, true)
        assertFalse(tracker.isSettled(1f))
        tracker.remove("view-all")
        assertTrue(tracker.isSettled(1f))
        tracker.report("view-all", 1f, true)
        assertTrue(tracker.isSettled(1f))
    }

}
