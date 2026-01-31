package com.nendo.argosy.libretro

import android.graphics.RectF
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import com.nendo.argosy.libretro.ui.RAConnectionInfo
import com.nendo.argosy.libretro.ui.RAConnectionNotification
import com.nendo.argosy.ui.input.ControllerDetector
import com.nendo.argosy.ui.input.DetectedLayout
import com.nendo.argosy.ui.input.LocalABIconsSwapped
import com.nendo.argosy.ui.input.LocalSwapStartSelect
import com.nendo.argosy.ui.input.LocalXYIconsSwapped
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.nendo.argosy.data.cheats.CheatsRepository
import com.nendo.argosy.data.emulator.EmulatorRegistry
import com.nendo.argosy.data.emulator.PlaySessionTracker
import com.nendo.argosy.data.local.dao.AchievementDao
import com.nendo.argosy.data.local.dao.CheatDao
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.entity.CheatEntity
import com.nendo.argosy.ui.screens.common.AchievementUpdateBus
import com.nendo.argosy.data.local.entity.HotkeyAction
import com.nendo.argosy.data.preferences.BuiltinEmulatorSettings
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.repository.InputConfigRepository
import com.nendo.argosy.data.repository.RAAwardResult
import com.nendo.argosy.data.repository.RetroAchievementsRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import com.nendo.argosy.libretro.scanner.MemoryScanner
import com.nendo.argosy.libretro.ui.cheats.CheatDisplayItem
import com.nendo.argosy.libretro.ui.cheats.CheatsScreen
import com.nendo.argosy.libretro.ui.cheats.CheatsTab
import com.nendo.argosy.libretro.ui.AchievementPopup
import com.nendo.argosy.libretro.ui.AchievementUnlock
import com.nendo.argosy.libretro.ui.InGameMenu
import com.nendo.argosy.libretro.ui.InGameMenuAction
import com.nendo.argosy.libretro.ui.LibretroMenuInputHandler
import com.nendo.argosy.ui.input.GamepadEvent
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.theme.ALauncherTheme
import com.swordfish.libretrodroid.GLRetroView
import com.swordfish.libretrodroid.GLRetroViewData
import com.swordfish.libretrodroid.LibretroDroid
import com.swordfish.libretrodroid.ShaderConfig
import com.swordfish.libretrodroid.Variable
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject

@AndroidEntryPoint
class LibretroActivity : ComponentActivity() {
    @Inject lateinit var playSessionTracker: PlaySessionTracker
    @Inject lateinit var preferencesRepository: UserPreferencesRepository
    @Inject lateinit var inputConfigRepository: InputConfigRepository
    @Inject lateinit var cheatDao: CheatDao
    @Inject lateinit var gameDao: GameDao
    @Inject lateinit var achievementDao: AchievementDao
    @Inject lateinit var cheatsRepository: CheatsRepository
    @Inject lateinit var raRepository: RetroAchievementsRepository
    @Inject lateinit var achievementUpdateBus: AchievementUpdateBus
    @Inject lateinit var saveCacheManager: com.nendo.argosy.data.repository.SaveCacheManager

    private lateinit var retroView: GLRetroView
    private val portResolver = ControllerPortResolver()
    private val inputMapper = ControllerInputMapper()
    private lateinit var hotkeyManager: HotkeyManager
    private var vibrator: Vibrator? = null
    private lateinit var statesDir: File
    private lateinit var savesDir: File
    private lateinit var romPath: String

    private var gameId: Long = -1L
    private var coreName: String? = null
    private var startPressed = false
    private var selectPressed = false
    private var menuVisible by mutableStateOf(false)
    private var cheatsMenuVisible by mutableStateOf(false)
    private var lastCheatsTab by mutableStateOf(CheatsTab.CHEATS)
    private var cheatsNeedReset = false
    private var hasQuickSave by mutableStateOf(false)
    private var cheats by mutableStateOf<List<CheatEntity>>(emptyList())
    private var gameName: String = ""
    private var lastSramHash: String? = null
    private var aspectRatioMode: String = "Auto"
    private var screenWidth: Int = 0
    private var screenHeight: Int = 0
    private var fastForwardSpeed: Int = 4
    private var overscanCrop: Int = 0
    private var rotationDegrees: Int = -1
    private var isFastForwarding = false
    private var isRewinding = false
    private var rewindEnabled = false
    private var lastCaptureTime = 0L
    private var lastRewindTime = 0L
    private var limitHotkeysToPlayer1 = true
    private val frameIntervalMs = 16L
    private val rewindSpeed = 2
    private val memoryScanner = MemoryScanner()
    private var firstFrameRendered = false
    private var swapAB by mutableStateOf(false)
    private var swapXY by mutableStateOf(false)
    private var swapStartSelect by mutableStateOf(false)
    private var menuFocusIndex by mutableStateOf(0)
    private lateinit var menuInputHandler: LibretroMenuInputHandler
    private var activeMenuHandler: InputHandler? = null

    private var sessionTainted = false
    private var hardcoreMode by mutableStateOf(false)
    private var raSessionActive by mutableStateOf(false)
    private var gameRaId: Long? = null
    private var heartbeatJob: Job? = null
    private var launchMode = LaunchMode.RESUME

    private val achievementInfo = mutableMapOf<Long, AchievementPatchInfo>()
    private var totalAchievements by mutableStateOf(0)
    private var earnedAchievements by mutableStateOf(0)
    private var currentAchievementUnlock by mutableStateOf<AchievementUnlock?>(null)
    private val achievementUnlockQueue = mutableListOf<AchievementUnlock>()
    private var raConnectionInfo by mutableStateOf<RAConnectionInfo?>(null)

    private data class AchievementPatchInfo(
        val title: String,
        val description: String?,
        val points: Int,
        val badgeName: String?
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("LibretroActivity", "onCreate: savedInstanceState=${savedInstanceState != null}")
        enableEdgeToEdge()
        enterImmersiveMode()

        romPath = intent.getStringExtra(EXTRA_ROM_PATH) ?: return finish()
        val corePath = intent.getStringExtra(EXTRA_CORE_PATH) ?: return finish()
        val systemPath = intent.getStringExtra(EXTRA_SYSTEM_DIR)
        gameName = intent.getStringExtra(EXTRA_GAME_NAME) ?: File(romPath).nameWithoutExtension
        gameId = intent.getLongExtra(EXTRA_GAME_ID, -1L)
        coreName = intent.getStringExtra(EXTRA_CORE_NAME)
        launchMode = LaunchMode.fromString(intent.getStringExtra(LaunchMode.EXTRA_LAUNCH_MODE))
        hardcoreMode = launchMode.isHardcore

        // Validate ROM file exists and is accessible
        val romFile = File(romPath)
        if (!romFile.exists()) {
            Log.e("LibretroActivity", "ROM file not found: $romPath")
            Toast.makeText(this, "Game file not found", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        if (!romFile.canRead()) {
            Log.e("LibretroActivity", "ROM file not readable: $romPath")
            Toast.makeText(this, "Cannot access game file", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        Log.d("LibretroActivity", "ROM validated: exists=${romFile.exists()}, size=${romFile.length()}, path=$romPath")

        val systemDir = if (systemPath != null) File(systemPath) else File(filesDir, "libretro/system")
        systemDir.mkdirs()
        savesDir = File(filesDir, "libretro/saves").apply { mkdirs() }
        statesDir = File(filesDir, "libretro/states").apply { mkdirs() }

        hasQuickSave = getQuickSaveFile().exists()

        // Restore appropriate save based on launch mode
        val existingSram = kotlinx.coroutines.runBlocking {
            restoreSaveForLaunchMode()
        }
        lastSramHash = existingSram?.let { hashBytes(it) }

        val settings = kotlinx.coroutines.runBlocking {
            preferencesRepository.getBuiltinEmulatorSettings().first()
        }
        val inputPrefs = kotlinx.coroutines.runBlocking {
            preferencesRepository.preferences.first()
        }
        val detectedLayout = ControllerDetector.detectFromActiveGamepad().layout
        val isNintendoLayout = when (inputPrefs.controllerLayout) {
            "nintendo" -> true
            "xbox" -> false
            else -> detectedLayout == DetectedLayout.NINTENDO
        }
        swapAB = isNintendoLayout xor inputPrefs.swapAB
        swapXY = isNintendoLayout xor inputPrefs.swapXY
        swapStartSelect = inputPrefs.swapStartSelect
        // Key mapping uses raw pref values (like GamepadInputHandler), not icon-swap values
        menuInputHandler = LibretroMenuInputHandler(
            inputPrefs.swapAB,
            inputPrefs.swapXY,
            inputPrefs.swapStartSelect
        )
        aspectRatioMode = settings.aspectRatio
        fastForwardSpeed = settings.fastForwardSpeed
        overscanCrop = settings.overscanCrop
        rotationDegrees = settings.rotation

        // Disable rewind for heavy cores (32/64-bit systems) - state capture causes lag
        val heavyCorePatterns = listOf(
            "mupen64plus", "parallel_n64",  // N64
            "mednafen_psx", "pcsx", "duckstation", "swanstation",  // PlayStation
            "mednafen_saturn", "yabause", "kronos",  // Saturn
            "flycast", "redream",  // Dreamcast
            "ppsspp",  // PSP
            "desmume", "melonds",  // DS
            "opera",  // 3DO
            "virtualjaguar"  // Jaguar
        )
        val isHeavyCore = coreName?.let { name ->
            heavyCorePatterns.any { pattern -> name.contains(pattern, ignoreCase = true) }
        } ?: false
        rewindEnabled = settings.rewindEnabled && !isHeavyCore

        Log.d("LibretroActivity", "Core: $coreName, isHeavyCore: $isHeavyCore, rewindEnabled: $rewindEnabled")

        Log.d("LibretroActivity", "[Startup] Creating GLRetroView: core=$coreName, shader=${settings.shaderConfig}")
        val coreVariables = getCoreVariables(coreName)
        retroView = GLRetroView(
            this,
            GLRetroViewData(this).apply {
                coreFilePath = corePath
                gameFilePath = romPath
                systemDirectory = systemDir.absolutePath
                savesDirectory = savesDir.absolutePath
                saveRAMState = existingSram
                shader = settings.shaderConfig
                skipDuplicateFrames = settings.skipDuplicateFrames
                preferLowLatencyAudio = settings.lowLatencyAudio
                rumbleEventsEnabled = settings.rumbleEnabled
                variables = coreVariables
            }
        )
        Log.d("LibretroActivity", "[Startup] GLRetroView created, adding lifecycle observer")

        lifecycle.addObserver(retroView)
        Log.d("LibretroActivity", "[Startup] Lifecycle observer added, setting up error/event listeners")

        // Observe game load errors from GLRetroView
        lifecycleScope.launch {
            retroView.getGLRetroErrors().collect { errorCode ->
                val errorMessage = when (errorCode) {
                    LibretroDroid.ERROR_LOAD_LIBRARY -> "Emulator core failed to load"
                    LibretroDroid.ERROR_LOAD_GAME -> "Game file could not be loaded"
                    LibretroDroid.ERROR_GL_NOT_COMPATIBLE -> "Device graphics not supported"
                    LibretroDroid.ERROR_SERIALIZATION -> "Save file is corrupted"
                    LibretroDroid.ERROR_CHEAT -> "Cheat system error"
                    else -> "An unexpected error occurred"
                }
                Log.e("LibretroActivity", "GLRetroView error: code=$errorCode, message=$errorMessage")
                Log.e("LibretroActivity", "Context: gameId=$gameId, core=$coreName, rom=$romPath")
                Toast.makeText(this@LibretroActivity, errorMessage, Toast.LENGTH_LONG).show()
                finish()
            }
        }

        // Track startup milestones (surface creation and first frame)
        lifecycleScope.launch {
            retroView.getGLRetroEvents().collect { event ->
                when (event) {
                    is GLRetroView.GLRetroEvents.SurfaceCreated -> {
                        Log.i("LibretroActivity", "[Startup] GL surface created - render pipeline ready")
                    }
                    is GLRetroView.GLRetroEvents.FrameRendered -> {
                        if (!firstFrameRendered) {
                            firstFrameRendered = true
                            Log.i("LibretroActivity", "[Startup] First frame rendered - emulation running successfully")
                            Log.d("LibretroActivity", "[Startup] gameId=$gameId, core=$coreName, hardcore=$hardcoreMode")
                        }
                    }
                }
            }
        }

        retroView.audioEnabled = true
        retroView.filterMode = settings.filterMode
        retroView.blackFrameInsertion = settings.blackFrameInsertion
        retroView.portResolver = portResolver
        retroView.keyMapper = inputMapper

        setupInputConfig()

        if (settings.rumbleEnabled) {
            setupRumble()
        }

        if (rewindEnabled && !hardcoreMode) {
            setupRewind()
        }

        val container = FrameLayout(this).apply {
            addView(retroView, FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)

            addView(
                ComposeView(this@LibretroActivity).apply {
                    setContent {
                        ALauncherTheme {
                            CompositionLocalProvider(
                                LocalABIconsSwapped provides swapAB,
                                LocalXYIconsSwapped provides swapXY,
                                LocalSwapStartSelect provides swapStartSelect
                            ) {
                                if (menuVisible) {
                                    activeMenuHandler = InGameMenu(
                                        gameName = gameName,
                                        hasQuickSave = hasQuickSave && !hardcoreMode,
                                        cheatsAvailable = cheats.isNotEmpty() && !hardcoreMode,
                                        focusedIndex = menuFocusIndex,
                                        onFocusChange = { menuFocusIndex = it },
                                        onAction = ::handleMenuAction,
                                        isHardcoreMode = hardcoreMode
                                    )
                                }
                                if (cheatsMenuVisible) {
                                    activeMenuHandler = CheatsScreen(
                                        cheats = cheats.map { CheatDisplayItem(it.id, it.description, it.code, it.enabled, it.isUserCreated, it.lastUsedAt) },
                                        scanner = memoryScanner,
                                        initialTab = lastCheatsTab,
                                        onToggleCheat = ::handleToggleCheat,
                                        onCreateCheat = ::handleCreateCheat,
                                        onUpdateCheat = ::handleUpdateCheat,
                                        onDeleteCheat = ::handleDeleteCheat,
                                        onGetRam = { retroView.getSystemRam() },
                                        onTabChange = { lastCheatsTab = it },
                                        onDismiss = {
                                            cheatsMenuVisible = false
                                            menuVisible = true
                                            memoryScanner.markGameRan()
                                            flushCheatReset()
                                        }
                                    )
                                }
                                if (!menuVisible && !cheatsMenuVisible) {
                                    activeMenuHandler = null
                                }

                                Box(modifier = Modifier.fillMaxSize()) {
                                    AchievementPopup(
                                        achievement = currentAchievementUnlock,
                                        onDismiss = ::showNextAchievementUnlock,
                                        modifier = Modifier
                                            .align(Alignment.TopCenter)
                                            .statusBarsPadding()
                                    )

                                    RAConnectionNotification(
                                        connectionInfo = raConnectionInfo,
                                        onDismiss = { raConnectionInfo = null },
                                        modifier = Modifier
                                            .align(Alignment.TopCenter)
                                            .statusBarsPadding()
                                    )
                                }
                            }
                        }
                    }
                },
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        setContentView(container)

        container.post {
            screenWidth = container.width
            screenHeight = container.height
            Log.d("LibretroActivity", "Container size: ${screenWidth}x${screenHeight}, aspectRatioMode: $aspectRatioMode")

            Handler(Looper.getMainLooper()).postDelayed({
                Log.d("LibretroActivity", "Applying aspect ratio after delay: $aspectRatioMode")
                applyAspectRatio()
                applyOverscanCrop()
                applyRotation()
            }, 500)
        }

        if (gameId != -1L) {
            val isNewGame = launchMode == LaunchMode.NEW_CASUAL || launchMode == LaunchMode.NEW_HARDCORE
            playSessionTracker.startSession(gameId, EmulatorRegistry.BUILTIN_PACKAGE, coreName, hardcoreMode, isNewGame)
            loadCheats()
            initializeRASession()
        }
    }

    private fun initializeRASession() {
        lifecycleScope.launch {
            val isLoggedIn = raRepository.isLoggedIn()
            Log.d("LibretroActivity", "RA login check: isLoggedIn=$isLoggedIn")
            if (!isLoggedIn) {
                Log.d("LibretroActivity", "Not logged in to RA, skipping session")
                return@launch
            }

            val game = gameDao.getById(gameId) ?: return@launch
            gameRaId = game.raId
            Log.d("LibretroActivity", "Game loaded: title=${game.title}, raId=$gameRaId")

            if (gameRaId == null) {
                Log.d("LibretroActivity", "Game has no RA ID, skipping RA session")
                return@launch
            }

            Log.d("LibretroActivity", "Starting RA session for raId=$gameRaId, hardcore=$hardcoreMode")
            val sessionResult = raRepository.startSession(gameRaId!!, hardcoreMode)
            if (sessionResult.success) {
                raSessionActive = true
                val preUnlocked = sessionResult.unlockedAchievements
                Log.d("LibretroActivity", "RA session started for game $gameRaId (hardcore=$hardcoreMode), pre-unlocked=${preUnlocked.size}: $preUnlocked")

                val patchData = raRepository.getGamePatchData(gameRaId!!)
                if (patchData != null) {
                    // Compute and store ROM hash if not already cached
                    val raConsoleId = patchData.consoleId ?: 0
                    if (game.romHash == null && raConsoleId > 0) {
                        try {
                            val hash = com.swordfish.libretrodroid.LibretroDroid.computeRomHash(romPath, raConsoleId)
                            if (hash != null) {
                                Log.d("LibretroActivity", "Computed ROM hash: $hash")
                                gameDao.updateRomHash(gameId, hash)
                            } else {
                                Log.w("LibretroActivity", "Failed to compute ROM hash for $romPath (console $raConsoleId)")
                            }
                        } catch (e: Exception) {
                            Log.e("LibretroActivity", "Error computing ROM hash", e)
                        }
                    } else if (game.romHash != null) {
                        Log.d("LibretroActivity", "Using cached ROM hash: ${game.romHash}")
                    }

                    // Filter out warning pseudo-achievements (e.g., "Unknown Emulator" warnings)
                    val validAchievements = patchData.achievements?.filter { ach ->
                        !ach.title.contains("Unknown Emulator", ignoreCase = true) &&
                        !ach.title.contains("Emulator Warning", ignoreCase = true) &&
                        ach.memAddr.isNotBlank()
                    } ?: emptyList()

                    // Store info for popup display
                    validAchievements.forEach { patch ->
                        achievementInfo[patch.id] = AchievementPatchInfo(
                            title = patch.title,
                            description = patch.description,
                            points = patch.points,
                            badgeName = patch.badgeName
                        )
                    }

                    // Update diagnostic counters
                    totalAchievements = validAchievements.size
                    earnedAchievements = preUnlocked.count { it in validAchievements.map { a -> a.id }.toSet() }

                    // Show connection notification
                    raConnectionInfo = RAConnectionInfo(
                        isHardcore = hardcoreMode,
                        earnedCount = earnedAchievements,
                        totalCount = totalAchievements
                    )

                    // Filter to only unearned achievements and send to native
                    val toWatch = validAchievements
                        .filter { it.id !in preUnlocked }

                    if (toWatch.isNotEmpty()) {
                        val achievementDefs = toWatch.map { patch ->
                            com.swordfish.libretrodroid.AchievementDef(patch.id, patch.memAddr)
                        }.toTypedArray()

                        Log.d("LibretroActivity", "Sending ${achievementDefs.size} achievements to native for console $raConsoleId")
                        com.swordfish.libretrodroid.LibretroDroid.initAchievements(achievementDefs, raConsoleId)

                        retroView.achievementUnlockListener = { achievementId ->
                            onAchievementUnlocked(achievementId)
                        }
                    } else {
                        Log.d("LibretroActivity", "No achievements to watch (all pre-unlocked)")
                    }
                }

                startHeartbeatLoop()
            } else {
                Log.w("LibretroActivity", "Failed to start RA session")
            }
        }
    }

    private fun startHeartbeatLoop() {
        heartbeatJob = lifecycleScope.launch {
            while (isActive && raSessionActive) {
                delay(240_000L) // 4 minutes
                val raId = gameRaId ?: break
                raRepository.sendHeartbeat(raId, null)
                Log.d("LibretroActivity", "RA heartbeat sent for game $raId")
            }
        }
    }

    private fun onAchievementUnlocked(achievementId: Long) {
        val info = achievementInfo[achievementId]

        Log.i("LibretroActivity", "=== ACHIEVEMENT UNLOCKED ===")
        Log.i("LibretroActivity", "  ID: $achievementId")
        Log.i("LibretroActivity", "  Title: ${info?.title}")
        Log.i("LibretroActivity", "  Points: ${info?.points}")
        Log.i("LibretroActivity", "  Hardcore: $hardcoreMode")

        earnedAchievements++

        lifecycleScope.launch {
            // 1. Submit to RA server
            Log.d("LibretroActivity", "Submitting achievement $achievementId to RA server...")
            val result = raRepository.awardAchievement(
                gameId = gameId,
                achievementRaId = achievementId,
                forHardcoreMode = hardcoreMode
            )

            when (result) {
                is RAAwardResult.Success -> {
                    Log.i("LibretroActivity", "Achievement $achievementId awarded to RA successfully")
                }
                is RAAwardResult.AlreadyAwarded -> {
                    Log.d("LibretroActivity", "Achievement $achievementId already awarded on RA")
                }
                is RAAwardResult.Queued -> {
                    Log.d("LibretroActivity", "Achievement $achievementId queued for later submission")
                    com.nendo.argosy.data.sync.AchievementSubmissionWorker.schedule(this@LibretroActivity)
                }
                is RAAwardResult.Error -> {
                    Log.e("LibretroActivity", "Failed to award achievement to RA: ${result.message}")
                }
            }

            // 2. Update local database
            val now = System.currentTimeMillis()
            if (hardcoreMode) {
                achievementDao.markUnlockedHardcore(gameId, achievementId, now)
                Log.d("LibretroActivity", "Marked achievement $achievementId as hardcore unlocked in local DB")
            } else {
                achievementDao.markUnlocked(gameId, achievementId, now)
                Log.d("LibretroActivity", "Marked achievement $achievementId as unlocked in local DB")
            }

            // 3. Update game's earned count
            gameDao.incrementEarnedAchievementCount(gameId)
            Log.d("LibretroActivity", "Incremented earned achievement count for game $gameId")

            // 4. Emit update to bus for UI refresh
            val totalCount = achievementDao.countByGameId(gameId)
            val earnedCount = achievementDao.countUnlockedByGameId(gameId)
            achievementUpdateBus.emit(
                AchievementUpdateBus.AchievementUpdate(
                    gameId = gameId,
                    totalCount = totalCount,
                    earnedCount = earnedCount
                )
            )
            Log.d("LibretroActivity", "Emitted achievement update: $earnedCount/$totalCount earned for game $gameId")
        }

        val badgeUrl = info?.badgeName?.let {
            "https://media.retroachievements.org/Badge/$it.png"
        }

        val unlock = AchievementUnlock(
            id = achievementId,
            title = info?.title ?: "Achievement",
            description = info?.description,
            points = info?.points ?: 0,
            badgeUrl = badgeUrl,
            isHardcore = hardcoreMode
        )

        achievementUnlockQueue.add(unlock)
        if (currentAchievementUnlock == null) {
            showNextAchievementUnlock()
        }
    }

    private fun showNextAchievementUnlock() {
        currentAchievementUnlock = if (achievementUnlockQueue.isNotEmpty()) {
            achievementUnlockQueue.removeAt(0)
        } else {
            null
        }
    }

    private fun loadCheats() {
        lifecycleScope.launch {
            val game = gameDao.getById(gameId)
            Log.d("LibretroActivity", "loadCheats: game=$game, cheatsFetched=${game?.cheatsFetched}, configured=${cheatsRepository.isConfigured()}")

            if (game != null && !game.cheatsFetched && cheatsRepository.isConfigured()) {
                Log.d("LibretroActivity", "Fetching cheats from server for game $gameId (${game.title})")
                try {
                    val success = cheatsRepository.syncCheatsForGame(game)
                    Log.d("LibretroActivity", "Cheats sync result: $success")
                } catch (e: Exception) {
                    Log.w("LibretroActivity", "Failed to fetch cheats: ${e.message}", e)
                }
            }

            cheats = cheatDao.getCheatsForGame(gameId)
            Log.d("LibretroActivity", "Loaded ${cheats.size} cheats for game $gameId")
            if (cheats.any { it.enabled }) {
                applyAllEnabledCheats()
            }
        }
    }

    private fun applyAspectRatio() {
        if (screenWidth == 0 || screenHeight == 0) {
            Log.w("LibretroActivity", "Cannot apply aspect ratio: screen size not available")
            return
        }

        val screenRatio = screenWidth.toFloat() / screenHeight.toFloat()

        if (aspectRatioMode == "Integer") {
            Log.d("LibretroActivity", "Enabling integer scaling")
            retroView.integerScaling = true
            retroView.aspectRatioOverride = -1f
            return
        }

        retroView.integerScaling = false
        val overrideRatio = when (aspectRatioMode) {
            "4:3" -> 4f / 3f
            "16:9" -> 16f / 9f
            "Stretch" -> screenRatio
            else -> -1f
        }

        Log.d("LibretroActivity", "Setting aspect ratio override: $overrideRatio for mode: $aspectRatioMode")
        retroView.aspectRatioOverride = overrideRatio
    }

    private fun applyOverscanCrop() {
        if (overscanCrop == 0) {
            retroView.viewport = RectF(0f, 0f, 1f, 1f)
            return
        }

        val cropPercentX = overscanCrop / 256f
        val cropPercentY = overscanCrop / 240f
        val left = cropPercentX
        val top = cropPercentY
        val right = 1f - cropPercentX
        val bottom = 1f - cropPercentY

        Log.d("LibretroActivity", "Applying overscan crop: ${overscanCrop}px -> viewport($left, $top, $right, $bottom)")
        retroView.viewport = RectF(left, top, right, bottom)
    }

    private fun applyRotation() {
        Log.d("LibretroActivity", "Applying rotation: $rotationDegrees degrees")
        retroView.rotation = rotationDegrees
    }

    private fun getQuickSaveFile(): File {
        val romName = File(romPath).nameWithoutExtension
        return File(statesDir, "$romName.state")
    }

    private fun getSramFile(): File {
        val romName = File(romPath).nameWithoutExtension
        return File(savesDir, "$romName.srm")
    }

    private suspend fun restoreSaveForLaunchMode(): ByteArray? {
        if (gameId < 0) {
            Log.w("LibretroActivity", "No valid gameId, using existing save")
            return getSramFile().takeIf { it.exists() }?.readBytes()
        }

        return when (launchMode) {
            LaunchMode.NEW_HARDCORE, LaunchMode.NEW_CASUAL -> {
                Log.d("LibretroActivity", "New game mode - starting fresh (no save)")
                val sramFile = getSramFile()
                if (sramFile.exists()) {
                    sramFile.delete()
                    Log.d("LibretroActivity", "Deleted existing save file for fresh start")
                }
                null
            }
            LaunchMode.RESUME_HARDCORE -> {
                Log.d("LibretroActivity", "Resuming hardcore - restoring hardcore save")
                val hardcoreSave = saveCacheManager.getLatestHardcoreSave(gameId)
                if (hardcoreSave != null) {
                    val isValid = saveCacheManager.isValidHardcoreSave(hardcoreSave)
                    if (!isValid) {
                        Log.w("LibretroActivity", "Hardcore save missing trailer - save may have been modified externally")
                    }
                    val bytes = saveCacheManager.getSaveBytesFromEntity(hardcoreSave)
                    if (bytes != null) {
                        getSramFile().writeBytes(bytes)
                        Log.d("LibretroActivity", "Restored hardcore save (${bytes.size} bytes, valid=$isValid)")
                    }
                    bytes
                } else {
                    Log.w("LibretroActivity", "No hardcore save found, starting fresh")
                    null
                }
            }
            LaunchMode.RESUME -> {
                val game = gameDao.getById(gameId)
                val activeSaveTimestamp = game?.activeSaveTimestamp
                val activeChannel = game?.activeSaveChannel

                val targetSave = when {
                    activeSaveTimestamp != null -> {
                        Log.d("LibretroActivity", "RESUME: Looking for activated save at timestamp $activeSaveTimestamp")
                        saveCacheManager.getByTimestamp(gameId, activeSaveTimestamp)
                    }
                    activeChannel != null -> {
                        Log.d("LibretroActivity", "RESUME: Looking for most recent save in channel '$activeChannel'")
                        saveCacheManager.getMostRecentInChannel(gameId, activeChannel)
                    }
                    else -> {
                        Log.d("LibretroActivity", "RESUME: Looking for most recent save overall")
                        saveCacheManager.getMostRecentSave(gameId)
                    }
                }

                if (targetSave != null) {
                    if (targetSave.isHardcore) {
                        val isValid = saveCacheManager.isValidHardcoreSave(targetSave)
                        if (isValid) {
                            hardcoreMode = true
                            Log.d("LibretroActivity", "RESUME: Loading hardcore save, switching to hardcore mode")
                        } else {
                            Log.w("LibretroActivity", "RESUME: Hardcore save missing trailer, loading as casual")
                        }
                    }
                    val bytes = saveCacheManager.getSaveBytesFromEntity(targetSave)
                    if (bytes != null) {
                        getSramFile().writeBytes(bytes)
                        Log.d("LibretroActivity", "RESUME: Restored save (${bytes.size} bytes, hardcore=${targetSave.isHardcore})")
                    }
                    bytes
                } else {
                    Log.d("LibretroActivity", "RESUME: No cached saves, using existing .srm if present")
                    getSramFile().takeIf { it.exists() }?.readBytes()
                }
            }
        }
    }

    private fun saveSram() {
        try {
            val sramData = retroView.serializeSRAM()
            if (sramData.isEmpty()) return

            val currentHash = hashBytes(sramData)
            if (currentHash == lastSramHash) return

            getSramFile().writeBytes(sramData)
            lastSramHash = currentHash
        } catch (e: Exception) {
            // SRAM save failed silently - some cores don't support SRAM
        }
    }

    private fun hashBytes(data: ByteArray): String {
        val digest = MessageDigest.getInstance("MD5")
        return digest.digest(data).joinToString("") { "%02x".format(it) }
    }

    private fun setupInputConfig() {
        hotkeyManager = HotkeyManager(inputConfigRepository)

        lifecycleScope.launch {
            val controllerOrder = inputConfigRepository.getControllerOrder()
            portResolver.setControllerOrder(controllerOrder)

            val mappings = mutableMapOf<String, Map<com.nendo.argosy.data.repository.InputSource, Int>>()
            for (controller in inputConfigRepository.getConnectedControllers()) {
                val mapping = inputConfigRepository.getOrCreateExtendedMappingForDevice(
                    android.view.InputDevice.getDevice(controller.deviceId)!!
                )
                mappings[controller.controllerId] = mapping
            }
            inputMapper.setExtendedMappings(mappings)
            inputMapper.setPortResolver { device -> portResolver.getPort(device) }

            val mappedButtons = mappings.mapValues { (_, mapping) ->
                mapping.keys
                    .filterIsInstance<com.nendo.argosy.data.repository.InputSource.Button>()
                    .map { it.keyCode }
                    .toSet()
            }

            inputConfigRepository.initializeDefaultHotkeys()
            val hotkeys = inputConfigRepository.getEnabledHotkeys()
            hotkeyManager.setHotkeys(hotkeys)
            hotkeyManager.setControllerMappedButtons(mappedButtons)
            hotkeyManager.setLimitToPlayer1(limitHotkeysToPlayer1)

            if (controllerOrder.isNotEmpty()) {
                hotkeyManager.setPlayer1ControllerId(controllerOrder.first().controllerId)
            }

            Log.d("LibretroActivity", "Input config loaded: ${controllerOrder.size} port assignments, ${mappings.size} mappings, ${hotkeys.size} hotkeys")
        }
    }

    private fun setupRumble() {
        vibrator = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(VIBRATOR_SERVICE) as? Vibrator
        }

        lifecycleScope.launch {
            retroView.getRumbleEvents().collect { event ->
                val strength = maxOf(event.strengthStrong, event.strengthWeak)
                if (strength > 0f) {
                    val amplitude = (strength * 255).toInt().coerceIn(1, 255)
                    val duration = 50L
                    vibrator?.vibrate(VibrationEffect.createOneShot(duration, amplitude))
                }
            }
        }
    }

    private fun setupRewind() {
        lifecycleScope.launch {
            retroView.getGLRetroEvents().collect { event ->
                when (event) {
                    is GLRetroView.GLRetroEvents.SurfaceCreated -> {
                        val slotCount = 900  // ~15 sec at 60fps
                        val maxStateSize = 4 * 1024 * 1024
                        retroView.initRewindBuffer(slotCount, maxStateSize)
                        Log.d("LibretroActivity", "Rewind buffer initialized: $slotCount slots, ${maxStateSize / 1024}KB max state")
                        applyAllEnabledCheats()
                    }
                    is GLRetroView.GLRetroEvents.FrameRendered -> {
                        if (!menuVisible) {
                            val now = System.currentTimeMillis()
                            if (isRewinding) {
                                if (now - lastRewindTime >= frameIntervalMs) {
                                    lastRewindTime = now
                                    repeat(rewindSpeed) { performRewind() }
                                }
                            } else {
                                if (now - lastCaptureTime >= frameIntervalMs) {
                                    lastCaptureTime = now
                                    val captureCount = if (isFastForwarding) fastForwardSpeed else 1
                                    repeat(captureCount) { retroView.captureRewindState() }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun performRewind(): Boolean {
        if (!rewindEnabled) return false
        return retroView.rewindFrame()
    }

    private fun handleMenuAction(action: InGameMenuAction) {
        when (action) {
            InGameMenuAction.Resume -> hideMenu()
            InGameMenuAction.QuickSave -> {
                performQuickSave()
                hideMenu()
            }
            InGameMenuAction.QuickLoad -> {
                performQuickLoad()
                hideMenu()
            }
            InGameMenuAction.Cheats -> {
                menuVisible = false
                cheatsMenuVisible = true
            }
            InGameMenuAction.Quit -> finish()
        }
    }

    private fun handleToggleCheat(cheatId: Long, enabled: Boolean) {
        lifecycleScope.launch {
            if (enabled) {
                sessionTainted = true
                Log.d("LibretroActivity", "Session marked as tainted (cheats enabled)")
            }
            cheatDao.setEnabled(cheatId, enabled, System.currentTimeMillis())
            cheats = cheatDao.getCheatsForGame(gameId)
            applyCheat(cheatId, enabled)
        }
    }

    private fun handleCreateCheat(address: Int, value: Int, description: String) {
        lifecycleScope.launch {
            val maxIndex = cheatDao.getMaxCheatIndex(gameId) ?: -1
            val code = String.format("%06X:%02X", address, value)
            val newCheat = CheatEntity(
                gameId = gameId,
                cheatIndex = maxIndex + 1,
                description = description,
                code = code,
                enabled = true,
                isUserCreated = true,
                lastUsedAt = System.currentTimeMillis()
            )
            val newId = cheatDao.insert(newCheat)
            cheats = cheatDao.getCheatsForGame(gameId)
            applyCheat(newId, true)
            Log.d("LibretroActivity", "Created custom cheat: $description -> $code")
        }
    }

    private fun handleUpdateCheat(cheatId: Long, description: String, code: String) {
        lifecycleScope.launch {
            cheatDao.updateDescription(cheatId, description)
            cheatDao.updateCode(cheatId, code)
            val cheat = cheats.find { it.id == cheatId }
            cheats = cheatDao.getCheatsForGame(gameId)
            if (cheat?.enabled == true) {
                val updatedCheat = cheats.find { it.id == cheatId }
                if (updatedCheat != null) {
                    retroView.setCheat(updatedCheat.cheatIndex, true, updatedCheat.code)
                }
            }
            Log.d("LibretroActivity", "Updated cheat $cheatId: $description -> $code")
        }
    }

    private fun handleDeleteCheat(cheatId: Long) {
        lifecycleScope.launch {
            val cheat = cheats.find { it.id == cheatId }
            if (cheat?.enabled == true) {
                retroView.setCheat(cheat.cheatIndex, false, cheat.code)
            }
            cheatDao.deleteById(cheatId)
            cheats = cheatDao.getCheatsForGame(gameId)
            Log.d("LibretroActivity", "Deleted cheat $cheatId")
        }
    }

    private fun applyCheat(cheatId: Long, enabled: Boolean) {
        val cheat = cheats.find { it.id == cheatId } ?: return
        if (enabled) {
            retroView.setCheat(cheat.cheatIndex, true, cheat.code)
        } else {
            retroView.setCheat(cheat.cheatIndex, false, cheat.code)
            cheatsNeedReset = true
        }
        Log.d("LibretroActivity", "Applied cheat ${cheat.cheatIndex}: $enabled - ${cheat.description}")
    }

    private fun flushCheatReset() {
        if (!cheatsNeedReset) return
        cheatsNeedReset = false
        val stateData = retroView.serializeState()
        retroView.resetCheat()
        retroView.unserializeState(stateData)
        applyAllEnabledCheats()
        Log.d("LibretroActivity", "Flushed cheat reset cycle")
    }

    private fun applyAllEnabledCheats() {
        if (hardcoreMode) return
        cheats.filter { it.enabled }.forEachIndexed { _, cheat ->
            retroView.setCheat(cheat.cheatIndex, true, cheat.code)
            Log.d("LibretroActivity", "Applied enabled cheat ${cheat.cheatIndex}: ${cheat.description}")
        }
    }

    private fun performQuickSave() {
        try {
            val stateData = retroView.serializeState()
            getQuickSaveFile().writeBytes(stateData)
            hasQuickSave = true
            Toast.makeText(this, "State saved", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to save state", Toast.LENGTH_SHORT).show()
        }
    }

    private fun performQuickLoad() {
        try {
            val stateFile = getQuickSaveFile()
            if (stateFile.exists()) {
                val stateData = stateFile.readBytes()
                retroView.unserializeState(stateData)
                Toast.makeText(this, "State loaded", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to load state", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showMenu() {
        retroView.onPause()
        menuFocusIndex = 0
        menuVisible = true
    }

    private fun hideMenu() {
        menuVisible = false
        retroView.onResume()
    }

    private fun enterImmersiveMode() {
        WindowCompat.setDecorFitsSystemWindows(window, false)

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.navigationBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            or android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
            or android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            or android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            or android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        )
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (menuVisible || cheatsMenuVisible) {
            val gamepadEvent = menuInputHandler.mapKeyToEvent(keyCode)
            if (gamepadEvent != null) {
                val handler = activeMenuHandler
                if (handler != null) {
                    val result = dispatchToMenuHandler(gamepadEvent, handler)
                    if (result.handled) return true
                }
            }
            return super.onKeyDown(keyCode, event)
        }

        val controllerId = event.device?.let { getControllerId(it) }
        val triggeredAction = hotkeyManager.onKeyDown(keyCode, controllerId)

        if (triggeredAction != null) {
            when (triggeredAction) {
                HotkeyAction.IN_GAME_MENU -> {
                    showMenu()
                    hotkeyManager.clearState()
                    return true
                }
                HotkeyAction.QUICK_SAVE -> {
                    if (hardcoreMode) {
                        Toast.makeText(this, "Save states disabled in Hardcore mode", Toast.LENGTH_SHORT).show()
                    } else {
                        performQuickSave()
                    }
                    hotkeyManager.clearState()
                    return true
                }
                HotkeyAction.QUICK_LOAD -> {
                    if (hardcoreMode) {
                        Toast.makeText(this, "Save states disabled in Hardcore mode", Toast.LENGTH_SHORT).show()
                    } else {
                        performQuickLoad()
                    }
                    hotkeyManager.clearState()
                    return true
                }
                HotkeyAction.FAST_FORWARD -> {
                    if (!isFastForwarding) {
                        isFastForwarding = true
                        retroView.frameSpeed = fastForwardSpeed
                    }
                    return true
                }
                HotkeyAction.REWIND -> {
                    if (hardcoreMode) {
                        Toast.makeText(this, "Rewind disabled in Hardcore mode", Toast.LENGTH_SHORT).show()
                        return true
                    }
                    if (rewindEnabled && !isRewinding) {
                        isRewinding = true
                        lastRewindTime = 0L
                        retroView.frameSpeed = 1
                    }
                    return true
                }
                HotkeyAction.QUICK_SUSPEND -> {
                    saveSram()
                    finish()
                    return true
                }
            }
        }

        return retroView.onKeyDown(keyCode, event) || super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (menuVisible || cheatsMenuVisible) {
            return super.onKeyUp(keyCode, event)
        }

        hotkeyManager.onKeyUp(keyCode)

        if (!hotkeyManager.isHotkeyActive(HotkeyAction.FAST_FORWARD) && isFastForwarding) {
            isFastForwarding = false
            retroView.frameSpeed = 1
        }

        if (!hotkeyManager.isHotkeyActive(HotkeyAction.REWIND) && isRewinding) {
            isRewinding = false
        }

        return retroView.onKeyUp(keyCode, event) || super.onKeyUp(keyCode, event)
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (menuVisible || cheatsMenuVisible) {
            return super.onGenericMotionEvent(event)
        }

        val syntheticEvents = inputMapper.processMotionEvent(event)
        for (synthetic in syntheticEvents) {
            retroView.sendKeyEvent(synthetic.action, synthetic.keyCode, synthetic.port)
        }

        return retroView.onGenericMotionEvent(event) || super.onGenericMotionEvent(event)
    }

    private fun dispatchToMenuHandler(event: GamepadEvent, handler: InputHandler): InputResult {
        return when (event) {
            GamepadEvent.Up -> handler.onUp()
            GamepadEvent.Down -> handler.onDown()
            GamepadEvent.Left -> handler.onLeft()
            GamepadEvent.Right -> handler.onRight()
            GamepadEvent.Confirm -> handler.onConfirm()
            GamepadEvent.Back -> handler.onBack()
            GamepadEvent.SecondaryAction -> handler.onSecondaryAction()
            GamepadEvent.ContextMenu -> handler.onContextMenu()
            GamepadEvent.PrevSection -> handler.onPrevSection()
            GamepadEvent.NextSection -> handler.onNextSection()
            GamepadEvent.Menu -> handler.onMenu()
            GamepadEvent.Select -> handler.onSelect()
            GamepadEvent.PrevTrigger -> handler.onPrevTrigger()
            GamepadEvent.NextTrigger -> handler.onNextTrigger()
            GamepadEvent.LeftStickClick -> handler.onLeftStickClick()
            GamepadEvent.RightStickClick -> handler.onRightStickClick()
        }
    }

    override fun onResume() {
        super.onResume()
        enterImmersiveMode()
        if (!menuVisible) {
            retroView.onResume()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            enterImmersiveMode()
        }
    }

    override fun onPause() {
        saveSram()
        retroView.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        Log.d("LibretroActivity", "onDestroy: isFinishing=$isFinishing, isChangingConfigurations=$isChangingConfigurations")
        heartbeatJob?.cancel()
        raSessionActive = false
        com.swordfish.libretrodroid.LibretroDroid.clearAchievements()
        if (rewindEnabled && !hardcoreMode) {
            retroView.destroyRewindBuffer()
        }
        if (isFinishing && gameId != -1L) {
            playSessionTracker.endSession()
        }
        super.onDestroy()
    }

    private fun getControllerId(device: android.view.InputDevice): String {
        return "${device.vendorId}:${device.productId}:${device.descriptor}"
    }

    private fun getCoreVariables(coreName: String?): Array<Variable> {
        return emptyArray()
    }

    companion object {
        const val EXTRA_ROM_PATH = "rom_path"
        const val EXTRA_CORE_PATH = "core_path"
        const val EXTRA_SYSTEM_DIR = "system_dir"
        const val EXTRA_GAME_NAME = "game_name"
        const val EXTRA_GAME_ID = "game_id"
        const val EXTRA_CORE_NAME = "core_name"
    }
}
