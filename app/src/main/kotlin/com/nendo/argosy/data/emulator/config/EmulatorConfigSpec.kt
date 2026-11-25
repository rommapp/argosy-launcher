package com.nendo.argosy.data.emulator.config

/**
 * How a config file stores its keys. [PREFS_JSON] is an Android SharedPreferences XML file whose
 * string values hold JSON objects; the preference name is the section of each JSON key.
 */
enum class ConfigFormat { INI, XML, YAML, JSON, PREFS_JSON }

enum class ConfigFileRole { PRIMARY, SETTINGS, PLAY_TIME }

enum class ConfigValueType { BOOL, INT, FLOAT, ENUM, STRING }

/**
 * Where a config file's relative paths start: the emulator's data root on shared storage
 * ([DATA_ROOT]), or its private `/data/data/<package>` folder, reachable through root alone
 * ([PACKAGE_DATA]).
 */
enum class ConfigFileLocation { DATA_ROOT, PACKAGE_DATA }

/**
 * A readable copy of a file's values: the entries of [file] under [section] stand in for the
 * owning file's entries when that file cannot be read.
 */
data class ConfigFallback(val file: ConfigFileSpec, val section: String)

/**
 * One config file of an emulator. [relativePaths] are tried in order under [location] and the
 * first one present is the file; the first entry also identifies the file.
 */
data class ConfigFileSpec(
    val relativePaths: List<String>,
    val format: ConfigFormat,
    val role: ConfigFileRole,
    val location: ConfigFileLocation = ConfigFileLocation.DATA_ROOT,
    val fallback: ConfigFallback? = null
) {
    val id: String get() = relativePaths.first()
    val isPrimary: Boolean get() = role == ConfigFileRole.PRIMARY
}

internal fun ConfigFileSpec.baseDir(dataRoot: String, packageName: String): String = when (location) {
    ConfigFileLocation.DATA_ROOT -> dataRoot.trimEnd('/')
    ConfigFileLocation.PACKAGE_DATA -> "/data/data/$packageName"
}

/**
 * A value an option takes: [token] as the emulator stores it, [name] as the emulator labels it.
 */
data class ConfigValue(val token: String, val name: String)

/**
 * The numbers an INT or FLOAT option accepts, both ends included, and the stride an edit moves by.
 */
data class ConfigRange(val min: Double, val max: Double, val step: Double)

/**
 * One known option. [section] is the INI section, XML element path or JSON path, empty at top
 * level; [defaultToken] is the value used when the key is absent. [range] bounds an editable
 * number; [driverDir] marks a GPU driver key and names the data-root folder its value points into.
 */
data class ConfigOption(
    val file: ConfigFileSpec,
    val section: String,
    val key: String,
    val type: ConfigValueType,
    val defaultToken: String?,
    val values: List<ConfigValue> = emptyList(),
    val editable: Boolean = false,
    val range: ConfigRange? = null,
    val driverDir: String? = null
)

/**
 * Keys whose values are never shown or logged. A null [section] or [key] matches any; a section
 * also matches every section nested below it.
 */
data class ConfigKeyMatcher(val file: ConfigFileSpec, val section: String?, val key: String?)

/**
 * What Argosy knows about one emulator's config files.
 *
 * [compiledDefaultFlag] is the suffix of the companion key that says whether a stored value is
 * used: a companion that is absent or `true` means the emulator runs its compiled default and
 * ignores the stored value. [keysIgnoreCase] matches sections and keys without regard to case.
 */
data class EmulatorConfigSpec(
    val files: List<ConfigFileSpec>,
    val options: List<ConfigOption>,
    val secrets: List<ConfigKeyMatcher>,
    val compiledDefaultFlag: String? = null,
    val keysIgnoreCase: Boolean = false
) {
    val primaryFile: ConfigFileSpec get() = files.first { it.isPrimary }

    fun isSecret(file: ConfigFileSpec, section: String, key: String): Boolean =
        secrets.any { matcher ->
            matcher.file == file &&
                (matcher.section == null || sameSection(section, matcher.section)) &&
                (matcher.key == null || matcher.key.equals(key, ignoreCase = keysIgnoreCase))
        }

    fun isSecret(option: ConfigOption): Boolean = isSecret(option.file, option.section, option.key)

    private fun sameSection(section: String, wanted: String): Boolean =
        section.equals(wanted, ignoreCase = keysIgnoreCase) ||
            section.startsWith("$wanted/", ignoreCase = keysIgnoreCase)
}

internal fun range(min: Double, max: Double, step: Double = 1.0): ConfigRange = ConfigRange(min, max, step)

internal fun ordinals(vararg names: String): List<ConfigValue> =
    names.mapIndexed { index, name -> ConfigValue(index.toString(), name) }

internal fun tokens(vararg tokens: String): List<ConfigValue> =
    tokens.map { ConfigValue(it, it) }

internal fun named(vararg values: Pair<String, String>): List<ConfigValue> =
    values.map { (token, name) -> ConfigValue(token, name) }
