package com.nendo.argosy.domain.usecase.collection

import com.nendo.argosy.data.remote.romm.RomMUserPropertyService
import com.nendo.argosy.data.repository.CollectionRepository
import com.nendo.argosy.domain.model.CompletionStatus
import javax.inject.Inject

/**
 * Moves a collection tile on to the next game, marking the one being left as finished.
 *
 * Finishing is the reason the queue moves, so it is recorded through the path that also tells RomM
 * rather than only the local row. A status the user set to say they are done with a game is left
 * alone: advancing past something retired, never-playing or already at 100% should not quietly
 * demote it to merely finished.
 *
 * The list wraps, so reaching the end starts it again rather than leaving a tile that does nothing.
 */
class AdvanceCollectionFocusUseCase @Inject constructor(
    private val collectionRepository: CollectionRepository,
    private val userPropertyService: RomMUserPropertyService
) {

    data class Result(val nextGameId: Long, val nextTitle: String)

    suspend operator fun invoke(collectionId: Long, currentGameId: Long): Result? {
        val games = collectionRepository.getGamesInCollection(collectionId)
        if (games.isEmpty()) return null

        val index = games.indexOfFirst { it.id == currentGameId }
        if (index < 0) return games.first().let { Result(it.id, it.title) }

        val leaving = games[index]
        if (!keepsOwnStatus(leaving.status)) {
            userPropertyService.updateUserStatus(leaving.id, CompletionStatus.FINISHED.apiValue)
        }

        val next = games[(index + 1) % games.size]
        return Result(next.id, next.title)
    }

    private fun keepsOwnStatus(status: String?): Boolean =
        when (CompletionStatus.fromApiValue(status)) {
            CompletionStatus.RETIRED,
            CompletionStatus.NEVER_PLAYING,
            CompletionStatus.COMPLETED_100 -> true
            else -> false
        }
}
