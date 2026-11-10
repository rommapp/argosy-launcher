package com.nendo.argosy.data.mod

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.Properties

internal data class ModOwnedFile(val path: String, val sha256: String, val owned: Boolean)

internal data class ModReceipt(
    val titleId: String,
    val dataRoot: String,
    val packageId: String,
    val archiveSha256: String,
    val files: List<ModOwnedFile>
)

internal data class ModMutation(val path: String, val before: String?, val after: String?, val backup: String?)

internal data class StoredModPackage(val installed: ModReceipt?, val pending: ModReceipt?)

internal class SwitchModStateStore(val root: File, dataRoot: String, titleId: String, packageId: String) {
    val directory: File = File(root, stateKey("$dataRoot\u0000$titleId\u0000$packageId"))
    val transaction = File(directory, "transaction")
    val pending = File(transaction, "pending.properties")
    private val installed = File(directory, "installed.properties")

    fun receipt(): ModReceipt? = readReceipt(installed)

    fun claims(): List<ModReceipt> = catalog().mapNotNull { it.installed }

    fun catalog(): List<StoredModPackage> = folders().mapNotNull { folder ->
        val installed = readReceipt(File(folder, "installed.properties"))
        val pendingFile = File(folder, "transaction/pending.properties")
        val before = if (pendingFile.exists()) readReceipt(File(folder, "transaction/before.properties")) else null
        val after = if (pendingFile.exists()) readReceipt(File(folder, "transaction/after.properties")) else null
        val pending = after ?: before
        if (pendingFile.exists() && pending == null) throw ModInstallException(ModInstallFailure.CORRUPT_STATE)
        val metadata = pending ?: installed ?: return@mapNotNull null
        val expectedFolder = stateKey("${metadata.dataRoot}\u0000${metadata.titleId}\u0000${metadata.packageId}")
        if (folder.name != expectedFolder || listOfNotNull(installed, before, after).any {
                it.dataRoot != metadata.dataRoot || it.titleId != metadata.titleId || it.packageId != metadata.packageId
            }) throw ModInstallException(ModInstallFailure.CORRUPT_STATE)
        StoredModPackage(installed, pending)
    }

    fun hasPending(): Boolean = folders().any { File(it, "transaction/pending.properties").exists() }

    private fun folders(): Array<File> {
        if (!root.exists()) return emptyArray()
        if (Files.isSymbolicLink(root.toPath())) throw ModInstallException(ModInstallFailure.CORRUPT_STATE)
        return root.listFiles() ?: throw ModInstallException(ModInstallFailure.ACCESS_DENIED)
    }

    fun persistReceipt(receipt: ModReceipt?) {
        if (receipt == null) {
            if (installed.exists() && !installed.delete()) throw ModInstallException(ModInstallFailure.ACCESS_DENIED)
        } else writeReceipt(installed, receipt)
    }

    fun prepare(before: ModReceipt?, after: ModReceipt?, mutations: List<ModMutation>) {
        if (before != null) writeReceipt(File(transaction, "before.properties"), before)
        if (after != null) writeReceipt(File(transaction, "after.properties"), after)
        val properties = Properties()
        properties.setProperty("count", mutations.size.toString())
        properties.setProperty("started", "0")
        mutations.forEachIndexed { index, item ->
            properties.setProperty("$index.path", item.path)
            item.before?.let { properties.setProperty("$index.before", it) }
            item.after?.let { properties.setProperty("$index.after", it) }
            item.backup?.let { properties.setProperty("$index.backup", it) }
        }
        writeProperties(pending, properties)
    }

    fun markStarted(count: Int) {
        val properties = readProperties(pending)
        properties.setProperty("started", count.toString())
        writeProperties(pending, properties)
    }

    fun startedCount(count: Int): Int = readProperties(pending).getProperty("started")?.toIntOrNull()
        ?.takeIf { it in 0..count } ?: throw ModInstallException(ModInstallFailure.CORRUPT_STATE)

    fun mutations(titleId: String): List<ModMutation> {
        val properties = readProperties(pending)
        val count = properties.getProperty("count")?.toIntOrNull()
            ?.takeIf { it in 0..(SwitchModPackage.MAX_ENTRIES * 2) }
            ?: throw ModInstallException(ModInstallFailure.CORRUPT_STATE)
        return (0 until count).map { index ->
            val path = properties.getProperty("$index.path") ?: throw ModInstallException(ModInstallFailure.CORRUPT_STATE)
            if (SwitchModPackage.canonicalPath(path, titleId) != path) throw ModInstallException(ModInstallFailure.CORRUPT_STATE)
            val backup = properties.getProperty("$index.backup")
            if (backup != null && backup != index.toString()) throw ModInstallException(ModInstallFailure.CORRUPT_STATE)
            ModMutation(path, properties.getProperty("$index.before"), properties.getProperty("$index.after"), backup)
        }
    }

    fun before(): ModReceipt? = readReceipt(File(transaction, "before.properties"))

    fun after(): ModReceipt? = readReceipt(File(transaction, "after.properties"))

    fun clearTransaction() {
        if (transaction.exists() && !transaction.deleteRecursively()) throw ModInstallException(ModInstallFailure.ACCESS_DENIED)
    }

    private fun readReceipt(file: File): ModReceipt? {
        if (!file.exists()) return null
        val properties = readProperties(file)
        val title = properties.getProperty("title") ?: throw ModInstallException(ModInstallFailure.CORRUPT_STATE)
        val count = properties.getProperty("count")?.toIntOrNull()
            ?.takeIf { it in 0..SwitchModPackage.MAX_ENTRIES }
            ?: throw ModInstallException(ModInstallFailure.CORRUPT_STATE)
        return ModReceipt(
            titleId = SwitchModPackage.titleId(title),
            dataRoot = properties.getProperty("root") ?: throw ModInstallException(ModInstallFailure.CORRUPT_STATE),
            packageId = properties.getProperty("package") ?: throw ModInstallException(ModInstallFailure.CORRUPT_STATE),
            archiveSha256 = properties.getProperty("archive") ?: throw ModInstallException(ModInstallFailure.CORRUPT_STATE),
            files = (0 until count).map { index ->
                val path = properties.getProperty("$index.path") ?: throw ModInstallException(ModInstallFailure.CORRUPT_STATE)
                if (SwitchModPackage.canonicalPath(path, title) != path) throw ModInstallException(ModInstallFailure.CORRUPT_STATE)
                ModOwnedFile(
                    path,
                    properties.getProperty("$index.hash") ?: throw ModInstallException(ModInstallFailure.CORRUPT_STATE),
                    when (properties.getProperty("$index.owned")) {
                        "true" -> true
                        "false" -> false
                        else -> throw ModInstallException(ModInstallFailure.CORRUPT_STATE)
                    }
                )
            }
        )
    }

    private fun writeReceipt(file: File, receipt: ModReceipt) {
        val properties = Properties()
        properties.setProperty("title", receipt.titleId)
        properties.setProperty("root", receipt.dataRoot)
        properties.setProperty("package", receipt.packageId)
        properties.setProperty("archive", receipt.archiveSha256)
        properties.setProperty("count", receipt.files.size.toString())
        receipt.files.forEachIndexed { index, item ->
            properties.setProperty("$index.path", item.path)
            properties.setProperty("$index.hash", item.sha256)
            properties.setProperty("$index.owned", item.owned.toString())
        }
        writeProperties(file, properties)
    }

    private fun readProperties(file: File): Properties {
        if (file.length() > 32 * 1024 * 1024) throw ModInstallException(ModInstallFailure.CORRUPT_STATE)
        return Properties().apply { file.inputStream().use { load(it) } }
    }

    private fun writeProperties(file: File, properties: Properties) {
        if (!file.parentFile!!.mkdirs() && !file.parentFile!!.isDirectory) {
            throw ModInstallException(ModInstallFailure.ACCESS_DENIED)
        }
        val temporary = File(file.parentFile, "${file.name}.tmp")
        FileOutputStream(temporary).use { output ->
            properties.store(output, null)
            output.fd.sync()
        }
        Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    }

    private fun stateKey(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}
