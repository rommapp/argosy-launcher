package com.nendo.argosy.data.remote.ssl

import okhttp3.OkHttpClient
import java.security.KeyStore
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

object UserCertTrustManager {

    fun OkHttpClient.Builder.withUserCertTrust(
        store: UserCertStore? = null
    ): OkHttpClient.Builder {
        val android = createAndroidCATrustManager()
        val imported = store?.trustManager()
        val delegates = listOfNotNull(android, imported)
        if (delegates.isEmpty()) return this

        val trustManager = if (delegates.size == 1) delegates.first() else FirstAccepting(delegates)
        val sslContext = SSLContext.getInstance("TLS").apply {
            init(null, arrayOf(trustManager), null)
        }

        return sslSocketFactory(sslContext.socketFactory, trustManager)
    }

    private fun createAndroidCATrustManager(): X509TrustManager? {
        return try {
            val keyStore = KeyStore.getInstance("AndroidCAStore")
            keyStore.load(null)

            val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            factory.init(keyStore)

            factory.trustManagers
                .filterIsInstance<X509TrustManager>()
                .firstOrNull()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Accepts a chain any delegate accepts, and rethrows the first delegate's rejection otherwise.
     *
     * The device store is asked before the imported certificates, so an import can only widen what
     * this app trusts and never narrows or overrides a chain the device already validates.
     */
    private class FirstAccepting(
        private val delegates: List<X509TrustManager>
    ) : X509TrustManager {

        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
            var first: CertificateException? = null
            for (delegate in delegates) {
                try {
                    delegate.checkServerTrusted(chain, authType)
                    return
                } catch (e: CertificateException) {
                    if (first == null) first = e
                }
            }
            throw first ?: CertificateException("No trust manager accepted the chain")
        }

        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
            delegates.first().checkClientTrusted(chain, authType)
        }

        override fun getAcceptedIssuers(): Array<X509Certificate> =
            delegates.flatMap { it.acceptedIssuers.asIterable() }.toTypedArray()
    }
}
