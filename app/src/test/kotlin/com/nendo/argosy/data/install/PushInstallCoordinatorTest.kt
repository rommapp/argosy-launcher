package com.nendo.argosy.data.install

import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.nendo.argosy.data.local.entity.RomMAccountEntity
import com.nendo.argosy.data.preferences.StoragePreferences
import com.nendo.argosy.data.preferences.StoragePreferencesRepository
import com.nendo.argosy.data.remote.romm.ConnectionState
import com.nendo.argosy.data.remote.romm.RomMApi
import com.nendo.argosy.data.remote.romm.RomMConnectionManager
import com.nendo.argosy.data.remote.romm.RomMDeviceCapabilitiesUpdate
import com.nendo.argosy.data.remote.romm.RomMDeviceSocket
import com.nendo.argosy.data.remote.romm.RomMInstallReport
import com.nendo.argosy.data.remote.romm.RomMInstallRequest
import com.nendo.argosy.data.repository.RomMAccountRepository
import com.nendo.argosy.data.setup.SetupStateRepository
import com.nendo.argosy.domain.model.SetupItem
import com.nendo.argosy.domain.model.SetupState
import com.nendo.argosy.domain.usecase.download.PushInstallFailure
import com.nendo.argosy.domain.usecase.download.PushInstallOutcome
import com.nendo.argosy.domain.usecase.download.PushInstallUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.ResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger

class PushInstallCoordinatorTest {

    private val api: RomMApi = mockk(relaxed = true)
    private val connectionManager: RomMConnectionManager = mockk(relaxed = true)
    private val storagePreferences: StoragePreferencesRepository = mockk(relaxed = true)
    private val setupStateRepository: SetupStateRepository = mockk(relaxed = true)
    private val setupState = MutableStateFlow<SetupState>(SetupState.Complete)
    private val accountRepository: RomMAccountRepository = mockk(relaxed = true)
    private val deviceSocket: RomMDeviceSocket = mockk(relaxed = true)
    private val pushInstall: PushInstallUseCase = mockk()
    private val socketEvents = MutableSharedFlow<RomMDeviceSocket.Event>()
    private val deviceIdState = MutableStateFlow<String?>(DEVICE_ID)
    private val baseUrlState = MutableStateFlow(LAN_URL)
    private val preferences = MutableStateFlow(StoragePreferences())
    private val account = RomMAccountEntity(
        rommUserId = 7,
        username = "player",
        baseUrl = "http://romm.local/",
        token = "token",
        lastLoginAt = Instant.EPOCH,
        createdAt = Instant.EPOCH
    )

    @Before
    fun setup() {
        mockkObject(ProcessLifecycleOwner.Companion)
        every { ProcessLifecycleOwner.get() } returns mockk<LifecycleOwner>(relaxed = true)
        every { connectionManager.deviceIdState } returns deviceIdState
        every { connectionManager.getDeviceId() } answers { deviceIdState.value }
        every { connectionManager.getAccessToken() } returns "token"
        every { connectionManager.baseUrlState } returns baseUrlState
        every { connectionManager.getBaseUrl() } answers { baseUrlState.value }
        every { connectionManager.getApi() } returns api
        every { storagePreferences.preferences } returns preferences
        every { setupStateRepository.state } returns setupState
        every { accountRepository.observeActiveAccount() } returns flowOf(account)
        coEvery { accountRepository.activeAccount() } returns account
        every { deviceSocket.events } returns socketEvents
        coEvery { api.updateDeviceCapabilities(any(), any()) } returns Response.success(mockk(relaxed = true))
        coEvery { api.claimInstallRequests(any()) } returns Response.success(emptyList())
        coEvery { api.reportInstallRequest(any(), any(), any()) } returns Response.success(mockk(relaxed = true))
    }

    @After
    fun tearDown() {
        unmockkObject(ProcessLifecycleOwner.Companion)
    }

    private fun coordinator(serverVersion: String, allowRemoteInstalls: Boolean): PushInstallCoordinator {
        every { connectionManager.connectionState } returns MutableStateFlow(ConnectionState.Connected(serverVersion))
        preferences.value = StoragePreferences(allowRemoteInstalls = allowRemoteInstalls)
        return PushInstallCoordinator(
            connectionManager,
            setupStateRepository,
            storagePreferences,
            accountRepository,
            deviceSocket,
            pushInstall
        )
    }

    private suspend fun awaitSocketSubscriber() {
        withTimeout(WAIT_MS) { socketEvents.subscriptionCount.first { it > 0 } }
    }

    @Test
    fun `with remote installs off nothing is claimed`() = runBlocking {
        coordinator("5.4.0", allowRemoteInstalls = false).start()

        verify(timeout = WAIT_MS) { deviceSocket.disconnect() }
        awaitSocketSubscriber()
        socketEvents.emit(RomMDeviceSocket.Event.InstallQueued)
        delay(SETTLE_MS)

        coVerify(exactly = 0) { api.claimInstallRequests(any()) }
        verify(exactly = 0) { deviceSocket.connect(any()) }
    }

    @Test
    fun `a 5_4_0 server with remote installs on reports the capability, connects and claims`() = runBlocking {
        coordinator("5.4.0", allowRemoteInstalls = true).start()

        coVerify(timeout = WAIT_MS) { api.claimInstallRequests(DEVICE_ID) }
        coVerify {
            api.updateDeviceCapabilities(
                DEVICE_ID,
                RomMDeviceCapabilitiesUpdate(mapOf("remote_install" to true))
            )
        }
        verify { deviceSocket.connect(RomMDeviceSocket.Target("http://romm.local/", "token")) }
    }

    @Test
    fun `before setup completes nothing is advertised, connected or claimed`() = runBlocking {
        setupState.value = SetupState.Incomplete(SetupItem.SAVE_SYNC)
        coordinator("5.4.0", allowRemoteInstalls = true).start()

        verify(timeout = WAIT_MS) { deviceSocket.disconnect() }
        awaitSocketSubscriber()
        socketEvents.emit(RomMDeviceSocket.Event.InstallQueued)
        delay(SETTLE_MS)

        coVerify(exactly = 0) { api.claimInstallRequests(any()) }
        coVerify(exactly = 0) { api.updateDeviceCapabilities(any(), any()) }
        verify(exactly = 0) { deviceSocket.connect(any()) }
    }

    @Test
    fun `completing setup drains the requests pushed during it`() = runBlocking {
        setupState.value = SetupState.Incomplete(SetupItem.SAVE_SYNC)
        coordinator("5.4.0", allowRemoteInstalls = true).start()
        verify(timeout = WAIT_MS) { deviceSocket.disconnect() }

        setupState.value = SetupState.Complete

        coVerify(timeout = WAIT_MS) { api.claimInstallRequests(DEVICE_ID) }
    }

    @Test
    fun `a server below 5_4_0 is never claimed from`() = runBlocking {
        coordinator("5.3.9", allowRemoteInstalls = true).start()

        verify(timeout = WAIT_MS) { deviceSocket.disconnect() }
        awaitSocketSubscriber()
        socketEvents.emit(RomMDeviceSocket.Event.InstallQueued)
        delay(SETTLE_MS)

        coVerify(exactly = 0) { api.claimInstallRequests(any()) }
        coVerify(exactly = 0) { api.updateDeviceCapabilities(any(), any()) }
    }

    @Test
    fun `a new device id gets its capability reported`() = runBlocking {
        coordinator("5.4.0", allowRemoteInstalls = true).start()
        coVerify(timeout = WAIT_MS) { api.updateDeviceCapabilities(DEVICE_ID, any()) }

        deviceIdState.value = OTHER_DEVICE_ID

        coVerify(timeout = WAIT_MS) { api.updateDeviceCapabilities(OTHER_DEVICE_ID, any()) }
    }

    @Test
    fun `a refused socket is not reopened until the device id changes`() = runBlocking {
        coordinator("5.4.0", allowRemoteInstalls = true).start()
        verify(timeout = WAIT_MS, exactly = 1) { deviceSocket.connect(any()) }
        awaitSocketSubscriber()

        socketEvents.emit(RomMDeviceSocket.Event.Refused("unauthorized"))
        delay(SETTLE_MS)
        preferences.value = StoragePreferences(allowRemoteInstalls = false)
        delay(SETTLE_MS)
        preferences.value = StoragePreferences(allowRemoteInstalls = true)
        delay(SETTLE_MS)

        verify(exactly = 1) { deviceSocket.connect(any()) }

        deviceIdState.value = OTHER_DEVICE_ID

        verify(timeout = WAIT_MS, exactly = 2) { deviceSocket.connect(any()) }
    }

    @Test
    fun `switching to another server address reopens the socket there`() = runBlocking {
        coordinator("5.4.0", allowRemoteInstalls = true).start()
        verify(timeout = WAIT_MS) { deviceSocket.connect(RomMDeviceSocket.Target(LAN_URL, "token")) }

        baseUrlState.value = WAN_URL

        verify(timeout = WAIT_MS) { deviceSocket.connect(RomMDeviceSocket.Target(WAN_URL, "token")) }
    }

    @Test
    fun `the startup refresh and the socket connecting share one claim`() = runBlocking {
        coordinator("5.4.0", allowRemoteInstalls = true).start()
        awaitSocketSubscriber()
        socketEvents.emit(RomMDeviceSocket.Event.Connected)

        coVerify(timeout = WAIT_MS) { api.claimInstallRequests(DEVICE_ID) }
        delay(SETTLE_MS)

        coVerify(exactly = 1) { api.claimInstallRequests(DEVICE_ID) }
    }

    @Test
    fun `triggers during a claim coalesce into one follow up claim and never overlap`() = runBlocking {
        val claimEntered = CompletableDeferred<Unit>()
        val releaseClaim = CompletableDeferred<Unit>()
        val claims = AtomicInteger()
        val inFlight = AtomicInteger()
        val maxInFlight = AtomicInteger()
        coEvery { api.claimInstallRequests(DEVICE_ID) } coAnswers {
            maxInFlight.accumulateAndGet(inFlight.incrementAndGet()) { seen, now -> maxOf(seen, now) }
            if (claims.incrementAndGet() == 1) {
                claimEntered.complete(Unit)
                releaseClaim.await()
            }
            inFlight.decrementAndGet()
            Response.success(emptyList())
        }

        coordinator("5.4.0", allowRemoteInstalls = true).start()
        withTimeout(WAIT_MS) { claimEntered.await() }
        awaitSocketSubscriber()
        repeat(OVERLAPPING_TRIGGERS) { socketEvents.emit(RomMDeviceSocket.Event.InstallQueued) }
        socketEvents.emit(RomMDeviceSocket.Event.Connected)
        delay(SETTLE_MS)
        assertEquals(1, claims.get())

        releaseClaim.complete(Unit)
        coVerify(timeout = WAIT_MS, exactly = 2) { api.claimInstallRequests(DEVICE_ID) }
        delay(SETTLE_MS)

        assertEquals(2, claims.get())
        assertEquals(1, maxInFlight.get())
    }

    @Test
    fun `each claimed request is reported exactly once`() = runBlocking {
        coEvery { api.claimInstallRequests(DEVICE_ID) } returnsMany listOf(
            Response.success(
                listOf(request("r1", 1L), request("r2", 2L), request("r3", 3L), request("r4", 4L))
            ),
            Response.success(emptyList())
        )
        coEvery { api.reportInstallRequest(DEVICE_ID, "r1", any()) } returns
            Response.error(500, ResponseBody.create(null, ""))
        coEvery { pushInstall(1L, any()) } returns PushInstallOutcome.Queued
        coEvery { pushInstall(2L, any()) } returns PushInstallOutcome.AlreadyInstalled
        coEvery { pushInstall(3L, any()) } returns PushInstallOutcome.Failed(PushInstallFailure.NO_EMULATOR)
        coEvery { pushInstall(4L, any()) } throws IllegalStateException("boom")

        coordinator("5.4.0", allowRemoteInstalls = true).start()

        coVerify(timeout = WAIT_MS, exactly = 4) { api.reportInstallRequest(DEVICE_ID, any(), any()) }
        delay(SETTLE_MS)

        coVerify(exactly = 1) { api.reportInstallRequest(DEVICE_ID, "r1", RomMInstallReport("done")) }
        coVerify(exactly = 1) {
            api.reportInstallRequest(DEVICE_ID, "r2", RomMInstallReport("already_installed"))
        }
        coVerify(exactly = 1) {
            api.reportInstallRequest(DEVICE_ID, "r3", RomMInstallReport("failed", "no emulator for this platform"))
        }
        coVerify(exactly = 1) {
            api.reportInstallRequest(DEVICE_ID, "r4", RomMInstallReport("failed", "download error: boom"))
        }
        coVerify(exactly = 4) { api.reportInstallRequest(any(), any(), any()) }
    }

    @Test
    fun `a report answered 404 is not retried and the drain carries on`() = runBlocking {
        coEvery { api.claimInstallRequests(DEVICE_ID) } returnsMany listOf(
            Response.success(listOf(request("r1", 1L), request("r2", 2L))),
            Response.success(emptyList())
        )
        coEvery { api.reportInstallRequest(DEVICE_ID, "r1", any()) } returns
            Response.error(404, ResponseBody.create(null, ""))
        coEvery { pushInstall(any(), any()) } returns PushInstallOutcome.Queued

        coordinator("5.4.0", allowRemoteInstalls = true).start()

        coVerify(timeout = WAIT_MS) { api.reportInstallRequest(DEVICE_ID, "r2", any()) }
        delay(SETTLE_MS)

        coVerify(exactly = 1) { api.reportInstallRequest(DEVICE_ID, "r1", any()) }
        coVerify(exactly = 1) { pushInstall(1L, any()) }
    }

    private fun request(id: String, romId: Long) = RomMInstallRequest(
        id = id,
        deviceId = DEVICE_ID,
        romId = romId,
        fileIds = listOf(romId * 10),
        status = "pending"
    )

    private companion object {
        const val DEVICE_ID = "device-1"
        const val OTHER_DEVICE_ID = "device-2"
        const val LAN_URL = "http://romm.local/"
        const val WAN_URL = "https://romm.example.org/"
        const val WAIT_MS = 5_000L
        const val SETTLE_MS = DRAIN_SETTLE_MS + 300L
        const val OVERLAPPING_TRIGGERS = 5
    }
}
