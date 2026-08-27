#!/usr/bin/env python3
"""Check a translated locale against the English base.

Android lint catches most of this at build time, but only once the whole module
compiles, which is a slow way to learn that a locale is missing forty keys. This
answers the same questions in a second, and says which key and which file.

Usage:
    scripts/ci/check-translations.py            # every values-* locale present
    scripts/ci/check-translations.py fr ru      # only these
"""

import glob
import os
import re
import sys
import xml.etree.ElementTree as ET
from collections import defaultdict

BASE_DIR = "app/src/main/res/values"

# Plural categories each locale must supply, per CLDR. A locale that omits one
# renders the wrong form for those counts, silently.
REQUIRED_PLURALS = {
    "fr": {"one", "many", "other"},
    "es": {"one", "many", "other"},
    "de": {"one", "other"},
    "ru": {"one", "few", "many", "other"},
    "hi": {"one", "other"},
    "zh-rCN": {"other"},
    "zh-rTW": {"other"},
    "b+zh+Hans": {"other"},
    "b+zh+Hant": {"other"},
}

FORMAT_ARG = re.compile(r"%(\d+)\$([a-zA-Z])")
BARE_FORMAT = re.compile(r"%(?!%)(?!\d+\$)[-#+ 0,(]*\d*(?:\.\d+)?[a-zA-Z]")


class MalformedResource(Exception):
    pass


def parse(path):
    strings, plurals, untranslatable = {}, {}, set()
    try:
        root = ET.parse(path).getroot()
    except ET.ParseError as exc:
        raise MalformedResource(f"{path}: XML is malformed: {exc}") from exc
    for node in root:
        name = node.get("name")
        if not name:
            continue
        if node.get("translatable") == "false":
            untranslatable.add(name)
            continue
        if node.tag == "string":
            strings[name] = "".join(node.itertext())
        elif node.tag == "plurals":
            plurals[name] = {
                item.get("quantity"): "".join(item.itertext()) for item in node
            }
    return strings, plurals, untranslatable


def args_of(text):
    """Which index carries which conversion. Order in the sentence may differ
    between languages; the index-to-type mapping may not."""
    return {int(i): c.lower() for i, c in FORMAT_ARG.findall(text)}


def check_locale(locale, base):
    base_strings, base_plurals, base_untranslatable = base
    loc_dir = f"app/src/main/res/values-{locale}"
    problems = []
    loc_strings, loc_plurals, loc_untranslatable = {}, {}, set()

    for path in sorted(glob.glob(f"{loc_dir}/*.xml")):
        try:
            s, p, u = parse(path)
        except MalformedResource as exc:
            return [str(exc)]
        for name in s:
            if name in loc_strings:
                problems.append(f"{locale}: duplicate string '{name}'")
        loc_strings.update(s)
        loc_plurals.update(p)
        loc_untranslatable |= u

    if not loc_strings and not loc_plurals:
        return [f"{locale}: no strings found under {loc_dir}"]

    missing = (set(base_strings) - set(loc_strings)) | (
        set(base_plurals) - set(loc_plurals)
    )
    for name in sorted(missing):
        problems.append(f"{locale}: MISSING '{name}'")

    extra = (set(loc_strings) - set(base_strings)) | (
        set(loc_plurals) - set(base_plurals)
    )
    for name in sorted(extra):
        if name in base_untranslatable:
            problems.append(
                f"{locale}: TRANSLATED '{name}', which is marked translatable=false"
            )
        else:
            problems.append(f"{locale}: EXTRA '{name}' has no English original")

    for name, text in sorted(loc_strings.items()):
        if name not in base_strings:
            continue
        want, got = args_of(base_strings[name]), args_of(text)
        if want != got:
            problems.append(
                f"{locale}: ARGS '{name}' expects {want or 'none'}, has {got or 'none'}"
            )
        if BARE_FORMAT.search(text) and not BARE_FORMAT.search(base_strings[name]):
            problems.append(
                f"{locale}: ARGS '{name}' uses a non-positional format specifier"
            )

    required = REQUIRED_PLURALS.get(locale)
    for name, forms in sorted(loc_plurals.items()):
        if name not in base_plurals:
            continue
        if required and not required <= set(forms):
            absent = ", ".join(sorted(required - set(forms)))
            problems.append(f"{locale}: PLURAL '{name}' is missing quantity: {absent}")
        # English often hardcodes the numeral in its `one` form ("1 episode"), while a locale
        # whose `one` also covers zero needs the argument there. So a translated form may use
        # any argument the base uses somewhere in the plural, but never invent a new index.
        available = {}
        for text in base_plurals[name].values():
            available.update(args_of(text))
        for quantity, text in forms.items():
            got = args_of(text)
            unknown = {i: c for i, c in got.items() if available.get(i) != c}
            if unknown:
                problems.append(
                    f"{locale}: PLURAL '{name}' [{quantity}] uses {unknown}, "
                    f"which the English plural never defines ({available or 'no arguments'})"
                )

    return problems


def main():
    if not os.path.isdir(BASE_DIR):
        print(f"run this from the repo root: {BASE_DIR} not found", file=sys.stderr)
        return 2

    base_strings, base_plurals, base_untranslatable = {}, {}, set()
    for path in sorted(glob.glob(f"{BASE_DIR}/strings*.xml")):
        try:
            s, p, u = parse(path)
        except MalformedResource as exc:
            print(exc, file=sys.stderr)
            return 2
        base_strings.update(s)
        base_plurals.update(p)
        base_untranslatable |= u

    wanted = sys.argv[1:]
    locales = wanted or [
        os.path.basename(d)[len("values-") :]
        for d in sorted(glob.glob("app/src/main/res/values-*"))
        if glob.glob(f"{d}/strings*.xml")
    ]
    if not locales:
        print("no translated locales yet; nothing to check")
        return 0

    base = (base_strings, base_plurals, base_untranslatable)
    total = len(base_strings) + len(base_plurals)
    failed = False
    by_locale = defaultdict(list)
    for locale in locales:
        by_locale[locale] = check_locale(locale, base)

    for locale in locales:
        problems = by_locale[locale]
        if problems:
            failed = True
            print(f"\n{locale}: {len(problems)} problem(s)")
            for line in problems[:40]:
                print(f"  {line}")
            if len(problems) > 40:
                print(f"  ... and {len(problems) - 40} more")
        else:
            print(f"{locale}: complete, {total} keys match the base")

    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
