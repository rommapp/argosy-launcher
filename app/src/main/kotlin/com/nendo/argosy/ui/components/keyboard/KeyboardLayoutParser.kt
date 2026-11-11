package com.nendo.argosy.ui.components.keyboard

import com.nendo.argosy.ui.components.keyboard.FunctionKey
import com.nendo.argosy.ui.components.keyboard.KeyboardKey
import com.nendo.argosy.ui.components.keyboard.KeyboardLayout
import com.nendo.argosy.ui.components.keyboard.KeyboardPage
import com.nendo.argosy.ui.components.keyboard.KeyboardRow
import com.nendo.argosy.ui.components.keyboard.ShiftRule
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonClass
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.Moshi

private const val SHIFT_UPPER = "upper"
private const val SHIFT_NONE = "none"
private const val ENTRY_PAD = "pad"
private const val ENTRY_FUNCTION = "fn"
private const val ENTRY_CHARACTER = "k"
private const val ENTRY_SHIFTED = "s"
private const val ENTRY_WIDTH = "w"
private const val DEFAULT_KEY_WIDTH = 1f

@JsonClass(generateAdapter = true)
internal data class KeyboardLayoutFileDto(
    val id: String,
    val version: Int,
    val script: String,
    val locales: List<String> = emptyList(),
    val composer: String,
    val pages: Map<String, KeyboardPageDto>,
    val accents: Map<String, List<String>> = emptyMap(),
    val accentDefault: Map<String, Map<String, String>> = emptyMap()
)

@JsonClass(generateAdapter = true)
internal data class KeyboardPageDto(
    val shift: String? = null,
    val rows: List<List<Any>>? = null,
    val ref: String? = null,
    val asciiOnly: Boolean = false
)

internal fun parseLayout(
    json: String,
    resolveRef: (String) -> String?,
    moshi: Moshi = Moshi.Builder().build()
): KeyboardLayout {
    val adapter = moshi.adapter(KeyboardLayoutFileDto::class.java)
    val file = adapter.fromJson(json) ?: throw JsonDataException("Empty keyboard layout file")
    if (file.pages.isEmpty()) throw JsonDataException("Keyboard layout ${file.id} has no pages")
    return KeyboardLayout(
        id = file.id,
        script = file.script,
        locales = file.locales,
        composer = file.composer,
        pages = file.pages.map { (pageId, page) -> resolvePage(file.id, pageId, page, adapter, resolveRef) },
        accents = file.accents,
        accentDefaults = file.accentDefault
    )
}

private fun resolvePage(
    layoutId: String,
    pageId: String,
    page: KeyboardPageDto,
    adapter: JsonAdapter<KeyboardLayoutFileDto>,
    resolveRef: (String) -> String?
): KeyboardPage {
    val ref = page.ref ?: return page.toPage(layoutId, pageId, page.asciiOnly)
    val refJson = resolveRef(ref)
        ?: throw JsonDataException("Keyboard page $layoutId/$pageId references missing file $ref")
    val refPages = adapter.fromJson(refJson)?.pages.orEmpty()
    val target = refPages[pageId] ?: refPages.values.singleOrNull()
        ?: throw JsonDataException("Keyboard file $ref has no page $pageId")
    if (target.ref != null) throw JsonDataException("Keyboard page $ref/$pageId is itself a reference")
    return target.toPage(ref, pageId, page.asciiOnly || target.asciiOnly)
}

private fun KeyboardPageDto.toPage(layoutId: String, pageId: String, asciiOnly: Boolean): KeyboardPage {
    val entries = rows.orEmpty()
    if (entries.isEmpty() || entries.any { it.isEmpty() }) {
        throw JsonDataException("Keyboard page $layoutId/$pageId has an empty row")
    }
    return KeyboardPage(
        id = pageId,
        rows = entries.map { row -> KeyboardRow(row.map(::parseKey)) },
        shift = parseShift(shift),
        asciiOnly = asciiOnly
    )
}

private fun parseShift(token: String?): ShiftRule = when (token) {
    null, SHIFT_UPPER -> ShiftRule.Upper
    SHIFT_NONE -> ShiftRule.None
    else -> ShiftRule.Page(token)
}

private fun parseKey(entry: Any): KeyboardKey = when (entry) {
    is String -> KeyboardKey.Character(entry)
    is Map<*, *> -> parseKeyObject(entry)
    else -> throw JsonDataException("Unsupported keyboard key $entry")
}

private fun parseKeyObject(entry: Map<*, *>): KeyboardKey {
    (entry[ENTRY_PAD] as? Number)?.let { return KeyboardKey.Gap(positiveWidth(it.toFloat(), entry)) }
    val width = positiveWidth((entry[ENTRY_WIDTH] as? Number)?.toFloat() ?: DEFAULT_KEY_WIDTH, entry)
    (entry[ENTRY_FUNCTION] as? String)?.let { token ->
        val function = FunctionKey.ofToken(token)
            ?: throw JsonDataException("Unknown keyboard function $token")
        return KeyboardKey.Function(function, width)
    }
    val text = entry[ENTRY_CHARACTER] as? String
        ?: throw JsonDataException("Keyboard key has none of k, fn or pad: $entry")
    return KeyboardKey.Character(text, entry[ENTRY_SHIFTED] as? String, width)
}

private fun positiveWidth(width: Float, entry: Map<*, *>): Float {
    if (width <= 0f) throw JsonDataException("Keyboard key width must be positive: $entry")
    return width
}
