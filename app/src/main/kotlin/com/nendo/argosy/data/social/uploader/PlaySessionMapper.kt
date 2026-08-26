package com.nendo.argosy.data.social.uploader

import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.entity.PlaySessionEntity
import com.nendo.argosy.data.remote.romm.RomMPlaySessionEntry
import java.time.temporal.ChronoUnit

object PlaySessionMapper {
    /**
     * Null for a session RomM would refuse, so one unsendable entry cannot fail the batch it
     * travels in. RomM truncates both timestamps to whole seconds before requiring end after
     * start, so a session opening and closing inside one second reads as zero-length there even
     * though it is not here. The comparison is made at the server's resolution for that reason.
     */
    suspend fun toRomMEntry(session: PlaySessionEntity, gameDao: GameDao): RomMPlaySessionEntry? {
        val start = session.startTime.truncatedTo(ChronoUnit.SECONDS)
        val end = session.endTime.truncatedTo(ChronoUnit.SECONDS)
        if (!end.isAfter(start)) return null
        val rommId = gameDao.getById(session.gameId)?.rommId
        return RomMPlaySessionEntry(
            romId = rommId,
            saveSlot = null,
            startTime = session.startTime.toString(),
            endTime = session.endTime.toString(),
            durationMs = session.activePlayMs
        )
    }
}
