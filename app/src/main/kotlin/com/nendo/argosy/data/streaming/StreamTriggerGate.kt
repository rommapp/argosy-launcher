package com.nendo.argosy.data.streaming

import com.nendo.argosy.data.remote.romm.RomMCapabilities
import com.nendo.argosy.data.remote.romm.RomMStreamingConfig
import com.nendo.argosy.data.remote.romm.RomMStreamingContainer

/**
 * Whether a game can be streamed: RomM 5.3.0 or newer, `GET /api/streaming/config` answering
 * enabled, the game's platform among the containers, and no 403 from a claim on this server since
 * the last config refresh.
 */
object StreamTriggerGate {
    fun containerFor(
        capabilities: RomMCapabilities,
        config: RomMStreamingConfig?,
        startRefused: Boolean,
        platformSlug: String?
    ): RomMStreamingContainer? {
        if (!capabilities.supportsStreaming || startRefused) return null
        if (config == null || !config.enabled) return null
        val slug = platformSlug?.lowercase()?.takeIf { it.isNotBlank() } ?: return null
        return config.containers.firstOrNull { it.platform.lowercase() == slug }
    }

    fun labelOf(container: RomMStreamingContainer): String? =
        container.label.takeIf { it.isNotBlank() } ?: container.emulator.takeIf { it.isNotBlank() }
}
