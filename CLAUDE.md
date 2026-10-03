# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

maro.gg is a Fabric client mod for **Minecraft 1.21.11** (Yarn `1.21.11+build.3`, Fabric API `0.141.6+1.21.11`, Loom `1.14-SNAPSHOT`, Java 21). `fabric.mod.json` pins the exact Minecraft version, so a jar only loads on 1.21.11.

## Commands

```
./gradlew build                              # mod jar -> build/libs/maro-mc1.21.11-<version>.jar
./gradlew runClient                          # dev client
./gradlew runProductionClientGameTest        # real client + production jar + src/gametest mod
java tools/KeySoundGen.java                  # rebuild the bundled Key Sounds samples
```

There are no unit tests. The only test is the client gametest in `src/gametest/java/dev/maro/gametest/MaroClientGameTest.java`. It launches the real game, drives the menu, modules and settings through real input, asserts behaviour, and saves screenshots to `run/screenshots/`. To exercise a change, add a step there and call `context.takeScreenshot("maro-...")`. It runs as one test; there is no per-test filter.

The jar version is `1.0.0+build.<GITHUB_RUN_NUMBER>` on CI and `+local` otherwise.

## CI and releases

`.github/workflows/build.yml` runs on every push:

1. Build.
2. Run `runProductionClientGameTest` under xvfb.
3. Print every screenshot into the job log as base64 JPEG between `PREVIEW_BEGIN <name>` and `PREVIEW_END` lines. This lets you inspect them from the log without downloading artifacts.
4. On push, publish a GitHub release `build-N` with the jar.

Only builds that pass the in-game test are released. Users download jars manually from Releases (there is no auto-updater).

Local Gradle builds may be impossible in sandboxed environments if `maven.fabricmc.net` is unreachable. In that case push and rely on CI. Pure-AWT/pure-Java pieces, such as `nathan/regionmap/*`, can still be compiled and previewed locally with plain `javac`.

## Architecture

The mod has two layers that share one module list, one settings UI and one config system.

**Maro core (`dev.maro.*`)**
- `Maro` is the client entrypoint. It handles keybind dispatch, HUD/tick hooks, and the ScreenEvents hook used by Screen Hider.
- `module/`: `Module`, `Category`, and `ModuleManager`. `ModuleManager.init()` registers native modules, then calls `NameeProtectAddon.init()`.
- `setting/`: the settings the menu renders, such as `BooleanSetting`, `NumberSetting`, `ModeSetting`, `ButtonSetting`, `RegionsSetting` and `SettingSection`.
- `gui/ClickGuiScreen` is an immediate-mode window with top-bar navigation, opened with Right Shift. `gui/page/*` are its pages; `gui/widget/SettingsList` renders every setting type.
- `gui/render/Render2D` and `ShapeRenderState` draw anti-aliased vector shapes by submitting `SimpleGuiElementRenderState`s through `DrawContextAccessor.maro$getState()`.
  - The 1.21.11 GUI pipeline **culls back faces**, so quads must use vanilla winding (`ShapeRenderState.Builder.quad` normalizes this).
  - Degenerate quads disappear.
- `gui/render/Fonts` holds the bundled Inter font. It has per-GUI-scale font definitions in `assets/maro/font/inter_*_x1..x6.json` and an all-caps mode.
- `config/ConfigManager` stores everything under `.minecraft/maro/`: `client.json` holds GUI settings and friends; `configs/*.json` holds modules. Settings are saved **by name**, so renaming a setting key is the way to make a changed default reach users who already have a saved config.

**Nathan port (`dev.maro.runtime.*` + `dev.maro.nathan.*`)**
- `dev.maro.runtime` is a small Meteor-like compatibility runtime with these parts:
  - `systems.modules.Module`, which **extends `dev.maro.module.Module`**
  - `settings.*` builders (`IntSetting.Builder`, …)
  - `MeteorClient.EVENT_BUS` with `@EventHandler`
  - `renderer.Renderer2D` / `MeshBuilder`
  - `RuntimeEvents`, which posts `TickEvent` / `Render2DEvent` / `Render3DEvent` from Maro's hooks
- Runtime modules subscribe to the event bus in `onEnable` and implement `onActivate`/`onDeactivate`.
- Runtime settings are adapted into Maro `SettingSection`s for the menu.
- `getWidget(GuiTheme)` buttons become an "Actions" section.
- Runtime settings persist through `saveExtra`/`loadExtra`, as an NBT tag keyed by setting name.
- `dev.maro.nathan.modules` holds the 20 ported modules. They are registered in `NameeProtectAddon.init()`, and the gametest asserts that exact count.
- Helpers live in `nathan/render`:
  - `RoundedBox` draws soft-edged rounded boxes, shadows, ripples and convex polygons into `Renderer2D.COLOR.triangles`; the caller owns `begin()`/`render()`.
  - `CrispFont` draws pixel-aligned Poppins text and must be wrapped in `CrispFont.begin`/`end`.
  - `PostEffect` handles shader chains.
- Nathan assets live under `assets/nameeprotect/`.
- Mixins are split between `maro.mixins.json` (`dev.maro.mixin`) and `nathan.mixins.json` (`dev.maro.nathan.mixin`).

**Notable native modules**
- `StretchRes` hooks the world projection in `GameRendererMixin`.
- `FastPlace` sets the item-use cooldown via `MinecraftClientAccessor`.
- `ScreenHider` pairs `gui/hider/RegionEditorScreen` (draw-to-hide areas, saved as normalized rects in `RegionsSetting`) with `HiderRenderer`. `HiderRenderer` blurs or pixelates with a custom `RenderPipeline` and the shader `assets/maro/shaders/core/screen_hider.fsh`, copies the framebuffer with `copyTextureToTexture`, and draws the banner.

## Conventions

- Port Fabric/Yarn code to **1.21.11** names: `Click`/`KeyInput`/`CharInput` input records, `GuiRenderState`, `Matrix3x2fStack`, and `HudElementRegistry`. Older-version examples usually do not compile.
- The menu's default look is black with an accent colour, using small bold all-caps type.
- New modules are plain modules; anti-cheat bypass features are out of scope.
