---
name: design-tokens
description: Argosy design tokens system. Use BEFORE adding colors, dimensions, typography, motion specs, or component defaults to ensure they go in tokens.json (the single source of truth), not as hardcoded literals in Composables.
---

# Design Tokens

All visual constants live in `design-system-docs/tokens.json`. A Node generator (`scripts/gen-tokens.mjs`) emits exactly six Kotlin files into `ui/theme/generated/` (`ColorTokens.kt`, `DimensionTokens.kt`, `TypographyTokens.kt`, `MotionTokens.kt`, `InputTokens.kt`, `ComponentDefaults.kt`) from that single source. Hand-edited Kotlin (`Color.kt` / `Dimens` / `Theme.kt`) consumes the generated values and adds derivations on top.

The system exists because we caught the same bug class repeatedly: a default in `UserPreferences` disagreeing with a default in `BoxArtStyleConfig` disagreeing with a `fromString` fallback. One source ends that class permanently.

---

## Hard rules

1. **Never add a `Color(0xFF...)` literal to a Composable.** If the color isn't already in `ColorTokens`, add it to `tokens.json` first, regenerate, then reference it.
2. **Never add a `*.dp` or `*.sp` magic number to a Composable** if it could fit an existing token slot. Check `DimensionTokens.Spacing` / `Radius` / `Border` / `Icon` / `Dot` / `Layout` first.
3. **Constants only in tokens.json.** If the value is computed from other tokens at runtime (HSV math, palette cascade, mode selection, scale multiplier), it stays hand-written in `Theme.kt` / `Dimens` / `UiScale`. See "Constant vs derivation" below.
4. **Enum-typed component defaults reference enum members by name.** `components.boxArt.cornerRadius` is `"MEDIUM"`, not `8` — the dp value lives in `enums.BoxArtCornerRadius`.
5. **Never edit the files under `ui/theme/generated/` directly.** The next generator run erases your edit. Change `tokens.json`, regenerate.

---

## Files

| Path | Role |
|---|---|
| `design-system-docs/tokens.json` | Source of truth — edit this |
| `design-system-docs/tokens.schema.json` | JSON Schema for IDE autocomplete |
| `scripts/gen-tokens.mjs` | Generator — Node, zero deps |
| `app/src/main/kotlin/com/nendo/argosy/ui/theme/generated/*.kt` | Generated Kotlin (6 files) — never hand-edit |

The generator emits Kotlin only. The design side reads tokens via Penpot, whose token names mirror this tree.

---

## Workflow: adding or changing a token

1. Edit `design-system-docs/tokens.json` in the right bucket
2. Run `node scripts/gen-tokens.mjs`
3. Diff the generated files; verify the change matches intent
4. Reference the new value from your consumer (Theme.kt, a Composable, Dimens, etc.)
5. Commit `tokens.json` AND the generated diffs together — they MUST stay in sync

CI lint should fail if `node scripts/gen-tokens.mjs && git diff --exit-code` is non-empty.

---

## Buckets

### `color.scheme.{dark, light}`
Material 3 role tokens: `primary`, `secondary`, `tertiary`, `surface`, `surfaceVariant`, `background`, `onSurface`, `outline`, `outlineVariant`. Always mode-paired. Consumed by `createDarkColorScheme` / `createLightColorScheme` in `Theme.kt`.

### `color.scheme.debugOverrides.{dark, light}`
Per-mode overrides that win in debug builds. Currently only `primary` (Orange in debug, the regular `scheme.dark.primary` in release).

### `color.semantic.{dark, light}`
Success / warning / info. Mode-paired. Consumed by `SemanticColors` data class through `LocalLauncherTheme.semanticColors`.

### `color.domain`
Narrow-purpose colors where the hue carries meaning: `ratingStar`, `difficulty`, `trophyAmber`, `favoriteStar`, `socialBrand.accent`, `presence.{online, away, offline}`, `battery.low`, `code.background`, `achievementTier.{hardcore, softcore}`, and `completion.{playing, beaten, completed, retired, never}` (each with `{dark, light}`). A value may be a flat hex, a `{ color, alpha }` object, a `{ dark, light }` pair, or a nested group containing any of those.

### `color.accentPresets`
Array of unnamed `{ dark, light }` pairs for the accent-color picker. Empty by default; add entries to expand the preset list.

### `dimension.*`
- `spacing.{xs..xxl}` — `{ base, floor }` pairs; UI-scaled at runtime with a minimum floor in `Dimens`
- `radius`, `border`, `icon`, `dot` — flat integer dp values
- `layout` — component dimensions (game card sizes, header/footer heights, modal widths, settings item min height)
- `elevation` — Material 3 elevation dp scale
- `uiScale` — `{ min, max, default, step }` for the user UI-scale preference

### `typography.*`
Per Material 3 style: `fontFamily`, `fontSize` (sp), `lineHeight` (sp), `fontWeight` (100-900 integer), optional `letterSpacing`. Maps 1:1 to the M3 type scale slots.

### `motion.spring.*` / `motion.tween.*`
Springs are `{ dampingRatio, stiffness }`. Tweens are durations in milliseconds. Generator emits both raw constants and pre-built `AnimationSpec<*>` instances.

### `input.*`
`debounce.*` timings in ms; `scrollPaddingPercent` as a 0-1 float. Not strictly "design tokens" but live here because they're system-wide UI behavior constants.

### `enums.*`
Each enum entry: `{ type, values }`. Types:
- `marker` — values is `string[]` of member names (e.g. `BoxArtBorderStyle: ["SOLID", "GLASS", "GRADIENT"]`)
- `dp` / `px` — values is `{ MEMBER: integer }`
- `alpha` — values is `{ MEMBER: float 0-1 }`
- `aspectRatio` — values is `{ MEMBER: [num, denom] }`
- `alpha+shadow` — values is `{ MEMBER: { alpha, isShadow } }`

Enum member names MUST match the Kotlin enum entries in `data/preferences/UserPreferencesRepository.kt`. The generator validates this when emitting `ComponentDefaults`.

### `components.*`
Per-component default values. Component name → flat field map. Enum-typed fields contain a member name string; the generator looks the field up in `fieldEnumMap` inside the script and emits `EnumName.MEMBER`. Non-enum scalars are numbers, booleans, or strings.

---

## Constant vs derivation

A token is a **constant** — a value with no runtime dependencies. It goes in `tokens.json`.

A **derivation** is a runtime computation over tokens or state. It stays hand-written in `Theme.kt`. Examples:

| Looks like a knob | Actually a derivation |
|---|---|
| `MaterialTheme.colorScheme.primaryContainer` | `toContainerDark(primary)` — HSV math |
| `LauncherThemeConfig.focusGlowColor` | `palette.rawPrimary.copy(alpha = focusGlowAlpha)` |
| `ArgosyPalette.effectivePrimary` | `rawPrimary ?: defaultPrimary` |
| `ArgosyPalette.effectiveSecondary` | `rawSecondary ?: effectivePrimary` (cascade) |
| `BoxArtStyleConfig.accentColor` | `palette.effectivePrimary` |
| `Dimens.spacingMd` | `maxOf(base * uiScale, floor).dp` |
| `BoxArtStyleConfig.glowAlpha` | `BoxArtGlowStrength.MEDIUM.alpha` (flattened enum) |

If a value is computed, do NOT tokenize the output — tokenize its inputs. The `focusGlowAlpha: 0.4` is a token; the resolved `Color` produced by `rawPrimary.copy(alpha = focusGlowAlpha)` is not.

---

## Regenerator gotchas

- **Floats with whole-number values** (`1`, `0`) need an `f` suffix in Kotlin. The generator's `isFloatField` heuristic in `scripts/gen-tokens.mjs` catches field names containing `alpha` / `scale` / `saturation` / `ratio` / `percent`. If you add a new Float-typed field with a different name, extend that list or expect a Kotlin type mismatch.
- **Color alpha** is emitted as 8-digit `Color(0xAARRGGBB)` in Kotlin.
- **Empty arrays** (e.g. `accentPresets: []`) are valid and emit `listOf()` in Kotlin.
- **New enum?** Add it to both `enums.*` AND `fieldEnumMap` / `enumNameMap` in `scripts/gen-tokens.mjs`. The Kotlin import for the enum class also needs to land in the generator's import list inside `emitComponentDefaults`.

---

## Anti-patterns (from the tokenization audit, learned the hard way)

- `private val goldColor = Color(0xFFFFD700)` declared locally in a screen file → use `ColorTokens.Domain.ratingStar`. The audit found this duplicated 3×.
- `Color(0xFF4CAF50)` for "synced" / "online" / "charging" → use `ColorTokens.Semantic.{Dark, Light}.success` or `ColorTokens.Domain.Presence.online` depending on intent. Found 8×.
- `Color(0xFF6366F1)` (Tailwind indigo-500) across social screens → use `ColorTokens.Domain.SocialBrand.accent`. Found 9×.
- `padding(15.dp)` when `spacingMd = 16` exists → use `Dimens.spacingMd`. If `12.dp` recurs (it does), argue for a new `spacingMs = 12` token.
- Adding a knob to `BoxArtStyleConfig` without also adding it to `tokens.components.boxArt` → the data-class default and the JSON default WILL drift, and you have just recreated the bug class this system exists to prevent.
- Editing a file under `ui/theme/generated/` directly → next `node scripts/gen-tokens.mjs` erases the change.

---

## Tertiary color is intentionally absent

`tertiaryColor` was a stored, settable, but never-read user preference (`UserPreferencesRepository.kt:580`). It is intentionally NOT in tokens.json. If you find code referencing or trying to revive a user-selectable tertiary, that is a bug to remove, not a feature to wire up.
