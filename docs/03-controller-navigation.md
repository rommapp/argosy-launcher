# Controller-Focused Navigation

## Design Principles

1. **Predictable**: Focus always moves in the direction pressed
2. **Visible**: Focused item is always clearly highlighted
3. **Restorable**: Returning to a screen restores previous focus
4. **Accessible**: Every action reachable without touch

## Input Mapping

### Standard Android Gamepad

| Input | KeyCode | Action |
|-------|---------|--------|
| D-pad Up | `KEYCODE_DPAD_UP` | Move focus up |
| D-pad Down | `KEYCODE_DPAD_DOWN` | Move focus down |
| D-pad Left | `KEYCODE_DPAD_LEFT` | Move focus left |
| D-pad Right | `KEYCODE_DPAD_RIGHT` | Move focus right |
| A Button | `KEYCODE_BUTTON_A` | Confirm/Select |
| B Button | `KEYCODE_BUTTON_B` | Back/Cancel |
| X Button | `KEYCODE_BUTTON_X` | Secondary action |
| Y Button | `KEYCODE_BUTTON_Y` | Context menu |
| L1 | `KEYCODE_BUTTON_L1` | Previous section |
| R1 | `KEYCODE_BUTTON_R1` | Next section |
| Start | `KEYCODE_BUTTON_START` | Quick Access |
| Select | `KEYCODE_BUTTON_SELECT` | Search |

### Button Prompts

Display context-sensitive button hints:
```
Home Screen:    [A] Play  [Y] Details  [START] Menu
Library:        [A] Select  [X] Filter  [L1/R1] Platform
Game Detail:    [A] Play  [B] Back  [Y] Favorite
```

## Focus System Architecture

### Core Classes

```kotlin
// Focus manager singleton per screen
class GamepadFocusManager {
    val focusedItem: StateFlow<FocusableItem?>

    fun registerItem(item: FocusableItem)
    fun unregisterItem(id: String)
    fun moveFocus(direction: FocusDirection): Boolean
    fun requestFocus(id: String)
    fun saveFocusState(screenId: String)
    fun restoreFocusState(screenId: String): Boolean
}

// Focusable item registration
data class FocusableItem(
    val id: String,
    val focusRequester: FocusRequester,
    val bounds: Rect?,
    val neighbors: FocusNeighbors
)

data class FocusNeighbors(
    val up: String? = null,
    val down: String? = null,
    val left: String? = null,
    val right: String? = null
)

enum class FocusDirection { UP, DOWN, LEFT, RIGHT }
```

### Modifier Extension

```kotlin
@Composable
fun Modifier.gamepadFocusable(
    id: String,
    onFocused: () -> Unit = {},
    onSelected: () -> Unit = {},
    neighbors: FocusNeighbors = FocusNeighbors()
): Modifier
```

Usage:
```kotlin
GameCard(
    game = game,
    modifier = Modifier.gamepadFocusable(
        id = "game_${game.id}",
        onFocused = { viewModel.setFocusedGame(game) },
        onSelected = { navController.navigate("detail/${game.id}") }
    )
)
```

## Focus Traversal Patterns

### Grid Navigation

```
+---+---+---+---+
| 1 | 2 | 3 | 4 |  <- Row 0
+---+---+---+---+
| 5 | 6 | 7 | 8 |  <- Row 1
+---+---+---+---+

From item 2:
  Up    -> null (edge) or previous section
  Down  -> 6 (same column)
  Left  -> 1
  Right -> 3
```

### Rail Navigation

```
+------+------+------+------+------+
| Game | Game | Game | Game | Game | ...
+------+------+------+------+------+
    ^
    Focus

Left/Right: Move within rail
Up/Down: Move to adjacent rail
```

### Edge Behavior

When focus hits an edge:
1. **Wrap** (optional): Move to opposite end
2. **Block**: Stay in place, optional haptic feedback
3. **Exit**: Move to adjacent section/component

Default: Block with subtle feedback

## Focus Restoration

### Screen Stack

```kotlin
class FocusStateStore {
    private val stack = ArrayDeque<FocusSnapshot>()

    fun push(snapshot: FocusSnapshot)
    fun pop(): FocusSnapshot?
    fun peek(): FocusSnapshot?
}

data class FocusSnapshot(
    val screenId: String,
    val focusedItemId: String,
    val scrollPosition: Int
)
```

### Restoration Flow

```
1. User on Library, focused on Game #47
2. User presses A -> Navigate to Detail
3. System calls focusStore.push(LibrarySnapshot)
4. Detail screen loads, focuses first item
5. User presses B -> Navigate back
6. System calls focusStore.pop() -> LibrarySnapshot
7. Library restores focus to Game #47
8. Scroll position also restored
```

## Scroll Behavior

### Focus-Driven Scrolling

When focus moves to an item:
1. Check if item is fully visible
2. If not, scroll to center the focused item
3. Use smooth scroll animation (250ms)

```kotlin
LaunchedEffect(focusedIndex) {
    lazyListState.animateScrollToItem(
        index = focusedIndex,
        scrollOffset = -centerOffset
    )
}
```

### Continuous Scrolling

When holding D-pad direction:
1. Initial delay: 400ms
2. Repeat rate: 100ms
3. Acceleration: Speed up after 1s

## Input Handling Layer

### Event Flow

```
Android KeyEvent
      |
      v
LauncherInputHandler (intercept global shortcuts)
      |
      v
Screen-level handler (screen-specific actions)
      |
      v
GamepadFocusManager (navigation)
      |
      v
Focused component (selection)
```

### Global Shortcuts

Always handled regardless of focus:
- `START` -> Quick Access overlay
- `SELECT` -> Search
- `L1/R1` -> Section navigation

### Implementation

```kotlin
class LauncherInputHandler(
    private val focusManager: GamepadFocusManager,
    private val navController: NavController
) {
    fun handleKeyEvent(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return false

        return when (event.keyCode) {
            // Global shortcuts
            KeyEvent.KEYCODE_BUTTON_START -> {
                showQuickAccess()
                true
            }

            // Navigation
            KeyEvent.KEYCODE_DPAD_UP -> focusManager.moveFocus(UP)
            KeyEvent.KEYCODE_DPAD_DOWN -> focusManager.moveFocus(DOWN)
            KeyEvent.KEYCODE_DPAD_LEFT -> focusManager.moveFocus(LEFT)
            KeyEvent.KEYCODE_DPAD_RIGHT -> focusManager.moveFocus(RIGHT)

            // Actions
            KeyEvent.KEYCODE_BUTTON_A -> focusManager.selectFocused()
            KeyEvent.KEYCODE_BUTTON_B -> navController.popBackStack()

            else -> false
        }
    }
}
```

## Visual Feedback

### Focus Indicator

Game cards use distinct visual states:

```
FOCUSED:     1.1x scale, 100% opacity, 100% saturation, glow + border
UNFOCUSED:   1.0x scale, 85% opacity, 80% saturation, no effects
```

```kotlin
@Composable
fun FocusIndicator(
    isFocused: Boolean,
    content: @Composable () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.1f else 1f,
        animationSpec = spring(dampingRatio = 0.7f)
    )

    val alpha = if (isFocused) 1f else 0.85f
    val saturation = if (isFocused) 1f else 0.8f
    val glowAlpha by animateFloatAsState(
        targetValue = if (isFocused) 0.4f else 0f
    )

    Box(
        modifier = Modifier
            .scale(scale)
            .graphicsLayer { this.alpha = alpha }
            .drawBehind {
                if (glowAlpha > 0) {
                    drawRoundRect(
                        color = PrimaryGlow.copy(alpha = glowAlpha),
                        cornerRadius = CornerRadius(12.dp.toPx()),
                        style = Fill
                    )
                }
            }
            .border(
                width = if (isFocused) 2.dp else 0.dp,
                color = Primary,
                shape = RoundedCornerShape(8.dp)
            )
    ) {
        // Content with saturation filter
        CompositionLocalProvider(
            LocalContentSaturation provides saturation
        ) {
            content()
        }
    }
}
```

### Haptic Feedback

```kotlin
enum class HapticPattern {
    FOCUS_CHANGE,    // Light tick
    SELECTION,       // Medium click
    BOUNDARY_HIT,    // Double tick
    ERROR            // Strong buzz
}

fun vibrate(pattern: HapticPattern) {
    val vibrator = context.getSystemService<Vibrator>()
    when (pattern) {
        FOCUS_CHANGE -> vibrator.vibrate(10ms)
        SELECTION -> vibrator.vibrate(20ms)
        BOUNDARY_HIT -> vibrator.vibrate([10ms, 50ms, 10ms])
        ERROR -> vibrator.vibrate(100ms)
    }
}
```

## Testing Navigation

### Test Scenarios

1. **Linear navigation**: Up/down through menu items
2. **Grid navigation**: 2D movement in library grid
3. **Rail navigation**: Left/right in horizontal rails
4. **Cross-section**: Moving between different sections
5. **Modal handling**: Overlay appears, focus traps inside
6. **Restoration**: Back button restores previous focus
7. **Edge cases**: First/last items, empty lists
