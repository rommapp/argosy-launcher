# Store Pattern

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                         UI Layer                            │
├──────────────┬──────────────┬──────────────┬───────────────┤
│  HomeScreen  │ LibraryScreen│ GameDetail   │  QuickMenu    │
│              │              │   Screen     │               │
└──────┬───────┴──────┬───────┴──────┬───────┴───────┬───────┘
       │              │              │               │
       ▼              ▼              ▼               ▼
┌─────────────────────────────────────────────────────────────┐
│                      ViewModel Layer                        │
├──────────────┬──────────────┬──────────────┬───────────────┤
│ HomeViewModel│LibraryVM     │GameDetailVM  │ QuickMenuVM   │
└──────┬───────┴──────┬───────┴──────┬───────┴───────┬───────┘
       │              │              │               │
       │   ┌──────────┴──────────────┴───────────────┘
       │   │         OBSERVE (read)
       ▼   ▼
┌─────────────────────────────────────────────────────────────┐
│                     Store Layer (NEW)                       │
│  ┌─────────────────┐  ┌─────────────────┐                  │
│  │ GameDetailStore │  │ AchievementStore│  ...             │
│  │   (cached UI    │  │   (cached       │                  │
│  │    models)      │  │    achievements)│                  │
│  └────────┬────────┘  └────────┬────────┘                  │
└───────────┼─────────────────────┼───────────────────────────┘
            │                     │
            │         FETCH (write)
            ▼                     ▼
┌─────────────────────────────────────────────────────────────┐
│                     Data Layer                              │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐         │
│  │   GameDao   │  │AchievementDao│  │ RomMRepo    │         │
│  └─────────────┘  └─────────────┘  └─────────────┘         │
└─────────────────────────────────────────────────────────────┘
```

## Key Concepts

### 1. Stores Hold Cached UI-Ready Data
```kotlin
data class GameDetailData(
    val game: GameEntity,
    val platformName: String,
    val achievements: List<AchievementUi>,
    val lastRefreshed: Long,
    val isStale: Boolean
)
```

### 2. Prepopulate from List Screens
```kotlin
// In HomeViewModel when loading game cards
games.forEach { game ->
    gameDetailStore.prepopulate(game.id)
}
```

### 3. Observe in Detail Screen
```kotlin
// In GameDetailViewModel
init {
    viewModelScope.launch {
        gameDetailStore.observe(gameId).collect { data ->
            if (data != null) {
                _uiState.update { it.copy(game = data.toUi()) }
            }
        }
    }
}

fun loadGame(gameId: Long) {
    viewModelScope.launch {
        // This is instant if prepopulated, only hits DB if stale
        gameDetailStore.load(gameId)

        // Background refresh for fresh achievements
        gameDetailStore.refreshFromRemote(gameId)
    }
}
```

### 4. Update After Actions
```kotlin
// In GameActionsDelegate
fun toggleFavorite(gameId: Long) {
    viewModelScope.launch {
        gameDao.toggleFavorite(gameId)

        // Update store directly - no need to reload
        gameDetailStore.updateGame(gameId) { game ->
            game.copy(isFavorite = !game.isFavorite)
        }
    }
}
```

### 5. Invalidate on External Changes
```kotlin
// When sync completes or game is updated elsewhere
gameDetailStore.invalidate(gameId)
```

## Benefits

1. **Reduced DB/API hits** - Data loaded once, shared across screens
2. **Instant navigation** - Detail view shows cached data immediately
3. **Consistent state** - All screens see the same data
4. **Stale-while-revalidate** - Show cached data, refresh in background
5. **Simple mental model** - Stores for data, Delegates for actions

## Stores vs Delegates

| Aspect | Store | Delegate |
|--------|-------|----------|
| Purpose | Cache & provide data | Execute business logic |
| State | Holds data in StateFlow | May hold UI state |
| Scope | Shared across screens | Can be shared or per-screen |
| Example | GameDetailStore | GameRatingDelegate |
| Operations | observe, load, invalidate | updateRating, toggleFavorite |
