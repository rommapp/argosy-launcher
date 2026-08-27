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
title id it derives from stays flat in `title_id`.

```
<base>/<id0>/<id1>/title/00040000/00033500/data
                         ^^^^^^^^^^^^^^^^^
                         save_id, used as-is
```

`<base>` is the `Nintendo 3DS` directory under the emulator's `sdmc`.
`normalizeBasePath` accepts any level of that tree and settles on the
`Nintendo 3DS` folder. `id0`/`id1` are the emulator's own 32-hex directories
and are discovered, never constructed.

`FOLDER_SPLIT` flags that the id is nested rather than one flat folder name;
the split itself arrives in the value, the same way PS2 reports the
region-prefixed `BASLUS-20565` rather than the disc's `SLUS-20565`. A save id
goes to the resolver unmodified on every platform, so a nested one needs no
special derivation.

Argosy's handler still reads the halves with `take(8)`/`takeLast(8)`, which
lands on the same two segments whether or not the separator is present, so a
row cached before the split arrived resolves identically to a fresh one.

A `save_id` shorter than 16 characters has no category in it, and the handler
falls back to `00040000` - correct for retail applications, a guess for
anything else.

The save unit is the `data` directory, so an archive's root entry is `data`
and carries no title. That is the `ArchiveRootMatch.UNIDENTIFIED` tier: the
resolved destination is the only thing identifying such an archive.

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

### Xbox (no path exists)

The one platform where `save_id` identifies without locating. Saves are
written to `E:\UDATA\<save_id>\` inside a FATX filesystem, inside the `.qcow2`
or `.img` hard-disk image the emulator boots. There is no host directory to
resolve, and hakuX takes that image from a user file picker rather than placing
it anywhere fixed, so there is not even a stable image path to look in. X1 BOX
and desktop xemu have the same property for the same reason.

`hakux` is registered with `supported = false` and an EMPTY `defaultPaths`, and
the emptiness is the point. `getConfig` and `SavePathAuthority.configFor` both
drop an unsupported config, so nothing offers the user a save folder, and no
plausible-looking path is left in the registry for a later reader to trust.
Reading one would mean a FATX parser over a qcow2, and hakuX's own FATX code
only imports a dashboard, it exports nothing.

sigil still returns a real `save_id` here, and `xbox` is in
`PlatformDefinitions.TITLE_ID_PLATFORMS` so that it gets extracted and stored:
it matches the game to its upstream record even when no file can be reached.
Membership in that set is about extraction, not about a syncable layout.

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
