# Data Integration

## Data Sources

```
+------------------+     +------------------+
|   Local Storage  |     |   RomM Server    |
|  (SAF / Files)   |     |   (REST API)     |
+------------------+     +------------------+
         |                       |
         v                       v
+------------------------------------------+
|            Repository Layer              |
+------------------------------------------+
         |                       |
         v                       v
+------------------+     +------------------+
|    Room (SQLite) |     |   Media Cache    |
|    Local DB      |     |   (Coil/Disk)    |
+------------------+     +------------------+
```

## Database Schema

### Entity Relationships

```
Platform (1) ----< (N) Game
Collection (N) >----< (N) Game
Game (1) ----< (N) EmulatorConfig
```

### Core Entities

```kotlin
@Entity(tableName = "platforms")
data class PlatformEntity(
    @PrimaryKey val id: String,       // "snes", "genesis", "psx"
    val name: String,                 // "Super Nintendo"
    val shortName: String?,           // "SNES"
    val extensions: String,           // JSON: [".sfc", ".smc", ".zip"]
    val logoPath: String?,
    val sortOrder: Int
)

@Entity(tableName = "games")
data class GameEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val platformId: String,
    val name: String,
    val filePath: String,
    val source: GameSource,           // LOCAL or ROMM
    val rommId: Int?,                 // If from RomM

    // Metadata
    val summary: String?,
    val developer: String?,
    val releaseYear: Int?,
    val genres: String?,              // JSON array

    // Media
    val coverPath: String?,

    // User data
    val isFavorite: Boolean,
    val lastPlayedAt: Long?,
    val playCount: Int,

    // Sync
    val syncState: SyncState,
    val localFileUri: String?
)

@Entity(tableName = "collections")
data class CollectionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val isSmartCollection: Boolean,
    val smartFilter: String?,         // JSON filter
    val sortOrder: Int
)

@Entity(tableName = "emulator_configs")
data class EmulatorConfigEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val platformId: String?,          // null = global fallback
    val gameId: Long?,                // null = platform default, set = per-game override
    val packageName: String,
    val coreName: String?,
    val isDefault: Boolean
)

// Emulator resolution: Game override > Platform default > Global
// To "reset" a game to platform default: DELETE the game-specific row
```

## Local ROM Scanning

### Scanner Architecture

```kotlin
interface RomScanner {
    fun scan(directoryUri: Uri): Flow<ScanEvent>
}

sealed interface ScanEvent {
    data class Progress(val current: Int, val total: Int) : ScanEvent
    data class GameFound(val file: DocumentFile, val platform: String) : ScanEvent
    data class Error(val message: String) : ScanEvent
    data object Complete : ScanEvent
}
```

### Detection Strategy

```
1. User selects directory via SAF picker
2. Scanner traverses directory tree
3. For each file:
   a. Check extension against platform definitions
   b. If ambiguous (.bin, .iso), check file header
   c. If match, emit GameFound event
4. Repository upserts games into database
```

### Platform Detection

```kotlin
// Extension-based (fast path)
val platformExtensions = mapOf(
    ".sfc" to "snes",
    ".smc" to "snes",
    ".nes" to "nes",
    ".gba" to "gba",
    ".md"  to "genesis",
    ".gen" to "genesis"
)

// Header-based (for ambiguous extensions)
val headerSignatures = mapOf(
    "SEGA" to "genesis",
    "NINTENDO" to "n64"
)
```

### Incremental Scanning

```kotlin
class IncrementalScanner {
    suspend fun scan(directoryUri: Uri): ScanResult {
        val existingFiles = gameDao.getLocalFilePaths()
        val currentFiles = listFilesInDirectory(directoryUri)

        val added = currentFiles - existingFiles
        val removed = existingFiles - currentFiles

        // Process additions
        added.forEach { processNewFile(it) }

        // Mark removals
        gameDao.markAsRemoved(removed)

        return ScanResult(added.size, removed.size)
    }
}
```

## RomM Integration

### API Client

```kotlin
interface RommApiService {
    @POST("api/token")
    suspend fun authenticate(
        @Field("username") username: String,
        @Field("password") password: String
    ): TokenResponse

    @GET("api/platforms")
    suspend fun getPlatforms(): List<PlatformResponse>

    @GET("api/roms")
    suspend fun getRoms(
        @Query("platform_id") platformId: Int?,
        @Query("limit") limit: Int,
        @Query("offset") offset: Int
    ): PaginatedResponse<RomResponse>

    @Streaming
    @GET("api/roms/{id}/content/{filename}")
    suspend fun downloadRom(
        @Path("id") romId: Int,
        @Path("filename") filename: String
    ): ResponseBody
}
```

### Sync Strategy

```
Full Sync (manual or scheduled):
1. Fetch all platforms from RomM
2. For each platform, fetch all ROMs (paginated)
3. Upsert into local database
4. Mark removed items

Selective Sync (on-demand):
1. User browses RomM library
2. User selects game to download
3. Download ROM to local storage
4. Update syncState to SYNCED
```

### Download Manager

```kotlin
@HiltWorker
class RomDownloadWorker(
    context: Context,
    params: WorkerParameters,
    private val rommApi: RommApiService,
    private val gameDao: GameDao
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val gameId = inputData.getLong("gameId", -1)
        val game = gameDao.getById(gameId) ?: return Result.failure()

        setProgress(workDataOf("progress" to 0))

        val response = rommApi.downloadRom(game.rommId!!, game.fileName)
        val file = saveToStorage(response, game.fileName) { progress ->
            setProgress(workDataOf("progress" to progress))
        }

        gameDao.updateSyncState(gameId, SyncState.SYNCED, file.uri.toString())
        return Result.success()
    }
}
```

## Metadata Scraping

### Scraper Sources

| Source | Coverage | Rate Limit | Notes |
|--------|----------|------------|-------|
| ScreenScraper | Best for retro | 1 req/sec | Requires account |
| TheGamesDB | Good coverage | 3 req/sec | Free |
| IGDB | Modern games | Varies | Twitch auth |

### Scraping Flow

```
1. Game added to library (no metadata)
2. Background worker picks up game
3. Query scraper APIs with game name + platform
4. If match found:
   a. Download cover image
   b. Store metadata in database
5. If no match:
   a. Mark as "needs manual match"
   b. User can search and select manually
```

### ScreenScraper Integration

```kotlin
interface ScreenScraperApi {
    @GET("api2/jeuInfos.php")
    suspend fun getGameInfo(
        @Query("devid") devId: String,
        @Query("devpassword") devPassword: String,
        @Query("softname") softName: String,
        @Query("output") output: String = "json",
        @Query("romnom") romName: String,
        @Query("systemeid") systemId: Int
    ): ScreenScraperResponse
}

// Platform ID mapping
val screenScraperPlatformIds = mapOf(
    "snes" to 4,
    "genesis" to 1,
    "nes" to 3,
    "gba" to 12,
    "psx" to 57
)
```

## Media Caching

### Coil Configuration

```kotlin
val imageLoader = ImageLoader.Builder(context)
    .memoryCache {
        MemoryCache.Builder()
            .maxSizePercent(context, 0.20)
            .build()
    }
    .diskCache {
        DiskCache.Builder()
            .directory(context.cacheDir.resolve("covers"))
            .maxSizeBytes(256 * 1024 * 1024)  // 256MB
            .build()
    }
    .crossfade(true)
    .build()
```

### Image Sizing

```kotlin
enum class CoverSize(val maxDimension: Int) {
    THUMBNAIL(150),   // Grid view
    CARD(300),        // Rail view
    DETAIL(600),      // Detail screen
    HERO(1280)        // Hero banner
}
```

## Repository Pattern

```kotlin
interface GameRepository {
    fun getAllGames(): Flow<List<Game>>
    fun getGamesByPlatform(platformId: String): Flow<List<Game>>
    fun getRecentGames(limit: Int): Flow<List<Game>>
    fun getFavorites(): Flow<List<Game>>
    fun searchGames(query: String): Flow<List<Game>>

    suspend fun addLocalGame(file: DocumentFile, platformId: String)
    suspend fun syncFromRomm()
    suspend fun downloadGame(gameId: Long)
    suspend fun updateFavorite(gameId: Long, isFavorite: Boolean)
    suspend fun recordPlaySession(gameId: Long, durationMinutes: Int)
}

class GameRepositoryImpl(
    private val gameDao: GameDao,
    private val rommApi: RommApiService,
    private val scraper: MetadataScraper
) : GameRepository {
    // Implementation combines local + remote sources
}
```

## Offline-First Strategy

```
1. Always read from local database
2. Sync remote data in background
3. Show stale data with "syncing" indicator
4. Handle conflicts with "last write wins"
5. Queue writes when offline, replay when online
```
