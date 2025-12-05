# Focus Management

TV apps require explicit focus management. Users navigate with D-pad/controller, not touch.

---

## Quick Reference

| Modifier | Purpose |
|----------|---------|
| `focusRequester(fr)` | Attach a FocusRequester to request focus programmatically |
| `onFocusChanged { }` | React to focus state changes |
| `focusable()` | Make a composable focusable |
| `focusProperties { }` | Control focus behavior (enter, exit, next, previous) |
| `focusGroup()` | Group focusable children for two-dimensional navigation |
| `focusRestorer(fr)` | Restore focus to last focused child when re-entering |

---

## Two Approaches

### 1. Compose Native Focus

Use Compose's built-in focus system with modifiers. Good for simpler UIs.

```kotlin
@Composable
fun FocusableCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .clickable { onClick() },
        colors = CardDefaults.colors(
            containerColor = if (isFocused)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surface
        )
    ) {
        // content
    }
}
```

### 2. Focus-as-State (Manual)

Track focus in ViewModel state. Better for complex UIs, easier to test.

```kotlin
data class ScreenState(
    val items: List<Item>,
    val focusedIndex: Int = 0
)

class ScreenViewModel : ViewModel() {
    private val _state = MutableStateFlow(ScreenState(items = emptyList()))
    val state = _state.asStateFlow()

    fun moveFocus(direction: FocusDirection) {
        _state.update { current ->
            val newIndex = when (direction) {
                FocusDirection.Up -> (current.focusedIndex - 1).coerceAtLeast(0)
                FocusDirection.Down -> (current.focusedIndex + 1).coerceAtMost(current.items.lastIndex)
                else -> current.focusedIndex
            }
            current.copy(focusedIndex = newIndex)
        }
    }
}

// In Composable
val state by viewModel.state.collectAsState()
items.forEachIndexed { index, item ->
    ItemCard(
        item = item,
        isFocused = index == state.focusedIndex
    )
}
```

---

## FocusRequester Patterns

### Request focus on screen load

```kotlin
@Composable
fun Screen() {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Button(
        onClick = { },
        modifier = Modifier.focusRequester(focusRequester)
    ) {
        Text("First Button")
    }
}
```

### Request focus on first item in list

```kotlin
@Composable
fun ItemList(items: List<Item>) {
    val firstItemFocusRequester = remember { FocusRequester() }

    LaunchedEffect(items) {
        if (items.isNotEmpty()) {
            firstItemFocusRequester.requestFocus()
        }
    }

    LazyColumn {
        itemsIndexed(items) { index, item ->
            ItemCard(
                item = item,
                modifier = if (index == 0) {
                    Modifier.focusRequester(firstItemFocusRequester)
                } else {
                    Modifier
                }
            )
        }
    }
}
```

---

## Focus Restoration

### Using focusRestorer (Experimental)

```kotlin
@Composable
fun CarouselRow(items: List<Item>) {
    val focusRequester = remember { FocusRequester() }

    LazyRow(
        modifier = Modifier.focusRestorer(focusRequester)
    ) {
        itemsIndexed(items) { index, item ->
            ItemCard(
                item = item,
                modifier = if (index == 0) {
                    Modifier.focusRequester(focusRequester)
                } else {
                    Modifier
                }
            )
        }
    }
}
```

### Using focusProperties (More Control)

```kotlin
@Composable
fun CarouselRow(items: List<Item>) {
    val parentFocusRequester = remember { FocusRequester() }

    LazyRow(
        modifier = Modifier
            .focusRequester(parentFocusRequester)
            .focusProperties {
                exit = {
                    parentFocusRequester.saveFocusedChild()
                    FocusRequester.Default
                }
                enter = {
                    if (!parentFocusRequester.restoreFocusedChild()) {
                        FocusRequester.Default
                    } else {
                        FocusRequester.Cancel
                    }
                }
            }
    ) {
        items(items) { item ->
            ItemCard(item = item)
        }
    }
}
```

---

## Focus Groups

Group items for two-dimensional navigation within a container.

```kotlin
@Composable
fun GridSection() {
    Column(modifier = Modifier.focusGroup()) {
        Row {
            Button(onClick = {}) { Text("1") }
            Button(onClick = {}) { Text("2") }
        }
        Row {
            Button(onClick = {}) { Text("3") }
            Button(onClick = {}) { Text("4") }
        }
    }
}
```

Without `focusGroup()`, D-pad navigation might exit the column prematurely.

---

## Explicit Focus Neighbors

Override default focus traversal:

```kotlin
val (button1, button2, button3) = remember { FocusRequester.createRefs() }

Button(
    modifier = Modifier
        .focusRequester(button1)
        .focusProperties {
            down = button3  // Skip button2 when pressing down
        }
) { Text("Button 1") }

Button(
    modifier = Modifier.focusRequester(button2)
) { Text("Button 2") }

Button(
    modifier = Modifier.focusRequester(button3)
) { Text("Button 3") }
```

---

## Pitfalls

### 1. Conditional modifiers break focus

BAD - Adding border conditionally changes modifier chain:
```kotlin
// Focus is lost when isFocused changes!
Box(
    modifier = Modifier
        .then(if (isFocused) Modifier.border(2.dp, Color.Blue) else Modifier)
        .focusable()
)
```

GOOD - Use parameters that don't affect modifier chain:
```kotlin
Box(
    modifier = Modifier
        .border(2.dp, if (isFocused) Color.Blue else Color.Transparent)
        .focusable()
)
```

### 2. Focus jumping erratically in LazyColumn

Compose's default focus traversal can jump unexpectedly. Solutions:
- Use `focusGroup()` to contain focus within sections
- Use explicit `focusProperties` to control neighbors
- Use TV-optimized components (`TVLazyColumn`)

### 3. Focus lost after recomposition

Focus state is not preserved by default. Solutions:
- Use `focusRestorer` modifier
- Implement Focus-as-State pattern
- Store focus position in ViewModel

### 4. focusRestorer crashes with dynamic lists

If the previously focused item no longer exists:
```kotlin
// Defensive focusRestorer
val focusRequester = remember { FocusRequester() }

LazyRow(
    modifier = Modifier.focusRestorer {
        if (items.isNotEmpty()) focusRequester else FocusRequester.Default
    }
) { ... }
```

### 5. Focus not working at all

Check modifier order:
```kotlin
// WRONG - focusable must come after focus modifiers
Modifier
    .focusable()
    .onFocusChanged { }  // Never called!

// CORRECT
Modifier
    .onFocusChanged { }
    .focusable()
```

---

## Focus State Properties

```kotlin
Modifier.onFocusChanged { focusState ->
    // Is this exact composable focused?
    focusState.isFocused

    // Is this composable OR any child focused?
    focusState.hasFocus

    // Is focus captured (e.g., text field editing)?
    focusState.isCaptured
}
```

---

## Sources

- [Focus in Compose](https://developer.android.com/develop/ui/compose/touch-input/focus)
- [React to Focus](https://developer.android.com/develop/ui/compose/touch-input/focus/react-to-focus)
- [Focus as a State Pattern](https://alexzaitsev.substack.com/p/focus-as-a-state-new-effective-tv)
- [Advanced FocusRequester Manipulation](https://oleksii-tym.medium.com/android-tv-advanced-focus-requester-manipulation-7569e818a734)
