package com.nendo.argosy.domain.music

import com.nendo.argosy.domain.model.MusicTrackIdentity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecordingKeyTest {

    private fun key(
        mbid: String? = null,
        isrc: String? = null,
        hash: String? = null,
        artist: String? = null,
        title: String? = "Song",
        duration: Int? = 200
    ) = RecordingKey.of(mbid, isrc, hash, artist, title, duration)

    @Test
    fun `musicbrainz id outranks isrc, hash and metadata`() {
        assertEquals("mbid:abc-1", key(mbid = " ABC-1 ", isrc = "JPTO08831620", hash = "ff", artist = "A"))
    }

    @Test
    fun `isrc is uppercased with dashes and spaces dropped, and outranks the hash`() {
        assertEquals("isrc:JPTO08831620", key(isrc = "jp-to0-88-31620", hash = "ff", artist = "A"))
        assertEquals(key(isrc = "JP TO0 8831620"), key(isrc = "JPTO08831620"))
    }

    @Test
    fun `a blank musicbrainz id falls through to the next form`() {
        assertEquals("isrc:JPTO08831620", key(mbid = "", isrc = "JPTO08831620"))
    }

    @Test
    fun `content hash outranks metadata`() {
        assertEquals("hash:ff00", key(hash = "FF00", artist = "A"))
    }

    @Test
    fun `metadata uses the primary credit, folded title and a two second bucket`() {
        assertEquals(
            "meta:yuki saito|sotsugyou remix|100",
            key(artist = "Yuki Saito, Night Tempo", title = "Sotsugyou (Remix)", duration = 200)
        )
        val fullWidthYuki = String(charArrayOf(Char(0xFF39), Char(0xFF35), Char(0xFF2B), Char(0xFF29)))
        assertEquals(
            key(artist = "Yuki Saito", title = "Sotsugyou (Remix)", duration = 200),
            key(artist = "$fullWidthYuki SAITO", title = "sotsugyou  remix!", duration = 199)
        )
        assertNotEquals(key(artist = "A", duration = 200), key(artist = "A", duration = 206))
    }

    @Test
    fun `no artist credit or no duration gives no key, never title and duration alone`() {
        assertNull(key(artist = null))
        assertNull(key(artist = "  "))
        assertNull(key(artist = "A", duration = null))
        assertNull(key(artist = "A", title = "!!!"))
    }

    @Test
    fun `identity overload reads the identity fields`() {
        val identity = MusicTrackIdentity(
            source = "navidrome",
            sourceAccountKey = "alice@nd",
            sourceTrackId = "t1",
            trackKey = "navidrome:track:t1",
            albumKey = null,
            isrc = "JPTO08831620",
            title = "Irene"
        )
        assertEquals("isrc:JPTO08831620", RecordingKey.of(identity))
    }
}
