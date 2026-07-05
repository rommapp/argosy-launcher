# F-Droid submission — handoff notes

This directory holds the **F-Droid build recipe** for Argosy Launcher and the notes
needed to submit it. Nothing here ships in the app; it is packaging material.

- `com.nendo.argosy.yml` — the build-metadata recipe. It belongs in the
  [`fdroiddata`](https://gitlab.com/fdroid/fdroiddata) repo at
  `metadata/com.nendo.argosy.yml`, **not** in this app repo. It lives here only as a
  reviewed, ready-to-copy artifact.

The media metadata (descriptions, icon, feature graphic, screenshots) already lives in
this repo under `fastlane/metadata/android/en-US/` and F-Droid pulls it automatically.

## App facts the recipe encodes

| Field | Value | Source |
|-------|-------|--------|
| Application ID | `com.nendo.argosy` | `app/build.gradle.kts` |
| versionName / base versionCode | `1.18.0` / `298` | `app/build.gradle.kts` |
| License | GPL-3.0 (`LICENSE`) | verify `-only` vs `-or-later`, see below |
| minSdk / targetSdk | 26 / 35 | `app/build.gradle.kts` |
| Release tag | `v1.18.0` | `git tag` |
| Submodules | `sigil`, `libretrodroid/.../rcheevos` | `.gitmodules` |

### Why the versionCodes are `1000298` / `2000298`, not `298`

`app/build.gradle.kts` overrides every APK's versionCode to
`abiCode * 1_000_000 + baseVersionCode` (`applicationVariants.all { ... }`), with
abiCodes `armeabi-v7a=1`, `arm64-v8a=2`, universal=3. So the published APKs are:

- armeabi-v7a → **1000298**
- arm64-v8a → **2000298**
- universal → 3000298 (intentionally **not** published — a higher code would always
  win over the per-ABI splits and defeat the split)

The recipe therefore builds with `-PallAbis` (the flag that enables
`splits { abi }`), ships the two per-ABI blocks, and uses `VercodeOperation`
(`%c + 1000000`, `%c + 2000000`) so autoupdate regenerates both blocks from each new
`v*` tag. `UpdateCheckMode: Tags ^v[0-9.]+$` matches stable tags only and skips the
`-beta` tags.

The changelogs under `fastlane/.../changelogs/` are provided for `1000298`, `2000298`,
`3000298`, and `298` so the "What's New" entry shows regardless of which code a client
sees.

## Known inclusion blockers — resolve before the build will pass

F-Droid clones the source, runs its **scanner**, then builds in isolation. These will
trip it up:

1. **Prebuilt JARs in `libs/maven/` (hard blocker).**
   `libs/maven/io/github/joshuatam/javasteam*/**.jar` are precompiled binaries committed
   to the repo (force-included via `.gitignore: !libs/maven/**/*.jar`) and consumed
   through the in-tree maven repo declared in `settings.gradle.kts`. The scanner rejects
   committed binaries **regardless of license** (JavaSteam is MIT). They cannot be
   `scandelete`d because the Steam feature won't compile without them. Options, best first:
   - Publish JavaSteam (and `javasteam-depotdownloader`) to a real maven repo (Maven
     Central / JitPack) and depend on it normally — removes the binaries entirely.
   - Build JavaSteam from source as part of the F-Droid build.
   - Add a build flavor that excludes the Steam feature, and package that flavor.
   - Last resort: `scanignore: [libs/maven]` in the recipe — keeps the binaries but
     reviewers commonly reject this. Not included in the recipe by default.

2. **`sigil` submodule is a separate repo** (`github.com/rommforge/argosy-sigil`,
   Rust title-ID extraction). Confirm its license is FOSS and F-Droid-compatible, and
   whether the F-Droid build VM needs a Rust toolchain set up (via `sudo`/`rm` build
   steps). It builds no committed `.so`, so it must compile at build time.

3. **Network dependency on `api.argosy.dev`** — `TITLEDB_API_URL` and `SOCIAL_API_URL`
   are hardcoded to the developer's proprietary backend (`app/build.gradle.kts`). The
   core RomM sync is self-hosted, but if titledb/social features depend on this
   closed service, reviewers may ask for a **`NonFreeNet`** AntiFeature. Decide and, if
   needed, add `AntiFeatures: [NonFreeNet]` with a description.

4. **Discord SDK** — an optional local AAR (`app/libs/*.aar`) that is gitignored and
   absent from the repo, so the default build excludes it (`DISCORD_SDK_ENABLED=false`).
   No action needed **as long as** it is never committed. If it is bundled, it becomes a
   `NonFreeDep`/`NonFreeNet` concern.

5. **License precision** — `LICENSE` is the full GPLv3 text and the README says
   "GNU General Public License v3.0". Source files carry no per-file headers, so confirm
   whether the intent is `GPL-3.0-only` (used in the recipe) or `GPL-3.0-or-later`.

Native code that is **fine** (builds from source, no committed blobs): `libretrodroid`
(C++ via CMake/NDK, bundled Oboe source), the `rcheevos` submodule (C). `submodules: true`
handles fetching both.

## Submission steps (after blockers are resolved)

1. Ensure the maintainer is aware / not opposed (this repo is the upstream — fine).
2. Fork `fdroiddata`, create branch `com.nendo.argosy`, copy `com.nendo.argosy.yml`
   to `metadata/com.nendo.argosy.yml`.
3. Lint & format: `fdroid lint com.nendo.argosy` and `fdroid rewritemeta com.nendo.argosy`.
4. Test the build locally (Docker): `fdroid build -v -l com.nendo.argosy`.
5. Push the branch to trigger fdroiddata CI; fix any scanner/build findings.
6. Open a merge request titled `New App: com.nendo.argosy`.

Reproducible builds are optional but recommended for a new app (see
`AllowedAPKSigningKeys`); consider it once the build passes.
