package com.nendo.argosy.data.music.library

import com.nendo.argosy.data.local.entity.MusicListenEntity
import com.nendo.argosy.data.music.stats.MusicListenEndReason
import com.nendo.argosy.domain.model.MusicAlbum
import com.nendo.argosy.domain.model.MusicResumePoint
import com.nendo.argosy.domain.model.MusicSourceFilter
import com.nendo.argosy.domain.model.MusicSourceKind
import com.nendo.argosy.domain.music.CreditSplitter
import com.nendo.argosy.domain.music.MusicKeys

internal object MusicRecentAlbums {

    fun resolve(
        listens: List<MusicListenEntity>,
        catalog: Map<String, MusicAlbum>,
        filter: MusicSourceFilter,
        limit: Int
    ): List<MusicAlbum> =
        listens.sortedWith(compareByDescending<MusicListenEntity> { it.startedAt }.thenByDescending { it.id })
            .mapNotNull { listen -> listen.albumKey?.let { key -> catalog[key] ?: fromListen(key, listen) } }
            .filter { filter.admits(it.source) }
            .distinctBy { it.key }
            .take(limit)

    fun resumePoint(listen: MusicListenEntity): MusicResumePoint? {
        val albumKey = listen.albumKey ?: return null
        return MusicResumePoint(
            selectionId = listen.selectionId,
            albumKey = albumKey,
            trackKey = listen.trackKey,
            positionMs = listen.endPositionMs,
            completed = listen.endReason == MusicListenEndReason.COMPLETED.token
        )
    }

    private fun fromListen(albumKey: String, listen: MusicListenEntity): MusicAlbum? {
        val (kind, albumId) = MusicSourceKind.entries.firstNotNullOfOrNull { kind ->
            MusicKeys.albumIdOf(kind.token, albumKey)?.let { kind to it }
        } ?: return null
        return MusicAlbum(
            key = albumKey,
            source = kind,
            id = albumId,
            title = listen.albumTitle ?: listen.trackGameTitle ?: listen.title,
            artist = listen.artistCredit,
            artistId = null,
            year = null,
            genre = null,
            trackCount = 0,
            durationSec = 0,
            art = null,
            playCount = 0,
            downloaded = false,
            credits = CreditSplitter.split(listen.artistCredit)
        )
    }
}
