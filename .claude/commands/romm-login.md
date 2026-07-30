Configure authenticated access to the user's RomM instance. Token-based
only; never document, request, or use a username/password (that flow is
being retired).

1. Config resolves from exported env vars, then `.claude/.env`, then
   project-root `.env` (template: `.env.example`). Required:
   `ROMM_BASE_URL` = the user's RomM instance URL.
2. The long-lived client API token lives in a file (default
   `~/.romm-client-token`, override `ROMM_CLIENT_TOKEN_FILE`). The user
   creates it in their RomM instance (Settings > Client Tokens, or
   `POST /api/client-tokens`) and pastes it into that file.
3. Verify:

```bash
scripts/romm-auth.sh status
```

It reports the resolved base URL and auth source. If unconfigured, walk the
user through steps 1-2; do not improvise other auth paths.
