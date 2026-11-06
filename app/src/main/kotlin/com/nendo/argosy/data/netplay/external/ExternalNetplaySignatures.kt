package com.nendo.argosy.data.netplay.external

import com.nendo.argosy.data.emulator.EmulatorRegistry

/**
 * The sockets each external emulator opens while its netplay is running, keyed by emulator family.
 *
 * Ports are each emulator's defaults. A user who changed the port in the emulator is not detected.
 */
object ExternalNetplaySignatures {

    enum class Side { LOCAL, REMOTE }

    data class PortSignature(
        val protocol: SocketProtocol,
        val port: Int,
        val side: Side,
        val role: ExternalNetplayRole
    )

    const val RETROARCH_FAMILY = "retroarch"

    private val familyByEmulatorId = mapOf(
        "retroarch" to RETROARCH_FAMILY,
        "retroarch_64" to RETROARCH_FAMILY,
        "retroarch_32" to RETROARCH_FAMILY,
        "dolphin" to "dolphin",
        "dolphin_handheld" to "dolphin",
        "dolphin_cs" to "dolphin",
        "flycast" to "flycast",
        "eden" to "yuzu_rooms",
        "eden_duo" to "yuzu_rooms",
        "citron" to "yuzu_rooms",
        "azahar" to "yuzu_rooms",
        "armsx2" to "armsx2",
        "armsx2_refresh" to "armsx2",
        "mupen64plus_fz" to "mupen64plus_fz",
        "mupen64plus_fz_pro" to "mupen64plus_fz",
        "hakux" to "hakux"
    )

    private val signaturesByFamily: Map<String, List<PortSignature>> = mapOf(
        RETROARCH_FAMILY to listOf(
            tcp(55435, Side.LOCAL, ExternalNetplayRole.HOST),
            tcp(55435, Side.REMOTE, ExternalNetplayRole.CLIENT)
        ),
        "dolphin" to listOf(
            udp(2626, Side.LOCAL, ExternalNetplayRole.HOST),
            udp(2626, Side.REMOTE, ExternalNetplayRole.CLIENT),
            udp(6262, Side.REMOTE, ExternalNetplayRole.UNKNOWN)
        ),
        "flycast" to listOf(
            udp(19713, Side.LOCAL, ExternalNetplayRole.UNKNOWN)
        ),
        "yuzu_rooms" to listOf(
            udp(24872, Side.LOCAL, ExternalNetplayRole.HOST),
            udp(24872, Side.REMOTE, ExternalNetplayRole.CLIENT)
        ),
        "armsx2" to listOf(
            udp(19072, Side.LOCAL, ExternalNetplayRole.UNKNOWN)
        ),
        "mupen64plus_fz" to listOf(
            tcp(43821, Side.LOCAL, ExternalNetplayRole.HOST),
            tcp(43822, Side.LOCAL, ExternalNetplayRole.HOST),
            tcp(43821, Side.REMOTE, ExternalNetplayRole.CLIENT),
            tcp(43822, Side.REMOTE, ExternalNetplayRole.CLIENT),
            tcp(37520, Side.REMOTE, ExternalNetplayRole.UNKNOWN)
        ),
        "hakux" to listOf(
            udp(3075, Side.LOCAL, ExternalNetplayRole.UNKNOWN),
            udp(34523, Side.REMOTE, ExternalNetplayRole.UNKNOWN)
        )
    )

    fun emulatorIdForPackage(packageName: String): String? {
        val id = EmulatorRegistry.getByPackage(packageName)?.id
            ?: EmulatorRegistry.findFamilyForPackage(packageName)?.baseId
            ?: return null
        return id.takeIf { familyOf(it) != null }
    }

    fun familyOf(emulatorId: String): String? =
        familyByEmulatorId[emulatorId] ?: familyByEmulatorId[EmulatorRegistry.familyBaseIdFor(emulatorId)]

    /**
     * The strongest role [sockets] show for the emulator running as [uid]: host over client over
     * unknown. Null when none of the emulator's signatures are open.
     */
    fun match(emulatorId: String, uid: Int, sockets: List<ProcNetSocket>): ExternalNetplayRole? {
        val signatures = familyOf(emulatorId)?.let(signaturesByFamily::get) ?: return null
        val owned = sockets.filter { it.uid == uid }
        val roles = signatures.filter { signature -> owned.any { signature.matches(it) } }.map { it.role }
        return listOf(ExternalNetplayRole.HOST, ExternalNetplayRole.CLIENT, ExternalNetplayRole.UNKNOWN)
            .firstOrNull { it in roles }
    }

    private fun PortSignature.matches(socket: ProcNetSocket): Boolean {
        if (socket.protocol != protocol) return false
        return when (side) {
            Side.LOCAL -> socket.localPort == port && (
                protocol == SocketProtocol.UDP ||
                    socket.state == ProcNetSockets.TCP_LISTEN ||
                    socket.state == ProcNetSockets.TCP_ESTABLISHED
                )
            Side.REMOTE -> socket.remotePort == port && (
                protocol == SocketProtocol.UDP || socket.state == ProcNetSockets.TCP_ESTABLISHED
                )
        }
    }

    private fun tcp(port: Int, side: Side, role: ExternalNetplayRole) =
        PortSignature(SocketProtocol.TCP, port, side, role)

    private fun udp(port: Int, side: Side, role: ExternalNetplayRole) =
        PortSignature(SocketProtocol.UDP, port, side, role)
}
