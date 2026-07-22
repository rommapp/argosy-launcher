#!/usr/bin/env python3
"""Argosy agentic-smell check. Flags house-rule violations on ADDED lines only,
so pre-existing code is never blamed. Driven by scripts/ci/smell-rules.json.

Usage:
  agentic-smells.py --range origin/main...HEAD   # CI: diff a git range
  agentic-smells.py --diff-file some.diff        # test: parse a saved diff

Exit 0 when clean, 1 when findings exist, 0 on internal errors (fails open)."""

import argparse
import json
import os
import re
import subprocess
import sys

HUNK_RE = re.compile(r"^@@ -\d+(?:,\d+)? \+(\d+)(?:,\d+)? @@")


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


def parse_added_with_lines(diff_text):
    added, current, lineno = [], None, 0
    for line in diff_text.splitlines():
        if line.startswith("+++ "):
            path = line[4:].strip()
            current = path[2:] if path.startswith("b/") else path
            if current == "/dev/null":
                current = None
        elif line.startswith("@@"):
            m = HUNK_RE.match(line)
            if m:
                lineno = int(m.group(1))
        elif current and line.startswith("+") and not line.startswith("+++"):
            added.append((current, lineno, line[1:]))
            lineno += 1
        elif current and not line.startswith("-") and not line.startswith("\\"):
            lineno += 1
    return added


def load_rules(root):
    with open(os.path.join(root, "scripts", "ci", "smell-rules.json")) as f:
        return json.load(f)["rules"]


def evaluate(added, rules):
    findings = []
    for rule in rules:
        pat = re.compile(rule["added_regex"])
        skip = re.compile(rule["skip_line_regex"]) if rule.get("skip_line_regex") else None
        for path, lineno, text in added:
            if not matches_any(path, rule["path_include"]):
                continue
            if matches_any(path, rule.get("path_exclude", [])):
                continue
            if skip and skip.search(text):
                continue
            if pat.search(text):
                findings.append((rule, path, lineno, text.strip()))
    return findings


def report(findings):
    by_rule = {}
    for rule, path, lineno, text in findings:
        by_rule.setdefault(rule["id"], (rule, []))[1].append((path, lineno, text))
    lines = ["AGENTIC SMELL CHECK: {} finding(s)".format(len(findings)), ""]
    for rule_id, (rule, hits) in by_rule.items():
        lines.append("[{}] {}".format(rule_id, rule["summary"]))
        lines.append("    {}".format(rule["message"]))
        for path, lineno, text in hits:
            lines.append("    {}:{}: {}".format(path, lineno, text[:120]))
        lines.append("")
    return "\n".join(lines)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--range")
    parser.add_argument("--diff-file")
    args = parser.parse_args()

    root = os.environ.get("GITHUB_WORKSPACE") or os.getcwd()
    try:
        rules = load_rules(root)
    except Exception as e:
        print("smell check: could not load rules ({}), skipping".format(e))
        sys.exit(0)

    if args.diff_file:
        with open(args.diff_file) as f:
            diff_text = f.read()
    elif args.range:
        try:
            diff_text = subprocess.run(
                ["git", "diff", "--unified=0", args.range],
                cwd=root, capture_output=True, text=True, timeout=60,
            ).stdout
        except Exception as e:
            print("smell check: git diff failed ({}), skipping".format(e))
            sys.exit(0)
    else:
        parser.error("one of --range or --diff-file is required")

    findings = evaluate(parse_added_with_lines(diff_text), rules)
    if not findings:
        print("smell check: clean")
        sys.exit(0)
    print(report(findings))
    sys.exit(1)


if __name__ == "__main__":
    main()
