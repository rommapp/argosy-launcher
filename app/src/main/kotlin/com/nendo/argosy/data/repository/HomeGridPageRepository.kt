package com.nendo.argosy.data.repository

import com.nendo.argosy.data.local.dao.HomeGridPageDao
import com.nendo.argosy.data.local.entity.HomeGridPageEntity
import com.nendo.argosy.data.local.entity.PageAudioKind
import com.nendo.argosy.data.local.entity.PageBackgroundKind
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The settings a curated grid page carries: what it shows behind the tiles and what it does about
 * sound. A page row is created on demand, so a page that has never been given either costs nothing.
 */
@Singleton
class HomeGridPageRepository @Inject constructor(
    private val pageDao: HomeGridPageDao
) {

    fun observePages(ownerUserId: Long?): Flow<List<HomeGridPageEntity>> =
        pageDao.observeAll(ownerUserId)

    suspend fun pageAt(ownerUserId: Long?, sortOrder: Int): HomeGridPageEntity? =
        pageDao.getAt(ownerUserId, sortOrder)

    /**
     * The row for a page, created if this is the first setting it has been given.
     */
    private suspend fun ensurePage(ownerUserId: Long?, sortOrder: Int): HomeGridPageEntity {
        pageDao.getAt(ownerUserId, sortOrder)?.let { return it }
        val created = HomeGridPageEntity(ownerUserId = ownerUserId, sortOrder = sortOrder)
        val id = pageDao.insert(created)
        return created.copy(id = id)
    }

    suspend fun setBackground(
        ownerUserId: Long?,
        sortOrder: Int,
        kind: PageBackgroundKind,
        path: String? = null,
        gameId: Long? = null
    ) {
        val page = ensurePage(ownerUserId, sortOrder)
        pageDao.update(
            page.copy(
                backgroundKind = kind.name,
                backgroundPath = path,
                backgroundGameId = gameId
            )
        )
    }

    suspend fun setAudio(
        ownerUserId: Long?,
        sortOrder: Int,
        kind: PageAudioKind,
        path: String? = null
    ) {
        val page = ensurePage(ownerUserId, sortOrder)
        pageDao.update(page.copy(audioKind = kind.name, audioPath = path))
    }

    suspend fun setName(ownerUserId: Long?, sortOrder: Int, name: String?) {
        val page = ensurePage(ownerUserId, sortOrder)
        pageDao.update(page.copy(name = name?.takeIf { it.isNotBlank() }))
    }

    /**
     * Drops the settings for a removed page and closes the gap, so the page after it does not
     * inherit a background it was never given.
     */
    suspend fun removePage(ownerUserId: Long?, sortOrder: Int) {
        pageDao.getAt(ownerUserId, sortOrder)?.let { pageDao.delete(it) }
        pageDao.closeGapAfter(ownerUserId, sortOrder)
    }
}
