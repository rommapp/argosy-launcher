package com.nendo.argosy.data.remote.romm

import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test

class RomMRepositoryMediaAuthHeadersTest {

    private val connectionManager = mockk<RomMConnectionManager>()
    private val repository = RomMRepository(
        connectionManager = connectionManager,
        apiClient = RomMApiClient(connectionManager, mockk(relaxed = true)),
        librarySyncService = mockk(relaxed = true),
        collectionSyncService = mockk(relaxed = true),
        userPropertyService = mockk(relaxed = true),
        achievementService = mockk(relaxed = true),
        galleryService = mockk(relaxed = true),
        userPreferencesRepository = mockk(relaxed = true)
    )

    private fun connectedTo(baseUrl: String) {
        every { connectionManager.getBaseUrl() } returns baseUrl
    }

    private fun previewHeaders(streamUrl: String, token: String? = TOKEN): Map<String, String> {
        val url = requireNotNull(repository.buildMediaUrlPublic(streamUrl))
        return repository.mediaAuthHeaders(url, token)
    }

    @Test
    fun `a server relative stream path resolves to the server and carries the token`() {
        connectedTo("https://romm.example.com")

        assertEquals(
            "https://romm.example.com/api/roms/7/files/content/track%201.flac",
            repository.buildMediaUrlPublic("/api/roms/7/files/content/track%201.flac")
        )
        assertEquals(BEARER, previewHeaders("/api/roms/7/files/content/track%201.flac"))
    }

    @Test
    fun `an absolute stream url on the server origin carries the token`() {
        connectedTo("https://romm.example.com/")

        assertEquals(BEARER, previewHeaders("https://romm.example.com/api/roms/7/files/content/a.flac"))
        assertEquals(BEARER, previewHeaders("https://ROMM.example.com:443/api/roms/7/files/content/a.flac"))
    }

    @Test
    fun `a stream url on another host plays without the token`() {
        connectedTo("https://romm.example.com")

        assertEquals(
            "https://media.example.net/a.flac",
            repository.buildMediaUrlPublic("https://media.example.net/a.flac")
        )
        assertEquals(emptyMap<String, String>(), previewHeaders("https://media.example.net/a.flac"))
        assertEquals(emptyMap<String, String>(), previewHeaders("https://romm.example.com.example.net/a.flac"))
        assertEquals(emptyMap<String, String>(), previewHeaders("https://romm.example.com@media.example.net/a.flac"))
    }

    @Test
    fun `a stream url on the server host but another port plays without the token`() {
        connectedTo("http://romm.example.com:8080")

        assertEquals(emptyMap<String, String>(), previewHeaders("http://romm.example.com:9090/a.flac"))
        assertEquals(emptyMap<String, String>(), previewHeaders("http://romm.example.com/a.flac"))
    }

    @Test
    fun `a stream url on the server host but another scheme plays without the token`() {
        connectedTo("https://romm.example.com")

        assertEquals(emptyMap<String, String>(), previewHeaders("http://romm.example.com/a.flac"))
    }

    @Test
    fun `no token is sent while disconnected`() {
        connectedTo("")

        assertEquals(emptyMap<String, String>(), previewHeaders("https://romm.example.com/a.flac"))
    }

    @Test
    fun `no header is built without a token`() {
        connectedTo("https://romm.example.com")

        assertEquals(emptyMap<String, String>(), previewHeaders("/api/roms/7/files/content/a.flac", token = null))
    }

    private companion object {
        const val TOKEN = "test-token"
        val BEARER = mapOf("Authorization" to "Bearer $TOKEN")
    }
}
