---
name: dual-screen
description: Dual-screen development reference. Load this before implementing any dual-screen feature.
---

# Dual-Screen Development Reference

Architecture: OSOT (one source of truth). `DualScreenManager` (DSM) owns ALL
cross-activity state as StateFlows; both activities are consumers. There is no
messaging layer between the activities.

All paths below are under `app/src/main/kotlin/com/nendo/argosy/`.
Line numbers verified 2026-07-22.

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

## OSOT Model (the spine)

```
                 DualScreenManager (DualScreenManager.kt)
                 held in DualScreenManagerHolder.instance
   StateFlows (pull)                      CompanionHost (push)
   dualScreenShowcase                     interface at DSM:167-193
   dualGameDetailState                    implemented by SecondaryHomeActivity:48-50
   dualViewMode / dualAppBarFocused
   dualDrawerOpen / dualCollectionShowcase
   dualSyncOverlay / dualSaveConflict (+focus indexes)
   pendingOverlayEvent / isCompanionActive
   isRolesSwapped / isDualScreenDevice
   swapped* family (DSM:420-445)
        |                                        |
        v                                        v
   MainActivity + ArgosyApp               SecondaryHomeActivity
   (creates DSM, collects flows,          (collects flows at :655-658,
    MainActivity.kt:258-301,337-348)       receives CompanionHost pushes)
```

- MainActivity creates DSM (or rebinds to the existing Holder instance) in
  onCreate (MainActivity.kt:258-301) and clears the Holder only when finishing
  (MainActivity.kt:401-404).
- Companion -> DSM direction is plain method calls, routed through
  `SecondaryHomeBroadcastHelper` (`dsm.onGameSelected`, `dsm.handleDirectAction`,
  `dsm.handleInlineUpdate`, ...).
- DSM -> companion direction is `companionHost?.onX(...)` pushes.

LAW: never anchor shared state in an activity.
- Rule: any state both displays can render, or that must survive a companion
  respawn, lives as a DSM StateFlow.
- Why: the showcase role renders entirely from DSM flows collected at
  SecondaryHomeActivity.kt:655-658; state parked in MainActivity never reaches
  it. State parked in SecondaryHomeActivity dies on respawn - the OS recreates
  the SECONDARY_HOME activity at will, and onResume reconnects to a possibly
  NEW DSM (stale-DSM reconnect, SecondaryHomeActivity.kt:272-277).
- Exception: purely local UI state (e.g. `isScreenshotViewerOpen`,
  `launchedExternalApp` in SecondaryHomeActivity).
- Boundary: the moment the other display renders it, or a respawn must restore
  it, it moves to DSM (or SessionStateStore if it must survive process death).

## Process and Lifecycle (verified, carried over)

ONE process, two activities. SecondaryHomeActivity has no `android:process`;
it is NOT a Hilt entry point and reaches shared singletons through `dsm.*`
internals (manual VM construction in `initializeDependencies()`,
SecondaryHomeActivity.kt:661-746).

- Manifest: SecondaryHomeActivity is the SECONDARY_HOME activity, intent-filter
  priority 1000 (MAIN + SECONDARY_HOME + DEFAULT), `launchMode=singleTop`,
  `taskAffinity=""`, `excludeFromRecents` (AndroidManifest.xml:180-193).
  The OS pins it: `finish()` respawns it. To actually remove it, disable the
  component: `SecondaryHomeComponent.setEnabled(context, false)`
  (util/SecondaryHomeComponent.kt; DSM.applyDualScreenEnabled at DSM:123-131).
- FGS guard: `CompanionGuardService`, foregroundServiceType
  `specialUse|dataSync`, subtype property `companion_display_guard`
  (AndroidManifest.xml:172-177), keeps the display session alive.
- onCreate gate: `SessionStateStore.isDualScreenEnabled()` finishes immediately
  when off (SecondaryHomeActivity.kt:112-116).
- DSM acquisition on companion boot (SecondaryHomeActivity.kt:118-153): use
  Holder instance if present; if respawned without a running Argosy and not
  default home, disable component and finish; else launch MainActivity on the
  default display and poll the Holder (100ms x 50) before initializing.
- Stale-DSM reconnect: onResume compares `dsm` to the current Holder instance
  and re-runs `initializeCompanion()` on mismatch
  (SecondaryHomeActivity.kt:272-277). Any init-time wiring you add must live
  inside that path or it will be lost across a MainActivity recreate.
- Theme: `SecondaryHomeTheme` + live prefs collected through
  `dsm.preferencesRepository` keyed on isInitialized
  (SecondaryHomeActivity.kt:171-180). V2 theme locals are available on the
  lower display; custom fonts flow through the `fonts` parameter.

## File Map (what lives where)

- `DualScreenManager.kt` - all shared state, modal state machine, game actions,
  save operations, launch/display routing, companion lifecycle watchdogs.
- `DualScreenManagerHolder.kt` - `@Volatile var instance` (the whole file is 6
  lines). Set by MainActivity, read everywhere else.
- `hardware/SecondaryHomeActivity.kt` (~930 lines) - lifecycle, CompanionHost
  implementation, key dispatch entry, DSM flow collection. Logic is extracted
  to the three helpers below; do not grow the activity.
- `hardware/SecondaryHomeInputHandler.kt` - ALL companion gamepad routing:
  `routeInput`, per-view-mode handlers, `handleModalInput` (:726-1065),
  `handleCompanionInput` (in-game dashboard), drawer input.
- `hardware/SecondaryHomeStateManager.kt` - boot-time state restore
  (`loadInitialState` :68-156 incl. CarouselNavContext + GAME_DETAIL restore),
  `loadInputSwapPreferences` (:158-177), `loadCompanionGameData`,
  `createGameDetailViewModel`, `persistCarouselPosition` (:223-225).
- `hardware/SecondaryHomeBroadcastHelper.kt` - thin adapter, companion -> DSM
  method calls only (see NAMING WARNING).
- `hardware/SecondaryHomeComposables.kt` - `CompanionScreen` enum (:51),
  `SecondaryHomeContent` (:54, normal role), `ShowcaseRoleContent` (:195,
  swapped role), lower dimming wiring (:424).
- `hardware/CompanionPanel.kt` - `CompanionInGameState` (:14-33),
  `withLiveQuickActionState` (:40-46), `CompanionSessionTimer`.
- `ui/dualscreen/home/DualHomeViewModel.kt` - lower home state,
  `DualHomeFocusZone` (:71), `DualHomeViewMode` (:73), `ForwardingMode` (:75),
  nav-context save/restore (:524, :634).
- `ui/dualscreen/gamedetail/DualGameDetailModels.kt` - `DualGameDetailTab`
  {SAVES, STATES, MEDIA, OPTIONS} (:17-22), `ActiveModal` (:24),
  `GameDetailOption` (:26-43), `DualGameDetailUpperState`, save-entry JSON DTOs.
- `ui/dualscreen/ShowcaseViewModel.kt` - touch/modal input adapter for the
  showcase role, gated by `isControlActive`.
- `util/DisplayAffinityHelper.kt` - display enumeration and launch options.
- `data/preferences/SessionStateStore.kt` - SharedPreferences persistence layer
  (session flags, swap prefs, CarouselNavContext :221-273, companion screen).

## Input Flow

Dedup first, always: every dispatch path calls `dsm.claimInput(event)` before
handling (MainActivity.kt:411 and :493, SecondaryHomeActivity.kt:345, plus the
libretro activity). First claimant wins; parallel deliveries of the same
physical event are dropped (InputDedupBuffer, DSM:146-159). New dispatch paths
MUST claim before handling.

Companion side (SecondaryHomeActivity.onKeyDown :344-381), in order:
1. `dsm.claimInput` - return if already claimed.
2. Sync-conflict then save-conflict handlers (these read DSM's
   `dualSyncOverlay`/`dualSaveConflict` and consume everything while active).
3. Showcase branch: if `isShowcaseRole`, only showcase-modal events are handled
   (via `ShowcaseViewModel.handleModalGamepadEvent`, and only while Argosy is
   backgrounded); everything else falls through to `super`.
4. `inputHandler.routeInput(event, true, isGameActive, currentScreen)`.

`routeInput` (SecondaryHomeInputHandler.kt:41-61): conflicts again (forwarded
keys enter here directly), then HOME/GAME_DETAIL handlers when Argosy is
foreground and no game is active, else `handleCompanionInput` (:232-269, the
in-game/backgrounded app-bar dashboard; returns UNHANDLED on external
displays).

Primary side (MainActivity.dispatchKeyEvent :410-481), in order:
1. `dsm.claimInput`.
2. `dsm.handleConflictInput` (DSM:300) - conflict overlays win on both sides.
3. Game on the other display and no overlay focused -> forward raw event to
   `dsm.emulatorKeyDispatcher` (cross-display session input).
4. Not swapped + on Home + companion active + no overlay ->
   `companionHost?.onForwardKey(keyCode, swapAB, swapXY, swapStartSelect)`
   (:432-448). The companion re-maps and feeds the SAME `routeInput`
   (SecondaryHomeActivity.kt:485-488). Sticks take the same path via
   dispatchGenericMotionEvent (:490-541).
5. Stale-link reassert (:450-463, `reassertCompanionForwarding` :601).
6. Local handling via GamepadInputHandler.

Rule: the companion's `SecondaryHomeInputHandler` is the single gamepad brain
for lower-screen content, whichever activity physically received the key.
- Exception: FILE_PICKER and SAVE_NAME modals (upper-owned, table below) and
  the swapped role (upper is the interactive screen).
- Why: forwarded and direct keys converge on `routeInput`, so state moves in
  exactly one place.
- Boundary: if you handle a key on the upper screen for lower-screen content,
  you have created a second brain - move it.

Swap prefs are read ONCE at boot into activity fields
(`stateManager.loadInputSwapPreferences()`, applied at
SecondaryHomeActivity.kt:779-785) and passed to every `mapKeycodeToGamepadEvent`
call; icon swaps flow through CompositionLocals `LocalABIconsSwapped` /
`LocalXYIconsSwapped` / `LocalSwapStartSelect`
(SecondaryHomeActivity.kt:182-186). `ControllerDetector` lives at
`core/input/ControllerDetector.kt` (NOT ui/input).

## Role Swap / Showcase Mode

`isRolesSwapped` (DSM:109-114) mirrors DisplayRoleResolver output (override
pref + display type; external HDMI defaults swapped). `swapRoles()`
(DSM:2140-2166) toggles the override - debounced 500ms, NO-OP while a session
is active.

When swapped, the roles invert:
- The PRIMARY activity becomes the interactive screen, driven by DSM's swapped
  mirror state: `swappedDualHomeViewModel` (built by `initSwappedViewModel`,
  DSM:520-534, triggered from MainActivity's `onRoleSwapped` callback,
  MainActivity.kt:307-311), `swappedCurrentScreen`, `swappedGameDetailViewModel`
  (created by `selectGameSwapped`, DSM:2168-2189), `swappedIsGameActive`,
  `swappedCompanionState`, `swappedSessionTimer` (DSM:420-445).
- The COMPANION becomes a passive showcase: `onRoleSwapped(isSwapped)` sets
  `isShowcaseRole` (SecondaryHomeActivity.kt:473-475) and setContent renders
  `ShowcaseRoleContent` from the DSM-mirrored flows
  (SecondaryHomeActivity.kt:187-203). Showcase touch on modals goes through
  `ShowcaseViewModel` straight into DSM confirm/move methods.
- HDMI unplug mid-swap: `cleanupSwappedState` (DSM:481-511) resets override to
  AUTO, clears swapped VMs/timers, and ends the session if the emulator was on
  the removed display.
- Per-game display targets can force an effective swap at launch only:
  `resolveEmulatorDisplaySwapped` (DSM:1631-1643) maps
  HERO/LIBRARY/TOP/BOTTOM, `preGameRolesSwapped` restores the pre-launch state
  at session end (DSM:1620-1625, :1033-1042).

This is why the OSOT law exists: the same feature must render on whichever
display currently holds the showcase role, and only DSM flows reach both.

## ForwardingMode + Overlay Event Flow

`ForwardingMode { NONE, OVERLAY, BACKGROUND }`
(ui/dualscreen/home/DualHomeViewModel.kt:75). While != NONE the lower home
swallows all input (SecondaryHomeInputHandler.kt:94-96).

OVERLAY (drawer / quick menu / quick settings on the upper screen):
1. Companion presses Menu/L3/R3 -> `broadcasts.broadcastOpenOverlay(name)`
   with name from `overlayNameFor` (SecondaryHomeInputHandler.kt:388-390,
   :1194-1198; names OVERLAY_MENU/QUICK_MENU/QUICK_SETTINGS, DSM:2315-2317).
2. Helper sets `startDrawerForwarding()` (OVERLAY) then calls
   `dsm.onOpenOverlayFromCompanion` (SecondaryHomeBroadcastHelper.kt:190-193).
3. DSM sets `isOverlayFocused = true`, publishes `pendingOverlayEvent`, calls
   `refocusMain()` (DSM:972-976).
4. ArgosyApp collects `pendingOverlayEvent` and opens the matching overlay,
   then `clearPendingOverlay()` (ArgosyApp.kt:423-433).
5. Close: every overlay-close observer funnels into `notifyOverlayClosed`
   (ArgosyApp.kt:807-833) -> `isOverlayFocused = false` +
   `companionHost.onOverlayClosed()` + `refocusSelf()`; the companion's
   `onOverlayClosed` calls `stopDrawerForwarding()`
   (SecondaryHomeActivity.kt:477-479).

BACKGROUND: an overlay closed while the upper is NOT on the Home route (user
navigated into Apps/Settings) -> `companionHost.onBackgroundForward()`
(ArgosyApp.kt:828, :857, :873) -> lower enters BACKGROUND forwarding: keys
swallowed, tap on the lower screen refocuses the upper
(SecondaryHomeActivity.kt:333-339). Returning to Home fires
`notifyOverlayClosed` (ArgosyApp.kt:836-846) and clears it.

Safety nets: dual-screen topology changes reset overlay focus and modal state
(ArgosyApp.kt:288-304); `reassertCompanionForwarding` clears a latched
`isOverlayFocused` when input arrives on a stale link (MainActivity.kt:601).

## Modal System

Modals render on the upper screen inside `DualGameDetailUpperScreen`, state
lives in DSM's `dualGameDetailState` (`DualGameDetailUpperState.modalType`).
Opening always goes through a DSM `open*Modal` method which sets state and
calls `refocusMain()` (DSM:713-803). The lower screen dims while a modal is
active (`isDimmed = activeModal != ActiveModal.NONE`,
SecondaryHomeComposables.kt:424).

`ActiveModal` - 14 values (DualGameDetailModels.kt:24):
NONE, RATING, DIFFICULTY, STATUS, EMULATOR, CORE, SAVE_PATH, DISPLAY_TARGET,
COLLECTION, SAVE_NAME, DISC_PICKER, VARIANT_PICKER, STEAM_INSTALL, FILE_PICKER.

Input ownership per modal, normal (non-swapped) mode. "Companion-owned" =
`handleModalInput` (SecondaryHomeInputHandler.kt:726-1065) drives the
companion VM and mirrors focus to the upper via
`broadcastInlineUpdate(<field>)` -> `dsm.handleInlineUpdate` (DSM:898-970);
confirm goes through `broadcastModalConfirmResult` -> `dsm.onModalConfirmResult`.

| Modal | Owner | Mechanics (SecondaryHomeInputHandler lines) |
|---|---|---|
| NONE | - | no-op (:1061) |
| RATING | Companion | Left/Right adjust, mirror `modal_rating`; Confirm/Back (:964-993) |
| DIFFICULTY | Companion | same branch as RATING (:964-993) |
| STATUS | Companion | Up/Down, mirror `modal_status` (:995-1024) |
| EMULATOR | Companion | live focus forwarding via `broadcastInlineUpdate("emulator_focus")` (:732-762) |
| CORE | Companion | `core_focus` (:800-829) |
| SAVE_PATH | Companion | `save_path_focus` (:831-860) |
| DISPLAY_TARGET | Companion | `display_target_focus` (:862-891) |
| COLLECTION | Companion | `collection_focus`; Confirm -> `collection_toggle` / `collection_create` (:924-962) |
| SAVE_NAME | UPPER | companion swallows ALL input incl. Back (:1061 fallthrough); text + confirm on the upper via MainActivity.updateDualSaveNameText/confirmDualSaveName (ArgosyApp.kt:1165-1168, DSM:1463-1497) |
| DISC_PICKER | Companion | `disc_focus`; Confirm closes modal + direct action PLAY_DISC (:1026-1059) |
| VARIANT_PICKER | Companion | `variant_focus` (:893-922) |
| STEAM_INSTALL | Companion | `steam_install_focus` (:769-798); also opens from Home as a chooser (`openSteamChooserForHome`, DSM:786-803) |
| FILE_PICKER | UPPER | companion is Back-only dismiss (:763-768); all focus/selection state is DSM `filePicker*` fields driven by the upper `dualModalInputHandler` (ArgosyApp.kt:594-683) and touch |

Rule: new picker modals are companion-owned with live focus forwarding -
copy the EMULATOR branch (:732-762), not FILE_PICKER.
- Exception: modals needing upper-only capabilities (text entry: SAVE_NAME;
  complex multi-select rows: FILE_PICKER) are upper-owned; companion swallows
  input (Back-only dismiss at most).
- Why: a modal with no companion branch is a dead modal when the companion has
  window focus - keys land on the companion and vanish.
- Boundary: every new `ActiveModal` value MUST get an explicit
  `handleModalInput` branch, even if it is only Back-dismiss.

The upper `dualModalInputHandler` (ArgosyApp.kt:560-683, subscribed while
`dualModalActive`, :757) mirrors every picker for the cases where the upper
owns input (overlay focus, swapped role); keep both sides in sync when adding
a modal.

Result delivery: DSM confirm/dismiss methods push
`companionHost.onModalResult(...)`; the companion applies to its VM and
`refocusSelf()` (SecondaryHomeActivity.kt:504-579). Watchdog: if the companion
pauses with a modal open, DSM auto-dismisses it after 5s
(`onCompanionPaused`, DSM:546-563); `resyncCompanionState` also clears stale
modals on companion resume (DSM:2089-2114).

## ID-Based Carousel Restore

`SessionStateStore.CarouselNavContext` (SessionStateStore.kt:221-273): restores
the lower carousel by IDENTITY (sectionKind + platformId + gameId) plus the
full filter/sort context; legacy index fields are fallback only. Persisted via
`stateManager.persistCarouselPosition` on selection moves and in onStop
(SecondaryHomeActivity.kt:300-308); restored in
`stateManager.loadInitialState` -> `dualHomeViewModel.restoreNavContext`
(SecondaryHomeStateManager.kt:92-98). The same load path also restores a
GAME_DETAIL screen (rebuilds the detail VM for the saved gameId,
:116-137) and clears any persisted modal/screenshot-viewer state (:106-114).
Do not restore by index anywhere - dynamic sections shift and the two screens
end up on different games.

## CompanionInGameState

`CompanionInGameState` (hardware/CompanionPanel.kt:14-33) is the in-game
dashboard state. Merge rule: async metadata loads MUST NOT clobber the live
quick-action flags - always apply `withLiveQuickActionState(quickActionsAvailable,
hasQuickSave)` after building a fresh snapshot (CompanionPanel.kt:40-46; used
at SecondaryHomeActivity.kt:792-795 and DSM:1014-1017). DSM's
`_swappedCompanionState` copy is canonical in BOTH companion modes;
`updateCompanionHasQuickSave` maintains it regardless of role (DSM:208-211,
:441-443).

## Session Survival Rules

- `dsm.hasLiveSession()` (DSM:133-134) = in-memory PlaySessionTracker check,
  flips false the moment teardown BEGINS. `SessionStateStore.hasActiveSession()`
  (SessionStateStore.kt:54) = persisted flag, stays true until save sync
  completes. Pick deliberately: UI "is a game up right now" -> hasLiveSession;
  "is it safe to relaunch/companion-launch" -> hasActiveSession.
- Rule: Argosy UI foregrounded = session over. `onForegroundChanged` ends the
  companion's session view when Argosy comes foreground during a game
  (SecondaryHomeActivity.kt:383-394).
  - Exception: showcase role checks `!dsm.hasLiveSession()` first - a
    cross-display session survives the upper UI foregrounding.
  - Why: on a single pair of displays, foregrounding the launcher means the
    game lost its screen; in swapped mode the game may still be running on the
    other display.
  - Boundary: only cross-display sessions survive.
- Companion resumed ON the emulator's display -> the game lost that display:
  end the session (`onResume`, SecondaryHomeActivity.kt:288-297; guarded by
  `dsm.isLaunchingGame`).
- HDMI disconnect with the emulator on the removed display ends the session
  (`cleanupSwappedState`, DSM:504-508).
- `swapRoles()` refuses while `hasActiveSession()` (DSM:2145).
- `ensureCompanionLaunched` refuses during an active session unless
  `allowDuringSession` (DSM:2278-2293); a startup guard retries every 1.5s
  until the companion is up (DSM:2258-2271).

## Display Affinity

`DisplayAffinityHelper.getActivityOptions(forEmulator: Boolean,
rolesSwapped: Boolean = false, overrideDisplayId: Int? = null)`
(util/DisplayAffinityHelper.kt:70-87) - note the two newer params: swapped
launches route the emulator to the secondary display, and `overrideDisplayId`
bypasses resolution entirely. Emulator display resolution at launch goes
through `resolveEmulatorDisplaySwapped` (per-game/per-platform
`EmulatorDisplayTarget`, DSM:1631-1643); DSM records `emulatorDisplayId` at
launch (DSM:1607) and it drives cross-display input forwarding
(`isGameOnOtherDisplay`, MainActivity.kt:594-598) and the session-survival
rules above. Companion launch uses `getCompanionLaunchOptions()` (:58-63).
`hasSecondaryDisplay` is gated by the `dualScreenEnabled` var - set it from
prefs before trusting it (:27-30).

## Focus Zones and Visuals (kept)

- Home: `DualHomeFocusZone { CAROUSEL, APP_BAR }` (DualHomeViewModel.kt:71);
  view modes `CAROUSEL, COLLECTIONS, COLLECTION_GAMES, LIBRARY_GRID` (:73), one
  input handler per mode in SecondaryHomeInputHandler.
- Detail tabs: SAVES (dual column, `SaveFocusColumn { SLOTS, HISTORY }` from
  `ui/common/savechannel/`), STATES, MEDIA (grid), OPTIONS (list + inline
  LEFT/RIGHT adjust for RATING/DIFFICULTY/STATUS which mirror via
  `broadcastInlineUpdate`).
- Lower dimming while a modal is up: SecondaryHomeComposables.kt:424 ->
  `DualGameDetailLowerScreen(isDimmed = ...)` (:109, :186).
- Dual modality is non-negotiable: gamepad via SecondaryHomeInputHandler AND
  touch via `touchOnly`/`clickableNoFocus` on the composables (see
  DualHomeLowerContent.kt:133 for the dim-tap pattern -> `broadcastRefocusUpper`).

## New Dual-Screen Feature Checklist

1. [ ] State: add a StateFlow on DSM (never an activity field). Companion
       mutations go through a method on DSM, exposed to the companion via
       SecondaryHomeBroadcastHelper.
2. [ ] Push: if the companion must react to a main-side event, add a
       `CompanionHost` method (DSM:167-193), implement it in
       SecondaryHomeActivity, call it from DSM next to its state update.
3. [ ] Consumers: collect on the upper (ArgosyApp/MainActivity) AND, if the
       showcase role renders it, add a collector in `initializeCompanion`
       (SecondaryHomeActivity.kt:655-658) plus ShowcaseRoleContent wiring.
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
