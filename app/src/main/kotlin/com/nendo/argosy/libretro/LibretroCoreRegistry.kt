package com.nendo.argosy.libretro

import com.nendo.argosy.data.platform.PlatformDefinitions

object LibretroCoreRegistry {

    data class CoreInfo(
        val coreId: String,
        val fileName: String,
        val displayName: String,
        val platforms: Set<String>,
        val estimatedSizeBytes: Long,
        val requiresBios: List<String> = emptyList(),
        val isDefault: Boolean = false
    )

    private val cores = listOf(
        // Nintendo 8-bit
        CoreInfo(
            coreId = "fceumm",
            fileName = "fceumm_libretro_android.so",
            displayName = "FCEUmm",
            platforms = setOf("nes", "fds"),
            estimatedSizeBytes = 1_500_000L,
            isDefault = true
        ),
        CoreInfo(
            coreId = "nestopia",
            fileName = "nestopia_libretro_android.so",
            displayName = "Nestopia",
            platforms = setOf("nes", "fds"),
            estimatedSizeBytes = 1_200_000L
        ),

        // Nintendo 16-bit
        CoreInfo(
            coreId = "snes9x",
            fileName = "snes9x_libretro_android.so",
            displayName = "Snes9x",
            platforms = setOf("snes"),
            estimatedSizeBytes = 2_500_000L,
            isDefault = true
        ),
        CoreInfo(
            coreId = "bsnes",
            fileName = "bsnes_libretro_android.so",
            displayName = "bsnes",
            platforms = setOf("snes"),
            estimatedSizeBytes = 4_000_000L
        ),

        // Nintendo Handheld
        CoreInfo(
            coreId = "mgba",
            fileName = "mgba_libretro_android.so",
            displayName = "mGBA",
            platforms = setOf("gb", "gbc", "gba"),
            estimatedSizeBytes = 3_000_000L,
            requiresBios = listOf("gba_bios.bin"),
            isDefault = true
        ),
        CoreInfo(
            coreId = "gambatte",
            fileName = "gambatte_libretro_android.so",
            displayName = "Gambatte",
            platforms = setOf("gb", "gbc"),
            estimatedSizeBytes = 800_000L
        ),
        CoreInfo(
            coreId = "vbam",
            fileName = "vbam_libretro_android.so",
            displayName = "VBA-M",
            platforms = setOf("gb", "gbc", "gba"),
            estimatedSizeBytes = 2_000_000L,
            requiresBios = listOf("gba_bios.bin")
        ),

        // Nintendo 64
        CoreInfo(
            coreId = "mupen64plus_next_gles3",
            fileName = "mupen64plus_next_gles3_libretro_android.so",
            displayName = "Mupen64+ GLES3",
            platforms = setOf("n64"),
            estimatedSizeBytes = 8_000_000L,
            isDefault = true
        ),
        CoreInfo(
            coreId = "mupen64plus_next_gles2",
            fileName = "mupen64plus_next_gles2_libretro_android.so",
            displayName = "Mupen64+ GLES2",
            platforms = setOf("n64"),
            estimatedSizeBytes = 7_000_000L
        ),
        CoreInfo(
            coreId = "parallel_n64",
            fileName = "parallel_n64_libretro_android.so",
            displayName = "ParaLLEl N64",
            platforms = setOf("n64"),
            estimatedSizeBytes = 6_000_000L
        ),

        // Nintendo GameCube/Wii
        CoreInfo(
            coreId = "dolphin",
            fileName = "dolphin_libretro_android.so",
            displayName = "Dolphin",
            platforms = setOf("gc", "ngc", "gamecube", "wii"),
            estimatedSizeBytes = 5_500_000L,
            isDefault = true
        ),

        // Sega 8/16-bit
        CoreInfo(
            coreId = "genesis_plus_gx",
            fileName = "genesis_plus_gx_libretro_android.so",
            displayName = "Genesis Plus GX",
            platforms = setOf("genesis", "megadrive", "sms", "gg", "scd", "segacd", "32x"),
            estimatedSizeBytes = 2_000_000L,
            isDefault = true
        ),
        CoreInfo(
            coreId = "picodrive",
            fileName = "picodrive_libretro_android.so",
            displayName = "PicoDrive",
            platforms = setOf("genesis", "megadrive", "sms", "gg", "scd", "segacd", "32x"),
            estimatedSizeBytes = 1_500_000L
        ),

        // Sony PlayStation
        CoreInfo(
            coreId = "mednafen_psx_hw",
            fileName = "mednafen_psx_hw_libretro_android.so",
            displayName = "Beetle PSX HW",
            platforms = setOf("psx", "ps1", "playstation"),
            estimatedSizeBytes = 4_000_000L,
            requiresBios = listOf("scph5501.bin"),
            isDefault = true
        ),
        CoreInfo(
            coreId = "pcsx_rearmed",
            fileName = "pcsx_rearmed_libretro_android.so",
            displayName = "PCSX ReARMed",
            platforms = setOf("psx", "ps1", "playstation"),
            estimatedSizeBytes = 3_000_000L,
            requiresBios = listOf("scph5501.bin")
        ),

        // Sega Saturn
        CoreInfo(
            coreId = "mednafen_saturn",
            fileName = "mednafen_saturn_libretro_android.so",
            displayName = "Beetle Saturn",
            platforms = setOf("saturn"),
            estimatedSizeBytes = 4_000_000L,
            isDefault = true
        ),

        // Sega Dreamcast
        CoreInfo(
            coreId = "flycast",
            fileName = "flycast_libretro_android.so",
            displayName = "Flycast",
            platforms = setOf("dreamcast", "dc"),
            estimatedSizeBytes = 5_000_000L,
            isDefault = true
        ),

        // Atari
        CoreInfo(
            coreId = "stella",
            fileName = "stella_libretro_android.so",
            displayName = "Stella",
            platforms = setOf("atari2600", "2600"),
            estimatedSizeBytes = 1_000_000L,
            isDefault = true
        ),

        // NEC
        CoreInfo(
            coreId = "mednafen_pce",
            fileName = "mednafen_pce_libretro_android.so",
            displayName = "Beetle PCE",
            platforms = setOf("tg16", "pce", "turbografx16", "pcengine"),
            estimatedSizeBytes = 1_500_000L,
            isDefault = true
        ),

        // Nintendo DS
        CoreInfo(
            coreId = "melonds",
            fileName = "melonds_libretro_android.so",
            displayName = "melonDS",
            platforms = setOf("nds", "ds"),
            estimatedSizeBytes = 4_000_000L,
            requiresBios = listOf("bios7.bin", "bios9.bin", "firmware.bin"),
            isDefault = true
        ),

        // Sony PSP
        CoreInfo(
            coreId = "ppsspp",
            fileName = "ppsspp_libretro_android.so",
            displayName = "PPSSPP",
            platforms = setOf("psp"),
            estimatedSizeBytes = 10_000_000L,
            isDefault = true
        ),

        // Virtual Boy
        CoreInfo(
            coreId = "mednafen_vb",
            fileName = "mednafen_vb_libretro_android.so",
            displayName = "Beetle VB",
            platforms = setOf("vb", "virtualboy"),
            estimatedSizeBytes = 1_500_000L,
            isDefault = true
        ),

        // Atari 5200
        CoreInfo(
            coreId = "a5200",
            fileName = "a5200_libretro_android.so",
            displayName = "a5200",
            platforms = setOf("atari5200"),
            estimatedSizeBytes = 500_000L,
            requiresBios = listOf("5200.rom"),
            isDefault = true
        ),

        // Atari 7800
        CoreInfo(
            coreId = "prosystem",
            fileName = "prosystem_libretro_android.so",
            displayName = "ProSystem",
            platforms = setOf("atari7800"),
            estimatedSizeBytes = 500_000L,
            requiresBios = listOf("7800 BIOS (U).rom"),
            isDefault = true
        ),

        // Atari Lynx
        CoreInfo(
            coreId = "handy",
            fileName = "handy_libretro_android.so",
            displayName = "Handy",
            platforms = setOf("lynx"),
            estimatedSizeBytes = 500_000L,
            requiresBios = listOf("lynxboot.img"),
            isDefault = true
        ),
        CoreInfo(
            coreId = "mednafen_lynx",
            fileName = "mednafen_lynx_libretro_android.so",
            displayName = "Beetle Lynx",
            platforms = setOf("lynx"),
            estimatedSizeBytes = 800_000L,
            requiresBios = listOf("lynxboot.img")
        ),

        // WonderSwan / WonderSwan Color
        CoreInfo(
            coreId = "mednafen_wswan",
            fileName = "mednafen_wswan_libretro_android.so",
            displayName = "Beetle WS",
            platforms = setOf("wonderswan", "wsc"),
            estimatedSizeBytes = 1_000_000L,
            isDefault = true
        ),

        // Neo Geo Pocket / Color
        CoreInfo(
            coreId = "mednafen_ngp",
            fileName = "mednafen_ngp_libretro_android.so",
            displayName = "Beetle NGP",
            platforms = setOf("ngp", "ngpc"),
            estimatedSizeBytes = 1_000_000L,
            isDefault = true
        ),

        // ColecoVision
        CoreInfo(
            coreId = "bluemsx",
            fileName = "bluemsx_libretro_android.so",
            displayName = "blueMSX",
            platforms = setOf("coleco", "msx", "msx2"),
            estimatedSizeBytes = 2_000_000L,
            isDefault = true
        ),
        CoreInfo(
            coreId = "gearcoleco",
            fileName = "gearcoleco_libretro_android.so",
            displayName = "Gearcoleco",
            platforms = setOf("coleco"),
            estimatedSizeBytes = 500_000L
        ),

        // Intellivision
        CoreInfo(
            coreId = "freeintv",
            fileName = "freeintv_libretro_android.so",
            displayName = "FreeIntv",
            platforms = setOf("intellivision"),
            estimatedSizeBytes = 500_000L,
            requiresBios = listOf("exec.bin", "grom.bin"),
            isDefault = true
        ),

        // 3DO
        CoreInfo(
            coreId = "opera",
            fileName = "opera_libretro_android.so",
            displayName = "Opera",
            platforms = setOf("3do"),
            estimatedSizeBytes = 2_000_000L,
            requiresBios = listOf("panafz10.bin"),
            isDefault = true
        ),

        // Arcade / Neo Geo
        CoreInfo(
            coreId = "fbneo",
            fileName = "fbneo_libretro_android.so",
            displayName = "FBNeo",
            platforms = setOf("arcade", "neogeo", "cps1", "cps2", "cps3"),
            estimatedSizeBytes = 15_000_000L,
            isDefault = true
        ),
        CoreInfo(
            coreId = "mame2003_plus",
            fileName = "mame2003_plus_libretro_android.so",
            displayName = "MAME 2003+",
            platforms = setOf("arcade"),
            estimatedSizeBytes = 8_000_000L
        ),

        // PC Engine SuperGrafx
        CoreInfo(
            coreId = "mednafen_supergrafx",
            fileName = "mednafen_supergrafx_libretro_android.so",
            displayName = "Beetle SuperGrafx",
            platforms = setOf("supergrafx", "tg16", "tgcd"),
            estimatedSizeBytes = 1_500_000L
        ),

        // Vectrex
        CoreInfo(
            coreId = "vecx",
            fileName = "vecx_libretro_android.so",
            displayName = "vecx",
            platforms = setOf("vectrex"),
            estimatedSizeBytes = 300_000L,
            isDefault = true
        ),

        // Odyssey 2
        CoreInfo(
            coreId = "o2em",
            fileName = "o2em_libretro_android.so",
            displayName = "O2EM",
            platforms = setOf("odyssey2"),
            estimatedSizeBytes = 500_000L,
            requiresBios = listOf("o2rom.bin"),
            isDefault = true
        ),

        // Commodore 64
        CoreInfo(
            coreId = "vice_x64",
            fileName = "vice_x64_libretro_android.so",
            displayName = "VICE x64",
            platforms = setOf("c64"),
            estimatedSizeBytes = 5_000_000L,
            isDefault = true
        ),

        // Amiga
        CoreInfo(
            coreId = "puae",
            fileName = "puae_libretro_android.so",
            displayName = "PUAE",
            platforms = setOf("amiga", "amigacd32", "cdtv"),
            estimatedSizeBytes = 3_000_000L,
            requiresBios = listOf("kick34005.A500"),
            isDefault = true
        ),

        // DOS
        CoreInfo(
            coreId = "dosbox_pure",
            fileName = "dosbox_pure_libretro_android.so",
            displayName = "DOSBox Pure",
            platforms = setOf("dos"),
            estimatedSizeBytes = 4_000_000L,
            isDefault = true
        ),

        // ZX Spectrum
        CoreInfo(
            coreId = "fuse",
            fileName = "fuse_libretro_android.so",
            displayName = "Fuse",
            platforms = setOf("zx"),
            estimatedSizeBytes = 1_000_000L,
            isDefault = true
        ),

        // Amstrad CPC
        CoreInfo(
            coreId = "cap32",
            fileName = "cap32_libretro_android.so",
            displayName = "Caprice32",
            platforms = setOf("amstradcpc"),
            estimatedSizeBytes = 1_000_000L,
            isDefault = true
        ),

        // Channel F
        CoreInfo(
            coreId = "freechaf",
            fileName = "freechaf_libretro_android.so",
            displayName = "FreeChaF",
            platforms = setOf("channelf"),
            estimatedSizeBytes = 300_000L,
            requiresBios = listOf("sl31253.bin", "sl31254.bin"),
            isDefault = true
        ),

        // Pokemon Mini
        CoreInfo(
            coreId = "pokemini",
            fileName = "pokemini_libretro_android.so",
            displayName = "PokeMini",
            platforms = setOf("pokemini"),
            estimatedSizeBytes = 500_000L,
            requiresBios = listOf("bios.min"),
            isDefault = true
        ),

        // Game & Watch
        CoreInfo(
            coreId = "gw",
            fileName = "gw_libretro_android.so",
            displayName = "GW",
            platforms = setOf("gameandwatch"),
            estimatedSizeBytes = 300_000L,
            isDefault = true
        ),
    )

    fun getCoreById(coreId: String): CoreInfo? = cores.find { it.coreId == coreId }

    fun getCoreByFileName(fileName: String): CoreInfo? = cores.find { it.fileName == fileName }

    fun getCoresForPlatform(platformSlug: String): List<CoreInfo> {
        val canonical = PlatformDefinitions.getCanonicalSlug(platformSlug)
        return cores.filter { canonical in it.platforms }
    }

    fun getDefaultCoreForPlatform(platformSlug: String): CoreInfo? =
        getCoresForPlatform(platformSlug).find { it.isDefault }
            ?: getCoresForPlatform(platformSlug).firstOrNull()

    fun getCoresForPlatforms(platformSlugs: Set<String>): List<CoreInfo> =
        cores.filter { core -> core.platforms.any { it in platformSlugs } }
            .distinctBy { it.coreId }

    fun getAllCores(): List<CoreInfo> = cores

    fun getSupportedPlatforms(): Set<String> = cores.flatMap { it.platforms }.toSet()

    fun isPlatformSupported(platformSlug: String): Boolean {
        val canonical = PlatformDefinitions.getCanonicalSlug(platformSlug)
        return cores.any { canonical in it.platforms }
    }
}
