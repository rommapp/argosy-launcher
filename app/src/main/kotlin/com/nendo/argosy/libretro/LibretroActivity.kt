package com.nendo.argosy.libretro

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.KeyEvent
import android.view.InputDevice
import android.view.MotionEvent
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import com.nendo.argosy.libretro.ui.RAConnectionNotification
import com.nendo.argosy.core.input.ControllerDetector
import com.nendo.argosy.core.input.DetectedLayout
import com.nendo.argosy.ui.input.LocalABIconsSwapped
import com.nendo.argosy.ui.input.LocalSwapStartSelect
import com.nendo.argosy.ui.input.LocalXYIconsSwapped
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.nendo.argosy.BuildConfig
import com.nendo.argosy.data.cheats.CheatsRepository
import com.nendo.argosy.data.emulator.EmulatorRegistry
import com.nendo.argosy.data.emulator.PlaySessionTracker
import com.nendo.argosy.data.repository.SaveCacheManager
import com.nendo.argosy.hardware.AmbientLedManager
import com.nendo.argosy.data.local.dao.AchievementDao
import com.nendo.argosy.data.local.dao.CheatDao
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.platform.PlatformWeightRegistry
import com.nendo.argosy.data.preferences.EffectiveLibretroSettingsResolver
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.repository.InputConfigRepository
import com.nendo.argosy.data.repository.InputSource
import com.nendo.argosy.data.repository.RetroAchievementsRepository
import com.nendo.argosy.data.netplay.CoreHashCache
import com.nendo.argosy.data.social.ArgosSocialService
import com.nendo.argosy.data.social.NetplaySessionMode
import com.nendo.argosy.data.social.SocialRepository
import com.nendo.argosy.core.event.AchievementUpdateBus
import com.nendo.argosy.libretro.ui.cheats.CheatDisplayItem
import com.nendo.argosy.libretro.ui.cheats.CheatVariantInfo
import com.nendo.argosy.libretro.ui.cheats.CheatsScreen
import com.nendo.argosy.libretro.ui.cheats.CheatsTab
import com.nendo.argosy.libretro.ui.AchievementPopup
import com.nendo.argosy.libretro.ui.AutoRestorePrompt
import com.nendo.argosy.libretro.ui.InGameMenu
import com.nendo.argosy.libretro.ui.InGameMenuAction
import com.nendo.argosy.libretro.ui.NetplayConnectionProgressOverlay
import com.nendo.argosy.libretro.ui.NetplayFriendPickerDialog
import com.nendo.argosy.libretro.ui.NetplayHostDisconnectPrompt
import com.nendo.argosy.libretro.ui.NetplayJoinRequestDialog
import com.nendo.argosy.libretro.ui.NetplayModePickerDialog
import com.nendo.argosy.libretro.ui.NetplayMenuRole
import com.nendo.argosy.libretro.ui.NetplayQualityInfo
import com.nendo.argosy.libretro.ui.NetplayQualityWarningPrompt
import com.nendo.argosy.libretro.ui.NetplayBorderHud
import com.nendo.argosy.libretro.ui.NetplayReconnectingOverlay
import com.nendo.argosy.libretro.ui.InGameStateManager
import com.nendo.argosy.libretro.ui.StateManagerViewMode
import com.nendo.argosy.libretro.ui.InGameControlsAction
import com.nendo.argosy.libretro.ui.InGameControlsState
import com.nendo.argosy.libretro.ui.InGameModalCallbacks
import com.nendo.argosy.libretro.ui.InGameSettingsScreen
import com.nendo.argosy.libretro.ui.InGameShaderChainScreen
import com.nendo.argosy.libretro.ui.LibretroGamepadInputHandler
import com.nendo.argosy.libretro.ui.LibretroMenuInputHandler
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.LocalGamepadInputHandler
import com.nendo.argosy.ui.screens.settings.libretro.InGameLibretroSettingsAccessor
import com.nendo.argosy.libretro.frame.FrameDownloader
import com.nendo.argosy.libretro.frame.FrameManager
import com.nendo.argosy.libretro.frame.FrameRegistry
import com.nendo.argosy.libretro.ui.InGameFrameScreen
import com.nendo.argosy.libretro.shader.ShaderChainManager
import com.nendo.argosy.libretro.shader.ShaderDownloader
import com.nendo.argosy.libretro.shader.ShaderPreviewRenderer
import com.nendo.argosy.libretro.shader.ShaderRegistry
import com.nendo.argosy.ui.theme.ALauncherTheme
import com.nendo.argosy.data.preferences.BuiltinEmulatorSettings
import com.nendo.argosy.util.AppPaths
import com.swordfish.libretrodroid.GLRetroView
import com.swordfish.libretrodroid.GLRetroViewData
import com.swordfish.libretrodroid.LibretroDroid
import com.swordfish.libretrodroid.Variable
import com.nendo.argosy.libretro.coreoptions.CoreControlManifestRegistry
import com.nendo.argosy.libretro.coreoptions.CoreOptionManifestRegistry
import com.nendo.argosy.data.local.entity.CoreInputMode
import com.nendo.argosy.data.local.entity.CoreOptionOverrideEntity
import com.nendo.argosy.data.local.entity.GameCoreOptionOverrideEntity
import com.nendo.argosy.ui.screens.settings.CoreOptionViewItem
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

@AndroidEntryPoint
class LibretroActivity : ComponentActivity() {
    @Inject lateinit var triggerAxisKeyEmitter: com.nendo.argosy.ui.input.TriggerAxisKeyEmitter
    @Inject lateinit var playSessionTracker: PlaySessionTracker
    @Inject lateinit var preferencesRepository: UserPreferencesRepository
    @Inject lateinit var touchLayoutRepository: com.nendo.argosy.data.repository.TouchLayoutRepository
    @Inject lateinit var inputConfigRepository: InputConfigRepository
    @Inject lateinit var cheatDao: CheatDao
    @Inject lateinit var gameDao: GameDao
    @Inject lateinit var achievementDao: AchievementDao
    @Inject lateinit var cheatsRepository: CheatsRepository
    @Inject lateinit var raRepository: RetroAchievementsRepository
    @Inject lateinit var verifyRAGameIdUseCase: com.nendo.argosy.domain.usecase.achievement.VerifyRAGameIdUseCase
    @Inject lateinit var achievementUpdateBus: AchievementUpdateBus
    @Inject lateinit var saveCacheManager: SaveCacheManager
    @Inject lateinit var ambientLedManager: AmbientLedManager
    @Inject lateinit var socialRepository: SocialRepository
    @Inject lateinit var argosSocialService: ArgosSocialService
    @Inject lateinit var coreHashCache: CoreHashCache
    @Inject lateinit var effectiveLibretroSettingsResolver: EffectiveLibretroSettingsResolver
    @Inject lateinit var platformLibretroSettingsDao: com.nendo.argosy.data.local.dao.PlatformLibretroSettingsDao
    @Inject lateinit var frameRegistry: FrameRegistry
    @Inject lateinit var coreOptionsRepository: com.nendo.argosy.data.repository.CoreOptionsRepository

    private var coreLoadedSuccessfully = false
    @Volatile private var coreDestroyed = false
    private var autoSaveStateCaptured = false
    private lateinit var retroView: GLRetroView
    private val portResolver = ControllerPortResolver()
    private val inputMapper = ControllerInputMapper()
    private lateinit var inputConfig: InputConfigCoordinator
    private lateinit var hotkeyDispatcher: LibretroHotkeyDispatcher
    private lateinit var motionProcessor: MotionEventProcessor
    private var vibrator: Vibrator? = null
    private lateinit var romPath: String

    private lateinit var saveStateManager: SaveStateManager
    private lateinit var videoSettings: VideoSettingsManager
    private lateinit var cheatManager: CheatSessionManager
    private lateinit var achievementBridge: LibretroAchievementBridge

    private var gameId: Long = -1L
    private var platformId: Long = -1L
    private var platformSlug: String = ""
    private var coreName: String? = null
    private var activeSaveChannel: String? = null
    private var menuVisible by mutableStateOf(false)
    private var isClosing by mutableStateOf(false)
    private var cheatsMenuVisible by mutableStateOf(false)
    private var settingsVisible by mutableStateOf(false)
    private var shaderChainEditorVisible by mutableStateOf(false)
    private var frameEditorVisible by mutableStateOf(false)
    private var inGameShaderChainManager: ShaderChainManager? = null
    private var inGameFrameManager: FrameManager? = null
    private var capturedGameFrame: Bitmap? = null
    private var lastCheatsTab by mutableStateOf(CheatsTab.CHEATS)
    private var inGameMessage by mutableStateOf<String?>(null)
    private var gameName: String = ""
    private var canEnableBFI = false

    private var limitHotkeysToPlayer1 by mutableStateOf(true)
    private var firstFrameRendered = false
    private lateinit var audioController: LibretroAudioController
    private var swapAB by mutableStateOf(false)
    private var swapXY by mutableStateOf(false)
    private var swapStartSelect by mutableStateOf(false)
    private var menuFocusIndex by mutableStateOf(0)
    private lateinit var menuInputHandler: LibretroMenuInputHandler
    private lateinit var gamepadInputBridge: LibretroGamepadInputHandler
    private var activeMenuHandler: InputHandler? = null

    private var restoredSram: ByteArray? = null
    private var hardcoreMode by mutableStateOf(false)
    private var launchMode = LaunchMode.RESUME
    private var statesSupported = true
    private var autoSaveEnabled = true
    private var menuWrapMode = com.nendo.argosy.data.preferences.MenuWrapMode.HARD_STOP
    private var autoRestorePromptVisible by mutableStateOf(false)
    private var autoRestorePromptFocusIndex by mutableStateOf(0)

    private var stateManagerVisible by mutableStateOf(false)
    private var stateManagerFocusIndex by mutableStateOf(0)
    private var stateManagerViewMode by mutableStateOf(StateManagerViewMode.SPLIT)
    private var stateManagerShowDelete by mutableStateOf(false)
    private var stateManagerDeleteTarget by mutableStateOf(-1)
    private var stateManagerSlots by mutableStateOf<List<SaveStateManager.SlotInfo>>(emptyList())
    private var pendingSaveScreenshot: Bitmap? = null

    private lateinit var netplay: LibretroNetplayCoordinator
    private val isGuestJoinedSession: Boolean
        get() = if (::netplay.isInitialized) netplay.isGuestJoinedSession else false
    private var corePath: String = ""
    private var resolvedCoreId: String? = null

    private var isGamepadConnectedState by mutableStateOf(false)
    private var currentOrientationState by mutableStateOf(android.content.res.Configuration.ORIENTATION_LANDSCAPE)
    private var currentRotationState by mutableStateOf(0)
    private var touchSettingsState by mutableStateOf(com.nendo.argosy.data.preferences.BuiltinEmulatorSettings())
    private var coreOptionOverrides by mutableStateOf<Map<String, String>>(emptyMap())
    private var gameCoreOptionOverrides by mutableStateOf<Map<String, String>>(emptyMap())
    private var perGameSettingsEnabled by mutableStateOf(false)
    private var inputDeviceListener: android.hardware.input.InputManager.InputDeviceListener? = null
    private var splitColumn: android.widget.LinearLayout? = null
    private var touchEditMode by mutableStateOf(false)
    private var baselineRotation: Int = 0
    private var orientationEventListener: android.view.OrientationEventListener? = null

    private val isAnyMenuOpen: Boolean
        get() = menuVisible || cheatsMenuVisible || settingsVisible || shaderChainEditorVisible || frameEditorVisible || autoRestorePromptVisible || stateManagerVisible ||
            isClosing || netplay.isAnyDialogVisible

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate: savedInstanceState=${savedInstanceState != null}")
        enableEdgeToEdge()
        enterImmersiveMode()
        currentOrientationState = resources.configuration.orientation
        currentRotationState = windowManager.defaultDisplay.rotation
        baselineRotation = currentRotationState
        isGamepadConnectedState = com.nendo.argosy.core.input.ControllerDetector.isAnyGamepadConnected()
        registerGamepadDetection()
        registerOrientationListener()

        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isAnyMenuOpen) {
                    activeMenuHandler?.onBack()
                } else {
                    showMenu()
                }
            }
        })

        com.nendo.argosy.DualScreenManagerHolder.instance?.let { dsm ->
            dsm.emulatorKeyDispatcher = { event -> dispatchKeyEvent(event) }
            dsm.emulatorMotionDispatcher = { event -> dispatchGenericMotionEvent(event) }
        }

        netplay = LibretroNetplayCoordinator(
            activity = this,
            gameDao = gameDao,
            coreHashCache = coreHashCache,
            socialRepository = socialRepository,
            argosSocialService = argosSocialService,
            preferencesRepository = preferencesRepository,
            scope = lifecycleScope,
            showToast = { msg -> inGameMessage = msg },
            onFastForwardRelease = { hotkeyDispatcher.releaseFastForward() },
            getRetroView = { retroView },
            getResolvedCoreId = { resolvedCoreId },
            getCorePath = { corePath },
            getRomPath = { romPath },
            getGameId = { gameId },
            getGameName = { gameName },
            getRaSessionManager = { achievementBridge.sessionManager }
        )

        if (!parseIntentExtras()) return

        val romFile = File(romPath)
        if (!validateRomFile(romFile)) return

        val systemDir = intent.getStringExtra(EXTRA_SYSTEM_DIR)
            ?.let { File(it) }
            ?: AppPaths.libretroSystemDir(filesDir)
        systemDir.mkdirs()
        val savesDir: File
        val statesDir: File
        if (isGuestJoinedSession) {
            // Guest sessions operate on a throwaway cache directory so no
            // SRAM is loaded from the user's real saves and nothing written
            // during the session leaks back into their persistent store.
            val (_, guestSaves, guestStates) = netplay.createScratchDir(cacheDir)
            savesDir = guestSaves
            statesDir = guestStates
        } else {
            savesDir = (intent.getStringExtra(EXTRA_SAVES_DIR)?.let { File(it) }
                ?: AppPaths.libretroSavesDir(filesDir)).apply { mkdirs() }
            statesDir = (intent.getStringExtra(EXTRA_STATES_DIR)?.let { File(it) }
                ?: AppPaths.libretroStatesDir(filesDir)).apply { mkdirs() }
        }

        val game = kotlinx.coroutines.runBlocking { gameDao.getById(gameId) }
        platformId = game?.platformId ?: -1L
        platformSlug = intent.getStringExtra(EXTRA_PLATFORM_SLUG)?.takeIf { it.isNotBlank() }
            ?: game?.platformSlug ?: ""
        activeSaveChannel = game?.activeSaveChannel
        perGameSettingsEnabled = game?.perGameSettingsEnabled == true

        initializeSaveState(savesDir, statesDir, activeSaveChannel)
        val globalSettings = kotlinx.coroutines.runBlocking {
            preferencesRepository.getBuiltinEmulatorSettings().first()
        }
        touchSettingsState = globalSettings
        var lastLockOrientation = globalSettings.touchControlsLockOrientation
        var lastShow = globalSettings.showTouchControlsWhenNoGamepad
        lifecycleScope.launch {
            preferencesRepository.getBuiltinEmulatorSettings().collect {
                touchSettingsState = it
                if (it.touchControlsLockOrientation != lastLockOrientation) {
                    lastLockOrientation = it.touchControlsLockOrientation
                    applyOrientationLock(it.touchControlsLockOrientation)
                }
                if (it.showTouchControlsWhenNoGamepad != lastShow) {
                    lastShow = it.showTouchControlsWhenNoGamepad
                    splitColumn?.let { col -> applyPortraitSplit(col) }
                }
            }
        }
        applyOrientationLock(globalSettings.touchControlsLockOrientation)
        val settings = kotlinx.coroutines.runBlocking {
            effectiveLibretroSettingsResolver.getEffectiveSettings(platformId, platformSlug)
        }

        autoSaveEnabled = globalSettings.autoSaveState && !isGuestJoinedSession
        initializeInputHandlers()
        initializeVideoSettings(globalSettings, settings)
        detectBFICapability()

        corePath = intent.getStringExtra(EXTRA_CORE_PATH)!!
        resolvedCoreId = resolveCoreIdFromPath(corePath)
        loadCoreOptionOverrides()
        createRetroView(corePath, systemDir, savesDir, settings, restoredSram)
        achievementBridge = LibretroAchievementBridge(
            gameDao = gameDao,
            achievementDao = achievementDao,
            raRepository = raRepository,
            verifyRAGameIdUseCase = verifyRAGameIdUseCase,
            achievementUpdateBus = achievementUpdateBus,
            ambientLedManager = ambientLedManager,
            socialRepository = socialRepository,
            scope = lifecycleScope,
            context = this
        )
        netplay.start()
        setupRetroViewListeners()
        audioController = LibretroAudioController(this, getRetroView = { retroView })
        configureRetroView(settings)
        audioController.requestAudioFocus()
        inputConfig = InputConfigCoordinator(
            inputConfigRepository = inputConfigRepository,
            portResolver = portResolver,
            inputMapper = inputMapper,
            platformSlug = platformSlug,
            coreId = resolvedCoreId,
            limitHotkeysToPlayer1 = limitHotkeysToPlayer1,
            scope = lifecycleScope
        )
        inputConfig.initialize()

        if (settings.rumbleEnabled) {
            setupRumble()
        }
        if (videoSettings.rewindEnabled && !hardcoreMode) {
            setupRewind(settings)
        }

        initializeCheatManager()
        initializeHotkeyDispatcher()
        motionProcessor = MotionEventProcessor(
            inputMapper = inputMapper,
            portResolver = portResolver,
            videoSettings = videoSettings,
            getRetroView = { retroView }
        )

        buildContentView()

        if (gameId != -1L) {
            val isNewGame = launchMode == LaunchMode.NEW_CASUAL || launchMode == LaunchMode.NEW_HARDCORE
            playSessionTracker.startSession(gameId, EmulatorRegistry.BUILTIN_PACKAGE, coreName, hardcoreMode, isNewGame, isNetplayGuest = isGuestJoinedSession)
            cheatManager.loadCheats(hardcoreMode)
            achievementBridge.start(gameId, romPath, hardcoreMode, retroView)
        }
    }

    private fun parseIntentExtras(): Boolean {
        romPath = intent.getStringExtra(EXTRA_ROM_PATH) ?: run { finish(); return false }
        intent.getStringExtra(EXTRA_CORE_PATH) ?: run { finish(); return false }
        gameName = intent.getStringExtra(EXTRA_GAME_NAME) ?: File(romPath).nameWithoutExtension
        gameId = intent.getLongExtra(EXTRA_GAME_ID, -1L)
        coreName = intent.getStringExtra(EXTRA_CORE_NAME)
        launchMode = LaunchMode.fromString(intent.getStringExtra(LaunchMode.EXTRA_LAUNCH_MODE))
        hardcoreMode = launchMode.isHardcore

        val joinSessionId = intent.getStringExtra(EXTRA_NETPLAY_JOIN_SESSION_ID)
        val joinHostUserId = intent.getStringExtra(EXTRA_NETPLAY_JOIN_HOST_USER_ID)
        if (netplay.parseJoinIntent(joinSessionId, joinHostUserId)) {
            // Guests joining a netplay session do not fetch, create, or sync
            // local saves/states. The runtime state is bound to the host's
            // snapshot for the duration of the session. Force a fresh launch
            // so nothing tries to auto-restore.
            launchMode = LaunchMode.NEW_CASUAL
        }

        return true
    }

    private fun validateRomFile(romFile: File): Boolean {
        if (!romFile.exists()) {
            Log.e(TAG, "ROM file not found: $romPath")
            inGameMessage = "Game file not found"
            finish()
            return false
        }
        if (!romFile.canRead()) {
            Log.e(TAG, "ROM file not readable: $romPath")
            inGameMessage = "Cannot access game file"
            finish()
            return false
        }
        Log.d(TAG, "ROM validated: exists=${romFile.exists()}, size=${romFile.length()}, path=$romPath")
        return true
    }

    private fun initializeSaveState(savesDir: File, statesDir: File, channelName: String? = null) {
        saveStateManager = SaveStateManager(
            savesDir = savesDir,
            statesDir = statesDir,
            romPath = romPath,
            gameId = gameId,
            gameDao = gameDao,
            saveCacheManager = saveCacheManager,
            usesExternalMemcard = com.nendo.argosy.data.platform.PlatformDefinitions.getCanonicalSlug(platformSlug) == "gc",
            channelName = channelName
        )
        val restoreResult = kotlinx.coroutines.runBlocking {
            saveStateManager.restoreSaveForLaunchMode(launchMode)
        }
        restoredSram = restoreResult.sramData
        if (restoreResult.switchToHardcore) {
            hardcoreMode = true
        }
        saveStateManager.initializeFromExistingSave(restoreResult.sramData)
    }

    private fun initializeInputHandlers() {
        val inputPrefs = kotlinx.coroutines.runBlocking {
            preferencesRepository.preferences.first()
        }
        menuWrapMode = inputPrefs.menuWrapMode
        val detectedLayout = ControllerDetector.detectFromActiveGamepad().layout
        val isNintendoLayout = when (inputPrefs.controllerLayout) {
            "nintendo" -> true
            "xbox" -> false
            else -> detectedLayout == DetectedLayout.NINTENDO
        }
        swapAB = isNintendoLayout xor inputPrefs.swapAB
        swapXY = isNintendoLayout xor inputPrefs.swapXY
        swapStartSelect = inputPrefs.swapStartSelect
        menuInputHandler = LibretroMenuInputHandler(
            inputPrefs.swapAB,
            inputPrefs.swapXY,
            inputPrefs.swapStartSelect
        )
        gamepadInputBridge = LibretroGamepadInputHandler(
            menuInputHandler = menuInputHandler,
            getActiveHandler = { activeMenuHandler }
        )
    }

    private fun initializeVideoSettings(
        globalSettings: com.nendo.argosy.data.preferences.BuiltinEmulatorSettings,
        settings: com.nendo.argosy.data.preferences.BuiltinEmulatorSettings
    ) {
        videoSettings = VideoSettingsManager(
            platformId = platformId,
            platformSlug = platformSlug,
            globalSettings = globalSettings,
            platformLibretroSettingsDao = platformLibretroSettingsDao,
            effectiveLibretroSettingsResolver = effectiveLibretroSettingsResolver,
            preferencesRepository = preferencesRepository,
            frameRegistry = frameRegistry,
            scope = lifecycleScope,
            shaderRegistryProvider = { ShaderRegistry(this) },
            getRetroView = { retroView }
        )
        videoSettings.applySettings(settings)
        videoSettings.resolveCustomShader(settings)
        limitHotkeysToPlayer1 = settings.limitHotkeysToPlayer1
        videoSettings.onRewindToggled = { enabled ->
            if (enabled && !hardcoreMode) {
                setupRewind(settings)
            } else if (!enabled) {
                retroView.rewindEnabled = false
                retroView.destroyRewindBuffer()
            }
        }
        videoSettings.onRewindConfigChanged = {
            if (videoSettings.rewindEnabled && !hardcoreMode) {
                retroView.rewindEnabled = false
                retroView.destroyRewindBuffer()
                setupRewind(settings)
            }
        }
    }

    private fun detectBFICapability() {
        val displayManager = getSystemService(android.content.Context.DISPLAY_SERVICE) as android.hardware.display.DisplayManager
        val display = displayManager.getDisplay(android.view.Display.DEFAULT_DISPLAY)
        canEnableBFI = (display?.supportedModes?.maxOfOrNull { it.refreshRate } ?: 60f) >= 120f
    }

    private fun createRetroView(
        corePath: String,
        systemDir: File,
        savesDir: File,
        settings: com.nendo.argosy.data.preferences.BuiltinEmulatorSettings,
        existingSram: ByteArray?
    ) {
        val effectiveShader = if (settings.shader == "Custom") videoSettings.resolvedCustomShader else settings.shaderConfig
        Log.d(TAG, "[Startup] Creating GLRetroView: core=$coreName, effectiveShader=${effectiveShader::class.simpleName}")
        retroView = GLRetroView(
            this,
            GLRetroViewData(this).apply {
                coreFilePath = corePath
                gameFilePath = romPath
                systemDirectory = systemDir.absolutePath
                savesDirectory = savesDir.absolutePath
                saveRAMState = existingSram
                shader = effectiveShader
                skipDuplicateFrames = if (coreName == "dolphin") false else settings.skipDuplicateFrames
                preferLowLatencyAudio = settings.lowLatencyAudio
                forceSoftwareTiming = settings.forceSoftwareTiming
                rumbleEventsEnabled = settings.rumbleEnabled
                variables = coreVariablesFromIntent()
            }
        )
        lifecycle.addObserver(retroView)
    }

    private fun setupRetroViewListeners() {
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
                Log.e(TAG, "GLRetroView error: code=$errorCode, message=$errorMessage")
                Log.e(TAG, "Context: gameId=$gameId, core=$coreName, rom=$romPath")
                inGameMessage = errorMessage
                finish()
            }
        }

        lifecycleScope.launch {
            retroView.getGLRetroEvents().collect { event ->
                when (event) {
                    is GLRetroView.GLRetroEvents.SurfaceCreated -> {
                        coreLoadedSuccessfully = true
                        Log.i(TAG, "[Startup] GL surface created - render pipeline ready")
                        videoSettings.currentFrame?.let { frameId ->
                            val bitmap = frameRegistry.loadFrame(frameId)
                            if (bitmap != null) {
                                Log.i(TAG, "[Startup] Setting initial frame: $frameId (${bitmap.width}x${bitmap.height})")
                                retroView.setBackgroundFrame(bitmap)
                            }
                        }
                        videoSettings.applyAspectRatio()
                        videoSettings.applyOverscanCrop()
                        videoSettings.applyRotation()
                    }
                    is GLRetroView.GLRetroEvents.FrameRendered -> {
                        if (!firstFrameRendered) {
                            firstFrameRendered = true
                            Log.i(TAG, "[Startup] First frame rendered - emulation running successfully")
                            Log.d(TAG, "[Startup] gameId=$gameId, core=$coreName, hardcore=$hardcoreMode")
                            checkStateSupport()
                            attemptAutoRestore()
                            netplay.triggerPendingNetplayJoin()
                        }
                    }
                }
            }
        }
    }

    private fun configureRetroView(settings: com.nendo.argosy.data.preferences.BuiltinEmulatorSettings) {
        audioController.applyInitialAudioConfig(settings.fastForwardPreservePitch)
        retroView.filterMode = settings.filterMode
        retroView.blackFrameInsertion = settings.blackFrameInsertion
        retroView.portResolver = portResolver
        retroView.keyMapper = inputMapper
    }

    private fun initializeCheatManager() {
        cheatManager = CheatSessionManager(
            gameId = gameId,
            cheatDao = cheatDao,
            gameDao = gameDao,
            cheatsRepository = cheatsRepository,
            scope = lifecycleScope
        )
        cheatManager.setRetroView(retroView)
        netplay.attachCheatSessionManager(cheatManager)
    }

    private fun initializeHotkeyDispatcher() {
        hotkeyDispatcher = LibretroHotkeyDispatcher(
            hotkeyManager = inputConfig.hotkeyManager,
            saveStateManager = saveStateManager,
            videoSettings = videoSettings,
            getRetroView = { retroView },
            showToast = { msg -> inGameMessage = msg },
            isHardcoreMode = { hardcoreMode },
            isNetplayInSession = { netplay.inSession },
            getNetplayRole = { netplay.role },
            onShowMenu = ::showMenu,
            onAutoSaveState = ::performAutoSaveState,
            onCycleCoreOption = { key, direction, values -> cycleCoreOption(key, direction, values) },
            onSendCoreInput = ::sendCoreInput,
            onQuit = ::finish
        )
    }

    private fun buildContentView() {
        val splitColumn = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            addView(
                retroView,
                android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                    0,
                    1f
                )
            )
            addView(
                android.widget.Space(this@LibretroActivity),
                android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                    0,
                    0f
                )
            )
        }
        val container = FrameLayout(this).apply {
            addView(splitColumn, FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            addView(
                ComposeView(this@LibretroActivity).apply {
                    setContent { InGameOverlay() }
                },
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        setContentView(container)
        this.splitColumn = splitColumn
        applyPortraitSplit(splitColumn)

        container.post {
            videoSettings.setScreenSize(container.width, container.height)
            Log.d(TAG, "Container size: ${container.width}x${container.height}, aspectRatioMode: ${videoSettings.aspectRatioMode}")
            if (coreLoadedSuccessfully) {
                videoSettings.applyAspectRatio()
                videoSettings.applyOverscanCrop()
                videoSettings.applyRotation()
            }
        }
    }

    private fun applyPortraitSplit(column: android.widget.LinearLayout) {
        val portrait = currentOrientationState == android.content.res.Configuration.ORIENTATION_PORTRAIT
        val overlayWouldShow = touchSettingsState.showTouchControlsWhenNoGamepad && !isGamepadConnectedState
        val splitWanted = portrait && overlayWouldShow
        val retroParams = retroView.layoutParams as android.widget.LinearLayout.LayoutParams
        val spacer = column.getChildAt(1)
        val spacerParams = spacer.layoutParams as android.widget.LinearLayout.LayoutParams
        retroParams.weight = 1f
        spacerParams.weight = if (splitWanted) 1f else 0f
        retroView.layoutParams = retroParams
        spacer.layoutParams = spacerParams
        column.requestLayout()
    }


    @androidx.compose.runtime.Composable
    private fun InGameOverlay() {
        ALauncherTheme {
            CompositionLocalProvider(
                LocalGamepadInputHandler provides gamepadInputBridge,
                LocalABIconsSwapped provides swapAB,
                LocalXYIconsSwapped provides swapXY,
                LocalSwapStartSelect provides swapStartSelect
            ) {
                com.nendo.argosy.libretro.touch.OnScreenControlsHost(
                    retroView = retroView,
                    platformSlug = platformSlug,
                    orientation = currentOrientationState,
                    isGamepadConnected = isGamepadConnectedState,
                    settings = touchSettingsState,
                    rotationKey = currentRotationState,
                    baselineRotation = baselineRotation,
                    editMode = touchEditMode,
                    repository = touchLayoutRepository,
                    onExitEdit = { exitTouchEditMode() },
                    onKey = { action, kc -> dispatchTouchKey(action, kc) }
                )
                if (menuVisible) {
                    val quality = if (netplay.inSession && netplay.role != null) {
                        NetplayQualityInfo(
                            peerDisplayName = netplay.peerDisplayName,
                            role = netplay.role!!,
                            pingMs = netplay.lastRttMs,
                            label = NetplayQualityInfo.labelForRttMs(netplay.lastRttMs)
                        )
                    } else null
                    activeMenuHandler = InGameMenu(
                        gameName = gameName,
                        coreName = resolvedCoreId?.let { id ->
                            LibretroCoreRegistry.getCoreById(id)?.displayName
                                ?: com.nendo.argosy.data.emulator.EmulatorRegistry
                                    .getCoresForPlatform(platformSlug).firstOrNull { it.id == id }?.displayName
                                ?: id
                        },
                        cheatsAvailable = !hardcoreMode && PlatformWeightRegistry.supportsCheats(platformSlug),
                        statesSupported = statesSupported && !hardcoreMode,
                        focusedIndex = menuFocusIndex,
                        onFocusChange = { menuFocusIndex = it },
                        onAction = ::handleMenuAction,
                        isHardcoreMode = hardcoreMode,
                        netplaySupported = netplay.isCoreSupported(),
                        isInNetplaySession = netplay.inSession,
                        netplayRole = netplay.role,
                        netplaySessionIsReserved = netplay.sessionIsReserved,
                        netplayQuality = quality,
                        touchControlsVisible = touchSettingsState.showTouchControlsWhenNoGamepad && !isGamepadConnectedState
                    )
                }
                if (netplay.modePickerVisible) {
                    activeMenuHandler = NetplayModePickerDialog(
                        focusedIndex = netplay.modePickerFocus,
                        onFocusChange = { netplay.modePickerFocus = it },
                        onSelectOpen = {
                            netplay.modePickerVisible = false
                            netplay.handleOpenWithMode(NetplaySessionMode.OPEN)
                        },
                        onSelectPrivate = {
                            netplay.modePickerVisible = false
                            netplay.handleOpenWithMode(NetplaySessionMode.PRIVATE)
                        },
                        onSelectInvite = {
                            netplay.modePickerVisible = false
                            netplay.handleInviteFriend()
                        },
                        onDismiss = { netplay.modePickerVisible = false }
                    )
                }
                if (netplay.friendPickerVisible) {
                    activeMenuHandler = NetplayFriendPickerDialog(
                        friends = netplay.friendPickerEntries,
                        focusedIndex = netplay.friendPickerFocus,
                        onFocusChange = { netplay.friendPickerFocus = it },
                        onSelect = netplay::onFriendPicked,
                        onDismiss = { netplay.friendPickerVisible = false }
                    )
                }
                if (netplay.joinRequestVisible) {
                    activeMenuHandler = NetplayJoinRequestDialog(
                        username = netplay.joinRequestUsername,
                        focusedIndex = netplay.joinRequestFocus,
                        onFocusChange = { netplay.joinRequestFocus = it },
                        onAccept = netplay::handleJoinRequestAccept,
                        onDecline = netplay::handleJoinRequestDecline
                    )
                }
                if (netplay.disconnectPromptVisible) {
                    activeMenuHandler = NetplayHostDisconnectPrompt(
                        peerDisplayName = netplay.disconnectPromptPeer,
                        focusedIndex = netplay.disconnectPromptFocus,
                        onFocusChange = { netplay.disconnectPromptFocus = it },
                        onKeepOpen = netplay::handleKeepSession,
                        onCloseAndEnd = netplay::handleCloseAfterDisconnect
                    )
                }
                if (netplay.qualityWarningVisible) {
                    activeMenuHandler = NetplayQualityWarningPrompt(
                        rttMs = netplay.qualityWarningRttMs,
                        jitterMs = netplay.qualityWarningJitterMs,
                        ratingLabel = netplay.qualityWarningLabel,
                        focusedIndex = netplay.qualityWarningFocus,
                        onFocusChange = { netplay.qualityWarningFocus = it },
                        onAccept = netplay::handleQualityAccept,
                        onDecline = netplay::handleQualityDecline
                    )
                }
                netplay.progressState?.let { progress ->
                    activeMenuHandler = NetplayConnectionProgressOverlay(
                        state = progress,
                        onDismiss = { netplay.progressState = null }
                    )
                }
                if (netplay.reconnecting) {
                    NetplayReconnectingOverlay(lastRttMs = netplay.lastRttMs)
                }
                if (cheatsMenuVisible) {
                    activeMenuHandler = CheatsScreen(
                        cheats = cheatManager.cheats.map { CheatDisplayItem(it.id, it.description, it.code, it.enabled, it.isUserCreated, it.lastUsedAt) },
                        variants = cheatManager.variants.map { CheatVariantInfo(it.variantRegion, it.variantVersion, it.cheatCount) },
                        selectedVariant = cheatManager.selectedVariant,
                        scanner = cheatManager.memoryScanner,
                        initialTab = lastCheatsTab,
                        onToggleCheat = cheatManager::handleToggleCheat,
                        onCreateCheat = cheatManager::handleCreateCheat,
                        onUpdateCheat = cheatManager::handleUpdateCheat,
                        onDeleteCheat = cheatManager::handleDeleteCheat,
                        onSelectVariant = { region, version ->
                            cheatManager.selectVariant(region, version, hardcoreMode)
                        },
                        onGetRam = { retroView.getSystemRam() },
                        onTabChange = { lastCheatsTab = it },
                        onDismiss = {
                            cheatsMenuVisible = false
                            menuVisible = true
                            cheatManager.memoryScanner.markGameRan()
                            cheatManager.flushCheatReset()
                        }
                    )
                }
                if (settingsVisible) {
                    activeMenuHandler = buildSettingsScreen()
                }
                if (shaderChainEditorVisible) {
                    val manager = inGameShaderChainManager
                    if (manager != null) {
                        activeMenuHandler = InGameShaderChainScreen(
                            manager = manager,
                            onDismiss = ::closeInGameShaderChainEditor
                        )
                    }
                }
                if (frameEditorVisible) {
                    val manager = inGameFrameManager
                    if (manager != null) {
                        activeMenuHandler = InGameFrameScreen(
                            manager = manager,
                            isOffline = false,
                            onConfirm = ::confirmInGameFrameEditor,
                            onDismiss = ::closeInGameFrameEditor
                        )
                    }
                }
                if (autoRestorePromptVisible) {
                    activeMenuHandler = AutoRestorePrompt(
                        focusedIndex = autoRestorePromptFocusIndex,
                        onFocusChange = { autoRestorePromptFocusIndex = it },
                        onRestore = { handleAutoRestoreResponse(true) },
                        onSkip = { handleAutoRestoreResponse(false) }
                    )
                }
                if (stateManagerVisible) {
                    activeMenuHandler = InGameStateManager(
                        slots = stateManagerSlots,
                        channelName = activeSaveChannel,
                        focusedIndex = stateManagerFocusIndex,
                        viewMode = stateManagerViewMode,
                        showDeleteConfirmation = stateManagerShowDelete,
                        onFocusChange = { stateManagerFocusIndex = it },
                        onViewModeToggle = {
                            stateManagerViewMode = when (stateManagerViewMode) {
                                StateManagerViewMode.SPLIT -> StateManagerViewMode.CAROUSEL
                                StateManagerViewMode.CAROUSEL -> StateManagerViewMode.SPLIT
                            }
                        },
                        onSave = ::handleStateManagerSave,
                        onLoad = ::handleStateManagerLoad,
                        onDeleteRequest = { slot ->
                            stateManagerDeleteTarget = slot
                            stateManagerShowDelete = true
                        },
                        onDeleteConfirm = {
                            if (stateManagerDeleteTarget >= SaveStateManager.AUTO_SLOT) {
                                saveStateManager.deleteSlot(stateManagerDeleteTarget)
                                stateManagerSlots = saveStateManager.getSlotInfoList()
                            }
                            stateManagerShowDelete = false
                            stateManagerDeleteTarget = -1
                        },
                        onDeleteCancel = {
                            stateManagerShowDelete = false
                            stateManagerDeleteTarget = -1
                        },
                        onDismiss = ::dismissStateManager,
                        loadAllowed = !netplay.inSession
                    )
                }
                if (!isAnyMenuOpen) {
                    activeMenuHandler = null
                }
                androidx.compose.runtime.LaunchedEffect(isAnyMenuOpen) {
                    if (!isAnyMenuOpen && !netplay.inSession) {
                        retroView.suppressAutoResume = false
                        retroView.resumeEmulation()
                    }
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    if (netplay.inSession && !menuVisible) {
                        val playerCount = if (netplay.peerConnected) 2 else 1
                        val isLocalHost = netplay.role == NetplayMenuRole.Host
                        NetplayBorderHud(
                            gameTitle = gameName,
                            sessionMode = netplay.hudSessionMode,
                            playerCount = playerCount,
                            averagePingMs = netplay.hudAveragePingMs,
                            hostUsername = if (isLocalHost) netplay.hudHostUsername else netplay.peerDisplayName,
                            guestUsername = if (netplay.peerConnected) {
                                if (isLocalHost) netplay.peerDisplayName else netplay.hudHostUsername
                            } else null,
                            hostAvatarColor = if (isLocalHost) netplay.hudHostAvatarColor else netplay.hudGuestAvatarColor,
                            guestAvatarColor = if (isLocalHost) netplay.hudGuestAvatarColor else netplay.hudHostAvatarColor,
                            observers = emptyList(),
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(start = 4.dp, top = 4.dp)
                        )
                    }
                    AchievementPopup(
                        achievement = achievementBridge.currentUnlock,
                        onDismiss = { achievementBridge.showNextUnlock() },
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .statusBarsPadding()
                    )
                    RAConnectionNotification(
                        connectionInfo = achievementBridge.connectionInfo,
                        onDismiss = { achievementBridge.dismissConnectionInfo() },
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .statusBarsPadding()
                    )
                    InGameMessageOverlay(
                        message = inGameMessage,
                        onDismiss = { inGameMessage = null },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 24.dp)
                    )
                }

                if (isClosing) {
                    ClosingSaveOverlay()
                }
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun ClosingSaveOverlay() {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.85f))
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) { awaitPointerEvent().changes.forEach { it.consume() } }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                Text(
                    text = "Validating save contents",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White
                )
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun InGameMessageOverlay(
        message: String?,
        onDismiss: () -> Unit,
        modifier: Modifier = Modifier
    ) {
        androidx.compose.animation.AnimatedVisibility(
            visible = message != null,
            enter = androidx.compose.animation.fadeIn(
                animationSpec = androidx.compose.animation.core.tween(150)
            ),
            exit = androidx.compose.animation.fadeOut(
                animationSpec = androidx.compose.animation.core.tween(150)
            ),
            modifier = modifier
        ) {
            message?.let { msg ->
                LaunchedEffect(msg) {
                    kotlinx.coroutines.delay(2000)
                    onDismiss()
                }
                Text(
                    text = msg,
                    color = Color.White,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .background(
                            Color.Black.copy(alpha = 0.7f),
                            RoundedCornerShape(6.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }

    private fun loadCoreOptionOverrides() {
        val coreId = resolvedCoreId ?: return
        if (CoreOptionManifestRegistry.getManifest(coreId) == null) return
        lifecycleScope.launch {
            val (global, perGame) = withContext(Dispatchers.IO) {
                val g = coreOptionsRepository.getOverridesForCore(coreId)
                    .associate { it.optionKey to it.value }
                val pg = if (gameId != -1L) {
                    coreOptionsRepository.getOverridesForGame(gameId, coreId)
                        .associate { it.optionKey to it.value }
                } else {
                    emptyMap()
                }
                g to pg
            }
            coreOptionOverrides = global
            gameCoreOptionOverrides = perGame
        }
    }

    private fun buildCoreOptionItems(): List<CoreOptionViewItem> {
        val coreId = resolvedCoreId ?: return emptyList()
        val manifest = CoreOptionManifestRegistry.getManifest(coreId) ?: return emptyList()
        val perGame = perGameSettingsEnabled && gameId != -1L
        return manifest.options.map { def ->
            val gameOverride = if (perGame) gameCoreOptionOverrides[def.key] else null
            val globalOverride = coreOptionOverrides[def.key]
            CoreOptionViewItem(
                key = def.key,
                displayName = def.displayName,
                description = def.description,
                values = def.values,
                currentValue = gameOverride ?: globalOverride ?: def.defaultValue,
                isOverridden = if (perGame) gameOverride != null else globalOverride != null,
                valueLabels = def.valueLabels
            )
        }
    }

    private fun cycleCoreOption(optionKey: String, direction: Int) =
        cycleCoreOption(optionKey, direction, emptyList())

    private fun cycleCoreOption(optionKey: String, direction: Int, explicitValues: List<String>) {
        val coreId = resolvedCoreId ?: return
        val def = CoreOptionManifestRegistry.getManifest(coreId)
            ?.options?.firstOrNull { it.key == optionKey } ?: return
        val rotation = explicitValues.filter { it in def.values }.takeIf { it.isNotEmpty() } ?: def.values
        if (rotation.isEmpty()) return
        val perGame = perGameSettingsEnabled && gameId != -1L
        val current = (if (perGame) gameCoreOptionOverrides[optionKey] else null)
            ?: coreOptionOverrides[optionKey] ?: def.defaultValue
        val currentIndex = rotation.indexOf(current).coerceAtLeast(0)
        val newValue = rotation[(currentIndex + direction).mod(rotation.size)]
        if (perGame) {
            gameCoreOptionOverrides = gameCoreOptionOverrides + (optionKey to newValue)
        } else {
            coreOptionOverrides = coreOptionOverrides + (optionKey to newValue)
        }
        if (::retroView.isInitialized) retroView.updateVariables(Variable(optionKey, newValue))
        lifecycleScope.launch(Dispatchers.IO) {
            if (perGame) {
                coreOptionsRepository.upsertForGame(
                    GameCoreOptionOverrideEntity(gameId, coreId, optionKey, newValue)
                )
            } else {
                coreOptionsRepository.upsert(CoreOptionOverrideEntity(coreId, optionKey, newValue))
            }
        }
    }

    private fun sendCoreInput(retropadId: Int, @Suppress("UNUSED_PARAMETER") mode: CoreInputMode) {
        if (!::retroView.isInitialized) return
        val keyCode = ControllerInputMapper.retroButtonToAndroidKeyCode(retropadId)
        retroView.sendKeyEvent(KeyEvent.ACTION_DOWN, keyCode, 0)
        lifecycleScope.launch {
            delay(CORE_INPUT_PULSE_MS)
            retroView.sendKeyEvent(KeyEvent.ACTION_UP, keyCode, 0)
        }
    }

    private fun resetCoreOption(optionKey: String) {
        val coreId = resolvedCoreId ?: return
        val def = CoreOptionManifestRegistry.getManifest(coreId)
            ?.options?.firstOrNull { it.key == optionKey } ?: return
        val perGame = perGameSettingsEnabled && gameId != -1L
        if (perGame) {
            gameCoreOptionOverrides = gameCoreOptionOverrides - optionKey
            val fallback = coreOptionOverrides[optionKey] ?: def.defaultValue
            if (::retroView.isInitialized) retroView.updateVariables(Variable(optionKey, fallback))
            lifecycleScope.launch(Dispatchers.IO) {
                coreOptionsRepository.deleteForGame(gameId, coreId, optionKey)
            }
        } else {
            coreOptionOverrides = coreOptionOverrides - optionKey
            if (::retroView.isInitialized) retroView.updateVariables(Variable(optionKey, def.defaultValue))
            lifecycleScope.launch(Dispatchers.IO) {
                coreOptionsRepository.delete(coreId, optionKey)
            }
        }
    }

    private fun applyPerGameSettingsToggle(enabled: Boolean) {
        if (gameId == -1L) return
        perGameSettingsEnabled = enabled
        val coreId = resolvedCoreId
        val manifest = coreId?.let { CoreOptionManifestRegistry.getManifest(it) }
        if (manifest != null && ::retroView.isInitialized) {
            manifest.options.forEach { def ->
                val gameOverride = if (enabled) gameCoreOptionOverrides[def.key] else null
                val effective = gameOverride ?: coreOptionOverrides[def.key] ?: def.defaultValue
                retroView.updateVariables(Variable(def.key, effective))
            }
        }
        lifecycleScope.launch(Dispatchers.IO) {
            gameDao.setPerGameSettingsEnabled(gameId, enabled)
        }
    }

    @androidx.compose.runtime.Composable
    private fun buildSettingsScreen(): InputHandler {
        return InGameSettingsScreen(
            accessor = InGameLibretroSettingsAccessor(
                getCurrentValue = videoSettings::getVideoSettingValue,
                globalValue = videoSettings::getGlobalVideoSettingValue,
                onCycle = videoSettings::cycleVideoSetting,
                onToggle = videoSettings::toggleVideoSetting,
                onReset = videoSettings::resetVideoSetting,
                onActionCallback = { setting ->
                    if (setting.key == "filter" && videoSettings.currentShader == "Custom") {
                        settingsVisible = false
                        openInGameShaderChainEditor()
                    } else if (setting.key == "frame") {
                        settingsVisible = false
                        openInGameFrameEditor()
                    }
                }
            ),
            platformSlug = platformSlug,
            canEnableBFI = canEnableBFI,
            menuWrapMode = menuWrapMode,
            controlsState = InGameControlsState(
                rumbleEnabled = videoSettings.currentRumbleEnabled,
                analogAsDpad = videoSettings.currentAnalogAsDpad,
                dpadAsAnalog = videoSettings.currentDpadAsAnalog,
                limitHotkeysToPlayer1 = limitHotkeysToPlayer1,
                fastForwardMode = videoSettings.fastForwardMode,
                fastForwardPreservePitch = videoSettings.fastForwardPreservePitch,
                controllerOrderCount = inputConfig.controllerOrderCount,
                touchEnabled = touchSettingsState.showTouchControlsWhenNoGamepad,
                touchOpacityLandscape = touchSettingsState.touchControlsOpacityLandscape,
                touchOpacityPortrait = touchSettingsState.touchControlsOpacityPortrait,
                touchSizeScale = touchSettingsState.touchControlsSizeScale,
                touchHaptic = touchSettingsState.touchControlsHaptic,
                touchLockOrientation = touchSettingsState.touchControlsLockOrientation,
                touchGenesis6Button = touchSettingsState.touchControlsGenesis6Button
            ),
            onControlsAction = ::handleControlsAction,
            coreOptions = buildCoreOptionItems(),
            coreOptionsSupported = resolvedCoreId?.let {
                CoreOptionManifestRegistry.getManifest(it) != null
            } ?: false,
            onCoreOptionCycle = ::cycleCoreOption,
            onCoreOptionReset = ::resetCoreOption,
            perGameSettingsSupported = gameId != -1L && resolvedCoreId?.let {
                CoreOptionManifestRegistry.getManifest(it) != null
            } ?: false,
            perGameSettingsEnabled = perGameSettingsEnabled,
            onTogglePerGameSettings = ::applyPerGameSettingsToggle,
            modalCallbacks = buildModalCallbacks(),
            onDismiss = {
                settingsVisible = false
                menuVisible = true
            }
        )
    }

    private fun buildModalCallbacks(): InGameModalCallbacks {
        val repo = inputConfig.inputConfigRepository
        return InGameModalCallbacks(
            controllerOrder = inputConfig.controllerOrderList,
            hotkeys = inputConfig.hotkeyList,
            connectedControllers = repo.getConnectedControllers(),
            onAssignController = { port, device ->
                lifecycleScope.launch {
                    repo.assignControllerToPort(port, device)
                    inputConfig.refreshControllerOrder()
                }
            },
            onClearControllerOrder = {
                lifecycleScope.launch {
                    repo.clearControllerOrder()
                    inputConfig.refreshControllerOrder()
                }
            },
            onGetMapping = { controller, mappingPlatformId ->
                val device = InputDevice.getDevice(controller.deviceId)
                if (device != null) {
                    repo.getOrCreateExtendedMappingForDevice(device, mappingPlatformId) to null
                } else {
                    emptyMap<InputSource, Int>() to null
                }
            },
            onSaveMapping = { controller, mapping, presetName, isAutoDetected, mappingPlatformId ->
                val device = InputDevice.getDevice(controller.deviceId)
                if (device != null) {
                    repo.saveExtendedMapping(device, mapping, presetName, isAutoDetected, mappingPlatformId)
                    inputConfig.refreshInputMappings()
                }
            },
            onApplyPreset = { controller, presetName ->
                val device = InputDevice.getDevice(controller.deviceId)
                if (device != null) {
                    repo.applyPreset(device, presetName)
                    inputConfig.refreshInputMappings()
                }
            },
            onSaveHotkey = { action, keyCodes ->
                repo.setHotkey(action, keyCodes)
                inputConfig.refreshHotkeys()
            },
            onClearHotkey = { action ->
                repo.deleteHotkey(action)
                inputConfig.refreshHotkeys()
            },
            onSetHotkeyHoldMs = { action, holdMs ->
                repo.setHotkeyHoldMs(action, holdMs)
                inputConfig.refreshHotkeys()
            },
            coreId = resolvedCoreId,
            coreName = resolvedCoreId?.let { LibretroCoreRegistry.getCoreById(it)?.displayName },
            coreControls = resolvedCoreId?.let { CoreControlManifestRegistry.getManifest(it)?.controls } ?: emptyList(),
            onSaveCoreControl = { retropadId, mode, keyCodes ->
                resolvedCoreId?.let { coreId ->
                    repo.setCoreControlHotkey(
                        id = null,
                        keyCodes = keyCodes,
                        retropadId = retropadId,
                        mode = mode,
                        coreId = coreId
                    )
                    inputConfig.refreshHotkeys()
                }
            },
            onClearCoreBind = { id ->
                repo.deleteHotkeyById(id)
                inputConfig.refreshHotkeys()
            }
        )
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

    private fun setupRewind(settings: BuiltinEmulatorSettings) {
        lifecycleScope.launch {
            retroView.getGLRetroEvents().collect { event ->
                if (event is GLRetroView.GLRetroEvents.SurfaceCreated) {
                    val fps = LibretroDroid.getContentFps()
                    val slotCount = (settings.rewindBufferDuration * fps).toInt()
                    val maxStateSize = 4 * 1024 * 1024
                    retroView.initRewindBuffer(slotCount, maxStateSize)
                    retroView.rewindEnabled = true
                    retroView.rewindSpeed = settings.rewindSpeed
                    Log.d(TAG, "Rewind buffer initialized: $slotCount slots (${settings.rewindBufferDuration}s), speed=${settings.rewindSpeed}x")
                    cheatManager.applyAllEnabledCheats(hardcoreMode)
                }
            }
        }
    }

    private fun handleMenuAction(action: InGameMenuAction) {
        when (action) {
            InGameMenuAction.Resume -> hideMenu()
            InGameMenuAction.QuickSave -> {
                val stateData = try { retroView.serializeState() } catch (_: Exception) { null }
                inGameMessage = if (stateData != null && saveStateManager.performQuickSave(stateData, pendingSaveScreenshot)) {
                    "State saved"
                } else {
                    "Failed to save state"
                }
                hideMenu()
            }
            InGameMenuAction.QuickLoad -> {
                inGameMessage = if (saveStateManager.performQuickLoad(retroView)) {
                    "State loaded"
                } else {
                    "Failed to load state"
                }
                hideMenu()
            }
            InGameMenuAction.ManageStates -> {
                menuVisible = false
                stateManagerSlots = saveStateManager.getSlotInfoList()
                stateManagerFocusIndex = 0
                stateManagerShowDelete = false
                stateManagerDeleteTarget = -1
                stateManagerVisible = true
            }
            InGameMenuAction.Settings -> {
                menuVisible = false
                settingsVisible = true
            }
            InGameMenuAction.Cheats -> {
                menuVisible = false
                cheatsMenuVisible = true
            }
            InGameMenuAction.CustomizeTouchControls -> {
                enterTouchEditMode()
            }
            InGameMenuAction.Quit -> {
                menuVisible = false
                isClosing = true
                lifecycleScope.launch {
                    try {
                        withContext(kotlinx.coroutines.Dispatchers.IO + kotlinx.coroutines.NonCancellable) {
                            performAutoSaveState()
                            try {
                                playSessionTracker.cacheCurrentSessionForQuit()
                            } catch (e: Exception) {
                                Log.w(TAG, "Pre-quit save cache failed", e)
                            }
                            try {
                                playSessionTracker.cacheCurrentSessionStatesForQuit()
                            } catch (e: Exception) {
                                Log.w(TAG, "Pre-quit state cache failed", e)
                            }
                            if (!isGuestJoinedSession) {
                                saveStateManager.saveSram(retroView)
                            }
                            coreDestroyed = true
                            retroView.destroyNative()
                        }
                    } finally {
                        finish()
                    }
                }
            }
            InGameMenuAction.OpenToFriends -> {
                hideMenu()
                netplay.modePickerFocus = 0
                netplay.modePickerVisible = true
            }
            InGameMenuAction.InviteFriend -> {
                hideMenu()
                netplay.handleInviteFriend()
            }
            InGameMenuAction.ClearReservation -> {
                hideMenu()
                netplay.handleClearReservation()
            }
            InGameMenuAction.CloseNetplaySession -> {
                hideMenu()
                netplay.handleCloseSession()
            }
        }
    }

    private fun resolveCoreIdFromPath(path: String): String? {
        val fileName = File(path).name
        val known = LibretroCoreRegistry.getAllCores().firstOrNull { it.fileName == fileName }
        if (known != null) return known.coreId
        return fileName.removeSuffix("_libretro_android.so")
            .removeSuffix("_libretro.so")
            .takeIf { it.isNotBlank() }
    }

    private fun performAutoSaveState() {
        if (isGuestJoinedSession) return
        if (coreDestroyed || hardcoreMode || !coreLoadedSuccessfully || !statesSupported || !autoSaveEnabled) return
        if (autoSaveStateCaptured) return
        try {
            val stateData = retroView.serializeState()
            autoSaveStateCaptured = true
            saveStateManager.performSlotSave(SaveStateManager.AUTO_SLOT, stateData, pendingSaveScreenshot)
            Log.d(TAG, "Auto-saved state on close")
        } catch (e: Exception) {
            Log.w(TAG, "Auto-save state failed", e)
        }
    }

    private fun checkStateSupport() {
        if (hardcoreMode) {
            statesSupported = false
            return
        }
        if (com.nendo.argosy.data.platform.PlatformDefinitions.getCanonicalSlug(platformSlug) in PLATFORMS_WITHOUT_STATE_SUPPORT) {
            statesSupported = false
            Log.d(TAG, "State support disabled for platform=$platformSlug")
            return
        }
        try {
            statesSupported = retroView.getSerializeSize() > 0
        } catch (_: Exception) {
            statesSupported = false
        }
        Log.d(TAG, "State support check: statesSupported=$statesSupported")
    }

    private fun handleStateManagerSave(slotNumber: Int) {
        val stateData = try { retroView.serializeState() } catch (_: Exception) { null }
        if (stateData != null) {
            val saved = saveStateManager.performSlotSave(slotNumber, stateData, pendingSaveScreenshot)
            inGameMessage = if (saved) "State saved to ${if (slotNumber == SaveStateManager.AUTO_SLOT) "Auto" else "Slot $slotNumber"}" else "Failed to save state"
        } else {
            inGameMessage = "Failed to serialize state"
        }
        stateManagerSlots = saveStateManager.getSlotInfoList()
    }

    private fun handleStateManagerLoad(slotNumber: Int) {
        val loaded = saveStateManager.performSlotLoad(retroView, slotNumber)
        if (loaded) {
            inGameMessage = "State loaded from ${if (slotNumber == SaveStateManager.AUTO_SLOT) "Auto" else "Slot $slotNumber"}"
            dismissStateManager()
        } else {
            inGameMessage = "Failed to load state"
        }
    }

    private fun dismissStateManager() {
        stateManagerVisible = false
        stateManagerShowDelete = false
        stateManagerDeleteTarget = -1
        pendingSaveScreenshot?.recycle()
        pendingSaveScreenshot = null
        if (!netplay.inSession) {
            retroView.suppressAutoResume = false
            retroView.resumeEmulation()
        }
    }

    private fun attemptAutoRestore() {
        if (isGuestJoinedSession) return
        if (launchMode != LaunchMode.RESUME || hardcoreMode || !statesSupported) return
        val autoFile = saveStateManager.getSlotFile(SaveStateManager.AUTO_SLOT)
        if (!autoFile.exists()) return

        val settings = kotlinx.coroutines.runBlocking {
            preferencesRepository.getBuiltinEmulatorSettings().first()
        }

        if (!settings.autoRestoreState || hardcoreMode) return

        if (saveStateManager.performSlotLoad(retroView, SaveStateManager.AUTO_SLOT)) {
            inGameMessage = "State restored"
            Log.d(TAG, "Auto-restored state from auto slot")
        }
    }

    private fun handleAutoRestoreResponse(restore: Boolean) {
        autoRestorePromptVisible = false
        if (restore) {
            if (saveStateManager.performSlotLoad(retroView, SaveStateManager.AUTO_SLOT)) {
                inGameMessage = "State restored"
            } else {
                inGameMessage = "Failed to restore state"
            }
        }
    }

    private fun openInGameShaderChainEditor() {
        capturedGameFrame = retroView.captureRawFrame()
        retroView.pauseEmulation()

        val registry = ShaderRegistry(this)
        val manager = ShaderChainManager(
            shaderRegistry = registry,
            shaderDownloader = ShaderDownloader(registry.getCatalogDir()),
            previewRenderer = ShaderPreviewRenderer(),
            scope = lifecycleScope,
            previewInputProvider = {
                val frame = capturedGameFrame
                frame?.copy(frame.config ?: Bitmap.Config.ARGB_8888, false)
            },
            onChainChanged = { config ->
                val shaderConfig = ShaderRegistry(this).resolveChain(config)
                videoSettings.resolvedCustomShader = shaderConfig
                retroView.shader = shaderConfig
            }
        )

        val settings = kotlinx.coroutines.runBlocking {
            effectiveLibretroSettingsResolver.getEffectiveSettings(platformId, platformSlug)
        }
        manager.loadChain(settings.shaderChainJson)
        inGameShaderChainManager = manager
        shaderChainEditorVisible = true
    }

    private fun closeInGameShaderChainEditor() {
        val manager = inGameShaderChainManager ?: return
        val config = manager.getChainConfig()
        val shaderConfig = ShaderRegistry(this).resolveChain(config)
        Log.d(TAG, "closeInGameShaderChainEditor: resolved shader type=${shaderConfig::class.simpleName}")
        videoSettings.resolvedCustomShader = shaderConfig
        retroView.shader = shaderConfig
        videoSettings.persistShaderChain(config.toJson())
        manager.destroy()
        inGameShaderChainManager = null
        capturedGameFrame?.recycle()
        capturedGameFrame = null
        shaderChainEditorVisible = false
        settingsVisible = true
        Log.d(TAG, "closeInGameShaderChainEditor: done, settingsVisible=true")
    }

    private fun openInGameFrameEditor() {
        val registry = frameRegistry
        val manager = FrameManager(
            frameRegistry = registry,
            frameDownloader = FrameDownloader(registry.getFramesDir()),
            platformSlug = platformSlug,
            scope = lifecycleScope,
            initialFrameId = videoSettings.currentFrame,
            onFrameChanged = { frameId ->
                val bitmap = if (frameId != null) registry.loadFrame(frameId) else null
                if (bitmap != null) {
                    retroView.setBackgroundFrame(bitmap)
                } else {
                    retroView.clearBackgroundFrame()
                }
            }
        )
        inGameFrameManager = manager
        retroView.enablePreviewMode()
        frameEditorVisible = true
    }

    private fun confirmInGameFrameEditor() {
        val manager = inGameFrameManager ?: return
        val frameId = manager.selectedFrameId
        videoSettings.currentFrame = frameId
        videoSettings.persistFrame(frameId)
        if (frameId != null) {
            lifecycleScope.launch {
                val globalSettings = preferencesRepository.getBuiltinEmulatorSettings().first()
                if (!globalSettings.framesEnabled) {
                    preferencesRepository.setBuiltinFramesEnabled(true)
                }
            }
        }
        closeInGameFrameEditorInternal()
    }

    private fun closeInGameFrameEditor() {
        val originalFrameId = videoSettings.currentFrame
        val bitmap = if (originalFrameId != null) frameRegistry.loadFrame(originalFrameId) else null
        if (bitmap != null) {
            retroView.setBackgroundFrame(bitmap)
        } else {
            retroView.clearBackgroundFrame()
        }
        closeInGameFrameEditorInternal()
    }

    private fun closeInGameFrameEditorInternal() {
        retroView.disablePreviewMode()
        inGameFrameManager?.destroy()
        inGameFrameManager = null
        frameEditorVisible = false
        settingsVisible = true
    }

    private fun handleControlsAction(action: InGameControlsAction) {
        when (action) {
            is InGameControlsAction.SetRumble -> {
                videoSettings.currentRumbleEnabled = action.enabled
                if (action.enabled) {
                    if (vibrator == null) setupRumble()
                } else {
                    vibrator = null
                }
                videoSettings.persistControlSetting("rumbleEnabled", action.enabled)
            }
            is InGameControlsAction.SetAnalogAsDpad -> {
                videoSettings.currentAnalogAsDpad = action.enabled
                videoSettings.persistControlSetting("analogAsDpad", action.enabled)
            }
            is InGameControlsAction.SetDpadAsAnalog -> {
                videoSettings.currentDpadAsAnalog = action.enabled
                videoSettings.persistControlSetting("dpadAsAnalog", action.enabled)
            }
            is InGameControlsAction.SetLimitHotkeys -> {
                limitHotkeysToPlayer1 = action.enabled
                inputConfig.hotkeyManager.setLimitToPlayer1(action.enabled)
                lifecycleScope.launch {
                    preferencesRepository.setBuiltinLimitHotkeysToPlayer1(action.enabled)
                }
            }
            is InGameControlsAction.SetFastForwardMode -> {
                videoSettings.fastForwardMode = action.mode
                lifecycleScope.launch {
                    preferencesRepository.setBuiltinFastForwardMode(action.mode)
                }
            }
            is InGameControlsAction.SetFastForwardPreservePitch -> {
                videoSettings.fastForwardPreservePitch = action.enabled
                audioController.setPitchPreservationEnabled(action.enabled)
                lifecycleScope.launch {
                    preferencesRepository.setBuiltinFastForwardPreservePitch(action.enabled)
                }
            }
            InGameControlsAction.ShowControllerOrder,
            InGameControlsAction.ShowInputMapping,
            InGameControlsAction.ShowHotkeys -> {}
            is InGameControlsAction.SetTouchEnabled -> {
                lifecycleScope.launch { preferencesRepository.setTouchControlsShowWhenNoGamepad(action.enabled) }
            }
            is InGameControlsAction.SetTouchHaptic -> {
                lifecycleScope.launch { preferencesRepository.setTouchControlsHaptic(action.enabled) }
            }
            is InGameControlsAction.SetTouchLockOrientation -> {
                lifecycleScope.launch { preferencesRepository.setTouchControlsLockOrientation(action.enabled) }
            }
            is InGameControlsAction.SetTouchGenesis6Button -> {
                lifecycleScope.launch { preferencesRepository.setTouchControlsGenesis6Button(action.enabled) }
            }
        }
    }

    private fun showMenu() {
        if (!netplay.inSession) {
            retroView.pauseEmulation()
            retroView.suppressAutoResume = true
        }
        pendingSaveScreenshot?.recycle()
        pendingSaveScreenshot = try { retroView.captureRawFrame() } catch (_: Exception) { null }
        menuFocusIndex = 0
        menuVisible = true
    }

    private fun hideMenu() {
        menuVisible = false
        pendingSaveScreenshot?.recycle()
        pendingSaveScreenshot = null
        if (!netplay.inSession) {
            retroView.suppressAutoResume = false
            retroView.resumeEmulation()
        }
    }

    fun enterTouchEditMode() {
        menuVisible = false
        settingsVisible = false
        retroView.pauseEmulation()
        retroView.suppressAutoResume = true
        touchEditMode = true
    }

    private fun exitTouchEditMode() {
        touchEditMode = false
        if (!netplay.inSession) {
            retroView.suppressAutoResume = false
            retroView.resumeEmulation()
        }
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

    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (isAnyMenuOpen) {
            if (gamepadInputBridge.handleKeyEvent(event)) return true
            if (menuInputHandler.mapKeyToEvent(event.keyCode) != null) return true
        }
        return super.dispatchKeyEvent(event)
    }

    fun dispatchTouchKey(action: Int, keyCode: Int) {
        if (isAnyMenuOpen) return
        val event = KeyEvent(action, keyCode)
        if (action == KeyEvent.ACTION_DOWN) {
            if (hotkeyDispatcher.onKeyDown(keyCode, null)) return
            retroView.onKeyDown(keyCode, event)
        } else {
            hotkeyDispatcher.onKeyUp(keyCode)
            retroView.onKeyUp(keyCode, event)
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (isAnyMenuOpen) return super.onKeyDown(keyCode, event)

        val controllerId = event.device?.let { getControllerId(it) }
        if (hotkeyDispatcher.onKeyDown(keyCode, controllerId)) return true

        if (shouldFilterShoulderButton(keyCode)) return true

        val handled = retroView.onKeyDown(keyCode, event)
        if (handled) return true

        if (keyCode == KeyEvent.KEYCODE_BACK) {
            showMenu()
            return true
        }

        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (isAnyMenuOpen) return super.onKeyUp(keyCode, event)

        hotkeyDispatcher.onKeyUp(keyCode)

        if (shouldFilterShoulderButton(keyCode)) return true

        return retroView.onKeyUp(keyCode, event) || super.onKeyUp(keyCode, event)
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        val device = event.device
        triggerAxisKeyEmitter.emit(event) { axis ->
            device != null && inputMapper.hasAnalogMappingForAxis(device, axis)
        }.forEach { dispatchKeyEvent(it) }

        if (isAnyMenuOpen) {
            if (gamepadInputBridge.handleMotionEvent(event)) return true
            return super.onGenericMotionEvent(event)
        }

        if (motionProcessor.processGamepadMotion(event)) return true

        return retroView.onGenericMotionEvent(event) || super.onGenericMotionEvent(event)
    }

    override fun onResume() {
        super.onResume()
        autoSaveStateCaptured = false
        enterImmersiveMode()
        retroView.onResume()
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        com.nendo.argosy.DualScreenManagerHolder.instance
            ?.onSessionChanged(-1L)
        finish()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        when (intent.action) {
            ACTION_SHOW_MENU -> showMenu()
            ACTION_QUIT -> finish()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            enterImmersiveMode()
        }
    }

    override fun onPause() {
        if (::netplay.isInitialized) netplay.gracefullyEndIfActive()
        if (isClosing) {
            super.onPause()
            return
        }
        if (coreLoadedSuccessfully) {
            performAutoSaveState()
            if (!isGuestJoinedSession && !coreDestroyed) {
                saveStateManager.saveSram(retroView)
            }
            if (!coreDestroyed) {
                captureTouchBackdrop()
            }
            if (isFinishing && !coreDestroyed) {
                coreDestroyed = true
                retroView.destroyNative()
            }
            retroView.onPause()
        }
        super.onPause()
    }

    private fun applyOrientationLock(locked: Boolean) {
        requestedOrientation = if (!locked) {
            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
        } else if (currentOrientationState == android.content.res.Configuration.ORIENTATION_PORTRAIT) {
            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
    }

    private fun captureTouchBackdrop() {
        if (platformSlug.isBlank()) return
        val bmp = try { retroView.captureRawFrame() } catch (_: Exception) { null } ?: return
        val orientation = currentOrientationState
        val ctx = applicationContext
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                com.nendo.argosy.libretro.touch.TouchBackdropCache.save(ctx, platformSlug, orientation, bmp)
            } finally {
                bmp.recycle()
            }
        }
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        currentOrientationState = newConfig.orientation
        currentRotationState = windowManager.defaultDisplay.rotation
        splitColumn?.let { applyPortraitSplit(it) }
    }

    private fun registerGamepadDetection() {
        val im = getSystemService(android.hardware.input.InputManager::class.java) ?: return
        val listener = object : android.hardware.input.InputManager.InputDeviceListener {
            override fun onInputDeviceAdded(deviceId: Int) {
                val connected = com.nendo.argosy.core.input.ControllerDetector.isAnyGamepadConnected()
                if (connected != isGamepadConnectedState) {
                    isGamepadConnectedState = connected
                    splitColumn?.let { applyPortraitSplit(it) }
                }
            }
            override fun onInputDeviceRemoved(deviceId: Int) {
                val connected = com.nendo.argosy.core.input.ControllerDetector.isAnyGamepadConnected()
                if (connected != isGamepadConnectedState) {
                    isGamepadConnectedState = connected
                    splitColumn?.let { applyPortraitSplit(it) }
                }
            }
            override fun onInputDeviceChanged(deviceId: Int) {
                val connected = com.nendo.argosy.core.input.ControllerDetector.isAnyGamepadConnected()
                if (connected != isGamepadConnectedState) {
                    isGamepadConnectedState = connected
                    splitColumn?.let { applyPortraitSplit(it) }
                }
            }
        }
        inputDeviceListener = listener
        im.registerInputDeviceListener(listener, null)
    }

    private fun unregisterGamepadDetection() {
        val l = inputDeviceListener ?: return
        getSystemService(android.hardware.input.InputManager::class.java)?.unregisterInputDeviceListener(l)
        inputDeviceListener = null
    }

    private fun registerOrientationListener() {
        val listener = object : android.view.OrientationEventListener(this) {
            override fun onOrientationChanged(orientation: Int) {
                if (orientation == android.view.OrientationEventListener.ORIENTATION_UNKNOWN) return
                val rot = windowManager.defaultDisplay.rotation
                if (rot != currentRotationState) currentRotationState = rot
            }
        }
        if (listener.canDetectOrientation()) {
            listener.enable()
            orientationEventListener = listener
        }
    }

    private fun unregisterOrientationListener() {
        orientationEventListener?.disable()
        orientationEventListener = null
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy: isFinishing=$isFinishing, isChangingConfigurations=$isChangingConfigurations")
        coreDestroyed = true
        unregisterGamepadDetection()
        unregisterOrientationListener()
        audioController.abandonAudioFocus()
        com.nendo.argosy.DualScreenManagerHolder.instance?.let { dsm ->
            dsm.emulatorKeyDispatcher = null
            dsm.emulatorMotionDispatcher = null
        }
        if (::netplay.isInitialized) netplay.shutdown()
        if (::achievementBridge.isInitialized) achievementBridge.destroy()
        if (isFinishing && gameId != -1L) {
            com.nendo.argosy.DualScreenManagerHolder.instance
                ?.onSessionChanged(-1L)
            playSessionTracker.endSessionInBackground(skipSaveSync = isGuestJoinedSession)
        }
        super.onDestroy()
    }

    private fun getControllerId(device: InputDevice): String {
        return "${device.vendorId}:${device.productId}:${device.descriptor}"
    }

    private fun coreVariablesFromIntent(): Array<Variable> {
        val keys = intent.getStringArrayExtra(EXTRA_CORE_VAR_KEYS) ?: return emptyArray()
        val values = intent.getStringArrayExtra(EXTRA_CORE_VAR_VALUES) ?: return emptyArray()
        return keys.zip(values) { k, v -> Variable(k, v) }.toTypedArray()
    }

    private fun shouldFilterShoulderButton(keyCode: Int): Boolean {
        val isL1R1 = keyCode == KeyEvent.KEYCODE_BUTTON_L1 || keyCode == KeyEvent.KEYCODE_BUTTON_R1
        val isL2R2 = keyCode == KeyEvent.KEYCODE_BUTTON_L2 || keyCode == KeyEvent.KEYCODE_BUTTON_R2

        if (!isL1R1 && !isL2R2) return false

        val canonicalPlatform = com.nendo.argosy.data.platform.PlatformDefinitions.getCanonicalSlug(platformSlug)
        if (isL1R1 && canonicalPlatform in PLATFORMS_WITHOUT_SHOULDERS) return true
        if (isL2R2 && canonicalPlatform !in PLATFORMS_WITH_L2_R2) return true

        return false
    }

    companion object {
        private const val TAG = "LibretroActivity"

        private const val CORE_INPUT_PULSE_MS = 50L

        const val EXTRA_ROM_PATH = "rom_path"
        const val EXTRA_CORE_PATH = "core_path"
        const val EXTRA_SYSTEM_DIR = "system_dir"
        const val EXTRA_GAME_NAME = "game_name"
        const val EXTRA_GAME_ID = "game_id"
        const val EXTRA_PLATFORM_SLUG = "platform_slug"
        const val EXTRA_CORE_NAME = "core_name"
        const val EXTRA_CORE_VAR_KEYS = "core_var_keys"
        const val EXTRA_CORE_VAR_VALUES = "core_var_values"
        const val EXTRA_SAVES_DIR = "saves_dir"
        const val EXTRA_STATES_DIR = "states_dir"
        const val EXTRA_NETPLAY_JOIN_SESSION_ID = "netplay_join_session_id"
        const val EXTRA_NETPLAY_JOIN_HOST_USER_ID = "netplay_join_host_user_id"
        const val ACTION_SHOW_MENU = "com.nendo.argosy.action.SHOW_MENU"
        const val ACTION_QUIT = "com.nendo.argosy.action.QUIT"

        private val PLATFORMS_WITHOUT_SHOULDERS = setOf(
            "gb", "gbc",
            "nes", "fds",
            "sg1000", "sms", "gg",
            "atari2600", "atari5200", "atari7800",
            "coleco", "intellivision", "odyssey2", "vectrex"
        )

        private val PLATFORMS_WITH_L2_R2 = setOf(
            "psx", "ps1", "playstation",
            "dreamcast", "dc",
            "saturn",
            "gc", "ngc", "gamecube", "wii",
            "psp",
            "3do"
        )

        private val PLATFORMS_WITHOUT_STATE_SUPPORT = setOf("psp")
    }
}
