Make an authenticated RomM API call against the configured instance.

Arguments format: `<METHOD> <endpoint> [data]`

Examples:
- `GET /api/platforms`
- `GET /api/roms?platform_id=1`
- `POST /api/search {"search_term": "mario"}`

Run the API call:
```bash
scripts/romm-auth.sh call $ARGUMENTS
```

If it reports missing config or auth, run `/romm-login` to set up the env
file and client token.
