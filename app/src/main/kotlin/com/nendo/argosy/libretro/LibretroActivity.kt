package com.nendo.argosy.libretro

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
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
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
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
import com.nendo.argosy.BuildConfig
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
import com.nendo.argosy.data.netplay.CandidateGatherer
import com.nendo.argosy.data.netplay.CoreHashCache
import com.nendo.argosy.data.netplay.NetplayHandshake
import com.nendo.argosy.data.netplay.NetplaySessionManager
import com.nendo.argosy.data.netplay.NetplaySessionRules
import com.nendo.argosy.data.netplay.RomHashComputer
import com.nendo.argosy.data.social.ArgosSocialService
import com.nendo.argosy.data.social.NetplayOpenPayload
import com.nendo.argosy.data.social.NetplaySessionState
import com.nendo.argosy.data.social.SocialRepository
import com.nendo.argosy.ui.screens.common.AchievementUpdateBus
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
import com.nendo.argosy.libretro.ui.NetplayFriendPickerEntry
import com.nendo.argosy.libretro.ui.NetplayHostDisconnectPrompt
import com.nendo.argosy.libretro.ui.NetplayMenuRole
import com.nendo.argosy.libretro.ui.NetplayProgressStage
import com.nendo.argosy.libretro.ui.NetplayProgressState
import com.nendo.argosy.libretro.ui.NetplayQualityInfo
import com.nendo.argosy.libretro.ui.NetplayQualityLabel
import com.nendo.argosy.libretro.ui.NetplayQualityWarningPrompt
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
import com.swordfish.libretrodroid.GLRetroView
import com.swordfish.libretrodroid.GLRetroViewData
import com.swordfish.libretrodroid.LibretroDroid
import com.swordfish.libretrodroid.Variable
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
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

    private var coreLoadedSuccessfully = false
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
    private var activeSaveChannel: String? = null
    private var menuVisible by mutableStateOf(false)
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
    private var isFastForwarding = false
    private var isRewinding = false
    private var canEnableBFI = false

    private var limitHotkeysToPlayer1 by mutableStateOf(true)
    private var firstFrameRendered = false
    private var audioFocusRequest: AudioFocusRequest? = null
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

    private var netplaySessionManager: NetplaySessionManager? = null
    private var pendingNetplayJoin: PendingNetplayJoin? = null
    private var isGuestJoinedSession: Boolean = false
    private var guestSessionEverStarted: Boolean = false

    private data class PendingNetplayJoin(val sessionId: String, val hostUserId: String)
    private var netplaySessionRules: NetplaySessionRules? = null
    private var netplayInSession by mutableStateOf(false)
    private var netplayRole: NetplayMenuRole? by mutableStateOf(null)
    private var netplaySessionIsReserved by mutableStateOf(false)
    private var lastJoinedToastPeerId: String? = null
    private var netplayProgressState by mutableStateOf<NetplayProgressState?>(null)
    private var netplayReconnecting by mutableStateOf(false)
    private var netplayDisconnectPromptVisible by mutableStateOf(false)
    private var netplayDisconnectPromptPeer by mutableStateOf("Friend")
    private var netplayDisconnectPromptFocus by mutableStateOf(0)
    private var netplayFriendPickerVisible by mutableStateOf(false)
    private var netplayFriendPickerFocus by mutableStateOf(0)
    private var netplayFriendPickerEntries by mutableStateOf<List<NetplayFriendPickerEntry>>(emptyList())
    private var netplayPeerDisplayName by mutableStateOf("Friend")
    private var netplayLastRttMs by mutableStateOf<Int?>(null)
    private var netplayQualityWarningVisible by mutableStateOf(false)
    private var netplayQualityWarningRttMs by mutableStateOf(0)
    private var netplayQualityWarningJitterMs by mutableStateOf(0)
    private var netplayQualityWarningLabel by mutableStateOf("")
    private var netplayQualityWarningFocus by mutableStateOf(0)
    private var lastAnnouncedTier: NetplayQualityLabel? = null
    private var tierChangeTimestamp: Long = 0L
    private var savedOrientation: Int = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    private var corePath: String = ""
    private var resolvedCoreId: String? = null

    private val isAnyMenuOpen: Boolean
        get() = menuVisible || cheatsMenuVisible || settingsVisible || shaderChainEditorVisible || frameEditorVisible || autoRestorePromptVisible || stateManagerVisible ||
            netplayProgressState != null || netplayDisconnectPromptVisible || netplayFriendPickerVisible || netplayQualityWarningVisible

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate: savedInstanceState=${savedInstanceState != null}")
        enableEdgeToEdge()
        enterImmersiveMode()

        com.nendo.argosy.DualScreenManagerHolder.instance?.let { dsm ->
            dsm.emulatorKeyDispatcher = { event -> dispatchKeyEvent(event) }
            dsm.emulatorMotionDispatcher = { event -> dispatchGenericMotionEvent(event) }
        }

        if (!parseIntentExtras()) return

        val romFile = File(romPath)
        if (!validateRomFile(romFile)) return

        val systemDir = intent.getStringExtra(EXTRA_SYSTEM_DIR)
            ?.let { File(it) }
            ?: File(filesDir, "libretro/system")
        systemDir.mkdirs()
        val savesDir: File
        val statesDir: File
        if (isGuestJoinedSession) {
            // Guest sessions operate on a throwaway cache directory so no
            // SRAM is loaded from the user's real saves and nothing written
            // during the session leaks back into their persistent store.
            val scratch = File(cacheDir, "netplay_guest/${System.currentTimeMillis()}")
            savesDir = File(scratch, "saves").apply { mkdirs() }
            statesDir = File(scratch, "states").apply { mkdirs() }
        } else {
            savesDir = (intent.getStringExtra(EXTRA_SAVES_DIR)?.let { File(it) }
                ?: File(filesDir, "libretro/saves")).apply { mkdirs() }
            statesDir = (intent.getStringExtra(EXTRA_STATES_DIR)?.let { File(it) }
                ?: File(filesDir, "libretro/states")).apply { mkdirs() }
        }

        val game = kotlinx.coroutines.runBlocking { gameDao.getById(gameId) }
        platformId = game?.platformId ?: -1L
        platformSlug = game?.platformSlug ?: ""
        activeSaveChannel = game?.activeSaveChannel

        initializeSaveState(savesDir, statesDir, activeSaveChannel)
        val globalSettings = kotlinx.coroutines.runBlocking {
            preferencesRepository.getBuiltinEmulatorSettings().first()
        }
        val settings = kotlinx.coroutines.runBlocking {
            effectiveLibretroSettingsResolver.getEffectiveSettings(platformId, platformSlug)
        }

        autoSaveEnabled = globalSettings.autoSaveState && !isGuestJoinedSession
        initializeInputHandlers()
        initializeVideoSettings(globalSettings, settings)
        detectBFICapability()

        corePath = intent.getStringExtra(EXTRA_CORE_PATH)!!
        resolvedCoreId = resolveCoreIdFromPath(corePath)
        createRetroView(corePath, systemDir, savesDir, settings, restoredSram)
        initializeNetplaySessionManager()
        setupRetroViewListeners()
        configureRetroView(settings)
        requestAudioFocus()
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

        val joinSessionId = intent.getStringExtra(EXTRA_NETPLAY_JOIN_SESSION_ID)
        val joinHostUserId = intent.getStringExtra(EXTRA_NETPLAY_JOIN_HOST_USER_ID)
        if (!joinSessionId.isNullOrEmpty() && !joinHostUserId.isNullOrEmpty()) {
            pendingNetplayJoin = PendingNetplayJoin(joinSessionId, joinHostUserId)
            isGuestJoinedSession = true
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
                    }
                    is GLRetroView.GLRetroEvents.FrameRendered -> {
                        if (!firstFrameRendered) {
                            firstFrameRendered = true
                            Log.i(TAG, "[Startup] First frame rendered - emulation running successfully")
                            Log.d(TAG, "[Startup] gameId=$gameId, core=$coreName, hardcore=$hardcoreMode")
                            checkStateSupport()
                            attemptAutoRestore()
                        }
                    }
                }
            }
        }
    }

    private fun requestAudioFocus() {
        val am = getSystemService(AudioManager::class.java) ?: return
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setOnAudioFocusChangeListener { }
            .build()
        audioFocusRequest = request
        am.requestAudioFocus(request)
    }

    private fun abandonAudioFocus() {
        val request = audioFocusRequest ?: return
        val am = getSystemService(AudioManager::class.java) ?: return
        am.abandonAudioFocusRequest(request)
        audioFocusRequest = null
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
        netplaySessionRules?.cheatSessionManager = cheatManager
    }

    private fun initializeHotkeyDispatcher() {
        hotkeyDispatcher = HotkeyDispatcher(
            saveStateManager = saveStateManager,
            videoSettings = videoSettings,
            hotkeyManager = inputConfig.hotkeyManager,
            getRetroView = { retroView },
            showToast = { msg -> inGameMessage = msg },
            isHardcoreMode = { hardcoreMode },
            isNetplayInSession = { netplayInSession },
            getNetplayRole = { netplayRole },
            onShowMenu = ::showMenu,
            onFastForwardChanged = { ff ->
                if (!netplayInSession && ff && !isFastForwarding && videoSettings.fastForwardEnabled) {
                    isFastForwarding = true
                    retroView.frameSpeed = videoSettings.fastForwardSpeed
                }
            },
            onRewindChanged = { rw ->
                if (!netplayInSession && rw && !isRewinding) {
                    isRewinding = true
                    retroView.isRewinding = true
                    retroView.frameSpeed = 1
                }
            },
            onAutoSaveState = ::performAutoSaveState,
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
                    val quality = if (netplayInSession && netplayRole != null) {
                        NetplayQualityInfo(
                            peerDisplayName = netplayPeerDisplayName,
                            role = netplayRole!!,
                            pingMs = netplayLastRttMs,
                            label = NetplayQualityInfo.labelForRttMs(netplayLastRttMs)
                        )
                    } else null
                    activeMenuHandler = InGameMenu(
                        gameName = gameName,
                        cheatsAvailable = !hardcoreMode && PlatformWeightRegistry.supportsCheats(platformSlug),
                        statesSupported = statesSupported && !hardcoreMode,
                        focusedIndex = menuFocusIndex,
                        onFocusChange = { menuFocusIndex = it },
                        onAction = ::handleMenuAction,
                        isHardcoreMode = hardcoreMode,
                        netplaySupported = isNetplayCoreSupported(),
                        isInNetplaySession = netplayInSession,
                        netplayRole = netplayRole,
                        netplaySessionIsReserved = netplaySessionIsReserved,
                        netplayQuality = quality
                    )
                }
                if (netplayFriendPickerVisible) {
                    activeMenuHandler = NetplayFriendPickerDialog(
                        friends = netplayFriendPickerEntries,
                        focusedIndex = netplayFriendPickerFocus,
                        onFocusChange = { netplayFriendPickerFocus = it },
                        onSelect = ::onNetplayFriendPicked,
                        onDismiss = { netplayFriendPickerVisible = false }
                    )
                }
                if (netplayDisconnectPromptVisible) {
                    activeMenuHandler = NetplayHostDisconnectPrompt(
                        peerDisplayName = netplayDisconnectPromptPeer,
                        focusedIndex = netplayDisconnectPromptFocus,
                        onFocusChange = { netplayDisconnectPromptFocus = it },
                        onKeepOpen = ::handleNetplayKeepSession,
                        onCloseAndEnd = ::handleNetplayCloseAfterDisconnect
                    )
                }
                if (netplayQualityWarningVisible) {
                    activeMenuHandler = NetplayQualityWarningPrompt(
                        rttMs = netplayQualityWarningRttMs,
                        jitterMs = netplayQualityWarningJitterMs,
                        ratingLabel = netplayQualityWarningLabel,
                        focusedIndex = netplayQualityWarningFocus,
                        onFocusChange = { netplayQualityWarningFocus = it },
                        onAccept = ::handleNetplayQualityAccept,
                        onDecline = ::handleNetplayQualityDecline
                    )
                }
                netplayProgressState?.let { progress ->
                    activeMenuHandler = NetplayConnectionProgressOverlay(
                        state = progress,
                        onDismiss = { netplayProgressState = null }
                    )
                }
                if (netplayReconnecting) {
                    NetplayReconnectingOverlay(lastRttMs = netplayLastRttMs)
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
                        loadAllowed = !netplayInSession
                    )
                }
                if (!isAnyMenuOpen) {
                    activeMenuHandler = null
                }
                androidx.compose.runtime.LaunchedEffect(isAnyMenuOpen) {
                    if (!isAnyMenuOpen && !netplayInSession) {
                        retroView.suppressAutoResume = false
                        retroView.resumeEmulation()
                    }
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
                    InGameMessageOverlay(
                        message = inGameMessage,
                        onDismiss = { inGameMessage = null },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 24.dp)
                    )
                }
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
            verifyRAGameIdUseCase = verifyRAGameIdUseCase,
            achievementUpdateBus = achievementUpdateBus,
            ambientLedManager = ambientLedManager,
            socialRepository = socialRepository,
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
                val bitmap = try { retroView.captureRawFrame() } catch (_: Exception) { null }
                inGameMessage = if (stateData != null && saveStateManager.performQuickSave(stateData, bitmap)) {
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
                pendingSaveScreenshot = try { retroView.captureRawFrame() } catch (_: Exception) { null }
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
            InGameMenuAction.Quit -> {
                performAutoSaveState()
                finish()
            }
            InGameMenuAction.OpenToFriends -> {
                hideMenu()
                handleNetplayOpenToFriends()
            }
            InGameMenuAction.InviteFriend -> {
                hideMenu()
                handleNetplayInviteFriend()
            }
            InGameMenuAction.ClearReservation -> {
                hideMenu()
                handleNetplayClearReservation()
            }
            InGameMenuAction.CloseNetplaySession -> {
                hideMenu()
                handleNetplayCloseSession()
            }
        }
    }

    private fun handleNetplayClearReservation() {
        val manager = netplaySessionManager ?: return
        lifecycleScope.launch {
            val ok = manager.reserveSession(null)
            if (ok) {
                netplaySessionIsReserved = false
                inGameMessage = "Session open to all friends"
            } else {
                inGameMessage = "Failed to clear reservation"
            }
        }
    }

    private fun initializeNetplaySessionManager() {
        val handshake = NetplayHandshake(
            candidateGatherer = CandidateGatherer(),
            socialService = argosSocialService
        )
        val rules = NetplaySessionRules(
            retroView = retroView,
            raSessionManager = { raSession },
            onFastForwardRelease = {
                if (isFastForwarding) {
                    isFastForwarding = false
                    retroView.frameSpeed = 1
                }
            }
        )
        netplaySessionRules = rules
        val manager = NetplaySessionManager(
            socialService = argosSocialService,
            handshake = handshake,
            retroView = retroView,
            sessionRules = rules
        )
        netplaySessionManager = manager
        lifecycleScope.launch {
            manager.sessionState.collect { state ->
                when (state) {
                    is NetplaySessionState.Connected -> {
                        if (isGuestJoinedSession) guestSessionEverStarted = true
                        val wasReconnecting = netplayReconnecting
                        if (!netplayInSession) {
                            savedOrientation = requestedOrientation
                            requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LOCKED
                        }
                        netplayInSession = true
                        netplayReconnecting = false
                        netplayDisconnectPromptVisible = false
                        val peerName = resolveFriendDisplayName(state.peerUserId)
                        netplayPeerDisplayName = peerName
                        val initRtt = manager.initialRttMs
                        val rttSuffix = if (initRtt != null) " -- ${initRtt}ms" else ""
                        if (netplayRole == NetplayMenuRole.Host &&
                            !wasReconnecting &&
                            lastJoinedToastPeerId != state.peerUserId
                        ) {
                            lastJoinedToastPeerId = state.peerUserId
                            inGameMessage = "$peerName joined your session$rttSuffix"
                        }
                        if (netplayProgressState?.stage != NetplayProgressStage.Failed) {
                            val label = NetplayQualityInfo.labelForRttMs(initRtt)
                            val readyMsg = if (initRtt != null) "Ready -- ${initRtt}ms [${label.name}]" else null
                            netplayProgressState = NetplayProgressState(NetplayProgressStage.Ready, readyMsg)
                            lastAnnouncedTier = if (initRtt != null) label else null
                            tierChangeTimestamp = System.currentTimeMillis()
                            delay(800)
                            if (netplayProgressState?.stage == NetplayProgressStage.Ready) {
                                netplayProgressState = null
                            }
                        }
                    }
                    is NetplaySessionState.Idle -> {
                        if (netplayInSession) {
                            requestedOrientation = savedOrientation
                        }
                        netplayInSession = false
                        netplayRole = null
                        netplaySessionIsReserved = false
                        lastJoinedToastPeerId = null
                        lastAnnouncedTier = null
                        netplayReconnecting = false
                        netplayDisconnectPromptVisible = false
                        if (isGuestJoinedSession && guestSessionEverStarted) {
                            // A joined session cannot continue without the host.
                            Log.d(TAG, "guest session ended; closing emulator activity")
                            finish()
                        }
                    }
                    is NetplaySessionState.Opening -> {
                        if (isGuestJoinedSession) guestSessionEverStarted = true
                        retroView.suppressAutoResume = false
                        retroView.resumeEmulation()
                        if (netplayRole == NetplayMenuRole.Guest) {
                            netplayProgressState = NetplayProgressState(NetplayProgressStage.RequestingJoin)
                        }
                    }
                    is NetplaySessionState.Handshaking -> {
                        if (isGuestJoinedSession) guestSessionEverStarted = true
                        retroView.suppressAutoResume = false
                        retroView.resumeEmulation()
                        netplayProgressState = NetplayProgressState(NetplayProgressStage.Connecting)
                    }
                    is NetplaySessionState.Reconnecting -> {
                        if (isGuestJoinedSession) guestSessionEverStarted = true
                        netplayReconnecting = true
                    }
                    is NetplaySessionState.PeerDisconnected -> {
                        if (isGuestJoinedSession) guestSessionEverStarted = true
                        netplayReconnecting = false
                        netplayDisconnectPromptPeer = resolveFriendDisplayName(state.peerUserId)
                        netplayDisconnectPromptFocus = 0
                        if (isGuestJoinedSession) {
                            netplayDisconnectPromptVisible = false
                            netplayProgressState = NetplayProgressState(
                                NetplayProgressStage.Failed,
                                "Disconnected from the session"
                            )
                            launch {
                                delay(1500)
                                finish()
                            }
                        } else {
                            netplayDisconnectPromptVisible = true
                        }
                    }
                    is NetplaySessionState.Error -> {
                        if (isGuestJoinedSession) guestSessionEverStarted = true
                        netplayProgressState = NetplayProgressState(
                            NetplayProgressStage.Failed,
                            netplayErrorMessage(state.reason)
                        )
                        if (isGuestJoinedSession) {
                            launch {
                                delay(2500)
                                finish()
                            }
                        }
                    }
                    else -> { }
                }
            }
        }
        lifecycleScope.launch {
            var candidateTier: NetplayQualityLabel? = null
            var candidateStartMs = 0L
            while (true) {
                kotlinx.coroutines.delay(500)
                val driver = manager.currentDriver()
                val rtt = driver?.lastRttNanos ?: 0L
                val rttMs = if (rtt > 0L) (rtt / 1_000_000L).toInt() else null
                netplayLastRttMs = rttMs
                if (!netplayInSession || rttMs == null) {
                    candidateTier = null
                    continue
                }
                val currentTier = NetplayQualityInfo.labelForRttMs(rttMs)
                if (currentTier != lastAnnouncedTier) {
                    if (currentTier != candidateTier) {
                        candidateTier = currentTier
                        candidateStartMs = System.currentTimeMillis()
                    } else if (System.currentTimeMillis() - candidateStartMs >= TIER_CHANGE_DEBOUNCE_MS) {
                        lastAnnouncedTier = currentTier
                        tierChangeTimestamp = System.currentTimeMillis()
                        inGameMessage = "Connection: ${currentTier.name} (${rttMs}ms)"
                    }
                } else {
                    candidateTier = null
                }
            }
        }
        lifecycleScope.launch {
            manager.guestLeftEvents.collect { event ->
                val name = resolveFriendDisplayName(event.guestUserId)
                inGameMessage = "$name left your session"
                netplayPeerDisplayName = "Friend"
                netplayLastRttMs = null
            }
        }
        lifecycleScope.launch {
            manager.qualityWarningPending.collect { warning ->
                if (warning != null) {
                    netplayQualityWarningRttMs = warning.measuredRttMs
                    netplayQualityWarningJitterMs = warning.measuredJitterMs
                    netplayQualityWarningLabel = warning.ratingLabel
                    netplayQualityWarningFocus = 0
                    netplayQualityWarningVisible = true
                } else {
                    netplayQualityWarningVisible = false
                }
            }
        }

        lifecycleScope.launch {
            manager.progressHint.collect { hint ->
                val stage = when (hint) {
                    NetplaySessionManager.ProgressHint.WaitingForHost -> NetplayProgressStage.WaitingForHost
                    NetplaySessionManager.ProgressHint.Measuring -> NetplayProgressStage.Measuring
                    NetplaySessionManager.ProgressHint.LoadingState -> NetplayProgressStage.LoadingState
                    null -> null
                }
                if (stage != null && netplayProgressState?.stage != NetplayProgressStage.Failed) {
                    netplayProgressState = NetplayProgressState(stage)
                }
            }
        }

        val pending = pendingNetplayJoin
        if (pending != null) {
            pendingNetplayJoin = null
            netplayRole = NetplayMenuRole.Guest
            lifecycleScope.launch {
                runCatching {
                    manager.joinSession(pending.sessionId, pending.hostUserId)
                }.onFailure { err ->
                    Log.w(TAG, "auto-join from intent failed: ${err.message}")
                }
            }
        }
    }

    private fun resolveFriendDisplayName(userId: String): String {
        return socialRepository.friends.value.firstOrNull { it.id == userId }?.displayName ?: "Friend"
    }

    private fun netplayErrorMessage(reason: String): String = when (reason) {
        "protocol_version_mismatch" -> "Update Argosy to join this netplay session."
        "rate_limited" -> "You've started too many netplay sessions recently. Please wait a few minutes."
        "send_failed" -> "Couldn't reach Argosy. Check your connection."
        "ready_timeout", "handshake_timeout" -> "Connection timed out. Try again."
        "candidate_pair_failed" -> "Couldn't establish a direct connection with your friend."
        "quality_rejected" -> "Connection quality too poor for netplay."
        "no_candidates" -> "No network paths available for netplay."
        "not_found" -> "That session is no longer available."
        "not_friend" -> "You can only join sessions from friends."
        "session_full" -> "That session is full."
        "already_open" -> "You already have an active netplay session."
        "already_filled", "already_joined" -> "Someone else already joined that session."
        "disabled" -> "Netplay is currently disabled on the server."
        "core_unsupported" -> "This core doesn't support netplay."
        "self_join" -> "You can't join your own session."
        "host_install_failed", "guest_install_failed" -> "Couldn't start the netplay session."
        "socket_bind_failed" -> "Couldn't open a network socket for netplay."
        "invalid_payload", "invalid_state" -> "Netplay request was rejected by the server."
        "db_error", "internal_error" -> "A server error occurred. Try again shortly."
        else -> "Couldn't connect: $reason"
    }

    private fun isNetplayCoreSupported(): Boolean {
        val coreId = resolvedCoreId ?: return false
        val core = LibretroCoreRegistry.getCoreById(coreId) ?: return false
        return core.netplaySupport == NetplaySupportLevel.SUPPORTED
    }

    private fun buildNetplayOpenPayload(): NetplayOpenPayload? {
        val coreId = resolvedCoreId ?: return null
        val romFile = File(romPath)
        val romHash = com.nendo.argosy.data.netplay.RomHashComputer.computeRomHashPrefix(romFile) ?: return null
        val coreHash = coreHashCache.getHashForCore(corePath) ?: return null
        return NetplayOpenPayload(
            gameIgdbId = null,
            gameTitle = gameName,
            coreId = coreId,
            romHashPrefix = romHash,
            coreHash = coreHash
        )
    }

    private fun handleNetplayOpenToFriends() {
        val manager = netplaySessionManager ?: run {
            inGameMessage = "Netplay manager unavailable"
            return
        }
        val payload = buildNetplayOpenPayload() ?: run {
            inGameMessage = "Netplay: failed to compute hashes"
            return
        }
        netplayRole = NetplayMenuRole.Host
        retroView.suppressAutoResume = false
        retroView.resumeEmulation()
        lifecycleScope.launch {
            val result = manager.openServer(payload)
            inGameMessage = result.fold(
                onSuccess = { _ -> "Netplay session opened to friends" },
                onFailure = { e ->
                    netplayRole = null
                    "Netplay open failed: ${e.message}"
                }
            )
        }
    }

    private fun handleNetplayInviteFriend() {
        if (netplaySessionManager == null) {
            inGameMessage = "Netplay manager unavailable"
            return
        }
        val onlineFriends = socialRepository.friends.value
            .asSequence()
            .filter { it.friendshipStatus == com.nendo.argosy.data.social.FriendshipStatus.ACCEPTED }
            .filter { it.presence != null && it.presence != com.nendo.argosy.data.social.PresenceStatus.OFFLINE }
            .map { friend ->
                NetplayFriendPickerEntry(
                    userId = friend.id,
                    displayName = friend.displayName,
                    avatarColorHex = friend.avatarColor,
                    isOnline = true
                )
            }
            .toList()
        netplayFriendPickerEntries = onlineFriends
        netplayFriendPickerFocus = 0
        netplayFriendPickerVisible = true
    }

    private fun onNetplayFriendPicked(friend: NetplayFriendPickerEntry) {
        netplayFriendPickerVisible = false
        val manager = netplaySessionManager ?: return
        val state = manager.sessionState.value
        lifecycleScope.launch {
            if (state is NetplaySessionState.Waiting || state is NetplaySessionState.Connected) {
                val ok = manager.reserveSession(friend.userId)
                if (ok) netplaySessionIsReserved = true
                inGameMessage = if (ok) "Invited ${friend.displayName}" else "Invite failed"
            } else {
                val payload = buildNetplayOpenPayload() ?: run {
                    inGameMessage = "Netplay: failed to compute hashes"
                    return@launch
                }
                netplayRole = NetplayMenuRole.Host
                val result = manager.openServer(payload)
                result.fold(
                    onSuccess = {
                        val reserved = manager.reserveSession(friend.userId)
                        if (reserved) netplaySessionIsReserved = true
                        inGameMessage = if (reserved) "Invited ${friend.displayName}" else "Session opened; invite failed"
                    },
                    onFailure = { e ->
                        netplayRole = null
                        inGameMessage = "Netplay open failed: ${e.message}"
                    }
                )
            }
        }
    }

    private fun handleNetplayQualityAccept() {
        netplayQualityWarningVisible = false
        netplaySessionManager?.acceptQualityWarning()
    }

    private fun handleNetplayQualityDecline() {
        netplayQualityWarningVisible = false
        netplaySessionManager?.declineQualityWarning()
    }

    private fun handleNetplayKeepSession() {
        netplayDisconnectPromptVisible = false
        val manager = netplaySessionManager ?: return
        lifecycleScope.launch { manager.onHostKeepSession() }
    }

    private fun handleNetplayCloseAfterDisconnect() {
        netplayDisconnectPromptVisible = false
        val manager = netplaySessionManager ?: return
        lifecycleScope.launch {
            manager.onHostCloseAfterDisconnect()
            finish()
        }
    }

    private fun handleNetplayCloseSession() {
        val manager = netplaySessionManager ?: return
        val role = netplayRole
        lifecycleScope.launch {
            val state = manager.sessionState.value
            if (state is NetplaySessionState.Waiting ||
                state is NetplaySessionState.Connected ||
                state is NetplaySessionState.Handshaking) {
                if (role == NetplayMenuRole.Guest) manager.leaveSession() else manager.closeServer()
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
        if (hardcoreMode || !coreLoadedSuccessfully || !statesSupported || !autoSaveEnabled) return
        try {
            val bitmap = try { retroView.captureRawFrame() } catch (_: Exception) { null }
            val stateData = retroView.serializeState()
            saveStateManager.performSlotSave(SaveStateManager.AUTO_SLOT, stateData, bitmap)
            bitmap?.recycle()
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
            InGameControlsAction.ShowControllerOrder,
            InGameControlsAction.ShowInputMapping,
            InGameControlsAction.ShowHotkeys -> {}
        }
    }

    private fun showMenu() {
        if (!netplayInSession) {
            retroView.pauseEmulation()
            retroView.suppressAutoResume = true
        }
        menuFocusIndex = 0
        menuVisible = true
    }

    private fun hideMenu() {
        menuVisible = false
        if (!netplayInSession) {
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

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (isAnyMenuOpen) return super.onKeyDown(keyCode, event)

        val controllerId = event.device?.let { getControllerId(it) }
        val triggeredAction = inputConfig.hotkeyManager.onKeyDown(keyCode, controllerId)
        if (triggeredAction != null) {
            return hotkeyDispatcher.dispatch(triggeredAction)
        }

        if (shouldFilterShoulderButton(keyCode)) return true

        val handled = retroView.onKeyDown(keyCode, event)
        return handled || super.onKeyDown(keyCode, event)
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
            retroView.isRewinding = false
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
        gracefullyEndNetplaySessionIfActive()
        if (coreLoadedSuccessfully) {
            performAutoSaveState()
            if (!isGuestJoinedSession) {
                // guest session SRAM is ephemeral and must not touch the persistent file
                saveStateManager.saveSram(retroView)
            }
            if (isFinishing) {
                retroView.destroyNative()
            }
            retroView.onPause()
        }
        super.onPause()
    }

    private fun gracefullyEndNetplaySessionIfActive() {
        val manager = netplaySessionManager ?: return
        val state = manager.sessionState.value
        if (state is NetplaySessionState.Idle) return
        kotlinx.coroutines.runBlocking {
            kotlinx.coroutines.withTimeoutOrNull(NETPLAY_CLOSE_TIMEOUT_MS) {
                if (netplayRole == NetplayMenuRole.Guest) manager.leaveSession() else manager.closeServer()
            }
        }
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy: isFinishing=$isFinishing, isChangingConfigurations=$isChangingConfigurations")
        abandonAudioFocus()
        com.nendo.argosy.DualScreenManagerHolder.instance?.let { dsm ->
            dsm.emulatorKeyDispatcher = null
            dsm.emulatorMotionDispatcher = null
        }
        netplaySessionManager?.shutdown()
        netplaySessionManager = null
        raSession?.destroy()
        if (isFinishing && gameId != -1L) {
            com.nendo.argosy.DualScreenManagerHolder.instance
                ?.onSessionChanged(-1L)
            playSessionTracker.endSessionInBackground()
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

        if (isL1R1 && platformSlug in PLATFORMS_WITHOUT_SHOULDERS) return true
        if (isL2R2 && platformSlug !in PLATFORMS_WITH_L2_R2) return true

        return false
    }

    companion object {
        private const val TAG = "LibretroActivity"
        private const val NETPLAY_CLOSE_TIMEOUT_MS = 500L
        private const val TIER_CHANGE_DEBOUNCE_MS = 2000L

        const val EXTRA_ROM_PATH = "rom_path"
        const val EXTRA_CORE_PATH = "core_path"
        const val EXTRA_SYSTEM_DIR = "system_dir"
        const val EXTRA_GAME_NAME = "game_name"
        const val EXTRA_GAME_ID = "game_id"
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
    }
}
