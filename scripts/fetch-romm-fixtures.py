#!/usr/bin/env python3
# Fetches a curated set of games + platforms from RomM, downloads cover and
# screenshot images locally, and emits a fixtures manifest. One-shot script —
# run again only when you want to refresh the dummy data.
#
# Outputs:
#   vue-design/public/fixtures/covers/<gameId>.png
#   vue-design/public/fixtures/screenshots/<gameId>-N.jpg
#   vue-design/public/fixtures/logos/<platformSlug>.jpg
#   vue-design/src/fixtures/romm-manifest.json

import json
import os
import subprocess
import sys
import urllib.request
import urllib.parse
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
TOKEN_FILE = Path.home() / ".romm-client-token"
BASE_URL = "http://romm.nendo.xyz"
PUBLIC = REPO / "vue-design" / "public" / "fixtures"
MANIFEST = REPO / "vue-design" / "src" / "fixtures" / "romm-manifest.json"

# (search_term, preferred_platform_slug). Order matters — first match wins.
TARGETS = [
    ("Chrono Trigger",                   "snes"),
    ("Super Metroid",                    "snes"),
    ("The Legend of Zelda: A Link",      "snes"),
    ("Final Fantasy IX",                 "psx"),
    ("Metal Gear Solid",                 "psx"),
    ("Castlevania: Symphony",            "psx"),
    ("Shadow of the Colossus",           "ps2"),
    ("Metroid Fusion",                   "gba"),
    ("Pokemon FireRed",                  "gba"),
    ("The Legend of Zelda: Ocarina",     "n64"),
    ("Super Mario 64",                   "n64"),
    ("Sonic the Hedgehog",               "genesis"),
    ("Super Mario Bros",                 "nes"),
    ("Mega Man 2",                       "nes"),
    ("Pokemon Red",                      "gb"),
    ("Tetris",                           "gb"),
    ("Mario Kart DS",                    "nds"),
    ("Street Fighter II",                "arcade"),
]


def bearer():
    if not TOKEN_FILE.exists():
        print("No client token at", TOKEN_FILE, file=sys.stderr)
        sys.exit(1)
    return TOKEN_FILE.read_text().strip()


def api_get(path):
    url = f"{BASE_URL}{path}"
    req = urllib.request.Request(url, headers={"Authorization": f"Bearer {bearer()}"})
    with urllib.request.urlopen(req) as r:
        return json.load(r)


def download(url, dest: Path):
    if dest.exists():
        return
    dest.parent.mkdir(parents=True, exist_ok=True)
    headers = {"Authorization": f"Bearer {bearer()}"}
    req = urllib.request.Request(url, headers=headers)
    try:
        with urllib.request.urlopen(req, timeout=30) as r, open(dest, "wb") as f:
            f.write(r.read())
    except Exception as e:
        print(f"  ! download failed: {url} -> {e}", file=sys.stderr)


def strip_ts(path):
    # /assets/romm/resources/.../big.png?ts=2026-05-07 08:55:41 -> drop the ts so URL-encoding stays simple
    return path.split("?", 1)[0]


def pick(search_term: str, prefer_slug: str):
    q = urllib.parse.quote(search_term)
    data = api_get(f"/api/roms?search_term={q}&limit=20")
    items = data.get("items") if isinstance(data, dict) else data
    items = items or []
    # Prefer requested platform; fall back to first item
    for g in items:
        if g.get("platform_slug") == prefer_slug:
            return g
    return items[0] if items else None


def main():
    print(f"Fetching from {BASE_URL}, {len(TARGETS)} targets")
    PUBLIC.mkdir(parents=True, exist_ok=True)
    (PUBLIC / "covers").mkdir(exist_ok=True)
    (PUBLIC / "screenshots").mkdir(exist_ok=True)
    (PUBLIC / "logos").mkdir(exist_ok=True)
    MANIFEST.parent.mkdir(parents=True, exist_ok=True)

    games = []
    platform_slugs = set()

    for term, slug in TARGETS:
        g = pick(term, slug)
        if g is None:
            print(f"  - {term:40s} : not found")
            continue
        rid = g["id"]
        pslug = g["platform_slug"]
        platform_slugs.add(pslug)
        print(f"  - {term:40s} : id={rid}  slug={pslug}  ({g.get('name','')[:50]})")

        cover_local = None
        cover_remote = g.get("path_cover_large")
        if cover_remote:
            cover_path = strip_ts(cover_remote)
            dest = PUBLIC / "covers" / f"{rid}.png"
            download(f"{BASE_URL}{cover_path}", dest)
            if dest.exists() and dest.stat().st_size > 0:
                cover_local = f"/fixtures/covers/{rid}.png"

        screenshots_local = []
        for i, ss in enumerate((g.get("merged_screenshots") or [])[:4]):
            ss_path = strip_ts(ss)
            ext = ss_path.rsplit(".", 1)[-1].lower()
            dest = PUBLIC / "screenshots" / f"{rid}-{i}.{ext}"
            download(f"{BASE_URL}{ss_path}", dest)
            if dest.exists() and dest.stat().st_size > 0:
                screenshots_local.append(f"/fixtures/screenshots/{rid}-{i}.{ext}")

        md = g.get("metadatum") or {}
        igdb = g.get("igdb_metadata") or {}

        games.append({
            "id": str(rid),
            "title": g.get("name") or g.get("fs_name_no_ext") or term,
            "platformSlug": pslug,
            "coverArtUrl": cover_local,
            "backgroundArtUrl": cover_local,  # RomM doesn't generally have fanart; reuse cover
            "screenshots": screenshots_local,
            "summary": g.get("summary"),
            "developers": md.get("companies", []) or igdb.get("companies", []),
            "publishers": [],
            "releaseDate": None,  # could derive from igdb.first_release_date (unix seconds)
            "genres": md.get("genres", []) or igdb.get("genres", []),
            "gameModes": md.get("game_modes", []) or igdb.get("game_modes", []),
            "franchises": md.get("franchises", []) or igdb.get("franchises", []),
            "players": md.get("player_count"),
            "communityRating": md.get("average_rating") or igdb.get("total_rating"),
            "ageRating": None,
            "igdbId": g.get("igdb_id"),
            "fileSize": g.get("fs_size_bytes"),
            "fileName": g.get("fs_name"),
            "fileExtension": g.get("fs_extension"),
        })

        if igdb.get("first_release_date"):
            ts = igdb["first_release_date"]
            try:
                from datetime import datetime, timezone
                games[-1]["releaseDate"] = datetime.fromtimestamp(int(ts), tz=timezone.utc).date().isoformat()
            except Exception:
                pass

    # Now fetch platform metadata + logos
    print(f"\nFetching {len(platform_slugs)} platforms")
    all_platforms = api_get("/api/platforms")
    plat_index = {p["slug"]: p for p in all_platforms}
    platforms = []
    for slug in sorted(platform_slugs):
        p = plat_index.get(slug)
        if not p:
            continue
        logo_local = None
        if p.get("url_logo"):
            dest = PUBLIC / "logos" / f"{slug}.jpg"
            try:
                # url_logo points to images.igdb.com — no auth needed for public CDN
                urllib.request.urlretrieve(p["url_logo"], dest)
                logo_local = f"/fixtures/logos/{slug}.jpg"
            except Exception as e:
                print(f"  ! logo failed: {slug} -> {e}", file=sys.stderr)
        print(f"  - {slug:10s} : id={p['id']}  {p['name']}  logo={logo_local is not None}")
        platforms.append({
            "id": str(p["id"]),
            "slug": slug,
            "name": p["name"],
            "iconUrl": logo_local,
        })

    manifest = {"games": games, "platforms": platforms}
    MANIFEST.write_text(json.dumps(manifest, indent=2, ensure_ascii=False))
    print(f"\nWrote {MANIFEST.relative_to(REPO)}  ({len(games)} games, {len(platforms)} platforms)")


if __name__ == "__main__":
    main()
