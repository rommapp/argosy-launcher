package com.nendo.argosy.ui.audio

import android.content.Context
import com.nendo.argosy.data.emulator.ActiveSession
import com.nendo.argosy.data.emulator.EmulatorRegistry
import com.nendo.argosy.data.emulator.GameSessionService
import com.nendo.argosy.data.emulator.PlaySessionTracker
import com.nendo.argosy.data.permissions.PermissionRegistry
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.domain.model.ArgosyPermission
import com.nendo.argosy.domain.model.PermissionAvailability
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Feeds each game session to [AmbientAudioManager] once, as it opens, and keeps the session
 * notification's mediaPlayback type in step with music playing through the game.
 */
@Singleton
class GameSessionAudioCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val playSessionTracker: PlaySessionTracker,
    private val preferencesRepository: UserPreferencesRepository,
    private val permissionRegistry: PermissionRegistry,
    private val ambientAudioManager: AmbientAudioManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var started = false

    fun start() {
        if (started) return
        started = true
        scope.launch {
            playSessionTracker.activeSession
                .map { session -> session?.let { SessionIdentity(it) } }
                .distinctUntilChanged()
                .collectLatest { identity ->
                    ambientAudioManager.setGameSession(identity?.let { audioFor(it) })
                }
        }
        scope.launch {
            combine(
                playSessionTracker.activeSession.map { it != null },
                ambientAudioManager.playback.map { it.isPlaying }
            ) { inSession, playing -> inSession && playing }
                .distinctUntilChanged()
                .collect { GameSessionService.setMusicPlaying(context, it) }
        }
    }

    private suspend fun audioFor(identity: SessionIdentity): GameSessionAudio {
        val prefs = preferencesRepository.userPreferences.first()
        return withContext(Dispatchers.IO) {
            GameSessionAudio(
                isBuiltIn = identity.isBuiltIn,
                canDrawOverlays = isGranted(ArgosyPermission.DISPLAY_OVERLAY),
                externalQuickPanelEnabled = prefs.quickPanelInEmulators && !identity.isStream,
                accessibilityServiceEnabled = isGranted(ArgosyPermission.ACCESSIBILITY),
                musicVolumePercent = prefs.ambientAudioGameVolume
            )
        }
    }

    private fun isGranted(permission: ArgosyPermission): Boolean =
        permissionRegistry.availabilityOf(permission) == PermissionAvailability.GRANTED

    private data class SessionIdentity(
        val startTime: Instant,
        val isBuiltIn: Boolean,
        val isStream: Boolean
    ) {
        constructor(session: ActiveSession) : this(
            startTime = session.startTime,
            isBuiltIn = session.emulatorPackage == EmulatorRegistry.BUILTIN_PACKAGE,
            isStream = session.isStream
        )
    }
}
