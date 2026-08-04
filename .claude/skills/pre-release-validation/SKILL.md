---
name: pre-release-validation
description: Run local CI/CD checks before pushing code. Use before commits, releases, or when validating code quality. Catches issues before they reach remote.
---

# Pre-Release Validation

Run comprehensive local checks before pushing code to catch issues early.

---

## When to Use This Skill

**Automatically invoke:**
- Before creating a release (stable or beta)
- Before pushing significant changes
- After completing a feature implementation

**Manual invocation when:**
- User asks to "run checks" or "validate"
- Debugging CI failures locally
- Auditing code quality

---

## Validation Tiers

Timing reality: a cold `:app:compileDebugKotlin` runs ~12 min with the Compose
stability config (per the comment at `app/build.gradle.kts:140`; >42 min without
it); a warm compile is ~4.5 min. Tier estimates assume a warm daemon and stretch
accordingly when cold. Build in the background and wait for completion; do not
kill slow builds mid-flight.

### Tier 1: Quick Check (warm ~5 min)
Fast validation for small changes.

```bash
# Lint check
./gradlew lintDebug 2>&1 | head -50

# Compile check
./gradlew compileDebugKotlin 2>&1
```

### Tier 2: Standard Check (warm ~10 min)
Full validation before commits.

```bash
# Lint
./gradlew lintDebug

# Unit tests
./gradlew testDebugUnitTest

# Compile release
./gradlew compileReleaseKotlin
```

### Tier 3: Full Check (warm 15+ min; much longer cold)
Complete validation before releases.

```bash
# Full lint (both debug and release)
./gradlew lint

# All unit tests
./gradlew testDebugUnitTest testReleaseUnitTest

# Full release build
./gradlew assembleRelease
```

---

## Coupling Sweep (release backstop)

The git-commit hook (`scripts/ci/coupling-guard.py`, via PreToolUse) gates each
agent commit against Argosy's coupling map. This sweep is the release-time backstop: it
re-runs the same rules over the WHOLE changeset since the last tag, catching coupling gaps
that slipped across many commits.

```bash
# Sweep every change since the last release tag
LAST_TAG=$(git describe --tags --abbrev=0 2>/dev/null || echo "")
python3 scripts/ci/coupling-guard.py --sweep "${LAST_TAG:+$LAST_TAG..}HEAD"
```

The sweep is advisory (never blocks the release on its own), but its output is a release
blocker for judgment:
- WALKTHROUGH-FORCED axes (save-sync, RomM) flagged -> confirm the live-data proof
  obligations were actually met for this release (`GET /api/saves`, negotiate returns no_op
  twice). Never ship these on code-reading alone.
- platform / settings / UI flags -> confirm the lockstep set and consumption sites.

Rules live in `scripts/ci/coupling-hotspots.json` (machine mirror of
`coupling-map.md`). Update both when the architecture moves. The guard and its rules are
tracked, not maintainer-local: CI runs the same sweep on every PR, so a rule added here
takes effect for every contributor rather than only on this machine.

---

## Validation Checklist

### Code Quality
```
[ ] No lint errors (warnings acceptable)
[ ] No compiler warnings in changed files
[ ] Unit tests pass
[ ] No TODO/FIXME in committed code (unless intentional)
```

### Build Validation
```
[ ] Debug build succeeds
[ ] Release build succeeds
[ ] APK size reasonable (check for bloat)
```

### Git Hygiene
```
[ ] No untracked files that should be committed
[ ] No secrets/credentials in staged files
[ ] Commit message follows conventions
```

### Compose Stability Guard
The stability config (`app/compose_stability_config.conf`) asserts covered packages
are immutable; the compiler does not check it. Before release, verify no mutable
constructor/body properties crept into covered state classes:

```bash
grep -rEn --include="*.kt" "^ {4}var [a-zA-Z]+:" \
  app/src/main/kotlin/com/nendo/argosy/data/model \
  app/src/main/kotlin/com/nendo/argosy/data/local/entity \
  app/src/main/kotlin/com/nendo/argosy/ui/screens \
  app/src/main/kotlin/com/nendo/argosy/ui/components \
  app/src/main/kotlin/com/nendo/argosy/ui/dualscreen
```

Triage hits: a `var` property on a data class used as UI state or a composable param
is a BLOCKER (fix to `val` + copy-on-change, or move the class out of covered
packages). `private var` internals of ViewModels/delegates are exempt, but confirm
nothing reads them directly inside composition.

```
[ ] Stability guard grep is clean (or all hits triaged exempt)
```

---

## Workflow

### Before Commit
```bash
# Quick validation
./gradlew lintDebug compileDebugKotlin testDebugUnitTest
```

### Before Push
```bash
# Standard validation
./gradlew lint testDebugUnitTest compileReleaseKotlin
```

### Before Release
```bash
# Full validation (run these in sequence)
./gradlew lint
./gradlew testDebugUnitTest testReleaseUnitTest
./gradlew assembleRelease
```

---

## Common Issues & Fixes

### Lint Errors

**Unused imports/variables:**
```kotlin
// Remove or use the import/variable
// Android Studio: Optimize Imports (Cmd+Opt+O)
```

**Hardcoded strings:**
```kotlin
// Move to strings.xml or suppress if intentional
@Suppress("HardcodedText")
```

**Missing nullability:**
```kotlin
// Add explicit null handling
val value = nullable ?: default
```

### Test Failures

**Flaky tests:**
```bash
# Run specific test multiple times
./gradlew testDebugUnitTest --tests "TestClass.testMethod" --rerun-tasks
```

**Missing mocks:**
```kotlin
// Ensure all dependencies are mocked in test setup
every { dependency.method() } returns expected
```

### Build Failures

**Dependency conflicts:**
```bash
# Check dependency tree
./gradlew dependencies --configuration releaseRuntimeClasspath
```

**ProGuard issues:**
```
# Check proguard-rules.pro for missing keep rules
-keep class com.nendo.argosy.** { *; }
```

---

## Single Invocation, Never Parallel

NEVER run concurrent gradle invocations against this project (no separate
terminals, no `&`). Concurrent invocations corrupted the KSP caches here in a
prior incident, and recovery meant scoped cache invalidation. Pass multiple
tasks to ONE invocation and let Gradle schedule them:

```bash
./gradlew lintDebug testDebugUnitTest
```

---

## Output Interpretation

### Lint Output
```
> Task :app:lintDebug
Wrote HTML report to file:///path/app/build/reports/lint-results-debug.html

0 errors, 3 warnings  # ACCEPTABLE - proceed
5 errors, 3 warnings  # BLOCKING - fix errors first
```

`app/lint-baseline.xml` snapshots the warnings that existed at 2.6.1, so anything
lint reports now is NEW and worth reading. Regenerate it only as a deliberate act
after clearing real findings (delete the file and re-run `lintDebug`), never to
silence a warning that just appeared.

What the baseline is holding, so it is not mistaken for a backlog of bugs: the
`Recycle` entries are false positives (cursors already closed via `?.use`,
animators driven by a started `AnimatorSet`); `HardwareIds` is the device
identity RomM device-sync and request signing depend on; `SdCardPath` entries are
the upstream-exact emulator data paths the save resolvers need verbatim;
`GetInstance` is AES-XTS, which is built from ECB primitives by definition; and
the network config permits cleartext and user certificates because the server is
self-hosted, frequently on a LAN with a self-signed cert. Genuinely worth doing
later: `ApplySharedPref` outside `SessionStateStore` (where the synchronous write
is deliberate), and the `ObsoleteSdkInt` dead branches.

### Test Output
```
> Task :app:testDebugUnitTest
85 tests completed, 0 failed  # PASS
85 tests completed, 2 failed  # BLOCKING - check failures
```

### Build Output
```
BUILD SUCCESSFUL in 54s  # PASS
BUILD FAILED             # BLOCKING - check error
```

---

## Integration with Release Skill

The release skill should invoke pre-release validation automatically:

```markdown
## Release Workflow (Updated)

1. Ask release type (stable/beta/rc)
2. **RUN PRE-RELEASE VALIDATION (Tier 3)**
   - ./gradlew lint
   - ./gradlew testDebugUnitTest testReleaseUnitTest
   - ./gradlew assembleRelease
3. **RUN COUPLING SWEEP** over changes since last tag (see Coupling Sweep above)
   - Resolve flagged axes; for save-sync/RomM confirm live-data proofs were met
4. If validation or sweep blockers remain, STOP and fix issues
5. If clean, proceed with version bump
6. ... (rest of release process)
```

---

## CI/CD Parity

CI (`.github/workflows/build.yml`) runs DEBUG variants only:

| Local Command | CI Job |
|---------------|--------|
| `./gradlew assembleDebug -PallAbis` | build |
| `./gradlew testDebugUnitTest -PallAbis` | test |
| `./gradlew lintDebug -PallAbis` | lint |

PRs additionally run the `rules` job (blocking agentic smell check + advisory
coupling sweep; skippable via the maintainer-applied `rules-exempt` PR label).
CI never builds, tests, or lints the RELEASE variant -- the
release-variant checks in Tier 2/3 are local-only and still required before a
release. Running the debug set locally first prevents CI failures.

---

## Quick Reference Commands

```bash
# One-liner: Full pre-release validation
./gradlew lint testDebugUnitTest assembleRelease

# Check specific area after changes
./gradlew testDebugUnitTest --tests "*DownloadManager*"
./gradlew testDebugUnitTest --tests "*Platform*"
./gradlew testDebugUnitTest --tests "*Emulator*"

# Just build, skip tests (quick sanity check)
./gradlew assembleRelease -x test -x lint
```

Do NOT reach for `./gradlew clean` when a build misbehaves -- house rule.
Invalidate the specific stale cache (scoped) instead; a clean forces the full
cold-compile cost for no diagnostic gain.

---

## Validation Report Template

After running checks, report:

```markdown
## Pre-Release Validation Report

### Lint
- Status: PASS/FAIL
- Errors: 0
- Warnings: 3 (acceptable)

### Tests
- Status: PASS/FAIL
- Passed: 85
- Failed: 0
- Skipped: 2

### Build
- Status: PASS/FAIL
- APK Size: 12.3 MB
- Build Time: 54s

### Verdict
Ready for release: YES/NO
Blockers: [list if any]
```
