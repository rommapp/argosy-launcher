package com.nendo.argosy.data.emulator.config

internal object ConfigPatchers {

    /**
     * [text] with [option] set to [token] and every other byte kept, or null when the file has no
     * place the option can be written.
     */
    fun patch(spec: EmulatorConfigSpec, option: ConfigOption, token: String, text: String): String? =
        when (option.file.format) {
            ConfigFormat.INI -> IniPatcher.set(
                text = text,
                section = option.section,
                key = option.key,
                value = token,
                ignoreCase = spec.keysIgnoreCase,
                defaultFlagSuffix = spec.compiledDefaultFlag
            )
            ConfigFormat.XML -> XmlPatcher.set(text, option.section, option.key, token)
            ConfigFormat.YAML -> if (option.section.isEmpty()) YamlPatcher.set(text, option.key, token) else null
            ConfigFormat.JSON -> null
            ConfigFormat.PREFS_JSON -> PrefsJsonPatcher.set(text, option.section, option.key, token, quoted(option, token))
        }

    private fun quoted(option: ConfigOption, token: String): Boolean = when (option.type) {
        ConfigValueType.STRING -> true
        ConfigValueType.ENUM -> token.toDoubleOrNull() == null
        else -> false
    }
}

internal object PrefsJsonPatcher {
    private const val CLOSE = "</string>"

    /**
     * [text] with the top-level [key] of the JSON object stored in string preference [preference]
     * set to [value], written as a JSON string when [quoted]. A value already stored keeps its own
     * kind. Null when the preference is absent, does not hold a JSON object, or its escaping is
     * not the form Android writes, so rewriting it would change bytes outside the key.
     */
    fun set(text: String, preference: String, key: String, value: String, quoted: Boolean): String? {
        val open = "<string name=\"${XmlText.escape(preference)}\">"
        val start = text.indexOf(open).takeIf { it >= 0 }?.plus(open.length) ?: return null
        val end = text.indexOf(CLOSE, start).takeIf { it >= 0 } ?: return null
        val escaped = text.substring(start, end)
        val json = XmlText.unescape(escaped) ?: return null
        if (XmlText.escape(json) != escaped) return null
        val patched = JsonTopLevel.set(json, key, value, quoted) ?: return null
        return text.substring(0, start) + XmlText.escape(patched) + text.substring(end)
    }
}

internal object XmlText {
    private val NAMED = mapOf("amp" to '&', "lt" to '<', "gt" to '>', "quot" to '"', "apos" to '\'')

    fun escape(value: String): String = buildString {
        value.forEach { c ->
            when (c) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                else -> append(c)
            }
        }
    }

    fun unescape(value: String): String? {
        val out = StringBuilder()
        var index = 0
        while (index < value.length) {
            val c = value[index]
            if (c != '&') {
                out.append(c)
                index++
                continue
            }
            val semi = value.indexOf(';', index).takeIf { it > index } ?: return null
            val entity = value.substring(index + 1, semi)
            val decoded = when {
                entity.startsWith("#x") -> entity.drop(2).toIntOrNull(16)?.toChar()
                entity.startsWith("#") -> entity.drop(1).toIntOrNull()?.toChar()
                else -> NAMED[entity]
            } ?: return null
            out.append(decoded)
            index = semi + 1
        }
        return out.toString()
    }
}

internal object JsonTopLevel {
    private const val OBJECT_OPEN = '{'
    private const val OBJECT_CLOSE = '}'

    /**
     * [json] with the top-level [key] of its object set to [value], every other byte kept. A
     * missing key is appended before the closing brace. Null when [json] is not an object.
     */
    fun set(json: String, key: String, value: String, quoted: Boolean): String? {
        val open = json.indexOfFirst { !it.isWhitespace() }.takeIf { it >= 0 && json[it] == OBJECT_OPEN } ?: return null
        var index = open + 1
        var depth = 1
        var members = 0
        while (index < json.length) {
            val c = json[index]
            when {
                c == '"' -> {
                    val close = stringEnd(json, index) ?: return null
                    val after = skipSpace(json, close + 1)
                    if (depth == 1 && after < json.length && json[after] == ':') {
                        members++
                        val valueStart = skipSpace(json, after + 1)
                        val valueEnd = valueEnd(json, valueStart) ?: return null
                        if (json.substring(index + 1, close) == encode(key)) {
                            if (json[valueStart] == OBJECT_OPEN || json[valueStart] == '[') return null
                            val asString = json[valueStart] == '"'
                            return json.substring(0, valueStart) + literal(value, asString) + json.substring(valueEnd)
                        }
                        index = valueEnd
                        continue
                    }
                    index = close + 1
                    continue
                }
                c == OBJECT_OPEN || c == '[' -> depth++
                c == OBJECT_CLOSE || c == ']' -> {
                    depth--
                    if (depth == 0) {
                        val member = "\"${encode(key)}\":${literal(value, quoted)}"
                        val separator = if (members > 0) "," else ""
                        return json.substring(0, index) + separator + member + json.substring(index)
                    }
                }
            }
            index++
        }
        return null
    }

    private fun literal(value: String, quoted: Boolean): String = if (quoted) "\"${encode(value)}\"" else value

    private fun encode(value: String): String = value.replace("\\", "\\\\").replace("\"", "\\\"")

    private fun skipSpace(json: String, from: Int): Int {
        var index = from
        while (index < json.length && json[index].isWhitespace()) index++
        return index
    }

    private fun stringEnd(json: String, open: Int): Int? {
        var index = open + 1
        while (index < json.length) {
            when (json[index]) {
                '\\' -> index += 2
                '"' -> return index
                else -> index++
            }
        }
        return null
    }

    private fun compositeEnd(json: String, start: Int): Int? {
        var depth = 0
        var index = start
        while (index < json.length) {
            when (json[index]) {
                '"' -> index = stringEnd(json, index) ?: return null
                OBJECT_OPEN, '[' -> depth++
                OBJECT_CLOSE, ']' -> {
                    depth--
                    if (depth == 0) return index + 1
                }
            }
            index++
        }
        return null
    }

    private fun valueEnd(json: String, start: Int): Int? {
        if (start >= json.length) return null
        return when (json[start]) {
            '"' -> stringEnd(json, start)?.plus(1)
            OBJECT_OPEN, '[' -> compositeEnd(json, start)
            else -> {
                var index = start
                while (index < json.length && json[index] != ',' && json[index] != OBJECT_CLOSE && !json[index].isWhitespace()) index++
                index
            }
        }
    }
}

internal data class TextLine(val content: String, val eol: String)

internal object TextLines {
    private const val LF = "\n"
    private const val CRLF = "\r\n"

    fun split(text: String): List<TextLine> {
        val lines = mutableListOf<TextLine>()
        var start = 0
        while (start < text.length) {
            val lf = text.indexOf('\n', start)
            if (lf < 0) {
                lines += TextLine(text.substring(start), "")
                break
            }
            val crlf = lf > start && text[lf - 1] == '\r'
            lines += TextLine(text.substring(start, if (crlf) lf - 1 else lf), if (crlf) CRLF else LF)
            start = lf + 1
        }
        return lines
    }

    fun join(lines: List<TextLine>): String = lines.joinToString("") { it.content + it.eol }

    fun eolOf(lines: List<TextLine>): String = lines.firstOrNull { it.eol.isNotEmpty() }?.eol ?: LF

    /**
     * [lines] with [added] appended, each ended by [eol], after closing an unterminated last line.
     */
    fun append(lines: List<TextLine>, added: List<String>, eol: String): List<TextLine> {
        val closed = lines.toMutableList()
        closed.lastOrNull()?.takeIf { it.eol.isEmpty() }?.let { closed[closed.lastIndex] = it.copy(eol = eol) }
        return closed + added.map { TextLine(it, eol) }
    }
}

internal object IniPatcher {
    private const val COMPILED_DEFAULT_OFF = "false"
    private const val DEFAULT_SEPARATOR = "="

    private data class KeyLine(val index: Int, val key: String, val prefix: String, val value: String)

    /**
     * [text] with [key] in [section] set to [value]. With [defaultFlagSuffix], the companion
     * `key + suffix` is set to `false` as well. A missing key goes right below its section header,
     * a missing section at the end of the file.
     */
    fun set(
        text: String,
        section: String,
        key: String,
        value: String,
        ignoreCase: Boolean,
        defaultFlagSuffix: String?
    ): String {
        val lines = TextLines.split(text).toMutableList()
        val eol = TextLines.eolOf(lines)
        val separator = separatorOf(lines)
        val flagKey = defaultFlagSuffix?.let { key + it }
        val header = headerIndex(lines, section, ignoreCase)
        if (header == null) {
            val added = buildList {
                if (lines.any { it.content.isNotBlank() }) add("")
                add("[$section]")
                flagKey?.let { add("$it$separator$COMPILED_DEFAULT_OFF") }
                add("$key$separator$value")
            }
            return TextLines.join(TextLines.append(lines, added, eol))
        }
        val keyLines = keyLinesOf(lines, header)
        var target = keyLines.lastOrNull { it.key.equals(key, ignoreCase) }
        val flag = flagKey?.let { wanted -> keyLines.lastOrNull { it.key.equals(wanted, ignoreCase) } }
        if (flagKey != null) {
            if (flag != null) {
                lines[flag.index] = lines[flag.index].copy(content = withValue(flag, COMPILED_DEFAULT_OFF))
            } else {
                lines.add(header + 1, TextLine("$flagKey$separator$COMPILED_DEFAULT_OFF", eol))
                target = target?.let { it.copy(index = it.index + 1) }
            }
        }
        if (target != null) {
            lines[target.index] = lines[target.index].copy(content = withValue(target, value))
        } else {
            val after = if (flagKey != null) (flag?.index ?: header + 1) else header
            lines.add(after + 1, TextLine("$key$separator$value", eol))
        }
        closeLineBefore(lines, eol)
        return TextLines.join(lines)
    }

    private fun closeLineBefore(lines: MutableList<TextLine>, eol: String) {
        for (index in 0 until lines.lastIndex) {
            if (lines[index].eol.isEmpty()) lines[index] = lines[index].copy(eol = eol)
        }
    }

    private fun headerIndex(lines: List<TextLine>, section: String, ignoreCase: Boolean): Int? =
        lines.indexOfFirst { sectionOf(it.content)?.equals(section, ignoreCase) == true }.takeIf { it >= 0 }

    private fun sectionOf(content: String): String? {
        val line = content.trimStart()
        if (!line.startsWith("[")) return null
        return line.substring(1).substringBefore(']', missingDelimiterValue = "").takeIf { line.contains(']') }
    }

    private fun keyLinesOf(lines: List<TextLine>, header: Int): List<KeyLine> {
        val found = mutableListOf<KeyLine>()
        for (index in header + 1 until lines.size) {
            val content = lines[index].content
            val trimmed = content.trimStart()
            if (sectionOf(content) != null) break
            if (trimmed.isEmpty() || trimmed.startsWith(";")) continue
            keyLine(index, content)?.let { found += it }
        }
        return found
    }

    private fun keyLine(index: Int, content: String): KeyLine? {
        val eq = content.indexOf('=').takeIf { it > 0 } ?: return null
        val key = content.substring(0, eq).trim(' ', '\t')
        if (key.isEmpty()) return null
        var valueStart = eq + 1
        while (valueStart < content.length && (content[valueStart] == ' ' || content[valueStart] == '\t')) valueStart++
        return KeyLine(index, key, content.substring(0, valueStart), content.substring(valueStart))
    }

    private fun withValue(line: KeyLine, value: String): String {
        val quoted = line.value.length >= 2 && line.value.startsWith("\"") && line.value.endsWith("\"")
        return line.prefix + if (quoted) "\"$value\"" else value
    }

    private fun separatorOf(lines: List<TextLine>): String {
        lines.forEachIndexed { index, line ->
            val trimmed = line.content.trimStart()
            if (trimmed.isEmpty() || trimmed.startsWith(";") || sectionOf(line.content) != null) return@forEachIndexed
            val parsed = keyLine(index, line.content) ?: return@forEachIndexed
            val leading = line.content.length - trimmed.length
            return parsed.prefix.substring(leading + parsed.key.length)
        }
        return DEFAULT_SEPARATOR
    }
}

internal object XmlPatcher {
    private const val PI_OPEN = "<?"
    private const val PI_CLOSE = "?>"
    private const val COMMENT_OPEN = "<!--"
    private const val COMMENT_CLOSE = "-->"
    private const val CDATA_OPEN = "<![CDATA["
    private const val CDATA_CLOSE = "]]>"

    /**
     * [text] with the text of the leaf element at [section]/[key] below the root set to [value].
     * Null when that element is absent, holds child elements, or the document cannot be scanned.
     */
    fun set(text: String, section: String, key: String, value: String): String? {
        val wanted = section.split('/').filter { it.isNotEmpty() } + key
        val stack = mutableListOf<String>()
        var cursor = 0
        while (true) {
            val open = text.indexOf('<', cursor).takeIf { it >= 0 } ?: return null
            cursor = when {
                text.startsWith(PI_OPEN, open) -> endOf(text, PI_CLOSE, open) ?: return null
                text.startsWith(COMMENT_OPEN, open) -> endOf(text, COMMENT_CLOSE, open) ?: return null
                text.startsWith(CDATA_OPEN, open) -> endOf(text, CDATA_CLOSE, open) ?: return null
                text.startsWith("<!", open) -> endOf(text, ">", open) ?: return null
                text.startsWith("</", open) -> {
                    if (stack.isEmpty()) return null
                    stack.removeAt(stack.lastIndex)
                    endOf(text, ">", open) ?: return null
                }
                else -> {
                    val close = tagEnd(text, open) ?: return null
                    val selfClosing = text[close - 1] == '/'
                    val name = text.substring(open + 1, close).takeWhile { !it.isWhitespace() && it != '/' && it != '>' }
                    if (stack.isNotEmpty() && stack.drop(1) + name == wanted) {
                        return replaceLeaf(text, open, close, name, selfClosing, escape(value))
                    }
                    if (!selfClosing) stack += name
                    close + 1
                }
            }
        }
    }

    private fun replaceLeaf(text: String, open: Int, close: Int, name: String, selfClosing: Boolean, value: String): String? {
        if (selfClosing) {
            return text.substring(0, open) + "<$name>$value</$name>" + text.substring(close + 1)
        }
        val end = text.indexOf("</$name", close + 1).takeIf { it >= 0 } ?: return null
        if (text.substring(close + 1, end).contains('<')) return null
        return text.substring(0, close + 1) + value + text.substring(end)
    }

    private fun endOf(text: String, marker: String, from: Int): Int? =
        text.indexOf(marker, from).takeIf { it >= 0 }?.plus(marker.length)

    private fun tagEnd(text: String, open: Int): Int? {
        var quote: Char? = null
        for (index in open + 1 until text.length) {
            val c = text[index]
            when {
                quote != null -> if (c == quote) quote = null
                c == '"' || c == '\'' -> quote = c
                c == '>' -> return index
            }
        }
        return null
    }

    private fun escape(value: String): String = buildString {
        value.forEach { c ->
            when (c) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&apos;")
                else -> append(c)
            }
        }
    }
}

internal object YamlPatcher {
    private const val DOCUMENT_END = "..."

    /**
     * [text] with the top-level scalar [key] set to [value], keeping the line's quote style. A
     * missing key goes before the document end marker, or at the end. Null when [key] holds a block.
     */
    fun set(text: String, key: String, value: String): String? {
        val lines = TextLines.split(text).toMutableList()
        val eol = TextLines.eolOf(lines)
        val index = lines.indexOfFirst { keyOf(it.content) == key }
        if (index < 0) {
            val end = lines.indexOfFirst { it.content.trimEnd() == DOCUMENT_END }
            if (end < 0) return TextLines.join(TextLines.append(lines, listOf("$key: $value"), eol))
            lines.add(end, TextLine("$key: $value", eol))
            return TextLines.join(lines)
        }
        val content = lines[index].content
        val colon = content.indexOf(':')
        val rest = content.substring(colon + 1)
        val current = rest.trim()
        if (current.isEmpty()) return null
        val leading = rest.substring(0, rest.indexOf(current[0]))
        val quote = current.first().takeIf { (it == '"' || it == '\'') && current.length >= 2 && current.last() == it }
        val written = quote?.let { "$it$value$it" } ?: value
        lines[index] = lines[index].copy(content = content.substring(0, colon + 1) + leading.ifEmpty { " " } + written)
        return TextLines.join(lines)
    }

    private fun keyOf(content: String): String? {
        if (content.isEmpty() || content.first().isWhitespace() || content.startsWith("#")) return null
        val colon = content.indexOf(':').takeIf { it > 0 } ?: return null
        return content.substring(0, colon).trim()
    }
}
