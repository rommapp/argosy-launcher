package com.nendo.argosy.domain.usecase.music

import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.GameFileDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Maps bgm track gameFileIds to the linked game's cover path; unresolvable ids are omitted. */
class GetBgmTrackGameCoversUseCase @Inject constructor(
    private val gameFileDao: GameFileDao,
    private val gameDao: GameDao
) {
    suspend operator fun invoke(gameFileIds: Collection<Long>): Map<Long, String> =
        withContext(Dispatchers.IO) {
            buildMap {
                for (fileId in gameFileIds.toSet()) {
                    val gameId = gameFileDao.getById(fileId)?.gameId ?: continue
                    val coverPath = gameDao.getById(gameId)?.coverPath
                    if (!coverPath.isNullOrBlank()) put(fileId, coverPath)
                }
            }
        }
}
