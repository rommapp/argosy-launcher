package com.nendo.argosy.data.streaming

import com.nendo.argosy.data.remote.romm.RomMStreamBusyDetail
import com.nendo.argosy.data.remote.romm.RomMStreamCardImportDetail
import com.nendo.argosy.data.remote.romm.RomMStreamCardSummary
import com.nendo.argosy.data.remote.romm.RomMStreamJoinableSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StreamClaimErrorsTest {

    @Test
    fun `409 names the game holding the container and since when`() {
        val outcome = StreamClaimErrors.classify(
            status = 409,
            busy = RomMStreamBusyDetail(message = "Session in use", romName = "Chrono Cross", claimedAt = "2026-11-13T09:00:00+00:00"),
            card = null,
            detailText = null
        )
        assertEquals(StreamClaimOutcome.Busy("Chrono Cross", "2026-11-13T09:00:00+00:00", draining = false), outcome)
    }

    @Test
    fun `409 while the previous session drains says so`() {
        val outcome = StreamClaimErrors.classify(409, RomMStreamBusyDetail(draining = true), null, null)
        assertEquals(StreamClaimOutcome.Busy(null, null, draining = true), outcome)
    }

    @Test
    fun `409 without a readable body is still a busy container`() {
        assertEquals(StreamClaimOutcome.Busy(null, null, draining = false), StreamClaimErrors.classify(409, null, null, null))
    }

    @Test
    fun `428 with a found card asks to import or start fresh`() {
        val card = RomMStreamCardImportDetail(
            code = RomMStreamCardImportDetail.CODE_IMPORT_REQUIRED,
            outcome = RomMStreamCardImportDetail.OUTCOME_FOUND,
            summary = RomMStreamCardSummary(fileCount = 3, totalBytes = 1024, gameCodes = listOf("SLUS-01234"))
        )
        assertEquals(
            StreamClaimOutcome.CardDecision(unreadable = false, fileCount = 3, gameCodes = listOf("SLUS-01234"), reason = null),
            StreamClaimErrors.classify(428, null, card, null)
        )
    }

    @Test
    fun `428 with an unreadable card offers only starting fresh`() {
        val card = RomMStreamCardImportDetail(
            code = RomMStreamCardImportDetail.CODE_IMPORT_REQUIRED,
            outcome = RomMStreamCardImportDetail.OUTCOME_UNREADABLE,
            reason = "broker timeout"
        )
        assertEquals(
            StreamClaimOutcome.CardDecision(unreadable = true, fileCount = 0, gameCodes = emptyList(), reason = "broker timeout"),
            StreamClaimErrors.classify(428, null, card, null)
        )
    }

    @Test
    fun `428 without the memory card code is a plain failure`() {
        assertEquals(StreamClaimOutcome.Failed(428), StreamClaimErrors.classify(428, null, null, null))
        assertEquals(
            StreamClaimOutcome.Failed(428),
            StreamClaimErrors.classify(428, null, RomMStreamCardImportDetail(code = "other"), null)
        )
    }

    @Test
    fun `403 is a group without Streaming write`() {
        assertEquals(StreamClaimOutcome.StartRefused, StreamClaimErrors.classify(403, null, null, "Forbidden"))
    }

    @Test
    fun `400 carries RomM's reason`() {
        assertEquals(
            StreamClaimOutcome.Refused("This container cannot restore the picked save"),
            StreamClaimErrors.classify(400, null, null, "This container cannot restore the picked save")
        )
    }

    @Test
    fun `404 and anything else`() {
        assertEquals(StreamClaimOutcome.NotFound, StreamClaimErrors.classify(404, null, null, null))
        assertEquals(StreamClaimOutcome.Failed(503), StreamClaimErrors.classify(503, null, null, null))
    }

    @Test
    fun `join is offered only for a joinable session of the same rom`() {
        val other = RomMStreamJoinableSession(container = "box-a", platform = "psx", romId = 7)
        val same = RomMStreamJoinableSession(container = "box-b", platform = "psx", romId = 42)
        val noPlatform = RomMStreamJoinableSession(container = "box-c", platform = null, romId = 42)
        assertEquals(same, StreamBusyChoice.joinTarget(listOf(other, noPlatform, same), romId = 42))
        assertNull(StreamBusyChoice.joinTarget(listOf(other, noPlatform), romId = 42))
        assertNull(StreamBusyChoice.joinTarget(emptyList(), romId = 42))
    }
}
