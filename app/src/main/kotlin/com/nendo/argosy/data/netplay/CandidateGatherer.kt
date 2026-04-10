package com.nendo.argosy.data.netplay

import com.nendo.argosy.data.social.NetplayCandidate
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetSocketAddress
import java.net.NetworkInterface

open class CandidateGatherer(
    private val stunClient: StunClient = StunClient(),
    private val upnpProbe: UpnpProbe = UpnpProbe(),
    private val stunServers: List<InetSocketAddress> = StunClient.PUBLIC_SERVERS
) {

    open suspend fun gatherCandidates(localSocket: DatagramSocket): List<NetplayCandidate> = coroutineScope {
        val localPort = localSocket.localPort

        val lanDeferred = async { enumerateLocalAddresses(localPort) }
        val stunDeferred = async { queryStunCandidate(localPort) }
        val upnpDeferred = async { queryUpnpCandidate(localPort) }

        val (lan, stun, upnp) = awaitAll(lanDeferred, stunDeferred, upnpDeferred)

        @Suppress("UNCHECKED_CAST")
        val lanCandidates = lan as List<NetplayCandidate>

        @Suppress("UNCHECKED_CAST")
        val stunCandidates = stun as List<NetplayCandidate>

        @Suppress("UNCHECKED_CAST")
        val upnpCandidates = upnp as List<NetplayCandidate>

        (lanCandidates + stunCandidates + upnpCandidates)
            .distinctBy { "${it.type}:${it.address}:${it.port}" }
    }

    private fun enumerateLocalAddresses(port: Int): List<NetplayCandidate> {
        val out = mutableListOf<NetplayCandidate>()
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return emptyList()
            for (nif in interfaces) {
                if (!nif.isUp || nif.isLoopback) continue
                for (addr in nif.inetAddresses) {
                    if (addr.isLoopbackAddress || addr.isAnyLocalAddress || addr.isLinkLocalAddress) continue
                    when (addr) {
                        is Inet4Address -> if (addr.isSiteLocalAddress) {
                            out += NetplayCandidate(
                                type = "lan",
                                address = addr.hostAddress ?: continue,
                                port = port,
                                priority = PRIORITY_LAN
                            )
                        }
                        is Inet6Address -> {
                            val host = addr.hostAddress?.substringBefore('%') ?: continue
                            if (!addr.isSiteLocalAddress) {
                                out += NetplayCandidate(
                                    type = "ipv6",
                                    address = host,
                                    port = port,
                                    priority = PRIORITY_IPV6
                                )
                            }
                        }
                    }
                }
            }
        } catch (_: Throwable) {
            return out
        }
        return out
    }

    private suspend fun queryStunCandidate(port: Int): List<NetplayCandidate> {
        for (server in stunServers) {
            try {
                val mapped = stunClient.discoverReflexiveAddress(server) ?: continue
                val host = mapped.address?.hostAddress ?: continue
                return listOf(
                    NetplayCandidate(
                        type = "stun",
                        address = host,
                        port = mapped.port.takeIf { it > 0 } ?: port,
                        priority = PRIORITY_STUN
                    )
                )
            } catch (_: Throwable) {
                continue
            }
        }
        return emptyList()
    }

    private suspend fun queryUpnpCandidate(port: Int): List<NetplayCandidate> {
        return try {
            val mapping = upnpProbe.requestPortMapping(localPort = port) ?: return emptyList()
            listOf(
                NetplayCandidate(
                    type = "upnp",
                    address = mapping.externalIp,
                    port = mapping.externalPort,
                    priority = PRIORITY_UPNP
                )
            )
        } catch (_: Throwable) {
            emptyList()
        }
    }

    companion object {
        // RFC 8445 higher-is-better ordering. Phase 2b's candidate pair
        // selection latches the highest-priority successful pair first.
        // Ordering rationale: LAN (same subnet) > IPv6 (no NAT) > UPnP
        // (forced mapping, more stable than hole punch) > STUN (hole punch,
        // works broadly but fragile on symmetric NAT).
        const val PRIORITY_LAN = 250
        const val PRIORITY_IPV6 = 200
        const val PRIORITY_UPNP = 150
        const val PRIORITY_STUN = 100
    }
}
