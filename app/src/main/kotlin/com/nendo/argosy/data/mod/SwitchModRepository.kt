package com.nendo.argosy.data.mod

import android.content.Context
import com.nendo.argosy.data.emulator.BiosPathRegistry
import com.nendo.argosy.data.emulator.EmulatorResolver
import com.nendo.argosy.data.model.VariantCategory
import com.nendo.argosy.data.platform.PlatformDefinitions
import com.nendo.argosy.data.repository.GameRepository
import com.nendo.argosy.data.storage.FileAccessLayer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.zip.ZipFile
import java.io.File
import javax.inject.Inject

data class DownloadedSwitchMod(
    val titleId: String,
    val packageId: String,
    val fileName: String,
    val archivePath: String?,
    val installed: Boolean,
    val needsRecovery: Boolean,
    val hasHandheldProfile: Boolean
)

data class SwitchModLibrary(val emulatorName: String, val packages: List<DownloadedSwitchMod>)

class SwitchModRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val games: GameRepository,
    private val emulators: EmulatorResolver,
    private val installer: SwitchModInstaller,
    private val fal: FileAccessLayer
) {
    private val stateRoot: File get() = SwitchModLaunchGate.stateRoot(context.filesDir)

    suspend fun list(gameId: Long): SwitchModLibrary = withContext(Dispatchers.IO) {
        val target = resolve(gameId)
        val packages = games.getGameFilesForGame(gameId)
            .filter { VariantCategory.fromKey(it.category) == VariantCategory.MOD }
            .mapNotNull { file ->
                val id = "game-$gameId-file-${file.id}"
                val store = SwitchModStateStore(stateRoot, target.root, target.titleId, id)
                val installed = store.receipt() != null
                val pending = store.pending.isFile
                val archive = file.localPath?.let(::File)?.takeIf { it.isFile && it.extension.equals("zip", ignoreCase = true) }
                if (archive == null && !installed && !pending) null else DownloadedSwitchMod(
                    titleId = target.titleId,
                    packageId = id,
                    fileName = file.fileName,
                    archivePath = archive?.absolutePath,
                    installed = installed,
                    needsRecovery = pending,
                    hasHandheldProfile = archive?.let { candidate ->
                        runCatching { ZipFile.builder().setFile(candidate).get().use { zip ->
                            zip.getEntry(UltraCamHandheldProfile.CONFIG_PATH) != null
                        } }.getOrDefault(false)
                    } ?: false
                )
            }
        val stored = SwitchModStateStore(stateRoot, target.root, target.titleId, "catalog").catalog()
        SwitchModLibrary(target.emulatorName, SwitchModCatalog.merge(packages, stored, target.root, target.titleId))
    }

    suspend fun install(gameId: Long, packageId: String, profile: SwitchModProfile, confirmedClosed: Boolean): SwitchModInstallResult = withContext(Dispatchers.IO) {
        val selected = list(gameId).packages.firstOrNull { it.packageId == packageId }
            ?: throw ModInstallException(ModInstallFailure.INVALID_ARCHIVE)
        val archive = selected.archivePath?.let(::File) ?: throw ModInstallException(ModInstallFailure.INVALID_ARCHIVE)
        val target = resolve(gameId)
        installer.install(archive, target.titleId, packageId, target.root, stateRoot, confirmedClosed, profile)
    }

    suspend fun remove(gameId: Long, titleId: String, packageId: String, confirmedClosed: Boolean) {
        val target = resolve(gameId)
        installer.remove(titleId, packageId, target.root, stateRoot, confirmedClosed)
    }

    suspend fun recover(gameId: Long, titleId: String, packageId: String, confirmedClosed: Boolean) {
        val target = resolve(gameId)
        installer.recover(titleId, packageId, target.root, stateRoot, confirmedClosed, restoreInterruptedFiles = true)
    }

    private suspend fun resolve(gameId: Long): EdenModTarget = withContext(Dispatchers.IO) {
        val game = games.getById(gameId) ?: throw ModInstallException(ModInstallFailure.INVALID_TITLE)
        if (PlatformDefinitions.getCanonicalSlug(game.platformSlug) != "switch") {
            throw ModInstallException(ModInstallFailure.UNSUPPORTED_EMULATOR)
        }
        val title = SwitchModPackage.titleId(game.titleId ?: throw ModInstallException(ModInstallFailure.INVALID_TITLE))
        val emulator = emulators.resolveEmulatorForGame(game.id, game.platformId, game.platformSlug)
            ?: throw ModInstallException(ModInstallFailure.UNSUPPORTED_EMULATOR)
        if (emulators.canonicalEmulatorId(emulator) != "eden") throw ModInstallException(ModInstallFailure.UNSUPPORTED_EMULATOR)
        val root = BiosPathRegistry.getEdenDataPath(emulator.packageName).trimEnd('/')
        if (!fal.prepareSaveAccess("$root/config")) throw ModInstallException(ModInstallFailure.ACCESS_DENIED)
        val config = fal.getInputStream("$root/config/config.ini")?.use { input ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (output.size() + count > 1024 * 1024) throw ModInstallException(ModInstallFailure.ACCESS_DENIED)
                output.write(buffer, 0, count)
            }
            output.toString(Charsets.UTF_8.name())
        }
        if (config != null) requireDefaultStorage(config, root)
        else if (!fal.isListable(root) || fal.listFilesUnion(root).any { it.name == "config" }) {
            throw ModInstallException(ModInstallFailure.ACCESS_DENIED)
        }
        EdenModTarget(title, root, emulator.displayName)
    }

    private fun requireDefaultStorage(config: String, root: String) {
        var storage = false
        config.lineSequence().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.startsWith('[') && trimmed.endsWith(']')) storage = trimmed in setOf("[DataStorage]", "[Data%20Storage]")
            if (storage && '=' in trimmed) {
                val key = trimmed.substringBefore('=').trim()
                val expected = when (key) {
                    "load_directory" -> "$root/load"
                    "sdmc_directory" -> "$root/sdmc"
                    else -> null
                }
                val value = trimmed.substringAfter('=').trim().trim('"').trimEnd('/')
                if (expected != null && value.isNotEmpty() && value != expected) {
                    throw ModInstallException(ModInstallFailure.UNSUPPORTED_PATH)
                }
            }
        }
    }

    private data class EdenModTarget(val titleId: String, val root: String, val emulatorName: String)
}
