# RomM sync rules

Rules the RomM library sync keeps, and why. Code KDoc names what a declaration is. The
reasoning lives here.

## One game per RomM rom

Every RomM rom syncs as its own game row. Regional copies RomM lists as siblings (a USA and a
Japan release of the same title) keep separate rows, separate rom ids and separate saves. The
Library shows one entry per sibling group (see "Sibling groups" below), so the split is in the
data, not in what the user browses. Argosy used to merge siblings into one game. Play history,
saves and screenshots then reported under the winner's rom id whichever copy was played (issue
#461), and the merge hid the losing copies.

`SiblingSplitRepair` runs once, after the first clean library pass on a build without merging.
The pass gives every sibling rom its own game and moves its `game_files` rows there. The repair
then repoints what still refers to the merged game:

- a launch path that belongs to a sibling's file moves to that sibling, and the merged game
  falls back to its own downloaded file;
- one downloaded file claimed by rows of several games (two RomM roms with the same file name,
  such as a folder rom and a loose copy) stays with the oldest game, the one that downloaded it,
  and the split-off games release it;
- one file several games adopted by title during discovery, with no `game_files` row pointing at
  it, stays with the game whose rom file shares its name (the oldest game when none does), and
  the others release it;
- a sibling whose file is on disk but whose game has no launch path gets it relinked;
- remembered version selections that point at another game's file are cleared;
- `save_sync` rows keyed to a sibling's rom move to that sibling, with the region prefix the
  merge added to the channel name removed;
- `save_cache` and `state_cache` rows carry no rom id, so the channel prefix identifies them: a
  row moves to the one regional copy (same platform and IGDB id, or the owner a moved `save_sync`
  row named) whose region or `Version <rommId>` prefix it carries. A prefix two copies share
  identifies nothing and the row stays. A moved save cache row is never the active row;
- built-in saves kept under `saves/variants/<fileId>` for a file the merge had tagged with a
  `versionGroup` are copied (never moved) into the save directory of the game that now owns the
  file's rom, when nothing is there yet. This is a one-time repair of merge-era data. New play
  never writes version-grouped files, and `VariantFileCleanup` clears the tags once the repair
  has finished (see "Files inside one rom").

A `save_sync` row the repair could not move, because the sibling's slot was already taken, stays
where it is. `SiblingSplitRepair.retryBlockedSaveSyncMoves` retries those moves after every
complete library pass once the repair has finished. It moves a row only into a free slot and
never overwrites or deletes one.

`SiblingConfigCarryOver` runs once after the repair. When exactly one non-hack member of a sibling
group has a per-game emulator config (the merged winner kept it), it copies that config to every
other member that has none. A group with two or more configured members is left alone, and an
existing config is never replaced.

Play history the merge summed into the winner (playtime, play count, last played, sessions)
follows the saves. When every `save_sync` row the winner holds is keyed to one sibling and each
will land in a free slot there, the history moves to that sibling first and the rows follow.
History moves before the rows so an interrupted run leaves the rows in place: the retry finds
them again and repeats the move, which adds nothing once the winner's history is zero. Otherwise it stays on the winner:
nothing records which copy the play came from, and history split away from its saves would
point Recent at a game whose progress lives elsewhere. Favourites, ratings and status stay put.

## Sibling groups

A sibling group is RomM's gallery key, not RomM's sibling links. `RomMSiblingIdentity.groupKey`
takes the first non-null id in RomM's order (igdb, ss, moby, ra, hasheous, launchbox, tgdb,
flashpoint, steam), the same order as `group_by_meta_id` in RomM's `roms_handler.py`, and stores
`<source>-<RomM platform id>-<id>` in `games.siblingGroupKey`. The platform id is part of the
key, so the same title on two platforms is two groups. A rom with none of those ids has no key
and is never grouped.

Sibling links would chain unrelated games together. RomM calls two roms siblings when any one
provider id matches, and on real data that merges HeartGold with SoulSilver (shared ra id) and
Ranger with Shadows of Almia (shared igdb id). The gallery key matches what the RomM web UI
shows as one card.

Argosy adds no links or overrides of its own. A wrong group or a wrong classification is fixed
in RomM, through the rom's metadata or its filename tags.

Migration 198 fills the key for existing RomM rows from the provider ids already stored. `steam_id`
is not stored on `games`, so a rom grouped only by steam gets its key on the next library sync.

### Hacks and translations

`RomMSiblingIdentity` classifies a member from RomM's filename tags alone:

- a tag equal to `Hack`, or starting with `patched-`, compared case-insensitively, marks a hack
  (`games.isHackVariant`);
- a `Translation` tag marks a translation (`games.isTranslationVariant`), and a translation is
  never a hack, even when it also carries a hack tag;
- a filename containing `(demo`, `(beta`, `(proto`, `(sample`, `(kiosk` or `(preview` marks a
  pre-release, the same rule as RomM's `PRERELEASE_FILENAME_TAGS`.

There is no hash rule. Most retail GBA roms on a real server carry igdb and ss ids with no
hasheous or ra id, so "identified but no hash match" would have marked them all as hacks. A hack
RomM does not group with its base (matched only by LaunchBox, for example) is a standalone game.

### Which member the Library shows

`SiblingGroupRanking.visibleMembers` picks one representative per group, in this order:

1. the device-local pick for the group, when it names a member;
2. members RomM flags `is_main_sibling` for this account, ranked by the order below;
3. every non-hack member, ranked by the order below.

The order within a tier is full releases before pre-releases and translations, then region
priority, then filename (case-insensitive), then game id.

Hacks sit outside that order. Every hack in a group is always visible as its own entry, beside the
representative. A hack becomes the representative only when it is the local pick or RomM's main
sibling, and then no non-hack member is shown.

Region priority is the `region_priority` key, an ordered list of every known region. It is
account-scoped and seeded once from the sync region filter's include list (regions the filter
does not name follow in the default order). A member's rank is the best position any of its
regions holds in that list, and a member with no listed region ranks last. The sync region filter
only decides what syncs; it has no say in which copy is shown.

`is_main_sibling` lives on RomM's per-user `rom_user` row. Argosy reads it into the fetching
account's `game_user_overlay.rommMainSibling` and never writes it back; the ranking reads the
active account's value, so one account's main copy never decides another's library. With no
signed-in account nothing is recorded and the ranking falls back to region priority. A library
pass or a single-game refresh copies it from the rom payload when the payload carries `rom_user`. After a download finishes, and on
launch beside the pre-launch sync, one background `GET /api/roms/{id}` re-reads it. A failed
request or a payload without `rom_user` keeps the stored value.

### The pick is device-local

The user's choice of copy is stored in `game_group_picks` (owner, group key, game id), one row
per group per account, on this device only. Argosy never sends it to RomM. Deleting the picked
game deletes the pick.

When no pick exists yet, a one-time seed fills it: a group with more than one member and exactly
one downloaded member gets that member as its pick. Groups that already have a pick are left
alone. `SiblingPickSeed` is the rule; `SiblingGroupRepository.seedPicksOnce` runs it once per
account, the first time that account is signed in, and records the account id in
`sibling_pick_seeded_owners`.

### Visibility waits for a full pass

`games.isGroupVisible` holds the result and every collapsing query filters on it. Before the
first complete library pass on a build with sibling groups, every row stays visible. Migration 198
backfills group keys but cannot backfill the hack and translation flags, because RomM tags are
not stored locally. Collapsing on that data would rank hacks as releases.

A complete pass is a full `syncLibrary` run with no errors and no platform resumed from a
checkpoint. At its end `SiblingGroupRepository.completeFullPass` sets `sibling_full_pass_done` and
recomputes every group. A weekly change-only sync, a failed pass or a resumed pass recomputes but
does not open the gate.

After the gate, groups are recomputed on every library pass, on a single-game refresh, on a pick
change, on a RomM main-sibling change, on a region priority change and on an account switch. Rows
without a group key are always visible.

### Where groups collapse

Surfaces that browse the library show one entry per group. Surfaces that point at a specific game
show that exact row, whichever copy it is, so a favourite, a collection member or a recent play
never jumps to a different rom.

Collapse (filter on `isGroupVisible`):

- Library lists: all games, per platform, playable, playable per platform;
- the Library's genre and region filter options and its showcase covers;
- platform game counts and downloaded counts, and the platform stats game and installed counts
  (the achievement and play-time sums in those stats still cover every row);
- Search and the quick-menu search;
- Home platform rows (`getByPlatformSorted`, `getByPlatformTitleOrdered`);
- `getByPlatform`, which Game Details falls back to for prev/next when it was opened without a
  navigation list.

Exact rows (no group filter):

- favourites, recently played and play history;
- collections and pinned collections (`CollectionDao` has no group filter);
- hidden lists and source lists;
- the Home genre list and the settings ambient covers.

Home grid tiles, the random tile, recommendations, related games and the social and editor game
pickers are meant to show exact rows too. None of them reads a query that filters on
`isGroupVisible`; a new query for any of them must keep it that way.

### Choosing a copy

Downloading an entry whose group has more than one member, from Game Details, the Library or Home,
opens a variant picker. Each member shows its title, its filename's region, revision and other
tags (or its stored regions when the filename has none), and a Hack, Translation or Pre-release
label where one applies. Choosing one stores it as this device's pick and downloads that rom.
Launching never prompts.

The pick can be changed later from "Active Variant" in the game menu that holding A opens on Game
Details, in the Library and on Home. The entry appears only when the game's group has more than
one member. On Game Details the same menu also opens from its Options entry, so touch and TV reach
it without a hold. "Automatic" clears the pick and hands the choice back to the ranking. Picking a
copy that is not downloaded shows the entry as not installed until it is.

Both choices write the device-local pick only. RomM's main sibling stays read-only, as described
under "Which member the Library shows".

### Save sync across copies

Uploads key on the played row's `rommId`, so each regional copy and each hack keeps its own
saves and states on the server. The pieces that keep that true:

- `LaunchWithSyncUseCase` resolves the emulator through `EmulatorResolver`, so pre-launch sync and
  the launch agree on the emulator and its save path;
- session-end save and state sync resolve the emulator with
  `EmulatorResolver.resolveSessionEmulator`: the session's own package when it names a known
  emulator, otherwise the game's configured one;
- a pending save, state or screenshot upload whose queued `rommId` no longer matches its game's
  current `rommId` is dropped from the queue (`QueueRomTarget.targetsRomOf`). A save or state stays
  on disk; the screenshot's upload copy is deleted;
- a session is a variant session only when a variant file actually launched. A requested variant
  that is not launchable runs the primary rom as a normal, synced session.

Emulators that key saves by serial or title id can still share one folder across copies on the
device. See `docs/save-id-to-path.md`.

## Files inside one rom

A RomM rom can carry extra files in category folders. `VariantCategory.isLaunchTarget` decides
which can run in place of the rom:

- `hack/` and `mod/` hold patch files, not playable roms. They never launch, never appear in the
  variant picker, and are off by default in the download file picker but can still be selected.
- A nested file with no category never launches.
- `translation/`, `demo/`, `prototype/` and `patch/` files are version choices: the user can run
  one instead of the base file. A session running one skips save and state sync, because RomM
  saves have no per-file target. Built-in isolates their saves under `saves/variants/<fileId>`.
  Other emulators write wherever they normally write. One that keys saves by serial or title id
  writes into the base game's save folder.
- `update/` and `dlc/` stay non-launch.

`VariantFileCleanup` runs once per device (`variant_file_cleanup_done`). It clears the launch flag
on rows whose category never launches, clears `activeVariantFileId` and `lastPlayedFileId` where
they point at a file that cannot launch as a variant (so the next launch is the base rom with sync
on), and removes every legacy `versionGroup` tag. It waits for `SiblingSplitRepair` to finish
while version-grouped rows remain, because the repair's variant save copy reads those tags. It
never touches files or saves on disk. Downloaded hack files and existing `saves/variants/<fileId>`
directories stay where they are and are not copied into the base.

## Patching a rom

To play a romhack with its own library entry and its own synced saves, patch it in RomM's web
patcher and let RomM store the result as a new rom. The patcher names the output
`<name> (patched-<patch>)<ext>`, which Argosy classifies as a hack. Keep that tag, or add
`(Hack)` to the filename of a rom patched elsewhere. Without either tag Argosy treats the rom as
another release of its group and may hide it behind the base game. A patch file kept inside the
base rom's `hack/` folder is only a reference; Argosy never runs it.

## Game files record everything the server reports

`RomMGameFileSync` writes every file RomM reports for a rom: discs, updates, DLC and soundtrack
tracks. Rows are keyed by `rommFileId`, so a file stored under another game moves to the syncing
game with its local path. Which files a platform offers or downloads by default is decided in
the download and variant layers. Dropping references at sync time once left title-id platforms
with no soundtrack rows, so nothing could play a game's theme.

## A rom missing from a pass is not proof of deletion

`reconcileOrphans` turns "the server did not return this rom" into a mask change or a deletion.
Absence counts as deletion only when the server has also said the rom is not hidden from this
account. Without that statement (an older server, a failed call) nothing is deleted, because for
a restricted account absence and invisibility look identical and only one of the outcomes is
recoverable.

A rom the pages returned and the pass then set aside (a filter excluded it, a folder multi-disc
parent owns its discs) was decided against, and removing it is the point. A rom the pages never
mentioned is removable only once `GET /api/roms/identifiers` agrees it is gone. While the server
still lists it, absence means it moved platform or the pass failed on it, and the row stays.

If `GET /api/roms/identifiers` fails, the pass cannot prove any rom is gone, and every deletion path
falls back to the evidence it had without that list.

## User properties from the server

Per-account game state lives in `game_user_overlay`. Every overlay write is mirrored onto the
matching `games` column, which holds the active account's values so list queries keep reading
`games`; the overlay is written first, then the mirror, in one transaction. `rommMainSibling` has
no mirror and is read from the overlay alone. A missing overlay row means the account never wrote
anything for that game: reads fall back to `games`, and the first write seeds the row from it.

A pass writes each rom's `rom_user` block against the account that fetched it, never onto the
shared library row, so one account's rating and status stay off other accounts on the device.
`rom_user.hidden` lands in `user_roms_hidden` as the user's own choice, separate from the
admin-imposed `serverHidden`.

A local edit that has not reached the server wins over the server's value: hidden, rating,
difficulty, completion and status each keep their local value while a pending or in-progress
queue row for that field exists. The pass reads the unsent queue once per platform, so an edit
made during a long pass is protected from the next platform on.

## Account-scoped preference keys

`AccountScopedPreferenceKeys` lists the DataStore keys that follow the signed-in RomM account.
Membership is by key name, because the same name is declared in several repositories with
different value types. A key not listed stays device-global, so a key forgotten there degrades
to shared between accounts rather than silently empty for everyone.

These must stay device-global:

- `sync_filter_delete_orphans` decides whether a sync may delete rows from `games`, one shared
  row per rom with a CASCADE onto every account's overlay. A per-account copy let a new account
  read the `true` default and re-enable cleanup the first account had turned off.
- `secure_saves` picks one save mode for one shared save directory.
- `builtin_custom_save_path` and `builtin_custom_state_path` define the resolved save path, so a
  per-account value would make teardown and placement target different directories.
- The `active_session_*` keys are how an interrupted session is detected across a switch.
- The one-shot flags (`save_sync_local_rekey_done`, `save_path_cache_purged`,
  `sibling_split_repair_finished`, `sibling_config_carry_over_done`,
  `sibling_full_pass_done`, `variant_file_cleanup_done`, `builtin_migration_v2`,
  `last_integrity_check_time`, `emulator_update_last_check`) are device migrations that run once
  per device. Re-running the rekey per account deletes save-sync rows.
- The setup confirmation markers (`romm_library_confirmed`, `rom_storage_path_confirmed`,
  `save_sync_answered`, `default_home_declined`) sit beside device values, so they stay device
  keys with them.
- `sibling_pick_seeded_owners` is one device key holding the account ids already seeded, because
  picks are per account and each account gets its own one-time seed.

`region_priority` is account-scoped, like the sync region filter it is seeded from.
