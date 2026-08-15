package com.nendo.argosy.domain.usecase.media

import com.nendo.argosy.data.local.dao.MediaCreditDao
import com.nendo.argosy.data.local.dao.MediaItemDao
import com.nendo.argosy.data.local.entity.MediaItemEntity
import com.nendo.argosy.data.local.entity.MediaItemType
import com.nendo.argosy.data.preferences.JellyfinPreferencesRepository
import com.nendo.argosy.data.remote.jellyfin.PERSON_TYPE_DIRECTOR
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject

private const val MIN_RESULTS = 5
private const val MAX_RESULTS = 15
private const val PER_QUERY_LIMIT = 15
private const val YEAR_WINDOW = 3

/**
 * Titles like this one, decided here rather than asked of the server.
 *
 * Jellyfin does answer `/Items/{id}/Similar`, but it scores on genre and tag overlap alone and
 * returns results a viewer reads as wrong. This mirrors the game library's related tiers instead:
 * strongest signal first, weaker ones consulted only while the list is still short, and everything
 * answered from what is already stored so the rail works with no network.
 *
 * A season or an episode is never a result. Only whole titles are somewhere to go next.
 */
class GetRelatedMediaUseCase @Inject constructor(
    private val mediaItemDao: MediaItemDao,
    private val mediaCreditDao: MediaCreditDao,
    private val jellyfinPreferencesRepository: JellyfinPreferencesRepository
) {
    suspend operator fun invoke(item: MediaItemEntity): List<MediaItemEntity> {
        val ownerUserId = jellyfinPreferencesRepository.preferences
            .map { it.userId?.takeIf { id -> id.isNotBlank() } }
            .first() ?: return emptyList()
        val results = LinkedHashMap<String, MediaItemEntity>()
        val browsable = listOf(MediaItemType.MOVIE.wireValue, MediaItemType.SERIES.wireValue)

        directorMatches(ownerUserId, item, browsable).forEach { results.putIfAbsent(it.itemId, it) }

        val year = item.productionYear
        if (results.size < MIN_RESULTS && year != null) {
            tokensOf(item.genres).forEach { token ->
                mediaItemDao.getRelatedByGenreAndYear(
                    ownerUserId = ownerUserId,
                    token = token,
                    yearLo = year - YEAR_WINDOW,
                    yearHi = year + YEAR_WINDOW,
                    excludeItemId = item.itemId,
                    itemTypes = browsable,
                    limit = PER_QUERY_LIMIT
                ).forEach { results.putIfAbsent(it.itemId, it) }
            }
        }

        if (results.size < MIN_RESULTS) {
            tokensOf(item.studios).forEach { token ->
                mediaItemDao.getRelatedByStudio(
                    ownerUserId = ownerUserId,
                    token = token,
                    excludeItemId = item.itemId,
                    itemTypes = browsable,
                    limit = PER_QUERY_LIMIT
                ).forEach { results.putIfAbsent(it.itemId, it) }
            }
        }

        return results.values.take(MAX_RESULTS)
    }

    private suspend fun directorMatches(
        ownerUserId: String,
        item: MediaItemEntity,
        browsable: List<String>
    ): List<MediaItemEntity> {
        val directorIds = mediaCreditDao.getForItem(ownerUserId, item.itemId)
            .filter { it.personType == PERSON_TYPE_DIRECTOR }
            .map { it.personId }
        if (directorIds.isEmpty()) return emptyList()

        val itemIds = mediaCreditDao.getItemIdsSharingPeople(
            ownerUserId = ownerUserId,
            personIds = directorIds,
            personType = PERSON_TYPE_DIRECTOR,
            excludeItemId = item.itemId,
            limit = PER_QUERY_LIMIT
        )
        if (itemIds.isEmpty()) return emptyList()

        return mediaItemDao.getByItemIds(ownerUserId, itemIds)
            .filter { it.itemType in browsable }
    }

    private fun tokensOf(joined: String?): List<String> =
        joined?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
}
