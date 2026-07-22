package com.nendo.argosy.ui.audio

import android.net.Uri
import android.util.Log
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.remote.romm.ConnectionState
import com.nendo.argosy.data.remote.romm.RomMRepository
import com.nendo.argosy.domain.usecase.music.DownloadMusicTrackUseCase
import com.nendo.argosy.domain.usecase.music.GameThemeSource
import com.nendo.argosy.domain.usecase.music.MeasureTrackLoudnessUseCase
import com.nendo.argosy.domain.usecase.music.ResolveGameThemeUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "GameThemeAudio"

/** Plays a game's title theme as a BGM override while its detail screen is the active one. */
@Singleton
class GameThemeAudioCoordinator @Inject constructor(
    private val ambientAudioManager: AmbientAudioManager,
    private val resolveGameTheme: ResolveGameThemeUseCase,
    private val downloadMusicTrack: DownloadMusicTrackUseCase,
    private val measureTrackLoudness: MeasureTrackLoudnessUseCase,
    private val romMRepository: RomMRepository,
    private val preferencesRepository: UserPreferencesRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val downloadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val inFlightDownloads: MutableSet<Long> = ConcurrentHashMap.newKeySet()
    private var activeGameId: Long? = null
    private var resolveJob: Job? = null

    fun enter(gameId: Long) {
        if (activeGameId == gameId) return
        activeGameId = gameId
        resolveJob?.cancel()
        resolveJob = scope.launch {
            val source = resolveSource(gameId)
            if (activeGameId != gameId) return@launch
            if (source == null) {
                ambientAudioManager.clearOverride()
            } else {
                Log.d(TAG, "Playing theme for game $gameId: ${source.displayName}")
                ambientAudioManager.playOverride(source)
            }
        }
    }

    private suspend fun resolveSource(gameId: Long): AmbientOverrideSource? {
        val prefs = preferencesRepository.userPreferences.first()
        if (!prefs.gameDetailThemeEnabled || !prefs.ambientAudioEnabled) return null
        return when (val theme = resolveGameTheme(gameId)) {
            null -> null
            is GameThemeSource.Local -> AmbientOverrideSource.Local(theme.path, theme.title)
            is GameThemeSource.Stream -> buildRemoteSource(theme, prefs.rommToken)
                ?.also { cacheStreamInBackground(theme) }
        }
    }

    private fun cacheStreamInBackground(theme: GameThemeSource.Stream) {
        if (theme.platformName.isBlank() || theme.gameName.isBlank()) return
        if (!inFlightDownloads.add(theme.rommFileId)) return
        downloadScope.launch {
            try {
                downloadMusicTrack(
                    rommFileId = theme.rommFileId,
                    fileName = theme.fileName,
                    platformName = theme.platformName,
                    gameName = theme.gameName,
                    trackNumber = theme.trackNumber,
                    title = theme.title
                ).onSuccess { file ->
                    measureTrackLoudness(file.absolutePath)
                }.onFailure {
                    Log.w(TAG, "Theme cache download failed for ${theme.fileName}: ${it.message}")
                }
            } finally {
                inFlightDownloads.remove(theme.rommFileId)
            }
        }
    }

    fun exit(gameId: Long) {
        if (activeGameId != gameId) return
        activeGameId = null
        resolveJob?.cancel()
        resolveJob = null
        ambientAudioManager.clearOverride()
    }

    private fun buildRemoteSource(
        theme: GameThemeSource.Stream,
        token: String?
    ): AmbientOverrideSource.Remote? {
        if (romMRepository.connectionState.value !is ConnectionState.Connected) return null
        val url = romMRepository.buildMediaUrlPublic(
            "/api/roms/${theme.rommFileId}/files/content/${Uri.encode(theme.fileName)}"
        )
        val headers = token?.let { mapOf("Authorization" to "Bearer $it") } ?: emptyMap()
        return AmbientOverrideSource.Remote(url, headers, theme.title)
    }
}
