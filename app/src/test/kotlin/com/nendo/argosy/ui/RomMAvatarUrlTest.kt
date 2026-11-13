package com.nendo.argosy.ui

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.nendo.argosy.data.preferences.SyncPreferencesRepository
import com.nendo.argosy.data.preferences.UserPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RomMAvatarUrlTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `same path successful refresh changes image URL while rejected refresh preserves it`() = runTest {
        val repo = SyncPreferencesRepository(PreferenceDataStoreFactory.create(scope = backgroundScope) {
            tempFolder.newFile("avatar.preferences_pb")
        })
        val baseUrl = "https://romm.example/"
        repo.setRomMCredentials(baseUrl, "token", "player", 7, "avatar.png")
        suspend fun imageUrl(): String? {
            val prefs = repo.preferences.first()
            return rommAvatarUrl(UserPreferences(
                rommBaseUrl = prefs.rommBaseUrl,
                rommUserId = prefs.rommUserId,
                rommAvatarPath = prefs.rommAvatarPath,
                rommProfileRevision = prefs.rommProfileRevision
            ))
        }
        val before = imageUrl()
        repo.refreshRomMProfile("token", 7, baseUrl, "player", "avatar.png")
        val refreshed = imageUrl()
        assertNotEquals(before, refreshed)
        assertEquals("${baseUrl}api/users/7/avatar?revision=${repo.preferences.first().rommProfileRevision}", refreshed)
        repo.refreshRomMProfile("stale-token", 7, baseUrl, "player", "avatar.png")
        assertEquals(refreshed, imageUrl())
        repo.refreshRomMProfile("token", 7, baseUrl, "player", null)
        assertNull(imageUrl())
    }
}
