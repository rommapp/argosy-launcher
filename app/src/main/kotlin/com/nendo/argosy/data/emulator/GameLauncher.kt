package com.nendo.argosy.data.emulator

import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import com.nendo.argosy.data.launcher.SteamLaunchers
import com.nendo.argosy.data.local.dao.EmulatorConfigDao
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.GameDiscDao
import com.nendo.argosy.data.local.entity.GameDiscEntity
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.model.GameSource
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "GameLauncher"

sealed class LaunchResult {
    data class Success(val intent: Intent, val discId: Long? = null) : LaunchResult()
    data class NoEmulator(val platformId: String) : LaunchResult()
    data class NoRomFile(val gamePath: String?) : LaunchResult()
    data class NoSteamLauncher(val launcherPackage: String) : LaunchResult()
    data class NoCore(val platformId: String) : LaunchResult()
    data class MissingDiscs(val missingDiscNumbers: List<Int>) : LaunchResult()
    data class Error(val message: String) : LaunchResult()
}

@Singleton
class GameLauncher @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gameDao: GameDao,
    private val gameDiscDao: GameDiscDao,
    private val emulatorConfigDao: EmulatorConfigDao,
    private val emulatorDetector: EmulatorDetector,
    private val m3uManager: M3uManager
) {
    suspend fun launch(gameId: Long, discId: Long? = null): LaunchResult {
        val game = gameDao.getById(gameId)
            ?: return LaunchResult.Error("Game not found")

        if (game.source == GameSource.STEAM) {
            return launchSteamGame(game)
        }

        if (game.isMultiDisc) {
            return launchMultiDiscGame(game, discId)
        }

        val romPath = game.localPath
            ?: return LaunchResult.NoRomFile(null)

        val romFile = File(romPath)
        if (!romFile.exists()) {
            return LaunchResult.NoRomFile(romPath)
        }

        val emulator = resolveEmulator(game)
            ?: return LaunchResult.NoEmulator(game.platformId)

        val intent = buildIntent(emulator, romFile, game)
            ?: return if (emulator.launchConfig is LaunchConfig.RetroArch) {
                LaunchResult.NoCore(game.platformId)
            } else {
                LaunchResult.Error("Failed to build launch intent")
            }

        gameDao.recordPlayStart(gameId, Instant.now())

        return LaunchResult.Success(intent)
    }

    private suspend fun launchMultiDiscGame(game: GameEntity, requestedDiscId: Long?): LaunchResult {
        val discs = gameDiscDao.getDiscsForGame(game.id)
        if (discs.isEmpty()) {
            return LaunchResult.Error("No discs found for multi-disc game")
        }

        val missingDiscs = discs.filter { it.localPath == null }
        if (missingDiscs.isNotEmpty()) {
            return LaunchResult.MissingDiscs(missingDiscs.map { it.discNumber })
        }

        for (disc in discs) {
            val discFile = disc.localPath?.let { File(it) }
            if (discFile == null || !discFile.exists()) {
                return LaunchResult.MissingDiscs(listOf(disc.discNumber))
            }
        }

        val emulator = resolveEmulator(game)
            ?: return LaunchResult.NoEmulator(game.platformId)

        val launchFile = if (m3uManager.supportsM3u(game.platformId)) {
            when (val m3uResult = m3uManager.ensureM3u(game)) {
                is M3uResult.Valid -> {
                    Log.d(TAG, "Using existing m3u: ${m3uResult.m3uFile.absolutePath}")
                    m3uResult.m3uFile
                }
                is M3uResult.Generated -> {
                    Log.d(TAG, "Generated m3u: ${m3uResult.m3uFile.absolutePath}")
                    m3uResult.m3uFile
                }
                is M3uResult.NotApplicable -> {
                    Log.d(TAG, "M3u not applicable: ${m3uResult.reason}, falling back to disc 1")
                    File(discs.minByOrNull { it.discNumber }!!.localPath!!)
                }
                is M3uResult.Error -> {
                    Log.w(TAG, "M3u error: ${m3uResult.message}, falling back to disc 1")
                    File(discs.minByOrNull { it.discNumber }!!.localPath!!)
                }
            }
        } else {
            val targetDisc: GameDiscEntity = when {
                requestedDiscId != null -> discs.find { it.id == requestedDiscId }
                game.lastPlayedDiscId != null -> discs.find { it.id == game.lastPlayedDiscId }
                else -> null
            } ?: discs.minByOrNull { it.discNumber }
                ?: return LaunchResult.Error("Could not determine which disc to launch")
            File(targetDisc.localPath!!)
        }

        val intent = buildIntent(emulator, launchFile, game)
            ?: return if (emulator.launchConfig is LaunchConfig.RetroArch) {
                LaunchResult.NoCore(game.platformId)
            } else {
                LaunchResult.Error("Failed to build launch intent")
            }

        gameDao.recordPlayStart(game.id, Instant.now())

        Log.d(TAG, "Launching multi-disc game ${game.title} via ${launchFile.name}")
        return LaunchResult.Success(intent)
    }

    private suspend fun launchSteamGame(game: GameEntity): LaunchResult {
        val steamAppId = game.steamAppId
            ?: return LaunchResult.Error("Steam game missing app ID")

        val launcherPackage = game.steamLauncher
            ?: return LaunchResult.Error("Steam game missing launcher")

        val launcher = SteamLaunchers.getByPackage(launcherPackage)
            ?: return LaunchResult.NoSteamLauncher(launcherPackage)

        if (!launcher.isInstalled(context)) {
            return LaunchResult.NoSteamLauncher(launcherPackage)
        }

        val intent = launcher.createLaunchIntent(steamAppId)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        gameDao.recordPlayStart(game.id, Instant.now())

        Log.d(TAG, "Launching Steam game ${game.title} via ${launcher.displayName}")
        return LaunchResult.Success(intent)
    }

    private suspend fun resolveEmulator(game: GameEntity): EmulatorDef? {
        val gameOverride = emulatorConfigDao.getByGameId(game.id)
        if (gameOverride?.packageName != null) {
            return EmulatorRegistry.getByPackage(gameOverride.packageName)
        }

        val platformDefault = emulatorConfigDao.getDefaultForPlatform(game.platformId)
        if (platformDefault?.packageName != null) {
            return EmulatorRegistry.getByPackage(platformDefault.packageName)
        }

        if (emulatorDetector.installedEmulators.value.isEmpty()) {
            emulatorDetector.detectEmulators()
        }

        return emulatorDetector.getPreferredEmulator(game.platformId)?.def
    }

    private suspend fun buildIntent(emulator: EmulatorDef, romFile: File, game: GameEntity): Intent? {
        Log.d(TAG, "buildIntent: emulator=${emulator.displayName}, config=${emulator.launchConfig::class.simpleName}, rom=${romFile.name}")
        return when (val config = emulator.launchConfig) {
            is LaunchConfig.FileUri -> buildFileUriIntent(emulator, romFile)
            is LaunchConfig.FilePathExtra -> buildFilePathIntent(emulator, romFile, config)
            is LaunchConfig.RetroArch -> buildRetroArchIntent(emulator, romFile, game, config)
            is LaunchConfig.Custom -> buildCustomIntent(emulator, romFile, game.platformId, config)
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

    private suspend fun buildRetroArchIntent(
        emulator: EmulatorDef,
        romFile: File,
        game: GameEntity,
        config: LaunchConfig.RetroArch
    ): Intent? {
        val retroArchPackage = emulator.packageName
        val dataDir = "/data/data/$retroArchPackage"
        val externalDir = "/storage/emulated/0/Android/data/$retroArchPackage/files"
        val configPath = "$externalDir/retroarch.cfg"

        val corePath = getCorePath(game, retroArchPackage)
        if (corePath == null) {
            Log.e(TAG, "No compatible core found for platform: ${game.platformId}")
            return null
        }

        Log.d(TAG, "Using core: $corePath for platform: ${game.platformId}")

        return Intent(emulator.launchAction).apply {
            component = ComponentName(retroArchPackage, config.activityClass)
            putExtra("ROM", romFile.absolutePath)
            putExtra("LIBRETRO", corePath)
            putExtra("CONFIGFILE", configPath)
            putExtra("IME", "com.android.inputmethod.latin/.LatinIME")
            putExtra("DATADIR", dataDir)
            putExtra("SDCARD", "/storage/emulated/0")
            putExtra("EXTERNAL", externalDir)
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
        }
    }

    private suspend fun getCorePath(game: GameEntity, retroArchPackage: String): String? {
        val coreName = resolveCoreName(game)
        if (coreName == null) {
            Log.w(TAG, "No core found for platform: ${game.platformId}")
            return null
        }
        val corePath = "/data/data/$retroArchPackage/cores/${coreName}_libretro_android.so"
        Log.d(TAG, "Using core path for ${game.platformId}: $corePath (core: $coreName)")
        return corePath
    }

    private suspend fun resolveCoreName(game: GameEntity): String? {
        val gameConfig = emulatorConfigDao.getByGameId(game.id)
        if (gameConfig?.coreName != null) {
            Log.d(TAG, "Using game-specific core: ${gameConfig.coreName}")
            return gameConfig.coreName
        }

        val platformConfig = emulatorConfigDao.getDefaultForPlatform(game.platformId)
        if (platformConfig?.coreName != null) {
            Log.d(TAG, "Using platform default core: ${platformConfig.coreName}")
            return platformConfig.coreName
        }

        val defaultCore = EmulatorRegistry.getDefaultCore(game.platformId)
        if (defaultCore != null) {
            Log.d(TAG, "Using hardcoded default core: ${defaultCore.id}")
            return defaultCore.id
        }

        return EmulatorRegistry.getPreferredCore(game.platformId)
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

            var hasFileUri = false
            config.intentExtras.forEach { (key, extraValue) ->
                val value = when (extraValue) {
                    is ExtraValue.FilePath -> romFile.absolutePath
                    is ExtraValue.FileUri -> {
                        hasFileUri = true
                        getFileUri(romFile).toString()
                    }
                    is ExtraValue.Platform -> platformId
                    is ExtraValue.Literal -> extraValue.value
                }
                putExtra(key, value)
            }

            if (hasFileUri) {
                val uri = getFileUri(romFile)
                clipData = android.content.ClipData.newRawUri(null, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
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

    private suspend fun killRetroArchProcess(packageName: String) {
        try {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

            // Kill any background processes
            activityManager.killBackgroundProcesses(packageName)

            // Also try to kill via running app processes
            @Suppress("DEPRECATION")
            val runningProcesses = activityManager.runningAppProcesses ?: emptyList()
            val isRunning = runningProcesses.any { it.processName == packageName }

            if (isRunning) {
                Log.d(TAG, "RetroArch is running, attempting to kill...")
                activityManager.killBackgroundProcesses(packageName)
                // Give the system time to clean up
                kotlinx.coroutines.delay(200)
            }

            Log.d(TAG, "Killed processes for $packageName")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to kill $packageName: ${e.message}")
        }
    }
}
