package com.nendo.argosy.data.remote.ssl

import okhttp3.Interceptor

internal class ClientCertPoolReset(private val sockets: ClientCertRevisionSocketFactory) {

    val application = Interceptor { chain ->
        sockets.closeStale()
        chain.proceed(chain.request())
    }
}
