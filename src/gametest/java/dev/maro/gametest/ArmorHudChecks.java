package dev.maro.gametest;

import dev.maro.module.ModuleManager;
import dev.maro.module.impl.visuals.ArmorHud;
import dev.maro.setting.ModeSetting;
import dev.maro.setting.Setting;
import dev.maro.setting.BooleanSetting;
import dev.maro.setting.NumberSetting;
import dev.maro.module.Module;
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
    private static Setting<?> setting(Module module,String name){return module.getSettings().stream().filter(s->s.getName().equals(name)).findFirst().orElseThrow();}
    private static void bounds(ArmorHud module){
        var c=net.minecraft.client.MinecraftClient.getInstance();float w=c.getWindow().getScaledWidth(),h=c.getWindow().getScaledHeight();
        require(module.hudLeft()>=3.5&&module.hudTop()>=3.5&&module.hudLeft()+module.hudWidth()<=w-3.5&&module.hudTop()+module.hudHeight()<=h-3.5,"Equipment card escaped the viewport");
    }

    static void run(ClientGameTestContext context, TestSingleplayerContext world) {
        ArmorHud module = ModuleManager.get(ArmorHud.class);
        require(module != null, "Armor HUD was not registered");
        int guiScale=context.computeOnClient(c->c.options.getGuiScale().getValue());
        try {
            world.getServer().runCommand("item replace entity @a armor.head with minecraft:diamond_helmet[damage=340]");
            world.getServer().runCommand("item replace entity @a armor.chest with minecraft:netherite_chestplate");
            world.getServer().runCommand("item replace entity @a armor.legs with minecraft:iron_leggings[damage=110]");
            world.getServer().runCommand("item replace entity @a armor.feet with minecraft:diamond_boots");
            world.getServer().runCommand("item replace entity @a weapon.offhand with minecraft:totem_of_undying");
            context.runOnClient(c -> {
                module.getSettings().forEach(Setting::reset);
                module.setEnabled(true);
                c.options.getGuiScale().setValue(2);c.onResolutionChanged();
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
            for(int gui:new int[]{2,3,4})for(String layout:new String[]{"Row","Column"}){
                context.runOnClient(c->{c.options.getGuiScale().setValue(gui);c.onResolutionChanged();((ModeSetting)setting(module,"Layout")).set(layout);bounds(module);});
                context.waitTicks(3);context.takeScreenshot("maro-armor-clean-"+layout.toLowerCase()+"-scale-"+gui);
            }
            for(String mode:new String[]{"Bar & Percent","Bar","Percent","Off"}){
                context.runOnClient(c->{c.options.getGuiScale().setValue(2);c.onResolutionChanged();((ModeSetting)setting(module,"Layout")).set("Row");((ModeSetting)setting(module,"Durability")).set(mode);bounds(module);});
                context.waitTicks(2);context.takeScreenshot("maro-armor-mode-"+mode.replace(' ','-').replace('&','a').toLowerCase());
            }
            context.runOnClient(c->{
                ((ModeSetting)setting(module,"Durability")).set("Bar & Percent");
                ((BooleanSetting)setting(module,"Main Hand")).set(true);
                c.player.equipStack(EquipmentSlot.MAINHAND,new net.minecraft.item.ItemStack(net.minecraft.item.Items.FIREWORK_ROCKET,64));
                var chest=c.player.getEquippedStack(EquipmentSlot.CHEST);chest.setDamage(chest.getMaxDamage());
                require(ArmorHud.left(chest)==0,"Zero durability must not have a positive bar");
            });context.waitTicks(3);context.takeScreenshot("maro-armor-counts-and-zero");
            context.runOnClient(c->{
                for(var slot:new EquipmentSlot[]{EquipmentSlot.HEAD,EquipmentSlot.LEGS,EquipmentSlot.FEET})c.player.equipStack(slot,net.minecraft.item.ItemStack.EMPTY);
                ((BooleanSetting)setting(module,"Show Empty")).set(false);bounds(module);
            });context.waitTicks(3);context.takeScreenshot("maro-armor-hidden-empty");
            context.runOnClient(c->{
                ((BooleanSetting)setting(module,"Show Empty")).set(true);
                ((BooleanSetting)setting(module,"Panel")).set(false);
                ((NumberSetting)setting(module,"Background Opacity")).set(0d);
            });context.waitTicks(3);context.takeScreenshot("maro-armor-minimal-transparent");
            for(String layout:new String[]{"Row","Column"})for(double size:new double[]{.5,1,2.5}){
                context.runOnClient(c->{
                    ((ModeSetting)setting(module,"Layout")).set(layout);((NumberSetting)setting(module,"Scale")).set(size);
                    module.hudMove(-1000,-1000);bounds(module);module.hudMove(10000,10000);bounds(module);
                });context.waitTicks(1);
            }
            context.runOnClient(c->{c.options.getGuiScale().setValue(4);c.onResolutionChanged();bounds(module);module.hudReset();bounds(module);});
            System.out.println("[armor-hud] PASS: native equipment/low warning, row and labeled column at GUI scales 2/3/4, all durability modes, zero durability, counts, empty filtering, transparent mode, placement clamping and resize at .5/1/2.5");
        } finally {
            context.runOnClient(c -> {
                module.setEnabled(false);
                module.getSettings().forEach(Setting::reset);
                c.player.equipStack(EquipmentSlot.MAINHAND,net.minecraft.item.ItemStack.EMPTY);
                c.options.getGuiScale().setValue(guiScale);c.onResolutionChanged();
            });
            for (String slot : new String[] {"armor.head", "armor.chest", "armor.legs", "armor.feet", "weapon.offhand"}) {
                world.getServer().runCommand("item replace entity @a " + slot + " with minecraft:air");
            }
            context.waitTicks(2);
        }
    }
}
