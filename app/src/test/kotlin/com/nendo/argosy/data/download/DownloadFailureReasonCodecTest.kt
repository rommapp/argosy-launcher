package com.nendo.argosy.data.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The `errorReason` column outlives the build that wrote it, so this pins the two things an
 * upgrade depends on: a token this build writes decodes back to the same reason, and a row
 * written by an older build still decodes to something the retry path can act on.
 */
class DownloadFailureReasonCodecTest {

    private fun roundTrip(reason: DownloadFailureReason): DownloadFailureReason? =
        DownloadFailureReasonCodec.decode(DownloadFailureReasonCodec.encode(reason))

    @Test
    fun `every reason survives a round trip`() {
        val reasons = listOf(
            DownloadFailureReason.FileTooSmall,
            DownloadFailureReason.InvalidContentType("image/png"),
            DownloadFailureReason.ServerError("500 Internal Server Error"),
            DownloadFailureReason.Unexpected("boom"),
            DownloadFailureReason.MoveToStorageFailed,
            DownloadFailureReason.MovedFileNotFound,
            DownloadFailureReason.DownloadedFileMissing,
            DownloadFailureReason.NoDownloadEntryFound,
            DownloadFailureReason.DownloadedFileNoLongerExists,
            DownloadFailureReason.ExtractionFailed("bad central directory")
        )
        reasons.forEach { assertEquals(it, roundTrip(it)) }
    }

    @Test
    fun `a colon inside dynamic text is not read as a field boundary`() {
        val message = "500: upstream said no: retry later"
        assertEquals(
            DownloadFailureReason.ServerError(message),
            roundTrip(DownloadFailureReason.ServerError(message))
        )
    }

    @Test
    fun `a legacy extraction failure still resumes extraction rather than restarting`() {
        val legacy = "Extraction failed: /data/roms/game.zip does not exist"
        val decoded = DownloadFailureReasonCodec.decode(legacy)
        assertTrue(
            "a pre-upgrade extraction failure must still be recognised as one",
            decoded is DownloadFailureReason.ExtractionFailed
        )
    }

    @Test
    fun `other legacy prose is kept verbatim rather than discarded`() {
        val legacy = "Server returned 404"
        assertEquals(
            DownloadFailureReason.LegacyRaw(legacy),
            DownloadFailureReasonCodec.decode(legacy)
        )
    }

    @Test
    fun `an absent reason stays absent`() {
        assertEquals(null, DownloadFailureReasonCodec.decode(null))
        assertEquals(null, DownloadFailureReasonCodec.decode(""))
    }
}
