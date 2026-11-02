package com.nendo.argosy.data.sync

import com.nendo.argosy.data.local.dao.SaveSyncDao
import com.nendo.argosy.data.repository.ActiveSaveRepository
import com.nendo.argosy.data.repository.ServerSaveGate
import io.mockk.coEvery
import io.mockk.mockk

internal fun noHoldGate(): ServerSaveGate {
    val activeSaves = mockk<ActiveSaveRepository>()
    coEvery { activeSaves.getActiveRow(any()) } returns null
    return ServerSaveGate(activeSaves, unpinnedSaveSyncDao(), mockk(relaxed = true))
}

internal fun unpinnedSaveSyncDao(): SaveSyncDao = mockk<SaveSyncDao>().also {
    coEvery { it.getByGame(any(), any()) } returns emptyList()
}
