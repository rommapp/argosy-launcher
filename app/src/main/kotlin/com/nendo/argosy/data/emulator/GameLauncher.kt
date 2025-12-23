package com.nendo.argosy.data.emulator

import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.nendo.argosy.data.launcher.SteamLaunchers
import com.nendo.argosy.data.local.dao.EmulatorConfigDao
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.GameDiscDao
import com.nendo.argosy.data.local.entity.GameDiscEntity
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.util.LogSanitizer
import com.nendo.argosy.util.Logger
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
    data class NoAndroidApp(val packageName: String) : LaunchResult()
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
        Logger.debug(TAG, "launch() called: gameId=$gameId, discId=$discId")

        val game = gameDao.getById(gameId)
            ?: return LaunchResult.Error("Game not found").also {
                Logger.warn(TAG, "launch() failed: game not found for id=$gameId")
            }

        Logger.debug(TAG, "Launching: platform=${game.platformId}, source=${game.source}, multiDisc=${game.isMultiDisc}")

        if (game.source == GameSource.STEAM) {
            return launchSteamGame(game)
        }

        if (game.source == GameSource.ANDROID_APP || game.platformSlug == "android") {
            return launchAndroidApp(game)
        }

        if (game.isMultiDisc) {
            return launchMultiDiscGame(game, discId)
        }

        val romPath = game.localPath
            ?: return LaunchResult.NoRomFile(null).also {
                Logger.warn(TAG, "launch() failed: no local path for game")
            }

        val romFile = File(romPath)
        if (!romFile.exists()) {
            return LaunchResult.NoRomFile(romPath).also {
                Logger.warn(TAG, "launch() failed: ROM file missing: ${romFile.name}")
            }
        }

        val emulator = resolveEmulator(game)
            ?: return LaunchResult.NoEmulator(game.platformId).also {
                Logger.warn(TAG, "launch() failed: no emulator found for platform=${game.platformId}")
            }

        Logger.debug(TAG, "Emulator resolved: ${emulator.displayName} (${emulator.packageName})")

        val intent = buildIntent(emulator, romFile, game)
            ?: return if (emulator.launchConfig is LaunchConfig.RetroArch) {
                LaunchResult.NoCore(game.platformId).also {
                    Logger.warn(TAG, "launch() failed: no RetroArch core for platform=${game.platformId}")
                }
            } else {
                LaunchResult.Error("Failed to build launch intent").also {
                    Logger.error(TAG, "launch() failed: could not build intent")
                }
            }

        gameDao.recordPlayStart(gameId, Instant.now())

        Logger.info(TAG, "Launching ${romFile.name} with ${emulator.displayName}")
        return LaunchResult.Success(intent)
    }

    private suspend fun launchMultiDiscGame(game: GameEntity, requestedDiscId: Long?): LaunchResult {
        Logger.debug(TAG, "launchMultiDiscGame(): discCount query for gameId=${game.id}")

        val discs = gameDiscDao.getDiscsForGame(game.id)
        if (discs.isEmpty()) {
            return LaunchResult.Error("No discs found for multi-disc game").also {
                Logger.warn(TAG, "launchMultiDiscGame() failed: no discs in database")
            }
        }

        Logger.debug(TAG, "Multi-disc game has ${discs.size} discs")

        val missingDiscs = discs.filter { it.localPath == null }
        if (missingDiscs.isNotEmpty()) {
            return LaunchResult.MissingDiscs(missingDiscs.map { it.discNumber }).also {
                Logger.warn(TAG, "launchMultiDiscGame() failed: missing discs ${missingDiscs.map { d -> d.discNumber }}")
            }
        }

        for (disc in discs) {
            val discFile = disc.localPath?.let { File(it) }
            if (discFile == null || !discFile.exists()) {
                return LaunchResult.MissingDiscs(listOf(disc.discNumber)).also {
                    Logger.warn(TAG, "launchMultiDiscGame() failed: disc ${disc.discNumber} file not found")
                }
            }
        }

        val emulator = resolveEmulator(game)
            ?: return LaunchResult.NoEmulator(game.platformId).also {
                Logger.warn(TAG, "launchMultiDiscGame() failed: no emulator for platform=${game.platformId}")
            }

        Logger.debug(TAG, "Emulator resolved: ${emulator.displayName}")

        val launchFile = if (m3uManager.supportsM3u(game.platformSlug)) {
            when (val m3uResult = m3uManager.ensureM3u(game)) {
                is M3uResult.Valid -> {
                    Logger.debug(TAG, "Using existing m3u: ${m3uResult.m3uFile.name}")
                    m3uResult.m3uFile
                }
                is M3uResult.Generated -> {
                    Logger.debug(TAG, "Generated m3u: ${m3uResult.m3uFile.name}")
                    m3uResult.m3uFile
                }
                is M3uResult.NotApplicable -> {
                    Logger.debug(TAG, "M3u not applicable: ${m3uResult.reason}, falling back to disc 1")
                    File(discs.minByOrNull { it.discNumber }!!.localPath!!)
                }
                is M3uResult.Error -> {
                    Logger.warn(TAG, "M3u error: ${m3uResult.message}, falling back to disc 1")
                    File(discs.minByOrNull { it.discNumber }!!.localPath!!)
                }
            }
        } else {
            val targetDisc: GameDiscEntity = when {
                requestedDiscId != null -> discs.find { it.id == requestedDiscId }
                game.lastPlayedDiscId != null -> discs.find { it.id == game.lastPlayedDiscId }
                else -> null
            } ?: discs.minByOrNull { it.discNumber }
                ?: return LaunchResult.Error("Could not determine which disc to launch").also {
                    Logger.error(TAG, "launchMultiDiscGame() failed: could not determine target disc")
                }
            Logger.debug(TAG, "Target disc: #${targetDisc.discNumber}")
            File(targetDisc.localPath!!)
        }

        val intent = buildIntent(emulator, launchFile, game)
            ?: return if (emulator.launchConfig is LaunchConfig.RetroArch) {
                LaunchResult.NoCore(game.platformId).also {
                    Logger.warn(TAG, "launchMultiDiscGame() failed: no core for platform=${game.platformId}")
                }
            } else {
                LaunchResult.Error("Failed to build launch intent").also {
                    Logger.error(TAG, "launchMultiDiscGame() failed: could not build intent")
                }
            }

        gameDao.recordPlayStart(game.id, Instant.now())

        Logger.info(TAG, "Launching multi-disc: ${launchFile.name} with ${emulator.displayName}")
        return LaunchResult.Success(intent)
    }

    private suspend fun launchSteamGame(game: GameEntity): LaunchResult {
        Logger.debug(TAG, "launchSteamGame(): steamAppId=${game.steamAppId}, launcher=${game.steamLauncher}")

        val steamAppId = game.steamAppId
            ?: return LaunchResult.Error("Steam game missing app ID").also {
                Logger.warn(TAG, "launchSteamGame() failed: missing steamAppId")
            }

        val launcherPackage = game.steamLauncher
            ?: return LaunchResult.Error("Steam game missing launcher").also {
                Logger.warn(TAG, "launchSteamGame() failed: missing launcher package")
            }

        val launcher = SteamLaunchers.getByPackage(launcherPackage)
            ?: return LaunchResult.NoSteamLauncher(launcherPackage).also {
                Logger.warn(TAG, "launchSteamGame() failed: unknown launcher $launcherPackage")
            }

        if (!launcher.isInstalled(context)) {
            return LaunchResult.NoSteamLauncher(launcherPackage).also {
                Logger.warn(TAG, "launchSteamGame() failed: ${launcher.displayName} not installed")
            }
        }

        val intent = launcher.createLaunchIntent(steamAppId)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        gameDao.recordPlayStart(game.id, Instant.now())

        Logger.info(TAG, "Launching Steam: appId=$steamAppId via ${launcher.displayName}")
        return LaunchResult.Success(intent)
    }

    private suspend fun launchAndroidApp(game: GameEntity): LaunchResult {
        Logger.debug(TAG, "launchAndroidApp(): packageName=${game.packageName}")

        val packageName = game.packageName
            ?: return LaunchResult.Error("Android game missing package name").also {
                Logger.warn(TAG, "launchAndroidApp() failed: missing packageName")
            }

        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            ?: return LaunchResult.NoAndroidApp(packageName).also {
                Logger.warn(TAG, "launchAndroidApp() failed: package not found or no launch intent")
            }

        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        gameDao.recordPlayStart(game.id, Instant.now())

        Logger.info(TAG, "Launching Android app: $packageName")
        return LaunchResult.Success(intent)
    }

    private suspend fun resolveEmulator(game: GameEntity): EmulatorDef? {
        if (emulatorDetector.installedEmulators.value.isEmpty()) {
            emulatorDetector.detectEmulators()
        }

        val gameOverride = emulatorConfigDao.getByGameId(game.id)
        if (gameOverride?.packageName != null) {
            return emulatorDetector.getByPackage(gameOverride.packageName)
        }

        val platformDefault = emulatorConfigDao.getDefaultForPlatform(game.platformId)
        if (platformDefault?.packageName != null) {
            return emulatorDetector.getByPackage(platformDefault.packageName)
        }

        return emulatorDetector.getPreferredEmulator(game.platformSlug)?.def
    }

    private suspend fun buildIntent(emulator: EmulatorDef, romFile: File, game: GameEntity): Intent? {
        val configType = emulator.launchConfig::class.simpleName
        Logger.debug(TAG, "buildIntent: emulator=${emulator.displayName}, config=$configType, rom=${romFile.name}")

        return when (val config = emulator.launchConfig) {
            is LaunchConfig.FileUri -> buildFileUriIntent(emulator, romFile)
            is LaunchConfig.FilePathExtra -> buildFilePathIntent(emulator, romFile, config)
            is LaunchConfig.RetroArch -> buildRetroArchIntent(emulator, romFile, game, config)
            is LaunchConfig.Custom -> buildCustomIntent(emulator, romFile, game.platformSlug, config)
            is LaunchConfig.CustomScheme -> buildCustomSchemeIntent(emulator, romFile, config)
            is LaunchConfig.Vita3K -> buildVita3KIntent(emulator, romFile, config)
        }.also { intent ->
            Logger.debug(TAG, "Intent built: ${LogSanitizer.describeIntent(intent)}")
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

        Logger.debug(TAG, "RetroArch: package=$retroArchPackage, activity=${config.activityClass}")

        val corePath = getCorePath(game, retroArchPackage)
        if (corePath == null) {
            Logger.error(TAG, "No compatible core found for platform: ${game.platformSlug}")
            return null
        }

        val coreName = corePath.substringAfterLast("/").removeSuffix("_libretro_android.so")
        Logger.debug(TAG, "RetroArch core: $coreName for platform: ${game.platformSlug}")

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
            Logger.warn(TAG, "No core found for platform: ${game.platformSlug}")
            return null
        }
        val corePath = "/data/data/$retroArchPackage/cores/${coreName}_libretro_android.so"
        return corePath
    }

    private suspend fun resolveCoreName(game: GameEntity): String? {
        val gameConfig = emulatorConfigDao.getByGameId(game.id)
        if (gameConfig?.coreName != null) {
            Logger.debug(TAG, "Core selection: game-specific override -> ${gameConfig.coreName}")
            return gameConfig.coreName
        }

        val platformConfig = emulatorConfigDao.getDefaultForPlatform(game.platformId)
        if (platformConfig?.coreName != null) {
            Logger.debug(TAG, "Core selection: platform default -> ${platformConfig.coreName}")
            return platformConfig.coreName
        }

        val defaultCore = EmulatorRegistry.getDefaultCore(game.platformSlug)
        if (defaultCore != null) {
            Logger.debug(TAG, "Core selection: registry default -> ${defaultCore.id}")
            return defaultCore.id
        }

        val preferredCore = EmulatorRegistry.getPreferredCore(game.platformSlug)
        Logger.debug(TAG, "Core selection: registry preferred -> $preferredCore")
        return preferredCore
    }

    private fun buildCustomIntent(
        emulator: EmulatorDef,
        romFile: File,
        platformSlug: String,
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
                    is ExtraValue.Platform -> platformSlug
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

    private fun buildVita3KIntent(
        emulator: EmulatorDef,
        romFile: File,
        config: LaunchConfig.Vita3K
    ): Intent {
        val titleId = extractVitaTitleId(romFile)

        return Intent(emulator.launchAction).apply {
            component = ComponentName(emulator.packageName, config.activityClass)

            if (titleId != null) {
                Logger.debug(TAG, "Vita3K: titleId=$titleId from ${romFile.name}")
                putExtra("AppStartParameters", arrayOf("-r", titleId))
            } else {
                Logger.debug(TAG, "Vita3K: no titleId in ${romFile.name}, opening emulator only")
            }

            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TASK
            )
        }
    }

    private fun extractVitaTitleId(romFile: File): String? {
        val filename = romFile.nameWithoutExtension

        val bracketPattern = Regex("""\[([A-Z]{4}\d{5})\]""")
        bracketPattern.find(filename)?.let { return it.groupValues[1] }

        val prefixPattern = Regex("""^([A-Z]{4}\d{5})""")
        prefixPattern.find(filename)?.let { return it.groupValues[1] }

        if (romFile.extension.equals("zip", ignoreCase = true)) {
            extractTitleIdFromZip(romFile)?.let { return it }
        }

        return null
    }

    private fun extractTitleIdFromZip(zipFile: File): String? {
        val titleIdPattern = Regex("""^([A-Z]{4}\d{5})/?""")
        return try {
            java.util.zip.ZipFile(zipFile).use { zip ->
                zip.entries().asSequence()
                    .mapNotNull { entry ->
                        titleIdPattern.find(entry.name)?.groupValues?.get(1)
                    }
                    .firstOrNull()
            }
        } catch (e: Exception) {
            Logger.warn(TAG, "Failed to read zip for titleId: ${zipFile.name}", e)
            null
        }
    }

    private fun getFileUri(file: File): Uri {
        return try {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            ).also {
                Logger.debug(TAG, "FileProvider URI created for ${file.name}")
            }
        } catch (e: IllegalArgumentException) {
            Logger.warn(TAG, "FileProvider failed for ${file.name}, using file:// URI", e)
            Uri.fromFile(file)
        }
    }

    private fun getMimeType(file: File): String {
        // Most emulators filter by file extension, not MIME type.
        // Using */* ensures the intent resolves to the target emulator.
        return "*/*"
    }

    @Suppress("unused")
    private suspend fun killRetroArchProcess(packageName: String) {
        try {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

            activityManager.killBackgroundProcesses(packageName)

            @Suppress("DEPRECATION")
            val runningProcesses = activityManager.runningAppProcesses ?: emptyList()
            val isRunning = runningProcesses.any { it.processName == packageName }

            if (isRunning) {
                Logger.debug(TAG, "RetroArch running, attempting kill...")
                activityManager.killBackgroundProcesses(packageName)
                kotlinx.coroutines.delay(200)
            }

            Logger.debug(TAG, "Killed processes for RetroArch")
        } catch (e: Exception) {
            Logger.warn(TAG, "Failed to kill RetroArch process", e)
        }
    }
}
