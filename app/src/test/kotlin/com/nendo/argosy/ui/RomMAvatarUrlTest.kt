package com.nendo.argosy.ui

import com.nendo.argosy.data.preferences.UserPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RomMAvatarUrlTest {

    private val prefs = UserPreferences(
        rommBaseUrl = "https://romm.example.com/",
        rommUserId = 7,
        rommAvatarPath = "users/7/profile/avatar.png",
        rommProfileUpdatedAt = "2026-11-01T10:00:00+00:00"
    )

    @Test
    fun `the url carries the profile revision`() {
        assertEquals(
            "https://romm.example.com/api/users/7/avatar?ts=2026-11-01T10%3A00%3A00%2B00%3A00",
            rommAvatarUrl(prefs)
        )
    }

    @Test
    fun `a newer profile on the same avatar path changes the url`() {
        val replaced = prefs.copy(rommProfileUpdatedAt = "2026-11-15T09:30:00+00:00")

        assertNotEquals(rommAvatarUrl(prefs), rommAvatarUrl(replaced))
    }

    @Test
    fun `a server without a revision gets the plain url`() {
        assertEquals(
            "https://romm.example.com/api/users/7/avatar",
            rommAvatarUrl(prefs.copy(rommProfileUpdatedAt = null))
        )
    }

    @Test
    fun `no avatar path means no url`() {
        assertNull(rommAvatarUrl(prefs.copy(rommAvatarPath = null)))
    }
}
