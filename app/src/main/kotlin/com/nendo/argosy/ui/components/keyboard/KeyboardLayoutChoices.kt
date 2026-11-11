package com.nendo.argosy.ui.components.keyboard

import android.content.Context
import android.view.inputmethod.InputMethodManager
import java.util.Locale

data class KeyboardLayoutChoice(val layoutId: String, val languageTag: String)

private val LATIN_LANGUAGES = setOf(
    "en", "es", "it", "pt", "nl", "sv", "da", "nb", "nn", "no", "fi", "pl", "cs", "sk", "sl", "hr",
    "ro", "hu", "tr", "id", "ms", "vi", "ca", "eu", "gl", "et", "lv", "lt", "is", "ga", "cy", "af",
    "sq", "tl", "sw", "fil"
)
private val CYRILLIC_LANGUAGES = setOf("ru", "uk", "be", "bg", "sr", "mk", "kk", "ky")

internal fun layoutIdForLanguage(language: String): String? = when (language) {
    "fr" -> KeyboardLayout.AZERTY_LAYOUT_ID
    "de" -> KeyboardLayout.QWERTZ_LAYOUT_ID
    "ko" -> KeyboardLayout.HANGUL_LAYOUT_ID
    in CYRILLIC_LANGUAGES -> KeyboardLayout.JCUKEN_LAYOUT_ID
    in LATIN_LANGUAGES -> KeyboardLayout.QWERTY_LAYOUT_ID
    else -> null
}

const val SYSTEM_KEYBOARD_CHOICE_ID = "system"

internal fun keyboardLayoutChoices(
    languageTags: List<String>,
    fallbackTag: String,
    systemKeyboardAvailable: Boolean = false
): List<KeyboardLayoutChoice> {
    val mapped = languageTags.map { tag -> tag to layoutIdForLanguage(Locale.forLanguageTag(tag).language) }
    val choices = mapped
        .mapNotNull { (tag, layoutId) -> layoutId?.let { KeyboardLayoutChoice(it, tag) } }
        .distinctBy { it.layoutId }
    val builtIn = listOf(
        KeyboardLayoutChoice(KeyboardLayout.QWERTY_LAYOUT_ID, fallbackTag),
        KeyboardLayoutChoice(KeyboardLayout.AZERTY_LAYOUT_ID, "fr"),
        KeyboardLayoutChoice(KeyboardLayout.QWERTZ_LAYOUT_ID, "de"),
        KeyboardLayoutChoice(KeyboardLayout.JCUKEN_LAYOUT_ID, "ru"),
        KeyboardLayoutChoice(KeyboardLayout.HANGUL_LAYOUT_ID, "ko")
    )
    val all = (choices + builtIn).distinctBy { it.layoutId }
    val unmapped = mapped.firstOrNull { it.second == null }?.first
    return if (systemKeyboardAvailable && unmapped != null) {
        all + KeyboardLayoutChoice(SYSTEM_KEYBOARD_CHOICE_ID, unmapped)
    } else {
        all
    }
}

internal fun enabledKeyboardLanguageTags(context: Context): List<String> {
    val imm = context.getSystemService(InputMethodManager::class.java) ?: return emptyList()
    return runCatching {
        imm.enabledInputMethodList.flatMap { info ->
            imm.getEnabledInputMethodSubtypeList(info, true).mapNotNull { subtype ->
                subtype.languageTag.ifEmpty { subtype.locale.replace('_', '-') }.takeIf { it.isNotEmpty() }
            }
        }
    }.getOrDefault(emptyList())
}

internal fun languageDisplayName(tag: String): String {
    val locale = Locale.forLanguageTag(tag)
    return locale.getDisplayLanguage(locale).replaceFirstChar { it.titlecase(locale) }
}
