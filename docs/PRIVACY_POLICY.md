# Privacy Policy for Argosy Launcher

**Last Updated:** January 2025

## Overview

Argosy Launcher ("the App") is an open-source Android launcher application for gaming handhelds. This privacy policy explains how the App handles user data.

## Data Collection

### Data We Do NOT Collect
- Personal identification information (beyond what you provide to third-party services)
- Location data
- Analytics or usage statistics
- Advertising identifiers
- Contact information

### Data Stored Locally
The following data is stored **only on your device**:

- **User Preferences**: Layout settings, theme preferences, and configuration options
- **RomM Server Credentials**: Connection details for your self-hosted RomM server (if configured)
- **RetroAchievements Credentials**: Username and API token (if logged in)
- **Game Library Cache**: Cached game metadata, achievement data, and ROM hashes
- **Achievement Queue**: Pending achievement unlocks awaiting network connectivity

## Network Communications

### RomM Server (Optional)
If you configure a RomM server connection:
- The App communicates with **your self-hosted RomM server**
- Game metadata, cover art, and save files may be synced
- Credentials are stored locally on your device

### RetroAchievements (Optional)
If you log in to RetroAchievements:
- **Data sent to retroachievements.org**:
  - Your username and authentication token
  - Game session starts (game ID, hardcore/casual mode)
  - Achievement unlocks (achievement ID, timestamp)
  - Periodic session heartbeats
- **Data received**:
  - Your achievement unlock history
  - Game achievement definitions
- RetroAchievements has its own [Privacy Policy](https://retroachievements.org/privacy-policy.php)

### Argosy API (Optional)
If you use enhanced metadata or cheats:
- **Data sent to api.argosy.dev**:
  - Game identifiers (for metadata lookup)
  - No personal information or account data
- **Data received**:
  - Additional game metadata not provided by RomM
  - Cheat codes for supported games
- Requests include standard HTTP headers (IP address visible to server)

### Libretro Buildbot (Optional)
If you download emulator cores:
- Cores are downloaded directly from the official libretro buildbot
- No personal data is transmitted
- Download requests include standard HTTP headers (IP address visible to server)

## Permissions

### QUERY_ALL_PACKAGES
Required to display installed applications in the launcher. This information never leaves your device.

### INTERNET & ACCESS_NETWORK_STATE
Used for connecting to configured servers (RomM, RetroAchievements, libretro buildbot).

### READ/WRITE_EXTERNAL_STORAGE
Used to access game files and save data stored on your device.

## Data Security

- All local data is stored using Android's standard security mechanisms
- Server credentials are stored in encrypted SharedPreferences
- RetroAchievements tokens are stored locally and transmitted over HTTPS
- No data is stored on servers controlled by the App developers

## Third-Party Services

The App integrates with the following optional third-party services:

| Service | Purpose | Data Shared |
|---------|---------|-------------|
| RomM | Game library management | Configured by you (self-hosted) |
| RetroAchievements | Achievement tracking | Username, game sessions, unlocks |
| Argosy API | Metadata and cheats | Game identifiers, IP address |
| Libretro Buildbot | Emulator core downloads | IP address (standard HTTP) |

The App does not integrate with any analytics, advertising, or tracking services.

## Your Rights (GDPR)

### Right to Access
All your data is stored locally on your device. You can access it through the App's settings or by examining the App's data directory.

### Right to Deletion
To delete your data:
- **Local data**: Uninstall the App or clear App data in Android settings
- **RetroAchievements**: Log out in Settings, then delete your account at retroachievements.org if desired
- **RomM**: Disconnect your server in Settings; data on your self-hosted server is under your control

### Data Portability
Game saves and local data are stored in standard formats on your device's storage and can be copied or backed up freely.

### Data Retention
- Local data persists until you uninstall the App or clear App data
- RetroAchievements data is retained by RetroAchievements according to their policies
- We do not retain any user data on our servers (we have no servers)

## Children's Privacy

The App does not knowingly collect data from children under 13. RetroAchievements requires users to be 13 or older.

## Changes to This Policy

Updates to this privacy policy will be reflected in the "Last Updated" date above. Significant changes will be noted in release notes.

## Contact

For privacy concerns, please open an issue at the project's GitHub repository.

## Open Source

Argosy Launcher is open-source software. You can review the complete source code to verify these privacy practices.
