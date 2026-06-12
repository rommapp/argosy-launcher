package com.nendo.argosy.data.social.uploader

import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.entity.PlaySessionEntity
import com.nendo.argosy.data.remote.romm.RomMPlaySessionEntry

object PlaySessionMapper {
    suspend fun toRomMEntry(session: PlaySessionEntity, gameDao: GameDao): RomMPlaySessionEntry? {
        if (!session.endTime.isAfter(session.startTime)) return null
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
