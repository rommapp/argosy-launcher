package com.nendo.argosy.ui.deeplink

import android.net.Uri
import com.nendo.argosy.domain.model.DeepLinkRequest

/**
 * Parses the `argosy://launch` URI an external frontend fires to start a game.
 *
 * Accepted forms, in resolution order:
 *   argosy://launch?game_id=42
 *   argosy://launch?romm_id=1234
 *   argosy://launch/1234
 *   argosy://launch?path=/storage/emulated/0/ROMs/snes/Game.zip
 *
 * An optional `channel` selects a named save channel. `path` accepts a bare path or a
 * file:// URI, since frontends differ on which they hand out.
 */
object DeepLinkParser {
    const val SCHEME = "argosy"
    private const val HOST_LAUNCH = "launch"

    fun parse(uri: Uri): DeepLinkRequest? {
        if (!uri.scheme.equals(SCHEME, ignoreCase = true)) return null
        if (!uri.host.equals(HOST_LAUNCH, ignoreCase = true)) return null

        val positionalRommId = uri.pathSegments.firstOrNull()?.toLongOrNull()

        val request = DeepLinkRequest(
            gameId = uri.getQueryParameter("game_id")?.toLongOrNull(),
            rommId = uri.getQueryParameter("romm_id")?.toLongOrNull() ?: positionalRommId,
            romPath = uri.getQueryParameter("path")?.let(::normalizePath),
            channelName = uri.getQueryParameter("channel")?.takeIf { it.isNotBlank() }
        )

        return request.takeIf { it.hasTarget }
    }

    private fun normalizePath(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        if (!trimmed.startsWith("file://")) return trimmed
        return Uri.parse(trimmed).path?.takeIf { it.isNotBlank() }
    }
}
