package com.nendo.argosy.data.netplay

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.SecureRandom
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

open class StunClient {

    private val secureRandom = SecureRandom()

    open suspend fun discoverReflexiveAddress(
        server: InetSocketAddress,
        localSocket: DatagramSocket? = null,
        timeout: Duration = 3.seconds
    ): InetSocketAddress? = withContext(Dispatchers.IO) {
        withTimeoutOrNull(timeout) {
            val socket = localSocket ?: DatagramSocket()
            val owningSocket = localSocket == null
            try {
                socket.soTimeout = timeout.inWholeMilliseconds.toInt()
                val resolved = if (server.isUnresolved) {
                    InetSocketAddress(InetAddress.getByName(server.hostString), server.port)
                } else {
                    server
                }
                val txid = ByteArray(TRANSACTION_ID_BYTES)
                secureRandom.nextBytes(txid)
                val request = buildBindingRequest(txid)
                socket.send(DatagramPacket(request, request.size, resolved))

                val buf = ByteArray(1500)
                val dgram = DatagramPacket(buf, buf.size)
                socket.receive(dgram)
                parseBindingResponse(buf, dgram.offset, dgram.length, txid)
            } catch (_: Throwable) {
                null
            } finally {
                if (owningSocket) {
                    try {
                        socket.close()
                    } catch (_: Throwable) {
                    }
                }
            }
        }
    }

    companion object {
        private const val TRANSACTION_ID_BYTES = 12
        private const val MAGIC_COOKIE = 0x2112A442.toInt()
        private const val MSG_BINDING_REQUEST: Short = 0x0001
        private const val MSG_BINDING_SUCCESS: Short = 0x0101
        private const val ATTR_MAPPED_ADDRESS: Short = 0x0001
        private const val ATTR_XOR_MAPPED_ADDRESS: Short = 0x0020
        private const val STUN_HEADER_BYTES = 20

        val PUBLIC_SERVERS: List<InetSocketAddress> = listOf(
            InetSocketAddress.createUnresolved("stun.l.google.com", 19302),
            InetSocketAddress.createUnresolved("stun1.l.google.com", 19302),
            InetSocketAddress.createUnresolved("stun2.l.google.com", 19302),
            InetSocketAddress.createUnresolved("stun.cloudflare.com", 3478)
        )

        internal fun buildBindingRequest(txid: ByteArray): ByteArray {
            require(txid.size == TRANSACTION_ID_BYTES)
            val buf = ByteBuffer.allocate(STUN_HEADER_BYTES).order(ByteOrder.BIG_ENDIAN)
            buf.putShort(MSG_BINDING_REQUEST)
            buf.putShort(0)
            buf.putInt(MAGIC_COOKIE)
            buf.put(txid)
            return buf.array()
        }

        internal fun parseBindingResponse(
            data: ByteArray,
            offset: Int,
            length: Int,
            expectedTxid: ByteArray
        ): InetSocketAddress? {
            if (length < STUN_HEADER_BYTES) return null
            val buf = ByteBuffer.wrap(data, offset, length).order(ByteOrder.BIG_ENDIAN)
            val messageType = buf.short
            val messageLength = buf.short.toInt() and 0xFFFF
            val cookie = buf.int
            val txid = ByteArray(TRANSACTION_ID_BYTES)
            buf.get(txid)

            if (messageType != MSG_BINDING_SUCCESS) return null
            if (cookie != MAGIC_COOKIE) return null
            if (!txid.contentEquals(expectedTxid)) return null
            if (length < STUN_HEADER_BYTES + messageLength) return null

            var mapped: InetSocketAddress? = null
            var xorMapped: InetSocketAddress? = null

            var consumed = 0
            while (consumed + 4 <= messageLength) {
                val attrType = buf.short
                val attrLen = buf.short.toInt() and 0xFFFF
                if (consumed + 4 + attrLen > messageLength) return null
                val attrData = ByteArray(attrLen)
                buf.get(attrData)
                val padding = (4 - (attrLen % 4)) % 4
                if (padding > 0) {
                    repeat(padding) {
                        if (buf.remaining() > 0) buf.get()
                    }
                }
                consumed += 4 + attrLen + padding

                when (attrType) {
                    ATTR_XOR_MAPPED_ADDRESS -> xorMapped = parseXorMappedAddress(attrData, txid)
                    ATTR_MAPPED_ADDRESS -> mapped = parseMappedAddress(attrData)
                }
            }

            return xorMapped ?: mapped
        }

        private fun parseMappedAddress(attr: ByteArray): InetSocketAddress? {
            if (attr.size < 8) return null
            val family = attr[1].toInt() and 0xFF
            val port = ((attr[2].toInt() and 0xFF) shl 8) or (attr[3].toInt() and 0xFF)
            val addrBytes: ByteArray = when (family) {
                0x01 -> if (attr.size >= 8) attr.copyOfRange(4, 8) else return null
                0x02 -> if (attr.size >= 20) attr.copyOfRange(4, 20) else return null
                else -> return null
            }
            val inet = InetAddress.getByAddress(addrBytes)
            return InetSocketAddress(inet, port)
        }

        private fun parseXorMappedAddress(attr: ByteArray, txid: ByteArray): InetSocketAddress? {
            if (attr.size < 8) return null
            val family = attr[1].toInt() and 0xFF
            val xPort = ((attr[2].toInt() and 0xFF) shl 8) or (attr[3].toInt() and 0xFF)
            val port = xPort xor ((MAGIC_COOKIE ushr 16) and 0xFFFF)

            val addrBytes: ByteArray = when (family) {
                0x01 -> {
                    if (attr.size < 8) return null
                    val raw = attr.copyOfRange(4, 8)
                    val cookieBytes = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(MAGIC_COOKIE).array()
                    ByteArray(4) { i -> (raw[i].toInt() xor cookieBytes[i].toInt()).toByte() }
                }
                0x02 -> {
                    if (attr.size < 20) return null
                    val raw = attr.copyOfRange(4, 20)
                    val mask = ByteBuffer.allocate(16).order(ByteOrder.BIG_ENDIAN)
                    mask.putInt(MAGIC_COOKIE)
                    mask.put(txid)
                    val maskBytes = mask.array()
                    ByteArray(16) { i -> (raw[i].toInt() xor maskBytes[i].toInt()).toByte() }
                }
                else -> return null
            }
            val inet = when (family) {
                0x01 -> Inet4Address.getByAddress(addrBytes) as InetAddress
                else -> Inet6Address.getByAddress(addrBytes) as InetAddress
            }
            return InetSocketAddress(inet, port)
        }
    }
}
