package com.nendo.argosy.data.emulator

import android.app.ActivityManager
import android.content.ClipData
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.nendo.argosy.data.download.ZipExtractor
import com.nendo.argosy.data.launcher.SteamLaunchers
import com.nendo.argosy.data.local.dao.EmulatorConfigDao
import com.nendo.argosy.data.repository.BiosRepository
import com.nendo.argosy.libretro.LibretroActivity
import com.nendo.argosy.libretro.LibretroCoreManager
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

data class DiscOption(
    val fileName: String,
    val filePath: String,
    val discNumber: Int
)

sealed class LaunchResult {
    data class Success(val intent: Intent, val discId: Long? = null) : LaunchResult()
    data class SelectDisc(val gameId: Long, val discs: List<DiscOption>) : LaunchResult()
    data class NoEmulator(val platformSlug: String) : LaunchResult()
    data class NoRomFile(val gamePath: String?) : LaunchResult()
    data class NoSteamLauncher(val launcherPackage: String) : LaunchResult()
    data class NoAndroidApp(val packageName: String) : LaunchResult()
    data class NoCore(val platformSlug: String) : LaunchResult()
    data class MissingDiscs(val missingDiscNumbers: List<Int>) : LaunchResult()
    data class NoScummVMGameId(val gameName: String) : LaunchResult()
    data class Error(val message: String) : LaunchResult()
}

@Singleton
class GameLauncher @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gameDao: GameDao,
    private val gameDiscDao: GameDiscDao,
    private val emulatorConfigDao: EmulatorConfigDao,
    private val emulatorDetector: EmulatorDetector,
    private val m3uManager: M3uManager,
    private val libretroCoreMgr: LibretroCoreManager,
    private val biosRepository: BiosRepository
) {
    suspend fun launch(
        gameId: Long,
        discId: Long? = null,
        forResume: Boolean = false,
        selectedDiscPath: String? = null
    ): LaunchResult {
        Logger.debug(TAG, "launch() called: gameId=$gameId, discId=$discId, forResume=$forResume, selectedDiscPath=$selectedDiscPath")

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
            return launchMultiDiscGame(game, discId, forResume)
        }

        val romPath = game.localPath
            ?: return LaunchResult.NoRomFile(null).also {
                Logger.warn(TAG, "launch() failed: no local path for game")
            }

        Logger.debug(TAG, "launch: gameId=$gameId, localPath=$romPath")

        var romFile = File(romPath)
        if (!romFile.exists()) {
            return LaunchResult.NoRomFile(romPath).also {
                Logger.warn(TAG, "launch() failed: ROM file missing: ${romFile.name}, fullPath=$romPath")
            }
        }

        // Extract ZIP/7z archives for platforms that don't use ZIP as ROM format
        romFile = extractArchiveIfNeeded(romFile, game)

        // For m3u files on platforms that don't support m3u launching, prompt for disc selection
        if (romFile.extension.lowercase() == "m3u" && !M3uManager.supportsM3u(game.platformSlug)) {
            val discFiles = M3uManager.parseAllDiscs(romFile)
            if (discFiles.size > 1 && selectedDiscPath == null) {
                val discOptions = discFiles.mapIndexed { index, file ->
                    DiscOption(
                        fileName = file.name,
                        filePath = file.absolutePath,
                        discNumber = index + 1
                    )
                }
                Logger.info(TAG, "${game.platformSlug} has ${discFiles.size} discs in m3u - prompting for selection")
                return LaunchResult.SelectDisc(gameId, discOptions)
            }
            // Use selected disc or fall back to first disc
            romFile = if (selectedDiscPath != null) {
                File(selectedDiscPath).also {
                    Logger.info(TAG, "Using selected disc: ${it.name}")
                }
            } else {
                discFiles.firstOrNull() ?: romFile
            }
        } else {
            // For m3u files, validate and fall back to disc file if broken
            romFile = validateAndResolveLaunchFile(game, romFile)
        }

        // Apply extension preference if needed (lazy rename on launch)
        romFile = applyExtensionPreferenceIfNeeded(game, romFile)

        val emulator = resolveEmulator(game)
            ?: return LaunchResult.NoEmulator(game.platformSlug).also {
                Logger.warn(TAG, "launch() failed: no emulator found for platform=${game.platformSlug}")
            }

        Logger.debug(TAG, "Emulator resolved: ${emulator.displayName} (${emulator.packageName})")

        val intent = buildIntent(emulator, romFile, game, forResume)
            ?: return when (emulator.launchConfig) {
                is LaunchConfig.RetroArch, is LaunchConfig.BuiltIn -> {
                    LaunchResult.NoCore(game.platformSlug).also {
                        Logger.warn(TAG, "launch() failed: no core for platform=${game.platformSlug}")
                    }
                }
                is LaunchConfig.ScummVM -> {
                    LaunchResult.NoScummVMGameId(game.title).also {
                        Logger.warn(TAG, "launch() failed: no .scummvm file for game=${game.title}")
                    }
                }
                else -> {
                    LaunchResult.Error("Failed to build launch intent").also {
                        Logger.error(TAG, "launch() failed: could not build intent")
                    }
                }
            }

        if (!forResume) {
            gameDao.recordPlayStart(gameId, Instant.now())
        }

        Logger.info(TAG, buildString {
            append("[Launch] ${romFile.name} via ${emulator.displayName}")
            append(" | gameId=$gameId")
            append(" | platform=${game.platformSlug}")
            append(" | size=${romFile.length()}b")
            append(" | ext=${romFile.extension}")
            if (emulator.launchConfig is LaunchConfig.BuiltIn || emulator.launchConfig is LaunchConfig.RetroArch) {
                append(" | config=${emulator.launchConfig::class.simpleName}")
            }
        })
        return LaunchResult.Success(intent)
    }

    private suspend fun launchMultiDiscGame(game: GameEntity, requestedDiscId: Long?, forResume: Boolean): LaunchResult {
        Logger.debug(TAG, "launchMultiDiscGame(): discCount query for gameId=${game.id}, forResume=$forResume")

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
            ?: return LaunchResult.NoEmulator(game.platformSlug).also {
                Logger.warn(TAG, "launchMultiDiscGame() failed: no emulator for platform=${game.platformSlug}")
            }

        Logger.debug(TAG, "Emulator resolved: ${emulator.displayName}")

        val launchFile = if (M3uManager.supportsM3u(game.platformSlug)) {
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

        val intent = buildIntent(emulator, launchFile, game, forResume)
            ?: return if (emulator.launchConfig is LaunchConfig.RetroArch) {
                LaunchResult.NoCore(game.platformSlug).also {
                    Logger.warn(TAG, "launchMultiDiscGame() failed: no core for platform=${game.platformSlug}")
                }
            } else {
                LaunchResult.Error("Failed to build launch intent").also {
                    Logger.error(TAG, "launchMultiDiscGame() failed: could not build intent")
                }
            }

        if (!forResume) {
            gameDao.recordPlayStart(game.id, Instant.now())
        }

        Logger.info(TAG, buildString {
            append("[Launch] ${launchFile.name} via ${emulator.displayName}")
            append(" | gameId=${game.id}")
            append(" | platform=${game.platformSlug}")
            append(" | multiDisc=true")
            append(" | size=${launchFile.length()}b")
            append(" | ext=${launchFile.extension}")
        })
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

    private suspend fun buildBuiltInIntent(romFile: File, game: GameEntity): Intent? {
        Logger.debug(TAG, "[BuiltIn] Preparing launch: rom=${romFile.name}, platform=${game.platformSlug}")

        var corePath = libretroCoreMgr.getCorePathForPlatform(game.platformSlug)
        if (corePath == null) {
            Logger.info(TAG, "[BuiltIn] Core not downloaded for ${game.platformSlug}, attempting download...")
            corePath = libretroCoreMgr.downloadCoreForPlatform(game.platformSlug)
            if (corePath == null) {
                Logger.error(TAG, "[BuiltIn] Failed to download core for platform: ${game.platformSlug}")
                return null
            }
            Logger.info(TAG, "[BuiltIn] Successfully downloaded core for ${game.platformSlug}")
        }

        val coreFile = File(corePath)
        Logger.debug(TAG, "[BuiltIn] Core: ${coreFile.name}, exists=${coreFile.exists()}, size=${coreFile.length()}b")

        biosRepository.distributeBiosToEmulator(game.platformSlug, EmulatorRegistry.BUILTIN_PACKAGE)
        val systemDir = biosRepository.getLibretroSystemDir()

        val coreName = coreFile.nameWithoutExtension
            .removeSuffix("_libretro_android")

        Logger.info(TAG, "[BuiltIn] Launching: rom=${romFile.name}, core=$coreName, romSize=${romFile.length()}b")
        return Intent(context, LibretroActivity::class.java).apply {
            putExtra(LibretroActivity.EXTRA_ROM_PATH, romFile.absolutePath)
            putExtra(LibretroActivity.EXTRA_CORE_PATH, corePath)
            putExtra(LibretroActivity.EXTRA_SYSTEM_DIR, systemDir.absolutePath)
            putExtra(LibretroActivity.EXTRA_GAME_NAME, game.title)
            putExtra(LibretroActivity.EXTRA_GAME_ID, game.id)
            putExtra(LibretroActivity.EXTRA_CORE_NAME, coreName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    private suspend fun resolveEmulator(game: GameEntity): EmulatorDef? {
        if (emulatorDetector.installedEmulators.value.isEmpty()) {
            emulatorDetector.detectEmulators()
        }

        var installedPackages = emulatorDetector.installedEmulators.value
            .map { it.def.packageName }
            .toSet()

        val gameOverride = emulatorConfigDao.getByGameId(game.id)
        val platformDefault = emulatorConfigDao.getDefaultForPlatform(game.platformId)

        // If configured emulator isn't in our cached list, re-detect in case it was just installed
        val configuredPackage = gameOverride?.packageName ?: platformDefault?.packageName
        if (configuredPackage != null && configuredPackage !in installedPackages) {
            Logger.debug(TAG, "Configured emulator $configuredPackage not in cache, re-detecting...")
            emulatorDetector.detectEmulators()
            installedPackages = emulatorDetector.installedEmulators.value
                .map { it.def.packageName }
                .toSet()
        }

        if (gameOverride?.packageName != null && gameOverride.packageName in installedPackages) {
            return emulatorDetector.getByPackage(gameOverride.packageName)
        }

        if (platformDefault?.packageName != null && platformDefault.packageName in installedPackages) {
            return emulatorDetector.getByPackage(platformDefault.packageName)
        }

        return emulatorDetector.getPreferredEmulator(game.platformSlug)?.def
    }

    private suspend fun buildIntent(emulator: EmulatorDef, romFile: File, game: GameEntity, forResume: Boolean): Intent? {
        val configType = emulator.launchConfig::class.simpleName
        Logger.debug(TAG, "buildIntent: emulator=${emulator.displayName}, config=$configType, rom=${romFile.name}, forResume=$forResume")

        return when (val config = emulator.launchConfig) {
            is LaunchConfig.FileUri -> buildFileUriIntent(emulator, romFile, forResume)
            is LaunchConfig.FilePathExtra -> buildFilePathIntent(emulator, romFile, config, forResume)
            is LaunchConfig.RetroArch -> buildRetroArchIntent(emulator, romFile, game, config, forResume)
            is LaunchConfig.Custom -> buildCustomIntent(emulator, romFile, game.platformSlug, config, forResume)
            is LaunchConfig.CustomScheme -> buildCustomSchemeIntent(emulator, romFile, config, forResume)
            is LaunchConfig.Vita3K -> buildVita3KIntent(emulator, romFile, config, forResume)
            is LaunchConfig.BuiltIn -> buildBuiltInIntent(romFile, game)
            is LaunchConfig.ScummVM -> buildScummVMIntent(emulator, romFile, forResume)
        }.also { intent ->
            Logger.debug(TAG, "Intent built: ${LogSanitizer.describeIntent(intent)}")
        }
    }

    private fun buildFileUriIntent(emulator: EmulatorDef, romFile: File, forResume: Boolean): Intent {
        val uri = getFileUri(romFile)

        try {
            context.grantUriPermission(emulator.packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (e: Exception) {
            Logger.warn(TAG, "Failed to grant URI permission to ${emulator.packageName}", e)
        }

        return Intent(emulator.launchAction).apply {
            setDataAndType(uri, getMimeType(romFile))
            setPackage(emulator.packageName)
            addCategory(Intent.CATEGORY_DEFAULT)
            clipData = ClipData.newRawUri(null, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (forResume) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            } else {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
        }
    }

    private fun buildFilePathIntent(
        emulator: EmulatorDef,
        romFile: File,
        config: LaunchConfig.FilePathExtra,
        forResume: Boolean
    ): Intent {
        return Intent(emulator.launchAction).apply {
            setPackage(emulator.packageName)
            config.extraKeys.forEach { key ->
                putExtra(key, romFile.absolutePath)
            }
            if (forResume) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            } else {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
        }
    }

    private suspend fun buildRetroArchIntent(
        emulator: EmulatorDef,
        romFile: File,
        game: GameEntity,
        config: LaunchConfig.RetroArch,
        forResume: Boolean
    ): Intent? {
        val retroArchPackage = emulator.packageName
        val dataDir = "/data/data/$retroArchPackage"
        val externalDir = "/storage/emulated/0/Android/data/$retroArchPackage/files"
        val configPath = "$externalDir/retroarch.cfg"

        Logger.debug(TAG, "RetroArch: package=$retroArchPackage, activity=${config.activityClass}, forResume=$forResume")

        val coreName = resolveCoreName(game)
        if (coreName == null) {
            Logger.error(TAG, "No compatible core found for platform: ${game.platformSlug}")
            return null
        }

        // NOTE: We send just the core filename and let RetroArch resolve the path internally.
        // This avoids Android 16 security restrictions on referencing paths in other apps' data dirs.
        // If this breaks on some RetroArch versions, we may need to revert to Argosy-managed cores
        // using LibretroCoreManager and passing paths within our own app directory.
        val coreFileName = "${coreName}_libretro_android.so"
        Logger.debug(TAG, "RetroArch core: $coreFileName for platform: ${game.platformSlug}")

        return Intent(emulator.launchAction).apply {
            component = ComponentName(retroArchPackage, config.activityClass)
            putExtra("ROM", romFile.absolutePath)
            putExtra("LIBRETRO", coreFileName)
            putExtra("CONFIGFILE", configPath)
            putExtra("IME", "com.android.inputmethod.latin/.LatinIME")
            putExtra("DATADIR", dataDir)
            putExtra("SDCARD", "/storage/emulated/0")
            putExtra("EXTERNAL", externalDir)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
    }

    suspend fun forceStopEmulator(packageName: String) {
        try {
            Runtime.getRuntime().exec(arrayOf("am", "force-stop", packageName))
            Logger.debug(TAG, "Force stopped via am: $packageName")
        } catch (e: Exception) {
            Logger.warn(TAG, "Failed to force stop $packageName", e)
        }
    }

    private suspend fun resolveCoreName(game: GameEntity): String? {
        val gameConfig = emulatorConfigDao.getByGameId(game.id)
        if (gameConfig?.coreName != null) {
            val corrected = normalizeLegacyCoreName(gameConfig.coreName, game.platformSlug)
            Logger.debug(TAG, "Core selection: game-specific override -> $corrected")
            return corrected
        }

        val platformConfig = emulatorConfigDao.getDefaultForPlatform(game.platformId)
        if (platformConfig?.coreName != null) {
            val corrected = normalizeLegacyCoreName(platformConfig.coreName, game.platformSlug)
            Logger.debug(TAG, "Core selection: platform default -> $corrected")
            return corrected
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

    private fun normalizeLegacyCoreName(coreName: String, platformSlug: String): String {
        val validCores = EmulatorRegistry.getCoresForPlatform(platformSlug)
        if (validCores.any { it.id == coreName }) {
            return coreName
        }
        val match = validCores.find { it.id.startsWith(coreName) }
        if (match != null) {
            Logger.debug(TAG, "Core name corrected: $coreName -> ${match.id}")
            return match.id
        }
        return coreName
    }

    private fun buildCustomIntent(
        emulator: EmulatorDef,
        romFile: File,
        platformSlug: String,
        config: LaunchConfig.Custom,
        forResume: Boolean
    ): Intent {
        val usesIntentDataUri = shouldUseIntentDataUri(emulator)
        val needsUriPermission = config.intentExtras.values.any { it is ExtraValue.FilePath || it is ExtraValue.FileUri }

        // Pre-grant URI permission to emulator package for Android 11+ Scoped Storage compatibility
        if (needsUriPermission) {
            val uri = getFileUri(romFile)
            try {
                context.grantUriPermission(emulator.packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: Exception) {
                Logger.warn(TAG, "Failed to grant URI permission to ${emulator.packageName}", e)
            }
        }

        return Intent(emulator.launchAction).apply {
            if (config.activityClass != null) {
                component = ComponentName(emulator.packageName, config.activityClass)
            } else {
                setPackage(emulator.packageName)
            }

            addCategory(Intent.CATEGORY_DEFAULT)

            if (emulator.launchAction == Intent.ACTION_VIEW && usesIntentDataUri) {
                val uri = if (config.useFileUri) {
                    Uri.fromFile(romFile)
                } else {
                    getFileUri(romFile)
                }
                val mimeType = config.mimeTypeOverride ?: getMimeType(romFile)
                setDataAndType(uri, mimeType)
                if (!config.useFileUri) {
                    clipData = ClipData.newRawUri(null, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }

            if (config.useAbsolutePath) {
                putExtra("path", romFile.absolutePath)
                putExtra("file", romFile.absolutePath)
                putExtra("filePath", romFile.absolutePath)
            }

            var hasFileUri = false
            val shouldSkipExtras = emulator.launchAction == Intent.ACTION_VIEW && usesIntentDataUri
            if (!shouldSkipExtras) {
                config.intentExtras.forEach { (key, extraValue) ->
                    // Using when as expression enforces compile-time exhaustiveness for sealed class
                    @Suppress("UNUSED_VARIABLE")
                    val handled: Unit = when (extraValue) {
                        is ExtraValue.FilePath -> putExtra(key, romFile.absolutePath)
                        is ExtraValue.FileUri -> {
                            hasFileUri = true
                            putExtra(key, getFileUri(romFile).toString())
                        }
                        is ExtraValue.Platform -> putExtra(key, platformSlug)
                        is ExtraValue.Literal -> putExtra(key, extraValue.value)
                        is ExtraValue.BooleanLiteral -> putExtra(key, extraValue.value)
                    }.let { Unit }
                }
            }

            // Include URI in clipData for emulators that may read from it (Android 11+ Scoped Storage)
            if (hasFileUri || needsUriPermission) {
                val uri = getFileUri(romFile)
                clipData = ClipData.newRawUri(null, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            if (forResume) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            } else {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
                )
            }
        }
    }

    private fun shouldUseIntentDataUri(emulator: EmulatorDef): Boolean {
        if (emulator.packageName != "me.magnum.melonds") return true

        val installed = emulatorDetector.installedEmulators.value
            .find { it.def.packageName == emulator.packageName }
            ?: return true

        // melonDS Nightly builds (Oct 2025+) support intent.data URI
        // Beta 1.10.0 (versionCode 33) only supports PATH extra
        val supportsIntentData = installed.versionCode > 33 ||
            installed.versionName?.contains("nightly", ignoreCase = true) == true

        Logger.debug(TAG, "melonDS version: ${installed.versionName} (${installed.versionCode}), " +
            "supportsIntentData=$supportsIntentData")

        return supportsIntentData
    }

    private fun buildCustomSchemeIntent(
        emulator: EmulatorDef,
        romFile: File,
        config: LaunchConfig.CustomScheme,
        forResume: Boolean
    ): Intent {
        val uri = Uri.Builder()
            .scheme(config.scheme)
            .authority(config.authority)
            .path(config.pathPrefix + romFile.absolutePath)
            .build()

        return Intent(emulator.launchAction).apply {
            data = uri
            setPackage(emulator.packageName)
            if (forResume) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            } else {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
                )
            }
        }
    }

    private fun buildVita3KIntent(
        emulator: EmulatorDef,
        romFile: File,
        config: LaunchConfig.Vita3K,
        forResume: Boolean
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

            if (forResume) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            } else {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TASK
                )
            }
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

    private fun buildScummVMIntent(emulator: EmulatorDef, romFile: File, forResume: Boolean): Intent? {
        val gameId = findScummVMGameId(romFile)
        if (gameId == null) {
            Logger.error(TAG, "[ScummVM] No .scummvm file found for: ${romFile.name}")
            return null
        }

        Logger.debug(TAG, "[ScummVM] Found game ID: $gameId")

        return Intent(Intent.ACTION_MAIN).apply {
            component = ComponentName(
                emulator.packageName,
                "org.scummvm.scummvm.SplashActivity"
            )
            addCategory(Intent.CATEGORY_LAUNCHER)
            data = Uri.fromParts("scummvm", gameId, null)

            if (forResume) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            } else {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
        }
    }

    private fun findScummVMGameId(romFile: File): String? {
        if (romFile.extension.equals("scummvm", ignoreCase = true)) {
            return readScummVMFile(romFile)
        }

        if (romFile.isDirectory) {
            val scummvmFile = romFile.listFiles()?.find {
                it.extension.equals("scummvm", ignoreCase = true)
            }
            if (scummvmFile != null) {
                return readScummVMFile(scummvmFile)
            }
        }

        val parentDir = romFile.parentFile
        if (parentDir != null) {
            val scummvmFile = parentDir.listFiles()?.find {
                it.extension.equals("scummvm", ignoreCase = true)
            }
            if (scummvmFile != null) {
                return readScummVMFile(scummvmFile)
            }
        }

        if (romFile.extension.equals("zip", ignoreCase = true)) {
            val fromZip = findScummVMGameIdInZip(romFile)
            if (fromZip != null) {
                Logger.debug(TAG, "[ScummVM] Found game ID in zip: $fromZip")
                return fromZip
            }
            val fallback = romFile.nameWithoutExtension.lowercase()
            Logger.debug(TAG, "[ScummVM] Using zip filename as game ID: $fallback")
            return fallback
        }

        return null
    }

    private fun findScummVMGameIdInZip(zipFile: File): String? {
        return try {
            java.util.zip.ZipFile(zipFile).use { zip ->
                val scummvmEntry = zip.entries().asSequence().find { entry ->
                    entry.name.endsWith(".scummvm", ignoreCase = true)
                }
                if (scummvmEntry != null) {
                    val content = zip.getInputStream(scummvmEntry).bufferedReader().readText().trim()
                    if (content.isNotEmpty()) parseScummVMGameId(content) else null
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            Logger.warn(TAG, "[ScummVM] Failed to read .scummvm from zip: ${zipFile.name}", e)
            null
        }
    }

    private fun readScummVMFile(file: File): String? {
        return try {
            val content = file.readText().trim()
            if (content.isEmpty()) return null
            parseScummVMGameId(content)
        } catch (e: Exception) {
            Logger.warn(TAG, "[ScummVM] Failed to read .scummvm file: ${file.name}", e)
            null
        }
    }

    private fun parseScummVMGameId(content: String): String {
        val colonIndex = content.indexOf(':')
        return if (colonIndex != -1) content.substring(colonIndex + 1) else content
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

    suspend fun buildInstallIntent(game: GameEntity, file: File): Intent? {
        val emulator = resolveEmulator(game) ?: return null

        Logger.debug(TAG, "buildInstallIntent: emulator=${emulator.displayName}, file=${file.name}")

        val uri = getFileUri(file)
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "*/*")
            setPackage(emulator.packageName)
            addCategory(Intent.CATEGORY_DEFAULT)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    suspend fun buildBatchInstallIntent(game: GameEntity, files: List<File>): Intent? {
        if (files.isEmpty()) return null

        val emulator = resolveEmulator(game) ?: return null

        Logger.debug(TAG, "buildBatchInstallIntent: emulator=${emulator.displayName}, files=${files.size}")

        val gameFile = game.localPath?.let { File(it) }
        if (gameFile == null || !gameFile.exists()) {
            Logger.warn(TAG, "buildBatchInstallIntent: base game file not found")
            return null
        }

        val gameUri = getFileUri(gameFile)
        val dlcUris = files.map { getFileUri(it) }

        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(gameUri, "*/*")
            setPackage(emulator.packageName)
            addCategory(Intent.CATEGORY_DEFAULT)

            clipData = ClipData.newRawUri(null, gameUri).apply {
                dlcUris.forEach { uri ->
                    addItem(ClipData.Item(uri))
                }
            }

            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
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

    private suspend fun validateAndResolveLaunchFile(game: GameEntity, romFile: File): File {
        if (romFile.extension.lowercase() != "m3u") return romFile

        // For platforms that don't support m3u launching (e.g., PS2), use the first disc
        if (!M3uManager.supportsM3u(game.platformSlug)) {
            val firstDisc = M3uManager.parseFirstDisc(romFile)
            if (firstDisc != null) {
                Logger.info(TAG, "${game.platformSlug} doesn't support m3u - using first disc: ${firstDisc.name}")
                gameDao.updateLocalPath(game.id, firstDisc.absolutePath, game.source)
                return firstDisc
            }
            Logger.warn(TAG, "Could not parse first disc from m3u for ${game.platformSlug}")
        }

        val parentDir = romFile.parentFile ?: return romFile
        val siblingFiles = parentDir.listFiles() ?: return romFile

        // Check if m3u is valid (references only launchable disc files)
        val m3uLines = try {
            romFile.readLines().filter { it.isNotBlank() && !it.startsWith("#") }
        } catch (e: Exception) {
            Logger.warn(TAG, "Failed to read m3u: ${e.message}")
            emptyList()
        }

        // Find launchable disc files in the folder (excluding macOS resource forks)
        val discFiles = siblingFiles.filter {
            !it.name.startsWith("._") && it.extension.lowercase() in setOf("cue", "gdi", "chd", "iso", "bin", "img")
        }
        val cueGdiFiles = discFiles.filter { it.extension.lowercase() in setOf("cue", "gdi") }
        val chdFiles = discFiles.filter { it.extension.lowercase() == "chd" }

        // Determine actual launchable files (prefer cue/gdi/chd over raw iso/bin)
        val launchableFiles = when {
            cueGdiFiles.isNotEmpty() -> cueGdiFiles
            chdFiles.isNotEmpty() -> chdFiles
            else -> discFiles.filter { it.extension.lowercase() in setOf("iso", "bin", "img") }
        }

        // For single-disc games, m3u is unnecessary - use disc file directly
        if (launchableFiles.size == 1) {
            val discFile = launchableFiles.first()
            Logger.info(TAG, "Single disc game - using ${discFile.name} instead of m3u")
            gameDao.updateLocalPath(game.id, discFile.absolutePath, game.source)
            return discFile
        }

        // Validate m3u references correct files
        val launchableNames = launchableFiles.map { it.name.lowercase() }.toSet()
        val allReferencesValid = m3uLines.all { line ->
            val refFile = File(parentDir, line)
            refFile.exists() && refFile.name.lowercase() in launchableNames
        }

        if (!allReferencesValid || m3uLines.size != launchableFiles.size) {
            // M3U is broken - for multi-disc, prefer first cue/chd; for single, use the disc file
            val fallback = launchableFiles.minByOrNull { it.name }
            if (fallback != null) {
                Logger.warn(TAG, "Invalid m3u detected - falling back to ${fallback.name}")
                gameDao.updateLocalPath(game.id, fallback.absolutePath, game.source)
                return fallback
            }
        }

        return romFile
    }

    private suspend fun applyExtensionPreferenceIfNeeded(game: GameEntity, romFile: File): File {
        val validExtensions = setOf("3ds", "cci")
        val currentExt = romFile.extension.lowercase()
        if (currentExt !in validExtensions) return romFile

        val preferredExt = emulatorConfigDao.getPreferredExtension(game.platformId)
            ?.takeIf { it.isNotEmpty() && it.lowercase() in validExtensions }
            ?: return romFile

        if (currentExt == preferredExt.lowercase()) return romFile

        val newPath = romFile.absolutePath.replaceAfterLast('.', preferredExt)
        val newFile = File(newPath)

        if (newFile.exists()) {
            Logger.debug(TAG, "Target file already exists, updating DB path only: ${newFile.name}")
            gameDao.updateLocalPath(game.id, newPath, game.source)
            return newFile
        }

        val renamed = romFile.renameTo(newFile)
        if (renamed) {
            Logger.info(TAG, "Renamed ${romFile.name} -> ${newFile.name}")
            gameDao.updateLocalPath(game.id, newPath, game.source)
            return newFile
        }

        Logger.warn(TAG, "Failed to rename ${romFile.name} to ${newFile.name}")
        return romFile
    }

    private fun getRomCacheDir(platformSlug: String, gameId: Long): File {
        return File(context.filesDir, "rom_cache/$platformSlug/$gameId")
    }

    private fun findCachedRom(cacheDir: File): File? {
        if (!cacheDir.exists() || !cacheDir.isDirectory) return null
        return cacheDir.listFiles()
            ?.filter { it.isFile && !it.name.startsWith(".") }
            ?.maxByOrNull { it.lastModified() }
    }

    private suspend fun extractArchiveIfNeeded(romFile: File, game: GameEntity): File {
        if (!ZipExtractor.isArchiveFile(romFile)) {
            return romFile
        }

        if (ZipExtractor.usesZipAsRomFormat(game.platformSlug)) {
            Logger.debug(TAG, "Platform ${game.platformSlug} uses ZIP as ROM format, skipping extraction")
            return romFile
        }

        val cacheDir = getRomCacheDir(game.platformSlug, game.id)
        val cachedRom = findCachedRom(cacheDir)

        if (cachedRom != null && cachedRom.exists()) {
            Logger.info(TAG, "Using cached extraction: ${cachedRom.name}")
            return cachedRom
        }

        Logger.info(TAG, "Extracting archive to cache: ${romFile.name}")

        return try {
            cacheDir.mkdirs()

            val extractedFile = if (ZipExtractor.shouldExtractArchive(romFile, game.platformSlug)) {
                val gameTitle = game.title.ifEmpty { romFile.nameWithoutExtension }
                val extracted = ZipExtractor.extractFolderRom(
                    archiveFilePath = romFile,
                    gameTitle = gameTitle,
                    platformDir = cacheDir
                )
                File(extracted.launchPath)
            } else {
                extractSingleFileArchive(romFile, cacheDir)
            }

            if (extractedFile.exists()) {
                Logger.info(TAG, "Extracted to cache: ${extractedFile.name}")
                extractedFile
            } else {
                Logger.error(TAG, "Extraction failed: extracted file doesn't exist: ${extractedFile.absolutePath}")
                cacheDir.deleteRecursively()
                romFile
            }
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to extract archive: ${e.message}", e)
            cacheDir.deleteRecursively()
            romFile
        }
    }

    private fun extractSingleFileArchive(archiveFile: File, targetDir: File): File {
        if (ZipExtractor.isSevenZFile(archiveFile)) {
            return extractSingleFile7z(archiveFile, targetDir)
        }

        java.util.zip.ZipFile(archiveFile).use { zip ->
            val entries = zip.entries().toList().filter { !it.isDirectory && !it.name.startsWith("._") }
            if (entries.isEmpty()) {
                throw IllegalStateException("Archive is empty")
            }

            val entry = entries.first()
            val fileName = File(entry.name).name
            val targetFile = File(targetDir, fileName)

            zip.getInputStream(entry).use { input ->
                targetFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            Logger.debug(TAG, "Extracted single file: ${entry.name} -> ${targetFile.name}")
            return targetFile
        }
    }

    private fun extractSingleFile7z(archiveFile: File, targetDir: File): File {
        org.apache.commons.compress.archivers.sevenz.SevenZFile.builder()
            .setFile(archiveFile)
            .get()
            .use { sevenZ ->
                var entry = sevenZ.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory && !entry.name.startsWith("._")) {
                        val fileName = File(entry.name).name
                        val targetFile = File(targetDir, fileName)

                        targetFile.outputStream().use { output ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            val inputStream = sevenZ.getInputStream(entry)
                            var bytes = inputStream.read(buffer)
                            while (bytes >= 0) {
                                output.write(buffer, 0, bytes)
                                bytes = inputStream.read(buffer)
                            }
                        }

                        Logger.debug(TAG, "Extracted single file from 7z: ${entry.name} -> ${targetFile.name}")
                        return targetFile
                    }
                    entry = sevenZ.nextEntry
                }
            }
        throw IllegalStateException("No valid files found in 7z archive")
    }
}
