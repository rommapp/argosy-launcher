package com.nendo.argosy.data.music.library

import com.nendo.argosy.domain.model.MusicPlaylist
import com.nendo.argosy.domain.model.MusicPlaylistEntry
import com.nendo.argosy.domain.model.MusicPlaylistGroup
import com.nendo.argosy.domain.model.MusicSourceKind
import com.nendo.argosy.domain.model.MusicTrack

private const val OWNED_PREFIX = "owned:"
private const val SHARED_PREFIX = "shared:"
private const val OCCURRENCE_SEPARATOR = '#'
private const val ORDER_SEPARATOR = "\n"

internal fun normalizePlaylistName(name: String): String = name.trim().lowercase()

internal fun ownedGroupKey(normalizedName: String): String = "$OWNED_PREFIX$normalizedName"

internal fun sharedGroupKey(playlist: MusicPlaylist): String = "$SHARED_PREFIX${playlist.key}"

internal fun MusicTrack.playlistTrackId(): String? = when (source) {
    MusicSourceKind.NAVIDROME -> id
    MusicSourceKind.ROMM -> romFileId?.toString()
}

private fun sourceRank(kind: MusicSourceKind): Int = when (kind) {
    MusicSourceKind.NAVIDROME -> 0
    MusicSourceKind.ROMM -> 1
}

internal fun mergePlaylistGroups(
    playlists: List<MusicPlaylist>,
    customOrders: Set<String> = emptySet()
): List<MusicPlaylistGroup> {
    val (owned, shared) = playlists.partition { it.writable }
    val ownedGroups = owned
        .groupBy { normalizePlaylistName(it.name) }
        .map { (normalized, members) ->
            val halves = members.sortedBy { sourceRank(it.source) }
            MusicPlaylistGroup(
                key = ownedGroupKey(normalized),
                name = halves.first().name.trim(),
                halves = halves,
                owned = true,
                hasCustomOrder = normalized in customOrders
            )
        }
    val sharedGroups = shared.map { playlist ->
        MusicPlaylistGroup(
            key = sharedGroupKey(playlist),
            name = playlist.name.trim(),
            halves = listOf(playlist),
            owned = false,
            hasCustomOrder = false
        )
    }
    return (ownedGroups + sharedGroups).sortedWith(compareBy({ it.name.lowercase() }, { it.key }))
}

internal fun defaultPlaylistEntries(halves: List<Pair<MusicPlaylist, List<MusicTrack>>>): List<MusicPlaylistEntry> {
    val seen = HashMap<String, Int>()
    return halves
        .sortedBy { (playlist, _) -> sourceRank(playlist.source) }
        .flatMap { (playlist, tracks) ->
            tracks.mapIndexed { position, track ->
                val occurrence = (seen[track.key] ?: 0) + 1
                seen[track.key] = occurrence
                MusicPlaylistEntry(
                    entryKey = "${track.key}$OCCURRENCE_SEPARATOR$occurrence",
                    track = track,
                    playlist = playlist,
                    position = position
                )
            }
        }
}

internal fun applyPlaylistOrder(entries: List<MusicPlaylistEntry>, saved: List<String>): List<MusicPlaylistEntry> {
    if (saved.isEmpty()) return entries
    val byKey = entries.associateBy { it.entryKey }
    val savedKeys = saved.toSet()
    val kept = saved.distinct().mapNotNull { byKey[it] }
    return kept + entries.filter { it.entryKey !in savedKeys }
}

internal fun <T> moveItem(items: List<T>, from: Int, to: Int): List<T>? {
    if (from !in items.indices || to !in items.indices) return null
    val moved = items.toMutableList()
    moved.add(to, moved.removeAt(from))
    return moved.toList()
}

internal fun dropAndRenumber(saved: List<String>, entryKey: String): List<String> {
    val trackKey = entryKey.substringBeforeLast(OCCURRENCE_SEPARATOR)
    val removed = entryKey.substringAfterLast(OCCURRENCE_SEPARATOR).toIntOrNull() ?: return saved - entryKey
    return saved.filter { it != entryKey }.map { key ->
        val occurrence = key.substringAfterLast(OCCURRENCE_SEPARATOR).toIntOrNull()
        if (key.substringBeforeLast(OCCURRENCE_SEPARATOR) == trackKey && occurrence != null && occurrence > removed) {
            "$trackKey$OCCURRENCE_SEPARATOR${occurrence - 1}"
        } else {
            key
        }
    }
}

internal fun encodePlaylistOrder(keys: List<String>): String = keys.joinToString(ORDER_SEPARATOR)

internal fun decodePlaylistOrder(entries: String): List<String> =
    entries.split(ORDER_SEPARATOR).filter { it.isNotEmpty() }
