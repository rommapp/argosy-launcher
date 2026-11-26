package com.nendo.argosy.ui.screens.music

import com.nendo.argosy.domain.model.MusicColumn
import com.nendo.argosy.domain.model.MusicColumnKind
import com.nendo.argosy.domain.model.MusicPlaylistGroup
import com.nendo.argosy.domain.model.MusicSourceKind
import com.nendo.argosy.domain.model.MusicTrack

internal fun fixtureTrack(id: String, source: MusicSourceKind = MusicSourceKind.NAVIDROME) = MusicTrack(
    key = "${source.token}:track:$id",
    source = source,
    id = id,
    title = "Track $id",
    artist = null,
    album = null,
    albumId = null,
    trackNumber = null,
    durationSec = null,
    year = null,
    genre = null,
    art = null,
    downloadedPath = null
)

internal fun fixtureGroup(name: String, owned: Boolean = true, custom: Boolean = false) = MusicPlaylistGroup(
    key = if (owned) "owned:${name.lowercase()}" else "shared:$name",
    name = name,
    halves = emptyList(),
    owned = owned,
    hasCustomOrder = custom
)

internal fun fixtureColumn(
    kind: MusicColumnKind = MusicColumnKind.PLAYLIST,
    key: String = "playlist:owned:road"
) = MusicColumn(
    key = key,
    kind = kind,
    title = "Road",
    sources = setOf(MusicSourceKind.NAVIDROME),
    albumCount = 0,
    trackCount = 3,
    durationSec = 0,
    heroArt = null,
    photo = null,
    sleeves = emptyList(),
    hiddenAlbumCount = 0,
    sleeveTitles = emptyList()
)
