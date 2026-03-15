package com.nendo.argosy.data.emulator

import android.os.Environment
import com.nendo.argosy.data.platform.PlatformDefinitions
import java.io.File

sealed class StateSlotPattern {
    abstract fun parseSlotNumber(fileName: String, baseName: String): Int?
    abstract fun buildFileName(baseName: String, slotNumber: Int): String
    abstract val extension: String

    data class SuffixNumber(
        override val extension: String,
        val autoSlotSuffix: String? = null
    ) : StateSlotPattern() {
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

    data class NameAndSlot(
        val separator: String,
        override val extension: String
    ) : StateSlotPattern() {
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

    data class SerialAndSlot(
        val separator: String,
        override val extension: String
    ) : StateSlotPattern() {
        override fun parseSlotNumber(fileName: String, baseName: String): Int? {
            val name = File(fileName).nameWithoutExtension
            val ext = File(fileName).extension
            if (!ext.equals(extension, ignoreCase = true)) return null

            val pattern = Regex(".*${Regex.escape(separator)}(\\d+)$")
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
        "builtin" to StatePathConfig(
            emulatorId = "builtin",
            defaultPaths = listOf("{appInternal}/libretro/states"),
            slotPattern = StateSlotPattern.SuffixNumber(
                extension = "state",
                autoSlotSuffix = "auto"
            ),
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

        "ppsspp" to StatePathConfig(
            emulatorId = "ppsspp",
            defaultPaths = listOf(
                "{extStorage}/PSP/PPSSPP_STATE",
                "{extStorage}/Android/data/org.ppsspp.ppsspp/files/PSP/PPSSPP_STATE"
            ),
            slotPattern = StateSlotPattern.SerialAndSlot(
                separator = "_",
                extension = "ppst"
            ),
            maxSlots = 5
        ),
        "ppsspp_gold" to StatePathConfig(
            emulatorId = "ppsspp_gold",
            defaultPaths = listOf(
                "{extStorage}/PSP/PPSSPP_STATE",
                "{extStorage}/Android/data/org.ppsspp.ppssppgold/files/PSP/PPSSPP_STATE"
            ),
            slotPattern = StateSlotPattern.SerialAndSlot(
                separator = "_",
                extension = "ppst"
            ),
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

        "duckstation" to StatePathConfig(
            emulatorId = "duckstation",
            defaultPaths = listOf(
                "{extStorage}/duckstation/savestates",
                "{extStorage}/Android/data/com.github.stenzek.duckstation/files/savestates"
            ),
            slotPattern = StateSlotPattern.SerialAndSlot(
                separator = "_",
                extension = "sav"
            ),
            maxSlots = 10
        ),

        "dolphin" to StatePathConfig(
            emulatorId = "dolphin",
            defaultPaths = listOf(
                "{extStorage}/Android/data/org.dolphinemu.dolphinemu/files/StateSaves",
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
        )
    )

    fun getConfig(emulatorId: String): StatePathConfig? {
        val config = configs[emulatorId] ?: return null
        return if (config.supported) config else null
    }

    fun getConfigIncludingUnsupported(emulatorId: String): StatePathConfig? = configs[emulatorId]

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
        val core = if (config.usesCore) getRetroArchCore(canonical) else null

        val paths = config.defaultPaths.map { path ->
            expandPath(path, core)
        }

        if (core != null) {
            val withoutCore = config.defaultPaths.map { path ->
                expandPath(path.replace("/{core}", "").replace("{core}", ""))
            }
            return paths + withoutCore
        }

        return paths
    }

    fun discoverStates(
        stateDir: File,
        romBaseName: String,
        pattern: StateSlotPattern
    ): List<Pair<File, Int>> {
        if (!stateDir.exists() || !stateDir.isDirectory) return emptyList()

        return stateDir.listFiles()
            ?.filter { it.isFile }
            ?.mapNotNull { file ->
                val slotNumber = pattern.parseSlotNumber(file.name, romBaseName)
                if (slotNumber != null) file to slotNumber else null
            }
            ?.sortedBy { it.second }
            ?: emptyList()
    }
}
