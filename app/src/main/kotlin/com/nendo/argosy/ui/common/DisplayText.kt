package com.nendo.argosy.ui.common

import android.content.Context
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource

/**
 * Localizable text computed outside a composable (a ViewModel state property or similar).
 * [Raw] is reserved for content the app did not author itself - a file name, a device model,
 * a track title - never for a sentence composed in English here. [Res] and [Plural] carry a
 * string resource id plus format args, resolved in the UI layer via [resolve].
 */
sealed interface DisplayText {
    data class Raw(val text: String) : DisplayText
    data class Res(@StringRes val resId: Int, val args: List<Any> = emptyList()) : DisplayText
    data class Plural(@PluralsRes val resId: Int, val count: Int, val args: List<Any> = emptyList()) : DisplayText
}

@Composable
fun DisplayText.resolve(): String = when (this) {
    is DisplayText.Raw -> text
    is DisplayText.Res -> stringResource(resId, *args.toTypedArray())
    is DisplayText.Plural -> pluralStringResource(resId, count, *args.toTypedArray())
}

fun DisplayText.resolve(context: Context): String = when (this) {
    is DisplayText.Raw -> text
    is DisplayText.Res -> context.getString(resId, *args.toTypedArray())
    is DisplayText.Plural -> context.resources.getQuantityString(resId, count, *args.toTypedArray())
}
