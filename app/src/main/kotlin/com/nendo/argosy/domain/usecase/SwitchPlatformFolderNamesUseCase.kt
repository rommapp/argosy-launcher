package com.nendo.argosy.domain.usecase

import com.nendo.argosy.data.local.dao.GameDiscDao
import com.nendo.argosy.data.local.dao.GameFileDao
import com.nendo.argosy.data.local.dao.PlatformDao
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.local.entity.PlatformEntity
import com.nendo.argosy.data.platform.PlatformFolderResolver
import com.nendo.argosy.data.platform.platformFolderName
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.repository.GameRepository
import com.nendo.argosy.data.storage.GameFileRelocator
import com.nendo.argosy.data.storage.GamePathRewriter
import com.nendo.argosy.data.storage.StorageAttributionRepository
import com.nendo.argosy.data.storage.StorageCategory
import com.nendo.argosy.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import javax.inject.Inject

sealed interface FolderNameSwitchResult {
    data class Switched(val moved: Int) : FolderNameSwitchResult
    data class Failed(val path: String) : FolderNameSwitchResult
}

/**
 * Turns server folder names for platform folders on or off and renames the downloaded roms of
 * every platform without a custom folder to match. Any rename that fails undoes the ones before
 * it and leaves the setting as it was.
 */
class SwitchPlatformFolderNamesUseCase @Inject constructor(
    private val platformFolderResolver: PlatformFolderResolver,
    private val platformDao: PlatformDao,
    private val gameRepository: GameRepository,
    private val gameFileDao: GameFileDao,
    private val gameDiscDao: GameDiscDao,
    private val pathRewriter: GamePathRewriter,
    private val preferencesRepository: UserPreferencesRepository,
    private val attributionRepository: StorageAttributionRepository
) {
    suspend fun pendingMoves(useFsSlug: Boolean): Int {
        val base = platformFolderResolver.storageBase() ?: return 0
        val platforms = platformDao.getAllPlatforms()
        return platforms.filter { it.customRomPath == null }.sumOf { platform ->
            val (from, to) = folders(platform, platforms, base, useFsSlug)
            if (from == to) 0 else gamesUnder(platform, from).size
        }
    }

    suspend operator fun invoke(useFsSlug: Boolean): FolderNameSwitchResult = withContext(Dispatchers.IO) {
        val base = platformFolderResolver.storageBase()
        if (base == null) {
            preferencesRepository.setPlatformFoldersFromServer(useFsSlug)
            return@withContext FolderNameSwitchResult.Switched(0)
        }
        val platforms = platformDao.getAllPlatforms()
        val done = mutableListOf<Pair<File, File>>()
        var moved = 0
        try {
            for (platform in platforms.filter { it.customRomPath == null }) {
                val (from, to) = folders(platform, platforms, base, useFsSlug)
                if (from == to || !from.exists()) continue
                val games = gamesUnder(platform, from)
                val members = if (ownsFolder(platform, platforms, from, base, useFsSlug)) {
                    if (to.exists()) from.listFiles().orEmpty().toList() else listOf(from)
                } else {
                    games.flatMap { membersOf(it, from) }.distinctBy { it.canonicalPath }
                }
                for (member in members) {
                    val target = if (member == from) to else File(to, member.canonicalFile.toRelativeString(from.canonicalFile))
                    rename(member, target)
                    done += member to target
                }
                if (from.listFiles()?.isEmpty() == true) from.delete()
                moved += games.size
            }
        } catch (e: IOException) {
            Logger.warn(TAG, "Folder name switch failed, undoing ${done.size} moves", e)
            undo(done)
            return@withContext FolderNameSwitchResult.Failed(e.message.orEmpty())
        }
        preferencesRepository.setPlatformFoldersFromServer(useFsSlug)
        attributionRepository.markDirty(StorageCategory.GAMES)
        FolderNameSwitchResult.Switched(moved)
    }

    private fun folders(platform: PlatformEntity, platforms: List<PlatformEntity>, base: File, useFsSlug: Boolean) =
        File(base, platformFolderName(platform, platforms, !useFsSlug)) to
            File(base, platformFolderName(platform, platforms, useFsSlug))

    private fun ownsFolder(
        platform: PlatformEntity,
        platforms: List<PlatformEntity>,
        from: File,
        base: File,
        useFsSlug: Boolean
    ): Boolean = platforms.none { other ->
        other.id != platform.id && other.customRomPath == null &&
            listOf(!useFsSlug, useFsSlug).any { File(base, platformFolderName(other, platforms, it)) == from }
    }

    private suspend fun gamesUnder(platform: PlatformEntity, from: File) =
        gameRepository.getGamesWithLocalPathsForPlatform(platform.id)
            .filter { game -> game.localPath?.let { GameFileRelocator.isUnder(File(it), from) } == true }

    private suspend fun membersOf(game: GameEntity, from: File): List<File> {
        val launch = File(game.localPath ?: return emptyList())
        if (!launch.exists()) return emptyList()
        val unit = GameFileRelocator.unitMembers(launch, from, game.platformSlug)
        val extras = (gameFileDao.getFilesForGame(game.id).mapNotNull { it.localPath } +
            gameDiscDao.getDiscsForGame(game.id).mapNotNull { it.localPath } +
            listOfNotNull(game.m3uPath))
            .map(::File)
            .filter { it.exists() && GameFileRelocator.isUnder(it, from) && unit.none { member -> GameFileRelocator.isUnder(it, member) } }
        return unit + extras
    }

    private suspend fun rename(from: File, to: File) {
        if (to.exists()) throw IOException(to.absolutePath)
        to.parentFile?.mkdirs()
        if (!from.renameTo(to)) throw IOException(from.absolutePath)
        pathRewriter.rewrite(listOf(from.absolutePath to to.absolutePath))
    }

    private suspend fun undo(done: List<Pair<File, File>>) {
        for ((from, to) in done.asReversed()) {
            from.parentFile?.mkdirs()
            if (to.renameTo(from)) {
                pathRewriter.rewrite(listOf(to.absolutePath to from.absolutePath))
            } else {
                Logger.warn(TAG, "Could not move ${to.absolutePath} back to ${from.absolutePath}")
            }
        }
    }

    private companion object {
        const val TAG = "SwitchPlatformFolders"
    }
}
