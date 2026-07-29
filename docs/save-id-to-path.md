# How a save id becomes a save path

sigil reads a ROM and returns a `title_id`, a `save_id`, and a `usage` saying
how the platform spends that id on disk. Argosy turns the id into a real
directory or file. This file records the second half of that trip, per
platform, so the derivation is not something you have to reconstruct by
reading five handlers.

Everything below is the mapping from `save_id` to a path *below a base
directory*. The base itself comes from `SavePathRegistry` defaults or a user
override; see `FolderSaveHandler.resolveBasePath`.

## The four usages

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
falls back to `00040000` — correct for retail applications, a guess for
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
which identifies nothing — the game's folder is one level below it. Archives
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
returns the *parent*, not a match, because there is no single folder.

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

### RetroArch and the file-based default

Not id-derived at all. The save is named for the ROM, not the title:

```
<base>/<rom name without extension>.srm
```

`RetroArchSaveHandler` resolves `<base>` through the core name and
`retroarch.cfg`, including the `savefiles_in_content_dir` case where the save
sits beside the ROM.

## Why this matters when changing anything

An archive is accepted for a save only if its root entry matches the id under
one of the tiers in `FolderSaveHandler.matchArchiveRoot` — exact, prefix,
contains, or the per-platform unidentified root. Changing what a handler
zips changes what every previously uploaded archive matches against, so it is
not a local change: existing server-side saves were written under the old
shape.
