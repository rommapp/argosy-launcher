package com.nendo.argosy.data.netplay.external

import android.app.Application
import com.nendo.argosy.data.emulator.EmulatorRegistry
import com.nendo.argosy.data.emulator.PlaySessionTracker
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.util.Logger
import com.nendo.argosy.util.RootShell
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Watches the external emulator of the active play session for a running netplay session, while
 * the player shares what they are playing.
 *
 * With root it reads the emulator's own sockets. Without root only a RetroArch host is visible,
 * through the public libretro lobby.
 */
@Singleton
class ExternalNetplayDetector @Inject constructor(
    private val application: Application,
    private val playSessionTracker: PlaySessionTracker,
    private val preferencesRepository: UserPreferencesRepository,
    private val retroArchLobby: RetroArchLobby
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val debouncer = DetectionDebouncer()

    private val _current = MutableStateFlow<ExternalNetplay?>(null)
    val current: StateFlow<ExternalNetplay?> = _current.asStateFlow()

    init {
        observeSessions()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeSessions() {
        scope.launch {
            combine(
                playSessionTracker.activeSession,
                preferencesRepository.userPreferences
            ) { session, prefs ->
                session?.emulatorPackage?.takeIf {
                    prefs.isSocialLinked && prefs.socialOnlineStatusEnabled && prefs.socialShowNowPlaying
                }
            }
                .distinctUntilChanged()
                .collectLatest { packageName ->
                    debouncer.reset()
                    _current.value = null
                    val emulatorId = packageName
                        ?.takeIf { it != EmulatorRegistry.BUILTIN_PACKAGE }
                        ?.let(ExternalNetplaySignatures::emulatorIdForPackage)
                        ?: return@collectLatest
                    poll(packageName, emulatorId)
                }
        }
    }

    private suspend fun poll(packageName: String, emulatorId: String) {
        val uid = runCatching { application.packageManager.getApplicationInfo(packageName, 0).uid }.getOrNull()
        val useRoot = RootShell.isAvailable && uid != null
        val isRetroArch = ExternalNetplaySignatures.familyOf(emulatorId) == ExternalNetplaySignatures.RETROARCH_FAMILY
        if (!useRoot && !isRetroArch) return

        var tick = 0
        while (true) {
            if (useRoot) {
                _current.value = debouncer.observe(detectFromSockets(emulatorId, uid!!))
            } else if (tick % LOBBY_EVERY_TICKS == 0) {
                _current.value = debouncer.observe(detectFromLobby(emulatorId))
            }
            tick++
            delay(POLL_INTERVAL_MS)
        }
    }

    private fun detectFromSockets(emulatorId: String, uid: Int): ExternalNetplay? {
        val result = RootShell.run(application, ProcNetSockets.readScript()) ?: return null
        val role = ExternalNetplaySignatures.match(emulatorId, uid, ProcNetSockets.parse(result.output))
        return role?.let { ExternalNetplay(emulatorId, it, ExternalNetplaySource.SOCKET) }
    }

    private suspend fun detectFromLobby(emulatorId: String): ExternalNetplay? {
        val hosting = runCatching { retroArchLobby.isHosting() }
            .onFailure { Logger.warn(TAG, "lobby check failed: ${it.message}") }
            .getOrDefault(false)
        return if (hosting) ExternalNetplay(emulatorId, ExternalNetplayRole.HOST, ExternalNetplaySource.LOBBY) else null
    }

    companion object {
        private const val TAG = "ExternalNetplayDetector"
        private const val POLL_INTERVAL_MS = 15_000L
        private const val LOBBY_EVERY_TICKS = 4
    }
}
