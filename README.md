# maro.gg

A **Fabric 1.21.11 / Java 21** client with a smooth, modern ClickGUI and the 20 modules from Nathan's current 1.21.11 source. It runs directly on Fabric; Meteor Client is not required.

Open the menu in-game with **Right Shift** (you can change this in *Settings*).

## Included modules

**Staff Notifier** (Visuals) recreates SignalDebug's recovered staff-list settings
and default account names in Maro. Edit **staff names** for your server. It shows
staff currently listed in tab, their heads and ping, and join/leave toast and sound
alerts. Matching uses exact account names, case insensitive. Hidden tab entries
can optionally appear with an explicit **Hidden from tab** label; this does not establish why the server hid them.
Use **Place staff list** to drag/resize the panel. Native SignalDebug method bodies
were unavailable, so the tracker, HUD, and alerts are implemented independently.

The refined staff panel includes player heads, visible/hidden counts, nearby distance,
spectator labels, fading highlights, comfortable/compact layouts, and a proximity alert
cooldown. Eight user-supplied names were added: Frenk_Btw, Napooo_, BobisFound,
CryptoDaveYt, MunkerLich, u_vv, Fallerfly, and Dough4. Existing saved lists receive these
names once; subsequent edits/removals persist. Choose **Sound mode → SelectedStaff**
and edit **Sound staff** to play join sounds only for chosen accounts. Chime, Bell,
Soft and Alert tones have volume/pitch controls; leave and proximity sounds are optional.

**Anti Vanish** (Misc) adapts the Anubis detector into Maro. It consumes tab visibility,
removal, game-mode and chat packets; command-target suggestions; invisible player
metadata; and unexplained nearby sounds/particles. It suppresses ordinary departures,
bulk tab removals, local interactions, explosions, visible causes, redstone mechanisms,
villager door use, and ambient smoke sources. It shares Maro's editable staff names,
resets across world/shard changes, coalesces/rate-limits alerts, and provides a movable
evidence HUD. The completion probe requests suggestions for `minecraft:msg ` every
5 seconds by default; it does not send a chat message and can be disabled. Range,
probe interval, sounds, chat alerts, and notifications are configurable. Evidence can
suggest a hidden player; it cannot reveal staff the server never exposes to the client.
The original Anubis Staff List module was not ported. GPL notices and corresponding
source are retained; see THIRD_PARTY.md.

**Base ESP** (Visuals) ports the underground base detector from the user-provided
Krypton Avengers source (`dev.dexter.kryptionians.modules.BaseESP`). It groups
storage and nearby built blocks into base shells, matches the included signature
catalog, and draws configurable green outlines and translucent faces through
terrain. Optional chat/sound alerts and **Chunk Mark** (Pillar or Slab) are included.
**Fast** scanning is the default, with **Balanced**, **Eco**, and **Custom** presets.
New chunks and tracked block changes take priority over periodic rescans. Custom
controls include chunks per tick, refresh interval, snapshot budget, and scan radius;
the worker queue stays bounded to twelve jobs. Snapshots changed during a scan are
discarded and refreshed. A movable **Detector HUD** lists nearby bases with distance,
coordinates, storage count, and scan status; use **Place detector HUD** to drag/resize
it. Choose **Both**, **Outline**, or **Filled**, and adjust outline width. A spatial
alert cooldown reduces repeat notifications when a base is rediscovered.
Only loaded chunks between Y −64 and −1 are scanned. Workers analyze copied chunk
sections; old results are discarded when disabling or changing worlds.

**Pet** (Visuals) adds a cosmetic companion: Wolf, Cat, Fox, Bunny, Bee, Allay,
Parrot, Axolotl, Slime, Turtle, Panda, or Pig. Choose **Follow**, **Sidekick**, or
**Orbit**, adjust size, distance, speed, and hover height, and enable baby models,
nicknames, or a colored wolf/cat collar. Ground pets follow your recent trail;
flying pets float with a gentle bob. Animation and fullbright are optional.
The companion appears in first and third person, catches up after teleports, and
has a **Recall Pet** button. It is visible only in your client and cannot be
attacked, collide with players, or interact with the server.

| Category | Modules |
| --- | --- |
| Movement | Auto Walk, Free Cam, Freelook |
| Player | Auto Tool, Auto Trident, Fast XP, Smart Eat, Spawner Protect, existing Fast Place |
| Visuals | Bloom, Color Correct, Custom Crosshair, Custom FOV, Fake XP, Hats, Key Zoom, Keystrokes, Motion Blur, Region Map, Spotify HUD, Spin Bot, Swing Speed, existing Stretch Res |
| Misc | Chat Macros, Key Sounds, existing Screen Hider |

The Nathan modules use Maro's settings, keybinds, friend list, and saved configs. Right-click a module to edit its settings. Chat Macros includes a manager and editor under its Actions section. Strings and item lists have an Edit button; lists accept JSON arrays or semicolon-separated entries.

Spotify HUD supports the existing desktop/browser media bridge on Windows, album covers, playback controls, draggable positioning, seeking, and animated audio bars. Press **F9** while enabled to interact. Audio-reactive bars use Windows system output; they do not record a microphone. Live media/audio integration needs Windows; other systems keep the HUD's preview/fallback state. Spotify control integration is retained from Nathan, with Maro's renderer underneath.

Region Map retains Nathan's updated region divisions and sharp labels. Keystrokes retains its Poppins labels. Their initial positions leave room for each other and Spotify, and can be changed in settings.

Swing Speed accepts **-10 to 10**: negative values slow the arm animation, **-10** makes it take 3.5 times as long, **0 or 1** is normal, and **2 to 10** retains the original faster speeds. The selected hand setting still applies.

Key Sounds includes **14 built-in sound packs**: Creamy, Thock, Soft, Clicky, Creamy Deep, Creamy Light, Silky, Milky, Marshmallow, Velvet, Poppy, Bubble, Marble, and Rain. Each has four key variations, a spacebar, and a mouse click. Changing Sound Pack stops the old sounds, cancels earlier previews, and plays a short preview of the new choice when the menu is open. Turn off Preview on Change for silent selection. Rebuild the bundled samples with `java tools/KeySoundGen.java`.

The optional EG Oreo pack must be imported from Mechvibes or placed under `.minecraft/maro/nameeprotect/keysounds/eg-oreo/`. Spawner Protect can use Baritone when installed separately; without it, automatic pathfinding is unavailable.

## Features

**Custom Crosshair** (Visuals) includes **24 vector presets**: Dot, Square Dot, Plus,
Cross, Cross + Dot, T Cross, X, X + Dot, Circle, Circle + Dot, Ring Cross, Double Ring,
Square, Square + Dot, Diamond, Diamond + Dot, Chevron, Double Chevron, Triangle,
Brackets, Corner Brackets, Star, Reticle, and Four Dots. Open **Preset Gallery** to
see and click the shapes; it scrolls on smaller screens and supports arrow keys.
Customize size, gap, thickness, dot size, an optional center dot, color/opacity,
and outline color/width. Optional Target Highlight changes color on living targets.
Shapes use Maro's anti-aliased vector renderer. Vanilla attack indicators, F1 hiding,
first-person visibility, spectator rules, and the debug crosshair still work.

Crosshairs use the exact framebuffer center, including odd window sizes and GUI scales.
For your own artwork, click **Import PNG**: browse for a file, paste its path, or drag it
into the import window. Import switches **Source** to **PNG** and saves a copy at
`.minecraft/maro/crosshairs/custom.png` for later launches. Customize **PNG Size**,
**PNG Opacity**, **Trim Padding** (centers visible artwork), **Smooth PNG**, and optional
**Tint PNG**. Aspect ratio and original colors are preserved. PNGs may be up to
1024 × 1024 and 8 MB; empty or unreadable images keep the working image. If no image
is available, the built-in preset remains visible.

**Auto Tool** (Player) selects the best tool in your hotbar when you mine a block.
It prefers tools that can harvest the block, then compares mining speed including
Efficiency enchantments. Switching happens before the first mining action and is
sent to the server immediately. **Switch Back** restores your previous slot when you
stop mining or disable the module; a manual slot change is preserved. **Protect Tools**
skips tools with one durability point remaining. Both options are on by default.
It leaves item use and creative mode alone and does not move items out of your inventory.

**Auto Trident** (Player) repeatedly charges and releases a trident while you hold
right-click (or your bound Use key). **Speed** ranges from **1–10**, defaulting to **10**:
10 releases after the normal minimum of 10 charge ticks (0.5 seconds at 20 TPS),
and 1 holds for 28 ticks (1.4 seconds). The next charge starts on the next client tick.
Works in either hand, pauses in menus, and leaves other active item uses alone.
Riptide still needs water or rain; thrown tridents must be returned or replaced before
the next throw in survival. Uses normal item interactions and release actions.

**GUI**
- Floating top-bar navigation (category tabs with a sliding pill, icon buttons for general pages) over a black, top-lit window
- Smooth, frame-rate independent animations everywhere: menu open/close, page transitions, staggered card entrances, the sliding tab indicator, toggles, sliders, hovers and accent colour fades
- Anti-aliased vector renderer (rounded rects, outlines, gradients, soft shadows/glow, arcs, lines). Pure geometry, no textures or shaders, and it stays crisp at every GUI scale
- Vector icon set (combat, movement, player, visuals, misc, settings, configs, theme, socials, search...)
- Bundled **Inter** font (SIL OFL), with a toggle back to the vanilla font
- Draggable window (drag the header)
- Search across all modules (`Ctrl+K` / `Ctrl+F`, or just start typing)
- `Tab` / `Shift+Tab` to cycle pages, `Esc` to back out or close
- Tooltips on the info icons, toast notifications with progress bars, optional UI click sounds

**Module cards**
- Left-click toggles, right-click (or the `⋯` button) opens settings, middle-click starts rebinding
- Keybind chip: click to rebind (keyboard or mouse buttons 3+), `Del`/`Backspace` unbinds, right-click resets
- Red marker for experimental modules, green dot on a category tab when something in it is enabled

**Settings widgets**
- Boolean (switch), Number (animated slider; scroll on the value, right-click resets), Mode (segmented control or cycling pill), Color (HSV picker with hue/alpha bars and presets), Keybind
- Conditional visibility with an animated collapse: `.visible(() -> mode.is("Smooth"))`

**Pages**
- **Settings**: menu key, font, animation speed, dim, tooltips, sounds, notifications, pause, auto-save
- **Configs**: create / load / save / delete (with confirmation), open folder, active-config badge
- **Theme**: 10 accent presets, custom colour, gradient accents, rainbow mode, background colour + opacity, corner radius, glow, shadow
- **Socials**: friend list with quick-add from the server player list, plus player heads. Use `FriendManager.isFriend(name)` in your modules

Everything is saved to `.minecraft/maro/`: `client.json` holds GUI settings and friends, and `configs/*.json` holds modules.

## Adding a module

```java
public class Sprint extends Module {
    private final BooleanSetting omni = add(new BooleanSetting("Omni", "Sprint in every direction", false));
    private final NumberSetting delay = add(new NumberSetting("Delay", "Ticks between checks", 2, 0, 20, 1));
    private final ModeSetting mode = add(new ModeSetting("Mode", "How to sprint", "Legit", "Legit", "Rage"));

    public Sprint() {
        super("Sprint", "Automatically sprints for you", Category.MOVEMENT);
        // setExperimental(true); // shows the red marker
    }

    @Override
    public void onTick() {
        if (!inGame()) return;
        mc.player.setSprinting(true);
    }
}
```

Then register it in `ModuleManager#init()`:

```java
register(new Sprint());
```

Hooks available on `Module`: `onEnable`, `onDisable`, `onTick`, `onRender2D(DrawContext, float)`.
To preview every setting type, uncomment the `ExampleModule` line in `ModuleManager#init()`.

Categories live in `module/Category.java`. Add or rename entries there and the sidebar updates.

## Project layout

```
dev.maro
├── Maro.java                 entrypoint, keybind dispatch, HUD/tick hooks
├── mixin/                    Keyboard + Mouse hooks for binds
├── module/                   Module, Category, ModuleManager (+ impl/ExampleModule template)
├── setting/                  Boolean / Number / Mode / Color / Keybind settings
├── config/                   ConfigManager (JSON), ClientSettings, FriendManager
├── util/                     Animation, Easing, ColorUtil, KeyUtil, Sounds
└── gui/
    ├── ClickGuiScreen.java   window, sidebar, header, input routing (immediate mode)
    ├── page/                 Modules (grid + settings view), Settings, Configs, Theme, Socials
    ├── widget/               TextField, SettingsList, Widgets (toggle/button/chip), Scroll, Anims
    ├── render/               Render2D (AA shapes), Fonts, Icons
    ├── theme/                Theme palette + animated accent
    └── notification/         toasts
```

## Spotify lyrics

Spotify Hud can show the current lyric and next line below the player. It looks up song title,
artist, album and duration through [LRCLIB](https://lrclib.net/docs) in a background worker.
Timed lyrics follow pause, playback and timeline seeking; availability depends on the song.
Untimed lyrics are marked **UNSYNCED**: open F9 controls and scroll over the lyrics panel to read them.
The **Lyrics** settings let you hide the panel, adjust text size, or change timing with
**lyrics offset ms** (positive values advance the lyrics). Instrumental tracks and missing
lyrics show a status instead. No Spotify account connection or token is needed.

**Word display** offers **Lines**, **WordHighlight** (the active word turns white), and
**SingleWord** (one sung word centred in a fixed position). Real word timing comes from LRCLIB's
[Lyricsfile](https://github.com/tranxuanthang/lyricsfile) or enhanced LRC word timestamps when available.
SingleWord always requires individual word timestamps; it never guesses a singer's delivery
from the length of a line. If those timestamps are missing, the current line is displayed with
**LINE SYNC / NO WORD TIMING** in the small header, with the next lyric below the current line.
The preview row contains lyrics only. Multiword segments cannot supply individual word timing either.
WordHighlight switches the whole word and its underline at the supplied timestamps.
**Approximate word preview** is an optional WordHighlight-only estimate, disabled by default
and labelled **ESTIMATED WORDS**. Existing Auto word follow settings do not enable it.
Word display defaults to WordHighlight. Words follow the same
playback/seek clock as the timeline. Timing accuracy also depends on the supplied lyrics
and the media player's position reports.
The Windows bridge stays open and samples every 250 ms, preserving the sample timestamp
so process delivery time does not push the lyric clock behind playback. If the stream stops,
the HUD falls back to regular polling. Turning the module off closes the bridge.

The wider player and lyrics share one softly shaded card. Long lyric lines wrap; word mode
gently scrolls to keep the current word visible. Missing, loading and instrumental lyrics
use an animated listening view. Artwork surfaces are prepared on the media worker, so
the HUD does not rebuild its gradient every frame.
The lyric preview reserves bottom padding for descenders at every text size and player scale.
When the upcoming line repeats the current one, its duplicate preview is hidden. Repeated
lines still restart the word cursor at their own timestamp.

## Skin Accessories

**Visuals → Skin Accessories** adds client-side 3D accessories to your player skin.
Mix eight head styles, five wing styles, five tails, four halos, three shoulder styles,
and three back accessories. Each slot also has a None option. Twelve themed presets
include Dragon, Angel, Demon, Fox, Cat, Bunny, Cyber, Royal, Forest, Butterfly, Astral,
and Adventurer. **3D Preview** shows the actual rendered accessories, supports rotation,
and lets you try presets or mix individual parts even before enabling the module.

Adjust head size, wing size/spread, tail length, halo height, primary/accent/halo colors,
rainbow accents, glow, animation speed/strength, and movement response.
Wing Spread increases from 0° (folded behind the back) to 90° (fully open outward).
Flapping stays behind the shoulders, including at the slider endpoints.
Head accessories hide under helmets and wings hide with elytra by default; both options can be disabled.
Accessories follow the player's head, torso, and arms, including crouching and swimming.
The Players setting selects Self or Everyone on your screen. Other clients do not receive
these cosmetic models. Meshes are cached and animation uses render matrices, with no
inventory or server packet changes.

## Auto Mine timing

**Player → Auto Mine → Humanizing** adds tick-based random delays between new digs and
placements, a delay after changing tools, and a turn speed sampled once per target.
Smooth Look slows as it reaches the target. An active block keeps one continuous break;
timing variation does not restart it on every tick or change vanilla mining speed.

**Short Breaks** pauses between completed blocks, using configurable block-count and
duration ranges. Min/max ranges are automatically ordered. **Stop On Manual Input**
hands control back when you move the view or press attack, use, back, jump, or sneak.
Optional **Pause Near Players** waits while another player is within the chosen radius.
Existing lava, health, inventory, tool durability, and distance stops still run during waits.
The status bar reports aiming, action delays, tool waits, breaks, and nearby-player pauses.
Turning Humanize off clears timing waits immediately. These variations cannot guarantee
that a server will allow automation or that it will avoid detection or bans.

**Obstacle Routing → Reroute Obstacles** checks both side tunnels when liquids, dangerous
blocks, unbreakable blocks, unsafe floor gaps, or a stuck path interrupt mining. It tries
the selected **Turn Preference** first, checks the full tunnel width and height for the
configured **Route Lookahead**, and turns smoothly before walking. Water and lava are
both avoided while routing is enabled. If neither side is safe or loaded, it stops.
Turning routing off restores the Stop At Lava / Stop At Water controls. Max Distance
counts progress across all tunnel segments; inventory, health and tool problems still stop.

## Stretch Res compatibility

Stretch Res changes the shared perspective matrix before the world, culling, ESP screen
projection and Motion Blur use it. The GPU receives the same matrix once, keeping ESP
aligned when the camera turns. HUD and menu orthographic projections stay unchanged;
the hand retains its separate stretched perspective.

The production game test compares the uploaded and world matrices across every aspect
ratio. To also check Meteor Shader, Box and 2D ESP against rendered entity positions, run
`./gradlew runProductionClientGameTest -PmeteorTestJar=/path/to/meteor-client.jar`.
Add `-PprojectionTestOnly=true` for just the projection and ESP checks. Meteor is test-only
and is not bundled into Maro.

## Building

Requires Java 21.

```
./gradlew build
```

The mod jar is written to `build/libs/`. Run the dev client with `./gradlew runClient`.

Run `./gradlew runProductionClientGameTest` to launch a real client against the production jar. It checks the module registry, settings/macros/config round-trips, HUD rendering, camera modules, shader effects, menu pages, Stretch Res, and Fast Place. Screenshots are saved under `run/screenshots/`. External Spotify sessions, sound devices, and optional Baritone are not controlled by this test.

## Tweaks

- **Font:** Inter SemiBold / ExtraBold. Each weight has one definition per GUI scale (`assets/maro/font/inter_*_x1..x6.json`) so text is rasterised pixel-perfect; change `"shift"` / `"size"` there if text sits slightly high or low.
- **Colours:** see `gui/theme/Theme.java`.
- **Window size:** see the layout block at the top of `ClickGuiScreen#render`.

Inter font © The Inter Project Authors, SIL Open Font License 1.1 (`INTER_LICENSE.txt` in the jar).

See [THIRD_PARTY.md](THIRD_PARTY.md) for bundled Nathan source, fonts, and retained notices.
