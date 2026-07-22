package com.nendo.argosy.domain.usecase.music

import com.nendo.argosy.data.local.dao.GameFileDao
import com.nendo.argosy.data.music.MusicDirectoryManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import javax.inject.Inject

data class LocalMusicTrackState(
    val gameFileId: Long?,
    val localPath: String
)

data class MusicTrackLookup(
    val romFileId: Long,
    val platformName: String,
    val gameName: String,
    val trackNumber: Int?,
    val trackTitle: String?,
    val fileName: String
)

/** Resolves which RomM music tracks are already on disk, healing stale game_files links via the deterministic Music path. */
class GetLocalMusicTrackStateUseCase @Inject constructor(
    private val gameFileDao: GameFileDao,
    private val musicDirectoryManager: MusicDirectoryManager
) {
    suspend operator fun invoke(tracks: List<MusicTrackLookup>): Map<Long, LocalMusicTrackState> =
        withContext(Dispatchers.IO) {
            buildMap {
                for (track in tracks) {
                    val row = gameFileDao.getByRommFileId(track.romFileId)
                    val rowPath = row?.localPath?.takeIf { File(it).exists() }
                    if (rowPath != null) {
                        put(track.romFileId, LocalMusicTrackState(row.id, rowPath))
                        continue
                    }
                    val expected = musicDirectoryManager.targetFileFor(
                        track.platformName,
                        track.gameName,
                        track.trackNumber,
                        track.trackTitle,
                        track.fileName
                    )
                    if (!expected.exists()) continue
                    if (row != null) {
                        gameFileDao.updateLocalPathByRommFileId(
                            track.romFileId,
                            expected.absolutePath,
                            Instant.now()
                        )
                    }
                    put(track.romFileId, LocalMusicTrackState(row?.id, expected.absolutePath))
                }
            }
        }
}
