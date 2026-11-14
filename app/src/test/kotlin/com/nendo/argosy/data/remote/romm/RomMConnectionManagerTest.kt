package com.nendo.argosy.data.remote.romm

import android.content.Context
import com.nendo.argosy.data.local.entity.RomMAccountEntity
import com.nendo.argosy.data.preferences.UserPreferences
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.repository.BiosRepository
import com.nendo.argosy.data.repository.RomMAccountRepository
import com.nendo.argosy.data.repository.SaveSyncRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test
import retrofit2.Response
import java.util.concurrent.atomic.AtomicInteger
import java.time.Instant

class RomMConnectionManagerTest {

    private val api: RomMApi = mockk(relaxed = true)
    private val heartbeats = AtomicInteger()

    private val preferences: UserPreferencesRepository = mockk(relaxed = true)
    private val accountRepository: RomMAccountRepository = mockk(relaxed = true)

    private fun manager(
        serverVersion: String = "5.4.0",
        stored: UserPreferences = UserPreferences(),
        reachable: Boolean = true,
        rememberedSnapshots: Boolean? = null,
        snapshots: Boolean = false
    ): RomMConnectionManager {
        coEvery { accountRepository.activeAccount() } returns null
        every { preferences.preferences } returns flowOf(stored)
        coEvery { preferences.getRommSnapshotSupport(any()) } returns null
        coEvery { preferences.getRommSnapshotSupport("romm.local") } returns rememberedSnapshots
        val apiFactory: RomMApiFactory = mockk()
        every { apiFactory.create(any(), any(), any()) } returns api
        every { apiFactory.reachability } returns mockk(relaxed = true)
        coEvery { api.heartbeat() } coAnswers {
            heartbeats.incrementAndGet()
            delay(HEARTBEAT_DELAY_MS)
            if (!reachable) throw java.io.IOException("unreachable")
            Response.success(
                RomMHeartbeatResponse(
                    system = RomMSystem(version = serverVersion),
                    saveSync = RomMSaveSyncFeatures(snapshots = snapshots)
                )
            )
        }
        coEvery { api.getCurrentUser() } returns Response.success(
            RomMUser(id = 1L, username = "player", enabled = true, role = "admin")
        )
        coEvery { api.registerDevice(any()) } returns Response.success(
            RomMDeviceRegistrationResponse(deviceId = "device-1")
        )
        val saveSyncRepository: SaveSyncRepository = mockk(relaxed = true)
        val biosRepository: BiosRepository = mockk(relaxed = true)
        val context = mockk<Context>(relaxed = true)
        every { context.getSystemService(Context.CONNECTIVITY_SERVICE) } returns
            mockk<android.net.ConnectivityManager>(relaxed = true)
        return RomMConnectionManager(
            context = context,
            userPreferencesRepository = preferences,
            saveSyncRepository = dagger.Lazy { saveSyncRepository },
            biosRepository = biosRepository,
            rommAccountRepository = dagger.Lazy { accountRepository },
            accountRemovalService = dagger.Lazy { mockk(relaxed = true) },
            syncCoordinator = dagger.Lazy { mockk(relaxed = true) },
            retroAchievementsRepository = dagger.Lazy { mockk(relaxed = true) },
            apiFactory = apiFactory
        )
    }

    private fun storedAccount() = UserPreferences(
        rommBaseUrl = "http://romm.local/",
        rommToken = "token",
        rommDeviceId = "device-1",
        rommUserId = 1L
    )

    @Test
    fun `a fresh registration publishes its device id`() = runBlocking {
        val manager = manager()

        val result = manager.connect("http://romm.local/", "token")

        assertTrue("connect: $result", result is RomMResult.Success)
        assertEquals("device-1", manager.deviceIdState.value)
    }

    @Test
    fun `overlapping initialize calls share one connection attempt`() = runBlocking {
        val single = manager(stored = storedAccount())
        single.initialize()
        val oneAttempt = heartbeats.getAndSet(0)

        val manager = manager(stored = storedAccount())
        val results = List(4) { async { manager.initialize() } }.awaitAll()

        assertEquals(oneAttempt, heartbeats.get())
        assertEquals(listOf(true, true, true, true), results)
        assertTrue(manager.isConnected())
    }

    @Test
    fun `initialize while connected with the same credentials does not reconnect`() = runBlocking {
        val manager = manager(stored = storedAccount())
        assertTrue(manager.initialize())
        val afterFirst = heartbeats.get()

        val connectedNow = manager.initialize()

        assertFalse(connectedNow)
        assertEquals(afterFirst, heartbeats.get())
    }

    @Test
    fun `a reprobe connects again even when already connected`() = runBlocking {
        val manager = manager(stored = storedAccount())
        manager.initialize()
        val afterFirst = heartbeats.get()

        val connectedNow = manager.initialize(reprobe = true)

        assertTrue(connectedNow)
        assertTrue(heartbeats.get() > afterFirst)
    }

    @Test
    fun `connecting remembers whether the server supports snapshots`() = runBlocking {
        val manager = manager(stored = storedAccount(), snapshots = true)

        manager.initialize()

        assertTrue(manager.snapshotsEnabled())
        coVerify { preferences.setRommSnapshotSupport("romm.local", true) }
    }

    @Test
    fun `offline, a server last seen with snapshots keeps snapshot saves`() = runBlocking {
        val manager = manager(stored = storedAccount(), reachable = false, rememberedSnapshots = true)

        manager.initialize()

        assertFalse(manager.isConnected())
        assertTrue(manager.snapshotsEnabled())
    }

    @Test
    fun `offline, a server never reached keeps legacy saves`() = runBlocking {
        val manager = manager(stored = storedAccount(), reachable = false, rememberedSnapshots = null)

        manager.initialize()

        assertFalse(manager.snapshotsEnabled())
    }

    @Test
    fun `connected, the live server decides over the remembered value`() = runBlocking {
        val manager = manager(serverVersion = "5.5.0", stored = storedAccount(), rememberedSnapshots = true)

        manager.initialize()

        assertFalse(manager.snapshotsEnabled())
        coVerify { preferences.setRommSnapshotSupport("romm.local", false) }
    }

    @Test
    fun `routine token checks refresh profile without signing in again`() = runBlocking {
        val manager = manager(stored = storedAccount())
        manager.initialize()
        val refreshed = RomMUser(id = 1L, username = "renamed", enabled = true, role = "viewer", avatarPath = "new.png")
        coEvery { api.getCurrentUser() } returns Response.success(refreshed)
        expireVerification(manager)

        manager.checkConnection()

        assertEquals(refreshed, manager.currentUser)
        coVerify {
            accountRepository.refreshProfile(null, 1L, "token", 1L, "http://romm.local/", "renamed", "new.png", null)
        }
        coVerify(exactly = 0) { accountRepository.onSignedIn(any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `a delayed token check after disconnect cannot resurrect current user`() = runBlocking {
        val manager = manager(stored = storedAccount())
        manager.initialize()
        expireVerification(manager)
        val requested = CompletableDeferred<Unit>()
        val response = CompletableDeferred<Response<RomMUser>>()
        coEvery { api.getCurrentUser() } coAnswers {
            requested.complete(Unit)
            response.await()
        }
        val check = async { manager.checkConnection() }
        requested.await()
        manager.disconnect()
        response.complete(Response.success(RomMUser(id = 1L, username = "stale", enabled = true, role = "admin", avatarPath = "stale.png")))
        check.await()

        assertNull(manager.currentUser)
        assertFalse(manager.isConnected())
        coVerify(exactly = 0) {
            accountRepository.refreshProfile(any(), any(), any(), any(), any(), "stale", any(), any())
        }
    }

    @Test
    fun `a delayed check after stored account switch cannot publish the outgoing profile`() = runBlocking {
        val manager = manager(stored = storedAccount())
        val stored = MutableStateFlow(storedAccount())
        every { preferences.preferences } returns stored
        manager.initialize()
        expireVerification(manager)
        val requested = CompletableDeferred<Unit>()
        val response = CompletableDeferred<Response<RomMUser>>()
        coEvery { api.getCurrentUser() } coAnswers {
            requested.complete(Unit)
            response.await()
        }
        val check = async { manager.checkConnection() }
        requested.await()
        stored.value = storedAccount().copy(rommUserId = 2L, rommToken = "other-token")
        response.complete(Response.success(RomMUser(id = 1L, username = "stale", enabled = true, role = "admin")))
        check.await()

        assertEquals("player", manager.currentUser?.username)
        coVerify(exactly = 0) {
            accountRepository.refreshProfile(any(), any(), any(), any(), any(), "stale", any(), any())
        }
    }

    @Test
    fun `token login persists verified avatar with only one identity request`() = runBlocking {
        val manager = manager(serverVersion = "4.6.0")
        coEvery { api.getCurrentUser() } returns Response.success(
            RomMUser(id = 1L, username = "player", enabled = true, role = "admin", avatarPath = "avatar.png")
        )

        assertTrue(manager.connectWithToken("http://romm.local/", "token") is RomMResult.Success)

        coVerify(exactly = 1) { api.getCurrentUser() }
        coVerify { preferences.setRomMCredentials("http://romm.local/", "token", "player", 1L, "avatar.png") }
        coVerify { accountRepository.onSignedIn(1L, "player", "http://romm.local/", "token", null, null, "avatar.png") }
    }

    @Test
    fun `offline reconnect never refreshes cached profile`() = runBlocking {
        val manager = manager(stored = storedAccount(), reachable = false)
        manager.initialize()
        coVerify(exactly = 0) { accountRepository.refreshProfile(any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `failed token refresh preserves the last verified profile`() = runBlocking {
        val manager = manager(stored = storedAccount())
        manager.initialize()
        val user = manager.currentUser
        expireVerification(manager)
        coEvery { api.getCurrentUser() } throws java.io.IOException("offline")

        manager.checkConnection()

        assertEquals(user, manager.currentUser)
        coVerify(exactly = 1) { accountRepository.refreshProfile(any(), any(), any(), any(), any(), any(), any(), any()) }
        manager.disconnect()
    }

    @Test
    fun `device auth finalization publishes and persists the fetched profile`() = runBlocking {
        val manager = manager()
        prepareDeviceAuth(manager)
        val user = RomMUser(id = 1L, username = "paired", enabled = true, role = "admin", avatarPath = "paired.png")
        coEvery { api.getCurrentUser() } returns Response.success(user)

        assertTrue(manager.pollDeviceAuthOnce("code") is DeviceAuthPoll.Approved)

        assertEquals(user, manager.currentUser)
        coVerify(exactly = 1) { api.getCurrentUser() }
        coVerify { preferences.setRomMCredentials("http://romm.local/", "paired-token", "paired", 1L, "paired.png") }
        coVerify { accountRepository.onSignedIn(1L, "paired", "http://romm.local/", "paired-token", null, null, "paired.png") }
    }

    @Test
    fun `additional device pairing stores its avatar without publishing over the live profile`() = runBlocking {
        val manager = manager(stored = storedAccount())
        coEvery { accountRepository.activeAccount() } returns activeAccount()
        manager.initialize()
        prepareDeviceAuth(manager)
        coEvery { api.getCurrentUser() } returns Response.success(
            RomMUser(id = 2L, username = "second", enabled = true, role = "viewer", avatarPath = "second.png")
        )

        assertTrue(manager.pollDeviceAuthOnce("code", activateOnSuccess = false) is DeviceAuthPoll.AddedAccount)

        assertEquals(1L, manager.currentUser?.id)
        assertEquals("token", manager.getAccessToken())
        coVerify { accountRepository.registerAdditional(2L, "second", "http://romm.local/", "paired-token", "paired-device", any(), "second.png") }
        coVerify(exactly = 0) { preferences.setRomMCredentials(any(), "paired-token", any(), any(), any()) }
    }

    @Test
    fun `pairing the active identity rotates stored and live credentials and remains reconnectable`() = runBlocking {
        val manager = manager(stored = storedAccount())
        val stored = MutableStateFlow(storedAccount())
        var account = activeAccount()
        every { preferences.preferences } returns stored
        coEvery { accountRepository.activeAccount() } coAnswers { account }
        coEvery { accountRepository.accounts() } coAnswers { listOf(account) }
        coEvery { preferences.setRomMCredentials(any(), any(), any(), any(), any()) } coAnswers {
            stored.value = stored.value.copy(
                rommBaseUrl = firstArg(), rommToken = secondArg(), rommUsername = thirdArg(),
                rommUserId = arg(3), rommAvatarPath = arg(4)
            )
        }
        coEvery { accountRepository.onSignedIn(any(), any(), any(), any(), any(), any(), any()) } coAnswers {
            account = account.copy(username = arg(1), baseUrl = arg(2), token = arg(3), avatarPath = arg(6))
            account.id
        }
        assertTrue(manager.initialize())
        prepareDeviceAuth(manager)
        coEvery { api.getCurrentUser() } returns Response.success(
            RomMUser(id = 1L, username = "repaired", enabled = true, role = "admin", avatarPath = "repaired.png")
        )

        val paired = manager.pollDeviceAuthOnce("code", activateOnSuccess = false)

        assertTrue(paired is DeviceAuthPoll.Approved)
        assertEquals("paired-token", account.token)
        assertEquals(account.token, stored.value.rommToken)
        assertEquals(account.token, manager.getAccessToken())
        assertEquals("repaired", manager.currentUser?.username)
        coVerify(exactly = 2) { api.getCurrentUser() }
        coVerify(exactly = 0) { accountRepository.registerAdditional(any(), any(), any(), any(), any(), any(), any()) }

        expireVerification(manager)
        manager.checkConnection()
        assertTrue(manager.reconnectWithStoredAddresses() is RomMResult.Success)
        assertEquals("paired-token", manager.getAccessToken())
        coVerify { accountRepository.refreshProfile(account.id, 1L, "paired-token", 1L, "http://romm.local/", "repaired", "repaired.png", null) }
    }

    @Test
    fun `delayed pairing after signout or switch cannot publish credentials`() = runBlocking {
        for (signedOut in listOf(true, false)) {
            val manager = manager(stored = storedAccount())
            val stored = MutableStateFlow(storedAccount())
            every { preferences.preferences } returns stored
            assertTrue(manager.initialize())
            prepareDeviceAuth(manager)
            val requested = CompletableDeferred<Unit>()
            val response = CompletableDeferred<Response<RomMUser>>()
            coEvery { api.getCurrentUser() } coAnswers {
                requested.complete(Unit)
                response.await()
            }
            val pairing = async { manager.pollDeviceAuthOnce("code", activateOnSuccess = false) }
            requested.await()
            if (signedOut) manager.disconnect()
            stored.value = if (signedOut) UserPreferences() else storedAccount().copy(rommUserId = 2L, rommToken = "other-token")
            response.complete(Response.success(RomMUser(id = 1L, username = "stale", enabled = true, role = "admin")))

            val failure = pairing.await() as DeviceAuthPoll.Failed
            assertEquals(RomMErrorKind.SESSION_CHANGED, failure.kind)
            assertFalse(failure.retryable)
            assertEquals(if (signedOut) null else "token", manager.getAccessToken())
            coVerify(exactly = 0) { preferences.setRomMCredentials(any(), "paired-token", any(), any(), any()) }
        }
    }

    @Test
    fun `unavailable paired identity returns a structured terminal failure`() = runBlocking {
        for (activate in listOf(true, false)) {
            val manager = manager()
            prepareDeviceAuth(manager)
            coEvery { api.getCurrentUser() } throws java.io.IOException("offline")

            val failure = manager.pollDeviceAuthOnce("code", activateOnSuccess = activate) as DeviceAuthPoll.Failed

            assertEquals(RomMErrorKind.IDENTITY_UNAVAILABLE, failure.kind)
            assertFalse(failure.retryable)
            coVerify(exactly = 0) { preferences.setRomMCredentials(any(), any(), any(), any(), any()) }
        }
    }

    @Test
    fun `cancelled identity request is not converted into a failed approval`() = runBlocking {
        val manager = manager()
        prepareDeviceAuth(manager)
        coEvery { api.getCurrentUser() } throws CancellationException("cancelled")

        val result = runCatching { manager.pollDeviceAuthOnce("code") }

        assertTrue(result.exceptionOrNull() is CancellationException)
        coVerify(exactly = 0) { preferences.setRomMCredentials(any(), any(), any(), any(), any()) }
    }

    private fun activeAccount() = RomMAccountEntity(
        id = 3, rommUserId = 1, username = "player", baseUrl = "http://romm.local/",
        token = "token", deviceId = "device-1", isActive = true,
        lastLoginAt = Instant.EPOCH, createdAt = Instant.EPOCH
    )

    @Test
    fun `stored user mismatch returns structured failure without persisting candidate profile`() = runBlocking {
        val manager = manager(stored = storedAccount())
        coEvery { api.getCurrentUser() } returns Response.success(
            RomMUser(id = 2L, username = "different", enabled = true, role = "admin", avatarPath = "different.png")
        )

        val failure = manager.reconnectWithStoredAddresses() as RomMResult.Error

        assertEquals(RomMErrorKind.IDENTITY_UNAVAILABLE, failure.kind)
        assertNull(manager.currentUser)
        coVerify(exactly = 0) { accountRepository.refreshProfile(any(), any(), any(), any(), any(), any(), any(), any()) }
        coVerify(exactly = 0) { preferences.setRomMCredentials(any(), any(), any(), any(), any()) }
        coVerify(exactly = 0) { accountRepository.onSignedIn(any(), any(), any(), any(), any(), any(), any()) }
        manager.disconnect()
    }

    private fun prepareDeviceAuth(manager: RomMConnectionManager) {
        RomMConnectionManager::class.java.getDeclaredField("deviceAuthApi").apply {
            isAccessible = true
            set(manager, api)
        }
        RomMConnectionManager::class.java.getDeclaredField("deviceAuthBaseUrl").apply {
            isAccessible = true
            set(manager, "http://romm.local/")
        }
        coEvery { api.deviceAuthToken(any()) } returns Response.success(
            RomMDeviceAuthTokenResponse("paired-token", "paired-device")
        )
    }

    private fun expireVerification(manager: RomMConnectionManager) {
        RomMConnectionManager::class.java.getDeclaredField("lastTokenVerifiedAt").apply {
            isAccessible = true
            setLong(manager, -60_000L)
        }
    }

    private companion object {
        const val HEARTBEAT_DELAY_MS = 50L
    }
}
