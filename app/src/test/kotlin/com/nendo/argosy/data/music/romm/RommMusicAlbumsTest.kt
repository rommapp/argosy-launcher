package com.nendo.argosy.data.music.romm

import com.nendo.argosy.data.local.entity.RommMusicTrackEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RommMusicAlbumsTest {

    private var nextFileId = 1L

    private fun track(
        romId: Long,
        album: String? = null,
        artist: String? = null,
        year: Int? = null,
        genre: String? = null,
        gameName: String? = "Game $romId",
        durationSeconds: Double? = 60.0,
        coverUrl: String? = null,
        gameCoverUrl: String? = null
    ) = RommMusicTrackEntity(
        ownerUserId = 7L,
        romFileId = nextFileId++,
        romId = romId,
        title = null,
        artist = artist,
        album = album,
        genre = genre,
        year = year,
        track = null,
        disc = null,
        durationSeconds = durationSeconds,
        gameName = gameName,
        platformId = 1L,
        platformSlug = "snes",
        platformName = "SNES",
        streamUrl = "/api/raw/$romId",
        coverUrl = coverUrl,
        gameCoverUrl = gameCoverUrl
    )

    @Test
    fun `each tag is the value most tracks carry`() {
        val rows = listOf(
            track(1, album = "OST", artist = "Koji Kondo", year = 1991, genre = "VGM"),
            track(1, album = "OST", artist = "Koji Kondo", year = 1991, genre = "VGM"),
            track(1, album = "Bonus", artist = "Other", year = 1992, genre = "Rock")
        )

        val album = RommMusicAlbums.albums(rows).single()

        assertEquals("OST", album.title)
        assertEquals("Koji Kondo", album.artist)
        assertEquals(1991, album.year)
        assertEquals("VGM", album.genre)
    }

    @Test
    fun `album credits and artists use the shared splitter, keeping bracketed groups and splitting pipes`() {
        val rows = listOf(
            track(1, artist = "ACE (TOMOri Kudo / CHiCO)"),
            track(1, artist = "Night Tempo|Yuki Saito")
        )

        val album = RommMusicAlbums.albums(rows).single()

        assertEquals(listOf("ACE (TOMOri Kudo / CHiCO)", "Night Tempo", "Yuki Saito"), album.credits)
        assertEquals(
            listOf("ACE (TOMOri Kudo / CHiCO)", "Night Tempo", "Yuki Saito"),
            RommMusicAlbums.artists(rows).map { it.name }
        )
    }

    @Test
    fun `a zero year tag counts as no year`() {
        val rows = listOf(track(1, year = 0), track(1, year = 0), track(1, year = 1998))

        assertEquals(1998, RommMusicAlbums.albums(rows).single().year)
        assertNull(RommMusicAlbums.albums(listOf(track(2, year = 0))).single().year)
    }

    @Test
    fun `album credits gather every split name across its tracks, most credited first`() {
        val rows = listOf(
            track(1, artist = "Go Ichinose, Hitomi Sato"),
            track(1, artist = "Go Ichinose, Minako Adachi"),
            track(1, artist = "Go Ichinose")
        )

        val album = RommMusicAlbums.albums(rows).single()

        assertEquals(listOf("Go Ichinose", "Hitomi Sato", "Minako Adachi"), album.credits)
        assertEquals(
            listOf("Go Ichinose", "Hitomi Sato", "Minako Adachi"),
            RommMusicAlbums.artists(rows).map { it.name }
        )
    }

    @Test
    fun `the minority value never wins whichever order the tracks arrive in`() {
        val majorityFirst = listOf(
            track(1, artist = "A"),
            track(1, artist = "A"),
            track(1, artist = "B")
        )
        val majorityLast = majorityFirst.reversed()

        assertEquals("A", RommMusicAlbums.albums(majorityFirst).single().artist)
        assertEquals("A", RommMusicAlbums.albums(majorityLast).single().artist)
    }

    @Test
    fun `a tie goes to the lowest value in either order`() {
        val tied = listOf(track(1, year = 2001), track(1, year = 1999))

        assertEquals(1999, RommMusicAlbums.albums(tied).single().year)
        assertEquals(1999, RommMusicAlbums.albums(tied.reversed()).single().year)
    }

    @Test
    fun `blank tags count as absent and untagged albums fall back to the game name`() {
        val rows = listOf(
            track(1, album = " ", artist = "", genre = "  ", gameName = "Chrono Trigger"),
            track(1, album = null, artist = null, gameName = "Chrono Trigger")
        )

        val album = RommMusicAlbums.albums(rows).single()

        assertEquals("Chrono Trigger", album.title)
        assertNull(album.artist)
        assertNull(album.year)
        assertNull(album.genre)
    }

    @Test
    fun `a blank tag never outvotes a real one`() {
        val rows = listOf(track(1, artist = " "), track(1, artist = " "), track(1, artist = "Uematsu"))

        assertEquals("Uematsu", RommMusicAlbums.albums(rows).single().artist)
    }

    @Test
    fun `one album per rom with its track count, duration and game cover first`() {
        val rows = listOf(
            track(1, durationSeconds = 90.5, coverUrl = "/track.png", gameCoverUrl = null),
            track(1, durationSeconds = 30.0, coverUrl = null, gameCoverUrl = "/game.png"),
            track(2, durationSeconds = null)
        )

        val albums = RommMusicAlbums.albums(rows).associateBy { it.romId }

        assertEquals(2, albums.size)
        assertEquals(2, albums.getValue(1).trackCount)
        assertEquals(120, albums.getValue(1).durationSec)
        assertEquals("/game.png", albums.getValue(1).coverUrl)
        assertEquals(1, albums.getValue(2).trackCount)
        assertEquals(0, albums.getValue(2).durationSec)
        assertNull(albums.getValue(2).coverUrl)
    }

    @Test
    fun `artists are split credits counted per distinct rom`() {
        val rows = listOf(
            track(1, artist = "Koji Kondo, Hajime Wakai"),
            track(1, artist = "Koji Kondo, Hajime Wakai"),
            track(2, artist = "Koji Kondo, Hajime Wakai"),
            track(2, artist = "Koji Kondo"),
            track(3, artist = " ")
        )

        val artists = RommMusicAlbums.artists(rows).associate { it.name to it.albumCount }

        assertEquals(mapOf("Koji Kondo" to 2, "Hajime Wakai" to 2), artists)
    }
}
