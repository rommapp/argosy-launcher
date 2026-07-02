package com.nendo.argosy.data.emulator

import android.content.Intent
import com.nendo.argosy.data.platform.PlatformDefinitions
import com.nendo.argosy.libretro.LibretroCoreRegistry

sealed class ReleaseSource {
    data class GitHub(val repo: String) : ReleaseSource()
    data class Gitea(val baseUrl: String, val repo: String) : ReleaseSource()
    data class GitLab(val baseUrl: String, val projectPath: String) : ReleaseSource()
}

data class EmulatorDef(
    val id: String,
    val packageName: String,
    val displayName: String,
    val supportedPlatforms: Set<String>,
    val launchAction: String = Intent.ACTION_VIEW,
    val launchConfig: LaunchConfig = LaunchConfig.FileUri,
    /**
     * Default method used to dispatch the launch intent. `SHELL` uses `am start`, bypassing
     * caller-side URI permission delegation -- more reliable on scoped storage for emulators that
     * already hold MANAGE_EXTERNAL_STORAGE. Users can override per-platform via the Launch Args
     * modal.
     */
    val defaultLaunchMethod: LaunchMethod = LaunchMethod.INTENT,
    val downloadUrl: String? = null,
    val releaseSource: ReleaseSource? = null,
    val packagePatterns: List<String> = emptyList()
)

data class EmulatorFamily(
    val baseId: String,
    val displayNamePrefix: String,
    val packagePatterns: List<String>,
    val supportedPlatforms: Set<String>,
    val launchAction: String = Intent.ACTION_VIEW,
    val launchConfig: LaunchConfig = LaunchConfig.FileUri,
    val defaultLaunchMethod: LaunchMethod = LaunchMethod.INTENT,
    val downloadUrl: String? = null
)

/**
 * Polymorphic per-subtype defaults for emulator launch dispatch. Centralized here so call sites can
 * read traits (`defaultIntentFlags`, `isCoreSelectable`, etc.) without re-fanning out a `when` block
 * over the sealed class.
 *
 * Categorical flags:
 *  - [defaultIntentFlags]: intent flags applied to a fresh launch when no user override is set.
 *  - [defaultMimeType]: mime applied to data URI when one is set; `null` means "do not set mime".
 *  - [defaultDataBinding]: ROM binding baked into the default launch (e.g. MelonDualDS needs a
 *     FileProvider data URI even when the ROM rides via extras).
 *  - [isInProcess]: this launch runs inside Argosy (the built-in libretro path) -- no external
 *     intent dispatch, no Launch Args modal.
 *  - [isCoreSelectable]: launches choose a libretro core (RetroArch + BuiltIn). Drives the
 *     core-picker UI in game/platform settings.
 *  - [requiresEmulatorKill]: external emulator that does not accept a fresh-launch intent while
 *     a prior session is still alive (Vita3K). The session tracker must `forceStop` before relaunch.
 *  - [bindingDefaults]: per-launch-config binding labels for the Launch Args modal.
 */
sealed class LaunchConfig {

    abstract val defaultIntentFlags: Int

    open val defaultMimeType: String? get() = null

    open val defaultDataBinding: RomBindingFormat? get() = null

    open val isInProcess: Boolean get() = false

    open val isCoreSelectable: Boolean get() = false

    open val requiresEmulatorKill: Boolean get() = false

    abstract fun bindingDefaults(emulator: EmulatorDef): BindingDefaults

    object FileUri : LaunchConfig() {
        override val defaultIntentFlags: Int =
            Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TASK or
                Intent.FLAG_GRANT_READ_URI_PERMISSION

        override val defaultMimeType: String = "application/octet-stream"

        override fun bindingDefaults(emulator: EmulatorDef): BindingDefaults = BindingDefaults(
            data = "FileProvider URI",
            extras = "None",
            clipData = "FileProvider URI"
        )
    }

    data class FilePathExtra(
        val extraKeys: List<String> = listOf("ROM", "rom", "romPath")
    ) : LaunchConfig() {
        override val defaultIntentFlags: Int =
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK

        override fun bindingDefaults(emulator: EmulatorDef): BindingDefaults = BindingDefaults(
            data = "None",
            extras = "Absolute path (${extraKeys.joinToString()})",
            clipData = "None"
        )
    }

    data class RetroArch(
        val activityClass: String = "com.retroarch.browser.retroactivity.RetroActivityFuture"
    ) : LaunchConfig() {
        override val defaultIntentFlags: Int =
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP

        override val isCoreSelectable: Boolean = true

        override fun bindingDefaults(emulator: EmulatorDef): BindingDefaults = BindingDefaults(
            data = "None",
            extras = "Absolute path (ROM)",
            clipData = "None"
        )
    }

    data class Custom(
        val activityClass: String? = null,
        val intentExtras: Map<String, ExtraValue> = emptyMap(),
        val mimeTypeOverride: String? = null,
        val useAbsolutePath: Boolean = false,
        val useFileUri: Boolean = false,
        val useShellLaunch: Boolean = false,
        // Bake a data-URI binding into the default launch command. Equivalent
        // to a user-set Launch Args data-binding override for this emulator,
        // applied when no per-(platform, emulator) override exists. Needed for
        // emulators (e.g. MelonDualDS) that require Intent.setData() with a
        // FileProvider URI alongside their custom action+extras.
        override val defaultDataBinding: RomBindingFormat? = null
    ) : LaunchConfig() {
        override val defaultIntentFlags: Int =
            Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TASK or
                Intent.FLAG_GRANT_READ_URI_PERMISSION

        override val defaultMimeType: String
            get() = mimeTypeOverride ?: "application/octet-stream"

        override fun bindingDefaults(emulator: EmulatorDef): BindingDefaults {
            val action = emulator.launchAction
            return when {
                useFileUri && action == Intent.ACTION_VIEW -> BindingDefaults(
                    data = "Absolute path (shell)",
                    extras = "None",
                    clipData = "None"
                )
                useShellLaunch && action == Intent.ACTION_VIEW -> BindingDefaults(
                    data = "FileProvider URI (shell)",
                    extras = "None",
                    clipData = "None"
                )
                action == Intent.ACTION_VIEW -> BindingDefaults(
                    data = "FileProvider URI",
                    extras = "None",
                    clipData = "FileProvider URI"
                )
                else -> {
                    val hasDocUri = intentExtras.values.any { it is ExtraValue.DocumentUri }
                    val hasFileUri = intentExtras.values.any { it is ExtraValue.FileUri }
                    val hasFileUriString = intentExtras.values.any { it is ExtraValue.FileUriString }
                    val hasFilePath = intentExtras.values.any { it is ExtraValue.FilePath }
                    val hasAbsPath = useAbsolutePath

                    val extrasLabel = when {
                        hasDocUri -> "Document URI (SAF)"
                        hasFileUri -> "FileProvider URI"
                        hasFileUriString -> "FileProvider URI (string)"
                        hasFilePath || hasAbsPath -> "Absolute path"
                        else -> "None"
                    }
                    val extraKeys = intentExtras.entries
                        .filter {
                            it.value is ExtraValue.FilePath ||
                                it.value is ExtraValue.FileUri ||
                                it.value is ExtraValue.FileUriString ||
                                it.value is ExtraValue.DocumentUri
                        }
                        .map { it.key }
                    val extrasWithKeys = if (extraKeys.isNotEmpty()) {
                        "$extrasLabel (${extraKeys.joinToString()})"
                    } else if (hasAbsPath) {
                        "$extrasLabel (path/file/filePath)"
                    } else {
                        extrasLabel
                    }

                    val clipLabel = when {
                        hasDocUri -> "Document URI"
                        hasFileUri -> "FileProvider URI"
                        else -> "None"
                    }

                    BindingDefaults(
                        data = "None",
                        extras = extrasWithKeys,
                        clipData = clipLabel
                    )
                }
            }
        }
    }

    data class CustomScheme(
        val scheme: String,
        val authority: String,
        val pathPrefix: String = ""
    ) : LaunchConfig() {
        override val defaultIntentFlags: Int =
            Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TASK or
                Intent.FLAG_ACTIVITY_NO_HISTORY

        override fun bindingDefaults(emulator: EmulatorDef): BindingDefaults = BindingDefaults(
            data = "$scheme:// scheme URI",
            extras = "None",
            clipData = "None",
            dataLocked = true
        )
    }

    data class Vita3K(
        val activityClass: String = "org.vita3k.emulator.Emulator"
    ) : LaunchConfig() {
        override val defaultIntentFlags: Int =
            Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TASK or
                Intent.FLAG_ACTIVITY_NO_HISTORY

        override val requiresEmulatorKill: Boolean = true

        override fun bindingDefaults(emulator: EmulatorDef): BindingDefaults = BindingDefaults(
            data = "None",
            extras = "Title ID (AppStartParameters)",
            clipData = "None",
            extrasLocked = true
        )
    }

    object BuiltIn : LaunchConfig() {
        override val defaultIntentFlags: Int = Intent.FLAG_ACTIVITY_NEW_TASK

        override val isInProcess: Boolean = true

        override val isCoreSelectable: Boolean = true

        override fun bindingDefaults(emulator: EmulatorDef): BindingDefaults = BindingDefaults(
            data = "N/A (in-process)",
            extras = "N/A (in-process)",
            clipData = "N/A (in-process)"
        )
    }

    object ScummVM : LaunchConfig() {
        override val defaultIntentFlags: Int =
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK

        override fun bindingDefaults(emulator: EmulatorDef): BindingDefaults = BindingDefaults(
            data = "scummvm: game ID",
            extras = "None",
            clipData = "None",
            dataLocked = true
        )
    }
}

/**
 * Per-launch-config UI labels for the Launch Args modal default-row text. Lives in `data/emulator/`
 * because each [LaunchConfig] subtype owns its own labels (avoids a separate `when` in UI code).
 */
data class BindingDefaults(
    val data: String,
    val extras: String,
    val clipData: String,
    val dataLocked: Boolean = false,
    val extrasLocked: Boolean = false
)

sealed class ExtraValue {
    object FilePath : ExtraValue()
    object FileUri : ExtraValue()
    object FileUriString : ExtraValue()
    object DocumentUri : ExtraValue()
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

    const val BUILTIN_PACKAGE = "argosy.builtin.libretro"

    private val builtinEmulator = EmulatorDef(
        id = "builtin",
        packageName = BUILTIN_PACKAGE,
        displayName = "Built-in",
        supportedPlatforms = LibretroCoreRegistry.getSupportedPlatforms(),
        launchConfig = LaunchConfig.BuiltIn
    )

    private val emulators = listOf(
        builtinEmulator,
        EmulatorDef(
            id = "retroarch",
            packageName = "com.retroarch",
            displayName = "RetroArch",
            supportedPlatforms = setOf(
                "nes", "snes", "n64", "gc", "wii", "gb", "gbc", "gba", "nds", "3ds",
                "genesis", "sms", "sg1000", "gg", "scd", "32x",
                "psx", "psp", "saturn", "dreamcast",
                "tg16", "tgcd", "pcfx", "3do",
                "atari2600", "atari5200", "atari7800", "atari8bit", "lynx", "jaguar",
                "ngp", "ngpc", "neogeo", "neogeocd",
                "msx", "msx2", "coleco",
                "wonderswan", "wsc", "pokemini",
                "arcade", "supergrafx",
                "c64", "vic20", "dos", "zx", "pc9800", "amstradcpc", "pico8", "scummvm"
            ),
            launchAction = Intent.ACTION_MAIN,
            launchConfig = LaunchConfig.RetroArch(),
            downloadUrl = "https://www.retroarch.com/?page=platforms"
        ),
        EmulatorDef(
            id = "retroarch_64",
            packageName = "com.retroarch.aarch64",
            displayName = "RetroArch (64-bit)",
            supportedPlatforms = setOf(
                "nes", "snes", "n64", "gc", "wii", "gb", "gbc", "gba", "nds", "3ds",
                "genesis", "sms", "sg1000", "gg", "scd", "32x",
                "psx", "psp", "saturn", "dreamcast",
                "tg16", "tgcd", "pcfx", "3do",
                "atari2600", "atari5200", "atari7800", "atari8bit", "lynx", "jaguar",
                "ngp", "ngpc", "neogeo", "neogeocd",
                "msx", "msx2", "coleco",
                "wonderswan", "wsc", "pokemini",
                "arcade", "supergrafx",
                "c64", "vic20", "dos", "zx", "pc9800", "amstradcpc", "pico8", "scummvm"
            ),
            launchAction = Intent.ACTION_MAIN,
            launchConfig = LaunchConfig.RetroArch(),
            downloadUrl = "https://www.retroarch.com/?page=platforms"
        ),

        EmulatorDef(
            id = "mupen64plus_fz",
            packageName = "org.mupen64plusae.v3.fzurita",
            displayName = "Mupen64Plus FZ",
            supportedPlatforms = setOf("n64"),
            launchAction = Intent.ACTION_VIEW,
            launchConfig = LaunchConfig.Custom(
                activityClass = "paulscode.android.mupen64plusae.SplashActivity"
            ),
            downloadUrl = "https://play.google.com/store/apps/details?id=org.mupen64plusae.v3.fzurita"
        ),
        EmulatorDef(
            id = "m64pro_fzx_plus",
            packageName = "com.m64.fx.plus.emulate",
            displayName = "M64Pro FZX Plus+",
            supportedPlatforms = setOf("n64"),
            launchAction = Intent.ACTION_VIEW,
            launchConfig = LaunchConfig.Custom(
                activityClass = "paulscode.android.mupen64plusae.SplashActivity"
            ),
            downloadUrl = "https://play.google.com/store/apps/details?id=com.m64.fx.plus.emulate"
        ),
        EmulatorDef(
            id = "picpic",
            packageName = "com.sa_moo_rai.picpic",
            displayName = "PicPic",
            supportedPlatforms = setOf("pico8"),
            launchAction = Intent.ACTION_VIEW,
            launchConfig = LaunchConfig.Custom(
                activityClass = "com.sa_moo_rai.picpic.MainActivity"
            ),
            downloadUrl = "https://play.google.com/store/apps/details?id=com.sa_moo_rai.picpic"
        ),

        EmulatorDef(
            id = "dolphin",
            packageName = "org.dolphinemu.dolphinemu",
            displayName = "Dolphin",
            supportedPlatforms = setOf("gc", "wii"),
            launchAction = Intent.ACTION_MAIN,
            launchConfig = LaunchConfig.Custom(
                activityClass = "org.dolphinemu.dolphinemu.ui.main.MainActivity",
                intentExtras = mapOf("AutoStartFile" to ExtraValue.FileUri)
            ),
            defaultLaunchMethod = LaunchMethod.SHELL,
            downloadUrl = "https://play.google.com/store/apps/details?id=org.dolphinemu.dolphinemu"
        ),
        EmulatorDef(
            id = "dolphin_handheld",
            packageName = "org.dolphinemu.handheld",
            displayName = "Dolphin (Handheld)",
            supportedPlatforms = setOf("gc", "wii"),
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
            downloadUrl = "https://github.com/SSimco/Cemu/releases",
            releaseSource = ReleaseSource.GitHub("SSimco/Cemu")
        ),
        // NOTE: Dual-screen fork uses same package name as official Cemu - only one can be installed
        EmulatorDef(
            id = "cemu_dualscreen",
            packageName = "info.cemu.cemu",
            displayName = "Cemu (Dual Screen)",
            supportedPlatforms = setOf("wiiu"),
            downloadUrl = "https://github.com/SapphireRhodonite/Cemu/releases",
            releaseSource = ReleaseSource.GitHub("SapphireRhodonite/Cemu")
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
            downloadUrl = "https://github.com/weihuoya/citra/releases",
            releaseSource = ReleaseSource.GitHub("weihuoya/citra")
        ),
        // NOTE: Azahar vanilla APK changed package from io.github.lime3ds.android
        // to org.azahar_emu.azahar. Play Store variant may keep old package.
        // Uses Citra's internal namespace for activities.
        EmulatorDef(
            id = "azahar",
            packageName = "org.azahar_emu.azahar",
            displayName = "Azahar",
            supportedPlatforms = setOf("3ds"),
            launchConfig = LaunchConfig.Custom(
                activityClass = "org.citra.citra_emu.activities.EmulationActivity",
                intentExtras = mapOf("SelectedGame" to ExtraValue.FilePath)
            ),
            defaultLaunchMethod = LaunchMethod.SHELL,
            downloadUrl = "https://github.com/azahar-emu/azahar/releases",
            releaseSource = ReleaseSource.GitHub("azahar-emu/azahar")
        ),
        EmulatorDef(
            id = "borked3ds",
            packageName = "io.github.borked3ds.android",
            displayName = "Borked3DS",
            supportedPlatforms = setOf("3ds"),
            launchConfig = LaunchConfig.Custom(
                activityClass = "io.github.borked3ds.android.activities.EmulationActivity"
            ),
            downloadUrl = "https://github.com/Borked3DS/Borked3DS/releases",
            releaseSource = ReleaseSource.GitHub("Borked3DS/Borked3DS")
        ),
        EmulatorDef(
            id = "yuzu",
            packageName = "org.yuzu.yuzu_emu",
            displayName = "Yuzu",
            supportedPlatforms = setOf("switch"),
            launchConfig = LaunchConfig.Custom(
                activityClass = "org.yuzu.yuzu_emu.activities.EmulationActivity",
            )
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
            launchConfig = LaunchConfig.Custom(
                activityClass = "org.yuzu.yuzu_emu.activities.EmulationActivity",
            ),
            defaultLaunchMethod = LaunchMethod.SHELL,
            downloadUrl = "https://git.eden-emu.dev/eden-emu/eden/releases",
            releaseSource = ReleaseSource.Gitea("https://git.eden-emu.dev", "eden-emu/eden")
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
            launchConfig = LaunchConfig.Custom(
                activityClass = "org.citron.citron_emu.activities.EmulationActivity",
            )
        ),
        // NOTE: Kenji-NX is an active fork of Ryujinx for Android
        EmulatorDef(
            id = "kenjinx",
            packageName = "org.kenjinx.android",
            displayName = "Kenji-NX",
            supportedPlatforms = setOf("switch"),
            downloadUrl = "https://git.ryujinx.app/kenji-nx/android/-/releases",
            releaseSource = ReleaseSource.GitLab("https://git.ryujinx.app", "kenji-nx/android")
        ),
        EmulatorDef(
            id = "sudachi",
            packageName = "org.sudachi.sudachi_emu",
            displayName = "Sudachi",
            supportedPlatforms = setOf("switch"),
            launchConfig = LaunchConfig.Custom(
                activityClass = "org.sudachi.sudachi_emu.activities.EmulationActivity",
            )
        ),
        EmulatorDef(
            id = "drastic",
            packageName = "com.dsemu.drastic",
            displayName = "DraStic",
            supportedPlatforms = setOf("nds"),
            launchConfig = LaunchConfig.Custom(
                activityClass = "com.dsemu.drastic.DraSticActivity",
                useShellLaunch = true,
                useFileUri = true
            ),
            downloadUrl = "https://play.google.com/store/apps/details?id=com.dsemu.drastic"
        ),
        EmulatorDef(
            id = "melonds",
            packageName = "me.magnum.melonds",
            displayName = "melonDS",
            supportedPlatforms = setOf("nds"),
            launchAction = "me.magnum.melonds.LAUNCH_ROM",
            launchConfig = LaunchConfig.Custom(
                activityClass = "me.magnum.melonds.ui.emulator.EmulatorActivity",
                intentExtras = mapOf("uri" to ExtraValue.FileUri),
                defaultDataBinding = RomBindingFormat.FILE_PROVIDER
            ),
            downloadUrl = "https://github.com/rafaelvcaetano/melonDS-android/releases/tag/nightly-release",
            releaseSource = ReleaseSource.GitHub("rafaelvcaetano/melonDS-android")
        ),
        EmulatorDef(
            id = "melondualds",
            packageName = "me.magnum.melondualds",
            displayName = "MelonDualDS",
            supportedPlatforms = setOf("nds"),
            launchAction = "me.magnum.melondualds.LAUNCH_ROM",
            launchConfig = LaunchConfig.Custom(
                activityClass = "me.magnum.melonds.ui.emulator.EmulatorActivity",
                intentExtras = mapOf("uri" to ExtraValue.FileUri),
                defaultDataBinding = RomBindingFormat.FILE_PROVIDER
            ),
            downloadUrl = "https://github.com/SapphireRhodonite/melonDS-android/releases",
            releaseSource = ReleaseSource.GitHub("SapphireRhodonite/melonDS-android")
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
            id = "linkboy",
            packageName = "com.pixelrespawn.linkboy",
            displayName = "LinkBoy",
            supportedPlatforms = setOf("gb", "gbc", "gba"),
            launchConfig = LaunchConfig.CustomScheme(
                scheme = "linkboy",
                authority = "emulator"
            ),
            downloadUrl = "https://play.google.com/store/apps/details?id=com.pixelrespawn.linkboy"
        ),
        EmulatorDef(
            id = "super_zsnes",
            packageName = "com.zsnes.superzsnes",
            displayName = "SUPER ZSNES",
            supportedPlatforms = setOf("snes", "sfc"),
            downloadUrl = "https://play.google.com/store/apps/details?id=com.zsnes.superzsnes"
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
                intentExtras = mapOf("bootPath" to ExtraValue.FileUriString)
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
                intentExtras = mapOf("bootPath" to ExtraValue.FileUriString)
            ),
            downloadUrl = "https://github.com/Trixarian/NetherSX2-patch/releases",
            releaseSource = ReleaseSource.GitHub("Trixarian/NetherSX2-patch")
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
                intentExtras = mapOf("bootPath" to ExtraValue.FileUriString)
            )
        ),
        EmulatorDef(
            id = "armsx2",
            packageName = "come.nanodata.armsx2",
            displayName = "ARMSX2",
            supportedPlatforms = setOf("ps2"),
            launchConfig = LaunchConfig.Custom(
                activityClass = "kr.co.iefriends.pcsx2.activities.MainActivity"
            ),
            defaultLaunchMethod = LaunchMethod.SHELL,
            downloadUrl = "https://github.com/ARMSX2/ARMSX2/releases",
            releaseSource = ReleaseSource.GitHub("ARMSX2/ARMSX2")
        ),
        EmulatorDef(
            id = "pcsx2",
            packageName = "net.pcsx2.emulator",
            displayName = "PCSX2",
            supportedPlatforms = setOf("ps2"),
            downloadUrl = "https://github.com/PCSX2/pcsx2/releases",
            releaseSource = ReleaseSource.GitHub("PCSX2/pcsx2")
        ),
        EmulatorDef(
            id = "psx2",
            packageName = "com.izzy2lost.psx2",
            displayName = "PSX2",
            supportedPlatforms = setOf("ps2"),
            downloadUrl = "https://github.com/izzy2lost/PSX2/releases",
            releaseSource = ReleaseSource.GitHub("izzy2lost/PSX2")
        ),
        EmulatorDef(
            id = "ppsspp",
            packageName = "org.ppsspp.ppsspp",
            displayName = "PPSSPP",
            supportedPlatforms = setOf("psp"),
            launchConfig = LaunchConfig.Custom(
                activityClass = "org.ppsspp.ppsspp.PpssppActivity",
            ),
            defaultLaunchMethod = LaunchMethod.SHELL,
            downloadUrl = "https://play.google.com/store/apps/details?id=org.ppsspp.ppsspp"
        ),
        EmulatorDef(
            id = "ppsspp_gold",
            packageName = "org.ppsspp.ppssppgold",
            displayName = "PPSSPP Gold",
            supportedPlatforms = setOf("psp"),
            launchConfig = LaunchConfig.Custom(
                activityClass = "org.ppsspp.ppssppgold.PpssppActivity",
            ),
            defaultLaunchMethod = LaunchMethod.SHELL,
            downloadUrl = "https://play.google.com/store/apps/details?id=org.ppsspp.ppssppgold"
        ),
        EmulatorDef(
            id = "ppsspp_legacy",
            packageName = "org.ppsspp.ppsspplegacy",
            displayName = "PPSSPP Legacy",
            supportedPlatforms = setOf("psp"),
            launchConfig = LaunchConfig.Custom(
                activityClass = "org.ppsspp.ppsspp.PpssppActivity",
            ),
            defaultLaunchMethod = LaunchMethod.SHELL,
            downloadUrl = "https://www.ppsspp.org/docs/reference/legacy-edition/"
        ),
        EmulatorDef(
            id = "vita3k",
            packageName = "org.vita3k.emulator",
            displayName = "Vita3K",
            supportedPlatforms = setOf("vita"),
            launchAction = Intent.ACTION_MAIN,
            launchConfig = LaunchConfig.Vita3K(),
            downloadUrl = "https://github.com/Vita3K/Vita3K-Android/releases",
            releaseSource = ReleaseSource.GitHub("Vita3K/Vita3K-Android")
        ),
        EmulatorDef(
            id = "vita3k-zx",
            packageName = "org.vita3k.emulator.ikhoeyZX",
            displayName = "Vita3K ZX",
            supportedPlatforms = setOf("vita"),
            launchAction = Intent.ACTION_MAIN,
            launchConfig = LaunchConfig.Vita3K(),
            downloadUrl = "https://github.com/ikhoeyZX/Vita3K-Android/releases",
            releaseSource = ReleaseSource.GitHub("ikhoeyZX/Vita3K-Android")
        ),

        // NOTE: Redream has known Android 13+ issues - explicit activity launches fail
        // https://github.com/TapiocaFox/Daijishou/issues/487
        // https://github.com/TapiocaFox/Daijishou/issues/579
        EmulatorDef(
            id = "redream",
            packageName = "io.recompiled.redream",
            displayName = "Redream",
            supportedPlatforms = setOf("dreamcast"),
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
            supportedPlatforms = setOf("dreamcast", "naomi", "atomiswave"),
            launchConfig = LaunchConfig.Custom(
                activityClass = "com.flycast.emulator.MainActivity"
            ),
            defaultLaunchMethod = LaunchMethod.SHELL,
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
            id = "yabasanshiro",
            packageName = "org.devmiyax.yabasanshioro2",
            displayName = "Yaba Sanshiro 2",
            supportedPlatforms = setOf("saturn"),
            launchConfig = LaunchConfig.Custom(
                activityClass = "org.uoyabause.android.Yabause",
                intentExtras = mapOf(
                    "org.uoyabause.android.FileNameEx" to ExtraValue.FilePath
                )
            ),
            downloadUrl = "https://play.google.com/store/apps/details?id=org.devmiyax.yabasanshioro2"
        ),
        EmulatorDef(
            id = "yabasanshiro_pro",
            packageName = "org.devmiyax.yabasanshioro2.pro",
            displayName = "Yaba Sanshiro 2 Pro",
            supportedPlatforms = setOf("saturn"),
            launchConfig = LaunchConfig.Custom(
                activityClass = "org.uoyabause.android.Yabause",
                intentExtras = mapOf(
                    "org.uoyabause.android.FileNameEx" to ExtraValue.FilePath
                )
            )
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
            launchConfig = LaunchConfig.ScummVM,
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

        EmulatorDef(
            id = "ax360e",
            packageName = "aenu.ax360e",
            displayName = "AX360E",
            supportedPlatforms = setOf("xbox360"),
            launchAction = "aenu.intent.action.AX360E",
            launchConfig = LaunchConfig.Custom(
                activityClass = "aenu.ax360e.EmulatorActivity",
                intentExtras = mapOf("game_uri" to ExtraValue.FileUri)
            ),
            downloadUrl = "https://play.google.com/store/apps/details?id=aenu.ax360e"
        ),
        EmulatorDef(
            id = "ax360e_free",
            packageName = "aenu.ax360e.free",
            displayName = "AX360E (Free)",
            supportedPlatforms = setOf("xbox360"),
            launchAction = "aenu.intent.action.AX360E",
            launchConfig = LaunchConfig.Custom(
                activityClass = "aenu.ax360e.EmulatorActivity",
                intentExtras = mapOf("game_uri" to ExtraValue.FileUri)
            ),
            downloadUrl = "https://play.google.com/store/apps/details?id=aenu.ax360e.free"
        ),

        EmulatorDef(
            id = "aps3e",
            packageName = "aenu.aps3e",
            displayName = "aPS3e",
            supportedPlatforms = setOf("ps3"),
            launchAction = "aenu.intent.action.APS3E",
            launchConfig = LaunchConfig.Custom(
                activityClass = "aenu.aps3e.EmulatorActivity",
                intentExtras = mapOf("iso_uri" to ExtraValue.FileUri)
            ),
            downloadUrl = "https://play.google.com/store/apps/details?id=aenu.aps3e"
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
            downloadUrl = "https://github.com/Producdevity/gamehub-lite/releases",
            releaseSource = ReleaseSource.GitHub("Producdevity/gamehub-lite")
        ),
        EmulatorDef(
            id = "gamenative",
            packageName = "app.gamenative",
            displayName = "GameNative",
            supportedPlatforms = setOf("steam"),
            downloadUrl = "https://github.com/utkarshdalal/GameNative/releases",
            releaseSource = ReleaseSource.GitHub("utkarshdalal/GameNative")
        )
    )

    private val emulatorMap = emulators.associateBy { it.id }
    private val packageMap = emulators.associateBy { it.packageName }

    fun getAll(): List<EmulatorDef> = emulators

    fun getById(id: String): EmulatorDef? = emulatorMap[id]

    fun getByPackage(packageName: String): EmulatorDef? = packageMap[packageName]

    fun isKnownPackage(packageName: String): Boolean = packageMap.containsKey(packageName)

    /**
     * Synthesize an [EmulatorDef] for an ad-hoc app binding. The `id` is deterministic per
     * package so [EmulatorLaunchArgsEntity] overrides persist across restarts.
     */
    fun synthesizeAdHocEmulatorDef(
        packageName: String,
        displayName: String,
        platformSlug: String
    ): EmulatorDef = EmulatorDef(
        id = "adhoc_$packageName",
        packageName = packageName,
        displayName = displayName,
        supportedPlatforms = setOf(platformSlug),
        launchAction = Intent.ACTION_VIEW,
        launchConfig = LaunchConfig.FileUri
    )

    fun getAlternatives(packageName: String): List<EmulatorDef> =
        emulators.filter { it.packageName == packageName }

    fun getUpdateCheckable(): List<EmulatorDef> = emulators.filter { it.releaseSource != null }

    fun getForPlatform(platformId: String): List<EmulatorDef> {
        val canonical = PlatformDefinitions.getCanonicalSlug(platformId)
        return emulators.filter { canonical in it.supportedPlatforms }
    }

    fun getRecommendedEmulators(): Map<String, List<String>> = mapOf(
        "psx" to listOf("builtin", "duckstation", "retroarch", "retroarch_64"),
        "ps2" to listOf("nethersx2", "armsx2", "psx2", "pcsx2"),
        "psp" to listOf("builtin", "ppsspp", "ppsspp_gold", "ppsspp_legacy", "retroarch", "retroarch_64"),
        "vita" to listOf("vita3k-zx", "vita3k"),
        "n64" to listOf("builtin", "mupen64plus_fz", "retroarch", "retroarch_64"),
        "nds" to listOf("builtin", "drastic", "melonds", "melondualds", "retroarch", "retroarch_64"),
        "3ds" to listOf("azahar", "citra_mmj", "borked3ds", "citra", "retroarch", "retroarch_64"),
        "gc" to listOf("dolphin", "dolphin_handheld", "retroarch", "retroarch_64"),
        "wii" to listOf("dolphin", "dolphin_handheld", "retroarch", "retroarch_64"),
        "wiiu" to listOf("cemu", "cemu_dualscreen"),
        "switch" to listOf("eden", "citron", "sudachi", "ryujinx", "yuzu", "strato", "skyline"),
        "gba" to listOf("builtin", "pizza_boy_gba", "linkboy", "retroarch", "retroarch_64"),
        "gb" to listOf("builtin", "pizza_boy_gb", "linkboy", "retroarch", "retroarch_64"),
        "gbc" to listOf("builtin", "pizza_boy_gb", "linkboy", "retroarch", "retroarch_64"),
        "nes" to listOf("builtin", "retroarch", "retroarch_64"),
        "snes" to listOf("builtin", "retroarch", "retroarch_64"),
        "genesis" to listOf("builtin", "md_emu", "retroarch", "retroarch_64"),
        "sms" to listOf("builtin", "md_emu", "retroarch", "retroarch_64"),
        "sg1000" to listOf("builtin", "md_emu", "retroarch", "retroarch_64"),
        "gg" to listOf("builtin", "md_emu", "retroarch", "retroarch_64"),
        "scd" to listOf("builtin", "md_emu", "retroarch", "retroarch_64"),
        "32x" to listOf("builtin", "md_emu", "retroarch", "retroarch_64"),
        "dreamcast" to listOf("redream", "flycast"),
        "saturn" to listOf("builtin", "yabasanshiro", "yabasanshiro_pro", "saturn_emu", "retroarch", "retroarch_64"),
        "arcade" to listOf("flycast", "mame4droid", "fbalpha", "retroarch", "retroarch_64"),
        "neogeo" to listOf("fbalpha", "retroarch", "retroarch_64"),
        "dos" to listOf("magic_dosbox", "dosbox_turbo"),
        "scummvm" to listOf("scummvm", "retroarch", "retroarch_64"),
        "ps3" to listOf("aps3e"),
        "atari2600" to listOf("builtin", "retroarch", "retroarch_64"),
        "lynx" to listOf("retroarch", "retroarch_64"),
        "tg16" to listOf("builtin", "retroarch", "retroarch_64"),
        "tgcd" to listOf("retroarch", "retroarch_64"),
        "3do" to listOf("builtin", "retroarch", "retroarch_64"),
        "ngp" to listOf("retroarch", "retroarch_64"),
        "ngpc" to listOf("retroarch", "retroarch_64"),
        "wonderswan" to listOf("builtin", "retroarch", "retroarch_64"),
        "wsc" to listOf("builtin", "retroarch", "retroarch_64"),
        "pokemini" to listOf("builtin", "retroarch", "retroarch_64"),
        "xbox360" to listOf("ax360e", "ax360e_free"),
        "steam" to listOf("gamehub", "gamehub_lite", "gamenative"),
        "c64" to listOf("retroarch", "retroarch_64"),
        "vic20" to listOf("retroarch", "retroarch_64"),
        "pc9800" to listOf("retroarch", "retroarch_64"),
        "pico8" to listOf("builtin", "picpic", "retroarch", "retroarch_64")
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
        "dos" to "dosbox_pure",
        "msx" to "bluemsx",
        "msx2" to "bluemsx",
        "wonderswan" to "mednafen_wswan",
        "wsc" to "mednafen_wswan",
        "pokemini" to "pokemini",
        "c64" to "vice_x64",
        "vic20" to "vice_xvic",
        "pc9800" to "np2kai",
        "pico8" to "fake08"
    )

    fun getRetroArchCorePatterns(): Map<String, List<String>> = mapOf(
        "nes" to listOf("fceumm", "nestopia", "quicknes", "mesen"),
        "snes" to listOf("snes9x", "bsnes", "mesen"),
        "n64" to listOf("mupen64plus_next", "parallel_n64"),
        "gc" to listOf("dolphin"),
        "ngc" to listOf("dolphin"),
        "wii" to listOf("dolphin"),
        "gb" to listOf("gambatte", "mgba", "sameboy", "gearboy", "tgbdual"),
        "gbc" to listOf("gambatte", "mgba", "sameboy", "gearboy", "tgbdual"),
        "gba" to listOf("mgba", "vba", "gpsp"),
        "nds" to listOf("melondsds", "melonds", "desmume"),
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
        "neogeocd" to listOf("neocd", "fbneo"),
        "supergrafx" to listOf("mednafen_supergrafx"),
        "arcade" to listOf("fbneo", "mame", "fbalpha"),
        "amstradcpc" to listOf("cap32", "crocods"),
        "dos" to listOf("dosbox_pure", "dosbox_core", "dosbox_svn"),
        "msx" to listOf("bluemsx", "fmsx"),
        "msx2" to listOf("bluemsx", "fmsx"),
        "wonderswan" to listOf("mednafen_wswan"),
        "wsc" to listOf("mednafen_wswan"),
        "pokemini" to listOf("pokemini"),
        "pico8" to listOf("fake08", "retro8"),
        "c64" to listOf("vice_x64", "vice_x64sc"),
        "vic20" to listOf("vice_xvic"),
        "pc9800" to listOf("np2kai")
    )

    private val platformCores: Map<String, List<RetroArchCore>> = mapOf(
        "scummvm" to listOf(RetroArchCore("scummvm", "ScummVM")),
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
            RetroArchCore("mupen64plus_next_gles2", "Mupen64Plus-Next (GLES2)"),
            RetroArchCore("parallel_n64", "ParaLLEl N64")
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
            RetroArchCore("melondsds", "melonDS DS"),
            RetroArchCore("melonds", "melonDS"),
            RetroArchCore("desmume", "DeSmuME"),
            RetroArchCore("desmume2015", "DeSmuME 2015"),
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
        "dos" to listOf(
            RetroArchCore("dosbox_pure", "DOSBox Pure"),
            RetroArchCore("dosbox_core", "DOSBox-core"),
            RetroArchCore("dosbox_svn", "DOSBox-SVN")
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
        "neogeocd" to listOf(
            RetroArchCore("neocd", "NeoCD"),
            RetroArchCore("fbneo", "FinalBurn Neo")
        ),
        "supergrafx" to listOf(
            RetroArchCore("mednafen_supergrafx", "Beetle SuperGrafx")
        ),
        "amstradcpc" to listOf(
            RetroArchCore("cap32", "Caprice32"),
            RetroArchCore("crocods", "CrocoDS")
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
        "wsc" to listOf(
            RetroArchCore("mednafen_wswan", "Mednafen WonderSwan")
        ),
        "pokemini" to listOf(
            RetroArchCore("pokemini", "PokeMini")
        ),
        "c64" to listOf(
            RetroArchCore("vice_x64", "VICE x64"),
            RetroArchCore("vice_x64sc", "VICE x64 (Accurate)")
        ),
        "vic20" to listOf(
            RetroArchCore("vice_xvic", "VICE VIC-20")
        ),
        "jaguar" to listOf(
            RetroArchCore("virtualjaguar", "Virtual Jaguar")
        ),
        "atari8bit" to listOf(
            RetroArchCore("atari800", "Atari800")
        ),
        "coleco" to listOf(
            RetroArchCore("bluemsx", "blueMSX"),
            RetroArchCore("gearcoleco", "Gearcoleco")
        ),
        "zx" to listOf(
            RetroArchCore("fuse", "Fuse"),
            RetroArchCore("81", "EightyOne")
        ),
        "pc9800" to listOf(
            RetroArchCore("np2kai", "Neko Project II Kai")
        ),
        "pico8" to listOf(
            RetroArchCore("fake08", "FAKE-08"),
            RetroArchCore("retro8", "Retro8")
        )
    )

    fun getCoresForPlatform(platformId: String): List<RetroArchCore> {
        val canonical = PlatformDefinitions.getCanonicalSlug(platformId)
        val curated = platformCores[canonical] ?: emptyList()
        val curatedIds = curated.map { it.id }.toSet()
        val builtIn = com.nendo.argosy.libretro.LibretroCoreRegistry
            .getCoresForPlatform(canonical)
            .filter { it.coreId !in curatedIds }
            .map { RetroArchCore(it.coreId, it.displayName) }
        return curated + builtIn
    }

    fun getDefaultCore(platformId: String): RetroArchCore? =
        getCoresForPlatform(platformId).firstOrNull()

    /**
     * Cores selectable for a game given its emulator mode: the built-in path can only run
     * cores from [LibretroCoreRegistry]; external RetroArch uses the curated RetroArch cores.
     */
    fun getSelectableCores(platformId: String, isBuiltIn: Boolean): List<RetroArchCore> {
        val canonical = PlatformDefinitions.getCanonicalSlug(platformId)
        return if (isBuiltIn) {
            com.nendo.argosy.libretro.LibretroCoreRegistry.getCoresForPlatform(canonical)
                .map { RetroArchCore(it.coreId, it.displayName) }
        } else {
            platformCores[canonical] ?: emptyList()
        }
    }

    fun getDefaultSelectableCore(platformId: String, isBuiltIn: Boolean): RetroArchCore? =
        if (isBuiltIn) {
            val canonical = PlatformDefinitions.getCanonicalSlug(platformId)
            com.nendo.argosy.libretro.LibretroCoreRegistry.getDefaultCoreForPlatform(canonical)
                ?.let { RetroArchCore(it.coreId, it.displayName) }
        } else {
            getSelectableCores(platformId, false).firstOrNull()
        }

    private val retroArchSaveDirByCore: Map<String, String> = mapOf(
        "snes9x2010" to "Snes9x 2010",
        "bsnes2014_accuracy" to "bsnes 2014 Accuracy",
        "mupen64plus_next" to "Mupen64Plus-Next",
        "mupen64plus_next_gles3" to "Mupen64Plus-Next",
        "mupen64plus_next_gles2" to "Mupen64Plus-Next",
        "parallel_n64" to "ParaLLEl N64",
        "tgbdual" to "TGB Dual",
        "vba_next" to "VBA Next",
        "vbam" to "VBA-M",
        "desmume2015" to "DeSmuME 2015",
        "melondsds" to "melonDS DS",
        "genesis_plus_gx" to "Genesis Plus GX",
        "pcsx_rearmed" to "PCSX-ReARMed",
        "mednafen_psx" to "Beetle PSX",
        "mednafen_psx_hw" to "Beetle PSX HW",
        "mednafen_saturn" to "Beetle Saturn",
        "mednafen_pce" to "Beetle PCE",
        "mednafen_pce_fast" to "Beetle PCE Fast",
        "mednafen_pcfx" to "Beetle PC-FX",
        "mednafen_lynx" to "Beetle Lynx",
        "mednafen_ngp" to "Beetle NeoPop",
        "mednafen_supergrafx" to "Beetle SuperGrafx",
        "mednafen_wswan" to "Beetle WonderSwan",
        "stella2014" to "Stella 2014",
        "dosbox_pure" to "DOSBox-pure",
        "dosbox_core" to "DOSBox-core",
        "dosbox_svn" to "DOSBox-SVN",
        "fbneo" to "FinalBurn Neo",
        "fbalpha2012" to "FB Alpha 2012",
        "fbalpha2012_neogeo" to "FB Alpha 2012 Neo Geo",
        "mame2003_plus" to "MAME 2003-Plus",
        "mame2010" to "MAME 2010 (0.139)",
        "cap32" to "Caprice32",
        "vice_x64" to "VICE x64",
        "vice_x64sc" to "VICE x64sc",
        "vice_xvic" to "VICE xvic",
        "virtualjaguar" to "Virtual Jaguar",
        "np2kai" to "Neko Project II Kai",
        "fake08" to "FAKE-08"
    )

    /**
     * On-disk save/state folder name RetroArch uses for a core when sort-into-folders is on: the
     * libretro `corename`, which is decoupled from the core id used for config/server identity.
     * Falls back to the id for cores whose folder differs by no more than case (case-insensitive FS).
     */
    fun getRetroArchSaveDirName(coreId: String): String =
        retroArchSaveDirByCore[coreId] ?: coreId

    private val libretroHostEmulators = setOf("builtin", "retroarch", "retroarch_64")

    /**
     * Server-side save emulator label: for libretro hosts (built-in + RetroArch) the libretro core
     * slug, matching RomM's EmulatorJS naming. Other emulators keep their own id.
     */
    fun toServerEmulator(emulatorId: String, coreId: String?): String {
        if (emulatorId !in libretroHostEmulators) return emulatorId
        val core = coreId ?: return emulatorId
        return when (core) {
            "mupen64plus_next_gles2", "mupen64plus_next_gles3" -> "mupen64plus_next"
            else -> core
        }
    }

    private val emulatorFamilies = listOf(
        EmulatorFamily(
            baseId = "dolphin",
            displayNamePrefix = "Dolphin",
            packagePatterns = listOf("org.dolphinemu.*"),
            supportedPlatforms = setOf("gc", "wii"),
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
            supportedPlatforms = setOf("vita"),
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
            baseId = "mupen64plus",
            displayNamePrefix = "Mupen64Plus",
            packagePatterns = listOf("org.mupen64plusae.*", "com.m64.fx.plus.emulate"),
            supportedPlatforms = setOf("n64"),
            launchAction = Intent.ACTION_VIEW,
            launchConfig = LaunchConfig.FileUri,
            downloadUrl = "https://play.google.com/store/apps/details?id=org.mupen64plusae.v3.fzurita"
        ),
        EmulatorFamily(
            baseId = "azahar",
            displayNamePrefix = "Azahar",
            packagePatterns = listOf("org.azahar_emu.*", "io.github.lime3ds.*", "io.github.azahar_emu.*"),
            supportedPlatforms = setOf("3ds"),
            launchConfig = LaunchConfig.Custom(
                activityClass = "org.citra.citra_emu.activities.EmulationActivity",
                intentExtras = mapOf("SelectedGame" to ExtraValue.FilePath)
            ),
            downloadUrl = "https://github.com/azahar-emu/azahar/releases"
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
            supportedPlatforms = setOf("switch")
        ),
        EmulatorFamily(
            baseId = "eden",
            displayNamePrefix = "Eden",
            packagePatterns = listOf(
                "dev.eden.*",
                "dev.legacy.eden*",
                "org.eden.*",
                "com.miHoYo.Yuanshen"
            ),
            supportedPlatforms = setOf("switch"),
            downloadUrl = "https://git.eden-emu.dev/eden-emu/eden/releases"
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
            downloadUrl = "https://git.ryujinx.app/kenji-nx/android/-/releases"
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
                intentExtras = mapOf("bootPath" to ExtraValue.FileUriString)
            ),
            downloadUrl = "https://github.com/Trixarian/NetherSX2-patch/releases"
        ),
        EmulatorFamily(
            baseId = "armsx2",
            displayNamePrefix = "ARMSX2",
            packagePatterns = listOf("come.nanodata.armsx2", "come.nanodata.armsx2.*"),
            supportedPlatforms = setOf("ps2"),
            launchConfig = LaunchConfig.Custom(
                activityClass = "kr.co.iefriends.pcsx2.activities.MainActivity"
            ),
            downloadUrl = "https://github.com/ARMSX2/ARMSX2/releases"
        ),
        EmulatorFamily(
            baseId = "psx2",
            displayNamePrefix = "PSX2",
            packagePatterns = listOf("com.izzy2lost.*"),
            supportedPlatforms = setOf("ps2"),
            downloadUrl = "https://github.com/izzy2lost/PSX2/releases"
        ),
        EmulatorFamily(
            baseId = "duckstation",
            displayNamePrefix = "DuckStation",
            packagePatterns = listOf("com.github.stenzek.duckstation*"),
            supportedPlatforms = setOf("psx"),
            launchAction = Intent.ACTION_MAIN,
            launchConfig = LaunchConfig.Custom(
                activityClass = "com.github.stenzek.duckstation.EmulationActivity",
                intentExtras = mapOf("bootPath" to ExtraValue.FileUriString)
            ),
            downloadUrl = "https://www.duckstation.org/android/"
        ),
        EmulatorFamily(
            baseId = "melonds",
            displayNamePrefix = "melonDS",
            packagePatterns = listOf("me.magnum.melonds*", "me.magnum.melondualds*"),
            supportedPlatforms = setOf("nds"),
            launchConfig = LaunchConfig.Custom(
                activityClass = "me.magnum.melonds.ui.emulator.EmulatorActivity",
                intentExtras = mapOf("PATH" to ExtraValue.FilePath)
            ),
            downloadUrl = "https://github.com/rafaelvcaetano/melonDS-android/releases/tag/nightly-release"
        ),
        EmulatorFamily(
            baseId = "ax360e",
            displayNamePrefix = "AX360E",
            packagePatterns = listOf("aenu.ax360e*"),
            supportedPlatforms = setOf("xbox360"),
            launchAction = "aenu.intent.action.AX360E",
            launchConfig = LaunchConfig.Custom(
                activityClass = "aenu.ax360e.EmulatorActivity",
                intentExtras = mapOf("game_uri" to ExtraValue.FileUri)
            )
        ),
        EmulatorFamily(
            baseId = "retroarch",
            displayNamePrefix = "RetroArch",
            packagePatterns = listOf("com.retroarch*"),
            supportedPlatforms = setOf(
                "nes", "snes", "n64", "gc", "gb", "gbc", "gba", "nds",
                "genesis", "sms", "sg1000", "gg", "scd", "32x",
                "psx", "psp", "saturn", "dreamcast",
                "tg16", "tgcd", "pcfx", "3do",
                "atari2600", "atari5200", "atari7800", "atari8bit", "lynx", "jaguar",
                "ngp", "ngpc", "neogeo", "neogeocd",
                "msx", "msx2", "coleco",
                "wonderswan", "wsc", "pokemini",
                "arcade", "supergrafx",
                "c64", "vic20", "dos", "zx", "pc9800", "amstradcpc", "pico8", "scummvm"
            ),
            launchAction = Intent.ACTION_MAIN,
            launchConfig = LaunchConfig.RetroArch(),
            downloadUrl = "https://www.retroarch.com/?page=platforms"
        )
    )

    fun getEmulatorFamilies(): List<EmulatorFamily> = emulatorFamilies

    private val platformExtensionOptions: Map<String, List<ExtensionOption>> = emptyMap()

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
            defaultLaunchMethod = family.defaultLaunchMethod,
            downloadUrl = family.downloadUrl
        )
    }
}
