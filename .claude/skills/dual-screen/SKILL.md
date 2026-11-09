---
name: dual-screen
description: Dual-screen development reference. Load this before implementing any dual-screen feature.
---

# Dual-Screen Development Reference

STATUS: rewritten 2026-09-17 after the screen-role rework. The spine below is verified on
device. The surface is still being restored, so read
`~/.claude/projects/-Users-nendo-projects-argosy-launcher/memory/project_ds_restoration_manifest.md`
before assuming a capability exists. Everything the rework deleted is recoverable with
`git show cb4c385e^:<path>`.

All paths are under `app/src/main/kotlin/com/nendo/argosy/`. Reference by SYMBOL, not line
number; offsets rot on the next edit above them.

## The one thing to understand first

There is no "upper screen" and no "companion UI" any more. Screens hold ROLES, and the role
decides everything.

```
ScreenRole { PRIMARY, PRESENTATION, APP_TARGET, OFF }   domain/model/ScreenLayout.kt

PRIMARY       hosts the real ArgosyApp. Menus open here, the pad drives it.
PRESENTATION  renders PresentationSlotContent: describes what PRIMARY has focused.
APP_TARGET    where launched apps and games open. Takes no launcher UI.
OFF           Argosy draws nothing.
```

Either physical activity can hold PRIMARY. `ArgosyActivity` and `SecondaryHomeActivity` are
interchangeable hosts, and which one is hosting is `DualScreenManager.companionHoldsPrimary`.

LAW: `companionHoldsPrimary` decides BOTH what an activity renders AND where its key events go.
- Why: a screen that draws a presentation slot while still handling input for a launcher it is
  not showing is exactly the bug class that cost a full day here. Both decisions must read one
  flow or they drift.
- Boundary: if you add a third consumer of "who is hosting", it reads that flow too.

## Screen layouts are per attached SET

`ScreenLayouts` (`domain/model/ScreenLayout.kt`) maps an attached-screen set to a `ScreenLayout`.
The set key is `ScreenLayouts.setKeyOf(keys)` - every attached panel's stable key, sorted, joined
with `|`. A stable key is the display's `getUniqueId()` when the OS offers one, else
`display:<id>:<short>x<long>` (`util/ScreenCatalog.kt`).

Consequence, and it is the feature: plug a monitor in and the set key changes, so that
combination gets its own profile. Unplug and the old profile returns untouched. Replug the same
monitor and you land back in its profile.

`ScreenCatalog.attachedScreens()` is the only enumerator. It reports true panel pixels from
`Display.Mode` (NOT `getRealSize()`, which reflects the CALLING context, not the target panel -
that bug made every screen card draw at the same aspect) and filters to BUILT_IN and EXTERNAL.

`DualScreenManager.applyStoredScreenLayout(promptWhenUnknown)` is THE entry point that resolves
and applies a layout. Called from `ArgosyActivity.onCreate`, from the display listener on screen
add and remove (only after `isScreenEvent` passes, see below), when the built-in screens come back
on after docked-dark, when DeX ends, and by the settings router after a role change. It reaches
the live arrangement through `applyScreenLayout` -> `setPrimaryDisplayId`, so the roles it sets
come from the stored layout and nothing else. `rederiveRolesFromLayout()` is the role-only
variant for session end, docking and HDMI cleanup. Do not resolve a layout anywhere else.

Swap rule (`ScreenLayout.withRole`): only PRIMARY and PRESENTATION ever trade. Assigning
APP_TARGET never disturbs the primary, so a media screen stays put. Tests in
`app/src/test/.../domain/model/ScreenLayoutTest.kt` cover both directions plus the arrangement
where an app-target screen must survive a swap.

## What each surface renders

Both activities choose from DSM flows:

```
ArgosyActivity                       SecondaryHomeActivity
  companionHoldsPrimary                isRolesSwapped
  || game on another screen            || game shows the dashboard here
    ? PresentationSlotContent(slot)      ? PresentationSlotContent(slot)
    : ArgosyApp()                        : ArgosyApp()
```

RULE: an activity's render choice reads DSM flows at composition time, never a flag the
activity holds itself. A `mutableStateOf` set from role callbacks is a second copy of "who is
hosting"; one (`isShowcaseRole`) let both screens compose `ArgosyApp` after sleep, a role swap
or a game closing, and every press moved menus twice.

The two gates are not exact mirrors: while the companion is stopped (screen off, `onStop`)
`companionHoldsPrimary` is false, so ArgosyActivity composes `ArgosyApp` while the stopped
companion's composition still exists. What keeps that safe is the next rule.

RULE: `ArgosyApp` collects the gamepad, shortcut and home event flows with
`flowWithLifecycle`, so a composition whose activity is stopped dispatches nothing. Any new
collector of a `GamepadInputHandler` flow must do the same.

`ArgosyApp` takes no dual-screen parameters. It reads `DualScreenManagerHolder.instance`.

LAW: inside `ArgosyApp` and anything it hosts, reach DSM through the Holder, never through
`context as? ArgosyActivity`.
- Why: on the companion that cast is null, and roughly twenty sites went silently inert when the
  companion held PRIMARY - overlay events, keyboard toggle, media routing, the screen dimmer.
- Exception: genuinely ArgosyActivity-owned things. Deep links are not one: ArgosyActivity
  receives the Intent and publishes it to `DualScreenManager.pendingDeepLink`, and an
  ArgosyActivity created on another display forwards the Intent to the default-display instance
  before it yields or hands that display to the companion.

## PresentationSlot

`ui/dualscreen/PresentationSlot.kt`. What the presentation screen draws.

| Case | Published by |
|---|---|
| `Fallback` | nobody; draws wallpaper via `surfaceBackdrop(BackdropRole.WALLPAPER)` |
| `HomeLayoutPreview` | `HomeScreenSection` via `PresentOnCompanion` |
| `PlayTime` | `PlayTimePresentation` via `PresentOnCompanion` |
| `ScreenIdentity` | `ScreensSection`, the numbered badge |
| `Detail` | `setCompanionDetail` from Home, Library, Media, and the Collections screens (collection detail, genre/mode game lists) through `PresentationFocus` |
| `PlatformShowcase` | `LibraryViewModel` while the platform grid is focused; Home's custom-grid collection tiles; the Collections list and genre/mode list through `PresentationFocus` |
| `GameHero` | `GameDetailViewModel` via `presentSlot` |

A focused game's `Detail` and a set of games' mosaic are built by `ui/common/PresentationShowcaseSource`
(`gameDetail`, `collectionShowcase`), so they look identical from every screen. A new list screen
that should drive the presentation screen holds a `ui/common/PresentationFocus`, calls `show()` from a
focus observer, `startDescribing()` on composition and ON_RESUME, and `stopDescribing()` on dispose.
| `InGame` | DSM while a session is live |

Three publishing mechanisms, deliberately:
- `PresentOnCompanion(SlotOwner("id"), slot)` - a composable publishes while on screen and
  releases on dispose. Held per owner so a screen taking over and the screen it replaced can
  publish and release in any order. The default for a slot built from what the composable
  already has.
- `setCompanionDetail(CompanionDetail?)` - a ViewModel describes its focused item. Publish on
  focus change, republish on resume, clear on dispose. Copy `MediaLibraryScreen`'s
  `DisposableEffect`; it is the canonical shape.
- `DualScreenManager.presentSlot(owner, slot)` / `releaseSlot(owner)` called from a ViewModel -
  for a dedicated slot case (not a `Detail`) that the ViewModel builds from suspend repository
  queries, in step with its own focus flow. `LibraryViewModel`'s
  `publishPlatformShowcase` is the reference. The contract comes from DSM:
  - Slots are keyed by `SlotOwner`. `presentSlot` replaces that owner's entry and moves it to
    the top; `releaseSlot` removes only that owner's entry. Use one fixed owner id per publisher.
  - The most recently presented slot wins over any `Detail`, so a slot left published hides
    every later `setCompanionDetail`. The ViewModel owns the release: call `releaseSlot` when
    focus leaves the thing it describes, and on every path that stops driving the screen.
  - Reach DSM through `DualScreenManagerHolder.instance` and return early when it is null.
  - Not for a composable that can publish from state it already holds. Use
    `PresentOnCompanion` there, because composition gives it release on dispose for free.

`presentationSlot` combines them with a live session outranking everything, then the most recent
published slot, then the described detail, then Fallback.

GOTCHA: `presentationSlot` is declared AFTER the flows it combines. Kotlin initialises properties
in declaration order, so a combine placed above its inputs reads nulls at construction and throws.
Same trap applies to `companionHoldsPrimary`.

## Input

`InputDispatcher` tiers, highest first:

```
interceptHandler    may DECLINE (returns UNHANDLED) and let the event fall through
criticalHandler     app-level modals
modalStack
drawerHandler
viewHandler
```

`interceptHandler` is the only pass-through tier. It exists so a transient prompt can own ONE
button without freezing the screen behind it; the new-monitor prompt is its only user today.
Every other tier is all-or-nothing.

`GamepadInputHandler` is a `@Singleton` and `eventFlow()` is a SharedFlow. Both activities feed
it; every composed `ArgosyApp` collects it while its activity is started. Two compositions can
exist briefly (see the render gates above), and the SharedFlow broadcasts to both, so the
lifecycle gate is what keeps one press from dispatching twice; `claimInput` dedup runs before
the emit and cannot catch it. So an activity "routing input" means calling
`gamepadInputHandler.handleKeyEvent(event)`.

Dedup: every dispatch path calls `dsm.claimInput(event)` first; first claimant wins.

LAW: an activity that is NOT hosting must decline to claim.
- Why: `claimInput` drops parallel deliveries of the same physical press. A companion drawing a
  presentation slot that claimed keys and then did nothing with them starved the panel that was
  actually hosting. That was the "input isn't captured to the primary display" bug.

`onForwardKey` (CompanionHost) must feed `gamepadInputHandler`, not `super.dispatchKeyEvent`.
The view hierarchy consumes nothing, because the launcher uses no Compose focus for navigation,
so a forwarded key handed to `super` vanishes silently.

SELECT swaps PRIMARY and PRESENTATION, gated on a dual-screen device WITH a presentation
screen. The Navigation setting `select_swap_mode` picks the gesture, and
`ui/dualscreen/RoleSwapInput.kt` is the one reader (`activeSelectSwapMode`). HOLD (default):
`GamepadInputHandler` defers Select through `SelectModifier`, emits `GamepadEvent.LongSelect`
after the long-press threshold, and the ArgosyApp fallback swaps; a press keeps the screen's own
Select action. TAP: screens return Select unhandled (`selectSwapsRoles()`) and the fallback
swaps. Screens that block Select under an overlay block `onLongSelect` the same way. Hold-A
opens the game/tile menu on every device. Also reachable from the Swap screens toggle in the
DISPLAY section of the quick settings panel's Quick page.

Quick settings panel parity (deferred by decision): the R3 panel renders on the control screen
only. R3 opens it where the controller is focused, and a companion request
(`onOpenOverlayFromCompanion` with `OVERLAY_QUICK_SETTINGS`) refocuses the main screen and opens
it there rather than drawing a second copy. The panel's
second-screen brightness row drives the second built-in panel through the root route with the
display service's per-display brightness binder calls (`service call display <set> i32 <id> f
<value>`, the call AYN's own dual-screen assistant makes through `DisplayManager.setBrightness`).
The transaction numbers are per Android release in `BrightnessController.callsBySdk`; only SDK 33
(Thor, 35/36) is mapped and verified, and the row stays hidden on any release not in that map.

Role-state writers: `swapRoles()` toggles, `applyDisplayRoleOverride(override)` sets, and
`setPrimaryDisplayId` / `rederiveRolesFromLayout()` apply the stored layout. All of them end in
`commitRoleSwap`. Do not write `_isRolesSwapped` from anywhere else. While a session is active:
- `swapRoles`, `applyDisplayRoleOverride` and `setPrimaryDisplayId` commit only through
  `moveGameWithRoles`, which carries the game to the display its role lands on first and refuses
  otherwise.
- `rederiveRolesFromLayout()` does not commit at all and waits for the session end, with one
  exception: docked-dark (built-in screens off beside an external display) always commits
  unswapped, session or not.

LAW: the stored layout of the attached set is the only source of the swapped flag. The two user
commands (`swapRoles`, `applyDisplayRoleOverride`) write the layout through `persistPrimaryRole`,
and only once the role change commits; nothing else writes it. Activity creation, screen
add/remove, docking, DeX exit and session end derive roles from the layout alone; none of them
consults the Control Screen override. A layout-derived move refused during a session needs no
pending copy, because the layout already holds it and every session end calls
`rederiveRolesFromLayout()`. A refused user swap writes no layout; one whose move timed out
commits late only if the game still reaches the target (`onGameMovedToDisplay`). The stored
override follows the layout (`followingLayout`, applied in `applyLayoutRoles`): AUTO stays AUTO,
an explicit value is rewritten to the arrangement the layout produces.

Live move (AYN Thor only, `liveSwapAvailable`): needs `hardware/ThorTaskMover` answering its probe,
a software-rendered built-in core registered as `LiveMoveHost`, and a game on the PRIMARY or
PRESENTATION display (never the app screen, never docked). Order: `isMovingGame` raised, second
screen hidden, `emulatorDisplayId` set to the target, task moved, arrival reported by
`LibretroActivity.onConfigurationChanged`, roles committed, focus sent to the game's display,
guard cleared. No arrival within `GAME_MOVE_CONFIRM_MS` restores the old display and commits
nothing. Every refocus, teardown and focus-restore path checks `isMovingGame`; a new one must too.
External emulators never move: their task id is not visible to the app.

## New-monitor prompt

On display add, `applyStoredScreenLayout(promptWhenUnknown = true)` applies the resolved layout
immediately, then sets `unconfiguredScreenSet` when the set has no stored layout and more than one
screen. `ArgosyApp` shows a 5-second countdown offering the display setup screen on X, registered
as the intercept handler so every other button still reaches the screen behind it.

Known-set changes never prompt; they just apply the stored profile.

UNPROVEN: this path has not been exercised with real hardware. `overlay_display_devices` cannot
fake it, because `ScreenCatalog` filters to BUILT_IN/EXTERNAL and the listener gates on the
same set.

LAW: the DSM display listener acts only on screens. `isScreenEvent` passes a display that is in
`ScreenCatalog.attachedScreens()` now or was at the previous event (a removed display can no
longer be classified, so membership is remembered). Emulators such as Azahar create and remove
their own virtual and presentation displays mid-session; those never reach role re-derivation,
companion relaunch, docked sync or session teardown. `ScreenCatalog.isScreen` is the one
predicate; `DisplayAffinityHelper.isPhysicalDisplay` is that predicate plus "not off". Needs a monitor plugged into the Thor.

## Process and lifecycle (unchanged, still true)

ONE process, two activities. `SecondaryHomeActivity` is the SECONDARY_HOME activity, intent-filter
priority 1000, `launchMode=singleTop`, `taskAffinity=""`, `excludeFromRecents`. The OS pins it:
`finish()` respawns it. To remove it, disable the component via
`util/SecondaryHomeComponent.setEnabled(context, false)`.

It IS a Hilt entry point now (`@AndroidEntryPoint`), so it can inject. It reaches shared
singletons through `dsm.*` for anything DSM owns.

- FGS guard: `CompanionGuardService`, subtype `companion_display_guard`.
- onCreate gate: `SessionStateStore.isDualScreenEnabled()` finishes immediately when off.
- Stale-DSM: the companion collects `DualScreenManagerHolder.instanceFlow` from `onCreate` and
  `recreate()`s when a different manager appears. Its composition reads `dsm` as a plain field,
  so rebinding in place would leave every screen on the old manager. Init-time wiring belongs in
  `initializeCompanion()`, which the recreated instance runs.
- One companion per display: `registerCompanionHost` finishes the companion it replaces. A
  replaced instance left running keeps a stopped `ArgosyApp` that still holds ViewModels.
- Theme: `SecondaryHomeTheme` provides the same locals as `ALauncherTheme`, including
  `LocalSurfaceBackdrop`.

## Display affinity

`hasSecondaryDisplay` is gated by THREE conditions:
`dualScreenEnabled && secondaryDisplayUsable && hasPhysicalSecondaryDisplay`.

`DisplayAffinityHelper.getRoleDisplayIds` follows the same gate: with `hasSecondaryDisplay` false
it returns null, whatever the stored layout names as PRIMARY. DS off means one screen with no
roles, so the interactive/showcase queries, the dashboard decision and the session's recorded game
display all read single-screen. The stored layout still decides APP_TARGET and OFF screens.

`secondaryDisplayUsable` is a FALLBACK LATCH, not a preference. It is set false by
`fallbackToSingleScreen(persistent)` once the companion has proven unable to start on the
secondary display. `reprobeSecondaryDisplay()` clears it. On a device that latched false, every
dual-screen entry point is off even though hardware and preference both say yes. Check the latch
before diagnosing "dual screen does nothing".

`DisplayAffinityHelper.appTargetDisplayId` routes launches to the APP_TARGET screen, which is what
makes launching to a media screen work even in single-display mode.

## Checklist for a new dual-screen feature

1. [ ] State on DSM as a StateFlow, never on an activity.
2. [ ] Does the presentation screen show it? Add a `PresentationSlot` case and publish it, rather
       than reaching for a second render path.
3. [ ] Reach DSM via the Holder, not via ArgosyActivity.
4. [ ] Gamepad AND touch. An app bar you can tap but not reach with the d-pad fails the
       completeness matrix; that has already shipped once.
5. [ ] If you add a CompanionHost method, implement it for real or do not add it. Three `= Unit`
       stubs sat behind a manager that reported success, and the drawer's Library entry silently
       did nothing for a week.
6. [ ] Verify in BOTH role arrangements. A feature that only works with the built-in screen
       primary is half done.
7. [ ] Shared components, never a DS-only copy. See the standing rule in
       `feedback_share_home_surface_components`.

## Things that no longer exist

`DualHomeViewModel`, `DualHomeLowerScreen`, `DualHomeUpperScreen`, `DualShowcaseComponents`,
`CompanionHomeHints`, `SecondaryHomeInputHandler`, `SecondaryHomeComposables`,
`SecondaryHomeStateManager`, `SecondaryHomeBroadcastHelper`, `ShowcaseViewModel`,
`ControlRoleContent`, the whole `ui/dualscreen/gamedetail` and `ui/dualscreen/media` packages,
`ui/screens/secondaryhome`, `DualCustomGridInputRouter`, `ActiveModal`, `ForwardingMode`,
`CarouselNavContext`, and the ~70 `moveDual*` / `confirmDual*` relay methods on DSM.

If a doc, comment or plan mentions any of those, it predates 2026-09-16 and is wrong.
