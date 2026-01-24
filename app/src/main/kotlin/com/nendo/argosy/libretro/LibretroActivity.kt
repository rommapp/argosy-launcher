package com.nendo.argosy.libretro

import android.os.Bundle
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
import com.nendo.argosy.libretro.ui.InGameMenu
import com.nendo.argosy.libretro.ui.InGameMenuAction
import com.nendo.argosy.ui.theme.ALauncherTheme
import com.swordfish.libretrodroid.GLRetroView
import com.swordfish.libretrodroid.GLRetroViewData
import dagger.hilt.android.AndroidEntryPoint
import java.io.File

@AndroidEntryPoint
class LibretroActivity : ComponentActivity() {
    private lateinit var retroView: GLRetroView
    private lateinit var statesDir: File
    private lateinit var romPath: String

    private var startPressed = false
    private var selectPressed = false
    private var menuVisible by mutableStateOf(false)
    private var hasQuickSave by mutableStateOf(false)
    private var gameName: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        enterImmersiveMode()

        romPath = intent.getStringExtra(EXTRA_ROM_PATH) ?: return finish()
        val corePath = intent.getStringExtra(EXTRA_CORE_PATH) ?: return finish()
        val systemPath = intent.getStringExtra(EXTRA_SYSTEM_DIR)
        gameName = intent.getStringExtra(EXTRA_GAME_NAME) ?: File(romPath).nameWithoutExtension

        val systemDir = if (systemPath != null) File(systemPath) else File(filesDir, "libretro/system")
        systemDir.mkdirs()
        val savesDir = File(filesDir, "libretro/saves").apply { mkdirs() }
        statesDir = File(filesDir, "libretro/states").apply { mkdirs() }

        hasQuickSave = getQuickSaveFile().exists()

        retroView = GLRetroView(
            this,
            GLRetroViewData(this).apply {
                coreFilePath = corePath
                gameFilePath = romPath
                systemDirectory = systemDir.absolutePath
                savesDirectory = savesDir.absolutePath
            }
        )

        lifecycle.addObserver(retroView)
        retroView.audioEnabled = true

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
    }

    private fun getQuickSaveFile(): File {
        val romName = File(romPath).nameWithoutExtension
        return File(statesDir, "$romName.state")
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
        retroView.onPause()
        super.onPause()
    }

    companion object {
        const val EXTRA_ROM_PATH = "rom_path"
        const val EXTRA_CORE_PATH = "core_path"
        const val EXTRA_SYSTEM_DIR = "system_dir"
        const val EXTRA_GAME_NAME = "game_name"
    }
}
