package com.nendo.argosy.data.music.identity

import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.local.entity.GameFileEntity
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.remote.romm.RomMMusicTrack
import com.nendo.argosy.domain.model.MusicSourceKind
import com.nendo.argosy.domain.model.MusicTrack
import com.nendo.argosy.domain.model.MusicTrackIdentity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MusicTrackIdentitiesTest {

    private val accounts = MusicAccounts(
        ownerKey = 7,
        rommServerKey = "romm.lan:8080",
        rommAccountKey = "7@romm.lan:8080",
        navidromeAccountKey = "alice@nd.lan"
    )

    private fun track(
        source: MusicSourceKind,
        key: String,
        id: String,
        albumId: String?,
        romId: Long? = null,
        romFileId: Long? = null,
        isrc: String? = null
    ) = MusicTrack(
        key = key,
        source = source,
        id = id,
        title = "Irene",
        artist = "YASUHIRO ABE",
        album = "Slit",
        albumId = albumId,
        trackNumber = 2,
        durationSec = 311,
        year = 1984,
        genre = null,
        art = null,
        downloadedPath = null,
        isrc = isrc,
        romId = romId,
        romFileId = romFileId
    )

    private val game = GameEntity(
        id = 42L,
        title = "Zelda",
        sortTitle = "zelda",
        platformId = 1L,
        rommId = 55L,
        igdbId = null,
        localPath = null,
        source = GameSource.ROMM_SYNCED
    )

    private fun file(rommFileId: Long?) = GameFileEntity(
        gameId = 42L,
        rommFileId = rommFileId,
        fileName = "01 Title.flac",
        filePath = "/server/01 Title.flac",
        category = "soundtrack",
        fileSize = 1L,
        localPath = "/sdcard/ost/01 Title.flac",
        durationSeconds = 95.4
    )

    @Test
    fun `navidrome tracks key on the song id and carry the isrc`() {
        val identity = MusicTrackIdentities.forLibraryTrack(
            track(MusicSourceKind.NAVIDROME, "navidrome:track:s1", "s1", albumId = "al1", isrc = "JPTO08831620"),
            accounts
        )

        assertEquals("navidrome", identity.source)
        assertEquals("alice@nd.lan", identity.sourceAccountKey)
        assertEquals("s1", identity.sourceTrackId)
        assertEquals("navidrome:track:s1", identity.trackKey)
        assertEquals("navidrome:album:al1", identity.albumKey)
        assertEquals("JPTO08831620", identity.isrc)
        assertEquals("YASUHIRO ABE", identity.artistCredit)
        assertEquals(311, identity.durationSec)
    }

    @Test
    fun `romm library tracks key on the rom file id whatever their track id`() {
        val identity = MusicTrackIdentities.forLibraryTrack(
            track(MusicSourceKind.ROMM, "romm:track:path:/x.flac", "path:/x.flac", albumId = "r55", romId = 55, romFileId = 900),
            accounts
        )

        assertEquals("romm", identity.source)
        assertEquals("7@romm.lan:8080", identity.sourceAccountKey)
        assertEquals("900", identity.sourceTrackId)
        assertEquals("romm:track:900", identity.trackKey)
        assertEquals("romm:album:r55", identity.albumKey)
        assertEquals(55L, identity.romId)
    }

    @Test
    fun `server romm tracks and device files of the same rom file share a track key`() {
        val server = MusicTrackIdentities.forRommTrack(
            RomMMusicTrack(
                romFileId = 900,
                romId = 55,
                title = "Title",
                gameName = "Zelda",
                platformId = 1,
                platformSlug = "switch",
                platformName = "Switch",
                streamUrl = "/api/raw/900",
                durationSeconds = 95.4
            ),
            "Title",
            accounts
        )
        val device = MusicTrackIdentities.forDeviceFile("/sdcard/ost/01 Title.flac", file(900), game, "Title", accounts)

        assertEquals(server.trackKey, device.trackKey)
        assertEquals("romm:album:r55", server.albumKey)
        assertEquals(server.albumKey, device.albumKey)
        assertEquals("Zelda", server.albumTitle)
        assertEquals(95, device.durationSec)
    }

    @Test
    fun `a device file without a romm file id keys on its path`() {
        val identity = MusicTrackIdentities.forDeviceFile("/sdcard/bgm/a.mp3", file(null), game, "a", accounts)

        assertEquals(MusicTrackIdentity.SOURCE_LOCAL, identity.source)
        assertEquals("local:/sdcard/bgm/a.mp3", identity.trackKey)
        assertEquals("romm:album:r55", identity.albumKey)
    }

    @Test
    fun `a device file no game row knows has no album`() {
        val identity = MusicTrackIdentities.forDeviceFile("/sdcard/bgm/a.mp3", null, null, "a", accounts)

        assertEquals("local:/sdcard/bgm/a.mp3", identity.trackKey)
        assertNull(identity.albumKey)
        assertNull(identity.romId)
    }

    @Test
    fun `a game without a rom id files its soundtrack under the game album`() {
        val identity = MusicTrackIdentities.forDeviceFile("/p.flac", file(null), game.copy(rommId = null), "p", accounts)
        assertEquals("romm:album:g42", identity.albumKey)
    }

    @Test
    fun `server keys keep host and port and fold case`() {
        assertEquals("romm.lan:8080", MusicAccountReader.serverKey("https://ROMM.lan:8080/"))
        assertEquals("nd.example.com", MusicAccountReader.serverKey("https://nd.example.com/music"))
        assertEquals("", MusicAccountReader.serverKey(null))
    }
}
