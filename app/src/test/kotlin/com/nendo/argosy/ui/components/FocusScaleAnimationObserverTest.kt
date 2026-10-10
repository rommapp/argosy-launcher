package com.nendo.argosy.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.runtime.MonotonicFrameClock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
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

    @Test
    fun coalescedReturnToSettledTargetNeedsNoFinishedCallback() = runTest {
        val animation = Animatable(2f)
        val targets = Channel<Float>(Channel.CONFLATED)
        targets.trySend(1f)
        targets.trySend(2f)
        val driver = launch(TestFrameClock()) {
            animateFocusScaleTargets(animation, targets) { spring(dampingRatio = 0.6f, stiffness = 400f) }
        }
        runCurrent()
        assertEquals(2f, animation.targetValue, 0f)
        assertFalse(focusScaleIsRunning(animation, 2f))
        driver.cancelAndJoin()
    }

    @Test
    fun reversalWhileMovingRetainsVelocityUntilTheRealSpringSettles() = runTest {
        val animation = Animatable(2f)
        val clock = TestFrameClock()
        val targets = Channel<Float>(Channel.CONFLATED)
        val driver = launch(clock) {
            animateFocusScaleTargets(animation, targets) { spring(dampingRatio = 0.6f, stiffness = 400f) }
        }
        targets.trySend(1f)
        assertTrue(focusScaleIsRunning(animation, 1f))
        runCurrent()
        clock.frame(0)
        runCurrent()
        clock.frame(1)
        runCurrent()
        assertTrue(animation.value < 2f)
        val priorVelocity = animation.velocity
        assertTrue(priorVelocity < 0f)
        targets.trySend(2f)
        runCurrent()
        assertEquals(priorVelocity, animation.velocity, 0.0001f)
        assertTrue(focusScaleIsRunning(animation, 2f))
        for (frame in 2..250) {
            clock.frame(frame)
            runCurrent()
            if (!focusScaleIsRunning(animation, 2f)) break
        }
        assertFalse(focusScaleIsRunning(animation, 2f))
        assertEquals(2f, animation.value, 0f)
        driver.cancelAndJoin()
    }

    @Test
    fun settingTransitionReleasesTheTrackerAfterActualAnimationCompletion() = runTest {
        val animation = Animatable(2f)
        val clock = TestFrameClock()
        val targets = Channel<Float>(Channel.CONFLATED)
        val tracker = CarouselScaleAnimationTracker()
        tracker.register("focused")
        val driver = launch(clock) {
            animateFocusScaleTargets(animation, targets) { spring(dampingRatio = 0.6f, stiffness = 400f) }
        }
        targets.trySend(1f)
        tracker.report("focused", 1f, focusScaleIsRunning(animation, 1f))
        assertFalse(tracker.isSettled(1f))
        runCurrent()
        for (frame in 0..250) {
            clock.frame(frame)
            runCurrent()
            tracker.report("focused", 1f, focusScaleIsRunning(animation, 1f))
            if (tracker.isSettled(1f)) break
        }
        assertTrue(tracker.isSettled(1f))
        driver.cancelAndJoin()
    }

    private class TestFrameClock : MonotonicFrameClock {
        private val frames = Channel<Long>(Channel.UNLIMITED)
        fun frame(index: Int) { frames.trySend(index * 16_000_000L) }
        override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R = onFrame(frames.receive())
    }
}
