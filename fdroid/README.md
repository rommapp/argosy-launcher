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
| License | `GPL-3.0-only` (confirmed) | `LICENSE` (verbatim GPLv3, no "or later") |
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

## Steam / prebuilt-jar blocker — RESOLVED via a FOSS product flavor

The prebuilt `libs/maven/io/github/joshuatam/javasteam*/**.jar` binaries (force-included via
`.gitignore: !libs/maven/**/*.jar`, consumed through the in-tree maven repo in
`settings.gradle.kts`) are the one thing F-Droid's scanner rejects **regardless of license**.

**What was found (research):** both jars come from one MIT multi-module fork
`github.com/joshuatam/JavaSteam` (modules `javasteam` + `javasteam-depotdownloader`, group
`io.github.joshuatam` `1.8.1`, rolling branch `gamenative-latest`; base jar ~16 MB of
protobuf-generated classes). It is **not on Maven Central** (upstream `in.dragonbra:javasteam`
is, but lacks the `depotdownloader` module), **JitPack cannot build it** (errors, no tags),
and app coupling is **narrow** — only 6 files import the library.

**What was implemented (chosen resolution):** a `distribution` flavor dimension with `full`
and `foss` product flavors (`app/build.gradle.kts`):

- `full` (default) — unchanged; the Steam-download build for GitHub releases. Keeps
  `"fullImplementation"(libs.bundles.steam)` and the six library-backed classes, now under
  `app/src/full/kotlin/.../data/steam/`.
- `foss` — **no Steam library**. The six library-touching classes (`SteamService`,
  `SteamAuthManager`, `SteamContentManager`, `SteamDepotManager`, `SteamLibraryManager`,
  `LicenseSerializer`) have no-op stubs in `app/src/foss/kotlin/.../data/steam/` that
  reproduce exactly the public API shared code consumes (state flows, control methods,
  `@Singleton @Inject` constructors, and the shared value types in `SteamModels.kt`). The
  ~30 other Steam files (Room entities/DAOs, repositories, UI) are unchanged in `src/main`
  and compile against the stubs, so the Steam settings screens still render but the feature
  is inert.

The F-Droid recipe therefore builds `assembleFossRelease` (`gradle: [foss]`) and uses
`scandelete: [libs/maven]` to remove the prebuilt jars before the build — safe because the
`foss` flavor never references them. This removes the binaries **and** the non-free depot
downloader from the F-Droid artifact entirely.

*Alternatives not taken (kept for reference):* publish the fork to Maven Central (cleanest if
keeping Steam, but needs the maintainer's Sonatype credentials); build the fork from source
in fdroiddata (fragile — JitPack already fails); or `scanignore: [libs/maven]` (reviewers
reject).

2. **Discord SDK** — an optional local AAR (`app/libs/*.aar`) that is gitignored and
   absent from the repo, so the default build excludes it (`DISCORD_SDK_ENABLED=false`).
   No action needed **as long as** it is never committed. If it is bundled, it becomes a
   `NonFreeDep`/`NonFreeNet` concern.

That is the only remaining hard blocker. The items below were investigated and resolved.

## Confirmed (no longer blockers)

- **`sigil` submodule — CONFIRMED FINE.** `github.com/rommforge/argosy-sigil` (pinned at
  `42ca623`) is a **pure C** ROM serial-ID parser, **MPL-2.0** (FOSS, GPLv3-compatible),
  built from source via **CMake/NDK** (`bindings/android/.../CMakeLists.txt`,
  `externalNativeBuild { cmake }`). The tree contains only `.c`/`.h`/CMake source — **no
  prebuilt `.so`/`.a`/`.jar`**. **No Rust toolchain is needed** (an earlier note here was
  wrong). `submodules: true` + the F-Droid VM's NDK is all it requires.

- **`api.argosy.dev` — CONFIRMED `NonFreeNet` (added to the recipe).** The social layer
  (`SOCIAL_API_URL`, `ArgosSocialService`/`SocialRepository`) — friends, activity feed,
  presence, achievement/session upload, and netplay matchmaking — depends on the
  proprietary `api.argosy.dev` backend over a persistent WebSocket. It is opt-in (QR-code
  login) but auto-reconnects on every startup once linked, with no disable short of
  unlinking. The core launcher and self-hosted RomM sync work without it. `titledb` and
  `cheats` also point at `api.argosy.dev` but are **build-secret-gated**
  (`TITLEDB_API_SECRET`/`CHEATSDB_API_SECRET` are empty in a source build →
  `isConfigured()` false → never contacted), so they are inert in an F-Droid build and are
  not the trigger. The recipe declares `AntiFeatures: NonFreeNet` with a description.

- **License — CONFIRMED `GPL-3.0-only`.** `LICENSE` is the verbatim GPLv3 text, the README
  says "GNU General Public License v3.0" (no "or later"), and no source file elects
  `-or-later` (no SPDX headers). `GPL-3.0-only` in the recipe is correct.

Native code that is **fine** (builds from source, no committed blobs): `libretrodroid`
(C++ via CMake/NDK, bundled Oboe source), the `rcheevos` submodule (C), and `sigil`
(C, above). `submodules: true` handles fetching the submodules.

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
