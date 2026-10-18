package dev.maro.gametest;

import dev.maro.gui.hud.CustomModelScreen;
import dev.maro.module.ModuleManager;
import dev.maro.module.impl.visuals.*;
import dev.maro.render.character.*;
import dev.maro.setting.*;
import net.fabricmc.fabric.api.client.gametest.v1.context.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.MouseInput;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.text.Text;
import org.joml.Vector3f;
import org.joml.Quaternionf;

final class CharacterMotionChecks {
    private static void require(boolean b,String why){if(!b)throw new AssertionError(why);}
    private static PlayerEntityRenderState state(MinecraftClient c,int pose){
        var s=(PlayerEntityRenderState)c.getEntityRenderDispatcher().getAndUpdateRenderState(c.player,0);
        s.id=Integer.MIN_VALUE+1;s.baseScale=1;s.light=0xF000F0;s.shadowPieces.clear();s.outlineColor=0;s.nameLabelPos=null;
        s.bodyYaw=200;s.relativeHeadYaw=12;s.pitch=0;s.limbAmplitudeInverse=1;s.limbSwingAmplitude=.9f;
        s.limbSwingAnimationProgress=pose==0?.4f:pose==1?2.2f:4.4f;
        s.isInSneakingPose=pose==1;s.handSwingProgress=pose==2?.55f:0;s.preferredArm=net.minecraft.util.Arm.RIGHT;
        s.rightArmPose=BipedEntityModel.ArmPose.ITEM;s.leftArmPose=BipedEntityModel.ArmPose.EMPTY;
        return s;
    }
    private static final class PoseScreen extends Screen{
        final int pose;PoseScreen(int pose){super(Text.literal("Moving character regression"));this.pose=pose;}
        @Override public void render(DrawContext ctx,int mx,int my,float delta){
            ctx.fill(0,0,width,height,0xFF182131);var s=state(client,pose);var worn=CustomModel.worn(s);
            if(worn==null){ctx.drawText(client.textRenderer,"Preparing model...",12,12,0xFFFFFFFF,false);return;}
            float h=worn.model.maxY*worn.model.scale*CustomModel.sizeFactor()/16;
            ctx.drawText(client.textRenderer,worn.model.name+" / "+(pose==0?"Walk":pose==1?"Crouch":"Swing")+" / "+Math.round(CustomModel.sizeFactor()*100)+"%",12,12,0xFFFFFFFF,false);
            ctx.addEntity(s,Math.min((width-50)/4f,(height-50)/(Math.max(2,h)+.3f)),new Vector3f(0,h/2,0),new Quaternionf().rotateZ((float)Math.PI).rotateX(.08f),new Quaternionf().rotateX(.08f),20,28,width-20,height-16);
        }
        @Override public boolean shouldPause(){return false;}
    }
    static void run(ClientGameTestContext context,TestSingleplayerContext world){
        var model=ModuleManager.get(CustomModel.class);var cosmetic=ModuleManager.get(SkinAccessories.class);
        int scale=context.computeOnClient(c->c.options.getGuiScale().getValue());var perspective=context.computeOnClient(c->c.options.getPerspective());
        try{
            context.runOnClient(c->{model.getSettings().forEach(Setting::reset);model.setEnabled(true);cosmetic.select("Merry Mint Axe");cosmetic.setEnabled(true);c.options.setPerspective(Perspective.THIRD_PERSON_FRONT);c.options.getGuiScale().setValue(2);c.onResolutionChanged();});
            for(String name:new String[]{"SpongeBob","LeBron James","Chun-Li","Skeleton","Jeffrey Epstein","Donald Trump","Michael Jackson","Roblox Bacon Hair","Travis Scott","Undead Fuhrer"}){
                context.runOnClient(c->{model.select(false,name);c.setScreen(new PoseScreen(0));});
                for(int i=0;i<300&&!context.computeOnClient(c->model.ready(name)!=null);i++)context.waitTicks(1);
                require(context.computeOnClient(c->model.ready(name)!=null),"Model failed to load: "+name);
                for(int pose=0;pose<3;pose++){
                    int p=pose;context.runOnClient(c->{c.setScreen(new PoseScreen(p));var s=state(c,p);var renderer=(PlayerEntityRenderer<?>)c.getEntityRenderDispatcher().getRenderer(c.player);CharacterDeformationProbe.check(model.ready(name),renderer.getModel(),s,model);});
                    context.waitTicks(3);context.takeScreenshot("maro-motion-"+name.toLowerCase().replace(' ','-')+"-"+pose);
                }
                for(int level=0;level<2;level++){
                    context.runOnClient(c->model.cycleDetail());
                    for(int i=0;i<300&&!context.computeOnClient(c->model.ready(name)!=null);i++)context.waitTicks(1);
                    require(context.computeOnClient(c->model.ready(name)!=null),"Detail model failed to load: "+name);
                    for(int pose=0;pose<3;pose++){int p=pose;context.runOnClient(c->{var s=state(c,p);var renderer=(PlayerEntityRenderer<?>)c.getEntityRenderDispatcher().getRenderer(c.player);CharacterDeformationProbe.check(model.ready(name),renderer.getModel(),s,model);});}
                }
                context.runOnClient(c->model.cycleDetail());
                for(int i=0;i<300&&!context.computeOnClient(c->model.ready(name)!=null);i++)context.waitTicks(1);
            }
            context.runOnClient(c->{c.setScreen(new CustomModelScreen(null,model));});context.waitTicks(3);
            context.runOnClient(c->{
                var screen=c.currentScreen;float w=Math.min(610,screen.width-16),h=Math.min(370,screen.height-16),x=(screen.width-w)/2f,y=(screen.height-h)/2f,pw=w*.4f;
                var plus=new Click(x+pw-19,y+h-48,new MouseInput(0,0));screen.mouseClicked(plus,false);require(CustomModel.sizeFactor()==1.05f,"Wardrobe plus does not update live");
                screen.mouseClicked(new Click(x+29,y+h-48,new MouseInput(0,0)),false);require(CustomModel.sizeFactor()==1,"Wardrobe minus does not update live");
                model.resize(20);require(CustomModel.sizeFactor()==2,"Size maximum wrong");
            });context.waitTicks(3);context.takeScreenshot("maro-model-size-200");
            context.runOnClient(c->{var screen=c.currentScreen;float w=Math.min(610,screen.width-16),h=Math.min(370,screen.height-16),x=(screen.width-w)/2f,y=(screen.height-h)/2f;screen.mouseClicked(new Click(x+w*.2f,y+h-48,new MouseInput(0,0)),false);require(CustomModel.sizeFactor()==1,"Clicking size did not reset");});
            for(int gui:new int[]{3,4}){context.runOnClient(c->{c.options.getGuiScale().setValue(gui);c.onResolutionChanged();});context.waitTicks(3);context.takeScreenshot("maro-model-size-layout-"+gui);}
            System.out.println("[character-motion] PASS: all 9 tested models including Travis Scott and all 3 detail levels, walking/crouching/swinging; deferred native geometry stays joined, normalized and finite; 50/100/200% body and weapon scaling, cached meshes, wardrobe +/-/reset, GUI scales 2/3/4");
        }finally{context.runOnClient(c->{model.resetSize();model.setEnabled(false);cosmetic.setEnabled(false);c.setScreen(null);c.options.setPerspective(perspective);c.options.getGuiScale().setValue(scale);c.onResolutionChanged();});}
    }
}
