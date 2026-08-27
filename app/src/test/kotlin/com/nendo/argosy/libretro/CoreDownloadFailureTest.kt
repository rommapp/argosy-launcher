package com.nendo.argosy.libretro

import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The download path used to recognise its own failures by reading the wording of the exception
 * messages it wrote, so rephrasing one would have downgraded a known failure to an unknown one
 * with no compiler or test complaint.
 */
class CoreDownloadFailureTest {

    @Test
    fun `failures we raise ourselves travel as a type`() {
        val corrupted = CoreDownloadException(
            CoreDownloadFailure.Corrupted,
            "any wording at all"
        )
        assertEquals(CoreDownloadFailure.Corrupted, classifyCoreDownloadFailure(corrupted))

        val notPublished = CoreDownloadException(
            CoreDownloadFailure.NotPublished,
            "reworded tomorrow"
        )
        assertEquals(CoreDownloadFailure.NotPublished, classifyCoreDownloadFailure(notPublished))
    }

    @Test
    fun `network failures are recognised by exception type`() {
        assertEquals(
            CoreDownloadFailure.TimedOut,
            classifyCoreDownloadFailure(SocketTimeoutException("whatever"))
        )
        assertEquals(
            CoreDownloadFailure.Offline,
            classifyCoreDownloadFailure(UnknownHostException("whatever"))
        )
        assertEquals(
            CoreDownloadFailure.Offline,
            classifyCoreDownloadFailure(ConnectException("whatever"))
        )
    }

    @Test
    fun `messages from elsewhere still fall back to reading the text`() {
        assertEquals(
            CoreDownloadFailure.NotPublished,
            classifyCoreDownloadMessage("Core not available: HTTP 404: Not Found")
        )
        assertEquals(
            CoreDownloadFailure.ServerError,
            classifyCoreDownloadMessage("HTTP 503: Service Unavailable")
        )
        assertEquals(
            CoreDownloadFailure.Offline,
            classifyCoreDownloadMessage("Unable to resolve host \"buildbot.libretro.com\"")
        )
    }

    @Test
    fun `an unrecognised failure keeps its own message`() {
        val failure = classifyCoreDownloadFailure(IllegalStateException("something new"))
        assertEquals(CoreDownloadFailure.Unknown("something new"), failure)
        assertEquals("something new", formatCoreDownloadError(failure))
    }
}
