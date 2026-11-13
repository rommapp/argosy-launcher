package com.nendo.argosy.data.preferences

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.Instant

/**
 * The device id identifies this device to one RomM instance. An instance answers on more than
 * one address, so moving between them must keep the id; only a different user drops it.
 */
class SyncPreferencesCredentialsTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun repository(scope: CoroutineScope): SyncPreferencesRepository {
        val file = tempFolder.newFile("settings.preferences_pb")
        return SyncPreferencesRepository(PreferenceDataStoreFactory.create(scope = scope) { file })
    }

    @Test
    fun `changing the address in use keeps the device id`() = runTest {
        val repo = repository(CoroutineScope(StandardTestDispatcher(testScheduler)))
        repo.setRomMCredentials("https://romm.example.com/", "token", "player", 7)
        repo.setRommDeviceId("device-1", "2.15.1")

        repo.setRomMCredentials("http://192.168.1.10:8080/", "token")

        val prefs = repo.preferences.first()
        assertEquals("http://192.168.1.10:8080/", prefs.rommBaseUrl)
        assertEquals("device-1", prefs.rommDeviceId)
        assertEquals("2.15.1", prefs.rommDeviceClientVersion)
        assertEquals(7L, prefs.rommUserId)
    }

    @Test
    fun `a different user drops the device id`() = runTest {
        val repo = repository(CoroutineScope(StandardTestDispatcher(testScheduler)))
        repo.setRomMCredentials("https://romm.example.com/", "token", "player", 7)
        repo.setRommDeviceId("device-1", "2.15.1")

        repo.setRomMCredentials("https://romm.example.com/", "other-token", "other", 8)

        val prefs = repo.preferences.first()
        assertNull(prefs.rommDeviceId)
        assertNull(prefs.rommDeviceClientVersion)
        assertEquals(8L, prefs.rommUserId)
    }

    @Test
    fun `a different user drops the play-session pull time`() = runTest {
        val repo = repository(CoroutineScope(StandardTestDispatcher(testScheduler)))
        repo.setRomMCredentials("https://romm.example.com/", "token", "player", 7)
        repo.setRommPlaySessionLastPull(Instant.parse("2026-10-13T12:00:00Z"))

        repo.setRomMCredentials("https://romm.example.com/", "other-token", "other", 8)

        assertNull(repo.getRommPlaySessionLastPull())
    }

    @Test
    fun `the same user keeps the play-session pull time`() = runTest {
        val repo = repository(CoroutineScope(StandardTestDispatcher(testScheduler)))
        val lastPull = Instant.parse("2026-10-13T12:00:00Z")
        repo.setRomMCredentials("https://romm.example.com/", "token", "player", 7)
        repo.setRommPlaySessionLastPull(lastPull)

        repo.setRomMCredentials("http://192.168.1.10:8080/", "token", "player", 7)

        assertEquals(lastPull, repo.getRommPlaySessionLastPull())
    }

    @Test
    fun `the same user on a new address keeps the device id`() = runTest {
        val repo = repository(CoroutineScope(StandardTestDispatcher(testScheduler)))
        repo.setRomMCredentials("https://romm.example.com/", "token", "player", 7)
        repo.setRommDeviceId("device-1", "2.15.1")

        repo.setRomMCredentials("http://192.168.1.10:8080/", "token", "player", 7)

        assertEquals("device-1", repo.preferences.first().rommDeviceId)
    }

    @Test
    fun `profile refresh updates username and avatar without changing session metadata`() = runTest {
        val repo = repository(backgroundScope)
        val lastPull = Instant.parse("2026-10-13T12:00:00Z")
        repo.setRomMCredentials("https://romm.example.com/", "token", "player", 7)
        repo.setRommDeviceId("device-1", "2.15.1")
        repo.setRommPlaySessionLastPull(lastPull)
        val before = repo.preferences.first()

        for (avatar in listOf(" avatar.png ", "replacement.png", "replacement.png", " ", null)) {
            val revision = repo.preferences.first().rommProfileRevision
            repo.refreshRomMProfile("token", 7, before.rommBaseUrl, "renamed", avatar)
            val after = repo.preferences.first()
            assertEquals(before.copy(
                rommUsername = "renamed",
                rommAvatarPath = avatar?.trim()?.takeIf { it.isNotEmpty() },
                rommProfileRevision = after.rommProfileRevision
            ), after)
            assertTrue(after.rommProfileRevision > revision)
            assertEquals(lastPull, repo.getRommPlaySessionLastPull())
        }
    }

    @Test
    fun `delayed profile refresh cannot overwrite a switched or signed out identity`() = runTest {
        val repo = repository(backgroundScope)
        val url = "https://romm.example.com/"
        repo.setRomMCredentials(url, "token", "player", 7, "old.png")
        repo.setRomMCredentials(url, "other-token", "other", 8, "other.png")
        val switched = repo.preferences.first()
        repo.refreshRomMProfile("token", 7, url, "stale", "stale.png")
        assertEquals(switched, repo.preferences.first())
        repo.refreshRomMProfile("other-token", 7, url, "stale", null)
        assertEquals(switched, repo.preferences.first())
        repo.refreshRomMProfile("other-token", 8, "https://different.example/", "stale", null)
        assertEquals(switched, repo.preferences.first())

        repo.clearRomMCredentials()
        val signedOut = repo.preferences.first()
        repo.refreshRomMProfile("other-token", 8, url, "stale", "stale.png")
        assertEquals(signedOut, repo.preferences.first())
    }

    @Test
    fun `token replacement rejects an old response for the same user`() = runTest {
        val repo = repository(backgroundScope)
        val url = "https://romm.example.com/"
        repo.setRomMCredentials(url, "new-token", "player", 7, "new.png")
        val before = repo.preferences.first()
        repo.refreshRomMProfile("old-token", 7, url, "stale", null)
        assertEquals(before, repo.preferences.first())
    }

    @Test
    fun `legacy credentials can refresh profile without adopting an identity`() = runTest {
        val repo = repository(backgroundScope)
        val url = "https://romm.example.com/"
        repo.setRomMCredentials(url, "token")
        repo.refreshRomMProfile("token", null, url, "legacy", "avatar.png")
        val prefs = repo.preferences.first()
        assertNull(prefs.rommUserId)
        assertEquals("legacy", prefs.rommUsername)
        assertEquals("avatar.png", prefs.rommAvatarPath)
    }
}
