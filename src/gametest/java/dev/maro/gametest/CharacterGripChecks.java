package dev.maro.gametest;

import dev.maro.gui.hud.CustomModelScreen;
import dev.maro.module.ModuleManager;
import dev.maro.module.impl.visuals.CustomModel;
import dev.maro.module.impl.visuals.SkinAccessories;
import dev.maro.setting.Setting;
import dev.maro.mixin.AccessorHandItemRenderState;
import dev.maro.mixin.AccessorHandItemLayer;
import dev.maro.render.pickaxe.PickaxeModels;
import dev.maro.render.pickaxe.PickaxeRenderer;
import net.fabricmc.fabric.api.client.gametest.v1.context.*;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.*;
import net.minecraft.util.Arm;

/** Supplied characters with real held tool render states, through the queued entity renderer. */
final class CharacterGripChecks {
    private static void anchors() {
        for(String name:PickaxeModels.NAMES)for(float size:new float[]{.65f,1,1.35f})for(boolean left:new boolean[]{false,true}) {
            var item=new ItemRenderState();
            var type=left?ItemDisplayContext.THIRD_PERSON_LEFT_HAND:ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
            PickaxeRenderer.update(item,name,type,new PickaxeRenderer.Style(.75f,true,0xFFFF0000,.004f,size,0,0,1),false);
            var layer=((AccessorHandItemRenderState)item).anubis$handLayers()[0];
            var pose=new MatrixStack();((AccessorHandItemLayer)layer).anubis$handTransform().apply(left,pose.peek());
            var contact=new org.joml.Vector3f(PickaxeModels.grip(name));pose.peek().getPositionMatrix().transformPosition(contact);
            if(contact.length()>1e-5f)throw new AssertionError("Handle moved away from palm: "+name+" "+type+" size="+size+" offset="+contact);
        }
    }
    static void run(ClientGameTestContext context, TestSingleplayerContext world) {
        var model=ModuleManager.get(CustomModel.class);var cosmetics=ModuleManager.get(SkinAccessories.class);
        int scale=context.computeOnClient(c->c.options.getGuiScale().getValue());
        var arm=context.computeOnClient(c->c.options.getMainArm().getValue());
        try {
            world.getServer().runCommand("gamemode creative @a");
            world.getServer().runCommand("item replace entity @a weapon.mainhand with minecraft:diamond_pickaxe");
            context.waitTicks(3);
            context.runOnClient(c->{
                anchors();
                model.getSettings().forEach(Setting::reset);cosmetics.getSettings().forEach(Setting::reset);
                cosmetics.select("Merry Mint Axe");cosmetics.setEnabled(true);model.setEnabled(true);
                c.options.getMainArm().setValue(Arm.RIGHT);c.options.getGuiScale().setValue(2);c.onResolutionChanged();
                c.player.getInventory().setStack(c.player.getInventory().getSelectedSlot(),new ItemStack(Items.DIAMOND_PICKAXE));
                c.setScreen(new CustomModelScreen(null,model));
            });
            for(String name:new String[]{"LeBron James","Chun-Li","SpongeBob","Skeleton","Jeffrey Epstein","Donald Trump","Michael Jackson","Roblox Bacon Hair","Travis Scott","Undead Fuhrer"}) {
                context.runOnClient(c->model.select(false,name));
                for(int i=0;i<300&&!context.computeOnClient(c->model.ready(name)!=null);i++)context.waitTicks(1);
                if(!context.computeOnClient(c->model.ready(name)!=null))throw new AssertionError("Missing character: "+name);
                for(int angle:new int[]{0,90}) {
                    context.runOnClient(c->((CustomModelScreen)c.currentScreen).showAngle(angle));context.waitTicks(3);
                    context.takeScreenshot("maro-grip-"+name.toLowerCase().replace(' ','-')+"-"+angle);
                }
                context.runOnClient(c->cosmetics.setEnabled(false));context.waitTicks(2);
                context.takeScreenshot("maro-grip-vanilla-"+name.toLowerCase().replace(' ','-'));
                context.runOnClient(c->cosmetics.setEnabled(true));
            }
            context.runOnClient(c->{model.select(false,"Chun-Li");c.options.getMainArm().setValue(Arm.LEFT);});
            for(String pick:PickaxeModels.NAMES) {
                context.runOnClient(c->cosmetics.select(pick));context.waitTicks(3);
                context.takeScreenshot("maro-grip-left-"+PickaxeModels.key(pick));
            }
            System.out.println("[character-grip] PASS: 5 handle anchors stay at the palm for both hands and sizes .65/1/1.35 through native Transformation.apply; native supplied-model previews, vanilla tools and all 5 left-hand pickaxes rendered");
        } finally {
            context.runOnClient(c->{c.setScreen(null);model.setEnabled(false);cosmetics.setEnabled(false);
                c.options.getMainArm().setValue(arm);c.options.getGuiScale().setValue(scale);c.onResolutionChanged();});
        }
    }
}
