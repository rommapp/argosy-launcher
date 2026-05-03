# DEV ONLY — DO NOT SHIP

The 347 SVG files in this directory are sourced from
https://github.com/datkat21/mii-creator (no LICENSE file in the upstream
repo) and likely derive from Nintendo's Mii Maker assets. They are
present here as **placeholder content for in-tree development of the
QuayPass avatar pipeline and Godot integration only**.

## Before any public release

Replace these SVGs wholesale with commissioned original art. The
filename pattern (`{category}-{NN}.svg`) is the contract; the renderer,
wire format, and Godot pipeline reference parts by category and index,
not by content. A drop-in directory swap is sufficient.

## Categories and counts (matches the wire format bit allocations)

- face: 12
- makeup: 12
- wrinkles: 12
- eyebrows: 24
- eyes: 60
- nose: 18
- mouth: 36
- mustache: 6
- goatee: 6
- hair: 132
- glasses: 21 (one filename uses 20 + a "no glasses" implicit option)
- hat: 9

## CI / release checklist (to be wired up later)

- Block release builds if any file in this directory is identical
  (by content hash) to its upstream counterpart in mii-creator.
- README.DEV_ONLY presence in this directory should fail the release
  build until intentionally removed alongside the asset swap.
