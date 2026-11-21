package com.nendo.argosy.data.sync.snapshot

import com.nendo.argosy.data.repository.SaveSyncResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

class SnapshotSyncRouterTest {

    private val engine = mockk<SnapshotSyncEngine>()
    private val activeSaves = mockk<com.nendo.argosy.data.repository.ActiveSaveRepository>()
    private val router = SnapshotSyncRouter(engine, activeSaves)

    @Test
    fun `a launch with no channel asked for syncs the active channel on a snapshot server`() = runBlocking {
        coEvery { engine.isEligible(5) } returns true
        coEvery { activeSaves.getActiveChannel(5) } returns "speedrun"

        org.junit.Assert.assertEquals("speedrun", router.launchChannel(5, null))
        org.junit.Assert.assertEquals("slot1", router.launchChannel(5, "slot1"))
    }

    @Test
    fun `a hardcore session keeps its channel on a snapshot server and drops it on an older one`() = runBlocking {
        coEvery { engine.isEligible(5) } returns true
        coEvery { engine.isEligible(6) } returns false

        org.junit.Assert.assertEquals("speedrun", router.sessionChannel(5, isHardcore = true, activeChannel = "speedrun"))
        org.junit.Assert.assertEquals(null, router.sessionChannel(6, isHardcore = true, activeChannel = "speedrun"))
        org.junit.Assert.assertEquals("speedrun", router.sessionChannel(6, isHardcore = false, activeChannel = "speedrun"))
    }

    @Test
    fun `an empty channel with no local save hands the launch to the server-save negotiation`() = runBlocking {
        coEvery { engine.isEligible(5) } returns true
        coEvery { engine.sync(5, "argosy", null, false) } returns SnapshotSyncResult.Empty

        org.junit.Assert.assertEquals(null, router.preLaunch(5, "argosy", null))
    }

    @Test
    fun `a download from an empty channel falls back to the server-save downloader`() = runBlocking {
        coEvery { engine.isEligible(5) } returns true
        coEvery { engine.keepServer(5, "argosy", null) } returns SnapshotSyncResult.Empty

        org.junit.Assert.assertEquals(null, router.download(5, "argosy", null))
    }

    @Test
    fun `a launch on an older server keeps syncing autosave`() = runBlocking {
        coEvery { engine.isEligible(5) } returns false

        org.junit.Assert.assertEquals(null, router.launchChannel(5, null))
    }

    @Test
    fun `a refused softcore push over a hardcore current parks as a hardcore conflict`() = runBlocking {
        coEvery { engine.isEligible(5) } returns true
        coEvery { engine.sync(5, "argosy", null, false) } returns SnapshotSyncResult.HardcoreDowngrade(41)

        val result = router.upload(5, "argosy", null, keepLocal = false, isHardcore = false)

        assertTrue((result as SaveSyncResult.Conflict).isHardcoreDowngrade)
    }

    @Test
    fun `approving the downgrade pushes the local save with the approval`() = runBlocking {
        coEvery { engine.isEligible(5) } returns true
        coEvery { engine.keepLocal(5, "argosy", null, approveHardcoreDowngrade = true) } returns SnapshotSyncResult.Pushed(42)

        router.approveHardcoreDowngrade(5, "argosy", null)

        coVerify { engine.keepLocal(5, "argosy", null, false, true) }
    }

    @Test
    fun `a pre-launch snapshot conflict asks with the snapshot choices`() = runBlocking {
        coEvery { engine.isEligible(5) } returns true
        coEvery { engine.sync(5, "argosy", null) } returns SnapshotSyncResult.Conflict(null, 43, "/saves/lunar.srm")

        val result = router.preLaunch(5, "argosy", null)

        val modified = result as com.nendo.argosy.data.repository.PreLaunchSyncResult.LocalModified
        assertTrue(modified.snapshotConflict)
        org.junit.Assert.assertEquals("/saves/lunar.srm", modified.localSavePath)
    }
}
