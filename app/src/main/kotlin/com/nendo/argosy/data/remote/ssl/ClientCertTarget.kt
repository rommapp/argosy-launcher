package com.nendo.argosy.data.remote.ssl

enum class ClientCertTarget(val token: String) {
    ROMM("romm"),
    JELLYFIN("jellyfin");

    companion object {
        fun fromToken(token: String): ClientCertTarget? = entries.firstOrNull { it.token == token }
    }
}

sealed interface ClientCertScope {
    /**
     * A client that talks to one target's server. It presents the certificate only to
     * [serverHost] and to the hosts recorded for [target], never to a host a redirect or an
     * absolute URL from the server points elsewhere.
     */
    data class Dedicated(val target: ClientCertTarget, val serverHost: String? = null) : ClientCertScope
    data object ByHost : ClientCertScope
}

fun ClientCertTarget.scopeFor(serverUrl: String): ClientCertScope =
    ClientCertScope.Dedicated(this, ClientCertStore.hostOf(serverUrl))

val JELLYFIN_CLIENT_CERT_SCOPE: ClientCertScope = ClientCertScope.Dedicated(ClientCertTarget.JELLYFIN)
