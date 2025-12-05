# TV Compose Components

TV-optimized components from `androidx.tv:tv-material`.

---

## Quick Reference

| TV Component | Mobile Equivalent | Key Difference |
|--------------|-------------------|----------------|
| `TvLazyRow` | `LazyRow` | Better scroll-on-focus |
| `TvLazyColumn` | `LazyColumn` | Better scroll-on-focus |
| `TvLazyVerticalGrid` | `LazyVerticalGrid` | Focus-aware scrolling |
| `androidx.tv.material3.Button` | `androidx.compose.material3.Button` | Focus indicators |
| `Card` (TV) | `Card` (Mobile) | Focus border built-in |

---

## Setup

```kotlin
// build.gradle.kts
dependencies {
    // TV Material library
    implementation("androidx.tv:tv-material:1.0.0")
    implementation("androidx.tv:tv-foundation:1.0.0")

    // Do NOT mix with mobile Material3 for TV-specific screens
    // implementation("androidx.compose.material3:material3")  // Avoid in TV screens
}
```

---

## When to Use TV vs Mobile Components

| Scenario | Use |
|----------|-----|
| Pure TV/handheld app | TV Material only |
| Shared mobile + TV codebase | Abstract components, use appropriate impl |
| Lists with D-pad navigation | `TvLazyRow`, `TvLazyColumn` |
| Buttons, cards with focus | TV Material variants |

**Warning**: Mixing `androidx.compose.material3.MaterialTheme` with `androidx.tv.material3.MaterialTheme` causes styling conflicts.

---

## TvLazyRow

Horizontal scrolling list with focus-aware scrolling:

```kotlin
import androidx.tv.foundation.lazy.list.TvLazyRow
import androidx.tv.foundation.lazy.list.items

@Composable
fun GameCarousel(games: List<Game>, onGameSelect: (Game) -> Unit) {
    TvLazyRow(
        contentPadding = PaddingValues(horizontal = 48.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(games, key = { it.id }) { game ->
            GameCard(
                game = game,
                onClick = { onGameSelect(game) },
                modifier = Modifier
                    .width(180.dp)
                    .height(240.dp)
            )
        }
    }
}
```

### With Focus Restoration

```kotlin
import androidx.tv.foundation.lazy.list.rememberTvLazyListState

@Composable
fun GameCarouselWithRestore(games: List<Game>) {
    val listState = rememberTvLazyListState()
    val focusRequester = remember { FocusRequester() }

    TvLazyRow(
        state = listState,
        modifier = Modifier.focusRestorer(focusRequester)
    ) {
        itemsIndexed(games) { index, game ->
            GameCard(
                game = game,
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

---

## TvLazyColumn

Vertical scrolling list:

```kotlin
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.items

@Composable
fun SettingsList(settings: List<Setting>) {
    TvLazyColumn(
        contentPadding = PaddingValues(vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(settings, key = { it.id }) { setting ->
            SettingItem(setting = setting)
        }
    }
}
```

---

## TvLazyVerticalGrid

Grid layout with focus-aware scrolling:

```kotlin
import androidx.tv.foundation.lazy.grid.TvLazyVerticalGrid
import androidx.tv.foundation.lazy.grid.TvGridCells
import androidx.tv.foundation.lazy.grid.items

@Composable
fun GameGrid(games: List<Game>, onGameSelect: (Game) -> Unit) {
    TvLazyVerticalGrid(
        columns = TvGridCells.Adaptive(minSize = 140.dp),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(games, key = { it.id }) { game ->
            GameCard(
                game = game,
                onClick = { onGameSelect(game) }
            )
        }
    }
}
```

---

## TV Card

Card with built-in focus handling:

```kotlin
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults

@Composable
fun TvGameCard(
    game: Game,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        scale = CardDefaults.scale(focusedScale = 1.08f),
        border = CardDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
                shape = CardDefaults.shape()
            )
        ),
        glow = CardDefaults.glow(
            focusedGlow = Glow(
                elevation = 8.dp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
            )
        )
    ) {
        AsyncImage(
            model = game.coverUrl,
            contentDescription = game.title,
            modifier = Modifier.fillMaxSize()
        )
    }
}
```

---

## TV Button

```kotlin
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults

@Composable
fun TvPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        scale = ButtonDefaults.scale(focusedScale = 1.1f),
        border = ButtonDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
            )
        )
    ) {
        Text(text)
    }
}
```

---

## Carousel (ImmersiveList)

Full-screen carousel with background:

```kotlin
import androidx.tv.material3.Carousel
import androidx.tv.material3.CarouselDefaults
import androidx.tv.material3.CarouselState

@Composable
fun FeaturedCarousel(
    featuredGames: List<Game>,
    onGameSelect: (Game) -> Unit
) {
    val carouselState = remember { CarouselState() }

    Carousel(
        itemCount = featuredGames.size,
        carouselState = carouselState,
        modifier = Modifier
            .fillMaxWidth()
            .height(400.dp),
        carouselIndicator = {
            CarouselDefaults.IndicatorRow(
                itemCount = featuredGames.size,
                activeItemIndex = carouselState.activeItemIndex,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp)
            )
        }
    ) { index ->
        val game = featuredGames[index]
        CarouselItem(
            game = game,
            onClick = { onGameSelect(game) }
        )
    }
}

@Composable
fun CarouselItem(game: Game, onClick: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize()) {
        // Background image
        AsyncImage(
            model = game.backgroundUrl,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        // Gradient overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))
                    )
                )
        )

        // Content
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(32.dp)
        ) {
            Text(
                text = game.title,
                style = MaterialTheme.typography.headlineLarge
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = onClick) {
                Text("Play")
            }
        }
    }
}
```

---

## Custom Focusable Card (Without TV Material)

If not using TV Material, build your own:

```kotlin
@Composable
fun CustomFocusableCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.08f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
        label = "scale"
    )

    val borderColor by animateColorAsState(
        targetValue = if (isFocused)
            MaterialTheme.colorScheme.primary
        else
            Color.Transparent,
        label = "border"
    )

    Card(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .border(2.dp, borderColor, RoundedCornerShape(8.dp))
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .clickable { onClick() }
    ) {
        content()
    }
}
```

---

## Content Row Pattern

Multiple carousels stacked vertically:

```kotlin
@Composable
fun ContentBrowser(sections: List<Section>) {
    TvLazyColumn {
        items(sections) { section ->
            Column {
                Text(
                    text = section.title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 48.dp, vertical = 8.dp)
                )
                TvLazyRow(
                    contentPadding = PaddingValues(horizontal = 48.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(section.items) { item ->
                        ContentCard(item = item)
                    }
                }
            }
        }
    }
}
```

---

## Scroll-on-Focus Behavior

TV Lazy components automatically scroll to bring focused items into view.

### Customize Scroll Behavior

```kotlin
TvLazyRow(
    pivotOffsets = PivotOffsets(
        parentFraction = 0.0f,  // Align to start
        childFraction = 0.0f
    )
) {
    // items
}

// Or center-focused
TvLazyRow(
    pivotOffsets = PivotOffsets(
        parentFraction = 0.5f,  // Center in viewport
        childFraction = 0.5f
    )
)
```

---

## Pitfalls

### 1. Mixing TV and Mobile Material

Don't import both in the same file:
```kotlin
// BAD
import androidx.compose.material3.Card
import androidx.tv.material3.Card  // Conflict!

// GOOD - Use alias or separate files
import androidx.tv.material3.Card as TvCard
```

### 2. Regular LazyRow doesn't scroll to focused item

Use `TvLazyRow` instead:
```kotlin
// BAD - Focused item may be off-screen
LazyRow { ... }

// GOOD - Scrolls to show focused item
TvLazyRow { ... }
```

### 3. Focus jumps unexpectedly in grids

Add `focusGroup()` modifier or use explicit focus neighbors.

### 4. TV Card onClick not working

Ensure the Card is focusable and the click handler is correct:
```kotlin
Card(
    onClick = { /* this gets called on Enter/A button */ },
    modifier = Modifier.focusable()  // May be redundant for TV Card
)
```

---

## Migration from Regular Compose

| Mobile | TV |
|--------|----|
| `LazyRow` | `TvLazyRow` |
| `LazyColumn` | `TvLazyColumn` |
| `LazyVerticalGrid` | `TvLazyVerticalGrid` |
| `rememberLazyListState()` | `rememberTvLazyListState()` |
| `GridCells.Adaptive` | `TvGridCells.Adaptive` |

---

## Sources

- [Compose on Android TV](https://developer.android.com/training/tv/playback/compose)
- [TV Material Catalog Sample](https://github.com/android/tv-samples/tree/main/TvMaterialCatalog)
- [JetStream Sample](https://github.com/android/tv-samples/tree/main/JetStreamCompose)
- [TV Composables Reference](https://composables.com/tv-material)
