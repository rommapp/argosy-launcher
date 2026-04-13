package com.nendo.argosy.data.netplay

import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.GameFileDao
import com.nendo.argosy.data.local.entity.GameFileEntity
import com.nendo.argosy.data.social.NetplaySession
import com.nendo.argosy.libretro.LibretroCoreManager
import com.nendo.argosy.libretro.LibretroCoreRegistry
import com.nendo.argosy.libretro.NetplaySupportLevel
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

sealed class NetplayPreflightResult {
    data class Joinable(val localFilePath: String) : NetplayPreflightResult()
    data object RomNotFound : NetplayPreflightResult()
    data object RomVersionMismatch : NetplayPreflightResult()
    data object CoreVersionMismatch : NetplayPreflightResult()
    data object CoreNotSupported : NetplayPreflightResult()
}

interface RomHashProvider {
    fun computeRomHashPrefix(file: File): String?
}

interface CoreHashLookup {
    fun hashForCoreId(coreId: String): String?
}

@Singleton
class NetplayPreflightChecker(
    private val gameDao: GameDao,
    private val gameFileDao: GameFileDao,
    private val coreHashLookup: CoreHashLookup,
    private val romHashProvider: RomHashProvider,
    private val coreRegistry: CoreRegistryAdapter
) {
    @Inject constructor(
        gameDao: GameDao,
        gameFileDao: GameFileDao,
        netplayCoreHashLookup: NetplayCoreHashLookup
    ) : this(
        gameDao = gameDao,
        gameFileDao = gameFileDao,
        coreHashLookup = netplayCoreHashLookup,
        romHashProvider = DefaultRomHashProvider,
        coreRegistry = DefaultCoreRegistryAdapter
    )

    suspend fun check(session: NetplaySession): NetplayPreflightResult {
        android.util.Log.d("NetplayPreflight", "check: coreId=${session.coreId} igdbId=${session.gameIgdbId} romHash=${session.romHashPrefix} coreHash=${session.coreHash}")
        val coreInfo = coreRegistry.findCore(session.coreId)
            ?: run { android.util.Log.d("NetplayPreflight", "FAIL: core not found"); return NetplayPreflightResult.CoreNotSupported }
        if (coreInfo.netplaySupport != NetplaySupportLevel.SUPPORTED) {
            android.util.Log.d("NetplayPreflight", "FAIL: core not supported for netplay"); return NetplayPreflightResult.CoreNotSupported
        }

        val igdbId = session.gameIgdbId?.toLong()
            ?: run { android.util.Log.d("NetplayPreflight", "FAIL: igdbId is null"); return NetplayPreflightResult.RomNotFound }
        val game = gameDao.getByIgdbId(igdbId)
            ?: run { android.util.Log.d("NetplayPreflight", "FAIL: no local game for igdbId=$igdbId"); return NetplayPreflightResult.RomNotFound }

        if (game.platformSlug.isNotEmpty() && !coreInfo.supportsPlatform(game.platformSlug)) {
            return NetplayPreflightResult.CoreNotSupported
        }

        android.util.Log.d("NetplayPreflight", "found game: id=${game.id} title=${game.title} platform=${game.platformSlug}")

        val files = gameFileDao.getFilesForGame(game.id)
            .filter { !it.localPath.isNullOrEmpty() }
        android.util.Log.d("NetplayPreflight", "local files: ${files.size}, paths=${files.map { it.localPath }}")

        if (files.isEmpty() && !game.localPath.isNullOrEmpty()) {
            android.util.Log.d("NetplayPreflight", "no game_files rows, falling back to game.localPath=${game.localPath}")
            val legacyPath = game.localPath!!
            val legacyFile = File(legacyPath)
            if (legacyFile.exists()) {
                val hash = romHashProvider.computeRomHashPrefix(legacyFile)
                android.util.Log.d("NetplayPreflight", "legacy hash=$hash vs session=${session.romHashPrefix}")
                if (hash != null && hash.equals(session.romHashPrefix, ignoreCase = true)) {
                    android.util.Log.d("NetplayPreflight", "SUCCESS via legacy path")
                    return NetplayPreflightResult.Joinable(legacyPath)
                } else if (hash != null) {
                    return NetplayPreflightResult.RomVersionMismatch
                }
            }
            android.util.Log.d("NetplayPreflight", "FAIL: legacy file not found or unhashable")
            return NetplayPreflightResult.RomNotFound
        }

        if (files.isEmpty()) { android.util.Log.d("NetplayPreflight", "FAIL: no local files"); return NetplayPreflightResult.RomNotFound }

        var sawFileWithHash = false
        var matchedFile: GameFileEntity? = null
        for (file in files) {
            val localPath = file.localPath ?: continue
            val hashPrefix = file.romHashPrefix ?: run {
                val computed = romHashProvider.computeRomHashPrefix(File(localPath))
                android.util.Log.d("NetplayPreflight", "computed hash for ${file.fileName}: $computed")
                if (computed != null) {
                    gameFileDao.updateRomHashPrefix(file.id, computed)
                }
                computed
            } ?: continue
            sawFileWithHash = true
            android.util.Log.d("NetplayPreflight", "comparing: local=$hashPrefix vs session=${session.romHashPrefix} match=${hashPrefix.equals(session.romHashPrefix, ignoreCase = true)}")
            if (hashPrefix.equals(session.romHashPrefix, ignoreCase = true)) {
                matchedFile = file
                break
            }
        }

        val resolved = matchedFile ?: return if (sawFileWithHash) {
            android.util.Log.d("NetplayPreflight", "FAIL: hash mismatch")
            NetplayPreflightResult.RomVersionMismatch
        } else {
            android.util.Log.d("NetplayPreflight", "FAIL: no files with hash")
            NetplayPreflightResult.RomNotFound
        }

        android.util.Log.d("NetplayPreflight", "SUCCESS via game_files path")
        return NetplayPreflightResult.Joinable(resolved.localPath!!)
    }
}

interface CoreRegistryAdapter {
    fun findCore(coreId: String): CoreDescriptor?
}

data class CoreDescriptor(
    val coreId: String,
    val netplaySupport: NetplaySupportLevel,
    val supportedPlatforms: Set<String>
) {
    fun supportsPlatform(platformSlug: String): Boolean =
        supportedPlatforms.any { it.equals(platformSlug, ignoreCase = true) }
}

object DefaultCoreRegistryAdapter : CoreRegistryAdapter {
    override fun findCore(coreId: String): CoreDescriptor? {
        val info = LibretroCoreRegistry.getCoreById(coreId) ?: return null
        return CoreDescriptor(
            coreId = info.coreId,
            netplaySupport = info.netplaySupport,
            supportedPlatforms = info.platforms
        )
    }
}

object DefaultRomHashProvider : RomHashProvider {
    override fun computeRomHashPrefix(file: File): String? =
        RomHashComputer.computeRomHashPrefix(file)
}

@Singleton
class NetplayCoreHashLookup @Inject constructor(
    private val libretroCoreManager: LibretroCoreManager,
    private val coreHashCache: CoreHashCache
) : CoreHashLookup {
    override fun hashForCoreId(coreId: String): String? {
        val path = libretroCoreManager.getCorePathForCoreId(coreId) ?: return null
        return coreHashCache.getHashForCore(path)
    }
}
