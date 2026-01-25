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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.nendo.argosy.data.emulator.EmulatorRegistry
import com.nendo.argosy.data.emulator.PlaySessionTracker
import com.nendo.argosy.data.preferences.BuiltinEmulatorSettings
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.libretro.ui.InGameMenu
import com.nendo.argosy.libretro.ui.InGameMenuAction
import com.nendo.argosy.ui.theme.ALauncherTheme
import com.swordfish.libretrodroid.GLRetroView
import com.swordfish.libretrodroid.GLRetroViewData
import com.swordfish.libretrodroid.ShaderConfig
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

    private lateinit var retroView: GLRetroView
    private var vibrator: Vibrator? = null
    private lateinit var statesDir: File
    private lateinit var savesDir: File
    private lateinit var romPath: String

    private var gameId: Long = -1L
    private var coreName: String? = null
    private var startPressed = false
    private var selectPressed = false
    private var menuVisible by mutableStateOf(false)
    private var hasQuickSave by mutableStateOf(false)
    private var gameName: String = ""
    private var lastSramHash: String? = null
    private var aspectRatioMode: String = "Auto"
    private var screenWidth: Int = 0
    private var screenHeight: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        enterImmersiveMode()

        romPath = intent.getStringExtra(EXTRA_ROM_PATH) ?: return finish()
        val corePath = intent.getStringExtra(EXTRA_CORE_PATH) ?: return finish()
        val systemPath = intent.getStringExtra(EXTRA_SYSTEM_DIR)
        gameName = intent.getStringExtra(EXTRA_GAME_NAME) ?: File(romPath).nameWithoutExtension
        gameId = intent.getLongExtra(EXTRA_GAME_ID, -1L)
        coreName = intent.getStringExtra(EXTRA_CORE_NAME)

        val systemDir = if (systemPath != null) File(systemPath) else File(filesDir, "libretro/system")
        systemDir.mkdirs()
        savesDir = File(filesDir, "libretro/saves").apply { mkdirs() }
        statesDir = File(filesDir, "libretro/states").apply { mkdirs() }

        hasQuickSave = getQuickSaveFile().exists()

        val existingSram = getSramFile().takeIf { it.exists() }?.readBytes()
        lastSramHash = existingSram?.let { hashBytes(it) }

        val settings = kotlinx.coroutines.runBlocking {
            preferencesRepository.getBuiltinEmulatorSettings().first()
        }
        aspectRatioMode = settings.aspectRatio

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
            }
        )

        lifecycle.addObserver(retroView)
        retroView.audioEnabled = true
        retroView.filterMode = settings.filterMode
        retroView.blackFrameInsertion = settings.blackFrameInsertion

        if (settings.rumbleEnabled) {
            setupRumble()
        }

        val container = FrameLayout(this).apply {
            addView(retroView, FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)

            addView(
                ComposeView(this@LibretroActivity).apply {
                    setContent {
                        ALauncherTheme {
                            if (menuVisible) {
                                InGameMenu(
                                    gameName = gameName,
                                    hasQuickSave = hasQuickSave,
                                    onAction = ::handleMenuAction
                                )
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
            }, 500)
        }

        if (gameId != -1L) {
            playSessionTracker.startSession(gameId, EmulatorRegistry.BUILTIN_PACKAGE, coreName)
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

    private fun getQuickSaveFile(): File {
        val romName = File(romPath).nameWithoutExtension
        return File(statesDir, "$romName.state")
    }

    private fun getSramFile(): File {
        val romName = File(romPath).nameWithoutExtension
        return File(savesDir, "$romName.srm")
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
            InGameMenuAction.Quit -> finish()
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
        menuVisible = true
    }

    private fun hideMenu() {
        menuVisible = false
        retroView.onResume()
    }

    private fun enterImmersiveMode() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (menuVisible) {
            return super.onKeyDown(keyCode, event)
        }

        when (keyCode) {
            KeyEvent.KEYCODE_BUTTON_START -> startPressed = true
            KeyEvent.KEYCODE_BUTTON_SELECT -> selectPressed = true
        }

        if (startPressed && selectPressed) {
            showMenu()
            startPressed = false
            selectPressed = false
            return true
        }

        return retroView.onKeyDown(keyCode, event) || super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (menuVisible) {
            return super.onKeyUp(keyCode, event)
        }

        when (keyCode) {
            KeyEvent.KEYCODE_BUTTON_START -> startPressed = false
            KeyEvent.KEYCODE_BUTTON_SELECT -> selectPressed = false
        }

        return retroView.onKeyUp(keyCode, event) || super.onKeyUp(keyCode, event)
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (menuVisible) {
            return super.onGenericMotionEvent(event)
        }
        return retroView.onGenericMotionEvent(event) || super.onGenericMotionEvent(event)
    }

    override fun onResume() {
        super.onResume()
        if (!menuVisible) {
            retroView.onResume()
        }
    }

    override fun onPause() {
        saveSram()
        retroView.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        if (isFinishing && gameId != -1L) {
            playSessionTracker.endSession()
        }
        super.onDestroy()
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
