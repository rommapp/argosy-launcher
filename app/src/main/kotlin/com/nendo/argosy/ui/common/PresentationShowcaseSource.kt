package com.nendo.argosy.ui.common

import android.content.Context
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.local.entity.getDisplayName
import com.nendo.argosy.data.remote.romm.RomMRepository
import com.nendo.argosy.data.repository.DownloadFileStatusRepository
import com.nendo.argosy.data.repository.GameRepository
import com.nendo.argosy.data.repository.PlatformRepository
import com.nendo.argosy.data.social.FriendActivity
import com.nendo.argosy.domain.model.PresentationLayout
import com.nendo.argosy.ui.dualscreen.CompanionDetail
import com.nendo.argosy.ui.dualscreen.PresentationSlot
import com.nendo.argosy.ui.screens.home.toCompanionDetail
import com.nendo.argosy.DualScreenManagerHolder
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

const val COLLECTION_SHOWCASE_COVERS = 24

/**
 * What the presentation screen shows for a focused game or a set of games, built the same way
 * whichever screen has the focus, so a game or collection looks identical there from Home, the
 * Library or a collection.
 */
@Singleton
class PresentationShowcaseSource @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gameRepository: GameRepository,
    private val platformRepository: PlatformRepository,
    private val downloadStatus: DownloadFileStatusRepository,
    private val romMRepository: RomMRepository
) {

    suspend fun gameDetail(
        gameId: Long,
        friendsByIgdbId: Map<Int, List<FriendActivity>> = emptyMap()
    ): CompanionDetail? {
        val game = gameRepository.getById(gameId) ?: return null
        return describe(game, friendsByIgdbId)
    }

    /**
     * Fetches the focused game's logo when the presentation layout draws one and the game has
     * none yet. True when a logo arrived, so the caller republishes the detail.
     */
    suspend fun backfillLogo(gameId: Long): Boolean {
        val layout = DualScreenManagerHolder.instance?.presentationStyle?.value?.layout
        if (layout != PresentationLayout.LOGO) return false
        val game = gameRepository.getById(gameId) ?: return false
        if (gameRepository.getArt(gameId).logoPath != null || game.rommId == null) return false
        return romMRepository.fetchLogo(gameId)?.startsWith("/") == true
    }

    suspend fun collectionShowcase(name: String, gameIds: List<Long>): PresentationSlot.PlatformShowcase =
        gameShowcase(
            context = context,
            name = name,
            coverPaths = gameRepository.coverPathsForGames(gameIds, COLLECTION_SHOWCASE_COVERS),
            stats = gameRepository.statsForGames(gameIds),
            fallbackCount = gameIds.size
        )

    private suspend fun describe(
        game: GameEntity,
        friendsByIgdbId: Map<Int, List<FriendActivity>>
    ): CompanionDetail {
        val platformName = platformRepository.getById(game.platformId)?.getDisplayName()
        val friends = game.igdbId?.let { friendsByIgdbId[it.toInt()] }.orEmpty()
        return game.toHomeGameUi(
            downloadStatus,
            gameRepository.getArt(game.id),
            gameRepository.getScreenshots(game.id).firstOrNull()?.sourceUrl,
            platformDisplayName = platformName
        ).toCompanionDetail(friends)
    }
}
