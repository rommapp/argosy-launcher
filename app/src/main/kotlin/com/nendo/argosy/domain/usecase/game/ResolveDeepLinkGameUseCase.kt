package com.nendo.argosy.domain.usecase.game

import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.domain.model.DeepLinkRequest
import com.nendo.argosy.util.Logger
import javax.inject.Inject

private const val TAG = "ResolveDeepLinkGame"

sealed interface DeepLinkResolution {
    data class Resolved(val gameId: Long) : DeepLinkResolution
    data class NotFound(val reason: String) : DeepLinkResolution
    data class Ambiguous(val reason: String) : DeepLinkResolution
}

/**
 * Turns an external launch request into a local game id.
 *
 * Identifiers are tried most-specific first: game id, then RomM rom id, then ROM path.
 * The path arm falls back to a file-name match when the caller's path does not byte-match
 * the stored one. A file name matching more than one installed game resolves to
 * [DeepLinkResolution.Ambiguous] rather than picking one.
 */
class ResolveDeepLinkGameUseCase @Inject constructor(
    private val gameDao: GameDao
) {
    suspend operator fun invoke(request: DeepLinkRequest): DeepLinkResolution {
        request.gameId?.let { id ->
            return if (gameDao.getById(id) != null) {
                DeepLinkResolution.Resolved(id)
            } else {
                DeepLinkResolution.NotFound("no game with gameId=$id")
            }
        }

        request.rommId?.let { id ->
            val game = gameDao.getByRommId(id)
            return if (game != null) {
                DeepLinkResolution.Resolved(game.id)
            } else {
                DeepLinkResolution.NotFound("no game with rommId=$id")
            }
        }

        val path = request.romPath
        if (!path.isNullOrBlank()) {
            return resolveByPath(path)
        }

        return DeepLinkResolution.NotFound("request carried no game identifier")
    }

    private suspend fun resolveByPath(path: String): DeepLinkResolution {
        gameDao.getByPath(path)?.let { return DeepLinkResolution.Resolved(it.id) }

        val fileName = path.substringAfterLast('/')
        if (fileName.isBlank()) {
            return DeepLinkResolution.NotFound("path carried no file name: $path")
        }

        val matches = gameDao.getGamesWithLocalPathInfo()
            .filter { it.localPath?.substringAfterLast('/').equals(fileName, ignoreCase = true) }

        return when {
            matches.isEmpty() ->
                DeepLinkResolution.NotFound("no installed game matches file name $fileName")
            matches.size > 1 ->
                DeepLinkResolution.Ambiguous("$fileName matches ${matches.size} installed games")
            else -> {
                Logger.info(TAG, "Resolved $fileName by file-name fallback, stored path differs from $path")
                DeepLinkResolution.Resolved(matches.first().id)
            }
        }
    }
}
