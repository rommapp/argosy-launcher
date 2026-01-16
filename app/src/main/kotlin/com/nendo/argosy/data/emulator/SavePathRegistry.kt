package com.nendo.argosy.data.emulator

import com.nendo.argosy.data.platform.PlatformDefinitions

data class SavePathConfig(
    val emulatorId: String,
    val defaultPaths: List<String>,
    val saveExtensions: List<String>,
    val usesCore: Boolean = false,
    val usesFolderBasedSaves: Boolean = false,
    val usesGameIdSubfolder: Boolean = false,
    val usesSharedMemoryCard: Boolean = false,
    val supported: Boolean = true
)

object SavePathRegistry {

    private val configs = mapOf(
        "retroarch" to SavePathConfig(
            emulatorId = "retroarch",
            defaultPaths = listOf(
                "/storage/emulated/0/RetroArch/saves/{core}",
                "/storage/emulated/0/Android/data/com.retroarch/files/saves/{core}",
                "/data/data/com.retroarch/saves/{core}"
            ),
            saveExtensions = listOf("srm", "sav"),
            usesCore = true
        ),
        "retroarch_64" to SavePathConfig(
            emulatorId = "retroarch_64",
            defaultPaths = listOf(
                "/storage/emulated/0/RetroArch/saves/{core}",
                "/storage/emulated/0/Android/data/com.retroarch.aarch64/files/saves/{core}",
                "/data/data/com.retroarch.aarch64/saves/{core}"
            ),
            saveExtensions = listOf("srm", "sav"),
            usesCore = true
        ),

        "mupen64plus_fz" to SavePathConfig(
            emulatorId = "mupen64plus_fz",
            defaultPaths = listOf(
                "/storage/emulated/0/Android/data/org.mupen64plusae.v3.fzurita/files/GameData"
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

        // GameCube/Wii - uses memory card files, not yet supported
        "dolphin" to SavePathConfig(
            emulatorId = "dolphin",
            defaultPaths = listOf(
                "/storage/emulated/0/Android/data/org.dolphinemu.dolphinemu/files/GC",
                "/storage/emulated/0/dolphin-emu/GC"
            ),
            saveExtensions = listOf("raw", "gci"),
            usesSharedMemoryCard = true,
            supported = false
        ),

        // 3DS - folder-based saves
        "citra" to SavePathConfig(
            emulatorId = "citra",
            defaultPaths = listOf(
                "/storage/emulated/0/Android/data/org.citra.citra_emu/files/sdmc/Nintendo 3DS"
            ),
            saveExtensions = listOf("*"),
            usesFolderBasedSaves = true
        ),
        "citra_mmj" to SavePathConfig(
            emulatorId = "citra_mmj",
            defaultPaths = listOf(
                "/storage/emulated/0/Android/data/org.citra.emu/files/sdmc/Nintendo 3DS"
            ),
            saveExtensions = listOf("*"),
            usesFolderBasedSaves = true
        ),
        "lime3ds" to SavePathConfig(
            emulatorId = "lime3ds",
            defaultPaths = listOf(
                "/storage/emulated/0/Android/data/io.github.lime3ds.android/files/sdmc/Nintendo 3DS"
            ),
            saveExtensions = listOf("*"),
            usesFolderBasedSaves = true
        ),
        "azahar" to SavePathConfig(
            emulatorId = "azahar",
            defaultPaths = listOf(
                "/storage/emulated/0/Android/data/io.github.azahar_emu.azahar/files/sdmc/Nintendo 3DS"
            ),
            saveExtensions = listOf("*"),
            usesFolderBasedSaves = true
        ),
        "borked3ds" to SavePathConfig(
            emulatorId = "borked3ds",
            defaultPaths = listOf(
                "/storage/emulated/0/Android/data/io.github.borked3ds.android/files/sdmc/Nintendo 3DS"
            ),
            saveExtensions = listOf("*"),
            usesFolderBasedSaves = true
        ),

        // Switch - folder-based saves
        "yuzu" to SavePathConfig(
            emulatorId = "yuzu",
            defaultPaths = listOf(
                "/storage/emulated/0/Android/data/org.yuzu.yuzu_emu/files/nand/user/save"
            ),
            saveExtensions = listOf("*"),
            usesFolderBasedSaves = true
        ),
        "ryujinx" to SavePathConfig(
            emulatorId = "ryujinx",
            defaultPaths = listOf(
                "/storage/emulated/0/Android/data/org.ryujinx.android/files/nand/user/save"
            ),
            saveExtensions = listOf("*"),
            usesFolderBasedSaves = true
        ),
        "citron" to SavePathConfig(
            emulatorId = "citron",
            defaultPaths = listOf(
                "/storage/emulated/0/Android/data/org.citron.emu/files/nand/user/save"
            ),
            saveExtensions = listOf("*"),
            usesFolderBasedSaves = true
        ),
        "strato" to SavePathConfig(
            emulatorId = "strato",
            defaultPaths = listOf(
                "/storage/emulated/0/Android/data/org.stratoemu.strato/files/nand/user/save"
            ),
            saveExtensions = listOf("*"),
            usesFolderBasedSaves = true
        ),
        "eden" to SavePathConfig(
            emulatorId = "eden",
            defaultPaths = listOf(
                "/storage/emulated/0/Android/data/dev.eden.eden_emulator/files/nand/user/save"
            ),
            saveExtensions = listOf("*"),
            usesFolderBasedSaves = true
        ),
        "skyline" to SavePathConfig(
            emulatorId = "skyline",
            defaultPaths = listOf(
                "/storage/emulated/0/Android/data/skyline.emu/files/nand/user/save"
            ),
            saveExtensions = listOf("*"),
            usesFolderBasedSaves = true
        ),
        "sudachi" to SavePathConfig(
            emulatorId = "sudachi",
            defaultPaths = listOf(
                "/storage/emulated/0/Android/data/org.sudachi.sudachi_emu/files/nand/user/save"
            ),
            saveExtensions = listOf("*"),
            usesFolderBasedSaves = true
        ),

        "drastic" to SavePathConfig(
            emulatorId = "drastic",
            defaultPaths = listOf(
                "/storage/emulated/0/DraStic/backup",
                "/storage/emulated/0/Android/data/com.dsemu.drastic/files/backup"
            ),
            saveExtensions = listOf("dsv", "sav")
        ),
        "melonds" to SavePathConfig(
            emulatorId = "melonds",
            defaultPaths = listOf(
                "/storage/emulated/0/melonDS/saves",
                "/storage/emulated/0/Android/data/me.magnum.melonds/files/saves"
            ),
            saveExtensions = listOf("sav")
        ),

        "pizza_boy_gba" to SavePathConfig(
            emulatorId = "pizza_boy_gba",
            defaultPaths = listOf(
                "/storage/emulated/0/PizzaBoyGBA/saves",
                "/storage/emulated/0/Android/data/it.dbtecno.pizzaboygba/files/saves"
            ),
            saveExtensions = listOf("sav")
        ),
        "pizza_boy_gb" to SavePathConfig(
            emulatorId = "pizza_boy_gb",
            defaultPaths = listOf(
                "/storage/emulated/0/PizzaBoy/saves",
                "/storage/emulated/0/Android/data/it.dbtecno.pizzaboy/files/saves"
            ),
            saveExtensions = listOf("sav")
        ),

        // PS1 - uses shared memory cards, not yet supported
        "duckstation" to SavePathConfig(
            emulatorId = "duckstation",
            defaultPaths = listOf(
                "/storage/emulated/0/Android/data/com.github.stenzek.duckstation/files/memcards",
                "/storage/emulated/0/duckstation/memcards"
            ),
            saveExtensions = listOf("mcd", "mcr"),
            usesSharedMemoryCard = true,
            supported = false
        ),

        // PS2 - uses shared memory cards, not yet supported
        "nethersx2" to SavePathConfig(
            emulatorId = "nethersx2",
            defaultPaths = listOf(
                "/storage/emulated/0/Android/data/xyz.aethersx2.android/files/memcards"
            ),
            saveExtensions = listOf("ps2"),
            usesSharedMemoryCard = true,
            supported = false
        ),
        "aethersx2" to SavePathConfig(
            emulatorId = "aethersx2",
            defaultPaths = listOf(
                "/storage/emulated/0/Android/data/xyz.aethersx2.android/files/memcards"
            ),
            saveExtensions = listOf("ps2"),
            usesSharedMemoryCard = true,
            supported = false
        ),
        "pcsx2" to SavePathConfig(
            emulatorId = "pcsx2",
            defaultPaths = listOf(
                "/storage/emulated/0/Android/data/net.pcsx2.emulator/files/memcards"
            ),
            saveExtensions = listOf("ps2"),
            usesSharedMemoryCard = true,
            supported = false
        ),

        // PSP - folder-based saves
        "ppsspp" to SavePathConfig(
            emulatorId = "ppsspp",
            defaultPaths = listOf(
                "/storage/emulated/0/PSP/SAVEDATA",
                "/storage/emulated/0/Android/data/org.ppsspp.ppsspp/files/PSP/SAVEDATA"
            ),
            saveExtensions = listOf("*"),
            usesFolderBasedSaves = true
        ),
        "ppsspp_gold" to SavePathConfig(
            emulatorId = "ppsspp_gold",
            defaultPaths = listOf(
                "/storage/emulated/0/PSP/SAVEDATA",
                "/storage/emulated/0/Android/data/org.ppsspp.ppssppgold/files/PSP/SAVEDATA"
            ),
            saveExtensions = listOf("*"),
            usesFolderBasedSaves = true
        ),

        // PS Vita - folder-based saves
        "vita3k" to SavePathConfig(
            emulatorId = "vita3k",
            defaultPaths = listOf(
                "/storage/emulated/0/Android/data/org.vita3k.emulator/files/ux0/user/00/savedata"
            ),
            saveExtensions = listOf("*"),
            usesFolderBasedSaves = true
        ),
        "vita3k-zx" to SavePathConfig(
            emulatorId = "vita3k-zx",
            defaultPaths = listOf(
                "/storage/emulated/0/Android/data/org.vita3k.emulator.ikhoeyZX/files/ux0/user/00/savedata"
            ),
            saveExtensions = listOf("*"),
            usesFolderBasedSaves = true
        ),

        // Wii U - folder-based saves by title ID
        "cemu" to SavePathConfig(
            emulatorId = "cemu",
            defaultPaths = listOf(
                "/storage/emulated/0/Android/data/info.cemu.cemu/files/mlc01/usr/save/00050000"
            ),
            saveExtensions = listOf("*"),
            usesFolderBasedSaves = true
        ),
        "cemu_dualscreen" to SavePathConfig(
            emulatorId = "cemu_dualscreen",
            defaultPaths = listOf(
                "/storage/emulated/0/Android/data/info.cemu.cemu/files/mlc01/usr/save/00050000"
            ),
            saveExtensions = listOf("*"),
            usesFolderBasedSaves = true
        ),

        // Dreamcast - uses shared VMU files, not yet supported
        "redream" to SavePathConfig(
            emulatorId = "redream",
            defaultPaths = listOf(
                "/storage/emulated/0/Android/data/io.recompiled.redream/files"
            ),
            saveExtensions = listOf("bin"),
            usesSharedMemoryCard = true,
            supported = false
        ),
        "flycast" to SavePathConfig(
            emulatorId = "flycast",
            defaultPaths = listOf(
                "/storage/emulated/0/Android/data/com.flycast.emulator/files/data",
                "/storage/emulated/0/Flycast/data"
            ),
            saveExtensions = listOf("bin"),
            usesSharedMemoryCard = true,
            supported = false
        ),

        // Saturn - not yet supported
        "saturn_emu" to SavePathConfig(
            emulatorId = "saturn_emu",
            defaultPaths = listOf(
                "/storage/emulated/0/Android/data/com.explusalpha.SaturnEmu/files"
            ),
            saveExtensions = listOf("srm", "sav"),
            supported = false
        ),

        // Genesis/Mega Drive - simple file-based, supported
        "md_emu" to SavePathConfig(
            emulatorId = "md_emu",
            defaultPaths = listOf(
                "/storage/emulated/0/Android/data/com.explusalpha.MdEmu/files"
            ),
            saveExtensions = listOf("srm", "sav")
        ),

        // Arcade - not yet supported
        "mame4droid" to SavePathConfig(
            emulatorId = "mame4droid",
            defaultPaths = listOf(
                "/storage/emulated/0/Android/data/com.seleuco.mame4droid/files/nvram"
            ),
            saveExtensions = listOf("nv"),
            supported = false
        ),

        // PC - not yet supported
        "scummvm" to SavePathConfig(
            emulatorId = "scummvm",
            defaultPaths = listOf(
                "/storage/emulated/0/Android/data/org.scummvm.scummvm/files/saves"
            ),
            saveExtensions = listOf("*"),
            supported = false
        ),
        "dosbox_turbo" to SavePathConfig(
            emulatorId = "dosbox_turbo",
            defaultPaths = listOf(
                "/storage/emulated/0/Android/data/com.fishstix.dosbox/files"
            ),
            saveExtensions = listOf("*"),
            supported = false
        ),
        "magic_dosbox" to SavePathConfig(
            emulatorId = "magic_dosbox",
            defaultPaths = listOf(
                "/storage/emulated/0/Android/data/bruenor.magicbox/files"
            ),
            saveExtensions = listOf("*"),
            supported = false
        )
    )

    fun getConfig(emulatorId: String): SavePathConfig? {
        val config = configs[emulatorId] ?: return null
        return if (config.supported) config else null
    }

    fun getConfigIncludingUnsupported(emulatorId: String): SavePathConfig? = configs[emulatorId]

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

    fun resolvePath(
        config: SavePathConfig,
        platformId: String
    ): List<String> {
        if (!config.usesCore) return config.defaultPaths

        val canonical = PlatformDefinitions.getCanonicalSlug(platformId)
        val core = getRetroArchCore(canonical) ?: return config.defaultPaths
        val withCore = config.defaultPaths.map { path ->
            path.replace("{core}", core)
        }
        val withoutCore = config.defaultPaths.map { path ->
            path.replace("/{core}", "").replace("{core}", "")
        }
        return withCore + withoutCore
    }
}
