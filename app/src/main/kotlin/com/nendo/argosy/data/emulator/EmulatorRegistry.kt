package com.nendo.argosy.data.emulator

import android.content.Intent

data class EmulatorDef(
    val id: String,
    val packageName: String,
    val displayName: String,
    val supportedPlatforms: Set<String>,
    val launchAction: String = Intent.ACTION_VIEW,
    val launchConfig: LaunchConfig = LaunchConfig.FileUri,
    val downloadUrl: String? = null
)

sealed class LaunchConfig {
    object FileUri : LaunchConfig()

    data class FilePathExtra(
        val extraKeys: List<String> = listOf("ROM", "rom", "romPath")
    ) : LaunchConfig()

    data class RetroArch(
        val activityClass: String = "com.retroarch.browser.retroactivity.RetroActivityFuture"
    ) : LaunchConfig()

    data class Custom(
        val activityClass: String? = null,
        val intentExtras: Map<String, ExtraValue> = emptyMap(),
        val mimeTypeOverride: String? = null,
        val useAbsolutePath: Boolean = false
    ) : LaunchConfig()

    data class CustomScheme(
        val scheme: String,
        val authority: String,
        val pathPrefix: String = ""
    ) : LaunchConfig()
}

sealed class ExtraValue {
    object FilePath : ExtraValue()
    object FileUri : ExtraValue()
    object Platform : ExtraValue()
    data class Literal(val value: String) : ExtraValue()
}

@Deprecated("Use LaunchConfig instead", ReplaceWith("LaunchConfig"))
enum class LaunchType {
    FILE_URI,
    FILE_PATH_EXTRA,
    RETROARCH_CORE
}

object EmulatorRegistry {

    private val emulators = listOf(
        EmulatorDef(
            id = "retroarch",
            packageName = "com.retroarch",
            displayName = "RetroArch",
            supportedPlatforms = setOf(
                "nes", "snes", "n64", "gc", "ngc", "gb", "gbc", "gba", "nds",
                "genesis", "sms", "gg", "scd", "32x",
                "psx", "psp",
                "tg16", "tgcd", "pcfx",
                "atari2600", "atari5200", "atari7800", "lynx",
                "ngp", "ngpc", "neogeo",
                "msx", "msx2",
                "wonderswan", "wonderswancolor",
                "arcade"
            ),
            launchAction = Intent.ACTION_MAIN,
            launchConfig = LaunchConfig.RetroArch(),
            downloadUrl = "https://play.google.com/store/apps/details?id=com.retroarch"
        ),
        EmulatorDef(
            id = "retroarch_64",
            packageName = "com.retroarch.aarch64",
            displayName = "RetroArch (64-bit)",
            supportedPlatforms = setOf(
                "nes", "snes", "n64", "gc", "ngc", "gb", "gbc", "gba", "nds",
                "genesis", "sms", "gg", "scd", "32x",
                "psx", "psp",
                "tg16", "tgcd", "pcfx",
                "atari2600", "atari5200", "atari7800", "lynx",
                "ngp", "ngpc", "neogeo",
                "msx", "msx2",
                "wonderswan", "wonderswancolor",
                "arcade"
            ),
            launchAction = Intent.ACTION_MAIN,
            launchConfig = LaunchConfig.RetroArch(),
            downloadUrl = "https://play.google.com/store/apps/details?id=com.retroarch.aarch64"
        ),

        EmulatorDef(
            id = "mupen64plus_fz",
            packageName = "org.mupen64plusae.v3.fzurita",
            displayName = "Mupen64Plus FZ",
            supportedPlatforms = setOf("n64"),
            downloadUrl = "https://play.google.com/store/apps/details?id=org.mupen64plusae.v3.fzurita"
        ),
        EmulatorDef(
            id = "dolphin",
            packageName = "org.dolphinemu.dolphinemu",
            displayName = "Dolphin",
            supportedPlatforms = setOf("gc", "ngc", "wii"),
            downloadUrl = "https://play.google.com/store/apps/details?id=org.dolphinemu.dolphinemu"
        ),
        EmulatorDef(
            id = "citra",
            packageName = "org.citra.citra_emu",
            displayName = "Citra",
            supportedPlatforms = setOf("3ds"),
            downloadUrl = "https://github.com/citra-emu/citra-android/releases"
        ),
        EmulatorDef(
            id = "citra_mmj",
            packageName = "org.citra.emu",
            displayName = "Citra MMJ",
            supportedPlatforms = setOf("3ds"),
            downloadUrl = "https://github.com/weihuoya/citra/releases"
        ),
        EmulatorDef(
            id = "lime3ds",
            packageName = "io.github.lime3ds.android",
            displayName = "Lime3DS",
            supportedPlatforms = setOf("3ds"),
            downloadUrl = "https://github.com/Lime3DS/Lime3DS/releases"
        ),
        EmulatorDef(
            id = "yuzu",
            packageName = "org.yuzu.yuzu_emu",
            displayName = "Yuzu",
            supportedPlatforms = setOf("switch"),
            downloadUrl = "https://github.com/yuzu-emu/yuzu-android/releases"
        ),
        EmulatorDef(
            id = "ryujinx",
            packageName = "org.ryujinx.android",
            displayName = "Ryujinx",
            supportedPlatforms = setOf("switch"),
            downloadUrl = "https://github.com/Ryujinx/Ryujinx/releases"
        ),
        EmulatorDef(
            id = "skyline",
            packageName = "skyline.emu",
            displayName = "Skyline",
            supportedPlatforms = setOf("switch"),
            downloadUrl = "https://github.com/skyline-emu/skyline/releases"
        ),
        EmulatorDef(
            id = "eden",
            packageName = "dev.eden.eden_emulator",
            displayName = "Eden",
            supportedPlatforms = setOf("switch"),
            downloadUrl = "https://github.com/AKuHAK/eden/releases"
        ),
        EmulatorDef(
            id = "strato",
            packageName = "org.stratoemu.strato",
            displayName = "Strato",
            supportedPlatforms = setOf("switch"),
            downloadUrl = "https://github.com/strato-emu/strato/releases"
        ),
        EmulatorDef(
            id = "citron",
            packageName = "org.citron.emu",
            displayName = "Citron",
            supportedPlatforms = setOf("switch"),
            downloadUrl = "https://git.citron-emu.org/Citron/Citron/releases"
        ),
        EmulatorDef(
            id = "drastic",
            packageName = "com.dsemu.drastic",
            displayName = "DraStic",
            supportedPlatforms = setOf("nds"),
            launchConfig = LaunchConfig.Custom(
                activityClass = "com.dsemu.drastic.DraSticActivity"
            ),
            downloadUrl = "https://play.google.com/store/apps/details?id=com.dsemu.drastic"
        ),
        EmulatorDef(
            id = "melonds",
            packageName = "me.magnum.melonds",
            displayName = "melonDS",
            supportedPlatforms = setOf("nds"),
            downloadUrl = "https://play.google.com/store/apps/details?id=me.magnum.melonds"
        ),
        EmulatorDef(
            id = "pizza_boy_gba",
            packageName = "it.dbtecno.pizzaboygba",
            displayName = "Pizza Boy GBA",
            supportedPlatforms = setOf("gba"),
            downloadUrl = "https://play.google.com/store/apps/details?id=it.dbtecno.pizzaboygba"
        ),
        EmulatorDef(
            id = "pizza_boy_gb",
            packageName = "it.dbtecno.pizzaboy",
            displayName = "Pizza Boy GB",
            supportedPlatforms = setOf("gb", "gbc"),
            downloadUrl = "https://play.google.com/store/apps/details?id=it.dbtecno.pizzaboy"
        ),
        EmulatorDef(
            id = "lemuroid",
            packageName = "com.swordfish.lemuroid",
            displayName = "Lemuroid",
            supportedPlatforms = setOf(
                "nes", "snes", "n64", "gb", "gbc", "gba", "nds",
                "genesis", "sms", "gg",
                "psx", "psp",
                "atari2600", "lynx"
            ),
            downloadUrl = "https://play.google.com/store/apps/details?id=com.swordfish.lemuroid"
        ),

        EmulatorDef(
            id = "duckstation",
            packageName = "com.github.stenzek.duckstation",
            displayName = "DuckStation",
            supportedPlatforms = setOf("psx"),
            downloadUrl = "https://play.google.com/store/apps/details?id=com.github.stenzek.duckstation"
        ),
        EmulatorDef(
            id = "aethersx2",
            packageName = "xyz.aethersx2.android",
            displayName = "AetherSX2",
            supportedPlatforms = setOf("ps2"),
            downloadUrl = "https://www.aethersx2.com/archive/"
        ),
        EmulatorDef(
            id = "pcsx2",
            packageName = "net.pcsx2.emulator",
            displayName = "PCSX2",
            supportedPlatforms = setOf("ps2"),
            downloadUrl = "https://github.com/PCSX2/pcsx2/releases"
        ),
        EmulatorDef(
            id = "ppsspp",
            packageName = "org.ppsspp.ppsspp",
            displayName = "PPSSPP",
            supportedPlatforms = setOf("psp"),
            launchConfig = LaunchConfig.Custom(
                activityClass = "org.ppsspp.ppsspp.PpssppActivity",
                mimeTypeOverride = "application/octet-stream"
            ),
            downloadUrl = "https://play.google.com/store/apps/details?id=org.ppsspp.ppsspp"
        ),
        EmulatorDef(
            id = "ppsspp_gold",
            packageName = "org.ppsspp.ppssppgold",
            displayName = "PPSSPP Gold",
            supportedPlatforms = setOf("psp"),
            launchConfig = LaunchConfig.Custom(
                activityClass = "org.ppsspp.ppssppgold.PpssppActivity",
                mimeTypeOverride = "application/octet-stream"
            ),
            downloadUrl = "https://play.google.com/store/apps/details?id=org.ppsspp.ppssppgold"
        ),
        EmulatorDef(
            id = "vita3k",
            packageName = "org.vita3k.emulator",
            displayName = "Vita3K",
            supportedPlatforms = setOf("vita"),
            downloadUrl = "https://github.com/Vita3K/Vita3K-Android/releases"
        ),

        // NOTE: Redream has known Android 13+ issues - explicit activity launches fail
        // https://github.com/TapiocaFox/Daijishou/issues/487
        // https://github.com/TapiocaFox/Daijishou/issues/579
        EmulatorDef(
            id = "redream",
            packageName = "io.recompiled.redream",
            displayName = "Redream",
            supportedPlatforms = setOf("dreamcast", "dc"),
            launchConfig = LaunchConfig.Custom(
                activityClass = "io.recompiled.redream.MainActivity",
                useAbsolutePath = false
            ),
            downloadUrl = "https://play.google.com/store/apps/details?id=io.recompiled.redream"
        ),
        EmulatorDef(
            id = "flycast",
            packageName = "com.flycast.emulator",
            displayName = "Flycast",
            supportedPlatforms = setOf("dreamcast", "dc", "arcade"),
            launchConfig = LaunchConfig.Custom(
                activityClass = "com.flycast.emulator.MainActivity"
            ),
            downloadUrl = "https://play.google.com/store/apps/details?id=com.flycast.emulator"
        ),
        EmulatorDef(
            id = "saturn_emu",
            packageName = "com.explusalpha.SaturnEmu",
            displayName = "Saturn.emu",
            supportedPlatforms = setOf("saturn"),
            downloadUrl = "https://play.google.com/store/apps/details?id=com.explusalpha.SaturnEmu"
        ),
        EmulatorDef(
            id = "md_emu",
            packageName = "com.explusalpha.MdEmu",
            displayName = "MD.emu",
            supportedPlatforms = setOf("genesis", "sms", "gg", "scd", "32x"),
            downloadUrl = "https://play.google.com/store/apps/details?id=com.explusalpha.MdEmu"
        ),

        EmulatorDef(
            id = "mame4droid",
            packageName = "com.seleuco.mame4droid",
            displayName = "MAME4droid",
            supportedPlatforms = setOf("arcade"),
            downloadUrl = "https://play.google.com/store/apps/details?id=com.seleuco.mame4droid"
        ),
        EmulatorDef(
            id = "fbalpha",
            packageName = "com.bangkokfusion.finalburn",
            displayName = "FinalBurn Alpha",
            supportedPlatforms = setOf("arcade", "neogeo"),
            downloadUrl = "https://play.google.com/store/apps/details?id=com.bangkokfusion.finalburn"
        ),

        EmulatorDef(
            id = "scummvm",
            packageName = "org.scummvm.scummvm",
            displayName = "ScummVM",
            supportedPlatforms = setOf("scummvm"),
            downloadUrl = "https://play.google.com/store/apps/details?id=org.scummvm.scummvm"
        ),
        EmulatorDef(
            id = "dosbox_turbo",
            packageName = "com.fishstix.dosbox",
            displayName = "DosBox Turbo",
            supportedPlatforms = setOf("dos"),
            downloadUrl = "https://play.google.com/store/apps/details?id=com.fishstix.dosbox"
        ),
        EmulatorDef(
            id = "magic_dosbox",
            packageName = "bruenor.magicbox",
            displayName = "Magic DosBox",
            supportedPlatforms = setOf("dos"),
            downloadUrl = "https://play.google.com/store/apps/details?id=bruenor.magicbox"
        )
    )

    private val emulatorMap = emulators.associateBy { it.id }
    private val packageMap = emulators.associateBy { it.packageName }

    fun getAll(): List<EmulatorDef> = emulators

    fun getById(id: String): EmulatorDef? = emulatorMap[id]

    fun getByPackage(packageName: String): EmulatorDef? = packageMap[packageName]

    fun getForPlatform(platformId: String): List<EmulatorDef> =
        emulators.filter { platformId in it.supportedPlatforms }

    fun getRecommendedEmulators(): Map<String, List<String>> = mapOf(
        "psx" to listOf("duckstation", "retroarch", "retroarch_64", "lemuroid"),
        "ps2" to listOf("aethersx2", "pcsx2"),
        "psp" to listOf("ppsspp_gold", "ppsspp", "retroarch", "retroarch_64", "lemuroid"),
        "vita" to listOf("vita3k"),
        "n64" to listOf("mupen64plus_fz", "retroarch", "retroarch_64", "lemuroid"),
        "nds" to listOf("drastic", "melonds", "retroarch", "retroarch_64", "lemuroid"),
        "3ds" to listOf("lime3ds", "citra", "citra_mmj"),
        "gc" to listOf("dolphin", "retroarch", "retroarch_64"),
        "ngc" to listOf("dolphin", "retroarch", "retroarch_64"),
        "wii" to listOf("dolphin"),
        "switch" to listOf("citron", "ryujinx", "yuzu", "strato", "eden", "skyline"),
        "gba" to listOf("pizza_boy_gba", "retroarch", "retroarch_64", "lemuroid"),
        "gb" to listOf("pizza_boy_gb", "retroarch", "retroarch_64", "lemuroid"),
        "gbc" to listOf("pizza_boy_gb", "retroarch", "retroarch_64", "lemuroid"),
        "nes" to listOf("retroarch", "retroarch_64", "lemuroid"),
        "snes" to listOf("retroarch", "retroarch_64", "lemuroid"),
        "genesis" to listOf("md_emu", "retroarch", "retroarch_64", "lemuroid"),
        "sms" to listOf("md_emu", "retroarch", "retroarch_64"),
        "gg" to listOf("md_emu", "retroarch", "retroarch_64"),
        "scd" to listOf("md_emu", "retroarch", "retroarch_64"),
        "32x" to listOf("md_emu", "retroarch", "retroarch_64"),
        "dreamcast" to listOf("redream", "flycast"),
        "dc" to listOf("redream", "flycast"),
        "saturn" to listOf("saturn_emu"),
        "arcade" to listOf("flycast", "mame4droid", "fbalpha", "retroarch", "retroarch_64"),
        "neogeo" to listOf("fbalpha", "retroarch", "retroarch_64"),
        "dos" to listOf("magic_dosbox", "dosbox_turbo"),
        "scummvm" to listOf("scummvm"),
        "atari2600" to listOf("retroarch", "retroarch_64", "lemuroid"),
        "lynx" to listOf("retroarch", "retroarch_64", "lemuroid"),
        "tg16" to listOf("retroarch", "retroarch_64"),
        "tgcd" to listOf("retroarch", "retroarch_64"),
        "ngp" to listOf("retroarch", "retroarch_64"),
        "ngpc" to listOf("retroarch", "retroarch_64"),
        "wonderswan" to listOf("retroarch", "retroarch_64"),
        "wonderswancolor" to listOf("retroarch", "retroarch_64")
    )

    fun getRetroArchCores(): Map<String, String> = mapOf(
        "nes" to "fceumm",
        "snes" to "snes9x",
        "n64" to "mupen64plus_next",
        "gc" to "dolphin",
        "ngc" to "dolphin",
        "gb" to "gambatte",
        "gbc" to "gambatte",
        "gba" to "mgba",
        "nds" to "melonds",
        "genesis" to "genesis_plus_gx",
        "sms" to "genesis_plus_gx",
        "gg" to "genesis_plus_gx",
        "scd" to "genesis_plus_gx",
        "32x" to "picodrive",
        "psx" to "pcsx_rearmed",
        "psp" to "ppsspp",
        "tg16" to "mednafen_pce_fast",
        "tgcd" to "mednafen_pce_fast",
        "pcfx" to "mednafen_pcfx",
        "atari2600" to "stella",
        "atari5200" to "atari800",
        "atari7800" to "prosystem",
        "lynx" to "handy",
        "ngp" to "mednafen_ngp",
        "ngpc" to "mednafen_ngp",
        "neogeo" to "fbneo",
        "arcade" to "fbneo",
        "msx" to "bluemsx",
        "msx2" to "bluemsx",
        "wonderswan" to "mednafen_wswan",
        "wonderswancolor" to "mednafen_wswan"
    )
}
