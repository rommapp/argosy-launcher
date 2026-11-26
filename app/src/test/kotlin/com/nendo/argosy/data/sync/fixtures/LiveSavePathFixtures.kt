package com.nendo.argosy.data.sync.fixtures

import com.nendo.argosy.data.emulator.EmulatorResolver
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.sync.LiveSavePath
import io.mockk.coEvery
import io.mockk.mockk

fun cachedPathAsStored(): LiveSavePath = launchingPackage(null)

fun launchingPackage(emulatorPackage: String?, game: GameEntity? = null): LiveSavePath {
    val resolver = mockk<EmulatorResolver>()
    coEvery { resolver.getEmulatorPackageForGame(any(), any(), any()) } returns emulatorPackage
    val gameDao = mockk<GameDao>()
    coEvery { gameDao.getById(any()) } returns game
    return LiveSavePath(resolver, gameDao)
}
