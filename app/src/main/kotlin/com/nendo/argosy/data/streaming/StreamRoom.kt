package com.nendo.argosy.data.streaming

import com.nendo.argosy.data.remote.romm.RomMRomFile

/**
 * Where the stream page lives, and which discs it can swap to.
 */
object StreamRoom {
    /**
     * The room URL to load. RomM answers an absolute URL for a container on its own host, or a
     * bare path for a container reverse proxied onto RomM's origin
     * (backend/handler/streaming/protocol.py `room_url_on`); a path is resolved against [rommBaseUrl].
     */
    fun resolve(rommBaseUrl: String, host: String?): String? {
        val room = host?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (room.startsWith(HTTP) || room.startsWith(HTTPS)) return room
        val base = rommBaseUrl.trim().takeIf { it.isNotEmpty() } ?: return null
        val origin = base.substringBefore("://") + "://" + base.substringAfter("://").substringBefore('/')
        return origin + "/" + room.trimStart('/')
    }

    /**
     * The files a disc swap offers, as RomM's player lists them (frontend/src/utils/playerDisc.ts
     * `bootableFiles`, Stream.vue `discOptions`): bootable files without the playlist, the cue
     * sheets alone when there are any. Empty when the rom ships no `.m3u`.
     */
    fun swappableDiscs(files: List<RomMRomFile>): List<RomMRomFile> {
        if (files.none { extensionOf(it.fileName) == M3U }) return emptyList()
        val bootable = files.filter { it.category == null || it.category.lowercase() !in NON_BOOTABLE }
            .ifEmpty { files }
            .filter { extensionOf(it.fileName) != M3U }
        val cues = bootable.filter { extensionOf(it.fileName) == CUE }
        return cues.ifEmpty { bootable }
    }

    private fun extensionOf(fileName: String): String = fileName.substringAfterLast('.', "").lowercase()

    private const val HTTP = "http://"
    private const val HTTPS = "https://"
    private const val M3U = "m3u"
    private const val CUE = "cue"
    private val NON_BOOTABLE = setOf("manual", "walkthrough", "screenshot", "soundtrack", "patch", "cheat")
}
