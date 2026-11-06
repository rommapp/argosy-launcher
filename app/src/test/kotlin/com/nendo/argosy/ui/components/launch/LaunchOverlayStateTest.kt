package com.nendo.argosy.ui.components.launch

import com.nendo.argosy.R
import com.nendo.argosy.domain.model.LaunchFailure
import com.nendo.argosy.domain.model.LaunchPromptOption
import com.nendo.argosy.domain.model.LaunchProgress
import com.nendo.argosy.domain.model.LaunchRecap
import com.nendo.argosy.domain.model.LaunchStep
import com.nendo.argosy.domain.model.SyncProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LaunchOverlayStateTest {

    private val start = 10_000L
    private val patience = 2500

    private val save = LaunchStep.Save(SyncProgress.PreLaunch.CheckingSave(null, found = null))

    private val quietSteps = listOf(
        LaunchStep.FinishingPrevious,
        LaunchStep.RecapLoading,
        save,
        LaunchStep.DownloadingCore(fraction = null),
        LaunchStep.DownloadingCore(fraction = 0.5f, totalBytes = 1_000_000L),
        LaunchStep.Starting,
        LaunchStep.PreparingSystemFiles,
        LaunchStep.PreparingUi,
        LaunchStep.Launching
    )

    private fun progress(
        step: LaunchStep,
        failure: LaunchFailure? = null,
        gameId: Long? = 1L
    ) = LaunchProgress(
        gameTitle = "Game",
        step = step,
        gameId = gameId,
        stepStartedAtMs = start,
        failure = failure
    )

    private fun map(
        progress: LaunchProgress,
        elapsedMs: Long,
        reducedMotion: Boolean = false,
        patienceMs: Int = patience
    ) = LaunchOverlayState.from(
        progress = progress,
        nowMs = start + elapsedMs,
        reducedMotion = reducedMotion,
        patienceMs = patienceMs,
        reflowMs = 900,
        statusFadeMs = 700,
        statusFadeDelayMs = 500,
        sweepMs = 1400
    )

    @Test
    fun `the first patience window is quiet whatever the step`() {
        for (step in quietSteps) {
            for (elapsed in listOf(0L, 1000L, patience - 1L)) {
                val ui = map(progress(step), elapsed)
                assertNull("$step at $elapsed", ui.line)
                assertEquals("$step at $elapsed", CalmLaunchBar.NONE, ui.bar)
                assertNull("$step at $elapsed", ui.percent)
                assertNull("$step at $elapsed", ui.etaSeconds)
            }
        }
    }

    @Test
    fun `a quiet step past patience shows its waiting line and an indeterminate bar`() {
        val expected = mapOf(
            LaunchStep.FinishingPrevious to R.string.ui_launch_overlay_wait_previous,
            LaunchStep.RecapLoading to R.string.ui_launch_overlay_wait_recap,
            save to R.string.ui_launch_overlay_wait_save,
            LaunchStep.DownloadingCore(fraction = null) to R.string.ui_launch_overlay_wait_core,
            LaunchStep.Starting to R.string.ui_launch_overlay_wait_prepare,
            LaunchStep.PreparingSystemFiles to R.string.ui_launch_overlay_wait_prepare,
            LaunchStep.PreparingUi to R.string.ui_launch_overlay_wait_prepare
        )
        for ((step, line) in expected) {
            val ui = map(progress(step), patience.toLong())
            assertEquals("$step", line, ui.line)
            assertFalse("$step", ui.lineIsFailure)
            assertEquals("$step", CalmLaunchBar.INDETERMINATE, ui.bar)
            assertNull("$step", ui.percent)
            assertTrue("$step", ui.cancellable)
        }
    }

    @Test
    fun `a sized step within patience stays quiet`() {
        val ui = map(progress(LaunchStep.DownloadingCore(fraction = 0.9f, totalBytes = 5_000_000L)), 2000L)
        assertNull(ui.line)
        assertEquals(CalmLaunchBar.NONE, ui.bar)
        assertNull(ui.percent)
        assertNull(ui.etaSeconds)
    }

    @Test
    fun `a sized step past patience shows a determinate bar with its percent`() {
        val ui = map(progress(LaunchStep.DownloadingCore(fraction = 0.426f)), 3000L)
        assertEquals(CalmLaunchBar.DETERMINATE, ui.bar)
        assertEquals(0.426f, ui.fraction!!, 0f)
        assertEquals(43, ui.percent)
        assertEquals(R.string.ui_launch_overlay_wait_core, ui.line)
    }

    @Test
    fun `a sized step past patience shows its byte size`() {
        val ui = map(progress(LaunchStep.DownloadingCore(fraction = 0.5f, totalBytes = 12_900_000L)), 3000L)
        assertEquals(R.string.ui_launch_overlay_wait_core_sized, ui.line)
        assertEquals(12_900_000L, ui.sizeBytes)
        assertEquals(50, ui.percent)
    }

    @Test
    fun `a sized step with no known size shows percent alone`() {
        for (unknown in listOf(null, -1L, 0L)) {
            val ui = map(progress(LaunchStep.DownloadingCore(fraction = 0.5f, totalBytes = unknown)), 3000L)
            assertEquals("$unknown", R.string.ui_launch_overlay_wait_core, ui.line)
            assertNull("$unknown", ui.sizeBytes)
            assertEquals("$unknown", 50, ui.percent)
        }
    }

    @Test
    fun `a failure shows at once without a bar`() {
        val expected = mapOf(
            LaunchFailure.SERVER_UNREACHABLE to R.string.ui_launch_overlay_failed_connect,
            LaunchFailure.SERVER_TIMED_OUT to R.string.ui_launch_overlay_failed_timeout,
            LaunchFailure.SAVE_DOWNLOAD_FAILED to R.string.ui_launch_overlay_failed_download,
            LaunchFailure.SAVE_WRITE_FAILED to R.string.ui_launch_overlay_failed_write
        )
        for ((failure, line) in expected) {
            val ui = map(progress(save, failure = failure), 0L)
            assertEquals("$failure", line, ui.line)
            assertTrue("$failure", ui.lineIsFailure)
            assertEquals("$failure", CalmLaunchBar.NONE, ui.bar)
        }
    }

    @Test
    fun `a slow server's line shows at once without a bar`() {
        val ui = map(progress(LaunchStep.Save(SyncProgress.PreLaunch.TimedOut(null))), 0L)
        assertEquals(R.string.ui_launch_overlay_failed_timeout, ui.line)
        assertTrue(ui.lineIsFailure)
        assertEquals(CalmLaunchBar.NONE, ui.bar)
    }

    @Test
    fun `a failure stays while later steps are quiet`() {
        for (step in listOf(LaunchStep.PreparingSystemFiles, LaunchStep.PreparingUi, LaunchStep.Launching)) {
            val ui = map(progress(step, failure = LaunchFailure.SERVER_UNREACHABLE), 100L)
            assertEquals("$step", R.string.ui_launch_overlay_failed_connect, ui.line)
            assertTrue("$step", ui.lineIsFailure)
            assertEquals("$step", CalmLaunchBar.NONE, ui.bar)
        }
    }

    @Test
    fun `a failure keeps a later sized step's bar`() {
        val step = LaunchStep.DownloadingCore(fraction = 0.4f, totalBytes = 3_000_000L)
        val ui = map(progress(step, failure = LaunchFailure.SERVER_UNREACHABLE), 3000L)
        assertEquals(R.string.ui_launch_overlay_failed_connect, ui.line)
        assertTrue(ui.lineIsFailure)
        assertEquals(CalmLaunchBar.DETERMINATE, ui.bar)
        assertEquals(40, ui.percent)
    }

    @Test
    fun `a question step never waits on screen`() {
        val questions = listOf(
            LaunchStep.ChooseLauncher(listOf(LaunchPromptOption.LAUNCH_WITH_GAMENATIVE)),
            LaunchStep.Prompt(SyncProgress.Error("x"), listOf(LaunchPromptOption.APPLY_LOCAL)),
            LaunchStep.Recap(LaunchRecap(gameTitle = "Game", lastSession = null, unlocks = emptyList()))
        )
        for (step in questions) {
            val ui = map(progress(step), 60_000L)
            assertNull("$step", ui.line)
            assertEquals("$step", CalmLaunchBar.NONE, ui.bar)
            assertNull("$step", ui.revealAtMs)
        }
    }

    @Test
    fun `launching cannot be cancelled and never waits`() {
        val ui = map(progress(LaunchStep.Launching), 60_000L)
        assertFalse(ui.cancellable)
        assertNull(ui.line)
        assertEquals(CalmLaunchBar.NONE, ui.bar)
        assertNull(ui.revealAtMs)
        assertTrue(map(progress(LaunchStep.PreparingUi), 0L).cancellable)
    }

    @Test
    fun `eta is the remaining fraction at the observed rate, rounded up`() {
        val quarter = progress(LaunchStep.DownloadingCore(fraction = 0.25f))
        assertEquals(9, map(quarter, 3000L).etaSeconds)
        assertEquals(10, map(quarter, 3100L).etaSeconds)
    }

    @Test
    fun `a finished download shows its percent with no time left`() {
        val done = map(progress(LaunchStep.DownloadingCore(fraction = 1f)), 3000L)
        assertEquals(100, done.percent)
        assertNull(done.etaSeconds)
        assertNull(map(progress(LaunchStep.DownloadingCore(fraction = 1.2f)), 3000L).etaSeconds)
    }

    @Test
    fun `no eta before the fraction and the elapsed time are known`() {
        assertNull(map(progress(LaunchStep.DownloadingCore(fraction = 0.04f)), 5000L).etaSeconds)
        assertNull(map(progress(LaunchStep.DownloadingCore(fraction = 0.5f)), 999L, patienceMs = 0).etaSeconds)
        assertEquals(1, map(progress(LaunchStep.DownloadingCore(fraction = 0.5f)), 1000L, patienceMs = 0).etaSeconds)
        assertNull(map(progress(LaunchStep.DownloadingCore(fraction = null)), 5000L).etaSeconds)
    }

    @Test
    fun `reduced motion zeroes every duration`() {
        val ui = map(progress(save), 3000L, reducedMotion = true)
        assertEquals(0, ui.reflowMs)
        assertEquals(0, ui.statusFadeMs)
        assertEquals(0, ui.statusFadeDelayMs)
        assertEquals(0, ui.sweepMs)
        assertEquals(CalmLaunchBar.INDETERMINATE, ui.bar)
        val full = map(progress(save), 3000L)
        assertEquals(listOf(900, 700, 500, 1400), listOf(full.reflowMs, full.statusFadeMs, full.statusFadeDelayMs, full.sweepMs))
    }

    @Test
    fun `reveal time is step start plus patience`() {
        assertEquals(start + patience, map(progress(save), 0L).revealAtMs)
        assertEquals(start + 4000L, map(progress(save), 0L, patienceMs = 4000).revealAtMs)
    }

    @Test
    fun `the presentation display draws only the recap, never a question`() {
        val recap = LaunchRecap(gameTitle = "Game", lastSession = null, unlocks = emptyList())
        assertTrue(LaunchStep.Recap(recap).mirrorsToPresentation)
        val kept = quietSteps + listOf(
            LaunchStep.Prompt(SyncProgress.Error("x"), listOf(LaunchPromptOption.APPLY_LOCAL)),
            LaunchStep.ChooseLauncher(listOf(LaunchPromptOption.LAUNCH_WITH_GAMENATIVE)),
            LaunchStep.Save(SyncProgress.PreLaunch.Connecting(null, success = false)),
            LaunchStep.Save(SyncProgress.Error("x")),
            LaunchStep.DownloadingCore(fraction = 0.5f)
        )
        for (step in kept) assertFalse("$step", step.mirrorsToPresentation)
    }

    @Test
    fun `a launch with no game id still maps to a quiet cancellable column`() {
        val ui = map(progress(save, gameId = null), 0L)
        assertTrue(ui.cancellable)
        assertNull(ui.line)
        assertEquals(CalmLaunchBar.NONE, ui.bar)
    }

    @Test
    fun `the cancel hint shows on a waiting step with no app prompt`() {
        assertTrue(LaunchStep.FinishingPrevious.showsCancelHint(appPromptShowing = false))
        assertTrue(save.showsCancelHint(appPromptShowing = false))
    }

    @Test
    fun `the cancel hint hides on a waiting step while an app prompt shows`() {
        assertFalse(LaunchStep.FinishingPrevious.showsCancelHint(appPromptShowing = true))
        assertFalse(save.showsCancelHint(appPromptShowing = true))
    }

    @Test
    fun `the cancel hint stays hidden on recap and launching whatever the prompt`() {
        val recap = LaunchStep.Recap(LaunchRecap(gameTitle = "Game", lastSession = null, unlocks = emptyList()))
        for (step in listOf(recap, LaunchStep.Launching)) {
            for (prompt in listOf(false, true)) {
                assertFalse("$step with prompt $prompt", step.showsCancelHint(appPromptShowing = prompt))
            }
        }
    }
}
