package com.nendo.argosy.data.emulator

import android.content.Context
import com.nendo.argosy.util.AppPaths
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves the built-in libretro core's live SRAM directory for the launch intent, mirroring
 * [LibretroStatePathResolver] for the save side. This owns the launch-side precedence only --
 * save-sync discovery (SavePathResolver.discoverSavePath) still resolves the built-in save
 * location independently, so this is not yet the single source of truth for every SRAM lookup.
 *
 * The base dir follows the precedence [GameLauncher] uses when it builds the launch intent:
 * saves-beside-rom -> per-platform override -> global custom path -> default
 * ({filesDir}/libretro/saves). The default matches the fallback LibretroActivity applies when no
 * EXTRA_SAVES_DIR is supplied, so passing it explicitly is equivalent to omitting the extra.
 *
 * Blank overrides are treated as absent (same as the state resolver) so an empty string never
 * shadows the custom/default fallback.
 */
@Singleton
class LibretroSavePathResolver @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /**
     * Overload for callers ([GameLauncher]) that already hold the resolved per-platform override,
     * global custom path and (optional) saves-beside-rom directory -- mirrors
     * [LibretroStatePathResolver.liveStateBaseDir] with the extra beside-rom branch that only the
     * save side has.
     */
    fun liveSaveBaseDir(
        platformSavePath: String?,
        customSavePath: String?,
        besideRomDir: String?,
    ): File {
        val base = besideRomDir?.takeIf { it.isNotBlank() }
            ?: platformSavePath?.takeIf { it.isNotBlank() }
            ?: customSavePath?.takeIf { it.isNotBlank() }
            ?: AppPaths.libretroSavesDir(context.filesDir).absolutePath
        return File(base)
    }
}
