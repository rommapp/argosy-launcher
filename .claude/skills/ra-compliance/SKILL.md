---
name: ra-compliance
description: Verify RetroAchievements hardcore compliance before release. Checks save state blocking, cheat blocking, rewind blocking, and save isolation.
---

# RetroAchievements Compliance Review

Verify the built-in emulator meets RetroAchievements hardcore requirements
before release. This skill is release-gating: a FAIL here blocks the release
until resolved or explicitly waived by the user.

**When to invoke:** before any release touching RA/achievement code, the
built-in emulator, LibretroActivity, LaunchMode, save cache/sync logic, or
hotkeys; and when debugging hardcore mode or auditing save isolation.

Every grep in this file was run against the tree and confirmed non-empty at
the time of writing (except the one marked EXPECT EMPTY). If a grep comes
back wrong, the code moved: re-anchor by tracing the named symbol, do not
"fix the code toward the skill."

References are by SYMBOL, never by line number - the greps are the durable
anchors, offsets are not.

---

## RA Hardcore Requirements (behavioral contract)

From the official docs (https://docs.retroachievements.org/developer-docs/hardcore-mode.html):

| Feature | Hardcore | Argosy enforcement |
|---------|----------|--------------------|
| Save states (save + load) | BLOCKED | HotkeyDispatcher, InGameMenu, auto-save/auto-restore gates |
| Cheats | BLOCKED | CheatSessionManager, InGameMenu cheats row |
| Rewind | BLOCKED | HotkeyDispatcher + rewind buffer never allocated |
| Fast forward | Allowed | Not gated on hardcore |
| SRAM (battery) saves | Allowed | Normal gameplay; see casualSaveInHardcore exception |

---

## 1. Preconditions: what "hardcore" means here

Effective hardcore = **requested hardcore AND secure saves enabled**. Secure
Saves is the user preference that lets Argosy own the save lifecycle; without
it, hardcore save integrity cannot be guaranteed, so hardcore is stripped at
FOUR independent layers:

1. **Pre-launch strip** - the `val launchMode = when {` block in
   `GameLaunchDelegate.kt` (grep `takeUnless { it.isHardcore }`): when
   `!prefs.secureSaves`, any hardcore override mode is discarded.
2. **Intent-arrival downgrade** - `LibretroActivity.parseIntentExtras`: reads
   `secureSaves` once, and if disabled downgrades
   `RESUME_HARDCORE -> RESUME`, `NEW_HARDCORE -> NEW_CASUAL`, then derives
   `hardcoreMode = launchMode.isHardcore`. This catches any caller that
   bypassed the delegate.
3. **switchToHardcore gate** - `LibretroActivity` (grep `switchToHardcore`):
   a resume that lands on a hardcore-tagged save only flips the session to
   hardcore when `secureSavesEnabled` is still true.
4. **Offer gate** - `PlayOptionsDelegate`:
   `hardcoreAvailable = hasRASupport && isRALoggedIn && secureSaves`; hardcore
   rows are never shown without all three.

```bash
grep -n "secureSaves\|isActiveSaveHardcore\|shouldDefaultToHardcore" app/src/main/kotlin/com/nendo/argosy/ui/screens/common/GameLaunchDelegate.kt
grep -n "secureSavesEnabled\|launchMode.isHardcore\|switchToHardcore" app/src/main/kotlin/com/nendo/argosy/libretro/LibretroActivity.kt
grep -n "hardcoreAvailable\|showResumeHardcore\|shouldShowModeSelection" app/src/main/kotlin/com/nendo/argosy/ui/screens/gamedetail/delegates/PlayOptionsDelegate.kt
```

### LaunchMode

`libretro/LaunchMode.kt`: `RESUME`, `NEW_CASUAL`, `NEW_HARDCORE`,
`RESUME_HARDCORE`; `isHardcore = NEW_HARDCORE || RESUME_HARDCORE`. Unknown
intent strings parse to `RESUME`.

### The 3-way mode preference

`builtin_default_to_hardcore_mode` (DataStore key `BUILTIN_DEFAULT_TO_HARDCORE_MODE`
in `BuiltinEmulatorPreferencesRepository.kt`) holds `"ask" | "casual" |
"hardcore"`. Consumers:

- **RASettingsSection** - "Play Mode Preference" cycle row
  (Ask / Default to Casual / Default to Hardcore); rendered disabled with
  subtitle "Requires Secure Saves" when secure saves is off.
- **GameLaunchDelegate.shouldDefaultToHardcore** - quick-launch resume
  defaults to `RESUME_HARDCORE` only when token == "hardcore" AND built-in
  emulator AND `game.achievementCount > 0` AND RA logged in.
- **PlayOptionsDelegate.shouldShowModeSelection** - the fresh-game
  mode-selection modal appears only when token == "ask" (plus built-in,
  has achievements, RA logged in, secure saves, and no existing saves).
- **PlayOptionsDelegate** - token == "hardcore" pre-focuses ResumeHardcore /
  NewHardcore in the modal.
- **SyncSettingsDelegate.toggleSecureSaves** - disabling secure saves while
  the pref is not "casual" and RA is logged in requires an extra confirm
  (the user is warned they are giving up hardcore).

```bash
grep -n "BUILTIN_DEFAULT_TO_HARDCORE_MODE" app/src/main/kotlin/com/nendo/argosy/data/preferences/BuiltinEmulatorPreferencesRepository.kt
grep -n "tokenOptions" app/src/main/kotlin/com/nendo/argosy/ui/screens/settings/sections/RASettingsSection.kt
```

---

## 2. The six gating sites

### 2.1 HotkeyDispatcher - state/rewind hotkeys

Invariant: QUICK_SAVE, QUICK_LOAD, and REWIND actions are no-ops with a toast
in hardcore. File: `libretro/HotkeyDispatcher.kt` - the `HotkeyAction.QUICK_SAVE`,
`QUICK_LOAD` and `REWIND` branches of `dispatch`, gated by the injected
`isHardcoreMode` lambda.

NAMING TRAP: `LibretroActivity` does not construct `HotkeyDispatcher`. It
constructs `LibretroHotkeyDispatcher` (`libretro/LibretroHotkeyDispatcher.kt`),
a wrapper that owns fast-forward/rewind runtime state and delegates every action
to an `inner` `HotkeyDispatcher`. It forwards `isHardcoreMode` verbatim and adds
NO hardcore logic of its own - grepping the activity for `HotkeyDispatcher(`
lands on the wrapper, not the gates.

```bash
grep -n "isHardcoreMode()" app/src/main/kotlin/com/nendo/argosy/libretro/HotkeyDispatcher.kt
grep -n "disabled in Hardcore mode" app/src/main/kotlin/com/nendo/argosy/libretro/HotkeyDispatcher.kt
```

Expect three `isHardcoreMode()` gates and toasts "Save states disabled in
Hardcore mode" (x2) and "Rewind disabled in Hardcore mode".

Belt-and-suspenders: LibretroActivity never allocates the rewind buffer in
hardcore (grep `rewindEnabled && !hardcoreMode`), and `checkStateSupport()`
forces `statesSupported = false` when `hardcoreMode`. The auto-save-state and
auto-restore paths each independently bail on `hardcoreMode`.

### 2.2 InGameMenu - state rows hidden

Invariant: Quick Save / Quick Load / Manage States rows do not exist in
hardcore. File: `libretro/ui/InGameMenu.kt`:
`val showStates = !isHardcoreMode && statesSupported && !isInNetplaySession`.
A "HARDCORE" badge renders in the menu header.

```bash
grep -n "showStates" app/src/main/kotlin/com/nendo/argosy/libretro/ui/InGameMenu.kt
```

### 2.3 CheatSessionManager - cheats never applied

Invariant: no cheat reaches the core in hardcore. File:
`libretro/CheatSessionManager.kt` - `applyAllEnabledCheats(hardcoreMode)`
returns immediately when true; `loadCheats` and `selectVariant` route through
it. Every LibretroActivity caller passes the live flag.

UI side: the Cheats menu row is gated in `LibretroActivity` -
`cheatsAvailable = !hardcoreMode && PlatformWeightRegistry.supportsCheats(platformSlug)`.

```bash
grep -n "if (hardcoreMode) return" app/src/main/kotlin/com/nendo/argosy/libretro/CheatSessionManager.kt
grep -n "cheatsAvailable = !hardcoreMode" app/src/main/kotlin/com/nendo/argosy/libretro/LibretroActivity.kt
```

### 2.4 SaveStateManager - SRAM restore per launch mode

File: `libretro/SaveStateManager.kt`. `restoreSaveForLaunchMode` is the single
entry; its KDoc states the two non-obvious rules (activeSaveApplied wins;
RESUME_HARDCORE SRAM fallback). It takes ONLY a `LaunchMode` - there is no
timestamp parameter anywhere in this path.

- **Short-circuit, before the mode branch**: when the active row has
  `activeSaveApplied == true`, the on-disk `.srm` is read and returned as-is.
  An explicit save-management restore is never second-guessed.
- **NEW_HARDCORE / NEW_CASUAL**: fresh start. Existing .srm is backed up via
  `saveCacheManager.cacheAsRollback` BEFORE deletion, then the .srm and all
  state slots are deleted. A fresh hardcore run must never destroy the only
  copy of a prior save.
- **RESUME_HARDCORE**: loads the latest hardcore save
  (`getLatestHardcoreSave`); validates the trailer with `isValidHardcoreSave`
  and logs a warning if missing. No hardcore save -> falls back to
  `restoreResumeSave` with `casualSaveInHardcore = true` (see exceptions).
- **RESUME** (`restoreResumeSave`): target precedence is the active row from
  `activeSaveRepository.getActiveRow`, else `getMostRecentSave`. If the target
  is hardcore-tagged, `switchToHardcore = true` ONLY when
  `isValidHardcoreSave` passes; an invalid trailer loads the save as casual.

```bash
grep -n "restoreSaveForLaunchMode\|casualSaveInHardcore\|isValidHardcoreSave\|switchToHardcore" app/src/main/kotlin/com/nendo/argosy/libretro/SaveStateManager.kt
grep -n "cacheAsRollback" app/src/main/kotlin/com/nendo/argosy/libretro/SaveStateManager.kt
```

### 2.5 GameLaunchDelegate - pre-launch mode resolution

File: `ui/screens/common/GameLaunchDelegate.kt`, the `val launchMode = when {`
block. Resolution order:

1. `!prefs.secureSaves` -> strip hardcore from any override.
2. Hardcore sync conflict resolved as KEEP_HARDCORE -> `RESUME_HARDCORE`.
3. Explicit `overrideLaunchMode` (from PlayOptionsModal) wins.
4. `isActiveSaveHardcore(gameId)` (active channel's most recent save has
   `isHardcore`) -> `RESUME_HARDCORE` (hardcore ratchet: a hardcore save
   resumes hardcore by default).
5. `shouldDefaultToHardcore` -> `RESUME_HARDCORE`.
6. else null (plain resume).

Preference flags are read once at the top of the launch pass and passed down;
mirror that if touching this path.

### 2.6 PlayOptionsDelegate - what the user is offered

File: `ui/screens/gamedetail/delegates/PlayOptionsDelegate.kt`.

- `hardcoreAvailable` = `hasRASupport && isRALoggedIn && secureSaves`.
- `showResumeHardcore`: offered whenever hardcore is available and ANY
  resumable save exists (casual or hardcore) - because of the SRAM fallback,
  continuing a casual save in hardcore is legal.
- `visibleActions` is the single source of truth for row order:
  Resume / ResumeNoSync / ResumeHardcore / NewCasual / NewHardcore.
- `confirmPlayOptionSelection`: NewHardcore refused while offline
  (hardcore unlocks need a live session).
- `shouldShowModeSelection`: fresh-game Casual-vs-Hardcore modal
  only when built-in + has achievements + RA logged in + secure saves +
  pref == "ask" + zero existing saves.

---

## 3. Isolation model: isHardcore flag + trailer

Hardcore saves are isolated by a **column plus an integrity trailer**, not by
slot name:

- `data/local/entity/SaveCacheEntity.kt` - `val isHardcore: Boolean`.
  `SLOT_HARDCORE` is `@Deprecated`. **DO NOT resurrect slot-name isolation**;
  anything keying on the "HARDCORE" slot string is a regression.
- Session save caching carries the flag end-to-end: `LibretroActivity` starts
  the play session with `hardcoreMode`; `data/emulator/PlaySessionTracker.kt`
  passes `isHardcore` into `saveCacheManager.cacheCurrentSave` (hardcore
  sessions also force `channelName = null` - hardcore saves live outside named
  channels, see `SaveCacheManager.resolveDefaultChannel`).
- **Trailer write**: `SaveCacheManager.cacheCurrentSave` appends the trailer
  to the cached copy when `isHardcore`, via
  `SaveArchiver.appendHardcoreTrailer`: `{"h":true,"v":1}` + LE length +
  magic, appended to the file.
- **Trailer read/strip**: `SaveArchiver.readHardcoreTrailer` /
  `hasHardcoreTrailer`; `readBytesWithoutTrailer` strips it before bytes are
  handed to the core or written to a target path (callers in
  `SaveCacheManager`).
- **Validation**: `SaveCacheManager.isValidHardcoreSave` = `entity.isHardcore`
  AND trailer present on the cached file. An isHardcore-tagged save whose
  trailer is missing (modified externally) is **demoted to casual on resume**:
  in `SaveStateManager.restoreResumeSave`, `switchToHardcore` is set only
  inside the `if (isValid)` branch, and the else branch logs "RESUME: Hardcore
  save missing trailer, loading as casual". The demotion is live; the session
  does not get hardcore credit from a tampered save.

```bash
grep -n "SLOT_HARDCORE\|isHardcore" app/src/main/kotlin/com/nendo/argosy/data/local/entity/SaveCacheEntity.kt
grep -n "appendHardcoreTrailer\|readHardcoreTrailer\|hasHardcoreTrailer" app/src/main/kotlin/com/nendo/argosy/data/sync/SaveArchiver.kt
grep -n "appendHardcoreTrailer\|isValidHardcoreSave\|getLatestHardcoreSave" app/src/main/kotlin/com/nendo/argosy/data/repository/SaveCacheManager.kt
```

---

## 4. Justified exceptions

Each exception is deliberate. Verify the boundary holds; do not "fix" the
exception itself.

### 4.1 Casual SRAM may continue in a hardcore session

- **Rule**: hardcore sessions load hardcore saves.
- **Exception**: `RESUME_HARDCORE` with no hardcore save falls back to the
  active (casual) SRAM, flagged `casualSaveInHardcore` in
  `SaveStateManager.restoreSaveForLaunchMode`; the UI surfaces "Continuing
  casual save in hardcore" (grep that string in `LibretroActivity.kt`).
- **Why**: RA forbids save STATES in hardcore, not SRAM battery-save
  continuity - stated in the `restoreSaveForLaunchMode` KDoc and mirrored in
  `PlayOptionsState.showResumeHardcore`.
- **Boundary**: any save-STATE load in hardcore remains absolutely blocked
  (sections 2.1, 2.2, and the auto-restore gates). If SRAM fallback ever
  starts touching state slots, that is a violation.

### 4.2 Speedrun mode runs alongside hardcore

- **Rule**: nothing extra manipulates game state in hardcore.
- **Exception**: the speedrun timer/splits overlay is available regardless of
  mode.
- **Why**: it only OBSERVES - reset events and hotkey-driven splits
  (`LibretroActivity.initializeHotkeyDispatcher` wires `onGameReset` and the
  five `onSpeedrun*` callbacks to `SpeedrunTimerEngine`); it never touches
  save states, SRAM, rewind, or core memory.
- **Boundary**: `libretro/speedrun/` must stay hardcore-agnostic. Verify:

```bash
grep -rn "hardcore" app/src/main/kotlin/com/nendo/argosy/libretro/speedrun/
```

  EXPECT EMPTY. Any speedrun feature that starts touching save states,
  rewind, or core memory re-clears this whole skill first.

### 4.3 Hardcore award failure falls back to casual award

- **Rule**: hardcore unlocks are submitted with the hardcore flag.
- **Exception (designed behavior)**: if the hardcore award errors, the
  achievement is re-submitted as casual and recorded locally as a casual
  unlock (`RetroAchievementsSessionManager`, the `RAAwardResult.Error` branch
  of the award handling).
- **Why**: hardcore awards cannot be queued offline (they need a live
  heartbeat); losing the unlock entirely would be worse than a softcore
  credit.
- **Enforcement site for "cannot be queued"**: in
  `RetroAchievementsRepository.awardAchievement`, EVERY `queueAchievement`
  call sits in the `else` branch of an `if (forHardcoreMode)` - HTTP failure,
  empty body, error body, and the exception handler each return
  `RAAwardResult.Error` for hardcore and only queue for casual. Adding a queue
  path that is not behind that branch would let a hardcore unlock be submitted
  after the session ended, which RA does not permit.
- **Boundary**: the fallback only ever DOWNGRADES (`earnedHardcore = false`);
  nothing may promote a casual unlock to hardcore after the fact.

---

## 5. Adjacent gates

- **Netplay guests force NEW_CASUAL**: `LibretroActivity.parseIntentExtras` -
  a join intent overrides launchMode to `NEW_CASUAL`; guests never earn
  hardcore on a host's snapshot. Netplay also blocks state operations in
  `HotkeyDispatcher` and hides state rows in `InGameMenu`
  (`!isInNetplaySession` in showStates) - but the netplay gates are NOT
  uniform:
  - `QUICK_SAVE` is blocked only for NON-HOSTS
    (`isNetplayInSession() && getNetplayRole() != NetplayMenuRole.Host`); the
    host CAN quick-save mid-netplay.
  - `QUICK_LOAD` and `RESET_GAME` are blocked for everyone in session.
  The hardcore gate is unconditional in all three cases and sits after the
  netplay check, so hardcore compliance does not depend on the role.
- **RA session carries the flag, not the heartbeat**:
  `RetroAchievementsSessionManager` starts the session with `hardcoreMode`
  (`raRepository.startSession(gameRaId, hardcoreMode)`, sent as
  `hardcore = 0/1`); awards send `forHardcoreMode`; the periodic
  `sendHeartbeat` carries no mode. Unlocks are stored split
  (`markUnlockedHardcore` vs `markUnlocked`) and social/LED surfaces receive
  `isHardcore`.
- **Unlock validation hash (upstream-exact, do not "simplify")**:
  `RetroAchievementsRepository.generateValidation` computes
  `md5(achievementId + username + hardcoreFlag)` where hardcoreFlag is the
  literal `"1"` or `"0"`, and sends it with every award. The input order, the
  string form of the flag, and MD5 itself are RA Connect's contract - a
  refactor that reorders the concatenation or switches digest produces awards
  the server silently rejects.
- **`handleAuthFailure` is a DELIBERATE no-op**: the body in
  `RetroAchievementsRepository` is empty with a long comment explaining why -
  the previous implementation cleared stored credentials on any error string
  containing "invalid"/"expired"/"credentials", which wiped tokens on
  non-auth errors like "Invalid game ID". Do not "implement" it; if auth
  handling is genuinely needed, it needs a precise signal, not a substring
  match.

```bash
grep -n "startSession\|sendHeartbeat" app/src/main/kotlin/com/nendo/argosy/libretro/RetroAchievementsSessionManager.kt
grep -n "launchMode = LaunchMode.NEW_CASUAL" app/src/main/kotlin/com/nendo/argosy/libretro/LibretroActivity.kt
```

---

## 6. UPSTREAM MANDATE

Hardcore semantics, memory addressing, and achievement logic are **verified
against upstream sources, never inferred**:

- rcheevos is vendored in-tree at `libretrodroid/src/main/cpp/rcheevos/`, but
  only PART of it is compiled. `CMakeLists.txt` builds `src/rcheevos/*.c`
  (alloc, condition, condset, consoleinfo, format, lboard, memref, operand,
  richpresence, runtime, runtime_progress, trigger, value) plus
  `rcheevos_stubs.c`. `rc_client.c` and `src/rapi/` are NOT in the build.
  So rcheevos is the authority for condition evaluation, memory peek
  semantics and runtime progress - NOT for anything on the wire.
- ALL RA server traffic is Kotlin: `RAApi` (Retrofit) driven by
  `RetroAchievementsRepository`. Hardcore flags, validation hashes, session
  start and awards are ours to get right; there is no native client to defer
  to.
- RetroAchievements docs (docs.retroachievements.org) are the authority for
  policy: what hardcore must block, award semantics, session rules.

Any compliance question this codebase cannot answer goes to those sources.
Any NEW hardcore-adjacent feature (new overlay, new input path, new save
mechanism, new core capability) verifies RA's actual rules against the docs
and rcheevos source BEFORE shipping - "it seems allowed" is not a
determination.

---

## 7. Manual verification checklist

Run when hardcore logic changed. Log tag anchors:
`[Startup] gameId=..., core=..., hardcore=...` (LibretroActivity) and
`RetroAchievementsSessionManager` session lines.

### Hardcore entry
1. With RA logged in + Secure Saves ON, launch via "New Hardcore" in the
   play options modal.
2. Verify logs show `hardcore=true` and the RA session starts with
   `hardcore=1`.
3. Open the in-game menu: HARDCORE badge visible.

### Preconditions
1. Turn Secure Saves OFF (note the confirm warning when the mode pref is not
   casual). Hardcore rows disappear from the play options modal; the settings
   Play Mode row renders disabled.
2. Launching a game whose last save was hardcore lands in casual
   (intent downgrade); verify `hardcore=false` in logs.
3. Set the mode pref to "Ask": a fresh built-in RA game with zero saves shows
   the Casual/Hardcore selection; a game with existing saves does not.
4. While offline, "New Hardcore" refuses to confirm.

### Blocking in-session (hardcore)
1. In-game menu shows no Quick Save / Quick Load / Manage States rows.
2. Quick-save hotkey -> toast "Save states disabled in Hardcore mode";
   quick-load likewise; rewind hotkey -> "Rewind disabled in Hardcore mode".
3. No Cheats row; cheats previously enabled for the game are not applied.
4. Exit the game: no auto-save state written; relaunch does not auto-restore
   a state.

### Save isolation
1. Create a casual save (in-game SRAM save), exit.
2. "New Hardcore" for the same game: verify a rollback backup is logged
   before the fresh start and prior state slots are gone.
3. Play, save in-game, exit. Verify the cached save logs `[HARDCORE]` and
   the hardcore save is not listed under a named channel.
4. Plain "Resume" now enters hardcore (ratchet via valid trailer).
5. Corrupt/strip the trailer on the cached hardcore file (test env only):
   Resume demotes to casual with the trailer warning in logs.
6. "Resume Hardcore" on a game with only casual saves shows
   "Continuing casual save in hardcore".

### Award fallback
1. With the network dropped mid-session, unlock an achievement in hardcore:
   verify the casual fallback path logs and the local unlock is casual, not
   hardcore.

---

## Integration with release process

If RA/emulator/save-cache code changed since the last release:

1. Run every grep in sections 1-5; each must match (section 4.2's must be
   empty).
2. Walk the manual checklist for the touched areas.
3. Any behavioral change to hardcore semantics gets checked against the
   upstream mandate (section 6) and documented in release notes.

## References

- https://docs.retroachievements.org/developer-docs/hardcore-mode.html
- https://docs.retroachievements.org/guidelines/users/code-of-conduct.html
- Vendored rcheevos: `libretrodroid/src/main/cpp/rcheevos/`
