# Android TV Launcher Design System

Reference documentation for building TV/handheld launcher apps with Kotlin and Jetpack Compose.

---

## Decision Tree

```
What are you trying to do?
|
+-- Handle D-pad/gamepad input -----> 02-input-handling.md
|
+-- Fix focus issues --------------> 01-focus-management.md
|   +-- Focus jumps erratically
|   +-- Focus not restored after navigation
|   +-- Items not focusable
|
+-- Navigate between screens ------> 03-navigation.md
|
+-- Style focus indicators --------> 04-theming.md
|   +-- Glow, scale, saturation
|   +-- Colors and typography
|
+-- Use TV-specific components ----> 05-tv-compose-components.md
|   +-- TVLazyRow, TVLazyColumn
|   +-- Carousel
|
+-- Configure as launcher ---------> 06-launcher-setup.md
```

---

## Quick Patterns

### Focus a specific item on screen load
```kotlin
val focusRequester = remember { FocusRequester() }

LaunchedEffect(Unit) {
    focusRequester.requestFocus()
}

Button(
    modifier = Modifier.focusRequester(focusRequester)
) { ... }
```

### Handle gamepad button press
```kotlin
override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
    if (event.source and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD) {
        when (keyCode) {
            KeyEvent.KEYCODE_BUTTON_A -> { /* confirm */ }
            KeyEvent.KEYCODE_BUTTON_B -> { /* back */ }
        }
        return true
    }
    return super.onKeyDown(keyCode, event)
}
```

### Track focus state for styling
```kotlin
var isFocused by remember { mutableStateOf(false) }

Box(
    modifier = Modifier
        .onFocusChanged { isFocused = it.isFocused }
        .focusable()
        .background(if (isFocused) Color.Blue else Color.Gray)
)
```

### TV Material dependency
```kotlin
implementation("androidx.tv:tv-material:1.0.0")
```

---

## Documentation Index

| File | Topic |
|------|-------|
| [01-focus-management.md](01-focus-management.md) | Focus handling, restoration, FocusRequester |
| [02-input-handling.md](02-input-handling.md) | Gamepad, D-pad, analog sticks, key events |
| [03-navigation.md](03-navigation.md) | Screen navigation, focus preservation |
| [04-theming.md](04-theming.md) | TV theming, focus indicators, animations |
| [05-tv-compose-components.md](05-tv-compose-components.md) | TV Material library, TVLazyRow/Column |
| [06-launcher-setup.md](06-launcher-setup.md) | AndroidManifest, launcher configuration |

---

## Key Dependencies

```kotlin
// build.gradle.kts
dependencies {
    // Compose BOM
    val composeBom = platform("androidx.compose:compose-bom:2024.02.00")
    implementation(composeBom)

    // TV Material (use instead of regular Material for TV)
    implementation("androidx.tv:tv-material:1.0.0")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.7.7")
}
```

---

## Sources

- [Focus in Compose](https://developer.android.com/develop/ui/compose/touch-input/focus)
- [Handle Controller Actions](https://developer.android.com/develop/ui/views/touch-and-input/game-controllers/controller-input)
- [Compose on Android TV](https://developer.android.com/training/tv/playback/compose)
- [Android TV Samples](https://github.com/android/tv-samples)
