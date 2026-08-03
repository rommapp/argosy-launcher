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

| Field | 4.9 | 5.0 | 5.1 |
|---|---|---|---|
| `files[].category` for root game files | ? | ? | `"game"` |
| `files[].is_top_level` | ? | ? | present |
| `files[].crc_hash`/`md5_hash`/`sha1_hash` | ? | ? | present |
| `files` populated on `GET /api/roms?with_files=true` | ? | ? | yes |
| rom `multi` | ? | ? | absent |
| folder rom `fs_name` | ? | ? | bare directory name, no extension |
| folder rom `fs_size_bytes` | ? | ? | sum of `category: "game"` files only |

The 5.1 column is from a live 5.1.0-alpha.4 instance. Fill the rest in from this
testbed rather than assuming continuity backwards.
