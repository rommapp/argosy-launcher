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

libretro / RetroArch / core identifiers are NEVER inferred, guessed, or pattern-matched from sibling entries. Every core id, option value token, file extension, save/state format, and BIOS filename is verified against upstream (docs.libretro.com, the core's own repo, RetroArch source) via WebFetch BEFORE it lands in a registry. A plausible-looking core id that does not exist upstream or on the buildbot produces a platform that can never launch - this has happened (an invented `vice_x128` id survived review because it looked like its siblings).

## Verification Sources

### RetroArch Cores
- **Primary**: https://docs.libretro.com/library/{core_name}/
- **Fallback**: https://github.com/libretro/{core_name}

### Standalone Emulators
- Check official GitHub/website for supported formats
- Look for "supported formats" or "file types" documentation

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

### PlatformDefinitions.kt
Location: `app/src/main/kotlin/com/nendo/argosy/data/platform/PlatformDefinitions.kt`

Contains:
- `PlatformDef` entries with extensions
- `slugAliases` for platform name normalization
- Display names and sort order
- Local platforms (`localPlatformIdMap`: android, steam, ios) - non-ROM platforms with fixed local IDs; local and RomM android are unified onto one platform
- `manyToOneSlugs` + `resolveImportSlug` - fs_slug-based re-slugging (arcade split)

---

## Platform Reference: Read the Source, Not This File

Do NOT keep extension/core tables in this skill - they duplicate code as doc and drift. For current slugs, extensions, and core routing, read `PlatformDefinitions.kt`, `LibretroCoreRegistry.kt`, and `EmulatorRegistry.kt` directly.

Known traps:
- **C128**: `c128` is a defined platform with NO core routing anywhere in the tree. There is no `vice_x128` core id in this codebase (a fabricated one previously lived in this skill). Confirm the exact upstream core id before wiring any C128 routing.
- **Arcade is split**: RomM `arcade` re-slugs by fs_slug via `manyToOneSlugs` + `resolveImportSlug` in `PlatformDefinitions.kt`; `fbneo` and `mame` are distinct platforms. Defaults in `EmulatorRegistry.kt`: arcade/fbneo/neogeo/cps1-3 -> `fbneo`, mame -> `mame2003_plus`.
- **Arcade ROMs stay zipped** - DO NOT EXTRACT (romset zips are the loadable unit).
- **Local platforms**: android, steam, ios are launcher-local (no ROM extensions, fixed IDs via `localPlatformIdMap`); do not treat them as emulated platforms.

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
