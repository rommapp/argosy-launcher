package com.nendo.argosy.data.quaypass

import com.nendo.argosy.data.local.dao.QuayPassDailyStatsDao
import com.nendo.argosy.data.local.dao.QuayPassEncounterDao
import com.nendo.argosy.data.local.entity.QuayPassDailyStatsEntity
import com.nendo.argosy.data.local.entity.QuayPassEncounterEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QuayPassRepository @Inject constructor(
    private val encounterDao: QuayPassEncounterDao,
    private val dailyStatsDao: QuayPassDailyStatsDao
) {

    fun observeEncounters(): Flow<List<QuayPassEncounterEntity>> = encounterDao.observeAll()

    fun observeHasUnseen(): Flow<Boolean> = encounterDao.observeHasUnseen()

    fun observeRecentDailyStats(days: Int = 30): Flow<List<QuayPassDailyStatsEntity>> =
        dailyStatsDao.observeRecent(days)

    suspend fun pageEncounters(limit: Int, offset: Int): List<QuayPassEncounterEntity> =
        encounterDao.page(limit, offset)

    suspend fun markAllSeen() {
        encounterDao.markAllSeen()
    }

    suspend fun markSeen(fingerprint: String) {
        encounterDao.markSeen(fingerprint)
    }

    suspend fun deleteEncounter(fingerprint: String) {
        encounterDao.delete(fingerprint)
    }

    suspend fun clearEncounters() {
        encounterDao.clear()
    }
}
