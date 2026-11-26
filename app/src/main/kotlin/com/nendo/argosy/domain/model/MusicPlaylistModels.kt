package com.nendo.argosy.domain.model

/**
 * One playlist row as the user sees it. An owned group joins the user's same-named playlists on
 * every server ([halves], Navidrome first); a shared group is a single playlist of another user.
 * [hasCustomOrder] is true when a local order replaces the default Navidrome-then-RomM order.
 */
data class MusicPlaylistGroup(
    val key: String,
    val name: String,
    val halves: List<MusicPlaylist>,
    val owned: Boolean,
    val hasCustomOrder: Boolean
) {
    val sources: Set<MusicSourceKind> get() = halves.mapTo(LinkedHashSet()) { it.source }

    val trackCount: Int get() = halves.sumOf { it.trackCount }

    val durationSec: Int get() = halves.sumOf { it.durationSec }

    val art: MusicArt?
        get() = halves.firstOrNull { it.source == MusicSourceKind.NAVIDROME && it.art != null }?.art
            ?: halves.firstOrNull { it.source == MusicSourceKind.ROMM && it.art != null }?.art
}

/**
 * One track slot in a group. [position] is the 0-based index inside [playlist] as its server
 * returned it. [entryKey] is `<track key>#<occurrence>`, the occurrence counting repeats of the
 * same track from 1.
 */
data class MusicPlaylistEntry(
    val entryKey: String,
    val track: MusicTrack,
    val playlist: MusicPlaylist,
    val position: Int
)

sealed interface MusicPlaylistResult {
    data class Done(val group: MusicPlaylistGroup?) : MusicPlaylistResult

    data object NeedsRommPermission : MusicPlaylistResult

    data class NameTaken(val existing: MusicPlaylistGroup) : MusicPlaylistResult

    data object Failed : MusicPlaylistResult
}
