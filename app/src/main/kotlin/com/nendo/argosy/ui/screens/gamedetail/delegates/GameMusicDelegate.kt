package com.nendo.argosy.ui.screens.gamedetail.delegates

import com.nendo.argosy.data.music.stats.MusicStatsRepository
import com.nendo.argosy.ui.screens.music.MusicPlayback
import com.nendo.argosy.ui.screens.music.musicGuarded
import javax.inject.Inject

private const val TAG = "GameMusic"
private const val RECOMMENDATION_LIMIT = 40
private const val SOURCE_PREFIX = "game_music:"

/**
 * Music picked for one game from the listen history, with the game's own soundtrack after it.
 */
class GameMusicDelegate @Inject constructor(
    private val stats: MusicStatsRepository,
    private val playback: MusicPlayback
) {
    suspend fun hasMusic(gameId: Long): Boolean =
        musicGuarded(TAG, "recommendations") { stats.recommendationsForGame(gameId, 1) }
            ?.tracks?.isNotEmpty() == true

    suspend fun play(gameId: Long, label: String?): Boolean {
        val recommendations = musicGuarded(TAG, "recommendations") {
            stats.recommendationsForGame(gameId, RECOMMENDATION_LIMIT)
        }
        return playback.playStats(
            sourceId = "$SOURCE_PREFIX$gameId",
            label = label,
            stats = recommendations?.tracks.orEmpty().map { it.track },
            startIndex = 0,
            shuffle = false
        )
    }
}
