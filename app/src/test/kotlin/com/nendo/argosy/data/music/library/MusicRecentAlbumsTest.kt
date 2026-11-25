package com.nendo.argosy.data.music.library

import com.nendo.argosy.data.local.entity.MusicListenEntity
import com.nendo.argosy.domain.model.MusicAlbum
import com.nendo.argosy.domain.model.MusicSourceFilter
import com.nendo.argosy.domain.model.MusicSourceKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicRecentAlbumsTest {

    private fun listen(
        id: Long,
        albumKey: String?,
        startedAt: Long,
        source: String = "navidrome",
        endReason: String? = null,
        albumTitle: String? = "Album $id"
    ) = MusicListenEntity(
        id = id,
        ownerKey = 7,
        startedAt = startedAt,
        endedAt = startedAt,
        localDay = 20261125,
        listenedMs = 1_000,
        endPositionMs = 42_000,
        durationMs = 200_000,
        counted = false,
        endReason = endReason,
        source = source,
        sourceAccountKey = "alice@nd",
        sourceTrackId = "t$id",
        trackKey = "navidrome:track:t$id",
        recordingKey = null,
        albumKey = albumKey,
        selectionId = "sel",
        romId = null,
        romFileId = null,
        isrc = null,
        title = "Track $id",
        artistCredit = "Night Tempo|Yuki Saito",
        albumTitle = albumTitle,
        trackGameTitle = null,
        contextId = null,
        scrobbleState = MusicListenEntity.SCROBBLE_NONE,
        scrobbledAt = null
    )

    private fun album(key: String, source: MusicSourceKind, id: String) = MusicAlbum(
        key = key,
        source = source,
        id = id,
        title = "Catalog $id",
        artist = null,
        artistId = null,
        year = null,
        genre = null,
        trackCount = 9,
        durationSec = 0,
        art = null,
        playCount = 0,
        downloaded = false
    )

    @Test
    fun `catalog albums win, the newest listen leads, and each album shows once`() {
        val catalog = mapOf("navidrome:album:a" to album("navidrome:album:a", MusicSourceKind.NAVIDROME, "a"))
        val listens = listOf(
            listen(1, "navidrome:album:a", startedAt = 100),
            listen(2, "navidrome:album:b", startedAt = 300),
            listen(3, "navidrome:album:a", startedAt = 200),
            listen(4, null, startedAt = 400)
        )

        val recent = MusicRecentAlbums.resolve(listens, catalog, MusicSourceFilter.ALL, 10)

        assertEquals(listOf("navidrome:album:b", "navidrome:album:a"), recent.map { it.key })
        assertEquals("Catalog a", recent[1].title)
    }

    @Test
    fun `an album missing from every snapshot is rebuilt from the listen's own fields`() {
        val recent = MusicRecentAlbums.resolve(
            listOf(listen(1, "romm:album:r55", startedAt = 1, source = "romm", albumTitle = "Zelda OST")),
            emptyMap(),
            MusicSourceFilter.ALL,
            10
        )

        val album = recent.single()
        assertEquals(MusicSourceKind.ROMM, album.source)
        assertEquals("r55", album.id)
        assertEquals("Zelda OST", album.title)
        assertEquals(listOf("Night Tempo", "Yuki Saito"), album.credits)
    }

    @Test
    fun `the source filter and the limit both apply`() {
        val listens = listOf(
            listen(1, "romm:album:r1", startedAt = 3, source = "romm"),
            listen(2, "navidrome:album:x", startedAt = 2),
            listen(3, "navidrome:album:y", startedAt = 1)
        )

        assertEquals(
            listOf("navidrome:album:x"),
            MusicRecentAlbums.resolve(listens, emptyMap(), MusicSourceFilter.NAVIDROME, 1).map { it.key }
        )
    }

    @Test
    fun `an unknown album key form is skipped`() {
        assertTrue(MusicRecentAlbums.resolve(listOf(listen(1, "jellyfin:album:z", 1)), emptyMap(), MusicSourceFilter.ALL, 10).isEmpty())
    }

    @Test
    fun `resume point carries where the listen stopped and whether it completed`() {
        val open = MusicRecentAlbums.resumePoint(listen(1, "navidrome:album:a", 1))!!
        assertEquals("navidrome:album:a", open.albumKey)
        assertEquals("navidrome:track:t1", open.trackKey)
        assertEquals(42_000L, open.positionMs)
        assertEquals("sel", open.selectionId)
        assertFalse(open.completed)

        assertTrue(MusicRecentAlbums.resumePoint(listen(2, "navidrome:album:a", 1, endReason = "completed"))!!.completed)
        assertNull(MusicRecentAlbums.resumePoint(listen(3, null, 1)))
    }
}
