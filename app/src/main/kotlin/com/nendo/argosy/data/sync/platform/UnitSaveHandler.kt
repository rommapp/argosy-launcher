package com.nendo.argosy.data.sync.platform

import android.content.Context
import com.nendo.argosy.data.emulator.ArchiveRomNaming
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.storage.FileAccessLayer
import com.nendo.argosy.data.sync.ResolvedSaveUnit
import com.nendo.argosy.data.sync.SaveArchiver
import com.nendo.argosy.data.sync.SaveUnitResolver
import com.nendo.argosy.util.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handler for libretro cores whose save is a unit of files rather than one `.srm`. A unit
 * with one member travels raw through the legacy handler; two or more travel as a flat zip
 * with each member at the root, the shape Sigil hashed.
 */
@Singleton
class UnitSaveHandler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val fal: FileAccessLayer,
    private val saveArchiver: SaveArchiver,
    private val saveUnitResolver: SaveUnitResolver,
    private val gameDao: GameDao,
    private val retroArchSaveHandler: RetroArchSaveHandler,
    private val defaultSaveHandler: DefaultSaveHandler
) : PlatformSaveHandler {
    companion object {
        private const val TAG = "UnitSaveHandler"
    }

    private fun fallbackFor(context: SaveContext): PlatformSaveHandler =
        if (context.emulatorId in PlatformSaveHandlerRegistry.RETROARCH_EMULATOR_IDS) retroArchSaveHandler
        else defaultSaveHandler

    private suspend fun resolveUnit(localPath: String, context: SaveContext): ResolvedSaveUnit? {
        val layout = context.coreName ?: return null
        val contentName = context.romPath
            ?.let { ArchiveRomNaming.syncContentName(it, context.platformSlug, context.emulatorId) }
            ?: return null
        val game = gameDao.getById(context.gameId)
        return saveUnitResolver.resolveForMember(localPath, layout, context.platformSlug, contentName, game, hash = false)
    }

    override suspend fun prepareForUpload(localPath: String, context: SaveContext): PreparedSave? =
        withContext(Dispatchers.IO) {
            val resolved = resolveUnit(localPath, context)
            if (resolved == null || !resolved.isMulti) {
                return@withContext fallbackFor(context).prepareForUpload(localPath, context)
            }
            val files = resolved.memberPaths.map { fal.getTransformedFile(it) }
            val bundle = File(this@UnitSaveHandler.context.cacheDir, "unit_${System.currentTimeMillis()}.zip")
            if (!saveArchiver.zipFiles(files, bundle)) {
                Logger.error(TAG, "prepareForUpload: bundle failed | members=${resolved.memberPaths}")
                return@withContext null
            }
            Logger.debug(TAG, "prepareForUpload: bundled ${files.size} members | artifact=${resolved.unit.artifact}, size=${bundle.length()}")
            PreparedSave(bundle, isTemporary = true, resolved.memberPaths)
        }

    override suspend fun sourcePathsFor(localPath: String, context: SaveContext): List<String> =
        resolveUnit(localPath, context)?.memberPaths?.takeIf { it.isNotEmpty() } ?: listOf(localPath)

    override suspend fun extractDownload(tempFile: File, context: SaveContext): ExtractResult =
        extractBundle(tempFile, context) ?: fallbackFor(context).extractDownload(tempFile, context)

    /**
     * Places a downloaded bundle's members under the save root, or returns null when the file
     * is not a bundle for this layout (a raw save, or a save that is itself a zip) so the
     * caller can write it the way it always has.
     */
    suspend fun extractBundle(tempFile: File, context: SaveContext): ExtractResult? =
        withContext(Dispatchers.IO) {
            val destinations = bundleDestinations(tempFile, context) ?: return@withContext null
            if (!saveArchiver.unzipEntriesTo(tempFile, destinations)) {
                return@withContext ExtractResult(false, null, "Failed to place bundle members")
            }
            val primaryPath = destinations.values.first()
            Logger.debug(TAG, "extractBundle: placed ${destinations.size} members | primary=$primaryPath")
            ExtractResult(true, primaryPath)
        }

    private suspend fun bundleDestinations(tempFile: File, context: SaveContext): Map<String, String>? {
        val layout = context.coreName ?: return null
        val contentName = context.romPath
            ?.let { ArchiveRomNaming.syncContentName(it, context.platformSlug, context.emulatorId) }
            ?: return null
        if (!saveArchiver.isZipArchive(tempFile)) return null
        val isRetroArch = context.emulatorId in PlatformSaveHandlerRegistry.RETROARCH_EMULATOR_IDS
        val anchor = context.localSavePath
            ?: (if (isRetroArch) retroArchSaveHandler.discoverSavePath(context) else null)
            ?: (if (isRetroArch) retroArchSaveHandler.constructSavePath(context) else null)
            ?: return null
        return saveUnitResolver.placeBundle(
            saveArchiver.listFileEntries(tempFile), anchor, layout, context.platformSlug, contentName,
            gameDao.getById(context.gameId)
        )
    }
}
