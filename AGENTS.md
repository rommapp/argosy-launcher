# Argosy Agent Constitution

Argosy is a controller-first game launcher for emulation handhelds and Android
TV, built around the RomM backend. This file is the structural law of the
project for ANY coding agent. Deep domain guidance lives in .claude/skills/
(Claude Code loads these; other agents should read the files named below
before touching their domains). Laws here carry their justified exceptions:
a rule, its exception, why the exception is legitimate, and the boundary
where it becomes a violation again.

## Architecture

- Layers: ui/ -> domain/ -> data/. Dependencies flow inward only.
  - domain/ is Compose-free (hard rule). Android-framework-free is
    aspirational with known debt (Intent in LaunchGameUseCase, Log in
    several use cases); do not add new framework imports.
  - ui/ reaches game/platform/collection data through repositories, never
    GameDao/PlatformDao/CollectionDao directly (aspirational with a known
    violator list in the code-quality skill; do not add new ones).
- Decomposition: ViewModels ~500 lines then extract delegates; repositories
  ~300 then extract services; routers split method routing (see
  GameDetailViewModel + delegates/, SaveSyncRepository + services).
- Compose stability contract: app/compose_stability_config.conf declares
  data.model/entity and ui packages stable. val-only state in covered
  packages; violations silently skip recomposition. Non-negotiable.
- Settings chain: DataStore key -> domain prefs repo -> UserPreferences
  aggregation -> SettingsModels state -> SettingsInitRouter hydrate -> owning
  delegate/router (gamepad A-press routes via SettingsConfirmRouter, not the
  section file) -> section render -> CONSUMPTION SITE. A setting with no
  consumption-site change is a ghost setting; trace the full chain.
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

- Platform/emulator registries: Glob **/*Registry*.kt (13 today; the
  platform-support skill lists roles). Core three: PlatformDefinitions,
  EmulatorRegistry, LibretroCoreRegistry.
- Two resolvers that MUST agree: EmulatorResolver.getEmulatorPackageForGame
  and GameLauncher.resolveEmulator.
- Sync engines: SyncCoordinator (negotiate/reconcile), SaveSyncOrchestrator
  (discovery/preamble), SaveSyncRepository facade + services.
- Save id -> on-disk path, per platform: docs/save-id-to-path.md. Read it
  before touching a save handler; the id-to-path rules are upstream-exact and
  a changed archive shape invalidates every save already on a server.
- Input: InputDispatcher + per-screen InputHandler; index wrap via .mod();
  no Compose focus anywhere.
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
- Platforms/emulators/cores -> platform-support. RA -> ra-compliance.
- Contributor expectations, smells, PR evidence -> CONTRIBUTING.md.
