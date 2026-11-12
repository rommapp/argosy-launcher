package com.nendo.argosy.data.streaming

import com.nendo.argosy.data.remote.romm.RomMStreamSave
import com.nendo.argosy.data.remote.romm.RomMStreamState
import com.nendo.argosy.data.remote.romm.RomMStreamStatePicks
import com.nendo.argosy.data.remote.romm.RomMStreamingContainer
import com.nendo.argosy.data.repository.SaveSyncApiClient
import java.time.Instant

/**
 * The save and state choices a stream launch offers, filtered the way RomM's own player filters
 * them (frontend/src/views/Player/Stream.vue, the save and state pickers).
 */
object StreamPicker {
    fun allSaves(saves: List<RomMStreamSave>): List<RomMStreamSave> =
        saves.sortedWith(compareByDescending<RomMStreamSave> { instantOf(it.updatedAt) }.thenByDescending { it.id })

    fun nativeRestorable(saves: List<RomMStreamSave>, container: RomMStreamingContainer): List<RomMStreamSave> {
        val emulator = container.emulator.lowercase().takeIf { it.isNotBlank() } ?: return emptyList()
        return allSaves(saves).filter {
            it.emulator.orEmpty().lowercase() == emulator && it.fileName.lowercase().endsWith(ARCHIVE_SUFFIX)
        }
    }

    fun pickableSaves(saves: List<RomMStreamSave>, container: RomMStreamingContainer): List<RomMStreamSave> =
        if (container.importsSaves) allSaves(saves) else nativeRestorable(saves, container)

    /**
     * The save the claim restores when it names none: this emulator's newest archive, unless a
     * RetroArch container that imports saves has a newer bare `.srm`.
     */
    fun serverDefaultSave(saves: List<RomMStreamSave>, container: RomMStreamingContainer): RomMStreamSave? {
        val native = nativeRestorable(saves, container).firstOrNull { !it.missingFromFs }
        if (container.emulator.lowercase() != RETROARCH || !container.importsSaves) return native
        return allSaves(saves).firstOrNull { save ->
            save == native || (!save.missingFromFs && save.fileName.lowercase().endsWith(SRM_SUFFIX))
        }
    }

    fun showsSavePicker(saves: List<RomMStreamSave>, container: RomMStreamingContainer): Boolean =
        container.supportsSavePicker && pickableSaves(saves, container).isNotEmpty()

    fun defaultSave(
        saves: List<RomMStreamSave>,
        container: RomMStreamingContainer,
        activeChannelHeadSaveId: Long?
    ): RomMStreamSave? {
        val head = activeChannelHeadSaveId?.let { id -> pickableSaves(saves, container).firstOrNull { it.id == id } }
        return head ?: serverDefaultSave(saves, container)
    }

    /**
     * The `save_id` a claim carries: none where the container has no save picker, and none when
     * the choice is the save the claim would restore by itself.
     */
    fun claimSaveId(
        saves: List<RomMStreamSave>,
        container: RomMStreamingContainer,
        chosen: RomMStreamSave?
    ): Long? {
        if (!container.supportsSavePicker || chosen == null) return null
        if (pickableSaves(saves, container).none { it.id == chosen.id }) return null
        return chosen.id.takeIf { it != serverDefaultSave(saves, container)?.id }
    }

    fun pickableStates(states: List<RomMStreamState>, picks: RomMStreamStatePicks?): List<RomMStreamState> =
        byId(states, picks?.pickable)

    fun nativeStates(states: List<RomMStreamState>, picks: RomMStreamStatePicks?): List<RomMStreamState> =
        byId(states, picks?.native)

    fun defaultState(states: List<RomMStreamState>, picks: RomMStreamStatePicks?): RomMStreamState? =
        nativeStates(states, picks).firstOrNull()

    fun supportsStates(container: RomMStreamingContainer): Boolean =
        container.capabilities.maxSlots > 0 || container.capabilities.hasAutosave

    private fun byId(states: List<RomMStreamState>, ids: List<Long>?): List<RomMStreamState> {
        val wanted = ids?.toSet() ?: return emptyList()
        return states.filter { it.id in wanted }
    }

    private fun instantOf(timestamp: String): Instant =
        SaveSyncApiClient.parseTimestampOrNull(timestamp) ?: Instant.EPOCH

    private const val ARCHIVE_SUFFIX = ".zip"
    private const val SRM_SUFFIX = ".srm"
    private const val RETROARCH = "retroarch"
}
