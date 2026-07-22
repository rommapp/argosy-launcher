# Argosy Launcher

[![Build](https://github.com/nendotools/argosy-launcher/actions/workflows/build.yml/badge.svg)](https://github.com/nendotools/argosy-launcher/actions/workflows/build.yml)
[![Latest Release](https://img.shields.io/github/v/release/nendotools/argosy-launcher)](https://github.com/nendotools/argosy-launcher/releases/latest)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)
[![Android](https://img.shields.io/badge/Android-8.0+-green.svg)](https://developer.android.com/about/versions/oreo)

**A gamepad-first Android launcher for retro gaming handhelds with native RomM integration.**

Sync your entire game library from your self-hosted [RomM](https://github.com/rommapp/romm) server, download games and BIOS files on demand, track your achievements, and play across devices with automatic save sync—all from a controller-native interface designed for Anbernic, Retroid Pocket, Odin, and similar devices.

**[Read the Wiki](https://github.com/rommapp/argosy-launcher/wiki)** for setup guides, emulator configuration, and troubleshooting.

<img width="400" alt="Home Screen" src="https://github.com/user-attachments/assets/931e1f4a-e0a1-46a9-a7ce-e712c0d558cc" />

## Feature Highlights

### Native RomM Client
First-class integration with [RomM](https://github.com/rommapp/romm). Sync your entire library with rich metadata from IGDB—cover art, descriptions, genres, franchises, player counts, and more. Your collection, your server, your handheld.

### Automatic Downloads
Download ROMs and BIOS files directly from your RomM server. Games are automatically sorted by platform. Queue multiple downloads and let Argosy handle the rest.

### Save Sync
Continue playing across devices. Bidirectional save sync with your RomM server keeps your progress in sync automatically. Conflict detection ensures you never lose data.

### RetroAchievements
View your earned achievements and track your progress. Argosy displays achievement data synced from your RomM server, showing what you've unlocked and what's left to earn.

### Collections
Organize your library your way. Create custom collections, or use smart collections like "Top Unplayed", "Recently Added", and "Most Played". Pin your favorites to the home screen for quick access.

### Gamepad-First Design
Built for controllers from the ground up. Navigate your entire library, manage downloads, and launch games without ever touching the screen. Full D-pad, analog stick, and button support.

## Customize Your Experience

### Video Wallpaper
Set YouTube video previews as dynamic backgrounds on the home screen. Watch trailers and gameplay footage while browsing your collection.

### Ambient Audio
Add background music to your browsing experience. Select any audio file to play while navigating your library.

### Display Customization
Fine-tune the visual experience:
- **Themes**: Light, dark, or system-matched
- **Backgrounds**: Blur, saturation, and opacity controls for game art backgrounds
- **Box Art Styles**: Adjust grid density, aspect ratio, and visual effects
- **Sound Effects**: Add audio feedback to navigation with built-in presets or custom sounds

## Smart Emulator Management

### Auto-Detection
Argosy automatically detects installed emulators and assigns them to the appropriate platforms. No manual configuration required for most setups.

### RetroArch Core Selection
When using RetroArch, select specific cores per platform. Argosy manages core selection so you launch with the right emulator every time.

### Multi-Disc Games
PlayStation and other multi-disc games are handled automatically. When you launch a multi-disc game, Argosy presents a disc picker so you can choose where to start.

### Supported Emulators
RetroArch, PPSSPP, DuckStation, AetherSX2, Dolphin, DraStic, melonDS, Mupen64Plus FZ, Pizza Boy, Lime3DS, Azahar, Flycast, Redream, and more. Missing your emulator? [Open an issue](https://github.com/nendotools/argosy-launcher/issues).

## Quick Menu (L3)

Press **L3** (left stick click) anywhere to open the Quick Menu—a fast overlay for discovering games:

- **Search**: Find games across your entire library with fuzzy search
- **Random**: Can't decide what to play? Let Argosy pick for you
- **Most Played**: Jump back into your favorites
- **Top Unplayed**: Highly-rated games you haven't touched yet
- **Recent**: Continue where you left off
- **Favorites**: Quick access to your starred games

## Quick Settings (R3)

Press **R3** (right stick click) anywhere to open Quick Settings—a right-side panel for instant adjustments:

- **Theme**: Switch between light, dark, or system theme
- **Haptics**: Toggle haptic feedback and adjust intensity
- **UI Sounds**: Enable or disable navigation sounds
- **BGM**: Toggle background music on or off

On supported devices (Odin, AYN, Retroid), Quick Settings also includes **Performance Mode** and **Fan Control**.

## More Features

| Feature | Description |
|---------|-------------|
| **Library Filtering** | Browse by genre, player count, franchise, or region |
| **Game Stats** | Track play status, ratings, and community scores |
| **User Ratings** | Rate your games and sync ratings to RomM |
| **Offline Mode** | Full access to downloaded games and cached metadata |
| **Image Caching** | Cache all cover art locally for fast, offline browsing |
| **Steam Games** | Index and launch Steam games installed via GameHub or GameNative |
| **App Launcher** | Quick access to emulators and other apps |
| **In-App Updates** | Update Argosy directly from the app |
| **First-Run Wizard** | Guided setup for new users |
| **Favorites** | Mark games for quick access |
| **Hide Games** | Remove games from view without deleting them |

## Getting Started

1. **Download** the latest APK from [GitHub Releases](https://github.com/nendotools/argosy-launcher/releases/latest)
2. **Install** the APK on your device
3. **Run** Argosy and follow the setup wizard
4. **Connect** to your RomM server (or skip for local-only use)
5. **Sync** your library and start playing

> **New to RomM?** Set up your self-hosted game library at [github.com/rommapp/romm](https://github.com/rommapp/romm)

Updates are handled in-app after initial install.

## Requirements

- Android 8.0 (Oreo) or higher
- Emulators installed for your desired platforms
- A [RomM](https://github.com/rommapp/romm) server (optional, for sync features)

### Target Devices

Argosy is designed for retro gaming handhelds:
- Anbernic (RG35XX, RG556, RG406, etc.)
- Retroid Pocket (RP2+, RP3, RP4, RP5)
- Odin / Odin 2
- AYN handhelds
- Android TV boxes

Works on any Android device, but the gamepad-first interface is optimized for handheld gaming.

## Screenshots

<details>
<summary>View all screenshots</summary>

### Home Screen

<img width="400" alt="Home Screen" src="https://github.com/user-attachments/assets/931e1f4a-e0a1-46a9-a7ce-e712c0d558cc" />

### Library

<img width="400" alt="Library" src="https://github.com/user-attachments/assets/b71a0297-c908-45a2-800c-a05bcc264201" />

### Library Filtering

<img width="400" alt="Library Filtering" src="https://github.com/user-attachments/assets/e9417ee8-a1b9-4f7d-99f0-37587bcfc9b9" />

### Game Details

<img width="400" alt="Game Details" src="https://github.com/user-attachments/assets/7e317d1d-ed5c-4459-a2b9-ac1f2f006789" />

### Download Queue

<img width="400" alt="Download Queue" src="https://github.com/user-attachments/assets/4235e9d6-ed7c-4be7-b20c-dcd5cbef5d3c" />

### Settings

<img width="400" alt="Settings" src="https://github.com/user-attachments/assets/fa925ef9-533a-48ba-ba71-5631297f43d4" />

### Emulator Autoconfig

<img width="400" alt="Emulator Autoconfig" src="https://github.com/user-attachments/assets/188a4296-7cf2-44c0-8501-913dc218bdfe" />

### Built-in Updater

<img width="400" alt="Built-in Updater" src="https://github.com/user-attachments/assets/be190b8a-f090-4387-939c-48ab4eb1369c" />

</details>

## Beta Releases

Subscribe to beta releases for early access to new features. Beta versions may be unstable and introduce breaking changes. Check the [Releases](https://github.com/nendotools/argosy-launcher/releases) page for available beta builds.

## Contributing

Argosy is open source! Contributions, bug reports, and feature requests are welcome.

- [Report a bug](https://github.com/nendotools/argosy-launcher/issues/new)
- [Request a feature](https://github.com/nendotools/argosy-launcher/issues/new)

## License

This project is licensed under the GNU General Public License v3.0 - see the [LICENSE](LICENSE) file for details.
