package com.nendo.argosy.data.emulator

import com.nendo.argosy.domain.model.LaunchFailure
import com.nendo.argosy.domain.model.LaunchPromptOption
import com.nendo.argosy.domain.model.LaunchRecap
import com.nendo.argosy.domain.model.LaunchStep
import com.nendo.argosy.domain.model.MakeRoomCandidate
import com.nendo.argosy.domain.model.MakeRoomStage
import com.nendo.argosy.domain.model.SyncProgress
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LaunchProgressTrackerTest {

    private fun TestScope.tracker() = LaunchProgressTracker(backgroundScope)

    private var now = 0L

    private fun TestScope.timedTracker() = LaunchProgressTracker(backgroundScope) { now }

    private val conflict = SyncProgress.LocalModified(gameId = 1L, localSavePath = "/save", channelName = null)

    private val recap = LaunchRecap(gameTitle = "Game", lastSession = null, unlocks = emptyList())

    @Test
    fun `a launch reports its steps under the game title`() = runTest {
        val tracker = tracker()
        val ticket = tracker.begin(1L, "F-Zero GX")!!

        ticket.step(LaunchStep.PreparingSystemFiles)

        assertEquals("F-Zero GX", tracker.progress.value?.gameTitle)
        assertEquals(LaunchStep.PreparingSystemFiles, tracker.progress.value?.step)
    }

    @Test
    fun `a second launch is refused while one is in progress`() = runTest {
        val tracker = tracker()
        tracker.begin(1L, "First")

        assertNull(tracker.begin(1L, "Second"))
    }

    @Test
    fun `cancelling hides the launch and stops its later steps from showing`() = runTest {
        val tracker = tracker()
        val ticket = tracker.begin(1L, "Game")!!

        assertTrue(tracker.cancel())
        ticket.step(LaunchStep.PreparingUi)

        assertTrue(ticket.isCancelled)
        assertNull(tracker.progress.value)
    }

    @Test
    fun `cancelling a prompt answers it with nothing`() = runTest {
        val tracker = tracker()
        val ticket = tracker.begin(1L, "Game")!!
        val answer = async { ticket.ask(conflict, listOf(LaunchPromptOption.APPLY_LOCAL)) }
        runCurrent()

        tracker.cancel()

        assertNull(answer.await())
    }

    @Test
    fun `a prompt answer reaches the launch`() = runTest {
        val tracker = tracker()
        val ticket = tracker.begin(1L, "Game")!!
        val answer = async { ticket.ask(conflict, listOf(LaunchPromptOption.APPLY_LOCAL, LaunchPromptOption.RESTORE_SERVER)) }
        runCurrent()

        tracker.answer(LaunchPromptOption.RESTORE_SERVER)

        assertEquals(LaunchPromptOption.RESTORE_SERVER, answer.await())
    }

    private val makeRoom = LaunchStep.MakeRoom(
        launchingTitle = "Game",
        candidates = listOf(
            MakeRoomCandidate(
                taskId = 7,
                slotIndex = 1,
                gameId = 2L,
                title = "Open",
                snapshotPath = null,
                snapshotTakenAtMillis = 0L,
                lastSavedAtMillis = null
            )
        ),
        initialFocus = 0,
        stage = MakeRoomStage.CHOOSE,
        chosenTaskId = null
    )

    @Test
    fun `choosing a game to close returns its task`() = runTest {
        val tracker = tracker()
        val ticket = tracker.begin(1L, "Game")!!
        val chosen = async { ticket.chooseGameToClose(makeRoom) }
        runCurrent()

        assertEquals(makeRoom, tracker.progress.value?.step)
        tracker.closeToMakeRoom(7)

        assertEquals(7, chosen.await())
    }

    @Test
    fun `cancelling the make-room question returns no task`() = runTest {
        val tracker = tracker()
        val ticket = tracker.begin(1L, "Game")!!
        val chosen = async { ticket.chooseGameToClose(makeRoom) }
        runCurrent()

        tracker.cancel()

        assertNull(chosen.await())
        assertTrue(ticket.isCancelled)
    }

    @Test
    fun `a prompt answer does not answer the make-room question`() = runTest {
        val tracker = tracker()
        val ticket = tracker.begin(1L, "Game")!!
        val chosen = async { ticket.chooseGameToClose(makeRoom) }
        runCurrent()

        tracker.answer(LaunchPromptOption.CLOSE_AND_LAUNCH)
        runCurrent()

        assertFalse(chosen.isCompleted)
        tracker.cancel()
        assertNull(chosen.await())
    }

    @Test
    fun `starting from the recap continues the launch`() = runTest {
        val tracker = tracker()
        val ticket = tracker.begin(1L, "Game")!!
        val started = async { ticket.offerRecap(recap) }
        runCurrent()

        assertEquals(LaunchStep.Recap(recap), tracker.progress.value?.step)
        tracker.answer(LaunchPromptOption.START_GAME)

        assertTrue(started.await())
    }

    @Test
    fun `cancelling from the recap stops the launch`() = runTest {
        val tracker = tracker()
        val ticket = tracker.begin(1L, "Game")!!
        val started = async { ticket.offerRecap(recap) }
        runCurrent()

        tracker.cancel()

        assertFalse(started.await())
        assertTrue(ticket.isCancelled)
    }

    @Test
    fun `a recap offered after a cancel is never shown`() = runTest {
        val tracker = tracker()
        val ticket = tracker.begin(1L, "Game")!!
        tracker.cancel()

        assertFalse(ticket.offerRecap(recap))
        assertNull(tracker.progress.value)
    }

    @Test
    fun `a new launch waits for a cancelled one to finish before it starts`() = runTest {
        val tracker = tracker()
        val cancelled = tracker.begin(1L, "First")!!
        tracker.cancel()

        val next = async { tracker.begin(1L, "Second") }
        runCurrent()

        assertFalse(next.isCompleted)
        assertEquals(LaunchStep.FinishingPrevious, tracker.progress.value?.step)

        tracker.finish(cancelled, launched = false)

        val ticket = next.await()!!
        assertEquals(ticket, tracker.current)
    }

    @Test
    fun `a cancelled launch that never finishes holds the next one for a bounded time`() = runTest {
        val tracker = tracker()
        tracker.begin(1L, "Stuck")!!
        tracker.cancel()

        val next = async { tracker.begin(1L, "Second") }
        advanceTimeBy(14_000)
        assertFalse(next.isCompleted)
        advanceTimeBy(2_000)

        assertEquals("Second", next.await()?.gameTitle)
        assertEquals("Second", tracker.current?.gameTitle)
    }

    @Test
    fun `a launch cancelled while it waits never starts, and the next one does not wait on it`() = runTest {
        val tracker = tracker()
        val first = tracker.begin(1L, "First")!!
        tracker.cancel()
        val second = async { tracker.begin(1L, "Second") }
        runCurrent()

        tracker.cancel()
        tracker.finish(first, launched = false)

        assertNull(second.await())
        assertEquals("Third", tracker.begin(1L, "Third")?.gameTitle)
    }

    @Test
    fun `a launch that opened its game shows until the screen that started it stops`() = runTest {
        val tracker = tracker()
        val ticket = tracker.begin(1L, "Game")!!

        tracker.finish(ticket, launched = true)
        assertEquals(LaunchStep.Launching, tracker.progress.value?.step)
        assertFalse(tracker.cancel())

        tracker.hostStopped()
        assertNull(tracker.progress.value)
    }

    @Test
    fun `a launched game's screen taking over ends the launching hold`() = runTest {
        val tracker = tracker()
        val ticket = tracker.begin(1L, "Game")!!

        tracker.finish(ticket, launched = true)
        advanceTimeBy(3_000)
        assertEquals(LaunchStep.Launching, tracker.progress.value?.step)

        tracker.hostStopped()
        assertNull(tracker.progress.value)
        advanceTimeBy(10_000)
        assertNull(tracker.progress.value)
    }

    @Test
    fun `a screen that leaves before the game opens keeps the launch running and its hold`() = runTest {
        val tracker = tracker()
        val ticket = tracker.begin(1L, "Game")!!
        ticket.step(LaunchStep.PreparingUi)

        tracker.hostStopped()
        assertEquals(LaunchStep.PreparingUi, tracker.progress.value?.step)
        assertEquals(ticket, tracker.current)

        tracker.finish(ticket, launched = true)
        assertEquals(LaunchStep.Launching, tracker.progress.value?.step)
        tracker.hostStopped()
        assertNull(tracker.progress.value)
    }

    @Test
    fun `a launch held for its window outlasts the screen that started it stopping`() = runTest {
        val tracker = tracker()
        val ticket = tracker.begin(1L, "Game")!!
        tracker.holdForWindow(ticket)
        tracker.finish(ticket, launched = true)

        tracker.hostStopped()
        assertEquals(LaunchStep.Launching, tracker.progress.value?.step)

        tracker.windowRequested(ticket)
        tracker.hostStopped()
        assertNull(tracker.progress.value)
    }

    @Test
    fun `a launch held for a window that is never requested still clears on its own`() = runTest {
        val tracker = tracker()
        val ticket = tracker.begin(1L, "Game")!!
        tracker.holdForWindow(ticket)
        tracker.finish(ticket, launched = true)

        advanceTimeBy(10_000)

        assertNull(tracker.progress.value)
    }

    @Test
    fun `a game screen that never opens clears the launching hold at once`() = runTest {
        val tracker = tracker()
        val ticket = tracker.begin(1L, "Game")!!
        tracker.finish(ticket, launched = true)

        tracker.openFailed(ticket)

        assertNull(tracker.progress.value)
        assertNull(tracker.current)
        assertTrue(tracker.begin(2L, "Next") != null)
    }

    @Test
    fun `a failed game screen leaves a launch begun since alone`() = runTest {
        val tracker = tracker()
        val first = tracker.begin(1L, "First")!!
        tracker.finish(first, launched = true)
        val second = tracker.begin(2L, "Second")!!
        second.step(LaunchStep.PreparingUi)

        tracker.openFailed(first)

        assertEquals(LaunchStep.PreparingUi, tracker.progress.value?.step)
        assertEquals(second, tracker.current)
    }

    @Test
    fun `a failed game screen leaves a newer launch's launching alone`() = runTest {
        val tracker = tracker()
        val first = tracker.begin(1L, "First")!!
        tracker.finish(first, launched = true)
        val second = tracker.begin(2L, "Second")!!
        tracker.finish(second, launched = true)

        tracker.openFailed(first)

        assertEquals(2L, tracker.progress.value?.gameId)
        assertEquals(LaunchStep.Launching, tracker.progress.value?.step)
    }

    @Test
    fun `a launch whose screen never stops clears on its own`() = runTest {
        val tracker = tracker()
        val ticket = tracker.begin(1L, "Game")!!

        tracker.finish(ticket, launched = true)
        advanceTimeBy(10_000)

        assertNull(tracker.progress.value)
    }

    @Test
    fun `a launch that failed clears at once`() = runTest {
        val tracker = tracker()
        val ticket = tracker.begin(1L, "Game")!!

        tracker.finish(ticket, launched = false)

        assertNull(tracker.progress.value)
        assertNull(tracker.current)
    }

    @Test
    fun `a launch may start while the last one is still showing its game opening`() = runTest {
        val tracker = tracker()
        tracker.finish(tracker.begin(1L, "First")!!, launched = true)

        val second = tracker.begin(1L, "Second")

        assertEquals(second, tracker.current)
    }

    @Test
    fun `a step in the same phase keeps the moment the phase started`() = runTest {
        val tracker = timedTracker()
        val ticket = tracker.begin(1L, "Game")!!
        now = 1_000L
        ticket.step(LaunchStep.Save(SyncProgress.PreLaunch.CheckingSave(null)))

        now = 4_000L
        ticket.step(LaunchStep.Save(SyncProgress.PreLaunch.Connecting(null)))
        now = 6_000L
        ticket.step(LaunchStep.Save(SyncProgress.PreLaunch.Connecting(null)))

        assertEquals(1_000L, tracker.progress.value?.stepStartedAtMs)
    }

    @Test
    fun `a step in a new phase starts its own clock`() = runTest {
        val tracker = timedTracker()
        val ticket = tracker.begin(1L, "Game")!!
        now = 1_000L
        ticket.step(LaunchStep.Save(SyncProgress.PreLaunch.CheckingSave(null)))

        now = 4_000L
        ticket.step(LaunchStep.DownloadingCore(fraction = null))
        now = 5_000L
        ticket.step(LaunchStep.DownloadingCore(fraction = 0.5f, totalBytes = 2_048L))

        assertEquals(4_000L, tracker.progress.value?.stepStartedAtMs)
    }

    @Test
    fun `the first step after a question starts its own clock`() = runTest {
        val tracker = timedTracker()
        val ticket = tracker.begin(1L, "Game")!!
        now = 1_000L
        val answer = async { ticket.ask(conflict, listOf(LaunchPromptOption.APPLY_LOCAL)) }
        runCurrent()
        tracker.answer(LaunchPromptOption.APPLY_LOCAL)
        answer.await()

        now = 7_000L
        async { ticket.chooseLauncher(listOf(LaunchPromptOption.LAUNCH_WITH_GAMENATIVE)) }
        runCurrent()

        assertTrue(tracker.progress.value?.step is LaunchStep.ChooseLauncher)
        assertEquals(7_000L, tracker.progress.value?.stepStartedAtMs)
        tracker.cancel()
    }

    @Test
    fun `a server that did not answer stays on the launch until its game opens`() = runTest {
        val tracker = timedTracker()
        val ticket = tracker.begin(1L, "Game")!!

        ticket.step(LaunchStep.Save(SyncProgress.PreLaunch.Connecting(null, success = false)))
        assertEquals(LaunchFailure.SERVER_UNREACHABLE, tracker.progress.value?.failure)
        ticket.step(LaunchStep.PreparingUi)
        assertEquals(LaunchFailure.SERVER_UNREACHABLE, tracker.progress.value?.failure)

        now = 9_000L
        tracker.finish(ticket, launched = true)

        assertEquals(LaunchStep.Launching, tracker.progress.value?.step)
        assertEquals(LaunchFailure.SERVER_UNREACHABLE, tracker.progress.value?.failure)
        assertEquals(9_000L, tracker.progress.value?.stepStartedAtMs)
    }

    @Test
    fun `a failed save download stays on the launch`() = runTest {
        val tracker = timedTracker()
        val ticket = tracker.begin(1L, "Game")!!

        ticket.step(LaunchStep.Save(SyncProgress.Error("download failed")))
        ticket.step(LaunchStep.Save(SyncProgress.PreLaunch.Launching(null)))
        ticket.step(LaunchStep.DownloadingCore(fraction = 0.25f))

        assertEquals(LaunchFailure.SAVE_DOWNLOAD_FAILED, tracker.progress.value?.failure)
        assertEquals(LaunchFailure.SAVE_DOWNLOAD_FAILED, ticket.failure)
    }

    @Test
    fun `a new launch starts without the last launch's failure`() = runTest {
        val tracker = timedTracker()
        val first = tracker.begin(1L, "First")!!
        first.step(LaunchStep.Save(SyncProgress.Error("download failed")))
        tracker.finish(first, launched = true)

        val second = tracker.begin(2L, "Second")!!
        second.step(LaunchStep.PreparingUi)

        assertNull(second.failure)
        assertNull(tracker.progress.value?.failure)
    }

    @Test
    fun `a launch is live from the moment it begins, before any step`() = runTest {
        val tracker = tracker()
        assertFalse(tracker.launchLive.value)

        val ticket = tracker.begin(1L, "Game")!!
        assertNull(tracker.progress.value)
        assertTrue(tracker.launchLive.value)

        ticket.step(LaunchStep.PreparingUi)
        assertTrue(tracker.launchLive.value)
    }

    @Test
    fun `a launch that opened its game stays live until the screen that started it stops`() = runTest {
        val tracker = tracker()
        val ticket = tracker.begin(1L, "Game")!!

        tracker.finish(ticket, launched = true)
        assertTrue(tracker.launchLive.value)

        tracker.hostStopped()
        assertFalse(tracker.launchLive.value)
    }

    @Test
    fun `a launch whose screen never stops is no longer live once its hold runs out`() = runTest {
        val tracker = tracker()
        val ticket = tracker.begin(1L, "Game")!!
        tracker.finish(ticket, launched = true)

        advanceTimeBy(10_000)

        assertFalse(tracker.launchLive.value)
    }

    @Test
    fun `a launch that failed is no longer live`() = runTest {
        val tracker = tracker()
        val ticket = tracker.begin(1L, "Game")!!

        tracker.finish(ticket, launched = false)

        assertFalse(tracker.launchLive.value)
    }

    @Test
    fun `a game screen that never opens ends the live launch`() = runTest {
        val tracker = tracker()
        val ticket = tracker.begin(1L, "Game")!!
        tracker.finish(ticket, launched = true)

        tracker.openFailed(ticket)

        assertFalse(tracker.launchLive.value)
    }

    @Test
    fun `a cancelled launch is no longer live, and the next one is`() = runTest {
        val tracker = tracker()
        val cancelled = tracker.begin(1L, "First")!!

        tracker.cancel()
        assertFalse(tracker.launchLive.value)

        val next = async { tracker.begin(2L, "Second") }
        runCurrent()
        assertTrue(tracker.launchLive.value)

        tracker.finish(cancelled, launched = false)
        next.await()!!
        assertTrue(tracker.launchLive.value)
    }

    @Test
    fun `a launch cancelled while it waits for the last one is not live`() = runTest {
        val tracker = tracker()
        val first = tracker.begin(1L, "First")!!
        tracker.cancel()
        val second = async { tracker.begin(2L, "Second") }
        runCurrent()

        tracker.cancel()
        tracker.finish(first, launched = false)

        assertNull(second.await())
        assertFalse(tracker.launchLive.value)
    }

    @Test
    fun `a launch reports its game id with its steps`() = runTest {
        val tracker = timedTracker()
        val ticket = tracker.begin(42L, "Game")!!

        ticket.step(LaunchStep.PreparingSystemFiles)
        assertEquals(42L, tracker.progress.value?.gameId)

        tracker.finish(ticket, launched = true)
        assertEquals(42L, tracker.progress.value?.gameId)
    }
}
