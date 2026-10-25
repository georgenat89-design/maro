package dev.maro.gametest;

import com.google.gson.JsonElement;
import dev.maro.module.Module;
import dev.maro.module.ModuleManager;
import dev.maro.module.impl.movement.CoordsFly;
import dev.maro.nathan.modules.FreeCam;
import dev.maro.runtime.events.meteor.MouseScrollEvent;
import dev.maro.setting.NumberSetting;
import dev.maro.setting.Setting;
import dev.maro.setting.TextSetting;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.option.Perspective;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

/** Native camera/input isolation, frame-rate-independent movement and dimension-aware HUD checks. */
final class MovementCoordinateChecks {
    private static final Method MOVE = method("move", double.class, double.class, double.class, double.class, boolean.class);
    private static final Method SCROLL = method("onMouseScroll", MouseScrollEvent.class);

    private MovementCoordinateChecks() { }

    private static Method method(String name, Class<?>... arguments) {
        try {
            Method method = FreeCam.class.getDeclaredMethod(name, arguments);
            method.setAccessible(true);
            return method;
        } catch (ReflectiveOperationException error) {
            throw new AssertionError(error);
        }
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static Setting<?> setting(Module module, String name) {
        return module.getSettings().stream().filter(s -> s.getName().equals(name)).findFirst().orElseThrow();
    }

    private static void number(Module module, String name, double value) {
        ((NumberSetting) setting(module, name)).set(value);
    }

    private static double field(FreeCam module, String name) {
        try {
            var field = FreeCam.class.getDeclaredField(name);
            field.setAccessible(true);
            return field.getDouble(module);
        } catch (ReflectiveOperationException error) {
            throw new AssertionError(error);
        }
    }

    private static Vec3d position(FreeCam module) {
        return new Vec3d(field(module, "x"), field(module, "y"), field(module, "z"));
    }

    private static Vec3d travel(FreeCam module, int frames, double forward, double across, double vertical, boolean sprinting) {
        FreeCam.placeCamera(0, 0, 0, 0, 0);
        try {
            for (int i = 0; i < frames; i++) MOVE.invoke(module, 1.0 / frames, forward, across, vertical, sprinting);
        } catch (ReflectiveOperationException error) {
            throw new AssertionError(error);
        }
        return position(module);
    }

    private static void motion(FreeCam module) {
        require(((NumberSetting) setting(module, "speed")).getMax() == 4096, "Freecam usable speed range is still capped");
        require(((NumberSetting) setting(module, "speed")).get() == 96, "Freecam default speed is not substantially faster");
        number(module, "smoothing", 0);
        require(Math.abs(travel(module, 60, 1, 0, 0, false).length() - 96) < 1e-7, "Default speed did not move 96 blocks in a second");
        for (double speed : new double[] {0.5, 96, 800, 4096}) {
            number(module, "speed", speed);
            double straight = travel(module, 60, 1, 0, 0, false).length();
            double diagonal = travel(module, 60, 1, 1, 0, false).length();
            double vertical = travel(module, 60, 0, 0, 1, false).length();
            double combined = travel(module, 60, 1, 1, 1, false).length();
            require(Math.abs(straight - speed) < 1e-7 && Math.abs(vertical - speed) < 1e-7,
                    "Speed setting does not affect both axes: " + speed + ", " + straight + ", " + vertical);
            require(Math.abs(diagonal - straight) < 1e-7 && Math.abs(combined - straight) < 1e-7,
                    "Freecam accelerated diagonally: " + straight + ", " + diagonal + ", " + combined);
        }
        number(module, "speed", 1000);
        require(Math.abs(travel(module, 60, 1, 0, 0, true).length() - 2000) < 1e-7, "Sprint multiplier remains restricted");
        for (double smoothing : new double[] {0.5, 0.88, 0.98, 0.995}) {
            number(module, "smoothing", smoothing);
            Vec3d at20 = travel(module, 20, 1, 1, 1, false);
            Vec3d at60 = travel(module, 60, 1, 1, 1, false);
            Vec3d at240 = travel(module, 240, 1, 1, 1, false);
            require(at20.distanceTo(at60) < 1e-7 && at20.distanceTo(at240) < 1e-7,
                    "Glide displacement changed with frame rate: " + smoothing);
        }
        number(module, "speed", 2500);
        try {
            var event = new MouseScrollEvent(1);
            SCROLL.invoke(module, event);
            require(event.isCancelled() && ((NumberSetting) setting(module, "speed")).get() > 2500,
                    "Mouse wheel still clamps faster speeds to the old cap");
            number(module, "speed", 4096);
            SCROLL.invoke(module, new MouseScrollEvent(100));
            require(((NumberSetting) setting(module, "speed")).get() == 4096, "Mouse wheel exceeds declared range");
        } catch (ReflectiveOperationException error) {
            throw new AssertionError(error);
        }
        number(module, "speed", 16);
        number(module, "smoothing", 0.98);
        var legacy = module.saveExtra();
        legacy.remove("movementVersion");
        module.loadExtra(legacy);
        require(((NumberSetting) setting(module, "speed")).get() == 96
                && Math.abs(((NumberSetting) setting(module, "smoothing")).get() - 0.88) < 1e-8,
                "Existing untouched defaults did not migrate");
        number(module, "speed", 2000);
        number(module, "smoothing", 0.7);
        var custom = module.saveExtra();
        custom.remove("movementVersion");
        module.loadExtra(custom);
        require(((NumberSetting) setting(module, "speed")).get() == 2000
                && Math.abs(((NumberSetting) setting(module, "smoothing")).get() - 0.7) < 1e-8,
                "Migration changed customized movement");
        module.getSettings().forEach(Setting::reset);
    }

    private static void conversions() {
        var overworld = CoordsFly.netherCoordinates(World.OVERWORLD, -17.5, 73.25, 65.5);
        require(overworld != null && overworld.x() == -2.1875 && overworld.y() == 73.25 && overworld.z() == 8.1875,
                "Overworld-to-Nether conversion changed signs, precision or Y: " + overworld);
        var nether = CoordsFly.netherCoordinates(World.NETHER, -17.5, 73.25, 65.5);
        require(nether != null && nether.x() == -17.5 && nether.y() == 73.25 && nether.z() == 65.5,
                "Nether coordinates were divided again: " + nether);
        RegistryKey<World> custom = RegistryKey.of(RegistryKeys.WORLD, Identifier.of("custom", "overworld"));
        require(CoordsFly.netherCoordinates(World.END, 1, 2, 3) == null
                && CoordsFly.netherCoordinates(custom, 1, 2, 3) == null,
                "Unsupported dimension displayed a misleading conversion");
    }

    private static void awaitDimension(ClientGameTestContext context, RegistryKey<World> dimension) {
        for (int i = 0; i < 120; i++) {
            if (context.computeOnClient(c -> c.world != null && dimension.equals(c.world.getRegistryKey()))) return;
            context.waitTick();
        }
        throw new AssertionError("Dimension transfer did not arrive: " + dimension);
    }

    static void run(ClientGameTestContext context, TestSingleplayerContext world) {
        FreeCam camera = ModuleManager.get(FreeCam.class);
        CoordsFly coords = ModuleManager.get(CoordsFly.class);
        require(camera != null && coords != null, "Movement modules are not registered");
        require(ModuleManager.getByName("No Fall") == null && ModuleManager.all().stream().noneMatch(m -> m.getName().equals("No Fall")),
                "No Fall remains in module registration or the UI");
        try {
            Class.forName("dev.maro.module.impl.movement.NoFall", false, MovementCoordinateChecks.class.getClassLoader());
            throw new AssertionError("Removed No Fall class remains packaged");
        } catch (ClassNotFoundException expected) { }
        conversions();
        var savedCamera = context.computeOnClient(c -> camera.saveExtra());
        Map<Setting<?>, JsonElement> savedCoords = new LinkedHashMap<>();
        context.runOnClient(c -> coords.getSettings().forEach(s -> savedCoords.put(s, s.toJson().deepCopy())));
        var savedPosition = context.computeOnClient(c -> c.player.getEntityPos());
        var savedDimension = context.computeOnClient(c -> c.world.getRegistryKey());
        float[] savedLook = context.computeOnClient(c -> new float[] {c.player.getYaw(), c.player.getPitch()});
        Perspective perspective = context.computeOnClient(c -> c.options.getPerspective());
        int scale = context.computeOnClient(c -> c.options.getGuiScale().getValue());
        int viewDistance = context.computeOnClient(c -> c.options.getViewDistance().getValue());
        boolean culling = context.computeOnClient(c -> c.chunkCullingEnabled);
        boolean cameraEnabled = camera.isEnabled(), coordsEnabled = coords.isEnabled();
        boolean flying = context.computeOnClient(c -> c.player.getAbilities().flying);
        var gameMode = world.getServer().computeOnServer(s -> s.getPlayerManager().getPlayerList().getFirst().interactionManager.getGameMode());
        try {
            world.getServer().runCommand("gamemode creative @a");
            world.getServer().runCommand("execute in minecraft:overworld run tp @a -17.5 80 -65.25 0 0");
            awaitDimension(context, World.OVERWORLD);
            context.waitTicks(5);
            context.runOnClient(c -> {
                c.setScreen(null);
                camera.setEnabled(false);
                coords.setEnabled(false);
                c.options.setPerspective(Perspective.FIRST_PERSON);
                c.player.getAbilities().flying = true;
                c.player.setVelocity(Vec3d.ZERO);
                camera.getSettings().forEach(Setting::reset);
                camera.setEnabled(true);
                motion(camera);
                FreeCam.placeCamera(c.player.getX(), c.player.getEyeY(), c.player.getZ(), 0, 0);
            });
            Vec3d anchored = context.computeOnClient(c -> c.player.getEntityPos());
            context.getInput().holdKey(o -> o.forwardKey);
            context.getInput().holdKey(o -> o.jumpKey);
            context.waitTicks(10);
            context.getInput().releaseKey(o -> o.forwardKey);
            context.getInput().releaseKey(o -> o.jumpKey);
            context.runOnClient(c -> {
                require(c.player.getEntityPos().distanceTo(anchored) < 0.05, "Flying the camera moved the real player");
                require(c.gameRenderer.getCamera().getCameraPos().distanceTo(c.player.getEyePos()) > 5,
                        "Native movement keys did not move the faster camera");
                float yaw = c.player.getYaw(), pitch = c.player.getPitch();
                c.player.changeLookDirection(80, -40);
                require(c.player.getYaw() == yaw && c.player.getPitch() == pitch, "Camera look modified the real player");
                camera.setEnabled(false);
                require(c.options.getViewDistance().getValue() == viewDistance && c.chunkCullingEnabled == culling,
                        "Disabling Freecam did not restore drawing options");
            });
            context.waitTicks(3);
            context.runOnClient(c -> {
                require(!c.gameRenderer.getCamera().isThirdPerson(), "Disabling Freecam retained detached perspective");
                require(c.gameRenderer.getCamera().getCameraPos().distanceTo(c.player.getEyePos()) < 0.2,
                        "Disabling Freecam did not return the camera to the player");
                coords.getSettings().forEach(Setting::reset);
                ((TextSetting) setting(coords, "Coords")).set("1000000 80 -2000000");
                coords.setEnabled(true);
                require(coords.playerPositionText(1).equals("Position  X -18  Y 80  Z -66"),
                        "Current position formatting changed negative block coordinates: " + coords.playerPositionText(1));
                require(coords.playerNetherText(1).equals("Nether  X -3  Y 80  Z -9"),
                        "Nether HUD does not describe current player position: " + coords.playerNetherText(1));
            });
            context.getInput().resizeWindow(1280, 720);
            for (int guiScale : new int[] {2, 3, 4, 0}) {
                context.runOnClient(c -> {
                    c.options.getGuiScale().setValue(guiScale);
                    c.onResolutionChanged();
                    number(coords, "Scale", 2.5);
                    require(coords.hudLeft() >= 0 && coords.hudTop() >= 0
                            && coords.hudLeft() + coords.hudWidth() <= c.getWindow().getScaledWidth()
                            && coords.hudTop() + coords.hudHeight() <= c.getWindow().getScaledHeight(),
                            "Expanded coordinate card leaves the viewport at GUI scale " + guiScale);
                });
                context.waitTicks(3);
                context.takeScreenshot("maro-player-nether-coords-scale-" + guiScale);
            }
            context.runOnClient(c -> number(coords, "Scale", 1));
            world.getServer().runCommand("execute in minecraft:the_nether run tp @a -17.5 132 -65.25 0 0");
            awaitDimension(context, World.NETHER);
            context.waitTicks(4);
            context.runOnClient(c -> require(coords.playerNetherText(1).startsWith("Nether  X -18  Y ")
                    && coords.playerNetherText(1).endsWith("  Z -66"), "Nether HUD divided actual Nether position again: " + coords.playerNetherText(1)));
            context.takeScreenshot("maro-player-nether-coords-nether");
            world.getServer().runCommand("execute in minecraft:the_end run tp @a -17.5 90 -65.25 0 0");
            awaitDimension(context, World.END);
            context.waitTicks(3);
            context.runOnClient(c -> require(coords.playerNetherText(1).isEmpty(), "End HUD retained a Nether conversion"));
            System.out.println("[movement-coordinates] PASS: 96 default, speeds through 4096, diagonal/vertical normalization, sprint, 20/60/240 FPS glide equivalence, wheel range, legacy defaults/custom config, real input/player isolation and camera/options restoration; No Fall absent; signed Nether conversion and actual OW/Nether/End transitions; expanded HUD fits scales 2/3/4/Auto");
        } finally {
            context.getInput().releaseKey(o -> o.forwardKey);
            context.getInput().releaseKey(o -> o.jumpKey);
            context.runOnClient(c -> {
                c.setScreen(null);
                camera.setEnabled(false);
                coords.setEnabled(false);
                camera.loadExtra(savedCamera);
                savedCoords.forEach(Setting::fromJson);
                c.options.setPerspective(perspective);
                c.options.getGuiScale().setValue(scale);
                c.onResolutionChanged();
            });
            world.getServer().runCommand("execute in " + savedDimension.getValue() + " run tp @a " + savedPosition.x + " " + savedPosition.y + " " + savedPosition.z + " " + savedLook[0] + " " + savedLook[1]);
            awaitDimension(context, savedDimension);
            world.getServer().runOnServer(s -> {
                var player = s.getPlayerManager().getPlayerList().getFirst();
                player.changeGameMode(gameMode);
                player.getAbilities().flying = flying;
                player.sendAbilitiesUpdate();
            });
            context.runOnClient(c -> {
                c.player.getAbilities().flying = flying;
                c.player.setVelocity(Vec3d.ZERO);
                coords.setEnabled(coordsEnabled);
                camera.setEnabled(cameraEnabled);
            });
            context.waitTicks(3);
        }
    }
}
