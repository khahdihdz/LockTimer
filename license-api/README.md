# LockTimer License API

Cloudflare Workers + D1 backend for mandatory app activation.

## Deploy from this directory
```bash
cd license-api
npx wrangler d1 create locktimer-license
# Copy database_id into wrangler.toml
npx wrangler d1 execute locktimer-license --remote --file=./schema.sql
npx wrangler secret put ADMIN_TOKEN
npx wrangler deploy
``

Set `LICENSE_API_URL` in `app/build.gradle.kts` to the deployed Worker origin before building APK. Default is `https://locktimer-license.khahdihdz.workers.dev`; change it if your Workers subdomain differs.

## Endpoints
- `POST /api/trial`: starts/retrieves one 7-day trial per Android ID.
- `POST /api/activate`: activates a key and binds it to a device.
- `POST /api/validate`: validates the current device on app launch.
- `POST /api/admin/keys`: create keys. JSON: `{"count":5,"days":365,"maxDevices":1}`. Use `days:0` for perpetual keys.
- `GET /api/admin/keys`: list key fingerprints/status.
- `PATCH /api/admin/keys/:id`: body `{"status":"revoked"}` to revoke.

Admin endpoints require `Authorization: Bearer $ADMIN_TOKEN`. Plaintext keys are returned only once at creation. Store them securely.

## Important
The app requires a successful online validation on launch; no offline grace period is enabled. The D1 database stores key hashes, not plaintext keys. Device binding uses Android ID. Keep `ADMIN_TOKEN` secret and never put it in the APK.

The Android release workflow is unchanged: it still delegates APK signing to `khahdihdz/apksign`.
