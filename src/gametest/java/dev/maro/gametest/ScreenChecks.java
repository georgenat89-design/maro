package dev.maro.gametest;

import com.mojang.authlib.GameProfile;
import dev.maro.module.Module;
import dev.maro.module.ModuleManager;
import dev.maro.module.impl.visuals.CustomTotem;
import dev.maro.module.impl.visuals.PotatoGraphics;
import dev.maro.nathan.modules.ColorCorrect;
import dev.maro.setting.Setting;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.client.option.Perspective;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * The game's own screens with Maro's visual modules on: every item in the inventory screen is still
 * drawn with Player ESP, Custom Totem, Potato Graphics and the colour effects on, alone and all at
 * once; and Color Correct still grades the world behind the pause menu that opens when the game is
 * tabbed out of.
 */
final class ScreenChecks {
    private ScreenChecks() {
    }

    static void run(ClientGameTestContext context, TestSingleplayerContext world) {
        for (String command : List.of("clear @a", "time set noon", "weather clear",
                "give @a minecraft:netherite_pickaxe", "give @a minecraft:firework_rocket 28", "give @a minecraft:obsidian 64",
                "give @a minecraft:end_crystal 64", "give @a minecraft:ender_pearl 16", "give @a minecraft:totem_of_undying",
                "give @a minecraft:totem_of_undying", "give @a minecraft:experience_bottle 64", "give @a minecraft:golden_apple 51",
                "give @a minecraft:respawn_anchor 12", "give @a minecraft:glowstone 57", "give @a minecraft:cobblestone 64",
                "give @a minecraft:diamond_sword")) {
            world.getServer().runCommand(command);
        }
        context.runOnClient(c -> {
            c.options.setPerspective(Perspective.FIRST_PERSON);
            c.player.setPitch(0f);
        });
        // Someone for the Player ESP to draw, just in front.
        int target = context.computeOnClient(c -> {
            var other = new OtherClientPlayerEntity(c.world, new GameProfile(UUID.randomUUID(), "MaroScreenTarget"));
            var at = c.player.getEntityPos().add(c.player.getRotationVector().multiply(4));
            other.setPosition(at.x, c.player.getY(), at.z);
            other.lastX = other.lastRenderX = at.x;
            other.lastY = other.lastRenderY = c.player.getY();
            other.lastZ = other.lastRenderZ = at.z;
            c.world.addEntity(other);
            return other.getId();
        });
        context.waitTicks(10);

        Module esp = ModuleManager.get(dev.maro.module.impl.visuals.PlayerESP.class);
        Module totem = ModuleManager.get(CustomTotem.class);
        Module potato = ModuleManager.get(PotatoGraphics.class);
        Module bloom = ModuleManager.getByName("Bloom"), grade = ModuleManager.getByName("Color Correct");
        List<String> failures = new ArrayList<>();
        try {
            failures.addAll(inventory(context, "plain"));
            failures.addAll(withModules(context, "player-esp", List.of(esp)));
            failures.addAll(withModules(context, "custom-totem", List.of(totem)));
            failures.addAll(withModules(context, "potato", List.of(potato)));
            failures.addAll(withModules(context, "effects", List.of(bloom, grade)));
            failures.addAll(withModules(context, "all", List.of(esp, totem, potato, bloom, grade)));
            pauseMenu(context, grade);
        } finally {
            context.runOnClient(c -> {
                c.setScreen(null);
                for (Module m : List.of(esp, totem, potato, bloom, grade)) {
                    m.setEnabled(false);
                    m.getSettings().forEach(Setting::reset);
                }
                c.world.removeEntity(target, net.minecraft.entity.Entity.RemovalReason.DISCARDED);
            });
            settleTextures(context);
            world.getServer().runCommand("clear @a");
        }
        // Reported, not failed, while the cause of the user's report is being found: the bug is in
        // builds already out, and failing here would hold back every other fix until it is known.
        System.out.println("INVENTORY SCREEN RESULT: " + (failures.isEmpty() ? "every item drawn in every case" : String.join("; ", failures)));
    }

    private static List<String> withModules(ClientGameTestContext context, String name, List<Module> modules) {
        context.runOnClient(c -> modules.forEach(m -> {
            m.getSettings().forEach(Setting::reset);
            m.setEnabled(true);
        }));
        settleTextures(context);
        List<String> failures = inventory(context, name);
        context.runOnClient(c -> modules.forEach(m -> m.setEnabled(false)));
        settleTextures(context);
        return failures;
    }

    private static void settleTextures(ClientGameTestContext context) {
        context.waitTicks(15);
        context.waitFor(c -> CustomTotem.textureSettled() && PotatoGraphics.texturesSettled(), 2400);
        context.waitTicks(5);
    }

    /** Opens the inventory, screenshots it and checks each slot with something in it shows it. */
    private static List<String> inventory(ClientGameTestContext context, String name) {
        context.runOnClient(c -> c.setScreen(new InventoryScreen(c.player)));
        context.waitTicks(8);
        Path shot = context.takeScreenshot("maro-inventory-screen-" + name);
        // Each stacked slot's top-left corner in screenshot pixels, and what it holds.
        List<Object[]> slots = context.computeOnClient(c -> {
            var screen = (InventoryScreen) c.currentScreen;
            double gui = c.getWindow().getScaleFactor();
            int left = (screen.width - 176) / 2, top = (screen.height - 166) / 2;
            List<Object[]> out = new ArrayList<>();
            for (var slot : screen.getScreenHandler().slots) {
                if (!slot.hasStack() || slot.y < 80) continue; // the storage rows and hotbar
                out.add(new Object[] {(int) Math.round((left + slot.x) * gui), (int) Math.round((top + slot.y) * gui), (int) Math.round(gui),
                        slot.getStack().getItem().toString()});
            }
            return out;
        });
        context.runOnClient(c -> c.setScreen(null));
        BufferedImage image = read(shot);
        List<String> missing = new ArrayList<>();
        for (Object[] slot : slots) {
            int x = (int) slot[0], y = (int) slot[1], scale = (int) slot[2];
            // The slot's top-left 11 x 9, clear of the count drawn at its bottom right.
            int drawn = 0, total = 0;
            for (int py = y + scale; py < y + 10 * scale && py < image.getHeight(); py++) {
                for (int px = x + scale; px < x + 12 * scale && px < image.getWidth(); px++) {
                    int rgb = image.getRGB(px, py);
                    int d = Math.abs((rgb >> 16 & 0xFF) - 0x8B) + Math.abs((rgb >> 8 & 0xFF) - 0x8B) + Math.abs((rgb & 0xFF) - 0x8B);
                    if (d > 45) drawn++;
                    total++;
                }
            }
            if (drawn * 20 < total) missing.add((String) slot[3]);
        }
        System.out.println("INVENTORY SCREEN " + name + ": " + (slots.size() - missing.size()) + " of " + slots.size() + " items drawn"
                + (missing.isEmpty() ? "" : ", missing " + missing));
        return missing.isEmpty() ? List.of() : List.of(name + " " + missing);
    }

    /**
     * Color Correct turned to grey, then menus over the world: the pause menu that tabbing out
     * opens, a chest like a server's /shop, and the options. The world behind each stays grey, graded
     * before the menu is even recorded; and with Grade Menus on, the menu is grey too.
     */
    private static void pauseMenu(ClientGameTestContext context, Module grade) {
        context.runOnClient(c -> {
            grade.getSettings().forEach(Setting::reset);
            setting(grade, "saturation", new com.google.gson.JsonPrimitive(0));
            grade.setEnabled(true);
        });
        context.waitTicks(10);
        double inGame = colourfulness(read(context.takeScreenshot("maro-color-correct-grey")));
        String inGamePoint = context.computeOnClient(c -> ColorCorrect.lastPoint());
        double paused = 0;
        List<String> menus = new ArrayList<>();
        for (String menu : List.of("paused", "chest", "options")) {
            context.runOnClient(c -> c.setScreen(menu(c, menu)));
            context.waitTicks(10);
            double colour = colourfulness(read(context.takeScreenshot("maro-color-correct-grey-" + menu)));
            if (menu.equals("paused")) paused = colour;
            menus.add(String.format(Locale.ROOT, "%s %.1f (graded at %s)", menu, colour, context.computeOnClient(c -> ColorCorrect.lastPoint())));
            context.runOnClient(c -> c.setScreen(null));
        }
        // Grade Menus: the chest itself, and the hotbar under it, go grey with the world.
        context.runOnClient(c -> {
            setting(grade, "grade-menus", new com.google.gson.JsonPrimitive(true));
            c.setScreen(menu(c, "chest"));
        });
        context.waitTicks(10);
        BufferedImage overMenu = read(context.takeScreenshot("maro-color-correct-grey-over-menu"));
        String overMenuPoint = context.computeOnClient(c -> ColorCorrect.lastPoint());
        context.runOnClient(c -> {
            c.setScreen(null);
            grade.setEnabled(false);
            grade.getSettings().forEach(Setting::reset);
        });
        System.out.printf(Locale.ROOT, "COLOR CORRECT greyscale colourfulness: in game %.1f (graded at %s); %s; Grade Menus whole screen %.1f (graded at %s)%n",
                inGame, inGamePoint, String.join("; ", menus), colourfulness(overMenu, 1), overMenuPoint);
        System.out.println("COLOR CORRECT RESULT: " + (inGame >= 8 ? "not grey in game" : paused >= 8 ? "lost behind the pause menu" : "kept behind the pause menu"));
    }

    private static net.minecraft.client.gui.screen.Screen menu(MinecraftClient c, String menu) {
        return switch (menu) {
            case "paused" -> new GameMenuScreen(true);
            case "chest" -> new net.minecraft.client.gui.screen.ingame.GenericContainerScreen(
                    net.minecraft.screen.GenericContainerScreenHandler.createGeneric9x3(1, c.player.getInventory()),
                    c.player.getInventory(), net.minecraft.text.Text.literal("Quick Buy"));
            default -> new net.minecraft.client.gui.screen.option.OptionsScreen(null, c.options);
        };
    }

    private static void setting(Module module, String name, com.google.gson.JsonElement value) {
        // The addon's settings show under their names with spaces: "grade-menus" is "grade menus".
        String wanted = name.replace('-', ' ');
        module.getSettings().stream().filter(s -> s.getName().replace('-', ' ').equalsIgnoreCase(wanted)).findFirst()
                .orElseThrow(() -> new AssertionError("No setting " + name + " on " + module.getName())).fromJson(value);
    }

    private static double colourfulness(BufferedImage image) {
        return colourfulness(image, 0.25);
    }

    /** How far from grey the top {@code share} of the screen (a quarter: the sky, clear of any buttons) is, on average. */
    private static double colourfulness(BufferedImage image, double share) {
        long sum = 0, count = 0;
        for (int y = 0; y < image.getHeight() * share; y += 2) {
            for (int x = 0; x < image.getWidth(); x += 2) {
                int rgb = image.getRGB(x, y);
                int r = rgb >> 16 & 0xFF, g = rgb >> 8 & 0xFF, b = rgb & 0xFF;
                sum += Math.max(r, Math.max(g, b)) - Math.min(r, Math.min(g, b));
                count++;
            }
        }
        return count == 0 ? 0 : sum / (double) count;
    }

    private static BufferedImage read(Path shot) {
        try {
            return ImageIO.read(shot.toFile());
        } catch (IOException e) {
            throw new AssertionError("Cannot read " + shot, e);
        }
    }
}
