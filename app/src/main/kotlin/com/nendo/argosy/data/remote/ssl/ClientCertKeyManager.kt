package com.nendo.argosy.data.remote.ssl

import java.net.Socket
import java.security.Principal
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.util.concurrent.ConcurrentHashMap
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509ExtendedKeyManager

class ClientCertKeyManager(
    private val store: ClientCertStore,
    private val scope: ClientCertScope
) : X509ExtendedKeyManager() {

    private val chosen = ConcurrentHashMap<String, ClientIdentity>()

    @Volatile
    private var latestAlias: String? = null

    override fun chooseClientAlias(
        keyType: Array<out String>?,
        issuers: Array<out Principal>?,
        socket: Socket?
    ): String? = chooseFor(keyType, (socket as? SSLSocket)?.handshakeSession?.peerHost)

    override fun chooseEngineClientAlias(
        keyType: Array<out String>?,
        issuers: Array<out Principal>?,
        engine: SSLEngine?
    ): String? = chooseFor(keyType, engine?.handshakeSession?.peerHost ?: engine?.peerHost)

    override fun getClientAliases(keyType: String?, issuers: Array<out Principal>?): Array<String>? =
        chosen.keys.toTypedArray().takeIf { it.isNotEmpty() }

    override fun getCertificateChain(alias: String?): Array<X509Certificate>? =
        alias?.let { chosen[it]?.chain }

    override fun getPrivateKey(alias: String?): PrivateKey? =
        alias?.let { chosen[it]?.privateKey }

    override fun getServerAliases(keyType: String?, issuers: Array<out Principal>?): Array<String>? = null

    override fun chooseServerAlias(
        keyType: String?,
        issuers: Array<out Principal>?,
        socket: Socket?
    ): String? = null

    private fun chooseFor(keyType: Array<out String>?, host: String?): String? {
        val identity = store.identityFor(scope, host)
            ?.takeIf { matchesKeyType(it.keyAlgorithm, keyType) }
        store.recordCertificateRequest(host, presented = identity != null)
        if (identity == null) return null
        val alias = ALIAS_PREFIX + System.identityHashCode(identity)
        if (chosen[alias] !== identity) {
            synchronized(chosen) {
                chosen.keys.retainAll(setOfNotNull(alias, latestAlias))
                chosen[alias] = identity
                latestAlias = alias
            }
        }
        return alias
    }

    companion object {
        private const val ALIAS_PREFIX = "argosy-client-"

        internal fun matchesKeyType(algorithm: String, keyType: Array<out String>?): Boolean {
            if (keyType.isNullOrEmpty()) return true
            return keyType.any {
                it.equals(algorithm, ignoreCase = true) ||
                    it.startsWith("${algorithm}_", ignoreCase = true) ||
                    (algorithm.equals("RSA", ignoreCase = true) && it.equals("RSASSA-PSS", ignoreCase = true))
            }
        }
    }
}
