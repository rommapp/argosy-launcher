#!/usr/bin/env python3
"""Argosy coupling guard. Checks a diff against coupling-hotspots.json beside this script and
reports the companion edits a change is expected to carry.

Two modes, one rule set. As a PreToolUse hook on Bash it inspects the staged (and -a tracked)
diff when the command runs `git commit`, and blocks (exit 2) on a gap so the lockstep set and
live-data proofs get verified before the commit lands. As `--sweep <range>` it reports over a
whole range and never blocks, which is what CI and the release checklist use: a gap that slips
past a single commit still gets caught across the PR.

Fails open on any internal error so it never wedges legitimate work."""

import json
import os
import re
import subprocess
import sys


def run_git(args, root):
    try:
        return subprocess.run(
            args, cwd=root, capture_output=True, text=True, timeout=15
        ).stdout
    except Exception:
        return ""


def glob_to_re(glob):
    out, i = [], 0
    while i < len(glob):
        c = glob[i]
        if glob[i : i + 3] == "**/":
            out.append("(?:.*/)?")
            i += 3
        elif glob[i : i + 2] == "**":
            out.append(".*")
            i += 2
        elif c == "*":
            out.append("[^/]*")
            i += 1
        elif c == "?":
            out.append("[^/]")
            i += 1
        else:
            out.append(re.escape(c))
            i += 1
    return re.compile("".join(out) + r"\Z")


def parse_added(diff_text):
    added, current = {}, None
    for line in diff_text.splitlines():
        if line.startswith("+++ "):
            path = line[4:].strip()
            current = path[2:] if path.startswith("b/") else path
            if current == "/dev/null":
                current = None
            elif current is not None:
                added.setdefault(current, [])
        elif current and line.startswith("+") and not line.startswith("+++"):
            added[current].append(line[1:])
    return added


def matches_glob(path, glob):
    return glob_to_re(glob).match(path) is not None


def glob_list(when, single_key, multi_key):
    if multi_key in when:
        return when[multi_key]
    if single_key in when:
        return [when[single_key]]
    return []


NON_PRODUCTION_RE = re.compile(r"(^|/)src/(test|androidTest)/")


def is_production(path):
    """Only shipped sources carry the coupling obligations.

    A test can restate a save path or a preference key without changing what the app does,
    so gating it forces an acknowledgement nobody can act on, and the token stops being read.
    """
    return not NON_PRODUCTION_RE.search(path)


def when_hit(when, added):
    globs = glob_list(when, "path_glob", "path_glob_any")
    exclude = when.get("exclude_path_glob")
    pat = re.compile(when.get("added_regex", "."))
    for path, lines in added.items():
        if not is_production(path):
            continue
        if exclude and matches_glob(path, exclude):
            continue
        if not any(matches_glob(path, g) for g in globs):
            continue
        if any(pat.search(ln) for ln in lines):
            return True
    return False


def requirement_satisfied(req, added):
    pat = re.compile(req.get("added_regex", "."))
    for path, lines in added.items():
        if not is_production(path):
            continue
        if matches_glob(path, req["path_glob"]) and any(pat.search(ln) for ln in lines):
            return True
    return False


def requirement_gap(rule, added):
    if "requires_all_added" in rule:
        missing = [r for r in rule["requires_all_added"] if not requirement_satisfied(r, added)]
        return missing
    if "requires_any_added" in rule:
        if any(requirement_satisfied(r, added) for r in rule["requires_any_added"]):
            return []
        return list(rule["requires_any_added"])
    return []


def format_entry(axis, rule, missing):
    lines = ["- [{}] {}".format(axis["label"], rule["message"])]
    if axis.get("routing"):
        lines.append("    routing: {}".format(axis["routing"]))
    if missing:
        lines.append("    missing companion edits:")
        for m in missing:
            lines.append("      - {} (expecting /{}/)".format(m["path_glob"], m.get("added_regex", ".")))
    for item in rule.get("checklist", []):
        lines.append("    check: {}".format(item))
    for item in rule.get("proof", []):
        lines.append("    proof: {}".format(item))
    return "\n".join(lines)


def load_cfg():
    cfg_path = os.path.join(os.path.dirname(os.path.abspath(__file__)), "coupling-hotspots.json")
    with open(cfg_path) as f:
        return json.load(f)


def evaluate(added, cfg):
    blocks, warns = [], []
    for axis in cfg.get("axes", []):
        for rule in axis.get("rules", []):
            if not when_hit(rule.get("when", {}), added):
                continue
            missing = requirement_gap(rule, added)
            if ("requires_all_added" in rule or "requires_any_added" in rule) and not missing:
                continue
            entry = format_entry(axis, rule, missing)
            (warns if rule.get("severity") == "warn" else blocks).append(entry)
    return blocks, warns


def main_sweep(root, rng):
    try:
        cfg = load_cfg()
    except Exception:
        print("coupling sweep: config not found, skipping")
        sys.exit(0)
    added = parse_added(run_git(["git", "diff", "--unified=0", rng], root))
    blocks, warns = evaluate(added, cfg)
    if not blocks and not warns:
        print("coupling sweep: clean over {}".format(rng))
        sys.exit(0)
    if blocks:
        print("COUPLING SWEEP -- review before release ({}):".format(rng))
        print("\n".join(blocks))
    if warns:
        print("\nCOUPLING SWEEP -- warnings:")
        print("\n".join(warns))
    sys.exit(0)


ACK_TRAILER_RE = re.compile(r"^Coupling-ack:\s*(.+)$", re.M)
MIN_ACK = 20


def commit_message(cmd, root):
    """The message this commit will carry, from -m or -F. Empty when neither is present."""
    m = re.search(r"-m\s+(['\"])(.*?)\1", cmd, re.S)
    if m:
        return m.group(2)
    f = re.search(r"-F\s+(\S+)", cmd)
    if f:
        path = f.group(1).strip("'\"")
        if not os.path.isabs(path):
            path = os.path.join(root, path)
        try:
            with open(path) as fh:
                return fh.read()
        except Exception:
            return ""
    return ""


def acknowledgement(cmd, root, skip_token):
    """The reason recorded for waving a block through, or None.

    A bare env flag leaves nothing behind: the next reader cannot tell a met obligation from a
    skipped one, and the token stops being read at all. Requiring the claim in the message puts
    it in history where review can reach it.
    """
    if skip_token not in cmd:
        return None
    found = ACK_TRAILER_RE.search(commit_message(cmd, root))
    if not found:
        return ""
    return found.group(1).strip()


def main():
    if len(sys.argv) > 1 and sys.argv[1] == "--sweep":
        root = os.environ.get("CLAUDE_PROJECT_DIR") or os.getcwd()
        rng = sys.argv[2] if len(sys.argv) > 2 else "HEAD"
        main_sweep(root, rng)
        return

    try:
        payload = json.load(sys.stdin)
    except Exception:
        sys.exit(0)

    if (payload.get("tool_name") or "") != "Bash":
        sys.exit(0)
    cmd = (payload.get("tool_input") or {}).get("command", "") or ""
    if "git commit" not in cmd:
        sys.exit(0)

    root = os.environ.get("CLAUDE_PROJECT_DIR") or os.getcwd()
    try:
        cfg = load_cfg()
    except Exception:
        sys.exit(0)

    skip_token = cfg.get("skip_token", "ARGOSY_SKIP_COUPLING_CHECK")
    ack = acknowledgement(cmd, root, skip_token)
    skipped = ack is not None and len(ack) >= MIN_ACK

    diff = run_git(["git", "diff", "--cached", "--unified=0"], root)
    if re.search(r"(?:\s-[A-Za-z]*a[A-Za-z]*\b|\s--all\b)", cmd):
        diff += "\n" + run_git(["git", "diff", "--unified=0"], root)
    added = parse_added(diff)
    if not added:
        sys.exit(0)

    blocks, warns = evaluate(added, cfg)

    if not blocks and not warns:
        sys.exit(0)

    report = []
    if blocks:
        report.append("COUPLING GUARD -- blocking:")
        report.extend(blocks)
    if warns:
        report.append("" if not blocks else "")
        report.append("COUPLING GUARD -- warnings (not blocking):")
        report.extend(warns)
    text = "\n".join(report)

    if blocks and not skipped:
        if ack is None:
            text += (
                "\n\nResolve the above, or acknowledge with BOTH:"
                "\n  1. prefix the command with {}=1"
                "\n  2. a `Coupling-ack: <what you actually checked>` trailer in the commit"
                " message (min {} chars)"
                "\n\nThe trailer is the record. A reader months from now cannot tell a met"
                " obligation from a waved one without it.".format(skip_token, MIN_ACK)
            )
        else:
            text += (
                "\n\n{} is set but the commit message carries no usable `Coupling-ack:`"
                " trailer (min {} chars). Name the proofs you ran, or what makes this a false"
                " positive.".format(skip_token, MIN_ACK)
            )
        sys.stderr.write(text + "\n")
        sys.exit(2)

    if skipped and blocks:
        text += "\n\n(acknowledged: {})".format(ack)
    sys.stderr.write(text + "\n")
    sys.exit(0)


if __name__ == "__main__":
    main()
