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

### Only The Root Thread Compiles
Subagents have no shell at all. Gradle, builds, test runs, lint, installs and
adb are the orchestrator's alone, and the agent definitions enforce this by
withholding Bash rather than asking for restraint. An agent that believes it
needs a build reports that and stops; the orchestrator runs the one gate.

### A Green Build Is Not Verification
`assembleDebug` does not compile `app/src/test`. A constructor change can leave
the test sources broken while the app builds, installs and runs correctly on a
device, and that state survives until CI. Before saying work is verified, know
which command actually covered it: `testDebugUnitTest` for test sources, a
device run for behaviour, live data for anything in the save zone. Say what was
checked and what was not, in those terms.

### Research Precedent Before Asking
Most choices already have an answer in the repo: an existing pattern, a sibling
feature, a decision recorded in git history or a skill. Find it and follow it.
Reserve questions for genuine forks - destructive operations, scope changes, or
a decision where two defensible readings lead somewhere materially different -
and when you do ask, say which precedents you checked and why they did not
settle it. A question that a five-minute search would have answered costs more
than the search.

## Before Writing Code
Load /code-quality for patterns and completion criteria.

## Before Refactoring
Trace all callers and references (Grep/find_usage) before modifying shared
code, DAOs, entities, or foreign keys.
