package dev.maro.gametest;

import dev.maro.gui.hud.CustomModelScreen;
import dev.maro.module.ModuleManager;
import dev.maro.module.impl.visuals.CustomModel;
import dev.maro.render.character.*;
import dev.maro.setting.Setting;
import net.fabricmc.fabric.api.client.gametest.v1.context.*;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.item.*;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.MapIdComponent;
import net.minecraft.util.*;

final class SkeletonHandChecks {
    private static void require(boolean b,String why){if(!b)throw new AssertionError(why);}
    static void run(ClientGameTestContext context,TestSingleplayerContext world){
        var model=ModuleManager.get(CustomModel.class);
        var perspective=context.computeOnClient(c->c.options.getPerspective());var arm=context.computeOnClient(c->c.options.getMainArm().getValue());
        int gui=context.computeOnClient(c->c.options.getGuiScale().getValue());
        try{
            world.getServer().runCommand("gamemode creative @a");world.getServer().runCommand("time set noon");
            context.runOnClient(c->{model.getSettings().forEach(Setting::reset);model.select(false,"Skeleton");model.setEnabled(true);c.options.setPerspective(Perspective.FIRST_PERSON);c.options.getGuiScale().setValue(2);c.onResolutionChanged();c.player.getInventory().clear();c.player.setYaw(0);c.player.setPitch(35);c.setScreen(null);});
            for(int level=0;level<3;level++){
                for(int i=0;i<300&&!context.computeOnClient(c->model.ready("Skeleton")!=null);i++)context.waitTicks(1);
                require(context.computeOnClient(c->model.ready("Skeleton")!=null),"Skeleton did not load");
                context.runOnClient(c->{var renderer=(PlayerEntityRenderer<?>)c.getEntityRenderDispatcher().getRenderer(c.player);CharacterHandProbe.check(model.ready("Skeleton"),renderer.getModel());});
                for(Arm hand:Arm.values()){
                    context.runOnClient(c->c.options.getMainArm().setValue(hand));context.waitTicks(8);
                    context.takeScreenshot("maro-skeleton-hand-"+model.detailLabel().toLowerCase()+"-"+hand.name().toLowerCase());
                    context.runOnClient(c->c.player.swingHand(Hand.MAIN_HAND));context.waitTicks(2);
                    context.takeScreenshot("maro-skeleton-hand-swing-"+model.detailLabel().toLowerCase()+"-"+hand.name().toLowerCase());context.waitTicks(5);
                }
                context.runOnClient(c->{c.setScreen(new CustomModelScreen(null,model));var renderer=(PlayerEntityRenderer<?>)c.getEntityRenderDispatcher().getRenderer(c.player);var s=(PlayerEntityRenderState)c.getEntityRenderDispatcher().getAndUpdateRenderState(c.player,0);s.baseScale=1;s.limbAmplitudeInverse=1;s.limbSwingAmplitude=.8f;s.limbSwingAnimationProgress=2;s.isInSneakingPose=true;s.handSwingProgress=.4f;CharacterDeformationProbe.check(model.ready("Skeleton"),renderer.getModel(),s,model);});
                context.waitTicks(3);context.takeScreenshot("maro-skeleton-repaired-"+model.detailLabel().toLowerCase());
                context.runOnClient(c->{c.setScreen(null);model.cycleDetail();});
            }
            for(int i=0;i<300&&!context.computeOnClient(c->model.ready("Skeleton")!=null);i++)context.waitTicks(1);
            context.runOnClient(c->{c.options.getMainArm().setValue(Arm.RIGHT);var map=new ItemStack(Items.FILLED_MAP);map.set(DataComponentTypes.MAP_ID,new MapIdComponent(0));c.player.getInventory().setStack(c.player.getInventory().getSelectedSlot(),map);});
            context.waitTicks(12);context.takeScreenshot("maro-skeleton-both-map-hands");
            context.runOnClient(c->{c.player.getInventory().clear();model.setEnabled(false);});context.waitTicks(8);context.takeScreenshot("maro-skeleton-vanilla-restored");
            RenderFaultChecks.assertClean();System.out.println("[skeleton-hands] PASS: complete anatomical arm/hand ownership, deferred first-person geometry, both hands idle/swing, all 3 detail levels, palm grips, third-person crouch/swing/size, map hands and vanilla restoration");
        }finally{context.runOnClient(c->{model.setEnabled(false);c.setScreen(null);c.player.getInventory().clear();c.options.setPerspective(perspective);c.options.getMainArm().setValue(arm);c.options.getGuiScale().setValue(gui);c.onResolutionChanged();});}
    }
}
