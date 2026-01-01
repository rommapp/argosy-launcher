package com.nendo.argosy.data.emulator

import com.nendo.argosy.data.platform.PlatformDefinitions

data class BiosRequirement(
    val platformSlug: String,
    val fileName: String,
    val md5Hash: String? = null,
    val description: String,
    val isRequired: Boolean = true
)

data class BiosPathConfig(
    val emulatorId: String,
    val defaultPaths: List<String>,
    val supportedPlatforms: Set<String>
)

object BiosPathRegistry {

    private val platformBiosFiles: Map<String, List<BiosRequirement>> = mapOf(
        "psx" to listOf(
            BiosRequirement("psx", "scph1001.bin", "924e392ed05558ffdb115408c263dccf", "PlayStation NTSC-U BIOS"),
            BiosRequirement("psx", "scph5500.bin", "8dd7d5296a650fac7319bce665a6a53c", "PlayStation NTSC-J BIOS"),
            BiosRequirement("psx", "scph5501.bin", "490f666e1afb15b7362b406ed1cea246", "PlayStation NTSC-U BIOS v3.0"),
            BiosRequirement("psx", "scph5502.bin", "32736f17079d0b2b7024407c39bd3050", "PlayStation PAL BIOS")
        ),
        "saturn" to listOf(
            BiosRequirement("saturn", "saturn_bios.bin", null, "Sega Saturn BIOS"),
            BiosRequirement("saturn", "sega_101.bin", null, "Sega Saturn Japan BIOS"),
            BiosRequirement("saturn", "mpr-17933.bin", null, "Sega Saturn US/EU BIOS")
        ),
        "scd" to listOf(
            BiosRequirement("scd", "bios_CD_U.bin", null, "Sega CD US BIOS"),
            BiosRequirement("scd", "bios_CD_E.bin", null, "Sega CD EU BIOS"),
            BiosRequirement("scd", "bios_CD_J.bin", null, "Sega CD Japan BIOS")
        ),
        "dreamcast" to listOf(
            BiosRequirement("dreamcast", "dc_boot.bin", null, "Dreamcast Boot ROM"),
            BiosRequirement("dreamcast", "dc_flash.bin", null, "Dreamcast Flash ROM")
        ),
        "dc" to listOf(
            BiosRequirement("dc", "dc_boot.bin", null, "Dreamcast Boot ROM"),
            BiosRequirement("dc", "dc_flash.bin", null, "Dreamcast Flash ROM")
        ),
        "neogeo" to listOf(
            BiosRequirement("neogeo", "neogeo.zip", null, "Neo Geo BIOS pack")
        ),
        "3do" to listOf(
            BiosRequirement("3do", "panafz10.bin", null, "3DO FZ-10 BIOS", isRequired = true)
        ),
        "nds" to listOf(
            BiosRequirement("nds", "bios7.bin", null, "NDS ARM7 BIOS"),
            BiosRequirement("nds", "bios9.bin", null, "NDS ARM9 BIOS"),
            BiosRequirement("nds", "firmware.bin", null, "NDS Firmware", isRequired = false)
        ),
        "gba" to listOf(
            BiosRequirement("gba", "gba_bios.bin", null, "GBA BIOS", isRequired = false)
        ),
        "tgcd" to listOf(
            BiosRequirement("tgcd", "syscard3.pce", null, "TurboGrafx-CD System Card 3")
        ),
        "pcfx" to listOf(
            BiosRequirement("pcfx", "pcfx.rom", null, "PC-FX BIOS")
        ),
        "lynx" to listOf(
            BiosRequirement("lynx", "lynxboot.img", null, "Atari Lynx Boot ROM")
        ),
        "arcade" to listOf(
            BiosRequirement("arcade", "neogeo.zip", null, "Neo Geo BIOS for arcade")
        )
    )

    private val emulatorBiosPaths: Map<String, BiosPathConfig> = mapOf(
        "retroarch" to BiosPathConfig(
            emulatorId = "retroarch",
            defaultPaths = listOf(
                "/storage/emulated/0/RetroArch/system",
                "/storage/emulated/0/Android/data/com.retroarch/files/system"
            ),
            supportedPlatforms = setOf(
                "psx", "saturn", "scd", "dreamcast", "dc", "neogeo",
                "3do", "nds", "gba", "tgcd", "pcfx", "lynx", "arcade"
            )
        ),
        "retroarch_64" to BiosPathConfig(
            emulatorId = "retroarch_64",
            defaultPaths = listOf(
                "/storage/emulated/0/RetroArch/system",
                "/storage/emulated/0/Android/data/com.retroarch.aarch64/files/system"
            ),
            supportedPlatforms = setOf(
                "psx", "saturn", "scd", "dreamcast", "dc", "neogeo",
                "3do", "nds", "gba", "tgcd", "pcfx", "lynx", "arcade"
            )
        ),
        "duckstation" to BiosPathConfig(
            emulatorId = "duckstation",
            defaultPaths = listOf(
                "/storage/emulated/0/Android/data/com.github.stenzek.duckstation/files/bios"
            ),
            supportedPlatforms = setOf("psx")
        ),
        "melonds" to BiosPathConfig(
            emulatorId = "melonds",
            defaultPaths = listOf(
                "/storage/emulated/0/melonDS/bios",
                "/storage/emulated/0/Android/data/me.magnum.melonds/files/bios"
            ),
            supportedPlatforms = setOf("nds")
        ),
        "flycast" to BiosPathConfig(
            emulatorId = "flycast",
            defaultPaths = listOf(
                "/storage/emulated/0/Android/data/com.flycast.emulator/files/data",
                "/storage/emulated/0/Flycast/data"
            ),
            supportedPlatforms = setOf("dreamcast", "dc", "arcade")
        ),
        "redream" to BiosPathConfig(
            emulatorId = "redream",
            defaultPaths = listOf(
                "/storage/emulated/0/Android/data/io.recompiled.redream/files"
            ),
            supportedPlatforms = setOf("dreamcast", "dc")
        ),
        "saturn_emu" to BiosPathConfig(
            emulatorId = "saturn_emu",
            defaultPaths = listOf(
                "/storage/emulated/0/Android/data/com.explusalpha.SaturnEmu/files"
            ),
            supportedPlatforms = setOf("saturn")
        ),
        "pizza_boy_gba" to BiosPathConfig(
            emulatorId = "pizza_boy_gba",
            defaultPaths = listOf(
                "/storage/emulated/0/PizzaBoyGBA",
                "/storage/emulated/0/Android/data/it.dbtecno.pizzaboygba/files"
            ),
            supportedPlatforms = setOf("gba")
        )
    )

    private val platformsWithBios: Set<String> = platformBiosFiles.keys

    fun getBiosRequirements(platformSlug: String): List<BiosRequirement> {
        val canonical = PlatformDefinitions.getCanonicalSlug(platformSlug)
        return platformBiosFiles[canonical] ?: emptyList()
    }

    fun getRequiredBiosFiles(platformSlug: String): List<BiosRequirement> {
        return getBiosRequirements(platformSlug).filter { it.isRequired }
    }

    fun getPlatformsNeedingBios(): Set<String> = platformsWithBios

    fun hasBiosRequirements(platformSlug: String): Boolean {
        val canonical = PlatformDefinitions.getCanonicalSlug(platformSlug)
        return platformsWithBios.contains(canonical)
    }

    fun getEmulatorBiosPaths(emulatorId: String): BiosPathConfig? {
        return emulatorBiosPaths[emulatorId]
    }

    fun getEmulatorsForPlatform(platformSlug: String): List<BiosPathConfig> {
        val canonical = PlatformDefinitions.getCanonicalSlug(platformSlug)
        return emulatorBiosPaths.values.filter { canonical in it.supportedPlatforms }
    }

    fun getAllBiosConfigs(): Map<String, BiosPathConfig> = emulatorBiosPaths

    fun matchesBiosFile(fileName: String, requirement: BiosRequirement): Boolean {
        return fileName.equals(requirement.fileName, ignoreCase = true)
    }

    fun findMatchingRequirement(fileName: String, platformSlug: String): BiosRequirement? {
        return getBiosRequirements(platformSlug).find { matchesBiosFile(fileName, it) }
    }

    fun getAllBiosFiles(): List<BiosRequirement> {
        return platformBiosFiles.values.flatten().distinctBy { it.fileName.lowercase() }
    }

    fun getBiosFilesByPlatform(): Map<String, List<BiosRequirement>> = platformBiosFiles
}
