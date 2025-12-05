# Input Handling

Handle gamepad, D-pad, touch, and controller input for handheld/TV devices.

---

## Input Modes

Handheld devices like Retroid Pocket support both touch AND gamepad. Handle both:

| Mode | Input Type | Focus Behavior |
|------|------------|----------------|
| Touch | Tap, swipe, long-press | Focus follows touch |
| Gamepad | D-pad, buttons, sticks | Focus follows D-pad navigation |
| Hybrid | Both simultaneously | Last input type wins |

---

## Quick Reference

| Key Code | Common Mapping |
|----------|----------------|
| `KEYCODE_DPAD_UP/DOWN/LEFT/RIGHT` | D-pad navigation |
| `KEYCODE_DPAD_CENTER` | D-pad center press (confirm) |
| `KEYCODE_BUTTON_A` | Confirm/Select |
| `KEYCODE_BUTTON_B` | Back/Cancel |
| `KEYCODE_BUTTON_X` | Secondary action |
| `KEYCODE_BUTTON_Y` | Context menu |
| `KEYCODE_BUTTON_L1` | Left shoulder |
| `KEYCODE_BUTTON_R1` | Right shoulder |
| `KEYCODE_BUTTON_L2` | Left trigger (digital) |
| `KEYCODE_BUTTON_R2` | Right trigger (digital) |
| `KEYCODE_BUTTON_START` | Menu/Pause |
| `KEYCODE_BUTTON_SELECT` | Select/Options |

---

## Input Sources

| Source | Description |
|--------|-------------|
| `SOURCE_GAMEPAD` | Has gamepad buttons (A/B/X/Y) |
| `SOURCE_DPAD` | Has D-pad buttons |
| `SOURCE_JOYSTICK` | Has analog sticks |

Some controllers report D-pad events as `SOURCE_JOYSTICK` via `AXIS_HAT_X/Y`.

---

## Basic KeyEvent Handling

```kotlin
class MainActivity : ComponentActivity() {

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (event.repeatCount == 0) { // Ignore held keys
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_UP -> { handleUp(); return true }
                KeyEvent.KEYCODE_DPAD_DOWN -> { handleDown(); return true }
                KeyEvent.KEYCODE_DPAD_LEFT -> { handleLeft(); return true }
                KeyEvent.KEYCODE_DPAD_RIGHT -> { handleRight(); return true }
                KeyEvent.KEYCODE_BUTTON_A,
                KeyEvent.KEYCODE_DPAD_CENTER,
                KeyEvent.KEYCODE_ENTER -> { handleConfirm(); return true }
                KeyEvent.KEYCODE_BUTTON_B,
                KeyEvent.KEYCODE_BACK -> { handleBack(); return true }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        // Handle key releases if needed (e.g., for held actions)
        return super.onKeyUp(keyCode, event)
    }
}
```

---

## Verify Input Source

Check that input comes from a game controller:

```kotlin
override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
    val isGamepad = event.source and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD
    val isDpad = event.source and InputDevice.SOURCE_DPAD == InputDevice.SOURCE_DPAD

    if (isGamepad || isDpad) {
        // Handle gamepad input
        return true
    }
    return super.onKeyDown(keyCode, event)
}
```

---

## Detect Connected Controllers

```kotlin
fun getGameControllerIds(): List<Int> {
    return InputDevice.getDeviceIds().filter { deviceId ->
        InputDevice.getDevice(deviceId)?.let { device ->
            val sources = device.sources
            (sources and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD) ||
            (sources and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK)
        } ?: false
    }
}
```

---

## Analog Stick Support

Override `onGenericMotionEvent` for analog stick input:

```kotlin
override fun onGenericMotionEvent(event: MotionEvent): Boolean {
    if (event.source and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK
        && event.action == MotionEvent.ACTION_MOVE) {

        // Process all historical values first
        for (i in 0 until event.historySize) {
            processJoystickInput(event, i)
        }
        // Process current value
        processJoystickInput(event, -1)
        return true
    }
    return super.onGenericMotionEvent(event)
}

private fun processJoystickInput(event: MotionEvent, historyPos: Int) {
    val device = event.device ?: return

    // Left stick
    var x = getCenteredAxis(event, device, MotionEvent.AXIS_X, historyPos)
    var y = getCenteredAxis(event, device, MotionEvent.AXIS_Y, historyPos)

    // Fallback to HAT axis (some controllers use this for D-pad)
    if (x == 0f) x = getCenteredAxis(event, device, MotionEvent.AXIS_HAT_X, historyPos)
    if (y == 0f) y = getCenteredAxis(event, device, MotionEvent.AXIS_HAT_Y, historyPos)

    // Right stick (optional)
    val rx = getCenteredAxis(event, device, MotionEvent.AXIS_Z, historyPos)
    val ry = getCenteredAxis(event, device, MotionEvent.AXIS_RZ, historyPos)

    // Convert to directional input
    if (x < -0.5f) handleLeft()
    else if (x > 0.5f) handleRight()
    if (y < -0.5f) handleUp()
    else if (y > 0.5f) handleDown()
}
```

---

## Joystick Deadzone Handling

Account for stick drift by checking against the "flat" region:

```kotlin
private fun getCenteredAxis(
    event: MotionEvent,
    device: InputDevice,
    axis: Int,
    historyPos: Int
): Float {
    val range = device.getMotionRange(axis, event.source) ?: return 0f
    val flat = range.flat

    val value = if (historyPos < 0) {
        event.getAxisValue(axis)
    } else {
        event.getHistoricalAxisValue(axis, historyPos)
    }

    // Ignore values within the deadzone
    return if (abs(value) > flat) value else 0f
}
```

---

## D-Pad Helper Class

Handle D-pad from both KeyEvent and MotionEvent:

```kotlin
class DpadHelper {
    companion object {
        const val UP = 0
        const val DOWN = 1
        const val LEFT = 2
        const val RIGHT = 3
        const val CENTER = 4

        fun getDirection(event: InputEvent): Int {
            return when (event) {
                is KeyEvent -> getDirectionFromKey(event)
                is MotionEvent -> getDirectionFromMotion(event)
                else -> -1
            }
        }

        private fun getDirectionFromKey(event: KeyEvent): Int {
            return when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_UP -> UP
                KeyEvent.KEYCODE_DPAD_DOWN -> DOWN
                KeyEvent.KEYCODE_DPAD_LEFT -> LEFT
                KeyEvent.KEYCODE_DPAD_RIGHT -> RIGHT
                KeyEvent.KEYCODE_DPAD_CENTER -> CENTER
                else -> -1
            }
        }

        private fun getDirectionFromMotion(event: MotionEvent): Int {
            val x = event.getAxisValue(MotionEvent.AXIS_HAT_X)
            val y = event.getAxisValue(MotionEvent.AXIS_HAT_Y)

            return when {
                x < -0.5f -> LEFT
                x > 0.5f -> RIGHT
                y < -0.5f -> UP
                y > 0.5f -> DOWN
                else -> -1
            }
        }
    }
}
```

---

## Key Repeat Handling

Throttle repeated key events for smooth navigation:

```kotlin
class KeyRepeatHandler {
    private var lastKeyCode: Int = -1
    private var lastEventTime: Long = 0
    private val initialDelay = 400L  // ms before repeat starts
    private val repeatInterval = 100L // ms between repeats

    fun shouldProcess(keyCode: Int, event: KeyEvent): Boolean {
        val now = SystemClock.uptimeMillis()

        if (event.action != KeyEvent.ACTION_DOWN) {
            if (event.action == KeyEvent.ACTION_UP && keyCode == lastKeyCode) {
                lastKeyCode = -1
            }
            return false
        }

        if (keyCode != lastKeyCode) {
            // New key pressed
            lastKeyCode = keyCode
            lastEventTime = now
            return true
        }

        // Same key held
        val elapsed = now - lastEventTime
        val threshold = if (event.repeatCount == 1) initialDelay else repeatInterval

        if (elapsed >= threshold) {
            lastEventTime = now
            return true
        }

        return false
    }
}
```

---

## Event Flow Pattern

Route events from Activity to ViewModel:

```kotlin
// Define event types
sealed interface GamepadEvent {
    data object Up : GamepadEvent
    data object Down : GamepadEvent
    data object Left : GamepadEvent
    data object Right : GamepadEvent
    data object Confirm : GamepadEvent
    data object Back : GamepadEvent
    data object Menu : GamepadEvent
    data class Shoulder(val isLeft: Boolean) : GamepadEvent
}

// In ViewModel
class AppViewModel : ViewModel() {
    private val _events = MutableSharedFlow<GamepadEvent>()
    val events = _events.asSharedFlow()

    fun onGamepadEvent(event: GamepadEvent) {
        viewModelScope.launch {
            _events.emit(event)
        }
    }
}

// In Composable
@Composable
fun Screen(viewModel: AppViewModel) {
    val events = viewModel.events

    LaunchedEffect(Unit) {
        events.collect { event ->
            when (event) {
                GamepadEvent.Up -> moveFocusUp()
                GamepadEvent.Down -> moveFocusDown()
                // ...
            }
        }
    }
}
```

---

## Trigger Buttons

Handle analog triggers (L2/R2):

```kotlin
// As digital button (KeyEvent)
KeyEvent.KEYCODE_BUTTON_L2 -> handleLeftTrigger()
KeyEvent.KEYCODE_BUTTON_R2 -> handleRightTrigger()

// As analog axis (MotionEvent)
override fun onGenericMotionEvent(event: MotionEvent): Boolean {
    val leftTrigger = event.getAxisValue(MotionEvent.AXIS_LTRIGGER)
    val rightTrigger = event.getAxisValue(MotionEvent.AXIS_RTRIGGER)

    // Trigger values are 0.0 to 1.0
    if (leftTrigger > 0.5f) handleLeftTrigger()
    if (rightTrigger > 0.5f) handleRightTrigger()

    return super.onGenericMotionEvent(event)
}
```

---

## Touch Input

Compose handles touch natively. Use `clickable` and `combinedClickable`:

```kotlin
@Composable
fun TouchableCard(
    onClick: () -> Unit,
    onLongClick: () -> Unit = {}
) {
    Card(
        modifier = Modifier
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        // content
    }
}
```

### Swipe Gestures

```kotlin
@Composable
fun SwipeableRow(
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit
) {
    var offsetX by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = Modifier
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        when {
                            offsetX < -100f -> onSwipeLeft()
                            offsetX > 100f -> onSwipeRight()
                        }
                        offsetX = 0f
                    },
                    onHorizontalDrag = { _, dragAmount ->
                        offsetX += dragAmount
                    }
                )
            }
    )
}
```

---

## Dual-Input Mode (Touch + Gamepad)

Track the last input type to switch UI behavior:

```kotlin
enum class InputMode { TOUCH, GAMEPAD }

class InputModeTracker {
    private val _mode = MutableStateFlow(InputMode.GAMEPAD)
    val mode = _mode.asStateFlow()

    fun onTouchInput() {
        _mode.value = InputMode.TOUCH
    }

    fun onGamepadInput() {
        _mode.value = InputMode.GAMEPAD
    }
}
```

### Update Focus Manager on Touch

When touch is used, sync the focus state:

```kotlin
@Composable
fun DualInputCard(
    index: Int,
    isFocused: Boolean,
    onFocusChange: (Int) -> Unit,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .onFocusChanged { state ->
                if (state.isFocused) onFocusChange(index)
            }
            .focusable()
            .clickable {
                onFocusChange(index)  // Sync focus on touch
                onClick()
            }
    ) {
        // Visual state based on isFocused
    }
}
```

### Show/Hide Focus Indicator Based on Mode

```kotlin
@Composable
fun AdaptiveCard(
    isFocused: Boolean,
    inputMode: InputMode
) {
    val showFocusRing = isFocused && inputMode == InputMode.GAMEPAD

    Card(
        modifier = Modifier
            .border(
                width = if (showFocusRing) 2.dp else 0.dp,
                color = if (showFocusRing) Color.Blue else Color.Transparent
            )
    ) {
        // content
    }
}
```

### Detect Input Type in Activity

```kotlin
class MainActivity : ComponentActivity() {
    @Inject lateinit var inputModeTracker: InputModeTracker

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        inputModeTracker.onTouchInput()
        return super.dispatchTouchEvent(ev)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        inputModeTracker.onGamepadInput()
        // ... handle gamepad
        return super.onKeyDown(keyCode, event)
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.source and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK) {
            inputModeTracker.onGamepadInput()
        }
        return super.onGenericMotionEvent(event)
    }
}
```

---

## Combining Touch and Focus

Ensure touch taps update the focus manager:

```kotlin
@Composable
fun GameGrid(
    games: List<Game>,
    focusedIndex: Int,
    onFocusChange: (Int) -> Unit,
    onGameSelect: (Game) -> Unit
) {
    LazyVerticalGrid(columns = GridCells.Fixed(4)) {
        itemsIndexed(games) { index, game ->
            GameCard(
                game = game,
                isFocused = index == focusedIndex,
                modifier = Modifier
                    .onFocusChanged { if (it.isFocused) onFocusChange(index) }
                    .focusable()
                    .clickable {
                        onFocusChange(index)  // Important: sync on tap
                        onGameSelect(game)
                    }
            )
        }
    }
}
```

---

## Pitfalls

### 1. Double input

Return `true` from event handlers to consume the event:
```kotlin
override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
    if (handleEvent(keyCode)) {
        return true  // Consumed - prevents secondary handling
    }
    return super.onKeyDown(keyCode, event)
}
```

### 2. D-pad not working on some controllers

Some controllers don't report `SOURCE_DPAD`. Check `SOURCE_JOYSTICK` and use `AXIS_HAT_X/Y`:
```kotlin
val isDpad = event.source and InputDevice.SOURCE_DPAD == InputDevice.SOURCE_DPAD
val isJoystick = event.source and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK

if (isDpad || isJoystick) {
    // Handle input
}
```

### 3. Missing analog stick input

Must override `onGenericMotionEvent`, not just `onKeyDown`:
```kotlin
// KeyEvent handles: D-pad, A/B/X/Y, shoulders, triggers (digital)
// MotionEvent handles: Analog sticks, triggers (analog), HAT axis
```

### 4. Stick drift

Always apply deadzone filtering using `InputDevice.MotionRange.flat`.

### 5. Touch doesn't update focus state

Touch taps bypass the focus system. Always sync manually:
```kotlin
.clickable {
    onFocusChange(index)  // Sync focus on tap
    onClick()
}
```

### 6. Focus indicator visible during touch

Hide focus ring when using touch:
```kotlin
val showFocusRing = isFocused && inputMode == InputMode.GAMEPAD
```

---

## Sources

- [Handle Controller Actions](https://developer.android.com/develop/ui/views/touch-and-input/game-controllers/controller-input)
- [Manage TV Controllers](https://developer.android.com/training/tv/get-started/controllers)
- [Game Controller Input (Fire TV)](https://developer.amazon.com/docs/fire-tv/game-controller-input.html)
