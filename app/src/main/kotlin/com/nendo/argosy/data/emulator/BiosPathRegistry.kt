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

    // MD5 hash -> RetroArch expected filename mapping
    // RetroArch cores are strict about filenames, so we rename during distribution
    private val retroArchBiosNames: Map<String, String> = mapOf(
        // 3DO - Opera core
        "f47264dd47fe30f73ab3c010015c155b" to "panafz1.bin",
        "51f2f43ae2f3508a14d9f56597e2d3ce" to "panafz10.bin",
        "1477bda80dc33731a65468c1f5bcbee9" to "panafz10-norsa.bin",
        "a48e6746bd7edec0f40cff078f0bb19f" to "panafz10e-anvil.bin",
        "cf11bbb5a16d7af9875cca9de9a15e09" to "panafz10e-anvil-norsa.bin",
        "a496cfdded3da562759be3561317b605" to "panafz1j.bin",
        "b832da9de7a5621bf1c67e79c9a6de9e" to "panafz1j-norsa.bin",
        "8639fd5e549bd6238cfee79e3e749114" to "goldstar.bin",
        "35fa1a1ebaaeea286dc5cd15487c13ea" to "sanyotry.bin",
        "8970fc987ab89a7f64da9f8a8c4333ff" to "3do_arcade_saot.bin",

        // PlayStation - PCSX ReARMed / Beetle PSX
        "924e392ed05558ffdb115408c263dccf" to "scph1001.bin",
        "8dd7d5296a650fac7319bce665a6a53c" to "scph5500.bin",
        "490f666e1afb15b7362b406ed1cea246" to "scph5501.bin",
        "32736f17079d0b2b7024407c39bd3050" to "scph5502.bin",
        "1e68c231d0896b7eadcad1d7d8e76129" to "scph7001.bin",
        "b9d9a0286c33dc6b7237bb13cd46fdee" to "scph101.bin",

        // Dreamcast - Flycast (goes in dc/ subfolder)
        "e10c53c2f8b90bab96ead2d368858623" to "dc/dc_boot.bin",
        "0a93f7940c455905bea6e392dfde92a4" to "dc/dc_flash.bin",

        // Saturn - Beetle Saturn / YabaSanshiro
        "af5828fdff51384f99b3c4926be27762" to "sega_101.bin",
        "3240872c70984b6cbfda1586cab68dbe" to "mpr-17933.bin",
        "85ec9ca47d8f6f9ab5af2d1234c832d4" to "mpr-18811-mx.ic1",
        "f273555d7d91e8a5a6bfd9bcf066331c" to "mpr-19367-mx.ic1",

        // Sega CD - Genesis Plus GX
        "2efd74e3232ff260e371b99f84024f7f" to "bios_CD_U.bin",
        "854b9150240a198070150e4566ae1290" to "bios_CD_U.bin",
        "e66fa1dc5820d254611fdcdba0662372" to "bios_CD_E.bin",
        "278a9397d192149e84e820ac621a8edd" to "bios_CD_J.bin",

        // GBA
        "a860e8c0b6d573d191e4ec7db1b1e4f6" to "gba_bios.bin",

        // NDS - melonDS / DeSmuME
        "24f67bdea115a2c847c8813a628571b3" to "bios7.bin",
        "df692a80a5b1bc90728bc3dfc76cd948" to "bios7.bin",
        "a392174eb3e572fed6447e956bde4b25" to "bios9.bin",
        "145eaef5bd3037cbc247c213bb3da1b3" to "firmware.bin",
        "94bc5094607c5e6598d50472c52f27f2" to "firmware.bin",

        // DSi - melonDS (DSi mode requires separate folder)
        "559dae4ea78eb9d67702c56c1d791e81" to "bios7.bin",
        "87b665fce118f76251271c3732532777" to "bios9.bin",
        "74f23348012d7b3e1cc216c47192ffeb" to "firmware.bin",
        "d71edf897ddd06bf335feeb68edeb272" to "nand.bin",

        // PC Engine CD - Beetle PCE
        "38179df8f4ac870017db21ebcbf53114" to "syscard3.pce",

        // PC-FX - Beetle PC-FX
        "08e36edbea28a017f79f8d4f7ff9b6d7" to "pcfx.rom",

        // Atari Lynx
        "fcd403db69f54290b51035d82f835e7b" to "lynxboot.img",

        // Neo Geo (kept as-is, usually a zip)
        "dffb72f116d36d025068b23970a4f6df" to "neogeo.zip"
    )

    fun getRetroArchBiosName(md5Hash: String?): String? {
        if (md5Hash == null) return null
        return retroArchBiosNames[md5Hash.lowercase()]
    }

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
