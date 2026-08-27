package com.nendo.argosy.libretro

import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Why a core download failed.
 *
 * This used to be decided by reading the wording of an exception message, including messages
 * this module writes itself, which made the copy a control channel: rephrasing "Downloaded core
 * is corrupted" would have silently turned a recognised failure into an unrecognised one. The
 * cases the download path detects now travel as [CoreDownloadException], network failures are
 * recognised by exception type, and only a message from somewhere neither covers is read as text.
 */
sealed interface CoreDownloadFailure {
    data object NotPublished : CoreDownloadFailure
    data object ServerError : CoreDownloadFailure
    data object Corrupted : CoreDownloadFailure
    data object TimedOut : CoreDownloadFailure
    data object Offline : CoreDownloadFailure
    data class Unknown(val message: String) : CoreDownloadFailure
}

class CoreDownloadException(
    val failure: CoreDownloadFailure,
    message: String
) : IOException(message)

fun classifyCoreDownloadFailure(error: Throwable?): CoreDownloadFailure = when (error) {
    is CoreDownloadException -> error.failure
    is SocketTimeoutException -> CoreDownloadFailure.TimedOut
    is UnknownHostException, is ConnectException -> CoreDownloadFailure.Offline
    else -> classifyCoreDownloadMessage(error?.message ?: "Unknown error")
}

fun classifyCoreDownloadMessage(message: String): CoreDownloadFailure {
    val lower = message.lowercase()
    return when {
        "HTTP 404" in message -> CoreDownloadFailure.NotPublished
        "HTTP 5" in message -> CoreDownloadFailure.ServerError
        "corrupted" in lower -> CoreDownloadFailure.Corrupted
        "timed out" in lower || "timeout" in lower -> CoreDownloadFailure.TimedOut
        "unable to resolve host" in lower ||
            "no address associated" in lower ||
            "failed to connect" in lower ||
            "network is unreachable" in lower ||
            "unreachable" in lower -> CoreDownloadFailure.Offline
        else -> CoreDownloadFailure.Unknown(message)
    }
}

fun formatCoreDownloadError(failure: CoreDownloadFailure): String = when (failure) {
    CoreDownloadFailure.NotPublished ->
        "Core is not published for ${LibretroBuildbot.deviceAbi}. " +
            "This core may not be available on 32-bit devices."
    CoreDownloadFailure.ServerError -> "Libretro buildbot server error. Try again later."
    CoreDownloadFailure.Corrupted -> "Downloaded core file is corrupted. Try again."
    CoreDownloadFailure.TimedOut -> "Download timed out. Check your network connection."
    CoreDownloadFailure.Offline ->
        "You're offline. Connect to a network so Argosy can download this core."
    is CoreDownloadFailure.Unknown -> failure.message
}

fun formatCoreDownloadError(error: Throwable?): String =
    formatCoreDownloadError(classifyCoreDownloadFailure(error))

fun formatCoreDownloadError(message: String): String =
    formatCoreDownloadError(classifyCoreDownloadMessage(message))
