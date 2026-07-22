package com.nendo.argosy.domain.usecase.music

import com.nendo.argosy.data.local.dao.GameFileDao
import com.nendo.argosy.data.music.BgmPlaylistRepository
import com.nendo.argosy.data.music.MusicDirectoryManager
import com.nendo.argosy.data.preferences.ControlsPreferencesRepository
import com.nendo.argosy.data.preferences.StoragePreferencesRepository
import com.nendo.argosy.data.storage.StorageAttributionRepository
import com.nendo.argosy.data.storage.StorageCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/** Switches the music root, optionally moving its tree and repointing every stored reference. */
class RelocateMusicLibraryUseCase @Inject constructor(
    private val musicDirectoryManager: MusicDirectoryManager,
    private val bgmPlaylistRepository: BgmPlaylistRepository,
    private val gameFileDao: GameFileDao,
    private val controlsPreferencesRepository: ControlsPreferencesRepository,
    private val storagePreferences: StoragePreferencesRepository,
    private val attributionRepository: StorageAttributionRepository
) {
    suspend operator fun invoke(
        oldPath: String,
        newPath: String,
        moveFiles: Boolean
    ): Unit = withContext(Dispatchers.IO) {
        val source = File(oldPath)
        val target = File(newPath)
        if (moveFiles && source.exists() && source.absolutePath != target.absolutePath) {
            musicDirectoryManager.relocate(source, target)
            val oldPrefix = source.absolutePath.trimEnd('/')
            val newPrefix = target.absolutePath.trimEnd('/')
            bgmPlaylistRepository.rewritePathPrefix(oldPrefix, newPrefix)
            gameFileDao.rewriteLocalPathPrefix(oldPrefix, newPrefix)
            controlsPreferencesRepository.rewriteSoundConfigPathPrefix(oldPrefix, newPrefix)
        }
        storagePreferences.setMusicStoragePath(target.absolutePath)
        attributionRepository.markDirty(StorageCategory.MUSIC)
    }
}
