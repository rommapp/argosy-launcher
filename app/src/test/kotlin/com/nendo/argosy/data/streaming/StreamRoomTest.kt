package com.nendo.argosy.data.streaming

import com.nendo.argosy.data.remote.romm.RomMRomFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamRoomTest {

    @Test
    fun `an absolute room URL loads as it is`() {
        assertEquals(
            "http://192.168.1.20:3000/room?token=abc",
            StreamRoom.resolve("https://romm.example/", "http://192.168.1.20:3000/room?token=abc")
        )
    }

    @Test
    fun `a bare path resolves against RomM's origin`() {
        assertEquals(
            "https://romm.example/stream/psx/room?token=abc",
            StreamRoom.resolve("https://romm.example/sub/", "/stream/psx/room?token=abc")
        )
    }

    @Test
    fun `no host is no room`() {
        assertNull(StreamRoom.resolve("https://romm.example/", null))
        assertNull(StreamRoom.resolve("https://romm.example/", " "))
    }

    private fun file(id: Long, name: String, category: String? = null) =
        RomMRomFile(id = id, romId = 1, fileName = name, filePath = "", fileSizeBytes = 0, fullPath = "", category = category)

    @Test
    fun `discs swap only for a rom with a playlist, cue sheets first`() {
        val files = listOf(
            file(1, "Game.m3u"),
            file(2, "Game (Disc 1).cue"),
            file(3, "Game (Disc 1).bin"),
            file(4, "Game (Disc 2).cue"),
            file(5, "Game (Disc 2).bin"),
            file(6, "Manual.pdf", category = "manual")
        )
        assertEquals(listOf(2L, 4L), StreamRoom.swappableDiscs(files).map { it.id })
        assertTrue(StreamRoom.swappableDiscs(files.filterNot { it.id == 1L }).isEmpty())
    }

    @Test
    fun `without cue sheets every bootable file but the playlist is a disc`() {
        val files = listOf(file(1, "Game.m3u"), file(2, "Game (Disc 1).chd"), file(3, "Game (Disc 2).chd"))
        assertEquals(listOf(2L, 3L), StreamRoom.swappableDiscs(files).map { it.id })
    }
}
