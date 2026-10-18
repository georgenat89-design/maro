# Public per-module configs

Right-click a module card or row to open its settings, in Panel or Dropdown. Click the
small page icon in that settings header to open public configs for that module. There is no
global public-config button or module picker on the main GUI. Browse
Top liked or New, search and Preview a preset. Apply changes only that module's supported
settings; Undo apply restores the preceding values. Post config publishes a snapshot of that
module. Like/Unlike updates the shared server's count, and an author can delete their post.
Settings returns to that same module's usual settings or custom panel; Close returns to the
original GUI with its settings retained. The dots button opens settings with either
left-click or right-click, including custom galleries. Public configs use the page icon.

The client needs a community server URL before it can browse or post. Deploy the included
`tools/config-community` server following its README, then enter its HTTPS URL using Server.
The package includes Python/SQLite, Docker Compose, Caddy HTTPS configuration, moderation and
backup instructions. No public endpoint is preconfigured or hosted automatically.

Supported settings are toggles, numbers, modes, colors and screen regions, plus Goliath map
selections, ESP block colors and keyboard placement. Personal text fields, binds, activation,
accounts, files and credentials stay local. Unknown settings are skipped. Wrong modules,
Minecraft versions, unexpected root/extra fields, unsupported modes and out-of-range numbers
are rejected before mutation. A listener failure rolls the module back.

Public display names carry a community ID and are not verified Minecraft identities. Top liked
sorts the server's votes; it does not guarantee a config's effectiveness. Requests and replies
are bounded, network activity runs on one worker and tokens stay outside exported presets.

Validation: eight Python API tests cover concurrent/idempotent votes, ranking, search, module
filtering, limits, paging, authorization and restart persistence. Production Minecraft tests
with Meteor capture/validate all registered test-profile modules, exercise real HTTP requests
with two private identities, and verify Preview/Apply/Undo/Post/Like/Delete at three GUI scales.
The Docker deployment itself requires the user's server and domain and is not deployed by tests.
