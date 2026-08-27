# Argosy Agent Constitution

Argosy is a controller-first game launcher for emulation handhelds and Android
TV, built around the RomM backend. This file is the structural law of the
project for ANY coding agent; CONTRIBUTING.md covers contributor expectations.
Deep domain guidance lives in the skills named by the routing table at the end.
Laws here carry their justified exceptions: a rule, its exception, why the
exception is legitimate, and the boundary where it becomes a violation again.

## Architecture

- Layers: ui/ -> domain/ -> data/. Dependencies flow inward only.
  - domain/ is Compose-free (hard rule). Android-framework-free is
    aspirational with known debt across 12 files (Intent in LaunchGameUseCase,
    Log in several use cases, and a whole media-codec pipeline in
    MeasureTrackLoudnessUseCase); the code-quality skill lists all 12. Existing
    debt is not a licence - do not add new framework imports.
  - ui/ reaches game/platform/collection data through repositories, never
    GameDao/PlatformDao/CollectionDao directly (aspirational with a known
    violator list in the code-quality skill; do not add new ones).
- Decomposition: ViewModels ~500 lines then extract delegates; repositories
  ~300 then extract services; routers split method routing (see
  GameDetailViewModel + delegates/, SaveSyncRepository + services).
- Compose stability contract: app/compose_stability_config.conf declares
  data.model.**, data.local.entity.**, ui.screens.**, ui.components.** and
  ui.dualscreen.** stable - those five, not all of ui/. ui.primitives is NOT
  covered despite holding FocusIndicators, InputGlyph and ConfirmModal.
  val-only state in covered packages; violations silently skip recomposition.
  Non-negotiable.
- Settings chain: DataStore key -> domain prefs repo -> UserPreferences
  aggregation -> SettingsModels state -> SettingsInitRouter hydrate -> owning
  delegate/router (gamepad A-press routes via SettingsConfirmRouter, not the
  section file) -> section render -> CONSUMPTION SITE. A setting with no
  consumption-site change is a ghost setting; trace the full chain.
- Label vs token: every user-facing string is a resource id; every stored,
  compared, routed or path-forming string is a literal token. One string must
  never be both, because renaming a label to read better then invalidates rows
  already written. Where they are identical today, introduce the token first
  and give the label its own key. Labels for data/ and domain/ types attach as
  extension properties in ui/common/ (see CompletionStatusUi.kt), so R never
  crosses inward. Carriers are @StringRes Int, never (Context) -> String: a
  lambda is not stability-safe under compose_stability_config.conf, and
  ViewModels outlive the activity recreation a locale change triggers. Never
  deduplicate identical English across usage sites - the seven
  LibretroCoreRegistry display names that are byte-identical to save-tree
  folder names are why. Upstream identifiers are never translated, and
  libretro/coreoptions/** stays English by decision, not by omission.
- DB: schema change = migration in data/local/migrations/Migrations.kt AND
  an append to MigrationRegistry.ALL (or it silently never runs) AND a
  version bump AND the exported schema JSON.
- Dual-screen: OSOT. DualScreenManager StateFlows are the single source of
  truth; CompanionHost is the push channel; activities are consumers. Never
  anchor shared state in an activity.
- Off-main-thread: file, network, DB, and blocking native calls run on
  Dispatchers.IO, never main (GLRetroView serialize/destroy latch-block the
  caller until the GL thread runs). A progress overlay over a blocked main
  thread is a bug, not a mitigation - a frozen spinner means main is blocked.

## Feature completeness (what "done" means)

Every user-facing feature, no exceptions unless justified in review:
1. Dual-modality input: touch via clickableNoFocus AND gamepad via
   InputHandler with ViewModel-owned focus index. One modality = incomplete.
2. Dual-screen parity considered (built OR explicitly deferred with reason).
   Where a shared component already exists, parity means rendering it on the
   other surface. Hiding the entry point instead is a ghost setting, not a
   deferral; a capability flag is legitimate only when it names what the surface
   cannot host.
3. TV/AspectRatioClass behavior (TV has no touch): every action needs a
   controller mapping and/or a navigable menu path, never touch alone
   (e.g. favorites: gamepad button + game-details action + library menu).
4. Sound feedback wired (InputDispatcher defaults or explicit override).
5. Dimensions/colors from tokens (tokens.json -> generated), never literals.
6. Footer hints follow control-is-the-guide (non-obvious hints only).
7. Empty/error/loading states exist.
8. On-device verification for anything the completeness matrix touches.

## Fragile zones (elevated bar)

- Save sync (data/sync/, save repositories): incredibly complex; every door
  into it (pre-launch negotiation, session-end archiving, reconcile) requires
  live-data proof - GET /api/saves, negotiate no_op twice. Code-reading is
  never sufficient evidence of correctness here.
- Hardcore/RA compliance: strict by intent; the ra-compliance skill is the
  authority; do not loosen rules as a side effect of other work.
- Native red zone: libretrodroid/ (C++/JNI/GLRetroView/rcheevos bridge) is
  maintainer-locked; do not modify without maintainer discussion.
- Maintainer domains (not open without prior discussion): social, netplay,
  music/BGM. Releases are maintainer-only.

## Handling user data that looks wrong

- Recovery before rejection. For a malformed archive, an unexpected layout, a
  stale or mis-keyed row: the order is repair, then ignore, then discard.
  Discarding needs a stated reason repair is impossible, not merely harder, and
  a choice between "accept it" and "throw it out" is not a complete set of
  options until you have established whether the data can be placed correctly
  from what is already known. The legitimate exception is data that identifies
  nothing - if it cannot be distinguished from another game's or another user's,
  refusing is correct. The boundary: "no mapping exists" is a finding, "I did
  not want to write the mapping" is not.
- A guard added to one path is unfinished. When adding a validation, a gate, or
  a repair, enumerate every other path reaching the same mutation and state
  which are covered and which are not. A check on the download path that the
  cache-restore path lacks is a half-built guard, and the uncovered path is
  where the damage lands.

## Upstream mandate

libretro / RetroArch / core / RetroAchievements identifiers and semantics
(core ids, option tokens, save formats, memory maps, hardcore rules) are
verified against upstream sources (libretro docs, core repos, RetroArch
source, vendored rcheevos) - NEVER inferred or analogized. Some in-repo maps
are deliberately upstream-exact (RetroArch core display names for on-disk
folder resolution); "tidying" them breaks resolution.

## Structural index (start here, don't crawl)

- Platform/emulator registries: Glob app/src/main/**/*Registry*.kt (14 today;
  the platform-support skill lists roles). Always scope to app/src/main - an
  unscoped glob picks up worktrees and build output. Core three:
  PlatformDefinitions, EmulatorRegistry, LibretroCoreRegistry - PlatformDefinitions
  does NOT match the glob, so finding 14 is not finding everything.
- Two resolvers that MUST agree: EmulatorResolver.getEmulatorPackageForGame
  and GameLauncher.resolveEmulator.
- Sync engines: SyncCoordinator (negotiate/reconcile), SaveSyncOrchestrator
  (discovery/preamble), SaveSyncRepository facade + services.
- Save id -> on-disk path, per platform: docs/save-id-to-path.md. Read it
  before touching a save handler; the id-to-path rules are upstream-exact and
  a changed archive shape invalidates every save already on a server.
- Input: InputDispatcher + per-screen InputHandler; index wrap via .mod().
  No Compose focus for navigation or selection - focusable() appears nowhere
  and must not. The legitimate exception is FocusRequester for soft-keyboard
  text entry and the root key sink in ArgosyApp; it becomes a violation the
  moment focus decides what is selected rather than what is typed into.
- Tokens: design-system-docs/tokens.json -> scripts/gen-tokens.mjs ->
  ui/theme/generated/*.
- File access: FileAccessLayer + Manage Storage permission. SAF is
  deliberately not used (assumes no Manage Storage; blocks the
  permissive-device workaround). Unreadable is not absent.
- Session layer: PlaySessionTracker + GameSessionService (all emulators,
  persistent app layer).

## Routing (touching X -> read Y first)

- Any new feature request -> the investigate skill (scoped plan before code).
- Coupled-change axes -> .claude/skills/investigate/coupling-map.md.
- Writing any code -> code-quality skill. Settings/modals -> menu-patterns.
- Dimensions/colors -> design-tokens. Dual-screen -> dual-screen skill.
- Any user-facing text -> the label-vs-token law above, CONTRIBUTING.md AS-7/AS-8.
- Platforms/emulators/cores -> platform-support. RA -> ra-compliance.
- Contributor expectations, smells, PR evidence -> CONTRIBUTING.md.
