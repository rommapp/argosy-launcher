# A-Launcher: Project Overview

## Vision

A Steam Deck-inspired Android launcher for emulation handhelds (Retroid, Odin, AYN) that provides:

- Immersive, gamepad-first interface
- Unified game library from multiple sources
- Seamless game launching (external + built-in emulation)
- Deep customization of content presentation

## Target Devices

| Device Family | Examples | Screen Size | Input |
|---------------|----------|-------------|-------|
| Retroid | RP2+, RP3, RP4 | 3.5-4.7" | D-pad + sticks |
| Odin | Odin Lite/Base/Pro | 5.98" | D-pad + sticks |
| AYN | Odin 2 | 6" | D-pad + sticks |
| Generic | Android TV boxes | 10-foot | D-pad only |

## Architecture Layers

```
+------------------------------------------+
|           Presentation Layer             |
|  Compose UI, Focus System, Theming       |
+------------------------------------------+
                    |
+------------------------------------------+
|             Domain Layer                 |
|  Use Cases, Models, Business Logic       |
+------------------------------------------+
                    |
+------------------------------------------+
|              Data Layer                  |
|  Room, RomM API, Scrapers, File System   |
+------------------------------------------+
                    |
+------------------------------------------+
|           Emulation Layer                |
|  External Intents | Libretro (Phase 2)   |
+------------------------------------------+
```

## Tech Stack

| Layer | Technology |
|-------|------------|
| Language | Kotlin |
| UI | Jetpack Compose |
| DI | Hilt |
| Database | Room |
| Preferences | DataStore |
| Network | Retrofit + OkHttp |
| Images | Coil |
| Background | WorkManager |

## Project Structure

```
app/src/main/java/com/alauncher/
├── di/                 # Hilt modules
├── data/
│   ├── local/          # Room database, DAOs
│   ├── remote/         # RomM API client
│   ├── scanner/        # Local ROM scanner
│   └── repository/     # Repository implementations
├── domain/
│   ├── model/          # Domain models
│   ├── usecase/        # Business logic
│   └── launcher/       # Game launching
└── ui/
    ├── theme/          # Colors, typography, shapes
    ├── focus/          # Gamepad focus management
    ├── components/     # Reusable composables
    ├── navigation/     # Nav graph
    └── screens/        # Screen composables
```

## Phased Approach

### Phase 1: MVP (Current Focus)
- Steam Deck-style UI
- Gamepad navigation system
- Local ROM scanning
- External emulator launching
- RomM backend integration

### Phase 2: Built-in Emulation
- Libretro JNI bridge
- Embedded cores for retro systems (NES, SNES, Genesis, GBA)
- Save state management
- Shader support

### Phase 3: Polish
- Theme marketplace
- Cloud sync
- RetroAchievements integration
- Advanced collections

## Key Differentiators vs Daijisho

| Feature | Daijisho | A-Launcher |
|---------|----------|------------|
| UI Style | Functional | Immersive/cinematic |
| Navigation | Touch-first | Gamepad-first |
| Emulation | External only | Hybrid (built-in + external) |
| Backend | Local only | Local + RomM |
| Customization | Moderate | Deep theming |

## References

- [Daijisho](https://github.com/TapiocaFox/Daijishou) - Feature reference
- [Lemuroid](https://github.com/Swordfish90/Lemuroid) - Libretro implementation
- [Steam Deck UI](https://store.steampowered.com/steamdeck) - Design inspiration
