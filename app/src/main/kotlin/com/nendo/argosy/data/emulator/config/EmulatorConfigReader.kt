package com.nendo.argosy.data.emulator.config

import android.content.Context
import com.nendo.argosy.data.storage.FileAccessLayer
import com.nendo.argosy.util.RootShell
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

enum class ConfigFileState { READ, ABSENT, BLOCKED }

/**
 * One config file as found. [path] is absolute under the data root, or the spec's relative path
 * when there is no data root. [modifiedAt] is set only for a [ConfigFileState.READ] file.
 */
data class ConfigFileSnapshot(
    val file: ConfigFileSpec,
    val path: String,
    val state: ConfigFileState,
    val modifiedAt: Long?
)

/**
 * One known option as stored. [raw] is null when the key is absent, hidden, or its file was not
 * read. [valueName] is the emulator's name for the effective value, null when the option has no
 * named values or the stored token is unknown. [isDefault] is false for a hidden option, an
 * unknown token, and an option whose file is [ConfigFileState.BLOCKED] or not well-formed.
 */
data class ConfigOptionSnapshot(
    val option: ConfigOption,
    val raw: String?,
    val valueName: String?,
    val isDefault: Boolean,
    val hidden: Boolean
)

/**
 * A stored key the registry has no option for. [value] is null when [hidden].
 */
data class ConfigEntrySnapshot(
    val file: ConfigFileSpec,
    val section: String,
    val key: String,
    val value: String?,
    val hidden: Boolean
)

data class EmulatorConfigSnapshot(
    val access: EmulatorDataCapabilities,
    val files: List<ConfigFileSnapshot>,
    val options: List<ConfigOptionSnapshot>,
    val otherKeys: List<ConfigEntrySnapshot>,
    val playTimes: Map<String, Long>
)

@Singleton
class EmulatorConfigReader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val access: EmulatorDataAccess,
    private val fal: FileAccessLayer
) {
    /**
     * Everything Argosy can read of [emulatorId]'s config, or null when the registry does not
     * know the emulator.
     */
    suspend fun snapshot(emulatorId: String, packageName: String): EmulatorConfigSnapshot? =
        withContext(Dispatchers.IO) {
            val spec = EmulatorConfigRegistry.specFor(emulatorId) ?: return@withContext null
            val capabilities = access.capabilities(emulatorId, packageName)
            read(spec, capabilities, packageName, ::readThroughRoot.takeIf { capabilities.canEdit })
        }

    internal fun read(
        spec: EmulatorConfigSpec,
        capabilities: EmulatorDataCapabilities,
        packageName: String = "",
        privateRead: ((String) -> ConfigRead)? = null
    ): EmulatorConfigSnapshot {
        val found = spec.files.map { file ->
            when (file.location) {
                ConfigFileLocation.DATA_ROOT -> locate(file, capabilities.dataRoot)
                ConfigFileLocation.PACKAGE_DATA -> locatePrivate(file, packageName, privateRead)
            }
        }
        val entriesByFile = found.associate { (snapshot, text) ->
            snapshot.file to when (snapshot.state) {
                ConfigFileState.ABSENT -> emptyList<ConfigEntry>()
                ConfigFileState.BLOCKED -> null
                ConfigFileState.READ -> text?.let { ConfigFileReaders.read(snapshot.file.format, it) }
            }
        }
        val options = spec.options.map { option ->
            val entries = entriesByFile[option.file] ?: option.file.fallback?.let { fallback ->
                entriesByFile[fallback.file]
                    ?.filter { it.section == fallback.section }
                    ?.map { it.copy(section = option.section) }
            }
            ConfigOptionDecoder.decode(spec, option, entries)
        }
        val playTimes = found
            .filter { (snapshot, _) -> snapshot.file.role == ConfigFileRole.PLAY_TIME }
            .mapNotNull { (_, text) -> text?.let(DolphinTimePlayed::decode) }
            .fold(emptyMap<String, Long>()) { acc, map -> acc + map }
        return EmulatorConfigSnapshot(
            access = capabilities,
            files = found.map { it.first },
            options = options,
            otherKeys = otherKeys(spec, entriesByFile),
            playTimes = playTimes
        )
    }

    private fun locate(file: ConfigFileSpec, root: String?): Pair<ConfigFileSnapshot, String?> {
        if (root == null) return ConfigFileSnapshot(file, file.id, ConfigFileState.BLOCKED, null) to null
        val paths = file.relativePaths.map { "$root/$it" }
        val present = paths.firstOrNull { fal.exists(it) }
            ?: return ConfigFileSnapshot(file, paths.first(), absentOrBlocked(paths.first(), root), null) to null
        if (!fal.canRead(present)) return ConfigFileSnapshot(file, present, ConfigFileState.BLOCKED, null) to null
        val text = fal.readBytes(present)?.toString(Charsets.UTF_8)
            ?: return ConfigFileSnapshot(file, present, ConfigFileState.BLOCKED, null) to null
        return ConfigFileSnapshot(file, present, ConfigFileState.READ, fal.lastModified(present)) to text
    }

    private fun locatePrivate(
        file: ConfigFileSpec,
        packageName: String,
        privateRead: ((String) -> ConfigRead)?
    ): Pair<ConfigFileSnapshot, String?> {
        val paths = file.relativePaths.map { "${file.baseDir("", packageName)}/$it" }
        val read = privateRead ?: return ConfigFileSnapshot(file, paths.first(), ConfigFileState.BLOCKED, null) to null
        for (path in paths) {
            when (val result = read(path)) {
                is ConfigRead.Found -> {
                    return ConfigFileSnapshot(file, path, ConfigFileState.READ, null) to result.bytes.toString(Charsets.UTF_8)
                }
                ConfigRead.Missing -> continue
                ConfigRead.Unreadable -> return ConfigFileSnapshot(file, path, ConfigFileState.BLOCKED, null) to null
            }
        }
        return ConfigFileSnapshot(file, paths.first(), ConfigFileState.ABSENT, null) to null
    }

    private fun readThroughRoot(path: String): ConfigRead =
        ConfigRootScripts.read(
            runner = { script -> RootShell.run(context, script) },
            path = path,
            workDir = File(context.cacheDir, ConfigRootScripts.WORK_DIR)
        )

    private fun absentOrBlocked(path: String, root: String): ConfigFileState {
        val packageDir = root.substringBeforeLast('/')
        var dir = path.substringBeforeLast('/')
        while (dir.length >= packageDir.length) {
            if (fal.isListable(dir)) return ConfigFileState.ABSENT
            if (fal.exists(dir)) return ConfigFileState.BLOCKED
            dir = dir.substringBeforeLast('/')
        }
        return ConfigFileState.BLOCKED
    }

    private fun otherKeys(
        spec: EmulatorConfigSpec,
        entriesByFile: Map<ConfigFileSpec, List<ConfigEntry>?>
    ): List<ConfigEntrySnapshot> =
        spec.files.filter { it.role != ConfigFileRole.PLAY_TIME }.flatMap { file ->
            val known = spec.options.mapNotNull { option ->
                when {
                    option.file == file -> ConfigOptionDecoder.lookupKey(spec, option.section, option.key)
                    option.file.fallback?.file == file ->
                        ConfigOptionDecoder.lookupKey(spec, option.file.fallback.section, option.key)
                    else -> null
                }
            }.toSet()
            entriesByFile[file].orEmpty()
                .filter { spec.compiledDefaultFlag == null || !it.key.endsWith(spec.compiledDefaultFlag) }
                .filter { ConfigOptionDecoder.lookupKey(spec, it.section, it.key) !in known }
                .map { entry ->
                    val hidden = spec.isSecret(file, entry.section, entry.key)
                    ConfigEntrySnapshot(file, entry.section, entry.key, entry.value.takeUnless { hidden }, hidden)
                }
        }
}

internal object ConfigOptionDecoder {
    private const val COMPILED_DEFAULT_OFF = "false"
    private val TRUE_TOKENS = setOf("true", "1")
    private val FALSE_TOKENS = setOf("false", "0")

    fun lookupKey(spec: EmulatorConfigSpec, section: String, key: String): Pair<String, String> =
        if (spec.keysIgnoreCase) section.lowercase() to key.lowercase() else section to key

    /**
     * [option] as [entries] store it. Null [entries] is a file whose content is unknown; an
     * absent file is an empty list.
     */
    fun decode(spec: EmulatorConfigSpec, option: ConfigOption, entries: List<ConfigEntry>?): ConfigOptionSnapshot {
        if (spec.isSecret(option)) return ConfigOptionSnapshot(option, null, null, isDefault = false, hidden = true)
        entries ?: return ConfigOptionSnapshot(option, null, null, isDefault = false, hidden = false)
        val stored = entries.associate { lookupKey(spec, it.section, it.key) to it.value }
        val raw = stored[lookupKey(spec, option.section, option.key)]
        if (raw == null) {
            return ConfigOptionSnapshot(option, null, option.defaultToken?.let { nameOf(option, it) }, isDefault = true, hidden = false)
        }
        val name = nameOf(option, raw)
        val unknown = option.values.isNotEmpty() && name == null && option.type == ConfigValueType.ENUM
        val compiledDefault = spec.compiledDefaultFlag?.let { flag ->
            stored[lookupKey(spec, option.section, option.key + flag)]?.trim() != COMPILED_DEFAULT_OFF
        } == true
        val isDefault = !unknown && (compiledDefault || option.defaultToken?.let { same(option.type, raw, it) } == true)
        return ConfigOptionSnapshot(option, raw, name, isDefault, hidden = false)
    }

    private fun nameOf(option: ConfigOption, token: String): String? =
        option.values.firstOrNull { same(option.type, token, it.token) }?.name

    /**
     * Whether [a] and [b] are the same value of [type]: booleans across `true`/`1` spellings and
     * case, numbers by value, everything else by exact text.
     */
    fun same(type: ConfigValueType, a: String, b: String): Boolean {
        val left = a.trim()
        val right = b.trim()
        return when (type) {
            ConfigValueType.BOOL -> bool(left)?.let { it == bool(right) } ?: (left == right)
            ConfigValueType.INT -> left.toLongOrNull()?.let { it == right.toLongOrNull() } ?: (left == right)
            ConfigValueType.FLOAT -> left.toDoubleOrNull()?.let { it == right.toDoubleOrNull() } ?: (left == right)
            ConfigValueType.ENUM -> left.toLongOrNull()?.let { it == right.toLongOrNull() } ?: (left == right)
            ConfigValueType.STRING -> left == right
        }
    }

    private fun bool(value: String): Boolean? = when (value.lowercase()) {
        in TRUE_TOKENS -> true
        in FALSE_TOKENS -> false
        else -> null
    }
}
