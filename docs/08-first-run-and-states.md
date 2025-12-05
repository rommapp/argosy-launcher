# First Run & App States

## First-Run Wizard

Linear setup flow on first launch:

```
+----------------------------------------------------------+
|                                                          |
|                     A-LAUNCHER                           |
|                                                          |
|                   Welcome! Let's get                     |
|                   you set up.                            |
|                                                          |
|                   [A] Get Started                        |
|                                                          |
+----------------------------------------------------------+
```

### Step 1: RomM Setup (Optional)

```
+----------------------------------------------------------+
|  SETUP 1/3                                               |
+----------------------------------------------------------+
|                                                          |
|  Connect to RomM Server?                                 |
|                                                          |
|  RomM lets you sync your game library from a             |
|  self-hosted server.                                     |
|                                                          |
|  [A] Yes, connect to RomM                                |
|  [B] Skip, use local files only                          |
|                                                          |
+----------------------------------------------------------+
```

If "Yes":
```
+----------------------------------------------------------+
|  ROMM LOGIN                                              |
+----------------------------------------------------------+
|                                                          |
|  Server URL:                                             |
|  +----------------------------------------------------+  |
|  | https://romm.example.com                           |  |
|  +----------------------------------------------------+  |
|                                                          |
|  Username:                                               |
|  +----------------------------------------------------+  |
|  | admin                                              |  |
|  +----------------------------------------------------+  |
|                                                          |
|  Password:                                               |
|  +----------------------------------------------------+  |
|  | ********                                           |  |
|  +----------------------------------------------------+  |
|                                                          |
|  [A] Connect    [B] Back                                 |
|                                                          |
+----------------------------------------------------------+
```

Connection result:
```
+----------------------------------------------------------+
|  ROMM LOGIN                                              |
+----------------------------------------------------------+
|                                                          |
|  [OK] Connected successfully!                            |
|                                                          |
|  Server: romm.example.com                                |
|  Library: 1,247 games across 12 platforms                |
|                                                          |
|  [A] Continue                                            |
|                                                          |
+----------------------------------------------------------+
```

### Step 2: ROM Storage Path

```
+----------------------------------------------------------+
|  SETUP 2/3                                               |
+----------------------------------------------------------+
|                                                          |
|  Select ROM Storage Folder                               |
|                                                          |
|  This is where your game files will be stored.           |
|  We'll automatically create subfolders for each          |
|  console when syncing from RomM.                         |
|                                                          |
|  Current: /storage/emulated/0/ROMs                       |
|                                                          |
|  [A] Use This Location                                   |
|  [Y] Choose Different Folder                             |
|                                                          |
|  You can set per-system folders later in Settings.       |
|                                                          |
+----------------------------------------------------------+
```

Auto-created structure (when syncing):
```
/storage/emulated/0/ROMs/
├── NES/
├── SNES/
├── Genesis/
├── PlayStation/
├── N64/
└── ... (created as needed)
```

### Step 3: Initial Scan

```
+----------------------------------------------------------+
|  SETUP 3/3                                               |
+----------------------------------------------------------+
|                                                          |
|  Scan for Games?                                         |
|                                                          |
|  We can scan your ROM folder now to find any             |
|  existing games.                                         |
|                                                          |
|  [A] Scan Now                                            |
|  [B] Skip for Now                                        |
|                                                          |
+----------------------------------------------------------+
```

If scanning:
```
+----------------------------------------------------------+
|  SCANNING...                                             |
+----------------------------------------------------------+
|                                                          |
|  Looking for games in /storage/emulated/0/ROMs           |
|                                                          |
|  [=========>                    ] 34%                    |
|                                                          |
|  Found: 127 games                                        |
|  Current: Super Mario World.sfc                          |
|                                                          |
+----------------------------------------------------------+
```

### Setup Complete

```
+----------------------------------------------------------+
|  ALL SET!                                                |
+----------------------------------------------------------+
|                                                          |
|  Your library is ready.                                  |
|                                                          |
|  +------+ +------+ +------+ +------+                    |
|  | SNES | | NES  | | Gen  | | PS1  |                    |
|  |  24  | |  31  | |  18  | |  54  |                    |
|  +------+ +------+ +------+ +------+                    |
|                                                          |
|  [A] Start Playing                                       |
|                                                          |
+----------------------------------------------------------+
```

---

## Empty States

### No Games (Home/Library)

```
+----------------------------------------------------------+
|                                                          |
|                                                          |
|                   No games yet                           |
|                                                          |
|           [A] Scan Local Folder                          |
|           [Y] Sync from RomM                             |
|                                                          |
|                                                          |
+----------------------------------------------------------+
```

### Platform Has No Games

```
+----------------------------------------------------------+
|  LIBRARY                        < N64 >    0 games       |
+----------------------------------------------------------+
|                                                          |
|                                                          |
|              No N64 games found                          |
|                                                          |
|         [A] Scan Folder    [Y] Sync from RomM            |
|                                                          |
|                                                          |
+----------------------------------------------------------+
```

### No Emulator Configured

When launching a game without emulator:
```
+----------------------------------------------------------+
|  NO EMULATOR SET UP                                      |
+----------------------------------------------------------+
|                                                          |
|  No emulator configured for Super Nintendo.              |
|                                                          |
|  Detected emulators:                                     |
|  [*] RetroArch                          [Installed]      |
|  [ ] Snes9x EX+                         [Installed]      |
|                                                          |
|  [A] Use Selected    [Y] Open Settings                   |
|                                                          |
+----------------------------------------------------------+
```

### RomM Not Configured

When trying to sync without RomM:
```
+----------------------------------------------------------+
|  ROMM NOT CONFIGURED                                     |
+----------------------------------------------------------+
|                                                          |
|  Connect to a RomM server to sync your library.          |
|                                                          |
|  [A] Set Up RomM    [B] Cancel                           |
|                                                          |
+----------------------------------------------------------+
```

---

## Offline Mode

When network unavailable or RomM unreachable:

### Indicators

- Status bar shows offline icon
- "Sync" buttons disabled with tooltip
- Remote-only games show "Not Downloaded" badge

### Behavior

| Feature | Online | Offline |
|---------|--------|---------|
| Browse local games | Yes | Yes |
| Play downloaded games | Yes | Yes |
| View RomM library | Yes | Cached only |
| Download from RomM | Yes | No (queued) |
| Sync metadata | Yes | No |
| Scrape artwork | Yes | No (queued) |

### Offline Banner

```
+----------------------------------------------------------+
| [!] Offline - Some features unavailable                  |
+----------------------------------------------------------+
|                                                          |
|  ... normal UI ...                                       |
|                                                          |
+----------------------------------------------------------+
```

### Remote-Only Game (Offline)

```
+------+
|      |
| Game |
| [!!] |  <-- "Not downloaded" indicator
+------+

Selecting shows:
+------------------------------------------+
|  GAME NOT DOWNLOADED                     |
+------------------------------------------+
|  This game is stored on your RomM        |
|  server and hasn't been downloaded yet.  |
|                                          |
|  [A] Queue Download (when online)        |
|  [B] Cancel                              |
+------------------------------------------+
```

---

## Search

### Context-Sensitive

| Current View | Search Scope |
|--------------|--------------|
| Home | Games (all platforms) |
| Library | Games (current platform or all) |
| Apps | Apps only |

### Search UI

Triggered by SELECT (when not in quick menu context):

```
+----------------------------------------------------------+
|  SEARCH GAMES                                            |
+----------------------------------------------------------+
|                                                          |
|  +----------------------------------------------------+  |
|  | mario_                                             |  |
|  +----------------------------------------------------+  |
|                                                          |
|  +------+ +------+ +------+ +------+ +------+           |
|  |Super | |Mario | |Paper | |Mario | |Dr.   |           |
|  |Mario | |Kart  | |Mario | |64    | |Mario |           |
|  +------+ +------+ +------+ +------+ +------+           |
|                                                          |
|  5 results for "mario"                                   |
|                                                          |
+----------------------------------------------------------+
```

### On-Screen Keyboard

Gamepad-navigable keyboard for text input:

```
+------------------------------------------+
| 1 2 3 4 5 6 7 8 9 0                      |
| Q W E R T Y U I O P                      |
| A S D F G H J K L                        |
| Z X C V B N M                            |
|                                          |
| [SPACE]    [BACKSPACE]    [CLEAR]        |
+------------------------------------------+
```

- D-pad navigates keys
- A types character
- B = backspace
- START = confirm/search

---

## Background Behavior

### Current (MVP)

- Solid color based on theme (dark/light mode)
- Background color from theme tokens

### Future Enhancement

- Textured background option
- Dynamic game art from focused platform
- User-selectable background images

```kotlin
// MVP implementation
@Composable
fun AppBackground() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    )
}

// Future: dynamic background
@Composable
fun AppBackground(currentPlatform: Platform?) {
    val backgroundImage = currentPlatform?.randomGameArt()

    if (backgroundImage != null) {
        AsyncImage(
            model = backgroundImage,
            modifier = Modifier
                .fillMaxSize()
                .blur(32.dp)
                .alpha(0.3f)
        )
    } else {
        // Fallback to solid or texture
    }
}
```
