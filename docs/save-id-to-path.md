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

`N3dsFolderHandler`, `Ps2FolderHandler`, `PspFolderHandler`, `Ps3FolderHandler`
and `Xbox360FolderHandler` are private classes inside
`data/sync/platform/PlatformSaveHandlerRegistry.kt`, not files of their own.
The other handlers named below each have their own file in the same package.

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

PCSX2 (`FolderMemoryCard::LoadMemoryCardData`) reads the full 0x2000-byte
superblock and indexes the card only when byte 0x16 is `0x6F`; anything shorter,
including an empty file, is an unformatted card whose folders the game never
sees. Every restore door (`extractDownload`, `placeArchive`, and the cache restore in
`SaveCacheManager.restoreSave`, which unpacks prefix-platform archives itself) therefore ends
with the card holding a formatted superblock: an existing formatted one is left
byte-for-byte, and a missing, short or unformatted one is replaced with the
standard 8 MB layout from `pcsx2/Reference/PS2-MemoryCardFileSystem.htm`
(`Ps2FolderCardSuperblock`). A superblock that exists but cannot be read is left
alone. The superblock never travels in an archive.

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

The built-in ARMSX2 core never goes through `Ps2FolderHandler`. It saves to the
two shared file cards `Mcd001.ps2` and `Mcd002.ps2` in
`<libretro system>/pcsx2/memcards/`, and Sigil collects and restores them under
its libretro `pcsx2` layout (`armsx2` aliases to `pcsx2`). The core has no
`pcsx2_shared_memory_cards` option, so the layout's shared default applies.

### PSP (`PspFolderHandler`)

`save_id` is the 9-character save prefix, `FOLDER_PREFIX`. Profile and system
data are separate sibling folders sharing it. It is the disc id (`ULUS10064`)
except for a Store release that keeps its disc release's prefix: the dump reads
`NPJH50804` and the game saves as `ULJS00445`. Two sources give that prefix:

- sigil, from the game's executable when it is readable. The rule is in
  sigil's `docs/platforms/psp.md`; only an `NP…` disc id is ever re-keyed.
- `PspSavePrefixLearner`, when the executable is encrypted. A built-in session
  whose live cache matched nothing adopts the one serial every folder the game
  wrote during that session starts with, unless another game is keyed by it.
  Only the built-in core qualifies: its `SAVEDATA` is Argosy's own and runs one
  game at a time. Standalone PPSSPP's folder is shared, so it is never learned
  from.

Discovery replaces a stored `save_id` that finds nothing with an extracted one
that finds the save, and migration 220 to 221 unlocks `NP…` rows so the next
launch reads them again.

```
<base>/PSP/SAVEDATA/<discId><saveName>
```

The save unit spans every prefix match, so an upload bundles all of them and a
restore deletes all of them before extracting. `findSaveFolderBySaveId`
returns the *parent*, not a match, because there is no single folder. All of
that lives in `PrefixBundleFolderHandler`, shared with PS3; what PSP owns is
the PARAM.SFO test that keeps installed game data out of the bundle.

### Vita (plain `FolderSaveHandler`)

`FOLDER_EXACT`, one directory named exactly for the id:

```
<base>/<save_id>
```

### Wii (`CategorySplitFolderHandler`)

`save_id` is `FOLDER_SPLIT`, the ticket's category and code, `00010000/524d4745`. The base is
the NAND's category directory `Wii/title/00010000`: under the standalone app's files for
`dolphin_wii`, `<builtin saves>/User/Wii/title/00010000` for the built-in Dolphin core
(`argosy_wii`), and `<RetroArch saves>/dolphin-emu/User/Wii/title/00010000` for RetroArch's
Dolphin core (`retroarch_wii`, `retroarch_64_wii`; `saves/User/...` when RetroArch does not sort
saves by core). The libretro core puts its User folder at `<save directory>/User` (libretro/dolphin
`Source/Core/DolphinLibretro/Boot.cpp`); the RetroArch entries have not been checked against a
device yet. The folder is the low id under it, `<base>/524d4745`, with the same id rules
as Wii U below: split, flat and bare low ids address one folder, and another `0001xxxx`
category resolves beside the base. Uploads and restores go through Sigil
(`dolphin_standalone` and the libretro `dolphin` layout); pre-launch discovery and active-cache
placement still find the folder through this handler. Joining the split id whole pointed both
at `00010000/00010000/524d4745`.

### Wii U (`WiiUFolderHandler`)

`WiiUFolderHandler` is the same `CategorySplitFolderHandler` over `0005xxxx` categories, plus
the user-folder archive shape below.

`save_id` is `FOLDER_SPLIT`, the title id split as `00050000/1012bd00`. Cemu's base is
already the category directory, `mlc01/usr/save/00050000`, so only the low half names the
folder:

```
<base>/<low id>
  meta/
  user/<persistent id>/...
  user/common/...
```

The low half is the last eight characters of the id, so the split form, the flat
`000500001012bd00` and the low id alone all resolve to the same folder, in lowercase as
Cemu writes it. The category is the high half of a full id. When it differs from the
category directory the base ends in, the folder is its sibling: a demo id
`00050002/<low id>` resolves to `mlc01/usr/save/00050002/<low id>`. A low id alone carries
no category and stays under the base. Before this handler the split id was joined onto the base whole, which
pointed every restore at `00050000/00050000/1012bd00` and never found the save Cemu
actually wrote.

An archive comes in one of two shapes. Argosy uploads the title folder under its own
name (`1012bd00/meta`, `1012bd00/user`), which matches exactly. Other clients upload the
contents of `user` (`80000001/`, `common/`): those roots name no title, so the archive
is placed on the resolved destination, each root under the title's `user` folder. An archive
with any other root, such as a different title's folder, is refused.

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

The title id in the path is always the application id. A merged NSP or XCI can
report its patch (`application + 0x800`) or add-on content
(`(application & ~0xFFF) + 0x1000 + index`) id instead, per nxdumptool's
`include/core/title.h`, and
the emulator never keeps a save under either. `SwitchTitleIds.baseApplicationId`
maps both back to the application. `TitleIdExtractor` applies it to sigil
output, `TitleDbRepository` to lookup results and cached candidates, and
`SwitchSaveHandler` to every id it searches for, builds a path from, or reads
out of a restore zip. `isValidCachedSavePath` rejects a cached path whose last
segment is a patch or add-on content id, and discovery rewrites a stored row
still holding one.

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

ARMSX3 bases (`com.armsx3` GitHub build, `com.armsx3.play` Play build):

```
{extStorage}/Android/data/com.armsx3/files/config/dev_hdd0/home/00000001/savedata
{extStorage}/Android/data/com.armsx3.play/files/config/dev_hdd0/home/00000001/savedata
```

ARMSX3 also passes user `00000001` (`Rpcs3Bridge.kt` in ARMSX2/ARMSX3), and its
root defaults to `getExternalFilesDir`. A root moved to the SD card or a custom
folder in its onboarding needs the user's save path set to match. Saves are
written to `.working_<dir>` and renamed into place, so dot-prefixed siblings are
transient.

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
its own content root. AX360E has no save path config on purpose. Its layout has
not been read from the app, and a guessed path resolves to a directory the
emulator never writes. `EmulatorRegistry` still routes `xbox360` to `ax360e`
and `ax360e_free`, so AX360E launches games but does not sync their saves.

### Xbox (`XboxSaveHandler`, root only)

`save_id` is the raw hex title id from the XBE certificate, not the `MS-100`
serial printed on the disc. The game writes its save to `E:\UDATA\<save_id>\`.

hakuX 1.x runs its own core and keeps E: as host folders in its private app
storage (`NativeCoreActivity.hddDir()` is `File(filesDir, "native/hdd")`, and
no setting moves it):

```
/data/data/com.rfandango.haku_x/files/native/hdd/Partition1/UDATA/<save_id>/
```

hakuX names the folders in lower case and its core matches them in any case, so
lookup is case-insensitive and a new folder is written in lower case.

Only the vendor root daemon reaches that folder, so the config is
`requiresRoot`. `XboxSaveHandler` copies the title's folder through root into
`cache/xbox-saves/<save_id>` and hands that to the ordinary folder pipeline. It
restages on every read, because hakuX writes the folder without telling us. On
restore it unpacks into the staging folder, then root-copies the tree back over
the title's folder, owned by hakuX's uid, dirs 700 and files 600.

hakuX signs saves with a default HD key every install shares, unless the user
imported their own console's EEPROM, so a save carries between hakuX installs as
is. The 0.3.x disk image (`x1box/hdd.img`, qcow2) is no longer read.
`xbox_hdd.qcow2` is retired firmware (`BiosPathRegistry.isRetiredFirmware`):
firmware sync gives it no row, and `BiosRepository.purgeRetiredFirmware` deletes
any copy or partial download in Argosy's BIOS folders at every startup. The
server's copy is left alone.

### Dreamcast (`DreamcastSaveHandler`)

`save_id` is the disc's IP.BIN product number. Port A1's card is named after it,
with each of ` /\:*?|<>` replaced by `_` (standalone also replaces `"`):

```
built-in flycast core   <save dir>/T13301N.A1.bin
standalone Flycast      <VMU folder>/T13301N_vmu_save_A1.bin
```

The standalone names come from `hostfs::getVmuPath` in Flycast's
`core/oslib/oslib.cpp`. The VMU folder is `Dreamcast.VMUPath` from `emu.cfg`
when it is set, else Flycast's `data` folder. `emu.cfg` sits beside `data`. With
`PerGameVmu` off, every game shares `vmu_save_A1.bin`, and only Sigil's
`flycast_standalone` layout can sort that card's saves by game.
`FlycastStandaloneConfig` reads both settings. `DreamcastSaveHandler.standalone`
names the file when Sigil doesn't route the save.

### RetroArch and the file-based default

Not id-derived at all. The save is named for the ROM, not the title:

```
<base>/<rom name without extension>.srm
```

`RetroArchSaveHandler` resolves `<base>` through the core name and
`retroarch.cfg`, including the `savefiles_in_content_dir` case where the save
sits beside the ROM.

A multi-disc game on a playlist platform (psx, saturn, dreamcast, dc, amiga) is
launched through `<folder>.m3u`, which `M3uManager` writes for whichever discs
are on disk, even one. Emulators name the save after that playlist, so the
game's `localPath` is the playlist and `<rom name>` above is the playlist's name
whichever discs are present. The game's title id is RomM's, read from the first
disc; a local read only ever takes disc 1.

## What wiring the three actually took

The JNI binding is slug-driven with no mirrored enum, so nothing about it
changed. What was NOT free, and is the part worth remembering:

**`TITLE_ID_PLATFORMS` is the gate, not the handler.** A handler and a config
are invisible without it. `PlatformDefinitions.TITLE_ID_PLATFORMS` guards
`TitleIdDownloadObserver` at all three of its entry points, which is what
extracts an id after a download, plus the title-id row on both game-detail
surfaces. Registering a layout for a platform outside that set builds a lookup
nothing ever supplies a key to. A second, smaller set with the same name,
`VariantCategory.TITLE_ID_PLATFORMS` in `data/model/VariantCategory.kt`, only
drives variant and base-ROM grouping. It has no say in save layouts; the
`PlatformDefinitions` set is the gate.

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
`argosy` to this one - and every `state_cache` row was written by the former, so
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

### DuckStation and PPSSPP name states after the disc

Both keep one state folder for every game, so a state belongs to a game only
through the serial in its name. `StateSlotPattern.SerialKeyed` matches on that
serial; the rom file name plays no part. Verified against upstream source
2026-11-12:

| Emulator | Game state | Never a game's state |
|----------|-----------|----------------------|
| DuckStation (`src/core/system.cpp` `GetGameSaveStatePath` 6175, `GetGlobalSaveStatePath` 6183; `PER_GAME_SAVE_STATE_SLOTS = 10` in `system.h`) | `<serial>_<slot>.sav` (slots 1..10), `<serial>_resume.sav` (the auto slot) | `savestate_<n>.sav`, `resume.sav` |
| PPSSPP (`Core/SaveState.cpp` 80-87 and 688-710, `SaveState.h:46`) | `<DISC_ID>_<DISC_VERSION>_<slot>.ppst`, screenshot `<same stem>.jpg` | `<...>_<slot>.undo.ppst`, `<...>_<slot>.undo.jpg`, `<...>_<slot>.name.txt`, `load_undo.ppst` |

DuckStation's serial is its GameDatabase entry serial when it knows the disc,
else the boot id (`SLUS-12345`); EXE paths get a hash id. 867 database entries
map several disc codes to one serial (Sigil `docs/platforms/psx.md:80`), so a
game whose database serial differs from its disc code matches nothing and is
left alone. PPSSPP's slot count is the `iSaveStateSlotCount` setting, so no
maximum is assumed, and its `DISC_VERSION` is only ever taken from a name PPSSPP
already wrote: a cached copy without one is not placed.

The serial set comes from `StateSerialResolver.serialsFor`, and only from Sigil
reading the local disc on every call:

1. `TitleIdExtractor.extractTitleIdWithSource` on the rom, used only when
   `fromBinary`; the result is stored with the lock when it differs from the
   stored value (an existing `saveId` is kept);
2. for a game with `game_discs` rows, one binary read per downloaded disc;
3. anything else (filename fallback, unreadable, no rom) is the empty set, which
   matches no file, so nothing is cached, displaced, restored or repaired.

No stored `titleId` stands in for the read, locked or not: `titleIdLocked` only
means RomM may not overwrite the value, and `PspSavePrefixLearner` sets it on a
title id that never came from the disc.

Sigil's psx `title_id` is the normalised boot serial and its psp `title_id` is the
disc id without a dash (`ULUS10064`), never `save_id`. RomM's `title_id` is never
used.

A cached state whose name carries another serial is detached from the game
(`StateCacheDao.detachFromGame` negates its `gameId`; row and file are kept) and
its server copy is tombstoned for the game. A cached copy whose name carries no
serial, which is every copy downloaded from RomM, is placed by `provenStateKey`
only when the serial in its own DuckStation header (`src/core/save_state_version.h`
`SAVE_STATE_HEADER`: magic `0x43435544` at 0, `serial[32]` at 136) is one of the
game's, and then under `<that serial>_<slot>.sav`; a foreign or unreadable header
is not placed. PPSSPP copies without a `DISC_VERSION` in their name are not
placed.

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
to sibling-family logic: `EmulatorRegistry.familyBaseIdFor("dolphin_wii")` resolves to
`dolphin`, which is how the GameCube override became the Wii save base
(`EmulatorSaveConfigRepository.resolveUserSavePath` guards against it). The private
`SavePathRegistry.familyBaseIdFor` is a different function: it answers null for any id
that is not a strictly longer family variant, and only feeds the registry's own config
fallback.

Two key conventions are live in the registry today and have not been reconciled:
the built-in GameCube entry is keyed by the canonical slug while `retroarch_ngc` is
keyed by the raw one. `SavePathRegistry` builds the built-in key as
`"${BUILTIN_EMULATOR_ID}_gc"`, which is `argosy_gc` because `BUILTIN_EMULATOR_ID` is
`EmulatorRegistry.BUILTIN_ID`. `getCanonicalSlug("ngc")` is `gc`, so `retroarch_ngc` is reachable only where
RomM's raw slug survives. Settle this before adding entries.

## Known limitation: one save folder for several roms

Every rule above maps an id to a path. Two roms that yield the same id resolve to the same
folder, and Argosy does not separate them on the device. That happens in two cases:

- Regional copies that share one id. A Switch title id often covers every region, so a USA and a
  Europe copy on the same emulator read and write one folder.
- A hack that keeps its base game's serial or header id. A PSP hack built on a retail ISO keeps
  the disc id, and a GameCube hack keeps the 6-character game id, so the hack and its base share
  one folder (one GCI set for GameCube).

The server side stays separate. An upload goes to the `rommId` of the game row that was played,
so each rom keeps its own saves in RomM. Only the local folder is shared. Playing one copy and
then the other starts the second copy on whatever the first left on disk, and an upload from the
second copy can send that save under the second rom's id.

Saves named after the rom file (RetroArch, the built-in `.srm` layout, and SeedlessDS `.dsv`
beside the rom) do not share, because regional copies and hacks have different file names. That
holds only while discovery matches by rom name. A folder that holds several copies' saves is
never searched by game title, since a title such as `Mario Kart DS` is a prefix of every regional
file name:

- the rom's own folder, whether the emulator saves beside the rom (`savesBesideRom`) or a
  per-game save folder points at it;
- a per-game save folder that another game's per-game config also names, which is what the
  one-time sibling config carry-over produced before it stopped copying `savePath` and
  `selectedMemcardPath`.

In those folders a rom-name miss means no save. The carry-over now copies launcher fields only.
Argosy leaves serial-keyed folders shared.

## Why this matters when changing anything

An archive is accepted for a save only if its root entry matches the id under
one of the tiers in `FolderSaveHandler.matchArchiveRoot` - exact, prefix,
contains, or the per-platform unidentified root. Changing what a handler
zips changes what every previously uploaded archive matches against, so it is
not a local change: existing server-side saves were written under the old
shape.
