# maro.gg

A **Fabric 1.21.11 / Java 21** client with a smooth, modern ClickGUI and the 20 modules from Nathan's current 1.21.11 source. It runs directly on Fabric; Meteor Client is not required.

Open the menu in-game with **Right Shift** (you can change this in *Settings*).

## Included modules

| Category | Modules |
| --- | --- |
| Movement | Auto Walk, Free Cam, Freelook |
| Player | Auto Tool, Auto Trident, Fast XP, Smart Eat, Spawner Protect, existing Fast Place |
| Visuals | Bloom, Color Correct, Custom FOV, Fake XP, Hats, Key Zoom, Keystrokes, Motion Blur, Region Map, Spotify HUD, Spin Bot, Swing Speed, existing Stretch Res |
| Misc | Chat Macros, Key Sounds, existing Screen Hider |

The Nathan modules use Maro's settings, keybinds, friend list, and saved configs. Right-click a module to edit its settings. Chat Macros includes a manager and editor under its Actions section. Strings and item lists have an Edit button; lists accept JSON arrays or semicolon-separated entries.

Spotify HUD supports the existing desktop/browser media bridge on Windows, album covers, playback controls, draggable positioning, seeking, and animated audio bars. Press **F9** while enabled to interact. Audio-reactive bars use Windows system output; they do not record a microphone. Live media/audio integration needs Windows; other systems keep the HUD's preview/fallback state. Spotify control integration is retained from Nathan, with Maro's renderer underneath.

Region Map retains Nathan's updated region divisions and sharp labels. Keystrokes retains its Poppins labels. Their initial positions leave room for each other and Spotify, and can be changed in settings.

Swing Speed accepts **-10 to 10**: negative values slow the arm animation, **-10** makes it take 3.5 times as long, **0 or 1** is normal, and **2 to 10** retains the original faster speeds. The selected hand setting still applies.

Key Sounds includes **14 built-in sound packs**: Creamy, Thock, Soft, Clicky, Creamy Deep, Creamy Light, Silky, Milky, Marshmallow, Velvet, Poppy, Bubble, Marble, and Rain. Each has four key variations, a spacebar, and a mouse click. Changing Sound Pack stops the old sounds, cancels earlier previews, and plays a short preview of the new choice when the menu is open. Turn off Preview on Change for silent selection. Rebuild the bundled samples with `java tools/KeySoundGen.java`.

The optional EG Oreo pack must be imported from Mechvibes or placed under `.minecraft/maro/nameeprotect/keysounds/eg-oreo/`. Spawner Protect can use Baritone when installed separately; without it, automatic pathfinding is unavailable.

## Features

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
