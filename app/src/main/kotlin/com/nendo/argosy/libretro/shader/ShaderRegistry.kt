package com.nendo.argosy.libretro.shader

import android.content.Context
import android.util.Log
import com.swordfish.libretrodroid.ShaderConfig
import java.io.File

class ShaderRegistry(private val context: Context) {

    enum class Category {
        CRT, HANDHELD, SCALING, SCANLINES, SHARPENING, ANTI_ALIASING, OTHER
    }

    enum class Source { CATALOG, CUSTOM }

    data class ShaderEntry(
        val id: String,
        val displayName: String,
        val category: Category,
        val source: Source,
        val githubPath: String = "",
        val preInstall: Boolean = false,
        val passCount: Int = 1,
        val description: String? = null
    )

    private var installedCache: Set<String>? = null

    fun getInstalledIds(): Set<String> {
        installedCache?.let { return it }
        val dir = getCatalogDir()
        val ids = if (dir.exists()) {
            dir.listFiles()
                ?.filter { it.extension == "glsl" }
                ?.map { it.nameWithoutExtension }
                ?.toSet()
                ?: emptySet()
        } else {
            emptySet()
        }
        installedCache = ids
        return ids
    }

    fun invalidateInstalledCache() {
        installedCache = null
    }

    private val catalogShaders = listOf(
        // --- CRT: Pre-install ---
        ShaderEntry(
            "crt-easymode", "CRT EasyMode", Category.CRT, Source.CATALOG,
            githubPath = "crt/shaders/crt-easymode.glsl", preInstall = true,
            description = "Flat CRT with configurable scanlines and shadow mask"
        ),
        ShaderEntry(
            "crt-lottes", "CRT Lottes", Category.CRT, Source.CATALOG,
            githubPath = "crt/shaders/crt-lottes.glsl", preInstall = true,
            description = "Tim Lottes' CRT approximation"
        ),
        ShaderEntry(
            "crt-geom", "CRT Geom", Category.CRT, Source.CATALOG,
            githubPath = "crt/shaders/crt-geom.glsl", preInstall = true,
            description = "Classic CRT geometry simulation"
        ),
        ShaderEntry(
            "crt-hyllian", "CRT Hyllian", Category.CRT, Source.CATALOG,
            githubPath = "crt/shaders/crt-hyllian.glsl", preInstall = true,
            description = "CRT with phosphor glow"
        ),
        ShaderEntry(
            "zfast_crt", "zFast CRT", Category.CRT, Source.CATALOG,
            githubPath = "crt/shaders/zfast_crt.glsl", preInstall = true,
            description = "Lightweight fast CRT"
        ),
        ShaderEntry(
            "crt-aperture", "CRT Aperture", Category.CRT, Source.CATALOG,
            githubPath = "crt/shaders/crt-aperture.glsl", preInstall = true,
            description = "CRT with aperture grille"
        ),
        ShaderEntry(
            "crt-pi", "CRT Pi", Category.CRT, Source.CATALOG,
            githubPath = "crt/shaders/crt-pi.glsl", preInstall = true,
            description = "CRT optimized for low-power devices"
        ),
        ShaderEntry(
            "fakelottes", "Fake Lottes", Category.CRT, Source.CATALOG,
            githubPath = "crt/shaders/fakelottes.glsl", preInstall = true,
            description = "Simplified Lottes CRT approximation"
        ),
        // --- CRT: Extended catalog ---
        ShaderEntry(
            "crt-hyllian-fast", "CRT Hyllian Fast", Category.CRT, Source.CATALOG,
            githubPath = "crt/shaders/crt-hyllian-fast.glsl"
        ),
        ShaderEntry(
            "crt-lottes-fast", "CRT Lottes Fast", Category.CRT, Source.CATALOG,
            githubPath = "crt/shaders/crt-lottes-fast.glsl"
        ),
        ShaderEntry(
            "crt-lottes-mini", "CRT Lottes Mini", Category.CRT, Source.CATALOG,
            githubPath = "crt/shaders/crt-lottes-mini.glsl"
        ),
        ShaderEntry(
            "crt-caligari", "CRT Caligari", Category.CRT, Source.CATALOG,
            githubPath = "crt/shaders/crt-caligari.glsl"
        ),
        ShaderEntry(
            "crt-cgwg-fast", "CRT CGWG Fast", Category.CRT, Source.CATALOG,
            githubPath = "crt/shaders/crt-cgwg-fast.glsl"
        ),
        ShaderEntry(
            "crt-gdv-mini", "CRT GDV Mini", Category.CRT, Source.CATALOG,
            githubPath = "crt/shaders/crt-gdv-mini.glsl"
        ),
        ShaderEntry(
            "crt-mattias", "CRT Mattias", Category.CRT, Source.CATALOG,
            githubPath = "crt/shaders/crt-mattias.glsl"
        ),
        ShaderEntry(
            "crt-nes-mini", "CRT NES Mini", Category.CRT, Source.CATALOG,
            githubPath = "crt/shaders/crt-nes-mini.glsl"
        ),
        ShaderEntry(
            "crt-nobody", "CRT Nobody", Category.CRT, Source.CATALOG,
            githubPath = "crt/shaders/crt-nobody.glsl"
        ),
        ShaderEntry(
            "crt-1tap", "CRT 1-Tap", Category.CRT, Source.CATALOG,
            githubPath = "crt/shaders/crt-1tap.glsl"
        ),
        ShaderEntry(
            "crt-beam", "CRT Beam", Category.CRT, Source.CATALOG,
            githubPath = "crt/shaders/CRT-Beam.glsl"
        ),
        ShaderEntry(
            "fakelottes-geom", "Fake Lottes Geom", Category.CRT, Source.CATALOG,
            githubPath = "crt/shaders/fakelottes-geom.glsl"
        ),
        ShaderEntry(
            "yee64", "Yee64", Category.CRT, Source.CATALOG,
            githubPath = "crt/shaders/yee64.glsl"
        ),
        ShaderEntry(
            "yeetron", "Yeetron", Category.CRT, Source.CATALOG,
            githubPath = "crt/shaders/yeetron.glsl"
        ),
        ShaderEntry(
            "zfast_crt_geo", "zFast CRT Geo", Category.CRT, Source.CATALOG,
            githubPath = "crt/shaders/zfast_crt_geo.glsl"
        ),
        ShaderEntry(
            "tvout-tweaks", "TV-Out Tweaks", Category.CRT, Source.CATALOG,
            githubPath = "crt/shaders/tvout-tweaks.glsl"
        ),
        // --- HANDHELD: Pre-install ---
        ShaderEntry(
            "lcd1x", "LCD 1x", Category.HANDHELD, Source.CATALOG,
            githubPath = "handheld/shaders/lcd1x.glsl", preInstall = true,
            description = "Clean LCD subpixel emulation"
        ),
        ShaderEntry(
            "lcd3x", "LCD 3x", Category.HANDHELD, Source.CATALOG,
            githubPath = "handheld/shaders/lcd3x.glsl", preInstall = true,
            description = "LCD subpixel grid at 3x scale"
        ),
        ShaderEntry(
            "zfast_lcd", "zFast LCD", Category.HANDHELD, Source.CATALOG,
            githubPath = "handheld/shaders/zfast_lcd.glsl", preInstall = true,
            description = "Lightweight LCD filter"
        ),
        ShaderEntry(
            "dot", "Dot Matrix", Category.HANDHELD, Source.CATALOG,
            githubPath = "handheld/shaders/dot.glsl", preInstall = true,
            description = "Dot matrix display effect"
        ),
        ShaderEntry(
            "retro-v2", "Retro v2", Category.HANDHELD, Source.CATALOG,
            githubPath = "handheld/shaders/retro-v2.glsl", preInstall = true,
            description = "Retro handheld screen look"
        ),
        // --- HANDHELD: Extended catalog ---
        ShaderEntry(
            "bevel", "Bevel", Category.HANDHELD, Source.CATALOG,
            githubPath = "handheld/shaders/bevel.glsl"
        ),
        ShaderEntry(
            "lcd1x_nds", "LCD 1x NDS", Category.HANDHELD, Source.CATALOG,
            githubPath = "handheld/shaders/lcd1x_nds.glsl"
        ),
        ShaderEntry(
            "lcd1x_psp", "LCD 1x PSP", Category.HANDHELD, Source.CATALOG,
            githubPath = "handheld/shaders/lcd1x_psp.glsl"
        ),
        ShaderEntry(
            "sameboy-lcd", "SameBoy LCD", Category.HANDHELD, Source.CATALOG,
            githubPath = "handheld/shaders/sameboy-lcd.glsl"
        ),
        ShaderEntry(
            "gba-color", "GBA Color", Category.HANDHELD, Source.CATALOG,
            githubPath = "handheld/shaders/color/gba-color.glsl"
        ),
        ShaderEntry(
            "gbc-color", "GBC Color", Category.HANDHELD, Source.CATALOG,
            githubPath = "handheld/shaders/color/gbc-color.glsl"
        ),
        ShaderEntry(
            "nds-color", "NDS Color", Category.HANDHELD, Source.CATALOG,
            githubPath = "handheld/shaders/color/nds-color.glsl"
        ),
        ShaderEntry(
            "psp-color", "PSP Color", Category.HANDHELD, Source.CATALOG,
            githubPath = "handheld/shaders/color/psp-color.glsl"
        ),
        // --- SCALING: Pre-install ---
        ShaderEntry(
            "sharp-bilinear", "Sharp Bilinear", Category.SCALING, Source.CATALOG,
            githubPath = "interpolation/shaders/sharp-bilinear.glsl", preInstall = true,
            description = "Integer prescale with bilinear smoothing"
        ),
        ShaderEntry(
            "sharp-bilinear-simple", "Sharp Bilinear Simple", Category.SCALING, Source.CATALOG,
            githubPath = "interpolation/shaders/sharp-bilinear-simple.glsl", preInstall = true,
            description = "Simplified sharp bilinear scaling"
        ),
        ShaderEntry(
            "pixellate", "Pixellate", Category.SCALING, Source.CATALOG,
            githubPath = "interpolation/shaders/pixellate.glsl", preInstall = true,
            description = "Clean integer pixel scaling"
        ),
        // --- SCALING: Extended catalog ---
        ShaderEntry(
            "quilez", "Quilez", Category.SCALING, Source.CATALOG,
            githubPath = "interpolation/shaders/quilez.glsl"
        ),
        ShaderEntry(
            "aann", "AA Nearest Neighbor", Category.SCALING, Source.CATALOG,
            githubPath = "interpolation/shaders/aann.glsl"
        ),
        ShaderEntry(
            "smootheststep", "Smoothest Step", Category.SCALING, Source.CATALOG,
            githubPath = "interpolation/shaders/smootheststep.glsl"
        ),
        ShaderEntry(
            "sharp-bilinear-scanlines", "Sharp Bilinear Scanlines", Category.SCALING, Source.CATALOG,
            githubPath = "interpolation/shaders/sharp-bilinear-scanlines.glsl"
        ),
        // --- SCANLINES: Pre-install ---
        ShaderEntry(
            "scanline", "Scanline", Category.SCANLINES, Source.CATALOG,
            githubPath = "scanlines/shaders/scanline.glsl", preInstall = true,
            description = "Simple scanline overlay"
        ),
        // --- SCANLINES: Extended catalog ---
        ShaderEntry(
            "scanlines-sine-abs", "Scanlines Sine", Category.SCANLINES, Source.CATALOG,
            githubPath = "scanlines/shaders/scanlines-sine-abs.glsl"
        ),
        ShaderEntry(
            "scanline-fract", "Scanline Fract", Category.SCANLINES, Source.CATALOG,
            githubPath = "scanlines/shaders/scanline-fract.glsl"
        ),
        ShaderEntry(
            "res-independent-scanlines", "Res-Independent Scanlines", Category.SCANLINES, Source.CATALOG,
            githubPath = "scanlines/shaders/res-independent-scanlines.glsl"
        ),
        // --- SHARPENING: Pre-install ---
        ShaderEntry(
            "adaptive-sharpen", "Adaptive Sharpen", Category.SHARPENING, Source.CATALOG,
            githubPath = "sharpen/shaders/adaptive-sharpen.glsl", preInstall = true,
            description = "Adaptive sharpening filter"
        ),
        ShaderEntry(
            "fast-sharpen", "Fast Sharpen", Category.SHARPENING, Source.CATALOG,
            githubPath = "sharpen/shaders/fast-sharpen.glsl", preInstall = true,
            description = "Lightweight sharpening pass"
        ),
        // --- SHARPENING: Extended catalog ---
        ShaderEntry(
            "diff", "Diff Sharpen", Category.SHARPENING, Source.CATALOG,
            githubPath = "sharpen/shaders/diff.glsl"
        ),
        // --- ANTI_ALIASING: Pre-install ---
        ShaderEntry(
            "fxaa", "FXAA", Category.ANTI_ALIASING, Source.CATALOG,
            githubPath = "anti-aliasing/shaders/fxaa.glsl", preInstall = true,
            description = "Fast approximate anti-aliasing"
        ),
        // --- ANTI_ALIASING: Extended catalog ---
        ShaderEntry(
            "advanced-aa", "Advanced AA", Category.ANTI_ALIASING, Source.CATALOG,
            githubPath = "anti-aliasing/shaders/advanced-aa.glsl"
        ),
        ShaderEntry(
            "aa-shader-4.0", "AA Shader 4.0", Category.ANTI_ALIASING, Source.CATALOG,
            githubPath = "anti-aliasing/shaders/aa-shader-4.0.glsl"
        ),
    )

    fun getCatalogShaders(): List<ShaderEntry> = catalogShaders

    fun getPreInstalls(): List<ShaderEntry> =
        catalogShaders.filter { it.preInstall }

    fun getMissingPreInstalls(): List<ShaderEntry> =
        getPreInstalls().filter { !isInstalled(it) }

    fun isInstalled(entry: ShaderEntry): Boolean {
        return when (entry.source) {
            Source.CATALOG -> entry.id in getInstalledIds()
            Source.CUSTOM -> true
        }
    }

    fun getCustomShaders(): List<ShaderEntry> {
        val customDir = getCustomShadersDir()
        if (!customDir.exists()) return emptyList()

        return customDir.listFiles()
            ?.filter { it.extension in listOf("glsl", "glslp") }
            ?.map { file ->
                ShaderEntry(
                    id = "custom:${file.nameWithoutExtension}",
                    displayName = file.nameWithoutExtension.replace('-', ' ')
                        .replaceFirstChar { it.uppercase() },
                    category = Category.OTHER,
                    source = Source.CUSTOM,
                    passCount = if (file.extension == "glslp") countPasses(file) else 1
                )
            } ?: emptyList()
    }

    fun getAllShaders(): List<ShaderEntry> = catalogShaders + getCustomShaders()

    fun getShadersByCategory(): Map<Category, List<ShaderEntry>> {
        return getAllShaders().groupBy { it.category }
    }

    fun getShadersForPicker(): List<ShaderEntry> {
        val catalog = catalogShaders.sortedWith(
            compareByDescending<ShaderEntry> { isInstalled(it) }
                .thenByDescending { it.preInstall }
        )
        return catalog + getCustomShaders()
    }

    fun findById(id: String): ShaderEntry? {
        return getAllShaders().find { it.id == id }
    }

    fun resolveChain(chain: ShaderChainConfig): ShaderConfig {
        if (chain.entries.isEmpty()) return ShaderConfig.Default

        val allPasses = chain.entries.flatMap { chainEntry ->
            val entry = findById(chainEntry.shaderId) ?: return@flatMap emptyList()
            try {
                val passes = loadShader(entry).passes
                val paramDefs = loadShaderParameters(entry)
                val defaults = paramDefs.associate { it.name to it.initial }
                val userParams = chainEntry.params.mapNotNull { (k, v) ->
                    v.toFloatOrNull()?.let { k to it }
                }.toMap()
                val resolved = defaults + userParams
                passes.map { pass ->
                    ShaderConfig.Custom.ShaderPass(
                        vertex = processShaderSource(pass.vertex, resolved),
                        fragment = processShaderSource(pass.fragment, resolved),
                        linear = pass.linear
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "resolveChain: failed for ${entry.id}: ${e.message}")
                emptyList()
            }
        }

        if (allPasses.isEmpty()) return ShaderConfig.Default
        return ShaderConfig.Custom(passes = allPasses)
    }

    private val PRAGMA_LINE = Regex("""^\s*#pragma\s+parameter\s""")
    private val DEFINE_LINE = Regex("""^(\s*#define\s+)(\w+)(\s+)([\d.eE+-]+)(.*)$""")
    private val UNIFORM_PARAM = Regex("""^(\s*)uniform\s+(?:\w+\s+)*float\s+(\w+)\s*;(.*)$""")

    fun prepareForUniformParams(source: String): String =
        processShaderSource(source, emptyMap())

    private fun processShaderSource(
        source: String,
        params: Map<String, Float>
    ): String {
        val lines = source.lines().mapNotNull { line ->
            when {
                PRAGMA_LINE.containsMatchIn(line) -> null
                params.isEmpty() -> line
                else -> {
                    val um = UNIFORM_PARAM.matchEntire(line)
                    if (um != null) {
                        val name = um.groupValues[2]
                        val resolved = params[name]
                        if (resolved != null) {
                            "${um.groupValues[1]}const float $name = ${formatGlslFloat(resolved)};${um.groupValues[3]}"
                        } else line
                    } else {
                        val dm = DEFINE_LINE.matchEntire(line)
                        if (dm != null) {
                            val name = dm.groupValues[2]
                            val resolved = params[name]
                            if (resolved != null) {
                                "${dm.groupValues[1]}${name}${dm.groupValues[3]}${formatGlslFloat(resolved)}${dm.groupValues[5]}"
                            } else line
                        } else line
                    }
                }
            }
        }
        return "#define PARAMETER_UNIFORM 1\n#define PARAMETER_UNIFORMS 1\n" + lines.joinToString("\n")
    }

    private fun formatGlslFloat(value: Float): String {
        val s = value.toBigDecimal().stripTrailingZeros().toPlainString()
        return if ('.' in s) s else "$s.0"
    }

    fun loadShaderParameters(entry: ShaderEntry): List<GlslParser.ShaderParameter> {
        return try {
            when (entry.source) {
                Source.CATALOG -> {
                    val file = File(getCatalogDir(), "${entry.id}.glsl")
                    if (file.exists()) GlslParser.parse(file.readText()).parameters else emptyList()
                }
                Source.CUSTOM -> {
                    val id = entry.id.removePrefix("custom:")
                    val glslFile = File(getCustomShadersDir(), "$id.glsl")
                    if (glslFile.exists()) GlslParser.parse(glslFile.readText()).parameters else emptyList()
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun loadShader(entry: ShaderEntry): ShaderConfig.Custom {
        return when (entry.source) {
            Source.CATALOG -> loadCatalogShader(entry.id)
            Source.CUSTOM -> loadCustomShader(entry.id.removePrefix("custom:"))
        }
    }

    private fun loadCatalogShader(id: String): ShaderConfig.Custom {
        val file = File(getCatalogDir(), "$id.glsl")
        if (!file.exists()) {
            throw IllegalStateException("Shader not downloaded: $id")
        }
        val parsed = GlslParser.parse(file.readText())
        return ShaderConfig.Custom(
            passes = listOf(
                ShaderConfig.Custom.ShaderPass(
                    vertex = parsed.vertex,
                    fragment = parsed.fragment,
                    linear = false
                )
            )
        )
    }

    private fun loadCustomShader(name: String): ShaderConfig.Custom {
        val customDir = getCustomShadersDir()

        val glslFile = File(customDir, "$name.glsl")
        if (glslFile.exists()) {
            val parsed = GlslParser.parse(glslFile.readText())
            return ShaderConfig.Custom(
                passes = listOf(
                    ShaderConfig.Custom.ShaderPass(
                        vertex = parsed.vertex,
                        fragment = parsed.fragment,
                        linear = false
                    )
                )
            )
        }

        val glslpFile = File(customDir, "$name.glslp")
        if (glslpFile.exists()) {
            return loadPreset(glslpFile)
        }

        throw IllegalArgumentException("Shader not found: $name")
    }

    private fun loadPreset(presetFile: File): ShaderConfig.Custom {
        val preset = GlslpParser.parse(presetFile.readText())
        val baseDir = presetFile.parentFile ?: getCustomShadersDir()

        val passes = preset.passes.map { pass ->
            val shaderFile = File(baseDir, pass.shaderPath)
            val parsed = GlslParser.parse(shaderFile.readText())
            ShaderConfig.Custom.ShaderPass(
                vertex = parsed.vertex,
                fragment = parsed.fragment,
                linear = pass.filterLinear,
                scale = pass.scale
            )
        }

        return ShaderConfig.Custom(passes = passes)
    }

    private fun countPasses(file: File): Int {
        return try {
            val content = file.readText()
            GlslpParser.parse(content).passes.size
        } catch (_: Exception) { 1 }
    }

    fun getCatalogDir(): File {
        return File(context.getExternalFilesDir(null), "shaders/catalog")
    }

    private fun getCustomShadersDir(): File {
        return File(context.getExternalFilesDir(null), "shaders/custom")
    }

    fun ensureDirectoriesExist() {
        getCatalogDir().mkdirs()
        getCustomShadersDir().mkdirs()
    }

    companion object {
        private const val TAG = "ShaderRegistry"

        const val GITHUB_RAW_BASE =
            "https://raw.githubusercontent.com/libretro/glsl-shaders/master/"

        fun downloadUrl(entry: ShaderEntry): String {
            return "$GITHUB_RAW_BASE${entry.githubPath}"
        }
    }
}
