package com.nendo.argosy.hardware

import com.nendo.argosy.util.RootShell

enum class WifiSecurity(val token: String) {
    OPEN("open"),
    OWE("owe"),
    WPA2("wpa2"),
    WPA3("wpa3");

    val needsPassphrase: Boolean get() = this == WPA2 || this == WPA3
}

enum class WifiBand { GHZ_2_4, GHZ_5, GHZ_6 }

/**
 * The joined network. [ssid] is null where Android withholds the name, such as without location
 * access.
 */
data class WifiConnection(val ssid: String?, val rssi: Int?, val frequencyMhz: Int?)

/**
 * One network from a scan. [security] is null for networks a shell suggestion cannot join, such as
 * enterprise and WEP networks.
 */
data class WifiScanResult(
    val ssid: String,
    val rssi: Int,
    val frequencyMhz: Int,
    val security: WifiSecurity?
)

data class SavedWifiNetwork(val networkId: Int, val ssid: String, val securityType: String)

fun interface RootScriptRunner {
    fun run(script: String): RootShell.Result?
}

object WifiShellCommands {
    const val STATUS = "cmd wifi status"
    const val START_SCAN = "cmd wifi start-scan"
    const val LIST_SCAN_RESULTS = "cmd wifi list-scan-results"
    const val LIST_NETWORKS = "cmd wifi list-networks"

    fun join(ssid: String, security: WifiSecurity, passphrase: String?): String {
        val base = "cmd wifi add-suggestion ${RootShell.quote(ssid)} ${security.token}"
        return if (security.needsPassphrase && passphrase != null) "$base ${RootShell.quote(passphrase)}" else base
    }

    fun forget(networkId: Int): String = "cmd wifi forget-network $networkId"
}

object WifiShellParser {
    const val MAX_SIGNAL_LEVEL = 4
    private val RSSI_LEVEL_FLOORS = listOf(-88, -77, -66, -55)
    private val CONNECTED_SSID = Regex("""Wifi is connected to "(.*)"""")
    private val RSSI = Regex("""RSSI:\s*(-?\d+)""")
    private val FREQUENCY = Regex("""Frequency:\s*(\d+)\s*MHz""")
    private val SAVED_ROW = Regex("""^\s*(\d+)\s+(.+?)\s+(\S+)\s*$""")
    private val SCAN_ROW = Regex(
        """^\s*([0-9A-Fa-f]{2}(?::[0-9A-Fa-f]{2}){5})\s+(\d+)\s+(-?\d+)(?:\([^)]*\))?\s+\d+(?:\.\d+)?\S*(?:\s*\([^)]*\))?\s+(.*?)\s*((?:\[[^\]]*\])*)\s*$"""
    )
    private val GHZ_2_4_RANGE = 2400..2500
    private val GHZ_5_RANGE = 4900..5900
    private val GHZ_6_RANGE = 5925..7125

    fun parseStatus(lines: List<String>): WifiConnection? {
        val ssid = lines.firstNotNullOfOrNull { CONNECTED_SSID.find(it)?.groupValues?.get(1) }
            ?.takeIf { it.isNotBlank() }
            ?: return null
        val rssi = lines.firstNotNullOfOrNull { RSSI.find(it)?.groupValues?.get(1)?.toIntOrNull() }
        val frequency = lines.firstNotNullOfOrNull { FREQUENCY.find(it)?.groupValues?.get(1)?.toIntOrNull() }
        return WifiConnection(ssid = ssid, rssi = rssi, frequencyMhz = frequency)
    }

    fun parseSavedNetworks(lines: List<String>): List<SavedWifiNetwork> = lines.mapNotNull { line ->
        val match = SAVED_ROW.find(line) ?: return@mapNotNull null
        val ssid = unquote(match.groupValues[2].trim())
        if (ssid.isEmpty()) return@mapNotNull null
        SavedWifiNetwork(
            networkId = match.groupValues[1].toInt(),
            ssid = ssid,
            securityType = match.groupValues[3]
        )
    }.distinctBy { it.networkId }

    fun parseScanResults(lines: List<String>): List<WifiScanResult> = lines
        .mapNotNull { line ->
            val match = SCAN_ROW.find(line) ?: return@mapNotNull null
            val ssid = unquote(match.groupValues[4].trim())
            if (ssid.isEmpty()) return@mapNotNull null
            WifiScanResult(
                ssid = ssid,
                rssi = match.groupValues[3].toInt(),
                frequencyMhz = match.groupValues[2].toInt(),
                security = securityOf(match.groupValues[5])
            )
        }
        .let(::strongestPerNetwork)

    fun strongestPerNetwork(results: List<WifiScanResult>): List<WifiScanResult> = results
        .groupBy { it.ssid }
        .map { (_, sightings) -> sightings.maxBy { it.rssi } }
        .sortedByDescending { it.rssi }

    fun securityOf(flags: String): WifiSecurity? {
        val upper = flags.uppercase()
        return when {
            "EAP" in upper || "WEP" in upper -> null
            "PSK" in upper -> WifiSecurity.WPA2
            "SAE" in upper -> WifiSecurity.WPA3
            "OWE" in upper -> WifiSecurity.OWE
            else -> WifiSecurity.OPEN
        }
    }

    fun bandOf(frequencyMhz: Int?): WifiBand? = when (frequencyMhz) {
        null -> null
        in GHZ_2_4_RANGE -> WifiBand.GHZ_2_4
        in GHZ_5_RANGE -> WifiBand.GHZ_5
        in GHZ_6_RANGE -> WifiBand.GHZ_6
        else -> null
    }

    fun signalLevel(rssi: Int?): Int {
        if (rssi == null) return 0
        return RSSI_LEVEL_FLOORS.count { rssi >= it }.coerceAtMost(MAX_SIGNAL_LEVEL)
    }

    private fun unquote(value: String): String =
        if (value.length >= 2 && value.startsWith('"') && value.endsWith('"')) value.substring(1, value.length - 1) else value
}

class WifiNetworks(private val runner: RootScriptRunner) {

    fun connection(): WifiConnection? =
        runner.run(WifiShellCommands.STATUS)?.output?.let(WifiShellParser::parseStatus)

    fun startScan(): Boolean = runner.run(WifiShellCommands.START_SCAN)?.succeeded == true

    fun scanResults(): List<WifiScanResult> =
        runner.run(WifiShellCommands.LIST_SCAN_RESULTS)?.output?.let(WifiShellParser::parseScanResults).orEmpty()

    fun savedNetworks(): List<SavedWifiNetwork> =
        runner.run(WifiShellCommands.LIST_NETWORKS)?.output?.let(WifiShellParser::parseSavedNetworks).orEmpty()

    fun join(ssid: String, security: WifiSecurity, passphrase: String?): Boolean =
        runner.run(WifiShellCommands.join(ssid, security, passphrase))?.succeeded == true

    fun forget(networkId: Int): Boolean = runner.run(WifiShellCommands.forget(networkId))?.succeeded == true
}
