package com.nendo.argosy.data.music.library

import com.nendo.argosy.data.local.ALauncherDatabase
import com.nendo.argosy.data.local.dao.MusicDownloadDao
import com.nendo.argosy.data.local.entity.MusicDownloadEntity
import com.nendo.argosy.data.local.entity.RomMAccountEntity
import com.nendo.argosy.data.music.identity.MusicAccountReader
import com.nendo.argosy.data.music.identity.MusicAccounts
import com.nendo.argosy.data.preferences.NavidromePreferences
import com.nendo.argosy.data.preferences.NavidromePreferencesRepository
import com.nendo.argosy.data.repository.RomMAccountRepository
import com.nendo.argosy.domain.model.MusicSourceKind
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class MusicDownloadScopeTest {

    private val serverA = MusicDownloadEntity.serverKeyOf("romm", "https://romm.a.example")!!
    private val serverB = MusicDownloadEntity.serverKeyOf("romm", "https://romm.b.example")!!
    private val navidromeServer = MusicDownloadEntity.serverKeyOf("navidrome", "https://nd.example")!!

    private fun row(
        trackKey: String = "romm:track:5",
        serverKey: String = serverA,
        ownerKey: Long = 1L,
        source: String = MusicSourceKind.ROMM.token
    ) = MusicDownloadEntity(
        trackKey = trackKey,
        serverKey = serverKey,
        ownerKey = ownerKey,
        source = source,
        trackId = trackKey,
        title = "Title",
        artist = null,
        album = null,
        albumId = null,
        trackNumber = null,
        durationSec = null,
        year = null,
        genre = null,
        artCacheKey = null,
        artLocalPath = null,
        filePath = "/music/tracks/a.mp3",
        status = MusicDownloadStatus.DONE.name,
        sizeBytes = 1L,
        downloadedAt = null
    )

    @Test
    fun `server key ignores scheme case and trailing slash`() {
        assertEquals(
            MusicDownloadEntity.serverKeyOf("romm", "http://RomM.A.example/"),
            MusicDownloadEntity.serverKeyOf("romm", "https://romm.a.example")
        )
        assertEquals("romm:romm.a.example", serverA)
    }

    @Test
    fun `server key is null without an address`() {
        assertNull(MusicDownloadEntity.serverKeyOf("romm", null))
        assertNull(MusicDownloadEntity.serverKeyOf("romm", "  "))
    }

    @Test
    fun `same server and track from another owner matches`() {
        val servers = MusicDownloadServers(romm = serverA, navidrome = null)
        assertTrue(servers.matches(row(ownerKey = 99L), "romm:track:5"))
    }

    @Test
    fun `same track id from a different server does not match`() {
        val servers = MusicDownloadServers(romm = serverB, navidrome = null)
        assertFalse(servers.matches(row(), "romm:track:5"))
    }

    @Test
    fun `a different track on the same server does not match`() {
        val servers = MusicDownloadServers(romm = serverA, navidrome = null)
        assertFalse(servers.matches(row(), "romm:track:6"))
    }

    @Test
    fun `a row is checked against its own source server`() {
        val servers = MusicDownloadServers(romm = serverA, navidrome = navidromeServer)
        assertFalse(servers.matches(row(serverKey = serverA, source = MusicSourceKind.NAVIDROME.token), "romm:track:5"))
        assertTrue(
            servers.matches(
                row(trackKey = "navidrome:track:x", serverKey = navidromeServer, source = MusicSourceKind.NAVIDROME.token),
                "navidrome:track:x"
            )
        )
    }

    @Test
    fun `an unscoped row and a signed-out source never match`() {
        assertFalse(
            MusicDownloadServers(romm = serverA, navidrome = null)
                .matches(row(serverKey = MusicDownloadEntity.UNSCOPED_SERVER_KEY), "romm:track:5")
        )
        assertFalse(MusicDownloadServers(romm = null, navidrome = null).matches(row(), "romm:track:5"))
    }

    @Test
    fun `keys list only the signed-in sources`() {
        assertEquals(listOf(serverA), MusicDownloadServers(romm = serverA, navidrome = null).keys)
        assertEquals(listOf(serverA, navidromeServer), MusicDownloadServers(serverA, navidromeServer).keys)
    }

    @Test
    fun `reader adopts unscoped rows once per signed-in server`() = runTest {
        val dao = mockk<MusicDownloadDao>(relaxed = true)
        val database = mockk<ALauncherDatabase>()
        every { database.musicDownloadDao() } returns dao
        val accountReader = mockk<MusicAccountReader>()
        coEvery { accountReader.current() } returns MusicAccounts(7L, "", "", "")
        val accounts = mockk<RomMAccountRepository>()
        every { accounts.observeActiveAccount() } returns flowOf(account(7L, "https://romm.a.example/", true))
        val navidrome = mockk<NavidromePreferencesRepository>()
        every { navidrome.preferences } returns flowOf(NavidromePreferences(serverUrl = null))
        val reader = MusicDownloadScopeReader(database, accountReader, accounts, navidrome)

        val scope = reader.current()
        reader.current()

        assertEquals(MusicDownloadScope(7L, MusicDownloadServers(romm = serverA, navidrome = null)), scope)
        coVerify(exactly = 1) { dao.adoptUnscoped(MusicSourceKind.ROMM.token, serverA, 7L) }
        coVerify(exactly = 0) { dao.adoptUnscoped(MusicSourceKind.NAVIDROME.token, any(), any()) }
    }

    private fun account(userId: Long, baseUrl: String, active: Boolean) = RomMAccountEntity(
        id = userId,
        rommUserId = userId,
        username = "user$userId",
        baseUrl = baseUrl,
        token = "token",
        isActive = active,
        lastLoginAt = Instant.EPOCH,
        createdAt = Instant.EPOCH
    )
}
