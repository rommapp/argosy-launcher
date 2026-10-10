package com.nendo.argosy.ui.screens.settings.delegates

import android.content.Context
import com.nendo.argosy.R
import com.nendo.argosy.data.steam.LibrarySyncState
import com.nendo.argosy.data.steam.QrAuthState
import com.nendo.argosy.data.steam.SteamAuthManager
import com.nendo.argosy.data.steam.SteamConnectionState
import com.nendo.argosy.data.steam.SteamLibraryManager
import com.nendo.argosy.data.steam.SteamService
import com.nendo.argosy.data.steam.SteamServiceState
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SteamSignInFailureTest {

    private val scope = TestScope(StandardTestDispatcher())
    private val serviceState = MutableStateFlow(SteamServiceState())
    private val service: SteamService = mockk(relaxed = true) {
        every { state } returns serviceState
    }
    private val authManager: SteamAuthManager = mockk(relaxed = true) {
        every { qrAuthState } returns MutableStateFlow<QrAuthState>(QrAuthState.Idle)
        coEvery { getActiveAccount() } returns null
    }
    private val libraryManager: SteamLibraryManager = mockk(relaxed = true) {
        every { syncState } returns MutableStateFlow<LibrarySyncState>(LibrarySyncState.Idle)
    }
    private val context: Context = mockk(relaxed = true) {
        every { getString(R.string.settings_steam_error_unreachable) } returns UNREACHABLE
    }

    private val delegate = SteamSettingsDelegate(
        steamRepository = mockk(relaxed = true),
        steamAuthManager = authManager,
        steamLibraryManager = libraryManager,
        steamContentManager = mockk(relaxed = true),
        androidDataAccessor = mockk(relaxed = true),
        notificationManager = mockk(relaxed = true),
        emulatorDownloadManager = mockk(relaxed = true),
        emulatorUpdateRepository = mockk(relaxed = true),
        steamIgdbResolver = mockk(relaxed = true),
        preferencesRepository = mockk(relaxed = true),
        steamPathResolver = mockk(relaxed = true),
        gameRepository = mockk(relaxed = true),
        platformRepository = mockk(relaxed = true),
        storagePrefs = mockk(relaxed = true),
        gameNativeStoreSync = mockk(relaxed = true)
    )

    private fun startSignIn() {
        delegate.bindService(service, scope)
        delegate.connectToSteam(context, scope)
        scope.runCurrent()
    }

    @Test
    fun `a drop the service gives up on ends in an error`() {
        startSignIn()

        serviceState.value = SteamServiceState(connectionState = SteamConnectionState.CONNECTING)
        scope.runCurrent()
        serviceState.value = SteamServiceState(
            connectionState = SteamConnectionState.DISCONNECTED,
            error = "Connection lost"
        )
        scope.runCurrent()

        assertEquals(SteamConnectionState.DISCONNECTED, delegate.state.value.connectionState)
        assertEquals(UNREACHABLE, delegate.state.value.error)
    }

    @Test
    fun `a drop the service reconnects from still reaches the QR`() {
        startSignIn()

        serviceState.value = SteamServiceState(connectionState = SteamConnectionState.CONNECTING)
        scope.runCurrent()
        serviceState.value = SteamServiceState(connectionState = SteamConnectionState.DISCONNECTED)
        scope.runCurrent()
        serviceState.value = SteamServiceState(connectionState = SteamConnectionState.CONNECTING)
        scope.runCurrent()
        serviceState.value = SteamServiceState(connectionState = SteamConnectionState.CONNECTED)
        scope.runCurrent()

        verify(exactly = 1) { authManager.startQrAuth() }
        assertEquals(null, delegate.state.value.error)
    }

    @Test
    fun `an error left from before the attempt does not fail it`() {
        serviceState.value = SteamServiceState(
            connectionState = SteamConnectionState.DISCONNECTED,
            error = "Connection lost"
        )
        startSignIn()

        assertEquals(SteamConnectionState.CONNECTING, delegate.state.value.connectionState)
        assertEquals(null, delegate.state.value.error)
    }

    @Test
    fun `no connection within the timeout ends in an error`() {
        startSignIn()
        serviceState.value = SteamServiceState(connectionState = SteamConnectionState.CONNECTING)
        scope.runCurrent()

        scope.advanceTimeBy(TIMEOUT_MS + 1)
        scope.runCurrent()

        assertEquals(SteamConnectionState.DISCONNECTED, delegate.state.value.connectionState)
        assertEquals(UNREACHABLE, delegate.state.value.error)
    }

    @Test
    fun `a connection in time starts the QR and the timeout never fires`() {
        startSignIn()
        serviceState.value = SteamServiceState(connectionState = SteamConnectionState.CONNECTED)
        scope.runCurrent()

        scope.advanceTimeBy(TIMEOUT_MS + 1)
        scope.runCurrent()

        verify(exactly = 1) { authManager.startQrAuth() }
        assertEquals(null, delegate.state.value.error)
    }

    @Test
    fun `retrying clears the previous failure`() {
        startSignIn()
        scope.advanceTimeBy(TIMEOUT_MS + 1)
        scope.runCurrent()

        delegate.connectToSteam(context, scope)
        scope.runCurrent()

        assertEquals(SteamConnectionState.CONNECTING, delegate.state.value.connectionState)
        assertEquals(null, delegate.state.value.error)
    }

    private companion object {
        const val UNREACHABLE = "Couldn't reach Steam"
        const val TIMEOUT_MS = 75_000L
    }
}
