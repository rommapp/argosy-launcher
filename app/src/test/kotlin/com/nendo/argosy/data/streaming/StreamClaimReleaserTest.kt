package com.nendo.argosy.data.streaming

import com.nendo.argosy.data.remote.romm.RomMApi
import com.nendo.argosy.data.remote.romm.RomMConnectionManager
import com.nendo.argosy.data.remote.romm.RomMStreamReleaseResponse
import com.nendo.argosy.data.remote.romm.RomMStreamSaveAndExitResponse
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.Response
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class StreamClaimReleaserTest {
    private val api: RomMApi = mockk(relaxed = true)
    private val connectionManager: RomMConnectionManager = mockk(relaxed = true) {
        every { getApi() } returns api
    }
    private val claim = StreamClaim(platform = "psx", containerKey = "box-1", claimedAt = "2026-11-13T09:00:00+00:00")
    private val json = "application/json".toMediaType()

    private fun TestScope.releaser() = StreamClaimReleaser(connectionManager, StandardTestDispatcher(testScheduler))

    @Test
    fun `a save and exit that frees the container is a release`() = runTest {
        coEvery { api.saveAndExitStreamSession("psx", any(), "box-1", claim.claimedAt) } returns
            Response.success(RomMStreamSaveAndExitResponse(status = "ok", saved = true, platform = "psx", released = true))

        assertEquals(StreamReleaseOutcome.RELEASED, releaser().release(claim))
        coVerify(exactly = 0) { api.releaseStreamSession(any(), any(), any(), any()) }
    }

    @Test
    fun `a save and exit answering not found means RomM already ended the session`() = runTest {
        coEvery { api.saveAndExitStreamSession("psx", any(), "box-1", claim.claimedAt) } returns
            Response.success(RomMStreamSaveAndExitResponse(status = RomMStreamSaveAndExitResponse.STATUS_NOT_FOUND, released = true))

        assertEquals(StreamReleaseOutcome.ALREADY_ENDED, releaser().release(claim))
        coVerify(exactly = 0) { api.releaseStreamSession(any(), any(), any(), any()) }
    }

    @Test
    fun `a failed save and exit falls back to a release`() = runTest {
        coEvery { api.saveAndExitStreamSession(any(), any(), any(), any()) } throws IOException("timeout")
        coEvery { api.releaseStreamSession("psx", "box-1", claim.claimedAt, true) } returns
            Response.success(RomMStreamReleaseResponse(status = "released", platform = "psx"))

        assertEquals(StreamReleaseOutcome.RELEASED, releaser().release(claim))
        coVerify(exactly = 1) { api.releaseStreamSession("psx", "box-1", claim.claimedAt, true) }
    }

    @Test
    fun `a save and exit that kept the container falls back to a release`() = runTest {
        coEvery { api.saveAndExitStreamSession(any(), any(), any(), any()) } returns
            Response.success(RomMStreamSaveAndExitResponse(status = "ok", saved = true, platform = "psx", released = false))
        coEvery { api.releaseStreamSession(any(), any(), any(), any()) } returns
            Response.success(RomMStreamReleaseResponse(status = "released", platform = "psx"))

        assertEquals(StreamReleaseOutcome.RELEASED, releaser().release(claim))
    }

    @Test
    fun `a release answering not found means RomM already ended the session`() = runTest {
        coEvery { api.saveAndExitStreamSession(any(), any(), any(), any()) } returns
            Response.error(500, "{}".toResponseBody(json))
        coEvery { api.releaseStreamSession(any(), any(), any(), any()) } returns
            Response.success(RomMStreamReleaseResponse(status = "not_found", platform = "psx"))

        assertEquals(StreamReleaseOutcome.ALREADY_ENDED, releaser().release(claim))
    }

    @Test
    fun `both calls failing is a failed release`() = runTest {
        coEvery { api.saveAndExitStreamSession(any(), any(), any(), any()) } throws IOException("down")
        coEvery { api.releaseStreamSession(any(), any(), any(), any()) } throws IOException("down")

        assertEquals(StreamReleaseOutcome.FAILED, releaser().release(claim))
    }

    @Test
    fun `no RomM connection after a connect attempt is a failed release`() = runTest {
        every { connectionManager.getApi() } returns null

        assertEquals(StreamReleaseOutcome.FAILED, releaser().release(claim))
        coVerify(exactly = 1) { connectionManager.initialize(any()) }
    }
}
