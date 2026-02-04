package com.nendo.argosy.libretro.shader

object GlslpParser {

    data class PresetPass(
        val shaderPath: String,
        val filterLinear: Boolean = false,
        val scale: Float = 1.0f,
        val scaleType: String = "source"
    )

    data class Preset(
        val passes: List<PresetPass>,
        val parameters: Map<String, String> = emptyMap()
    )

    fun parse(content: String): Preset {
        val props = mutableMapOf<String, String>()

        for (line in content.lines()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue

            val eqIndex = trimmed.indexOf('=')
            if (eqIndex == -1) continue

            val key = trimmed.substring(0, eqIndex).trim()
            val value = trimmed.substring(eqIndex + 1).trim().removeSurrounding("\"")
            props[key] = value
        }

        val shaderCount = props["shaders"]?.toIntOrNull() ?: 0
        val passes = (0 until shaderCount).map { i ->
            PresetPass(
                shaderPath = props["shader$i"] ?: "",
                filterLinear = props["filter_linear$i"]?.toBooleanStrictOrNull() ?: false,
                scale = props["scale$i"]?.toFloatOrNull() ?: 1.0f,
                scaleType = props["scale_type$i"] ?: "source"
            )
        }

        val parameters = props.filterKeys { key ->
            key != "shaders" &&
                !key.startsWith("shader") &&
                !key.startsWith("filter_linear") &&
                !key.startsWith("scale") &&
                !key.startsWith("scale_type") &&
                !key.startsWith("wrap_mode") &&
                !key.startsWith("mipmap_input") &&
                !key.startsWith("alias") &&
                !key.startsWith("float_framebuffer") &&
                !key.startsWith("srgb_framebuffer")
        }

        return Preset(passes = passes, parameters = parameters)
    }
}
