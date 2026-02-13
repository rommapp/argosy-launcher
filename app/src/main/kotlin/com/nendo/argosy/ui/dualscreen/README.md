# Dual-Screen UI

Reimagined UI for dual-display devices (e.g., AYN Thor).

## Philosophy

- **Upper screen**: Visual context (art, title, stats, read-only info)
- **Lower screen**: Interaction (lists, actions, selection, editing)
- **Reduce cognitive load**: One screen to look at, one screen to control
- **Input clarity**: Lower screen owns focus; upper forwards input

## Packages

- `home/` - Dual-screen home with game carousel (lower) and showcase (upper)
- `gamedetail/` - Tabbed game details with Saves/Media/Options (lower) and info display (upper)
- `library/` - Dual-screen library browser
- `collections/` - Dual-screen collection management

## Input Handling

**Normal state**: Upper screen is display-only. Any input -> refocus to lower.

**Modal state**: Upper screen handles input directly (e.g., Start menu).

## Cross-Process Communication

Uses `SessionStateStore` (SharedPreferences) for state sync between main and companion processes.
Broadcasts for live updates, SharedPreferences for startup reads.
