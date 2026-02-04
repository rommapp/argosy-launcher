package com.nendo.argosy.libretro.shader

object GlslParser {

    data class ParsedShader(
        val vertex: String,
        val fragment: String,
        val parameters: List<ShaderParameter> = emptyList()
    )

    data class ShaderParameter(
        val name: String,
        val description: String,
        val initial: Float,
        val min: Float,
        val max: Float,
        val step: Float
    )

    private val VERTEX_START = Regex(
        """#if\s+defined\s*\(\s*VERTEX\s*\)|#ifdef\s+VERTEX"""
    )
    private val FRAGMENT_START = Regex(
        """#elif\s+defined\s*\(\s*FRAGMENT\s*\)|#else\s*$|#ifdef\s+FRAGMENT"""
    )
    private val ENDIF = Regex("""#endif""")
    private val IF_OPEN = Regex("""^\s*#\s*(if|ifdef|ifndef)\b""")
    private val IF_CLOSE = Regex("""^\s*#\s*endif\b""")

    private val VERSION_DIRECTIVE = Regex("""^\s*#version\s+\d+.*$""")

    private val PRAGMA_PARAMETER = Regex(
        """#pragma\s+parameter\s+(\w+)\s+"([^"]+)"\s+([\d.+-]+)\s+([\d.+-]+)\s+([\d.+-]+)\s+([\d.+-]+)"""
    )

    private const val ES_PREAMBLE = """#ifdef GL_ES
#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif
#endif
"""

    fun parse(source: String): ParsedShader {
        val lines = source.lines()
        val parameters = parseParameters(lines)

        var vertexStart = -1
        var fragmentStart = -1
        var endifLine = -1
        var depth = 0

        for ((i, line) in lines.withIndex()) {
            when {
                vertexStart == -1 -> {
                    if (VERTEX_START.containsMatchIn(line)) vertexStart = i
                }
                fragmentStart == -1 -> {
                    when {
                        depth == 0 && FRAGMENT_START.containsMatchIn(line) -> {
                            fragmentStart = i
                        }
                        IF_OPEN.containsMatchIn(line) -> depth++
                        IF_CLOSE.containsMatchIn(line) -> { if (depth > 0) depth-- }
                    }
                }
                else -> {
                    when {
                        IF_OPEN.containsMatchIn(line) -> depth++
                        IF_CLOSE.containsMatchIn(line) -> {
                            if (depth > 0) {
                                depth--
                            } else {
                                endifLine = i
                                break
                            }
                        }
                    }
                }
            }
        }

        if (vertexStart == -1 || fragmentStart == -1) {
            throw IllegalArgumentException(
                "GLSL file missing #if defined(VERTEX) / #elif defined(FRAGMENT) structure"
            )
        }

        val preamble = if (vertexStart > 0) {
            lines.subList(0, vertexStart).joinToString("\n")
        } else ""

        val vertexBody = lines.subList(vertexStart + 1, fragmentStart).joinToString("\n")
        val fragmentBody = if (endifLine != -1) {
            lines.subList(fragmentStart + 1, endifLine).joinToString("\n")
        } else {
            lines.subList(fragmentStart + 1, lines.size).joinToString("\n")
        }

        val vertexSource = buildShaderSource(preamble, vertexBody, needsPrecision = false)
        val fragmentSource = buildShaderSource(preamble, fragmentBody, needsPrecision = true)

        return ParsedShader(
            vertex = vertexSource,
            fragment = fragmentSource,
            parameters = parameters
        )
    }

    private fun buildShaderSource(preamble: String, body: String, needsPrecision: Boolean): String {
        val combined = if (preamble.isNotBlank()) "$preamble\n$body" else body
        val stripped = combined.lines()
            .filterNot { VERSION_DIRECTIVE.matches(it) }
            .joinToString("\n")

        val hasPrecision = stripped.contains("precision ") &&
            (stripped.contains("precision mediump") || stripped.contains("precision highp"))

        return if (needsPrecision && !hasPrecision) {
            "$ES_PREAMBLE\n$stripped"
        } else {
            stripped
        }
    }

    private fun parseParameters(lines: List<String>): List<ShaderParameter> {
        return lines.mapNotNull { line ->
            PRAGMA_PARAMETER.find(line)?.let { match ->
                val (name, description, initial, min, max, step) = match.destructured
                ShaderParameter(
                    name = name,
                    description = description,
                    initial = initial.toFloatOrNull() ?: 0f,
                    min = min.toFloatOrNull() ?: 0f,
                    max = max.toFloatOrNull() ?: 1f,
                    step = step.toFloatOrNull() ?: 0.01f
                )
            }
        }
    }
}
