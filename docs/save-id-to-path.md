# How a save id becomes a save path

sigil reads a ROM and returns a `title_id`, a `save_id`, and a `usage` saying
how the platform spends that id on disk. Argosy turns the id into a real
directory or file. This file records the second half of that trip, per
platform, so the derivation is not something you have to reconstruct by
reading five handlers.

Everything below is the mapping from `save_id` to a path *below a base
directory*. The base itself comes from `SavePathRegistry` defaults or a user
override; see `FolderSaveHandler.resolveBasePath`.

## The five usages

`usage` is the contract between sigil and the handler, and getting it wrong
silently mismatches saves rather than failing loudly.

| Usage | Meaning | Match rule |
| --- | --- | --- |
| `FOLDER_EXACT` | the id names one directory | case-insensitive equality |
| `FOLDER_PREFIX` | the id is a stem several directories share | `startsWith` |
| `FILE_EXACT` | the id names one file | equality on the stem |
| `FILE_PREFIX` | the id is a stem several files share | `startsWith` |
| `FOLDER_SPLIT` | the id names one directory, nested as equal-length path segments rather than a flat name | equality per segment |

## Per platform

### 3DS (`N3dsFolderHandler`)

`save_id` is the on-disk location, already split: `00040000/00033500`. The
title id it derives from stays flat in `title_id`. One title occupies two
directories under the same `id1` root, and the save unit is both of them:

```
<base>/<id0>/<id1>/title/00040000/00113200/data
                         ^^^^^^^^^^^^^^^^^
                         save_id, used as-is
<base>/<id0>/<id1>/extdata/00000000/00001132
                                    ^^^^^^^^
                                    extdata id, derived from the low half
```

`<base>` is the `Nintendo 3DS` directory under the emulator's `sdmc`.
`normalizeBasePath` accepts any level of that tree and settles on the
`Nintendo 3DS` folder. `id0`/`id1` are the emulator's own 32-hex directories
and are discovered, never constructed.

The extdata id rule for retail titles: take the low 8 hex of the title id,
shift it right by 8 bits, render 8 lowercase hex digits. `00113200` becomes
`00001132`, `00175e00` becomes `0000175e`. `N3dsFolderHandler.extdataIdFor`
is the one place that encodes it. Some titles keep every byte of progress in
extdata and never create `data` (Fantasy Life writes
`extdata/00000000/00001132/user/fl_ext0.fsd` and nothing under
`title/00040000/00113200`), which is why either directory alone counts as a
save and `NoSaveFound` means neither exists.

`FOLDER_SPLIT` flags that the id is nested rather than one flat folder name;
the split itself arrives in the value, the same way PS2 reports the
region-prefixed `BASLUS-20565` rather than the disc's `SLUS-20565`. A save id
goes to the resolver unmodified on every platform, so a nested one needs no
special derivation.

Argosy's handler reads the halves off the flattened id with `take(8)` and
`takeLast(8)`, which lands on the same two segments whether or not the
separator is present, so a row cached before the split arrived resolves
identically to a fresh one. The category half is only used to *construct* a
path. Discovery ignores it and matches the low half under every category
directory in the tree, then prefers the base category `00040000` over an
update (`0004000e`) or DLC (`0004008c`) tree carrying the same low id, then
the id's own category, then whichever was written most recently. Before this
rule it took the newest `data` across all categories, which let an update
tree shadow the real save.

A `save_id` shorter than 16 characters has no category in it, and the handler
falls back to `00040000` - correct for retail applications, a guess for
anything else.

The resolved local path is the title `data` directory whenever it exists, so
every sync row written before extdata joined the unit keeps its path, and the
extdata directory only when there is no `data`. Either component identifies
the unit: `sourcePathsFor`, `namedArchiveRoots` and `findAllSaveFoldersBySaveId`
read the `id1` root back out of whichever path they are handed and answer with
every component that exists.

Archive shape: root `data` for the title directory, unchanged, so every
archive already uploaded still matches; plus root `extdata` for the extdata
directory. Neither root names a title, so both sit in
`unidentifiedArchiveRoots` and match on the `ArchiveRootMatch.UNIDENTIFIED`
tier: the resolved destination is the only thing identifying such an archive.
A restore places `data` into `title/<category>/<low>/data` and `extdata` into
`extdata/00000000/<extid>` whichever component the target path names, creating
whichever the archive carries, and refuses an archive with a root it cannot
place. A legacy single-root `data` archive restores exactly as it always has.

A restore never deletes a component the archive does not carry. Placement
(`N3dsFolderHandler.unpackArchive`) replaces each component whose root is in
the archive and leaves the other alone, so a legacy `data`-only archive
restored over a title with extdata on disk keeps that extdata. The clear that
precedes a restore (`SaveSyncApiClient.clearSavesBeforeRestore`) asks the
handler `pathsClearedBeforeRestore` with the archive's root names: 3DS keeps
every component absent from the archive and, when the archive has not been
read yet (a server restore), clears nothing and lets placement do it. PSP and
PS2 clear every match either way, as they always have. `clearSavesForTitle`
without an archive remains the full clear used by "clear active save" and the
account-switch teardown, which has already verified the archive holds the
whole unit.
Every door that zips or unpacks a 3DS unit asks the handler for its named
roots rather than the folder's own name: upload, the local cache, the cache
restore, the hardcore downgrade and `downloadSaveById` all go through
`FolderSaveHandler.namedArchiveRoots` / `placeArchive`. `N3dsSaveCaseRepair`
reconciles the title tree only; extdata is written lowercase by both sides and
has never split on case.

### PS2 (`Ps2FolderHandler`)

`save_id` is a stem with `FOLDER_PREFIX`: a region prefix followed by the disc
serial, e.g. `BASLUS-20152`. A game owns *every* card entry starting with it
(`BASLUS-20152AC04`, `BASLUS-20152SYS`, ...), so the save unit is the card,
not one folder.

```
<base>/<card>.ps2/<stem><per-artifact suffix>
```

A card is a directory ending `.ps2` or holding a `_pcsx2_superblock`.
Comparison is on a normalized form with `-` and `_` stripped, uppercased.

The region prefix comes from the third character of the four-letter serial
code, and this rule exists in two places that must agree:

| 3rd char | Prefix | Source |
| --- | --- | --- |
| `E` | `BE` | `ps2_region_prefix` (sigil/src/ps2.c) |
| `P`, `J`, `K` | `BI` | `Ps2FolderHandler.territoryPrefixFor` |
| anything else | `BA` | both |

Argosy re-derives it only as a fallback, for ids extracted before sigil
emitted a stem. `withoutRegionPrefix` drops it entirely as a last resort,
because a wrong region prefix still leaves the disc identifiable.

Nothing fabricates an entry name. If a game has no folder on the card yet, a
restore lands in the card and the archive's own entry names create it.

Because the save unit is the card, an upload is rooted at the card's own name,
which identifies nothing - the game's folder is one level below it. Archives
written before the save unit became the card are rooted at that folder
directly, and both shapes are on servers. `Ps2FolderHandler.matchArchive`
therefore looks at folder names at any depth, and `unpackArchive` strips the
archive's root only when that root is the card. Stripping a game-folder-rooted
archive would empty its contents loose into the card, which no emulator reads;
a card left in that state by an older build is repaired on the next restore.

### PSP (`PspFolderHandler`)

`save_id` is the 9-character disc id (`ULUS10064`), `FOLDER_PREFIX`. Profile
and system data are separate sibling folders sharing it.

```
<base>/PSP/SAVEDATA/<discId><saveName>
```

The save unit spans every prefix match, so an upload bundles all of them and a
restore deletes all of them before extracting. `findSaveFolderBySaveId`
returns the *parent*, not a match, because there is no single folder. All of
that lives in `PrefixBundleFolderHandler`, shared with PS3; what PSP owns is
the PARAM.SFO test that keeps installed game data out of the bundle.

### Vita, Wii, Wii U (plain `FolderSaveHandler`)

`FOLDER_EXACT`, one directory named exactly for the id:

```
<base>/<save_id>
```

### GameCube (`GciSaveHandler`)

Not id-derived from sigil. The 6-character game id is parsed from the ROM
header, and GCI files are matched either by the id appearing in the filename
or by parsing the id out of the GCI header itself. Several `.gci` files per
game, so the unit is the matched set.

### Switch (`SwitchSaveHandler`)

`FOLDER_EXACT` on a 16-hex title id beginning `01`, nested under the
emulator's user and profile directories:

```
<base>/save/<user 16 hex>/<profile 16 or 32 hex>/<titleId>
```

Both intermediate levels are discovered. `isValidCachedSavePath` re-checks the
shape before trusting a cached path.

### PS3 (`Ps3FolderHandler`)

`save_id` is the 9-character title id (`BCUS99086`) read from `PARAM.SFO`,
`FOLDER_PREFIX`. The game appends a per-artifact suffix, so this is PSP's
shape rather than Vita's:

```
<base>/<titleId><suffix>          BCUS99086GAMEDATA, BCUS99086-AUTOSAVE
```

`FolderSaveHandler.folderMatches` defaults to case-insensitive equality, which
would match only a folder named exactly `BCUS99086` and miss every real save.
PSP and PS3 both sit on `PrefixBundleFolderHandler` instead, which carries the
whole shape: prefix matching, the parent as the resolved path, every matched
sibling bundled on upload, and those same siblings cleared before a restore
unpacks back into the parent. PS3 adds nothing to it. PSP adds one thing, the
PARAM.SFO test that keeps installed game data out of the bundle.

aPS3e base:

```
{extStorage}/Android/data/aenu.aps3e/files/aps3e/config/dev_hdd0/home/00000001/savedata
```

The user segment is hardcoded to `00000001` in aPS3e, so the config names it.
Desktop RPCS3 can hold several, so a desktop path added later has to discover
that level rather than inherit this constant.

### Xbox 360 (`Xbox360FolderHandler`)

`save_id` is 8-hex uppercase (`4D5307DC`), `FOLDER_EXACT`, but it is *not* the
first segment. XenDroid keys content by profile before title:

```
<base>/<XUID 16 hex>/<save_id>/00000001/<package>/
        ^^^^^^^^^^^^            ^^^^^^^^
        discovered              saved-game content type
```

The model is `N3dsFolderHandler`, not `SwitchSaveHandler`: a `FolderSaveHandler`
subclass that overrides `findSaveFolderBySaveId` to walk a discovered level and
`constructSavePath` to pick one, which is the same problem 3DS solves for id0
and id1. Nothing about Switch's profile parsing or JKSV format applies here.

Three traps, each of which produces a silent miss rather than an error:

- The XUID level is enumerated, never constructed. It names the signed-in
  profile, so `constructSavePath` answers null when no profile directory exists
  rather than inventing one.
- `00000001` is the saved-game content type. `00000002` is DLC and `000B0000`
  is title updates, so matching on the title id alone picks up add-on content
  and syncs it as a save. The save unit therefore stops at the content-type
  directory, not at the title.
- Non-profile content sits under the machine XUID `0000000000000000`. A tree
  containing only that XUID has no saves in it, so finding the title id there
  is not a hit.

A consequence of the second trap: the archive is rooted at `00000001`, a name
every title on the console shares, so it goes in `unidentifiedArchiveRoots` and
the resolved destination is what places it. Same reason 3DS lists `data`.

`isValidCachedSavePath` checks the `<16 hex>/<8 hex>/00000001` tail before a
cached row is trusted, because deleting the profile that XUID names leaves a
path that still exists on disk and still holds files, pointing at somewhere
XenDroid no longer reads.

XenDroid base:

```
{extStorage}/Android/data/xendroid.compose/files/compose/content
```

The layout is Xenia's `ResolvePackageRoot()`, so desktop Xenia matches below
its own content root. AX360E is deliberately unregistered: its layout has not
been read from the app, and a guessed path resolves to a directory the emulator
never writes.

### Xbox (the path names an image, not a folder)

The one platform whose save is not a host file. Saves are written to
`E:\UDATA\<save_id>\` inside a FATX filesystem inside the disk image the
emulator boots, and `save_id` here is the raw hex title id the console names
the directory after, not the `MS-100` serial printed on the disc.

`hakux`'s `defaultPaths` names the directory holding that image, not the saves:

```
{extStorage}/Android/data/com.rfandango.haku_x/files/x1box
```

hakuX ingests the flash ROM, MCPX and hard disk into that directory during
setup, renaming them to `flash.bin`, `mcpx.bin` and `hdd.img`, and boots from
its own copies. The file is qcow2 despite the `.img` name, so the reader checks
the `QFI\xfb` magic rather than trusting the extension.

Two layers sit between the path and the save. `Qcow2Image` maps guest offsets
through the L1 and L2 tables, and `FatxVolume` reads the filesystem inside. The
E: partition begins at sector `0x55F400` on every Xbox-shaped image and runs to
the end of the disk, which is upstream-exact and must not be recomputed.

`XboxSaveHandler` stages a save out of the image into the app cache and hands
that directory to the ordinary folder pipeline, then writes the staged tree back
in on restore. Nothing downstream of the handler knows a container is involved.
Staging is rebuilt from the image on every read, because the emulator writes the
image without telling us and a cached copy would upload a stale save.

Writing is in place only. A file must already exist in the image at the same
size, so a save can be restored over itself but one the emulator has never
written cannot be introduced. Growing a file or creating one needs cluster
allocation, which means updating the qcow2 refcount tables, and a mistake there
damages an image the user cannot rebuild.

The image is also registered `writeOnce` in `BiosPathRegistry`, so BIOS
redistribution never copies a fresh one over it. Every other firmware entry is a
static blob where overwriting is harmless; this one is a volume the user's saves
live inside.

### RetroArch and the file-based default

Not id-derived at all. The save is named for the ROM, not the title:

```
<base>/<rom name without extension>.srm
```

`RetroArchSaveHandler` resolves `<base>` through the core name and
`retroarch.cfg`, including the `savefiles_in_content_dir` case where the save
sits beside the ROM.

## What wiring the three actually took

The JNI binding is slug-driven with no mirrored enum, so nothing about it
changed. What was NOT free, and is the part worth remembering:

**`TITLE_ID_PLATFORMS` is the gate, not the handler.** A handler and a config
are invisible without it. `PlatformDefinitions.TITLE_ID_PLATFORMS` guards
`TitleIdDownloadObserver` at all three of its entry points, which is what
extracts an id after a download, plus the title-id row on both game-detail
surfaces. Registering a layout for a platform outside that set builds a lookup
nothing ever supplies a key to.

**The config ids stayed bare.** `aps3e` and `xendroid`, not `aps3e_ps3` and
`xendroid_xbox360`. Platform-qualifying a single-platform emulator here would
have broken it: `getConfig`, `getConfigByPackage` and `canSyncWithSettings`
answer for an emulator alone and look up the bare key, so a platform-only entry
makes save sync unreachable for that emulator entirely. The qualified form
exists for emulators that serve more than one platform, which is why `dolphin`
keeps the bare key for GameCube and only Wii gets `dolphin_wii`. Adding a
second platform to either emulator later means adding the qualified entry then,
which the `getConfigForPlatform` fallback chain already handles.

**`isValidCachedSavePath` moved onto `PlatformSaveHandler`.** It was a Switch
method reached through `if (platformSlug == "switch")` in `SaveUploader` and
`SaveSyncConflictResolver`. Both now ask
`PlatformSaveHandlerRegistry.isValidCachedSavePath(platformSlug, path)`, and
the two platforms that nest a save under a per-install identifier answer it.
Every other platform inherits true, where existence on disk is the whole
question.

Nothing here has been tested against a real save. The paths were read out of
each emulator's source, not observed on device, so PS3 is the place to confirm
the approach before trusting the other two. The save-zone rule applies in full:
GET /api/saves and negotiate no_op twice before calling any of it working.

PSP is the regression risk in this change, not the new platforms. Extracting
`PrefixBundleFolderHandler` moved PSP's bundling, discovery and restore into a
shared base without changing them, so a PSP round trip is what proves the
extraction was clean.

## Save states are a peer subsystem with its own rule

Everything above is about saves. States resolve through `StatePathRegistry` and,
for RetroArch, `RetroArchConfigParser` - not through the save handlers - and they
have a directory rule saves do not:

```
<states base>/[<content dir>/]<core dir>/<rom name without extension>.state<N>
```

The `<core dir>` segment is what `sort_savestates_enable` produces. Read a
`retroarch.cfg` that omits the key and it is treated as on, where the matching
`sort_savefiles_enable` is treated as off. When the cfg cannot be read at all
neither default applies: `resolveStatePathsWithConfig` probes the disk for an
existing core folder and writes flat if it finds none, which is the case where a
misplaced state actually happens.

The consequence is that a state cannot be written without knowing the core, and
a restore that resolves none writes to the parent directory, where the emulator
will never look for it. The failure is silent, not an error.

**States resolve their core through `CoreVersionExtractor.getCoreIdForEmulator`,
not the save side's `resolveCoreForGame`.** The two genuinely disagree - the
built-in emulator is its libretro core to the save resolver and the literal
`builtin` to this one - and every `state_cache` row was written by the former, so
validating or pathing a state against the latter compares a row to a value that
never wrote it. `RestoreStateUseCase` falls back to the cached row's own core
when a caller supplies none, so a new restore entry point must either pass the
`CoreVersionExtractor` value or inherit that fallback.

The on-disk core folder is the libretro `corename`, not the core id
(`EmulatorRegistry.getRetroArchSaveDirName`). Cores whose folder differs from
their id by case alone are deliberately absent from that map; `matchExistingFolder`
only rescues a folder that already exists, so the first write into a fresh tree
uses the raw id and is harmless solely because Android's storage is
case-insensitive. Do not "complete" that map.

## Which config answers, and under which key

A save layout is chosen by `(emulator, platform)`, never by emulator alone. The
registry expresses this by keying platform variants as `<id>_<slug>`, so Dolphin has
`dolphin` for GameCube and `dolphin_wii` for Wii, and RetroArch has `retroarch_ngc`,
`retroarch_psp` and `retroarch_3ds` beside the generic entry.

Two consequences that have each already caused a bug:

- **Resolve with the platform.** `getConfig` and `getConfigByPackage` answer for an
  emulator, so a multi-platform emulator gets whichever layout is listed first. That
  is how the Wii platform row came to display GameCube's path (#380). Ask
  `SavePathAuthority`, which takes the platform slug as a required argument. A CI rule
  (`scripts/ci/smell-rules.json`, `platform-blind-save-config`) refuses new direct
  calls.
- **The config id is the override key.** A user's custom save path is stored in
  `emulator_save_config` under `config.emulatorId`, so a shared config id is a shared
  row, and GameCube and Wii overwrote each other. Reads and writes must derive it the
  same way; `SavePathAuthority.configIdFor` is that derivation.

A platform-qualified config id names a layout, not an installed app. Do not hand one
to sibling-family logic: `familyBaseIdFor("dolphin_wii")` resolves to `dolphin`, which
is how the GameCube override became the Wii save base.

Two key conventions are live in the registry today and have not been reconciled:
`builtin_gc` is keyed by the canonical slug while `retroarch_ngc` is keyed by the raw
one. `getCanonicalSlug("ngc")` is `gc`, so `retroarch_ngc` is reachable only where
RomM's raw slug survives. Settle this before adding entries.

## Why this matters when changing anything

An archive is accepted for a save only if its root entry matches the id under
one of the tiers in `FolderSaveHandler.matchArchiveRoot` - exact, prefix,
contains, or the per-platform unidentified root. Changing what a handler
zips changes what every previously uploaded archive matches against, so it is
not a local change: existing server-side saves were written under the old
shape.
