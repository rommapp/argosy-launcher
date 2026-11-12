package com.nendo.argosy.ui.components.launch

import com.nendo.argosy.domain.model.LaunchPromptOption
import com.nendo.argosy.domain.model.LaunchProgress
import com.nendo.argosy.domain.model.LaunchStep
import com.nendo.argosy.domain.model.SyncProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LaunchVeilStateTest {

    private val gameDisplay = 1
    private val otherDisplay = 0
    private val veilMs = 700
    private val tolerance = 0.0001f

    private fun progress(step: LaunchStep) = LaunchProgress(
        gameTitle = "Game",
        step = step,
        gameId = 1L,
        stepStartedAtMs = 10_000L
    )

    private val quietSteps = listOf(
        LaunchStep.FinishingPrevious,
        LaunchStep.RecapLoading,
        LaunchStep.Save(SyncProgress.PreLaunch.CheckingSave(null, found = null)),
        LaunchStep.DownloadingCore(fraction = 0.5f, totalBytes = 1_000_000L),
        LaunchStep.InstallingTextures(fraction = 0.5f),
        LaunchStep.Starting,
        LaunchStep.PreparingSystemFiles,
        LaunchStep.PreparingUi,
        LaunchStep.ChooseLauncher(listOf(LaunchPromptOption.LAUNCH_WITH_GAMENATIVE))
    )

    @Test
    fun launchingStepVeils() {
        assertTrue(veilFor(progress(LaunchStep.Launching)))
    }

    @Test
    fun quietStepLeavesTheShowcaseUp() {
        quietSteps.forEach { step -> assertFalse("$step", veilFor(progress(step))) }
    }

    @Test
    fun noLaunchLeavesTheShowcaseUp() {
        assertFalse(veilFor(null))
    }

    @Test
    fun displayTheGameOpensOnIsVeiledWhileLaunching() {
        assertTrue(veilsDisplay(progress(LaunchStep.Launching), gameDisplay, gameDisplay))
    }

    @Test
    fun displayTheGameWasSentToClearsOnceLaunchingEnds() {
        assertFalse(veilsDisplay(null, gameDisplay, gameDisplay))
    }

    @Test
    fun displayTheGameWasSentToClearsOverAQuietStep() {
        quietSteps.forEach { step -> assertFalse("$step", veilsDisplay(progress(step), gameDisplay, gameDisplay)) }
    }

    @Test
    fun dashboardOnAnotherDisplayIsNeverVeiled() {
        assertFalse(veilsDisplay(null, gameDisplay, otherDisplay))
        assertFalse(veilsDisplay(progress(LaunchStep.Launching), gameDisplay, otherDisplay))
    }

    @Test
    fun unknownGameDisplayVeilsNothing() {
        assertFalse(veilsDisplay(progress(LaunchStep.Launching), null, gameDisplay))
        assertFalse(veilsDisplay(progress(LaunchStep.Launching), gameDisplay, null))
        assertFalse(veilsDisplay(progress(LaunchStep.Launching), null, null))
    }

    @Test
    fun singleScreenOverlayDarkensOnceTheGameIsLaunching() {
        assertTrue(darkensForLaunch(progress(LaunchStep.Launching), gameOpensHere = true))
    }

    @Test
    fun singleScreenOverlayStaysCalmBeforeLaunching() {
        quietSteps.forEach { step ->
            assertFalse("$step", darkensForLaunch(progress(step), gameOpensHere = true))
        }
        assertFalse(darkensForLaunch(null, gameOpensHere = true))
    }

    @Test
    fun overlayOnASurfaceTheGameDoesNotOpenOnNeverDarkens() {
        assertFalse(darkensForLaunch(progress(LaunchStep.Launching), gameOpensHere = false))
        quietSteps.forEach { step ->
            assertFalse("$step", darkensForLaunch(progress(step), gameOpensHere = false))
        }
    }

    @Test
    fun gameOpensOnTheOnlySurface() {
        assertTrue(opensOnSurface(hasPresentationSurface = false, gameDisplayId = null, surfaceDisplayId = otherDisplay))
        assertTrue(opensOnSurface(hasPresentationSurface = false, gameDisplayId = gameDisplay, surfaceDisplayId = otherDisplay))
    }

    @Test
    fun gamePlacedOnTheControlDisplayOpensOnTheControlSurface() {
        assertTrue(opensOnSurface(hasPresentationSurface = true, gameDisplayId = otherDisplay, surfaceDisplayId = otherDisplay))
    }

    @Test
    fun gamePlacedOnThePresentationDisplayLeavesTheControlSurfaceCalm() {
        assertFalse(opensOnSurface(hasPresentationSurface = true, gameDisplayId = gameDisplay, surfaceDisplayId = otherDisplay))
    }

    @Test
    fun unplacedGameBesideAPresentationSurfaceLeavesTheControlSurfaceCalm() {
        assertFalse(opensOnSurface(hasPresentationSurface = true, gameDisplayId = null, surfaceDisplayId = otherDisplay))
        assertFalse(opensOnSurface(hasPresentationSurface = true, gameDisplayId = gameDisplay, surfaceDisplayId = null))
    }

    @Test
    fun veilFirstDrawnAsLaunchingBeginsStartsClear() {
        assertEquals(0f, veilStartAlpha(true, 0L, veilMs, reducedMotion = false), tolerance)
    }

    @Test
    fun veilRecreatedHalfwayCarriesOnFromHalfway() {
        assertEquals(0.5f, veilStartAlpha(true, veilMs / 2L, veilMs, reducedMotion = false), tolerance)
    }

    @Test
    fun veilRecreatedAfterTheFadeIsBlack() {
        assertEquals(1f, veilStartAlpha(true, veilMs.toLong(), veilMs, reducedMotion = false), tolerance)
        assertEquals(1f, veilStartAlpha(true, veilMs * 10L, veilMs, reducedMotion = false), tolerance)
    }

    @Test
    fun clockAheadOfTheStepStartsClear() {
        assertEquals(0f, veilStartAlpha(true, -50L, veilMs, reducedMotion = false), tolerance)
    }

    @Test
    fun reducedMotionStartsAtTheTarget() {
        assertEquals(1f, veilStartAlpha(true, 0L, veilMs, reducedMotion = true), tolerance)
        assertEquals(0f, veilStartAlpha(false, veilMs.toLong(), veilMs, reducedMotion = true), tolerance)
    }

    @Test
    fun clearVeilStartsClear() {
        assertEquals(0f, veilStartAlpha(false, veilMs / 2L, veilMs, reducedMotion = false), tolerance)
    }

    @Test
    fun unknownStartKeepsTheTarget() {
        assertEquals(1f, veilStartAlpha(true, null, veilMs, reducedMotion = false), tolerance)
        assertEquals(0f, veilStartAlpha(false, null, veilMs, reducedMotion = false), tolerance)
    }

    @Test
    fun remainderRunsAtTheFullFadesRate() {
        assertEquals(veilMs, veilRemainingMs(0f, 1f, veilMs, reducedMotion = false))
        assertEquals(veilMs / 2, veilRemainingMs(0.5f, 1f, veilMs, reducedMotion = false))
        assertEquals(veilMs / 2, veilRemainingMs(0.5f, 0f, veilMs, reducedMotion = false))
        assertEquals(0, veilRemainingMs(1f, 1f, veilMs, reducedMotion = false))
    }

    @Test
    fun reducedMotionHasNoRemainder() {
        assertEquals(0, veilRemainingMs(0f, 1f, veilMs, reducedMotion = true))
    }
}
