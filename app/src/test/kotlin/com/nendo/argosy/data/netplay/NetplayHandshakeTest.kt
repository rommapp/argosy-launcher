package com.nendo.argosy.data.netplay

import com.nendo.argosy.data.social.ArgosSocialService
import com.nendo.argosy.data.social.NetplayCandidate
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NetplayHandshakeTest {

    @Test
    fun canConstruct() {
        val handshake = NetplayHandshake(
            candidateGatherer = CandidateGatherer(),
            socialService = mockk<ArgosSocialService>(relaxed = true)
        )
        assertNotNull(handshake)
    }

    @Test
    fun qualityWarningThresholdConstants() {
        val success = simulatedResult(rttMs = 150, jitterMs = 10)
        assertTrue("RTT 150ms should be Success", success is NetplayHandshake.HandshakeResult.Success)

        val warning = simulatedResult(rttMs = 250, jitterMs = 10)
        assertTrue("RTT 250ms should be QualityWarning", warning is NetplayHandshake.HandshakeResult.QualityWarning)
        val w = warning as NetplayHandshake.HandshakeResult.QualityWarning
        assertEquals(250, w.measuredRttMs)
        assertEquals(10, w.measuredJitterMs)
        assertEquals("Bad", w.ratingLabel)

        val rejected = simulatedResult(rttMs = 350, jitterMs = 10)
        assertTrue("RTT 350ms should be Failure", rejected is NetplayHandshake.HandshakeResult.Failure)
        assertEquals("quality_rejected", (rejected as NetplayHandshake.HandshakeResult.Failure).reason)

        val jitterReject = simulatedResult(rttMs = 100, jitterMs = 60)
        assertTrue("Jitter 60ms should be Failure", jitterReject is NetplayHandshake.HandshakeResult.Failure)
    }

    @Test
    fun qualityWarningBoundaryAt200ms() {
        val at199 = simulatedResult(rttMs = 199, jitterMs = 10)
        assertTrue("RTT 199ms should be Success", at199 is NetplayHandshake.HandshakeResult.Success)

        val at200 = simulatedResult(rttMs = 200, jitterMs = 10)
        assertTrue("RTT 200ms should be Success (not >200)", at200 is NetplayHandshake.HandshakeResult.Success)

        val at201 = simulatedResult(rttMs = 201, jitterMs = 10)
        assertTrue("RTT 201ms should be QualityWarning", at201 is NetplayHandshake.HandshakeResult.QualityWarning)
    }

    @Test
    fun qualityWarningBoundaryAt300ms() {
        val at300 = simulatedResult(rttMs = 300, jitterMs = 10)
        assertTrue("RTT 300ms should be QualityWarning", at300 is NetplayHandshake.HandshakeResult.QualityWarning)

        val at301 = simulatedResult(rttMs = 301, jitterMs = 10)
        assertTrue("RTT 301ms should be Failure", at301 is NetplayHandshake.HandshakeResult.Failure)
    }

    companion object {
        private const val QUALITY_WARN_RTT_MS = 200
        private const val QUALITY_MAX_RTT_MS = 300
        private const val QUALITY_MAX_JITTER_MS = 50

        private fun simulatedResult(rttMs: Int, jitterMs: Int): NetplayHandshake.HandshakeResult {
            if (rttMs > QUALITY_MAX_RTT_MS || jitterMs > QUALITY_MAX_JITTER_MS) {
                return NetplayHandshake.HandshakeResult.Failure("quality_rejected")
            }
            if (rttMs > QUALITY_WARN_RTT_MS) {
                return NetplayHandshake.HandshakeResult.QualityWarning(
                    latchedCandidate = NetplayCandidate(type = "stun", address = "1.2.3.4", port = 1234),
                    transport = mockk(relaxed = true),
                    peerAddress = mockk(relaxed = true),
                    measuredRttMs = rttMs,
                    measuredJitterMs = jitterMs,
                    ratingLabel = "Bad"
                )
            }
            return NetplayHandshake.HandshakeResult.Success(
                latchedCandidate = NetplayCandidate(type = "stun", address = "1.2.3.4", port = 1234),
                transport = mockk(relaxed = true),
                peerAddress = mockk(relaxed = true),
                measuredRttMs = rttMs,
                measuredJitterMs = jitterMs
            )
        }
    }
}
