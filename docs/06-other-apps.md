# Other Apps Integration

## Purpose

While focused on emulation, A-Launcher serves as the device's home screen. Users need access to non-gaming apps (streaming, settings, file managers, etc.) without leaving the launcher experience.

## App Categories

### Gaming-Adjacent
- Streaming: Moonlight, Steam Link, Parsec
- Game stores: Play Store, itch.io
- Utilities: RetroAchievements, BIOS checkers

### System
- Settings
- File managers
- Browser

### Media
- YouTube, Netflix, Plex
- Music players
- Podcast apps

## Apps Screen Layout

```
+----------------------------------------------------------+
| APPS                                    [A-Z] [Category] |
+----------------------------------------------------------+
|                                                          |
|  GAMING                                                  |
|  +------+ +------+ +------+ +------+                    |
|  |Steam | |Moon- | |Parsec| |Retro |                    |
|  |Link  | |light |        | |Arch  |                    |
|  +------+ +------+ +------+ +------+                    |
|                                                          |
|  MEDIA                                                   |
|  +------+ +------+ +------+ +------+                    |
|  |You-  | |Netflix| |Plex  | |Spot- |                    |
|  |Tube  |        |        | |ify   |                    |
|  +------+ +------+ +------+ +------+                    |
|                                                          |
|  SYSTEM                                                  |
|  +------+ +------+ +------+                             |
|  |Sett- | |Files | |Chrome|                             |
|  |ings  |        |        |                             |
|  +------+ +------+ +------+                             |
|                                                          |
+----------------------------------------------------------+
```

## App Discovery

### Querying Installed Apps

```kotlin
class AppRepository(
    private val packageManager: PackageManager
) {
    fun getInstalledApps(): Flow<List<AppInfo>> = flow {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val apps = packageManager.queryIntentActivities(intent, 0)
            .map { resolveInfo ->
                AppInfo(
                    packageName = resolveInfo.activityInfo.packageName,
                    label = resolveInfo.loadLabel(packageManager).toString(),
                    icon = resolveInfo.loadIcon(packageManager),
                    activityName = resolveInfo.activityInfo.name
                )
            }
            .filter { !isHiddenApp(it.packageName) }
            .sortedBy { it.label }

        emit(apps)
    }

    private fun isHiddenApp(packageName: String): Boolean {
        return packageName == BuildConfig.APPLICATION_ID ||
               hiddenApps.contains(packageName)
    }
}

data class AppInfo(
    val packageName: String,
    val label: String,
    val icon: Drawable,
    val activityName: String
)
```

### Auto-Categorization

```kotlin
val appCategories = mapOf(
    "gaming" to listOf(
        "com.valvesoftware.steamlink",
        "com.limelight",
        "com.parsec",
        "com.retroarch"
    ),
    "media" to listOf(
        "com.google.android.youtube",
        "com.netflix.mediaclient",
        "com.plexapp.android"
    ),
    "system" to listOf(
        "com.android.settings",
        "com.google.android.apps.nbu.files"
    )
)

fun categorizeApp(packageName: String): String {
    for ((category, packages) in appCategories) {
        if (packages.any { packageName.startsWith(it) }) {
            return category
        }
    }
    return "other"
}
```

## App Card Component

```kotlin
@Composable
fun AppCard(
    app: AppInfo,
    isFocused: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.05f else 1f
    )

    Column(
        modifier = modifier
            .scale(scale)
            .clip(RoundedCornerShape(12.dp))
            .background(if (isFocused) FocusSurface else Surface)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // App icon
        Image(
            bitmap = app.icon.toBitmap().asImageBitmap(),
            contentDescription = app.label,
            modifier = Modifier.size(64.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // App label
        Text(
            text = app.label,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            textAlign = TextAlign.Center,
            overflow = TextOverflow.Ellipsis
        )
    }
}
```

## Launching Apps

```kotlin
class AppLauncher(private val context: Context) {

    fun launch(app: AppInfo): Boolean {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            setClassName(app.packageName, app.activityName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        return try {
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun openPlayStore(packageName: String) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("market://details?id=$packageName")
            setPackage("com.android.vending")
        }
        context.startActivity(intent)
    }
}
```

## Quick Access Integration

The Quick Access overlay includes an "Apps" shortcut:

```
+------------------------------------------+
|  QUICK ACCESS                            |
+------------------------------------------+
|  > Recent Games                          |
|  > Favorites                             |
|  > Apps                    <-- Quick link|
|  ---------------------------------       |
|  > Settings                              |
+------------------------------------------+
```

## Pinned Apps

Users can pin frequently-used apps to Home screen:

```kotlin
@Entity(tableName = "pinned_apps")
data class PinnedApp(
    @PrimaryKey val packageName: String,
    val sortOrder: Int
)

// Home screen shows pinned apps rail
@Composable
fun HomeScreen() {
    // ... game rails ...

    // Pinned apps rail
    if (pinnedApps.isNotEmpty()) {
        AppRail(
            title = "Apps",
            apps = pinnedApps,
            onAppSelected = { appLauncher.launch(it) }
        )
    }
}
```

## App Management

### Hide Apps

```
Apps > Long press > Hide

Hidden apps won't appear in the Apps screen but remain installed.
```

### Customize Category

```
Apps > Long press > Move to category > [Gaming / Media / System / Other]
```

### Pin to Home

```
Apps > Long press > Pin to Home

Adds app to the "Apps" rail on Home screen.
```

## Streaming App Integration

### Moonlight / Steam Link Special Handling

These apps can launch directly into specific games:

```kotlin
// Moonlight deep link
fun launchMoonlightGame(hostId: String, appId: String) {
    val intent = Intent(Intent.ACTION_VIEW).apply {
        data = Uri.parse("moonlight://launch/$hostId/$appId")
    }
    context.startActivity(intent)
}

// Steam Link deep link
fun launchSteamLinkGame(appId: String) {
    val intent = Intent(Intent.ACTION_VIEW).apply {
        data = Uri.parse("steamlink://launch/$appId")
    }
    context.startActivity(intent)
}
```

### Streaming as "Platforms"

Option to treat streaming services as game platforms:

```kotlin
// User can add "Moonlight" as a platform
// Games scraped from Moonlight's game list
// Launching goes through Moonlight

data class StreamingPlatform(
    val id: String,          // "moonlight", "steamlink"
    val name: String,
    val packageName: String,
    val canListGames: Boolean,
    val deepLinkPattern: String?
)
```

## System Settings Shortcuts

Quick access to common Android settings:

```kotlin
val settingsShortcuts = mapOf(
    "wifi" to Settings.ACTION_WIFI_SETTINGS,
    "bluetooth" to Settings.ACTION_BLUETOOTH_SETTINGS,
    "display" to Settings.ACTION_DISPLAY_SETTINGS,
    "sound" to Settings.ACTION_SOUND_SETTINGS,
    "storage" to Settings.ACTION_INTERNAL_STORAGE_SETTINGS
)

fun openSystemSetting(key: String) {
    val action = settingsShortcuts[key] ?: return
    context.startActivity(Intent(action))
}
```

## UI Consistency

All apps use the same visual treatment as games:

- Same card style and sizing
- Same focus animations
- Same navigation patterns
- Consistent with overall launcher aesthetic

This creates a unified experience whether browsing games or apps.
