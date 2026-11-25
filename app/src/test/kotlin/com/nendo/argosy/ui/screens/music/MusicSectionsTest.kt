package com.nendo.argosy.ui.screens.music

import com.nendo.argosy.domain.model.MusicColumn
import com.nendo.argosy.domain.model.MusicColumnKind
import com.nendo.argosy.domain.model.MusicSourceKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MusicSectionsTest {

    private fun column(title: String, kind: MusicColumnKind = MusicColumnKind.ARTIST) = MusicColumn(
        key = "${kind.name}:$title",
        kind = kind,
        title = title,
        sources = setOf(MusicSourceKind.NAVIDROME),
        albumCount = 1,
        trackCount = 1,
        durationSec = 60,
        heroArt = null,
        photo = null,
        sleeves = emptyList(),
        hiddenAlbumCount = 0,
        sleeveTitles = emptyList()
    )

    private val artists = listOf(
        column("", MusicColumnKind.RECENT),
        column("Anri"),
        column("Asuka"),
        column("Bandai"),
        column("Basiscape"),
        column("Kirinji"),
        column("")
    )

    @Test
    fun `sections are first letters, decades, or none`() {
        assertEquals("A", MusicSections.sectionOf(column("anri")))
        assertEquals("#", MusicSections.sectionOf(column("8 Eyes")))
        assertEquals("1980s", MusicSections.sectionOf(column("1987", MusicColumnKind.YEAR)))
        assertNull(MusicSections.sectionOf(column("", MusicColumnKind.RECENT)))
        assertNull(MusicSections.sectionOf(column("")))
    }

    @Test
    fun `jumping forward lands on the first column of the next section`() {
        assertEquals(3, MusicSections.jumpTarget(artists, focus = 2, delta = 1))
        assertEquals(5, MusicSections.jumpTarget(artists, focus = 3, delta = 1))
    }

    @Test
    fun `jumping back from inside a section goes to the previous section's start`() {
        assertEquals(1, MusicSections.jumpTarget(artists, focus = 4, delta = -1))
    }

    @Test
    fun `jumps wrap and skip the recent and unknown columns`() {
        assertEquals(1, MusicSections.jumpTarget(artists, focus = 5, delta = 1))
        assertEquals(5, MusicSections.jumpTarget(artists, focus = 1, delta = -1))
        assertEquals(1, MusicSections.jumpTarget(artists, focus = 0, delta = 1))
    }

    @Test
    fun `a single section has nowhere to jump`() {
        assertNull(MusicSections.jumpTarget(artists.take(3), focus = 1, delta = 1))
    }
}
