# Current Maro handoff

## Local 1.0.5 GUI and hand placement update

- The db199e9d request, repeated identically in fd06785d, supersedes the prior design
  freeze: reference-style charcoal/purple GUI, natural Inter Regular/SemiBold,
  horizontal real-category navigation, two-column browser, independent large
  settings panel, real profile selector and editable numeric fields.
- Fixed Screen GUI canvas is now 1080×640. Preserve its stationary anchor and
  existing exact projection, depth occlusion, input and resource ownership.
- View Model has a physical left/right HandPlacementScreen with detached draft,
  mirrored linked deltas, resets, Save/Cancel, native empty arms, projection-aware
  dragging and no inventory mutation or module activation. Legacy Main/Off keys
  persist with physicalMainArm extra binding; resolve it lazily because options
  are null during onInitializeClient. Saved section titles show physical arms.
- HandPlacementRender repeats only a private native hand pass under Streamproof,
  using a reusable pre-hand color snapshot; the clean target keeps ordinary
  hands. It restores projection, output targets and native lighting state.
- All six gates passed: focused native editor/GUI, fresh-JVM exact persistence,
  the full suite with Sodium/ImmediatelyFast/EntityCulling/Lithium/FerriteCore/
  Meteor, stationary world GUI, all 38 unified previews, and normal build.
  Actual controls/pixels, physical handedness, input isolation, swing/equip/use,
  resizing/scales/FOV/fullscreen/reload, cleanup and clean/private targets passed.
  Inherited fullscreen blit debug warnings remain; OS capture apps are unverified.
- Normal JAR SHA256 is
  `6381a118fb04efc50593275b68518f248c3eef43d088b94c44b4af30e5480d3b`,
  42,299,908 bytes. All 12 character assets match 1.0.4; test probes and retired
  modules are absent. Report, six-gate manifest and native PNGs are in co/outputs.
- User requested pushing this release before installation. Source belongs on
  `codex/smooth-rendering`. 1.0.5 is staged in the Modrinth profile's maro-updates;
  Minecraft was still running with 1.0.4 at the last check. Recheck after push.
  `co/work/install-maro-1.0.5.ps1` validates the release, backs up outside mods,
  installs only when the game is closed, and writes an installation receipt.
  Never terminate Minecraft to replace its loaded JAR.

## Local 1.0.4 stationary Screen GUI

- User explicitly canceled the screenshot redesign and resumed only the fixed
  world-panel request. Preserve current design, fonts, categories and flat GUI.
- `ScreenGuiPose` captures centre, upright yaw basis and physical size once.
  Removed per-frame lookAlong billboarding; uses exact WorldRenderer view and
  projection matrices on double-precision camera-relative coordinates. Screen
  GUI reserves 1080x480 with fixed browser/preview/settings columns, so opening
  settings, resizing or GUI scale cannot move/resize its geometry. Back-side UVs
  stay readable without rotating the basis. Reopening creates a new anchor.
- `ScreenGuiWorldDepthMixin` snapshots actual terrain depth before hand/GUI clears.
  Surface compositor performs depth occlusion. CPU picks invert the same rendered
  projection, start at its actual near ray and reject outside/blocked/behind picks.
  Widget drags consume relative mouse motion without player look; walking remains
  available. Offscreen previews suspend their timestamp/work and resume their
  presentation. Floating Window keeps its prior Preview flat-pointer behavior.
- Surface owns Minecraft's six native special renderers and dynamic item subjects
  register on that canvas. This is necessary for actual player/item pixels in the
  world preview, not merely queued render states. Owned targets and depth snapshot
  release on close/reload; no second world render/session is used by the feature.
- Four final gates passed: stationary native, identical full suite with Sodium,
  ImmediatelyFast, EntityCulling, Lithium, FerriteCore and Meteor; flat/unified
  preview regression (38 supported/49 unsupported); and normal build. Native
  tests cover actual WASD, full yaw/up/down, front/oblique/far/edge/back/away,
  landmark pixels, native controls/dropdown/tooltip/model drag, actual ESP/item
  pixels, partial/full wall occlusion and blocked clicks, hidden preview resume,
  three sizes/scales2/3/4, resource reload, fresh reopen and 20M-coordinate picking.
- No reference video file was available. Followed written behavior. Block-outline
  picking can differ from transparency/foliage/fluid depth; unlisted renderers
  need verification. Latest stationary runs with synchronous diagnostics had no
  invalid-enum messages; inherited fullscreen blit warning remains in flat tests.
  OS capture apps remain unverified. See `docs/stationary-screen-gui.md`.
- Normal tested JAR SHA256:
  `f37c9c4101b7bc59c9187ae1d9beeb39ab50a730aee3b8a7582c6d26d3be56df`.
  All12 character assets match1.0.3; gametest probes/removed modules are absent.
- JAR, validation, report and native PNG evidence are in
  `C:/Users/havn3/Documents/Codex/2026-10-24/co/outputs/`.
  The user closed Minecraft and 1.0.4 is installed as the only active Maro JAR
  in the Modrinth profile's `mods` folder. Its checksum was independently
  verified. Previous 1.0.3 is checksum-verified outside `mods` in
  `maro-backups/client-1.0.4-1111343f/`. Installation receipt:
  `maro-1.0.4-installation.json` in the output folder. Never terminate the user's
  game for an update. Reusable installer:
  `C:/Users/havn3/Documents/Codex/2026-10-24/co/work/install-maro-1.0.4.ps1`.

## Local 1.0.3 unified preview update

- The user requested the new shared three-column preview system. It supersedes
  the older upper-right/inline preview layout described below. Supported modules
  open Preview by default, with an independent middle panel and editable settings
  on the right; Settings hides the panel. See `docs/unified-previews.md`.
- Explicit typed providers cover 38 modules; 49 unsupported modules have no tab.
  Player previews use private skin/equipment state, actual effect paths and a
  paused presentation clock. SELF/OTHERS, front/back/reset, drag/zoom and animation
  controls never activate gameplay. Wardrobe/Cosmetics are asset editors with
  shared Preview actions; the old world-camera ESP preview is removed.
- Preview temporarily uses a flat unlocked pointer in Screen GUI/Floating Window
  modes, preserving and restoring saved preferences. Module switches clear stale
  settings input. Owned item/shader targets, isolated subjects and clocks stop and
  release on hide/close/switch/reload/shutdown.
- Native `unifiedPreviewTestOnly` passed, then passed again with Sodium,
  ImmediatelyFast, EntityCulling, Lithium, FerriteCore and Meteor. The normal build
  passed. Tests covered all providers, real controls, paused setting pixel edits,
  player/inventory/entity/packet isolation, both world-GUI input preferences,
  native fullscreen, scales 2/3/4, three sizes, reload and internal clean/private
  Streamproof targets. No canceled schematic/Auto Builder tests were run.
- Both native logs contain transient fullscreen-resize OpenGL blit debug messages.
  Settled layout, native preview and final GL error checks pass; do not claim
  transition-frame smoothness across drivers. OS capture remains unverified.
- All 12 bundled character assets remain byte-identical to 1.0.2. Normal JAR:
  `56257f9178a10b82bbec98e7e479da32b6c19410f60e2db14a1a98dacb0a28ce`.
- The JAR and validation/report/evidence are in
  `C:/Users/havn3/Documents/Codex/2026-10-24/co/outputs/`.
  The user closed Minecraft and 1.0.3 is now installed as the only active Maro
  JAR in the Modrinth profile's `mods` folder. Its checksum was independently
  verified. Previous 1.0.2 is checksum-verified outside `mods` in
  `maro-backups/client-1.0.3-21fab596/`. The installation receipt is
  `maro-1.0.3-installation.json` in the output folder. Never terminate the user's
  game for an update. The reusable installer is
  `C:/Users/havn3/Documents/Codex/2026-10-24/co/work/install-maro-1.0.3.ps1`.

## Local 1.0.2 verified build

- Continue on `codex/smooth-rendering`; preserve the approved 1.0.1 rendering and
  model repairs below. No Fall is now also removed, including its module-only
  tick/packet mixins and tests. Do not restore any of the three removed modules.
- Free Cam defaults to 96 blocks/s, supports 0.5–4096 plus its sprint multiplier,
  and integrates normalized 3D movement by elapsed time. Coords Fly adds current
  Position and Nether rows with exact dimension checks. The native
  `movementCoordinatesTestOnly` suite passed; see `docs/movement-coordinate-update.md`.
- GUI backgrounds support solid/gradient/transparent/imported PNG modes through
  Maro's own private file picker. Player appearance previews use explicit
  capabilities and appear beside qualifying settings. Preserve these new controls.
- Streamproof is being verified as one world render with clean native Minecraft
  presentation and a capture-excluded local presentation window. Incompatible
  scene effects pause through `StreamproofScenePolicy` and restore on disable.
  Requested enabled states must remain saved while paused. Shutdown clears the
  policy without reactivating modules. Fake Block's initial rebuild readiness is
  latched once; later normal terrain activity must not make private visuals blink.
- Five focused native suites passed: movement/coordinates, backgrounds/picker/blur,
  player previews, Streamproof/policy/failure recovery, and menus with Sodium,
  ImmediatelyFast, EntityCulling, Lithium, FerriteCore and Meteor 1.21.11-86.
  The final normal `build` passed. Streamproof verified 59 world renders over 59
  frames and live failure recovery with 13,064 changed terrain samples.
- OS capture control was stopped with Escape. Windows display/window capture,
  OBS, Discord, WGC and DXGI are **unverified**; do not infer their support from
  internal GPU/front-buffer checks. `docs/streamproof.md` records exact limits.
- Tested normal JAR SHA-256:
  `446f1b75e4200d7c6bf4d076fa17f4fe6ec5c95110f0034b722562a0146a083f`.
  Download and validation receipt are in
  `C:/Users/havn3/Documents/Codex/2026-10-24/co/outputs/`.
  The same JAR is staged in the Modrinth profile's `maro-updates` directory and
  installed as the only active Maro JAR in `mods`. The user closed Minecraft;
  installation and SHA-256 verification succeeded on October 25, 2026.
  Previous 1.0.1 JAR is checksum-verified in
  `maro-backups/client-1.0.2-9b9bd4be/`. Never stop the user's game.
  Retry `C:/Users/havn3/Documents/Codex/2026-10-24/co/work/install-maro-1.0.2.ps1`
  after the user closes the game. It verifies validation/checksums, backs up old
  Maro JARs outside `mods`, and installs one active JAR with rollback on failure.

## Local 1.0.1 rendering update

- Work from `codex/smooth-rendering`, based on the installed `build-710` source.
- Trident Boost and Air Stuck are removed, including their hooks. Do not restore them.
- Shared text and Lucide icons use visible-ink centering and grayscale antialiasing.
  Character texture wrapping matches the original repeated UVs; original image bytes
  are retained. See `docs/rendering-quality.md` for implementation and checks.
- User preference: after every verified Maro update, install the normal remapped JAR
  in `C:/Users/havn3/AppData/Roaming/ModrinthApp/profiles/Fabric 1.21.11/mods`.
  Move the previous Maro JAR to a backup folder outside `mods`; keep one active Maro
  JAR and verify its copy. Never stop the user's running Minecraft to replace a file.

## Earlier published baseline

The default branch now contains the tested GUI and base-counter release from October 18, 2026.
Minecraft 1.21.11, Fabric, Java 21. Start from this branch's current HEAD.

## Current scope and user instructions

- The user canceled schematic/Auto Builder work. Do not resume its implementation or tests unless the user asks again. The released builder code is preserved; unpublished builder experiments are excluded from this snapshot.
- Protection work is frozen. The normal client remains auth-free. Do not change protection or its credentials/signing setup without a new user request.
- Preserve the blue theme, flat module lists, paged settings, new middle preview panel, jump-circle preview repair, right-click module toggle, and main-menu branding from the current GUI release.
- Do not ask routine clarification questions; make reasonable implementation choices. Respect any new user instructions.

## Base counter

Enable Base ESP for the compact top-right counter. Open chat to use + / -; corrections and unique discoveries save independently of config profiles. Nearby bases expands the counter with coordinates and estimated condition. Likely raided sites are hidden by default but still count.

Storage ESP and Block ESP scan results schedule independent verification. Repeated sightings, scanned surrounding chunks, substantial constructed/storage evidence, and local generated-structure rejection precede counting. Spatial identities survive revisits and are scoped by server/world plus dimension. Two copies of the same template at different places both count.

Conditions are likely intact / likely raided / uncertain. Storage loss requires a complete fresh original footprint. Unloaded chunks must never cause a raid downgrade. Unopened chest loot is unavailable, so do not promise an unraided base or loot. Very small/erased bases and server-masked blocks can be missed. The natural filter is conservative and may reject player builds decorated like generated structures.

Relevant code:

- `src/main/java/dev/maro/module/impl/visuals/BaseESP.java`
- `src/main/java/dev/maro/module/impl/visuals/BlockESP.java` (Storage ESP inherits it)
- `src/main/java/dev/maro/render/esp/BaseEvidence.java`
- `src/main/java/dev/maro/render/esp/BaseDiscoveryLog.java`
- `src/gametest/java/dev/maro/gametest/BaseCounterChecks.java`
- `docs/base-counter.md`

## Build and validate

```sh
./gradlew remapJar remapGametestJar
./gradlew runProductionClientGameTest -PbaseCounterTestOnly=true
./gradlew runProductionClientGameTest -PbaseEspTestOnly=true
```

On Windows use `gradlew.bat`. Native tests create disposable local worlds and do not log into Donut SMP. GitHub CI runs the two current ESP suites before publishing a release. The normal build does not enable the optional protected edition.

The native test rejected actual vanilla trial-chamber and ancient-city templates with extra dense storage. It also verified five distinct sites, two raid estimates, both ESP evidence sources, real mouse corrections, duplicate prevention, toggles, storage loss, and chunk unload. An independent clean source build produced all 45 ESP runtime entries byte-for-byte identical to the tested JAR.

See `docs/releases/base-counter-7b562bb4.json` for hashes and validation details. `BUILD-SNAPSHOT.json` is the original tested desktop source manifest; the GitHub workflow and this handoff are the documented publishing-only differences. The desktop JAR SHA-256 is `7b562bb4ceaa74634e94fe6585608a1363a92109fe37399867891121884c384b`. CI may produce a different whole-JAR hash because its version includes the workflow build number.
