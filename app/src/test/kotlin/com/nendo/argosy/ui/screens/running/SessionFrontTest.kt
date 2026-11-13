package com.nendo.argosy.ui.screens.running

import com.nendo.argosy.data.emulator.ActiveSession
import com.nendo.argosy.data.streaming.StreamClaim
import com.nendo.argosy.data.streaming.StreamSessionManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class SessionFrontTest {

    @Test
    fun `an external session is front for its emulator`() {
        val session = ActiveSession(gameId = 3L, startTime = Instant.EPOCH, emulatorPackage = "org.ppsspp.ppsspp")

        assertTrue(isSessionFront(session, "org.ppsspp.ppsspp", 3L, null))
    }

    @Test
    fun `a stream session is never the front task`() {
        val session = ActiveSession(
            gameId = 3L,
            startTime = Instant.EPOCH,
            emulatorPackage = StreamSessionManager.STREAM_PACKAGE,
            streamClaim = StreamClaim("psx", "box-1", "2026-11-13T09:00:00+00:00")
        )

        assertFalse(isSessionFront(session, StreamSessionManager.STREAM_PACKAGE, 3L, null))
    }
}
