package com.nendo.argosy.ui.components.keyboard

import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sign
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet

/**
 * Focus, shift, page and text state for one keyboard session. Every operation returns the
 * [KeyboardFeedback] the host turns into sound and haptics. The host picks the starting layout
 * with [requiredLayoutId], and [onTextChange] receives the full text after every edit.
 */
class KeyboardEngine(
    layout: KeyboardLayout,
    session: KeyboardSession,
    private val doubleTapTimeoutMs: Long = DEFAULT_DOUBLE_TAP_TIMEOUT_MS,
    private val onTextChange: (String) -> Unit = {},
    private val nowMs: () -> Long = { System.nanoTime() / NANOS_PER_MILLI },
    private val readClipboard: () -> String? = { null }
) {
    private var activeLayout: KeyboardLayout = layout
    private var composer: KeyboardComposer? = keyboardComposerFor(layout.composer)
    private var column: Float
    private var lastShiftClickMs: Long? = null

    private val _state: MutableStateFlow<KeyboardUiState>
    val state: StateFlow<KeyboardUiState>

    init {
        val page = layout.page(KeyboardLayout.LETTERS_PAGE) ?: layout.pages.first()
        val rows = composeRows(layout, page, session.fieldType)
        val focus = session.initialFocus?.takeIf { rows.isFocusable(it) } ?: homeKey(rows)
        column = centerOf(rows, focus)
        _state = MutableStateFlow(
            KeyboardUiState(
                layoutId = layout.id,
                pageId = page.id,
                rows = rows,
                focus = focus,
                shift = ShiftState.OFF,
                text = session.initialText,
                caret = session.initialText.length,
                label = session.label,
                placeholder = session.placeholder,
                error = session.error,
                fieldType = session.fieldType,
                submitLabel = session.submitLabel,
                canSubmit = session.canSubmit,
                enabled = session.enabled,
                maxLength = session.maxLength,
                languageLabel = session.languageLabel,
                accentStrip = null,
                shiftRule = page.shift,
                localeTag = layout.localeTag
            )
        )
        state = _state.asStateFlow()
    }

    fun move(dx: Int, dy: Int): KeyboardFeedback {
        val current = _state.value
        if (!current.enabled) return KeyboardFeedback.NONE
        if (current.accentStrip != null) return moveInStrip(dx)
        if (dx == 0 && dy == 0) return KeyboardFeedback.NONE
        if (current.pasteFocused) {
            if (dy <= 0) return KeyboardFeedback.BOUNDARY
            _state.update { it.copy(pasteFocused = false) }
            return KeyboardFeedback.NAVIGATE
        }
        if (dx == 0 && dy < 0 && current.focus.row == 0 && current.pasteAvailable) {
            _state.update { it.copy(pasteFocused = true) }
            return KeyboardFeedback.NAVIGATE
        }
        val rows = current.rows
        val horizontal = if (dx == 0) {
            current.focus
        } else {
            KeyPosition(current.focus.row, stepHorizontal(rows[current.focus.row], current.focus.index, dx))
                .also { column = centerOf(rows, it) }
        }
        val target = if (dy == 0) horizontal else stepVertical(rows, horizontal, dy, column)
        _state.update { it.copy(focus = target) }
        return KeyboardFeedback.NAVIGATE
    }

    fun activate(): KeyboardFeedback {
        val current = _state.value
        if (!current.enabled) return KeyboardFeedback.NONE
        current.accentStrip?.let { return pickAccent(it, it.focused) }
        if (current.pasteFocused) return pasteClipboard()
        return when (val key = current.rows.keyAt(current.focus)) {
            is KeyboardKey.Character -> insert(current.typedText(key), consumeShift = true)
            is KeyboardKey.Function -> runFunction(key.function)
            is KeyboardKey.Gap, null -> KeyboardFeedback.NONE
        }
    }

    fun longActivate(): KeyboardFeedback {
        val current = _state.value
        if (!current.enabled) return KeyboardFeedback.NONE
        if (current.accentStrip != null || current.pasteFocused) return activate()
        val key = current.rows.keyAt(current.focus) as? KeyboardKey.Character ?: return activate()
        val options = if (current.fieldType.isAsciiOnly) emptyList() else accentOptions(current, key)
        if (options.isEmpty()) return activate()
        _state.update {
            it.copy(accentStrip = AccentStrip(current.focus, options, defaultAccentIndex(current, key)))
        }
        return KeyboardFeedback.NAVIGATE
    }

    fun closeAccentStrip(): KeyboardFeedback {
        if (_state.value.accentStrip == null) return KeyboardFeedback.NONE
        _state.update { it.copy(accentStrip = null) }
        return KeyboardFeedback.BACK
    }

    fun back(): KeyboardFeedback =
        if (_state.value.accentStrip != null) closeAccentStrip() else KeyboardFeedback.CLOSED

    fun backspace(): KeyboardFeedback {
        val current = _state.value
        if (!current.enabled) return KeyboardFeedback.NONE
        if (current.caret == 0) return KeyboardFeedback.BOUNDARY
        composer?.backspace(current.text, current.caret)?.let { edit ->
            val composed = _state.updateAndGet { it.copy(text = edit.text, caret = edit.caret) }
            onTextChange(composed.text)
            return KeyboardFeedback.BACK
        }
        val next = _state.updateAndGet { s ->
            if (s.caret == 0) {
                s
            } else {
                val start = s.text.offsetByCodePoints(s.caret, -1)
                s.copy(text = s.text.removeRange(start, s.caret), caret = start)
            }
        }
        onTextChange(next.text)
        return KeyboardFeedback.BACK
    }

    fun space(): KeyboardFeedback {
        if (!_state.value.enabled) return KeyboardFeedback.NONE
        return insert(SPACE_TEXT, consumeShift = false)
    }

    fun shiftClick(): KeyboardFeedback = shiftClick(nowMs())

    fun shiftClick(nowMs: Long): KeyboardFeedback {
        val current = _state.value
        if (!current.enabled) return KeyboardFeedback.NONE
        val previous = lastShiftClickMs
        lastShiftClickMs = nowMs
        val withinDoubleTap = previous != null && nowMs - previous <= doubleTapTimeoutMs
        val (next, feedback) = when (current.shift) {
            ShiftState.OFF -> ShiftState.ONCE to KeyboardFeedback.TOGGLE
            ShiftState.ONCE -> if (withinDoubleTap) {
                ShiftState.LOCKED to KeyboardFeedback.TOGGLE_ON
            } else {
                ShiftState.OFF to KeyboardFeedback.TOGGLE
            }
            ShiftState.LOCKED -> ShiftState.OFF to KeyboardFeedback.TOGGLE_OFF
        }
        _state.update { it.copy(shift = next) }
        return feedback
    }

    fun toggleSymbols(): KeyboardFeedback {
        val current = _state.value
        if (!current.enabled) return KeyboardFeedback.NONE
        val targetId = if (current.pageId == KeyboardLayout.SYMBOLS_PAGE) {
            KeyboardLayout.LETTERS_PAGE
        } else {
            KeyboardLayout.SYMBOLS_PAGE
        }
        val page = activeLayout.page(targetId) ?: return KeyboardFeedback.NONE
        showPage(activeLayout, page, current.languageLabel)
        return KeyboardFeedback.TOGGLE
    }

    fun switchLayout(layout: KeyboardLayout, languageLabel: String): KeyboardFeedback {
        val current = _state.value
        if (!current.enabled || current.fieldType.requiredLayoutId != null) return KeyboardFeedback.NONE
        val page = layout.page(current.pageId)
            ?: layout.page(KeyboardLayout.LETTERS_PAGE)
            ?: layout.pages.firstOrNull()
            ?: return KeyboardFeedback.NONE
        activeLayout = layout
        composer = keyboardComposerFor(layout.composer)
        showPage(layout, page, languageLabel)
        return KeyboardFeedback.TOGGLE
    }

    fun moveCaret(delta: Int): KeyboardFeedback {
        val current = _state.value
        if (!current.enabled || delta == 0) return KeyboardFeedback.NONE
        val target = caretOffset(current.text, current.caret, delta)
        if (target == current.caret) return KeyboardFeedback.BOUNDARY
        composer?.commit()
        _state.update { it.copy(caret = target) }
        return KeyboardFeedback.NAVIGATE
    }

    fun setCaret(index: Int): KeyboardFeedback {
        val current = _state.value
        if (!current.enabled) return KeyboardFeedback.NONE
        val target = index.coerceIn(0, current.text.length)
        if (target == current.caret) return KeyboardFeedback.NONE
        composer?.commit()
        _state.update { it.copy(caret = target.coerceAtMost(it.text.length)) }
        return KeyboardFeedback.NAVIGATE
    }

    fun submit(): KeyboardFeedback {
        val current = _state.value
        return when {
            !current.enabled -> KeyboardFeedback.NONE
            !current.canSubmit -> KeyboardFeedback.ERROR
            else -> KeyboardFeedback.SUBMITTED
        }
    }

    fun tapKey(position: KeyPosition): KeyboardFeedback =
        if (focusKey(position)) activate() else KeyboardFeedback.NONE

    fun longTapKey(position: KeyPosition): KeyboardFeedback =
        if (focusKey(position)) longActivate() else KeyboardFeedback.NONE

    fun tapAccent(index: Int): KeyboardFeedback {
        val current = _state.value
        if (!current.enabled) return KeyboardFeedback.NONE
        val strip = current.accentStrip ?: return KeyboardFeedback.NONE
        if (index !in strip.options.indices) return KeyboardFeedback.NONE
        return pickAccent(strip, index)
    }

    fun updateSession(error: String?, canSubmit: Boolean, enabled: Boolean) {
        _state.update {
            it.copy(
                error = error,
                canSubmit = canSubmit,
                enabled = enabled,
                accentStrip = if (enabled) it.accentStrip else null
            )
        }
    }

    /**
     * Adopts [text] when the field stored something other than what was typed, for a field that
     * trims or normalizes its value. The caret keeps its distance from the end of the text.
     */
    fun syncText(text: String) {
        val current = _state.value
        if (current.text == text) return
        composer?.commit()
        val fromEnd = current.text.length - current.caret.coerceIn(0, current.text.length)
        _state.update { it.copy(text = text, caret = (text.length - fromEnd).coerceIn(0, text.length)) }
    }

    private fun focusKey(position: KeyPosition): Boolean {
        val current = _state.value
        if (!current.enabled || !current.rows.isFocusable(position)) return false
        column = centerOf(current.rows, position)
        _state.update { it.copy(focus = position, accentStrip = null, pasteFocused = false) }
        return true
    }

    private fun moveInStrip(dx: Int): KeyboardFeedback {
        if (dx == 0) return KeyboardFeedback.NONE
        _state.update { s ->
            s.copy(accentStrip = s.accentStrip?.let { it.copy(focused = (it.focused + dx).mod(it.options.size)) })
        }
        return KeyboardFeedback.NAVIGATE
    }

    private fun pickAccent(strip: AccentStrip, index: Int): KeyboardFeedback {
        _state.update { it.copy(accentStrip = null) }
        return insert(strip.options[index], consumeShift = true)
    }

    private fun runFunction(function: FunctionKey): KeyboardFeedback = when (function) {
        FunctionKey.SHIFT -> shiftClick(nowMs())
        FunctionKey.BACKSPACE -> backspace()
        FunctionKey.SPACE -> space()
        FunctionKey.SYMBOLS, FunctionKey.LETTERS -> toggleSymbols()
        FunctionKey.LAYOUT -> if (_state.value.fieldType.requiredLayoutId == null) {
            KeyboardFeedback.LAYOUT_REQUESTED
        } else {
            KeyboardFeedback.NONE
        }
        FunctionKey.ENTER -> submit()
    }

    /**
     * Shows or hides the Paste stop above the top row as the clipboard gains or loses text. Focus
     * on it returns to the key it came from when it goes.
     */
    fun setPasteAvailable(available: Boolean) {
        _state.update {
            it.copy(pasteAvailable = available, pasteFocused = it.pasteFocused && available)
        }
    }

    fun pasteClipboard(): KeyboardFeedback {
        if (!_state.value.enabled) return KeyboardFeedback.NONE
        val text = readClipboard() ?: return KeyboardFeedback.BOUNDARY
        return paste(text)
    }

    /**
     * Inserts clipboard [text] at the caret as typed for this field: line breaks become spaces,
     * URI, email, password and number fields drop surrounding whitespace, a number field keeps
     * only digits, and text past the length limit is cut. Nothing fitting reads as a boundary.
     */
    fun paste(text: String): KeyboardFeedback {
        val current = _state.value
        if (!current.enabled) return KeyboardFeedback.NONE
        composer?.commit()
        val cleaned = pasteText(text, current.fieldType)
        val room = current.maxLength?.let { (it - current.text.length).coerceAtLeast(0) } ?: cleaned.length
        val fitting = cleaned.take(room)
        if (fitting.isEmpty()) return KeyboardFeedback.BOUNDARY
        return insertPlain(fitting, consumeShift = false)
    }

    private fun insert(text: String, consumeShift: Boolean): KeyboardFeedback {
        val current = _state.value
        val edit = composer?.input(current.text, current.caret.coerceIn(0, current.text.length), text)
        return if (edit == null) insertPlain(text, consumeShift) else applyComposed(edit, consumeShift)
    }

    private fun applyComposed(edit: ComposerEdit, consumeShift: Boolean): KeyboardFeedback {
        val limit = _state.value.maxLength
        if (limit != null && edit.text.length > limit) {
            composer?.commit()
            return KeyboardFeedback.BOUNDARY
        }
        val next = _state.updateAndGet { s ->
            s.copy(
                text = edit.text,
                caret = edit.caret,
                shift = if (consumeShift && s.shift == ShiftState.ONCE) ShiftState.OFF else s.shift,
                accentStrip = null
            )
        }
        onTextChange(next.text)
        return KeyboardFeedback.SELECT
    }

    private fun insertPlain(text: String, consumeShift: Boolean): KeyboardFeedback {
        val current = _state.value
        val limit = current.maxLength
        if (limit != null && current.text.length + text.length > limit) return KeyboardFeedback.BOUNDARY
        val next = _state.updateAndGet { s ->
            val caret = s.caret.coerceIn(0, s.text.length)
            s.copy(
                text = StringBuilder(s.text).insert(caret, text).toString(),
                caret = caret + text.length,
                shift = if (consumeShift && s.shift == ShiftState.ONCE) ShiftState.OFF else s.shift,
                accentStrip = null
            )
        }
        onTextChange(next.text)
        return KeyboardFeedback.SELECT
    }

    private fun showPage(layout: KeyboardLayout, page: KeyboardPage, languageLabel: String) {
        composer?.commit()
        val current = _state.value
        val rows = composeRows(layout, page, current.fieldType)
        val scaledColumn = column * unitsOf(rows) / unitsOf(current.rows)
        val row = current.focus.row.coerceAtMost(rows.lastIndex)
        val focus = nearestKey(rows, row, scaledColumn)?.let { KeyPosition(row, it) }
            ?: stepVertical(rows, KeyPosition(row, 0), 1, scaledColumn)
        column = scaledColumn
        _state.update {
            it.copy(
                layoutId = layout.id,
                pageId = page.id,
                rows = rows,
                focus = focus,
                languageLabel = languageLabel,
                accentStrip = null,
                shiftRule = page.shift,
                localeTag = layout.localeTag
            )
        }
    }

    private fun accentOptions(current: KeyboardUiState, key: KeyboardKey.Character): List<String> {
        val locale = keyboardLocale(current.localeTag)
        val base = activeLayout.accents[key.text]
            ?: activeLayout.accents[key.text.lowercase(locale)]
            ?: return emptyList()
        val upper = current.shift != ShiftState.OFF && current.shiftRule == ShiftRule.Upper
        return if (upper) base.map { it.uppercase(locale) } else base
    }

    private fun defaultAccentIndex(current: KeyboardUiState, key: KeyboardKey.Character): Int {
        val locale = keyboardLocale(current.localeTag)
        val base = activeLayout.accents[key.text] ?: activeLayout.accents[key.text.lowercase(locale)].orEmpty()
        val preferred = current.localeTag
            ?.let { activeLayout.accentDefaults[it] }
            ?.get(key.text.lowercase(locale))
            ?: return 0
        return base.indexOf(preferred).coerceAtLeast(0)
    }

    companion object {
        const val DEFAULT_DOUBLE_TAP_TIMEOUT_MS = 300L
        private const val NANOS_PER_MILLI = 1_000_000L
        private const val PAGE_TOGGLE_WIDTH = 1.5f
        private const val ENTER_WIDTH = 1.5f
        private const val MIN_SPACE_WIDTH = 1f
        private const val SPACE_TEXT = " "
        private const val COMMA_TEXT = ","
        private const val PERIOD_TEXT = "."
        private const val URI_SLOT_TEXT = "/"
        private const val EMAIL_SLOT_TEXT = "@"
        private const val SYMBOLS_SLOT_TEXT = "`"

        fun composeRows(
            layout: KeyboardLayout,
            page: KeyboardPage,
            fieldType: KeyboardFieldType
        ): List<KeyboardRow> =
            if (layout.id == KeyboardLayout.NUMERIC_LAYOUT_ID) page.rows else page.rows + bottomRow(page, fieldType)

        fun bottomRow(page: KeyboardPage, fieldType: KeyboardFieldType): KeyboardRow {
            val onSymbols = page.id == KeyboardLayout.SYMBOLS_PAGE
            val pageToggle = KeyboardKey.Function(
                if (onSymbols) FunctionKey.LETTERS else FunctionKey.SYMBOLS,
                PAGE_TOGGLE_WIDTH
            )
            val slot: KeyboardKey? = when {
                onSymbols -> KeyboardKey.Character(SYMBOLS_SLOT_TEXT)
                fieldType == KeyboardFieldType.URI -> KeyboardKey.Character(URI_SLOT_TEXT)
                fieldType == KeyboardFieldType.EMAIL -> KeyboardKey.Character(EMAIL_SLOT_TEXT)
                fieldType == KeyboardFieldType.PASSWORD -> null
                else -> KeyboardKey.Function(FunctionKey.LAYOUT)
            }
            val comma = KeyboardKey.Character(COMMA_TEXT)
            val period = KeyboardKey.Character(PERIOD_TEXT)
            val enter = KeyboardKey.Function(FunctionKey.ENTER, ENTER_WIDTH)
            val fixedWidth = listOfNotNull(pageToggle, slot, comma, period, enter).sumOf { it.width.toDouble() }.toFloat()
            val space = KeyboardKey.Function(
                FunctionKey.SPACE,
                (unitsOf(page.rows) - fixedWidth).coerceAtLeast(MIN_SPACE_WIDTH)
            )
            return KeyboardRow(listOfNotNull(pageToggle, slot, comma, space, period, enter))
        }
    }
}

private val LINE_BREAKS = Regex("\\r\\n|\\r|\\n")

internal fun pasteText(text: String, fieldType: KeyboardFieldType): String {
    val oneLine = text.replace(LINE_BREAKS, " ").filter { !it.isISOControl() }
    return when (fieldType) {
        KeyboardFieldType.TEXT -> oneLine
        KeyboardFieldType.NUMBER -> oneLine.filter { it.isDigit() }
        KeyboardFieldType.URI, KeyboardFieldType.EMAIL, KeyboardFieldType.PASSWORD -> oneLine.trim()
    }
}

private fun rowUnits(row: KeyboardRow): Float = row.keys.sumOf { it.width.toDouble() }.toFloat()

private fun unitsOf(rows: List<KeyboardRow>): Float = rows.maxOfOrNull(::rowUnits)?.takeIf { it > 0f } ?: 1f

private fun keyEdges(rows: List<KeyboardRow>, row: Int): List<Float> {
    val keys = rows[row]
    val offset = (unitsOf(rows) - rowUnits(keys)) / 2f
    return keys.keys.runningFold(offset) { start, key -> start + key.width }
}

private fun centerOf(rows: List<KeyboardRow>, position: KeyPosition): Float {
    val edges = keyEdges(rows, position.row)
    return (edges[position.index] + edges[position.index + 1]) / 2f
}

private fun List<KeyboardRow>.keyAt(position: KeyPosition): KeyboardKey? =
    getOrNull(position.row)?.keys?.getOrNull(position.index)

private fun List<KeyboardRow>.isFocusable(position: KeyPosition): Boolean {
    val key = keyAt(position)
    return key != null && key !is KeyboardKey.Gap
}

private fun nearestKey(rows: List<KeyboardRow>, row: Int, column: Float): Int? {
    val edges = keyEdges(rows, row)
    val candidates = rows[row].keys.indices.filter { rows[row].keys[it] !is KeyboardKey.Gap }
    return candidates.firstOrNull { column >= edges[it] && column < edges[it + 1] }
        ?: candidates.minByOrNull { abs((edges[it] + edges[it + 1]) / 2f - column) }
}

private fun stepHorizontal(row: KeyboardRow, from: Int, dx: Int): Int =
    (0 until abs(dx)).fold(from) { index, _ -> nextFocusableInRow(row, index, dx.sign) }

private fun nextFocusableInRow(row: KeyboardRow, from: Int, direction: Int): Int =
    (1..row.keys.size).asSequence()
        .map { (from + it * direction).mod(row.keys.size) }
        .firstOrNull { row.keys[it] !is KeyboardKey.Gap }
        ?: from

private fun stepVertical(rows: List<KeyboardRow>, from: KeyPosition, dy: Int, column: Float): KeyPosition =
    (0 until abs(dy)).fold(from) { position, _ -> nextRow(rows, position, dy.sign, column) }

private fun nextRow(rows: List<KeyboardRow>, from: KeyPosition, direction: Int, column: Float): KeyPosition =
    (1..rows.size).asSequence()
        .map { (from.row + it * direction).mod(rows.size) }
        .firstNotNullOfOrNull { row ->
            nearestKey(rows, row, column + direction * VERTICAL_TIE_BIAS)?.let { KeyPosition(row, it) }
        }
        ?: from

private const val VERTICAL_TIE_BIAS = 0.01f

private fun homeKey(rows: List<KeyboardRow>): KeyPosition {
    val homeRow = (rows.size - 1) / 2
    return (listOf(homeRow) + rows.indices.filter { it != homeRow })
        .firstNotNullOfOrNull { row ->
            val focusable = rows[row].keys.indices.filter { rows[row].keys[it] !is KeyboardKey.Gap }
            focusable.getOrNull(focusable.size / 2)?.let { KeyPosition(row, it) }
        }
        ?: KeyPosition(0, 0)
}

private fun caretOffset(text: String, caret: Int, delta: Int): Int {
    val available = if (delta < 0) text.codePointCount(0, caret) else text.codePointCount(caret, text.length)
    return text.offsetByCodePoints(caret, min(abs(delta), available) * delta.sign)
}
