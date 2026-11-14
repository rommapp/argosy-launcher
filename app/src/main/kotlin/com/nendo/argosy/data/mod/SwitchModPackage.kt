package com.nendo.argosy.data.mod

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipFile
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.Locale
import java.util.zip.CRC32

enum class ModInstallFailure {
    INVALID_TITLE, INVALID_ARCHIVE, UNSAFE_PATH, UNSUPPORTED_PATH, WRONG_TITLE,
    DUPLICATE_PATH, TOO_LARGE, EMPTY_PACKAGE, ACCESS_DENIED, CONFLICT,
    MODIFIED_FILE, NEEDS_RECOVERY, EMULATOR_CLOSE_REQUIRED, CORRUPT_STATE, UNSUPPORTED_PROFILE, UNSUPPORTED_EMULATOR
}

class ModInstallException(val reason: ModInstallFailure, val path: String? = null) :
    IOException("${reason.name}${path?.let { ": $it" }.orEmpty()}")

data class StagedModFile(val relativePath: String, val file: File, val sha256: String)

data class StagedSwitchMod(
    val titleId: String,
    val archiveSha256: String,
    val files: List<StagedModFile>,
    val skippedDesktopSettings: Boolean
)

internal fun modHash(file: File): String = file.inputStream().use { modHash(it) }

internal fun modPathKey(path: String): String = path.uppercase(Locale.ROOT).lowercase(Locale.ROOT)

internal fun modHash(input: java.io.InputStream): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(8192)
    var size = 0L
    while (true) {
        val read = input.read(buffer)
        if (read < 0) break
        size += read
        if (size > SwitchModPackage.MAX_FILE_BYTES) throw ModInstallException(ModInstallFailure.TOO_LARGE)
        digest.update(buffer, 0, read)
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

object SwitchModPackage {
    const val MAX_FILE_BYTES = 256L * 1024 * 1024
    const val MAX_TOTAL_BYTES = 1024L * 1024 * 1024
    const val MAX_ENTRIES = 10_000
    private val titlePattern = Regex("[0-9A-F]{16}")
    private val ultraCamTitles = mapOf("0100F2C0115B6000" to "TOTK", "01007EF00011E000" to "BOTW")

    fun titleId(value: String): String = value.uppercase(Locale.ROOT).also {
        if (!titlePattern.matches(it)) throw ModInstallException(ModInstallFailure.INVALID_TITLE)
    }

    fun stage(archive: File, requestedTitle: String, directory: File, profile: SwitchModProfile = SwitchModProfile.ORIGINAL): StagedSwitchMod {
        val title = titleId(requestedTitle)
        if (!directory.isDirectory || directory.list().isNullOrEmpty().not()) {
            throw ModInstallException(ModInstallFailure.ACCESS_DENIED)
        }
        if (archive.length() > MAX_TOTAL_BYTES) throw ModInstallException(ModInstallFailure.TOO_LARGE)
        val archiveDigest = archive.inputStream().use { input ->
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(8192)
            var size = 0L
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                size += count
                if (size > MAX_TOTAL_BYTES) throw ModInstallException(ModInstallFailure.TOO_LARGE)
                digest.update(buffer, 0, count)
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        }
        return ZipFile.builder().setFile(archive).get().use { zip ->
            val entries = mutableListOf<Pair<ZipArchiveEntry, String?>>()
            val claimed = mutableSetOf<String>()
            var advertised = 0L
            var skippedSettings = false
            val iterator = zip.entries
            while (iterator.hasMoreElements()) {
                val entry = iterator.nextElement()
                if (entries.size >= MAX_ENTRIES) throw ModInstallException(ModInstallFailure.TOO_LARGE)
                if (entry.rawName?.any { it == 0.toByte() || it == '\\'.code.toByte() } == true) {
                    throw ModInstallException(ModInstallFailure.UNSAFE_PATH, entry.name)
                }
                val mode = entry.unixMode and 0xf000
                if (mode !in setOf(0, if (entry.isDirectory) 0x4000 else 0x8000) || entry.isUnixSymlink || !zip.canReadEntryData(entry)) {
                    throw ModInstallException(ModInstallFailure.INVALID_ARCHIVE, entry.name)
                }
                val path = canonicalPath(entry.name, title, entry.isDirectory)
                if (!claimed.add(modPathKey(entry.name.trimEnd('/')))) {
                    throw ModInstallException(ModInstallFailure.DUPLICATE_PATH, entry.name)
                }
                if (!entry.isDirectory) {
                    if (entry.size !in 0..MAX_FILE_BYTES) throw ModInstallException(ModInstallFailure.TOO_LARGE)
                    advertised += entry.size
                    if (advertised > MAX_TOTAL_BYTES) throw ModInstallException(ModInstallFailure.TOO_LARGE)
                    if (entry.name.equals("config/custom/$title.ini", ignoreCase = true)) skippedSettings = true
                }
                entries += entry to path
            }
            val destinations = entries.filter { !it.first.isDirectory && it.second != null }.map { it.second!! }
            val folded = destinations.map(::modPathKey).toSet()
            val prefixes = mutableMapOf<String, String>()
            destinations.forEach { destination ->
                val parts = destination.split('/')
                for (count in 1 until parts.size) {
                    val prefix = parts.take(count).joinToString("/")
                    val key = modPathKey(prefix)
                    val previous = prefixes.putIfAbsent(key, prefix)
                    if (key in folded || (previous != null && previous != prefix)) {
                        throw ModInstallException(ModInstallFailure.DUPLICATE_PATH, destination)
                    }
                }
            }
            val staged = mutableListOf<StagedModFile>()
            var total = 0L
            entries.filter { !it.first.isDirectory && it.second != null }.forEachIndexed { index, (entry, path) ->
                val target = File(directory, index.toString())
                val crc = CRC32()
                var size = 0L
                zip.getInputStream(entry).use { input ->
                    target.outputStream().use { output ->
                        val buffer = ByteArray(8192)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            size += count
                            total += count
                            if (size > MAX_FILE_BYTES || total > MAX_TOTAL_BYTES) {
                                throw ModInstallException(ModInstallFailure.TOO_LARGE)
                            }
                            crc.update(buffer, 0, count)
                            output.write(buffer, 0, count)
                        }
                    }
                }
                if (size != entry.size || crc.value != entry.crc) {
                    throw ModInstallException(ModInstallFailure.INVALID_ARCHIVE, entry.name)
                }
                if (profile == SwitchModProfile.HANDHELD_720 && path == UltraCamHandheldProfile.CONFIG_PATH) {
                    if (target.length() > 1024 * 1024) throw ModInstallException(ModInstallFailure.UNSUPPORTED_PROFILE, path)
                    val text = try {
                        Charsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                            .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
                            .decode(java.nio.ByteBuffer.wrap(target.readBytes())).toString()
                    } catch (_: java.nio.charset.CharacterCodingException) {
                        throw ModInstallException(ModInstallFailure.UNSUPPORTED_PROFILE, path)
                    }
                    target.writeText(UltraCamHandheldProfile.apply(text), Charsets.UTF_8)
                }
                staged += StagedModFile(requireNotNull(path), target, modHash(target))
            }
            if (staged.none { it.relativePath.startsWith("load/") }) throw ModInstallException(ModInstallFailure.EMPTY_PACKAGE)
            if (profile == SwitchModProfile.HANDHELD_720 && staged.none { it.relativePath == UltraCamHandheldProfile.CONFIG_PATH }) {
                throw ModInstallException(ModInstallFailure.UNSUPPORTED_PROFILE)
            }
            StagedSwitchMod(title, archiveDigest, staged.toList(), skippedSettings)
        }
    }

    internal fun canonicalPath(name: String, title: String, directory: Boolean = false): String? {
        val clean = if (directory) name.removeSuffix("/") else name
        val parts = clean.split('/')
        if (clean.toByteArray(Charsets.UTF_8).size > 1024 || parts.any { part ->
                part.isEmpty() || part == "." || part == ".." || part.toByteArray(Charsets.UTF_8).size > 255 ||
                    part.lastOrNull() in listOf(' ', '.') || part.any { Character.isISOControl(it) || it in "\\:<>|?*\"" }
            }) throw ModInstallException(ModInstallFailure.UNSAFE_PATH, name)
        if (parts.any { it == "__MACOSX" || it == ".DS_Store" || it.startsWith("._") }) return null
        if (parts.size == 1 && clean.lowercase(Locale.ROOT) in setOf("readme", "readme.txt", "readme.md", "license", "license.txt", "license.md")) return null
        if (parts.first() == "load") {
            if (parts.size > 1 && !parts[1].equals(title, ignoreCase = true)) {
                throw ModInstallException(ModInstallFailure.WRONG_TITLE, name)
            }
            if (directory && parts.size <= 3) return null
            if (parts.size >= 4 && parts[3] in setOf("romfs", "exefs", "cheats") && (directory || parts.size >= 5)) {
                return (parts.take(1) + title + parts.drop(2)).joinToString("/")
            }
        }
        if (parts.first() == "config") {
            if (directory && parts in listOf(listOf("config"), listOf("config", "custom"))) return null
            if (parts.size == 3 && parts[1] == "custom" && parts[2].equals("$title.ini", ignoreCase = true)) return null
        }
        if (parts.first() == "sdmc") {
            val game = ultraCamTitles[title]
            val prefix = listOf("sdmc", "UltraCam", game, "Config")
            if (directory && parts.size <= 4 && parts == prefix.take(parts.size)) return null
            if (!directory && parts.size == 5 && parts.take(4) == prefix && parts[4].substringAfterLast('.').lowercase(Locale.ROOT) in setOf("ini", "json")) {
                return clean
            }
        }
        throw ModInstallException(ModInstallFailure.UNSUPPORTED_PATH, name)
    }
}
