package dev.maro.gametest;

import dev.maro.gui.hud.EspPreviewScreen;
import dev.maro.module.ModuleManager;
import dev.maro.module.impl.visuals.PlayerESP;
import dev.maro.setting.BooleanSetting;
import dev.maro.setting.ColorSetting;
import dev.maro.setting.ModeSetting;
import dev.maro.setting.NumberSetting;
import dev.maro.setting.Setting;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.client.option.Perspective;
import net.minecraft.entity.Entity;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;

/** Player ESP on yourself in third person: the silhouette must be drawn, and drawn where you are. */
final class PlayerEspChecks {
    private PlayerEspChecks() {
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static Setting<?> setting(PlayerESP esp, String name) {
        return esp.getSettings().stream().filter(s -> s.getName().equals(name)).findFirst().orElseThrow();
    }

    static void run(ClientGameTestContext context, TestSingleplayerContext world) {
        PlayerESP esp = ModuleManager.get(PlayerESP.class);
        require(esp != null, "Player ESP was not registered");
        var original = context.computeOnClient(c -> c.player.getEntityPos());
        var perspective = context.computeOnClient(c -> c.options.getPerspective());
        try {
            world.getServer().runCommand("fill -6 99 -6 6 99 6 minecraft:stone");
            world.getServer().runCommand("fill -6 100 -6 6 104 6 minecraft:air");
            world.getServer().runCommand("time set noon");
            world.getServer().runCommand("tp @a 0 100 0 0 10");
            context.waitTicks(8);
            context.runOnClient(c -> {
                c.options.setPerspective(Perspective.THIRD_PERSON_BACK);
                esp.getSettings().forEach(Setting::reset);
                ((BooleanSetting) setting(esp, "Self")).set(true);
                ((ModeSetting) setting(esp, "Fill Style")).set("Solid");
                ((ColorSetting) setting(esp, "Color")).set(0xFFFF00FF);
                ((NumberSetting) setting(esp, "Fill Opacity")).set(100.0);
                ((NumberSetting) setting(esp, "Edge Fade")).set(0.0);
                ((BooleanSetting) setting(esp, "Glow")).set(false);
                esp.setEnabled(true);
            });
            context.waitTicks(4);
            Path solid = context.takeScreenshot("maro-player-esp-solid");
            context.runOnClient(c -> esp.setEnabled(false));
            context.waitTicks(3);
            Path off = context.takeScreenshot("maro-player-esp-off");

            int[] on = magenta(solid), baseline = magenta(off);
            System.out.println("PLAYER ESP magenta on=" + on[0] + " off=" + baseline[0] + " centre=" + on[1] + "/" + on[3] + " width=" + on[2]);
            require(on[0] > 1500 && on[0] > baseline[0] * 10, "Player ESP did not draw the silhouette: " + on[0] + " vs " + baseline[0]);
            require(Math.abs(on[1] - on[2] / 2) < on[2] * 0.12, "Player ESP silhouette is not where the player is: centre x " + on[1] + " of " + on[2]);

            // The preview: with the module and Self both off, it still shows you, facing the camera.
            context.runOnClient(c -> {
                c.options.setPerspective(Perspective.FIRST_PERSON);
                ((BooleanSetting) setting(esp, "Self")).set(false);
                esp.setEnabled(false);
                c.setScreen(new EspPreviewScreen(null, esp));
            });
            context.waitTicks(4);
            require(context.computeOnClient(c -> c.options.getPerspective() == Perspective.THIRD_PERSON_FRONT && PlayerESP.previewing()),
                    "Preview did not turn the camera to face you");
            int[] preview = magenta(context.takeScreenshot("maro-player-esp-preview"));
            System.out.println("PLAYER ESP preview magenta=" + preview[0] + " centre=" + preview[1] + "/" + preview[3]);
            require(preview[0] > 1500, "Preview did not draw the ESP on you: " + preview[0]);
            context.runOnClient(c -> ((ModeSetting) setting(esp, "Fill Style")).set("Galaxy"));
            context.waitTicks(3);
            context.takeScreenshot("maro-player-esp-preview-galaxy");
            context.runOnClient(c -> c.currentScreen.close());
            context.waitTicks(3);
            require(context.computeOnClient(c -> c.currentScreen == null && c.options.getPerspective() == Perspective.FIRST_PERSON && !PlayerESP.previewing()),
                    "Closing the preview did not put the camera and ESP back");

            context.runOnClient(c -> {
                c.options.setPerspective(Perspective.THIRD_PERSON_BACK);
                esp.getSettings().forEach(Setting::reset);
                ((BooleanSetting) setting(esp, "Self")).set(true);
                esp.setEnabled(true);
            });
            for (String style : new String[] {"Galaxy", "Lava", "Rainbow"}) {
                context.runOnClient(c -> ((ModeSetting) setting(esp, "Fill Style")).set(style));
                context.waitTicks(3);
                context.takeScreenshot("maro-player-esp-" + style.toLowerCase());
            }

            // Another player far away behind a wall, past the entity distance the game draws at
            // (halved here, as Potato Graphics does): the ESP must still show them.
            world.getServer().runCommand("fill -8 99 -8 8 99 46 minecraft:stone");
            world.getServer().runCommand("fill -8 100 -8 8 106 46 minecraft:air");
            world.getServer().runCommand("fill -8 100 4 8 106 4 minecraft:stone");
            world.getServer().runCommand("tp @a 0 100 0 0 0");
            context.waitTicks(10);
            context.runOnClient(c -> {
                c.options.setPerspective(Perspective.FIRST_PERSON);
                c.options.getEntityDistanceScaling().setValue(0.5);
                esp.getSettings().forEach(Setting::reset);
                ((ModeSetting) setting(esp, "Fill Style")).set("Solid");
                ((ColorSetting) setting(esp, "Color")).set(0xFFFF00FF);
                ((NumberSetting) setting(esp, "Fill Opacity")).set(100.0);
                ((NumberSetting) setting(esp, "Edge Fade")).set(0.0);
                ((BooleanSetting) setting(esp, "Glow")).set(false);
                esp.setEnabled(true);
                c.player.setYaw(0);
                c.player.setPitch(0);
            });
            int farId = context.computeOnClient(c -> spawnPlayer(c, 0.5, 100, 40.5));
            context.waitTicks(5);
            Path far = context.takeScreenshot("maro-player-esp-far-behind-wall");
            int[] farPixels = magenta(far);
            System.out.println("PLAYER ESP far magenta=" + farPixels[0] + " centre=" + farPixels[1] + "/" + farPixels[3]);
            require(farPixels[0] > 40, "Player ESP did not draw a far player behind a wall: " + farPixels[0]);
            require(Math.abs(farPixels[1] - farPixels[2] / 2) < farPixels[2] * 0.08, "Far player's silhouette is off target: x " + farPixels[1]);

            // A nearer one with the default look, to see the contour and glow at a middle distance.
            context.runOnClient(c -> {
                c.world.removeEntity(farId, Entity.RemovalReason.DISCARDED);
                c.options.getEntityDistanceScaling().setValue(1.0);
                esp.getSettings().forEach(Setting::reset);
                esp.setEnabled(true);
            });
            int nearId = context.computeOnClient(c -> spawnPlayer(c, 0.5, 100, 14.5));
            context.waitTicks(5);
            context.takeScreenshot("maro-player-esp-through-wall");
            context.runOnClient(c -> c.world.removeEntity(nearId, Entity.RemovalReason.DISCARDED));
        } finally {
            context.runOnClient(c -> {
                esp.setEnabled(false);
                esp.getSettings().forEach(Setting::reset);
                c.options.setPerspective(perspective);
                c.options.getEntityDistanceScaling().setValue(1.0);
            });
            world.getServer().runCommand("tp @a " + original.x + " " + original.y + " " + original.z);
            context.waitTicks(3);
        }
    }

    /** Adds a client-side player facing the camera at the given position; returns its entity id. */
    private static int spawnPlayer(MinecraftClient client, double x, double y, double z) {
        var other = new OtherClientPlayerEntity(client.world, new GameProfile(UUID.randomUUID(), "MaroEspTarget"));
        other.setPosition(x, y, z);
        other.lastX = other.lastRenderX = x;
        other.lastY = other.lastRenderY = y;
        other.lastZ = other.lastRenderZ = z;
        other.setYaw(180f);
        other.setBodyYaw(180f);
        other.setHeadYaw(180f);
        client.world.addEntity(other);
        return other.getId();
    }

    /** {count, centre x, image width, centre y} of strongly magenta pixels. */
    private static int[] magenta(Path screenshot) {
        try {
            var image = ImageIO.read(screenshot.toFile());
            long sumX = 0, sumY = 0;
            int count = 0;
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    int rgb = image.getRGB(x, y);
                    int r = rgb >> 16 & 0xFF, g = rgb >> 8 & 0xFF, b = rgb & 0xFF;
                    if (r > 200 && b > 200 && g < 90) {
                        count++;
                        sumX += x;
                        sumY += y;
                    }
                }
            }
            return new int[] {count, count == 0 ? -1 : (int) (sumX / count), image.getWidth(), count == 0 ? -1 : (int) (sumY / count)};
        } catch (IOException e) {
            throw new AssertionError("Cannot read " + screenshot, e);
        }
    }
}
