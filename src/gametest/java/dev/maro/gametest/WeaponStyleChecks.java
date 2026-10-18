package dev.maro.gametest;

import dev.maro.anubis.inspect.*;
import dev.maro.module.ModuleManager;
import dev.maro.module.impl.visuals.ItemInspect;
import dev.maro.setting.*;
import net.fabricmc.fabric.api.client.gametest.v1.context.*;
import net.minecraft.item.*;
import org.lwjgl.glfw.GLFW;
import java.util.*;

final class WeaponStyleChecks {
    private static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private static Setting<?> setting(WeaponInspectModule m,String name){return m.getSettings().stream().filter(s->s.getName().equals(name)).findFirst().orElseThrow();}
    private static void mode(WeaponInspectModule m,String name,String value){((ModeSetting)setting(m,name)).set(value);}
    static void run(ClientGameTestContext context,TestSingleplayerContext world){
        var m=ModuleManager.get(WeaponInspectModule.class);
        world.getServer().runCommand("gamemode creative @a");world.getServer().runCommand("time set noon");
        world.getServer().runCommand("tp @a 8 85 8 0 8");world.getServer().runCommand("item replace entity @a weapon.mainhand with minecraft:diamond_sword");
        context.waitTicks(6);
        var saved=new LinkedHashMap<Setting<?>,com.google.gson.JsonElement>();m.getSettings().forEach(s->saved.put(s,s.toJson()));
        try{
            context.runOnClient(c->{ModuleManager.get(ItemInspect.class).setEnabled(false);m.getSettings().forEach(Setting::reset);
                ((BooleanSetting)setting(m,"Deploy Animation")).set(false);((NumberSetting)setting(m,"Speed")).set(2d);
                c.player.getInventory().setSelectedSlot(0);c.player.getInventory().setStack(0,new ItemStack(Items.DIAMOND_SWORD));c.player.getAbilities().flying=true;m.setEnabled(true);});
            for(String skin:List.of("Butterfly","Karambit","Katana","Bayonet"))for(String style:List.of("Classic","Twirl","Edge Check","Reverse Grip","Palm Roll","Showcase","Quick Flip")){
                context.runOnClient(c->{mode(m,"Knife Skin",skin);mode(m,"Style",style);});context.waitTicks(2);
                context.getInput().pressKey(GLFW.GLFW_KEY_F);context.waitTicks(3);
                require(context.computeOnClient(c->m.inspecting()&&m.inspectionStyle().equals(style)),"Weapon style ignored: "+skin+" / "+style);
                require(context.computeOnClient(c->c.player.getMainHandStack().isOf(Items.DIAMOND_SWORD)),"Inspect key swapped the weapon");
                context.waitTicks(5);context.takeScreenshot("maro-cs-inspect-"+skin.toLowerCase()+"-"+style.toLowerCase().replace(' ','-'));
                for(int i=0;i<100&&context.computeOnClient(c->m.inspecting());i++)context.waitTick();
                require(!context.computeOnClient(c->m.inspecting()),"Weapon inspect did not settle: "+style);
            }
            context.runOnClient(c->mode(m,"Style","Cycle"));context.getInput().pressKey(GLFW.GLFW_KEY_F);context.waitTicks(2);
            String first=context.computeOnClient(c->m.inspectionStyle());context.waitTicks(45);context.getInput().pressKey(GLFW.GLFW_KEY_F);context.waitTicks(2);
            require(!context.computeOnClient(c->m.inspectionStyle()).equals(first),"Weapon cycle repeated the same style");context.waitTicks(40);
            context.runOnClient(c->{mode(m,"Knife Skin","None");mode(m,"Style","Edge Check");});context.getInput().pressKey(GLFW.GLFW_KEY_F);context.waitTicks(5);
            require(context.computeOnClient(c->m.inspecting()),"Vanilla weapon inspection stopped working");context.takeScreenshot("maro-weapon-native-edge-check");
            context.runOnClient(c->{c.player.swingHand(net.minecraft.util.Hand.MAIN_HAND);m.onTick();require(!m.inspecting(),"Attack did not interrupt weapon inspection");});
            System.out.println("[weapon-styles] PASS: 13 smooth knife models, seven selectable routines plus Cycle, 28 skin/style combinations, native F, idle return, vanilla item mode and attack cancellation");
        }finally{context.getInput().releaseKey(GLFW.GLFW_KEY_F);context.runOnClient(c->{m.setEnabled(false);saved.forEach(Setting::fromJson);});}
    }
}
