package com.nendo.argosy.data.emulator

import com.nendo.argosy.data.platform.PlatformDefinitions
import java.io.File
import java.util.Locale

enum class ExternalTextureBaseRoot {
    PACKAGE_DATA,
    SHARED_STORAGE
}

data class ExternalTextureRule(
    val emulatorPackages: Set<String>,
    val platformSlugs: Set<String>,
    val baseRoot: ExternalTextureBaseRoot,
    val relativeBaseDir: String,
    val folderKeyStrategy: TextureFolderKeyStrategy = TextureFolderKeyStrategy.GAME_ID,
    val fallbackRelativeDirs: List<String> = emptyList()
)

object ExternalTexturePathRegistry {

    private val rules: List<ExternalTextureRule> = listOf(
        ExternalTextureRule(
            emulatorPackages = setOf(
                "org.dolphinemu.dolphinemu",
                "org.dolphinemu.handheld",
                "com.joeyos.dolphinemu",
                "org.dolphinemu.mmjr"
            ),
            platformSlugs = setOf("ngc", "gc", "gamecube", "wii"),
            baseRoot = ExternalTextureBaseRoot.PACKAGE_DATA,
            relativeBaseDir = "Load/Textures",
            fallbackRelativeDirs = listOf("dolphin-emu/Load/Textures", "mmjr/Load/Textures", "mmjr2-vbi/Load/Textures")
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
            emulatorPackages = setOf("xyz.aethersx2.android"),
            platformSlugs = setOf("ps2"),
            baseRoot = ExternalTextureBaseRoot.PACKAGE_DATA,
            relativeBaseDir = "textures"
        )
    )

    fun resolveRule(emulatorPackage: String?, platformSlug: String): ExternalTextureRule? {
        val pkg = emulatorPackage?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val canonicalSlug = PlatformDefinitions.getCanonicalSlug(platformSlug).lowercase(Locale.ROOT)
        val rawSlug = platformSlug.lowercase(Locale.ROOT)
        return rules.firstOrNull { rule ->
            pkg in rule.emulatorPackages &&
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

        for (fallbackRel in rule.fallbackRelativeDirs) {
            val resolvedRel = fallbackRel.replace("{package}", emulatorPackage)
            val candidate = File(root, resolvedRel)
            if (directoryExists(candidate)) {
                return candidate
            }
        }

        return when (rule.baseRoot) {
            ExternalTextureBaseRoot.PACKAGE_DATA ->
                File(root, "Android/data/$emulatorPackage/files/${rule.relativeBaseDir}")
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
        return File(baseDir, cleanKey)
    }
}
