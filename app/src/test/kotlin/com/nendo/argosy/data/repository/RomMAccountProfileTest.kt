package com.nendo.argosy.data.repository

import com.nendo.argosy.data.local.dao.RomMAccountDao
import com.nendo.argosy.data.local.entity.RomMAccountEntity
import com.nendo.argosy.data.preferences.UserPreferences
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class RomMAccountProfileTest {
    private val dao = mockk<RomMAccountDao>(relaxed = true)
    private val preferences = mockk<UserPreferencesRepository>(relaxed = true)
    init {
        coEvery { dao.getActive() } returns null
    }
    private val repository = RomMAccountRepository(
        dao, mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true),
        mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true),
        mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true),
        preferences, mockk(relaxed = true)
    )
    private val account = RomMAccountEntity(
        id = 3, rommUserId = 7, username = "old", baseUrl = "https://romm.example/",
        token = "token", deviceId = "device", deviceClientVersion = "version",
        avatarPath = "old.png", isActive = true,
        lastLoginAt = Instant.parse("2026-01-01T00:00:00Z"),
        createdAt = Instant.parse("2025-01-01T00:00:00Z")
    )

    @Test
    fun `profile refresh only updates profile columns and mirrors accepted identity`() = runTest {
        coEvery { dao.refreshProfile(3, 7, "token", "renamed", "new.png") } returns 1
        repository.refreshProfile(3, 7, "token", 7, account.baseUrl, "renamed", " new.png ")
        coVerify { dao.refreshProfile(3, 7, "token", "renamed", "new.png") }
        coVerify { preferences.refreshRomMProfile("token", 7, account.baseUrl, "renamed", "new.png", null) }
        coVerify(exactly = 0) { dao.upsert(any()) }
        coVerify(exactly = 0) { dao.updateCredentials(any(), any(), any(), any(), any()) }
        coVerify(exactly = 0) { dao.setActive(any()) }
    }

    @Test
    fun `a removed switched or retokened row cannot refresh the active mirror`() = runTest {
        coEvery { dao.refreshProfile(any(), any(), any(), any(), any()) } returns 0
        repository.refreshProfile(3, 7, "token", 7, account.baseUrl, "stale", null)
        coVerify(exactly = 0) { preferences.refreshRomMProfile(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `additional pairing stores supplied avatar without changing the live mirror`() = runTest {
        coEvery { dao.getByRommUserId(7) } returns account
        val written = slot<RomMAccountEntity>()
        coEvery { dao.upsert(capture(written)) } returns 3
        repository.registerAdditional(7, "renamed", account.baseUrl, "new-token", null, null, " new.png ")
        assertEquals(account.copy(
            username = "renamed", token = "new-token", avatarPath = "new.png",
            lastLoginAt = written.captured.lastLoginAt
        ), written.captured)
        coVerify(exactly = 0) { preferences.setRomMCredentials(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `sign in replaces or removes cached avatar while retaining account history`() = runTest {
        coEvery { dao.getByRommUserId(7) } returns account
        val written = slot<RomMAccountEntity>()
        coEvery { dao.upsert(capture(written)) } returns 3
        for (avatar in listOf("new.png", " ", null)) {
            repository.onSignedIn(7, "renamed", account.baseUrl, "new-token", null, null, avatar)
            assertEquals(account.copy(
                username = "renamed", token = "new-token",
                avatarPath = avatar?.trim()?.takeIf { it.isNotEmpty() },
                lastLoginAt = written.captured.lastLoginAt
            ), written.captured)
        }
    }

    @Test
    fun `offline activation mirrors cached avatar and removes previous users avatar when absent`() = runTest {
        for (avatar in listOf("cached.png", null)) {
            coEvery { dao.getById(3) } returns account.copy(avatarPath = avatar)
            repository.activate(3)
            coVerify { preferences.setRomMCredentials(account.baseUrl, "token", "old", 7, avatar) }
        }
    }

    @Test
    fun `legacy account adoption preserves cached avatar`() = runTest {
        coEvery { dao.count() } returns 0
        every { preferences.preferences } returns flowOf(UserPreferences(
            rommBaseUrl = account.baseUrl, rommToken = "token", rommUsername = "old",
            rommUserId = 7, rommAvatarPath = "cached.png"
        ))
        val written = slot<RomMAccountEntity>()
        coEvery { dao.upsert(capture(written)) } returns 3
        repository.adoptLegacyCredentialsIfNeeded()
        assertEquals("cached.png", written.captured.avatarPath)
        assertEquals(7L, written.captured.rommUserId)
    }
}
