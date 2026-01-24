package com.nendo.argosy.libretro

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
            coreId = "mupen64plus_next",
            fileName = "mupen64plus_next_libretro_android.so",
            displayName = "Mupen64Plus-Next",
            platforms = setOf("n64"),
            estimatedSizeBytes = 8_000_000L,
            isDefault = true
        ),
        CoreInfo(
            coreId = "parallel_n64",
            fileName = "parallel_n64_libretro_android.so",
            displayName = "ParaLLEl N64",
            platforms = setOf("n64"),
            estimatedSizeBytes = 6_000_000L
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
    )

    fun getCoreById(coreId: String): CoreInfo? = cores.find { it.coreId == coreId }

    fun getCoreByFileName(fileName: String): CoreInfo? = cores.find { it.fileName == fileName }

    fun getCoresForPlatform(platformSlug: String): List<CoreInfo> =
        cores.filter { platformSlug in it.platforms }

    fun getDefaultCoreForPlatform(platformSlug: String): CoreInfo? =
        getCoresForPlatform(platformSlug).find { it.isDefault }
            ?: getCoresForPlatform(platformSlug).firstOrNull()

    fun getCoresForPlatforms(platformSlugs: Set<String>): List<CoreInfo> =
        cores.filter { core -> core.platforms.any { it in platformSlugs } }
            .distinctBy { it.coreId }

    fun getAllCores(): List<CoreInfo> = cores

    fun getSupportedPlatforms(): Set<String> = cores.flatMap { it.platforms }.toSet()

    fun isPlatformSupported(platformSlug: String): Boolean =
        cores.any { platformSlug in it.platforms }
}
