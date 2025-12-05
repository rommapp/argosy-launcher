# Launcher Setup

Configure your app as an Android launcher (home screen replacement).

---

## Quick Reference

| Intent Filter | Purpose |
|---------------|---------|
| `android.intent.category.HOME` | Register as home screen |
| `android.intent.category.DEFAULT` | Required for HOME |
| `android.intent.category.LEANBACK_LAUNCHER` | Android TV launcher |

---

## AndroidManifest Configuration

### Basic Launcher

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <application
        android:name=".LauncherApp"
        android:label="@string/app_name"
        android:icon="@mipmap/ic_launcher"
        android:theme="@style/Theme.Launcher">

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:launchMode="singleTask"
            android:stateNotNeeded="true"
            android:configChanges="orientation|screenSize|screenLayout|keyboardHidden">

            <!-- Standard launcher intent -->
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.HOME" />
                <category android:name="android.intent.category.DEFAULT" />
            </intent-filter>

        </activity>

    </application>

</manifest>
```

### TV Launcher (Leanback)

```xml
<activity
    android:name=".MainActivity"
    android:exported="true"
    android:launchMode="singleTask"
    android:stateNotNeeded="true"
    android:configChanges="orientation|screenSize|screenLayout|keyboardHidden">

    <!-- Standard launcher -->
    <intent-filter>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.HOME" />
        <category android:name="android.intent.category.DEFAULT" />
    </intent-filter>

    <!-- Android TV launcher -->
    <intent-filter>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.LEANBACK_LAUNCHER" />
    </intent-filter>

</activity>
```

### Handheld Game Launcher (Retroid, etc.)

For devices that have both touch and gamepad:

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <!-- Gamepad support (optional, not required) -->
    <uses-feature
        android:name="android.hardware.gamepad"
        android:required="false" />

    <!-- Touchscreen (optional for TV compatibility) -->
    <uses-feature
        android:name="android.hardware.touchscreen"
        android:required="false" />

    <!-- Vibration for haptics -->
    <uses-permission android:name="android.permission.VIBRATE" />

    <application ...>

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:launchMode="singleTask"
            android:stateNotNeeded="true"
            android:windowSoftInputMode="adjustPan"
            android:configChanges="orientation|screenSize|screenLayout|keyboardHidden|navigation">

            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.HOME" />
                <category android:name="android.intent.category.DEFAULT" />
            </intent-filter>

        </activity>

    </application>

</manifest>
```

---

## Activity Attributes

| Attribute | Value | Purpose |
|-----------|-------|---------|
| `launchMode` | `singleTask` | Only one instance, reuse existing |
| `stateNotNeeded` | `true` | Don't save state on kill |
| `configChanges` | See below | Handle changes without restart |
| `windowSoftInputMode` | `adjustPan` | Keyboard doesn't resize layout |

### configChanges

Handle these without activity restart:
```xml
android:configChanges="orientation|screenSize|screenLayout|keyboardHidden|navigation"
```

---

## Setting as Default Launcher

### Programmatically (Request)

```kotlin
fun requestDefaultLauncher(context: Context) {
    val intent = Intent(Intent.ACTION_MAIN).apply {
        addCategory(Intent.CATEGORY_HOME)
    }
    context.startActivity(Intent.createChooser(intent, "Select Launcher"))
}
```

### Via ADB (Testing)

```bash
# List available launchers
adb shell pm query-intent-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME

# Clear default launcher
adb shell pm clear-default-browser-status

# Or clear package preferences
adb shell pm set-home-activity com.yourpackage/.MainActivity
```

### Disable Stock Launcher (Advanced)

```bash
# Disable Google TV launcher
adb shell pm uninstall -k --user 0 com.google.android.tvlauncher

# Disable stock Android TV launcher
adb shell pm uninstall -k --user 0 com.google.android.leanbacklauncher

# Re-enable if needed
adb shell pm install-existing com.google.android.tvlauncher
```

---

## Permissions

### Common Launcher Permissions

```xml
<!-- Query installed apps -->
<uses-permission android:name="android.permission.QUERY_ALL_PACKAGES" />

<!-- Haptic feedback -->
<uses-permission android:name="android.permission.VIBRATE" />

<!-- Storage for ROMs/media -->
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />

<!-- For Android 11+ scoped storage -->
<uses-permission android:name="android.permission.MANAGE_EXTERNAL_STORAGE"
    tools:ignore="ScopedStorage" />

<!-- Network for cover art/metadata -->
<uses-permission android:name="android.permission.INTERNET" />

<!-- Install packages (for sideloading) -->
<uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />
```

### Package Visibility (Android 11+)

```xml
<queries>
    <!-- Query all packages (launcher needs this) -->
    <intent>
        <action android:name="android.intent.action.MAIN" />
    </intent>

    <!-- Or specific package queries -->
    <package android:name="org.ppsspp.ppsspp" />
    <package android:name="com.retroarch" />
</queries>
```

---

## Querying Installed Apps

```kotlin
@SuppressLint("QueryPermissionsNeeded")
fun getInstalledApps(context: Context): List<AppInfo> {
    val pm = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN).apply {
        addCategory(Intent.CATEGORY_LAUNCHER)
    }

    return pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
        .map { resolveInfo ->
            AppInfo(
                packageName = resolveInfo.activityInfo.packageName,
                label = resolveInfo.loadLabel(pm).toString(),
                icon = resolveInfo.loadIcon(pm)
            )
        }
        .sortedBy { it.label.lowercase() }
}

data class AppInfo(
    val packageName: String,
    val label: String,
    val icon: Drawable
)
```

---

## Launching Apps

```kotlin
fun launchApp(context: Context, packageName: String) {
    val intent = context.packageManager.getLaunchIntentForPackage(packageName)
    if (intent != null) {
        context.startActivity(intent)
    } else {
        Toast.makeText(context, "App not found", Toast.LENGTH_SHORT).show()
    }
}

// Launch with specific activity
fun launchActivity(context: Context, packageName: String, activityName: String) {
    val intent = Intent().apply {
        component = ComponentName(packageName, activityName)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}
```

---

## Launching Emulators with ROMs

```kotlin
fun launchEmulatorWithRom(
    context: Context,
    emulatorPackage: String,
    romPath: String
) {
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(Uri.fromFile(File(romPath)), "application/octet-stream")
        setPackage(emulatorPackage)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    // For Android 7.0+ use FileProvider
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            File(romPath)
        )
        intent.setDataAndType(uri, "application/octet-stream")
    }

    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        // Emulator not installed
    }
}
```

### FileProvider Setup

```xml
<!-- AndroidManifest.xml -->
<provider
    android:name="androidx.core.content.FileProvider"
    android:authorities="${applicationId}.fileprovider"
    android:exported="false"
    android:grantUriPermissions="true">
    <meta-data
        android:name="android.support.FILE_PROVIDER_PATHS"
        android:resource="@xml/file_paths" />
</provider>
```

```xml
<!-- res/xml/file_paths.xml -->
<?xml version="1.0" encoding="utf-8"?>
<paths>
    <external-path name="external" path="." />
    <external-files-path name="external_files" path="." />
</paths>
```

---

## Home Button Handling

Launcher receives home button via intent, not key event:

```kotlin
class MainActivity : ComponentActivity() {

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // User pressed home button while launcher is running
        // Typically: scroll to top, close drawers, reset state
        if (intent.action == Intent.ACTION_MAIN &&
            intent.hasCategory(Intent.CATEGORY_HOME)) {
            handleHomePress()
        }
    }

    private fun handleHomePress() {
        // Reset to home state
        viewModel.resetToHome()
    }
}
```

---

## First-Run Experience

```kotlin
class LauncherViewModel @Inject constructor(
    private val preferences: UserPreferencesRepository
) : ViewModel() {

    val isFirstRun: StateFlow<Boolean> = preferences.isFirstRunComplete
        .map { !it }
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    fun completeFirstRun() {
        viewModelScope.launch {
            preferences.setFirstRunComplete(true)
        }
    }
}

@Composable
fun LauncherApp(viewModel: LauncherViewModel = hiltViewModel()) {
    val isFirstRun by viewModel.isFirstRun.collectAsState()

    if (isFirstRun) {
        FirstRunScreen(onComplete = { viewModel.completeFirstRun() })
    } else {
        HomeScreen()
    }
}
```

---

## Pitfalls

### 1. App doesn't appear in launcher selection

Ensure both `HOME` and `DEFAULT` categories:
```xml
<category android:name="android.intent.category.HOME" />
<category android:name="android.intent.category.DEFAULT" />
```

### 2. Can't query installed apps on Android 11+

Add `QUERY_ALL_PACKAGES` permission or use `<queries>` in manifest.

### 3. Activity recreates on configuration change

Add all relevant `configChanges`:
```xml
android:configChanges="orientation|screenSize|screenLayout|keyboardHidden|navigation"
```

### 4. Home button doesn't return to launcher

Use `launchMode="singleTask"` and handle `onNewIntent`.

### 5. Launcher crashes and device stuck

Always test on device with ADB access to reinstall stock launcher if needed.

---

## Sources

- [Create a Custom Launcher](https://citrusdev.com.ua/how-to-create-a-custom-android-tv-launcher-app/)
- [Android TV Controllers](https://developer.android.com/training/tv/get-started/controllers)
- [Package Visibility](https://developer.android.com/training/package-visibility)
