package com.nendo.argosy.data.storage

import android.content.Context
import android.os.Build
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.Process
import com.nendo.argosy.util.AppDataShell
import com.nendo.argosy.util.Logger
import com.nendo.argosy.util.RootShell
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A copy of other apps' Android/data folders in Argosy's own storage, kept in step with the real
 * folders through a root route or a shell-level Shizuku route, for devices where Argosy is
 * outside the group that owns those folders. Every read and write Argosy makes there lands on the copy;
 * [sync] reconciles one folder against what it held at the previous sync, taking the emulator's
 * changes into the copy and Argosy's changes out to the real folder.
 */
@Singleton
class AndroidDataMirror @Inject constructor(
    @ApplicationContext private val context: Context
) {
    data class Entry(val isDirectory: Boolean, val size: Long, val mtimeSec: Long)

    sealed interface Action {
        val key: String
        data class Pull(override val key: String, val entry: Entry) : Action
        data class DropFromMirror(override val key: String) : Action
        data class Push(override val key: String, val entry: Entry, val isNew: Boolean) : Action
        data class DeleteReal(override val key: String, val isDirectory: Boolean) : Action
        data class Adopt(override val key: String, val entry: Entry) : Action
    }

    private val root: File by lazy { File(context.filesDir, MIRROR_DIR) }
    private val base = ConcurrentHashMap<String, Entry>()
    private val syncedAt = ConcurrentHashMap<String, Long>()
    private val lock = Any()

    private sealed interface Transport {
        data object Root : Transport
        data class Shell(val shell: AppDataShell) : Transport
    }

    private val outsideDataGroup: Boolean by lazy {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !processHasGroup(EXT_DATA_RW_GID)
    }

    @Volatile
    private var activated = false

    private fun transport(): Transport? = when {
        RootShell.isAvailable -> Transport.Root
        else -> RootShell.appDataShell?.let { Transport.Shell(it) }
    }

    val isActive: Boolean
        get() {
            if (!outsideDataGroup || transport() == null) return false
            if (!activated) activate()
            return true
        }

    private fun activate() = synchronized(lock) {
        if (activated) return@synchronized
        root.deleteRecursively()
        root.mkdirs()
        base.clear()
        syncedAt.clear()
        activated = true
        Logger.info(TAG, "Android/data is outside Argosy's groups; mirroring it through ${transport()} | mirror=${root.absolutePath}")
    }

    fun isMirrorPath(path: String): Boolean = path.startsWith(root.absolutePath + "/")

    fun realPathOf(mirrorPath: String): String? {
        if (!isMirrorPath(mirrorPath)) return null
        val rest = mirrorPath.removePrefix(root.absolutePath + "/")
        val user = rest.substringBefore('/')
        return "/storage/emulated/$user/${rest.substringAfter('/')}"
    }

    /**
     * The copy's path for [path], synced first unless synced within the last few seconds. Null
     * when [path] is outside internal Android/data and obb, or when the real folder is unreadable.
     */
    fun mirrorPathFor(path: String): String? {
        val key = lowerOf(path) ?: return null
        if (Looper.myLooper() != Looper.getMainLooper() && !ensureFresh(key)) return null
        return mirrorOf(key)
    }

    /**
     * Reconciles each path and everything under it now, ignoring freshness. A folder path syncs
     * its whole tree; a file path syncs its containing folder. False when any folder could not be
     * read or any copy in either direction failed.
     */
    fun sync(paths: Collection<String>): Boolean =
        paths.mapNotNull(::lowerOf).distinct().map { key ->
            val parentOk = parentScope(key)?.let { syncScope(it, recursive = false) } ?: true
            val treeOk = if (File(mirrorOf(key)).isDirectory) {
                syncScope(key, recursive = INSIDE_PACKAGE.matches(key))
            } else {
                true
            }
            parentOk && treeOk
        }.all { it }

    /**
     * Names and sizes of the regular files directly inside the real folder at [path], read without
     * copying anything into the mirror. Empty when the folder is absent; null when [path] is
     * outside internal Android/data and obb, or when the real folder is unreadable.
     */
    fun realFileSizes(path: String): Map<String, Long>? {
        val key = lowerOf(path) ?: return null
        val manifest = readRealManifest(key, recursive = false) ?: return null
        return manifest.entries
            .filterValues { !it.isDirectory }
            .entries
            .associate { (entryKey, entry) -> entryKey.substringAfterLast('/') to entry.size }
    }

    private fun ensureFresh(key: String): Boolean {
        val parent = parentScope(key)
        if (parent != null && !isFresh(parent) && !syncScope(parent, recursive = false)) return false
        val mirrored = File(mirrorOf(key))
        if ((parent == null || mirrored.isDirectory) && !isFresh(key)) return syncScope(key, recursive = false)
        return true
    }

    private fun isFresh(scope: String): Boolean =
        syncedAt[scope]?.let { System.currentTimeMillis() - it < FRESH_MS } == true

    private fun syncScope(scope: String, recursive: Boolean): Boolean = synchronized(lock) {
        val real = readRealManifest(scope, recursive) ?: run {
            Logger.warn(TAG, "sync: could not read the real folder | scope=$scope")
            return false
        }
        val scopeMirror = File(mirrorOf(scope))
        if (real.scopeExists) scopeMirror.mkdirs()
        val mirror = readMirrorManifest(scope, recursive)
        val baseUnder = base.filterKeys { isUnder(it, scope, recursive) }
        val actions = plan(real.entries, mirror, baseUnder, MAX_FILE_BYTES)
        val failures = if (actions.isNotEmpty()) apply(actions) else 0
        if (failures > 0) return false
        val now = System.currentTimeMillis()
        syncedAt[scope] = now
        if (recursive) {
            real.entries.filterValues { it.isDirectory }.keys.forEach { syncedAt[it] = now }
        }
        true
    }

    private class RealManifest(val scopeExists: Boolean, val entries: Map<String, Entry>)

    private fun readRealManifest(scope: String, recursive: Boolean): RealManifest? {
        val transport = transport() ?: return null
        val depth = if (recursive) "" else "-maxdepth 1 "
        val q = RootShell.quote(realPathOn(transport, scope))
        val script = "p=$q\n" +
            "if [ -d \"\$p\" ]; then echo $SCOPE_PRESENT; " +
            "find \"\$p\" -mindepth 1 $depth-exec stat -c '%F|%s|%Y|%n' {} + ; " +
            "else echo $SCOPE_ABSENT; fi"
        val result = runOn(transport, script) ?: return null
        if (!result.succeeded) return null
        val lines = result.output
        val first = lines.firstOrNull() ?: return null
        if (first == SCOPE_ABSENT) return RealManifest(false, emptyMap())
        if (first != SCOPE_PRESENT) return null
        val entries = lines.drop(1).mapNotNull(::parseStatLine).mapNotNull { (path, entry) ->
            val key = if (transport is Transport.Shell) lowerOf(path) else path
            key?.let { it to entry }
        }.toMap()
        return RealManifest(true, entries)
    }

    private fun realPathOn(transport: Transport, key: String): String =
        if (transport is Transport.Shell) upperOf(key) else key

    private fun runOn(transport: Transport, script: String): RootShell.Result? = when (transport) {
        Transport.Root -> RootShell.run(context, script)
        is Transport.Shell -> transport.shell.runScript(script)
    }

    private fun readMirrorManifest(scope: String, recursive: Boolean): Map<String, Entry> {
        val dir = File(mirrorOf(scope))
        if (!dir.isDirectory) return emptyMap()
        val files = if (recursive) dir.walkTopDown().drop(1).toList() else dir.listFiles()?.toList().orEmpty()
        return files.associate { file ->
            keyOfMirror(file) to Entry(file.isDirectory, if (file.isDirectory) 0L else file.length(), file.lastModified() / 1000)
        }
    }

    private fun apply(actions: List<Action>): Int {
        actions.filterIsInstance<Action.DropFromMirror>().forEach { File(mirrorOf(it.key)).deleteRecursively() }
        actions.filterIsInstance<Action.Pull>().filter { it.entry.isDirectory }
            .forEach { File(mirrorOf(it.key)).mkdirs() }
        actions.filterIsInstance<Action.Pull>().filter { !it.entry.isDirectory }
            .forEach { File(mirrorOf(it.key)).parentFile?.mkdirs() }

        val touchesReal = actions.any { it is Action.Pull && !it.entry.isDirectory || it is Action.Push || it is Action.DeleteReal }
        val failed = if (touchesReal) applyToReal(actions) else emptySet()

        actions.forEach { action ->
            if (action.key in failed) {
                Logger.warn(TAG, "sync: ${action::class.simpleName} failed | path=${action.key}")
                if (action is Action.DeleteReal && action.isDirectory) {
                    File(mirrorOf(action.key)).mkdirs()
                    base.keys.removeAll { it == action.key || it.startsWith(action.key + "/") }
                    syncedAt.remove(action.key)
                }
                return@forEach
            }
            when (action) {
                is Action.Pull -> {
                    val copy = File(mirrorOf(action.key))
                    if (action.entry.isDirectory || copy.length() == action.entry.size) {
                        base[action.key] = action.entry
                    } else {
                        Logger.warn(TAG, "sync: copy size differs from the real file | path=${action.key}")
                    }
                }
                is Action.DropFromMirror -> base.keys.removeAll { it == action.key || it.startsWith(action.key + "/") }
                is Action.Push -> base[action.key] = action.entry
                is Action.DeleteReal -> base.remove(action.key)
                is Action.Adopt -> base[action.key] = action.entry
            }
        }
        val summary = actions.groupingBy { it::class.simpleName }.eachCount()
        Logger.debug(TAG, "sync: applied $summary failed=${failed.size}")
        return failed.size
    }

    private fun applyToReal(actions: List<Action>): Set<String> = when (val transport = transport()) {
        null -> {
            Logger.warn(TAG, "sync: no route reaches Android/data | actions=${actions.size}")
            actions.map { it.key }.toSet()
        }
        Transport.Root -> applyThroughRoot(actions)
        is Transport.Shell -> applyThroughShell(transport.shell, actions)
    }

    private fun applyThroughShell(shell: AppDataShell, actions: List<Action>): Set<String> {
        val failed = mutableSetOf<String>()
        actions.filterIsInstance<Action.Pull>().filter { !it.entry.isDirectory }.forEach { pull ->
            val copy = File(mirrorOf(pull.key))
            val pulled = shell.open(upperOf(pull.key), ParcelFileDescriptor.MODE_READ_ONLY)?.let { descriptor ->
                runCatching {
                    ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { input ->
                        copy.outputStream().use { input.copyTo(it) }
                    }
                    copy.setLastModified(pull.entry.mtimeSec * MS_PER_SECOND)
                }.isSuccess
            } == true
            if (!pulled) failed += pull.key
        }

        val pushes = actions.filterIsInstance<Action.Push>()
        val folders = pushes.filter { it.entry.isDirectory }.sortedBy { it.key.length }
        if (folders.isNotEmpty()) {
            val script = folders.joinToString("\n") { push ->
                "mkdir ${RootShell.quote(upperOf(push.key))} || echo $FAIL${RootShell.quote(push.key)}"
            }
            failed += failuresOf(shell.runScript(script), folders.map { it.key })
        }

        val finish = StringBuilder()
        val finishKeys = mutableListOf<String>()
        pushes.filter { !it.entry.isDirectory }.forEach { push ->
            val real = upperOf(push.key)
            val mode = ParcelFileDescriptor.MODE_WRITE_ONLY or ParcelFileDescriptor.MODE_CREATE or
                ParcelFileDescriptor.MODE_TRUNCATE
            val written = shell.open(real, mode)?.let { descriptor ->
                runCatching {
                    File(mirrorOf(push.key)).inputStream().use { input ->
                        ParcelFileDescriptor.AutoCloseOutputStream(descriptor).use { input.copyTo(it) }
                    }
                }.isSuccess
            } == true
            if (!written) {
                failed += push.key
                return@forEach
            }
            finish.append("touch -m -d ${touchTime(push.entry.mtimeSec)} ${RootShell.quote(real)} || echo $FAIL${RootShell.quote(push.key)}\n")
            finishKeys += push.key
        }
        val deletes = actions.filterIsInstance<Action.DeleteReal>()
        deletes.filter { !it.isDirectory }.forEach {
            finish.append("rm -f ${RootShell.quote(upperOf(it.key))} || echo $FAIL${RootShell.quote(it.key)}\n")
            finishKeys += it.key
        }
        deletes.filter { it.isDirectory }.sortedByDescending { it.key.length }.forEach {
            finish.append("rmdir ${RootShell.quote(upperOf(it.key))} || echo $FAIL${RootShell.quote(it.key)}\n")
            finishKeys += it.key
        }
        if (finish.isNotEmpty()) failed += failuresOf(shell.runScript(finish.toString()), finishKeys)
        return failed
    }

    private fun failuresOf(result: RootShell.Result?, keys: List<String>): Set<String> =
        result?.output?.filter { it.startsWith(FAIL) }?.map { it.removePrefix(FAIL) }?.toSet() ?: keys.toSet()

    private fun applyThroughRoot(actions: List<Action>): Set<String> {
        val uid = Process.myUid()
        val rootCtx = "\$(stat -c %C ${RootShell.quote(root.absolutePath)})"
        val script = StringBuilder("ctx=$rootCtx\n")

        actions.filterIsInstance<Action.Pull>().filter { !it.entry.isDirectory }.chunked(PULL_BATCH).forEach { batch ->
            script.append("set --\n")
            batch.forEach { pull ->
                val r = RootShell.quote(pull.key)
                val m = RootShell.quote(mirrorOf(pull.key))
                script.append("if cp -p $r $m; then set -- \"\$@\" $m; else echo $FAIL$r; fi\n")
            }
            val failAll = batch.joinToString("; ") { "echo $FAIL${RootShell.quote(it.key)}" }
            script.append(
                "if [ \$# -gt 0 ]; then chown $uid:$uid \"\$@\" && chmod 600 \"\$@\" && chcon \"\$ctx\" \"\$@\" || { $failAll; }; fi\n"
            )
        }

        val pushes = actions.filterIsInstance<Action.Push>()
        pushes.filter { it.entry.isDirectory }.sortedBy { it.key.length }.forEach { push ->
            val r = RootShell.quote(push.key)
            script.append(
                "pd=\$(dirname $r); mkdir $r && chown \$(stat -c %u:%g \"\$pd\") $r && " +
                    "chmod \$(stat -c %a \"\$pd\") $r && chcon \$(stat -c %C \"\$pd\") $r || echo $FAIL$r\n"
            )
        }
        pushes.filter { !it.entry.isDirectory }.forEach { push ->
            val r = RootShell.quote(push.key)
            val m = RootShell.quote(mirrorOf(push.key))
            if (push.isNew) {
                script.append(
                    "pd=\$(dirname $r); cat $m > $r && chown \$(stat -c %u:%g \"\$pd\") $r && " +
                        "chmod $NEW_FILE_MODE $r && chcon \$(stat -c %C \"\$pd\") $r && touch -r $m $r || echo $FAIL$r\n"
                )
            } else {
                script.append("cat $m > $r && touch -r $m $r || echo $FAIL$r\n")
            }
        }

        val deletes = actions.filterIsInstance<Action.DeleteReal>()
        deletes.filter { !it.isDirectory }.forEach {
            val r = RootShell.quote(it.key)
            script.append("rm -f $r || echo $FAIL$r\n")
        }
        deletes.filter { it.isDirectory }.sortedByDescending { it.key.length }.forEach {
            val r = RootShell.quote(it.key)
            script.append("rmdir $r || echo $FAIL$r\n")
        }

        val result = RootShell.run(context, script.toString())
        if (result == null) {
            Logger.warn(TAG, "sync: root route refused the copy | actions=${actions.size}")
            return actions.map { it.key }.toSet()
        }
        return result.output.filter { it.startsWith(FAIL) }.map { it.removePrefix(FAIL) }.toSet()
    }

    private fun mirrorOf(key: String): String =
        root.absolutePath + "/" + key.removePrefix(LOWER_PREFIX)

    private fun touchTime(mtimeSec: Long): String = Instant.ofEpochSecond(mtimeSec).toString()

    private fun keyOfMirror(file: File): String =
        LOWER_PREFIX + file.absolutePath.removePrefix(root.absolutePath + "/")

    companion object {
        private const val TAG = "AndroidDataMirror"
        private const val MIRROR_DIR = "android-data-mirror"
        private const val LOWER_PREFIX = "/data/media/"
        private const val EXT_DATA_RW_GID = 1078
        private const val FRESH_MS = 3_000L
        private const val PULL_BATCH = 100
        private const val MAX_FILE_BYTES = 512L * 1024 * 1024
        private const val NEW_FILE_MODE = "660"
        private const val SCOPE_PRESENT = "SCOPE:present"
        private const val SCOPE_ABSENT = "SCOPE:absent"
        private const val FAIL = "FAIL:"
        private const val MS_PER_SECOND = 1_000L
        private const val UPPER_PREFIX = "/storage/emulated/"
        private val LEFT_TO_RIGHT_ISOLATE = Char(0x2066).toString()
        private val EMULATED = Regex("^/storage/emulated/(\\d+)/(Android/(?:data|obb)(?:/.*)?)$")
        private val SDCARD = Regex("^/sdcard/(Android/(?:data|obb)(?:/.*)?)$")
        private val SCOPE_ROOT = Regex("^/data/media/\\d+/Android/(data|obb)$")
        private val INSIDE_PACKAGE = Regex("^/data/media/\\d+/Android/(data|obb)/[^/]+/.+")

        internal fun lowerOf(path: String): String? {
            val clean = path.replace(LEFT_TO_RIGHT_ISOLATE, "").trimEnd('/')
            if (".." in clean.split('/')) return null
            EMULATED.find(clean)?.let { return "$LOWER_PREFIX${it.groupValues[1]}/${it.groupValues[2]}" }
            SDCARD.find(clean)?.let { return "${LOWER_PREFIX}0/${it.groupValues[1]}" }
            return null
        }

        internal fun upperOf(key: String): String = UPPER_PREFIX + key.removePrefix(LOWER_PREFIX)

        internal fun parentScope(key: String): String? {
            if (SCOPE_ROOT.matches(key)) return null
            return key.substringBeforeLast('/')
        }

        internal fun isUnder(key: String, scope: String, recursive: Boolean): Boolean {
            if (!key.startsWith("$scope/")) return false
            return recursive || '/' !in key.removePrefix("$scope/")
        }

        internal fun parseStatLine(line: String): Pair<String, Entry>? {
            val parts = line.split('|', limit = 4)
            if (parts.size != 4) return null
            val isDirectory = when {
                parts[0] == "directory" -> true
                parts[0].startsWith("regular") -> false
                else -> return null
            }
            val size = parts[1].toLongOrNull() ?: return null
            val mtime = parts[2].toLongOrNull() ?: return null
            return parts[3] to Entry(isDirectory, if (isDirectory) 0L else size, mtime)
        }

        private fun same(a: Entry?, b: Entry?): Boolean = when {
            a == null || b == null -> a == b
            a.isDirectory || b.isDirectory -> a.isDirectory == b.isDirectory
            else -> a.size == b.size && a.mtimeSec == b.mtimeSec
        }

        internal fun plan(
            real: Map<String, Entry>,
            mirror: Map<String, Entry>,
            base: Map<String, Entry>,
            maxFileBytes: Long
        ): List<Action> {
            val keys = real.keys + mirror.keys + base.keys
            return keys.mapNotNull { key ->
                val r = real[key]
                val m = mirror[key]
                val b = base[key]
                if (r != null && !r.isDirectory && r.size > maxFileBytes) return@mapNotNull null
                if (m != null && !m.isDirectory && m.size > maxFileBytes) return@mapNotNull null
                when {
                    same(m, b) && same(r, b) -> null
                    same(m, b) -> if (r == null) Action.DropFromMirror(key) else Action.Pull(key, r)
                    same(r, b) -> if (m == null) Action.DeleteReal(key, b?.isDirectory == true) else Action.Push(key, m, isNew = r == null)
                    same(r, m) -> r?.let { Action.Adopt(key, it) } ?: Action.DropFromMirror(key)
                    r == null -> Action.DropFromMirror(key)
                    else -> Action.Pull(key, r)
                }
            }
        }

        private fun processHasGroup(gid: Int): Boolean = runCatching {
            File("/proc/self/status").readLines()
                .firstOrNull { it.startsWith("Groups:") }
                ?.removePrefix("Groups:")
                ?.trim()
                ?.split(Regex("\\s+"))
                ?.contains(gid.toString()) == true
        }.getOrDefault(true)
    }
}
