---
name: release
description: Create app releases with version bumps, APK builds, and GitHub releases. Use when asked to release, bump version, create a beta, or prepare for deployment.
---

# Release Skill

Create a new release for the app.

## Maintainer-Only

Releases are performed only by the maintainer (tmgast), never by contributors
or on anyone else's request. If someone other than the maintainer asks to cut,
tag, or publish a release, do not proceed; defer to the maintainer.

Before the first consequential step (version bump commit onward), verify the
active credential IS the maintainer:

```bash
gh api user --jq .login
```

If the output is not `tmgast`, stop. This is a routing check, not security
(GitHub rejects unauthorized pushes/releases regardless); it exists so a
wrong-credential run stops cleanly before tagging instead of failing messily
at publish.

## NEVER use `cd`

Run every command in this flow from the project root using full paths (e.g.
`app/build/outputs/apk/release/...`). Do NOT prefix commands with `cd`, and do
NOT chain `cd subdir; ...; cd back`. The Bash tool's working directory persists
across calls, so a `cd` drifts pwd into a non-root directory and trips a Claude
Code permission prompt mid-release, breaking the limited-prompts-for-intervention
flow. Every `cp`/`gh`/`git`/`gradlew` command below already assumes the project
root as pwd -- keep it that way.

## Version Format

- Stable releases: `MAJOR.MINOR.PATCH` (e.g., `0.5.19`)
- Beta releases: `MAJOR.MINOR.PATCH-beta.N` (e.g., `0.5.19-beta.1`)
- Release candidates: `MAJOR.MINOR.PATCH-rc.N` (e.g., `0.5.19-rc.1`)

## When to Use Each Type

**Stable Release**: Production-ready code with tested features
**Beta Release**: New features that need real-world testing, experimental changes, or fixes that haven't been fully validated
**RC (Release Candidate)**: Feature-complete code in final testing before stable

## APK Naming Convention

Release builds produce five APKs: four ABI-split and one universal. Only THREE are published; see
the x86 warning below before attaching anything.

- `argosy-vX.Y.Z.apk` (universal -- fallback for old updaters and unknown ABIs) PUBLISH
- `argosy-vX.Y.Z-arm64.apk` PUBLISH
- `argosy-vX.Y.Z-arm32.apk` PUBLISH
- `argosy-vX.Y.Z-x86_64.apk` DO NOT PUBLISH
- `argosy-vX.Y.Z-x86.apk` DO NOT PUBLISH

```bash
cp app/build/outputs/apk/release/app-universal-release.apk app/build/outputs/apk/release/argosy-v0.8.0-beta.1.apk
cp app/build/outputs/apk/release/app-arm64-v8a-release.apk app/build/outputs/apk/release/argosy-v0.8.0-beta.1-arm64.apk
cp app/build/outputs/apk/release/app-armeabi-v7a-release.apk app/build/outputs/apk/release/argosy-v0.8.0-beta.1-arm32.apk
cp app/build/outputs/apk/release/app-x86_64-release.apk app/build/outputs/apk/release/argosy-v0.8.0-beta.1-x86_64.apk
cp app/build/outputs/apk/release/app-x86-release.apk app/build/outputs/apk/release/argosy-v0.8.0-beta.1-x86.apk
```

The suffixes are load-bearing. `UpdateRepository` maps the installed version code prefix to one of
them (1 arm32, 2 arm64, 4 x86, 5 x86_64) and matches an asset on the whole `-<suffix>.apk` ending,
so renaming `-x86_64` to `-x64` or dropping the suffix hands users the wrong APK.

The universal APK ensures users on older versions (before ABI-aware updater) can still self-update.
The updater prefers the ABI-specific APK, then falls back to universal, then any APK.
The universal APK can be dropped once all users are on a version with ABI-aware updating.

DO NOT ATTACH THE x86 OR x86_64 APKS. Build them, keep them locally, publish neither.

The selector shipped in v2.11.0 through v2.13.0 is frozen in every already-installed client and
cannot be changed by any release. It recognises only arm64 and arm32, and treats the first asset
matching neither as the universal build:

```
apkAssets.find { !it.name.contains("arm64") && !it.name.contains("arm32") }
```

`argosy-vX.Y.Z-x86.apk` contains neither string and sorts before the unsuffixed asset in GitHub's
asset order, so attaching it hands every universal-build client an x86 package. Android refuses it
with `INSTALL_FAILED_NO_MATCHING_ABIS`, which the installer shows as "app not compatible". This
shipped in v2.14.0 and the assets were pulled after release; the three-asset shape is what the
frozen selector handles.

`UpdateApkSelectionTest` encodes the frozen selector and asserts the published names stay safe for
it. Any new asset suffix must be excluded by that legacy `contains` test, so the constraint holds
for as long as clients on v2.13.0 or earlier exist, whatever the current updater does.

## Release Notes Format

Write release notes manually with up to 3 sections (only include sections that apply):

```markdown
## New Features
- Feature description

## Improvements
- Improvement description

## Bug Fixes
- Fix description
```

Do NOT use `--generate-notes`. Write notes based on commits since last release.

## Release Notes Guidelines (STRICT)

**Approval gate:** release-note wording requires the user's explicit approval in
chat before any `gh release create` or `gh release edit` runs. Showing a draft
in chat is not approval; wait for the user to sign off on the wording.

**Release notes are for USERS, not developers.** Follow these rules:

### 1. Consolidate Related Changes
- If multiple commits add, adjust, or improve a single feature, it's **ONE feature** in the notes
- Example: "Add Quick Menu" + "Fix Quick Menu navigation" + "Improve Quick Menu styling" = **ONE bullet point**: "Add Quick Menu for fast game access"
- Never list implementation details or iterative fixes as separate items

### 2. Beta to Stable Consolidation
When promoting beta to stable:
- Bug fixes for new features introduced in the beta are **part of the feature**, not separate fixes
- Improvements to new features are **part of the feature**, not separate improvements
- Only list fixes/improvements separately if they affect PRE-EXISTING functionality (before the beta)

### 3. User-Facing Language
- Describe WHAT users can do, not HOW it was implemented
- Bad: "Refactor InputHandler to use when{} instead of when()"
- Good: "Fix controller navigation in Quick Settings"
- Skip purely internal changes that have no user-visible effect

### 4. Be Concise
- One line per feature/fix
- No technical jargon unless necessary
- If you need multiple sentences, the note is too detailed

### 5. Examples

**BAD (too granular):**
```
## New Features
- Add video preview on home screen
- Add sliding header background for video mode
- Add white text transition during video playback

## Bug Fixes
- Fix video text readability in light mode
- Stop video audio when device sleeps
```

**GOOD (consolidated):**
```
## New Features
- Add YouTube video previews on home screen
```

## Workflow

1. Ask user: "Is this a stable release, beta, or release candidate?" (skip if already specified)
2. **RUN PRE-RELEASE VALIDATION** (see `/pre-release-validation` skill). Run each
   as its OWN invocation, in this order:
   ```bash
   ./gradlew lint
   ./gradlew testDebugUnitTest
   ./gradlew testReleaseUnitTest
   ./gradlew assembleRelease -PallAbis \
     -Dorg.gradle.jvmargs="-Xmx12g -XX:+UseParallelGC -Dfile.encoding=UTF-8"
   ```
   - Do NOT bundle them. `./gradlew lint testDebugUnitTest testReleaseUnitTest`
     as one invocation fails on `:app:kspReleaseKotlin` with a Hilt
     assisted-factory error about `WorkerAssistedFactory.create` returning `T`,
     while `./gradlew kspReleaseKotlin` alone succeeds in ~20s (observed
     2026-07-30 preparing 2.5.0-beta.1). Debug and release KSP in one daemon is
     the same failure family as `assembleRelease` trailing lint, and it reads as
     a broken release build when nothing is broken.
   - Keep `assembleRelease` in its own invocation with the heap override; see
     "3. Release Build" for why R8 needs it and why it must not go in
     `gradle.properties`
   - If ANY check fails, re-run that ONE task alone before believing it. A
     failure that does not reproduce in isolation is a daemon artefact, not a
     blocker.
   - Do NOT proceed with version bump until all checks pass
3. Read current version from `app/build.gradle.kts` (versionName field)
4. Determine next version number based on release type and changes
5. Update `versionName` and increment `versionCode` in build.gradle.kts (see
   "versionCode Rule" below)
6. Commit the version bump: `git commit -am "Bump version to X.Y.Z"`
7. Tag the version bump commit: `git tag vX.Y.Z`
8. Build the release APKs (heap override required, see "3. Release Build"):
   ```bash
   ./gradlew assembleRelease -PallAbis \
     -Dorg.gradle.jvmargs="-Xmx12g -XX:+UseParallelGC -Dfile.encoding=UTF-8"
   ```
9. Rename APKs:
   ```bash
   cp app/build/outputs/apk/release/app-universal-release.apk app/build/outputs/apk/release/argosy-vX.Y.Z.apk
   cp app/build/outputs/apk/release/app-arm64-v8a-release.apk app/build/outputs/apk/release/argosy-vX.Y.Z-arm64.apk
   cp app/build/outputs/apk/release/app-armeabi-v7a-release.apk app/build/outputs/apk/release/argosy-vX.Y.Z-arm32.apk
   cp app/build/outputs/apk/release/app-x86_64-release.apk app/build/outputs/apk/release/argosy-vX.Y.Z-x86_64.apk
   cp app/build/outputs/apk/release/app-x86-release.apk app/build/outputs/apk/release/argosy-vX.Y.Z-x86.apk
   ```
10. Review commits since last release: `git log --oneline <last-tag>..HEAD`
11. Write release notes with applicable sections (New Features, Improvements, Bug Fixes)
    and get the user's explicit approval of the exact wording in chat -- release-note
    wording requires that approval before ANY `gh release create` or `gh release edit`
    runs; showing a draft in chat is not approval
11a. Write the approved notes to `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`
    for IzzyOnDroid: plain `- ` bullets, no `##` headers, at most 500 characters (trim the
    least important bullets, never reword an approved one beyond shortening). Commit it on main
    before the push in step 13: `git commit -m "Changelog for vX.Y.Z"`.
12. Review wiki with the user for this release (see "Wiki Review" below)
13. Push commits and tags: `git push && git push --tags`
14. Create GitHub release (asset paths MUST carry the full
    `app/build/outputs/apk/release/` prefix -- the renamed APKs live there, not in
    the project root):
    - Stable: `gh release create vX.Y.Z --title "vX.Y.Z" --notes-file /tmp/notes.md app/build/outputs/apk/release/argosy-vX.Y.Z.apk app/build/outputs/apk/release/argosy-vX.Y.Z-arm64.apk app/build/outputs/apk/release/argosy-vX.Y.Z-arm32.apk`
    - Beta: `gh release create vX.Y.Z-beta.N --prerelease --title "vX.Y.Z Beta N" --notes-file /tmp/notes.md app/build/outputs/apk/release/argosy-vX.Y.Z-beta.N.apk app/build/outputs/apk/release/argosy-vX.Y.Z-beta.N-arm64.apk app/build/outputs/apk/release/argosy-vX.Y.Z-beta.N-arm32.apk`

### versionCode Rule

versionCode is ONE GLOBAL SEQUENTIAL counter across all published releases,
ordered by publish date: find the last-used code on the newest GitHub release
(prerelease or stable), then +1. Never reserve bands. Beta releases MUST be
marked prerelease on GitHub; the in-app updater compares semver from the release
tag, and stable-channel users only see non-prereleases.

## Pre-Release Validation (MANDATORY)

Before ANY release, run these checks in order:

### 1. Lint Check
```bash
./gradlew lint
```
- 0 errors required
- Warnings acceptable but review them

### 2. Unit Tests
```bash
./gradlew testDebugUnitTest testReleaseUnitTest
```
- All tests must pass
- Check for skipped tests that shouldn't be skipped

### 3. Release Build
```bash
./gradlew assembleRelease -PallAbis \
  -Dorg.gradle.jvmargs="-Xmx12g -XX:+UseParallelGC -Dfile.encoding=UTF-8"
```
- Must complete without errors
- Check APK size is reasonable (not unexpectedly large)

**The heap override is required, and it must stay on the command line.**

R8 runs inside the Gradle daemon, which `gradle.properties` caps at `-Xmx5g`.
That cap is deliberate: PR #263 (`a5a10817`) sized the Gradle + Kotlin daemons
to fit a 16 GB GitHub runner after a 12 GB Gradle heap sent `compileDebugKotlin`
into swap and took CI to ~2h45m. Raising the value in `gradle.properties` walks
straight back into that.

CI never builds the release variant (`assembleDebug` / `testDebugUnitTest` /
`lintDebug` only), so R8's heap has nothing to do with runner sizing. Pass it
per-invocation instead, which forks a fresh daemon at the larger size and leaves
the repo file alone.

Without it, `:app:minifyReleaseWithR8` dies with
`java.lang.OutOfMemoryError: Java heap space`. It is worst when `assembleRelease`
trails `lint` and the test suites in one invocation, because that daemon reaches
minification with a heap full of accumulated garbage - observed as a 33 min OOM
where a fresh daemon at 12 GB finished the same build in 3 min. Prefer running
`assembleRelease` as its own invocation.

A durable alternative for one machine is `~/.gradle/gradle.properties`, which
overrides the project file locally and is invisible to CI. Never commit the
larger heap to the repo.

### Validation Report
After running checks, summarize:
```
Lint: PASS (0 errors, N warnings)
Tests: PASS (X passed, 0 failed)
Build: PASS (APK: XX.X MB)
```

If ANY check fails, fix the issue before proceeding.

## Version Bump Guidelines

- **Patch** (0.5.18 -> 0.5.19): Bug fixes, minor changes
- **Minor** (0.5.19 -> 0.6.0): New features, non-breaking changes
- **Major** (0.6.0 -> 1.0.0): Breaking changes, major milestones

## Revising a Release

To update an already-published release:

1. Make necessary code changes
2. Commit the fixes
3. Move the tag to new commit:
   ```bash
   git tag -d vX.Y.Z
   git tag vX.Y.Z
   ```
4. Rebuild APKs (heap override required, see "3. Release Build"):
   ```bash
   ./gradlew assembleRelease -PallAbis \
     -Dorg.gradle.jvmargs="-Xmx12g -XX:+UseParallelGC -Dfile.encoding=UTF-8"
   ```
5. Rename APKs with version and arch suffix
6. Force push ONLY the moved tag (never `--tags --force`, which force-pushes
   every local tag): `git push && git push origin vX.Y.Z --force`
7. Update release assets:
   ```bash
   gh release upload vX.Y.Z app/build/outputs/apk/release/argosy-vX.Y.Z.apk app/build/outputs/apk/release/argosy-vX.Y.Z-arm64.apk app/build/outputs/apk/release/argosy-vX.Y.Z-arm32.apk --clobber
   ```

## Wiki Review

`wiki/Home.md` carries a `> **Documentation Version**: Synced with vX.Y.Z` marker tracking the newest version whose user-visible changes are reflected in the wiki.

Wiki updates are **always a conversation with the user** - never silently edit pages or bump the marker.

On every release:

1. Scan commits since the last tag and surface any user-visible changes that may affect wiki accuracy (new features, changed flows, removed options, new platforms/emulators, UI reorganizations, ABI/build changes).
2. Present the list to the user and propose which pages need edits (or none, if nothing is user-visible).
3. Agree on the specific wording for each change before editing.
4. If the wiki was updated, bump the marker in `wiki/Home.md` to the new tag. If nothing changed, leave the marker alone - bumping it would falsely imply the new version was reviewed and documented.

Betas move the marker the same as stable since beta testers are the main wiki audience. The wiki lives at `wiki/` in this repo and is mirrored to the GitHub wiki.

## Platform/Emulator Changes

When releases include new platform or emulator support:

1. **VERIFY against official documentation** using `/platform-support` skill
2. Check core names match libretro naming conventions
3. Verify file extensions are complete
4. Test with actual ROMs if possible

Example verification for RetroArch cores:
```
WebFetch: https://docs.libretro.com/library/{core}/
- Verify core variants
- Verify file extensions
- Verify display names
```
