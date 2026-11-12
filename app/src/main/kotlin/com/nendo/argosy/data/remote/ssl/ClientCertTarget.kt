package com.nendo.argosy.data.remote.ssl

enum class ClientCertTarget(val token: String) {
    ROMM("romm"),
    JELLYFIN("jellyfin");

    companion object {
        fun fromToken(token: String): ClientCertTarget? = entries.firstOrNull { it.token == token }
    }
}

sealed interface ClientCertScope {
    data class Dedicated(val target: ClientCertTarget) : ClientCertScope
    data object ByHost : ClientCertScope
}

val ROMM_CLIENT_CERT_SCOPE: ClientCertScope = ClientCertScope.Dedicated(ClientCertTarget.ROMM)
val JELLYFIN_CLIENT_CERT_SCOPE: ClientCertScope = ClientCertScope.Dedicated(ClientCertTarget.JELLYFIN)
