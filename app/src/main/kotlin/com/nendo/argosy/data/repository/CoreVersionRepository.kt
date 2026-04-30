package com.nendo.argosy.data.repository

import com.nendo.argosy.data.local.dao.CoreVersionDao
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CoreVersionRepository @Inject constructor(
    private val coreVersionDao: CoreVersionDao
) {
    fun observeUpdateCount(): Flow<Int> = coreVersionDao.observeUpdateCount()
}
