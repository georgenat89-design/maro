package dev.maro.gametest;

import dev.maro.gui.hud.ItemPickerScreen;
import dev.maro.module.ModuleManager;
import dev.maro.module.impl.visuals.FakeBlock;
import dev.maro.setting.Setting;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.block.Blocks;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

/**
 * Fake Block: gold blocks drawn as diamond blocks, in the world and as items, and a golden sword
 * drawn as a netherite one with the enchanted shine; the picker finds items by name.
 */
final class FakeBlockChecks {
    private FakeBlockChecks() {
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    static void run(ClientGameTestContext context, TestSingleplayerContext world) {
        FakeBlock module = ModuleManager.get(FakeBlock.class);
        require(module != null, "Fake Block was not registered");
        var position = context.computeOnClient(c -> c.player.getEntityPos());
        float[] angles = context.computeOnClient(c -> new float[] {c.player.getYaw(), c.player.getPitch()});
        try {
            context.runOnClient(c -> {
                var picker = new ItemPickerScreen(null, "Source", module::setSource);
                c.setScreen(picker);
                require(picker.search("gold block").contains(Items.GOLD_BLOCK), "The picker did not find gold blocks");
            });
            context.waitTicks(3);
            context.takeScreenshot("maro-fake-block-picker");
            context.runOnClient(c -> c.setScreen(null));

            world.getServer().runCommand("fill 2 -58 4 4 -56 4 minecraft:gold_block");
            world.getServer().runCommand("tp @a 3 -57 0 0 10");
            context.waitTicks(10);
            context.takeScreenshot("maro-fake-block-before");
            context.runOnClient(c -> {
                module.setSource(Items.GOLD_BLOCK);
                module.setReplace(Items.DIAMOND_BLOCK);
                module.setEnabled(true);
            });
            context.waitTicks(20);
            context.takeScreenshot("maro-fake-block-world");
            context.runOnClient(c -> {
                require(FakeBlock.worldLook(Blocks.GOLD_BLOCK.getDefaultState()).isOf(Blocks.DIAMOND_BLOCK), "Gold blocks are not drawn as diamond");
                ItemStack shown = FakeBlock.itemLook(new ItemStack(Items.GOLD_BLOCK));
                require(shown != null && Items.DIAMOND_BLOCK.getComponents().get(DataComponentTypes.ITEM_MODEL).equals(shown.get(DataComponentTypes.ITEM_MODEL)),
                        "A gold block item is not drawn as diamond");
                // Use Its Name: called a Block of Diamond; a stack named by hand keeps its name; off, its own.
                String diamond = Items.DIAMOND_BLOCK.getName().getString(), gold = Items.GOLD_BLOCK.getName().getString();
                require(new ItemStack(Items.GOLD_BLOCK).getName().getString().equals(diamond),
                        "A gold block is not called " + diamond + ": " + new ItemStack(Items.GOLD_BLOCK).getName().getString());
                ItemStack named = new ItemStack(Items.GOLD_BLOCK);
                named.set(DataComponentTypes.CUSTOM_NAME, net.minecraft.text.Text.literal("Mine"));
                require(named.getName().getString().equals("Mine"), "A named stack lost its name");
                module.getSettings().stream().filter(s -> s.getName().equals("Use Its Name")).findFirst()
                        .ifPresent(s -> ((dev.maro.setting.BooleanSetting) s).set(false));
                require(new ItemStack(Items.GOLD_BLOCK).getName().getString().equals(gold), "With Use Its Name off, gold kept the other's name");
                // Enchanted works for any look, a block as well as gear.
                module.getSettings().stream().filter(s -> s.getName().equals("Enchanted")).findFirst()
                        .ifPresent(s -> ((dev.maro.setting.BooleanSetting) s).set(true));
                ItemStack shiny = FakeBlock.itemLook(new ItemStack(Items.GOLD_BLOCK));
                require(shiny != null && Boolean.TRUE.equals(shiny.get(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE)), "A block look did not shine as enchanted");
                module.setSource(Items.GOLDEN_SWORD);
                module.setReplace(Items.NETHERITE_SWORD);
                module.getSettings().stream().filter(s -> s.getName().equals("Enchanted")).findFirst()
                        .ifPresent(s -> ((dev.maro.setting.BooleanSetting) s).set(true));
                ItemStack sword = FakeBlock.itemLook(new ItemStack(Items.GOLDEN_SWORD));
                require(sword != null && Boolean.TRUE.equals(sword.get(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE)),
                        "The netherite look did not shine as enchanted");
            });
        } finally {
            context.runOnClient(c -> {
                module.setEnabled(false);
                module.setSource(null);
                module.setReplace(null);
                module.getSettings().forEach(Setting::reset);
                if (c.currentScreen != null) c.setScreen(null);
            });
            world.getServer().runCommand("fill 2 -58 4 4 -56 4 minecraft:air");
            world.getServer().runCommand("tp @a " + position.x + " " + position.y + " " + position.z + " " + angles[0] + " " + angles[1]);
            context.waitTicks(5);
        }
    }
}
