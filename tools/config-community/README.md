# Maro public module configs

Right-click a module card or row to open its settings, then click the small page icon in that settings header to share a preset, browse Top liked or New, search, preview changes, like/unlike, apply and undo. Public configs open only in this module context; the main GUI has no global public-config button or module picker. Settings returns to the retained module settings. The existing dots button also offers a right-click shortcut. The client starts with no public server selected.

## Run locally

Requires Python 3.11 or newer. There are no Python dependencies.

```sh
python server.py --host 127.0.0.1 --port 8787 --db community.sqlite
```

In Maro, open Public configs → Server and connect to `http://127.0.0.1:8787`. For another computer on your LAN, bind to `0.0.0.0` and use that machine's LAN address. Keep this local HTTP endpoint on your LAN.

## Deploy for your community

On a server with Docker Compose, point your domain's DNS to that server and allow inbound TCP ports 80 and 443. Copy this directory to the server.

1. Copy `.env.example` to `.env`.
2. Set `DOMAIN` to your domain (no scheme or path).
3. Generate a secret with `python -c "import secrets; print(secrets.token_urlsafe(48))"` and put it in `COMMUNITY_ADMIN_TOKEN`.
4. Run `docker compose up -d --build`.
5. Verify `https://YOUR_DOMAIN/health` returns `{"ok":true,"api":1}`.
6. Connect that HTTPS URL in Maro and share it with your community.

Caddy handles HTTPS. The API has no published host port and only trusts the proxy's overwritten `X-Real-IP` header in this arrangement. If hosting differently, leave `TRUST_PROXY` unset unless direct access to the API is blocked and your proxy overwrites that header. Use `docker compose logs` to investigate a failed startup. Public hosting and Docker execution are not performed by the client.

## Data and moderation

- SQLite persists posts, identities and likes in the `community_data` volume. Back up this volume with the service stopped, then start it again; do not copy only the main database while WAL writes are active.
- To remove a post as server owner, send `DELETE /api/v1/posts/POST_ID` with `Authorization: Bearer YOUR_ADMIN_TOKEN`. Authors can remove their own posts from the client.
- A local random identity has at most one like per post. Likes are idempotent, self-likes are rejected, and registrations/posts/votes are rate-limited. Display names are chosen names, not verified Minecraft accounts; likes rank popularity and do not certify a preset.
- Request bodies are capped at 96 KB, listing pages contain at most 50 summaries, and clients cap streamed replies at 512 KB. There are at most 32 concurrent API requests and 10,000 posts. For a larger public service, add account verification, operational monitoring and moderation suited to that community.
- No third-party JARs, executable code, enabled states or commands are shared. The client exports only supported numeric, toggle, mode, color and region settings plus selected Goliath destinations, ESP block selections and keyboard layouts. Text fields, credentials and keybinds remain local. The receiving client validates types, ranges, module and Minecraft version before changing anything.
- Client identity tokens are private in `<game directory>/maro/community-configs.json`. Keep that file to preserve ownership and likes. It is excluded from public presets. Back it up privately; do not distribute it with config exports.

## Test

```sh
python -m unittest discover -s . -p "test_*.py" -v
```

The Java native game tests use two independent clients against this API and verify publishing, liking, ranking, previews, module isolation, undo and screen rendering.
