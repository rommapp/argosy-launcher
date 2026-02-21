package com.nendo.argosy.libretro

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.KeyEvent
import android.view.InputDevice
import android.view.MotionEvent
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
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
import com.nendo.argosy.data.repository.SaveCacheManager
import com.nendo.argosy.hardware.AmbientLedManager
import com.nendo.argosy.data.local.dao.AchievementDao
import com.nendo.argosy.data.local.dao.CheatDao
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.entity.HotkeyAction
import com.nendo.argosy.data.platform.PlatformWeightRegistry
import com.nendo.argosy.data.preferences.EffectiveLibretroSettingsResolver
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.repository.InputConfigRepository
import com.nendo.argosy.data.repository.InputSource
import com.nendo.argosy.data.repository.RetroAchievementsRepository
import com.nendo.argosy.ui.screens.common.AchievementUpdateBus
import com.nendo.argosy.libretro.ui.cheats.CheatDisplayItem
import com.nendo.argosy.libretro.ui.cheats.CheatsScreen
import com.nendo.argosy.libretro.ui.cheats.CheatsTab
import com.nendo.argosy.libretro.ui.AchievementPopup
import com.nendo.argosy.libretro.ui.InGameMenu
import com.nendo.argosy.libretro.ui.InGameMenuAction
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
import com.swordfish.libretrodroid.GLRetroView
import com.swordfish.libretrodroid.GLRetroViewData
import com.swordfish.libretrodroid.LibretroDroid
import com.swordfish.libretrodroid.Variable
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
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
    @Inject lateinit var saveCacheManager: SaveCacheManager
    @Inject lateinit var ambientLedManager: AmbientLedManager
    @Inject lateinit var effectiveLibretroSettingsResolver: EffectiveLibretroSettingsResolver
    @Inject lateinit var platformLibretroSettingsDao: com.nendo.argosy.data.local.dao.PlatformLibretroSettingsDao
    @Inject lateinit var frameRegistry: FrameRegistry

    private lateinit var retroView: GLRetroView
    private val portResolver = ControllerPortResolver()
    private val inputMapper = ControllerInputMapper()
    private lateinit var inputConfig: InputConfigCoordinator
    private lateinit var hotkeyDispatcher: HotkeyDispatcher
    private lateinit var motionProcessor: MotionEventProcessor
    private var vibrator: Vibrator? = null
    private lateinit var romPath: String

    private lateinit var saveStateManager: SaveStateManager
    private lateinit var videoSettings: VideoSettingsManager
    private lateinit var cheatManager: CheatSessionManager
    private var raSession: RetroAchievementsSessionManager? = null

    private var gameId: Long = -1L
    private var platformId: Long = -1L
    private var platformSlug: String = ""
    private var coreName: String? = null
    private var menuVisible by mutableStateOf(false)
    private var cheatsMenuVisible by mutableStateOf(false)
    private var settingsVisible by mutableStateOf(false)
    private var shaderChainEditorVisible by mutableStateOf(false)
    private var frameEditorVisible by mutableStateOf(false)
    private var inGameShaderChainManager: ShaderChainManager? = null
    private var inGameFrameManager: FrameManager? = null
    private var capturedGameFrame: Bitmap? = null
    private var lastCheatsTab by mutableStateOf(CheatsTab.CHEATS)
    private var gameName: String = ""
    private var isFastForwarding = false
    private var isRewinding = false
    private var canEnableBFI = false

    private var lastCaptureTime = 0L
    private var lastRewindTime = 0L
    private var limitHotkeysToPlayer1 by mutableStateOf(true)
    private val frameIntervalMs = 16L
    private val rewindSpeed = 2
    private var firstFrameRendered = false
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

    private val isAnyMenuOpen: Boolean
        get() = menuVisible || cheatsMenuVisible || settingsVisible || shaderChainEditorVisible || frameEditorVisible

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate: savedInstanceState=${savedInstanceState != null}")
        enableEdgeToEdge()
        enterImmersiveMode()

        if (!parseIntentExtras()) return

        val romFile = File(romPath)
        if (!validateRomFile(romFile)) return

        val systemDir = intent.getStringExtra(EXTRA_SYSTEM_DIR)
            ?.let { File(it) }
            ?: File(filesDir, "libretro/system")
        systemDir.mkdirs()
        val savesDir = File(filesDir, "libretro/saves").apply { mkdirs() }
        val statesDir = File(filesDir, "libretro/states").apply { mkdirs() }

        initializeSaveState(savesDir, statesDir)

        val game = kotlinx.coroutines.runBlocking { gameDao.getById(gameId) }
        platformId = game?.platformId ?: -1L
        platformSlug = game?.platformSlug ?: ""
        val globalSettings = kotlinx.coroutines.runBlocking {
            preferencesRepository.getBuiltinEmulatorSettings().first()
        }
        val settings = kotlinx.coroutines.runBlocking {
            effectiveLibretroSettingsResolver.getEffectiveSettings(platformId, platformSlug)
        }

        initializeInputHandlers()
        initializeVideoSettings(globalSettings, settings)
        detectBFICapability()

        val corePath = intent.getStringExtra(EXTRA_CORE_PATH)!!
        createRetroView(corePath, systemDir, savesDir, settings, restoredSram)
        setupRetroViewListeners()
        configureRetroView(settings)
        inputConfig = InputConfigCoordinator(
            inputConfigRepository = inputConfigRepository,
            portResolver = portResolver,
            inputMapper = inputMapper,
            platformSlug = platformSlug,
            limitHotkeysToPlayer1 = limitHotkeysToPlayer1,
            scope = lifecycleScope
        )
        inputConfig.initialize()

        if (settings.rumbleEnabled) {
            setupRumble()
        }
        if (videoSettings.rewindEnabled && !hardcoreMode) {
            setupRewind()
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
            playSessionTracker.startSession(gameId, EmulatorRegistry.BUILTIN_PACKAGE, coreName, hardcoreMode, isNewGame)
            cheatManager.loadCheats(hardcoreMode)
            initializeRASession()
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
        return true
    }

    private fun validateRomFile(romFile: File): Boolean {
        if (!romFile.exists()) {
            Log.e(TAG, "ROM file not found: $romPath")
            Toast.makeText(this, "Game file not found", Toast.LENGTH_LONG).show()
            finish()
            return false
        }
        if (!romFile.canRead()) {
            Log.e(TAG, "ROM file not readable: $romPath")
            Toast.makeText(this, "Cannot access game file", Toast.LENGTH_LONG).show()
            finish()
            return false
        }
        Log.d(TAG, "ROM validated: exists=${romFile.exists()}, size=${romFile.length()}, path=$romPath")
        return true
    }

    private fun initializeSaveState(savesDir: File, statesDir: File) {
        saveStateManager = SaveStateManager(
            savesDir = savesDir,
            statesDir = statesDir,
            romPath = romPath,
            gameId = gameId,
            gameDao = gameDao,
            saveCacheManager = saveCacheManager
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
                setupRewind()
            } else if (!enabled) {
                retroView.destroyRewindBuffer()
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
                skipDuplicateFrames = settings.skipDuplicateFrames
                preferLowLatencyAudio = settings.lowLatencyAudio
                forceSoftwareTiming = settings.forceSoftwareTiming
                rumbleEventsEnabled = settings.rumbleEnabled
                variables = getCoreVariables(coreName)
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
                Toast.makeText(this@LibretroActivity, errorMessage, Toast.LENGTH_LONG).show()
                finish()
            }
        }

        lifecycleScope.launch {
            retroView.getGLRetroEvents().collect { event ->
                when (event) {
                    is GLRetroView.GLRetroEvents.SurfaceCreated -> {
                        Log.i(TAG, "[Startup] GL surface created - render pipeline ready")
                        videoSettings.currentFrame?.let { frameId ->
                            val bitmap = frameRegistry.loadFrame(frameId)
                            if (bitmap != null) {
                                Log.i(TAG, "[Startup] Setting initial frame: $frameId (${bitmap.width}x${bitmap.height})")
                                retroView.setBackgroundFrame(bitmap)
                            }
                        }
                    }
                    is GLRetroView.GLRetroEvents.FrameRendered -> {
                        if (!firstFrameRendered) {
                            firstFrameRendered = true
                            Log.i(TAG, "[Startup] First frame rendered - emulation running successfully")
                            Log.d(TAG, "[Startup] gameId=$gameId, core=$coreName, hardcore=$hardcoreMode")
                        }
                    }
                }
            }
        }
    }

    private fun configureRetroView(settings: com.nendo.argosy.data.preferences.BuiltinEmulatorSettings) {
        retroView.audioEnabled = true
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
    }

    private fun initializeHotkeyDispatcher() {
        hotkeyDispatcher = HotkeyDispatcher(
            saveStateManager = saveStateManager,
            videoSettings = videoSettings,
            hotkeyManager = inputConfig.hotkeyManager,
            getRetroView = { retroView },
            showToast = { msg -> Toast.makeText(this, msg, Toast.LENGTH_SHORT).show() },
            isHardcoreMode = { hardcoreMode },
            onShowMenu = ::showMenu,
            onFastForwardChanged = { ff ->
                if (ff && !isFastForwarding) {
                    isFastForwarding = true
                    retroView.frameSpeed = videoSettings.fastForwardSpeed
                }
            },
            onRewindChanged = { rw ->
                if (rw && !isRewinding) {
                    isRewinding = true
                    lastRewindTime = 0L
                    retroView.frameSpeed = 1
                }
            },
            onQuit = ::finish
        )
    }

    private fun buildContentView() {
        val container = FrameLayout(this).apply {
            addView(retroView, FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            addView(
                ComposeView(this@LibretroActivity).apply {
                    setContent { InGameOverlay() }
                },
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        setContentView(container)

        container.post {
            videoSettings.setScreenSize(container.width, container.height)
            Log.d(TAG, "Container size: ${container.width}x${container.height}, aspectRatioMode: ${videoSettings.aspectRatioMode}")
            Handler(Looper.getMainLooper()).postDelayed({
                Log.d(TAG, "Applying aspect ratio after delay: ${videoSettings.aspectRatioMode}")
                videoSettings.applyAspectRatio()
                videoSettings.applyOverscanCrop()
                videoSettings.applyRotation()
            }, 500)
        }
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
                if (menuVisible) {
                    activeMenuHandler = InGameMenu(
                        gameName = gameName,
                        hasQuickSave = saveStateManager.hasQuickSave && !hardcoreMode,
                        cheatsAvailable = !hardcoreMode && PlatformWeightRegistry.supportsCheats(platformSlug),
                        focusedIndex = menuFocusIndex,
                        onFocusChange = { menuFocusIndex = it },
                        onAction = ::handleMenuAction,
                        isHardcoreMode = hardcoreMode
                    )
                }
                if (cheatsMenuVisible) {
                    activeMenuHandler = CheatsScreen(
                        cheats = cheatManager.cheats.map { CheatDisplayItem(it.id, it.description, it.code, it.enabled, it.isUserCreated, it.lastUsedAt) },
                        scanner = cheatManager.memoryScanner,
                        initialTab = lastCheatsTab,
                        onToggleCheat = cheatManager::handleToggleCheat,
                        onCreateCheat = cheatManager::handleCreateCheat,
                        onUpdateCheat = cheatManager::handleUpdateCheat,
                        onDeleteCheat = cheatManager::handleDeleteCheat,
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
                if (!isAnyMenuOpen) {
                    activeMenuHandler = null
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    AchievementPopup(
                        achievement = raSession?.currentAchievementUnlock,
                        onDismiss = { raSession?.showNextUnlock() },
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .statusBarsPadding()
                    )
                    RAConnectionNotification(
                        connectionInfo = raSession?.raConnectionInfo,
                        onDismiss = { raSession?.dismissConnectionInfo() },
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .statusBarsPadding()
                    )
                }
            }
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
            controlsState = InGameControlsState(
                rumbleEnabled = videoSettings.currentRumbleEnabled,
                analogAsDpad = videoSettings.currentAnalogAsDpad,
                dpadAsAnalog = videoSettings.currentDpadAsAnalog,
                limitHotkeysToPlayer1 = limitHotkeysToPlayer1,
                controllerOrderCount = inputConfig.controllerOrderCount
            ),
            onControlsAction = ::handleControlsAction,
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
            }
        )
    }

    private fun initializeRASession() {
        val session = RetroAchievementsSessionManager(
            gameId = gameId,
            romPath = romPath,
            hardcoreMode = hardcoreMode,
            gameDao = gameDao,
            achievementDao = achievementDao,
            raRepository = raRepository,
            achievementUpdateBus = achievementUpdateBus,
            ambientLedManager = ambientLedManager,
            scope = lifecycleScope,
            context = this
        )
        raSession = session
        session.initialize(retroView)
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
                        val slotCount = 900
                        val maxStateSize = 4 * 1024 * 1024
                        retroView.initRewindBuffer(slotCount, maxStateSize)
                        Log.d(TAG, "Rewind buffer initialized: $slotCount slots, ${maxStateSize / 1024}KB max state")
                        cheatManager.applyAllEnabledCheats(hardcoreMode)
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
                                    val captureCount = if (isFastForwarding) videoSettings.fastForwardSpeed else 1
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
        if (!videoSettings.rewindEnabled) return false
        return retroView.rewindFrame()
    }

    private fun handleMenuAction(action: InGameMenuAction) {
        when (action) {
            InGameMenuAction.Resume -> hideMenu()
            InGameMenuAction.QuickSave -> {
                if (saveStateManager.performQuickSave(retroView)) {
                    Toast.makeText(this, "State saved", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Failed to save state", Toast.LENGTH_SHORT).show()
                }
                hideMenu()
            }
            InGameMenuAction.QuickLoad -> {
                if (saveStateManager.performQuickLoad(retroView)) {
                    Toast.makeText(this, "State loaded", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Failed to load state", Toast.LENGTH_SHORT).show()
                }
                hideMenu()
            }
            InGameMenuAction.Settings -> {
                menuVisible = false
                settingsVisible = true
            }
            InGameMenuAction.Cheats -> {
                menuVisible = false
                cheatsMenuVisible = true
            }
            InGameMenuAction.Quit -> finish()
        }
    }

    private fun openInGameShaderChainEditor() {
        retroView.onResume()
        capturedGameFrame = retroView.captureRawFrame()
        retroView.onPause()

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
        videoSettings.resolvedCustomShader = shaderConfig
        retroView.shader = shaderConfig
        videoSettings.persistShaderChain(config.toJson())
        manager.destroy()
        inGameShaderChainManager = null
        capturedGameFrame?.recycle()
        capturedGameFrame = null
        shaderChainEditorVisible = false
        settingsVisible = true
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
            InGameControlsAction.ShowControllerOrder,
            InGameControlsAction.ShowInputMapping,
            InGameControlsAction.ShowHotkeys -> {}
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

    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (isAnyMenuOpen) {
            if (gamepadInputBridge.handleKeyEvent(event)) return true
            if (menuInputHandler.mapKeyToEvent(event.keyCode) != null) return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (isAnyMenuOpen) return super.onKeyDown(keyCode, event)

        val controllerId = event.device?.let { getControllerId(it) }
        val triggeredAction = inputConfig.hotkeyManager.onKeyDown(keyCode, controllerId)
        if (triggeredAction != null) {
            return hotkeyDispatcher.dispatch(triggeredAction)
        }

        if (shouldFilterShoulderButton(keyCode)) return true

        return retroView.onKeyDown(keyCode, event) || super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (isAnyMenuOpen) return super.onKeyUp(keyCode, event)

        inputConfig.hotkeyManager.onKeyUp(keyCode)

        if (!inputConfig.hotkeyManager.isHotkeyActive(HotkeyAction.FAST_FORWARD) && isFastForwarding) {
            isFastForwarding = false
            retroView.frameSpeed = 1
        }
        if (!inputConfig.hotkeyManager.isHotkeyActive(HotkeyAction.REWIND) && isRewinding) {
            isRewinding = false
        }

        if (shouldFilterShoulderButton(keyCode)) return true

        return retroView.onKeyUp(keyCode, event) || super.onKeyUp(keyCode, event)
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (isAnyMenuOpen) {
            if (gamepadInputBridge.handleMotionEvent(event)) return true
            return super.onGenericMotionEvent(event)
        }

        if (motionProcessor.processGamepadMotion(event)) return true

        return retroView.onGenericMotionEvent(event) || super.onGenericMotionEvent(event)
    }

    override fun onResume() {
        super.onResume()
        enterImmersiveMode()
        if (!isAnyMenuOpen) {
            retroView.onResume()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == ACTION_SHOW_MENU) {
            showMenu()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            enterImmersiveMode()
        }
    }

    override fun onPause() {
        saveStateManager.saveSram(retroView)
        retroView.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy: isFinishing=$isFinishing, isChangingConfigurations=$isChangingConfigurations")
        raSession?.destroy()
        if (videoSettings.rewindEnabled && !hardcoreMode) {
            retroView.destroyRewindBuffer()
        }
        if (isFinishing && gameId != -1L) {
            kotlinx.coroutines.GlobalScope.launch { playSessionTracker.endSession() }
        }
        super.onDestroy()
    }

    private fun getControllerId(device: InputDevice): String {
        return "${device.vendorId}:${device.productId}:${device.descriptor}"
    }

    private fun getCoreVariables(coreName: String?): Array<Variable> {
        return when (coreName?.lowercase()) {
            "flycast" -> arrayOf(
                Variable("flycast_threaded_rendering", "enabled"),
                Variable("flycast_internal_resolution", "640x480"),
                Variable("flycast_anisotropic_filtering", "off"),
                Variable("flycast_enable_dsp", "enabled"),
                Variable("flycast_synchronous_rendering", "disabled")
            )
            "opera" -> arrayOf(
                Variable("opera_high_resolution", "disabled"),
                Variable("opera_hack_timing_1", "disabled"),
                Variable("opera_hack_timing_3", "disabled")
            )
            else -> emptyArray()
        }
    }

    private fun shouldFilterShoulderButton(keyCode: Int): Boolean {
        val isL1R1 = keyCode == KeyEvent.KEYCODE_BUTTON_L1 || keyCode == KeyEvent.KEYCODE_BUTTON_R1
        val isL2R2 = keyCode == KeyEvent.KEYCODE_BUTTON_L2 || keyCode == KeyEvent.KEYCODE_BUTTON_R2

        if (!isL1R1 && !isL2R2) return false

        if (isL1R1 && platformSlug in PLATFORMS_WITHOUT_SHOULDERS) return true
        if (isL2R2 && platformSlug !in PLATFORMS_WITH_L2_R2) return true

        return false
    }

    companion object {
        private const val TAG = "LibretroActivity"

        const val EXTRA_ROM_PATH = "rom_path"
        const val EXTRA_CORE_PATH = "core_path"
        const val EXTRA_SYSTEM_DIR = "system_dir"
        const val EXTRA_GAME_NAME = "game_name"
        const val EXTRA_GAME_ID = "game_id"
        const val EXTRA_CORE_NAME = "core_name"
        const val ACTION_SHOW_MENU = "com.nendo.argosy.action.SHOW_MENU"

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
    }
}
