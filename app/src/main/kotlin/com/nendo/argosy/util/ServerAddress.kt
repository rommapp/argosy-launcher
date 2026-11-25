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
}
