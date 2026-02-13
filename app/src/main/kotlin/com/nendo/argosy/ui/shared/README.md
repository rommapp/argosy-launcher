# Shared UI Components

Components in this package are used by **both single-screen and dual-screen UIs**.

## Important

**Any changes to files in this package affect both layouts. Test both when modifying.**

Every Kotlin file should include this header comment:

```kotlin
/**
 * SHARED COMPONENT - Changes affect both single-screen and dual-screen UIs.
 * Test both layouts when modifying.
 */
```

## Packages

- `cards/` - Game cards, platform badges, stat displays
- `modals/` - Base modal, save timeline, channel picker
- `footer/` - Footer bar, input hints
- `input/` - Input forwarding, focus management
