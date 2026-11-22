# F-Droid packaging

`com.nendo.argosy.yml` is the F-Droid build recipe. It belongs in
[fdroiddata](https://gitlab.com/fdroid/fdroiddata) at `metadata/com.nendo.argosy.yml`; it is kept
here so it is reviewed alongside the code it builds. Store text, icon and screenshots come from
`fastlane/metadata/android/en-US/`, which F-Droid and IzzyOnDroid read directly.

## What the recipe relies on

- **No committed binaries.** Steam support builds against `in.dragonbra:javasteam` from Maven
  Central. Everything native (libretrodroid, rcheevos, libadrenotools, Sigil) is built from source
  with CMake and the NDK the Android Gradle Plugin defaults to (`ndk:` in the recipe must match it).
- **`-Pfdroid`.** Turns off Argosy's own GitHub updater (F-Droid signs with its own key, so a GitHub
  APK cannot install over an F-Droid one) and leaves release APKs unsigned for F-Droid to sign.
- **`-PallAbis`** with ABI splits. versionCode is `abi * 1_000_000 + base`: armeabi-v7a is
  `1_000_000 + base`, arm64-v8a is `2_000_000 + base` (`app/build.gradle.kts`). Each build entry
  picks its split with `output:`.
- **Stable tags only.** `UpdateCheckMode` matches `^v[0-9.]+$`, so `-beta.N` tags are skipped.

The first build entry must be a tag that contains the Maven Central JavaSteam switch and the
`fdroid` property. v3.0.0 does not, so the recipe starts at v3.0.1.

## Features that differ in an F-Droid build

- No self-update and no update prompts; F-Droid delivers updates.
- Game title lookup (TitleDB) and cheat database sync are off: they need request-signing secrets
  that are injected only into our own release builds.
- The Discord Social SDK is an optional AAR that is not in this repository, so Discord presence is
  off, as it is in any build without that AAR.

## Review risks to expect

- The built-in emulator downloads libretro cores from the libretro buildbot (armGBA from its
  author's GitHub releases) at runtime, and managed installers download emulator APKs. Reviewers
  may ask for this to be declared or limited.
- Anti-features declared: `NonFreeNet` (Steam, RetroAchievements, the argosy.dev social backend)
  and `NonFreeAdd` (offers to install non-free emulator apps).

## Checking the recipe before submitting

With `fdroidserver` installed, from an fdroiddata checkout on a branch named `com.nendo.argosy`:

```shell
fdroid readmeta
fdroid rewritemeta com.nendo.argosy
fdroid lint com.nendo.argosy
fdroid build -v -l com.nendo.argosy
```

Push the branch to a fork of fdroiddata, wait for its CI pipeline to pass, and open a merge
request using the "New App" template.

## IzzyOnDroid

IzzyOnDroid uses the APKs attached to our GitHub releases (our signature, our updater) and the
fastlane metadata above. It needs no build recipe; inclusion is requested at
https://codeberg.org/IzzyOnDroid/repo/issues.
