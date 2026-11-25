package com.nendo.argosy.ui.components.nowplaying

import com.nendo.argosy.data.music.analysis.TrackAnalysis
import com.nendo.argosy.data.music.analysis.TrackAnalysisStore
import com.nendo.argosy.domain.model.MusicQueueTrack
import com.nendo.argosy.ui.audio.AmbientPlaybackState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

val AmbientPlaybackState.audibleTrack: MusicQueueTrack?
    get() = if (overrideTitle != null) null else tracks.getOrNull(index)

@OptIn(ExperimentalCoroutinesApi::class)
fun Flow<AmbientPlaybackState>.currentTrackAnalysis(store: TrackAnalysisStore): Flow<TrackAnalysis?> =
    map { it.audibleTrack }
        .distinctUntilChanged { old, new -> old?.id == new?.id }
        .flatMapLatest { track -> track?.let(store::analysis) ?: flowOf(null) }
