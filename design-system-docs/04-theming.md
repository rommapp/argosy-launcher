# Theming

TV/handheld theming with Material 3, focus indicators, and animations.

---

## Quick Reference

| Component | Purpose |
|-----------|---------|
| `MaterialTheme` | Color, typography, shapes |
| `CompositionLocal` | Custom theme values |
| `animateColorAsState` | Smooth color transitions |
| `animateFloatAsState` | Smooth scale/alpha transitions |
| `spring()` | Bouncy animations for focus |

---

## Material 3 Theme Setup

```kotlin
@Composable
fun LauncherTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) darkColorScheme() else lightColorScheme()

    MaterialTheme(
        colorScheme = colorScheme,
        typography = LauncherTypography,
        content = content
    )
}

// Custom dark scheme optimized for TV viewing
private fun darkColorScheme() = darkColorScheme(
    primary = Color(0xFF6B8EFF),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF3D5A99),
    surface = Color(0xFF121212),
    surfaceVariant = Color(0xFF1E1E1E),
    background = Color(0xFF0A0A0A),
    onSurface = Color(0xFFE0E0E0),
    onSurfaceVariant = Color(0xFFB0B0B0)
)
```

---

## Custom Theme Values

Use `CompositionLocal` for launcher-specific values:

```kotlin
data class LauncherThemeConfig(
    val focusGlowColor: Color = Color(0x666B8EFF),
    val focusScale: Float = 1.08f,
    val focusedAlpha: Float = 1f,
    val unfocusedAlpha: Float = 0.85f,
    val focusedSaturation: Float = 1f,
    val unfocusedSaturation: Float = 0.7f
)

val LocalLauncherTheme = staticCompositionLocalOf { LauncherThemeConfig() }

@Composable
fun LauncherTheme(
    config: LauncherThemeConfig = LauncherThemeConfig(),
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalLauncherTheme provides config) {
        MaterialTheme(...) {
            content()
        }
    }
}

// Access anywhere
@Composable
fun SomeComponent() {
    val theme = LocalLauncherTheme.current
    val scale = theme.focusScale
}
```

---

## Focus Indicator Styling

### Scale + Glow Effect

```kotlin
@Composable
fun FocusableCard(
    isFocused: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val theme = LocalLauncherTheme.current

    val scale by animateFloatAsState(
        targetValue = if (isFocused) theme.focusScale else 1f,
        animationSpec = spring(
            dampingRatio = 0.6f,
            stiffness = 400f
        ),
        label = "scale"
    )

    val glowAlpha by animateFloatAsState(
        targetValue = if (isFocused) 0.4f else 0f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
        label = "glow"
    )

    Box(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .drawBehind {
                if (glowAlpha > 0f) {
                    drawRoundRect(
                        color = theme.focusGlowColor.copy(alpha = glowAlpha),
                        cornerRadius = CornerRadius(16.dp.toPx()),
                        size = Size(size.width + 16.dp.toPx(), size.height + 16.dp.toPx()),
                        topLeft = Offset(-8.dp.toPx(), -8.dp.toPx())
                    )
                }
            }
    ) {
        content()
    }
}
```

### Border Focus Indicator

```kotlin
@Composable
fun BorderFocusCard(
    isFocused: Boolean,
    modifier: Modifier = Modifier
) {
    val borderColor by animateColorAsState(
        targetValue = if (isFocused)
            MaterialTheme.colorScheme.primary
        else
            Color.Transparent,
        label = "border"
    )

    Card(
        modifier = modifier.border(
            width = 2.dp,
            color = borderColor,
            shape = RoundedCornerShape(8.dp)
        )
    ) {
        // content
    }
}
```

### Saturation Effect

Desaturate unfocused items to draw attention to focused item:

```kotlin
@Composable
fun SaturatedImage(
    imageUrl: String,
    isFocused: Boolean,
    modifier: Modifier = Modifier
) {
    val theme = LocalLauncherTheme.current

    val saturation by animateFloatAsState(
        targetValue = if (isFocused) theme.focusedSaturation else theme.unfocusedSaturation,
        label = "saturation"
    )

    val saturationMatrix = remember(saturation) {
        ColorMatrix().apply { setToSaturation(saturation) }
    }

    AsyncImage(
        model = imageUrl,
        contentDescription = null,
        modifier = modifier,
        colorFilter = ColorFilter.colorMatrix(saturationMatrix)
    )
}
```

---

## Safe Modifier Patterns

### Problem: Conditional modifiers break focus

```kotlin
// BAD - Focus is lost when isFocused changes!
Box(
    modifier = Modifier
        .then(if (isFocused) Modifier.border(2.dp, Color.Blue) else Modifier)
        .focusable()
)
```

### Solution: Use parameters, not conditional modifiers

```kotlin
// GOOD - Border always exists, just transparent when unfocused
Box(
    modifier = Modifier
        .border(2.dp, if (isFocused) Color.Blue else Color.Transparent)
        .focusable()
)

// GOOD - Use animated values
val borderWidth by animateDpAsState(if (isFocused) 2.dp else 0.dp)
Box(
    modifier = Modifier
        .border(borderWidth, Color.Blue)
        .focusable()
)
```

---

## Animation Specs

### Spring (Recommended for Focus)

```kotlin
// Bouncy, natural feel
spring<Float>(
    dampingRatio = 0.6f,  // 0.0 = no damping, 1.0 = critical
    stiffness = 400f      // Higher = faster
)
```

### Tween (For Transitions)

```kotlin
// Linear timing
tween<Float>(
    durationMillis = 200,
    easing = FastOutSlowInEasing
)
```

### Animation Speed Settings

```kotlin
enum class AnimationSpeed(val multiplier: Float) {
    SLOW(1.5f),
    NORMAL(1f),
    FAST(0.5f),
    OFF(0f)
}

@Composable
fun animatedScale(isFocused: Boolean, speed: AnimationSpeed): Float {
    val targetValue = if (isFocused) 1.08f else 1f

    return if (speed == AnimationSpeed.OFF) {
        targetValue
    } else {
        val scale by animateFloatAsState(
            targetValue = targetValue,
            animationSpec = spring(
                dampingRatio = 0.6f,
                stiffness = 400f / speed.multiplier
            ),
            label = "scale"
        )
        scale
    }
}
```

---

## Typography for TV/Handheld

```kotlin
val LauncherTypography = Typography(
    // Large titles for distance viewing
    displayLarge = TextStyle(
        fontSize = 57.sp,
        lineHeight = 64.sp,
        fontWeight = FontWeight.Normal
    ),
    // Game titles
    titleLarge = TextStyle(
        fontSize = 22.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.Medium
    ),
    // Secondary info
    bodyMedium = TextStyle(
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Normal
    ),
    // Small labels
    labelSmall = TextStyle(
        fontSize = 11.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.5.sp
    )
)
```

---

## Dimensions

```kotlin
object Dimensions {
    // Spacing
    val spacingXS = 4.dp
    val spacingS = 8.dp
    val spacingM = 16.dp
    val spacingL = 24.dp
    val spacingXL = 32.dp
    val spacingXXL = 48.dp

    // Card sizes
    val gameCardWidth = 180.dp
    val gameCardHeight = 240.dp
    val gameCardWidthSmall = 140.dp
    val gameCardHeightSmall = 187.dp

    // Radii
    val radiusS = 4.dp
    val radiusM = 8.dp
    val radiusL = 12.dp
    val radiusXL = 16.dp

    // Focus
    val focusBorderWidth = 2.dp
    val focusGlowRadius = 8.dp
}
```

---

## Dynamic Theming

### User-Selected Accent Color

```kotlin
@Composable
fun LauncherTheme(
    primaryColor: Color = Color(0xFF6B8EFF),
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = darkColorScheme(
        primary = primaryColor,
        primaryContainer = primaryColor.toContainerColor(),
        // ... derive other colors
    )

    MaterialTheme(colorScheme = colorScheme, content = content)
}

// Derive container color from primary
fun Color.toContainerColor(): Color {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(this.toArgb(), hsl)
    hsl[1] = hsl[1] * 0.25f  // Reduce saturation
    hsl[2] = hsl[2] * 0.15f  // Reduce lightness
    return Color(ColorUtils.HSLToColor(hsl))
}
```

### Preset Colors

```kotlin
val presetColors = listOf(
    Color(0xFF6B8EFF),  // Indigo
    Color(0xFF4DB6AC),  // Teal
    Color(0xFFFFB74D),  // Orange
    Color(0xFFE57373),  // Red
    Color(0xFF81C784),  // Green
    Color(0xFFBA68C8),  // Purple
)

@Composable
fun ColorPicker(
    selectedColor: Color,
    onColorSelected: (Color) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        presetColors.forEach { color ->
            ColorCircle(
                color = color,
                isSelected = color == selectedColor,
                onClick = { onColorSelected(color) }
            )
        }
    }
}
```

---

## Pitfalls

### 1. Conditional modifiers cause focus loss

See "Safe Modifier Patterns" above.

### 2. Animations jank on low-end devices

Use simpler animations or provide OFF option:
```kotlin
if (animationSpeed == AnimationSpeed.OFF) {
    targetValue  // Skip animation entirely
}
```

### 3. Colors hard to see on TV

Ensure sufficient contrast (WCAG AA minimum):
- Normal text: 4.5:1 contrast ratio
- Large text: 3:1 contrast ratio
- Use lighter text on dark backgrounds

### 4. Focus glow clips at edges

Add padding to parent container:
```kotlin
Box(modifier = Modifier.padding(Dimensions.focusGlowRadius)) {
    FocusableCard(...)
}
```

---

## Sources

- [Material Design 3 in Compose](https://developer.android.com/develop/ui/compose/designsystems/material3)
- [Animation in Compose](https://developer.android.com/develop/ui/compose/animation)
- [TV Design Guidelines](https://developer.android.com/design/ui/tv)
