package com.nendo.argosy.domain.usecase.music

import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.GameFileDao
import com.nendo.argosy.data.music.BgmPlaylistRepository
import com.nendo.argosy.data.music.identity.MusicAccountReader
import com.nendo.argosy.data.music.identity.MusicTrackIdentities
import com.nendo.argosy.domain.model.MusicQueueTrack
import com.nendo.argosy.domain.model.MusicTrackSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class GetLauncherQueueUseCase @Inject constructor(
    private val bgmPlaylistRepository: BgmPlaylistRepository,
    private val getBgmTrackGames: GetBgmTrackGamesUseCase,
    private val gameFileDao: GameFileDao,
    private val gameDao: GameDao,
    private val accountReader: MusicAccountReader
) {
    suspend operator fun invoke(): List<MusicQueueTrack> = withContext(Dispatchers.IO) {
        val rows = bgmPlaylistRepository.playableTracks()
        val games = getBgmTrackGames(rows.mapNotNull { it.gameFileId })
        val accounts = accountReader.current()
        rows.map { row ->
            val game = row.gameFileId?.let { games[it] }
            val file = row.gameFileId?.let { gameFileDao.getById(it) } ?: gameFileDao.getByLocalPath(row.filePath)
            val gameRow = file?.let { gameDao.getById(it.gameId) }
            MusicQueueTrack(
                id = localTrackId(row.filePath),
                source = MusicTrackSource.Local(row.filePath),
                title = row.displayName,
                gameTitle = game?.title,
                coverPath = game?.coverPath,
                identity = MusicTrackIdentities.forDeviceFile(row.filePath, file, gameRow, row.displayName, accounts)
            )
        }
    }
}

fun localTrackId(path: String): String = "path:$path"

fun serverTrackId(romFileId: Long): String = "romm:$romFileId"
