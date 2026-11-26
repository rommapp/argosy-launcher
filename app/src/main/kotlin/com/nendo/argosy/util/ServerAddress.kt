package com.nendo.argosy.util

object ServerAddress {
    private val IPV4 = Regex("""^\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}$""")

    fun hasScheme(url: String): Boolean =
        url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)

    /**
     * A bare address gains the scheme the app tries first for it: plain http for an IP or
     * localhost, https for a hostname. Addresses that already carry a scheme are returned as is.
     */
    fun withDefaultScheme(url: String): String {
        val trimmed = url.trim()
        if (trimmed.isBlank() || hasScheme(trimmed)) return trimmed
        val hostPart = trimmed.removePrefix("//")
        val authority = hostPart.substringBefore('/')
        val host = if (authority.startsWith("[")) authority else authority.substringBefore(':')
        val plain = host.matches(IPV4) || host.startsWith("[") || host.equals("localhost", ignoreCase = true)
        return if (plain) "http://$hostPart" else "https://$hostPart"
    }

    fun candidates(url: String): List<String> {
        val trimmed = url.trim()
        if (trimmed.isBlank()) return emptyList()
        if (hasScheme(trimmed)) return listOf(trimmed)
        val hostPart = trimmed.removePrefix("//")
        val preferred = withDefaultScheme(hostPart)
        val fallback = if (preferred.startsWith("http://")) "https://$hostPart" else "http://$hostPart"
        return listOf(preferred, fallback)
    }

    /**
     * The first of [candidates] the server [answers] on, or [withDefaultScheme]'s choice when it
     * answers on none, so an address saved while the server is down keeps the scheme tried first.
     */
    suspend fun resolve(url: String, answers: suspend (String) -> Boolean): String {
        val options = candidates(url)
        if (options.size < 2) return options.firstOrNull().orEmpty()
        return options.firstOrNull { answers(it) } ?: options.first()
    }
}
