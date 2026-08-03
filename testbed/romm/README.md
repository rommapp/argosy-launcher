# RomM version testbed

Three released RomM versions, one shared read-only library, so an API response
shape can be compared across the versions Argosy actually supports.

Argosy's support floor is the latest three minor releases. As of 2026-08-03 that
is **4.9, 5.0, 5.1**, pinned here at 4.9.2, 5.0.0 and 5.1.0.

## Why this exists

`RomMCapabilities` gates *features* by version, but nothing has ever recorded how
the response *shape* changes between versions. That gap is what issue #173 ran
into: root game files come back as `category: "game"` on 5.1, while three call
sites in the client still look for `category == null`. Nobody could say which
versions emit which, because there was no way to ask more than one server.

## Setup

```
cp .env.example .env
openssl rand -hex 32     # paste into ROMM_AUTH_SECRET_KEY
```

`TESTBED_LIBRARY` defaults to the `romm_mock` library in the sibling RomM
checkout. Any RomM-shaped library works; it is mounted read-only so three
concurrent scans cannot mutate the sample or each other's view.

## Running

The database comes up once and is shared by all three, as three separate
databases. Bring up whichever versions you need:

```
docker compose -f docker-compose.base.yml -f romm-5.1.yml up -d
docker compose -f docker-compose.base.yml -f romm-4.9.yml -f romm-5.0.yml -f romm-5.1.yml up -d
```

| Version | URL                   | Database   |
|---------|-----------------------|------------|
| 4.9.2   | http://localhost:8091 | `romm_v49` |
| 5.0.0   | http://localhost:8092 | `romm_v50` |
| 5.1.0   | http://localhost:8093 | `romm_v51` |

Each instance needs its own first-run admin user and its own library scan. Scans
are the slow part, which is why the sample library matters more than its
realism.

Creating the admin over the API needs the CSRF cookie echoed back as a header,
and the cookie is `romm_csrftoken` (no underscore before `token`):

```
CSRF=$(curl -s -c jar http://localhost:8093/api/heartbeat -o /dev/null -D - \
  | sed -n 's/^[Ss]et-[Cc]ookie: romm_csrftoken=\([^;]*\);.*/\1/p')
curl -s -b jar -X POST http://localhost:8093/api/users \
  -H 'Content-Type: application/json' -H "x-csrftoken: $CSRF" \
  -d '{"username":"testbed","password":"testbed123","email":"testbed@local.invalid","role":"admin"}'
```

`email` is required even though the UI implies otherwise. Without the header the
request fails 403 with "CSRF token verification failed", which reads like an auth
problem and is not one.

State-changing calls after login also need `Origin` and `Referer` set to the
instance, on top of the CSRF header and the `romm_session` cookie.

**Scans are driven over socket.io, not REST.** `POST /api/tasks/run/scan_library`
answers 400 "cannot be run" because that task is `manual_run: false`; it is the
nightly scheduled job, not the scan the UI performs. Connect a socket.io client
to path `/ws/socket.io/` carrying the `romm_session` cookie and emit:

```
socket.emit("scan", {
  platforms: [], platform_fs_slugs: [], type: "quick",
  apis: [], launchbox_remote_enabled: false, playmatch_enabled: false
})
```

Empty platform lists scan everything. Progress arrives as `scan:scanning_platform`
and completion as `scan:done`; a refusal (usually "a scan is already in progress")
arrives as `scan:done_ko`. Scan types are `new_platforms`, `quick`, `update`,
`unmatched`, `complete`, `hashes`. A `quick` scan of the mock library takes under
90 seconds per instance and all three can run at once.

Tear down with `docker compose -f docker-compose.base.yml -f romm-<v>.yml down`;
add `-v` to discard that version's database, resources and assets.

## One-way migrations

Each version gets its own database on purpose. RomM's schema migrations only run
forward, so a database a newer RomM has already migrated is not a supported
input to an older one. Pointing two versions at one database measures migration
damage, not the shape each version ships.

## Adding a version

Copy a `romm-<version>.yml`, change the image tag, database name, port and volume
prefix, and add the database to `initdb/01-databases.sql`. That file only runs on
a fresh database volume, so an existing testbed needs the `CREATE DATABASE` and
`GRANT` applied by hand or the volume recreated.

When a new minor ships, the oldest of the three drops off the floor and its
compose file goes with it.

## Shape differences found so far

Recorded against the versions where they were observed, so the next person has a
baseline instead of a ritual.

Measured on this testbed against the same library, 2026-08-04.

| Field | 4.9.2 | 5.0.0 | 5.1.0 |
|---|---|---|---|
| `files[].category` for root game files | **`null`** | **`"game"`** | **`"game"`** |
| `files[].category` for categorized files | `"update"` etc. | same | same |
| `files[].is_top_level` | **absent** | present | present |
| `files[].crc_hash`/`md5_hash`/`sha1_hash` | present | present | present |
| `files` populated on `GET /api/roms?with_files=true` | yes | yes | yes |
| rom `multi` | absent | absent | absent |
| folder rom `fs_name` | bare directory name | same | same |
| rom `fs_path` | parent dir of the game folder | same | same |
| `files[].file_path` | full dir incl. game folder | same | same |

### What this settles

**`category` for root game files flipped at 5.0.** Argosy's three call sites that
test `category == null` (`RomMGameFileSync.kt:39`, `RomMGameMetadata.kt:86`,
`DownloadDelegate.kt:442`) were written correctly for 4.9 and silently broke at
5.0: on 4.9 the `!= null` filter drops every root file, and on 5.0+ the `== null`
selectors match nothing. Both readings of the bug were right, for different
servers. Accept `null || "game"`; do not flip the comparison, and do not gate it
on version, because both shapes are inside the supported window.

**`is_top_level` is 5.0+.** It is the cleanest shape signal available but cannot
be relied on at the floor. Treat it as preferred-when-present.

**Per-file hashes are available on all three**, so disambiguating two candidate
files by content works everywhere in the supported window.

**`multi` is absent on every supported version.** `RomMModels` maps it, so it has
always deserialized to its default. Dead field.

**The layout below the platform directory is fully derivable on all three**: rom
`fs_path` is the parent, `files[].file_path` is the full directory including the
game folder, and `file_name` is a basename. Subtracting the former from the
latter gives the exact relative layout.

To re-measure after a version bump, scan each instance and compare
`GET /api/roms?limit=2000&with_files=true`.
