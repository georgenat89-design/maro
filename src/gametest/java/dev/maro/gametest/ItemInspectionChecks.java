package dev.maro.gametest;

import com.google.gson.JsonElement;
import dev.maro.anubis.inspect.WeaponInspectModule;
import dev.maro.anubis.inspect.InspectionMotion;
import dev.maro.config.ClientSettings;
import dev.maro.gui.ClickGuiScreen;
import dev.maro.gui.hud.ItemPickerScreen;
import dev.maro.module.ModuleManager;
import dev.maro.module.impl.visuals.ItemInspect;
import dev.maro.setting.*;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.Click;
import net.minecraft.client.input.MouseInput;
import net.minecraft.client.option.Perspective;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.MapIdComponent;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import org.lwjgl.glfw.GLFW;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Inspection selection, native keyboard input, render contexts, interrupts and every reference style. */
final class ItemInspectionChecks {
    private static Setting<?> setting(ItemInspect m, String name) {
        return m.getSettings().stream().filter(s -> s.getName().equals(name)).findFirst().orElseThrow();
    }
    private static void require(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    private static void waitMillis(ClientGameTestContext c, long time) {
        long end = System.nanoTime() + time * 1_000_000L;
        do { c.waitTick(); } while (System.nanoTime() < end);
    }
    private static void equip(ClientGameTestContext context, TestSingleplayerContext world, Item item) {
        world.getServer().runOnServer(s -> {
            var p = s.getPlayerManager().getPlayerList().getFirst();
            p.getInventory().setSelectedSlot(0); p.getInventory().setStack(0, new ItemStack(item));
        });
        context.waitTicks(5);
        context.runOnClient(c -> { c.player.getInventory().setSelectedSlot(0); c.player.getInventory().setStack(0, new ItemStack(item)); });
        context.waitTicks(2);
    }
    static void run(ClientGameTestContext context, TestSingleplayerContext world) {
        var m = ModuleManager.get(ItemInspect.class);
        var weapon = ModuleManager.get(WeaponInspectModule.class);
        Map<Setting<?>, JsonElement> saved = new LinkedHashMap<>();
        m.getSettings().forEach(s -> saved.put(s, s.toJson()));
        Arm arm = context.computeOnClient(c -> c.options.getMainArm().getValue());
        String style = ClientSettings.menuStyle.get();
        boolean spatial = ClientSettings.spatialMenu.get();
        boolean screen = ClientSettings.screenGui.get();
        double textSize = ClientSettings.menuTextSize.get();
        try {
            world.getServer().runCommand("gamemode creative @a");
            world.getServer().runCommand("time set noon");
            world.getServer().runCommand("tp @a 8 80 8 0 8");
            context.waitTicks(8);
            context.runOnClient(c -> {
                m.getSettings().forEach(Setting::reset); m.setEnabled(true);
                c.options.setPerspective(Perspective.FIRST_PERSON); c.player.getAbilities().flying = true;
                c.options.getMainArm().setValue(Arm.RIGHT);
                ((NumberSetting)setting(m,"Duration")).set(1.8);
                require(m.getDisplayName().equals("Item Inspection") && ModuleManager.search("item inspection").contains(m), "Inspection name/search missing");
                ((ButtonSetting)setting(m,"Choose Item")).press();
                require(c.currentScreen instanceof ItemPickerScreen, "Choose Item did not open the native grid");
                require(((ItemPickerScreen)c.currentScreen).search("ender pearl").equals(List.of(Items.ENDER_PEARL)), "Item grid did not find the requested item");
            });
            context.waitTicks(3);
            context.takeScreenshot("maro-inspection-item-picker");
            context.runOnClient(c -> {
                var picker = (ItemPickerScreen)c.currentScreen;
                double pw = Math.min(520, picker.width - 24), ph = Math.min(360, picker.height - 24);
                double x = (picker.width-pw)/2+25, y = (picker.height-ph)/2+67;
                picker.mouseClicked(new Click(x,y,new MouseInput(0,0)),false);
                require(((ModeSetting)setting(m,"Items")).is("Selected Item") && m.accepts(new ItemStack(Items.ENDER_PEARL)), "Grid selection did not restrict inspection");
                require(!m.accepts(new ItemStack(Items.DIAMOND_SWORD)), "Unselected sword was accepted");
                c.setScreen(null);
            });
            equip(context,world,Items.DIAMOND_SWORD);
            context.getInput().holdKey(GLFW.GLFW_KEY_Y); context.waitTicks(3); context.getInput().releaseKey(GLFW.GLFW_KEY_Y);
            require(!context.computeOnClient(c -> m.inspecting()), "Unselected item inspected from native key");
            require(!context.computeOnClient(c -> ItemInspect.handles(c.player.getMainHandStack())), "Unselected item acquired a knife appearance");
            context.runOnClient(c -> ((ButtonSetting)setting(m,"Use Held Item")).press());
            context.getInput().holdKey(GLFW.GLFW_KEY_Y); context.waitTicks(3); context.getInput().releaseKey(GLFW.GLFW_KEY_Y);
            require(context.computeOnClient(c -> m.inspecting()), "Selected held item did not inspect from native Y");
            context.runOnClient(c -> c.player.swingHand(Hand.MAIN_HAND)); context.waitTicks(2);
            require(!context.computeOnClient(c -> m.inspecting()), "Attacking did not cancel inspection");
            context.waitTicks(12);
            for (String animation : InspectionMotion.KNIVES) {
                context.runOnClient(c -> { ((ModeSetting)setting(m,"Knife")).set(animation); });
                context.waitTicks(3); context.takeScreenshot("maro-knife-idle-"+animation.toLowerCase(java.util.Locale.ROOT).replace(' ','-'));
                require(context.computeOnClient(c -> ItemInspect.handles(c.player.getMainHandStack())), "Knife replacement missing at idle: "+animation);
                context.runOnClient(c -> ((BooleanSetting)setting(m,"Show Arm")).set(false));
                context.waitTicks(3); context.takeScreenshot("maro-knife-model-"+animation.toLowerCase(java.util.Locale.ROOT).replace(' ','-'));
                context.runOnClient(c -> ((BooleanSetting)setting(m,"Show Arm")).set(true));
                for(String routine:List.of("Signature","Alternate")) {
                    context.runOnClient(c -> {((ModeSetting)setting(m,"Inspection Style")).set(routine);m.inspect();});
                    waitMillis(context,350);
                    require(context.computeOnClient(c -> m.inspecting() && m.alternateInspection()==routine.equals("Alternate")), "Wrong inspection routine: "+animation+"/"+routine);
                    context.takeScreenshot("maro-inspection-"+animation.toLowerCase(java.util.Locale.ROOT).replace(' ','-')+"-"+routine.toLowerCase());
                    waitMillis(context,450);
                    context.takeScreenshot("maro-inspection-detail-"+animation.toLowerCase(java.util.Locale.ROOT).replace(' ','-')+"-"+routine.toLowerCase());
                    waitMillis(context,1200);
                    require(!context.computeOnClient(c -> m.inspecting()), "Animation never returned to rest: "+animation);
                    require(context.computeOnClient(c -> ItemInspect.handles(c.player.getMainHandStack())), "Knife disappeared after inspecting: "+animation);
                    require(context.computeOnClient(c -> c.player.getMainHandStack().isOf(Items.DIAMOND_SWORD)), "Inspection changed the actual item");
                }
            }
            for(String coat:List.of("Satin Steel","Damascus","Black Cerakote","Maro Blue","Vanilla Steel")) {
                context.runOnClient(c -> {((ModeSetting)setting(m,"Knife")).set("Maro Rift");((ModeSetting)setting(m,"Finish")).set(coat);});
                context.waitTicks(4);context.takeScreenshot("maro-knife-finish-"+coat.toLowerCase(java.util.Locale.ROOT).replace(' ','-'));
            }
            context.runOnClient(c -> {
                ((ModeSetting)setting(m,"Finish")).reset();((ModeSetting)setting(m,"Knife")).set("Skeleton");
                ((ModeSetting)setting(m,"Inspection Style")).set("Cycle");m.inspect();
            });
            boolean first=context.computeOnClient(c->m.alternateInspection());
            context.getInput().holdKey(GLFW.GLFW_KEY_Y);context.waitTicks(2);context.getInput().releaseKey(GLFW.GLFW_KEY_Y);
            waitMillis(context,1900);
            require(context.computeOnClient(c->m.inspecting()&&m.alternateInspection()!=first),"Repeated inspect did not queue the next routine smoothly");
            waitMillis(context,1900);

            context.runOnClient(c -> {
                ((ModeSetting)setting(m,"Items")).set("Any Item");
                require(!m.accepts(ItemStack.EMPTY), "Empty hand was inspectable");
            });
            for (Item item : List.of(Items.APPLE,Items.DIAMOND_BLOCK,Items.BOW,Items.TRIDENT,Items.FILLED_MAP)) {
                equip(context,world,item);
                if (item == Items.FILLED_MAP) context.runOnClient(c -> c.player.getMainHandStack().set(DataComponentTypes.MAP_ID,new MapIdComponent(0)));
                context.runOnClient(c -> m.inspect()); waitMillis(context,250);
                require(context.computeOnClient(c -> m.inspecting()), "Any Item rejected "+item);
                context.takeScreenshot("maro-inspection-any-"+net.minecraft.registry.Registries.ITEM.getId(item).getPath());
            }
            context.runOnClient(c -> { c.player.setStackInHand(Hand.OFF_HAND,new ItemStack(Items.SHIELD)); m.inspect(); });
            waitMillis(context,250); context.takeScreenshot("maro-inspection-map-one-hand");
            context.runOnClient(c -> c.player.setStackInHand(Hand.OFF_HAND,ItemStack.EMPTY));
            equip(context,world,Items.NETHERITE_SWORD);
            context.runOnClient(c -> {
                ((ModeSetting)setting(m,"Finish")).set("Item Material");
                ((NumberSetting)setting(m,"Knife Size")).set(135d);
                c.player.getMainHandStack().set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE,true);
                m.inspect();
            });
            waitMillis(context,550); context.takeScreenshot("maro-inspection-material-glint");
            context.runOnClient(c -> {((ModeSetting)setting(m,"Finish")).reset();((NumberSetting)setting(m,"Knife Size")).reset();});
            equip(context,world,Items.DIAMOND_SWORD);
            context.runOnClient(c -> {
                c.options.getMainArm().setValue(Arm.LEFT); weapon.setEnabled(true); m.inspect();
                require(ItemInspect.handles(c.player.getMainHandStack()), "Weapon Inspect masked Item Inspection");
            });
            waitMillis(context,550); context.takeScreenshot("maro-inspection-left-hand");
            context.runOnClient(c -> { c.player.getInventory().setSelectedSlot(1); m.onTick(); require(!m.inspecting(),"Slot switch did not cancel"); weapon.setEnabled(false); });
            equip(context,world,Items.APPLE);
            context.runOnClient(c -> {
                ((BooleanSetting)setting(m,"Inspect On Equip")).set(true);
                c.player.getInventory().setStack(0,new ItemStack(Items.DIAMOND_BLOCK)); m.onTick();
                require(m.inspecting(), "Inspect On Equip still restricted itself to weapons");
                m.setEnabled(false); m.inspect(); require(!m.inspecting(), "Disabled inspection could start");
                ClientSettings.menuStyle.set("Window"); ClientSettings.spatialMenu.set(false); ClientSettings.screenGui.set(false);
                require(!ItemInspect.handles(c.player.getMainHandStack()), "Disabled module retained knife appearance");
                ClientSettings.menuTextSize.set(90d); var gui = new ClickGuiScreen(); c.setScreen(gui); gui.openModuleSettings(m,"Knife");
            });
            context.waitTicks(15); context.takeScreenshot("maro-item-inspection-settings");
            System.out.println("[item-inspection] PASS: 12 persistent knives, 24 inspections, 6 finishes, queued/cycling native Y, searchable grid, selected/any item, maps, left arm, equip/attack/swap cancellation, legacy weapon coexistence");
        } finally {
            context.getInput().releaseKey(GLFW.GLFW_KEY_Y);
            context.runOnClient(c -> {
                c.setScreen(null); m.setEnabled(false); weapon.setEnabled(false); saved.forEach(Setting::fromJson);
                c.options.getMainArm().setValue(arm); ClientSettings.menuStyle.set(style); ClientSettings.spatialMenu.set(spatial);
                ClientSettings.screenGui.set(screen); ClientSettings.menuTextSize.set(textSize);
            });
        }
    }
}
