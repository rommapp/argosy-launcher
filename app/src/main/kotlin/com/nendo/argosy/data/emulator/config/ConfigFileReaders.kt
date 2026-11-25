package com.nendo.argosy.data.emulator.config

import com.nendo.argosy.data.emulator.savepath.IniSections
import org.json.JSONArray
import org.json.JSONObject
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import javax.xml.parsers.DocumentBuilderFactory

/**
 * One stored key of a config file, located the way [ConfigOption.section] and [ConfigOption.key]
 * locate an option.
 */
data class ConfigEntry(val section: String, val key: String, val value: String)

internal object ConfigFileReaders {

    /**
     * The entries of [text] in file order, or null when [text] is not well-formed [format].
     */
    fun read(format: ConfigFormat, text: String): List<ConfigEntry>? = when (format) {
        ConfigFormat.INI -> ini(text)
        ConfigFormat.XML -> xml(text)
        ConfigFormat.YAML -> yaml(text)
        ConfigFormat.JSON -> json(text)
        ConfigFormat.PREFS_JSON -> prefsJson(text)
    }

    /**
     * Each preference of a SharedPreferences file. A string preference holding a JSON object
     * yields that object's scalars, sectioned by the preference name; any other preference is a
     * top-level key.
     */
    fun prefsJson(text: String): List<ConfigEntry>? {
        val root = parseXml(text) ?: return null
        val entries = mutableListOf<ConfigEntry>()
        childElements(root).forEach { element ->
            val name = element.getAttribute(PREFS_NAME)
            if (name.isEmpty()) return@forEach
            val value = if (element.hasAttribute(PREFS_VALUE)) element.getAttribute(PREFS_VALUE) else element.textContent.orEmpty()
            val nested = if (element.tagName == PREFS_STRING) runCatching { JSONObject(value) }.getOrNull() else null
            if (nested != null) collectJson(nested, name, entries) else entries += ConfigEntry("", name, value)
        }
        return entries
    }

    fun ini(text: String): List<ConfigEntry> =
        IniSections.allEntries(text).map { ConfigEntry(it.section, it.key, it.value) }

    /**
     * Leaf elements below the document root, keyed by their parent's path, plus each attribute as
     * an `@name` key of its element's path.
     */
    fun xml(text: String): List<ConfigEntry>? {
        val root = parseXml(text) ?: return null
        val entries = mutableListOf<ConfigEntry>()
        childElements(root).forEach { collectXml(it, "", entries) }
        return entries
    }

    private fun parseXml(text: String): Element? = runCatching {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            isExpandEntityReferences = false
            runCatching { setFeature(DISALLOW_DOCTYPE, true) }
        }
        factory.newDocumentBuilder().parse(ByteArrayInputStream(text.toByteArray(Charsets.UTF_8))).documentElement
    }.getOrNull()

    private fun collectXml(element: Element, parentPath: String, out: MutableList<ConfigEntry>) {
        val name = element.tagName
        val path = if (parentPath.isEmpty()) name else "$parentPath/$name"
        val attributes = element.attributes
        for (index in 0 until attributes.length) {
            val attribute = attributes.item(index)
            out += ConfigEntry(path, "@${attribute.nodeName}", attribute.nodeValue.orEmpty())
        }
        val children = childElements(element)
        if (children.isEmpty()) {
            val text = element.textContent.orEmpty().trim()
            if (attributes.length == 0 || text.isNotEmpty()) out += ConfigEntry(parentPath, name, text)
            return
        }
        children.forEach { collectXml(it, path, out) }
    }

    private fun childElements(element: Element): List<Element> {
        val nodes = element.childNodes
        return (0 until nodes.length).map { nodes.item(it) }
            .filter { it.nodeType == Node.ELEMENT_NODE }
            .map { it as Element }
    }

    /**
     * The top-level `key: value` lines of a YAML mapping. A key whose value is an indented block
     * of `- item` lines reads as `[item, item]`; quotes around a scalar are dropped.
     */
    fun yaml(text: String): List<ConfigEntry> {
        val entries = mutableListOf<ConfigEntry>()
        var blockKey: String? = null
        val blockItems = mutableListOf<String>()
        fun closeBlock() {
            val key = blockKey ?: return
            entries += ConfigEntry("", key, blockItems.joinToString(", ", "[", "]"))
            blockKey = null
            blockItems.clear()
        }
        for (raw in text.lineSequence()) {
            val line = raw.trimEnd()
            if (line.isBlank() || line.trimStart().startsWith("#")) continue
            if (line.first().isWhitespace()) {
                val item = line.trim()
                if (blockKey != null && item.startsWith("- ")) blockItems += unquoteYaml(item.removePrefix("- ").trim())
                continue
            }
            closeBlock()
            if (line == YAML_DOCUMENT_START || line == YAML_DOCUMENT_END) continue
            val colon = line.indexOf(':').takeIf { it > 0 } ?: continue
            val key = line.substring(0, colon).trim()
            val value = line.substring(colon + 1).trim()
            if (value.isEmpty()) blockKey = key else entries += ConfigEntry("", key, unquoteYaml(value))
        }
        closeBlock()
        return entries
    }

    private fun unquoteYaml(value: String): String {
        if (value.length >= 2 && (value.first() == '"' || value.first() == '\'') && value.last() == value.first()) {
            return value.substring(1, value.length - 1)
        }
        return value
    }

    /**
     * Every scalar of a JSON object, keyed by the slash-joined path of the objects holding it.
     * Arrays stay as their JSON text.
     */
    fun json(text: String): List<ConfigEntry>? {
        val root = runCatching { JSONObject(text) }.getOrNull() ?: return null
        val entries = mutableListOf<ConfigEntry>()
        collectJson(root, "", entries)
        return entries
    }

    private fun collectJson(obj: JSONObject, path: String, out: MutableList<ConfigEntry>) {
        obj.keys().asSequence().toList().forEach { key ->
            when (val value = obj.get(key)) {
                is JSONObject -> collectJson(value, if (path.isEmpty()) key else "$path/$key", out)
                is JSONArray -> out += ConfigEntry(path, key, value.toString())
                else -> out += ConfigEntry(path, key, value.toString())
            }
        }
    }

    private const val DISALLOW_DOCTYPE = "http://apache.org/xml/features/disallow-doctype-decl"
    private const val PREFS_NAME = "name"
    private const val PREFS_VALUE = "value"
    private const val PREFS_STRING = "string"
    private const val YAML_DOCUMENT_START = "---"
    private const val YAML_DOCUMENT_END = "..."
}

internal object DolphinTimePlayed {
    private const val SECTION = "TimePlayed"
    private const val HEX_PREFIX = "0x"
    private const val HEX_RADIX = 16

    /**
     * Milliseconds played per game ID in a Dolphin `TimePlayed.ini`, whose values are u64 written
     * as `0x` and sixteen hex digits. A value that does not decode is left out.
     */
    fun decode(text: String): Map<String, Long> =
        IniSections.entries(text, SECTION).mapNotNull { (gameId, value) ->
            val hex = value.trim().removePrefix(HEX_PREFIX).removePrefix(HEX_PREFIX.uppercase())
            hex.toULongOrNull(HEX_RADIX)?.takeIf { it <= Long.MAX_VALUE.toULong() }?.let { gameId to it.toLong() }
        }.toMap()
}
