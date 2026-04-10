package com.nendo.argosy.data.netplay

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bitlet.weupnp.GatewayDevice
import org.bitlet.weupnp.GatewayDiscover
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

open class UpnpProbe {

    data class PortMapping(
        val externalIp: String,
        val externalPort: Int,
        val internalPort: Int,
        val protocol: String,
        val gatewayDevice: GatewayDevice
    )

    @Suppress("UNUSED_PARAMETER")
    open suspend fun requestPortMapping(
        localPort: Int,
        protocol: String = "UDP",
        description: String = "Argosy Netplay",
        duration: Duration = 10.minutes
    ): PortMapping? = withContext(Dispatchers.IO) {
        try {
            val discover = GatewayDiscover()
            discover.discover()
            val gateway = discover.validGateway ?: return@withContext null
            val externalIp = gateway.externalIPAddress ?: return@withContext null
            val localAddress = gateway.localAddress?.hostAddress ?: return@withContext null
            val added = gateway.addPortMapping(
                localPort,
                localPort,
                localAddress,
                protocol,
                description
            )
            if (!added) return@withContext null
            PortMapping(
                externalIp = externalIp,
                externalPort = localPort,
                internalPort = localPort,
                protocol = protocol,
                gatewayDevice = gateway
            )
        } catch (_: Throwable) {
            null
        }
    }

    open suspend fun releaseMapping(mapping: PortMapping): Boolean = withContext(Dispatchers.IO) {
        try {
            mapping.gatewayDevice.deletePortMapping(mapping.externalPort, mapping.protocol)
        } catch (_: Throwable) {
            false
        }
    }
}
