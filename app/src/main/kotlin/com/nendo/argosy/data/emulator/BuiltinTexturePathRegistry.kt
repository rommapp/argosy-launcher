package com.nendo.argosy.data.emulator

import com.nendo.argosy.data.platform.PlatformDefinitions
import java.io.File
import java.util.Locale

enum class TextureBaseRoot {
    LIBRETRO_SAVE_DIR,
    LIBRETRO_SYSTEM_DIR
}

enum class TextureFolderKeyStrategy {
    GAME_ID,
    N64_HEADER_NAME
}

data class BuiltinTextureRule(
    val coreIds: Set<String>,
    val platformSlugs: Set<String>,
    val baseRoot: TextureBaseRoot,
    val relativeBaseDir: String,
    val folderKeyStrategy: TextureFolderKeyStrategy,
    val coreOptions: Map<String, String> = emptyMap(),
    val htcCoreOptions: Map<String, String> = emptyMap(),
    val htsCoreOptions: Map<String, String> = emptyMap(),
    val htcHtsRelativeCacheDir: String? = null,
    val supportsHtc: Boolean = false,
    val supportsHts: Boolean = false,
    val packSubdir: String? = null
) {
    fun optionsForPack(hasHtc: Boolean = false, hasHts: Boolean = false): Map<String, String> = when {
        hasHts && htsCoreOptions.isNotEmpty() -> htsCoreOptions
        hasHtc && htcCoreOptions.isNotEmpty() -> htcCoreOptions
        else -> coreOptions
    }
}

object BuiltinTexturePathRegistry {

    private val TEXTURE_PACK_EXTENSIONS = setOf("zip", "htc", "hts")

    private val HTC_HTS_EXTENSIONS = setOf("htc", "hts")

    private val rules: List<BuiltinTextureRule> = listOf(
        BuiltinTextureRule(
            coreIds = setOf("dolphin"),
            platformSlugs = setOf("ngc", "gc", "gamecube", "wii"),
            baseRoot = TextureBaseRoot.LIBRETRO_SAVE_DIR,
            relativeBaseDir = "User/Load/Textures",
            folderKeyStrategy = TextureFolderKeyStrategy.GAME_ID,
            coreOptions = mapOf("dolphin_load_custom_textures" to "enabled")
        ),
        BuiltinTextureRule(
            coreIds = setOf("ppsspp"),
            platformSlugs = setOf("psp"),
            baseRoot = TextureBaseRoot.LIBRETRO_SAVE_DIR,
            relativeBaseDir = "PSP/TEXTURES",
            folderKeyStrategy = TextureFolderKeyStrategy.GAME_ID,
            coreOptions = mapOf("ppsspp_texture_replacement" to "enabled")
        ),
        BuiltinTextureRule(
            coreIds = setOf("mupen64plus_next_gles3", "mupen64plus_next_gles2"),
            platformSlugs = setOf("n64"),
            baseRoot = TextureBaseRoot.LIBRETRO_SYSTEM_DIR,
            relativeBaseDir = "Mupen64plus/hires_texture",
            folderKeyStrategy = TextureFolderKeyStrategy.N64_HEADER_NAME,
            coreOptions = mapOf(
                "mupen64plus-rdp-plugin" to "gliden64",
                "mupen64plus-txHiresEnable" to "True",
                "mupen64plus-txHiresFullAlphaChannel" to "True"
            ),
            htcCoreOptions = mapOf(
                "mupen64plus-rdp-plugin" to "gliden64",
                "mupen64plus-txHiresEnable" to "True",
                "mupen64plus-txHiresFullAlphaChannel" to "True",
                "mupen64plus-EnableEnhancedHighResStorage" to "False"
            ),
            htsCoreOptions = mapOf(
                "mupen64plus-rdp-plugin" to "gliden64",
                "mupen64plus-txHiresEnable" to "True",
                "mupen64plus-txHiresFullAlphaChannel" to "True",
                "mupen64plus-EnableEnhancedHighResStorage" to "True"
            ),
            htcHtsRelativeCacheDir = "Mupen64plus/cache",
            supportsHtc = true,
            supportsHts = true
        ),
        BuiltinTextureRule(
            coreIds = setOf("parallel_n64"),
            platformSlugs = setOf("n64", "n64dd"),
            baseRoot = TextureBaseRoot.LIBRETRO_SYSTEM_DIR,
            relativeBaseDir = "Mupen64plus/hires_texture",
            folderKeyStrategy = TextureFolderKeyStrategy.N64_HEADER_NAME,
            coreOptions = mapOf(
                "parallel-n64-gfxplugin" to "gliden64",
                "parallel-n64-gliden64-txHiresEnable" to "True",
                "parallel-n64-gliden64-txHiresFullAlphaChannel" to "True"
            ),
            htcCoreOptions = mapOf(
                "parallel-n64-gfxplugin" to "gliden64",
                "parallel-n64-gliden64-txHiresEnable" to "True",
                "parallel-n64-gliden64-txHiresFullAlphaChannel" to "True"
            ),
            htcHtsRelativeCacheDir = "Mupen64plus/cache",
            supportsHtc = true,
            supportsHts = false
        ),
        BuiltinTextureRule(
            coreIds = setOf("flycast"),
            platformSlugs = setOf("dc", "dreamcast"),
            baseRoot = TextureBaseRoot.LIBRETRO_SYSTEM_DIR,
            relativeBaseDir = "dc/textures",
            folderKeyStrategy = TextureFolderKeyStrategy.GAME_ID,
            coreOptions = mapOf("reicast_custom_textures" to "enabled")
        ),
        BuiltinTextureRule(
            coreIds = setOf("azahar"),
            platformSlugs = setOf("3ds", "n3ds"),
            baseRoot = TextureBaseRoot.LIBRETRO_SAVE_DIR,
            relativeBaseDir = "Azahar/load/textures",
            folderKeyStrategy = TextureFolderKeyStrategy.GAME_ID,
            coreOptions = mapOf("citra_custom_textures" to "enabled")
        ),
        BuiltinTextureRule(
            coreIds = setOf("armsx2"),
            platformSlugs = setOf("ps2"),
            baseRoot = TextureBaseRoot.LIBRETRO_SYSTEM_DIR,
            relativeBaseDir = "pcsx2/textures",
            folderKeyStrategy = TextureFolderKeyStrategy.GAME_ID,
            packSubdir = "replacements"
        )
    )

    fun resolveRule(coreId: String?, platformSlug: String): BuiltinTextureRule? {
        val canonicalSlug = PlatformDefinitions.getCanonicalSlug(platformSlug).lowercase(Locale.ROOT)
        val rawSlug = platformSlug.lowercase(Locale.ROOT)
        val normalizedCoreId = coreId?.trim()?.lowercase(Locale.ROOT)?.takeIf { it.isNotEmpty() }

        if (normalizedCoreId != null) {
            return rules.firstOrNull { rule ->
                normalizedCoreId in rule.coreIds &&
                    (canonicalSlug in rule.platformSlugs || rawSlug in rule.platformSlugs)
            } ?: rules.firstOrNull { rule -> normalizedCoreId in rule.coreIds }
        }

        return rules.firstOrNull { rule ->
            canonicalSlug in rule.platformSlugs || rawSlug in rule.platformSlugs
        }
    }

    fun resolveRuleForPlatform(platformSlug: String): BuiltinTextureRule? {
        val canonicalSlug = PlatformDefinitions.getCanonicalSlug(platformSlug).lowercase(Locale.ROOT)
        val rawSlug = platformSlug.lowercase(Locale.ROOT)
        return rules.firstOrNull { rule ->
            canonicalSlug in rule.platformSlugs || rawSlug in rule.platformSlugs
        }
    }

    fun resolveBaseDir(rule: BuiltinTextureRule, saveDir: File, systemDir: File): File {
        val root = when (rule.baseRoot) {
            TextureBaseRoot.LIBRETRO_SAVE_DIR -> saveDir
            TextureBaseRoot.LIBRETRO_SYSTEM_DIR -> systemDir
        }
        return File(root, rule.relativeBaseDir)
    }

    fun resolveTargetDir(
        rule: BuiltinTextureRule,
        saveDir: File,
        systemDir: File,
        folderKey: String
    ): File {
        val baseDir = resolveBaseDir(rule, saveDir, systemDir)
        val cleanKey = folderKey.trim().trim('/', '\\')
        val gameDir = File(baseDir, cleanKey)
        return rule.packSubdir?.let { File(gameDir, it) } ?: gameDir
    }

    fun resolveHtcHtsCacheDir(rule: BuiltinTextureRule, saveDir: File, systemDir: File): File? {
        val rel = rule.htcHtsRelativeCacheDir ?: return null
        val root = when (rule.baseRoot) {
            TextureBaseRoot.LIBRETRO_SAVE_DIR -> saveDir
            TextureBaseRoot.LIBRETRO_SYSTEM_DIR -> systemDir
        }
        return File(root, rel)
    }

    fun isTexturePackFileName(fileName: String): Boolean {
        val ext = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return ext in TEXTURE_PACK_EXTENSIONS
    }

    fun isHtcOrHtsFileName(fileName: String): Boolean {
        val ext = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return ext in HTC_HTS_EXTENSIONS
    }

    fun isHtsFileName(fileName: String): Boolean =
        fileName.substringAfterLast('.', "").lowercase(Locale.ROOT) == "hts"

    fun isHtcFileName(fileName: String): Boolean =
        fileName.substringAfterLast('.', "").lowercase(Locale.ROOT) == "htc"

    fun isSystemTexturePath(rawPath: String): Boolean {
        val normalized = rawPath.replace('\\', '/').trim('/').lowercase(Locale.ROOT)
        if (normalized.isEmpty()) return false
        val segments = normalized.split('/')
        return segments.containsSublist("mupen64plus", "hires_texture") ||
            segments.containsSublist("mupen64plus", "cache") ||
            segments.containsSublist("dc", "textures") ||
            segments.containsSublist("pcsx2", "textures")
    }

    private fun List<String>.containsSublist(vararg target: String): Boolean {
        if (target.isEmpty() || size < target.size) return false
        val maxStart = size - target.size
        for (i in 0..maxStart) {
            var match = true
            for (j in target.indices) {
                if (this[i + j] != target[j]) {
                    match = false
                    break
                }
            }
            if (match) return true
        }
        return false
    }
}
