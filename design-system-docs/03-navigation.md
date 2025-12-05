# Navigation

Screen navigation patterns for TV/handheld apps with focus preservation.

---

## Quick Reference

| Pattern | Use Case |
|---------|----------|
| NavHost + NavController | Multi-screen navigation |
| ModalNavigationDrawer | Side drawer navigation |
| BackHandler | Intercept back button |
| Focus Stack | Preserve focus across screens |

---

## Compose Navigation Setup

```kotlin
// Screen routes
sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Library : Screen("library")
    data object Settings : Screen("settings")
    data class GameDetail(val gameId: Long) : Screen("game/{gameId}") {
        fun createRoute(id: Long) = "game/$id"
    }
}

// NavHost
@Composable
fun AppNavigation(navController: NavHostController = rememberNavController()) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                onGameSelect = { gameId ->
                    navController.navigate(Screen.GameDetail(gameId).createRoute(gameId))
                }
            )
        }
        composable(
            route = Screen.GameDetail.route,
            arguments = listOf(navArgument("gameId") { type = NavType.LongType })
        ) { backStackEntry ->
            val gameId = backStackEntry.arguments?.getLong("gameId") ?: return@composable
            GameDetailScreen(gameId = gameId)
        }
        composable(Screen.Library.route) { LibraryScreen() }
        composable(Screen.Settings.route) { SettingsScreen() }
    }
}
```

---

## Focus Preservation Across Navigation

### Problem

Focus state is lost when navigating between screens.

### Solution 1: Save/Restore in ViewModel

```kotlin
class HomeViewModel : ViewModel() {
    private val _focusedIndex = MutableStateFlow(0)
    val focusedIndex = _focusedIndex.asStateFlow()

    fun saveFocusedIndex(index: Int) {
        _focusedIndex.value = index
    }
}

// ViewModel persists across navigation if using Hilt
@Composable
fun HomeScreen(viewModel: HomeViewModel = hiltViewModel()) {
    val focusedIndex by viewModel.focusedIndex.collectAsState()

    // Restore focus on return
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(focusedIndex) {
        focusRequester.requestFocus()
    }
}
```

### Solution 2: Navigation SavedStateHandle

```kotlin
class HomeViewModel(savedStateHandle: SavedStateHandle) : ViewModel() {
    var focusedIndex: Int
        get() = savedStateHandle["focusedIndex"] ?: 0
        set(value) { savedStateHandle["focusedIndex"] = value }
}
```

### Solution 3: Focus Stack Pattern

```kotlin
data class FocusSnapshot(
    val screenRoute: String,
    val focusedItemId: String,
    val scrollPosition: Int = 0
)

class FocusStackManager {
    private val stack = ArrayDeque<FocusSnapshot>()

    fun push(snapshot: FocusSnapshot) {
        stack.addLast(snapshot)
    }

    fun pop(): FocusSnapshot? {
        return if (stack.isNotEmpty()) stack.removeLast() else null
    }

    fun peek(): FocusSnapshot? = stack.lastOrNull()
}

// Before navigating away
focusStackManager.push(FocusSnapshot(
    screenRoute = "home",
    focusedItemId = "game_123",
    scrollPosition = listState.firstVisibleItemIndex
))

// When returning
val snapshot = focusStackManager.pop()
if (snapshot != null) {
    listState.scrollToItem(snapshot.scrollPosition)
    focusManager.requestFocus(snapshot.focusedItemId)
}
```

---

## Back Navigation

### BackHandler

```kotlin
@Composable
fun ScreenWithBackHandler(onBack: () -> Unit) {
    var showDialog by remember { mutableStateOf(false) }

    BackHandler(enabled = true) {
        if (showDialog) {
            showDialog = false
        } else {
            onBack()
        }
    }
}
```

### Conditional Back Handling

```kotlin
@Composable
fun LibraryScreen(
    onBack: () -> Unit,
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // Close overlays first, then navigate back
    BackHandler(enabled = true) {
        when {
            uiState.showFilterMenu -> viewModel.closeFilterMenu()
            uiState.showQuickMenu -> viewModel.closeQuickMenu()
            else -> onBack()
        }
    }
}
```

---

## Drawer Navigation

```kotlin
@Composable
fun AppWithDrawer(navController: NavHostController) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = drawerState.isOpen,  // Only swipe when open
        drawerContent = {
            DrawerContent(
                onItemClick = { screen ->
                    scope.launch { drawerState.close() }
                    navController.navigate(screen.route) {
                        popUpTo(Screen.Home.route) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        }
    ) {
        NavHost(navController = navController, ...) {
            // screens
        }
    }
}
```

### Drawer Focus Management

```kotlin
@Composable
fun DrawerContent(
    onItemClick: (Screen) -> Unit,
    focusManager: FocusManager
) {
    val items = listOf(Screen.Home, Screen.Library, Screen.Settings)
    var focusedIndex by remember { mutableIntStateOf(0) }

    Column {
        items.forEachIndexed { index, screen ->
            DrawerItem(
                screen = screen,
                isFocused = index == focusedIndex,
                onClick = { onItemClick(screen) },
                modifier = Modifier
                    .onFocusChanged { if (it.isFocused) focusedIndex = index }
                    .focusable()
            )
        }
    }

    // Handle D-pad in drawer
    LaunchedEffect(Unit) {
        focusManager.events.collect { event ->
            when (event) {
                GamepadEvent.Up -> focusedIndex = (focusedIndex - 1).coerceAtLeast(0)
                GamepadEvent.Down -> focusedIndex = (focusedIndex + 1).coerceAtMost(items.lastIndex)
                GamepadEvent.Confirm -> onItemClick(items[focusedIndex])
                else -> {}
            }
        }
    }
}
```

---

## Modal Focus Isolation

When a modal (drawer, dialog, bottom sheet) is open, isolate focus:

```kotlin
@Composable
fun AppWithModalFocus(
    drawerState: DrawerState,
    gamepadEvents: SharedFlow<GamepadEvent>
) {
    val isDrawerOpen by remember { derivedStateOf { drawerState.isOpen } }

    LaunchedEffect(Unit) {
        gamepadEvents.collect { event ->
            if (isDrawerOpen) {
                handleDrawerInput(event)  // Focus stays in drawer
            } else {
                handleMainInput(event)    // Focus in main content
            }
        }
    }
}
```

---

## Section-Based Navigation (L1/R1)

```kotlin
data class HomeState(
    val sections: List<Section>,
    val currentSectionIndex: Int = 0,
    val focusedGameIndex: Int = 0
)

class HomeViewModel : ViewModel() {
    private val _state = MutableStateFlow(HomeState(sections = emptyList()))
    val state = _state.asStateFlow()

    fun nextSection() {
        _state.update { current ->
            val newIndex = (current.currentSectionIndex + 1)
                .coerceAtMost(current.sections.lastIndex)
            current.copy(
                currentSectionIndex = newIndex,
                focusedGameIndex = 0  // Reset game focus
            )
        }
    }

    fun previousSection() {
        _state.update { current ->
            val newIndex = (current.currentSectionIndex - 1).coerceAtLeast(0)
            current.copy(
                currentSectionIndex = newIndex,
                focusedGameIndex = 0
            )
        }
    }
}

// In Screen
LaunchedEffect(Unit) {
    gamepadEvents.collect { event ->
        when (event) {
            GamepadEvent.PrevSection -> viewModel.previousSection()  // L1
            GamepadEvent.NextSection -> viewModel.nextSection()      // R1
            // ...
        }
    }
}
```

---

## Navigation with Arguments

### Type-safe navigation

```kotlin
sealed class Screen(val route: String) {
    data object GameDetail : Screen("game/{gameId}") {
        const val ARG_GAME_ID = "gameId"
        fun createRoute(gameId: Long) = "game/$gameId"
    }
}

// Navigate
navController.navigate(Screen.GameDetail.createRoute(gameId = 123L))

// Receive
composable(
    route = Screen.GameDetail.route,
    arguments = listOf(
        navArgument(Screen.GameDetail.ARG_GAME_ID) {
            type = NavType.LongType
        }
    )
) { backStackEntry ->
    val gameId = backStackEntry.arguments?.getLong(Screen.GameDetail.ARG_GAME_ID)
        ?: return@composable
    GameDetailScreen(gameId = gameId)
}
```

---

## Pitfalls

### 1. Focus lost after popBackStack

Focus doesn't automatically restore. Use ViewModel or SavedStateHandle:
```kotlin
// Before navigating
viewModel.saveFocusedIndex(currentIndex)

// On return, ViewModel retains the value
```

### 2. Drawer steals focus

Use `gesturesEnabled = drawerState.isOpen` to prevent accidental swipes:
```kotlin
ModalNavigationDrawer(
    gesturesEnabled = drawerState.isOpen,
    // ...
)
```

### 3. Back button closes app instead of drawer

Handle back explicitly:
```kotlin
BackHandler(enabled = drawerState.isOpen) {
    scope.launch { drawerState.close() }
}
```

### 4. Navigation recomposes entire screen

Use `launchSingleTop = true` and `restoreState = true`:
```kotlin
navController.navigate(route) {
    launchSingleTop = true
    restoreState = true
}
```

---

## Sources

- [Navigation Compose](https://developer.android.com/develop/ui/compose/navigation)
- [Navigation Back Stack](https://developer.android.com/guide/navigation/backstack)
- [Material 3 Navigation Drawer](https://developer.android.com/develop/ui/compose/layouts/material#navigation-drawer)
