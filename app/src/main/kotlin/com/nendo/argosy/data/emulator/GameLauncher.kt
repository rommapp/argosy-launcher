package com.nendo.argosy.data.emulator

import android.app.ActivityManager
import android.content.ClipData
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.content.FileProvider
import com.nendo.argosy.data.download.ZipExtractor
import com.nendo.argosy.data.local.dao.GameFileDao
import com.nendo.argosy.data.storage.StoragePathUtils
import com.nendo.argosy.data.launcher.GameNativeLauncher
import com.nendo.argosy.data.launcher.SteamLaunchers
import com.nendo.argosy.data.local.dao.EmulatorConfigDao
import com.nendo.argosy.data.local.dao.EmulatorLaunchArgsDao
import com.nendo.argosy.data.local.entity.EmulatorLaunchArgsEntity
import com.nendo.argosy.data.repository.BiosRepository
import com.nendo.argosy.libretro.LibretroActivity
import com.nendo.argosy.libretro.LibretroCoreManager
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.GameDiscDao
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.libretro.coreoptions.CoreOptionResolver
import kotlinx.coroutines.flow.first
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
private const val EXTRA_ALREADY_LAUNCHED = "argosy.already_launched"

data class DiscOption(
    val fileName: String,
    val filePath: String,
    val discNumber: Int
)

sealed class LaunchResult {
    data class Success(val intent: Intent, val discId: Long? = null, val alreadyLaunched: Boolean = false) : LaunchResult()
    data class SelectDisc(val gameId: Long, val discs: List<DiscOption>) : LaunchResult()
    data class SelectVariant(val gameId: Long, val variants: List<VariantOption>) : LaunchResult()
    data class SelectMemcard(
        val gameId: Long,
        val emulatorId: String,
        val platformName: String,
        val cards: List<com.nendo.argosy.data.sync.platform.MemcardInfo>
    ) : LaunchResult()
    data class NoEmulator(val platformSlug: String) : LaunchResult()
    data class NoRomFile(val gamePath: String?) : LaunchResult()
    data class NoSteamLauncher(val launcherPackage: String) : LaunchResult()
    data class NoAndroidApp(val packageName: String) : LaunchResult()
    data class NoCore(val platformSlug: String, val reason: String? = null) : LaunchResult()
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
    private val emulatorLaunchArgsDao: EmulatorLaunchArgsDao,
    private val variantResolver: VariantResolver,
    private val installedAppResolver: com.nendo.argosy.data.platform.InstalledAppResolver,
    private val platformLibretroSettingsDao: com.nendo.argosy.data.local.dao.PlatformLibretroSettingsDao,
    private val emulatorDetector: EmulatorDetector,
    private val m3uManager: M3uManager,
    private val libretroCoreMgr: LibretroCoreManager,
    private val biosRepository: BiosRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val coreOptionResolver: CoreOptionResolver,
    private val coreSystemDataManager: CoreSystemDataManager,
    private val gameFileDao: GameFileDao,
    private val emulatorSaveConfigRepository: com.nendo.argosy.data.repository.EmulatorSaveConfigRepository,
    private val saveHandlerRegistry: com.nendo.argosy.data.sync.platform.PlatformSaveHandlerRegistry
) {
    private val shellAmAvailable: Boolean by lazy {
        try {
            val p = ProcessBuilder("sh", "-c", "/system/bin/am").redirectErrorStream(true).start()
            p.inputStream.readBytes()
            p.waitFor() != 255
        } catch (_: Exception) {
            false
        }
    }

    @Volatile
    private var shellLaunchRejected: Boolean = false

    @Volatile
    private var lastCoreDownloadError: String? = null

    suspend fun launch(
        gameId: Long,
        discId: Long? = null,
        forResume: Boolean = false,
        selectedDiscPath: String? = null,
        variantFileId: Long? = null,
        prefetchedGame: GameEntity? = null
    ): LaunchResult {
        Logger.debug(TAG, "launch() called: gameId=$gameId, discId=$discId, forResume=$forResume, variantFileId=$variantFileId")

        val game = prefetchedGame ?: gameDao.getById(gameId)
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

        // Variant selection: if no variant specified and variants exist, prompt the user.
        if (variantFileId == null) {
            val options = variantResolver.getVariantOptions(game)
            if (options != null) {
                return LaunchResult.SelectVariant(gameId, options)
            }
        }

        // A specific variant was selected — launch its file instead of the primary.
        if (variantFileId != null) {
            val variantFile = gameFileDao.getById(variantFileId)
            if (variantFile != null) {
                val result = launchVariantFile(game, variantFile, forResume)
                if (result is LaunchResult.Success) {
                    gameDao.updateLastPlayedFileId(game.id, variantFileId)
                }
                return result
            }
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
            gameDao.clearLocalPath(game.id)
            return LaunchResult.NoRomFile(romPath).also {
                Logger.warn(TAG, "launch() failed: ROM file missing; cleared localPath for ${romFile.name}, fullPath=$romPath")
            }
        }

        val emulator = resolveEmulator(game)
            ?: return LaunchResult.NoEmulator(game.platformSlug).also {
                Logger.warn(TAG, "launch() failed: no emulator found for platform=${game.platformSlug}")
            }

        Logger.debug(TAG, "Emulator resolved: ${emulator.displayName} (${emulator.packageName})")

        ps2MemcardGate(gameId, game, emulator)?.let { return it }

        if (emulator.id == "eden" && ZipExtractor.isNswPlatform(game.platformSlug)) {
            migrateToExtcontent(game)
        }

        // Extract ZIP/7z archives only for the built-in (in-process) emulator. External emulators
        // such as RetroArch handle archives natively and would receive a wrong file path here.
        if (emulator.launchConfig.isInProcess) {
            romFile = extractArchiveIfNeeded(romFile, game)
        }

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

        val intent = buildIntent(emulator, romFile, game, forResume)
            ?: return when (emulator.launchConfig) {
                is LaunchConfig.RetroArch, is LaunchConfig.BuiltIn -> {
                    LaunchResult.NoCore(game.platformSlug, lastCoreDownloadError).also {
                        Logger.warn(TAG, "launch() failed: no core for platform=${game.platformSlug}")
                    }
                }
                is LaunchConfig.ScummVM -> {
                    LaunchResult.NoScummVMGameId(game.title).also {
                        Logger.warn(TAG, "launch() failed: no .scummvm file for game=${game.title}")
                    }
                }
                else -> {
                    LaunchResult.Error("Failed to launch ${emulator.displayName}").also {
                        Logger.error(TAG, "launch() failed: could not build or execute intent for ${emulator.displayName}")
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
            if (emulator.launchConfig.isCoreSelectable) {
                append(" | config=${emulator.launchConfig::class.simpleName}")
            }
        })
        val alreadyLaunched = intent.getBooleanExtra(EXTRA_ALREADY_LAUNCHED, false)
        return LaunchResult.Success(intent, alreadyLaunched = alreadyLaunched)
    }

    private suspend fun launchVariantFile(game: GameEntity, variant: com.nendo.argosy.data.local.entity.GameFileEntity, forResume: Boolean): LaunchResult {
        val variantPath = variant.localPath
            ?: return LaunchResult.NoRomFile(null)
        val variantFile = File(variantPath)
        if (!variantFile.exists()) return LaunchResult.NoRomFile(variantPath)

        // Multi-disc variant: launch via its M3U, which feeds into the existing disc flow.
        if (variant.isMultiDisc) {
            val m3u = variant.m3uPath?.let { File(it) }
            if (m3u != null && m3u.exists()) {
                val emulator = resolveEmulator(game) ?: return LaunchResult.NoEmulator(game.platformSlug)
                val intent = buildIntent(emulator, m3u, game, forResume) ?: return LaunchResult.NoCore(game.platformSlug, lastCoreDownloadError)
                gameDao.recordPlayStart(game.id, java.time.Instant.now())
                val alreadyLaunched = intent.getBooleanExtra(EXTRA_ALREADY_LAUNCHED, false)
                return LaunchResult.Success(intent, alreadyLaunched = alreadyLaunched)
            }
            return LaunchResult.Error("Variant M3U file not found")
        }

        // Single-file variant.
        val emulator = resolveEmulator(game) ?: return LaunchResult.NoEmulator(game.platformSlug)
        val intent = buildIntent(emulator, variantFile, game, forResume) ?: return LaunchResult.NoCore(game.platformSlug, lastCoreDownloadError)
        gameDao.recordPlayStart(game.id, java.time.Instant.now())
        val alreadyLaunched = intent.getBooleanExtra(EXTRA_ALREADY_LAUNCHED, false)
        return LaunchResult.Success(intent, alreadyLaunched = alreadyLaunched)
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

        if (emulator.id == "eden" && ZipExtractor.isNswPlatform(game.platformSlug)) {
            migrateToExtcontent(game)
        }

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
                LaunchResult.NoCore(game.platformSlug, lastCoreDownloadError).also {
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
        val alreadyLaunched = intent.getBooleanExtra(EXTRA_ALREADY_LAUNCHED, false)
        return LaunchResult.Success(intent, alreadyLaunched = alreadyLaunched)
    }

    private suspend fun launchSteamGame(game: GameEntity): LaunchResult {
        Logger.debug(TAG, "launchSteamGame(): steamAppId=${game.steamAppId}, launcher=${game.steamLauncher}")

        val steamAppId = game.steamAppId
            ?: return LaunchResult.Error("Steam game missing app ID").also {
                Logger.warn(TAG, "launchSteamGame() failed: missing steamAppId")
            }

        val launcherPackage = game.steamLauncher ?: "native"

        val launcher = when (launcherPackage) {
            "native" -> GameNativeLauncher
            else -> SteamLaunchers.getByPackage(launcherPackage) ?: GameNativeLauncher
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
        lastCoreDownloadError = null

        val selectedCoreId = resolveBuiltinCoreId(game)
        var corePath = libretroCoreMgr.getCorePathForPlatform(game.platformSlug, selectedCoreId)
        if (corePath == null) {
            if (!com.nendo.argosy.util.NetworkUtils.isOnline(context)) {
                lastCoreDownloadError = "Built-in core for ${game.platformSlug} isn't installed yet. Connect to a network so Argosy can download it, then try again."
                Logger.error(TAG, "[BuiltIn] Core missing for ${game.platformSlug} (selected=$selectedCoreId) and device is offline; aborting launch")
                return null
            }
            Logger.info(TAG, "[BuiltIn] Core not downloaded for ${game.platformSlug} (selected=$selectedCoreId), attempting download...")
            val downloadResult = libretroCoreMgr.downloadCoreForPlatform(game.platformSlug, selectedCoreId)
            corePath = downloadResult.getOrElse { err ->
                val reason = err.message ?: "Unknown error"
                lastCoreDownloadError = com.nendo.argosy.libretro.formatCoreDownloadError(reason)
                Logger.error(TAG, "[BuiltIn] Failed to download core for platform ${game.platformSlug}: $reason")
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

        if (!coreSystemDataManager.ensureCoreSystemData(coreName, systemDir)) {
            Logger.error(TAG, "Failed to download system data for $coreName")
            return null
        }

        val coreVariables = coreOptionResolver.resolveVariables(coreName)

        Logger.info(TAG, "[BuiltIn] Launching: rom=${romFile.name}, core=$coreName, romSize=${romFile.length()}b, coreVars=${coreVariables.size}")
        val builtinSettings = userPreferencesRepository.getBuiltinEmulatorSettings().first()
        // Per-platform builtin save/state overrides (Video & Performance in platform context).
        val platformLibretroOverride = platformLibretroSettingsDao.getByPlatformId(game.platformId)
        val effectiveSavePath = platformLibretroOverride?.savePath ?: builtinSettings.customSavePath
        val effectiveStatePath = platformLibretroOverride?.statePath ?: builtinSettings.customStatePath
        return Intent(context, LibretroActivity::class.java).apply {
            putExtra(LibretroActivity.EXTRA_ROM_PATH, romFile.absolutePath)
            putExtra(LibretroActivity.EXTRA_CORE_PATH, corePath)
            putExtra(LibretroActivity.EXTRA_SYSTEM_DIR, systemDir.absolutePath)
            putExtra(LibretroActivity.EXTRA_GAME_NAME, game.title)
            putExtra(LibretroActivity.EXTRA_GAME_ID, game.id)
            putExtra(LibretroActivity.EXTRA_CORE_NAME, coreName)
            putExtra(LibretroActivity.EXTRA_CORE_VAR_KEYS, coreVariables.map { it.key }.toTypedArray())
            putExtra(LibretroActivity.EXTRA_CORE_VAR_VALUES, coreVariables.map { it.value }.toTypedArray())
            effectiveSavePath?.let { putExtra(LibretroActivity.EXTRA_SAVES_DIR, it) }
            effectiveStatePath?.let { putExtra(LibretroActivity.EXTRA_STATES_DIR, it) }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    private suspend fun ps2MemcardGate(gameId: Long, game: GameEntity, emulator: EmulatorDef): LaunchResult? {
        if (game.platformSlug != "ps2") return null
        val emulatorId = emulator.id
        val userConfig = emulatorSaveConfigRepository.getByEmulator(emulatorId)
        if (userConfig?.selectedMemcardPath != null) return null
        val basePathOverride = if (userConfig?.isUserOverride == true) userConfig.savePathPattern else null
        val cards = saveHandlerRegistry.listPs2FolderMemcardsForEmulator(
            emulatorId = emulatorId,
            emulatorPackage = emulator.packageName,
            basePathOverride = basePathOverride
        )
        if (cards.size <= 1) return null
        Logger.info(TAG, "[Launch] PS2 memcard gate triggered | gameId=$gameId, cards=${cards.size}")
        return LaunchResult.SelectMemcard(
            gameId = gameId,
            emulatorId = emulatorId,
            platformName = game.platformSlug,
            cards = cards
        )
    }

    private suspend fun resolveEmulator(game: GameEntity): EmulatorDef? {
        if (emulatorDetector.installedEmulators.value.isEmpty()) {
            emulatorDetector.detectEmulators()
        }

        val builtinEnabled = userPreferencesRepository.userPreferences.first().builtinLibretroEnabled

        var installedPackages = emulatorDetector.installedEmulators.value
            .map { it.def.packageName }
            .toSet()

        val gameOverride = emulatorConfigDao.getByGameId(game.id)
        val platformDefault = emulatorConfigDao.getDefaultForPlatform(game.platformId)

        val configuredPackage = gameOverride?.packageName ?: platformDefault?.packageName
        if (configuredPackage != null && configuredPackage !in installedPackages) {
            Logger.debug(TAG, "Configured emulator $configuredPackage not in cache, re-detecting...")
            emulatorDetector.detectEmulators()
            installedPackages = emulatorDetector.installedEmulators.value
                .map { it.def.packageName }
                .toSet()
        }

        val isBuiltinPackage: (String?) -> Boolean = { pkg ->
            pkg == EmulatorRegistry.BUILTIN_PACKAGE
        }

        if (gameOverride?.packageName != null && gameOverride.packageName in installedPackages) {
            val skipBuiltin = isBuiltinPackage(gameOverride.packageName) &&
                (!builtinEnabled || !libretroCoreMgr.isPlatformSupported(game.platformSlug))
            if (!skipBuiltin) {
                return emulatorDetector.getByPackage(gameOverride.packageName)
            }
        }

        if (platformDefault?.packageName != null && platformDefault.packageName in installedPackages) {
            val skipBuiltin = isBuiltinPackage(platformDefault.packageName) &&
                (!builtinEnabled || !libretroCoreMgr.isPlatformSupported(game.platformSlug))
            if (!skipBuiltin) {
                return emulatorDetector.getByPackage(platformDefault.packageName)
            }
        }

        val adHocPackage = gameOverride?.packageName?.takeIf {
            !EmulatorRegistry.isKnownPackage(it) && installedAppResolver.isAppInstalled(it)
        } ?: platformDefault?.packageName?.takeIf {
            !EmulatorRegistry.isKnownPackage(it) && installedAppResolver.isAppInstalled(it)
        }
        if (adHocPackage != null) {
            val displayName = (if (adHocPackage == gameOverride?.packageName) gameOverride.displayName else platformDefault?.displayName)
                ?: adHocPackage
            Logger.debug(TAG, "Resolved ad-hoc emulator binding: $adHocPackage for ${game.platformSlug}")
            return EmulatorRegistry.synthesizeAdHocEmulatorDef(
                packageName = adHocPackage,
                displayName = displayName,
                platformSlug = game.platformSlug
            )
        }

        return emulatorDetector.getPreferredEmulator(game.platformSlug, builtinEnabled)?.def
    }

    private suspend fun buildIntent(emulator: EmulatorDef, romFile: File, game: GameEntity, forResume: Boolean): Intent? {
        val configType = emulator.launchConfig::class.simpleName
        Logger.debug(TAG, "buildIntent: emulator=${emulator.displayName}, config=$configType, rom=${romFile.name}, forResume=$forResume")

        if (emulator.launchConfig.isInProcess) {
            return buildBuiltInIntent(romFile, game)
        }

        val command = buildEffectiveCommand(emulator, romFile, game, forResume) ?: return null

        Logger.info(TAG, buildString {
            append("[Command] method=${command.launchMethod}")
            append(" action=${command.action}")
            append(" pkg=${command.packageName}")
            if (command.activityClass != null) append(" activity=${command.activityClass}")
            if (command.dataUri != null) append(" data=${command.dataUri}")
            if (command.mimeType != null) append(" mime=${command.mimeType}")
            append(" flags=0x${command.intentFlags.toString(16)}")
            if (command.extras.isNotEmpty()) {
                append(" extras=[")
                command.extras.forEachIndexed { i, e ->
                    if (i > 0) append(", ")
                    append("${e.key}=")
                    when (e) {
                        is ResolvedExtra.StringExtra -> append(e.value)
                        is ResolvedExtra.UriExtra -> append(e.uri)
                        is ResolvedExtra.BoolExtra -> append(e.value)
                        is ResolvedExtra.StringArrayExtra -> append(e.values.joinToString(","))
                    }
                }
                append("]")
            }
            if (command.clipDataUri != null) append(" clipData=${command.clipDataUri}")
            if (command.grantReadUriTo.isNotEmpty()) append(" grants=${command.grantReadUriTo.size}")
        })

        val hasContentUri = command.dataUri?.scheme == "content" ||
            command.extras.any { it is ResolvedExtra.UriExtra && it.uri.scheme == "content" } ||
            command.clipDataUri?.scheme == "content"
        val needsIntentForContentUri = command.launchMethod == LaunchMethod.SHELL && hasContentUri
        if (needsIntentForContentUri) {
            Logger.debug(TAG, "Falling back to Intent (shell can't grant content:// URIs)")
        }

        val effectiveMethod = when {
            needsIntentForContentUri -> LaunchMethod.INTENT
            command.launchMethod == LaunchMethod.SHELL && !shellAmAvailable -> {
                Logger.debug(TAG, "Falling back to Intent (am not available from app process)")
                LaunchMethod.INTENT
            }
            command.launchMethod == LaunchMethod.SHELL && shellLaunchRejected -> {
                Logger.debug(TAG, "Falling back to Intent (shell launch previously rejected on this device)")
                LaunchMethod.INTENT
            }
            else -> command.launchMethod
        }

        var dispatchedMethod = effectiveMethod
        val dispatched = when (effectiveMethod) {
            LaunchMethod.INTENT -> command.copy(launchMethod = LaunchMethod.INTENT).toIntent(context)
            LaunchMethod.SHELL -> when (val outcome = launchViaShell(command)) {
                is ShellLaunchOutcome.Success -> outcome.stubIntent
                ShellLaunchOutcome.Rejected -> {
                    shellLaunchRejected = true
                    dispatchedMethod = LaunchMethod.INTENT
                    Logger.info(TAG, "Shell launch rejected, launching via intent")
                    command.copy(launchMethod = LaunchMethod.INTENT).toIntent(context)
                }
            }
        }
        return dispatched?.also {
            Logger.debug(TAG, "Launch dispatched: method=$dispatchedMethod")
        }
    }

    private suspend fun buildEffectiveCommand(
        emulator: EmulatorDef,
        romFile: File,
        game: GameEntity,
        forResume: Boolean
    ): EffectiveLaunchCommand? {
        val rawBase = when (val config = emulator.launchConfig) {
            is LaunchConfig.FileUri -> commandForFileUri(emulator, romFile, forResume)
            is LaunchConfig.FilePathExtra -> commandForFilePathExtra(emulator, romFile, config, forResume)
            is LaunchConfig.Custom -> {
                val platformConfig = emulatorConfigDao.getByGameId(game.id)
                    ?: emulatorConfigDao.getDefaultForPlatform(game.platformId)
                val legacyUseFileUri = platformConfig?.useFileUri == true
                val effectiveConfig = if (legacyUseFileUri) config.copy(useFileUri = true) else config
                commandForCustom(emulator, romFile, game.platformSlug, effectiveConfig, forResume)
            }
            is LaunchConfig.RetroArch -> commandForRetroArch(emulator, romFile, game, config, forResume)
            is LaunchConfig.CustomScheme -> commandForCustomScheme(emulator, romFile, config, forResume)
            is LaunchConfig.Vita3K -> commandForVita3K(emulator, romFile, config)
            is LaunchConfig.ScummVM -> commandForScummVM(emulator, romFile, forResume)
            is LaunchConfig.BuiltIn -> null
        } ?: return null

        // commandFor* helpers that hardcode SHELL (legacy useFileUri/useShellLaunch) must not be overwritten.
        val base = if (rawBase.launchMethod == LaunchMethod.INTENT) {
            rawBase.copy(launchMethod = emulator.defaultLaunchMethod)
        } else {
            rawBase
        }

        val override = emulatorLaunchArgsDao.getByPlatformAndEmulator(game.platformId, emulator.id)
        if (override != null && override.hasAnyOverride()) {
            Logger.debug(TAG, "Applying launch args override: data=${override.dataBinding} extras=${override.extraBinding} clip=${override.clipDataBinding} flags=${override.intentFlagsMask} mime=${override.mimeType}")
            return applyLaunchArgsOverride(base, override, romFile, forResume)
        }
        return base
    }

    private fun applyLaunchArgsOverride(
        base: EffectiveLaunchCommand,
        override: EmulatorLaunchArgsEntity,
        romFile: File,
        forResume: Boolean
    ): EffectiveLaunchCommand {
        var result = base

        override.launchMethod?.let { method ->
            result = result.copy(
                launchMethod = runCatching { LaunchMethod.valueOf(method) }.getOrDefault(result.launchMethod)
            )
        }

        override.mimeType?.let { mime ->
            if (result.dataUri != null) {
                result = result.copy(mimeType = mime)
            }
        }

        override.intentFlagsMask?.let { mask ->
            if (!forResume) {
                result = result.copy(intentFlags = mask)
            }
        }

        val dataBinding = override.dataBinding?.let { parseBindingFormat(it) }
        val extraBinding = override.extraBinding?.let { parseBindingFormat(it) }
        val clipDataBinding = override.clipDataBinding?.let { parseBindingFormat(it) }
        if (dataBinding != null || extraBinding != null || clipDataBinding != null) {
            result = rewriteBindings(result, romFile, dataBinding, extraBinding, clipDataBinding)
        }

        return result
    }

    private fun parseBindingFormat(name: String): RomBindingFormat? =
        runCatching { RomBindingFormat.valueOf(name) }.getOrNull()

    private val primaryRootSlash: String by lazy { "${StoragePathUtils.primaryExternalRoot}/" }

    private fun isPathOpaqueToOtherApps(path: String): Boolean {
        if (!path.startsWith("/storage/")) return false
        val canonical = StoragePathUtils.canonicalize(path)
        if (canonical.startsWith(primaryRootSlash)) {
            // Primary external is readable to other apps EXCEPT cross-app /Android/data/<pkg>
            // and /Android/obb/<pkg>, which scoped storage seals off on Android 11+.
            val rel = canonical.removePrefix(primaryRootSlash)
            return rel.startsWith("Android/data/") || rel.startsWith("Android/obb/")
        }
        // Any other mount under /storage/ (SD card UUID, USB OTG, secondary users) is opaque
        // to other apps via raw paths.
        return true
    }

    private fun rewriteBindings(
        command: EffectiveLaunchCommand,
        romFile: File,
        dataBinding: RomBindingFormat?,
        extraBinding: RomBindingFormat?,
        clipDataBinding: RomBindingFormat?
    ): EffectiveLaunchCommand {
        val absolutePath = romFile.absolutePath
        val grantUris = command.grantReadUriTo.toMutableList()

        val needsContentUri = isPathOpaqueToOtherApps(absolutePath)
        fun upgradeIfOpaque(b: RomBindingFormat?): RomBindingFormat? =
            if (b == RomBindingFormat.ABSOLUTE_PATH && needsContentUri) {
                Logger.warn(TAG, "ROM at $absolutePath is opaque to other apps; upgrading ABSOLUTE_PATH → FILE_PROVIDER")
                RomBindingFormat.FILE_PROVIDER
            } else b

        @Suppress("NAME_SHADOWING") val dataBinding = upgradeIfOpaque(dataBinding)
        @Suppress("NAME_SHADOWING") val extraBinding = upgradeIfOpaque(extraBinding)
        @Suppress("NAME_SHADOWING") val clipDataBinding = upgradeIfOpaque(clipDataBinding)

        fun targetUriFor(format: RomBindingFormat): Uri? = when (format) {
            RomBindingFormat.NONE -> null
            RomBindingFormat.ABSOLUTE_PATH -> Uri.parse(absolutePath)
            RomBindingFormat.FILE_PROVIDER -> {
                val uri = getFileUri(romFile)
                if (uri !in grantUris) grantUris += uri
                uri
            }
            RomBindingFormat.DOCUMENT_URI -> {
                val docUri = getDocumentUri(romFile)
                if (docUri != null && docUri !in grantUris) grantUris += docUri
                docUri
            }
        }

        // Non-file schemes (game IDs, custom schemes) are not reformattable.
        val baseDataIsFilePath = command.dataUri == null ||
            command.dataUri.scheme == null ||
            command.dataUri.scheme in listOf("file", "content") ||
            command.dataUri.toString() == absolutePath
        val newDataUri: Uri? = when {
            dataBinding == null -> command.dataUri
            !baseDataIsFilePath -> command.dataUri
            dataBinding == RomBindingFormat.NONE -> null
            else -> targetUriFor(dataBinding) ?: command.dataUri
        }

        fun isPathExtra(extra: ResolvedExtra): Boolean = when (extra) {
            is ResolvedExtra.UriExtra -> true
            is ResolvedExtra.StringExtra -> extra.value == absolutePath
            else -> false
        }
        val newExtras: List<ResolvedExtra> = when (extraBinding) {
            null -> command.extras
            RomBindingFormat.NONE -> command.extras.filterNot { isPathExtra(it) }
            RomBindingFormat.ABSOLUTE_PATH -> command.extras.map { extra ->
                if (isPathExtra(extra)) ResolvedExtra.StringExtra(extra.key, absolutePath) else extra
            }
            RomBindingFormat.FILE_PROVIDER -> {
                val uri = getFileUri(romFile)
                if (uri !in grantUris) grantUris += uri
                command.extras.map { extra ->
                    if (isPathExtra(extra)) ResolvedExtra.UriExtra(extra.key, uri) else extra
                }
            }
            RomBindingFormat.DOCUMENT_URI -> {
                val docUri = getDocumentUri(romFile)
                if (docUri != null && docUri !in grantUris) grantUris += docUri
                command.extras.map { extra ->
                    if (isPathExtra(extra) && docUri != null) ResolvedExtra.UriExtra(extra.key, docUri) else extra
                }
            }
        }

        val newClipDataUri: Uri? = when (clipDataBinding) {
            null -> command.clipDataUri
            RomBindingFormat.NONE -> null
            else -> targetUriFor(clipDataBinding) ?: command.clipDataUri
        }

        return command.copy(
            dataUri = newDataUri,
            extras = newExtras,
            clipDataUri = newClipDataUri,
            grantReadUriTo = grantUris
        )
    }

    private fun commandForFileUri(emulator: EmulatorDef, romFile: File, forResume: Boolean): EffectiveLaunchCommand {
        val uri = getFileUri(romFile)
        return EffectiveLaunchCommand(
            action = emulator.launchAction,
            packageName = emulator.packageName,
            activityClass = null,
            dataUri = uri,
            mimeType = getMimeType(romFile),
            intentFlags = if (forResume) {
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
            } else {
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK or
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
            },
            grantReadUriTo = listOf(uri),
            clipDataUri = uri
        )
    }

    private fun commandForFilePathExtra(
        emulator: EmulatorDef,
        romFile: File,
        config: LaunchConfig.FilePathExtra,
        forResume: Boolean
    ): EffectiveLaunchCommand {
        return EffectiveLaunchCommand(
            action = emulator.launchAction,
            packageName = emulator.packageName,
            activityClass = null,
            categories = emptyList(),
            extras = config.extraKeys.map { key ->
                ResolvedExtra.StringExtra(key, romFile.absolutePath)
            },
            intentFlags = if (forResume) {
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            } else {
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        )
    }

    private suspend fun commandForRetroArch(
        emulator: EmulatorDef,
        romFile: File,
        game: GameEntity,
        config: LaunchConfig.RetroArch,
        forResume: Boolean
    ): EffectiveLaunchCommand? {
        val retroArchPackage = emulator.packageName
        val dataDir = "/data/data/$retroArchPackage"
        val primaryRoot = StoragePathUtils.primaryExternalRoot
        val externalDir = "$primaryRoot/Android/data/$retroArchPackage/files"
        val configPath = "$externalDir/retroarch.cfg"

        Logger.debug(TAG, "RetroArch: package=$retroArchPackage, activity=${config.activityClass}")

        val coreName = resolveCoreName(game) ?: run {
            Logger.error(TAG, "No compatible core found for platform: ${game.platformSlug}")
            return null
        }

        val coreFileName = "${coreName}_libretro_android.so"
        Logger.debug(TAG, "RetroArch core: $coreFileName for platform: ${game.platformSlug}")

        // Grant RetroArch a content URI for the ROM. On Android 11+ scoped
        // storage, RetroArch reading /storage/<vol>/... raw can fail in the
        // receiving UID even when both apps technically have storage access.
        // Mirrors the URI-grant flow other emulators get via the dispatcher;
        // commandForRetroArch was bespoke and had been skipping it.
        val romUri: Uri? = try {
            getFileUri(romFile)
        } catch (e: Exception) {
            Logger.warn(TAG, "RetroArch: FileProvider URI unavailable for ${romFile.name}, falling back to path-only", e)
            null
        }
        val grantUris = listOfNotNull(romUri)

        val baseFlags = if (forResume) {
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        } else {
            Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TASK or
                Intent.FLAG_ACTIVITY_NO_HISTORY
        }
        val grantFlag = if (romUri != null) Intent.FLAG_GRANT_READ_URI_PERMISSION else 0

        return EffectiveLaunchCommand(
            action = emulator.launchAction,
            packageName = retroArchPackage,
            activityClass = config.activityClass,
            categories = emptyList(),
            extras = listOf(
                ResolvedExtra.StringExtra("ROM", romFile.absolutePath),
                ResolvedExtra.StringExtra("LIBRETRO", coreFileName),
                ResolvedExtra.StringExtra("CONFIGFILE", configPath),
                ResolvedExtra.StringExtra("IME", "com.android.inputmethod.latin/.LatinIME"),
                ResolvedExtra.StringExtra("DATADIR", dataDir),
                ResolvedExtra.StringExtra("SDCARD", primaryRoot),
                ResolvedExtra.StringExtra("EXTERNAL", externalDir)
            ),
            intentFlags = baseFlags or grantFlag,
            grantReadUriTo = grantUris,
            clipDataUri = romUri
        )
    }

    suspend fun forceStopEmulator(packageName: String) {
        try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            am.killBackgroundProcesses(packageName)
            Logger.debug(TAG, "killBackgroundProcesses: $packageName")
        } catch (e: Exception) {
            Logger.warn(TAG, "Failed to kill $packageName", e)
        }
    }

    /**
     * Core selection for the built-in libretro path. Walks: game override ->
     * platform default -> legacy built-in pref -> registry default. Accepts
     * any non-empty core id; the built-in path downloads from the libretro
     * buildbot, so membership in [com.nendo.argosy.libretro.LibretroCoreRegistry]
     * is a metadata hint, not a gate. If the chosen id isn't a real core, the
     * download will 404 and surface via [lastCoreDownloadError].
     */
    private suspend fun resolveBuiltinCoreId(game: GameEntity): String? {
        emulatorConfigDao.getByGameId(game.id)?.coreName?.takeIf { it.isNotBlank() }?.let {
            Logger.debug(TAG, "[BuiltIn] core selection: game override -> $it")
            return it
        }
        emulatorConfigDao.getDefaultForPlatform(game.platformId)?.coreName
            ?.takeIf { it.isNotBlank() }?.let {
                Logger.debug(TAG, "[BuiltIn] core selection: platform default -> $it")
                return it
            }
        userPreferencesRepository.getBuiltinCoreSelections().first()[game.platformSlug]
            ?.takeIf { it.isNotBlank() }?.let {
                Logger.debug(TAG, "[BuiltIn] core selection: legacy pref -> $it")
                return it
            }
        val default = com.nendo.argosy.libretro.LibretroCoreRegistry
            .getDefaultCoreForPlatform(game.platformSlug)?.coreId
        Logger.debug(TAG, "[BuiltIn] core selection: registry default -> $default")
        return default
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

    private fun commandForCustom(
        emulator: EmulatorDef,
        romFile: File,
        platformSlug: String,
        config: LaunchConfig.Custom,
        forResume: Boolean
    ): EffectiveLaunchCommand? {
        if (config.useFileUri && emulator.launchAction == Intent.ACTION_VIEW) {
            return EffectiveLaunchCommand(
                action = emulator.launchAction,
                packageName = emulator.packageName,
                activityClass = config.activityClass,
                categories = emptyList(),
                dataUri = Uri.parse(romFile.absolutePath),
                intentFlags = Intent.FLAG_ACTIVITY_CLEAR_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_NO_HISTORY,
                launchMethod = LaunchMethod.SHELL
            )
        }
        if (config.useShellLaunch && emulator.launchAction == Intent.ACTION_VIEW) {
            val uri = getFileUri(romFile)
            return EffectiveLaunchCommand(
                action = emulator.launchAction,
                packageName = emulator.packageName,
                activityClass = config.activityClass,
                categories = emptyList(),
                dataUri = uri,
                intentFlags = Intent.FLAG_ACTIVITY_CLEAR_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_NO_HISTORY,
                grantReadUriTo = listOf(uri),
                launchMethod = LaunchMethod.SHELL
            )
        }

        val resolvedExtras = mutableListOf<ResolvedExtra>()
        var documentUri: Uri? = null
        var fileUri: Uri? = null

        if (config.useAbsolutePath) {
            resolvedExtras += ResolvedExtra.StringExtra("path", romFile.absolutePath)
            resolvedExtras += ResolvedExtra.StringExtra("file", romFile.absolutePath)
            resolvedExtras += ResolvedExtra.StringExtra("filePath", romFile.absolutePath)
        }

        config.intentExtras.forEach { (key, extraValue) ->
            when (extraValue) {
                is ExtraValue.FilePath -> resolvedExtras += ResolvedExtra.StringExtra(key, romFile.absolutePath)
                is ExtraValue.DocumentUri -> {
                    val docUri = getDocumentUri(romFile)
                    if (docUri == null) {
                        Logger.error(TAG, "Cannot build document URI for ${romFile.absolutePath} -- game cannot be launched")
                        return null
                    }
                    documentUri = docUri
                    resolvedExtras += ResolvedExtra.UriExtra(key, docUri)
                }
                is ExtraValue.FileUri -> {
                    val uri = getFileUri(romFile)
                    fileUri = uri
                    resolvedExtras += ResolvedExtra.UriExtra(key, uri)
                }
                is ExtraValue.FileUriString -> {
                    val value = if (romFile.extension.equals("m3u", ignoreCase = true)) {
                        // Playlist files (.m3u) often rely on relative sibling paths.
                        // Passing content:// as a string can prevent emulators from resolving those entries.
                        romFile.absolutePath
                    } else {
                        getFileUri(romFile).also { fileUri = it }.toString()
                    }
                    resolvedExtras += ResolvedExtra.StringExtra(key, value)
                }
                is ExtraValue.Platform -> resolvedExtras += ResolvedExtra.StringExtra(key, platformSlug)
                is ExtraValue.Literal -> resolvedExtras += ResolvedExtra.StringExtra(key, extraValue.value)
                is ExtraValue.BooleanLiteral -> resolvedExtras += ResolvedExtra.BoolExtra(key, extraValue.value)
            }
        }

        if (emulator.launchAction == Intent.ACTION_VIEW) {
            val uri = fileUri ?: getFileUri(romFile)
            val mimeType = config.mimeTypeOverride ?: getMimeType(romFile)
            val grantUris = mutableListOf(uri)
            if (documentUri != null && documentUri !in grantUris) {
                grantUris += documentUri!!
            }
            return EffectiveLaunchCommand(
                action = emulator.launchAction,
                packageName = emulator.packageName,
                activityClass = config.activityClass,
                dataUri = uri,
                mimeType = mimeType,
                extras = resolvedExtras,
                intentFlags = if (forResume) {
                    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                } else {
                    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK or
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                },
                grantReadUriTo = grantUris,
                clipDataUri = documentUri ?: uri
            )
        }

        val hasUriExtra = fileUri != null || documentUri != null
        val grantUris = mutableListOf<Uri>()
        var clipDataUri: Uri? = null
        if (hasUriExtra) {
            val providerUri = fileUri ?: getFileUri(romFile)
            grantUris += providerUri
            if (documentUri != null) {
                grantUris += documentUri!!
            }
            clipDataUri = documentUri ?: providerUri
        }

        // Apply baked-in data binding (e.g. MelonDualDS requires Intent.setData
        // with a FileProvider URI even when the ROM is also passed via extras).
        var defaultDataUri: Uri? = null
        when (config.defaultDataBinding) {
            RomBindingFormat.FILE_PROVIDER -> {
                val providerUri = fileUri ?: getFileUri(romFile)
                defaultDataUri = providerUri
                if (providerUri !in grantUris) grantUris += providerUri
            }
            RomBindingFormat.DOCUMENT_URI -> {
                val docUri = documentUri ?: getDocumentUri(romFile)
                if (docUri != null) {
                    defaultDataUri = docUri
                    if (docUri !in grantUris) grantUris += docUri
                }
            }
            RomBindingFormat.ABSOLUTE_PATH -> {
                defaultDataUri = Uri.parse(romFile.absolutePath)
            }
            RomBindingFormat.NONE, null -> {}
        }
        val needsGrantFlag = hasUriExtra ||
            config.defaultDataBinding == RomBindingFormat.FILE_PROVIDER ||
            config.defaultDataBinding == RomBindingFormat.DOCUMENT_URI

        val grantFlag = if (needsGrantFlag) Intent.FLAG_GRANT_READ_URI_PERMISSION else 0

        return EffectiveLaunchCommand(
            action = emulator.launchAction,
            packageName = emulator.packageName,
            activityClass = config.activityClass,
            dataUri = defaultDataUri,
            extras = resolvedExtras,
            intentFlags = if (forResume) {
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or grantFlag
            } else {
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TASK or
                    Intent.FLAG_ACTIVITY_NO_HISTORY or grantFlag
            },
            grantReadUriTo = grantUris,
            clipDataUri = clipDataUri
        )
    }

    private fun demoteContentUrisToFilePaths(command: EffectiveLaunchCommand, romFile: File): EffectiveLaunchCommand {
        val absolutePath = romFile.absolutePath
        val pathUri = Uri.parse(absolutePath)
        val isContentUri = { uri: Uri? -> uri?.scheme == "content" }

        val newDataUri = if (isContentUri(command.dataUri)) pathUri else command.dataUri
        val newExtras = command.extras.map { extra ->
            if (extra is ResolvedExtra.UriExtra && isContentUri(extra.uri)) {
                ResolvedExtra.StringExtra(extra.key, absolutePath)
            } else extra
        }

        return command.copy(
            dataUri = newDataUri,
            clipDataUri = null,
            extras = newExtras,
            grantReadUriTo = emptyList()
        )
    }

    private sealed class ShellLaunchOutcome {
        data class Success(val stubIntent: Intent) : ShellLaunchOutcome()
        object Rejected : ShellLaunchOutcome()
    }

    private fun launchViaShell(command: EffectiveLaunchCommand): ShellLaunchOutcome {
        command.grantReadUriTo.forEach { uri ->
            try {
                context.grantUriPermission(command.packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: Exception) {
                Logger.warn(TAG, "Failed to grant URI permission to ${command.packageName} for $uri", e)
            }
        }

        val argv = command.toShellArgv()
        Logger.debug(TAG, "Shell command: ${argv.last()}")

        try {
            val process = ProcessBuilder(*argv).redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().use { it.readText() }.trim()
            val exitCode = process.waitFor()
            if (exitCode != 0) {
                Logger.debug(TAG, "Shell launch exit=$exitCode output=$output")
                return ShellLaunchOutcome.Rejected
            }
            if (output.isNotEmpty()) {
                Logger.debug(TAG, "Shell launch output: $output")
            }
        } catch (e: Exception) {
            Logger.debug(TAG, "Shell exec threw for ${command.packageName}: ${e.message}")
            return ShellLaunchOutcome.Rejected
        }

        val stub = Intent(Intent.ACTION_VIEW).apply {
            this.component = ComponentName(
                command.packageName,
                command.activityClass ?: command.packageName
            )
            putExtra(EXTRA_ALREADY_LAUNCHED, true)
        }
        return ShellLaunchOutcome.Success(stub)
    }

    private fun commandForCustomScheme(
        emulator: EmulatorDef,
        romFile: File,
        config: LaunchConfig.CustomScheme,
        forResume: Boolean
    ): EffectiveLaunchCommand {
        val uri = Uri.Builder()
            .scheme(config.scheme)
            .authority(config.authority)
            .path(config.pathPrefix + romFile.absolutePath)
            .build()

        return EffectiveLaunchCommand(
            action = emulator.launchAction,
            packageName = emulator.packageName,
            activityClass = null,
            categories = emptyList(),
            dataUri = uri,
            intentFlags = if (forResume) {
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            } else {
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TASK or
                    Intent.FLAG_ACTIVITY_NO_HISTORY
            }
        )
    }

    private fun commandForVita3K(
        emulator: EmulatorDef,
        romFile: File,
        config: LaunchConfig.Vita3K
    ): EffectiveLaunchCommand {
        val titleId = extractVitaTitleId(romFile)
        val extras = if (titleId != null) {
            Logger.debug(TAG, "Vita3K: titleId=$titleId from ${romFile.name}")
            listOf(ResolvedExtra.StringArrayExtra("AppStartParameters", arrayOf("-r", titleId)))
        } else {
            Logger.debug(TAG, "Vita3K: no titleId in ${romFile.name}, opening emulator only")
            emptyList()
        }

        return EffectiveLaunchCommand(
            action = emulator.launchAction,
            packageName = emulator.packageName,
            activityClass = config.activityClass,
            categories = emptyList(),
            extras = extras,
            intentFlags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TASK or
                Intent.FLAG_ACTIVITY_NO_HISTORY
        )
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

    private fun commandForScummVM(emulator: EmulatorDef, romFile: File, forResume: Boolean): EffectiveLaunchCommand? {
        val gameId = findScummVMGameId(romFile)
        if (gameId == null) {
            Logger.error(TAG, "[ScummVM] No .scummvm file found for: ${romFile.name}")
            return null
        }

        Logger.debug(TAG, "[ScummVM] Found game ID: $gameId")

        return EffectiveLaunchCommand(
            action = Intent.ACTION_MAIN,
            packageName = emulator.packageName,
            activityClass = "org.scummvm.scummvm.SplashActivity",
            categories = listOf(Intent.CATEGORY_LAUNCHER),
            dataUri = Uri.fromParts("scummvm", gameId, null),
            intentFlags = if (forResume) {
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            } else {
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        )
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
        val resolved = try { file.canonicalFile } catch (_: Exception) { file }
        return try {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                resolved
            ).also {
                Logger.debug(TAG, "FileProvider URI created for ${resolved.name}")
            }
        } catch (e: IllegalArgumentException) {
            Logger.error(TAG, "FileProvider failed for ${resolved.name}, file may not be launchable", e)
            throw e
        }
    }

    private fun getDocumentUri(file: File): Uri? {
        val (volumeId, relativePath) = StoragePathUtils.extractVolumeAndPath(file.absolutePath)
            ?: run {
                Logger.warn(TAG, "Cannot build document URI for non-documentable path: ${file.absolutePath}")
                return null
            }
        // Build a plain document URI (content://com.android.externalstorage.documents/document/...)
        // rather than a tree-delegated URI. The tree form embeds a caller-scoped tree grant, which
        // only resolves when the *receiver* holds a persistent SAF grant on that tree. Argosy uses
        // MANAGE_EXTERNAL_STORAGE and never holds tree grants for ROM folders, so the tree form
        // was effectively relying on the receiver app having its own grant -- fragile across
        // emulator updates. The plain document form is what a caller-delegated
        // FLAG_GRANT_READ_URI_PERMISSION can actually hand off.
        return DocumentsContract.buildDocumentUri(
            "com.android.externalstorage.documents",
            "$volumeId:$relativePath"
        )
    }

    private fun getMimeType(file: File): String {
        return "application/octet-stream"
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

    @Suppress("UNUSED_PARAMETER")
    private suspend fun applyExtensionPreferenceIfNeeded(game: GameEntity, romFile: File): File {
        // Extension switching was a workaround for old Azahar not supporting .3ds
        // Modern Azahar supports all formats natively, so this is no longer needed
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

    private companion object {
        val EXTCONTENT_SOURCE_NAMES = setOf(
            "update", "updates", "dlc", "dlcs"
        )
    }

    private suspend fun migrateToExtcontent(game: GameEntity) {
        val romPath = game.localPath ?: return
        val gameFolder = File(romPath).parentFile ?: return
        if (!gameFolder.isDirectory) return

        val sourceFolders = gameFolder.listFiles { file ->
            file.isDirectory && file.name.lowercase() in EXTCONTENT_SOURCE_NAMES
        } ?: return

        if (sourceFolders.isEmpty()) return

        val extcontent = File(gameFolder, "extcontent").apply { mkdirs() }
        var movedCount = 0

        for (folder in sourceFolders) {
            val files = folder.listFiles() ?: continue
            for (file in files) {
                if (!file.isFile) continue
                val target = File(extcontent, file.name)
                if (file.renameTo(target)) {
                    gameFileDao.updateLocalPathByOldPath(file.absolutePath, target.absolutePath)
                    movedCount++
                } else {
                    Logger.warn(TAG, "migrateToExtcontent: failed to move ${file.name}")
                }
            }
            if (folder.listFiles().isNullOrEmpty()) {
                folder.delete()
            }
        }

        if (movedCount > 0) {
            Logger.info(TAG, "migrateToExtcontent: moved $movedCount files to extcontent/ for ${game.title}")
        }
    }
}
