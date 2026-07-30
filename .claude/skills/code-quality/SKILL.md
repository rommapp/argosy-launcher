---
name: code-quality
description: Code quality standards and patterns for Argosy. Use BEFORE writing or modifying code to ensure consistency with project patterns.
---

# Code Quality Standards

Essential reminders for writing consistent, maintainable code in this project.

---

## Mandatory Requirements

These are NON-NEGOTIABLE. A feature missing any of these is INCOMPLETE.

### 1. Input Handling (TV + Touch)
All interactive UI components MUST have:

**Touch Support:**
- Use `Modifier.clickableNoFocus { }` (from `ui/util/Modifiers.kt`)
- NEVER use plain `Modifier.clickable()` - it enables Compose TV focus

**Controller Support:**
- `InputHandler` implementation for D-pad navigation
- Proper index wrapping with `.mod(size)` (NOT `% size`)
- Visual focus state via `isFocused: Boolean` prop

**Focus Management:**
- NO Compose focus for navigation or selection. `focusable()` appears nowhere in the
  tree and must not. The legitimate exception is `FocusRequester` for soft-keyboard
  text entry (~20 `ui/` files) and the root key sink in `ArgosyApp`; it becomes a
  violation the moment focus decides what is SELECTED rather than what is TYPED INTO.
- Focus index stored in ViewModel
- Manual focus visuals via `FocusIndicators` (`ui/primitives/Focus.kt`): fill/halo/stripe/ring/lift;
  never movement or scale except lift, which is reserved for cover tiles
  (`FocusIndicators.Tile` is the only preset that sets `lift`)

A component that only handles ONE input modality is INCOMPLETE.
Pattern: Check existing components for `InputHandler` + `clickableNoFocus` pairing.

**Modal input capture (learned the hard way, 2026-07-06):** a modal/dialog/overlay that
RENDERS without taking the input stack is INCOMPLETE - gamepad input drives the screen
behind it (dual-focus ghost, drawer-over-dialog). Every modal must use exactly one of:
(A) `ModalInputEffect`/`pushModal` (self-contained; `ArgosyConfirmModalHost` does this for
you - pushes IMMEDIATELY on open, never deferred to a lifecycle event), (B) the host
handler guarding the modal's visibility state in EVERY nav method (up/down/left/right/
confirm/back AND prev/next section/trigger - partial guards leak the unguarded
directions), or (C) a handler the host stores and delegates to. `Modal`/`CenteredModal`/
`ModalScaffold` are pure visuals - they capture NOTHING by themselves.
Mechanism-B guards MUST return `InputResult.HANDLED`, never UNHANDLED: the app-level
fallback treats an unhandled result as "no modal here" and runs global actions (LEFT
opens the drawer, Menu toggles it) straight over the modal. A guard that returns
UNHANDLED is the bug, not a guard.

### 2. Footer Hints (V2: the control is the guide)
Authority: `design-handoff/CONTROL-FOUNDATIONS.md`. The bar surfaces what is NOT obvious
from the focused control; the inline affordance carries the interaction.
- A / B / d-pad / Back alone never justify a bar - if they are the only candidates, no bar.
- Hints that earn the bar: screen-global verbs (search, filter), shoulder paging (LB/RB),
  non-obvious bound buttons (X/Y, triggers).
- When space is limited, shed OBVIOUS guides first (d-pad, then A/B); never drop a
  non-obvious hint to keep an obvious one. `FooterHint.hidePriority()` implements this
  order (X/Y highest keep priority, A/B low, d-pad lowest).
- A/Confirm means enter/commit/toggle, NEVER adjust; sliders/steppers/enums adjust on
  Left/Right only.

### 3. Lazy List Scrolling
Lists MUST use LazyColumn/LazyVerticalGrid, never regular Column/Row.
- Enables efficient scrolling and memory management
- Required for gamepad navigation patterns
- No exceptions for "small" lists

---

## Pre-Implementation Questions

Before writing code, answer these questions:

1. **Scope**: What are ALL the places this feature touches? (UI, data, settings, navigation)
2. **States**: What are the error, loading, empty, and success states?
3. **Lifecycle**: What happens on rotation? Backgrounding? Return from other app?
4. **First-run**: Does this behave differently on fresh install vs existing data?
5. **Consistency**: What existing patterns should this follow? (Find and cite file:line examples)

If you cannot answer these, investigate before writing code.

---

## Core Principles

### SOLID
- **Single Responsibility**: Each class/function does one thing well
- **Open/Closed**: Extend behavior without modifying existing code
- **Liskov Substitution**: Subtypes must be substitutable for base types
- **Interface Segregation**: Small, focused interfaces over large ones
- **Dependency Inversion**: Depend on abstractions, not concretions

### DRY (Don't Repeat Yourself)
- Extract common logic into shared utilities
- Reuse existing components before creating new ones
- Check for existing patterns in codebase before implementing

### KISS (Keep It Simple, Stupid)
- Prefer simple, readable solutions over clever ones
- Avoid premature optimization
- Don't add features "just in case"

### YAGNI (You Aren't Gonna Need It)
- Only implement what's currently needed
- Remove unused code rather than commenting it out
- Don't build for hypothetical future requirements

## Project-Specific Patterns

### UI State Management
- State flows from ViewModel to Composables via `StateFlow`
- Use delegates for section-specific logic (e.g., `DisplaySettingsDelegate`)
- Update state via `_uiState.update { it.copy(...) }`
- Keep UI state in single `UiState` data class per screen

### StateFlow writes MUST be atomic (lost-update trap, learned 2026-07-22)
`_uiState.value = _uiState.value.copy(...)` is a read-modify-write that silently
loses concurrent updates. On screens with several collectors (feed, profile,
prefs), a field written by one collector gets wiped by another and never
re-emits - the avatar-doodle fields vanished on exactly the busy social screens
this way while quiet screens worked. Rules:
- ALWAYS `_uiState.update { it.copy(...) }` - never assign `.value` from a read
  of `.value`, even though older code in some files still does.
- NEVER write back a pre-suspend snapshot: `val s = _uiState.value` ... suspend
  call ... `_uiState.value = s.copy(...)` clobbers everything that landed during
  the suspend. Compute the async value first, then `update {}` with fields from
  the lambda's current state.
- When adding a collector to an existing ViewModel, do not copy its legacy
  write pattern; use `update {}` and convert wide-window writers you touch.

### Compose Stability Contract (NON-NEGOTIABLE)
`app/compose_stability_config.conf` declares `data.model.**`, `data.local.entity.**`,
`ui.screens.**`, `ui.components.**`, and `ui.dualscreen.**` stable to the Compose
compiler. This is what keeps cold compiles at ~4 min instead of 1h+ (StabilityInferencer
recursion), but the compiler NO LONGER VERIFIES stability for covered classes -- we
promise it. A violation does not crash or warn; it silently skips recompositions and
the UI goes stale. Rules for ALL new/edited code in covered packages:
- State/model data classes: `val`-only, including constructor params. Never add `var`.
- Collections in state are replaced via `copy(...)`, never mutated in place.
- Never read a plain `var`/non-State property of a ViewModel or delegate inside
  composition. UI-visible data reaches composables ONLY via `StateFlow`/`collectAsState`
  or params.
- A class that genuinely needs mutable fields must live OUTSIDE the covered packages,
  or get an explicit exclusion note in the conf.
- `ui.primitives` is NOT covered, despite hosting `FocusIndicators`, `InputGlyph` and
  `ConfirmModal`. The compiler still infers stability there, so the val-only promise is
  not load-bearing for those files - but do not read that as licence to put mutable
  state in a primitive.
- If the conf file is absent on the current branch, these rules are still the house
  style; they just aren't load-bearing yet.

### Input Handling (TV/Gamepad) - CRITICAL

#### InputHandler Interface

All controller input goes through `InputHandler` (`ui/input/InputHandler.kt`). This is the
actual interface -- every method has a default `UNHANDLED` body, so implementors override
only what they handle:

```kotlin
interface InputHandler {
    fun onUp(): InputResult = InputResult.UNHANDLED
    fun onDown(): InputResult = InputResult.UNHANDLED
    fun onLeft(): InputResult = InputResult.UNHANDLED
    fun onRight(): InputResult = InputResult.UNHANDLED
    fun onConfirm(): InputResult = InputResult.UNHANDLED
    fun onBack(): InputResult = InputResult.UNHANDLED
    fun onMenu(): InputResult = InputResult.UNHANDLED
    fun onSecondaryAction(): InputResult = InputResult.UNHANDLED
    fun onContextMenu(): InputResult = InputResult.UNHANDLED
    fun onPrevSection(): InputResult = InputResult.UNHANDLED
    fun onNextSection(): InputResult = InputResult.UNHANDLED
    fun onPrevTrigger(): InputResult = InputResult.UNHANDLED
    fun onNextTrigger(): InputResult = InputResult.UNHANDLED
    fun onSelect(): InputResult = InputResult.UNHANDLED
    fun onLeftStickClick(): InputResult = InputResult.UNHANDLED
    fun onRightStickClick(): InputResult = InputResult.UNHANDLED
    fun onLongConfirm(): InputResult = InputResult.UNHANDLED
}
```

#### Button Names and Swap Resolution

`InputButton` (`ui/components/FooterHint.kt`) uses INTENT names, not physical positions:

```kotlin
enum class InputButton {
    A, B, X, Y,
    DPAD, DPAD_UP, DPAD_DOWN, DPAD_LEFT, DPAD_RIGHT, DPAD_HORIZONTAL, DPAD_VERTICAL,
    LB, RB, LB_RB, LT, RT, LT_RT,
    START, SELECT
}
```

There are NO position-named values (`SOUTH`/`EAST`/`WEST`/`NORTH` do not exist).
`InputButton.A` means "the confirm intent", wherever the user has mapped it. User button
swaps are resolved at exactly two points -- never by choosing a different enum value:

1. **Icon render:** `InputButton.toPainter()` reads `LocalABIconsSwapped` /
   `LocalXYIconsSwapped` / `LocalSwapStartSelect` and picks the physical glyph
   (e.g. `InputButton.A` draws `FaceRight` when AB is swapped).
2. **Keyevent dispatch:** `mapKeycodeToGamepadEvent(keyCode, swapAB, swapXY, swapStartSelect)`
   in `ui/input/GamepadInputHandler.kt` routes the physical keycode to the intended
   `GamepadEvent` (e.g. `KEYCODE_BUTTON_X` -> `SecondaryAction` when XY is swapped).

| InputButton | Handler Method | Typical Use |
|-------------|----------------|-------------|
| `A` | `onConfirm()` | Select/Confirm (never adjust) |
| `B` | `onBack()` | Back/Cancel |
| `X` | `onContextMenu()` | Full context menu |
| `Y` | `onSecondaryAction()` | Quick action (favorite, add) |

#### CRITICAL: Never Hardcode Button Names

**WRONG - Breaks when buttons are swapped:**
```kotlin
Text("Press X to add friend")
Box { Text("X") }  // Custom button hint
```

**CORRECT - Uses FooterBar with InputButton enum:**
```kotlin
FooterBar(
    hints = listOf(
        InputButton.Y to "Add Friend",
        InputButton.A to "Select"
    )
)
```

`FooterBar` takes `hints: List<Pair<InputButton, String>>`; each hint's icon resolves the
swap automatically via `toPainter()`.

#### Remove Compose Focus System

TV UI does NOT use Compose's built-in focus. Always use `clickableNoFocus`:

```kotlin
import com.nendo.argosy.ui.util.clickableNoFocus

// CORRECT - use clickableNoFocus
Modifier.clickableNoFocus { /* action */ }
Modifier.clickableNoFocus(enabled = isEnabled) { /* action */ }

// WRONG - never use plain clickable()
Modifier.clickable { /* action */ }  // Enables TV focus, breaks gamepad nav
```

**Why `clickableNoFocus`?**

Compose's `clickable()` enables TV focus by default, which conflicts with this app's manual InputHandler-based focus system. Using `clickable()` directly causes:
- Double focus visuals (Compose ring + manual glow/border)
- Unpredictable navigation when Compose and InputHandler fight for control
- Broken gamepad input when Compose steals focus

The `clickableNoFocus` extension (defined in `ui/util/Modifiers.kt`) disables Compose focus while preserving touch support.

**Focus Management:**
- Use `isFocused: Boolean` prop for visual focus state
- Manage focus index in ViewModel, not Compose focus system
- Never use `Modifier.focusable()` - it appears nowhere in the tree
- `FocusRequester` is allowed ONLY for soft-keyboard text entry and the root key
  sink in `ArgosyApp`. Using it to move selection between rows is the violation

**Material3 Components with Built-in Focus:**

Some Material3 components have built-in TV focus. Disable it:

```kotlin
// Switch - disable focus
Switch(
    checked = isEnabled,
    onCheckedChange = onToggle,
    modifier = Modifier.focusProperties { canFocus = false },
    interactionSource = remember { MutableInteractionSource() }
)
```

Other components that may need similar treatment: `Checkbox`, `RadioButton`, `Slider`.

#### Proper Index Calculation

Use `.mod()` for wrapping, NOT `%` operator:
```kotlin
// CORRECT - handles negative numbers
val newIndex = (currentIndex + direction).mod(items.size)

// WRONG - breaks on negative indices
val newIndex = (currentIndex + direction) % items.size  // -1 % 5 = -1, not 4
```

#### Dual Input Support (Touch + Controller)

Every interactive element needs BOTH:
```kotlin
// Touch support - use clickableNoFocus
Modifier.clickableNoFocus { onItemClick(index) }

// Controller support
class MyInputHandler : InputHandler {
    override fun onConfirm(): InputResult {
        onItemClick(focusedIndex)
        return InputResult.HANDLED
    }
    override fun onRight(): InputResult {
        focusedIndex = (focusedIndex + 1).mod(items.size)
        return InputResult.HANDLED
    }
}
```

### Footer Hints
- Only show hints for controls available in current context
- Use `FooterBar` with `InputButton` enum - NEVER hardcode button letters
- Add hints for L1/R1 when shoulder buttons have actions

### Navigation
- Settings sections: Add to `SettingsSection` enum
- Navigation state in ViewModel, not Composable
- Use `navigateBack()` with proper parent section restoration

### Preferences
- New prefs do NOT go in `UserPreferencesRepository`. That file holds ZERO DataStore
  keys - it is a pure aggregator that combines seven domain repos
  (`displayPrefs`, `syncPrefs`, `controlsPrefs`, `storagePrefs`, `appPrefs`,
  `builtinPrefs`, `sessionPrefs`) into one `UserPreferences` flow.
- The chain is: DataStore key + default + getter/setter in the OWNING domain repo
  under `data/preferences/` (e.g. `DisplayPreferencesRepository`,
  `BuiltinEmulatorPreferencesRepository`) -> its own `Preferences` data class ->
  the `UserPreferences` aggregation -> settings state -> consumption site.
  Mirror the sibling key naming in the repo you are adding to; prefixes differ
  between repos.
- Use `companion object { fun fromString() }` pattern for enum persistence
- Keep display names in UI layer, not data layer

### Theming
- Use `CompositionLocal` for cross-cutting styling concerns
- Provide via `ALauncherTheme` wrapper
- Read via `LocalXxx.current` in Composables

### Glow Effects
- Use `drawIntoCanvas` with `BlurMaskFilter` for proper glow
- Example pattern from GameCard:
  ```kotlin
  Modifier.drawBehind {
      drawIntoCanvas { canvas ->
          val paint = Paint().apply { color = glowColor.copy(alpha = glowAlpha) }
          val frameworkPaint = paint.asFrameworkPaint().apply {
              maskFilter = android.graphics.BlurMaskFilter(glowRadius, BlurMaskFilter.Blur.NORMAL)
          }
          canvas.nativeCanvas.drawRoundRect(...)
      }
  }
  ```
- Do NOT use simple `drawRect` for glow - it won't blur

### Light/Dark Mode & Colors
- Check theme: `LocalLauncherTheme.current.isDarkTheme`
- Use `MaterialTheme.colorScheme` for context-appropriate colors:
  - `primary` / `onPrimary`: Accent elements and their text
  - `surface` / `onSurface`: Backgrounds and their text
  - `primaryContainer` / `onPrimaryContainer`: Focused/selected items
  - `surfaceVariant` / `onSurfaceVariant`: Secondary surfaces, muted text
- For overlays/scrims:
  - Dark mode: `Color.Black.copy(alpha = X)`
  - Light mode: `Color.White.copy(alpha = X)`
- Semantic colors via `LocalLauncherTheme.current.semanticColors`:
  - `warning`, `success`, `error` for status indicators
- Never hardcode colors - always derive from theme

## Architecture Rules

These rules prevent the structural debt that required a major cleanup. Violating them creates problems that compound over time.

### Layer Boundaries (NON-NEGOTIABLE)

```
UI (ui/) --> Domain (domain/) --> Data (data/)
   ^              ^                  |
   |              |                  |
   +--------------+------------------+
     Dependencies flow inward only
```

- **Domain layer** (`domain/`): NO Compose imports. This half HOLDS in the tree and is a
  hard rule. The "no Android framework imports / pure Kotlin" half is LAW but currently
  **aspirational - known debt**; do not add new violations, and see the violator list below.
- **Data layer** (`data/`): NO Compose imports (`Color`, `ImageVector`, `Icons.*`). Use extension properties in `ui/common/` to attach UI concerns to domain types (see `CompletionStatusUi.kt` pattern).
- **UI layer** (`ui/`): NO direct DAO imports. Use repositories. LAW but currently
  **aspirational - known debt**; violator list below.

**Known debt: domain/ Android framework imports** (do not extend this list):
- `domain/usecase/game/LaunchGameUseCase.kt` (`android.content.Intent`)
- `domain/usecase/MigrateStorageUseCase.kt` (`android.util.Log`)
- `domain/usecase/MigratePlatformStorageUseCase.kt` (`android.util.Log`)
- `domain/usecase/PurgePlatformUseCase.kt` (`android.util.Log`)
- `domain/usecase/collection/GetCollectionsUseCase.kt` (`android.util.Log`)
- `domain/usecase/download/DownloadGameUseCase.kt` (`android.util.Log`)
- `domain/usecase/libretro/LibretroMigrationUseCase.kt` (`android.util.Log`)
- `domain/usecase/music/MeasureTrackLoudnessUseCase.kt` (`android.media.AudioFormat`, `MediaCodec`, `MediaExtractor`, `MediaFormat`, `android.util.Log`)
- `domain/usecase/save/RestoreCachedSaveUseCase.kt` (`android.util.Log`)
- `domain/usecase/state/PreLaunchStateSyncUseCase.kt` (`android.util.Log`)
- `domain/usecase/state/RestoreCachedStatesUseCase.kt` (`android.util.Log`)
- `domain/usecase/state/SyncStatesOnSessionEndUseCase.kt` (`android.util.Log`)

**Known debt: ui/ direct DAO use** (do not extend this list). It was built by
import-grep, so it under-reports: a fully-qualified type in a constructor
parameter or a reach through another ViewModel's public DAO field never shows up
in an import search. Before saying "this is not on the list", grep for `Dao` in
the file, not just the import block.
- `ui/screens/settings/SettingsViewModel.kt` (`SaveCacheDao`)
- `ui/screens/settings/delegates/BiosSettingsDelegate.kt` (`FirmwareDao`)
- `ui/screens/savesync/SaveSyncViewModel.kt` (`GameDao`, `PendingConflictDao`, `SaveSyncDao`)
- `ui/screens/gamedetail/GameDetailViewModel.kt` (`EmulatorConfigDao`, `GameDiscDao`, `GameFileDao`)
- `ui/screens/gamedetail/delegates/AchievementDelegate.kt` (`AchievementDao`)
- `ui/screens/gamedetail/delegates/PerGameSettingsDelegate.kt` (`EmulatorConfigDao`)
- `ui/screens/gamedetail/delegates/SaveManagementDelegate.kt` (`EmulatorSaveConfigDao`, `SaveSyncDao`)
- `ui/dualscreen/gamedetail/DualGameDetailViewModel.kt` (`EmulatorConfigDao`)
- `ui/ArgosyViewModel.kt` (`PendingConflictDao`, fully-qualified constructor param)
- `ui/screens/gamedetail/delegates/DownloadDelegate.kt` (`GameFileDao`, fully-qualified constructor param)
- `ui/screens/settings/SettingsInitRouter.kt` (`vm.saveCacheDao.countNeedingRemoteSync()`, reached through SettingsViewModel)

### Repository Pattern (NON-NEGOTIABLE)

ViewModels and delegates in `ui/` MUST access data through repositories, never DAOs directly.

| DAO | Repository | Notes |
|-----|-----------|-------|
| `GameDao` | `GameRepository` | Available everywhere, including dual-screen VMs (via `DualScreenManagerHolder`) |
| `PlatformDao` | `PlatformRepository` | Simple, works everywhere |
| `CollectionDao` | `CollectionRepository` | Simple, works everywhere |

`DualHomeViewModel`, `DualGameDetailViewModel`, and `SecondaryHomeViewModel` all take
`GameRepository` in their constructors -- there is no dual-screen DAO exception anymore.
Remaining direct DAO use in `ui/` is tracked in the known-debt list above; do not add to it.

When adding new DAO methods that UI needs: add the method to the repository, not the ViewModel.

### Data Layer Change Checklist
When modifying DAOs, entities, foreign keys, or database queries:
1. Find ALL entities with FK constraints to affected tables
2. Check for existing migration methods (e.g., `migratePlatform`)
3. Verify operation order: create before reference, delete after dereference
4. Test with existing user data, not just fresh installs

### File Size Limits

| Type | Soft Limit | Action |
|------|-----------|--------|
| ViewModel | ~500 lines | Extract delegates (see `GameDetailViewModel` + `delegates/`) |
| Activity | ~500 lines | Extract managers/helpers (see `SecondaryHomeActivity`) |
| Repository | ~300 lines | Extract service classes (see `SaveSyncRepository` + services) |
| Composable file | ~400 lines | Extract private sub-composables |
| Preferences repo | ~300 lines | Split by domain (see 7 domain repos under `data/preferences/`) |

**Decomposition patterns used in this codebase:**
- **Delegates**: ViewModel logic split by feature area (`DownloadDelegate`, `SaveManagementDelegate`)
- **Routers**: ViewModel method routing by category (`SettingsGeneralRouter`, `SettingsBuiltinRouter`)
- **Services**: Repository logic split by concern (`SaveSyncOrchestrator`, `SaveSyncApiClient`)
- **Facade**: Original class becomes thin facade, delegates to extracted classes

### LazyList Keys (NON-NEGOTIABLE)

Every `items()`, `itemsIndexed()`, and `LazyVerticalGrid` call MUST have a `key` parameter with a stable, unique identifier:

```kotlin
// CORRECT
itemsIndexed(games, key = { _, game -> game.id }) { index, game -> ... }
items(apps.size, key = { apps[it].packageName }) { index -> ... }

// WRONG - causes unnecessary recomposition
itemsIndexed(games) { index, game -> ... }
```

### Compose Performance

- Use `derivedStateOf` for values computed from state that don't need to trigger recomposition on every state change
- Avoid `.chunked()`, `.map()`, `.filter()` chains inside composable functions without `remember`/`derivedStateOf` -- they allocate on every recomposition
- Use `remember(key) { computation }` for expensive calculations

### Don't Copy-Paste Patterns

Before duplicating code, check for existing shared utilities:

| Pattern | Shared Utility | Location |
|---------|---------------|----------|
| Long-press scale animation | `LongPressAnimation` | `ui/common/LongPressAnimation.kt` |
| GameEntity -> UI model | Check existing `toUi()` extensions | Model files or `*Mapper.kt` |
| PlatformEntity -> UI model | `toHomePlatformUi()` | `ui/screens/home/HomeModels.kt` |
| Modal dialogs | `Modal`, `CenteredModal` | `ui/components/` |
| Gradient extraction | `GradientColorExtractor` | `ui/common/GradientColorExtractor.kt` |
| Completion status icons/colors | Extension properties | `ui/common/CompletionStatusUi.kt` |

### Dual-Screen (Companion) ViewModel Construction

The companion (dual-screen secondary display) runs in the SAME process as the launcher --
there is no `:companion` process. Its ViewModels are still manually constructed (not
Hilt-injected at the call site) in `SecondaryHomeActivity` (`hardware/SecondaryHomeActivity.kt`),
but their dependencies come from `DualScreenManagerHolder.instance` -- e.g.
`dsm.gameRepository`, `dsm.platformRepository`, `dsm.collectionRepository`,
`dsm.displayAffinityHelper`. This means:
- Any dependency exposed on `DualScreenManager` is available, including `GameRepository`
- `SecondaryHomeViewModel` is `@HiltViewModel @Inject` (and is also constructed manually
  in `SecondaryHomeActivity` from `dsm` deps)
- When adding a dependency to a dual-screen VM, expose it on `DualScreenManager` and
  update every manual construction site (`SecondaryHomeActivity`, `DualScreenManager`)

## Completion Criteria

A feature is NOT done until all of these are true:

### Mandatory (blocking)
- [ ] Touch input works (clickable with onClick)
- [ ] Controller input works (InputHandler with D-pad + A/B buttons)
- [ ] No Compose focus ring (indication = null on ALL clickables)
- [ ] Index wrapping uses `.mod()` not `%`
- [ ] Footer hints updated (following priority tiers)
- [ ] Lists use LazyColumn/LazyVerticalGrid
- [ ] Error handling is explicit (no silent failures)
- [ ] Builds without errors

### Required
- [ ] Follows existing patterns (or documents why not)
- [ ] State managed in ViewModel, not locally (unless truly local)
- [ ] Focus state is visual prop (`isFocused`), not Compose focus
- [ ] Colors from theme, not hardcoded (works in light AND dark mode)
- [ ] No duplicate code that could be extracted
