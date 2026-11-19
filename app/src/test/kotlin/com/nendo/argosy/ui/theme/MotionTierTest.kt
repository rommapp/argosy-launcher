package com.nendo.argosy.ui.theme

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.SnapSpec
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.ui.unit.dp
import coil.transition.Transition
import com.nendo.argosy.domain.model.isReducedMotion
import com.nendo.argosy.ui.common.AlwaysCrossfadeFactory
import com.nendo.argosy.ui.common.alwaysCrossfadeFor
import com.nendo.argosy.ui.theme.generated.MotionTokens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionTierTest {

    @Test
    fun `the toggle or a zero animator scale reduces motion, neither keeps it`() {
        assertTrue(isReducedMotion(reduceAnimations = true, animatorsEnabled = true))
        assertTrue(isReducedMotion(reduceAnimations = false, animatorsEnabled = false))
        assertTrue(isReducedMotion(reduceAnimations = true, animatorsEnabled = false))
        assertFalse(isReducedMotion(reduceAnimations = false, animatorsEnabled = true))
    }

    @Test
    fun `the resolver maps the rule onto the tier`() {
        assertEquals(MotionTier.Reduced, resolveMotionTier(reduceAnimations = true, animatorsEnabled = true))
        assertEquals(MotionTier.Reduced, resolveMotionTier(reduceAnimations = false, animatorsEnabled = false))
        assertEquals(MotionTier.Reduced, resolveMotionTier(reduceAnimations = true, animatorsEnabled = false))
        assertEquals(MotionTier.Comfortable, resolveMotionTier(reduceAnimations = false, animatorsEnabled = true))
    }

    @Test
    fun `specs snap under reduced and pass through otherwise`() {
        val spec = tween<Float>(Motion.durationPage)
        assertTrue(Motion.tierSpec(MotionTier.Reduced, spec) is SnapSpec<*>)
        assertSame(spec, Motion.tierSpec(MotionTier.Comfortable, spec))
        assertSame(spec, Motion.tierSpec(MotionTier.Snappy, spec))
    }

    @Test
    fun `durations are zero under reduced`() {
        assertEquals(0, Motion.tierDuration(MotionTier.Reduced, Motion.durationContent))
        assertEquals(Motion.durationContent, Motion.tierDuration(MotionTier.Comfortable, Motion.durationContent))
    }

    @Test
    fun `visibility and content transitions are none under reduced`() {
        val enter = fadeIn()
        val exit = fadeOut()
        assertSame(EnterTransition.None, Motion.tierEnter(MotionTier.Reduced, enter))
        assertSame(ExitTransition.None, Motion.tierExit(MotionTier.Reduced, exit))
        assertSame(enter, Motion.tierEnter(MotionTier.Comfortable, enter))
        assertSame(exit, Motion.tierExit(MotionTier.Comfortable, exit))

        val transform = enter togetherWith exit
        val reduced = Motion.tierContent(MotionTier.Reduced, transform)
        assertSame(EnterTransition.None, reduced.targetContentEnter)
        assertSame(ExitTransition.None, reduced.initialContentExit)
        assertSame(transform, Motion.tierContent(MotionTier.Comfortable, transform))
    }

    @Test
    fun `modal blur is zero under reduced and the requested radius otherwise`() {
        assertEquals(0.dp, Motion.tierModalBlur(MotionTier.Reduced, Motion.blurRadiusModal))
        assertEquals(Motion.blurRadiusModal, Motion.tierModalBlur(MotionTier.Comfortable, Motion.blurRadiusModal))
    }

    @Test
    fun `focus springs use the reduced tokens under reduced`() {
        assertSame(MotionTokens.Spring.focusReduced, Motion.tierFocusSpring(MotionTier.Reduced))
        assertSame(MotionTokens.Spring.focusReducedDp, Motion.tierFocusSpringDp(MotionTier.Reduced))
        assertSame(MotionTokens.Spring.focusReducedColor, Motion.tierFocusColorSpec(MotionTier.Reduced))
        assertSame(MotionTokens.Spring.focusColor, Motion.tierFocusColorSpec(MotionTier.Comfortable))
    }

    @Test
    fun `coil crossfades are none under reduced or at zero length`() {
        assertSame(Transition.Factory.NONE, alwaysCrossfadeFor(MotionTier.Reduced, 380))
        assertSame(Transition.Factory.NONE, alwaysCrossfadeFor(MotionTier.Comfortable, 0))
        assertEquals(AlwaysCrossfadeFactory(380), alwaysCrossfadeFor(MotionTier.Comfortable, 380))
    }
}
