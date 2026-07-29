#!/usr/bin/env python3
"""Argosy smell guard. PostToolUse hook on Edit/Write: checks the just-written
content against scripts/ci/smell-rules.json (the same rules CI enforces) and
feeds violations back so the agent self-corrects at write time. Fails open on
any internal error."""

import json
import os
import re
import sys


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


def matches_any(path, globs):
    return any(glob_to_re(g).match(path) for g in globs)


def main():
    try:
        payload = json.load(sys.stdin)
    except Exception:
        sys.exit(0)

    if (payload.get("tool_name") or "") not in ("Edit", "Write"):
        sys.exit(0)

    tool_input = payload.get("tool_input") or {}
    file_path = tool_input.get("file_path", "") or ""
    root = os.environ.get("CLAUDE_PROJECT_DIR") or os.getcwd()
    rel = os.path.relpath(file_path, root) if file_path.startswith(root) else file_path

    if payload["tool_name"] == "Edit":
        text = tool_input.get("new_string")
        if not text:
            sys.exit(0)
        carried = set((tool_input.get("old_string") or "").splitlines())
        lines = [ln for ln in text.splitlines() if ln not in carried]
        text = "\n".join(lines)
    else:
        text = tool_input.get("content")
    if not text:
        sys.exit(0)

    try:
        with open(os.path.join(root, "scripts", "ci", "smell-rules.json")) as f:
            rules = json.load(f)["rules"]
    except Exception:
        sys.exit(0)

    findings = []
    for rule in rules:
        if not matches_any(rel, rule["path_include"]):
            continue
        if matches_any(rel, rule.get("path_exclude", [])):
            continue
        pat = re.compile(rule["added_regex"])
        skip = re.compile(rule["skip_line_regex"]) if rule.get("skip_line_regex") else None
        for line in text.splitlines():
            if skip and skip.search(line):
                continue
            if pat.search(line):
                findings.append((rule, line.strip()))

    if not findings:
        sys.exit(0)

    lines = ["SMELL GUARD: the content just written violates house rules:"]
    seen = set()
    for rule, line in findings:
        if rule["id"] not in seen:
            lines.append("[{}] {}".format(rule["id"], rule["message"]))
            seen.add(rule["id"])
        lines.append("    {}".format(line[:120]))
    lines.append("Fix the flagged lines now; CI enforces the same rules on the PR diff.")
    sys.stderr.write("\n".join(lines) + "\n")
    sys.exit(2)


if __name__ == "__main__":
    main()
