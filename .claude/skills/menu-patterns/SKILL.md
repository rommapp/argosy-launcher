---
name: menu-patterns
description: Menu system patterns for building settings screens, modals, and preference items. Use BEFORE building any menu UI to ensure consistency with input handling, visibility, and footer hints.
---

# Menu System Patterns

Standardized patterns for constructing menus across the Argosy launcher, covering preference item types, input behavior, visibility/disabled handling, footer hints, modals, and section navigation.

> V2 NOTE: `design-handoff/CONTROL-FOUNDATIONS.md` is the design authority and the V2
> components are SHIPPED: `CyclePreference` renders `EnumValueControl` and opens
> `EnumPickerModal` on A, `SliderPreference` renders `StepperControl`, and
> `FooterHint.hidePriority()` implements the V2 shed order. Non-negotiables from the spec:
> A/Confirm means enter/commit/toggle, NEVER adjust; focus never moves an element
> (fill/stripe/ring/halo only); inline affordances are always visible on every row;
> menu rows are 40dp (52 two-line).

---

## Menu Item Types

### 1. Toggle (SwitchPreference)
Boolean on/off states. V2 silhouette is BOXY (rounded-square track + narrow vertical knob),
not an M3 switch.

| Input | Behavior |
|-------|----------|
| A / Tap | Toggle state |
| LEFT / RIGHT | left=off, right=on (V2) |

**Examples:** Haptic feedback, sync toggles, screen dimmer

---

### 2. Enum (CyclePreference)
One control, two access paths (V2).

| Input | Behavior |
|-------|----------|
| A / Tap value | OPEN full option list as a modal - A never adjusts |
| LEFT / RIGHT / Tap triangle | Cycle one option (wrap behavior configurable per item) |

**Visual (V2):** small FILLED triangles `< value >` that rhyme with the d-pad glyphs, never
text chevrons. Tint accent on focus; pressed direction flashes.

**Wrap Configuration:** `CyclePreference` takes no wrap parameter. Wrap is decided by the
section's own input handler when it cycles the value.

**Shipped:** `CyclePreference` (`ui/components/PreferenceItem.kt`) renders
`EnumValueControl` (`ui/primitives/Controls.kt`) inline and opens `EnumPickerModal`
(`ui/components/EnumPickerModal.kt`) on A/Tap.

**Examples:** Theme mode, controller layout, border style

**Options are tokens, labels are resources.** A cycle's selection mechanism is
`options.indexOf(currentValue)`, and the value it writes back is an element of
`options`, so those entries are the persisted DataStore tokens and must stay byte-exact
literals. Only the rendered label becomes a `stringResource`. `LibretroSettingDef.Cycle`
models this directly: `options` (on-disk, upstream-exact) alongside `labels`, resolved
through `labelFor()`. Getting it backwards is silent - a miss coerces to index 0 rather
than throwing, so the user's setting resets to the first option with nothing logged.

---

### 3. Stepper (SliderPreference)
Discrete stepped values; V2 silhouette is `- value +`.

| Input | Behavior |
|-------|----------|
| A | No action - A never adjusts (V2) |
| LEFT / RIGHT / Tap - or + | Adjust value, clamp at bounds with haptic feedback |

**Boundary Behavior:**
- Clamp at min/max, trigger `HapticPattern.BOUNDARY_HIT`

**Shipped:** `SliderPreference` (`ui/components/PreferenceItem.kt`) renders
`StepperControl` (`ui/primitives/Controls.kt`); A does not adjust.

**Examples:** UI scale, blur amount, dim level

---

### 4. Track Slider (TrackSliderPreference)
Draggable slider track for continuous or fine-grained values.

| Input | Behavior |
|-------|----------|
| Drag | Adjust value continuously via touch |
| LEFT / RIGHT | Adjust by step amount, clamp at bounds with haptic |
| A / Tap | No action (use drag or D-pad) |

**Responsive Layout:**
- Wide displays (ULTRA_WIDE, WIDE): Slider track takes right 50% of row
- Square/Tall displays (STANDARD, TALL, ULTRA_TALL): Slider takes full width below title

```kotlin
val aspectRatioClass = LocalUiScale.current.aspectRatioClass
val isWideDisplay = aspectRatioClass == AspectRatioClass.ULTRA_WIDE ||
                    aspectRatioClass == AspectRatioClass.WIDE
```

**Examples:** LED brightness. `AmbientLedSection` is the only user of
`TrackSliderPreference`; the Quick Settings vibration row is a local
`VibrationStrengthSlider` in `ui/components/QuickSettingsPanel.kt`, not this component.

---

### 5. Counter
Discrete integer ranges (subset of stepper behavior).

| Input | Behavior |
|-------|----------|
| A | No action - A never adjusts |
| LEFT / RIGHT | Increment/decrement by 1, clamp with `HapticPattern.BOUNDARY_HIT` |

**Examples:** Ratings (1-10), concurrent downloads, enum-as-counter

---

### 6. Action (ActionPreference)
Buttons that trigger operations.

| Input | Behavior |
|-------|----------|
| A / Tap | Execute action |
| LEFT / RIGHT | No action |

**Examples:** Clear cache, start migration, open downloads

---

### 7. Navigation (NavigationPreference)
Links to other screens or sections.

| Input | Behavior |
|-------|----------|
| A / Tap | Navigate to target |
| LEFT / RIGHT | No action |

**Examples:** Platform settings, emulator configuration

---

### 8. Info (InfoPreference)
Read-only display values.

| Input | Behavior |
|-------|----------|
| All inputs | No click/confirm handling |

`InfoPreference` takes `isFocused: Boolean` and renders a focused state. Focusability is
decided by the sealed item's `isFocusable`, not by the component, and Info rows are commonly
focusable - see `SteamItem.GnStatus`, `InstallTriage` and `AccountInfo` in
`ui/screens/settings/sections/SteamSection.kt`.

**Examples:** Version number, stats, storage usage

---

### 9. Color Picker (ColorPickerPreference / HueSliderPreference)
Color selection.

| Input | Behavior |
|-------|----------|
| A / Tap | Open picker or select preset |
| LEFT / RIGHT | Adjust hue value |

**Examples:** Accent color, secondary color

---

### 10. Expandable (ExpandablePreference)
Collapsible groups with child items.

| Input | Behavior |
|-------|----------|
| A / Tap | Toggle expanded/collapsed |
| LEFT / RIGHT | No action (or navigate children if expanded) |

**Examples:** Platform settings groups

---

## Visibility & Disabled Handling

### Two-Layer System

**Layer 1: Item Visibility (`visibleWhen`)** - real example from
`ui/components/QuickSettingsPanel.kt`:
```kotlin
data object VibrationStrength : QuickSettingsItem(
    "vibrationStrength", "audioVisual",
    visibleWhen = { it.vibrationSupported && it.hapticEnabled }
)
```

**Layer 2: Disabled Behavior (`DisabledBehavior`)**

| Behavior | Visible? | Focusable? | Use Case |
|----------|----------|------------|----------|
| `HIDDEN` | No | No | Default - item removed from list |
| `LOCKED` | Yes | No | Show greyed-out, can't interact |

### Index Management

```
visibleItems()     = items where visibleWhen(state) OR disabledBehavior == LOCKED
focusableItems()   = visibleItems filtered by isFocusable(item)

focusIndex         = position in focusableItems (gamepad navigation)
listIndex          = position in visibleItems (LazyColumn rendering)
```

**Conversion Functions:**
- `focusIndexOf(item, state)` - Item -> focus index
- `itemAtFocusIndex(index, state)` - Focus index -> Item
- `focusToListIndex(focusIndex, state)` - Focus index -> list index
- `maxFocusIndex(state)` - Maximum valid focus index

### Navigation Rules

When an item becomes hidden/disabled:
1. Focus index may become invalid
2. Clamp focus to `maxFocusIndex(state)` after state changes
3. If focused item becomes unfocusable, move to next focusable item

---

## Construction Patterns

### Sealed Class Item Hierarchy
```kotlin
private sealed class SectionItem(
    val key: String,
    val visibleWhen: (SectionState) -> Boolean = { true }
) {
    data object Toggle1 : SectionItem("toggle1")
    data object Slider1 : SectionItem(
        key = "slider1",
        visibleWhen = { it.someCondition }
    )
    companion object {
        val ALL = listOf(Toggle1, Slider1, ...)
    }
}
```

### Layout Manager
```kotlin
private val sectionLayout = SettingsLayout<SectionItem, SectionState>(
    allItems = SectionItem.ALL,
    isFocusable = { it !is SectionItem.Header },
    visibleWhen = { item, state -> item.visibleWhen(state) },
    sectionOf = { it.section }
)
```

### Render Shell
`SectionPaneLayout` (`ui/screens/settings/components/SectionPaneLayout.kt`) is the shell
26 section files render into. It owns `SectionFocusedScroll` and switches between a
split-pane layout (wide displays with 2+ named sections: nav rail on the left, content on
the right) and a plain sticky-header LazyColumn. A new section renders through it rather
than building its own LazyColumn.

### Focus Check Pattern
```kotlin
fun isFocused(item: SectionItem): Boolean =
    uiState.focusedIndex == sectionLayout.focusIndexOf(item, state)
```

### Input Handler Pattern
```kotlin
override fun onLeft(): InputResult {
    when (itemAtFocusIndex(state.focusedIndex, state)) {
        is SectionItem.Slider1 -> {
            viewModel.adjustSlider1(-STEP)
            return InputResult.HANDLED
        }
        is SectionItem.Enum1 -> {
            viewModel.cycleEnum1(direction = -1)
            return InputResult.HANDLED
        }
        else -> return InputResult.UNHANDLED
    }
}
```

---

## Footer Hints (V2: the control is the guide)

The inline affordance carries the interaction; the bar is not a crutch. It exists to surface
what is NOT obvious from the focused control.

### What earns a hint (V2)
- Screen-global verbs (filter, search, compose), shoulder paging (LB/RB), genuinely
  non-obvious bound buttons (X/Y, triggers).
- A / B / d-pad / Back are LOW priority: alone they never justify a bar. If they are the only
  candidates, show no bar (it collapses by sliding below the edge, never blanks).
- One app-root bottom bar (singleton). No modal or drawer owns its own footer.
  **Committed exception:** a modal rendered above an overlay that covers the root bar
  shows its own hints inline -- pass `inlineFooterHints = true` + `footerHints = ...` to
  `Modal` (see `HotkeysModal` and `InputMappingModal` in
  `ui/screens/settings/components/`).

### Space-Constrained Filtering (Auto-Hide Order, V2)
Shed the OBVIOUS guides first; never drop a non-obvious hint to keep an obvious one:
1. D-pad hints (first to hide)
2. A/B standard hints (then Start/Select, then bumpers/triggers)
3. Non-obvious hints (X/Y) - last to hide

**Shipped:** `FooterHint.hidePriority()` implements this order (X/Y=4, bumpers/triggers=3,
Start/Select=2, A/B=1, d-pad=0; higher survives longer).

### Hint Labels Are Resources, Never Button Letters

A hint's label is the action ("Select", "Back", "Filter") as a `@StringRes Int`, and the
button is rendered as an `InputGlyph` from the hint's `InputButton`. Never write the
letter into the label text: A/B are user-swappable, so a baked-in letter describes the
wrong button the moment someone swaps them, and it hardcodes an English glyph into every
translation. Identical hint text on two screens gets two keys.

### Face Button Naming Convention

Use **intended button names** (A/B/X/Y), not positions:
- **A** = Primary action (confirm, select, toggle, activate) - NEVER adjust
- **B** = Back/cancel action
- **X** = Secondary action (edit, filter, preview)
- **Y** = Tertiary action (favorite, delete, clear)

The footer automatically shows the correct icon based on controller type and user swap preferences.

### Width-Based Filtering (filterHintsByWidth)
There is no per-aspect-ratio hint cap. `FooterBar` runs every hint list through
`filterHintsByWidth` (`ui/components/FooterHint.kt`), which is `private` to that file -
you cannot call it, you only get its behavior by rendering a footer bar:
- Estimates each hint's width (icon 22dp, 44dp for composite LB_RB/LT_RT, plus
  ~7dp per label character) against `screenWidthDp` minus padding.
- If everything fits, shows everything.
- Otherwise keeps hints in descending `hidePriority()` order until the width runs out.
- Floor of 2: never shows fewer than the two highest-priority hints, even if they
  overflow the estimate.

### Tappable Hints
All footer hints MUST support tap via `onHintClick` callback.

### Hint Display Order (in footer)
- Left side: D-pad, Bumpers (LB/RB)
- Right side: Shoulders (LT/RT), Start/Select, Face buttons

### Standard Hints by Item Type

| Item Type | Hints |
|-----------|-------|
| Toggle | A="Toggle", B="Back" |
| Enum | DPAD_HORIZONTAL="Adjust", A="Options" (opens picker), B="Back" |
| Stepper | DPAD_HORIZONTAL="Adjust", B="Back" (A does nothing - never hint it) |
| Track Slider | DPAD_HORIZONTAL="Adjust", B="Back" |
| Counter | DPAD_HORIZONTAL="Adjust", B="Back" (A does nothing - never hint it) |
| Action | A="Select", B="Back" |
| Navigation | A="Open", B="Back" |

A never adjusts a value. A hint on an adjustable item is only valid when A opens
something (the enum picker modal); "A=Cycle" is always wrong.

### Defaults + Overrides Pattern
```kotlin
val hints = buildList {
    // Section-specific hints first (higher priority)
    if (currentSection == SettingsSection.BOX_ART) {
        add(FooterHintItem(LB_RB, "Preview Shape"))
        add(FooterHintItem(LT_RT, "Preview Game"))
    }
    // Then add defaults for focused item type
    addAll(defaultHintsFor(focusedItemType))
}
```

---

## Modal Patterns

### Modal Types

| Component | Purpose | Overlay Alpha |
|-----------|---------|---------------|
| `Modal` | Standard modal (supports `inlineFooterHints` + `footerHints`) | 0.7 dark / 0.5 light |
| `CenteredModal` | Center-aligned content | 0.7 dark / 0.5 light |
| `NestedModal` | Secondary modal on top | 0.5 dark / 0.35 light |
| `ModalScaffold` (`ui/primitives/ModalScaffold.kt`) | V2 scrim + GlassPanel shell; pure visuals, captures no input | 0.55 black |
| `ArgosyConfirmModalHost` (`ui/primitives/ConfirmModal.kt`) | V2 confirm modal host; pushes onto the modal input stack IMMEDIATELY on open | - |

### Current Input-Routing Infrastructure (settings)

- `ArgosyConfirmModalHost` is the standard way to show a confirm modal WITH input
  capture handled for you.
- `ModalInputRouter` (`ui/screens/settings/ModalInputRouter.kt`) routes gamepad input to
  whichever settings modal is open, before section handlers see it.
- `SettingsConfirmRouter` (`ui/screens/settings/SettingsConfirmRouter.kt`) routes
  `onConfirm` per section via file-level `routeConfirm(vm)` + per-section
  `route*Confirm` functions -- trace it before wiring a new confirmable row.
- `SettingsGeneralRouter` (`ui/screens/settings/SettingsGeneralRouter.kt`) owns section
  navigation (`routeNavigateToSection` and the `routeNavigateTo*` family) and the LEFT/RIGHT
  adjust routes, including the `HapticPattern.BOUNDARY_HIT` fired when a value is already
  clamped.
- `LightSectionsInput` (`ui/screens/settings/sections/input/LightSectionsInput.kt`) is the
  shared handler `SettingsInputHandler` installs for 14 sections. A section that only needs
  standard up/down/confirm needs NO new handler file - add it to that list instead.
- `ModalScaffold` is visuals only; pair it with one of the capture mechanisms from the
  code-quality skill (modal input capture rules).

### Modal State Pattern
```kotlin
data class SectionState(
    val showMyModal: Boolean = false,
    val myModalFocusIndex: Int = 0,
    val myModalButtonIndex: Int = 0,  // For multi-button modals
    val myModalInfo: ModalInfo? = null
)
```

### Modal Input Handling

Input handler checks modals FIRST (deepest modal first):
```kotlin
override fun onDown(): InputResult {
    val state = viewModel.uiState.value

    if (state.showNestedModal) {
        viewModel.moveNestedModalFocus(1)
        return InputResult.HANDLED
    }
    if (state.showMainModal) {
        viewModel.moveMainModalFocus(1)
        return InputResult.HANDLED
    }

    viewModel.moveFocus(1)
    return InputResult.HANDLED
}

override fun onBack(): InputResult {
    if (state.showNestedModal) {
        viewModel.dismissNestedModal()
        return InputResult.HANDLED
    }
    if (state.showMainModal) {
        viewModel.dismissMainModal()
        return InputResult.HANDLED
    }
    // Handle normal back
}
```

### Modal Focus Management
- Modal focus is independent from main menu focus
- Reset modal focus to 0 when opening (exception: pickers showing current value may pre-select it)
- Preserve main menu focus while modal is open
- Modal content must scroll if it would overflow and hide the footer

### Nested Modals
```kotlin
if (showOuterModal) {
    Modal(title = "Outer", onDismiss = { dismissOuter() }) {
        // Content that can trigger nested modal
    }
}
if (showNestedModal) {
    NestedModal(title = "Nested", onDismiss = { dismissNested() }) {
        // Nested content
    }
}
```

### Modal Input Summary

| Input | Action |
|-------|--------|
| UP/DOWN | Navigate options |
| A/Tap | Confirm selection |
| B/Back | Dismiss modal |
| LEFT/RIGHT | Adjust values (sliders/enums) |

---

## Section Navigation (LB/RB)

### Section Definition
```kotlin
private sealed class SectionItem(
    val key: String,
    val section: String,  // Section identifier
    val visibleWhen: (State) -> Boolean = { true }
) {
    data object Item1 : SectionItem("item1", "general")
    data object Item2 : SectionItem("item2", "advanced")
}
```

### Building Sections
```kotlin
private val layout = SettingsLayout<SectionItem, State>(
    allItems = SectionItem.ALL,
    isFocusable = { true },
    visibleWhen = { item, state -> item.visibleWhen(state) },
    sectionOf = { it.section }
)

val sections: List<ListSection> = layout.buildSections(state)
```

### ListSection Structure
```kotlin
data class ListSection(
    val name: String? = null,
    val listStartIndex: Int,
    val listEndIndex: Int,
    val focusStartIndex: Int,
    val focusEndIndex: Int
)
```

`name` is populated by `SettingsLayout.buildSections` from `sectionTitle(sectionName)`; a null
name means the section renders no nav entry in the split-pane layout.

### Section Jump Implementation

The idiom is to hand the section list to the ViewModel and let it move focus. Do not
hand-roll the search:
```kotlin
override fun onPrevSection(): InputResult {  // LB
    if (viewModel.jumpToPrevSection(themeSections())) {
        return InputResult.HANDLED
    }
    return InputResult.UNHANDLED
}

override fun onNextSection(): InputResult {  // RB
    if (viewModel.jumpToNextSection(themeSections())) {
        return InputResult.HANDLED
    }
    return InputResult.UNHANDLED
}
```

Followed by `ThemeSectionInput`, `StorageSectionInput` and `AmbientLedSectionInput` in
`ui/screens/settings/sections/input/`. `EmulatorsSectionInput` still hand-rolls the scan
because it builds its layout info from live platform state; it is the exception, not the
model to copy.

### Context-Specific Section Actions
LB/RB may be repurposed per screen:
```kotlin
override fun onNextSection(): InputResult {
    when (state.currentSection) {
        SettingsSection.BOX_ART -> {
            viewModel.cycleBoxArtShape(1)
            return InputResult.HANDLED
        }
        else -> return InputResult.UNHANDLED
    }
}
```

### Section Scroll
```kotlin
SectionFocusedScroll(
    listState = listState,
    focusedIndex = state.focusedIndex,
    focusToListIndex = { layout.focusToListIndex(it, state) },
    sections = layout.buildSections(state)
)
```

### Top-Level Rows (there is no LB/RB fallback)

LB/RB is per-section only. When a section handler's `onPrevSection`/`onNextSection` returns
UNHANDLED, `SettingsInputHandler` swallows it - its fallbacks return a bare
`InputResult.HANDLED` and nothing jumps. There is no `TOP_LEVEL_SECTIONS` constant; the
companion object holds only `SLIDER_STEP`, `FONT_SCALE_STEP` and `HUE_STEP`.

The top level is a list of rows, `MainSettingsItem.ALL` in
`ui/screens/settings/sections/MainSettingsSection.kt`, grouped under headers:

```
LAUNCHER     Theme, Interface, Navigation, Audio, Displays
GAMEPLAY     BuiltinEmulator, Saves, RetroAchievements, Bios, Drivers
LIBRARY      Platforms, Storage
CONNECTIONS  RomM, Steam, Social
SYSTEM       Permissions, DeviceSettings, About
```

Navigating INTO one of those rows goes through `routeNavigateToSection`, which resets focus
to 0 and preserves `parentFocusIndex`.

There is no `EMULATORS` section; emulator config lives under `PLATFORMS` (which binds to
`EmulatorsSectionInput`) and `BUILTIN_EMULATOR`. Theme sub-sections exist in
`SettingsSection`, but `THEME_SOUNDS` and `THEME_MUSIC` now back out to `AUDIO`, not
`THEME` - check `SettingsConfirmRouter`'s back routing before assuming a parent.

---

## Clickable Modifiers

**See `code-quality` skill for full details.**

All interactive elements MUST use `clickableNoFocus` from `ui/util/Modifiers.kt`:

```kotlin
import com.nendo.argosy.ui.util.clickableNoFocus

Modifier.clickableNoFocus { onItemClick() }
Modifier.clickableNoFocus(enabled = isEnabled) { onItemClick() }
```

**NEVER use plain `Modifier.clickable()`** - it enables Compose TV focus which conflicts with our InputHandler-based focus system.

---

## Files to Reference

| File | Purpose |
|------|---------|
| `ui/components/PreferenceItem.kt` | Preference components (Cycle/Slider/Switch/TrackSlider...) |
| `ui/components/ExpandablePreference.kt` | ExpandablePreference |
| `ui/components/EnumPickerModal.kt` | Full-list picker opened by CyclePreference on A |
| `ui/components/FooterHint.kt` | Footer hint system (InputButton, FooterBar, filterHintsByWidth) |
| `ui/components/Modal.kt` | Modal, CenteredModal, NestedModal |
| `ui/components/SectionScroll.kt` | FocusedScroll, SectionFocusedScroll |
| `ui/primitives/Controls.kt` | EnumValueControl, StepperControl (V2 inline affordances) |
| `ui/primitives/ConfirmModal.kt` | ArgosyConfirmModalHost |
| `ui/primitives/ModalScaffold.kt` | V2 modal shell (visuals only) |
| `ui/primitives/Focus.kt` | FocusIndicators presets, argosyFocusIndicators |
| `ui/screens/settings/menu/SettingsLayout.kt` | Layout manager |
| `ui/screens/settings/components/SectionPaneLayout.kt` | Section render shell (split-pane / sticky-header list) |
| `ui/screens/settings/SettingsInputHandler.kt` | Per-section handler map, dispatch, fallbacks |
| `ui/screens/settings/sections/input/LightSectionsInput.kt` | Shared handler for 14 plain sections |
| `ui/screens/settings/ModalInputRouter.kt` | Modal-first input routing |
| `ui/screens/settings/SettingsConfirmRouter.kt` | Per-section confirm routing |
| `ui/screens/settings/SettingsGeneralRouter.kt` | Section navigation, LEFT/RIGHT adjust, boundary haptics |
| `ui/screens/settings/SettingsModels.kt` | State data classes, SettingsSection enum |
| `ui/screens/settings/sections/*.kt` | Section examples |
| `ui/input/HapticFeedback.kt` | HapticPattern enum |
