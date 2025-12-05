# Emulator Launching

## Launch Strategy

### Hybrid Approach

| System Category | Launch Method | Examples |
|-----------------|---------------|----------|
| 8-bit / 16-bit | Built-in (Phase 2) | NES, SNES, Genesis, GB/GBA |
| 32-bit Retro | Built-in (Phase 2) | PS1, N64 |
| Heavy / Modern | External always | PS2, PSP, GC, Wii, 3DS, Switch |

### Phase 1: External Only

All games launch via Android Intents to external emulator apps.

### Phase 2: Libretro Integration

Retro systems use embedded libretro cores for seamless experience.

## External Emulator Launch

### Intent Architecture

```kotlin
class ExternalEmulatorLauncher(
    private val context: Context,
    private val configRepository: EmulatorConfigRepository
) {
    suspend fun launch(game: Game): LaunchResult {
        val config = configRepository.getConfigFor(game)
            ?: return LaunchResult.NoEmulatorConfigured

        val romUri = resolveRomUri(game)

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setPackage(config.packageName)
            data = romUri
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            // Emulator-specific extras
            applyEmulatorExtras(config)
        }

        return try {
            context.startActivity(intent)
            LaunchResult.Success
        } catch (e: ActivityNotFoundException) {
            LaunchResult.EmulatorNotInstalled(config.packageName)
        }
    }
}
```

### Emulator Configurations

```kotlin
data class EmulatorConfig(
    val packageName: String,
    val displayName: String,
    val intentAction: String,
    val activityName: String?,
    val coreName: String?,           // For RetroArch
    val extraArgs: Map<String, Any>,
    val supportedPlatforms: List<String>
)

// Pre-defined configurations
val knownEmulators = listOf(
    // RetroArch (multi-system)
    EmulatorConfig(
        packageName = "com.retroarch",
        displayName = "RetroArch",
        intentAction = Intent.ACTION_VIEW,
        activityName = null,
        coreName = null,  // Set per platform
        extraArgs = emptyMap(),
        supportedPlatforms = listOf("*")
    ),

    // Standalone emulators
    EmulatorConfig(
        packageName = "org.ppsspp.ppsspp",
        displayName = "PPSSPP",
        intentAction = Intent.ACTION_VIEW,
        supportedPlatforms = listOf("psp")
    ),
    EmulatorConfig(
        packageName = "org.dolphinemu.dolphinemu",
        displayName = "Dolphin",
        intentAction = Intent.ACTION_VIEW,
        supportedPlatforms = listOf("gc", "wii")
    ),
    EmulatorConfig(
        packageName = "dev.AUF.aethersx2",
        displayName = "AetherSX2",
        intentAction = Intent.ACTION_VIEW,
        supportedPlatforms = listOf("ps2")
    )
)
```

### RetroArch Core Mapping

```kotlin
val retroArchCores = mapOf(
    "nes"     to "fceumm_libretro_android.so",
    "snes"    to "snes9x_libretro_android.so",
    "genesis" to "genesis_plus_gx_libretro_android.so",
    "gba"     to "mgba_libretro_android.so",
    "gb"      to "gambatte_libretro_android.so",
    "psx"     to "pcsx_rearmed_libretro_android.so",
    "n64"     to "mupen64plus_next_libretro_android.so"
)

fun buildRetroArchIntent(game: Game, romUri: Uri): Intent {
    val core = retroArchCores[game.platformId]

    return Intent(Intent.ACTION_VIEW).apply {
        setPackage("com.retroarch")
        data = romUri
        putExtra("LIBRETRO", core)
        putExtra("CONFIGFILE", "/storage/emulated/0/RetroArch/config/retroarch.cfg")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}
```

---

## Per-Game Emulator Override

### Use Case
Some games run better on specific emulators:
- **Switch**: Yuzu vs Skyline vs Citron - compatibility varies by game
- **PS2**: AetherSX2 vs Play! - different performance profiles
- **N64**: Mupen64Plus vs ParaLLEl - accuracy vs speed tradeoffs

### Resolution Order

```
1. Game-specific override (if set)
       |
       v
2. Platform default emulator
       |
       v
3. Global fallback (RetroArch)
```

### Database Model

```kotlin
@Entity(tableName = "emulator_configs")
data class EmulatorConfigEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val platformId: String?,      // null = global default
    val gameId: Long?,            // null = platform default, set = per-game
    val packageName: String,
    val coreName: String?,        // For RetroArch
    val isDefault: Boolean
)

// Query priority
suspend fun getEmulatorForGame(game: Game): EmulatorConfig? {
    // 1. Check game-specific
    gameOverride = dao.getByGameId(game.id)
    if (gameOverride != null) return gameOverride

    // 2. Check platform default
    platformDefault = dao.getDefaultForPlatform(game.platformId)
    if (platformDefault != null) return platformDefault

    // 3. Global fallback
    return dao.getGlobalDefault()
}
```

### UI Access Points

**Game Details View** (SELECT > More Options):
```
+------------------------------------------+
|  MORE OPTIONS                            |
+------------------------------------------+
|  > Change Emulator        [AetherSX2]    |  <-- Shows current
|  > Rescrape Metadata                     |
|  ...                                     |
+------------------------------------------+
```

**Change Emulator Dialog**:
```
+------------------------------------------+
|  SELECT EMULATOR FOR THIS GAME           |
+------------------------------------------+
|  (*) Use platform default (AetherSX2)    |  <-- Resets to system setting
|  ─────────────────────────────────────   |
|  ( ) AetherSX2              [Installed]  |
|  ( ) Play!                  [Installed]  |
|  ( ) PCSX2               [Not Installed] |
+------------------------------------------+
|  [A] Confirm    [B] Cancel               |
+------------------------------------------+
```

- Selecting specific emulator = per-game override (stored in DB)
- Selecting "Use platform default" = deletes override row, falls back to platform setting

**Library Quick Menu** (SELECT on game):
```
+------------------------------------------+
|  FINAL FANTASY X                         |
+------------------------------------------+
|  > Play                                  |
|  > Edit Metadata                         |
|  > Change Emulator        [AetherSX2]    |  <-- Quick access
|  > Toggle Favorite                       |
|  ...                                     |
+------------------------------------------+
```

### Visual Indicator

Games with custom emulator show a badge:

```
+--------+
|        |
|  Game  |
|   [E]  |  <-- Small badge indicating override
+--------+
```

---

### Launch Result Handling

```kotlin
sealed interface LaunchResult {
    object Success : LaunchResult
    object NoEmulatorConfigured : LaunchResult
    data class EmulatorNotInstalled(val packageName: String) : LaunchResult
    data class FileNotFound(val path: String) : LaunchResult
    data class PermissionDenied(val uri: Uri) : LaunchResult
}

// UI response
when (val result = launcher.launch(game)) {
    is LaunchResult.Success -> {
        // Track play session
        gameRepository.recordPlayStart(game.id)
    }
    is LaunchResult.EmulatorNotInstalled -> {
        // Show dialog: "Install {emulator}?" with Play Store link
    }
    is LaunchResult.NoEmulatorConfigured -> {
        // Navigate to emulator setup for this platform
    }
}
```

## Built-in Libretro (Phase 2)

### Architecture

```
Kotlin Layer
+----------------------------------+
|  CoreManager                     |
|  - loadCore(platform)            |
|  - loadGame(path)                |
|  - runFrame()                    |
|  - saveState() / loadState()     |
+----------------------------------+
              | JNI
              v
Native Layer (C++)
+----------------------------------+
|  libretro_bridge.cpp             |
|  - Core loading (dlopen)         |
|  - Callback implementation       |
|  - Video/Audio/Input bridges     |
+----------------------------------+
              |
              v
+----------------------------------+
|  Libretro Core (.so)             |
|  snes9x, mgba, genesis_plus_gx   |
+----------------------------------+
```

### JNI Bridge

```kotlin
// Kotlin interface
object LibretroBridge {
    init { System.loadLibrary("retro_bridge") }

    external fun loadCore(corePath: String): Boolean
    external fun unloadCore()
    external fun loadGame(gamePath: String): Boolean
    external fun runFrame()
    external fun setSurface(surface: Surface)
    external fun setInputState(port: Int, button: Int, pressed: Boolean)
    external fun saveState(): ByteArray?
    external fun loadState(data: ByteArray): Boolean
    external fun pause()
    external fun resume()
}
```

```cpp
// Native implementation (simplified)
extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_alauncher_libretro_LibretroBridge_loadCore(
    JNIEnv* env, jobject, jstring corePath) {

    const char* path = env->GetStringUTFChars(corePath, nullptr);
    g_core_handle = dlopen(path, RTLD_LAZY);

    // Load required symbols
    g_retro_init = dlsym(g_core_handle, "retro_init");
    g_retro_load_game = dlsym(g_core_handle, "retro_load_game");
    g_retro_run = dlsym(g_core_handle, "retro_run");
    // ... more symbols

    // Set up callbacks
    g_retro_set_video_refresh(video_callback);
    g_retro_set_audio_sample_batch(audio_callback);
    g_retro_set_input_state(input_callback);

    g_retro_init();
    return JNI_TRUE;
}

}
```

### Emulation Screen

```kotlin
@Composable
fun EmulatorScreen(
    gameId: Long,
    viewModel: EmulatorViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    DisposableEffect(gameId) {
        viewModel.startGame(gameId)
        onDispose { viewModel.stopGame() }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // OpenGL surface for rendering
        AndroidView(
            factory = { EmulatorSurfaceView(it) },
            modifier = Modifier.fillMaxSize()
        )

        // Quick menu overlay (triggered by button combo)
        if (state.showQuickMenu) {
            EmulatorQuickMenu(
                onResume = { viewModel.resume() },
                onSaveState = { viewModel.saveState() },
                onLoadState = { viewModel.loadState() },
                onExit = { viewModel.exit() }
            )
        }
    }
}
```

### Input Mapping for Libretro

```kotlin
// Map Android gamepad to libretro buttons
val inputMapping = mapOf(
    KeyEvent.KEYCODE_BUTTON_A to RETRO_DEVICE_ID_JOYPAD_B,
    KeyEvent.KEYCODE_BUTTON_B to RETRO_DEVICE_ID_JOYPAD_A,
    KeyEvent.KEYCODE_BUTTON_X to RETRO_DEVICE_ID_JOYPAD_Y,
    KeyEvent.KEYCODE_BUTTON_Y to RETRO_DEVICE_ID_JOYPAD_X,
    KeyEvent.KEYCODE_DPAD_UP to RETRO_DEVICE_ID_JOYPAD_UP,
    KeyEvent.KEYCODE_DPAD_DOWN to RETRO_DEVICE_ID_JOYPAD_DOWN,
    KeyEvent.KEYCODE_DPAD_LEFT to RETRO_DEVICE_ID_JOYPAD_LEFT,
    KeyEvent.KEYCODE_DPAD_RIGHT to RETRO_DEVICE_ID_JOYPAD_RIGHT,
    KeyEvent.KEYCODE_BUTTON_L1 to RETRO_DEVICE_ID_JOYPAD_L,
    KeyEvent.KEYCODE_BUTTON_R1 to RETRO_DEVICE_ID_JOYPAD_R,
    KeyEvent.KEYCODE_BUTTON_START to RETRO_DEVICE_ID_JOYPAD_START,
    KeyEvent.KEYCODE_BUTTON_SELECT to RETRO_DEVICE_ID_JOYPAD_SELECT
)
```

## Emulator Configuration UI

### Per-Platform Setup

```
Settings > Emulators > [Platform]

+------------------------------------------+
| Super Nintendo                           |
+------------------------------------------+
| Default Emulator: [RetroArch v]          |
|                                          |
| RetroArch Core: [Snes9x v]               |
|                                          |
| [ ] Use built-in emulation (Phase 2)     |
+------------------------------------------+
| Installed Emulators:                     |
| (*) RetroArch                            |
| ( ) Snes9x EX+                          |
+------------------------------------------+
```

### Per-Game Override

```
Game Detail > Settings

+------------------------------------------+
| Emulator Override                        |
+------------------------------------------+
| ( ) Use platform default                 |
| (*) Specific emulator: [PPSSPP v]        |
+------------------------------------------+
```

## Play Session Tracking

```kotlin
class PlaySessionTracker(
    private val gameRepository: GameRepository
) {
    private var currentGameId: Long? = null
    private var sessionStart: Long = 0

    fun startSession(gameId: Long) {
        currentGameId = gameId
        sessionStart = System.currentTimeMillis()
        gameRepository.updateLastPlayed(gameId, sessionStart)
        gameRepository.incrementPlayCount(gameId)
    }

    fun endSession() {
        currentGameId?.let { gameId ->
            val duration = (System.currentTimeMillis() - sessionStart) / 60000
            gameRepository.addPlayTime(gameId, duration.toInt())
        }
        currentGameId = null
    }
}
```
