package com.nendo.argosy.data.emulator.config

import android.content.Context
import com.nendo.argosy.data.emulator.PlaySessionTracker
import com.nendo.argosy.data.emulator.background.BackgroundGames
import com.nendo.argosy.data.storage.AndroidDataMirror
import com.nendo.argosy.data.storage.FileAccessLayer
import com.nendo.argosy.util.Logger
import com.nendo.argosy.util.RootShell
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

enum class ConfigWriteFailure {
    NOT_EDITABLE,
    NO_EDIT_ROUTE,
    INVALID_VALUE,
    FILE_MISSING,
    FILE_UNREADABLE,
    EMULATOR_RUNNING,
    PATCH_FAILED,
    BACKUP_FAILED,
    WRITE_FAILED,
    VERIFY_FAILED,
    NO_BACKUP,
    CHANGED_SINCE,
    GAME_RUNNING
}

sealed interface ConfigWriteResult {
    data object Written : ConfigWriteResult
    data object Unchanged : ConfigWriteResult
    data class Failed(val reason: ConfigWriteFailure) : ConfigWriteResult
}

@Singleton
class EmulatorConfigWriter @Inject constructor(
    @ApplicationContext context: Context,
    private val access: EmulatorDataAccess,
    fal: FileAccessLayer,
    locks: EmulatorEditLocks,
    private val sessions: dagger.Lazy<PlaySessionTracker>,
    private val backgroundGames: dagger.Lazy<BackgroundGames>
) {
    private val engine = ConfigWriteEngine(
        root = ConfigRootRunner { script -> RootShell.run(context, script) },
        fal = fal,
        backups = ConfigBackupStore(File(context.filesDir, ConfigBackupStore.DIR)),
        locks = locks,
        workDir = File(context.cacheDir, ConfigRootScripts.WORK_DIR)
    )

    /**
     * Sets [option] of [emulatorId]'s config to [token], closing the emulator first. Needs an edit
     * route, or for a driver key app-data write access.
     */
    suspend fun write(emulatorId: String, packageName: String, option: ConfigOption, token: String): ConfigWriteResult =
        withContext(Dispatchers.IO) {
            val spec = specHolding(emulatorId, option)
                ?: return@withContext ConfigWriteResult.Failed(ConfigWriteFailure.NOT_EDITABLE)
            if (holdsGame(packageName)) return@withContext ConfigWriteResult.Failed(ConfigWriteFailure.GAME_RUNNING)
            engine.write(spec, option, token, packageName, access.capabilities(emulatorId, packageName))
        }

    /**
     * Reverts the newest edit Argosy made to [option]'s file, closing the emulator first.
     */
    suspend fun undo(emulatorId: String, packageName: String, option: ConfigOption): ConfigWriteResult =
        withContext(Dispatchers.IO) {
            val spec = specHolding(emulatorId, option)
                ?: return@withContext ConfigWriteResult.Failed(ConfigWriteFailure.NOT_EDITABLE)
            if (holdsGame(packageName)) return@withContext ConfigWriteResult.Failed(ConfigWriteFailure.GAME_RUNNING)
            engine.undo(spec, option, packageName, access.capabilities(emulatorId, packageName))
        }

    /**
     * Whether [packageName] holds a game Argosy launched: the game being played, or one left running
     * in the background. Closing the emulator would end that game.
     */
    suspend fun holdsGame(packageName: String): Boolean {
        if (sessions.get().activeSession.value?.emulatorPackage == packageName) return true
        return backgroundGames.get().liveApp(packageName)?.game != null
    }

    internal fun pendingUndos(emulatorId: String, packageName: String): List<Pair<ConfigFileSpec, ConfigEdit>> {
        val spec = EmulatorConfigRegistry.specFor(emulatorId) ?: return emptyList()
        return engine.pendingUndos(spec, packageName)
    }

    private fun specHolding(emulatorId: String, option: ConfigOption): EmulatorConfigSpec? =
        EmulatorConfigRegistry.specFor(emulatorId)?.takeIf { option in it.options }

}

internal fun interface ConfigRootRunner {
    fun run(script: String): RootShell.Result?
}

internal sealed interface ConfigRead {
    class Found(val bytes: ByteArray) : ConfigRead
    data object Missing : ConfigRead
    data object Unreadable : ConfigRead
}

internal interface ConfigFileChannel {
    suspend fun stop(packageName: String): Boolean
    fun read(path: String): ConfigRead
    fun replace(path: String, bytes: ByteArray): Boolean
}

internal class ConfigWriteEngine(
    private val root: ConfigRootRunner,
    private val fal: FileAccessLayer,
    private val backups: ConfigBackupStore,
    private val locks: EmulatorEditLocks,
    private val workDir: File,
    private val pause: suspend (Long) -> Unit = { delay(it) }
) {
    suspend fun write(
        spec: EmulatorConfigSpec,
        option: ConfigOption,
        requested: String,
        packageName: String,
        capabilities: EmulatorDataCapabilities
    ): ConfigWriteResult {
        if (!option.editable && option.driverDir == null) return failed(ConfigWriteFailure.NOT_EDITABLE)
        val channel = channelFor(option, capabilities) ?: return failed(ConfigWriteFailure.NO_EDIT_ROUTE)
        val dataRoot = capabilities.dataRoot ?: return failed(ConfigWriteFailure.FILE_MISSING)
        ConfigTokens.resolve(option, requested, null, dataRoot) ?: return failed(ConfigWriteFailure.INVALID_VALUE)
        return locks.withEdit(packageName) {
            if (!channel.stop(packageName)) return@withEdit failed(ConfigWriteFailure.EMULATOR_RUNNING)
            val (path, original) = when (val located = locate(channel, option.file.baseDir(dataRoot, packageName), option.file)) {
                is Located.Found -> located.path to located.bytes
                Located.Missing -> return@withEdit failed(ConfigWriteFailure.FILE_MISSING)
                Located.Unreadable -> return@withEdit failed(ConfigWriteFailure.FILE_UNREADABLE)
            }
            val text = original.toString(Charsets.UTF_8)
            val entries = ConfigFileReaders.read(option.file.format, text)
                ?: return@withEdit failed(ConfigWriteFailure.FILE_UNREADABLE)
            val token = ConfigTokens.resolve(option, requested, storedValue(spec, option, entries), dataRoot)
                ?: return@withEdit failed(ConfigWriteFailure.INVALID_VALUE)
            val patched = ConfigPatchers.patch(spec, option, token, text)
                ?: return@withEdit failed(ConfigWriteFailure.PATCH_FAILED)
            if (patched == text) return@withEdit ConfigWriteResult.Unchanged
            val patchedBytes = patched.toByteArray(Charsets.UTF_8)
            val edit = ConfigEdit(option.section, option.key, token, ConfigBackupStore.sha256(patchedBytes))
            val backup = backups.save(packageName, option.file, original, edit)
                ?: return@withEdit failed(ConfigWriteFailure.BACKUP_FAILED)
            replaceVerified(spec, option, token, channel, path, patchedBytes)?.let { failure ->
                if (restore(channel, path, original)) backups.delete(backup)
                return@withEdit failed(failure)
            }
            Logger.info(TAG, "edit written | pkg=$packageName option=${option.section}/${option.key}")
            ConfigWriteResult.Written
        }
    }

    /**
     * The newest edit Argosy made to each of [spec]'s files for [packageName], the one undo reverts.
     */
    fun pendingUndos(spec: EmulatorConfigSpec, packageName: String): List<Pair<ConfigFileSpec, ConfigEdit>> =
        spec.files.mapNotNull { file -> backups.newestBackup(packageName, file)?.edit?.let { file to it } }

    /**
     * Reverts the newest edit of [option]'s file. A file still exactly as Argosy wrote it gets its
     * backed-up bytes back; a file the emulator has rewritten since gets the option's previous
     * value, provided the option still holds what Argosy wrote.
     */
    suspend fun undo(
        spec: EmulatorConfigSpec,
        option: ConfigOption,
        packageName: String,
        capabilities: EmulatorDataCapabilities
    ): ConfigWriteResult {
        val channel = channelFor(option, capabilities) ?: return failed(ConfigWriteFailure.NO_EDIT_ROUTE)
        val dataRoot = capabilities.dataRoot ?: return failed(ConfigWriteFailure.FILE_MISSING)
        return locks.withEdit(packageName) {
            val backup = backups.newestBackup(packageName, option.file) ?: return@withEdit failed(ConfigWriteFailure.NO_BACKUP)
            val edit = backup.edit?.takeIf { it.section == option.section && it.key == option.key }
                ?: return@withEdit failed(ConfigWriteFailure.NO_BACKUP)
            val saved = runCatching { backup.file.readBytes() }.getOrNull()
                ?: return@withEdit failed(ConfigWriteFailure.NO_BACKUP)
            if (!channel.stop(packageName)) return@withEdit failed(ConfigWriteFailure.EMULATOR_RUNNING)
            val (path, current) = when (val located = locate(channel, option.file.baseDir(dataRoot, packageName), option.file)) {
                is Located.Found -> located.path to located.bytes
                Located.Missing -> return@withEdit failed(ConfigWriteFailure.FILE_MISSING)
                Located.Unreadable -> return@withEdit failed(ConfigWriteFailure.FILE_UNREADABLE)
            }
            val failure = if (ConfigBackupStore.sha256(current) == edit.writtenFileSha256) {
                restoreBytes(channel, path, current, saved)
            } else {
                revertValue(spec, option, edit, channel, path, current, saved)
            }
            if (failure != null) return@withEdit failed(failure)
            backups.delete(backup.file)
            Logger.info(TAG, "edit undone | pkg=$packageName option=${option.section}/${option.key}")
            ConfigWriteResult.Written
        }
    }

    private fun restoreBytes(channel: ConfigFileChannel, path: String, current: ByteArray, saved: ByteArray): ConfigWriteFailure? {
        if (!channel.replace(path, saved)) {
            restore(channel, path, current)
            return ConfigWriteFailure.WRITE_FAILED
        }
        if (!holds(channel.read(path), saved)) {
            restore(channel, path, current)
            return ConfigWriteFailure.VERIFY_FAILED
        }
        return null
    }

    private fun revertValue(
        spec: EmulatorConfigSpec,
        option: ConfigOption,
        edit: ConfigEdit,
        channel: ConfigFileChannel,
        path: String,
        current: ByteArray,
        saved: ByteArray
    ): ConfigWriteFailure? {
        val text = current.toString(Charsets.UTF_8)
        val entries = ConfigFileReaders.read(option.file.format, text) ?: return ConfigWriteFailure.FILE_UNREADABLE
        val stored = storedValue(spec, option, entries)
        if (stored == null || !ConfigOptionDecoder.same(option.type, stored, edit.written)) return ConfigWriteFailure.CHANGED_SINCE
        val previous = ConfigFileReaders.read(option.file.format, saved.toString(Charsets.UTF_8))
            ?.let { storedValue(spec, option, it) }
            ?: option.defaultToken
            ?: return ConfigWriteFailure.NO_BACKUP
        val patched = ConfigPatchers.patch(spec, option, previous, text) ?: return ConfigWriteFailure.PATCH_FAILED
        return replaceVerified(spec, option, previous, channel, path, patched.toByteArray(Charsets.UTF_8))
            ?.also { restore(channel, path, current) }
    }

    private fun replaceVerified(
        spec: EmulatorConfigSpec,
        option: ConfigOption,
        token: String,
        channel: ConfigFileChannel,
        path: String,
        patched: ByteArray
    ): ConfigWriteFailure? {
        if (!channel.replace(path, patched)) return ConfigWriteFailure.WRITE_FAILED
        if (!confirms(spec, option, token, channel.read(path))) return ConfigWriteFailure.VERIFY_FAILED
        return null
    }

    private fun channelFor(option: ConfigOption, capabilities: EmulatorDataCapabilities): ConfigFileChannel? = when {
        capabilities.canEdit -> RootChannel()
        option.file.location == ConfigFileLocation.PACKAGE_DATA -> null
        option.driverDir != null && capabilities.canWriteAppData -> AppDataChannel()
        else -> null
    }

    private sealed interface Located {
        class Found(val path: String, val bytes: ByteArray) : Located
        data object Missing : Located
        data object Unreadable : Located
    }

    private fun locate(channel: ConfigFileChannel, baseDir: String, file: ConfigFileSpec): Located {
        for (relative in file.relativePaths) {
            val path = "$baseDir/$relative"
            when (val read = channel.read(path)) {
                is ConfigRead.Found -> return Located.Found(path, read.bytes)
                ConfigRead.Missing -> continue
                ConfigRead.Unreadable -> return Located.Unreadable
            }
        }
        return Located.Missing
    }

    private fun restore(channel: ConfigFileChannel, path: String, original: ByteArray): Boolean {
        if (holds(channel.read(path), original)) return true
        return channel.replace(path, original) && holds(channel.read(path), original)
    }

    private fun holds(read: ConfigRead, bytes: ByteArray): Boolean =
        read is ConfigRead.Found && read.bytes.contentEquals(bytes)

    private fun storedValue(spec: EmulatorConfigSpec, option: ConfigOption, entries: List<ConfigEntry>): String? {
        val wanted = ConfigOptionDecoder.lookupKey(spec, option.section, option.key)
        return entries.lastOrNull { ConfigOptionDecoder.lookupKey(spec, it.section, it.key) == wanted }?.value
    }

    private fun confirms(spec: EmulatorConfigSpec, option: ConfigOption, token: String, read: ConfigRead): Boolean {
        if (read !is ConfigRead.Found) return false
        val entries = ConfigFileReaders.read(option.file.format, read.bytes.toString(Charsets.UTF_8)) ?: return false
        val stored = storedValue(spec, option, entries) ?: return false
        if (!ConfigOptionDecoder.same(option.type, stored, token)) return false
        val flag = spec.compiledDefaultFlag ?: return true
        return storedValue(spec, option.copy(key = option.key + flag), entries)?.trim() == COMPILED_DEFAULT_OFF
    }

    private fun failed(reason: ConfigWriteFailure): ConfigWriteResult {
        Logger.warn(TAG, "edit refused | reason=$reason")
        return ConfigWriteResult.Failed(reason)
    }

    private inner class RootChannel : ConfigFileChannel {
        override suspend fun stop(packageName: String): Boolean {
            val pkg = RootShell.quote(packageName)
            repeat(STOP_CHECKS) { attempt ->
                if (attempt > 0) pause(STOP_RETRY_MS)
                val result = root.run("am force-stop $pkg\npidof $pkg\nexit 0") ?: return false
                if (result.output.all { it.isBlank() }) return true
            }
            return false
        }

        override fun read(path: String): ConfigRead = ConfigRootScripts.read(root, lower(path), workDir)

        override fun replace(path: String, bytes: ByteArray): Boolean {
            val staging = stagingFile(WRITE_PREFIX)
            try {
                staging.writeBytes(bytes)
                staging.setReadable(true, false)
                return root.run(ConfigRootScripts.replace(lower(path), staging.absolutePath))?.succeeded == true
            } catch (e: Exception) {
                Logger.warn(TAG, "staging write failed: ${e.message}")
                return false
            } finally {
                staging.delete()
            }
        }

        private fun lower(path: String): String = AndroidDataMirror.lowerOf(path) ?: path

        private fun stagingFile(prefix: String): File {
            workDir.mkdirs()
            workDir.setExecutable(true, false)
            return File(workDir, "$prefix${System.nanoTime()}")
        }
    }

    private inner class AppDataChannel : ConfigFileChannel {
        override suspend fun stop(packageName: String): Boolean = true

        override fun read(path: String): ConfigRead {
            if (!fal.exists(path)) return ConfigRead.Missing
            return fal.readBytes(path)?.let { ConfigRead.Found(it) } ?: ConfigRead.Unreadable
        }

        override fun replace(path: String, bytes: ByteArray): Boolean {
            val dir = path.substringBeforeLast('/')
            return fal.prepareSaveAccess(dir) && fal.writeBytes(path, bytes) && fal.commitSaveAccess(dir)
        }
    }

    private companion object {
        const val TAG = "EmulatorConfigWriter"
        const val COMPILED_DEFAULT_OFF = "false"
        const val STOP_CHECKS = 5
        const val STOP_RETRY_MS = 300L
        const val WRITE_PREFIX = "write-"
    }
}

internal object ConfigRootScripts {
    const val EXIT_MISSING = 3
    const val WORK_DIR = "emulator-config"
    private const val TEMP_SUFFIX = ".argosy-tmp"
    private const val READ_PREFIX = "read-"

    /**
     * [path] read through [runner] by way of a staging copy in [workDir].
     */
    fun read(runner: ConfigRootRunner, path: String, workDir: File): ConfigRead {
        workDir.mkdirs()
        workDir.setExecutable(true, false)
        val staging = File(workDir, "$READ_PREFIX${System.nanoTime()}")
        try {
            val result = runner.run(copyOut(path, staging.absolutePath)) ?: return ConfigRead.Unreadable
            return when {
                result.exitCode == EXIT_MISSING -> ConfigRead.Missing
                !result.succeeded -> ConfigRead.Unreadable
                else -> runCatching { staging.readBytes() }.getOrNull()?.let { ConfigRead.Found(it) }
                    ?: ConfigRead.Unreadable
            }
        } finally {
            staging.delete()
        }
    }

    /**
     * Copies [target] to [staging] readable by Argosy; exits [EXIT_MISSING] when [target] is absent.
     */
    fun copyOut(target: String, staging: String): String {
        val t = RootShell.quote(target)
        val s = RootShell.quote(staging)
        return "[ -f $t ] || exit $EXIT_MISSING\n" +
            "cat $t > $s && chmod 644 $s || exit 4\n"
    }

    /**
     * Writes [staging] to a temp file beside [target], gives it [target]'s owner, mode and SELinux
     * label, checks all three took, then renames it over [target].
     */
    fun replace(target: String, staging: String): String {
        val t = RootShell.quote(target)
        val s = RootShell.quote(staging)
        val n = RootShell.quote(target + TEMP_SUFFIX)
        return "[ -f $t ] || exit $EXIT_MISSING\n" +
            "own=\$(stat -c %u:%g $t) && mode=\$(stat -c %a $t) && ctx=\$(stat -c %C $t) || exit 4\n" +
            "cat $s > $n || { rm -f $n; exit 5; }\n" +
            "chown \"\$own\" $n && chmod \"\$mode\" $n && chcon \"\$ctx\" $n || { rm -f $n; exit 6; }\n" +
            "[ \"\$(stat -c '%u:%g %a %C' $n)\" = \"\$own \$mode \$ctx\" ] || { rm -f $n; exit 7; }\n" +
            "mv -f $n $t || { rm -f $n; exit 8; }\n"
    }
}
