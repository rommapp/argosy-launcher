package com.nendo.argosy.ui.screens.musicstats

import com.nendo.argosy.domain.model.MusicAlbum
import com.nendo.argosy.domain.model.MusicArt
import com.nendo.argosy.domain.model.MusicFlashbackYear
import com.nendo.argosy.domain.model.MusicPairingGroup
import com.nendo.argosy.domain.model.MusicSourceKind
import com.nendo.argosy.domain.model.MusicStatTrack
import com.nendo.argosy.domain.music.CreditSplitter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicStatsShelvesTest {

    private fun track(
        key: String,
        source: String = MusicSourceKind.NAVIDROME.token,
        albumKey: String? = null,
        romId: Long? = null,
        artist: String? = null
    ) = MusicStatTrack(
        groupKey = key,
        trackKey = key,
        recordingKey = null,
        source = source,
        sourceAccountKey = "account",
        sourceTrackId = key,
        albumKey = albumKey,
        romId = romId,
        romFileId = null,
        title = key,
        artistCredit = artist,
        albumTitle = null,
        trackGameTitle = null,
        durationMs = 180_000,
        listenedMs = 60_000,
        playCount = 1,
        lastListenedAt = 0
    )

    private fun year(year: Int, vararg keys: String) =
        MusicFlashbackYear(year = year, listenedMs = 60_000L * keys.size, playCount = keys.size, tracks = keys.map { track(it) })

    @Test
    fun `flashback lists on this day years before this month years, each newest first`() {
        val shelves = MusicStatsShelves.flashbackShelves(
            onThisDay = listOf(year(2023, "a"), year(2025, "b")),
            inMonth = listOf(year(2024, "c"), year(2025, "d")),
            month = 11,
            currentYear = 2026
        )

        assertEquals(
            listOf(
                MusicStatsShelfTitle.OnThisDay(2025),
                MusicStatsShelfTitle.OnThisDay(2023),
                MusicStatsShelfTitle.MonthInYear(2025, 11),
                MusicStatsShelfTitle.MonthInYear(2024, 11)
            ),
            shelves.map { it.title }
        )
    }

    @Test
    fun `flashback leaves out the current year and years without tracks`() {
        val shelves = MusicStatsShelves.flashbackShelves(
            onThisDay = listOf(year(2026, "today"), year(2025)),
            inMonth = listOf(year(2026, "now"), year(2024, "then")),
            month = 11,
            currentYear = 2026
        )

        assertEquals(listOf(MusicStatsShelfTitle.MonthInYear(2024, 11)), shelves.map { it.title })
        assertEquals(listOf("then"), shelves.single().tracks.map { it.stat.trackKey })
    }

    @Test
    fun `flashback shelf keys stay distinct when both groups hold the same year`() {
        val shelves = MusicStatsShelves.flashbackShelves(
            onThisDay = listOf(year(2025, "a")),
            inMonth = listOf(year(2025, "b")),
            month = 11,
            currentYear = 2026
        )

        assertEquals(2, shelves.map { it.key }.toSet().size)
    }

    @Test
    fun `month shelves always start with the month stepper and skip empty lists`() {
        val shelves = MusicStatsShelves.monthShelves(listOf(track("a")), emptyList(), emptyList())

        assertEquals(listOf(MusicStatsShelfKind.MONTH, MusicStatsShelfKind.TRACKS), shelves.map { it.kind })
        assertEquals(1, shelves.first().itemCount)
    }

    @Test
    fun `an empty month is only the stepper and has no history`() {
        val page = MusicStatsPage(MusicStatsShelves.monthShelves(emptyList(), emptyList(), emptyList()), isLoading = false)

        assertEquals(listOf(MusicStatsShelfKind.MONTH), page.shelves.map { it.kind })
        assertTrue(!page.hasHistory)
    }

    @Test
    fun `pairing shelves take the replacement label and the cover for their group`() {
        val groups = listOf(
            MusicPairingGroup("switch", "switch", 120_000, listOf(track("a"))),
            MusicPairingGroup("empty", "empty", 0, emptyList())
        )

        val shelves = MusicStatsShelves.pairingShelves(
            MusicStatsPairingKind.PLATFORM,
            groups,
            labels = mapOf("switch" to "Nintendo Switch"),
            covers = mapOf("switch" to "/covers/switch.png")
        )

        assertEquals(1, shelves.size)
        assertEquals(MusicStatsShelfTitle.Pairing(MusicStatsPairingKind.PLATFORM, "Nintendo Switch"), shelves[0].title)
        assertEquals("/covers/switch.png", shelves[0].coverPath)
    }

    @Test
    fun `a RomM track without an album key falls back to its rom soundtrack album`() {
        assertEquals("romm:album:r42", MusicStatsShelves.albumKeyOf(track("t", MusicSourceKind.ROMM.token, romId = 42)))
        assertNull(MusicStatsShelves.albumKeyOf(track("t", MusicSourceKind.NAVIDROME.token, romId = 42)))
        assertEquals("navidrome:album:x", MusicStatsShelves.albumKeyOf(track("t", albumKey = "navidrome:album:x")))
    }

    @Test
    fun `catalog art reaches every track of a known album`() {
        val art = MusicArt.Local("/art/x.jpg")
        val album = MusicAlbum(
            key = "navidrome:album:x",
            source = MusicSourceKind.NAVIDROME,
            id = "x",
            title = "X",
            artist = null,
            artistId = null,
            year = null,
            genre = null,
            trackCount = 1,
            durationSec = 180,
            art = art,
            playCount = 0,
            downloaded = false
        )
        val shelves = MusicStatsShelves.monthShelves(
            listOf(track("a", albumKey = "navidrome:album:x"), track("b", albumKey = "navidrome:album:y")),
            emptyList(),
            emptyList()
        )

        val withArt = MusicStatsShelves.withCatalog(shelves, mapOf(album.key to album))

        assertEquals(listOf(art, null), withArt[1].tracks.map { it.art })
    }

    @Test
    fun `artist tracks match every credit of a multi artist track`() {
        val pool = listOf(
            track("a", artist = "Night Tempo, Yuki Saito"),
            track("b", artist = "Yuki Saito"),
            track("c", artist = "Someone Else")
        )

        val key = CreditSplitter.artistKey("Yuki Saito")

        assertEquals(listOf("a", "b"), MusicStatsShelves.artistTracks(pool, key).map { it.trackKey })
    }
}
