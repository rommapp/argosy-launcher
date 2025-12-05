# UI Concepts

## Design Philosophy

Inspired by EmulationStation-DE and Steam Big Picture:

- **Immersive**: Full-screen, game art as focal point
- **Platform-centric**: Navigate by console/system
- **Controller-native**: Every action maps to a button
- **Consistent**: Same patterns across all views

---

## View Hierarchy

```
+----------------+
|  Main Drawer   |  <-- START button (always available)
+----------------+
| Home           |
| Library        |
| Apps           |
| Settings       |
+----------------+
```

Four primary views, accessed via the main drawer.

---

## 1. Home View

### Concept
EmulationStation-DE style: large preview, games at bottom, platform switching via UP/DOWN.

### Layout

```
+----------------------------------------------------------+
|                                                          |
|  [SNES LOGO]                                   12:34 PM  |
|                                                          |
|                                                          |
|                                                          |
|        [    FULL-SCREEN GAME BACKGROUND ART    ]         |
|                                                          |
|                                                          |
|                  SUPER MARIO WORLD                       |
|              Nintendo | 1990 | Platformer                |
|                                                          |
+----------------------------------------------------------+
|                                                          |
|   +----+  +----+  +======+  +----+  +----+  +----+       |
|   |    |  |    |  |FOCUS |  |    |  |    |  |    |  ...  |
|   +----+  +----+  +======+  +----+  +----+  +----+       |
|                                                          |
+----------------------------------------------------------+
```

### Visual Details

- **Background**: Full-bleed game fanart/screenshot, slight blur, darkened edges
- **Platform logo**: Top-left, shows current system
- **Game info**: Centered, overlaid on background with text shadow
- **Game strip**: Bottom 20% of screen, horizontal carousel
- **Focused game**: Slightly larger, glow effect, box art visible

### Behavior

| Input | Result |
|-------|--------|
| LEFT/RIGHT | Scroll through games |
| UP | Previous platform (with transition) |
| DOWN | Next platform (with transition) |
| L1/R1 | Jump platforms (fast) |
| A | **Instant launch** (quick play) |
| SELECT | Game details page |
| Y | Context options |

---

## 2. Library View

### Concept
Dense grid for browsing large collections. Platform tabs via shoulder buttons.

### Layout

```
+----------------------------------------------------------+
|  LIBRARY                        < SNES >    123 games    |
+----------------------------------------------------------+
|                                                          |
|  +------+ +------+ +------+ +------+ +------+ +------+   |
|  |      | |      | |      | |      | |      | |      |   |
|  | Game | | Game | | Game | | Game | | Game | | Game |   |
|  +------+ +------+ +------+ +------+ +------+ +------+   |
|                                                          |
|  +------+ +------+ +------+ +------+ +------+ +------+   |
|  |      | |======| |      | |      | |      | |      |   |
|  | Game | |FOCUS | | Game | | Game | | Game | | Game |   |
|  +------+ +======+ +------+ +------+ +------+ +------+   |
|                                                          |
|  +------+ +------+ +------+ +------+ +------+ +------+   |
|  |      | |      | |      | |      | |      | |      |   |
|  | Game | | Game | | Game | | Game | | Game | | Game |   |
|  +------+ +------+ +------+ +------+ +------+ +------+   |
|                                                          |
+----------------------------------------------------------+
|  [A] Details    [Y] Options    [SELECT] Quick Menu       |
+----------------------------------------------------------+
```

### Visual Details

- **Header**: View title, current platform with arrows, game count
- **Grid**: 6 columns (configurable), tight spacing
- **Cards**: Box art only, title on focus
- **Focus**: Scale up, glow, show title below
- **Footer**: Context-sensitive button hints

### Behavior

| Input | Result |
|-------|--------|
| D-pad | 2D grid navigation |
| L1 | Previous platform |
| R1 | Next platform |
| A | **Game details page** |
| Y | Context options |
| SELECT | Quick options menu |

### Quick Options (SELECT)

```
+------------------------------------------+
|  SUPER MARIO WORLD                       |
+------------------------------------------+
|  > Play                                  |
|  > Edit Metadata                         |
|  > Toggle Favorite                       |
|  > Hide Game                             |
|  ─────────────────────────────────────   |
|  SNES OPTIONS                            |
|  > Configure Emulator                    |
|  > Rescan Games                          |
|  > Platform Settings                     |
+------------------------------------------+
```

---

## 3. Apps View

### Concept
Standard app grid, user-organizable.

### Layout

```
+----------------------------------------------------------+
|  APPS                                                     |
+----------------------------------------------------------+
|                                                          |
|  +--------+ +--------+ +--------+ +--------+ +--------+  |
|  |        | |        | |        | |        | |        |  |
|  | Retro  | | Moon-  | | Steam  | | PPSSPP | | Dolphin|  |
|  |  Arch  | | light  | | Link   | |        | |        |  |
|  +--------+ +--------+ +--------+ +--------+ +--------+  |
|                                                          |
|  +--------+ +--------+ +--------+ +--------+ +--------+  |
|  |        | |        | |        | |        | |        |  |
|  |YouTube | |Netflix | | Plex   | |Settings| | Files  |  |
|  |        | |        | |        | |        | |        |  |
|  +--------+ +--------+ +--------+ +--------+ +--------+  |
|                                                          |
+----------------------------------------------------------+
|  [A] Launch    [Y] Info    [SELECT] Organize             |
+----------------------------------------------------------+
```

### Visual Details

- **Cards**: App icon + label below
- **Larger than Library**: More padding, bigger icons
- **User order**: Drag to reorder in organize mode

### Behavior

| Input | Result |
|-------|--------|
| D-pad | Grid navigation |
| A | Launch app |
| Y | App info |
| SELECT | Enter organize mode |

### Organize Mode

```
+----------------------------------------------------------+
|  APPS - ORGANIZE MODE                          [B] Done  |
+----------------------------------------------------------+
|                                                          |
|  +--------+ +--------+ +--------+ +--------+ +--------+  |
|  |  [1]   | |  [2]   | |  [3]   | |  [4]   | |  [5]   |  |
|  | Retro  | |  Moon  | | [HOLD] | | PPSSPP | | Dolphin|  |
|  |  Arch  | | light  | | Steam  | |        | |        |  |
|  +--------+ +--------+ +========+ +--------+ +--------+  |
|                        ^^ Moving                         |
+----------------------------------------------------------+
|  [A] Pick Up/Drop    [Y] Hide    [X] Delete Shortcut     |
+----------------------------------------------------------+
```

---

## 4. Settings View

### Concept
Two-panel layout: categories on left, options on right.

### Layout

```
+----------------------------------------------------------+
|  SETTINGS                                                 |
+----------------------------------------------------------+
|                                                          |
|  +------------------+  +-------------------------------+ |
|  |                  |  |                               | |
|  |  > Library       |  |  LIBRARY SETTINGS             | |
|  |    Emulators     |  |                               | |
|  |    Display       |  |  ROM Scan Paths               | |
|  |    Input         |  |  +--------------------------+ | |
|  |    Network       |  |  | /storage/emulated/0/ROMs | | |
|  |    System        |  |  | /sdcard/Games            | | |
|  |                  |  |  +--------------------------+ | |
|  |                  |  |  [Add Path]                   | |
|  |                  |  |                               | |
|  |                  |  |  RomM Sync Folder             | |
|  |                  |  |  /storage/emulated/0/RomM     | |
|  |                  |  |  [Change]                     | |
|  |                  |  |                               | |
|  +------------------+  +-------------------------------+ |
|                                                          |
+----------------------------------------------------------+
```

### Categories

| Category | Contents |
|----------|----------|
| Library | Scan paths, RomM sync path, auto-scan, scraper settings |
| Emulators | Per-platform emulator selection, core configs |
| Display | Theme mode (Light/Dark/System), Colors (primary/secondary/tertiary customization), grid size, animations |
| Input | Button mapping, prompts, vibration |
| Network | RomM server, sync settings, WiFi-only |
| System | About, licenses, storage, reset |

### Emulator Subview

```
+----------------------------------------------------------+
|  SETTINGS > EMULATORS                                    |
+----------------------------------------------------------+
|                                                          |
|  +------------------+  +-------------------------------+ |
|  |                  |  |                               | |
|  |    NES           |  |  SUPER NINTENDO               | |
|  |  > SNES          |  |                               | |
|  |    Genesis       |  |  Default Emulator:            | |
|  |    Game Boy      |  |  +---------------------------+| |
|  |    GBA           |  |  |(*) RetroArch   [Installed]|| |
|  |    N64           |  |  |( ) Snes9x EX+  [Installed]|| |
|  |    PlayStation   |  |  |( ) Lemuroid [Not Installed]| |
|  |    PSP           |  |  +---------------------------+| |
|  |    ...           |  |                               | |
|  |                  |  |  RetroArch Core:              | |
|  |                  |  |  [Snes9x          v]          | |
|  |                  |  |                               | |
|  +------------------+  +-------------------------------+ |
|                                                          |
+----------------------------------------------------------+
```

---

## Main Drawer

### Concept
Side drawer (not modal), dims background, provides navigation.

### Layout

```
+----------------------------------------------------------+
|  +------------------+                                    |
|  |                  |                                    |
|  |   A-LAUNCHER     |      [Dimmed current view]         |
|  |                  |                                    |
|  |   > Home         |                                    |
|  |     Library      |                                    |
|  |     Apps         |                                    |
|  |     ──────────   |                                    |
|  |     Settings     |                                    |
|  |                  |                                    |
|  |                  |                                    |
|  |                  |                                    |
|  |   v1.0.0         |                                    |
|  +------------------+                                    |
+----------------------------------------------------------+
```

### Visual Details

- **Width**: ~25% of screen
- **Background**: Solid dark with slight transparency
- **Dimming**: Main content at 40% brightness
- **Animation**: Slide in from left (250ms)

### Behavior

| Input | Result |
|-------|--------|
| START | Toggle drawer open/closed |
| UP/DOWN | Navigate menu items |
| A | Go to selected view |
| B | Close drawer |

---

## Theming System

### System Theme Detection

Follows Android system setting (dark/light mode):

```kotlin
val darkTheme = isSystemInDarkTheme()
```

### Color Roles

| Role | Usage |
|------|-------|
| **Primary** | Focus rings, selected items, buttons, key actions |
| **Secondary** | Badges, tags, secondary buttons, progress bars |
| **Tertiary** | Accents, highlights, notifications, warnings |
| **Surface** | Cards, dialogs, drawer background |
| **Background** | Screen background |

### Default Palette

**Primary: Indigo**
```
Dark:   #818CF8 (Indigo 400)
Light:  #4F46E5 (Indigo 600)
```

**Secondary: Teal**
```
Dark:   #2DD4BF (Teal 400)
Light:  #0D9488 (Teal 600)
```

**Tertiary: Orange**
```
Dark:   #FB923C (Orange 400)
Light:  #EA580C (Orange 600)
```

### Dark Theme Colors

```
Background:       #0A0A0B
Surface:          #18181B (Zinc 900)
SurfaceVariant:   #27272A (Zinc 800)
SurfaceElevated:  #3F3F46 (Zinc 700)

Primary:          #818CF8 (Indigo 400)
PrimaryContainer: #3730A3 (Indigo 800)
OnPrimary:        #1E1B4B

Secondary:        #2DD4BF (Teal 400)
SecondaryContainer: #115E59 (Teal 800)
OnSecondary:      #042F2E

Tertiary:         #FB923C (Orange 400)
TertiaryContainer: #9A3412 (Orange 800)
OnTertiary:       #431407

OnBackground:     #FAFAFA (Zinc 50)
OnSurface:        #F4F4F5 (Zinc 100)
TextSecondary:    #A1A1AA (Zinc 400)
TextTertiary:     #71717A (Zinc 500)

Divider:          #3F3F46 (Zinc 700)
Outline:          #52525B (Zinc 600)

FocusGlow:        #818CF8 @ 40% alpha
DimOverlay:       #000000 @ 60% alpha
```

### Light Theme Colors

```
Background:       #FAFAFA (Zinc 50)
Surface:          #FFFFFF
SurfaceVariant:   #F4F4F5 (Zinc 100)
SurfaceElevated:  #E4E4E7 (Zinc 200)

Primary:          #4F46E5 (Indigo 600)
PrimaryContainer: #E0E7FF (Indigo 100)
OnPrimary:        #FFFFFF

Secondary:        #0D9488 (Teal 600)
SecondaryContainer: #CCFBF1 (Teal 100)
OnSecondary:      #FFFFFF

Tertiary:         #EA580C (Orange 600)
TertiaryContainer: #FFEDD5 (Orange 100)
OnTertiary:       #FFFFFF

OnBackground:     #18181B (Zinc 900)
OnSurface:        #27272A (Zinc 800)
TextSecondary:    #52525B (Zinc 600)
TextTertiary:     #71717A (Zinc 500)

Divider:          #E4E4E7 (Zinc 200)
Outline:          #D4D4D8 (Zinc 300)

FocusGlow:        #4F46E5 @ 30% alpha
DimOverlay:       #000000 @ 40% alpha
```

### User Customization

Users can override primary, secondary, and tertiary colors in Settings > Display > Colors.

```
+----------------------------------------------------------+
|  SETTINGS > DISPLAY > COLORS                             |
+----------------------------------------------------------+
|                                                          |
|  Theme Mode:  ( ) Light  (*) Dark  ( ) System            |
|                                                          |
|  ─────────────────────────────────────────────────────   |
|                                                          |
|  Primary Color (focus, selection):                       |
|  [■] Indigo  [ ] Blue  [ ] Purple  [ ] Pink  [Custom]   |
|                                                          |
|  Secondary Color (badges, progress):                     |
|  [ ] Teal  [■] Cyan  [ ] Green  [ ] Emerald  [Custom]   |
|                                                          |
|  Tertiary Color (accents, alerts):                       |
|  [■] Orange  [ ] Amber  [ ] Red  [ ] Rose  [Custom]     |
|                                                          |
|  ─────────────────────────────────────────────────────   |
|                                                          |
|  Preview:                                                |
|  +------+  +------+  +------+                           |
|  |██████|  |██████|  |██████|                           |
|  +------+  +------+  +------+                           |
|  Primary   Secondary  Tertiary                           |
|                                                          |
|  [Reset to Defaults]                                     |
|                                                          |
+----------------------------------------------------------+
```

### Color Presets

| Preset | Primary | Secondary | Tertiary |
|--------|---------|-----------|----------|
| Default | Indigo | Teal | Orange |
| Ocean | Blue | Cyan | Amber |
| Forest | Emerald | Green | Orange |
| Sunset | Orange | Pink | Purple |
| Monochrome | Zinc | Zinc | Zinc |

### Custom Color Picker

For "Custom" option, show HSL picker or hex input:

```
+------------------------------------------+
|  CUSTOM PRIMARY COLOR                    |
+------------------------------------------+
|                                          |
|  [Color wheel / gradient picker]         |
|                                          |
|  Hex: #6366F1                            |
|                                          |
|  [A] Confirm    [B] Cancel               |
+------------------------------------------+
```

### Implementation

```kotlin
data class UserColorPrefs(
    val themeMode: ThemeMode,        // LIGHT, DARK, SYSTEM
    val primaryHue: Float?,          // null = default (Indigo)
    val secondaryHue: Float?,        // null = default (Teal)
    val tertiaryHue: Float?,         // null = default (Orange)
)

enum class ThemeMode { LIGHT, DARK, SYSTEM }

// Generate color scheme from hue
fun colorSchemeFromHue(hue: Float, isDark: Boolean): Color {
    return if (isDark) {
        Color.hsl(hue, 0.85f, 0.70f)  // Lighter for dark theme
    } else {
        Color.hsl(hue, 0.75f, 0.45f)  // Darker for light theme
    }
}
```

---

## Visual Design Tokens

### Typography

```
ViewTitle:     24sp Bold
PlatformName:  20sp SemiBold
GameTitle:     16sp Medium
GameSubtitle:  14sp Regular
Body:          14sp Regular
Caption:       12sp Regular
ButtonHint:    12sp Medium
```

### Spacing

```
ScreenPadding:     48dp
GridGap:           12dp (Library)
CardGap:           16dp (Home strip)
DrawerWidth:       280dp
CardCornerRadius:  8dp
```

### Focus States (Home + Library)

Highlighted game cards have strong visual distinction:

```
HIGHLIGHTED (Focused):
  Scale:        1.1x
  Opacity:      100%
  Saturation:   100%
  Glow:         Primary color @ 40% alpha, 16dp blur
  Border:       2dp Primary color

NON-HIGHLIGHTED:
  Scale:        1.0x
  Opacity:      85%
  Saturation:   80% (20% desaturated)
  Glow:         None
  Border:       None
```

Implementation:
```kotlin
@Composable
fun GameCard(isFocused: Boolean) {
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.1f else 1.0f,
        animationSpec = spring(dampingRatio = 0.7f)
    )
    val alpha = if (isFocused) 1f else 0.85f
    val saturation = if (isFocused) 1f else 0.8f

    Box(
        modifier = Modifier
            .scale(scale)
            .graphicsLayer {
                this.alpha = alpha
            }
            .then(if (isFocused) Modifier.focusGlow() else Modifier)
    ) {
        AsyncImage(
            colorFilter = ColorFilter.saturation(saturation),
            // ...
        )
    }
}
```

### Animations

```
FocusDuration:     150ms
FocusEasing:       Spring (damping 0.7)
DrawerSlide:       250ms
PlatformSwitch:    300ms (crossfade)
ViewTransition:    350ms
```
