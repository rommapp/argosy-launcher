# Making library sync cheaper on the RomM server

`RomMLibrarySyncService.syncPlatformRoms` walks every enabled platform, one
page at a time, and asks `GET /api/roms` for 100 ROMs with their files. On a
large library that is several hundred sequential round trips, and each one costs
more than it has to. This file records what the endpoint actually does with our
parameters and what each fix is worth once measured.

Read the payload table before proposing anything. The parameter ideas this file
opened with were mostly wrong: one was a measured regression and is now recorded
as such, and the two the earlier draft called structural are worth 8% and 13%.
The cost is concentrated somewhere none of them touched.

Server behaviour is read against RomM 5.2.0 source; the live measurements are
against a 5.1.0-alpha.4 instance with a 23,873-rom library, and each section
says which it is.

## What we send today

`RomMApiClient.buildRomsQueryParams` produces:

```
platform_ids=<id>&order_by=id&order_dir=asc
&limit=100&offset=<n>&with_char_index=false&with_filter_values=false
&with_files=true
```

`SYNC_PAGE_SIZE` is 100. The loop in `syncPlatformRoms` is strictly
sequential, the whole sync holds `syncMutex`, and cover art is queued to
`ImageCacheManager` rather than fetched inline. So sync wall-clock is the API
loop and nothing else, which makes every millisecond of server time per page
directly visible to the user as sync duration.

## 1. The id-index opt-out is a regression - do not try it again

`GET /api/roms` has four "sidecar" aggregates that the RomM gallery uses and we
do not: a character index for the alphabet strip, a filter-value list for the
filter drawer, a full ordered list of every matching ROM id for virtual scroll,
and a total count. All four default to `true`, and sidecar memoisation only
applies to an *unscoped* request, so sync's `platform_ids` makes the cache key
null and every page recomputes live.

That reasoning says to send `with_rom_id_index=false`. It was built, measured
against a 23,873-rom library on RomM 5.1.0-alpha.4, and reverted. Median of
three, Nintendo DS (6541 roms), `with_files=true`, limit 100:

| offset | index on | index off |
| --- | --- | --- |
| 0 | 781ms | 1760ms |
| 1000 | 681ms | 1837ms |
| 3000 | 598ms | 2080ms |
| 6000 | 623ms | 2303ms |

Two to nearly four times slower at every depth, and worse the deeper it pages.
PlayStation 2 was a wash except at offset 0 (572ms to 1254ms); Nintendo Switch
was neutral because payload serialisation swamps everything there. Rows were
identical in every pairing and a full platform walk produced the same id
sequence under both settings, so the flag is not lossy - only slower.

The premise was wrong in two places. The id index under a platform filter spans
that platform, not the library, so it saves about 37 KiB per page rather than
the whole-library list the estimate assumed. And it is what lets the server
serve the page by primary key: `rom_id_index` is sliced and the rows fetched
`WHERE id IN (...)`, whereas with the index off the page comes from
`OFFSET n LIMIT m` on a platform-filtered `ORDER BY id` that has no covering
index - the same missing index recorded under "What we cannot fix from this
side", which is the cause rather than a separate curiosity.

`with_total` falls with it: `resolve_total()` returns `len(rom_id_index)` while
the index is being built, so with the index left on the count is free and
opting out of it buys nothing.

Two things from that attempt were kept, because they are correct either way:
`RomMRomPage.total` is nullable, and the page loop terminates on a short page
rather than on the total, which was previously its only exit besides an empty
page.

Anything that revisits this needs keyset pagination (`id > last_seen`) first.
That removes the deep-offset walk and the id scan together; without it, opting
out of the index only removes the half that was helping.

## 2. Deletion reconciliation via `/api/roms/identifiers` (done)

`GET /api/roms/identifiers` returns every visible ROM id as a bare array from a
single `SELECT roms.id`. It is scoped exactly as `GET /api/roms` is - the same
hidden-platform and hidden-rom filtering - so an id absent from the set is
either deleted or hidden from this account, which is the distinction
`RomMVisibility` already makes.

The set is used two ways.

`reconcileOrphans` now separates the two reasons a row can still be dirty at the
end of a platform pass. A ROM the pages returned and the pass set aside - a
filter excluded it, its dedup key was taken, a folder multi-disc parent owns its
discs - was decided against, and removing it is the point. A ROM the pages never
mentioned is only removable once the id set agrees it is gone; while the server
still lists it, absence means it moved platform or the pass failed on it, and
the row stays. A ROM whose `syncRom` threw is deliberately excluded from the
decided set, so a transient failure can no longer delete a game.

`reconcileDeletedRoms` is a new library-wide sweep at the end of a full sync,
covering what the per-platform sweep structurally cannot: a ROM on a platform
since disabled, unshared, or dropped from the server is never marked dirty, so
its row previously survived forever. It re-reads the id set rather than reusing
the one the pass opened with, because a ROM added while the pass was walking
platforms is missing from that older set and present in the library - exactly
the shape the sweep deletes.

Both withhold entirely on missing evidence: no id set, an empty id set, an
unavailable visibility answer, or any platform error.

Preservation widened at the same time. `hasLocalContent` now counts cached saves
and states, across every account rather than the syncing one. Neither table has
a foreign key to `games`, so deleting the row left them dangling and
unreachable; a game with saves but no ROM on disk is now preserved under the
synthetic negative id rather than hard-deleted.

## 3. Every sync is a full re-pull

We never send `updated_after`. Every sync fetches every ROM on every enabled
platform with its complete file list, whether or not anything changed. For a
library that changes by a handful of ROMs between syncs, that is the single
largest waste in the whole flow, and it is entirely ours.

`updated_after` takes an ISO 8601 timestamp with timezone and filters to ROMs
whose `updated_at` is strictly greater. Two things have to be solved before it
can be switched on.

**The whole-pass sweep must go first.** Every sync marks every ROMM row of a
platform dirty and deletes what is still dirty at the end. Under an incremental
pull, absent-because-unchanged is indistinguishable from absent-because-deleted,
so the sweep would delete the library minus the changed rows. Section 2 is the
prerequisite, not a companion: an incremental pass has to run none of
`markSyncDirty`, `reconcileOrphans`, sibling consolidation or dedup cleanup, and
leave deletion entirely to the id set.

**Sibling consolidation assumes it sees the whole platform.** `deleteInvalidFiles`
builds its valid-file list only from group members seen in the pass, so a group
whose other members are unchanged and therefore absent loses their `game_files`
rows - `localPath` and `downloadedAt` included, orphaning downloaded variant
content on disk. `chooseWinner` likewise ranks only the members present, and a
changed winner runs `GameAbsorptionDao.absorb`, which repoints `save_cache`,
`save_sync`, `state_cache`, tombstones and sessions to the new row and deletes
the old one. Any changed ROM belonging to a sibling group has to promote that
group to an explicit by-id fetch so consolidation still sees every member.

**A rescan defeats it.** `COMPLETE` and `HASHES` scans call `update_rom` for
every ROM they touch, so the first sync after one is a full pull regardless.
Incremental buys quiet libraries staying quiet, not small syncs in general.

**There is no server clock on this path.** `RomMRom` does not bind `updated_at`,
heartbeat carries no timestamp, and every RomM timestamp the client holds comes
from the device. A watermark needs a server-anchored source first. `lastRommSync`
is unsuitable as-is: it already advances past a partial sync, which today costs a
stale-looking timestamp and as a watermark would cost permanent data loss.
`PlatformEntity.lastScanned` exists but is never written by anything. The working
precedent is Jellyfin's `MediaLibraryEntity.lastSyncedAt` - per-library,
per-owner, stamped only on completion.

**Filter changes stop taking effect.** A ROM that starts passing the filters
because the user widened them is only picked up if its `updated_at` also moved.
A filter-preference change has to force a full pull.

**File changes may not bump `updated_at`.** RomM sets `updated_at` through
SQLAlchemy's `onupdate`, which fires when the `roms` row itself is written.
Inserting or deleting a `rom_files` row does not touch it. In practice a
changed file set usually rewrites `roms.fs_size_bytes` and so does bump the
timestamp, but a same-size rename, or a change confined to track metadata,
will not. Since we sync with `with_files=true`, that is a real hole.

Practical shape: use `updated_after` for routine background syncs and keep a
full pull for the explicit user-triggered "resync library" action and for the
first sync after an upgrade. Do not silently make incremental the only path.

## What only the server can fix

Recorded here so nobody re-derives it. Sizes are in the payload table below,
which is what decides the order these are worth asking for.

**The response carries nine provider metadata blobs per ROM and we use five.**
`RomMRom` binds `metadatum`, `ss_metadata`, `launchbox_metadata`,
`hltb_metadata` and `merged_ra_metadata`. The response also always contains
`igdb_metadata`, `moby_metadata`, `hasheous_metadata`, `flashpoint_metadata`,
`gamelist_metadata` and `manual_metadata`, which we parse past and discard.
There is no field-selection parameter; a `fields=` query param sent by another
RomM client is silently ignored, and we should not copy that pattern.

Note this is 8% of the payload, not the dominant cost an earlier draft assumed,
and that a `with_metadata=` opt-in named after these nine would not reach
`merged_ra_metadata` - which is 45% - because that one is a computed property
rather than a stored blob.

**`with_files=true` is a lightly travelled path.** No RomM web UI surface sets
that flag; we are effectively its only heavy consumer. The server batches the
file rows for the page in one query but does not eager-load each file's
`track_meta`, so the response build emits one extra query per file. It scales
with files-per-ROM rather than ROMs-per-page, which is why a soundtrack-heavy
platform is so much worse than a flat cartridge one: Nintendo Switch averages
113 file rows per ROM, so a 100-ROM page is roughly 11,000 extra queries, 9 MiB,
6.2 seconds, and a 502 on the request after it.

**The list endpoint undefers three correlated scalar subqueries per row.**
`include_file_stats=True` is hardcoded at `roms/__init__.py:693` and undefers
`multi_file`, `top_level_file_count` and `has_soundtrack` - 300 subqueries on a
100-ROM page. `top_level_file_count` compares concatenated paths, so no index
can serve it, and the comment above the column definitions already asks for this
to be revisited. Sync uses none of the three; it derives multi-disc and variant
structure from the `files` array it already requests.

**Ordering by `id` under a platform filter has no covering index.** The server
has `(platform_id, fs_name)`, which serves the filter but not the sort, so the
page is sorted after the fact. Switching our sort key would not help; the
gallery's own `name_sort_key` ordering has the same problem. This is the cause
of the section 1 result rather than a separate curiosity, and keyset pagination
is what actually removes it.

## Parameter reference

Defaults are what the server applies when we omit the parameter. Anything we
want off has to be sent explicitly.

| Parameter | Server default | What we send | Available since |
| --- | --- | --- | --- |
| `with_char_index` | `true` | `false` | 4.1.4 |
| `with_filter_values` | `true` | `false` | 4.6.0 |
| `with_rom_id_index` | `true` | not sent - see section 1 | 5.1.0 |
| `with_total` | `true` | not sent - free while the index is on | 5.1.1 |
| `with_files` | `false` | `true` | 4.9.0 |
| `updated_after` | none | not sent - see section 3 | 4.6.0 |

`GET /api/roms/identifiers` arrived in 4.7.0, below the 4.9.0
`MIN_SUPPORTED_VERSION`, so it needs no capability gate.

## Where the payload actually goes

Measured against the live 23,873-rom library, sampled per platform and weighted
by rom count. Total sync payload is roughly 383 MiB.

| Share | What |
| --- | --- |
| 173 MiB (45%) | `merged_ra_metadata` |
| 37 MiB | `launchbox_metadata` |
| 30 MiB | `ss_metadata` |
| 51 MiB (13%) | the whole `files` list |
| 32 MiB (8%) | the six blobs we parse past, 28 MiB of it `igdb_metadata` |

This reorders every remaining idea.

**`merged_ra_metadata` is the whole game.** Library sync reads exactly one thing
from it - `rom.raMetadata?.achievements?.size`, at `RomMGameMetadata.kt:84`. The
two real consumers, `AchievementDelegate` and `FetchAchievementsUseCase`, both
call `getRom(rommId)` fresh. So 45% of the payload is transferred and
Moshi-parsed into an object per achievement, on a handheld, to compute a list
length. An achievement-count scalar is worth more than everything else in this
file combined. Server-side.

It has to be a scalar, not an opt-out. `achievementCount` has three readers that
run before a game is ever opened, and one of them is
`GameLaunchDelegate.shouldDefaultToHardcore:154` - drop the count and "Default
to Hardcore" silently stops applying to any game the user has not visited. The
array is disposable; the number is not.

On the server side this is also the most expensive blob to produce, not just the
largest. `merged_ra_metadata` is a `@cached_property` (`models/rom.py:706`) that
deep-copies the whole `ra_metadata` document and rewrites `badge_path` and
`badge_path_lock` on every achievement. Nothing gates it, and the raw
`ra_metadata` column is never sent - so a `with_metadata=` list built from the
nine provider blob names would miss the one that matters.

**Trimming the unused blobs is worth 8%**, not the "dominates the wire" the
earlier draft assumed. Still real, still server-side, no longer a priority.

**Moving files out of sync is worth 13%**, and soundtrack rows are only 7% of
the total. Not worth the rework on bandwidth grounds.

**`track_meta` is worth 2.3%** - but it is the entire cause of the per-file N+1,
because it is the lazy relationship the response build walks once per file. On
Nintendo Switch that is 113 file rows per rom, ~11,000 extra queries for a
100-rom page, 9 MiB and 6.2 seconds per page, and a 502 on the following
request. One platform sits at the edge of what the instance will serve.

Two upstream fixes, in order of preference:

1. Eager-load `track_meta` on the file query. Kills the N+1, keeps every field,
   needs no client change at all.
2. `with_track_meta=false`, or make it opt-in under `with_files`. Kills the N+1
   and the bytes, but `RomMGameFileSync` must stop overwriting `trackTitle` /
   `trackNumber` / `durationSeconds` with null when they are absent (`:83-85`,
   which already reads `existing` for `localPath` and siblings and should do the
   same here), and `recoverMusicLocalPath` (`:99-104`) loses its ability to
   re-find a downloaded track on disk after DB loss. The first of those is a
   latent bug today: any response without `track_meta` already wipes the
   columns.

## A `GET /api/roms/sync` projection

`/api/roms` is a gallery endpoint being used as a mirror feed, and every fix
above is a workaround for that mismatch. A dedicated endpoint is worth it only
for the two things flags structurally cannot deliver: a cursor, and deletions in
the stream. Everything else on this page can be revised in place and should be,
because those revisions help the gallery too and are not wasted if the endpoint
never happens.

### What only a new endpoint can do

**Cursor pagination.** `/api/roms` returns `CustomLimitOffsetPage` through
fastapi-pagination's `LimitOffsetParams`. A cursor is a different response
envelope, not another query parameter: adding it in place means either a union
response type every existing consumer handles, or a parallel path inside
`get_roms` that bypasses `resolve_params()` - in a function already carrying
~50 parameters and a cache gate with ~25 conditions that has had one correctness
bug against it. Keyset (`id > last_seen`) is also the real fix for what section
1 measured; it removes the deep-offset walk and the id scan together, rather
than trading one for the other.

**Deletion tombstones.** Absence currently means deleted, or filtered, or
consolidated, or failed, or moved platform. The `decidedRomIds` machinery in
`reconcileOrphans` exists only to guess between those, and `reconcileDeletedRoms`
re-reads the identifier set at sweep time only to close a mid-sync race. A
stream that states deletions removes both. This is what makes `updated_after`
safe enough to build.

### What the projection carries

The test for each field, in order:

1. Does any list surface read it - grid, rails, search, sort, filter,
   collections, launch? Then it stays, regardless of size; a list cannot round
   trip per tile.
2. Is it a key into something cached locally? Then it stays. The asset survives
   offline only if we still hold the identifier to look it up by.
3. Otherwise relocate, and name the call site.

Offline availability is not a fourth question. If the feature a field serves
needs the server, the field's absence offline costs nothing, because the feature
is already gone. File size on the download button is the model case: no server,
no download.

Stays at sync time:

| Field | Why |
| --- | --- |
| identity, `sortTitle`, `searchTitle` | `sortTitle` is the ORDER BY of ~30 `GameDao` queries; `searchTitle` is the entire search predicate |
| `metadatum` genres / game modes | library filter options, and `SyncVirtualCollectionsUseCase` runs inside the pass |
| `metadatum` rating / first release date | library and home sort keys, part of the `GameListItem` grid projection |
| `metadatum` franchises / collections | `GetRelatedGamesUseCase` queries *other* games' columns; a per-game fetch cannot fill the neighbours |
| `ss_metadata` box art paths | `boxSpinePath` / `boxBackPath` render on the library card itself |
| `launchbox_metadata` background and screenshot urls | `backgroundPath` is home hero art; the screenshot cache job selects rows where `screenshotPaths` is non-null, so a null column never backfills |
| `regions`, `youtubeVideoId`, developer, release year | dedup grouping and save-channel naming; home video wallpaper |
| siblings, sibling roms, disc variants | consumed inside the page loop before any row exists - `groupFor`, `isComplete`, `chooseWinner`, the folder-multi-disc skip |
| `files` identity, name, path, category | `game_files` row creation, the folder-multi-disc gate (`isFolderMultiDisc`), and `RomMSyncFilter.extractExtension`, which reads `rom.files` first |

Relocates:

| Field | Where it goes | Notes |
| --- | --- | --- |
| `merged_ra_metadata` array | replaced by an `achievement_count` scalar | not a fetch site; both array consumers already call `getRom` on detail open |
| `files[].track_meta` | `getMusicTracks`, already the music browser's only source | blocked by the null-overwrite bug below |
| `files[].file_size_bytes` | already relocated - `DownloadGameUseCase:93-106` reads a fresh `getRom` | see the two bypass sites below |
| `player_count`, `hltb_metadata` | detail-only; `refreshGameData` already backfills | |
| `age_ratings` | nothing reads it | |
| the six discarded provider blobs | dropped | 8%, 28 MiB of it `igdb_metadata` |
| 20 write-only `GameEntity` columns | dropped | includes `crcHash`, `md5Hash`, `sha1Hash`, `raHash`, `languages`, `alternativeNames`, and every provider id except the ones `getDedupKey` reads off `RomMRom` directly |

That is roughly 55% of the payload, and the two largest items need no new client
fetch site at all.

### Blockers to clear first

**The `track_meta` null-overwrite is live today, at two sites.**
`RomMGameFileSync:83-85` and `RomMLibrarySyncService.syncVersionFiles:1204-1206`
write `trackTitle` / `trackNumber` / `durationSeconds` straight from
`file.trackMeta?...` with no `?: existing?.` fallback, while the same blocks do
consult `existing` for `localPath`, `downloadedAt`, `isMultiDisc` and `m3uPath`.
Any response without `track_meta` wipes those columns for every already-synced
row. Fix this before anything stops sending track metadata.

**Two download paths bypass the fresh fetch.**
`SecondaryHomeViewModel.startDownload:415` does not call `getRom` at all and
takes `expectedSizeBytes = game.fileSizeBytes ?: 0`; `DualScreenManager:1219`
and `FilePickerFlowUseCase:269` pass the synced `game_files.fileSize`. Those go
to zero if the size leaves sync. The dual-screen path already diverges from
`DownloadGameUseCase` on file selection, so this is worth unifying rather than
patching.

**`getRom` has no coalescing.** Detail open already fires up to four independent
calls - achievements, screenshots, download-size backfill, theme resolution. On
a handheld the round-trip count is the cost, not the payload. Relocating more
work onto detail open without coalescing trades bytes for latency at the moment
the user is watching.

**Permission filtering must be identical.** A second read path has to apply the
same hidden-platform and hidden-rom scoping as `/api/roms`. Section 2's deletion
sweep now depends on that scoping being exact: a set that is too narrow reads as
deletions, a set that is too wide is a leak.

### Sequencing

The revisions do not depend on the endpoint and should not wait for it:

1. Eager-load `track_meta`. No client change, stops the 502s.
2. Make `include_file_stats` a parameter. It is hardcoded `True` at one call
   site (`roms/__init__.py:693`) and undefers three correlated scalar subqueries
   per row - `multi_file`, `top_level_file_count`, `has_soundtrack` - so 300 per
   100-rom page. `top_level_file_count` compares concatenated paths, which no
   index can serve, and the comment above the definition already asks for this
   to be revisited. Sync needs none of the three.
3. `achievement_count` on the list schema.

Then the endpoint, if `updated_after` is actually going to be built. Without
it, the cursor and the tombstones have nothing to serve.

## Remaining

Client-only: the `track_meta` null-overwrite fix at both sites, unifying the two
download paths that bypass `getRom`, and `updated_after` per section 3 - still
the only change with an order-of-magnitude effect, and still the one that needs
design rather than a parameter.

Upstream: `track_meta` eager-loading, `include_file_stats` as a parameter, the
`achievement_count` scalar, and then `GET /api/roms/sync`.

Also stale and worth correcting while in the area: `RomMGameFileSync:14-18` and
`RomMUserPropertyService:145` both still say the list endpoint returns no files.
It returns them - they are 13% of the measured payload. Both KDocs predate
`with_files`.

## Verification status

Section 2 is built and compiles, app and test sources both, and has not been run
against a live server: a real deletion round trip has not been reconciled. The
section 1 numbers and the payload table above are live measurements against RomM
5.1.0-alpha.4 with a 23,873-rom library; `with_total` could not be exercised
there because that server predates it.
