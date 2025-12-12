package com.nendo.argosy.data.platform

import com.nendo.argosy.data.local.entity.PlatformEntity

data class PlatformDef(
    val id: String,
    val name: String,
    val shortName: String,
    val extensions: Set<String>,
    val sortOrder: Int
)

object PlatformDefinitions {

    private val platforms = listOf(
        PlatformDef("nes", "Nintendo Entertainment System", "NES", setOf("nes", "unf", "unif"), 1),
        PlatformDef("snes", "Super Nintendo", "SNES", setOf("sfc", "smc", "fig", "swc"), 2),
        PlatformDef("n64", "Nintendo 64", "N64", setOf("n64", "z64", "v64"), 3),
        PlatformDef("gc", "GameCube", "GameCube", setOf("iso", "gcm", "gcz", "rvz", "ciso"), 4),
        PlatformDef("wii", "Nintendo Wii", "Wii", setOf("wbfs", "iso", "rvz", "gcz"), 5),
        PlatformDef("gb", "Game Boy", "Game Boy", setOf("gb"), 6),
        PlatformDef("gbc", "Game Boy Color", "Game Boy Color", setOf("gbc"), 7),
        PlatformDef("gba", "Game Boy Advance", "Game Boy Advance", setOf("gba"), 8),
        PlatformDef("nds", "Nintendo DS", "NDS", setOf("nds", "dsi"), 9),
        PlatformDef("3ds", "Nintendo 3DS", "3DS", setOf("3ds", "cia", "cxi", "app"), 10),
        PlatformDef("switch", "Nintendo Switch", "Switch", setOf("nsp", "xci", "nsz", "xcz"), 11),
        PlatformDef("wiiu", "Nintendo Wii U", "Wii U", setOf("wud", "wux", "rpx", "wua"), 12),

        PlatformDef("sms", "Sega Master System", "SMS", setOf("sms", "sg"), 20),
        PlatformDef("genesis", "Sega Genesis", "Genesis", setOf("md", "gen", "smd", "bin"), 21),
        PlatformDef("scd", "Sega CD", "Sega CD", setOf("iso", "bin", "chd"), 22),
        PlatformDef("32x", "Sega 32X", "32X", setOf("32x"), 23),
        PlatformDef("saturn", "Sega Saturn", "Saturn", setOf("iso", "bin", "cue", "chd"), 24),
        PlatformDef("dreamcast", "Sega Dreamcast", "Dreamcast", setOf("gdi", "cdi", "chd"), 25),
        PlatformDef("gg", "Sega Game Gear", "GG", setOf("gg"), 26),

        PlatformDef("psx", "Sony PlayStation", "PS1", setOf("bin", "iso", "img", "chd", "pbp", "cue", "7z", "zip"), 30),
        PlatformDef("ps2", "Sony PlayStation 2", "PS2", setOf("iso", "bin", "chd", "gz", "cso"), 31),
        PlatformDef("psp", "Sony PlayStation Portable", "PSP", setOf("iso", "cso", "pbp"), 32),
        PlatformDef("vita", "Sony PlayStation Vita", "Vita", setOf("vpk", "mai"), 33),

        PlatformDef("tg16", "TurboGrafx-16", "TG16", setOf("pce"), 40),
        PlatformDef("tgcd", "TurboGrafx-CD", "TG-CD", setOf("chd", "cue", "ccd"), 41),
        PlatformDef("pcfx", "PC-FX", "PC-FX", setOf("chd", "cue", "ccd"), 42),

        PlatformDef("ngp", "Neo Geo Pocket", "NGP", setOf("ngp", "ngc"), 50),
        PlatformDef("ngpc", "Neo Geo Pocket Color", "NGPC", setOf("ngpc", "ngc"), 51),
        PlatformDef("neogeo", "Neo Geo", "Neo Geo", setOf("zip"), 52),

        PlatformDef("atari2600", "Atari 2600", "2600", setOf("a26", "bin"), 60),
        PlatformDef("atari5200", "Atari 5200", "5200", setOf("a52", "bin"), 61),
        PlatformDef("atari7800", "Atari 7800", "7800", setOf("a78", "bin"), 62),
        PlatformDef("lynx", "Atari Lynx", "Lynx", setOf("lnx"), 63),
        PlatformDef("jaguar", "Atari Jaguar", "Jaguar", setOf("j64", "jag"), 64),

        PlatformDef("msx", "MSX", "MSX", setOf("rom", "mx1", "mx2"), 70),
        PlatformDef("msx2", "MSX2", "MSX2", setOf("rom", "mx2"), 71),

        PlatformDef("arcade", "Arcade", "Arcade", setOf("zip"), 80),

        PlatformDef("dos", "DOS", "DOS", setOf("exe", "com", "bat"), 90),
        PlatformDef("scummvm", "ScummVM", "ScummVM", setOf("scummvm"), 91),

        PlatformDef("wonderswan", "WonderSwan", "WS", setOf("ws"), 100),
        PlatformDef("wonderswancolor", "WonderSwan Color", "WSC", setOf("wsc"), 101),

        PlatformDef("vectrex", "Vectrex", "Vectrex", setOf("vec"), 110),
        PlatformDef("coleco", "ColecoVision", "Coleco", setOf("col"), 111),
        PlatformDef("intellivision", "Intellivision", "Intv", setOf("int", "bin"), 112),

        PlatformDef("steam", "Steam", "Steam", emptySet(), 130),
    )

    private val platformMap = platforms.associateBy { it.id }
    private val extensionMap: Map<String, List<PlatformDef>>

    init {
        val extMap = mutableMapOf<String, MutableList<PlatformDef>>()
        platforms.forEach { platform ->
            platform.extensions.forEach { ext ->
                extMap.getOrPut(ext.lowercase()) { mutableListOf() }.add(platform)
            }
        }
        extensionMap = extMap
    }

    fun getAll(): List<PlatformDef> = platforms

    fun getById(id: String): PlatformDef? = platformMap[id]

    fun getPlatformsForExtension(extension: String): List<PlatformDef> =
        extensionMap[extension.lowercase()] ?: emptyList()

    fun toEntity(def: PlatformDef) = PlatformEntity(
        id = def.id,
        name = def.name,
        shortName = def.shortName,
        sortOrder = def.sortOrder,
        romExtensions = def.extensions.joinToString(","),
        isVisible = true
    )

    fun toEntities(): List<PlatformEntity> = platforms.map { toEntity(it) }
}
