package com.nendo.argosy.data.diagnostics

/**
 * Strips secrets and identity from diagnostics text. A setting whose name looks like a token,
 * password, address, server, user name or e-mail address has its value replaced whole. Every
 * other text has URLs, e-mail addresses, IPv4 hosts, bearer tokens and sensitive key/value pairs
 * replaced, and each of the known secrets passed in, plus the host of any that is a URL.
 */
object DiagnosticsRedactor {
    const val REDACTED = "<redacted>"
    const val URL_MARK = "<url>"
    const val EMAIL_MARK = "<email>"
    const val HOST_MARK = "<host>"

    private const val MIN_SECRET_LENGTH = 2
    private const val SCHEME_SEPARATOR = "://"

    private val SENSITIVE_NAME_PARTS = listOf(
        "token", "password", "passwd", "secret", "credential", "api_key", "apikey", "key_alias",
        "cookie", "auth", "url", "_uri", "address", "server", "host", "username", "user_name",
        "user_id", "email", "e_mail", "device_id", "install_id", "display_name", "friend"
    )

    private val URL = Regex("(?i)\\b[a-z][a-z0-9+.-]*://[^\\s\"'<>]+")
    private val EMAIL = Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")
    private val BEARER = Regex("(?i)\\b(bearer|basic)\\s+[A-Za-z0-9._~+/=-]+")
    private val KEY_VALUE = Regex(
        "(?i)([A-Za-z0-9_.-]*(?:token|password|passwd|secret|api[_-]?key|apikey|authorization|cookie|credential)" +
            "[A-Za-z0-9_.-]*)([\"']?\\s*[:=]\\s*[\"']?)([^\\s\"'&,;]+)"
    )
    private val IPV4 = Regex("\\b(?:\\d{1,3}\\.){3}\\d{1,3}(?::\\d{1,5})?\\b")

    fun isSensitiveName(name: String): Boolean {
        val lower = name.lowercase()
        return SENSITIVE_NAME_PARTS.any { it in lower }
    }

    fun redactSetting(name: String, value: Any?, knownSecrets: Collection<String> = emptySet()): String = when {
        value == null -> "null"
        value is Boolean || value is Number -> value.toString()
        isSensitiveName(name) -> if (value.toString().isEmpty()) "" else REDACTED
        else -> redactText(value.toString(), knownSecrets)
    }

    fun secretsIn(settings: Map<String, Any?>): Set<String> = buildSet {
        settings.forEach { (name, value) ->
            if (value is Boolean || value is Number || value == null || !isSensitiveName(name)) return@forEach
            val text = value.toString().trim()
            if (text.length < MIN_SECRET_LENGTH) return@forEach
            add(text)
            hostOf(text)?.let(::add)
        }
    }

    fun redactText(text: String, knownSecrets: Collection<String> = emptySet()): String {
        var result = text
        knownSecrets
            .filter { it.length >= MIN_SECRET_LENGTH }
            .sortedByDescending { it.length }
            .forEach { secret -> result = result.replace(literalPattern(secret), REDACTED) }
        result = URL.replace(result, URL_MARK)
        result = EMAIL.replace(result, EMAIL_MARK)
        result = BEARER.replace(result) { "${it.groupValues[1]} $REDACTED" }
        result = KEY_VALUE.replace(result) { "${it.groupValues[1]}${it.groupValues[2]}$REDACTED" }
        result = IPV4.replace(result, HOST_MARK)
        return result
    }

    private fun literalPattern(secret: String): Regex =
        Regex("(?<![A-Za-z0-9]|[A-Za-z0-9]\\.)" + Regex.escape(secret) + "(?![A-Za-z0-9]|\\.[A-Za-z0-9])")

    private fun hostOf(value: String): String? {
        if (SCHEME_SEPARATOR !in value) return null
        return value.substringAfter(SCHEME_SEPARATOR)
            .substringBefore('/')
            .substringAfterLast('@')
            .substringBefore(':')
            .takeIf { it.length >= MIN_SECRET_LENGTH }
    }
}
