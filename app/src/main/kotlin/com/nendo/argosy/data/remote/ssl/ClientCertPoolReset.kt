package com.nendo.argosy.data.remote.ssl

import okhttp3.Connection
import okhttp3.ConnectionPool
import okhttp3.Interceptor
import java.util.WeakHashMap

internal class ClientCertPoolReset(
    private val store: ClientCertStore,
    private val pool: ConnectionPool
) {
    private val openedUnder = WeakHashMap<Connection, Int>()

    @Volatile
    private var seen = store.revision

    val application = Interceptor { chain ->
        val revision = store.revision
        if (revision != seen || hasStaleConnection(revision)) {
            seen = revision
            pool.evictAll()
        }
        chain.proceed(chain.request())
    }

    val network = Interceptor { chain ->
        chain.connection()?.let { connection ->
            synchronized(openedUnder) { openedUnder.getOrPut(connection) { store.revision } }
        }
        chain.proceed(chain.request())
    }

    private fun hasStaleConnection(revision: Int): Boolean = synchronized(openedUnder) {
        openedUnder.entries.removeAll { (connection, _) -> connection.socket().isClosed }
        openedUnder.values.any { it != revision }
    }
}
