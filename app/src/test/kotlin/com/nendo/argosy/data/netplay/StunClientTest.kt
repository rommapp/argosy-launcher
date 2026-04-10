package com.nendo.argosy.data.netplay

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.time.Duration.Companion.milliseconds

class StunClientTest {

    @Test
    fun parseXorMappedAddress_ipv4() {
        val txid = ByteArray(12) { (it + 1).toByte() }
        val response = buildResponse(
            txid = txid,
            attributes = listOf(xorMappedAddressAttr("1.2.3.4", 5555, txid))
        )
        val parsed = StunClient.parseBindingResponse(response, 0, response.size, txid)
        assertNotNull(parsed)
        assertEquals("1.2.3.4", parsed!!.address.hostAddress)
        assertEquals(5555, parsed.port)
    }

    @Test
    fun parseMappedAddress_fallback() {
        val txid = ByteArray(12) { (it + 9).toByte() }
        val response = buildResponse(
            txid = txid,
            attributes = listOf(mappedAddressAttr("8.8.8.8", 4321))
        )
        val parsed = StunClient.parseBindingResponse(response, 0, response.size, txid)
        assertNotNull(parsed)
        assertEquals("8.8.8.8", parsed!!.address.hostAddress)
        assertEquals(4321, parsed.port)
    }

    @Test
    fun xorMappedAddressTakesPrecedence() {
        val txid = ByteArray(12) { (it + 3).toByte() }
        val response = buildResponse(
            txid = txid,
            attributes = listOf(
                mappedAddressAttr("10.0.0.1", 1111),
                xorMappedAddressAttr("10.0.0.2", 2222, txid)
            )
        )
        val parsed = StunClient.parseBindingResponse(response, 0, response.size, txid)
        assertEquals("10.0.0.2", parsed!!.address.hostAddress)
        assertEquals(2222, parsed.port)
    }

    @Test
    fun wrongTransactionId_returnsNull() {
        val txid = ByteArray(12) { (it + 1).toByte() }
        val different = ByteArray(12) { (it + 99).toByte() }
        val response = buildResponse(
            txid = txid,
            attributes = listOf(xorMappedAddressAttr("1.2.3.4", 5555, txid))
        )
        assertNull(StunClient.parseBindingResponse(response, 0, response.size, different))
    }

    @Test
    fun buildBindingRequest_hasCorrectShape() {
        val txid = ByteArray(12) { (it + 5).toByte() }
        val req = StunClient.buildBindingRequest(txid)
        assertEquals(20, req.size)
        val buf = ByteBuffer.wrap(req).order(ByteOrder.BIG_ENDIAN)
        assertEquals(0x0001.toShort(), buf.short)
        assertEquals(0.toShort(), buf.short)
        assertEquals(0x2112A442.toInt(), buf.int)
        val outTxid = ByteArray(12)
        buf.get(outTxid)
        assertEquals(txid.toList(), outTxid.toList())
    }

    @Test
    fun timeout_returnsNull() = runBlocking {
        val client = StunClient()
        val unreachable = InetSocketAddress("192.0.2.1", 19302)
        val result = client.discoverReflexiveAddress(unreachable, timeout = 200.milliseconds)
        assertNull(result)
    }

    private fun buildResponse(txid: ByteArray, attributes: List<ByteArray>): ByteArray {
        val attrsConcat = ByteArray(attributes.sumOf { it.size })
        var pos = 0
        for (a in attributes) {
            System.arraycopy(a, 0, attrsConcat, pos, a.size)
            pos += a.size
        }
        val header = ByteBuffer.allocate(20).order(ByteOrder.BIG_ENDIAN)
        header.putShort(0x0101.toShort())
        header.putShort(attrsConcat.size.toShort())
        header.putInt(0x2112A442.toInt())
        header.put(txid)
        return header.array() + attrsConcat
    }

    private fun mappedAddressAttr(ip: String, port: Int): ByteArray {
        val parts = ip.split('.').map { it.toInt().toByte() }.toByteArray()
        val buf = ByteBuffer.allocate(12).order(ByteOrder.BIG_ENDIAN)
        buf.putShort(0x0001.toShort())
        buf.putShort(8.toShort())
        buf.put(0.toByte())
        buf.put(0x01.toByte())
        buf.putShort(port.toShort())
        buf.put(parts)
        return buf.array()
    }

    private fun xorMappedAddressAttr(ip: String, port: Int, txid: ByteArray): ByteArray {
        val parts = ip.split('.').map { it.toInt().toByte() }.toByteArray()
        val cookie = 0x2112A442.toInt()
        val xPort = port xor ((cookie ushr 16) and 0xFFFF)
        val cookieBytes = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(cookie).array()
        val xAddr = ByteArray(4) { i -> (parts[i].toInt() xor cookieBytes[i].toInt()).toByte() }
        val buf = ByteBuffer.allocate(12).order(ByteOrder.BIG_ENDIAN)
        buf.putShort(0x0020.toShort())
        buf.putShort(8.toShort())
        buf.put(0.toByte())
        buf.put(0x01.toByte())
        buf.putShort(xPort.toShort())
        buf.put(xAddr)
        return buf.array()
    }
}
