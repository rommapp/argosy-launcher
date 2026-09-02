---
name: dual-screen
description: Dual-screen development reference. Load this before implementing any dual-screen feature.
---

# Dual-Screen Development Reference

Architecture: OSOT (one source of truth). `DualScreenManager` (DSM) owns ALL
cross-activity state as StateFlows; both activities are consumers. There is no
messaging layer between the activities.

All paths below are under `app/src/main/kotlin/com/nendo/argosy/`. References are by
SYMBOL, not line number - offsets rot on the next edit above them. Grep the symbol.

## NAMING WARNING (read first)

`SecondaryHomeBroadcastHelper` and every `broadcast*` method name in it (and a
few on DSM: `broadcastForegroundState`, `broadcastUnifiedSaves`,
`broadcastSessionCleared`, `broadcastOpenOverlay`) are HISTORICAL names from a
deleted broadcast-based layer. They are plain, same-process method calls into
DSM / `CompanionHost`. `DualScreenBroadcasts.kt` no longer exists; neither
activity registers a receiver or calls `sendBroadcast` for cross-activity
communication (verify: zero `sendBroadcast`/`registerReceiver` hits in
`hardware/SecondaryHomeActivity.kt` and `hardware/SecondaryHomeBroadcastHelper.kt`).
A rename is queued; until then do NOT let the names suggest a broadcast layer,
and do NOT add Android broadcasts between the activities.

DSM's own `registerReceivers()` / `unregisterReceivers()` (called from MainActivity) are
part of the same mess: they register a DisplayManager display listener, not a
BroadcastReceiver, and nothing about them concerns companion messaging.

## OSOT Model (the spine)

```
                 DualScreenManager (DualScreenManager.kt)
                 held in DualScreenManagerHolder.instance
   StateFlows (pull)                      CompanionHost (push)
   dualScreenShowcase                     interface CompanionHost in DSM
   dualGameDetailState                    implemented by SecondaryHomeActivity
   dualViewMode / dualAppBarFocused
   dualDrawerOpen / dualCollectionShowcase
   dualSyncOverlay / dualSaveConflict (+focus indexes)
   pendingOverlayEvent / isCompanionActive
   isRolesSwapped / isDualScreenDevice
   swapped* family
        |                                        |
        v                                        v
   MainActivity + ArgosyApp               SecondaryHomeActivity
   (creates DSM, collects flows            (collects flows in
    in onCreate)                            initializeCompanion, receives
                                            CompanionHost pushes)
```

- MainActivity creates DSM (or rebinds to the existing Holder instance) in
  `onCreate` and clears the Holder only when finishing.
- Companion -> DSM direction is plain method calls, routed through
  `SecondaryHomeBroadcastHelper` (`dsm.onGameSelected`, `dsm.handleDirectAction`,
  `dsm.handleInlineUpdate`, ...).
- DSM -> companion direction is `companionHost?.onX(...)` pushes.

LAW: never anchor shared state in an activity.
- Rule: any state both displays can render, or that must survive a companion
  respawn, lives as a DSM StateFlow.
- Why: the showcase role renders entirely from DSM flows collected in
  `SecondaryHomeActivity.initializeCompanion`; state parked in MainActivity never
  reaches it. State parked in SecondaryHomeActivity dies on respawn - the OS recreates
  the SECONDARY_HOME activity at will, and `onResume` reconnects to a possibly
  NEW DSM (the stale-DSM reconnect).
- Exception: purely local UI state (e.g. `isScreenshotViewerOpen`,
  `launchedExternalApp` in SecondaryHomeActivity).
- Boundary: the moment the other display renders it, or a respawn must restore
  it, it moves to DSM (or SessionStateStore if it must survive process death).

## Process and Lifecycle (verified, carried over)

ONE process, two activities. SecondaryHomeActivity has no `android:process`;
it is NOT a Hilt entry point and reaches shared singletons through `dsm.*`
internals (manual VM construction in `initializeDependencies()`).

- Manifest: SecondaryHomeActivity is the SECONDARY_HOME activity, intent-filter
  priority 1000 (MAIN + SECONDARY_HOME + DEFAULT), `launchMode=singleTop`,
  `taskAffinity=""`, `excludeFromRecents` (grep `SecondaryHomeActivity` in
  AndroidManifest.xml). The OS pins it: `finish()` respawns it. To actually
  remove it, disable the component: `SecondaryHomeComponent.setEnabled(context,
  false)` (util/SecondaryHomeComponent.kt; called from `applyDualScreenEnabled`).
- FGS guard: `CompanionGuardService`, foregroundServiceType
  `specialUse|dataSync`, subtype property `companion_display_guard` (grep
  `companion_display_guard` in AndroidManifest.xml), keeps the display session alive.
- onCreate gate: `SessionStateStore.isDualScreenEnabled()` finishes immediately
  when off.
- DSM acquisition on companion boot (`onCreate`): use the Holder instance if
  present; if respawned without a running Argosy and not default home, disable the
  component and finish; else launch MainActivity on the default display and poll
  the Holder (100ms x 50) before initializing.
- Stale-DSM reconnect: `onResume` compares `dsm` to the current Holder instance
  and re-runs `initializeCompanion()` on mismatch. Any init-time wiring you add
  must live inside that path or it will be lost across a MainActivity recreate.
- Theme: `SecondaryHomeTheme` + live prefs collected through
  `dsm.preferencesRepository` keyed on `isInitialized`. V2 theme locals are
  available on the lower display; custom fonts flow through the `fonts` parameter.

## File Map (what lives where)

- `DualScreenManager.kt` - all shared state, modal state machine, game actions,
  save operations, launch/display routing, companion lifecycle watchdogs.
- `DualScreenManagerHolder.kt` - `@Volatile var instance` (the whole file is 6
  lines). Set by MainActivity, read everywhere else.
- `hardware/SecondaryHomeActivity.kt` (993 lines) - lifecycle, CompanionHost
  implementation, key dispatch entry, DSM flow collection. Logic is extracted
  to the three helpers below; do not grow the activity.
- `hardware/SecondaryHomeInputHandler.kt` - ALL companion gamepad routing:
  `routeInput`, per-view-mode handlers, `handleModalInput`,
  `handleCompanionInput` (in-game dashboard), drawer input.
- `hardware/SecondaryHomeStateManager.kt` - boot-time state restore
  (`loadInitialState`, incl. CarouselNavContext + GAME_DETAIL restore),
  `loadInputSwapPreferences`, `inputSwapStateFrom`, `loadCompanionGameData`,
  `createGameDetailViewModel`, `persistCarouselPosition`.
- `hardware/SecondaryHomeBroadcastHelper.kt` - thin adapter, companion -> DSM
  method calls only (see NAMING WARNING).
- `hardware/SecondaryHomeComposables.kt` - `CompanionScreen` enum,
  `SecondaryHomeContent` (normal role), `ShowcaseRoleContent` (swapped role),
  lower dimming wiring.
- `hardware/CompanionPanel.kt` - `CompanionInGameState`,
  `withLiveQuickActionState`, `CompanionSessionTimer`.
- `ui/dualscreen/home/DualHomeViewModel.kt` - lower home state,
  `DualHomeFocusZone`, `DualHomeViewMode`, `ForwardingMode`, nav-context
  save/restore.
- `ui/dualscreen/gamedetail/DualGameDetailModels.kt` - `DualGameDetailTab`
  {SAVES, STATES, MEDIA, OPTIONS}, `ActiveModal`, `GameDetailOption`,
  `DualGameDetailUpperState`, save-entry JSON DTOs.
- `ui/dualscreen/ShowcaseViewModel.kt` - touch/modal input adapter for the
  showcase role, gated by `isControlActive`.
- `util/DisplayAffinityHelper.kt` - display enumeration, usability latch, launch
  options.
- `data/preferences/SessionStateStore.kt` - SharedPreferences persistence layer
  (session flags, swap prefs, `CarouselNavContext`, companion screen).

## Input Flow

Dedup first, always: every dispatch path calls `dsm.claimInput(event)` before
handling (`MainActivity.dispatchKeyEvent` and `dispatchGenericMotionEvent`,
`SecondaryHomeActivity.onKeyDown`, plus the libretro activity). First claimant
wins; parallel deliveries of the same physical event are dropped
(`InputDedupBuffer`, reached via `DualScreenManager.claimInput`). New dispatch
paths MUST claim before handling.

Companion side (`SecondaryHomeActivity.onKeyDown`), in order:
1. `dsm.claimInput` - return if already claimed.
2. Sync-conflict then save-conflict handlers (these read DSM's
   `dualSyncOverlay`/`dualSaveConflict` and consume everything while active).
3. Showcase branch: if `isShowcaseRole`, only showcase-modal events are handled
   (via `ShowcaseViewModel.handleModalGamepadEvent`, and only while Argosy is
   backgrounded); everything else falls through to `super`.
4. `inputHandler.routeInput(event, true, isGameActive, currentScreen)`.

`routeInput` (SecondaryHomeInputHandler): conflicts again (forwarded keys enter
here directly), then HOME/GAME_DETAIL handlers when Argosy is foreground and no
game is active, else `handleCompanionInput` (the in-game/backgrounded app-bar
dashboard; returns UNHANDLED on external displays).

Primary side (`MainActivity.dispatchKeyEvent`), in order:
1. `dsm.claimInput`.
2. `dsm.handleConflictInput` - conflict overlays win on both sides.
3. Game on the other display and no overlay focused -> forward raw event to
   `dsm.emulatorKeyDispatcher` (cross-display session input).
4. Not swapped + on Home + companion active + no overlay ->
   `companionHost?.onForwardKey(keyCode, swapAB, swapXY, swapStartSelect)`. The
   companion re-maps and feeds the SAME `routeInput`. Sticks take the same path
   via `dispatchGenericMotionEvent`.
5. Stale-link reassert (`reassertCompanionForwarding`).
6. Local handling via GamepadInputHandler.

Rule: the companion's `SecondaryHomeInputHandler` is the single gamepad brain
for lower-screen content, whichever activity physically received the key.
- Exception: FILE_PICKER and SAVE_NAME modals (upper-owned, table below) and
  the swapped role (upper is the interactive screen).
- Why: forwarded and direct keys converge on `routeInput`, so state moves in
  exactly one place.
- Boundary: if you handle a key on the upper screen for lower-screen content,
  you have created a second brain - move it.

Swap prefs reach the companion by TWO paths, and only one of them is live:
- Boot: `loadInitialState` calls `stateManager.loadInputSwapPreferences()` once and
  writes the activity fields. This reads the `SessionStateStore` mirror and IGNORES
  `prefs.controllerLayout` entirely.
- Live: `initializeCompanion` collects `dsm.preferencesRepository.userPreferences`
  and calls `applyInputSwapState(stateManager.inputSwapStateFrom(prefs))` on every
  emission. `inputSwapStateFrom` is a DIFFERENT method from
  `loadInputSwapPreferences` and is the ONLY place the companion honours the
  Controller Layout override.

A swap-affecting preference wired only into the boot path silently never goes live -
add it to `inputSwapStateFrom` too. The resulting fields feed every
`mapKeycodeToGamepadEvent` call; icon swaps flow through CompositionLocals
`LocalABIconsSwapped` / `LocalXYIconsSwapped` / `LocalSwapStartSelect`.
`ControllerDetector` lives at `core/input/ControllerDetector.kt` (NOT ui/input).

## Role Swap / Showcase Mode

`isRolesSwapped` mirrors DisplayRoleResolver output (override pref + display
type; external HDMI defaults swapped). `swapRoles()` toggles the override -
debounced 500ms, NO-OP while a session is active.

When swapped, the roles invert:
- The PRIMARY activity becomes the interactive screen, driven by DSM's swapped
  mirror state: `swappedDualHomeViewModel` (built by `initSwappedViewModel`,
  triggered from MainActivity's `onRoleSwapped` callback),
  `swappedCurrentScreen`, `swappedGameDetailViewModel` (created by
  `selectGameSwapped`), `swappedIsGameActive`, `swappedCompanionState`,
  `swappedSessionTimer`.
- The COMPANION becomes a passive showcase: `onRoleSwapped(isSwapped)` sets
  `isShowcaseRole` and setContent renders `ShowcaseRoleContent` from the
  DSM-mirrored flows. Showcase touch on modals goes through `ShowcaseViewModel`
  straight into DSM confirm/move methods.
- HDMI unplug mid-swap: `cleanupSwappedState` resets the override to AUTO,
  clears swapped VMs/timers, and ends the session if the emulator was on the
  removed display.
- Per-game display targets can force an effective swap at launch only:
  `resolveEmulatorDisplaySwapped` maps HERO/LIBRARY/TOP/BOTTOM, and
  `preGameRolesSwapped` restores the pre-launch state at session end.

This is why the OSOT law exists: the same feature must render on whichever
display currently holds the showcase role, and only DSM flows reach both.

## ForwardingMode + Overlay Event Flow

`ForwardingMode { NONE, OVERLAY, BACKGROUND }` (DualHomeViewModel.kt). While
!= NONE the lower home swallows all input (guard at the top of `routeInput`'s
home path in SecondaryHomeInputHandler).

OVERLAY (drawer / quick menu / quick settings on the upper screen):
1. Companion presses Menu/L3/R3 -> `broadcasts.broadcastOpenOverlay(name)`
   with the name from `overlayNameFor` (names OVERLAY_MENU / QUICK_MENU /
   QUICK_SETTINGS, matched in DSM's `onOpenOverlayFromCompanion`).
2. Helper sets `startDrawerForwarding()` (OVERLAY) then calls
   `dsm.onOpenOverlayFromCompanion`.
3. DSM sets `isOverlayFocused = true`, publishes `pendingOverlayEvent`, calls
   `refocusMain()`.
4. ArgosyApp collects `pendingOverlayEvent`, opens the matching overlay, then
   calls `clearPendingOverlay()`.
5. Close: every overlay-close observer funnels into `notifyOverlayClosed` in
   ArgosyApp -> `isOverlayFocused = false` + `companionHost.onOverlayClosed()` +
   `refocusSelf()`; the companion's `onOverlayClosed` calls
   `stopDrawerForwarding()`.

BACKGROUND: an overlay closed while the upper is NOT on the Home route (user
navigated into Apps/Settings) -> `companionHost.onBackgroundForward()` -> lower
enters BACKGROUND forwarding: keys swallowed, tap on the lower screen refocuses
the upper. Returning to Home fires `notifyOverlayClosed` and clears it.

Safety nets: dual-screen topology changes reset overlay focus and modal state
(the ArgosyApp `LaunchedEffect` keyed on `isRolesSwapped`, `companionActive`,
`swappedGameActive` - grep `dsmForTopology`); `reassertCompanionForwarding`
clears a latched `isOverlayFocused` when input arrives on a stale link.

## Modal System

Modals render on the upper screen inside `DualGameDetailUpperScreen`, state
lives in DSM's `dualGameDetailState` (`DualGameDetailUpperState.modalType`).
Opening always goes through a DSM `open*Modal` method which sets state and
calls `refocusMain()`. Those methods are NOT contiguous in DualScreenManager.kt
- `openModal`, `openEmulatorModal`, `openCollectionModal`, `openSaveNameModal`,
`openDiscModal`, `openSteamInstallModal`, `openSteamChooserForHome` sit together,
while `openCoreModal`, `openSavePathModal`, `openDisplayTargetModal`,
`openMemoryCardModal` and `openVariantModal` are scattered several hundred lines
later. Grep `fun open`, do not scroll. The lower screen dims while a modal is
active (`isDimmed = activeModal != ActiveModal.NONE`, wired in
SecondaryHomeComposables.kt).

`ActiveModal` - 16 values (DualGameDetailModels.kt):
NONE, RATING, DIFFICULTY, STATUS, EMULATOR, CORE, SAVE_PATH, DISPLAY_TARGET,
MEMORY_CARD, COLLECTION, SAVE_NAME, DISC_PICKER, VARIANT_PICKER, STEAM_INSTALL,
FILE_PICKER, COVER_PICKER.

Input ownership per modal, normal (non-swapped) mode. "Companion-owned" =
`handleModalInput` (SecondaryHomeInputHandler) drives the companion VM and
mirrors focus to the upper via `broadcastInlineUpdate(<field>)` ->
`dsm.handleInlineUpdate`; confirm goes through `broadcastModalConfirmResult` ->
`dsm.onModalConfirmResult`.

| Modal | Owner | Mechanics (branch in `handleModalInput`) |
|---|---|---|
| NONE | - | no-op |
| RATING | Companion | Left/Right adjust, mirror `modal_rating`; Confirm/Back |
| DIFFICULTY | Companion | same branch as RATING |
| STATUS | Companion | Up/Down, mirror `modal_status` |
| EMULATOR | Companion | live focus forwarding via `broadcastInlineUpdate("emulator_focus")` |
| CORE | Companion | `core_focus` |
| SAVE_PATH | Companion | `save_path_focus` |
| DISPLAY_TARGET | Companion | `display_target_focus` |
| MEMORY_CARD | Companion | `memory_card_focus`; opened via `broadcastMemoryCardModalOpen` -> `dsm.openMemoryCardModal`; upper mirror is `moveDualMemoryCardFocus` / `confirmDualMemoryCardSelection` |
| COLLECTION | Companion | `collection_focus`; Confirm -> `collection_toggle` / `collection_create` |
| SAVE_NAME | UPPER | companion swallows ALL input incl. Back (the else fallthrough); text + confirm on the upper via `updateDualSaveNameText` / `confirmDualSaveName` on DSM |
| DISC_PICKER | Companion | `disc_focus`; Confirm closes modal + direct action PLAY_DISC |
| VARIANT_PICKER | Companion | `variant_focus` |
| STEAM_INSTALL | Companion | `steam_install_focus`; also opens from Home as a chooser (`openSteamChooserForHome`) |
| FILE_PICKER | UPPER | companion is Back-only dismiss; all focus/selection state is DSM `filePicker*` fields driven by the upper `dualModalInputHandler` and touch |
| COVER_PICKER | UPPER | search text needs the keyboard; state is DSM `coverPicker*` / `coverCandidates`, driven by `handleDualCoverPickerInput` (both handlers), the upper `dualModalInputHandler` and touch; opened via direct action CHANGE_COVER, X re-runs the search |

Rule: new picker modals are companion-owned with live focus forwarding -
copy the EMULATOR branch, not FILE_PICKER.
- Exception: modals needing upper-only capabilities (text entry: SAVE_NAME;
  complex multi-select rows: FILE_PICKER) are upper-owned; companion swallows
  input (Back-only dismiss at most).
- Why: a modal with no companion branch is a dead modal when the companion has
  window focus - keys land on the companion and vanish.
- Boundary: every new `ActiveModal` value MUST get an explicit
  `handleModalInput` branch, even if it is only Back-dismiss.

The upper `dualModalInputHandler` (ArgosyApp.kt, subscribed while
`dualModalActive`) mirrors every picker for the cases where the upper owns input
(overlay focus, swapped role); keep both sides in sync when adding a modal.

Result delivery: DSM confirm/dismiss methods push
`companionHost.onModalResult(...)`; the companion applies to its VM and
`refocusSelf()`. Watchdog: if the companion pauses with a modal open, DSM
auto-dismisses it after 5s (`onCompanionPaused`); `resyncCompanionState` also
clears stale modals on companion resume.

## ID-Based Carousel Restore

`SessionStateStore.CarouselNavContext`: restores the lower carousel by IDENTITY
(sectionKind + platformId + gameId) plus the full filter/sort context; legacy
index fields are fallback only. Persisted via
`stateManager.persistCarouselPosition` on selection moves and in `onStop`;
restored in `stateManager.loadInitialState` ->
`dualHomeViewModel.restoreNavContext`. The same load path also restores a
GAME_DETAIL screen (rebuilds the detail VM for the saved gameId) and clears any
persisted modal/screenshot-viewer state. Do not restore by index anywhere -
dynamic sections shift and the two screens end up on different games.

## CompanionInGameState

`CompanionInGameState` (hardware/CompanionPanel.kt) is the in-game dashboard
state. Merge rule: async metadata loads MUST NOT clobber the live quick-action
flags - always apply `withLiveQuickActionState(quickActionsAvailable,
hasQuickSave)` after building a fresh snapshot (used by
`SecondaryHomeActivity.loadCompanionGameData` and by DSM's own load). DSM's
`_swappedCompanionState` copy is canonical in BOTH companion modes;
`updateCompanionHasQuickSave` maintains it regardless of role.

## Session Survival Rules

- `dsm.hasLiveSession()` = in-memory PlaySessionTracker check, flips false the
  moment teardown BEGINS. `SessionStateStore.hasActiveSession()` = persisted
  flag, stays true until save sync completes. Pick deliberately: UI "is a game up
  right now" -> hasLiveSession; "is it safe to relaunch/companion-launch" ->
  hasActiveSession.
- Rule: Argosy UI foregrounded = session over. `onForegroundChanged` ends the
  companion's session view when Argosy comes foreground during a game.
  - Exception: showcase role checks `!dsm.hasLiveSession()` first - a
    cross-display session survives the upper UI foregrounding.
  - Why: on a single pair of displays, foregrounding the launcher means the
    game lost its screen; in swapped mode the game may still be running on the
    other display.
  - Boundary: only cross-display sessions survive.
- Companion resumed ON the emulator's display -> the game lost that display:
  end the session (`onResume`, guarded by `dsm.isLaunchingGame`).
- HDMI disconnect with the emulator on the removed display ends the session
  (`cleanupSwappedState`).
- `swapRoles()` refuses while `hasActiveSession()`.
- `ensureCompanionLaunched` refuses during an active session unless
  `allowDuringSession`; a startup guard retries every 1.5s until the companion
  is up.

## Display Affinity

`DisplayAffinityHelper.getActivityOptions(forEmulator: Boolean,
rolesSwapped: Boolean = false, overrideDisplayId: Int? = null)` - note the two
newer params: swapped launches route the emulator to the secondary display, and
`overrideDisplayId` bypasses resolution entirely. Emulator display resolution at
launch goes through `resolveEmulatorDisplaySwapped` (per-game/per-platform
`EmulatorDisplayTarget`); DSM records `emulatorDisplayId` at launch and it drives
cross-display input forwarding (`isGameOnOtherDisplay` in MainActivity) and the
session-survival rules above. Companion launch uses `getCompanionLaunchOptions()`.

`hasSecondaryDisplay` is gated by THREE conditions, not one:
`dualScreenEnabled && secondaryDisplayUsable && hasPhysicalSecondaryDisplay`.

- `dualScreenEnabled` is a plain var - set it from prefs before trusting it.
- `secondaryDisplayUsable` is a FALLBACK LATCH, not a preference. It starts true,
  is hydrated at MainActivity startup from
  `SessionStateStore.isSecondaryDisplayUsable()`, and is set false by
  `DualScreenManager.fallbackToSingleScreen(persistent)` once the companion has
  been proven unable to initialize on the secondary display (OS builds that will
  not run a home activity there). `DualScreenManager.reprobeSecondaryDisplay()`
  clears it, and re-enabling dual screen in settings sets it true directly.
- Consequence: on a device that latched false, every dual-screen entry point is
  off even though the hardware and the preference both say yes. Check the latch
  before diagnosing a "dual screen does nothing" report.

## Focus Zones and Visuals (kept)

- Home: `DualHomeFocusZone { CAROUSEL, APP_BAR }` and `DualHomeViewMode
  { CAROUSEL, COLLECTIONS, COLLECTION_GAMES, LIBRARY_GRID }`, both in
  DualHomeViewModel.kt; one input handler per mode in SecondaryHomeInputHandler.
- Detail tabs: SAVES (dual column, `SaveFocusColumn { SLOTS, HISTORY }` from
  `ui/common/savechannel/`), STATES, MEDIA (grid), OPTIONS (list + inline
  LEFT/RIGHT adjust for RATING/DIFFICULTY/STATUS which mirror via
  `broadcastInlineUpdate`).
- Lower dimming while a modal is up: SecondaryHomeComposables.kt passes
  `isDimmed` into `DualGameDetailLowerScreen`.
- Dual modality is non-negotiable: gamepad via SecondaryHomeInputHandler AND
  touch via `touchOnly`/`clickableNoFocus` on the composables (grep
  `broadcastRefocusUpper` in DualHomeLowerContent.kt for the dim-tap pattern).

## Shared Surfaces, Not Second Copies (read before "DS does not have this")

The home surfaces are ONE feature drawn on two displays. When something the
phone-sized home has is missing on dual screen, the fix is to render the SAME
component there, never to build a companion-only variant and never to hide the
entry that leads to it.

The shared layer already exists, and it is where new grid behaviour goes:

- `ui/home/grid/CustomGridCoordinator.kt` - every action the curated grid takes,
  for both surfaces. Behaviour belongs here, not in a view model.
- `ui/home/grid/DualCustomGridInputRouter.kt` - gamepad routing for the grid on a
  companion display, shared by both DS handlers.
- `ui/home/grid/PageChooserEntrySource.kt` - the rows the page chooser offers.
- `ui/components/` - `CustomTileMenuModal`, `HomeTilePickerModal`,
  `PageChooserModal`, `PageBackdrop`, `PageThemePlayer`. All take state in and
  hand callbacks out; none of them know which display they are on.

A missing DS feature is therefore almost always three small edits: render the
component in `DualHomeLowerContent`, route its input in
`DualCustomGridInputRouter`, and delegate the view-model calls in
`DualHomeViewModel`. Do not describe that as parity work to be scheduled.

Two rules that follow:

- Hiding a menu entry on DS is not a fix. If an action opens something the
  companion does not draw, build the consumption site - the setting is otherwise
  a ghost, which the AGENTS.md settings-chain law already forbids.
- A capability flag is only legitimate for something the surface genuinely
  cannot host, and it must name the blocker. Today there is exactly one:
  `PageChooserEntrySource.canBrowseFiles`, false on the companion because
  `FileBrowserScreen` needs `LocalInputDispatcher` and a `hiltViewModel()`, and
  `SecondaryHomeActivity` is deliberately not a Hilt entry point and provides no
  Compose input dispatcher. Removing that flag means giving the companion those
  two things, or pushing browsable rows from DSM the way FILE_PICKER does.

## New Dual-Screen Feature Checklist

1. [ ] State: add a StateFlow on DSM (never an activity field). Companion
       mutations go through a method on DSM, exposed to the companion via
       SecondaryHomeBroadcastHelper.
2. [ ] Push: if the companion must react to a main-side event, add a
       `CompanionHost` method (interface in DualScreenManager.kt), implement it
       in SecondaryHomeActivity, call it from DSM next to its state update.
3. [ ] Consumers: collect on the upper (ArgosyApp/MainActivity) AND, if the
       showcase role renders it, add a collector in `initializeCompanion` plus
       ShowcaseRoleContent wiring.
4. [ ] SHA overrides: any new CompanionHost method needs its
       SecondaryHomeActivity override to survive the stale-DSM reconnect path
       (re-wired by `initializeCompanion`).
5. [ ] Input: companion branch in SecondaryHomeInputHandler (routed via
       `routeInput`); upper branch in ArgosyApp's handler if the upper can own
       input for it; both sides inherit `dsm.claimInput` dedup from their
       activities.
6. [ ] Modal? Extend `ActiveModal`, add `DualGameDetailUpperState` fields, DSM
       open/move/confirm methods + `handleInlineUpdate` field, a
       `handleModalInput` branch, an ArgosyApp `dualModalInputHandler` branch,
       `onModalResult` handling in SecondaryHomeActivity, the upper render
       branch - and add the modal to the ownership table in this skill.
7. [ ] Dual modality: touch handlers on every interactive composable.
8. [ ] Resync: decide what `resyncCompanionState` / the pause watchdog should
       do with your state when the companion bounces.
9. [ ] Prefs: read once at operation start, pass down.
10. [ ] DS parity: verify in BOTH roles (normal and swapped/showcase) - a
        feature that only works in one role is incomplete.
11. [ ] Home-surface work: reuse the shared grid layer above. A component that
        already takes state and callbacks gets rendered on DS, not reimplemented
        and not gated off.
