package com.nendo.argosy.data.sync.strategy

import com.nendo.argosy.data.remote.romm.RomMApi
import com.nendo.argosy.data.remote.romm.RomMConnectionManager
import com.nendo.argosy.data.remote.romm.RomMSyncNegotiateResponse
import com.nendo.argosy.data.remote.romm.RomMSyncOperation
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import retrofit2.Response

class NegotiatorSaveSyncStrategyTest {

    private val api: RomMApi = mockk()
    private val connectionManager: RomMConnectionManager = mockk {
        every { getApi() } returns api
        every { getDeviceId() } returns "dev-1"
    }
    private val strategy = NegotiatorSaveSyncStrategy(connectionManager)

    private fun inventory(romId: Long = 1L, hash: String = "h1") = listOf(
        LocalSaveState(
            romId = romId,
            fileName = "save.sav",
            slot = null,
            emulator = "mgba",
            contentHash = hash,
            updatedAt = "2026-05-15T12:00:00Z",
            fileSizeBytes = 1024
        )
    )

    @Test
    fun `maps server response to ReconcilePlan with sessionId`() = runTest {
        coEvery { api.negotiateSync(any()) } returns Response.success(
            RomMSyncNegotiateResponse(
                sessionId = 77L,
                operations = listOf(
                    RomMSyncOperation(action = "upload", romId = 1L, fileName = "a.sav", reason = "client only"),
                    RomMSyncOperation(action = "download", romId = 2L, fileName = "b.sav", reason = "server newer", saveId = 99L),
                    RomMSyncOperation(action = "conflict", romId = 3L, fileName = "c.sav", reason = "both changed"),
                    RomMSyncOperation(action = "no_op", romId = 4L, fileName = "d.sav", reason = "untracked"),
                    RomMSyncOperation(action = "unknown_future_action", romId = 5L, fileName = "e.sav", reason = "ignore")
                ),
                totalUpload = 1, totalDownload = 1, totalConflict = 1, totalNoOp = 2
            )
        )

        val plan = strategy.planReconcile(inventory())

        assertEquals(77L, plan.sessionId)
        assertEquals(5, plan.operations.size)
        assertEquals(ReconcileAction.UPLOAD, plan.operations[0].action)
        assertEquals(ReconcileAction.DOWNLOAD, plan.operations[1].action)
        assertEquals(99L, plan.operations[1].saveId)
        assertEquals(ReconcileAction.CONFLICT, plan.operations[2].action)
        assertEquals(ReconcileAction.NO_OP, plan.operations[3].action)
        assertEquals(ReconcileAction.NO_OP, plan.operations[4].action)
    }

    @Test
    fun `returns EMPTY when api is null`() = runTest {
        every { connectionManager.getApi() } returns null

        val plan = strategy.planReconcile(inventory())

        assertSame(ReconcilePlan.EMPTY, plan)
    }

    @Test
    fun `returns EMPTY when deviceId is null`() = runTest {
        every { connectionManager.getDeviceId() } returns null

        val plan = strategy.planReconcile(inventory())

        assertSame(ReconcilePlan.EMPTY, plan)
    }

    @Test
    fun `returns EMPTY on non-2xx response`() = runTest {
        coEvery { api.negotiateSync(any()) } returns Response.error(
            500,
            okhttp3.ResponseBody.create(null, "")
        )

        val plan = strategy.planReconcile(inventory())

        assertSame(ReconcilePlan.EMPTY, plan)
    }

    @Test
    fun `returns EMPTY on network exception`() = runTest {
        coEvery { api.negotiateSync(any()) } throws RuntimeException("boom")

        val plan = strategy.planReconcile(inventory())

        assertSame(ReconcilePlan.EMPTY, plan)
    }

    @Test
    fun `sends deviceId and inventory in payload`() = runTest {
        val payloadSlot = slot<com.nendo.argosy.data.remote.romm.RomMSyncNegotiatePayload>()
        coEvery { api.negotiateSync(capture(payloadSlot)) } returns Response.success(
            RomMSyncNegotiateResponse(sessionId = 1L, operations = emptyList())
        )

        strategy.planReconcile(inventory(romId = 42L, hash = "abc"))

        assertEquals("dev-1", payloadSlot.captured.deviceId)
        assertEquals(1, payloadSlot.captured.saves.size)
        assertEquals(42L, payloadSlot.captured.saves[0].romId)
        assertEquals("abc", payloadSlot.captured.saves[0].contentHash)
    }

    @Test
    fun `completeSession posts counts`() = runTest {
        coEvery { api.completeSyncSession(any(), any()) } returns Response.success(
            com.nendo.argosy.data.remote.romm.RomMSyncCompleteResponse(
                session = com.nendo.argosy.data.remote.romm.RomMSyncSession(
                    id = 5L, deviceId = "dev-1", userId = 1L, status = "completed"
                )
            )
        )

        val result = strategy.completeSession(5L, operationsCompleted = 3, operationsFailed = 1)

        assertEquals(true, result.isSuccess)
        coVerify { api.completeSyncSession(5L, match { it.operationsCompleted == 3 && it.operationsFailed == 1 }) }
    }
}
