# Switch mod packages

The Mods menu installs downloaded ZIP files categorized as `mod` by RomM into
the Eden emulator selected for the game. Download a package through Files first.
Each package must match the game's title ID and installed game version.

Supported ZIP paths:

```
load/<16-digit-title-id>/<mod-name>/exefs/...
load/<16-digit-title-id>/<mod-name>/romfs/...
load/<16-digit-title-id>/<mod-name>/cheats/...
sdmc/UltraCam/TOTK/Config/<file>.ini
sdmc/UltraCam/TOTK/Config/<file>.json
```

UltraCam auxiliary files are title-bound: `TOTK` belongs to
`0100F2C0115B6000`; `BOTW` belongs to `01007EF00011E000`. The same layout is
supported for BOTW's Config folder. Other games can use the `load` layout.
Game-version compatibility remains a user check; a matching title ID alone does
not prove a mod is compatible.

The installer skips README files, LICENSE, macOS metadata, and a matching
`config/custom/<title-id>.ini`. It reports skipped desktop emulator settings.
It rejects unknown paths, traversal, symbolic links, special files, conflicting
destinations, and archives exceeding 10,000 entries, 256 MiB per file or 1 GiB
total. The shared path examples in `app/src/test/resources/switch-mod-paths.tsv`
are also used by Ludo's installer.

TOTK UltraCam packages containing
`sdmc/UltraCam/TOTK/Config/maxlastbreath.ini` offer original settings or an
explicit handheld profile: 16:9, 720p, lower shadow resolution, and dynamic FPS
with a 60 FPS ceiling. The profile changes existing documented keys only; it
rejects missing or duplicate keys. A frame-rate ceiling is not a performance
guarantee. Other config fields and Eden settings remain unchanged.

Close Eden and acknowledge game-version compatibility before installing,
updating, removing or restoring. Android does not provide a reliable universal
way for Argosy to prove another emulator process has stopped. Argosy does not
claim to force-stop Eden.

The installer stages payloads privately and records before-images and hashes
before writing emulator files through FileAccessLayer. A failed operation rolls
back files whose mutations started. A surviving journal blocks further installs
until recovery. Recovery offers an explicit restoration of the recorded files;
later changes to those files are replaced. Identical preexisting files remain
unowned and survive removal. Differing unowned files, changed managed files and
paths claimed by another package are preserved and reported as conflicts.
Argosy blocks Switch launches and background resumes while mod changes are in
progress or an interrupted installation needs recovery.

Current Android support uses Eden's default data, load and SDMC locations.
Nondefault load/SDMC overrides are rejected. The receipt store is local to Argosy;
it is not transferred by RomM save sync. The official application and its data
remain separate from the debug application's package and receipt store.

Emulator layout references:
[Eden's mod documentation](https://github.com/UzuCore/eden/blob/dev/docs/user/Mods.md)
and [NX Optimizer setup](https://nxoptimizer.com/guides/setup-guide/).
