# maro.gg

A **Fabric 1.21.11 / Java 21** client with a smooth, modern ClickGUI and the 20 modules from Nathan's current 1.21.11 source. It runs directly on Fabric; Meteor Client is not required.

Open the menu in-game with **Right Shift** (you can change this in *Settings*).

The **1.0.6** update adds a connected left category sidebar, centralized Barlow
Condensed typography with bold uppercase labels, and one cohesive wordmark whose
custom M forms the first letter of Maro. Module font preferences remain saved
behind the global font override. See [branding, font and sidebar](docs/branding-font-sidebar.md).

The local **1.0.2** update makes Free Cam faster: 96 blocks per second by default,
with an effective 0.5–4096 speed range, normalized diagonal movement and clean camera
restoration. **No Fall is removed**. **Coords Fly** adds labeled Position and Nether
rows: Overworld X/Z divide by eight, Nether coordinates use the actual position,
and unsupported dimensions show no conversion. See [movement and coordinates](docs/movement-coordinate-update.md).

**Settings → Background Studio** adds default, solid, gradient, transparent and PNG
backdrops with live previews, persistent image imports and fit/fill/stretch/tile controls.
**Interface → Frosted Glass** has a native blur strength of 1–10 without changing
Minecraft's saved blur preference. Image, resource-pack and phone-video imports use
an in-game file picker, keeping folder navigation inside Maro's private GUI.
See [GUI backgrounds](docs/gui-backgrounds.md).

The local 1.0.1 update improves icon/text smoothing and centering, restores the
custom models' original texture quality, and removes Trident Boost and Air Stuck.
See [rendering and model appearance](docs/rendering-quality.md).

The menu is one window. Its connected left sidebar contains the categories and
the Settings, Configs, Theme and Socials pages. Search sits above the module browser,
with quiet module/enabled counts below it. Each category shows its modules as cards
with an icon, a star and a switch; **All**, **Enabled** and **Starred** filter them.
**Left-click** a card to open its settings in the independent panel on the right, and
**right-click** it (or click its switch) to turn it on or off. Settings are grouped into
cards. An explicit capability list gives **Custom Model, Cosmetics, Skin Changer,
Emotes and Player ESP** a live player preview on the left of their settings, using
the current skin and equipment where applicable. World, sky, GUI, coordinate and
Free Cam settings do not receive an unrelated player portrait. **Esc** closes the
panel, then the menu.

See [unified module previews](docs/unified-previews.md) for the common Preview tab,
supported subjects, controls and rendering limits.


Maro opens with a black/cobalt loading screen and matching main menu, a sharp
split-M logo and real loading progress. Menus, editors, HUDs, phone and music text
use smooth bold uppercase lettering. Existing native controls and server-entry
fades are preserved. See [Startup](docs/startup.md) and [Typography](docs/typography.md).

**Settings → Floating Window** floats the menu on a glass pane. Aim at a control and click; hold the bar below the pane
to move it, scroll while holding to change its distance, and click the red dot
to close. **Walk With Menu Open** enables movement while you configure the menu;
typing into a field pauses movement. **Remember Spot** reopens the pane where
you left it when that position is nearby and in front of you. Existing Spatial
preferences migrate to Floating Window.
**Custom Sky → Caustics** keeps build 690's animated light pattern.
See [Build 691 merge](docs/build-691-merge.md) for merge details and validation.

Enable **Screen GUI** at the bottom of the sidebar to place the menu
in front of you in the world. Walk and look around normally, aim your crosshair at a control,
and click or scroll to configure it. **Screen Distance** sets its opening distance. Click
a text field (or press **Ctrl+F**) to type; movement pauses while a field is focused.
Press **Enter** to finish typing and **Esc** to go back or close the menu.
Turn Floating Window off to use Screen GUI's placement and distance controls.

The local **1.0.4** Screen GUI change keeps the panel's opening position, facing
and physical size fixed, with attached settings and previews and local model/slider
drag input. The current design and flat GUI are preserved. Native behavior,
rendering-mod compatibility and regular GUI regression checks passed; see
[stationary Screen GUI](docs/stationary-screen-gui.md).

When the optional Zen companion is installed, Maro reserves the missing native vertex/index
capacity for its Block Outlines star before it draws. This prevents the address-zero crash
when first aiming at a block, and protects later draws as the mesh fills. The integration
does not require or bundle the companion. HUD meshes reuse CPU geometry and reserve shape
arrays to the needed size; queued GUI batches keep independent snapshots.
Rounded shapes reuse precomputed corner normals, module lookups use a class index and
shared read-only registry views, and unchanged keybinds are retained between ticks.
The phone reuses its immutable album-art geometry across frames.

## Included modules

**Name Protect** (Misc) changes your local display name and other players' aliases immediately
while typing. It includes a searchable editor, anonymous player numbers and friend exclusions.
Original account identities, commands and messages stay intact. See [Name Protect](docs/name-protect.md).

**Streamproof** (Misc, Windows) uses one world render in the same Minecraft instance:
the normal window presents ordinary gameplay, while an attached capture-excluded
layer presents local GUI, HUD and annotations on the same monitor. Shared-scene
effects, including Free Cam, custom player/item models and sky/lighting changes,
pause locally as well as in the capture; their requested states restore when
Streamproof ends. Gameplay actions remain visible. Operating-system capture,
OBS and Discord protection are not verified for this update; check the selected
application's actual preview. External applications opened through links are outside
this presentation's scope. See [Streamproof and capture limits](docs/streamproof.md).

Click a module for its usual settings. The folder icon in its settings header opens that module's
public configs, including Top liked, preview, apply and undo. See [public configs](docs/public-configs.md).

**Gameplay Textures** (Visuals) combines Better Looks, Better Textures and Texture
Packs. Browse PacksMC's first 50 Trending packs with previews, download Modrinth
packs, manage your installed loadout, and adjust appearance from one module.
The charcoal workspace has a compact sidebar, live collection previews and a
quiet master switch. **Download pack** opens the selected pack's original page in
your default external web browser. **Browse website** opens PacksMC's listing.
Import the downloaded ZIP through the menu or drag and drop it; saved packs have
a one-click **Apply** button and work offline. The standard client remains small,
with no embedded pack collection. Existing profiles migrate. Preview images have
antialiased rounded corners.
See [Gameplay Textures](docs/gameplay-textures.md) and [PacksMC](docs/packsmc.md).

**Cosmetics** now includes 66 swords and five 3D pickaxes, with detailed textures and new
split blades, gear teeth, crystal forms, drills, claws and asymmetric mining heads.
Existing skin selections retain their names. Shovels and tridents retain their skins.

**Keyboard Strokes → Arrange Keys** lets you drag each selected key into your own
HUD layout. Snap, fine arrow-key nudges, profile persistence and physical-layout
reset are included. **Place HUD** still moves/resizes the entire overlay.

**Fake Pay** (Misc) intercepts `/pay <player> <amount>` while enabled and shows
Glazed-style local chat feedback, with optional action bar and hit-marker sound.
Compact/full amounts, exact decimal parsing, a preview button and optional local
sidebar deductions are included. Invalid `/pay` input is consumed locally too.
See [Fake Pay](docs/fake-pay.md) for controls and native packet checks.

**Skin Changer** (Misc) adapts Anubis's skin changer. Set **Self Player** to the Minecraft
username whose skin you want, and optionally **Others Player** to give other players a
separate skin. Leave either field empty to keep the original skins. Classic and slim
models work in first and third person, while capes and elytra remain intact. Skins load
asynchronously and are cached; use **Reload Skins** to retry or fetch an updated skin.
Changes are visible locally, and disabling the module restores the original skins.

**Keyboard Strokes** (Visuals) shows the keys you press on screen, lit while held. Click it (or
**Choose Keys**) to open a full 104-key keyboard with function keys, navigation, arrows,
numpad and three mouse buttons. Click keycaps to select them; filled caps are shown on the HUD.
Unused physical keys work too. **Game actions** selects individual bindings, including mod controls.
A preview updates as you click. Selected game actions follow anything you rebind.
Selected keys keep their positions from the keyboard chooser: F is beside D and R stays on the
row above. Wide keys and tall numpad keys keep their shape; mouse buttons are grouped below.
The preview and placement bounds follow the selection, and large layouts fit the viewport.
Drag it anywhere, resize it, and set the key opacity.

**Acrobatics** (Visuals) flips and spins on jumps or the **Trick Key** (G by default).
**Trick View → Auto Third Person** makes a first-person player's trick visible, then restores
their camera; manual F5 changes take priority. **Follow Game** keeps the current perspective.
**First Person Flip** turns the view instead, including Side Flip's roll. Short key taps are
queued reliably. See [keyboard and Acrobatics update](docs/keyboard-acrobatics.md).

**Spotify Phone** (Misc) opens a handheld music player with **F10**. Click previous,
play/pause or next, or use **Left / P / Right** (**Ctrl+Space** also plays/pauses).
**Page Up / Page Down** adjusts output volume and **M** mutes. These shortcuts
are configurable in the module and ignore typing and held-key repeats. Drag the progress bar to seek;
**F10**, **Esc**, the close button or the bottom home bar puts the phone away.
The phone shows live Spotify metadata and album artwork, pulls out of your pocket,
and slips back when closed. Its hand uses your skin. Choose **Hand → Left/Right**, adjust **Size**, or hide
the hand. A song change brings the opposite hand across the screen to swipe its
artwork. Artwork is cropped without stretching, masked and decoded off-thread.
Change its keybind in the module settings. It shares Spotify HUD's Windows
media connection and works with desktop Spotify or the browser media session, without
an API login. Start a song in Spotify first; an idle phone offers **Open Spotify**.
Closing the phone keeps your music playing. It does not pause the world. **Move While Open**
keeps your configured movement, jump, sneak and sprint keys active. Mouse Look captures the
cursor for normal camera movement; hold Alt or press I to use phone controls. Space jumps; Ctrl+Space plays/pauses. Disable movement for
stationary controls with Space to play/pause. The phone's **− / +** buttons resize it from
**55% to 145%**. Drag or scroll **Output volume**, or click its speaker to mute. This controls
Windows output volume for all apps, including both desktop and browser Spotify.
**Game Actions** is on by default: left mouse mines, including successive blocks directly
below you; right mouse uses held items, launches fireworks and boosts Elytra with main-hand
or offhand rockets. Opening the phone preserves held mining/use input. Alt, app controls,
focus loss and closing release those actions. Turn it off in the module or phone Settings
to keep gameplay clicks disabled. The old opt-in setting is replaced by this default-on option.

The phone and iPad also include **Messages**, **Playlists**, **Videos**, **Settings** and **Connection** apps.
Press **I** (configurable **Interact Key**) or click **Interact** to open the app grid;
press I again to return to mouse look. Connect both testers to the same test relay to
get separate, persistent 10-digit phone numbers. Text by number, receive unread badges
and HUD notifications, and block unwanted numbers. Long messages open in a scrollable
reader. New senders go into **Requests**; accept them into **Inbox** or delete/block them.
Settings control notifications, request alerts, sound and message previews. Saved
Spotify links and playlists imported with **From Spotify** can be shared as cards the
recipient opens or saves. **Videos** opens the real TikTok **For You**, **Entertainment** (`#funny`)
and **Gaming** (`#gaming`) pages inside the phone using an installed Edge or Chrome
browser with a separate phone profile. Sign in on TikTok's own page for
personal recommendations. **Feed** selects a topic, with explanatory labels. The
focused video view uses more of the phone and preserves aspect ratio; **··· → Show
full website** restores page navigation and sign-in. That menu also offers retry,
an external browser and **Local video files**, with scroll/arrow navigation, pause,
sound and saved direct links. No demo videos are inserted. Typing pauses movement; music controls and pocket animation
remain available. The relay runs locally or on a LAN for now; see
[phone relay setup](tools/phone-relay/README.md) for startup and testing instructions.

**Jump Circle** now uses Anubis's fading, expanding ring effect with the Maro logo.
The old Neon/Orbit/Ripple renderer is replaced. **Logo**, **Logo Glow**, radius,
lifetime and accent/custom color are configurable; jump/landing triggers and
optional shared **Spotify Sync** remain available. Ring storage is
bounded to 24, uses cached 64-segment geometry and respects world depth.
**Hand Shader** and its render hooks have been removed.
See [build 693 phone and rendering update](docs/build-693-phone-jump.md).
See [phone apps](docs/phone-apps.md) for Spotify sign-in, video formats and validation.

**Spotify Playlists** (Misc) keeps four playlists one click away: **Larp Aux**, **Phonk**,
**House Music** and **Summer Music**, all with links built in. Click **Open**, or give one a
key and turn the module on to open it in-game. Paste a
Spotify link (Share → Copy link) into a slot to change it, or leave it empty for the built-in
one. Playlist, album and song links all work, with or without `?si=` or a language prefix,
as do `spotify:` URIs. **Open In** chooses the Spotify app or the web player. No Spotify
login is needed.

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

Staff names and details now use larger, brighter text in the same panel dimensions.
**HUD → Text size** adjusts text independently of **Staff list size**. **Smooth text**
is enabled by default: cached lettering follows the selected client font (Sora by default), uses antialiasing and a 2× edge smoothing
pass, then draws at one texture pixel per screen pixel through the normal GUI layers.
Resizing creates the correct new text size rather than stretching the old lettering.

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

**Player ESP** (Visuals) shows players through walls as silhouettes of their actual model
(armor, held items and cape included) instead of boxes. **Fill Style** offers **Solid**,
**Player** (the per-player color), **Gradient**, **Rainbow**, **Galaxy** (drifting nebula with
twinkling stars), **Aurora**, **Plasma**, **Lava** and **Hologram**, with **Color**/**Second
Color**, **Fill Opacity**, **Edge Fade**, **Stars**, **Pattern Scale** and **Animation Speed**.
The **Outline** is measured from the silhouette's edge, so it stays smooth and constant-width
rather than tracing pixel steps; set its width, opacity and color (**Custom**, **Player**,
**Rainbow** or **Fill**). **Glow** adds a soft falloff in the outline color. Targets: yourself in
third person, friends (optionally in their own **Friend Tint**), spectators, a range limit, and
**Health Colors** (green to red) for the player color. Width and glow are given at 1080p and scale
with resolution. The effect is drawn before Bloom and Color Correct, so it blooms with the world.

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
| Player | Auto Tool, Fast XP, Smart Eat, Spawner Protect, existing Fast Place |
| Visuals | Bloom, Color Correct, Custom Crosshair, Custom FOV, Fake XP, Hats, Key Zoom, Motion Blur, Region Map, Spotify HUD, Spin Bot, Swing Speed, existing Stretch Res |
| Misc | Chat Macros, Key Sounds, existing Screen Hider |

The Nathan modules use Maro's settings, keybinds, friend list, and saved configs. Left-click a module to edit its settings. Chat Macros includes a manager and editor under its Actions section. Strings and item lists have an Edit button; lists accept JSON arrays or semicolon-separated entries.

Spotify HUD supports the existing desktop/browser media bridge on Windows, album covers, playback controls, draggable positioning, seeking, and animated audio bars. Press **F9** while enabled to interact. Audio-reactive bars use Windows system output; they do not record a microphone. Live media/audio integration needs Windows; other systems keep the HUD's preview/fallback state. Spotify control integration is retained from Nathan, with Maro's renderer underneath.

Enable **Controls → Volume control** to show the **SYSTEM** slider in F9 controls.
Drag it from 0–100%, scroll over it in 2% steps, or click the speaker to mute/unmute.
This changes Windows output volume for all apps, covering both desktop and browser playback.
The percentage follows keyboard/Windows changes; moving the slider unmutes, and the speaker
restores a remembered audible level after setting it to zero. The row replaces the playback
status while interacting, so the normal HUD stays the same size. Mini mode expands in F9 to
reveal the controls. Unsupported/disconnected audio shows an unavailable state.
The existing persistent WASAPI helper handles reads and coalesced writes away from the render
thread using [Windows endpoint volume controls](https://learn.microsoft.com/en-us/windows/win32/api/endpointvolume/nn-endpointvolume-iaudioendpointvolume).

Region Map retains Nathan's updated region divisions and sharp labels. Its initial position leaves room for Spotify and can be changed in settings.

Region Map defaults to **0.8×**. **Auto fit screen** limits its height to **42%** of
the screen and width to 25%, including with a large Minecraft GUI scale or an old
saved size. Change **Max screen height**, or disable Auto fit screen for unrestricted
manual sizing. The old untouched 1× default migrates once; custom scales are preserved.
**Compact number fit** uses narrower digits only when this keeps a cramped label taller.
Numbers have a subtle dark outline for contrast; the raster is still cached at its actual
display resolution. Number Font, Number Size, and Fit Numbers remain available.

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

Auto Trident, Trident Boost and Air Stuck have been removed. Tridents use native Riptide behavior.

**GUI**
- Floating top-bar navigation (category tabs with a sliding pill, icon buttons for general pages) over a black, top-lit window
- Smooth, frame-rate independent animations everywhere: menu open/close, page transitions, staggered card entrances, the sliding tab indicator, toggles, sliders, hovers and accent colour fades
- Anti-aliased vector renderer (rounded rects, outlines, gradients, soft shadows/glow, arcs, lines). Pure geometry, no textures or shaders, and it stays crisp at every GUI scale
- Lucide line icons for every module, category and page
- Bundled **Sora Bold / SemiBold** font (SIL OFL), all caps by default, with Rubik, Inter and vanilla alternatives
- Draggable window (drag the logo)
- Search across all modules (`Ctrl+K` / `Ctrl+F`, or just start typing)
- `Tab` / `Shift+Tab` to cycle pages, `Esc` to back out or close
- Tooltips on the info icons, toast notifications with progress bars, optional UI click sounds

**Module cards**
- Left-click opens settings, right-click (or the switch) toggles, middle-click starts rebinding
- The star keeps a module under the **Starred** filter; stars are saved with the client settings
- Keybind chip in the settings' **Main** card: click to rebind (keyboard or mouse buttons 3+), `Del`/`Backspace` unbinds, right-click resets
- Red marker for experimental modules

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

### Schematic Auto Builder

Click **Player → Auto Builder** for a compact panel with schematic selection, origin/nudges, auction budget and **Start Build**. **Automatic** builds without holding right mouse. Imports `.schem`, `.schematic`, `.litematic` and vanilla `.nbt`, previews textured ghosts, prepares the whole material list in your selected double chests (press R on each to add, Shift + R to remove), restocks each layer, buys missing materials and cleans its temporary dirt. **Saved Builds** keeps named placements and restores progress after reconnecting; resume rescans matching world blocks. Optional head smoothing and timing variation are in **Options → Build**. See [Auto Builder setup and limits](docs/AUTO_BUILDER.md).

Run its focused in-game checks with `./gradlew runProductionClientGameTest -PbuilderTestOnly=true`.

Requires Java 21.

```
./gradlew build
```

The mod jar is written to `build/libs/`. Run the dev client with `./gradlew runClient`.

Run `./gradlew runProductionClientGameTest` to launch a real client against the production jar. It checks the module registry, settings/macros/config round-trips, HUD rendering, camera modules, shader effects, menu pages, Stretch Res, and Fast Place. Screenshots are saved under `run/screenshots/`. External Spotify sessions, sound devices, and optional Baritone are not controlled by this test.

## Tweaks

- **Font:** Sora Bold / SemiBold by default. Each weight has one definition per GUI scale (`assets/maro/font/sora_*_x1..x6.json`) so text is rasterised at the matching scale. Settings offer Rubik and Inter alternatives and a separate Menu Text Size control.
- **Colours:** see `gui/theme/Theme.java`.
- **Window size:** see the layout block at the top of `ClickGuiScreen#render`.

Sora font © The Sora Project Authors, SIL Open Font License 1.1 (`LICENSES/Sora-OFL.txt`). Inter and Rubik licenses are retained with their bundled alternatives.

See [THIRD_PARTY.md](THIRD_PARTY.md) for bundled Nathan source, fonts, and retained notices.

**Cosmetics → Pickaxe** contains only Driver, Minty, Leviathan Axe, Cold Snap and Star Wand,
with rebuilt 3D details, smooth swing previews and fifteen tool sounds edited from the supplied
recordings. Other pickaxe choices were removed. See [the pickaxe guide](docs/custom-pickaxes.md).

The module grid and the settings panel have a clickable, draggable scrollbar. See
[the startup menu](docs/startup.md).
