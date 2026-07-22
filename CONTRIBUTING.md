# Contributing to Argosy

Argosy is a controller-first launcher for emulation handhelds and Android TV.
PRs are welcome, including AI-assisted ones. What matters is that you own what
you submit: you've personally reviewed it and understand what it does.

Structural law for coding agents lives in [AGENTS.md](AGENTS.md); this file
covers the human side of contributing.

## The laws

These are enforced, not suggested. The `rules` CI check fails PRs that violate
the mechanically-checkable ones; reviewers enforce the rest.

1. **Dual-modality input.** Every interactive element must work with touch AND
   gamepad. Touch uses `clickableNoFocus` (ui/util/Modifiers.kt), never plain
   `clickable()`. Gamepad focus is index-driven via `InputHandler`, not Compose
   focus. A component with one input modality is incomplete.
2. **No inline comments.** Zero `//` inside function bodies, and no
   single-line `/* */` or `/** */` anywhere - the one-line block form is an
   inline comment with different delimiters. A KDoc that is genuinely needed
   (non-obvious public contract) uses the multi-line block form above the
   declaration; rationale goes in the PR description, not the diff.
3. **Tokens, not literals.** Dimensions route through `Dimens` / tokens.json.
   No hardcoded `.dp` values in UI code (0.dp excepted).
4. **Off-main-thread work.** File, network, DB, and blocking native calls run
   on `Dispatchers.IO`. A progress spinner over a blocked main thread is a bug.
5. **Save handling is high-risk.** Anything touching save sync, archiving, or
   restore paths requires live verification against a real device and server,
   not code-reading. Expect a higher review bar and requests for proof.
6. **Hardcore rules are strict by intent.** No save states, no cheats, no
   rewind, save isolation. Do not loosen these as a side effect of another
   change; propose lockout changes on their own.

## Agentic code smells (AS taxonomy)

Reviews reference these by number. If your tooling produced one, fix it before
submitting. These are the patterns that turn a decent PR into a slow one.

- **AS-1 Reviewer-persuasion comments.** Rationale prose in the diff, arguing
  the change's correctness to the reader. Belongs in the PR description.
- **AS-2 Leaked internal referents.** Agent task numbers, private plan names,
  or tool artifacts in code, comments, or docs.
- **AS-3 Smuggled behavior change.** Any observable delta shipped under
  "refactor" or "behavior-preserving" without being declared in the Behavior
  changes section.
- **AS-4 Hot-path cost blindness.** Network calls, DB writes, or blocking work
  added to launch, frame, or sync paths without acknowledging the cost.
- **AS-5 Coverage theater.** Tests that assert a mock was called instead of
  defending an invariant. Every new test should have an answer to "what breaks
  if this is deleted?"
- **AS-6 Context-window responsiveness.** Addressing whichever review thread is
  most recent while standing maintainer asks go unhandled. Check the full
  review state before pushing.

## AI assistance notice

If you used AI to help with your contribution, mention it in the PR along with
how much it did (docs only, code generation, most of the work... whatever's
accurate). Same policy as the rest of the rommapp org.

This just helps me figure out how much scrutiny to apply and where to focus
when reviewing. That's all it's for.

## Process

- PRs need the template filled in: behavior-change inventory, hot-path
  declaration, and testing evidence (real hardware, real flow). PRs missing
  these, or failing the `rules` check, may be closed with a pointer here.
  Reopen once fixed, no hard feelings.
- The `rules-exempt` label (maintainer-applied) skips the smell check for the
  rare legitimate exception.
- Squash-merge is the house style; keep PR titles in lowercase-imperative
  scope-prefixed form (`sync: ...`, `ui: ...`, `libretro: ...`).
- New emulator cores or platforms ship flagged as untested/unstable until
  verified on hardware.
