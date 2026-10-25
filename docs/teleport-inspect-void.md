# Teleport, inspect and bedrock modules

Minecraft 1.21.11 / Java 21 / Fabric. These modules use Maro's normal module list,
settings, keybinds and configuration. Meteor is optional.

## Controls

- **Misc → Tpahere Kill Bot:** configure the command, request order, letters,
  delay and excluded names. Requests pause for nearby players; combat uses the
  native attack cooldown, line of sight, entity reach and published look angle.
  Maro friends are excluded from requests and combat. Healing, respawning and
  selling have separate settings. The module starts disabled on each launch.
  **Attack Mode** selects **Sword** (the default), **Bow Spam**, or **Both**. Sword mode
  equips a hotbar sword and respects the native melee cooldown. A fixed Sword
  Slot containing another item pauses combat.
  Bow Spam automatically selects a usable hotbar bow and starts firing
  when a live, eligible player enters Bow Range; no held right-click is needed.
  It stops when that player dies, starts its death
  animation, leaves range or disappears. An active draw is cancelled without
  firing a final arrow. It waits for another live player before firing again.
  The minimum draw is **Bow Charge (ticks)** (4–20, default 5).
  **Shot Delay (ticks)** controls the wait between draws. **Bow Slot**
  is 1–9 or 0 for automatic selection. **Bow Range** is independent of Pause
  Radius. **Adaptive Bow Charge** increases the minimum draw up to 20 ticks
  when the configured charge cannot reach the target. Turning it off keeps
  the configured charge and skips unreachable shots.
  Bow aiming uses a low trajectory with arrow drag/gravity, bounded camera
  turns and the last published look angle. Target selection remains stable
  until the player becomes invalid, excluded, out of range or occluded.
  The curved arrow path must clear solid blocks and intervening players.
  Missing ammunition, a nearly broken bow, other item uses and open screens
  pause combat. Friends, teammates, spectators and Never Request names are
  excluded. Healing, disabling, switching modes and losing a target cancel
  owned draws by selecting a different non-bow hotbar item instead of firing.
  Keep a non-bow slot available for server-side cancellation. The bot never
  changes a manually selected slot while cancelling a draw.
  Requests and Auto Sell pause for a valid combat target even outside Pause
  Radius. Auto Sell retains bows and arrows while Bow Spam is selected.
  **Auto Lock** turns toward the target for either weapon; disabling it leaves
  the camera under manual control and only allows attacks aligned with the
  published look. **Auto Shoot** controls automatic bow drawing and releasing.
  With Auto Shoot off, Auto Lock can still aim while you draw/release manually.
  Other item uses retain priority, and manually holding use takes over a draw.
  **Both** chooses the bow for armor points below **Armor Cutoff** by default,
  and the sword at/above it. The default cutoff is 1 (unarmored versus armored).
  **Bow Rule** reverses this choice. **Ranged Bow Fallback** uses the bow beyond
  melee range regardless of armor; disabling it strictly follows the armor rule.
  Armor is checked every tick, so equipment changes switch weapons and cancel
  an owned draw before selecting the sword.
  Optional **Conservative Timing** requires full 20-tick bow draws, a minimum
  five-tick pause between draws, and a settled published aim. It defaults off.
  No Donut/public-server detection claim is made; validation uses a local server.
- **Misc → Tp Macro:** set a Player and choose `tpa` or `tpahere`. The interval
  controls repeated requests; recognized server cooldown messages pause them.
  Invalid names prevent activation. The module starts disabled on each launch.
- **Visuals → Weapon Inspect:** press **F** to inspect, or hold it to loop.
  Choose Butterfly, Karambit, Katana or None; None keeps the original weapon
  with Classic or Twirl animation. Inspect Key is rebindable. Size, speed,
  deploy animation, arms, trails and blade sparkle are configurable.
- **Staff Notifier:** the STAFF WATCH panel displays portraits, presence badges,
  proximity distance, ping and an empty state. Comfortable and Compact layouts
  keep their existing drag, resize, text size and name-list controls.
- **Visuals → Bedrock Void ESP:** searches loaded chunks for enclosed connected
  pockets in the Overworld bottom five layers and Nether floor/roof layers.
  Minimum/maximum size and range filter results. Each pocket has one region
  highlight. **Tracers** and **Labels** default to off; enable them for stable
  tracers or size/distance/coordinate labels. Existing saved preferences are retained.
  Scanning runs incrementally on the client thread, with a time budget and an
  operation cap. Updates are coalesced by chunk; dimension changes clear state.
- **Trident Util** was removed, including its dry-use and charge overrides.
  Auto Trident, Trident Boost and Air Stuck have also been removed.

## Validation

Run the focused production-client suite with:

```powershell
./gradlew.bat build runProductionClientGameTest -PfeaturePortsTestOnly=true
```

To include Meteor compatibility, add
`-PmeteorTestJar=C:/path/to/meteor-client-1.21.11-85.jar`.

The suite drives real command completion and command execution on an integrated
server, checks target exclusions and cooldown handling, observes combat packets
and look publication against a local player fixture, renders all inspect skins
and checks hold/release looping, tests staff tab-list packets and HUD layouts,
and changes seam-crossing bedrock pockets through server block updates. Nether
roof detection, layer switching and End/Overworld transitions are exercised.
Screenshots go to `run/screenshots`.
Bow checks observe actual native use/release timing and a server arrow hitting
a stationary target at twelve blocks, then exercise automatic player detection,
leaving/re-entering range, stopping a draw on death, repeating short draws,
friend and wall exclusions, missing ammunition, fixed slots, Sword switching
and cancellation on disable. Options checks exercise Auto Shoot/Auto Lock off/on,
both armor rules, actual melee packets, ranged fallback and full-charge timing.
Meteor is loaded for the compatibility run.

This suite uses a local test world; it does not validate a public server's
teleport confirmations, order menus or server-specific command responses.

See `THIRD_PARTY_NOTICES.md` and `LICENSE-Anubis` in the JAR for Anubis attribution
and licensing. The accompanying source archive contains the adapted source and
build files.
