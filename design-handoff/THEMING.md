# Theming

Decision record for user-facing theming. Sits beside CONTROL-FOUNDATIONS.md: that doc
defines the system language; theming is user expression WITHIN that language.

## Position (2026-07-03)

- No ES-DE-style layout engine. Screens are compiled Compose wired to the custom input
  model (dual-modality, InputHandler focus, guide bar, dual-screen, TV/handheld scale);
  arbitrary community layouts cannot carry those guarantees. Argosy is a console UI:
  themes mean color, surface, sound, and presentation - not element positioning.
- Themes ship as PACKS: a named, shareable JSON bundle (plus optional assets) of the
  knobs that already exist. Import/export; ship 3-4 first-party presets. Community
  trades files, not code.

## Tiers

1. Theme packs (committed direction): accents, box-art style, background config,
   sounds, density - the existing UserPreferences knobs, bundled and serialized.
   Prerequisite: the Phase 2 primitive port designs `LocalArgosyTheme` so every field
   resolves `user-theme value ?: token default` from day one; retrofitting runtime
   resolution later means touching every consumer twice.
2. Asset packs: per-platform / per-screen backdrops, platform icon packs, font family
   (issue #60), sound packs. Runtime-loaded through the same pack format; the app
   already loads covers, screenshots, and video wallpaper at runtime.
3. Curated layout variants (post-V2 possibility): first-party alternates (hero / grid /
   carousel home), each hand-built with full input wiring, selectable per pack. This is
   the ceiling of layout theming; there is no tier 4.

## Surface backdrops (the "feel" layer)

User-set decorative treatment per ALLOWED surface role: solid color, gradient, or a
GRID-LATTICE pattern. Initial allowlist: dual-screen companion background, settings/menu
screen background. Grow the allowlist deliberately.

The pattern engine is a grid lattice with three independent, togglable layers (2026-07-03):

- CELL layer: a shape inside each grid cell (user-selected shape set, scale, rotation
  jitter).
- VERTEX layer: icons at the grid corners (user-selected icon set).
- EDGE layer: a line effect along cell edges between vertices (solid / dashed / faded).

Each layer has its own toggle, tint, and alpha (all within the surface cap); all layers
share one stored seed. Presets (dots, scanlines, hex, icon grid) are just saved layer
configs.

Cell/vertex content sources: built-in shape sets, icon sets, and USER-DRAWN stamps via
the existing social doodle canvas reused as a stamp editor. The doodle is a PIXEL canvas
(pixels: Map of coords -> DoodleColor, fixed sizes) - stamps store that pixel map
directly (tiny), render as a monochrome MASK (any drawn pixel = on; the pattern tint
owns the color, drawn colors are ignored), and scale nearest-neighbor so they stay
crisp. Retro-native by construction. Stamps ship inside the pack as assets.

Guardrails:

- Pattern renders on BASE/background surfaces only, never raised/elevated panels; the
  elevation ramp and the 0.15-0.18 focus washes must stay legible above it.
- Opacity caps per surface role: content surfaces <= 0.15; wallpaper-role surfaces
  (companion background) may go higher. Caps live in tokens.json
  (`components.surfaceBackdrop`), not literals.
- Pattern is monochrome - one tint (surface tint or accent) at low alpha. Never
  full-color glyphs in chrome.
- Seed-deterministic randomness: the seed is stored; reshuffle only on explicit user
  action, never per recomposition.
- Static; no pattern animation. Parallax/drift is a separate future decision.
- Implementation: render one tile offscreen and repeat via ShaderBrush/ImageShader,
  not per-icon draws each frame.

## Surface tint ramp + bleed (2026-07-03)

The original backdrop intent: users tune the FEEL of chrome without hand-picking hexes.

- User picks a hue; the neutral elevation ramp derives from it (deep navy / purple /
  warm charcoal families). Contrast stays guaranteed because only the hue moves, never
  the lightness steps.
- A "color bleed" slider tunes how strongly the primary/accent tints surfaces (and
  optionally text tint). CLAMPED: a max chroma/saturation cap so chrome can never
  collapse into one hue or wash out legibility. Free-form full palettes stay out of
  scope (the ES-DE contrast lottery).

## Fonts (2026-07-03)

Decision: user-provided fonts ship in the FIRST font-customization release (no
curated-only phase; do not half-ass it). Curated bundled fonts exist as defaults and
examples, not as the ceiling.

- Two slots: DISPLAY (titles, headers, eyebrows) and BODY (UI text). Personality lives
  in display; body warns harder on risky choices.
- Formats: TTF and OTF only, declared in the UI. File picker limited accordingly (SAF;
  extension filter + parse validation on import - reject anything Typeface cannot load,
  with a plain error).
- The Font Customization UI ships guidelines (10-foot legibility, weight coverage
  400/500/600, CJK per-glyph fallback behavior), a live preview rendering real UI
  samples at title/body sizes and weights, and a one-tap revert to Default.
- Row-height tolerance is tight post-density (40dp rows): preview must include a
  focused menu row so clipping/mis-centering is visible before the user commits.

## Sounds (2026-07-03)

- Sound packs (nav tick / confirm / back / launch samples) ride the pack format.
- PREREQUISITE: sound manager rework. Rapid successive replays are currently broken
  (fast d-pad nav); move to pre-loaded, polyphonic playback (SoundPool-style streams
  with per-sound stream limits) BEFORE packs land, and improve the base sample set as
  part of that rework.

## Candidate knobs (evaluated 2026-07-03, unscheduled)

- Focus treatment intensity: promote focusGlowAlpha / halo tint to pack fields.
- Placeholder-cover theming: no-art cards inherit pack tint + pattern (reuses the
  backdrop engine).
- Issue #60 fields: glow intensity steps, border palette modes
  (complementary/triadic/analogous), per-view grid spacing.

## Settings IA restructure (2026-07-07)

Interface was one flat section holding four concerns; the theming roadmap does not fit
in it. Decision: split identity from activity, and embrace sub-menus wherever a concern
has depth.

**Theme (new top-level section)** - the identity layer, future home of packs:
- Mode (dark/light/auto); colors: accent, secondary, tint ramp + bleed sliders inline
- Surface Backdrop -> subsection (phase 1 presets; phase 2 builder + tile creator)
- Fonts -> subsection (user TTF/OTF import, validated, preview + revert)
- Sounds -> subsection (UI sounds toggle/volume/per-sound customize; sound sets later)
- Box Art -> moves here (visual identity)
- Packs (future): Save-as-pack / Apply-pack rows at section top when tiers land

**Interface (remaining, compartmentalized by activity):**
- Layout: grid density, UI scale, Home Screen behavior
- Screen safety: dimmer trio
- Ambience: BGM group (content you listen to, not identity - stays apart from theme sounds)
- Displays: dual-screen, display roles, Ambient LED (hardware behavior; palette-fed but not identity)

Mechanics: nav-row-to-subsection pattern already exists (Box Art, Ambient LED); Theme is
a new SettingsSection entry plus row redistribution, not new machinery.

## Tile creator (2026-07-07)

The backdrop tile/doodle drawing tool paints in GREYSCALE ONLY, exactly 5 steps from
black to white (black, dark, mid, light, white). Tiles are luminance masks: the theme
tint ramp colors them at render time, so every tile works under every accent and both
modes by construction. No per-tile color data is ever stored.

## Surface tint: single knob + value lift (2026-07-07, supersedes the two-slider cut)

Shipped and revised on device:
- ONE control: "Surface Tint" (0-100%), tinting chrome toward the ACCENT hue. The
  separate hue slider is removed - at bleed 0 it visibly did nothing (hue x zero
  saturation), and chrome-hue-differs-from-accent is a niche served later, if ever,
  by a small preset row (Chrome: Accent/Warm/Cool/Neutral), never a raw hue slider.
- The "lightness never moves" rule is amended: saturation on a V=0.03 near-black is
  a rounding error, so the tint applies a dark-weighted VALUE LIFT:
  v' = v + fraction * valueLiftRatio * (1 - v), valueLiftRatio = 0.12 (tokens.json,
  components.surfaceTint). Real deep-navy chrome is V~0.12-0.16 WITH saturation, not
  black plus saturation. Light surfaces (V~0.95) are barely lifted and tint via pure
  pastel saturation. Step ORDER and approximate spacing are preserved; contrast against
  V~0.9 text remains enormous at any setting. Saturation stays capped
  (maxSaturationRatio 0.3). Tint 0 remains byte-identical to the neutral ramp.

## Backdrop on Home (2026-07-08)

The allowlist grows to the HOME screen, as a user-chosen background mode:
- "Game Art" (current behavior) vs "Pattern" (the backdrop grid as the Home background).
- In Game Art mode the pattern ALSO renders as the permanent underlayer beneath the
  screenshot/art layer: art fades in over the pattern instead of a black flash while
  loading, and games without art inherit a designed background for free.
- Home uses the content-role alpha cap (it sits under real content, not wallpaper).
- Mode row lives with the Home Screen settings (Interface > Layout > Home Screen).

## Backdrop motion (2026-07-08, supersedes "Static; no pattern animation")

Optional, deliberately subtle motion on the backdrop pattern:
- Motion knob: Off (default) / Drift / Sway.
- Drift: slow directional scroll. Direction is a free angle (0-360 degrees, 0 = up,
  clockwise, default 45) picked on a compass-ring modal: d-pad steps 15/45 degrees,
  analog stick and touch drag set the dot directly, A commits, B cancels.
- Speed: percent slider (25-200, step 25, default 100) multiplying the designed
  token base; drift speed scales up, sway period scales down.
- Sway: gentle Lissajous wander (two out-of-phase sinusoids at incommensurate
  frequencies) - reads as slightly random, amplitude well under one cell.
- Implementation: translate the cached tile shader's local matrix per frame; the
  tile bitmap is never regenerated. Drift animates each axis as its own loop of
  exactly one tile length so wraps land on the lattice at any angle.
- Gated on the motion tier: Reduced renders static regardless of the knob. The tier comes
  from `ProvideArgosyThemeLocals`, which provides `LocalMotionTier` through
  `resolveMotionTier`: Reduced when Settings > Interface > Reduced motion is on or Android's
  animator scale is zero, Comfortable otherwise.
- Base speeds/amplitudes live in tokens.json components.surfaceBackdrop.

## Backdrop scatter + scale jitter + density spread (2026-07-08)

The perfect lattice is too uniform; texture knobs:
- Density becomes a STEPPER (Int level), replacing the 3-value enum: cell size maps
  across a wide range (~16dp dense micro-texture to ~120dp sparse), min/max/step as
  token values. The enum is unreleased - delete it, no migration.
- Scatter (0-100%): seeded per-shape position offset from the grid, up to ~0.35 cell
  at full. Applies to cell shapes and vertex icons.
- Scale Jitter (0-100%): per-shape size variance (0 = uniform); rotation jitter rides
  the same intensity (jitter% of +/-30 deg). Replaces the fixed internal constants.
- Speed/scatter/jitter magnitudes are token ratios (components.surfaceBackdrop).
- TILING RULE: per-shape randomness derives from WRAPPED lattice coordinates (hash of
  i.mod(cells), j.mod(cells), seed) so overflow-ring copies match exactly; the cell
  layer gains the same inclusive ring the vertex layer has. Sequential Random draws
  are forbidden - they break seam continuity.
