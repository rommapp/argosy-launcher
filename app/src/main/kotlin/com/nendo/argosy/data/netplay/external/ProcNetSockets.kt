package com.nendo.argosy.data.netplay.external

enum class SocketProtocol { TCP, UDP }

data class ProcNetSocket(
    val protocol: SocketProtocol,
    val localPort: Int,
    val remotePort: Int,
    val state: Int,
    val uid: Int
)

/**
 * Reads the socket tables the kernel exposes in `/proc/net/{tcp,tcp6,udp,udp6}`.
 *
 * The input is the concatenated tables, each preceded by a `@@<table>` marker line naming the file
 * it came from.
 */
object ProcNetSockets {
    const val TCP_ESTABLISHED = 0x01
    const val TCP_LISTEN = 0x0A

    private const val MARKER = "@@"

    fun readScript(): String =
        listOf("tcp", "tcp6", "udp", "udp6").joinToString("\n") { table ->
            "echo $MARKER$table\ncat /proc/net/$table"
        }

    fun parse(lines: List<String>): List<ProcNetSocket> {
        var protocol: SocketProtocol? = null
        val sockets = mutableListOf<ProcNetSocket>()
        for (raw in lines) {
            val line = raw.trim()
            if (line.startsWith(MARKER)) {
                protocol = when (line.removePrefix(MARKER)) {
                    "tcp", "tcp6" -> SocketProtocol.TCP
                    "udp", "udp6" -> SocketProtocol.UDP
                    else -> null
                }
                continue
            }
            val current = protocol ?: continue
            parseRow(current, line)?.let(sockets::add)
        }
        return sockets
    }

    private fun parseRow(protocol: SocketProtocol, line: String): ProcNetSocket? {
        val fields = line.split(WHITESPACE)
        if (fields.size < 8 || !fields[0].endsWith(":")) return null
        return ProcNetSocket(
            protocol = protocol,
            localPort = portOf(fields[1]) ?: return null,
            remotePort = portOf(fields[2]) ?: return null,
            state = fields[3].toIntOrNull(16) ?: return null,
            uid = fields[7].toIntOrNull() ?: return null
        )
    }

    private fun portOf(address: String): Int? = address.substringAfterLast(':', "").toIntOrNull(16)

    private val WHITESPACE = Regex("\\s+")
}
