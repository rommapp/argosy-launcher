package com.nendo.argosy.data.storage

import com.nendo.argosy.data.emulator.TexturePackInstaller
import com.nendo.argosy.data.local.dao.GameFileDao
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.music.MusicDirectoryManager
import com.nendo.argosy.data.platform.PlatformDefinitions
import com.nendo.argosy.util.DiscSheets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import javax.inject.Inject

enum class RelocationOutcome { MOVED, ALREADY_MOVED, MISSING }

class GameFileRelocator @Inject constructor(
    private val gameFileDao: GameFileDao,
    private val musicDirectoryManager: MusicDirectoryManager,
    private val pathRewriter: GamePathRewriter
) {
    /**
     * Moves everything [game] keeps on disk to where [target] maps it, and repoints every stored
     * path under a moved location, other games' included. A game already under [destinationRoot]
     * is left alone. Throws when a copy fails, after removing the copies it made.
     */
    suspend fun relocate(
        game: GameEntity,
        platformFolder: File,
        destinationRoot: File,
        target: (File) -> File
    ): RelocationOutcome = withContext(Dispatchers.IO) {
        val launch = File(game.localPath ?: return@withContext RelocationOutcome.MISSING)
        if (isUnder(launch, destinationRoot)) return@withContext RelocationOutcome.ALREADY_MOVED
        if (!launch.exists()) return@withContext RelocationOutcome.MISSING

        val unit = unitMembers(launch, platformFolder, game.platformSlug)
        val musicDir = musicDirectoryManager.resolveMusicDir()
        val addOns = gameFileDao.getFilesForGame(game.id)
            .mapNotNull { it.localPath?.let(::File) }
            .filter {
                it.isFile &&
                    (!TexturePackInstaller.isManifestFile(it) || isUnder(it, platformFolder)) &&
                    !isUnder(it, musicDir) &&
                    unit.none { member -> isUnder(it, member) }
            }
            .distinctBy { it.canonicalPath }
        val moves = (unit + addOns)
            .map { it to target(it) }
            .filterNot { (from, to) -> from.canonicalPath == to.canonicalPath }

        val copied = mutableListOf<File>()
        try {
            for ((from, to) in moves) {
                to.parentFile?.mkdirs()
                if (to.exists()) to.deleteRecursively()
                copied += to
                if (!from.copyRecursively(to, overwrite = true)) {
                    throw IOException("Copy failed: ${from.absolutePath} -> ${to.absolutePath}")
                }
            }
        } catch (e: Exception) {
            copied.forEach { it.deleteRecursively() }
            throw e
        }

        pathRewriter.rewrite(moves.map { (from, to) -> from.absolutePath to to.absolutePath })
        moves.forEach { (from, _) -> from.deleteRecursively() }
        RelocationOutcome.MOVED
    }

    companion object {
        fun unitMembers(launch: File, platformFolder: File, platformSlug: String): List<File> {
            if (launch.isDirectory) return listOf(launch)
            val parent = launch.parentFile
            if (parent != null && parent.canonicalPath != platformFolder.canonicalPath && isUnder(parent, platformFolder)) {
                return listOf(parent)
            }
            val romExtensions = PlatformDefinitions.definitionFor(platformSlug)?.extensions.orEmpty()
            return listOf(launch) + DiscSheets.companions(launch, romExtensions)
        }

        fun isUnder(file: File, root: File): Boolean {
            val path = file.canonicalPath
            val rootPath = root.canonicalPath
            return path == rootPath || path.startsWith(rootPath + File.separator)
        }

        fun mapped(file: File, oldRoot: File, newRoot: File, platformFolder: File, fallbackDir: File): File =
            when {
                isUnder(file, oldRoot) -> File(newRoot, file.canonicalFile.toRelativeString(oldRoot.canonicalFile))
                isUnder(file, platformFolder) ->
                    File(fallbackDir, file.canonicalFile.toRelativeString(platformFolder.canonicalFile))
                else -> File(fallbackDir, file.name)
            }
    }
}
