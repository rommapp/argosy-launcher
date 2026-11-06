package com.nendo.argosy.data.netplay.external

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExternalNetplayDetectionTest {

    private val retroArchUid = 10234
    private val otherUid = 10500

    private fun tcpRow(local: Int, remote: Int, state: Int, uid: Int) =
        "  0: 0100007F:${hex(local)} 0200A8C0:${hex(remote)} ${state.toString(16).uppercase().padStart(2, '0')} " +
            "00000000:00000000 00:00000000 00000000 $uid 0 123 1 0000000000000000 100 0 0 10 0"

    private fun udpRow(local: Int, remote: Int, uid: Int) =
        "  1: 00000000:${hex(local)} 00000000:${hex(remote)} 07 00000000:00000000 00:00000000 00000000 $uid 0 456 2 0000000000000000 0"

    private fun hex(port: Int) = port.toString(16).uppercase().padStart(4, '0')

    private fun table(name: String, vararg rows: String) =
        listOf("@@$name", "  sl  local_address rem_address   st tx_queue rx_queue tr tm->when retrnsmt   uid  timeout inode") +
            rows.toList()

    @Test
    fun `parser reads ports state and uid per table`() {
        val sockets = ProcNetSockets.parse(
            table("tcp", tcpRow(55435, 0, ProcNetSockets.TCP_LISTEN, retroArchUid)) +
                table("udp6", udpRow(24872, 0, otherUid))
        )

        assertEquals(
            listOf(
                ProcNetSocket(SocketProtocol.TCP, 55435, 0, ProcNetSockets.TCP_LISTEN, retroArchUid),
                ProcNetSocket(SocketProtocol.UDP, 24872, 0, 7, otherUid)
            ),
            sockets
        )
    }

    @Test
    fun `parser skips rows before any table marker and malformed rows`() {
        val sockets = ProcNetSockets.parse(
            listOf(tcpRow(55435, 0, ProcNetSockets.TCP_LISTEN, retroArchUid), "@@tcp", "garbage row")
        )

        assertEquals(emptyList<ProcNetSocket>(), sockets)
    }

    @Test
    fun `a listening RetroArch socket reads as host`() {
        val sockets = listOf(ProcNetSocket(SocketProtocol.TCP, 55435, 0, ProcNetSockets.TCP_LISTEN, retroArchUid))

        assertEquals(ExternalNetplayRole.HOST, ExternalNetplaySignatures.match("retroarch_64", retroArchUid, sockets))
    }

    @Test
    fun `a RetroArch connection to a remote host reads as client`() {
        val sockets = listOf(ProcNetSocket(SocketProtocol.TCP, 41000, 55435, ProcNetSockets.TCP_ESTABLISHED, retroArchUid))

        assertEquals(ExternalNetplayRole.CLIENT, ExternalNetplaySignatures.match("retroarch", retroArchUid, sockets))
    }

    @Test
    fun `host outranks client whichever socket comes first`() {
        val listening = ProcNetSocket(SocketProtocol.TCP, 55435, 0, ProcNetSockets.TCP_LISTEN, retroArchUid)
        val connected = ProcNetSocket(SocketProtocol.TCP, 41000, 55435, ProcNetSockets.TCP_ESTABLISHED, retroArchUid)

        assertEquals(ExternalNetplayRole.HOST, ExternalNetplaySignatures.match("retroarch", retroArchUid, listOf(listening, connected)))
        assertEquals(ExternalNetplayRole.HOST, ExternalNetplaySignatures.match("retroarch", retroArchUid, listOf(connected, listening)))
    }

    @Test
    fun `sockets owned by another app never match`() {
        val sockets = listOf(ProcNetSocket(SocketProtocol.TCP, 55435, 0, ProcNetSockets.TCP_LISTEN, otherUid))

        assertNull(ExternalNetplaySignatures.match("retroarch", retroArchUid, sockets))
    }

    @Test
    fun `a closed tcp socket on the netplay port does not match`() {
        val timeWait = 0x06
        val sockets = listOf(ProcNetSocket(SocketProtocol.TCP, 41000, 55435, timeWait, retroArchUid))

        assertNull(ExternalNetplaySignatures.match("retroarch", retroArchUid, sockets))
    }

    @Test
    fun `the protocol has to match the signature`() {
        val sockets = listOf(ProcNetSocket(SocketProtocol.UDP, 55435, 0, 7, retroArchUid))

        assertNull(ExternalNetplaySignatures.match("retroarch", retroArchUid, sockets))
    }

    @Test
    fun `an emulator without signatures never matches`() {
        val sockets = listOf(ProcNetSocket(SocketProtocol.TCP, 55435, 0, ProcNetSockets.TCP_LISTEN, retroArchUid))

        assertNull(ExternalNetplaySignatures.match("drastic", retroArchUid, sockets))
    }

    @Test
    fun `a udp room port reads as host locally and client remotely`() {
        val hosting = listOf(ProcNetSocket(SocketProtocol.UDP, 24872, 0, 7, otherUid))
        val joined = listOf(ProcNetSocket(SocketProtocol.UDP, 50000, 24872, 1, otherUid))

        assertEquals(ExternalNetplayRole.HOST, ExternalNetplaySignatures.match("eden", otherUid, hosting))
        assertEquals(ExternalNetplayRole.CLIENT, ExternalNetplaySignatures.match("azahar", otherUid, joined))
    }

    private val hosting = ExternalNetplay("retroarch", ExternalNetplayRole.HOST, ExternalNetplaySource.SOCKET)
    private val joined = ExternalNetplay("retroarch", ExternalNetplayRole.CLIENT, ExternalNetplaySource.SOCKET)

    @Test
    fun `a session is reported only after two matching polls`() {
        val debouncer = DetectionDebouncer()

        assertNull(debouncer.observe(hosting))
        assertEquals(hosting, debouncer.observe(hosting))
    }

    @Test
    fun `a reported session clears only after two empty polls`() {
        val debouncer = DetectionDebouncer()
        debouncer.observe(hosting)
        debouncer.observe(hosting)

        assertEquals(hosting, debouncer.observe(null))
        assertNull(debouncer.observe(null))
    }

    @Test
    fun `alternating polls never replace the reported session`() {
        val debouncer = DetectionDebouncer()
        debouncer.observe(hosting)
        debouncer.observe(hosting)

        assertEquals(hosting, debouncer.observe(joined))
        assertEquals(hosting, debouncer.observe(null))
        assertEquals(hosting, debouncer.observe(joined))
        assertEquals(hosting, debouncer.observe(hosting))
        assertEquals(hosting, debouncer.observe(joined))
        assertEquals(joined, debouncer.observe(joined))
    }

    @Test
    fun `wire fields round trip and reject unknown tokens`() {
        val wire = hosting.toWire()

        assertEquals(hosting, ExternalNetplay.fromWire(wire["emulator"], wire["role"], wire["source"]))
        assertNull(ExternalNetplay.fromWire("retroarch", "spectator", "socket"))
        assertNull(ExternalNetplay.fromWire("retroarch", "host", "guess"))
        assertNull(ExternalNetplay.fromWire("", "host", "socket"))
        assertNull(ExternalNetplay.fromWire("x".repeat(ExternalNetplay.MAX_EMULATOR_ID_LENGTH + 1), "host", "socket"))
    }

    @Test
    fun `lobby host addresses come from each room's fields`() {
        val body = """[{"fields":{"ip":"203.0.113.7","port":55435}},{"fields":{"ip":""}},{"other":1}]"""

        assertEquals(setOf("203.0.113.7"), RetroArchLobby.hostIps(body))
        assertEquals(emptySet<String>(), RetroArchLobby.hostIps("not json"))
    }
}
