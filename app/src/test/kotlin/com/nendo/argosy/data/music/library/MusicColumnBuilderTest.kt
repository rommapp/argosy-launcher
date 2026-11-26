package com.nendo.argosy.data.music.library

import com.nendo.argosy.data.local.entity.MusicDownloadEntity
import com.nendo.argosy.domain.model.MusicAlbum
import com.nendo.argosy.domain.model.MusicArt
import com.nendo.argosy.domain.model.MusicArtist
import com.nendo.argosy.domain.model.MusicColumnKind
import com.nendo.argosy.domain.model.MusicGrouping
import com.nendo.argosy.domain.model.MusicPlaylist
import com.nendo.argosy.domain.model.MusicSourceKind
import com.nendo.argosy.domain.music.CreditSplitter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicColumnBuilderTest {

    private val builder = MusicColumnBuilder()

    private fun album(
        id: String,
        source: MusicSourceKind = MusicSourceKind.NAVIDROME,
        title: String = "Album $id",
        artist: String? = "Artist",
        year: Int? = null,
        genre: String? = null,
        playCount: Int = 0,
        art: MusicArt? = MusicArt.Local("/art/$id.jpg"),
        trackCount: Int = 10
    ) = MusicAlbum(
        key = "${source.token}:album:$id",
        source = source,
        id = id,
        title = title,
        artist = artist,
        artistId = null,
        year = year,
        genre = genre,
        trackCount = trackCount,
        durationSec = trackCount * 60,
        art = art,
        playCount = playCount,
        downloaded = false
    )

    private fun snapshot(
        kind: MusicSourceKind,
        albums: List<MusicAlbum> = emptyList(),
        artists: List<MusicArtist> = emptyList(),
        playlists: List<MusicPlaylist> = emptyList(),
        offline: Boolean = false
    ) = MusicSourceSnapshot(kind, albums, artists, playlists, offline)

    @Test
    fun `artists merge case-insensitively across sources and carry the photo`() {
        val photo = MusicArt.Remote("https://nd/photo", "nd:artist:1")
        val navidrome = snapshot(
            MusicSourceKind.NAVIDROME,
            albums = listOf(album("a1", artist = "Boards of Canada")),
            artists = listOf(MusicArtist("nd:artist:1", MusicSourceKind.NAVIDROME, "1", "Boards of Canada", 1, photo))
        )
        val other = snapshot(
            MusicSourceKind.NAVIDROME,
            albums = listOf(album("a2", artist = "boards of canada "))
        )

        val columns = builder.page(MusicGrouping.ARTISTS, listOf(navidrome, other)).columns
        val artistColumns = columns.filter { it.kind == MusicColumnKind.ARTIST }

        assertEquals(1, artistColumns.size)
        assertEquals("Boards of Canada", artistColumns.single().title)
        assertEquals(2, artistColumns.single().albumCount)
        assertEquals(photo, artistColumns.single().photo)
    }

    @Test
    fun `an album with several credits sits in every credited artist column`() {
        val shared = album("swsh", source = MusicSourceKind.ROMM, artist = "Go Ichinose, Minako Adachi")
            .copy(credits = listOf("Go Ichinose", "Minako Adachi"))
        val solo = album("bdsp", source = MusicSourceKind.ROMM, artist = "Go Ichinose")
            .copy(credits = listOf("Go Ichinose"))

        val columns = builder.page(
            MusicGrouping.ARTISTS,
            listOf(snapshot(MusicSourceKind.ROMM, albums = listOf(shared, solo)))
        ).columns.filter { it.kind == MusicColumnKind.ARTIST }

        assertEquals(listOf("Go Ichinose", "Minako Adachi"), columns.map { it.title })
        assertEquals(2, columns.first { it.title == "Go Ichinose" }.albumCount)
        assertEquals(1, columns.first { it.title == "Minako Adachi" }.albumCount)
    }

    @Test
    fun `a navidrome multi-credit artist tag splits into one column per credit`() {
        val tag = "Night Tempo|Yuki Saito"
        val split = album("nd1", artist = tag).copy(credits = CreditSplitter.split(tag))
        val solo = album("nd2", artist = "Yuki Saito").copy(credits = CreditSplitter.split("Yuki Saito"))

        val columns = builder.page(
            MusicGrouping.ARTISTS,
            listOf(snapshot(MusicSourceKind.NAVIDROME, albums = listOf(split, solo)))
        ).columns.filter { it.kind == MusicColumnKind.ARTIST }

        assertEquals(listOf("Night Tempo", "Yuki Saito"), columns.map { it.title })
        assertEquals(2, columns.first { it.title == "Yuki Saito" }.albumCount)
    }

    @Test
    fun `hero is the most played album, ties go to the newest year then title`() {
        val mostPlayed = album("old", year = 1990, playCount = 9)
        val newer = album("new", year = 2020, playCount = 3)
        val columns = builder.page(
            MusicGrouping.ARTISTS,
            listOf(snapshot(MusicSourceKind.NAVIDROME, albums = listOf(newer, mostPlayed)))
        ).columns
        assertEquals(mostPlayed.art, columns.single().heroArt)

        val tiedOld = album("b", title = "B", year = 1990, playCount = 5)
        val tiedNew = album("c", title = "C", year = 2001, playCount = 5)
        val tiedColumns = builder.page(
            MusicGrouping.ARTISTS,
            listOf(snapshot(MusicSourceKind.NAVIDROME, albums = listOf(tiedOld, tiedNew)))
        ).columns
        assertEquals(tiedNew.art, tiedColumns.single().heroArt)

        val sameYearZ = album("z", title = "Zeta", year = 2001, playCount = 5)
        val sameYearA = album("y", title = "Alpha", year = 2001, playCount = 5)
        val titleColumns = builder.page(
            MusicGrouping.ARTISTS,
            listOf(snapshot(MusicSourceKind.NAVIDROME, albums = listOf(sameYearZ, sameYearA)))
        ).columns
        assertEquals(sameYearA.art, titleColumns.single().heroArt)
    }

    @Test
    fun `sleeves cap at three with the hero in front and the rest counted as hidden`() {
        val albums = (1..5).map { album("a$it", title = "T$it", playCount = it) }
        val column = builder.page(
            MusicGrouping.ARTISTS,
            listOf(snapshot(MusicSourceKind.NAVIDROME, albums = albums))
        ).columns.single()

        assertEquals(3, column.sleeves.size)
        assertEquals(column.heroArt, column.sleeves.first())
        assertEquals(listOf("T5", "T4", "T3"), column.sleeveTitles)
        assertEquals(2, column.hiddenAlbumCount)
        assertEquals(5, column.albumCount)
    }

    @Test
    fun `albums without art are counted as hidden rather than sleeved`() {
        val albums = listOf(album("a1", art = null, playCount = 9), album("a2"))
        val column = builder.page(
            MusicGrouping.ARTISTS,
            listOf(snapshot(MusicSourceKind.NAVIDROME, albums = albums))
        ).columns.single()

        assertEquals(listOf(albums[1].art), column.sleeves)
        assertEquals(1, column.hiddenAlbumCount)
    }

    @Test
    fun `years sort ascending with romm albums merged and the untitled year last`() {
        val navidrome = snapshot(
            MusicSourceKind.NAVIDROME,
            albums = listOf(album("a", year = 2005), album("b", year = null), album("c", year = 1999))
        )
        val romm = snapshot(
            MusicSourceKind.ROMM,
            albums = listOf(
                album("r1", source = MusicSourceKind.ROMM, year = 1999),
                album("r2", source = MusicSourceKind.ROMM, year = null)
            )
        )

        val columns = builder.page(MusicGrouping.YEARS, listOf(navidrome, romm)).columns

        assertEquals(listOf("year:1999", "year:2005", "year:none"), columns.map { it.key })
        assertEquals(setOf(MusicSourceKind.NAVIDROME, MusicSourceKind.ROMM), columns.first().sources)
        assertEquals(2, columns.last().albumCount)
    }

    @Test
    fun `romm albums group by their artist tag alongside navidrome artists`() {
        val navidrome = snapshot(
            MusicSourceKind.NAVIDROME,
            albums = listOf(album("a", artist = "Zed"), album("b", artist = "Koji Kondo"))
        )
        val romm = snapshot(
            MusicSourceKind.ROMM,
            albums = listOf(album("r1", source = MusicSourceKind.ROMM, artist = "koji kondo"))
        )

        val columns = builder.page(MusicGrouping.ARTISTS, listOf(navidrome, romm)).columns

        assertEquals(listOf("artist:koji kondo", "artist:zed"), columns.map { it.key })
        assertEquals(MusicColumnKind.ARTIST, columns.first().kind)
        assertEquals(setOf(MusicSourceKind.NAVIDROME, MusicSourceKind.ROMM), columns.first().sources)
    }

    @Test
    fun `untagged, blank and navidrome unknown artists share the last unknown artist column`() {
        val navidrome = snapshot(
            MusicSourceKind.NAVIDROME,
            albums = listOf(
                album("a", artist = "Abe"),
                album("b", artist = "[Unknown Artist]"),
                album("c", artist = "  "),
                album("d", artist = "[unknown artist]")
            )
        )
        val romm = snapshot(
            MusicSourceKind.ROMM,
            albums = listOf(album("r1", source = MusicSourceKind.ROMM, artist = null))
        )

        val columns = builder.page(MusicGrouping.ARTISTS, listOf(navidrome, romm)).columns

        assertEquals(listOf("artist:abe", MusicColumnBuilder.ARTIST_NONE_KEY), columns.map { it.key })
        val unknown = columns.last()
        assertEquals(4, unknown.albumCount)
        assertEquals(setOf(MusicSourceKind.NAVIDROME, MusicSourceKind.ROMM), unknown.sources)
        assertEquals("", unknown.title)
    }

    @Test
    fun `albums without a genre land in the last genre column`() {
        val navidrome = snapshot(
            MusicSourceKind.NAVIDROME,
            albums = listOf(album("a", genre = "VGM"), album("b", genre = null))
        )
        val romm = snapshot(
            MusicSourceKind.ROMM,
            albums = listOf(album("r1", source = MusicSourceKind.ROMM, genre = "vgm"))
        )

        val columns = builder.page(MusicGrouping.GENRES, listOf(navidrome, romm)).columns

        assertEquals(listOf("genre:vgm", MusicColumnBuilder.GENRE_NONE_KEY), columns.map { it.key })
        assertEquals(2, columns.first().albumCount)
    }

    @Test
    fun `albums grouping gives romm soundtracks their own album columns`() {
        val romm = snapshot(MusicSourceKind.ROMM, albums = listOf(album("r1", source = MusicSourceKind.ROMM)))
        val columns = builder.page(MusicGrouping.ALBUMS, listOf(romm)).columns
        assertEquals(listOf(MusicColumnKind.ALBUM), columns.map { it.kind })
    }

    @Test
    fun `recent leads every grouping in the given recency order across sources`() {
        val first = album("r1", title = "Zulu", playCount = 0)
        val second = album("r2", source = MusicSourceKind.ROMM, title = "Alpha", playCount = 50)
        val navidrome = snapshot(MusicSourceKind.NAVIDROME, albums = listOf(first))
        val romm = snapshot(MusicSourceKind.ROMM, albums = listOf(second))

        val columns = builder.page(MusicGrouping.GENRES, listOf(navidrome, romm), recent = listOf(first, second, first))
            .columns

        assertEquals("recent", columns.first().key)
        assertEquals(MusicColumnKind.RECENT, columns.first().kind)
        assertEquals("", columns.first().title)
        assertEquals(listOf("Zulu", "Alpha"), columns.first().sleeveTitles)
        assertEquals(2, columns.first().albumCount)
        assertEquals(setOf(MusicSourceKind.NAVIDROME, MusicSourceKind.ROMM), columns.first().sources)
    }

    @Test
    fun `no recent column without listening history`() {
        val columns = builder.page(
            MusicGrouping.ALBUMS,
            listOf(snapshot(MusicSourceKind.NAVIDROME, albums = listOf(album("a"))))
        ).columns
        assertFalse(columns.any { it.kind == MusicColumnKind.RECENT })
    }

    @Test
    fun `an offline snapshot marks the page offline and an online one does not`() {
        val online = snapshot(MusicSourceKind.NAVIDROME, albums = listOf(album("a")))
        val offline = snapshot(MusicSourceKind.ROMM, offline = true)

        assertTrue(builder.page(MusicGrouping.ALBUMS, listOf(online, offline)).offline)
        assertFalse(builder.page(MusicGrouping.ALBUMS, listOf(online)).offline)
    }

    @Test
    fun `downloaded rows rebuild albums for the offline fallback`() {
        val rows = listOf(
            row("t1", albumId = "al1", album = "Geogaddi", art = "/files/art/x"),
            row("t2", albumId = "al1", album = "Geogaddi"),
            row("t3", albumId = null, album = null),
            row("t4", albumId = "al2", source = MusicSourceKind.ROMM.token)
        )

        val albums = MusicDownloadAlbums.albumsFrom(MusicSourceKind.NAVIDROME, rows)
        val geogaddi = albums.single { it.id == "al1" }

        assertEquals(2, albums.size)
        assertEquals("navidrome:album:al1", geogaddi.key)
        assertEquals("Geogaddi", geogaddi.title)
        assertEquals(2, geogaddi.trackCount)
        assertEquals(listOf("Artist"), geogaddi.credits)
        assertEquals(MusicArt.Local("/files/art/x"), geogaddi.art)
        assertTrue(geogaddi.downloaded)
        assertNull(albums.single { it.id != "al1" }.art)
    }

    @Test
    fun `playlists grouping lists every source's playlists by name`() {
        val nd = MusicPlaylist("nd:playlist:1", MusicSourceKind.NAVIDROME, "1", "Night", 4, 900, null, true)
        val romm = MusicPlaylist("romm:playlist:2", MusicSourceKind.ROMM, "2", "Arcade", 7, 0, null, false)
        val columns = builder.page(
            MusicGrouping.PLAYLISTS,
            listOf(
                snapshot(MusicSourceKind.NAVIDROME, playlists = listOf(nd)),
                snapshot(MusicSourceKind.ROMM, playlists = listOf(romm))
            )
        ).columns

        assertEquals(listOf("Arcade", "Night"), columns.map { it.title })
        assertTrue(columns.all { it.kind == MusicColumnKind.PLAYLIST })
    }

    @Test
    fun `owned playlists with the same name on both sources share one column`() {
        val nd = MusicPlaylist("navidrome:playlist:1", MusicSourceKind.NAVIDROME, "1", "Night", 4, 900, null, true)
        val romm = MusicPlaylist("romm:playlist:2", MusicSourceKind.ROMM, "2", " night", 7, 0, null, true)
        val groups = builder.groups(
            MusicGrouping.PLAYLISTS,
            listOf(
                snapshot(MusicSourceKind.NAVIDROME, playlists = listOf(nd)),
                snapshot(MusicSourceKind.ROMM, playlists = listOf(romm))
            )
        )

        val column = groups.single().column
        assertEquals("playlist:owned:night", column.key)
        assertEquals("Night", column.title)
        assertEquals(setOf(MusicSourceKind.NAVIDROME, MusicSourceKind.ROMM), column.sources)
        assertEquals(11, column.trackCount)
        assertEquals(listOf(nd, romm), groups.single().playlistGroup?.halves)
    }

    private fun row(
        key: String,
        albumId: String?,
        album: String? = "Album",
        art: String? = null,
        source: String = MusicSourceKind.NAVIDROME.token
    ) = MusicDownloadEntity(
        trackKey = key,
        source = source,
        trackId = key,
        title = "Title $key",
        artist = "Artist",
        album = album,
        albumId = albumId,
        trackNumber = 1,
        durationSec = 120,
        year = 2002,
        genre = "IDM",
        artCacheKey = null,
        artLocalPath = art,
        filePath = "/files/tracks/$key.mp3",
        status = MusicDownloadStatus.DONE.name,
        sizeBytes = 1L,
        downloadedAt = null
    )
}
