# Argosy Launcher

@../AGENTS.md is the structural law of this project: architecture, feature
completeness, fragile zones, upstream mandate, structural index, routing.
Read it as binding; this file adds only Claude-specific behavior.

## Skills first

Skills cover the recurring domains and workflows; load the relevant one
BEFORE acting, per the routing table in AGENTS.md. Scoping wide work =
investigate. Device verification = debug-device / ui-ux-testing. Shipping =
pre-release-validation / release (maintainer-only).

## Session non-negotiables

### User Data Protection
NEVER run destructive commands without explicit permission: adb pm clear,
adb shell rm on app data, deleting DB/DataStore files. Includes "debugging"
cache clears. Always ASK first.

### Save Handling Is Fragile
Any change to save sync, archiving, or restore paths is high-risk: trace
both emulator resolvers, and never call it fixed from code-reading alone.
Verify against live data (GET /api/saves, negotiate no_op twice).

### No Junk Comments
Zero inline // inside function bodies; no single-line /* */ or /** */
anywhere (the one-line block form is a rephrased //). A genuinely needed
KDoc uses the multi-line block form above the declaration, non-obvious
public contracts only. Default to zero comments.

## Before Writing Code
Load /code-quality for patterns and completion criteria.

## Before Refactoring
Trace all callers and references (Grep/find_usage) before modifying shared
code, DAOs, entities, or foreign keys.
