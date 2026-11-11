package com.nendo.argosy.data.install

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.nendo.argosy.data.preferences.StoragePreferencesRepository
import com.nendo.argosy.data.remote.romm.ConnectionState
import com.nendo.argosy.data.remote.romm.RomMConnectionManager
import com.nendo.argosy.data.remote.romm.RomMDeviceCapabilitiesUpdate
import com.nendo.argosy.data.remote.romm.RomMDeviceSocket
import com.nendo.argosy.data.remote.romm.RomMInstallReport
import com.nendo.argosy.data.remote.romm.RomMInstallRequest
import com.nendo.argosy.data.remote.romm.deviceCapabilities
import com.nendo.argosy.data.repository.RomMAccountRepository
import com.nendo.argosy.data.setup.SetupStateRepository
import com.nendo.argosy.domain.model.isComplete
import com.nendo.argosy.domain.usecase.download.PushInstallFailure
import com.nendo.argosy.domain.usecase.download.PushInstallOutcome
import com.nendo.argosy.domain.usecase.download.PushInstallUseCase
import com.nendo.argosy.util.Logger
import com.nendo.argosy.util.SafeCoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "PushInstallCoordinator"
private const val HTTP_NOT_FOUND = 404
internal const val DRAIN_SETTLE_MS = 500L

/**
 * Sole sender of this device's install capability; drains the claim endpoint on start, foreground,
 * socket connect and `install:queued`. A refused socket stays closed until the token or device id
 * changes. Inert below [com.nendo.argosy.data.remote.romm.RomMCapabilities.DEVICE_INSTALL_MIN_VERSION]
 * and while setup is incomplete; requests pushed before then stay queued on the server.
 */
@Singleton
class PushInstallCoordinator @Inject constructor(
    private val connectionManager: RomMConnectionManager,
    private val setupState: SetupStateRepository,
    private val storagePreferences: StoragePreferencesRepository,
    private val accountRepository: RomMAccountRepository,
    private val deviceSocket: RomMDeviceSocket,
    private val pushInstall: PushInstallUseCase
) {
    private data class Session(
        val baseUrl: String,
        val token: String,
        val deviceId: String,
        val allowRemoteInstalls: Boolean
    ) {
        val credentials: SocketCredentials get() = SocketCredentials(token, deviceId)
    }

    private data class SocketCredentials(val token: String, val deviceId: String)

    private data class RefreshKey(
        val connectionState: ConnectionState,
        val baseUrl: String,
        val deviceId: String?,
        val allowRemoteInstalls: Boolean,
        val accountId: Long?,
        val setupComplete: Boolean
    )

    private val scope = SafeCoroutineScope(Dispatchers.IO, TAG)
    private val refreshMutex = Mutex()
    private val drainRequests = Channel<Unit>(Channel.CONFLATED)
    private var reportedCapability: Pair<String, Boolean>? = null
    private var socketCredentials: SocketCredentials? = null
    private var refusedCredentials: SocketCredentials? = null

    private val foregroundObserver = object : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) {
            scope.launch { refresh() }
        }
    }

    fun start() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(foregroundObserver)
        scope.launch {
            drainRequests.consumeEach {
                delay(DRAIN_SETTLE_MS)
                drainRequests.tryReceive()
                drainSafely()
            }
        }
        scope.launch {
            combine(
                combine(connectionManager.connectionState, connectionManager.baseUrlState, ::Pair),
                connectionManager.deviceIdState,
                storagePreferences.preferences.map { it.allowRemoteInstalls }.distinctUntilChanged(),
                accountRepository.observeActiveAccount().map { it?.id }.distinctUntilChanged(),
                setupState.state.map { it.isComplete }.distinctUntilChanged()
            ) { (state, baseUrl), deviceId, allow, accountId, setupComplete ->
                RefreshKey(state, baseUrl, deviceId, allow, accountId, setupComplete)
            }
                .distinctUntilChanged()
                .collect { refresh() }
        }
        scope.launch {
            deviceSocket.events.collect { event ->
                when (event) {
                    RomMDeviceSocket.Event.Connected, RomMDeviceSocket.Event.InstallQueued -> requestDrain()
                    is RomMDeviceSocket.Event.Refused -> onRefused(event.reason)
                }
            }
        }
    }

    private suspend fun currentSession(): Session? {
        if (!setupState.state.first().isComplete) return null
        val connected = connectionManager.connectionState.value as? ConnectionState.Connected ?: return null
        if (!connected.capabilities.supportsDeviceInstall) return null
        if (accountRepository.activeAccount() == null) return null
        val deviceId = connectionManager.getDeviceId() ?: return null
        val token = connectionManager.getAccessToken() ?: return null
        val baseUrl = connectionManager.getBaseUrl().takeIf { it.isNotBlank() } ?: return null
        val allow = storagePreferences.preferences.first().allowRemoteInstalls
        return Session(baseUrl, token, deviceId, allow)
    }

    private suspend fun refresh() {
        val session = refreshMutex.withLock {
            val current = currentSession()
            if (current == null) {
                closeSocket()
                return@withLock null
            }
            reportCapability(current)
            when {
                !current.allowRemoteInstalls -> closeSocket()
                current.credentials == refusedCredentials -> closeSocket()
                else -> {
                    refusedCredentials = null
                    socketCredentials = current.credentials
                    deviceSocket.connect(RomMDeviceSocket.Target(current.baseUrl, current.token))
                }
            }
            current
        }
        if (session?.allowRemoteInstalls == true) requestDrain()
    }

    private fun closeSocket() {
        socketCredentials = null
        deviceSocket.disconnect()
    }

    private suspend fun onRefused(reason: String) {
        refreshMutex.withLock {
            refusedCredentials = socketCredentials
            socketCredentials = null
        }
        Logger.warn(TAG, "socket refused ($reason); holding off until the token or device id changes")
    }

    private suspend fun reportCapability(session: Session) {
        val report = session.deviceId to session.allowRemoteInstalls
        if (reportedCapability == report) return
        val api = connectionManager.getApi() ?: return
        val sent = try {
            api.updateDeviceCapabilities(
                session.deviceId,
                RomMDeviceCapabilitiesUpdate(deviceCapabilities(session.allowRemoteInstalls))
            ).isSuccessful
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.warn(TAG, "reportCapability: ${e.message}")
            false
        }
        if (sent) reportedCapability = report
        Logger.info(TAG, "reportCapability: install=${session.allowRemoteInstalls} sent=$sent")
    }

    private fun requestDrain() {
        drainRequests.trySend(Unit)
    }

    private suspend fun drainSafely() {
        try {
            drainOnce()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.warn(TAG, "drain: failed", e)
        }
    }

    private suspend fun drainOnce() {
        val session = currentSession() ?: return
        if (!session.allowRemoteInstalls) return
        val api = connectionManager.getApi() ?: return
        val claimed = try {
            val response = api.claimInstallRequests(session.deviceId)
            if (!response.isSuccessful) {
                Logger.warn(TAG, "drain: claim returned ${response.code()}")
                return
            }
            response.body().orEmpty()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.warn(TAG, "drain: claim failed: ${e.message}")
            return
        }
        if (claimed.isEmpty()) return
        Logger.info(TAG, "drain: claimed ${claimed.size} install request(s)")
        for (request in claimed) {
            report(session.deviceId, request, install(request))
        }
    }

    private suspend fun install(request: RomMInstallRequest): PushInstallOutcome = try {
        pushInstall(request.romId, request.fileIds)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Logger.warn(TAG, "install: request ${request.id} for rom ${request.romId} threw", e)
        PushInstallOutcome.Failed(PushInstallFailure.DOWNLOAD_ERROR, e.message)
    }

    private suspend fun report(deviceId: String, request: RomMInstallRequest, outcome: PushInstallOutcome) {
        val body = when (outcome) {
            PushInstallOutcome.Queued -> RomMInstallReport(RomMInstallReport.STATUS_DONE)
            PushInstallOutcome.AlreadyInstalled -> RomMInstallReport(RomMInstallReport.STATUS_ALREADY_INSTALLED)
            is PushInstallOutcome.Failed -> RomMInstallReport(RomMInstallReport.STATUS_FAILED, failureReason(outcome))
        }
        Logger.info(TAG, "report: request ${request.id} rom ${request.romId} -> ${body.status} ${body.reason.orEmpty()}")
        val api = connectionManager.getApi() ?: return
        try {
            val response = api.reportInstallRequest(deviceId, request.id, body)
            when {
                response.isSuccessful -> Unit
                response.code() == HTTP_NOT_FOUND ->
                    Logger.debug(TAG, "report: request ${request.id} already handled on the server")
                else -> Logger.warn(TAG, "report: request ${request.id} returned ${response.code()}")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.warn(TAG, "report: request ${request.id} failed: ${e.message}")
        }
    }

    private fun failureReason(outcome: PushInstallOutcome.Failed): String {
        val base = when (outcome.failure) {
            PushInstallFailure.ROM_UNRESOLVED -> "rom could not be resolved"
            PushInstallFailure.NO_EMULATOR -> "no emulator for this platform"
            PushInstallFailure.NO_INSTALLABLE_FILES -> "no installable files requested"
            PushInstallFailure.DOWNLOAD_ERROR -> "download error"
        }
        val full = outcome.detail?.let { "$base: $it" } ?: base
        return full.take(RomMInstallReport.REASON_MAX_LENGTH)
    }
}
