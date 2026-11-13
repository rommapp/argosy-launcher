package com.nendo.argosy.data.remote.ssl

import java.net.InetAddress
import java.net.Socket
import java.util.WeakHashMap
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory

internal class ClientCertRevisionSocketFactory(
    private val store: ClientCertStore,
    private val newContext: () -> SSLContext
) : SSLSocketFactory() {

    private class Built(val revision: Int, val factory: SSLSocketFactory)

    @Volatile
    private var built: Built? = null

    private val opened = WeakHashMap<Socket, Int>()

    private fun current(): Built {
        val revision = store.revision
        built?.takeIf { it.revision == revision }?.let { return it }
        return synchronized(this) {
            built?.takeIf { it.revision == revision }
                ?: Built(revision, newContext().socketFactory).also { built = it }
        }
    }

    private inline fun open(create: (SSLSocketFactory) -> Socket): Socket {
        val certificateRevision = store.certificateRevision
        val socket = create(current().factory)
        synchronized(opened) { opened[socket] = certificateRevision }
        return socket
    }

    fun closeStale() {
        val revision = store.certificateRevision
        val stale = synchronized(opened) {
            val found = opened.filter { (socket, openedAt) -> openedAt != revision || socket.isClosed }.keys
            found.forEach { opened.remove(it) }
            found.filterNot { it.isClosed }
        }
        stale.forEach { runCatching { it.close() } }
    }

    override fun getDefaultCipherSuites(): Array<String> = current().factory.defaultCipherSuites

    override fun getSupportedCipherSuites(): Array<String> = current().factory.supportedCipherSuites

    override fun createSocket(): Socket = open { it.createSocket() }

    override fun createSocket(socket: Socket?, host: String?, port: Int, autoClose: Boolean): Socket =
        open { it.createSocket(socket, host, port, autoClose) }

    override fun createSocket(host: String?, port: Int): Socket = open { it.createSocket(host, port) }

    override fun createSocket(host: String?, port: Int, localHost: InetAddress?, localPort: Int): Socket =
        open { it.createSocket(host, port, localHost, localPort) }

    override fun createSocket(host: InetAddress?, port: Int): Socket = open { it.createSocket(host, port) }

    override fun createSocket(
        address: InetAddress?,
        port: Int,
        localAddress: InetAddress?,
        localPort: Int
    ): Socket = open { it.createSocket(address, port, localAddress, localPort) }
}
