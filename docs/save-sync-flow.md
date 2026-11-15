# Save sync flow: audit and target

This document records how save sync decides "is this save correct and in sync" today, where
that decision forks across the app, and the single flow it is being consolidated onto. The
current-state section was produced by four slice audits (launch, session end and passive,
server-driven, user-initiated) and then checked claim by claim by four independent
verifiers. Paths are relative to `app/src/main/kotlin/com/nendo/argosy/`; line numbers are as
of 2026-10-02 and drift, so treat them as pointers, not anchors.

Nothing in this document is proven against live data yet. The save zone rule still applies:
`GET /api/saves` plus a negotiate `no_op` twice before any fix here is called done.

## Model (maintainer decisions)

1. The local save cache is the local authority. A game's active save is one specific cached
   version, identified by its content hash.
2. Before launch, the on-disk save is hashed and compared with the active version. With Secure
   Saves on, a mismatch means the disk is wrong and the cached version is written back.
3. When the active version is "latest" rather than a pinned older version, the active version's
   hash is compared with the server's before launch, and a newer server save is pulled.
4. Hashes are the key, the same key negotiate uses. Timestamps never decide correctness.
5. Secure Saves off is the permissive mode for people who do not launch through Argosy. It
   runs the same hash model through passive checks instead of session hooks. A disk save that
   differs from the active version is adopted as a new version, synced, and the launch
   proceeds.
6. There is one path for checking a save's correctness, used by every entry point.
7. `"autosave"` is the standard save slot for every game. `"argosy-latest"` is dead.
8. GameCube follows the Sigil spec: one save unit is every `.gci` whose game code matches the
   disc. One file uploads raw, two or more upload as a zip.
9. A change of shape (one `.gci` to a zip or back) never deletes a server save. It is another
   entry in the history.

## Current state: where the decision forks

### There is no shared decision

`GetUnifiedSavesUseCase.resolveActive` (commit 0a9388706) is documented as "the single seam
for which save is active". It is read-only and has two production callers: the game-details
status row (`SaveManagementDelegate`) and the hardcore-resume check
(`GameLaunchDelegate.isActiveSaveHardcore`). Every other path chooses "the active or latest
save" on its own, and more than ten choosers exist: `getActiveRow`, `getMostRecentSave`,
`getMostRecentInChannel`, `getMostRecent`, `getLatestCasualSaveInChannel`,
`getNewestIdInChannelForOwner`, `getLatestHardcoreSave`, the server slot's newest
`updatedAt` in pre-launch, `selectServerSaveForChannel` in `analyzeChannel`, the newest per
channel in `forceSaveCheck` and `SaveUploader`, `getPlaceableForOwner` in account switch,
and `activateSlot`'s newest-by-timestamp.
The seam is strict per channel; `getMostRecentSave` falls back across channels, so the layers
can disagree about the same game.

### Pre-launch does not look at the disk

`SaveSyncRepository.preLaunchSyncForGame` decides:

    serverHasNewer = !(serverHash == lastUploadedHash) && ourDevice.isCurrent != true

A server-side `isCurrent` flag overrides hashes. `localDirty` is only the `needsRemoteSync` flag
on cache rows. The disk-against-active decision itself is one function,
`ActiveSaveDiskJudge.judge`: a disk matching the version's known forms is left as it is, a disk
matching a newer unsynced version activates that version, a differing disk is adopted with
Secure Saves off (never another account's) and cached with `cacheAsRollback` before the version
is written with Secure Saves on; a failed rollback writes nothing. `SaveSyncOrchestrator.reconcileDisk`
(pre-launch and the cross-emulator migrator) and the built-in SRAM restore
(`SaveStateManager.placeCachedSave`, behind every resume door: the active row, the slot's latest,
the most recent save and the hardcore save) both act on its verdict. The built-in restore runs
it offline too, and treats a disk save it cannot read as one it must not write over. It still
skips GameCube entirely.

With Secure Saves off, `refreshCacheFromSystem` runs instead and does the opposite of step 2:
a differing disk save becomes a new active row flagged for upload. Folder saves are judged
dirty on newest mtime alone.

### "The hash we last uploaded" is rewritten on every fetch

`SaveSyncApiClient.adoptServerHashes` runs inside every `checkSavesForGame` and sets
`lastUploadedHash` to the server's current hash for the matching save id, without looking at
local content. Consequences:

- `analyzeChannel` reads the row after the fetch, so its hash-conflict check is always false;
  `forceSyncChannel` then force-uploads over another device's newer save.
- `preLaunchSyncForGame` reads the row before the fetch, then the fetch adopts the hash, so on
  the next launch a server change that was never downloaded looks already synced.
- `forceSaveCheck` reads the held hash before and the row after, producing false conflicts.

### The latest row has three identities

`markRestored` rewrites the null-channel `save_sync` row to `"autosave"`. Null-channel readers
(`getByGameAndEmulator`, and `getByGameAndEmulatorWithDefault`, which matches null or the dead
`"argosy-latest"`) no longer see it. `forceSaveCheck` and `syncSavesForNewDownload` then insert
a duplicate null row, which `deleteDuplicateRows` treats as a separate group.
`markUserSelectedRestorePoint(null)` finds no row after the rewrite, so a restore of a
null-channel entry is never protected at the next launch.

### Two flags mean "the user chose this version"

`userSelectedRestorePoint` (on `save_sync`, read only by pre-launch, persists until an upload)
and `activeSaveApplied` (on the active `save_cache` row, read by the built-in restore and
`ConflictAutoResolver`, reset at boot). Game-details restores set both; the LocalModified
dialog sets only `activeSaveApplied`; bulk upserts in `forceSaveCheck` and
`syncSavesForNewDownload` reset `userSelectedRestorePoint` to false.

### Session end loses work

- `PlaySessionTracker.syncAndCacheSave` clears every dirty flag for the game before caching,
  then returns without uploading or queueing if the cache step fails.
- A `Duplicate` cache result never activates the row, anywhere.
- The cache fast path (`SaveCacheDao.findUnchangedSinceMtime`) matches any row of the game with
  the same size and `cachedAt >= mtime`, with no channel, hash or path filter, before hashing.
- A session-end conflict lives only in memory and is lost if the process dies.
- Orphan recovery skips the flag clear, the cache link, the queue cleanup, the upload on
  `Duplicate`, and conflict surfacing.
- The presence watch can end a built-in session early; the real exit then does no save work.
- One session end resolves the emulator three ways (session package, `resolveSessionEmulator`,
  current config).
- `SaveCacheManager.cacheCurrentSave` and the session watcher catch `Exception`, which swallows
  coroutine cancellation and can leave an inserted, inactive, dirty row.

### Five ways to write bytes to the live save

| Writer | Backup | Hardcore gate | Unit placement | Verify | Cache row | Activate | Sync row | Device confirm |
|---|---|---|---|---|---|---|---|---|
| `RestoreCachedSaveUseCase` | no, deletes first | no | yes | yes | server entries only | yes | yes | yes |
| `SaveDownloader.downloadSave` | yes, not GCI | not cache-hit, not GCI | yes | cache-hit only | yes | yes | yes | yes |
| `SaveDownloader.downloadSaveById` | no | no | no | no | no | no | no | no |
| `resolveHardcoreConflict` downgrade | no | n/a | no | no | yes | no | partial | no |
| `CrossEmulatorSaveMigrator` | rollback before every overwrite | skips hardcore | Sigil restore, else `reconcileDisk` | no | rollback, or the adopted version | adopt only | anchor (Secure Saves off) | no |

`RestoreCachedSaveUseCase` deletes the live save before writing and before a server fetch, so a
failure leaves no save. `downloadSaveById`'s GameCube branch ignores the target path, returns
success on failure, and does not check the save's game code.

### Hashing the disk is done four ways

`calculateLocalSaveHash`, `computeRestoredHash`, `calculateArtifactHash` and
`hashArchiveRoots` (the last defines the cached archive's hash). PS2's save path is the whole
card directory while the archive holds only one game's folders, so every caller of
`calculateLocalSaveHash` on PS2 hashes the whole card and can never match: `analyzeChannel`,
`localMovedSinceUpload` (always reports "moved"), `ReconcileEffectApplier`, the downloader's
stored `localContentHash`, and restore verification.

### GameCube does not follow its spec

- Discovery returns the first `.gci` only (`SavePathResolver.discoverGciSavePath`).
- The cache stores and hashes that one file; the uploader zips every `.gci`, even when there is
  only one. The cache hash and the upload hash never match.
- A server bundle cached as `save.zip` restores through the folder branch, never through
  `GciSaveHandler.extractBundle`.
- GCI discovery and extraction ignore the built-in save-directory overrides (platform, custom,
  beside the ROM) that the launch uses.
- `GameCubeHeaderParser` reads only ISO and RVZ; other formats yield a garbage game code.
- No session watcher runs for a game with no `.gci` yet.
- On a server whose heartbeat carries a `SAVE_SYNC` block (`RomMCapabilities.supportsSnapshots`), standalone Dolphin goes through
  `SigilSaveHandler` (Sigil's `dolphin_standalone` layout, raw cards included), as do the other
  standalone card and profile emulators. On older servers each keeps its legacy handler and
  format, except a game whose saves sit on a container every game shares (a shared memory card,
  VMU or backup RAM volume) and on none of its own files: `SigilSaveHandler.route` routes it, built-in
  or standalone, and its upload and restore go through the Sigil unit (`SigilSaveLocator` reads
  this from Sigil's layout, `unkeyed` shared files against present members). While disconnected,
  the route follows the last snapshot capability the stored server reported
  (`RomMConnectionManager.snapshotsEnabled`); a server never reached keeps the legacy handler and
  format under the same shared-container exception. A built-in or RetroArch core with a Sigil
  layout whose config keeps folders or GCI files (PSP, 3DS, Wii, GameCube) downloads and restores
  through Sigil wherever it uploads through Sigil (`PlatformSaveHandlerRegistry.isSigilRoutedFor`).
  RetroArch's PPSSPP core keeps its memory stick in the save folder RetroArch hands it; its Citra
  core, and Azahar's core, keep their user folder (`Citra/`, `Azahar/`) inside that save folder.
  Without a Sigil route, the libretro and built-in GameCube cores build the unit in
  `GciSaveHandler`.
- Each `save_cache` row records the format of its bytes in `saveFormat` at write time: `neutral`
  for a unit Sigil built, `native` for the files the emulator wrote. An offline-chain push
  (`SnapshotSyncEngine.pushCached`) labels the save with the row's format, and a cache restore
  (`SaveCacheManager.restoreSave`, `restoreThroughSigil`) hands only a non-native row to Sigil, so
  neither depends on the route at the time it runs. A `neutral` row restores through
  `SigilSaveHandler.localRoute`, so it still restores after a move to an older server or a
  sign-out. A `neutral` row no local route covers (after an emulator change) is refused and
  nothing is written; a Sigil unit is never placed as raw files. A null `saveFormat` marks a row written before
  schema 206, or a server download whose format the server did not report: a push treats it as
  `native`, a restore follows the live route.
- `SigilSaveHandler` calls collect and restore with `unmanaged = true` although Argosy launches the
  game. Users also start these emulators outside Argosy, and a session can end before the content
  unloads, so a managed volume swap could rest on a stale collect and lose other games' saves.
  Restore passes `overwriteLocal = true`: the snapshot decide table (`SnapshotDecision`) is the one
  conflict gate before every automatic restore, and a restore the user picks from history is meant
  to overwrite. Revisit managed mode once sessions end on the emulator's task dying.
- Shared-volume claims use Argosy's own `newlyUnowned` (names absent at the previous collect).
  Sigil's `unownedChanged` also counts rewritten saves but measures from the state blob Argosy
  passes back, which stays at the last claimed collect; moving to it needs Sigil's
  collect-before-launch protocol and a device check on a Saturn or Sega CD shared volume.

### Bulk and server-driven paths

- `syncSavesForNewDownload` downloads every server save for a new ROM to the live path, last
  one wins, with no state-save filter, no latest-per-channel reduction, and the hardcore result
  ignored.
- Negotiate's inventory sends the stored `lastUploadedHash` and an mtime, never a hash of the
  disk, and uses stored paths with no discovery or access preparation.
- `ReconcileEffectApplier` marks downloads as `SERVER_NEWER` over any status, including
  conflicts, and drops the server hash.
- `checkForServerUpdates` (the six-hour worker) is timestamp-only and skips the latest slot.
- The Secure Saves off passive scan runs only after negotiate is available and off cooldown, or
  from a manual scan; the worker never scans. Pre-launch also refreshes, per game.
- `ConflictResolutionService` dismisses a conflict before acting and drops the result.
- Answers to background conflicts parked by the dirty-cache drain are never applied, and a stale
  answer auto-resolves the next conflict for the same game.
- `DeleteGameUseCase` deletes unsynced cache rows and queued uploads without the pending-upload
  guard that cache reset, purge and hard reset have.

### Launch paths that skip sync

`launchSimple` (disc and variant pickers, new casual and new hardcore), core-crash relaunch and
netplay joins never run pre-launch sync. A PS2 memory card picked in the picker is set after
pre-launch sync already resolved the old card. A deep link's `channel` parameter is dropped
before launch. Every one of them runs the cross-emulator migration (below), which needs no
server.

### Dead code

`DualScreenManager`'s save and state handlers (`handleSaveSwitchChannel` and the six around it)
have no callers; the companion has no save-version actions.

## Target flow (revision 2, after critic review)

Every entry point runs the same pipeline under one per-game lock. Entry points differ only in
their trigger and in which actions they may take. Revision 1 was replayed against fifteen real
scenarios by an independent critic; it failed thirteen. The defects and their fixes are folded
in below and listed at the end.

### 1. Resolve the unit

- One emulator resolver. `GameLauncher.resolveEmulator` delegates to `EmulatorResolver`. A
  session persists the emulator it started with, and session end uses that, never re-resolving.
- One unit locator returns a `SaveUnit`: members, shape (single file, multi-file, folder set),
  root, and placement for an absent unit (constructed from the layout, never a guess).
- Discovery is found, absent, or unreadable. Unreadable is never absent. Unknown placement (for
  example a RetroArch core-subfolder setting that cannot be read), an ambiguous PS2 card, or a
  Switch profile that cannot be determined among several are all unreadable, and unreadable blocks
  any write.
- GameCube: members are every `.gci` whose header game code matches the disc, de-duplicated on the
  GCI header identity, under the exact base directory the launch uses (built-in overrides and user
  overrides normalised to the card directory at any depth). Region comes from the disc header's
  region byte; `JPN` is recognised. A ROM format the header parser cannot read is unreadable.
- PS2: members are this game's folders on the card, never the card.
- Any member that cannot be read makes the whole unit unreadable. No silently partial archives.

### 2. Hash the unit

- One function hashes a unit. It returns two values: a content hash, which is exactly what is
  cached and uploaded (raw bytes for a single file, the zip entry-list hash otherwise, per the
  Sigil wire contract), and an identity hash used for "did progress change" comparisons (ignores
  RTC ticks via the Sigil unit identity, and the hardcore trailer).
- A native snapshot save compares by content hash on both sides. The server's `identity_hash` for a
  zip with one non-clock member is that member's own md5, while Argosy's native units carry no
  clock member, so content equality is the exact test and identity would never match.
- Zip entry names are canonical, not on-disk names: GameCube entries are named from the GCI
  header, cross-fork size sidecars are excluded.
- Every archive entering the cache (session end, download, legacy server shapes such as
  card-rooted PS2, raw `.gci`, `data`-only 3DS) is normalised to the canonical unit shape and
  stored with the canonical hash.

### 3. Key the slot

- `"autosave"` is the latest slot; null and `"argosy-latest"` are repaired to it once, merging
  duplicates.
- Hardcore is a key alongside the slot, not a slot: the latest version is looked up by
  (slot, hardcore).

### 4. Read the active version

- One accessor returns the active cache row for (game, owner, slot, hardcore) and two flags:
  `pinned`, persistent, meaning the user chose a version other than the server head (no pull or
  push while pinned); and `placed`, transient and reset at boot, meaning the version was written to
  disk and not yet played (built-in restore uses the disk bytes).
- Insert and activate happen in one transaction. A cache result that matches an existing row
  activates that row.
- Rollback snapshots are never chosen as active or latest.

### 5. Keep an honest ledger

- The ledger is keyed by (game, owner, slot, hardcore), not by emulator, and holds two hashes from
  the last transfer of the slot head: the server's reported hash and the local identity hash.
- Only a completed upload or download of the slot head changes which server save the ledger
  points at. Restoring a history version never moves it. The hash stored for any server save is
  the server's, refreshed whenever the server reports it (settled; see below).
- One-time repair for existing rows: keep the ledger only where an uploaded `localContentHash` or a
  cache row with the matching `rommSaveId` proves this device held those bytes; otherwise null.

### 6. Decide

A pure function, unit-tested for every arrangement, takes: disk hash or absent or unreadable,
active row, ledger, server head (hash, absent, deleted, or unknown), pinned, placed, Secure Saves,
trigger, launch mode, variant id, netplay role, slot.

- Unknown server state (unreachable, error, untrusted hash) blocks every server action. Fetch
  errors are never read as "no saves".
- Disk unreadable or unit placement unknown: block, never adopt or overwrite.
- Disk differs from active:
  - Disk hash matches a newer dirty cache row, or the game's last session never closed: adopt the
    disk (it is progress, not drift).
  - Secure Saves on: snapshot the disk (hardcore flag and trailer preserved), then restore the
    active version.
  - Secure Saves off: if the disk hash is unknown to the cache and the server history, adopt it as a
    new version and make it active; if it matches a known older version, it is a rollback, so restore
    the active version. Then the server decision below, then launch.
- Server decision, unless pinned: negotiate decides. RomM's sync endpoint is the one place
  sync state is determined; it verifies hashes against its own timestamps (the device's
  `last_synced_at`, the save's `updated_at`, `removed_at`). Argosy never compares its own
  timestamps against anything. Its job is to send honest inputs:
  - the active version's content hash, computed from the bytes (never an adopted server hash);
  - the slot, emulator and file name;
  - `client_updated_at` as settled below.
  The upload, download, conflict or no-op answer is then applied through the same writer and
  queue as every other path. The ledger exists to hold what negotiate needs and what the
  pairing endpoints record; it is not a second decision engine.
- Exemptions: new-game modes skip restore; a variant uses its own unit; a netplay guest's disk is
  restored afterward and never adopted.
- A push sends overwrite only after re-fetching the head inside the lock and proving server equals
  ledger. Queue drains re-run this decision at drain time and never force.

### 7. Apply through one writer

- Server bytes are downloaded into a cache version (normalised, step 2) before touching disk.
- One writer puts a version on disk: snapshot the current disk if it is not already a known
  version, prepare access, place by shape through the platform handler (never a generic unzip),
  apply the hardcore gate, verify against the roots the archive carries, activate, confirm the
  device with the server.
- The writer updates the ledger only when the version is the slot head.
- A version is placed on another emulator only after a layout-compatibility check.
- With Secure Saves off, server pulls land in the cache only and reach the disk at pre-launch,
  never under a running emulator.

### 8. Make state durable

- Dirty state is cleared only after an upload succeeds or is queued.
- Conflicts are stored, and an answer applies only to the conflict it was given for.
- The session watcher caches only after the unit hash is stable across two reads several seconds
  apart, and watcher rows are never the version a pre-launch restore writes.
- Cancellation is never swallowed.
- Pre-launch waits for the game's pending session end and for save recovery.
- Pruning never evicts the active row, dirty rows, the pinned version or hardcore rows; snapshots
  are de-duplicated by hash and have their own budget.

### Entry points onto the pipeline

| Trigger | Allowed actions |
|---|---|
| Pre-launch: every launch, including picker, crash relaunch and deep link (with its slot) | restore active, adopt (Secure Saves off), pull, push, conflict |
| Session end, orphan recovery (both skip netplay guests and variants) | cache disk as version, push, conflict |
| Passive scan (Secure Saves off) | cache disk as version, push, conflict (pulls to cache only) |
| User restore or activate | apply chosen version, pin |
| Account switch | archive, re-hash, tear down; never adopt another owner's bytes (ownership check) |
| Negotiate | the authority for the server half of the decision (see below) |
| New-ROM download, server update check | ask negotiate for those games; no client-side timestamp checks |

No upload path deletes server saves. A change of shape is another history entry.

## Settled decisions during review

- Secure Saves off, on a disk/active mismatch: adopt the current files as a new version, sync
  them, and proceed with the launch.
- Negotiate (the unified sync endpoint) determines sync state, verifying hashes against server
  timestamps. Argosy never compares its own timestamps.
- Negotiate is the determining factor for sync state on every supported server, 5.3.1 included.
- Hardcore saves stay in `"autosave"`; playing hardcore-only is a legitimate use of the slot. No
  separate hardcore slot. When a user mixes modes, the trailer identifies hardcore bytes and the
  one writer's hardcore gate (the existing keep / downgrade choice) applies on every path,
  including cache hits and GameCube. Locally, hardcore stays a key beside the slot so the right
  version is chosen per launch mode.
- Pinned restores: restoring an older version pins it (launches use it, nothing is pulled). The
  first session that produces a new version clears the pin, and negotiate then compares that new
  version with the server head; if the server moved since the restored version, the user gets a
  conflict prompt, never a silent overwrite. A session that saves nothing leaves the pin in place.
- Snapshots before a Secure Saves restore use the existing rollback cache (`cacheAsRollback`) as
  is. No extra pruning protection, history marking or notification.
- Upgrade: no grace period. The first launch after the update runs the normal rules, and any
  damage from stale legacy rows is accepted. No re-hash migration: the unified hasher is the one
  cache rows were always hashed with (`hashArchiveRoots` for folders), so existing PS2 and folder
  rows already match; only the comparison side changed. Old single-file GameCube rows remain
  valid one-file versions.
- Zip entry names follow the Sigil MULTI contract (members' own names). GameCube restores write
  the file name Dolphin would, so equal bytes hash equal across devices.
- First sync on a device with no pairing follows negotiate's own answer (newer timestamp wins on
  5.3.1); no client-side override. The guarantee is that the default action never loses
  progress: before any download replaces local bytes that are not already a cached version, the
  local bytes are cached, and every prompt's default choice is the non-destructive one.
- The server's hash is the authority for a server save. Argosy computes its own hash for local
  bytes, but whenever the server reports a hash for a save Argosy holds (upload response,
  download, or a listed save matching a held `rommSaveId`), the stored hash for that version is
  replaced with the server's. The ledger records which server save this device last transferred;
  the hash it compares is that save's server hash.
- `client_updated_at`, used by RomM only server-side
  (`client_changed = client_ts > synced_ts and not client_unchanged`): when the active version is
  unchanged since the last transfer, Argosy sends that transfer's server stamp; when the active
  version is new, it sends the time Argosy cached it.

## Implementation phases

Each phase is one commit, builds and passes `testDebugUnitTest`, and is proven on a device
against live data (`GET /api/saves`, negotiate no-op twice) before the next phase starts.

1. Slot identity. `"autosave"` is the only latest key in every `save_sync` and `save_cache`
   query; null and `"argosy-latest"` rows are repaired once by a migration that merges
   duplicates. The restore-point flag lands on the row restores write. Done when a null-channel
   restore, a forced save check and a new-ROM download leave exactly one latest row per
   (game, emulator, owner).
2. Save unit and hash. One unit locator (GameCube: every `.gci` with the game code under the
   launch's base directory, one raw or several zipped with their own names; PS2: this game's
   folders) and one hasher used by cache, upload, comparison and verification. The
   unchanged-since-mtime shortcut goes; a duplicate cache result activates its row; the built-in
   save folder comes from one rule shared by launch and discovery; server hashes replace local
   ones for held server saves. Done when F-Zero GX's six files cache, hash and upload as one unit
   with equal hashes everywhere.
3. One writer. Every write of a version to disk caches unprotected local bytes first
   (`SaveCacheManager.protectBeforeOverwrite`; a failed backup aborts the write), places by shape
   through the platform handler, applies the hardcore gate, activates and confirms. Built as two
   entry points behind that contract: `SaveDownloader.downloadSave` for sync pulls (its cache-hit
   shortcut included) and `SaveCacheManager.restoreSave` for cached versions. A user restore of a
   server save downloads into the cache first (`downloadToCache`) and then restores that row;
   `downloadSaveById` is gone. Shape changes never delete server saves. Done when a restore, a
   download, a hardcore downgrade and an emulator change all leave the same final state.
4. Durable session end. Dirty state clears only after an upload succeeds or is queued; conflicts
   are stored; orphan recovery matches live session end; the session keeps the emulator it
   started with; cancellation is not swallowed. Done when a kill at any point of session end
   leaves the progress cached and queued.
5. Pre-launch and negotiate. Every launch path runs pre-launch: hash disk against active, restore
   (Secure Saves on) or adopt and sync (off), then a negotiate scoped to the game decides the
   server half with honest inputs. Argosy's own timestamp checks are removed. The pinned rule
   applies. Done when F-Zero GX launches clean twice and negotiate answers no-op twice.
   Built as: `SaveSyncOrchestrator.checkDiskAgainstActive` for an active version the launching
   emulator wrote (a disk with no save gets it placed at the constructed path); an active version
   from another emulator is left to `CrossEmulatorSaveMigrator`, which has already run;
   `NegotiateInventory` reports each slot's active cached version (the last transfer's server
   hash and stamp when it is unchanged, its own hash and cache time when it is new) and reports
   the disk only for the slot in play; pre-launch, the Scan and the background worker all ask
   negotiate. The older-save session flag and the client-side upload timestamp guard are gone for
   device-fenced servers, which answer 409 themselves. Negotiate's `delete` stays a no-op: sync
   never deletes local bytes, and re-uploading a deliberately deleted save would undo it. The
   core-crash relaunch runs the normal launch.
6. Bulk and user paths. New-ROM download, forced checks, the reconcile applier, the Save Sync
   screen's conflict resolution, background conflict answers and game deletion all run through
   the same decision and writer; dead dual-screen handlers go.
   Built as: `ConflictResolutionService` dismisses a conflict only after its upload or download
   succeeds; conflicts the dirty-cache drain parks are stored in `pending_conflicts` and the
   background dialog answers them by id, so no answer lands in the in-memory map without a
   waiter; the reconcile applier never marks a slot awaiting a decision as server-newer unless
   an auto-resolve rule chose the server; Delete Download keeps every cached save and state the
   server does not hold.

## Cross-emulator save migration

`CrossEmulatorSaveMigrator.migrateBeforeLaunch` is the one owner of putting a game's active save
into an emulator other than the one that wrote it. `LaunchWithSyncUseCase.invokeWithProgress`
runs it before the sync preamble, outside the pre-launch budget and whether or not RomM is
reachable, the game has a `rommId` or sync is on. `LaunchGameUseCase` runs it for every other
fresh launch (`launchSimple`, the disc, variant and memory-card pickers, netplay joins, the
social screen); `GameLaunchDelegate.launchGame` passes `savesPrepared` so it runs once.

- Trigger: the active `save_cache` row's `emulatorId` differs from the emulator
  `EmulatorResolver` resolves for the launch. Hardcore active or newest rows and `state_`
  channels are left alone. The row carries no core, so a core change under one emulator is not
  a trigger.
- Routes come from `SigilSaveHandler.localRoute`, independent of the server's unit support. A
  built-in or RetroArch core with no layout of its own on the platform (in neither
  `LIBRETRO_LAYOUTS` nor `BUILTIN_LIBRETRO_LAYOUTS`) gets Sigil's generic `libretro` layout
  (`{stem}.srm`, `{stem}.rtc`), rooted at the built-in save base or at the save folder RetroArch
  resolves for the real core. The generic layout is local only: `route()`, which decides what
  travels to RomM, never returns it. A built-in-only core gets no generic route under RetroArch.
  Two routes with the same layout and root are the same save and need nothing.
- A pair with no route on either side, or a generic route paired with a layout of its own, is
  placed the way a launch placed it before Sigil (`SaveSyncOrchestrator.reconcileDisk`): an empty
  target gets the active save at the constructed path; a target already holding it is kept; a
  differing target is adopted with Secure Saves off and backed up then overwritten with it on.
- The target is read with `SigilSaveHandler.probe` (a collect against the stored sync state that
  stores nothing). A target that already holds the active save, or is unchanged since Argosy
  recorded the active save's hash for it, is left as it is.
- Secure Saves on: the target's save is cached with `cacheAsRollback`, then the active save is
  placed. Secure Saves off: a target changed since Argosy's anchor for (target root, target
  emulator) is adopted as a new dirty version under the target emulator and nothing is written;
  an unchanged target gets the rollback and the placement; a target with no anchor is adopted.
  The anchor is the Sigil sync state for the root, else the `save_ownership` hash for the root
  and emulator. A target save another account owns is never adopted. An empty
  target is placed into in either mode.
- Between two routes, placement goes through Sigil. A `neutral` row is restored straight into the target route.
  A `native` (or untagged) row is laid out under a scratch root in the source layout, collected
  there with no sync state, and that unit is restored into the target. The scratch root leaves no
  ownership, anchor, sync or `sigil_sync_state` row. Sigil's restore replaces only this game's
  entries in a shared container. The anchor and ownership are then recorded under the target
  emulator (`SaveCacheManager.recordPlacement`).
- A native row Sigil finds nothing in, or whose restore Sigil refuses as `UNSUPPORTED_FORMAT`,
  is placed through `reconcileDisk` when it is a single file, and only into a save path with the
  same extension.
- Unreadable target, any other Sigil refusal (`CONFLICT`, `NO_SPACE`, `NO_TARGET`, `AMBIGUOUS`,
  `DAMAGED`, `EXISTS`, I/O), a failed rollback, or a placement without Sigil that failed: nothing
  more is written, the launch continues and the active row stays for the next launch.
- Notices: a placement, an adoption, a target already holding the save and a same-layout pair
  are logged only. A save that reached the target by no path gets one notice naming the reason,
  once per active `save_cache` row (`SyncPreferencesRepository.markSaveMigrationNoticeShown`);
  later launches with the same active row log it.
- `checkDiskAgainstActive` returns `Untouched` for an active row from another emulator, and the
  built-in SRAM restore (`SaveStateManager`) loads the save on disk instead of writing such a row.

## Critic defects folded into revision 2

1. Shape migration deleted server history (`SaveUploader.needsGciMigration`): removed.
2. The watcher cached torn multi-file units: stability check, watcher rows never restored.
3. Multi-file hashes depended on on-disk names: canonical entry names.
4. #475 restore could not know the core subfolder: unknown placement is unreadable.
5. #474 GCI placement nested `<region>/Card A` under an override; PAL and `JPN` regions wrong:
   override normalisation, region from the disc header.
6. PS2 cache restore bypassed the handler; legacy card-rooted archives: normalise on entry,
   always place through the handler, ambiguous card unreadable.
7. Secure Saves off was blocked from adopting at pre-launch: pre-launch may adopt and push.
8. Queue drains forced uploads over other devices: re-decide at drain time, never force.
9. A kill between insert and activate reverted progress: one transaction, pre-launch waits.
10. Hardcore trailer broke hash equality: identity hash ignores the trailer.
11. Snapshots lost hardcore status; null merged hardcore into casual: hardcore is a key.
12. Account switch could adopt another owner's bytes: ownership check.
13. Restoring a history version moved the ledger: only slot-head transfers write it.
14. Pinned pushes met RomM's 409: overwrite only after re-fetch inside the lock.
15. The ledger was per emulator; layouts differ across emulators: ledger per slot, compatibility check.
16. Variants, new-game modes, netplay, crash relaunch, deep links: explicit inputs and exemptions.
17. Null ledger on fresh install: baseline rule.
18. Unknown server state read as "no saves": unknown blocks server actions.
19. Switch profile guessed by mtime; 3DS legacy `data` verify: unreadable when ambiguous, verify the
    carried roots only.
20. RTC ticks and unreadable members: identity hash, fail closed.
21. Secure Saves off pulls under a running emulator: cache only until pre-launch.
22. No shared lock across entry points: one per-game lock.
23. Pruning could evict active, dirty, pinned and hardcore rows: protected.
24. Adopted hashes in existing rows: one-time ledger repair, two-hash ledger.
25. Negotiate decides on timestamps: overruled by the maintainer. Negotiate stays the authority
    for sync state; the defect is Argosy's inputs (mtime as `updatedAt`, the adopted
    `lastUploadedHash` as content hash) and Argosy's own client-side timestamp checks
    (`ConflictDetector`, `checkForServerUpdates`, `analyzeChannel`'s mtime fallback), which go.
26. One pinned flag lost the transient "placed" meaning: two flags.
27. Unaddressed current-state items (fast-path dedupe, cancellation, presence watch, delete guard,
    `ReconcileEffectApplier` status overwrite, PS2 picker card timing): covered above.

## Critic round 2 (against revision 2)

### Negotiate on released RomM

Negotiate is the determining factor for sync state on every supported server (settled). On
5.3.1 (`backend/handler/sync/comparison.py`): equal hashes are a no-op; otherwise, with device
sync history, `client_updated_at` and the server's `updated_at` are compared against the
device's `last_synced_at`; without history the newer timestamp wins. RomM master adds per-device
hash baselines (`5b3d0bf54`), one session per launch (`7dd35aa13`) and exact deleted-save
tracking (`d1ce4e3db`); these refine the answer but are not required. On 5.3.1 every negotiate
cancels the previous session, so a per-game pre-launch negotiate costs a running bulk session
its counters, nothing more. The correctness work is Argosy's inputs, not the server.

Negotiate as RomM master defines it (`backend/endpoints/sync/__init__.py`):

- Request: `device_id`, `saves[]` (`rom_id`, `file_name`, `slot`, `emulator`, `content_hash`,
  `updated_at`, `file_size_bytes`), optional `rom_ids`. Argosy sends no `rom_ids`.
- Response: `session_id`, `operations[]` (action including `delete`, `server_content_hash`,
  `server_updated_at`), totals including `total_delete`. Argosy maps `delete` to no-op.
- The head is the newest `updated_at` row per (rom, slot); pairing is per (device, save) and is
  never keyed by emulator.
- Without `rom_ids`, every unpaired head in the library comes back as `download`, so a per-game
  negotiate must always be scoped with `rom_ids`.
- Hash baselines are only recorded if the client sends `content_hash` on upload and on
  `/downloaded`; Argosy sends neither today.

### Optional server improvements (in `../romm`, not blockers)

- S1. Upload dedupe matches any version in the slot, so re-uploading an older version's bytes
  links pairing to the old row and never moves the head. Dedupe against the head only, or
  promote the match to head.
- S2. A compare-and-swap upload parameter (expected head id or hash, 409 on mismatch) to replace
  overwrite-after-refetch, which races.
- S3. An opt-in negotiate flag that returns `conflict`, not a timestamp verdict, when there is no
  pairing and hashes differ. Without it, a fresh install or a reset ledger uploads over newer
  heads by timestamp.
- S4. A one-time backfill of `content_hash` for slotted rows where it is null.
- S5 (optional). Hardcore is a local key but the server pairs on (rom, slot) only; either a
  hardcore qualifier in pairing or a distinct hardcore slot name.

### Defects still open or introduced

- Canonical zip entry names change the hash of every existing server zip and depart from the
  Sigil MULTI contract (entries are the members' own names). Follow Sigil instead, and fix
  Argosy's GameCube restore naming to match Dolphin's, so equal bytes hash equal everywhere.
- Hardcore as a local key collides on the server's single (rom, slot) pairing.
- `pinned` needs a clear rule: the first session end that produces a new version clears it, and
  its push goes through compare-and-swap.
- A head that fails the layout-compatibility check loops as a download every launch; it needs a
  stored "not placeable here" outcome.
- Snapshots can hold progress that exists nowhere else; never evict a snapshot whose identity
  hash is unknown elsewhere, and cap snapshot bytes.
- The Secure Saves off "known older version" rollback test contradicts the ruling (adopt the
  current files) and reverts legitimate resets; drop it and let negotiate's deleted-save and
  baseline rules decide.
- First launch after upgrade: legacy active rows carry hashes from the broken hashers (whole PS2
  card, first `.gci` only), so every PS2 and GameCube game would read as a mismatch and restore a
  stale cache. Re-hash every row with the unit hasher first, and treat the first mismatch per
  game as adopt, once.
- GameCube ROMs in CISO, GCZ, WBFS or NKit would be permanently unreadable; the header parser
  must cover them.
- The per-game lock needs a defined scope and reentrancy; pre-launch waiting on session end must
  not hold the lock session end needs.

### Migration (no bulk re-upload, no conflict storm, nothing lost)

1. Server: leave `slot=null` archives and autosave piles untouched. S1-S4 are optional and can
   land independently.
2. No server version gate beyond the existing supported minimum.
3. One local DB migration: fold null and `"argosy-latest"` into `"autosave"` (merge duplicates),
   split hardcore into its key, move `isActive` to the new key, re-key the ledger per slot (null it
   where emulator rows disagree).
4. Re-hash every cache row with the unit hasher; keep the server's hash for that save as an alias;
   never mark a row dirty because of a re-hash.
5. One scoped negotiate per account with alias hashes to seed baselines without traffic.
6. Unequal pairs become stored conflicts shown as one review list; nothing is applied in this pass.
7. First post-migration disk mismatch per game adopts instead of restoring.
8. Queued uploads from the old client re-decide at drain, compare-and-swap, never forced.
9. Prove on live data: `GET /api/saves` row count unchanged; negotiate twice all no-op except the
   stored conflicts; spot-check a GameCube zip, a PS2 card game and a hardcore game.
