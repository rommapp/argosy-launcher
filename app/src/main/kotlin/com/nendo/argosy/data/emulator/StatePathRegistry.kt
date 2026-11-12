package com.nendo.argosy.data.emulator

import android.os.Environment
import com.nendo.argosy.data.platform.PlatformDefinitions
import com.nendo.argosy.libretro.LibretroStateSlots
import com.nendo.argosy.util.AppPaths
import java.io.File

/**
 * What a state file name is matched against: the rom stem for rom-named emulators, the disc
 * serials for serial-keyed ones. An empty [serials] set makes a serial-keyed pattern match nothing.
 */
data class StateNameKey(
    val romBaseName: String,
    val serials: Set<String>
)

private const val AUTO_SLOT = -1

private fun pairedPng(stateFile: File): File = File("${stateFile.absolutePath}.png")

sealed class StateSlotPattern {
    abstract val extension: String
    abstract fun parseSlotNumber(fileName: String, key: StateNameKey): Int?

    /**
     * The live file name a state for [slotNumber] is written to, or null when no name can be
     * proven to belong to the game. [cachedFileName] is the name the state was cached under.
     */
    abstract fun targetFileName(key: StateNameKey, slotNumber: Int, cachedFileName: String?): String?

    /**
     * The live file name a cached state is put back under when a channel's states are restored
     * as a set, or null when it cannot be placed without touching another game's file.
     */
    abstract fun restoreFileName(key: StateNameKey, slotNumber: Int, cachedFileName: String): String?

    abstract fun resumeFileNames(key: StateNameKey): List<String>

    open fun screenshotFile(stateFile: File): File = pairedPng(stateFile)

    sealed class RomNamed : StateSlotPattern() {
        protected abstract fun parseSlotNumber(fileName: String, baseName: String): Int?
        protected abstract fun buildFileName(baseName: String, slotNumber: Int): String

        final override fun parseSlotNumber(fileName: String, key: StateNameKey): Int? =
            parseSlotNumber(fileName, key.romBaseName)

        final override fun targetFileName(key: StateNameKey, slotNumber: Int, cachedFileName: String?): String =
            buildFileName(key.romBaseName, slotNumber)

        final override fun restoreFileName(key: StateNameKey, slotNumber: Int, cachedFileName: String): String =
            cachedFileName

        final override fun resumeFileNames(key: StateNameKey): List<String> = listOf(
            buildFileName(key.romBaseName, AUTO_SLOT),
            LibretroStateSlots.fileName(key.romBaseName, LibretroStateSlots.RESUME_SLOT)
        )
    }

    /**
     * Emulators that keep one state folder for every game and name each state after the disc
     * serial. A name is the game's only when its serial is one of [StateNameKey.serials].
     */
    sealed class SerialKeyed : StateSlotPattern() {
        abstract fun serialOf(fileName: String): String?
        protected abstract fun slotOf(fileName: String): Int?
        abstract fun withSlot(fileName: String, slotNumber: Int): String?
        protected abstract fun fromSerial(serial: String, slotNumber: Int): String?

        fun ownsSerial(fileName: String, serials: Set<String>): Boolean {
            val serial = serialOf(fileName) ?: return false
            return serials.any { it.equals(serial, ignoreCase = true) }
        }

        final override fun parseSlotNumber(fileName: String, key: StateNameKey): Int? =
            if (ownsSerial(fileName, key.serials)) slotOf(fileName) else null

        final override fun targetFileName(key: StateNameKey, slotNumber: Int, cachedFileName: String?): String? {
            if (cachedFileName != null && serialOf(cachedFileName) != null) {
                return if (ownsSerial(cachedFileName, key.serials)) withSlot(cachedFileName, slotNumber) else null
            }
            val serial = key.serials.singleOrNull() ?: return null
            return fromSerial(serial, slotNumber)
        }

        final override fun restoreFileName(key: StateNameKey, slotNumber: Int, cachedFileName: String): String? =
            targetFileName(key, slotNumber, cachedFileName)

        final override fun resumeFileNames(key: StateNameKey): List<String> =
            key.serials.mapNotNull { fromSerial(it, AUTO_SLOT) }
    }

    /**
     * DuckStation `src/core/system.cpp` `GetGameSaveStatePath`: `<serial>_<slot>.sav` and
     * `<serial>_resume.sav`, the resume state carried as the auto slot. Global states
     * (`savestate_<n>.sav`, `resume.sav`) carry no game serial.
     */
    data object DuckStationSerial : SerialKeyed() {
        private const val RESUME_TOKEN = "resume"
        private val NAME = Regex("""^(.+)_(\d+|resume)\.sav$""", RegexOption.IGNORE_CASE)

        override val extension: String = "sav"

        override fun serialOf(fileName: String): String? = NAME.matchEntire(fileName)?.groupValues?.get(1)

        override fun slotOf(fileName: String): Int? {
            val token = NAME.matchEntire(fileName)?.groupValues?.get(2) ?: return null
            return if (token.equals(RESUME_TOKEN, ignoreCase = true)) AUTO_SLOT else token.toIntOrNull()
        }

        override fun withSlot(fileName: String, slotNumber: Int): String? =
            serialOf(fileName)?.let { fromSerial(it, slotNumber) }

        override fun fromSerial(serial: String, slotNumber: Int): String? = when {
            slotNumber == AUTO_SLOT -> "${serial}_$RESUME_TOKEN.$extension"
            slotNumber >= 0 -> "${serial}_$slotNumber.$extension"
            else -> null
        }
    }

    /**
     * PPSSPP `Core/SaveState.cpp`: `<DISC_ID>_<DISC_VERSION>_<slot>.ppst` with a `.jpg` screenshot
     * of the same stem. Undo states, slot names and `load_undo.ppst` never match. A target name
     * keeps the version of the name the state was cached under; a cached name without one has no
     * target.
     */
    data object PpssppSerial : SerialKeyed() {
        private const val SCREENSHOT_EXTENSION = "jpg"
        private val NAME = Regex("""^([^_]+)_([^_]+)_(\d+)\.ppst$""", RegexOption.IGNORE_CASE)

        override val extension: String = "ppst"

        override fun serialOf(fileName: String): String? = NAME.matchEntire(fileName)?.groupValues?.get(1)

        override fun slotOf(fileName: String): Int? =
            NAME.matchEntire(fileName)?.groupValues?.get(3)?.toIntOrNull()

        override fun withSlot(fileName: String, slotNumber: Int): String? {
            if (slotNumber < 0) return null
            val match = NAME.matchEntire(fileName) ?: return null
            return "${match.groupValues[1]}_${match.groupValues[2]}_$slotNumber.$extension"
        }

        override fun fromSerial(serial: String, slotNumber: Int): String? = null

        override fun screenshotFile(stateFile: File): File =
            File(stateFile.parentFile, "${stateFile.nameWithoutExtension}.$SCREENSHOT_EXTENSION")
    }

    data class SuffixNumber(
        override val extension: String,
        val autoSlotSuffix: String? = null
    ) : RomNamed() {
        override fun parseSlotNumber(fileName: String, baseName: String): Int? {
            if (!fileName.startsWith(baseName, ignoreCase = true)) return null

            val suffix = fileName.substring(baseName.length)

            return when {
                suffix.equals(".$extension", ignoreCase = true) -> 0
                suffix.matches(Regex("\\.$extension(\\d+)", RegexOption.IGNORE_CASE)) -> {
                    Regex("\\.$extension(\\d+)", RegexOption.IGNORE_CASE)
                        .find(suffix)?.groupValues?.get(1)?.toIntOrNull()
                }
                autoSlotSuffix != null && suffix.equals(".$extension.$autoSlotSuffix", ignoreCase = true) -> -1
                else -> null
            }
        }

        override fun buildFileName(baseName: String, slotNumber: Int): String {
            return when (slotNumber) {
                0 -> "$baseName.$extension"
                -1 -> if (autoSlotSuffix != null) "$baseName.$extension.$autoSlotSuffix" else "$baseName.$extension"
                else -> "$baseName.$extension$slotNumber"
            }
        }
    }

    /**
     * Built-in libretro flat slots. Delegates slot <-> filename to [LibretroStateSlots] -- the
     * single source of truth shared with the live save-state engine (SaveStateManager) -- so the
     * write codec ([LibretroStateSlots.fileName]) and read codec ([LibretroStateSlots.parseSlotNumber])
     * can never drift apart.
     */
    data object BuiltinLibretro : RomNamed() {
        override val extension: String = "state"

        override fun parseSlotNumber(fileName: String, baseName: String): Int? =
            LibretroStateSlots.parseSlotNumber(baseName, fileName)

        override fun buildFileName(baseName: String, slotNumber: Int): String =
            LibretroStateSlots.fileName(baseName, slotNumber)
    }

    data class NameAndSlot(
        val separator: String,
        override val extension: String
    ) : RomNamed() {
        override fun parseSlotNumber(fileName: String, baseName: String): Int? {
            val name = File(fileName).nameWithoutExtension
            val ext = File(fileName).extension
            if (!ext.equals(extension, ignoreCase = true)) return null

            val pattern = Regex("${Regex.escape(baseName)}${Regex.escape(separator)}(\\d+)", RegexOption.IGNORE_CASE)
            return pattern.find(name)?.groupValues?.get(1)?.toIntOrNull()
        }

        override fun buildFileName(baseName: String, slotNumber: Int): String {
            return "$baseName$separator$slotNumber.$extension"
        }
    }
}

data class StatePathConfig(
    val emulatorId: String,
    val defaultPaths: List<String>,
    val slotPattern: StateSlotPattern,
    val usesCore: Boolean = false,
    val maxSlots: Int = 10,
    val supported: Boolean = true
)

object StatePathRegistry {

    private val configs = mapOf(
        EmulatorRegistry.BUILTIN_ID to StatePathConfig(
            emulatorId = EmulatorRegistry.BUILTIN_ID,
            defaultPaths = listOf("{filesDir}/${AppPaths.LIBRETRO_STATES_SUBDIR}"),
            slotPattern = StateSlotPattern.BuiltinLibretro,
            maxSlots = 10
        ),
        "retroarch" to StatePathConfig(
            emulatorId = "retroarch",
            defaultPaths = listOf(
                "{extStorage}/RetroArch/states/{core}",
                "{extStorage}/Android/data/com.retroarch/files/states/{core}",
                "/data/data/com.retroarch/states/{core}",
                "{extStorage}/RetroArch/states",
                "{extStorage}/Android/data/com.retroarch/files/states",
                "/data/data/com.retroarch/states"
            ),
            slotPattern = StateSlotPattern.SuffixNumber(
                extension = "state",
                autoSlotSuffix = "auto"
            ),
            usesCore = true,
            maxSlots = 10
        ),
        "retroarch_64" to StatePathConfig(
            emulatorId = "retroarch_64",
            defaultPaths = listOf(
                "{extStorage}/RetroArch/states/{core}",
                "{extStorage}/Android/data/com.retroarch.aarch64/files/states/{core}",
                "/data/data/com.retroarch.aarch64/states/{core}"
            ),
            slotPattern = StateSlotPattern.SuffixNumber(
                extension = "state",
                autoSlotSuffix = "auto"
            ),
            usesCore = true,
            maxSlots = 10
        ),
        "retroarch_32" to StatePathConfig(
            emulatorId = "retroarch_32",
            defaultPaths = listOf(
                "{extStorage}/RetroArch/states/{core}",
                "{extStorage}/Android/data/com.retroarch.ra32/files/states/{core}",
                "/data/data/com.retroarch.ra32/states/{core}"
            ),
            slotPattern = StateSlotPattern.SuffixNumber(
                extension = "state",
                autoSlotSuffix = "auto"
            ),
            usesCore = true,
            maxSlots = 10
        ),

        "ppsspp" to StatePathConfig(
            emulatorId = "ppsspp",
            defaultPaths = listOf(
                "{extStorage}/PSP/PPSSPP_STATE",
                "{extStorage}/Android/data/org.ppsspp.ppsspp/files/PSP/PPSSPP_STATE"
            ),
            slotPattern = StateSlotPattern.PpssppSerial,
            maxSlots = 5
        ),
        "ppsspp_gold" to StatePathConfig(
            emulatorId = "ppsspp_gold",
            defaultPaths = listOf(
                "{extStorage}/PSP/PPSSPP_STATE",
                "{extStorage}/Android/data/org.ppsspp.ppssppgold/files/PSP/PPSSPP_STATE"
            ),
            slotPattern = StateSlotPattern.PpssppSerial,
            maxSlots = 5
        ),
        "ppsspp_legacy" to StatePathConfig(
            emulatorId = "ppsspp_legacy",
            defaultPaths = listOf(
                "{extStorage}/PSP/PPSSPP_STATE",
                "{extStorage}/Android/data/org.ppsspp.ppsspplegacy/files/PSP/PPSSPP_STATE"
            ),
            slotPattern = StateSlotPattern.PpssppSerial,
            maxSlots = 5
        ),

        "drastic" to StatePathConfig(
            emulatorId = "drastic",
            defaultPaths = listOf(
                "{extStorage}/DraStic/savestates",
                "{extStorage}/Android/data/com.dsemu.drastic/files/savestates"
            ),
            slotPattern = StateSlotPattern.NameAndSlot(
                separator = "_",
                extension = "dss"
            ),
            maxSlots = -1
        ),

        "melonds" to StatePathConfig(
            emulatorId = "melonds",
            defaultPaths = listOf(
                "{extStorage}/melonDS/states",
                "{extStorage}/Android/data/me.magnum.melonds/files/states"
            ),
            slotPattern = StateSlotPattern.NameAndSlot(
                separator = ".",
                extension = "mln"
            ),
            maxSlots = 8
        ),
        "melondualds" to StatePathConfig(
            emulatorId = "melondualds",
            defaultPaths = listOf(
                "{extStorage}/melonDS/states",
                "{extStorage}/Android/data/me.magnum.melondualds/files/states"
            ),
            slotPattern = StateSlotPattern.NameAndSlot(
                separator = ".",
                extension = "mln"
            ),
            maxSlots = 8
        ),

        "duckstation" to StatePathConfig(
            emulatorId = "duckstation",
            defaultPaths = listOf(
                "{extStorage}/duckstation/savestates",
                "{extStorage}/Android/data/com.github.stenzek.duckstation/files/savestates"
            ),
            slotPattern = StateSlotPattern.DuckStationSerial,
            maxSlots = 10
        ),

        "dolphin" to StatePathConfig(
            emulatorId = "dolphin",
            defaultPaths = listOf(
                "{extStorage}/Android/data/org.dolphinemu.dolphinemu/files/StateSaves",
                "{extStorage}/Android/data/com.joeyos.dolphinemu/files/StateSaves",
                "{extStorage}/dolphin-emu/StateSaves"
            ),
            slotPattern = StateSlotPattern.NameAndSlot(
                separator = ".s",
                extension = ""
            ),
            maxSlots = 10,
            supported = false
        ),

        "mupen64plus_fz" to StatePathConfig(
            emulatorId = "mupen64plus_fz",
            defaultPaths = listOf(
                "{extStorage}/Android/data/org.mupen64plusae.v3.fzurita/files/GameData"
            ),
            slotPattern = StateSlotPattern.SuffixNumber(extension = "st"),
            maxSlots = 10,
            supported = false
        ),
        "mupen64plus_fz_pro" to StatePathConfig(
            emulatorId = "mupen64plus_fz_pro",
            defaultPaths = listOf(
                "{extStorage}/Android/data/org.mupen64plusae.v3.fzurita.pro/files/GameData"
            ),
            slotPattern = StateSlotPattern.SuffixNumber(extension = "st"),
            maxSlots = 10,
            supported = false
        ),
        "m64pro_fzx_plus" to StatePathConfig(
            emulatorId = "m64pro_fzx_plus",
            defaultPaths = listOf(
                "{extStorage}/Android/data/com.m64.fx.plus.emulate/files/GameData"
            ),
            slotPattern = StateSlotPattern.SuffixNumber(extension = "st"),
            maxSlots = 10,
            supported = false
        ),

        "pizza_boy_gba" to StatePathConfig(
            emulatorId = "pizza_boy_gba",
            defaultPaths = listOf(
                "{extStorage}/PizzaBoyGBA/states",
                "{extStorage}/Android/data/it.dbtecno.pizzaboygba/files/states"
            ),
            slotPattern = StateSlotPattern.NameAndSlot(
                separator = "_",
                extension = "state"
            ),
            maxSlots = 10
        ),
        "pizza_boy_gb" to StatePathConfig(
            emulatorId = "pizza_boy_gb",
            defaultPaths = listOf(
                "{extStorage}/PizzaBoy/states",
                "{extStorage}/Android/data/it.dbtecno.pizzaboy/files/states"
            ),
            slotPattern = StateSlotPattern.NameAndSlot(
                separator = "_",
                extension = "state"
            ),
            maxSlots = 10
        ),
        "pizza_boy_gba_pro" to StatePathConfig(
            emulatorId = "pizza_boy_gba_pro",
            defaultPaths = listOf(
                "{extStorage}/PizzaBoyGBA/states",
                "{extStorage}/Android/data/it.dbtecno.pizzaboygbapro/files/states"
            ),
            slotPattern = StateSlotPattern.NameAndSlot(
                separator = "_",
                extension = "state"
            ),
            maxSlots = 10
        ),
        "pizza_boy_gb_pro" to StatePathConfig(
            emulatorId = "pizza_boy_gb_pro",
            defaultPaths = listOf(
                "{extStorage}/PizzaBoy/states",
                "{extStorage}/Android/data/it.dbtecno.pizzaboypro/files/states"
            ),
            slotPattern = StateSlotPattern.NameAndSlot(
                separator = "_",
                extension = "state"
            ),
            maxSlots = 10
        )
    )

    fun getConfig(emulatorId: String): StatePathConfig? {
        val config = configs[emulatorId] ?: familyFallbackConfig(emulatorId) ?: return null
        return if (config.supported) config else null
    }

    fun getConfigIncludingUnsupported(emulatorId: String): StatePathConfig? =
        configs[emulatorId] ?: familyFallbackConfig(emulatorId)

    /**
     * Family-variant ids are `<baseId>_<package>` (EmulatorRegistry.createDefFromFamily);
     * recover the base config so fork installs resolve state paths.
     */
    private fun familyFallbackConfig(emulatorId: String): StatePathConfig? =
        EmulatorRegistry.getEmulatorFamilies()
            .filter { emulatorId.length > it.baseId.length + 1 && emulatorId.startsWith(it.baseId + "_") }
            .maxByOrNull { it.baseId.length }
            ?.let { configs[it.baseId] }

    fun getAllConfigs(): Map<String, StatePathConfig> = configs.filterValues { it.supported }

    fun getRetroArchCore(platformId: String): String? {
        val canonical = PlatformDefinitions.getCanonicalSlug(platformId)
        return EmulatorRegistry.getRetroArchCorePatterns()[canonical]?.firstOrNull()
    }

    @Suppress("DEPRECATION")
    private fun getExternalStoragePath(): String {
        return Environment.getExternalStorageDirectory().absolutePath
    }

    private fun expandPath(path: String, core: String? = null): String {
        var result = path.replace("{extStorage}", getExternalStoragePath())
        if (core != null) {
            result = result.replace("{core}", core)
        }
        return result
    }

    fun resolvePath(
        config: StatePathConfig,
        platformId: String
    ): List<String> {
        val canonical = PlatformDefinitions.getCanonicalSlug(platformId)
        val core = if (config.usesCore) {
            getRetroArchCore(canonical)?.let { EmulatorRegistry.getRetroArchSaveDirName(it) }
        } else {
            null
        }

        val paths = config.defaultPaths.map { path ->
            expandPath(path, core)
        }.filterNot { UNRESOLVED_TOKEN.containsMatchIn(it) }

        if (core != null) {
            val withoutCore = config.defaultPaths.map { path ->
                expandPath(path.replace("/{core}", "").replace("{core}", ""))
            }.filterNot { UNRESOLVED_TOKEN.containsMatchIn(it) }
            return paths + withoutCore
        }

        return paths
    }

    fun serialPatternFor(emulatorId: String): StateSlotPattern.SerialKeyed? =
        getConfigIncludingUnsupported(emulatorId)?.slotPattern as? StateSlotPattern.SerialKeyed

    fun screenshotFileFor(emulatorId: String, stateFile: File): File =
        getConfigIncludingUnsupported(emulatorId)?.slotPattern?.screenshotFile(stateFile) ?: pairedPng(stateFile)

    fun discoverStates(
        stateDir: File,
        key: StateNameKey,
        pattern: StateSlotPattern
    ): List<Pair<File, Int>> {
        if (!stateDir.exists() || !stateDir.isDirectory) return emptyList()

        return stateDir.listFiles()
            ?.filter { it.isFile }
            ?.mapNotNull { file ->
                val slotNumber = pattern.parseSlotNumber(file.name, key)
                if (slotNumber != null) file to slotNumber else null
            }
            ?.sortedBy { it.second }
            ?: emptyList()
    }
}
