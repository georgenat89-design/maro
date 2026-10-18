package dev.maro.gametest;

import dev.maro.module.ModuleManager;
import dev.maro.module.impl.visuals.ItemInspect;
import dev.maro.setting.*;
import net.fabricmc.fabric.api.client.gametest.v1.context.*;
import net.minecraft.item.*;
import net.minecraft.util.Arm;
import java.util.*;

/** Samples the production renderer at known animation phases for a reproducible native video. */
final class KnifePreviewChecks {
    static void run(ClientGameTestContext context,TestSingleplayerContext world){
        if(!Boolean.getBoolean("maro.gametest.knifePreview"))return;
        var m=ModuleManager.get(ItemInspect.class);var saved=new LinkedHashMap<Setting<?>,com.google.gson.JsonElement>();
        m.getSettings().forEach(s->saved.put(s,s.toJson()));
        boolean hud=context.computeOnClient(c->c.options.hudHidden);Arm arm=context.computeOnClient(c->c.options.getMainArm().getValue());
        try{
            var clock=ItemInspect.class.getDeclaredField("startedAt");clock.setAccessible(true);
            world.getServer().runCommand("time set noon");world.getServer().runCommand("gamemode creative @a");
            world.getServer().runCommand("gamerule fall_damage false");world.getServer().runCommand("tp @a 8 85 8 0 8");
            context.runOnClient(c->{c.setScreen(null);c.options.hudHidden=false;c.options.getMainArm().setValue(Arm.RIGHT);
                c.player.getInventory().setSelectedSlot(0);c.player.getInventory().setStack(0,new ItemStack(Items.DIAMOND_SWORD));c.player.getAbilities().flying=true;
                m.getSettings().forEach(Setting::reset);((NumberSetting)setting(m,"Duration")).set(6d);
                ((NumberSetting)setting(m,"Camera Sway")).set(0d);((BooleanSetting)setting(m,"Idle Motion")).set(false);
                ((ModeSetting)setting(m,"Inspection Style")).set("Signature");});
            context.waitTicks(3);
            for(String knife:List.of("Butterfly","Maro Rift","Kukri","Ursus")){
                context.runOnClient(c->{m.setEnabled(false);((ModeSetting)setting(m,"Knife")).set(knife);m.setEnabled(true);m.inspect();});
                for(int frame=0;frame<64;frame++){
                    double phase=frame/63d;
                    context.runOnClient(c->{
                        if(c.player==null||!c.player.isAlive()||c.currentScreen!=null)throw new AssertionError("Preview must remain in live first-person gameplay");
                        try{clock.setLong(m,System.nanoTime()-(long)(phase*6_000_000_000L));}catch(IllegalAccessException e){throw new AssertionError(e);}});
                    context.waitTick();context.takeScreenshot("maro-preview-"+knife.toLowerCase().replace(' ','-')+"-"+String.format(Locale.ROOT,"%03d",frame));
                }
            }
            System.out.println("[knife-preview] PASS: 256 native frames of four signature inspections");
        }catch(ReflectiveOperationException e){throw new AssertionError(e);}
        finally{context.runOnClient(c->{m.setEnabled(false);saved.forEach(Setting::fromJson);c.options.hudHidden=hud;c.options.getMainArm().setValue(arm);});}
    }
    private static Setting<?> setting(ItemInspect m,String name){return m.getSettings().stream().filter(s->s.getName().equals(name)).findFirst().orElseThrow();}
}
