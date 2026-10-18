package dev.maro.anubis.inspect;

import dev.maro.gui.render.EffectSamples;
import dev.maro.gui.render.Render2D;
import dev.maro.mixin.*;
import dev.maro.module.Module;
import dev.maro.module.impl.visuals.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.render.SpecialGuiElementRenderer;
import net.minecraft.client.gui.render.state.special.SpecialGuiElementRenderState;
import net.minecraft.client.render.*;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.*;
import net.minecraft.util.math.RotationAxis;

/** Native first-person item models and the real knife choreography on an isolated GUI target. */
public final class ItemSubjectPreview extends SpecialGuiElementRenderer<ItemSubjectPreview.State> {
    public record State(Module module,ItemRenderState main,ItemRenderState off,ItemStack stack,double time,boolean playing,int x1,int y1,int x2,int y2,float scale,ScreenRect bounds) implements SpecialGuiElementRenderState {public ScreenRect scissorArea(){return bounds;}}
    private ItemSubjectPreview(){super(MinecraftClient.getInstance().getBufferBuilders().getEntityVertexConsumers());}
    public static void draw(DrawContext ctx,Module module,float x,float y,float w,float h,double time,boolean playing){
        var mc=MinecraftClient.getInstance();var gui=((GameRendererSpatialAccessor)mc.gameRenderer).maro$guiRenderer();((GuiRendererAccessor)gui).maro$specialRenderers().computeIfAbsent(State.class,k->new ItemSubjectPreview());
        var area=new ScreenRect(Math.round(x),Math.round(y),Math.max(1,Math.round(w)),Math.max(1,Math.round(h))).transformEachVertex(ctx.getMatrices());var clip=Render2D.getScissor();var visible=clip==null?area:area.intersection(clip);if(visible==null)return;
        var stack=mc.player.getMainHandStack().isEmpty()?new ItemStack(Items.DIAMOND_SWORD):mc.player.getMainHandStack().copy();var offStack=mc.player.getOffHandStack().isEmpty()?new ItemStack(Items.TOTEM_OF_UNDYING):mc.player.getOffHandStack().copy();
        if(module instanceof ItemInspect&&EffectSamples.mode(module,"Items","Selected Item").equals("Selected Item")){var id=net.minecraft.util.Identifier.tryParse(EffectSamples.mode(module,"Selected Item","minecraft:diamond_sword"));if(id!=null&&net.minecraft.registry.Registries.ITEM.containsId(id))stack=new ItemStack(net.minecraft.registry.Registries.ITEM.get(id));}
        var main=new ItemRenderState();var off=new ItemRenderState();mc.getItemModelManager().updateForLivingEntity(main,stack,ItemDisplayContext.FIRST_PERSON_RIGHT_HAND,mc.player);mc.getItemModelManager().updateForLivingEntity(off,offStack,ItemDisplayContext.FIRST_PERSON_LEFT_HAND,mc.player);
        float scale=Math.min(area.width()*.85f,area.height()*1.2f);
        ((DrawContextAccessor)ctx).maro$getState().addSpecialElement(new State(module,main,off,stack,time,playing,area.getLeft(),area.getTop(),area.getRight(),area.getBottom(),scale,visible));
    }
    @Override public Class<State> getElementClass(){return State.class;}
    @Override protected String getName(){return "Maro item motion preview";}
    @Override protected float getYOffset(int height,int scale){return height*.55f;}
    @Override protected void render(State s,MatrixStack pose){
        var mc=MinecraftClient.getInstance();mc.gameRenderer.getDiffuseLighting().setShaderLights(DiffuseLighting.Type.ENTITY_IN_UI);pose.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(180));
        var m=s.module();var queue=mc.gameRenderer.getEntityRenderCommandQueue();
        boolean weapon=m instanceof WeaponInspectModule,knife=m instanceof ItemInspect||weapon&&!EffectSamples.mode(m,"Knife Skin","None").equals("None");
        if(knife){
            String name=EffectSamples.mode(m,weapon?"Knife Skin":"Knife","Skeleton"),style=EffectSamples.mode(m,weapon?"Style":"Inspection Style",weapon?"Classic":"Signature");
            boolean alternate=style.equals("Alternate");double duration=weapon?3.2/EffectSamples.number(m,"Speed",1):EffectSamples.number(m,"Duration",3.2),progress=s.playing()?s.time()%duration/duration:0;
            var at=InspectionMotion.sample(name.equals("Katana")?"Bayonet":name,alternate,progress);
            if(weapon&&!style.equals("Classic")&&!style.equals("Cycle")){var values=WeaponInspectionStyles.clip(style,true).sample((float)progress,new float[16]);at=new InspectionMotion.Pose(values[0]*.6f,values[1]*.6f,values[2]*.5f,values[4]*.8f,values[3]*.75f,values[5]*.8f,values[6]+values[7],0,0);}
            pose.translate(-.39,.27,.82);
            InspectionKnifeRenderer.draw(pose,queue,LightmapTextureManager.MAX_LIGHT_COORDINATE,s.stack(),name,at,progress,s.playing(),alternate,false,EffectSamples.flag(m,"Show Arm",true),(float)EffectSamples.number(m,"Knife Size",weapon?1:100)/(weapon?1:100),0,0,EffectSamples.mode(m,"Finish","Satin Steel"),(float)EffectSamples.number(m,"Metal Reflections",75)/100,EffectSamples.mode(m,"Hand Style","Tactical Gloves").equals("Tactical Gloves"));
        }else{
            pose.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-35));
            double speed=m instanceof dev.maro.nathan.modules.SwingSpeed?dev.maro.nathan.modules.SwingSpeed.speedOf((int)EffectSamples.number(m,"swing speed",1)):1;
            float swing=s.playing()?(float)(s.time()*speed/.30%1):0;
            for(int hand=0;hand<(m instanceof ViewModel&&!EffectSamples.flag(m,"Hide Off Hand",false)?2:1);hand++){
                boolean left=hand==1;float side=left?-1:1;pose.push();
                try{
                    pose.translate(side*.20,-.06,0);String prefix=left?"Off":"Main";
                    if(m instanceof ViewModel){double depth=EffectSamples.number(m,prefix+" Z",0);pose.translate(side*EffectSamples.number(m,prefix+" X",0),EffectSamples.number(m,prefix+" Y",0),depth);pose.multiply(RotationAxis.POSITIVE_X.rotationDegrees((float)EffectSamples.number(m,prefix+" Rot X",0)));pose.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(side*(float)EffectSamples.number(m,prefix+" Rot Y",0)));pose.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(side*(float)EffectSamples.number(m,prefix+" Rot Z",0)));float scale=(float)EffectSamples.number(m,prefix+" Scale",1)*(float)Math.clamp(.82/Math.max(.2,.82-depth),.25,3);pose.scale(scale,scale,scale);}
                    String mode=EffectSamples.mode(m,"Swing Mode","Vanilla");if(mode.equals("Spin"))pose.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-360*swing*side));else if(mode.equals("Stab"))pose.translate(0,0,-Math.sin(swing*Math.PI)*.7);else if(!mode.equals("None")){pose.multiply(RotationAxis.POSITIVE_X.rotationDegrees((float)-Math.sin(Math.sqrt(swing)*Math.PI)*70));pose.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((float)Math.sin(swing*swing*Math.PI)*20*side));}
                    if(weapon){var values=WeaponInspectionStyles.clip(EffectSamples.mode(m,"Style","Classic"),false).sample(s.playing()?(float)(s.time()/3.2%1):0,new float[16]);pose.translate(values[0],values[1],values[2]);pose.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(values[3]));pose.multiply(RotationAxis.POSITIVE_X.rotationDegrees(values[4]));pose.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(values[5]+values[6]+values[7]));}
                    (left?s.off():s.main()).render(pose,queue,LightmapTextureManager.MAX_LIGHT_COORDINATE,OverlayTexture.DEFAULT_UV,0);
                }finally{pose.pop();}
            }
        }
        mc.gameRenderer.getEntityRenderDispatcher().render();
    }
}
