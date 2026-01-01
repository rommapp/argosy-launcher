package com.nendo.argosy.data.remote.ssl

import okhttp3.OkHttpClient
import java.security.KeyStore
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

object UserCertTrustManager {

    fun OkHttpClient.Builder.withUserCertTrust(enabled: Boolean): OkHttpClient.Builder {
        if (!enabled) return this

        val trustManager = createAndroidCATrustManager() ?: return this
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
}
