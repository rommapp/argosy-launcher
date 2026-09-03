package com.nendo.argosy.data.emulator

import android.content.Context
import com.nendo.argosy.data.local.dao.PlatformLibretroSettingsDao
import com.nendo.argosy.data.preferences.BuiltinEmulatorPreferencesRepository
import com.nendo.argosy.util.AppPaths
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.firstOrNull
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The one answer to where the built-in libretro core keeps its live SRAM. The launch intent,
 * save discovery, the download target and the settings screens all read the base directory from
 * here, so a folder the user picks is the folder the core writes to and the folder sync reads.
 *
 * Precedence: saves-beside-rom -> per-platform override -> global custom path -> default
 * ({filesDir}/libretro/saves). The default matches the fallback LibretroActivity applies when no
 * EXTRA_SAVES_DIR is supplied. Folder-based cores hang their own tree off the base (PSP/SAVEDATA,
 * Azahar/sdmc/...), which SavePathRegistry appends through its `{builtinSaves}` token.
 *
 * Blank overrides are treated as absent so an empty string never shadows the fallback.
 */
@Singleton
class LibretroSavePathResolver @Inject constructor(
    @ApplicationContext private val context: Context,
    private val platformLibretroSettingsDao: PlatformLibretroSettingsDao,
    private val builtinPreferences: BuiltinEmulatorPreferencesRepository,
) {
    suspend fun liveSaveBaseDir(platformId: Long?, besideRomDir: String? = null): File {
        val platformOverride = platformId?.let { platformLibretroSettingsDao.getByPlatformId(it)?.savePath }
        val custom = builtinPreferences.getBuiltinEmulatorSettings().firstOrNull()?.customSavePath
        return liveSaveBaseDir(platformOverride, custom, besideRomDir)
    }

    /**
     * Overload for callers that already hold the resolved per-platform override, global custom
     * path and (optional) saves-beside-rom directory.
     */
    fun liveSaveBaseDir(
        platformSavePath: String?,
        customSavePath: String?,
        besideRomDir: String?,
    ): File {
        val base = besideRomDir?.takeIf { it.isNotBlank() }
            ?: platformSavePath?.takeIf { it.isNotBlank() }
            ?: customSavePath?.takeIf { it.isNotBlank() }
            ?: defaultBaseDir().absolutePath
        return File(base)
    }

    fun defaultBaseDir(): File = AppPaths.libretroSavesDir(context.filesDir)

    fun isDefaultBase(dir: File): Boolean = dir.absolutePath == defaultBaseDir().absolutePath
}
