---
name: platform-support
description: Verify emulator, ROM, and file type support against official documentation. Use when adding new platforms, emulators, or cores to ensure completeness and correctness.
---

# Platform Support Verification

Verify platform, emulator, and file type support against authoritative sources before implementation.

---

## When to Use This Skill

**Automatically invoke when:**
- Adding support for a new platform/console
- Adding new RetroArch cores
- Updating file extension lists
- Verifying existing platform configurations

**Manual invocation when:**
- User questions if support is correct
- Debugging ROM detection issues
- Auditing platform coverage

---

## Upstream Research Mandate (Non-Negotiable)

libretro / RetroArch / core identifiers are NEVER inferred, guessed, or pattern-matched from sibling entries. Every core id, option value token, file extension, save/state format, and BIOS filename is verified against upstream (docs.libretro.com, the core's own repo, RetroArch source) via WebFetch BEFORE it lands in a registry. A plausible-looking core id that does not exist upstream or on the buildbot produces a platform that can never launch, and it passes review because it looks like its siblings. Looking like a sibling proves nothing in either direction. `vice_x128` reads like a guess and is real (see Known traps below).

## Verification Sources

### RetroArch Cores
- **Primary**: https://docs.libretro.com/library/{core_name}/
- **Fallback**: https://github.com/libretro/{core_name}

### The core-info manifest (fastest authoritative source)

RetroArch bundles ~292 `.info` files in its APK, readable on any device with root at
`/data/data/com.retroarch/info/`. Each declares `corename`, `display_name`,
`systemid`, `systemname` and `supported_extensions` - i.e. the core id, its
platform, and its formats, straight from upstream. Upstream repo:
`libretro/libretro-core-info`. Prefer this over prose docs for core ids,
extensions and display names.

TWO sources are required, not one. The info file proves a core EXISTS; only the
buildbot proves it is BUILT FOR ANDROID:

    https://buildbot.libretro.com/nightly/android/latest/arm64-v8a/

`cdi2015` is the cautionary case - it ships an info file and looks like a valid
Philips CD-i core, but no `cdi2015_libretro_android.so` exists on the arm64
buildbot. Routing to it produces a platform that can never launch. `same_cdi` is
the one that actually ships. Grep the buildbot listing for
`<coreid>_libretro_android.so` before any core id lands in a registry.

Note the app CANNOT read the info directory at runtime - it is another app's
private data. Treat it as a build-time reference, not a discovery mechanism.

### Standalone Emulators
- Check official GitHub/website for supported formats
- Look for "supported formats" or "file types" documentation

### Emulator config value tables (data/emulator/config/*ConfigSpec.kt)

Argosy writes these tokens into the user's emulator config through root, so
every editable ENUM table is checked against the upstream file that parses or
writes it. Re-verify against a newer commit before adding or changing an entry.
Last verified 2026-11-26:

- Eden: git.eden-emu.dev/eden-emu/eden @ 1cd2ecd562c5679c3220129259b64d3f38b862cf,
  src/common/settings_enums.h (ENUM ordinals) and src/common/settings.h
  (labels, Android defaults). config.ini stores the ordinal.
- DuckStation: github.com/stenzek/duckstation @ 2c79bd36a705b3b08ac7761257bbcff71bfc27ec,
  src/core/settings.cpp (s_*_names tables, Load keys, ResolutionScale 0 =
  automatic), src/core/settings.h (DEFAULT_*). settings.ini stores the name.
- ARMSX2 Refresh (com.armsx2): github.com/ARMSX2/ARMSX2 @ 46f157520540a2538b8cb48f4695398ef2908025,
  platforms/android/app/src/main/java/com/armsx2/config/Settings.kt (field
  docs, defaults, toJson keys) and config/ConfigStore.kt ("config.global",
  armsx2-settings.json mirror).
- Dolphin: github.com/dolphin-emu/dolphin @ 44a1e006d172b426c489581ee1c362b82438438d,
  Source/Core/VideoCommon/VideoConfig.h (AspectMode, ShaderCompilationMode,
  TextureFilteringMode, AnisotropicFilteringMode = -1..4) and
  Source/Core/Core/Config/GraphicsSettings.cpp (sections, keys, defaults).
- Cemu: github.com/cemu-project/Cemu @ 2f49c079da1d50b85cea0ed99aedda58846b3523,
  src/config/CemuConfig.h (enums), src/config/CemuConfig.cpp (XML element
  names, load fallbacks; UpscaleFilter loads as bicubic Hermite),
  src/Cafe/HW/Latte/Renderer/Vulkan/SwapchainInfoVk.h (VSync 0..3).
- Vita3K: github.com/Vita3K/Vita3K @ aee471327b972380f4377e49b762f179c6a0a8e3,
  vita3k/config/include/config/config.h (keys, defaults, overlay and
  screenshot enums) and
  android/app/src/main/java/org/vita3k/emulator/ui/screens/settings/SettingsSections.kt
  (screen-filter, memory-mapping and anisotropy tokens). memory-mapping
  options are device-gated by supportedMemoryMappingMask upstream.

---

## Verification Checklist

### 1. Core/Emulator Names
```
[ ] Core ID matches libretro naming (e.g., "vice_x64" not "vice-x64")
[ ] Display name is accurate (check docs for official name)
[ ] All variants listed (e.g., vice_x64, vice_x64sc, vice_xscpu64)
```

### 2. File Extensions
```
[ ] All common extensions from docs included
[ ] No obsolete/deprecated formats
[ ] Archive formats included (zip, 7z) if supported
[ ] Platform-specific formats not missed (e.g., .p00 for C64)
```

### 3. Platform Mapping
```
[ ] Canonical slug matches RomM/common conventions
[ ] All slug aliases registered (e.g., "commodore64" -> "c64")
[ ] Sort order is logical (chronological within manufacturer)
```

---

## Workflow

### Step 1: Gather Requirements
```
What platform/emulator is being added or verified?
What is the authoritative documentation source?
```

### Step 2: Fetch Documentation
Use WebFetch to retrieve official docs:
```
WebFetch(
  url: "https://docs.libretro.com/library/{core}/",
  prompt: "List all core variants, supported file extensions, and system requirements"
)
```

### Step 3: Compare Against Implementation

Check these files:
- `LibretroCoreRegistry.kt` - built-in cores (the PRIMARY launch path): core id, .so fileName, platforms, requiresBios, isDefault, netplaySupport
- `EmulatorRegistry.kt` - external emulators, per-platform default cores, core detection patterns
- `PlatformDefinitions.kt` - Extensions, slug mappings, display names, local platforms

A new platform or core can also touch the other registries (BiosPathRegistry, SavePathRegistry, StatePathRegistry, CoreOptionManifestRegistry, CoreControlManifestRegistry, TouchLayoutRegistry, ShaderRegistry, FrameRegistry, PlatformWeightRegistry, PlatformSaveHandlerRegistry). Enumerate them with Glob `**/*Registry*.kt` and check each for relevance - the two-file model above is the minimum, not the whole checklist.

#### One emulator resolver

`EmulatorResolver.resolveEmulatorForGame` is the only answer to "which emulator runs this game". `GameLauncher.resolveEmulator` delegates to it and launches the returned def; `getEmulatorPackageForGame` is its package, which save sync uses to decide where saves are read and written. A second precedence chain is how restores once landed in a directory the running emulator never read.

The precedence is game override, then platform default, then ad-hoc (an installed package unknown to the registry), then the detector's preferred emulator. A built-in package is skipped when `builtinLibretroEnabled` is off or the core does not support the platform. The resolver returns a def, not a package, because Cemu and AetherSX2 each have two defs on one package.

One asymmetry is BY DESIGN and must not be "fixed": save paths collapse family variants to their base id via `resolveEmulatorId` / `canonicalEmulatorId`, because the path registries key on exact ids, while the launcher starts the variant `EmulatorDef`. The family fallback in the path registries is what makes the two views meet.

### Step 4: Report Discrepancies

Format findings as:
```markdown
## Platform Verification: {Platform Name}

### Source
{URL to documentation}

### Cores
| Core ID | Display Name | Status |
|---------|--------------|--------|
| vice_x64 | VICE x64 | OK |
| vice_x64sc | VICE x64 (Accurate) | FIXED - was "SuperCPU" |

### File Extensions
| Extension | In Docs | In Code | Status |
|-----------|---------|---------|--------|
| .d64 | Yes | Yes | OK |
| .p00 | Yes | No | MISSING |

### Recommendations
1. Add missing extension: p00
2. Fix display name for vice_x64sc
```

---

## Key Files

### LibretroCoreRegistry.kt
Location: `app/src/main/kotlin/com/nendo/argosy/libretro/LibretroCoreRegistry.kt`

Defines the built-in cores - the primary launch path. Each `CoreInfo` carries:
- `coreId` / `fileName` (the buildbot .so name)
- `platforms` - which platform slugs the core serves
- `requiresBios` - BIOS filenames
- `isDefault` - default core for its platforms
- `netplaySupport`

### EmulatorRegistry.kt
Location: `app/src/main/kotlin/com/nendo/argosy/data/emulator/EmulatorRegistry.kt`

Contains:
- `supportedPlatforms` - Which platforms each emulator supports
- `preferredCores` - Default core per platform
- `getRetroArchCorePatterns()` - Core detection patterns
- `platformCores` - Available cores with display names
- `getRecommendedEmulators()` - Emulator recommendations per platform
- The family/variant machinery: `EmulatorFamily(baseId, displayNamePrefix, packagePatterns, supportedPlatforms, ...)`, roughly two dozen family entries, `getEmulatorFamilies()`, `matchesFamily()`, `findFamilyForPackage()`, `variantSuffix()`, `createDefFromFamily()`

#### Family variants (read before adding a nightly or fork package)

A package matching a family's `packagePatterns` does not get its own registry entry. `createDefFromFamily` SYNTHESIZES an `EmulatorDef` whose id is `<baseId>_<packageName with dots replaced by underscores>` (e.g. `citra_io_github_lime3ds_android`).

That id shape is load-bearing outside EmulatorRegistry. Both save-path registries carry explicit recovery for it - `familyBaseIdFor` / `familyFallbackConfig` in `SavePathRegistry.kt` and `familyFallbackConfig` in `StatePathRegistry.kt` - which strip the `<baseId>_` prefix (longest matching baseId wins) so a fork resolves the base emulator's save and state paths. Add a fork package under a family and it inherits those paths automatically; give it a hand-written id that does NOT start with `<baseId>_` and it silently falls through to no config, which is how saves land in the wrong directory.

### PlatformDefinitions.kt
Location: `app/src/main/kotlin/com/nendo/argosy/data/platform/PlatformDefinitions.kt`

Contains:
- `PlatformDef` entries with extensions
- `slugAliases` for platform name normalization
- Display names and sort order
- Local platforms (`localPlatformIdMap`: android, steam, ios) - fixed negative local IDs; local and RomM android are unified onto one platform
- `LocalPlatformIds` declares SIX constants (ANDROID, STEAM, IOS, GOG, EPIC, AMAZON) but `localPlatformIdMap` maps only three. GOG, EPIC and AMAZON resolve to null through `getLocalPlatformId` and false through `isLocalPlatform`, and `getLocalPlatformEntities` never emits them. Adding a store front means adding the map entry AND a `PlatformDef`, not just the id constant.
- `manyToOneSlugs` + `resolveImportSlug` - fs_slug-based re-slugging (arcade split). `resolveImportSlug` also carries a name-based case: a `pico` import whose name matches `pico8NamePattern` re-slugs to `pico8`, keeping Sega Pico and PICO-8 apart.

---

## Platform Reference: Read the Source, Not This File

Do NOT keep extension/core tables in this skill - they duplicate code as doc and drift. For current slugs, extensions, and core routing, read `PlatformDefinitions.kt`, `LibretroCoreRegistry.kt`, and `EmulatorRegistry.kt` directly.

Known traps:
- **C128**: `vice_x128` is REAL - RetroArch's bundled `commodore_c128` info file declares it and the arm64-v8a buildbot ships `vice_x128_libretro_android.so` (verified 2026-07-30). `c128` routes to it in `EmulatorRegistry`.
- **Arcade is split**: RomM `arcade` re-slugs by fs_slug via `manyToOneSlugs` + `resolveImportSlug` in `PlatformDefinitions.kt`; `fbneo` and `mame` are distinct platforms. Defaults in `EmulatorRegistry.kt`: arcade/fbneo/neogeo/cps1-3 -> `fbneo`, mame -> `mame2003_plus`.
- **Arcade ROMs stay zipped** - DO NOT EXTRACT (romset zips are the loadable unit).
- **Local platforms**: android, steam, ios are launcher-local with fixed negative IDs via `localPlatformIdMap`; do not treat them as emulated platforms. They are not uniformly extension-free: `android` carries `setOf("apk", "xapk")`, while `steam` and `ios` are `emptySet()`. Do not "clean up" the android extensions.
- **`vice_x64sc` has two different display names on purpose**: `RetroArchCore("vice_x64sc", "VICE x64 (Accurate)")` in the user-facing core list, and `"vice_x64sc" to "VICE x64sc"` in the map used for on-disk RetroArch folder resolution. The second is upstream-exact and must match RetroArch's own display name; renaming it to match the first breaks path resolution. This is the exception to the "fix the display name" instruction above - check which map you are editing.
- **SwanStation** (verified 2026-11-26 against libretro-core-info `swanstation_libretro.info` @ de2472e24f25, libretro/swanstation @ 631f0fb31b80, and the arm64-v8a buildbot listing, which ships `swanstation_libretro_android.so.zip`). There is no docs.libretro.com page, it returns a 404. The info file lists five firmware files, all optional: `psxonpsp660.bin`, `scph5500.bin`, `scph5501.bin`, `scph5502.bin` and `ps1_rom.bin`. `psxonpsp660.bin` and `ps1_rom.bin` are real values of the `swanstation_BIOS_PathNTSCJ`/`NTSCU`/`PAL` options in `src/libretro/libretro_core_options.h`, so they are not stray entries copied from Beetle. Renderer tokens are `Auto`/`OpenGL`/`Vulkan`/`Software`, case-sensitive. Controller info lives in `retro_set_environment` in `src/libretro/libretro_host_interface.cpp`. Ports 0-1 offer `pads`, which include GunCon. Ports 2-3 offer `pads_mt`, which has no GunCon. The info file sets `savestate_features = "basic"` and has no netplay line, so netplay stays UNSUPPORTED. RetroAchievements lists SwanStation as a supported PlayStation core with no notes (docs.retroachievements.org/general/emulator-support-and-issues.html).

---

## Validation Commands

After making changes, verify:

```bash
# Build to catch compile errors
./gradlew assembleDebug

# Run platform-related tests
./gradlew testDebugUnitTest --tests "*Platform*"
./gradlew testDebugUnitTest --tests "*Emulator*"
```

---

## Example Session

**User**: Add support for Commodore 64

**Assistant**:
1. Fetch libretro VICE docs
2. Identify cores: vice_x64, vice_x64sc, vice_xscpu64, etc.
3. Extract file extensions from docs
4. Check EmulatorRegistry.kt for existing entries
5. Check PlatformDefinitions.kt for extension list
6. Compare and report discrepancies
7. Make corrections
8. Verify against docs again

---

## Anti-Patterns

**DON'T:**
- Assume core names from memory (always verify)
- Invent a core id by analogy with siblings (x64 exists, therefore x128 must) - verify the exact id exists upstream via WebFetch before adding it
- Skip checking file extensions (common source of bugs)
- Confuse core variants (e.g., x64sc is NOT SuperCPU)
- Hardcode platform-specific behavior without checking if flag exists

**DO:**
- Always fetch current documentation
- Cross-reference multiple sources when uncertain
- Test with actual ROM files when possible
- Document source of truth in commit messages
