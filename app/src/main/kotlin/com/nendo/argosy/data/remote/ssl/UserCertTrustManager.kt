package com.nendo.argosy.data.remote.ssl

import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import java.net.Socket
import java.security.KeyStore
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509ExtendedTrustManager
import javax.net.ssl.X509TrustManager

object UserCertTrustManager {

    /**
     * Hosts that never accept an imported certificate. Mirrors the domain-config entries in
     * res/xml/network_security_config.xml, which pin these hosts to system trust anchors.
     */
    val SYSTEM_TRUST_DOMAINS: Set<String> = setOf(
        "buildbot.libretro.com",
        "github.com",
        "githubusercontent.com",
        "argosy.dev",
        "retroachievements.org",
        "steampowered.com",
        "steamcommunity.com",
        "steamstatic.com"
    )

    fun OkHttpClient.Builder.withUserCertTrust(
        store: UserCertStore? = null,
        clientCerts: ClientCertStore? = null,
        scope: ClientCertScope = ClientCertScope.ByHost
    ): OkHttpClient.Builder {
        if (store == null && clientCerts == null) return this
        val platform = platformTrustManager() ?: return this
        val trustManager = ImportedForUserHosts(platform) { store?.trustManager() }
        if (clientCerts == null) {
            val sslContext = SSLContext.getInstance("TLS").apply { init(null, arrayOf(trustManager), null) }
            return sslSocketFactory(sslContext.socketFactory, trustManager)
        }
        val keyManager = ClientCertKeyManager(clientCerts, scope)
        val pool = ConnectionPool()
        val socketFactory = ClientCertRevisionSocketFactory(clientCerts) {
            SSLContext.getInstance("TLS").apply { init(arrayOf(keyManager), arrayOf(trustManager), null) }
        }
        val reset = ClientCertPoolReset(clientCerts, pool)
        return connectionPool(pool)
            .addInterceptor(reset.application)
            .addNetworkInterceptor(reset.network)
            .sslSocketFactory(socketFactory, trustManager)
    }

    fun acceptsImportedCertFor(host: String?): Boolean =
        !host.isNullOrBlank() && SYSTEM_TRUST_DOMAINS.none { domain ->
            host.equals(domain, ignoreCase = true) || host.endsWith(".$domain", ignoreCase = true)
        }

    private fun platformTrustManager(): X509ExtendedTrustManager? = try {
        TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            .apply { init(null as KeyStore?) }
            .trustManagers
            .filterIsInstance<X509ExtendedTrustManager>()
            .firstOrNull()
    } catch (e: Exception) {
        null
    }

    private class ImportedForUserHosts(
        private val platform: X509ExtendedTrustManager,
        private val imported: () -> X509TrustManager?
    ) : X509ExtendedTrustManager() {

        override fun checkServerTrusted(
            chain: Array<out X509Certificate>?,
            authType: String?,
            socket: Socket?
        ) {
            try {
                platform.checkServerTrusted(chain, authType, socket)
            } catch (rejected: CertificateException) {
                val host = (socket as? SSLSocket)?.handshakeSession?.peerHost
                retryWithImported(chain, authType, host, rejected)
            }
        }

        override fun checkServerTrusted(
            chain: Array<out X509Certificate>?,
            authType: String?,
            engine: SSLEngine?
        ) {
            try {
                platform.checkServerTrusted(chain, authType, engine)
            } catch (rejected: CertificateException) {
                val host = engine?.handshakeSession?.peerHost ?: engine?.peerHost
                retryWithImported(chain, authType, host, rejected)
            }
        }

        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
            platform.checkServerTrusted(chain, authType)
        }

        override fun checkClientTrusted(
            chain: Array<out X509Certificate>?,
            authType: String?,
            socket: Socket?
        ) {
            platform.checkClientTrusted(chain, authType, socket)
        }

        override fun checkClientTrusted(
            chain: Array<out X509Certificate>?,
            authType: String?,
            engine: SSLEngine?
        ) {
            platform.checkClientTrusted(chain, authType, engine)
        }

        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
            platform.checkClientTrusted(chain, authType)
        }

        override fun getAcceptedIssuers(): Array<X509Certificate> =
            platform.acceptedIssuers + imported()?.acceptedIssuers.orEmpty()

        private fun retryWithImported(
            chain: Array<out X509Certificate>?,
            authType: String?,
            host: String?,
            rejected: CertificateException
        ) {
            if (!acceptsImportedCertFor(host)) throw rejected
            val current = imported() ?: throw rejected
            try {
                current.checkServerTrusted(chain, authType)
            } catch (_: CertificateException) {
                throw rejected
            }
        }
    }
}
