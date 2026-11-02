---
name: social-service
description: Argosy Social WebSocket service for friends, presence, and social features. Use when implementing social features to ensure correct connection handling.
---

# Argosy Social Service

WebSocket-based service for social features including friends list, presence status, and friend codes.

**Scope**: this skill covers connection handling only - roughly 10% of a wire protocol with ~40+ message types (`MessageTypes` in `SocialModels.kt`). For feature payloads and message handling, read `ArgosSocialService.kt` and `SocialModels.kt` directly.

---

## Architecture Overview

### Two Backend Services (Do Not Confuse)

| Service | Purpose | Protocol | Repository |
|---------|---------|----------|------------|
| **RomM** | Library sync, metadata, downloads, saves | REST API | `RomMRepository` |
| **Social** | Friends, presence, friend codes | WebSocket | `SocialRepository` |

**Critical**: These are SEPARATE services with SEPARATE connection states.

---

## Connection Lifecycle

### Automatic Connection

The social service connects automatically on app startup if the user has linked their account:

```
App Start
    |
SocialRepository.init { attemptAutoConnect() }
    |
If (isSocialLinked && sessionToken != null)
    |
ArgosSocialService.connect(token)
    |
WebSocket -> wss://api.argosy.dev/ws
    |
Auth message sent -> auth_success received
    |
ConnectionState.Connected
```

### Reconnection

- Automatic reconnection with exponential backoff (1s, 2s, 4s... up to 60s max)
- Triggered on WebSocket failure or unexpected close
- Disabled on intentional disconnect or session revocation
- Heartbeat watchdog (`ArgosSocialService.startHeartbeat`): 30s ping interval, 10s pong timeout. An overdue pong or a failed ping send force-fails the connection and schedules a reconnect - this is the recovery path for silently dead sockets
- `SocialRepository.reconnectIfNeeded()` is additionally triggered on screen-on, app foreground, and by `PresenceManager` when presence is needed while disconnected (5s cooldown)

### Connection States

```kotlin
sealed class SocialConnectionState {
    data object Disconnected          // Not linked or logged out
    data object Connecting            // WebSocket connecting
    data class AwaitingAuth(...)      // QR code displayed, waiting for scan
    data class Connected(user)        // Active and ready
    data class Failed(reason)         // Error occurred
}
```

---

## Checking Connection State

### In ViewModel/Repository Code

```kotlin
// Method 1: Boolean check
if (socialRepository.isConnected()) {
    socialRepository.requestFriendCode()
}

// Method 2: State check
if (socialRepository.connectionState.value is SocialConnectionState.Connected) {
    // Safe to use social features
}
```

### In UI State (Recommended)

Combine the connection state into your UI state:

```kotlin
val uiState = combine(
    socialRepository.connectionState,
    // other flows...
) { values ->
    val socialConnection = values[0] as SocialConnectionState
    MyUiState(
        socialConnected = socialConnection is SocialConnectionState.Connected,
        // ...
    )
}
```

Then in Composables:
```kotlin
if (uiState.socialConnected) {
    FriendsTab(...)
}
```

---

## Feature -> Service Mapping

| Feature | Service | Guard |
|---------|---------|-------|
| Friends tab visibility | Social | `socialConnected` |
| Friends list | Social | `socialConnected` |
| Friend code request | Social | `socialRepository.isConnected()` |
| Add friend by code | Social | `socialRepository.isConnected()` |
| Presence updates | Social | Automatic via `PresenceManager` |
| Library sync | RomM | `rommConnected` |
| Downloads | RomM | `rommConnected` |
| Save sync | RomM | `rommConnected` |

---

## Common Operations

### Requesting Friend Code

```kotlin
fun showFriendCodeModal() {
    _modalState.value = FriendCodeModal
    if (socialRepository.friendCode.value == null) {
        socialRepository.requestFriendCode()  // Only sends if connected
    }
}
```

### Adding Friend by Code

```kotlin
fun addFriendByCode(code: String) {
    if (socialRepository.isConnected()) {
        socialRepository.addFriendByCode(code)
    }
}
```

### Play Sessions and Feed Events (Queue-First)

Play sessions and feed events are NOT gated on `isConnected()`. They are written to `PendingSocialSyncEntity` first and drained by `SocialSyncCoordinator` (driven by `SocialSyncWorker`), so they survive offline periods. Guard direct WebSocket requests (friend code, add friend) on connection; queue-first data goes through the pending queue instead. "Guard everything on isConnected" is not the whole pattern anymore.

### Observing Friends List

```kotlin
// SocialRepository exposes:
val friends: StateFlow<List<Friend>>

// In ViewModel, combine with other state:
combine(
    socialRepository.friends,
    socialRepository.connectionState,
    // ...
) { friends, connection, ... ->
    // Build UI state
}
```

---

## Presence System

Presence is handled AUTOMATICALLY by `PresenceManager`. You don't need to manually send presence updates.

### How It Works

`PresenceManager` combines (see `observePresenceChanges` in PresenceManager.kt):
1. `playSessionTracker.activeSession` - Is a game running?
2. `preferencesRepository.userPreferences` - online status / now-playing / linked flags
3. `socialRepository.serviceConnectionState` - Is the socket connected (service layer)?
4. `_screenOn` - screen on/off broadcast receiver

Argosy-hosted netplay is NOT observed here. `netplay_session` is server-owned and reaches presence only via server payloads handled in `ArgosSocialService` - do not wire it into `PresenceManager`.

External-emulator netplay is the exception: only the playing device can see it, so `ExternalNetplayDetector` (`data/netplay/external/`) feeds `PresenceManager`, which sends it as `external_netplay` on `set_presence` with in-game presence. Detection only; it carries no join data.

When any of these change, it automatically sends the appropriate presence:
- `ONLINE` - Connected, not playing (or playing with now-playing hidden)
- `IN_GAME` - Playing a game (if user allows)
- `OFFLINE` - Status disabled or screen off
- While disconnected, nothing is sent at all (last-sent state is reset so the next connect re-sends; `PresenceManager` may trigger `reconnectIfNeeded()` instead)
- `AWAY` exists in `PresenceStatus` but is never sent by this client (render-only for friends' statuses)

### User Privacy Settings

Respect these preferences (handled automatically by PresenceManager):
- `socialOnlineStatusEnabled` - Show online at all?
- `socialShowNowPlaying` - Show which game?

---

## Avatars (one component, one data source)

ALL avatar circles render through `SocialAvatar`
(`ui/components/friends/SocialAvatar.kt`) - never hand-roll an initials circle.
The local user's doodle avatar (`social_avatar_doodle` +
`social_avatar_use_doodle` in `SyncPreferencesRepository`; local-only, server
sync is a later phase) resolves automatically: `ArgosyApp` provides
`LocalUserAvatarState` (userId + active doodle) as a CompositionLocal, and
`SocialAvatar(userId = user.id)` applies the doodle when the id matches.

- New avatar surface: pass `userId` and you are done. Do NOT plumb the doodle
  through another ViewModel; per-screen plumbing is the bug class that caused
  missed surfaces (event view, own-profile view) at initial ship.
- Explicit `avatarDoodle` param still exists and wins over the
  CompositionLocal; only the doodle editor/preview flows should need it.
- Sizes come from `Dimens.avatarXs..avatarXl` tokens.

## Doodle content (one decode path)

ALL doodle rendering - avatars AND feed/post doodles - decodes through
`rememberDecodedDoodle(data)` (`ui/screens/doodle/DoodleCanvas.kt`): guarded,
null on absent or malformed payload; never call `DoodleEncoder.decodeFromBase64`
directly in a composable. Feed-density pixel gaps come from
`CanvasSize.feedPixelGap` (same file), not hand-rolled when-tables. Callers:
`SocialAvatar`, `SocialScreen` DoodleCard, `FeedEventDetailScreen`
EventMediaContent, `PostEditorScreen` DoodleColumn. A null decode renders the
caller's fallback (initials for avatars, empty tile for posts) - malformed
server payloads must never crash a feed.

## Key Files

| File | Purpose |
|------|---------|
| `data/social/SocialRepository.kt` | Main entry point, connection management |
| `data/social/ArgosSocialService.kt` | WebSocket implementation |
| `data/social/PresenceManager.kt` | Automatic presence updates |
| `data/social/SocialModels.kt` | State classes, enums, message types, `Friend` model |
| `data/social/SocialAuthManager.kt` | Account-link auth flow (pending-auth WebSocket) |
| `data/social/SocialApi.kt` | REST endpoints (auth/device-key) |
| `data/sync/SocialSyncCoordinator.kt` | Drains the offline queue (play sessions, feed events) |

Connection state is two-layer: `SocialRepository.connectionState` (`SocialConnectionState`, UI-facing) wraps `SocialRepository.serviceConnectionState` (`ArgosSocialService.ConnectionState`, socket-level). `PresenceManager` reads the service layer.

---

## Common Mistakes

### Wrong: Using RomM state for social features
```kotlin
// WRONG - rommConnected is for library/downloads
if (drawerState.rommConnected) {
    showFriendsTab()
}
```

### Correct: Using Social state
```kotlin
// CORRECT - socialConnected is for friends/presence
if (drawerState.socialConnected) {
    showFriendsTab()
}
```

### Wrong: Not guarding social operations
```kotlin
// WRONG - Will silently fail if not connected
socialRepository.requestFriendCode()
```

### Correct: Check connection first
```kotlin
// CORRECT - Request only sent if connected
if (socialRepository.isConnected()) {
    socialRepository.requestFriendCode()
}
// Note: requestFriendCode() has internal guard, but explicit check is clearer
```

---

## Debugging

### Check Connection State
```kotlin
Log.d(TAG, "Social state: ${socialRepository.connectionState.value}")
Log.d(TAG, "Is connected: ${socialRepository.isConnected()}")
```

### Verify WebSocket
Look for these log tags:
- `ArgosSocialService` - WebSocket events, message sending
- `SocialRepository` - High-level connection state changes
- `PresenceManager` - Presence update triggers
