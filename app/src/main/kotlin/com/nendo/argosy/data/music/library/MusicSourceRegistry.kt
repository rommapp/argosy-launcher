package com.nendo.argosy.data.music.library

import com.nendo.argosy.data.music.navidrome.NavidromeMusicSource
import com.nendo.argosy.data.music.romm.RommMusicSource
import com.nendo.argosy.domain.model.MusicSourceKind
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MusicSourceRegistry @Inject constructor(
    navidrome: NavidromeMusicSource,
    romm: RommMusicSource
) {
    val all: List<MusicLibrarySource> = listOf(navidrome, romm)

    fun of(kind: MusicSourceKind): MusicLibrarySource = all.first { it.kind == kind }
}
