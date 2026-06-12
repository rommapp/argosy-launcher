package com.nendo.argosy.libretro

fun formatCoreDownloadError(message: String): String {
    val abi = LibretroBuildbot.deviceAbi
    val lower = message.lowercase()
    return when {
        "HTTP 404" in message -> "Core is not published for $abi. This core may not be available on 32-bit devices."
        "HTTP 5" in message -> "Libretro buildbot server error. Try again later."
        "corrupted" in message -> "Downloaded core file is corrupted. Try again."
        "timed out" in lower || "timeout" in lower ->
            "Download timed out. Check your network connection."
        "unable to resolve host" in lower ||
            "no address associated" in lower ||
            "failed to connect" in lower ||
            "network is unreachable" in lower ||
            "unreachable" in lower ->
            "You're offline. Connect to a network so Argosy can download this core."
        else -> message
    }
}
