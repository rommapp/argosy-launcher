package com.nendo.argosy.ui.components.keyboard

private const val SYLLABLE_BASE = 0xAC00
private const val VOWEL_COUNT = 21
private const val TAIL_COUNT = 28
private const val FIRST_VOWEL = 'ㅏ'
private const val LAST_VOWEL = 'ㅣ'

private const val LEADS =
    "ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅ" +
        "ㅆㅇㅈㅉㅊㅋㅌㅍㅎ"

private const val TAILS =
    "ㄱㄲㄳㄴㄵㄶㄷㄹㄺㄻ" +
        "ㄼㄽㄾㄿㅀㅁㅂㅄㅅㅆ" +
        "ㅇㅈㅊㅋㅌㅍㅎ"

private val COMPOUND_VOWELS: Map<Pair<Char, Char>, Char> = mapOf(
    ('ㅗ' to 'ㅏ') to 'ㅘ',
    ('ㅗ' to 'ㅐ') to 'ㅙ',
    ('ㅗ' to 'ㅣ') to 'ㅚ',
    ('ㅜ' to 'ㅓ') to 'ㅝ',
    ('ㅜ' to 'ㅔ') to 'ㅞ',
    ('ㅜ' to 'ㅣ') to 'ㅟ',
    ('ㅡ' to 'ㅣ') to 'ㅢ'
)

private val COMPOUND_TAILS: Map<Pair<Char, Char>, Char> = mapOf(
    ('ㄱ' to 'ㅅ') to 'ㄳ',
    ('ㄴ' to 'ㅈ') to 'ㄵ',
    ('ㄴ' to 'ㅎ') to 'ㄶ',
    ('ㄹ' to 'ㄱ') to 'ㄺ',
    ('ㄹ' to 'ㅁ') to 'ㄻ',
    ('ㄹ' to 'ㅂ') to 'ㄼ',
    ('ㄹ' to 'ㅅ') to 'ㄽ',
    ('ㄹ' to 'ㅌ') to 'ㄾ',
    ('ㄹ' to 'ㅍ') to 'ㄿ',
    ('ㄹ' to 'ㅎ') to 'ㅀ',
    ('ㅂ' to 'ㅅ') to 'ㅄ'
)

private fun Char.isLead(): Boolean = this in LEADS

private fun Char.isVowel(): Boolean = this in FIRST_VOWEL..LAST_VOWEL

private fun Char.isTail(): Boolean = this in TAILS

private data class HangulSyllable(
    val lead: Char? = null,
    val vowel: Char? = null,
    val tail: Char? = null
) {
    fun plus(jamo: Char): HangulSyllable? {
        val currentVowel = vowel ?: return if (jamo.isVowel()) copy(vowel = jamo) else null
        val currentTail = tail
        return when {
            currentTail != null -> COMPOUND_TAILS[currentTail to jamo]?.let { copy(tail = it) }
            jamo.isVowel() -> COMPOUND_VOWELS[currentVowel to jamo]?.let { copy(vowel = it) }
            lead != null && jamo.isTail() -> copy(tail = jamo)
            else -> null
        }
    }

    fun render(): Char {
        val currentLead = lead
        val currentVowel = vowel
        if (currentLead == null || currentVowel == null) return checkNotNull(currentLead ?: currentVowel)
        val tailIndex = tail?.let { TAILS.indexOf(it) + 1 } ?: 0
        val index = (LEADS.indexOf(currentLead) * VOWEL_COUNT + (currentVowel - FIRST_VOWEL)) * TAIL_COUNT + tailIndex
        return (SYLLABLE_BASE + index).toChar()
    }

    companion object {
        fun of(jamo: Char): HangulSyllable =
            if (jamo.isVowel()) HangulSyllable(vowel = jamo) else HangulSyllable(lead = jamo)
    }
}

/**
 * Two-set Hangul composer. Takes compatibility jamo one at a time and keeps the syllable being
 * composed as the single character before the caret; a lone consonant or vowel shows as itself.
 */
class HangulComposer : KeyboardComposer {
    private var start: Int = NO_COMPOSITION
    private var steps: List<HangulSyllable> = emptyList()

    override fun input(text: String, caret: Int, jamo: String): ComposerEdit? {
        val char = jamo.singleOrNull()?.takeIf { it.isLead() || it.isVowel() }
        if (char == null) {
            commit()
            return null
        }
        val current = activeSyllable(text, caret) ?: return begin(text, caret, HangulSyllable.of(char))
        current.plus(char)?.let { return extend(text, it) }
        val tail = current.tail
        if (char.isVowel() && tail != null) return carryTail(text, current, tail, char)
        return begin(text, caret, HangulSyllable.of(char))
    }

    override fun backspace(text: String, caret: Int): ComposerEdit? {
        activeSyllable(text, caret) ?: return null
        val remaining = steps.dropLast(1)
        val previous = remaining.lastOrNull()
        if (previous == null) {
            val at = start
            commit()
            return ComposerEdit(text.removeRange(at, at + 1), at)
        }
        steps = remaining
        return ComposerEdit(text.replaceRange(start, start + 1, previous.render().toString()), start + 1)
    }

    override fun commit() {
        start = NO_COMPOSITION
        steps = emptyList()
    }

    private fun activeSyllable(text: String, caret: Int): HangulSyllable? {
        val current = steps.lastOrNull()
        val intact = current != null && caret == start + 1 && start in text.indices && text[start] == current.render()
        if (!intact) commit()
        return current?.takeIf { intact }
    }

    private fun begin(text: String, at: Int, syllable: HangulSyllable): ComposerEdit {
        start = at
        steps = listOf(syllable)
        return ComposerEdit(StringBuilder(text).insert(at, syllable.render()).toString(), at + 1)
    }

    private fun extend(text: String, next: HangulSyllable): ComposerEdit {
        steps = steps + next
        return ComposerEdit(text.replaceRange(start, start + 1, next.render().toString()), start + 1)
    }

    private fun carryTail(text: String, current: HangulSyllable, tail: Char, vowel: Char): ComposerEdit {
        val compound = COMPOUND_TAILS.entries.firstOrNull { it.value == tail }?.key
        val kept = current.copy(tail = compound?.first)
        val lead = HangulSyllable(lead = compound?.second ?: tail)
        val next = checkNotNull(lead.plus(vowel))
        val edited = text.replaceRange(start, start + 1, "${kept.render()}${next.render()}")
        start += 1
        steps = listOf(lead, next)
        return ComposerEdit(edited, start + 1)
    }

    companion object {
        const val COMPOSER_ID = "hangul-2set"
        private const val NO_COMPOSITION = -1
    }
}
