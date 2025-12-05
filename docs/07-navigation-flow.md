# Navigation Flow

## Global Controls

| Input | Action |
|-------|--------|
| START | Open/close main drawer |
| B | Back / Close drawer |
| A | Select / Confirm |

## Search

Context-sensitive search (not a global shortcut - accessed via drawer or view-specific):

| View | Search Scope |
|------|--------------|
| Home | All games |
| Library | Games (current or all platforms) |
| Apps | Apps only |

## Main Drawer (START)

Triggered from any screen. Dims background, slides in from left.

```
+----------------------------------------------------------+
|                                                          |
|  +----------------+                                      |
|  |  A-LAUNCHER    |  [Dimmed current screen]            |
|  +----------------+                                      |
|  |                |                                      |
|  |  > Home        |                                      |
|  |    Library     |                                      |
|  |    Apps        |                                      |
|  |    ─────────   |                                      |
|  |    Settings    |                                      |
|  |                |                                      |
|  +----------------+                                      |
|                                                          |
+----------------------------------------------------------+
```

- D-pad UP/DOWN: Navigate menu items
- A: Select destination
- B or START: Close drawer, return to current screen

---

## Home View (EmulationStation-DE Style)

### Layout

```
+----------------------------------------------------------+
|                                                          |
|  [PLATFORM LOGO]                        [Current Time]   |
|                                                          |
|                                                          |
|           [LARGE BACKGROUND - Selected Game Art]         |
|                                                          |
|                    GAME TITLE                            |
|               Developer | Year | Genre                   |
|                                                          |
|                                                          |
+----------------------------------------------------------+
|  +------+  +------+  +========+  +------+  +------+     |
|  |      |  |      |  | FOCUS  |  |      |  |      |     |
|  | Game |  | Game |  |  Game  |  | Game |  | Game | ... |
|  |      |  |      |  |        |  |      |  |      |     |
|  +------+  +------+  +========+  +------+  +------+     |
+----------------------------------------------------------+
```

### Controls

| Input | Action |
|-------|--------|
| LEFT/RIGHT | Move between games (horizontal scroll) |
| UP/DOWN | Switch platform (with transition animation) |
| A | **Instant launch** (starts emulator immediately) |
| SELECT | Open game details page |
| Y | Context options menu |
| L1/R1 | Jump to prev/next platform (fast switch) |

### Platform Switching

```
UP pressed on "SNES":
  - Fade/slide transition
  - Load "Genesis" game list
  - Focus first game (or remember last focused)
  - Background updates to new game's art

DOWN pressed on "SNES":
  - Load "NES" game list
  - Same transition behavior
```

### Platform Order
- User-configurable sort order in Settings
- Only shows platforms with games
- Can hide empty platforms

---

## Library View (Dense Grid)

### Layout

```
+----------------------------------------------------------+
| LIBRARY          [All v]             [SNES]  < L1  R1 >  |
+----------------------------------------------------------+
|                                                          |
|  +------+ +------+ +------+ +------+ +------+ +------+  |
|  | Game | | Game | | Game | | Game | | Game | | Game |  |
|  |  [C] | |  [C] | |      | |  [F] | |  [C] | |      |  |
|  +------+ +------+ +------+ +------+ +------+ +------+  |
|  +------+ +------+ +------+ +------+ +------+ +------+  |
|  | Game | | FOCUS| | Game | | Game | | Game | | Game |  |
|  |      | |======| |  [C] | |      | |  [F] | |  [C] |  |
|  +------+ +======+ +------+ +------+ +------+ +------+  |
|  +------+ +------+ +------+ +------+ +------+ +------+  |
|  | Game | | Game | | Game | | Game | | Game | | Game |  |
|  |  [F] | |      | |  [C] | |  [C] | |      | |  [F] |  |
|  +------+ +------+ +------+ +------+ +------+ +------+  |
|                                                          |
+----------------------------------------------------------+
| [A] Details  [Y] Options  [X] Filter  [SELECT] Quick Menu|
+----------------------------------------------------------+
```

### Source Indicators

| Icon | Meaning |
|------|---------|
| (none) | Remote-only (not downloaded from RomM) |
| [C] | Synced (downloaded from RomM, playable) |
| [F] | Local-only (found via local scan) |

### Filter Options (X Button)

```
+------------------------------------------+
|  FILTER BY SOURCE                        |
+------------------------------------------+
|  (*) All Games                           |
|  ( ) Playable Only (Local + Synced)      |
|  ( ) Local Only                          |
|  ( ) Synced Only                         |
|  ( ) Remote Only (Not Downloaded)        |
+------------------------------------------+
```

### Controls

| Input | Action |
|-------|--------|
| D-pad | Navigate grid (2D movement) |
| A | **Open game details page** |
| Y | Context options menu |
| X | Open filter menu |
| L1/R1 | Switch platform |
| SELECT | Open quick options menu |

### Quick Options Menu (SELECT)

Context-sensitive overlay:

```
+------------------------------------------+
|  SUPER MARIO WORLD                       |
+------------------------------------------+
|  > Play                                  |
|  > Edit Metadata                         |
|  > Change Emulator        [Snes9x]       |  <-- Per-game override
|  > Toggle Favorite                       |
|  > Hide Game                             |
|  ─────────────────────────────────────   |
|  SNES OPTIONS                            |
|  > Configure Platform Emulator           |
|  > Rescan Platform                       |
+------------------------------------------+
```

---

## Apps View

### Layout

```
+----------------------------------------------------------+
| APPS                                                      |
+----------------------------------------------------------+
|                                                          |
|  +------+ +------+ +------+ +------+ +------+ +------+  |
|  |RetroA| |Moon- | |Steam | |You-  | |Netflx| |Chrome|  |
|  | rch  | |light | |Link  | |Tube  | |      | |      |  |
|  +------+ +------+ +------+ +------+ +------+ +------+  |
|  +------+ +------+ +------+ +------+ +------+ +------+  |
|  |Files | |Setti-| | Plex | |Spotify| |      | |      |  |
|  |      | | ngs  | |      | |      | |      | |      |  |
|  +------+ +------+ +------+ +------+ +------+ +------+  |
|                                                          |
+----------------------------------------------------------+
| [A] Launch  [Y] Options  [SELECT] Organize               |
+----------------------------------------------------------+
```

### Controls

| Input | Action |
|-------|--------|
| D-pad | Navigate grid |
| A | Launch app |
| Y | App options (uninstall, info) |
| SELECT | Organize mode (drag to reorder, create folders) |

### Organization Features
- Drag and drop reorder
- Create folders/categories
- Hide apps
- Pin favorites to top

---

## Game Details View

Accessed from:
- Home: SELECT on a game
- Library: A on a game

### Layout

```
+----------------------------------------------------------+
|                                                          |
|  [Full-screen game artwork / fanart - blurred]           |
|                                                          |
|  +----------------+                                      |
|  |                |    SUPER MARIO WORLD                 |
|  |   [BOX ART]    |    Super Nintendo | 1990             |
|  |                |    Nintendo | Platformer             |
|  |                |    --------------------------------  |
|  +----------------+    Players: 1-2 | Rating: 9.5        |
|                                                          |
|  [A] PLAY    [Y] Favorite    [X] Edit    [SELECT] More   |
|                                                          |
|  DESCRIPTION                                             |
|  Mario and Luigi embark on a quest to save Princess...   |
|                                                          |
|  SCREENSHOTS                                             |
|  +------+ +------+ +------+ +------+                    |
|  |      | |      | |      | |      |                    |
|  +------+ +------+ +------+ +------+                    |
+----------------------------------------------------------+
```

### Controls

| Input | Action |
|-------|--------|
| A | Play game (launches emulator) |
| B | Back to previous view |
| Y | Toggle favorite |
| X | Edit game metadata |
| SELECT | More options (delete, hide, change emulator) |
| D-pad | Navigate sections (if scrollable) |

### More Options Menu (SELECT)

```
+------------------------------------------+
|  MORE OPTIONS                            |
+------------------------------------------+
|  > Change Emulator        [AetherSX2]    |  <-- Shows current, allows override
|  > Rescrape Metadata                     |
|  > View File Info                        |
|  ─────────────────────────────────────   |
|  > Hide Game                             |
|  > Delete from Library                   |
+------------------------------------------+
```

### Change Emulator Flow

When changing emulator from Game Details or Library Quick Menu,
this sets a **per-game override** (only affects this game).

```
+------------------------------------------+
|  SELECT EMULATOR FOR THIS GAME           |
+------------------------------------------+
|  (*) Use platform default (RetroArch)    |  <-- Selecting this RESETS override
|  ─────────────────────────────────────   |
|  ( ) AetherSX2              [Installed]  |
|  ( ) Play!                  [Installed]  |
|  ( ) PCSX2               [Not Installed] |
+------------------------------------------+
|  [A] Confirm    [B] Cancel               |
+------------------------------------------+
```

- Selecting a specific emulator = creates per-game override
- Selecting "Use platform default" = removes override, uses system setting
- Games with active override show [E] badge on cover
- Platform default is configured in Settings > Emulators

---

## Settings View

### Layout

```
+----------------------------------------------------------+
| SETTINGS                                                  |
+----------------------------------------------------------+
|                                                          |
|  +------------------+  +-------------------------------+ |
|  | Categories       |  | ROM Paths                     | |
|  +------------------+  +-------------------------------+ |
|  |                  |  |                               | |
|  |  > Library       |  | Scan Paths:                   | |
|  |    Emulators     |  | /storage/ROMs          [Edit] | |
|  |    Display       |  | /sdcard/Games          [Edit] | |
|  |    Input         |  |                        [Add]  | |
|  |    Network       |  |                               | |
|  |    About         |  | RomM Sync Path:               | |
|  |                  |  | /storage/RomM          [Edit] | |
|  +------------------+  |                               | |
|                        +-------------------------------+ |
+----------------------------------------------------------+
```

### Categories

**Library**
- ROM scan paths (add/edit/remove)
- RomM sync path
- Auto-scan on startup
- Scraper settings

**Emulators**
- Per-platform emulator selection
- Shows: Installed / Not Installed status
- RetroArch core selection
- Default emulator fallback

**Display**
- Theme selection
- Grid density (Library)
- Animation speed
- Background blur intensity

**Input**
- Controller mapping
- Button prompts style
- Vibration feedback

**Network**
- RomM server configuration
- Sync schedule
- WiFi-only sync toggle

**About**
- Version info
- Licenses
- Check for updates

### Emulator Configuration Detail

```
+----------------------------------------------------------+
| EMULATORS > Super Nintendo                               |
+----------------------------------------------------------+
|                                                          |
|  Default Emulator:                                       |
|  +--------------------------------------------------+   |
|  | (*) RetroArch (Snes9x core)           [Installed] |   |
|  | ( ) Snes9x EX+                        [Installed] |   |
|  | ( ) SNES9x                         [Not Installed]|   |
|  +--------------------------------------------------+   |
|                                                          |
|  RetroArch Core:                                         |
|  +--------------------------------------------------+   |
|  | (*) Snes9x                                        |   |
|  | ( ) bsnes                                         |   |
|  | ( ) Mesen-S                                       |   |
|  +--------------------------------------------------+   |
|                                                          |
|  [ ] Use built-in emulation (when available)             |
|                                                          |
+----------------------------------------------------------+
```

---

## Screen Transitions

### Home <-> Platform Switch
- Crossfade background (300ms)
- Game row slides out/in (200ms)
- Platform logo swaps

### Home/Library/Apps Switching (via Drawer)
- Drawer slides closed
- Current screen fades out
- New screen fades in
- Total: ~400ms

### Opening Game Details
- Background zooms slightly
- Detail panel slides up from bottom
- Game row fades/slides down

### Opening Drawer
- Screen dims to 40% brightness
- Drawer slides in from left (250ms)
- Focus moves to first menu item

---

## Focus Memory

Each screen remembers its last focused item:

| Screen | Remembers |
|--------|-----------|
| Home | Last platform + last game per platform |
| Library | Last platform + grid position |
| Apps | Grid position |
| Settings | Last category + last setting |

When returning to a screen, focus is restored to last position.
