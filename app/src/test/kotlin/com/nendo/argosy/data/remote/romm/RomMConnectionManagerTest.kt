package com.nendo.argosy.data.remote.romm

import android.content.Context
import com.nendo.argosy.data.preferences.UserPreferences
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.repository.BiosRepository
import com.nendo.argosy.data.repository.RomMAccountRepository
import com.nendo.argosy.data.repository.SaveSyncRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.Response
import java.util.concurrent.atomic.AtomicInteger

class RomMConnectionManagerTest {

    private val api: RomMApi = mockk(relaxed = true)
    private val heartbeats = AtomicInteger()

    private val preferences: UserPreferencesRepository = mockk(relaxed = true)
    private val apiFactory: RomMApiFactory = mockk()
    private val createdUrls = mutableListOf<String>()

    private fun manager(
        serverVersion: String = "5.4.0",
        stored: UserPreferences = UserPreferences(),
        reachable: Boolean = true,
        rememberedSnapshots: Boolean? = null,
        snapshots: Boolean = false
    ): RomMConnectionManager {
        every { preferences.preferences } returns flowOf(stored)
        coEvery { preferences.getRommSnapshotSupport(any()) } returns null
        coEvery { preferences.getRommSnapshotSupport("romm.local") } returns rememberedSnapshots
        every { apiFactory.create(any(), any(), any()) } answers {
            createdUrls += firstArg<String>()
            api
        }
        every { apiFactory.reachability } returns mockk(relaxed = true)
        every { apiFactory.requestedClientCertificate(any()) } returns false
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
        val accountRepository: RomMAccountRepository = mockk(relaxed = true)
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
    fun `a client certificate rejection over https survives the plain http fallback`() = runBlocking {
        val manager = manager()
        val tlsApi: RomMApi = mockk(relaxed = true)
        coEvery { tlsApi.heartbeat() } throws javax.net.ssl.SSLHandshakeException(
            "error:10000074:SSL routines:OPENSSL_internal:TLSV1_ALERT_CERTIFICATE_REQUIRED"
        )
        coEvery { api.heartbeat() } returns Response.error(
            400,
            "The plain HTTP request was sent to HTTPS port".toResponseBody()
        )
        every { apiFactory.create(match { it.startsWith("https://") }, any(), any()) } answers {
            createdUrls += firstArg<String>()
            tlsApi
        }

        val result = manager.connect("romm.example:8443")

        assertEquals(RomMErrorKind.CLIENT_CERTIFICATE_REQUIRED, (result as RomMResult.Error).kind)
        assertEquals(listOf("https://romm.example:8443/", "http://romm.example:8443/"), createdUrls)
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

    private companion object {
        const val HEARTBEAT_DELAY_MS = 50L
    }
}
