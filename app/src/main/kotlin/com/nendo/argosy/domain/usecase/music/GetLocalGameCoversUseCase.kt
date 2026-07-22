package com.nendo.argosy.domain.usecase.music

import com.nendo.argosy.data.local.dao.GameDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Maps RomM rom ids to local library cover paths; games not in the library are omitted. */
class GetLocalGameCoversUseCase @Inject constructor(
    private val gameDao: GameDao
) {
    suspend operator fun invoke(rommIds: Collection<Long>): Map<Long, String> =
        withContext(Dispatchers.IO) {
            buildMap {
                for (rommId in rommIds) {
                    val coverPath = gameDao.getByRommId(rommId)?.coverPath
                    if (!coverPath.isNullOrBlank()) put(rommId, coverPath)
                }
            }
        }
}
