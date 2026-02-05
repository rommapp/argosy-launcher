package com.nendo.argosy.data.platform

import com.nendo.argosy.data.local.entity.PlatformEntity

object LocalPlatformIds {
    const val ANDROID = -1L
    const val STEAM = -2L
    const val IOS = -3L
}

data class PlatformDef(
    val slug: String,
    val name: String,
    val shortName: String,
    val extensions: Set<String>,
    val sortOrder: Int
)

object PlatformDefinitions {

    // Slug aliases: maps alternate slugs to canonical slug
    private val slugAliases = mapOf(
        // Nintendo
        "famicom" to "nes",
        "fc" to "nes",
        "fam" to "nes",
        "family_computer" to "nes",
        "family-computer" to "nes",
        "nintendo_entertainment_system" to "nes",
        "ngc" to "gc",
        "gamecube" to "gc",
        "nintendo-gamecube" to "gc",
        "nintendoswitch" to "switch",
        "nswitch" to "switch",
        "nintendo-switch" to "switch",
        "new_nintendo3ds" to "3ds",
        "new-nintendo-3ds" to "3ds",
        "n3ds" to "3ds",
        "nintendo_dsi" to "dsi",
        "nintendo-dsi" to "dsi",
        "sfam" to "snes",
        "sfc" to "snes",
        "superfamicom" to "snes",
        "super_famicom" to "snes",
        "super-famicom" to "snes",
        "super_nintendo" to "snes",
        "super-nintendo-entertainment-system" to "snes",
        "nintendo64" to "n64",
        "nintendo_64" to "n64",
        "nintendo-64" to "n64",
        "64dd" to "n64dd",
        "nintendo-64dd" to "n64dd",
        "gameboy" to "gb",
        "game_boy" to "gb",
        "game-boy" to "gb",
        "gameboycolor" to "gbc",
        "game_boy_color" to "gbc",
        "game-boy-color" to "gbc",
        "gameboyadvance" to "gba",
        "game_boy_advance" to "gba",
        "game-boy-advance" to "gba",
        "virtualboy" to "vb",
        "virtual_boy" to "vb",
        "virtual-boy" to "vb",
        "pokemonmini" to "pokemini",
        "pokemon_mini" to "pokemini",
        "pokemon-mini" to "pokemini",
        "nintendo-ds" to "nds",
        "nintendo_ds" to "nds",
        "nintendo-3ds" to "3ds",
        "nintendo_3ds" to "3ds",
        // Sega
        "megadrive" to "genesis",
        "mega_drive" to "genesis",
        "mega-drive" to "genesis",
        "md" to "genesis",
        "sega_genesis" to "genesis",
        "sega-genesis" to "genesis",
        "genesis-slash-megadrive" to "genesis",
        "sega-mega-drive-genesis" to "genesis",
        "segacd" to "scd",
        "sega_cd" to "scd",
        "sega-cd" to "scd",
        "mega_cd" to "scd",
        "mega-cd" to "scd",
        "megacd" to "scd",
        "sega32x" to "32x",
        "sega_32x" to "32x",
        "sega-32x" to "32x",
        "sega32" to "32x",
        "dc" to "dreamcast",
        "sega_dreamcast" to "dreamcast",
        "sega-dreamcast" to "dreamcast",
        "gamegear" to "gg",
        "game_gear" to "gg",
        "game-gear" to "gg",
        "sega_game_gear" to "gg",
        "sega-game-gear" to "gg",
        "sgg" to "gg",
        "sega_saturn" to "saturn",
        "sega-saturn" to "saturn",
        "sega_sg1000" to "sg1000",
        "sega-sg-1000" to "sg1000",
        "sg-1000" to "sg1000",
        "sega_master_system" to "sms",
        "sega-master-system" to "sms",
        "mastersystem" to "sms",
        "master_system" to "sms",
        "master-system" to "sms",
        "segapico" to "pico",
        "sega_pico" to "pico",
        "sega-pico" to "pico",
        // Sony
        "ps" to "psx",
        "ps1" to "psx",
        "playstation" to "psx",
        "playstation1" to "psx",
        "playstation_1" to "psx",
        "playstation-1" to "psx",
        "sony_playstation" to "psx",
        "sony-playstation" to "psx",
        "playstation2" to "ps2",
        "playstation_2" to "ps2",
        "playstation-2" to "ps2",
        "sony_playstation_2" to "ps2",
        "sony-playstation-2" to "ps2",
        "playstation3" to "ps3",
        "playstation_3" to "ps3",
        "playstation-3" to "ps3",
        "sony_playstation_3" to "ps3",
        "sony-playstation-3" to "ps3",
        "playstation4" to "ps4",
        "playstation_4" to "ps4",
        "playstation-4" to "ps4",
        "sony_playstation_4" to "ps4",
        "sony-playstation-4" to "ps4",
        "playstation5" to "ps5",
        "playstation_5" to "ps5",
        "playstation-5" to "ps5",
        "sony_playstation_5" to "ps5",
        "sony-playstation-5" to "ps5",
        "psvita" to "vita",
        "ps_vita" to "vita",
        "ps-vita" to "vita",
        "playstation_vita" to "vita",
        "playstation-vita" to "vita",
        "playstationportable" to "psp",
        "playstation_portable" to "psp",
        "playstation-portable" to "psp",
        // Microsoft
        "originalxbox" to "xbox",
        "original-xbox" to "xbox",
        "microsoft_xbox" to "xbox",
        "microsoft-xbox" to "xbox",
        "x360" to "xbox360",
        "microsoft_xbox_360" to "xbox360",
        "microsoft-xbox-360" to "xbox360",
        "xbox-360" to "xbox360",
        "xb1" to "xboxone",
        "xone" to "xboxone",
        "microsoft_xbox_one" to "xboxone",
        "microsoft-xbox-one" to "xboxone",
        "xbox-one" to "xboxone",
        "xsx" to "xboxseriesx",
        "xbox_series_x" to "xboxseriesx",
        "xbox-series-x" to "xboxseriesx",
        "xbox-series-x-s" to "xboxseriesx",
        // NEC
        "pce" to "tg16",
        "pcengine" to "tg16",
        "pc_engine" to "tg16",
        "pc-engine" to "tg16",
        "turbografx16" to "tg16",
        "turbografx-16" to "tg16",
        "turbografx_16" to "tg16",
        "turbografx16--1" to "tg16",
        "turbografx-16-slash-pc-engine" to "tg16",
        "sgx" to "supergrafx",
        "super_grafx" to "supergrafx",
        "super-grafx" to "supergrafx",
        "pc-engine-supergrafx" to "supergrafx",
        "pcecd" to "tgcd",
        "pc_engine_cd" to "tgcd",
        "pc-engine-cd" to "tgcd",
        "turbografxcd" to "tgcd",
        "turbografx-cd" to "tgcd",
        "turbografx-16-slash-pc-engine-cd" to "tgcd",
        "pc-fx" to "pcfx",
        "pc_fx" to "pcfx",
        // SNK
        "neogeoaes" to "neogeo",
        "neogeo_aes" to "neogeo",
        "neogeo-aes" to "neogeo",
        "neo_geo" to "neogeo",
        "neo-geo" to "neogeo",
        "neogeomvs" to "neogeo",
        "neogeo_mvs" to "neogeo",
        "neogeo-mvs" to "neogeo",
        "neo-geo-mvs" to "neogeo",
        "neocd" to "neogeocd",
        "neogeo_cd" to "neogeocd",
        "neogeo-cd" to "neogeocd",
        "neo_geo_cd" to "neogeocd",
        "neo-geo-cd" to "neogeocd",
        "neogeopocket" to "ngp",
        "neo_geo_pocket" to "ngp",
        "neo-geo-pocket" to "ngp",
        "neogeopocketcolor" to "ngpc",
        "neo_geo_pocket_color" to "ngpc",
        "neo-geo-pocket-color" to "ngpc",
        // Atari
        "atari_2600" to "atari2600",
        "atari-2600" to "atari2600",
        "a2600" to "atari2600",
        "atari_5200" to "atari5200",
        "atari-5200" to "atari5200",
        "a5200" to "atari5200",
        "atari_7800" to "atari7800",
        "atari-7800" to "atari7800",
        "a7800" to "atari7800",
        "atari_st" to "atarist",
        "atari-st" to "atarist",
        "atari_lynx" to "lynx",
        "atari-lynx" to "lynx",
        "atari_jaguar" to "jaguar",
        "atari-jaguar" to "jaguar",
        "atarijaguar" to "jaguar",
        "atari_jaguar_cd" to "jaguarcd",
        "atari-jaguar-cd" to "jaguarcd",
        "atarijaguarcd" to "jaguarcd",
        // Commodore
        "commodore64" to "c64",
        "commodore_64" to "c64",
        "commodore-64" to "c64",
        "commodore128" to "c128",
        "commodore_128" to "c128",
        "commodore-128" to "c128",
        "commodore_amiga" to "amiga",
        "commodore-amiga" to "amiga",
        "commodore_vic20" to "vic20",
        "commodore-vic-20" to "vic20",
        "vic-20" to "vic20",
        "amiga-cd32" to "amigacd32",
        "amiga_cd32" to "amigacd32",
        // Other
        "colecovision" to "coleco",
        "coleco_vision" to "coleco",
        "coleco-vision" to "coleco",
        "mattel_intellivision" to "intellivision",
        "mattel-intellivision" to "intellivision",
        "philips_cdi" to "cdi",
        "philips-cd-i" to "cdi",
        "cd-i" to "cdi",
        "cdinteractive" to "cdi",
        "3do_interactive" to "3do",
        "3do-interactive-multiplayer" to "3do",
        "panasonic_3do" to "3do",
        "panasonic-3do" to "3do",
        "wonderswancolor" to "wsc",
        "wonderswan_color" to "wsc",
        "wonderswan-color" to "wsc",
        "ws" to "wonderswan",
        "bandai_wonderswan" to "wonderswan",
        "bandai-wonderswan" to "wonderswan",
        // Arcade
        "mame" to "arcade",
        "fbneo" to "arcade",
        "fba" to "arcade",
        "cps-1" to "cps1",
        "cps-2" to "cps2",
        "cps-3" to "cps3",
        "naomi2" to "naomi",
        "hikaru" to "naomi",
        // Computers
        "ibm_pc" to "dos",
        "ibm-pc" to "dos",
        "msdos" to "dos",
        "ms-dos" to "dos",
        "ms_dos" to "dos",
        "scumm" to "scummvm",
        "windows_pc" to "windows",
        "windows-pc" to "windows",
        "msx1" to "msx",
        "msx_2" to "msx2",
        "msx-2" to "msx2",
        "amstrad" to "amstradcpc",
        "amstrad_cpc" to "amstradcpc",
        "amstrad-cpc" to "amstradcpc",
        "zxspectrum" to "zx",
        "zx_spectrum" to "zx",
        "zx-spectrum" to "zx",
        "sinclair_zx_spectrum" to "zx",
        "sinclair-zx-spectrum" to "zx",
        "bbc_micro" to "bbcmicro",
        "bbc-micro" to "bbcmicro",
        "fm-towns" to "fmtowns",
        "fm_towns" to "fmtowns",
        "sharp_x68000" to "x68000",
        "sharp-x68000" to "x68000",
        "x68k" to "x68000",
        "sharp_x1" to "sharpx1",
        "sharp-x1" to "sharpx1",
        "pc88" to "pc8800",
        "pc-88" to "pc8800",
        "pc_8800" to "pc8800",
        "pc_8800_series" to "pc8800",
        "nec-pc-8801" to "pc8800",
        "pc98" to "pc9800",
        "pc-98" to "pc9800",
        "pc_9800" to "pc9800",
        "pc_9800_series" to "pc9800",
        "nec-pc-9801" to "pc9800"
    )

    private val localPlatformIdMap = mapOf(
        "android" to LocalPlatformIds.ANDROID,
        "steam" to LocalPlatformIds.STEAM,
        "ios" to LocalPlatformIds.IOS
    )

    fun getLocalPlatformId(slug: String): Long? = localPlatformIdMap[slug.lowercase()]

    fun isLocalPlatform(slug: String): Boolean = localPlatformIdMap.containsKey(slug.lowercase())

    private val platforms = listOf(
        // =====================================================================
        // NINTENDO CONSOLES (100-149) - Chronological order
        // =====================================================================
        PlatformDef("nes", "Nintendo Entertainment System", "NES", setOf("nes", "unf", "unif", "fds", "zip", "7z"), 100),
        PlatformDef("fds", "Famicom Disk System", "FDS", setOf("fds", "zip", "7z"), 102),
        PlatformDef("snes", "Super Nintendo", "SNES", setOf("sfc", "smc", "fig", "swc", "bs", "zip", "7z"), 105),
        PlatformDef("satellaview", "Satellaview", "BS-X", setOf("bs", "sfc", "zip", "7z"), 106),
        PlatformDef("n64", "Nintendo 64", "N64", setOf("n64", "z64", "v64", "zip", "7z"), 110),
        PlatformDef("n64dd", "Nintendo 64DD", "64DD", setOf("ndd", "zip", "7z"), 111),
        PlatformDef("gc", "GameCube", "GCN", setOf("iso", "gcm", "gcz", "rvz", "ciso", "wbfs", "zip", "7z"), 115),
        PlatformDef("wii", "Wii", "Wii", setOf("wbfs", "iso", "rvz", "gcz", "wad", "zip", "7z"), 120),
        PlatformDef("wiiu", "Wii U", "Wii U", setOf("wud", "wux", "rpx", "wua", "zip", "7z"), 125),
        PlatformDef("switch", "Switch", "Switch", setOf("nsp", "xci", "nsz", "xcz", "zip", "7z"), 130),

        // =====================================================================
        // NINTENDO HANDHELDS (150-199) - Chronological order
        // =====================================================================
        PlatformDef("gameandwatch", "Game & Watch", "G&W", setOf("mgw", "zip", "7z"), 150),
        PlatformDef("gb", "Game Boy", "GB", setOf("gb", "zip", "7z"), 155),
        PlatformDef("gbc", "Game Boy Color", "GBC", setOf("gbc", "gb", "zip", "7z"), 160),
        PlatformDef("vb", "Virtual Boy", "VB", setOf("vb", "vboy", "zip", "7z"), 163),
        PlatformDef("gba", "Game Boy Advance", "GBA", setOf("gba", "zip", "7z"), 165),
        PlatformDef("pokemini", "Pokemon Mini", "PokeMini", setOf("min", "zip", "7z"), 167),
        PlatformDef("nds", "Nintendo DS", "NDS", setOf("nds", "dsi", "zip", "7z"), 170),
        PlatformDef("dsi", "Nintendo DSi", "DSi", setOf("nds", "dsi", "zip", "7z"), 172),
        PlatformDef("3ds", "Nintendo 3DS", "3DS", setOf("3ds", "cci", "cia", "cxi", "app", "zcci", "zip", "7z"), 175),
        PlatformDef("n3ds", "New Nintendo 3DS", "N3DS", setOf("3ds", "cci", "cia", "cxi", "app", "zcci", "zip", "7z"), 177),

        // =====================================================================
        // SONY CONSOLES (200-249) - Chronological order
        // =====================================================================
        PlatformDef("psx", "PlayStation", "PS1", setOf("bin", "iso", "img", "chd", "pbp", "cue", "ecm", "zip", "7z"), 200),
        PlatformDef("ps2", "PlayStation 2", "PS2", setOf("iso", "bin", "chd", "gz", "cso", "zso", "zip", "7z"), 210),
        PlatformDef("ps3", "PlayStation 3", "PS3", setOf("iso", "pkg", "zip", "7z"), 220),
        PlatformDef("ps4", "PlayStation 4", "PS4", setOf("pkg", "zip", "7z"), 230),
        PlatformDef("ps5", "PlayStation 5", "PS5", emptySet(), 240),

        // =====================================================================
        // SONY HANDHELDS (250-299) - Chronological order
        // =====================================================================
        PlatformDef("psp", "PlayStation Portable", "PSP", setOf("iso", "cso", "chd", "pbp", "zip", "7z"), 250),
        PlatformDef("vita", "PlayStation Vita", "Vita", setOf("vpk", "mai", "zip", "7z"), 260),

        // =====================================================================
        // SEGA CONSOLES (300-349) - Chronological order
        // =====================================================================
        PlatformDef("sg1000", "SG-1000", "SG-1000", setOf("sg", "zip", "7z"), 300),
        PlatformDef("sms", "Master System", "SMS", setOf("sms", "sg", "zip", "7z"), 305),
        PlatformDef("genesis", "Genesis", "Genesis", setOf("md", "gen", "smd", "bin", "zip", "7z"), 310),
        PlatformDef("scd", "Sega CD", "Sega CD", setOf("iso", "bin", "chd", "cue", "zip", "7z"), 315),
        PlatformDef("32x", "32X", "32X", setOf("32x", "zip", "7z"), 317),
        PlatformDef("pico", "Pico", "Pico", setOf("md", "bin", "zip", "7z"), 318),
        PlatformDef("saturn", "Saturn", "Saturn", setOf("iso", "bin", "cue", "chd", "zip", "7z"), 320),
        PlatformDef("dreamcast", "Dreamcast", "DC", setOf("gdi", "cdi", "chd", "zip", "7z"), 325),

        // =====================================================================
        // SEGA HANDHELDS (350-399)
        // =====================================================================
        PlatformDef("gg", "Game Gear", "GG", setOf("gg", "zip", "7z"), 350),
        PlatformDef("nomad", "Nomad", "Nomad", setOf("md", "gen", "zip", "7z"), 355),

        // =====================================================================
        // SEGA ARCADE (360-379)
        // =====================================================================
        PlatformDef("naomi", "NAOMI", "NAOMI", setOf("zip", "7z", "chd"), 360),
        PlatformDef("naomi2", "NAOMI 2", "NAOMI 2", setOf("zip", "7z", "chd"), 361),
        PlatformDef("atomiswave", "Atomiswave", "Atomiswave", setOf("zip", "7z", "chd"), 365),

        // =====================================================================
        // MICROSOFT (400-449) - Chronological order
        // =====================================================================
        PlatformDef("xbox", "Xbox", "Xbox", setOf("iso", "xiso", "zip", "7z"), 400),
        PlatformDef("xbox360", "Xbox 360", "X360", setOf("iso", "xex", "god", "zip", "7z"), 410),
        PlatformDef("xboxone", "Xbox One", "XB1", emptySet(), 420),
        PlatformDef("xboxseriesx", "Xbox Series X", "XSX", emptySet(), 430),

        // =====================================================================
        // ATARI CONSOLES (450-469) - Chronological order
        // =====================================================================
        PlatformDef("atari2600", "Atari 2600", "2600", setOf("a26", "bin", "zip", "7z"), 450),
        PlatformDef("atari5200", "Atari 5200", "5200", setOf("a52", "bin", "zip", "7z"), 455),
        PlatformDef("atari7800", "Atari 7800", "7800", setOf("a78", "bin", "zip", "7z"), 460),
        PlatformDef("jaguar", "Jaguar", "Jaguar", setOf("j64", "jag", "zip", "7z"), 470),
        PlatformDef("jaguarcd", "Jaguar CD", "Jag CD", setOf("chd", "cue", "zip", "7z"), 475),

        // =====================================================================
        // ATARI HANDHELDS & COMPUTERS (480-499)
        // =====================================================================
        PlatformDef("lynx", "Lynx", "Lynx", setOf("lnx", "zip", "7z"), 480),
        PlatformDef("atarist", "Atari ST", "ST", setOf("st", "stx", "msa", "zip", "7z"), 485),
        PlatformDef("atari8bit", "Atari 8-bit", "A8", setOf("atr", "xex", "xfd", "zip", "7z"), 490),

        // =====================================================================
        // NEC (500-549) - Chronological order
        // =====================================================================
        PlatformDef("tg16", "TurboGrafx-16", "TG16", setOf("pce", "zip", "7z"), 500),
        PlatformDef("supergrafx", "SuperGrafx", "SGX", setOf("pce", "sgx", "zip", "7z"), 505),
        PlatformDef("tgcd", "TurboGrafx-CD", "TG-CD", setOf("chd", "cue", "ccd", "zip", "7z"), 510),
        PlatformDef("pcfx", "PC-FX", "PC-FX", setOf("chd", "cue", "ccd", "zip", "7z"), 520),

        // =====================================================================
        // SNK (550-599) - Chronological order
        // =====================================================================
        PlatformDef("neogeo", "Neo Geo", "Neo Geo", setOf("zip", "7z"), 550),
        PlatformDef("neogeocd", "Neo Geo CD", "NGCD", setOf("chd", "cue", "iso", "zip", "7z"), 555),
        PlatformDef("ngp", "Neo Geo Pocket", "NGP", setOf("ngp", "ngc", "zip", "7z"), 560),
        PlatformDef("ngpc", "Neo Geo Pocket Color", "NGPC", setOf("ngpc", "ngc", "zip", "7z"), 565),
        PlatformDef("hyperneogeo64", "Hyper Neo Geo 64", "HNG64", setOf("zip", "7z"), 570),

        // =====================================================================
        // COMMODORE (600-649) - Chronological order
        // =====================================================================
        PlatformDef("vic20", "VIC-20", "VIC-20", setOf("d64", "t64", "tap", "prg", "p00", "crt", "20", "40", "60", "a0", "b0", "zip", "7z"), 600),
        PlatformDef("c64", "Commodore 64", "C64", setOf("d64", "d81", "g64", "t64", "tap", "prg", "p00", "crt", "bin", "nib", "zip", "7z"), 605),
        PlatformDef("c128", "Commodore 128", "C128", setOf("d64", "d81", "prg", "zip", "7z"), 610),
        PlatformDef("amiga", "Amiga", "Amiga", setOf("adf", "ipf", "lha", "hdf", "zip", "7z"), 620),
        PlatformDef("amigacd32", "Amiga CD32", "CD32", setOf("chd", "cue", "iso", "zip", "7z"), 625),
        PlatformDef("cdtv", "CDTV", "CDTV", setOf("chd", "cue", "iso", "zip", "7z"), 627),

        // =====================================================================
        // ARCADE (650-699) - Alphabetical by system name
        // =====================================================================
        PlatformDef("arcade", "Arcade", "Arcade", setOf("zip", "7z", "chd"), 650),
        PlatformDef("cps1", "CPS-1", "CPS1", setOf("zip", "7z"), 655),
        PlatformDef("cps2", "CPS-2", "CPS2", setOf("zip", "7z"), 660),
        PlatformDef("cps3", "CPS-3", "CPS3", setOf("zip", "7z"), 665),
        PlatformDef("daphne", "Daphne", "Daphne", setOf("daphne", "zip", "7z"), 670),
        PlatformDef("model2", "Model 2", "Model 2", setOf("zip", "7z"), 675),
        PlatformDef("model3", "Model 3", "Model 3", setOf("zip", "7z"), 676),

        // =====================================================================
        // COMPUTERS (700-749) - Alphabetical
        // =====================================================================
        PlatformDef("amstradcpc", "Amstrad CPC", "CPC", setOf("dsk", "sna", "cdt", "zip", "7z"), 700),
        PlatformDef("bbcmicro", "BBC Micro", "BBC", setOf("ssd", "dsd", "uef", "zip", "7z"), 705),
        PlatformDef("dos", "DOS", "DOS", setOf("exe", "com", "bat", "iso", "cue", "img", "ima", "vhd", "dosz", "m3u", "m3u8", "conf", "zip", "7z"), 710),
        PlatformDef("fmtowns", "FM Towns", "FM Towns", setOf("chd", "cue", "iso", "zip", "7z"), 715),
        PlatformDef("msx", "MSX", "MSX", setOf("rom", "mx1", "mx2", "dsk", "zip", "7z"), 720),
        PlatformDef("msx2", "MSX2", "MSX2", setOf("rom", "mx2", "dsk", "zip", "7z"), 721),
        PlatformDef("pc8800", "PC-8800", "PC-88", setOf("d88", "zip", "7z"), 725),
        PlatformDef("pc9800", "PC-9800", "PC-98", setOf("hdi", "fdi", "d98", "zip", "7z"), 726),
        PlatformDef("scummvm", "ScummVM", "ScummVM", setOf("scummvm", "zip", "7z"), 730),
        PlatformDef("sharpx1", "Sharp X1", "X1", setOf("2d", "zip", "7z"), 735),
        PlatformDef("x68000", "X68000", "X68K", setOf("dim", "xdf", "hdm", "zip", "7z"), 736),
        PlatformDef("zx", "ZX Spectrum", "ZX", setOf("tzx", "tap", "z80", "sna", "zip", "7z"), 740),
        PlatformDef("zx81", "ZX81", "ZX81", setOf("p", "81", "zip", "7z"), 741),
        PlatformDef("pc", "PC", "PC", emptySet(), 745),
        PlatformDef("windows", "Windows", "Windows", setOf("exe", "zip", "7z"), 746),

        // =====================================================================
        // OTHER CLASSIC (750-799) - Alphabetical by name
        // =====================================================================
        PlatformDef("3do", "3DO", "3DO", setOf("iso", "chd", "cue", "zip", "7z"), 750),
        PlatformDef("cdi", "CD-i", "CD-i", setOf("chd", "cue", "iso", "zip", "7z"), 755),
        PlatformDef("channelf", "Channel F", "Channel F", setOf("bin", "chf", "zip", "7z"), 760),
        PlatformDef("coleco", "ColecoVision", "Coleco", setOf("col", "zip", "7z"), 765),
        PlatformDef("intellivision", "Intellivision", "Intv", setOf("int", "bin", "rom", "zip", "7z"), 770),
        PlatformDef("odyssey2", "Odyssey 2", "O2", setOf("bin", "zip", "7z"), 775),
        PlatformDef("vectrex", "Vectrex", "Vectrex", setOf("vec", "zip", "7z"), 780),

        // =====================================================================
        // BANDAI (800-849)
        // =====================================================================
        PlatformDef("wonderswan", "WonderSwan", "WS", setOf("ws", "zip", "7z"), 800),
        PlatformDef("wsc", "WonderSwan Color", "WSC", setOf("wsc", "ws", "zip", "7z"), 805),
        PlatformDef("playdia", "Playdia", "Playdia", setOf("chd", "cue", "zip", "7z"), 810),

        // =====================================================================
        // OBSCURE / NICHE (850-899) - Alphabetical by name
        // =====================================================================
        PlatformDef("casioloopy", "Casio Loopy", "Loopy", setOf("zip", "7z"), 850),
        PlatformDef("cassettevision", "Cassette Vision", "CV", setOf("zip", "7z"), 851),
        PlatformDef("supercassettevision", "Super Cassette Vision", "SCV", setOf("zip", "7z"), 852),
        PlatformDef("evercade", "Evercade", "Evercade", emptySet(), 855),
        PlatformDef("gamate", "Gamate", "Gamate", setOf("bin", "zip", "7z"), 860),
        PlatformDef("gp32", "GP32", "GP32", setOf("gxb", "zip", "7z"), 865),
        PlatformDef("megaduck", "Mega Duck", "Mega Duck", setOf("bin", "zip", "7z"), 870),
        PlatformDef("supervision", "Supervision", "Supervision", setOf("sv", "bin", "zip", "7z"), 875),
        PlatformDef("playdate", "Playdate", "Playdate", setOf("pdx", "zip", "7z"), 880),
        PlatformDef("nuon", "Nuon", "Nuon", setOf("iso", "zip", "7z"), 885),
        PlatformDef("arduboy", "Arduboy", "Arduboy", setOf("hex", "arduboy", "zip", "7z"), 890),
        PlatformDef("uzebox", "Uzebox", "Uzebox", setOf("uze", "zip", "7z"), 891),
        PlatformDef("tic80", "TIC-80", "TIC-80", setOf("tic", "zip", "7z"), 892),
        PlatformDef("pico8", "PICO-8", "PICO-8", setOf("p8", "png", "zip", "7z"), 893),
        PlatformDef("lowresnx", "LowRes NX", "LowRes NX", setOf("nx", "zip", "7z"), 894),

        // =====================================================================
        // STREAMING / LAUNCHER (1-9) - Before console platforms
        // =====================================================================
        PlatformDef("android", "Android", "Android", setOf("apk", "xapk"), 1),
        PlatformDef("steam", "Steam", "Steam", emptySet(), 2),
        PlatformDef("ios", "iOS", "iOS", emptySet(), 3)
    )

    private val platformMap: Map<String, PlatformDef>
    private val extensionMap: Map<String, List<PlatformDef>>

    init {
        // Build platform map with both canonical slugs and aliases
        val pMap = mutableMapOf<String, PlatformDef>()
        platforms.forEach { platform ->
            pMap[platform.slug] = platform
        }
        // Add aliases pointing to canonical platforms
        slugAliases.forEach { (alias, canonical) ->
            pMap[canonical]?.let { pMap[alias] = it }
        }
        platformMap = pMap

        // Build extension map
        val extMap = mutableMapOf<String, MutableList<PlatformDef>>()
        platforms.forEach { platform ->
            platform.extensions.forEach { ext ->
                extMap.getOrPut(ext.lowercase()) { mutableListOf() }.add(platform)
            }
        }
        extensionMap = extMap
    }

    fun getAll(): List<PlatformDef> = platforms

    fun getBySlug(slug: String): PlatformDef? = platformMap[slug.lowercase()]

    fun isAlias(slug: String): Boolean = slugAliases.containsKey(slug.lowercase())

    fun getCanonicalSlug(slug: String): String {
        val lower = slug.lowercase()
        return slugAliases[lower] ?: lower
    }

    fun getPlatformsForExtension(extension: String): List<PlatformDef> =
        extensionMap[extension.lowercase()] ?: emptyList()

    fun normalizeDisplayName(name: String): String {
        return name
            .removePrefix("Sony ")
            .removePrefix("Sega ")
            .removePrefix("Microsoft ")
            .removePrefix("Nintendo ")
            .removePrefix("Atari ")
            .removePrefix("Commodore ")
            .removePrefix("Bandai ")
            .removePrefix("SNK ")
            .removePrefix("NEC ")
            .removePrefix("Philips ")
            .removePrefix("Panasonic ")
            .removePrefix("Mattel ")
            .removePrefix("Magnavox ")
            .removePrefix("Sharp ")
            .removePrefix("Sinclair ")
            .removePrefix("Fujitsu ")
            .trim()
    }

    fun toLocalPlatformEntity(def: PlatformDef): PlatformEntity? {
        val localId = getLocalPlatformId(def.slug) ?: return null
        return PlatformEntity(
            id = localId,
            slug = def.slug,
            name = def.name,
            shortName = def.shortName,
            sortOrder = def.sortOrder,
            romExtensions = def.extensions.joinToString(","),
            isVisible = true
        )
    }

    fun toEntity(platformId: Long, def: PlatformDef) = PlatformEntity(
        id = platformId,
        slug = def.slug,
        name = def.name,
        shortName = def.shortName,
        sortOrder = def.sortOrder,
        romExtensions = def.extensions.joinToString(","),
        isVisible = true
    )

    fun getLocalPlatformEntities(): List<PlatformEntity> =
        localPlatformIdMap.mapNotNull { (slug, _) ->
            getBySlug(slug)?.let { toLocalPlatformEntity(it) }
        }
}
