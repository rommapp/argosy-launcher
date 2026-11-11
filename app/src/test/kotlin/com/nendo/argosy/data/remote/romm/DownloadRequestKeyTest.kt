package com.nendo.argosy.data.remote.romm

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class DownloadRequestKeyTest {
    private val lan = "http://192.168.1.20:8080/api/roms/42/content/Game%20%23.zip?file_ids=9%2C3"
    private val wan = "https://user:secret@romm.example.org/romm/api/roms/42/content/Game%20%23.zip?file_ids=9%2C3#frag"

    @Test
    fun `addresses differing in scheme host port and base path share one key`() {
        assertEquals(DownloadRequestKey.of(lan.toHttpUrl()), DownloadRequestKey.of(wan.toHttpUrl()))
        assertEquals("/api/roms/42/content/Game%20%23.zip?file_ids=9%2C3", DownloadRequestKey.of(wan.toHttpUrl()))
    }

    @Test
    fun `query and path still distinguish requests`() {
        val reordered = "http://192.168.1.20:8080/api/roms/42/content/Game%20%23.zip?file_ids=3%2C9"
        val gameFile = "http://192.168.1.20:8080/api/roms/42/files/content/Game%20%23.zip"
        assertNotEquals(DownloadRequestKey.of(lan.toHttpUrl()), DownloadRequestKey.of(reordered.toHttpUrl()))
        assertNotEquals(DownloadRequestKey.of(lan.toHttpUrl()), DownloadRequestKey.of(gameFile.toHttpUrl()))
        assertEquals("/api/roms/42/files/content/Game%20%23.zip", DownloadRequestKey.of(gameFile.toHttpUrl()))
    }

    @Test
    fun `legacy full url key normalizes equal to the key from any address`() {
        assertEquals(DownloadRequestKey.of(lan.toHttpUrl()), DownloadRequestKey.normalize(wan))
        assertEquals(DownloadRequestKey.of(wan.toHttpUrl()), DownloadRequestKey.normalize(lan))
    }

    @Test
    fun `current form and non url keys are unchanged by normalize`() {
        val key = DownloadRequestKey.of(lan.toHttpUrl())
        assertEquals(key, DownloadRequestKey.normalize(key))
        assertEquals("synthetic", DownloadRequestKey.normalize("synthetic"))
    }
}
