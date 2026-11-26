package com.nendo.argosy.data.music.library

import com.nendo.argosy.domain.model.MusicPlaylist
import com.nendo.argosy.domain.model.MusicSourceKind
import com.nendo.argosy.domain.model.MusicTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicPlaylistRulesTest {

    private fun playlist(
        id: String,
        source: MusicSourceKind,
        name: String,
        writable: Boolean = true,
        trackCount: Int = 0
    ) = MusicPlaylist(
        key = "${source.token}:playlist:$id",
        source = source,
        id = id,
        name = name,
        trackCount = trackCount,
        durationSec = 0,
        art = null,
        writable = writable
    )

    private fun track(id: String, source: MusicSourceKind) = MusicTrack(
        key = "${source.token}:track:$id",
        source = source,
        id = if (source == MusicSourceKind.ROMM) "/stream/$id" else id,
        title = "Track $id",
        artist = null,
        album = null,
        albumId = null,
        trackNumber = null,
        durationSec = null,
        year = null,
        genre = null,
        art = null,
        downloadedPath = null,
        romFileId = if (source == MusicSourceKind.ROMM) id.toLong() else null
    )

    @Test
    fun `owned playlists merge by trimmed case-insensitive name, navidrome half first`() {
        val romm = playlist("9", MusicSourceKind.ROMM, "Road Trip ")
        val nd = playlist("a", MusicSourceKind.NAVIDROME, "road trip")

        val group = mergePlaylistGroups(listOf(romm, nd)).single()

        assertEquals("owned:road trip", group.key)
        assertTrue(group.owned)
        assertEquals(listOf(nd, romm), group.halves)
        assertEquals(setOf(MusicSourceKind.NAVIDROME, MusicSourceKind.ROMM), group.sources)
    }

    @Test
    fun `two same-named navidrome playlists fold into one group`() {
        val first = playlist("a", MusicSourceKind.NAVIDROME, "Mix", trackCount = 2)
        val second = playlist("b", MusicSourceKind.NAVIDROME, "MIX", trackCount = 3)

        val group = mergePlaylistGroups(listOf(first, second)).single()

        assertEquals(listOf(first, second), group.halves)
        assertEquals(5, group.trackCount)
    }

    @Test
    fun `shared playlists stay separate and never join an owned group`() {
        val mine = playlist("a", MusicSourceKind.NAVIDROME, "Mix")
        val theirs = playlist("b", MusicSourceKind.NAVIDROME, "Mix", writable = false)
        val theirsOnRomm = playlist("5", MusicSourceKind.ROMM, "Mix", writable = false)

        val groups = mergePlaylistGroups(listOf(mine, theirs, theirsOnRomm))

        assertEquals(3, groups.size)
        assertEquals(listOf(mine), groups.single { it.owned }.halves)
        val shared = groups.filterNot { it.owned }
        assertEquals(setOf("shared:navidrome:playlist:b", "shared:romm:playlist:5"), shared.map { it.key }.toSet())
        assertTrue(shared.all { it.halves.size == 1 && !it.hasCustomOrder })
    }

    @Test
    fun `an owned group with a saved order is marked custom`() {
        val groups = mergePlaylistGroups(
            listOf(playlist("a", MusicSourceKind.NAVIDROME, "Mix"), playlist("b", MusicSourceKind.NAVIDROME, "Other")),
            customOrders = setOf("mix")
        )

        assertTrue(groups.single { it.name == "Mix" }.hasCustomOrder)
        assertFalse(groups.single { it.name == "Other" }.hasCustomOrder)
    }

    @Test
    fun `default order is the navidrome half then the romm half, each in server order`() {
        val nd = playlist("a", MusicSourceKind.NAVIDROME, "Mix")
        val romm = playlist("9", MusicSourceKind.ROMM, "Mix")

        val entries = defaultPlaylistEntries(
            listOf(
                romm to listOf(track("7", MusicSourceKind.ROMM), track("3", MusicSourceKind.ROMM)),
                nd to listOf(track("s2", MusicSourceKind.NAVIDROME), track("s1", MusicSourceKind.NAVIDROME))
            )
        )

        assertEquals(
            listOf("navidrome:track:s2#1", "navidrome:track:s1#1", "romm:track:7#1", "romm:track:3#1"),
            entries.map { it.entryKey }
        )
        assertEquals(listOf(0, 1, 0, 1), entries.map { it.position })
        assertEquals(listOf(nd, nd, romm, romm), entries.map { it.playlist })
    }

    @Test
    fun `a repeated navidrome song gets one key per occurrence`() {
        val nd = playlist("a", MusicSourceKind.NAVIDROME, "Mix")
        val song = track("s1", MusicSourceKind.NAVIDROME)

        val entries = defaultPlaylistEntries(listOf(nd to listOf(song, track("s2", MusicSourceKind.NAVIDROME), song)))

        assertEquals(
            listOf("navidrome:track:s1#1", "navidrome:track:s2#1", "navidrome:track:s1#2"),
            entries.map { it.entryKey }
        )
        assertEquals(2, entries.last().position)
    }

    @Test
    fun `a saved order keeps present keys, drops missing ones and appends new ones navidrome first`() {
        val nd = playlist("a", MusicSourceKind.NAVIDROME, "Mix")
        val romm = playlist("9", MusicSourceKind.ROMM, "Mix")
        val entries = defaultPlaylistEntries(
            listOf(
                nd to listOf(track("s1", MusicSourceKind.NAVIDROME), track("s3", MusicSourceKind.NAVIDROME)),
                romm to listOf(track("7", MusicSourceKind.ROMM), track("8", MusicSourceKind.ROMM))
            )
        )
        val saved = listOf("romm:track:7#1", "navidrome:track:gone#1", "navidrome:track:s1#1")

        val ordered = applyPlaylistOrder(entries, saved)

        assertEquals(
            listOf("romm:track:7#1", "navidrome:track:s1#1", "navidrome:track:s3#1", "romm:track:8#1"),
            ordered.map { it.entryKey }
        )
    }

    @Test
    fun `no saved order leaves the default order`() {
        val nd = playlist("a", MusicSourceKind.NAVIDROME, "Mix")
        val entries = defaultPlaylistEntries(listOf(nd to listOf(track("s1", MusicSourceKind.NAVIDROME))))

        assertEquals(entries, applyPlaylistOrder(entries, emptyList()))
    }

    @Test
    fun `move shifts one item and rejects out of range indices`() {
        val keys = listOf("a", "b", "c", "d")

        assertEquals(listOf("b", "c", "a", "d"), moveItem(keys, 0, 2))
        assertEquals(listOf("d", "a", "b", "c"), moveItem(keys, 3, 0))
        assertEquals(keys, moveItem(keys, 1, 1))
        assertNull(moveItem(keys, 4, 0))
        assertNull(moveItem(keys, 0, -1))
    }

    @Test
    fun `removing an occurrence renumbers the later ones of the same track`() {
        val saved = listOf("navidrome:track:s1#2", "romm:track:7#1", "navidrome:track:s1#1", "navidrome:track:s1#3")

        assertEquals(
            listOf("navidrome:track:s1#1", "romm:track:7#1", "navidrome:track:s1#2"),
            dropAndRenumber(saved, "navidrome:track:s1#1")
        )
    }

    @Test
    fun `order rows round trip through the stored text`() {
        val keys = listOf("navidrome:track:s1#1", "romm:track:7#1")

        assertEquals(keys, decodePlaylistOrder(encodePlaylistOrder(keys)))
        assertEquals(emptyList<String>(), decodePlaylistOrder(""))
    }

    @Test
    fun `playlist track ids are the navidrome song id and the romm rom file id`() {
        assertEquals("s1", track("s1", MusicSourceKind.NAVIDROME).playlistTrackId())
        assertEquals("7", track("7", MusicSourceKind.ROMM).playlistTrackId())
        assertNull(track("7", MusicSourceKind.ROMM).copy(romFileId = null).playlistTrackId())
    }
}
