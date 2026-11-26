package com.nendo.argosy.ui.components.resumeplayer

import com.nendo.argosy.data.music.analysis.TrackAnalysis
import com.nendo.argosy.data.music.analysis.TrackAnalysisStore
import com.nendo.argosy.data.preferences.ControlsPreferencesRepository
import com.nendo.argosy.data.preferences.NowPlayingPreferencesRepository
import com.nendo.argosy.ui.audio.AmbientAudioManager
import com.nendo.argosy.ui.audio.AmbientMusicSwitch
import com.nendo.argosy.ui.audio.AmbientPlaybackState
import com.nendo.argosy.ui.audio.PlaybackPosition
import com.nendo.argosy.ui.components.nowplaying.currentTrackAnalysis
import com.nendo.argosy.ui.components.nowplaying.isAudible
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import javax.inject.Inject

interface ResumePlayerTransport {
    val playback: StateFlow<AmbientPlaybackState>
    val analysis: Flow<TrackAnalysis?>
    val stage: Flow<ResumeStagePreferences>
    fun positionSnapshot(): PlaybackPosition?
    suspend fun togglePlayPause()
    suspend fun skipNext()
    suspend fun skipPrevious()
    suspend fun playAt(position: Int)
}

class AmbientResumePlayerTransport @Inject constructor(
    private val ambientAudioManager: AmbientAudioManager,
    private val ambientMusicSwitch: AmbientMusicSwitch,
    private val controlsPreferences: ControlsPreferencesRepository,
    nowPlayingPreferences: NowPlayingPreferencesRepository,
    analysisStore: TrackAnalysisStore
) : ResumePlayerTransport {
    override val playback: StateFlow<AmbientPlaybackState> = ambientAudioManager.playback

    override val analysis: Flow<TrackAnalysis?> = ambientAudioManager.playback.currentTrackAnalysis(analysisStore)

    override val stage: Flow<ResumeStagePreferences> = combine(
        nowPlayingPreferences.preferences,
        controlsPreferences.preferences
    ) { nowPlaying, controls -> ResumeStagePreferences(nowPlaying, controls.ambientAudioPlayInBackground) }

    override fun positionSnapshot(): PlaybackPosition? = ambientAudioManager.positionSnapshot()

    override suspend fun togglePlayPause() {
        if (ambientAudioManager.playback.value.isAudible) {
            ambientAudioManager.pause()
            return
        }
        ambientMusicSwitch.ensureEnabled()
        ambientAudioManager.play()
    }

    override suspend fun skipNext() {
        if (musicEnabled()) ambientAudioManager.skipNext()
    }

    override suspend fun skipPrevious() {
        if (musicEnabled()) ambientAudioManager.skipPrevious()
    }

    override suspend fun playAt(position: Int) {
        ambientMusicSwitch.ensureEnabled()
        ambientAudioManager.playAt(position)
    }

    private suspend fun musicEnabled(): Boolean = controlsPreferences.preferences.first().ambientAudioEnabled
}
