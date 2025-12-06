package com.nendo.argosy.domain.usecase.game

import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.repository.GameRepository
import java.io.File
import javax.inject.Inject

class DeleteGameUseCase @Inject constructor(
    private val gameDao: GameDao,
    private val gameRepository: GameRepository
) {
    suspend operator fun invoke(gameId: Long): Boolean {
        val game = gameDao.getById(gameId) ?: return false
        val path = game.localPath ?: return false

        val file = File(path)
        if (file.exists()) {
            file.delete()
        }

        gameRepository.clearLocalPath(gameId)
        return true
    }
}
