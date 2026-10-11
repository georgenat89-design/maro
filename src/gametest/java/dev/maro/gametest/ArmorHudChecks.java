package dev.maro.gametest;

import dev.maro.module.ModuleManager;
import dev.maro.module.impl.visuals.ArmorHud;
import dev.maro.setting.ModeSetting;
import dev.maro.setting.Setting;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.entity.EquipmentSlot;

/**
 * Armor HUD: worn armour shows with how much is left, a nearly broken helmet reads as low and red,
 * and it can stand as a column.
 */
final class ArmorHudChecks {
    private ArmorHudChecks() {
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    static void run(ClientGameTestContext context, TestSingleplayerContext world) {
        ArmorHud module = ModuleManager.get(ArmorHud.class);
        require(module != null, "Armor HUD was not registered");
        try {
            world.getServer().runCommand("item replace entity @a armor.head with minecraft:diamond_helmet[damage=340]");
            world.getServer().runCommand("item replace entity @a armor.chest with minecraft:netherite_chestplate");
            world.getServer().runCommand("item replace entity @a armor.legs with minecraft:iron_leggings[damage=110]");
            world.getServer().runCommand("item replace entity @a armor.feet with minecraft:diamond_boots");
            world.getServer().runCommand("item replace entity @a weapon.offhand with minecraft:totem_of_undying");
            context.runOnClient(c -> {
                module.getSettings().forEach(Setting::reset);
                module.setEnabled(true);
            });
            context.waitTicks(5);
            context.runOnClient(c -> {
                float helmet = ArmorHud.left(c.player.getEquippedStack(EquipmentSlot.HEAD));
                float legs = ArmorHud.left(c.player.getEquippedStack(EquipmentSlot.LEGS));
                require(helmet > 0 && helmet < 0.15f, "The worn helmet should read nearly broken: " + helmet);
                require(Math.abs(legs - 0.51f) < 0.02f, "The leggings should be about half worn: " + legs);
                require(ArmorHud.left(c.player.getOffHandStack()) < 0, "A totem does not wear out");
                int red = ArmorHud.wearColor(0.05f), green = ArmorHud.wearColor(1f);
                require((red >> 16 & 0xFF) > (red >> 8 & 0xFF) && (green >> 8 & 0xFF) > (green >> 16 & 0xFF), "Wear colours should go green to red");
                require(module.hudWidth() > module.hudHeight(), "A row should be wider than it is tall");
                // Out of the way by default: beside the hotbar, or above the health bars, never over them.
                int sw = c.getWindow().getScaledWidth(), sh = c.getWindow().getScaledHeight();
                boolean beside = module.hudLeft() >= sw / 2f + 91;
                require(beside || module.hudTop() + module.hudHeight() <= sh - 49, "The Armor HUD sits over the health bars: top "
                        + module.hudTop() + " of " + sh);
            });
            context.takeScreenshot("maro-armor-hud");
            context.runOnClient(c -> ((ModeSetting) module.getSettings().stream().filter(s -> s.getName().equals("Layout")).findFirst().orElseThrow()).set("Column"));
            context.waitTicks(2);
            require(context.computeOnClient(c -> module.hudHeight() > module.hudWidth()), "A column should be taller than it is wide");
            context.takeScreenshot("maro-armor-hud-column");
        } finally {
            context.runOnClient(c -> {
                module.setEnabled(false);
                module.getSettings().forEach(Setting::reset);
            });
            for (String slot : new String[] {"armor.head", "armor.chest", "armor.legs", "armor.feet", "weapon.offhand"}) {
                world.getServer().runCommand("item replace entity @a " + slot + " with minecraft:air");
            }
            context.waitTicks(2);
        }
    }
}
