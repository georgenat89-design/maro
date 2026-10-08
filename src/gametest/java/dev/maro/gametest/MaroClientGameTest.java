package dev.maro.gametest;

import dev.maro.gui.ClickGuiScreen;
import dev.maro.module.Module;
import dev.maro.module.ModuleManager;
import dev.maro.module.impl.ExampleModule;
import dev.maro.module.impl.visuals.StretchRes;
import dev.maro.module.impl.player.FastPlace;
import dev.maro.module.impl.misc.ScreenHider;
import dev.maro.gui.hider.RegionEditorScreen;
import dev.maro.mixin.MinecraftClientAccessor;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import org.lwjgl.glfw.GLFW;

/**
 * Smoke test run in CI against the production jar: if a mixin, the renderer or any page
 * throws, the game crashes and the build fails. Screenshots of every page are uploaded.
 */
public class MaroClientGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        MiningCadenceChecks.run();
        context.getInput().resizeWindow(1280, 720);
        context.runOnClient(client -> {
            if (ModuleManager.getByName("Example") == null) ModuleManager.register(new ExampleModule());
            // Saved test-profile modules (especially Screen Hider) must not mask later screenshots.
            ModuleManager.all().forEach(module -> module.setEnabled(false));
        });

        try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
            singleplayer.getClientWorld().waitForChunksRender();

            // make sure nothing (pause menu, toasts...) is in the way before testing the keybind
            context.setScreen(() -> null);
            context.waitTicks(5);
            // Optional startup probe for an externally supplied companion addon.
            if (Boolean.getBoolean("maro.gametest.launchOnly")) {
                context.waitTicks(100);
                context.takeScreenshot("maro-companion-launch");
                return;
            }
            if (Boolean.getBoolean("maro.gametest.builderOnly")) {
                AutoBuilderChecks.run(context,singleplayer);
                return;
            }
            AutoBuilderChecks.imports();
            if (Boolean.getBoolean("maro.gametest.staffOnly")) {
                StaffNotifierChecks.run(context);
                AntiVanishChecks.run(context);
                return;
            }
            if (Boolean.getBoolean("maro.gametest.spotifyOnly")) {
                SpotifyLyricsChecks.run(context);
                SpotifyVolumeChecks.run(context);
                return;
            }
            if (Boolean.getBoolean("maro.gametest.hudOnly")) {
                StaffNotifierChecks.run(context);
                HudReadabilityChecks.run(context);
                return;
            }
            if (Boolean.getBoolean("maro.gametest.baseEspOnly")) {
                BaseEspChecks.run(context, singleplayer);
                return;
            }
            if (Boolean.getBoolean("maro.gametest.skyOnly")) {
                SkyChecks.run(context, singleplayer);
                return;
            }
            if (Boolean.getBoolean("maro.gametest.totemOnly")) {
                TotemChecks.run(context, singleplayer);
                return;
            }
            if (Boolean.getBoolean("maro.gametest.petOnly")) {
                PetChecks.run(context, singleplayer);
                return;
            }
            if (Boolean.getBoolean("maro.gametest.projectionOnly")) {
                StretchProjectionChecks.run(context, singleplayer);
                return;
            }
            checkNathanPort(context);
            SpotifyLyricsChecks.run(context);
            SpotifyVolumeChecks.run(context);
            checkSlowSwing(context);
            checkKeySounds(context);
            checkRegionMap(context);
            AutoTridentChecks.run(context, singleplayer);
            AutoToolChecks.run(context, singleplayer);
            CrosshairChecks.run(context);
            CrosshairPngChecks.run(context);
            SkinAccessoriesChecks.run(context);
            PetChecks.run(context, singleplayer);
            PlayerEspChecks.run(context, singleplayer);
            BaseEspChecks.run(context, singleplayer);
            SkyChecks.run(context, singleplayer);
            TotemChecks.run(context, singleplayer);
            ScreenChecks.run(context, singleplayer);
            EmoteChecks.run(context, singleplayer);
            StaffNotifierChecks.run(context);
            HudReadabilityChecks.run(context);
            AntiVanishChecks.run(context);
            String before = context.computeOnClient(client -> describe(client));
            context.takeScreenshot("maro-00-world");

            // open through the real keybind path (KeyboardMixin -> Maro.onKey)
            context.getInput().pressKey(GLFW.GLFW_KEY_RIGHT_SHIFT);
            context.waitTicks(5);
            boolean opened = context.computeOnClient(client -> client.currentScreen instanceof ClickGuiScreen);
            if (!opened) {
                String after = context.computeOnClient(client -> describe(client));
                context.takeScreenshot("maro-00-keybind-failed");
                throw new AssertionError("Right Shift did not open the menu. before=[" + before + "] after=[" + after + "]");
            }
            settle(context);
            context.takeScreenshot("maro-01-open");
            PanelChecks.run(context);

            String[] pages = {"combat", "movement", "player", "visuals", "misc", "settings", "configs", "theme", "socials"};
            for (int i = 0; i < pages.length; i++) {
                final int index = i;
                context.runOnClient(client -> ((ClickGuiScreen) client.currentScreen).openPage(index));
                settle(context);
                context.takeScreenshot("maro-page-" + (i + 1 < 10 ? "0" : "") + (i + 1) + "-" + pages[i]);
            }

            context.runOnClient(client -> {
                Module example = ModuleManager.getByName("Example");
                example.setEnabled(true);
                ((ClickGuiScreen) client.currentScreen).openModuleSettings(example);
            });
            settle(context);
            context.takeScreenshot("maro-module-settings");

            // Bigger modules open on a card per category; a card opens just that category.
            context.runOnClient(client -> ((ClickGuiScreen) client.currentScreen)
                    .openModuleSettings(ModuleManager.get(dev.maro.module.impl.visuals.Compass.class)));
            settle(context);
            context.takeScreenshot("maro-settings-categories");
            context.runOnClient(client -> ((ClickGuiScreen) client.currentScreen)
                    .openModuleSettings(ModuleManager.get(dev.maro.module.impl.visuals.Compass.class), "Look"));
            settle(context);
            context.takeScreenshot("maro-settings-category");
            context.runOnClient(client -> ((ClickGuiScreen) client.currentScreen)
                    .openModuleSettings(ModuleManager.getByName("Keystrokes")));
            settle(context);
            context.takeScreenshot("maro-settings-categories-keystrokes");
            context.runOnClient(client -> ((ClickGuiScreen) client.currentScreen)
                    .openModuleSettings(ModuleManager.getByName("Keystrokes"), "Colors"));
            settle(context);
            context.takeScreenshot("maro-settings-category-keystrokes");
            context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE); // back to the categories
            settle(context);
            context.takeScreenshot("maro-settings-back");

            context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE); // back to the grid
            settle(context);
            context.getInput().typeChars("exa");
            settle(context);
            context.takeScreenshot("maro-search");

            // Escape clears the search, unfocuses it, goes back to the panels, then closes the menu
            for (int i = 0; i < 6 && context.computeOnClient(client -> client.currentScreen instanceof ClickGuiScreen); i++) {
                context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
                settle(context);
            }
            context.waitForScreen(null);

            StretchProjectionChecks.run(context, singleplayer);

            // Stretch Res must actually change the world projection. Build a brick wall in front of
            // the camera so the horizontal stretch is obvious in the screenshots.
            singleplayer.getServer().runCommand("execute as @a at @s run fill ^-6 ^-1 ^7 ^6 ^5 ^7 minecraft:bricks");
            singleplayer.getServer().runCommand("execute as @a at @s run fill ^-1 ^1 ^7 ^1 ^3 ^7 minecraft:gold_block");
            settle(context);
            settle(context);
            context.takeScreenshot("maro-stretch-off");
            float[] m00 = context.computeOnClient(client -> {
                org.joml.Matrix4f base = client.gameRenderer.getBasicProjectionMatrix(70f);
                ModuleManager.get(StretchRes.class).setEnabled(true);
                return new float[]{base.m00(), client.gameRenderer.getBasicProjectionMatrix(70f).m00()};
            });
            if (Math.abs(m00[0] - m00[1]) < 1e-4f) {
                throw new AssertionError("Stretch Res did not change the projection (m00 " + m00[0] + " -> " + m00[1] + ")");
            }
            settle(context);
            settle(context);
            context.takeScreenshot("maro-stretch-on");
            context.runOnClient(client -> ModuleManager.get(StretchRes.class).setEnabled(false));

            // Fast Place: with a block in hand the vanilla 4-tick cooldown is cut to the delay (0)
            singleplayer.getServer().runCommand("give @a minecraft:stone 64");
            settle(context);
            int cooldown = context.computeOnClient(client -> {
                client.player.getInventory().setSelectedSlot(0);
                FastPlace fastPlace = ModuleManager.get(FastPlace.class);
                fastPlace.setEnabled(true);
                ((MinecraftClientAccessor) client).maro$setItemUseCooldown(4);
                fastPlace.onTick();
                int value = ((MinecraftClientAccessor) client).maro$getItemUseCooldown();
                fastPlace.setEnabled(false);
                return value;
            });
            if (cooldown != 0) throw new AssertionError("Fast Place did not shorten the cooldown (got " + cooldown + ")");

            // Screen Hider: scribble an area in the editor with the real mouse, then check it is blurred
            context.runOnClient(client -> {
                ScreenHider hider = ModuleManager.get(ScreenHider.class);
                hider.setEnabled(true);
                hider.regions().list().clear();
                client.setScreen(new RegionEditorScreen(hider.regions(), null));
            });
            settle(context);
            context.getInput().setCursorPos(420, 220);
            context.getInput().holdMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
            for (int i = 1; i <= 12; i++) {
                context.getInput().setCursorPos(420 + i * 36, i % 2 == 0 ? 230 : 470);
                context.waitTick();
            }
            settle(context);
            context.takeScreenshot("maro-hider-drawing");
            context.getInput().releaseMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
            settle(context);
            int areas = context.computeOnClient(client -> ModuleManager.get(ScreenHider.class).regions().list().size());
            if (areas != 1) throw new AssertionError("Drawing in the Screen Hider editor made " + areas + " areas (expected 1)");
            context.takeScreenshot("maro-hider-editor");
            context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
            context.waitForScreen(null);
            settle(context);
            settle(context);
            context.takeScreenshot("maro-hider-blur");
            context.runOnClient(client -> ModuleManager.get(ScreenHider.class).getSettings().stream()
                    .filter(s -> s.getName().equals("Style")).findFirst()
                    .ifPresent(s -> ((dev.maro.setting.ModeSetting) s).set("Banner")));
            settle(context);
            context.takeScreenshot("maro-hider-banner");
            context.runOnClient(client -> ModuleManager.get(ScreenHider.class).setEnabled(false));

            // FreeCam: with the camera inside solid ground the world beyond must still be drawn
            // (occlusion culling used to hide it), and culling must come back afterwards.
            singleplayer.getServer().runCommand("execute as @a at @s run fill ^-10 ^-3 ^3 ^10 ^8 ^14 minecraft:stone");
            singleplayer.getServer().runCommand("execute as @a at @s run fill ^-8 ^-2 ^22 ^8 ^8 ^22 minecraft:gold_block");
            settle(context);
            settle(context);
            boolean cullingBefore = context.computeOnClient(client -> client.chunkCullingEnabled);
            boolean cullingWhileOut = context.computeOnClient(client -> {
                dev.maro.nathan.modules.FreeCam freeCam = ModuleManager.get(dev.maro.nathan.modules.FreeCam.class);
                freeCam.setEnabled(true);
                net.minecraft.util.math.Vec3d look = client.player.getRotationVector();
                dev.maro.nathan.modules.FreeCam.placeCamera(client.player.getX() + look.x * 8, client.player.getEyeY(),
                        client.player.getZ() + look.z * 8, client.player.getYaw(), 0f);
                return client.chunkCullingEnabled;
            });
            if (cullingWhileOut) throw new AssertionError("FreeCam left chunk culling on");
            settle(context);
            settle(context);
            context.takeScreenshot("maro-freecam-in-stone");
            boolean cullingAfter = context.computeOnClient(client -> {
                ModuleManager.get(dev.maro.nathan.modules.FreeCam.class).setEnabled(false);
                return client.chunkCullingEnabled;
            });
            if (cullingAfter != cullingBefore) throw new AssertionError("FreeCam did not restore chunk culling");

            // Freelook through walls: the same, while the orbiting camera can pass through blocks.
            boolean freelookOff = context.computeOnClient(client -> {
                var freeLook = ModuleManager.get(dev.maro.nathan.modules.FreeLook.class);
                freeLook.getSettings().stream().filter(s -> s.getName().equals("through walls")).findFirst()
                        .orElseThrow().fromJson(new com.google.gson.JsonPrimitive(true));
                freeLook.setEnabled(true);
                return !client.chunkCullingEnabled;
            });
            settle(context);
            context.takeScreenshot("maro-freelook-through-walls");
            boolean freelookRestored = context.computeOnClient(client -> {
                var freeLook = ModuleManager.get(dev.maro.nathan.modules.FreeLook.class);
                freeLook.setEnabled(false);
                for (var s : freeLook.getSettings()) if (s.getName().equals("through walls")) s.reset();
                return client.chunkCullingEnabled == cullingBefore;
            });
            if (!freelookOff) throw new AssertionError("Freelook through walls left chunk culling on");
            if (!freelookRestored) throw new AssertionError("Freelook did not restore chunk culling");

            checkInventoryHud(context, singleplayer);
            checkFullbright(context, singleplayer);
            checkPotatoGraphics(context, singleplayer);
            checkAutoMine(context, singleplayer);
            AutoMineRouteChecks.run(context, singleplayer);
            checkCrafterDisabler(context, singleplayer);
            checkNoRender(context, singleplayer);
            checkItemInspect(context, singleplayer);
            checkViewModel(context, singleplayer);
            checkBlockDisconnect(context, singleplayer);
            checkCompass(context);
        }
    }

    private static String describe(net.minecraft.client.MinecraftClient client) {
        return "screen=" + (client.currentScreen == null ? "none" : client.currentScreen.getClass().getName())
                + " player=" + (client.player != null) + " world=" + (client.world != null);
    }

    private static void checkNathanPort(ClientGameTestContext context) {
        long failures = context.computeOnClient(client -> dev.maro.runtime.MeteorClient.EVENT_BUS.failureCount());
        context.runOnClient(client -> {
            if (dev.maro.runtime.systems.modules.Modules.get().getAll().stream()
                    .filter(m -> m.getClass().getPackageName().equals("dev.maro.nathan.modules")).count() != 20)
                throw new AssertionError("Expected all 20 current Nathan modules");
            for (var module : dev.maro.runtime.systems.modules.Modules.get().getAll()) {
                if (module.getSettings().isEmpty()) throw new AssertionError("No settings for " + module.name);
                module.setEnabled(false);
                for (var group : module.settings) for (var setting : group) setting.reset();
                for (var setting : module.getSettings()) setting.fromJson(setting.toJson());
            }
            // Macro definitions need their own saved data, beyond the module's scalar settings.
            var macros = ModuleManager.get(dev.maro.nathan.modules.ChatMacros.class);
            var macro = new dev.maro.nathan.modules.ChatMacros.Macro();
            macro.name.set("Port smoke test"); macro.steps.set(java.util.List.of("Hello; world!", "@wait 1500"));
            macros.macros.add(macro);
            var saved = macros.saveExtra(); macros.macros.clear(); macros.loadExtra(saved);
            if (macros.macros.size() != 1 || !macros.macros.getFirst().name.get().equals("Port smoke test")
                || !macros.macros.getFirst().steps.get().equals(macro.steps.get()))
                throw new AssertionError("Macro config did not round-trip");
            var map = ModuleManager.get(dev.maro.nathan.modules.RegionMap.class);
            var x = map.getSettings().stream().filter(s -> s.getName().equals("x")).findFirst().orElseThrow();
            var oldX = x.toJson();
            x.fromJson(new com.google.gson.JsonPrimitive(65));
            if (!dev.maro.config.ConfigManager.save("port_smoke")) throw new AssertionError("Config save failed");
            x.fromJson(oldX); macros.macros.clear();
            if (!dev.maro.config.ConfigManager.load("port_smoke") || x.toJson().getAsInt() != 65
                || macros.macros.size() != 1 || !macros.macros.getFirst().steps.get().equals(macro.steps.get()))
                throw new AssertionError("Maro config did not restore Nathan settings and macros");
            x.fromJson(oldX);
            dev.maro.config.ConfigManager.delete("port_smoke");
            ModuleManager.get(dev.maro.nathan.modules.SpotifyHud.class).getSettings().stream()
                .filter(s -> s.getName().equals("lyrics")).findFirst().orElseThrow().fromJson(new com.google.gson.JsonPrimitive(false));
            for (String name : java.util.List.of("Region Map", "Keystrokes", "Spotify Hud", "Hats", "Spin Bot", "Custom Fov", "Key Zoom", "Free Cam", "Freelook")) {
                var module = ModuleManager.getByName(name);
                if (module == null) throw new AssertionError("Missing " + name);
                module.setEnabled(true);
            }
        });
        context.waitTicks(15);
        context.takeScreenshot("maro-nathan-huds");
        context.runOnClient(client -> {
            for (var module : dev.maro.runtime.systems.modules.Modules.get().getAll()) module.setEnabled(false);
            if (client.options.forwardKey.isPressed() || client.options.useKey.isPressed())
                throw new AssertionError("A ported module left an input key pressed");
        });
        context.setScreen(() -> new dev.maro.nathan.gui.ChatMacroScreens.Manager(new dev.maro.runtime.gui.GuiTheme(), ModuleManager.get(dev.maro.nathan.modules.ChatMacros.class)));
        context.waitTicks(3);
        context.takeScreenshot("maro-macro-manager");
        context.setScreen(() -> new dev.maro.nathan.gui.ChatMacroScreens.Editor(new dev.maro.runtime.gui.GuiTheme(), ModuleManager.get(dev.maro.nathan.modules.ChatMacros.class), ModuleManager.get(dev.maro.nathan.modules.ChatMacros.class).macros.getFirst(), () -> {}));
        context.waitTicks(3);
        context.takeScreenshot("maro-macro-editor");
        context.setScreen(() -> null);
        context.runOnClient(client -> ModuleManager.get(dev.maro.nathan.modules.ChatMacros.class).macros.clear());
        for (String name : java.util.List.of("Bloom", "Color Correct", "Motion Blur")) {
            context.runOnClient(client -> {
                var module = ModuleManager.getByName(name);
                if (name.equals("Color Correct")) module.getSettings().stream().filter(s -> s.getName().equals("saturation")).findFirst().orElseThrow().fromJson(new com.google.gson.JsonPrimitive(.75));
                module.setEnabled(true);
            });
            context.waitTicks(5);
            context.takeScreenshot("maro-effect-" + name.replace(' ', '-'));
            context.runOnClient(client -> {
                var module = ModuleManager.getByName(name);
                try {
                    var field = module.getClass().getDeclaredField("effect"); field.setAccessible(true);
                    var effect = (dev.maro.nathan.render.PostEffect)field.get(module);
                    var broken = effect.getClass().getDeclaredField("broken"); broken.setAccessible(true);
                    if (effect.chain() == null || broken.getBoolean(effect)) throw new AssertionError(name + " shader did not run");
                } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
                module.setEnabled(false);
            });
        }
        context.runOnClient(client -> {
            if (dev.maro.runtime.MeteorClient.EVENT_BUS.failureCount() != failures)
                throw new AssertionError("A Nathan event handler failed; see the game log");
        });
    }

    /** Fullbright: sealed in a dark stone room at midnight, the screen must get much brighter, and the option must not change. */
    /** Potato Graphics lowers the video settings while on and puts every one back when off. */
    private static void checkPotatoGraphics(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        // A small daylight scene up in the air - grass, dirt, a tree and some stone - to see the flat textures on.
        double[] home = context.computeOnClient(client -> new double[]{client.player.getX(), client.player.getY(), client.player.getZ()});
        int sx = (int) Math.floor(home[0]), sy = (int) Math.floor(home[1]) + 40, sz = (int) Math.floor(home[2]);
        for (String command : java.util.List.of(
                "time set noon",
                "fill " + (sx - 12) + " " + sy + " " + (sz - 4) + " " + (sx + 12) + " " + sy + " " + (sz + 24) + " minecraft:grass_block",
                "fill " + (sx - 7) + " " + (sy + 1) + " " + (sz + 12) + " " + (sx + 7) + " " + (sy + 2) + " " + (sz + 16) + " minecraft:grass_block",
                "fill " + (sx - 7) + " " + (sy + 1) + " " + (sz + 11) + " " + (sx - 2) + " " + (sy + 1) + " " + (sz + 11) + " minecraft:dirt",
                "fill " + (sx + 3) + " " + (sy + 1) + " " + (sz + 7) + " " + (sx + 3) + " " + (sy + 4) + " " + (sz + 7) + " minecraft:oak_log",
                "fill " + (sx + 1) + " " + (sy + 4) + " " + (sz + 5) + " " + (sx + 5) + " " + (sy + 6) + " " + (sz + 9) + " minecraft:oak_leaves[persistent=true] replace minecraft:air",
                "fill " + (sx - 5) + " " + (sy + 1) + " " + (sz + 6) + " " + (sx - 3) + " " + (sy + 1) + " " + (sz + 7) + " minecraft:stone",
                "setblock " + (sx - 4) + " " + (sy + 2) + " " + (sz + 6) + " minecraft:cobblestone",
                "tp @a " + (sx + 0.5) + " " + (sy + 1) + " " + (sz + 0.5) + " 0 15")) {
            singleplayer.getServer().runCommand(command);
        }
        context.runOnClient(client -> {
            client.options.getViewDistance().setValue(12);
            client.options.getAo().setValue(true);
            client.options.getCloudRenderMode().setValue(net.minecraft.client.option.CloudRenderMode.FANCY);
            client.options.getParticles().setValue(net.minecraft.particle.ParticlesMode.ALL);
            client.options.getEntityShadows().setValue(true);
        });
        settle(context);
        java.util.function.Function<net.minecraft.client.MinecraftClient, String> snapshot = client -> client.options.getViewDistance().getValue()
                + "|" + client.options.getAo().getValue() + "|" + client.options.getCloudRenderMode().getValue()
                + "|" + client.options.getParticles().getValue() + "|" + client.options.getEntityShadows().getValue()
                + "|" + client.options.getMipmapLevels().getValue() + "|" + client.options.getMaxFps().getValue()
                + "|" + client.options.getBiomeBlendRadius().getValue() + "|" + client.options.getEnableVsync().getValue();
        String before = context.computeOnClient(snapshot::apply);
        context.takeScreenshot("maro-potato-scene-normal");
        var potato = ModuleManager.get(dev.maro.module.impl.visuals.PotatoGraphics.class);
        context.runOnClient(client -> potato.setEnabled(true));
        context.waitTicks(3);
        context.waitFor(client -> dev.maro.module.impl.visuals.PotatoGraphics.texturesSettled(), 2400);
        settle(context);
        int flattened = context.computeOnClient(client -> dev.maro.module.impl.visuals.PotatoGraphics.flattenedSprites());
        String lowered = context.computeOnClient(snapshot::apply);
        context.takeScreenshot("maro-potato-graphics-on");
        if (flattened < 200) throw new AssertionError("Potato Graphics did not flatten the block textures: " + flattened);
        context.runOnClient(client -> potato.setEnabled(false));
        context.waitTicks(3);
        context.waitFor(client -> dev.maro.module.impl.visuals.PotatoGraphics.texturesSettled(), 2400);
        settle(context);
        String after = context.computeOnClient(snapshot::apply);
        singleplayer.getServer().runCommand("tp @a " + home[0] + " " + home[1] + " " + home[2]);
        context.waitTicks(3);
        System.out.println("POTATO before=" + before + " on=" + lowered + " after=" + after + " flattened=" + flattened);
        // Textures only now: your video settings are left exactly as they are.
        if (!before.equals(lowered) || !before.equals(after))
            throw new AssertionError("Potato Graphics changed video settings: " + before + " -> " + lowered + " -> " + after);

        // Someone who updated with an older version on gets the settings it lowered put back.
        context.runOnClient(client -> {
            client.options.getViewDistance().setValue(5);
            client.options.getAo().setValue(false);
            var saved = new com.google.gson.JsonObject();
            saved.addProperty("render-distance", 12);
            saved.addProperty("smooth-lighting", true);
            potato.loadExtra(saved);
        });
        context.waitTicks(3);
        String restored = context.computeOnClient(client -> client.options.getViewDistance().getValue() + "|" + client.options.getAo().getValue());
        System.out.println("POTATO legacy originals restored=" + restored);
        if (!restored.equals("12|true")) throw new AssertionError("Potato Graphics did not put back the settings an older version saved: " + restored);
        if (!potato.saveExtra().entrySet().isEmpty()) throw new AssertionError("Potato Graphics kept old saved settings after putting them back");
    }

    private static void checkFullbright(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        singleplayer.getServer().runCommand("time set midnight");
        singleplayer.getServer().runCommand("execute as @a at @s run fill ~-5 ~-1 ~-5 ~5 ~5 ~5 minecraft:stone hollow");
        singleplayer.getServer().runCommand("execute as @a at @s run fill ~-4 ~ ~-4 ~4 ~4 ~4 minecraft:air");
        context.runOnClient(client -> client.player.setPitch(10f));
        settle(context);
        settle(context);
        double gammaBefore = context.computeOnClient(client -> client.options.getGamma().getValue());
        double dark = luminance(context.takeScreenshot("maro-fullbright-off"));
        var fullbright = ModuleManager.get(dev.maro.module.impl.visuals.Fullbright.class);
        context.runOnClient(client -> fullbright.setEnabled(true));
        settle(context);
        double bright = luminance(context.takeScreenshot("maro-fullbright-on"));
        double gammaAfter = context.computeOnClient(client -> client.options.getGamma().getValue());
        context.runOnClient(client -> fullbright.setEnabled(false));
        settle(context);
        if (gammaAfter != gammaBefore) throw new AssertionError("Fullbright changed the Brightness option: " + gammaBefore + " -> " + gammaAfter);
        if (bright < dark + 30 || bright < dark * 1.5) throw new AssertionError("Fullbright did not brighten the dark room (" + dark + " -> " + bright + ")");
    }

    /** Auto Mine digs a 1x2 tunnel through solid stone in survival, mines a wall ore, fills a hole and lights the way. */
    private static void checkAutoMine(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        int[] at = context.computeOnClient(client -> new int[]{client.player.getBlockX(), client.player.getBlockY() + 30, client.player.getBlockZ()});
        int x = at[0], y = at[1], z = at[2];
        for (String command : java.util.List.of(
                "time set noon",
                "gamemode survival @a",
                "clear @a",
                "give @a minecraft:diamond_pickaxe[enchantments={efficiency:5}]",
                "give @a minecraft:torch 16",
                "give @a minecraft:cobblestone 32",
                "fill " + (x - 3) + " " + y + " " + (z - 1) + " " + (x + 3) + " " + (y + 5) + " " + (z + 16) + " minecraft:stone",
                "fill " + x + " " + (y + 1) + " " + z + " " + x + " " + (y + 2) + " " + z + " minecraft:air",
                "setblock " + (x - 1) + " " + (y + 2) + " " + (z + 5) + " minecraft:diamond_ore",
                "setblock " + x + " " + y + " " + (z + 7) + " minecraft:air",
                "tp @a " + (x + 0.5) + " " + (y + 1) + " " + (z + 0.5) + " 0 0")) {
            singleplayer.getServer().runCommand(command);
        }
        settle(context);
        var mine = ModuleManager.get(dev.maro.module.impl.player.AutoMine.class);
        String facing = context.computeOnClient(client -> {
            // Face down the tunnel here rather than trusting the teleport: earlier checks move the
            // mouse, and a stray mouse turn would point the tunnel somewhere else.
            client.player.setYaw(0f);
            client.player.setPitch(0f);
            client.player.getInventory().setSelectedSlot(0);
            for (var s : mine.getSettings()) {
                if (s.getName().equals("Max Distance")) s.fromJson(new com.google.gson.JsonPrimitive(10));
                if (s.getName().equals("Torch Gap")) s.fromJson(new com.google.gson.JsonPrimitive(4));
                if (s.getName().equals("Humanize") || s.getName().equals("Short Breaks")) s.fromJson(new com.google.gson.JsonPrimitive(true));
                if (s.getName().equals("Min Blocks Between Breaks")) s.fromJson(new com.google.gson.JsonPrimitive(3));
                if (s.getName().equals("Max Blocks Between Breaks")) s.fromJson(new com.google.gson.JsonPrimitive(5));
                if (s.getName().equals("Min Break Length")) s.fromJson(new com.google.gson.JsonPrimitive(2));
                if (s.getName().equals("Max Break Length")) s.fromJson(new com.google.gson.JsonPrimitive(5));
            }
            mine.setEnabled(true);
            return client.player.getHorizontalFacing().asString();
        });
        context.waitTicks(60);
        context.takeScreenshot("maro-auto-mine");
        for (int i = 0; i < 800 && context.computeOnClient(client -> mine.isEnabled()); i++) context.waitTick();
        int[] result = context.computeOnClient(client -> new int[]{
                client.player.getBlockZ() - z, mine.minedCount(), mine.oreCount(),
                client.player.getInventory().count(net.minecraft.item.Items.TORCH),
                client.world.getBlockState(new net.minecraft.util.math.BlockPos(x, y, z + 7)).isAir() ? 0 : 1,
                client.world.getBlockState(new net.minecraft.util.math.BlockPos(x - 1, y + 2, z + 5)).isAir() ? 1 : 0,
                mine.isEnabled() ? 1 : 0});
        context.takeScreenshot("maro-auto-mine-done");
        if (mine.delayedTickCount() == 0 || mine.restCount() < 2)
            throw new AssertionError("Auto Mine did not use varied action timing and between-block breaks");
        // What the server has, not what the client shows: a ghost-mined block is still there.
        String ghosts = singleplayer.getServer().computeOnServer(server -> {
            var world = server.getOverworld();
            StringBuilder out = new StringBuilder();
            for (int step = 1; step <= 9; step++) {
                for (int up = 1; up <= 2; up++) {
                    var pos = new net.minecraft.util.math.BlockPos(x, y + up, z + step);
                    var state = world.getBlockState(pos);
                    if (!state.getCollisionShape(world, pos).isEmpty()) out.append(' ').append(pos.toShortString());
                }
            }
            if (world.getBlockState(new net.minecraft.util.math.BlockPos(x, y, z + 7)).isAir()) out.append(" hole-not-filled");
            int torches = 0;
            for (var pos : net.minecraft.util.math.BlockPos.iterate(x - 1, y + 1, z, x + 1, y + 2, z + 10)) {
                if (world.getBlockState(pos).isOf(net.minecraft.block.Blocks.WALL_TORCH)) torches++;
            }
            if (torches == 0) out.append(" no-torch");
            return out.toString();
        });
        context.runOnClient(client -> mine.setEnabled(false));
        singleplayer.getServer().runCommand("gamemode creative @a");
        if (result[6] == 1) throw new AssertionError("Auto Mine never stopped at its Max Distance");
        if (result[0] < 9) throw new AssertionError("Auto Mine only got " + result[0] + " blocks along the tunnel (facing " + facing + ")");
        if (result[1] < 15) throw new AssertionError("Auto Mine mined only " + result[1] + " blocks");
        if (result[2] < 1 || result[5] != 1) throw new AssertionError("Auto Mine did not find and mine the diamond ore in the wall");
        if (result[4] != 1) throw new AssertionError("Auto Mine did not fill the hole in the floor");
        if (result[3] >= 16) throw new AssertionError("Auto Mine placed no torches");
        if (!ghosts.isEmpty()) throw new AssertionError("Server disagrees with Auto Mine:" + ghosts);
        // A human input must immediately stop automation and release its movement keys.
        context.runOnClient(client -> {
            for (var setting : mine.getSettings()) setting.reset();
            client.options.attackKey.setPressed(false); client.options.useKey.setPressed(false);
            mine.setEnabled(true); client.options.backKey.setPressed(true);
        });
        context.waitTick();
        context.runOnClient(client -> {
            client.options.backKey.setPressed(false);
            if (mine.isEnabled() || client.options.forwardKey.isPressed() || dev.maro.module.impl.player.AutoMine.holdingBreak())
                throw new AssertionError("Auto Mine did not stop and release control on manual input");
        });
        int neighborId = context.computeOnClient(client -> {
            for (var setting : mine.getSettings()) if (setting.getName().equals("Pause Near Players"))
                setting.fromJson(new com.google.gson.JsonPrimitive(true));
            var neighbor = new net.minecraft.client.network.OtherClientPlayerEntity(client.world,
                    new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "MaroTestNeighbor"));
            neighbor.setPosition(client.player.getX() + 2, client.player.getY(), client.player.getZ());
            client.world.addEntity(neighbor);
            mine.setEnabled(true);
            return neighbor.getId();
        });
        context.waitTicks(2);
        context.runOnClient(client -> {
            if (!mine.isEnabled() || !mine.activity().equals("Player nearby")
                    || client.options.forwardKey.isPressed() || dev.maro.module.impl.player.AutoMine.holdingBreak())
                throw new AssertionError("Auto Mine did not pause and release control near another player");
            client.world.removeEntity(neighborId, net.minecraft.entity.Entity.RemovalReason.DISCARDED);
        });
        context.waitTicks(2);
        context.runOnClient(client -> {
            if (!mine.isEnabled() || mine.activity().equals("Player nearby"))
                throw new AssertionError("Auto Mine did not resume after the nearby player left");
            client.player.setYaw(client.player.getYaw() + 20);
        });
        context.waitTick();
        context.runOnClient(client -> {
            if (mine.isEnabled() || client.options.forwardKey.isPressed() || dev.maro.module.impl.player.AutoMine.holdingBreak())
                throw new AssertionError("Auto Mine did not hand control back after a manual view turn");
            for (var setting : mine.getSettings()) setting.reset();
        });
    }

    /** Crafter Disabler: opening a crafter takes items out of the chosen slots and disables exactly those. */
    private static void checkCrafterDisabler(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        int[] at = context.computeOnClient(client -> new int[]{client.player.getBlockX(), client.player.getBlockY() + 20, client.player.getBlockZ()});
        int x = at[0], y = at[1], z = at[2];
        for (String command : java.util.List.of(
                "clear @a",
                "fill " + (x - 2) + " " + (y - 1) + " " + (z - 2) + " " + (x + 2) + " " + (y - 1) + " " + (z + 3) + " minecraft:stone",
                "fill " + (x - 2) + " " + y + " " + (z - 2) + " " + (x + 2) + " " + (y + 2) + " " + (z + 3) + " minecraft:air",
                "setblock " + x + " " + y + " " + (z + 2) + " minecraft:crafter",
                "item replace block " + x + " " + y + " " + (z + 2) + " container.0 with minecraft:cobblestone 5",
                "item replace block " + x + " " + y + " " + (z + 2) + " container.4 with minecraft:oak_planks 3",
                "tp @a " + (x + 0.5) + " " + y + " " + (z + 0.5) + " 0 30")) {
            singleplayer.getServer().runCommand(command);
        }
        settle(context);
        var disabler = ModuleManager.get(dev.maro.module.impl.player.CrafterDisabler.class);
        context.runOnClient(client -> {
            disabler.clearAll();
            disabler.toggle(0);
            disabler.toggle(4);
            disabler.toggle(8);
            disabler.setEnabled(true);
            var pos = new net.minecraft.util.math.BlockPos(x, y, z + 2);
            client.interactionManager.interactBlock(client.player, net.minecraft.util.Hand.MAIN_HAND,
                    new net.minecraft.util.hit.BlockHitResult(net.minecraft.util.math.Vec3d.ofCenter(pos), net.minecraft.util.math.Direction.UP, pos, false));
        });
        context.waitTicks(15);
        String state = context.computeOnClient(client -> {
            if (!(client.currentScreen instanceof net.minecraft.client.gui.screen.ingame.CrafterScreen screen)) return "no crafter screen";
            var handler = screen.getScreenHandler();
            StringBuilder out = new StringBuilder();
            for (int slot = 0; slot < 9; slot++) {
                out.append(handler.isSlotDisabled(slot) ? 'X' : '.');
                if (handler.getSlot(slot).hasStack()) out.append('*');
            }
            return out.toString();
        });
        context.takeScreenshot("maro-crafter-disabler");
        context.setScreen(() -> new dev.maro.module.impl.player.CrafterSlotsScreen(null, disabler));
        context.waitTicks(3);
        context.takeScreenshot("maro-crafter-slots");
        context.setScreen(() -> null);
        context.runOnClient(client -> {
            disabler.setEnabled(false);
            disabler.clearAll();
        });
        if (!state.equals("X...X...X")) throw new AssertionError("Crafter Disabler left the crafter as " + state + " (expected X...X...X)");
    }

    /** No Render with every switch on, in rain with items, orbs and a falling block about: nothing may break. */
    private static void checkNoRender(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        for (String command : java.util.List.of(
                "time set noon",
                "weather thunder",
                "execute as @a at @s run summon item ~2 ~1 ~2 {Item:{id:\"minecraft:diamond\",count:1}}",
                "execute as @a at @s run summon experience_orb ~-2 ~1 ~2 {Value:5}",
                "execute as @a at @s run summon armor_stand ~ ~ ~4",
                "execute as @a at @s run summon falling_block ~1 ~6 ~3 {BlockState:{Name:\"minecraft:sand\"}}",
                "execute as @a at @s run summon tnt ~ ~ ~6 {fuse:10}")) {
            singleplayer.getServer().runCommand(command);
        }
        settle(context);
        context.takeScreenshot("maro-no-render-off");
        var noRender = ModuleManager.get(dev.maro.module.impl.visuals.NoRender.class);
        context.runOnClient(client -> {
            for (var s : noRender.getSettings()) if (s instanceof dev.maro.setting.BooleanSetting b) b.set(true);
            noRender.setEnabled(true);
            client.gameRenderer.showFloatingItem(new net.minecraft.item.ItemStack(net.minecraft.item.Items.TOTEM_OF_UNDYING));
        });
        settle(context);
        context.takeScreenshot("maro-no-render-on");
        boolean hidden = context.computeOnClient(client -> {
            var item = new net.minecraft.entity.ItemEntity(client.world, client.player.getX(), client.player.getY(), client.player.getZ(),
                    new net.minecraft.item.ItemStack(net.minecraft.item.Items.STONE));
            var frustum = new net.minecraft.client.render.Frustum(new org.joml.Matrix4f(), new org.joml.Matrix4f());
            return !client.getEntityRenderDispatcher().shouldRender(item, frustum, 0, 0, 0);
        });
        context.runOnClient(client -> {
            noRender.setEnabled(false);
            for (var s : noRender.getSettings()) s.reset();
        });
        singleplayer.getServer().runCommand("weather clear");
        if (!hidden) throw new AssertionError("No Render did not hide dropped items");
    }

    /** Item Inspect plays through with a sword in hand, swoosh and all, and puts itself away when done. */
    private static void checkItemInspect(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        singleplayer.getServer().runCommand("clear @a");
        singleplayer.getServer().runCommand("give @a minecraft:netherite_sword");
        context.waitTicks(5);
        var inspect = ModuleManager.get(dev.maro.module.impl.visuals.ItemInspect.class);
        context.runOnClient(client -> {
            client.player.getInventory().setSelectedSlot(0);
            client.player.setPitch(10f);
            inspect.setEnabled(true);
        });
        context.waitTicks(3);
        context.runOnClient(client -> inspect.inspect());
        long until = System.currentTimeMillis() + 700;
        while (System.currentTimeMillis() < until) context.waitTick();
        boolean midway = context.computeOnClient(client -> inspect.inspecting());
        context.takeScreenshot("maro-item-inspect");
        until = System.currentTimeMillis() + 2500;
        while (System.currentTimeMillis() < until) context.waitTick();
        boolean done = !context.computeOnClient(client -> inspect.inspecting());
        context.runOnClient(client -> inspect.setEnabled(false));
        if (!midway) throw new AssertionError("Item Inspect was not playing halfway through");
        if (!done) throw new AssertionError("Item Inspect never finished");
    }

    /** View Model: a preset applied, a spin swing and the off hand shown; drawn without trouble. */
    private static void checkViewModel(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        singleplayer.getServer().runCommand("item replace entity @a weapon.offhand with minecraft:shield");
        context.waitTicks(3);
        context.takeScreenshot("maro-view-model-off");
        var viewModel = ModuleManager.get(dev.maro.module.impl.visuals.ViewModel.class);
        context.runOnClient(client -> {
            for (var s : viewModel.getSettings()) {
                if (s.getName().equals("Preset")) ((dev.maro.setting.ModeSetting) s).set("Centered");
                if (s.getName().equals("Swing Mode")) ((dev.maro.setting.ModeSetting) s).set("Spin");
            }
            for (var s : viewModel.getSettings()) if (s instanceof dev.maro.setting.ButtonSetting b) b.press();
            viewModel.setEnabled(true);
        });
        context.waitTicks(5);
        context.takeScreenshot("maro-view-model-on");
        context.runOnClient(client -> client.player.swingHand(net.minecraft.util.Hand.MAIN_HAND));
        context.waitTicks(2);
        context.takeScreenshot("maro-view-model-swing");
        double x = context.computeOnClient(client -> viewModel.getSettings().stream()
                .filter(s -> s.getName().equals("Main X")).map(s -> ((dev.maro.setting.NumberSetting) s).get()).findFirst().orElse(0.0));
        context.runOnClient(client -> {
            viewModel.setEnabled(false);
            for (var s : viewModel.getSettings()) s.reset();
        });
        if (Math.abs(x + 0.25) > 1e-6) throw new AssertionError("Apply Preset did not set Main X (got " + x + ")");
    }

    /** Block Disconnect: nothing found on an empty flat world, then a beacon a few chunks away is found. */
    private static void checkBlockDisconnect(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        var finder = ModuleManager.get(dev.maro.module.impl.misc.BlockDisconnect.class);
        context.runOnClient(client -> {
            for (var s : finder.getSettings()) if (s.getName().equals("Action")) ((dev.maro.setting.ModeSetting) s).set("Stop");
        });
        boolean before = context.computeOnClient(client -> finder.scanAroundNow());
        singleplayer.getServer().runCommand("execute as @a at @s run setblock ~20 ~ ~35 minecraft:beacon");
        settle(context);
        boolean after = context.computeOnClient(client -> finder.scanAroundNow());
        String found = context.computeOnClient(client -> finder.lastFound());
        context.runOnClient(client -> {
            finder.setEnabled(true);
            finder.setEnabled(false);
            for (var s : finder.getSettings()) s.reset();
        });
        if (before) throw new AssertionError("Block Disconnect found something on an empty world: " + found);
        if (!after || !found.contains("Beacon")) throw new AssertionError("Block Disconnect did not find the beacon (" + found + ")");
    }

    /** Compass: a home set behind you, the strip and then the dial. */
    private static void checkCompass(ClientGameTestContext context) {
        var compass = ModuleManager.get(dev.maro.module.impl.visuals.Compass.class);
        context.runOnClient(client -> {
            compass.setEnabled(true);
            for (var s : compass.getSettings()) if (s instanceof dev.maro.setting.ButtonSetting b && s.getName().equals("Set Home Here")) b.press();
            client.player.setYaw(client.player.getYaw() + 140f);
        });
        context.waitTicks(10);
        context.takeScreenshot("maro-compass-strip");
        context.runOnClient(client -> {
            for (var s : compass.getSettings()) if (s.getName().equals("Style")) ((dev.maro.setting.ModeSetting) s).set("Dial");
        });
        context.waitTicks(5);
        context.takeScreenshot("maro-compass-dial");
        context.runOnClient(client -> {
            compass.setEnabled(false);
            for (var s : compass.getSettings()) s.reset();
        });
    }

    /** Average brightness of a screenshot, 0 to 255. */
    private static double luminance(java.nio.file.Path path) {
        try {
            var image = javax.imageio.ImageIO.read(path.toFile());
            long total = 0;
            int samples = 0;
            // The middle half of the screen: the world, without the HUD round the edges.
            for (int y = image.getHeight() / 4; y < image.getHeight() * 3 / 4; y += 4) {
                for (int x = image.getWidth() / 4; x < image.getWidth() * 3 / 4; x += 4) {
                    int rgb = image.getRGB(x, y);
                    total += (rgb >> 16 & 255) * 299 + (rgb >> 8 & 255) * 587 + (rgb & 255) * 114;
                    samples++;
                }
            }
            return total / 1000.0 / samples;
        } catch (java.io.IOException e) {
            throw new AssertionError("Could not read screenshot " + path, e);
        }
    }

    /** The Inventory HUD with armour, rare and enchanted items and a tool about to break, then its tooltip. */
    private static void checkInventoryHud(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        for (String command : java.util.List.of(
                "clear @a",
                "item replace entity @a armor.head with minecraft:netherite_helmet",
                "item replace entity @a armor.chest with minecraft:diamond_chestplate[enchantments={protection:4}]",
                "item replace entity @a armor.legs with minecraft:iron_leggings",
                "item replace entity @a armor.feet with minecraft:golden_boots",
                "item replace entity @a weapon.offhand with minecraft:totem_of_undying",
                "give @a minecraft:diamond_sword[enchantments={sharpness:5}]",
                "give @a minecraft:diamond_pickaxe[damage=1500]",
                "give @a minecraft:golden_apple 12",
                "give @a minecraft:enchanted_golden_apple 3",
                "give @a minecraft:ender_pearl 16",
                "give @a minecraft:cobblestone 64",
                "give @a minecraft:oak_log 37",
                "give @a minecraft:elytra",
                "give @a minecraft:experience_bottle 64",
                "give @a minecraft:bow",
                "give @a minecraft:arrow 48",
                "give @a minecraft:torch 50",
                "give @a minecraft:water_bucket",
                "give @a minecraft:cooked_beef 24",
                "give @a minecraft:obsidian 20")) {
            singleplayer.getServer().runCommand(command);
        }
        context.waitTicks(10);
        var hud = ModuleManager.get(dev.maro.module.impl.visuals.InventoryHud.class);
        context.runOnClient(client -> {
            client.player.getInventory().setSelectedSlot(1);
            hud.setEnabled(true);
        });
        settle(context);
        context.takeScreenshot("maro-inventory-hud");

        int[] cursor = context.computeOnClient(client -> {
            double gui = client.getWindow().getScaleFactor();
            // the first storage slot: below the header (18) and the equipment row (23)
            return new int[]{(int) ((hud.hudLeft() + (6 + 9) * hud.hudScale()) * gui),
                    (int) ((hud.hudTop() + (6 + 18 + 23 + 9) * hud.hudScale()) * gui)};
        });
        context.setScreen(() -> new dev.maro.gui.hud.HudPlacementScreen(null, hud));
        context.getInput().setCursorPos(cursor[0], cursor[1]);
        context.waitTicks(5);
        context.takeScreenshot("maro-inventory-hud-placement");
        context.setScreen(() -> null);
        context.runOnClient(client -> hud.setEnabled(false));
    }

    /** The Region Map on its own, large enough to judge, then with the placement screen's hover card. */
    private static void checkRegionMap(ClientGameTestContext context) {
        var map = ModuleManager.get(dev.maro.nathan.modules.RegionMap.class);
        var scale = map.getSettings().stream().filter(s -> s.getName().equals("scale")).findFirst().orElseThrow();
        context.runOnClient(client -> {
            scale.fromJson(new com.google.gson.JsonPrimitive(1.25));
            map.setEnabled(true);
        });
        context.waitTicks(10);
        context.takeScreenshot("maro-region-map");
        context.setScreen(() -> new dev.maro.nathan.gui.RegionMapScreen(null, map));
        context.getInput().setCursorPos(230, 200);
        context.waitTicks(5);
        context.takeScreenshot("maro-region-map-hover");
        context.setScreen(() -> null);
        context.runOnClient(client -> {
            for (var s : map.getSettings()) {
                if (s.getName().equals("warmth")) s.fromJson(new com.google.gson.JsonPrimitive(45));
                if (s.getName().equals("contrast")) s.fromJson(new com.google.gson.JsonPrimitive(1.25));
                if (s.getName().equals("tile-depth")) s.fromJson(new com.google.gson.JsonPrimitive(55));
            }
        });
        context.waitTicks(5);
        context.takeScreenshot("maro-region-map-corrected");
        context.runOnClient(client -> map.getSettings().stream()
            .filter(s -> java.util.List.of("warmth", "contrast", "tile-depth").contains(s.getName()))
            .forEach(s -> s.fromJson(s.getName().equals("contrast") ? new com.google.gson.JsonPrimitive(1.0)
                : new com.google.gson.JsonPrimitive(s.getName().equals("tile-depth") ? 72 : 0))));
        context.runOnClient(client -> {
            map.setEnabled(false);
            scale.fromJson(new com.google.gson.JsonPrimitive(1.0));
        });
    }

    /** Checks the actual client arm clock, including the mixin and native settings bridge. */
    private static void checkSlowSwing(ClientGameTestContext context) {
        var module = ModuleManager.get(dev.maro.nathan.modules.SwingSpeed.class);
        var speed = module.getSettings().stream().filter(s -> s.getName().equals("swing speed")).findFirst().orElseThrow();
        var hand = module.getSettings().stream().filter(s -> s.getName().equals("hand")).findFirst().orElseThrow();
        context.runOnClient(client -> module.setEnabled(false));
        int normal = swingTicks(context, net.minecraft.util.Hand.MAIN_HAND);
        int[] levels = {-1, -5, -10, 0, 1, 10};
        for (int level : levels) {
            context.runOnClient(client -> {
                speed.fromJson(new com.google.gson.JsonPrimitive(level));
                speed.fromJson(speed.toJson());
                if (speed.toJson().getAsInt() != level) throw new AssertionError("Swing strength did not round-trip: " + level);
                module.setEnabled(true);
            });
            int duration = swingTicks(context, net.minecraft.util.Hand.MAIN_HAND);
            if ((level < 0 && duration <= normal) || (level == 10 && duration >= normal)
                || ((level == 0 || level == 1) && duration != normal))
                throw new AssertionError("Swing strength " + level + ": " + duration + " ticks; normal=" + normal);
        }
        context.runOnClient(client -> {
            speed.fromJson(new com.google.gson.JsonPrimitive(-10));
            hand.fromJson(new com.google.gson.JsonPrimitive("MainHand"));
        });
        if (swingTicks(context, net.minecraft.util.Hand.OFF_HAND) != normal)
            throw new AssertionError("Main-hand slowdown changed the offhand animation");
        // Changing from a long animation to a short one must finish the current swing cleanly.
        context.runOnClient(client -> client.player.swingHand(net.minecraft.util.Hand.MAIN_HAND));
        context.waitTicks(normal + 2);
        context.runOnClient(client -> speed.fromJson(new com.google.gson.JsonPrimitive(10)));
        context.waitTicks(2);
        context.runOnClient(client -> {
            if (client.player.handSwinging || client.player.handSwingProgress != 0)
                throw new AssertionError("Changing swing strength left a stuck arm");
            module.setEnabled(false);
        });
        if (swingTicks(context, net.minecraft.util.Hand.MAIN_HAND) != normal)
            throw new AssertionError("Disabling Swing Speed did not restore normal animation");
        context.runOnClient(client -> {
            speed.reset(); hand.reset();
        });
    }

    private static int swingTicks(ClientGameTestContext context, net.minecraft.util.Hand arm) {
        context.runOnClient(client -> {
            client.player.handSwinging = false;
            client.player.handSwingTicks = 0;
            client.player.swingHand(arm);
        });
        int ticks = 0;
        while (context.computeOnClient(client -> client.player.handSwinging) && ticks < 80) {
            context.waitTick(); ticks++;
        }
        if (ticks == 80) throw new AssertionError("Swing animation never finished");
        return ticks;
    }

    /** Switch through every bundled preset using the same setting exposed by the menu. */
    private static void checkKeySounds(ClientGameTestContext context) {
        var module = ModuleManager.get(dev.maro.nathan.modules.KeySounds.class);
        var pack = (dev.maro.setting.ModeSetting) module.getSettings().stream().filter(s -> s.getName().equals("sound pack")).findFirst().orElseThrow();
        var mouse = module.getSettings().stream().filter(s -> s.getName().equals("mouse click sounds")).findFirst().orElseThrow();
        if (pack.getModes().size() != 15) throw new AssertionError("Expected 14 built-in packs plus EG Oreo");
        context.runOnClient(client -> {
            module.setEnabled(true);
            mouse.fromJson(new com.google.gson.JsonPrimitive(true));
        });
        for (var preset : dev.maro.nathan.modules.KeySounds.Preset.values()) {
            if (preset == dev.maro.nathan.modules.KeySounds.Preset.EgOreo) continue;
            context.runOnClient(client -> pack.set(preset.name()));
            context.getInput().pressKey(GLFW.GLFW_KEY_P);
            context.getInput().pressKey(GLFW.GLFW_KEY_SPACE);
            context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
            context.runOnClient(client -> assertSoundBuffers(module, preset, true));
        }
        // Switching while a preview is queued must cancel it and load only the new pack.
        context.setScreen(ClickGuiScreen::new);
        context.runOnClient(client -> {
            ((ClickGuiScreen) client.currentScreen).openModuleSettings(module);
            module.getSettings().stream().filter(s -> s.getName().equals("Preview Sound")).map(s -> (dev.maro.setting.ActionSetting)s).findFirst().orElseThrow().run();
            pack.set("CreamyDeep"); pack.set("Marble");
        });
        long end = System.currentTimeMillis() + 1200;
        while (System.currentTimeMillis() < end) context.waitTick();
        context.runOnClient(client -> assertSoundBuffers(module, dev.maro.nathan.modules.KeySounds.Preset.Marble, false));
        context.takeScreenshot("maro-key-sounds-presets");
        context.runOnClient(client -> {
            module.setEnabled(false);
            pack.reset(); mouse.reset();
        });
        context.setScreen(() -> null);
    }

    @SuppressWarnings("unchecked")
    private static void assertSoundBuffers(dev.maro.nathan.modules.KeySounds module,
                                          dev.maro.nathan.modules.KeySounds.Preset preset, boolean mouse) {
        try {
            var playerField = module.getClass().getDeclaredField("player"); playerField.setAccessible(true);
            Object player = playerField.get(module);
            var buffersField = player.getClass().getDeclaredField("buffers"); buffersField.setAccessible(true);
            var buffers = (java.util.Map<String, Integer>)buffersField.get(player);
            var folderField = preset.getClass().getDeclaredField("folder"); folderField.setAccessible(true);
            String prefix = "/assets/nameeprotect/keysounds/" + folderField.get(preset) + "/";
            if (buffers.size() < (mouse ? 3 : 2) || !buffers.containsKey(prefix + "space.wav")
                || (mouse && !buffers.containsKey(prefix + "mouse.wav")))
                throw new AssertionError("No complete playback buffers for " + preset + ": " + buffers);
            for (var entry : buffers.entrySet()) {
                if (!entry.getKey().startsWith(prefix) || entry.getValue() == 0
                    || !org.lwjgl.openal.AL10.alIsBuffer(entry.getValue())
                    || org.lwjgl.openal.AL10.alGetBufferi(entry.getValue(), org.lwjgl.openal.AL10.AL_FREQUENCY) != 44100)
                    throw new AssertionError("Wrong or invalid live sample after switching to " + preset + ": " + entry);
            }
            var madeField = player.getClass().getDeclaredField("made"); madeField.setAccessible(true);
            if (madeField.getInt(player) <= 0) throw new AssertionError("No OpenAL playback source for " + preset);
            var sourcesField = player.getClass().getDeclaredField("sources"); sourcesField.setAccessible(true);
            int[] sources = (int[])sourcesField.get(player);
            boolean bound = false;
            for (int i = 0; i < madeField.getInt(player); i++)
                bound |= buffers.containsValue(org.lwjgl.openal.AL10.alGetSourcei(sources[i], org.lwjgl.openal.AL10.AL_BUFFER));
            if (!bound || org.lwjgl.openal.AL10.alGetError() != org.lwjgl.openal.AL10.AL_NO_ERROR)
                throw new AssertionError("Selected sample was not bound to a valid source for " + preset);
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }

    /** Lets time-based animations finish (the game only advances inside wait calls). */
    private static void settle(ClientGameTestContext context) {
        long end = System.currentTimeMillis() + 900;
        while (System.currentTimeMillis() < end) context.waitTick();
    }
}
