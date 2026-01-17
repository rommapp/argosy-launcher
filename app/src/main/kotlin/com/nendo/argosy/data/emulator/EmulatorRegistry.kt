package com.nendo.argosy.data.emulator

import android.content.Intent
import com.nendo.argosy.data.platform.PlatformDefinitions

data class EmulatorDef(
    val id: String,
    val packageName: String,
    val displayName: String,
    val supportedPlatforms: Set<String>,
    val launchAction: String = Intent.ACTION_VIEW,
    val launchConfig: LaunchConfig = LaunchConfig.FileUri,
    val downloadUrl: String? = null,
    val packagePatterns: List<String> = emptyList()
)

data class EmulatorFamily(
    val baseId: String,
    val displayNamePrefix: String,
    val packagePatterns: List<String>,
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
        val useAbsolutePath: Boolean = false,
        val useFileUri: Boolean = false
    ) : LaunchConfig()

    data class CustomScheme(
        val scheme: String,
        val authority: String,
        val pathPrefix: String = ""
    ) : LaunchConfig()

    data class Vita3K(
        val activityClass: String = "org.vita3k.emulator.Emulator"
    ) : LaunchConfig()
}

sealed class ExtraValue {
    object FilePath : ExtraValue()
    object FileUri : ExtraValue()
    object Platform : ExtraValue()
    data class Literal(val value: String) : ExtraValue()
    data class BooleanLiteral(val value: Boolean) : ExtraValue()
}

data class RetroArchCore(
    val id: String,
    val displayName: String
)

data class ExtensionOption(
    val extension: String,
    val label: String
)

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
                "nes", "snes", "n64", "gc", "ngc", "gb", "gbc", "gba", "nds", "3ds",
                "genesis", "sms", "gg", "scd", "32x",
                "psx", "psp", "saturn", "dreamcast", "dc",
                "tg16", "tgcd", "pcfx", "3do",
                "atari2600", "atari5200", "atari7800", "lynx",
                "ngp", "ngpc", "neogeo",
                "msx", "msx2",
                "wonderswan", "wonderswancolor",
                "arcade",
                "c64", "vic20"
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
                "nes", "snes", "n64", "gc", "ngc", "gb", "gbc", "gba", "nds", "3ds",
                "genesis", "sms", "gg", "scd", "32x",
                "psx", "psp", "saturn", "dreamcast", "dc",
                "tg16", "tgcd", "pcfx", "3do",
                "atari2600", "atari5200", "atari7800", "lynx",
                "ngp", "ngpc", "neogeo",
                "msx", "msx2",
                "wonderswan", "wonderswancolor",
                "arcade",
                "c64", "vic20"
            ),
            launchAction = Intent.ACTION_MAIN,
            launchConfig = LaunchConfig.RetroArch(),
            downloadUrl = "https://play.google.com/store/apps/details?id=com.retroarch.aarch64"
        ),

        // NOTE: Mupen64Plus-based emulators require special handling for external ROM launches
        // Problem: The emulator calls `new File(romPath).exists()` - content:// URIs fail this check
        // Solution: Pass absolute file path via extras (not URI), so File.exists() returns true
        // The emulator then calls launchGameOnCreation() which scans and caches the ROM properly
        EmulatorDef(
            id = "mupen64plus_fz",
            packageName = "org.mupen64plusae.v3.fzurita",
            displayName = "Mupen64Plus FZ",
            supportedPlatforms = setOf("n64"),
            launchAction = Intent.ACTION_MAIN,
            launchConfig = LaunchConfig.Custom(
                activityClass = "paulscode.android.mupen64plusae.SplashActivity",
                intentExtras = mapOf(
                    // Key derived from: ActivityHelper.Keys.ROM_PATH = Keys.class.getCanonicalName() + ".ROM_PATH"
                    // Source: https://github.com/mupen64plus-ae/mupen64plus-ae/blob/master/app/src/main/java/paulscode/android/mupen64plusae/ActivityHelper.java
                    "paulscode.android.mupen64plusae.ActivityHelper.Keys.ROM_PATH" to ExtraValue.FilePath
                )
            ),
            downloadUrl = "https://play.google.com/store/apps/details?id=org.mupen64plusae.v3.fzurita"
        ),
        // NOTE: M64Pro FZX Plus+ is a Mupen64Plus fork, uses same launch pattern
        EmulatorDef(
            id = "m64pro_fzx_plus",
            packageName = "com.m64.fx.plus.emulate",
            displayName = "M64Pro FZX Plus+",
            supportedPlatforms = setOf("n64"),
            launchAction = Intent.ACTION_MAIN,
            launchConfig = LaunchConfig.Custom(
                activityClass = "paulscode.android.mupen64plusae.SplashActivity",
                intentExtras = mapOf(
                    // Same key as mupen64plus_fz - see source reference above
                    "paulscode.android.mupen64plusae.ActivityHelper.Keys.ROM_PATH" to ExtraValue.FilePath
                )
            ),
            downloadUrl = "https://play.google.com/store/apps/details?id=com.m64.fx.plus.emulate"
        ),
        EmulatorDef(
            id = "dolphin",
            packageName = "org.dolphinemu.dolphinemu",
            displayName = "Dolphin",
            supportedPlatforms = setOf("gc", "ngc", "wii"),
            launchAction = Intent.ACTION_MAIN,
            launchConfig = LaunchConfig.Custom(
                activityClass = "org.dolphinemu.dolphinemu.ui.main.MainActivity",
                intentExtras = mapOf("AutoStartFile" to ExtraValue.FileUri)
            ),
            downloadUrl = "https://play.google.com/store/apps/details?id=org.dolphinemu.dolphinemu"
        ),
        EmulatorDef(
            id = "dolphin_handheld",
            packageName = "org.dolphinemu.handheld",
            displayName = "Dolphin (Handheld)",
            supportedPlatforms = setOf("gc", "ngc", "wii"),
            launchAction = Intent.ACTION_MAIN,
            launchConfig = LaunchConfig.Custom(
                activityClass = "org.dolphinemu.dolphinemu.ui.main.MainActivity",
                intentExtras = mapOf("AutoStartFile" to ExtraValue.FileUri)
            ),
            downloadUrl = "https://dolphin-emu.org/download/"
        ),
        EmulatorDef(
            id = "cemu",
            packageName = "info.cemu.cemu",
            displayName = "Cemu",
            supportedPlatforms = setOf("wiiu"),
            downloadUrl = "https://github.com/SSimco/Cemu/releases"
        ),
        // NOTE: Dual-screen fork uses same package name as official Cemu - only one can be installed
        EmulatorDef(
            id = "cemu_dualscreen",
            packageName = "info.cemu.cemu",
            displayName = "Cemu (Dual Screen)",
            supportedPlatforms = setOf("wiiu"),
            downloadUrl = "https://github.com/SapphireRhodonite/Cemu/releases"
        ),
        // NOTE: Original Citra is discontinued - use Azahar or Borked3DS instead
        EmulatorDef(
            id = "citra",
            packageName = "org.citra.citra_emu",
            displayName = "Citra",
            supportedPlatforms = setOf("3ds"),
            launchConfig = LaunchConfig.Custom(
                activityClass = "org.citra.citra_emu.activities.EmulationActivity",
                intentExtras = mapOf("SelectedGame" to ExtraValue.FilePath)
            )
        ),
        EmulatorDef(
            id = "citra_mmj",
            packageName = "org.citra.emu",
            displayName = "Citra MMJ",
            supportedPlatforms = setOf("3ds"),
            launchConfig = LaunchConfig.Custom(
                activityClass = "org.citra.emu.ui.EmulationActivity",
                intentExtras = mapOf("GamePath" to ExtraValue.FilePath)
            ),
            downloadUrl = "https://github.com/weihuoya/citra/releases"
        ),
        // NOTE: Azahar took over Lime3DS development, keeping the same package name
        // Uses Citra's internal namespace for activities
        EmulatorDef(
            id = "azahar",
            packageName = "io.github.lime3ds.android",
            displayName = "Azahar",
            supportedPlatforms = setOf("3ds"),
            launchConfig = LaunchConfig.Custom(
                activityClass = "org.citra.citra_emu.activities.EmulationActivity",
                intentExtras = mapOf("SelectedGame" to ExtraValue.FilePath)
            ),
            downloadUrl = "https://github.com/azahar-emu/azahar/releases"
        ),
        // NOTE: AzaharPlus is a fork of Azahar that restores .3ds file support
        EmulatorDef(
            id = "azahar_plus",
            packageName = "io.github.azaharplus.android",
            displayName = "AzaharPlus",
            supportedPlatforms = setOf("3ds"),
            launchConfig = LaunchConfig.Custom(
                activityClass = "org.citra.citra_emu.activities.EmulationActivity",
                intentExtras = mapOf("SelectedGame" to ExtraValue.FilePath)
            ),
            downloadUrl = "https://github.com/AzaharPlus/AzaharPlus/releases"
        ),
        EmulatorDef(
            id = "borked3ds",
            packageName = "io.github.borked3ds.android",
            displayName = "Borked3DS",
            supportedPlatforms = setOf("3ds"),
            launchConfig = LaunchConfig.Custom(
                activityClass = "io.github.borked3ds.android.activities.EmulationActivity"
            ),
            downloadUrl = "https://github.com/Borked3DS/Borked3DS/releases"
        ),
        EmulatorDef(
            id = "yuzu",
            packageName = "org.yuzu.yuzu_emu",
            displayName = "Yuzu",
            supportedPlatforms = setOf("switch")
        ),
        EmulatorDef(
            id = "ryujinx",
            packageName = "org.ryujinx.android",
            displayName = "Ryujinx",
            supportedPlatforms = setOf("switch")
        ),
        EmulatorDef(
            id = "skyline",
            packageName = "skyline.emu",
            displayName = "Skyline",
            supportedPlatforms = setOf("switch")
        ),
        EmulatorDef(
            id = "eden",
            packageName = "dev.eden.eden_emulator",
            displayName = "Eden",
            supportedPlatforms = setOf("switch"),
            downloadUrl = "https://github.com/eden-emulator/Releases/releases"
        ),
        EmulatorDef(
            id = "strato",
            packageName = "org.stratoemu.strato",
            displayName = "Strato",
            supportedPlatforms = setOf("switch")
        ),
        EmulatorDef(
            id = "citron",
            packageName = "org.citron.emu",
            displayName = "Citron",
            supportedPlatforms = setOf("switch"),
            downloadUrl = "https://git.citron-emu.org/citron/emulator/releases"
        ),
        // NOTE: Kenji-NX is an active fork of Ryujinx for Android
        EmulatorDef(
            id = "kenjinx",
            packageName = "org.kenjinx.android",
            displayName = "Kenji-NX",
            supportedPlatforms = setOf("switch"),
            downloadUrl = "https://github.com/Kenji-NX/Android-Releases/releases"
        ),
        EmulatorDef(
            id = "sudachi",
            packageName = "org.sudachi.sudachi_emu",
            displayName = "Sudachi",
            supportedPlatforms = setOf("switch")
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
        // NOTE: Nightly builds (Oct 2025+) use intent.data URI, Beta uses PATH extra
        // We send both for compatibility - nightly checks data first, beta falls back to PATH
        EmulatorDef(
            id = "melonds",
            packageName = "me.magnum.melonds",
            displayName = "melonDS",
            supportedPlatforms = setOf("nds"),
            launchConfig = LaunchConfig.Custom(
                activityClass = "me.magnum.melonds.ui.emulator.EmulatorActivity",
                intentExtras = mapOf("PATH" to ExtraValue.FilePath)
            ),
            downloadUrl = "https://github.com/rafaelvcaetano/melonDS-android/releases/tag/nightly-release"
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

        // NOTE: Lemuroid does NOT support intent launching from external apps
        // https://github.com/Swordfish90/Lemuroid/issues/803

        EmulatorDef(
            id = "duckstation",
            packageName = "com.github.stenzek.duckstation",
            displayName = "DuckStation",
            supportedPlatforms = setOf("psx"),
            launchAction = Intent.ACTION_MAIN,
            launchConfig = LaunchConfig.Custom(
                activityClass = "com.github.stenzek.duckstation.EmulationActivity",
                intentExtras = mapOf("bootPath" to ExtraValue.FilePath)
            ),
            downloadUrl = "https://play.google.com/store/apps/details?id=com.github.stenzek.duckstation"
        ),
        EmulatorDef(
            id = "nethersx2",
            packageName = "xyz.aethersx2.android",
            displayName = "NetherSX2",
            supportedPlatforms = setOf("ps2"),
            launchAction = Intent.ACTION_MAIN,
            launchConfig = LaunchConfig.Custom(
                activityClass = "xyz.aethersx2.android.EmulationActivity",
                intentExtras = mapOf("bootPath" to ExtraValue.FilePath)
            ),
            downloadUrl = "https://github.com/Trixarian/NetherSX2-patch/releases"
        ),
        // AetherSX2 is discontinued - shares package with NetherSX2, kept for detection
        EmulatorDef(
            id = "aethersx2",
            packageName = "xyz.aethersx2.android",
            displayName = "AetherSX2",
            supportedPlatforms = setOf("ps2"),
            launchAction = Intent.ACTION_MAIN,
            launchConfig = LaunchConfig.Custom(
                activityClass = "xyz.aethersx2.android.EmulationActivity",
                intentExtras = mapOf("bootPath" to ExtraValue.FilePath)
            )
        ),
        EmulatorDef(
            id = "armsx2",
            packageName = "come.nanodata.armsx2",
            displayName = "ARMSX2",
            supportedPlatforms = setOf("ps2"),
            downloadUrl = "https://github.com/ARMSX2/ARMSX2/releases"
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
            supportedPlatforms = setOf("vita", "psvita"),
            launchAction = Intent.ACTION_MAIN,
            launchConfig = LaunchConfig.Vita3K(),
            downloadUrl = "https://github.com/Vita3K/Vita3K-Android/releases"
        ),
        EmulatorDef(
            id = "vita3k-zx",
            packageName = "org.vita3k.emulator.ikhoeyZX",
            displayName = "Vita3K ZX",
            supportedPlatforms = setOf("vita", "psvita"),
            launchAction = Intent.ACTION_MAIN,
            launchConfig = LaunchConfig.Vita3K(),
            downloadUrl = "https://github.com/ikhoeyZX/Vita3K-Android/releases"
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
                activityClass = "com.flycast.emulator.MainActivity",
                useFileUri = true
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
        ),

        // Steam launchers
        EmulatorDef(
            id = "gamehub",
            packageName = "com.xiaoji.egggame",
            displayName = "GameHub",
            supportedPlatforms = setOf("steam"),
            downloadUrl = "https://play.google.com/store/apps/details?id=com.xiaoji.egggame"
        ),
        EmulatorDef(
            id = "gamehub_lite",
            packageName = "com.antutu.ABenchMark",
            displayName = "GameHub Lite",
            supportedPlatforms = setOf("steam"),
            downloadUrl = "https://github.com/AntGameHub/GameHub-Lite-Support/releases"
        ),
        EmulatorDef(
            id = "gamenative",
            packageName = "app.gamenative",
            displayName = "GameNative",
            supportedPlatforms = setOf("steam"),
            downloadUrl = "https://github.com/utkarshdalal/GameNative/releases"
        )
    )

    private val emulatorMap = emulators.associateBy { it.id }
    private val packageMap = emulators.associateBy { it.packageName }

    fun getAll(): List<EmulatorDef> = emulators

    fun getById(id: String): EmulatorDef? = emulatorMap[id]

    fun getByPackage(packageName: String): EmulatorDef? = packageMap[packageName]

    fun getForPlatform(platformId: String): List<EmulatorDef> {
        val canonical = PlatformDefinitions.getCanonicalSlug(platformId)
        return emulators.filter { canonical in it.supportedPlatforms }
    }

    fun getRecommendedEmulators(): Map<String, List<String>> = mapOf(
        "psx" to listOf("duckstation", "retroarch", "retroarch_64"),
        "ps2" to listOf("nethersx2", "armsx2", "pcsx2"),
        "psp" to listOf("ppsspp_gold", "ppsspp", "retroarch", "retroarch_64"),
        "vita" to listOf("vita3k-zx", "vita3k"),
        "psvita" to listOf("vita3k-zx", "vita3k"),
        "n64" to listOf("m64pro_fzx_plus", "mupen64plus_fz", "retroarch", "retroarch_64"),
        "nds" to listOf("drastic", "melonds", "retroarch", "retroarch_64"),
        "3ds" to listOf("azahar_plus", "azahar", "citra_mmj", "borked3ds", "citra", "retroarch", "retroarch_64"),
        "gc" to listOf("dolphin", "dolphin_handheld", "retroarch", "retroarch_64"),
        "ngc" to listOf("dolphin", "dolphin_handheld", "retroarch", "retroarch_64"),
        "wii" to listOf("dolphin", "dolphin_handheld"),
        "wiiu" to listOf("cemu", "cemu_dualscreen"),
        "switch" to listOf("citron", "sudachi", "ryujinx", "yuzu", "strato", "eden", "skyline"),
        "gba" to listOf("pizza_boy_gba", "retroarch", "retroarch_64"),
        "gb" to listOf("pizza_boy_gb", "retroarch", "retroarch_64"),
        "gbc" to listOf("pizza_boy_gb", "retroarch", "retroarch_64"),
        "nes" to listOf("retroarch", "retroarch_64"),
        "snes" to listOf("retroarch", "retroarch_64"),
        "genesis" to listOf("md_emu", "retroarch", "retroarch_64"),
        "sms" to listOf("md_emu", "retroarch", "retroarch_64"),
        "gg" to listOf("md_emu", "retroarch", "retroarch_64"),
        "scd" to listOf("md_emu", "retroarch", "retroarch_64"),
        "32x" to listOf("md_emu", "retroarch", "retroarch_64"),
        "dreamcast" to listOf("redream", "flycast"),
        "dc" to listOf("redream", "flycast"),
        "saturn" to listOf("saturn_emu", "retroarch", "retroarch_64"),
        "arcade" to listOf("flycast", "mame4droid", "fbalpha", "retroarch", "retroarch_64"),
        "neogeo" to listOf("fbalpha", "retroarch", "retroarch_64"),
        "dos" to listOf("magic_dosbox", "dosbox_turbo"),
        "scummvm" to listOf("scummvm"),
        "atari2600" to listOf("retroarch", "retroarch_64"),
        "lynx" to listOf("retroarch", "retroarch_64"),
        "tg16" to listOf("retroarch", "retroarch_64"),
        "tgcd" to listOf("retroarch", "retroarch_64"),
        "3do" to listOf("retroarch", "retroarch_64"),
        "ngp" to listOf("retroarch", "retroarch_64"),
        "ngpc" to listOf("retroarch", "retroarch_64"),
        "wonderswan" to listOf("retroarch", "retroarch_64"),
        "wonderswancolor" to listOf("retroarch", "retroarch_64"),
        "steam" to listOf("gamehub", "gamehub_lite", "gamenative"),
        "c64" to listOf("retroarch", "retroarch_64"),
        "vic20" to listOf("retroarch", "retroarch_64")
    )

    fun getPreferredCore(platformId: String): String? {
        val canonical = PlatformDefinitions.getCanonicalSlug(platformId)
        return preferredCores[canonical]
    }

    private val preferredCores = mapOf(
        "nes" to "fceumm",
        "snes" to "snes9x",
        "n64" to "mupen64plus_next_gles3",
        "gc" to "dolphin",
        "ngc" to "dolphin",
        "gb" to "gambatte",
        "gbc" to "gambatte",
        "gba" to "mgba",
        "nds" to "melonds",
        "3ds" to "citra",
        "genesis" to "genesis_plus_gx",
        "sms" to "genesis_plus_gx",
        "gg" to "genesis_plus_gx",
        "scd" to "genesis_plus_gx",
        "32x" to "picodrive",
        "psx" to "pcsx_rearmed",
        "psp" to "ppsspp",
        "saturn" to "yabasanshiro",
        "dreamcast" to "flycast",
        "dc" to "flycast",
        "tg16" to "mednafen_pce_fast",
        "tgcd" to "mednafen_pce_fast",
        "pcfx" to "mednafen_pcfx",
        "3do" to "opera",
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
        "wonderswancolor" to "mednafen_wswan",
        "c64" to "vice_x64",
        "vic20" to "vice_xvic"
    )

    fun getRetroArchCorePatterns(): Map<String, List<String>> = mapOf(
        "nes" to listOf("fceumm", "nestopia", "quicknes", "mesen"),
        "snes" to listOf("snes9x", "bsnes", "mesen"),
        "n64" to listOf("mupen64plus_next"),
        "gc" to listOf("dolphin"),
        "ngc" to listOf("dolphin"),
        "gb" to listOf("gambatte", "mgba", "sameboy", "gearboy", "tgbdual"),
        "gbc" to listOf("gambatte", "mgba", "sameboy", "gearboy", "tgbdual"),
        "gba" to listOf("mgba", "vba", "gpsp"),
        "nds" to listOf("melonds", "desmume"),
        "3ds" to listOf("citra"),
        "genesis" to listOf("genesis_plus_gx", "picodrive"),
        "sms" to listOf("genesis_plus_gx", "picodrive", "gearsystem"),
        "gg" to listOf("genesis_plus_gx", "gearsystem"),
        "scd" to listOf("genesis_plus_gx", "picodrive"),
        "32x" to listOf("picodrive"),
        "psx" to listOf("pcsx_rearmed", "swanstation", "mednafen_psx"),
        "psp" to listOf("ppsspp"),
        "saturn" to listOf("yabasanshiro", "yabause", "mednafen_saturn"),
        "dreamcast" to listOf("flycast"),
        "dc" to listOf("flycast"),
        "tg16" to listOf("mednafen_pce"),
        "tgcd" to listOf("mednafen_pce"),
        "pcfx" to listOf("pcfx"),
        "3do" to listOf("opera"),
        "atari2600" to listOf("stella"),
        "atari5200" to listOf("atari800", "a5200"),
        "atari7800" to listOf("prosystem"),
        "lynx" to listOf("handy", "mednafen_lynx"),
        "ngp" to listOf("mednafen_ngp"),
        "ngpc" to listOf("mednafen_ngp"),
        "neogeo" to listOf("fbneo", "fbalpha"),
        "arcade" to listOf("fbneo", "mame", "fbalpha"),
        "msx" to listOf("bluemsx", "fmsx"),
        "msx2" to listOf("bluemsx", "fmsx"),
        "wonderswan" to listOf("mednafen_wswan"),
        "wonderswancolor" to listOf("mednafen_wswan"),
        "c64" to listOf("vice_x64", "vice_x64sc"),
        "vic20" to listOf("vice_xvic")
    )

    private val platformCores: Map<String, List<RetroArchCore>> = mapOf(
        "nes" to listOf(
            RetroArchCore("fceumm", "FCEUmm"),
            RetroArchCore("nestopia", "Nestopia"),
            RetroArchCore("mesen", "Mesen"),
            RetroArchCore("quicknes", "QuickNES")
        ),
        "snes" to listOf(
            RetroArchCore("snes9x", "Snes9x"),
            RetroArchCore("snes9x2010", "Snes9x 2010"),
            RetroArchCore("bsnes", "bsnes"),
            RetroArchCore("bsnes2014_accuracy", "bsnes 2014 Accuracy"),
            RetroArchCore("mesen-s", "Mesen-S")
        ),
        "n64" to listOf(
            RetroArchCore("mupen64plus_next_gles3", "Mupen64Plus-Next (GLES3)"),
            RetroArchCore("mupen64plus_next_gles2", "Mupen64Plus-Next (GLES2)")
        ),
        "gb" to listOf(
            RetroArchCore("gambatte", "Gambatte"),
            RetroArchCore("mgba", "mGBA"),
            RetroArchCore("sameboy", "SameBoy"),
            RetroArchCore("gearboy", "Gearboy"),
            RetroArchCore("tgbdual", "TGB Dual")
        ),
        "gbc" to listOf(
            RetroArchCore("gambatte", "Gambatte"),
            RetroArchCore("mgba", "mGBA"),
            RetroArchCore("sameboy", "SameBoy"),
            RetroArchCore("gearboy", "Gearboy"),
            RetroArchCore("tgbdual", "TGB Dual")
        ),
        "gba" to listOf(
            RetroArchCore("mgba", "mGBA"),
            RetroArchCore("vba_next", "VBA Next"),
            RetroArchCore("vbam", "VBA-M"),
            RetroArchCore("gpsp", "gpSP")
        ),
        "nds" to listOf(
            RetroArchCore("melonds", "melonDS"),
            RetroArchCore("desmume", "DeSmuME"),
            RetroArchCore("desmume2015", "DeSmuME 2015")
        ),
        "3ds" to listOf(
            RetroArchCore("citra", "Citra"),
            RetroArchCore("citra_canary", "Citra Canary")
        ),
        "genesis" to listOf(
            RetroArchCore("genesis_plus_gx", "Genesis Plus GX"),
            RetroArchCore("picodrive", "PicoDrive")
        ),
        "sms" to listOf(
            RetroArchCore("genesis_plus_gx", "Genesis Plus GX"),
            RetroArchCore("picodrive", "PicoDrive"),
            RetroArchCore("gearsystem", "Gearsystem")
        ),
        "gg" to listOf(
            RetroArchCore("genesis_plus_gx", "Genesis Plus GX"),
            RetroArchCore("gearsystem", "Gearsystem")
        ),
        "scd" to listOf(
            RetroArchCore("genesis_plus_gx", "Genesis Plus GX"),
            RetroArchCore("picodrive", "PicoDrive")
        ),
        "32x" to listOf(
            RetroArchCore("picodrive", "PicoDrive")
        ),
        "psx" to listOf(
            RetroArchCore("pcsx_rearmed", "PCSX ReARMed"),
            RetroArchCore("swanstation", "SwanStation"),
            RetroArchCore("mednafen_psx", "Mednafen PSX"),
            RetroArchCore("mednafen_psx_hw", "Mednafen PSX HW")
        ),
        "psp" to listOf(
            RetroArchCore("ppsspp", "PPSSPP")
        ),
        "saturn" to listOf(
            RetroArchCore("yabasanshiro", "YabaSanshiro"),
            RetroArchCore("yabause", "Yabause"),
            RetroArchCore("mednafen_saturn", "Mednafen Saturn")
        ),
        "dreamcast" to listOf(
            RetroArchCore("flycast", "Flycast")
        ),
        "dc" to listOf(
            RetroArchCore("flycast", "Flycast")
        ),
        "tg16" to listOf(
            RetroArchCore("mednafen_pce_fast", "Mednafen PCE Fast"),
            RetroArchCore("mednafen_pce", "Mednafen PCE")
        ),
        "tgcd" to listOf(
            RetroArchCore("mednafen_pce_fast", "Mednafen PCE Fast"),
            RetroArchCore("mednafen_pce", "Mednafen PCE")
        ),
        "pcfx" to listOf(
            RetroArchCore("mednafen_pcfx", "Mednafen PC-FX")
        ),
        "3do" to listOf(
            RetroArchCore("opera", "Opera")
        ),
        "atari2600" to listOf(
            RetroArchCore("stella", "Stella"),
            RetroArchCore("stella2014", "Stella 2014")
        ),
        "atari5200" to listOf(
            RetroArchCore("atari800", "Atari800"),
            RetroArchCore("a5200", "a5200")
        ),
        "atari7800" to listOf(
            RetroArchCore("prosystem", "ProSystem")
        ),
        "lynx" to listOf(
            RetroArchCore("handy", "Handy"),
            RetroArchCore("mednafen_lynx", "Mednafen Lynx")
        ),
        "ngp" to listOf(
            RetroArchCore("mednafen_ngp", "Mednafen NGP")
        ),
        "ngpc" to listOf(
            RetroArchCore("mednafen_ngp", "Mednafen NGP")
        ),
        "neogeo" to listOf(
            RetroArchCore("fbneo", "FinalBurn Neo"),
            RetroArchCore("fbalpha2012_neogeo", "FB Alpha 2012 Neo Geo")
        ),
        "arcade" to listOf(
            RetroArchCore("fbneo", "FinalBurn Neo"),
            RetroArchCore("mame2003_plus", "MAME 2003-Plus"),
            RetroArchCore("mame2010", "MAME 2010"),
            RetroArchCore("fbalpha2012", "FB Alpha 2012")
        ),
        "msx" to listOf(
            RetroArchCore("bluemsx", "blueMSX"),
            RetroArchCore("fmsx", "fMSX")
        ),
        "msx2" to listOf(
            RetroArchCore("bluemsx", "blueMSX"),
            RetroArchCore("fmsx", "fMSX")
        ),
        "wonderswan" to listOf(
            RetroArchCore("mednafen_wswan", "Mednafen WonderSwan")
        ),
        "wonderswancolor" to listOf(
            RetroArchCore("mednafen_wswan", "Mednafen WonderSwan")
        ),
        "c64" to listOf(
            RetroArchCore("vice_x64", "VICE x64"),
            RetroArchCore("vice_x64sc", "VICE x64 (Accurate)")
        ),
        "vic20" to listOf(
            RetroArchCore("vice_xvic", "VICE VIC-20")
        )
    )

    fun getCoresForPlatform(platformId: String): List<RetroArchCore> {
        val canonical = PlatformDefinitions.getCanonicalSlug(platformId)
        return platformCores[canonical] ?: emptyList()
    }

    fun getDefaultCore(platformId: String): RetroArchCore? {
        val canonical = PlatformDefinitions.getCanonicalSlug(platformId)
        return platformCores[canonical]?.firstOrNull()
    }

    private val emulatorFamilies = listOf(
        EmulatorFamily(
            baseId = "dolphin",
            displayNamePrefix = "Dolphin",
            packagePatterns = listOf("org.dolphinemu.*"),
            supportedPlatforms = setOf("gc", "ngc", "wii"),
            launchAction = Intent.ACTION_MAIN,
            launchConfig = LaunchConfig.Custom(
                activityClass = "org.dolphinemu.dolphinemu.ui.main.MainActivity",
                intentExtras = mapOf("AutoStartFile" to ExtraValue.FileUri)
            ),
            downloadUrl = "https://dolphin-emu.org/download/"
        ),
        EmulatorFamily(
            baseId = "vita3k",
            displayNamePrefix = "Vita3K",
            packagePatterns = listOf("org.vita3k.emulator*"),
            supportedPlatforms = setOf("vita", "psvita"),
            launchAction = Intent.ACTION_MAIN,
            launchConfig = LaunchConfig.Vita3K(),
            downloadUrl = "https://vita3k.org/"
        ),
        EmulatorFamily(
            baseId = "citra",
            displayNamePrefix = "Citra",
            packagePatterns = listOf("org.citra.*", "org.gamerytb.citra.*"),
            supportedPlatforms = setOf("3ds"),
            downloadUrl = "https://citra-emu.org/"
        ),
        EmulatorFamily(
            baseId = "azahar",
            displayNamePrefix = "Azahar",
            packagePatterns = listOf("io.github.lime3ds.*"),
            supportedPlatforms = setOf("3ds"),
            launchConfig = LaunchConfig.Custom(
                activityClass = "org.citra.citra_emu.activities.EmulationActivity",
                intentExtras = mapOf("SelectedGame" to ExtraValue.FilePath)
            ),
            downloadUrl = "https://github.com/azahar-emu/azahar/releases"
        ),
        EmulatorFamily(
            baseId = "azahar_plus",
            displayNamePrefix = "AzaharPlus",
            packagePatterns = listOf("io.github.azaharplus.*"),
            supportedPlatforms = setOf("3ds"),
            launchConfig = LaunchConfig.Custom(
                activityClass = "org.citra.citra_emu.activities.EmulationActivity",
                intentExtras = mapOf("SelectedGame" to ExtraValue.FilePath)
            ),
            downloadUrl = "https://github.com/AzaharPlus/AzaharPlus/releases"
        ),
        EmulatorFamily(
            baseId = "borked3ds",
            displayNamePrefix = "Borked3DS",
            packagePatterns = listOf("io.github.borked3ds.*"),
            supportedPlatforms = setOf("3ds"),
            downloadUrl = "https://github.com/Borked3DS/Borked3DS/releases"
        ),
        EmulatorFamily(
            baseId = "yuzu",
            displayNamePrefix = "Yuzu",
            packagePatterns = listOf("org.yuzu.*"),
            supportedPlatforms = setOf("switch")
        ),
        EmulatorFamily(
            baseId = "sudachi",
            displayNamePrefix = "Sudachi",
            packagePatterns = listOf("org.sudachi.*"),
            supportedPlatforms = setOf("switch")
        ),
        EmulatorFamily(
            baseId = "citron",
            displayNamePrefix = "Citron",
            packagePatterns = listOf("org.citron.*"),
            supportedPlatforms = setOf("switch"),
            downloadUrl = "https://git.citron-emu.org/citron/emulator/releases"
        ),
        EmulatorFamily(
            baseId = "eden",
            displayNamePrefix = "Eden",
            packagePatterns = listOf("dev.eden.*", "dev.legacy.eden*"),
            supportedPlatforms = setOf("switch"),
            downloadUrl = "https://github.com/eden-emulator/Releases/releases"
        ),
        EmulatorFamily(
            baseId = "strato",
            displayNamePrefix = "Strato",
            packagePatterns = listOf("org.stratoemu.*"),
            supportedPlatforms = setOf("switch")
        ),
        EmulatorFamily(
            baseId = "skyline",
            displayNamePrefix = "Skyline",
            packagePatterns = listOf("skyline.*", "emu.skyline.*"),
            supportedPlatforms = setOf("switch")
        ),
        EmulatorFamily(
            baseId = "ryujinx",
            displayNamePrefix = "Ryujinx",
            packagePatterns = listOf("org.ryujinx.*"),
            supportedPlatforms = setOf("switch")
        ),
        EmulatorFamily(
            baseId = "kenjinx",
            displayNamePrefix = "Kenji-NX",
            packagePatterns = listOf("org.kenjinx.*"),
            supportedPlatforms = setOf("switch"),
            downloadUrl = "https://github.com/Kenji-NX/Android-Releases/releases"
        ),
        EmulatorFamily(
            baseId = "ppsspp",
            displayNamePrefix = "PPSSPP",
            packagePatterns = listOf("org.ppsspp.*"),
            supportedPlatforms = setOf("psp"),
            downloadUrl = "https://www.ppsspp.org/download/"
        ),
        EmulatorFamily(
            baseId = "nethersx2",
            displayNamePrefix = "NetherSX2",
            packagePatterns = listOf("xyz.aethersx2.*"),
            supportedPlatforms = setOf("ps2"),
            launchAction = Intent.ACTION_MAIN,
            launchConfig = LaunchConfig.Custom(
                activityClass = "xyz.aethersx2.android.EmulationActivity",
                intentExtras = mapOf("bootPath" to ExtraValue.FilePath)
            ),
            downloadUrl = "https://github.com/Trixarian/NetherSX2-patch/releases"
        ),
        EmulatorFamily(
            baseId = "duckstation",
            displayNamePrefix = "DuckStation",
            packagePatterns = listOf("com.github.stenzek.duckstation*"),
            supportedPlatforms = setOf("psx"),
            launchAction = Intent.ACTION_MAIN,
            launchConfig = LaunchConfig.Custom(
                activityClass = "com.github.stenzek.duckstation.EmulationActivity",
                intentExtras = mapOf("bootPath" to ExtraValue.FilePath)
            ),
            downloadUrl = "https://www.duckstation.org/android/"
        ),
        EmulatorFamily(
            baseId = "melonds",
            displayNamePrefix = "melonDS",
            packagePatterns = listOf("me.magnum.melonds*"),
            supportedPlatforms = setOf("nds"),
            launchConfig = LaunchConfig.Custom(
                activityClass = "me.magnum.melonds.ui.emulator.EmulatorActivity",
                intentExtras = mapOf("PATH" to ExtraValue.FilePath)
            ),
            downloadUrl = "https://github.com/rafaelvcaetano/melonDS-android/releases/tag/nightly-release"
        ),
        EmulatorFamily(
            baseId = "retroarch",
            displayNamePrefix = "RetroArch",
            packagePatterns = listOf("com.retroarch*"),
            supportedPlatforms = setOf(
                "nes", "snes", "n64", "gc", "ngc", "gb", "gbc", "gba", "nds",
                "genesis", "sms", "gg", "scd", "32x",
                "psx", "psp", "saturn", "dreamcast", "dc",
                "tg16", "tgcd", "pcfx", "3do",
                "atari2600", "atari5200", "atari7800", "lynx",
                "ngp", "ngpc", "neogeo",
                "msx", "msx2",
                "wonderswan", "wonderswancolor",
                "arcade",
                "c64", "vic20"
            ),
            launchAction = Intent.ACTION_MAIN,
            launchConfig = LaunchConfig.RetroArch(),
            downloadUrl = "https://www.retroarch.com/?page=platforms"
        )
    )

    fun getEmulatorFamilies(): List<EmulatorFamily> = emulatorFamilies

    private val extensionOptions3ds = listOf(
        ExtensionOption("", "Unchanged"),
        ExtensionOption("3ds", ".3ds"),
        ExtensionOption("cci", ".cci")
    )

    private val platformExtensionOptions: Map<String, List<ExtensionOption>> = mapOf(
        "3ds" to extensionOptions3ds,
        "n3ds" to extensionOptions3ds
    )

    fun getExtensionOptionsForPlatform(platformSlug: String): List<ExtensionOption> {
        val canonical = PlatformDefinitions.getCanonicalSlug(platformSlug)
        return platformExtensionOptions[canonical] ?: emptyList()
    }

    fun hasExtensionOptions(platformSlug: String): Boolean {
        val canonical = PlatformDefinitions.getCanonicalSlug(platformSlug)
        return platformExtensionOptions.containsKey(canonical)
    }

    fun matchesFamily(packageName: String, family: EmulatorFamily): Boolean {
        return family.packagePatterns.any { pattern ->
            val regex = pattern
                .replace(".", "\\.")
                .replace("*", ".*")
            packageName.matches(Regex(regex))
        }
    }

    fun findFamilyForPackage(packageName: String): EmulatorFamily? {
        return emulatorFamilies.find { matchesFamily(packageName, it) }
    }

    fun createDefFromFamily(family: EmulatorFamily, packageName: String): EmulatorDef {
        val suffix = packageName
            .removePrefix(family.packagePatterns.first().substringBefore("*"))
            .replace(".", " ")
            .trim()
            .replaceFirstChar { it.uppercase() }

        val displayName = if (suffix.isNotEmpty() && suffix.lowercase() != family.displayNamePrefix.lowercase()) {
            "${family.displayNamePrefix} ($suffix)"
        } else {
            family.displayNamePrefix
        }

        return EmulatorDef(
            id = "${family.baseId}_${packageName.replace(".", "_")}",
            packageName = packageName,
            displayName = displayName,
            supportedPlatforms = family.supportedPlatforms,
            launchAction = family.launchAction,
            launchConfig = family.launchConfig,
            downloadUrl = family.downloadUrl
        )
    }
}
