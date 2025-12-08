package com.nendo.argosy.data.emulator

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import com.nendo.argosy.data.local.dao.EmulatorConfigDao
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.entity.GameEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "GameLauncher"

sealed class LaunchResult {
    data class Success(val intent: Intent) : LaunchResult()
    data class NoEmulator(val platformId: String) : LaunchResult()
    data class NoRomFile(val gamePath: String?) : LaunchResult()
    data class Error(val message: String) : LaunchResult()
}

@Singleton
class GameLauncher @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gameDao: GameDao,
    private val emulatorConfigDao: EmulatorConfigDao,
    private val emulatorDetector: EmulatorDetector
) {
    suspend fun launch(gameId: Long): LaunchResult {
        val game = gameDao.getById(gameId)
            ?: return LaunchResult.Error("Game not found")

        val romPath = game.localPath
            ?: return LaunchResult.NoRomFile(null)

        val romFile = File(romPath)
        if (!romFile.exists()) {
            return LaunchResult.NoRomFile(romPath)
        }

        val emulator = resolveEmulator(game)
            ?: return LaunchResult.NoEmulator(game.platformId)

        val intent = buildIntent(emulator, romFile, game.platformId)
            ?: return LaunchResult.Error("Failed to build launch intent")

        gameDao.recordPlayStart(gameId, Instant.now())

        return LaunchResult.Success(intent)
    }

    private suspend fun resolveEmulator(game: GameEntity): EmulatorDef? {
        val gameOverride = emulatorConfigDao.getByGameId(game.id)
        if (gameOverride != null) {
            return EmulatorRegistry.getByPackage(gameOverride.packageName)
        }

        val platformDefault = emulatorConfigDao.getDefaultForPlatform(game.platformId)
        if (platformDefault != null) {
            return EmulatorRegistry.getByPackage(platformDefault.packageName)
        }

        if (emulatorDetector.installedEmulators.value.isEmpty()) {
            emulatorDetector.detectEmulators()
        }

        return emulatorDetector.getPreferredEmulator(game.platformId)?.def
    }

    private fun buildIntent(emulator: EmulatorDef, romFile: File, platformId: String): Intent? {
        Log.d(TAG, "buildIntent: emulator=${emulator.displayName}, config=${emulator.launchConfig::class.simpleName}, rom=${romFile.name}")
        return when (val config = emulator.launchConfig) {
            is LaunchConfig.FileUri -> buildFileUriIntent(emulator, romFile)
            is LaunchConfig.FilePathExtra -> buildFilePathIntent(emulator, romFile, config)
            is LaunchConfig.RetroArch -> buildRetroArchIntent(emulator, romFile, platformId, config)
            is LaunchConfig.Custom -> buildCustomIntent(emulator, romFile, platformId, config)
            is LaunchConfig.CustomScheme -> buildCustomSchemeIntent(emulator, romFile, config)
        }.also { intent ->
            Log.d(TAG, "buildIntent: action=${intent?.action}, package=${intent?.`package`}, component=${intent?.component}, data=${intent?.data}")
        }
    }

    private fun buildFileUriIntent(emulator: EmulatorDef, romFile: File): Intent {
        val uri = getFileUri(romFile)

        return Intent(emulator.launchAction).apply {
            setDataAndType(uri, getMimeType(romFile))
            setPackage(emulator.packageName)
            addCategory(Intent.CATEGORY_DEFAULT)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
    }

    private fun buildFilePathIntent(
        emulator: EmulatorDef,
        romFile: File,
        config: LaunchConfig.FilePathExtra
    ): Intent {
        return Intent(emulator.launchAction).apply {
            setPackage(emulator.packageName)
            config.extraKeys.forEach { key ->
                putExtra(key, romFile.absolutePath)
            }
            // Clear existing task to ensure new ROM loads instead of resuming previous game
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
    }

    private fun buildRetroArchIntent(
        emulator: EmulatorDef,
        romFile: File,
        platformId: String,
        config: LaunchConfig.RetroArch
    ): Intent {
        val coreName = EmulatorRegistry.getRetroArchCores()[platformId]
        val retroArchPackage = emulator.packageName

        val corePath = coreName?.let {
            "/data/data/$retroArchPackage/cores/${it}_libretro_android.so"
        }
        val configPath = "/storage/emulated/0/Android/data/$retroArchPackage/files/retroarch.cfg"

        return Intent(emulator.launchAction).apply {
            component = ComponentName(retroArchPackage, config.activityClass)
            putExtra("ROM", romFile.absolutePath)
            if (corePath != null) {
                putExtra("LIBRETRO", corePath)
            }
            putExtra("CONFIGFILE", configPath)
            putExtra("QUITFOCUS", "")
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_NO_HISTORY
            )
        }
    }

    private fun buildCustomIntent(
        emulator: EmulatorDef,
        romFile: File,
        platformId: String,
        config: LaunchConfig.Custom
    ): Intent {
        return Intent(emulator.launchAction).apply {
            if (config.activityClass != null) {
                component = ComponentName(emulator.packageName, config.activityClass)
            } else {
                setPackage(emulator.packageName)
            }

            addCategory(Intent.CATEGORY_DEFAULT)

            if (emulator.launchAction == Intent.ACTION_VIEW) {
                val uri = getFileUri(romFile)
                val mimeType = config.mimeTypeOverride ?: getMimeType(romFile)
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            if (config.useAbsolutePath) {
                putExtra("path", romFile.absolutePath)
                putExtra("file", romFile.absolutePath)
                putExtra("filePath", romFile.absolutePath)
            }

            config.intentExtras.forEach { (key, extraValue) ->
                val value = when (extraValue) {
                    is ExtraValue.FilePath -> romFile.absolutePath
                    is ExtraValue.FileUri -> getFileUri(romFile).toString()
                    is ExtraValue.Platform -> platformId
                    is ExtraValue.Literal -> extraValue.value
                }
                putExtra(key, value)
            }

            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP
            )
        }
    }

    private fun buildCustomSchemeIntent(
        emulator: EmulatorDef,
        romFile: File,
        config: LaunchConfig.CustomScheme
    ): Intent {
        val uri = Uri.Builder()
            .scheme(config.scheme)
            .authority(config.authority)
            .path(config.pathPrefix + romFile.absolutePath)
            .build()

        return Intent(emulator.launchAction).apply {
            data = uri
            setPackage(emulator.packageName)
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP
            )
        }
    }

    private fun getFileUri(file: File): Uri {
        return try {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            ).also { uri ->
                Log.d(TAG, "getFileUri: FileProvider success, uri=$uri")
            }
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "getFileUri: FileProvider failed for ${file.absolutePath}, falling back to file:// URI", e)
            Uri.fromFile(file)
        }
    }

    private fun getMimeType(file: File): String {
        // Most emulators filter by file extension, not MIME type.
        // Using */* ensures the intent resolves to the target emulator.
        return "*/*"
    }
}
