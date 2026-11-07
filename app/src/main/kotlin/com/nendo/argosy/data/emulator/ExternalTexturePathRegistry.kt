package com.nendo.argosy.data.emulator

import com.nendo.argosy.data.platform.PlatformDefinitions
import java.io.File
import java.util.Locale

enum class ExternalTextureBaseRoot {
    PACKAGE_DATA,
    SHARED_STORAGE
}

/**
 * @property activeMarker a path under the package's files directory that only exists once the
 * emulator has adopted that directory as its data root. While it exists the legacy
 * [fallbackRelativeDirs] are ignored, because a leftover legacy folder is not the folder the
 * emulator reads.
 * @property packSubdir the folder under the per-game directory the emulator scans for
 * replacements, when it is not the per-game directory itself.
 */
data class ExternalTextureRule(
    val emulatorPackages: Set<String>,
    val platformSlugs: Set<String>,
    val baseRoot: ExternalTextureBaseRoot,
    val relativeBaseDir: String,
    val folderKeyStrategy: TextureFolderKeyStrategy = TextureFolderKeyStrategy.GAME_ID,
    val fallbackRelativeDirs: List<String> = emptyList(),
    val emulatorPackagePrefixes: Set<String> = emptySet(),
    val activeMarker: String? = null,
    val packSubdir: String? = null
) {
    fun matchesPackage(pkg: String): Boolean =
        pkg in emulatorPackages || emulatorPackagePrefixes.any { pkg.startsWith(it) }
}

object ExternalTexturePathRegistry {

    private val DOLPHIN_PLATFORMS = setOf("ngc", "gc", "gamecube", "wii")

    private val rules: List<ExternalTextureRule> = listOf(
        ExternalTextureRule(
            emulatorPackages = setOf("org.dolphinemu.mmjr"),
            platformSlugs = DOLPHIN_PLATFORMS,
            baseRoot = ExternalTextureBaseRoot.PACKAGE_DATA,
            relativeBaseDir = "Load/Textures",
            fallbackRelativeDirs = listOf("mmjr/Load/Textures", "mmjr2-vbi/Load/Textures"),
            activeMarker = "Config"
        ),
        ExternalTextureRule(
            emulatorPackages = setOf("com.joeyos.dolphinemu"),
            emulatorPackagePrefixes = setOf("org.dolphinemu."),
            platformSlugs = DOLPHIN_PLATFORMS,
            baseRoot = ExternalTextureBaseRoot.PACKAGE_DATA,
            relativeBaseDir = "Load/Textures",
            fallbackRelativeDirs = listOf("dolphin-emu/Load/Textures"),
            activeMarker = "Config"
        ),
        ExternalTextureRule(
            emulatorPackages = setOf("org.ppsspp.ppsspp", "org.ppsspp.ppssppgold", "org.ppsspp.ppsspplegacy"),
            platformSlugs = setOf("psp"),
            baseRoot = ExternalTextureBaseRoot.SHARED_STORAGE,
            relativeBaseDir = "PSP/TEXTURES",
            fallbackRelativeDirs = listOf("Android/data/{package}/files/PSP/TEXTURES")
        ),
        ExternalTextureRule(
            emulatorPackages = setOf("com.github.stenzek.duckstation"),
            platformSlugs = setOf("psx", "ps1"),
            baseRoot = ExternalTextureBaseRoot.PACKAGE_DATA,
            relativeBaseDir = "textures",
            fallbackRelativeDirs = listOf("duckstation/textures")
        ),
        ExternalTextureRule(
            emulatorPackages = setOf("xyz.aethersx2.android", "com.armsx2", "come.nanodata.armsx2"),
            emulatorPackagePrefixes = setOf("com.armsx2.", "come.nanodata.armsx2."),
            platformSlugs = setOf("ps2"),
            baseRoot = ExternalTextureBaseRoot.PACKAGE_DATA,
            relativeBaseDir = "textures",
            packSubdir = "replacements"
        )
    )

    fun resolveRule(emulatorPackage: String?, platformSlug: String): ExternalTextureRule? {
        val pkg = emulatorPackage?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val canonicalSlug = PlatformDefinitions.getCanonicalSlug(platformSlug).lowercase(Locale.ROOT)
        val rawSlug = platformSlug.lowercase(Locale.ROOT)
        return rules.firstOrNull { rule ->
            rule.matchesPackage(pkg) &&
                (canonicalSlug in rule.platformSlugs || rawSlug in rule.platformSlugs)
        }
    }

    fun resolveRuleForPlatform(platformSlug: String): ExternalTextureRule? {
        val canonicalSlug = PlatformDefinitions.getCanonicalSlug(platformSlug).lowercase(Locale.ROOT)
        val rawSlug = platformSlug.lowercase(Locale.ROOT)
        return rules.firstOrNull { rule ->
            canonicalSlug in rule.platformSlugs || rawSlug in rule.platformSlugs
        }
    }

    fun resolveBaseDir(
        rule: ExternalTextureRule,
        emulatorPackage: String,
        externalStorageRoots: List<String>,
        directoryExists: (File) -> Boolean = { it.exists() }
    ): File? {
        val rootPath = externalStorageRoots.firstOrNull()?.takeIf { it.isNotBlank() } ?: return null
        val root = File(rootPath)
        val packageFilesDir = File(root, "Android/data/$emulatorPackage/files")

        val primaryIsActive = rule.activeMarker?.let { directoryExists(File(packageFilesDir, it)) } == true
        if (!primaryIsActive) {
            for (fallbackRel in rule.fallbackRelativeDirs) {
                val resolvedRel = fallbackRel.replace("{package}", emulatorPackage)
                val candidate = File(root, resolvedRel)
                if (directoryExists(candidate)) {
                    return candidate
                }
            }
        }

        return when (rule.baseRoot) {
            ExternalTextureBaseRoot.PACKAGE_DATA -> File(packageFilesDir, rule.relativeBaseDir)
            ExternalTextureBaseRoot.SHARED_STORAGE ->
                File(root, rule.relativeBaseDir.replace("{package}", emulatorPackage))
        }
    }

    fun resolveTargetDir(
        rule: ExternalTextureRule,
        emulatorPackage: String,
        externalStorageRoots: List<String>,
        folderKey: String,
        directoryExists: (File) -> Boolean = { it.exists() }
    ): File? {
        val baseDir = resolveBaseDir(rule, emulatorPackage, externalStorageRoots, directoryExists) ?: return null
        val cleanKey = folderKey.trim().trim('/', '\\')
        val gameDir = File(baseDir, cleanKey)
        return rule.packSubdir?.let { File(gameDir, it) } ?: gameDir
    }
}
