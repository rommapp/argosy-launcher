package com.nendo.argosy.data.emulator

import android.os.Environment
import com.nendo.argosy.data.platform.PlatformDefinitions

data class SavePathConfig(
    val emulatorId: String,
    val defaultPaths: List<String>,
    val saveExtensions: List<String>,
    val usesCore: Boolean = false,
    val usesFolderBasedSaves: Boolean = false,
    val usesGameIdSubfolder: Boolean = false,
    val usesSharedMemoryCard: Boolean = false,
    val usesPackageTemplate: Boolean = false,
    val usesInternalStorage: Boolean = false,
    val usesGciFormat: Boolean = false,
    val supported: Boolean = true
)

private const val BUILTIN_EMULATOR_ID = "builtin"

object SavePathRegistry {

    private val configs = mapOf(
        "retroarch" to SavePathConfig(
            emulatorId = "retroarch",
            defaultPaths = listOf(
                "{extStorage}/RetroArch/saves/{core}",
                "{extStorage}/Android/data/com.retroarch/files/saves/{core}",
                "/data/data/com.retroarch/saves/{core}"
            ),
            saveExtensions = listOf("srm", "sav"),
            usesCore = true
        ),
        "retroarch_64" to SavePathConfig(
            emulatorId = "retroarch_64",
            defaultPaths = listOf(
                "{extStorage}/RetroArch/saves/{core}",
                "{extStorage}/Android/data/com.retroarch.aarch64/files/saves/{core}",
                "/data/data/com.retroarch.aarch64/saves/{core}"
            ),
            saveExtensions = listOf("srm", "sav"),
            usesCore = true
        ),

        "mupen64plus_fz" to SavePathConfig(
            emulatorId = "mupen64plus_fz",
            defaultPaths = listOf(
                "{extStorage}/Android/data/org.mupen64plusae.v3.fzurita/files/GameData"
            ),
            saveExtensions = listOf("sra", "eep", "fla", "mpk"),
            usesGameIdSubfolder = true
        ),
        "m64pro_fzx_plus" to SavePathConfig(
            emulatorId = "m64pro_fzx_plus",
            defaultPaths = listOf(
                "/storage/emulated/0/Android/data/com.m64.fx.plus.emulate/files/GameData"
            ),
            saveExtensions = listOf("sra", "eep", "fla", "mpk"),
            usesGameIdSubfolder = true
        ),

        // GameCube - GCI folder mode (per-game saves)
        "dolphin" to SavePathConfig(
            emulatorId = "dolphin",
            defaultPaths = listOf(
                "{extStorage}/Android/data/org.dolphinemu.dolphinemu/files/GC",
                "{extStorage}/Android/data/org.dolphinemu.handheld/files/GC",
                "{extStorage}/dolphin-emu/GC"
            ),
            saveExtensions = listOf("gci"),
            usesGciFormat = true,
            supported = true
        ),
        "dolphin_mmjr" to SavePathConfig(
            emulatorId = "dolphin_mmjr",
            defaultPaths = listOf(
                "{extStorage}/Android/data/org.dolphinemu.mmjr/files/GC",
                "{extStorage}/mmjr/GC",
                "{extStorage}/mmjr2-vbi/GC"
            ),
            saveExtensions = listOf("gci"),
            usesGciFormat = true,
            supported = true
        ),
        // RetroArch dolphin-emu core uses GCI folder structure
        "retroarch_dolphin" to SavePathConfig(
            emulatorId = "retroarch_dolphin",
            defaultPaths = listOf(
                "{extStorage}/RetroArch/saves/dolphin-emu/User/GC",
                "{extStorage}/Android/data/com.retroarch/files/saves/dolphin-emu/User/GC",
                "{extStorage}/Android/data/com.retroarch.aarch64/files/saves/dolphin-emu/User/GC"
            ),
            saveExtensions = listOf("gci"),
            usesGciFormat = true,
            supported = true
        ),

        // Wii - NAND folder-based saves (game ID as hex)
        "dolphin_wii" to SavePathConfig(
            emulatorId = "dolphin_wii",
            defaultPaths = listOf(
                "{extStorage}/Android/data/org.dolphinemu.dolphinemu/files/Wii/title/00010000",
                "{extStorage}/Android/data/org.dolphinemu.handheld/files/Wii/title/00010000",
                "{extStorage}/dolphin-emu/Wii/title/00010000"
            ),
            saveExtensions = listOf("*"),
            usesFolderBasedSaves = true,
            supported = true
        ),
        "dolphin_mmjr_wii" to SavePathConfig(
            emulatorId = "dolphin_mmjr_wii",
            defaultPaths = listOf(
                "{extStorage}/Android/data/org.dolphinemu.mmjr/files/Wii/title/00010000",
                "{extStorage}/mmjr/Wii/title/00010000",
                "{extStorage}/mmjr2-vbi/Wii/title/00010000"
            ),
            saveExtensions = listOf("*"),
            usesFolderBasedSaves = true,
            supported = true
        ),

        // 3DS - folder-based saves
        "citra" to SavePathConfig(
            emulatorId = "citra",
            defaultPaths = listOf(
                "{extStorage}/Android/data/org.citra.citra_emu/files/sdmc/Nintendo 3DS"
            ),
            saveExtensions = listOf("*"),
            usesFolderBasedSaves = true
        ),
        "citra_mmj" to SavePathConfig(
            emulatorId = "citra_mmj",
            defaultPaths = listOf(
                "{extStorage}/Android/data/org.citra.emu/files/sdmc/Nintendo 3DS"
            ),
            saveExtensions = listOf("*"),
            usesFolderBasedSaves = true
        ),
        "lime3ds" to SavePathConfig(
            emulatorId = "lime3ds",
            defaultPaths = listOf(
                "{extStorage}/Android/data/io.github.lime3ds.android/files/sdmc/Nintendo 3DS"
            ),
            saveExtensions = listOf("*"),
            usesFolderBasedSaves = true
        ),
        "azahar" to SavePathConfig(
            emulatorId = "azahar",
            defaultPaths = listOf(
                "{extStorage}/Android/data/io.github.azahar_emu.azahar/files/sdmc/Nintendo 3DS"
            ),
            saveExtensions = listOf("*"),
            usesFolderBasedSaves = true
        ),
        "borked3ds" to SavePathConfig(
            emulatorId = "borked3ds",
            defaultPaths = listOf(
                "{extStorage}/Android/data/io.github.borked3ds.android/files/sdmc/Nintendo 3DS"
            ),
            saveExtensions = listOf("*"),
            usesFolderBasedSaves = true
        ),

        // Switch - folder-based saves (using {package} for dynamic path resolution)
        "yuzu" to SavePathConfig(
            emulatorId = "yuzu",
            defaultPaths = listOf(
                "{extStorage}/Android/data/{package}/files/nand/user/save"
            ),
            saveExtensions = listOf("*"),
            usesFolderBasedSaves = true,
            usesPackageTemplate = true
        ),
        "ryujinx" to SavePathConfig(
            emulatorId = "ryujinx",
            defaultPaths = listOf(
                "{extStorage}/Android/data/{package}/files/nand/user/save"
            ),
            saveExtensions = listOf("*"),
            usesFolderBasedSaves = true,
            usesPackageTemplate = true
        ),
        "citron" to SavePathConfig(
            emulatorId = "citron",
            defaultPaths = listOf(
                "{extStorage}/Android/data/{package}/files/nand/user/save"
            ),
            saveExtensions = listOf("*"),
            usesFolderBasedSaves = true,
            usesPackageTemplate = true
        ),
        "strato" to SavePathConfig(
            emulatorId = "strato",
            defaultPaths = listOf(
                "{extStorage}/Android/data/{package}/files/nand/user/save"
            ),
            saveExtensions = listOf("*"),
            usesFolderBasedSaves = true,
            usesPackageTemplate = true
        ),
        "eden" to SavePathConfig(
            emulatorId = "eden",
            defaultPaths = listOf(
                "{extStorage}/Android/data/{package}/files/nand/user/save"
            ),
            saveExtensions = listOf("*"),
            usesFolderBasedSaves = true,
            usesPackageTemplate = true
        ),
        "skyline" to SavePathConfig(
            emulatorId = "skyline",
            defaultPaths = listOf(
                "{extStorage}/Android/data/{package}/files/nand/user/save"
            ),
            saveExtensions = listOf("*"),
            usesFolderBasedSaves = true,
            usesPackageTemplate = true
        ),
        "sudachi" to SavePathConfig(
            emulatorId = "sudachi",
            defaultPaths = listOf(
                "{extStorage}/Android/data/{package}/files/nand/user/save"
            ),
            saveExtensions = listOf("*"),
            usesFolderBasedSaves = true,
            usesPackageTemplate = true
        ),
        "kenjinx" to SavePathConfig(
            emulatorId = "kenjinx",
            defaultPaths = listOf(
                "{extStorage}/Android/data/{package}/files/nand/user/save"
            ),
            saveExtensions = listOf("*"),
            usesFolderBasedSaves = true,
            usesPackageTemplate = true
        ),

        "drastic" to SavePathConfig(
            emulatorId = "drastic",
            defaultPaths = listOf(
                "{extStorage}/DraStic/backup",
                "{extStorage}/Android/data/com.dsemu.drastic/files/backup"
            ),
            saveExtensions = listOf("dsv", "sav")
        ),
        "melonds" to SavePathConfig(
            emulatorId = "melonds",
            defaultPaths = listOf(
                "{extStorage}/melonDS/saves",
                "{extStorage}/Android/data/me.magnum.melonds/files/saves"
            ),
            saveExtensions = listOf("sav")
        ),

        "pizza_boy_gba" to SavePathConfig(
            emulatorId = "pizza_boy_gba",
            defaultPaths = listOf(
                "{extStorage}/PizzaBoyGBA/saves",
                "{extStorage}/Android/data/it.dbtecno.pizzaboygba/files/saves"
            ),
            saveExtensions = listOf("sav")
        ),
        "pizza_boy_gb" to SavePathConfig(
            emulatorId = "pizza_boy_gb",
            defaultPaths = listOf(
                "{extStorage}/PizzaBoy/saves",
                "{extStorage}/Android/data/it.dbtecno.pizzaboy/files/saves"
            ),
            saveExtensions = listOf("sav")
        ),

        // PS1 - uses shared memory cards, not yet supported
        "duckstation" to SavePathConfig(
            emulatorId = "duckstation",
            defaultPaths = listOf(
                "{extStorage}/Android/data/com.github.stenzek.duckstation/files/memcards",
                "{extStorage}/duckstation/memcards"
            ),
            saveExtensions = listOf("mcd", "mcr"),
            usesSharedMemoryCard = true,
            supported = false
        ),

        // PS2 - uses shared memory cards, not yet supported
        "nethersx2" to SavePathConfig(
            emulatorId = "nethersx2",
            defaultPaths = listOf(
                "{extStorage}/Android/data/xyz.aethersx2.android/files/memcards"
            ),
            saveExtensions = listOf("ps2"),
            usesSharedMemoryCard = true,
            supported = false
        ),
        "aethersx2" to SavePathConfig(
            emulatorId = "aethersx2",
            defaultPaths = listOf(
                "{extStorage}/Android/data/xyz.aethersx2.android/files/memcards"
            ),
            saveExtensions = listOf("ps2"),
            usesSharedMemoryCard = true,
            supported = false
        ),
        "pcsx2" to SavePathConfig(
            emulatorId = "pcsx2",
            defaultPaths = listOf(
                "{extStorage}/Android/data/net.pcsx2.emulator/files/memcards"
            ),
            saveExtensions = listOf("ps2"),
            usesSharedMemoryCard = true,
            supported = false
        ),
        "armsx2" to SavePathConfig(
            emulatorId = "armsx2",
            defaultPaths = listOf(
                "{extStorage}/Android/data/come.nanodata.armsx2/files/memcards"
            ),
            saveExtensions = listOf("ps2"),
            usesSharedMemoryCard = true,
            supported = false
        ),

        // PSP - folder-based saves
        "ppsspp" to SavePathConfig(
            emulatorId = "ppsspp",
            defaultPaths = listOf(
                "{extStorage}/PSP/SAVEDATA",
                "{extStorage}/Android/data/org.ppsspp.ppsspp/files/PSP/SAVEDATA"
            ),
            saveExtensions = listOf("*"),
            usesFolderBasedSaves = true
        ),
        "ppsspp_gold" to SavePathConfig(
            emulatorId = "ppsspp_gold",
            defaultPaths = listOf(
                "{extStorage}/PSP/SAVEDATA",
                "{extStorage}/Android/data/org.ppsspp.ppssppgold/files/PSP/SAVEDATA"
            ),
            saveExtensions = listOf("*"),
            usesFolderBasedSaves = true
        ),

        // PS Vita - folder-based saves
        "vita3k" to SavePathConfig(
            emulatorId = "vita3k",
            defaultPaths = listOf(
                "{extStorage}/Android/data/org.vita3k.emulator/files/VITA/ux0/user/00/savedata"
            ),
            saveExtensions = listOf("*"),
            usesFolderBasedSaves = true
        ),
        "vita3k-zx" to SavePathConfig(
            emulatorId = "vita3k-zx",
            defaultPaths = listOf(
                "{extStorage}/Android/data/org.vita3k.emulator.ikhoeyZX/files/VITA/ux0/user/00/savedata"
            ),
            saveExtensions = listOf("*"),
            usesFolderBasedSaves = true
        ),

        // Wii U - folder-based saves by title ID
        "cemu" to SavePathConfig(
            emulatorId = "cemu",
            defaultPaths = listOf(
                "{extStorage}/Android/data/info.cemu.cemu/files/mlc01/usr/save/00050000"
            ),
            saveExtensions = listOf("*"),
            usesFolderBasedSaves = true
        ),
        "cemu_dualscreen" to SavePathConfig(
            emulatorId = "cemu_dualscreen",
            defaultPaths = listOf(
                "{extStorage}/Android/data/info.cemu.cemu/files/mlc01/usr/save/00050000"
            ),
            saveExtensions = listOf("*"),
            usesFolderBasedSaves = true
        ),

        // Dreamcast - uses shared VMU files, not yet supported
        "redream" to SavePathConfig(
            emulatorId = "redream",
            defaultPaths = listOf(
                "{extStorage}/Android/data/io.recompiled.redream/files"
            ),
            saveExtensions = listOf("bin"),
            usesSharedMemoryCard = true,
            supported = false
        ),
        "flycast" to SavePathConfig(
            emulatorId = "flycast",
            defaultPaths = listOf(
                "{extStorage}/Android/data/com.flycast.emulator/files/data",
                "{extStorage}/Flycast/data"
            ),
            saveExtensions = listOf("bin"),
            usesSharedMemoryCard = true,
            supported = false
        ),

        // Saturn - not yet supported
        "saturn_emu" to SavePathConfig(
            emulatorId = "saturn_emu",
            defaultPaths = listOf(
                "{extStorage}/Android/data/com.explusalpha.SaturnEmu/files"
            ),
            saveExtensions = listOf("srm", "sav"),
            supported = false
        ),

        // Genesis/Mega Drive - simple file-based, supported
        "md_emu" to SavePathConfig(
            emulatorId = "md_emu",
            defaultPaths = listOf(
                "{extStorage}/Android/data/com.explusalpha.MdEmu/files"
            ),
            saveExtensions = listOf("srm", "sav")
        ),

        // Arcade - not yet supported
        "mame4droid" to SavePathConfig(
            emulatorId = "mame4droid",
            defaultPaths = listOf(
                "{extStorage}/Android/data/com.seleuco.mame4droid/files/nvram"
            ),
            saveExtensions = listOf("nv"),
            supported = false
        ),

        // PC - not yet supported
        "scummvm" to SavePathConfig(
            emulatorId = "scummvm",
            defaultPaths = listOf(
                "{extStorage}/Android/data/org.scummvm.scummvm/files/saves"
            ),
            saveExtensions = listOf("*"),
            supported = false
        ),
        "dosbox_turbo" to SavePathConfig(
            emulatorId = "dosbox_turbo",
            defaultPaths = listOf(
                "{extStorage}/Android/data/com.fishstix.dosbox/files"
            ),
            saveExtensions = listOf("*"),
            supported = false
        ),
        "magic_dosbox" to SavePathConfig(
            emulatorId = "magic_dosbox",
            defaultPaths = listOf(
                "{extStorage}/Android/data/bruenor.magicbox/files"
            ),
            saveExtensions = listOf("*"),
            supported = false
        ),

        BUILTIN_EMULATOR_ID to SavePathConfig(
            emulatorId = BUILTIN_EMULATOR_ID,
            defaultPaths = listOf("{filesDir}/libretro/saves"),
            saveExtensions = listOf("srm"),
            usesInternalStorage = true
        )
    )

    private val packageToConfigId = mapOf(
        "org.dolphinemu.dolphinemu" to "dolphin",
        "org.dolphinemu.mmjr" to "dolphin_mmjr",
        "org.dolphinemu.handheld" to "dolphin",
        "com.retroarch" to "retroarch",
        "com.retroarch.aarch64" to "retroarch_64",
        "org.ppsspp.ppsspp" to "ppsspp",
        "org.ppsspp.ppssppgold" to "ppsspp_gold",
        "org.mupen64plusae.v3.fzurita" to "mupen64plus_fz",
        "com.m64.fx.plus.emulate" to "m64pro_fzx_plus",
        "me.magnum.melonds" to "melonds",
        "com.dsemu.drastic" to "drastic",
        "it.dbtecno.pizzaboygba" to "pizza_boy_gba",
        "it.dbtecno.pizzaboy" to "pizza_boy_gb",
        "info.cemu.cemu" to "cemu",
        "org.vita3k.emulator" to "vita3k",
        "org.vita3k.emulator.ikhoeyZX" to "vita3k-zx"
    )

    private val packagePrefixToConfigId = mapOf(
        "org.dolphinemu.mmjr" to "dolphin_mmjr",
        "org.dolphinemu" to "dolphin"
    )

    fun getConfig(emulatorId: String): SavePathConfig? {
        val config = configs[emulatorId] ?: return null
        return if (config.supported) config else null
    }

    fun getConfigByPackage(packageName: String): SavePathConfig? {
        val configId = resolveConfigIdForPackage(packageName) ?: return null
        return getConfig(configId)
    }

    fun resolveConfigIdForPackage(packageName: String): String? {
        return packageToConfigId[packageName]
            ?: packagePrefixToConfigId.entries.firstOrNull { packageName.startsWith(it.key) }?.value
    }

    fun getConfigIncludingUnsupported(emulatorId: String): SavePathConfig? = configs[emulatorId]

    fun getConfigIncludingUnsupportedByPackage(packageName: String): SavePathConfig? {
        val configId = resolveConfigIdForPackage(packageName) ?: return null
        return getConfigIncludingUnsupported(configId)
    }

    fun getAllConfigs(): Map<String, SavePathConfig> = configs.filterValues { it.supported }

    fun canSyncWithSettings(
        emulatorId: String,
        saveSyncEnabled: Boolean,
        experimentalFolderSaveSync: Boolean
    ): Boolean {
        if (!saveSyncEnabled) return false
        val config = getConfig(emulatorId) ?: return false
        if (config.usesFolderBasedSaves && !experimentalFolderSaveSync) return false
        return true
    }

    fun getRetroArchCore(platformId: String): String? {
        val canonical = PlatformDefinitions.getCanonicalSlug(platformId)
        return EmulatorRegistry.getRetroArchCorePatterns()[canonical]?.firstOrNull()
    }

    @Suppress("DEPRECATION")
    private fun getExternalStoragePath(): String {
        return Environment.getExternalStorageDirectory().absolutePath
    }

    private fun expandPath(
        path: String,
        packageName: String? = null,
        core: String? = null,
        filesDir: String? = null
    ): String {
        var result = path.replace("{extStorage}", getExternalStoragePath())
        if (packageName != null) {
            result = result.replace("{package}", packageName)
        }
        if (core != null) {
            result = result.replace("{core}", core)
        }
        if (filesDir != null) {
            result = result.replace("{filesDir}", filesDir)
        }
        return result
    }

    fun resolvePath(
        config: SavePathConfig,
        platformId: String,
        filesDir: String? = null
    ): List<String> {
        val canonical = PlatformDefinitions.getCanonicalSlug(platformId)
        val core = if (config.usesCore) getRetroArchCore(canonical) else null

        val paths = config.defaultPaths.map { path ->
            expandPath(path, core = core, filesDir = filesDir)
        }

        if (core != null) {
            val withoutCore = config.defaultPaths.map { path ->
                expandPath(path.replace("/{core}", "").replace("{core}", ""), filesDir = filesDir)
            }
            return paths + withoutCore
        }

        return paths
    }

    fun resolvePathWithPackage(
        config: SavePathConfig,
        emulatorPackage: String?
    ): List<String> {
        val packageName = if (config.usesPackageTemplate) {
            emulatorPackage ?: EmulatorRegistry.getById(config.emulatorId)?.packageName
        } else null

        return config.defaultPaths.map { path ->
            expandPath(path, packageName = packageName)
        }
    }
}
