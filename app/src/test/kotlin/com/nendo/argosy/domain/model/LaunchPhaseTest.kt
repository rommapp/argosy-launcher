package com.nendo.argosy.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LaunchPhaseTest {

    private val recap = LaunchRecap(gameTitle = "Game", lastSession = null, unlocks = emptyList())

    private val localModified = SyncProgress.LocalModified(gameId = 1L, localSavePath = "/save", channelName = null)

    private val hardcoreConflict = SyncProgress.HardcoreConflict(
        gameId = 1L,
        gameName = "Game",
        tempFilePath = "/tmp/save",
        emulatorId = "emulator",
        targetPath = "/save",
        isFolderBased = false,
        channelName = null
    )

    private val flags = listOf(null, true, false)

    private fun preLaunchValues(): List<SyncProgress.PreLaunch> = flags.flatMap { flag ->
        listOf(
            SyncProgress.PreLaunch.CheckingSave(null, found = flag),
            SyncProgress.PreLaunch.Connecting(null, success = flag),
            SyncProgress.PreLaunch.Downloading(null, success = flag),
            SyncProgress.PreLaunch.Writing(null, success = flag)
        )
    } + SyncProgress.PreLaunch.Launching(null) + SyncProgress.PreLaunch.TimedOut(null)

    private fun otherSyncValues(): List<SyncProgress> = listOf(
        SyncProgress.Idle,
        SyncProgress.Skipped,
        SyncProgress.Error("download failed"),
        hardcoreConflict,
        localModified,
        SyncProgress.BlockedReason.PermissionRequired(),
        SyncProgress.BlockedReason.AccessDenied()
    )

    private fun everySaveStep(): List<LaunchStep.Save> = (preLaunchValues() + otherSyncValues()).map { LaunchStep.Save(it) }

    private fun everyStep(): List<LaunchStep> = listOf(
        LaunchStep.FinishingPrevious,
        LaunchStep.RecapLoading,
        LaunchStep.Recap(recap),
        LaunchStep.Prompt(localModified, listOf(LaunchPromptOption.APPLY_LOCAL)),
        LaunchStep.ChooseLauncher(listOf(LaunchPromptOption.LAUNCH_WITH_GAMENATIVE)),
        LaunchStep.DownloadingCore(null),
        LaunchStep.DownloadingCore(0.5f),
        LaunchStep.DownloadingCore(0.5f, totalBytes = 4_000_000L),
        LaunchStep.DownloadingCore(null, totalBytes = 4_000_000L),
        LaunchStep.Starting,
        LaunchStep.PreparingSystemFiles,
        LaunchStep.PreparingUi,
        LaunchStep.Launching
    ) + everySaveStep()

    @Test
    fun `finishing the previous launch is its own phase`() {
        assertEquals(LaunchPhase.PREVIOUS, LaunchStep.FinishingPrevious.phase)
    }

    @Test
    fun `loading the recap is its own phase`() {
        assertEquals(LaunchPhase.RECAP_LOADING, LaunchStep.RecapLoading.phase)
    }

    @Test
    fun `the recap and every prompt wait for an answer`() {
        assertEquals(LaunchPhase.QUESTION, LaunchStep.Recap(recap).phase)
        assertEquals(LaunchPhase.QUESTION, LaunchStep.Prompt(localModified, listOf(LaunchPromptOption.APPLY_LOCAL)).phase)
        assertEquals(LaunchPhase.QUESTION, LaunchStep.Prompt(hardcoreConflict, listOf(LaunchPromptOption.KEEP_HARDCORE)).phase)
        assertEquals(LaunchPhase.QUESTION, LaunchStep.ChooseLauncher(listOf(LaunchPromptOption.LAUNCH_WITH_DROIDDECK)).phase)
    }

    @Test
    fun `every pre-launch sync value is the save phase whatever its flag`() {
        preLaunchValues().forEach { progress ->
            assertEquals(progress.toString(), LaunchPhase.SAVE, LaunchStep.Save(progress).phase)
        }
    }

    @Test
    fun `every other sync value is the save phase`() {
        otherSyncValues().forEach { progress ->
            assertEquals(progress.toString(), LaunchPhase.SAVE, LaunchStep.Save(progress).phase)
        }
    }

    @Test
    fun `a core download is the core phase with or without a fraction`() {
        assertEquals(LaunchPhase.CORE, LaunchStep.DownloadingCore(null).phase)
        assertEquals(LaunchPhase.CORE, LaunchStep.DownloadingCore(0.25f).phase)
    }

    @Test
    fun `preparing system files and the UI share the prepare phase`() {
        assertEquals(LaunchPhase.PREPARE, LaunchStep.PreparingSystemFiles.phase)
        assertEquals(LaunchPhase.PREPARE, LaunchStep.PreparingUi.phase)
    }

    @Test
    fun `starting after the recap is the prepare phase`() {
        assertEquals(LaunchPhase.PREPARE, LaunchStep.Starting.phase)
    }

    @Test
    fun `launching is the last phase`() {
        assertEquals(LaunchPhase.LAUNCHING, LaunchStep.Launching.phase)
    }

    @Test
    fun `a server that did not answer is the connecting step's only failure`() {
        assertEquals(LaunchFailure.SERVER_UNREACHABLE, LaunchStep.Save(SyncProgress.PreLaunch.Connecting(null, success = false)).failure)
        assertNull(LaunchStep.Save(SyncProgress.PreLaunch.Connecting(null, success = null)).failure)
        assertNull(LaunchStep.Save(SyncProgress.PreLaunch.Connecting(null, success = true)).failure)
    }

    @Test
    fun `a save that could not be written is the writing step's only failure`() {
        assertEquals(LaunchFailure.SAVE_WRITE_FAILED, LaunchStep.Save(SyncProgress.PreLaunch.Writing(null, success = false)).failure)
        assertNull(LaunchStep.Save(SyncProgress.PreLaunch.Writing(null, success = null)).failure)
        assertNull(LaunchStep.Save(SyncProgress.PreLaunch.Writing(null, success = true)).failure)
    }

    @Test
    fun `a sync with no answer in time is a slow server`() {
        val timedOut = LaunchStep.Save(SyncProgress.PreLaunch.TimedOut(null))
        assertEquals(LaunchPhase.SAVE, timedOut.phase)
        assertEquals(LaunchFailure.SERVER_TIMED_OUT, timedOut.failure)
        assertNull(timedOut.fraction)
        assertNull(timedOut.sizeBytes)
    }

    @Test
    fun `a sync error is a failed save download`() {
        assertEquals(LaunchFailure.SAVE_DOWNLOAD_FAILED, LaunchStep.Save(SyncProgress.Error("download failed")).failure)
    }

    @Test
    fun `a download that did not succeed reports no failure of its own`() {
        flags.forEach { flag ->
            assertNull(LaunchStep.Save(SyncProgress.PreLaunch.Downloading(null, success = flag)).failure)
        }
    }

    @Test
    fun `no step outside those four carries a failure`() {
        val failing: Set<LaunchStep> = setOf(
            LaunchStep.Save(SyncProgress.PreLaunch.Connecting(null, success = false)),
            LaunchStep.Save(SyncProgress.PreLaunch.Writing(null, success = false)),
            LaunchStep.Save(SyncProgress.PreLaunch.TimedOut(null)),
            LaunchStep.Save(SyncProgress.Error("download failed"))
        )
        everyStep().filterNot { it in failing }.forEach { step ->
            assertNull(step.toString(), step.failure)
        }
    }

    @Test
    fun `only a core download with a known fraction is sized`() {
        val sized = LaunchStep.DownloadingCore(0.5f)
        val sizedWithBytes = LaunchStep.DownloadingCore(0.5f, totalBytes = 4_000_000L)
        assertEquals(0.5f, sized.fraction)
        assertEquals(0.5f, sizedWithBytes.fraction)
        everyStep().filterNot { it == sized || it == sizedWithBytes }.forEach { step ->
            assertNull(step.toString(), step.fraction)
        }
    }

    @Test
    fun `a core download reports its byte size only when the server gave one`() {
        assertEquals(4_000_000L, LaunchStep.DownloadingCore(0.5f, totalBytes = 4_000_000L).sizeBytes)
        assertEquals(4_000_000L, LaunchStep.DownloadingCore(null, totalBytes = 4_000_000L).sizeBytes)
        assertNull(LaunchStep.DownloadingCore(0.5f).sizeBytes)
        assertNull(LaunchStep.DownloadingCore(0.5f, totalBytes = -1L).sizeBytes)
        assertNull(LaunchStep.DownloadingCore(0.5f, totalBytes = 0L).sizeBytes)
    }

    @Test
    fun `no step outside a core download carries a byte size`() {
        everyStep().filterNot { it is LaunchStep.DownloadingCore }.forEach { step ->
            assertNull(step.toString(), step.sizeBytes)
        }
    }
}
