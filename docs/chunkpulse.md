# Redstone Bypass / ChunkPulse

Integrated into Maro for **Minecraft Java 1.21.11, Fabric, Java 21**. `minecraft_version`, mappings, loader and Fabric API remain Gradle properties. The full project includes the wrapper, `fabric.mod.json` and registered mixins.

Enable **Visuals > Redstone Bypass** on an allowed multiplayer host. The default is `donutsmp.net`, including its subdomains. Singleplayer, unrelated hosts, substring lookalikes and disconnected sessions remain inactive. Each server address and dimension has separate saved observations. This detector only observes activity already sent within the server's loaded/send range; idle machinery may produce no evidence.

## Passive observations and evidence

Network observers use non-cancelling HEAD injections on the client network handler. They do not send, rewrite, delay or cancel packets. Local interaction observers only record short-lived break/use intent masks. The detector never requests inventories, automates movement, interacts with machines, or scans initial chunk palettes.

* Default ingestion band: **-64 <= Y < 0**. Exact block positions can include Y=-1; approximate coordinates within one block of either boundary are rejected. Block-light sections must be entirely inside the band and have a negative section Y. Only the below-threshold section range is stored.
* Exact piston block events and crafter/dispenser world events carry the most weight. Machine state changes include POWERED, EXTENDED, TRIGGERED, CRAFTING, LIT, OPEN, hopper ENABLED/LOCKED and target POWER. First states establish a weak baseline; identical states do not count as toggles.
* Redstone sounds, dust/smoke/flame particles, periodic item/TNT/falling-block/minecart spawns and distant tight hostile-mob groups provide supporting evidence. Item metadata supplies the actual item type; ordinary movement and later stack-count metadata cannot invent output spawns. Players, villagers and chest lid events are excluded.
* Generic observations within **10 blocks** of you are ignored. Only exact piston/crafter/dispenser events and comparator/repeater/observer toggles retain machine weight nearby. Break/use masks suppress effects within **3 blocks for 60 ticks**; tunnelling drops use a 5-block mask. Holding/placing a light-emitting block or lava bucket suppresses nearby section changes for 40 ticks. Fluid block updates always contribute zero.
* Light changes and entity output require a repeating cadence at the same position, with the same item/entity type and +/-1 block tolerance for output. Single light changes and isolated falling blocks cannot score. Hostile density needs at least **20 entities in one 3x3x3 grid cell**, outside a 24-block local radius. Generic observations near other players receive at most 10% weight.
* Counters decay over 10 seconds, 60 seconds and 10 minutes. Exact emitter histories detect tick-quantised and alternating clocks. Continuity is checked on every interval, so a long random prelude followed by a brief regular burst does not satisfy the sustained-clock rule.
* Adjacent chunks merge by eight-neighbour flood fill. **Generic evidence alone stays LOW. MEDIUM requires at least one fixed machine position with six effective events in its decaying window. HIGH/HUGE additionally require at least 60 seconds of periodicity and four distinct qualified machine positions.** HUGE also needs the configured score, chunk and position counts. If more than 60% of the chunk's attempted observations are locally caused, it stays LOW and is tagged SELF. Clusters containing SELF evidence conservatively stay LOW.

## Flat tiles and controls

Each flagged chunk draws one horizontal 16x16 quad with a dark charcoal fill and a thin confidence border (MEDIUM orange, HIGH red, HUGE magenta). Defaults: fill alpha .32, border .9, slow 1 Hz HIGH/HUGE pulse, labels off and farm alerts off. Shared borders on the same plane are removed; connected perimeters are thicker. No columns, vertical walls or tracers are drawn.

The recommended strong preset follows `floor(playerY)+.02`, shows tiles through terrain, and displays MEDIUM or stronger activity from **Y=-64 through -1**. Range defaults to 8 chunks. Tiles fade for 10 seconds after falling below threshold; **Show History** keeps a grey .1-alpha ghost. Dig guidance rotates with your view and shows distance/depth. The ranked activity list is off by default to keep the screen quiet.

Both Click GUI layouts expose exactly five settings: **Chunk Tiles**, **Render Distance**, **Dig Guidance**, **Dig Cue**, and **Show History**. Detection thresholds, self-mining suppression, cadence checks, depth limits, FOUND suppression and rendering defaults are automatic. The first load of an older config migrates its advanced controls to this preset, preserving the five visible preferences, server allowlist and saved discoveries. Local expert commands and legacy config fields remain compatible; they are absent from the normal settings UI. Prior pre-filter snapshots cannot carry old clock confidence into this version.

Rendering uses **WorldRenderEvents.END_MAIN**, the actual post-main/translucent world-render event available in Fabric API 0.141.6 for 1.21.11. It uses Maro's existing position/colour QUADS and LINES RenderPipelines, camera-relative vertices, shared batched buffers and the frame's frustum. This version does not expose legacy LAST/AFTER_TRANSLUCENT callbacks under those old names.

## Commands and exports

```text
/chunkpulse toggle
/chunkpulse list
/chunkpulse clear
/chunkpulse export
/chunkpulse export waypoints
/chunkpulse threshold high 160
/chunkpulse plane PLAYER
/chunkpulse plane FIXED
/chunkpulse plane CLUSTER
/chunkpulse see-through on
/chunkpulse labels off
/chunkpulse history on
/chunkpulse debug
```

Commands are client-side. `debug` toggles bounded recording and prints recent kept/dropped decisions with effective weights, including DROP_Y_ABOVE, DROP_SELF_RADIUS, DROP_SELF_MASK, DROP_SELF_LIGHT, DROP_FLUID, DROP_PLAYER_NEARBY, DROP_NON_PERIODIC and KEPT. The nearby-player reason means the signal was reduced to its displayed weight, rather than completely discarded.

Location JSON is stored per server/dimension under `config/maro/chunkpulse/`. Atomic writes, immutable snapshots and save revisions prevent an older delayed save from undoing Clear. History tiles are included. CSV and Xaero-style cluster-centroid `.points` exports go in `exports/`. When debug recording is enabled, export also writes `*-decisions.json` and `*-replay.json`. Replay v2 contains event, player-context and self-mask frames plus a tuning-config snapshot. Recording is local and bounded; it does not write a packet log during normal operation. CSV/waypoints describe the current ranked clusters; retained grey ghosts remain in the location JSON.

## Build, tests and replay

```powershell
./gradlew.bat build testChunkPulse
./gradlew.bat replayChunkPulse -PpulseReplay=docs/fixtures/chunkpulse-shaft-farm.json
./gradlew.bat replayChunkPulse -PpulseReplay=events.json -PpulseReplayConfig=config.json
./gradlew.bat runProductionClientGameTest -PchunkPulseTestOnly=true
```

On Unix use `./gradlew` with the same tasks. Output: `build/libs/maro-mc1.21.11-1.0.0+local.jar`.

Headless checks cover random/same-tick/alternating cadence, late-clock rejection, clustering, memory/work limits, depth boundaries, self masks, other-player weighting, periodic light and the complete simulated mining shaft from Y60 to -60. The miner alone creates no ChunkStats; a periodic farm at Y=-40, 30 blocks away, reaches HIGH. Context/mask replay reproduces the classification. Tile tests cover all planes, shared borders, fade and saved history. Native tests exercise the production mixins and singleplayer gate, native packet objects including item metadata, JSON dimension isolation, clear/save ordering, exports, flat rendering and GUI scales. A positive synthetic render fixture does not override the production multiplayer gate. Server-specific live validation on DonutSMP is separate from these tests.

Work is bounded per signal; multi-update packets cost proportional to their payload. Position, chunk, emitter, mask, tracked-entity, light, debug and rendering caches have caps. Clustering runs twice per second, player context once per client tick, and file writes run on one worker.

The same build also fixes Player ESP tracer endpoints for custom models: they resolve from the current frame's posed torso, after model submission, so flight, swimming, crouching and model sizing move the line with the visible body.

## Mine-down guidance and FOUND suppression

**Dig Guidance** is on by default and only signals **MEDIUM** or stronger hidden activity. Inside an active cluster's actual chunk footprint, the HUD shows **DIG DOWN**, live estimated blocks below your feet and the target Y band. Depth uses the highest stored exact machine Y, then another exact observation, then the band top. Descending updates the distance every frame. The selected hidden region gets a brighter pulse and thicker flat outline. Within **Alignment Radius = 6 blocks** of the band, the card changes to **TUNNEL TO ACTIVITY** with N/S/E/W offsets to the centroid.

Outside the footprint, the card points toward the nearest eligible hidden cluster by **3D centroid distance**, with higher tier breaking distance ties. It shows horizontal distance, depth, compass offsets and a stable cluster ID. The existing Nearest Direction setting can hide this outside card. Small GUI viewports prioritise the guidance card over the ranked list so the two do not overlap. **Dig Cue** defaults on: one local sound and toast on first entering each hidden HIGH/HUGE region per connection session. Changing dimensions or toggling the module does not reset a cue already used in that session.

A region becomes **REVEALED / FOUND** when:

1. You approach within **Reveal Radius = 24 blocks**, in 3D, of a stored exact machine event.
2. You remain inside its chunk footprint and within its Y band plus/minus 8 blocks for five continuous seconds. Leaving either resets the dwell timer.
3. A received block-state update exposes a real machine within the reveal radius. Identical machine updates still provide reveal evidence even though scoring deduplicates them. This also covers machine exposure before its activity reaches the flag threshold.
4. You explicitly mark it with the local found command.

Already nearby detections start as FOUND before any signal or cue is displayed. FOUND regions keep scoring in the background, while all dig/direction prompts and bright tiles are suppressed. With Show History off their tiles disappear. With it on they render as grey ghosts with **FOUND** tags, including when ordinary tile labels are off. The ranked HUD identifies found activity instead of prompting you toward it.

Discovery IDs are separate from the changing flood-fill seed. Their saved chunk ownership survives merges, changing activity boundaries, disconnects and dimension changes. Saved FOUND ownership does not expire when activity counters decay. Version 3 location files include this state; older version 1/2 location files load with no prior found markers. Guidance waits for saved state to finish loading, so reconnecting cannot briefly display a new dig cue before FOUND state is restored.

A found footprint remains frozen. An adjacent expansion must contain at least **Split Chunks = 3 new chunks** of fresh HIGH/HUGE evidence before receiving a separate HIDDEN identity. Only the new portion is scored for this test; the found farm's old clock cannot promote a short new burst. One or two new chunks remain suppressed with the parent. Marking a region unfound is an explicit manual override; ordinary proximity rules can reveal it again immediately if you remain close.

```text
/chunkpulse found
/chunkpulse found cp-<id>
/chunkpulse unfound
/chunkpulse unfound cp-<id>
/chunkpulse signal on
/chunkpulse signal off
/chunkpulse clusters
```

Omitting an ID selects the nearest currently active discovery. `clusters` lists stable ID, tier, HIDDEN/REVEALED state, depth below you and compass offsets. These commands never send a server command. Config JSON also exposes `signalEnabled`, `signalMinTier`, `digCue`, `alignRadius`, `revealRadius` and `splitChunks`; the common values are in the module's Dig Guidance settings section.

`debug` prints recent state transitions and, while enabled, new transitions with their conditions: `C1_EXACT_MACHINE`, `C2_BAND_DWELL`, `C3_BLOCK_UPDATE`, `C4_MANUAL_FOUND`, or `MANUAL_UNFOUND`. Initial proximity is tagged `INITIAL_NEAR`. Each CSV/waypoint export also writes a companion `*-clusters.json` with the saved identities, states, footprints and bounded transition history.

Discovery storage is bounded to 4,096 identities and 32,768 owned chunks per server/dimension. The registry preserves saved FOUND ownership at capacity instead of evicting it and sending false new signals; additional identities wait for an explicit Clear. The in-session cue set and exposure/transition caches are bounded too. `/chunkpulse clear` explicitly clears found markers together with activity/history for the current dimension.

`DiscoveryChecks` exercises a HIGH source at Y=-45 while the player descends from Y=60, including live 105/65/25-block depth values, automatic signal removal, first-detected-nearby suppression, five-second dwell/reset, direct machine exposure, manual controls, min-tier filtering, 3D selection, once/session cues, ghost visibility, JSON reconnect and fresh expansion isolation. Native tests additionally save/reload found states on disk, exercise unchanged machine-update reveal callbacks, and render outside/DIG DOWN/alignment/found-hidden/found-ghost states through the production drawing paths at GUI scales 2/3/4. The positive render fixture still does not bypass the production singleplayer/server gate.
