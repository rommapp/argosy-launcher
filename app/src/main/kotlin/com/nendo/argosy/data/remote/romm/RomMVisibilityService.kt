package com.nendo.argosy.data.remote.romm

import com.nendo.argosy.util.Logger
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "RomMVisibility"

/**
 * What the server withholds from the connected account.
 *
 * A restricted account gets a 404 for a hidden rom rather than a 403, so absence from a library
 * listing is indistinguishable from deletion by listing alone. `GET /api/permissions/me` is the
 * discriminator, and a server that cannot answer it leaves the client unable to prove a deletion
 * at all - which is [Unavailable], and must be treated as "keep everything".
 */
sealed class RomMVisibility {
    data class Known(
        val hiddenPlatformIds: Set<Long>,
        val hiddenRomIds: Set<Long>,
        val isAdmin: Boolean
    ) : RomMVisibility() {
        fun hides(rommId: Long, platformId: Long): Boolean =
            !isAdmin && (rommId in hiddenRomIds || platformId in hiddenPlatformIds)
    }

    data object Unavailable : RomMVisibility()
}

@Singleton
class RomMVisibilityService @Inject constructor() {

    /**
     * Reads the caller's hidden sets once, for a whole sync pass. Any failure at all - endpoint
     * missing on an older server, auth refused, transport error - degrades to [RomMVisibility.Unavailable]
     * rather than an empty hidden set, because an empty set would read as "nothing is hidden" and
     * license the deletion this exists to prevent.
     */
    suspend fun fetch(api: RomMApi?): RomMVisibility {
        if (api == null) return RomMVisibility.Unavailable
        return try {
            val response = api.getMyPermissions()
            if (!response.isSuccessful) {
                Logger.info(TAG, "permissions/me unavailable (HTTP ${response.code()}); deletions will be withheld")
                return RomMVisibility.Unavailable
            }
            val body = response.body() ?: return RomMVisibility.Unavailable
            RomMVisibility.Known(
                hiddenPlatformIds = body.hidden.platforms.toSet(),
                hiddenRomIds = body.hidden.roms.toSet(),
                isAdmin = body.isAdmin
            )
        } catch (e: Exception) {
            Logger.info(TAG, "permissions/me failed (${e.message}); deletions will be withheld")
            RomMVisibility.Unavailable
        }
    }
}
