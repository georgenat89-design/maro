# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

maro.gg is a client-only Fabric mod for **Minecraft 1.21.11** (Java 21, Yarn mappings; exact versions in `gradle.properties`). `fabric.mod.json` pins the Minecraft version, so the jar loads only on 1.21.11. The README covers user-facing features, the "Adding a module" example, and Spotify lyrics behaviour.

## Commands

```
./gradlew build                          # jar -> build/libs/maro-mc1.21.11-1.0.0+local.jar (+build.N on CI)
./gradlew runClient                      # dev client
./gradlew runProductionClientGameTest    # real client + production jar + the src/gametest mod
java tools/KeySoundGen.java              # regenerate the bundled Key Sounds samples
```

**There are no unit tests.** The test suite is a Fabric client gametest (`src/gametest`, its own source set and mod `maro-gametest`). `MaroClientGameTest` launches the game, opens the menu via the keybind, drives pages/modules/settings with real input, throws `AssertionError` on failure, and saves screenshots to `run/screenshots/` via `context.takeScreenshot("maro-...")`. `SpotifyLyricsChecks.run(context)` is called from it for lyrics parsing/timing checks. It is a single test with no per-test filter. To cover a change, add a step and screenshot there. Example: the test asserts that exactly 20 runtime modules are registered (`Modules.get().getAll().size() != 20`). Update it when that count changes.

The production test runs with `-Dmaro.debug=true` (currently it enables matrix logging in `StretchRes`).

Gradle needs `maven.fabricmc.net`. In sandboxed sessions that host may be blocked (it is blocked in the Claude Code cloud environment), so Gradle cannot resolve. Then push and rely on CI.

## CI

`.github/workflows/build.yml` runs on every push and PR:

1. It runs `build`.
2. It runs `runProductionClientGameTest` under xvfb with `ALSOFT_DRIVERS=null`. Any mixin failure, crash or render exception fails the build.
3. It prints each screenshot into the job log as base64 JPEG between `PREVIEW_BEGIN <name>` and `PREVIEW_END`. Screenshots are also uploaded as artifacts.
4. On push, it publishes a GitHub release `build-<run_number>` containing the jar.

## Architecture

There are two layers. They share one module registry, one ClickGUI and one config system.

### Maro core: `dev.maro.*`
- **Entrypoint and modules.** `Maro` is the entrypoint and handles keybind dispatch and HUD/tick hooks. `module/ModuleManager.init()` registers the native modules (`module/impl/*`: ScreenHider, FastPlace, StretchRes), then calls `nathan.NameeProtectAddon.init()`.
- **Settings.** `setting/*` holds the types the menu understands: Boolean, Number, Mode, Color, Keybind, Button, Action and Regions, grouped into `SettingSection`s.
- **ClickGUI.** `gui/ClickGuiScreen` is an immediate-mode screen. `gui/page/*` are its pages. `gui/widget/SettingsList` renders every setting type.
- **Rendering.** `gui/render/Render2D` with `ShapeRenderState` draws anti-aliased vector geometry as GUI render states, using `DrawContextAccessor`. The 1.21.11 GUI pipeline **culls back faces**, so quads must use vanilla winding (`ShapeRenderState.Builder.quad` normalizes it). Degenerate quads vanish.
- **Fonts.** Fonts are bundled Inter, with one definition per GUI scale in `assets/maro/font/inter_*_x1..x6.json`.
- **Config.** `config/ConfigManager` writes to `.minecraft/maro/`:
  - `client.json` holds GUI settings and friends.
  - `configs/*.json` holds modules: `enabled`, `bind`, `settings` keyed by setting name, and an opaque `extra` object from `Module.saveExtra()`/`loadExtra()`.
  - Because settings are stored by name, renaming a setting resets it for existing configs.

### Nathan port: `dev.maro.runtime.*` and `dev.maro.nathan.*`
- **`dev.maro.runtime`** is a small Meteor-Client-shaped compatibility layer, so that ported Meteor addon code compiles almost unchanged. It does not bundle Meteor. It has these parts:
  - `systems.modules.Module` **extends `dev.maro.module.Module`**. `onEnable`/`onDisable` are final. They subscribe and unsubscribe the module on `MeteorClient.EVENT_BUS` and call `onActivate`/`onDeactivate`.
  - Event handlers use `@EventHandler`. `RuntimeEvents` and the mixins in `dev.maro.mixin` post `TickEvent`, `Render2DEvent`, `Render3DEvent`, packet, key and mouse events.
  - Runtime `settings.*` (builder style, in `SettingGroup`s) are bridged to Maro settings by `settings/SettingAdapters`. A module's `getWidget(GuiTheme)` output becomes an "Actions" section.
  - Runtime settings persist as an NBT tag, converted to JSON, inside the config's `extra` object.
- **Ported modules.** `dev.maro.nathan.modules` holds the ported modules registered in `NameeProtectAddon`. Some files there are helpers rather than modules; for example, `Aim` and `Highlight` belong to SpawnerProtect. The rest of `nathan/*` holds:
  - Screens (`gui`).
  - Windows media and audio bridges and LRCLIB lyrics (`audio`).
  - Render helpers (`render`):
    - `RoundedBox` draws into `Renderer2D.COLOR`, and the caller owns `begin()`/`render()`.
    - `CrispFont` draws Poppins and must be wrapped in `begin`/`end`.
    - `PostEffect` handles shader chains.
- **Assets.** Nathan assets stay under the `assets/nameeprotect/` namespace so that existing shader, texture and font ids resolve.
- **Mixins.** Two mixin configs: `maro.mixins.json` (`dev.maro.mixin`) and `nathan.mixins.json` (`dev.maro.nathan.mixin`). `defaultRequire` is 1, so a missed injection crashes at startup. CI catches that.

## Conventions

- Use **1.21.11 Yarn** APIs, for example the `Click`/`KeyInput`/`CharInput` input records, `GuiRenderState`, `Matrix3x2fStack` and `HudElementRegistry`. Snippets written for older versions usually will not compile.
- Ported third-party files keep their license headers. Update `THIRD_PARTY.md` and `LICENSES/` when bundling new third-party code or assets. The jar task packages `LICENSE-Nathan`, `THIRD_PARTY.md` and `LICENSES/`.
- The README's "Project layout" tree predates the `runtime`/`nathan` packages. Treat the source tree as authoritative.
