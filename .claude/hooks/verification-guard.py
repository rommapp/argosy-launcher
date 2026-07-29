#!/usr/bin/env python3
"""Argosy verification guard. PreToolUse hook on Bash: refuses `git push` when Kotlin in the
outgoing commits was edited after unit tests last ran.

Gates push, not commit. `assembleDebug` does not compile `src/test`, so a constructor change can
leave the test sources broken while the app builds, installs and runs correctly on a device. That
state costs nothing locally and breaks CI, so the check belongs where work leaves the machine.

Fails open on any internal error. ARGOSY_SKIP_VERIFY_CHECK=1 acknowledges a push that genuinely
needs no test run.
"""

import json
import os
import re
import subprocess
import sys

TEST_RESULTS = "app/build/test-results/testDebugUnitTest"
SOURCE_ROOTS = ("app/src/main", "app/src/test")
PUSH_RE = re.compile(r"\bgit\b[^|;&]*\bpush\b")


def git(args, root):
    try:
        r = subprocess.run(args, cwd=root, capture_output=True, text=True, timeout=15)
        return r.stdout.strip() if r.returncode == 0 else None
    except Exception:
        return None


def outgoing_range(root):
    """The commits this push would send, as a diff range. None when it cannot be determined."""
    upstream = git(
        ["git", "rev-parse", "--abbrev-ref", "--symbolic-full-name", "@{upstream}"], root
    )
    if upstream:
        return "{}..HEAD".format(upstream)
    for base in ("origin/main", "main"):
        if git(["git", "rev-parse", "--verify", base], root) is not None:
            return "{}..HEAD".format(base)
    return None


def outgoing_kotlin(root):
    rng = outgoing_range(root)
    if rng is None:
        return None
    out = git(["git", "diff", "--name-only", rng], root)
    if out is None:
        return None
    return {p for p in out.splitlines() if p.endswith(".kt") and p.startswith(SOURCE_ROOTS)}


def results_mtime(root):
    """Newest result XML. The directory's own mtime survives a failed run, the files do not."""
    newest = 0.0
    for dirpath, _dirnames, filenames in os.walk(os.path.join(root, TEST_RESULTS)):
        for name in filenames:
            if not name.endswith(".xml"):
                continue
            try:
                newest = max(newest, os.path.getmtime(os.path.join(dirpath, name)))
            except OSError:
                continue
    return newest


def main():
    try:
        payload = json.load(sys.stdin)
    except Exception:
        return 0

    if (payload.get("tool_name") or "") != "Bash":
        return 0
    cmd = (payload.get("tool_input") or {}).get("command", "") or ""
    if not PUSH_RE.search(cmd) or "ARGOSY_SKIP_VERIFY_CHECK=1" in cmd:
        return 0
    if "--dry-run" in cmd:
        return 0

    root = os.environ.get("CLAUDE_PROJECT_DIR") or os.getcwd()
    changed = outgoing_kotlin(root)
    if not changed:
        return 0

    tested = results_mtime(root)
    if tested == 0.0:
        sys.stderr.write(
            "VERIFICATION GUARD -- blocking push:\n"
            "  This push carries Kotlin and unit tests have never run in this tree.\n"
            "  `assembleDebug` does NOT compile app/src/test, so a green build says nothing\n"
            "  about whether the test sources still compile. CI runs them.\n\n"
            "  Run: ./gradlew testDebugUnitTest\n"
            "  Or acknowledge: ARGOSY_SKIP_VERIFY_CHECK=1 git push ...\n"
        )
        return 2

    stale = []
    for rel in changed:
        try:
            mtime = os.path.getmtime(os.path.join(root, rel))
        except OSError:
            continue
        if mtime > tested:
            stale.append((mtime - tested, rel))

    if not stale:
        return 0

    stale.sort(reverse=True)
    lines = [
        "VERIFICATION GUARD -- blocking push:",
        "  Kotlin in the outgoing commits changed after unit tests last ran.",
        "  `assembleDebug` does NOT compile app/src/test -- a green build is not evidence here.",
        "",
    ]
    for delta, path in stale[:3]:
        lines.append("  %-58s newer than tests by %d min" % (path, int(delta // 60)))
    if len(stale) > 3:
        lines.append("  ... and %d more" % (len(stale) - 3))
    lines += [
        "",
        "  Run: ./gradlew testDebugUnitTest",
        "  Or acknowledge: ARGOSY_SKIP_VERIFY_CHECK=1 git push ...",
    ]
    sys.stderr.write("\n".join(lines) + "\n")
    return 2


if __name__ == "__main__":
    try:
        sys.exit(main())
    except Exception:
        sys.exit(0)
