package com.nendo.argosy.domain.usecase.collection

import com.nendo.argosy.data.repository.CollectionRepository
import com.nendo.argosy.domain.usecase.download.DownloadGameUseCase
import javax.inject.Inject

/**
 * Keeps a collection queue one game ahead: the game being played and the one after it are both on
 * disk, so advancing never lands on something that has to be fetched before it can start.
 *
 * Downloading is idempotent, so this is safe to call whenever the queue moves rather than only when
 * it is first armed, and a lookahead that failed earlier is retried on the next advance.
 */
class PrepareCollectionQueueUseCase @Inject constructor(
    private val collectionRepository: CollectionRepository,
    private val downloadGame: DownloadGameUseCase
) {

    suspend operator fun invoke(collectionId: Long, activeGameId: Long) {
        val games = collectionRepository.getGamesInCollection(collectionId)
        if (games.isEmpty()) return

        val index = games.indexOfFirst { it.id == activeGameId }
        if (index < 0) return

        val next = games[(index + 1) % games.size]
        val wanted = if (next.id == activeGameId) {
            listOf(activeGameId)
        } else {
            listOf(activeGameId, next.id)
        }
        wanted.forEach { downloadGame(it) }
    }
}
