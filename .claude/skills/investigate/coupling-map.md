# Argosy Coupling Map (Layer 1)

Reference for the `/investigate` scout step. Each axis lists the files that must move
in lockstep, the decisions only a human can settle (grill questions), the facts the
agent must go find itself (loose ends, never ask the user), and the proof obligations
that gate "done". Routing flag decides the readiness exit. Three routing flavors:
walkthrough-forced (human walks the plan, live proof non-negotiable), autonomous-eligible
(agent may proceed, often with a device-proof variant), maintainer-domain (surface to the
maintainer before any work proceeds; contributor PRs not open without prior discussion).

Refresh this doc when the architecture moves; the scout trusts it instead of re-crawling.

---

## Axis: SAVE-SYNC / PERSISTENCE / DB MIGRATION
Routing: WALKTHROUGH-FORCED (live-data proof is non-negotiable; never autonomous)

Triggers: changes to save sync, archiving, restore, conflict logic, Room schema,
DataStore sync keys, or RomM save models.

Lockstep set:
- `data/sync/SavePathResolver.kt` -- `discoverSavePath()` AND `constructSavePath()` must
  produce identical paths; divergence = restore lands in wrong dir.
- `data/sync/strategy/NegotiatorSaveSyncStrategy.kt` -- `planReconcile()`, UPLOAD/DOWNLOAD/
  CONFLICT/NO_OP mapping; unknown action silently falls to NO_OP.
- `data/repository/SaveSyncOrchestrator.kt`, `SaveSyncRepository.kt`, `SaveDownloader.kt`,
  `SaveUploader.kt` (upload + hardcore-trailer path), `SaveSyncApiClient.kt`,
  `SaveSyncEntityManager.kt`, `SaveSyncConflictResolver.kt`, `SaveCacheManager.kt`.
- `data/sync/ConflictResolutionService.kt` -- must handle every `ConflictResolution` value
  (KEEP_LOCAL / KEEP_SERVER / SKIP).
- `data/sync/platform/` handlers (Gci, Switch, Ps2 nested, folder) + `SaveArchiver.kt`
  (zip root peek, JKSV meta, hardcore trailer).
- `data/local/entity/SaveSyncEntity.kt` + `data/local/dao/SaveSyncDao.kt` +
  `data/local/migrations/Migrations.kt` + `data/local/migrations/MigrationRegistry.kt`
  (a new Migration_N_M object MUST be appended to MigrationRegistry.ALL or it silently
  never runs) + `ALauncherDatabase.kt` version + exported `app/schemas/N.json`.
- `data/preferences/SyncPreferencesRepository.kt` (SAVE_SYNC_ENABLED, SECURE_SAVES,
  path-cache flags; ANDROID_DATA_SAF_URI is legacy -- file access is FAL + Manage
  Storage, never SAF).
- Secure Saves OFF path: `SaveSyncOrchestrator.refreshCacheFromSystem` preamble
  (cache-vs-system reconcile, serial-scoped folder cards, RefreshOutcome
  Unreadable-never-Absent), `SaveAccessNotices`, hardcore prohibition gates.
  Changes here must keep secure_saves=ON byte-identical.
- `data/remote/romm/RomMSaveModels.kt`, `RomMSyncModels.kt` (contentHash >=4.9 only,
  nullable emulator/slot).

Grill (decisions for the user):
- Does this change EmulatorResolver.resolveEmulatorForGame? Which dir must restore land in for the affected platform?
- Does it touch the schema? Confirm the migration + version bump + schema-JSON export plan.
- Hash vs timestamp: is conflict detection content-based or time-based for this change?
- Multi-channel / slot semantics affected? (null channel = primary save)

Loose ends (agent finds, never asks):
- Current DB version and whether the field already exists.
- Which platform handler the affected platform routes through.
- Whether the server is >=4.9 (contentHash trust).

Proof obligations:
- Live: GET /api/saves; negotiate returns no_op twice on a clean run.
- On-disk: downloaded save appears at the path SaveSyncEntity.localSavePath claims.
- Migration: isolated test DB opens; schema-JSON matches entity.

---

## Axis: ROMM API / NETWORK / DATA MODEL
Routing: WALKTHROUGH-FORCED (silent null-on-drift; needs live API diff)

Triggers: new/changed RomM endpoint, Moshi model, sync feature, auth/device flow, cover cache.

Lockstep set:
- `data/remote/romm/RomMApi.kt` -- Retrofit interface; device-aware vs plain variants.
- `data/remote/romm/RomMModels.kt` (+ RomMSaveModels, RomMSyncModels, RomMDeviceModels,
  RomMPlaySessionModels) -- `@Json` names; `effectiveSiblings = siblingRoms ?: siblings`.
- `data/remote/romm/RomMLibrarySyncService.kt` -- `syncRom()` maps DTO -> GameEntity.
- `data/local/entity/GameEntity.kt`, `GameFileEntity.kt`, `GameDiscEntity.kt`, `CollectionEntity.kt`.
- `data/remote/romm/RomMCollectionSyncService.kt`, `RomMSyncFilter.kt`, `RomMCapabilities.kt`
  (version gating, e.g. contentHash >=4.9; the device-auth >=4.7 gate lives in
  `RomMConnectionManager.kt` as MIN_DEVICE_API_VERSION, not in RomMCapabilities).
- `data/cache/ImageCacheManager.kt` + `RepairImageCacheUseCase.kt` (cover/background/screenshot
  fallback chains).
- `data/remote/romm/RomMConnectionManager.kt` (token/device; password login retired).

Grill (decisions for the user):
- Does the upstream field name actually exist in the target RomM version? (do not assume)
- New field: required or nullable? What fallback when absent?
- Does it need a device-aware variant and a version gate?
- Does it map to a new GameEntity column (which forces the save-sync migration axis)?

Loose ends (agent finds, never asks):
- Diff a live response against the model; flag any silently-nulled field.
- Whether KSP regenerated the Moshi adapter.
- Existing fallback chains (siblings, screenshotUrls/Paths, backgroundUrls).

Proof obligations:
- Live: fetch the real endpoint, log deserialized JSON, confirm no field nulled by drift.
- Multi-disc / covers still populate after sync.

---

## Axis: PLATFORM / EMULATOR / CORE
Routing: autonomous-eligible (mechanical, verifiable by artifact + on-device launch)

Triggers: adding/changing a platform, external emulator, or libretro core.

Lockstep set (13 registries):
- `data/platform/PlatformDefinitions.kt` -- `platforms`, `slugAliases`, extensions,
  TITLE_ID_PLATFORMS.
- `data/emulator/EmulatorRegistry.kt` -- `EmulatorDef.supportedPlatforms`.
- `data/emulator/EmulatorResolver.kt` -- `resolveEmulatorForGame()`, the one resolver
  launch and save paths share.
- `libretro/LibretroCoreRegistry.kt` -- `CoreInfo.platforms`, default core.
- `libretro/coreoptions/CoreOptionManifestRegistry.kt` (+ manifests/; match upstream tokens
  exactly or the core silently rejects).
- `libretro/coreoptions/CoreControlManifestRegistry.kt`, `libretro/touch/TouchLayoutRegistry.kt`,
  `libretro/shader/ShaderRegistry.kt`, `libretro/frame/FrameRegistry.kt` (per-core control/touch/
  shader/frame coverage for built-in cores).
- `data/emulator/SavePathRegistry.kt`, `StatePathRegistry.kt`, `BiosPathRegistry.kt`
  (+ GameLauncher `mandatoryBios`), `data/sync/platform/PlatformSaveHandlerRegistry.kt`.
- `data/platform/PlatformWeightRegistry.kt` (platform ordering/weighting).
- `data/emulator/M3uManager.kt` (multi-disc), `CoreSystemDataManager.kt` (Dolphin/PPSSPP assets).

Grill (decisions for the user):
- Folder-based or file-based saves? Shared memcard? GCI?
- BIOS required (gating) or optional?
- Multi-disc (m3u)? Special system assets?
- Is this a family variant (nightly/fork package) -- prefer prefix matching, not synthesized id.

Loose ends (agent finds, never asks):
- Canonical slug + existing aliases.
- Which cores already list the platform; whether both resolvers currently agree.

Proof obligations:
- supportedPlatforms in EmulatorRegistry has a backing core in LibretroCoreRegistry.
- SavePathRegistry + (if mandatory) BiosPathRegistry cover the platform.
- On-device: game launches and a save round-trips.

---

## Axis: SETTINGS / PREFERENCES
Routing: autonomous-eligible (linear chain; trace the consumption site)

Triggers: adding/changing a user-facing setting.

Lockstep chain (15 links; the trap is the last):
enum/model -> data-class field + default -> DataStore key -> read path -> write setter ->
`UserPreferencesRepository` aggregation -> `SettingsModels.kt` UI state ->
`SettingsInitRouter.kt` hydrate -> the owning delegate/router (sync toggles:
`SyncSettingsDelegate.kt`; builtin: `SettingsBuiltinRouter.kt`; gamepad A-press
routes through `SettingsConfirmRouter.kt`, NOT the section file) ->
`SettingsViewModel.kt` method -> section `CyclePreference/SwitchPreference/...` render ->
CONSUMPTION SITE (where behavior actually changes) -> `SessionStateStore.kt` (if dual-screen/
in-game) -> RetroArch propagation (`RetroArchConfigParser.kt`, if it must reach the emulator) ->
label + subtitle.

Grill (decisions for the user):
- What behavior must actually change at the consumption site? (prevents the ghost setting)
- Does it need to reach RetroArch / the companion process / in-game session state?
- Gated by hardware (hasSecondaryDisplay) or a feature flag?

Loose ends (agent finds, never asks):
- The repository + section file the setting belongs in (mirror a sibling setting).
- Whether a SessionStateStore entry is needed (cross-process).

Proof obligations:
- Trace one path from setting value -> changed behavior (no ghost setting).
- Persists across restart; UI reflects stored value on launch.

---

## Axis: INTERACTIVE UI
Routing: autonomous-eligible (on-device both-modalities proof)

Triggers: new/changed screen, menu, modal, or preference item.

Lockstep set:
- Touch: `Modifier.clickableNoFocus` (`ui/util/Modifiers.kt`) on every clickable; never plain
  `clickable()`.
- Gamepad: `InputHandler` impl (`ui/input/InputHandler.kt`) + DisposableEffect/ON_RESUME
  `inputDispatcher.subscribeView(...)` with route; unsubscribe onDispose.
- Modal/preference: `ui/components/Modal.kt`, `PreferenceItem.kt`, footer hints
  (`FooterHint.kt`, InputButton enum).
- Focus visuals: `ui/util/FocusModifiers.kt` (focusGlow/Border/Background); index in ViewModel,
  NOT Compose focus.
- Dual-screen: `DualScreenManager`, `SecondaryHomeInputHandler.routeInput()`; broadcast
  selection/modal state; guard on `hasSecondaryDisplay`.
- Scale: `ui/theme/UiScale.kt` AspectRatioClass (TV = no touch).
- Tokens: `Dimens.*` / tokens.json, NEVER hardcoded dp (standing stray example:
  `CARD_WIDTH = 140.dp` in `ui/dualscreen/home/DualHomeLowerScreen.kt:91`).

Grill (decisions for the user):
- Does it appear/behave differently on the companion screen or Android TV?
- New actionable buttons -> which footer hints?

Loose ends (agent finds, never asks):
- The nearest existing screen/component to mirror for input + focus wiring.
- Which tokens already cover the needed dimensions.

Proof obligations:
- On-device: confirmed reachable AND operable by BOTH touch and gamepad.
- No hardcoded dp literals introduced.

---

## Axis: UI SOUND
Routing: autonomous-eligible (single-engine surface; proof is an on-device listening pass)

Triggers: changes to UI sound playback, sound settings, sample files, or sound-pack work.

Lockstep set:
- `ui/input/SoundFeedback.kt` -- SoundFeedbackManager (SoundPool engine, SoundPreset enum,
  load/retrigger/priority logic). THE core; public API (play/setEnabled/setVolume/
  setSoundConfigs) is depended on by ~30 injection sites and ~98 play() call sites --
  keep it stable and no DI changes are needed (Hilt auto-provides, no module).
- `core/input/SoundConfig.kt` -- SoundType enum (17 values) + per-type config model.
- `ui/input/InputDispatcher.kt` playFeedback -- implicit per-event default sounds;
  `InputHandler.kt` InputResult.handled(soundOverride).
- `data/preferences/ControlsPreferencesRepository.kt` (sound_enabled/sound_volume/
  sound_configs serialized string) + `UserPreferencesRepository.kt` aggregation.
- Settings UI: `SoundSettingsDelegate.kt`, `SoundPickerPopup.kt`,
  `ui/screens/settings/sections/ThemeSoundsSection.kt` +
  `sections/input/ThemeSoundsSectionInput.kt` (sound rows moved out of InterfaceSection).
- Samples: `app/src/main/res/raw/*.ogg` (15, mono post-2026-07 re-encode; loudness
  hierarchy nav -12dB / interaction -8dB / event -5dB peak).

NOT coupled: AmbientAudioManager (separate MediaPlayer engine, USAGE_MEDIA),
LibretroActivity (no UI sound in that window), Oboe core audio.

Grill (decisions for the user):
- Rapid-repeat feel for a sound class (retrigger-cut vs overlap vs floor)?
- Sample changes: re-engineer existing vs new set (who authors)?
- Pack/custom-file scope: engine indirection now or defects-only?

Loose ends (agent finds, never asks):
- Whether the pool is lazy or eager and what gates play on load completion.
- Which SoundTypes map to which presets today (defaultPresetMap).
- ffmpeg availability for sample work.

Proof obligations:
- On-device: fast d-pad autorepeat ticks continuously (no dropouts); first input after
  a fresh launch is audible; per-sound picker remap + preview still work.
- Sound disabled -> pool never constructed (audio-focus side effects).
- No main-thread pool init.

---

## Axis: LIBRETRO SESSION LIFECYCLE (two-tier)

### Tier 1: RED ZONE -- `libretrodroid/`
Routing: MAINTAINER-DOMAIN (surface to the maintainer before ANY work; contributor PRs
here are not open without prior discussion; upstream research mandate applies doubly --
libretro API semantics are never inferred, always checked against libretro source/docs)

Triggers: anything under `libretrodroid/` -- C++ (`libretrodroid/src/main/cpp/`), JNI,
`GLRetroView.kt` internals, core loading, EGL/context lifecycle, the rcheevos native
bridge (`libretrodroid/src/main/cpp/rcheevos/` vendored source + `achievements.cpp`).

The GL-thread latch discipline lives HERE, not in the wrapper: `GLRetroView.runOnGLThread`
/ `runOnGLThreadVoid` block the caller on a CountDownLatch bounded by
GL_THREAD_OP_TIMEOUT_MS = 8000ms (`GLRetroView.kt:781`). serializeState/serializeSRAM/
destroyNative all route through it.

### Tier 2: WRAPPER (contributor-safe)
Routing: autonomous-eligible, device-proof-forced

Triggers / lockstep set:
- `libretro/LibretroActivity.kt` (session host; onPause/onDestroy teardown paths).
- `libretro/SaveStateManager.kt` (slot/quick-ring states, saveSram).
- `libretro/ui/InGameMenu.kt`, `libretro/LibretroAudioController.kt`,
  `libretro/VideoSettingsManager.kt`.
- `data/repository/StateCacheManager.kt`, `domain/usecase/state/PreLaunchStateSyncUseCase.kt`.

Laws:
- GLRetroView.serializeState/serializeSRAM/destroyNative latch-block the caller until the
  GL thread runs -- always Dispatchers.IO, never main (the PSP rotation freeze was exactly
  this). Verified pattern: the InGameMenu Quit path wraps autosave + saveSram + destroyNative
  in `Dispatchers.IO + NonCancellable` before finish() (`LibretroActivity.kt:1794`); the
  8s bounded latch itself is inside GLRetroView (red zone), NOT a join in LibretroActivity.
  KNOWN SOFT SPOT: the onPause isFinishing path still calls performAutoSaveState/saveSram/
  destroyNative inline on the calling thread (`LibretroActivity.kt:2317`) -- do not copy
  that shape into new code.
- HW cores (Dolphin/Flycast) cannot survive backgrounding: the shared EGL context cannot
  rebuild on resume. Current handling keeps a non-finishing HW core paused without destroy
  (`LibretroActivity.kt:2309`). The fix pattern is reload-on-resume, never context_reset.
  In-game features get tested on SOFTWARE cores.
- Session-foreground policy: Argosy UI foregrounded = session over; only cross-display
  sessions survive. The one launcher trigger is a launcher window gaining focus on the
  game's display (`DualScreenManager.onLauncherWindowFocused`, called from both activities'
  `onWindowFocusChanged`); the save flow runs in `SessionEndCoordinator`. Screen resume is not
  a trigger: both activities stay RESUMED on a dual-screen device. Startup orphan recovery
  runs once per process in `LauncherStartupCoordinator`.
  Rule: foreground kills the session. Exception: single-screen devices keep a 15s grace
  when the emulator package was foregrounded within the last 15s. Why: relaunch-to-resume
  is legitimate there. Boundary: dual-screen devices get NO grace; a session on a
  different display is the only survivor.
- The `isStateShapedSave` guard (`SaveSyncApiClient`, filtered in
  `SaveSyncRepository.kt:342`) is untouchable: states and saves are different pipelines.

Grill (decisions for the user):
- Does the change run during teardown (quit/rotation/backgrounding)? Which path?
- HW-core exposure: does it need to behave differently when isHwCore?
- Does it touch state files at all (drags in the state-sync peer)?

Loose ends (agent finds, never asks):
- Whether the affected call site is on the Quit path (off-main) or the onPause path (inline).
- Whether the core in play is HW-rendered (LibretroActivity.isHwCore gates).

Proof obligations:
- On-device: session start -> save -> quit round trip.
- Suspend/resume on a SOFTWARE core.
- No main-thread blocking (a frozen spinner = failure).
- State round-trip if states were touched.

---

## Axis: SESSION TRACKING
Routing: autonomous-eligible for tracking/ingest mechanics; WALKTHROUGH-FORCED the moment
an edit crosses into save archiving/negotiation (door into the save-sync axis, below)

Own identity: persistent application layer covering ALL emulators (external + built-in),
not a built-in subsystem.

Triggers: play-session start/end/recovery, live save caching during play, session
persistence, RomM play-session ingest.

Lockstep set:
- `data/emulator/PlaySessionTracker.kt` -- session lifecycle; `recoverOrphanedPlaySession()`
  (`:342`) crash/orphan recovery; `cacheCurrentSave()` (`:1029`) at session end; variant
  sessions skip save recovery (`:226`) and get null channel (`:448`).
- `data/emulator/GameSessionService.kt` -- FileObserver live-cache watcher (`:58`, `:214`);
  calls `saveCacheManager.cacheCurrentSave` during play (`:323`).
- `data/sync/SaveRecoveryGate.kt` + `data/sync/SyncCoordinator.kt` (`saveRecoveryGate.await()`
  with ORPHAN_RECOVERY_TIMEOUT_MS at `:548`) -- sync waits for orphan recovery.
- RomM ingest: `data/remote/romm/RomMPlaySessionModels.kt`,
  `data/social/uploader/RomMPlaySessionUploader.kt`, `supportsPlaySessionIngest` capability
  in `data/remote/romm/RomMCapabilities.kt`.
- Session-foreground policy + cross-display survival (`DualScreenManager.onLauncherWindowFocused`,
  see libretro axis) and session-end callers (SessionEndCoordinator, DualScreenManager,
  LibretroActivity, GameSessionService presence watch, PlaySessionTracker).

CRITICAL cross-reference: the save-archiving tail (cacheCurrentSave at session end,
live-cache during play) is a DOOR INTO THE SAVE-SYNC AXIS and inherits walkthrough-forced
escalation by cross-reference. Save sync is incredibly complex and requires a steady hand
with supplemental context; ALL doors into it escalate: pre-launch negotiation, session-end
archiving, reconcile flows.

Grill (decisions for the user):
- Does the change alter WHEN a save is archived, or only how sessions are recorded?
  (the former escalates)
- Orphan recovery semantics affected? What should happen to an in-flight session on crash?

Loose ends (agent finds, never asks):
- Whether the emulator in play routes through GameSessionService (external) or
  LibretroActivity (built-in) for the session tail.
- Whether the server advertises supportsPlaySessionIngest.

Proof obligations:
- Session record round trip: start -> play -> end produces one session with sane durations.
- Kill the emulator mid-session; orphan recovery produces a session and no duplicate archive.
- If archiving was touched: full save-sync proof obligations apply (live negotiate no_op x2).

---

## Axis: LAUNCH PIPELINE
Routing: autonomous-eligible + device proof (built-in AND one external launch, variant
isolation check, session record round trip)

Triggers: changes to game launch, resume, emulator resolution, variant selection,
intent building, pre-launch hooks.

Lockstep set:
- `ui/screens/common/GameLaunchDelegate.kt` -- resume vs fresh launch, variant resolution
  (`:219`), sync gating: `canSync` requires `resolvedVariantId == null` (`:248`) and
  pre-launch sync is skipped when `skipPreLaunchSync || resolvedVariantId != null` (`:267`).
- `domain/usecase/game/LaunchGameUseCase.kt`, `LaunchWithSyncUseCase.kt`.
- `data/emulator/GameLauncher.kt` -- intent building; `resolveEmulator()` (`:653`);
  `resolveBaseRomFile()` (`:1745`).
- `data/emulator/EmulatorResolver.kt` -- `getEmulatorPackageForGame()` (`:32`).
- `data/emulator/VariantResolver.kt` (+ `data/model/VariantCategory.kt`).

Laws:
- One resolver: `EmulatorResolver.resolveEmulatorForGame`. A second precedence chain lands
  restores in the wrong dir.
- Variant isolation: variants skip pre-launch/session-end sync, or a variant save clobbers
  the primary's server copy (enforced at GameLaunchDelegate `:248`/`:267` and
  PlaySessionTracker variant guards).
- Multi-file base resolution is all-platform in intent, with KNOWN DEBT. Rule: base/primary
  resolution should be platform-agnostic. Exception (current code): `resolveBaseRomFile`
  gates full largest-file resolution to `VariantCategory.TITLE_ID_PLATFORMS`; other
  platforms only resolve when the rom sits in a content subfolder or is an update/DLC file
  (`GameLauncher.kt:1758`). Why: title-id platforms are where multi-file bases bite hardest
  today. Boundary: extending resolution to more platforms is welcome work, silently relying
  on the current gate is not. (NOTE: the gate constant is TITLE_ID_PLATFORMS, not
  VARIANT_EXCLUDED_PLATFORMS as older notes claim.)
- External emulators launch via intent; `activityClass` FQN comes from the emulator's
  namespace, not its applicationId (`data/emulator/EmulatorRegistry.kt:106`) -- apparent
  package/class mismatches are NOT bugs.

Grill (decisions for the user):
- Does it add work to the launch hot path? How much latency is acceptable?
- Pre-launch sync hooks touched? That is a save-sync door -- escalates by cross-reference.
- Should the behavior differ for resume vs fresh launch?

Loose ends (agent finds, never asks):
- Whether both resolvers currently agree for the affected platform/emulator.
- Whether the game has variants (activeVariantFileId) and which VariantCategory applies.

Proof obligations:
- On-device: built-in launch AND one external-emulator launch succeed.
- Variant isolation: launching a variant does not trigger sync for the primary.
- Session record round trip after launch/quit.

---

## Axis: RA / ACHIEVEMENTS (router entry)
Routing: contributor-open at the Kotlin/UI layer; hardcore semantics + submission logic
gated through the ra-compliance verification pass; rcheevos native = RED ZONE (libretro
session axis).

Triggers: `data/remote/ra/` (RAApi/RAConsoleIds/RAModels), `libretro/
RetroAchievementsSessionManager.kt`, `libretro/LibretroAchievementBridge.kt`,
`data/repository/RetroAchievementsRepository.kt`, `data/sync/AchievementSubmissionWorker.kt`,
`data/remote/romm/RomMAchievementService.kt`, anything touching hardcore semantics.
Authority: the ra-compliance skill (verified greps, six gating sites). Upstream mandate:
rcheevos source vendored at `libretrodroid/src/main/cpp/rcheevos/` + RA docs -- never
inference.

---

## Axis: STORAGE ATTRIBUTION (guidance, not gate)
Routing: contributor-open; guidance below is orientation, not strict enforcement.

Triggers: `data/storage/*` (FileAccessLayer/Impl, ManagedStorageAccessor,
StorageAttributionRepository, StorageSnapshotStore, StorageVolumeDetector),
`data/preferences/StoragePreferencesRepository.kt`, Storage settings sections
(`ui/screens/settings/sections/Storage*.kt`).

Guidance:
- Understand Android filesystem access limits and HOW this project accesses storage first:
  `FileAccessLayer` + Manage Storage permission. A user-granted persisted SAF tree is
  deliberately NEVER depended on because (a) it assumes no Manage Storage permission and
  (b) it forecloses the workaround used on permissive devices (AYN, Retroid handhelds).
  DocumentsContract with `manage=true` IS used: `ManagedStorageAccessor` is tier 2 of
  every `FileAccessLayer` operation, because Android 11+ returns null for direct File I/O
  to Android/data even with Manage Storage held. "No SAF" means no persisted tree grant,
  not no DocumentsContract.
- Fingerprint-invalidation discipline: attribution caches invalidate on volume fingerprint
  drift, not time (`StorageAttributionRepository.kt:147`, `:173`).
- Storage CP4 drill-ins are SHIPPED on main (games -> platform -> per-game bucket
  breakdown, with per-bucket delete). Treat the hub as existing surface to extend,
  not as in-flight work to avoid.

---

## Axis: COVER / IMAGE CACHE (stub)
Routing: contributor-open. LIVE DEFECT flag: reports of covers still failing to cache --
open bug; modifications welcome but tread carefully.

Triggers: `data/cache/ImageCacheManager.kt`, `domain/usecase/cache/RepairImageCacheUseCase.kt`,
`recoverMissingCovers` (`ImageCacheManager.kt:1240`, boot call in `ArgosyActivity.kt`),
fallback chains.

Three fragilities:
- Covers vanish when coverPath gets nulled (dead zone: repairCover returns null on a null
  path, and resume queries never see the row).
- Resume-repair is http%-only (`GameDao.kt:434` `coverPath LIKE 'http%'`) and repairCover
  is RomM-only (requires `game.rommId`), so Steam/Android covers rely entirely on
  recoverMissingCovers at boot.
- RomM asset paths need the `/assets/romm/resources/` prefix (`RomMApiClient.kt:27`);
  the SPA catch-all fakes 200s for wrong paths.

---

## Axis: DUAL-SCREEN ROLES
Routing: autonomous-eligible, device-proof-forced (a wrong step compiles cleanly and only
shows up on two physical screens).

Triggers: anything that changes which screen is driven, what either screen shows, or how a
swap behaves.

The rule: exactly one screen is interactive at a time and carries Home, Library and Media;
the other shows detail for whatever the interactive one has focused. Select and the settings
toggle swap which is which. Nothing is addressed by display id.

Lockstep set:
- `DualScreenManager` owns shared state (OSOT). `swapRoles` -> `commitRoleSwap`; the flip is
  what reveals the incoming screen, so it happens last.
- There is one Home: `HomeViewModel`+`HomeScreen`, rendered inside `ArgosyApp`. Each activity
  renders either `ArgosyApp` or `PresentationSlotContent`, chosen from
  `DualScreenManager.companionHoldsPrimary`; a swap changes which activity renders which, it does
  not start a second Home.
- Focus travels through `refocusSelf()`, implemented by both `ArgosyActivity` and
  `SecondaryHomeActivity`. A side without it leaves the pad looking dead until a touch.
- `ArgosyActivity` (HOME through the `.MainActivity` activity-alias) is bound to display 0 and
  cannot change display. Home reaches the other screen only through `SecondaryHomeActivity`
  rendering `ArgosyApp` after a swap.
- The presentation screen draws a `PresentationSlot`. Home feeds the showcase through
  `HomeViewModel.publishCompanionDetail` (`setCompanionDetail` on focus change, republished on
  resume, cleared on dispose); other ViewModel-owned content uses `presentSlot`/`releaseSlot`.
  Key forwarding and companion lifecycle go through `CompanionHost`.

Grill (decisions for the user):
- Does the showcase change for this, and what does it draw?
- Should the swap wait for the incoming screen to settle, or reveal immediately?

Loose ends (agent finds, never asks):
- Whether the state in question lives in a `DualScreenManager` StateFlow or only in one activity.
- Which activity renders `ArgosyApp` and which renders `PresentationSlotContent` in the case
  being changed.

Proof obligations:
- Capture BOTH displays before and after a swap and diff them; byte-identical companion
  captures across a primary navigation is the signature of the companion never being told.
- Swap repeatedly and confirm input survives, since the failure mode is focus, not a crash.

---

## Axis: MEDIA / JELLYFIN
Routing: WALKTHROUGH-FORCED for playback negotiation (remote API with a Moshi model, same
silent-drift shape as ROMM); autonomous-eligible for browse and layout.

Triggers: media browse, the player, episode lists, image URLs, playback quality.

Lockstep set:
- `JellyfinApiClient.buildImageUrl` takes maxWidth/maxHeight/quality; no caller passes any,
  so the server returns originals. Size is never the problem; which image kind is.
- `MediaMapper.heroImageUrl` / `wideImageUrl` pair a kind with a tag. A parent's tag against
  a child's id is a 404, so a series is asked for untagged deliberately.
- `MediaRepository` is owner-scoped via `currentOwner()`; every read and write needs it.
- Player placement resolves through `DualScreenManager.playMediaItem` ->
  `mediaPlayerDisplayId` (an observation reported by `PlayerActivity.reportDisplay`), falling
  back to `mediaPlayerRelocationDisplayId()`. A role swap invalidates the cached value.
- `PlayerActivity.onStop` ends the viewing by design; a game launch closing the player is not
  a bug unless the player was on the other display.

Grill (decisions for the user):
- Does this change what the companion shows while something is playing?
- Autoplay/quality behaviour is a product decision, never inferred.

Loose ends (agent finds, never asks):
- Whether the field is already parsed off the wire but dropped at persistence.
- Which display the player is actually on, from `mediaPlayerDisplayId`.

Proof obligations:
- On-device playback, not just a compile: the player's display placement only shows up on
  hardware.
- For image changes, confirm against a title that lacks the preferred image kind.

---

## MAINTAINER-DOMAIN entries
Routing flag for all three: MAINTAINER-DOMAIN -- agents surface to the maintainer before
any work proceeds; contributor PRs here are not open without prior discussion.

SOCIAL: `data/social/` (ArgosSocialService, PresenceManager, SocialAuthManager, uploader/).
NETPLAY: `data/netplay/` (drivers, handshake, transport, crypto) + the coordinator at
`libretro/LibretroNetplayCoordinator.kt` (lives in libretro/, not data/netplay/).
MUSIC/BGM: `ui/audio/` (AmbientAudioManager, BgmPlaylistCoordinator,
GameThemeAudioCoordinator), `data/music/`, `ui/screens/musicbrowser/`; may graduate to a
normal axis when it matures.
