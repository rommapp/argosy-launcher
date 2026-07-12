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

## Submission steps (fdroiddata lives on GitLab: gitlab.com/fdroid/fdroiddata)

Per fdroiddata's `CONTRIBUTING.md`:

1. Register on GitLab, **fork** `gitlab.com/fdroid/fdroiddata`, clone your fork:
   ```shell
   git clone https://gitlab.com/YOUR_USERNAME/fdroiddata.git
   cd fdroiddata
   ```
2. Create a branch **named after the app id** — do NOT work on `master` (it is protected
   and you cannot open an MR from it):
   ```shell
   git checkout -b com.nendo.argosy
   ```
3. Add the recipe as `metadata/com.nendo.argosy.yml` (copy this repo's
   `fdroid/com.nendo.argosy.yml`). Optionally scaffold instead with
   `fdroid import --url https://github.com/rommapp/argosy-launcher --subdir app`.
4. With `fdroidserver` installed (`pip install git+https://gitlab.com/fdroid/fdroiddata.git`),
   run the local checks:
   ```shell
   fdroid readmeta                       # syntax
   fdroid rewritemeta com.nendo.argosy   # canonical formatting
   fdroid checkupdates com.nendo.argosy  # fills Auto Name / Current Version
   fdroid lint com.nendo.argosy          # fix all warnings
   fdroid build -v -l com.nendo.argosy   # must produce at least one working build
   ```
5. Commit with a clear message (the "New App: com.nendo.argosy" convention is common),
   push the branch to your fork, and check **CI/CD → Pipelines** passes; fix metadata until
   it does.
6. Open a **merge request** against fdroiddata from your `com.nendo.argosy` branch and
   **fill in the MR template**. Squash is on by default. Reply promptly to packager
   questions.
