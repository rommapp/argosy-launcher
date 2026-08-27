package com.nendo.argosy.core.notification

/**
 * Localizable notification text. [Raw] is reserved for content the user themselves
 * named (a game title, a platform display name, a filename, a server-supplied
 * message) - never for a sentence the app authored. [Res] and [Plural] carry a
 * string resource id plus format args, resolved in the UI layer.
 */
sealed interface NotificationText {
    data class Raw(val value: String) : NotificationText
    data class Res(val id: Int, val args: List<Any> = emptyList()) : NotificationText
    data class Plural(val id: Int, val count: Int, val args: List<Any> = emptyList()) : NotificationText
}
