package com.nendo.argosy.data.emulator.config

import java.io.File
import java.security.MessageDigest

internal data class ConfigEdit(val section: String, val key: String, val written: String, val writtenFileSha256: String)

internal class ConfigBackup(val file: File, val edit: ConfigEdit?)

internal class ConfigBackupStore(
    private val root: File,
    private val keep: Int = DEFAULT_KEEP,
    private val clock: () -> Long = System::currentTimeMillis
) {
    fun save(packageName: String, file: ConfigFileSpec, bytes: ByteArray, edit: ConfigEdit? = null): File? {
        val dir = File(root, packageName)
        if (!dir.isDirectory && !dir.mkdirs()) return null
        var stamp = clock()
        while (File(dir, name(file, stamp)).exists()) stamp++
        val target = File(dir, name(file, stamp))
        val written = runCatching {
            target.writeBytes(bytes)
            edit?.let { editFile(target).writeText(listOf(it.section, it.key, it.written, it.writtenFileSha256).joinToString(EDIT_SEPARATOR)) }
        }.isSuccess
        if (!written) {
            delete(target)
            return null
        }
        list(packageName, file).drop(keep).forEach(::delete)
        return target
    }

    fun newest(packageName: String, file: ConfigFileSpec): File? = list(packageName, file).firstOrNull()

    fun newestBackup(packageName: String, file: ConfigFileSpec): ConfigBackup? =
        newest(packageName, file)?.let { ConfigBackup(it, editOf(it)) }

    fun list(packageName: String, file: ConfigFileSpec): List<File> {
        val prefix = baseName(file) + "."
        return File(root, packageName).listFiles()
            ?.filter { it.isFile && it.name.startsWith(prefix) }
            ?.mapNotNull { backup -> backup.name.removePrefix(prefix).toLongOrNull()?.let { it to backup } }
            ?.sortedByDescending { it.first }
            ?.map { it.second }
            .orEmpty()
    }

    fun delete(backup: File) {
        backup.delete()
        editFile(backup).delete()
    }

    private fun editOf(backup: File): ConfigEdit? {
        val parts = runCatching { editFile(backup).readText() }.getOrNull()?.split(EDIT_SEPARATOR) ?: return null
        if (parts.size != EDIT_FIELDS) return null
        return ConfigEdit(parts[0], parts[1], parts[2], parts[3])
    }

    private fun editFile(backup: File): File = File(backup.parentFile, backup.name + EDIT_SUFFIX)

    private fun name(file: ConfigFileSpec, stamp: Long): String = "${baseName(file)}.$stamp"

    private fun baseName(file: ConfigFileSpec): String = file.id.replace('/', '_')

    companion object {
        const val DIR = "emulator-config-backups"
        const val DEFAULT_KEEP = 5
        private const val EDIT_SUFFIX = ".edit"
        private const val EDIT_SEPARATOR = "\n"
        private const val EDIT_FIELDS = 4

        fun sha256(bytes: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }
}
