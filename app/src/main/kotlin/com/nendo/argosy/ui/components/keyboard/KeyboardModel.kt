package com.nendo.argosy.ui.components.keyboard

import java.util.Locale

enum class FunctionKey(val token: String) {
    SHIFT("shift"),
    BACKSPACE("backspace"),
    SPACE("space"),
    SYMBOLS("symbols"),
    LETTERS("letters"),
    LAYOUT("layout"),
    ENTER("enter");

    companion object {
        fun ofToken(token: String): FunctionKey? = entries.firstOrNull { it.token == token }
    }
}

sealed interface KeyboardKey {
    val width: Float

    data class Character(
        val text: String,
        val shifted: String? = null,
        override val width: Float = 1f
    ) : KeyboardKey

    data class Function(
        val function: FunctionKey,
        override val width: Float = 1f
    ) : KeyboardKey

    data class Gap(override val width: Float) : KeyboardKey
}

data class KeyboardRow(val keys: List<KeyboardKey>)

sealed interface ShiftRule {
    data object Upper : ShiftRule
    data class Page(val pageId: String) : ShiftRule
    data object None : ShiftRule
}

data class KeyboardPage(
    val id: String,
    val rows: List<KeyboardRow>,
    val shift: ShiftRule = ShiftRule.Upper,
    val asciiOnly: Boolean = false
)

data class KeyboardLayout(
    val id: String,
    val script: String,
    val locales: List<String>,
    val composer: String,
    val pages: List<KeyboardPage>,
    val accents: Map<String, List<String>>,
    val accentDefaults: Map<String, Map<String, String>> = emptyMap()
) {
    fun page(id: String): KeyboardPage? = pages.firstOrNull { it.id == id }

    val localeTag: String? get() = locales.firstOrNull()

    companion object {
        const val LETTERS_PAGE = "letters"
        const val SYMBOLS_PAGE = "symbols"
        const val NUMERIC_LAYOUT_ID = "numeric"
        const val QWERTY_LAYOUT_ID = "latin-qwerty"
        const val AZERTY_LAYOUT_ID = "latin-azerty"
        const val QWERTZ_LAYOUT_ID = "latin-qwertz"
        const val JCUKEN_LAYOUT_ID = "cyrillic-jcuken"
        const val HANGUL_LAYOUT_ID = "hangul-2set"
        const val SHARED_SYMBOLS_ID = "shared-symbols"
    }
}

enum class KeyboardFieldType { TEXT, URI, EMAIL, PASSWORD, NUMBER }

val KeyboardFieldType.isAsciiOnly: Boolean
    get() = this == KeyboardFieldType.URI || this == KeyboardFieldType.EMAIL || this == KeyboardFieldType.PASSWORD

/**
 * Layout id the field type forces, or null when the user's chosen layout applies.
 */
val KeyboardFieldType.requiredLayoutId: String?
    get() = when {
        isAsciiOnly -> KeyboardLayout.QWERTY_LAYOUT_ID
        this == KeyboardFieldType.NUMBER -> KeyboardLayout.NUMERIC_LAYOUT_ID
        else -> null
    }

enum class KeyboardFeedback {
    NAVIGATE,
    SELECT,
    BACK,
    TOGGLE,
    TOGGLE_ON,
    TOGGLE_OFF,
    BOUNDARY,
    ERROR,
    NONE,
    SUBMITTED,
    CLOSED,
    LAYOUT_REQUESTED
}

data class KeyboardSession(
    val label: String,
    val submitLabel: String,
    val languageLabel: String,
    val initialText: String = "",
    val placeholder: String? = null,
    val error: String? = null,
    val fieldType: KeyboardFieldType = KeyboardFieldType.TEXT,
    val canSubmit: Boolean = true,
    val enabled: Boolean = true,
    val maxLength: Int? = null,
    val initialFocus: KeyPosition? = null
)

enum class ShiftState { OFF, ONCE, LOCKED }

data class KeyPosition(val row: Int, val index: Int)

data class AccentStrip(
    val anchor: KeyPosition,
    val options: List<String>,
    val focused: Int
)

data class KeyboardUiState(
    val layoutId: String,
    val pageId: String,
    val rows: List<KeyboardRow>,
    val focus: KeyPosition,
    val shift: ShiftState,
    val text: String,
    val caret: Int,
    val label: String,
    val placeholder: String?,
    val error: String?,
    val fieldType: KeyboardFieldType,
    val submitLabel: String,
    val canSubmit: Boolean,
    val enabled: Boolean,
    val maxLength: Int?,
    val languageLabel: String,
    val accentStrip: AccentStrip?,
    val shiftRule: ShiftRule = ShiftRule.Upper,
    val localeTag: String? = null,
    val pasteAvailable: Boolean = false,
    val pasteFocused: Boolean = false
) {
    val units: Float get() = rows.maxOfOrNull { row -> row.keys.sumOf { it.width.toDouble() }.toFloat() } ?: 1f

    /**
     * The text [key] types and displays under the current shift state.
     */
    fun typedText(key: KeyboardKey.Character): String = when {
        shift == ShiftState.OFF -> key.text
        key.shifted != null -> key.shifted
        shiftRule == ShiftRule.Upper -> key.text.uppercase(keyboardLocale(localeTag))
        else -> key.text
    }
}

internal fun keyboardLocale(tag: String?): Locale = tag?.let(Locale::forLanguageTag) ?: Locale.ROOT
