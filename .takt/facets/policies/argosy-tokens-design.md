# Argosy tokens and design policy

Select for changes to composables, theme, tokens or visual styling.
Sources: design-tokens, code-quality and ui-design-direction skills. Blocking unless marked.

## Tokens

- Dimensions, text sizes, colors and motion come from tokens: `Dimens`, theme typography,
  `ColorTokens`, `MaterialTheme.colorScheme`, `LocalLauncherTheme.semanticColors`, `MotionTokens`.
  `Color(0x...)`, `N.sp`, and `N.dp` (other than `0.dp`) in UI code are REJECT, including in
  `libretro/ui/` and other non-`ui/` composables.
- Bespoke `tween(durationMillis = N)` or `spring(stiffness = N)` instead of motion tokens is REJECT.
  New animation respects reduced motion through the `Motion.kt` helpers (`reducedMotion()`,
  `motionSpec`, `motionDuration`, `motionEnter`/`motionExit`, `loopingFloat`, `animateModalBlur` with
  `modalScrim`) or `alwaysCrossfade` for Coil requests. A new
  `LocalMotionTier.current == MotionTier.Reduced` comparison is REJECT; use `reducedMotion()`.
- A new color never goes into `ALauncherColors`.
- Files under `ui/theme/generated/` are never hand-edited. A `tokens.json` change ships with its
  regenerated output, and the reverse.
- A new token has a consumer, and a runtime-derived value is not tokenized.
- A `tokens.json` enum field holds a member name, not a number. A new enum is added to `enums.*`,
  `fieldEnumMap`/`enumNameMap` and the generator's import list, with member names matching Kotlin.
- A new Float token field name contains alpha, scale, saturation, ratio or percent, or the
  generator's `isFloatField` is extended.
- A new `BoxArtStyleConfig` knob has a matching `tokens.components.boxArt` entry.
- A user-selectable tertiary color is not read, wired or added to tokens.
- Glow uses `BlurMaskFilter`, never stacked `drawRect`, and box-art focus glow stays in
  `BoxArtFrame.kt`.

## Visual language

- A change to an existing screen conforms to its siblings: no new palette, type pairing or visual
  language.
- Colors work in both light and dark themes.
- Menu rows are 40dp (52dp for two lines) through tokens; enums use filled triangles, not text
  chevrons; toggles use the boxy track, not a Material `Switch`.
- Inline affordances are visible on every row, not only the focused one.

## Copy

- New copy is sentence case, uses plain verbs and user-facing nouns, never system internals.
- The action name stays the same through its flow ("Sync" then "Synced").
- Errors say what went wrong and how to fix it, without apology or vagueness.
- Counted text uses `<plurals>` with `pluralStringResource`, never "item(s)" or a singular/plural
  branch.
- A new string whose word is ambiguous to a translator (Save, State, Play, Frame, Core, Channel,
  Collection) carries an XML translator comment.
- A new key lives in the `strings_<area>.xml` owning the screen, named `<area>_<component>_<role>`.
- Identical English at two usage sites gets two keys. Merging them is REJECT.

## Suggestions (never blocking)

- Platform lists sort chronologically within manufacturer.
