package com.nendo.argosy.data.social.uploader

import com.nendo.argosy.data.local.entity.PlaySessionEntity
import com.nendo.argosy.data.remote.romm.RomMDevice
import com.nendo.argosy.data.remote.romm.RomMPlaySession
import com.nendo.argosy.util.parseTimestamp
import java.time.Duration
import java.time.Instant

/**
 * What a pulled session is filed under. [gameId] is the library row when the rom is in the
 * library, else the negated rom id so the row stays distinct per game without colliding with
 * any real game id. A blank [title] means the rom could not be named and the UI shows its own
 * placeholder.
 */
data class PulledGameRef(
    val gameId: Long,
    val igdbId: Long?,
    val title: String,
    val platformSlug: String
) {
    companion object {
        fun unresolved(romId: Long) = PulledGameRef(
            gameId = -romId,
            igdbId = null,
            title = "",
            platformSlug = ""
        )
    }
}

object PlaySessionRemoteMapper {
    /**
     * Null for a session that cannot be filed: an unparseable timestamp or one that does not
     * end after it starts. [PlaySessionEntity.userId] stays null so the social queue never picks
     * the row up, and [PlaySessionEntity.rommSessionId] is set so the RomM ingest never does.
     */
    fun toEntity(
        remote: RomMPlaySession,
        device: RomMDevice,
        ownerUserId: Long,
        game: PulledGameRef
    ): PlaySessionEntity? {
        val start = parseTimestamp(remote.startTime)?.let(Instant::ofEpochMilli) ?: return null
        val end = parseTimestamp(remote.endTime)?.let(Instant::ofEpochMilli) ?: return null
        if (!end.isAfter(start)) return null
        val wallMs = Duration.between(start, end).toMillis()
        val activeMs = (remote.durationMs ?: wallMs).coerceIn(0L, wallMs)
        return PlaySessionEntity(
            userId = null,
            gameId = game.gameId,
            igdbId = game.igdbId,
            gameTitle = game.title,
            platformSlug = game.platformSlug,
            startTime = start,
            endTime = end,
            continued = false,
            deviceId = device.id,
            deviceManufacturer = "",
            deviceModel = device.name ?: device.id,
            activePlayMs = activeMs,
            standbyMs = wallMs - activeMs,
            ownerUserId = ownerUserId,
            rommSessionId = remote.id
        )
    }
}
