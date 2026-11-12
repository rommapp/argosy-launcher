package com.nendo.argosy.data.remote.romm

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class RomMStreamSlotCapabilities(
    @Json(name = "max_slots") val maxSlots: Int = 0,
    @Json(name = "has_autosave") val hasAutosave: Boolean = false,
    @Json(name = "autosave_slot") val autosaveSlot: Int = 0,
    @Json(name = "has_memory_card") val hasMemoryCard: Boolean = false,
    @Json(name = "supports_disc_swap") val supportsDiscSwap: Boolean = false,
    @Json(name = "has_manual_disc_swap") val hasManualDiscSwap: Boolean = false
)

@JsonClass(generateAdapter = true)
data class RomMStreamingContainer(
    @Json(name = "platform") val platform: String,
    @Json(name = "host") val host: String = "",
    @Json(name = "label") val label: String = "",
    @Json(name = "capabilities") val capabilities: RomMStreamSlotCapabilities = RomMStreamSlotCapabilities(),
    @Json(name = "emulator") val emulator: String = "",
    @Json(name = "supports_memory_cards") val supportsMemoryCards: Boolean = false,
    @Json(name = "supports_save_picker") val supportsSavePicker: Boolean = false,
    @Json(name = "supports_live_states") val supportsLiveStates: Boolean = false,
    @Json(name = "import_kinds") val importKinds: List<String> = emptyList()
) {
    val importsSaves: Boolean get() = IMPORT_KIND_SAVE in importKinds

    companion object {
        const val IMPORT_KIND_SAVE = "save"
    }
}

@JsonClass(generateAdapter = true)
data class RomMStreamingConfig(
    @Json(name = "enabled") val enabled: Boolean = false,
    @Json(name = "containers") val containers: List<RomMStreamingContainer> = emptyList(),
    @Json(name = "emulator_labels") val emulatorLabels: Map<String, String> = emptyMap()
)

@JsonClass(generateAdapter = true)
data class RomMStreamStatePicks(
    @Json(name = "native") val native: List<Long> = emptyList(),
    @Json(name = "pickable") val pickable: List<Long> = emptyList()
)

@JsonClass(generateAdapter = true)
data class RomMStreamSave(
    @Json(name = "id") val id: Long,
    @Json(name = "file_name") val fileName: String,
    @Json(name = "emulator") val emulator: String? = null,
    @Json(name = "updated_at") val updatedAt: String = "",
    @Json(name = "missing_from_fs") val missingFromFs: Boolean = false,
    @Json(name = "channel_id") val channelId: String? = null,
    @Json(name = "slot") val slot: String? = null
)

@JsonClass(generateAdapter = true)
data class RomMStreamState(
    @Json(name = "id") val id: Long,
    @Json(name = "file_name") val fileName: String,
    @Json(name = "emulator") val emulator: String? = null,
    @Json(name = "updated_at") val updatedAt: String = "",
    @Json(name = "created_at") val createdAt: String? = null,
    @Json(name = "username") val username: String? = null
)

/**
 * The parts of `GET /api/roms/{id}` the stream launch reads: the caller's saves, the caller's and
 * other users' public states, the saves a snapshot holds, and the files a disc swap picks from.
 */
@JsonClass(generateAdapter = true)
data class RomMStreamRomAssets(
    @Json(name = "id") val id: Long,
    @Json(name = "platform_slug") val platformSlug: String = "",
    @Json(name = "user_saves") val userSaves: List<RomMStreamSave> = emptyList(),
    @Json(name = "all_user_states") val allUserStates: List<RomMStreamState> = emptyList(),
    @Json(name = "snapshot_save_ids") val snapshotSaveIds: List<Long> = emptyList(),
    @Json(name = "files") val files: List<RomMRomFile> = emptyList()
)

@JsonClass(generateAdapter = true)
data class RomMStreamClaimRequest(
    @Json(name = "rom_id") val romId: Long,
    @Json(name = "state_id") val stateId: Long? = null,
    @Json(name = "save_id") val saveId: Long? = null,
    @Json(name = "card_import") val cardImport: String? = null,
    @Json(name = "multiplayer") val multiplayer: Boolean = false
)

@JsonClass(generateAdapter = true)
data class RomMStreamLaunching(
    @Json(name = "platform") val platform: String,
    @Json(name = "container") val container: String,
    @Json(name = "label") val label: String = "",
    @Json(name = "rom_name") val romName: String = "",
    @Json(name = "claimed_at") val claimedAt: String
)

@JsonClass(generateAdapter = true)
data class RomMStreamTermination(
    @Json(name = "ended_by") val endedBy: String? = null,
    @Json(name = "reason") val reason: String? = null
)

@JsonClass(generateAdapter = true)
data class RomMStreamSessionStatus(
    @Json(name = "status") val status: String,
    @Json(name = "platform") val platform: String = "",
    @Json(name = "extraction_phase") val extractionPhase: String? = null,
    @Json(name = "host") val host: String? = null,
    @Json(name = "termination") val termination: RomMStreamTermination? = null
) {
    val isActive: Boolean get() = status == STATUS_ACTIVE

    companion object {
        const val STATUS_ACTIVE = "active"
        const val STATUS_ENDED = "ended"
    }
}

@JsonClass(generateAdapter = true)
data class RomMStreamBusyDetail(
    @Json(name = "message") val message: String = "",
    @Json(name = "draining") val draining: Boolean = false,
    @Json(name = "rom_name") val romName: String? = null,
    @Json(name = "claimed_at") val claimedAt: String? = null
)

@JsonClass(generateAdapter = true)
data class RomMStreamBusyEnvelope(
    @Json(name = "detail") val detail: RomMStreamBusyDetail? = null
)

@JsonClass(generateAdapter = true)
data class RomMStreamCardSummary(
    @Json(name = "file_count") val fileCount: Int = 0,
    @Json(name = "total_bytes") val totalBytes: Long = 0,
    @Json(name = "game_codes") val gameCodes: List<String> = emptyList()
)

@JsonClass(generateAdapter = true)
data class RomMStreamCardImportDetail(
    @Json(name = "code") val code: String = "",
    @Json(name = "outcome") val outcome: String = "",
    @Json(name = "summary") val summary: RomMStreamCardSummary? = null,
    @Json(name = "reason") val reason: String? = null
) {
    companion object {
        const val CODE_IMPORT_REQUIRED = "memory_card_import_required"
        const val OUTCOME_FOUND = "found"
        const val OUTCOME_UNREADABLE = "unreadable"
    }
}

@JsonClass(generateAdapter = true)
data class RomMStreamCardImportEnvelope(
    @Json(name = "detail") val detail: RomMStreamCardImportDetail? = null
)

@JsonClass(generateAdapter = true)
data class RomMStreamJoinableSession(
    @Json(name = "container") val container: String,
    @Json(name = "label") val label: String? = null,
    @Json(name = "platform") val platform: String? = null,
    @Json(name = "rom_id") val romId: Long? = null,
    @Json(name = "rom_name") val romName: String? = null,
    @Json(name = "host_username") val hostUsername: String? = null,
    @Json(name = "claimed_at") val claimedAt: String? = null
)

@JsonClass(generateAdapter = true)
data class RomMStreamJoinableSessions(
    @Json(name = "sessions") val sessions: List<RomMStreamJoinableSession> = emptyList()
)

@JsonClass(generateAdapter = true)
data class RomMStreamJoined(
    @Json(name = "platform") val platform: String,
    @Json(name = "host") val host: String,
    @Json(name = "label") val label: String = "",
    @Json(name = "rom_id") val romId: Long? = null,
    @Json(name = "rom_name") val romName: String? = null
)

@JsonClass(generateAdapter = true)
data class RomMStreamSlotRequest(
    @Json(name = "slot") val slot: Int
)

@JsonClass(generateAdapter = true)
data class RomMStreamVolumeRequest(
    @Json(name = "level") val level: Int
)

@JsonClass(generateAdapter = true)
data class RomMStreamMuteRequest(
    @Json(name = "mute") val mute: Boolean
)

@JsonClass(generateAdapter = true)
data class RomMStreamMuteResponse(
    @Json(name = "status") val status: String = "",
    @Json(name = "mute") val mute: Boolean? = null,
    @Json(name = "platform") val platform: String = ""
)

@JsonClass(generateAdapter = true)
data class RomMStreamSwapDiscRequest(
    @Json(name = "file_id") val fileId: Long
)

@JsonClass(generateAdapter = true)
data class RomMStreamSaveAndExitRequest(
    @Json(name = "slot") val slot: Int = 0,
    @Json(name = "wait") val wait: Boolean = true
)

@JsonClass(generateAdapter = true)
data class RomMStreamSaveAndExitResponse(
    @Json(name = "status") val status: String = "",
    @Json(name = "saved") val saved: Boolean = false,
    @Json(name = "platform") val platform: String = "",
    @Json(name = "released") val released: Boolean = true
)

@JsonClass(generateAdapter = true)
data class RomMStreamReleaseResponse(
    @Json(name = "status") val status: String = "",
    @Json(name = "platform") val platform: String = ""
)
