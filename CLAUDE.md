# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

maro.gg is a Fabric client mod for **Minecraft 1.21.11** that uses Java 21. It is built against Yarn `1.21.11+build.3`, Fabric API `0.141.6+1.21.11` and Loom `1.14-SNAPSHOT`; the versions are in `gradle.properties`. `fabric.mod.json` pins the exact Minecraft version, so a jar only loads on 1.21.11. Meteor Client is not a dependency.

## Commands

```
./gradlew build                              # mod jar -> build/libs/maro-mc1.21.11-<version>.jar
./gradlew runClient                          # dev client
./gradlew runProductionClientGameTest        # real client + production jar + src/gametest mod
java tools/KeySoundGen.java                  # rebuild the bundled Key Sounds samples
```

There are no unit tests. Testing runs through a Fabric client gametest mod in `src/gametest`, which launches the real game against the production jar.
- `MaroClientGameTest` drives the menu, modules and settings through real input and saves screenshots to `run/screenshots/`.
- It calls `SpotifyLyricsChecks` (lyrics parsing/timing) and `AutoTotemChecks` (inventory swaps in a singleplayer world).
- To exercise a change, add a step or a `*Checks` class and call `context.takeScreenshot("maro-...")`.
- It runs as a single test, with no per-test filter.

The jar version is `1.0.0+build.<GITHUB_RUN_NUMBER>` on CI and `1.0.0+local` otherwise.

Local Gradle builds fail in sandboxed environments where `maven.fabricmc.net` is unreachable. In that case, push and rely on CI. Pure-Java/AWT code, such as `nathan/regionmap/*`, can still be compiled and checked with plain `javac`.

## CI and releases

`.github/workflows/build.yml` runs on every push and PR:

1. Build.
2. Run `runProductionClientGameTest` under xvfb with `ALSOFT_DRIVERS=null`. Any mixin failure, crash or render exception fails the job.
3. Print every screenshot into the job log as base64 JPEG between `PREVIEW_BEGIN <name>` and `PREVIEW_END`, so screenshots can be inspected from the log.
4. On push, publish a GitHub release `build-N` with the jar.

Only builds that pass the in-game test are released.

## Architecture

There are two layers, which share one module list, one ClickGUI and one config system.

**Maro core (`dev.maro.*`)**
- `Maro`: the client entrypoint. It handles keybind dispatch, HUD/tick hooks and the ScreenEvents hook used by Screen Hider.
- `module/ModuleManager.init()`:
  - registers the native modules (`AutoTotem`, `ScreenHider`, `FastPlace`, `StretchRes` under `module/impl/<category>/`);
  - then calls `dev.maro.nathan.NameeProtectAddon.init()` for the ported modules.
  - Module names must be unique.
- `setting/`: the settings the menu renders, such as `BooleanSetting`, `NumberSetting`, `ModeSetting`, `ButtonSetting`, `RegionsSetting` and `SettingSection`. `gui/widget/SettingsList` renders every setting type.
- `gui/ClickGuiScreen`: an immediate-mode window with top-bar navigation, opened with Right Shift. Its pages live in `gui/page/*`.
- `gui/render/Render2D` and `ShapeRenderState`: draw anti-aliased vector shapes by submitting `SimpleGuiElementRenderState`s through `DrawContextAccessor.maro$getState()`.
  - The 1.21.11 GUI pipeline **culls back faces**, so quads must use vanilla winding. `ShapeRenderState.Builder.quad` normalizes this.
  - Degenerate quads disappear.
- `gui/render/Fonts`: the bundled Inter font, with one definition per GUI scale (`assets/maro/font/inter_*_x1..x6.json`).
- `config/ConfigManager`: stores data under `.minecraft/maro/`.
  - `client.json` holds GUI settings, friends and the active config.
  - `configs/*.json` holds module state, binds and settings.
  - Settings are saved **by name**. To make a changed default reach users who already have a saved config, rename the setting key.

**Nathan port (`dev.maro.runtime.*` + `dev.maro.nathan.*`)**
- `dev.maro.runtime`: a small Meteor-like compatibility runtime, so ported code keeps Meteor-style APIs. It provides:
  - `systems.modules.Module`, which **extends `dev.maro.module.Module`**;
  - `settings.*` builders;
  - `MeteorClient.EVENT_BUS` with `@EventHandler`;
  - `renderer.Renderer2D`/`MeshBuilder`;
  - `RuntimeEvents`, which posts Tick/Render2D/Render3D events from Maro's hooks.
- How ported modules fit into Maro:
  - They implement `onActivate`/`onDeactivate`.
  - Their settings are adapted into Maro `SettingSection`s, and `getWidget(GuiTheme)` buttons become an "Actions" section.
  - They persist through `saveExtra`/`loadExtra` as an NBT tag stored in the config's `extra` field.
- `dev.maro.nathan.modules`: ported modules registered in `NameeProtectAddon.init()`. Registration is explicit: `Aim` and `Highlight` exist but are not registered. The gametest asserts **exactly 20** runtime modules, so update that check when you add or remove one.
- Helpers in `nathan/render`:
  - `RoundedBox` draws into `Renderer2D.COLOR.triangles`, and the caller owns `begin()`/`render()`.
  - `CrispFont` (Poppins) must be wrapped in `CrispFont.begin`/`end`.
  - `PostEffect` handles shader chains.
  - `Spotify*` classes hold the Spotify HUD layout and raster pieces. Lyrics live in `nathan/audio/SpotifyLyrics` (LRCLIB, background worker).
- Nathan assets live under `assets/nameeprotect/`.
- Mixins are split between `maro.mixins.json` (`dev.maro.mixin`) and `nathan.mixins.json` (`dev.maro.nathan.mixin`). Both use `defaultRequire: 1`, so an injection that fails to match crashes the game, and the CI gametest.

**Notable native modules**
- `StretchRes` hooks the world projection in `GameRendererMixin`.
- `FastPlace` sets the item-use cooldown through `MinecraftClientAccessor`.
- `ScreenHider` pairs `gui/hider/RegionEditorScreen` with `HiderRenderer`. Hidden areas are stored as normalized rects in `RegionsSetting`. `HiderRenderer` blurs or pixelates them with a custom `RenderPipeline` and `assets/maro/shaders/core/screen_hider.fsh`.

## Conventions

- Use **1.21.11** Fabric/Yarn names: `Click`/`KeyInput`/`CharInput` input records, `GuiRenderState`, `Matrix3x2fStack` and `HudElementRegistry`. Examples written for older versions usually do not compile.
- The menu's default look is black with an accent colour and small, bold, all-caps type.
- Do not add anti-cheat bypass features. Auto Totem, for example, uses normal inventory swaps.
- When behaviour changes, update the user-facing feature notes in `README.md`.
