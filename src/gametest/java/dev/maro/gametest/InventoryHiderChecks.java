package dev.maro.gametest;

import dev.maro.module.ModuleManager;
import dev.maro.module.impl.misc.InventoryHider;
import dev.maro.setting.BooleanSetting;
import dev.maro.setting.ModeSetting;
import dev.maro.setting.Setting;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Inventory Hider: with it on, your inventory's items are not drawn (the screenshot changes where
 * they were), your own slots in a chest are hidden but the chest's are not unless asked, the whole
 * menu can vanish, and the hotbar and the sword in your hand are hidden too.
 */
final class InventoryHiderChecks {
    private InventoryHiderChecks() {
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static Setting<?> setting(InventoryHider module, String name) {
        return module.getSettings().stream().filter(s -> s.getName().equals(name)).findFirst().orElseThrow();
    }

    private static BufferedImage read(Path shot) {
        try {
            return ImageIO.read(shot.toFile());
        } catch (IOException e) {
            throw new AssertionError("Cannot read " + shot, e);
        }
    }

    /** Pixels clearly different between two screenshots, sampled every other pixel. */
    private static int changed(BufferedImage before, BufferedImage after) {
        int count = 0;
        int width = Math.min(before.getWidth(), after.getWidth()), height = Math.min(before.getHeight(), after.getHeight());
        for (int y = 0; y < height; y += 2) {
            for (int x = 0; x < width; x += 2) {
                int a = before.getRGB(x, y), b = after.getRGB(x, y);
                int d = Math.abs((a >> 16 & 0xFF) - (b >> 16 & 0xFF)) + Math.abs((a >> 8 & 0xFF) - (b >> 8 & 0xFF))
                        + Math.abs((a & 0xFF) - (b & 0xFF));
                if (d > 60) count++;
            }
        }
        return count;
    }

    /** How many slots with an item the open menu hides, and how many it shows, for your slots and the menu's own. */
    private static int[] hiddenCounts(net.minecraft.client.MinecraftClient c) {
        int[] counts = new int[4];
        if (!(c.currentScreen instanceof HandledScreen<?> screen)) return counts;
        for (var slot : screen.getScreenHandler().slots) {
            if (slot.getStack().isEmpty()) continue;
            boolean own = slot.inventory instanceof PlayerInventory;
            boolean hidden = InventoryHider.hides(screen, slot);
            counts[(own ? 0 : 2) + (hidden ? 0 : 1)]++;
        }
        return counts;
    }

    static void run(ClientGameTestContext context, TestSingleplayerContext world) {
        InventoryHider module = ModuleManager.get(InventoryHider.class);
        require(module != null, "Inventory Hider was not registered");
        var perspective = context.computeOnClient(c -> c.options.getPerspective());
        var gameMode = world.getServer().computeOnServer(s -> s.getPlayerManager().getPlayerList().getFirst().interactionManager.getGameMode());
        int selected = context.computeOnClient(c -> c.player.getInventory().getSelectedSlot());
        try {
            world.getServer().runCommand("gamemode survival @a");
            context.waitFor(c -> c.interactionManager.getCurrentGameMode() == net.minecraft.world.GameMode.SURVIVAL);
            world.getServer().runCommand("clear @a");
            world.getServer().runCommand("item replace entity @a hotbar.0 with minecraft:diamond_block 64");
            world.getServer().runCommand("item replace entity @a hotbar.1 with minecraft:netherite_sword");
            world.getServer().runCommand("item replace entity @a inventory.0 with minecraft:netherite_ingot 32");
            world.getServer().runCommand("item replace entity @a inventory.5 with minecraft:enchanted_golden_apple 16");
            world.getServer().runCommand("item replace entity @a inventory.13 with minecraft:totem_of_undying");
            world.getServer().runCommand("item replace entity @a armor.head with minecraft:diamond_helmet");
            context.waitFor(c -> c.player.getInventory().getStack(0).isOf(Items.DIAMOND_BLOCK)
                    && c.player.getInventory().getStack(1).isOf(Items.NETHERITE_SWORD)
                    && c.player.getInventory().getStack(9).isOf(Items.NETHERITE_INGOT)
                    && c.player.getInventory().getStack(14).isOf(Items.ENCHANTED_GOLDEN_APPLE)
                    && c.player.getInventory().getStack(22).isOf(Items.TOTEM_OF_UNDYING)
                    && c.player.getEquippedStack(net.minecraft.entity.EquipmentSlot.HEAD).isOf(Items.DIAMOND_HELMET));
            context.runOnClient(c -> {
                module.getSettings().forEach(Setting::reset);
                ((BooleanSetting) setting(module, "Player Model")).set(false);
                c.setScreen(new InventoryScreen(c.player));
            });
            context.waitTicks(4);
            BufferedImage shown = read(context.takeScreenshot("maro-inventory-hider-off"));
            context.runOnClient(c -> module.setEnabled(true));
            context.waitTicks(2);
            BufferedImage hidden = read(context.takeScreenshot("maro-inventory-hider"));
            int diff = changed(shown, hidden);
            int[] counts = context.computeOnClient(InventoryHiderChecks::hiddenCounts);
            System.out.println("INVENTORY HIDER changed=" + diff + " own hidden=" + counts[0] + " own shown=" + counts[1]);
            require(counts[0] >= 6 && counts[1] == 0, "Not every item in your inventory is hidden: " + counts[0] + " hidden, " + counts[1] + " shown");
            require(diff > 150, "Hiding the inventory changed almost nothing on screen: " + diff + " pixels");

            // The figure of you goes too, and the peek key shows everything.
            context.runOnClient(c -> ((BooleanSetting) setting(module, "Player Model")).set(true));
            context.waitTicks(2);
            context.takeScreenshot("maro-inventory-hider-model");
            context.getInput().holdKey(GLFW.GLFW_KEY_LEFT_ALT);
            context.waitTicks(2);
            System.out.println("INVENTORY HIDER peeking=" + context.computeOnClient(c -> module.peeking()));
            context.getInput().releaseKey(GLFW.GLFW_KEY_LEFT_ALT);
            context.waitTicks(2);

            // Blacked Out covers each slot instead.
            context.runOnClient(c -> ((ModeSetting) setting(module, "Style")).set("Blacked Out"));
            context.waitTicks(2);
            context.takeScreenshot("maro-inventory-hider-covered");

            // Invisible Menu: the whole inventory is gone while it stays open.
            context.runOnClient(c -> ((ModeSetting) setting(module, "Style")).set("Invisible Menu"));
            context.waitTicks(2);
            require(context.computeOnClient(c -> c.currentScreen instanceof InventoryScreen && InventoryHider.hidesMenu(c.currentScreen)),
                    "Invisible Menu does not hide the inventory");
            context.takeScreenshot("maro-inventory-hider-invisible");
            context.runOnClient(c -> {
                ((ModeSetting) setting(module, "Style")).reset();
                c.player.closeHandledScreen();
            });
            context.waitTicks(2);

            // A chest: your slots hidden, its own shown, until Chest Contents is on.
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerManager().getPlayerList().getFirst();
                SimpleInventory box = new SimpleInventory(27);
                box.setStack(0, new ItemStack(Items.DIAMOND, 64));
                box.setStack(4, new ItemStack(Items.ELYTRA));
                box.setStack(13, new ItemStack(Items.BEACON));
                player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
                        (syncId, inventory, owner) -> GenericContainerScreenHandler.createGeneric9x3(syncId, inventory, box), Text.literal("Loot Chest")));
            });
            context.waitTicks(5);
            int[] chest = context.computeOnClient(InventoryHiderChecks::hiddenCounts);
            require(chest[0] >= 5 && chest[1] == 0, "Your own items show in the chest: " + chest[0] + " hidden, " + chest[1] + " shown");
            require(chest[2] == 0 && chest[3] == 3, "The chest's items should show by default: " + chest[2] + " hidden, " + chest[3] + " shown");
            context.takeScreenshot("maro-inventory-hider-chest");
            context.runOnClient(c -> ((BooleanSetting) setting(module, "Chest Contents")).set(true));
            chest = context.computeOnClient(InventoryHiderChecks::hiddenCounts);
            require(chest[2] == 3 && chest[3] == 0, "Chest Contents did not hide the chest's items");
            context.runOnClient(c -> c.player.closeHandledScreen());
            context.waitTicks(2);

            // The hotbar and what you hold are hidden by default; a config saved before is moved over.
            context.runOnClient(c -> {
                ((BooleanSetting) setting(module, "Hotbar")).set(false);
                var old = module.saveExtra();
                old.remove("hider-revision");
                module.loadExtra(old);
                c.player.getInventory().setSelectedSlot(1);
                c.options.setPerspective(net.minecraft.client.option.Perspective.FIRST_PERSON);
            });
            context.waitTicks(3);
            require(context.computeOnClient(c -> InventoryHider.hidesHotbar()), "Hotbar is not hidden");
            require(context.computeOnClient(c -> InventoryHider.hidesHeldItem()), "The held item is not hidden");
            BufferedImage handHidden = read(context.takeScreenshot("maro-inventory-hider-hotbar"));
            context.runOnClient(c -> module.setEnabled(false));
            context.waitTicks(2);
            BufferedImage handShown = read(context.takeScreenshot("maro-inventory-hider-hotbar-off"));
            int handDiff = changed(handShown, handHidden);
            System.out.println("INVENTORY HIDER hotbar and hand changed=" + handDiff);
            require(handDiff > 150, "Hiding the hotbar and held sword changed almost nothing on screen: " + handDiff + " pixels");
        } finally {
            context.runOnClient(c -> {
                module.setEnabled(false);
                module.getSettings().forEach(Setting::reset);
                if (c.currentScreen != null) c.player.closeHandledScreen();
                c.options.setPerspective(perspective);
                c.player.getInventory().setSelectedSlot(selected);
            });
            world.getServer().runCommand("clear @a");
            world.getServer().runCommand("gamemode " + gameMode.getId() + " @a");
            context.waitTicks(3);
        }
    }
}
