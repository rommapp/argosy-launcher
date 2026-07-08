package com.nendo.argosy.data.emulator

import android.content.Context
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.PlatformLibretroSettingsDao
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.libretro.LibretroStateSlots
import com.nendo.argosy.util.AppPaths
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for the built-in libretro core's FLAT live save-state directory.
 *
 * Live states are flat ({base}/rom.state.X), mirroring SRAM and external emulators; channels
 * live only in the cache. The base dir mirrors the precedence [GameLauncher] uses when it builds
 * the launch intent: per-platform override -> global custom path -> default
 * ({filesDir}/libretro/states). Routing the cache/restore side through here keeps it in step with
 * the live engine and fixes the old bug where the cache side used StatePathRegistry's unexpanded
 * "{filesDir}/libretro/states" token for built-in.
 *
 * Keyed by gameId because the cache/restore callers have a gameId but not always the numeric
 * platformId the per-platform override is keyed on. Variant isolation ({base}/variants/{id}) is
 * applied by LibretroActivity at launch and is not modelled here (cache/restore is non-variant).
 */
@Singleton
class LibretroStatePathResolver @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gameDao: GameDao,
    private val platformLibretroSettingsDao: PlatformLibretroSettingsDao,
    private val userPreferencesRepository: UserPreferencesRepository,
) {
    suspend fun liveStateBaseDir(gameId: Long): File {
        val platformId = gameDao.getById(gameId)?.platformId
        val platformOverride = platformId?.let { platformLibretroSettingsDao.getByPlatformId(it)?.statePath }
        val custom = userPreferencesRepository.getBuiltinEmulatorSettings().first().customStatePath
        return liveStateBaseDir(platformOverride, custom)
    }

    /**
     * Overload for callers ([GameLauncher]) that already hold the resolved per-platform override and
     * custom paths -- avoids the extra game/platform/prefs lookups the gameId overload performs.
     */
    fun liveStateBaseDir(platformStatePath: String?, customStatePath: String?): File {
        val base = platformStatePath?.takeIf { it.isNotBlank() }
            ?: customStatePath?.takeIf { it.isNotBlank() }
            ?: AppPaths.libretroStatesDir(context.filesDir).absolutePath
        return File(base)
    }

    fun liveStateFile(baseDir: File, romBaseName: String, slotNumber: Int): File =
        File(baseDir, LibretroStateSlots.fileName(romBaseName, slotNumber))
}
