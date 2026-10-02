# maro.gg

A clean **Fabric 1.21.11** client base: a smooth, modern ClickGUI and the plumbing behind it.
It ships with **no modules** so you can add your own.

Open the menu in-game with **Right Shift** (you can change this in *Settings*).

## Features

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

## Building

Requires Java 21.

```
./gradlew build
```

The mod jar is written to `build/libs/`. Run the dev client with `./gradlew runClient`.

## Tweaks

- **Font:** Inter SemiBold / ExtraBold. Each weight has one definition per GUI scale (`assets/maro/font/inter_*_x1..x6.json`) so text is rasterised pixel-perfect; change `"shift"` / `"size"` there if text sits slightly high or low.
- **Colours:** see `gui/theme/Theme.java`.
- **Window size:** see the layout block at the top of `ClickGuiScreen#render`.

Inter font © The Inter Project Authors, SIL Open Font License 1.1 (`INTER_LICENSE.txt` in the jar).
