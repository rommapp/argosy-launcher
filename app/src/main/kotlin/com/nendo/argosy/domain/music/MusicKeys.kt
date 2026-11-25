package com.nendo.argosy.domain.music

import com.nendo.argosy.domain.model.MusicSourceKind

/**
 * Stored key forms for music tracks and albums: `<source>:track:<id>`, `<source>:album:<id>`,
 * RomM album ids `r<romId>` (a rom's soundtrack) and `g<gameId>` (a device-only soundtrack),
 * and `local:<path>` for a file no game row knows.
 */
object MusicKeys {
    private const val LOCAL_PREFIX = "local:"
    private const val ROM_ALBUM_PREFIX = "r"
    private const val GAME_ALBUM_PREFIX = "g"

    fun trackKey(source: String, trackId: String): String = "$source:track:$trackId"

    fun albumKey(source: String, albumId: String): String = "$source:album:$albumId"

    fun albumIdOf(source: String, key: String): String? {
        val prefix = albumKey(source, "")
        return key.takeIf { it.startsWith(prefix) }?.removePrefix(prefix)?.takeIf { it.isNotEmpty() }
    }

    fun rommTrackKey(romFileId: Long): String = trackKey(MusicSourceKind.ROMM.token, romFileId.toString())

    fun rommRomAlbumId(romId: Long): String = "$ROM_ALBUM_PREFIX$romId"

    fun rommGameAlbumId(gameId: Long): String = "$GAME_ALBUM_PREFIX$gameId"

    fun rommRomIdOf(albumId: String): Long? =
        albumId.takeIf { it.startsWith(ROM_ALBUM_PREFIX) }?.removePrefix(ROM_ALBUM_PREFIX)?.toLongOrNull()

    fun rommGameIdOf(albumId: String): Long? =
        albumId.takeIf { it.startsWith(GAME_ALBUM_PREFIX) }?.removePrefix(GAME_ALBUM_PREFIX)?.toLongOrNull()

    fun rommRomAlbumKey(romId: Long): String = albumKey(MusicSourceKind.ROMM.token, rommRomAlbumId(romId))

    fun rommGameAlbumKey(gameId: Long): String = albumKey(MusicSourceKind.ROMM.token, rommGameAlbumId(gameId))

    fun localTrackKey(path: String): String = "$LOCAL_PREFIX$path"
}
