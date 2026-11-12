package com.nendo.argosy.data.remote.ssl

import java.net.InetAddress
import java.net.Socket
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory

internal class ClientCertRevisionSocketFactory(
    private val store: ClientCertStore,
    private val newContext: () -> SSLContext
) : SSLSocketFactory() {

    private class Built(val revision: Int, val factory: SSLSocketFactory)

    @Volatile
    private var built: Built? = null

    private fun current(): SSLSocketFactory {
        val revision = store.revision
        built?.takeIf { it.revision == revision }?.let { return it.factory }
        return synchronized(this) {
            built?.takeIf { it.revision == revision }?.factory
                ?: newContext().socketFactory.also { built = Built(revision, it) }
        }
    }

    override fun getDefaultCipherSuites(): Array<String> = current().defaultCipherSuites

    override fun getSupportedCipherSuites(): Array<String> = current().supportedCipherSuites

    override fun createSocket(): Socket = current().createSocket()

    override fun createSocket(socket: Socket?, host: String?, port: Int, autoClose: Boolean): Socket =
        current().createSocket(socket, host, port, autoClose)

    override fun createSocket(host: String?, port: Int): Socket = current().createSocket(host, port)

    override fun createSocket(host: String?, port: Int, localHost: InetAddress?, localPort: Int): Socket =
        current().createSocket(host, port, localHost, localPort)

    override fun createSocket(host: InetAddress?, port: Int): Socket = current().createSocket(host, port)

    override fun createSocket(
        address: InetAddress?,
        port: Int,
        localAddress: InetAddress?,
        localPort: Int
    ): Socket = current().createSocket(address, port, localAddress, localPort)
}
