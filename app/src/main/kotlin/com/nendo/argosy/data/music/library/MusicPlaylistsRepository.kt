package com.nendo.argosy.data.music.library

import com.nendo.argosy.data.local.dao.MusicPlaylistOrderDao
import com.nendo.argosy.data.local.entity.MusicPlaylistOrderEntity
import com.nendo.argosy.data.music.identity.MusicAccountReader
import com.nendo.argosy.domain.model.MusicPlaylist
import com.nendo.argosy.domain.model.MusicPlaylistEntry
import com.nendo.argosy.domain.model.MusicPlaylistGroup
import com.nendo.argosy.domain.model.MusicPlaylistResult
import com.nendo.argosy.domain.model.MusicQueueTrack
import com.nendo.argosy.domain.model.MusicSourceFilter
import com.nendo.argosy.domain.model.MusicSourceKind
import com.nendo.argosy.domain.model.MusicTrack
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MusicPlaylistsRepository @Inject constructor(
    private val sources: MusicSourceRegistry,
    private val orderDao: MusicPlaylistOrderDao,
    private val accountReader: MusicAccountReader
) : MusicPlaylists {

    private val _changes = MutableStateFlow(0)
    override val changes: StateFlow<Int> = _changes.asStateFlow()

    override suspend fun groups(filter: MusicSourceFilter): List<MusicPlaylistGroup> {
        val ownerKey = accountReader.current().ownerKey
        val playlists = coroutineScope {
            sources.all
                .filter { filter.admits(it.kind) && it.isConfigured() }
                .map { source -> async { source.playlists().getOrDefault(emptyList()) } }
                .awaitAll()
                .flatten()
        }
        return mergePlaylistGroups(playlists, orderDao.names(ownerKey).toSet())
    }

    override suspend fun group(key: String): MusicPlaylistGroup? =
        groups(MusicSourceFilter.ALL).firstOrNull { it.key == key }

    override suspend fun entries(group: MusicPlaylistGroup): List<MusicPlaylistEntry> {
        val ownerKey = accountReader.current().ownerKey
        val halves = halfTracks(group).map { (playlist, tracks) -> playlist to tracks.getOrDefault(emptyList()) }
        return ordered(ownerKey, group, defaultPlaylistEntries(halves))
    }

    override suspend fun addTracks(group: MusicPlaylistGroup, tracks: List<MusicTrack>): MusicPlaylistResult {
        val targets = tracks.mapNotNull { track -> track.playlistTrackId()?.let { WriteTarget(track.source, it) } }
        return addTargets(group, targets)
    }

    override suspend fun addTrack(group: MusicPlaylistGroup, track: MusicQueueTrack): MusicPlaylistResult =
        addTargets(group, listOfNotNull(track.writeTarget()))

    override suspend fun create(name: String, tracks: List<MusicTrack>): MusicPlaylistResult {
        val targets = tracks.mapNotNull { track -> track.playlistTrackId()?.let { WriteTarget(track.source, it) } }
        return createWith(name, targets)
    }

    override suspend fun create(name: String, track: MusicQueueTrack): MusicPlaylistResult =
        createWith(name, listOfNotNull(track.writeTarget()))

    override suspend fun rename(group: MusicPlaylistGroup, name: String): MusicPlaylistResult {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return MusicPlaylistResult.Failed
        val ownerKey = accountReader.current().ownerKey
        val current = this.group(group.key)?.takeIf { it.owned } ?: return MusicPlaylistResult.Failed
        val oldName = normalizePlaylistName(current.name)
        val newName = normalizePlaylistName(trimmed)
        if (newName != oldName) ownedGroup(newName)?.let { return MusicPlaylistResult.NameTaken(it) }
        val renamed = mutableListOf<MusicPlaylist>()
        for (half in current.halves) {
            val failure = sources.of(half.source).renamePlaylist(half.id, trimmed).exceptionOrNull()
            if (failure != null) {
                renamed.forEach { sources.of(it.source).renamePlaylist(it.id, it.name) }
                if (renamed.isNotEmpty()) bumpChanges()
                return failureResult(failure, newName)
            }
            renamed += half
        }
        orderDao.rename(ownerKey, oldName, newName)
        bumpChanges()
        return MusicPlaylistResult.Done(this.group(ownedGroupKey(newName)))
    }

    override suspend fun delete(group: MusicPlaylistGroup): MusicPlaylistResult {
        val ownerKey = accountReader.current().ownerKey
        val current = (this.group(group.key) ?: group).takeIf { it.owned } ?: return MusicPlaylistResult.Failed
        val results = current.halves.map { sources.of(it.source).deletePlaylist(it.id) }
        val failure = results.firstNotNullOfOrNull { it.exceptionOrNull() }
        if (failure == null) orderDao.delete(ownerKey, normalizePlaylistName(current.name))
        if (results.any { it.isSuccess }) bumpChanges()
        return if (failure == null) MusicPlaylistResult.Done(null) else failureResult(failure, null)
    }

    override suspend fun remove(group: MusicPlaylistGroup, entry: MusicPlaylistEntry): MusicPlaylistResult {
        if (!group.owned || !entry.playlist.writable) return MusicPlaylistResult.Failed
        val trackId = entry.track.playlistTrackId() ?: return MusicPlaylistResult.Failed
        val ownerKey = accountReader.current().ownerKey
        val failure = sources.of(entry.playlist.source)
            .removeFromPlaylist(entry.playlist.id, entry.position, trackId)
            .exceptionOrNull()
        if (failure != null) return failureResult(failure, null)
        val name = normalizePlaylistName(group.name)
        orderDao.get(ownerKey, name)?.let { saved ->
            val kept = dropAndRenumber(decodePlaylistOrder(saved.entries), entry.entryKey)
            orderDao.upsert(saved.copy(entries = encodePlaylistOrder(kept), updatedAt = System.currentTimeMillis()))
        }
        bumpChanges()
        return MusicPlaylistResult.Done(this.group(group.key))
    }

    override suspend fun move(group: MusicPlaylistGroup, from: Int, to: Int): MusicPlaylistResult {
        if (!group.owned) return MusicPlaylistResult.Failed
        val ownerKey = accountReader.current().ownerKey
        val fetched = halfTracks(group)
        if (fetched.any { (_, tracks) -> tracks.isFailure }) return MusicPlaylistResult.Failed
        val halves = fetched.map { (playlist, tracks) -> playlist to tracks.getOrThrow() }
        val current = ordered(ownerKey, group, defaultPlaylistEntries(halves))
        val moved = moveItem(current.map { it.entryKey }, from, to) ?: return MusicPlaylistResult.Failed
        orderDao.upsert(
            MusicPlaylistOrderEntity(
                ownerKey = ownerKey,
                playlistName = normalizePlaylistName(group.name),
                entries = encodePlaylistOrder(moved),
                updatedAt = System.currentTimeMillis()
            )
        )
        bumpChanges()
        return MusicPlaylistResult.Done(group.copy(hasCustomOrder = true))
    }

    override suspend fun resetOrder(group: MusicPlaylistGroup) {
        val ownerKey = accountReader.current().ownerKey
        orderDao.delete(ownerKey, normalizePlaylistName(group.name))
        bumpChanges()
    }

    private suspend fun addTargets(group: MusicPlaylistGroup, targets: List<WriteTarget>): MusicPlaylistResult {
        if (targets.isEmpty()) return MusicPlaylistResult.Failed
        val current = (this.group(group.key) ?: group).takeIf { it.owned } ?: return MusicPlaylistResult.Failed
        val results = targets.groupBy({ it.kind }, { it.trackId }).map { (kind, ids) ->
            val half = current.halves.firstOrNull { it.source == kind }
            if (half != null) {
                sources.of(kind).addToPlaylist(half.id, ids)
            } else {
                sources.of(kind).createPlaylist(current.name, ids).map { }
            }
        }
        return finish(current.key, results, takenName = null)
    }

    private suspend fun createWith(name: String, targets: List<WriteTarget>): MusicPlaylistResult {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || targets.isEmpty()) return MusicPlaylistResult.Failed
        val normalized = normalizePlaylistName(trimmed)
        ownedGroup(normalized)?.let { return MusicPlaylistResult.NameTaken(it) }
        val results = targets.groupBy({ it.kind }, { it.trackId }).map { (kind, ids) ->
            sources.of(kind).createPlaylist(trimmed, ids).map { }
        }
        return finish(ownedGroupKey(normalized), results, takenName = normalized)
    }

    private suspend fun finish(key: String, results: List<Result<Unit>>, takenName: String?): MusicPlaylistResult {
        if (results.any { it.isSuccess }) bumpChanges()
        val failure = results.firstNotNullOfOrNull { it.exceptionOrNull() }
        return if (failure == null) MusicPlaylistResult.Done(group(key)) else failureResult(failure, takenName)
    }

    private suspend fun failureResult(failure: Throwable, takenName: String?): MusicPlaylistResult = when (failure) {
        is MusicPermissionException -> MusicPlaylistResult.NeedsRommPermission
        is MusicPlaylistNameTakenException ->
            takenName?.let { ownedGroup(it) }?.let { MusicPlaylistResult.NameTaken(it) } ?: MusicPlaylistResult.Failed
        else -> MusicPlaylistResult.Failed
    }

    private suspend fun ownedGroup(normalizedName: String): MusicPlaylistGroup? =
        groups(MusicSourceFilter.ALL).firstOrNull { it.owned && it.key == ownedGroupKey(normalizedName) }

    private suspend fun halfTracks(group: MusicPlaylistGroup): List<Pair<MusicPlaylist, Result<List<MusicTrack>>>> =
        coroutineScope {
            group.halves
                .map { half -> async { half to sources.of(half.source).playlistTracks(half.id) } }
                .awaitAll()
        }

    private suspend fun ordered(
        ownerKey: Long,
        group: MusicPlaylistGroup,
        entries: List<MusicPlaylistEntry>
    ): List<MusicPlaylistEntry> {
        if (!group.owned) return entries
        val saved = orderDao.get(ownerKey, normalizePlaylistName(group.name)) ?: return entries
        return applyPlaylistOrder(entries, decodePlaylistOrder(saved.entries))
    }

    private fun MusicQueueTrack.writeTarget(): WriteTarget? {
        val identity = identity ?: return null
        val kind = MusicSourceKind.fromToken(identity.source) ?: return null
        return WriteTarget(kind, identity.sourceTrackId)
    }

    private fun bumpChanges() {
        _changes.update { it + 1 }
    }

    private class WriteTarget(val kind: MusicSourceKind, val trackId: String)
}
