package com.nendo.argosy.data.mod

import com.nendo.argosy.data.storage.FileAccessLayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import javax.inject.Inject
import javax.inject.Singleton

data class SwitchModInstallResult(val installedFiles: Int, val skippedDesktopSettings: Boolean)

@Singleton
class SwitchModInstaller @Inject constructor(private val fal: FileAccessLayer) {
    suspend fun install(
        archive: File,
        requestedTitle: String,
        packageId: String,
        dataRoot: String,
        stateRoot: File,
        confirmedEmulatorClosed: Boolean,
        profile: SwitchModProfile = SwitchModProfile.ORIGINAL
    ): SwitchModInstallResult = withContext(Dispatchers.IO) {
        mutex.withLock {
            requireClosed(confirmedEmulatorClosed)
            val title = SwitchModPackage.titleId(requestedTitle)
            val root = requireRoot(dataRoot)
            val store = SwitchModStateStore(stateRoot, root, title, packageId)
            if (store.hasPending()) throw ModInstallException(ModInstallFailure.NEEDS_RECOVERY)
            if (!stateRoot.mkdirs() && !stateRoot.isDirectory) throw ModInstallException(ModInstallFailure.ACCESS_DENIED)
            val staging = Files.createTempDirectory(stateRoot.toPath(), "staging-").toFile()
            try {
                if (packageId.isBlank() || packageId.length > 256) throw ModInstallException(ModInstallFailure.CORRUPT_STATE)
                val staged = SwitchModPackage.stage(archive, title, staging, profile)
                val before = store.receipt()?.also { requireContext(it, title, root, packageId) }
                val allPaths = (before?.files.orEmpty().map { it.path } + staged.files.map { it.relativePath }).distinct()
                requirePrepared(root, allPaths)
                before?.files?.forEach { if (currentHash(root, it.path) != it.sha256) modified(it.path) }
                val old = before?.files.orEmpty().associateBy { it.path }
                val foreign = store.claims().filter { it.dataRoot == root && it.packageId != packageId }
                    .flatMap { it.files }.map { modPathKey(it.path) }
                val files = staged.files.map { entry ->
                    val folded = modPathKey(entry.relativePath)
                    if (foreign.any { it == folded || it.startsWith("$folded/") || folded.startsWith("$it/") }) {
                        throw ModInstallException(ModInstallFailure.CONFLICT, entry.relativePath)
                    }
                    val existing = currentHash(root, entry.relativePath)
                    val previous = old[entry.relativePath]
                    when {
                        previous?.owned == true -> ModOwnedFile(entry.relativePath, entry.sha256, true)
                        existing == null -> ModOwnedFile(entry.relativePath, entry.sha256, true)
                        existing == entry.sha256 -> ModOwnedFile(entry.relativePath, entry.sha256, false)
                        else -> throw ModInstallException(ModInstallFailure.CONFLICT, entry.relativePath)
                    }
                }
                val after = ModReceipt(title, root, packageId, staged.archiveSha256, files)
                val payloads = staged.files.associate { it.relativePath to it.file }
                transact(store, before, after, root, payloads)
                SwitchModInstallResult(files.size, staged.skippedDesktopSettings)
            } finally {
                staging.deleteRecursively()
            }
        }
    }

    suspend fun remove(
        requestedTitle: String,
        packageId: String,
        dataRoot: String,
        stateRoot: File,
        confirmedEmulatorClosed: Boolean
    ): Unit = withContext(Dispatchers.IO) {
        mutex.withLock {
            requireClosed(confirmedEmulatorClosed)
            val title = SwitchModPackage.titleId(requestedTitle)
            val root = requireRoot(dataRoot)
            val store = SwitchModStateStore(stateRoot, root, title, packageId)
            if (store.hasPending()) throw ModInstallException(ModInstallFailure.NEEDS_RECOVERY)
            val before = store.receipt() ?: return@withLock
            requireContext(before, title, root, packageId)
            requirePrepared(root, before.files.map { it.path })
            before.files.filter { it.owned }.forEach { if (currentHash(root, it.path) != it.sha256) modified(it.path) }
            transact(store, before, null, root, emptyMap())
        }
    }

    suspend fun recover(
        requestedTitle: String,
        packageId: String,
        dataRoot: String,
        stateRoot: File,
        confirmedEmulatorClosed: Boolean,
        restoreInterruptedFiles: Boolean = false
    ): Unit = withContext(Dispatchers.IO) {
        mutex.withLock {
            requireClosed(confirmedEmulatorClosed)
            val title = SwitchModPackage.titleId(requestedTitle)
            val root = requireRoot(dataRoot)
            val store = SwitchModStateStore(stateRoot, root, title, packageId)
            if (!store.pending.exists()) return@withLock
            val receipts = listOfNotNull(store.before(), store.after())
            if (receipts.isEmpty()) throw ModInstallException(ModInstallFailure.CORRUPT_STATE)
            receipts.forEach { requireContext(it, title, root, packageId) }
            rollback(store, root, title, restoreInterruptedFiles)
        }
    }

    private fun transact(
        store: SwitchModStateStore,
        before: ModReceipt?,
        after: ModReceipt?,
        root: String,
        payloads: Map<String, File>
    ) {
        store.clearTransaction()
        val oldOwned = before?.files.orEmpty().filter { it.owned }.associateBy { it.path }
        val newOwned = after?.files.orEmpty().filter { it.owned }.associateBy { it.path }
        val mutations = (oldOwned.keys + newOwned.keys).mapNotNull { path ->
            val original = currentHash(root, path)
            if (original != oldOwned[path]?.sha256) modified(path)
            val expected = newOwned[path]?.sha256
            if (original == expected) null else ModMutation(path, original, expected, null)
        }.mapIndexed { index, item ->
            if (item.before == null) item else {
                val backup = File(store.transaction, index.toString())
                if (!backup.parentFile!!.mkdirs() && !backup.parentFile!!.isDirectory) {
                    throw ModInstallException(ModInstallFailure.ACCESS_DENIED)
                }
                val input = fal.getInputStream("$root/${item.path}")
                    ?: throw ModInstallException(ModInstallFailure.ACCESS_DENIED, item.path)
                input.use { source -> FileOutputStream(backup).use { output ->
                    copyBounded(source, output)
                    output.fd.sync()
                } }
                if (modHash(backup) != item.before) modified(item.path)
                item.copy(backup = index.toString())
            }
        }
        store.prepare(before, after, mutations)
        try {
            mutations.forEachIndexed { index, item ->
                if (currentHash(root, item.path) != item.before) modified(item.path)
                store.markStarted(index + 1)
                if (item.after == null) {
                    if (!fal.delete("$root/${item.path}")) throw ModInstallException(ModInstallFailure.ACCESS_DENIED, item.path)
                } else {
                    val source = payloads[item.path] ?: throw ModInstallException(ModInstallFailure.CORRUPT_STATE)
                    if (modHash(source) != item.after) throw ModInstallException(ModInstallFailure.INVALID_ARCHIVE, item.path)
                    write(root, item.path, source)
                }
            }
            requireCommitted(root, mutations.map { it.path })
            mutations.forEach { if (currentHash(root, it.path) != it.after) modified(it.path) }
            store.persistReceipt(after)
        } catch (failure: Exception) {
            try { rollback(store, root, (before ?: after)!!.titleId, allowPartial = true) }
            catch (rollbackFailure: Exception) { failure.addSuppressed(rollbackFailure) }
            throw failure
        }
        store.clearTransaction()
    }

    private fun rollback(store: SwitchModStateStore, root: String, title: String, allowPartial: Boolean) {
        val planned = store.mutations(title)
        val mutations = planned.take(store.startedCount(planned.size))
        requirePrepared(root, mutations.map { it.path })
        mutations.forEach { item ->
            val hash = currentHash(root, item.path)
            if (!allowPartial && hash != item.before && hash != item.after) modified(item.path)
            if (item.before != null) {
                val backup = item.backup?.let { File(store.transaction, it) }
                    ?: throw ModInstallException(ModInstallFailure.CORRUPT_STATE)
                if (modHash(backup) != item.before) throw ModInstallException(ModInstallFailure.CORRUPT_STATE)
            }
        }
        mutations.asReversed().forEach { item ->
            if (currentHash(root, item.path) == item.before) return@forEach
            if (item.before == null) {
                if (!fal.delete("$root/${item.path}")) throw ModInstallException(ModInstallFailure.ACCESS_DENIED, item.path)
            } else write(root, item.path, File(store.transaction, item.backup!!))
        }
        requireCommitted(root, mutations.map { it.path })
        mutations.forEach { if (currentHash(root, it.path) != it.before) modified(it.path) }
        store.persistReceipt(store.before())
        store.clearTransaction()
    }

    private fun write(root: String, relativePath: String, source: File) {
        val path = "$root/$relativePath"
        val parent = path.substringBeforeLast('/')
        if (!fal.mkdirs(parent) && !fal.isDirectory(parent)) throw ModInstallException(ModInstallFailure.ACCESS_DENIED, relativePath)
        val output = fal.getOutputStream(path) ?: throw ModInstallException(ModInstallFailure.ACCESS_DENIED, relativePath)
        source.inputStream().use { input -> output.use { copyBounded(input, it) } }
    }

    private fun currentHash(root: String, relativePath: String): String? {
        var directory = root
        val parts = relativePath.split('/')
        parts.forEachIndexed { index, component ->
            if (Files.isSymbolicLink(fal.getTransformedFile(directory).toPath()) || !fal.isListable(directory)) {
                throw ModInstallException(ModInstallFailure.ACCESS_DENIED, relativePath)
            }
            val matching = fal.listFilesUnion(directory).filter { modPathKey(it.name) == modPathKey(component) }
            if (matching.isEmpty()) return null
            val found = matching.singleOrNull()?.takeIf { it.name == component }
                ?: throw ModInstallException(ModInstallFailure.CONFLICT, relativePath)
            directory += "/$component"
            if (Files.isSymbolicLink(fal.getTransformedFile(directory).toPath())) {
                throw ModInstallException(ModInstallFailure.UNSAFE_PATH, relativePath)
            }
            if (index < parts.lastIndex && !found.isDirectory) throw ModInstallException(ModInstallFailure.CONFLICT, relativePath)
            if (index == parts.lastIndex && !found.isFile) throw ModInstallException(ModInstallFailure.CONFLICT, relativePath)
        }
        return fal.getInputStream(directory)?.use { modHash(it) }
            ?: throw ModInstallException(ModInstallFailure.ACCESS_DENIED, relativePath)
    }

    private fun requireRoot(path: String): String {
        val root = File(path)
        if (!root.isAbsolute || path.trimEnd('/') == "" || path.split('/').any { it == "." || it == ".." } ||
            Files.isSymbolicLink(fal.getTransformedFile(path).toPath())) {
            throw ModInstallException(ModInstallFailure.ACCESS_DENIED)
        }
        return path.trimEnd('/')
    }

    private fun accessRoots(root: String, paths: List<String>): Array<String> = paths.map { path ->
        "$root/${path.split('/').take(if (path.startsWith("load/")) 2 else 4).joinToString("/")}"
    }.distinct().toTypedArray()

    private fun requirePrepared(root: String, paths: List<String>) {
        if (!fal.prepareSaveAccess(*accessRoots(root, paths))) throw ModInstallException(ModInstallFailure.ACCESS_DENIED)
    }

    private fun requireCommitted(root: String, paths: List<String>) {
        if (!fal.commitSaveAccess(*accessRoots(root, paths))) throw ModInstallException(ModInstallFailure.ACCESS_DENIED)
    }

    private fun requireContext(receipt: ModReceipt, title: String, root: String, packageId: String) {
        if (receipt.titleId != title || receipt.dataRoot != root || receipt.packageId != packageId) {
            throw ModInstallException(ModInstallFailure.CORRUPT_STATE)
        }
    }

    private fun requireClosed(confirmed: Boolean) {
        if (!confirmed) throw ModInstallException(ModInstallFailure.EMULATOR_CLOSE_REQUIRED)
    }

    private fun copyBounded(input: java.io.InputStream, output: java.io.OutputStream) {
        val buffer = ByteArray(8192)
        var size = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            size += read
            if (size > SwitchModPackage.MAX_FILE_BYTES) throw ModInstallException(ModInstallFailure.TOO_LARGE)
            output.write(buffer, 0, read)
        }
    }

    private fun modified(path: String): Nothing = throw ModInstallException(ModInstallFailure.MODIFIED_FILE, path)

    internal companion object {
        private val mutex = Mutex()
        val isBusy: Boolean get() = mutex.isLocked
    }
}
